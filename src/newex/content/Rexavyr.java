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
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.EntityMapping;
import mindustry.gen.Groups;
import mindustry.gen.MechUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;
import newex.NexNet;

/**
 * Rexavyr - Khủng long bạo chúa cơ giáp khổng lồ (phong cách Bulvasyr, kích thước x3):
 * - Kết cấu khớp cầu liền mạch 100%: thân, 5 đốt cổ, sọ + 2 hàm nghiền cơ học tách rời, 2 tay vuốt trước, 2 chân thủy lực, 10 đốt đuôi kiếm.
 * - Animation điện ảnh: cổ/đầu xoay mượt theo hướng nhắm, hàm mở rộng khi phun lửa / gầm, há to rồi ngoạm sập dữ dội khi Lao + Cắn,
 *   nhịp dậm chân nặng rung chấn, đuôi lấy đà rồi quất vòng cung 105°.
 */
public final class Rexavyr {
    private Rexavyr() {}

    public static UnitType rexavyr;

    // ---- Chỉ số & tầm đánh (tương xứng kích thước khổng lồ x3) ----
    public static final float BASE_ATK = 300f;
    public static final float FLAME_RANGE = 255f;
    public static final float FLAME_RATIO = 0.10f;   // % ATK mỗi viên lửa xuyên
    public static final float BITE_RATIO = 6.0f;     // % ATK cú cắn chí mạng
    public static final float ROAR_RATIO = 1.5f;
    public static final float TAIL_RATIO = 2.2f;
    public static final float DASH_DIST = 340f, DASH_DUR = 26f, DASH_CD = 480f;   // 8s
    public static final float ROAR_RADIUS = 280f, ROAR_DUR = 54f, ROAR_CD = 1200f; // 20s
    public static final float TAIL_CD = 140f, TAIL_DUR = 34f, TAIL_SWING = 105f;

    // ---- Hình học khớp chuẩn 100% với bộ sprite tách từ bản vẽ Rexavyr (kích thước x3: ngang ~86u, dài ~310u) ----
    static final float SC = 0.38f;
    static final float NECK_ANCHOR = 80f;   // cổ gắn khít vào vai trước (px)
    static final float TAIL_ANCHOR = -88f;  // đuôi gắn khít dưới eo sau (px)
    static final float LEG_LAT = 60f;       // khoảng cách ngang 2 chân sau (px)
    static final float LEG_FWD = -58f;      // lùi chân về hông sau để không đè vào 2 tay vuốt trên thân (px)
    public static final float HEAD_TIP = 134f; // khoảng cách từ tâm thân tới chóp mõm lúc nghỉ (đơn vị thế giới)

    static final Color OUTLINE = Color.valueOf("161924");
    static final Color FL_WHITE = Color.valueOf("fff8e2"), FL_YEL = Color.valueOf("ffd44e"),
        FL_ORG = Color.valueOf("ff8a1f"), FL_RED = Color.valueOf("e62c3a"),
        STEEL = Color.valueOf("d0d8e8"), TITAN_VIOLET = Color.valueOf("66729a"),
        BLOOD = Color.valueOf("e62c3a"), SMOKE = Color.valueOf("32374a");

    // ---- Sprite ----
    static TextureRegion rBody, rHead, rJawL, rJawR, rArmL, rArmR, rLegL, rLegR;
    static final TextureRegion[] rNeck = new TextureRegion[5], rTail = new TextureRegion[10];
    static boolean loaded = false;

    static void ensureRegions() {
        if (loaded) return;
        rBody = Core.atlas.find("newex-rexavyr-body");
        rHead = Core.atlas.find("newex-rexavyr-head");
        rJawL = Core.atlas.find("newex-rexavyr-jaw-left");
        rJawR = Core.atlas.find("newex-rexavyr-jaw-right");
        rArmL = Core.atlas.find("newex-rexavyr-arm-left");
        rArmR = Core.atlas.find("newex-rexavyr-arm-right");
        rLegL = Core.atlas.find("newex-rexavyr-leg-left");
        rLegR = Core.atlas.find("newex-rexavyr-leg-right");
        for (int i = 0; i < 5; i++) rNeck[i] = Core.atlas.find("newex-rexavyr-neck-" + (i + 1));
        for (int i = 0; i < 10; i++) rTail[i] = Core.atlas.find("newex-rexavyr-tail-" + (i + 1));
        loaded = true;
    }

    // ---- Hiệu ứng ----
    public static Effect emberFx, flameHitFx, dashStartFx, dashTrailFx, biteFx, roarFx, tailFx, stompFx;

    public static float calcAtk(Unit u) {
        return (BASE_ATK + NewModePvp.skillFlatAtk(u)) * Math.max(0.1f, u.damageMultiplier);
    }

