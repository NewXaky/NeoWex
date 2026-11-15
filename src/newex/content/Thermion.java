package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Floatp;
import arc.func.Prov;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import java.util.ArrayList;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.Effect.EffectContainer;
import mindustry.entities.Puddles;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.units.AIController;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.blocks.environment.Floor;
import newex.NexNet;

/** Thermion (thermion.js) — PHẦN A: hằng số, bảng số liệu, hàm vẽ, hiệu ứng, âm thanh. */
public class Thermion {
    // ---------- hằng
    static final float TS = Vars.tilesize;
    static final double DEG = Math.PI / 180;
    static final int YELLOW = 0, BLUE = 1, PURPLE = 2, DEATH = 3, MAX_LEVEL = 5, CFG_ACTIVE = 10, CFG_UPGRADE = 20;
    static final int AST_NONE = 0, AST_CHARGE = 1, AST_FALL = 2;
    static final int T_FIELD = 0, T_BLADE = 1, T_RCT = 2, T_ILLUSION = 3, T_DRONE = 4, T_SHAPE = 5, T_MIND = 6, T_ASTEROID = 7;
    static final int MAX_COST_ITEMS = 8;

    // ---------- bảng số liệu (index = level - 1)
    static final float[] FORM_CD = {120, 105, 90, 75, 60}, FORM_TIME = {5, 4.5f, 4, 3.5f, 3}, HP_BONUS = {75, 50, 20};
    static final float[] SLOW = {10, 15, 20, 30, 40}, SPH_CHARGE = {0, 120, 105, 105, 90}, SPH_DMG = {0, 300, 500, 700, 1100}, SPH_MAX = {0, 10, 15, 30, 50};
    static final float[] SH_PCT = {0, 0, 50, 75, 100}, SH_CD = {0, 0, 1200, 900, 600}, SH_RADIUS = {0, 0, 5, 10, 15};
    static final float[] MAG = {0, 0, 0, 1.5f, 2}, BL_COUNT = {0, 0, 0, 1, 2}, BL_DMG = {0, 0, 0, 400, 500}, BL_CREATE = {0, 0, 0, 1800, 1200};
    static final float EMP_DURATION = 300, EMP_COOLDOWN = 7200;
    static final float[] BLS_CH = {15, 25, 35, 45, 60}, BLS_DMG = {300, 350, 450, 600, 900}, BLS_SPLASH = {2, 2, 2.5f, 2.5f, 3};
    static final float[] MET_CH = {0, 5, 10, 15, 20}, MET_PCT = {0, 1, 1, 2, 2}, MET_FLAT = {0, 50, 75, 125, 250};
    static final float[] LAS_CH = {0, 0, 2, 5, 10}, LAS_DMG = {0, 0, 250, 400, 700}, LAS_TIME = {0, 0, 2, 2.5f, 3};
    static final float[] RCT_T = {0, 0, 0, 900, 600}, RCT_A = {0, 0, 0, 50, 100}, LIM_DECAY = {0, 0, 0, 5, 10};
    static final float[] ILL_T = {5, 4.5f, 4, 3.5f, 3}, ILL_CH = {5, 10, 15, 20, 25}, ILL_DUR = {1, 1.5f, 2, 2.5f, 3};
    static final float[] DRONE_T = {0, 10, 8.5f, 7, 5}, SHAPE_T = {0, 0, 10, 9, 8}, SHAPE_CH = {0, 0, 5, 10, 15};
    static final float[] MIND_T = {0, 0, 0, 15, 10}, MIND_CH = {0, 0, 0, 10, 15};
    static final float AST_CHARGE_TICKS = 3600, SKY_HEIGHT = 1400, METEOR_FALL = 27, AST_TRAVEL = 360, AST_WAVE = 150;
    static final float AST_BODY_TILES = 100, AST_CRATERS = 32, AST_FLASH = 300, AST_FLASH_HOLD = 0.1f, AST_QUAKE = 3600, AST_SHAKE = 20;
    static final float MUSHROOM_TICKS = 2160, MUSHROOM_TILES = 64, AST_COOLDOWN = 18000, DEATH_TICKS = 36000, MEGA_COOLDOWN = 10800;
    static final float BLS_MAX_TICKS = 300, BLS_COOLDOWN_TICKS = 180; // tia plasma: đuổi tối đa 5s, hồi 3s
    static final float ORB_R = 12, METEOR_RAD_TILES = 4, METEOR_PUDDLES = 3, SLAG_CHANCE = 0.75f, BLS_SPLASH_FRAC = 1f / 3f, LAS_SPLASH_FRAC = 0.5f;
    static final float SH_START = 0.1f, BONUS_PER_M = 0.005f, BONUS_CAP = 10000000, BONUS_BLADE = 0.5f;
    static final float SH_REGEN = 120, SH_REGEN_FRAC = 0.1f, SH_HIT_GAP = 4, LOOP_TICKS = 46, SPH_SPAWN_R = 15;
    static final float ORB_Z = Layer.flyingUnit + 1, BLADE_Z = Layer.flyingUnitLow, PUSH = 2.5f, BLADE_SPEED = 7, BLADE_R = 20;
    static final float BLS_INT = 15, BLS_FALL = 10, LIM_DIV = 3, SHAPE_DELAY = 120, RANGE_REF = 50, DRONE_V2_LEVEL = 4;
    static final Color YELLOW_COLOR = Color.gold, BLUE_COLOR = Color.sky, PURPLE_COLOR = Color.purple, GRAY_COLOR = Color.gray;
    static final Color AST_BODY_C = Color.valueOf("3a2f2a"), AST_LIGHT_C = Color.valueOf("7a6858"), CR_RIM = Color.valueOf("6b5a4d"),
        CR_PIT = Color.valueOf("231b18"), CR_DEEP = Color.valueOf("120d0b"), SMOKE_C = Color.valueOf("3b322d"), ASH_C = Color.valueOf("6a5c52");
    static float auraAlpha = 1f;

    // ---------- helper toán (JS dùng Math.* kiểu double; giữ double để khớp)
    static float cl(double x) { return (float) Math.min(1.0, Math.max(0.0, x)); }
    static float cl(double x, double a, double b) { return (float) Math.min(b, Math.max(a, x)); }
    static float rnd(double a, double b) { return (float) (a + Math.random() * (b - a)); }
    static float rr(double a) { return (float) ((Math.random() * 2 - 1) * a); }
    static int ri(int a, int b) { return a + (int) Math.floor(Math.random() * (b - a + 1)); }
    static boolean chance(double p) { return Math.random() < p; }
    static float rs(double s, double a, double b) { double x = Math.sin(s * 12.9898 + 78.233) * 43758.5453; x -= Math.floor(x); return (float) (a + (b - a) * x); }
    static float tx(double a, double l) { return (float) (Math.cos(a * DEG) * l); }
    static float ty(double a, double l) { return (float) (Math.sin(a * DEG) * l); }
    static float lerp(double a, double b, double t) { return (float) (a + (b - a) * t); }
    static float dst(double a, double b, double c, double d) { return (float) Math.hypot(c - a, d - b); }

