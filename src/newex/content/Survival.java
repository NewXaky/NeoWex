package newex.content;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.UnitSpawnEvent;
import mindustry.game.EventType.WaveEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Rules;
import mindustry.game.SpawnGroup;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.MatchTimer;
import newex.NexNet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Chuyển từ newmode-survival.js. Chế độ Survival: diệt 1000 địch, địch spawn ngẫu nhiên khắp bản đồ. */
public final class Survival {
    private Survival() {}

    static final int KILL_GOAL = 1000;
    static final int REWARD_POINTS = 100;
    static final int DEFAULT_SAFE_TILES = 50;
    static final int EDGE_MARGIN_TILES = 3;
    static final int MAX_TRIES = 80;

    static final String TAG_ACTIVE = "newex-survival";
    static final String TAG_KILLS = "newex-survival-kills";

    static final float HUD_W = 150f;
    static final float SKILL_BTN_H = 28f;

    // ---- Cầu nối cho survivor-skills.js (chưa chuyển): thay cho global.NexSkills ----
    public static class LevelInfo { public int level, exp, need; }
    /** survivor-skills gán: trả cấp độ/exp người chơi cục bộ. */
    public static Supplier<LevelInfo> levelInfoProvider;
    /** survivor-skills gán: mở danh sách kĩ năng. */
    public static Runnable skillListOpener;

    private static boolean winning = false;
    private static boolean killDirty = false;
    private static float killT = 0f;
    private static NewMode.Result lastResult = null;

    private static boolean en() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static ObjectMap<String, String> tags() {
        Rules r = Vars.state != null ? Vars.state.rules : null;
        return (r != null && r.tags != null) ? r.tags : null;
    }

    public static boolean isActive() {
        if (Vars.state == null || !Vars.state.isGame()) return false;
        ObjectMap<String, String> tags = tags();
        return tags != null && "1".equals(tags.get(TAG_ACTIVE, "0"));
    }

    public static int getKills() {
        ObjectMap<String, String> tags = tags();
        if (tags == null) return 0;
        try { return Integer.parseInt(tags.get(TAG_KILLS, "0")); } catch (NumberFormatException e) { return 0; }
    }

    private static void setKills(int n) {
        ObjectMap<String, String> tags = tags();
        if (tags != null) tags.put(TAG_KILLS, String.valueOf(n));
    }

    // ------------------------------------------------------------------ spawn

    private static float safeRadiusWorld() {
        float r = Vars.state.rules.enemyCoreBuildRadius;
        return r > 0 ? r : DEFAULT_SAFE_TILES * Vars.tilesize;
    }

    private static List<float[]> getCorePositions() {
        List<float[]> out = new ArrayList<>();
        Team team = Vars.state.rules.defaultTeam;
        Seq<CoreBuild> cores = team != null ? team.cores() : null;
        if (cores == null) return out;
        for (int i = 0; i < cores.size; i++) {
            CoreBuild c = cores.get(i);
            if (c != null) out.add(new float[]{c.x, c.y});
        }
        return out;
    }

    private static boolean tooCloseToCore(float wx, float wy, List<float[]> cores, float minDist) {
        for (float[] c : cores) {
            float dx = wx - c[0], dy = wy - c[1];
            if (Math.sqrt(dx * dx + dy * dy) < minDist) return true;
        }
        return false;
    }

    private static boolean isSpawnable(int tx, int ty, UnitType type, int clear) {
        for (int dx = -clear; dx <= clear; dx++) {
            for (int dy = -clear; dy <= clear; dy++) {
                Tile t = Vars.world.tile(tx + dx, ty + dy);
                if (t == null) return false;
                if (t.solid() || t.build != null) return false;
            }
        }
        Tile center = Vars.world.tile(tx, ty);
        if (center == null) return false;
        if (type.naval) return center.floor().isLiquid;
        if (!type.flying && center.floor().isDeep()) return false;
        return true;
    }

