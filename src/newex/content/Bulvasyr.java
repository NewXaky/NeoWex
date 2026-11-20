package newex.content;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
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
 * Unit Bulvasyr - Cơ giáp 2 chân hạng nặng mang khiên vật lý mạn trái và 3 cụm pháo tên lửa tổ ong.
 * - Khóa mục tiêu & hiệu ứng draw giống Aureon (1.5s).
 * - Khai hỏa 2 đợt loạt tên lửa cong tự dẫn hướng, nổ diện rộng, bỏ qua giáp, nạp 5 giây.
 * - Khiên vật lý bên hông trái độ bền 500% HP, ưu tiên chịu sát thương trước, chỉ reset khi chết hoặc đổi unit.
 */
public final class Bulvasyr {
    public static UnitType bulvasyr;
    public static Sound launchSound;

    /** Tầm khóa & tầm bắn (47.5 ô ~ 500m). */
    public static final float RANGE = 380f;
    /** Thời gian khóa mục tiêu giống Aureon: 1.5 giây (90 tick). */
    public static final float LOCK_DURATION = 90f;
    /** Góc quay thân tối đa cho phép khai hỏa về phía mục tiêu đã khóa. */
    public static final float FIRE_CONE = 32f;

    /** Thông số loạt đạn (2 đợt nối tiếp, mỗi đợt 4 tên lửa/khẩu, nạp nguyên ổ 5 giây). */
    public static final int BURSTS_PER_CLIP = 2;
    public static final int ROCKETS_PER_BURST = 4;
    public static final float ROCKET_STAGGER = 3.5f;   // độ trễ giữa từng quả trong 1 đợt (tick)
    public static final float BURST_INTERVAL = 56f;    // ~0.93s giữa đợt 1 và đợt 2 (khớp video)
    public static final float FULL_RELOAD = 300f;

    /** Đạn xoắn ốc (nhìn từ trên xuống): lệch ngang theo sin quanh trục bay, biên độ nhỏ dần khi tới gần mục tiêu. */
    public static final float SWIRL_AMP = 11f;        // biên độ lệch ngang tối đa (đơn vị thế giới)
    public static final float SWIRL_SPEED = 0.2f;     // tốc độ xoay (rad/tick), ~2 vòng trong hành trình
    public static final float SWIRL_RAMP = 14f;       // tick đầu: biên độ tăng dần từ 0 để đạn rời nòng thẳng
    public static final float SWIRL_END = 60f;        // cách mục tiêu bao nhiêu thì bắt đầu thu hẹp xoắn
    public static final float SWIRL_DEPTH = 0.22f;    // phóng to/thu nhỏ đạn theo pha, giả hiệu ứng chiều sâu 3D      // 5.0s nạp toàn bộ ổ đạn

    /** Chỉ số Tấn công chuẩn (đồng bộ công thức Aureon/NewModePvp). */
    public static final float BASE_ATK = 320f;
    /** Sát thương trực tiếp & nổ của mỗi quả tên lửa (% Tấn công). */
    public static final float ROCKET_DIRECT_RATIO = 0.22f;
    public static final float ROCKET_SPLASH_RATIO = 0.34f;

    /** Thông số Khiên Vật Lý bên hông trái. */
    public static final float SHIELD_HP_MULT = 5.0f;         // 500% HP của unit
    public static final float SHIELD_ANGLE_OFFSET = 90f;     // +90 độ = mạn TRÁI so với hướng quay của unit
    public static final float SHIELD_HALF_ARC = 65f;         // che phủ cung 130 độ dọc toàn bộ hông trái

    // Bảng màu
    private static final Color OUTLINE_COL = Color.valueOf("232933");
    private static final Color ARC_OUTER = Color.valueOf("66d9ff");
    private static final Color LOCK_RED = Color.valueOf("ff3b30");
    private static final Color LOCK_CHARGE = Color.valueOf("38e57d");
    private static final Color RANGE_GOLD = Color.valueOf("ffd166");

    private static final Color MISSILE_GOLD = Color.valueOf("ffcf4d");
    private static final Color MISSILE_ORANGE = Color.valueOf("ff7b29");
    private static final Color MISSILE_CORE = Color.valueOf("fff4cc");

    private static final Color SHIELD_BLUE = Color.valueOf("4654c8");
    private static final Color SHIELD_STEEL = Color.valueOf("9ca6ba");
    private static final Color SHIELD_RED = Color.valueOf("d64550");
    private static final Color SHIELD_SPARK = Color.valueOf("ffe394");

