package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.StatusEntry;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.type.Weapon;

/**
 * Chuyển từ cin.js. Năng lực của đơn vị "cin": nộ tăng dần mỗi lần bị đánh, đủ 100 điểm thì nổ lan; 20% mỗi phát bắn
 * tạo loạt 10 đạn và tia laser; hút các unit Reign đồng minh trong 10 ô để tăng máu tối đa; mỗi phe chỉ được có 1 Cin.
 */
public final class Cin {
    private static final float CRIT_CHANCE = 0.5f;
    private static final float CRIT_MULTIPLIER = 1.8f;

    private static final float HIT_RAGE_CHANCE = 0.5f;
    private static final float RAGE_STEP = 0.01f;
    private static final int RAGE_MAX = 100;
    private static final int POINTS_PER_HIT = 1;
    private static final int POINTS_NEEDED = 100;

    private static final float BLAST_RADIUS = 25f * Vars.tilesize;
    private static final float BLAST_DAMAGE = 1500f;
    private static final float BLAST_HEAL = 0.1f;
    private static final float ABSORB_RADIUS = 10f * Vars.tilesize;
    private static final float ABSORB_HEALTH = 0.1f;
    private static final float ABSORB_TIME = 60f;
    private static final float ABSORB_SCAN = 15f;
    private static final float ABSORB_LEASH = 1.3f;

    private static final int MAX_PER_TEAM = 1;

    private static final float BURST_CHANCE = 0.2f;
    private static final int BURST_BULLETS = 10;
    private static final float BURST_SPREAD = 4f;
    private static final float BURST_COOLDOWN = 20f;
    private static final float FRENZY_TIME = 180f;
    private static final float FRENZY_RELOAD = 6f;

    private static final float BEAM_CHANCE = 0.2f;
    private static final float BEAM_FORWARD = 4f * Vars.tilesize;
    private static final float BEAM_DAMAGE = (float) (2400 + 112 * 1.71 * 3);
    private static final float BEAM_RADIUS = 40f;
    private static final float BEAM_TIME = 12f;
    private static final float BEAM_COOLDOWN = 30f;
    private static final float BEAM_MIN_RANGE = 260f;
    private static final boolean BEAM_HITS_AIR = true;

    private static final Color beamColor = Color.valueOf("33ff55");
    private static final Color blastColor = Color.valueOf("ffaa00");
    private static final Color linkColor = Color.valueOf("ff4d6d");

    private static final class State {
        float hp;
        int rage = 0;
        int points = 0;
        final float[] reloads;
        float burstReady = 0f;
        float beamReady = 0f;
        Unit absorbTarget = null;
        float absorbTimer = 0f;
        float scanTimer = ABSORB_SCAN;

        State(Unit unit) {
            hp = unit.health;
            reloads = new float[unit.mounts.length];
            for (int i = 0; i < unit.mounts.length; i++) {
                reloads[i] = unit.mounts[i].reload;
            }
        }
    }

    private static final IntMap<State> states = new IntMap<>();

    private static final Effect beamFx = new Effect(BEAM_TIME, 900f, e -> {
        if (!(e.data instanceof Vec2)) return;
        Vec2 target = (Vec2) e.data;
        float f = e.fout();
        Draw.color(beamColor);
        Lines.stroke(16f * f);
        Lines.line(e.x, e.y, target.x, target.y);
        Fill.circle(e.x, e.y, 9f * f);
        Draw.color(Color.white);
        Lines.stroke(5f * f);
        Lines.line(e.x, e.y, target.x, target.y);
        Fill.circle(e.x, e.y, 4f * f);
        Draw.reset();
    });

    private static final Effect blastFx = new Effect(30f, BLAST_RADIUS * 2.5f, e -> {
        Draw.color(blastColor, Color.white, e.fout());
        Lines.stroke(e.fout() * 6f + 0.5f);
        Lines.circle(e.x, e.y, e.finpow() * BLAST_RADIUS);
        Draw.reset();
    });

    private static final Effect linkFx = new Effect(3f, 400f, e -> {
        if (!(e.data instanceof Unit)) return;
        Unit t = (Unit) e.data;
        if (t.dead) return;
        float p = e.rotation;
        float f = e.fout();
        float angle = Angles.angle(t.x, t.y, e.x, e.y);

        Draw.color(linkColor, Color.white, p);
        Lines.stroke((1f + 3f * p) * f);
        Lines.line(e.x, e.y, t.x, t.y);
        Lines.stroke((0.8f + 1.5f * p) * f);
        Lines.circle(t.x, t.y, t.hitSize * (1.3f - 0.7f * p));

        Draw.color(linkColor);
        Fill.circle(e.x, e.y, (4f + 10f * p) * f);

        for (int i = 0; i < 6; i++) {
            float k = (Time.time * 0.03f * (1f + p * 2f) + i / 6f) % 1f;
            float side = Mathf.sin(k * 12f + i) * 6f * (1f - k);
            float px = Mathf.lerp(t.x, e.x, k) + Angles.trnsx(angle + 90f, side);
            float py = Mathf.lerp(t.y, e.y, k) + Angles.trnsy(angle + 90f, side);
            Draw.color(Color.white, linkColor, k);
            Fill.circle(px, py, (1f + 2f * (1f - k)) * f);
        }
        Draw.reset();
    });

