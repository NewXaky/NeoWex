package newex.content;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
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
 * Unit Zeralion - Cơ giáp sư tử vàng 2 chân, 2 pháo vai.
 *
 * - Di chuyển 2 chân kiểu mới: mỗi bước chỉ một chân đưa tới trước, chân còn lại đứng yên tại chỗ cũ, rồi đổi chân (lặp lại);
 *   mỗi lần chân chạm đất rung nhẹ màn hình.
 * - 2 khe vũ khí, mỗi khe bắn theo chu kỳ 3 nhịp (cả 2 khe bắn cùng lúc):
 *     nhịp 1: 3 viên, mỗi viên 50% Tấn công
 *     nhịp 2: 2 viên, mỗi viên 125% Tấn công + 10% máu hiện tại
 *     nhịp 3: 1 viên, 270% Tấn công + 30% máu hiện tại
 *   rồi quay lại nhịp 1.
 * - Nhịp càng về sau càng "nặng": rung màn hình mạnh dần, giật lùi + giật nòng mạnh dần, đạn bay nhanh dần,
 *   thời gian sống của đạn = tầm bắn / tốc độ nên đạn biến mất (nổ) đúng ở rìa tầm bắn.
 * - Hiệu ứng đạn: 16 khung (video 2) khi bay + 15 khung (video 1) khi nổ, dùng sprite trong sprites/bullets/zeralion/.
 */
public final class Zeralion {
    public static UnitType zeralion;

    // ---- Hằng số chỉnh cân bằng ----
    /** Tầm bắn (đơn vị thế giới, 8 = 1 ô). */
    public static final float RANGE = 260f;
    /** Chỉ số Tấn công chuẩn (cùng quy ước với Honivery); nhân thêm damageMultiplier của unit. */
    static final float BASE_ATK = 300f;

    static final int STAGES = 3;
    /** Số viên mỗi khe ở từng nhịp. */
    static final int[] COUNT = {3, 2, 1};
    /** % Tấn công mỗi viên. */
    static final float[] ATK_RATIO = {0.50f, 1.25f, 2.70f};
    /** % máu HIỆN TẠI của Zeralion cộng thêm vào mỗi viên (nhịp 2: 10%, nhịp 3: 30%). */
    static final float[] HP_RATIO = {0f, 0.10f, 0.30f};
    /** Tốc độ đạn (đơn vị/tick): nhanh dần theo nhịp. Thời gian sống = RANGE / tốc độ. */
    static final float[] SPEED = {26f, 36f, 50f};
    /** Độ tản góc (độ) giữa các viên cùng khe. */
    static final float[] SPREAD = {8f, 4.5f, 0f};
    /** Cỡ sprite đạn / vụ nổ theo nhịp. */
    static final float[] SCALE = {0.50f, 0.72f, 1.05f};
    /** Rung màn hình khi bắn (cường độ, thời gian tick): nhịp 1 không rung, nhịp 3 rung mạnh nhất (vẫn nhẹ). */
    static final float[] SHAKE_I = {0f, 1.6f, 3.4f};
    static final float[] SHAKE_T = {0f, 8f, 14f};
    /** Lực đẩy lùi thân unit (mỗi khe). */
    static final float[] KICK = {0f, 0.12f, 0.32f};
    /** Thời gian chờ SAU khi bắn nhịp đó tới nhịp kế (tick): sau nhát nặng nhất nghỉ lâu hơn. */
    static final float[] RELOAD = {30f, 34f, 58f};
    /** Quá ngần này tick không bắn thì chu kỳ quay lại nhịp 1. */
    static final float RESET_AFTER = 150f;

    // ---- Sprite hiệu ứng ----
    static final int SHOT_FRAMES = 16;
    static final int BOOM_FRAMES = 15;
    static final float PX = 0.25f; // 1 pixel sprite = 0.25 đơn vị thế giới (quy ước của mod)
    static TextureRegion[] shotFrames, boomFrames;

    static final Color PURPLE = Color.valueOf("d65cff");
    static final Color PURPLE_L = Color.valueOf("f2a8ff");
    static final Color PURPLE_D = Color.valueOf("6a1fd0");

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

