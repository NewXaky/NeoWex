package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Boolc;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.struct.StringMap;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import newex.MachineUi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Chế độ PvP Chiếm Cứ (Domination) + Hệ thống Lưu 5 Ô Unit từ PvP Cày Cấp (Cùng bạn bè):
 * 1) Trong PvP Cày Cấp (pvpnewex3110): đạt 10 phút HOẶC trang bị vật phẩm cấp hiếm nhất (Thần Thoại)
 *    sẽ hiện bảng hỏi lưu Unit hiện tại (Unit, Cấp, Chỉ số, Pilot, Pháo, Trang bị).
 *    - Nhấn Đồng ý: lưu vào 1 trong 5 ô lưu vĩnh viễn và tiếp tục chơi bình thường.
 *    - Nhấn Không: bảo lưu trong save map cho tới khi xóa save hoặc người chơi bấm nút [Lưu Unit] để đồng ý.
 * 2) Trong PvP Chiếm Cứ (file/pvpchiemcu.msav):
 *    - Vào trận hiện bảng chọn 1 trong 5 Unit đã lưu (nếu chưa lưu thì chỉ dùng Unit rỗng).
 *    - Khối Lõi bất tử (chỉ dùng để spawn & hồi máu), các cụm nền đặt lõi (core-zone) là Cứ Điểm cần chiếm.
 *    - Đứng trong vòng cứ điểm -> nạp vòng tròn theo màu phe; chiếm cứ điểm cộng điểm lên thanh tiến độ đường thẳng.
 *    - Phe nào nạp đầy thanh tiến độ (1000 điểm) -> kích nổ phá hủy toàn bộ Lõi đối phương và chiến thắng!
 */
public final class PvpDomination {
    private PvpDomination() {}

    public static final String MAP_ID = "pvpchiemcu";
    public static final String TRAIN_MAP_ID = "huanluyenunitplayer";
    public static final String TAG_DOM = "newex-pvp-dom";
    public static final String TAG_TRAIN = "newex-pvp-train";
    public static final String TAG_TRAIN_MAXW = "newex-train-maxw";
    public static final String TAG_TRAIN_MULTI = "newex-train-multi";
    public static final String TAG_TRAIN_TMODE = "newex-train-tmode";
    public static final String TAG_TRAIN_MULTISAVE = "newex-train-multisave";
    public static final String TAG_DOM_SCORES = "newex-dom-scores";
    public static final String TAG_DOM_PTS = "newex-dom-pts";
    public static final String TAG_SAVE_PROMPT = "newex-pvp-saveprompt";
    public static final String TAG_PLAY_TICKS = "newex-pvp-playticks";

    public static final String KEY_SAVED_UNITS = "newex-pvp-saved-units-v1";
    public static final String KEY_TRAIN_MAX_WAVES = "newex-train-max-waves";
    public static final String KEY_TRAIN_DUP_TURRET = "newex-train-dup-turret";
    public static final String KEY_TRAIN_TMODE = "newex-train-tmode";
    public static final String KEY_TRAIN_MULTI_SAVE = "newex-train-multi-save";
    public static final int DEFAULT_TRAIN_MAX_WAVES = 15;
    private static final String KEY_BAR_X = "newex-dom-bar-x";
    private static final String KEY_BAR_Y = "newex-dom-bar-y";
    private static final String KEY_SWAP_X = "newex-dom-swap-x";
    private static final String KEY_SWAP_Y = "newex-dom-swap-y";

    // Cài đặt riêng cho map PvP Chiếm Cứ (lưu Core.settings & đồng bộ qua rules.tags)
    public static final String KEY_CFG_RATE = "newex-dom-cfg-rate";           // % tốc độ tăng điểm mốc (10..1000, mặc định 100%)
    public static final String KEY_CFG_RESPAWN = "newex-dom-cfg-respawn";     // true = hồi sinh & đổi unit; false = mỗi unit 1 mạng, hết -> khán giả
    public static final String KEY_CFG_CD_ON = "newex-dom-cfg-cd-on";         // bật/tắt hồi chiêu chọn lại unit mới bị hạ
    public static final String KEY_CFG_CD_SEC = "newex-dom-cfg-cd-sec";       // thời gian hồi chiêu (giây, mặc định 60)
    private static final String TAG_CFG_RATE = "newex-dom-rate";
    private static final String TAG_CFG_RESPAWN = "newex-dom-respawn";
    private static final String TAG_CFG_CD_ON = "newex-dom-cd-on";
    private static final String TAG_CFG_CD_SEC = "newex-dom-cd-sec";

    public static final int MAX_SAVED_SLOTS = 5;
    public static final int EMPTY_SLOT_IDX = MAX_SAVED_SLOTS; // chỉ số 5 = Unit Rỗng
    public static final int SAVE_TRIGGER_MINUTES = 10;
    public static final float SAVE_TRIGGER_TICKS = SAVE_TRIGGER_MINUTES * 60f * 60f; // 36,000 ticks

    public static final float SCORE_GOAL = 1000f;
    public static final float PTS_PER_POINT_SEC = 6.5f;
    public static final float CAPTURE_SECONDS = 6.5f;
    public static final float CORE_HEAL_RADIUS = 165f;
    public static final int REWARD_POINTS = 35;

    static final String REQ_DOM_LOAD = "newex-dom-load";
    static final String REQ_DOM_SPAWN = "newex-dom-spawn";
    static final String SYNC_DOM_KIND = "newex-dom-sync";
    static final String FX_CAP_KIND = "newex-dom-capfx";
    static final String DEATH_DOM_KIND = "newex-dom-death";

    /** Unit hỗ trợ đi chiếm cứ được gọi bằng phím 4 (phe vàng) / Shift+4 (phe còn lại), di chuyển né tường như PvpBotAI. */
    static final class SupportUnit {
        Unit unit;
        Team team;
        float pathT = 0f, pathDx = Float.NaN, pathDy = Float.NaN, pathGx = Float.NaN, pathGy = Float.NaN;
        boolean pathWait = false;
    }
    static final ArrayList<SupportUnit> supportUnits = new ArrayList<>();
    static final Vec2 tmpGoal = new Vec2();
    static final SupportUnit soloTrack = new SupportUnit();
    static ArrayList<UnitType> validSpawnPool = null;

    /**
     * Lọc chuẩn unit spawn ngẫu nhiên cho MỌI chế độ:
     * né hoàn toàn thủy quân (naval), đạn là unit (MissileUnitType / lifetime ngắn / internal),
     * và nhóm unit cargo / tether / không có vũ khí.
     */
    public static boolean isValidRandomSpawnUnit(UnitType ut) {
        if (ut == null || ut.naval || ut.internal || ut.hidden || !ut.isEnemy || !ut.useUnitCap) return false;
        if (ut instanceof mindustry.type.unit.MissileUnitType) return false;
        if (ut.weapons == null || ut.weapons.isEmpty()) return false;
        String cn = ut.getClass().getSimpleName().toLowerCase();
        if (cn.contains("missile") || cn.contains("tether") || cn.contains("cargo")) return false;
        String nm = ut.name != null ? ut.name.toLowerCase() : "";
        if (nm.contains("cargo") || nm.contains("missile") || nm.contains("torpedo")
            || nm.contains("bullet") || nm.contains("projectile") || nm.contains("tether")) return false;
        return true;
    }

    private static long f1ArmUntil = 0L, f2ArmUntil = 0L;
    public static boolean isDomModKeyActive() {
        long now = Time.millis();
        return Core.input.keyDown(KeyCode.f1) || Core.input.keyDown(KeyCode.f2)
            || now <= f1ArmUntil || now <= f2ArmUntil;
    }

    private static UnitType pickRandomSupportType() {
        if (validSpawnPool == null || validSpawnPool.isEmpty()) {
            validSpawnPool = new ArrayList<>();
            for (UnitType ut : Vars.content.units()) {
                if (isValidRandomSpawnUnit(ut)) validSpawnPool.add(ut);
            }
        }
        if (validSpawnPool.isEmpty()) return null;
        return validSpawnPool.get(Mathf.random(validSpawnPool.size() - 1));
    }

    /** Trạng thái vòng đời Unit của từng player trong trận PvP Chiếm Cứ (hồi sinh / khán giả / hồi chiêu ô bị hạ). */
    static final class DomLife {
        int activeSlot = EMPTY_SLOT_IDX; // 0..4 = ô đã lưu, 5 = Unit Rỗng
        int deadMask = 0;                // bitmask các ô đã bị hạ vĩnh viễn (khi tắt hồi sinh)
        final float[] cdUntil = new float[MAX_SAVED_SLOTS + 1]; // Time.time hết hồi chiêu từng ô
        boolean waitingPick = false;     // đang chờ người chơi chọn unit khác (hoặc chờ hết CD)
        boolean spectating = false;      // hết sạch unit -> trạng thái khán giả xem trận chiến
    }
    static final HashMap<Integer, DomLife> lifeByPid = new HashMap<>();
    static final DomLife localLife = new DomLife();

    static DomLife lifeOf(int pid) {
        DomLife lf = lifeByPid.get(pid);
        if (lf == null) { lf = new DomLife(); lifeByPid.put(pid, lf); }
        return lf;
    }

    // ===== Đọc cài đặt PvP Chiếm Cứ (ưu tiên rules.tags trong trận để đồng bộ multiplayer) =====
    public static int scoreRatePct() {
        StringMap tg = NewModePvp.getTags();
        if (tg != null && tg.containsKey(TAG_CFG_RATE)) {
            try { return Mathf.clamp(Integer.parseInt(tg.get(TAG_CFG_RATE, "100")), 10, 1000); } catch (Throwable ignored) {}
        }
        return Mathf.clamp(Core.settings.getInt(KEY_CFG_RATE, 100), 10, 1000);
    }
    public static float effectivePtsPerSec() {
        return PTS_PER_POINT_SEC * (scoreRatePct() / 100f);
    }
    public static boolean allowRespawnAndSwap() {
        StringMap tg = NewModePvp.getTags();
        if (tg != null && tg.containsKey(TAG_CFG_RESPAWN)) return "1".equals(tg.get(TAG_CFG_RESPAWN, "1"));
        return Core.settings.getBool(KEY_CFG_RESPAWN, true);
    }
    public static boolean slotCooldownOn() {
        StringMap tg = NewModePvp.getTags();
        if (tg != null && tg.containsKey(TAG_CFG_CD_ON)) return "1".equals(tg.get(TAG_CFG_CD_ON, "0"));
        return Core.settings.getBool(KEY_CFG_CD_ON, false);
    }
    public static int slotCooldownSec() {
        StringMap tg = NewModePvp.getTags();
        if (tg != null && tg.containsKey(TAG_CFG_CD_SEC)) {
            try { return Mathf.clamp(Integer.parseInt(tg.get(TAG_CFG_CD_SEC, "60")), 5, 600); } catch (Throwable ignored) {}
        }
        return Mathf.clamp(Core.settings.getInt(KEY_CFG_CD_SEC, 60), 5, 600);
    }
    private static void stampSettingsTags(Rules r) {
        if (r == null || r.tags == null) return;
        r.tags.put(TAG_CFG_RATE, String.valueOf(Mathf.clamp(Core.settings.getInt(KEY_CFG_RATE, 100), 10, 1000)));
        r.tags.put(TAG_CFG_RESPAWN, Core.settings.getBool(KEY_CFG_RESPAWN, true) ? "1" : "0");
        r.tags.put(TAG_CFG_CD_ON, Core.settings.getBool(KEY_CFG_CD_ON, false) ? "1" : "0");
        r.tags.put(TAG_CFG_CD_SEC, String.valueOf(Mathf.clamp(Core.settings.getInt(KEY_CFG_CD_SEC, 60), 5, 600)));
    }

    /** Trang cài đặt riêng cho bản đồ PvP Chiếm Cứ (dùng MachineUi). */
    public static void openSettingsDialog() {
        if (Vars.headless) return;
        final MachineUi.Panel panel = MachineUi.panel(tr("Cài Đặt — PvP Chiếm Cứ", "Settings — PvP Domination"));
        final int[] rateVal = {Mathf.clamp(Core.settings.getInt(KEY_CFG_RATE, 100), 10, 1000)};
        final boolean[] respawnVal = {Core.settings.getBool(KEY_CFG_RESPAWN, true)};
        final boolean[] cdOnVal = {Core.settings.getBool(KEY_CFG_CD_ON, false)};
        final int[] cdSecVal = {Mathf.clamp(Core.settings.getInt(KEY_CFG_CD_SEC, 60), 5, 600)};
        final TextField[] rateRef = new TextField[1];
        final TextField[] cdRef = new TextField[1];

        panel.onBuild(() -> {
            Table c = panel.content();
            c.clear();
            c.margin(10f).top().left().defaults().left();
            float w = panel.innerW();

            c.add("[accent]" + tr("-- TÙY CHỈNH CHẾ ĐỘ PVP CHIẾM CỨ --", "-- PVP DOMINATION SETTINGS --") + "[]").padBottom(8f).row();

            // 1) Tốc độ tăng điểm mốc
            final Label ratePreview = new Label("");
            final Runnable updateRateLbl = () -> ratePreview.setText("[lightgray]" + tr("Tốc độ thực tế: ", "Actual rate: ")
                + "[#7cfc00]+" + Strings.fixed(PTS_PER_POINT_SEC * (rateVal[0] / 100f), 1) + tr(" điểm/giây mỗi cứ điểm", " pts/sec per point") + "[]");
            c.add(tr("Tốc độ tăng điểm mốc (%):", "Capture score speed (%):")).padTop(4f).row();
            Table rateRow = new Table();
            final TextField rateField = rateRow.field(String.valueOf(rateVal[0]), txt -> {
                try {
                    int v = Integer.parseInt(txt.trim());
                    rateVal[0] = Mathf.clamp(v, 10, 1000);
                } catch (Throwable ignored) {}
                updateRateLbl.run();
            }).width(110f).get();
            rateField.setFilter(TextField.TextFieldFilter.digitsOnly);
            rateField.setMaxLength(4);
            rateRef[0] = rateField;
            rateRow.add("[lightgray]% (10 - 1000, mặc định 100%)[]").padLeft(8f);
            c.add(rateRow).padTop(2f).row();
            updateRateLbl.run();
            c.add(ratePreview).padBottom(10f).row();

            // 2) Bật/tắt cho phép hồi sinh & dùng unit khác
            c.check(tr("Cho phép Unit player hồi sinh & dùng lại (Tắt: mỗi Unit 1 mạng, hết Unit -> Khán giả)",
                "Allow player Unit respawn & reuse (Off: 1 life per Unit, out of Units -> Spectator)"),
                respawnVal[0], (Boolc) b -> respawnVal[0] = b).width(Math.max(160f, w - 20f)).left().padBottom(8f).get().getLabel().setWrap(true);
            c.row();

            // 3) Bật/tắt thời gian hồi chiêu chọn lại Unit mới bị hạ + tùy chỉnh giây
            c.check(tr("Bật thời gian hồi chiêu khi chọn lại Unit mới bị hạ",
                "Enable cooldown before re-selecting a recently killed Unit"),
                cdOnVal[0], (Boolc) b -> cdOnVal[0] = b).width(Math.max(160f, w - 20f)).left().padBottom(4f).get().getLabel().setWrap(true);
            c.row();

            c.add(tr("Thời gian hồi chiêu chọn lại Unit bị hạ (giây):", "Dead Unit re-select cooldown (seconds):")).padTop(4f).row();
            Table cdRow = new Table();
            final TextField cdField = cdRow.field(String.valueOf(cdSecVal[0]), txt -> {
                try { cdSecVal[0] = Mathf.clamp(Integer.parseInt(txt.trim()), 5, 600); } catch (Throwable ignored) {}
            }).width(110f).get();
            cdField.setFilter(TextField.TextFieldFilter.digitsOnly);
            cdField.setMaxLength(3);
            cdRef[0] = cdField;
            cdRow.add("[lightgray]" + tr("giây (5 - 600, mặc định 60s)", "sec (5 - 600, default 60s)") + "[]").padLeft(8f);
            c.add(cdRow).padBottom(12f).row();

            c.add("[gray]" + tr("Lưu ý: Cài đặt áp dụng khi tạo trận PvP Chiếm Cứ mới (Host chia sẻ cho cả phòng).",
                "Note: Settings apply when starting a new PvP Domination match (Host syncs to all players).") + "[]")
                .wrap().width(Math.max(160f, w - 20f)).row();

            Table ft = panel.footer();
            ft.clear();
            ft.margin(4f);
            ft.button(tr("[#38ef7d]Lưu Cài Đặt[]", "[#38ef7d]Save Settings[]"), Styles.flatBordert, () -> {
                try { if (rateRef[0] != null) rateVal[0] = Mathf.clamp(Integer.parseInt(rateRef[0].getText().trim()), 10, 1000); } catch (Throwable ignored) {}
                try { if (cdRef[0] != null) cdSecVal[0] = Mathf.clamp(Integer.parseInt(cdRef[0].getText().trim()), 5, 600); } catch (Throwable ignored) {}
                Core.settings.put(KEY_CFG_RATE, rateVal[0]);
                Core.settings.put(KEY_CFG_RESPAWN, respawnVal[0]);
                Core.settings.put(KEY_CFG_CD_ON, cdOnVal[0]);
                Core.settings.put(KEY_CFG_CD_SEC, cdSecVal[0]);
                Vars.ui.showInfoToast(tr("[green]Đã lưu cài đặt PvP Chiếm Cứ![]", "[green]Saved PvP Domination settings![]"), 2.5f);
                panel.hide();
            }).height(44f).minWidth(170f).pad(4f);
            ft.button(tr("Đóng", "Close"), Styles.flatBordert, panel::hide).height(44f).minWidth(120f).pad(4f);
        });
        panel.show();
    }

