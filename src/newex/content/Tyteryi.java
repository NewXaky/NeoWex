package newex.content;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ tyteryi.js. Pháo kiếm TyterYi (MK1/MK2/MK3 + chế độ Abeta pha lê).
 * Block do Java tạo (`new ItemTurret`) → tyteryi.hjson không có `type:` cấp cao nhất (bài 29a).
 */
public final class Tyteryi {
    private Tyteryi() {}

    static final class Req { final int silicon, titanium, thorium, plastanium;
        Req(int s, int t, int th, int p) { silicon = s; titanium = t; thorium = th; plastanium = p; } }
    static final Req reqMK2 = new Req(150, 100, 80, 0);
    static final Req reqMK3 = new Req(250, 180, 120, 60);

    static final Color SWORD_LIGHT = Color.valueOf("#ffffff"), SWORD_MAIN = Color.valueOf("#ffe066"),
        SWORD_DARK = Color.valueOf("#ffd700"), SWORD_DEEP = Color.valueOf("#ff8800");
    static final Color CRYSTAL_LIGHT = Color.valueOf("#ffffff"), CRYSTAL_MAIN = Color.valueOf("#e0f7ff"),
        CRYSTAL_DARK = Color.valueOf("#80deea"), CRYSTAL_DEEP = Color.valueOf("#00bcd4");

    public static ItemTurret block;

    static Effect swordMuzzleFx, crystalMuzzleFx, swordLaunchFx, swordHitFx, swordExplosionFx, skyLaserFx, jigsawAssembleFx, starFlashFx;
    static FlatSword microSword1, microSword2, microCrystal1, microCrystal2, miniSword, rainSword, rainCrystal;
    static SwordBullet swordMK1, swordMK2, swordMK3, crystalMK1, crystalMK2, crystalMK3;

    // ------------------------------------------------------------------ hiệu ứng

