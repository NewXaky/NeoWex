package newex.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
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
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.MechUnit;
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
 * Unit Aureon - Robot xung kích 2 chân hạng nặng T5.
 * Trang bị 4 pháo hồ quang điện hội tụ, yêu cầu khóa mục tiêu trước khi khai hỏa.
 */
public final class Aureon {
    private static final Rand rand = new Rand();

    public static UnitType aureon;

    /** Tầm bắn tối đa (42.5 ô). */
    public static final float RANGE = 340f;
    /** Thời gian khóa mục tiêu: 1.5 giây (90 tick). */
    public static final float LOCK_DURATION = 90f;

    // Bảng màu chủ đạo (trắng-xanh / đỏ / vàng / hồ quang điện)
    private static final Color OUTLINE_COL = Color.valueOf("232933");
    private static final Color ARC_OUTER = Color.valueOf("66d9ff");
    private static final Color ARC_CORE = Color.valueOf("eefbff");
    private static final Color LOCK_RED = Color.valueOf("ff3b30");
    private static final Color LOCK_CHARGE = Color.valueOf("38e57d");
    private static final Color RANGE_GOLD = Color.valueOf("ffd166");

    private Aureon() {}

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        final ElectricArcBulletType arcBullet = new ElectricArcBulletType() {{
            // Sát thương mỗi 5 tick (12 lần/giây) -> ~72 DPS/nòng, 4 nòng = ~288 DPS hội tụ + sốc điện
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
            constructor = MechUnit::create;

            localizedName = "Aureon";
            description = "Robot xung kích 2 chân hạng nặng T5 trang bị 4 pháo hồ quang điện hội tụ.";
            details = "Cần 1.5 giây để khóa mục tiêu trước khi phóng 4 luồng sét cao áp hội tụ. Mục tiêu rời khỏi tầm bắn sẽ lập tức mất khóa.";

            // Thông số cân bằng cho Unit 2 chân T5
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

            // Hoạt ảnh bước đi 2 chân (MechUnit)
            mechFrontSway = 1.15f;
            mechSideSway = 0.75f;
            mechStepParticles = true;
            stepShake = 0.6f;

            outlineRadius = 3;
            outlineColor = OUTLINE_COL;

            // Kỹ năng điều khiển khóa mục tiêu + vẽ phạm vi bắn cho riêng người chơi điều khiển
            abilities.add(new TargetLockAbility());

            // Cặp vũ khí trong (2 nòng phía trên/trong gần sọc đỏ)
            weapons.add(createArcWeapon(9.5f, 4.5f, arcBullet));

            // Cặp vũ khí ngoài (2 nòng gắn trên 2 giá cánh hai bên)
            weapons.add(createArcWeapon(18.5f, -1.5f, arcBullet));
        }};
    }

    /** Tạo vũ khí hồ quang điện đối xứng (mirror = true -> 2 vũ khí mỗi lần gọi -> tổng 4 vũ khí). */
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
            shootSound = Sounds.spark;
            loopSound = Sounds.electricHum;
            loopSoundVolume = 0.45f;

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
                // Mất khóa hoặc chưa khóa xong -> ngắt tia điện ngay lập tức
                mount.shoot = false;
                if (mount.bullet != null) {
                    mount.bullet.remove();
                    mount.bullet = null;
                }
                super.update(unit, mount);
                return;
            }

            // Ép nòng súng luôn hướng thẳng vào mục tiêu đã khóa
            Teamc target = lock.lockedTarget;
            if (target != null) {
                mount.aimX = target.x();
                mount.aimY = target.y();
            }

            super.update(unit, mount);

            // Chặn bắn nếu chưa đủ 100% khóa
            if (!locked) {
                mount.shoot = false;
            }
        }
    }

    private static TargetLockAbility getLockAbility(Unit unit) {
        for (Ability a : unit.abilities) {
            if (a instanceof TargetLockAbility) return (TargetLockAbility) a;
        }
        return null;
    }

    // ================================================================== Đạn tia điện giật hội tụ (giống video 1)

    public static class ElectricArcBulletType extends ContinuousBulletType {
        @Override
        public void update(Bullet b) {
            if (!(b.owner instanceof Unit)) {
                b.remove();
                return;
            }
            Unit u = (Unit) b.owner;
            TargetLockAbility lock = getLockAbility(u);

            // Nếu unit chết, mất khóa, hoặc mục tiêu ra khỏi tầm bắn -> hủy tia điện ngay
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

            // Cập nhật góc tia điện từ miệng súng tới thẳng tâm mục tiêu
            b.rotation(b.angleTo(target));
            b.fdata = b.dst(target);

            // Gây sát thương định kỳ trực tiếp lên mục tiêu đang khóa
            if (b.timer(1, damageInterval)) {
                if (target instanceof Hitboxc) {
                    Hitboxc h = (Hitboxc) target;
                    h.collision(b, target.x(), target.y());
                    Damage.collidePoint(b, b.team, hitEffect, target.x(), target.y());
                } else {
                    Damage.collidePoint(b, b.team, hitEffect, target.x(), target.y());
                }
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

            // Tạo hiệu ứng tia sét gãy khúc liên tục biến thiên theo thời gian
            int segments = Mathf.clamp((int) (dst / 16f), 5, 22);
            long seed = (long) (Time.time * 0.45f) + b.id * 97L;
            rand.setSeed(seed);

            // Vẽ 2 lớp: hào quang xanh điện bên ngoài + lõi sét trắng sáng bên trong
            for (int pass = 0; pass < 2; pass++) {
                rand.setSeed(seed);
                if (pass == 0) {
                    Draw.color(ARC_OUTER, 0.55f);
                    Lines.stroke(4.2f + Mathf.absSin(Time.time, 2f, 1.2f));
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
                        // Độ lệch giật mạnh nhất ở đoạn giữa tia sét, hội tụ chính xác ở 2 đầu
                        float envelope = Mathf.sin(t * Mathf.pi);
                        float jitter = rand.range(11f) * envelope;
                        curX += perpX * jitter;
                        curY += perpY * jitter;
                    }

                    Lines.line(prevX, prevY, curX, curY);

                    // Vẽ nhánh tia lửa phụ nhỏ ở lớp lõi
                    if (pass == 1 && i < segments && rand.chance(0.28f)) {
                        float branchAng = ang + rand.range(55f);
                        float branchLen = rand.random(8f, 18f);
                        Lines.lineAngle(curX, curY, branchAng, branchLen);
                    }

                    prevX = curX;
                    prevY = curY;
                }
            }

            // Quầng sáng ở miệng nòng và điểm hội tụ trên mục tiêu
            Draw.color(ARC_OUTER, 0.6f);
            Fill.circle(x1, y1, 4.5f);
            Fill.circle(x2, y2, 6.5f);
            Draw.color(ARC_CORE);
            Fill.circle(x1, y1, 2.2f);
            Fill.circle(x2, y2, 3.2f);
            Draw.reset();
        }
    }

    // ================================================================== Hệ thống khóa mục tiêu & Drawer (giống video 1 & 2)

    public static class TargetLockAbility extends Ability {
        public Teamc lockedTarget = null;
        public float lockProgress = 0f; // 0.0 -> 1.0

        public boolean isLocked() {
            return lockedTarget != null && isValidTarget(lockedTarget) && lockProgress >= 1f;
        }

        private boolean isValidTarget(Teamc t) {
            if (t == null) return false;
            if (t instanceof Unit) {
                Unit u = (Unit) t;
                return u.isValid() && !u.dead && u.hittable();
            }
            if (t instanceof mindustry.gen.Building) {
                mindustry.gen.Building b = (mindustry.gen.Building) t;
                return b.isValid() && !b.dead;
            }
            return true;
        }

        @Override
        public void update(Unit unit) {
            // 1. Kiểm tra mục tiêu hiện tại có còn sống và nằm trong tầm bắn không
            if (lockedTarget != null) {
                if (!isValidTarget(lockedTarget) || unit.dst(lockedTarget) > RANGE) {
                    // Ra khỏi tầm bắn hoặc mục tiêu bị tiêu diệt -> mất khóa ngay lập tức
                    lockedTarget = null;
                    lockProgress = 0f;
                }
            }

            // 2. Nếu người chơi đang điều khiển trực tiếp: ưu tiên mục tiêu gần con trỏ chuột trong tầm bắn
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

            // 3. Nếu chưa có mục tiêu, tự động tìm kẻ địch gần nhất trong tầm bắn
            if (lockedTarget == null) {
                lockedTarget = Units.closestTarget(unit.team, unit.x, unit.y, RANGE,
                    u -> u.checkTarget(true, true),
                    b -> true
                );
                lockProgress = 0f;
            }

            // 4. Nạp tiến trình khóa mục tiêu nếu đang giữ mục tiêu trong phạm vi bắn
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
            // CHỈ vẽ phạm vi bắn và tâm khóa mục tiêu khi chính player đang nhập vào điều khiển unit này
            if (Vars.player == null || Vars.player.unit() != unit) return;

            Draw.draw(Layer.overlayUI, () -> {
                drawFiringRange(unit);
                if (lockedTarget != null && isValidTarget(lockedTarget)) {
                    drawLockReticle(unit, lockedTarget, lockProgress);
                }
            });
        }

        /** Vẽ vòng phạm vi bắn xung quanh unit (chỉ player điều khiển mới thấy). */
        private void drawFiringRange(Unit unit) {
            float x = unit.x, y = unit.y;

            // Vòng viền mờ bên ngoài
            Draw.color(RANGE_GOLD, 0.16f);
            Lines.stroke(3f);
            Lines.circle(x, y, RANGE);

            // Vòng nét đứt xoay nhẹ sắc nét
            Draw.color(ARC_OUTER, 0.55f);
            Lines.stroke(1.3f);
            Lines.dashCircle(x, y, RANGE);

            // 4 vạch đánh dấu hướng trên vòng tầm bắn
            for (int i = 0; i < 4; i++) {
                float a = i * 90f + Time.time * 0.15f;
                float rx = x + Angles.trnsx(a, RANGE);
                float ry = y + Angles.trnsy(a, RANGE);
                Drawf.tri(rx, ry, 5f, 7f, a + 180f);
            }
            Draw.reset();
        }

        /**
         * Vẽ khung khóa mục tiêu phong cách War Robots (như video 1 & video 2):
         * - Hình thoi đỏ bên ngoài + 2 ngoặc mũi tên trái/phải thu hẹp dần khi đang khóa.
         * - Khung vuông đỏ bên trong với 4 tam giác chỉ vào tâm.
         * - Thanh tiến trình khóa mục tiêu bên cạnh.
         */
        private void drawLockReticle(Unit unit, Teamc target, float progress) {
            float tx = target.x();
            float ty = target.y();
            boolean locked = progress >= 1f;

            float baseRadius = (target instanceof Hitboxc)
                ? Math.max(((Hitboxc) target).hitSize() * 0.65f, 14f)
                : 16f;

            // Khi chưa khóa xong (0 -> 1), khung ngoài phóng to và khép dần vào tâm
            float expand = (1f - progress) * 22f;
            float pulse = locked ? Mathf.absSin(Time.time, 3f, 1.5f) : 0f;
            float sqSize = baseRadius + pulse;
            float diamondSize = (baseRadius + 5f) + expand;

            Color mainCol = locked ? LOCK_RED : LOCK_CHARGE;

            // 1. Khung vuông bên trong + 4 mũi tên tam giác hướng vào tâm
            Draw.color(LOCK_RED, locked ? 0.95f : 0.75f);
            Lines.stroke(locked ? 2.0f : 1.5f);
            Lines.square(tx, ty, sqSize, 0f);

            float triSize = 4.2f;
            Drawf.tri(tx, ty + sqSize + 2f, triSize, triSize, 270f); // Trên chỉ xuống
            Drawf.tri(tx, ty - sqSize - 2f, triSize, triSize, 90f);  // Dưới chỉ lên
            Drawf.tri(tx - sqSize - 2f, ty, triSize, triSize, 0f);   // Trái chỉ phải
            Drawf.tri(tx + sqSize + 2f, ty, triSize, triSize, 180f); // Phải chỉ trái

            // 2. Khung hình thoi xoay 45 độ bên ngoài thu hẹp theo tiến trình khóa
            Draw.color(mainCol, 0.85f);
            Lines.stroke(1.6f);
            Lines.square(tx, ty, diamondSize, 45f);

            // 3. Hai ngoặc chữ V (< >) hai bên khép lại khi khóa
            float bracketOffset = sqSize + 9f + expand * 1.2f;
            float bracketH = 7f;
            Lines.line(tx - bracketOffset, ty + bracketH, tx - bracketOffset - 5f, ty);
            Lines.line(tx - bracketOffset - 5f, ty, tx - bracketOffset, ty - bracketH);

            Lines.line(tx + bracketOffset, ty + bracketH, tx + bracketOffset + 5f, ty);
            Lines.line(tx + bracketOffset + 5f, ty, tx + bracketOffset, ty - bracketH);

            // 4. Thanh tiến trình khóa dọc bên phải mục tiêu (giống thanh xanh/đỏ trong video 2)
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