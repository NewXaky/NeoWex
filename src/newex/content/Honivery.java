package newex.content;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.Predict;
import mindustry.entities.Sized;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.LegsUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.type.weapons.PointDefenseWeapon;
import mindustry.world.meta.Env;

/**
 * Unit Honivery - chuyển từ mod Multi Dimensional sang NeoWex.
 *
 * <p>Cơ giáp 4 chân, hai súng Gatling xoay nòng (quá nhiệt nếu bắn liền quá lâu) và hai bệ phòng thủ điểm.
 * Sprite nằm ở sprites/units/honivery/.
 */
public final class Honivery {
    public static UnitType honivery;

    private static final Color HONIVERY_COLOR = Color.valueOf("D0C2FF");
    private static final Rand fxRand = new Rand();
    private static final Vec2 fxV = new Vec2();

    private Honivery() {}

    // ================================================================== hiệu ứng

    /** Hiệu ứng nổ nhỏ khi đạn trúng/tan: vòng tròn + vài tia chạy ra, màu theo hitColor của đạn. */
    private static Effect hitBulletFx(float circleRad, int lines, float linesRad) {
        final Interp inter = circleRad > 20f ? Interp.pow3Out : Interp.linear;
        final float da = circleRad > 20f ? 0.5f : 0f;
        final float l = Math.max(0f, (circleRad - 10f) / 4f);
        return new Effect(14f + l, e -> {
            Draw.color(Color.white, e.color, e.fin());
            e.scaled(7f + l / 2f, s -> {
                Draw.alpha(1f - s.fin() * da);
                Lines.stroke(0.1f + inter.apply(1f - s.fin()));
                Lines.circle(e.x, e.y, s.fin(inter) * circleRad);
            });
            Lines.stroke(0.3f + inter.apply(1f - e.fin()));
            Angles.randLenVectors(e.id, lines, e.fin(inter) * linesRad, (x, y) -> {
                float ang = Mathf.angle(x, y);
                Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 3f * (linesRad / 16f) + 0.2f);
            });
            Drawf.light(e.x, e.y, 20f, e.color, 0.6f * e.fout());
        });
    }

    /** Hiệu ứng nòng súng: vài mảnh vuông nhỏ tỏa ra theo hướng bắn. */
    private static Effect shootFx() {
        return new Effect(20f, e -> {
            Draw.color(e.color, Color.white, e.fin() * 0.5f);
            fxRand.setSeed(e.id);
            for (int i = 0; i < 7; i++) {
                float dr = fxRand.range(20f);
                fxV.trns(e.rotation + dr, (fxRand.random(6f) + 3f) * e.finpow());
                Fill.poly(e.x + fxV.x, e.y + fxV.y, 4, e.fout() * 3f);
            }
        });
    }

    // ================================================================== định nghĩa unit

    public static void create() {
        final Effect hitFx = hitBulletFx(5f, 8, 10f);
        final Effect despawnFx = hitBulletFx(12f, 10, 18f);
        final Effect honiveryShootFx = shootFx(); // ten khac 'shootEffect' de khong bi truong cua BulletType che

        honivery = new UnitType("honivery") {{
            constructor = LegsUnit::create;

            localizedName = "Honivery";
            description = "Cơ giáp 4 chân nhỏ gọn, trang bị 2 súng máy Gatling hạng nặng và 2 bệ phòng thủ điểm tự bắn hạ đạn địch ở cự ly gần.";
            details = "Súng Gatling xoay nòng bắn đạn xuyên tối đa 2 mục tiêu, kèm nổ lan nhỏ và tia sét, mỗi viên bỏ qua 50% giáp mục tiêu. "
                + "Bắn liên tục quá lâu súng sẽ quá nhiệt và ngừng bắn cho tới khi nguội. "
                + "Bệ phòng thủ điểm bắn hạ đạn địch trong bán kính 12 ô.";

            outlineColor = Pal.darkOutline;
            envDisabled = Env.none;
            researchCostMultiplier = 7.5f;

            hitSize = 18f;
            health = 3500f;
            armor = 10f;
            speed = 3.4f / 7.5f;

            legCount = 4;
            legLength = 10f;
            legLengthScl = 1.2f;
            legBaseOffset = 0.1f;
            legForwardScl = 0.3f;
            legExtension = 3f;
            legStraightness = 0.2f;

            weapons.addAll(new HoniveryGatlingWeapon("newex-honivery-weapon") {{
                x = 49 / 4f;
                y = -6 / 4f;
                top = false;
                bx = -0.5f / 4f;
                by = 29 / 4f;
                width = 9 / 4f;
                inclineRotation = -37f;
                mirror = true;
                rotate = true;
                rotationLimit = 16;
                rotateSpeed = 1f;
                shootCone = 80;
                recoils = 4;
                reload = 3;
                shootWarmupSpeed = 0.025f;
                shootY = 48 / 4f;
                shootX = -3.5f / 4f;
                shootSoundVolume = 1f;
                shootSound = Sounds.shootReign;
                soundPitchMax = 1.1f;
                soundPitchMin = 0.85f;
                shoot.shots = 1;
                overheatSpeed = 0.02f;
                overheatDurationTime = 5 * 60f;
                targetInterval = 0;
                targetSwitchInterval = 0;
                inversion = true;
                inaccuracy = 2;
                bullet = new BasicBulletType(12f, 25f) {{
                    hitEffect = hitFx;
                    despawnEffect = despawnFx;
                    shootEffect = honiveryShootFx;
                    smokeEffect = Fx.none;
                    width = 7f;
                    height = 7f;
                    pierce = true;
                    pierceCap = 2;
                    shrinkX = 0.2f;
                    shrinkY = 0f;
                    backColor = lightningColor = trailColor = hitColor = HONIVERY_COLOR;
                    trailLength = 5;
                    trailWidth = 1.3f;
                    lifetime = 18.66f;
                    splashDamageRadius = 12f;
                    splashDamage = 20f;
                    armorMultiplier = 0.5f;
                    lightningDamage = 10f;
                    lightning = 1;
                    lightningLength = 5;
                    lightningCone = 15f;
                }};
            }}, new PointDefenseWeapon("newex-honivery-point-weapon") {{
                x = 24 / 4f;
                y = -28 / 4f;
                reload = 5f;
                color = HONIVERY_COLOR;
                rotateSpeed = 35f;
                targetInterval = 8f;
                targetSwitchInterval = 8f;
                bullet = new BulletType() {{
                    shootEffect = Fx.sparkShoot;
                    hitEffect = Fx.pointHit;
                    maxRange = 12 * 8;
                    damage = 35f;
                }};
            }});
        }};
    }

    // ================================================================== súng Gatling quá nhiệt

    /** Súng nhiều nòng xoay: số nòng = recoils; bắn nhiều thì tích nhiệt, quá 1.0 thì ngắt bắn tới khi nguội. */
    public static class HoniveryGatlingWeapon extends Weapon {
        public float recoilTravel = 2f;
        public float baseAngle = 0f;
        public float shootAngle = 180f;
        public float inclineRotation = 0f;
        public float bx, by, width;
        public float keepWarmup = 45f;
        public float overheatSpeed = 0.01f, overheatDurationTime = 3 * 60f;
        /** Bullet lifetime randomization range (like velocityRnd, but for lifetime). */
        public float lifeRnd = 0f;
        /** Extra flat bullet lifetime added on top of the randomized base (like extraVelocity, but for lifetime). */
        public float extraLife = 0f;
        /** Minimum unit velocity required before this weapon is allowed to shoot. */
        public float minShootVelocity = 0f;
        /** Maximum unit velocity allowed for shooting; -1 disables the cap. */
        public float maxShootVelocity = -1f;
        public boolean inversion = false;
        public Color overheatColor = Pal.turretHeat;
        public TextureRegion barrel, barrelHeat;

        public HoniveryGatlingWeapon(String name) {
            super(name);
            shootWarmupSpeed = 0.03f;
            mountType = HoniveryGatlingMount::new;
        }

        @Override
        public void load() {
            super.load();
            barrel = Core.atlas.find(name + "-barrel");
            barrelHeat = Core.atlas.find(name + "-barrel-heat");
        }

        @Override
        public void flip() {
            super.flip();
            inclineRotation *= -1;
            bx *= -1;
            shootAngle = Mathf.mod(180 - shootAngle, 360f);
            inversion = !inversion;
        }

        @Override
        protected void bullet(Unit unit, WeaponMount m, float xOffset, float yOffset, float angleOffset, Mover mover) {
            if (!unit.isAdded()) return;
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            mount.charging = false;
            float xSpread = Mathf.range(xRand),
                ySpread = Mathf.range(yRand),
                weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
                mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y),
                bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX + xOffset + xSpread, this.shootY + yOffset + ySpread),
                bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX + xOffset + xSpread, this.shootY + yOffset + ySpread),
                shootAng = bulletRotation(unit, mount, bulletX, bulletY) + angleOffset,
                baseLife = (1f - lifeRnd) + Mathf.random(lifeRnd) + extraLife,
                lifeScl = bullet.scaleLife ? baseLife * Mathf.clamp(Mathf.dst(bulletX, bulletY, mount.aimX, mount.aimY) / bullet.range) : baseLife,
                angle = shootAng + Mathf.range(inaccuracy + bullet.inaccuracy);

            mount.bullet = bullet.create(unit, unit.team, bulletX, bulletY, angle, -1f,
                (1f - velocityRnd) + Mathf.random(velocityRnd) + extraVelocity, lifeScl, null, mover, mount.aimX, mount.aimY);
            handleBullet(unit, mount, mount.bullet);

            if (!continuous) shootSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);
            else initialShootSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);

            if (mount.allowShootEffects) {
                ejectEffect.at(mountX, mountY, angle * Mathf.sign(this.x));
                bullet.shootEffect.at(bulletX, bulletY, angle, bullet.hitColor, unit);
                bullet.smokeEffect.at(bulletX, bulletY, angle, bullet.hitColor, unit);
            }

            unit.vel.add(Tmp.v1.trns(shootAng + 180f, bullet.recoil));
            Effect.shake(shake, shake, bulletX, bulletY);
            mount.recoil = 1f;
            if (recoils > 0) {
                if (mount.recoils == null) mount.recoils = new float[recoils];
                float step = 360f / recoils;
                float delta = Mathf.mod(this.shootAngle - mount.angle, 360f);
                int index = Mathf.round(delta / step) % recoils;
                mount.recoils[index] = 1f;
            }
            mount.heat = 1f;
            mount.overheat += overheatSpeed;
        }

        @Override
        public void update(Unit unit, WeaponMount m) {
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            if (mount.overheat > 1f) mount.isOverheat = true;
            if (mount.overheat > 0) {
                mount.overheat -= Time.delta * (1 / overheatDurationTime) * (1 - mount.warmup);
                if (mount.overheat <= 0) {
                    mount.overheat = 0;
                    mount.isOverheat = false;
                }
            }

            boolean can = unit.canShoot() && !mount.isOverheat;

            float lastReload = mount.reload;
            mount.angle = (mount.angle + (inversion ? -1 : 1) * (360f / reload) * Time.delta * unit.reloadMultiplier * mount.warmup / recoils * shoot.shots) % 360f;
            mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier * mount.warmup, 0);
            mount.recoil = Mathf.approachDelta(mount.recoil, 0, unit.reloadMultiplier / recoilTime);
            if (recoils > 0) {
                if (mount.recoils == null) mount.recoils = new float[recoils];
                for (int i = 0; i < recoils; i++) {
                    mount.recoils[i] = Mathf.approachDelta(mount.recoils[i], 0, unit.reloadMultiplier / recoilTime);
                }
            }
            mount.smoothReload = Mathf.lerpDelta(mount.smoothReload, mount.reload / reload, smoothReloadSpeed);
            mount.charge = mount.charging && shoot.firstShotDelay > 0 ? Mathf.approachDelta(mount.charge, 1, 1 / shoot.firstShotDelay) : 0;

            float warmupTarget = (can && mount.shoot && !mount.isOverheat) || (continuous && mount.bullet != null) || mount.charging ? 1f : 0f;
            mount.keepWarmup = Mathf.approachDelta(mount.keepWarmup, warmupTarget, 1 / keepWarmup);
            warmupTarget = mount.keepWarmup > 0.01f ? 1f : warmupTarget;
            if (linearWarmup) mount.warmup = Mathf.approachDelta(mount.warmup, warmupTarget, shootWarmupSpeed);
            else mount.warmup = Mathf.lerpDelta(mount.warmup, warmupTarget, shootWarmupSpeed);

            float mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

            // tự tìm mục tiêu nếu vũ khí không do người chơi điều khiển
            if (!controllable && autoTarget) {
                if ((mount.retarget -= Time.delta) <= 0f) {
                    mount.target = findTarget(unit, mountX, mountY, bullet.range, bullet.collidesAir, bullet.collidesGround);
                    mount.retarget = mount.target == null ? targetInterval : targetSwitchInterval;
                }
                if (mount.target != null && checkTarget(unit, mount.target, mountX, mountY, bullet.range)) mount.target = null;

                boolean shootNow = false;
                if (mount.target != null) {
                    shootNow = mount.target.within(mountX, mountY, bullet.range + Math.abs(shootY) + (mount.target instanceof Sized ? ((Sized) mount.target).hitSize() / 2f : 0f)) && can;
                    if (predictTarget) {
                        Vec2 to = Predict.intercept(unit, mount.target, bullet);
                        mount.aimX = to.x;
                        mount.aimY = to.y;
                    } else {
                        mount.aimX = mount.target.x();
                        mount.aimY = mount.target.y();
                    }
                }
                mount.shoot = mount.rotate = shootNow;
            }

            // xoay súng
            if (rotate && (mount.rotate || mount.shoot) && can) {
                float axisX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                    axisY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);
                mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY) - unit.rotation;
                mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation, rotateSpeed * Time.delta);
                if (rotationLimit < 360) {
                    float dst = Angles.angleDist(mount.rotation, baseRotation);
                    if (dst > rotationLimit / 2f) {
                        mount.rotation = Angles.moveToward(mount.rotation, baseRotation, dst - rotationLimit / 2f);
                    }
                }
            } else if (!rotate) {
                mount.rotation = baseRotation;
                mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
            }

            float weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
                bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX, this.shootY),
                bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX, this.shootY),
                shootAng = bulletRotation(unit, mount, bulletX, bulletY);

            if (alwaysShooting) mount.shoot = true;

            // nhiệt giảm dần khi không bắn (đang quá nhiệt thì bám theo mức nhiệt thật)
            if (mount.isOverheat) mount.heat = Mathf.approachDelta(mount.heat, mount.overheat, 1 / 30f);
            else mount.heat = Math.max(mount.heat - Time.delta * unit.reloadMultiplier / cooldownTime, 0);
            if (mount.sound != null) mount.sound.update(bulletX, bulletY, false);

            // đổi bên bắn nếu vũ khí luân phiên
            boolean wasFlipped = mount.side;
            if (otherSide >= 0 && alternate && mount.side == flipSprite && otherSide < unit.mounts.length
                && mount.reload <= reload / 2f && lastReload > reload / 2f) {
                unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
                mount.side = !mount.side;
            }

            if (!Vars.headless && activeSound != Sounds.none && mount.shoot && can && mount.warmup >= minWarmup) {
                Vars.control.sound.loop(activeSound, unit, activeSoundVolume);
            }

            float velLen = unit.isRemote() ? unit.vel.len() : unit.deltaLen() / Time.delta;

            if (mount.shoot && can
                && (!alternate || wasFlipped == flipSprite)
                && mount.warmup >= minWarmup
                && velLen >= minShootVelocity && (maxShootVelocity == -1 || velLen <= maxShootVelocity)
                && mount.reload <= 0.0001f
                && (alwaysShooting || Angles.within(rotate ? mount.rotation : unit.rotation + baseRotation, mount.targetRotation, shootCone))) {
                shoot(unit, mount, bulletX, bulletY, shootAng);
                mount.reload = reload;
            }
        }

        @Override
        public void draw(Unit unit, WeaponMount m) {
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            if (recoils > 0 && mount.recoils == null) mount.recoils = new float[recoils];

            float rotation = unit.rotation - 90,
                realRecoil = Mathf.pow(mount.recoil, recoilPow) * recoil,
                weaponRotation = rotation + (rotate ? mount.rotation : baseRotation),
                wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0, -realRecoil),
                wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0, -realRecoil),
                tx = wx + Angles.trnsx(weaponRotation, bx, by),
                ty = wy + Angles.trnsy(weaponRotation, bx, by);
            float z = Draw.z();
            float w = this.width / Mathf.cosDeg(inclineRotation);
            if (barrel != null && barrel.found()) {
                for (int i = 0; i < recoils; i++) {
                    float angle = Mathf.mod(mount.angle + (360f / recoils) * i, 360f);
                    Vec2 v = Tmp.v2.trns(weaponRotation + inclineRotation, w / 2f)
                        .scl(w / 2f * Mathf.cosDeg(angle))
                        .add(tx, ty).add(Tmp.v3.trns(weaponRotation - 90, mount.recoils[i] * recoilTravel));
                    float s = Mathf.sinDeg(angle);
                    Draw.z(z + s * 0.04f + 0.05f);
                    Draw.color(Color.white, Color.black, 0.10f - s * 0.09f);
                    Draw.rect(barrel, v.x, v.y, weaponRotation);
                    if (mount.overheat > 0.01f && barrelHeat.found()) {
                        Draw.color(overheatColor, mount.overheat);
                        Draw.blend(Blending.additive);
                        Draw.rect(barrelHeat, v.x, v.y, weaponRotation);
                        Draw.blend();
                    }
                }
            }
            Draw.reset();
            Draw.z(z + 0.1f);
            super.draw(unit, m);
        }

        public static class HoniveryGatlingMount extends WeaponMount {
            public float angle;
            public float keepWarmup = 0f;
            public float overheat = 0f;
            public boolean isOverheat;

            public HoniveryGatlingMount(Weapon weapon) {
                super(weapon);
                angle = ((HoniveryGatlingWeapon) weapon).baseAngle;
            }
        }
    }
}
