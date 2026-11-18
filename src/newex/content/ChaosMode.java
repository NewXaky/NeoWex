package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Font;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitSpawnEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Chế độ HỖN LOẠN của NewMode PvP.
 *
 * - Mỗi N phút trong trận (tối đa M lần; N, M, số thẻ tối đa mỗi người chỉnh trong Cài đặt) hiện giao diện thẻ bài ở layer cao nhất, buộc phải chọn 1 trong 3 thẻ mới tắt được.
 * - Cùng một thẻ có thể được chọn/gắn nhiều lần: hiệu lực CỘNG DỒN nhưng giảm dần (xem stackMul).
 * - Thẻ chọn xong vào "tay"; ở trang Pilot bấm "Trang bị thẻ" cạnh tên pilot để gắn thẻ vào pilot đó (không tháo, không chuyển).
 * - Thẻ chỉ có hiệu lực khi người gắn nó đang chiêu mộ đúng pilot đó. Hiệu lực của thẻ áp dụng cho CẢ HAI PHE.
 * - Bật Hỗn loạn: chỉ số nâng vô hạn, pháo hết giới hạn buff; sát thương % máu tối đa tối đa 99%, tỉ lệ bạo kích dư > 100% đổi thành sát thương bạo kích.
 */
final class ChaosMode {
    private ChaosMode() {}

    static final String KEY = "newex-pvp-chaos";
    static final String TAG = "newex-pvp-chaos-on";
    static final String REQ_PICK = "newex-pvp-chaos-pick";
    static final String REQ_EQUIP = "newex-pvp-chaos-equip";
    // Tùy chỉnh (Cài đặt NewMode PvP -> Chế độ Hỗn loạn); máy chủ ghi vào rules.tags lúc bắt đầu trận để client dùng cùng giá trị
    static final String KEY_PICKS = "newex-pvp-chaos-picks", KEY_ROUNDS = "newex-pvp-chaos-rounds", KEY_MINUTES = "newex-pvp-chaos-minutes";
    static final String TAG_PICKS = "newex-pvp-chaos-picks-t", TAG_ROUNDS = "newex-pvp-chaos-rounds-t", TAG_MINUTES = "newex-pvp-chaos-minutes-t";
    static final int DEF_PICKS = 3, LIM_PICKS = 20, DEF_ROUNDS = 3, LIM_ROUNDS = 20, DEF_MINUTES = 10, LIM_MINUTES = 120;
    static final int HIDE_CYCLE = 5 * 3600, HIDE_TICKS = 3600, MIN_TICKS = 3600;

    // chỉ số thẻ (xếp theo thứ tự người dùng mô tả, bỏ số 6 vì đó là luật chung)
    static final int C_STAT10 = 0, C_LV50 = 1, C_CORE1 = 2, C_BDMG = 3, C_HP250 = 4, C_NOCD = 5, C_LIVES = 6, C_LVMIN = 7, C_SHIELD = 8, C_VANISH = 9, C_PILOT2 = 10, CARDS = 11;

    static final class Card {
        final String itemName, fallback, viName, enName, viPro, enPro, viCon, enCon; final Color col;
        Card(String itemName, String fallback, String col, String viName, String enName, String viPro, String enPro, String viCon, String enCon) {
            this.itemName = itemName; this.fallback = fallback; this.col = Color.valueOf(col);
            this.viName = viName; this.enName = enName; this.viPro = viPro; this.enPro = enPro; this.viCon = viCon; this.enCon = enCon;
        }
    }

    static final Card[] DECK = {
        new Card("newex-obsidis", "copper", "c084fc", "Chỉ số vượt hạn", "Overstat",
            "+10 cấp cho mọi thẻ ở trang chỉ số (cả hai phe).", "+10 levels on every stat card (both teams).",
            "Nhận 100% sát thương: giáp và mọi giảm sát thương nhận bị vô hiệu.", "Take 100% damage: armor and all damage reduction are nullified."),
        new Card("newex-starlight", "silicon", "ffd34d", "Bước nhảy cấp độ", "Level leap",
            "Mọi người chơi (địch lẫn ta) +50 cấp và +50 điểm nâng cấp.", "Every player (both teams) gets +50 levels and +50 upgrade points.",
            "Nhận EXP chỉ còn 10% (-90%).", "EXP gain is reduced to 10% (-90%)."),
        new Card("newex-uranilum", "thorium", "7df9ff", "Lò lính cuồng nhiệt", "Barracks frenzy",
            "+500% tốc độ sản xuất lính (cả hai phe).", "+500% soldier production speed (both teams).",
            "Lõi chỉ còn 1 máu (khiên lõi vẫn chặn sát thương).", "Cores drop to 1 HP (the core shield still absorbs damage)."),
        new Card("surge-alloy", "surge-alloy", "ff5a5a", "Pháo hủy diệt", "Doom turrets",
            "+500% sát thương của mọi pháo đã xây.", "+500% damage on every built turret.",
            "Không thể lắp pháo lên unit người chơi nữa (pháo đã lắp bị tháo về túi).", "Turrets can no longer be mounted on player units (mounted ones return to the bag)."),
        new Card("plastanium", "plastanium", "4dff88", "Thể trạng khổng lồ", "Colossal bodies",
            "+250% máu cho mọi unit toàn trận (địch lẫn ta).", "+250% HP for every unit in the match (both teams).",
            "Pháo vai trò Tấn công và Sát thương không còn bắn hay gây sát thương (cả lắp lẫn xây).", "Attack and Dealer role turrets stop firing and dealing damage (mounted and built)."),
        new Card("phase-fabric", "phase-fabric", "e9d5ff", "Thời gian ngừng trôi", "Frozen time",
            "Kĩ năng chủ động của pilot chỉ còn hồi chiêu 1 giây.", "Pilot active skills drop to a 1 second cooldown.",
            "Tốc độ bắn của pháo đã xây và pháo lắp giảm 80%.", "Fire rate of built and mounted turrets is reduced by 80%."),
        new Card("newex-raykstone", "thorium", "ffb347", "Năm mạng", "Five lives",
            "Mỗi lõi có 5 mạng: bị phá thì hồi sinh đầy máu cho tới khi hết mạng.", "Every core has 5 lives: when destroyed it revives at full HP until lives run out.",
            "Không thể xây thêm pháo.", "No more turrets can be built."),
        new Card("newex-berrybyr", "silicon", "39e75f", "Thăng tiến từng phút", "Rank per minute",
            "Mỗi phút mọi người chơi +1 cấp và +1 điểm nâng cấp.", "Every minute all players gain +1 level and +1 upgrade point.",
            "Nhận EXP chỉ còn 10% (-90%).", "EXP gain is reduced to 10% (-90%)."),
        new Card("newex-sallowyr", "titanium", "00a2ff", "Khiên hỗn loạn", "Chaos shield",
            "Mỗi phút unit người chơi và lính nhận 1 lớp khiên (độ bền = 90% máu + 10% giáp).", "Every minute player units and soldiers gain a shield (durability = 90% HP + 10% armor).",
            "Cứ 5 phút, toàn bộ chữ trong trận bị ẩn 1 phút.", "Every 5 minutes all text in the match is hidden for 1 minute."),
        new Card("scrap", "scrap", "ff8c42", "Hư vô nuốt quái", "Void devourer",
            "Quái wave vừa spawn lập tức biến mất; điểm EXP của chúng được chia đều cho mọi người chơi của cả hai phe.", "Wave monsters vanish the instant they spawn; their EXP is split evenly among every player of both teams.",
            "Chỉ số \"Tầm bắn & đạn của lính\" bị vô hiệu hóa (cả hai phe).", "The \"Soldier range & bullet life\" stat is disabled (both teams)."),
        new Card("spore-pod", "silicon", "b388ff", "Pilot thức tỉnh", "Pilot awakening",
            "Mọi chỉ số và hiệu ứng của pilot đang chiêu mộ tăng gấp đôi (cả hai phe).", "All stats and effects of recruited pilots are doubled (both teams).",
            "Bản đồ phủ sương mù đen: ngoài tầm nhìn là đen kịt, vùng đã đi qua cũng không được lưu lại.", "The map is covered in black fog: everything outside your vision is pitch black, explored areas are not remembered.")
    };

