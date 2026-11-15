package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.units.StatusEntry;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock;
import newex.NexNet;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ galaxvorram.js. Pháo Galaxvorram 7x7: 5 loại đạn "tấn công từ trên trời" tốn điện + vật phẩm,
 * tầm bắn cộng thêm theo số Redstone-Wall quanh pháo, tia phụ từ các khối Pinfyr, nối chuỗi với pháo kề bên.
 * Block do Java tạo (`new PowerTurret`) → galaxvorram.hjson không có `type:` cấp cao nhất (bài 29a).
 */
public final class Galaxvorram {
    private Galaxvorram() {}

    /** JS: `galaxvorram.powerCapacity = 10000` chỉ là thuộc tính JS (Block không có trường này, bài 89). */
    static final float POWER_CAP = 10000f;
    /** JS: `b.block.powerCapacity || 4000` — Block v157 không có trường → luôn 4000. */
    static final int OTHER_POWER_CAP = 4000;

    static final String[] REDSTONE_NAMES = {"redstone-wall", "newex-redstone-wall"};
    static final String[] PINFYR_NAMES = {"pinfyr", "newex-pinfyr"};

    static final Map<Integer, Integer> pinfyrStackMap = new HashMap<>();

    static final class Ammo {
        final String name_vi, name_en, desc_vi, desc_en;
        final int val;
        final float dmgMult;
        final int reqThorium, extraPowerCost;
        final Color c1, c2;
        final Item reqItem;
        final int reqAmount;

        Ammo(String name_vi, String name_en, String desc_vi, String desc_en, int val, float dmgMult,
             int reqThorium, int extraPowerCost, Color c1, Color c2, Item reqItem, int reqAmount) {
            this.name_vi = name_vi; this.name_en = name_en; this.desc_vi = desc_vi; this.desc_en = desc_en;
            this.val = val; this.dmgMult = dmgMult; this.reqThorium = reqThorium; this.extraPowerCost = extraPowerCost;
            this.c1 = c1; this.c2 = c2; this.reqItem = reqItem; this.reqAmount = reqAmount;
        }
    }

    static final Ammo[] AMMO_DATA = {
        new Ammo("1. Đạn Tiêu Chuẩn Galax", "1. Galax Standard Ammo",
            "Sát thương gốc: [accent]5600 Dmg (100%)[]\nYêu cầu: Thorium x2 + 20 Điện/s (+500 Điện khi bắn)",
            "Base Damage: [accent]5600 Dmg (100%)[]\nRequirement: Thorium x2 + 20 Power/s (+500 Power on shot)",
            0, 1.0f, 2, 500, Color.valueOf("#c084fc"), Color.valueOf("#e879f9"), Items.thorium, 2),
        new Ammo("2. Đạn Chi Phí Thấp", "2. Low Cost Ammo",
            "Sát thương: [accent]4480 Dmg (80%)[]\nYêu cầu: Thorium x2 + Silicon x2 | +400 Điện khi bắn",
            "Damage: [accent]4480 Dmg (80%)[]\nRequirement: Thorium x2 + Silicon x2 | +400 Power on shot",
            1, 0.8f, 2, 400, Color.valueOf("#38bdf8"), Color.valueOf("#818cf8"), Items.silicon, 2),
        new Ammo("3. Đạn Hạt Nhân Diện RỘNG", "3. Wide Area Nuclear Ammo",
            "Sát thương: [accent]10080 Dmg (180%)[]\nYêu cầu: Thorium x4 + Surge Alloy x4 | +900 Điện khi bắn",
            "Damage: [accent]10080 Dmg (180%)[]\nRequirement: Thorium x4 + Surge Alloy x4 | +900 Power on shot",
            2, 1.8f, 4, 900, Color.valueOf("#facc15"), Color.valueOf("#f97316"), Items.surgeAlloy, 4),
        new Ammo("4. Đạn Xung Độc Điện Từ", "4. EMP Ammo",
            "Sát thương: [accent]6720 Dmg (120%)[]\nYêu cầu: Thorium x2 + Phase Fabric x2 | +600 Điện khi bắn",
            "Damage: [accent]6720 Dmg (120%)[]\nRequirement: Thorium x2 + Phase Fabric x2 | +600 Power on shot",
            3, 1.2f, 2, 600, Color.valueOf("#4ade80"), Color.valueOf("#22d3ee"), Items.phaseFabric, 2),
        new Ammo("5. Siêu Đạn Obs Khai Diệt", "5. Annihilation Super Obs Ammo",
            "Sát thương: [accent]28000 Dmg (500%)[]\nYêu cầu: Thorium x1000 + Obs x1200 | +1200 Điện khi bắn\n[lightgray]Tạo 5-14 nổ phụ lần lượt xung quanh. Tone màu Trắng-Đen.[]",
            "Damage: [accent]28000 Dmg (500%)[]\nRequirement: Thorium x1000 + Obs x1200 | +1200 Power on shot\n[lightgray]Triggers 5-14 sub-explosions sequentially. Black-White tone.[]",
            4, 5.0f, 1000, 1200, Color.valueOf("#ffffff"), Color.valueOf("#18181b"), null, 1200)
    };

    static final Color ELEC_OUTER_BLACK = Color.valueOf("#18181b");
    static final Color ELEC_MID_GRAY = Color.valueOf("#52525b");
    static final Color ELEC_CORE_WHITE = Color.valueOf("#ffffff");

    static final Color CHARGE_DARK_PINK_RED = Color.valueOf("#be123c");
    static final Color CHARGE_LIGHT_PINK = Color.valueOf("#fb7185");

