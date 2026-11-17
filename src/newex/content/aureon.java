package newex.content;

import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.LegsUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;
import mindustry.world.meta.StatUnit;

/**
 * Unit Aureon - Robot xung kích 4 chân hạng nặng T5.
 * Trang bị 4 pháo hồ quang điện hội tụ, yêu cầu khóa mục tiêu trước khi khai hỏa.
 */
public final class aureon {
    private static final Rand rand = new Rand();

    public static UnitType aureon;
    /** Âm thanh điện giật tự tạo: assets/sounds/aureon-zap.ogg */
    public static Sound zapSound;

    /** Tầm bắn tối đa (42.5 ô). */
    public static final float RANGE = 340f;
    /** Thời gian khóa mục tiêu: 1.5 giây (90 tick). */
    public static final float LOCK_DURATION = 90f;

    private static final Color OUTLINE_COL = Color.valueOf("232933");
    private static final Color ARC_OUTER = Color.valueOf("66d9ff");
    private static final Color ARC_CORE = Color.valueOf("eefbff");
    private static final Color LOCK_RED = Color.valueOf("ff3b30");
    private static final Color LOCK_CHARGE = Color.valueOf("38e57d");
    private static final Color RANGE_GOLD = Color.valueOf("ffd166");

    private aureon() {}

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        zapSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("aureon-zap");
        final ElectricArcBulletType arcBullet = new ElectricArcBulletType() {{
            damage = 6f;
            damageInterval = 5f;
            length = RANGE;
            rangeOverride = RANGE;
            hitColor = ARC_OUTER;
            status = StatusEffects.shocked;
            statusDuration = 20f;
            pierce = false;
            pierceBuilding = false;
            drawSize = RANGE * 2.2f;
            hitEffect = new Effect(12f, e -> {
                Draw.color(ARC_OUTER, ARC_CORE, e.fout());
                Lines.stroke(1.5f * e.fout());
                Lines.circle(e.x, e.y, 3f + e.fin() * 10f);
                Angles.randLenVectors(e.id, 3, 14f * e.fin(), (x, y) ->
                    Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4f * e.fout())
                );
            });
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
        }};

        aureon = new UnitType("aureon") {{
            constructor = LegsUnit::create;

            localizedName = "Aureon";
            description = "Robot xung kích 4 chân hạng nặng T5 trang bị 4 pháo hồ quang điện hội tụ.";
            details = "Cần 1.5 giây để khóa mục tiêu trước khi phóng 4 luồng sét cao áp hội tụ. Mục tiêu rời khỏi tầm bắn sẽ lập tức mất khóa.";

            health = 22000f;
            armor = 22f;
            hitSize = 38f;
            speed = 0.78f;
            rotateSpeed = 2.2f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;
            envDisabled = Env.none;
            drawCell = false; // sprite đã có chi tiết riêng, không vẽ ô màu đội

            // 4 chân kiểu nhện, chân mảnh
            hovering = true;
            allowLegStep = true;
            legCount = 4;
            legLength = 30f;
            legForwardScl = 0.55f;
            legMoveSpace = 1.2f;
            legBaseOffset = 6f;
            legExtension = -2f;
            stepShake = 0.6f;

            outlineRadius = 3;
            outlineColor = OUTLINE_COL;

            abilities.add(new TargetLockAbility());

            // Cặp vũ khí trong (2 nòng) + cặp vũ khí ngoài (2 nòng) = 4 vũ khí
            weapons.add(createArcWeapon(9.5f, 4.5f, arcBullet));
            weapons.add(createArcWeapon(18.5f, -1.5f, arcBullet));
        }};
    }

    private static Weapon createArcWeapon(float wx, float wy, ElectricArcBulletType arcBullet) {
        return new LockRequiredWeapon("newex-aureon-weapon") {{
            x = wx;
            y = wy;
            mirror = true;
            top = true;
            rotate = true;
            rotateSpeed = 6f;
            rotationLimit = 110f;
            layerOffset = 0.02f;

            shootY = 13f;
            recoil = 1.2f;
            reload = 10f;
            continuous = true;
            alwaysContinuous = true;
            shootSound = zapSound;

            bullet = arcBullet;
        }};
    }

    // ================================================================== Vũ khí chỉ bắn khi đã khóa mục tiêu

    public static class LockRequiredWeapon extends Weapon {
        public LockRequiredWeapon(String name) {
            super(name);
        }

        @Override
        public void update(Unit unit, WeaponMount mount) {
            TargetLockAbility lock = getLockAbility(unit);
            boolean locked = lock != null && lock.isLocked();

            if (!locked) {
                mount.shoot = false;
                if (mount.bullet != null) {
                    mount.bullet.remove();
                    mount.bullet = null;
                }
                super.update(unit, mount);
                return;
            }

            Teamc target = lock.lockedTarget;
            if (target != null) {
                mount.aimX = target.x();
                mount.aimY = target.y();
            }

            super.update(unit, mount);
        }
    }

    private static TargetLockAbility getLockAbility(Unit unit) {
        for (Ability a : unit.abilities) {
            if (a instanceof TargetLockAbility) return (TargetLockAbility) a;
        }
        return null;
    }

    // ================================================================== Đạn tia điện giật hội tụ

    public static class ElectricArcBulletType extends ContinuousBulletType {
        @Override
        public void update(Bullet b) {
            if (!(b.owner instanceof Unit)) {
                b.remove();
                return;
            }
            Unit u = (Unit) b.owner;
            TargetLockAbility lock = getLockAbility(u);

            if (!u.isValid() || lock == null || !lock.isLocked() || lock.lockedTarget == null) {
                b.remove();
                return;
            }

            Teamc target = lock.lockedTarget;
            float dist = u.dst(target);
            if (dist > RANGE + 4f) {
                b.remove();
                return;
            }

            b.rotation(b.angleTo(target));
            b.fdata = b.dst(target);

            if (b.timer(1, damageInterval)) {
                if (target instanceof Hitboxc) {
                    Hitboxc h = (Hitboxc) target;
                    h.collision(b, target.x(), target.y());
                }
                Damage.collidePoint(b, b.team, hitEffect, target.x(), target.y());
            }
        }

        @Override
        public void draw(Bullet b) {
            if (!(b.owner instanceof Unit)) return;
            Unit u = (Unit) b.owner;
            TargetLockAbility lock = getLockAbility(u);
            if (lock == null || !lock.isLocked() || lock.lockedTarget == null) return;

            float x1 = b.x, y1 = b.y;
            float x2 = lock.lockedTarget.x(), y2 = lock.lockedTarget.y();
            float dst = Mathf.dst(x1, y1, x2, y2);
            if (dst < 1f) return;

            float ang = Angles.angle(x1, y1, x2, y2);
            float perpX = -Mathf.sinDeg(ang);
            float perpY = Mathf.cosDeg(ang);

            int segments = Mathf.clamp((int) (dst / 16f), 5, 22);
            long seed = (long) (Time.time * 0.45f) + b.id * 97L;

            for (int pass = 0; pass < 2; pass++) {
                rand.setSeed(seed);
                if (pass == 0) {
                    Draw.color(ARC_OUTER, 0.55f);
                    Lines.stroke(4.2f + Mathf.absin(Time.time, 2f, 1.2f));
                } else {
                    Draw.color(ARC_CORE, 0.95f);
                    Lines.stroke(1.8f);
                }

                float prevX = x1, prevY = y1;
                for (int i = 1; i <= segments; i++) {
                    float t = (float) i / segments;
                    float curX = Mathf.lerp(x1, x2, t);
                    float curY = Mathf.lerp(y1, y2, t);

                    if (i < segments) {
                        float envelope = Mathf.sin(t * Mathf.pi);
                        float jitter = rand.range(11f) * envelope;
                        curX += perpX * jitter;
                        curY += perpY * jitter;
                    }

                    Lines.line(prevX, prevY, curX, curY);

                    if (pass == 1 && i < segments && rand.chance(0.28f)) {
                        float branchAng = ang + rand.range(55f);
                        float branchLen = rand.random(8f, 18f);
                        Lines.lineAngle(curX, curY, branchAng, branchLen);
                    }

                    prevX = curX;
                    prevY = curY;
                }
            }

            Draw.color(ARC_OUTER, 0.6f);
            Fill.circle(x1, y1, 4.5f);
            Fill.circle(x2, y2, 6.5f);
            Draw.color(ARC_CORE);
            Fill.circle(x1, y1, 2.2f);
            Fill.circle(x2, y2, 3.2f);
            Draw.reset();
        }
    }

    // ================================================================== Hệ thống khóa mục tiêu & Drawer

    public static class TargetLockAbility extends Ability {
        public Teamc lockedTarget = null;
        public float lockProgress = 0f;

        public boolean isLocked() {
            return lockedTarget != null && isValidTarget(lockedTarget) && lockProgress >= 1f;
        }

        private boolean isValidTarget(Teamc t) {
            if (t == null) return false;
            if (t instanceof Unit) {
                Unit u = (Unit) t;
                return u.isValid() && !u.dead && u.hittable();
            }
            if (t instanceof Building) {
                Building b = (Building) t;
                return b.isValid() && !b.dead;
            }
            return true;
        }

        @Override
        public void update(Unit unit) {
            if (lockedTarget != null) {
                if (!isValidTarget(lockedTarget) || unit.dst(lockedTarget) > RANGE) {
                    lockedTarget = null;
                    lockProgress = 0f;
                }
            }

            if (unit.isPlayer()) {
                Teamc hovered = Units.closestTarget(unit.team, unit.aimX, unit.aimY, 48f,
                    u -> u.checkTarget(true, true) && unit.within(u, RANGE),
                    b -> unit.within(b, RANGE)
                );
                if (hovered != null && hovered != lockedTarget) {
                    lockedTarget = hovered;
                    lockProgress = 0f;
                }
            }

            if (lockedTarget == null) {
                lockedTarget = Units.closestTarget(unit.team, unit.x, unit.y, RANGE,
                    u -> u.checkTarget(true, true),
                    b -> true
                );
                lockProgress = 0f;
            }

            if (lockedTarget != null) {
                if (lockProgress < 1f) {
                    lockProgress = Mathf.clamp(lockProgress + Time.delta / LOCK_DURATION);
                }
            } else {
                lockProgress = 0f;
            }
        }

        @Override
        public void draw(Unit unit) {
            if (Vars.player == null || Vars.player.unit() != unit) return;

            Draw.draw(Layer.overlayUI, () -> {
                drawFiringRange(unit);
                if (lockedTarget != null && isValidTarget(lockedTarget)) {
                    drawLockReticle(lockedTarget, lockProgress);
                }
            });
        }

        private void drawFiringRange(Unit unit) {
            float x = unit.x, y = unit.y;

            Draw.color(RANGE_GOLD, 0.16f);
            Lines.stroke(3f);
            Lines.circle(x, y, RANGE);

            Draw.color(ARC_OUTER, 0.55f);
            Lines.stroke(1.3f);
            Lines.dashCircle(x, y, RANGE);

            for (int i = 0; i < 4; i++) {
                float a = i * 90f + Time.time * 0.15f;
                float rx = x + Angles.trnsx(a, RANGE);
                float ry = y + Angles.trnsy(a, RANGE);
                Drawf.tri(rx, ry, 5f, 7f, a + 180f);
            }
            Draw.reset();
        }

        private void drawLockReticle(Teamc target, float progress) {
            float tx = target.x();
            float ty = target.y();
            boolean locked = progress >= 1f;

            float baseRadius = (target instanceof Hitboxc)
                ? Math.max(((Hitboxc) target).hitSize() * 0.65f, 14f)
                : 16f;

            float expand = (1f - progress) * 22f;
            float pulse = locked ? Mathf.absin(Time.time, 3f, 1.5f) : 0f;
            float sqSize = baseRadius + pulse;
            float diamondSize = (baseRadius + 5f) + expand;

            Color mainCol = locked ? LOCK_RED : LOCK_CHARGE;

            Draw.color(LOCK_RED, locked ? 0.95f : 0.75f);
            Lines.stroke(locked ? 2.0f : 1.5f);
            Lines.square(tx, ty, sqSize, 0f);

            float triSize = 4.2f;
            Drawf.tri(tx, ty + sqSize + 2f, triSize, triSize, 270f);
            Drawf.tri(tx, ty - sqSize - 2f, triSize, triSize, 90f);
            Drawf.tri(tx - sqSize - 2f, ty, triSize, triSize, 0f);
            Drawf.tri(tx + sqSize + 2f, ty, triSize, triSize, 180f);

            Draw.color(mainCol, 0.85f);
            Lines.stroke(1.6f);
            Lines.square(tx, ty, diamondSize, 45f);

            float bracketOffset = sqSize + 9f + expand * 1.2f;
            float bracketH = 7f;
            Lines.line(tx - bracketOffset, ty + bracketH, tx - bracketOffset - 5f, ty);
            Lines.line(tx - bracketOffset - 5f, ty, tx - bracketOffset, ty - bracketH);

            Lines.line(tx + bracketOffset, ty + bracketH, tx + bracketOffset + 5f, ty);
            Lines.line(tx + bracketOffset + 5f, ty, tx + bracketOffset, ty - bracketH);

            float barX = tx + sqSize + 15f;
            float barH = sqSize * 1.8f;
            float barW = 3f;

            Draw.color(Color.black, 0.55f);
            Fill.rect(barX, ty, barW + 1.5f, barH + 1.5f);

            Draw.color(mainCol, 0.95f);
            float fillH = barH * progress;
            Fill.rect(barX, ty - (barH - fillH) * 0.5f, barW, fillH);

            Draw.reset();
        }

        @Override
        public String localized() {
            return "Khóa mục tiêu hồ quang";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Tầm khóa & bắn: [white]" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô");
            t.row();
            t.add("[lightgray]Thời gian khóa: [white]" + Strings.autoFixed(LOCK_DURATION / 60f, 1) + " " + StatUnit.seconds.localized());
            t.row();
            t.add("[lightgray]Cơ chế: [white]Hội tụ 4 tia sét sau khi khóa; mất khóa ngay nếu mục tiêu rời tầm bắn");
        }
    }
}