    static String tr(String vi, String en) { return NewModePvp.tr(vi, en); }
    static Item itemOf(Card c) {
        Item it = null;
        try { it = Vars.content.getByName(ContentType.item, c.itemName); } catch (Throwable ignored) {}
        if (it == null) { try { it = Vars.content.getByName(ContentType.item, c.fallback); } catch (Throwable ignored) {} }
        return it;
    }
    static String hex(Color c) { return "[#" + c.toString() + "]"; }

    // ------------------------------------------------------------------ trạng thái chung
    static int round = 0;                 // vòng thẻ hiện tại (do máy chủ tính, đồng bộ cho client)
    static boolean hideFlag = false;      // máy chủ báo: đang trong phút ẩn chữ
    static boolean[] act = new boolean[CARDS];
    static int[] cnt = new int[CARDS];     // số lần mỗi thẻ đang được kích hoạt (cộng dồn)
    static float actAt = -1000; static int actVer = -1;
    static int lvGranted = 0;              // số lần thẻ "Bước nhảy cấp độ" đã được thưởng
    static float minT = 0, shieldT = 0, lvAcc = 0;
    static Boolean fogOrig = null;         // giá trị rules.fog trước khi thẻ "Pilot thức tỉnh" bật sương mù
    static boolean staticOrig = true;      // rules.staticFog trước đó
    static Color dynOrig = null;           // rules.dynamicColor trước đó
    static final HashMap<Integer, Integer> lives = new HashMap<>();
    static final HashMap<Integer, Float> coreOrig = new HashMap<>();
    static final java.util.HashSet<Integer> touchedBuilds = new java.util.HashSet<>();

    static void reset() {
        round = 0; hideFlag = false; lvGranted = 0; minT = 0; shieldT = 0; lvAcc = 0; fogOrig = null; staticOrig = true; dynOrig = null;
        lives.clear(); coreOrig.clear(); touchedBuilds.clear(); act = new boolean[CARDS]; cnt = new int[CARDS]; actAt = -1000; actVer = -1;
    }

    /** Hỗn loạn đang bật? Ưu tiên cờ do máy chủ ghi vào rules.tags lúc bắt đầu trận; không có thì đọc cài đặt cục bộ. */
    static long onFrame = -1; static boolean onVal = false;
    static boolean on() {
        long fr = 0;
        try { fr = Core.graphics.getFrameId(); } catch (Throwable ignored) {}
        if (fr == onFrame) return onVal;   // tính 1 lần mỗi khung hình (hàm này được gọi rất nhiều lần)
        onFrame = fr; onVal = computeOn();
        return onVal;
    }
    static boolean computeOn() {
        try {
            if (!NewModePvp.isActive()) return false;
            arc.struct.StringMap tags = NewModePvp.getTags();
            if (tags != null && tags.containsKey(TAG)) return "1".equals(tags.get(TAG, "0"));
        } catch (Throwable ignored) {}
        return NewModePvp.setBool(KEY, false);
    }
    static void stampTag() {
        try {
            Vars.state.rules.tags.put(TAG, Core.settings.getBool(KEY, false) ? "1" : "0");
            Vars.state.rules.tags.put(TAG_PICKS, String.valueOf(clampI(Core.settings.getInt(KEY_PICKS, DEF_PICKS), 1, LIM_PICKS)));
            Vars.state.rules.tags.put(TAG_ROUNDS, String.valueOf(clampI(Core.settings.getInt(KEY_ROUNDS, DEF_ROUNDS), 1, LIM_ROUNDS)));
            Vars.state.rules.tags.put(TAG_MINUTES, String.valueOf(clampI(Core.settings.getInt(KEY_MINUTES, DEF_MINUTES), 1, LIM_MINUTES)));
        } catch (Throwable ignored) {}
    }
    static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    /** Đọc giá trị tùy chỉnh: ưu tiên bản máy chủ ghi trong rules.tags, không có thì dùng cài đặt cục bộ. */
    static int tagInt(String tag, String key, int def, int lo, int hi) {
        try {
            arc.struct.StringMap tags = NewModePvp.getTags();
            if (tags != null && tags.containsKey(tag)) return clampI(Integer.parseInt(tags.get(tag, String.valueOf(def))), lo, hi);
        } catch (Throwable ignored) {}
        return clampI(Core.settings.getInt(key, def), lo, hi);
    }
    static int maxPicks() { return tagInt(TAG_PICKS, KEY_PICKS, DEF_PICKS, 1, LIM_PICKS); }
    static int maxRounds() { return tagInt(TAG_ROUNDS, KEY_ROUNDS, DEF_ROUNDS, 1, LIM_ROUNDS); }
    static int roundMinutes() { return tagInt(TAG_MINUTES, KEY_MINUTES, DEF_MINUTES, 1, LIM_MINUTES); }
    static int roundTicks() { return roundMinutes() * 3600; }