    static float val(float[] t, int lv) { return t[Math.min(Math.max(lv, 1), MAX_LEVEL) - 1]; }
    static float powerRequirement(int lv) { return lv * lv * 1000f; }
    static float formHealthBonus(int f, int lv) { return (f < YELLOW || f > PURPLE) ? 0f : HP_BONUS[f] * lv; }
    static Color formColor(int f) { return f == YELLOW ? YELLOW_COLOR : f == BLUE ? BLUE_COLOR : f == PURPLE ? PURPLE_COLOR : GRAY_COLOR; }
    static String formName(int f) { return new String[]{"Yellow", "Blue", "Purple", "Death"}[f]; }
    static String colorTag(int f) { return "[#" + formColor(f) + "]"; }
    /** JS: số gần nguyên -> chuỗi nguyên, ngược lại toFixed(1). */
    static String fmt(double v) { return Math.abs(v - Math.round(v)) < 0.001 ? String.valueOf(Math.round(v)) : Strings.fixed((float) v, 1); }
    static String compactNum(double v) {
        double a = Math.abs(v);
        return a >= 1000000 ? Strings.fixed((float) (v / 1000000), 2) + "M" : a >= 10000 ? Strings.fixed((float) (v / 1000), 1) + "k" : String.valueOf(Math.round(v));
    }
    static String timeText(double t) {
        int s = (int) Math.max(0, Math.ceil(t / 60));
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
    static boolean isSandbox() { return Vars.state != null && Vars.state.rules != null && Vars.state.rules.mode() == mindustry.game.Gamemode.sandbox; }
    static boolean isEn() { return Core.settings.getString("locale").startsWith("en"); }
    static String T(String vi, String en) { return isEn() ? en : vi; }

    // ---------- chi phí nâng cấp: [item, số lượng]
    static Object[][][] upCosts = null;
    /** Trả mảng {Item, Integer}; null nếu không nâng được. */
    static Object[][] upgradeCost(int lv) {
        if (lv < 1 || lv >= MAX_LEVEL) return null;
        if (upCosts == null) upCosts = new Object[][][]{
            {{Items.copper, 500}, {Items.silicon, 100}, {Items.metaglass, 200}},
            {{Items.copper, 1500}, {Items.silicon, 600}, {Items.metaglass, 1000}, {Items.titanium, 500}, {Items.plastanium, 200}},
            {{Items.copper, 6000}, {Items.silicon, 3000}, {Items.titanium, 2500}, {Items.plastanium, 1600}, {Items.thorium, 1500}, {Items.phaseFabric, 600}},
            {{Items.copper, 45000}, {Items.silicon, 17000}, {Items.titanium, 24000}, {Items.plastanium, 11000}, {Items.thorium, 21000}, {Items.phaseFabric, 12000}, {Items.surgeAlloy, 7000}}
        };
        Object[][] b = upCosts[lv - 1];
        if (!isSandbox()) return b;
        Object[][] z = new Object[b.length][];
        for (int i = 0; i < b.length; i++) z[i] = new Object[]{b[i][0], 0};
        return z;
    }

    // ---------- âm thanh (JS tra Sounds[tên] động -> reflection; không có thì bỏ qua)
    static arc.audio.Sound snd(String... names) {
        for (String n : names) {
            try { Object s = Sounds.class.getField(n).get(null); if (s instanceof arc.audio.Sound) return (arc.audio.Sound) s; } catch (Throwable ignored) {}
        }
        return null;
    }
    static final String[] ASSET_NAMES = {"DeathLaserExplosion", "DeathLaserLoop", "DroneShootLaser", "DualBladeHitSound", "EnergyShieldBreak", "EnergyShieldHit", "MachineGunShootSound", "MeteoroidHitSound", "OrbHitSound", "OrbShootSound"};
    static final java.util.HashMap<String, arc.audio.Sound> assetSnd = new java.util.HashMap<>();
    static boolean sndLoaded = false;
    static void loadSounds() {
        if (sndLoaded) return; sndLoaded = true;
        for (String n : ASSET_NAMES) {
            try { arc.audio.Sound s = Vars.tree.loadSound(n); if (s != null) assetSnd.put(n, s); } catch (Throwable e) { Log.err("[Thermion] không nạp được sounds/" + n); }
        }
    }
    static arc.audio.Sound pick(String n, java.util.function.Supplier<arc.audio.Sound> fb) { arc.audio.Sound s = assetSnd.get(n); return s != null ? s : fb.get(); }
    static boolean isAssetSound(arc.audio.Sound s) { for (arc.audio.Sound v : assetSnd.values()) if (v == s) return true; return false; }
    static arc.audio.Sound sndShootBig() { return snd("shootBig", "shootbig", "bigshot", "shootAltLong", "shootAlt", "shotgun", "shoot"); }
    static arc.audio.Sound sndSpark() { return snd("spark", "electric", "shock", "flux"); }
    static arc.audio.Sound sndPulse() { return snd("pulse", "shockBlast", "empulse", "laserblast", "laserBlast", "lasercharge", "laserCharge"); }
    static arc.audio.Sound sndLaser() { return snd("laser", "lasershoot", "laserShoot", "shootLaser", "beam", "laserbeam", "laserBeam"); }
    static arc.audio.Sound sndLaserBig() { return snd("laserbig", "laserBig", "laserblast", "laserBlast", "laserbeam", "laserBeam", "lasercharge", "laserCharge"); }
    static arc.audio.Sound sndExplosionBig() { return snd("explosionbig", "explosionBig", "bigExplosion", "largeExplosion", "corexplode", "coreExplode", "explosion"); }
    static arc.audio.Sound sndOrbShoot() { return pick("OrbShootSound", Thermion::sndShootBig); }
    static arc.audio.Sound sndMachineGun() { return pick("MachineGunShootSound", () -> Sounds.shoot); }
    static arc.audio.Sound sndDroneLaser() { return pick("DroneShootLaser", Thermion::sndLaser); }
    static arc.audio.Sound sndOrbHit() { return pick("OrbHitSound", () -> Sounds.none); }
    static arc.audio.Sound sndShieldHit() { return pick("EnergyShieldHit", Thermion::sndSpark); }
    static arc.audio.Sound sndShieldBreak() { return pick("EnergyShieldBreak", Thermion::sndPulse); }
    static arc.audio.Sound sndBladeHit() { return pick("DualBladeHitSound", Thermion::sndSpark); }
    static arc.audio.Sound sndMeteorHit() { return pick("MeteoroidHitSound", () -> Sounds.explosion); }
    static arc.audio.Sound sndDeathLoop() { return pick("DeathLaserLoop", Thermion::sndLaserBig); }
    static arc.audio.Sound sndDeathBlast() { return pick("DeathLaserExplosion", Thermion::sndExplosionBig); }

    // ---------- hàm vẽ
    interface Multi { void draw(float scale, Color ov, float alpha, float ss); }
    static void drawMulti(Multi f) { f.draw(1.3f, null, 0.3f * auraAlpha, 1.4f); f.draw(1f, null, auraAlpha, 1f); f.draw(0.65f, Color.white, 0.9f * auraAlpha, 0.6f); }
    static void drawTight(Multi f) { f.draw(1.12f, null, 0.35f * auraAlpha, 1.1f); f.draw(1f, null, auraAlpha, 1f); f.draw(0.82f, Color.white, 0.8f * auraAlpha, 0.85f); }
    static void drawCorePulse(float x, float y, Color color, float scale) {
        Draw.color(color);
        float r = (Time.time * 2) % 24 * scale;
        Draw.alpha(Math.max(0, 1 - r / (24 * scale)) * auraAlpha);
        Lines.stroke(2); Lines.poly(x, y, 6, r, 0); Draw.reset();
    }
    static void drawMeteorImpact(float x, float y, float radius, float p) {
        Draw.color(Color.scarlet, Color.orange, p); Lines.stroke(3 * (1 - p)); Lines.circle(x, y, radius * p);
        Draw.color(Color.orange); Fill.circle(x, y, (1 - p) * 8); Draw.reset();
    }
    static void drawCrater(float cx, float cy, float cr) {
        Draw.color(CR_RIM); Draw.alpha(0.55f); Fill.circle(cx, cy, cr * 1.12f);
        Draw.color(CR_PIT); Draw.alpha(0.75f); Fill.circle(cx, cy, cr * 0.94f);
        Draw.color(CR_DEEP); Draw.alpha(0.8f); Fill.circle(cx - cr * 0.14f, cy + cr * 0.14f, cr * 0.72f); Draw.reset();
    }
    static void drawAsteroidBody(float px, float py, float rad, double seed) {
        Draw.color(AST_BODY_C); Draw.alpha(0.6f); Fill.poly(px, py, 64, rad, 0);
        Draw.color(AST_LIGHT_C); Draw.alpha(0.18f); Fill.circle(px - rad * 0.16f, py + rad * 0.16f, rad * 0.78f);
        int n = (int) AST_CRATERS;
        for (int k = 0; k < n; k++) {
            double u = k / (double) (n - 1);
            float cr = (float) (rad * (0.02 + 0.16 * Math.pow(1 - u, 2.2)) * rs(seed + k * 131 + 5, 0.75, 1.25));
            float ca = rs(seed + k * 197 + 11, 0, 360);
            float cd = (float) ((rad - cr * 1.2) * Math.sqrt(rs(seed + k * 263 + 17, 0, 1)));
            drawCrater(px + tx(ca, cd), py + ty(ca, cd), cr);
        }
        Draw.reset();
    }
    static void heatColor(float heat, float alpha) {
        heat = cl(heat);
        if (heat > 0.5f) Draw.color(Color.orange, Color.white, (heat - 0.5f) * 2); else Draw.color(SMOKE_C, Color.orange, heat * 2);
        Draw.alpha(alpha);
    }
    static void drawMushroom(float x, float y, float s, float p) {
        int sd = 4242; float fade = cl((1 - p) * 4), rise = (float) (1 - Math.pow(1 - cl(p / 0.6), 3));
        float capGrow = (float) (1 - Math.pow(1 - cl((p - 0.08) / 0.55), 2)), height = s * 2.6f * rise;
        float capR = s * (0.25f + 0.75f * capGrow), capY = y + height, cool = 1.15f - p * 1.5f;
        Draw.z(Layer.effect + 1);
        float dustR = s * (0.5f + cl(p / 0.5));
        for (int i = 0; i < 16; i++) {
            float a = i * 22.5f + rs(sd + i * 3, -8, 8);
            Draw.color(ASH_C); Draw.alpha(0.55f * fade);
            Fill.circle(x + (float) Math.cos(a * DEG) * dustR, y + (float) Math.sin(a * DEG) * dustR * 0.22f, s * 0.22f);
        }
        for (int i = 0; i < 22; i++) {
            float f = i / 21f, px = x + rs(sd + 100 + i, -1, 1) * s * 0.07f, py = y + height * f;
            float pr = s * (0.16f + 0.4f * (1 - f) * (1 - f)) * rs(sd + 200 + i, 0.9, 1.15), heat = cool + (1 - f) * 0.3f;
            heatColor(heat, 0.22f * fade); Fill.circle(px, py, pr * 1.35f);
            heatColor(heat, 0.85f * fade); Fill.circle(px, py, pr);
        }
        float fb = 1 - cl(p / 0.35);
        if (fb > 0) {
            Draw.color(Color.white, Color.orange, 1 - fb); Draw.alpha(fade * (0.4f + 0.6f * fb));
            Fill.circle(x, y, s * (0.5f + 0.5f * cl(p / 0.08)) * (0.6f + 0.4f * fb));
        }
        if (capGrow > 0) {
            for (int i = 0; i < 12; i++) {
                float a = i * 30 + rs(sd + 300 + i, -10, 10);
                heatColor(cool - 0.25f, 0.8f * fade);
                Fill.circle(x + (float) Math.cos(a * DEG) * capR * 0.62f, capY - capR * 0.28f + (float) Math.sin(a * DEG) * capR * 0.22f, capR * 0.27f);
            }
            for (int i = 0; i < 28; i++) {
                float a = i * (360f / 28f) + rs(sd + 500 + i, -6, 6) + p * 40, k = rs(sd + 600 + i, 0.85, 1.05);
                heatColor(cool + 0.15f - 0.3f * (float) Math.sin(a * DEG), 0.9f * fade);
                Fill.circle(x + (float) Math.cos(a * DEG) * capR * k, capY + (float) Math.sin(a * DEG) * capR * 0.5f * k, capR * rs(sd + 650 + i, 0.22, 0.32));
            }
            for (int i = 0; i < 16; i++) {
                float a = rs(sd + 700 + i, 0, 360), r2 = capR * 0.75f * (float) Math.sqrt(rs(sd + 800 + i, 0, 1));
                heatColor(cool + 0.3f, 0.9f * fade);
                Fill.circle(x + (float) Math.cos(a * DEG) * r2, capY + (float) Math.sin(a * DEG) * r2 * 0.5f, capR * rs(sd + 850 + i, 0.2, 0.32));
            }
        }
        Draw.reset();
    }
    static void drawYellowAura(float x, float y, int level, float t) {
        float core = 7 + (float) Math.sin(t * 0.15) * 2, base = 0;
        drawMulti((scale, ov, alpha, ss) -> {
            Draw.color(ov != null ? ov : Color.gold); Draw.alpha(alpha); Fill.circle(x, y, core * scale);
            for (int i = 0; i < 8; i++) {
                float ang = base + i * 45, off = (float) Math.sin(t * 0.1 + i) * 5, cr = 16 + Math.max(0, off);
                Fill.poly(x + (float) Math.cos(ang * DEG) * cr, y + (float) Math.sin(ang * DEG) * cr, 3, 4 * scale, ang + 90);
            }
            Draw.reset();
        });
        if (level >= 3) drawMulti((scale, ov, alpha, ss) -> {
            if (ov != null) Draw.color(ov); else Draw.color(Color.yellow, Color.orange, ((float) Math.sin(t * 0.05) + 1) * 0.5f);
            Draw.alpha(alpha * 0.7f); Lines.stroke(1.5f * ss); Lines.poly(x, y, 8, 36 * scale, 0); Draw.reset();
        });
    }
    static void drawBlueAura(float x, float y, float t) {
        float sf = 1 + (float) Math.sin(t * 0.1) * 0.25f, core = (7 + (float) Math.sin(t * 0.15) * 2) * sf, r1 = 22 * sf, r2 = 30 * sf;
        float a1 = 0, a2 = 180;
        drawMulti((scale, ov, alpha, ss) -> {
            Draw.color(ov != null ? ov : Color.sky); Draw.alpha(alpha); Fill.circle(x, y, core * scale);
            Lines.stroke(2 * ss); Lines.arc(x, y, r1, 0.75f, a1); Lines.arc(x, y, r2, 0.75f, a2); Draw.reset();
        });
    }
    static void drawPurpleAura(float x, float y, float t) {
        float radius = 28, base = 0;
        drawMulti((scale, ov, alpha, ss) -> {
            for (int i = 0; i < 4; i++) {
                float sa = i * 90, sr = 12 + (float) Math.sin(t * 0.1 + i) * 3;
                Draw.color(ov != null ? ov : Color.violet); Draw.alpha(alpha);
                Fill.circle(x + (float) Math.cos(sa * DEG) * sr, y + (float) Math.sin(sa * DEG) * sr, 3.5f * scale);
            }
            Draw.color(ov != null ? ov : Color.purple); Draw.alpha(alpha);
            Fill.circle(x, y, (6 + (float) Math.cos(t * 0.1) * 1.5f) * scale);
            Lines.stroke(1.5f * ss); Lines.circle(x, y, radius);
            for (int i = 0; i < 4; i++) {
                float ang = base + i * 90;
                Fill.poly(x + (float) Math.cos(ang * DEG) * radius, y + (float) Math.sin(ang * DEG) * radius, 3, 5 * scale, ang);
            }
            Draw.reset();
        });
    }
    static void drawRange(float x, float y, float radius, Color c) {
        Draw.z(Layer.overlayUI); Draw.color(c); Draw.alpha(0.6f); Lines.stroke(2); Lines.poly(x, y, 12, radius, 0);
        float pr = (Time.time % 90) / 90 * radius;
        Draw.alpha(1 - pr / radius); Lines.stroke(1); Lines.circle(x, y, pr);
        Draw.alpha(0.8f);
        for (int i = 0; i < 4; i++) { float a = i * 90; Fill.square(x + (float) Math.cos(a * DEG) * radius, y + (float) Math.sin(a * DEG) * radius, 3, a); }
        Draw.alpha(0.15f); Fill.circle(x, y, radius); Draw.reset();
    }
    static void skyBeam(float x, float y, float width, float alpha, float height, Color c) {
        float top = y + height;
        drawMulti((scale, ov, a, ss) -> {
            Draw.color(ov != null ? ov : c); Draw.alpha(a * alpha); Lines.stroke(Math.max(0.1f, width * scale));
            Lines.line(x, y, x, top); Fill.circle(x, y, width * 0.9f * scale); Draw.reset();
        });
    }
    static void beamTo(float x1, float y1, float x2, float y2, float width, Color c) {
        drawMulti((scale, ov, a, ss) -> {
            Draw.color(ov != null ? ov : c); Draw.alpha(a); Lines.stroke(Math.max(0.1f, width * scale));
            Lines.line(x1, y1, x2, y2); Fill.circle(x1, y1, width * 0.9f * scale); Draw.reset();
        });
    }
    static void drawSaw(float sx, float sy, float rad, float rot, float am) {
        additive(); Draw.color(Color.gold); Draw.alpha(0.55f * am);
        for (int k = 0; k < 2; k++) Drawf.tri(sx, sy, rad * 0.55f, rad * 1.5f, rot + k * 180);
        Draw.color(Color.white, Color.gold, 0.25f); Draw.alpha(0.95f * am);
        for (int k = 0; k < 2; k++) Drawf.tri(sx, sy, rad * 0.3f, rad * 1.15f, rot + k * 180);
        Draw.color(Color.gold); Draw.alpha(0.8f * am); Lines.stroke(1.5f); Lines.circle(sx, sy, rad * 0.6f);
        Draw.blend(); bloom(sx, sy, rad * 0.9f, Color.gold, 0.8f * am);
        Draw.color(Color.white); Fill.circle(sx, sy, rad * 0.16f); Draw.reset();
    }
    static void drawOrb(float ox, float oy, float r, float glow, float spinMul) {
        float g = cl(glow), len = r * (1.2f + 2.8f * g), w = r * (0.55f + 0.35f * g), rot = 90;
        additive(); Draw.color(Color.gold); Draw.alpha(0.5f);
        for (int k = 0; k < 3; k++) Drawf.tri(ox, oy, w * 1.7f, len * 1.25f, rot + k * 120);
        Draw.color(Color.white, Color.gold, 0.3f); Draw.alpha(0.95f);
        for (int k = 0; k < 3; k++) Drawf.tri(ox, oy, w, len, rot + k * 120);
        Draw.blend(); bloom(ox, oy, r * (1 + g), Color.gold, 0.7f);
        Draw.color(Color.white); Fill.circle(ox, oy, r * 0.35f); Draw.reset();
    }
    static void drawElectricRing(float x, float y, float r, float t) {
        Draw.color(Color.gold, Color.white, 0.3f); Draw.alpha(0.85f); Lines.stroke(1.2f);
        int seg = 10; float px = 0, py = 0;
        for (int i = 0; i <= seg; i++) {
            float a = i * 360f / seg, r2 = r + (float) Math.sin(t * 0.7 + i * 2.3) * 2;
            float cx = x + (float) Math.cos(a * DEG) * r2, cy = y + (float) Math.sin(a * DEG) * r2;
            if (i > 0) Lines.line(px, py, cx, cy);
            px = cx; py = cy;
        }
        Draw.reset();
    }
    static void drawStunMark(float x, float y, float r, float t, Color c) {
        Draw.color(c); Draw.alpha(0.9f); Lines.stroke(1.5f);
        Lines.arc(x, y, r + 4, 0.25f, 0); Lines.arc(x, y, r + 4, 0.25f, 180);
        Fill.square(x, y + r + 8 + (float) Math.sin(t * 0.2) * 2, 2, 45); Draw.reset();
    }

    // ---------- nâng cấp điện ảnh (bloom, tia sáng...)
    static void additive() { try { Draw.blend(Blending.additive); } catch (Throwable ignored) {} }
    static void bloom(float x, float y, float r, Color col, float a) {
        if (r <= 0 || a <= 0) return;
        additive();
        for (int i = 0; i < 4; i++) { Draw.color(col, Color.white, i * 0.24f); Draw.alpha(Math.min(1, a * (0.22f + 0.14f * i))); Fill.circle(x, y, r * (1 - i * 0.22f)); }
        Draw.blend(); Draw.reset();
    }
    static void flare(float x, float y, float len, float w, Color col, float rot, float a) {
        if (len <= 0 || a <= 0) return;
        additive(); Draw.color(col, Color.white, 0.5f); Draw.alpha(a);
        for (int k = 0; k < 2; k++) { float r = rot + k * 90; Drawf.tri(x, y, w, len, r); Drawf.tri(x, y, w, len, r + 180); }
        Draw.blend(); Draw.reset();
    }
    static void sparkBurst(float x, float y, int id, float p, int n, float dist, float len, Color col) {
        float fo = 1 - p;
        Draw.color(Color.white, col, p);
        for (int i = 0; i < n; i++) {
            float a = rs(id * 7 + i, 0, 360), d = (float) (dist * Math.pow(p, 0.55) * rs(id * 3 + i * 5, 0.55, 1));
            Lines.stroke(1.8f * fo + 0.3f);
            Lines.lineAngle(x + tx(a, d), y + ty(a, d), a, len * fo + 1);
        }
        Draw.reset();
    }

    // ---------- meteor sprite + sao rơi
    static final float METEOR_DX = 480, METEOR_DY = 520, STAR_MIN = 2, STAR_MAX = 3.5f;
    static TextureRegion meteorRegion = null;
    static TextureRegion getMeteorRegion() { if (meteorRegion == null) meteorRegion = Core.atlas.find("newex-meteor"); return meteorRegion; }
    static void drawMeteorFall(EffectContainer e, float f, float hx, float hy) {
        float ang = (float) (Math.atan2(METEOR_DY, METEOR_DX) / DEG), size = rs(e.id * 3 + 1, STAR_MIN, STAR_MAX) * TS, tail = rs(e.id * 5 + 2, STAR_MIN, STAR_MAX) * TS;
        float spin = e.fin() * 540 + rs(e.id, 0, 360);
        Draw.color(Color.black); Draw.alpha(0.28f * f); Fill.circle(e.x, e.y, 5 + 17 * f);
        Draw.color(Color.scarlet, Color.orange, f); Draw.alpha(0.5f + 0.4f * f); Lines.stroke(2); Lines.circle(e.x, e.y, 23 * (1 - f) + 6);
        additive();
        Draw.color(Color.white, Color.scarlet, 0.5f); Draw.alpha(0.7f); Drawf.tri(hx, hy, size * 0.5f, tail, ang);
        Draw.color(Color.orange); Draw.alpha(0.35f); Drawf.tri(hx, hy, size * 0.9f, tail * 0.8f, ang);
        for (int k = 1; k <= 6; k++) {
            float d = tail * k / 6f, sc = 1 - k / 7f;
            Draw.color(Color.orange, Color.scarlet, k / 6f); Draw.alpha(0.5f * sc);
            for (int a2 = 0; a2 < 4; a2++) Drawf.tri(hx + tx(ang, d), hy + ty(ang, d), size * 0.2f * sc, size * 0.8f * sc, spin - k * 25 + a2 * 90);
        }
        Draw.color(Color.orange, Color.white, 0.3f); Draw.alpha(0.6f);
        for (int a2 = 0; a2 < 4; a2++) Drawf.tri(hx, hy, size * 0.34f, size * 1.25f, spin + a2 * 90);
        Draw.color(Color.white); Draw.alpha(0.95f);
        for (int a2 = 0; a2 < 4; a2++) Drawf.tri(hx, hy, size * 0.22f, size * (a2 % 2 == 0 ? 1 : 0.7f), spin + a2 * 90);
        Draw.blend();
        bloom(hx, hy, size * 1.1f, Color.orange, 0.9f);
        Draw.color(Color.white); Fill.circle(hx, hy, size * 0.12f);
        Draw.color(Color.white, Color.orange, 0.5f); Lines.stroke(1.6f);
        for (int i = 0; i < 14; i++) {
            float a = ang + rs(e.id * 5 + i, -30, 30), d = 10 + tail * 1.4f * ((e.fin() * 6 + i * 0.29f) % 1);
            Lines.lineAngle(hx + tx(ang, d) + tx(a + 90, rs(i, -8, 8)), hy + ty(ang, d), a, 8 * (1 - d / (tail * 1.5f)));
        }
        Draw.reset();
    }
    static final float AST_SPRITE_SCALE = 2.15f, AST_SPRITE_ROT = 0;
    static void drawAsteroidSprite(float px, float py, float rad, double seed, float ap) {
        TextureRegion reg = getMeteorRegion();
        if (reg == null || !reg.found()) { drawAsteroidBody(px, py, rad, seed); return; }
        float d = rad * AST_SPRITE_SCALE, rot = AST_SPRITE_ROT + rs(seed, 0, 360) + ap * 50;
        bloom(px, py, rad * 1.25f, Color.orange, 0.35f + 0.4f * ap);
        Draw.reset(); Draw.rect(reg, px, py, d, d, rot);
        Draw.color(Color.scarlet, Color.orange, ap); Draw.alpha(0.25f + 0.35f * ap);
        additive();
        Fill.circle(px, py, rad * 1.02f);
        Draw.blend(); Draw.reset();
    }

    // ---------- hiệu ứng (tạo lúc nạp lớp, giống JS tạo lúc nạp script)
    static final Effect FX_SPHERE = new Effect(14, 100, e -> {
        Draw.color(Color.white, e.color, e.fin()); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, 3 + 9 * e.finpow()); Draw.reset();
    });
    static final Effect FX_SHIELD_BREAK = new Effect(40, 400, e -> {
        Draw.color(e.color, Color.white, e.fout()); Lines.stroke(3 * e.fout());
        Lines.poly(e.x, e.y, 6, e.rotation * (1 + 0.4f * e.finpow()), Time.time * 0.3f);
        for (int i = 0; i < 6; i++) {
            float a = i * 60 + 15, r1 = e.rotation * (1 + 0.2f * e.fin()), r2 = r1 + 14 * e.fout();
            Lines.line(e.x + tx(a, r1), e.y + ty(a, r1), e.x + tx(a, r2), e.y + ty(a, r2));
        }
        Draw.reset();
    });
    static final Effect FX_MAGNETIC = new Effect(30, 100, e -> {
        Draw.color(e.color, Color.white, e.fin()); Lines.stroke(1 + 2 * e.fin()); Lines.circle(e.x, e.y, 22 * e.fout());
        for (int i = 0; i < 4; i++) {
            float a = i * 90 + 45, r1 = 30 * e.fout(), r2 = 16 * e.fout();
            Lines.line(e.x + tx(a, r1), e.y + ty(a, r1), e.x + tx(a, r2), e.y + ty(a, r2));
        }
        Draw.reset();
    });
    static final Effect FX_BLADE_SLASH = new Effect(12, 100, e -> {
        Draw.color(Color.white, e.color, e.fin()); Lines.stroke(3 * e.fout());
        Lines.lineAngleCenter(e.x, e.y, e.rotation, 26 * e.finpow()); Lines.lineAngleCenter(e.x, e.y, e.rotation + 90, 14 * e.finpow()); Draw.reset();
    });
    static final Effect FX_EMP = new Effect(55, 6000, e -> {
        float r = e.rotation * e.finpow();
        Draw.color(e.color, Color.white, e.fout()); Lines.stroke(6 * e.fout()); Lines.circle(e.x, e.y, r);
        Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, r * 0.7f);
        for (int i = 0; i < 18; i++) {
            float a = i * 20 + rs(e.id + i, -6, 6), r2 = r * 0.82f;
            Lines.line(e.x + tx(a, r2), e.y + ty(a, r2), e.x + tx(a, r), e.y + ty(a, r));
        }
        Draw.reset();
    });
    static final Effect FX_BLASTER = new Effect(16, 1700, e -> {
        float split = 0.6f;
        if (e.fin() < split) {
            float fall = e.fin() / split, head = e.y + 520 * (1 - fall);
            drawMulti((scale, ov, a, ss) -> {
                Draw.color(ov != null ? ov : e.color); Draw.alpha(a); Lines.stroke(5 * scale);
                Lines.line(e.x, head, e.x, head + 110); Fill.circle(e.x, head, 4 * scale); Draw.reset();
            });
        } else {
            float pr = (e.fin() - split) / (1 - split);
            Draw.color(Color.white, e.color, pr); Lines.stroke(3 * (1 - pr)); Lines.circle(e.x, e.y, 6 + 16 * pr);
            Draw.alpha(1 - pr); Fill.circle(e.x, e.y, 6 * (1 - pr));
        }
        Draw.reset();
    });
    static final Effect FX_METEOR = new Effect(45, 900, e -> {
        float split = 0.6f;
        if (e.fin() < split) {
            float f = e.fin() / split;
            drawMeteorFall(e, f, e.x + METEOR_DX * (1 - f), e.y + METEOR_DY * (1 - f));
        } else drawMeteorImpact(e.x, e.y, e.rotation > 0 ? e.rotation : 30, (e.fin() - split) / (1 - split));
        Draw.reset();
    });
    static final Effect FX_DEATH_LASER = new Effect(12, 1700, e -> {
        float fade = Math.min(1, e.fout() * 2), pulse = 1 + 0.2f * (float) Math.sin(Time.time * 0.5);
        skyBeam(e.x, e.y, 18 * pulse * fade, 1, SKY_HEIGHT, e.color);
        Draw.color(e.color); Draw.alpha(0.6f * fade); Lines.stroke(2); Lines.poly(e.x, e.y, 16, e.rotation, Time.time * 0.5f);
        Draw.alpha(0.08f * fade); Fill.circle(e.x, e.y, e.rotation); Draw.reset();
    });
    static final Effect FX_DEATH_BLAST = new Effect(40, 1700, e -> {
        Draw.color(Color.white, e.color, e.fin()); Lines.stroke(8 * e.fout()); Lines.circle(e.x, e.y, e.rotation * e.finpow());
        Draw.alpha(0.35f * e.fout()); Fill.circle(e.x, e.y, e.rotation * 0.5f * e.fout());
        skyBeam(e.x, e.y, 32 * e.fout(), e.fout(), SKY_HEIGHT, e.color); Draw.reset();
    });
    static final Effect FX_HEAL = new Effect(45, 400, e -> {
        Draw.color(e.color, Color.white, e.fout()); Lines.stroke(3 * e.fout()); Lines.circle(e.x, e.y, e.rotation * 0.5f + e.rotation * e.finpow());
        for (int i = 0; i < 6; i++) {
            float a = i * 60, r2 = e.rotation * 0.6f, px = e.x + tx(a, r2), py = e.y + ty(a, r2) + 30 * e.fin(), sz = 3.5f * e.fout();
            Lines.stroke(1.5f * e.fout() + 0.5f); Lines.line(px - sz, py, px + sz, py); Lines.line(px, py - sz, px, py + sz);
        }
        Draw.reset();
    });
    static final Effect FX_AST_FALL = new Effect(AST_TRAVEL, 40000, e -> {
        float acx = Vars.world.unitWidth() / 2f, acy = Vars.world.unitHeight() / 2f, ap = e.fin();
        float px = lerp(e.x, acx, ap), py = lerp(e.y, acy, ap), rad = AST_BODY_TILES * TS;
        drawAsteroidSprite(px, py, rad, Math.floor(e.rotation * 100), ap);
        drawMulti((scale, ov, a, ss) -> {
            Draw.color(ov != null ? ov : Color.orange); Draw.alpha(a * 0.7f); Lines.stroke(30 * ss); Lines.poly(px, py, 64, rad, 0); Draw.reset();
        });
        Draw.reset();
    });
    static final Effect FX_AST_FLASH = new Effect(AST_FLASH, 100000, e -> {
        Draw.z(Layer.overlayUI + 10); Draw.color(Color.white);
        float left = 1 - cl((e.fin() - AST_FLASH_HOLD) / (1 - AST_FLASH_HOLD));
        Draw.alpha(left * left * (3 - 2 * left));
        Fill.rect(Core.camera.position.x, Core.camera.position.y, Core.camera.width * 1.3f, Core.camera.height * 1.3f); Draw.reset();
    });
    static final Effect FX_AST_QUAKE = new Effect(AST_QUAKE, 100000, e -> { if (!Vars.state.isPaused()) Vars.renderer.shake((float) (AST_SHAKE * Math.pow(e.fout(), 0.6)), 6); });
    static final Effect FX_AST_IMPACT = new Effect(260, 100000, e -> {
        float reach = Math.max(e.rotation, 400), p = e.fin(), r = reach * 1.05f * p, a = (float) (1 - Math.pow(p, 4));
        additive();
        for (int i = 0; i < 10; i++) {
            float rr2 = r - i * 14;
            if (rr2 <= 0) break;
            float k = 1 - i / 10f;
            Draw.color(Color.white, Color.orange, i / 10f); Draw.alpha(0.85f * a * k * k); Lines.stroke(16 * k + 2); Lines.circle(e.x, e.y, rr2);
        }
        Draw.blend(); Draw.reset();
    });
    static final Effect FX_AST_MUSHROOM = new Effect(MUSHROOM_TICKS, 12000, e -> { drawMushroom(e.x, e.y, Math.max(e.rotation, 80), e.fin()); });
    static final Effect FX_BLAST = new Effect(30, 400, e -> {
        float sz = Math.max(6, e.rotation);
        Draw.color(Color.white, Color.orange, e.fin()); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, sz * (0.6f + e.finpow()));
        Draw.alpha(e.fout() * 0.8f); Fill.circle(e.x, e.y, Math.max(4, sz * 0.5f) * e.fout()); Draw.reset();
    });
    static final Effect FX_PURPLE_BURST = new Effect(30, 200, e -> {
        Draw.color(e.color, Color.white, e.fout()); Lines.stroke(2 * e.fout()); Lines.poly(e.x, e.y, 6, 4 + 18 * e.finpow(), e.fin() * 90);
        for (int i = 0; i < 4; i++) {
            float a = i * 90 + e.fin() * 45;
            Fill.poly(e.x + tx(a, 8 + 16 * e.finpow()), e.y + ty(a, 8 + 16 * e.finpow()), 3, 3 * e.fout(), a);
        }
        Draw.reset();
    });
    static final Effect FX_SHAPE = new Effect(45, 200, e -> {
        Draw.color(e.color, Color.white, e.fout()); Lines.stroke(2 * e.fout());
        int sides = 3 + (int) Math.floor(3 * e.fin());
        Lines.poly(e.x, e.y, sides, 4 + 16 * e.finpow(), e.fin() * 180); Lines.poly(e.x, e.y, sides, 2 + 10 * e.finpow(), -e.fin() * 180); Draw.reset();
    });
    static final Effect FX_MIND = new Effect(55, 200, e -> {
        float ey = e.y + e.rotation * 0.5f + 8 + 14 * e.fin(), open = e.fslope(), w = 10, n = 10;
        Draw.color(e.color, Color.white, 0.3f); Lines.stroke(1.8f * (0.3f + e.fout()));
        for (int sg = -1; sg <= 1; sg += 2) for (int i = 0; i < n; i++) {
            float u0 = -1 + 2 * i / n, u1 = -1 + 2 * (i + 1) / n;
            Lines.line(e.x + u0 * w, ey + sg * 5 * open * (1 - u0 * u0), e.x + u1 * w, ey + sg * 5 * open * (1 - u1 * u1));
        }
        Fill.circle(e.x, ey, 2.4f * open); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, e.rotation * 0.9f * e.fout() + 2); Draw.reset();
    });
    static final Effect FX_HYPNOSIS = new Effect(70, 8000, e -> {
        for (int k = 0; k < 3; k++) {
            float pr = cl(e.fin() * 1.5 - k * 0.18);
            if (pr <= 0) continue;
            Draw.color(e.color, Color.white, 1 - pr); Lines.stroke(4 * (1 - pr)); Lines.poly(e.x, e.y, 6, e.rotation * pr, k * 30);
            Draw.alpha(0.08f * (1 - pr)); Fill.poly(e.x, e.y, 6, e.rotation * pr, k * 30);
        }
        Draw.reset();
    });
    static final Effect FX_PLASMA = new Effect(6, 1700, e -> {
        float fl = 1 + 0.25f * (float) Math.sin(Time.time * 0.9 + e.id), a = e.fout();
        skyBeam(e.x, e.y, 9 * fl, a, SKY_HEIGHT, e.color);
        bloom(e.x, e.y, 38 * fl, e.color, 0.9f * a);
        Draw.color(Color.white, e.color, e.fin()); Draw.alpha(0.9f * a); Lines.stroke(2.2f * a); Lines.circle(e.x, e.y, 14 + 12 * e.fin());
        for (int i = 0; i < 6; i++) { float ang = rs(e.id * 7 + i + Math.floor(Time.time / 3), 0, 360), d = 10 + 26 * e.fin(); Lines.stroke(1.6f * a); Lines.lineAngle(e.x + tx(ang, d), e.y + ty(ang, d), ang, 10 * a + 2); }
        Draw.reset();
    });

    // ---------- upgradeFx: bọc renderer gốc bằng bloom/flare/tia lửa/rung màn hình
    interface Pre { void run(EffectContainer e, Color c); }
    static class Opt {
        float r = 30, from = 0, shake = 0, len = 8, flareRot = 0; boolean rot, glow, flash, ring; int smoke = 0, sparks = 0; Pre pre;
        Opt r(float v) { r = v; return this; } Opt rot() { rot = true; return this; } Opt glow() { glow = true; return this; }
        Opt flash() { flash = true; return this; } Opt ring() { ring = true; return this; } Opt smoke(int v) { smoke = v; return this; }
        Opt sparks(int v) { sparks = v; return this; } Opt len(float v) { len = v; return this; } Opt shake(float v) { shake = v; return this; }
        Opt from(float v) { from = v; return this; } Opt flareRot(float v) { flareRot = v; return this; } Opt pre(Pre p) { pre = p; return this; }
    }
    static void upgradeFx(Effect fx, Opt o) {
        final Cons<EffectContainer> orig = fx.renderer;
        fx.renderer = e -> {
            Color c = e.color == null ? Color.white : e.color;
            float R = o.rot ? Math.max(e.rotation, o.r) : o.r;
            float f0 = o.from; boolean on = e.fin() >= f0; float p = on ? (e.fin() - f0) / (1 - f0) : 0, fo = 1 - p;
            if (!on && o.pre != null) o.pre.run(e, c);
            if (on) {
                if (o.flash && p < 0.22f) { float k = 1 - p / 0.22f; bloom(e.x, e.y, R * (0.45f + 0.9f * p / 0.22f), c, k); flare(e.x, e.y, R * 2.2f * k, Math.max(2, R * 0.1f), c, o.flareRot, 0.9f * k); }
                if (o.glow) bloom(e.x, e.y, R * (0.35f + 0.35f * p) * fo + 2, c, 0.75f * fo);
                if (o.shake > 0 && p < 0.1f && !Vars.state.isPaused()) Effect.shake(o.shake * (1 - p / 0.1f), 8, e.x, e.y);
            }
            orig.get(e);
            if (on) {
                if (o.ring) { float f = Math.max(0, (p - 0.12f) / 0.88f); if (f > 0) { Draw.color(c, Color.white, 1 - f); Draw.alpha(0.8f * (1 - f)); Lines.stroke(2.5f * (1 - f)); Lines.circle(e.x, e.y, R * 1.1f * (float) Math.pow(f, 0.5)); Draw.reset(); } }
                if (o.smoke > 0) {
                    for (int i = 0; i < o.smoke; i++) {
                        float q = Math.min(1, p * 1.2f), a = rs(e.id + i * 13, 0, 360), d = (float) (R * 0.9f * Math.pow(q, 0.5) * rs(e.id * 3 + i, 0.3, 1));
                        Draw.color(Color.gray, Color.darkGray, q); Draw.alpha(0.5f * (1 - q) * (1 - q)); Fill.circle(e.x + tx(a, d), e.y + ty(a, d) + 18 * q, R * (0.12f + 0.22f * q));
                    }
                    Draw.reset();
                }
                if (o.sparks > 0) sparkBurst(e.x, e.y, e.id, p, o.sparks, R * 1.15f, o.len, c);
            }
        };
    }
    static boolean fxUpgraded = false;
    static void upgradeAllFx() {
        if (fxUpgraded) return; fxUpgraded = true;
        upgradeFx(FX_SPHERE, new Opt().r(14).glow());
        upgradeFx(FX_SHIELD_BREAK, new Opt().rot().r(60).flash().sparks(14).len(10).ring().shake(2));
        upgradeFx(FX_MAGNETIC, new Opt().r(26).glow().sparks(6).len(6));
        upgradeFx(FX_BLADE_SLASH, new Opt().r(20).flash().sparks(8).len(12));
        upgradeFx(FX_EMP, new Opt().rot().r(80).flash().sparks(28).len(16).ring().shake(5));
        upgradeFx(FX_BLASTER, new Opt().smoke(5).r(50).from(0.6f).flash().sparks(14).len(12).ring().shake(2).flareRot(90)
            .pre((e, c) -> { float head = e.y + 520 * (1 - e.fin() / 0.6f); bloom(e.x, head, 22, c, 0.7f); flare(e.x, head, 46, 5, c, 90, 0.6f); }));
        upgradeFx(FX_METEOR, new Opt().rot().r(70).from(0.6f).flash().sparks(26).len(18).ring().shake(5).smoke(9));
        upgradeFx(FX_DEATH_LASER, new Opt().rot().r(40).glow().sparks(6).len(8));
        upgradeFx(FX_DEATH_BLAST, new Opt().smoke(8).rot().r(70).flash().sparks(24).len(14).ring().shake(6));
        upgradeFx(FX_HEAL, new Opt().rot().r(40).glow().sparks(8).len(6));
        upgradeFx(FX_BLAST, new Opt().smoke(5).r(46).flash().sparks(14).len(10).ring());
        upgradeFx(FX_PURPLE_BURST, new Opt().r(30).flash().sparks(12).len(10));
        upgradeFx(FX_SHAPE, new Opt().r(30).glow().sparks(10).len(8));
        upgradeFx(FX_MIND, new Opt().r(30).glow().sparks(10).len(8).ring());
        upgradeFx(FX_HYPNOSIS, new Opt().rot().r(60).glow().sparks(16).len(14).ring());
    }

    // ---------- registry: bar/hook/đạn truy cập building qua đây (id -> building)
    static final java.util.HashMap<Integer, ThermionBuild> SELF = new java.util.HashMap<>();
    static float bonusFraction(float cap) { return Math.min(Math.max(cap, 0), BONUS_CAP) / 1000000f * BONUS_PER_M; }
    static float nearestYellowCapacity(mindustry.game.Team team, float px, float py) {
        ThermionBuild best = null; float bd = 1e30f;
        java.util.Iterator<java.util.Map.Entry<Integer, ThermionBuild>> it = SELF.entrySet().iterator();
        while (it.hasNext()) {
            ThermionBuild t = it.next().getValue();
            if (!t.isValid()) { it.remove(); continue; }
            if (t.team != team || t.getForm() != YELLOW) continue;
            float d = (t.x - px) * (t.x - px) + (t.y - py) * (t.y - py);
            if (d < bd) { bd = d; best = t; }
        }
        return best == null ? 0 : best.gridCapacity();
    }

    // ---------- status effect (tạo trong create())
    static final StatusEffect[] slowEffects = new StatusEffect[MAX_LEVEL];
    static StatusEffect weakened;
    static final mindustry.entities.bullet.BulletType[] orbBullets = new mindustry.entities.bullet.BulletType[MAX_LEVEL];
    static UnitType droneType, droneV2Type;
    static Block thermion;

    // ---------- Orb bullet
    static class OrbBullet extends BasicBulletType {
        OrbBullet(float dmg) {
            super(24f, dmg);
            width = ORB_R * 2; height = ORB_R * 2; hitSize = ORB_R * 1.8f; lifetime = 80;
            homingPower = 0.35f; homingRange = TS * 400;
            backColor = YELLOW_COLOR; frontColor = Color.white; trailColor = YELLOW_COLOR; trailLength = 24; trailWidth = ORB_R * 0.6f;
            LightningBulletType lb = new LightningBulletType();
            lb.damage = dmg / 10; lb.lightningDamage = dmg / 10; lb.lightningColor = YELLOW_COLOR; lb.lightningLength = 7;
            intervalBullet = lb; bulletInterval = 6; intervalBullets = 1; intervalRandomSpread = 360;
            collidesGround = true; collidesAir = true;
            arc.audio.Sound hs = sndOrbHit(); if (hs != null) hitSound = hs;
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            if (Vars.net.client() || !(entity instanceof Unit)) return;
            Unit u = (Unit) entity;
            if (u.team == b.team || !u.isValid()) return;
            float f = bonusFraction(nearestYellowCapacity(b.team, b.x, b.y));
            if (f > 0) u.damage(u.maxHealth * f);
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            if (Vars.net.client() || build == null || build.team == b.team || !build.isValid()) return;
            float f = bonusFraction(nearestYellowCapacity(b.team, b.x, b.y));
            if (f > 0) build.damage(build.maxHealth * f);
        }
        @Override public void draw(Bullet b) { Draw.z(ORB_Z); drawTrail(b); drawOrb(b.x, b.y, ORB_R, 1, 2); }
    }

    // ---------- Drone
    static class DroneBullet extends BasicBulletType {
        final float maxPct;
        DroneBullet(float dmg, float maxPct, float spd, float life, float w, float h, int pierces) {
            super(spd, dmg);
            this.maxPct = maxPct;
            width = w; height = h; lifetime = life; backColor = PURPLE_COLOR; frontColor = Color.white;
            collidesAir = true; collidesGround = true;
            if (pierces > 1) { pierce = true; pierceBuilding = true; pierceCap = pierces; }
        }
        @Override public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            if (maxPct <= 0 || Vars.net.client() || !(entity instanceof Unit)) return;
            Unit u = (Unit) entity;
            if (u.team == b.team || !u.isValid()) return;
            u.damage(u.maxHealth * (float) Math.random() * maxPct);
        }
        @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            if (maxPct <= 0 || Vars.net.client() || build == null || build.team == b.team || !build.isValid()) return;
            build.damage(build.maxHealth * (float) Math.random() * maxPct);
        }
    }
    static void setupDroneBody(UnitType t, float hp) {
        UnitType fl = UnitTypes.flare;
        t.constructor = UnitEntity::create; t.flying = true; t.health = hp; t.speed = fl.speed; t.accel = fl.accel; t.drag = fl.drag;
        t.rotateSpeed = fl.rotateSpeed; t.hitSize = fl.hitSize; t.engineOffset = fl.engineOffset; t.engineSize = fl.engineSize;
        t.lowAltitude = fl.lowAltitude; t.circleTarget = false; t.itemCapacity = 0;
    }
    static Weapon droneGun(BulletType shot) {
        Weapon w = new Weapon();
        w.reload = 50; w.x = 0; w.y = 0; w.top = false; w.mirror = false; w.inaccuracy = 4;
        w.shoot.shots = 12; w.shoot.shotDelay = 2;
        arc.audio.Sound s = sndMachineGun(); if (s != null) w.shootSound = s;
        w.bullet = shot; return w;
    }
    /** Drone hình ngôi sao 2 cánh: ghi đè draw() nên không dùng sprite của UnitType. */
    static void drawDroneStar(Unit unit, boolean big) {
        if (unit.inFogTo(Vars.player.team())) return;
        float len = big ? 22 : 15, w = len * 0.28f, rot = 90, z = Draw.z();
        Draw.z(Layer.flyingUnit);
        additive();
        Draw.color(PURPLE_COLOR); Draw.alpha(0.55f);
        for (int k = 0; k < 3; k++) Drawf.tri(unit.x, unit.y, w * 1.9f, len * 1.3f, rot + k * 120);
        Draw.color(Color.white, PURPLE_COLOR, 0.45f); Draw.alpha(0.95f);
        for (int k = 0; k < 3; k++) Drawf.tri(unit.x, unit.y, w, len, rot + k * 120);
        Draw.blend();
        bloom(unit.x, unit.y, len * 0.6f, PURPLE_COLOR, 0.6f);
        Draw.color(PURPLE_COLOR); Fill.circle(unit.x, unit.y, w * 1.35f);
        Draw.color(Color.black); Fill.circle(unit.x, unit.y, w * 1.05f);
        Draw.reset(); Draw.z(z);
    }
    static void createDrones() {
        droneType = new UnitType("thermion-drone") { @Override public void draw(Unit unit) { drawDroneStar(unit, false); } };
        setupDroneBody(droneType, 200);
        droneType.weapons.add(droneGun(new DroneBullet(25, 0, 4, 45, 6, 10, 1)));
        droneV2Type = new UnitType("thermion-drone-v2") { @Override public void draw(Unit unit) { drawDroneStar(unit, true); } };
        setupDroneBody(droneV2Type, 800);
        droneV2Type.weapons.add(droneGun(new DroneBullet(90, 0.01f, 4, 45, 6, 10, 1)));
        Weapon w = new Weapon();
        w.reload = 60; w.x = 7; w.y = 0; w.top = false; w.mirror = true; w.alternate = true; w.inaccuracy = 0;
        arc.audio.Sound s = sndDroneLaser(); if (s != null) w.shootSound = s;
        w.bullet = new DroneBullet(400, 0.15f, 7, 40, 8, 28, 7);
        droneV2Type.weapons.add(w);
    }
    static AIController makeDroneAI(ThermionBuild owner) {
        return new AIController() {
            @Override public void updateUnit() {
                if (!owner.isValid() || owner.getForm() != PURPLE) {
                    if (unit != null && unit.isValid() && !Vars.net.client()) unit.kill();
                    return;
                }
                float r = owner.affectRadiusWorld();
                if (target != null && dst(target.getX(), target.getY(), owner.x, owner.y) > r) target = null;
                super.updateUnit();
            }
            @Override public Teamc findTarget(float x, float y, float range, boolean air, boolean ground) {
                if (!owner.isValid()) return null;
                float r = owner.affectRadiusWorld();
                if (r <= 0) return null;
                return Units.closestTarget(unit.team, owner.x, owner.y, r, u -> u.checkTarget(air, ground), t -> ground);
            }
            @Override public void updateMovement() {
                if (target != null && unit.hasWeapons()) {
                    moveTo(target, unit.type.range * 0.8f);
                    unit.lookAt(target);
                } else if (owner.tile != null && owner.tile.build != null) {
                    moveTo(owner.tile.build, thermion.size * TS * 1.5f);
                }
            }
        };
    }

    // ---------- mô tả skill (Info) — sinh tự động từ thermion.js dòng 678-708
    static class Skill {
        final String name, vi, en; final int lv; final boolean active;
        Skill(String name, int lv, boolean active, String vi, String en) { this.name = name; this.lv = lv; this.active = active; this.vi = vi; this.en = en; }
    }
    static final Skill[][] SKILLS = {
        {
            new Skill("ElectrifiedField", 1, false, "Làm chậm mọi kẻ địch trong phạm vi (10/15/20/30/40% theo cấp); mỗi kẻ địch bị chậm có vòng điện bao quanh.", "Slows every enemy in range (10/15/20/30/40% by level); each slowed enemy is ringed with electricity."),
            new Skill("ElectricSphere", 2, false, "Mỗi kẻ địch trong phạm vi tạo một quả cầu điện ở vị trí ngẫu nhiên trong vòng 15 tile quanh khối. Nạp đủ thì mọi quả bay tới mục tiêu gây sát thương kèm sét. Tối đa 10/15/30/50 quả mỗi lượt (ưu tiên địch gần). Nội tại: mỗi lần trúng gây thêm % máu tối đa của mục tiêu: +0.5% cho mỗi 1 triệu sức chứa pin, tính tới 10 triệu.", "Each enemy in range spawns an electric sphere at a random spot within 15 tiles of the block. Once charged, every sphere flies to its target dealing damage plus lightning. Max 10/15/30/50 spheres per volley (nearest enemies first). Passive: each hit deals extra max-HP% damage: +0.5% per 1 million battery capacity, counted up to 10 million."),
            new Skill("EnergyShield", 3, false, "Khiên bán kính 5/10/15 tile (Lv3/4/5) chặn đạn địch và đẩy đơn vị địch ra ngoài. Máu khiên = 50/75/100% dung tích pin của lưới. Mới xuất hiện hoặc hồi sau khi vỡ chỉ có 10% máu, tự sửa mỗi 2 giây bằng điện trong pin (tối đa 10% sức chứa lưới mỗi lần).", "Shield of 5/10/15 tiles radius (Lv3/4/5) that blocks enemy bullets and pushes enemy units outward. Shield HP = 50/75/100% of the grid's battery capacity. A fresh or recovered shield starts with only 10% HP and repairs itself every 2 seconds using battery power (up to 10% of grid capacity each time)."),
            new Skill("MagneticField", 4, false, "Mỗi giây gây sát thương chuẩn 1.5% (Lv5: 2%) máu tối đa lên mọi kẻ địch trong phạm vi.", "Every second deals true damage equal to 1.5% (Lv5: 2%) of max HP to every enemy in range."),
            new Skill("DualBlade", 4, false, "Chế tạo 1 lưỡi cưa (Lv5: 2 lưỡi) tự bay tới kẻ địch gần nhất, bám vào cắt 4 nhát/giây rồi tìm mục tiêu mới. Nội tại giống ElectricSphere nhưng hiệu quả bằng một nửa.", "Builds 1 saw blade (Lv5: 2) that flies to the nearest enemy, latches on and cuts 4 times per second, then picks a new target. Same passive as ElectricSphere at half effectiveness."),
            new Skill("ElectromagneticPulse", 5, true, "Chủ động: vô hiệu hóa mọi đơn vị và công trình địch trong phạm vi 5 giây. Hồi 120 giây.", "Active: disables all enemy units and buildings in range for 5 seconds. Cooldown 120 seconds.")
        },
        {
            new Skill("Blaster", 1, false, "Mỗi giây, mỗi kẻ địch có xác suất bị khóa và hứng 1..level tia laser từ trời (cách nhau 15 tick). Mỗi tia = sát thương gốc x (1 + số tia/5); tia cuối thêm 0.5 x level% máu tối đa. Địch xung quanh nhận 1/3 sát thương.", "Every second, each enemy has a chance to be locked on and hit by 1..level orbital laser beams (15 ticks apart). Each beam = base damage x (1 + beams/5); the last beam adds 0.5 x level% of max HP. Nearby enemies take 1/3 of the damage."),
            new Skill("Meteor", 2, false, "Mỗi giây, mỗi kẻ địch có xác suất bị 3..level x 3 thiên thạch rơi xuống; mỗi quả nổ bán kính 4 tile (% máu tối đa + sát thương số), để lại 3 vũng (75% xỉ, 25% dầu).", "Every second, each enemy has a chance to be hit by 3..level x 3 meteors; each explodes in a 4-tile radius (max-HP% + flat damage) and leaves 3 puddles (75% slag, 25% oil)."),
            new Skill("DeathLaser", 3, false, "Khóa mục tiêu bằng tia laser từ trời bám theo và gây sát thương mỗi giây (địch khác trong vùng nhận 1/2). Quanh tia (2 x level tile): đơn vị địch giảm 50% hoạt động, công trình chậm 50%. Hết tia thì nổ gây (2..5) x level% máu tối đa.", "Locks onto a target with a tracking orbital laser that deals damage every second (other enemies in the area take 1/2). Around the beam (2 x level tiles): enemy units are 50% less active and buildings 50% slower. When the beam ends it explodes for (2..5) x level% of max HP."),
            new Skill("Limitless", 4, false, "Trong vùng 1/3 phạm vi, mọi đạn không cùng team mất 5% (Lv5: 10%) tốc độ ban đầu mỗi giây.", "Within 1/3 of the range, every non-allied bullet loses 5% (Lv5: 10%) of its initial speed per second."),
            new Skill("RCT", 4, false, "Định kỳ (15 giây, Lv5: 10 giây) hồi 50% (Lv5: 100%) máu tối đa.", "Periodically (every 15 s, Lv5: 10 s) restores 50% (Lv5: 100%) of max HP."),
            new Skill("Asteroid", 5, true, "Chủ động: gồng 1 phút (khóa đổi dạng và phạm vi, địch trong phạm vi bị đóng băng, đạn địch đứng yên, không phụ thuộc lưới điện), rồi tiểu hành tinh bay tới giữa bản đồ tiêu diệt mọi đơn vị/công trình ngoài phạm vi (trừ Thermion). Sau đó vào dạng Death 10 phút rồi trở về Blue. Hồi 300 giây.", "Active: charge for 1 minute (locks form change and range, enemies in range are frozen, enemy bullets stand still, independent of the power grid), then an asteroid flies to the map center and destroys every unit/building outside the range (except Thermion). Afterwards enters Death form for 10 minutes, then returns to Blue. Cooldown 300 seconds.")
        },
        {
            new Skill("Illusion", 1, false, "Định kỳ mỗi kẻ địch có xác suất rơi vào ảo giác, bị vô hiệu hóa (thời gian giảm theo giáp: x(1 - giáp/100)).", "Periodically each enemy has a chance to fall into an illusion and be disabled (duration reduced by armor: x(1 - armor/100))."),
            new Skill("Drone", 2, false, "Định kỳ triệu hồi 1 drone (không giới hạn). Lv2-3: Drone thường (200 máu); Lv4+: Drone V2 (800 máu, súng máy + 2 laser xuyên). Drone biến mất khi Thermion bị phá hủy hoặc rời dạng Purple.", "Periodically summons 1 drone (unlimited). Lv2-3: normal Drone (200 HP); Lv4+: Drone V2 (800 HP, machine gun + 2 piercing lasers). Drones vanish when Thermion is destroyed or leaves Purple form."),
            new Skill("Shapeshifter", 3, false, "Định kỳ mỗi kẻ địch có xác suất bị khóa; sau 2 giây một đơn vị đồng minh cùng loại với chỉ số y hệt xuất hiện tại đó.", "Periodically each enemy has a chance to be marked; after 2 seconds an allied unit of the same type with identical stats appears there."),
            new Skill("Hypnosis", 3, false, "Bị động: khi bị phá hủy ở dạng Purple, mọi đơn vị địch trong phạm vi thành đồng minh.", "Passive: when destroyed in Purple form, all enemy units in range become allies."),
            new Skill("MindControl", 4, false, "Định kỳ mỗi kẻ địch có xác suất bị biến thành đồng minh.", "Periodically each enemy has a chance to be turned into an ally."),
            new Skill("Mega Hypnosis", 5, true, "Chủ động: kích hoạt Hypnosis không cần bị phá hủy. Hồi 180 giây.", "Active: triggers Hypnosis without needing to be destroyed. Cooldown 180 seconds.")
        },
    };
    static final String[][] FORM_ROLE = {
        {"Phòng thủ bằng điện. Làm chậm và giật điện kẻ địch, dựng khiên chặn đạn và dùng lưỡi cưa xẻ mục tiêu. Có phần thưởng máu tối đa lớn nhất.", "Electric defense. Slows and shocks enemies, raises a bullet-blocking shield and shreds targets with saw blades. Has the largest max-HP bonus."},
        {"Pháo kích từ quỹ đạo. Laser, thiên thạch và tia tử thần giáng từ trời, đạn địch bị làm chậm và Thermion tự hồi máu. Level 5 mở khóa Asteroid.", "Orbital bombardment. Lasers, meteors and death beams strike from the sky, enemy bullets are slowed and Thermion heals itself. Level 5 unlocks Asteroid."},
        {"Kiểm soát và triệu hồi. Vô hiệu hóa, chiêu mộ và nhân bản kẻ địch, triệu hồi drone chiến đấu. Khi bị phá hủy sẽ chiêu mộ mọi thứ xung quanh.", "Control and summoning. Disables, recruits and clones enemies, and summons combat drones. When destroyed it recruits everything around it."}
    };

    // ---------- UI helper
    static float dialogWidth() { return Math.min(480f, Core.graphics.getWidth() / Scl.scl(1f) * 0.92f); }
    static void dialogPane(BaseDialog d, Cons<Table> build) { d.cont.pane(Styles.noBarPane, build).size(dialogWidth(), Core.graphics.getHeight() / Scl.scl(1f) * 0.62f); }
    static Cell<Label> wrap(Table t, String text, float w) { Cell<Label> c = t.add(text).width(w).left(); c.get().setWrap(true); return c; }
    static void section(Table t, String title) {
        t.table(h -> { h.add("[#" + Pal.accent + "]" + title + "[]").left().row(); h.image().color(Pal.accent).height(3).growX(); }).left().padTop(14).padBottom(6).row();
    }
    static void addBack(BaseDialog d) { d.addCloseButton(); d.buttons.clear(); d.buttons.button(T("Trở về", "Back"), Styles.flatBordert, () -> d.hide()).size(210, 64); }

    // ---------- thermion-glow: phát sáng theo màu dạng + effect theo dạng
    static TextureRegion glowRegion = null;
    static TextureRegion getGlow() { if (glowRegion == null) glowRegion = Core.atlas.find("newex-thermion-glow"); return glowRegion; }
    static void drawGlow(float x, float y, Color c, float a, float sc) {
        TextureRegion r = getGlow();
        if (r == null || !r.found()) return;
        float w = 64; try { w = r.width * r.scl(); } catch (Throwable ignored) {}
        w *= sc; Draw.color(c); Draw.alpha(Math.min(1, Math.max(0, a))); Draw.rect(r, x, y, w, w); Draw.reset();
    }
    static final Effect FX_GLOW_ZOOM = new Effect(50, 400, e -> { drawGlow(e.x, e.y, e.color, 0.7f * e.fout(), 1 + 0.18f * e.finpow()); });
    static final Effect FX_GLOW_IN = new Effect(50, 400, e -> {
        float d = 52 * e.fout(), px = e.x + tx(e.rotation, d), py = e.y + ty(e.rotation, d);
        additive(); Draw.color(Color.gold, Color.white, e.fin()); Lines.stroke(2.2f * e.fout() + 0.4f);
        Lines.lineAngle(px, py, e.rotation + 180, 6 + 10 * e.fout()); Fill.circle(px, py, 2.2f * e.fout() + 0.5f);
        Draw.blend(); Draw.reset();
    });
    /** rotation = góc(từ drone về lõi) + 360 * khoảng cách */
    static final Effect FX_GLOW_DRONE = new Effect(45, 400, e -> {
        float ang = e.rotation % 360, dist = (float) Math.floor(e.rotation / 360), cx = e.x + tx(ang, dist), cy = e.y + ty(ang, dist);
        float mv = cl((e.fin() - 0.3) / 0.7), sc = lerp(1, 0.25, cl(e.fin() / 0.5));
        drawGlow(lerp(cx, e.x, mv * mv * (3 - 2 * mv)), lerp(cy, e.y, mv * mv * (3 - 2 * mv)), e.color, 0.75f * (1 - cl((e.fin() - 0.8) / 0.2)), sc);
    });

    // ---------- trạng thái cutscene (khai báo trước vì Build gọi startCutscene)
    static final class Plasma { Unit tgt; float x, y, life, acc, fxT, snd, dmg, ex, rad, speed; }
    static final class Laser { Unit target; float tx, ty, timeLeft, secTimer, weakenTimer, fxTimer, loopTimer; }

    // ======================================================================== Building
    public static class ThermionBuild extends Building {
        int level = 1, form = YELLOW, pendingForm = -1;
        float formChangeTimer = 0, formCooldown = 0;
        final float[] timers = new float[9];
        float secondTimer = 0; boolean secTick = false;
        float sphereCharge = 0; int orbCount = 0;
        float shield = 0, shieldMax = 0, shieldCooldown = 0, shieldRegenTimer = 0, shieldHitCd = 0; boolean shieldDirty = true;
        final float[] blades = new float[2], bladeX = new float[2], bladeY = new float[2], bladeHit = new float[2];
        final boolean[] bladeInit = new boolean[2]; final Unit[] bladeTarget = new Unit[2];
        int orbSeed = 0; float skillCd = 1, empActiveTimer = 0; boolean upgrading = false; final int[] paid = new int[MAX_COST_ITEMS];
        ArrayList<Plasma> plasmas = new ArrayList<>(); float plasmaCd = 0;
        ArrayList<Laser> lasers = new ArrayList<>();
        int asteroidPhase = AST_NONE; float asteroidTimer = 0, asteroidRadius = 0, asteroidTx = 0, asteroidTy = 0;
        float deathTimer = 0, chargeLoopTimer = 0, rangeShown = 0; long rangeStamp = -1;
        ArrayList<Unit> drones = new ArrayList<>(); ArrayList<Integer> savedDroneIds = new ArrayList<>();
        float ringRadius = 0, ringStamp = -1000;
        float ceStamp = -1e9f; ArrayList<Unit> ceList = new ArrayList<>();
        final Color glowCol = new Color();

        int getForm() { return form; }
        int getLevel() { return level; }
        void fillMarkColor(Color c) { c.set(formColor(form)); if (pendingForm >= 0) c.lerp(formColor(pendingForm), changeProgress()); }

        // ---------- điện / phạm vi
        float totalPowerProduction() { return (power != null && power.graph != null) ? power.graph.getLastPowerProduced() * 60 : 0; }
        float totalPowerUse() { return (power != null && power.graph != null) ? power.graph.getLastPowerNeeded() * 60 : 0; }
        boolean hasEnoughPower() { if (asteroidPhase != AST_NONE) return true; return totalPowerProduction() >= powerRequirement(level); }
        float powerNet() { return totalPowerProduction() - totalPowerUse(); }
        float gridCapacity() { return (power != null && power.graph != null) ? power.graph.getTotalBatteryCapacity() : 0; }
        float gridStored() { return (power != null && power.graph != null) ? power.graph.getBatteryStored() : 0; }
        float liveAffectRadius() { return Math.max(0, powerNet() / (100 * level)); }
        float affectRadius() { return asteroidPhase != AST_NONE ? asteroidRadius / TS : liveAffectRadius(); }
        float affectRadiusWorld() { return affectRadius() * TS; }
        float rangeBarValue() {
            long now = Time.millis();
            if (rangeStamp < 0 || now < rangeStamp || now - rangeStamp >= 1000) { rangeStamp = now; rangeShown = affectRadius(); }
            return rangeShown;
        }
        float nexMaxHealth() { return thermion.health * (1 + formHealthBonus(form, level) / 100); }
        @Override public float maxHealth() { return nexMaxHealth(); }

        // ---------- cấp / dạng
        void setLevel(int n) {
            n = Math.min(Math.max(n, 1), MAX_LEVEL);
            if (n == level) return;
            float frac = healthf(); boolean leveledUp = n > level;
            level = n; clearUpgrade();
            if (leveledUp) {
                NexNet.fx(n >= MAX_LEVEL ? Fx.bigShockwave : Fx.upgradeCore, x, y);
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(6, 6, x, y);
                if (n >= MAX_LEVEL) startCutscene(x, y, formColor(form));
            }
            health = Math.max(1, frac * nexMaxHealth());
            resetSkillStates();
        }
        boolean canChangeForm(int to) {
            return to >= YELLOW && to <= PURPLE && form != DEATH && to != form && pendingForm < 0 &&
                asteroidPhase == AST_NONE && formCooldown <= 0 && hasEnoughPower();
        }
        void requestForm(int to) {
            if (Vars.net.client() || !canChangeForm(to)) return;
            pendingForm = to; formChangeTimer = val(FORM_TIME, level) * 60;
        }
        void tickFormChange() {
            if (pendingForm < 0) return;
            formChangeTimer -= Time.delta;
            if (formChangeTimer <= 0) { int to = pendingForm; setForm(to); formCooldown = val(FORM_CD, level) * 60; }
        }
        void setForm(int to) {
            if (to < YELLOW || to > DEATH) return;
            float frac = healthf();
            if (form == PURPLE && to != PURPLE) killDrones();
            form = to; plasmas = new ArrayList<>(); plasmaCd = 0; pendingForm = -1; formChangeTimer = 0; asteroidPhase = AST_NONE;
            if (to == DEATH) deathTimer = DEATH_TICKS;
            health = Math.max(1, frac * nexMaxHealth());
            resetSkillStates();
        }
        void killDrones() {
            restoreDrones();
            for (Unit d : drones) if (d != null && d.isValid()) d.kill();
            drones = new ArrayList<>();
        }
        void restoreDrones() {
            if (savedDroneIds.isEmpty()) return;
            for (int id : savedDroneIds) {
                Unit d = Groups.unit.getByID(id);
                if (d != null && d.isValid() && d.team == team && !drones.contains(d)) { d.controller(makeDroneAI(this)); drones.add(d); }
            }
            savedDroneIds = new ArrayList<>();
        }
        void resetSkillStates() {
            sphereCharge = 0; orbCount = 0; shield = 0; shieldCooldown = 0; shieldRegenTimer = 0; shieldHitCd = 0;
            shieldDirty = true; blades[0] = 0; blades[1] = 0; bladeInit[0] = false; bladeInit[1] = false;
            bladeTarget[0] = null; bladeTarget[1] = null; lasers = new ArrayList<>();
            for (int i = 0; i < timers.length; i++) timers[i] = 0;
        }
        boolean every(int i, float ticks) {
            if (ticks <= 0) return false;
            timers[i] += Time.delta;
            if (timers[i] >= ticks) { timers[i] -= ticks; return true; }
            return false;
        }

        // ---------- mục tiêu / tiện ích
        ArrayList<Unit> unitsAround(float cx, float cy, float r) {
            final ArrayList<Unit> out = new ArrayList<>();
            Units.nearbyEnemies(team, cx - r, cy - r, r * 2, r * 2, u -> { if (u.isValid() && u.within(cx, cy, r + u.hitSize / 2)) out.add(u); });
            return out;
        }
        ArrayList<Unit> enemyUnits(float r) { return r <= 0 ? new ArrayList<>() : unitsAround(x, y, r); }
        ArrayList<Unit> cachedEnemies(float r) {
            if (Time.time < ceStamp || Time.time - ceStamp >= 6) { ceStamp = Time.time; ceList = enemyUnits(r); }
            ArrayList<Unit> out = new ArrayList<>();
            for (Unit u : ceList) if (u.isValid()) out.add(u);
            return out;
        }
        ArrayList<Building> buildingsAround(float cx, float cy, float r) {
            final ArrayList<Building> out = new ArrayList<>(); final Team tm = team;
            if (r <= 0) return out;
            Vars.indexer.allBuildings(cx, cy, r, b -> { if (b.team != tm && b.team != Team.derelict) out.add(b); });
            return out;
        }
        ArrayList<Building> enemyBuildings(float r) { return buildingsAround(x, y, r); }
        void fx(Effect e, float ex, float ey, float rot, Color c) { if (Vars.net.server()) Call.effect(e, ex, ey, rot, c); else e.at(ex, ey, rot, c); }
        void sfx(arc.audio.Sound s, float sx, float sy, float vol, float pitch) {
            if (s == null) return;
            if (isAssetSound(s)) { if (!Vars.headless) s.at(sx, sy, pitch, vol); }
            else if (Vars.net.server()) Call.soundAt(s, sx, sy, vol, pitch); else s.at(sx, sy, pitch, vol);
        }
        void stun(Unit u, float ticks) { u.apply(StatusEffects.unmoving, ticks); u.apply(StatusEffects.disarmed, ticks); }
        void disableBuilding(Building b, float ticks) { b.applySlowdown(0f, ticks); }

        // ---------- khiên chặn damage
        @Override public float handleDamage(float amount) {
            if (form == YELLOW && level >= 3 && shieldCooldown <= 0 && shield > 0 && hasEnoughPower()) {
                float ab = Math.min(shield, amount);
                shield -= ab; amount -= ab;
                if (shield <= 0) {
                    shield = 0; shieldCooldown = val(SH_CD, level);
                    fx(FX_SHIELD_BREAK, x, y, shieldRadius(), YELLOW_COLOR);
                    sfx(sndShieldBreak(), x, y, 1.5f, 1f);
                }
            }
            return amount;
        }

        // ---------- vòng đời
        @Override public void updateTile() {
            SELF.put(id, this);
            if (Vars.net.client()) return;
            if (form == DEATH) { updateDeath(); return; }
            restoreDrones();
            if (asteroidPhase != AST_NONE) { updateAsteroid(); return; }
            if (!hasEnoughPower()) return;
            if (formCooldown > 0) formCooldown -= Time.delta;
            if (empActiveTimer > 0) empActiveTimer -= Time.delta;
            secTick = false;
            secondTimer += Time.delta;
            if (secondTimer >= 60) { secondTimer -= 60; secTick = true; }
            if (pendingForm >= 0) { tickFormChange(); return; }
            if (secTick) {
                if (form == BLUE) fx(FX_GLOW_ZOOM, x, y, 0, BLUE_COLOR);
                else if (form == YELLOW) for (int i = 0; i < 8; i++) fx(FX_GLOW_IN, x, y, rnd(0, 360), YELLOW_COLOR);
            }
            if (skillCd < 1) skillCd = Math.min(1, skillCd + Time.delta / skillCooldownTicks(form));
            int lv = level;
            if (form == YELLOW) {
                updateElectrifiedField();
                if (lv >= 2) updateElectricSphere();
                if (lv >= 3) updateEnergyShield();
                if (lv >= 4) { updateMagneticField(); updateDualBlade(); }
            } else if (form == BLUE) {
                updateBlaster();
                if (lv >= 2) updateMeteor();
                if (lv >= 3) updateDeathLaser();
                if (lv >= 4) { updateLimitless(); updateRCT(); }
            } else if (form == PURPLE) {
                updateIllusion();
                if (lv >= 2) updateDrone();
                if (lv >= 3) updateShapeshifter();
                if (lv >= 4) updateMindControl();
            }
        }
        @Override public void onDestroyed() {
            if (!Vars.net.client() && form == PURPLE && level >= 3) hypnosis();
            if (!Vars.net.client()) killDrones();
            super.onDestroyed();
        }
        @Override public void onRemoved() {
            if (!Vars.net.client()) killDrones();
            SELF.remove(id);
            super.onRemoved();
        }
        void updateDeath() {
            deathTimer -= Time.delta;
            if (deathTimer <= 0) { deathTimer = 0; setForm(BLUE); fx(FX_HEAL, x, y, thermion.size * TS, BLUE_COLOR); }
        }
        @Override public byte version() { return 2; }
        @Override public void write(Writes w) {
            super.write(w);
            w.b(level); w.b(form); w.b(pendingForm); w.f(formChangeTimer); w.f(formCooldown);
            w.f(shield); w.f(shieldCooldown); w.f(skillCd); w.f(empActiveTimer);
            w.b(asteroidPhase); w.f(asteroidTimer); w.f(asteroidRadius); w.f(asteroidTx); w.f(asteroidTy);
            for (int i = 0; i < 2; i++) { w.f(blades[i]); w.f(bladeX[i]); w.f(bladeY[i]); }
            w.s(orbCount); w.f(sphereCharge); w.i(orbSeed);
            ArrayList<Unit> alive = new ArrayList<>();
            for (Unit d : drones) if (d != null && d.isValid()) alive.add(d);
            drones = alive;
            w.s(drones.size());
            for (Unit d : drones) w.i(d.id);
            w.bool(upgrading);
            for (int i = 0; i < MAX_COST_ITEMS; i++) w.i(paid[i]);
            w.f(deathTimer);
        }
        @Override public void read(Reads r, byte revision) {
            super.read(r, revision);
            level = Math.min(Math.max(r.b(), 1), MAX_LEVEL); form = r.b(); pendingForm = r.b();
            formChangeTimer = r.f(); formCooldown = r.f(); shield = r.f(); shieldCooldown = r.f();
            skillCd = r.f(); empActiveTimer = r.f();
            asteroidPhase = r.b(); asteroidTimer = r.f(); asteroidRadius = r.f(); asteroidTx = r.f(); asteroidTy = r.f();
            for (int i = 0; i < 2; i++) { blades[i] = r.f(); bladeX[i] = r.f(); bladeY[i] = r.f(); }
            orbCount = r.s(); sphereCharge = r.f(); orbSeed = r.i();
            int dc = r.s();
            savedDroneIds = new ArrayList<>();
            for (int i = 0; i < dc; i++) savedDroneIds.add(r.i());
            if (revision >= 1) { upgrading = r.bool(); for (int i = 0; i < MAX_COST_ITEMS; i++) paid[i] = r.i(); }
            else clearUpgrade();
            if (revision >= 2) deathTimer = r.f(); else if (form == DEATH) deathTimer = DEATH_TICKS;
            shieldDirty = false;
        }

        // ============ YELLOW
        void updateElectrifiedField() {
            if (!every(T_FIELD, 5)) return;
            StatusEffect s = slowEffects[level - 1];
            for (Unit u : enemyUnits(affectRadiusWorld())) u.apply(s, 12);
        }
        float[] orbPos(int i) {
            double sd = orbSeed * 977 + i * 2, a = rs(sd, 0, 360), r = SPH_SPAWN_R * TS * Math.sqrt(rs(sd + 1, 0, 1));
            return new float[]{x + tx(a, r), y + ty(a, r)};
        }
        void updateElectricSphere() {
            ArrayList<Unit> enemies = cachedEnemies(affectRadiusWorld());
            int cap = (int) val(SPH_MAX, level);
            if (enemies.size() > cap) {
                enemies.sort((a, b) -> Float.compare(a.dst2(x, y), b.dst2(x, y)));
                enemies = new ArrayList<>(enemies.subList(0, cap));
            }
            orbCount = enemies.size();
            if (orbCount == 0) { sphereCharge = 0; return; }
            sphereCharge += Time.delta;
            if (sphereCharge < val(SPH_CHARGE, level)) return;
            sphereCharge = 0;
            BulletType bt = orbBullets[level - 1];
            if (bt == null) return;
            for (int i = 0; i < orbCount; i++) {
                Unit t = enemies.get(i); float[] p = orbPos(i);
                float ang = (float) (Math.atan2(t.y - p[1], t.x - p[0]) / DEG);
                float travel = Thermion.dst(p[0], p[1], t.x, t.y) / bt.speed, lifeScl = Math.max(1.5f, travel * 1.8f / bt.lifetime);
                bt.createNet(team, p[0], p[1], ang, -1f, 1f, lifeScl);
                fx(FX_SPHERE, p[0], p[1], ang, YELLOW_COLOR);
            }
            sfx(sndOrbShoot(), x, y, 1.2f, rnd(0.95, 1.05));
            orbSeed++;
        }
        float shieldRadius() { return val(SH_RADIUS, level) * TS; }
        void updateEnergyShield() {
            final float capacity = gridCapacity();
            shieldMax = val(SH_PCT, level) / 100 * capacity;
            if (shieldHitCd > 0) shieldHitCd -= Time.delta;
            if (shieldCooldown > 0) {
                shieldCooldown -= Time.delta;
                if (shieldCooldown <= 0) { shieldCooldown = 0; shield = Math.max(0, shieldMax) * SH_START; shieldRegenTimer = 0; }
                return;
            }
            if (shieldMax <= 0) { shield = 0; shieldDirty = true; return; }
            if (shieldDirty) { shield = shieldMax * SH_START; shieldDirty = false; }
            if (shield > shieldMax) shield = shieldMax;
            if (shield < shieldMax) {
                shieldRegenTimer += Time.delta;
                if (shieldRegenTimer >= SH_REGEN) {
                    shieldRegenTimer -= SH_REGEN;
                    if (power != null && power.graph != null) {
                        float want = Math.min(shieldMax - shield, capacity * SH_REGEN_FRAC);
                        shield += power.graph.useBatteries(want);
                    }
                }
            } else shieldRegenTimer = 0;
            final float sr = shieldRadius(); final ThermionBuild self = this;
            Groups.bullet.intersect(x - sr, y - sr, sr * 2, sr * 2, b -> {
                if (self.shield <= 0 || b.team == self.team || !b.type.absorbable || !b.within(self.x, self.y, sr)) return;
                self.shield -= b.damage();
                b.absorb();
                self.fx(FX_SPHERE, b.x, b.y, 0, YELLOW_COLOR);
                if (self.shieldHitCd <= 0) { self.shieldHitCd = SH_HIT_GAP; self.sfx(sndShieldHit(), b.x, b.y, 0.7f, rnd(0.9, 1.1)); }
            });
            if (shield <= 0) {
                shield = 0; shieldCooldown = val(SH_CD, level);
                fx(FX_SHIELD_BREAK, x, y, sr, YELLOW_COLOR);
                sfx(sndShieldBreak(), x, y, 1.5f, 1f);
            } else {
                for (Unit u : enemyUnits(sr)) pushOut(u);
            }
        }
        void pushOut(Unit u) {
            float dx = u.x - x, dy = u.y - y, d = (float) Math.hypot(dx, dy), nx, ny;
            if (d < 0.001f) { double a = (u.id % 360) * DEG; nx = (float) Math.cos(a); ny = (float) Math.sin(a); }
            else { nx = dx / d; ny = dy / d; }
            float radial = u.vel.x * nx + u.vel.y * ny;
            if (radial < PUSH) { float add = PUSH - radial; u.vel.add(nx * add, ny * add); }
        }
        void updateMagneticField() {
            if (!secTick) return;
            float pct = val(MAG, level) / 100;
            ArrayList<Unit> list = enemyUnits(affectRadiusWorld());
            for (Unit u : list) { u.damagePierce(u.maxHealth * pct); fx(FX_MAGNETIC, u.x, u.y, 0, YELLOW_COLOR); }
            if (!list.isEmpty()) sfx(sndSpark(), x, y, 0.8f, 0.8f);
        }
        void updateDualBlade() {
            int count = (int) val(BL_COUNT, level); float create = val(BL_CREATE, level), r = affectRadiusWorld(), dmg = val(BL_DMG, level);
            float home = thermion.size * TS * 0.5f + BLADE_R + 6; boolean retarget = every(T_BLADE, 10);
            ArrayList<Unit> enemies = null;
            for (int i = 0; i < count; i++) {
                if (blades[i] < create) {
                    blades[i] += Time.delta;
                    float oa = Time.time * 6 + i * 180;
                    bladeX[i] = x + tx(oa, home); bladeY[i] = y + ty(oa, home);
                    bladeInit[i] = false; bladeTarget[i] = null;
                    continue;
                }
                if (!bladeInit[i]) { bladeX[i] = x; bladeY[i] = y; bladeHit[i] = 0; bladeInit[i] = true; }
                Unit tg = bladeTarget[i];
                if (tg != null && (!tg.isValid() || !tg.within(x, y, r + tg.hitSize / 2))) tg = null;
                if (tg == null && retarget) {
                    if (enemies == null) enemies = enemyUnits(r);
                    Unit other = count > 1 ? bladeTarget[1 - i] : null;
                    float best = 1e30f;
                    for (Unit u : enemies) {
                        float d = Thermion.dst(u.x, u.y, bladeX[i], bladeY[i]) + (u == other ? 400 : 0);
                        if (d < best) { best = d; tg = u; }
                    }
                }
                bladeTarget[i] = tg;
                float tX, tY; boolean cutting = false;
                if (tg != null) {
                    float reach = tg.hitSize / 2 + 6;
                    if (Thermion.dst(tg.x, tg.y, bladeX[i], bladeY[i]) <= reach + 4) {
                        float ca = Time.time * 14 + i * 180;
                        tX = tg.x + tx(ca, reach * 0.8f); tY = tg.y + ty(ca, reach * 0.8f); cutting = true;
                    } else { tX = tg.x; tY = tg.y; }
                } else {
                    float ha = Time.time * 6 + i * 180;
                    tX = x + tx(ha, home); tY = y + ty(ha, home);
                }
                float dx = tX - bladeX[i], dy = tY - bladeY[i], dd = (float) Math.hypot(dx, dy);
                if (dd > 0.001f) { float step = Math.min(dd, BLADE_SPEED * Time.delta); bladeX[i] += dx / dd * step; bladeY[i] += dy / dd * step; }
                if (cutting) {
                    bladeHit[i] += Time.delta;
                    if (bladeHit[i] >= 15) {
                        bladeHit[i] -= 15;
                        tg.damage(dmg);
                        float bf = bonusFraction(gridCapacity()) * BONUS_BLADE;
                        if (bf > 0 && tg.isValid()) tg.damage(tg.maxHealth * bf);
                        fx(FX_BLADE_SLASH, tg.x, tg.y, rnd(0, 360), YELLOW_COLOR);
                        sfx(sndBladeHit(), tg.x, tg.y, 1.2f, rnd(0.9, 1.1));
                    }
                }
            }
        }
        void activateElectromagneticPulse() {
            if (Vars.net.client() || form != YELLOW || level < 5 || pendingForm >= 0) return;
            if (!hasEnoughPower() || skillCd < 1) return;
            skillCd = 0; empActiveTimer = EMP_DURATION;
            float r = affectRadiusWorld();
            ArrayList<Unit> us = enemyUnits(r); ArrayList<Building> bs = enemyBuildings(r);
            for (Unit u : us) stun(u, EMP_DURATION);
            for (Building b : bs) disableBuilding(b, EMP_DURATION);
            fx(FX_EMP, x, y, r, YELLOW_COLOR);
            sfx(sndPulse(), x, y, 2f, 0.9f);
            NexNet.shake(8, 10, x, y);
        }

        // ============ BLUE
        void updateBlaster() {
            tickPlasma();
            if (!secTick || !plasmas.isEmpty() || plasmaCd > 0) return;
            float ch = val(BLS_CH, level) / 100, dmg = val(BLS_DMG, level), extraPct = 0.5f * level / 100;
            float splashRad = val(BLS_SPLASH, level) * TS;
            for (Unit u : enemyUnits(affectRadiusWorld())) {
                if (!chance(ch)) continue;
                int beams = ri(1, level); float perBeam = dmg + dmg * beams / 5f, extra = u.maxHealth * extraPct;
                for (int i = 0; i < beams && plasmas.size() < 12; i++) plasmaBeam(u, i, i == beams - 1, perBeam, extra, splashRad);
            }
        }
        /** tia plasma từ trên trời: bắt đầu quanh pháo rồi đuổi theo mục tiêu, gây sát thương duy trì (tổng ~ perBeam mỗi giây) */
        void plasmaBeam(Unit u, int idx, boolean last, float perBeam, float extra, float splashRad) {
            float px = x + rr(TS), py = y + rr(TS);
            Plasma p = new Plasma();
            p.tgt = u; p.x = px; p.y = py; p.life = BLS_MAX_TICKS; p.acc = idx * 3; p.fxT = 0; p.snd = 0; p.dmg = perBeam / 6;
            p.ex = last ? extra / (BLS_MAX_TICKS / 10) : 0; p.rad = Math.max(splashRad * 0.6f, TS * 1.5f); p.speed = 4 + level * 0.6f + rnd(0, 1.5);
            plasmas.add(p);
            fx(FX_BLASTER, px, py, 0, BLUE_COLOR);
        }
        void tickPlasma() {
            if (Vars.net.client() || plasmas.isEmpty()) { if (plasmaCd > 0 && plasmas.isEmpty()) plasmaCd -= Time.delta; return; }
            for (int i = plasmas.size() - 1; i >= 0; i--) {
                Plasma p = plasmas.get(i); Unit u = p.tgt;
                p.life -= Time.delta;
                if (!u.isValid() || p.life <= 0) { fx(FX_BLAST, p.x, p.y, 0, BLUE_COLOR); plasmas.remove(i); continue; }
                float dx = u.x - p.x, dy = u.y - p.y, d = (float) Math.sqrt(dx * dx + dy * dy), sp = p.speed * Time.delta;
                if (d > sp) { p.x += dx / d * sp; p.y += dy / d * sp; } else { p.x = u.x; p.y = u.y; }
                p.fxT -= Time.delta; if (p.fxT <= 0) { p.fxT = 3; fx(FX_PLASMA, p.x, p.y, 0, BLUE_COLOR); }
                p.acc -= Time.delta;
                if (p.acc <= 0) {
                    p.acc = 10;
                    for (Unit a : unitsAround(p.x, p.y, p.rad)) a.damagePierce(p.dmg);
                    if (p.ex > 0 && u.isValid()) u.damagePierce(p.ex);
                }
                p.snd -= Time.delta; if (p.snd <= 0) { p.snd = 30; sfx(sndLaser(), p.x, p.y, 0.5f, rnd(0.9, 1.2)); }
            }
            if (plasmas.isEmpty()) plasmaCd = BLS_COOLDOWN_TICKS;
        }
        void updateMeteor() {
            if (!secTick) return;
            float ch = val(MET_CH, level) / 100, pct = val(MET_PCT, level) / 100, flat = val(MET_FLAT, level), rad = METEOR_RAD_TILES * TS;
            for (Unit u : enemyUnits(affectRadiusWorld())) {
                if (!chance(ch)) continue;
                int count = ri(3, level * 3);
                for (int i = 0; i < count; i++) meteor(u.x + rr(TS * 2), u.y + rr(TS * 2), rnd(0, 25), rad, pct, flat);
            }
        }
        void meteor(final float ix, final float iy, float d, final float rad, final float pct, final float flat) {
            final ThermionBuild self = this;
            Time.run(d, () -> self.fx(FX_METEOR, ix, iy, rad, Color.orange));
            Time.run(d + METEOR_FALL, () -> {
                self.sfx(sndMeteorHit(), ix, iy, 1.4f, rnd(0.9, 1.1));
                for (Unit v : self.unitsAround(ix, iy, rad)) v.damage(v.maxHealth * pct + flat);
                Liquid liq = chance(SLAG_CHANCE) ? Liquids.slag : Liquids.oil;
                for (int i = 0; i < METEOR_PUDDLES; i++) {
                    Tile t = Vars.world.tileWorld(ix + rr(rad), iy + rr(rad));
                    if (t != null) Puddles.deposit(t, liq, 20f);
                }
            });
        }
        void updateDeathLaser() {
            if (secTick) {
                float ch = val(LAS_CH, level) / 100;
                for (Unit u : enemyUnits(affectRadiusWorld())) {
                    if (!chance(ch)) continue;
                    Laser l = new Laser();
                    l.target = u; l.tx = u.x; l.ty = u.y; l.timeLeft = val(LAS_TIME, level) * 60; l.secTimer = 0; l.weakenTimer = 0; l.fxTimer = 5; l.loopTimer = 0;
                    lasers.add(l);
                    sfx(sndDeathLoop(), u.x, u.y, 2f, 1f);
                }
            }
            float range = 2 * level * TS, dps = val(LAS_DMG, level);
            for (int i = lasers.size() - 1; i >= 0; i--) {
                Laser b = lasers.get(i);
                if (b.target != null && b.target.isValid()) { b.tx = b.target.x; b.ty = b.target.y; }
                b.timeLeft -= Time.delta; b.secTimer += Time.delta; b.loopTimer += Time.delta; b.weakenTimer += Time.delta; b.fxTimer += Time.delta;
                if (b.loopTimer >= LOOP_TICKS) {
                    b.loopTimer -= LOOP_TICKS;
                    if (b.timeLeft > LOOP_TICKS * 0.5f) sfx(sndDeathLoop(), b.tx, b.ty, 2f, 1f);
                }
                if (b.fxTimer >= 5) { b.fxTimer = 0; fx(FX_DEATH_LASER, b.tx, b.ty, range, BLUE_COLOR); }
                if (b.weakenTimer >= 10) {
                    b.weakenTimer = 0;
                    for (Unit u : unitsAround(b.tx, b.ty, range)) u.apply(weakened, 20);
                    for (Building bb : buildingsAround(b.tx, b.ty, range)) bb.applySlowdown(0.5f, 20);
                }
                if (b.secTimer >= 60) {
                    b.secTimer -= 60;
                    if (b.target != null && b.target.isValid()) {
                        b.target.damage(dps);
                        for (Unit u : unitsAround(b.tx, b.ty, range)) if (u != b.target) u.damage(dps * LAS_SPLASH_FRAC);
                    }
                }
                if (b.timeLeft <= 0) {
                    float pct = ri(2, 5) * level / 100f;
                    for (Unit u : unitsAround(b.tx, b.ty, range)) u.damage(u.maxHealth * pct);
                    fx(FX_DEATH_BLAST, b.tx, b.ty, range, BLUE_COLOR);
                    sfx(sndDeathBlast(), b.tx, b.ty, 1.2f, rnd(0.95, 1.05));
                    lasers.remove(i);
                }
            }
        }
        void updateLimitless() {
            final float r = affectRadiusWorld() / LIM_DIV, pct = val(LIM_DECAY, level) / 100; final ThermionBuild self = this;
            if (r <= 0 || pct <= 0) return;
            Groups.bullet.intersect(x - r, y - r, r * 2, r * 2, b -> {
                if (b.team == self.team || !b.within(self.x, self.y, r)) return;
                float spd = b.vel.len();
                if (spd <= 0.0001f) return;
                float base = b.type.speed > 0.0001f ? b.type.speed : spd;
                b.vel.setLength(Math.max(0, spd - base * pct / 60 * Time.delta));
            });
        }
        void updateRCT() {
            if (!every(T_RCT, val(RCT_T, level))) return;
            heal(nexMaxHealth() * val(RCT_A, level) / 100);
            fx(FX_HEAL, x, y, thermion.size * TS, BLUE_COLOR);
        }
        void activateAsteroid() {
            if (Vars.net.client() || form != BLUE || level < 5 || pendingForm >= 0 || asteroidPhase != AST_NONE) return;
            if (!hasEnoughPower() || skillCd < 1) return;
            skillCd = 0;
            asteroidRadius = liveAffectRadius() * TS;
            asteroidPhase = AST_CHARGE; asteroidTimer = AST_CHARGE_TICKS;
            float w = Vars.world.unitWidth(), h = Vars.world.unitHeight();
            asteroidTx = x < w / 2 ? 2 * w : -w; asteroidTy = 2 * h;
            lasers = new ArrayList<>(); chargeLoopTimer = LOOP_TICKS;
        }
        void updateAsteroid() {
            final float r = asteroidRadius; final ThermionBuild self = this;
            if (every(T_ASTEROID, 5)) {
                for (Unit u : enemyUnits(r)) stun(u, 20);
                for (Building b : enemyBuildings(r)) disableBuilding(b, 20);
            }
            Groups.bullet.intersect(x - r, y - r, r * 2, r * 2, b -> { if (b.team != self.team && b.within(self.x, self.y, r)) b.vel.setZero(); });
            if (asteroidPhase == AST_CHARGE) {
                chargeLoopTimer += Time.delta;
                if (chargeLoopTimer >= LOOP_TICKS) {
                    chargeLoopTimer -= LOOP_TICKS;
                    if (asteroidTimer > LOOP_TICKS * 0.5f) sfx(sndDeathLoop(), x, y, 2f, 1f);
                }
            }
            asteroidTimer -= Time.delta;
            if (asteroidTimer > 0) return;
            if (asteroidPhase == AST_CHARGE) {
                asteroidPhase = AST_FALL; asteroidTimer = AST_TRAVEL;
                fx(FX_AST_FALL, asteroidTx, asteroidTy, rnd(1, 1000), Color.orange);
            } else if (asteroidPhase == AST_FALL) asteroidStrike();
        }
        void asteroidStrike() {
            final float r = asteroidRadius, cx = Vars.world.unitWidth() / 2f, cy = Vars.world.unitHeight() / 2f; final ThermionBuild self = this;
            final ArrayList<Unit> doomedU = new ArrayList<>(); final ArrayList<Building> doomedB = new ArrayList<>();
            Groups.unit.each(u -> { if (u.isValid() && !u.within(self.x, self.y, r)) doomedU.add(u); });
            Groups.build.each(b -> { if (b == null || !b.isValid() || b.block == thermion || b.within(self.x, self.y, r)) return; doomedB.add(b); });
            fx(FX_AST_FLASH, cx, cy, 0, Color.white);
            fx(FX_AST_QUAKE, cx, cy, 0, Color.orange);
            fx(FX_AST_IMPACT, cx, cy, Thermion.dst(cx, cy, 0, 0), Color.orange);
            for (Unit u : doomedU) u.kill();
            for (Building b : doomedB) b.kill();
            scorchMap();
            setForm(DEATH);
        }
        /** FloorRenderer v157 không có clearTiles(): gọi hàm làm mới cache sàn nếu có (qua reflection để không lỗi biên dịch). */
        static void refreshFloorCache() throws Exception {
            Object fr = Vars.renderer.blocks.floor;
            for (String n : new String[]{"clearTiles", "reload", "clear"}) {
                try { fr.getClass().getMethod(n).invoke(fr); return; } catch (NoSuchMethodException ignored) {}
            }
        }
        void scorchMap() {
            final int W = Vars.world.width(), H = Vars.world.height();
            final Floor[] rocks = {(Floor) Blocks.hotrock, (Floor) Blocks.magmarock, (Floor) Blocks.basalt};
            final int[] y0 = {0};
            Runnable step = new Runnable() {
                @Override public void run() {
                    int y1 = Math.min(H, y0[0] + 10);
                    for (int yy = y0[0]; yy < y1; yy++) for (int xx = 0; xx < W; xx++) {
                        try {
                            Tile t = Vars.world.tile(xx, yy);
                            if (t == null) continue;
                            Floor f = t.floor(); Block blk = t.block();
                            Floor rock = rocks[(int) Math.floor(rs(xx * 7 + yy * 131, 0, 2.999))];
                            t.setFloor(f.isLiquid ? (f.liquidDrop == Liquids.water ? (Floor) Blocks.slag : rock) : rock);
                            if (t.build == null && blk != Blocks.air && !blk.synthetic()) t.setAir();
                        } catch (Throwable err) { Log.err("[Thermion] scorch " + xx + "," + yy + ": " + err); }
                    }
                    y0[0] = y1;
                    if (y0[0] < H) Time.run(1f, this);
                    else try { refreshFloorCache(); } catch (Throwable ignored) {}
                }
            };
            step.run();
        }

        // ============ PURPLE
        void updateIllusion() {
            if (!every(T_ILLUSION, val(ILL_T, level) * 60)) return;
            float ch = val(ILL_CH, level) / 100, base = val(ILL_DUR, level) * 60;
            for (Unit u : enemyUnits(affectRadiusWorld())) {
                if (!chance(ch)) continue;
                float dur = base * (1 - cl(u.armor / 100));
                if (dur > 0) { stun(u, dur); fx(FX_PURPLE_BURST, u.x, u.y, 0, PURPLE_COLOR); }
            }
        }
        void updateDrone() {
            ArrayList<Unit> alive = new ArrayList<>();
            for (Unit d : drones) if (d != null && d.isValid()) alive.add(d);
            drones = alive;
            if (!every(T_DRONE, val(DRONE_T, level) * 60)) return;
            UnitType type = level >= DRONE_V2_LEVEL ? droneV2Type : droneType;
            Unit d = type.create(team); float off = thermion.size * TS / 2;
            d.set(x + rr(off), y + rr(off));
            d.controller(makeDroneAI(this));
            d.add();
            drones.add(d);
            fx(FX_PURPLE_BURST, d.x, d.y, 0, PURPLE_COLOR);
            float gdx = x - d.x, gdy = y - d.y;
            fx(FX_GLOW_DRONE, d.x, d.y, (float) ((Math.atan2(gdy, gdx) / DEG + 360) % 360 + 360 * Math.round(Math.hypot(gdx, gdy))), PURPLE_COLOR);
        }
        void updateShapeshifter() {
            if (!every(T_SHAPE, val(SHAPE_T, level) * 60)) return;
            float ch = val(SHAPE_CH, level) / 100;
            for (Unit u : enemyUnits(affectRadiusWorld())) {
                if (!chance(ch)) continue;
                shapeClone(u);
            }
        }
        void shapeClone(Unit u) {
            final ThermionBuild self = this; final Team tm = team; final UnitType utype = u.type;
            final float sx = u.x, sy = u.y, srot = u.rotation, shp = u.health, smax = u.maxHealth, ssh = u.shield, sarm = u.armor;
            fx(FX_SHAPE, sx, sy, 0, PURPLE_COLOR);
            Time.run(SHAPE_DELAY, () -> {
                if (Vars.net.client()) return;
                Unit s = utype.create(tm);
                s.set(sx, sy); s.rotation = srot; s.maxHealth = smax; s.health = shp; s.shield = ssh; s.armor = sarm; s.add();
                self.fx(FX_SHAPE, sx, sy, 0, PURPLE_COLOR);
            });
        }
        void updateMindControl() {
            if (!every(T_MIND, val(MIND_T, level) * 60)) return;
            float ch = val(MIND_CH, level) / 100;
            for (Unit u : enemyUnits(affectRadiusWorld())) if (chance(ch)) convert(u);
        }
        void convert(Unit u) { u.team = team; u.resetController(); fx(FX_MIND, u.x, u.y, u.hitSize, PURPLE_COLOR); }
        void activateMegaHypnosis() {
            if (Vars.net.client() || form != PURPLE || level < 5 || pendingForm >= 0) return;
            if (!hasEnoughPower() || skillCd < 1) return;
            skillCd = 0; hypnosis();
        }
        void hypnosis() {
            float r = affectRadiusWorld();
            fx(FX_HYPNOSIS, x, y, r, PURPLE_COLOR);
            for (Unit u : enemyUnits(r)) convert(u);
        }

        // ============ điều khiển
        void activateActiveSkill() {
            if (form == YELLOW) activateElectromagneticPulse();
            else if (form == BLUE) activateAsteroid();
            else if (form == PURPLE) activateMegaHypnosis();
        }
        void handleConfig(int v) {
            if (v >= YELLOW && v <= PURPLE) requestForm(v);
            else if (v == CFG_ACTIVE) activateActiveSkill();
            else if (v == CFG_UPGRADE) startUpgrade();
        }
        @Override public void configured(Unit builder, Object value) { if (value instanceof Number) handleConfig(((Number) value).intValue()); }
        @Override public void buildConfiguration(Table table) {
            final ThermionBuild self = this;
            String[] names = {"Yellow", "Blue", "Purple"};
            for (int f = YELLOW; f <= PURPLE; f++) {
                final int ff = f;
                table.button(names[f], Styles.flatBordert, () -> self.configure(Integer.valueOf(ff))).size(90, 40).disabled(b -> !self.canChangeForm(ff));
            }
            table.row();
            table.button(T("Kỹ năng chủ động", "Active skill"), Styles.flatBordert, () -> self.configure(Integer.valueOf(CFG_ACTIVE))).colspan(3).growX().height(40)
                .disabled(b -> level < 5 || form == DEATH || asteroidPhase != AST_NONE);
            table.row();
            table.table(t -> {
                t.button(T("Nâng cấp", "Upgrade"), Styles.flatBordert, () -> self.showUpgradeDialog()).size(135, 40).disabled(b -> level >= MAX_LEVEL || form == DEATH);
                t.button(T("Thông tin", "Info"), Styles.flatBordert, () -> self.showInfoDialog()).size(135, 40);
            }).colspan(3).growX();
        }

        // ============ nâng cấp (trả góp)
        int remaining(Object[][] cost, int i) { return Math.max(0, (Integer) cost[i][1] - paid[i]); }
        int coreAmount(Item item) { CoreBuild c = core(); return c == null ? 0 : c.items.get(item); }
        boolean canPayAny() {
            Object[][] cost = upgradeCost(level); CoreBuild c = core();
            if (cost == null || c == null) return false;
            if (isSandbox()) return true;
            for (int i = 0; i < cost.length; i++) if (remaining(cost, i) > 0 && c.items.get((Item) cost[i][0]) > 0) return true;
            return false;
        }
        boolean canUpgrade() { return level < MAX_LEVEL && form != DEATH && asteroidPhase == AST_NONE && canPayAny(); }
        void clearUpgrade() { upgrading = false; for (int i = 0; i < paid.length; i++) paid[i] = 0; }
        int paidTotal() { int s = 0; for (int i = 0; i < paid.length; i++) s += paid[i]; return s; }
        float paidFraction() {
            Object[][] cost = upgradeCost(level);
            if (cost == null || cost.length == 0) return 1;
            float s = 0;
            for (int i = 0; i < cost.length; i++) { int tot = (Integer) cost[i][1]; s += tot <= 0 ? 1 : Math.min(1f, paid[i] / (float) tot); }
            return s / cost.length;
        }
        void startUpgrade() { if (Vars.net.client() || !canUpgrade()) return; payInstallment(); }
        void payInstallment() {
            Object[][] cost = upgradeCost(level); CoreBuild c = core();
            if (cost == null) { clearUpgrade(); return; }
            if (c == null) return;
            boolean done = true, took = false;
            for (int i = 0; i < cost.length; i++) {
                int need = remaining(cost, i); Item it = (Item) cost[i][0];
                if (need > 0) {
                    int take = Math.min(need, c.items.get(it));
                    if (take > 0) { c.items.remove(it, take); paid[i] += take; took = true; }
                }
                if (paid[i] < (Integer) cost[i][1]) done = false;
            }
            if (took) upgrading = true;
            if (done) setLevel(level + 1);
        }
        void showUpgradeDialog() {
            final ThermionBuild self = this; final BaseDialog d = new BaseDialog(T("Nâng cấp Thermion", "Thermion Upgrade"));
            addBack(d);
            final Table body = new Table(); final int[] sig = {-1};
            body.update(() -> {
                if (level >= MAX_LEVEL) { d.hide(); return; }
                int sg = level * 1000003 + self.paidTotal() * 31 + (isSandbox() ? 7 : 0);
                if (sig[0] != sg) { sig[0] = sg; self.buildUpgradeBody(body); }
            });
            dialogPane(d, p -> p.add(body).width(dialogWidth() - 30).left());
            d.show();
        }
        void buildUpgradeBody(Table t) {
            final ThermionBuild self = this; final float cw = dialogWidth() - 90; final Object[][] cost = upgradeCost(level);
            t.clear(); t.defaults().left().pad(3);
            if(cost == null){ t.add(T("Đã đạt cấp tối đa.", "Max level reached.")).row(); return; }
            t.add("[accent]Level " + level + "  ->  " + (level + 1) + "[]").padTop(4).row();
            section(t, T("Tài nguyên", "Resources"));
            t.table(c -> {
                c.defaults().left().pad(3);
                for (int i = 0; i < cost.length; i++) {
                    final int idx = i; final Item item = (Item) cost[i][0]; final int total = (Integer) cost[i][1];
                    c.image(item.uiIcon).size(26).padRight(6);
                    c.add(item.localizedName).padRight(10);
                    c.label(() -> {
                        int have = self.coreAmount(item), rem = Math.max(0, total - paid[idx]);
                        String col = rem == 0 ? "[lightgray]" : have >= rem ? "[green]" : "[scarlet]";
                        return col + have + "[] / " + rem + (paid[idx] > 0 ? "  [lightgray](" + paid[idx] + "/" + total + ")[]" : "");
                    });
                    c.row();
                }
                c.add(mkBar(() -> T("Đã trả ", "Paid ") + (int) Math.floor(self.paidFraction() * 100) + "%", () -> Pal.accent, () -> self.paidFraction())).colspan(3).growX().height(26).padTop(6).row();
            wrap(c, T("[lightgray]Trong lõi / còn cần  (đã trả / tổng)[]", "[lightgray]In core / still needed  (paid / total)[]"), cw).colspan(3).row();
            }).growX().padLeft(16).row();
            wrap(t, T("Mỗi lần bấm chỉ trả một lần, lấy đúng lượng tài nguyên lõi đang có. Quay lại bấm tiếp để trả thêm; đủ tất cả thì tự lên cấp.", "Each click pays once, taking exactly what the core currently has. Come back and click again to pay more; once everything is paid the block levels up automatically."), cw).padTop(4).padLeft(16).row();
            Cell<TextButton> ub = t.button("", Styles.flatBordert, () -> self.configure(Integer.valueOf(CFG_UPGRADE))).growX().height(46).padTop(12);
            ub.disabled(b -> !self.canUpgrade());
            final TextButton tb = ub.get();
            tb.update(() -> tb.setText(upgrading ? T("Trả góp", "Pay installment") : T("Nâng cấp", "Upgrade")));
            t.row();
            t.label(() -> self.canUpgrade() ? "" : T("[lightgray]Lõi hiện không có tài nguyên nào còn thiếu.[]", "[lightgray]The core has none of the missing resources.[]")).row();
        }

        // ============ info
        void showInfoDialog() {
            final ThermionBuild self = this; final BaseDialog d = new BaseDialog(T("Thông tin Thermion", "Thermion Info"));
            addBack(d);
            final Table body = new Table(); final int[] sig = {-1};
            body.update(() -> { if (sig[0] != level) { sig[0] = level; self.buildInfoBody(body); } });
            dialogPane(d, p -> p.add(body).width(dialogWidth() - 30).left());
            d.show();
        }
        void buildInfoBody(Table t) {
            final ThermionBuild self = this; final float cw = dialogWidth() - 90;
            t.clear(); t.defaults().left().pad(3);
            section(t, T("Tổng quan", "Overview"));
            t.table(c -> {
                c.defaults().left().pad(3);
            wrap(c, T("Thermion là cỗ máy 5x5 nhiều dạng chạy bằng lưới điện. Cần công suất điện tối thiểu để hoạt động, điện dư càng lớn thì phạm vi ảnh hưởng càng rộng. Có ba dạng (Yellow / Blue / Purple), mỗi dạng có kỹ năng bị động riêng, mở khóa thêm khi lên cấp. Ở level 5 mỗi dạng có thêm một kỹ năng chủ động.", "Thermion is a 5x5 multi-form machine powered by the power grid. It needs a minimum power output to work, and the more surplus power, the larger its range of effect. It has three forms (Yellow / Blue / Purple), each with its own passive skills that unlock as it levels up. At level 5 each form gains an active skill."), cw).row();
                c.label(() -> T("Cấp: ", "Level: ") + level + " / " + MAX_LEVEL).row();
                c.label(() -> T("Dạng: ", "Form: ") + colorTag(form) + formName(form) + "[]").row();
                c.label(() -> {
                    float prod = self.totalPowerProduction(), req = powerRequirement(level);
                    return T("Công suất điện: ", "Power output: ") + (prod >= req ? "[green]" : "[scarlet]") + fmt(prod) + "[] / " + fmt(req) + " /s";
                }).row();
                c.label(() -> T("Phạm vi ảnh hưởng: ", "Effect range: ") + Strings.fixed(self.affectRadius(), 1) + " tile").row();
                c.label(() -> T("Máu tối đa: ", "Max HP: ") + fmt(self.nexMaxHealth())).row();
            }).growX().padLeft(16).row();

            section(t, T("Tiến trình theo cấp", "Progression by level"));
            t.table(c -> {
                c.defaults().pad(4);
                String[] hs = {T("Cấp", "Level"), T("Điện /s", "Power /s"), T("Đổi dạng", "Form change"), T("Hồi", "Cooldown"), T("Máu tối đa +", "Max HP +")};
                for (String h : hs) c.add("[lightgray]" + h + "[]");
                c.row();
                for (int L = 1; L <= MAX_LEVEL; L++) {
                    String[] cells = {String.valueOf(L), fmt(powerRequirement(L)), fmt(val(FORM_TIME, L)) + "s", fmt(val(FORM_CD, L)) + "s",
                        colorTag(YELLOW) + fmt(formHealthBonus(YELLOW, L)) + "[]/" + colorTag(BLUE) + fmt(formHealthBonus(BLUE, L)) + "[]/" + colorTag(PURPLE) + fmt(formHealthBonus(PURPLE, L)) + "[]%"};
                    String hl = L == level ? "[accent]" : "";
                    for (String s : cells) c.add(hl + s);
                    c.row();
                }
            }).growX().padLeft(16).row();

            for (int f = YELLOW; f <= PURPLE; f++) {
                final int fm = f;
                section(t, formName(f));
                t.table(c -> {
                    c.defaults().left().pad(3);
                    wrap(c, T(FORM_ROLE[fm][0], FORM_ROLE[fm][1]), cw).row();
                    c.add(T("[accent]Kỹ năng (bấm để xem chi tiết)[]", "[accent]Skills (tap for details)[]")).padTop(6).row();
                    for (Skill sk : SKILLS[fm]) {
                        if (sk.lv > level) continue;
                        c.button(colorTag(fm) + sk.name + "[]" + (sk.active ? T("  (chủ động)", "  (active)") : ""), Styles.flatBordert, () -> self.showSkillDialog(fm, sk)).width(cw).height(38).left().row();
                    }
                }).growX().padLeft(16).row();
            }
            section(t, "Death");
            wrap(t, T("Sau Asteroid, Thermion rơi vào dạng Death 10 phút: không có kỹ năng, không đổi dạng, không nâng cấp. Sau đó tự trở về dạng Blue. Hồi chiêu Asteroid bị khóa trong thời gian này.", "After Asteroid, Thermion falls into Death form for 10 minutes: no skills, no form change, no upgrades. Then it automatically returns to Blue. The Asteroid cooldown is locked during this time."), cw).padLeft(16).row();
            section(t, T("Cần biết", "Good to know"));
            wrap(t, T("- Công suất điện thấp hơn mức yêu cầu thì Thermion ngừng hoạt động hoàn toàn.\n- Đổi dạng mất vài giây rồi cần thời gian hồi.\n- Mọi kỹ năng chủ động dùng chung một thời gian hồi, đóng băng khi đang đổi dạng.\n- Nâng cấp trả bằng tài nguyên trong lõi và có thể trả góp.", "- If power output is below the requirement, Thermion stops working entirely.\n- Changing form takes a few seconds and then needs a cooldown.\n- All active skills share one cooldown, which freezes while changing form.\n- Upgrades are paid with core resources and can be paid in installments."), cw).padLeft(16).row();
        }
        void showSkillDialog(final int f, final Skill sk) {
            BaseDialog d = new BaseDialog(colorTag(f) + sk.name + "[]");
            addBack(d);
            dialogPane(d, p -> {
                float cw = dialogWidth() - 90;
                p.defaults().left().pad(3);
                p.add(colorTag(f) + formName(f) + "[] - " + (sk.active ? T("Chủ động", "Active") : T("Bị động", "Passive"))).row();
                p.label(() -> level >= sk.lv ? T("[green]Đã mở khóa[]", "[green]Unlocked[]") : T("[scarlet]Chưa mở khóa - cần level ", "[scarlet]Locked - requires level ") + sk.lv + T(" (hiện tại: ", " (current: ") + level + ")[]").row();
                section(p, T("Mô tả", "Description"));
                wrap(p, T(sk.vi, sk.en), cw).padLeft(16).row();
            });
            d.show();
        }

        // ============ bar
        float skillCooldownTicks(int f) { return f == YELLOW ? EMP_COOLDOWN : f == BLUE ? AST_COOLDOWN : f == PURPLE ? MEGA_COOLDOWN : 1; }
        float changeProgress() { return cl(1 - formChangeTimer / Math.max(1, val(FORM_TIME, level) * 60)); }
        String formBarText() {
            if (form == DEATH) return "Death: " + timeText(deathTimer);
            if (pendingForm >= 0) return T("Đang đổi", "Changing");
            if (formCooldown > 0) return T("Hồi: ", "Cooldown: ") + Strings.fixed(formCooldown / 60, 1) + "s";
            return formName(form);
        }
        Color formBarColor() {
            if (pendingForm >= 0) return changeProgress() < 0.5f ? formColor(form) : formColor(pendingForm);
            return formColor(form);
        }
        float formBarFraction() {
            if (form == DEATH) return cl(deathTimer / DEATH_TICKS);
            if (pendingForm >= 0) { float p = changeProgress(); return p < 0.5f ? 1 - p * 2 : (p - 0.5f) * 2; }
            if (formCooldown > 0) return 1 - cl(formCooldown / Math.max(1, val(FORM_CD, level) * 60));
            return 1;
        }
        boolean showShieldBar() { return form == YELLOW && level >= 3; }
        float shieldMaxNow() { return val(SH_PCT, level) / 100 * gridCapacity(); }
        String storageBarText() {
            if (showShieldBar()) return T("Máu khiên: ", "Shield HP: ") + compactNum(shield) + "/" + compactNum(shieldMaxNow());
            return T("Lưu trữ: ", "Stored: ") + compactNum(gridStored()) + "/" + compactNum(gridCapacity());
        }
        Color storageBarColor() { return showShieldBar() ? YELLOW_COLOR : Pal.powerBar; }
        float storageBarFraction() {
            boolean sh = showShieldBar(); float max = sh ? shieldMaxNow() : gridCapacity(), cur = sh ? shield : gridStored();
            return max > 0 ? cl(cur / max) : 0;
        }
        String cooldownBarText() {
            if (form == DEATH) return T("Hồi chiêu: Khóa", "Cooldown: Locked");
            if (asteroidPhase == AST_CHARGE) return T("Đang gồng: ", "Charging: ") + Strings.fixed(asteroidTimer / 60, 1) + "s";
            if (asteroidPhase == AST_FALL) return T("Va chạm: ", "Impact: ") + Strings.fixed(asteroidTimer / 60, 1) + "s";
            if (level < 5) return T("Hồi chiêu: --", "Cooldown: --");
            return T("Hồi chiêu: ", "Cooldown: ") + (int) Math.floor(skillCd * 100) + "%";
        }
        float cooldownBarFraction() {
            if (asteroidPhase == AST_CHARGE) return cl(1 - asteroidTimer / AST_CHARGE_TICKS);
            if (asteroidPhase == AST_FALL) return cl(1 - asteroidTimer / AST_TRAVEL);
            if (level < 5) return 0;
            return cl(skillCd);
        }

        // ============ vẽ
        void drawRangeRing() {
            if (ringStamp > Time.time || Time.time - ringStamp >= 15) { ringStamp = Time.time; ringRadius = affectRadiusWorld(); }
            if (ringRadius <= 0) return;
            Draw.z(Layer.blockOver); Draw.color(formColor(form), 0.5f); Lines.stroke(1.5f); Lines.circle(x, y, ringRadius); Draw.reset();
        }
        @Override public void draw() {
            super.draw();
            if (form == DEATH) return;
            drawRangeRing();
            float t = Time.time;
            if (hasEnoughPower() || asteroidPhase != AST_NONE) {
                glowCol.set(formColor(form));
                if (pendingForm >= 0) glowCol.lerp(formColor(pendingForm), changeProgress());
                Draw.z(Layer.block + 0.01f); drawGlow(x, y, glowCol, 0.8f + 0.2f * (float) Math.sin(t * 0.08), 1f);
            }
            Draw.z(Layer.blockOver);
            if (asteroidPhase != AST_NONE) { drawAsteroidBeam(t); Draw.reset(); return; }
            if (!hasEnoughPower()) return;
            if (pendingForm >= 0) {
                float p = changeProgress();
                drawAuraOf(form, t, 1 - p); drawAuraOf(pendingForm, t, p);
            } else {
                drawAuraOf(form, t, 1);
                drawSkillVisuals(t, formColor(form));
            }
            Draw.reset();
        }
        void drawAuraOf(int f, float t, float am) {
            auraAlpha = am;
            drawCorePulse(x, y, formColor(f), 1);
            if (f == YELLOW) drawYellowAura(x, y, level, t);
            else if (f == BLUE) drawBlueAura(x, y, t);
            else if (f == PURPLE) drawPurpleAura(x, y, t);
            auraAlpha = 1; Draw.reset();
        }
        void drawAsteroidBeam(float t) {
            float cx = Vars.world.unitWidth() / 2f, cy = Vars.world.unitHeight() / 2f;
            float ex = asteroidTx, ey = asteroidTy, progress = 1;
            if (asteroidPhase == AST_FALL) {
                float f = cl(1 - asteroidTimer / AST_TRAVEL);
                ex = lerp(asteroidTx, cx, f); ey = lerp(asteroidTy, cy, f);
            } else progress = cl(1 - asteroidTimer / AST_CHARGE_TICKS);
            float sf = 1 + (float) Math.sin(t * 0.1) * 0.25f;
            Draw.z(Layer.effect);
            beamTo(x, y, ex, ey, (5 + 11 * progress) * sf, BLUE_COLOR);
            // hạt xoắn quanh tia laser, chạy dọc tia (pháo -> thiên thạch)
            float dx = ex - x, dy = ey - y, dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 1) {
                float nx = dx / dist, ny = dy / dist, fwd = (float) (Math.atan2(dy, dx) / DEG);
                int N = (int) Math.min(260, Math.max(80, Math.floor(dist / 5)));
                float bw = (5 + 11 * progress) * sf;
                additive();
                for (int i = 0; i < N; i++) {
                    float ph = (float) ((t * (4 + rs(i * 7 + 1, 0, 7)) / dist + rs(i * 13 + 5, 0, 1)) % 1);
                    float len = rs(i * 11 + 3, 1, 3) * TS, rad = bw * rs(i * 17 + 9, 0.9, 2.2) + 4;
                    float lat = (float) Math.sin(ph * dist * 0.03 + rs(i * 3 + 2, 0, 6.28) + t * 0.12) * rad;
                    float bx = lerp(x, ex, ph) - ny * lat, by = lerp(y, ey, ph) + nx * lat;
                    Draw.color(Color.white, BLUE_COLOR, rs(i * 19 + 7, 0, 1)); Draw.alpha((float) Math.sin(ph * Math.PI) * 0.95f);
                    Lines.stroke(1 + rs(i * 23 + 1, 0, 1.4)); Lines.lineAngle(bx, by, fwd, len);
                }
                for (int k = 0; k < 4; k++) {
                    float q = (t * 0.018f + k / 4f) % 1;
                    Draw.color(BLUE_COLOR, Color.white, q); Draw.alpha(0.7f * (float) Math.sin(q * Math.PI)); Lines.stroke(2.5f * (1 - q) + 0.5f); Lines.circle(x, y, 90 * (1 - q) + 8);
                }
                Draw.blend();
                bloom(x, y, 30 + 40 * progress, BLUE_COLOR, 0.8f);
                bloom(ex, ey, 26 + 30 * progress, Color.orange, 0.7f);
                Draw.reset();
            }
        }
        void drawSkillVisuals(float t, Color fc) {
            float r = affectRadiusWorld(); ArrayList<Unit> enemies = cachedEnemies(r);
            for (Unit u : enemies) drawCorePulse(u.x, u.y, fc, 0.5f);
            if (form == YELLOW || form == PURPLE) {
                for (Unit u : enemies) if (u.hasEffect(StatusEffects.disarmed) && u.hasEffect(StatusEffects.unmoving)) drawStunMark(u.x, u.y, u.hitSize * 0.6f, t, fc);
            }
            if (form == YELLOW) drawYellowSkills(t, r, enemies);
            else if (form == BLUE) drawBlueSkills(t, r);
        }
        void drawYellowSkills(float t, float r, ArrayList<Unit> enemies) {
            for (Unit u : enemies) drawElectricRing(u.x, u.y, u.hitSize * 0.6f + 5, t);
            if (level >= 2 && orbCount > 0) {
                float cf = cl(sphereCharge / val(SPH_CHARGE, level)), orbR = 9 + 7 * cf, pz = Draw.z();
                Draw.z(ORB_Z);
                for (int i = 0; i < orbCount; i++) {
                    float[] p = orbPos(i);
                    drawOrb(p[0] + (float) Math.sin(t * 0.1 + i) * 2, p[1] + (float) Math.cos(t * 0.12 + i) * 2, orbR, cf, 2f);
                }
                Draw.z(pz);
            }
            if (level >= 3 && shield > 0 && shieldCooldown <= 0) {
                final float sr = shieldRadius(), cx = x, cy = y;
                Draw.z(Layer.shields);
                drawMulti((scale, ov, alpha, ss) -> {
                    Draw.color(ov != null ? ov : YELLOW_COLOR); Draw.alpha(alpha * 0.85f); Lines.stroke(4 * ss); Lines.circle(cx, cy, sr);
                    Draw.alpha(alpha * 0.05f); Fill.circle(cx, cy, sr); Draw.reset();
                });
                Draw.color(YELLOW_COLOR, 0.25f); Lines.stroke(1.5f); Lines.poly(cx, cy, 6, sr * 0.96f, 0);
                Draw.z(Layer.blockOver);
            }
            if (level >= 5 && empActiveTimer > 0 && r > 0) {
                for (Building b : enemyBuildings(r)) drawStunMark(b.x, b.y, b.block.size * TS / 2, t, YELLOW_COLOR);
            }
        }
        void drawBlades() {
            if (!isValid() || form != YELLOW || level < 4 || asteroidPhase != AST_NONE || pendingForm >= 0 || !hasEnoughPower()) return;
            float t = Time.time; int bc = (int) val(BL_COUNT, level); float create = val(BL_CREATE, level);
            Draw.z(BLADE_Z);
            for (int i = 0; i < bc; i++) {
                if (bladeX[i] == 0 && bladeY[i] == 0) continue;
                float prog = cl(blades[i] / create);
                drawSaw(bladeX[i], bladeY[i], BLADE_R, t * 25 + i * 40, prog >= 1 ? 1 : 0.15f + 0.35f * prog);
            }
            Draw.reset();
        }
        void drawBlueSkills(final float t, float r) {
            if (level < 4 || r <= 0) return;
            final float lr = r / LIM_DIV; final ThermionBuild self = this;
            Draw.color(BLUE_COLOR); Draw.alpha(0.35f); Lines.stroke(1.5f);
            for (int i = 0; i < 4; i++) Lines.arc(x, y, lr, 0.18f, i * 90);
            Draw.alpha(0.05f); Fill.circle(x, y, lr);
            Groups.bullet.intersect(x - lr, y - lr, lr * 2, lr * 2, b -> {
                if (b.team == self.team || !b.within(self.x, self.y, lr)) return;
                Draw.color(BLUE_COLOR, Color.white, 0.4f); Draw.alpha(0.9f); Lines.stroke(1);
                Lines.circle(b.x, b.y, 3 + (float) Math.sin(t * 0.4 + b.id) * 1);
            });
            Draw.reset();
        }
        @Override public void drawConfigure() {
            super.drawConfigure();
            float r = affectRadiusWorld();
            if (r > 0) drawRange(x, y, r, formColor(form));
        }
    } // ===== hết ThermionBuild

    static Bar mkBar(Prov<CharSequence> name, Prov<Color> color, Floatp fraction) { return new Bar(name, color, fraction); }

    // ---------- cutscene khi lên level 5 ----------
    // 1) camera lia qua đủ 4 góc map, mỗi góc hiện 1 "ngôi sao 2 cánh"; 2) camera bay về unit người chơi rồi trả quyền điều khiển.
    static final class CutScene { float[] pending = null; Color pendingCol = null; float age, t, ox, oy, sx, sy; boolean on; int stage; Color col = Color.sky; final float[] stars = new float[4]; }
    static final CutScene CS = new CutScene();
    static final float CS_PAN = 100, CS_HOLD = 50, CS_BACK = 90, STAR_LEN = TS * 8;
    static boolean markShown = false;
    static final Color markCol = new Color();
    static float layerOr(String name, float d) { try { return Layer.class.getField(name).getFloat(null); } catch (Throwable err) { return d; } }
    static Float markZ = null;
    static float getMarkZ() { if (markZ == null) markZ = Math.max(Math.max(layerOr("fogOfWar", 130), layerOr("light", 140)), layerOr("overlayUI", 120)) + 1; return markZ; }
    static float[] csCorner(int i) {
        float W = Vars.world.unitWidth(), H = Vars.world.unitHeight(), m = 4 * TS;
        float[][] c = {{m, m}, {W - m, m}, {W - m, H - m}, {m, H - m}};
        return c[i];
    }
    static float smooth(double p) { float q = cl(p); return q * q * (3 - 2 * q); }
    static ThermionBuild anyLevel5() {
        for (ThermionBuild b : SELF.values()) if (b.isValid() && b.getLevel() >= MAX_LEVEL) return b;
        return null;
    }
    static float[] playerPos() {
        Unit u = Vars.player.unit();
        if (u != null && !Vars.player.dead()) return new float[]{u.x, u.y};
        return new float[]{CS.ox, CS.oy};
    }
    static void startCutscene(float x, float y, Color col) { if (!CS.on && !Vars.headless && !markShown) { CS.pending = new float[]{x, y}; CS.pendingCol = col; } }
    static void beginCutscene(float x, float y, Color col) {
        CS.on = true; CS.age = 0; CS.stage = 0; CS.t = 0; CS.ox = x; CS.oy = y; CS.sx = Core.camera.position.x; CS.sy = Core.camera.position.y;
        CS.col = col; for (int i = 0; i < 4; i++) CS.stars[i] = 0;
        try { Vars.control.input.logicCutscene = true; } catch (Throwable ignored) {}
    }
    static void endCutscene() {
        CS.on = false;
        try { Vars.control.input.logicCutscene = false; } catch (Throwable ignored) {}
    }
    static void csCam(float x, float y) { Core.camera.position.set(x, y); }
    static void csUpdate() {
        // trạng thái bền: không có Thermion lv5 -> xóa; có sẵn lúc vào map (không có cutscene) -> hiện luôn đủ
        ThermionBuild b5 = anyLevel5();
        if (b5 == null) { if (!CS.on && CS.pending == null) markShown = false; }
        else if (!CS.on && CS.pending == null && !markShown) markShown = true;
        if (CS.pending != null && Vars.state.isGame() && !Vars.state.isPaused() && !Core.scene.hasDialog()) { float[] p = CS.pending; Color pc = CS.pendingCol; CS.pending = null; beginCutscene(p[0], p[1], pc); }
        if (!CS.on) return;
        if (!Vars.state.isGame()) { endCutscene(); return; }
        CS.t += Time.delta; CS.age += Time.delta;
        int st = CS.stage;
        if (st < 4) {
            float[] c = csCorner(st);
            if (CS.t < CS_PAN) {
                float p = smooth(CS.t / CS_PAN);
                csCam(lerp(CS.sx, c[0], p), lerp(CS.sy, c[1], p));
            } else {
                csCam(c[0], c[1]);
                if (CS.stars[st] == 0) { CS.stars[st] = 0.001f; FX_BLAST.at(c[0], c[1], 70, CS.col); Effect.shake(6, 12, c[0], c[1]); }
                CS.stars[st] = Math.min(1, CS.stars[st] + Time.delta / 25);
                if (CS.t >= CS_PAN + CS_HOLD) { CS.stage++; CS.t = 0; CS.sx = c[0]; CS.sy = c[1]; }
            }
        } else {
            float[] pp = playerPos(); float p = smooth(CS.t / CS_BACK);
            csCam(lerp(CS.sx, pp[0], p), lerp(CS.sy, pp[1], p));
            if (CS.t >= CS_BACK) { endCutscene(); markShown = true; }
        }
        try {
            if (CS.age > 60 && !Core.scene.hasDialog() && Core.input.keyTap(KeyCode.escape)) { endCutscene(); markShown = true; }
        } catch (Throwable ignored) {}
    }
    /** Ngôi sao 2 cánh trái/phải: 2 tam giác phát sáng nhấp nháy + vòng tròn đen đặc vẽ đè ở layer cao hơn. */
    static void drawWingStar(float x, float y, float len, float t, int i) {
        if (len <= 0) return;
        float pulse = 1 + 0.1f * (float) Math.sin(t * 0.08 + i * 1.7), flick = 0.85f + 0.15f * (float) Math.sin(t * 0.21 + i * 2.3);
        float L = len * pulse, w = len * 0.2f, z = getMarkZ();
        Draw.z(z);
        additive();
        Draw.color(markCol); Draw.alpha(0.45f * flick);
        for (int k = 0; k < 2; k++) Drawf.tri(x, y, w * 1.5f, L * 1.25f, k * 180);
        Draw.color(Color.white, markCol, 0.35f); Draw.alpha(0.95f * flick);
        for (int k = 0; k < 2; k++) Drawf.tri(x, y, w, L, k * 180);
        Draw.blend();
        Draw.z(z + 0.5f);
        Draw.color(Color.black); Fill.circle(x, y, w * 1.7f);
        Draw.reset();
    }
    /** tia từ pháo tới góc đang lia camera (chỉ trong lúc lia, tự mất sau đó) */
    static void csDraw() {
        if (!CS.on) return;
        int st = CS.stage;
        if (st >= 4 || CS.t >= CS_PAN + 20) return;
        float z = Draw.z(); Draw.z(getMarkZ());
        float[] c = csCorner(st); float p = smooth(CS.t / CS_PAN), hx = lerp(CS.ox, c[0], p), hy = lerp(CS.oy, c[1], p);
        beamTo(CS.ox, CS.oy, hx, hy, 4 * (1 - 0.5f * p), CS.col); bloom(hx, hy, 26, CS.col, 0.9f);
        Draw.reset(); Draw.z(z);
    }
    /** vẽ mỗi khung hình: 4 ngôi sao ở 4 góc, cho tới khi không còn Thermion level 5 */
    static void drawWorldMarks() {
        if (Vars.headless || !Vars.state.isGame()) return;
        ThermionBuild b = anyLevel5();
        if (b == null) return;
        b.fillMarkColor(markCol);
        float t = Time.time, cx = Core.camera.position.x, cy = Core.camera.position.y, hw = Core.camera.width / 2, hh = Core.camera.height / 2, pz = Draw.z();
        for (int i = 0; i < 4; i++) {
            float pop = markShown ? 1 : (CS.on ? CS.stars[i] : (CS.pending != null ? 0 : 1));
            if (pop <= 0) continue;
            float[] c = csCorner(i); float len = (float) (STAR_LEN * (1 - Math.pow(1 - pop, 3)));
            if (Math.abs(c[0] - cx) > hw + len * 1.8f || Math.abs(c[1] - cy) > hh + len * 1.8f) continue;
            drawWingStar(c[0], c[1], len, t, i);
        }
        Draw.z(pz); Draw.reset();
    }

    // ---------- đăng ký
    static boolean created = false, inited = false;
    /** Gọi trong NexMod.loadContent(): status, đạn, drone (JS tạo lúc nạp script). */
    public static void create() {
        if (created) return; created = true;
        loadSounds();
        upgradeAllFx();
        for (int i = 0; i < MAX_LEVEL; i++) {
            StatusEffect s = new StatusEffect("thermion-slow-" + (i + 1));
            s.speedMultiplier = 1 - SLOW[i] / 100; s.show = false; s.color = YELLOW_COLOR; slowEffects[i] = s;
        }
        weakened = new StatusEffect("thermion-weakened");
        weakened.speedMultiplier = 0.5f; weakened.reloadMultiplier = 0.5f; weakened.show = false; weakened.color = BLUE_COLOR;
        for (int i = 0; i < MAX_LEVEL; i++) orbBullets[i] = SPH_DMG[i] > 0 ? new OrbBullet(SPH_DMG[i]) : null;
        createDrones();
    }
    /** Gọi trong NexMod.init(): block do thermion.hjson (type: Block) tạo, Java chỉ gắn bar + buildType (bài 29b). */
    public static void init() {
        if (inited) return; inited = true;
        Events.on(WorldLoadEvent.class, e -> { CS.pending = null; if (CS.on) endCutscene(); markShown = false; });
        Events.run(Trigger.update, Thermion::csUpdate);
        Events.on(ContentInitEvent.class, e -> {
            thermion = Vars.content.block("newex-thermion");
            if (thermion == null) { Log.err("[Thermion] không tìm thấy block newex-thermion — kiểm tra content/blocks/thermion.hjson"); return; }
            thermion.addBar("thermion-form", (Building b) -> { ThermionBuild s = SELF.get(b.id); return mkBar(() -> s != null ? s.formBarText() : "", () -> s != null ? s.formBarColor() : GRAY_COLOR, () -> s != null ? s.formBarFraction() : 0f); });
            thermion.addBar("thermion-range", (Building b) -> { ThermionBuild s = SELF.get(b.id); return mkBar(() -> T("Phạm vi: ", "Range: ") + (s != null ? Strings.fixed(s.rangeBarValue(), 1) : "0"), () -> s != null ? formColor(s.getForm()) : GRAY_COLOR, () -> s != null ? cl(s.rangeBarValue() / RANGE_REF) : 0f); });
            thermion.addBar("thermion-storage", (Building b) -> { ThermionBuild s = SELF.get(b.id); return mkBar(() -> s != null ? s.storageBarText() : "", () -> s != null ? s.storageBarColor() : GRAY_COLOR, () -> s != null ? s.storageBarFraction() : 0f); });
            thermion.addBar("thermion-cooldown", (Building b) -> { ThermionBuild s = SELF.get(b.id); return mkBar(() -> s != null ? s.cooldownBarText() : "", () -> s != null ? formColor(s.getForm()) : GRAY_COLOR, () -> s != null ? s.cooldownBarFraction() : 0f); });
            thermion.buildType = ThermionBuild::new;
        });
        // vẽ lưỡi cưa trong hook toàn cục (không bị ẩn khi khối ngoài màn hình)
        Events.run(Trigger.draw, () -> {
            if (!Vars.state.isGame()) return;
            java.util.Iterator<ThermionBuild> it = SELF.values().iterator();
            while (it.hasNext()) {
                ThermionBuild b = it.next();
                if (!b.isValid()) { it.remove(); continue; }
                b.drawBlades();
            }
            csDraw();
            drawWorldMarks();
        });
    }
}
