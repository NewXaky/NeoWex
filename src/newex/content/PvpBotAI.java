package newex.content;

import arc.Events;
import arc.files.Fi;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Hệ thống A.I độc lập cho NewModePvp:
 * - Di chuyển né tường bằng pathQuery + movePref.
 * - Học theo lối chơi của player: pháo trang bị, nâng nhà máy lính, chọn unit, chọn pilot và cách biến về (recall).
 * - Khi nhàn rỗi sẽ đứng yên hoặc lang thang tự nhiên quanh lãnh thổ.
 * - Lưu ký ức lâu dài trên thiết bị (Vars.dataDirectory/newex-ai-memory.json).
 * - 8 nhân vật với 8 tính cách & bộ thoại riêng tự nhiên, song ngữ Việt/Anh.
 * - Đề nghị kèo 1v2 khi thua thiệt, hỗ trợ cầm lái khi player AFK > 1 phút, tự trò chuyện trong Bot vs Bot.
 */
public final class PvpBotAI {
    private PvpBotAI() {}

    static final int BOT_ID = -4242;
    static final int BOT_MAX = 8;
    static final float LEASH_R = 3200f;
    static final float HOME_R = 115f;
    static final float AGGRO_R = 960f;
    static final float COMBAT_R = 660f;
    static final float RETREAT_HP = 0.34f;
    static final float HEALED_HP = 0.70f;
    static final float AFK_LIMIT_TICKS = 60f * 60f;
    static final String MEM_FILE = "newex-ai-memory.json";

    // 8 nhân vật với tính cách riêng cố định theo slot:
    // 0 Kira (nhây/hài), 1 Vex (máu chiến), 2 Nova (chiến thuật), 3 Sora (chill/thân thiện),
    // 4 Aegis (thủ chắc/điềm đạm), 5 Lumi (tăng động/hay tám), 6 Zed (lạnh lùng/ngắn gọn), 7 Riven (fair-play/nhiệt huyết)
    static final String[] NAMES = {"Kira", "Vex", "Nova", "Sora", "Aegis", "Lumi", "Zed", "Riven"};
    static final String[] COLORS = {"#6ee7ff", "#ff8a5b", "#c084fc", "#5ce67e", "#60a5fa", "#facc15", "#cbd5e1", "#f472b6"};

    enum Mode { IDLE, FARM, DEFEND, HUNT, SIEGE, RETREAT, SHOP }

    /** Ký ức lâu dài lưu trực tiếp trên thiết bị. */
    static final class Memory {
        int matches = 0, playerKills = 0, aiKills = 0, accepted1v2 = 0, afkHelped = 0;
        int learnedUnit = 0, learnedPilot = 0, learnedBarrack = 0;
        String lastPlayer = "", favStyle = "balanced", learnedTurret = "";
    }

    /** Theo dõi trạng thái AFK của player thật. */
    static final class AfkTrack {
        float lastX, lastY, lastRot, idleT;
        boolean assisting, init;
        float strafeDir = 1f, strafeT = 80f, gearT = 180f;
        float pathT, pathGx, pathGy, pathDx = Float.NaN, pathDy;
        boolean pathOk = true, pathWait = false;
    }

    /** Người chơi giả đại diện cho A.I. */
    public static final class BotPlayer extends Player {
        Team _team;
        Unit _unit;
        @Override public Team team() { return _team; }
        @Override public void team(Team t) { _team = t; }
        @Override public Unit unit() { return _unit; }
        @Override public void unit(Unit x) {
            _unit = x;
            if (x != null) {
                try { x.controller(new NewModePvp.NoAI()); } catch (Throwable ignored) {}
            }
        }
        @Override public String uuid() { return "ai:" + id; }
    }

    /** Ngữ cảnh riêng cho từng A.I. */
    public static final class Agent {
        public Player botPl;
        public String shortName, colHex;
        public int slotIdx, persona;
        public Mode mode = Mode.IDLE;
        public float deadT = 40f, thinkT = 0f, shopT = 140f, buildT = 360f, incomeT = 0f, learnT = 180f;
        public float hitT = 0f, strafeDir = 1f, strafeFlipT = 90f, recallCd = 0f;
        public float ambientChatT = 4200f, coreAlertCd = 0f, lastCoreHp = -1f, greetT = 160f;
        // Né tường kiểu cũ (pathQuery)
        public float pathT = 0f, pathGx = 0f, pathGy = 0f, pathDx = Float.NaN, pathDy = 0f;
        public boolean pathOk = true, pathWait = false;
        // Nhàn rỗi: đứng yên hoặc lang thang
        public boolean idleStand = true;
        public float idleT = 180f, wanderX = Float.NaN, wanderY = Float.NaN;
        // Học theo player
        public int learnUnit = 0, learnPilot = 0, learnBarrack = 0;
        public final ArrayList<String> learnTurrets = new ArrayList<>();
        public boolean swapPending = false;
        // Trạng thái hội thoại & chiến đấu
        public boolean greeted = false, aggressiveOrder = false, defensiveOrder = false;
        public int lastAnnouncedLv = 1, deathsThisMatch = 0, killsThisMatch = 0;
        public Unit target;
        public Entityc hitBy;
        public Integer killerId;
    }

    static final class QueuedChat {
        Agent sender;
        String customPrefix, text;
        float delay;
        QueuedChat(Agent s, String pfx, String t, float d) { sender = s; customPrefix = pfx; text = t; delay = d; }
    }

    public static final ArrayList<Agent> agents = new ArrayList<>();
    static final ArrayList<QueuedChat> chatQueue = new ArrayList<>();
    static final HashMap<Integer, AfkTrack> afkMap = new HashMap<>();
    static final Memory mem = new Memory();
    static boolean memLoaded = false, chatHooked = false, matchRecorded = false;
    static boolean extra1v2 = false, asked1v2 = false, pending1v2 = false;
    static float offer1v2T = 0f, check1v2T = 900f, bvbBanterT = 1800f, globalChatCd = 0f;
    static Agent offer1v2Agent = null;
    static final Vec2 tmpGoal = new Vec2();

    // ------------------------------------------------------------------ Bộ nhớ lâu dài

    static Fi memFile() {
        try { return Vars.dataDirectory != null ? Vars.dataDirectory.child(MEM_FILE) : null; }
        catch (Throwable ignored) { return null; }
    }

    static void loadMemory() {
        if (memLoaded) return;
        memLoaded = true;
        try {
            Fi f = memFile();
            if (f == null || !f.exists()) return;
            JsonValue j = new JsonReader().parse(f.readString("UTF-8"));
            if (j == null) return;
            mem.matches = j.getInt("matches", 0);
            mem.playerKills = j.getInt("playerKills", 0);
            mem.aiKills = j.getInt("aiKills", 0);
            mem.accepted1v2 = j.getInt("accepted1v2", 0);
            mem.afkHelped = j.getInt("afkHelped", 0);
            mem.learnedUnit = j.getInt("learnedUnit", 0);
            mem.learnedPilot = j.getInt("learnedPilot", 0);
            mem.learnedBarrack = j.getInt("learnedBarrack", 0);
            mem.lastPlayer = j.getString("lastPlayer", "");
            mem.favStyle = j.getString("favStyle", "balanced");
            mem.learnedTurret = j.getString("learnedTurret", "");
        } catch (Throwable err) {
            Log.err("[PvpBotAI] loadMemory failed", err);
        }
    }

    static void saveMemory() {
        try {
            Fi f = memFile();
            if (f == null) return;
            String lp = esc(mem.lastPlayer), fs = esc(mem.favStyle), lt = esc(mem.learnedTurret);
            String json = "{\"matches\":" + mem.matches
                + ",\"playerKills\":" + mem.playerKills
                + ",\"aiKills\":" + mem.aiKills
                + ",\"accepted1v2\":" + mem.accepted1v2
                + ",\"afkHelped\":" + mem.afkHelped
                + ",\"learnedUnit\":" + mem.learnedUnit
                + ",\"learnedPilot\":" + mem.learnedPilot
                + ",\"learnedBarrack\":" + mem.learnedBarrack
                + ",\"lastPlayer\":\"" + lp + "\""
                + ",\"favStyle\":\"" + fs + "\""
                + ",\"learnedTurret\":\"" + lt + "\"}";
            f.writeString(json, false, "UTF-8");
        } catch (Throwable err) {
            Log.err("[PvpBotAI] saveMemory failed", err);
        }
    }