    private static final Color[] DEFAULT_COLORS = {Color.valueOf("#c084fc"), Color.valueOf("#e879f9")};

    private static StatusEffect armorShredStatus;
    private static LaserBulletType burstLaserType, pinfyrLaser;
    private static Effect lyvervon3LayerLightningFx, vGalaxChargeFx, vGalaxIndeniterExplosionFx, vGalaxMiniExplosionFx;
    private static Effect vGalaxCollapseFx, vGalaxSkyStrikeFx, vTargetWarningFx, vSkyLaunchFx, vChainLinkFx;
    private static LaserBulletType dummyLaserSkyType;
    private static PowerTurret turret;

    private static Item cachedObs = null;

    private static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static Item findItem(String name) {
        Item found = (Item) Vars.content.getByName(ContentType.item, name);
        if (found == null) found = (Item) Vars.content.getByName(ContentType.item, "newex-" + name);
        return found;
    }

    /** JS: `itemObs || findContent(item, "newex-obs")` — tra lười vì item obs nạp sau create(). */
    private static Item obs() {
        if (cachedObs == null) cachedObs = findItem("newex-obs");
        return cachedObs;
    }

    private static boolean isValidAmmoItem(Item item) {
        Item targetObs = obs();
        return item == Items.thorium || item == Items.silicon || item == Items.surgeAlloy
            || item == Items.phaseFabric || (targetObs != null && item == targetObs);
    }

    private static void drawLines(Seq<Vec2> points) {
        for (int i = 0; i < points.size - 1; i++) {
            Lines.line(points.get(i).x, points.get(i).y, points.get(i + 1).x, points.get(i + 1).y, false);
        }
    }

