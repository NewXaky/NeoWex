package newex.content;

import arc.Events;
import arc.files.Fi;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
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
 * Hệ thống A.I độc lập cho chế độ NewModePvp.
 * - Lưu ký ức lâu dài vào thư mục dữ liệu game (chỉ mất khi xóa game).
 * - Giao tranh thông minh, tự nâng cấp/mua đồ/xây thủ/tặng quà Pilot.
 * - Đề nghị kèo 1 vs 2 khi A.I thua thiệt (người chơi xác nhận qua chat VI/EN).
 * - Tự động hỗ trợ cầm lái khi người chơi AFK > 1 phút cho đến khi quay lại.
 * - Hỗ trợ Bot vs Bot với cả hai phe A.I tự đánh và trò chuyện với nhau.
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
    static final float AFK_LIMIT_TICKS = 60f * 60f; // 1 phút
    static final String MEM_FILE = "newex-ai-memory.json";

    static final String[] NAMES = {"Kira", "Vex", "Nova", "Sora", "Aegis", "Lumi", "Zed", "Riven"};
    static final String[] COLORS = {"#6ee7ff", "#ffb86c", "#c084fc", "#5ce67e", "#ff79c6", "#f1fa8c", "#ff6e6e", "#8be9fd"};

    enum Mode { FARM, DEFEND, HUNT, RETREAT, SHOP }

    /** Ký ức lâu dài của A.I lưu trực tiếp trong thiết bị (Vars.dataDirectory). */
    static final class Memory {
        int matches = 0;
        int playerKills = 0;
        int aiKills = 0;
        int accepted1v2 = 0;
        int afkHelped = 0;
        String lastPlayer = "";
        String favStyle = "balanced"; // "solo" | "farm" | "balanced"
    }

    /** Theo dõi trạng thái AFK của từng người chơi thật. */
    static final class AfkTrack {
        float lastX, lastY, lastRot, idleT;
        boolean assisting;
        float strafeDir = 1f, strafeT = 80f, gearT = 180f;
        boolean init;
    }

    /** Người chơi giả đại diện cho A.I (không thêm vào Groups.player). */
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

    /** Ngữ cảnh riêng cho từng A.I độc lập. */
    public static final class Agent {
        public Player botPl;
        public String shortName, colHex;
        public int slotIdx;
        public Mode mode = Mode.FARM;
        public float deadT = 40f, thinkT = 0f, shopT = 150f, buildT = 340f, incomeT = 0f;
        public float hitT = 0f, stuckT = 0f, lastX = 0f, lastY = 0f, strafeDir = 1f, strafeFlipT = 90f;
        public float ambientChatT = 3600f, coreAlertCd = 0f, lastCoreHp = -1f, greetT = 160f;
        public boolean greeted = false, aggressiveOrder = false, defensiveOrder = false;
        public int lastAnnouncedLv = 1, deathsThisMatch = 0, killsThisMatch = 0;
        public Unit target;
        public Entityc hitBy;
        public Integer killerId;
    }

    static final class QueuedChat {
        Agent sender;
        String customPrefix;
        String text;
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
    static final Vec2 tmpVec = new Vec2();

    // ------------------------------------------------------------------ Bộ nhớ lâu dài trên thiết bị

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
            mem.lastPlayer = j.getString("lastPlayer", "");
            mem.favStyle = j.getString("favStyle", "balanced");
        } catch (Throwable err) {
            Log.err("[PvpBotAI] loadMemory failed", err);
        }
    }

    static void saveMemory() {
        try {
            Fi f = memFile();
            if (f == null) return;
            String lp = (mem.lastPlayer == null ? "" : mem.lastPlayer).replace("\\", "\\\\").replace("\"", "\\\"");
            String fs = (mem.favStyle == null ? "balanced" : mem.favStyle).replace("\\", "\\\\").replace("\"", "\\\"");
            String json = "{\"matches\":" + mem.matches
                + ",\"playerKills\":" + mem.playerKills
                + ",\"aiKills\":" + mem.aiKills
                + ",\"accepted1v2\":" + mem.accepted1v2
                + ",\"afkHelped\":" + mem.afkHelped
                + ",\"lastPlayer\":\"" + lp + "\""
                + ",\"favStyle\":\"" + fs + "\"}";
            f.writeString(json, false, "UTF-8");
        } catch (Throwable err) {
            Log.err("[PvpBotAI] saveMemory failed", err);
        }
    }

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
    static String cleanName(Player p) { return p != null && p.name != null ? p.name.replaceAll("\\[[^\\]]*\\]", "").trim() : "bro"; }

    // ------------------------------------------------------------------ Tạo / Xóa / Thay thế A.I

    static Player makeBotPl(Team team, int id, String shortName, String colHex) {
        BotPlayer o = new BotPlayer();
        o.id = id;
        o.name = "[" + colHex + "][AI] " + shortName + "[]";
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
        String sName = NAMES[k % NAMES.length] + (k >= NAMES.length ? (k / NAMES.length + 1) : "");
        String cHex = COLORS[k % COLORS.length];
        Agent a = new Agent();
        a.slotIdx = k;
        a.shortName = sName;
        a.colHex = cHex;
        a.botPl = makeBotPl(team, id, sName, cHex);
        a.greetT = 130f + k * 60f;
        a.ambientChatT = 5000f + Mathf.random(3600f);
        agents.add(a);

        NewModePvp.St st = NewModePvp.bind(a.botPl);
        if (st.pi == 0) {
            int[] pilots = {NewModePvp.PILOT_XIKI, NewModePvp.PILOT_XAKY, NewModePvp.PILOT_HIST, NewModePvp.PILOT_VANHAU, NewModePvp.PILOT_XAKYNEO};
            st.pi = pilots[Mathf.random(pilots.length - 1)];
            if (st.pi == NewModePvp.PILOT_XAKYNEO) NewModePvp.xakyNeoStart.put(a.botPl.id, Time.time);
        }
        a.lastAnnouncedLv = st.lv;
        NewModePvp.changed();
        if (agents.size() == 1 && !NewModePvp.bvbOn()) {
            NewModePvp.toastAll("[#" + team.color + "]■[] " + tr("[cyan]A.I " + sName + " đã vào trận! Nhắn tin ở khung chat để tám chuyện cùng nhau nha.",
                "[cyan]AI " + sName + " joined the match! Type in chat anytime to hang out."), 4f);
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
        // Bot vs Bot: spawn A.I cho cả 2 phe (hoặc tất cả phe PvP có lõi)
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
        // Chơi 1 mình: tạo 1 A.I (hoặc 2 A.I nếu người chơi đã đồng ý kèo 1 vs 2)
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
        // Ghi nhận trận đấu vào ký ức thiết bị khi có player thật chơi cùng A.I
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

    // ------------------------------------------------------------------ Hỗ trợ người chơi AFK (> 1 phút)

    static void markPlayerActive(Player pl) {
        if (pl == null) return;
        AfkTrack trk = afkMap.get(pl.id);
        if (trk != null) {
            if (trk.assisting) {
                trk.assisting = false;
                sayCopilot(tr("Chào mừng " + cleanName(pl) + " quay lại! Trả tay lái cho bạn đó, chiến tiếp thôiii!",
                    "Welcome back " + cleanName(pl) + "! Handing controls back to you, let's cook!"));
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
            boolean playerReclaimed = t.assisting && (pl.shooting || (u.vel.len2() > 0.2f && pl.mouseX != 0 && Angles.angleDist(u.angleTo(pl.mouseX, pl.mouseY), u.rotation) > 25f && pl.boosting));
            if (manualMove) {
                t.idleT = 0f;
                t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
            } else if (!t.assisting) {
                t.idleT += Time.delta;
                if (t.idleT >= AFK_LIMIT_TICKS) {
                    t.assisting = true;
                    mem.afkHelped++;
                    saveMemory();
                    sayCopilot(tr("Ê " + cleanName(pl) + " AFK hơn 1 phút rồi kìa! Để A.I Trợ Lý cầm lái farm & thủ nhà giúp bạn tới khi quay lại nha!",
                        "Yo " + cleanName(pl) + " has been AFK for over 1 min! AI Copilot taking the wheel to farm & defend until you're back!"));
                }
            } else {
                // Đang hỗ trợ AFK: nếu người chơi di chuyển tay hoặc bấm bắn -> trả lại điều khiển ngay
                if (pl.shooting || playerReclaimed) {
                    t.assisting = false;
                    t.idleT = 0f;
                    t.lastX = u.x; t.lastY = u.y; t.lastRot = u.rotation;
                    sayCopilot(tr("Chào mừng " + cleanName(pl) + " quay lại! Trả tay lái cho bạn đó, chiến tiếp thôiii!",
                        "Welcome back " + cleanName(pl) + "! Controls are all yours, let's go!"));
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
            moveToward(u, core.x, core.y, HOME_R * 0.7f, false);
            aimAndShoot(u, tgt);
            return;
        }
        if (tgt != null) {
            float wantR = Math.max(90f, Math.min(240f, u.range() * 0.78f));
            float dst = Mathf.dst(u.x, u.y, tgt.x, tgt.y);
            float ang = Angles.angle(u.x, u.y, tgt.x, tgt.y);
            if (dst > wantR * 1.15f) moveToward(u, tgt.x, tgt.y, wantR * 0.85f, false);
            else if (dst < wantR * 0.65f) moveToward(u, u.x + Angles.trnsx(ang + 180f, 70f), u.y + Angles.trnsy(ang + 180f, 70f), 8f, false);
            else moveToward(u, u.x + Angles.trnsx(ang + t.strafeDir * 90f, 70f), u.y + Angles.trnsy(ang + t.strafeDir * 90f, 70f), 8f, false);
            aimAndShoot(u, tgt);
        } else {
            float pAng = (Time.time * 0.2f) % 360f;
            moveToward(u, core.x + Angles.trnsx(pAng, NewModePvp.TERRITORY_RADIUS * 0.5f), core.y + Angles.trnsy(pAng, NewModePvp.TERRITORY_RADIUS * 0.5f), 40f, false);
            aimAndShoot(u, null);
        }
    }

    static void sayCopilot(String msg) {
        if (Vars.net.client() || msg == null) return;
        try { Call.sendMessage("[#5ce67e][AI Trợ Lý][]: [white]" + msg); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ Đề nghị kèo 1 vs 2 khi A.I thua thiệt

    static void tick1v2OfferCheck() {
        if (pending1v2) {
            offer1v2T -= Time.delta;
            if (offer1v2T <= 0f) {
                pending1v2 = false;
                if (offer1v2Agent != null && agents.contains(offer1v2Agent)) {
                    queueSay(offer1v2Agent, tr("Không thấy bạn rep nên mình chốt tiếp kèo 1v1 luôn nha!",
                        "No reply so I'll keep it 1v1! Let's keep rolling!"), 20f);
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
        offer1v2T = 5400f; // chờ phản hồi 90 giây
        offer1v2Agent = a;
        String pn = cleanName(human);
        say(a, pick(
            tr("Trời ơi " + pn + " out trình mình quá rồi, đánh khét lẹt luôn á! Bạn có muốn thử kèo [gold]1 vs 2[] không? (Mình gọi thêm 1 A.I nữa vào phe mình!) Nhắn [green]đồng ý / ok / yes[] hoặc [scarlet]không / thôi / no[] nha!",
               "Bro " + pn + " you're totally cooking me rn! Wanna make it a [gold]1 vs 2[]? (I'll call 1 more AI onto my team!) Type [green]yes / ok[] or [scarlet]no[]!"),
            tr("Gánh hết nổi rồi " + pn + " ơi, bạn mạnh vãi chưởng! Chơi kèo [gold]1 vs 2[] cho kịch tính không? Nhắn [green]ok / uk / đồng ý / yes[] để mình gọi thêm đồng đội A.I, hoặc [scarlet]không / thôi / no[] nha!",
               "I'm getting diffed hard " + pn + "! Up for a [gold]1 vs 2[] challenge? Say [green]yes / ok[] so I can summon an AI teammate, or [scarlet]no[]!")
        ));
    }

    // ------------------------------------------------------------------ Hội thoại giữa 2 phe A.I (Bot vs Bot)

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
        int topic = Mathf.random(3);
        if (topic == 0) {
            say(a, tr("Ê " + b.shortName + ", chuẩn bị tinh thần chưa? Pha này mình đẩy thẳng lõi luôn đó!",
                "Yo " + b.shortName + ", ready? Pushing straight for your core this time!"));
            queueSay(b, tr("Cứ qua đây " + a.shortName + ", dàn pháo nhà mình đón tiếp nhiệt tình luôn nha!",
                "Pull up then " + a.shortName + ", my turrets are waiting for you!"), 75f);
        } else if (topic == 1) {
            say(a, tr("Nay farm đỏ quá trời, đồ rơi liên tục luôn " + b.shortName + " ơi!",
                "RNG is blessed today " + b.shortName + ", drops keep coming!"));
            queueSay(b, tr("Xạo vừa thôi bro, lát combat xem ai gánh ai nha!",
                "Cap! Let's see who wins the next teamfight bro!"), 80f);
        } else if (topic == 2) {
            say(a, tr("Kèo Bot vs Bot này căng đét luôn á, không biết player đang xem có cổ vũ ai không nhỉ?",
                "This Bot vs Bot match is intense, wonder who the spectator is rooting for?"));
            queueSay(b, tr("Chắc chắn cổ vũ phe mình rồi, đánh đẹp mắt thế này cơ mà!",
                "Definitely rooting for my squad, look at these clean plays!"), 85f);
        } else {
            say(a, tr("Khoan đã " + b.shortName + ", cho mình farm thêm 1 cấp nữa rồi solo công bằng nha!",
                "Hold up " + b.shortName + ", lemme grab 1 more level then fair 1v1!"));
            queueSay(b, tr("Không có mùa xuân đó đâu bro, chốt đơn luôn!",
                "No chance bro, it's on sight!"), 70f);
        }
    }

    // ------------------------------------------------------------------ Vòng lặp từng A.I

    static void tickAgent(Agent a) {
        Player pl = a.botPl;
        Team tm = pl.team();
        if (tm == null || tm.cores().size == 0) return;
        CoreBlock.CoreBuild core = tm.cores().first();
        NewModePvp.St st = NewModePvp.bind(pl);

        // Lời chào đầu trận (tích hợp ký ức các trận trước!)
        if (!a.greeted) {
            a.greetT -= Time.delta;
            if (a.greetT <= 0f) {
                a.greeted = true;
                if (!NewModePvp.bvbOn()) {
                    Player hp = firstHumanPlayer();
                    String pn = hp != null ? cleanName(hp) : (mem.lastPlayer.isEmpty() ? "bro" : mem.lastPlayer);
                    say(a, buildMemoryGreeting(a, pn));
                } else if (a.slotIdx == 0) {
                    say(a, tr("Chào mọi người! Kèo Bot vs Bot bắt đầu rồi nha, để tụi mình diễn cho bạn xem!",
                        "Hey everyone! Bot vs Bot is live, sit back and enjoy the show!"));
                }
            }
        }

        // Thông báo khi lên các mốc cấp 5, 10, 15...
        if (st.lv >= a.lastAnnouncedLv + 5) {
            a.lastAnnouncedLv = st.lv;
            queueSay(a, pick(
                tr("Ồ mình vừa lên cấp " + st.lv + " rồi nè! Trận này bắt đầu nóng máy rồi đó!",
                   "Just hit Lv " + st.lv + "! Things are heating up now!"),
                tr("Cấp " + st.lv + " chốt đơn! Sức mạnh tăng thấy rõ luôn bro!",
                   "Lv " + st.lv + " locked in! Power spike feels real bro!")
            ), 50f);
        }

        // Hồi sinh unit nếu chết
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

        // Thu nhập điểm phụ nhẹ
        a.incomeT += Time.delta;
        if (a.incomeT >= 3200f) {
            a.incomeT = 0f;
            st.pts += 1;
            NewModePvp.changed();
        }

        // Cảnh báo lõi bị đánh
        if (a.lastCoreHp > 0f && core.health < a.lastCoreHp - 10f && a.coreAlertCd <= 0f) {
            a.coreAlertCd = 1500f;
            a.mode = Mode.DEFEND;
            queueSay(a, pick(
                tr("Ê khoan! Ai đang đập lõi mình vậy?? Về thủ gấp!", "Wait up! Who's hitting my core?? Defending ASAP!"),
                tr("Báo động đỏ! Đừng đụng vào nhà mình chứ bro!!", "Red alert! Hands off my base bro!!")
            ), 25f);
        }
        a.lastCoreHp = core.health;
        if (a.coreAlertCd > 0f) a.coreAlertCd -= Time.delta;
        if (a.hitT > 0f) a.hitT -= Time.delta;

        // Trò chuyện chill khi trận đấu yên ắng
        a.ambientChatT -= Time.delta;
        if (a.ambientChatT <= 0f && !NewModePvp.bvbOn()) {
            a.ambientChatT = 6600f + Mathf.random(4800f);
            if (Groups.player.size() > 0 && globalChatCd <= 0f) {
                queueSay(a, pick(
                    tr("Bạn farm ổn áp không bro? Mình đang build đồ cháy lắm nha!", "Farming going smooth bro? My build is looking kinda fire!"),
                    tr("Mẹo nhỏ: hạ quái trong lãnh thổ để cày cấp Pilot lên 10 cực ngon luôn đó!", "Pro tip: kill mobs inside territory to level your Pilot to Lv 10 fast!"),
                    tr("Trận này chill phết! Muốn khô máu thì cứ nhắn 'solo' trong chat nha.", "Vibes are chill! Type 'solo' in chat whenever you wanna brawl."),
                    tr("Mình đang tin dùng " + NewModePvp.pilotNameById(st.pi) + ", còn bạn pick pilot nào thế?", "I'm rocking " + NewModePvp.pilotNameById(st.pi) + ", who's your pilot?")
                ), 40f);
            }
        }

        // Mua sắm / nâng cấp / xây phòng thủ
        a.shopT -= Time.delta;
        if (a.shopT <= 0f) {
            a.shopT = 135f;
            doShopping(a, st, u, core);
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

        executeMovementAndCombat(a, u, core);
    }

    static String buildMemoryGreeting(Agent a, String pn) {
        if (mem.matches <= 1) {
            return pick(
                tr("Helu " + pn + "! Mình là " + a.shortName + ", A.I chơi cùng bạn hôm nay nè! Cứ nhắn chat tán gẫu bất cứ lúc nào nha.",
                   "Yo " + pn + "! I'm " + a.shortName + ", your AI buddy today! Drop a message in chat anytime."),
                tr("Chào " + pn + " nha! Chơi 1 mình buồn lắm nên để " + a.shortName + " vào quẩy chung cho vui nhé!",
                   "Hey " + pn + "! Solo gets quiet, so " + a.shortName + " hopped in to vibe and battle with you!")
            );
        }
        if (mem.playerKills > mem.aiKills + 3) {
            return tr("Ồ gặp lại " + pn + " ở trận thứ " + mem.matches + " rồi! Mấy trận trước bạn hạ mình tận " + mem.playerKills + " lần, nay mình phục thù nha!",
                "Back again " + pn + " for match #" + mem.matches + "! You've got " + mem.playerKills + " kills on me so far, time for my comeback!");
        }
        if (mem.accepted1v2 > 0 && Mathf.chance(0.45)) {
            return tr("Chào người quen " + pn + " (trận #" + mem.matches + ")! Nhớ hồi trước bạn chấp kèo 1v2 khét lẹt luôn á, nay chiến tiếp nha!",
                "Welcome back " + pn + " (match #" + mem.matches + ")! Still remember you taking on that 1v2 like a boss, let's run it back!");
        }
        if ("solo".equals(mem.favStyle)) {
            return tr("Helu " + pn + "! Trận thứ " + mem.matches + " rồi nè, biết gu bạn thích khô máu nên nay mình cũng đánh nhiệt luôn nha!",
                "Yo " + pn + "! Match #" + mem.matches + " — I remember you love aggressive 1v1s, let's bring the heat!");
        }
        return tr("Gặp lại " + pn + " ở trận thứ " + mem.matches + " rồi nè! Mình nhớ bạn đó, chúc trận này lụm toàn đồ thần thoại nha!",
            "Good to see you again " + pn + " on match #" + mem.matches + "! Wishing you mythic drops this game!");
    }

    static void spawnAgentUnit(Agent a, CoreBlock.CoreBuild core, NewModePvp.St st) {
        try {
            Unit nu = NewModePvp.tierType(NewModePvp.activeTier(st)).create(a.botPl.team());
            float ang = Mathf.random(360f);
            nu.set(core.x + Angles.trnsx(ang, 48f), core.y + Angles.trnsy(ang, 48f));
            nu.add();
            a.botPl.unit(nu);
            a.lastX = nu.x;
            a.lastY = nu.y;
            a.stuckT = 0f;
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ Ra quyết định thông minh (FSM)

    static void updateDecision(Agent a, Unit u, CoreBlock.CoreBuild core, NewModePvp.St st) {
        float hpRatio = u.maxHealth > 0f ? u.health / u.maxHealth : 1f;

        if (a.mode == Mode.RETREAT) {
            if (hpRatio >= HEALED_HP) a.mode = Mode.FARM;
            else return;
        } else if (hpRatio < RETREAT_HP) {
            a.mode = Mode.RETREAT;
            a.target = null;
            return;
        }

        // Ưu tiên 1: Thủ lãnh thổ / lõi
        Unit invader = findSmartTarget(u, core, NewModePvp.TERRITORY_RADIUS + 140f, false);
        if (invader != null) {
            a.mode = Mode.DEFEND;
            a.target = invader;
            return;
        }

        // Ưu tiên 2: Săn đối thủ nếu mạnh hơn, bị đánh, đang BvB hoặc player gọi kèo solo
        Unit enemyPl = findNearestEnemyPlayer(u);
        if (enemyPl != null) {
            float eHpPct = enemyPl.maxHealth > 0f ? enemyPl.health / enemyPl.maxHealth : 1f;
            boolean finishKill = eHpPct < 0.45f && Mathf.dst(u.x, u.y, enemyPl.x, enemyPl.y) <= AGGRO_R * 1.15f;
            boolean wantHunt = a.hitT > 0f || a.aggressiveOrder || finishKill
                || (NewModePvp.bvbOn() && st.lv >= 4)
                || (!a.defensiveOrder && st.lv >= 6 && Mathf.dst(u.x, u.y, enemyPl.x, enemyPl.y) <= AGGRO_R);
            if (wantHunt) {
                a.mode = Mode.HUNT;
                a.target = enemyPl;
                return;
            }
        }

        // Ưu tiên 3: Về lõi tiêu điểm nếu tích nhiều
        if (st.pts >= 3 && Mathf.dst(u.x, u.y, core.x, core.y) > 220f) {
            a.mode = Mode.SHOP;
            a.target = null;
            return;
        }

        // Ưu tiên 4: Farm quái đợt (ưu tiên trong lãnh thổ để cày EXP Pilot)
        Unit waveMob = findSmartTarget(u, core, NewModePvp.TERRITORY_RADIUS + 220f, true);
        if (waveMob == null) waveMob = findSmartTarget(u, u, LEASH_R * 0.7f, true);
        a.mode = Mode.FARM;
        a.target = waveMob;
    }

    /** Chọn mục tiêu thông minh: ưu tiên mục tiêu thấp máu dễ kết liễu + gần. */
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

    // ------------------------------------------------------------------ Di chuyển & Giao tranh

    static void executeMovementAndCombat(Agent a, Unit u, CoreBlock.CoreBuild core) {
        a.strafeFlipT -= Time.delta;
        if (a.strafeFlipT <= 0f) {
            a.strafeFlipT = 65f + Mathf.random(70f);
            a.strafeDir = -a.strafeDir;
        }
        if (Mathf.dst2(u.x, u.y, a.lastX, a.lastY) < 4f) a.stuckT += Time.delta;
        else { a.stuckT = 0f; a.lastX = u.x; a.lastY = u.y; }

        Unit tgt = a.target;
        if (tgt != null && (tgt.dead || !tgt.isValid())) { tgt = null; a.target = null; }

        float desiredRange = Math.max(95f, Math.min(265f, u.range() * 0.80f));

        if (a.mode == Mode.RETREAT || a.mode == Mode.SHOP) {
            moveToward(u, core.x, core.y, HOME_R * 0.7f, a.stuckT > 40f);
            if (a.mode == Mode.SHOP && Mathf.dst(u.x, u.y, core.x, core.y) <= HOME_R + 65f) {
                doShopping(a, NewModePvp.bind(a.botPl), u, core);
                a.mode = Mode.FARM;
            }
            aimAndShoot(u, tgt != null ? tgt : findSmartTarget(u, u, u.range(), false));
            return;
        }

        if (tgt != null) {
            float dst = Mathf.dst(u.x, u.y, tgt.x, tgt.y);
            float angTo = Angles.angle(u.x, u.y, tgt.x, tgt.y);
            if (dst > desiredRange * 1.15f) {
                moveToward(u, tgt.x, tgt.y, desiredRange * 0.85f, a.stuckT > 40f);
            } else if (dst < desiredRange * 0.65f) {
                float backAng = angTo + 180f + a.strafeDir * 35f;
                moveToward(u, u.x + Angles.trnsx(backAng, 80f), u.y + Angles.trnsy(backAng, 80f), 8f, false);
            } else {
                float strafeAng = angTo + a.strafeDir * 90f;
                moveToward(u, u.x + Angles.trnsx(strafeAng, 80f), u.y + Angles.trnsy(strafeAng, 80f), 8f, false);
            }
            aimAndShoot(u, tgt);
        } else {
            float patrolAng = (Time.time * 0.18f + a.slotIdx * 72f) % 360f;
            float px = core.x + Angles.trnsx(patrolAng, NewModePvp.TERRITORY_RADIUS * 0.55f);
            float py = core.y + Angles.trnsy(patrolAng, NewModePvp.TERRITORY_RADIUS * 0.55f);
            moveToward(u, px, py, 40f, a.stuckT > 40f);
            aimAndShoot(u, null);
        }
    }

    static void moveToward(Unit u, float tx, float ty, float stopDist, boolean unstuck) {
        float dst = Mathf.dst(u.x, u.y, tx, ty);
        if (dst <= stopDist) return;
        float ang = Angles.angle(u.x, u.y, tx, ty);
        if (unstuck) ang += 75f;
        tmpVec.trns(ang, u.speed());
        u.moveAt(tmpVec);
        if (!u.isShooting()) u.lookAt(ang);
    }

    static void aimAndShoot(Unit u, Unit tgt) {
        if (tgt != null && !tgt.dead && tgt.isValid() && Mathf.dst(u.x, u.y, tgt.x, tgt.y) <= Math.max(u.range() + 45f, COMBAT_R)) {
            u.aimLook(tgt.x, tgt.y);
            u.controlWeapons(true, true);
        } else {
            u.controlWeapons(false, false);
        }
    }

    // ------------------------------------------------------------------ Mua sắm, Nâng cấp & Xây phòng thủ

    static void doShopping(Agent a, NewModePvp.St st, Unit u, CoreBlock.CoreBuild core) {
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

        try {
            NewModePvp.Defs defs = NewModePvp.droneDefs();
            int slots = NewModePvp.equipSlots(st);
            if (st.d.size() < slots && defs != null && defs.list.size() > 0) {
                NewModePvp.Def best = null;
                for (int i = 0; i < defs.list.size(); i++) {
                    NewModePvp.Def d = defs.list.get(i);
                    if (d.kind.equals("tractor") || d.kind.equals("point")) continue;
                    if (d.cost <= st.pts && (best == null || d.cost > best.cost)) best = d;
                }
                if (best != null) {
                    st.pts -= best.cost;
                    st.d.add(best.id);
                    st.p.add(0);
                    NewModePvp.changed();
                }
            }
        } catch (Throwable ignored) {}

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
            for (int i = 0; i < defs.list.size(); i++) {
                NewModePvp.Def d = defs.list.get(i);
                if (d.block == null || d.kind.equals("tractor") || d.kind.equals("point")) continue;
                if (d.cost <= st.pts && (pickDef == null || d.cost > pickDef.cost)) pickDef = d;
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

    // ------------------------------------------------------------------ Hệ thống Trò Chuyện (Gen-Z VI/EN + 1v2)

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
        globalChatCd = 75f;
        try { Call.sendMessage("[" + a.colHex + "][AI] " + a.shortName + "[]: [white]" + msg); } catch (Throwable ignored) {}
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

    /** Phản hồi tin nhắn người chơi (ưu tiên xử lý kèo 1 vs 2 trước). */
    public static void onPlayerChat(Player speaker, String rawMsg) {
        if (agents.isEmpty() || rawMsg == null) return;
        String m = rawMsg.trim().toLowerCase();
        if (m.isEmpty() || m.startsWith("/")) return;
        String pname = cleanName(speaker);
        float delay = 35f + Mathf.random(55f);

        // Xử lý xác nhận kèo 1 vs 2
        if (pending1v2) {
            Agent hostA = (offer1v2Agent != null && agents.contains(offer1v2Agent)) ? offer1v2Agent : agents.get(0);
            if (isYesReply(m)) {
                pending1v2 = false;
                extra1v2 = true;
                mem.accepted1v2++;
                saveMemory();
                Team aiTeam = hostA.botPl.team();
                say(hostA, tr("Chốt đơn kèo [gold]1 vs 2[]!! Quá cháy luôn " + pname + " ơi, gọi thêm anh em vào gánh đây!",
                    "[gold]1 vs 2[] locked in!! Respect " + pname + ", calling my duo in right now!"));
                if (aiTeam != null && aiTeam.cores().size > 0 && agents.size() < BOT_MAX) {
                    Agent ally = createAgent(aiTeam);
                    ally.greeted = true;
                    queueSay(ally, tr("Có mặt liền! Nghe đồn " + pname + " đánh khét lắm hả? Để tụi mình 2 đánh 1 xem sao nha!",
                        "Reporting in! Heard " + pname + " is cracked — let's see how you handle a 2v1!"), 55f);
                }
                return;
            }
            if (isNoReply(m)) {
                pending1v2 = false;
                queueSay(hostA, tr("Okela " + pname + "! Vậy cứ 1v1 công bằng tiếp nha, mình try-hard hết mình luôn!",
                    "All good " + pname + "! Fair 1v1 it is, I'm locking in!"), delay);
                return;
            }
        }

        Agent a = agents.get(Mathf.random(agents.size() - 1));

        if (hasAny(m, "chào", "chao", "hello", "hi", "helo", "helu", "alo", "yo", "ê ", "bot ơi", "ai ơi")) {
            queueSay(a, pick(
                tr("Helu " + pname + "! Nay đánh nhẹ tay cho mình thở với nha bro!", "Yo " + pname + "! Go easy on me today bro!"),
                tr("Có mặt liền! " + a.shortName + " nghe nè, nay làm kèo cháy phố không?", "Present! " + a.shortName + " here — ready to cook?")
            ), delay);
        } else if (hasAny(m, "solo", "1v1", "tấn công", "tan cong", "đánh nhau", "danh nhau", "ra đây", "ra day", "combat", "rush", "fight", "khô máu", "kho mau")) {
            a.aggressiveOrder = true;
            a.defensiveOrder = false;
            mem.favStyle = "solo";
            saveMemory();
            queueSay(a, pick(
                tr("Chốt kèo khô máu " + pname + "! Mình đang lao lên đây, tới công chuyện luôn!", "1v1 accepted " + pname + "! Pulling up right now, let's brawl!"),
                tr("Nghe mùi combat là mê rồi! Chuẩn bị đón chiêu nha bro!", "Say less! Heading over, don't blink bro!")
            ), delay);
        } else if (hasAny(m, "phòng thủ", "phong thu", "thủ", "def", "đợi", "doi", "chờ", "cho ", "farm", "hòa", "hoa", "chill")) {
            a.aggressiveOrder = false;
            a.defensiveOrder = true;
            mem.favStyle = "farm";
            saveMemory();
            queueSay(a, pick(
                tr("Okela! Tạm đình chiến farm chill chill lên đồ đã nha " + pname + "!", "Say less! Chill farming truce while we scale up, " + pname + "!"),
                tr("Nhất trí bro! Mình về thủ nhà cày cấp Pilot đây.", "Gotchu bro! Heading home to farm Pilot EXP.")
            ), delay);
        } else if (hasAny(m, "gg", "hay quá", "hay qua", "pro", "giỏi", "gioi", "đỉnh", "dinh", "cháy", "chay", "khét", "khet", "nice", "good", "slay", "goat")) {
            queueSay(a, pick(
                tr("Ái chà khen làm mình ngại quá trời, " + pname + " cũng khét lẹt không kém đâu!", "Stoppp you're making me blush, " + pname + " you're cracked too!"),
                tr("GG bro! Quá cháy luôn, chơi với bạn cuốn thật sự!", "GG bro! Absolute cinema, playing with you is peak!")
            ), delay);
        } else if (hasAny(m, "khó", "kho", "mạnh", "manh", "trâu", "trau", "đau", "dau", "cay", "thua", "op", "hard", "diff")) {
            queueSay(a, pick(
                tr("Bình tĩnh " + pname + " ơi! Tặng mấy món đồ dư cho Pilot là bỏ hết debuff, lật kèo dễ ợt à!", "Stay calm " + pname + "! Gift spare gear to your Pilot to strip debuffs and flip the game!"),
                tr("Chưa hết trận chưa biết mèo nào cắn mỉu nào đâu bro, cố lên!", "Never ff bro, one good fight and you're right back in it!")
            ), delay);
        } else if (hasAny(m, "buồn", "buon", "chán", "chan", "1 mình", "một mình", "mot minh", "cô đơn", "co don", "lonely", "alone")) {
            queueSay(a, pick(
                tr("Ủa có " + a.shortName + " ở đây rồi mà buồn gì nữa " + pname + "! Cứ tám chuyện thoải mái nha!", "Hey " + a.shortName + " is right here " + pname + ", no sad vibes allowed! Chat anytime!"),
                tr("Chơi một mình có A.I gánh mood luôn nè, trận này mình nhớ lưu vào ký ức thiết bị rồi đó!", "Solo with an AI buddy hits different — I'll remember this match in my memory too!")
            ), delay);
        } else if (hasAny(m, "nhớ", "nho", "ký ức", "ky uc", "memory", "trận trước", "tran truoc")) {
            queueSay(a, tr("Mình nhớ hết nha! Tụi mình chơi với nhau " + mem.matches + " trận rồi, bạn hạ mình " + mem.playerKills + " lần còn mình ăn lại " + mem.aiKills + " lần đó!",
                "I remember everything! We've played " + mem.matches + " matches together — you got " + mem.playerKills + " kills on me and I got " + mem.aiKills + " on you!"), delay);
        } else if (hasAny(m, "cảm ơn", "cam on", "thanks", "thank", "ty")) {
            queueSay(a, pick(
                tr("Không có gì đâu bro! Vui là chính nha!", "Anytime bro! All about the good vibes!"),
                tr("Chuyện nhỏ như con thỏ, chiến tiếp thôi!", "No worries at all, let's keep cooking!")
            ), delay);
        } else if (hasAny(m, "haha", "hihi", "kaka", "lol", "lmao", ":v", ":d", "xd", "bruh")) {
            queueSay(a, pick(
                tr("Haha cười xỉu luôn á trời!", "Lmao actually hilarious!"),
                tr("Đúng kiểu giải trí cực mạnh luôn :D", "Peak comedy right here :D")
            ), delay);
        } else if (globalChatCd <= 0f) {
            queueSay(a, pick(
                tr("Okela " + pname + "! Mình vẫn đang nghe bạn nói nè, vừa farm vừa tám chuyện quá chill!", "Bet " + pname + "! Listening while farming — peak chill vibes!"),
                tr("Nghe hợp lý đó " + pname + "! Bạn muốn farm tiếp hay nhắn 'solo' làm pha highlight?", "Valid take " + pname + "! Wanna keep scaling or type 'solo' for a highlight clip?"),
                tr("Hehe thú vị ghê! Mình đang để ý map lắm nha, đừng hòng đánh lén lõi mình!", "Hehe nice! Keeping my eyes on the minimap though, no sneaking my core!")
            ), delay);
        }
    }

    /** Gọi khi A.I hạ gục người chơi thật. */
    public static void onBotKilledPlayer(Player botPl, Player victim) {
        Agent a = botPl != null ? botOf(botPl.id) : null;
        if (a == null || victim == null) return;
        a.killsThisMatch++;
        mem.aiKills++;
        saveMemory();
        String vn = cleanName(victim);
        queueSay(a, pick(
            tr("Lụm mạng của " + vn + " nha! Pha đó hơi bị mượt luôn á!", "Gotcha " + vn + "! That play was kinda clean ngl!"),
            tr("Hehe ăn điểm rồi " + vn + " ơi! Hồi sinh lên làm lại kèo nha!", "Boom! Waiting for your revenge arc " + vn + "!")
        ), 35f);
    }

    /** Gọi khi người chơi thật (hoặc A.I khác trong BvB) hạ gục A.I. */
    public static void onPlayerKilledBot(Player killer, Player botPl) {
        Agent a = botPl != null ? botOf(botPl.id) : null;
        if (a == null || killer == null) return;
        a.killerId = killer.id;
        a.deathsThisMatch++;
        if (isBotPl(killer)) {
            Agent ka = botOf(killer.id);
            if (ka != null) {
                ka.killsThisMatch++;
                queueSay(ka, tr("Ez mạng " + a.shortName + " nha! Out trình nhẹ nhàng!", "Ez takedown on " + a.shortName + "! Clean diff!"), 30f);
                queueSay(a, tr("Cay vãi " + ka.shortName + " ơi, đợi mình hồi sinh trả đủ nha!", "No way " + ka.shortName + ", wait till I respawn!"), 80f);
            }
            return;
        }
        mem.playerKills++;
        saveMemory();
        String kn = cleanName(killer);
        queueSay(a, pick(
            tr("Á đau quá trời!! " + kn + " đánh khét lẹt vậy bro, đợi mình hồi sinh phục thù!", "Ouch!! " + kn + " you're actually cracked, wait for my comeback!"),
            tr("Bị " + kn + " cook luôn rồi... Đỉnh thực sự đó bro!", "Got cooked by " + kn + "... Respect, that was clean!")
        ), 35f);
    }

    static boolean hasAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
