package newex.content;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.ui.Fonts;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;
import newex.NexNet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cầu nối TikTok LIVE <-> NeoWex Mindustry:
 * 1) Đọc trực tiếp hàng đợi lệnh từ bridge.py (%APPDATA%/Mindustry/newex-tiktok-queue.jsonl)
 *    -> không cần focus cửa sổ game, hỗ trợ tên tiếng Việt UTF-8 đầy đủ!
 * 2) Đồng thời đọc %APPDATA%/Mindustry/newex-tiktok-donor.json khi bridge.py dùng chế độ bấm phím (tap/hold)
 *    -> tự động gắn tên viewer lên unit vừa được spawn!
 * 3) Vẽ bảng tên viewer nổi bật trên đầu mỗi unit (đồng bộ cả multiplayer) + thông báo tặng quà.
 */
public final class TikTokBridge {
    private TikTokBridge() {}

    static final String QUEUE_FILE = "newex-tiktok-queue.jsonl";
    static final String DONOR_FILE = "newex-tiktok-donor.json";
    static final String TT_NAME_KIND = "newex-tt-name";

    static final class ViewerTag {
        String viewer;
        String gift;
        int diamonds;
        String display;
        float width;
    }

    private static final HashMap<Integer, ViewerTag> unitTags = new HashMap<>();
    private static final GlyphLayout tmpLayout = new GlyphLayout();
    private static final Color tmpCol = new Color();