    /** Server: kiểm tra player đang ở trạng thái Khán giả hoặc chờ chọn unit mới -> giữ không cho spawn unit. */
    static boolean isPlayerHeldOut(Player pl) {
        if (!isActive() || pl == null) return false;
        DomLife lf = lifeByPid.get(pl.id);
        if (lf == null) return false;
        if (lf.spectating) return true;
        if (lf.waitingPick) {
            // Nếu đang chờ hồi chiêu ô duy nhất của mình và hồi chiêu đã xong -> tự giải phóng cho hồi sinh
            if (allowRespawnAndSwap() && slotCooldownOn() && lf.activeSlot >= 0 && lf.activeSlot <= EMPTY_SLOT_IDX) {
                if (Time.time >= lf.cdUntil[lf.activeSlot]) {
                    lf.waitingPick = false;
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Server gọi khi unit của player bị hạ trong PvP Chiếm Cứ. */
    static void onPlayerUnitDeath(Player pl) {
        if (!isActive() || pl == null) return;
        DomLife lf = lifeOf(pl.id);
        int diedSlot = Mathf.clamp(lf.activeSlot, 0, EMPTY_SLOT_IDX);
        boolean respawnAllowed = allowRespawnAndSwap();
        boolean cdOn = slotCooldownOn();
        int cdSec = slotCooldownSec();

        if (!respawnAllowed) {
            // Đánh dấu ô vừa chết bị loại vĩnh viễn trong trận này
            lf.deadMask |= (1 << diedSlot);
            lf.waitingPick = true;
        } else if (cdOn) {
            // Đặt hồi chiêu cho ô vừa bị hạ
            lf.cdUntil[diedSlot] = Time.time + cdSec * 60f;
            lf.waitingPick = true;
        } else {
            // Hồi sinh bình thường sau 4 giây
            NewModePvp.startRespawnLock(pl, false);
            return;
        }

        // Gửi sự kiện tử vong cho chính player đó (nếu là host cục bộ -> gọi thẳng; nếu client -> gửi packet)
        String msg = pl.id + "|" + diedSlot + "|" + (respawnAllowed ? 1 : 0) + "|" + (cdOn ? cdSec : 0);
        if (Vars.player != null && pl == Vars.player) {
            handleLocalDeathNotice(diedSlot, respawnAllowed, cdOn ? cdSec : 0);
        } else if (Vars.net.server() && pl.con != null) {
            try { Call.clientPacketReliable(pl.con, DEATH_DOM_KIND, msg); } catch (Throwable ignored) {}
        }
    }

    private static void onDeathPacket(String raw) {
        if (Vars.headless || raw == null) return;
        try {
            String[] p = raw.split("\\|");
            if (p.length < 4) return;
            int pid = Integer.parseInt(p[0]);
            if (Vars.player == null || Vars.player.id != pid) return;
            int slot = Integer.parseInt(p[1]);
            boolean respawn = "1".equals(p[2]);
            int cdSec = Integer.parseInt(p[3]);
            handleLocalDeathNotice(slot, respawn, cdSec);
        } catch (Throwable ignored) {}
    }

    /** Client/Host cục bộ xử lý khi unit của mình bị hạ theo luật phòng (loại unit -> khán giả hoặc hồi chiêu ô). */
    private static void handleLocalDeathNotice(int diedSlot, boolean respawnAllowed, int cdSec) {
        if (Vars.headless) return;
        int slot = Mathf.clamp(diedSlot, 0, EMPTY_SLOT_IDX);
        if (!respawnAllowed) {
            localLife.deadMask |= (1 << slot);
            ArrayList<SavedUnit> saved = loadSavedUnits();
            boolean anyLeft = false;
            for (int i = 0; i < saved.size(); i++) {
                if ((localLife.deadMask & (1 << i)) == 0) { anyLeft = true; break; }
            }
            if (!anyLeft && (localLife.deadMask & (1 << EMPTY_SLOT_IDX)) == 0 && saved.isEmpty()) {
                anyLeft = false;
            } else if (!anyLeft && (localLife.deadMask & (1 << EMPTY_SLOT_IDX)) == 0) {
                anyLeft = true;
            }
            if (!anyLeft) {
                localLife.spectating = true;
                localLife.waitingPick = false;
                NewModePvp.send(REQ_DOM_LOAD, "SPEC");
                Vars.ui.showInfoToast(tr("[scarlet]Bạn đã hết Unit! Chuyển sang trạng thái Khán giả xem trận chiến.",
                    "[scarlet]No Units left! You are now Spectating the battle."), 5f);
                return;
            }
            localLife.waitingPick = true;
            Vars.ui.showInfoToast(tr("[scarlet]Unit vừa bị hạ đã bị loại! Hãy chọn Unit khác để tiếp tục.",
                "[scarlet]Your fallen Unit is eliminated! Choose another Unit."), 3.5f);
            Time.run(20f, () -> { if (isActive() && !localLife.spectating) openUnitPickerDialog(false); });
        } else if (cdSec > 0) {
            localLife.cdUntil[slot] = Time.time + cdSec * 60f;
            localLife.waitingPick = true;
            Vars.ui.showInfoToast(tr("[accent]Unit vừa bị hạ đang hồi chiêu " + cdSec + "s! Hãy chọn Unit khác hoặc đợi hết hồi chiêu.",
                "[accent]Fallen Unit is on " + cdSec + "s cooldown! Pick another Unit or wait."), 4f);
            Time.run(20f, () -> { if (isActive()) openUnitPickerDialog(true); });
        }
    }

    private static float localSlotCdLeft(int slotIdx) {
        if (slotIdx < 0 || slotIdx > EMPTY_SLOT_IDX) return 0f;
        return Math.max(0f, (localLife.cdUntil[slotIdx] - Time.time) / 60f);
    }
    private static boolean localSlotDead(int slotIdx) {
        if (allowRespawnAndSwap() || slotIdx < 0 || slotIdx > EMPTY_SLOT_IDX) return false;
        return (localLife.deadMask & (1 << slotIdx)) != 0;
    }

    // ===== Dữ liệu 1 Cứ Điểm (cụm nền core-zone) =====
    static final class CapPoint {
        int id;
        String label;
        float x, y, radius;
        int ownerTeamId = -1;       // -1 = trung lập
        int capTeamId = -1;         // phe đang nạp / chiếm
        float progress = 0f;        // 0..1
        boolean contested = false;
        int activeCount = 0;
        float flashT = 0f;
    }

    // ===== Dữ liệu 1 Ô Unit Đã Lưu (tối đa 5 ô) =====
    public static final class SavedUnit {
        public String name = "";
        public long savedAt = 0L;
        public int lv = 1, u = 0, tm = 0, pi = 0;
        public int[] s = new int[NewModePvp.STATS.length];
        public int[] plv = new int[NewModePvp.PILOT_COUNT];
        public int[] pexp = new int[NewModePvp.PILOT_COUNT];
        public ArrayList<Integer> pgift = new ArrayList<>();
        public ArrayList<String> d = new ArrayList<>();
        public int[] gEq = GearItems.newEq();
        public int gEqSrc = 0;
        public int[] gEqBonus = new int[GearItems.MAXSLOTS];
        public String ch = "";
        public int power = 0;
        public int maxWaves = 0;
    }

    /** Lưu vị trí lõi gốc của bản đồ PvP Chiếm Cứ để bảo đảm bất tử 100% (tự phục hồi kể cả bị skill gọi kill()/setAir()). */
    static final class SavedCore {
        int tx, ty;
        mindustry.world.Block block;
        Team team;
    }
    static final ArrayList<SavedCore> savedCores = new ArrayList<>();
    private static final HashMap<mindustry.world.Block, boolean[]> origCoreFlags = new HashMap<>();
    private static boolean corePassThroughApplied = false;

    /**
     * Đặt underBullets=true & targetable=false cho mọi CoreBlock trong PvP Chiếm Cứ:
     * - Đạn bay xuyên qua lõi (không va chạm đạn, không nổ trên lõi).
     * - Unit/AI địch không nhắm bắn vào lõi mà tập trung chiếm cứ điểm.
     * - Không cần bơm máu ảo! Tự khôi phục trạng thái gốc khi trận kết thúc hoặc sang map khác.
     */
    private static boolean coreBuildHooked = false;

    /**
     * Bọc CoreBuild cho mọi CoreBlock chuẩn để chặn 100% MỌI loại sát thương khi coresInvul()=true:
     * va chạm đạn, nổ lan, lửa/DoT mỗi giây, laser xuyên, damagePierce() lẫn kill() — không cần bơm máu ảo!
     */
    private static void hookCoreBuilds() {
        if (coreBuildHooked) return;
        coreBuildHooked = true;
        try {
            for (mindustry.world.Block b : Vars.content.blocks()) {
                if (!(b instanceof CoreBlock)) continue;
                final CoreBlock cb = (CoreBlock) b;
                // Chỉ bọc các CoreBlock dùng CoreBuild mặc định (an toàn 100% với lõi mod có subclass riêng)
                try {
                    Object sample = cb.buildType.get();
                    if (sample == null || sample.getClass() != CoreBlock.CoreBuild.class) continue;
                } catch (Throwable ignored) {
                    continue;
                }
                cb.buildType = () -> cb.new CoreBuild() {
                    @Override public boolean collide(Bullet other) {
                        if (PvpDomination.coresInvul()) return false;
                        return super.collide(other);
                    }
                    @Override public void damage(float amount) {
                        if (PvpDomination.coresInvul()) return;
                        super.damage(amount);
                    }
                    @Override public void damage(float amount, boolean withEffect) {
                        if (PvpDomination.coresInvul()) return;
                        super.damage(amount, withEffect);
                    }
                    @Override public void damage(Team source, float amount) {
                        if (PvpDomination.coresInvul()) return;
                        super.damage(source, amount);
                    }
                    @Override public void damageContinuous(float amount) {
                        if (PvpDomination.coresInvul()) return;
                        super.damageContinuous(amount);
                    }
                    @Override public void damageContinuousPierce(float amount) {
                        if (PvpDomination.coresInvul()) return;
                        super.damageContinuousPierce(amount);
                    }
                    @Override public void damagePierce(float amount, boolean withEffect) {
                        if (PvpDomination.coresInvul()) return;
                        super.damagePierce(amount, withEffect);
                    }
                    @Override public void kill() {
                        if (PvpDomination.coresInvul()) return;
                        super.kill();
                    }
                };
            }
        } catch (Throwable err) {
            Log.err("[PvpDomination] hookCoreBuilds failed", err);
        }
    }

    static void setCoresPassThrough(boolean enable) {
        if (corePassThroughApplied == enable && !origCoreFlags.isEmpty()) return;
        corePassThroughApplied = enable;
        try {
            for (mindustry.world.Block b : Vars.content.blocks()) {
                if (!(b instanceof CoreBlock)) continue;
                boolean[] orig = origCoreFlags.get(b);
                if (orig == null) {
                    orig = new boolean[]{b.underBullets, b.targetable};
                    origCoreFlags.put(b, orig);
                }
                if (enable) {
                    b.underBullets = true;
                    b.targetable = false;
                } else {
                    b.underBullets = orig[0];
                    b.targetable = orig[1];
                }
            }
        } catch (Throwable ignored) {}
    }

    static void recordMapCores() {
        savedCores.clear();
        for (Team tm : Team.all) {
            if (tm == Team.derelict) continue;
            Seq<CoreBlock.CoreBuild> cs = tm.cores();
            for (int i = 0; i < cs.size; i++) {
                CoreBlock.CoreBuild cb = cs.get(i);
                if (cb == null || !cb.isValid()) continue;
                SavedCore sc = new SavedCore();
                sc.tx = cb.tileX();
                sc.ty = cb.tileY();
                sc.block = cb.block;
                sc.team = tm;
                savedCores.add(sc);
            }
        }
    }

    static void ensureCoresAlive() {
        if (!coresInvul() || Vars.net.client() || Vars.world == null) return;
        if (savedCores.isEmpty()) recordMapCores();
        for (int i = 0; i < savedCores.size(); i++) {
            SavedCore sc = savedCores.get(i);
            Tile t = Vars.world.tile(sc.tx, sc.ty);
            if (t == null) continue;
            if (t.build instanceof CoreBlock.CoreBuild && t.build.team == sc.team && !t.build.dead) {
                t.build.health = t.build.maxHealth;
            } else {
                try {
                    t.setNet(sc.block, sc.team, 0);
                    if (t.build != null) t.build.health = t.build.maxHealth;
                } catch (Throwable ignored) {}
            }
        }
    }

    // ===== Trạng thái trận Chiếm Cứ =====
    static final ArrayList<CapPoint> points = new ArrayList<>();
    static final HashMap<Integer, Float> scores = new HashMap<>();
    static final HashMap<Integer, Integer> rateCount = new HashMap<>();
    static boolean matchEnded = false;
    static int winnerTeamId = -1;
    static int hostPid = -1;
    static float syncTimer = 0f;
    static float healTimer = 0f;
    static float botAiTimer = 0f;
    static boolean pickerShownThisMatch = false;
    static float pickerDelay = 0f;
    static MachineUi.Panel activePickerPanel = null;
    static BaseDialog activeSaveDialog = null;
    static Element domBarEl = null;
    static Table domSwapWrap = null;

    // ===== Trạng thái chế độ Huấn Luyện Unit Player (huanluyenunitplayer.msav) =====
    static final ArrayList<Tile> trainSpawnTiles = new ArrayList<>();
    static Unit trainUnit = null;
    static int trainWave = 0;
    static int trainMaxWaves = DEFAULT_TRAIN_MAX_WAVES;
    static float trainNextDelay = 0f;
    static boolean trainFinished = false;
    static boolean trainPromptShown = false;
    static boolean trainActiveCache = false;
    static boolean trainMultiCache = true;
    static int trainTmodeCache = 0;
    static ArrayList<UnitType> trainPoolCache = null;

    // Trạng thái chung
    static boolean savePromptOpen = false;
    static boolean initialized = false;

    // Bot AI nhẹ riêng cho PvP Chiếm Cứ khi chơi đơn (giúp test & đấu đơn hấp dẫn)
    static Unit soloBotUnit = null;
    static Team soloBotTeam = null;
    static float soloBotRespawn = 0f;
    static float soloBotFireCd = 0f;
    static final Vec2 tmpVec = new Vec2();

    // Hiệu ứng nổ sóng năng lượng khi chiếm thành công cứ điểm
    static final Effect capBurstFx = new Effect(45f, 360f, e -> {
        float fin = e.fin();
        float fout = e.fout();
        float r = e.rotation > 10f ? e.rotation : 90f;
        Lines.stroke(4.5f * fout);
        Draw.color(e.color, fout * 0.95f);
        Lines.circle(e.x, e.y, r * (0.25f + 0.95f * fin));
        Lines.stroke(2.2f * fout);
        Draw.color(Color.white, fout * 0.85f);
        Lines.circle(e.x, e.y, r * (0.12f + 0.72f * fin));
        Draw.color(e.color, fout * 0.25f);
        Fill.circle(e.x, e.y, r * (1f - fin * 0.35f));
        Draw.reset();
    });

    static String tr(String vi, String en) {
        return NewModePvp.tr(vi, en);
    }

    /** Kiểm tra hiện có đang ở trận PvP Chiếm Cứ không. */
    public static boolean isActive() {
        if (Vars.state == null || !Vars.state.isGame()) return false;
        StringMap tags = NewModePvp.getTags();
        if (tags != null && "1".equals(tags.get(TAG_DOM, "0"))) return true;
        try {
            if (Vars.state.map != null && Vars.state.map.file != null) {
                return MAP_ID.equalsIgnoreCase(Vars.state.map.file.nameWithoutExtension());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Kiểm tra hiện có đang ở chế độ Huấn Luyện Unit Player (huanluyenunitplayer.msav) không (O(1) cached). */
    public static boolean isTraining() {
        return trainActiveCache && Vars.state != null && Vars.state.isGame();
    }

    private static boolean detectTrainingMap() {
        if (Vars.state == null || !Vars.state.isGame()) return false;
        StringMap tags = NewModePvp.getTags();
        if (tags != null && "1".equals(tags.get(TAG_TRAIN, "0"))) return true;
        try {
            if (Vars.state.map != null && Vars.state.map.file != null) {
                return TRAIN_MAP_ID.equalsIgnoreCase(Vars.state.map.file.nameWithoutExtension());
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static int getTrainMaxWaves() {
        return Mathf.clamp(Core.settings.getInt(KEY_TRAIN_MAX_WAVES, DEFAULT_TRAIN_MAX_WAVES), 1, 200);
    }

    public static boolean trainDupTurretOn() {
        return Core.settings.getBool(KEY_TRAIN_DUP_TURRET, true);
    }

    public static int trainTurretMode() {
        return Core.settings.getInt(KEY_TRAIN_TMODE, 0) == 1 ? 1 : 0;
    }

    public static boolean trainMultiSaveOn() {
        StringMap tg = NewModePvp.getTags();
        if (isTraining() && tg != null && tg.containsKey(TAG_TRAIN_MULTISAVE)) {
            return "1".equals(tg.get(TAG_TRAIN_MULTISAVE, "0"));
        }
        return Core.settings.getBool(KEY_TRAIN_MULTI_SAVE, false);
    }

    public static int hostPlayerId() {
        if (!Vars.net.client() && Vars.player != null) return Vars.player.id;
        return hostPid;
    }

    /** Lõi có đang bất tử trong PvP Chiếm Cứ không (chỉ mất bất tử khi trận đã phân thắng bại). */
    public static boolean coresInvul() {
        return isActive() && !matchEnded;
    }

    public static boolean isMatchEnded() {
        return matchEnded;
    }

    static boolean isSoloBot(Unit u) {
        if (u == null) return false;
        if (soloBotUnit != null && u.id == soloBotUnit.id) return true;
        for (int i = 0; i < supportUnits.size(); i++) {
            SupportUnit su = supportUnits.get(i);
            if (su.unit != null && su.unit.id == u.id) return true;
        }
        return false;
    }

    // ===== Hook NewMode =====
    public static final NewMode.ModeHook HOOK = new NewMode.ModeHook() {
        @Override public int rewardPoints() { return REWARD_POINTS; }
        @Override public void onBegin() { PvpDomination.onBegin(); }
        @Override public void onResume() { PvpDomination.onResume(); }
        @Override public void persistStore() { PvpDomination.persistState(); }
    };

    public static void init() {
        if (initialized) return;
        initialized = true;
        NewMode.domination = HOOK;

        // Đăng ký gói nhận Unit đã lưu & lệnh phím tắt (F1+4 / F2+4 / F1+5) từ Client -> Host
        NewModePvp.onReq(REQ_DOM_LOAD, 250, PvpDomination::handleLoadUnitReq);
        NewModePvp.onReq(REQ_DOM_SPAWN, 30, PvpDomination::handleDomSpawnReq);
        Events.on(ContentInitEvent.class, e -> hookCoreBuilds());

        // Đăng ký gói nhận đồng bộ Cứ Điểm & Điểm từ Host -> Client
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    Vars.netClient.addPacketHandler(SYNC_DOM_KIND, PvpDomination::onSyncPacket);
                    Vars.netClient.addPacketHandler(FX_CAP_KIND, PvpDomination::onCapFxPacket);
                    Vars.netClient.addPacketHandler(DEATH_DOM_KIND, PvpDomination::onDeathPacket);
                } catch (Throwable err) {
                    Log.err("[PvpDomination] client packet handler failed", err);
                }
                addHudElements();
            });
            Events.run(Trigger.draw, PvpDomination::drawWorldFx);
            Events.run(Trigger.uiDrawEnd, PvpDomination::drawMinimapOverlays);
        }

        Events.on(BlockDestroyEvent.class, e -> {
            if (coresInvul() && !Vars.net.client() && e.tile != null && e.tile.block() instanceof CoreBlock) {
                Core.app.post(PvpDomination::ensureCoresAlive);
            }
        });
        Events.run(Trigger.update, PvpDomination::tick);
    }

    public static void onWorldLoad() {
        points.clear();
        savedCores.clear();
        scores.clear();
        rateCount.clear();
        supportUnits.clear();
        lifeByPid.clear();
        localLife.activeSlot = EMPTY_SLOT_IDX;
        localLife.deadMask = 0;
        Arrays.fill(localLife.cdUntil, 0f);
        localLife.waitingPick = false;
        localLife.spectating = false;
        matchEnded = false;
        winnerTeamId = -1;
        hostPid = (!Vars.net.client() && Vars.player != null) ? Vars.player.id : -1;
        trainSpawnTiles.clear();
        trainUnit = null;
        trainWave = 0;
        trainMaxWaves = getTrainMaxWaves();
        trainNextDelay = 0f;
        trainFinished = false;
        trainPromptShown = false;
        trainActiveCache = false;
        setCoresPassThrough(true);
        syncTimer = 0f;
        healTimer = 0f;
        botAiTimer = 0f;
        soloBotUnit = null;
        soloBotTeam = null;
        soloBotRespawn = 0f;
        pickerShownThisMatch = false;
        pickerDelay = 35f;
        if (activePickerPanel != null) { try { activePickerPanel.hide(); } catch (Throwable ignored) {} activePickerPanel = null; }
        if (activeSaveDialog != null) { try { activeSaveDialog.hide(); } catch (Throwable ignored) {} activeSaveDialog = null; }
        savePromptOpen = false;

        if (isActive()) {
            setCoresPassThrough(true);
            try { if (Vars.state != null && Vars.state.rules != null) NewModePvp.setf(Vars.state.rules, "possessionAllowed", true); } catch (Throwable ignored) {}
            scanCapturePoints();
            restoreStateFromTags();
            if (!Vars.net.client()) {
                Time.run(3f, () -> {
                    if (!Vars.state.isGame() || !isActive()) return;
                    StringMap tg = NewModePvp.getTags();
                    if (tg == null || !"1".equals(tg.get(TAG_DOM, "0"))) {
                        onBegin();
                    }
                });
            }
        } else if (detectTrainingMap()) {
            trainActiveCache = true;
            setCoresPassThrough(false);
            scanTrainingSpawns();
            StringMap tg = NewModePvp.getTags();
            if (tg != null) {
                if (tg.containsKey(TAG_TRAIN_MAXW)) {
                    try { trainMaxWaves = Mathf.clamp(Integer.parseInt(tg.get(TAG_TRAIN_MAXW, String.valueOf(DEFAULT_TRAIN_MAX_WAVES))), 1, 200); } catch (Throwable ignored) {}
                }
                trainMultiCache = "1".equals(tg.get(TAG_TRAIN_MULTI, trainDupTurretOn() ? "1" : "0"));
                try { trainTmodeCache = Integer.parseInt(tg.get(TAG_TRAIN_TMODE, String.valueOf(trainTurretMode()))) == 1 ? 1 : 0; } catch (Throwable ignored) { trainTmodeCache = 0; }
            }
        } else {
            trainActiveCache = false;
            setCoresPassThrough(false);
        }
    }

    /** Giữ lại để tương thích với NewMode khi xóa save. */
    public static void onPvpSaveDeleted() {
        savePromptOpen = false;
    }

    // ===== CHẾ ĐỘ HUẤN LUYỆN UNIT PLAYER (maps/huanluyenunitplayer.msav) =====
    private static boolean isMetalFloor4(Tile t) {
        if (t == null || t.floor() == null) return false;
        if (t.floor() == Blocks.metalFloor4) return true;
        String nm = t.floor().name;
        return nm != null && nm.equals("metal-floor-4");
    }

    private static void scanTrainingSpawns() {
        trainSpawnTiles.clear();
        if (Vars.world == null) return;
        int w = Vars.world.width(), h = Vars.world.height();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Tile t = Vars.world.tile(x, y);
                if (isMetalFloor4(t) && (t.block() == null || !t.block().solid)) {
                    trainSpawnTiles.add(t);
                }
            }
        }
    }

    private static UnitType pickRandomTrainingUnit() {
        if (trainPoolCache == null || trainPoolCache.isEmpty()) {
            trainPoolCache = new ArrayList<>();
            UnitType[] t1to5 = {
                mindustry.content.UnitTypes.dagger, mindustry.content.UnitTypes.mace, mindustry.content.UnitTypes.fortress, mindustry.content.UnitTypes.scepter, mindustry.content.UnitTypes.reign,
                mindustry.content.UnitTypes.nova, mindustry.content.UnitTypes.pulsar, mindustry.content.UnitTypes.quasar, mindustry.content.UnitTypes.vela, mindustry.content.UnitTypes.corvus,
                mindustry.content.UnitTypes.crawler, mindustry.content.UnitTypes.atrax, mindustry.content.UnitTypes.spiroct, mindustry.content.UnitTypes.arkyid, mindustry.content.UnitTypes.toxopid,
                mindustry.content.UnitTypes.flare, mindustry.content.UnitTypes.horizon, mindustry.content.UnitTypes.zenith, mindustry.content.UnitTypes.antumbra, mindustry.content.UnitTypes.eclipse,
                mindustry.content.UnitTypes.stell, mindustry.content.UnitTypes.locus, mindustry.content.UnitTypes.precept, mindustry.content.UnitTypes.vanquish, mindustry.content.UnitTypes.conquer,
                mindustry.content.UnitTypes.merui, mindustry.content.UnitTypes.cleroi, mindustry.content.UnitTypes.anthicus, mindustry.content.UnitTypes.tecta, mindustry.content.UnitTypes.collaris,
                mindustry.content.UnitTypes.elude, mindustry.content.UnitTypes.avert, mindustry.content.UnitTypes.obviate, mindustry.content.UnitTypes.quell, mindustry.content.UnitTypes.disrupt
            };
            for (UnitType ut : t1to5) {
                if (ut != null) trainPoolCache.add(ut);
            }
        }
        return trainPoolCache.get(Mathf.random(trainPoolCache.size() - 1));
    }

    public static void onBeginTraining(int maxWaves, boolean dupTurret, int turretMode, boolean multiSave) {
        int clamped = Mathf.clamp(maxWaves, 1, 200);
        int tm = turretMode == 1 ? 1 : 0;
        Core.settings.put(KEY_TRAIN_MAX_WAVES, clamped);
        Core.settings.put(KEY_TRAIN_DUP_TURRET, dupTurret);
        Core.settings.put(KEY_TRAIN_TMODE, tm);
        Core.settings.put(KEY_TRAIN_MULTI_SAVE, multiSave);
        NewModePvp.onBegin();
        Rules r = Vars.state.rules;
        r.tags.put(TAG_DOM, "0");
        r.tags.put(TAG_TRAIN, "1");
        r.tags.put(TAG_TRAIN_MAXW, String.valueOf(clamped));
        r.tags.put(TAG_TRAIN_MULTI, dupTurret ? "1" : "0");
        r.tags.put(TAG_TRAIN_TMODE, String.valueOf(tm));
        r.tags.put(TAG_TRAIN_MULTISAVE, multiSave ? "1" : "0");
        r.tags.put(ChaosMode.TAG, "0");
        r.tags.put(GearItems.TAG_SLOTS, String.valueOf(GearItems.MAXSLOTS));
        r.waves = false;
        r.waveTimer = false;
        r.attackMode = false;
        r.pvp = false;
        r.winWave = 0;
        r.waveTeam = Team.crux;
        r.unitCapVariable = false;
        r.unitCap = 999999;
        NewModePvp.setf(r, "possessionAllowed", true);
        r.spawns = new Seq<>();

        setCoresPassThrough(false);
        trainActiveCache = true;
        trainMultiCache = dupTurret;
        trainTmodeCache = tm;
        trainMaxWaves = clamped;
        trainWave = 0;
        trainUnit = null;
        trainFinished = false;
        trainPromptShown = false;
        trainNextDelay = 30f;
        scanTrainingSpawns();
        NewModePvp.syncRules();
        if (!Vars.headless) {
            Vars.ui.showInfoToast(tr("[#38ef7d]Huấn Luyện Unit Player: Hạ lần lượt " + clamped + " quái đợt (tỉ lệ rơi đồ 100%) để tạo bản sao lưu!",
                "[#38ef7d]Unit Training: Defeat " + clamped + " wave dummies (100% drop rate) to create a saved Unit!"), 4.5f);
        }
    }

    private static void spawnNextTrainingMonster() {
        if (!isTraining() || Vars.net.client() || trainFinished) return;
        if (trainSpawnTiles.isEmpty()) scanTrainingSpawns();
        UnitType ut = pickRandomTrainingUnit();
        if (ut == null) return;

        float sx, sy;
        if (!trainSpawnTiles.isEmpty()) {
            Tile t = trainSpawnTiles.get(Mathf.random(trainSpawnTiles.size() - 1));
            sx = t.worldx();
            sy = t.worldy();
        } else {
            sx = Vars.world.unitWidth() * 0.78f;
            sy = Vars.world.unitHeight() * 0.5f;
        }

        trainWave++;
        if (Vars.state != null) Vars.state.wave = trainWave;

        Team wt = Vars.state.rules.waveTeam != null ? Vars.state.rules.waveTeam : Team.crux;
        Unit u = ut.create(wt);
        u.set(sx, sy);
        try { u.controller(new NewModePvp.NoAI()); } catch (Throwable ignored) {}
        u.controlWeapons(false, false);
        u.add();
        trainUnit = u;
        NewModePvp.fx(sx, sy);

        if (!Vars.headless) {
            Vars.ui.showInfoToast(tr("[accent]Đợt " + trainWave + "/" + trainMaxWaves + ": []", "[accent]Wave " + trainWave + "/" + trainMaxWaves + ": []")
                + "[white]" + ut.localizedName + "[]", 2.2f);
        }
    }

    private static void tickTraining() {
        if (!isTraining() || Vars.net.client() || Vars.state.gameOver) return;
        if (trainUnit != null) {
            if (!trainUnit.isValid() || trainUnit.dead) {
                Unit dead = trainUnit;
                onTrainingUnitKilled(dead);
            } else {
                // Quái huấn luyện đứng yên trên nền kim loại 4 và KHÔNG tấn công
                trainUnit.controlWeapons(false, false);
                trainUnit.vel.setZero();
            }
            return;
        }
        if (trainFinished) return;
        trainNextDelay -= Time.delta;
        if (trainNextDelay <= 0f) {
            spawnNextTrainingMonster();
        }
    }

    public static void onTrainingUnitKilled(Unit u) {
        if (!isTraining() || Vars.net.client() || trainFinished) return;
        if (trainUnit != null && u != null && u.id != trainUnit.id) return;
        trainUnit = null;
        if (trainWave >= trainMaxWaves) {
            trainFinished = true;
            if (!trainPromptShown && !Vars.headless) {
                trainPromptShown = true;
                Time.run(25f, () -> {
                    if (isTraining()) {
                        openSaveUnitPromptDialog(
                            tr("Bạn đã hoàn thành huấn luyện " + trainMaxWaves + "/" + trainMaxWaves + " đợt!",
                               "You completed " + trainMaxWaves + "/" + trainMaxWaves + " training waves!"),
                            trainMaxWaves);
                    }
                });
            }
        } else {
            trainNextDelay = 30f;
        }
    }

    public static void onBegin() {
        NewModePvp.onBegin();
        Rules r = Vars.state.rules;
        r.tags.put(TAG_DOM, "1");
        stampSettingsTags(r);
        r.tags.put(ChaosMode.TAG, "0"); // Tắt Hỗn loạn để mọi trang bị GearItems lưu từ PvP Cày Cấp luôn hoạt động đầy đủ
        r.tags.put(GearItems.TAG_SLOTS, String.valueOf(GearItems.MAXSLOTS));
        r.waves = false;
        r.waveTimer = false;
        r.attackMode = false;
        r.pvp = true;
        r.winWave = 0;
        r.unitCapVariable = false;
        r.unitCap = 999999;
        NewModePvp.setf(r, "possessionAllowed", true);
        r.spawns = new Seq<>();

        points.clear();
        savedCores.clear();
        scores.clear();
        rateCount.clear();
        supportUnits.clear();
        lifeByPid.clear();
        localLife.activeSlot = EMPTY_SLOT_IDX;
        localLife.deadMask = 0;
        Arrays.fill(localLife.cdUntil, 0f);
        localLife.waitingPick = false;
        localLife.spectating = false;
        matchEnded = false;
        winnerTeamId = -1;
        setCoresPassThrough(true);
        soloBotUnit = null;
        soloBotTeam = null;
        soloBotRespawn = 90f;
        pickerShownThisMatch = false;
        pickerDelay = 25f;

        scanCapturePoints();
        Seq<Team> tl = NewModePvp.pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            scores.put(tl.get(i).id, 0f);
        }
        persistState();
        NewModePvp.syncRules();
        broadcastDomState();
        if (!Vars.headless) {
            Vars.ui.showInfoToast(tr("[accent]PvP Chiếm Cứ: Chiếm các nền lõi (cứ điểm) để nạp đầy thanh tiến độ!",
                "[accent]PvP Domination: Capture core-zone points to fill the progress bar!"), 4f);
        }
    }

    public static void onResume() {
        NewModePvp.onResume();
        Rules r = Vars.state.rules;
        r.tags.put(TAG_DOM, "1");
        r.waves = false;
        r.waveTimer = false;
        NewModePvp.setf(r, "possessionAllowed", true);
        scanCapturePoints();
        restoreStateFromTags();
        pickerShownThisMatch = false;
        pickerDelay = 25f;
        NewModePvp.syncRules();
        broadcastDomState();
    }

    // ===== Quét bản đồ tìm các cụm nền đặt lõi (core-zone) làm Cứ Điểm =====
    private static boolean isCoreZoneTile(Tile t) {
        if (t == null || t.floor() == null) return false;
        if (t.floor() == Blocks.coreZone) return true;
        String nm = t.floor().name;
        return nm != null && (nm.equals("core-zone") || nm.endsWith("-core-zone"));
    }

    private static boolean nearAnyCore(int tx, int ty) {
        for (Team tm : Team.all) {
            if (tm == Team.derelict || tm == Team.crux) continue;
            Seq<CoreBlock.CoreBuild> cs = tm.cores();
            for (int i = 0; i < cs.size; i++) {
                CoreBlock.CoreBuild cb = cs.get(i);
                if (cb == null || !cb.isValid()) continue;
                int half = cb.block.size / 2 + 2;
                if (Math.abs(tx - cb.tileX()) <= half && Math.abs(ty - cb.tileY()) <= half) return true;
            }
        }
        return false;
    }

    static void scanCapturePoints() {
        points.clear();
        if (Vars.world == null) return;
        int w = Vars.world.width(), h = Vars.world.height();
        if (w <= 0 || h <= 0) return;

        boolean[] visited = new boolean[w * h];
        IntSeq queue = new IntSeq(64);
        ArrayList<CapPoint> raw = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (visited[idx]) continue;
                Tile start = Vars.world.tile(x, y);
                if (!isCoreZoneTile(start)) continue;

                // BFS gom cụm nền đặt lõi liên thông (kể cả cách nhau 1 ô chéo)
                queue.clear();
                queue.add(idx);
                visited[idx] = true;
                int minX = x, maxX = x, minY = y, maxY = y;
                double sumX = 0, sumY = 0;
                int count = 0;
                boolean touchesCore = false;

                int head = 0;
                while (head < queue.size) {
                    int cur = queue.get(head++);
                    int cx = cur % w, cy = cur / w;
                    sumX += cx;
                    sumY += cy;
                    count++;
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;
                    if (nearAnyCore(cx, cy)) touchesCore = true;

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = cx + dx, ny = cy + dy;
                            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                            int nidx = ny * w + nx;
                            if (visited[nidx]) continue;
                            if (!isCoreZoneTile(Vars.world.tile(nx, ny))) continue;
                            visited[nidx] = true;
                            queue.add(nidx);
                        }
                    }
                }

                // Bỏ cụm nền đặt lõi nằm ngay dưới Lõi spawn của 2 phe
                if (touchesCore) continue;

                CapPoint cp = new CapPoint();
                cp.x = (float) ((sumX / count) * Vars.tilesize);
                cp.y = (float) ((sumY / count) * Vars.tilesize);
                float span = Math.max(maxX - minX + 1, maxY - minY + 1) * Vars.tilesize;
                cp.radius = Mathf.clamp(span * 0.55f + 42f, 74f, 152f);
                raw.add(cp);
            }
        }