    static void refresh() {
        if (Time.time - actAt < 20f && actVer == NewModePvp.stVer) return;
        actAt = Time.time; actVer = NewModePvp.stVer;
        boolean[] n = new boolean[CARDS];
        int[] c = new int[CARDS];
        if (on()) {
            for (NewModePvp.St st : NewModePvp.byId.values()) {
                if (st == null) continue;
                for (int[] e : st.chEq) if (e[0] == st.pi && e[1] >= 0 && e[1] < CARDS) { n[e[1]] = true; c[e[1]]++; }
            }
        }
        act = n; cnt = c;
    }
    static boolean has(int card) { if (!on()) return false; refresh(); return act[card]; }
    /** Số lần thẻ đang được kích hoạt (0 nếu không có). */
    static int stack(int card) { if (!on()) return 0; refresh(); return cnt[card]; }
    /** Hệ số cộng dồn giảm dần: 1, 1.5, 1.75, 1.875... (tối đa 2). Chỉ áp cho phần có lợi; phần bất lợi giữ nguyên hoặc nặng thêm riêng từng thẻ. */
    static float stackMul(int n) { return n <= 1 ? 1f : (float) (2.0 - Math.pow(0.5, n - 1)); }
    /** Hệ số nhân chỉ số pilot của thẻ "Pilot thức tỉnh": 1 khi không có, 2 / 2.5 / 2.75... khi cộng dồn. */
    static float pilotK() { int n = stack(C_PILOT2); return n <= 0 ? 1f : 1f + stackMul(n); }
    /** Thẻ "Hư vô nuốt quái": vô hiệu chỉ số Tầm bắn & đạn của lính. */
    static boolean soldierRangeOff() { return has(C_VANISH); }

    // ------------------------------------------------------------------ móc vào NewModePvp
    static boolean unlimited() { return on(); }
    static int statBonus() { int n = stack(C_STAT10); return n > 0 ? Math.round(10f * stackMul(n)) : 0; }
    static boolean noArmor() { return has(C_STAT10); }
    /** EXP còn 10%; cộng dồn thẻ nặng thêm: 10% / n. */
    static float expMul() { int n = Math.max(stack(C_LV50), stack(C_LVMIN)); return n > 0 ? 0.1f / n : 1f; }
    static float hpMul() { int n = stack(C_HP250); return n > 0 ? 1f + 2.5f * stackMul(n) : 1f; }
    /** Hồi chiêu pilot: 1 giây; cộng dồn: 1/n giây (tối thiểu 0.25 giây). */
    static int cd(int base) { int n = stack(C_NOCD); return n > 0 ? Math.min(base, Math.max(15, 60 / n)) : base; }
    static boolean slowTurrets() { return has(C_NOCD); }
    /** Chu kỳ làm chậm pháo -80%: chỉ cho chạy 1 trên 5 khung hình. */
    static boolean skipFrame() { int n = stack(C_NOCD); return n > 0 && ((int) Time.time) % (5 * n) != 0; }
    static boolean noMount() { return has(C_BDMG); }
    static boolean noBuild() { return has(C_LIVES); }
    static float barrackMul() { int n = stack(C_CORE1); return n > 0 ? 1f + 5f * stackMul(n) : 1f; }
    static float builtDmgMul() { int n = stack(C_BDMG); return n > 0 ? 1f + 5f * stackMul(n) : 1f; }
    static boolean silenced(String turretId) {
        if (!has(C_HP250) || turretId == null) return false;
        String role = NewModePvp.TURRET_ROLES.get(turretId);
        return NewModePvp.ROLE_ATTACK.equals(role) || NewModePvp.ROLE_DEALER.equals(role);
    }
    static float capRole(float cap, float v) { return on() ? v : Math.min(cap, v); }
    static float capCut(float cap, float v) { return on() ? Math.min(99f, v) : Math.min(cap, v); }
    static float armorCutCap(float v, float normalCap) { return on() ? Math.min(99f, v) : Math.min(normalCap, v); }
    /** % máu tối đa (đơn vị %): tối đa 99% khi Hỗn loạn. */
    static float pctMaxHp(int lvl, float perLevel) { float v = lvl * perLevel; return on() ? Math.min(99f, v) : v; }
    /** Hệ số bạo kích; Hỗn loạn: tỉ lệ vượt 100% đổi thành sát thương bạo kích. */
    static float critMul(int s5, int s6) {
        if (s5 <= 0) return 1f;
        float ch = s5 * 0.02f, extra = 0f;
        if (on() && ch > 1f) { extra = ch - 1f; ch = 1f; }
        return Math.random() < ch ? 1.5f + s6 * 0.12f + extra : 1f;
    }
    static float critChanceShown(int s5) { float v = s5 * 2f; return on() ? Math.min(100f, v) : v; }
    static float critDmgShown(int s5, int s6) { float v = 150f + s6 * 12f; if (on() && s5 * 2f > 100f) v += s5 * 2f - 100f; return v; }

    /** Trả về mảng chỉ số hiệu dụng sau khi cộng thẻ +10 (mảng cache theo từng người). */
    static int[] adjustStats(NewModePvp.St st, int[] base) {
        int b = statBonus();
        int[] g = GearItems.delta(st);   // vật phẩm (chỉ chế độ Chiến tranh)
        if ((b == 0 && g == null) || base == null) return base;
        int[] out = (st.chs != null && st.chs.length == base.length) ? st.chs : (st.chs = new int[base.length]);
        for (int i = 0; i < base.length; i++) {
            int v = base[i] + b + (g != null && i < g.length ? g[i] : 0);
            out[i] = g != null ? Math.max(GearItems.floorOf(i), v) : v;
        }
        return out;
    }

