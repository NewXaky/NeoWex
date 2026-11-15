package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Entityc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.consumers.ConsumeCoolant;
import newex.NexBlackHole;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ emperfum.js. Pháo hạng nặng bắn chậm; MK1 / MK2 (đánh đất) / MK2B (phòng không).
 * Block do Java tạo (`new ItemTurret`) nên emperfum.hjson KHÔNG có `type:` cấp cao nhất (bài 29a).
 */
public final class Emperfum {
    private Emperfum() {}

    // JS: isVN() = locale bắt đầu bằng "vi", mặc định "en" (bài 8)
    private static boolean isVN() {
        String loc = Core.settings.getString("locale", "en");
        return loc.startsWith("vi");
    }

    static final class ReqMK2 { final int surgeAlloy = 500, plastanium = 1200, copper = 9000; }
    static final class ReqMK2B { final int thorium = 1900, titanium = 2400, silicon = 3100; }
    static final ReqMK2 reqMK2 = new ReqMK2();
    static final ReqMK2B reqMK2B = new ReqMK2B();

    static final Color C_PURPLE_LIGHT = Color.valueOf("#e9d5ff");
    static final Color C_PURPLE_MAIN = Color.valueOf("#c084fc");
    static final Color C_PURPLE_DARK = Color.valueOf("#9333ea");
    static final Color C_WHITE = Color.valueOf("#ffffff");

    public static ItemTurret block;
    static TextureRegion barrel1Region, barrel2Region;

    static Effect muzzleEffect, clusterFlashEffect, starDespawnEffect;
    static StarBullet starBullet;
    static MicroBullet microGround, microAir;
    static SubBullet subGround, subAir;
    static ClusterBullet clusterGround, clusterAir;
    static MainBullet mainMK1, mainMK2, mainMK2B;
    /** Lỗ đen sinh ra tại điểm nổ của đạn chính (MK1 / MK2 / MK2B). */
    static NexBlackHole.BlackHoleBulletType holeMK1, holeMK2, holeMK2B;

    private static TextureRegion findTexture(String name) {
        String[] candidates = {"newex-" + name, name, "newex-blocks-turrets-" + name, "newex-sprites-" + name};
        for (String c : candidates) {
            TextureRegion reg = PerfCore.region(c);
            if (reg != null && reg.found()) return reg;
        }
        return PerfCore.region("clear");
    }

    // ------------------------------------------------------------------ vẽ