        // Sắp xếp cố định từ trái sang phải, trên xuống dưới để tên A, B, C... đồng nhất giữa Host & Client
        raw.sort((a, b) -> {
            int dx = Float.compare(Math.round(a.x / 32f), Math.round(b.x / 32f));
            return dx != 0 ? dx : Float.compare(b.y, a.y);
        });

        for (int i = 0; i < raw.size(); i++) {
            CapPoint cp = raw.get(i);
            cp.id = i;
            cp.label = i < 26 ? String.valueOf((char) ('A' + i)) : ("P" + (i + 1));
            points.add(cp);
        }
    }

    // ===== Lưu / khôi phục trạng thái điểm & cứ điểm vào rules.tags =====
    public static void persistState() {
        NewModePvp.apiPersistStore();
        if (Vars.net.client()) return;
        StringMap tags = NewModePvp.getTags();
        if (tags == null) return;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (java.util.Map.Entry<Integer, Float> e : scores.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append(':').append(Strings.fixed(e.getValue(), 1));
        }
        tags.put(TAG_DOM_SCORES, sb.toString());

        StringBuilder pb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            if (i > 0) pb.append(';');
            pb.append(cp.ownerTeamId).append(',').append(cp.capTeamId).append(',').append(Strings.fixed(cp.progress, 3));
        }
        tags.put(TAG_DOM_PTS, pb.toString());
    }

    static void restoreStateFromTags() {
        StringMap tags = NewModePvp.getTags();
        if (tags == null) return;
        String sc = tags.get(TAG_DOM_SCORES, "");
        if (!sc.isEmpty()) {
            for (String seg : sc.split(",")) {
                String[] kv = seg.split(":");
                if (kv.length == 2) {
                    try { scores.put(Integer.parseInt(kv[0].trim()), Float.parseFloat(kv[1].trim())); }
                    catch (Throwable ignored) {}
                }
            }
        }
        String ps = tags.get(TAG_DOM_PTS, "");
        if (!ps.isEmpty()) {
            String[] arr = ps.split(";");
            for (int i = 0; i < arr.length && i < points.size(); i++) {
                String[] f = arr[i].split(",");
                if (f.length >= 3) {
                    try {
                        CapPoint cp = points.get(i);
                        cp.ownerTeamId = Integer.parseInt(f[0].trim());
                        cp.capTeamId = Integer.parseInt(f[1].trim());
                        cp.progress = Mathf.clamp(Float.parseFloat(f[2].trim()));
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    // ===== Vòng lặp chính =====
    private static void tick() {
        if (Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;

        // 1) Chế độ Huấn Luyện Unit Player (maps/huanluyenunitplayer.msav)
        if (isTraining()) {
            tickTraining();
            return;
        }

        if (!isActive()) return;

        // 2) Mở bảng chọn Unit đã lưu ngay khi vừa vào trận PvP Chiếm Cứ
        if (!Vars.headless && !pickerShownThisMatch && Vars.player != null && Vars.player.team() != Team.derelict) {
            pickerDelay -= Time.delta;
            if (pickerDelay <= 0f) {
                pickerShownThisMatch = true;
                openUnitPickerDialog(false);
            }
        }

        // Phím tắt trong PvP Chiếm Cứ: F1+4 = gọi unit hỗ trợ phe vàng (vô hạn), F2+4 = gọi unit hỗ trợ phe còn lại (vô hạn), F1+5 = reset toàn bộ cứ điểm
        // Hỗ trợ cả nhấn giữ đồng thời (F1+4) lẫn bấm nối tiếp nhanh trong 0.8s (F1 -> 4) cho mọi bàn phím laptop/PC!
        if (!Vars.headless && !Core.scene.hasKeyboard() && !Core.scene.hasDialog()) {
            long now = Time.millis();
            boolean f1Tap = Core.input.keyTap(KeyCode.f1);
            boolean f2Tap = Core.input.keyTap(KeyCode.f2);
            if (f1Tap) { f1ArmUntil = now + 800L; f2ArmUntil = 0L; }
            if (f2Tap) { f2ArmUntil = now + 800L; f1ArmUntil = 0L; }

            boolean f1 = Core.input.keyDown(KeyCode.f1) || now <= f1ArmUntil;
            boolean f2 = Core.input.keyDown(KeyCode.f2) || now <= f2ArmUntil;
            boolean tap4 = Core.input.keyTap(KeyCode.num4) || Core.input.keyTap(KeyCode.numpad4);
            boolean tap5 = Core.input.keyTap(KeyCode.num5) || Core.input.keyTap(KeyCode.numpad5);
            boolean down4 = Core.input.keyDown(KeyCode.num4) || Core.input.keyDown(KeyCode.numpad4);
            boolean down5 = Core.input.keyDown(KeyCode.num5) || Core.input.keyDown(KeyCode.numpad5);

            if ((f1 && tap4) || (f1Tap && down4)) {
                f1ArmUntil = now + 800L;
                NewModePvp.send(REQ_DOM_SPAWN, "0");
            } else if ((f2 && tap4) || (f2Tap && down4)) {
                f2ArmUntil = now + 800L;
                NewModePvp.send(REQ_DOM_SPAWN, "1");
            } else if ((f1 && tap5) || (f1Tap && down5)) {
                f1ArmUntil = 0L;
                NewModePvp.send(REQ_DOM_SPAWN, "R");
            }
        }

        // Giảm hiệu ứng flash trên các cứ điểm
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            if (cp.flashT > 0f) cp.flashT = Math.max(0f, cp.flashT - Time.delta);
        }

        if (Vars.net.client()) return;

        // Server / Host cập nhật logic bảo vệ lõi bất tử, chiếm cứ điểm, hồi máu tại lõi, điểm số, unit hỗ trợ và bot solo
        if (points.isEmpty()) scanCapturePoints();
        ensureCoresAlive();
        tickCoreFountain();
        tickSoloBot();
        tickSupportUnits();
        tickCaptureAndScore();

        syncTimer -= Time.delta;
        if (syncTimer <= 0f) {
            syncTimer = 10f;
            broadcastDomState();
        }
    }

    /** Hồi máu cho unit đồng minh đứng gần Lõi spawn bất tử (mỗi giây hồi 1 lần để tránh lag host). */
    private static void tickCoreFountain() {
        healTimer -= Time.delta;
        if (healTimer > 0f) return;
        healTimer = 60f;
        float dtSec = 1f;
        Seq<Team> tl = NewModePvp.pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            Team tm = tl.get(i);
            CoreBlock.CoreBuild cb = tm.core();
            if (cb == null || !cb.isValid()) continue;
            Groups.unit.intersect(cb.x - CORE_HEAL_RADIUS, cb.y - CORE_HEAL_RADIUS, CORE_HEAL_RADIUS * 2f, CORE_HEAL_RADIUS * 2f, u -> {
                if (u == null || u.dead || u.team != tm) return;
                if (Mathf.within(u.x, u.y, cb.x, cb.y, CORE_HEAL_RADIUS)) {
                    if (u.health < u.maxHealth) {
                        u.heal(u.maxHealth * 0.10f * dtSec);
                    }
                }
            });
        }
    }

    /** Reset toàn bộ cứ điểm về trung lập (gọi được từ phím F1+5 hoặc TikTokBridge). */
    static void resetAllCapturePoints() {
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            cp.ownerTeamId = -1;
            cp.capTeamId = -1;
            cp.progress = 0f;
            cp.contested = false;
            cp.activeCount = 0;
            cp.flashT = 25f;
        }
        rateCount.clear();
        broadcastDomState();
    }

    /** Spawn 1 unit hỗ trợ đi chiếm cứ vô hạn cho phe vàng (otherTeam=false) hoặc phe còn lại (otherTeam=true). */
    static Unit spawnSupportUnit(boolean otherTeam, UnitType forcedType) {
        if (!isActive() || Vars.net.client()) return null;
        Seq<Team> tl = NewModePvp.pvpTeams();
        if (tl.isEmpty()) return null;
        Team yellow = tl.contains(Team.sharded) ? Team.sharded : tl.get(0);
        Team other = yellow;
        for (int i = 0; i < tl.size; i++) {
            if (tl.get(i) != yellow && tl.get(i).cores().size > 0) { other = tl.get(i); break; }
        }
        if (other == yellow && Team.crux.cores().size > 0) other = Team.crux;
        Team targetTeam = otherTeam ? other : yellow;
        CoreBlock.CoreBuild core = targetTeam.core();
        if (core == null || !core.isValid()) return null;

        UnitType ut = forcedType != null ? forcedType : pickRandomSupportType();
        if (ut == null) return null;
        // Bảo đảm spawn vô hạn không bị giới hạn unitCap của lõi
        if (Vars.state != null && Vars.state.rules != null && Vars.state.rules.unitCap < 999999) {
            Vars.state.rules.unitCapVariable = false;
            Vars.state.rules.unitCap = 999999;
        }
        Unit u = ut.create(targetTeam);
        float ang = Mathf.random(360f);
        float rad = core.block.size * Vars.tilesize * 0.65f + u.hitSize * 0.6f + 8f;
        u.set(core.x + Mathf.cosDeg(ang) * rad, core.y + Mathf.sinDeg(ang) * rad);
        try { u.controller(new NewModePvp.NoAI()); } catch (Throwable ignored) {}
        u.add();
        NewModePvp.fx(u.x, u.y);

        SupportUnit su = new SupportUnit();
        su.unit = u;
        su.team = targetTeam;
        supportUnits.add(su);
        return u;
    }

    /** Xử lý phím F1+4 (phe vàng), F2+4 (phe còn lại) và phím F1+5 (reset toàn bộ cứ điểm). */
    private static void handleDomSpawnReq(Player pl, String payload) {
        if (!isActive() || Vars.net.client()) return;
        if ("R".equals(payload)) {
            resetAllCapturePoints();
            NewModePvp.toastAll(tr("[accent]Đã reset toàn bộ cứ điểm về chưa phe nào chiếm!",
                "[accent]All capture points have been reset to neutral!"), 3f);
            return;
        }

        Unit u = spawnSupportUnit("1".equals(payload), null);
        if (u == null) return;
        TikTokBridge.tagIfRecentDonor(u);

        NewModePvp.toastAll("[#" + u.team.color + "]■ " + u.team.localized() + "[] "
            + tr("gọi hỗ trợ chiếm cứ: ", "deployed capture support: ") + "[accent]" + u.type.localizedName + "[]", 2.2f);
    }

    /** Di chuyển thông minh né tường (dùng PvpBotAI.pathQuery cho bộ binh) hướng đến cứ điểm và chiến đấu. */
    private static void driveCaptureUnit(SupportUnit trk, Unit u, Team tm) {
        if (u == null || !u.isValid() || u.dead || points.isEmpty()) return;

        CapPoint target = null;
        float bestScore = -1e9f;
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            if (cp.ownerTeamId == tm.id && cp.progress >= 0.99f && !cp.contested) continue;
            float dist = Mathf.dst(u.x, u.y, cp.x, cp.y);
            float prio = (cp.ownerTeamId == -1 ? 420f : (cp.contested ? 500f : 280f)) - dist * 0.32f;
            if (prio > bestScore) { bestScore = prio; target = cp; }
        }
        if (target == null) target = points.get(Math.abs(u.id) % points.size());

        float dist = Mathf.dst(u.x, u.y, target.x, target.y);
        if (dist > target.radius * 0.48f) {
            if (!u.type.flying) {
                // Bộ binh: dùng thuật toán tìm đường né tường PvpBotAI.pathQuery
                trk.pathT -= Time.delta;
                boolean need = trk.pathT <= 0f || Float.isNaN(trk.pathDx)
                    || Math.abs(target.x - trk.pathGx) > 24f || Math.abs(target.y - trk.pathGy) > 24f;
                if (need) {
                    trk.pathT = 6f;
                    trk.pathGx = target.x; trk.pathGy = target.y;
                    trk.pathWait = false;
                    trk.pathDx = target.x; trk.pathDy = target.y;
                    tmpGoal.set(target.x, target.y);
                    PvpBotAI.PathRes pr = PvpBotAI.pathQuery(u, tmpGoal);
                    if (pr != null) {
                        if (pr.move) { trk.pathDx = pr.dx; trk.pathDy = pr.dy; }
                        else if (!pr.unreachable) trk.pathWait = true;
                    }
                }
                if (trk.pathWait) {
                    tmpVec.setZero();
                    u.movePref(tmpVec);
                } else {
                    tmpVec.set(trk.pathDx - u.x, trk.pathDy - u.y);
                    if (tmpVec.len() > 0.1f) tmpVec.setLength(u.speed());
                    else tmpVec.setZero();
                    u.movePref(tmpVec);
                    if (!u.isShooting() && tmpVec.len2() > 0.01f) u.lookAt(tmpVec.angle());
                }
            } else {
                tmpVec.set(target.x - u.x, target.y - u.y).setLength(u.speed());
                u.movePref(tmpVec);
                if (!u.isShooting() && tmpVec.len2() > 0.01f) u.lookAt(tmpVec.angle());
            }
        } else {
            float ang = Time.time * 1.3f + (u.id % 13) * 28f;
            float tx = target.x + Mathf.cosDeg(ang) * target.radius * 0.34f;
            float ty = target.y + Mathf.sinDeg(ang) * target.radius * 0.34f;
            tmpVec.set(tx - u.x, ty - u.y).limit(u.speed() * 0.55f);
            u.movePref(tmpVec);
        }

        Unit foe = Units.closestEnemy(tm, u.x, u.y, Math.max(120f, u.range() * 1.15f), e -> e != null && !e.dead);
        if (foe != null) {
            u.aimLook(foe.x, foe.y);
            u.controlWeapons(true, true);
        } else {
            u.controlWeapons(false, false);
        }
    }

    private static void tickSupportUnits() {
        for (int i = supportUnits.size() - 1; i >= 0; i--) {
            SupportUnit su = supportUnits.get(i);
            if (su.unit == null || !su.unit.isValid() || su.unit.dead) {
                supportUnits.remove(i);
                continue;
            }
            driveCaptureUnit(su, su.unit, su.team);
        }
    }

    /** Nếu người chơi chơi đơn (không có player người thật ở phe địch), tự điều khiển 1 Unit bot phe địch ra chiếm cứ điểm. */
    private static void tickSoloBot() {
        if (matchEnded || Vars.state.gameOver || points.isEmpty()) return;
        Seq<Team> tl = NewModePvp.pvpTeams();
        if (tl.size < 2) return;
        Team humanTeam = Vars.player != null ? Vars.player.team() : tl.get(0);
        Team enemyTeam = null;
        for (int i = 0; i < tl.size; i++) {
            if (tl.get(i) != humanTeam && tl.get(i).cores().size > 0) {
                enemyTeam = tl.get(i);
                break;
            }
        }
        if (enemyTeam == null) return;

        // Nếu đã có người chơi thật ở phe địch -> hủy bot solo
        boolean enemyHasHuman = false;
        for (Player p : Groups.player) {
            if (p != null && p.team() == enemyTeam) { enemyHasHuman = true; break; }
        }
        if (enemyHasHuman) {
            if (soloBotUnit != null && soloBotUnit.isValid()) {
                try { Call.unitDespawn(soloBotUnit); } catch (Throwable ignored) { soloBotUnit.remove(); }
            }
            soloBotUnit = null;
            return;
        }

        soloBotTeam = enemyTeam;
        if (soloBotUnit == null || !soloBotUnit.isValid() || soloBotUnit.dead) {
            soloBotUnit = null;
            soloBotRespawn -= Time.delta;
            if (soloBotRespawn <= 0f) {
                CoreBlock.CoreBuild ec = enemyTeam.core();
                if (ec != null && ec.isValid()) {
                    // Chọn sức mạnh bot cân bằng theo unit người chơi đang chọn
                    NewModePvp.St hSt = Vars.player != null ? NewModePvp.byId.get(Vars.player.id) : null;
                    int tier = hSt != null ? NewModePvp.activeTier(hSt) : 1;
                    UnitType ut = NewModePvp.tierType(Math.min(5, Math.max(1, tier)));
                    Unit bu = ut.create(enemyTeam);
                    bu.set(ec.x, ec.y);
                    float scale = hSt != null ? (1f + Math.min(20, hSt.lv) * 0.045f) : 1f;
                    bu.maxHealth = ut.health * scale;
                    bu.health = bu.maxHealth;
                    bu.add();
                    soloBotUnit = bu;
                    soloBotRespawn = 5f * 60f;
                    NewModePvp.fx(ec.x, ec.y);
                }
            }
            return;
        }

        // Điều khiển bot solo né tường thông minh hướng đến cứ điểm & chiến đấu
        driveCaptureUnit(soloTrack, soloBotUnit, enemyTeam);
    }

    /** Cập nhật tiến độ chiếm các cứ điểm & cộng điểm lên thanh tiến độ trận đấu. */
    private static void tickCaptureAndScore() {
        if (matchEnded || Vars.state.gameOver) return;

        float dtSec = Time.delta / 60f;
        float capRateBase = dtSec / CAPTURE_SECONDS;
        rateCount.clear();

        Seq<Team> tl = NewModePvp.pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            if (!scores.containsKey(tl.get(i).id)) scores.put(tl.get(i).id, 0f);
        }

        HashMap<Integer, Integer> teamUnits = new HashMap<>();

        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            teamUnits.clear();

            // Đếm các player unit (và bot solo) trong phạm vi cứ điểm
            for (Player pl : Groups.player) {
                if (pl == null || pl.team() == Team.derelict || pl.team() == Team.crux) continue;
                Unit u = pl.unit();
                if (u == null || u.dead || !u.isValid()) continue;
                if (Mathf.within(u.x, u.y, cp.x, cp.y, cp.radius)) {
                    teamUnits.merge(pl.team().id, 1, Integer::sum);
                }
            }
            if (soloBotUnit != null && soloBotUnit.isValid() && !soloBotUnit.dead && soloBotTeam != null) {
                if (Mathf.within(soloBotUnit.x, soloBotUnit.y, cp.x, cp.y, cp.radius)) {
                    teamUnits.merge(soloBotTeam.id, 1, Integer::sum);
                }
            }
            for (int si = 0; si < supportUnits.size(); si++) {
                SupportUnit su = supportUnits.get(si);
                if (su.unit != null && su.unit.isValid() && !su.unit.dead && su.team != null) {
                    if (Mathf.within(su.unit.x, su.unit.y, cp.x, cp.y, cp.radius)) {
                        teamUnits.merge(su.team.id, 1, Integer::sum);
                    }
                }
            }

            if (teamUnits.size() >= 2) {
                cp.contested = true;
                cp.activeCount = teamUnits.size();
            } else if (teamUnits.size() == 1) {
                cp.contested = false;
                java.util.Map.Entry<Integer, Integer> entry = teamUnits.entrySet().iterator().next();
                int tid = entry.getKey();
                int cnt = entry.getValue();
                cp.activeCount = cnt;
                float mult = 1f + 0.35f * Math.max(0, cnt - 1);
                float delta = capRateBase * mult;

                if (cp.ownerTeamId == tid) {
                    // Đang thuộc phe mình: nạp đầy lại nếu bị hạ dở
                    cp.capTeamId = tid;
                    cp.progress = Math.min(1f, cp.progress + delta * 1.2f);
                } else if (cp.ownerTeamId >= 0) {
                    // Đang thuộc phe địch: hạ tiến độ phe địch xuống 0 trước
                    cp.progress -= delta * 1.25f;
                    if (cp.progress <= 0f) {
                        cp.progress = 0f;
                        cp.ownerTeamId = -1;
                        cp.capTeamId = tid;
                    }
                } else {
                    // Đang trung lập
                    if (cp.capTeamId >= 0 && cp.capTeamId != tid && cp.progress > 0f) {
                        cp.progress -= delta * 1.25f;
                        if (cp.progress <= 0f) {
                            cp.progress = 0f;
                            cp.capTeamId = tid;
                        }
                    } else {
                        cp.capTeamId = tid;
                        cp.progress += delta;
                        if (cp.progress >= 1f) {
                            cp.progress = 1f;
                            cp.ownerTeamId = tid;
                            cp.flashT = 35f;
                            onPointCaptured(cp, Team.get(tid));
                        }
                    }
                }
            } else {
                cp.contested = false;
                cp.activeCount = 0;
                if (cp.ownerTeamId >= 0 && cp.progress < 1f) {
                    cp.progress = Math.min(1f, cp.progress + capRateBase * 0.25f);
                } else if (cp.ownerTeamId < 0 && cp.progress > 0f) {
                    cp.progress = Math.max(0f, cp.progress - capRateBase * 0.35f);
                    if (cp.progress <= 0.001f) { cp.progress = 0f; cp.capTeamId = -1; }
                }
            }

            if (cp.ownerTeamId >= 0) {
                rateCount.merge(cp.ownerTeamId, 1, Integer::sum);
                float ns = scores.getOrDefault(cp.ownerTeamId, 0f) + effectivePtsPerSec() * dtSec;
                scores.put(cp.ownerTeamId, Math.min(SCORE_GOAL, ns));
                if (ns >= SCORE_GOAL && !matchEnded) {
                    triggerDominationVictory(Team.get(cp.ownerTeamId));
                    return;
                }
            }
        }
    }

    private static void onPointCaptured(CapPoint cp, Team team) {
        if (team == null) return;
        playCapFx(cp.x, cp.y, cp.radius, team.id);
        if (Vars.net.server()) {
            try { Call.clientPacketReliable(FX_CAP_KIND, Math.round(cp.x) + "|" + Math.round(cp.y) + "|" + Math.round(cp.radius) + "|" + team.id); }
            catch (Throwable ignored) {}
        }
        String msg = "[#" + team.color + "]■ " + team.localized() + "[] " + tr("đã chiếm cứ điểm ", "captured point ") + "[accent]" + cp.label + "[]!";
        NewModePvp.toastAll(msg, 3f);
        broadcastDomState();
    }

    private static void playCapFx(float x, float y, float r, int teamId) {
        if (Vars.headless) return;
        try {
            Team t = Team.get(teamId);
            Color col = t != null ? t.color : Color.cyan;
            capBurstFx.at(x, y, r, col);
        } catch (Throwable ignored) {}
    }

    private static void onCapFxPacket(String data) {
        try {
            String[] p = data.split("\\|");
            if (p.length >= 4) {
                playCapFx(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Integer.parseInt(p[3]));
            }
        } catch (Throwable ignored) {}
    }

    /** Phe thắng đã nạp đầy thanh tiến độ -> hủy toàn bộ Lõi đối phương! */
    private static void triggerDominationVictory(Team winner) {
        if (matchEnded || winner == null) return;
        matchEnded = true;
        winnerTeamId = winner.id;
        setCoresPassThrough(false);
        scores.put(winner.id, SCORE_GOAL);
        broadcastDomState();

        String banner = "[#" + winner.color + "]⚡ " + winner.localized() + "[] " +
            tr("đã nạp đầy thanh Chiếm Cứ! Lõi đối phương đang bị phá hủy!",
               "filled the Domination bar! Enemy cores are being destroyed!");
        NewModePvp.toastAll(banner, 5f);

        // Phá hủy toàn bộ lõi của các phe đối phương (kể cả Team.crux nếu có) bằng chuỗi nổ lõi của NewModePvp
        boolean destroyedAny = false;
        for (Team tm : Team.all) {
            if (tm == winner || tm == Team.derelict) continue;
            Seq<CoreBlock.CoreBuild> cs = tm.cores().copy();
            for (int j = 0; j < cs.size; j++) {
                CoreBlock.CoreBuild cb = cs.get(j);
                if (cb != null && cb.isValid()) {
                    destroyedAny = true;
                    NewModePvp.startCoreDeath(cb);
                }
            }
        }
        // Dự phòng nếu map không còn lõi địch nào để kích hoạt kết thúc ngay
        if (!destroyedAny) {
            NewModePvp.win(winner);
        }
    }

    // ===== Đồng bộ trạng thái Chiếm Cứ từ Host -> Client =====
    static void broadcastDomState() {
        if (!Vars.net.server()) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(matchEnded ? 1 : 0).append('|').append(winnerTeamId).append('|');
            boolean f = true;
            for (java.util.Map.Entry<Integer, Float> e : scores.entrySet()) {
                if (!f) sb.append(',');
                f = false;
                sb.append(e.getKey()).append(':').append(Strings.fixed(e.getValue(), 1));
            }
            sb.append('|');
            for (int i = 0; i < points.size(); i++) {
                CapPoint cp = points.get(i);
                if (i > 0) sb.append(';');
                sb.append(cp.ownerTeamId).append(',').append(cp.capTeamId).append(',')
                  .append(Strings.fixed(cp.progress, 3)).append(',').append(cp.contested ? 1 : 0);
            }
            sb.append('|').append(Vars.player != null ? Vars.player.id : -1);
            Call.clientPacketReliable(SYNC_DOM_KIND, sb.toString());
        } catch (Throwable ignored) {}
    }

    static void onSyncPacket(String raw) {
        if (!Vars.net.client() || raw == null) return;
        try {
            if (points.isEmpty()) scanCapturePoints();
            String[] parts = raw.split("\\|", -1);
            if (parts.length < 4) return;
            matchEnded = "1".equals(parts[0]);
            winnerTeamId = Integer.parseInt(parts[1]);
            if (parts.length >= 5) {
                try { hostPid = Integer.parseInt(parts[4]); } catch (Throwable ignored) {}
            }
            scores.clear();
            if (!parts[2].isEmpty()) {
                for (String seg : parts[2].split(",")) {
                    String[] kv = seg.split(":");
                    if (kv.length == 2) scores.put(Integer.parseInt(kv[0]), Float.parseFloat(kv[1]));
                }
            }
            rateCount.clear();
            if (!parts[3].isEmpty()) {
                String[] arr = parts[3].split(";");
                for (int i = 0; i < arr.length && i < points.size(); i++) {
                    String[] p = arr[i].split(",");
                    if (p.length >= 4) {
                        CapPoint cp = points.get(i);
                        cp.ownerTeamId = Integer.parseInt(p[0]);
                        cp.capTeamId = Integer.parseInt(p[1]);
                        cp.progress = Mathf.clamp(Float.parseFloat(p[2]));
                        cp.contested = "1".equals(p[3]);
                        if (cp.ownerTeamId >= 0) rateCount.merge(cp.ownerTeamId, 1, Integer::sum);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    // ===== Vẽ hiệu ứng vòng tròn Cứ Điểm & vòng tròn đang nạp theo màu phe =====
    private static void drawWorldFx() {
        if (Vars.headless || !isActive() || points.isEmpty()) return;

        Draw.draw(Layer.groundUnit - 1.5f, () -> {
            float t = Time.time;
            for (int i = 0; i < points.size(); i++) {
                CapPoint cp = points.get(i);
                if (!NewModePvp.camVisible(cp.x, cp.y, cp.radius + 32f)) continue;

                Team owner = cp.ownerTeamId >= 0 ? Team.get(cp.ownerTeamId) : null;
                Team capper = cp.capTeamId >= 0 ? Team.get(cp.capTeamId) : null;

                Color baseCol = owner != null ? owner.color : Tmp.c1.set(0.76f, 0.88f, 1f, 1f);
                Color chargeCol = capper != null ? capper.color : baseCol;
                if (cp.contested) {
                    float blink = 0.5f + 0.5f * Mathf.sin(t * 0.25f);
                    chargeCol = Tmp.c2.set(Color.scarlet).lerp(Color.gold, blink);
                }

                float pulse = 0.5f + 0.5f * Mathf.sin(t * 0.06f + cp.id * 1.3f);
                float r = cp.radius;

                // 1) Đĩa năng lượng nền cứ điểm
                Draw.color(baseCol, owner != null ? (0.14f + 0.05f * pulse) : (0.08f + 0.04f * pulse));
                Fill.circle(cp.x, cp.y, r);

                // Sóng lan tỏa nhẹ từ tâm cứ điểm
                float waveF = ((t * 0.012f + cp.id * 0.23f) % 1f);
                Lines.stroke(1.6f * (1f - waveF));
                Draw.color(baseCol, (1f - waveF) * 0.35f);
                Lines.circle(cp.x, cp.y, r * (0.2f + 0.8f * waveF));

                // 2) Viền ngoài cứ điểm nét đứt xoay mượt (8 cung tròn)
                Lines.stroke(2.2f);
                Draw.color(baseCol, 0.65f + 0.2f * pulse);
                float rot = t * 0.45f + cp.id * 35f;
                for (int s = 0; s < 8; s++) {
                    Lines.arc(cp.x, cp.y, r, 0.085f, rot + s * 45f);
                }

                // Vòng trong trang trí
                Lines.stroke(1.2f);
                Draw.color(baseCol, 0.35f);
                Lines.circle(cp.x, cp.y, r * 0.78f);

                // 3) Vòng tròn ĐANG NẠP theo màu phe khi có tiến độ (0% -> 100%)
                if (cp.progress > 0.005f) {
                    float arcR = r + 5.5f;
                    // Rãnh nền vòng nạp
                    Lines.stroke(5.0f);
                    Draw.color(Color.black, 0.45f);
                    Lines.circle(cp.x, cp.y, arcR);

                    // Cung tròn tiến độ sáng rực chạy từ đỉnh (90°) theo chiều kim đồng hồ / ngược chiều
                    Lines.stroke(4.2f);
                    Draw.color(chargeCol, 0.95f);
                    Lines.arc(cp.x, cp.y, arcR, cp.progress, 90f);

                    // Ánh sáng trắng lõi cung tiến độ
                    Lines.stroke(1.6f);
                    Draw.color(Color.white, 0.7f);
                    Lines.arc(cp.x, cp.y, arcR, cp.progress, 90f);

                    // Hạt năng lượng sáng tại đầu mũi vòng tròn đang nạp (khi đang nạp dở)
                    if (cp.progress < 0.995f) {
                        float tipAng = 90f + cp.progress * 360f;
                        float tx = cp.x + Mathf.cosDeg(tipAng) * arcR;
                        float ty = cp.y + Mathf.sinDeg(tipAng) * arcR;
                        Draw.color(chargeCol, 0.45f);
                        Fill.circle(tx, ty, 7.5f);
                        Draw.color(Color.white, 0.95f);
                        Fill.circle(tx, ty, 3.6f);
                    }
                }

                // 4) Nhãn tên cứ điểm (A, B, C...) & trạng thái nạp/tranh chấp
                float fontScl = 0.72f;
                Fonts.outline.getData().setScale(fontScl);
                Fonts.outline.setColor(owner != null ? owner.color : Color.white);
                Fonts.outline.draw(cp.label, cp.x, cp.y + 8f, Align.center);

                if (cp.contested) {
                    Fonts.outline.getData().setScale(0.36f);
                    Fonts.outline.setColor(Color.gold);
                    Fonts.outline.draw(tr("TRANH CHẤP!", "CONTESTED!"), cp.x, cp.y - 10f, Align.center);
                } else if (cp.progress > 0.01f && cp.progress < 0.995f) {
                    Fonts.outline.getData().setScale(0.38f);
                    Fonts.outline.setColor(chargeCol);
                    Fonts.outline.draw(Math.round(cp.progress * 100f) + "%", cp.x, cp.y - 10f, Align.center);
                }

                Fonts.outline.setColor(Color.white);
                Fonts.outline.getData().setScale(1f);
            }

            // Vẽ vòng hồi máu nhẹ quanh Lõi bất tử của các phe
            Seq<Team> tl = NewModePvp.pvpTeams();
            for (int i = 0; i < tl.size; i++) {
                CoreBlock.CoreBuild cb = tl.get(i).core();
                if (cb == null || !cb.isValid() || !NewModePvp.camVisible(cb.x, cb.y, CORE_HEAL_RADIUS + 16f)) continue;
                Lines.stroke(1.3f);
                Draw.color(tl.get(i).color, 0.24f);
                for (int s = 0; s < 16; s++) Lines.arc(cb.x, cb.y, CORE_HEAL_RADIUS, 0.035f, s * 22.5f);
            }
            Draw.reset();
        });
    }

    // ===== Hiển thị Cứ Điểm trên Minimap (góc màn hình) & Full Map (phím M) =====
    private static Element cachedMinimapEl = null;
    private static Field fPanX, fPanY, fZoom, fBaseSize;
    private static boolean mmReflectChecked = false;
    private static final Rect mmScissor = new Rect(), mmBounds = new Rect();

    private static void ensureMinimapReflect() {
        if (mmReflectChecked) return;
        mmReflectChecked = true;
        try {
            Class<?> c = mindustry.ui.fragments.MinimapFragment.class;
            fPanX = c.getDeclaredField("panx"); fPanX.setAccessible(true);
            fPanY = c.getDeclaredField("pany"); fPanY.setAccessible(true);
            fZoom = c.getDeclaredField("zoom"); fZoom.setAccessible(true);
            fBaseSize = c.getDeclaredField("baseSize"); fBaseSize.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    private static Element findMinimapWidget(Group g) {
        if (g == null) return null;
        for (Element el : g.getChildren()) {
            if ("Minimap".equals(el.getClass().getSimpleName())) return el;
            if (el instanceof Group) {
                Element sub = findMinimapWidget((Group) el);
                if (sub != null) return sub;
            }
        }
        return null;
    }

    private static boolean isEffectivelyVisible(Element el) {
        Element cur = el;
        while (cur != null) {
            if (!cur.visible) return false;
            cur = cur.parent;
        }
        return true;
    }

    private static void drawMinimapOverlays() {
        if (Vars.headless || !isActive() || points.isEmpty() || Vars.world == null || Vars.renderer == null || Vars.renderer.minimap == null) return;
        if (Vars.state == null || !Vars.state.isGame()) return;

        float worldW = Vars.world.width() * Vars.tilesize;
        float worldH = Vars.world.height() * Vars.tilesize;
        if (worldW <= 1f || worldH <= 1f) return;

        // 1) Bản đồ toàn màn hình khi nhấn phím M (Vars.ui.minimapfrag)
        if (Vars.ui != null && Vars.ui.minimapfrag != null && Vars.ui.minimapfrag.shown()) {
            ensureMinimapReflect();
            float panx = 0f, pany = 0f, zoom = 1f, baseSize = Scl.scl(5f);
            try {
                if (fPanX != null) panx = fPanX.getFloat(Vars.ui.minimapfrag);
                if (fPanY != null) pany = fPanY.getFloat(Vars.ui.minimapfrag);
                if (fZoom != null) zoom = fZoom.getFloat(Vars.ui.minimapfrag);
                if (fBaseSize != null) baseSize = fBaseSize.getFloat(Vars.ui.minimapfrag);
            } catch (Throwable ignored) {}

            float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
            float ratio = (float) Vars.world.height() / Math.max(1, Vars.world.width());
            float size = baseSize * zoom * Vars.world.width();
            float rx = sw * 0.5f + panx * zoom - size * 0.5f;
            float ry = sh * 0.5f + pany * zoom - size * 0.5f * ratio;
            float rw = size, rh = size * ratio;

            for (int i = 0; i < points.size(); i++) {
                CapPoint cp = points.get(i);
                float sx = rx + (cp.x / worldW) * rw;
                float sy = ry + (cp.y / worldH) * rh;
                float sr = Math.max(Scl.scl(10f), (cp.radius / worldW) * rw);
                drawMapPointMarker(cp, sx, sy, sr, true);
            }
            Draw.reset();
            return;
        }

        // 2) Minimap góc màn hình (mindustry.ui.Minimap trong Vars.ui.hudGroup)
        if (Vars.ui == null || Vars.ui.hudfrag == null || !Vars.ui.hudfrag.shown || !Core.settings.getBool("minimap", true)) return;
        if (cachedMinimapEl == null || cachedMinimapEl.parent == null) {
            cachedMinimapEl = findMinimapWidget(Vars.ui.hudGroup);
        }
        if (cachedMinimapEl == null || !isEffectivelyVisible(cachedMinimapEl)) return;

        float mw = cachedMinimapEl.getWidth(), mh = cachedMinimapEl.getHeight();
        if (mw <= 4f || mh <= 4f) return;
        Tmp.v1.set(0f, 0f);
        cachedMinimapEl.localToStageCoordinates(Tmp.v1);
        float mx = Tmp.v1.x, my = Tmp.v1.y;

        TextureRegion reg = Vars.renderer.minimap.getRegion();
        if (reg == null) return;
        float uMin = reg.u, uMax = reg.u2;
        float yMin = 1f - reg.v2, yMax = 1f - reg.v;
        if (yMin > yMax) { float tmp = yMin; yMin = yMax; yMax = tmp; }
        float uSpan = Math.max(0.0001f, uMax - uMin);
        float ySpan = Math.max(0.0001f, yMax - yMin);

        Draw.flush();
        Gl.enable(GL20.GL_SCISSOR_TEST);
        Gl.scissor(Math.max(0, Math.round(mx)), Math.max(0, Math.round(my)), Math.max(1, Math.round(mw)), Math.max(1, Math.round(mh)));

        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            float nx = cp.x / worldW;
            float ny = cp.y / worldH;
            float sx = mx + ((nx - uMin) / uSpan) * mw;
            float sy = my + ((ny - yMin) / ySpan) * mh;
            float sr = Mathf.clamp((cp.radius / worldW / uSpan) * mw, Scl.scl(6.5f), mw * 0.24f);
            drawMapPointMarker(cp, sx, sy, sr, false);
        }
        Draw.flush();
        Gl.disable(GL20.GL_SCISSOR_TEST);
        Draw.reset();
    }

    private static void drawMapPointMarker(CapPoint cp, float sx, float sy, float sr, boolean fullMap) {
        Team owner = cp.ownerTeamId >= 0 ? Team.get(cp.ownerTeamId) : null;
        Team capper = cp.capTeamId >= 0 ? Team.get(cp.capTeamId) : null;
        Color baseCol = owner != null ? owner.color : Tmp.c1.set(0.76f, 0.90f, 1f, 1f);
        Color chargeCol = capper != null ? capper.color : baseCol;
        if (cp.contested) {
            float blink = 0.5f + 0.5f * Mathf.sin(Time.time * 0.28f);
            chargeCol = Tmp.c2.set(Color.scarlet).lerp(Color.gold, blink);
        }

        // Nền vòng tròn cứ điểm
        Draw.color(baseCol, owner != null ? 0.32f : 0.20f);
        Fill.circle(sx, sy, sr);

        // Viền ngoài cứ điểm
        Lines.stroke(Scl.scl(fullMap ? 2.0f : 1.5f));
        Draw.color(cp.contested ? chargeCol : baseCol, 0.95f);
        Lines.circle(sx, sy, sr);

        // Vòng tròn đang nạp trên minimap / map M
        if (cp.progress > 0.01f && cp.progress < 0.995f) {
            Lines.stroke(Scl.scl(fullMap ? 2.8f : 2.0f));
            Draw.color(chargeCol, 1f);
            Lines.arc(sx, sy, sr + Scl.scl(fullMap ? 2.5f : 1.6f), cp.progress, 90f);
        }

        // Nhãn A, B, C... rõ nét
        float fs = Scl.scl(fullMap ? 0.72f : 0.52f);
        Fonts.outline.getData().setScale(fs);
        Fonts.outline.setColor(owner != null ? owner.color : Color.white);
        Fonts.outline.draw(cp.label, sx, sy + (fullMap ? Scl.scl(6f) : Scl.scl(4.5f)), Align.center);

        if (fullMap && cp.contested) {
            Fonts.outline.getData().setScale(Scl.scl(0.48f));
            Fonts.outline.setColor(Color.gold);
            Fonts.outline.draw("!", sx, sy - Scl.scl(7f), Align.center);
        } else if (fullMap && cp.progress > 0.01f && cp.progress < 0.995f) {
            Fonts.outline.getData().setScale(Scl.scl(0.45f));
            Fonts.outline.setColor(chargeCol);
            Fonts.outline.draw(Math.round(cp.progress * 100f) + "%", sx, sy - Scl.scl(7f), Align.center);
        }

        Fonts.outline.setColor(Color.white);
        Fonts.outline.getData().setScale(1f);
    }

    // ===== UI Thanh Tiến Độ Đường Thẳng (tự do kéo thả) & Nút Đổi Unit (tự do kéo thả) =====
    private static void clampPos(Element el, String kx, String ky) {
        float sw = Core.scene != null ? Core.scene.getWidth() : Core.graphics.getWidth();
        float sh = Core.scene != null ? Core.scene.getHeight() : Core.graphics.getHeight();
        float mx = Math.max(0f, sw - el.getWidth());
        float my = Math.max(0f, sh - el.getHeight());
        el.setPosition(Mathf.clamp(el.x, 0f, mx), Mathf.clamp(el.y, 0f, my));
        if (kx != null && ky != null) {
            Core.settings.put(kx, el.x);
            Core.settings.put(ky, el.y);
        }
    }

    private static void drawDominationBarAt(float ox, float oy, float panelW, float panelH, float u) {
        if (Vars.headless || !isActive() || Vars.state == null || !Vars.state.isGame()) return;

        Seq<Team> tl = NewModePvp.pvpTeams();
        if (tl.size < 2) return;

        Team leftTeam = (Vars.player != null && tl.contains(Vars.player.team())) ? Vars.player.team() : tl.get(0);
        Team rightTeam = null;
        for (int i = 0; i < tl.size; i++) {
            if (tl.get(i) != leftTeam) { rightTeam = tl.get(i); break; }
        }
        if (rightTeam == null) rightTeam = tl.get(1);

        float sLeft = scores.getOrDefault(leftTeam.id, 0f);
        float sRight = scores.getOrDefault(rightTeam.id, 0f);
        float fracL = Mathf.clamp(sLeft / SCORE_GOAL);
        float fracR = Mathf.clamp(sRight / SCORE_GOAL);
        int cntL = rateCount.getOrDefault(leftTeam.id, 0);
        int cntR = rateCount.getOrDefault(rightTeam.id, 0);

        float cx = ox + panelW * 0.5f;
        float topY = oy + panelH;

        // Khung nền kính tối
        Draw.color(0.04f, 0.06f, 0.11f, 0.80f);
        Fill.rect(cx, oy + panelH * 0.5f, panelW, panelH);
        Lines.stroke(1.5f * u);
        Draw.color(0.35f, 0.78f, 1f, 0.55f);
        Lines.rect(ox, oy, panelW, panelH);

        // Tiêu đề & số điểm 2 phe
        float textY = topY - 8f * u;
        float ptsPerSec = effectivePtsPerSec();
        Fonts.outline.getData().setScale(0.72f * u);
        Fonts.outline.setColor(leftTeam.color);
        String lTxt = leftTeam.localized() + " " + Math.round(sLeft) + "/" + (int) SCORE_GOAL
            + (cntL > 0 ? " (+" + Strings.fixed(cntL * ptsPerSec, 1) + "/s)" : "");
        Fonts.outline.draw(lTxt, ox + 10f * u, textY, Align.left);

        Fonts.outline.setColor(rightTeam.color);
        String rTxt = (cntR > 0 ? "(+" + Strings.fixed(cntR * ptsPerSec, 1) + "/s) " : "")
            + Math.round(sRight) + "/" + (int) SCORE_GOAL + " " + rightTeam.localized();
        Fonts.outline.draw(rTxt, ox + panelW - 10f * u, textY, Align.right);

        // 1 ĐƯỜNG THẲNG TIẾN ĐỘ ĐỐI ĐẦU
        float barW = panelW - 24f * u;
        float barH = 12f * u;
        float barY = topY - 34f * u;
        float halfW = barW * 0.5f - 3f * u;

        Draw.color(0.14f, 0.16f, 0.22f, 0.92f);
        Fill.rect(cx - halfW * 0.5f - 2f * u, barY, halfW, barH);
        Fill.rect(cx + halfW * 0.5f + 2f * u, barY, halfW, barH);

        if (fracL > 0.002f) {
            float fw = halfW * fracL;
            float leftEdge = cx - halfW - 2f * u;
            Draw.color(leftTeam.color, 0.95f);
            Fill.rect(leftEdge + fw * 0.5f, barY, fw, barH);
            Draw.color(Color.white, 0.35f);
            Fill.rect(leftEdge + fw * 0.5f, barY + barH * 0.25f, fw, barH * 0.30f);
            if (cntL > 0) {
                Draw.color(Color.white, 0.85f);
                Fill.rect(leftEdge + fw, barY, 2.5f * u, barH + 3f * u);
            }
        }

        if (fracR > 0.002f) {
            float fw = halfW * fracR;
            float rightEdge = cx + halfW + 2f * u;
            Draw.color(rightTeam.color, 0.95f);
            Fill.rect(rightEdge - fw * 0.5f, barY, fw, barH);
            Draw.color(Color.white, 0.35f);
            Fill.rect(rightEdge - fw * 0.5f, barY + barH * 0.25f, fw, barH * 0.30f);
            if (cntR > 0) {
                Draw.color(Color.white, 0.85f);
                Fill.rect(rightEdge - fw, barY, 2.5f * u, barH + 3f * u);
            }
        }

        Lines.stroke(1.2f * u);
        Draw.color(Color.white, 0.45f);
        Lines.rect(cx - halfW - 2f * u, barY - barH * 0.5f, halfW, barH);
        Lines.rect(cx + 2f * u, barY - barH * 0.5f, halfW, barH);
        Draw.color(Color.gold, 0.95f);
        Fill.rect(cx, barY, 3f * u, barH + 6f * u);

        // Hàng chấm trạng thái các cứ điểm A, B, C...
        if (!points.isEmpty()) {
            float badgeY = topY - 58f * u;
            float step = Math.min(34f * u, (panelW - 28f * u) / Math.max(1, points.size()));
            float startX = cx - (points.size() - 1) * step * 0.5f;
            float br = 9.5f * u;
            for (int i = 0; i < points.size(); i++) {
                CapPoint cp = points.get(i);
                float bx = startX + i * step;
                Team ow = cp.ownerTeamId >= 0 ? Team.get(cp.ownerTeamId) : null;
                Color bc = ow != null ? ow.color : Color.lightGray;
                if (cp.contested) {
                    bc = (Mathf.sin(Time.time * 0.3f) > 0f) ? Color.gold : Color.scarlet;
                }
                Draw.color(bc, ow != null ? 0.88f : 0.35f);
                Fill.circle(bx, badgeY, br);
                Lines.stroke(1.5f * u);
                Draw.color(cp.contested ? Color.white : bc, 0.95f);
                Lines.circle(bx, badgeY, br);

                Fonts.outline.getData().setScale(0.60f * u);
                Fonts.outline.setColor(Color.white);
                Fonts.outline.draw(cp.label, bx, badgeY + 5f * u, Align.center);
            }
        }

        Fonts.outline.setColor(Color.white);
        Fonts.outline.getData().setScale(1f);
        Draw.reset();
    }

    // ===== Thêm các phần tử HUD tự do kéo thả (Thanh Tiến Độ, Nút Đổi Unit) & Nút Lưu Unit =====
    private static void addHudElements() {
        if (Vars.headless || Vars.ui == null || Vars.ui.hudGroup == null) return;
        try {
            // 1) Thanh hiển thị tiến độ PvP Chiếm Cứ — tự do kéo thả mọi vị trí
            final float barU = Scl.scl(Vars.mobile ? 0.88f : 1.0f);
            final float initSw = Core.scene != null ? Core.scene.getWidth() : Core.graphics.getWidth();
            final float initSh = Core.scene != null ? Core.scene.getHeight() : Core.graphics.getHeight();
            final float barW = Math.min(initSw - 24f * barU, (Vars.mobile ? 370f : 480f) * barU);
            final float barH = 74f * barU;

            final Element barEl = new Element() {
                @Override
                public void draw() {
                    if (!isActive() || Vars.state == null || !Vars.state.isGame()) return;
                    float curU = Scl.scl(Vars.mobile ? 0.88f : 1.0f);
                    float sw = Core.scene != null ? Core.scene.getWidth() : Core.graphics.getWidth();
                    float w = Math.min(sw - 24f * curU, (Vars.mobile ? 370f : 480f) * curU);
                    float h = (points.isEmpty() ? 52f : 74f) * curU;
                    if (Math.abs(width - w) > 1f || Math.abs(height - h) > 1f) {
                        setSize(w, h);
                        clampPos(this, null, null);
                    }
                    drawDominationBarAt(x, y, width, height, curU);
                }
            };
            barEl.name = "newex-dom-progress-bar";
            domBarEl = barEl;
            barEl.setSize(barW, barH);
            barEl.touchable = Touchable.enabled;
            barEl.visibility = () -> isActive() && NewModePvp.mapUiEnabled() && Vars.state != null && Vars.state.isGame() && (Vars.ui.hudfrag == null || Vars.ui.hudfrag.shown);
            if (Core.settings.has(KEY_BAR_X) && Core.settings.has(KEY_BAR_Y)) {
                barEl.setPosition(Core.settings.getFloat(KEY_BAR_X, 0f), Core.settings.getFloat(KEY_BAR_Y, 0f));
                clampPos(barEl, null, null);
            } else {
                barEl.setPosition((initSw - barW) * 0.5f, initSh - barH - (Vars.mobile ? 56f : 44f) * barU);
                clampPos(barEl, null, null);
            }
            final float[] barOff = new float[2];
            barEl.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    barOff[0] = x; barOff[1] = y;
                    return true;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    barEl.setPosition(barEl.x + x - barOff[0], barEl.y + y - barOff[1]);
                    clampPos(barEl, KEY_BAR_X, KEY_BAR_Y);
                }
            });
            Vars.ui.hudGroup.addChild(barEl);

            // 2) Thanh nút [Đổi Unit] — tự do kéo thả mọi vị trí (nhấn nhẹ = mở chọn Unit, kéo >= 6px = di chuyển)
            final Table swapWrap = new Table(Styles.black6);
            swapWrap.name = "newex-dom-swap-unit";
            domSwapWrap = swapWrap;
            swapWrap.margin(4f);
            swapWrap.touchable = Touchable.enabled;
            final TextButton swapBtn = new TextButton(tr("Đổi Unit", "Switch Unit"), Styles.flatBordert);
            swapBtn.touchable = Touchable.disabled; // để swapWrap xử lý cả kéo thả lẫn bấm không xung đột!
            swapWrap.add(swapBtn).size(118f, 36f);
            swapWrap.pack();
            swapWrap.visibility = () -> isActive() && NewModePvp.mapUiEnabled() && Vars.state != null && Vars.state.isGame() && (Vars.ui.hudfrag == null || Vars.ui.hudfrag.shown);
            if (Core.settings.has(KEY_SWAP_X) && Core.settings.has(KEY_SWAP_Y)) {
                swapWrap.setPosition(Core.settings.getFloat(KEY_SWAP_X, 0f), Core.settings.getFloat(KEY_SWAP_Y, 0f));
                clampPos(swapWrap, null, null);
            } else {
                swapWrap.setPosition(initSw - swapWrap.getWidth() - 12f, initSh - swapWrap.getHeight() - (Vars.mobile ? 115f : 96f));
                clampPos(swapWrap, null, null);
            }
            final boolean[] swapDrag = {false};
            final float[] swapOff = new float[2];
            swapWrap.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    swapDrag[0] = false;
                    swapOff[0] = x;
                    swapOff[1] = y;
                    return true;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (Math.abs(x - swapOff[0]) > 6f || Math.abs(y - swapOff[1]) > 6f) swapDrag[0] = true;
                    if (swapDrag[0]) {
                        swapWrap.setPosition(swapWrap.x + x - swapOff[0], swapWrap.y + y - swapOff[1]);
                        clampPos(swapWrap, KEY_SWAP_X, KEY_SWAP_Y);
                    }
                }
                @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    boolean wasDrag = swapDrag[0];
                    swapDrag[0] = false;
                    if (!wasDrag && isActive()) {
                        if (localLife.spectating) {
                            Vars.ui.showInfoToast(tr("[scarlet]Bạn đã hết Unit và đang ở chế độ Khán giả!", "[scarlet]Out of Units — Spectating!"), 2.5f);
                            return;
                        }
                        if (!localLife.waitingPick && !NewModePvp.nearOwnCore() && Vars.player != null && Vars.player.unit() != null && !Vars.player.unit().dead) {
                            Vars.ui.showInfoToast(tr("[scarlet]Hãy về gần Lõi nhà để đổi Unit đã lưu!", "[scarlet]Return near your Core to switch saved Unit!"), 2.5f);
                            return;
                        }
                        openUnitPickerDialog(true);
                    }
                }
            });
            Vars.ui.hudGroup.addChild(swapWrap);

            // 3) Thanh trạng thái Huấn Luyện Unit Player (Đợt X/Max + nút Lưu Unit khi đã hoàn thành)
            Table trainBox = new Table();
            trainBox.name = "newex-train-hud";
            trainBox.top().right();
            trainBox.setFillParent(true);
            trainBox.touchable = Touchable.childrenOnly;
            Table trainCard = new Table(Styles.black6);
            trainCard.margin(6f);
            trainCard.label(() -> "[#38ef7d]" + tr("Huấn luyện: ", "Training: ") + "[white]" + tr("Đợt ", "Wave ") + trainWave + "/" + trainMaxWaves
                + (trainFinished ? "  [#ffd166](" + tr("Hoàn thành!", "Done!") + ")[]" : "") + "[]").padRight(6f);
            TextButton trSaveBtn = new TextButton(tr("[#ffd166]Lưu Unit[]", "[#ffd166]Save Unit[]"), Styles.flatBordert);
            trSaveBtn.clicked(() -> openSaveUnitPromptDialog(
                tr("Lưu bản sao lưu Unit (" + trainMaxWaves + " đợt huấn luyện)", "Save Unit backup (" + trainMaxWaves + " training waves)"),
                trainMaxWaves));
            trSaveBtn.visibility = () -> isTraining() && trainFinished;
            trainCard.add(trSaveBtn).size(104f, 32f);
            trainCard.visibility = () -> isTraining() && NewModePvp.mapUiEnabled() && Vars.state != null && Vars.state.isGame() && (Vars.ui.hudfrag == null || Vars.ui.hudfrag.shown);
            trainBox.add(trainCard).top().right().padTop(Vars.mobile ? 110f : 92f).padRight(8f);
            Vars.ui.hudGroup.addChild(trainBox);
        } catch (Throwable err) {
            Log.err("[PvpDomination] addHudElements failed", err);
        }
    }

    /** Đưa toàn bộ nút UI kéo thả của PvP Chiếm Cứ về giữa màn hình khi bật lại nút hiện UI trong Cài đặt > Trò chơi. */
    public static void centerAllDomHud() {
        float sw = Core.scene != null ? Core.scene.getWidth() : Core.graphics.getWidth();
        float sh = Core.scene != null ? Core.scene.getHeight() : Core.graphics.getHeight();
        if (domBarEl != null) {
            domBarEl.setPosition((sw - domBarEl.getWidth()) * 0.5f, sh * 0.5f + 46f);
            clampPos(domBarEl, KEY_BAR_X, KEY_BAR_Y);
        }
        if (domSwapWrap != null) {
            domSwapWrap.setPosition((sw - domSwapWrap.getWidth()) * 0.5f, sh * 0.5f - domSwapWrap.getHeight() - 10f);
            clampPos(domSwapWrap, KEY_SWAP_X, KEY_SWAP_Y);
        }
    }

    // ===== PHẦN 2: LƯU BẢN SAO LƯU UNIT SAU KHI HOÀN THÀNH HUẤN LUYỆN =====
    /** Hộp thoại hỏi người chơi có muốn lưu file Unit hiện tại vào 5 ô lưu hay không. */
    public static void openSaveUnitPromptDialog(final String reason, final int maxWaves) {
        if (Vars.headless || Vars.player == null) return;
        final NewModePvp.St st = NewModePvp.localState();
        if (st == null) return;

        savePromptOpen = true;
        final SavedUnit snap = captureFromState(st, maxWaves);
        final ArrayList<SavedUnit> slots = loadSavedUnits();
        final boolean multiSave = trainMultiSaveOn();
        final int[] copyCnt = {1};

        final BaseDialog d = new BaseDialog(tr("Lưu Bản Sao Lưu Unit (Kho Unit)", "Save Unit Backup (Unit Vault)"));
        activeSaveDialog = d;
        d.cont.margin(12f);

        float w = Math.min(Core.graphics.getWidth() / Scl.scl() - 40f, 520f);
        d.cont.add("[#ffd166]" + reason + "[]").wrap().width(w).labelAlign(Align.center).padBottom(8f).row();
        d.cont.add("[white]" + tr("Bạn có muốn lưu bản sao lưu Unit hiện tại (Max " + maxWaves + " đợt) vào [accent]Kho Unit[] không?",
            "Do you want to save your current Unit build (Max " + maxWaves + " waves) into the [accent]Unit Vault[]?") + "[]")
            .wrap().width(w).labelAlign(Align.center).padBottom(10f).row();

        // Thẻ tóm tắt Unit hiện tại
        d.cont.add(buildUnitCard(snap, w - 16f, -1, null, null)).width(w - 16f).padBottom(10f).row();

        d.cont.add("[lightgray]" + tr("Ô lưu hiện tại: ", "Saved slots: ") + "[accent]" + slots.size() + "/" + MAX_SAVED_SLOTS + "[]").padBottom(6f).row();

        if (multiSave) {
            Table cpRow = new Table(Styles.black6);
            cpRow.margin(6f);
            cpRow.add("[#38ef7d]" + tr("Số bản sao lưu cùng lúc:", "Copies to save at once:") + "[]").padRight(8f);
            final TextButton[] cpBtns = new TextButton[MAX_SAVED_SLOTS];
            for (int k = 1; k <= MAX_SAVED_SLOTS; k++) {
                final int cnt = k;
                cpBtns[k - 1] = cpRow.button(String.valueOf(cnt), Styles.flatBordert, () -> {
                    copyCnt[0] = cnt;
                    for (int j = 0; j < MAX_SAVED_SLOTS; j++) {
                        cpBtns[j].setText((j + 1 == copyCnt[0] ? "[#38ef7d]" : "[white]") + (j + 1) + "[]");
                    }
                }).size(42f, 36f).pad(2f).get();
            }
            cpBtns[0].setText("[#38ef7d]1[]");
            d.cont.add(cpRow).padBottom(8f).row();
        }

        Table btns = new Table();
        btns.button(tr("[green]Có (Lưu Unit)[]", "[green]Yes (Save Unit)[]"), Styles.flatBordert, () -> {
            int want = multiSave ? Mathf.clamp(copyCnt[0], 1, MAX_SAVED_SLOTS) : 1;
            if (want == 1) {
                if (slots.size() < MAX_SAVED_SLOTS) {
                    slots.add(snap);
                    persistSavedUnits(slots);
                    savePromptOpen = false;
                    d.hide();
                    Vars.ui.showInfoToast(tr("[green]Đã lưu bản sao lưu Unit (Max " + maxWaves + " đợt) vào Ô #" + slots.size() + "/" + MAX_SAVED_SLOTS + "!",
                        "[green]Saved Unit (Max " + maxWaves + " waves) to Slot #" + slots.size() + "/" + MAX_SAVED_SLOTS + "!"), 4f);
                } else {
                    d.hide();
                    openOverwriteSlotDialog(snap, slots);
                }
            } else {
                // Lưu nhiều bản sao cùng lúc: điền vào các ô trống trước, nếu vượt quá 5 ô thì ghi đè lần lượt từ ô đầu
                int saved = 0, owIdx = 0;
                while (saved < want) {
                    SavedUnit copy = decodeSavedUnit(encodeSavedUnit(snap));
                    if (copy == null) copy = snap;
                    if (slots.size() < MAX_SAVED_SLOTS) {
                        slots.add(copy);
                    } else {
                        slots.set(owIdx % MAX_SAVED_SLOTS, copy);
                        owIdx++;
                    }
                    saved++;
                }
                persistSavedUnits(slots);
                savePromptOpen = false;
                d.hide();
                Vars.ui.showInfoToast(tr("[green]Đã sao lưu " + saved + " bản Unit cùng lúc vào Kho Unit (" + slots.size() + "/" + MAX_SAVED_SLOTS + " ô)!",
                    "[green]Saved " + saved + " Unit copies at once into Unit Vault (" + slots.size() + "/" + MAX_SAVED_SLOTS + " slots)!"), 4f);
            }
        }).size(200f, 48f).pad(6f);

        btns.button(tr("[lightgray]Không[]", "[lightgray]No[]"), Styles.flatBordert, () -> {
            savePromptOpen = false;
            d.hide();
        }).size(160f, 48f).pad(6f);

        d.cont.add(btns).row();
        d.hidden(() -> {
            savePromptOpen = false;
            if (activeSaveDialog == d) activeSaveDialog = null;
        });
        d.show();
    }

    /** Khi đã đầy 5/5 ô lưu: cho phép người chơi chọn ô muốn ghi đè. */
    private static void openOverwriteSlotDialog(final SavedUnit snap, final ArrayList<SavedUnit> slots) {
        final BaseDialog d = new BaseDialog(tr("Đã đầy 5/5 ô lưu — Chọn ô để ghi đè", "All 5/5 Slots Full — Pick Slot to Overwrite"));
        activeSaveDialog = d;
        float w = Math.min(Core.graphics.getWidth() / Scl.scl() - 40f, 540f);
        Table list = new Table();
        list.top();
        for (int i = 0; i < slots.size(); i++) {
            final int idx = i;
            list.add(buildUnitCard(slots.get(i), w - 24f, i, tr("Ghi đè", "Overwrite"), () -> {
                slots.set(idx, snap);
                persistSavedUnits(slots);
                savePromptOpen = false;
                d.hide();
                Vars.ui.showInfoToast(tr("[green]Đã ghi đè Unit vào Ô #" + (idx + 1) + "/5!",
                    "[green]Overwrote Slot #" + (idx + 1) + "/5!"), 4f);
            })).width(w - 24f).pad(4f).row();
        }
        d.cont.add(new ScrollPane(list)).width(w).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).row();
        d.buttons.button(tr("Hủy", "Cancel"), () -> {
            savePromptOpen = false;
            d.hide();
        }).size(160f, 44f);
        d.hidden(() -> {
            savePromptOpen = false;
            if (activeSaveDialog == d) activeSaveDialog = null;
        });
        d.show();
    }

    // ===== PHẦN 3: CÀI ĐẶT HUẤN LUYỆN TẠO BẢN SAO UNIT & KHO UNIT / CHỌN UNIT (MachineUi) =====
    /** Trang cài đặt trước khi vào map Huấn Luyện Unit Player (dùng MachineUi). */
    public static void openTrainingSetupDialog() {
        if (Vars.headless) return;
        final MachineUi.Panel panel = MachineUi.panel(tr("Cài Đặt — Tạo Bản Sao Lưu Unit", "Setup — Unit Training Backup"));
        final boolean[] dupVal = {trainDupTurretOn()};
        final int[] tmVal = {trainTurretMode()};
        final int[] maxWVal = {getTrainMaxWaves()};
        final boolean[] multiSaveVal = {trainMultiSaveOn()};
        final TextField[] mwRef = new TextField[1];

        panel.onBuild(() -> {
            Table c = panel.content();
            c.clear();
            c.margin(10f).top().left().defaults().left();
            float w = panel.innerW();
            float wrapW = Math.max(160f, w - 28f);

            c.add("[#38ef7d]" + tr("-- CÀI ĐẶT CHẾ ĐỘ HUẤN LUYỆN UNIT PLAYER --", "-- UNIT TRAINING SETUP --") + "[]").padBottom(6f).row();
            c.add("[lightgray]" + tr("Map: huanluyenunitplayer.msav • Quái T1-T5 không tấn công • Rơi đồ 100% (độ hiếm giữ nguyên).",
                "Map: huanluyenunitplayer.msav • Non-attacking T1-T5 dummies • 100% drop rate (normal rarity).") + "[]")
                .wrap().width(wrapW).padBottom(10f).row();

            // 1) Bật/tắt cho phép trang bị nhiều pháo cùng 1 loại -> khi bật mở 2 lựa chọn chế độ mới
            Table dupCard = new Table(Styles.black6);
            dupCard.margin(8f).left().defaults().left();
            dupCard.check(tr("Cho phép trang bị nhiều pháo cùng 1 loại", "Allow equipping multiple turrets of the same type"),
                dupVal[0], (Boolc) b -> {
                    dupVal[0] = b;
                    panel.rebuild();
                }).width(wrapW - 12f).left().get().getLabel().setWrap(true);
            dupCard.row();

            if (dupVal[0]) {
                dupCard.add("[accent]" + tr("Chọn cơ chế khi trang bị nhiều pháo cùng loại:", "Choose duplicate turret mechanic:") + "[]")
                    .padTop(6f).padBottom(4f).row();

                Table opt1 = new Table(tmVal[0] == 0 ? Styles.black8 : Styles.black5);
                opt1.margin(8f).left();
                opt1.touchable = Touchable.enabled;
                opt1.add((tmVal[0] == 0 ? "[#38ef7d]● " : "[gray]○ ") + "[white]" + tr("1. Dồn lực (Tăng sát thương)[]", "1. Focus (Boost damage)[]")
                    + "\n[lightgray]" + tr("Trang bị từ 2 pháo cùng loại trở lên KHÔNG bắn thêm đạn mà tăng +25% sát thương gây ra cho chính pháo đó.",
                        "2+ of the same turret does NOT fire extra bullets; boosts that turret's damage by +25% per copy.") + "[]")
                    .left().wrap().width(wrapW - 28f);
                opt1.clicked(() -> { tmVal[0] = 0; panel.rebuild(); });
                dupCard.add(opt1).width(wrapW - 12f).padBottom(4f).row();

                Table opt2 = new Table(tmVal[0] == 1 ? Styles.black8 : Styles.black5);
                opt2.margin(8f).left();
                opt2.touchable = Touchable.enabled;
                opt2.add((tmVal[0] == 1 ? "[#38ef7d]● " : "[gray]○ ") + "[white]" + tr("2. Đa đạn (-10% tốc độ di chuyển)[]", "2. Multi-shot (-10% move speed)[]")
                    + "\n[lightgray]" + tr("Trang bị nhiều pháo cùng loại vẫn bắn thêm nhiều đạn hơn nhưng giảm 10% tốc độ di chuyển cho mỗi pháo cùng loại.",
                        "Equipping duplicate turrets fires extra shots, but reduces movement speed by 10% per extra duplicate turret.") + "[]")
                    .left().wrap().width(wrapW - 28f);
                opt2.clicked(() -> { tmVal[0] = 1; panel.rebuild(); });
                dupCard.add(opt2).width(wrapW - 12f).row();
            }
            c.add(dupCard).width(Math.max(160f, w - 8f)).padBottom(8f).row();

            // 2) Tùy chỉnh số đợt tối đa
            Table mwCard = new Table(Styles.black6);
            mwCard.margin(8f).left().defaults().left();
            mwCard.add("[white]" + tr("Tùy chỉnh số đợt tối đa:", "Max training waves:") + "[]").padBottom(4f).row();
            Table mwRow = new Table();
            final TextField mwField = mwRow.field(String.valueOf(maxWVal[0]), txt -> {
                try { maxWVal[0] = Mathf.clamp(Integer.parseInt(txt.trim()), 1, 200); } catch (Throwable ignored) {}
            }).width(100f).get();
            mwField.setFilter(TextField.TextFieldFilter.digitsOnly);
            mwField.setMaxLength(3);
            mwRef[0] = mwField;
            mwRow.add("[lightgray]" + tr("đợt (1 - 200, mặc định 15)", "waves (1 - 200, default 15)") + "[]").padLeft(8f);
            mwCard.add(mwRow).row();
            c.add(mwCard).width(Math.max(160f, w - 8f)).padBottom(8f).row();

            // 3) Bật/tắt cho phép sao lưu 1 unit nhiều bản cùng 1 lúc
            Table msCard = new Table(Styles.black6);
            msCard.margin(8f).left();
            msCard.check(tr("Cho phép sao lưu 1 Unit thành nhiều bản cùng 1 lúc (1–5 ô)",
                "Allow saving multiple copies of 1 Unit at once (1–5 slots)"),
                multiSaveVal[0], (Boolc) b -> multiSaveVal[0] = b).width(wrapW - 12f).left().get().getLabel().setWrap(true);
            c.add(msCard).width(Math.max(160f, w - 8f)).padBottom(8f).row();

            Table ft = panel.footer();
            ft.clear();
            ft.margin(4f);
            ft.button(tr("[#38ef7d]Bắt Đầu Huấn Luyện[]", "[#38ef7d]Start Training[]"), Styles.flatBordert, () -> {
                try { if (mwRef[0] != null) maxWVal[0] = Mathf.clamp(Integer.parseInt(mwRef[0].getText().trim()), 1, 200); } catch (Throwable ignored) {}
                Core.settings.put(KEY_TRAIN_MAX_WAVES, maxWVal[0]);
                Core.settings.put(KEY_TRAIN_DUP_TURRET, dupVal[0]);
                Core.settings.put(KEY_TRAIN_TMODE, tmVal[0]);
                Core.settings.put(KEY_TRAIN_MULTI_SAVE, multiSaveVal[0]);
                panel.hide();
                NewMode.startTrainingMap(maxWVal[0], dupVal[0], tmVal[0], multiSaveVal[0]);
            }).height(44f).minWidth(190f).pad(4f);
            ft.button(tr("Quay Lại", "Back"), Styles.flatBordert, () -> {
                panel.hide();
                openUnitManagerFromMenu();
            }).height(44f).minWidth(110f).pad(4f);
        });
        panel.show();
    }

    public static void openUnitManagerFromMenu() {
        openUnitPickerDialog(false, true);
    }

    public static void openUnitPickerDialog(boolean allowClose) {
        openUnitPickerDialog(allowClose, false);
    }

    private static void openUnitPickerDialog(final boolean allowClose, final boolean manageOnly) {
        if (Vars.headless) return;
        if (activePickerPanel != null && activePickerPanel.dialog.isShown()) return;

        final MachineUi.Panel panel = MachineUi.panel(manageOnly
            ? tr("Kho Unit Đã Lưu (Tối đa 5 ô)", "Saved Unit Vault (Max 5 Slots)")
            : tr("PvP Chiếm Cứ — Chọn Unit Ra Trận", "PvP Domination — Choose Your Unit"));
        activePickerPanel = panel;

        panel.onBuild(() -> {
            final ArrayList<SavedUnit> slots = loadSavedUnits();
            float w = panel.innerW();

            Table hd = panel.header();
            hd.clear();
            if (manageOnly) {
                Table topBar = new Table(Styles.black6);
                topBar.margin(8f);
                topBar.add("[accent]" + tr("Ô đã lưu: ", "Saved slots: ") + slots.size() + "/" + MAX_SAVED_SLOTS + "[]").left().expandX();
                topBar.button(tr("[#38ef7d]+ Tạo Bản Sao Lưu Unit[]", "[#38ef7d]+ Create Unit Backup[]"), Styles.flatBordert, () -> {
                    panel.hide();
                    openTrainingSetupDialog();
                }).height(38f).minWidth(205f).right();
                hd.add(topBar).width(w).pad(3f).row();
            } else {
                Table topBar = new Table(Styles.black6);
                topBar.margin(6f);
                topBar.add("[accent]" + tr("Chọn 1 trong " + slots.size() + "/" + MAX_SAVED_SLOTS + " Unit đã lưu hoặc dùng Unit Rỗng:",
                    "Pick 1 of " + slots.size() + "/" + MAX_SAVED_SLOTS + " saved Units or deploy Empty:") + "[]").left().wrap().width(Math.max(160f, w - 20f));
                hd.add(topBar).width(w).pad(3f).row();
            }

            Table list = panel.content();
            list.clear();
            list.top().margin(4f);
            if (slots.isEmpty()) {
                list.add("[#ffd166]" + tr("Chưa có file Unit nào được lưu (0/5)!", "No saved Units yet (0/5)!") + "[]").padTop(10f).padBottom(6f).row();
                list.add("[lightgray]" + tr("Hãy nhấn nút [accent]+ Tạo Bản Sao Lưu Unit[] trong Kho Unit để cài đặt và huấn luyện Unit, Pilot, Pháo & Trang bị.\nHiện tại bạn chỉ có thể ra trận bằng [white]Unit Rỗng (Cơ bản)[].",
                    "Press [accent]+ Create Unit Backup[] in the Unit Vault to configure & train your Unit, Pilot, Turrets & Gear.\nFor now you can only deploy with an [white]Empty Unit (Default)[].") + "[]")
                    .wrap().width(Math.max(160f, w - 24f)).labelAlign(Align.center).padBottom(12f).row();
            } else {
                for (int i = 0; i < MAX_SAVED_SLOTS; i++) {
                    if (i < slots.size()) {
                        final int idx = i;
                        final SavedUnit su = slots.get(i);
                        boolean dead = !manageOnly && localSlotDead(idx);
                        float cdLeft = !manageOnly && slotCooldownOn() ? localSlotCdLeft(idx) : 0f;
                        String actLbl = manageOnly ? null
                            : dead ? tr("[scarlet]Đã hạ[]", "[scarlet]Dead[]")
                            : cdLeft > 0.5f ? ("[yellow]" + (int) Math.ceil(cdLeft) + "s[]")
                            : tr("Ra Trận", "Deploy");
                        Runnable actRun = manageOnly ? null : () -> {
                            if (localSlotDead(idx)) {
                                Vars.ui.showInfoToast(tr("[scarlet]Unit này đã bị hạ trong trận!", "[scarlet]This Unit is eliminated!"), 2f);
                                return;
                            }
                            float rem = slotCooldownOn() ? localSlotCdLeft(idx) : 0f;
                            if (rem > 0.5f) {
                                Vars.ui.showInfoToast(tr("[yellow]Unit này đang hồi chiêu: còn " + (int) Math.ceil(rem) + "s!", "[yellow]Unit on cooldown: " + (int) Math.ceil(rem) + "s left!"), 2f);
                                return;
                            }
                            applySavedUnitSelection(idx, su);
                            panel.hide();
                        };
                        Table card = buildUnitCard(su, Math.max(180f, w - 12f), i, actLbl, actRun,
                            manageOnly ? tr("Xóa", "Delete") : null,
                            manageOnly ? () -> {
                                slots.remove(idx);
                                persistSavedUnits(slots);
                                panel.rebuild();
                            } : null);
                        list.add(card).width(Math.max(180f, w - 12f)).pad(4f).row();
                    } else {
                        Table empty = new Table(Styles.black6);
                        empty.margin(10f);
                        empty.add("[gray]" + tr("Ô #" + (i + 1) + " — Trống (Nhấn + Tạo Bản Sao Lưu Unit để huấn luyện)", "Slot #" + (i + 1) + " — Empty (Press + Create Unit Backup to train)") + "[]").left().wrap().width(Math.max(150f, w - 34f));
                        list.add(empty).width(Math.max(180f, w - 12f)).pad(4f).row();
                    }
                }
            }

            Table ft = panel.footer();
            ft.clear();
            ft.margin(4f);
            if (!manageOnly) {
                boolean emptyDead = localSlotDead(EMPTY_SLOT_IDX);
                float emptyCd = slotCooldownOn() ? localSlotCdLeft(EMPTY_SLOT_IDX) : 0f;
                String emptyLbl = emptyDead ? tr("[scarlet]Unit Rỗng (Đã hạ)[]", "[scarlet]Empty Unit (Dead)[]")
                    : emptyCd > 0.5f ? tr("[yellow]Unit Rỗng (" + (int) Math.ceil(emptyCd) + "s)[]", "[yellow]Empty (" + (int) Math.ceil(emptyCd) + "s)[]")
                    : tr("Dùng Unit Rỗng (Cơ bản)", "Use Empty Unit (Default)");
                ft.button(emptyLbl, Styles.flatBordert, () -> {
                    if (localSlotDead(EMPTY_SLOT_IDX)) {
                        Vars.ui.showInfoToast(tr("[scarlet]Unit Rỗng đã bị hạ trong trận!", "[scarlet]Empty Unit is eliminated!"), 2f);
                        return;
                    }
                    float rem = slotCooldownOn() ? localSlotCdLeft(EMPTY_SLOT_IDX) : 0f;
                    if (rem > 0.5f) {
                        Vars.ui.showInfoToast(tr("[yellow]Unit Rỗng đang hồi chiêu: còn " + (int) Math.ceil(rem) + "s!", "[yellow]Empty Unit on cooldown: " + (int) Math.ceil(rem) + "s left!"), 2f);
                        return;
                    }
                    applySavedUnitSelection(EMPTY_SLOT_IDX, null);
                    panel.hide();
                }).height(44f).minWidth(210f).pad(4f);
            }
            if (allowClose || manageOnly) {
                ft.button(tr("Đóng", "Close"), Styles.flatBordert, panel::hide).height(44f).minWidth(120f).pad(4f);
            }
        });

        panel.dialog.hidden(() -> { if (activePickerPanel == panel) activePickerPanel = null; });
        panel.show();
    }

    private static Table buildUnitCard(SavedUnit su, float w, int slotIdx, String b1, Runnable r1) {
        return buildUnitCard(su, w, slotIdx, b1, r1, null, null);
    }

    private static Table buildUnitCard(SavedUnit su, float w, int slotIdx, String b1, Runnable r1, String b2, Runnable r2) {
        Table card = new Table(Styles.black8);
        card.margin(8f);
        UnitType ut = NewModePvp.tierType(Math.max(1, su.u > 0 ? su.u : NewModePvp.tierCap(su.lv)));
        TextureRegion reg = (ut != null && ut.uiIcon != null) ? ut.uiIcon : Core.atlas.find("error");
        card.add(new Image(reg)).size(48f).padRight(10f);

        String pilotTxt = su.pi > 0 ? NewModePvp.pilotNameById(su.pi) + " (Lv." + (su.pi < su.plv.length ? su.plv[su.pi] : 0) + ")" : tr("Không", "None");
        int gearCnt = 0, mythicCnt = 0;
        for (int i = 0; i < su.gEq.length; i++) {
            GearItems.Def gd = GearItems.def(su.gEq[i]);
            if (gd != null) {
                gearCnt++;
                if (gd.rarity == GearItems.RARITIES - 1) mythicCnt++;
            }
        }
        String slotPrefix = slotIdx >= 0 ? "[#ffd166]#" + (slotIdx + 1) + "[] " : "";
        String mwTag = su.maxWaves > 0 ? ("  [cyan][" + tr("Max ", "Max ") + su.maxWaves + " " + tr("đợt", "w") + "][]") : "";
        String title = slotPrefix + "[accent]" + (ut != null ? ut.localizedName : "Unit") + "[]  [white]Lv." + su.lv + "[]" + mwTag + "  [lightgray](" + tr("Lực chiến: ", "Power: ") + "[#7cfc00]" + su.power + "[])[]";
        String sub = "[lightgray]Pilot: [white]" + pilotTxt + "[]  •  " + tr("Pháo: ", "Turrets: ") + "[cyan]" + su.d.size() + "[]  •  "
            + tr("Trang bị: ", "Items: ") + "[#ffd166]" + gearCnt + "[]"
            + (mythicCnt > 0 ? "  [#ff4d6d](" + mythicCnt + " " + tr("Thần Thoại", "Mythic") + ")[]" : "");
        card.add(title + "\n" + sub).left().growX().wrap().minWidth(0);

        if (b1 != null) card.button(b1, Styles.flatBordert, r1).size(88f, 40f).padLeft(6f);
        if (b2 != null) card.button(b2, Styles.flatBordert, r2).size(66f, 40f).padLeft(4f);
        return card;
    }

    /** Client/Host gửi yêu cầu áp dụng Unit đã chọn lên Host (hoặc áp dụng trực tiếp khi là Host). */
    private static void applySavedUnitSelection(int slotIdx, SavedUnit su) {
        localLife.activeSlot = Mathf.clamp(slotIdx, 0, EMPTY_SLOT_IDX);
        localLife.waitingPick = false;
        String payload = localLife.activeSlot + "|" + (su == null ? "EMPTY" : encodeSavedUnit(su));
        NewModePvp.send(REQ_DOM_LOAD, payload);
        Vars.ui.showInfoToast(su == null
            ? tr("[accent]Đã ra trận bằng Unit Rỗng!", "[accent]Deployed with Empty Unit!")
            : tr("[green]Đã nạp Unit đã lưu: ", "[green]Loaded saved Unit: ") + su.name, 3f);
    }

    /** Host nhận gói REQ_DOM_LOAD và áp dụng toàn bộ chỉ số, unit, pilot, pháo, trang bị cho người chơi. */
    private static void handleLoadUnitReq(Player player, String payload) {
        if (!isActive() || player == null) return;
        DomLife lf = lifeOf(player.id);
        if ("SPEC".equals(payload)) {
            lf.spectating = true;
            lf.waitingPick = false;
            return;
        }
        if (lf.spectating) return;

        int slotIdx = EMPTY_SLOT_IDX;
        String body = payload == null ? "" : payload;
        int sep = body.indexOf('|');
        if (sep > 0) {
            try { slotIdx = Mathf.clamp(Integer.parseInt(body.substring(0, sep)), 0, EMPTY_SLOT_IDX); }
            catch (Throwable ignored) {}
            body = body.substring(sep + 1);
        }

        // Kiểm tra luật phòng phía máy chủ: cấm chọn lại ô đã bị loại hoặc đang hồi chiêu
        if (!allowRespawnAndSwap() && (lf.deadMask & (1 << slotIdx)) != 0) {
            NewModePvp.toast(player, tr("[scarlet]Unit này đã bị hạ trong trận!", "[scarlet]This Unit is eliminated!"));
            return;
        }
        if (allowRespawnAndSwap() && slotCooldownOn() && Time.time < lf.cdUntil[slotIdx]) {
            int rem = (int) Math.ceil((lf.cdUntil[slotIdx] - Time.time) / 60f);
            NewModePvp.toast(player, tr("[yellow]Unit này đang hồi chiêu: còn " + rem + "s!", "[yellow]Unit on cooldown: " + rem + "s left!"));
            return;
        }

        lf.activeSlot = slotIdx;
        lf.waitingPick = false;
        NewModePvp.respawnLock.remove(player.id);

        NewModePvp.St st = NewModePvp.bind(player);
        if (st == null) return;

        // Xóa dữ liệu cũ của người chơi trong trận này
        NewModePvp.wipeBuilt(st);
        st.d.clear();
        st.p.clear();
        st.gInv.clear();
        st.gInvSrc.clear();
        st.gBought.clear();
        st.pgift.clear();
        st.chHand.clear();
        st.chEq.clear();
        Arrays.fill(st.s, 0);
        Arrays.fill(st.gEq, -1);
        Arrays.fill(st.gEqBonus, 0);
        Arrays.fill(st.plv, 0);
        Arrays.fill(st.pexp, 0);
        st.gEqSrc = 0;
        st.gKey = Integer.MIN_VALUE;
        st.gSkills = 0;
        st.gMoveAtk = 0;
        st.gSkillAtk = 0;
        st.lv = 1;
        st.exp = 0;
        st.pts = 0;
        st.u = 1;
        st.pi = 0;
        st.tm = 0;
        st.trainWaves = 0;

        if (!body.isEmpty() && !"EMPTY".equals(body)) {
            SavedUnit su = decodeSavedUnit(body);
            if (su != null) {
                st.lv = Mathf.clamp(su.lv, 1, NewModePvp.LIMIT_MAXLV);
                st.u = Mathf.clamp(su.u, 1, NewModePvp.TIER_MAX);
                st.tm = Mathf.clamp(su.tm, 0, 2);
                st.pi = Mathf.clamp(su.pi, 0, NewModePvp.PILOT_COUNT);
                st.trainWaves = Math.max(0, su.maxWaves);
                for (int i = 0; i < st.s.length && i < su.s.length; i++) {
                    st.s[i] = Mathf.clamp(su.s[i], 0, 120);
                }
                for (int i = 0; i < st.plv.length && i < su.plv.length; i++) {
                    st.plv[i] = Mathf.clamp(su.plv[i], 0, NewModePvp.PILOT_MAX_LV);
                }
                for (int i = 0; i < st.pexp.length && i < su.pexp.length; i++) {
                    st.pexp[i] = Math.max(0, su.pexp[i]);
                }
                int maxGifts = Math.min(su.pgift.size(), NewModePvp.PILOT_MAX_GIFTS * NewModePvp.PILOT_COUNT);
                for (int i = 0; i < maxGifts; i++) st.pgift.add(su.pgift.get(i));

                int maxTurrets = Math.min(su.d.size(), NewModePvp.EQUIP_SLOT_CAP);
                for (int i = 0; i < maxTurrets; i++) {
                    st.d.add(su.d.get(i));
                    st.p.add(NewModePvp.EQUIPPED);
                }
                for (int i = 0; i < st.gEq.length && i < su.gEq.length; i++) {
                    GearItems.Def gd = GearItems.def(su.gEq[i]);
                    st.gEq[i] = (gd != null && gd.slot == i % GearItems.TYPES) ? gd.id : -1;
                }
                st.gEqSrc = su.gEqSrc;
                for (int i = 0; i < st.gEqBonus.length && i < su.gEqBonus.length; i++) {
                    st.gEqBonus[i] = Mathf.clamp(su.gEqBonus[i], 0, 3);
                }
                if (su.ch != null && !su.ch.isEmpty()) ChaosMode.dec(st, su.ch);
            }
        }

        st.gKey = Integer.MIN_VALUE;
        st.eqC = null;
        NewModePvp.changed();

        // Đổi unit ngay cho người chơi tại vị trí hiện tại hoặc Lõi
        Unit cur = player.unit();
        UnitType want = NewModePvp.tierType(NewModePvp.activeTier(st));
        if (cur != null && cur.isValid() && !cur.dead && want != null) {
            NewModePvp.swapUnit(player, cur, want);
        }
        NewModePvp.broadcast();
        NewModePvp.apiPersistStore();
    }

    // ===== Chụp & Mã hóa / Giải mã 5 Ô Unit Lưu (Core.settings) =====
    static SavedUnit captureFromState(NewModePvp.St st, int maxWaves) {
        SavedUnit su = new SavedUnit();
        su.savedAt = System.currentTimeMillis();
        su.maxWaves = Math.max(0, maxWaves);
        su.lv = st.lv;
        su.u = NewModePvp.activeTier(st);
        su.tm = st.tm;
        su.pi = st.pi;
        su.s = Arrays.copyOf(st.s, NewModePvp.STATS.length);
        su.plv = st.plv != null ? Arrays.copyOf(st.plv, NewModePvp.PILOT_COUNT) : new int[NewModePvp.PILOT_COUNT];
        su.pexp = st.pexp != null ? Arrays.copyOf(st.pexp, NewModePvp.PILOT_COUNT) : new int[NewModePvp.PILOT_COUNT];
        if (st.pgift != null) su.pgift.addAll(st.pgift);

        // Chỉ lưu pháo đang trang bị trên unit (EQUIPPED = -1), tối đa EQUIP_SLOT_CAP
        for (int i = 0; i < st.d.size() && i < st.p.size(); i++) {
            if (st.p.get(i) == NewModePvp.EQUIPPED && su.d.size() < NewModePvp.EQUIP_SLOT_CAP) {
                su.d.add(st.d.get(i));
            }
        }
        // Nếu chưa lắp pháo lên người nhưng có pháo đã mua/đặt -> lấy tối đa 4 pháo mạnh nhất mang theo!
        if (su.d.isEmpty() && !st.d.isEmpty()) {
            for (int i = 0; i < st.d.size() && su.d.size() < 4; i++) su.d.add(st.d.get(i));
        }

        su.gEq = Arrays.copyOf(st.gEq, GearItems.MAXSLOTS);
        su.gEqSrc = st.gEqSrc;
        su.gEqBonus = st.gEqBonus != null ? Arrays.copyOf(st.gEqBonus, GearItems.MAXSLOTS) : new int[GearItems.MAXSLOTS];
        su.ch = ChaosMode.enc(st);

        UnitType ut = NewModePvp.tierType(Math.max(1, su.u));
        su.name = (ut != null ? ut.localizedName : "Unit") + " Lv." + su.lv
            + (su.pi > 0 ? " • " + NewModePvp.pilotNameById(su.pi) : "");
        su.power = computePower(su);
        return su;
    }

    static int computePower(SavedUnit su) {
        int p = su.lv * 10 + su.u * 25 + su.d.size() * 18;
        for (int v : su.s) p += v * 6;
        if (su.pi > 0) p += 40 + (su.pi < su.plv.length ? su.plv[su.pi] * 12 : 0);
        for (int i = 0; i < su.gEq.length; i++) {
            GearItems.Def gd = GearItems.def(su.gEq[i]);
            if (gd != null) p += GearItems.power(gd, i < su.gEqBonus.length ? su.gEqBonus[i] : 0) * 5;
        }
        return p;
    }

    static String encodeSavedUnit(SavedUnit su) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"nm\":\"").append(su.name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        sb.append(",\"at\":").append(su.savedAt);
        sb.append(",\"lv\":").append(su.lv).append(",\"u\":").append(su.u).append(",\"tm\":").append(su.tm).append(",\"pi\":").append(su.pi);
        sb.append(",\"pw\":").append(su.power).append(",\"mw\":").append(su.maxWaves);
        sb.append(",\"s\":"); NewModePvp.arr(sb, su.s);
        sb.append(",\"plv\":"); NewModePvp.arr(sb, su.plv);
        sb.append(",\"pexp\":"); NewModePvp.arr(sb, su.pexp);
        sb.append(",\"pg\":"); NewModePvp.ints(sb, su.pgift);
        sb.append(",\"d\":"); NewModePvp.strs(sb, su.d);
        sb.append(",\"geq\":"); NewModePvp.arr(sb, su.gEq);
        sb.append(",\"gsrc\":").append(su.gEqSrc);
        sb.append(",\"gbn\":"); NewModePvp.arr(sb, su.gEqBonus);
        sb.append(",\"ch\":\"").append(su.ch == null ? "" : su.ch.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        sb.append('}');
        return sb.toString();
    }

    static SavedUnit decodeSavedUnit(String json) {
        try {
            JsonValue o = new JsonReader().parse(json);
            return readSavedUnitObj(o);
        } catch (Throwable err) {
            return null;
        }
    }

    static SavedUnit readSavedUnitObj(JsonValue o) {
        if (o == null || !o.isObject()) return null;
        SavedUnit su = new SavedUnit();
        su.name = o.getString("nm", "Unit");
        su.savedAt = o.getLong("at", 0L);
        su.lv = o.getInt("lv", 1);
        su.u = o.getInt("u", 1);
        su.tm = o.getInt("tm", 0);
        su.pi = o.getInt("pi", 0);
        su.s = NewModePvp.readInts(o.get("s"), NewModePvp.STATS.length, su.s);
        su.plv = NewModePvp.readInts(o.get("plv"), NewModePvp.PILOT_COUNT, su.plv);
        su.pexp = NewModePvp.readInts(o.get("pexp"), NewModePvp.PILOT_COUNT, su.pexp);
        JsonValue pg = o.get("pg");
        if (pg != null && pg.isArray()) for (JsonValue c = pg.child; c != null; c = c.next) su.pgift.add(c.asInt());
        JsonValue d = o.get("d");
        if (d != null && d.isArray()) for (JsonValue c = d.child; c != null; c = c.next) su.d.add(c.asString());
        su.gEq = NewModePvp.readInts(o.get("geq"), GearItems.MAXSLOTS, su.gEq);
        su.gEqSrc = o.getInt("gsrc", 0);
        su.gEqBonus = NewModePvp.readInts(o.get("gbn"), GearItems.MAXSLOTS, su.gEqBonus);
        su.ch = o.getString("ch", "");
        su.power = o.getInt("pw", computePower(su));
        su.maxWaves = o.getInt("mw", 0);
        return su;
    }

    public static ArrayList<SavedUnit> loadSavedUnits() {
        ArrayList<SavedUnit> out = new ArrayList<>();
        try {
            String raw = Core.settings.getString(KEY_SAVED_UNITS, "[]");
            if (raw == null || raw.isEmpty()) return out;
            JsonValue arr = new JsonReader().parse(raw);
            if (arr != null && arr.isArray()) {
                for (JsonValue c = arr.child; c != null && out.size() < MAX_SAVED_SLOTS; c = c.next) {
                    SavedUnit su = readSavedUnitObj(c);
                    if (su != null) out.add(su);
                }
            }
        } catch (Throwable err) {
            Log.err("[PvpDomination] loadSavedUnits failed", err);
        }
        return out;
    }

    public static void persistSavedUnits(ArrayList<SavedUnit> list) {
        try {
            StringBuilder sb = new StringBuilder("[");
            int cap = Math.min(MAX_SAVED_SLOTS, list.size());
            for (int i = 0; i < cap; i++) {
                if (i > 0) sb.append(',');
                sb.append(encodeSavedUnit(list.get(i)));
            }
            sb.append(']');
            Core.settings.put(KEY_SAVED_UNITS, sb.toString());
        } catch (Throwable err) {
            Log.err("[PvpDomination] persistSavedUnits failed", err);
        }
    }
}