    private static StatusEffect frenzyStatus;
    private static StatusEffect coreStatus;
    private static Ability cinAbility;
    private static boolean patched = false;

    private Cin() {}

    /** Gọi từ NexMod.loadContent(): tạo 2 trạng thái trước khi game khởi tạo nội dung. */
    public static void create() {
        frenzyStatus = new StatusEffect("cin-frenzy");
        frenzyStatus.reloadMultiplier = FRENZY_RELOAD;
        frenzyStatus.show = false;

        coreStatus = new StatusEffect("cin-core") {
            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                State s = states.get(unit.id);
                float m = s == null ? 1f : 1f + s.rage * RAGE_STEP;
                if (Mathf.chance(CRIT_CHANCE)) m *= CRIT_MULTIPLIER;
                unit.damageMultiplier *= m;
            }
        };
        coreStatus.permanent = true;
        coreStatus.show = false;

        cinAbility = new Ability() {
            @Override
            public void update(Unit unit) {
                if (unit == null || unit.dead || Vars.net.client()) return;
                State s = states.get(unit.id);
                if (s == null) {
                    if (isOverLimit(unit)) {
                        rejectExtra(unit);
                        return;
                    }
                    s = new State(unit);
                    states.put(unit.id, s);
                    unit.apply(coreStatus, 1f);
                }
                trackAbsorb(unit, s);
                trackDamage(unit, s);
                trackShots(unit, s);
            }

            @Override
            public void death(Unit unit) {
                if (unit != null) states.remove(unit.id);
            }

            @Override
            public String localized() {
                return "Cin";
            }
        };
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> patch());
        Events.on(ClientLoadEvent.class, e -> patch());
        Events.on(ServerLoadEvent.class, e -> patch());
        Events.on(WorldLoadEvent.class, e -> states.clear());
    }

    private static boolean isCin(String name) {
        String n = name.toLowerCase();
        return n.equals("cin") || n.endsWith("-cin");
    }

    private static void patch() {
        if (patched) return;
        final UnitType[] found = {null};
        Vars.content.units().each(u -> {
            if (found[0] == null && isCin(u.name)) found[0] = u;
        });
        if (found[0] == null) return;
        patched = true;
        found[0].abilities.add(cinAbility);
    }

    // ------------------------------------------------------------------ logic

    private static void blast(Unit unit, State s) {
        final float x = unit.x;
        final float y = unit.y;
        Team team = unit.team;
        float bonus = 1f + s.rage * RAGE_STEP;

        final float damage = BLAST_DAMAGE * bonus;
        Units.nearbyEnemies(team, x - BLAST_RADIUS, y - BLAST_RADIUS, BLAST_RADIUS * 2f, BLAST_RADIUS * 2f, u -> {
            if (!u.dead && Mathf.dst(x, y, u.x, u.y) <= BLAST_RADIUS) {
                u.damage(damage);
            }
        });

        unit.heal(unit.maxHealth * BLAST_HEAL);

        blastFx.at(x, y);
        Fx.massiveExplosion.at(x, y);
        Effect.shake(10f, 20f, x, y);
    }

    private static boolean isReign(Unit u) {
        return !u.dead && u.type == UnitTypes.reign;
    }

    private static void absorbDone(Unit unit, Unit target) {
        float gain = unit.type.health * ABSORB_HEALTH;
        unit.maxHealth += gain;
        unit.heal(gain);
        Fx.massiveExplosion.at(target.x, target.y);
        Fx.hitLancer.at(unit.x, unit.y, linkColor);
        target.kill();
    }

    private static void trackAbsorb(Unit unit, State s) {
        Unit target = s.absorbTarget;
        if (target != null) {
            if (target.dead || !target.isValid() || Mathf.dst(unit.x, unit.y, target.x, target.y) > ABSORB_RADIUS * ABSORB_LEASH) {
                s.absorbTarget = null;
                s.absorbTimer = 0f;
                return;
            }
            s.absorbTimer += Time.delta;
            float progress = Math.min(s.absorbTimer / ABSORB_TIME, 1f);
            linkFx.at(unit.x, unit.y, progress, linkColor, target);
            if (s.absorbTimer >= ABSORB_TIME) {
                absorbDone(unit, target);
                s.absorbTarget = null;
                s.absorbTimer = 0f;
                s.scanTimer = ABSORB_SCAN;
            }
            return;
        }

        s.scanTimer += Time.delta;
        if (s.scanTimer < ABSORB_SCAN) return;
        s.scanTimer = 0f;
        Unit found = Units.closest(unit.team, unit.x, unit.y, ABSORB_RADIUS, Cin::isReign);
        if (found != null) {
            s.absorbTarget = found;
            s.absorbTimer = 0f;
        }
    }

    private static void trackDamage(Unit unit, State s) {
        float hp = unit.health;
        if (hp < s.hp - 0.01f) {
            if (Mathf.chance(HIT_RAGE_CHANCE)) {
                if (s.rage < RAGE_MAX) s.rage++;
            } else {
                s.points += POINTS_PER_HIT;
                if (s.points >= POINTS_NEEDED) {
                    s.points -= POINTS_NEEDED;
                    blast(unit, s);
                }
            }
        }
        s.hp = unit.health;
    }

    private static void fireBurst(Unit unit, WeaponMount mount) {
        Weapon weapon = mount.weapon;
        mindustry.entities.bullet.BulletType bullet = weapon.bullet;
        float base = unit.rotation - 90f;
        float mx = unit.x + Angles.trnsx(base, weapon.x, weapon.y);
        float my = unit.y + Angles.trnsy(base, weapon.x, weapon.y);
        float wr = base + (weapon.rotate ? mount.rotation : weapon.baseRotation);
        float bx = mx + Angles.trnsx(wr, weapon.shootX, weapon.shootY);
        float by = my + Angles.trnsy(wr, weapon.shootX, weapon.shootY);
        float angle = weapon.rotate
            ? wr + 90f
            : Angles.angle(bx, by, mount.aimX, mount.aimY) + (unit.rotation - unit.angleTo(mount.aimX, mount.aimY));
        float half = (BURST_BULLETS - 1) / 2f;
        for (int i = 0; i < BURST_BULLETS; i++) {
            bullet.create(unit, unit.team, bx, by, angle + (i - half) * BURST_SPREAD);
        }
    }

    private static boolean fireBeam(Unit unit, State s) {
        float ox = unit.x + Angles.trnsx(unit.rotation, BEAM_FORWARD);
        float oy = unit.y + Angles.trnsy(unit.rotation, BEAM_FORWARD);
        Team team = unit.team;
        float range = Math.max(unit.type.range, BEAM_MIN_RANGE);

        final Teamc target = Units.closestTarget(team, ox, oy, range, u -> !u.dead && (BEAM_HITS_AIR || !u.isFlying()), b -> true);
        if (target == null) return false;

        final float tx = ((Posc) target).x();
        final float ty = ((Posc) target).y();
        final float damage = BEAM_DAMAGE * (1f + s.rage * RAGE_STEP) * (Mathf.chance(CRIT_CHANCE) ? CRIT_MULTIPLIER : 1f);

        beamFx.at(ox, oy, 0f, beamColor, new Vec2(tx, ty));
        Fx.hitLancer.at(ox, oy, beamColor);
        Fx.scatheExplosion.at(tx, ty);
        Effect.shake(6f, 6f, tx, ty);

        ((Healthc) target).damage(damage);
        Units.nearbyEnemies(team, tx - BEAM_RADIUS, ty - BEAM_RADIUS, BEAM_RADIUS * 2f, BEAM_RADIUS * 2f, u -> {
            if (u != target && !u.dead && Mathf.dst(tx, ty, u.x, u.y) <= BEAM_RADIUS) {
                u.damage(damage);
            }
        });
        return true;
    }

    private static void onShoot(Unit unit, WeaponMount mount, State s, float now) {
        if (now >= s.burstReady && Mathf.chance(BURST_CHANCE)) {
            s.burstReady = now + BURST_COOLDOWN;
            unit.apply(frenzyStatus, FRENZY_TIME);
            fireBurst(unit, mount);
        }
        if (now >= s.beamReady && Mathf.chance(BEAM_CHANCE)) {
            if (fireBeam(unit, s)) s.beamReady = now + BEAM_COOLDOWN;
        }
    }

    private static void trackShots(Unit unit, State s) {
        WeaponMount[] mounts = unit.mounts;
        float now = Time.time;
        for (int i = 0; i < mounts.length; i++) {
            float reload = mounts[i].reload;
            boolean fired = reload > s.reloads[i] + 0.5f;
            s.reloads[i] = reload;
            if (fired) onShoot(unit, mounts[i], s, now);
        }
    }

    private static boolean isOverLimit(Unit unit) {
        return unit.team != Vars.state.rules.waveTeam && unit.team.data().countType(unit.type) > MAX_PER_TEAM;
    }

    private static void rejectExtra(Unit unit) {
        if (!Vars.headless && Vars.ui != null && unit.team == Vars.player.team()) {
            boolean isEn = Core.settings.getString("locale", "").startsWith("en");
            Vars.ui.showInfoToast(isEn ? "[scarlet]Each team can only have 1 Cin![]" : "[scarlet]Mỗi phe chỉ được có 1 unit Cin![]", 4f);
        }
        unit.remove();
    }
}