    /** Lõi sắp chết: nếu có thẻ "Năm mạng" thì trừ 1 mạng và hồi sinh đầy máu. true = đã chặn cái chết. */
    static boolean coreExtraLife(CoreBlock.CoreBuild cb) {
        if (!has(C_LIVES) || cb == null) return false;
        Integer left = lives.get(cb.id);
        int l = left == null ? Math.round(5f * stackMul(stack(C_LIVES))) : left;
        l--;
        lives.put(cb.id, l);
        if (l <= 0) return false;
        cb.health = cb.maxHealth;
        NewModePvp.toastAll(tr("[scarlet]Một lõi vừa mất 1 mạng! Còn " + l + " mạng.", "[scarlet]A core just lost a life! " + l + " lives left."), 4f);
        return true;
    }

    // ------------------------------------------------------------------ mã hóa trạng thái người chơi (lưu + đồng bộ)
    static String enc(NewModePvp.St st) {
        StringBuilder sb = new StringBuilder();
        sb.append(st.chRound).append(';').append(st.chPicks).append(';');
        for (int i = 0; i < st.chHand.size(); i++) { if (i > 0) sb.append(','); sb.append(st.chHand.get(i)); }
        sb.append(';');
        for (int i = 0; i < st.chEq.size(); i++) { if (i > 0) sb.append(','); sb.append(st.chEq.get(i)[0]).append(':').append(st.chEq.get(i)[1]); }
        return sb.toString();
    }
    static void dec(NewModePvp.St st, String s) {
        st.chHand.clear(); st.chEq.clear(); st.chRound = 0; st.chPicks = 0;
        if (s == null || s.isEmpty()) return;
        try {
            String[] p = s.split(";", -1);
            st.chRound = Integer.parseInt(p[0]); st.chPicks = Integer.parseInt(p[1]);
            if (p.length > 2 && !p[2].isEmpty()) for (String c : p[2].split(",")) st.chHand.add(Integer.parseInt(c));
            if (p.length > 3 && !p[3].isEmpty()) for (String c : p[3].split(",")) { String[] q = c.split(":"); st.chEq.add(new int[]{Integer.parseInt(q[0]), Integer.parseInt(q[1])}); }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ yêu cầu từ client
    static void define() {
        NewModePvp.onReq(REQ_PICK, 200, (player, payload) -> {
            if (!on() || !NewModePvp.isFull()) return;
            NewModePvp.St st = NewModePvp.bind(player);
            String[] a = payload.split("\\|");
            Integer r = NewModePvp.leadInt(a[0]), c = a.length > 1 ? NewModePvp.leadInt(a[1]) : null;
            if (r == null || c == null || c < 0 || c >= CARDS) return;
            if (r != round || r < 1 || r > maxRounds() || st.chRound >= r || st.chPicks >= maxPicks()) return;
            st.chHand.add(c); st.chRound = r; st.chPicks++;
            NewModePvp.toast(player, tr("[accent]Đã nhận thẻ: ", "[accent]Card received: ") + name(c) + tr(" - vào trang Pilot để trang bị.", " - open the Pilot page to equip it."));
            NewModePvp.changed();
        });
        NewModePvp.onReq(REQ_EQUIP, 200, (player, payload) -> {
            if (!on() || !NewModePvp.isActive()) return;
            NewModePvp.St st = NewModePvp.bind(player);
            String[] a = payload.split("\\|");
            Integer pi = NewModePvp.leadInt(a[0]), c = a.length > 1 ? NewModePvp.leadInt(a[1]) : null;
            if (pi == null || c == null || pi == 0 || !NewModePvp.validPilot(pi)) return;
            int idx = st.chHand.indexOf(c);
            if (idx < 0) return;
            st.chHand.remove(idx);
            st.chEq.add(new int[]{pi, c});
            NewModePvp.toast(player, tr("[accent]Đã trang bị thẻ ", "[accent]Equipped card ") + name(c) + tr(" cho ", " on ") + pilotName(pi) + tr(" (không thể tháo).", " (cannot be removed)."));
            NewModePvp.changed();
        });
    }

    static String name(int c) { return tr(DECK[c].viName, DECK[c].enName); }
    static String pilotName(int pi) {
        for (NewModePvp.PilotDef d : NewModePvp.pilotList()) if (d.pi == pi) return d.name;
        return tr("Không có pilot", "No pilot");
    }

    // ------------------------------------------------------------------ máy chủ: mỗi khung hình
    static void serverTick() {
        if (Vars.net.client() || !NewModePvp.isFull() || Vars.state.gameOver || Vars.state.isPaused() || !on()) return;
        float dt = Time.delta;
        // vòng chọn thẻ
        int due = Math.min(maxRounds(), (int) (NewModePvp.matchT / roundTicks()));
        if (due > round) {
            round = due;
            NewModePvp.toastAll(tr("[accent]HỖN LOẠN! Chọn 1 thẻ bài (vòng " + round + "/" + maxRounds() + ")", "[accent]CHAOS! Pick a card (round " + round + "/" + maxRounds() + ")"), 5f);
            botsPick();
            NewModePvp.changed();
        }
        // thẻ Khiên hỗn loạn: cộng dồn -> ẩn chữ lâu hơn (n phút mỗi chu kỳ 5 phút, tối đa 4 phút)
        int hideTicks = Math.min(HIDE_CYCLE - MIN_TICKS, HIDE_TICKS * Math.max(1, stack(C_SHIELD)));
        boolean nh = has(C_SHIELD) && NewModePvp.matchT >= HIDE_CYCLE && (NewModePvp.matchT % HIDE_CYCLE) < hideTicks;
        if (nh != hideFlag) { hideFlag = nh; NewModePvp.changed(); }

        // +50 cấp một lần
        // (cộng dồn: lần thứ k thưởng 50 x 0.5^(k-1) cấp, tối thiểu 10)
        int lvN = stack(C_LV50);
        while (lvGranted < lvN) {
            lvGranted++;
            final int give = Math.max(10, Math.round(50f * (float) Math.pow(0.5, lvGranted - 1)));
            for (NewModePvp.St st : NewModePvp.byId.values()) if (st != null) { st.lv += give; st.pts += give; }
            NewModePvp.toastAll(tr("[accent]Thẻ Bước nhảy cấp độ: mọi người chơi +" + give + " cấp!", "[accent]Level leap card: every player +" + give + " levels!"), 5f);
            NewModePvp.changed();
        }
        // +1 cấp mỗi phút
        if (has(C_LVMIN)) {
            minT += dt;
            if (minT >= MIN_TICKS) {
                minT -= MIN_TICKS;
                lvAcc += stackMul(stack(C_LVMIN));          // cộng dồn giảm dần: 1, 1.5, 1.75... cấp mỗi phút (phần lẻ dồn sang phút sau)
                final int give = (int) lvAcc;
                lvAcc -= give;
                if (give > 0) { for (NewModePvp.St st : NewModePvp.byId.values()) if (st != null) { st.lv += give; st.pts += give; } NewModePvp.changed(); }
            }
        } else { minT = 0; lvAcc = 0; }
        // khiên mỗi phút
        if (has(C_SHIELD)) {
            shieldT += dt;
            if (shieldT >= MIN_TICKS) { shieldT -= MIN_TICKS; giveShields(); }
        } else shieldT = 0;

        // tháo pháo lắp trên unit khi có thẻ "Pháo hủy diệt"
        if (has(C_BDMG)) {
            boolean any = false;
            for (NewModePvp.St st : NewModePvp.byId.values()) {
                if (st == null || st.p.size() != st.d.size()) continue;
                for (int i = 0; i < st.p.size(); i++) if (st.p.get(i) == NewModePvp.EQUIPPED) { st.p.set(i, NewModePvp.IN_BAG); any = true; }
            }
            if (any) NewModePvp.changed();
        }
        builtTurretsTick();
        coreHpTick();
        fogTick();
    }

    /**
     * Thẻ "Pilot thức tỉnh": bật sương mù ĐEN khi thẻ có hiệu lực, trả lại như cũ khi hết hiệu lực.
     * Tắt staticFog (không lưu vùng đã khám phá) và cho dynamicColor đen đặc (alpha 1)
     * => mọi nơi ngoài tầm nhìn đều đen kịt, không còn lớp sương mù xám "đi tới đâu mở tới đó".
     */
    static void fogTick() {
        try {
            mindustry.game.Rules r = Vars.state.rules;
            boolean want = has(C_PILOT2);
            if (want) {
                if (fogOrig == null) {
                    fogOrig = r.fog; staticOrig = r.staticFog;
                    dynOrig = r.dynamicColor == null ? null : r.dynamicColor.cpy();
                }
                boolean dark = r.dynamicColor != null && r.dynamicColor.r < 0.01f && r.dynamicColor.g < 0.01f && r.dynamicColor.b < 0.01f && r.dynamicColor.a > 0.99f;
                if (!r.fog || r.staticFog || !dark) {
                    r.fog = true;
                    r.staticFog = false;
                    r.dynamicColor = new Color(0f, 0f, 0f, 1f);
                    NewModePvp.syncRules();
                }
            } else if (fogOrig != null) {
                r.fog = fogOrig;
                r.staticFog = staticOrig;
                if (dynOrig != null) r.dynamicColor = dynOrig;
                fogOrig = null; dynOrig = null;
                NewModePvp.syncRules();
            }
        } catch (Throwable ignored) {}
    }

    /** Thẻ "Hư vô nuốt quái": quái wave vừa spawn biến mất ngay, EXP của chúng chia đều cho mọi người chơi (cả hai phe, gồm bot). */
    static void onWaveSpawn(Unit u) {
        if (Vars.net.client() || u == null || !has(C_VANISH) || !NewModePvp.isFull() || Vars.state.gameOver) return;
        if (u.team != Vars.state.rules.waveTeam) return;
        final float exp = NewModePvp.killExp(u);
        final ArrayList<Player> ps = new ArrayList<>();
        NewModePvp.eachPl(p -> { if (p != null) ps.add(p); });
        if (!ps.isEmpty() && exp > 0f) {
            float each = exp / ps.size();
            for (Player p : ps) { try { NewModePvp.addExp(p, each, true); } catch (Throwable ignored) {} }
            NewModePvp.changed();
        }
        try { Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable ignored) {} }
    }

    static void botsPick() {
        for (int i = 0; i < NewModePvp.bots.size(); i++) {
            try {
                Player bp = NewModePvp.bots.get(i).botPl;
                NewModePvp.St st = bp == null ? null : NewModePvp.byId.get(bp.id);
                if (st == null || st.chRound >= round || st.chPicks >= maxPicks()) continue;
                int c = Mathf.random(0, CARDS - 1);
                st.chRound = round; st.chPicks++;
                st.chEq.add(new int[]{st.pi, c});   // bot tự gắn luôn vào pilot hiện tại
            } catch (Throwable ignored) {}
        }
    }

    static void giveShields() {
        int n = 0;
        for (Map.Entry<Integer, NewModePvp.St> e : NewModePvp.byId.entrySet()) {
            Unit u = null;
            try { u = NewModePvp.plUnit(e.getKey()); } catch (Throwable ignored) {}
            if (u != null && u.isValid() && !u.dead) { shield(u); n++; }
            ArrayList<Integer> ids = NewModePvp.soldiers.get(e.getKey());
            if (ids != null) for (int id : ids) { Unit s = Groups.unit.getByID(id); if (s != null && s.isValid() && !s.dead) { shield(s); n++; } }
        }
        if (n > 0) NewModePvp.toastAll(tr("[#00a2ff]Khiên hỗn loạn đã kích hoạt!", "[#00a2ff]Chaos shields activated!"), 3f);
    }
    static void shield(Unit u) { u.shield = Math.max(u.shield, (u.maxHealth * 0.9f + u.armor * 0.1f) * stackMul(stack(C_SHIELD))); }

    /** Pháo đã xây: tắt hẳn nếu bị thẻ Thể trạng khổng lồ vô hiệu; hoặc cho chạy 1/5 khung hình (-80% tốc bắn). */
    static void builtTurretsTick() {
        boolean silence = has(C_HP250), slow = has(C_NOCD);
        if (!silence && !slow) {
            if (!touchedBuilds.isEmpty()) {
                for (int id : touchedBuilds) { try { Building b = Groups.build.getByID(id); if (b != null) b.enabled = true; } catch (Throwable ignored) {} }
                touchedBuilds.clear();
            }
            return;
        }
        NewModePvp.Defs defs = NewModePvp.droneDefs();
        boolean frame = ((int) Time.time) % (5 * Math.max(1, stack(C_NOCD))) == 0;
        for (NewModePvp.St st : NewModePvp.byId.values()) {
            if (st == null || st.p.size() != st.d.size()) continue;
            for (int i = 0; i < st.d.size(); i++) {
                int pos = st.p.get(i);
                if (pos < 0) continue;
                NewModePvp.Def d = defs.map.get(st.d.get(i));
                Building b = Vars.world.build(pos);
                if (d == null || b == null || !b.isValid()) continue;
                boolean off = silenced(d.id) || (slow && !frame);
                b.enabled = !off;
                touchedBuilds.add(b.id);
            }
        }
    }

    static void coreHpTick() {
        boolean one = has(C_CORE1);
        Seq<Team> tl = NewModePvp.pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            Seq<CoreBlock.CoreBuild> cores = tl.get(i).cores();
            for (int j = 0; j < cores.size; j++) {
                CoreBlock.CoreBuild cb = cores.get(j);
                if (cb == null || !cb.isValid()) continue;
                if (one) {
                    if (!coreOrig.containsKey(cb.id)) coreOrig.put(cb.id, cb.maxHealth);
                    if (cb.maxHealth != 1f) cb.maxHealth = 1f;
                    if (cb.health > 1f) cb.health = 1f;
                } else if (coreOrig.containsKey(cb.id)) {
                    float o = coreOrig.remove(cb.id);
                    cb.maxHealth = o; cb.health = o;
                }
            }
        }
    }