    private static boolean initialized = false;
    private static float pollTimer = 0f;
    private static float pruneTimer = 0f;
    private static long lastDonorUsedTs = 0L;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(WorldLoadEvent.class, e -> unitTags.clear());

        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    Vars.netClient.addPacketHandler(TT_NAME_KIND, TikTokBridge::onNamePacket);
                } catch (Throwable err) {
                    Log.err("[TikTokBridge] packet handler failed", err);
                }
            });
            Events.run(Trigger.draw, TikTokBridge::drawViewerTags);
        }

        Events.run(Trigger.update, TikTokBridge::tick);
    }

    private static void tick() {
        if (Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;

        // Dọn nhãn của các unit đã chết định kỳ mỗi 2 giây
        pruneTimer -= Time.delta;
        if (pruneTimer <= 0f) {
            pruneTimer = 120f;
            if (!unitTags.isEmpty()) {
                Iterator<Map.Entry<Integer, ViewerTag>> it = unitTags.entrySet().iterator();
                while (it.hasNext()) {
                    Unit u = Groups.unit.getByID(it.next().getKey());
                    if (u == null || !u.isValid() || u.dead) it.remove();
                }
            }
        }

        // Chỉ Host / Singleplayer đọc file hàng đợi lệnh từ bridge.py (~6 lần/giây, 0ms khi file rỗng)
        if (Vars.net.client()) return;
        pollTimer -= Time.delta;
        if (pollTimer > 0f) return;
        pollTimer = 10f;

        pollQueueFile();
    }

    private static void pollQueueFile() {
        try {
            if (Vars.dataDirectory == null) return;
            Fi f = Vars.dataDirectory.child(QUEUE_FILE);
            if (!f.exists() || f.length() <= 0L) return;

            String content = f.readString("UTF-8");
            // Xóa nội dung file ngay sau khi đọc để không chạy lặp
            try { f.writeString("", false, "UTF-8"); } catch (Throwable ignored) {}
            if (content == null || content.trim().isEmpty()) return;

            String[] lines = content.split("\\r?\\n");
            int processed = 0;
            for (String line : lines) {
                if (processed >= 25) break;
                String s = line.trim();
                if (s.isEmpty() || !s.startsWith("{")) continue;
                try {
                    JsonValue j = new JsonReader().parse(s);
                    executeCommand(j);
                    processed++;
                } catch (Throwable err) {
                    Log.err("[TikTokBridge] bad queue line: " + s, err);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Thực thi 1 lệnh trực tiếp gửi từ bridge.py. */
    private static void executeCommand(JsonValue j) {
        if (j == null || !j.isObject()) return;
        String cmd = j.getString("cmd", "").trim().toLowerCase();
        String viewer = sanitizeName(j.getString("viewer", "Viewer"));
        String gift = sanitizeName(j.getString("gift", ""));
        int diamonds = Math.max(0, j.getInt("diamonds", 0));
        int count = Mathf.clamp(j.getInt("count", 1), 1, 20);
        String unitName = j.getString("unit", "").trim();
        String teamOpt = j.getString("team", "").trim().toLowerCase();

        if ("reset_points".equals(cmd) || "reset".equals(cmd)) {
            if (PvpDomination.isActive()) {
                PvpDomination.resetAllCapturePoints();
                announceGift(viewer, gift, diamonds, NewModePvp.tr("Reset toàn bộ cứ điểm!", "Reset all capture points!"));
            }
            return;
        }

        if ("meteor".equals(cmd)) {
            for (int i = 0; i < Math.min(count, 5); i++) {
                LoadoutSelection.triggerRandomMeteor(null);
            }
            announceGift(viewer, gift, diamonds, NewModePvp.tr("Gọi thiên thạch x" + Math.min(count, 5) + "!", "Meteor x" + Math.min(count, 5) + "!"));
            return;
        }

        if ("wave".equals(cmd)) {
            LoadoutSelection.triggerMultipleWaves(Math.min(count, 10), null);
            announceGift(viewer, gift, diamonds, NewModePvp.tr("Gọi " + Math.min(count, 10) + " đợt quái!", "Spawned " + Math.min(count, 10) + " waves!"));
            return;
        }

        if ("spawn_pvp_yellow".equals(cmd) || "spawn_pvp_red".equals(cmd)) {
            boolean otherSide = "spawn_pvp_red".equals(cmd);
            Unit lastSpawned = null;
            for (int i = 0; i < count; i++) {
                UnitType pvpUt = pickRandomPvpPlayerUnit();
                Unit u = spawnTikTokUnit(otherSide, pvpUt);
                if (u != null) {
                    tagUnit(u, viewer, gift, diamonds);
                    lastSpawned = u;
                }
            }
            if (lastSpawned != null) {
                String uLbl = lastSpawned.type != null ? lastSpawned.type.localizedName : "PvP Unit";
                String teamTag = "[#" + lastSpawned.team.color + "]" + lastSpawned.team.localized() + "[]";
                String suffix = count > 1 ? (" x" + count) : "";
                announceGift(viewer, gift, diamonds, teamTag + " → [gold]" + uLbl + suffix + "[]");
            }
            return;
        }

        if ("spawn_yellow".equals(cmd) || "spawn_other".equals(cmd) || "spawn_red".equals(cmd) || "spawn_enemy".equals(cmd)
            || "spawn_ally".equals(cmd) || "spawn_unit".equals(cmd) || "mega".equals(cmd)) {

            UnitType forced = null;
            if ("mega".equals(cmd)) {
                forced = Vars.content.getByName(ContentType.unit, "mega");
            } else if (!unitName.isEmpty()) {
                forced = findUnitByName(unitName);
            }

            boolean otherSide = "spawn_other".equals(cmd) || "spawn_red".equals(cmd) || "spawn_enemy".equals(cmd)
                || "other".equals(teamOpt) || "red".equals(teamOpt) || "enemy".equals(teamOpt);

            Unit lastSpawned = null;
            for (int i = 0; i < count; i++) {
                Unit u = spawnTikTokUnit(otherSide, forced);
                if (u != null) {
                    tagUnit(u, viewer, gift, diamonds);
                    lastSpawned = u;
                }
            }
            if (lastSpawned != null) {
                String uLbl = lastSpawned.type != null ? lastSpawned.type.localizedName : "Unit";
                String teamTag = "[#" + lastSpawned.team.color + "]" + lastSpawned.team.localized() + "[]";
                String suffix = count > 1 ? (" x" + count) : "";
                announceGift(viewer, gift, diamonds, teamTag + " → [accent]" + uLbl + suffix + "[]");
            }
        }
    }

    private static UnitType findUnitByName(String name) {
        UnitType ut = Vars.content.getByName(ContentType.unit, name);
        if (ut == null) ut = Vars.content.getByName(ContentType.unit, "newex-" + name);
        if (ut == null) {
            String low = name.toLowerCase();
            for (UnitType cand : Vars.content.units()) {
                if (cand != null && cand.name != null && (cand.name.equalsIgnoreCase(low) || cand.name.toLowerCase().endsWith("-" + low))) {
                    return cand;
                }
            }
        }
        return ut;
    }

    private static Unit spawnTikTokUnit(boolean otherOrEnemy, UnitType forced) {
        // 1) Trong PvP Chiếm Cứ: spawn tại Lõi phe tương ứng & dùng AI né tường đi chiếm cứ (vô hạn!)
        if (PvpDomination.isActive()) {
            return PvpDomination.spawnSupportUnit(otherOrEnemy, forced);
        }

        // 2) Ở các bản đồ khác (Campaign / Survival / PvP thường / Custom):
        if (Vars.world == null || Vars.state == null || Vars.state.rules == null) return null;
        UnitType ut = forced != null ? forced : pickRandomValidUnit();
        if (ut == null) return null;

        Team targetTeam = otherOrEnemy
            ? (Vars.state.rules.waveTeam != null ? Vars.state.rules.waveTeam : Team.crux)
            : (Vars.state.rules.defaultTeam != null ? Vars.state.rules.defaultTeam : Team.sharded);

        if (Vars.state.rules.unitCap < 999999) {
            Vars.state.rules.unitCapVariable = false;
            Vars.state.rules.unitCap = 999999;
        }

        float sx, sy;
        CoreBlock.CoreBuild core = targetTeam.core();
        if (core != null && core.isValid()) {
            float ang = Mathf.random(360f);
            float rad = core.block.size * Vars.tilesize * 0.65f + ut.hitSize * 0.6f + 8f;
            sx = core.x + Mathf.cosDeg(ang) * rad;
            sy = core.y + Mathf.sinDeg(ang) * rad;
        } else {
            Tile t = findOpenTile();
            if (t == null) return null;
            sx = t.worldx();
            sy = t.worldy();
        }

        Unit u = ut.create(targetTeam);
        u.set(sx, sy);
        u.add();
        Fx.spawn.at(sx, sy);
        try { NexNet.fx(Fx.spawn, sx, sy); } catch (Throwable ignored) {}
        return u;
    }

    private static UnitType pickRandomPvpPlayerUnit() {
        Seq<UnitType> pool = new Seq<>();
        for (int lv = 1; lv <= NewModePvp.TIER_MAX; lv++) {
            UnitType ut = NewModePvp.tierType(lv);
            if (ut != null && !pool.contains(ut)) pool.add(ut);
        }
        return pool.isEmpty() ? pickRandomValidUnit() : pool.random();
    }

    private static UnitType pickRandomValidUnit() {
        Seq<UnitType> pool = new Seq<>();
        for (UnitType ut : Vars.content.units()) {
            if (PvpDomination.isValidRandomSpawnUnit(ut)) pool.add(ut);
        }
        if (pool.isEmpty()) return null;
        return pool.random();
    }

    private static Tile findOpenTile() {
        int w = Vars.world.width(), h = Vars.world.height();
        for (int tries = 0; tries < 80; tries++) {
            int tx = Mathf.random(2, Math.max(2, w - 3));
            int ty = Mathf.random(2, Math.max(2, h - 3));
            Tile t = Vars.world.tile(tx, ty);
            if (t != null && !t.solid() && t.build == null && !t.floor().isLiquid) return t;
        }
        return null;
    }

    private static void announceGift(String viewer, String gift, int diamonds, String result) {
        String giftPart = gift.isEmpty() ? "" : (" [#ffd166]" + gift + (diamonds > 0 ? " (" + diamonds + " xu)" : "") + "[]");
        String msg = "[#ff4d6d]♦ TikTok[] [white]" + viewer + "[]" + giftPart + " • " + result;
        try {
            NewModePvp.toastAll(msg, 3.2f);
        } catch (Throwable ignored) {
            if (!Vars.headless && Vars.ui != null) Vars.ui.showInfoToast(msg, 3.2f);
        }
    }

    // ===== Hỗ trợ chế độ cũ (bridge.py gửi phím bấm): tự động đọc file donor gần nhất (< 3.5 giây) để gắn tên viewer =====
    public static void tagIfRecentDonor(Unit u) {
        if (u == null || Vars.net.client() || Vars.dataDirectory == null) return;
        try {
            Fi f = Vars.dataDirectory.child(DONOR_FILE);
            if (!f.exists() || f.length() <= 0L) return;
            JsonValue j = new JsonReader().parse(f.readString("UTF-8"));
            if (j == null || !j.isObject()) return;
            long ts = j.getLong("ts", 0L);
            long now = System.currentTimeMillis();
            if (ts <= 0L || Math.abs(now - ts) > 3500L) return;
            String viewer = sanitizeName(j.getString("viewer", ""));
            if (viewer.isEmpty()) return;
            String gift = sanitizeName(j.getString("gift", ""));
            int diamonds = Math.max(0, j.getInt("diamonds", 0));
            lastDonorUsedTs = ts;
            tagUnit(u, viewer, gift, diamonds);
        } catch (Throwable ignored) {}
    }

    /** Gắn tên viewer lên unit & đồng bộ cho toàn bộ client trong phòng. */
    public static void tagUnit(Unit u, String viewer, String gift, int diamonds) {
        if (u == null || viewer == null || viewer.trim().isEmpty()) return;
        String cleanViewer = sanitizeName(viewer);
        String cleanGift = sanitizeName(gift == null ? "" : gift);
        putLocalTag(u.id, cleanViewer, cleanGift, diamonds);

        if (Vars.net.server()) {
            try {
                String safeV = cleanViewer.replace("|", " ");
                String safeG = cleanGift.replace("|", " ");
                Call.clientPacketReliable(TT_NAME_KIND, u.id + "|" + diamonds + "|" + safeV + "|" + safeG);
            } catch (Throwable ignored) {}
        }
    }

    private static void putLocalTag(int uid, String viewer, String gift, int diamonds) {
        ViewerTag tag = new ViewerTag();
        tag.viewer = viewer;
        tag.gift = gift;
        tag.diamonds = diamonds;
        tag.display = diamonds >= 10
            ? ("★ " + viewer + " (" + diamonds + " xu)")
            : viewer;
        tag.width = -1f;
        unitTags.put(uid, tag);
    }

    private static void onNamePacket(String raw) {
        if (!Vars.net.client() || raw == null) return;
        try {
            String[] p = raw.split("\\|", -1);
            if (p.length >= 3) {
                int uid = Integer.parseInt(p[0]);
                int dm = Integer.parseInt(p[1]);
                String viewer = p[2];
                String gift = p.length >= 4 ? p[3] : "";
                putLocalTag(uid, viewer, gift, dm);
            }
        } catch (Throwable ignored) {}
    }

    private static String sanitizeName(String s) {
        if (s == null) return "";
        String clean = s.replaceAll("\\[[^\\]]*\\]", "").replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() > 22) clean = clean.substring(0, 21) + "…";
        return clean;
    }

    /** Vẽ bảng tên viewer nổi bật bay theo từng unit (gọn đẹp, tối ưu cho live TikTok). */
    private static void drawViewerTags() {
        if (Vars.headless || unitTags.isEmpty() || Vars.state == null || !Vars.state.isGame()) return;

        Draw.draw(Layer.flyingUnit + 2.2f, () -> {
            float scale = 0.28f;
            Fonts.outline.getData().setScale(scale);

            for (Map.Entry<Integer, ViewerTag> e : unitTags.entrySet()) {
                Unit u = Groups.unit.getByID(e.getKey());
                if (u == null || !u.isValid() || u.dead) continue;
                if (!NewModePvp.camVisible(u.x, u.y, u.hitSize + 64f)) continue;

                ViewerTag tag = e.getValue();
                if (tag.width < 0f) {
                    tmpLayout.setText(Fonts.outline, tag.display);
                    tag.width = tmpLayout.width;
                }

                float tx = u.x;
                float ty = u.y + u.hitSize * 0.62f + 13f;
                float bw = tag.width + 10f;
                float bh = 11.5f;

                // Viền đổi màu theo giá trị quà (100+ xu cầu vồng VIP, 10+ xu vàng gold, thường theo màu phe)
                if (tag.diamonds >= 100) {
                    tmpCol.fromHsv((Time.time * 2.5f) % 360f, 0.72f, 1f);
                } else if (tag.diamonds >= 10) {
                    tmpCol.set(Color.gold);
                } else {
                    tmpCol.set(u.team != null ? u.team.color : Color.white);
                }

                // Khung nền kính tối + viền sáng
                Draw.color(0.03f, 0.04f, 0.08f, 0.70f);
                Fill.rect(tx, ty, bw, bh);
                Lines.stroke(0.9f);
                Draw.color(tmpCol, 0.92f);
                Lines.rect(tx - bw * 0.5f, ty - bh * 0.5f, bw, bh);

                // Tên viewer
                Fonts.outline.setColor(tag.diamonds >= 10 ? tmpCol : Color.white);
                Fonts.outline.draw(tag.display, tx, ty + 3.6f, Align.center);
            }

            Fonts.outline.setColor(Color.white);
            Fonts.outline.getData().setScale(1f);
            Draw.reset();
        });
    }
}