    static float angDiff(float from, float to) {
        float d = (to - from) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    static float lerpAng(float a, float b, float t) {
        return a + angDiff(a, b) * Mathf.clamp(t);
    }

    // ================================================================== tạo nội dung
    public static void create() {
        int ensure = RexavyrUnit.CLASS_ID;
        if (!Vars.headless) buildEffects();

        rexavyr = new UnitType("rexavyr") {{
            constructor = RexavyrUnit::new;
            localizedName = "Rexavyr";
            description = "Khủng long bạo chúa cơ giáp khổng lồ: lao tới cắn xé, gầm làm choáng cả chiến trường, vẫy đuôi kiếm hất văng kẻ địch và phun bão lửa thiêu rụi mọi thứ.";
            details = "[gold]Kẻ săn mồi cận chiến khổng lồ[]. Phun bão lửa xuyên mục tiêu (đốt cháy); tự vẫy đuôi quét khi có địch phía sau. "
                + "Kỹ năng 1 [accent]Lao tới + Cắn[]: lao thẳng rồi ngoạm sập hàm nghiền gây sát thương lớn, hút máu. "
                + "Kỹ năng 2 [accent]Gầm[]: sóng âm chấn động choáng, làm yếu địch quanh đó, xóa đạn địch và tăng sức mạnh bản thân.";

            health = 56000f;
            armor = 26f;
            hitSize = 72f;
            speed = 0.92f;
            rotateSpeed = 2.3f;
            range = FLAME_RANGE;
            maxRange = FLAME_RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;
            envDisabled = Env.none;
            drawCell = false;
            hovering = false;
            canBoost = false;
            mechStride = 26f;
            mechStepParticles = false;
            outlineColor = OUTLINE;

            weapons.add(new Weapon("newex-rexavyr-maw") {{
                x = 0f;
                y = 0f;
                shootY = HEAD_TIP;
                mirror = false;
                rotate = false;
                top = false;
                reload = 4f;
                inaccuracy = 7.5f;
                velocityRnd = 0.22f;
                shootCone = 34f;
                recoil = 0f;
                ejectEffect = Fx.none;
                shootSound = Sounds.shootFlame;
                bullet = new FlameBullet();
            }});
        }};
    }

    // ================================================================== đạn bão lửa từ hàm Rexavyr
    public static class FlameBullet extends BulletType {
        public FlameBullet() {
            super(7.6f, 30f);
            lifetime = 21f; // vươn xa ~160u tính từ chóp mõm (136 + 160 > 255u tầm đánh)
            hitSize = 24f;
            pierce = true;
            pierceBuilding = true;
            pierceCap = -1;
            collidesAir = true;
            collidesGround = true;
            hittable = false;
            absorbable = false;
            reflectable = false;
            keepVelocity = false;
            ammoMultiplier = 1f;
            status = StatusEffects.burning;
            statusDuration = 180f;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            despawnEffect = Fx.none;
            hitEffect = flameHitFx != null ? flameHitFx : Fx.none;
        }

        @Override
        public void init(Bullet b) {
            super.init(b);
            if (b.owner instanceof RexavyrUnit) {
                RexavyrUnit ru = (RexavyrUnit) b.owner;
                b.damage = calcAtk(ru) * FLAME_RATIO;
                // Khớp chính xác luồng lửa phun ra từ hàm đang lắc/xoay thực tế của Rexavyr
                if (ru.mawValid) {
                    float spread = angDiff(ru.rotation, b.rotation());
                    b.set(ru.mawX, ru.mawY);
                    b.vel.setAngle(ru.mawAng + spread);
                }
            } else if (b.owner instanceof Unit) {
                b.damage = calcAtk((Unit) b.owner) * FLAME_RATIO;
            }
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (!Vars.headless && Mathf.chanceDelta(0.45f)) {
                emberFx.at(b.x + Mathf.range(6f), b.y + Mathf.range(6f), b.rotation());
            }
        }

        @Override
        public void draw(Bullet b) {
            float f = b.fin();
            float fout = 1f - f;
            float r = 6.5f + Interp.pow2Out.apply(f) * 24f;
            float rot = b.rotation();
            Draw.blend(Blending.additive);
            // Luồng lửa nhọn kiểu nón nhiệt động lực thay vì chỉ cục tròn đơn điệu
            Draw.color(FL_RED, 0.42f * fout);
            Drawf.tri(b.x, b.y, r * 1.45f, r * 2.1f, rot);
            Drawf.tri(b.x, b.y, r * 1.45f, r * 0.9f, rot + 180f);
            Draw.color(FL_ORG, FL_RED, f);
            Draw.alpha(0.78f * (1f - f * 0.55f));
            Fill.circle(b.x, b.y, r * 0.85f);
            Draw.color(FL_WHITE, FL_YEL, f);
            Draw.alpha(0.92f * fout);
            Drawf.tri(b.x, b.y, r * 0.65f, r * 1.3f, rot);
            Fill.circle(b.x, b.y, r * 0.42f * fout);
            Draw.blend();
            Draw.reset();
            Drawf.light(b.x, b.y, r * 3.8f, FL_ORG, 0.58f * fout);
        }
    }

    // ================================================================== hiệu ứng điện ảnh (KHÔNG kẻ vạch thẳng quanh vòng tròn!)
    private static void buildEffects() {
        emberFx = new Effect(38f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(FL_YEL, FL_RED, e.fin());
            Draw.alpha(e.fout());
            Angles.randLenVectors(e.id, 2, 5f + 34f * e.finpow(), (x, y) ->
                Fill.square(e.x + x, e.y + y + e.fin() * 8f, 1.4f + 2.6f * e.fout(), 45f));
            Draw.blend();
            Draw.reset();
        });

        flameHitFx = new Effect(22f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(FL_WHITE, FL_ORG, e.fin());
            Angles.randLenVectors(e.id, 5, 6f + 24f * e.finpow(), (x, y) -> {
                float ang = Mathf.angle(x, y);
                Drawf.tri(e.x + x, e.y + y, 2.2f * e.fout(), 7f * e.fout(), ang);
            });
            Draw.blend();
            Draw.reset();
        });