    /** Vụ nổ hố đen tím (video 1): phát hết 15 khung trong 45 tick (~20 fps như video gốc). */
    private static Effect boomFx(final float scale) {
        return new Effect(45f, 200f, e -> {
            if (Vars.headless) return;
            loadFrames();
            int idx = Math.min(BOOM_FRAMES - 1, (int) (e.fin() * BOOM_FRAMES));
            TextureRegion r = boomFrames[idx];
            if (r == null || !r.found()) return;
            float w = r.width * PX * scale, h = r.height * PX * scale;
            Draw.color(Color.white);
            Draw.rect(r, e.x, e.y, w, h, 0f);
            Draw.blend(Blending.additive);
            Draw.color(PURPLE, 0.30f * e.fout());
            Draw.rect(r, e.x, e.y, w * 1.08f, h * 1.08f, 0f);
            Draw.blend();
            Draw.reset();
            Drawf.light(e.x, e.y, w * 1.1f, PURPLE, 0.55f * e.fout());
        });
    }

    /** Chớp lửa đầu nòng, to dần theo nhịp. */
    private static Effect muzzleFx(final float s) {
        return new Effect(18f, 120f, e -> {
            float fin = e.fin(), fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(Color.white, PURPLE_L, fin);
            Drawf.tri(e.x, e.y, 5f * s * fout + 1f, (16f + 22f * s) * Mathf.sqrt(fout), e.rotation);
            Drawf.tri(e.x, e.y, 3f * s * fout + 0.5f, 7f * s * fout, e.rotation + 180f);
            Draw.color(PURPLE, 0.8f * fout);
            Lines.stroke(1.2f + 2.2f * s * fout);
            float rx = e.x + Angles.trnsx(e.rotation, 5f * s), ry = e.y + Angles.trnsy(e.rotation, 5f * s);
            Lines.circle(rx, ry, 3f * s + (10f + 12f * s) * e.finpow());
            Draw.color(PURPLE_L, fout);
            Angles.randLenVectors(e.id, 5 + (int) (4 * s), (6f + 22f * s) * e.finpow(), e.rotation, 26f, (x, y) ->
                spark(e.x + x, e.y + y, 1.6f * fout * (0.6f + s * 0.5f), Mathf.angle(x, y)));
            Draw.blend();
            Draw.reset();
            Drawf.light(e.x, e.y, 30f + 30f * s, PURPLE, 0.7f * fout);
        });
    }

    private static void spark(float x, float y, float size, float rot) {
        Drawf.tri(x, y, size, size * 3f, rot);
        Drawf.tri(x, y, size, size, rot + 180f);
    }

    // ================================================================== Đạn

    /** Đạn tím: vẽ bằng 16 khung video 2 (lặp), nổ bằng 15 khung video 1. */
    static final class ZBullet extends BasicBulletType {
        final int stage;

        ZBullet(int stage) {
            super(SPEED[stage], 0f); // sát thương thật truyền lúc create() theo công thức của nhịp
            this.stage = stage;
            // Thời gian sống = tầm bắn / tốc độ -> đạn chạm đúng rìa tầm bắn thì biến mất (nổ)
            lifetime = RANGE / SPEED[stage];
            width = height = 0f; // không vẽ hình mặc định
            hitSize = 6f + 3f * stage;
            collidesAir = true;
            collidesGround = true;
            pierce = false;
            backColor = trailColor = hitColor = lightColor = PURPLE;
            frontColor = Color.white;
            trailLength = 10 + 2 * stage;
            trailWidth = 1.5f + 1.1f * stage;
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
            // Đạn sống rất ngắn (5-10 tick) nên chạy khung nhanh: ~1.6 khung/tick, lệch pha theo id đạn
            int idx = ((int) (b.time * 1.6f) + b.id) % SHOT_FRAMES;
            if (idx < 0) idx += SHOT_FRAMES;
            TextureRegion r = shotFrames[idx];
            if (r == null || !r.found()) return;
            float sc = SCALE[stage];
            float w = r.width * PX * sc, h = r.height * PX * sc;
            Draw.z(Layer.bullet + 0.01f);
            Draw.color(Color.white);
            Draw.rect(r, b.x, b.y, w, h, b.rotation());
            Draw.blend(Blending.additive);
            Draw.color(PURPLE, 0.35f);
            Draw.rect(r, b.x, b.y, w * 1.12f, h * 1.12f, b.rotation());
            Draw.blend();
            Draw.reset();
        }

        @Override
        public void drawLight(Bullet b) {
            Drawf.light(b.x, b.y, 38f * SCALE[stage], PURPLE, 0.6f);
        }

        @Override
        public void hit(Bullet b, float x, float y) {
            super.hit(b, x, y);
            if (!Vars.headless && stage > 0) Effect.shake(SHAKE_I[stage] * 0.5f, SHAKE_T[stage] * 0.6f, x, y);
        }
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

