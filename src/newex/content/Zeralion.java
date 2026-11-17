package newex.content;

import arc.Core;
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
import arc.struct.IntFloatMap;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Bullet;
import mindustry.gen.LegsUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;

/**
 * Unit Zeralion - Cơ giáp sư tử vàng 4 chân (sprite chân lấy từ Aureon), 2 pháo vai.
 *
 * - 2 khe vũ khí bắn cùng lúc theo chu kỳ 3 nhịp, mỗi nhịp bắn TỪNG VIÊN THẲNG HÀNG (không tản góc):
 *     nhịp 1: 3 viên, mỗi viên 50% Tấn công
 *     nhịp 2: 2 viên, mỗi viên 125% Tấn công + 10% máu hiện tại
 *     nhịp 3: 1 viên, 270% Tấn công + 30% máu hiện tại
 * - Nhịp càng về sau càng nặng: rung màn hình, giật lùi, đạn nhanh dần; đạn nổ đúng ở rìa tầm bắn.
 * - Effect: đạn có vệt đuôi thon + bóng mờ + flare ngang; chớp nòng dạng quạt tia + sóng xung kích;
 *   vụ nổ giữ 15 khung hố đen vàng kim, thêm vòng sóng, tia tỏa, tia lửa và flare ngang.
 */
public final class Zeralion {
    public static UnitType zeralion;

    // ---- Hằng số chỉnh cân bằng ----
    public static final float RANGE = 390f; // 260 x 1.5; thời gian sống đạn = RANGE / tốc độ nên cũng tăng 50%
    static final float BASE_ATK = 300f;

    static final int STAGES = 3;
    static final int[] COUNT = {3, 2, 1};
    static final float[] ATK_RATIO = {0.50f, 1.25f, 2.70f};
    static final float[] HP_RATIO = {0f, 0.10f, 0.30f};
    static final float[] SPEED = {26f, 36f, 50f};
    /** Khoảng cách (tick) giữa các viên nối tiếp trong cùng nhịp. */
    static final float[] GAP = {5f, 9f, 0f};
    static final float[] SCALE = {0.50f, 0.72f, 1.05f};
    static final float[] SHAKE_I = {0f, 1.6f, 3.4f};
    static final float[] SHAKE_T = {0f, 8f, 14f};
    /** Tổng lực đẩy lùi cả nhịp (chia đều cho từng viên). */
    static final float[] KICK = {0f, 0.12f, 0.32f};
    /** Chờ sau khi nhịp đó bắt đầu tới nhịp kế (tick); lớn hơn thời gian bắn hết các viên. */
    static final float[] RELOAD = {30f, 34f, 100f};
    static final float RESET_AFTER = 150f;
    /** Thời gian tụ lực trước đòn 3 (tick). */
    static final float CHARGE = 48f;
    static Effect chargeFx;

    // ---- Sprite hiệu ứng ----
    static final int SHOT_FRAMES = 16;
    static final int BOOM_FRAMES = 15;
    static final float PX = 0.25f;
    static TextureRegion[] shotFrames, boomFrames;

    static final Color GOLD = Color.valueOf("ffc933");
    static final Color GOLD_L = Color.valueOf("fff1a6");
    static final Color GOLD_D = Color.valueOf("b8741a");

    static final BulletType[] BULLETS = new BulletType[STAGES];
    static Sound plasmaSound, cannonSound;

    private Zeralion() {}

    static void loadFrames() {
        if (shotFrames != null) return;
        TextureRegion[] s = new TextureRegion[SHOT_FRAMES];
        TextureRegion[] b = new TextureRegion[BOOM_FRAMES];
        for (int i = 0; i < SHOT_FRAMES; i++) s[i] = Core.atlas.find("newex-zeralion-shot-f" + (i < 10 ? "0" : "") + i);
        for (int i = 0; i < BOOM_FRAMES; i++) b[i] = Core.atlas.find("newex-zeralion-boom-f" + (i < 10 ? "0" : "") + i);
        boomFrames = b;
        shotFrames = s;
    }

    // ================================================================== Hiệu ứng