        dashStartFx = new Effect(40f, 340f, e -> {
            float a = e.rotation;
            float fout = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            for (int i = 0; i < 2; i++) {
                float f = Mathf.clamp(e.fin() * 1.4f - i * 0.2f);
                Draw.color(STEEL, FL_ORG, f);
                Lines.stroke(6f * (1f - f));
                Lines.circle(e.x, e.y, 18f + 135f * Interp.pow3Out.apply(f));
            }
            // Chớp phản lực văng ngược phía sau
            Draw.color(FL_ORG, FL_WHITE, fout);
            Angles.randLenVectors(e.id, 14, 24f + 165f * e.finpow(), a + 180f, 36f, (x, y) -> {
                float ang = Mathf.angle(x, y);
                Drawf.tri(e.x + x, e.y + y, 3.8f * fout, 18f * fout, ang);
            });
            Draw.blend();
            Draw.color(SMOKE, 0.55f * e.fout());
            Angles.randLenVectors(e.id + 3, 10, 12f + 78f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 8f * e.fout() + 2f));
            Draw.reset();
        }).layer(Layer.groundUnit - 0.8f);

        dashTrailFx = new Effect(28f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(FL_ORG, FL_RED, e.fin());
            Drawf.tri(e.x, e.y, 7.5f * fout, 38f * fout, e.rotation + 180f);
            Draw.blend();
            Draw.color(SMOKE, 0.45f * fout);
            Fill.circle(e.x + Mathf.range(9f), e.y + Mathf.range(9f), 5f + 9f * e.fin());
            Draw.reset();
        }).layer(Layer.groundUnit - 0.8f);

        biteFx = new Effect(48f, 380f, e -> {
            float r = e.rotation;
            float fout = e.fout();
            // Hai gọng hàm năng lượng khổng lồ sập mạnh vào tâm cú ngoạm!
            float snap = Interp.pow4Out.apply(Math.min(1f, e.fin() * 3.6f));
            float off = 46f * (1f - snap) + 4f;
            Draw.blend(Blending.additive);
            Draw.color(FL_WHITE, STEEL, e.fin());
            Draw.alpha(Mathf.clamp(fout * 2.2f));
            for (int s = -1; s <= 1; s += 2) {
                for (int k = -2; k <= 2; k++) {
                    float bx = e.x + Angles.trnsx(r, k * 14f, s * off);
                    float by = e.y + Angles.trnsy(r, k * 14f, s * off);
                    float tx = e.x + Angles.trnsx(r, k * 14f, s * (off - 24f));
                    float ty = e.y + Angles.trnsy(r, k * 14f, s * (off - 24f));
                    Fill.tri(
                        bx + Angles.trnsx(r, 6.5f, 0f), by + Angles.trnsy(r, 6.5f, 0f),
                        bx - Angles.trnsx(r, 6.5f, 0f), by - Angles.trnsy(r, 6.5f, 0f),
                        tx, ty
                    );
                }
            }
            // 3 vết xé huyết quang chéo ngang mục tiêu
            for (int i = 0; i < 3; i++) {
                float f = Mathf.clamp(e.fin() * 2.1f - i * 0.10f);
                float sf = 1f - f;
                float cx = e.x + Angles.trnsx(r, 0f, (i - 1) * 20f);
                float cy = e.y + Angles.trnsy(r, 0f, (i - 1) * 20f);
                float len = 95f * Interp.pow3Out.apply(f);
                Draw.color(FL_WHITE, BLOOD, f);
                Drawf.tri(cx, cy, 8f * sf, len, r + 58f);
                Drawf.tri(cx, cy, 8f * sf, len, r + 58f + 180f);
            }
            Draw.color(FL_ORG, BLOOD, e.fin());
            Lines.stroke(4.2f * fout);
            Lines.circle(e.x, e.y, 14f + 86f * e.finpow());
            Angles.randLenVectors(e.id, 14, 16f + 110f * e.finpow(), (x, y) -> {
                float ang = Mathf.angle(x, y);
                Drawf.tri(e.x + x, e.y + y, 3.0f * fout, 14f * fout, ang);
            });
            Draw.blend();
            Drawf.light(e.x, e.y, 140f * fout, BLOOD, 0.8f * fout);
            Draw.reset();
        });

        // Sóng gầm chấn động (tuyệt đối KHÔNG vẽ vạch thẳng quanh vòng tròn!)
        roarFx = new Effect(72f, ROAR_RADIUS * 3f, e -> {
            Draw.blend(Blending.additive);
            for (int i = 0; i < 3; i++) {
                float f = Mathf.clamp(e.fin() * 1.45f - i * 0.20f);
                if (f <= 0f || f >= 1f) continue;
                float rad = ROAR_RADIUS * Interp.pow3Out.apply(f);
                float sf = 1f - f;
                Draw.color(FL_ORG, BLOOD, f);
                Lines.stroke(8.5f * sf);
                Lines.circle(e.x, e.y, rad);
                Draw.color(FL_WHITE, FL_YEL, f);
                Lines.stroke(3.0f * sf);
                Lines.circle(e.x, e.y, rad * 0.96f);
            }
            Draw.color(FL_ORG, 0.34f * e.fout());
            Fill.circle(e.x, e.y, 92f * e.fout() + 16f);
            // Mảnh sóng xung kích tam giác bay tỏa tự nhiên
            Angles.randLenVectors(e.id + 5, 18, 25f + ROAR_RADIUS * 0.95f * e.finpow(), (x, y) -> {
                float ang = Mathf.angle(x, y);
                Draw.color(FL_YEL, BLOOD, e.fin());
                Drawf.tri(e.x + x, e.y + y, 4.2f * e.fout(), 16f * e.fout(), ang);
            });
            Draw.blend();
            Drawf.light(e.x, e.y, ROAR_RADIUS * 1.35f * e.fout(), FL_ORG, 0.78f * e.fout());
            Draw.reset();
        }).layer(Layer.groundUnit - 0.6f);

        tailFx = new Effect(38f, 320f, e -> {
            float r = e.rotation;
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(FL_WHITE, STEEL, e.fin());
            Lines.stroke(5.5f * fout);
            Lines.circle(e.x, e.y, 12f + 68f * e.finpow());
            Draw.color(FL_YEL, FL_ORG, e.fin());
            Angles.randLenVectors(e.id, 12, 14f + 98f * e.finpow(), r, 60f, (x, y) -> {
                float ang = Mathf.angle(x, y);
                Drawf.tri(e.x + x, e.y + y, 3.2f * fout, 16f * fout, ang);
            });
            Draw.blend();
            Draw.color(SMOKE, 0.5f * fout);
            Angles.randLenVectors(e.id + 1, 7, 8f + 52f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 7f * fout));
            Draw.reset();
        });

        stompFx = new Effect(30f, e -> {
            float fout = e.fout();
            Draw.color(STEEL, fout * 0.75f);
            Lines.stroke(2.8f * fout);
            Lines.circle(e.x, e.y, 6f + 36f * e.finpow());
            Draw.color(SMOKE, 0.58f * fout);
            Angles.randLenVectors(e.id, 6, 6f + 32f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 5.5f * fout));
            Draw.blend(Blending.additive);
            Draw.color(FL_YEL, FL_ORG, e.fin());
            Angles.randLenVectors(e.id + 2, 5, 8f + 36f * e.finpow(), (x, y) ->
                Fill.square(e.x + x, e.y + y, 2.0f * fout, 45f));
            Draw.blend();
            Draw.reset();
        }).layer(Layer.groundUnit - 0.9f);
    }

    // ================================================================== Entity Khủng Long Cơ Giáp
    public static class RexavyrUnit extends MechUnit {
        public static final int CLASS_ID = EntityMapping.register("newex-rexavyr-unit", RexavyrUnit::new);

        @Override
        public int classId() {
            return CLASS_ID;
        }

        // Thời điểm bắt đầu các animation (đồng bộ qua RexavyrSkill FX)
        public float dashT = -9999f, roarT = -9999f, tailT = -9999f, tailSide = 1f;
        // Hồi chiêu phía máy chủ
        public float cdDash = 0f, cdRoar = 0f, tailCd = 0f, aiNext = 0f;
        private boolean tailPending = false;

        // Tọa độ & hướng mõm thực tế để đạn lửa phun khớp 100% với đầu đang quay
        public float mawX = 0f, mawY = 0f, mawAng = 0f;
        public boolean mawValid = false;

        private final float[] tA = new float[10], nA = new float[5];
        private final float[] tcx = new float[10], tcy = new float[10];
        private boolean chainInit = false;
        private float legAmp = 0f;
        private float stompImpact = 0f;
        private float smoothHeadTurn = 0f;
        private int lastStep = -1, trailTick = 0;

        @Override
        public void update() {
            super.update();
            if (dead) return;
            if (stompImpact > 0f) stompImpact = Math.max(0f, stompImpact - Time.delta * 0.14f);
            if (!Vars.headless) {
                stepFx();
                if (Time.time - dashT >= 0f && Time.time - dashT <= DASH_DUR + 2f && (trailTick++ & 1) == 0) {
                    dashTrailFx.at(x, y, rotation);
                }
            }
            if (Vars.net.client()) return;
            if (cdDash > 0f) cdDash -= Time.delta;
            if (cdRoar > 0f) cdRoar -= Time.delta;
            if (tailCd > 0f) tailCd -= Time.delta;
            tickTail();
            if (!isPlayer()) aiSkills();
        }

        private void stepFx() {
            if (isFlying() || vel.len() < 0.12f) return;
            int step = (int) Math.floor(walkExtend(true) / 2f);
            if (lastStep == -1) { lastStep = step; return; }
            if (step == lastStep) return;
            lastStep = step;
            stompImpact = 1f;
            float side = (step & 1) == 0 ? 1f : -1f;
            float fx = x + Angles.trnsx(rotation, 4f, side * LEG_LAT * SC);
            float fy = y + Angles.trnsy(rotation, 4f, side * LEG_LAT * SC);
            stompFx.at(fx, fy, rotation);
            Effect.shake(2.8f, 11f, fx, fy);
            Sounds.mechStepHeavy.at(fx, fy, Mathf.random(0.78f, 0.92f), 0.65f);
        }

        private void tickTail() {
            if (tailPending && Time.time - tailT >= 11f) {
                tailPending = false;
                tailStrike();
            }
            if (tailCd > 0f || tailPending || Time.time - tailT < TAIL_DUR + 10f) return;
            final float back = rotation + 180f;
            final float bx = x + Angles.trnsx(back, 105f), by = y + Angles.trnsy(back, 105f);
            final float rr = 145f;
            final boolean[] found = {false};
            Groups.unit.intersect(bx - rr, by - rr, rr * 2f, rr * 2f, e -> {
                if (found[0] || e.team == team || e.dead || !e.within(bx, by, rr)) return;
                float rel = angDiff(back, angleTo(e));
                if (Math.abs(rel) > 90f) return;
                found[0] = true;
                tailSide = rel >= 0f ? 1f : -1f;
            });
            if (!found[0]) return;
            tailCd = TAIL_CD;
            tailT = Time.time;
            tailPending = true;
            NexNet.broadcast(RexavyrSkill.FX, id + ",2," + tailSide + ",0,0");
        }

        private void tailStrike() {
            final float dmg = calcAtk(this) * TAIL_RATIO;
            final float ang = rotation + 180f + tailSide * 56f;
            for (int k = 0; k < 2; k++) {
                final float px = x + Angles.trnsx(ang, 105f + k * 76f);
                final float py = y + Angles.trnsy(ang, 105f + k * 76f);
                final float hitR = 72f;
                Damage.damage(team, px, py, hitR, dmg * (k == 0 ? 0.6f : 0.4f), false, true, true);
                Groups.unit.intersect(px - hitR, py - hitR, hitR * 2f, hitR * 2f, e -> {
                    if (e.team == team || e.dead || !e.within(px, py, hitR + e.hitSize / 2f)) return;
                    e.vel.add(Tmp.v1.set(e.x - x, e.y - y).nor().scl(Mathf.clamp(65f / e.hitSize, 0.35f, 2.2f) * 6.5f));
                    e.apply(StatusEffects.slow, 80f);
                });
                NexNet.broadcast(RexavyrSkill.FX, id + ",4," + px + "," + py + "," + ang);
            }
        }

        private void aiSkills() {
            if (Time.time < aiNext) return;
            aiNext = Time.time + 20f;
            Unit t = Units.closestEnemy(team, x, y, 360f, e -> !e.dead);
            if (t == null) return;
            if (cdRoar <= 0f) {
                final int[] n = {0};
                Groups.unit.intersect(x - 220f, y - 220f, 440f, 440f, e -> {
                    if (e.team != team && !e.dead && within(e, 220f)) n[0]++;
                });
                if (n[0] >= 3) { RexavyrSkill.startRoar(this); return; }
            }
            float d = dst(t);
            if (cdDash <= 0f && d > 120f && d < 350f) RexavyrSkill.startDash(this, angleTo(t));
        }

        // ---------------------------------------------------------------- Vẽ cơ giáp T-Rex liền mạch & điện ảnh
        @Override
        public void draw() {
            ensureRegions();
            if (rBody == null || !rBody.found()) return;
            final float t = Time.time, dl = Math.min(2.5f, Time.delta);
            final float z = Layer.groundUnit + Mathf.clamp(hitSize / 4000f, 0f, 0.015f);

            float spd = Mathf.clamp(vel.len() / Math.max(0.01f, type.speed), 0f, 1.35f);
            legAmp = Mathf.lerp(legAmp, spd, Mathf.clamp(0.16f * dl));
            float ph = walkExtend(true) * Mathf.PI / 2f;

            // Các pha kỹ năng điện ảnh: Lao (dashP), Cắn (lungeBite), Gầm (roarEnv), Quất đuôi (tailWhip)
            float tb = t - dashT, tr = t - roarT, tt = t - tailT;
            boolean inDash = tb >= 0f && tb <= DASH_DUR + 18f;
            float lungeOut = inDash
                ? Interp.pow2Out.apply(Mathf.curve(tb, DASH_DUR * 0.25f, DASH_DUR * 0.82f))
                  * (1f - Interp.pow2In.apply(Mathf.curve(tb, DASH_DUR * 0.85f, DASH_DUR + 18f)))
                : 0f;
            // Pha há miệng rộng trước khi ngoạm sập hàm ở mốc 0.85 * DASH_DUR!
            float biteOpen = inDash
                ? Interp.pow2Out.apply(Mathf.curve(tb, 0f, DASH_DUR * 0.72f))
                  * (1f - Interp.pow4In.apply(Mathf.curve(tb, DASH_DUR * 0.74f, DASH_DUR * 0.88f)))
                : 0f;
            float biteSnap = inDash
                ? (1f - Mathf.curve(tb, DASH_DUR * 0.86f, DASH_DUR + 10f)) * Mathf.curve(tb, DASH_DUR * 0.80f, DASH_DUR * 0.86f)
                : 0f;

            float R = (tr >= 0f && tr <= ROAR_DUR)
                ? Interp.pow2Out.apply(Mathf.curve(tr, 0f, 10f)) * (1f - Interp.pow2In.apply(Mathf.curve(tr, ROAR_DUR - 16f, ROAR_DUR)))
                : 0f;

            // Quất đuôi: lấy đà ngược (-26°) trong 30% đầu rồi quất vọt (+105°) cực mạnh!
            float sw = 0f;
            if (tt >= 0f && tt <= TAIL_DUR) {
                float pTail = tt / TAIL_DUR;
                if (pTail < 0.28f) {
                    sw = -tailSide * 26f * Interp.pow2Out.apply(pTail / 0.28f);
                } else {
                    float wP = (pTail - 0.28f) / 0.72f;
                    sw = tailSide * (-26f + (TAIL_SWING + 26f) * Mathf.sin(wP * Mathf.PI * 0.88f));
                }
            }

            // ---- 1) TỌA ĐỘ & GÓC THÂN CHÍNH (MỌI BỘ PHẬN GẮN CHẶT VÀO KHUNG NÀY -> KHÔNG BAO GIỜ LỆCH/RỜI KHỚP!)
            float hipSwayLat = Mathf.sin(ph) * 2.8f * legAmp;
            float strideFwd = (-Math.abs(Mathf.cos(ph)) * 1.8f * legAmp - stompImpact * 2.6f + lungeOut * 9.5f - R * 5.0f);
            float bodySwayRot = Mathf.sin(ph) * 2.4f * legAmp + R * Mathf.sin(tr * 1.4f) * 1.8f;
            float brot = rotation + bodySwayRot;
            float bx = x + Angles.trnsx(rotation, strideFwd, hipSwayLat);
            float by = y + Angles.trnsy(rotation, strideFwd, hipSwayLat);
            float breathScale = 1f + 0.014f * Mathf.sin(t * 0.055f) + 0.045f * R - 0.018f * stompImpact;

            if (!chainInit) {
                for (int i = 0; i < 10; i++) tA[i] = brot + 180f;
                for (int i = 0; i < 5; i++) nA[i] = brot;
                chainInit = true;
            }

            // Hướng cổ nhìn mượt về mục tiêu đang nhắm (giới hạn tự nhiên ±34°)
            float aimDiff = 0f;
            if (isShooting() || mounts.length > 0) {
                float wantAng = Angles.angle(bx, by, aimX, aimY);
                aimDiff = Mathf.clamp(angDiff(brot, wantAng), -34f, 34f);
            }
            smoothHeadTurn = Mathf.lerp(smoothHeadTurn, aimDiff, Mathf.clamp(0.18f * dl));

            if (hitTime > 0f) Draw.mixcol(Color.white, Mathf.clamp(hitTime));

            // ---- 2) BÓNG ĐỔ KHỔNG LỒ DƯỚI MẶT ĐẤT
            Draw.z(Layer.groundUnit - 1f);
            Drawf.shadow(bx + 4f, by - 5f, hitSize * 1.55f);

            // ---- 3) ĐUÔI CƠ GIÁP 10 ĐỐT (khớp cầu chồng 50% -> liền mạch tuyệt đối kể cả khi quất 105°!)
            float tailWaveAmp = 1.8f + 4.8f * legAmp + 2.5f * R;
            float tailWaveSpd = 0.075f + 0.055f * legAmp;
            float cx = bx + Angles.trnsx(brot, TAIL_ANCHOR * SC * breathScale);
            float cy = by + Angles.trnsy(brot, TAIL_ANCHOR * SC * breathScale);
            for (int i = 0; i < 10; i++) {
                float wave = Mathf.sin(t * tailWaveSpd - i * 0.58f) * tailWaveAmp * (0.40f + i * 0.085f);
                float whipSeg = sw * (0.22f + i * 0.045f);
                float tgt = (i == 0) ? (brot + 180f + sw * 0.42f + wave * 0.5f) : (tA[i - 1] + wave + whipSeg * 0.25f);
                float followRate = (tt >= 0f && tt <= TAIL_DUR) ? 0.65f : (0.34f - i * 0.016f);
                tA[i] = lerpAng(tA[i], tgt, followRate * dl);

                TextureRegion r = rTail[i];
                float step = r.height * SC * 0.46f;
                float dx = Mathf.cosDeg(tA[i]), dy = Mathf.sinDeg(tA[i]);
                tcx[i] = cx + dx * step * 0.5f;
                tcy[i] = cy + dy * step * 0.5f;
                cx += dx * step;
                cy += dy * step;
            }
            Drawf.shadow(tcx[3], tcy[3], hitSize * 0.95f);
            Drawf.shadow(tcx[7], tcy[7], hitSize * 0.65f);
            Draw.z(z - 0.005f);
            for (int i = 9; i >= 0; i--) {
                TextureRegion r = rTail[i];
                if (r != null && r.found()) {
                    Draw.rect(r, tcx[i], tcy[i], r.width * SC, r.height * SC, tA[i] + 90f);
                }
            }
            // Vệt chém nhiệt đỏ-vàng dọc lưỡi đuôi khi đang tung chiêu Quất Đuôi!
            if (tt >= 6f && tt <= TAIL_DUR * 0.85f) {
                Draw.blend(Blending.additive);
                Draw.color(FL_ORG, FL_YEL, 0.5f);
                Draw.alpha(0.65f * Mathf.sin((tt / TAIL_DUR) * Mathf.PI));
                Drawf.tri(tcx[9], tcy[9], 14f, 52f, tA[9] + tailSide * 75f);
                Draw.blend();
                Draw.color();
                if (hitTime > 0f) Draw.mixcol(Color.white, Mathf.clamp(hitTime));
            }

            // ---- 4) HAI CHÂN SAU HẠNG NẶNG (gắn đúng hông dưới eo sau, bước dậm bất đối xứng)
            Draw.z(z - 0.003f);
            for (int s = 1; s >= -1; s -= 2) {
                TextureRegion legR = s == 1 ? rLegL : rLegR;
                if (legR == null || !legR.found()) continue;
                float sp = s == 1 ? ph : ph + Mathf.PI;
                float swing = Mathf.sin(sp);
                float liftPhase = Math.max(0f, Mathf.cos(sp));
                float fwdPx = LEG_FWD + swing * 28f * legAmp - lungeOut * 10f;
                float latPx = s * (LEG_LAT + liftPhase * 5f * legAmp);
                float liftScl = 1f + 0.11f * liftPhase * legAmp + 0.05f * lungeOut;
                float legTurn = swing * 6.0f * legAmp * s;
                float lx = bx + Angles.trnsx(brot, fwdPx * SC, latPx * SC);
                float ly = by + Angles.trnsy(brot, fwdPx * SC, latPx * SC);
                Draw.rect(legR, lx, ly, legR.width * SC * liftScl, legR.height * SC * liftScl, brot - 90f + legTurn);
            }

            // ---- 5) GIÁP THÂN XƯƠNG SƯỜN & 2 TAY VUỐT (đè khít lên khớp eo đuôi & hông chân)
            Draw.z(z);
            Draw.rect(rBody, bx, by, rBody.width * SC * breathScale, rBody.height * SC * breathScale, brot - 90f);

            // ---- 6) CỔ 5 ĐỐT GIÁP VẢY (gắn liền mạch vào vai thân, vươn mượt theo hướng nhắm & cú đớp)
            float stretch = 1f + 0.20f * lungeOut - 0.06f * R;
            cx = bx + Angles.trnsx(brot, NECK_ANCHOR * SC * breathScale);
            cy = by + Angles.trnsy(brot, NECK_ANCHOR * SC * breathScale);
            Draw.z(z + 0.0015f);
            float perSegAim = smoothHeadTurn * 0.20f;
            for (int i = 0; i < 5; i++) {
                float gaitSway = Mathf.sin(t * 0.065f + i * 0.55f + ph) * (0.9f + 2.2f * legAmp);
                float roarShake = R * Mathf.sin(tr * 1.35f + i * 0.4f) * 2.2f;
                float tgt = (i == 0 ? brot : nA[i - 1]) + perSegAim + gaitSway * 0.45f + roarShake;
                nA[i] = lerpAng(nA[i], tgt, 0.42f * dl);

                TextureRegion r = rNeck[i];
                float step = r.height * SC * 0.36f * stretch;
                float dx = Mathf.cosDeg(nA[i]), dy = Mathf.sinDeg(nA[i]);
                if (r != null && r.found()) {
                    Draw.rect(r, cx + dx * step * 0.5f, cy + dy * step * 0.5f,
                        r.width * SC, r.height * SC * (1f + 0.14f * lungeOut), nA[i] - 90f);
                }
                cx += dx * step;
                cy += dy * step;
            }

            // ---- 7) ĐẦU SỌ SỪNG & HÀM RĂNG DỮ TỢN (ôm khít lên đốt cổ thứ 5, 2 sừng cong ôm dọc gáy)
            boolean shooting = isShooting();
            float headAng = nA[4] + smoothHeadTurn * 0.15f + R * Mathf.sin(tr * 1.6f) * 4.5f;
            float hs = 1f + 0.14f * lungeOut + 0.18f * R * (0.75f + 0.25f * Mathf.sin(tr * 0.9f));
            float headWidthScl = hs * (1f + 0.08f * biteOpen - 0.05f * biteSnap);
            float headLenScl = hs * (1f + 0.12f * biteOpen + 0.08f * R);
            float headFwd = rHead.height * SC * hs * 0.17f + 6f * lungeOut;
            float shake = R * 1.6f + biteSnap * 2.2f;
            float hx = cx + Mathf.cosDeg(headAng) * headFwd + Mathf.range(shake);
            float hy = cy + Mathf.sinDeg(headAng) * headFwd + Mathf.range(shake);

            // Lưu tọa độ mõm thực tế cho FlameBullet & cú cắn!
            mawAng = headAng;
            mawX = hx + Mathf.cosDeg(headAng) * (rHead.height * SC * headLenScl * 0.45f);
            mawY = hy + Mathf.sinDeg(headAng) * (rHead.height * SC * headLenScl * 0.45f);
            mawValid = true;

            Draw.z(z + 0.0034f);
            Draw.rect(rHead, hx, hy, rHead.width * SC * headWidthScl, rHead.height * SC * headLenScl, headAng - 90f);

            Draw.mixcol();

            // ---- 8) ÁNH SÁNG NĂNG LƯỢNG ĐIỆN ẢNH (mắt đỏ rực lửa đúng hốc mắt sọ, lửa họng giữa hàm răng)
            float glow = 0.45f + 0.25f * Mathf.absin(t, 12f, 1f) + 0.55f * R + (shooting ? 0.35f : 0f) + 0.4f * lungeOut;
            Draw.z(z + 0.0045f);
            Draw.blend(Blending.additive);

            // Ánh đỏ rực đúng vị trí 2 mắt của hộp sọ
            Draw.color(FL_RED, FL_ORG, 0.3f + 0.4f * R);
            Draw.alpha(Mathf.clamp(glow));
            for (int s = -1; s <= 1; s += 2) {
                float ex = hx + Angles.trnsx(headAng, 2f * SC * headLenScl, s * 25f * SC * headWidthScl);
                float ey = hy + Angles.trnsy(headAng, 2f * SC * headLenScl, s * 25f * SC * headWidthScl);
                Fill.circle(ex, ey, (2.6f + 1.4f * R) * hs);
            }

            // Chớp lửa ở kẽ hàm răng khi đang phun bão lửa, Gầm hoặc ngoạm Cắn!
            if (shooting || R > 0.05f || biteOpen > 0.1f) {
                float mouthInt = Math.max(shooting ? 0.85f : 0f, Math.max(R * 0.9f, biteOpen * 0.75f));
                Draw.color(FL_YEL, FL_ORG, Mathf.absin(t, 4f, 1f));
                Draw.alpha(mouthInt);
                Drawf.tri(mawX, mawY, 9f * hs, 24f * hs, headAng);
                Fill.circle(mawX, mawY, 5.5f + 3.0f * Mathf.absin(t, 3f, 1f));
                Drawf.light(mawX, mawY, 90f, FL_ORG, 0.72f * mouthInt);
            }

            Draw.blend();
            Draw.reset();
            Draw.z(z);
        }
    }
}
