package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Bullet;
import mindustry.gen.EntityMapping;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.MechUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;

/**
 * Unit Lancelot (Relic Lancelot) - Cơ giáp 2 chân hạng nặng phong cách vàng kim - carbon đen.
 * - Tầm bắn trội hơn Bulvasyr 22% (463.6 đơn vị ~ 58 ô).
 * - Khiên vật lý trước ngực (550% HP) vẽ tách lớp riêng (tấm chính + 2 cánh giáp bản lề) có animation cơ học riêng:
 *   nhấp nhô theo bước chân, đẩy thủy lực & mở rộng cánh khiên khi giao tranh/khai hỏa, giật lùi & tóe tia vàng khi đỡ đạn.
 * - 3 khe vũ khí:
 *   + 2 khe hông (vũ khí nhẹ): bắn nhanh loạt tên lửa tốc độ cao, sát thương nhỏ hơn nhưng liên thanh dồn dập.
 *   + 1 khe tâm unit (vũ khí nặng): pháo shotgun siêu tốc tầm ngắn-trung, tốc bay cực cao nhưng thời gian tồn tại thấp, sát thương nổ dồn cực lớn.
 */
public final class Lancelot {
    public static UnitType lancelot;

    /** Tầm bắn trội hơn Bulvasyr đúng +22% (380 * 1.22 = 463.6 ~ 58 ô). */
    public static final float RANGE = Bulvasyr.RANGE * 1.22f;

    /** Thông số Khiên Vật Lý trước ngực. */
    public static final float SHIELD_HP_MULT = 5.5f;         // 550% HP
    public static final float SHIELD_ANGLE_OFFSET = 0f;      // 0° = CHÍNH DIỆN trước ngực
    public static final float SHIELD_HALF_ARC = 66f;         // che phủ toàn bộ mặt trước (±66°)

    // Bảng màu Relic Lancelot (carbon đen + vàng kim vân đá + tia cam cháy + LED xanh tím)
    private static final Color OUTLINE_COL = Color.valueOf("1b1f27");
    private static final Color CARBON_DARK = Color.valueOf("272d37");
    private static final Color STEEL_LIGHT = Color.valueOf("b8c4d8");
    private static final Color GOLD_DARK   = Color.valueOf("a66308");
    private static final Color GOLD_MID    = Color.valueOf("f2a81d");
    private static final Color GOLD_BRIGHT = Color.valueOf("ffe37a");
    private static final Color GOLD_CORE   = Color.valueOf("fff8d6");
    private static final Color SOLAR_RED   = Color.valueOf("ff5a26");
    private static final Color LED_BLUE    = Color.valueOf("7a9aff");

    private static boolean initialized = false;
    private static TextureRegion shieldCenterRegion;
    private static TextureRegion shieldWingRegion;
    private static final Seq<LancelotUnit> activeLancelots = new Seq<>(false, 8);

    // Hiệu ứng chiến đấu cực ngầu & gây nghiện
    private static Effect shieldHitFx;
    private static Effect shieldBreakFx;
    private static Effect footstepFx;
    private static Effect thrusterSteamFx;
    private static Effect missileMuzzleFx;
    private static Effect missileTrailFx;
    private static Effect missileHitFx;
    private static Effect shotgunMuzzleFx;
    private static Effect shotgunTrailFx;
    private static Effect shotgunHitFx;
    private static Effect shotgunDespawnFx;

    private Lancelot() {}

    static void drawStar4(float x, float y, float rot, float len, float wid, float shortLen) {
        Drawf.tri(x, y, wid, len, rot);
        Drawf.tri(x, y, wid, len, rot + 180f);
        Drawf.tri(x, y, wid * 0.75f, shortLen, rot + 90f);
        Drawf.tri(x, y, wid * 0.75f, shortLen, rot - 90f);
    }

