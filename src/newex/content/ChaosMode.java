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
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
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
 * - Mỗi 10 phút trong trận (tối đa 3 lần) hiện giao diện thẻ bài ở layer cao nhất, buộc phải chọn 1 trong 3 thẻ mới tắt được.
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
    static final int ROUND_TICKS = 10 * 3600, MAX_ROUNDS = 3, MAX_PICKS = 3;
    static final int HIDE_CYCLE = 5 * 3600, HIDE_TICKS = 3600, MIN_TICKS = 3600;

    // chỉ số thẻ (xếp theo thứ tự người dùng mô tả, bỏ số 6 vì đó là luật chung)
    static final int C_STAT10 = 0, C_LV50 = 1, C_CORE1 = 2, C_BDMG = 3, C_HP250 = 4, C_NOCD = 5, C_LIVES = 6, C_LVMIN = 7, C_SHIELD = 8, CARDS = 9;

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
            "Cứ 5 phút, toàn bộ chữ trong trận bị ẩn 1 phút.", "Every 5 minutes all text in the match is hidden for 1 minute.")
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
    static float actAt = -1000; static int actVer = -1;
    static boolean grant50 = false;
    static float minT = 0, shieldT = 0;
    static final HashMap<Integer, Integer> lives = new HashMap<>();
    static final HashMap<Integer, Float> coreOrig = new HashMap<>();
    static final java.util.HashSet<Integer> touchedBuilds = new java.util.HashSet<>();

    static void reset() {
        round = 0; hideFlag = false; grant50 = false; minT = 0; shieldT = 0;
        lives.clear(); coreOrig.clear(); touchedBuilds.clear(); act = new boolean[CARDS]; actAt = -1000; actVer = -1;
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
        try { Vars.state.rules.tags.put(TAG, Core.settings.getBool(KEY, false) ? "1" : "0"); } catch (Throwable ignored) {}
    }

    static void refresh() {
        if (Time.time - actAt < 20f && actVer == NewModePvp.stVer) return;
        actAt = Time.time; actVer = NewModePvp.stVer;
        boolean[] n = new boolean[CARDS];
        if (on()) {
            for (NewModePvp.St st : NewModePvp.byId.values()) {
                if (st == null) continue;
                for (int[] e : st.chEq) if (e[0] == st.pi && e[1] >= 0 && e[1] < CARDS) n[e[1]] = true;
            }
        }
        act = n;
    }
    static boolean has(int card) { if (!on()) return false; refresh(); return act[card]; }

    // ------------------------------------------------------------------ móc vào NewModePvp
    static boolean unlimited() { return on(); }
    static int statBonus() { return has(C_STAT10) ? 10 : 0; }
    static boolean noArmor() { return has(C_STAT10); }
    static float expMul() { return (has(C_LV50) || has(C_LVMIN)) ? 0.1f : 1f; }
    static float hpMul() { return has(C_HP250) ? 3.5f : 1f; }
    static int cd(int base) { return has(C_NOCD) ? Math.min(base, 60) : base; }
    static boolean slowTurrets() { return has(C_NOCD); }
    /** Chu kỳ làm chậm pháo -80%: chỉ cho chạy 1 trên 5 khung hình. */
    static boolean skipFrame() { return has(C_NOCD) && ((int) Time.time) % 5 != 0; }
    static boolean noMount() { return has(C_BDMG); }
    static boolean noBuild() { return has(C_LIVES); }
    static float barrackMul() { return has(C_CORE1) ? 6f : 1f; }
    static float builtDmgMul() { return has(C_BDMG) ? 6f : 1f; }
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
        if (b == 0 || base == null) return base;
        int[] out = (st.chs != null && st.chs.length == base.length) ? st.chs : (st.chs = new int[base.length]);
        for (int i = 0; i < base.length; i++) out[i] = base[i] + b;
        return out;
    }

    /** Lõi sắp chết: nếu có thẻ "Năm mạng" thì trừ 1 mạng và hồi sinh đầy máu. true = đã chặn cái chết. */
    static boolean coreExtraLife(CoreBlock.CoreBuild cb) {
        if (!has(C_LIVES) || cb == null) return false;
        Integer left = lives.get(cb.id);
        int l = left == null ? 5 : left;
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
            if (r != round || r < 1 || r > MAX_ROUNDS || st.chRound >= r || st.chPicks >= MAX_PICKS) return;
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
        int due = Math.min(MAX_ROUNDS, (int) (NewModePvp.matchT / ROUND_TICKS));
        if (due > round) {
            round = due;
            NewModePvp.toastAll(tr("[accent]HỖN LOẠN! Chọn 1 thẻ bài (vòng " + round + "/" + MAX_ROUNDS + ")", "[accent]CHAOS! Pick a card (round " + round + "/" + MAX_ROUNDS + ")"), 5f);
            botsPick();
            NewModePvp.changed();
        }
        boolean nh = has(C_SHIELD) && NewModePvp.matchT >= HIDE_CYCLE && (NewModePvp.matchT % HIDE_CYCLE) < HIDE_TICKS;
        if (nh != hideFlag) { hideFlag = nh; NewModePvp.changed(); }

        // +50 cấp một lần
        if (has(C_LV50) && !grant50) {
            grant50 = true;
            for (NewModePvp.St st : NewModePvp.byId.values()) if (st != null) { st.lv += 50; st.pts += 50; }
            NewModePvp.toastAll(tr("[accent]Thẻ Bước nhảy cấp độ: mọi người chơi +50 cấp!", "[accent]Level leap card: every player +50 levels!"), 5f);
            NewModePvp.changed();
        }
        // +1 cấp mỗi phút
        if (has(C_LVMIN)) {
            minT += dt;
            if (minT >= MIN_TICKS) {
                minT -= MIN_TICKS;
                for (NewModePvp.St st : NewModePvp.byId.values()) if (st != null) { st.lv++; st.pts++; }
                NewModePvp.changed();
            }
        } else minT = 0;
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
    }

    static void botsPick() {
        for (int i = 0; i < NewModePvp.bots.size(); i++) {
            try {
                Player bp = NewModePvp.bots.get(i).botPl;
                NewModePvp.St st = bp == null ? null : NewModePvp.byId.get(bp.id);
                if (st == null || st.chRound >= round || st.chPicks >= MAX_PICKS) continue;
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
    static void shield(Unit u) { u.shield = Math.max(u.shield, u.maxHealth * 0.9f + u.armor * 0.1f); }

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
        boolean frame = ((int) Time.time) % 5 == 0;
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
    static int[] offer = new int[3];
    static boolean rerolled = false, sent = false;
    static int offerRound = 0;
    static final IdentityHashMap<Label, Float> hiddenText = new IdentityHashMap<>();
    static boolean fontHidden = false; static float fsx, fsy;
    static float sweepT = 0;

    static boolean pending(NewModePvp.St st) {
        return st != null && round >= 1 && round <= MAX_ROUNDS && st.chRound < round && st.chPicks < MAX_PICKS;
    }

    static void clientTick() {
        if (Vars.headless || Core.scene == null || !Vars.state.isGame()) return;
        boolean enabled = on();
        NewModePvp.St st = NewModePvp.localState();
        boolean need = enabled && pending(st);
        if (need && dialog == null) openOffer(round);
        else if (!need && dialog != null) closeOffer();
        if (dialog != null) { try { dialog.toFront(); } catch (Throwable ignored) {} }
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

    static void openOffer(int r) {
        offerRound = r; rerolled = false; sent = false; pickOffer();
        final BaseDialog d = new BaseDialog(tr("HỖN LOẠN - Chọn 1 thẻ (vòng ", "CHAOS - Pick 1 card (round ") + r + "/" + MAX_ROUNDS + ")");
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
        ScrollPane sp = new ScrollPane(row, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);   // kéo thả tự do cả hai chiều
        sp.setFlickScroll(true);
        sp.setOverscroll(true, true);
        d.cont.add(sp).grow();
    }

    static Table cardView(final BaseDialog d, final int slot, float cw) {
        final int id = offer[slot];
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
        body.add("[lightgray]" + tr("Mỗi 10 phút chọn 1 trong 3 thẻ (tối đa 3 lần). Thẻ áp dụng cho cả hai phe khi pilot được gắn thẻ đang được chiêu mộ.",
            "Every 10 minutes pick 1 of 3 cards (max 3 times). Cards affect both teams while the pilot carrying them is recruited.") + "[]").left().growX().wrap().width(340f).padBottom(8f).row();
        for (int i = 0; i < CARDS; i++) body.add(cardRow(i, null)).growX().pad(4f).row();
        ScrollPane sp = new ScrollPane(body, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);
        sp.setFlickScroll(true);
        d.cont.add(sp).grow().maxWidth(Math.max(320f, Core.scene.getWidth() - 40f));
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
        for (int i = 0; i < CARDS; i++) if (act[i]) { if (sb.length() > 0) sb.append(", "); sb.append(name(i)); }
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
        ScrollPane sp = new ScrollPane(body, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);
        sp.setFlickScroll(true);
        d.cont.add(sp).grow().maxWidth(Math.max(320f, Core.scene.getWidth() - 40f));
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
        arc.Events.run(Trigger.update, () -> { try { serverTick(); } catch (Throwable err) { NewModePvp.logOnce("chaos-server", "chaos tick failed: " + err, false); } });
        arc.Events.run(Trigger.update, () -> { try { clientTick(); } catch (Throwable err) { NewModePvp.logOnce("chaos-client", "chaos ui failed: " + err, false); } });
    }
}