    private static void createEffects() {
        swordMuzzleFx = new Effect(15f, e -> {
            Draw.color(SWORD_MAIN, SWORD_LIGHT, e.fin());
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 7f * e.finpow());
        });
        crystalMuzzleFx = new Effect(15f, e -> {
            Draw.color(CRYSTAL_MAIN, CRYSTAL_LIGHT, e.fin());
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 8f * e.finpow());
        });
        swordLaunchFx = new Effect(18f, e -> {
            Draw.color(SWORD_LIGHT, SWORD_MAIN, e.fin());
            Lines.stroke(1.2f * e.fout());
            Lines.circle(e.x, e.y, 6f * e.finpow());
            Angles.randLenVectors(e.id, 4, 12f * e.finpow(), (x, y) -> Fill.circle(e.x + x, e.y + y, 1f * e.fout()));
        });
        swordHitFx = new Effect(20f, e -> {
            Draw.color(SWORD_LIGHT, SWORD_MAIN, e.fin());
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 10f * e.finpow());
            Angles.randLenVectors(e.id, 5, 14f * e.finpow(), (x, y) -> {
                Draw.color(SWORD_MAIN);
                Fill.circle(e.x + x, e.y + y, 1.4f * e.fout());
            });
        });
        swordExplosionFx = new Effect(25f, e -> {
            float size = 14f * e.finpow();
            float spinRot = e.rotation + e.fin() * 60f;
            Draw.color(SWORD_DEEP);
            Draw.alpha(0.8f * e.fout());
            Drawf.tri(e.x, e.y, 4f * e.fout(), size * 1.3f, spinRot);
            Drawf.tri(e.x, e.y, 4f * e.fout(), size * 1.3f, spinRot + 180f);
            Draw.color(SWORD_MAIN);
            Draw.alpha(1f * e.fout());
            Drawf.tri(e.x, e.y, 3f * e.fout(), size, spinRot + 90f);
            Drawf.tri(e.x, e.y, 3f * e.fout(), size, spinRot + 270f);
            Draw.reset();
        });
        skyLaserFx = new Effect(40f, e -> {
            Draw.color(Color.white, CRYSTAL_LIGHT, e.fin());
            Lines.stroke(8f * e.fout());
            Lines.line(e.x, e.y + 400f, e.x, e.y);
            Draw.color(CRYSTAL_MAIN);
            Lines.stroke(3f * e.fout());
            Lines.line(e.x, e.y + 400f, e.x, e.y);
        });
        jigsawAssembleFx = new Effect(45f, e -> {
            Draw.color(CRYSTAL_LIGHT, SWORD_MAIN, e.fin());
            float progress = e.fin();
            for (int side = 0; side < 2; side++) {
                for (int i = 0; i < 10; i++) {
                    // JS: vòng 1 seed = id + i*13, vòng 2 seed = id + i*17 + 200
                    int seed = side == 0 ? e.id + i * 13 : e.id + i * 17 + 200;
                    float yOffset = Mathf.randomSeed(seed, -45f, 45f);
                    float extra = Mathf.randomSeed(seed + 1, 0f, 50f);
                    float startX = side == 0 ? e.x - 120f - extra : e.x + 120f + extra;
                    float cx = Mathf.lerp(startX, e.x, progress);
                    float cy = e.y + yOffset * (1f - progress);
                    Drawf.tri(cx, cy, 5f * e.fout(), 10f * e.fout(), side == 0 ? 45f : 225f);
                }
            }
        });
        starFlashFx = new Effect(45f, e -> {
            Draw.color(Color.white, CRYSTAL_LIGHT, e.fin());
            float size = e.finpow() * 130f;
            float rot = e.fin() * 50f;
            for (int i = 0; i < 4; i++) {
                Drawf.tri(e.x, e.y, 18f * e.fout(), size, i * 90f + rot);
                Drawf.tri(e.x, e.y, 18f * e.fout(), size, i * 90f + 45f + rot);
            }
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, e.fout() * 40f);
        });
    }

    static void spawn16Smoke(Building build) {
        for (int i = 0; i < 16; i++) {
            float angle = i * 22.5f;
            for (int d = 16; d <= 72; d += 18) {
                float sx = build.x + Angles.trnsx(angle, d), sy = build.y + Angles.trnsy(angle, d);
                Fx.smoke.at(sx, sy, angle);
                Fx.smoke.at(sx + Mathf.range(10f), sy + Mathf.range(10f), angle + Mathf.range(30f));
            }
        }
    }

    static void drawSwordShape(float x, float y, float rot, float baseSize, float progress, boolean isCrystal) {
        float sz = baseSize * Mathf.curve(progress, 0f, 1f);
        if (sz <= 0.1f) return;
        float alphaFactor = Mathf.clamp(progress);
        Color deep = isCrystal ? CRYSTAL_DEEP : SWORD_DEEP;
        Color main = isCrystal ? CRYSTAL_MAIN : SWORD_MAIN;
        Color light = isCrystal ? CRYSTAL_LIGHT : SWORD_LIGHT;
        Draw.color(deep);
        Draw.alpha(0.7f * alphaFactor);
        Drawf.tri(x, y, sz * 0.4f, sz * 2.7f, rot);
        Drawf.tri(x, y, sz * 0.3f, sz * 0.7f, rot + 90f);
        Drawf.tri(x, y, sz * 0.3f, sz * 0.7f, rot + 270f);
        Drawf.tri(x, y, sz * 0.22f, sz * 0.5f, rot + 180f);
        Draw.color(main);
        Draw.alpha(0.95f * alphaFactor);
        Drawf.tri(x, y, sz * 0.26f, sz * 2.2f, rot);
        Drawf.tri(x, y, sz * 0.2f, sz * 0.55f, rot + 90f);
        Drawf.tri(x, y, sz * 0.2f, sz * 0.55f, rot + 270f);
        Drawf.tri(x, y, sz * 0.15f, sz * 0.35f, rot + 180f);
        Draw.color(light);
        Draw.alpha(1f * alphaFactor);
        Drawf.tri(x, y, sz * 0.09f, sz * 1.2f, rot);
        Fill.circle(x, y, 1.8f * alphaFactor);
        Draw.reset();
    }

    // ------------------------------------------------------------------ đạn

    /** Kiếm nhỏ / kiếm mưa / kiếm mini: chỉ vẽ kiếm; wave 1 = sin(0.45)*2.5, 2 = cos(0.35)*3.2 (đạn micro). */
    static final class FlatSword extends BasicBulletType {
        final float drawSize, drawClamp;
        final boolean crystal;
        final int wave;
        FlatSword(float speed, float damage, float width, float height, float homingPower, float homingRange,
                  int pierceCap, boolean crystal, float drawSize, float drawClamp, int wave) {
            this.speed = speed; this.damage = damage; this.width = width; this.height = height;
            shrinkX = 0f; shrinkY = 0f;
            this.homingPower = homingPower;
            if (homingRange > 0f) this.homingRange = homingRange;
            pierce = true; pierceBuilding = true; this.pierceCap = pierceCap;
            frontColor = crystal ? CRYSTAL_LIGHT : SWORD_LIGHT;
            backColor = crystal ? CRYSTAL_MAIN : SWORD_MAIN;
            shootEffect = Fx.none; hitEffect = swordHitFx; despawnEffect = Fx.none;
            this.crystal = crystal; this.drawSize = drawSize; this.drawClamp = drawClamp; this.wave = wave;
        }
        @Override public void update(Bullet b) {
            super.update(b);
            if (wave == 0) return;
            float w = wave == 1 ? Mathf.sin(b.time * 0.45f) * 2.5f : Mathf.cos(b.time * 0.35f) * 3.2f;
            float perp = b.rotation() + 90f;
            b.set(b.x + Angles.trnsx(perp, w), b.y + Angles.trnsy(perp, w));
        }
        @Override public void draw(Bullet b) {
            drawSwordShape(b.x, b.y, b.rotation(), drawSize, Mathf.clamp(b.time / drawClamp), crystal);
        }
    }

    static void triggerSwordRain(Bullet b, Posc entity) {
        if (entity == null || b.owner == null) return;
        int tier = b.owner instanceof TyterYiBuild ? ((TyterYiBuild) b.owner).getTier() : 0;
        if (tier < 1) return;
        boolean isAbeta = b.owner instanceof TyterYiBuild && ((TyterYiBuild) b.owner).isAbeta;
        float chance = isAbeta ? 0.3f : 0.1f;
        if (Mathf.chance(chance)) {
            for (int i = 0; i < 6; i++) {
                float ox = Mathf.range(45f), oy = Mathf.range(45f);
                float sx = entity.getX() + ox + Mathf.range(10f);
                float sy = entity.getY() + 160f + Mathf.range(20f);
                float targetX = entity.getX() + ox, targetY = entity.getY() + oy;
                float angle = Angles.angle(sx, sy, targetX, targetY);
                BulletType type = isAbeta ? rainCrystal : rainSword;
                Bullet rainB = type.create(b.owner, b.team, sx, sy, angle);
                if (rainB != null) rainB.lifetime = 25f;
                Fx.itemTransfer.at(targetX, targetY, 6f, SWORD_MAIN);
            }
        }
    }

    /** Kiếm chính (MK1–MK3, thường & pha lê). hpFrac = % máu tối đa gây thêm; abetaHp: MK3 thường dùng 8% khi Abeta. */
    static final class SwordBullet extends BasicBulletType {
        final boolean crystal, abetaHp;
        final float hpFrac, drawSize;
        SwordBullet(boolean crystal, float speed, float damage, float width, float height, float homingPower, float homingRange,
                    int pierceCap, Color back, float splashDamage, float splashRadius, StatusEffect st, float stDur,
                    float drawSize, float hpFrac, boolean abetaHp) {
            this.crystal = crystal; this.abetaHp = abetaHp; this.hpFrac = hpFrac; this.drawSize = drawSize;
            this.speed = speed; this.damage = damage; this.width = width; this.height = height;
            shrinkX = 0f; shrinkY = 0f;
            this.homingPower = homingPower; this.homingRange = homingRange;
            pierce = true; pierceBuilding = true; this.pierceCap = pierceCap;
            frontColor = crystal ? CRYSTAL_LIGHT : SWORD_LIGHT; backColor = back;
            this.splashDamage = splashDamage; splashDamageRadius = splashRadius;
            shootEffect = crystal ? crystalMuzzleFx : swordMuzzleFx; hitEffect = swordHitFx; despawnEffect = swordExplosionFx;
            status = st; statusDuration = stDur;
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            if (b.owner instanceof TyterYiBuild) ((TyterYiBuild) b.owner).addCharge(2f);
            if (entity instanceof Healthc) {
                Healthc h = (Healthc) entity;
                float maxHp = h.maxHealth();
                float frac = hpFrac;
                if (abetaHp) frac = (b.owner instanceof TyterYiBuild && ((TyterYiBuild) b.owner).isAbeta) ? 0.08f : 0.03f;
                float extraDmg = (b.vel.len() < speed * 0.7f) ? (maxHp * 0.05f) : 0f;
                h.damage(maxHp * frac + extraDmg);
            }
            if (entity instanceof Unit && ((((Unit) entity).type != null && "akenewm-inventer".equals(((Unit) entity).type.name))
                || entity instanceof LeveledUnit)) {
                ((Unit) entity).damage(b.damage * 3f);
                Fx.itemTransfer.at(((Unit) entity).x, ((Unit) entity).y, 10f, crystal ? CRYSTAL_MAIN : SWORD_MAIN);
            }
            if (crystal && entity instanceof Posc) {
                Posc p = (Posc) entity;
                float miniDmg = b.damage * 0.6f;
                for (int i = 0; i < 3; i++) {
                    float angleOffset = i * 120f + Mathf.random(30f);
                    float sx = p.getX() + Angles.trnsx(angleOffset, 32f), sy = p.getY() + Angles.trnsy(angleOffset, 32f);
                    float shootAngle = Angles.angle(sx, sy, p.getX(), p.getY());
                    Bullet miniB = miniSword.create(b.owner, b.team, sx, sy, shootAngle);
                    if (miniB != null) { miniB.damage = miniDmg; miniB.lifetime = 45f; }
                }
            }
            triggerSwordRain(b, entity instanceof Posc ? (Posc) entity : null);
        }
        @Override public void draw(Bullet b) {
            drawSwordShape(b.x, b.y, b.rotation(), drawSize, Mathf.clamp(b.time / 10f), crystal);
        }
    }

    // ------------------------------------------------------------------ khởi tạo

    public static void create() {
        createEffects();
        microSword1 = new FlatSword(15.4f, 140f * 3f, 2f, 7f, 0.15f, 140f, 5, false, 4.2f, 5f, 1);
        microSword2 = new FlatSword(15.4f, 140f * 3f, 2f, 7f, 0.18f, 150f, 5, false, 4.2f, 5f, 2);
        microCrystal1 = new FlatSword(15.4f, 140f * 4.8f * 3f, 2f, 7f, 0.18f, 150f, 5, true, 4.2f, 5f, 1);
        microCrystal2 = new FlatSword(15.4f, 140f * 4.8f * 3f, 2f, 7f, 0.2f, 160f, 5, true, 4.2f, 5f, 2);
        miniSword = new FlatSword(4.2f, 126f * 3f, 2.5f, 10f, 0.1f, 150f, 5, true, 6.3f, 8f, 0);
        rainSword = new FlatSword(7f, 100f * 3f, 2.5f, 10f, 0f, 0f, 3, false, 6.3f, 5f, 0);
        rainCrystal = new FlatSword(7f, 155f * 3f, 2.5f, 10f, 0f, 0f, 3, true, 6.3f, 5f, 0);

        swordMK1 = new SwordBullet(false, 7.7f, 210f * 3f, 3.2f, 12f, 0.05f, 150f, 20, SWORD_MAIN, 40f * 3f, 35f, StatusEffects.melting, 90f, 9.8f, 0.03f, false);
        swordMK2 = new SwordBullet(false, 11.55f, 273f * 3f, 3.5f, 14f, 0.08f, 200f, 30, SWORD_DARK, 60f * 3f, 45f, StatusEffects.melting, 130f, 11.2f, 0.03f, false);
        swordMK3 = new SwordBullet(false, 16.94f, 462f * 3f, 3.8f, 16f, 0.1f, 250f, 40, SWORD_DARK, 80f * 3f, 55f, StatusEffects.melting, 160f, 11.9f, 0.03f, true);
        crystalMK1 = new SwordBullet(true, 8.4f, 210f * 4.8f * 3f, 3.5f, 14f, 0.1f, 220f, 35, CRYSTAL_MAIN, 80f * 3f, 50f, StatusEffects.freezing, 150f, 10.5f, 0.08f, false);
        crystalMK2 = new SwordBullet(true, 11.55f, 273f * 4.8f * 3f, 3.6f, 15f, 0.12f, 230f, 38, CRYSTAL_MAIN, 90f * 3f, 52f, StatusEffects.freezing, 160f, 11.2f, 0.08f, false);
        crystalMK3 = new SwordBullet(true, 16.94f, 462f * 4.8f * 3f, 3.8f, 16f, 0.15f, 260f, 45, CRYSTAL_MAIN, 110f * 3f, 60f, StatusEffects.freezing, 180f, 11.9f, 0.08f, false);

        block = new ItemTurret("tyteryi");
        block.squareSprite = false;
        block.shootSound = mindustry.gen.Sounds.none;
        block.health = 3000;
        block.size = 3;
        block.reload = 34f;
        block.inaccuracy = 1f;
        block.range = 1920f;
        block.configurable = true;
        block.ammoTypes.put(Items.silicon, swordMK1); // hjson `ammo:` nạp SAU và thay cả map (bài 76)
        block.config(Integer.class, (TyterYiBuild t, Integer v) -> { if (t != null) t.setTier(v); });
        block.buildType = () -> new TyterYiBuild(block);
    }

    public static class TyterYiBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;
        float limitCheck = 0f;
        final float[] swords = {1f, 1f, 1f, 1f, 1f, 1f};
        boolean isReloading = false;
        float lastShotTick = -999f;
        float internalCharge = 0f;
        boolean isAbeta = false;
        boolean isTransforming = false;
        float transitionTimer = 0f;

        public TyterYiBuild(ItemTurret outer) { outer.super(); }

        int getTier() { return tierState; }

        void setTier(int val) {
            tierState = val;
            if (val == 0) health = 3000f;
            if (val == 1) health = 5400f;
            if (val == 2) health = 6600f;
            maxHealth = health;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 1) return 2560.2f;
            if (tier == 2) return 4224f;
            return 1920f;
        }

        int getMaxSwords() { return isAbeta ? 6 : 4; }

        void addCharge(float amount) {
            if (!isAbeta && !isTransforming) {
                internalCharge += amount;
                if (internalCharge >= 100f) {
                    internalCharge = 100f;
                    isTransforming = true;
                    transitionTimer = 120f;
                    spawn16Smoke(this);
                    skyLaserFx.at(x, y);
                    jigsawAssembleFx.at(x, y);
                }
            }
        }

        @Override
        public void draw() {
            super.draw();
            float chargeRatio = Mathf.clamp(internalCharge / 100f);
            float ringRadius = 14f;
            Draw.color(Color.darkGray);
            Lines.stroke(1.5f);
            Lines.circle(x, y, ringRadius);
            Draw.color(isAbeta ? CRYSTAL_MAIN : SWORD_MAIN);
            Lines.stroke(2.5f);
            if (chargeRatio > 0f) {
                int segments = 36;
                float totalAngle = chargeRatio * 360f;
                int activeSegs = Math.max(1, Mathf.floor(segments * chargeRatio));
                for (int j = 0; j < activeSegs; j++) {
                    float a1 = ((float) j / segments) * 360f;
                    float a2 = ((float) (j + 1) / segments) * 360f;
                    if (a2 > totalAngle) a2 = totalAngle;
                    Lines.line(x + Angles.trnsx(a1, ringRadius), y + Angles.trnsy(a1, ringRadius),
                        x + Angles.trnsx(a2, ringRadius), y + Angles.trnsy(a2, ringRadius));
                }
            }
            String percentStr = isAbeta ? "ABETA" : (isTransforming ? "TRANS..." : Mathf.floor(internalCharge) + "%");
            Fonts.outline.getData().setScale(0.38f);
            Fonts.outline.draw(percentStr, x, y - 18f, Align.center);
            Fonts.outline.getData().setScale(1f);

            int maxS = getMaxSwords();
            float backAngle = rotation + 180f;
            for (int i = 0; i < maxS; i++) {
                float prog = swords[i];
                if (prog <= 0.01f) continue;
                float offsetDist = 11f + (i * 3.5f);
                float sideOffset = (maxS == 6) ? ((i - 2.5f) * 4.5f) : ((i - 1.5f) * 6f);
                float fx = 0f, fy = 0f;
                if (prog >= 1f) {
                    fx = Mathf.absin(Time.time * 2.5f + i * 25f, 15f, 1.5f);
                    fy = Mathf.absin(Time.time * 3f + i * 35f, 15f, 1.5f);
                }
                float targetX = x + Angles.trnsx(backAngle, offsetDist + fx) + Angles.trnsx(backAngle + 90f, sideOffset + fy);
                float targetY = y + Angles.trnsy(backAngle, offsetDist + fx) + Angles.trnsy(backAngle + 90f, sideOffset + fy);
                float sideAngle = (i % 2 == 0) ? (backAngle + 90f) : (backAngle - 90f);
                float startX = x + Angles.trnsx(sideAngle, 45f), startY = y + Angles.trnsy(sideAngle, 45f);
                float moveProg = Mathf.curve(prog, 0f, 1f);
                drawSwordShape(Mathf.lerp(startX, targetX, moveProg), Mathf.lerp(startY, targetY, moveProg), rotation, 7.7f, prog, isAbeta);
            }
            Draw.reset();
        }

        @Override
        public void updateTile() {
            limitCheck += Time.delta;
            if (limitCheck >= 15f) {
                limitCheck = 0f;
                Seq<Building> lim = PerfCore.teamBlocks(team, block);
                int count = lim != null ? lim.size : 0;
                Building firstBuild = (lim != null && count > 0) ? lim.first() : null;
                if (count > 10 && this != firstBuild) {
                    Call.sendMessage("[red]CẢNH BÁO QUÁ TẢI MẠNG LƯỚI: Giới hạn tối đa 10 đơn vị TyterYi trên cùng hệ thống![]");
                    kill();
                    return;
                }
            }

            if (isTransforming) {
                transitionTimer -= Time.delta;
                if (Mathf.chance(0.5)) spawn16Smoke(this);
                if (transitionTimer <= 0f) {
                    isTransforming = false;
                    isAbeta = true;
                    spawn16Smoke(this);
                    skyLaserFx.at(x, y);
                    jigsawAssembleFx.at(x, y);
                    starFlashFx.at(x, y);
                }
            }

            int maxS = getMaxSwords();
            if (isAbeta) {
                internalCharge -= 0.05f * Time.delta;
                if (internalCharge <= 0f) {
                    internalCharge = 0f;
                    isAbeta = false;
                    spawn16Smoke(this);
                    skyLaserFx.at(x, y);
                    jigsawAssembleFx.at(x, y);
                    starFlashFx.at(x, y);
                }
            }

            boolean allEmpty = true;
            for (int i = 0; i < maxS; i++) if (swords[i] > 0f) { allEmpty = false; break; }
            if (allEmpty) isReloading = true;

            if (isReloading) {
                int targetIdx = -1;
                for (int i = 0; i < maxS; i++) if (swords[i] < 1f) { targetIdx = i; break; }
                if (targetIdx != -1) {
                    float baseRecharge = (getTier() == 2) ? (Time.delta / 22f) : ((getTier() == 1) ? (Time.delta / 35f) : (Time.delta / 45f));
                    swords[targetIdx] += isAbeta ? (baseRecharge * 1.5f) : baseRecharge;
                    if (swords[targetIdx] >= 1f) swords[targetIdx] = 1f;
                } else {
                    isReloading = false;
                }
            }

            // JS còn gán this.inaccuracy (Build v157 không có trường này → vô tác dụng, bài 62) nên bỏ.
            ItemTurret it = (ItemTurret) block;
            it.reload = getTier() == 2 ? 14f : 34f;

            super.updateTile();
        }

        @Override
        public void shoot(BulletType type) {
            if (isTransforming) return;
            float currentTick = Time.time;
            int tier = getTier();
            int requiredCooldown = isAbeta ? 14 : (tier == 2 ? 17 : 26);
            if (currentTick - lastShotTick < requiredCooldown) return;
            if (isReloading) return;

            int maxS = getMaxSwords();
            int firedIndex = -1;
            for (int i = 0; i < maxS; i++) if (swords[i] >= 1f) { firedIndex = i; break; }
            if (firedIndex == -1) return;

            lastShotTick = currentTick;
            swords[firedIndex] = 0f;

            SwordBullet bulletType;
            if (isAbeta) bulletType = tier == 2 ? crystalMK3 : (tier == 1 ? crystalMK2 : crystalMK1);
            else bulletType = tier == 2 ? swordMK3 : (tier == 1 ? swordMK2 : swordMK1);

            float exactLifetime = range() / bulletType.speed;
            float backAngle = rotation + 180f, leftAngle = rotation + 90f;
            float spawnX = x + Angles.trnsx(backAngle, 12f) + Angles.trnsx(leftAngle, 9f);
            float spawnY = y + Angles.trnsy(backAngle, 12f) + Angles.trnsy(leftAngle, 9f);

            Bullet b = bulletType.create(this, team, spawnX, spawnY, rotation);
            if (b != null) {
                b.lifetime = exactLifetime;
                if (Mathf.chance(0.5)) b.vel.scl(0.5f);
                swordLaunchFx.at(spawnX, spawnY, rotation);
                if (tier == 2) {
                    for (int k = 1; k <= 4; k++) {
                        float backDist = k * 10f + Mathf.random(5f);
                        int sideSign = (k % 2 == 0) ? 1 : -1;
                        float sideOffset = sideSign * (7f + k * 3f + Mathf.random(4f));
                        float microX = spawnX - Angles.trnsx(rotation, backDist) + Angles.trnsx(rotation + 90f, sideOffset);
                        float microY = spawnY - Angles.trnsy(rotation, backDist) + Angles.trnsy(rotation + 90f, sideOffset);
                        FlatSword mt;
                        if (isAbeta) mt = (k % 2 == 0) ? microCrystal1 : microCrystal2;
                        else mt = (k % 2 == 0) ? microSword1 : microSword2;
                        Bullet mb = mt.create(this, team, microX, microY, rotation);
                        if (mb != null) mb.lifetime = exactLifetime;
                    }
                }
            }
        }

        // ------------------------------------------------------------- giao diện

        private void openUpgradeDialog(int tier) {
            int nextTier = tier + 1;
            BaseDialog dialog = new BaseDialog(nextTier == 1 ? "TRUNG TÂM NÂNG CẤP MK2" : "TRUNG TÂM NÂNG CẤP MK3");
            dialog.cont.add(nextTier == 1 ? "[#ffe066]=== NÂNG CẤP LÊN PROTOCOL MK2 ===[]" : "[#ffaa00]=== NÂNG CẤP LÊN PROTOCOL MK3 ===[]").row();

            String reqSnapshot;
            CoreBuild core0 = team.core();
            if (core0 == null) {
                reqSnapshot = "[red]CRITICAL ERROR: Mất tín hiệu với Kho Lõi Trung Tâm![]";
            } else {
                Req req = nextTier == 1 ? reqMK2 : reqMK3;
                int sil = core0.items.get(Items.silicon), tit = core0.items.get(Items.titanium);
                int tho = core0.items.get(Items.thorium), pla = core0.items.get(Items.plastanium);
                String cSil = sil >= req.silicon ? "[#00ff88]" : "[red]";
                String cTit = tit >= req.titanium ? "[#00ff88]" : "[red]";
                String cTho = tho >= req.thorium ? "[#00ff88]" : "[red]";
                String cPla = nextTier == 2 ? (pla >= req.plastanium ? "[#ffe066]" : "[red]") : "";
                String resStr = "[#ffd700]👑 TÀI NGUYÊN KHO TRUNG TÂM:[]\n" +
                " • Si: " + cSil + sil + "[]/" + req.silicon + "\n" +
                " • Ti: " + cTit + tit + "[]/" + req.titanium + "\n" +
                " • Th: " + cTho + tho + "[]/" + req.thorium;
                if (nextTier == 2) resStr += "\n • Plastanium: " + cPla + pla + "[]/" + req.plastanium;
                reqSnapshot = resStr;
            }
            dialog.cont.add(reqSnapshot).row();
            dialog.cont.add().height(10).row();

            dialog.cont.button(nextTier == 1 ? "[#ffd700]KÍCH HOẠT MK2[]" : "[#ffaa00]KÍCH HOẠT MK3[]", () -> {
                CoreBuild core = team.core();
                Req req = nextTier == 1 ? reqMK2 : reqMK3;
                boolean hasMats = core != null
                    && core.items.get(Items.silicon) >= req.silicon
                    && core.items.get(Items.titanium) >= req.titanium
                    && core.items.get(Items.thorium) >= req.thorium;
                if (nextTier == 2 && core != null) hasMats = hasMats && core.items.get(Items.plastanium) >= req.plastanium;
                if (hasMats) {
                    NexNet.take(core, Items.silicon, req.silicon);
                    NexNet.take(core, Items.titanium, req.titanium);
                    NexNet.take(core, Items.thorium, req.thorium);
                    if (nextTier == 2) NexNet.take(core, Items.plastanium, req.plastanium);
                    NexNet.fx(Fx.upgradeCore, x, y);
                    NexNet.fx(Fx.explosion, x, y);
                    NexNet.configure(this, Integer.valueOf(nextTier));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo("[red]ERROR: Vật tư nguyên liệu không đủ![]");
                }
            }).size(200, 42);

            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
                String title = "📡 HỆ THỐNG PHÁO TYTERYI ABETA & MK3";
                String descStr = "[#ffe066]⚙️ THÔNG TIN CƠ CHẾ NÂNG CẤP & ABETA:[]\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                " • [#ffd700]Hiệu ứng chuyển đổi:[] Ngưng 2 giây, mảnh ghép 2 bên bay vào, laser từ trời, khói lan tỏa xa và ngôi sao sáng lớn.\n" +
                " • [#ffaa00]Mưa Kiếm (MK2/MK3):[] Mở khóa khi đạt MK2 hoặc MK3.\n" +
                " • [#00e5ff]Trạng thái Abeta:[] Cộng dồn nhân sát thương theo cấp, triệu hồi kiếm nhỏ khi trúng đích.";
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Label cell = infoTable.add(descStr).width(360).get();
            cell.setWrap(true);
            cell.setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            int tier = getTier();
            if (tier < 2) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgradeDialog(tier))
                    .size(50, 40).tooltip("Tái cấu trúc nâng cấp");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(
                    "[#00ff88]SYSTEM LOG: Đơn vị TyterYi đã đạt cấp độ bão hòa tối đa (MAX LEVEL MK3).[]"))
                    .size(50, 40).tooltip("Hệ thống đạt giới hạn");
            }
            table.button(Icon.info, Styles.cleari, 40, this::openInfoDialog)
                .size(50, 40).tooltip("Truy xuất dữ liệu kỹ thuật");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes w) {
            super.write(w);
            w.b(getTier());
        }

        @Override
        public void read(Reads r, byte revision) {
            super.read(r, revision);
            setTier(r.b());
        }
    }
}