    /** Gọi từ NexMod() constructor để đăng ký EntityMapping & sự kiện chặn laser/đạn xuyên cho khiên trước ngực. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        int ensureEntityRegistered = LancelotUnit.CLASS_ID;

        Events.on(WorldLoadEvent.class, e -> activeLancelots.clear());

        Events.on(UnitDamageEvent.class, e -> {
            if (e.unit == null || e.bullet == null) return;
            if (e.unit instanceof LancelotUnit) {
                ((LancelotUnit) e.unit).handleBulletDamageEvent(e.bullet);
            } else if (!activeLancelots.isEmpty() && Bulvasyr.isBeamOrInstantType(e.bullet.type) && !Bulvasyr.isExplosiveBullet(e.bullet.type)) {
                tryBlockLaserBehindShield(e.unit, e.bullet);
            }
        });

        Events.run(Trigger.afterGameUpdate, () -> {
            for (int i = activeLancelots.size - 1; i >= 0; i--) {
                LancelotUnit lu = activeLancelots.get(i);
                if (lu == null || !lu.isAdded() || lu.dead) {
                    activeLancelots.remove(i);
                    continue;
                }
                lu.flushDeferredLethal();
            }
        });
    }

    private static void tryBlockLaserBehindShield(Unit victim, Bullet b) {
        if (victim == null || victim.dead || b == null || b.team == victim.team) return;
        float srcX = b.x, srcY = b.y;
        if (b.owner instanceof Position) {
            Position p = (Position) b.owner;
            srcX = p.getX();
            srcY = p.getY();
        }
        for (int i = activeLancelots.size - 1; i >= 0; i--) {
            LancelotUnit lu = activeLancelots.get(i);
            if (lu == null || !lu.isAdded() || lu.dead) {
                activeLancelots.remove(i);
                continue;
            }
            if (lu.team != victim.team || !lu.isShieldActive()) continue;
            if (!lu.doesSegmentHitShieldPlate(srcX, srcY, victim.x, victim.y)) continue;
            float angToSrc = Angles.angle(lu.x, lu.y, srcX, srcY);
            if (!lu.isAngleCoveredByShield(angToSrc)) continue;

            float mult = Bulvasyr.shieldDamageMultiplier(b.type);
            float rawDmg = Math.max(0f, b.damage);
            if (rawDmg <= 0f) return;

            float blockedRaw = rawDmg;
            if (!lu.wasBeamChargedThisTick(b.id)) {
                lu.markBeamChargedThisTick(b.id);
                float absorbed = lu.absorbPhysicalShield(rawDmg * mult, Tmp.v1.x, Tmp.v1.y, angToSrc);
                blockedRaw = absorbed / mult;
            }
            if (blockedRaw > 0.01f && !victim.dead) {
                float healBack = Math.min(blockedRaw, Math.max(0f, victim.maxHealth - victim.health));
                victim.health += healBack;
            }
            return;
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        init();
        createEffects();

        final RelicMissileBulletType lightMissile = new RelicMissileBulletType();
        final RelicShotgunBulletType heavyShotgun = new RelicShotgunBulletType();

        lancelot = new UnitType("lancelot") {{
            constructor = LancelotUnit::new;

            localizedName = "Lancelot";
            description = "Cơ giáp 2 chân hạng nặng với khiên vật lý trước ngực (550% HP) tách lớp chuyển động thủy lực, 2 bệ tên lửa nhanh bên hông và pháo shotgun hạng nặng ở tâm.";
            details = "Tầm bắn trội hơn Bulvasyr 22% (" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô). "
                + "2 khe vũ khí nhẹ hai bên hông bắn loạt tên lửa tốc độ cao liên thanh; khe vũ khí nặng ở tâm bắn đạn shotgun siêu tốc tầm ngắn-trung gây sát thương dồn cực lớn. "
                + "Khiên vật lý trước ngực (550% HP) chặn đạn, mảnh đạn và laser chính diện; tự động đẩy thủy lực và mở 2 cánh khiên khi giao tranh.";

            health = 48000f;
            armor = 22f;
            hitSize = 35f;
            speed = 0.74f;
            rotateSpeed = 2.75f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;
            envDisabled = Env.none;
            drawCell = false;

            // Cơ giáp 2 chân hạng nặng bước luân phiên trái/phải giống Bulvasyr
            hovering = false;
            canBoost = false;
            mechStride = 11.5f;
            mechFrontSway = 0.90f;
            mechSideSway = 0.58f;
            mechStepParticles = true;
            stepShake = 1.5f;

            outlineRadius = 3;
            outlineColor = OUTLINE_COL;

            abilities.add(new FrontShieldAbility());

            // 2 khe vũ khí NHẸ 2 bên hông: sát thương nhỏ hơn nhưng bắn rất nhanh, phóng tên lửa tốc độ cao
            weapons.add(new Weapon("newex-lancelot-weapon-light") {{
                x = 20.2f;
                y = -1.0f;
                mirror = true;
                alternate = true;
                top = true;
                rotate = true;
                rotateSpeed = 9.5f;
                rotationLimit = 68f;
                shootCone = 28f;
                layerOffset = 0.04f;
                shootX = 0f;
                shootY = 9.0f;
                recoil = 2.4f;
                recoilTime = 16f;
                reload = 18f;
                inaccuracy = 4.5f;
                shootSound = Sounds.shootSmite;
                ejectEffect = Fx.none;
                bullet = lightMissile;
            }});

            // 1 khe vũ khí NẶNG ở giữa tâm unit: bắn chậm hơn, đạn shotgun tốc bay cực cao nhưng thời gian tồn tại thấp, sát thương cực lớn
            weapons.add(new Weapon("newex-lancelot-weapon-heavy") {{
                x = 0f;
                y = 2.5f;
                mirror = false;
                top = true;
                rotate = true;
                rotateSpeed = 5.4f;
                rotationLimit = 115f;
                shootCone = 24f;
                layerOffset = 0.08f;
                shootX = 0f;
                shootY = 14.0f;
                recoil = 5.5f;
                recoilTime = 42f;
                reload = 108f;
                shake = 3.2f;
                inaccuracy = 3.5f;
                velocityRnd = 0.14f;
                shoot = new ShootSpread() {{
                    shots = 9;
                    spread = 2.3f;
                }};
                shootSound = Sounds.shootSpectre;
                ejectEffect = Fx.none;
                bullet = heavyShotgun;
            }});
        }};
    }

    private static void createEffects() {
        // Khiên trước ngực đỡ đạn: tóe tia vàng nóng chảy + vòng xung kích kim loại
        shieldHitFx = new Effect(18f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(GOLD_CORE, GOLD_MID, e.fin());
            Lines.stroke(2.0f * e.fout());
            Lines.circle(e.x, e.y, 2.5f + e.fin(Interp.pow2Out) * 12f);
            drawStar4(e.x, e.y, e.rotation, 8f * e.fout(), 2.2f * e.fout(), 4f * e.fout());
            Angles.randLenVectors(e.id, 7, 17f * e.finpow(), e.rotation, 72f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 5.5f * e.fout())
            );
            Draw.blend();
            Draw.reset();
        });

        // Vỡ khiên trước ngực: mảnh giáp vàng kim + sóng nổ chói lóa
        shieldBreakFx = new Effect(40f, 140f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, e.fin());
            Lines.stroke(3.6f * e.fout());
            Lines.circle(e.x, e.y, 7f + e.fin(Interp.pow2Out) * 40f);
            Draw.blend();
            Angles.randLenVectors(e.id, 16, 44f * e.finpow(), (x, y) -> {
                Draw.color(GOLD_MID, CARBON_DARK, Mathf.randomSeed(e.id + (int) x));
                Fill.poly(e.x + x, e.y + y, 4, 5.2f * e.fout(), e.rotation + x * 4.5f);
            });
            Draw.color(GOLD_CORE, e.fout());
            Fill.circle(e.x, e.y, 10f * e.fout());
            Draw.reset();
        });

        // Bước chân nặng: vòng chấn + bụi công nghiệp + tia lửa vàng/xanh LED
        footstepFx = new Effect(32f, 75f, e -> {
            Draw.color(STEEL_LIGHT, 0.50f * e.fout());
            Lines.stroke(1.7f * e.fout());
            Lines.circle(e.x, e.y, 2.5f + e.fin(Interp.pow2Out) * 16f);
            Angles.randLenVectors(e.id, 6, 3f + 15f * e.finpow(), (x, y) -> {
                Draw.color(Tmp.c1.set(Color.valueOf("9ea6b3")).lerp(Color.gray, e.fin()), 0.44f * e.fout());
                Fill.circle(e.x + x, e.y + y, 1.8f + 3.0f * e.fin());
            });
            Draw.color(GOLD_BRIGHT, LED_BLUE, e.fin());
            Lines.stroke(1.2f * e.fout());
            Angles.randLenVectors(e.id + 3, 5, 4f + 13f * e.finpow(), (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + 3.4f * e.fout())
            );
            Draw.reset();
        }).layer(Layer.groundUnit - 1f);

        // Phụt khói & tia plasma trắng-vàng từ ống xả thruster sau lưng khi di chuyển (như ảnh 1 trailer)
        thrusterSteamFx = new Effect(26f, 60f, e -> {
            float fin = e.fin(), fout = e.fout();
            float dist = 12f * e.fin(Interp.pow2Out);
            float sx = e.x + Angles.trnsx(e.rotation, dist);
            float sy = e.y + Angles.trnsy(e.rotation, dist);
            Draw.color(Tmp.c1.set(Color.white).lerp(Color.lightGray, fin), 0.36f * fout);
            Fill.circle(sx, sy, 2.0f + 4.4f * fin);
            if (fin < 0.35f) {
                float fa = 1f - fin / 0.35f;
                Draw.blend(Blending.additive);
                Draw.color(GOLD_BRIGHT, SOLAR_RED, fin * 2.5f);
                Draw.alpha(0.75f * fa);
                Drawf.tri(e.x, e.y, 3.4f * fa, 10f * fa, e.rotation);
                Draw.blend();
            }
            Draw.reset();
        }).layer(Layer.groundUnit - 0.5f);

        // === VŨ KHÍ NHẸ (2 BÊN HÔNG): TÊN LỬA NHANH ===
        missileMuzzleFx = new Effect(18f, 70f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, e.fin());
            Drawf.tri(e.x, e.y, 5.5f * fout, 22f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_CORE, fout);
            Drawf.tri(e.x, e.y, 2.4f * fout, 11f * e.fout(Interp.pow2Out), e.rotation);
            Lines.stroke(1.4f * fout);
            Draw.color(GOLD_MID, 0.8f * fout);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow2Out) * 11f);
            Draw.blend();
            Angles.randLenVectors(e.id, 4, 18f * e.finpow(), e.rotation + 180f, 34f, (x, y) -> {
                Draw.color(Color.lightGray, 0.38f * fout);
                Fill.circle(e.x + x, e.y + y, 1.3f + 2.2f * e.fin());
            });
            Drawf.light(e.x, e.y, 32f * fout, GOLD_BRIGHT, 0.65f * fout);
            Draw.reset();
        });

        missileTrailFx = new Effect(22f, 40f, e -> {
            float fout = e.fout();
            Draw.color(Tmp.c1.set(GOLD_MID).lerp(Color.darkGray, e.fin()), 0.36f * fout);
            Fill.circle(e.x, e.y, 1.0f + 2.5f * e.fin(Interp.pow2Out));
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, e.fin());
            Draw.alpha(0.85f * fout);
            drawStar4(e.x, e.y, e.rotation, 3.6f * fout + 0.5f, 0.9f * fout + 0.15f, 1.8f * fout);
            Draw.blend();
            Draw.reset();
        });

        missileHitFx = new Effect(34f, 140f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            // Ngôi sao 4 cánh vàng rực + lõi trắng bùng nổ cực sướng mắt
            Draw.color(SOLAR_RED, 0.65f * a);
            drawStar4(e.x, e.y, e.rotation + 45f, 24f * e.fin(Interp.pow2Out) + 6f, 5.2f * a, 12f * e.fin(Interp.pow2Out) + 3f);
            Draw.color(GOLD_BRIGHT, 0.95f * a);
            drawStar4(e.x, e.y, e.rotation, 19f * e.fin(Interp.pow2Out) + 5f, 3.4f * a, 9f * e.fin(Interp.pow2Out) + 2f);
            Draw.color(GOLD_CORE, a);
            Fill.circle(e.x, e.y, 7.5f * a);
            // Vòng sóng nổ kép
            Lines.stroke(2.4f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(e.x, e.y, 3f + e.fin(Interp.pow3Out) * 28f);
            Lines.stroke(1.1f * a);
            Draw.color(GOLD_CORE, 0.85f * a);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow2Out) * 18f);
            // Tia lửa bắn tung tóe
            Lines.stroke(1.4f * fout);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Angles.randLenVectors(e.id + 9, 10, 6f + 30f * e.finpow(), (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1.5f + 5.5f * fout)
            );
            Draw.blend();
            Drawf.light(e.x, e.y, 58f * a, GOLD_MID, 0.85f * a);
            Draw.reset();
        });

        // === VŨ KHÍ NẶNG (TÂM UNIT): SHOTGUN SIÊU TỐC ===
        shotgunMuzzleFx = new Effect(28f, 130f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            // Chớp nòng hình nón rộng cực gắt
            Draw.color(SOLAR_RED, 0.72f * a);
            Drawf.tri(e.x, e.y, 24f * a, 52f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_BRIGHT, 0.95f * a);
            Drawf.tri(e.x, e.y, 15f * a, 38f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_CORE, a);
            Drawf.tri(e.x, e.y, 7f * a, 22f * e.fout(Interp.pow2Out), e.rotation);

            // 2 vòng sóng âm nén hình elip/cung phóng về phía trước (rất đã mắt!)
            float r1 = 6f + e.fin(Interp.pow3Out) * 30f;
            float r2 = 4f + e.fin(Interp.pow2Out) * 18f;
            float ox1 = e.x + Angles.trnsx(e.rotation, 8f + fin * 20f);
            float oy1 = e.y + Angles.trnsy(e.rotation, 8f + fin * 20f);
            Lines.stroke(2.8f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(ox1, oy1, r2);
            Lines.stroke(1.6f * a);
            Draw.color(GOLD_CORE, 0.8f * a);
            Lines.circle(e.x, e.y, r1);

            // Tia lửa plasma điện vàng tóe theo hình nón shotgun
            Lines.stroke(1.8f * fout);
            Draw.color(GOLD_CORE, GOLD_MID, fin);
            Angles.randLenVectors(e.id + 17, 14, 10f + 48f * e.finpow(), e.rotation, 24f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 3f + 9f * fout)
            );
            Draw.blend();

            // Khói nóng phụt ngược 2 bên họng pháo
            Angles.randLenVectors(e.id + 31, 8, 22f * e.finpow(), e.rotation + 180f, 55f, (x, y) -> {
                Draw.color(Color.lightGray, 0.42f * fout);
                Fill.circle(e.x + x, e.y + y, 1.8f + 3.6f * fin);
            });
            Drawf.light(e.x, e.y, 82f * a, GOLD_BRIGHT, 0.95f * a);
            Draw.reset();
        });

        shotgunTrailFx = new Effect(10f, 36f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, e.fin());
            Draw.alpha(0.78f * fout);
            Lines.stroke(1.6f * fout);
            Lines.lineAngleCenter(e.x, e.y, e.rotation, 12f * fout + 4f);
            Draw.blend();
            Draw.reset();
        });

        shotgunHitFx = new Effect(24f, 110f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            // Vết chém chéo plasma + tia điện vàng rực (như ảnh 1 trailer!)
            Draw.color(SOLAR_RED, 0.7f * a);
            drawStar4(e.x, e.y, e.rotation + 22f, 20f * e.fin(Interp.pow2Out) + 6f, 4.5f * a, 9f * e.fin(Interp.pow2Out) + 3f);
            Draw.color(GOLD_CORE, a);
            drawStar4(e.x, e.y, e.rotation, 14f * e.fin(Interp.pow2Out) + 4f, 2.2f * a, 6f * e.fin(Interp.pow2Out) + 2f);
            Lines.stroke(2.0f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow3Out) * 20f);
            Lines.stroke(1.5f * fout);
            Draw.color(GOLD_CORE, LED_BLUE, fin * 0.7f);
            Angles.randLenVectors(e.id + 5, 8, 6f + 26f * e.finpow(), e.rotation + 180f, 52f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 2f + 6.5f * fout)
            );
            Draw.blend();
            Drawf.light(e.x, e.y, 44f * a, GOLD_BRIGHT, 0.8f * a);
            Draw.reset();
        });

        shotgunDespawnFx = new Effect(14f, 40f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(GOLD_MID, SOLAR_RED, e.fin());
            Draw.alpha(0.65f * fout);
            Lines.stroke(1.2f * fout);
            Lines.lineAngle(e.x, e.y, e.rotation, 9f * fout);
            Fill.circle(e.x, e.y, 2.2f * fout);
            Draw.blend();
            Draw.reset();
        });
    }

    // ================================================================== Đạn Tên Lửa Nhanh (2 Khe Vũ Khí Nhẹ Bên Hông)

    private static class RelicMissileBulletType extends MissileBulletType {
        RelicMissileBulletType() {
            super(11.8f, 130f);
            lifetime = RANGE / 11.8f + 1.2f; // Đạt đủ tầm 463.6 (+22% hơn Bulvasyr)
            width = 7f;
            height = 13f;
            hitSize = 5f;
            splashDamage = 75f;
            splashDamageRadius = 26f;
            pierce = false;
            pierceArmor = false;
            collidesAir = true;
            collidesGround = true;
            homingPower = 0.085f;
            homingRange = 220f;
            homingDelay = 4f;
            weaveScale = 2.6f;
            weaveMag = 0.8f;

            trailLength = 15;
            trailWidth = 2.1f;
            trailColor = GOLD_MID;
            trailInterval = 1.6f;
            trailEffect = missileTrailFx;
            trailChance = 0f;

            shootEffect = missileMuzzleFx;
            smokeEffect = Fx.none;
            hitEffect = missileHitFx;
            despawnEffect = missileHitFx;
            hitColor = GOLD_BRIGHT;
            hitSound = Sounds.explosion;
            hitSoundVolume = 0.38f;
            lightRadius = 34f;
            lightOpacity = 0.65f;
            lightColor = GOLD_BRIGHT;
        }

        @Override
        public void draw(Bullet b) {
            drawTrail(b);
            float rot = b.rotation();
            float fx = Angles.trnsx(rot, 1f), fy = Angles.trnsy(rot, 1f);
            float lx = Angles.trnsx(rot + 90f, 1f), ly = Angles.trnsy(rot + 90f, 1f);
            float pulse = 0.85f + 0.15f * Mathf.sin(Time.time * 0.9f + b.id);

            // Hào quang lửa động cơ tên lửa phía sau
            Draw.blend(Blending.additive);
            float tailX = b.x - fx * 6.5f, tailY = b.y - fy * 6.5f;
            Draw.color(SOLAR_RED, 0.72f);
            Drawf.tri(tailX, tailY, 4.6f * pulse, 15f * pulse, rot + 180f);
            Draw.color(GOLD_BRIGHT, 0.92f);
            Drawf.tri(tailX, tailY, 2.8f * pulse, 10f * pulse, rot + 180f);
            Draw.color(GOLD_CORE, 0.98f);
            Drawf.tri(tailX, tailY, 1.4f, 5.5f * pulse, rot + 180f);
            Draw.blend();

            // Cánh lái tên lửa màu đen carbon viền vàng
            Draw.color(OUTLINE_COL);
            Drawf.tri(b.x - fx * 4.5f, b.y - fy * 4.5f, 7.2f, 5.2f, rot + 180f);
            Draw.color(GOLD_DARK);
            Drawf.tri(b.x - fx * 4.2f, b.y - fy * 4.2f, 5.4f, 3.6f, rot + 180f);

            // Thân tên lửa thon nhọn (carbon đen + đầu đạn vàng rực)
            Draw.color(CARBON_DARK);
            Fill.quad(
                b.x + fx * 3.5f + lx * 1.9f, b.y + fy * 3.5f + ly * 1.9f,
                b.x + fx * 3.5f - lx * 1.9f, b.y + fy * 3.5f - ly * 1.9f,
                b.x - fx * 5.8f - lx * 2.1f, b.y - fy * 5.8f - ly * 2.1f,
                b.x - fx * 5.8f + lx * 2.1f, b.y - fy * 5.8f + ly * 2.1f
            );
            Draw.color(GOLD_MID);
            Drawf.tri(b.x + fx * 3.0f, b.y + fy * 3.0f, 3.9f, 5.6f, rot);
            Draw.color(GOLD_CORE);
            Drawf.tri(b.x + fx * 4.2f, b.y + fy * 4.2f, 1.9f, 3.8f, rot);

            // Đốm sáng đầu đạn cực sắc nét
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, 0.7f);
            Fill.circle(b.x + fx * 5.5f, b.y + fy * 5.5f, 2.2f);
            Draw.blend();
            Draw.reset();
        }
    }

    // ================================================================== Đạn Shotgun Siêu Tốc (Khe Vũ Khí Nặng Ở Tâm)

    private static class RelicShotgunBulletType extends BasicBulletType {
        RelicShotgunBulletType() {
            super(20.0f, 235f);
            lifetime = 13.5f; // Tốc bay siêu cao (20.0) nhưng thời gian tồn tại thấp (~270u)
            drag = 0.012f;
            width = 6f;
            height = 18f;
            hitSize = 5.5f;
            pierce = true;
            pierceCap = 2;
            pierceArmor = true;
            knockback = 2.8f;
            collidesAir = true;
            collidesGround = true;

            trailLength = 9;
            trailWidth = 2.0f;
            trailColor = GOLD_BRIGHT;
            trailInterval = 1.2f;
            trailEffect = shotgunTrailFx;
            trailChance = 0f;

            shootEffect = shotgunMuzzleFx;
            smokeEffect = Fx.none;
            hitEffect = shotgunHitFx;
            despawnEffect = shotgunDespawnFx;
            hitColor = GOLD_BRIGHT;
            lightRadius = 38f;
            lightOpacity = 0.75f;
            lightColor = GOLD_BRIGHT;
        }

        @Override
        public void draw(Bullet b) {
            drawTrail(b);
            float rot = b.rotation();
            float fout = 0.45f + 0.55f * b.fout();

            Draw.blend(Blending.additive);
            // Tia plasma vàng-cam siêu tốc kiểu mũi lao shotgun
            Draw.color(SOLAR_RED, 0.65f * fout);
            Drawf.tri(b.x, b.y, 6.2f * fout, 22f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 5.2f * fout, 10f * fout, rot);

            Draw.color(GOLD_BRIGHT, 0.95f);
            Drawf.tri(b.x, b.y, 3.6f * fout, 17f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 3.4f * fout, 8.5f * fout, rot);

            Draw.color(GOLD_CORE, 1f);
            Drawf.tri(b.x, b.y, 1.8f, 11f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 1.8f, 5.5f, rot);

            // Chấm điện xanh-trắng nhỏ nhấp nháy ở đầu đạn
            if (((int) (Time.time + b.id) & 1) == 0) {
                Draw.color(LED_BLUE, 0.55f * fout);
                Fill.circle(b.x, b.y, 2.4f);
            }
            Draw.blend();
            Draw.reset();
        }
    }

    // ================================================================== Custom MechUnit với Khiên Vật Lý Trước Ngực + Animation Riêng

    public static class LancelotUnit extends MechUnit {
        public static final int CLASS_ID = EntityMapping.register("newex-lancelot-unit", LancelotUnit::new);

        @Override
        public int classId() {
            return CLASS_ID;
        }

        public float physShieldMax = -1f;
        public float physShieldHp = 0f;
        public boolean physShieldDamaged = false;
        public float shieldFlash = 0f;

        /** Trạng thái animation độc lập cho khiên vật lý trước ngực (đẩy thủy lực, mở cánh giáp, giật lùi khi đỡ đạn). */
        public float shieldBrace = 0f;    // 0 = hành quân, 1 = thủ thế chiến đấu (đẩy khiên + mở 2 cánh)
        public float shieldRecoil = 0f;   // độ giật lùi khiên khi hứng đạn (0..1)
        public float shieldBob = 0f;      // nhịp nhấp nhô quán tính riêng theo bước chân
        private float braceHold = 0f;
        private float thrusterTimer = 0f;
        private int lastStep = -1;
        private int ageTicks = 0;