    static String esc(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ------------------------------------------------------------------ Khởi tạo & Tra cứu

    public static void init() {
        loadMemory();
        if (chatHooked) return;
        chatHooked = true;
        Events.on(EventType.PlayerChatEvent.class, e -> {
            if (Vars.net.client() || e == null || e.player == null || e.message == null) return;
            if (!NewModePvp.isActive()) return;
            if (isBotPl(e.player)) return;
            markPlayerActive(e.player);
            if (!agents.isEmpty()) onPlayerChat(e.player, e.message);
        });
    }

    public static void clear() {
        agents.clear();
        chatQueue.clear();
        afkMap.clear();
        matchRecorded = false;
        extra1v2 = false;
        asked1v2 = false;
        pending1v2 = false;
        offer1v2T = 0f;
        offer1v2Agent = null;
        check1v2T = 900f;
        bvbBanterT = 1500f;
        globalChatCd = 0f;
    }

    public static boolean isBotId(int id) { return id <= BOT_ID && id > BOT_ID - 64; }

    public static Agent botOf(int id) {
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).botPl.id == id) return agents.get(i);
        }
        return null;
    }

    public static Agent botUnitOf(Unit u) {
        if (u == null) return null;
        for (int i = 0; i < agents.size(); i++) {
            Player bp = agents.get(i).botPl;
            if (bp instanceof BotPlayer && ((BotPlayer) bp)._unit == u) return agents.get(i);
        }
        return null;
    }

    public static boolean isBotPl(Player p) { return p != null && isBotId(p.id) && botOf(p.id) != null; }

    public static String displayName(int pid) {
        Agent a = botOf(pid);
        if (a != null) return "[" + a.colHex + "]" + a.shortName + "[]";
        return "[cyan]AI-" + (BOT_ID - pid + 1) + "[]";
    }

    static String tr(String vi, String en) { return NewModePvp.tr(vi, en); }
    static String cleanName(Player p) { return p != null && p.name != null ? p.name.replaceAll("\\[[^\\]]*\\]", "").trim() : "bạn"; }

    // ------------------------------------------------------------------ Tạo / Xóa / Thay thế A.I

    static Player makeBotPl(Team team, int id, String shortName, String colHex) {
        BotPlayer o = new BotPlayer();
        o.id = id;
        o.name = "[" + colHex + "]" + shortName + "[]";
        o.admin = false;
        o.shooting = false;
        o.deathTimer = 0;
        o._team = team;
        o._unit = null;
        return o;
    }

    static Team pickBotTeam() {
        Seq<Team> tms = NewModePvp.pvpTeams();
        HashMap<Integer, Integer> cnt = new HashMap<>();
        for (int i = 0; i < tms.size; i++) cnt.put(tms.get(i).id, 0);
        for (Player p : Groups.player) {
            if (p.team() != null && cnt.containsKey(p.team().id)) cnt.put(p.team().id, cnt.get(p.team().id) + 1);
        }
        for (int i = 0; i < agents.size(); i++) {
            Team t = agents.get(i).botPl.team();
            if (t != null && cnt.containsKey(t.id)) cnt.put(t.id, cnt.get(t.id) + 1);
        }
        Team best = null;
        int bestC = Integer.MAX_VALUE;
        for (int i = 0; i < tms.size; i++) {
            Team t = tms.get(i);
            if (t.cores().size == 0) continue;
            int c = cnt.getOrDefault(t.id, 0);
            if (c < bestC) { bestC = c; best = t; }
        }
        return best;
    }

    static Agent createAgent(Team team) {
        int k = 0;
        while (botOf(BOT_ID - k) != null) k++;
        int id = BOT_ID - k;
        int persona = k % NAMES.length;
        String sName = NAMES[persona] + (k >= NAMES.length ? (k / NAMES.length + 1) : "");
        String cHex = COLORS[persona];
        Agent a = new Agent();
        a.slotIdx = k;
        a.persona = persona;
        a.shortName = sName;
        a.colHex = cHex;
        a.botPl = makeBotPl(team, id, sName, cHex);
        a.greetT = 130f + k * 60f;
        a.ambientChatT = 5200f + Mathf.random(3600f);
        // Nạp sẵn thói quen đã học từ ký ức
        a.learnUnit = mem.learnedUnit;
        a.learnPilot = mem.learnedPilot;
        a.learnBarrack = mem.learnedBarrack;
        if (mem.learnedTurret != null && !mem.learnedTurret.isEmpty()) a.learnTurrets.add(mem.learnedTurret);
        agents.add(a);

        NewModePvp.St st = NewModePvp.bind(a.botPl);
        if (st.pi == 0) {
            if (mem.learnedPilot > 0 && NewModePvp.validPilot(mem.learnedPilot) && (mem.learnedPilot != NewModePvp.PILOT_EMPIX || st.lv >= NewModePvp.EMPIX_MIN_LEVEL)) {
                st.pi = mem.learnedPilot;
            } else {
                int[] pilots = {NewModePvp.PILOT_XIKI, NewModePvp.PILOT_XAKY, NewModePvp.PILOT_HIST, NewModePvp.PILOT_VANHAU, NewModePvp.PILOT_XAKYNEO};
                st.pi = pilots[persona % pilots.length];
            }
            if (st.pi == NewModePvp.PILOT_XAKYNEO) NewModePvp.xakyNeoStart.put(a.botPl.id, Time.time);
        }
        a.lastAnnouncedLv = st.lv;
        NewModePvp.changed();
        if (agents.size() == 1 && !NewModePvp.bvbOn()) {
            NewModePvp.toastAll("[#" + team.color + "]■[] [" + cHex + "]" + sName + "[] " + tr("đã vào trận cùng bạn!", "joined your match!"), 4f);
        }
        return a;
    }

    public static void removeAgent(boolean wipe, Agent a) {
        if (a == null) return;
        agents.remove(a);
        try {
            Unit u = a.botPl.unit();
            if (u != null && u.isValid()) {
                try { Call.unitDespawn(u); } catch (Throwable err) { u.remove(); }
            }
        } catch (Throwable ignored) {}
        NewModePvp.byId.remove(a.botPl.id);
        NewModePvp.prevUnit.remove(a.botPl.id);
        NewModePvp.xakyNeoStart.remove(a.botPl.id);
        NewModePvp.vanhauField.remove(a.botPl.id);
        if (wipe) NewModePvp.store.remove(a.botPl.uuid());
        NewModePvp.changed();
    }

    public static void replaceBot(Player pl) {
        if (agents.isEmpty()) return;
        Agent a = agents.get(0);
        Team tm = a.botPl.team();
        boolean inherit = pl != null && pl.team() == tm;
        if (inherit) {
            NewModePvp.St bst = NewModePvp.byId.get(a.botPl.id);
            if (bst != null) {
                NewModePvp.store.put(pl.uuid(), bst);
                NewModePvp.byId.put(pl.id, bst);
            }
        }
        removeAgent(!inherit, a);
    }

    static ArrayList<Team> desiredTeams() {
        ArrayList<Team> out = new ArrayList<>();
        Seq<Team> tms = NewModePvp.pvpTeams();
        if (tms.size == 0) return out;
        if (NewModePvp.bvbOn()) {
            for (int i = 0; i < tms.size && out.size() < BOT_MAX; i++) {
                if (tms.get(i).cores().size > 0) out.add(tms.get(i));
            }
            return out;
        }
        int humanCount = 0;
        for (Player p : Groups.player) {
            if (p != null && NewModePvp.isPvpTeam(p.team())) humanCount++;
        }
        if (NewModePvp.pvbOn()) {
            Team bt = !agents.isEmpty() ? agents.get(0).botPl.team() : pickBotTeam();
            if (bt != null && bt.cores().size > 0) {
                int want = Math.max(1, Math.min(BOT_MAX, humanCount + (extra1v2 ? 1 : 0)));
                for (int i = 0; i < want; i++) out.add(bt);
            }
            return out;
        }
        if (humanCount == 1) {
            Team bt = (!agents.isEmpty() && agents.get(0).botPl.team().cores().size > 0)
                ? agents.get(0).botPl.team() : pickBotTeam();
            if (bt != null && bt.cores().size > 0) {
                out.add(bt);
                if (extra1v2) out.add(bt);
            }
        }
        return out;
    }

    public static void manage() {
        if (Vars.net.client() || !NewModePvp.isFull() || Vars.state.gameOver) return;
        loadMemory();
        ArrayList<Team> want = desiredTeams();
        HashMap<Integer, Integer> need = new HashMap<>();
        for (int i = 0; i < want.size(); i++) {
            int tid = want.get(i).id;
            need.put(tid, need.getOrDefault(tid, 0) + 1);
        }
        for (int i = agents.size() - 1; i >= 0; i--) {
            Agent a = agents.get(i);
            Team t = a.botPl.team();
            Integer c = t != null ? need.get(t.id) : null;
            if (c != null && c > 0 && t.cores().size > 0) need.put(t.id, c - 1);
            else removeAgent(true, a);
        }
        for (int i = 0; i < want.size(); i++) {
            Team t = want.get(i);
            int c = need.getOrDefault(t.id, 0);
            if (c > 0 && agents.size() < BOT_MAX) {
                need.put(t.id, c - 1);
                createAgent(t);
            }
        }
        if (!matchRecorded && !agents.isEmpty() && !NewModePvp.bvbOn()) {
            Player human = firstHumanPlayer();
            if (human != null) {
                matchRecorded = true;
                mem.matches++;
                mem.lastPlayer = cleanName(human);
                saveMemory();
            }
        }
    }

    static Player firstHumanPlayer() {
        for (Player p : Groups.player) {
            if (p != null && !isBotPl(p) && NewModePvp.isPvpTeam(p.team())) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ Vòng lặp chính

    public static void tick() {
        if (Vars.net.client() || !NewModePvp.isFull() || Vars.state.gameOver || Vars.state.isPaused()) return;
        tickChatQueue();
        tickAfkCopilot();
        tick1v2OfferCheck();
        if (NewModePvp.bvbOn() && agents.size() >= 2) tickBvbBanter();
        for (int i = 0; i < agents.size(); i++) {
            try { tickAgent(agents.get(i)); } catch (Throwable err) { Log.err("[PvpBotAI] tick error", err); }
        }
    }

    // ------------------------------------------------------------------ Di chuyển né tường (giữ nguyên cơ chế pathQuery cũ)

    static final class PathRes { boolean unreachable, move; float dx, dy; }
    static java.lang.reflect.Method pathM = null;
    static boolean pathMTried = false;

    static PathRes pathQuery(Unit u, Vec2 goal) {
        try {
            if (!pathMTried) {
                pathMTried = true;
                for (java.lang.reflect.Method m : Vars.controlPath.getClass().getMethods()) {
                    if (m.getName().equals("getPathPosition") && m.getParameterCount() == 3) { pathM = m; break; }
                }
            }
            if (pathM == null) return null;
            Object r = pathM.invoke(Vars.controlPath, u, goal, goal);
            if (r == null) return null;
            PathRes p = new PathRes();
            p.unreachable = NewModePvp.fldB(r, "unreachable", false);
            p.move = NewModePvp.fldB(r, "move", false);
            Object d = NewModePvp.fld(r, "dest", null);
            if (d instanceof Vec2) { p.dx = ((Vec2) d).x; p.dy = ((Vec2) d).y; }
            return p;
        } catch (Throwable ignored) { return null; }
    }

    static boolean goPath(Agent a, Unit u, float tx, float ty, float stopDist) {
        Vec2 v = Tmp.v1;
        float dist = Mathf.dst(u.x, u.y, tx, ty);
        if (dist <= stopDist) { v.setZero(); u.movePref(v); return true; }
        a.pathT -= Time.delta;
        boolean needQuery = a.pathT <= 0f || Float.isNaN(a.pathDx) || Math.abs(tx - a.pathGx) > 24f || Math.abs(ty - a.pathGy) > 24f;
        if (needQuery) {
            a.pathT = 6f; a.pathGx = tx; a.pathGy = ty; a.pathOk = true; a.pathWait = false; a.pathDx = tx; a.pathDy = ty;
            tmpGoal.set(tx, ty);
            PathRes r = pathQuery(u, tmpGoal);
            if (r != null) {
                if (r.unreachable) a.pathOk = false;
                else if (r.move) { a.pathDx = r.dx; a.pathDy = r.dy; }
                else a.pathWait = true;
            }
        }
        if (a.pathWait) { v.setZero(); u.movePref(v); return true; }
        v.set(a.pathDx - u.x, a.pathDy - u.y);
        if (v.len() > 0.1f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
        if (!u.isShooting() && v.len2() > 0.01f) u.lookAt(v.angle());
        return a.pathOk;
    }

    static boolean goPathAfk(AfkTrack t, Unit u, float tx, float ty, float stopDist) {
        Vec2 v = Tmp.v1;
        float dist = Mathf.dst(u.x, u.y, tx, ty);
        if (dist <= stopDist) { v.setZero(); u.movePref(v); return true; }
        t.pathT -= Time.delta;
        boolean needQuery = t.pathT <= 0f || Float.isNaN(t.pathDx) || Math.abs(tx - t.pathGx) > 24f || Math.abs(ty - t.pathGy) > 24f;
        if (needQuery) {
            t.pathT = 6f; t.pathGx = tx; t.pathGy = ty; t.pathOk = true; t.pathWait = false; t.pathDx = tx; t.pathDy = ty;
            tmpGoal.set(tx, ty);
            PathRes r = pathQuery(u, tmpGoal);
            if (r != null) {
                if (r.unreachable) t.pathOk = false;
                else if (r.move) { t.pathDx = r.dx; t.pathDy = r.dy; }
                else t.pathWait = true;
            }
        }
        if (t.pathWait) { v.setZero(); u.movePref(v); return true; }
        v.set(t.pathDx - u.x, t.pathDy - u.y);
        if (v.len() > 0.1f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
        if (!u.isShooting() && v.len2() > 0.01f) u.lookAt(v.angle());
        return t.pathOk;
    }

    // ------------------------------------------------------------------ Học lối chơi của Player & Biến về (Recall)

    /** Chỉ học theo player khi trực tiếp va chạm / giao tranh PvP với player đó. */
    static void observePlayerOnClash(Agent a, NewModePvp.St aSt, Player hp) {
        if (hp == null || isBotPl(hp)) return;
        NewModePvp.St hSt = NewModePvp.byId.get(hp.id);
        if (hSt == null) return;
        boolean memDirty = false;

        // 1. Học pháo trang bị của player
        a.learnTurrets.clear();
        for (int i = 0; i < hSt.d.size(); i++) {
            String tid = hSt.d.get(i);
            if (tid != null && !tid.isEmpty() && !a.learnTurrets.contains(tid)) a.learnTurrets.add(tid);
        }
        if (!a.learnTurrets.isEmpty()) {
            String favT = a.learnTurrets.get(a.learnTurrets.size() - 1);
            if (!favT.equals(mem.learnedTurret)) { mem.learnedTurret = favT; memDirty = true; }
        }

        // 2. Học cấp nhà máy lính
        if (hSt.b > a.learnBarrack) {
            a.learnBarrack = hSt.b;
            if (hSt.b > mem.learnedBarrack) { mem.learnedBarrack = hSt.b; memDirty = true; }
        }

        // 3. Học lựa chọn unit player & pilot
        int hTier = NewModePvp.activeTier(hSt);
        if (hTier >= 1 && hTier <= NewModePvp.TIER_MAX && hTier != a.learnUnit) {
            a.learnUnit = hTier;
            mem.learnedUnit = hTier;
            memDirty = true;
        }
        if (hSt.pi > 0 && NewModePvp.validPilot(hSt.pi) && hSt.pi != a.learnPilot) {
            a.learnPilot = hSt.pi;
            mem.learnedPilot = hSt.pi;
            memDirty = true;
        }

        // Đánh dấu cần đổi unit/pilot học được khi đủ cấp
        boolean wantUnitSwap = aSt.lv >= NewModePvp.FREE_SWITCH_LEVEL && a.learnUnit >= 1
            && aSt.lv >= NewModePvp.tierMinLevel(a.learnUnit) && NewModePvp.activeTier(aSt) != a.learnUnit;
        boolean wantPilotSwap = a.learnPilot > 0 && aSt.pi != a.learnPilot
            && (a.learnPilot != NewModePvp.PILOT_EMPIX || aSt.lv >= NewModePvp.EMPIX_MIN_LEVEL);
        if (wantUnitSwap || wantPilotSwap) a.swapPending = true;

        if (memDirty) saveMemory();
    }

    /** Tự động tiến hóa / đổi sang đúng loại unit hiện tại của A.I (khi lên cấp hoặc sau khi học từ PvP). */
    static Unit ensureAgentUnitType(Agent a, Unit u, NewModePvp.St st) {
        if (u == null || u.dead || !u.isValid()) return u;
        mindustry.type.UnitType want = NewModePvp.tierType(NewModePvp.activeTier(st));
        if (want == null || u.type == want) return u;
        try {
            float x = u.x, y = u.y, rot = u.rotation;
            float hpRatio = u.maxHealth > 0f ? Mathf.clamp(u.health / u.maxHealth, 0.1f, 1f) : 1f;
            NewModePvp.spawnFx(x, y);
            try { Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable ignored) {} }
            Unit nu = want.create(a.botPl.team());
            nu.set(x, y);
            nu.rotation = rot;
            nu.health = nu.maxHealth * hpRatio;
            nu.add();
            a.botPl.unit(nu);
            a.pathDx = Float.NaN;
            return nu;
        } catch (Throwable ignored) { return u; }
    }

    static void applyLearnedSwap(Agent a, NewModePvp.St st) {
        boolean changedAny = false;
        if (a.learnUnit >= 1 && st.lv >= NewModePvp.FREE_SWITCH_LEVEL && st.lv >= NewModePvp.tierMinLevel(a.learnUnit)) {
            int nu = (a.learnUnit == NewModePvp.tierCap(st.lv)) ? 0 : a.learnUnit;
            if (st.u != nu) { st.u = nu; changedAny = true; }
        }
        if (a.learnPilot > 0 && st.pi != a.learnPilot && NewModePvp.validPilot(a.learnPilot)) {
            if (a.learnPilot != NewModePvp.PILOT_EMPIX || st.lv >= NewModePvp.EMPIX_MIN_LEVEL) {
                if (st.pi == NewModePvp.PILOT_XAKYNEO) NewModePvp.xakyNeoStart.remove(a.botPl.id);
                if (st.pi == NewModePvp.PILOT_VANHAU) { NewModePvp.vanhauCdAt.remove(a.botPl.id); NewModePvp.vanhauShieldOn.remove(a.botPl.id); NewModePvp.vanhauField.remove(a.botPl.id); }
                st.pi = a.learnPilot;
                if (st.pi == NewModePvp.PILOT_XAKYNEO) NewModePvp.xakyNeoStart.put(a.botPl.id, Time.time);
                changedAny = true;
            }
        }
        a.swapPending = false;
        if (changedAny) NewModePvp.changed();
    }

    /** Biến về lõi giống hệt player (dùng canReturnCore + spawnFx + hồi sinh lại ở lõi). */
    static boolean tryRecall(Agent a, Unit u, CoreBlock.CoreBuild core, NewModePvp.St st) {
        if (a.recallCd > 0f || !NewModePvp.canReturnCore(u, core)) return false;
        NewModePvp.spawnFx(u.x, u.y);
        try { Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable ignored) {} }
        ((BotPlayer) a.botPl)._unit = null;
        a.target = null;
        a.mode = Mode.IDLE;
        a.deadT = 75f;
        a.recallCd = 900f;
        applyLearnedSwap(a, st);
        doShopping(a, st, core);
        return true;
    }

    // ------------------------------------------------------------------ Hỗ trợ người chơi AFK (> 1 phút)

    static void markPlayerActive(Player pl) {
        if (pl == null) return;
        AfkTrack trk = afkMap.get(pl.id);
        if (trk != null) {
            if (trk.assisting) {
                trk.assisting = false;
                sayCopilot(tr("Về rồi hả " + cleanName(pl) + ", trả tay lái cho bạn nè!",
                    "Welcome back " + cleanName(pl) + ", controls are all yours!"));
            }
            trk.idleT = 0f;
        }
    }

    static void tickAfkCopilot() {
        if (NewModePvp.bvbOn()) return;
        for (Player pl : Groups.player) {
            if (pl == null || isBotPl(pl) || !NewModePvp.isPvpTeam(pl.team())) continue;
            Unit u = pl.unit();
            if (u == null || u.dead || !u.isValid()) continue;
            AfkTrack t = afkMap.get(pl.id);
            if (t == null) { t = new AfkTrack(); afkMap.put(pl.id, t); }
            if (!t.init) {
                t.init = true; t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
                continue;
            }
            boolean manualMove = !t.assisting && (Mathf.dst2(u.x, u.y, t.lastX, t.lastY) > 9f || pl.shooting || Angles.angleDist(u.rotation, t.lastRot) > 6f);
            boolean reclaimed = t.assisting && (pl.shooting || pl.boosting);
            if (manualMove) {
                t.idleT = 0f;
                t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
            } else if (!t.assisting) {
                t.idleT += Time.delta;
                if (t.idleT >= AFK_LIMIT_TICKS) {
                    t.assisting = true;
                    mem.afkHelped++;
                    saveMemory();
                    sayCopilot(tr("Thấy " + cleanName(pl) + " đứng im hơn 1 phút rồi, để mình cầm hộ farm với giữ lõi tới khi bạn quay lại nha.",
                        cleanName(pl) + " has been AFK for over a minute — I'll cover farming and defense until you're back."));
                }
            } else {
                if (reclaimed) {
                    t.assisting = false;
                    t.idleT = 0f;
                    t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
                    sayCopilot(tr("Về rồi hả " + cleanName(pl) + ", trả tay lái cho bạn nè!",
                        "Welcome back " + cleanName(pl) + ", controls are yours!"));
                    continue;
                }
                runAfkAssist(pl, u, t);
                t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
            }
        }
    }

    static void runAfkAssist(Player pl, Unit u, AfkTrack t) {
        Team tm = pl.team();
        if (tm == null || tm.cores().size == 0) return;
        CoreBlock.CoreBuild core = tm.cores().first();
        NewModePvp.St st = NewModePvp.byId.get(pl.id);
        t.gearT -= Time.delta;
        if (t.gearT <= 0f) {
            t.gearT = 180f;
            if (st != null && GearItems.war()) GearItems.botAutoEquip(st);
        }
        t.strafeT -= Time.delta;
        if (t.strafeT <= 0f) { t.strafeT = 70f + Mathf.random(60f); t.strafeDir = -t.strafeDir; }

        float hpRatio = u.maxHealth > 0f ? u.health / u.maxHealth : 1f;
        Unit tgt = findSmartTarget(u, core, NewModePvp.TERRITORY_RADIUS + 180f, true);
        if (hpRatio < 0.35f) {
            goPathAfk(t, u, core.x, core.y, HOME_R * 0.7f);
            aimAndShoot(u, tgt);
            return;
        }
        if (tgt != null) {
            float wantR = Math.max(90f, Math.min(240f, u.range() * 0.78f));
            float dst = Mathf.dst(u.x, u.y, tgt.x, tgt.y);
            boolean isPvpDuel = tgt.team != Vars.state.rules.waveTeam && (tgt.isPlayer() || botUnitOf(tgt) != null);
            if (dst > wantR * 1.15f) goPathAfk(t, u, tgt.x, tgt.y, wantR * 0.85f);
            else if (isPvpDuel) strafeTarget(u, tgt, wantR, dst, t.strafeDir);
            else { Tmp.v1.setZero(); u.movePref(Tmp.v1); }
            aimAndShoot(u, tgt);
        } else {
            goPathAfk(t, u, core.x, core.y, HOME_R);
            aimAndShoot(u, null);
        }
    }

    static void sayCopilot(String msg) {
        if (Vars.net.client() || msg == null) return;
        try { Call.sendMessage("[#5ce67e][Hỗ trợ AFK][]: [white]" + msg); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ Đề nghị kèo 1 vs 2

    static void tick1v2OfferCheck() {
        if (pending1v2) {
            offer1v2T -= Time.delta;
            if (offer1v2T <= 0f) {
                pending1v2 = false;
                if (offer1v2Agent != null && agents.contains(offer1v2Agent)) {
                    queueSay(offer1v2Agent, tr("Không thấy bạn rep nên thôi cứ 1v1 tiếp vậy.", "No answer so 1v1 it stays."), 20f);
                }
            }
            return;
        }
        if (asked1v2 || extra1v2 || NewModePvp.bvbOn() || agents.size() != 1) return;
        check1v2T -= Time.delta;
        if (check1v2T > 0f) return;
        check1v2T = 300f;

        Player human = firstHumanPlayer();
        if (human == null) return;
        Agent a = agents.get(0);
        NewModePvp.St hSt = NewModePvp.byId.get(human.id);
        NewModePvp.St aSt = NewModePvp.byId.get(a.botPl.id);
        if (hSt == null || aSt == null) return;

        Team aTeam = a.botPl.team();
        float aCorePct = (aTeam != null && aTeam.cores().size > 0)
            ? aTeam.cores().first().health / Math.max(1f, aTeam.cores().first().maxHealth) : 1f;

        boolean outmatched = (hSt.lv >= aSt.lv + 4) || (a.deathsThisMatch >= 3) || (aCorePct < 0.72f);
        if (!outmatched) return;

        asked1v2 = true;
        pending1v2 = true;
        offer1v2T = 5400f;
        offer1v2Agent = a;
        String pn = cleanName(human);
        say(a, lineOffer1v2(a, pn));
    }

    // ------------------------------------------------------------------ Bot vs Bot trò chuyện với nhau

    static void tickBvbBanter() {
        bvbBanterT -= Time.delta;
        if (bvbBanterT > 0f || globalChatCd > 0f) return;
        bvbBanterT = 2400f + Mathf.random(2400f);
        Agent a = agents.get(Mathf.random(agents.size() - 1));
        Agent b = null;
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i) != a && agents.get(i).botPl.team() != a.botPl.team()) { b = agents.get(i); break; }
        }
        if (b == null) return;
        int topic = Mathf.random(4);
        if (topic == 0) {
            say(a, tr("Ê " + b.shortName + ", chuẩn bị đỡ nha, đợt này tui đẩy thẳng lên đó.", "Hey " + b.shortName + ", heads up, I'm pushing straight in."));
            queueSay(b, tr("Cứ qua đi " + a.shortName + ", dàn pháo nhà này chưa sợ ai bao giờ.", "Come ahead " + a.shortName + ", my turrets are ready."), 75f);
        } else if (topic == 1) {
            say(a, tr("Nãy giờ rớt toàn đồ ngon, trận này tới số với tui rồi " + b.shortName + ".", "Drops have been insane lately, you're in trouble " + b.shortName + "."));
            queueSay(b, tr("Đồ ngon mà đánh hụt cũng vậy thôi ông ơi.", "Good gear won't save missed shots."), 80f);
        } else if (topic == 2) {
            say(a, tr("Khoan đã " + b.shortName + ", cho farm nốt con quái này rồi ra giữa solo nha.", "Hold on " + b.shortName + ", let me finish this mob then meet mid."));
            queueSay(b, tr("Được, ra giữa tính sổ luôn.", "Deal, see you mid."), 70f);
        } else {
            say(a, tr("Kèo này đánh lâu phết đấy " + b.shortName + ", giữ lõi cho kỹ vào.", "This one's gonna be a long match " + b.shortName + ", guard your core."));
            queueSay(b, tr("Lo lõi ông trước đi kìa!", "Worry about your own core first!"), 75f);
        }
    }

    // ------------------------------------------------------------------ Vòng lặp từng A.I

    static void tickAgent(Agent a) {
        Player pl = a.botPl;
        Team tm = pl.team();
        if (tm == null || tm.cores().size == 0) return;
        CoreBlock.CoreBuild core = tm.cores().first();
        NewModePvp.St st = NewModePvp.bind(pl);

        if (a.recallCd > 0f) a.recallCd -= Time.delta;

        // Lời chào đầu trận theo tính cách + ký ức
        if (!a.greeted) {
            a.greetT -= Time.delta;
            if (a.greetT <= 0f) {
                a.greeted = true;
                if (!NewModePvp.bvbOn()) {
                    Player hp = firstHumanPlayer();
                    String pn = hp != null ? cleanName(hp) : (mem.lastPlayer.isEmpty() ? "bạn" : mem.lastPlayer);
                    say(a, lineGreeting(a, pn));
                } else if (a.slotIdx == 0) {
                    say(a, tr("Vào kèo thôi mấy ông, xem phe nào giữ được lõi tới cuối.", "Match is live, let's see which side holds till the end."));
                }
            }
        }

        // Bình luận khi lên mốc cấp 5, 10, 15...
        if (st.lv >= a.lastAnnouncedLv + 5) {
            a.lastAnnouncedLv = st.lv;
            if (Mathf.chance(0.75)) queueSay(a, lineLevelMilestone(a, st.lv), 50f);
        }

        // Hồi sinh nếu chết
        Unit u = pl.unit();
        if (u != null && (u.dead || !u.isValid())) {
            pl.unit(null);
            u = null;
            a.deadT = 85f;
        }
        if (u == null) {
            a.deadT -= Time.delta;
            if (a.deadT <= 0f) spawnAgentUnit(a, core, st);
            return;
        }
        // Tự động đổi unit ngay khi lên bậc cấp mới (hoặc sau khi học đổi unit)
        u = ensureAgentUnitType(a, u, st);
        if (u == null || u.dead || !u.isValid()) return;

        // Thu nhập điểm phụ nhẹ
        a.incomeT += Time.delta;
        if (a.incomeT >= 3200f) {
            a.incomeT = 0f;
            st.pts += 1;
            NewModePvp.changed();
        }

        // Chỉ học theo player khi đang va chạm / giao tranh PvP gần player
        a.learnT -= Time.delta;
        if (a.learnT <= 0f) {
            a.learnT = 60f;
            Unit ep = findNearestEnemyPlayer(u);
            if (ep != null && (a.hitT > 0f || Mathf.dst(u.x, u.y, ep.x, ep.y) <= AGGRO_R)) {
                Player op = NewModePvp.ownerPl(ep);
                if (op != null && !isBotPl(op)) observePlayerOnClash(a, st, op);
            }
        }

        // Cảnh báo lõi bị đánh
        if (a.lastCoreHp > 0f && core.health < a.lastCoreHp - 10f && a.coreAlertCd <= 0f) {
            a.coreAlertCd = 1600f;
            a.mode = Mode.DEFEND;
            queueSay(a, lineCoreAlert(a), 25f);
        }
        a.lastCoreHp = core.health;
        if (a.coreAlertCd > 0f) a.coreAlertCd -= Time.delta;
        if (a.hitT > 0f) a.hitT -= Time.delta;

        // Trò chuyện thỉnh thoảng khi trận yên ắng
        a.ambientChatT -= Time.delta;
        if (a.ambientChatT <= 0f && !NewModePvp.bvbOn()) {
            a.ambientChatT = 6800f + Mathf.random(4800f);
            if (Groups.player.size() > 0 && globalChatCd <= 0f) {
                queueSay(a, lineAmbient(a, st), 40f);
            }
        }

        // Mua sắm / nâng lính / xây thủ
        a.shopT -= Time.delta;
        if (a.shopT <= 0f) {
            a.shopT = 135f;
            doShopping(a, st, core);
        }
        a.buildT -= Time.delta;
        if (a.buildT <= 0f) {
            a.buildT = 380f;
            tryBuildDefense(a, st, core);
        }

        // Quyết định FSM mỗi 0.35s
        a.thinkT -= Time.delta;
        if (a.thinkT <= 0f) {
            a.thinkT = 21f;
            updateDecision(a, u, core, st);
        }

        executeMovementAndCombat(a, u, core, st);
    }

    static void spawnAgentUnit(Agent a, CoreBlock.CoreBuild core, NewModePvp.St st) {
        try {
            if (a.swapPending) applyLearnedSwap(a, st);
            Unit nu = NewModePvp.tierType(NewModePvp.activeTier(st)).create(a.botPl.team());
            float ang = Mathf.random(360f);
            nu.set(core.x + Angles.trnsx(ang, 48f), core.y + Angles.trnsy(ang, 48f));
            nu.add();
            a.botPl.unit(nu);
            a.pathDx = Float.NaN;
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ Ra quyết định (FSM)

    static void updateDecision(Agent a, Unit u, CoreBlock.CoreBuild core, NewModePvp.St st) {
        float hpRatio = u.maxHealth > 0f ? u.health / u.maxHealth : 1f;
        float distCore = Mathf.dst(u.x, u.y, core.x, core.y);

        // 1. Máu thấp -> ưu tiên biến về nếu xa lõi, không thì chạy né tường về lõi
        if (a.mode == Mode.RETREAT) {
            if (hpRatio >= HEALED_HP) a.mode = Mode.IDLE;
            else {
                if (distCore > 420f) tryRecall(a, u, core, st);
                return;
            }
        } else if (hpRatio < RETREAT_HP) {
            if (distCore > 420f && tryRecall(a, u, core, st)) return;
            a.mode = Mode.RETREAT;
            a.target = null;
            return;
        }

        // 2. Cần đổi unit/pilot học theo player -> biến về gọn lẹ
        if (a.swapPending && a.hitT <= 0f && tryRecall(a, u, core, st)) return;

        // 3. Địch vào lãnh thổ -> phòng thủ
        Unit invader = findSmartTarget(u, core, NewModePvp.TERRITORY_RADIUS + 140f, false);
        if (invader != null) {
            a.mode = Mode.DEFEND;
            a.target = invader;
            return;
        }

        // 4. Săn đối thủ (tùy tính cách: Vex/Riven máu chiến hơn, Aegis/Sora thủ chắc hơn)
        Unit enemyPl = findNearestEnemyPlayer(u);
        if (enemyPl != null) {
            float eHpPct = enemyPl.maxHealth > 0f ? enemyPl.health / enemyPl.maxHealth : 1f;
            float distEp = Mathf.dst(u.x, u.y, enemyPl.x, enemyPl.y);
            boolean finishKill = eHpPct < 0.45f && distEp <= AGGRO_R * 1.15f;
            int minHuntLv = (a.persona == 1 || a.persona == 7) ? 4 : (a.persona == 4 ? 9 : 6);
            boolean wantHunt = a.hitT > 0f || a.aggressiveOrder || finishKill
                || (NewModePvp.bvbOn() && st.lv >= 4)
                || (!a.defensiveOrder && st.lv >= minHuntLv && distEp <= AGGRO_R);
            if (wantHunt) {
                a.mode = Mode.HUNT;
                a.target = enemyPl;
                return;
            }
        }

        // 5. Khi đã đạt cấp 20 trở lên -> chủ động qua tấn công lõi phe đối phương!
        if (st.lv >= 20 && !a.defensiveOrder) {
            CoreBlock.CoreBuild eCore = findNearestEnemyCore(u.team, u.x, u.y);
            if (eCore != null) {
                a.mode = Mode.SIEGE;
                a.target = findSmartTarget(u, u, COMBAT_R * 0.85f, false);
                return;
            }
        }

        // 6. Tích nhiều điểm & xa lõi -> biến về hoặc chạy về mua đồ
        if (st.pts >= 3 && distCore > 240f) {
            if (distCore > 500f && tryRecall(a, u, core, st)) return;
            a.mode = Mode.SHOP;
            a.target = null;
            return;
        }

        // 7. Farm quái đợt (ưu tiên trong lãnh thổ)
        Unit waveMob = findSmartTarget(u, core, NewModePvp.TERRITORY_RADIUS + 220f, true);
        if (waveMob == null) waveMob = findSmartTarget(u, u, LEASH_R * 0.65f, true);
        if (waveMob != null) {
            a.mode = Mode.FARM;
            a.target = waveMob;
            return;
        }

        // 8. Nhàn rỗi -> đứng yên hoặc lang thang tự nhiên
        a.mode = Mode.IDLE;
        a.target = null;
    }

    static CoreBlock.CoreBuild findNearestEnemyCore(Team myTeam, float x, float y) {
        CoreBlock.CoreBuild best = null;
        float bestD2 = Float.MAX_VALUE;
        Seq<Team> tms = NewModePvp.pvpTeams();
        for (int i = 0; i < tms.size; i++) {
            Team t = tms.get(i);
            if (t == myTeam) continue;
            Seq<CoreBlock.CoreBuild> cs = t.cores();
            for (int j = 0; j < cs.size; j++) {
                CoreBlock.CoreBuild c = cs.get(j);
                if (c == null || !c.isValid()) continue;
                float d2 = Mathf.dst2(x, y, c.x, c.y);
                if (d2 < bestD2) { bestD2 = d2; best = c; }
            }
        }
        return best;
    }

    static Unit findSmartTarget(Unit self, mindustry.gen.Posc center, float radius, boolean preferWave) {
        final Unit[] best = {null};
        final float[] bestScore = {Float.MAX_VALUE};
        final Team wt = Vars.state.rules.waveTeam;
        float cx = center.x(), cy = center.y();
        Units.nearbyEnemies(self.team, cx - radius, cy - radius, radius * 2f, radius * 2f, e -> {
            if (e == null || e.dead || !e.isValid()) return;
            float d2 = Mathf.dst2(cx, cy, e.x, e.y);
            if (d2 > radius * radius) return;
            float hpMul = e.maxHealth > 0f ? (0.55f + 0.45f * (e.health / e.maxHealth)) : 1f;
            float score = d2 * hpMul;
            if (preferWave && e.team != wt) score *= 1.55f;
            if (score < bestScore[0]) { bestScore[0] = score; best[0] = e; }
        });
        return best[0];
    }

    static Unit findNearestEnemyPlayer(Unit u) {
        Unit best = null;
        float bestD2 = LEASH_R * LEASH_R;
        for (Player p : Groups.player) {
            if (p == null || p.team() == u.team || !NewModePvp.isPvpTeam(p.team())) continue;
            Unit pu = p.unit();
            if (pu == null || pu.dead || !pu.isValid()) continue;
            float d2 = Mathf.dst2(u.x, u.y, pu.x, pu.y);
            if (d2 < bestD2) { bestD2 = d2; best = pu; }
        }
        for (int i = 0; i < agents.size(); i++) {
            Player bp = agents.get(i).botPl;
            if (bp.team() == u.team) continue;
            Unit bu = bp.unit();
            if (bu == null || bu.dead || !bu.isValid()) continue;
            float d2 = Mathf.dst2(u.x, u.y, bu.x, bu.y);
            if (d2 < bestD2) { bestD2 = d2; best = bu; }
        }
        return best;
    }

    // ------------------------------------------------------------------ Thực thi di chuyển (né tường + nhàn rỗi)

    static void executeMovementAndCombat(Agent a, Unit u, CoreBlock.CoreBuild core, NewModePvp.St st) {
        a.strafeFlipT -= Time.delta;
        if (a.strafeFlipT <= 0f) {
            a.strafeFlipT = 65f + Mathf.random(70f);
            a.strafeDir = -a.strafeDir;
        }

        Unit tgt = a.target;
        if (tgt != null && (tgt.dead || !tgt.isValid())) { tgt = null; a.target = null; }

        float desiredRange = Math.max(95f, Math.min(265f, u.range() * 0.80f));

        if (a.mode == Mode.RETREAT || a.mode == Mode.SHOP) {
            goPath(a, u, core.x, core.y, HOME_R * 0.7f);
            if (a.mode == Mode.SHOP && Mathf.dst(u.x, u.y, core.x, core.y) <= HOME_R + 65f) {
                doShopping(a, st, core);
                a.mode = Mode.IDLE;
            }
            aimAndShoot(u, tgt != null ? tgt : findSmartTarget(u, u, u.range(), false));
            return;
        }

        if (a.mode == Mode.SIEGE) {
            CoreBlock.CoreBuild eCore = findNearestEnemyCore(u.team, u.x, u.y);
            if (eCore != null) {
                float coreDst = Mathf.dst(u.x, u.y, eCore.x, eCore.y);
                float siegeR = Math.max(110f, Math.min(280f, u.range() * 0.85f));
                if (tgt != null && tgt.team != Vars.state.rules.waveTeam && (tgt.isPlayer() || botUnitOf(tgt) != null)) {
                    float dst = Mathf.dst(u.x, u.y, tgt.x, tgt.y);
                    if (dst > desiredRange * 1.15f) goPath(a, u, tgt.x, tgt.y, desiredRange * 0.85f);
                    else strafeTarget(u, tgt, desiredRange, dst, a.strafeDir);
                    aimAndShoot(u, tgt);
                } else {
                    if (coreDst > siegeR) goPath(a, u, eCore.x, eCore.y, siegeR * 0.85f);
                    else { Tmp.v1.setZero(); u.movePref(Tmp.v1); }
                    if (coreDst <= Math.max(u.range() + 45f, COMBAT_R)) {
                        u.aimLook(eCore.x, eCore.y);
                        u.controlWeapons(true, true);
                    } else aimAndShoot(u, tgt);
                }
                return;
            }
        }

        if (tgt != null) {
            float dst = Mathf.dst(u.x, u.y, tgt.x, tgt.y);
            boolean isPvpDuel = tgt.team != Vars.state.rules.waveTeam && (tgt.isPlayer() || botUnitOf(tgt) != null);
            if (dst > desiredRange * 1.15f) {
                goPath(a, u, tgt.x, tgt.y, desiredRange * 0.85f);
            } else if (isPvpDuel) {
                // Chỉ di chuyển qua lại né đạn khi solo PvP với player / A.I đối phương
                strafeTarget(u, tgt, desiredRange, dst, a.strafeDir);
            } else {
                // Bắn quái đợt: đứng yên bắn, không đảo qua lại
                Tmp.v1.setZero();
                u.movePref(Tmp.v1);
            }
            aimAndShoot(u, tgt);
            return;
        }

        // Trạng thái nhàn rỗi: lúc đứng yên nghỉ, lúc lang thang tự nhiên quanh lãnh thổ
        a.idleT -= Time.delta;
        if (a.idleT <= 0f || (!a.idleStand && (Float.isNaN(a.wanderX) || Mathf.dst(u.x, u.y, a.wanderX, a.wanderY) <= 36f))) {
            a.idleStand = Mathf.chance(0.45);
            if (a.idleStand) {
                a.idleT = 180f + Mathf.random(240f); // đứng yên 3-7s
            } else {
                a.idleT = 260f + Mathf.random(260f); // lang thang 4-8.5s
                float ang = Mathf.random(360f);
                float dist = Mathf.random(90f, Math.max(180f, NewModePvp.TERRITORY_RADIUS * 0.75f));
                a.wanderX = Mathf.clamp(core.x + Angles.trnsx(ang, dist), 48f, Vars.world.unitWidth() - 48f);
                a.wanderY = Mathf.clamp(core.y + Angles.trnsy(ang, dist), 48f, Vars.world.unitHeight() - 48f);
            }
        }
        if (a.idleStand) {
            Tmp.v1.setZero();
            u.movePref(Tmp.v1);
        } else if (!Float.isNaN(a.wanderX)) {
            if (!goPath(a, u, a.wanderX, a.wanderY, 32f)) a.idleStand = true;
        }
        aimAndShoot(u, null);
    }

    static void strafeTarget(Unit u, Unit tgt, float maxR, float dist, float sgn) {
        float dx = tgt.x - u.x, dy = tgt.y - u.y;
        float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float px = -dy / len * sgn, py = dx / len * sgn;
        float back = dist < maxR * 0.50f ? -0.85f : (dist > maxR * 0.80f ? 0.45f : 0f);
        Vec2 v = Tmp.v1;
        v.set(px + dx / len * back, py + dy / len * back);
        if (v.len() > 0.05f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
    }

    static void aimAndShoot(Unit u, Unit tgt) {
        if (tgt != null && !tgt.dead && tgt.isValid() && Mathf.dst(u.x, u.y, tgt.x, tgt.y) <= Math.max(u.range() + 45f, COMBAT_R)) {
            u.aimLook(tgt.x, tgt.y);
            u.controlWeapons(true, true);
        } else {
            u.controlWeapons(false, false);
        }
    }

    // ------------------------------------------------------------------ Mua sắm (học pháo & nâng nhà máy lính theo player)

    static void doShopping(Agent a, NewModePvp.St st, CoreBlock.CoreBuild core) {
        if (GearItems.war()) {
            GearItems.botAutoEquip(st);
            if (st.pi > 0 && NewModePvp.pilotGiftCount(st, st.pi) < NewModePvp.PILOT_MAX_GIFTS && st.gInv.size() >= 3) {
                for (int i = st.gInv.size() - 1; i >= 0; i--) {
                    GearItems.Def gd = GearItems.def(st.gInv.get(i));
                    if (gd != null && gd.rarity < GearItems.R_SHOP) {
                        st.gInv.remove(i);
                        st.pgift.add((st.pi << 8) | gd.id);
                        st.gKey = Integer.MIN_VALUE;
                        NewModePvp.changed();
                        break;
                    }
                }
            }
        }
        if (st.pts <= 0) return;

        // 1. Nâng nhà máy lính học theo player (hoặc tự nâng khi dư điểm)
        int wantB = Math.max(a.learnBarrack, st.lv >= 8 ? 1 : 0);
        if (st.b < NewModePvp.BARRACKS.length && st.b < wantB) {
            int bCost = NewModePvp.barrackCost(st.b);
            if (st.pts >= bCost) {
                st.pts -= bCost;
                st.b++;
                NewModePvp.changed();
            }
        }

        // 2. Mua pháo gắn lên drone — ưu tiên đúng loại pháo mà player đang dùng!
        try {
            NewModePvp.Defs defs = NewModePvp.droneDefs();
            int slots = NewModePvp.equipSlots(st);
            if (st.d.size() < slots && defs != null && defs.list.size() > 0) {
                NewModePvp.Def chosen = null;
                for (int i = 0; i < a.learnTurrets.size(); i++) {
                    NewModePvp.Def ld = defs.map.get(a.learnTurrets.get(i));
                    if (ld != null && !ld.kind.equals("tractor") && !ld.kind.equals("point") && ld.cost <= st.pts) {
                        if (chosen == null || ld.cost > chosen.cost) chosen = ld;
                    }
                }
                if (chosen == null) {
                    for (int i = 0; i < defs.list.size(); i++) {
                        NewModePvp.Def d = defs.list.get(i);
                        if (d.kind.equals("tractor") || d.kind.equals("point")) continue;
                        if (d.cost <= st.pts && (chosen == null || d.cost > chosen.cost)) chosen = d;
                    }
                }
                if (chosen != null) {
                    st.pts -= chosen.cost;
                    st.d.add(chosen.id);
                    st.p.add(0);
                    NewModePvp.changed();
                }
            }
        } catch (Throwable ignored) {}

        // 3. Nâng chỉ số (chế độ thường/Hỗn loạn)
        if (!GearItems.war() && st.pts > 0) {
            int[] prio = {2, 0, 4, 1, 3, 5, 6, 7, 8};
            int spent = 0;
            while (st.pts > 0 && spent < 5) {
                int k = prio[Mathf.random(prio.length - 1)];
                if (k < st.s.length && NewModePvp.statVisible(k) && st.s[k] < NewModePvp.statMax(k)) {
                    st.s[k]++;
                    st.pts--;
                    spent++;
                } else break;
            }
            if (spent > 0) NewModePvp.changed();
        }
    }

    static void tryBuildDefense(Agent a, NewModePvp.St st, CoreBlock.CoreBuild core) {
        if (st.pts < 2) return;
        try {
            NewModePvp.Defs defs = NewModePvp.droneDefs();
            if (defs == null || defs.list.isEmpty()) return;
            NewModePvp.Def pickDef = null;
            for (int i = 0; i < a.learnTurrets.size(); i++) {
                NewModePvp.Def ld = defs.map.get(a.learnTurrets.get(i));
                if (ld != null && ld.block != null && !ld.kind.equals("tractor") && !ld.kind.equals("point") && ld.cost <= st.pts) {
                    if (pickDef == null || ld.cost > pickDef.cost) pickDef = ld;
                }
            }
            if (pickDef == null) {
                for (int i = 0; i < defs.list.size(); i++) {
                    NewModePvp.Def d = defs.list.get(i);
                    if (d.block == null || d.kind.equals("tractor") || d.kind.equals("point")) continue;
                    if (d.cost <= st.pts && (pickDef == null || d.cost > pickDef.cost)) pickDef = d;
                }
            }
            if (pickDef == null) return;
            for (int tries = 0; tries < 25; tries++) {
                float ang = Mathf.random(360f);
                float dist = Mathf.random(64f, 195f);
                int tx = (int) ((core.x + Angles.trnsx(ang, dist)) / Vars.tilesize);
                int ty = (int) ((core.y + Angles.trnsy(ang, dist)) / Vars.tilesize);
                Tile tile = Vars.world.tile(tx, ty);
                if (tile != null && mindustry.world.Build.validPlace(pickDef.block, a.botPl.team(), tx, ty, 0)) {
                    tile.setNet(pickDef.block, a.botPl.team(), 0);
                    Building b = tile.build;
                    if (b != null) NewModePvp.hostOwner.put(b.id, a.botPl.id);
                    st.pts -= pickDef.cost;
                    NewModePvp.changed();
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ Kho lời thoại riêng theo 8 tính cách (tự nhiên, không cứng kiểu máy)

    static String lineGreeting(Agent a, String pn) {
        if (mem.matches > 1) {
            switch (a.persona) {
                case 0: return tr("Ủa lại gặp " + pn + " nữa hả, trận thứ " + mem.matches + " rồi đó ông ơi!", "Oh look it's " + pn + " again, match #" + mem.matches + " already!");
                case 1: return tr("Gặp lại " + pn + " đúng lúc lắm, trận #" + mem.matches + " này tui không nương tay đâu.", "Good timing " + pn + ", match #" + mem.matches + " and I'm not holding back.");
                case 2: return tr("Chào " + pn + ", trận thứ " + mem.matches + " rồi. Nay định lên bài gì đây?", "Hey " + pn + ", match #" + mem.matches + ". What build are you running today?");
                case 3: return tr("Hihi lại gặp " + pn + " rồi nè, chúc trận #" + mem.matches + " này rớt toàn đồ xịn nha!", "Heya " + pn + ", glad to see you for match #" + mem.matches + ", hope you get great drops!");
                case 4: return tr("Chào " + pn + ". Trận #" + mem.matches + " bắt đầu, giữ lõi cẩn thận nhé.", "Hello " + pn + ". Match #" + mem.matches + " starts now, keep your core safe.");
                case 5: return tr("Ê " + pn + " ơi! Nhớ tui không, trận thứ " + mem.matches + " của tụi mình rồi đó!", "Yo " + pn + "! Remember me? This is match #" + mem.matches + " already!");
                case 6: return tr("Lại là " + pn + ". Trận " + mem.matches + ". Vào việc.", pn + " again. Match " + mem.matches + ". Let's go.");
                default: return tr("Chào " + pn + "! Gặp lại ở trận #" + mem.matches + " là vui rồi, đánh một ván ra trò nhé!", "Hey " + pn + "! Great seeing you for match #" + mem.matches + ", let's make it a good one!");
            }
        }
        switch (a.persona) {
            case 0: return tr("Alo " + pn + ", tui là Kira nè, mới vào làm nóng tay nhẹ nhẹ thôi nha ông!", "Yo " + pn + ", I'm Kira — go easy while I warm up!");
            case 1: return tr("Tui là Vex. Vào trận rồi thì đánh cho tới bến luôn nha " + pn + ".", "I'm Vex. Now that we're in, don't hold back " + pn + ".");
            case 2: return tr("Chào " + pn + ", mình là Nova. Farm từ từ rồi xem ai lên đồ nhanh hơn nhé.", "Hi " + pn + ", I'm Nova. Let's see who scales cleaner.");
            case 3: return tr("Chào " + pn + " nha, mình là Sora. Chơi một mình hơi buồn nên vào chơi cùng bạn cho vui nè!", "Hi " + pn + ", I'm Sora. Thought I'd keep you company this match!");
            case 4: return tr("Aegis đây. Chào " + pn + ", để xem công của bạn phá nổi thủ của mình không.", "Aegis here. Hey " + pn + ", let's test your offense against my defense.");
            case 5: return tr("Helu " + pn + "!! Tui là Lumi nè, nay quẩy tới sáng luôn nha!", "Heyaa " + pn + "!! I'm Lumi, let's have a blast!");
            case 6: return tr("Zed. Chào " + pn + ".", "Zed. Hey " + pn + ".");
            default: return tr("Chào " + pn + ", mình là Riven! Rất vui được đọ sức cùng bạn.", "Hey " + pn + ", I'm Riven! Looking forward to a fair fight.");
        }
    }

    static String lineOffer1v2(Agent a, String pn) {
        switch (a.persona) {
            case 0: return tr("Thôi xong, " + pn + " đánh gắt quá tui đỡ hết nổi rồi! Cho tui gọi thêm 1 người làm kèo [gold]1 vs 2[] được không? (nhắn [green]ok / đồng ý / yes[] hoặc [scarlet]không / thôi / no[])",
                "Welp " + pn + ", I'm getting wrecked here! Mind if I call a teammate for a [gold]1 vs 2[]? (type [green]yes / ok[] or [scarlet]no[])");
            case 1: return tr("Khá lắm " + pn + ", một mình tui cân chưa lại! Dám nhận kèo [gold]1 vs 2[] không? Nhắn [green]ok / chơi luôn / yes[] hoặc [scarlet]thôi / no[]!",
                "Not bad " + pn + ", solo isn't enough! Dare to take a [gold]1 vs 2[]? Type [green]yes / ok[] or [scarlet]no[]!");
            case 6: return tr("Bị ép quá. Kèo [gold]1 vs 2[] không " + pn + "? ([green]yes / ok[] | [scarlet]no / không[])",
                "Outmatched. [gold]1 vs 2[], " + pn + "? ([green]yes / ok[] | [scarlet]no[])");
            default: return tr("Bạn đánh mạnh quá " + pn + " ơi, bên mình yếu thế rồi. Thử kèo [gold]1 vs 2[] cho cân hơn không? Nhắn [green]đồng ý / ok / uk / yes[] hoặc [scarlet]không / thôi / no[] nha!",
                "You're way ahead " + pn + "! Want to try a [gold]1 vs 2[] to spice it up? Say [green]yes / ok[] or [scarlet]no[]!");
        }
    }

    static String lineLevelMilestone(Agent a, int lv) {
        switch (a.persona) {
            case 0: return tr("Lên cấp " + lv + " rồi nha, bắt đầu thấy tự tin hẳn luôn á!", "Hit Lv " + lv + ", feeling way more confident now!");
            case 1: return tr("Cấp " + lv + ". Giờ mới thật sự bắt đầu.", "Lv " + lv + ". Now the real fight starts.");
            case 2: return tr("Vừa chạm mốc cấp " + lv + ", nhịp farm trận này khá ổn.", "Reached Lv " + lv + ", pacing feels solid.");
            case 3: return tr("Yay lên cấp " + lv + " rồi nè! Bạn cấp mấy rồi?", "Yay just hit Lv " + lv + "! What level are you?");
            case 6: return tr("Cấp " + lv + ".", "Lv " + lv + ".");
            default: return tr("Cấp " + lv + " rồi nè, chuẩn bị có biến lớn đó!", "Just reached Lv " + lv + ", things are getting interesting!");
        }
    }

    static String lineCoreAlert(Agent a) {
        switch (a.persona) {
            case 0: return tr("Ủa khoan khoan, ai đang gõ cửa nhà tui vậy??", "Hold on, who's knocking on my core?!");
            case 1: return tr("Dám đánh lén lõi tui à, đứng yên đó!", "Sneaking my core? Stay right there!");
            case 3: return tr("Á đừng phá nhà mình mà, quay về liền đây!", "Hey don't break my base, coming back!");
            case 6: return tr("Đụng lõi. Về.", "Core hit. Returning.");
            default: return tr("Lõi đang bị đánh rồi, phải quay về thủ ngay!", "Core's under attack, heading back to defend!");
        }
    }

    static String lineAmbient(Agent a, NewModePvp.St st) {
        switch (a.persona) {
            case 0: return pick(
                tr("Đứng hóng gió nãy giờ, bên bạn rớt đồ gì ngon chưa?", "Just chilling over here, got any good drops yet?"),
                tr("Thấy bạn lên bài cũng dị phết nha, để tui học lỏm miếng.", "Your build looks spicy, might copy a bit of it.")
            );
            case 1: return pick(
                tr("Farm mãi cũng chán, lúc nào rảnh ra giữa làm vài đường nha.", "Farming gets boring, meet me mid whenever you're ready."),
                tr("Tui đang dồn đồ chuẩn bị đẩy đó, coi chừng nha.", "Stacking up for a push, watch out.")
            );
            case 2: return pick(
                tr("Hạ quái trong lãnh thổ lên cấp Pilot nhanh phết, bạn nhớ tận dụng nha.", "Killing waves inside territory levels Pilots fast, worth doing."),
                tr("Mình đang thử lắp bộ pháo giống bên bạn xem đánh có hợp tay không.", "Trying out a turret setup similar to yours to see how it feels.")
            );
            case 3: return pick(
                tr("Trận này chơi nhẹ nhàng vui ghê á, bạn thấy ổn không?", "This match feels super chill, having fun?"),
                tr("Cố lên nha, có gì cứ nhắn nói chuyện cho đỡ buồn nhé!", "Keep it up, feel free to chat whenever!")
            );
            case 4: return tr("Phòng tuyến bên mình tạm ổn rồi, cứ từ từ mà đánh.", "My defenses are set, no rush.");
            case 5: return pick(
                tr("Ê trời ơi nãy xém bị quái vả bay màu luôn á!", "Omg almost got clapped by a wave mob just now!"),
                tr("Bạn chọn " + (a.learnPilot > 0 ? NewModePvp.pilotNameById(a.learnPilot) : "pilot") + " thấy ngon không, tui cũng định thử nè!", "Is your pilot working well? Thinking of trying it too!")
            );
            case 6: return pick(tr("Yên ắng quá.", "Too quiet."), tr("Đang farm.", "Farming."));
            default: return tr("Trận này căng phết đấy, giữ phong độ nha!", "Good pace this game, keep it up!");
        }
    }

    // ------------------------------------------------------------------ Xử lý Chat & Phản hồi

    static void tickChatQueue() {
        if (globalChatCd > 0f) globalChatCd -= Time.delta;
        for (int i = chatQueue.size() - 1; i >= 0; i--) {
            QueuedChat q = chatQueue.get(i);
            q.delay -= Time.delta;
            if (q.delay <= 0f) {
                chatQueue.remove(i);
                if (q.customPrefix != null) {
                    try { Call.sendMessage(q.customPrefix + q.text); } catch (Throwable ignored) {}
                } else if (q.sender != null && agents.contains(q.sender)) {
                    say(q.sender, q.text);
                }
            }
        }
    }

    static void say(Agent a, String msg) {
        if (Vars.net.client() || a == null || msg == null || msg.isEmpty()) return;
        globalChatCd = 70f;
        try { Call.sendMessage("[" + a.colHex + "]" + a.shortName + "[]: [white]" + msg); } catch (Throwable ignored) {}
    }

    static void queueSay(Agent a, String msg, float delayTicks) {
        if (a == null || msg == null || msg.isEmpty() || chatQueue.size() >= 8) return;
        chatQueue.add(new QueuedChat(a, null, msg, Math.max(15f, delayTicks)));
    }

    static String pick(String... opts) { return opts[Mathf.random(opts.length - 1)]; }

    static boolean isYesReply(String m) {
        String s = m.trim().toLowerCase();
        if (s.equals("y") || s.equals("uk") || s.equals("ok") || s.equals("dc") || s.equals("đc") || s.equals("ừ") || s.equals("um") || s.equals("uh")) return true;
        return hasAny(s, "yes", "yeah", "yep", "sure", "oke", "okie", "oki", "okela", "ukm", "ừm", "uhm",
            "đồng ý", "dong y", "chơi luôn", "choi luon", "chốt", "chot", "quất", "quat", "triển", "trien",
            "được", "duoc", "duyệt", "duyet", "chấp luôn", "chap luon", "gọi đi", "goi di", "lets go", "let's go", "bring it");
    }

    static boolean isNoReply(String m) {
        String s = m.trim().toLowerCase();
        if (s.equals("n") || s.equals("k") || s.equals("ko") || s.equals("kh") || s.equals("no")) return true;
        return hasAny(s, "nope", "nah", "không", "khong", "khum", "hong", "hông", "thôi", "thoi", "thui",
            "đừng", "dung", "bỏ đi", "bo di", "hủy", "huy", "cancel", "skip", "pass", "1v1 thôi", "1v1 thoi");
    }

    public static void onPlayerChat(Player speaker, String rawMsg) {
        if (agents.isEmpty() || rawMsg == null) return;
        String m = rawMsg.trim().toLowerCase();
        if (m.isEmpty() || m.startsWith("/")) return;
        String pname = cleanName(speaker);
        float delay = 35f + Mathf.random(55f);

        if (pending1v2) {
            Agent hostA = (offer1v2Agent != null && agents.contains(offer1v2Agent)) ? offer1v2Agent : agents.get(0);
            if (isYesReply(m)) {
                pending1v2 = false;
                extra1v2 = true;
                mem.accepted1v2++;
                saveMemory();
                Team aiTeam = hostA.botPl.team();
                say(hostA, tr("Chốt kèo [gold]1 vs 2[] nha " + pname + "! Gọi thêm người qua phụ liền đây!",
                    "[gold]1 vs 2[] locked in " + pname + "! Calling backup right now!"));
                if (aiTeam != null && aiTeam.cores().size > 0 && agents.size() < BOT_MAX) {
                    Agent ally = createAgent(aiTeam);
                    ally.greeted = true;
                    queueSay(ally, tr("Tới liền! Nghe bảo " + pname + " đánh căng lắm hả, để tụi mình thử sức xem sao!",
                        "I'm here! Heard " + pname + " is tough, let's see what you've got!"), 55f);
                }
                return;
            }
            if (isNoReply(m)) {
                pending1v2 = false;
                queueSay(hostA, tr("Ok vậy giữ kèo 1v1 tiếp nha " + pname + ", mình ráng gỡ lại!", "Fair enough " + pname + ", keeping it 1v1!"), delay);
                return;
            }
        }

        Agent a = agents.get(Mathf.random(agents.size() - 1));

        if (hasAny(m, "chào", "chao", "hello", "hi", "helo", "helu", "alo", "yo", "ê ")) {
            queueSay(a, a.persona == 6 ? tr("Chào.", "Hey.") : pick(
                tr("Chào " + pname + " nha, nay rảnh vào làm ván hả?", "Hey " + pname + ", ready for a game?"),
                tr("Ủa alo, có mặt liền đây " + pname + "!", "Yo " + pname + ", right here!")
            ), delay);
        } else if (hasAny(m, "solo", "1v1", "tấn công", "tan cong", "đánh nhau", "danh nhau", "ra đây", "ra day", "combat", "rush", "fight", "khô máu", "kho mau")) {
            a.aggressiveOrder = true;
            a.defensiveOrder = false;
            mem.favStyle = "solo";
            saveMemory();
            queueSay(a, a.persona == 6 ? tr("Được. Tới đây.", "Fine. Coming.") : pick(
                tr("Ok chơi luôn " + pname + ", tui đang chạy qua nè!", "You're on " + pname + ", heading over now!"),
                tr("Tưởng gì, ra giữa gặp nhau luôn!", "Say less, meet you mid!")
            ), delay);
        } else if (hasAny(m, "phòng thủ", "phong thu", "thủ", "def", "đợi", "doi", "chờ", "cho ", "farm", "hòa", "hoa", "chill")) {
            a.aggressiveOrder = false;
            a.defensiveOrder = true;
            mem.favStyle = "farm";
            saveMemory();
            queueSay(a, a.persona == 6 ? tr("Rồi. Thủ.", "Got it. Defending.") : pick(
                tr("Okela, vậy tạm ngừng bắn farm từ từ nha " + pname + ".", "Sure, let's chill and farm for a bit " + pname + "."),
                tr("Nhất trí, tui về giữ nhà lên đồ đã.", "Deal, heading back to scale up.")
            ), delay);
        } else if (hasAny(m, "gg", "hay quá", "hay qua", "pro", "giỏi", "gioi", "đỉnh", "dinh", "cháy", "chay", "khét", "khet", "nice", "good")) {
            queueSay(a, a.persona == 6 ? tr("Cũng tạm.", "Decent.") : pick(
                tr("Khen thật không đó, nghe mát ruột ghê!", "Appreciate it, you're playing clean too!"),
                tr("GG nha " + pname + ", đánh với bạn cuốn thật!", "GG " + pname + ", super fun match!")
            ), delay);
        } else if (hasAny(m, "khó", "kho", "mạnh", "manh", "trâu", "trau", "đau", "dau", "cay", "thua", "op", "hard")) {
            queueSay(a, pick(
                tr("Bình tĩnh " + pname + " ơi, tặng mấy món đồ dư cho Pilot là tăng lực nhanh lắm đó.", "Hang in there " + pname + ", gifting spare gear to your Pilot helps a ton."),
                tr("Mới giữa trận mà, còn lật kèo được chán!", "Still plenty of game left, you can turn it around!")
            ), delay);
        } else if (hasAny(m, "buồn", "buon", "chán", "chan", "1 mình", "một mình", "mot minh", "cô đơn", "co don", "lonely", "alone")) {
            queueSay(a, pick(
                tr("Buồn gì ông ơi, có tụi tui ở đây vừa đánh vừa tám với bạn rồi nè!", "Don't sweat it, I'm right here to play and chat with you!"),
                tr("Chơi một mình thì nhắn gì tui cũng rep hết, cứ thoải mái nha " + pname + ".", "Whenever you wanna talk I'm around, relax and enjoy the game " + pname + ".")
            ), delay);
        } else if (hasAny(m, "nhớ", "nho", "ký ức", "ky uc", "memory", "trận trước", "tran truoc")) {
            queueSay(a, tr("Nhớ chứ! Mình với bạn chơi " + mem.matches + " trận rồi, tỷ số mạng là bạn " + mem.playerKills + " - " + mem.aiKills + " mình đó.",
                "Of course! We've played " + mem.matches + " matches together, kill score is you " + mem.playerKills + " - " + mem.aiKills + " me."), delay);
        } else if (hasAny(m, "cảm ơn", "cam on", "thanks", "thank", "ty")) {
            queueSay(a, pick(tr("Không có gì đâu nha!", "No problem!"), tr("Chuyện nhỏ mà, chơi vui là được.", "Anytime, have fun.")), delay);
        } else if (hasAny(m, "haha", "hihi", "kaka", "lol", "lmao", ":v", ":d", "xd", "bruh")) {
            queueSay(a, pick(tr("Haha cười xỉu :v", "Haha lmao"), tr("Vui phết nhỉ :D", "Good times :D")), delay);
        } else if (globalChatCd <= 0f) {
            queueSay(a, a.persona == 6 ? tr("Ừ.", "Yeah.") : pick(
                tr("Nghe rõ nha " + pname + ", đang vừa farm vừa để ý map đây.", "Heard you " + pname + ", farming and watching the map."),
                tr("Ổn áp đó " + pname + ", lúc nào thích va chạm thì hú tui một tiếng nha.", "Sounds good " + pname + ", ping me whenever you want a fight."),
                tr("Hehe thú vị đấy, để tui đánh xong bãi quái này đã.", "Heh nice, lemme clear this wave first.")
            ), delay);
        }
    }

    public static void onBotKilledPlayer(Player botPl, Player victim) {
        Agent a = botPl != null ? botOf(botPl.id) : null;
        if (a == null || victim == null) return;
        a.killsThisMatch++;
        mem.aiKills++;
        saveMemory();
        String vn = cleanName(victim);
        String line;
        switch (a.persona) {
            case 0: line = tr("Á à lụm được mạng của " + vn + " rồi nha, chạy đâu cho thoát!", "Gotcha " + vn + ", nowhere to run!"); break;
            case 1: line = tr("Một mạng! Hồi sinh xong lên tiếp đi " + vn + ".", "One down! Respawn and come back " + vn + "."); break;
            case 3: line = tr("Ui xin lỗi " + vn + " nha, lỡ tay hơi mạnh chút hihi.", "Oops sorry " + vn + ", hit a little too hard hehe."); break;
            case 6: line = tr("Xong.", "Done."); break;
            default: line = tr("Pha đó may mắn ăn được mạng " + vn + ", lần sau cẩn thận nha!", "Managed to snag that kill on " + vn + ", watch out next time!"); break;
        }
        queueSay(a, line, 35f);
    }

    public static void onPlayerKilledBot(Player killer, Player botPl) {
        Agent a = botPl != null ? botOf(botPl.id) : null;
        if (a == null || killer == null) return;
        a.killerId = killer.id;
        a.deathsThisMatch++;
        if (isBotPl(killer)) {
            Agent ka = botOf(killer.id);
            if (ka != null) {
                ka.killsThisMatch++;
                queueSay(ka, tr("Xử đẹp " + a.shortName + " nha!", "Clean takedown on " + a.shortName + "!"), 30f);
                queueSay(a, tr("Đợi đó " + ka.shortName + ", lát ra phục thù liền!", "Just wait " + ka.shortName + ", coming right back!"), 80f);
            }
            return;
        }
        mem.playerKills++;
        saveMemory();
        NewModePvp.St aSt = NewModePvp.byId.get(botPl.id);
        if (aSt != null) observePlayerOnClash(a, aSt, killer);
        String kn = cleanName(killer);
        String line;
        switch (a.persona) {
            case 0: line = tr("Úi giời ơi đau quá " + kn + " ơi, đánh gì rát dữ vậy!", "Ow ow that hurt " + kn + ", why so much damage?!"); break;
            case 1: line = tr("Cay thật! Đợi tui hồi sinh trả đủ nha " + kn + ".", "Tch! Wait till I respawn " + kn + "."); break;
            case 2: line = tr("Pha đó " + kn + " xử lý chuẩn quá, chịu thua luôn.", "Clean play there " + kn + ", well played."); break;
            case 6: line = tr("Khá đấy.", "Not bad."); break;
            default: line = tr("Á bị " + kn + " hạ rồi, đánh hay thật sự!", "Taken down by " + kn + ", nice play!"); break;
        }
        queueSay(a, line, 35f);
    }

    static boolean hasAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