    // ------------------------------------------------------------------ client: giao diện thẻ, ẩn chữ
    static BaseDialog dialog = null;
    static BaseDialog activeView = null;   // bảng "thẻ đang kích hoạt" mở từ bảng chọn thẻ
    static int[] offer = new int[3];
    static boolean rerolled = false, sent = false;
    static int offerRound = 0;
    static final IdentityHashMap<Label, Float> hiddenText = new IdentityHashMap<>();
    static boolean fontHidden = false; static float fsx, fsy;
    static float sweepT = 0;

    static boolean pending(NewModePvp.St st) {
        return st != null && round >= 1 && round <= maxRounds() && st.chRound < round && st.chPicks < maxPicks();
    }

    static void clientTick() {
        if (Vars.headless || Core.scene == null || !Vars.state.isGame()) return;
        boolean enabled = on();
        NewModePvp.St st = NewModePvp.localState();
        boolean need = enabled && pending(st);
        if (need && dialog == null) openOffer(round);
        else if (!need && dialog != null) closeOffer();
        if (dialog != null) {
            try { if (activeView != null && activeView.isShown()) activeView.toFront(); else dialog.toFront(); } catch (Throwable ignored) {}
        }
        applyTextHide(enabled && hideFlag && dialog == null);
    }

    static void closeOffer() {
        BaseDialog d = dialog;
        dialog = null;
        if (d != null) { try { d.hide(); } catch (Throwable ignored) {} }
    }