    private static float[] pickSpawnPos(UnitType type, List<float[]> cores, float minDist) {
        int w = Vars.world.width(), h = Vars.world.height(), m = EDGE_MARGIN_TILES;
        if (w <= 2 * m || h <= 2 * m) return null;

        int ts = Vars.tilesize;
        int clear = Math.min(4, Math.max(1, (int) Math.ceil(type.hitSize / (2f * ts))));

        for (int pass = 0; pass < 2; pass++) {
            int cr = pass == 0 ? clear : 1;
            for (int i = 0; i < MAX_TRIES; i++) {
                int tx = m + (int) Math.floor(Math.random() * (w - 2 * m));
                int ty = m + (int) Math.floor(Math.random() * (h - 2 * m));
                float wx = tx * ts, wy = ty * ts;
                if (tooCloseToCore(wx, wy, cores, minDist)) continue;
                if (!isSpawnable(tx, ty, type, cr)) continue;
                return new float[]{wx, wy};
            }
        }
        return null;
    }

    private static float facingCore(float wx, float wy, List<float[]> cores) {
        if (cores.isEmpty()) return 0f;
        float[] best = cores.get(0);
        double bd = Double.MAX_VALUE;
        for (float[] c : cores) {
            double d = Math.pow(c[0] - wx, 2) + Math.pow(c[1] - wy, 2);
            if (d < bd) { bd = d; best = c; }
        }
        return (float) (Math.atan2(best[1] - wy, best[0] - wx) * 180.0 / Math.PI);
    }

    private static boolean spawnOne(SpawnGroup g, int waveIndex, Team team, List<float[]> cores, float minDist) {
        float[] pos = pickSpawnPos(g.type, cores, minDist);
        if (pos == null) return false;

        Unit unit = g.type.create(team);
        if (g.effect != null) unit.apply(g.effect, 999999f);
        if (g.items != null) unit.addItem(g.items.item, g.items.amount);
        unit.shield = g.getShield(waveIndex);
        unit.set(pos[0], pos[1]);
        unit.rotation = facingCore(pos[0], pos[1], cores);

        unit.apply(StatusEffects.unmoving, 30f);
        unit.apply(StatusEffects.invincible, 60f);
        unit.add();

        Events.fire(new UnitSpawnEvent(unit));
        Fx.spawn.at(pos[0], pos[1]);
        return true;
    }

    private static void spawnWave(int waveIndex) {
        Rules rules = Vars.state.rules;
        Team team = rules.waveTeam;
        List<float[]> cores = getCorePositions();
        float minDist = safeRadiusWorld();
        rules.spawns.each(g -> {
            if (g == null || g.type == null) return;
            int n = g.getSpawned(waveIndex);
            for (int i = 0; i < n; i++) spawnOne(g, waveIndex, team, cores, minDist);
        });
    }

    private static void win() {
        if (winning) return;
        winning = true;

        NewMode.Result res = new NewMode.Result();
        res.kills = getKills();
        res.wave = Vars.state.wave;
        String time = "--:--";
        try { time = MatchTimer.format(MatchTimer.getSeconds()); } catch (Throwable ignored) {}
        res.time = time;
        lastResult = res;

        Time.run(5f, () -> {
            Team team = Vars.state.rules.defaultTeam;
            try {
                Vars.logic.gameOver(team);
            } catch (Throwable err) {
                Vars.state.gameOver = true;
                Events.fire(new GameOverEvent(team));
            }
        });
    }

    // ------------------------------------------------------------------ HUD

    private static String hudLine1() {
        int k = Math.min(getKills(), KILL_GOAL);
        return (en() ? "[accent]Kills [white]" : "[accent]Diệt [white]") + k + "[lightgray]/" + KILL_GOAL
            + (en() ? "  [accent]W [white]" : "  [accent]Đợt [white]") + Vars.state.wave;
    }

    private static LevelInfo lvInfo() {
        try { return levelInfoProvider != null ? levelInfoProvider.get() : null; } catch (Throwable e) { return null; }
    }

    private static String expText() {
        LevelInfo info = lvInfo();
        if (info == null) return "";
        String lv = (en() ? "Lv " : "Cấp ") + info.level;
        if (info.need <= 0) return lv + " MAX";
        return lv + "  " + info.exp + "/" + info.need;
    }

    private static float expFrac() {
        LevelInfo info = lvInfo();
        if (info == null) return 0f;
        if (info.need <= 0) return 1f;
        return Math.max(0f, Math.min(1f, (float) info.exp / info.need));
    }