        private final int[] handledBulletIds = new int[16];
        private int handledBulletIdx = 0;
        private final int[] handledExplosiveIds = new int[16];
        private int handledExplosiveIdx = 0;
        private final int[] beamTickBulletIds = new int[12];
        private int beamTickStamp = -1;
        private int beamTickCount = 0;

        {
            java.util.Arrays.fill(handledBulletIds, -1);
            java.util.Arrays.fill(handledExplosiveIds, -1);
        }

        private float hpBeforeRaw = -1f;
        private float shieldBeforeRaw = -1f;
        private float lastRawAmount = 0f;
        private int lastRawTick = -1;
        private boolean deferredLethal = false;
        private boolean applyingDeferredLethal = false;

        private boolean wasBulletHandled(int id) {
            for (int i = 0; i < handledBulletIds.length; i++) if (handledBulletIds[i] == id) return true;
            return false;
        }

        private void markBulletHandled(int id) {
            handledBulletIds[handledBulletIdx] = id;
            handledBulletIdx = (handledBulletIdx + 1) & 15;
        }

        private boolean wasExplosiveCharged(int id) {
            for (int i = 0; i < handledExplosiveIds.length; i++) if (handledExplosiveIds[i] == id) return true;
            return false;
        }

        private void markExplosiveCharged(int id) {
            handledExplosiveIds[handledExplosiveIdx] = id;
            handledExplosiveIdx = (handledExplosiveIdx + 1) & 15;
        }