    private static boolean initialized = false;
    private static TextureRegion shieldRegion;

    private static Effect shieldHitFx;
    private static Effect shieldBreakFx;
    private static Effect rocketMuzzleFx;
    private static Effect rocketHitFx;

    private Bulvasyr() {}

    /** Gọi từ NexMod() constructor. */
    public static void init() {
        if (initialized) return;
        initialized = true;

        // Lớp 2: Bắt sát thương tia/laser/đạn tức thời (như Aureon, Meltdown, Zeralion...) đánh từ mạn có khiên
        Events.on(UnitDamageEvent.class, e -> {
            if (!(e.unit instanceof BulvasyrUnit) || e.bullet == null) return;
            ((BulvasyrUnit) e.unit).handleBulletDamageEvent(e.bullet);
        });
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        launchSound = Vars.headless ? Sounds.none : Sounds.shootSmite;

        shieldHitFx = new Effect(16f, e -> {
            Draw.color(Color.white, SHIELD_SPARK, e.fin());
            Lines.stroke(1.6f * e.fout());
            Lines.circle(e.x, e.y, 2f + e.fin() * 10f);
            Angles.randLenVectors(e.id, 5, 14f * e.fin(), e.rotation, 65f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4.5f * e.fout())
            );
        });

        shieldBreakFx = new Effect(36f, 120f, e -> {
            Draw.color(SHIELD_SPARK, SHIELD_BLUE, e.fin());
            Lines.stroke(3.2f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 34f);
            Angles.randLenVectors(e.id, 14, 38f * e.finpow(), (x, y) -> {
                Draw.color(SHIELD_BLUE, SHIELD_STEEL, Mathf.randomSeed(e.id + (int) x));
                Fill.poly(e.x + x, e.y + y, 4, 4.5f * e.fout(), e.rotation + x * 4f);
            });
            Draw.color(Color.white, e.fout());
            Fill.circle(e.x, e.y, 8f * e.fout());
        });

