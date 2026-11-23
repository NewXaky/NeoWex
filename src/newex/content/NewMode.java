package newex.content;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.WaveEvent;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.io.MapIO;
import mindustry.game.Saves.SaveSlot;
import mindustry.maps.Map;
import mindustry.ui.Styles;
import newex.MachineUi;

import java.util.zip.CRC32;

/**
 * Chuyển từ newmode.js. Menu "NewMode": chọn map, lưu/tiếp tục, thưởng điểm Gacha.
 * Các chế độ Survival / PvP (newmode-survival.js, newmode-pvp.js) khi được chuyển sang Java
 * chỉ cần gọi NewMode.survival = ... / NewMode.pvp = ... để cắm vào (thay cho global.NexSurvival / global.NexPvp).
 */
public final class NewMode {
    private NewMode() {}

    /** Thay cho global.NexSurvival / global.NexPvp của JS. */
    public interface ModeHook {
        int rewardPoints();
        void onBegin();
        void onResume();
        default int killGoal() { return 0; }
        default void persistStore() {}
        /** Kết quả trận (chỉ Survival dùng), null nếu không có. */
        default Result lastResult() { return null; }
    }

    public static class Result {
        public int kills, wave;
        public String time;
    }

    public static ModeHook survival;
    public static ModeHook pvp;
    public static ModeHook domination;

    static final String MOD_NAME = "newex";
    static final String GACHA_POINTS_KEY = "newex-gacha-points";
    static final String SURVIVAL_ID = "newmodesurvival";
    static final String PVP_ID = "pvpnewex3110";
    static final String DOM_ID = "pvpchiemcu";
    static final String MAP_SIG_KEY = "newex-newmode-mapsig-";
    static final String SLOT_PREFIX = "NewEx_NewMode_Save_";

    private static final java.util.HashMap<String, long[]> sigCache = new java.util.HashMap<>();

    private static String activeMapName = null;
    private static boolean isPlayingNewMode = false;
    private static int pendingGachaReward = 0;
    private static boolean hasWonPending = false;
    /** Người chơi vào host của bạn (không tự bắt đầu trận): vẫn nhận thưởng thắng nhưng không đụng tới save của mình. */
    private static boolean guest = false;

    private static final String[][] MAPS = {
        {"Newbie", "Newbie"},
        {"redces", "Redces ᑈᐴᐾᐶᒅ"},
        {"sentyderd", "Sentyderd"},
        {"defxy1", "Def Xy-1"},
        {"defxy2", "Def Xy-2"},
        {"defxy3", "Def Xy-3"},
        {"rock", "Rocks"},
        {SURVIVAL_ID, "Survival (Diệt 1000 địch)"},
        {PVP_ID, "PvP Cày Cấp (Cùng bạn bè)"},
        {DOM_ID, "PvP Chiếm Cứ"}
    };