        boolean wasBeamChargedThisTick(int id) {
            int tick = (int) Time.time;
            if (beamTickStamp != tick) {
                beamTickStamp = tick;
                beamTickCount = 0;
                return false;
            }
            for (int i = 0; i < beamTickCount; i++) if (beamTickBulletIds[i] == id) return true;
            return false;
        }

        void markBeamChargedThisTick(int id) {
            int tick = (int) Time.time;
            if (beamTickStamp != tick) {
                beamTickStamp = tick;
                beamTickCount = 0;
            }
            if (beamTickCount < beamTickBulletIds.length) beamTickBulletIds[beamTickCount++] = id;
        }

        public void ensureShieldInit() {
            if (maxHealth <= 0f) return;
            float targetMax = maxHealth * SHIELD_HP_MULT;
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

        /** Kiểm tra hướng nguồn sát thương có rơi vào mặt TRƯỚC có khiên ngực hay không. */
        public boolean isAngleCoveredByShield(float angleFromUnitToSource) {
            return Angles.within(rotation + SHIELD_ANGLE_OFFSET, angleFromUnitToSource, SHIELD_HALF_ARC);
        }

        /** Đoạn thẳng (ax, ay) -> (bx, by) có cắt ngang tấm khiên vật lý trước ngực Lancelot không (ghi điểm cắt vào Tmp.v1). */
        public boolean doesSegmentHitShieldPlate(float ax, float ay, float bx, float by) {
            float fwdX = Angles.trnsx(rotation, 1f), fwdY = Angles.trnsy(rotation, 1f);
            float rightX = Angles.trnsx(rotation - 90f, 1f), rightY = Angles.trnsy(rotation - 90f, 1f);
            float plateDist = hitSize * 0.56f + shieldBrace * 2.5f;
            float halfWidth = hitSize * (0.64f + 0.10f * shieldBrace);
            float sx1 = x + fwdX * plateDist + rightX * halfWidth;
            float sy1 = y + fwdY * plateDist + rightY * halfWidth;
            float sx2 = x + fwdX * plateDist - rightX * halfWidth;
            float sy2 = y + fwdY * plateDist - rightY * halfWidth;
            return Intersector.intersectSegments(ax, ay, bx, by, sx1, sy1, sx2, sy2, Tmp.v1);
        }

        public float absorbPhysicalShield(float shieldDmg, float hitX, float hitY, float hitAngle) {
            ensureShieldInit();
            if (shieldDmg <= 0f || physShieldHp <= 0f) return 0f;

            physShieldDamaged = true;
            shieldFlash = 1f;
            shieldRecoil = Math.min(1f, shieldRecoil + 0.72f);
            braceHold = Math.max(braceHold, 110f);

            float absorbed = Math.min(physShieldHp, shieldDmg);
            physShieldHp -= absorbed;

            if (physShieldHp <= 0.01f) {
                physShieldHp = 0f;
                shieldBreakFx.at(hitX, hitY, rotation);
                if (!Vars.headless) Sounds.explosion.at(hitX, hitY, 0.85f, 0.95f);
            } else {
                shieldHitFx.at(hitX, hitY, hitAngle);
            }
            return absorbed;
        }

        private boolean isMovingBulletFromShieldSide(Bullet b, float hitX, float hitY) {
            if (isAngleCoveredByShield(Angles.angle(x, y, hitX, hitY))) return true;
            if (isAngleCoveredByShield(b.rotation() + 180f)) return true;
            float step = Math.max(1f, Time.delta);
            return doesSegmentHitShieldPlate(
                b.x - b.vel.x * step, b.y - b.vel.y * step,
                b.x + b.vel.x * step, b.y + b.vel.y * step
            );
        }

        private boolean processShieldProjectile(Bullet b, float hx, float hy) {
            if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return false;
            if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return true;

            float angToHit = Angles.angle(x, y, hx, hy);
            float mult = Bulvasyr.shieldDamageMultiplier(b.type);

            if (Bulvasyr.isExplosiveBullet(b.type)) {
                markExplosiveCharged(b.id);
                float directDmg = Math.max(0f, b.damage);
                float splashDmg = Math.max(0f, b.type.splashDamage);
                float shieldCost = (directDmg + splashDmg) * mult;
                if (shieldCost > 0f) absorbPhysicalShield(shieldCost, hx, hy, angToHit);

                if (!Vars.headless) {
                    if (b.type.hitEffect != null) b.type.hitEffect.at(hx, hy, b.rotation(), b.type.hitColor);
                    if (b.type.hitSound != null && b.type.hitSound != Sounds.none) {
                        b.type.hitSound.at(hx, hy, Mathf.random(0.9f, 1.1f), b.type.hitSoundVolume);
                    }
                }
                if (splashDmg > 0f && b.type.splashDamageRadius > 0f) {
                    Damage.damage(b.team, hx, hy, b.type.splashDamageRadius, splashDmg,
                        b.type.pierceArmor, b.type.collidesAir, b.type.collidesGround, b.type.scaledSplashDamage, b);
                }
                b.absorb();
                return true;
            }

            float rawDmg = Math.max(0f, b.damage);
            if (rawDmg <= 0f) {
                markBulletHandled(b.id);
                b.absorb();
                return true;
            }
            float shieldCost = rawDmg * mult;
            float absorbedShield = absorbPhysicalShield(shieldCost, hx, hy, angToHit);
            float overflowShield = shieldCost - absorbedShield;

            if (overflowShield <= 0.01f) {
                markBulletHandled(b.id);
                b.absorb();
                return true;
            } else {
                markBulletHandled(b.id);
                b.damage = overflowShield / mult;
                return false;
            }
        }

        @Override
        public void add() {
            super.add();
            if (!activeLancelots.contains(this, true)) activeLancelots.add(this);
        }

        @Override
        public void remove() {
            activeLancelots.remove(this, true);
            super.remove();
        }

        @Override
        public void draw() {
            super.draw();
            FrontShieldAbility.render(this);
        }

        private void footstepAndThrusterCinematic() {
            if (Vars.headless || isFlying()) return;
            float spd = vel.len();
            if (spd >= 0.12f) {
                int step = (int) Math.floor(walkExtend(true) / 2f);
                if (lastStep == -1) {
                    lastStep = step;
                } else if (step != lastStep) {
                    lastStep = step;
                    float side = (step & 1) == 0 ? 1f : -1f;
                    float fx = x + Angles.trnsx(baseRotation + 90f, side * 10f) + Angles.trnsx(baseRotation, 8.5f);
                    float fy = y + Angles.trnsy(baseRotation + 90f, side * 10f) + Angles.trnsy(baseRotation, 8.5f);
                    footstepFx.at(fx, fy, baseRotation);
                    Effect.shake(1.15f, 6f, fx, fy);
                }
            }
            // Phụt hơi/lửa đẩy từ 2 ống thruster sau lưng khi di chuyển (giống ảnh 1 Relic Lancelot)
            if (spd >= 0.25f) {
                thrusterTimer += Time.delta;
                if (thrusterTimer >= 4.5f) {
                    thrusterTimer = 0f;
                    float backAng = rotation + 180f;
                    float side = Mathf.chance(0.5) ? 1f : -1f;
                    float tx = x + Angles.trnsx(backAng, 14.5f) + Angles.trnsx(rotation + 90f, side * 5.5f);
                    float ty = y + Angles.trnsy(backAng, 14.5f) + Angles.trnsy(rotation + 90f, side * 5.5f);
                    thrusterSteamFx.at(tx, ty, backAng + Mathf.range(10f));
                }
            }
        }

        private void updateShieldAnimation() {
            boolean firing = isShooting();
            if (!firing && mounts != null) {
                for (WeaponMount m : mounts) {
                    if (m.recoil > 0.15f || m.shoot) {
                        firing = true;
                        break;
                    }
                }
            }
            if (firing) braceHold = Math.max(braceHold, 55f);
            if (braceHold > 0f) braceHold = Math.max(0f, braceHold - Time.delta);

            float targetBrace = braceHold > 0f ? 1f : 0f;
            shieldBrace = Mathf.lerpDelta(shieldBrace, targetBrace, targetBrace > shieldBrace ? 0.16f : 0.06f);
            if (shieldRecoil > 0f) shieldRecoil = Math.max(0f, shieldRecoil - Time.delta / 11f);
            if (shieldFlash > 0f) shieldFlash = Math.max(0f, shieldFlash - Time.delta / 10f);

            // Quán tính nhấp nhô độc lập theo sải chân
            float targetBob = vel.len() > 0.08f ? walkExtend(false) * 1.4f : Mathf.sin(Time.time * 0.055f, 1f, 0.35f);
            shieldBob = Mathf.lerpDelta(shieldBob, targetBob, 0.18f);
        }

        @Override
        public void update() {
            if (!dead && isAdded() && !activeLancelots.contains(this, true)) activeLancelots.add(this);
            flushDeferredLethal();
            ageTicks++;
            footstepAndThrusterCinematic();
            ensureShieldInit();
            updateShieldAnimation();

            if (isShieldActive() && !dead) {
                interceptFrontBullets();
            }

            super.update();
        }

        private void interceptFrontBullets() {
            float r = hitSize * 1.28f + 22f;
            float plateDist = hitSize * 0.56f + shieldBrace * 2.5f;
            final float plateX = x + Angles.trnsx(rotation, plateDist);
            final float plateY = y + Angles.trnsy(rotation, plateDist);

            Groups.bullet.intersect(x - r, y - r, r * 2f, r * 2f, b -> {
                if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return;

                if (b.type instanceof PointLaserBulletType) {
                    float srcX = b.x, srcY = b.y;
                    if (b.owner instanceof Position) {
                        srcX = ((Position) b.owner).getX();
                        srcY = ((Position) b.owner).getY();
                    }
                    if (isAngleCoveredByShield(Angles.angle(x, y, srcX, srcY)) && Mathf.within(x, y, b.aimX, b.aimY, hitSize * 0.92f)) {
                        b.aimX = plateX;
                        b.aimY = plateY;
                    }
                    return;
                }

                if (Bulvasyr.isBeamOrInstantType(b.type)) return;
                if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return;

                float step = Math.max(1f, Time.delta);
                boolean hitSeg = doesSegmentHitShieldPlate(
                    b.x - b.vel.x * step, b.y - b.vel.y * step,
                    b.x + b.vel.x * step, b.y + b.vel.y * step
                );

                if (!hitSeg) {
                    if (!isMovingBulletFromShieldSide(b, b.x, b.y)) return;
                    float dstPlate = Mathf.dst(plateX, plateY, b.x, b.y);
                    float dstCenter = Mathf.dst(x, y, b.x, b.y);
                    if (dstPlate > hitSize * 0.88f && dstCenter > hitSize * 0.78f) return;
                }

                float hx = hitSeg ? Tmp.v1.x : b.x;
                float hy = hitSeg ? Tmp.v1.y : b.y;
                processShieldProjectile(b, hx, hy);
            });
        }

        @Override
        public void collision(Hitboxc other, float cx, float cy) {
            if (other instanceof Bullet) {
                Bullet b = (Bullet) other;
                if (!dead && isShieldActive() && b.team != team && !Bulvasyr.isBeamOrInstantType(b.type)) {
                    if (isMovingBulletFromShieldSide(b, cx, cy)) {
                        if (processShieldProjectile(b, cx, cy)) return;
                    }
                }
            }
            super.collision(other, cx, cy);
        }

        void flushDeferredLethal() {
            if (!deferredLethal || dead) return;
            deferredLethal = false;
            applyingDeferredLethal = true;
            try {
                health = 0f;
                kill();
            } finally {
                applyingDeferredLethal = false;
            }
        }

        private void applyOverflowDamage(float amt) {
            if (amt <= 0f || dead) return;
            applyingDeferredLethal = true;
            try {
                super.rawDamage(amt);
            } finally {
                applyingDeferredLethal = false;
            }
        }

        @Override
        public void rawDamage(float amount) {
            if (applyingDeferredLethal) {
                super.rawDamage(amount);
                return;
            }
            hpBeforeRaw = health;
            shieldBeforeRaw = shield;
            lastRawAmount = amount;
            lastRawTick = (int) Time.time;
            deferredLethal = false;

            if (isShieldActive() && amount > 0f && (health + shield - amount) <= 0f) {
                deferredLethal = true;
                float safeAmt = Math.max(0f, health + shield - 0.01f);
                super.rawDamage(safeAmt);
                if (health <= 0f) health = 0.01f;
                return;
            }

            super.rawDamage(amount);
        }

        public void handleBulletDamageEvent(Bullet b) {
            if (b == null || b.team == team) return;
            if (dead && !deferredLethal) return;
            if (!isShieldActive()) {
                flushDeferredLethal();
                return;
            }

            float lostHp = lastRawTick == (int) Time.time && hpBeforeRaw >= 0f ? Math.max(0f, hpBeforeRaw - health) : 0f;
            float lostEnergyShield = lastRawTick == (int) Time.time && shieldBeforeRaw >= 0f ? Math.max(0f, shieldBeforeRaw - shield) : 0f;
            float totalLost = deferredLethal ? Math.max(lostHp + lostEnergyShield, lastRawAmount) : (lostHp + lostEnergyShield);

            if (wasBulletHandled(b.id)) {
                if (lastRawTick == (int) Time.time && hpBeforeRaw >= 0f) {
                    deferredLethal = false;
                    health = Math.min(maxHealth, hpBeforeRaw);
                    shield = Math.max(shield, shieldBeforeRaw);
                }
                return;
            }

            float plateDist = hitSize * 0.56f + shieldBrace * 2.5f;
            float plateX = x + Angles.trnsx(rotation, plateDist);
            float plateY = y + Angles.trnsy(rotation, plateDist);

            if (Bulvasyr.isExplosiveBullet(b.type)) {
                if (!wasExplosiveCharged(b.id) && totalLost > 0f) {
                    markExplosiveCharged(b.id);
                    float ang = Angles.angle(x, y, b.x, b.y);
                    absorbPhysicalShield(totalLost * Bulvasyr.shieldDamageMultiplier(b.type), plateX, plateY, ang);
                }
                flushDeferredLethal();
                return;
            }

            float srcX = b.x, srcY = b.y;
            if (b.owner instanceof Position) {
                Position p = (Position) b.owner;
                srcX = p.getX();
                srcY = p.getY();
            }
            float angToSource = Angles.angle(x, y, srcX, srcY);
            boolean covered = isAngleCoveredByShield(angToSource)
                || (!Bulvasyr.isBeamOrInstantType(b.type) && isMovingBulletFromShieldSide(b, b.x, b.y));
            if (!covered) {
                flushDeferredLethal();
                return;
            }

            if (totalLost <= 0f) return;

            markBeamChargedThisTick(b.id);
            if (b.type instanceof PointLaserBulletType) {
                b.aimX = plateX;
                b.aimY = plateY;
            }

            float mult = Bulvasyr.shieldDamageMultiplier(b.type);
            float absorbedShield = absorbPhysicalShield(totalLost * mult, plateX, plateY, angToSource);
            float rawBlocked = absorbedShield / mult;

            if (deferredLethal) {
                deferredLethal = false;
                float overflowRaw = Math.max(0f, totalLost - rawBlocked);
                health = Math.min(maxHealth, hpBeforeRaw);
                shield = shieldBeforeRaw;
                if (overflowRaw > 0.01f) applyOverflowDamage(overflowRaw);
            } else {
                float refundHp = Math.min(lostHp, rawBlocked);
                health = Math.min(maxHealth, health + refundHp);
                float rem = rawBlocked - refundHp;
                if (rem > 0f && lostEnergyShield > 0f) {
                    shield += Math.min(lostEnergyShield, rem);
                }
            }
        }
    }