    static void pickOffer() {
        offer[0] = Mathf.random(0, CARDS - 1);
        do { offer[1] = Mathf.random(0, CARDS - 1); } while (offer[1] == offer[0]);
        do { offer[2] = Mathf.random(0, CARDS - 1); } while (offer[2] == offer[0] || offer[2] == offer[1]);
    }

    /** Ô cuộn kéo thả tự do cả hai chiều (dùng chung cho mọi bảng thẻ). */
    static ScrollPane freeScroll(Table content) {
        ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(true, true);
        return sp;
    }
    /** Kích thước tối đa của ô cuộn để không bao giờ tràn màn hình (reserve = phần dành cho tiêu đề, nút đóng...). */
    static float viewW() { return Math.max(300f, Core.scene.getWidth() - 40f); }
    static float viewH(float reserve) { return Math.max(150f, Core.scene.getHeight() - reserve); }

    static void openOffer(int r) {
        offerRound = r; rerolled = false; sent = false; pickOffer();
        final BaseDialog d = new BaseDialog(tr("HỖN LOẠN - Chọn 1 thẻ (vòng ", "CHAOS - Pick 1 card (round ") + r + "/" + maxRounds() + ")");
        dialog = d;
        d.hidden(() -> { if (dialog == d) { dialog = null; } });   // bị tắt nhầm -> clientTick mở lại vì vẫn chưa chọn
        buildOffer(d);
        d.show();
    }

    static void buildOffer(final BaseDialog d) {
        d.cont.clear();
        float sw = Core.scene.getWidth();
        float cw = Math.max(190f, Math.min(240f, (sw - 90f) / 3f));
        Table row = new Table();
        row.top();
        for (int i = 0; i < 3; i++) row.add(cardView(d, i, cw)).width(cw).pad(6f).top();
        ScrollPane sp = freeScroll(row);   // kéo thả tự do cả hai chiều
        d.cont.add(sp).grow().row();
        // Nút xem các thẻ đã kích hoạt trong trận (hiện dạng thẻ bài giống bảng chọn)
        d.cont.button(tr("Xem thẻ đang kích hoạt trong trận", "View active cards in the match"), Styles.flatBordert, ChaosMode::openActive).height(46f).growX().maxWidth(420f).pad(6f).row();
    }

