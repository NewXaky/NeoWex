package newex.content;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chuyển từ element-system.js (global.NexElement). Mỗi pháo được gán cố định 1 nguyên tố (Băng giá hoặc Lửa đốt) theo seed:
 * 20% khi trúng đích sẽ gắn ấn. Băng giá chồng tối đa 3 tầng; Lửa đốt gây sát thương mỗi giây;
 * có cả hai ấn thì phản ứng "Hoàng Hỏa". Chỉ chạy ở server/host.
 * Pháo gọi {@link #hit}; sát thương gián tiếp (không qua hit) được quy về pháo gần nhất đang nhắm đúng đơn vị đó.
 */
public final class ElementSystem {
    private static final float PROC_CHANCE = 0.20f;
    private static final int FROST_MAX_STACKS = 3;
    private static final float FROST_DURATION = 300f;
    private static final float BURN_DURATION = 300f;
    private static final float BURN_FLAT_DAMAGE = 10f;
    private static final float BURN_DAMAGE_RATIO = 0.10f;
    private static final float HUANGHUO_DURATION = 600f;
    private static final boolean REACTION_BOTH_ORDERS = true;
    private static final long ELEMENT_SEED = 20260425L;
    private static final float POLL_INTERVAL = 30f;
    private static final float CACHE_INTERVAL = 6f;
    private static final float ROLL_COOLDOWN = 12f;
    private static final float UNIT_COOLDOWN = 6f;
    private static final float ASSOC_RADIUS = 200f;
    private static final float MIN_DROP = 0.5f;

    private static final String[] TURRET_NAMES = {"aether-spiral", "bayrowfyr", "blaw", "blixalum", "buffetles", "dor", "drone-launcher", "dtg-soldern", "emperfum", "emperical", "endyr", "erysidus", "flazerd", "forstarsilum", "galaxvorram", "heliyron", "hitekalum", "holyder", "indeniter", "lavunder", "laytery", "lazash", "lyvervon", "maxitoner", "nethyr", "nucleytor", "overyr", "persefer", "plasanod", "rangtaturs", "ravendes", "reflecounum", "reguilater", "repulsyron", "sonicor", "swordoder", "tankani2k", "tankani4k", "tankani500", "tenytisyr", "therdum", "thermion", "tyber", "tyteryi", "vendicum", "xylaon", "zoj"};

    private static final Map<String, Float> AURA_TURRETS = new HashMap<>();

    /** Tên khối -> "frost" hoặc "fire". */
    private static final Map<String, String> elementOf = new HashMap<>();

    private static final class Mark {
        final Unit unit;
        int frost = 0;
        float frostEnd = 0f, burnEnd = 0f, burnDps = 0f, burnNext = 0f, huanghuoEnd = 0f;

        Mark(Unit unit) {
            this.unit = unit;
        }
    }

    private static final class Statuses {
        final StatusEffect[] frost;
        final StatusEffect flame, huanghuo;

        Statuses(StatusEffect[] frost, StatusEffect flame, StatusEffect huanghuo) {
            this.frost = frost;
            this.flame = flame;
            this.huanghuo = huanghuo;
        }
    }

    private static final TreeMap<Integer, Mark> marks = new TreeMap<>();
    private static final Map<Integer, Float> track = new HashMap<>();
    private static final Map<Integer, Integer> exactStamp = new HashMap<>();
    private static final Map<String, Float> rollCd = new HashMap<>();
    private static final Map<Integer, Float> unitCd = new HashMap<>();
    private static final Map<Integer, List<Building>> targetMap = new HashMap<>();
    private static final List<Building> turretCache = new ArrayList<>();
    private static Statuses statusCache = null;
    private static float lastPoll = 0f;
    private static float lastCache = -999f;
    private static float lastPrune = 0f;
    private static int frame = 0;

    static {
        AURA_TURRETS.put("newex-thermion", 45f);

        // Gán nguyên tố: sắp xếp tên, xáo trộn bằng LCG theo seed cố định, nửa đầu = Băng giá, nửa sau = Lửa đốt.
        String[] names = TURRET_NAMES.clone();
        Arrays.sort(names);
        long seed = ELEMENT_SEED;
        for (int i = names.length - 1; i > 0; i--) {
            seed = (seed * 1664525L + 1013904223L) % 4294967296L;
            double rnd = seed / 4294967296.0;
            int j = (int) Math.floor(rnd * (i + 1));
            String tmp = names[i];
            names[i] = names[j];
            names[j] = tmp;
        }
        int half = (int) Math.ceil(names.length / 2.0);
        for (int i = 0; i < names.length; i++) {
            elementOf.put("newex-" + names[i], i < half ? "frost" : "fire");
        }
    }

    private ElementSystem() {}

    /** Nguyên tố của khối theo tên ("frost" / "fire" / null). */
    public static String elementOf(String blockName) {
        return elementOf.get(blockName);
    }

    private static boolean isEn() {
        return Core.settings.getString("locale", "vi").startsWith("en");
    }

    private static Statuses getStatuses() {
        if (statusCache != null) return statusCache;
        StatusEffect[] frost = {get("frostmark1"), get("frostmark2"), get("frostmark3")};
        StatusEffect flame = get("flamemark");
        StatusEffect huanghuo = get("hoanghoa");
        if (frost[0] == null || flame == null || huanghuo == null) return null;
        statusCache = new Statuses(frost, flame, huanghuo);
        return statusCache;
    }

    private static StatusEffect get(String n) {
        return Vars.content.getByName(ContentType.status, "newex-" + n);
    }

    private static String elementOfSource(Object source) {
        if (!(source instanceof Building)) return null;
        Block blk = ((Building) source).block;
        if (blk == null) return null;
        return elementOf.get(String.valueOf(blk.name));
    }

    private static Mark getMark(Unit unit) {
        Mark m = marks.get(unit.id);
        if (m == null) {
            m = new Mark(unit);
            marks.put(unit.id, m);
        }
        return m;
    }

    private static void clearFrost(Unit unit, Mark m) {
        Statuses st = getStatuses();
        if (st != null) {
            for (int i = 0; i < st.frost.length; i++) {
                if (st.frost[i] != null) unit.unapply(st.frost[i]);
            }
        }
        m.frost = 0;
        m.frostEnd = 0f;
    }

    private static void clearBurn(Unit unit, Mark m) {
        Statuses st = getStatuses();
        if (st != null) unit.unapply(st.flame);
        m.burnEnd = 0f;
        m.burnDps = 0f;
        m.burnNext = 0f;
    }

    private static void react(Unit unit, Mark m) {
        Statuses st = getStatuses();
        if (st == null) return;
        clearFrost(unit, m);
        clearBurn(unit, m);
        m.huanghuoEnd = Time.time + HUANGHUO_DURATION;
        unit.apply(st.huanghuo, HUANGHUO_DURATION);
        try {
            Call.label(isEn() ? "[gold]HUANGHUO![]" : "[gold]HOÀNG HỎA![]", 1.4f, unit.x, unit.y);
        } catch (Throwable ignored) {
        }
    }

    public static void applyFrost(Unit unit) {
        Statuses st = getStatuses();
        if (st == null) return;
        Mark m = getMark(unit);
        float now = Time.time;
        if (m.frost > 0 && now >= m.frostEnd) m.frost = 0;

        int prev = m.frost;
        m.frost = Math.min(FROST_MAX_STACKS, m.frost + 1);
        m.frostEnd = now + FROST_DURATION;

        if (prev > 0 && st.frost[prev - 1] != null && prev != m.frost) unit.unapply(st.frost[prev - 1]);
        StatusEffect cur = st.frost[m.frost - 1];
        if (cur != null) unit.apply(cur, FROST_DURATION);

        if (REACTION_BOTH_ORDERS && m.burnEnd > now) react(unit, m);
    }

    public static void applyFire(Unit unit, float baseDmg) {
        Statuses st = getStatuses();
        if (st == null) return;
        Mark m = getMark(unit);
        float now = Time.time;
        if (m.burnEnd > 0 && now >= m.burnEnd) {
            m.burnEnd = 0f;
            m.burnDps = 0f;
        }

        float dps = BURN_FLAT_DAMAGE + BURN_DAMAGE_RATIO * Math.max(0f, baseDmg);
        if (m.burnEnd <= 0) m.burnNext = now + 60f;
        m.burnDps = Math.max(m.burnDps, dps);
        m.burnEnd = now + BURN_DURATION;
        unit.apply(st.flame, BURN_DURATION);

        if (m.frost > 0 && m.frostEnd > now) react(unit, m);
    }

    /** Pháo báo "đã trúng chính xác" đơn vị này (tránh quy gián tiếp trùng lặp). */
    public static void hit(Building source, Unit unit, float baseDmg) {
        if (Vars.net.client()) return;
        if (unit == null) return;
        exactStamp.put(unit.id, frame);
        rollProc(source, unit, baseDmg);
    }

    private static void rollProc(Building source, Unit unit, float baseDmg) {
        if (Vars.net.client()) return;
        if (unit == null || !unit.isValid() || unit.dead) return;
        String element = elementOfSource(source);
        if (element == null) return;
        if (source.team == unit.team) return;
        if (!Mathf.chance(PROC_CHANCE)) return;

        if (element.equals("frost")) applyFrost(unit);
        else applyFire(unit, Float.isNaN(baseDmg) ? 0f : baseDmg);
    }

    private static void burnTick(Unit unit, Mark m) {
        try {
            unit.damagePierce(m.burnDps);
        } catch (Throwable err) {
            unit.damage(m.burnDps);
        }
        track.put(unit.id, unit.health + unit.shield);
    }

    private static void clearAll() {
        marks.clear();
        track.clear();
        exactStamp.clear();
        rollCd.clear();
        unitCd.clear();
        targetMap.clear();
        turretCache.clear();
        statusCache = null;
        lastPoll = 0f;
        lastCache = -999f;
        lastPrune = 0f;
    }

    /** Mục tiêu hiện tại của khối (chỉ pháo TurretBuild mới có), hoặc null. */
    private static Object targetOf(Building b) {
        if (b instanceof Turret.TurretBuild) return ((Turret.TurretBuild) b).target;
        return null;
    }

    private static float turretRange(Building b) {
        if (b instanceof Turret.TurretBuild) {
            float r = ((Turret.TurretBuild) b).range();
            if (r > 0) return r;
        }
        float br = b.block instanceof Turret ? ((Turret) b.block).range : 0f;
        return br > 0 ? br : 200f;
    }

    private static void refreshTurrets() {
        turretCache.clear();
        targetMap.clear();
        Groups.build.each(b -> {
            if (b == null || !b.isValid()) return;
            if (!elementOf.containsKey(String.valueOf(b.block.name))) return;
            turretCache.add(b);
            Object t = targetOf(b);
            if (t instanceof Unit && ((Unit) t).isValid()) {
                int tid = ((Unit) t).id;
                List<Building> arr = targetMap.get(tid);
                if (arr == null) {
                    arr = new ArrayList<>();
                    targetMap.put(tid, arr);
                }
                arr.add(b);
            }
        });
    }

    private static void rollFor(Building b, Unit unit, float drop, float now) {
        String key = b.pos() + ":" + unit.id;
        Float last = rollCd.get(key);
        if (last != null && now - last < ROLL_COOLDOWN) return;
        rollCd.put(key, now);
        rollProc(b, unit, drop);
    }

    private static void attribute(Unit unit, float drop, float now) {
        Float lastU = unitCd.get(unit.id);
        if (lastU != null && now - lastU < UNIT_COOLDOWN) return;
        unitCd.put(unit.id, now);

        List<Building> list = targetMap.get(unit.id);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Building b = list.get(i);
                if (b.isValid() && b.team != unit.team) rollFor(b, unit, drop, now);
            }
            return;
        }

        Building best = null;
        double bestD = 1e18;
        float assoc2 = ASSOC_RADIUS * ASSOC_RADIUS;
        for (int i = 0; i < turretCache.size(); i++) {
            Building b = turretCache.get(i);
            if (b.team == unit.team || !b.isValid()) continue;
            Object t = targetOf(b);
            if (t == null) continue;
            Posc tp = (Posc) t;
            float tx = tp.x(), ty = tp.y();
            if ((tx - unit.x) * (tx - unit.x) + (ty - unit.y) * (ty - unit.y) > assoc2) continue;
            float reach = turretRange(b) + unit.hitSize;
            float d2 = (b.x - unit.x) * (b.x - unit.x) + (b.y - unit.y) * (b.y - unit.y);
            if (d2 > reach * reach || d2 >= bestD) continue;
            bestD = d2;
            best = b;
        }
        if (best != null) rollFor(best, unit, drop, now);
    }

    private static void observe(final float now) {
        frame++;
        if (turretCache.size() == 0) {
            track.clear();
            return;
        }
        Groups.unit.each(u -> {
            if (u.dead || !u.isValid()) return;
            int id = u.id;
            float hp = u.health + u.shield;
            Float prev = track.get(id);
            track.put(id, hp);
            if (prev == null) return;
            float drop = prev - hp;
            if (drop < MIN_DROP) return;
            Integer ex = exactStamp.get(id);
            if (ex != null && ex >= frame - 1) return;
            attribute(u, drop, now);
        });

        if (now - lastPrune > 600 || now < lastPrune) {
            lastPrune = now;
            track.clear();
            for (Iterator<Map.Entry<String, Float>> it = rollCd.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue() > 120) it.remove();
            }
            for (Iterator<Map.Entry<Integer, Float>> it = unitCd.entrySet().iterator(); it.hasNext(); ) {
                if (now - it.next().getValue() > 120) it.remove();
            }
            for (Iterator<Map.Entry<Integer, Integer>> it = exactStamp.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue() < frame - 120) it.remove();
            }
        }
    }

    private static void pollTurrets() {
        Groups.build.each(b -> {
            if (b == null || !b.isValid()) return;
            String name = String.valueOf(b.block.name);

            final Float aura = AURA_TURRETS.get(name);
            if (aura != null && b instanceof AuraTurret) {
                final float r = ((AuraTurret) b).affectRadius();
                if (!(r > 0)) return;
                final Building src = b;
                Units.nearbyEnemies(b.team, b.x - r, b.y - r, r * 2f, r * 2f, u -> {
                    if (u.within(src.x, src.y, r)) hit(src, u, aura);
                });
            }
        });
    }

    private static void update() {
        if (Vars.net.client() || !Vars.state.isGame()) return;
        float now = Time.time;
        if (now < lastPoll) lastPoll = now;
        if (now < lastCache) lastCache = -999f;

        if (now - lastCache >= CACHE_INTERVAL) {
            lastCache = now;
            refreshTurrets();
        }

        Iterator<Map.Entry<Integer, Mark>> it = marks.entrySet().iterator();
        while (it.hasNext()) {
            Mark m = it.next().getValue();
            Unit u = m.unit;
            if (u == null || !u.isValid() || u.dead || !u.isAdded()) {
                it.remove();
                continue;
            }
            if (m.frost > 0 && now >= m.frostEnd) clearFrost(u, m);
            if (m.burnEnd > 0) {
                if (now >= m.burnEnd) {
                    m.burnEnd = 0f;
                    m.burnDps = 0f;
                } else if (now >= m.burnNext) {
                    m.burnNext += 60f;
                    burnTick(u, m);
                }
            }
            if (m.frost <= 0 && m.burnEnd <= 0 && now >= m.huanghuoEnd) it.remove();
        }

        observe(now);

        if (now - lastPoll >= POLL_INTERVAL) {
            lastPoll = now;
            pollTurrets();
        }
    }

    public static void init() {
        TurretSystem.elementHit = ElementSystem::hit;

        Events.on(UnitDamageEvent.class, e -> {
            if (Vars.net.client()) return;
            if (e.bullet == null) return;
            Object owner = e.bullet.owner;
            if (owner == null || elementOfSource(owner) == null) return;
            hit((Building) owner, e.unit, e.bullet.damage);
        });

        Events.run(Trigger.update, ElementSystem::update);

        Events.on(WorldLoadEvent.class, e -> clearAll());

        Events.on(ClientLoadEvent.class, e -> {
            for (String n : TURRET_NAMES) {
                Block blk = Vars.content.getByName(ContentType.block, "newex-" + n);
                if (blk == null) continue;
                String el = elementOf.get("newex-" + n);
                boolean frost = "frost".equals(el);
                String tag = frost ? "[#9bdcff]" : "[#ff9a3c]";
                String line;
                if (isEn()) {
                    line = frost
                        ? "\n\n" + tag + "Element: Frost[] - 20% on hit to apply Frost (max 3 stacks, -10% move & fire rate each)."
                        : "\n\n" + tag + "Element: Flame[] - 20% on hit to apply Flame (10 + 10% base dmg per second).";
                } else {
                    line = frost
                        ? "\n\n" + tag + "Nguyên tố: Băng giá[] - 20% khi trúng đích gắn ấn Băng giá (tối đa 3 tầng, mỗi tầng -10% di chuyển và tốc bắn)."
                        : "\n\n" + tag + "Nguyên tố: Lửa đốt[] - 20% khi trúng đích gắn ấn Lửa đốt (mỗi giây 10 + 10% sát thương gốc).";
                }
                if (blk.description != null && !blk.description.contains("Nguyên tố:") && !blk.description.contains("Element:")) {
                    blk.description = blk.description + line;
                }
            }
        });
    }
}
