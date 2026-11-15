package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.entities.units.StatusEntry;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Tile;

import java.util.ArrayList;

/**
 * Chuyển từ cos.js. Đơn vị "cos" (hjson tạo, Java chỉ vá ở ContentInitEvent):
 * crit 50% x3, buff x3 khi có Cos gần, lá chắn 100000 khi dưới 50% máu, xung sét khi di chuyển,
 * laser xung (sau 100 phát/mỗi súng A), tia MEGA sau 12 lần súng B trúng (sạc 5 giây),
 * chết sinh 3 Corvus. Phần hiệu ứng (chargeFx, edgeFx, rubbleFx...) chép đúng công thức JS.
 */
public final class Cos {
    static final float CRIT_CHANCE = 0.5f, CRIT_MULTIPLIER = 3f, FOOT_UNIT_MULTIPLIER = 1.5f;
    static final int PULSAL_SHOTS_NEEDED = 100;
    static final float PULSAL_DURATION = 600f, PULSAL_LASER_DAMAGE = 420f, PULSAL_LASER_RANGE = 420f * 1.5f * 2f;
    static final float LIGHTNING_INTERVAL = 60f, LIGHTNING_RADIUS = 20f * Vars.tilesize;
    static final float LIGHTNING_HEAL_PERCENT = 0.10f, LIGHTNING_ENEMY_MAX_HP_DAMAGE = 0.10f;
    static final float BUFF_RADIUS = 10f * Vars.tilesize, BUFF_MULTIPLIER = 3f, BUFF_CHECK_INTERVAL = 10f;
    static final float SHIELD_THRESHOLD = 0.5f, SHIELD_MAX_HEALTH = 100000f;
    static final int BEAM_HITS_NEEDED = 12;
    static final float BEAM_CHARGE_TIME = 300f, BEAM_STAR_TIME = 60f, BEAM_DAMAGE = 10000f, BEAM_SCALE = 5f;
    static final float BEAM_FRONT_OFFSET = 4f * Vars.tilesize, BEAM_BASE_LENGTH = 160f, BEAM_BASE_WIDTH = 15f;
    static final int BEAM_ROWS_PER_TICK = 6, BEAM_MAX_HALF_WIDTH = 12;
    static final float BEAM_AIM_INTERVAL = 10f;
    static final boolean BEAM_DESTROY_ALLY_BLOCKS = true;
    static final float BEAM_RING_SPACING = 18f * Vars.tilesize, BEAM_RING_TRAVEL = 12f * Vars.tilesize;
    static final float BEAM_RING_LIFE = 24f, BEAM_RING_SIZE = 1.2f, BEAM_RING_WAVE_SPEED = 40f;
    static final int BEAM_RING_MAX = 40;
    static final float BEAM_EDGE_LIFE = 55f;
    static final int CHARGE_PARTICLES = 28;
    static final float CHARGE_RADIUS = 100f;
    static final int EDGE_MAX = 110, RUBBLE_MAX = 140;
    static final float RUBBLE_HOLD = 60f, RUBBLE_FADE = 60f, RUBBLE_POP = 14f;
    static final float RUBBLE_LIFE = RUBBLE_HOLD + RUBBLE_FADE + 36f;
    static final float RUBBLE_BAND = 4f * Vars.tilesize;
    static final float DEG = (float) (Math.PI / 180.0);
    static final int PT = 17, CK = 6, RB = 13, CP = 9, AR = 9, RG = 7;

    static final Color laserColor = Color.valueOf("84f4ff");
    static final Color lightningColor = Color.valueOf("84f4ff");
    static final Color chargeColor = Color.valueOf("b2ffc4");
    static final Color rockColor = Color.valueOf("a8a08c");
    static final Color rockDark = Color.valueOf("5f5a4c");
    static final Color rockLight = Color.valueOf("d2cbb4");
    static final Color dustColor = Color.valueOf("7d7868");
    static final Color hotColor = Color.valueOf("ffb36b");
    static final Color crackDark = Color.valueOf("3a362e");

    static final IntMap<State> states = new IntMap<>();
    static final ArrayList<Beam> beams = new ArrayList<>();
    static float bBeamLength = BEAM_BASE_LENGTH, bBeamWidth = BEAM_BASE_WIDTH;

    static final int RING_SEG = 24, WARP_SEG = 36;
    static final float[] RC = new float[RING_SEG + 1], RS = new float[RING_SEG + 1];
    static final float[] WC = new float[WARP_SEG + 1], WS = new float[WARP_SEG + 1];
    static final float[] cam = new float[4];

    static {
        for (int j = 0; j <= RING_SEG; j++) {
            RC[j] = (float) Math.cos(j * 2 * Math.PI / RING_SEG);
            RS[j] = (float) Math.sin(j * 2 * Math.PI / RING_SEG);
        }
        for (int j = 0; j <= WARP_SEG; j++) {
            WC[j] = (float) Math.cos(j * 2 * Math.PI / WARP_SEG);
            WS[j] = (float) Math.sin(j * 2 * Math.PI / WARP_SEG);
        }
    }

    static final class State {
        int[] shotsWeaponA = {0, 0};
        float pulsalTimer = 0f;
        float[] pulsalLaserTimer = {0f, 0f};
        float lightningTimer = 0f;
        boolean hasNearbyCosBuff = false;
        float buffTimer = (float) (Math.random() * BUFF_CHECK_INTERVAL);
        boolean shieldActivated = false;
        float lastX, lastY;
        float[] reloads;
        int[] kinds;
        int hitsB = 0;
        boolean charging = false;
        float chargeTimer = 0f, chargeAngle = 0f, aimTimer = 0f;
        boolean starDone = false;
        float[] pp;
        int[] pc;
        float[] rg, ar;
        int arB = -1;
    }

    static final class Beam {
        float x, y, cos, sin;
        int row, rows, half;
        Team team;
    }

    /** Dữ liệu tia (JS buildBeamData trả object literal). */
    static final class BeamData {
        float L, half, eh, step;
        int n, ptCount, rbCount;
        float[] pt, ck, rb;
    }

    static final LaserBulletType megaLaser = new LaserBulletType() {{
        damage = BEAM_DAMAGE;
        lifetime = 40f;
        length = BEAM_BASE_LENGTH * BEAM_SCALE;
        width = BEAM_BASE_WIDTH * BEAM_SCALE;
        largeHit = true;
        laserAbsorb = false;
    }};

    static StatusEffect cosStatus;
    static boolean patched = false;

    private Cos() {}

    // ---------- tiện ích toán ----------
    static float rnd(double seed, double k) {
        double n = Math.sin(seed * 12.9898 + k * 78.233) * 43758.5453;
        return (float) (n - Math.floor(n));
    }