        /** Weapon.update gọi hàm này mỗi khi đủ điều kiện bắn rồi đặt mount.reload = reload ngay sau đó. */
        @Override
        protected void shoot(Unit unit, WeaponMount m, float shootX, float shootY, float rotation) {
            ZeralionMount mount = (ZeralionMount) m;
            if (Time.time - mount.lastShot > RESET_AFTER) mount.stage = 0;
            mount.lastShot = Time.time;

            final int stage = mount.stage;
            mount.stage = (stage + 1) % STAGES;
            reload = RELOAD[stage]; // thời gian chờ tới nhịp kế

            // Sát thương mỗi viên = %Tấn công (đã nhân damageMultiplier khi create) + %máu hiện tại (chia lại để không bị nhân đôi)
            float dm = Math.max(0.01f, unit.damageMultiplier);
            float dmg = BASE_ATK * ATK_RATIO[stage] + Math.max(0f, unit.health) * HP_RATIO[stage] / dm;

            BulletType type = BULLETS[stage];
            int n = COUNT[stage];
            for (int i = 0; i < n; i++) {
                float off = n == 1 ? 0f : SPREAD[stage] * (i / (float) (n - 1) * 2f - 1f);
                float ang = rotation + off + Mathf.range(inaccuracy);
                type.create(unit, unit.team, shootX, shootY, ang, dmg, 1f, 1f, null, null, mount.aimX, mount.aimY);
            }

            type.shootEffect.at(shootX, shootY, rotation, PURPLE, unit);

            // Cảm giác lực: giật nòng, đẩy lùi thân, rung màn hình tăng dần theo nhịp
            mount.recoil = 0.45f + 0.275f * stage;
            mount.heat = 1f;
            if (KICK[stage] > 0f) unit.vel.add(Tmp.v1.trns(rotation + 180f, KICK[stage]));
            if (!Vars.headless) {
                if (SHAKE_I[stage] > 0f) Effect.shake(SHAKE_I[stage], SHAKE_T[stage], shootX, shootY);
                if (stage == 0) plasmaSound.at(shootX, shootY, Mathf.random(1.2f, 1.35f), 0.65f);
                else if (stage == 1) plasmaSound.at(shootX, shootY, Mathf.random(0.92f, 1.02f), 0.9f);
                else {
                    cannonSound.at(shootX, shootY, Mathf.random(0.78f, 0.86f), 1.1f);
                    plasmaSound.at(shootX, shootY, 0.6f, 1f);
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

        zeralion = new UnitType("zeralion") {{
            constructor = LegsUnit::create;

            localizedName = "Zeralion";
            description = "Cơ giáp sư tử vàng 2 chân, 2 pháo vai bắn chu kỳ 3 nhịp, nhịp sau mạnh hơn nhịp trước.";
            details = "Mỗi khe pháo bắn lần lượt (rồi lặp lại): 3 viên 50% Tấn công -> 2 viên 125% Tấn công + 10% máu hiện tại "
                + "-> 1 viên 270% Tấn công + 30% máu hiện tại. Nhịp càng nặng đạn càng nhanh, màn hình rung nhẹ. "
                + "Đi bằng 2 chân luân phiên: mỗi bước 1 chân bước tới, chân kia đứng yên.";

            outlineColor = Pal.darkOutline;
            envDisabled = Env.none;
            researchCostMultiplier = 7.5f;
            drawCell = false; // sprite đã có chi tiết riêng

            hitSize = 34f;
            health = 6500f;
            armor = 12f;
            speed = 0.52f;
            rotateSpeed = 2.6f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;

            // 2 chân bước luân phiên: 1 chân đưa tới trước, chân kia ở yên, rồi đổi (mỗi bước chân chạm đất rung nhẹ)
            hovering = true;
            allowLegStep = true;
            legCount = 2;
            legLength = 34f;
            legLengthScl = 1f;
            legBaseOffset = 9f;
            legForwardScl = 0.95f;
            legMoveSpace = 1.5f;
            legExtension = -3f;
            legStraightness = 0.3f;
            stepShake = 1.2f;

            weapons.add(new ZeralionWeapon("newex-zeralion-weapon") {{
                x = 15f;
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
                shootSound = Sounds.none; // âm thanh tự phát theo nhịp trong shoot()
                ejectEffect = Fx.none;
                bullet = BULLETS[STAGES - 1]; // cả 3 loại đạn có cùng tầm (tốc độ * thời gian sống = RANGE)
            }});
        }};
    }
}