    private static void buildHud() {
        if (Vars.headless) return;

        boolean hasSavedPos = Core.settings.has("newex-survival-hud-x") && Core.settings.has("newex-survival-hud-y");
        final float[] pos = {Core.settings.getFloat("newex-survival-hud-x", -1f), Core.settings.getFloat("newex-survival-hud-y", -1f)};

        Table t = new Table(Styles.black6);
        t.margin(4f);
        t.name = "newex-survival-hud";
        t.touchable = Touchable.enabled;
        t.table(r -> {
            r.left();
            r.label(Survival::hudLine1).width(HUD_W).left().get().setFontScale(0.8f);
        }).left();
        t.row();
        t.add(new Bar(Survival::expText, () -> Pal.accent, Survival::expFrac)).size(HUD_W, 14f).padTop(2f);
        t.row();
        arc.scene.ui.TextButton sb = t.button(en() ? "Skills" : "Kĩ năng", Icon.list, () -> {
            try { if (skillListOpener != null) skillListOpener.run(); } catch (Throwable ignored) {}
        }).size(HUD_W, SKILL_BTN_H).padTop(3f).get();
        sb.getLabel().setFontScale(0.8f);
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown);
        t.pack();

        if (!hasSavedPos) {
            pos[0] = Core.graphics.getWidth() / 2f - t.getWidth() / 2f;
            pos[1] = Core.graphics.getHeight() - t.getHeight() - 70f;
        }
        t.setPosition(pos[0], pos[1]);

        final boolean[] dragging = {false};
        final float[] off = {0f, 0f};
        t.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging[0] = false;
                off[0] = x; off[1] = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - off[0]) > 5 || Math.abs(y - off[1]) > 5) dragging[0] = true;
                if (dragging[0]) {
                    float maxX = Math.max(0f, Core.graphics.getWidth() - t.getWidth());
                    float maxY = Math.max(0f, Core.graphics.getHeight() - t.getHeight());
                    pos[0] = Mathf.clamp(t.x + x - off[0], 0f, maxX);
                    pos[1] = Mathf.clamp(t.y + y - off[1], 0f, maxY);
                    t.setPosition(pos[0], pos[1]);
                    Core.settings.put("newex-survival-hud-x", pos[0]);
                    Core.settings.put("newex-survival-hud-y", pos[1]);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging[0] = false;
            }
        });

        Vars.ui.hudGroup.addChild(t);
    }

    // ------------------------------------------------------------------ hook cho NewMode

    private static final NewMode.ModeHook HOOK = new NewMode.ModeHook() {
        @Override public int rewardPoints() { return REWARD_POINTS; }
        @Override public int killGoal() { return KILL_GOAL; }
        @Override public NewMode.Result lastResult() { return lastResult; }

        @Override
        public void onBegin() {
            winning = false;
            lastResult = null;
            Rules r = Vars.state.rules;
            r.tags.put(TAG_ACTIVE, "1");
            setKills(0);
            r.waves = true;
            r.waveTimer = true;
            r.attackMode = false;
            r.winWave = 0;
        }

        @Override
        public void onResume() {
            winning = false;
            lastResult = null;
            Vars.ui.showInfoToast((en() ? "Survival: " : "Survival: đã diệt ") + getKills() + "/" + KILL_GOAL, 3f);
        }
    };

    public static void init() {
        NewMode.survival = HOOK;

        // server -> mọi client: đồng bộ số kill cho bạn bè
        NexNet.onBroadcast("newex-survival-kills", payload -> {
            if (Vars.net.client()) {
                try { setKills(Integer.parseInt(payload)); } catch (NumberFormatException ignored) {}
            }
        });

        Events.run(Trigger.update, () -> {
            if (Vars.net.client() || !Vars.net.server() || !isActive()) return;
            killT -= Time.delta;
            if (killT > 0f) return;
            if (killDirty || killT < -120f) {
                killDirty = false;
                killT = 15f;
                NexNet.broadcast("newex-survival-kills", String.valueOf(getKills()));
            }
        });

        Events.on(WaveEvent.class, e -> {
            if (Vars.net.client() || !isActive() || Vars.state.gameOver) return;
            spawnWave(Vars.state.wave - 2);
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if (Vars.net.client() || !isActive() || Vars.state.gameOver || winning) return;
            Unit u = e.unit;
            if (u == null || u.team != Vars.state.rules.waveTeam) return;
            int k = getKills() + 1;
            setKills(k);
            killDirty = true;
            if (k >= KILL_GOAL) win();
        });

        Events.on(WorldLoadEvent.class, e -> winning = false);

        Events.on(ClientLoadEvent.class, e -> buildHud());
    }
}
