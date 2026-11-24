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
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.bullet.SapBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.EntityMapping;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
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
    public static final float SHIELD_HP_MULT = 5.0f;            // 500% HP của unit
    public static final float SHIELD_ANGLE_OFFSET = 90f;        // +90 độ = mạn TRÁI so với hướng quay của unit
    public static final float SHIELD_HALF_ARC = 68f;            // che phủ cung rộng dọc toàn bộ hông trái
    public static final float SHIELD_PHYSICAL_DMG_MULT = 1.5f;  // +50% sát thương vào khiên từ đạn vật lý
    public static final float SHIELD_ENERGY_DMG_MULT = 0.5f;    // -50% sát thương vào khiên từ đạn năng lượng / laser / tia nhắm thẳng

    // Bảng màu
    private static final Color OUTLINE_COL = Color.valueOf("232933");
    private static final Color ARC_OUTER = Color.valueOf("66d9ff");
    private static final Color LOCK_RED = Color.valueOf("ff3b30");
    private static final Color LOCK_CHARGE = Color.valueOf("38e57d");
    private static final Color RANGE_GOLD = Color.valueOf("ffd166");

    private static final Color MISSILE_GOLD = Color.valueOf("ffcf4d");
    private static final Color MISSILE_ORANGE = Color.valueOf("ff7b29");
    private static final Color MISSILE_CORE = Color.valueOf("fff4cc");

    // Tông Bulvasyr: xanh tím đậm + giáp thép xám, điểm nhấn đỏ, vent vàng
    private static final Color MISS_INDIGO = Color.valueOf("3b2fb8");
    private static final Color MISS_VIOLET = Color.valueOf("7a68ff");
    private static final Color MISS_STEEL = Color.valueOf("c9d0e0");
    private static final Color MISS_RED = Color.valueOf("e0303c");
    private static final Color MISS_YELLOW = Color.valueOf("ffd84a");
    private static final Color MISS_CYAN = Color.valueOf("5fe3ff");

    private static final Color SHIELD_BLUE = Color.valueOf("4654c8");
    private static final Color SHIELD_STEEL = Color.valueOf("9ca6ba");
    private static final Color SHIELD_RED = Color.valueOf("d64550");
    private static final Color SHIELD_SPARK = Color.valueOf("ffe394");

    private static boolean initialized = false;
    private static TextureRegion shieldRegion;
    private static final Seq<BulvasyrUnit> activeBulvasyrs = new Seq<>(false, 8);
    private static final ObjectMap<Class<?>, Boolean> energyClassCache = new ObjectMap<>(64);
    private static final ObjectMap<Class<?>, Boolean> beamClassCache = new ObjectMap<>(64);

    private static Effect shieldHitFx;
    private static Effect shieldBreakFx;
    private static Effect rocketMuzzleFx;
    private static Effect rocketHitFx;
    private static Effect rocketSmokeFx;
    private static Effect footstepFx;
    private static Effect starDustFx;
    private static Effect starPulseFx;

    /** Đạn gây vụ nổ diện rộng: chỉ loại này mới gây sát thương lên CẢ khiên lẫn unit. */
    public static boolean isExplosiveBullet(BulletType type) {
        return type != null && type.splashDamage > 0f && type.splashDamageRadius > 0f;
    }

    /** Phân loại đạn năng lượng / laser / tia laser nhắm thẳng (-50% sát thương lên khiên) vs đạn vật lý (+50% sát thương lên khiên). */
    public static boolean isEnergyBullet(BulletType type) {
        if (type == null) return false;
        if (type instanceof LaserBulletType
                || type instanceof ContinuousBulletType
                || type instanceof PointLaserBulletType
                || type instanceof SapBulletType
                || type instanceof LightningBulletType) {
            return true;
        }
        Class<?> cls = type.getClass();
        Boolean cached = energyClassCache.get(cls);
        if (cached != null) {
            if (cached) return true;
        } else {
            String sn = cls.getSimpleName().toLowerCase();
            boolean byClass = sn.contains("laser") || sn.contains("beam") || sn.contains("plasma")
                    || sn.contains("lightning") || sn.contains("electric") || sn.contains("arc")
                    || sn.contains("energy") || sn.contains("photon") || sn.contains("ion")
                    || sn.contains("emp") || sn.contains("pointbullet") || sn.contains("void")
                    || sn.contains("psi") || sn.contains("psychic");
            energyClassCache.put(cls, byClass);
            if (byClass) return true;
        }
        if (type instanceof BasicBulletType) {
            String sp = ((BasicBulletType) type).sprite;
            if (sp != null) {
                String ls = sp.toLowerCase();
                if (ls.contains("laser") || ls.contains("plasma") || ls.contains("energy")
                        || ls.contains("pulse") || ls.contains("beam") || ls.contains("orb")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Hệ số sát thương lên khiên vật lý: đạn năng lượng/laser = 0.5x (-50%), đạn vật lý = 1.5x (+50%). */
    public static float shieldDamageMultiplier(BulletType type) {
        return isEnergyBullet(type) ? SHIELD_ENERGY_DMG_MULT : SHIELD_PHYSICAL_DMG_MULT;
    }

    /** Các loại tia/đạn xuyên tức thời hoặc liên tục (laser, tia nhắm thẳng, mảnh shrapnel, railgun, sét). */
    public static boolean isBeamOrInstantType(BulletType type) {
        if (type == null) return false;
        if (type instanceof LaserBulletType
                || type instanceof ContinuousBulletType
                || type instanceof PointLaserBulletType
                || type instanceof RailBulletType
                || type instanceof SapBulletType
                || type instanceof LightningBulletType) {
            return true;
        }
        Class<?> cls = type.getClass();
        Boolean cached = beamClassCache.get(cls);
        if (cached != null) return cached;
        String sn = cls.getSimpleName().toLowerCase();
        boolean res = sn.contains("shrapnel") || sn.contains("pointbullet") || sn.contains("laser") || sn.contains("beam");
        beamClassCache.put(cls, res);
        return res;
    }

    /** Màu đạn trôi qua lại giữa tím và xanh lam theo thời gian/pha: nhìn liên tục chuyển sắc. */
    static Color starHue(float t) {
        return Tmp.c3.set(MISS_VIOLET).lerp(MISS_CYAN, 0.5f + 0.5f * Mathf.sin(t));
    }

    /** Ngôi sao 4 điểm: 2 cánh dài dọc theo rot, 2 cánh ngắn ngang. */
    static void drawStar(float x, float y, float rot, float len, float wid, float shortLen) {
        Drawf.tri(x, y, wid, len, rot);
        Drawf.tri(x, y, wid, len, rot + 180f);
        Drawf.tri(x, y, wid * 0.8f, shortLen, rot + 90f);
        Drawf.tri(x, y, wid * 0.8f, shortLen, rot - 90f);
    }

    private Bulvasyr() {}

    /** Gọi từ NexMod() constructor (và tự động gọi trong create() phòng trường hợp bị thiếu). */
    public static void init() {
        if (initialized) return;
        initialized = true;
        int ensureEntityRegistered = BulvasyrUnit.CLASS_ID; // ép đăng ký sớm, trước khi map/save được nạp

        Events.on(WorldLoadEvent.class, e -> activeBulvasyrs.clear());

        // Bắt sát thương laser / tia nhắm thẳng / shrapnel / railgun / vụ nổ cho cả Bulvasyr lẫn đồng đội đứng sau khiên
        Events.on(UnitDamageEvent.class, e -> {
            if (e.unit == null || e.bullet == null) return;
            if (e.unit instanceof BulvasyrUnit) {
                ((BulvasyrUnit) e.unit).handleBulletDamageEvent(e.bullet);
            } else if (!activeBulvasyrs.isEmpty() && isBeamOrInstantType(e.bullet.type) && !isExplosiveBullet(e.bullet.type)) {
                tryBlockLaserBehindShield(e.unit, e.bullet);
            }
        });

        // Đảm bảo sát thương chí mạng không đi qua UnitDamageEvent (ví dụ thiêu đốt / chết đuối) vẫn kết liễu đúng tick
        Events.run(Trigger.afterGameUpdate, () -> {
            for (int i = activeBulvasyrs.size - 1; i >= 0; i--) {
                BulvasyrUnit bu = activeBulvasyrs.get(i);
                if (bu == null || !bu.isAdded() || bu.dead) {
                    activeBulvasyrs.remove(i);
                    continue;
                }
                bu.flushDeferredLethal();
            }
        });
    }

    /** Chặn tia laser / shrapnel / railgun không cho bắn xuyên qua khiên Bulvasyr trúng đồng minh phía sau. */
    private static void tryBlockLaserBehindShield(Unit victim, Bullet b) {
        if (victim == null || victim.dead || b == null || b.team == victim.team) return;
        float srcX = b.x, srcY = b.y;
        if (b.owner instanceof Position) {
            Position p = (Position) b.owner;
            srcX = p.getX();
            srcY = p.getY();
        }
        for (int i = activeBulvasyrs.size - 1; i >= 0; i--) {
            BulvasyrUnit bu = activeBulvasyrs.get(i);
            if (bu == null || !bu.isAdded() || bu.dead) {
                activeBulvasyrs.remove(i);
                continue;
            }
            if (bu.team != victim.team || !bu.isShieldActive()) continue;
            if (!bu.doesSegmentHitShieldPlate(srcX, srcY, victim.x, victim.y)) continue;
            float angToSrc = Angles.angle(bu.x, bu.y, srcX, srcY);
            if (!bu.isAngleCoveredByShield(angToSrc)) continue;

            // Tia laser đã bị tấm khiên của Bulvasyr chắn lại trước khi chạm tới đồng minh phía sau!
            float mult = shieldDamageMultiplier(b.type);
            float rawDmg = Math.max(0f, b.damage);
            if (rawDmg <= 0f) return;

            // Chỉ trừ độ bền khiên 1 lần mỗi tick cho mỗi viên đạn xuyên (tránh trừ chồng nhiều lần khi nhiều đồng minh đứng sau)
            float blockedRaw = rawDmg;
            if (!bu.wasBeamChargedThisTick(b.id)) {
                bu.markBeamChargedThisTick(b.id);
                float shieldCost = rawDmg * mult;
                float absorbed = bu.absorbPhysicalShield(shieldCost, Tmp.v1.x, Tmp.v1.y, angToSrc);
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
        launchSound = Vars.headless ? Sounds.none : Sounds.shootSmite;

        shieldHitFx = new Effect(16f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(Color.white, SHIELD_SPARK, e.fin());
            Lines.stroke(1.6f * e.fout());
            Lines.circle(e.x, e.y, 2f + e.fin() * 10f);
            drawStar(e.x, e.y, e.rotation, 7f * e.fout(), 2.0f * e.fout(), 3.2f * e.fout());
            Angles.randLenVectors(e.id, 5, 14f * e.fin(), e.rotation, 65f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.8f * e.fout())
            );
            Draw.blend();
            Draw.reset();
        });

        shieldBreakFx = new Effect(64f, 240f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            float outAng = e.rotation;          // hướng văng ngang ra khỏi mạn trái (rotation + 90)
            float fwdAng = e.rotation - 90f;    // hướng mũi cơ giáp Bulvasyr
            float fwdX = Angles.trnsx(fwdAng, 1f), fwdY = Angles.trnsy(fwdAng, 1f);

            // 1) Khói áp suất thủy lực & bụi chiến trường bung dọc mạn trái
            Angles.randLenVectors(e.id + 51, 11, 8f + 46f * e.finpow(), outAng, 78f, (x, y) -> {
                Draw.color(Tmp.c1.set(SHIELD_STEEL).lerp(Color.darkGray, fin), 0.42f * fout);
                Fill.circle(e.x + x, e.y + y, 2.5f + 6.5f * e.fin(Interp.pow2Out));
            });

            // 2) Tấm khiên vật lý nứt toác làm 3 mảng giáp lớn rồi văng xoay 3D ra ngoài (không dùng đường thẳng)
            if (fin < 0.72f) {
                float p = fin / 0.72f;
                float ep = Interp.pow3Out.apply(p);
                float fadePlate = 1f - Interp.pow2In.apply(p);
                for (int seg = -1; seg <= 1; seg++) {
                    float segAng = outAng + seg * 28f + (Mathf.randomSeedRange(e.id + seg * 17, 14f));
                    float dist = 4f + ep * (32f + Math.abs(seg) * 8f);
                    float cx = e.x + fwdX * (seg * 11f) + Angles.trnsx(segAng, dist);
                    float cy = e.y + fwdY * (seg * 11f) + Angles.trnsy(segAng, dist);
                    float spin = fwdAng + seg * (18f + ep * 95f);
                    float tilt3D = 0.35f + 0.65f * Math.abs(Mathf.cos(p * 5.2f + seg));
                    float w = 8.5f * tilt3D * (1f - 0.25f * p);
                    float h = 11.5f * (1f - 0.2f * p);
                    float sx = Angles.trnsx(spin, 1f), sy = Angles.trnsy(spin, 1f);
                    float rx = Angles.trnsx(spin - 90f, 1f), ry = Angles.trnsy(spin - 90f, 1f);

                    float x1 = cx + sx * h - rx * w, y1 = cy + sy * h - ry * w;
                    float x2 = cx + sx * h + rx * w, y2 = cy + sy * h + ry * w;
                    float x3 = cx - sx * h + rx * (w * 0.7f), y3 = cy - sy * h + ry * (w * 0.7f);
                    float x4 = cx - sx * h - rx * (w * 0.85f), y4 = cy - sy * h - ry * (w * 0.85f);

                    Draw.color(OUTLINE_COL, 0.92f * fadePlate);
                    Fill.quad(x1, y1, x2, y2, x3, y3, x4, y4);
                    Draw.color(Tmp.c1.set(SHIELD_BLUE).lerp(SHIELD_SPARK, Math.max(0f, 1f - p * 2.4f)), 0.95f * fadePlate);
                    Fill.quad(
                        Mathf.lerp(cx, x1, 0.82f), Mathf.lerp(cy, y1, 0.82f),
                        Mathf.lerp(cx, x2, 0.82f), Mathf.lerp(cy, y2, 0.82f),
                        Mathf.lerp(cx, x3, 0.82f), Mathf.lerp(cy, y3, 0.82f),
                        Mathf.lerp(cx, x4, 0.82f), Mathf.lerp(cy, y4, 0.82f)
                    );
                }
            }

            // 3) 22 mảnh giáp đa giác xoay 3D & hạt cầu sáng (tuyệt đối không dùng đường thẳng quanh vòng tròn)
            Angles.randLenVectors(e.id, 22, 10f + 72f * e.finpow(), outAng, 88f, (x, y) -> {
                int seed = e.id + (int) (x * 13f + y * 7f);
                float sx = e.x + x, sy = e.y + y;
                float flip = 0.25f + 0.75f * Math.abs(Mathf.cos(fin * 9f + seed));
                float sz = (2.8f + Mathf.randomSeed(seed, 0f, 3.8f)) * fout;
                float rot = e.rotation + x * 5.5f + fin * 160f;

                Draw.color(OUTLINE_COL, 0.85f * fout);
                Fill.poly(sx, sy, 4, sz * 1.22f * flip, rot);
                boolean isCyan = (seed & 3) == 0;
                Draw.color(isCyan ? MISS_CYAN : ((seed & 1) == 0 ? SHIELD_BLUE : SHIELD_STEEL), 0.95f * fout);
                Fill.poly(sx, sy, 4, sz * flip, rot);

                if (fin < 0.65f) {
                    Draw.blend(Blending.additive);
                    Draw.color(isCyan ? MISS_CYAN : SHIELD_SPARK, 0.55f * fout);
                    Fill.circle(sx, sy, sz * 0.95f);
                    Draw.blend();
                }
            });

            Draw.blend(Blending.additive);

            // 4) Sóng xung kích lưỡi liềm mềm mượt quét ra mạn trái (dải quad mịn, không kẻ đường thẳng)
            float cresR = 12f + e.fin(Interp.pow3Out) * 68f;
            int arcSegs = 18;
            float halfArc = 68f;
            for (int i = 0; i < arcSegs; i++) {
                float f1 = (float) i / arcSegs, f2 = (float) (i + 1) / arcSegs;
                float a1 = outAng - halfArc + halfArc * 2f * f1;
                float a2 = outAng - halfArc + halfArc * 2f * f2;
                float env1 = Mathf.sin(f1 * Mathf.PI), env2 = Mathf.sin(f2 * Mathf.PI);
                float th1 = (12f * a + 2f) * env1, th2 = (12f * a + 2f) * env2;

                Draw.color(MISS_VIOLET, MISS_CYAN, f1);
                Draw.alpha(0.75f * a * ((env1 + env2) * 0.5f));
                Fill.quad(
                    e.x + Angles.trnsx(a1, cresR - th1), e.y + Angles.trnsy(a1, cresR - th1),
                    e.x + Angles.trnsx(a1, cresR), e.y + Angles.trnsy(a1, cresR),
                    e.x + Angles.trnsx(a2, cresR), e.y + Angles.trnsy(a2, cresR),
                    e.x + Angles.trnsx(a2, cresR - th2), e.y + Angles.trnsy(a2, cresR - th2)
                );
            }

            // 5) Vòng sóng tròn đồng tâm mượt mà (không có bất kỳ đường thẳng hay tia sét thẳng nào quanh vòng tròn)
            Lines.stroke(3.2f * a);
            Draw.color(SHIELD_SPARK, MISS_VIOLET, fin);
            Lines.circle(e.x, e.y, 8f + e.fin(Interp.pow3Out) * 54f);

            Lines.stroke(1.8f * a);
            Draw.color(MISS_CYAN, SHIELD_BLUE, fin);
            Lines.circle(e.x, e.y, 5f + e.fin(Interp.pow2Out) * 38f);

            // 6) Các ngôi sao 4 cánh nhỏ lấp lánh bung theo luồng nổ
            Angles.randLenVectors(e.id + 23, 8, 8f + 52f * e.fin(Interp.pow2Out), outAng, 75f, (x, y) -> {
                Draw.color(MISS_CYAN, Color.white, 0.5f);
                Draw.alpha(0.85f * a);
                drawStar(e.x + x, e.y + y, 0f, 7.5f * a, 1.8f * a, 3.2f * a);
            });

            // 7) Chớp sao 4 cánh khổng lồ tại tâm vỡ khiên
            float starGrow = e.fout(Interp.pow2Out);
            Draw.color(MISS_VIOLET, 0.75f * a);
            drawStar(e.x, e.y, outAng + 45f, 44f * starGrow + 6f, 8.5f * a, 22f * starGrow);
            Draw.color(MISS_CYAN, 0.92f * a);
            drawStar(e.x, e.y, outAng, 36f * starGrow + 4f, 5.5f * a, 16f * starGrow);
            Draw.color(Color.white, a);
            drawStar(e.x, e.y, outAng, 22f * starGrow, 3.0f * a, 9.5f * starGrow);
            Fill.circle(e.x, e.y, 11f * a);

            Draw.blend();
            Drawf.light(e.x, e.y, 115f * a, MISS_CYAN, 0.95f * a);
            Draw.reset();
        }).layer(Layer.effect + 0.25f);

        // Bụi sao: các ngôi sao 2 cánh nhỏ lấp lánh rơi lại dọc đường bay (giảm 75% kích thước)
        starDustFx = new Effect(36f, e -> {
            float tw = 0.55f + 0.45f * Mathf.absin(e.id * 0.7f + e.time, 3.2f, 1f);
            Draw.blend(Blending.additive);
            Draw.color(e.color, Color.white, 0.4f * e.fout());
            Draw.alpha(e.fout() * tw);
            drawStar(e.x, e.y - 0.75f * e.fin(), 0f, (5.2f * e.fout() + 0.8f) * 0.25f, (1.0f * e.fout() + 0.2f) * 0.25f, 2.2f * e.fout() * 0.25f);
            Draw.blend();
            Draw.reset();
        });

        // Nhịp ngân: vòng sáng mỏng nở ra khi ngôi sao đạt cỡ lớn nhất giữa đường bay (giảm 75% kích thước)
        starPulseFx = new Effect(28f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(e.color, 0.75f * e.fout());
            Lines.stroke((1.6f * e.fout() + 0.3f) * 0.4f);
            Lines.circle(e.x, e.y, (4f + e.fin(Interp.pow2Out) * 22f) * 0.25f);
            Draw.blend();
            Draw.reset();
        });

        // Bước chân nặng: vòng chấn + bụi bung + hạt sáng tím (không dùng đường thẳng quanh vòng tròn)
        footstepFx = new Effect(30f, 70f, e -> {
            Draw.color(MISS_STEEL, 0.55f * e.fout());
            Lines.stroke(1.6f * e.fout());
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow2Out) * 15f);
            Angles.randLenVectors(e.id, 6, 3f + 14f * e.finpow(), (x, y) -> {
                Draw.color(Tmp.c1.set(Color.valueOf("8d93a6")).lerp(Color.gray, e.fin()), 0.45f * e.fout());
                Fill.circle(e.x + x, e.y + y, 1.6f + 2.8f * e.fin());
            });
            Draw.color(MISS_VIOLET, MISS_YELLOW, e.fin());
            Angles.randLenVectors(e.id + 5, 4, 4f + 12f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.4f * e.fout())
            );
            Draw.reset();
        }).layer(Layer.groundUnit - 1f); // nằm dưới unit

        // Khói vệt đuôi: tím xám nở dần, tan chậm (giảm 75% kích thước)
        rocketSmokeFx = new Effect(38f, e -> {
            Draw.color(Tmp.c1.set(MISS_VIOLET).lerp(Color.gray, e.fin()), 0.5f * e.fout());
            Fill.circle(e.x, e.y, (1.4f + 3.6f * e.fin(Interp.pow2Out)) * 0.25f);
        });

        // Chớp lửa nòng: tia vàng-tím, vòng xung kích và khói phụt ngược
        rocketMuzzleFx = new Effect(34f, 100f, e -> {
            Draw.color(MISS_YELLOW, MISS_VIOLET, e.fin());
            Drawf.tri(e.x, e.y, 8f * e.fout(), 34f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(Color.white, e.fout());
            Drawf.tri(e.x, e.y, 3.5f * e.fout(), 16f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(MISS_STEEL, 0.8f * e.fout());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, 3f + e.fin(Interp.pow3Out) * 22f);
            Angles.randLenVectors(e.id, 8, 42f * e.finpow(), e.rotation + 180f, 36f, (x, y) -> {
                Draw.color(Tmp.c2.set(MISS_STEEL).lerp(Color.gray, e.fin()), 0.5f * e.fout());
                Fill.circle(e.x + x, e.y + y, 1.8f + 3.2f * e.fin());
            });
            Drawf.light(e.x, e.y, 46f * e.fout(), MISS_VIOLET, 0.7f * e.fout());
        });

        // Va chạm: ngôi sao 2 cánh dài phóng to dần và tan chậm (không dùng đường thẳng quanh vòng tròn)
        rocketHitFx = new Effect(58f, 120f, e -> {
            float grow = (0.45f + 8f * e.fin(Interp.pow2Out)) * 0.25f;
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            Draw.color(MISS_VIOLET, 0.55f * a);
            drawStar(e.x, e.y, 0f, 17f * grow, 4.2f * grow, 6f * grow);
            Draw.color(MISS_STEEL, 0.9f * a);
            drawStar(e.x, e.y, 0f, 12f * grow, 2.4f * grow, 4f * grow);
            Draw.color(Color.white, a);
            drawStar(e.x, e.y, 0f, 7f * grow, 1.2f * grow, 2.2f * grow);
            Draw.blend();
            Draw.color(MISS_STEEL, 0.6f * a);
            Lines.stroke(1.6f * a * 0.5f);
            Lines.circle(e.x, e.y, e.fin(Interp.pow2Out) * 38f * 0.25f);
            Draw.color(MISS_YELLOW, MISS_RED, e.fin());
            Angles.randLenVectors(e.id + 7, 8, (6f + 40f * e.finpow()) * 0.25f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.2f * a)
            );
            Draw.reset();
        });

        final SalvoMissileBulletType missileBullet = new SalvoMissileBulletType();

        bulvasyr = new UnitType("bulvasyr") {{
            constructor = BulvasyrUnit::new;

            localizedName = "Bulvasyr";
            description = "Cơ giáp 2 chân hạng nặng trang bị khiên vật lý mạn trái (500% HP) và 3 cụm pháo tên lửa tổ ong tự dẫn hướng.";
            details = "Khóa mục tiêu trong 1.5 giây giống Aureon rồi phóng 2 đợt liên tiếp gồm 24 tên lửa cong tự dẫn hướng, nổ diện rộng và bỏ qua giáp (nạp lại 5 giây). "
                + "Tấm khiên vật lý bên hông trái có độ bền 500% HP, chặn mọi loại đạn, mảnh đạn, laser và tia laser nhắm thẳng (không thể bắn xuyên qua); chỉ có đạn gây vụ nổ mới gây sát thương lên cả khiên lẫn unit. "
                + "Khiên nhận +50% sát thương từ đạn vật lý và giảm 50% sát thương từ đạn năng lượng/laser.";

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

            // Robot 2 chân (Mech): bước luân phiên trái/phải như Dagger/Fortress, sprite bulvasyr-leg vẽ đối xứng 2 bên
            hovering = false;
            canBoost = false;
            mechStride = 11f;        // sải bước dài hơn: mỗi bước nhìn rõ chân vươn ra
            mechFrontSway = 0.85f;   // thân chúi nhấp nhô theo nhịp bước
            mechSideSway = 0.55f;    // thân đảo trái phải như cơ giáp hạng nặng
            mechStepParticles = true;
            stepShake = 1.4f;

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

    public static class BulvasyrUnit extends MechUnit {
        /** Đăng ký classId riêng: nếu không, vào lại map/tải save unit bị dựng thành MechUnit thường => mất khiên. */
        public static final int CLASS_ID = EntityMapping.register("newex-bulvasyr-unit", BulvasyrUnit::new);

        @Override
        public int classId() {
            return CLASS_ID;
        }

        public float physShieldMax = -1f;
        public float physShieldHp = 0f;
        public boolean physShieldDamaged = false;
        public float shieldFlash = 0f;
        private int ageTicks = 0;

        /** Bộ đệm vòng theo dõi nhiều viên đạn xử lý cùng lúc (tránh sót & tránh trừ 2 lần). */
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
            for (int i = 0; i < handledBulletIds.length; i++) {
                if (handledBulletIds[i] == id) return true;
            }
            return false;
        }

        private void markBulletHandled(int id) {
            handledBulletIds[handledBulletIdx] = id;
            handledBulletIdx = (handledBulletIdx + 1) & 15;
        }

        private boolean wasExplosiveCharged(int id) {
            for (int i = 0; i < handledExplosiveIds.length; i++) {
                if (handledExplosiveIds[i] == id) return true;
            }
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
            for (int i = 0; i < beamTickCount; i++) {
                if (beamTickBulletIds[i] == id) return true;
            }
            return false;
        }

        void markBeamChargedThisTick(int id) {
            int tick = (int) Time.time;
            if (beamTickStamp != tick) {
                beamTickStamp = tick;
                beamTickCount = 0;
            }
            if (beamTickCount < beamTickBulletIds.length) {
                beamTickBulletIds[beamTickCount++] = id;
            }
        }

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

        /** Kiểm tra đoạn thẳng (ax, ay) -> (bx, by) có cắt ngang tấm khiên mạn trái của Bulvasyr không (ghi điểm cắt vào Tmp.v1). */
        public boolean doesSegmentHitShieldPlate(float ax, float ay, float bx, float by) {
            float fwdX = Angles.trnsx(rotation, 1f), fwdY = Angles.trnsy(rotation, 1f);
            float leftX = Angles.trnsx(rotation + SHIELD_ANGLE_OFFSET, 1f), leftY = Angles.trnsy(rotation + SHIELD_ANGLE_OFFSET, 1f);
            float plateDist = hitSize * 0.56f;
            float halfLen = hitSize * 0.72f;
            float sx1 = x + leftX * plateDist + fwdX * halfLen;
            float sy1 = y + leftY * plateDist + fwdY * halfLen;
            float sx2 = x + leftX * plateDist - fwdX * halfLen;
            float sy2 = y + leftY * plateDist - fwdY * halfLen;
            return Intersector.intersectSegments(ax, ay, bx, by, sx1, sy1, sx2, sy2, Tmp.v1);
        }

        /**
         * Hấp thụ sát thương (đã nhân hệ số vật lý 1.5x / năng lượng 0.5x) vào khiên vật lý.
         * Trả về lượng sát thương khiên đã thực sự hấp thụ.
         */
        public float absorbPhysicalShield(float shieldDmg, float hitX, float hitY, float hitAngle) {
            ensureShieldInit();
            if (shieldDmg <= 0f || physShieldHp <= 0f) return 0f;

            physShieldDamaged = true;
            shieldFlash = 1f;

            float absorbed = Math.min(physShieldHp, shieldDmg);
            physShieldHp -= absorbed;

            if (physShieldHp <= 0.01f) {
                physShieldHp = 0f;
                float shieldFacing = rotation + SHIELD_ANGLE_OFFSET;
                float plateX = x + Angles.trnsx(shieldFacing, hitSize * 0.48f);
                float plateY = y + Angles.trnsy(shieldFacing, hitSize * 0.48f);
                shieldHitFx.at(hitX, hitY, hitAngle);
                shieldBreakFx.at(plateX, plateY, shieldFacing);
                Effect.shake(5.4f, 24f, plateX, plateY);
                if (!Vars.headless) {
                    Sounds.explosion.at(plateX, plateY, 0.82f, 1.0f);
                    Sounds.shootSmite.at(plateX, plateY, 0.72f, 0.85f);
                }
            } else {
                shieldHitFx.at(hitX, hitY, hitAngle);
            }
            return absorbed;
        }

        /** Kiểm tra một viên đạn di chuyển có đang tiếp cận từ phía mạn có khiên hay cắt qua tấm khiên không. */
        private boolean isMovingBulletFromShieldSide(Bullet b, float hitX, float hitY) {
            float angToHit = Angles.angle(x, y, hitX, hitY);
            if (isAngleCoveredByShield(angToHit)) return true;
            float incomingSide = b.rotation() + 180f;
            if (isAngleCoveredByShield(incomingSide)) return true;
            float step = Math.max(1f, Time.delta);
            float prevX = b.x - b.vel.x * step, prevY = b.y - b.vel.y * step;
            float nextX = b.x + b.vel.x * step, nextY = b.y + b.vel.y * step;
            return doesSegmentHitShieldPlate(prevX, prevY, nextX, nextY);
        }

        /**
         * Xử lý đạn bay (vật lý, năng lượng, xuyên thấu, mảnh đạn, đạn nổ) khi va vào tấm khiên mạn trái.
         * Trả về true nếu viên đạn đã bị chặn hoàn toàn (không để chạy collision gốc / không cho xuyên qua).
         */
        private boolean processShieldProjectile(Bullet b, float hx, float hy) {
            if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return false;
            if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return true;

            float angToHit = Angles.angle(x, y, hx, hy);
            float mult = shieldDamageMultiplier(b.type);

            if (isExplosiveBullet(b.type)) {
                // Đạn gây vụ nổ: gây sát thương cho CẢ khiên lẫn unit, nhưng vẫn chặn đạn không xuyên qua và không cho mảnh đạn bay qua!
                markExplosiveCharged(b.id);
                float directDmg = Math.max(0f, b.damage);
                float splashDmg = Math.max(0f, b.type.splashDamage);
                float shieldCost = (directDmg + splashDmg) * mult;
                if (shieldCost > 0f) {
                    absorbPhysicalShield(shieldCost, hx, hy, angToHit);
                }

                // Kích nổ sát thương diện rộng ngay tại điểm va chạm trên khiên (gây sát thương vụ nổ lên unit trong bán kính)
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
                // Hấp thụ vỏ đạn để không xuyên qua khiên và không sinh mảnh đạn (fragBullets) xuyên qua sau lưng
                b.absorb();
                return true;
            }

            // Đạn không gây vụ nổ (kể cả đạn xuyên thấu, mảnh đạn, laser bolt, năng lượng): chặn hoàn toàn, unit 0 sát thương!
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
                // Khiên vừa vỡ: quy đổi phần sát thương dư về sát thương gốc cho unit
                markBulletHandled(b.id);
                b.damage = overflowShield / mult;
                return false;
            }
        }

        private int lastStep = -1;

        @Override
        public void add() {
            super.add();
            if (!activeBulvasyrs.contains(this, true)) activeBulvasyrs.add(this);
        }

        @Override
        public void remove() {
            activeBulvasyrs.remove(this, true);
            super.remove();
        }

        @Override
        public void draw() {
            super.draw();
            PhysicalShieldAbility.render(this);
        }

        /** Mỗi lần một chân đạp xuống: bụi, tia lửa tím, sóng chấn nhỏ và rung nhẹ màn hình. */
        private void footstepCinematic() {
            if (Vars.headless || isFlying() || vel.len() < 0.12f) return;
            int step = (int) Math.floor(walkExtend(true) / 2f);
            if (lastStep == -1) { lastStep = step; return; }
            if (step == lastStep) return;
            lastStep = step;
            float side = (step & 1) == 0 ? 1f : -1f;
            float fx = x + Angles.trnsx(baseRotation + 90f, side * 9.5f) + Angles.trnsx(baseRotation, 8f);
            float fy = y + Angles.trnsy(baseRotation + 90f, side * 9.5f) + Angles.trnsy(baseRotation, 8f);
            footstepFx.at(fx, fy, baseRotation);
            Effect.shake(1.1f, 6f, fx, fy);
        }

        @Override
        public void update() {
            if (!dead && isAdded() && !activeBulvasyrs.contains(this, true)) activeBulvasyrs.add(this);
            flushDeferredLethal();
            ageTicks++;
            footstepCinematic();
            ensureShieldInit();
            if (shieldFlash > 0f) shieldFlash = Math.max(0f, shieldFlash - Time.delta / 10f);

            // Lớp 1: Chủ động chặn đạn, mảnh đạn, và ghim tia laser nhắm thẳng ngay tại mặt khiên mạn trái
            if (isShieldActive() && !dead) {
                interceptLeftFlankBullets();
            }

            super.update();
        }

        private void interceptLeftFlankBullets() {
            float r = hitSize * 1.25f + 20f;
            float shieldFacing = rotation + SHIELD_ANGLE_OFFSET;
            final float plateX = x + Angles.trnsx(shieldFacing, hitSize * 0.52f);
            final float plateY = y + Angles.trnsy(shieldFacing, hitSize * 0.52f);

            Groups.bullet.intersect(x - r, y - r, r * 2f, r * 2f, b -> {
                if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return;

                // Tia laser nhắm thẳng vào unit (PointLaserBulletType): giữ điểm chiếu trên mặt khiên (sát thương xử lý tại Lớp 3)
                if (b.type instanceof PointLaserBulletType) {
                    float srcX = b.x, srcY = b.y;
                    if (b.owner instanceof Position) {
                        srcX = ((Position) b.owner).getX();
                        srcY = ((Position) b.owner).getY();
                    }
                    if (isAngleCoveredByShield(Angles.angle(x, y, srcX, srcY)) && Mathf.within(x, y, b.aimX, b.aimY, hitSize * 0.9f)) {
                        b.aimX = plateX;
                        b.aimY = plateY;
                    }
                    return;
                }

                // Các tia laser/chùm tức thời & liên tục xử lý sát thương ở Lớp 3 (handleBulletDamageEvent + tryBlockLaserBehindShield)
                if (isBeamOrInstantType(b.type)) return;

                if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return;

                float step = Math.max(1f, Time.delta);
                float prevX = b.x - b.vel.x * step, prevY = b.y - b.vel.y * step;
                float nextX = b.x + b.vel.x * step, nextY = b.y + b.vel.y * step;
                boolean hitSeg = doesSegmentHitShieldPlate(prevX, prevY, nextX, nextY);

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

        /** Lớp 2: Chặn trực tiếp ngay trong collision() — mọi đạn nhanh, đạn xuyên, mảnh đạn sinh sát thân đều không thể lọt qua! */
        @Override
        public void collision(Hitboxc other, float cx, float cy) {
            if (other instanceof Bullet) {
                Bullet b = (Bullet) other;
                if (!dead && isShieldActive() && b.team != team && !isBeamOrInstantType(b.type)) {
                    if (isMovingBulletFromShieldSide(b, cx, cy)) {
                        if (processShieldProjectile(b, cx, cy)) {
                            return;
                        }
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
            // Lưu trạng thái trước khi trừ máu để Lớp 3 có thể hoàn lại 100% nếu trúng khiên
            hpBeforeRaw = health;
            shieldBeforeRaw = shield;
            lastRawAmount = amount;
            lastRawTick = (int) Time.time;
            deferredLethal = false;

            // Bảo vệ chống bị kết liễu (kill/remove) bởi 1 phát laser siêu lớn TRƯỚC KHI UnitDamageEvent kịp kiểm tra khiên
            if (isShieldActive() && amount > 0f && (health + shield - amount) <= 0f) {
                deferredLethal = true;
                float safeAmt = Math.max(0f, health + shield - 0.01f);
                super.rawDamage(safeAmt);
                if (health <= 0f) health = 0.01f;
                return;
            }

            super.rawDamage(amount);
        }

        /** Lớp 3: Xử lý laser, tia liên tục, tia laser nhắm thẳng, mảnh shrapnel, railgun, sét và sát thương vụ nổ qua UnitDamageEvent. */
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

            // Nếu là viên đạn đã được chặn hoàn toàn ở Lớp 1/2 mà vẫn có sự kiện dư -> hoàn trả sạch sát thương
            if (wasBulletHandled(b.id)) {
                if (lastRawTick == (int) Time.time && hpBeforeRaw >= 0f) {
                    deferredLethal = false;
                    health = Math.min(maxHealth, hpBeforeRaw);
                    shield = Math.max(shield, shieldBeforeRaw);
                }
                return;
            }

            float plateX = x + Angles.trnsx(rotation + SHIELD_ANGLE_OFFSET, hitSize * 0.52f);
            float plateY = y + Angles.trnsy(rotation + SHIELD_ANGLE_OFFSET, hitSize * 0.52f);

            // Đạn gây vụ nổ: CHỈ loại này gây sát thương cho CẢ khiên lẫn unit!
            if (isExplosiveBullet(b.type)) {
                if (!wasExplosiveCharged(b.id) && totalLost > 0f) {
                    markExplosiveCharged(b.id);
                    float ang = Angles.angle(x, y, b.x, b.y);
                    float mult = shieldDamageMultiplier(b.type);
                    absorbPhysicalShield(totalLost * mult, plateX, plateY, ang);
                }
                // Không hoàn trả máu cho unit (vụ nổ gây sát thương cả khiên lẫn unit)
                flushDeferredLethal();
                return;
            }

            // Xác định hướng nguồn tấn công (ưu tiên vị trí chủ sở hữu cho laser, tia nhắm thẳng, tia sét)
            float srcX = b.x, srcY = b.y;
            if (b.owner instanceof Position) {
                Position p = (Position) b.owner;
                srcX = p.getX();
                srcY = p.getY();
            }
            float angToSource = Angles.angle(x, y, srcX, srcY);
            boolean covered = isAngleCoveredByShield(angToSource)
                    || (!isBeamOrInstantType(b.type) && isMovingBulletFromShieldSide(b, b.x, b.y));
            if (!covered) {
                // Trúng mạn không có khiên -> thực thi sát thương/kết liễu bình thường
                flushDeferredLethal();
                return;
            }

            if (totalLost <= 0f) return;

            // Đánh dấu đã trừ khiên cho tia này ở tick hiện tại để tryBlockLaserBehindShield không trừ trùng
            markBeamChargedThisTick(b.id);

            // Ghim điểm cuối của tia laser nhắm thẳng lên mặt khiên
            if (b.type instanceof PointLaserBulletType) {
                b.aimX = plateX;
                b.aimY = plateY;
            }

            // Áp dụng hệ số: đạn năng lượng/laser = -50% (0.5x), đạn vật lý = +50% (1.5x)
            float mult = shieldDamageMultiplier(b.type);
            float shieldCost = totalLost * mult;
            float absorbedShield = absorbPhysicalShield(shieldCost, plateX, plateY, angToSource);
            float rawBlocked = absorbedShield / mult;

            if (deferredLethal) {
                deferredLethal = false;
                float overflowRaw = Math.max(0f, totalLost - rawBlocked);
                health = Math.min(maxHealth, hpBeforeRaw);
                shield = shieldBeforeRaw;
                if (overflowRaw > 0.01f) {
                    applyOverflowDamage(overflowRaw);
                }
            } else {
                // Hoàn trả đúng phần sát thương khiên đã chặn (nếu khiên chưa vỡ -> hoàn 100% HP/khiên năng lượng)
                float refundHp = Math.min(lostHp, rawBlocked);
                health = Math.min(maxHealth, health + refundHp);
                float rem = rawBlocked - refundHp;
                if (rem > 0f) {
                    shield += Math.min(lostEnergyShield, rem);
                }
                if (health > 0f) dead = false;
            }
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

            // Cả chùm bay cùng nhau như bắn thẳng, nhưng mỗi chùm lệch một hướng và độ cong ngẫu nhiên riêng
            final float burstArc = Mathf.randomSign() * Mathf.random(18f, 55f);
            final float burstTurn = Mathf.random(2.4f, 4.0f);

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
                    float arcSpread = burstArc + (idx - 1.5f) * 3f + Mathf.range(1.5f); // cả chùm cùng lệch về một phía, chỉ xòe rất nhẹ
                    float launchAngle = weaponRot + arcSpread;

                    float swirlPhase = idx * (Mathf.PI / 2f) + x * 0.37f;
                    SwirlData sdNew = new SwirlData(target, swirlPhase);
                    sdNew.turn = burstTurn + Mathf.range(0.12f); // cả chùm uốn cong cùng nhịp
                    sdNew.idx = idx;
                    Bullet b = bullet.create(unit, unit.team, sx, sy, launchAngle, -1f, 0.92f + idx * 0.04f, 1f, sdNew);
                    if (b != null) {
                        float atk = calcAtk(unit);
                        b.damage = atk * ROCKET_DIRECT_RATIO;
                    }

                    mount.recoil = 1f;
                    rocketMuzzleFx.at(sx, sy, launchAngle);
                    Effect.shake(1.3f, 7f, sx, sy);
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
        public float minDst = 1e9f;
        public boolean passed, expiring, pulsed;
        public int idx;
        public float turn = 3f;          // tốc độ uốn cong (độ/tick), mỗi quả ngẫu nhiên
        public float startDst = -1f;     // khoảng cách tới mục tiêu lúc mới bay
        public float expireT, scExp = 1f; // mốc bắt đầu 0,5s cuối khi trượt mục tiêu
        public SwirlData(Teamc target, float phase) { this.target = target; this.phase = phase; }
    }

    public static class SalvoMissileBulletType extends MissileBulletType {
        public SalvoMissileBulletType() {
            super(5.6f, 70f);
            lifetime = 100f;
            width = 7.5f * 0.25f;
            height = 13f * 0.25f;
            shrinkY = 0f;
            sprite = "missile-large";

            pierceArmor = true; // Bỏ qua hệ thống phòng thủ (ảnh 3)
            splashDamage = 110f;
            splashDamageRadius = 34f;
            scaledSplashDamage = true;

            homingPower = 0.16f;
            homingRange = RANGE * 1.4f;
            homingDelay = 12f; // bay lệch tạo vòng cung trong 0.15s đầu rồi bẻ lái lao thẳng vào mục tiêu

            keepVelocity = false;
            collidesAir = true;
            collidesGround = true;

            frontColor = MISS_STEEL;
            backColor = MISS_INDIGO;
            trailColor = MISS_VIOLET;
            hitColor = MISS_VIOLET;
            trailWidth = 3.1f * 0.25f;
            trailLength = 30;
            trailChance = 0.9f;
            trailEffect = rocketSmokeFx;

            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            despawnHit = true; // hết giờ (trượt mục tiêu) cũng nổ diện rộng
            hitEffect = rocketHitFx;
            despawnEffect = rocketHitFx;
            hitSound = Sounds.explosion;
        }

        @Override
        public void updateHoming(Bullet b) {
            if (b.time < homingDelay) return;

            SwirlData sd = (b.data instanceof SwirlData) ? (SwirlData) b.data : null;
            if (sd != null && sd.passed) return; // đã lướt qua mục tiêu: bay thẳng thêm 0,5s rồi nổ
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
                b.vel.setAngle(Angles.moveToward(b.rotation(), b.angleTo(target), (sd != null ? sd.turn : 3f) * Mathf.clamp(0.5f + (b.time - homingDelay) / 20f, 0.5f, 1.5f) * Time.delta));
            }
        }

        /** Dịch vị trí đạn ngang quỹ đạo theo sin: nhìn từ trên xuống, xoắn ốc 3D chính là sóng lượn quanh trục bay. */
        /** Cỡ sao theo hành trình: nhỏ lúc rời nòng, to dần tới giữa đoạn, nhỏ lại khi sắp va chạm. */
        private static float starScale(Bullet b, SwirlData sd) {
            float prog;
            if (isTargetAlive(sd.target) && sd.startDst > 1f) prog = Mathf.clamp(1f - (b.dst(sd.target) - 8f) / sd.startDst);
            else prog = Mathf.clamp(b.time / Math.max(1f, b.lifetime));
            return Mathf.lerp(0.3f, 1.7f, Mathf.sin(prog * Mathf.PI));
        }

        private static float swirlOffset(Bullet b, SwirlData sd) {
            float t = b.time;
            float env = Mathf.clamp(t / SWIRL_RAMP);
            if (isTargetAlive(sd.target)) env *= Mathf.clamp((b.dst(sd.target) - 12f) / SWIRL_END);
            return SWIRL_AMP * env * Mathf.sin(sd.phase + t * SWIRL_SPEED);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            // Điện ảnh: rời nòng chậm, động cơ bùng lên rồi vọt tới tốc độ tối đa
            b.vel.setLength(Mathf.lerp(2.3f, 8.4f, Interp.pow2Out.apply(Mathf.clamp((b.time - 4f) / 28f))));
            if (!(b.data instanceof SwirlData)) return;
            SwirlData sd = (SwirlData) b.data;
            if (!Vars.headless) {
                Color hue = starHue(sd.phase + b.time * 0.12f);
                if (b.timer(2, 2.2f)) starDustFx.at(b.x + Mathf.range(2.5f), b.y + Mathf.range(2.5f), 0f, hue.cpy());
                if (!sd.pulsed && starScale(b, sd) > 1.62f) { sd.pulsed = true; starPulseFx.at(b.x, b.y, 0f, hue.cpy()); }
            }
            // Trượt mục tiêu (lướt qua hoặc mất mục tiêu): bay thêm đúng 0,5s (30 tick) rồi va chạm
            if (sd.startDst < 0f && isTargetAlive(sd.target)) sd.startDst = b.dst(sd.target);
            if (!sd.expiring) {
                if (isTargetAlive(sd.target)) {
                    float dd = b.dst(sd.target);
                    sd.minDst = Math.min(sd.minDst, dd);
                    if (sd.minDst < 90f && dd > sd.minDst + 8f) { sd.passed = true; sd.expiring = true; }
                } else if (b.time > homingDelay + 12f) {
                    sd.expiring = true;
                }
                if (sd.expiring) { b.lifetime = Math.min(b.lifetime, b.time + 30f); sd.expireT = b.time; sd.scExp = starScale(b, sd); }
            }
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
            float tilt = Mathf.atan2(lat, Math.max(0.5f, b.vel.len())) * Mathf.radDeg;
            float sc = sd.expiring ? Mathf.lerp(sd.scExp, 0.3f, Mathf.clamp((t - sd.expireT) / 30f)) : starScale(b, sd);
            float heading = r0 + tilt;
            float flick = 0.85f + Mathf.absin(Time.time + b.id, 2.3f, 0.3f);
            drawTrail(b);
            Color hue = starHue(sd.phase + t * 0.12f);
            float breathe = 1f + 0.1f * Mathf.sin(t * 0.55f + sd.phase);
            sc *= breathe * 0.25f; // Giảm 75% kích thước đạn
            Draw.blend(Blending.additive);
            Draw.color(hue, 0.28f);
            Fill.circle(b.x, b.y, 7f * sc);
            Draw.color(hue, 0.5f);
            drawStar(b.x, b.y, 0f, 19f * sc, 4.4f * sc, 6.5f * sc);
            Draw.blend();
            Draw.color(MISS_INDIGO);
            drawStar(b.x, b.y, 0f, 12f * sc, 3.0f * sc, 4.2f * sc);
            Draw.color(MISS_STEEL);
            drawStar(b.x, b.y, 0f, 9f * sc, 1.8f * sc, 2.8f * sc);
            Draw.color(Color.white);
            Fill.circle(b.x, b.y, 1.6f * sc);
            Draw.reset();
            Drawf.light(b.x, b.y, 28f * sc, hue, 0.5f);
        }

        @Override
        public void hit(Bullet b, float x, float y) {
            // Tính sát thương nổ theo chỉ số Tấn công hiện tại của unit chủ
            float dynSplash = splashDamage;
            if (b.owner instanceof Unit) {
                dynSplash = calcAtk((Unit) b.owner) * ROCKET_SPLASH_RATIO;
            }
            hitEffect.at(x, y, b.rotation(), hitColor);
            Effect.shake(1.8f, 8f, x, y);
            int note = (b.data instanceof SwirlData) ? ((SwirlData) b.data).idx : 0;
            hitSound.at(x, y, 0.95f + note * 0.17f, hitSoundVolume * 0.6f); // 4 quả nổ nối nhau như 4 nốt nhạc đi lên
            if (!Vars.headless) {
                for (int k = 0; k < 9; k++) starDustFx.at(x + Mathf.range(2.5f), y + Mathf.range(2.5f), 0f, starHue(k * 0.7f).cpy());
                starPulseFx.at(x, y, 0f, MISS_CYAN.cpy());
            }

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
        public void draw(Unit unit) { /* khiên được vẽ trong BulvasyrUnit.draw() để luôn hiện đúng lớp */ }

        public static void render(Unit unit) {
            if (!(unit instanceof BulvasyrUnit)) return;
            BulvasyrUnit bu = (BulvasyrUnit) unit;
            bu.ensureShieldInit();
            if (!bu.isShieldActive()) return;

            float ratio = bu.shieldRatio();
            float shieldFacing = bu.rotation + SHIELD_ANGLE_OFFSET;
            float px = bu.x + Angles.trnsx(shieldFacing, bu.hitSize * 0.48f);
            float py = bu.y + Angles.trnsy(shieldFacing, bu.hitSize * 0.48f);

            float zPrev = Draw.z();
            Draw.z(Layer.groundUnit + 0.03f);

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

            // Thanh độ bền khiên: chỉ phe đồng minh với người chơi mới nhìn thấy (không còn vẽ cung cảnh báo)
            if (Vars.player != null && bu.team == Vars.player.team()) {
                Draw.z(Layer.overlayUI);
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

        private static void drawVectorShieldPlate(BulvasyrUnit bu, float px, float py, float ratio) {
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
            t.add("[lightgray]Chặn đòn: [white]Chặn mọi đạn, mảnh đạn, laser & tia laser nhắm thẳng (không bắn xuyên); chỉ vụ nổ mới gây sát thương cho cả khiên lẫn unit").row();
            t.add("[lightgray]Kháng/Yếu: [scarlet]+50%[] sát thương từ đạn vật lý | [green]-50%[] sát thương từ đạn năng lượng & laser");
        }
    }
}