    static void create3LayerLightningStandard(float x1, float y1, float x2, float y2, float thickness) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(4, Mathf.floor(dst / 7f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);

        for (int i = 1; i < segs; i++) {
            float t = i / (float) segs;
            float px = Mathf.lerp(x1, x2, t);
            float py = Mathf.lerp(y1, y2, t);
            float maxCurve = 18f;
            float jitter = 13f;
            float arc = Mathf.sin(t * Mathf.PI) * maxCurve;
            float noise = Mathf.range(jitter) * (1f - t);

            Tmp.v1.trns(angle + 90f, arc + noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));
        lyvervon3LayerLightningFx.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, points);
    }

    private static Effect explosionFx(float radius) {
        return new Effect(50f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 0.1f);
            float maxRadius = radius;
            float alpha = 1f - e.fin();

            Color[] colorArray = e.data instanceof Color[] ? (Color[]) e.data : DEFAULT_COLORS;
            Color col = colorArray[0];
            Color col2 = colorArray[1];

            Draw.color(col);
            Draw.alpha(alpha * 0.35f);
            Fill.circle(e.x, e.y, maxRadius);

            Draw.color(col);
            Draw.alpha(alpha * 0.7f);
            Lines.stroke(2.5f * alpha);
            Lines.circle(e.x, e.y, maxRadius);

            Color[] ringColors = {Color.white, col2, col};

            for (int i = 0; i < 3; i++) {
                float delay = i * 0.12f;
                if (e.fin() > delay) {
                    float progress = (e.fin() - delay) / (1f - delay);
                    float smoothProgress = Interp.pow3Out.apply(progress);
                    float dynamicRadius = maxRadius * smoothProgress;

                    Draw.color(ringColors[i]);
                    Draw.alpha(alpha * (1f - smoothProgress));
                    Lines.stroke((14f - i * 3f) * (1f - smoothProgress));
                    Lines.circle(e.x, e.y, dynamicRadius);
                }
            }

            Draw.reset();
        });
    }

    static void draw3DRotatedEllipseWave(float centerX, float centerY, float radiusX, float radiusY, float rotationDeg) {
        int points = 24;
        float rotationRad = rotationDeg * Mathf.degRad;
        float cosRot = (float) Math.cos(rotationRad);
        float sinRot = (float) Math.sin(rotationRad);

        float localX = radiusX;
        float localY = 0f;
        float lastX = centerX + (localX * cosRot - localY * sinRot);
        float lastY = centerY + (localX * sinRot + localY * cosRot);

        for (int i = 1; i <= points; i++) {
            float angle = (i * 360f / points) * Mathf.degRad;
            localX = (float) Math.cos(angle) * radiusX;
            localY = (float) Math.sin(angle) * radiusY;

            float nextX = centerX + (localX * cosRot - localY * sinRot);
            float nextY = centerY + (localX * sinRot + localY * cosRot);

            Lines.line(lastX, lastY, nextX, nextY);

            lastX = nextX;
            lastY = nextY;
        }
    }

    private static Color[] colorsOf(Object data) {
        return data instanceof Color[] ? (Color[]) data : DEFAULT_COLORS;
    }

    private static void createEffects() {
        lyvervon3LayerLightningFx = new Effect(14f, e -> {
            if (Vars.state.isPaused()) return;
            if (!(e.data instanceof Seq)) return;

            @SuppressWarnings("unchecked")
            Seq<Vec2> points = (Seq<Vec2>) e.data;
            float baseWidth = e.rotation > 0f ? e.rotation : 3.5f;
            float fout = e.fout();

            Draw.z(Layer.effect + 0.2f);

            Draw.color(ELEC_OUTER_BLACK);
            Lines.stroke(baseWidth * 1.8f * fout);
            drawLines(points);

            Draw.color(ELEC_MID_GRAY);
            Lines.stroke(baseWidth * 1.0f * fout);
            drawLines(points);

            Draw.color(ELEC_CORE_WHITE);
            Lines.stroke(baseWidth * 0.45f * fout);
            drawLines(points);

            Draw.reset();
        });

        vGalaxChargeFx = new Effect(120f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 0.1f);

            float fin = e.fin();
            float fout = e.fout();

            float[][] rings = {
                {120f, 1.0f, 3.0f},
                {90f, 1.3f, 2.2f},
                {150f, 0.8f, 4.0f},
                {70f, 1.6f, 1.8f}
            };

            for (float[] ring : rings) {
                float currentProgress = Math.min(1f, fin * ring[1]);
                float currentRadius = ring[0] * (1f - currentProgress);

                if (currentRadius > 0.5f) {
                    Draw.color(CHARGE_DARK_PINK_RED, CHARGE_LIGHT_PINK, currentProgress);
                    Lines.stroke(ring[2] * (1f - currentProgress));
                    Lines.circle(e.x, e.y, currentRadius);

                    Draw.color(Color.white);
                    Lines.stroke((ring[2] * 0.5f) * (1f - currentProgress));
                    Lines.circle(e.x, e.y, currentRadius);
                }
            }

            if (Mathf.chanceDelta(0.3)) {
                float angle = Mathf.random(360f);
                float startX = e.x + Angles.trnsx(angle, 40f * fout);
                float startY = e.y + Angles.trnsy(angle, 40f * fout);
                Fx.lightning.at(startX, startY, angle + 180f, CHARGE_DARK_PINK_RED);
            }

            Draw.reset();
        });

        vGalaxIndeniterExplosionFx = explosionFx(960f);
        vGalaxMiniExplosionFx = explosionFx(96f);

        vGalaxCollapseFx = new Effect(45f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 0.05f);
            float fout = e.fout();
            float maxRadius = 960f;
            float currentRadius = maxRadius * fout;

            Color[] colors = colorsOf(e.data);

            Draw.color(colors[0], colors[1], e.fin());
            Lines.stroke(4f * fout);
            Lines.circle(e.x, e.y, currentRadius);

            Draw.color(Color.white);
            Lines.stroke(1.5f * fout);
            Lines.circle(e.x, e.y, currentRadius);

            Draw.reset();
        });

        vGalaxSkyStrikeFx = new Effect(50f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 3f);

            float f = e.fin();
            float alpha = 1f - Interp.pow3Out.apply(f);

            Color[] colors = colorsOf(e.data);
            Color primaryColor = colors[0];
            Color secondaryColor = colors[1];

            float startX = e.x - 600f;
            float startY = e.y + 1100f;

            Lines.stroke(32f * alpha, primaryColor);
            Lines.line(startX, startY, e.x, e.y);
            Lines.stroke(14f * alpha, Color.white);
            Lines.line(startX, startY, e.x, e.y);

            for (int i = 0; i < 5; i++) {
                float t1 = Mathf.random(1f);
                float t2 = Math.min(1f, t1 + 0.2f);
                float lx1 = Mathf.lerp(startX, e.x, t1) + Mathf.range(30f);
                float ly1 = Mathf.lerp(startY, e.y, t1) + Mathf.range(30f);
                float lx2 = Mathf.lerp(startX, e.x, t2) + Mathf.range(30f);
                float ly2 = Mathf.lerp(startY, e.y, t2) + Mathf.range(30f);

                create3LayerLightningStandard(lx1, ly1, lx2, ly2, 3f);
            }

            float laserAngle = 33f;
            float moveX = (startX - e.x) * 0.2f * Interp.pow2Out.apply(f);
            float moveY = (startY - e.y) * 0.2f * Interp.pow2Out.apply(f);

            float wave1X = e.x + moveX;
            float wave1Y = e.y + moveY;
            float radius1 = 10f + (120f * Interp.pow3Out.apply(f));

            Lines.stroke(3.5f * (1f - f), primaryColor);
            draw3DRotatedEllipseWave(wave1X, wave1Y, radius1, radius1 * 0.45f, laserAngle);

            float wave2X = e.x + (moveX * 0.7f);
            float wave2Y = e.y + (moveY * 0.7f);
            float radius2 = radius1 * 0.66f;

            Lines.stroke(2f * (1f - f), secondaryColor);
            draw3DRotatedEllipseWave(wave2X, wave2Y, radius2, radius2 * 0.45f, laserAngle);

            Draw.reset();
        });

        vTargetWarningFx = new Effect(240f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 0.01f);
            float fout = e.fout();
            float fin = e.fin();

            Color color = e.data instanceof Color ? (Color) e.data : Color.valueOf("#c084fc");

            Draw.color(color);
            Lines.stroke(2.5f * fout);
            Lines.circle(e.x, e.y, 960f * fin);

            float crossSize = 25f * fout;
            Lines.stroke(2f * fout, color);
            Lines.line(e.x - crossSize, e.y, e.x + crossSize, e.y);
            Lines.line(e.x, e.y - crossSize, e.x, e.y + crossSize);

            Lines.stroke(1f * fout, Color.white);
            Lines.line(e.x - crossSize * 0.6f, e.y, e.x + crossSize * 0.6f, e.y);
            Lines.line(e.x, e.y - crossSize * 0.6f, e.x, e.y + crossSize * 0.6f);

            float triOffset = 40f + (30f * fin);
            float triSize = 10f * fout;

            for (int i = 0; i < 4; i++) {
                float angle = i * 90f;
                float tx = e.x + Angles.trnsx(angle, triOffset);
                float ty = e.y + Angles.trnsy(angle, triOffset);

                Draw.color(color);
                Fill.poly(tx, ty, 3, triSize, angle + 180f);
                Draw.color(Color.white);
                Fill.poly(tx, ty, 3, triSize * 0.5f, angle + 180f);
            }

            Fill.circle(e.x, e.y, 4f * fout);
            Draw.reset();
        });

        vSkyLaunchFx = new Effect(35f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 2f);

            float fin = e.fin();
            float fout = e.fout();
            Color[] colors = colorsOf(e.data);

            float currentDist = 1200f * Interp.pow2Out.apply(fin);
            float endX = e.x + (180f * (currentDist / 1200f));
            float endY = e.y + currentDist;

            Lines.stroke(18f * fout, colors[0]);
            Lines.line(e.x, e.y, endX, endY);
            Lines.stroke(8f * fout, Color.white);
            Lines.line(e.x, e.y, endX, endY);

            for (int i = 0; i < 4; i++) {
                float t1 = Mathf.random(0.8f);
                float t2 = Math.min(1f, t1 + 0.2f);
                float lx1 = Mathf.lerp(e.x, endX, t1) + Mathf.range(20f);
                float ly1 = Mathf.lerp(e.y, endY, t1) + Mathf.range(20f);
                float lx2 = Mathf.lerp(e.x, endX, t2) + Mathf.range(20f);
                float ly2 = Mathf.lerp(e.y, endY, t2) + Mathf.range(20f);

                create3LayerLightningStandard(lx1, ly1, lx2, ly2, 2.8f);
            }

            Fill.circle(e.x, e.y, 12f * fout);
            Draw.reset();
        });

        vChainLinkFx = new Effect(15f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 0.05f);
            float fout = e.fout();

            if (e.data instanceof Vec2) {
                Vec2 d = (Vec2) e.data;
                float tx = d.x;
                float ty = d.y;

                Draw.color(PerfCore.color("#c084fc"), Color.white, e.fin());
                Lines.stroke(2f * fout);
                Lines.line(e.x, e.y, tx, ty);

                Draw.color(Color.white);
                Lines.stroke(1f * fout);
                Lines.line(e.x, e.y, tx, ty);

                Fill.circle(e.x, e.y, 3f * fout);
                Fill.circle(tx, ty, 3f * fout);
            }
            Draw.reset();
        });
    }

    public static void create() {
        armorShredStatus = new StatusEffect("pinfyr-armor-shred") {
            @Override
            public void init() {
                super.init();
                color = Color.valueOf("#fb7185");
                speedMultiplier = 0.85f;
                healthMultiplier = 0.95f;
            }

            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                if (unit != null) {
                    unit.armor = 0f;

                    if (unit.dead || unit.health <= 0f) {
                        pinfyrStackMap.remove(unit.id);
                    }
                }
            }
        };

        createEffects();

        burstLaserType = new LaserBulletType();
        burstLaserType.length = 480f;
        burstLaserType.width = 18f;
        burstLaserType.sideLength = 0f;
        burstLaserType.sideWidth = 0f;
        burstLaserType.lifetime = 25f;
        burstLaserType.colors = new Color[]{ELEC_OUTER_BLACK, Color.valueOf("#c084fc"), Color.white};

        // JS: createPinfyrDynamicLaser tạo MỘT loại đạn mới cho mỗi phát; Java dùng chung 1 loại và đặt `length` trước khi create.
        // JS còn có `armorPiercing: true` — BulletType không có trường này (bài 89) nên bỏ.
        pinfyrLaser = new LaserBulletType() {
            @Override
            public void hitEntity(mindustry.gen.Bullet b, Hitboxc entity, float health) {
                super.hitEntity(b, entity, health);
                if (entity instanceof Unit) {
                    Unit u = (Unit) entity;
                    int unitId = u.id;
                    int currentStacks = pinfyrStackMap.getOrDefault(unitId, 0);

                    currentStacks++;
                    pinfyrStackMap.put(unitId, currentStacks);

                    u.apply(armorShredStatus, 300f);
                }
            }
        };
        pinfyrLaser.damage = 2002f;
        pinfyrLaser.width = 10f;
        pinfyrLaser.sideLength = 0f;
        pinfyrLaser.sideWidth = 0f;
        pinfyrLaser.lifetime = 20f;
        pinfyrLaser.colors = new Color[]{Color.valueOf("#f43f5e"), Color.valueOf("#fb7185"), Color.white};

        dummyLaserSkyType = new LaserBulletType();
        dummyLaserSkyType.damage = 0f;
        dummyLaserSkyType.length = 1200f;
        dummyLaserSkyType.width = 22f;
        dummyLaserSkyType.collides = false;
        dummyLaserSkyType.collidesTiles = false;
        dummyLaserSkyType.collidesAir = false;
        dummyLaserSkyType.collidesGround = false;

        turret = new PowerTurret("galaxvorram");
        turret.configurable = true;
        turret.category = Category.turret;

        turret.health = 17000;
        turret.range = 1f;
        turret.reload = 240f;

        turret.hasItems = true;
        turret.itemCapacity = 2000;

        // JS: targetBuildings / powerCapacity / acceptItem không phải trường của Turret/Block v157 → bỏ (bài 89).
        turret.targetAir = false;
        turret.targetGround = false;

        turret.shootType = dummyLaserSkyType;
        turret.consumePower(20f);

        turret.config(Integer.class, (GalaxvorramBuild tile, Integer value) -> {
            if (tile != null) tile.setAmmoTypeIndex(value);
        });

        turret.buildType = () -> new GalaxvorramBuild(turret);
    }

    public static class GalaxvorramBuild extends PowerTurret.PowerTurretBuild {
        int ammoTypeIndex = 0;

        float reloadTimer = 0f;
        float targetScanTimer = 0f;

        float pinfyrTimer = 0f;
        float pinfyrNextInterval = Mathf.random(30f, 120f);

        boolean isChainFiring = false;
        float lockRotationTimer = 0f;

        int slaveTicks = 0;
        float slaveTargetAngle = 0f;
        boolean slaveIsShooting = false;
        float slaveAimX = 0f;
        float slaveAimY = 0f;

        public GalaxvorramBuild(PowerTurret outer) {
            outer.super();
        }

        Seq<Building> getCornerBuildings(String targetTypeStr, float minPowerRequired) {
            Seq<Building> foundList = new Seq<>();
            String[] validNames = targetTypeStr.equals("redstone") ? REDSTONE_NAMES : PINFYR_NAMES;

            float searchRadius = targetTypeStr.equals("pinfyr") ? (21 * 8) : ((block.size * 8) + 16);

            Units.nearbyBuildings(x, y, searchRadius, building -> {
                if (building != null && building.team == team && !foundList.contains(building)) {
                    String bName = building.block.name;
                    boolean isMatch = false;
                    for (String name : validNames) {
                        if (bName.equals(name) || bName.endsWith("/" + name)) { isMatch = true; break; }
                    }

                    if (isMatch) {
                        if (minPowerRequired > 0) {
                            float currentStored = building.power != null ? (building.power.status * OTHER_POWER_CAP) : 0f;
                            if (currentStored >= minPowerRequired) {
                                foundList.add(building);
                            }
                        } else {
                            foundList.add(building);
                        }
                    }
                }
            });

            return foundList;
        }

        int getCornerBlockCount(String targetTypeStr, float minPowerRequired) {
            return getCornerBuildings(targetTypeStr, minPowerRequired).size;
        }

        @Override
        public float range() {
            int redstoneWallCount = getCornerBlockCount("redstone", 0f);
            return turret.range + (redstoneWallCount * 800f);
        }

        @Override
        protected void turnToTarget(float targetAngle) {
            if (lockRotationTimer > 0f) return;
            super.turnToTarget(targetAngle);
        }

        @Override
        public int getMaximumAccepted(Item item) {
            if (isValidAmmoItem(item)) return turret.itemCapacity;
            return 0;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (!isValidAmmoItem(item) || items == null) return false;
            return items.get(item) < getMaximumAccepted(item);
        }

        int getAmmoTypeIndex() { return ammoTypeIndex; }

        void setAmmoTypeIndex(int val) { ammoTypeIndex = val; }

        void setSlaveCommand(float angle, boolean shooting, float aimX, float aimY) {
            slaveTicks = 5;
            slaveTargetAngle = angle;
            slaveIsShooting = shooting;
            slaveAimX = aimX;
            slaveAimY = aimY;
        }

        void syncChainCommand(float targetAngle, boolean shooting, float aimX, float aimY) {
            if (isChainFiring) return;
            isChainFiring = true;

            float adjacentRadius = (block.size * 8) + 8f;

            Units.nearbyBuildings(x, y, adjacentRadius, b -> {
                if (b != null && b != this && b.block == block && b.team == team) {
                    float dstX = Math.abs(x - b.x);
                    float dstY = Math.abs(y - b.y);

                    boolean isAdjacent = (dstX < 4f && dstY <= adjacentRadius) || (dstY < 4f && dstX <= adjacentRadius);

                    if (isAdjacent && b instanceof GalaxvorramBuild && !((GalaxvorramBuild) b).isChainFiring) {
                        GalaxvorramBuild other = (GalaxvorramBuild) b;
                        other.setSlaveCommand(targetAngle, shooting, aimX, aimY);

                        if (shooting) {
                            vChainLinkFx.at(x, y, 0f, new Vec2(b.x, b.y));
                        }

                        other.syncChainCommand(targetAngle, shooting, aimX, aimY);
                    }
                }
            });

            isChainFiring = false;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            float adjacentRadius = (block.size * 8) + 8f;

            Draw.z(Layer.power + 1f);
            Draw.color(PerfCore.color("#fb7185"));
            Lines.stroke(1f);
            Lines.dashCircle(x, y, 21 * 8);
            Draw.reset();

            Units.nearbyBuildings(x, y, adjacentRadius, b -> {
                if (b != null && b != this && b.block == block && b.team == team) {
                    float dstX = Math.abs(x - b.x);
                    float dstY = Math.abs(y - b.y);
                    boolean isAdjacent = (dstX < 4f && dstY <= adjacentRadius) || (dstY < 4f && dstX <= adjacentRadius);

                    if (isAdjacent) {
                        Draw.z(Layer.power + 1f);
                        Draw.color(AMMO_DATA[getAmmoTypeIndex()].c1);
                        Lines.stroke(1.2f);
                        Lines.line(x, y, b.x, b.y);

                        Draw.color(Color.white);
                        Lines.stroke(0.6f);
                        Lines.line(x, y, b.x, b.y);

                        Fill.circle(b.x, b.y, 2f);
                        Draw.reset();
                    }
                }
            });

            Seq<Building> redstoneList = getCornerBuildings("redstone", 0f);
            redstoneList.each(b -> {
                Draw.z(Layer.power + 1f);
                Draw.color(PerfCore.color("#ef4444"));
                Lines.stroke(1f);
                Lines.dashLine(x, y, b.x, b.y, Mathf.floor(Mathf.dst(x, y, b.x, b.y) / 12f));

                Draw.color(Color.white);
                Fill.circle(b.x, b.y, 2f);
                Lines.stroke(0.8f);
                Lines.circle(b.x, b.y, b.block.size * 4);
                Draw.reset();
            });

            Seq<Building> pinfyrList = getCornerBuildings("pinfyr", 0f);
            pinfyrList.each(b -> {
                float currentP = b.power != null ? (b.power.status * OTHER_POWER_CAP) : 0f;
                boolean hasPower = currentP >= 1000f;

                Draw.z(Layer.power + 1f);
                Draw.color(hasPower ? Color.valueOf("#fb7185") : Color.gray);
                Lines.stroke(1f);
                Lines.dashLine(x, y, b.x, b.y, Mathf.floor(Mathf.dst(x, y, b.x, b.y) / 12f));

                Draw.color(hasPower ? Color.white : Color.darkGray);
                Fill.circle(b.x, b.y, 2f);
                Draw.reset();
            });
        }

        boolean hasAmmoAndPowerToShoot() {
            if (items == null || power == null) return false;

            Ammo ammo = AMMO_DATA[getAmmoTypeIndex()];
            Item targetObs = obs();

            if (items.get(Items.thorium) < ammo.reqThorium) return false;

            Item requiredItem = (ammo.val == 4) ? targetObs : ammo.reqItem;
            if (requiredItem != null && requiredItem != Items.thorium) {
                if (items.get(requiredItem) < ammo.reqAmount) return false;
            }

            return (power.status * POWER_CAP) >= ammo.extraPowerCost;
        }

        void executeSkyStrike(final float targetX, final float targetY) {
            final int ammoIndex = getAmmoTypeIndex();
            final Ammo ammo = AMMO_DATA[ammoIndex];
            Item targetObs = obs();

            if (items != null) {
                items.remove(Items.thorium, ammo.reqThorium);
                Item requiredItem = (ammoIndex == 4) ? targetObs : ammo.reqItem;
                if (requiredItem != null && requiredItem != Items.thorium) {
                    items.remove(requiredItem, ammo.reqAmount);
                }
            }

            if (power != null) {
                power.status -= (ammo.extraPowerCost / POWER_CAP);
                if (power.status < 0f) power.status = 0f;
            }

            lockRotationTimer = 180f;

            vGalaxChargeFx.at(x, y);
            // JS truyền Color vào overload `Color` (không phải data) nên hiệu ứng luôn dùng tím mặc định;
            // Java ép (Object) để màu theo từng loại đạn (bài 89).
            vTargetWarningFx.at(targetX, targetY, 0f, (Object) ammo.c1);

            final mindustry.game.Team team = this.team;
            final Color[] colors = {ammo.c1, ammo.c2};
            final float dmgMult = ammo.dmgMult;
            final float launchX = x;
            final float launchY = y;

            Time.run(120f, () -> {
                vSkyLaunchFx.at(launchX, launchY, 0f, (Object) colors);

                Time.run(120f, () -> {
                    float baseDmg = 5600f * dmgMult;

                    vGalaxSkyStrikeFx.at(targetX, targetY, 0f, (Object) colors);
                    vGalaxCollapseFx.at(targetX, targetY, 0f, (Object) colors);
                    vGalaxIndeniterExplosionFx.at(targetX, targetY, 0f, (Object) colors);

                    float laserDmg = baseDmg * 0.5f;
                    int laserCount = 12;

                    for (int i = 0; i < laserCount; i++) {
                        float randomAngle = (i * (360f / laserCount)) + Mathf.range(15f);
                        burstLaserType.create(this, team, targetX, targetY, randomAngle, laserDmg, 1f);
                    }

                    Damage.damage(team, targetX, targetY, 960f, baseDmg, true, true);

                    if (ammoIndex == 4) {
                        float subExplosionCount = Mathf.random(5f, 14f);
                        float subDmg = 5600f * 0.5f;
                        float currentDelay = 0f;

                        for (int i = 0; i < subExplosionCount; i++) {
                            float offsetDist = Mathf.random(80f, 960f * 0.85f);
                            float offsetAngle = Mathf.random(360f);
                            final float subX = targetX + Angles.trnsx(offsetAngle, offsetDist);
                            final float subY = targetY + Angles.trnsy(offsetAngle, offsetDist);

                            currentDelay += Mathf.random(3f, 12f);

                            Time.run(currentDelay, () -> {
                                vGalaxMiniExplosionFx.at(subX, subY, 0f, (Object) colors);
                                Damage.damage(team, subX, subY, 96f, subDmg, true, true);
                                Fx.reactorExplosion.at(subX, subY);
                            });
                        }
                    }

                    Effect.shake(14f, 14f, targetX, targetY);
                    Fx.reactorExplosion.at(targetX, targetY);
                });
            });
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (slaveTicks > 0) slaveTicks--;
            if (reloadTimer > 0f) reloadTimer -= Time.delta;
            if (lockRotationTimer > 0f) lockRotationTimer -= Time.delta;

            Seq<Building> pinfyrActiveBuildings = getCornerBuildings("pinfyr", 1000f);
            if (pinfyrActiveBuildings.size > 0) {
                pinfyrTimer += Time.delta;

                if (pinfyrTimer >= pinfyrNextInterval) {
                    pinfyrTimer = 0f;
                    pinfyrNextInterval = Mathf.random(30f, 120f);

                    pinfyrActiveBuildings.each(pBuilding -> {
                        if (pBuilding.power != null) {
                            float currentStored = pBuilding.power.status * OTHER_POWER_CAP;

                            if (currentStored >= 1000f) {
                                float newPower = currentStored - 1000f;
                                pBuilding.power.status = Math.max(0f, newPower / OTHER_POWER_CAP);

                                Teamc enemyTarget = Units.closestTarget(
                                    team, x, y, 4000f,
                                    u -> u.checkTarget(true, true),
                                    b -> !(b.block instanceof CoreBlock)
                                );

                                if (enemyTarget != null) {
                                    float dst = Mathf.dst(pBuilding.x, pBuilding.y, enemyTarget.getX(), enemyTarget.getY());
                                    float aimAngle = Angles.angle(pBuilding.x, pBuilding.y, enemyTarget.getX(), enemyTarget.getY());

                                    pinfyrLaser.length = Math.max(16f, dst);
                                    // JS truyền damage = 1.0 → tia chỉ gây 1 sát thương dù khai 2002 (bài 89); Java dùng -1 = sát thương của loại đạn.
                                    pinfyrLaser.create(this, team, pBuilding.x, pBuilding.y, aimAngle, -1f, 1f);
                                }
                            }
                        }
                    });
                }
            }

            boolean isLocked = (lockRotationTimer > 0f);
            boolean isDirectControlled = isControlled() || logicControlled();
            boolean isSlaveControlled = (slaveTicks > 0);
            boolean hasPower = power != null && power.status > 0.1f;
            // JS: `this.isShooting` thiếu `()` (luôn truthy, bài 90) → Java gọi isShooting() thật.
            boolean shooting = isShooting();

            float targetX = x;
            float targetY = y;

            if (isDirectControlled) {
                slaveTicks = 0;
                mindustry.gen.BlockUnitc u = unit;
                if (u != null) {
                    targetX = u.aimX();
                    targetY = u.aimY();

                    if (!isLocked) {
                        float tAngle = angleTo(targetX, targetY);
                        rotation = Angles.moveToward(rotation, tAngle, turret.rotateSpeed * Time.delta);
                    }

                    syncChainCommand(rotation, shooting, targetX, targetY);
                }
            } else if (isSlaveControlled) {
                targetX = slaveAimX;
                targetY = slaveAimY;

                if (!isLocked) {
                    rotation = Angles.moveToward(rotation, slaveTargetAngle, turret.rotateSpeed * Time.delta);
                }
            } else {
                targetScanTimer += Time.delta;
                if (targetScanTimer >= 15f) {
                    targetScanTimer = 0f;

                    Building enemyCore = Vars.state.teams.closestEnemyCore(x, y, team);
                    if (enemyCore != null && within(enemyCore, range())) {
                        target = enemyCore;
                    } else {
                        target = null;
                    }
                }

                if (hasPower && target != null) {
                    targetX = target.getX();
                    targetY = target.getY();
                    turnToTarget(angleTo(target));
                }
            }

            float targetAngle = angleTo(targetX, targetY);
            boolean isAimed = Angles.near(rotation, targetAngle, 5f);

            boolean canShoot = false;
            if (!isLocked && hasPower && isAimed && hasAmmoAndPowerToShoot()) {
                if (isDirectControlled) canShoot = shooting;
                else if (isSlaveControlled) canShoot = slaveIsShooting;
                else canShoot = (target != null && within(target, range()));
            }

            if (canShoot && reloadTimer <= 0f) {
                reloadTimer = turret.reload;
                executeSkyStrike(targetX, targetY);
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();

            table.button(Icon.upOpen, Styles.cleari, 40, () -> {
                String dialogTitle = isEn() ? "Ammo Selection System - Galaxvorram" : "Hệ Thống Lựa Chọn Loại Đạn - Galaxvorram";
                BaseDialog dialog = new BaseDialog(dialogTitle);

                Table mainTable = new Table();
                mainTable.background(Styles.black6);
                mainTable.margin(12f);

                String headerText = isEn() ? "[gold]★ GALAXVORRAM AMMO CONFIGURATION PANEL ★[]" : "[gold]★ BẢNG ĐIỀU CHỈNH CHẾ ĐỘ ĐẠN GALAXVORRAM ★[]";
                mainTable.add(headerText).row();
                mainTable.add().height(10f).row();

                for (Ammo ammo : AMMO_DATA) {
                    boolean isSelected = (getAmmoTypeIndex() == ammo.val);
                    String ammoName = isEn() ? ammo.name_en : ammo.name_vi;
                    String ammoDesc = isEn() ? ammo.desc_en : ammo.desc_vi;

                    String btnText = (isSelected ? "[green]✔ " : "[white]") + ammoName;

                    mainTable.button(btnText, () -> {
                        setAmmoTypeIndex(ammo.val);
                        NexNet.configure(this, Integer.valueOf(ammo.val));
                        NexNet.fx(Fx.upgradeCore, x, y);
                        String infoText = isEn() ? "[gold]Ammo type installed:[]\n" : "[gold]Đã cài đặt loại đạn:[]\n";
                        Vars.ui.showInfo(infoText + ammoName);
                        dialog.hide();
                        deselect();
                    }).size(340f, 42f).row();

                    Cell<Label> descCell = mainTable.add(ammoDesc).width(320f);
                    descCell.get().setWrap(true);
                    descCell.get().setAlignment(Align.left);
                    mainTable.add().height(12f).row();
                }

                ScrollPane scroll = new ScrollPane(mainTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(420f);
                dialog.addCloseButton();
                dialog.show();
            }).size(50f, 40f).tooltip(isEn() ? "Select ammo type for Galaxvorram" : "Chọn loại đạn bắn cho pháo Galaxvorram");

            table.button(Icon.info, Styles.cleari, 40, () -> {
                int redstoneCount = getCornerBlockCount("redstone", 0f);
                Seq<Building> pinfyrList = getCornerBuildings("pinfyr", 0f);
                int pinfyrActiveCount = getCornerBlockCount("pinfyr", 1000f);

                int totalRange = Math.round(turret.range) + (redstoneCount * 800);

                String pinfyrInfoStr = "";
                // JS: pinfyrList.each((b, i) => …) — Seq.each chỉ truyền 1 đối số nên `i` undefined → "#NaN"; Java dùng chỉ số thật (bài 89).
                for (int i = 0; i < pinfyrList.size; i++) {
                    Building b = pinfyrList.get(i);
                    int pCap = OTHER_POWER_CAP;
                    int currentP = b.power != null ? (int) Math.floor(b.power.status * pCap) : 0;
                    String statusColor = currentP >= 1000 ? "[green]" : "[scarlet]";
                if (isEn()) {
                    pinfyrInfoStr += "\n  └ Pinfyr Block #" + (i + 1) + ": " + statusColor + currentP + " / " + pCap + " Power[] (Req ≥ 1,000)";
                } else {
                    pinfyrInfoStr += "\n  └ Khối Pinfyr #" + (i + 1) + ": " + statusColor + currentP + " / " + pCap + " Điện[] (Cần ≥ 1,000)";
                }
                }

                String dialogTitle = isEn() ? "Galaxvorram Cannon Stats" : "Thông số pháo Galaxvorram";
                BaseDialog dialog = new BaseDialog(dialogTitle);
                Table infoTable = new Table();
                infoTable.margin(10f);

                String descStr = "";
                if (isEn()) {
                descStr = "[gold]⚡ GALAXVORRAM WEAPON STATS ⚡[]\n" +
                          "• Health: 17,000\n" +
                          "• Base Range: 1px\n" +
                          "• Ammo Capacity: 2,000\n" +
                          "• Base Damage: [accent]5600 Dmg[]\n\n" +
                          "[scarlet]━━━━ REALTIME BUFF STATUS ━━━━[]\n\n" +
                          "[red]● Redstone-Wall Blocks:[] " + redstoneCount + " blocks in connection range\n" +
                          "  └ Extra Range: [green]+" + (redstoneCount * 800) + "px[]\n" +
                          "  └ Current Total Range: [accent]" + totalRange + "px[]\n\n" +
                          "[pink]● Pinfyr Blocks:[] " + pinfyrList.size + " blocks within 21 tiles\n" +
                          "  └ READY Blocks (≥1k Power): [accent]" + pinfyrActiveCount + "/" + pinfyrList.size + "[]" +
                          (pinfyrList.size > 0 ? pinfyrInfoStr : "") + "\n" +
                          "  └ Power Cost: [yellow]1,000 Power / 1 shot / 1 block[]\n" +
                          "  └ Sub-beam Damage: [accent]2002 Dmg[] (Armor Piercing + 100% Armor Shred)\n" +
                          "  └ Beam Length: [lightgray]Auto-extends to target (Ignores Cores)[]\n" +
                          "  └ Stacking Effect: [pink]Unlimited Stacks[]";
                } else {
                descStr = "[gold]⚡ THÔNG SỐ VŨ KHÍ GALAXVORRAM ⚡[]\n" +
                          "• Máu: 17,000\n" +
                          "• Tầm ngắm gốc: 1,400px\n" +
                          "• Sức chứa kho đạn: 2,000\n" +
                          "• Sát thương gốc: [accent]5600 Dmg[]\n\n" +
                          "[scarlet]━━━━ TRẠNG THÁI BUFF REALTIME ━━━━[]\n\n" +
                          "[red]● Khối Redstone-Wall:[] " + redstoneCount + " khối trong tầm kết nối\n" +
                          "  └ Tầm bắn bổ sung: [green]+" + (redstoneCount * 800) + "px[]\n" +
                          "  └ Tổng Tầm Bắn hiện tại: [accent]" + totalRange + "px[]\n\n" +
                          "[pink]● Khối Pinfyr:[] " + pinfyrList.size + " khối trong phạm vi 21 ô xung quanh pháo\n" +
                          "  └ Khối SẴN SÀNG (≥1k Điện): [accent]" + pinfyrActiveCount + "/" + pinfyrList.size + "[]" +
                          (pinfyrList.size > 0 ? pinfyrInfoStr : "") + "\n" +
                          "  └ Tiêu hao điện: [yellow]1,000 Điện / 1 lần bắn / 1 khối[]\n" +
                          "  └ Sát thương tia phụ: [accent]2002 Dmg[] (Xuyên giáp + Trừ 100% Giáp)\n" +
                          "  └ Chiều dài tia: [lightgray]Tự động kéo dài tới vị trí mục tiêu (Không bắn Lõi)[]\n" +
                          "  └ Hiệu ứng cộng dồn: [pink]Không giới hạn tầng[]";
                }

                Cell<Label> cell = infoTable.add(descStr).width(380f);
                cell.get().setWrap(true);
                cell.get().setAlignment(Align.left);

                ScrollPane scroll = new ScrollPane(infoTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(420f);
                dialog.addCloseButton();
                dialog.show();
            }).size(50f, 40f).tooltip(isEn() ? "View Stats & Buff Status" : "Xem thông số pháo & trạng thái Buff");
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getAmmoTypeIndex());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setAmmoTypeIndex(read.b());
        }
    }
}