    static float fxSeed(Effect.EffectContainer e) {
        return (e.id % 1000) * 100000f;
    }

    static float clamp01(float x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    static float easeOut(float x, float p) {
        return 1f - (float) Math.pow(1f - clamp01(x), p);
    }

    static float cd(float deg) { return (float) Math.cos(deg * DEG); }
    static float sd(float deg) { return (float) Math.sin(deg * DEG); }
    static float rand() { return (float) Math.random(); }

    static void updateCam(float margin) {
        float hw = Core.camera.width / 2f + margin;
        float hh = Core.camera.height / 2f + margin;
        cam[0] = Core.camera.position.x - hw;
        cam[1] = Core.camera.position.y - hh;
        cam[2] = Core.camera.position.x + hw;
        cam[3] = Core.camera.position.y + hh;
    }

    static boolean inCam(float x, float y) {
        return x >= cam[0] && x <= cam[2] && y >= cam[1] && y <= cam[3];
    }

    static boolean segVisible(float x1, float y1, float x2, float y2) {
        return !(Math.max(x1, x2) < cam[0] || Math.min(x1, x2) > cam[2] || Math.min(y1, y2) > cam[3] || Math.max(y1, y2) < cam[1]);
    }

    static void wobbleRing(float cx, float cy, float r, float amp, float f1, float f2, float p1, float p2, float s1, float s2, float time) {
        float lx = 0, ly = 0;
        for (int j = 0; j <= RING_SEG; j++) {
            float ang = j * 15f;
            float rr = r + (float) Math.sin((ang * f1 + p1 + time * s1) * DEG) * amp + (float) Math.sin((ang * f2 + p2 - time * s2) * DEG) * amp * 0.6f;
            float x = cx + RC[j] * rr;
            float y = cy + RS[j] * rr;
            if (j > 0) Lines.line(lx, ly, x, y);
            lx = x;
            ly = y;
        }
    }

    static void wobbleRingSeed(float cx, float cy, float r, float amp, float s, float time) {
        wobbleRing(cx, cy, r, amp,
            2 + (float) Math.floor(rnd(s, 3) * 3), 3 + (float) Math.floor(rnd(s, 4) * 4),
            rnd(s, 1) * 360f, rnd(s, 2) * 360f,
            5 + rnd(s, 5) * 8, 7 + rnd(s, 6) * 10, time);
    }

    static void rollParticle(float[] pp, int o) {
        pp[o + 2] = rand() * 360f;
        pp[o + 3] = CHARGE_RADIUS * (0.4f + rand());
        pp[o + 4] = (rand() - 0.5f) * 300f;
        pp[o + 5] = 0.7f + rand() * 2.3f;
        pp[o + 6] = rand() * 360f;
        pp[o + 7] = rand() * 7f;
        pp[o + 8] = 0.05f + rand() * 0.1f;
    }

    static void rollArcs(float[] ar) {
        for (int j = 0; j < 8; j++) {
            int o = j * AR;
            ar[o] = rand() < 0.35f ? 1f : 0f;
            ar[o + 1] = rand() * 360f;
            ar[o + 2] = 1.3f + rand() * 2.2f;
            ar[o + 3] = rand();
            ar[o + 4] = 0.4f + 0.6f * rand();
            ar[o + 5] = 0.8f + rand() * 1.4f;
            ar[o + 6] = rand() - 0.5f;
            ar[o + 7] = rand() - 0.5f;
            ar[o + 8] = rand() - 0.5f;
        }
    }

    static void initChargeCache(State s) {
        float[] pp = new float[CHARGE_PARTICLES * CP];
        int[] pc = new int[CHARGE_PARTICLES];
        for (int i = 0; i < CHARGE_PARTICLES; i++) {
            pc[i] = -1;
            pp[i * CP] = 26f + rand() * 44f;
            pp[i * CP + 1] = rand();
            rollParticle(pp, i * CP);
        }
        float[] rg = new float[3 * RG];
        for (int k = 0; k < 3; k++) {
            int o = k * RG;
            rg[o] = 2 + (float) Math.floor(rand() * 3);
            rg[o + 1] = 3 + (float) Math.floor(rand() * 4);
            rg[o + 2] = rand() * 360f;
            rg[o + 3] = rand() * 360f;
            rg[o + 4] = 5 + rand() * 8;
            rg[o + 5] = 7 + rand() * 10;
            rg[o + 6] = rand();
        }
        float[] ar = new float[8 * AR];
        rollArcs(ar);
        s.pp = pp;
        s.pc = pc;
        s.rg = rg;
        s.ar = ar;
        s.arB = -1;
    }

    static BeamData buildBeamData(float eh) {
        BeamData d = new BeamData();
        float L = megaLaser.length;
        float half = megaLaser.width / 2f;
        float step = 3f * Vars.tilesize;
        int n = (int) Math.ceil(L / step);

        int ptCount = Math.min(EDGE_MAX, Math.round(L / 6f));
        float[] pt = new float[ptCount * PT];
        for (int i = 0; i < ptCount; i++) {
            int o = i * PT;
            float dd = rand() * L;
            float a0 = rand() * 360f * DEG;
            float a1 = a0 + (120f + rand() * 30f) * DEG;
            float a2 = a0 + (230f + rand() * 30f) * DEG;
            pt[o] = dd;
            pt[o + 1] = rand() < 0.5f ? -1f : 1f;
            pt[o + 2] = rand();
            pt[o + 3] = (dd / L) * 0.22f + rand() * 0.08f;
            pt[o + 4] = 25f + rand() * 130f;
            pt[o + 5] = (rand() - 0.5f) * 40f;
            pt[o + 6] = half * (0.85f + rand() * 0.3f);
            pt[o + 7] = 3.5f + rand() * 7f;
            pt[o + 8] = (rand() - 0.5f) * 720f;
            pt[o + 9] = rand();
            pt[o + 10] = (float) Math.cos(a0);
            pt[o + 11] = (float) Math.sin(a0);
            pt[o + 12] = (float) Math.cos(a1) * 0.8f;
            pt[o + 13] = (float) Math.sin(a1) * 0.8f;
            pt[o + 14] = (float) Math.cos(a2) * 0.9f;
            pt[o + 15] = (float) Math.sin(a2) * 0.9f;
            pt[o + 16] = 0f;
        }

        float[] ck = new float[2 * (n + 1) * CK];
        int ci = 0;
        for (int side = 0; side < 2; side++) {
            for (int j = 0; j <= n; j++) {
                ck[ci++] = 3f + rand() * 14f;
                ck[ci++] = 0.8f + rand() * 2f;
                ck[ci++] = rand() > 0.72f ? 1f : 0f;
                ck[ci++] = 50f + rand() * 40f;
                ck[ci++] = 18f + rand() * 50f;
                ck[ci++] = (rand() - 0.5f) * 10f;
            }
        }

        int rbCount = Math.min(RUBBLE_MAX, Math.round(L / 5f));
        float[] rb = new float[rbCount * RB];
        for (int i = 0; i < rbCount; i++) {
            int o = i * RB;
            float dd = rand() * L;
            boolean big = rand() < 0.12f;
            float a0 = rand() * 360f * DEG;
            float a1 = a0 + (110f + rand() * 40f) * DEG;
            float a2 = a0 + (225f + rand() * 40f) * DEG;
            rb[o] = dd;
            rb[o + 1] = rand() < 0.5f ? -1f : 1f;
            rb[o + 2] = Math.min(30f, dd / (BEAM_ROWS_PER_TICK * Vars.tilesize)) + rand() * 5f;
            rb[o + 3] = eh + (rand() * 1.3f - 0.3f) * RUBBLE_BAND;
            rb[o + 4] = big ? 10f + rand() * 6f : 3.5f + rand() * 6f;
            rb[o + 5] = rand();
            rb[o + 6] = (rand() - 0.5f) * 540f;
            rb[o + 7] = (float) Math.cos(a0);
            rb[o + 8] = (float) Math.sin(a0);
            rb[o + 9] = (float) Math.cos(a1) * 0.8f;
            rb[o + 10] = (float) Math.sin(a1) * 0.8f;
            rb[o + 11] = (float) Math.cos(a2) * 0.9f;
            rb[o + 12] = (float) Math.sin(a2) * 0.9f;
        }

        d.L = L; d.half = half; d.eh = eh; d.step = step; d.n = n;
        d.pt = pt; d.ptCount = ptCount; d.ck = ck; d.rb = rb; d.rbCount = rbCount;
        return d;
    }

    // ---------- hiệu ứng ----------
    static final Effect pulsalLaserFx = new Effect(15f, 900f, e -> {
        if (!(e.data instanceof Vec2)) return;
        Vec2 target = (Vec2) e.data;
        float f = e.fout();
        Draw.color(laserColor);
        Lines.stroke(8f * f);
        Lines.line(e.x, e.y, target.x, target.y);
        Fill.circle(e.x, e.y, 6f * f);
        Draw.color(Color.white);
        Lines.stroke(3f * f);
        Lines.line(e.x, e.y, target.x, target.y);
        Fill.circle(e.x, e.y, 4f * f);
        Draw.reset();
    });

    static final Effect activateFx = new Effect(40f, 200f, e -> {
        float f = e.fout();
        Draw.color(Color.valueOf("84f4ff"), Color.white, f);
        Lines.stroke(2f * f);
        Lines.circle(e.x, e.y, e.finpow() * 40f);
        Fill.circle(e.x, e.y, 6f * f);
        Draw.reset();
    });

    static final Effect chargeFx = new Effect(BEAM_CHARGE_TIME, 4000f, e -> {
        if (!(e.data instanceof Unit)) return;
        Unit u = (Unit) e.data;
        if (u.dead || !u.isAdded()) return;
        State s = states.get(u.id);
        if (s == null || s.pp == null) return;

        float f = e.fin();
        float time = e.time;
        float a = s.chargeAngle;
        float ca = cd(a), sa = sd(a);
        float px = u.x + ca * BEAM_FRONT_OFFSET;
        float py = u.y + sa * BEAM_FRONT_OFFSET;
        float len = megaLaser.length;
        float ex = px + ca * len;
        float ey = py + sa * len;

        updateCam(260f);
        if (!segVisible(px, py, ex, ey)) return;
        updateCam(CHARGE_RADIUS * 2f);

        if (inCam(px, py)) {
            float[] pp = s.pp, rg = s.rg, ar = s.ar;
            int[] pc = s.pc;

            float pulse = 1f + 0.12f * (float) Math.sin(time * 7 * DEG) + 0.07f * (float) Math.sin((time * 19 + 40) * DEG) + 0.04f * (float) Math.sin(time * 43 * DEG);
            float coreR = (6f + f * 30f) * pulse;

            for (int k = 0; k < 3; k++) {
                int o = k * RG;
                float q = (time / (48f + k * 13f) + rg[o + 6]) % 1f;
                float r = CHARGE_RADIUS * 0.85f * (1f - q * q) + coreR * 0.4f;
                Draw.color(chargeColor, Color.white, q * 0.5f);
                Draw.alpha((float) Math.sin(q * Math.PI) * (0.25f + 0.35f * f));
                Lines.stroke(0.8f + 1.8f * q);
                wobbleRing(px, py, r, r * 0.07f, rg[o], rg[o + 1], rg[o + 2], rg[o + 3], rg[o + 4], rg[o + 5], time);
            }

            float active = 0.3f + 0.7f * f;
            for (int i = 0; i < CHARGE_PARTICLES; i++) {
                float gate = clamp01(active * CHARGE_PARTICLES - i);
                if (gate <= 0) break;

                int o = i * CP;
                float t = time / pp[o] + pp[o + 1];
                int cyc = (int) Math.floor(t);
                float p = t - cyc;
                if (pc[i] != cyc) {
                    pc[i] = cyc;
                    rollParticle(pp, o);
                }

                float startR = pp[o + 3];
                float twist = pp[o + 4];
                float p2 = p - pp[o + 8];
                if (p2 < 0) p2 = 0;
                float w = (float) Math.sin((time * 11 + pp[o + 6]) * DEG) * pp[o + 7] * (1f - p);

                float an = (pp[o + 2] + twist * p * p) * DEG;
                float cA = (float) Math.cos(an), sA = (float) Math.sin(an);
                float r = startR * (1f - p * p);
                float x = px + cA * r - sA * w;
                float y = py + sA * r + cA * w;

                float an2 = (pp[o + 2] + twist * p2 * p2) * DEG;
                float r2 = startR * (1f - p2 * p2);
                float x2 = px + (float) Math.cos(an2) * r2 - (float) Math.sin(an2) * w;
                float y2 = py + (float) Math.sin(an2) * r2 + (float) Math.cos(an2) * w;

                float size = pp[o + 5];
                Draw.color(chargeColor, Color.white, p * p);
                Draw.alpha(clamp01(p * 7f) * (0.45f + 0.55f * p) * gate);
                Lines.stroke(size * 0.6f);
                Lines.line(x, y, x2, y2);
                Fill.circle(x, y, size * (0.7f + 0.6f * p));
            }

            int bucket = (int) Math.floor(time / 3f);
            if (s.arB != bucket) {
                s.arB = bucket;
                rollArcs(ar);
            }
            int arcs = 2 + (int) Math.floor(f * 6f);
            for (int j = 0; j < arcs; j++) {
                int o = j * AR;
                if (ar[o] > 0) continue;
                float cA = cd(ar[o + 1]), sA = sd(ar[o + 1]);
                float start = coreR * 0.7f;
                float alen = coreR * ar[o + 2];
                float lx = px + cA * start;
                float ly = py + sA * start;
                Draw.color(Color.white, chargeColor, ar[o + 3]);
                Draw.alpha(ar[o + 4] * (0.4f + 0.6f * f));
                Lines.stroke(ar[o + 5]);
                for (int i = 1; i <= 4; i++) {
                    float d = start + alen * i / 4f;
                    // JS: ar[o + 5 + i] (i=1 trùng ar[o+6]... i=4 đã loại vì off = 0)
                    float off = i == 4 ? 0f : ar[o + 5 + i] * coreR * 0.6f;
                    float x = px + cA * d - sA * off;
                    float y = py + sA * d + cA * off;
                    Lines.line(lx, ly, x, y);
                    lx = x;
                    ly = y;
                }
            }

            Draw.color(chargeColor);
            Draw.alpha(0.12f);
            Fill.circle(px, py, coreR * 1.7f);
            Draw.alpha(0.22f);
            Fill.circle(px, py, coreR * 1.25f);
            Draw.alpha(0.4f);
            Fill.circle(px, py, coreR);
            Draw.color(Color.white);
            Draw.alpha(0.85f);
            Fill.circle(px, py, coreR * 0.45f);
        }

        Draw.color(chargeColor);
        Draw.alpha((0.12f + 0.2f * f) * (0.85f + 0.15f * (float) Math.sin(time * 17 * DEG)));
        Lines.stroke(1.5f + f);
        Lines.line(px, py, ex, ey);
        Draw.reset();
    });

    static final Effect starFx = new Effect(36f, 600f, e -> {
        float f = e.fin();
        float sdd = fxSeed(e) + 70000f;
        float k = Mathf.curve(f, 0f, 0.25f) * (1f - Mathf.curve(f, 0.25f, 1f));
        k *= 0.9f + 0.1f * (float) Math.sin(e.time * 53 * DEG);
        float rot = e.rotation + f * 30f;

        Draw.color(chargeColor);
        for (int i = 0; i < 4; i++) {
            float ln = 135f + rnd(sdd, i + 1) * 55f;
            Drawf.tri(e.x, e.y, 18f * k, ln * k, rot + i * 90f + (rnd(sdd, i + 8) - 0.5f) * 6f);
            float ln2 = 55f + rnd(sdd, i + 20) * 45f;
            Drawf.tri(e.x, e.y, 10f * k, ln2 * k, rot + 45f + i * 90f + (rnd(sdd, i + 28) - 0.5f) * 12f);
        }
        Draw.color(Color.white);
        for (int i = 0; i < 4; i++) {
            Drawf.tri(e.x, e.y, 8f * k, (90f + rnd(sdd, i + 40) * 40f) * k, rot + i * 90f);
        }
        Fill.circle(e.x, e.y, 10f * k);

        for (int i = 0; i < 12; i++) {
            float ang = rnd(sdd, 50 + i) * 360f * DEG;
            float ln = 40f + rnd(sdd, 70 + i) * 130f;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            Draw.color(chargeColor, Color.white, rnd(sdd, 90 + i));
            Draw.alpha(0.7f * k);
            Lines.stroke((0.6f + rnd(sdd, 110 + i)) * 1.6f * k);
            Lines.line(e.x + c * 8f, e.y + s * 8f, e.x + c * ln * k, e.y + s * ln * k);
        }

        for (int i = 0; i < 24; i++) {
            float local = clamp01((f - 0.18f - rnd(sdd, 130 + i) * 0.12f) / 0.62f);
            if (local <= 0 || local >= 1) continue;
            float ang = rnd(sdd, 150 + i) * 360f * DEG;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float dist = (30f + rnd(sdd, 170 + i) * 150f) * easeOut(local, 2.2f);
            float back = dist * 0.82f;
            Draw.color(Color.white, chargeColor, local);
            Draw.alpha(1f - local);
            Lines.stroke(1.2f * (1f - local));
            Lines.line(e.x + c * back, e.y + s * back, e.x + c * dist, e.y + s * dist);
            Fill.circle(e.x + c * dist, e.y + s * dist, (1f + rnd(sdd, 190 + i) * 1.8f) * (1f - local));
        }

        Draw.color(chargeColor);
        Draw.alpha(1f - f);
        Lines.stroke(1f + 2.5f * (1f - f));
        wobbleRingSeed(e.x, e.y, 10f + 50f * e.finpow(), 3f * (1f - f), sdd, e.time);
        Draw.reset();
    });

    static void drawWarpRing(float cx, float cy, float radiusX, float radiusY, float angle, float strokeWidth, Color color) {
        Draw.color(color);
        Lines.stroke(strokeWidth);
        float lastX = 0, lastY = 0;
        float cosA = cd(angle), sinA = sd(angle);
        for (int i = 0; i <= WARP_SEG; i++) {
            float lx = WC[i] * radiusX;
            float ly = WS[i] * radiusY;
            float rx = cx + (lx * cosA - ly * sinA);
            float ry = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, rx, ry);
            lastX = rx;
            lastY = ry;
        }
    }

