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
import mindustry.ui.dialogs.BaseDialog;

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

    static final String MOD_NAME = "newex";
    static final String GACHA_POINTS_KEY = "newex-gacha-points";
    static final String SURVIVAL_ID = "newmodesurvival";
    static final String PVP_ID = "pvpnewex3110";
    static final String MAP_SIG_KEY = "newex-newmode-mapsig-";
    static final String SLOT_PREFIX = "NewEx_NewMode_Save_";

    private static final java.util.HashMap<String, long[]> sigCache = new java.util.HashMap<>();

    private static String activeMapName = null;
    private static boolean isPlayingNewMode = false;
    private static int pendingGachaReward = 0;
    private static boolean hasWonPending = false;

    private static final String[][] MAPS = {
        {"Newbie", "Newbie"},
        {"redces", "Redces ᑈᐴᐾᐶᒅ"},
        {"sentyderd", "Sentyderd"},
        {"defxy1", "Def Xy-1"},
        {"defxy2", "Def Xy-2"},
        {"defxy3", "Def Xy-3"},
        {"rock", "Rocks"},
        {SURVIVAL_ID, "Survival (Diệt 1000 địch)"},
        {PVP_ID, "PvP Cày Cấp (Cùng bạn bè)"}
    };

    private static Fi getMapFile(String mapName) {
        try {
            mindustry.mod.Mods.LoadedMod mod = Vars.mods.getMod(MOD_NAME);
            if (mod != null && mod.root != null) {
                Fi f = mod.root.child("maps").child(mapName + ".msav");
                if (f.exists()) return f;
            }
        } catch (Throwable ignored) {}
        return null;
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
        return null;
    }

    private static void showNewModeDialog() {
        BaseDialog dialog = new BaseDialog("NewMode - Chọn Màn Chơi");
        dialog.setFillParent(true);

        Table content = dialog.cont;
        content.clear();
        content.add("[accent]CHỌN BẢN ĐỒ NEWMODE[]").pad(10f).get().setFontScale(1.2f);
        content.row();

        Table mapTable = new Table();
        mapTable.top().margin(10f);

        for (String[] mapInfo : MAPS) {
            final String id = mapInfo[0], display = mapInfo[1];
            final SaveSlot slot = getSlot(id);
            final boolean hasSave = isSlotValid(slot);

            Table card = new Table(Tex.button);
            card.margin(12f);

            card.add("[white]Map: " + display + "[]").left().row();
            if (id.equals(SURVIVAL_ID) && survival != null) {
                card.add("[lightgray]Tiêu diệt đủ " + survival.killGoal() + " địch để thắng. Địch xuất hiện ngẫu nhiên khắp bản đồ theo từng đợt.[]").left().padBottom(4f).row();
            }
            if (id.equals(PVP_ID)) {
                card.add("[lightgray]Điều khiển unit riêng, diệt địch wave để lấy EXP, về lõi nâng cấp rồi phá lõi đối phương. Bấm Host Game trong menu tạm dừng để mời bạn bè.[]").left().wrap().width(520f).padBottom(4f).row();
            }
            long[] sg = mapSig(id);
            if (sg != null) {
                card.add("[gray]File: maps/" + id + ".msav • " + (long) Math.ceil(sg[0] / 1024.0) + " KB • #" + Long.toHexString(sg[1]) + "[]").left().padBottom(4f).row();
            } else {
                card.add("[scarlet]Không thấy file maps/" + id + ".msav trong mod![]").left().padBottom(4f).row();
            }
            if (hasSave) {
                String titleText = "Save File";
                try { titleText = slot.getName(); } catch (Throwable ignored) {}
                card.add("[yellow]Có dữ liệu lưu từ trận trước (" + titleText + ")[]").left().padBottom(6f).row();
            } else {
                card.add("[gray]Màn chơi mới (Chưa có Save)[]").left().padBottom(6f).row();
            }

            card.button(hasSave ? "Tiếp Tục Chơi" : "Bắt Đầu Chơi", Styles.flatTogglet, () -> {
                dialog.hide();
                startNewModeMap(id);
            }).size(180f, 45f).pad(4f);

            if (hasSave) {
                card.button("Xóa Save & Chơi Mới", Styles.flatTogglet, () -> {
                    slot.delete();
                    Vars.ui.showInfo("Đã xóa dữ liệu lưu của " + display + "!");
                    dialog.hide();
                    showNewModeDialog();
                }).size(200f, 45f).pad(4f);
            }

            mapTable.add(card).growX().pad(6f).row();
        }

        content.add(new ScrollPane(mapTable)).grow().row();
        dialog.addCloseButton();
        dialog.show();
    }

    private static void startNewModeMap(String mapName) {
        isPlayingNewMode = true;
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
                Vars.ui.showInfo("Không đọc được file maps/" + mapName + ".msav: " + ex.getMessage());
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
        Vars.ui.showInfo("Không tìm thấy file maps/" + mapName + ".msav trong thư mục mod!");
    }

    private static void saveNewModeGame() {
        if (!isPlayingNewMode || activeMapName == null || !Vars.state.isGame()) return;
        try {
            // Ghi tiến trình PvP vào rules.tags trước khi lưu
            if (activeMapName.equals(PVP_ID) && pvp != null) {
                try { pvp.persistStore(); } catch (Throwable ignored) {}
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
            if (!isPlayingNewMode) return;
            boolean isPvp = PVP_ID.equals(activeMapName) && pvp != null;
            boolean wonNow = isPvp
                ? (event.winner != null && Vars.player != null && event.winner == Vars.player.team())
                : (event.winner != null && event.winner == Vars.state.rules.defaultTeam);
            if (wonNow) {
                hasWonPending = true;
                if (isPvp) pendingGachaReward = pvp.rewardPoints();
                else if ("Newbie".equals(activeMapName)) pendingGachaReward = 20;
                else if (SURVIVAL_ID.equals(activeMapName) && survival != null) pendingGachaReward = survival.rewardPoints();
                else pendingGachaReward = 50;
            }
            deleteNewModeSave();
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
