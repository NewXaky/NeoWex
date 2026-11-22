package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
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
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules;
import mindustry.game.Team;
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
    public static final String TAG_DOM = "newex-pvp-dom";
    public static final String TAG_DOM_SCORES = "newex-dom-scores";
    public static final String TAG_DOM_PTS = "newex-dom-pts";
    public static final String TAG_SAVE_PROMPT = "newex-pvp-saveprompt"; // "" | "deferred" | "saved"
    public static final String TAG_PLAY_TICKS = "newex-pvp-playticks";

    public static final String KEY_SAVED_UNITS = "newex-pvp-saved-units-v1";
    public static final int MAX_SAVED_SLOTS = 5;
    public static final int SAVE_TRIGGER_MINUTES = 10;
    public static final float SAVE_TRIGGER_TICKS = SAVE_TRIGGER_MINUTES * 60f * 60f; // 36,000 ticks

    public static final float SCORE_GOAL = 1000f;
    public static final float PTS_PER_POINT_SEC = 6.5f;
    public static final float CAPTURE_SECONDS = 6.5f;
    public static final float CORE_HEAL_RADIUS = 165f;
    public static final int REWARD_POINTS = 35;

    static final String REQ_DOM_LOAD = "newex-dom-load";
    static final String SYNC_DOM_KIND = "newex-dom-sync";
    static final String FX_CAP_KIND = "newex-dom-capfx";

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
    }

    // ===== Trạng thái trận Chiếm Cứ =====
    static final ArrayList<CapPoint> points = new ArrayList<>();
    static final HashMap<Integer, Float> scores = new HashMap<>();
    static final HashMap<Integer, Integer> rateCount = new HashMap<>();
    static boolean matchEnded = false;
    static int winnerTeamId = -1;
    static float syncTimer = 0f;
    static float healTimer = 0f;
    static float botAiTimer = 0f;
    static boolean pickerShownThisMatch = false;
    static float pickerDelay = 0f;
    static BaseDialog activePickerDialog = null;
    static BaseDialog activeSaveDialog = null;

    // Trạng thái theo dõi lưu Unit ở PvP Cày Cấp (pvpnewex3110)
    static float localPlayTicks = 0f;
    static float saveCheckTimer = 0f;
    static String localPromptState = ""; // "" | "deferred" | "saved"
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

    /** Lõi có đang bất tử trong PvP Chiếm Cứ không (chỉ mất bất tử khi trận đã phân thắng bại). */
    public static boolean coresInvul() {
        return isActive() && !matchEnded;
    }

    public static boolean isMatchEnded() {
        return matchEnded;
    }

    static boolean isSoloBot(Unit u) {
        return u != null && soloBotUnit != null && u.id == soloBotUnit.id;
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

        // Đăng ký gói nhận Unit đã lưu từ Client -> Host
        NewModePvp.onReq(REQ_DOM_LOAD, 250, PvpDomination::handleLoadUnitReq);

        // Đăng ký gói nhận đồng bộ Cứ Điểm & Điểm từ Host -> Client
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    Vars.netClient.addPacketHandler(SYNC_DOM_KIND, PvpDomination::onSyncPacket);
                    Vars.netClient.addPacketHandler(FX_CAP_KIND, PvpDomination::onCapFxPacket);
                } catch (Throwable err) {
                    Log.err("[PvpDomination] client packet handler failed", err);
                }
                addHudElements();
            });
            Events.run(Trigger.draw, PvpDomination::drawWorldFx);
            Events.run(Trigger.uiDrawEnd, PvpDomination::drawDominationBar);
        }

        Events.run(Trigger.update, PvpDomination::tick);
    }

    public static void onWorldLoad() {
        points.clear();
        scores.clear();
        rateCount.clear();
        matchEnded = false;
        winnerTeamId = -1;
        syncTimer = 0f;
        healTimer = 0f;
        botAiTimer = 0f;
        soloBotUnit = null;
        soloBotTeam = null;
        soloBotRespawn = 0f;
        pickerShownThisMatch = false;
        pickerDelay = 35f;
        if (activePickerDialog != null) { try { activePickerDialog.hide(); } catch (Throwable ignored) {} activePickerDialog = null; }
        if (activeSaveDialog != null) { try { activeSaveDialog.hide(); } catch (Throwable ignored) {} activeSaveDialog = null; }
        savePromptOpen = false;
        saveCheckTimer = 60f;

        StringMap tags = NewModePvp.getTags();
        if (tags != null) {
            localPromptState = tags.get(TAG_SAVE_PROMPT, "");
            try { localPlayTicks = Float.parseFloat(tags.get(TAG_PLAY_TICKS, "0")); }
            catch (Throwable ignored) { localPlayTicks = 0f; }
        } else {
            localPromptState = "";
            localPlayTicks = 0f;
        }

        if (isActive()) {
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
        }
    }

    /** Gọi từ NewMode khi người chơi bấm "Xóa Save & Chơi Mới" trên map PvP Cày Cấp. */
    public static void onPvpSaveDeleted() {
        localPromptState = "";
        localPlayTicks = 0f;
        savePromptOpen = false;
    }

    public static void onBegin() {
        NewModePvp.onBegin();
        Rules r = Vars.state.rules;
        r.tags.put(TAG_DOM, "1");
        r.tags.put(ChaosMode.TAG, "0"); // Tắt Hỗn loạn để mọi trang bị GearItems lưu từ PvP Cày Cấp luôn hoạt động đầy đủ
        r.tags.put(GearItems.TAG_SLOTS, String.valueOf(GearItems.MAXSLOTS));
        r.waves = false;
        r.waveTimer = false;
        r.attackMode = false;
        r.pvp = true;
        r.winWave = 0;
        r.spawns = new Seq<>();

        points.clear();
        scores.clear();
        rateCount.clear();
        matchEnded = false;
        winnerTeamId = -1;
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

        // 1) Trong PvP Cày Cấp (không phải Chiếm Cứ): theo dõi điều kiện lưu Unit (10 phút hoặc có item hiếm nhất)
        if (NewModePvp.isFull() && !isActive()) {
            tickPvpLevelingSaveTrigger();
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

        // Giảm hiệu ứng flash trên các cứ điểm
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            if (cp.flashT > 0f) cp.flashT = Math.max(0f, cp.flashT - Time.delta);
        }

        if (Vars.net.client()) return;

        // Server / Host cập nhật logic chiếm cứ điểm, hồi máu tại lõi, điểm số và bot solo
        if (points.isEmpty()) scanCapturePoints();
        tickCoreFountain();
        tickSoloBot();
        tickCaptureAndScore();

        syncTimer -= Time.delta;
        if (syncTimer <= 0f) {
            syncTimer = 10f;
            broadcastDomState();
        }
    }

    /** Hồi máu nhanh cho unit đồng minh đứng gần Lõi spawn bất tử. */
    private static void tickCoreFountain() {
        healTimer -= Time.delta;
        if (healTimer > 0f) return;
        healTimer = 15f;
        float dtSec = 15f / 60f;
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

        // Điều khiển bot di chuyển đến cứ điểm chưa thuộc phe địch gần nhất
        CapPoint target = null;
        float bestScore = -1e9f;
        for (int i = 0; i < points.size(); i++) {
            CapPoint cp = points.get(i);
            if (cp.ownerTeamId == enemyTeam.id && cp.progress >= 0.99f && !cp.contested) continue;
            float dist = Mathf.dst(soloBotUnit.x, soloBotUnit.y, cp.x, cp.y);
            float prio = (cp.ownerTeamId == -1 ? 400f : 250f) - dist * 0.35f;
            if (prio > bestScore) { bestScore = prio; target = cp; }
        }
        if (target == null) target = points.get(0);

        float dist = Mathf.dst(soloBotUnit.x, soloBotUnit.y, target.x, target.y);
        if (dist > target.radius * 0.45f) {
            tmpVec.set(target.x - soloBotUnit.x, target.y - soloBotUnit.y).setLength(soloBotUnit.speed());
            soloBotUnit.movePref(tmpVec);
        } else {
            // Di chuyển vòng nhẹ trong cứ điểm
            float ang = Time.time * 1.4f;
            float tx = target.x + Mathf.cosDeg(ang) * target.radius * 0.35f;
            float ty = target.y + Mathf.sinDeg(ang) * target.radius * 0.35f;
            tmpVec.set(tx - soloBotUnit.x, ty - soloBotUnit.y).limit(soloBotUnit.speed() * 0.6f);
            soloBotUnit.movePref(tmpVec);
        }

        // Tự động ngắm bắn địch ở gần
        Unit foe = Units.closestEnemy(enemyTeam, soloBotUnit.x, soloBotUnit.y, soloBotUnit.range() * 1.15f, u -> u != null && !u.dead);
        if (foe != null) {
            soloBotUnit.aimLook(foe.x, foe.y);
            soloBotUnit.controlWeapons(true, true);
        } else {
            soloBotUnit.controlWeapons(false, false);
        }
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
                float ns = scores.getOrDefault(cp.ownerTeamId, 0f) + PTS_PER_POINT_SEC * dtSec;
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
        scores.put(winner.id, SCORE_GOAL);
        broadcastDomState();

        String banner = "[#" + winner.color + "]⚡ " + winner.localized() + "[] " +
            tr("đã nạp đầy thanh Chiếm Cứ! Lõi đối phương đang bị phá hủy!",
               "filled the Domination bar! Enemy cores are being destroyed!");
        NewModePvp.toastAll(banner, 5f);

        // Phá hủy toàn bộ lõi của các phe đối phương bằng chuỗi nổ lõi của NewModePvp
        boolean destroyedAny = false;
        Seq<Team> tl = NewModePvp.pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            Team tm = tl.get(i);
            if (tm == winner || tm == Team.derelict || tm == Team.crux) continue;
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

    // ===== UI Thanh Tiến Độ Đường Thẳng (trên đầu màn hình) =====
    private static void drawDominationBar() {
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

        float sw = Core.graphics.getWidth();
        float sh = Core.graphics.getHeight();
        float u = Scl.scl(Vars.mobile ? 0.88f : 1.0f);
        float panelW = Math.min(sw - 24f * u, (Vars.mobile ? 370f : 480f) * u);
        float panelH = (points.isEmpty() ? 52f : 74f) * u;
        float cx = sw * 0.5f;
        float topY = sh - (Vars.mobile ? 56f : 44f) * u;

        // Khung nền kính tối
        Draw.color(0.04f, 0.06f, 0.11f, 0.80f);
        Fill.rect(cx, topY - panelH * 0.5f, panelW, panelH);
        Lines.stroke(1.5f * u);
        Draw.color(0.35f, 0.78f, 1f, 0.55f);
        Lines.rect(cx - panelW * 0.5f, topY - panelH, panelW, panelH);

        // Tiêu đề & số điểm 2 phe
        float textY = topY - 8f * u;
        Fonts.outline.getData().setScale(0.72f * u);
        Fonts.outline.setColor(leftTeam.color);
        String lTxt = leftTeam.localized() + " " + Math.round(sLeft) + "/" + (int) SCORE_GOAL
            + (cntL > 0 ? " (+" + Strings.fixed(cntL * PTS_PER_POINT_SEC, 1) + "/s)" : "");
        Fonts.outline.draw(lTxt, cx - panelW * 0.5f + 10f * u, textY, Align.left);

        Fonts.outline.setColor(rightTeam.color);
        String rTxt = (cntR > 0 ? "(+" + Strings.fixed(cntR * PTS_PER_POINT_SEC, 1) + "/s) " : "")
            + Math.round(sRight) + "/" + (int) SCORE_GOAL + " " + rightTeam.localized();
        Fonts.outline.draw(rTxt, cx + panelW * 0.5f - 10f * u, textY, Align.right);

        // 1 ĐƯỜNG THẲNG TIẾN ĐỘ ĐỐI ĐẦU (2 nửa nạp tiến vào tâm mốc 100%, dễ nhìn cực kỳ rõ ràng!)
        float barW = panelW - 24f * u;
        float barH = 12f * u;
        float barY = topY - 34f * u;
        float halfW = barW * 0.5f - 3f * u;

        // Nền thanh trái & phải
        Draw.color(0.14f, 0.16f, 0.22f, 0.92f);
        Fill.rect(cx - halfW * 0.5f - 2f * u, barY, halfW, barH);
        Fill.rect(cx + halfW * 0.5f + 2f * u, barY, halfW, barH);

        // Đường nạp phe trái (chạy từ trái sang tâm)
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

        // Đường nạp phe phải (chạy từ phải sang tâm)
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

        // Viền ngoài đường thẳng & vạch đích ở giữa
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

    // ===== Nút HUD nhỏ gọn: [Đổi Unit] trong Chiếm Cứ & [Lưu Unit] khi bảo lưu trong PvP Cày Cấp =====
    private static void addHudElements() {
        if (Vars.headless || Vars.ui == null || Vars.ui.hudGroup == null) return;
        try {
            Table box = new Table();
            box.name = "newex-dom-hud-btns";
            box.top().right();
            box.setFillParent(true);
            box.touchable = Touchable.childrenOnly;

            // Nút [Đổi Unit] khi đang ở PvP Chiếm Cứ
            TextButton swapBtn = new TextButton(tr("Đổi Unit", "Switch Unit"), Styles.flatBordert);
            swapBtn.clicked(() -> {
                if (!isActive()) return;
                if (!NewModePvp.nearOwnCore() && Vars.player != null && Vars.player.unit() != null && !Vars.player.unit().dead) {
                    Vars.ui.showInfoToast(tr("[scarlet]Hãy về gần Lõi nhà để đổi Unit đã lưu!", "[scarlet]Return near your Core to switch saved Unit!"), 2.5f);
                    return;
                }
                openUnitPickerDialog(true);
            });
            swapBtn.visibility = () -> isActive() && Vars.state != null && Vars.state.isGame();

            // Nút [Lưu Unit] khi đã đủ điều kiện lưu (10 phút hoặc có đồ Thần Thoại) và người chơi nhấn "Không (Bảo lưu)" trong PvP Cày Cấp
            TextButton saveBtn = new TextButton(tr("[#ffd166]Lưu Unit[]", "[#ffd166]Save Unit[]"), Styles.flatBordert);
            saveBtn.clicked(() -> openSaveUnitPromptDialog(tr("Lưu Unit theo yêu cầu (từ chế độ bảo lưu)", "Save Unit (from deferred prompt)")));
            saveBtn.visibility = () -> NewModePvp.isFull() && !isActive()
                && ("deferred".equals(localPromptState) || "saved".equals(localPromptState))
                && Vars.state != null && Vars.state.isGame();

            Table inner = new Table();
            inner.add(swapBtn).size(116f, 36f).pad(4f).row();
            inner.add(saveBtn).size(116f, 36f).pad(4f).row();
            box.add(inner).top().right().padTop(Vars.mobile ? 110f : 92f).padRight(8f);
            Vars.ui.hudGroup.addChild(box);
        } catch (Throwable err) {
            Log.err("[PvpDomination] addHudElements failed", err);
        }
    }

    // ===== PHẦN 2: THEO DÕI ĐIỀU KIỆN LƯU UNIT TRONG PVP CÀY CẤP (10 PHÚT HOẶC CÓ ĐỒ HIẾM NHẤT) =====
    private static boolean hasHighestRarityEquipped(NewModePvp.St st) {
        if (st == null || st.gEq == null) return false;
        int topRar = GearItems.RARITIES - 1; // 4 = Thần thoại (Mythic)
        for (int i = 0; i < st.gEq.length; i++) {
            GearItems.Def d = GearItems.def(st.gEq[i]);
            if (d != null && d.rarity == topRar) return true;
        }
        return false;
    }

    private static void tickPvpLevelingSaveTrigger() {
        if (Vars.headless || Vars.player == null || Vars.state.gameOver) return;
        localPlayTicks += Time.delta;
        if (!Vars.net.client()) {
            // Đồng bộ thời gian chơi vào rules.tags định kỳ để khi Save & Resume vẫn nhớ mốc 10 phút
            StringMap tags = NewModePvp.getTags();
            if (tags != null && ((int) localPlayTicks) % 180 == 0) {
                tags.put(TAG_PLAY_TICKS, String.valueOf((int) Math.max(localPlayTicks, NewModePvp.matchT)));
            }
        }

        if (savePromptOpen || "deferred".equals(localPromptState) || "saved".equals(localPromptState)) return;

        saveCheckTimer -= Time.delta;
        if (saveCheckTimer > 0f) return;
        saveCheckTimer = 60f;

        NewModePvp.St st = NewModePvp.localState();
        if (st == null) return;

        float elapsed = Math.max(localPlayTicks, NewModePvp.matchT);
        boolean reached10Min = elapsed >= SAVE_TRIGGER_TICKS;
        boolean hasMythic = hasHighestRarityEquipped(st);

        if (reached10Min || hasMythic) {
            String reason = hasMythic
                ? tr("Bạn đã trang bị vật phẩm cấp hiếm nhất (Thần Thoại)!", "You equipped a Mythic (highest rarity) item!")
                : tr("Đã đạt mốc 10 phút cày cấp!", "You reached 10 minutes of play!");
            openSaveUnitPromptDialog(reason);
        }
    }

    private static void setPromptState(String state) {
        localPromptState = state;
        StringMap tags = NewModePvp.getTags();
        if (tags != null) {
            tags.put(TAG_SAVE_PROMPT, state);
            tags.put(TAG_PLAY_TICKS, String.valueOf((int) Math.max(localPlayTicks, NewModePvp.matchT)));
        }
    }

    /** Hộp thoại hỏi người chơi có muốn lưu file Unit hiện tại vào 5 ô lưu hay không. */
    public static void openSaveUnitPromptDialog(final String reason) {
        if (Vars.headless || Vars.player == null) return;
        final NewModePvp.St st = NewModePvp.localState();
        if (st == null) return;

        savePromptOpen = true;
        final SavedUnit snap = captureFromState(st);
        final ArrayList<SavedUnit> slots = loadSavedUnits();

        final BaseDialog d = new BaseDialog(tr("Lưu File Unit (PvP Chiếm Cứ)", "Save Unit File (PvP Domination)"));
        activeSaveDialog = d;
        d.cont.margin(12f);

        float w = Math.min(Core.graphics.getWidth() / Scl.scl() - 40f, 520f);
        d.cont.add("[#ffd166]" + reason + "[]").wrap().width(w).labelAlign(Align.center).padBottom(8f).row();
        d.cont.add("[white]" + tr("Bạn có muốn lưu file unit hiện tại để mang vào chế độ [accent]PvP Chiếm Cứ[] không?\n(Sau khi nhấn Đồng ý, trận đấu vẫn tiếp tục như bình thường!)",
            "Do you want to save your current unit build for [accent]PvP Domination[]?\n(Clicking Yes will save and let you keep playing normally!)") + "[]")
            .wrap().width(w).labelAlign(Align.center).padBottom(10f).row();

        // Thẻ tóm tắt Unit hiện tại
        d.cont.add(buildUnitCard(snap, w - 16f, -1, null, null)).width(w - 16f).padBottom(10f).row();

        d.cont.add("[lightgray]" + tr("Ô lưu hiện tại: ", "Saved slots: ") + "[accent]" + slots.size() + "/" + MAX_SAVED_SLOTS + "[]").padBottom(8f).row();

        Table btns = new Table();
        btns.button(tr("[green]Đồng ý (Lưu Unit)[]", "[green]Yes (Save Unit)[]"), Styles.flatBordert, () -> {
            if (slots.size() < MAX_SAVED_SLOTS) {
                slots.add(snap);
                persistSavedUnits(slots);
                setPromptState("saved");
                savePromptOpen = false;
                d.hide();
                Vars.ui.showInfoToast(tr("[green]Đã lưu file Unit vào Ô #" + slots.size() + "/" + MAX_SAVED_SLOTS + "! Tiếp tục chơi bình thường.",
                    "[green]Saved Unit to Slot #" + slots.size() + "/" + MAX_SAVED_SLOTS + "! Continue playing."), 4f);
            } else {
                d.hide();
                openOverwriteSlotDialog(snap, slots);
            }
        }).size(210f, 48f).pad(6f);

        btns.button(tr("[lightgray]Không (Bảo lưu)[]", "[lightgray]No (Keep Deferred)[]"), Styles.flatBordert, () -> {
            setPromptState("deferred");
            savePromptOpen = false;
            d.hide();
            Vars.ui.showInfoToast(tr("[accent]Đã bảo lưu lời nhắc! Bạn có thể bấm nút [Lưu Unit] bất kỳ lúc nào cho tới khi xóa save map.",
                "[accent]Deferred! You can press [Save Unit] anytime before deleting the map save."), 4f);
        }).size(210f, 48f).pad(6f);

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
                setPromptState("saved");
                savePromptOpen = false;
                d.hide();
                Vars.ui.showInfoToast(tr("[green]Đã ghi đè Unit vào Ô #" + (idx + 1) + "/5! Tiếp tục chơi bình thường.",
                    "[green]Overwrote Slot #" + (idx + 1) + "/5! Continue playing."), 4f);
            })).width(w - 24f).pad(4f).row();
        }
        d.cont.add(new ScrollPane(list)).width(w).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.65f).row();
        d.buttons.button(tr("Hủy (Bảo lưu)", "Cancel (Defer)"), () -> {
            setPromptState("deferred");
            savePromptOpen = false;
            d.hide();
        }).size(200f, 44f);
        d.hidden(() -> {
            savePromptOpen = false;
            if (activeSaveDialog == d) activeSaveDialog = null;
        });
        d.show();
    }

    // ===== PHẦN 3: CHỌN UNIT ĐÃ LƯU KHI VÀO PVP CHIẾM CỨ (VÀ QUẢN LÝ 5 Ô TỪ NEWMODE) =====
    public static void openUnitManagerFromMenu() {
        openUnitPickerDialog(false, true);
    }

    public static void openUnitPickerDialog(boolean allowClose) {
        openUnitPickerDialog(allowClose, false);
    }

    private static void openUnitPickerDialog(final boolean allowClose, final boolean manageOnly) {
        if (Vars.headless) return;
        if (activePickerDialog != null && activePickerDialog.isShown()) return;

        final ArrayList<SavedUnit> slots = loadSavedUnits();
        final BaseDialog d = new BaseDialog(manageOnly
            ? tr("Kho Unit Đã Lưu (Tối đa 5 ô)", "Saved Unit Vault (Max 5 Slots)")
            : tr("PvP Chiếm Cứ — Chọn Unit Ra Trận", "PvP Domination — Choose Your Unit"));
        activePickerDialog = d;
        float w = Math.min(Core.graphics.getWidth() / Scl.scl() - 36f, 560f);

        d.cont.margin(10f);
        if (slots.isEmpty()) {
            d.cont.add("[#ffd166]" + tr("Chưa có file Unit nào được lưu (0/5)!", "No saved Units yet (0/5)!") + "[]").padBottom(6f).row();
            d.cont.add("[lightgray]" + tr("Hãy chơi bản đồ [accent]PvP Cày Cấp (Cùng bạn bè)[] đủ 10 phút hoặc trang bị vật phẩm Thần Thoại để lưu Unit, Pilot, Pháo và Trang bị vào đây.\nHiện tại bạn chỉ có thể ra trận bằng [white]Unit Rỗng (Cơ bản)[].",
                "Play [accent]PvP Leveling[] for 10 minutes or equip a Mythic item to save your Unit, Pilot, Turrets & Gear here.\nFor now you can only deploy with an [white]Empty Unit (Default)[].") + "[]")
                .wrap().width(w - 20f).labelAlign(Align.center).padBottom(12f).row();
        } else {
            d.cont.add("[accent]" + tr("Chọn 1 trong " + slots.size() + "/" + MAX_SAVED_SLOTS + " Unit đã lưu hoặc dùng Unit Rỗng:",
                "Pick 1 of " + slots.size() + "/" + MAX_SAVED_SLOTS + " saved Units or deploy Empty:") + "[]").padBottom(8f).row();

            Table list = new Table();
            list.top();
            for (int i = 0; i < MAX_SAVED_SLOTS; i++) {
                if (i < slots.size()) {
                    final int idx = i;
                    final SavedUnit su = slots.get(i);
                    Table card = buildUnitCard(su, w - 24f, i,
                        manageOnly ? null : tr("Ra Trận", "Deploy"),
                        manageOnly ? null : () -> {
                            applySavedUnitSelection(su);
                            d.hide();
                        },
                        tr("Xóa", "Delete"),
                        () -> {
                            slots.remove(idx);
                            persistSavedUnits(slots);
                            d.hide();
                            openUnitPickerDialog(allowClose, manageOnly);
                        });
                    list.add(card).width(w - 24f).pad(4f).row();
                } else {
                    Table empty = new Table(Styles.black6);
                    empty.margin(10f);
                    empty.add("[gray]" + tr("Ô #" + (i + 1) + " — Trống (Cày trong PvP Cày Cấp để lưu)", "Slot #" + (i + 1) + " — Empty (Farm in PvP Leveling to save)") + "[]").left();
                    list.add(empty).width(w - 24f).pad(4f).row();
                }
            }
            d.cont.add(new ScrollPane(list)).width(w).maxHeight(Core.graphics.getHeight() / Scl.scl() * 0.56f).padBottom(8f).row();
        }

        Table bot = new Table();
        if (!manageOnly) {
            bot.button(tr("Dùng Unit Rỗng (Cơ bản)", "Use Empty Unit (Default)"), Styles.flatBordert, () -> {
                applySavedUnitSelection(null);
                d.hide();
            }).size(240f, 46f).pad(5f);
        }
        if (allowClose || manageOnly) {
            bot.button(tr("Đóng", "Close"), Styles.flatBordert, d::hide).size(140f, 46f).pad(5f);
        }
        d.cont.add(bot).row();

        d.hidden(() -> { if (activePickerDialog == d) activePickerDialog = null; });
        d.show();
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
        String title = slotPrefix + "[accent]" + (ut != null ? ut.localizedName : "Unit") + "[]  [white]Lv." + su.lv + "[]  [lightgray](" + tr("Lực chiến: ", "Power: ") + "[#7cfc00]" + su.power + "[])[]";
        String sub = "[lightgray]Pilot: [white]" + pilotTxt + "[]  •  " + tr("Pháo: ", "Turrets: ") + "[cyan]" + su.d.size() + "[]  •  "
            + tr("Trang bị: ", "Items: ") + "[#ffd166]" + gearCnt + "[]"
            + (mythicCnt > 0 ? "  [#ff4d6d](" + mythicCnt + " " + tr("Thần Thoại", "Mythic") + ")[]" : "");
        card.add(title + "\n" + sub).left().growX().wrap().minWidth(0);

        if (b1 != null) card.button(b1, Styles.flatBordert, r1).size(88f, 40f).padLeft(6f);
        if (b2 != null) card.button(b2, Styles.flatBordert, r2).size(66f, 40f).padLeft(4f);
        return card;
    }

    /** Client/Host gửi yêu cầu áp dụng Unit đã chọn lên Host (hoặc áp dụng trực tiếp khi là Host). */
    private static void applySavedUnitSelection(SavedUnit su) {
        String payload = su == null ? "EMPTY" : encodeSavedUnit(su);
        NewModePvp.send(REQ_DOM_LOAD, payload);
        Vars.ui.showInfoToast(su == null
            ? tr("[accent]Đã ra trận bằng Unit Rỗng!", "[accent]Deployed with Empty Unit!")
            : tr("[green]Đã nạp Unit đã lưu: ", "[green]Loaded saved Unit: ") + su.name, 3f);
    }

    /** Host nhận gói REQ_DOM_LOAD và áp dụng toàn bộ chỉ số, unit, pilot, pháo, trang bị cho người chơi. */
    private static void handleLoadUnitReq(Player player, String payload) {
        if (!isActive() || player == null) return;
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

        if (payload != null && !payload.isEmpty() && !"EMPTY".equals(payload)) {
            SavedUnit su = decodeSavedUnit(payload);
            if (su != null) {
                st.lv = Mathf.clamp(su.lv, 1, NewModePvp.LIMIT_MAXLV);
                st.u = Mathf.clamp(su.u, 1, NewModePvp.TIER_MAX);
                st.tm = Mathf.clamp(su.tm, 0, 2);
                st.pi = Mathf.clamp(su.pi, 0, NewModePvp.PILOT_COUNT);
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
    static SavedUnit captureFromState(NewModePvp.St st) {
        SavedUnit su = new SavedUnit();
        su.savedAt = System.currentTimeMillis();
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
        sb.append(",\"pw\":").append(su.power);
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
