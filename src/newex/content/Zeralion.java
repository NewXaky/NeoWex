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
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.LegsUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.StatusEffect;
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

    // ================================================================== Nội tại: khóa mục tiêu + laser cấm hành động

    public static final float LOCK_TICKS = 90f;       // 1.5s khóa như Aureon
    public static final float BIND_TICKS = 54f;       // 0.9s cấm hành động
    public static final float BIND_COOLDOWN = 480f;   // 8s nghỉ giữa 2 lần khóa

    static StatusEffect bindStatus;
    static Effect laserFx, bindHitFx, bindSparkFx;
    static final class BindRec { Unit u; float rot; }
    static final Seq<BindRec> recs = new Seq<>();
    static boolean hooked;

    /** Đang bị Zeralion cấm hành động (không bắn, không đi, không quay, không dùng kỹ năng). Pilot không bị ảnh hưởng. */
    public static boolean isBound(Unit u) { return bindStatus != null && u != null && u.hasEffect(bindStatus); }

    static void bind(Unit t, float ticks) {
        if (bindStatus == null || t == null || !t.isValid() || t.dead) return;
        if (!t.hasEffect(bindStatus)) {
            BindRec r = new BindRec();
            r.u = t;
            r.rot = t.rotation;
            recs.add(r);
        }
        t.apply(bindStatus, ticks);
    }

    /** Giữ nguyên hướng nhìn của mục tiêu trong thời gian bị khóa. */
    static void tickBinds() {
        for (int i = recs.size - 1; i >= 0; i--) {
            BindRec r = recs.get(i);
            if (!r.u.isValid() || r.u.dead || !r.u.hasEffect(bindStatus)) recs.remove(i);
            else r.u.rotation = r.rot;
        }
    }

    static void makeBindContent() {
        bindStatus = new StatusEffect("zeralion-bind") {{
            localizedName = "Phong ấn Zeralion";
            color = GOLD;
            speedMultiplier = 0f;
            disarm = true;
            effect = bindSparkFx = new Effect(26f, e -> {
                Draw.color(GOLD_L, GOLD, e.fin());
                Angles.randLenVectors(e.id, 2, 12f * e.finpow() + 2f, (x, y) -> Fill.circle(e.x + x, e.y + y, 1.4f * e.fout() + 0.2f));
                Draw.reset();
            });
            effectChance = 0.5f;
        }};

        // tia laser vàng kim: lõi đen như hố đen, viền vàng sáng (vành bồi tụ), hạt xoáy dọc tia, đĩa hố đen ở mục tiêu
        laserFx = new Effect(26f, 1400f, e -> {
            if (!(e.data instanceof Object[])) return;
            Object[] d = (Object[]) e.data;
            Unit src = (Unit) d[0];
            Teamc tg = (Teamc) d[1];
            if (tg == null) return;
            float sx = e.x, sy = e.y;
            if (src != null && src.isValid()) {
                sx = src.x + Angles.trnsx(src.rotation, 12f);
                sy = src.y + Angles.trnsy(src.rotation, 12f);
            }
            float tx = tg.x(), ty = tg.y();
            float ang = Angles.angle(sx, sy, tx, ty), len = Mathf.dst(sx, sy, tx, ty);
            float fin = e.fin(), fout = e.fout(), grow = Interp.pow3Out.apply(Math.min(1f, fin * 6f));
            float ex = sx + Angles.trnsx(ang, len * grow), ey = sy + Angles.trnsy(ang, len * grow);
            float px = Angles.trnsx(ang + 90f, 1f), py = Angles.trnsy(ang + 90f, 1f);

            Draw.blend(Blending.additive);
            Draw.color(GOLD, 0.28f * fout);
            Lines.stroke(15f * fout + 2f);
            Lines.line(sx, sy, ex, ey);
            Draw.color(GOLD_L, 0.45f * fout);
            Lines.stroke(9f * fout + 1f);
            Lines.line(sx, sy, ex, ey);
            Draw.blend();

            Draw.color(Color.black, 0.95f * Math.min(1f, fout * 2f));
            Lines.stroke(5.5f * fout + 0.5f);
            Lines.line(sx, sy, ex, ey);

            Draw.blend(Blending.additive);
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float off = sgn * (3.4f * fout + 1.2f);
                Draw.color(GOLD_L, fout);
                Lines.stroke(1.8f * fout + 0.4f);
                Lines.line(sx + px * off, sy + py * off, ex + px * off, ey + py * off);
            }
            Draw.color(Color.white, fout);
            int n = 18;
            for (int i = 0; i < n; i++) {
                float p = Mathf.mod(i / (float) n + fin * 1.6f, 1f);
                float off = Mathf.sin(p * 40f + e.time * 0.9f + i) * (4.5f * fout + 0.8f);
                Fill.circle(sx + (ex - sx) * p + px * off, sy + (ey - sy) * p + py * off, 1.1f * fout + 0.3f);
            }
            Draw.color(GOLD_L, fout);
            Fill.circle(sx, sy, 5f * fout + 1f);
            Draw.color(Color.white, fout);
            Fill.circle(sx, sy, 2.5f * fout);
            Draw.blend();

            Draw.color(Color.black, 0.9f * fout);
            Fill.circle(tx, ty, 8f * fout * grow);
            Draw.blend(Blending.additive);
            Draw.color(GOLD_L, fout);
            Lines.stroke(2f * fout + 0.4f);
            Lines.circle(tx, ty, 9f * grow + 10f * (1f - fout));
            Draw.blend();
            Draw.reset();
            Drawf.light(sx, sy, 60f, GOLD, 0.6f * fout);
        });

        // nổ hố đen vàng kim tại mục tiêu khi bị khóa
        bindHitFx = new Effect(32f, 160f, e -> {
            float fin = e.fin(), fout = e.fout();
            Draw.color(Color.black, fout);
            Fill.circle(e.x, e.y, 10f * Interp.pow2Out.apply(Math.min(1f, fin * 3f)) * (0.6f + 0.4f * fout));
            Draw.blend(Blending.additive);
            Draw.color(GOLD_L, fout);
            Lines.stroke(3f * fout + 0.5f);
            Lines.circle(e.x, e.y, 10f + 26f * Interp.pow3Out.apply(fin));
            Draw.color(GOLD, fout);
            Lines.stroke(2f * fout + 0.3f);
            Lines.circle(e.x, e.y, 8f + 14f * Interp.pow2Out.apply(fin));
            Draw.color(Color.white, GOLD_L, fin);
            Angles.randLenVectors(e.id, 10, 28f * Interp.pow3Out.apply(fin) + 4f, (x, y) -> Fill.circle(e.x + x, e.y + y, 1.2f * fout + 0.3f));
            Draw.blend();
            Draw.reset();
        });

        if (!hooked) {
            hooked = true;
            Events.run(EventType.Trigger.update, Zeralion::tickBinds);
        }
    }

    public static class BindLockAbility extends Ability {
        public Teamc target;
        public float progress, cooldown, phase;

        @Override
        public void update(Unit unit) {
            phase += Time.delta;
            if (isBound(unit)) { progress = 0f; return; }
            if (cooldown > 0f) { cooldown -= Time.delta; target = null; progress = 0f; return; }

            if (target != null && !(target instanceof Unit && validTarget(unit, (Unit) target))) { target = null; progress = 0f; }

            if (unit.isPlayer()) {
                Teamc hov = Units.closestTarget(unit.team, unit.aimX, unit.aimY, 48f,
                    u -> u.checkTarget(true, true) && !isBound(u) && unit.within(u, RANGE),
                    b -> false);
                if (hov != null && hov != target) { target = hov; progress = 0f; }
            }
            if (target == null) {
                target = Units.closestTarget(unit.team, unit.x, unit.y, RANGE,
                    u -> u.checkTarget(true, true) && !isBound(u),
                    b -> false);
                progress = 0f;
            }
            if (!(target instanceof Unit)) { target = null; return; }

            progress = Math.min(1f, progress + Time.delta / LOCK_TICKS);
            if (progress >= 1f) fire(unit, (Unit) target);
        }

        private boolean validTarget(Unit unit, Unit t) {
            return t.isValid() && !t.dead && t.hittable() && unit.within(t, RANGE);
        }

        private void fire(Unit unit, Unit tu) {
            laserFx.at(unit.x, unit.y, unit.angleTo(tu), GOLD, new Object[]{unit, tu});
            bindHitFx.at(tu.x, tu.y, 0f, GOLD);
            if (!Vars.headless) {
                cannonSound.at(unit.x, unit.y, 1.4f, 0.9f);
                Effect.shake(2.5f, 10f, tu.x, tu.y);
            }
            if (!Vars.net.client()) bind(tu, BIND_TICKS);
            cooldown = BIND_COOLDOWN;
            target = null;
            progress = 0f;
        }

        @Override
        public void draw(Unit unit) {
            if (!(target instanceof Unit) || progress <= 0.02f) return;
            final Unit tu = (Unit) target;
            final boolean mine = Vars.player != null && Vars.player.unit() == unit;
            final float pr = progress, ph = phase;
            Draw.draw(Layer.overlayUI, () -> {
                float r = Math.max(tu.hitSize() * 0.7f, 14f), a = mine ? 1f : 0.6f;
                Draw.color(pr >= 1f ? Color.white : GOLD, a);
                Lines.stroke(1.6f);
                Lines.circle(tu.x, tu.y, r * (1f + 1.2f * (1f - pr)));
                Draw.color(GOLD_L, a);
                for (int i = 0; i < 4; i++) {
                    float ang = i * 90f + ph * 1.5f * (1f + pr);
                    Drawf.tri(tu.x + Angles.trnsx(ang, r * 1.35f), tu.y + Angles.trnsy(ang, r * 1.35f), 3.5f, 6f, ang + 180f);
                }
                Draw.color(GOLD, a * 0.9f);
                Lines.stroke(2f);
                Lines.arc(tu.x, tu.y, r * 1.6f, pr, 90f);
                Draw.reset();
            });
        }
    }

    // ================================================================== Khởi tạo unit

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        plasmaSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("plasma-shot-3");
        cannonSound = Vars.headless ? Sounds.none : Vars.tree.loadSound("cannon-shot-1");
        for (int i = 0; i < STAGES; i++) BULLETS[i] = new ZBullet(i);
        chargeFx = makeChargeFx();
        makeBindContent();

        zeralion = new UnitType("zeralion") {{
            constructor = LegsUnit::create;

            localizedName = "Zeralion";
            description = "Cơ giáp sư tử vàng 4 chân, 2 pháo vai bắn chu kỳ 3 nhịp, nhịp sau mạnh hơn nhịp trước.";
            details = "Mỗi khe pháo bắn lần lượt (rồi lặp lại): 3 viên 50% Tấn công -> 2 viên 125% Tấn công + 10% máu hiện tại "
                + "-> 1 viên 270% Tấn công + 30% máu hiện tại. Các viên bắn thẳng hàng nối tiếp nhau; "
                + "nhịp càng nặng đạn càng nhanh, màn hình rung nhẹ. Đòn 3 tụ lực gần 1 giây trước khi bắn. "
                + "Nội tại: tự khóa mục tiêu 1.5 giây rồi bắn tia laser vàng kim, cấm mục tiêu hành động 0.9 giây "
                + "(không bắn, không di chuyển, không quay, không dùng kỹ năng; pilot vẫn hoạt động), hồi 8 giây.";

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
            abilities.add(new BindLockAbility());

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