    static final Effect warpRingFx = new Effect(BEAM_RING_LIFE, 900f, e -> {
        float t = e.fin();
        float progress = e.fout();
        float maxY = megaLaser.width * 0.5f * BEAM_RING_SIZE;
        float baseY = maxY / 7f;
        float baseX = baseY * 0.4f;
        float off = BEAM_RING_TRAVEL * (50f / 64f) - t * BEAM_RING_TRAVEL;
        float cx = e.x + cd(e.rotation) * off;
        float cy = e.y + sd(e.rotation) * off;

        updateCam(maxY + 40f);
        if (!inCam(cx, cy)) return;

        float stroke = Math.max(2f, maxY * 0.07f) * progress;
        drawWarpRing(cx, cy, baseX * (1f + t * 6f), baseY * (1f + t * 6f), e.rotation, stroke, chargeColor);
        Draw.reset();
    });

    static final Effect edgeFx = new Effect(BEAM_EDGE_LIFE, 10000f, e -> {
        if (!(e.data instanceof BeamData)) return;
        BeamData fd = (BeamData) e.data;
        float L = fd.L, half = fd.half;
        float rot = e.rotation * DEG;
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);

        updateCam(300f);
        if (!segVisible(e.x, e.y, e.x + cs * L, e.y + sn * L)) return;
        updateCam(90f);

        float f = e.fin(), fo = e.fout();
        float[] ck = fd.ck;
        int n = fd.n;
        float step = fd.step;
        float sweep = clamp01(f / 0.2f) * L;

        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? -1f : 1f;
            int base = side * (n + 1) * CK;
            float lx = 0, ly = 0;
            for (int j = 0; j <= n; j++) {
                float d = Math.min(L, j * step);
                if (d > sweep) break;
                int o = base + j * CK;
                float lat = sg * (half + ck[o] + f * 10f);
                float x = e.x + cs * d - sn * lat;
                float y = e.y + sn * d + cs * lat;
                boolean vis = inCam(x, y);

                if (j > 0 && (vis || inCam(lx, ly))) {
                    Draw.color(Color.white, chargeColor, f);
                    Draw.alpha(0.4f + 0.6f * fo);
                    Lines.stroke(ck[o + 1] * (0.3f + 0.7f * fo));
                    Lines.line(lx, ly, x, y);
                }

                if (j > 0 && vis && ck[o + 2] > 0) {
                    float bAng = (e.rotation + sg * ck[o + 3]) * DEG;
                    float bc = (float) Math.cos(bAng), bs = (float) Math.sin(bAng);
                    float bLen = ck[o + 4] * easeOut(f * 2.5f, 2f);
                    float mo = ck[o + 5];
                    float mx = x + bc * bLen * 0.5f - bs * mo;
                    float my = y + bs * bLen * 0.5f + bc * mo;
                    Draw.color(chargeColor, Color.white, 0.4f);
                    Draw.alpha(0.6f * fo);
                    Lines.stroke(0.8f + fo);
                    Lines.line(x, y, mx, my);
                    Lines.line(mx, my, x + bc * bLen, y + bs * bLen);
                }
                lx = x;
                ly = y;
            }
        }