    /** Bảng các thẻ đang có hiệu lực trong trận, dạng thẻ bài như lúc chọn thẻ (không có nút chọn / đổi bài). */
    static void openActive() {
        refresh();
        final BaseDialog d = new BaseDialog(tr("Thẻ đang kích hoạt", "Active cards"));
        activeView = d;
        d.hidden(() -> { if (activeView == d) activeView = null; });
        d.addCloseButton();
        float sw = Core.scene.getWidth();
        float cw = Math.max(190f, Math.min(240f, (sw - 90f) / 3f));
        int cols = Math.max(1, (int) ((sw - 60f) / (cw + 12f)));
        Table grid = new Table();
        grid.top();
        int shown = 0;
        for (int i = 0; i < CARDS; i++) {
            int n = cnt[i];
            if (n <= 0) continue;
            Table face = cardFace(i, cw, n);
            face.row();
            face.add("[lightgray]" + tr("Người trang bị: ", "Equipped by: ") + owners(i) + "[]").width(cw - 16f).left().wrap().pad(6f).row();
            grid.add(face).width(cw).pad(6f).top();
            if (++shown % cols == 0) grid.row();
        }
        if (shown == 0) grid.add("[lightgray]" + tr("Chưa có thẻ nào đang kích hoạt. Thẻ chỉ có hiệu lực khi người gắn thẻ đang chiêu mộ đúng pilot đó.", "No card is active yet. A card only works while its owner has the matching pilot recruited.") + "[]").width(Math.min(420f, sw - 60f)).wrap().pad(20f);
        ScrollPane sp = freeScroll(grid);
        // giới hạn kích thước theo màn hình: tràn thì cuộn chứ không còn đẩy ra ngoài màn hình
        d.cont.add(sp).maxHeight(viewH(170f)).maxWidth(viewW());
        d.show();
        try { d.toFront(); } catch (Throwable ignored) {}
    }

