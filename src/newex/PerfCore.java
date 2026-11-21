package newex;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.geom.Rect;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.UnitType;
import mindustry.world.Block;

import java.util.HashMap;
import java.util.Map;

/** Chuyển từ perf-core.js (global.PerfCore). Tiện ích hiệu năng dùng chung cho toàn mod. */
public final class PerfCore {
    private static final Map<String, Color> colorCache = new HashMap<>();
    private static final Map<String, ScanEntry> frameCountCache = new HashMap<>();
    private static final Map<String, Float> slotLast = new HashMap<>();
    private static final Rect viewRect = new Rect();

    private static int lvl = 0, lowCount = 0, midCount = 0;
    private static float tickAcc = 0f;

    private static final class ScanEntry {
        final float t;
        final int count;
        final Building first;

        ScanEntry(float t, int count, Building first) {
            this.t = t;
            this.count = count;
            this.first = first;
        }
    }

    private PerfCore() {}

    /** Gọi 1 lần từ NexMod. */
    public static void init() {
        Events.run(Trigger.update, PerfCore::tick);
        Events.on(WorldLoadEvent.class, e -> {
            frameCountCache.clear();
            slotLast.clear();
            lvl = 0;
            lowCount = 0;
            midCount = 0;
            tickAcc = 0f;
        });
    }

    private static void tick() {
        if (Vars.state == null || !Vars.state.isGame()) {
            lvl = 0;
            lowCount = 0;
            midCount = 0;
            return;
        }
        tickAcc += Time.delta;
        if (tickAcc < 30f) return;
        tickAcc = 0f;
        if (Core.graphics == null) return;
        int fps = Core.graphics.getFramesPerSecond();
        if (fps < 25) {
            lowCount++;
            midCount++;
        } else if (fps < 42) {
            lowCount = 0;
            midCount++;
        } else {
            lowCount = 0;
            midCount = 0;
        }
        lvl = lowCount >= 3 ? 2 : (midCount >= 3 ? 1 : 0);
    }

    /** Kiểm tra nhanh O(1) theo TeamData xem bản đồ có ít nhất 1 unit thuộc các loại truyền vào hay không. */
    public static boolean hasAnyUnitType(UnitType... types) {
        if (types == null || types.length == 0 || Groups.unit.isEmpty()) return false;
        try {
            Seq<Teams.TeamData> active = Vars.state.teams.active;
            for (int i = 0; i < active.size; i++) {
                Teams.TeamData td = active.get(i);
                for (int j = 0; j < types.length; j++) {
                    UnitType t = types[j];
                    if (t != null && td.countType(t) > 0) return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static TextureRegion region(String name) {
        return Core.atlas.find(name);
    }

    public static TextureRegion region(String name, String fallback) {
        return Core.atlas.find(name, fallback);
    }

    public static Color color(String hex) {
        Color c = colorCache.get(hex);
        if (c == null) {
            c = Color.valueOf(hex);
            colorCache.put(hex, c);
        }
        return c;
    }

    /** Danh sách công trình của đội theo loại block, hoặc null nếu lỗi. */
    public static Seq<Building> teamBlocks(Team team, Block block) {
        try {
            return team.data().getBuildings(block);
        } catch (Throwable e) {
            return null;
        }
    }

    private static ScanEntry scanCount(Team team, Block block) {
        String key = (team != null ? team.id : -1) + ":" + block.id;
        ScanEntry e = frameCountCache.get(key);
        if (e != null && e.t == Time.time) return e;

        final int[] count = {0};
        final Building[] first = {null};
        Groups.build.each(b -> {
            if (b.block == block && (team == null || b.team == team)) {
                count[0]++;
                if (first[0] == null) first[0] = b;
            }
        });
        e = new ScanEntry(Time.time, count[0], first[0]);
        frameCountCache.put(key, e);
        return e;
    }

    public static int teamCount(Team team, Block block) {
        if (block == null) return 0;
        if (team != null) {
            Seq<Building> l = teamBlocks(team, block);
            if (l != null) return l.size;
            return scanCount(team, block).count;
        }
        try {
            int total = 0;
            Seq<Teams.TeamData> act = Vars.state.teams.active;
            for (int i = 0; i < act.size; i++) total += act.get(i).getBuildings(block).size;
            return total;
        } catch (Throwable e) {
            return scanCount(null, block).count;
        }
    }

    public static Building firstOf(Team team, Block block) {
        Seq<Building> l = teamBlocks(team, block);
        if (l != null) return l.size > 0 ? l.first() : null;
        return scanCount(team, block).first;
    }

    /** true tối đa 1 lần mỗi {@code ticks} tick cho mỗi {@code key}. */
    public static boolean every(String key, float ticks) {
        Float last = slotLast.get(key);
        if (last == null || Time.time - last >= ticks || Time.time < last) {
            slotLast.put(key, Time.time);
            return true;
        }
        return false;
    }

    public static boolean onScreen(float x, float y, float pad) {
        Core.camera.bounds(viewRect);
        return x >= viewRect.x - pad && x <= viewRect.x + viewRect.width + pad
            && y >= viewRect.y - pad && y <= viewRect.y + viewRect.height + pad;
    }

    public static boolean onScreen(float x, float y) {
        return onScreen(x, y, 0f);
    }

    /** 0 = mượt, 1 = hơi tụt FPS, 2 = tụt FPS nặng. */
    public static int level() {
        return lvl;
    }
}