        rocketMuzzleFx = new Effect(18f, e -> {
            Draw.color(MISSILE_CORE, MISSILE_ORANGE, e.fin());
            Angles.randLenVectors(e.id, 5, 16f * e.fin(), e.rotation, 24f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 2.8f * e.fout())
            );
            Draw.color(Color.gray, 0.45f * e.fout());
            Angles.randLenVectors(e.id + 9, 4, 12f * e.fin(), e.rotation + 180f, 35f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 3.5f * e.fout())
            );
        });

        rocketHitFx = new Effect(24f, 80f, e -> {
            Draw.color(MISSILE_CORE, MISSILE_ORANGE, e.fin());
            Lines.stroke(2.2f * e.fout());
            Lines.circle(e.x, e.y, 4f + e.fin() * 26f);

            Draw.color(MISSILE_GOLD, MISSILE_ORANGE, e.fin());
            Angles.randLenVectors(e.id, 7, 24f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 3.8f * e.fout())
            );

            Draw.color(Color.valueOf("8be9ff"), Color.white, e.fout());
            Fill.circle(e.x, e.y, 5.5f * e.fout());
        });

        final SalvoMissileBulletType missileBullet = new SalvoMissileBulletType();

        bulvasyr = new UnitType("bulvasyr") {{
            constructor = BulvasyrUnit::new;

            localizedName = "Bulvasyr";
            description = "Cơ giáp 2 chân hạng nặng trang bị khiên vật lý mạn trái (500% HP) và 3 cụm pháo tên lửa tổ ong tự dẫn hướng.";
            details = "Khóa mục tiêu trong 1.5 giây giống Aureon rồi phóng 2 đợt liên tiếp gồm 24 tên lửa cong tự dẫn hướng, nổ diện rộng và bỏ qua giáp (nạp lại 5 giây). "
                + "Tấm khiên vật lý bên hông trái có độ bền bằng 500% HP của unit, ưu tiên nhận sát thương từ hướng hông trái trước và chỉ hồi phục khi unit bị tiêu diệt hoặc đổi unit.";

            health = 46000f;
            armor = 20f;
            hitSize = 34f;
            speed = 0.72f;
            rotateSpeed = 2.6f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;
            envDisabled = Env.none;
            drawCell = false;

            // Cơ giáp 2 chân bước luân phiên (giống ảnh 1 & 2)
            hovering = false;
            allowLegStep = true;
            legCount = 2;
            legGroupSize = 1;
            legLength = 22f;
            legForwardScl = 0.85f;
            legMoveSpace = 1.4f;
            legBaseOffset = 7f;
            legExtension = -2f;
            legSpeed = 0.25f;
            stepShake = 0.7f;

            outlineRadius = 3;
            outlineColor = OUTLINE_COL;

            abilities.add(new TargetLockAbility());
            abilities.add(new PhysicalShieldAbility());

            // 3 khe pháo đặt bất đối xứng ở phần trên & hông PHẢI (x >= 0 là bên phải, đối diện tấm khiên bên trái)
            weapons.add(createSalvoWeapon(4.2f, 6.0f, missileBullet));   // khe trên-giữa
            weapons.add(createSalvoWeapon(21.0f, 7.8f, missileBullet));  // khe trên-phải
            weapons.add(createSalvoWeapon(13.5f, -8.1f, missileBullet)); // khe dưới-phải
        }};
    }

    private static Weapon createSalvoWeapon(float wx, float wy, SalvoMissileBulletType missileBullet) {
        return new LockSalvoWeapon("newex-bulvasyr-weapon") {{
            x = wx;
            y = wy;
            mirror = false; // 3 khe bất đối xứng đúng ảnh 1 & 2
            top = true;
            rotate = true;
            rotateSpeed = 7f;
            rotationLimit = 80f;
            shootCone = 360f;
            layerOffset = 0.04f;
            shootX = 0f;
            shootY = 9f;
            recoil = 2.6f;
            recoilTime = 24f;
            reload = FULL_RELOAD;
            shootSound = Sounds.none; // âm thanh tự phát theo từng đợt tên lửa
            ejectEffect = Fx.none;
            bullet = missileBullet;
        }};
    }

    static float calcAtk(Unit u) {
        float atk = BASE_ATK + NewModePvp.skillFlatAtk(u);
        return atk * Math.max(0.1f, u.damageMultiplier);
    }

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

    // ================================================================== Custom LegsUnit với Khiên Vật Lý mạn trái

    public static class BulvasyrUnit extends LegsUnit {
        public float physShieldMax = -1f;
        public float physShieldHp = 0f;
        public boolean physShieldDamaged = false;
        public float shieldFlash = 0f;
        private int ageTicks = 0;

        /** Theo dõi viên đạn vừa va chạm gần nhất để ngăn double-deduct với UnitDamageEvent. */
        private int lastHandledBulletId = -1;
        private float hpBeforeRaw = -1f;
        private float shieldBeforeRaw = -1f;
        private int lastRawTick = -1;

        public void ensureShieldInit() {
            if (maxHealth <= 0f) return;
            float targetMax = maxHealth * SHIELD_HP_MULT;
            // Khởi tạo lần đầu; nếu NewModePvp cập nhật maxHealth ngay lúc mới spawn (chưa mất độ bền khiên), cập nhật theo 500% HP
            if (physShieldMax < 0f || (!physShieldDamaged && ageTicks < 20 && Math.abs(physShieldMax - targetMax) > 1f)) {
                physShieldMax = targetMax;
                physShieldHp = targetMax;
            }
        }

        public float shieldRatio() {
            return physShieldMax > 0f ? Mathf.clamp(physShieldHp / physShieldMax) : 0f;
        }

        public boolean isShieldActive() {
            return physShieldHp > 0.01f;
        }

        /** Kiểm tra góc tấn công (từ tâm unit hướng ra nguồn sát thương) có rơi vào mạn trái có khiên không. */
        public boolean isAngleCoveredByShield(float angleFromUnitToSource) {
            float shieldFacing = rotation + SHIELD_ANGLE_OFFSET;
            return Angles.within(shieldFacing, angleFromUnitToSource, SHIELD_HALF_ARC);
        }

        /**
         * Hấp thụ sát thương vào khiên vật lý trước.
         * Trả về lượng sát thương thực sự đã được khiên chặn.
         */
        public float absorbPhysicalShield(float dmg, float hitX, float hitY, float hitAngle) {
            ensureShieldInit();
            if (dmg <= 0f || physShieldHp <= 0f) return 0f;

            physShieldDamaged = true;
            shieldFlash = 1f;

            float absorbed = Math.min(physShieldHp, dmg);
            physShieldHp -= absorbed;

            if (physShieldHp <= 0.01f) {
    physShieldHp = 0f;
    shieldBreakFx.at(hitX, hitY, rotation + SHIELD_ANGLE_OFFSET);
    if (!Vars.headless) Sounds.explosion.at(hitX, hitY, 0.85f, 0.9f);
} else {
                shieldHitFx.at(hitX, hitY, hitAngle);
            }
            return absorbed;
        }

        @Override
        public void update() {
            ageTicks++;
            ensureShieldInit();
            if (shieldFlash > 0f) shieldFlash = Math.max(0f, shieldFlash - Time.delta / 10f);

            // Lớp 1: Chủ động chặn đạn bay tiến vào từ mạn trái trước khi chạm thân
            if (isShieldActive() && !dead) {
                interceptLeftFlankBullets();
            }

            super.update();
        }

        private void interceptLeftFlankBullets() {
            float r = hitSize * 0.95f + 10f;
            float shieldFacing = rotation + SHIELD_ANGLE_OFFSET;
            float plateX = x + Angles.trnsx(shieldFacing, hitSize * 0.45f);
            float plateY = y + Angles.trnsy(shieldFacing, hitSize * 0.45f);

            Groups.bullet.intersect(x - r, y - r, r * 2f, r * 2f, b -> {
                if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return;
                if (b.id == lastHandledBulletId) return;
                // Bỏ qua đạn tia liên tục ở đây (sẽ xử lý ở Lớp 2 qua UnitDamageEvent)
                if (b.type instanceof mindustry.entities.bullet.ContinuousBulletType) return;

                // Kiểm tra đạn nằm ở phía mạn trái và đang bay hướng về phía unit
                float angToBullet = Angles.angle(x, y, b.x, b.y);
                float incomingSide = b.rotation() + 180f;
                boolean bulletOnLeft = isAngleCoveredByShield(angToBullet) || isAngleCoveredByShield(incomingSide);
                if (!bulletOnLeft) return;

                float dstPlate = Mathf.dst(plateX, plateY, b.x, b.y);
                float dstCenter = Mathf.dst(x, y, b.x, b.y);
                if (dstPlate > hitSize * 0.82f && dstCenter > hitSize * 0.72f) return;

                float totalDmg = Math.max(0f, b.damage) + (b.type.splashDamage > 0f ? b.type.splashDamage * 0.5f : 0f);
                if (totalDmg <= 0f) return;

                lastHandledBulletId = b.id;
                float absorbed = absorbPhysicalShield(totalDmg, b.x, b.y, angToBullet);
                float overflow = totalDmg - absorbed;

                if (overflow <= 0.01f) {
                    b.absorb();
                } else {
                    // Khiên vừa vỡ: chỉ để phần sát thương dư đi tiếp
                    b.damage = overflow;
                }
            });
        }

        @Override
        public void rawDamage(float amount) {
            // Lưu trạng thái trước khi trừ máu để Lớp 2 có thể hoàn lại nếu UnitDamageEvent xác nhận trúng mạn trái
            hpBeforeRaw = health;
            shieldBeforeRaw = shield;
            lastRawTick = (int) Time.time;
            super.rawDamage(amount);
        }

        /** Lớp 2: Xử lý tia/laser/đạn tức thời đánh từ hướng mạn trái thông qua UnitDamageEvent. */
        public void handleBulletDamageEvent(Bullet b) {
            if (dead || !isShieldActive() || b == null || b.team == team) return;
            if (b.id == lastHandledBulletId) return;
            if (lastRawTick != (int) Time.time || hpBeforeRaw < 0f) return;

            // Xác định hướng nguồn tấn công (ưu tiên vị trí chủ sở hữu cho laser/tia sét)
            float srcX = b.x, srcY = b.y;
            if (b.owner instanceof Position) {
                Position p = (Position) b.owner;
                srcX = p.getX();
                srcY = p.getY();
            }
            float angToSource = Angles.angle(x, y, srcX, srcY);
            if (!isAngleCoveredByShield(angToSource)) return;

            float lostHp = Math.max(0f, hpBeforeRaw - health);
            float lostEnergyShield = Math.max(0f, shieldBeforeRaw - shield);
            float totalLost = lostHp + lostEnergyShield;
            if (totalLost <= 0f) return;

            float plateX = x + Angles.trnsx(rotation + SHIELD_ANGLE_OFFSET, hitSize * 0.52f);
            float plateY = y + Angles.trnsy(rotation + SHIELD_ANGLE_OFFSET, hitSize * 0.52f);
            float absorbed = absorbPhysicalShield(totalLost, plateX, plateY, angToSource);

            // Hoàn trả đúng phần sát thương đã bị trừ oan vào HP / khiên năng lượng
            float refundHp = Math.min(lostHp, absorbed);
            health = Math.min(maxHealth, health + refundHp);
            float rem = absorbed - refundHp;
            if (rem > 0f) {
                shield += Math.min(lostEnergyShield, rem);
            }
            if (health > 0f) dead = false;
        }
    }

    // ================================================================== Vũ khí 2 đợt loạt tên lửa (chỉ bắn khi đã khóa)

    public static class SalvoMount extends WeaponMount {
        public int burstsLeft = BURSTS_PER_CLIP;

        public SalvoMount(Weapon weapon) {
            super(weapon);
        }
    }

    public static class LockSalvoWeapon extends Weapon {
        public LockSalvoWeapon(String name) {
            super(name);
            mountType = SalvoMount::new;
        }

        @Override
        public void update(Unit unit, WeaponMount m) {
            SalvoMount mount = (SalvoMount) m;
            TargetLockAbility lock = getLockAbility(unit);
            boolean locked = lock != null && lock.isLocked() && !Zeralion.isBound(unit) && isFacing(unit, lock.lockedTarget);

            // Giảm hồi chiêu đều đặn kể cả khi chưa có mục tiêu (nạp đạn bình thường)
            if (mount.reload > 0f) {
                mount.reload = Math.max(0f, mount.reload - Time.delta * unit.reloadMultiplier);
            }

            if (locked) {
                Teamc target = lock.lockedTarget;
                mount.aimX = target.x();
                mount.aimY = target.y();

                // Xoay nòng về mục tiêu
                float mountX = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
                float mountY = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
                float targetAngle = Angles.angle(mountX, mountY, target.x(), target.y()) - unit.rotation;
                mount.rotation = Angles.moveToward(mount.rotation, Mathf.clamp(Angles.angleDist(0f, targetAngle) <= rotationLimit ? targetAngle : 0f, -rotationLimit, rotationLimit), rotateSpeed * Time.delta);

                if (mount.reload <= 0f) {
                    fireSalvoBurst(unit, mount, target);
                    mount.burstsLeft--;
                    if (mount.burstsLeft <= 0) {
                        mount.burstsLeft = BURSTS_PER_CLIP;
                        mount.reload = FULL_RELOAD; // hết 2 đợt -> nạp nguyên ổ 5 giây
                    } else {
                        mount.reload = BURST_INTERVAL; // nghỉ ~0.93s rồi bắn đợt 2
                    }
                }
            } else {
                // Nếu rời khóa khi đang dở băng đạn và đã hết reload thì giữ sẵn đợt còn lại
                mount.rotation = Angles.moveToward(mount.rotation, 0f, rotateSpeed * 0.5f * Time.delta);
            }

            // Không để vanilla tự bắn đúp
            mount.shoot = false;
            super.update(unit, mount);
        }

        private void fireSalvoBurst(Unit unit, SalvoMount mount, Teamc target) {
            if (unit == null || !unit.isValid()) return;

            for (int i = 0; i < ROCKETS_PER_BURST; i++) {
                final int idx = i;
                Time.run(i * ROCKET_STAGGER, () -> {
                    if (!unit.isValid() || unit.dead) return;

                    float weaponRot = unit.rotation + mount.rotation;
                    float mx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
                    float my = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
                    // 4 nòng nhỏ xếp 2x2 trên khẩu tổ ong
                    float ox = (idx % 2 == 0 ? -2.2f : 2.2f);
                    float oy = shootY + (idx < 2 ? 1.5f : -1.0f);
                    float sx = mx + Angles.trnsx(weaponRot - 90f, ox, oy);
                    float sy = my + Angles.trnsy(weaponRot - 90f, ox, oy);

                    // Góc phóng vồng lệch sang 2 bên tạo chùm tên lửa cong uốn lượn giống video
                    float arcSpread = (idx - 1.5f) * 5f + Mathf.range(3f);
                    float launchAngle = weaponRot + arcSpread;

                    float swirlPhase = idx * (Mathf.PI / 2f) + x * 0.37f;
                    Bullet b = bullet.create(unit, unit.team, sx, sy, launchAngle, -1f, 0.92f + idx * 0.04f, 1f, new SwirlData(target, swirlPhase));
                    if (b != null) {
                        float atk = calcAtk(unit);
                        b.damage = atk * ROCKET_DIRECT_RATIO;
                    }

                    mount.recoil = 1f;
                    rocketMuzzleFx.at(sx, sy, launchAngle);
                    if (idx == 0 && !Vars.headless) {
                        launchSound.at(sx, sy, Mathf.random(0.92f, 1.08f), 0.75f);
                    }
                });
            }
        }
    }

    // ================================================================== Đạn tên lửa cong tự dẫn hướng, nổ & xuyên giáp

    /** Dữ liệu từng quả: mục tiêu, pha xoắn và độ lệch ngang đã áp dụng ở tick trước. */
    public static class SwirlData {
        public Teamc target;
        public float phase, prev;
        public SwirlData(Teamc target, float phase) { this.target = target; this.phase = phase; }
    }

    public static class SalvoMissileBulletType extends MissileBulletType {
        public SalvoMissileBulletType() {
            super(5.6f, 70f);
            lifetime = 95f;
            width = 7.5f;
            height = 13f;
            shrinkY = 0f;
            sprite = "missile-large";

            pierceArmor = true; // Bỏ qua hệ thống phòng thủ (ảnh 3)
            splashDamage = 110f;
            splashDamageRadius = 34f;
            scaledSplashDamage = true;

            homingPower = 0.16f;
            homingRange = RANGE * 1.4f;
            homingDelay = 9f; // bay lệch tạo vòng cung trong 0.15s đầu rồi bẻ lái lao thẳng vào mục tiêu

            keepVelocity = false;
            collidesAir = true;
            collidesGround = true;

            frontColor = MISSILE_CORE;
            backColor = MISSILE_ORANGE;
            trailColor = MISSILE_GOLD;
            trailWidth = 2.1f;
            trailLength = 13;
            trailChance = 0.85f;
            trailEffect = Fx.missileTrail;

            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            hitEffect = rocketHitFx;
            despawnEffect = rocketHitFx;
            hitSound = Sounds.explosion;
        }

        @Override
        public void updateHoming(Bullet b) {
            if (b.time < homingDelay) return;

            SwirlData sd = (b.data instanceof SwirlData) ? (SwirlData) b.data : null;
            Teamc target = sd != null ? sd.target : null;
            if (!isTargetAlive(target)) {
                target = Units.closestTarget(b.team, b.x, b.y, homingRange,
                    e -> e.checkTarget(collidesAir, collidesGround),
                    t -> collidesGround
                );
                if (sd != null) sd.target = target;
            }

            if (target != null) {
                // Tăng dần lực bẻ lái để tên lửa uốn cong mượt rồi lao gắt vào mục tiêu như video
                float ramp = Mathf.clamp((b.time - homingDelay) / 18f, 0.35f, 1.6f);
                b.vel.setAngle(Angles.moveToward(b.rotation(), b.angleTo(target), homingPower * 58f * ramp * Time.delta));
            }
        }

        /** Dịch vị trí đạn ngang quỹ đạo theo sin: nhìn từ trên xuống, xoắn ốc 3D chính là sóng lượn quanh trục bay. */
        private static float swirlOffset(Bullet b, SwirlData sd) {
            float t = b.time;
            float env = Mathf.clamp(t / SWIRL_RAMP);
            if (isTargetAlive(sd.target)) env *= Mathf.clamp((b.dst(sd.target) - 12f) / SWIRL_END);
            return SWIRL_AMP * env * Mathf.sin(sd.phase + t * SWIRL_SPEED);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (!(b.data instanceof SwirlData)) return;
            SwirlData sd = (SwirlData) b.data;
            float off = swirlOffset(b, sd);
            float d = off - sd.prev;
            sd.prev = off;
            float side = b.rotation() + 90f;
            b.x += Angles.trnsx(side, d);
            b.y += Angles.trnsy(side, d);
        }

        @Override
        public void draw(Bullet b) {
            if (!(b.data instanceof SwirlData)) { super.draw(b); return; }
            SwirlData sd = (SwirlData) b.data;
            // Đầu đạn quay theo tiếp tuyến thực của đường xoắn; kích thước đổi theo pha để giống đang vòng xa-gần
            float t = b.time;
            float env = Mathf.clamp(t / SWIRL_RAMP);
            if (isTargetAlive(sd.target)) env *= Mathf.clamp((b.dst(sd.target) - 12f) / SWIRL_END);
            float ph = sd.phase + t * SWIRL_SPEED;
            float lat = SWIRL_AMP * env * SWIRL_SPEED * Mathf.cos(ph);
            float r0 = b.rotation();
            float tilt = Mathf.atan2(lat, speed) * Mathf.radDeg;
            float sc = 1f + SWIRL_DEPTH * env * Mathf.cos(ph);
            float w0 = width, h0 = height;
            width = w0 * sc; height = h0 * sc;
            b.rotation(r0 + tilt);
            super.draw(b);
            b.rotation(r0);
            width = w0; height = h0;
        }

        @Override
        public void hit(Bullet b, float x, float y) {
            // Tính sát thương nổ theo chỉ số Tấn công hiện tại của unit chủ
            float dynSplash = splashDamage;
            if (b.owner instanceof Unit) {
                dynSplash = calcAtk((Unit) b.owner) * ROCKET_SPLASH_RATIO;
            }
            hitEffect.at(x, y, b.rotation(), hitColor);
            hitSound.at(x, y, hitSoundPitch, hitSoundVolume * 0.65f);

            // Gây sát thương nổ xuyên giáp diện rộng (pierceArmor = true)
            Damage.damage(b.team, x, y, splashDamageRadius, dynSplash, true, collidesAir, collidesGround, scaledSplashDamage, b);
        }

        private static boolean isTargetAlive(Teamc t) {
            if (t == null) return false;
            if (t instanceof Unit) {
                Unit u = (Unit) t;
                return u.isValid() && !u.dead && u.hittable();
            }
            if (t instanceof Building) {
                Building b = (Building) t;
                return b.isValid() && !b.dead;
            }
            return false;
        }
    }

    // ================================================================== Nội tại 1: Khóa mục tiêu & Drawer (giống hệt Aureon)

    public static class TargetLockAbility extends Ability {
        public Teamc lockedTarget = null;
        public float lockProgress = 0f;
        public float phase = 0f;
        private boolean chain = false;

        private float takeChain() {
            if (!chain) return 0f;
            chain = false;
            return 1f;
        }

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
            if (Zeralion.isBound(unit)) return;
            phase += Time.delta;

            if (lockedTarget != null) {
                if (!isValidTarget(lockedTarget) || unit.dst(lockedTarget) > RANGE) {
                    if (lockProgress >= 1f && !isValidTarget(lockedTarget)) chain = true;
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
                    lockProgress = takeChain();
                }
            }

            if (lockedTarget == null) {
                lockedTarget = Units.closestTarget(unit.team, unit.x, unit.y, RANGE,
                    u -> u.checkTarget(true, true),
                    b -> true
                );
                lockProgress = lockedTarget != null ? takeChain() : 0f;
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
                float a = i * 90f + phase * 0.15f;
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
            float pulse = locked ? Mathf.absin(phase, 3f, 1.5f) : 0f;
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
            return "Khóa mục tiêu tên lửa";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Tầm khóa & bắn: [white]" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô").row();
            t.add("[lightgray]Thời gian khóa: [white]" + Strings.autoFixed(LOCK_DURATION / 60f, 1) + " " + StatUnit.seconds.localized()).row();
            t.add("[lightgray]Loạt đạn: [white]3 khẩu × 2 đợt × 4 tên lửa dẫn hướng nổ xuyên giáp (nạp 5s)");
        }
    }

    // ================================================================== Nội tại 2: Hiển thị & Thông số Khiên Vật Lý mạn trái

    public static class PhysicalShieldAbility extends Ability {
        @Override
        public void draw(Unit unit) {
            if (!(unit instanceof BulvasyrUnit)) return;
            BulvasyrUnit bu = (BulvasyrUnit) unit;
            if (!bu.isShieldActive()) return;

            float ratio = bu.shieldRatio();
            float shieldFacing = bu.rotation + SHIELD_ANGLE_OFFSET;
            float px = bu.x + Angles.trnsx(shieldFacing, bu.hitSize * 0.48f);
            float py = bu.y + Angles.trnsy(shieldFacing, bu.hitSize * 0.48f);

            float zPrev = Draw.z();
            Draw.z(Layer.groundUnit + 0.08f);

            // Nếu đã có sprite bulvasyr-shield.png thì vẽ sprite; chưa có thì vẽ vector tấm khiên vát chéo đúng ảnh 1 & 2
            if (shieldRegion == null && !Vars.headless) {
                shieldRegion = Core.atlas.find("newex-bulvasyr-shield");
            }

            if (shieldRegion != null && shieldRegion.found()) {
                Draw.color(Tmp.c1.set(Color.white).lerp(SHIELD_SPARK, bu.shieldFlash * 0.7f));
                Draw.rect(shieldRegion, bu.x, bu.y, bu.rotation - 90f);
            } else {
                drawVectorShieldPlate(bu, px, py, ratio);
            }

            // Đường cung cảnh báo vùng bảo vệ khi người chơi điều khiển hoặc khiên vừa chặn đạn
            if ((Vars.player != null && Vars.player.unit() == bu) || bu.shieldFlash > 0.05f) {
                Draw.z(Layer.overlayUI);
                float arcAlpha = (Vars.player != null && Vars.player.unit() == bu ? 0.35f : 0f) + bu.shieldFlash * 0.55f;
                Draw.color(Tmp.c1.set(SHIELD_STEEL).lerp(SHIELD_SPARK, bu.shieldFlash), Mathf.clamp(arcAlpha));
                Lines.stroke(2.0f + bu.shieldFlash * 1.4f);
                Lines.arc(bu.x, bu.y, bu.hitSize * 0.76f, (SHIELD_HALF_ARC * 2f) / 360f, shieldFacing - SHIELD_HALF_ARC);

                // Thanh độ bền khiên vật lý nhỏ ngay cạnh unit
                float bw = bu.hitSize * 1.15f;
                float bh = 3.2f;
                float by = bu.y - bu.hitSize * 0.85f;
                Draw.color(Color.black, 0.65f);
                Fill.rect(bu.x, by, bw + 2f, bh + 2f);
                Draw.color(ratio > 0.3f ? SHIELD_BLUE : SHIELD_RED, 0.95f);
                Fill.rect(bu.x - (bw * (1f - ratio)) * 0.5f, by, bw * ratio, bh);
            }

            Draw.z(zPrev);
            Draw.reset();
        }

        private void drawVectorShieldPlate(BulvasyrUnit bu, float px, float py, float ratio) {
            float rot = bu.rotation;
            float fwdX = Angles.trnsx(rot, 1f), fwdY = Angles.trnsy(rot, 1f);
            float leftX = Angles.trnsx(rot + 90f, 1f), leftY = Angles.trnsy(rot + 90f, 1f);

            // Hình khối tấm khiên vát nhọn chạy dọc mạn trái (khớp vòng đỏ ảnh 1 & 2)
            float x1 = bu.x + leftX * 15f + fwdX * 17f;
            float y1 = bu.y + leftY * 15f + fwdY * 17f;
            float x2 = bu.x + leftX * 21f + fwdX * 4f;
            float y2 = bu.y + leftY * 21f + fwdY * 4f;
            float x3 = bu.x + leftX * 19f - fwdX * 18f;
            float y3 = bu.y + leftY * 19f - fwdY * 18f;
            float x4 = bu.x + leftX * 11f - fwdX * 13f;
            float y4 = bu.y + leftY * 11f - fwdY * 13f;

            // Viền tối kiểu Mindustry
// Viền tối kiểu Mindustry
Draw.color(OUTLINE_COL);
Lines.stroke(3.6f);
Lines.line(x1, y1, x2, y2);
Lines.line(x2, y2, x3, y3);
Lines.line(x3, y3, x4, y4);
Lines.line(x4, y4, x1, y1);

            // Mặt giáp xanh-tím (chuyển sẫm dần khi độ bền thấp, lóe vàng khi trúng đạn)
            Color plateCol = Tmp.c1.set(SHIELD_BLUE).lerp(Color.darkGray, (1f - ratio) * 0.45f).lerp(SHIELD_SPARK, bu.shieldFlash * 0.7f);
            Draw.color(plateCol);
            Fill.quad(x1, y1, x2, y2, x3, y3, x4, y4);

            // Sọc đỏ trang trí + gờ thép
            Draw.color(SHIELD_RED);
            Lines.stroke(1.8f);
            Lines.line(
                Mathf.lerp(x1, x4, 0.35f), Mathf.lerp(y1, y4, 0.35f),
                Mathf.lerp(x2, x3, 0.55f), Mathf.lerp(y2, y3, 0.55f)
            );
            Draw.color(SHIELD_STEEL, 0.9f);
            Lines.stroke(1.2f);
            Lines.line(x1, y1, x2, y2);
            Lines.line(x2, y2, x3, y3);
        }

        @Override
        public String localized() {
            return "Khiên Vật Lý Mạn Trái";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Độ bền khiên: [sky]500% HP[] của unit").row();
            t.add("[lightgray]Vùng che chắn: [white]Mạn trái (±" + (int) SHIELD_HALF_ARC + "°)").row();
            t.add("[lightgray]Cơ chế: [white]Ưu tiên trừ độ bền khiên trước khi trúng mạn trái; không tự hồi, chỉ reset khi bị tiêu diệt hoặc đổi unit");
        }
    }
}