    /** Tên những người đang gắn thẻ c vào pilot đang chiêu mộ (gộp theo người, ghi ×n nếu gắn nhiều lần). */
    static String owners(int c) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, NewModePvp.St> en : NewModePvp.byId.entrySet()) {
            NewModePvp.St st = en.getValue();
            if (st == null) continue;
            int k = 0;
            for (int[] e : st.chEq) if (e[0] == st.pi && e[1] == c) k++;
            if (k == 0) continue;
            String nm;
            try { Player p = Groups.player.getByID(en.getKey()); nm = p != null ? p.name : "Bot"; } catch (Throwable ex) { nm = "?"; }
            if (sb.length() > 0) sb.append(", ");
            sb.append(nm).append(k > 1 ? " x" + k : "");
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** Thân thẻ bài (icon, tên, có lợi, bất lợi, trạng thái cộng dồn): dùng chung cho bảng chọn thẻ và bảng thẻ đang kích hoạt. */
    static Table cardFace(int id, float cw, int count) {
        final Card c = DECK[id];
        Table t = new Table();
        t.background(Styles.black8);
        t.image(Tex.whiteui, c.col).growX().height(6f).row();
        Table head = new Table();
        Item it = itemOf(c);
        if (it != null) head.add(new Image(it.uiIcon)).size(56f).pad(6f);
        Label title = head.add(hex(c.col) + tr(c.viName, c.enName)).growX().left().wrap().get();
        title.setAlignment(Align.left);
        t.add(head).growX().row();
        t.image(Tex.whiteui, new Color(c.col).a(0.5f)).growX().height(2f).pad(2f).row();
        Table body = new Table();
        body.margin(8f);
        body.add("[#4dff88]" + tr("CÓ LỢI", "BENEFIT") + "[]").left().row();
        body.add("[white]" + tr(c.viPro, c.enPro) + "[]").left().growX().wrap().padBottom(8f).row();
        body.add("[#ff4d4d]" + tr("BẤT LỢI", "DRAWBACK") + "[]").left().row();
        body.add("[white]" + tr(c.viCon, c.enCon) + "[]").left().growX().wrap().row();
        t.add(body).growX().minHeight(150f).top().row();
        if (count > 0) {
            String x = Strings.fixed(stackMul(count), 2);
            t.add("[#ffd34d]" + tr("Đang kích hoạt x" + count + " (lợi ích x" + x + ", chọn thêm sẽ cộng dồn giảm dần)", "Active x" + count + " (benefit x" + x + ", picking again stacks with diminishing returns)") + "[]")
                .width(cw - 16f).left().wrap().pad(4f).row();
        }
        return t;
    }

    static Table cardView(final BaseDialog d, final int slot, float cw) {
        final int id = offer[slot];
        Table t = cardFace(id, cw, stack(id));
        TextButton pick = t.button(tr("Chọn thẻ này", "Pick this card"), Styles.flatBordert, () -> {
            if (sent) return;
            sent = true;
            NewModePvp.send(REQ_PICK, offerRound + "|" + id);
        }).growX().height(44f).pad(6f).get();
        pick.update(() -> pick.setDisabled(sent));
        t.row();
        TextButton re = t.button(rerolled ? tr("Đã dùng đổi bài", "Reroll used") : tr("Đổi bài (1 lần)", "Reroll (once)"), Styles.flatBordert, () -> {
            if (rerolled || sent) return;
            int n;
            do { n = Mathf.random(0, CARDS - 1); } while (n == offer[0] || n == offer[1] || n == offer[2]);
            offer[slot] = n; rerolled = true;
            buildOffer(d);
        }).growX().height(38f).padLeft(6f).padRight(6f).padBottom(6f).get();
        re.setDisabled(rerolled);
        return t;
    }

    /** Một dòng thông tin thẻ (icon, tên, có lợi, bất lợi); onEquip != null thì thêm nút Trang bị. */
    static Table cardRow(int c, Runnable onEquip) {
        Card cd = DECK[c];
        Table row = new Table(Styles.black6);
        row.margin(8f);
        Item it = itemOf(cd);
        if (it != null) row.add(new Image(it.uiIcon)).size(40f).padRight(8f).top();
        Table col = new Table(); col.left();
        col.add(hex(cd.col) + name(c) + "[]").left().growX().wrap().width(230f).row();
        col.add("[#4dff88]+ " + tr(cd.viPro, cd.enPro) + "[]").left().growX().wrap().width(230f).row();
        col.add("[#ff4d4d]- " + tr(cd.viCon, cd.enCon) + "[]").left().growX().wrap().width(230f).row();
        row.add(col).growX().left();
        if (onEquip != null) row.button(tr("Trang bị", "Equip"), Styles.flatBordert, onEquip).size(96f, 44f).padLeft(8f);
        return row;
    }

    /** Xem tất cả thẻ của chế độ Hỗn loạn (dùng được ngoài trận). */
    static void openAll() {
        BaseDialog d = new BaseDialog(tr("Tất cả thẻ Hỗn loạn", "All Chaos cards"));
        d.addCloseButton();
        Table body = new Table();
        body.top().margin(8f);
        body.add("[lightgray]" + tr("Mỗi " + roundMinutes() + " phút chọn 1 trong 3 thẻ (tối đa " + maxRounds() + " lần, mỗi người tối đa " + maxPicks() + " thẻ). Chọn lại thẻ đã có sẽ cộng dồn (giảm dần). Thẻ áp dụng cho cả hai phe khi pilot được gắn thẻ đang được chiêu mộ.",
            "Every " + roundMinutes() + " minutes pick 1 of 3 cards (max " + maxRounds() + " times, up to " + maxPicks() + " cards per player). Picking a card again stacks it (diminishing returns). Cards affect both teams while the pilot carrying them is recruited.") + "[]").left().growX().wrap().width(340f).padBottom(8f).row();
        for (int i = 0; i < CARDS; i++) body.add(cardRow(i, null)).growX().pad(4f).row();
        ScrollPane sp = freeScroll(body);
        d.cont.add(sp).maxHeight(viewH(170f)).maxWidth(viewW());
        d.show();
    }

    // ------------------------------------------------------------------ trang bị thẻ cho pilot
    static String equippedText(NewModePvp.St st, int pi) {
        if (st == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int[] e : st.chEq) if (e[0] == pi) { if (sb.length() > 0) sb.append(", "); sb.append(hex(DECK[e[1]].col)).append(name(e[1])).append("[]"); }
        return sb.length() == 0 ? "" : tr("Thẻ: ", "Cards: ") + sb;
    }
    static String activeText() {
        refresh();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CARDS; i++) if (act[i]) { if (sb.length() > 0) sb.append(", "); sb.append(name(i)); if (cnt[i] > 1) sb.append(" x").append(cnt[i]); }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    static void openEquip(final int pi, final String pilotName) {
        final NewModePvp.St st = NewModePvp.localState();
        if (st == null) return;
        final BaseDialog d = new BaseDialog(tr("Trang bị thẻ cho ", "Equip cards on ") + pilotName);
        d.addCloseButton();
        Table body = new Table();
        body.top().margin(8f);
        body.add("[lightgray]" + tr("Thẻ đã gắn vào pilot sẽ không thể tháo hay chuyển sang pilot khác. Thẻ chỉ có hiệu lực khi bạn đang chiêu mộ pilot này.",
            "A card equipped on a pilot cannot be removed or moved to another pilot. It only works while you have this pilot recruited.") + "[]").left().growX().wrap().width(340f).padBottom(8f).row();
        boolean anyEq = false;
        for (int[] e : st.chEq) {
            if (e[0] != pi) continue;
            if (!anyEq) { anyEq = true; body.add("[accent]" + tr("Thẻ đã trang bị cho pilot này", "Cards equipped on this pilot") + "[]").left().padBottom(4f).row(); }
            body.add(cardRow(e[1], null)).growX().pad(4f).row();
        }
        if (anyEq) body.add("[accent]" + tr("Thẻ trong tay", "Cards in hand") + "[]").left().padTop(8f).padBottom(4f).row();
        if (st.chHand.isEmpty()) body.add("[lightgray]" + tr("Bạn chưa có thẻ nào để trang bị.", "You have no cards to equip.") + "[]").left().row();
        for (final int c : new ArrayList<>(st.chHand)) {
            body.add(cardRow(c, () -> { NewModePvp.send(REQ_EQUIP, pi + "|" + c); d.hide(); })).growX().pad(4f).row();
        }
        ScrollPane sp = freeScroll(body);
        d.cont.add(sp).maxHeight(viewH(230f)).maxWidth(viewW()).row();
        // nút xem các thẻ đang kích hoạt trong trận (giống ở bảng chọn thẻ)
        d.cont.button(tr("Xem thẻ đang kích hoạt trong trận", "View active cards in the match"), Styles.flatBordert, ChaosMode::openActive).height(46f).growX().maxWidth(420f).pad(6f).row();
        d.show();
    }

    // ------------------------------------------------------------------ ẩn chữ (thẻ Khiên hỗn loạn)
    static void applyTextHide(boolean hide) {
        if (hide) {
            try {
                if (!fontHidden) { fontHidden = true; fsx = Fonts.outline.getData().scaleX; fsy = Fonts.outline.getData().scaleY; }
                Fonts.outline.getData().setScale(0.0001f, 0.0001f);   // chữ vẽ trực tiếp trong thế giới
            } catch (Throwable ignored) {}
            sweepT -= Time.delta;
            if (sweepT <= 0f) { sweepT = 4f; sweep(Core.scene.root); }
        } else if (fontHidden || !hiddenText.isEmpty()) {
            try { if (fontHidden) Fonts.outline.getData().setScale(fsx, fsy); } catch (Throwable ignored) {}
            fontHidden = false;
            for (Map.Entry<Label, Float> e : hiddenText.entrySet()) { try { e.getKey().color.a = e.getValue(); } catch (Throwable ignored) {} }
            hiddenText.clear();
        }
    }
    static void sweep(Element e) {
        if (e instanceof Label) {
            Label l = (Label) e;
            if (!hiddenText.containsKey(l)) hiddenText.put(l, l.color.a);
            l.color.a = 0f;
        }
        if (e instanceof Group) {
            Seq<Element> ch = ((Group) e).getChildren();
            for (int i = 0; i < ch.size; i++) sweep(ch.get(i));
        }
    }

    public static void init() {
        arc.Events.on(UnitSpawnEvent.class, e -> { try { onWaveSpawn(e.unit); } catch (Throwable err) { NewModePvp.logOnce("chaos-vanish", "chaos vanish failed: " + err, false); } });
        arc.Events.run(Trigger.update, () -> { try { serverTick(); } catch (Throwable err) { NewModePvp.logOnce("chaos-server", "chaos tick failed: " + err, false); } });
        arc.Events.run(Trigger.update, () -> { try { clientTick(); } catch (Throwable err) { NewModePvp.logOnce("chaos-client", "chaos ui failed: " + err, false); } });
    }
}