    // ================================================================== Nội tại: Vẽ & Animation Khiên Vật Lý Trước Ngực Tách Lớp

    public static class FrontShieldAbility extends Ability {
        @Override
        public void draw(Unit unit) { /* vẽ trực tiếp trong LancelotUnit.draw() để luôn khớp lớp độ sâu */ }

        public static void render(Unit unit) {
            if (!(unit instanceof LancelotUnit)) return;
            LancelotUnit lu = (LancelotUnit) unit;
            lu.ensureShieldInit();
            if (!lu.isShieldActive()) return;

            float ratio = lu.shieldRatio();
            float rot = lu.rotation;
            float fwdX = Angles.trnsx(rot, 1f), fwdY = Angles.trnsy(rot, 1f);
            float rightX = Angles.trnsx(rot - 90f, 1f), rightY = Angles.trnsy(rot - 90f, 1f);

            // Khoảng đẩy thủy lực trước ngực: đẩy tiến +3.4u khi giao tranh, giật lùi -2.5u khi đỡ đạn, nhấp nhô theo nhịp bước
            float brace = lu.shieldBrace;
            float fwdDist = 15.8f + brace * 3.4f - lu.shieldRecoil * 2.5f + lu.shieldBob * 0.45f;
            float sideSway = lu.shieldBob * 0.35f;

            float sx = lu.x + fwdX * fwdDist + rightX * sideSway;
            float sy = lu.y + fwdY * fwdDist + rightY * sideSway;

            float zPrev = Draw.z();
            Draw.z(Layer.groundUnit + 0.05f);

            if (!Vars.headless) {
                if (shieldCenterRegion == null) shieldCenterRegion = Core.atlas.find("newex-lancelot-shield");
                if (shieldWingRegion == null) shieldWingRegion = Core.atlas.find("newex-lancelot-shield-wing");
            }

            // Vẽ 2 thanh piston thủy lực nối từ ngực tới tấm khiên khi đang đẩy tiến
            if (brace > 0.05f) {
                Lines.stroke(2.2f);
                Draw.color(STEEL_LIGHT, 0.85f);
                float baseF = 11.5f;
                for (int s = -1; s <= 1; s += 2) {
                    float bx = lu.x + fwdX * baseF + rightX * (s * 4.8f);
                    float by = lu.y + fwdY * baseF + rightY * (s * 4.8f);
                    float ex = sx - fwdX * 3.5f + rightX * (s * 4.8f);
                    float ey = sy - fwdY * 3.5f + rightY * (s * 4.8f);
                    Lines.line(bx, by, ex, ey);
                }
            }

            Color tint = Tmp.c1.set(Color.white).lerp(GOLD_CORE, lu.shieldFlash * 0.75f);

            // 1) Vẽ 2 cánh giáp bản lề trái/phải tách riêng -> tự động mở rộng góc khi thủ thế chiến đấu!
            if (shieldWingRegion != null && shieldWingRegion.found()) {
                float wingOut = 10.6f + brace * 2.1f;
                float wingBack = -2.8f + brace * 1.1f;
                float wingAngle = 6f + brace * 18f - lu.shieldRecoil * 7f; // mở bản lề 6° -> 24°
                float ww = shieldWingRegion.width * Draw.scl;
                float wh = shieldWingRegion.height * Draw.scl;

                Draw.color(tint);
                // Cánh PHẢI
                float rx = sx + rightX * wingOut + fwdX * wingBack;
                float ry = sy + rightY * wingOut + fwdY * wingBack;
                Draw.rect(shieldWingRegion, rx, ry, ww, wh, rot - 90f - wingAngle);

                // Cánh TRÁI (lật ngang -ww)
                float lx = sx - rightX * wingOut + fwdX * wingBack;
                float ly = sy - rightY * wingOut + fwdY * wingBack;
                Draw.rect(shieldWingRegion, lx, ly, -ww, wh, rot - 90f + wingAngle);
            }

            // 2) Vẽ tấm khiên chính trước ngực
            if (shieldCenterRegion != null && shieldCenterRegion.found()) {
                Draw.color(tint);
                Draw.rect(shieldCenterRegion, sx, sy, rot - 90f);
            }

            // 3) Hiệu ứng mạch năng lượng vàng kim sáng lên ở khớp bản lề khi thủ thế hoặc trúng đạn
            float energyA = Math.max(brace * 0.45f, lu.shieldFlash * 0.9f);
            if (energyA > 0.04f) {
                Draw.blend(Blending.additive);
                Draw.color(GOLD_BRIGHT, energyA);
                Lines.stroke(1.4f + lu.shieldFlash * 1.4f);
                float seamW = 11.5f + brace * 1.5f;
                Lines.line(
                    sx - rightX * seamW + fwdX * 1.5f, sy - rightY * seamW + fwdY * 1.5f,
                    sx + rightX * seamW + fwdX * 1.5f, sy + rightY * seamW + fwdY * 1.5f
                );
                Draw.blend();
            }

            // Thanh độ bền khiên (chỉ đồng minh thấy)
            if (Vars.player != null && lu.team == Vars.player.team()) {
                Draw.z(Layer.overlayUI);
                float bw = lu.hitSize * 1.18f;
                float bh = 3.2f;
                float by = lu.y - lu.hitSize * 0.85f;
                Draw.color(Color.black, 0.65f);
                Fill.rect(lu.x, by, bw + 2f, bh + 2f);
                Draw.color(ratio > 0.3f ? GOLD_MID : SOLAR_RED, 0.95f);
                Fill.rect(lu.x - (bw * (1f - ratio)) * 0.5f, by, bw * ratio, bh);
            }

            Draw.z(zPrev);
            Draw.reset();
        }

        @Override
        public String localized() {
            return "Khiên Vật Lý Trước Ngực (Relic Aegis)";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Độ bền khiên trước ngực: [accent]" + Math.round(SHIELD_HP_MULT * 100f) + "% HP").row();
            t.add("[lightgray]Góc che chắn chính diện: [white]±" + Math.round(SHIELD_HALF_ARC) + "°").row();
            t.add("[lightgray]Tầm bắn (+22% Bulvasyr): [white]" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô");
        }
    }
}