        float[] pt = fd.pt;
        for (int i = 0; i < fd.ptCount; i++) {
            int o = i * PT;
            float delay = pt[o + 3];
            float local = (f - delay) / (1f - delay);
            if (local <= 0 || local >= 1) continue;

            float sg = pt[o + 1];
            float q = 1f - local;
            float ease = 1f - q * q * (float) Math.sqrt(q);
            float out = pt[o + 6] + pt[o + 4] * ease;
            float along = pt[o] + pt[o + 5] * ease;
            float x = e.x + cs * along - sn * sg * out;
            float y = e.y + sn * along + cs * sg * out;
            if (!inCam(x, y)) continue;

            float kind = pt[o + 2];
            if (kind < 0.5f) {
                float sz = pt[o + 7] * (1f - local * 0.5f);
                float ang = pt[o + 8] * local * DEG;
                float ca = (float) Math.cos(ang) * sz, sa = (float) Math.sin(ang) * sz;
                Draw.color(rockColor, dustColor, pt[o + 9]);
                Draw.alpha(1f - local * local * local);
                Fill.tri(
                    x + pt[o + 10] * ca - pt[o + 11] * sa, y + pt[o + 10] * sa + pt[o + 11] * ca,
                    x + pt[o + 12] * ca - pt[o + 13] * sa, y + pt[o + 12] * sa + pt[o + 13] * ca,
                    x + pt[o + 14] * ca - pt[o + 15] * sa, y + pt[o + 14] * sa + pt[o + 15] * ca);
            } else if (kind < 0.8f) {
                float ln = 3f + pt[o + 4] * 0.06f * q;
                Draw.color(Color.white, hotColor, local);
                Draw.alpha(q);
                Lines.stroke(1.4f * q + 0.3f);
                Lines.line(x, y, x + sn * sg * ln, y - cs * sg * ln);
                Fill.circle(x, y, 1.2f * q);
            } else {
                Draw.color(dustColor);
                Draw.alpha(0.22f * q);
                Fill.circle(x, y, 2f + pt[o + 7] * 0.9f * (0.5f + local));
            }
        }
        Draw.reset();
    });

    static final Effect rubbleFx = new Effect(RUBBLE_LIFE, 10000f, e -> {
        if (!(e.data instanceof BeamData)) return;
        BeamData fd = (BeamData) e.data;
        float L = fd.L;
        float rot = e.rotation * DEG;
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);

        updateCam(300f);
        if (!segVisible(e.x, e.y, e.x + cs * L, e.y + sn * L)) return;
        updateCam(60f);

        Draw.z(Layer.debris);
        float time = e.time;
        float eh = fd.eh;

        float gl = time < RUBBLE_HOLD + 16f ? 1f : 1f - (time - RUBBLE_HOLD - 16f) / RUBBLE_FADE;
        if (gl > 0) {
            float[] ck = fd.ck;
            int n = fd.n;
            float step = fd.step;
            float reveal = Math.min(L, time * BEAM_ROWS_PER_TICK * Vars.tilesize);
            float cool = clamp01(time / 70f);
            for (int side = 0; side < 2; side++) {
                float sg = side == 0 ? -1f : 1f;
                int base = side * (n + 1) * CK;
                float lx = 0, ly = 0;
                for (int j = 0; j <= n; j++) {
                    float d = Math.min(L, j * step);
                    if (d > reveal) break;
                    int o = base + j * CK;
                    float lat = sg * (eh + ck[o] * 0.5f);
                    float x = e.x + cs * d - sn * lat;
                    float y = e.y + sn * d + cs * lat;
                    if (j > 0 && (inCam(x, y) || inCam(lx, ly))) {
                        Draw.color(crackDark);
                        Draw.alpha(0.7f * gl);
                        Lines.stroke(ck[o + 1] * 1.3f);
                        Lines.line(lx, ly, x, y);
                        Draw.color(hotColor, crackDark, cool);
                        Draw.alpha((1f - cool) * 0.8f * gl);
                        Lines.stroke(ck[o + 1] * 0.5f);
                        Lines.line(lx, ly, x, y);
                    }
                    lx = x;
                    ly = y;
                }
            }
        }

        float[] rb = fd.rb;
        float start = fd.half * 0.6f;
        for (int i = 0; i < fd.rbCount; i++) {
            int o = i * RB;
            float tl = time - rb[o + 2];
            if (tl <= 0) continue;
            float a = tl <= RUBBLE_HOLD ? 1f : 1f - (tl - RUBBLE_HOLD) / RUBBLE_FADE;
            if (a <= 0) continue;

            float pop = tl >= RUBBLE_POP ? 1f : easeOut(tl / RUBBLE_POP, 2f);
            float sg = rb[o + 1];
            float lat = sg * (start + (rb[o + 3] - start) * pop);
            float x = e.x + cs * rb[o] - sn * lat;
            float y = e.y + sn * rb[o] + cs * lat;
            if (pop < 1) y += (float) Math.sin(pop * Math.PI) * 10f;
            if (!inCam(x, y)) continue;

            float sz = rb[o + 4] * (0.7f + 0.3f * a);
            float v0x = rb[o + 7], v0y = rb[o + 8], v1x = rb[o + 9], v1y = rb[o + 10], v2x = rb[o + 11], v2y = rb[o + 12];
            float ca = sz, sa = 0f;
            if (pop < 1) {
                float ang = rb[o + 6] * (1f - pop) * DEG;
                ca = (float) Math.cos(ang) * sz;
                sa = (float) Math.sin(ang) * sz;
            }
            float x0 = x + v0x * ca - v0y * sa, y0 = y + v0x * sa + v0y * ca;
            float x1 = x + v1x * ca - v1y * sa, y1 = y + v1x * sa + v1y * ca;
            float x2 = x + v2x * ca - v2y * sa, y2 = y + v2x * sa + v2y * ca;

            Draw.color(Color.black);
            Draw.alpha(0.3f * a);
            Fill.tri(x0 + 1.5f, y0 - 1.5f, x1 + 1.5f, y1 - 1.5f, x2 + 1.5f, y2 - 1.5f);
            Draw.color(rockDark, rockColor, rb[o + 5]);
            Draw.alpha(a);
            Fill.tri(x0, y0, x1, y1, x2, y2);
            Draw.color(rockLight);
            Draw.alpha(0.5f * a);
            Fill.tri(x0, y0, x1, y1, x, y);
        }
        Draw.reset();
    });

    static final Effect muzzleBurstFx = new Effect(32f, 800f, e -> {
        float f = e.fin(), fo = e.fout();
        float sdd = fxSeed(e) + 90000f;

        Draw.color(chargeColor);
        Draw.alpha(0.5f * fo);
        Fill.circle(e.x, e.y, 20f + 40f * e.finpow());
        Draw.color(Color.white);
        Draw.alpha(0.9f * fo * fo);
        Fill.circle(e.x, e.y, 10f + 14f * f);

        float ease = easeOut(f, 2.2f);
        for (int i = 0; i < 28; i++) {
            boolean forward = rnd(sdd, i + 1) < 0.65f;
            float ang = (forward ? e.rotation + (rnd(sdd, i + 50) - 0.5f) * 70f : rnd(sdd, i + 50) * 360f) * DEG;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            float dist = (25f + rnd(sdd, i + 100) * 150f) * ease;
            float tail = dist * (0.55f + rnd(sdd, i + 150) * 0.3f);
            Draw.color(Color.white, chargeColor, f);
            Draw.alpha(fo);
            Lines.stroke(0.8f + 1.6f * fo);
            Lines.line(e.x + c * tail, e.y + s * tail, e.x + c * dist, e.y + s * dist);
        }

        Draw.color(chargeColor);
        Draw.alpha(fo);
        Lines.stroke(1f + 3f * fo);
        wobbleRingSeed(e.x, e.y, 15f + 90f * e.finpow(), 5f * fo, sdd, e.time);
        Draw.reset();
    });

    // ---------- logic ----------
    static State createState(Unit unit) {
        State s = new State();
        int n = unit.mounts.length;
        s.reloads = new float[n];
        s.kinds = new int[n];
        for (int i = 0; i < n; i++) {
            WeaponMount mount = unit.mounts[i];
            String name = mount.weapon.name;
            s.reloads[i] = mount.reload;
            s.kinds[i] = name.endsWith("cos-weapon-a") ? 1 : (name.endsWith("cos-weapon-b") ? 2 : 0);
        }
        s.lastX = unit.x;
        s.lastY = unit.y;
        return s;
    }

    static void updateShield(Unit unit, State s) {
        if (!s.shieldActivated && unit.health / unit.maxHealth < SHIELD_THRESHOLD) {
            s.shieldActivated = true;
            unit.shield += SHIELD_MAX_HEALTH;
            Fx.shieldApply.at(unit.x, unit.y, unit.hitSize);
            Fx.shockwave.at(unit.x, unit.y);
        }
    }

    static void updateNearbyCosBuff(Unit unit, State s) {
        final int[] count = {0}; // bài 14: lambda không sửa biến cục bộ
        Units.nearby(unit.team, unit.x - BUFF_RADIUS, unit.y - BUFF_RADIUS, BUFF_RADIUS * 2f, BUFF_RADIUS * 2f, u -> {
            if (!u.dead && u != unit && u.type == unit.type && Mathf.dst(unit.x, unit.y, u.x, u.y) <= BUFF_RADIUS) count[0]++;
        });
        s.hasNearbyCosBuff = count[0] > 0;
    }

    static void triggerLightningPulse(Unit unit) {
        final float x = unit.x, y = unit.y;

        Units.nearby(unit.team, x - LIGHTNING_RADIUS, y - LIGHTNING_RADIUS, LIGHTNING_RADIUS * 2f, LIGHTNING_RADIUS * 2f, u -> {
            if (!u.dead && Mathf.dst(x, y, u.x, u.y) <= LIGHTNING_RADIUS) {
                u.heal(u.maxHealth * LIGHTNING_HEAL_PERCENT);
                Fx.heal.at(u.x, u.y);
                Lightning.create(unit.team, lightningColor, 10f, x, y, Angles.angle(x, y, u.x, u.y), (int) (Mathf.dst(x, y, u.x, u.y) / Vars.tilesize));
            }
        });

        Vars.indexer.eachBlock(unit.team, x, y, LIGHTNING_RADIUS, b -> true, b -> {
            b.heal(b.maxHealth * LIGHTNING_HEAL_PERCENT);
            // JS gốc nhân b.x * tilesize (b.x đã là toạ độ thế giới) nên hiệu ứng lệch xa; giữ nguyên (parity), xem bài 95.
            Fx.healBlock.at(b.x * Vars.tilesize, b.y * Vars.tilesize);
        });

        Units.nearbyEnemies(unit.team, x - LIGHTNING_RADIUS, y - LIGHTNING_RADIUS, LIGHTNING_RADIUS * 2f, LIGHTNING_RADIUS * 2f, u -> {
            if (!u.dead && Mathf.dst(x, y, u.x, u.y) <= LIGHTNING_RADIUS) {
                u.damage(u.maxHealth * LIGHTNING_ENEMY_MAX_HP_DAMAGE);
                Fx.hitLancer.at(u.x, u.y, lightningColor);
                Lightning.create(unit.team, lightningColor, 20f, x, y, Angles.angle(x, y, u.x, u.y), (int) (Mathf.dst(x, y, u.x, u.y) / Vars.tilesize));
            }
        });
    }

    static void handleMovementLightning(Unit unit, State s) {
        boolean isMoving = Mathf.dst(unit.x, unit.y, s.lastX, s.lastY) > 0.1f;
        s.lastX = unit.x;
        s.lastY = unit.y;
        if (isMoving) {
            s.lightningTimer += Time.delta;
            if (s.lightningTimer >= LIGHTNING_INTERVAL) {
                s.lightningTimer = 0;
                triggerLightningPulse(unit);
            }
        } else {
            s.lightningTimer = 0;
        }
    }

    static Teamc closest(Unit unit) {
        return Units.closestTarget(unit.team, unit.x, unit.y, PULSAL_LASER_RANGE, u -> !u.dead, b -> true);
    }

    static void firePulsalLaser(Unit unit, WeaponMount mount) {
        Teamc target = closest(unit);
        if (!(target instanceof Posc) || !(target instanceof Healthc)) return; // bài 5/26
        float tx = ((Posc) target).getX(), ty = ((Posc) target).getY();

        float totalDmg = PULSAL_LASER_DAMAGE;
        if (target instanceof Unit && !((Unit) target).isFlying()) totalDmg *= FOOT_UNIT_MULTIPLIER;

        pulsalLaserFx.at(unit.x, unit.y, 0f, laserColor, new Vec2(tx, ty));
        Fx.hitLancer.at(tx, ty, laserColor);
        ((Healthc) target).damage(totalDmg);
    }

    static boolean beamWillHit(Unit unit, WeaponMount mount) {
        Weapon w = mount.weapon;
        float x1 = unit.x + Angles.trnsx(unit.rotation - 90f, w.x, w.y);
        float y1 = unit.y + Angles.trnsy(unit.rotation - 90f, w.x, w.y);
        float ang = w.rotate ? Angles.angle(x1, y1, mount.aimX, mount.aimY) : unit.rotation + w.baseRotation;
        final float x2 = x1 + Angles.trnsx(ang, bBeamLength);
        final float y2 = y1 + Angles.trnsy(ang, bBeamLength);
        final float fx1 = x1, fy1 = y1;
        final float hw = bBeamWidth / 2f;
        final boolean[] hit = {false};

        float minX = Math.min(x1, x2) - hw;
        float minY = Math.min(y1, y2) - hw;
        Units.nearbyEnemies(unit.team, minX, minY, Math.abs(x2 - x1) + hw * 2f, Math.abs(y2 - y1) + hw * 2f, u -> {
            if (!hit[0] && !u.dead && Intersector.distanceSegmentPoint(fx1, fy1, x2, y2, u.x, u.y) <= u.hitSize / 2f + hw) hit[0] = true;
        });
        if (hit[0]) return true;

        Vars.world.raycast(Math.round(x1 / Vars.tilesize), Math.round(y1 / Vars.tilesize), Math.round(x2 / Vars.tilesize), Math.round(y2 / Vars.tilesize), (tx, ty) -> {
            Tile t = Vars.world.tile(tx, ty);
            if (t != null && t.build != null && t.build.team != unit.team && t.build.team != Team.derelict) {
                hit[0] = true;
                return true;
            }
            return false;
        });
        return hit[0];
    }

    static float aimAngle(Unit unit) {
        Teamc t = closest(unit);
        if (!(t instanceof Posc)) return unit.rotation;
        return Angles.angle(unit.x, unit.y, ((Posc) t).getX(), ((Posc) t).getY());
    }

    static void startCharge(Unit unit, State s) {
        s.charging = true;
        s.chargeTimer = BEAM_CHARGE_TIME;
        s.aimTimer = 0;
        s.hitsB = 0;
        s.starDone = false;
        s.chargeAngle = aimAngle(unit);
        if (!Vars.headless) {
            initChargeCache(s);
            chargeFx.at(unit.x, unit.y, 0f, chargeColor, unit);
        }
    }

    static void fireBeam(Unit unit, State s) {
        float a = aimAngle(unit);
        float cs = Mathf.cosDeg(a), sn = Mathf.sinDeg(a);
        final float ox = unit.x + Angles.trnsx(a, BEAM_FRONT_OFFSET);
        final float oy = unit.y + Angles.trnsy(a, BEAM_FRONT_OFFSET);
        int half = Math.min(BEAM_MAX_HALF_WIDTH, (int) Math.ceil(megaLaser.width / Vars.tilesize / 2f));

        megaLaser.create(unit, unit.team, ox, oy, a);
        Effect.shake(12f, 40f, ox, oy);
        Fx.shockwave.at(ox, oy);

        if (!Vars.headless) {
            muzzleBurstFx.at(ox, oy, a, chargeColor);

            final float fa = a;
            for (float d = 0, n = 0; d <= megaLaser.length && n < BEAM_RING_MAX; d += BEAM_RING_SPACING, n++) {
                final float rx = ox + cs * d, ry = oy + sn * d;
                float delay = d / BEAM_RING_WAVE_SPEED;
                if (delay <= 0) warpRingFx.at(rx, ry, fa, chargeColor);
                else Time.run(delay, () -> warpRingFx.at(rx, ry, fa, chargeColor));
            }

            BeamData fd = buildBeamData(half * Vars.tilesize);
            edgeFx.at(ox, oy, a, chargeColor, fd);
            rubbleFx.at(ox, oy, a, chargeColor, fd);
        }

        Beam bm = new Beam();
        bm.x = ox; bm.y = oy; bm.cos = cs; bm.sin = sn;
        bm.row = 0;
        bm.rows = (int) Math.ceil(megaLaser.length / Vars.tilesize);
        bm.half = half;
        bm.team = unit.team;
        beams.add(bm);
    }

    static void updateCharge(Unit unit, State s) {
        if (!s.charging) return;
        unit.vel.setZero();

        s.aimTimer += Time.delta;
        if (s.aimTimer >= BEAM_AIM_INTERVAL) {
            s.aimTimer = 0;
            s.chargeAngle = aimAngle(unit);
        }

        s.chargeTimer -= Time.delta;

        if (!s.starDone && s.chargeTimer <= BEAM_STAR_TIME) {
            s.starDone = true;
            if (!Vars.headless) {
                starFx.at(unit.x + Angles.trnsx(s.chargeAngle, BEAM_FRONT_OFFSET),
                    unit.y + Angles.trnsy(s.chargeAngle, BEAM_FRONT_OFFSET), s.chargeAngle, chargeColor);
            }
        }

        if (s.chargeTimer <= 0) {
            s.charging = false;
            fireBeam(unit, s);
        }
    }

    static void breakTile(Tile t, Team team) {
        Building b = t.build;
        if (b != null) {
            if (BEAM_DESTROY_ALLY_BLOCKS || b.team != team) b.kill();
        } else if (t.block() != Blocks.air) {
            t.setNet(Blocks.air);
        }

        mindustry.world.Block f = t.floor();
        if (f != Blocks.hotrock && f != Blocks.magmarock) {
            t.setFloorNet(Mathf.chance(0.5) ? Blocks.hotrock : Blocks.magmarock);
        }
    }

    static void sweepRow(Beam bm, int r) {
        float d = r * Vars.tilesize;
        float cx = bm.x + bm.cos * d;
        float cy = bm.y + bm.sin * d;
        for (int k = -bm.half; k <= bm.half; k++) {
            Tile t = Vars.world.tileWorld(cx - bm.sin * k * Vars.tilesize, cy + bm.cos * k * Vars.tilesize);
            if (t != null) breakTile(t, bm.team);
        }
    }

    static void trackShots(Unit unit, State s) {
        WeaponMount[] mounts = unit.mounts;
        for (int i = 0; i < mounts.length && i < s.kinds.length; i++) {
            int kind = s.kinds[i];
            if (kind == 0) {
                s.reloads[i] = mounts[i].reload;
                continue;
            }
            WeaponMount mount = mounts[i];
            Weapon weapon = mount.weapon;

            if (kind == 1) {
                if (s.pulsalTimer > 0) {
                    mount.reload = 0;
                    s.pulsalLaserTimer[i] += Time.delta * unit.reloadMultiplier;
                    if (s.pulsalLaserTimer[i] >= weapon.reload) {
                        s.pulsalLaserTimer[i] = 0;
                        firePulsalLaser(unit, mount);
                    }
                } else {
                    float bonusSpeed = 1f + Math.min(s.shotsWeaponA[i] * 0.015f, 2f);
                    mount.reload += Time.delta * (bonusSpeed - 1f);

                    boolean fired = mount.reload > s.reloads[i] + 0.5f;
                    if (fired) {
                        s.shotsWeaponA[i]++;
                        if (s.shotsWeaponA[0] >= PULSAL_SHOTS_NEEDED && s.shotsWeaponA[1] >= PULSAL_SHOTS_NEEDED) {
                            s.pulsalTimer = PULSAL_DURATION;
                            s.shotsWeaponA[0] = 0;
                            s.shotsWeaponA[1] = 0;
                            activateFx.at(unit.x, unit.y);
                        }
                    }
                }
            } else if (!s.charging) {
                if (mount.reload > s.reloads[i] + 0.5f && beamWillHit(unit, mount)) {
                    s.hitsB++;
                    if (s.hitsB >= BEAM_HITS_NEEDED) startCharge(unit, s);
                }
            }
            s.reloads[i] = mount.reload;
        }
    }

    static void spawnCorvus(Unit unit) {
        UnitType type = UnitTypes.corvus;
        if (type == null) return;
        int spawnCount = 3;
        float spawnSpread = 20f;
        float base = rand() * 360f;
        for (int i = 0; i < spawnCount; i++) {
            float ang = base + i * (360f / spawnCount);
            float x = unit.x + Angles.trnsx(ang, spawnSpread);
            float y = unit.y + Angles.trnsy(ang, spawnSpread);
            Unit c = type.spawn(unit.team, x, y);
            if (c != null) c.rotation = unit.rotation;
        }
        Fx.shockwave.at(unit.x, unit.y);
    }

    static final Ability cosAbility = new Ability() {
        @Override
        public void update(Unit unit) {
            if (unit == null || unit.dead || Vars.net.client()) return;
            State s = states.get(unit.id);
            if (s == null) {
                s = createState(unit);
                states.put(unit.id, s);
                unit.apply(cosStatus, 1f);
            }

            if (s.pulsalTimer > 0) s.pulsalTimer -= Time.delta;

            s.buffTimer += Time.delta;
            if (s.buffTimer >= BUFF_CHECK_INTERVAL) {
                s.buffTimer = 0;
                updateNearbyCosBuff(unit, s);
            }

            updateShield(unit, s);
            handleMovementLightning(unit, s);
            trackShots(unit, s);
            updateCharge(unit, s);
        }

        @Override
        public void death(Unit unit) {
            if (unit == null) return;
            states.remove(unit.id);
            if (Vars.net.client()) return;
            spawnCorvus(unit);
        }

        @Override
        public String localized() {
            return "Cos Ability";
        }
    };

    static boolean isCos(String name) {
        if (name == null || name.isEmpty()) return false;
        String n = name.toLowerCase();
        return n.equals("cos") || n.endsWith("-cos");
    }

    static void patch() {
        if (patched) return;
        final UnitType[] found = {null};
        Vars.content.units().each(u -> {
            if (found[0] == null && isCos(u.name)) found[0] = u;
        });
        if (found[0] == null) return;
        patched = true;
        UnitType cos = found[0];
        cos.abilities.add(cosAbility);
        cos.range *= 1.5f;

        cos.weapons.each(w -> {
            if (w.name.endsWith("cos-weapon-b") && w.bullet instanceof LaserBulletType) {
                bBeamLength = ((LaserBulletType) w.bullet).length;
                bBeamWidth = ((LaserBulletType) w.bullet).width;
            }
        });
        megaLaser.length = bBeamLength * BEAM_SCALE;
        megaLaser.width = bBeamWidth * BEAM_SCALE;
        megaLaser.drawSize = megaLaser.length * 2f;
    }

    /** Tạo StatusEffect "cos-core" — gọi trong NexMod.loadContent (JS: extend(StatusEffect, "cos-core")). */
    public static void create() {
        cosStatus = new StatusEffect("cos-core") {
            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                State s = states.get(unit.id);
                if (s == null) return;

                float dmgMult = 1f;
                if (Mathf.chance(CRIT_CHANCE)) dmgMult *= CRIT_MULTIPLIER;
                if (s.hasNearbyCosBuff) dmgMult *= BUFF_MULTIPLIER;

                unit.damageMultiplier = dmgMult;
                unit.reloadMultiplier = s.hasNearbyCosBuff ? BUFF_MULTIPLIER : 1f;

                if (s.charging) unit.speedMultiplier = 0f;
            }
        };
        cosStatus.permanent = true;
        cosStatus.show = false;
    }

    public static void init() {
        Events.run(EventType.Trigger.update, () -> {
            if (beams.isEmpty() || Vars.state.isPaused() || Vars.net.client()) return;
            for (int i = beams.size() - 1; i >= 0; i--) {
                Beam bm = beams.get(i);
                int end = Math.min(bm.row + BEAM_ROWS_PER_TICK, bm.rows);
                for (int r = bm.row; r < end; r++) sweepRow(bm, r);
                bm.row = end;
                if (end >= bm.rows) beams.remove(i);
            }
        });
        Events.on(EventType.ContentInitEvent.class, e -> patch());
        Events.on(EventType.ClientLoadEvent.class, e -> patch());
        Events.on(EventType.ServerLoadEvent.class, e -> patch());
        Events.on(EventType.WorldLoadEvent.class, e -> {
            states.clear();
            beams.clear();
        });
    }
}