    /** Vụ nổ: 15 khung hố đen vàng kim + vòng sóng + tia tỏa + tia lửa + flare ngang. */
    private static Effect boomFx(final float scale) {
        return new Effect(55f, 280f, e -> {
            if (Vars.headless) return;
            loadFrames();
            float fin = e.fin(), fout = e.fout();
            int idx = Math.min(BOOM_FRAMES - 1, (int) (Mathf.clamp(e.time / 45f) * BOOM_FRAMES));
            TextureRegion r = boomFrames[idx];
            if (r == null || !r.found()) return;
            float w = r.width * PX * scale, h = r.height * PX * scale;
            float rad = w * 0.5f;

            Draw.color(Color.white);
            Draw.rect(r, e.x, e.y, w, h, 0f);

            Draw.blend(Blending.additive);
            Draw.color(GOLD, 0.30f * fout);
            Draw.rect(r, e.x, e.y, w * 1.08f, h * 1.08f, 0f);

            // chớp trắng 6 tick đầu
            float flash = Mathf.clamp(1f - e.time / 6f);
            if (flash > 0f) {
                Draw.color(Color.white, flash);
                Fill.circle(e.x, e.y, rad * 0.55f * (1f + (1f - flash)));
            }

            // vòng sóng xung kích chính (sáng) + vòng phụ (vàng sẫm, chậm hơn)
            Draw.color(GOLD_L, 0.9f * fout);
            Lines.stroke((2.6f + 1.2f * scale) * fout);
            Lines.circle(e.x, e.y, rad * 1.7f * Interp.pow3Out.apply(fin));
            Draw.color(GOLD_D, 0.7f * fout);
            Lines.stroke((4f + 2f * scale) * fout);
            Lines.circle(e.x, e.y, rad * 1.15f * Interp.pow2Out.apply(fin));

            // tia lửa vệt bay ra
            Draw.color(Color.white, GOLD, fin);
            Angles.randLenVectors(e.id, 12 + (int) (5 * scale), rad * 2.1f * Interp.pow3Out.apply(fin) + 2f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.1f * fout + 0.3f));

            Draw.blend();
            Draw.reset();
            Drawf.light(e.x, e.y, w * 1.2f, GOLD, 0.6f * fout);
        });
    }

    /** Chớp nòng: lõi trắng + quạt tia + cung xung kích + flare ngang + tia lửa vệt. */
    private static Effect muzzleFx(final float s) {
        return new Effect(22f, 150f, e -> {
            float fin = e.fin(), fout = e.fout(), pf = e.finpow();
            Draw.blend(Blending.additive);

            Draw.color(Color.white, GOLD_L, fin);
            Fill.circle(e.x, e.y, (3f + 4f * s) * fout * fout);

            // quạt 7 tia mảnh xòe phía trước nòng
            Draw.color(GOLD_L, GOLD, fin);
            for (int i = -3; i <= 3; i++) {
                float len = (30f + 28f * s) * fout * (1f - Math.abs(i) * 0.13f);
                Drawf.tri(e.x, e.y, 1.1f * s * fout + 0.3f, len, e.rotation + i * 7f);
            }

            // flare ngang mỏng
            Draw.color(GOLD, 0.7f * fout);
            Drawf.tri(e.x, e.y, 1.6f * fout + 0.3f, (40f + 40f * s) * fout, 0f);
            Drawf.tri(e.x, e.y, 1.6f * fout + 0.3f, (40f + 40f * s) * fout, 180f);

            // 2 cung xung kích bay ra phía trước
            Draw.color(GOLD_L, 0.8f * fout);
            Lines.stroke((1f + 1.6f * s) * fout);
            float d1 = (6f + 22f * s) * pf, d2 = d1 * 0.6f;
            Lines.arc(e.x, e.y, d1 + 2f, 0.2f, e.rotation - 36f);
            Lines.arc(e.x, e.y, d2 + 1f, 0.28f, e.rotation - 50f);

            // tia lửa dạng vệt
            Draw.color(Color.white, GOLD_L, fin);
            Lines.stroke(1.1f * fout);
            Angles.randLenVectors(e.id, 7 + (int) (4 * s), (14f + 40f * s) * pf + 2f, e.rotation, 22f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 2f + (3f + 5f * s) * fout));

            Draw.blend();
            Draw.reset();
            Drawf.light(e.x, e.y, 30f + 30f * s, GOLD, 0.7f * fout);
        });
    }

    // ================================================================== Đạn

    static final class ZBullet extends BasicBulletType {
        final int stage;

        ZBullet(int stage) {
            super(SPEED[stage], 0f);
            this.stage = stage;
            lifetime = RANGE / SPEED[stage];
            width = height = 0f;
            hitSize = 6f + 3f * stage;
            collidesAir = true;
            collidesGround = true;
            pierce = false;
            backColor = hitColor = lightColor = GOLD;
            trailColor = GOLD_L;
            frontColor = Color.white;
            trailLength = 14 + 3 * stage;
            trailWidth = 1.6f + 1.2f * stage;
            Effect boom = boomFx(1.0f + 0.5f * stage);
            hitEffect = boom;
            despawnEffect = boom;
            shootEffect = muzzleFx(0.8f + 0.45f * stage);
            smokeEffect = Fx.none;
            hitSound = stage == 2 ? Sounds.explosion : Sounds.none;
            despawnSound = Sounds.none;
        }

        @Override
        public void draw(Bullet b) {
            if (Vars.headless) return;
            loadFrames();
            drawTrail(b);
            int idx = ((int) (b.time * 1.6f) + b.id) % SHOT_FRAMES;
            if (idx < 0) idx += SHOT_FRAMES;
            TextureRegion r = shotFrames[idx];
            if (r == null || !r.found()) return;
            float sc = SCALE[stage];
            float w = r.width * PX * sc, h = r.height * PX * sc;
            float rot = b.rotation();
            float tail = SPEED[stage] * 0.9f;

            Draw.z(Layer.bullet + 0.01f);

            // bóng mờ lùi dần phía sau (ảo ảnh chuyển động nhanh)
            Draw.blend(Blending.additive);
            for (int i = 1; i <= 2; i++) {
                float back = SPEED[stage] * 0.3f * i;
                Draw.color(i == 1 ? GOLD_L : GOLD_D, 0.38f / i);
                Draw.rect(r, b.x - Angles.trnsx(rot, back), b.y - Angles.trnsy(rot, back), w * (1f - 0.12f * i), h * (1f - 0.12f * i), rot);
            }

            // đuôi thon: rộng vàng + hẹp trắng
            Draw.color(GOLD, 0.45f);
            Drawf.tri(b.x, b.y, h * 0.30f, tail, rot + 180f);
            Draw.color(Color.white, 0.8f);
            Drawf.tri(b.x, b.y, h * 0.10f, tail * 1.25f, rot + 180f);
            Draw.blend();

            // thân đạn
            Draw.color(Color.white);
            Draw.rect(r, b.x, b.y, w, h, rot);
            Draw.blend(Blending.additive);
            Draw.color(GOLD, 0.35f);
            Draw.rect(r, b.x, b.y, w * 1.12f, h * 1.12f, rot);

            // flare ngang nhỏ quanh viên đạn
            Draw.color(GOLD_L, 0.55f);
            Drawf.tri(b.x, b.y, 0.9f * sc + 0.2f, 16f * sc + 6f, 0f);
            Drawf.tri(b.x, b.y, 0.9f * sc + 0.2f, 16f * sc + 6f, 180f);
            Draw.blend();
            Draw.reset();
        }

        @Override
        public void drawLight(Bullet b) {
            Drawf.light(b.x, b.y, 38f * SCALE[stage], GOLD, 0.6f);
        }

        @Override
        public void hit(Bullet b, float x, float y) {
            super.hit(b, x, y);
            if (!Vars.headless && stage > 0) Effect.shake(SHAKE_I[stage] * 0.5f, SHAKE_T[stage] * 0.6f, x, y);
        }
    }

    /** Dữ liệu gắn vào effect tụ lực để bám theo nòng súng. */
    static final class ChargeData {
        final Unit unit;
        final ZeralionWeapon w;
        final ZeralionMount m;

        ChargeData(Unit unit, ZeralionWeapon w, ZeralionMount m) {
            this.unit = unit;
            this.w = w;
            this.m = m;
        }
    }

    /** Tụ lực đòn 3: hạt vàng xoáy hút vào, quả cầu năng lượng lớn dần, vòng co lại, cung xoay, tia ngắm, chớp nén rồi nổ. */
    private static Effect makeChargeFx() {
        return new Effect(CHARGE + 6f, 500f, e -> {
            if (Vars.headless) return;
            float x = e.x, y = e.y, rot = e.rotation;
            if (e.data instanceof ChargeData) {
                ChargeData d = (ChargeData) e.data;
                if (d.unit.isValid()) {
                    x = d.w.muzzleX(d.unit, d.m);
                    y = d.w.muzzleY(d.unit, d.m);
                    rot = d.w.muzzleAng(d.unit, d.m);
                }
            }
            final float cx = x, cy = y;
            float t = Mathf.clamp(e.time / CHARGE), t2 = t * t, t3 = t2 * t;
            float fade = e.time > CHARGE ? Mathf.clamp(1f - (e.time - CHARGE) / 6f) : 1f;

            if (t > 0.15f) Effect.shake(0.25f + 2.4f * t2, 5f, cx, cy);

            Draw.blend(Blending.additive);

            // 1. hạt vàng xoáy hút vào nòng (có vệt mờ phía sau)
            Draw.color(GOLD_L);
            for (int i = 0; i < 28; i++) {
                float seed = i * 137.5f, life = Mathf.mod(t * 1.5f + i / 28f, 1f);
                for (int j = 0; j < 3; j++) {
                    float l = life - j * 0.04f;
                    if (l < 0f) break;
                    float rr = 6f + 74f * (1f - l) * (1f - l);
                    float a = seed + l * 220f * (1f + t);
                    Draw.alpha(Mathf.sin(l * Mathf.PI) * (0.35f + 0.65f * t) / (1f + j) * fade);
                    Fill.circle(cx + Angles.trnsx(a, rr), cy + Angles.trnsy(a, rr), (0.7f + 1.5f * l) / (1f + j * 0.35f));
                }
            }

            // 2. vòng sóng co lại vào tâm (3 vòng so le)
            Lines.stroke(0.8f + 1.4f * t);
            for (int i = 0; i < 3; i++) {
                float l = Mathf.mod(t * 2f + i / 3f, 1f);
                Draw.color(GOLD_L, Mathf.sin(l * Mathf.PI) * (0.3f + 0.6f * t) * fade);
                Lines.circle(cx, cy, 8f + 52f * (1f - l) * (1f - l));
            }

            // 3. cung vàng xoay ngược chiều, nhanh dần
            Lines.stroke(1.2f + 1.6f * t);
            for (int i = 0; i < 3; i++) {
                Draw.color(i % 2 == 0 ? GOLD_L : GOLD, (0.35f + 0.55f * t) * fade);
                Lines.arc(cx, cy, 12f + 9f * i + 5f * t, 0.22f, e.time * (4f + 7f * t) * (i % 2 == 0 ? 1f : -1f) + i * 120f);
            }

            // 4. quả cầu năng lượng: lớn dần, đập nhịp nhanh dần
            float orb = (1.5f + 11f * t2) * (1f + 0.12f * Mathf.sin(e.time * (0.5f + 0.7f * t))) * fade;
            Draw.color(GOLD_D, 0.5f);
            Fill.circle(cx, cy, orb * 1.9f);
            Draw.color(GOLD, 0.7f);
            Fill.circle(cx, cy, orb * 1.35f);
            Draw.color(GOLD_L, 0.9f);
            Fill.circle(cx, cy, orb);
            Draw.color(Color.white);
            Fill.circle(cx, cy, orb * 0.55f);

            // 5. tia ngắm dài dần theo hướng bắn + flare ngang
            Draw.color(GOLD, (0.25f + 0.5f * t) * fade);
            Drawf.tri(cx, cy, 0.6f + 1.6f * t3, 20f + 150f * t3, rot);
            Draw.color(GOLD_L, (0.15f + 0.6f * t2) * fade);
            Drawf.tri(cx, cy, 0.5f + 1.5f * t2, 10f + 90f * t2, 0f);
            Drawf.tri(cx, cy, 0.5f + 1.5f * t2, 10f + 90f * t2, 180f);

            // 6. 10 tick cuối: chớp trắng nén + vòng co mạnh vào tâm
            float snap = Mathf.clamp((e.time - (CHARGE - 10f)) / 10f);
            if (snap > 0f) {
                Draw.color(Color.white, snap * fade);
                Fill.circle(cx, cy, orb * (1f + 0.6f * snap));
                Lines.stroke(3.5f * (1f - snap) + 0.5f);
                Draw.color(GOLD_L, snap * fade);
                Lines.circle(cx, cy, 44f * (1f - snap) * (1f - snap) + 2f);
            }

            // 7. khoảnh khắc nổ ra sau khi bắn
            if (e.time > CHARGE) {
                float post = Mathf.clamp((e.time - CHARGE) / 6f);
                Lines.stroke(4f * (1f - post));
                Draw.color(GOLD_L, 1f - post);
                Lines.circle(cx, cy, 10f + 50f * post);
            }

            Draw.blend();
            Draw.reset();
            Drawf.light(cx, cy, 30f + 90f * t2, GOLD, (0.3f + 0.6f * t) * fade);
        });
    }

    // ================================================================== Khóa (bind)

    /** id unit -> thời điểm (Time.time) hết khóa. Unit bị khóa không bắn được. */
    static final IntFloatMap BOUND_UNTIL = new IntFloatMap();

    /** Khóa unit trong {@code ticks} tick (cộng dồn lấy mốc lớn hơn). */
    public static void bind(Unit u, float ticks) {
        if (u == null) return;
        float until = Time.time + ticks;
        if (until > BOUND_UNTIL.get(u.id, 0f)) BOUND_UNTIL.put(u.id, until);
    }

    /** true nếu unit đang bị Zeralion khóa. */
    public static boolean isBound(Unit u) {
        if (u == null) return false;
        float until = BOUND_UNTIL.get(u.id, 0f);
        if (until <= 0f) return false;
        if (!u.isValid() || Time.time >= until) {
            BOUND_UNTIL.remove(u.id, 0f);
            return false;
        }
        return true;
    }

    // ================================================================== Vũ khí 3 nhịp

    public static class ZeralionMount extends WeaponMount {
        public int stage = 0;
        public float lastShot = -9999f;

        public ZeralionMount(Weapon weapon) {
            super(weapon);
        }
    }

    public static class ZeralionWeapon extends Weapon {
        public ZeralionWeapon(String name) {
            super(name);
            mountType = ZeralionMount::new;
        }

        /** Viên đầu bắn ngay, các viên sau nối tiếp theo GAP[stage] tick, tất cả bắn thẳng theo nòng. */
        @Override
        protected void shoot(Unit unit, WeaponMount m, float shootX, float shootY, float rotation) {
            ZeralionMount mount = (ZeralionMount) m;
            if (Time.time - mount.lastShot > RESET_AFTER) mount.stage = 0;

            final int stage = mount.stage;
            mount.stage = (stage + 1) % STAGES;
            reload = RELOAD[stage];

            int n = COUNT[stage];
            mount.lastShot = Time.time + GAP[stage] * (n - 1);

            if (stage == 2) { // đòn 3: tụ lực CHARGE tick rồi mới bắn (hướng nòng cập nhật lúc bắn)
                mount.lastShot = Time.time + CHARGE;
                chargeFx.at(shootX, shootY, rotation, GOLD, new ChargeData(unit, this, mount));
                if (!Vars.headless) {
                    plasmaSound.at(shootX, shootY, 0.5f, 0.9f);
                    Time.run(CHARGE * 0.55f, () -> plasmaSound.at(shootX, shootY, 0.85f, 0.9f));
                    Time.run(CHARGE * 0.85f, () -> plasmaSound.at(shootX, shootY, 1.5f, 0.8f));
                }
                Time.run(CHARGE, () -> {
                    if (unit.isValid()) fireLive(unit, mount, stage);
                });
                return;
            }
            fire(unit, mount, stage, shootX, shootY, rotation);
            for (int i = 1; i < n; i++) {
                Time.run(GAP[stage] * i, () -> {
                    if (unit.isValid()) fireLive(unit, mount, stage);
                });
            }
        }

        /** Tính lại vị trí/hướng nòng tại thời điểm bắn (unit có thể đã di chuyển/xoay). */
        private void fireLive(Unit unit, ZeralionMount mount, int stage) {
            float wr = unit.rotation - 90f + (rotate ? mount.rotation : baseRotation);
            float mx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y) + Angles.trnsx(wr, shootX, shootY);
            float my = unit.y + Angles.trnsy(unit.rotation - 90f, x, y) + Angles.trnsy(wr, shootX, shootY);
            fire(unit, mount, stage, mx, my, wr + 90f);
        }

        float wrot(Unit u, ZeralionMount m) {
            return u.rotation - 90f + (rotate ? m.rotation : baseRotation);
        }

        public float muzzleX(Unit u, ZeralionMount m) {
            return u.x + Angles.trnsx(u.rotation - 90f, x, y) + Angles.trnsx(wrot(u, m), shootX, shootY);
        }

        public float muzzleY(Unit u, ZeralionMount m) {
            return u.y + Angles.trnsy(u.rotation - 90f, x, y) + Angles.trnsy(wrot(u, m), shootX, shootY);
        }

        public float muzzleAng(Unit u, ZeralionMount m) {
            return wrot(u, m) + 90f;
        }

        private void fire(Unit unit, ZeralionMount mount, int stage, float bx, float by, float rotation) {
            float dm = Math.max(0.01f, unit.damageMultiplier);
            float dmg = BASE_ATK * ATK_RATIO[stage] + Math.max(0f, unit.health) * HP_RATIO[stage] / dm;

            BulletType type = BULLETS[stage];
            float ang = rotation + Mathf.range(inaccuracy);
            type.create(unit, unit.team, bx, by, ang, dmg, 1f, 1f, null, null, mount.aimX, mount.aimY);
            type.shootEffect.at(bx, by, rotation, GOLD, unit);

            mount.recoil = 0.45f + 0.275f * stage;
            mount.heat = 1f;
            float kick = KICK[stage] / COUNT[stage];
            if (kick > 0f) unit.vel.add(Tmp.v1.trns(rotation + 180f, kick));
            if (!Vars.headless) {
                if (SHAKE_I[stage] > 0f) Effect.shake(SHAKE_I[stage], SHAKE_T[stage], bx, by);
                if (stage == 0) plasmaSound.at(bx, by, Mathf.random(1.2f, 1.35f), 0.65f);
                else if (stage == 1) plasmaSound.at(bx, by, Mathf.random(0.92f, 1.02f), 0.9f);
                else {
                    cannonSound.at(bx, by, Mathf.random(0.78f, 0.86f), 1.1f);
                    plasmaSound.at(bx, by, 0.6f, 1f);
                }
            }
        }
    }

    // ================================================================== Khởi tạo unit

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        plasmaSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("plasma-shot-3");
        cannonSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("cannon-shot-1");
        for (int i = 0; i < STAGES; i++) BULLETS[i] = new ZBullet(i);
        chargeFx = makeChargeFx();

        zeralion = new UnitType("zeralion") {{
            constructor = LegsUnit::create;

            localizedName = "Zeralion";
            description = "Cơ giáp sư tử vàng 4 chân, 2 pháo vai bắn chu kỳ 3 nhịp, nhịp sau mạnh hơn nhịp trước.";
            details = "Mỗi khe pháo bắn lần lượt (rồi lặp lại): 3 viên 50% Tấn công -> 2 viên 125% Tấn công + 10% máu hiện tại "
                + "-> 1 viên 270% Tấn công + 30% máu hiện tại. Các viên bắn thẳng hàng nối tiếp nhau; "
                + "nhịp càng nặng đạn càng nhanh, màn hình rung nhẹ. Đòn 3 tụ lực gần 1 giây trước khi bắn.";

            outlineColor = Pal.darkOutline;
            envDisabled = Env.none;
            researchCostMultiplier = 7.5f;
            drawCell = false;

            hitSize = 30f;
            health = 6500f;
            armor = 12f;
            speed = 0.52f;
            rotateSpeed = 2.6f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;

            // 4 chân kiểu Aureon (sprite leg / leg-base / joint / joint-base copy từ Aureon)
            hovering = true;
            allowLegStep = true;
            legCount = 4;
            legLength = 27f;
            legForwardScl = 0.6f;
            legMoveSpace = 1.2f;
            legBaseOffset = 5f;
            legExtension = -2f;
            stepShake = 1f;

            weapons.add(new ZeralionWeapon("newex-zeralion-weapon") {{
                x = 12f;
                y = 4f;
                mirror = true;
                alternate = false; // 2 khe bắn cùng lúc
                top = true;
                rotate = true;
                rotateSpeed = 7f;
                rotationLimit = 70f;
                shootCone = 16f;
                shootX = 0f;
                shootY = 11.5f;
                reload = RELOAD[0];
                recoil = 3.4f;
                recoilTime = 28f;
                inaccuracy = 0.6f;
                shootSound = Sounds.none; // âm thanh phát theo nhịp trong fire()
                ejectEffect = Fx.none;
                bullet = BULLETS[STAGES - 1]; // 3 loại đạn cùng tầm (tốc độ * thời gian sống = RANGE)
            }});
        }};
    }
}
