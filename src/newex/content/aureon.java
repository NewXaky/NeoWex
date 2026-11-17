package newex.content;

import arc.audio.Sound;
import arc.graphics.Blending;
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
import arc.util.Tmp;
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

    // Bộ nhớ đệm vẽ tia (tránh tạo mảng mới mỗi khung hình)
    private static final float[] bx = new float[64];
    private static final float[] by = new float[64];

    /** Dựng đường sét gấp khúc từ (x1,y1) tới (x2,y2); biên độ rung lớn nhất ở giữa, bằng 0 ở hai đầu. */
    private static void buildBolt(int seg, float x1, float y1, float x2, float y2,
                                  float perpX, float perpY, float amp, long seed) {
        rand.setSeed(seed);
        bx[0] = x1; by[0] = y1;
        for (int i = 1; i < seg; i++) {
            float t = (float) i / seg;
            float env = Mathf.sin(t * Mathf.pi);
            float j = rand.range(amp) * env;
            bx[i] = Mathf.lerp(x1, x2, t) + perpX * j;
            by[i] = Mathf.lerp(y1, y2, t) + perpY * j;
        }
        bx[seg] = x2; by[seg] = y2;
    }

    private static void strokeBolt(int seg) {
        for (int i = 0; i < seg; i++) Lines.line(bx[i], by[i], bx[i + 1], by[i + 1]);
    }

    public static UnitType aureon;
    /** Âm thanh điện giật tự tạo: assets/sounds/aureon-zap.ogg */
    public static Sound zapSound;

    /** Tầm bắn tối đa (42.5 ô). */
    public static final float RANGE = 340f;
    /** Thời gian khóa mục tiêu: 1.5 giây (90 tick). */
    public static final float LOCK_DURATION = 90f;
    /** Chỉ bắn khi thân unit hướng về mục tiêu trong góc này (độ). */
    public static final float FIRE_CONE = 20f;

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
            pierce = true;           // tia liên tục không tự hủy khi chạm mục tiêu
            pierceBuilding = true;
            pierceCap = -1;
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
            alternate = false; // bắn đồng thời, không luân phiên trái/phải
            shootCone = 360f;  // không bắt nòng phải xoay đúng góc mới được bắn (tia tự bám mục tiêu)
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
            if (locked && !isFacing(unit, lock.lockedTarget)) locked = false; // chưa quay mặt tới mục tiêu -> không bắn

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

    /** Thân unit đã hướng về mục tiêu (trong FIRE_CONE độ) chưa. */
    private static boolean isFacing(Unit unit, Teamc target) {
        if (target == null) return false;
        return Angles.within(unit.rotation, unit.angleTo(target), FIRE_CONE);
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
            if (!isFacing(u, target)) {
                b.remove();
                return;
            }
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
            float tx = lock.lockedTarget.x(), ty = lock.lockedTarget.y();
            float dst0 = Mathf.dst(x1, y1, tx, ty);
            if (dst0 < 1f) return;

            float ang0 = Angles.angle(x1, y1, tx, ty);
            float perpX = -Mathf.sinDeg(ang0);
            float perpY = Mathf.cosDeg(ang0);

            // Độ lệch ngang của nòng so với thân -> mỗi tia chạm mục tiêu ở một điểm riêng, nhìn rõ đủ 4 tia
            float lateral = (x1 - u.x) * perpX + (y1 - u.y) * perpY;
            float x2 = tx + perpX * lateral * 0.32f;
            float y2 = ty + perpY * lateral * 0.32f;

            float dst = Mathf.dst(x1, y1, x2, y2);
            float ang = Angles.angle(x1, y1, x2, y2);
            float pX = -Mathf.sinDeg(ang);
            float pY = Mathf.cosDeg(ang);

            int seg = Mathf.clamp((int) (dst / 10f), 8, 36);
            long fast = (long) (Time.time * 0.9f) + b.id * 131L;
            float pulse = 0.5f + 0.5f * Mathf.sin(Time.time * 0.35f + b.id);

            // nòng trong sáng/trắng hơn, nòng ngoài xanh đậm hơn -> dễ phân biệt từng tia
            float inner = Mathf.clamp(1f - Math.abs(lateral) / 60f, 0f, 1f);
            Color beam = Tmp.c1.set(ARC_OUTER).lerp(Color.white, inner * 0.35f);

            float zPrev = Draw.z();
            Draw.z(Layer.bullet + 1f);

            buildBolt(seg, x1, y1, x2, y2, pX, pY, 6.5f, fast);

            // vầng sáng mảnh
            Draw.blend(Blending.additive);
            Draw.color(beam, 0.16f);
            Lines.stroke(4.2f + pulse * 0.8f);
            strokeBolt(seg);
            Draw.blend();

            // thân tia + lõi
            Draw.color(beam, 0.95f);
            Lines.stroke(1.7f + pulse * 0.4f);
            strokeBolt(seg);
            Draw.color(ARC_CORE, 1f);
            Lines.stroke(0.75f);
            strokeBolt(seg);

            // nhánh sét nhỏ, thưa
            rand.setSeed(fast + 777L);
            for (int i = 2; i < seg - 1; i++) {
                if (!rand.chance(0.16f)) continue;
                float side = rand.chance(0.5f) ? 1f : -1f;
                float a = ang + side * rand.random(30f, 70f);
                float len = rand.random(8f, 18f);
                float mx = bx[i] + Angles.trnsx(a, len * 0.55f);
                float my = by[i] + Angles.trnsy(a, len * 0.55f);
                float ex = mx + Angles.trnsx(a + rand.range(40f), len * 0.45f);
                float ey = my + Angles.trnsy(a + rand.range(40f), len * 0.45f);
                Draw.color(beam, 0.85f);
                Lines.stroke(0.9f);
                Lines.line(bx[i], by[i], mx, my);
                Lines.line(mx, my, ex, ey);
            }

            // hạt sáng chạy dọc tia
            for (int k = 0; k < 3; k++) {
                float t = (Time.time * 0.016f + k * 0.333f + (b.id % 7) * 0.13f) % 1f;
                float f = t * seg;
                int idx = Mathf.clamp((int) f, 0, seg - 1);
                float fr = f - idx;
                float px = Mathf.lerp(bx[idx], bx[idx + 1], fr);
                float py = Mathf.lerp(by[idx], by[idx + 1], fr);
                Draw.color(ARC_CORE, 0.95f);
                Fill.circle(px, py, 1.5f);
            }

            // đầu nòng: chấm sáng nhỏ + vòng mảnh
            Draw.color(beam, 0.55f);
            Fill.circle(x1, y1, 3.2f + pulse);
            Draw.color(ARC_CORE);
            Fill.circle(x1, y1, 1.5f);
            Draw.color(beam, 0.8f);
            Lines.stroke(0.8f);
            Lines.circle(x1, y1, 4.5f + pulse * 1.5f);

            // điểm trúng: nhỏ gọn, mỗi tia một điểm
            Draw.color(beam, 0.5f);
            Fill.circle(x2, y2, 3.6f);
            Draw.color(ARC_CORE);
            Fill.circle(x2, y2, 1.6f);
            float rf = (Time.time * 0.05f + (b.id % 5) * 0.2f) % 1f;
            Draw.color(beam, 1f - rf);
            Lines.stroke(1.1f * (1f - rf));
            Lines.circle(x2, y2, 3f + rf * 11f);

            rand.setSeed(fast + 4242L);
            Draw.color(ARC_CORE, 0.9f);
            Lines.stroke(0.9f);
            for (int i = 0; i < 4; i++) {
                float a = rand.random(360f);
                Lines.lineAngle(x2 + Angles.trnsx(a, 3f), y2 + Angles.trnsy(a, 3f), a, rand.random(4f, 10f));
            }

            Drawf.light(x1, y1, 16f, beam, 0.3f);
            Drawf.light(x2, y2, 24f + pulse * 4f, beam, 0.35f);

            Draw.z(zPrev);
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