    static void draw4PointStar(float x, float y, float size, float rotation, Color mainColor, Color coreColor) {
        float rotRad = rotation * Mathf.degRad;
        float cosA = (float) Math.cos(rotRad), sinA = (float) Math.sin(rotRad);
        float innerR = size * 0.18f, outerR = size;
        float[] points = {0, outerR, innerR, innerR, outerR, 0, innerR, -innerR,
            0, -outerR, -innerR, -innerR, -outerR, 0, -innerR, innerR};
        float[] tr = new float[16];
        for (int i = 0; i < 8; i++) {
            float px = points[i * 2], py = points[i * 2 + 1];
            tr[i * 2] = x + (px * cosA - py * sinA);
            tr[i * 2 + 1] = y + (px * sinA + py * cosA);
        }
        Draw.color(mainColor);
        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;
            Fill.tri(x, y, tr[i * 2], tr[i * 2 + 1], tr[next * 2], tr[next * 2 + 1]);
        }
        if (coreColor != null) {
            Draw.color(coreColor);
            float outerR2 = size * 0.55f;
            for (int i = 0; i < 8; i++) {
                float px = points[i * 2] * (outerR2 / outerR), py = points[i * 2 + 1] * (outerR2 / outerR);
                tr[i * 2] = x + (px * cosA - py * sinA);
                tr[i * 2 + 1] = y + (px * sinA + py * cosA);
            }
            for (int i = 0; i < 8; i++) {
                int next = (i + 1) % 8;
                Fill.tri(x, y, tr[i * 2], tr[i * 2 + 1], tr[next * 2], tr[next * 2 + 1]);
            }
        }
        Draw.reset();
    }

    static void drawWindRing(float cx, float cy, float radiusX, float radiusY, float angle, float strokeWidth, Color color) {
        Draw.color(color);
        Lines.stroke(strokeWidth);
        int steps = 12;
        float lastX = 0f, lastY = 0f;
        float rad = angle * Mathf.degRad;
        float cosA = (float) Math.cos(rad), sinA = (float) Math.sin(rad);
        for (int i = 0; i <= steps; i++) {
            float a = (i * (360f / steps)) * Mathf.degRad;
            float lx = (float) Math.cos(a) * radiusX, ly = (float) Math.sin(a) * radiusY;
            float rx = cx + (lx * cosA - ly * sinA), ry = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, rx, ry);
            lastX = rx;
            lastY = ry;
        }
        Draw.reset();
    }

    private static void ring(Effect.EffectContainer e, float baseAngle, float off0, float offK, float rx0, float rxK,
                             float ryK, float w, Color col) {
        float offset = off0 - e.fin() * offK;
        float bx = e.x + Angles.trnsx(baseAngle, offset), by = e.y + Angles.trnsy(baseAngle, offset);
        drawWindRing(bx, by, rx0 + e.fin() * rxK, e.fin() * ryK, baseAngle, w * e.fout(), col);
    }

    // ------------------------------------------------------------------ đạn

    static Entityc ownerOf(Bullet b) { return b.owner != null ? b.owner : b; }

    static void spawnFinalStarBullets(Bullet b) {
        clusterFlashEffect.at(b.x, b.y);
        float angle = b.rotation() + Mathf.range(15f);
        Bullet star = starBullet.create(ownerOf(b), b.team, b.x, b.y, angle);
        if (star != null) star.vel.setLength(Mathf.random(30f, 45f));
    }

    static void spawnSubBulletsRandom(Bullet b, BulletType type, int count, float minSpeed, float maxSpeed, float minLife, float maxLife) {
        clusterFlashEffect.at(b.x, b.y);
        Entityc owner = ownerOf(b);
        for (int i = 0; i < count; i++) {
            float angle = (360f / count) * i + Mathf.range(4f);
            Bullet child = type.create(owner, b.team, b.x + Angles.trnsx(angle, 4f), b.y + Angles.trnsy(angle, 4f), angle);
            if (child != null) {
                child.vel.setLength(Mathf.random(minSpeed, maxSpeed));
                child.lifetime = Mathf.random(minLife, maxLife);
            }
        }
    }

    static void checkTargetExplosion(Bullet b) {
        if (b.data instanceof Vec2) {
            Vec2 target = (Vec2) b.data;
            if (Mathf.dst(b.x, b.y, target.x, target.y) <= 12f) b.remove();
        }
    }

    static final class StarBullet extends BasicBulletType {
        StarBullet() {
            speed = 35f; drag = 0f; damage = 1000f; homingPower = 0.8f; homingRange = 450f; lifetime = 10f;
            collidesAir = true; collidesGround = true; collidesTiles = true; collides = true;
            pierce = true; pierceCap = 999;
            hitEffect = starDespawnEffect; despawnEffect = starDespawnEffect;
        }
        @Override public void draw(Bullet b) {}
    }

    /** Đạn nhỏ (mảnh) — air=false: đánh đất, air=true: phòng không. hit/despawned KHÔNG gọi super (bài 19). */
    static final class MicroBullet extends BasicBulletType {
        final boolean air;
        MicroBullet(boolean air) {
            this.air = air;
            speed = 4f; drag = 0.025f; lifetime = 20f; frontColor = C_WHITE;
            hitEffect = starDespawnEffect; despawnEffect = starDespawnEffect;
            splashDamageRadius = 45f; collides = true; pierce = true; pierceCap = 15;
            if (air) {
                damage = 822.5f; splashDamage = 1762.5f; homingPower = 0.25f; homingRange = 150f;
                width = 10f; height = 10f; backColor = C_PURPLE_DARK;
                collidesAir = true; collidesGround = false; collidesTiles = false;
            } else {
                damage = 350f; splashDamage = 750f; homingPower = 0.2f; homingRange = 120f;
                width = 8f; height = 8f; backColor = C_PURPLE_MAIN;
                collidesAir = false; collidesGround = true; collidesTiles = true;
            }
        }
        @Override public void draw(Bullet b) {
            Draw.z(Layer.bullet);
            if (air) draw4PointStar(b.x, b.y, 12f, b.rotation(), C_PURPLE_DARK, C_PURPLE_LIGHT);
            else draw4PointStar(b.x, b.y, 10f, b.rotation(), C_PURPLE_MAIN, C_WHITE);
        }
        @Override public void despawned(Bullet b) { spawnFinalStarBullets(b); }
        @Override public void hit(Bullet b, float x, float y) {
            if (b.collided.size >= pierceCap) { spawnFinalStarBullets(b); b.remove(); }
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            if (b.collided.size >= pierceCap) { spawnFinalStarBullets(b); b.remove(); }
        }
    }

    static final class SubBullet extends BasicBulletType {
        final boolean air;
        SubBullet(boolean air) {
            this.air = air;
            drag = 0.03f; splashDamageRadius = 45f; frontColor = C_WHITE;
            hitEffect = Fx.blastExplosion; despawnEffect = Fx.blastExplosion;
            collides = true; pierce = true; pierceCap = 15;
            if (air) {
                speed = 7f; damage = 940f; splashDamage = 1762.5f; homingPower = 0.2f; homingRange = 200f;
                width = 7f; height = 14f; lifetime = 24f; backColor = C_PURPLE_DARK;
                collidesAir = true; collidesGround = false; collidesTiles = false;
            } else {
                speed = 6f; damage = 400f; splashDamage = 750f; homingPower = 0.15f; homingRange = 160f;
                width = 6f; height = 12f; lifetime = 24f; backColor = C_PURPLE_MAIN;
                collidesAir = false; collidesGround = true; collidesTiles = true;
            }
        }
        @Override public void despawned(Bullet b) {
            if (Mathf.chance(0.8)) {
                clusterFlashEffect.at(b.x, b.y);
                Entityc owner = ownerOf(b);
                MicroBullet micro = air ? microAir : microGround;
                for (int i = 0; i < 40; i++) {
                    float angle = (360f / 40f) * i + Mathf.range(5f);
                    Bullet m = micro.create(owner, b.team, b.x, b.y, angle);
                    if (m != null) {
                        m.vel.setLength(Mathf.random(3f, 7f));
                        m.lifetime = Mathf.random(10f, 20f); // bài 20: float
                    }
                }
            }
        }
        @Override public void hit(Bullet b, float x, float y) { if (b.collided.size >= pierceCap) despawned(b); }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            if (b.collided.size >= pierceCap) despawned(b);
        }
    }

    static final class ClusterBullet extends BasicBulletType {
        final boolean air;
        ClusterBullet(boolean air) {
            this.air = air;
            drag = 0.03f; splashDamageRadius = 45f;
            hitEffect = Fx.blastExplosion; despawnEffect = Fx.blastExplosion;
            collides = true; pierce = true; pierceCap = 15; frontColor = C_PURPLE_LIGHT;
            if (air) {
                speed = 8f; damage = 1057.5f; splashDamage = 1762.5f; width = 12f; height = 24f; lifetime = 22f;
                backColor = C_PURPLE_DARK; collidesAir = true; collidesGround = false; collidesTiles = false;
            } else {
                speed = 7f; damage = 450f; splashDamage = 750f; width = 10f; height = 20f; lifetime = 22f;
                backColor = C_PURPLE_MAIN; collidesAir = false; collidesGround = true; collidesTiles = true;
            }
        }
        private void burst(Bullet b) { spawnSubBulletsRandom(b, air ? subAir : subGround, 8, 4f, 8.5f, 12f, 24f); }
        @Override public void despawned(Bullet b) { burst(b); }
        @Override public void hit(Bullet b, float x, float y) { if (b.collided.size >= pierceCap) burst(b); }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            if (b.collided.size >= pierceCap) burst(b);
        }
    }

    /** Đạn chính theo tier (0 = MK1, 1 = MK2, 2 = MK2B). */
    static final class MainBullet extends BasicBulletType {
        final int tier;
        final Color ringColor;
        MainBullet(int tier) {
            this.tier = tier;
            lifetime = 120f; frontColor = C_WHITE; trailColor = C_PURPLE_LIGHT;
            collides = true; pierce = true; pierceCap = 15;
            if (tier == 0) {
                speed = 18f; drag = 0.035f; damage = 500f; splashDamageRadius = 15f; width = 20f; height = 40f;
                backColor = C_PURPLE_MAIN; trailWidth = 4f; trailLength = 10;
                hitEffect = Fx.hitBulletColor; despawnEffect = Fx.hitBulletColor;
                collidesAir = true; collidesGround = true; collidesTiles = true;
                ringColor = C_PURPLE_LIGHT;
            } else if (tier == 1) {
                speed = 18f; drag = 0f; damage = 500f; splashDamage = 750f; splashDamageRadius = 45f; width = 20f; height = 40f;
                backColor = C_PURPLE_MAIN; trailWidth = 4f; trailLength = 10;
                hitEffect = Fx.blastExplosion; despawnEffect = Fx.blastExplosion;
                collidesAir = false; collidesGround = true; collidesTiles = true;
                ringColor = C_PURPLE_MAIN;
            } else {
                speed = 22f; drag = 0f; damage = 1175f; splashDamage = 1762.5f; splashDamageRadius = 45f; width = 24f; height = 48f;
                backColor = C_PURPLE_DARK; trailWidth = 5f; trailLength = 12;
                hitEffect = Fx.blastExplosion; despawnEffect = Fx.blastExplosion;
                collidesAir = true; collidesGround = false; collidesTiles = false;
                ringColor = C_PURPLE_DARK;
            }
        }
        private void burst(Bullet b) {
            spawnSubBulletsRandom(b, tier == 2 ? clusterAir : clusterGround, 12, 5f, 9.5f, 15f, 28f);
            // burst() có thể bị gọi 2-3 lần cho cùng 1 viên (xem MIGRATION bài 70) -> dùng fdata để chỉ sinh 1 lỗ đen.
            if (b.fdata < 1f) {
                b.fdata = 1f;
                BulletType hole = tier == 0 ? holeMK1 : (tier == 1 ? holeMK2 : holeMK2B);
                if (hole != null) hole.create(ownerOf(b), b.team, b.x, b.y, 0f);
            }
        }
        @Override public void draw(Bullet b) {
            super.draw(b);
            float bAngle = b.rotation();
            float travel = (b.time * 0.08f) % 1f;
            float fout = 1f - travel;
            float offset = 16f - travel * 30f;
            float rx = b.x + Angles.trnsx(bAngle, offset), ry = b.y + Angles.trnsy(bAngle, offset);
            float zoom = travel * 1.5f;
            if (fout > 0.05f) {
                if (tier == 2) drawWindRing(rx, ry, 4f + zoom * 6f, 8f + zoom * 12f, bAngle, 2f * fout, ringColor);
                else drawWindRing(rx, ry, 3f + zoom * 5f, 6f + zoom * 10f, bAngle, 2f * fout, ringColor);
            }
        }
        @Override public void update(Bullet b) { super.update(b); checkTargetExplosion(b); }
        @Override public void despawned(Bullet b) { burst(b); }
        @Override public void hit(Bullet b, float x, float y) { burst(b); b.remove(); }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct); // super → hit() → burst + remove; rồi burst lần nữa (giữ y JS)
            burst(b);
            b.remove();
        }
    }

    // ------------------------------------------------------------------ khởi tạo

    public static void create() {
        muzzleEffect = new Effect(20f, e -> {
            // bài 2: at(x,y,rot,Color) gán e.color chứ KHÔNG gán e.data → JS luôn ra màu mặc định
            Color tColor = e.data instanceof Color ? (Color) e.data : C_PURPLE_MAIN;
            float a = e.rotation;
            ring(e, a, 12f, 18f, 3f, 5f, 15f, 2.2f, tColor);
            ring(e, a, 18f, 26f, 4f, 7f, 22f, 1.8f, C_WHITE);
            ring(e, a, 35f, 20f, 2f, 10f, 28f, 2f, tColor);
            ring(e, a, 55f, 40f, 1f, 7f, 20f, 1.6f, C_WHITE);
        });
        clusterFlashEffect = new Effect(18f, e -> {
            Draw.color(C_WHITE, C_PURPLE_LIGHT, e.fin());
            Lines.stroke(e.fout() * 4f);
            Lines.circle(e.x, e.y, e.finpow() * 28f);
            Draw.color(C_PURPLE_MAIN);
            Lines.stroke(e.fout() * 2f);
            Lines.circle(e.x, e.y, e.finpow() * 16f);
            Draw.reset();
        });
        starDespawnEffect = new Effect(25f, e -> {
            Draw.z(Layer.bullet + 1f);
            Color mainColor = e.color != null ? e.color : C_PURPLE_MAIN;
            draw4PointStar(e.x, e.y, 18f * e.fout(), e.rotation + e.fin() * 120f, mainColor, C_WHITE);
        });

        starBullet = new StarBullet();
        microGround = new MicroBullet(false);
        microAir = new MicroBullet(true);
        subGround = new SubBullet(false);
        subAir = new SubBullet(true);
        clusterGround = new ClusterBullet(false);
        clusterAir = new ClusterBullet(true);
        holeMK1 = new NexBlackHole.BlackHoleBulletType(16f, 70f);
        holeMK2 = new NexBlackHole.BlackHoleBulletType(20f, 90f);
        holeMK2B = new NexBlackHole.BlackHoleBulletType(24f, 110f);
        holeMK1.damage = 100f; holeMK2.damage = 150f; holeMK2B.damage = 200f;
        holeMK1.lifetime = holeMK2.lifetime = holeMK2B.lifetime = 150f;
        mainMK1 = new MainBullet(0);
        mainMK2 = new MainBullet(1);
        mainMK2B = new MainBullet(2);

        block = new ItemTurret("emperfum") {
            @Override public void load() {
                super.load();
                barrel1Region = findTexture("emperfum-barrel-1");
                barrel2Region = findTexture("emperfum-barrel-2");
            }
        };
        block.squareSprite = false;
        block.health = 3625;
        block.size = 3;
        block.reload = 300f;
        block.range = 550f;
        block.configurable = true;
        block.shootEffect = Fx.none;
        block.smokeEffect = Fx.none;
        block.targetAir = true;
        block.targetGround = true;
        block.ammoTypes.put(Items.pyratite, mainMK1); // hjson có ammoTypes: titanium, nạp sau (bài 23)
        block.hasLiquids = true;
        ConsumeCoolant coolant = new ConsumeCoolant(0.2f);
        coolant.booster = false;
        block.consume(coolant);
        block.config(Integer.class, (EmperfumBuild t, Integer v) -> { if (t != null) t.setTier(v); });
        block.buildType = () -> new EmperfumBuild(block);
    }

    public static class EmperfumBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;

        public EmperfumBuild(ItemTurret outer) {
            outer.super();
        }

        int getTier() { return tierState; }

        void setTier(int val) {
            tierState = val;
            if (val == 0) health = 3625;
            if (val == 1) health = 4713;
            if (val == 2) health = 6525;
            maxHealth = health;
        }

        @Override
        protected void findTarget() {
            if (isControlled()) return;
            if (getTier() == 2) target = Units.closestEnemy(team, x, y, range(), u -> u != null && u.isFlying());
            else target = Units.closestEnemy(team, x, y, range(), u -> u != null && !u.isFlying());
        }

        Vec2 getClampedTarget() {
            if (isControlled() && unit != null) return new Vec2(unit.aimX(), unit.aimY());
            float maxRange = range();
            float tx = targetPos != null ? targetPos.x : x;
            float ty = targetPos != null ? targetPos.y : y;
            float dst = Mathf.dst(x, y, tx, ty);
            if (dst <= maxRange) return new Vec2(tx, ty);
            float angle = Angles.angle(x, y, tx, ty);
            return new Vec2(x + Angles.trnsx(angle, maxRange), y + Angles.trnsy(angle, maxRange));
        }

        @Override
        public void draw() {
            super.draw();
            float reload = ((ItemTurret) block).reload;
            float chargeProgress = 1f - (reloadCounter / reload);
            if (Float.isNaN(chargeProgress) || chargeProgress < 0f) chargeProgress = 0f;
            float recoilOffset = 0f; // JS đọc this.recoil (không có trên Build v157) → luôn 0
            float chargeShift = chargeProgress * 5f;
            float rot = rotation;
            float b1x = x + Angles.trnsx(rot + 135f, chargeShift) - Angles.trnsx(rot, recoilOffset);
            float b1y = y + Angles.trnsy(rot + 135f, chargeShift) - Angles.trnsy(rot, recoilOffset);
            float b2x = x + Angles.trnsx(rot - 135f, chargeShift) - Angles.trnsx(rot, recoilOffset);
            float b2y = y + Angles.trnsy(rot - 135f, chargeShift) - Angles.trnsy(rot, recoilOffset);
            Draw.z(Layer.turret + 0.1f);
            if (barrel1Region != null && barrel1Region.found()) Draw.rect(barrel1Region, b1x, b1y, rot - 90f);
            else { Draw.color(C_PURPLE_MAIN); Fill.rect(b1x, b1y, 4f, 12f, rot); }
            if (barrel2Region != null && barrel2Region.found()) Draw.rect(barrel2Region, b2x, b2y, rot - 90f);
            else { Draw.color(C_PURPLE_MAIN); Fill.rect(b2x, b2y, 4f, 12f, rot); }
            Draw.reset();
        }

        // ------------------------------------------------------------- giao diện

        private void upgrade(int target, BaseDialog dialog) {
            CoreBuild core = team.core();
            if (target == 1) {
                if (core != null && core.items.get(Items.surgeAlloy) >= reqMK2.surgeAlloy
                    && core.items.get(Items.plastanium) >= reqMK2.plastanium
                    && core.items.get(Items.copper) >= reqMK2.copper) {
                    NexNet.take(core, Items.surgeAlloy, reqMK2.surgeAlloy);
                    NexNet.take(core, Items.plastanium, reqMK2.plastanium);
                    NexNet.take(core, Items.copper, reqMK2.copper);
                    NexNet.fx(Fx.upgradeCore, x, y);
                } else {
                    Vars.ui.showInfo(isVN() ? "[red]Không đủ tài nguyên cho nhánh MK2![]" : "[red]Not enough resources for MK2![]");
                    return;
                }
            } else {
                if (core != null && core.items.get(Items.thorium) >= reqMK2B.thorium
                    && core.items.get(Items.titanium) >= reqMK2B.titanium
                    && core.items.get(Items.silicon) >= reqMK2B.silicon) {
                    NexNet.take(core, Items.thorium, reqMK2B.thorium);
                    NexNet.take(core, Items.titanium, reqMK2B.titanium);
                    NexNet.take(core, Items.silicon, reqMK2B.silicon);
                    NexNet.fx(Fx.bigShockwave, x, y);
                } else {
                    Vars.ui.showInfo(isVN() ? "[red]Không đủ tài nguyên cho nhánh MK2B![]" : "[red]Not enough resources for MK2B![]");
                    return;
                }
            }
            NexNet.fx(Fx.mineHuge, x, y);
            NexNet.shake(4f, 4f, x, y);
            NexNet.configure(this, Integer.valueOf(target));
            dialog.hide();
            deselect();
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(isVN() ? "Trung tâm nâng cấp pháo Emperfum" : "Emperfum Upgrade Center");
            Table reqTable = new Table();
            reqTable.background(Styles.black6);
            reqTable.margin(10);
            Label reqCell = reqTable.add("").width(320).get();
            reqCell.setWrap(true);
            reqCell.setAlignment(Align.left);

            CoreBuild core = team.core();
            if (core == null) {
                reqCell.setText(isVN() ? "[red]⚠️ KHÔNG THẤY LÕI ĐỘI![]" : "[red]⚠️ NO TEAM CORE FOUND![]");
            } else {
                int cSurge = core.items.get(Items.surgeAlloy);
                int cPla = core.items.get(Items.plastanium);
                int cCop = core.items.get(Items.copper);
                int cTho = core.items.get(Items.thorium);
                int cTit = core.items.get(Items.titanium);
                int cSil = core.items.get(Items.silicon);
                String surCol1 = cSurge >= reqMK2.surgeAlloy ? "[lime]" : "[scarlet]";
                String plaCol1 = cPla >= reqMK2.plastanium ? "[lime]" : "[scarlet]";
                String copCol1 = cCop >= reqMK2.copper ? "[lime]" : "[scarlet]";
                String thoCol2 = cTho >= reqMK2B.thorium ? "[lime]" : "[scarlet]";
                String titCol2 = cTit >= reqMK2B.titanium ? "[lime]" : "[scarlet]";
                String silCol2 = cSil >= reqMK2B.silicon ? "[lime]" : "[scarlet]";
                if (isVN()) {
                reqCell.setText(
                "[gold]📦 KHO TÀI NGUYÊN LÕI CẦN THIẾT:[]\n\n" +
                "[#c084fc]🔹 Nhánh MK2 (Xuyên Phá / Đánh Đất):[]\n" +
                " • Hợp kim Surge: " + surCol1 + cSurge + "[] / " + reqMK2.surgeAlloy + "\n" +
                " • Nhựa Plastanium: " + plaCol1 + cPla + "[] / " + reqMK2.plastanium + "\n" +
                " • Đồng (Copper): " + copCol1 + cCop + "[] / " + reqMK2.copper + "\n\n" +
                "[#e9d5ff]🔸 Nhánh MK2B (Tầm Nhiệt / Phòng Không):[]\n" +
                " • Thorium: " + thoCol2 + cTho + "[] / " + reqMK2B.thorium + "\n" +
                " • Titan: " + titCol2 + cTit + "[] / " + reqMK2B.titanium + "\n" +
                " • Silicon: " + silCol2 + cSil + "[] / " + reqMK2B.silicon
                );
                } else {
                reqCell.setText(
                "[gold]📦 REQUIRED CORE RESOURCES:[]\n\n" +
                "[#c084fc]🔹 MK2 Branch (Piercing / Anti-Ground):[]\n" +
                " • Surge Alloy: " + surCol1 + cSurge + "[] / " + reqMK2.surgeAlloy + "\n" +
                " • Plastanium: " + plaCol1 + cPla + "[] / " + reqMK2.plastanium + "\n" +
                " • Copper: " + copCol1 + cCop + "[] / " + reqMK2.copper + "\n\n" +
                "[#e9d5ff]🔸 MK2B Branch (Homing / Anti-Air):[]\n" +
                " • Thorium: " + thoCol2 + cTho + "[] / " + reqMK2B.thorium + "\n" +
                " • Titanium: " + titCol2 + cTit + "[] / " + reqMK2B.titanium + "\n" +
                " • Silicon: " + silCol2 + cSil + "[] / " + reqMK2B.silicon
                );
                }
            }
            dialog.cont.add(reqTable).width(340).padBottom(10).row();

            Table branchesTable = new Table();

            Table b1 = new Table();
            b1.background(Styles.black8);
            b1.margin(10);
            b1.add(isVN() ? "[#c084fc]⚡ [BOLD]CẤU HÌNH MK2 - GIA TỐC XUYÊN PHÁ[] ⚡" : "[#c084fc]⚡ [BOLD]MK2 CONFIG - PIERCING ACCELERATION[] ⚡").center().row();
            b1.add().height(6).row();
                String b1Desc = isVN() ?
                "[lightgray]Tối ưu hóa rãnh nòng từ tính tím, gia tăng hỏa lực càn quét mặt đất:\n" +
                "• [white]Máu tháp pháo: [green]4,713 HP[] [lime](+30%)[]\n" +
                "• [white]Tầm bắn: [orange]607.9 pixel[]\n" +
                "• [white]Sát thương: [yellow]500 thô + 750 nổ diện rộng[]\n" +
                "• [white]Đặc tính: [gold]Xuyên qua 15 mục tiêu[], phân tách đạn chùm 2 tầng càn quét công trình/kẻ địch."
                :
                "[lightgray]Optimized purple magnetic barrel grooves, increasing ground sweeping firepower:\n" +
                "• [white]Turret Health: [green]4,713 HP[] [lime](+30%)[]\n" +
                "• [white]Range: [orange]607.9 pixels[]\n" +
                "• [white]Damage: [yellow]500 raw + 750 splash[]\n" +
                "• [white]Features: [gold]Pierces up to 15 targets[], splits into 2-tier cluster bullets.";
            Label b1D = b1.add(b1Desc).width(300).get();
            b1D.setWrap(true);
            b1D.setAlignment(Align.left);
            b1.row();
            b1.add().height(8).row();
            b1.button(isVN() ? "[#c084fc]KÍCH HOẠT MK2[]" : "[#c084fc]ACTIVATE MK2[]", () -> upgrade(1, dialog)).size(200, 40).center();

            Table b2 = new Table();
            b2.background(Styles.black8);
            b2.margin(10);
            b2.add(isVN() ? "[#e9d5ff]🔥 [BOLD]CẤU HÌNH MK2B - XUNG KÍCH TẦM NHIỆT[] 🔥" : "[#e9d5ff]🔥 [BOLD]MK2B CONFIG - HOMING IMPULSE[] 🔥").center().row();
            b2.add().height(6).row();
                String b2Desc = isVN() ?
                "[lightgray]Chuyển đổi sang hệ thống phòng không tầm xa chuyên dụng:\n" +
                "• [white]Máu tháp pháo: [green]6,525 HP[] [lime](+80%)[]\n" +
                "• [white]Tầm bắn: [orange]550 pixel[]\n" +
                "• [white]Sát thương: [red]1,175 thô + 1,762 nổ diện rộng[]\n" +
                "• [white]Đặc tính: [#e9d5ff]Tự động bẻ lái khóa mục tiêu bay[], đạn nổ tỏa ra mảnh đạn ngôi sao tím truy đuổi."
                :
                "[lightgray]Converted into a specialized long-range anti-air system:\n" +
                "• [white]Turret Health: [green]6,525 HP[] [lime](+80%)[]\n" +
                "• [white]Range: [orange]550 pixels[]\n" +
                "• [white]Damage: [red]1,175 raw + 1,762 splash[]\n" +
                "• [white]Features: [#e9d5ff]Homing onto flying targets[], bursts into purple star shrapnel.";
            Label b2D = b2.add(b2Desc).width(300).get();
            b2D.setWrap(true);
            b2D.setAlignment(Align.left);
            b2.row();
            b2.add().height(8).row();
            b2.button(isVN() ? "[#e9d5ff]KÍCH HOẠT MK2B[]" : "[#e9d5ff]ACTIVATE MK2B[]", () -> upgrade(2, dialog)).size(200, 40).center();

            branchesTable.add(b1).width(320).padBottom(10).row();
            branchesTable.add(b2).width(320).row();
            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).width(340).maxHeight(320);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = isVN() ? " Thông số pháo Emperfum: " : " Emperfum Turret Stats: ";
            String descStr = "";
            int currentTier = getTier();
            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = isVN() ?
                "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1 - CƠ BẢN) ⚡[]\n\n" +
                "• [lightgray]Máu pháo:[] [green]3,625 HP[]\n" +
                "• [lightgray]Tầm bắn:[] [orange]550 pixel[]\n" +
                "• [lightgray]Mục tiêu:[] Đất & Khai hỏa Pyro\n" +
                "• [lightgray]Sát thương:[] [yellow]500 thô[] + đạn chùm phân tách\n" +
                "• [lightgray]Khả năng xuyên:[] [white]15 mục tiêu[]\n\n" +
                "[#c084fc]💡 Mô tả: Pháo càn quét diện rộng giai đoạn đầu. Bắn đạn chính tích tụ vòng năng lượng oval tím-trắng, khi chạm mục tiêu sẽ giải phóng đạn ngôi sao tím bộc phá.[]"
                :
                "[gold]⚡ BASIC STATS (MK1 - BASE) ⚡[]\n\n" +
                "• [lightgray]Health:[] [green]3,625 HP[]\n" +
                "• [lightgray]Range:[] [orange]550 pixels[]\n" +
                "• [lightgray]Target:[] Ground & Pyratite Ammo\n" +
                "• [lightgray]Damage:[] [yellow]500 raw[] + cluster splits\n" +
                "• [lightgray]Piercing:[] [white]15 targets[]\n\n" +
                "[#c084fc]💡 Description: Early-game area sweep turret. Fires main shells charging purple-white oval energy rings, releasing star shrapnel on impact.[]";
            } else if (currentTier == 1) {
                title += "[#c084fc](MK2)[]";
                descStr = isVN() ?
                "[#c084fc]⚡ THÔNG SỐ CẤU HÌNH (MK2 - XUYÊN PHÁ) ⚡[]\n\n" +
                "• [lightgray]Máu pháo:[] [green]4,713 HP [lime](+30%)[]\n" +
                "• [lightgray]Tầm bắn:[] [orange]607.9 pixel[]\n" +
                "• [lightgray]Mục tiêu:[] Chuyên Đánh Đất\n" +
                "• [lightgray]Sát thương:[] [yellow]500 thô + 750 nổ diện rộng[]\n" +
                "• [lightgray]Khả năng xuyên:[] [yellow]15 mục tiêu[]\n\n" +
                "[lime]💡 Mô tả: Tăng cường kết cấu nòng từ tính tím thẫm. Đạn chính bay nhanh hơn, xuyên qua toàn bộ đội hình địch và kích hoạt chuỗi nổ bộc phá liên hoàn.[]"
                :
                "[#c084fc]⚡ CONFIG STATS (MK2 - PIERCING) ⚡[]\n\n" +
                "• [lightgray]Health:[] [green]4,713 HP [lime](+30%)[]\n" +
                "• [lightgray]Range:[] [orange]607.9 pixels[]\n" +
                "• [lightgray]Target:[] Ground specialized\n" +
                "• [lightgray]Damage:[] [yellow]500 raw + 750 splash[]\n" +
                "• [lightgray]Piercing:[] [yellow]15 targets[]\n\n" +
                "[lime]💡 Description: Reinforced magnetic barrel structure. Main shots travel faster, pierce through enemy lines, and trigger cluster explosions.[]";
            } else if (currentTier == 2) {
                title += "[#e9d5ff](MK2B)[]";
                descStr = isVN() ?
                "[#e9d5ff]⚡ THÔNG SỐ CẤU HÌNH (MK2B - TRUY ĐUỔI TẦM NHIỆT) ⚡[]\n\n" +
                "• [lightgray]Máu pháo:[] [green]6,525 HP [lime](+80%)[]\n" +
                "• [lightgray]Tầm bắn:[] [orange]550 pixel[]\n" +
                "• [lightgray]Mục tiêu:[] Chuyên Phòng Không (Bay)\n" +
                "• [lightgray]Sát thương:[] [red]1,175 thô + 1,762 nổ diện rộng[]\n" +
                "• [lightgray]Khả năng bẻ lái:[] [#e9d5ff]Truy đuổi tầm nhiệt 200px[]\n\n" +
                "[#c084fc]🔥 Mô tả: Chuyển đổi toàn bộ mạch năng lượng sang sắc tím xung kích. Tự động bẻ lái đuổi theo các đơn vị không quân địch, phát nổ thành mảnh đạn ngôi sao 4 cánh tím-trắng.[]"
                :
                "[#e9d5ff]⚡ CONFIG STATS (MK2B - HOMING IMPULSE) ⚡[]\n\n" +
                "• [lightgray]Health:[] [green]6,525 HP [lime](+80%)[]\n" +
                "• [lightgray]Range:[] [orange]550 pixels[]\n" +
                "• [lightgray]Target:[] Anti-Air specialized\n" +
                "• [lightgray]Damage:[] [red]1,175 raw + 1,762 splash[]\n" +
                "• [lightgray]Homing:[] [#e9d5ff]200px homing radius[]\n\n" +
                "[#c084fc]🔥 Description: Converts energy grid to impulse shockwaves. Automatically home in on aerial targets, bursting into 4-point purple-white flare star fragments.[]";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            infoTable.background(Styles.black6);
            infoTable.margin(12);
            Label cell = infoTable.add(descStr).width(320).get();
            cell.setWrap(true);
            cell.setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).width(340).maxHeight(360);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openUpgradeDialog)
                    .size(50, 40).tooltip(isVN() ? "Nâng cấp hệ thống Emperfum" : "Upgrade Emperfum system");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(isVN()
                    ? "[scarlet]HỆ THỐNG EMPERFUM ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]"
                    : "[scarlet]EMPERFUM HAS REACHED MAX EVOLUTION TIER![]"))
                    .size(50, 40).tooltip(isVN() ? "Đã đạt cấp tối đa" : "Max tier reached");
            }
            table.button(Icon.info, Styles.cleari, 40, this::openInfoDialog)
                .size(50, 40).tooltip(isVN() ? "Xem thông số chi tiết hệ thống" : "View system details");
        }

        @Override
        public Object config() {
            return Integer.valueOf(getTier());
        }

        @Override
        public void shoot(BulletType type) {
            int tier = getTier();
            Vec2 tp = getClampedTarget();
            MainBullet bulletToShoot = mainMK1;
            Color muzzleColor = C_PURPLE_LIGHT;
            if (tier == 1) { bulletToShoot = mainMK2; muzzleColor = C_PURPLE_MAIN; }
            if (tier == 2) { bulletToShoot = mainMK2B; muzzleColor = C_PURPLE_DARK; }

            float spawnX = x + Angles.trnsx(rotation, 10f), spawnY = y + Angles.trnsy(rotation, 10f);
            muzzleEffect.at(spawnX, spawnY, rotation, muzzleColor);

            float shootAngle = (isControlled() && unit != null) ? Angles.angle(spawnX, spawnY, tp.x, tp.y) : rotation;
            Bullet b = bulletToShoot.create(this, team, spawnX, spawnY, shootAngle);
            if (b != null) {
                b.data = tp;
                float dist = Mathf.dst(spawnX, spawnY, tp.x, tp.y);
                b.lifetime = (dist / bulletToShoot.speed) + 2f;
                useAmmo();
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
        }
    }
}