    private static Fi getMapFile(String mapName) {
        try {
            mindustry.mod.Mods.LoadedMod mod = Vars.mods.getMod(MOD_NAME);
            if (mod != null && mod.root != null) {
                if (DOM_ID.equals(mapName)) {
                    Fi f1 = mod.root.child("file").child(mapName + ".msav");
                    if (f1.exists()) return f1;
                }
                Fi f = mod.root.child("maps").child(mapName + ".msav");
                if (f.exists()) return f;
                Fi f2 = mod.root.child("file").child(mapName + ".msav");
                if (f2.exists()) return f2;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String mapDisplayPath(String mapName) {
        return DOM_ID.equals(mapName) ? ("file/" + mapName + ".msav") : ("maps/" + mapName + ".msav");
    }

    /** {size, crc} hoặc null nếu không thấy file. */
    private static long[] mapSig(String mapName) {
        if (sigCache.containsKey(mapName)) return sigCache.get(mapName);
        long[] r = null;
        try {
            Fi f = getMapFile(mapName);
            if (f != null) {
                byte[] bytes = f.readBytes();
                CRC32 crc = new CRC32();
                crc.update(bytes, 0, bytes.length);
                r = new long[]{bytes.length, crc.getValue()};
            }
        } catch (Throwable e) {
            Log.err("Lỗi đọc file map " + mapName + ": " + e);
        }
        sigCache.put(mapName, r);
        return r;
    }

    private static String sigText(long[] sg) { return sg[0] + ":" + Long.toHexString(sg[1]); }

    private static SaveSlot getSlot(String mapId) {
        Seq<SaveSlot> slots = Vars.control.saves.getSaveSlots();
        return slots.find(s -> s.getName().equals(SLOT_PREFIX + mapId));
    }

    private static boolean isSlotValid(SaveSlot slot) {
        return slot != null && slot.file != null && slot.file.exists();
    }

    private static ModeHook hookFor(String id) {
        if (SURVIVAL_ID.equals(id)) return survival;
        if (PVP_ID.equals(id)) return pvp;
        if (DOM_ID.equals(id)) return domination;
        return null;
    }

    private static int menuTab = 0; // 0 = Tất cả, 1 = PvP & Survival, 2 = Chiến dịch

    private static void showNewModeDialog() {
        final MachineUi.Panel panel = MachineUi.panel("NewMode - Chọn Màn Chơi");
        panel.tabs(new String[]{"Tất cả", "PvP & Survival", "Chiến dịch"}, menuTab, i -> {
            menuTab = i;
            panel.rebuild();
        });
        panel.onBuild(() -> buildNewModePanel(panel));
        panel.show();
    }

    private static void buildNewModePanel(final MachineUi.Panel panel) {
        Table head = panel.header();
        Table content = panel.content();
        head.clear();
        content.clear();

        final float W = panel.innerW();
        final boolean narrow = W < 460f;
        int gachaPts = Core.settings.getInt(GACHA_POINTS_KEY, 0);
        int savedUnits = PvpDomination.loadSavedUnits().size();

        // Header cố định kiểu MachineUi (giống trang Nâng Cấp của NewMode PvP)
        Table hd = new Table(Styles.black6);
        hd.margin(8f);
        hd.add("[accent]CHỌN BẢN ĐỒ NEWMODE[]").left().expandX();
        hd.add("[yellow]Điểm Gacha: " + gachaPts + "[]").right().row();
        head.add(hd).width(W).pad(3f).row();

        for (String[] mapInfo : MAPS) {
            final String id = mapInfo[0], display = mapInfo[1];
            boolean isSpecial = SURVIVAL_ID.equals(id) || PVP_ID.equals(id) || DOM_ID.equals(id);
            if (menuTab == 1 && !isSpecial) continue;
            if (menuTab == 2 && isSpecial) continue;

            final SaveSlot slot = getSlot(id);
            final boolean hasSave = isSlotValid(slot);

            String tagCol = DOM_ID.equals(id) ? "[#38ef7d]● PvP Chiếm Cứ[]"
                : PVP_ID.equals(id) ? "[#ffd166]● PvP Cày Cấp[]"
                : SURVIVAL_ID.equals(id) ? "[#ff6b6b]● Survival[]"
                : "[#6ee7ff]● Chiến dịch[]";

            Table card = new Table(Styles.black8);
            card.margin(10f);

            Table top = new Table();
            top.add("[white]" + display + "[]").left().expandX();
            top.add(tagCol).right();
            card.add(top).growX().padBottom(4f).row();

            float descW = Math.max(160f, W - 28f);
            if (id.equals(SURVIVAL_ID) && survival != null) {
                card.add("[lightgray]Tiêu diệt đủ " + survival.killGoal() + " địch để thắng. Địch xuất hiện ngẫu nhiên khắp bản đồ theo từng đợt.[]")
                    .left().wrap().width(descW).padBottom(4f).row();
            } else if (id.equals(PVP_ID)) {
                card.add("[lightgray]Điều khiển unit riêng, diệt địch wave lấy EXP, nâng cấp & phá lõi địch cùng bạn bè.[]")
                    .left().wrap().width(descW).padBottom(4f).row();
            } else if (id.equals(DOM_ID)) {
                card.add("[lightgray]Dùng Unit đã lưu (" + savedUnits + "/" + PvpDomination.MAX_SAVED_SLOTS + " ô) chiếm các nền lõi (cứ điểm)! Lõi bất tử làm nơi spawn; phe nạp đầy thanh tiến độ trước phá hủy lõi địch.[]")
                    .left().wrap().width(descW).padBottom(4f).row();
            }

            long[] sg = mapSig(id);
            String pathHint = mapDisplayPath(id);
            String meta = (sg != null)
                ? ("[gray]" + pathHint + " • " + (long) Math.ceil(sg[0] / 1024.0) + " KB • #" + Long.toHexString(sg[1]) + "[]")
                : ("[scarlet]Không thấy file " + pathHint + " trong mod![]");
            String saveTxt = hasSave ? "[yellow]Có dữ liệu Save trận trước[]" : "[gray]Chưa có Save[]";
            card.add(meta + "   " + saveTxt).left().wrap().width(descW).padBottom(6f).row();

            Table btnRow = new Table();
            btnRow.left();
            float bh = narrow ? 38f : 42f;
            btnRow.button(hasSave ? "[#7cfc00]Tiếp Tục[]" : "[accent]Bắt Đầu[]", Styles.flatBordert, () -> {
                panel.hide();
                startNewModeMap(id);
            }).height(bh).minWidth(narrow ? 115f : 140f).padRight(6f);

            if (id.equals(DOM_ID)) {
                btnRow.button("Cài Đặt", Styles.flatBordert, PvpDomination::openSettingsDialog)
                    .height(bh).minWidth(narrow ? 86f : 105f).padRight(6f);
                btnRow.button("Kho Unit (" + savedUnits + "/" + PvpDomination.MAX_SAVED_SLOTS + ")", Styles.flatBordert, () -> {
                    panel.hide();
                    PvpDomination.openUnitManagerFromMenu();
                }).height(bh).minWidth(narrow ? 115f : 145f).padRight(6f);
            }

            if (hasSave) {
                btnRow.button("[scarlet]Xóa Save[]", Styles.flatBordert, () -> {
                    slot.delete();
                    if (PVP_ID.equals(id)) PvpDomination.onPvpSaveDeleted();
                    Vars.ui.showInfoToast("Đã xóa dữ liệu lưu của " + display + "!", 2.5f);
                    panel.rebuild();
                }).height(bh).minWidth(narrow ? 96f : 120f);
            }
            card.add(btnRow).left().row();

            content.add(card).width(W - 6f).pad(4f).row();
        }
    }

    private static void startNewModeMap(String mapName) {
        isPlayingNewMode = true;
        guest = false;
        activeMapName = mapName;
        hasWonPending = false;
        pendingGachaReward = 0;

        Core.settings.put("newex-is-playing-newmode", true);

        String slotName = SLOT_PREFIX + mapName;
        SaveSlot slot = getSlot(mapName);

        long[] sg = mapSig(mapName);
        String sigKey = MAP_SIG_KEY + mapName;

        // Save cũ chứa map cũ -> nếu file map trong mod đã đổi thì bỏ save, chơi lại từ map mới
        if (isSlotValid(slot) && sg != null) {
            String saved = Core.settings.getString(sigKey, "");
            if (!saved.equals(sigText(sg))) {
                if (saved.isEmpty() && !mapName.equals(PVP_ID)) {
                    Core.settings.put(sigKey, sigText(sg));
                } else {
                    try { slot.delete(); } catch (Throwable ignored) {}
                    slot = null;
                    Vars.ui.showInfoToast("[accent]Map đã được cập nhật - bỏ save cũ, bắt đầu trận mới!", 3f);
                }
            }
        }

        ModeHook hook = hookFor(mapName);

        if (isSlotValid(slot)) {
            try {
                slot.load();
                Vars.state.set(GameState.State.playing);
                Vars.ui.showInfoToast("Đã tải lại trận đấu NewMode thành công!", 2f);
                if (hook != null) hook.onResume();
                return;
            } catch (Throwable e) {
                Log.err("Lỗi load save NewMode, tạo lại trận mới: " + e);
            }
        }

        Fi mapFile = getMapFile(mapName);
        if (mapFile != null) {
            Map map;
            try {
                map = MapIO.createMap(mapFile, true);
            } catch (java.io.IOException ex) {
                Log.err("Lỗi đọc map " + mapName + ": " + ex);
                isPlayingNewMode = false;
                activeMapName = null;
                Core.settings.put("newex-is-playing-newmode", false);
                Vars.ui.showInfo("Không đọc được file " + mapDisplayPath(mapName) + ": " + ex.getMessage());
                return;
            }

            Vars.logic.reset();
            Vars.world.loadMap(map);
            Vars.state.rules = map.applyRules(Gamemode.survival);
            Vars.logic.play();

            if (hook != null) hook.onBegin();

            SaveSlot newSlot = Vars.control.saves.addSave(slotName);
            newSlot.save();

            if (sg != null) Core.settings.put(sigKey, sigText(sg));

            Vars.ui.showInfoToast("Đã khởi tạo màn chơi " + mapName + (sg != null ? " (#" + Long.toHexString(sg[1]) + ")" : "") + "!", 2f);
            return;
        }

        isPlayingNewMode = false;
        activeMapName = null;
        Core.settings.put("newex-is-playing-newmode", false);
        Vars.ui.showInfo("Không tìm thấy file " + mapDisplayPath(mapName) + " trong thư mục mod!");
    }

    /** Vào chế độ Huấn Luyện Unit Player (maps/huanluyenunitplayer.msav) để tạo bản sao lưu vào Kho Unit. */
    public static void startTrainingMap(int maxWaves, boolean dupTurret, int turretMode, boolean multiSave) {
        startTrainingMap(maxWaves, dupTurret, turretMode, multiSave, PvpDomination.getTrainGearSlots());
    }

    /** Vào chế độ Huấn Luyện Unit Player (maps/huanluyenunitplayer.msav) với tùy chỉnh số ô trang bị. */
    public static void startTrainingMap(int maxWaves, boolean dupTurret, int turretMode, boolean multiSave, int gearSlots) {
        isPlayingNewMode = false;
        guest = false;
        activeMapName = null;
        Core.settings.put("newex-is-playing-newmode", false);

        Fi mapFile = getMapFile(PvpDomination.TRAIN_MAP_ID);
        if (mapFile == null) {
            Vars.ui.showInfo("Không tìm thấy file maps/" + PvpDomination.TRAIN_MAP_ID + ".msav trong thư mục mod!");
            return;
        }
        Map map;
        try {
            map = MapIO.createMap(mapFile, true);
        } catch (java.io.IOException ex) {
            Log.err("Lỗi đọc map huấn luyện: " + ex);
            Vars.ui.showInfo("Không đọc được file maps/" + PvpDomination.TRAIN_MAP_ID + ".msav: " + ex.getMessage());
            return;
        }
        Vars.logic.reset();
        Vars.world.loadMap(map);
        Vars.state.rules = map.applyRules(Gamemode.survival);
        Vars.logic.play();
        PvpDomination.onBeginTraining(maxWaves, dupTurret, turretMode, multiSave, gearSlots);
    }

    private static void saveNewModeGame() {
        if (guest || !isPlayingNewMode || activeMapName == null || !Vars.state.isGame()) return;
        try {
            if (activeMapName.equals(SURVIVAL_ID) && survival != null) {
                try { survival.persistStore(); } catch (Throwable ignored) {}
            }
            // Ghi tiến trình PvP / PvP Chiếm Cứ vào rules.tags trước khi lưu
            if (activeMapName.equals(PVP_ID) && pvp != null) {
                try { pvp.persistStore(); } catch (Throwable ignored) {}
            }
            if (activeMapName.equals(DOM_ID) && domination != null) {
                try { domination.persistStore(); } catch (Throwable ignored) {}
            }
            SaveSlot slot = getSlot(activeMapName);
            if (slot == null) slot = Vars.control.saves.addSave(SLOT_PREFIX + activeMapName);
            slot.save();
            Vars.ui.showInfoToast("[accent]Đã tự động lưu trận NewMode![]", 2f);
        } catch (Throwable e) {
            Log.err("Lỗi Save NewMode: " + e);
        }
    }

    private static void deleteNewModeSave() {
        if (isPlayingNewMode && activeMapName != null) {
            SaveSlot slot = getSlot(activeMapName);
            if (slot != null) slot.delete();
        }
    }

    public static void init() {
        Events.on(GameOverEvent.class, event -> {
            if (!isPlayingNewMode && Vars.net.client() && survival != null && Survival.isActive()
                && event.winner != null && event.winner == Vars.state.rules.defaultTeam) {
                isPlayingNewMode = true; guest = true; activeMapName = SURVIVAL_ID;
            }
            if (!isPlayingNewMode) return;
            boolean isPvp = (PVP_ID.equals(activeMapName) && pvp != null) || (DOM_ID.equals(activeMapName) && domination != null);
            boolean wonNow = isPvp
                ? (event.winner != null && Vars.player != null && event.winner == Vars.player.team())
                : (event.winner != null && event.winner == Vars.state.rules.defaultTeam);
            if (wonNow) {
                hasWonPending = true;
                if (DOM_ID.equals(activeMapName) && domination != null) pendingGachaReward = domination.rewardPoints();
                else if (PVP_ID.equals(activeMapName) && pvp != null) pendingGachaReward = pvp.rewardPoints();
                else if ("Newbie".equals(activeMapName)) pendingGachaReward = 20;
                else if (SURVIVAL_ID.equals(activeMapName) && survival != null) pendingGachaReward = survival.rewardPoints();
                else pendingGachaReward = 50;
            }
            if (!guest) {
                if (PVP_ID.equals(activeMapName)) PvpDomination.onPvpSaveDeleted();
                deleteNewModeSave();
            }
        });

        Events.on(StateChangeEvent.class, event -> {
            if (event.from == GameState.State.playing && event.to == GameState.State.menu) {
                if (isPlayingNewMode) {
                    if (hasWonPending) {
                        int currentPts = Core.settings.getInt(GACHA_POINTS_KEY, 0) + pendingGachaReward;
                        Core.settings.put(GACHA_POINTS_KEY, currentPts);
                        String extra = "";
                        Result res = (SURVIVAL_ID.equals(activeMapName) && survival != null) ? survival.lastResult() : null;
                        if (res != null) {
                            extra = "\n\n[white]Đã diệt [scarlet]" + res.kills + "[white] địch • Đợt [cyan]" + res.wave + "[white] • Thời gian [cyan]" + res.time + "[]";
                        }
                        Vars.ui.showInfo("[accent]🎉 CHIẾN THẮNG NEWMODE! 🎉\n\n[white]Bạn đã vượt qua bản đồ [cyan]" + activeMapName + "[white] và nhận được [yellow]+" + pendingGachaReward + " điểm Gacha[white]!\n\nTổng số điểm hiện tại: [yellow]" + currentPts + " điểm[]" + extra);
                    } else {
                        saveNewModeGame();
                    }
                    isPlayingNewMode = false;
                    guest = false;
                    activeMapName = null;
                    hasWonPending = false;
                    pendingGachaReward = 0;
                }
                Core.settings.put("newex-is-playing-newmode", false);
            }
        });

        Events.on(WaveEvent.class, event -> {
            if (isPlayingNewMode && Vars.state.isGame()) {
                saveNewModeGame();

                if (Vars.state.wave > 0 && Vars.state.wave % 10 == 0) {
                    int currentPts = Core.settings.getInt(GACHA_POINTS_KEY, 0) + 1;
                    Core.settings.put(GACHA_POINTS_KEY, currentPts);
                    Vars.ui.showInfoToast("[accent]Đã qua đợt " + Vars.state.wave + "! Nhận +1 điểm Gacha (Tổng: " + currentPts + ")[ ]", 3f);
                }
            }
        });

        Events.on(ClientLoadEvent.class, event -> {
            Core.settings.put("newex-is-playing-newmode", false);
            try {
                Vars.ui.menufrag.addButton("NewMode", Icon.play, NewMode::showNewModeDialog);
            } catch (Throwable ignored) {}
        });
    }
}
