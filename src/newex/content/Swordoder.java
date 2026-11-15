package newex.content;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.UnitSorts;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Chuyển từ swordoder.js. Pháo kiếm (block do Java tạo, hjson KHÔNG có `type:` – bài 29a):
 * đạn kiếm + chém vòng cung 90°, tận diệt mục tiêu <= 15% máu, MK2 (bắn 2 bên, bạo kích), MK2B (tầm xa, chém vào mục tiêu).
 */
public final class Swordoder {
    private Swordoder() {}

    static final class Req {
        final int copper, lead, silicon, titanium, thorium, graphite;
        Req(int copper, int lead, int silicon, int titanium, int thorium, int graphite) {
            this.copper = copper; this.lead = lead; this.silicon = silicon;
            this.titanium = titanium; this.thorium = thorium; this.graphite = graphite;
        }
    }

    static final Req reqMK2 = new Req(2000, 2000, 2000, 0, 0, 0);
    static final Req reqMK2B = new Req(0, 0, 0, 1000, 1000, 1000);
    static final float SLASH_DAMAGE = 1200f, BULLET_DAMAGE = 500f, BASE_RANGE = 210f;
    static final float MK2_CRIT_CHANCE = 0.5f, MK2_CRIT_MULT = 2.45f;

    static ItemTurret swordoder;
    static SwordBullet swordoderBulletBase;

    static boolean isEn() {
        return String.valueOf(Core.settings.get("locale", "vi")).startsWith("en");
    }

    static String t(String vi, String en) {
        return isEn() ? en : vi;
    }

    static boolean isLightOn() {
        return Core.settings.getBool("drawlight", true) && Core.settings.getBool("bloom", true);
    }

    // ---------- LOD hiệu ứng theo mật độ ----------
    static long fxFrame = -1;
    static int fxRiftNow = 0, fxRiftPrev = 0, fxSlashNow = 0, fxSlashPrev = 0;

    static void fxFrameTick() {
        long fid = Core.graphics.getFrameId();
        if (fid != fxFrame) {
            boolean sequential = fid - fxFrame == 1;
            fxRiftPrev = sequential ? fxRiftNow : 0;
            fxSlashPrev = sequential ? fxSlashNow : 0;
            fxRiftNow = 0;
            fxSlashNow = 0;
            fxFrame = fid;
        }
    }

    static int riftCrowdLod() {
        fxFrameTick();
        fxRiftNow++;
        int c = Math.max(fxRiftPrev, fxRiftNow);
        return c > 28 ? 3 : (c > 14 ? 2 : (c > 6 ? 1 : 0));
    }

    static int slashCrowdLod() {
        fxFrameTick();
        fxSlashNow++;
        int c = Math.max(fxSlashPrev, fxSlashNow);
        return c > 10 ? 2 : (c > 4 ? 1 : 0);
    }

    static TextureRegion swordRegion() {
        TextureRegion r = PerfCore.region("newex-swordoder-sword");
        if (!r.found()) r = PerfCore.region("swordoder-sword");
        return r;
    }

    static void drawSwordRegion(float x, float y, float rot, float scale) {
        TextureRegion r = swordRegion();
        if (r.found()) {
            Draw.rect(r, x, y, r.width * Draw.scl * scale, r.height * Draw.scl * scale, rot - 90f);
        }
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    static float pw(float a, float b) { return (float) Math.pow(a, b); }

    // ---------- sát thương ----------
    static void checkExecute(Unit unit, mindustry.game.Team team) {
        if (unit != null && unit.team != team && !unit.dead) {
            if (unit.health / unit.maxHealth <= 0.15f) unit.damagePierce(unit.maxHealth);
        }
    }

    static void elementHit(Object source, Unit unit, float dmg) {
        if (source instanceof Building && unit != null) ElementSystem.hit((Building) source, unit, dmg);
    }

    static void damageArc(mindustry.game.Team team, float x, float y, float rotation, float radius, float arcAngle, float damageAmount, Object source) {
        float originX = x - Angles.trnsx(rotation, radius);
        float originY = y - Angles.trnsy(rotation, radius);
        float startAngle = rotation - arcAngle / 2f;
        int steps = 18;
        float angleStep = arcAngle / steps;
        final float hitRadius = 50f;
        final HashSet<Integer> rolled = new HashSet<>();

        for (int i = 0; i <= steps; i++) {
            float currentAngle = startAngle + angleStep * i;
            final float px = originX + Angles.trnsx(currentAngle, radius);
            final float py = originY + Angles.trnsy(currentAngle, radius);

            Units.nearbyEnemies(team, px - hitRadius, py - hitRadius, hitRadius * 2f, hitRadius * 2f, unit -> {
                if (unit.within(px, py, hitRadius)) {
                    unit.damage(damageAmount);
                    checkExecute(unit, team);
                    if (source != null && rolled.add(unit.id)) elementHit(source, unit, damageAmount);
                }
            });

            Damage.damage(team, px, py, hitRadius, damageAmount, true, true);
        }
    }

    static void damageCut(mindustry.game.Team team, float x, float y, float angle, float length, float damageAmount, Object source, float radius) {
        float half = length / 2f;
        int steps = Math.max(1, (int) Math.ceil(length / (radius * 1.5f)));
        final HashSet<Integer> rolled = new HashSet<>();

        for (int i = 0; i <= steps; i++) {
            float d = -half + (length * i) / steps;
            final float px = x + Angles.trnsx(angle, d);
            final float py = y + Angles.trnsy(angle, d);

            Damage.damage(team, px, py, radius, damageAmount, true, true);

            Units.nearbyEnemies(team, px - radius, py - radius, radius * 2f, radius * 2f, unit -> {
                if (unit.within(px, py, radius)) {
                    checkExecute(unit, team);
                    if (source != null && rolled.add(unit.id)) elementHit(source, unit, damageAmount);
                }
            });
        }
    }

    // ---------- hiệu ứng chém ----------
    static final Effect vSlashHitFx = new Effect(24f, e -> {
        Draw.z(Layer.effect + 0.01f);

        float fin = e.fin();
        float rot = e.rotation;
        float turretX = e.x, turretY = e.y;

        float fxX = e.x + Angles.trnsx(rot, 15f);
        float fxY = e.y + Angles.trnsy(rot, 15f);

        float radius = 240f, arcAngle = 90f;
        float originX = fxX - Angles.trnsx(rot, radius);
        float originY = fxY - Angles.trnsy(rot, radius);

        float startAngle = rot - arcAngle / 2f;
        float endAngle = rot + arcAngle / 2f;

        float currentAngle = startAngle + (endAngle - startAngle) * fin;
        float swordX = originX + Angles.trnsx(currentAngle, radius - 33f);
        float swordY = originY + Angles.trnsy(currentAngle, radius - 33f);

        float swordRot = currentAngle;
        float scale = Math.max(0f, 1f - fin) * 3f;

        float alpha = 1f - fin;
        float headProgress = Math.min(1f, fin * 1.2f);
        float currentSweep = (startAngle + (endAngle - startAngle) * headProgress) - startAngle;

        if (currentSweep > 0) {
            Draw.color(PerfCore.color("#ffe066"), PerfCore.color("#ff5500"), fin);
            Draw.alpha(alpha);
            Lines.stroke(36f * (scale / 3f));
            Lines.arc(originX, originY, radius - 18f, currentSweep / 360f, startAngle);

            Draw.color(Color.white);
            Draw.alpha(alpha);
            Lines.stroke(15f * (scale / 3f));
            Lines.arc(originX, originY, radius - 24f, currentSweep / 360f, startAngle);
        }

        int slashLod = Math.max(PerfCore.level(), slashCrowdLod());
        int smokeCount = slashLod >= 2 ? 8 : (slashLod == 1 ? 12 : 20);
        for (int i = 0; i <= smokeCount; i++) {
            float spawnProgress = (float) i / smokeCount;

            if (fin >= spawnProgress) {
                float stepAngle = startAngle + (endAngle - startAngle) * spawnProgress;
                float px = originX + Angles.trnsx(stepAngle, radius);
                float py = originY + Angles.trnsy(stepAngle, radius);

                long smokeSeed = (long) e.id * 100 + i;
                float particleLife = (fin - spawnProgress) / (1f - spawnProgress + 0.0001f);

                float baseAngle = stepAngle + 25f;
                float smAng = baseAngle + Mathf.randomSeed(smokeSeed, -30f, 30f);

                float flySpeed = 20f + Mathf.randomSeed(smokeSeed + 1, 0f, 25f);
                float flyDist = flySpeed * particleLife;

                float smX = px + Angles.trnsx(smAng, flyDist);
                float smY = py + Angles.trnsy(smAng, flyDist);

                float smSize = 2.5f + particleLife * 7f + Mathf.randomSeed(smokeSeed + 2, 0f, 3f);
                float smAlpha = alpha * (1f - particleLife) * 0.5f;

                Draw.color(Color.gray, Color.darkGray, particleLife);
                Draw.alpha(Math.max(0f, smAlpha));
                Fill.circle(smX, smY, smSize);

                if (Mathf.randomSeed(smokeSeed + 3, 0f, 1f) > 0.4f) {
                    Draw.color(PerfCore.color("#aee5ff"), Color.white, Mathf.randomSeed(smokeSeed + 4, 0f, 1f));
                    Lines.stroke((1.5f + Mathf.randomSeed(smokeSeed + 5, 0f, 1.5f)) * (1f - particleLife));

                    long sparkSeed = (long) e.id * 1000 + i * 10L + (long) Math.floor(fin * 15);
                    float p1X = smX + Mathf.randomSeed(sparkSeed, -5f, 5f);
                    float p1Y = smY + Mathf.randomSeed(sparkSeed + 1, -5f, 5f);
                    float p2X = p1X + Mathf.randomSeed(sparkSeed + 2, -8f, 8f);
                    float p2Y = p1Y + Mathf.randomSeed(sparkSeed + 3, -8f, 8f);
                    Lines.line(p1X, p1Y, p2X, p2Y);
                }
            }
        }

        Draw.color(PerfCore.color("#aee5ff"), Color.white, Mathf.randomSeed(e.id + 99L, 0f, 1f));
        Lines.stroke(2.5f * alpha);

        for (int j = 0; j < 3; j++) {
            long beamSeed = (long) e.id * 50 + j + (long) Math.floor(fin * 20);
            float targetAngle = startAngle + (endAngle - startAngle) * Mathf.randomSeed(beamSeed, 0f, fin);
            float targetX = originX + Angles.trnsx(targetAngle, radius);
            float targetY = originY + Angles.trnsy(targetAngle, radius);

            float midX = (turretX + targetX) / 2f + Mathf.randomSeed(beamSeed + 1, -20f, 20f);
            float midY = (turretY + targetY) / 2f + Mathf.randomSeed(beamSeed + 2, -20f, 20f);

            Lines.line(turretX, turretY, midX, midY);
            Lines.line(midX, midY, targetX, targetY);
        }

        drawSwordRegion(swordX, swordY, swordRot, scale);
        Draw.reset();
    });

    // ---------- khe nứt không gian ----------
    static final int RIFT_MAX_POINTS = 26, RIFT_MAX_ALIVE = 40;
    static final float[] riftHl = new float[RIFT_MAX_POINTS + 1];
    static final float[] riftHr = new float[RIFT_MAX_POINTS + 1];
    static final ArrayList<Float> riftSpawnTimes = new ArrayList<>();
    static final ArrayList<Float> riftAliveUntil = new ArrayList<>();

    static Color cHalo, cFill, cGlowA, cGlowB, cEdge, cCoreA, cCoreB, cBrA, cBrB, cDbA, cDbB;
    static Color[] cStars;

    static void riftColors() {
        if (cHalo != null) return;
        cHalo = PerfCore.color("#000008");
        cFill = PerfCore.color("#04010f");
        cGlowA = PerfCore.color("#5b21b6");
        cGlowB = PerfCore.color("#1d4ed8");
        cStars = new Color[]{PerfCore.color("#ffffff"), PerfCore.color("#aee5ff"), PerfCore.color("#ffd1f5"), PerfCore.color("#fff4b0")};
        cEdge = PerfCore.color("#a855f7");
        cCoreA = PerfCore.color("#ffffff");
        cCoreB = PerfCore.color("#8fd8ff");
        cBrA = PerfCore.color("#d8b4fe");
        cBrB = PerfCore.color("#ffffff");
        cDbA = PerfCore.color("#e0f2ff");
        cDbB = PerfCore.color("#a855f7");
    }

    static final class Stars { int[] i = new int[34]; float[] f = new float[34], cx = new float[34], cy = new float[34], nx = new float[34], ny = new float[34], v = new float[34], ph = new float[34]; }
    static final class Branch { int bi, side; float[] cx = new float[3], cy = new float[3], len = new float[3]; }
    static final class Debris { int di, side; float cx, cy, reach; }
    static final class Geom {
        int n;
        float[] u, mid, px, py, nx, ny, wl, wr;
        Stars st;
        Branch[] br;
        Debris[] db;
    }

    /** Dữ liệu truyền vào hiệu ứng (JS: object literal {lod, len, hw, geom}); null = undefined. */
    static final class RiftData {
        int lod;
        Float len, hw;
        Geom geom;
    }

    static int riftDetail() {
        float now = Time.time;
        if (!riftSpawnTimes.isEmpty() && now < riftSpawnTimes.get(0)) riftSpawnTimes.clear();
        while (!riftSpawnTimes.isEmpty() && now - riftSpawnTimes.get(0) > 70f) riftSpawnTimes.remove(0);
        riftSpawnTimes.add(now);

        int n = riftSpawnTimes.size();
        int lod = PerfCore.level();
        if (n > 10) lod = Math.max(lod, 2);
        else if (n > 4) lod = Math.max(lod, 1);
        return lod;
    }

    static float riftBody(float fin) {
        float opening = fin < 0.1f ? 1f - pw(1f - fin / 0.1f, 3f) : 1f;
        float closeK = fin < 0.4f ? 0f : (fin - 0.4f) / 0.6f;
        return opening * pw(1f - closeK, 1.5f);
    }

    static Geom buildRiftGeom(Effect.EffectContainer e, boolean isArc, float length, float halfWidth, int n) {
        long seed = (long) e.id * 977;
        float rot = e.rotation;

        float arcRadius = 240f, arcAngle = 90f;
        float originX = e.x - Angles.trnsx(rot, arcRadius);
        float originY = e.y - Angles.trnsy(rot, arcRadius);
        float startAngle = rot - arcAngle / 2f;

        Geom g = new Geom();
        g.n = n;
        g.u = new float[n + 1]; g.mid = new float[n + 1]; g.px = new float[n + 1]; g.py = new float[n + 1];
        g.nx = new float[n + 1]; g.ny = new float[n + 1]; g.wl = new float[n + 1]; g.wr = new float[n + 1];

        for (int i = 0; i <= n; i++) {
            float u = (float) i / n;
            float mid = (float) Math.sin(Math.PI * u);
            float bx, by, tx, ty;

            if (isArc) {
                float ang = startAngle + arcAngle * u;
                bx = originX + Angles.trnsx(ang, arcRadius);
                by = originY + Angles.trnsy(ang, arcRadius);
                tx = Angles.trnsx(ang + 90f, 1f);
                ty = Angles.trnsy(ang + 90f, 1f);
            } else {
                float dd = (u - 0.5f) * length;
                bx = e.x + Angles.trnsx(rot, dd);
                by = e.y + Angles.trnsy(rot, dd);
                tx = Angles.trnsx(rot, 1f);
                ty = Angles.trnsy(rot, 1f);
            }

            float nx = -ty, ny = tx;
            float jag = (Mathf.randomSeed(seed + i * 31L, -1f, 1f) + Mathf.randomSeed(seed + i * 53L + 7, -0.5f, 0.5f)) * 4.5f * pw(mid, 0.5f);
            float wBase = halfWidth * pw(mid, 0.65f);

            g.u[i] = u;
            g.mid[i] = mid;
            g.px[i] = bx + nx * jag;
            g.py[i] = by + ny * jag;
            g.nx[i] = nx;
            g.ny[i] = ny;
            g.wl[i] = wBase * (1f + 0.4f * Mathf.randomSeed(seed + i * 17L + 3, -1f, 1f));
            g.wr[i] = wBase * (1f + 0.4f * Mathf.randomSeed(seed + i * 19L + 5, -1f, 1f));
        }
        return g;
    }

    static Stars buildRiftStars(Geom g, long seed) {
        int n = g.n;
        Stars st = new Stars();
        for (int s = 0; s < 34; s++) {
            float u = Mathf.randomSeed(seed + s * 13L + 1, 0.05f, 0.95f);
            float idx = u * n;
            int i = Math.min(n - 1, (int) Math.floor(idx));
            float f = idx - i;

            st.i[s] = i;
            st.f[s] = f;
            st.cx[s] = g.px[i] + (g.px[i + 1] - g.px[i]) * f;
            st.cy[s] = g.py[i] + (g.py[i + 1] - g.py[i]) * f;
            st.nx[s] = g.nx[i];
            st.ny[s] = g.ny[i];
            st.v[s] = Mathf.randomSeed(seed + s * 13L + 2, -0.9f, 0.9f);
            st.ph[s] = Mathf.randomSeed(seed + s * 13L + 3, 0f, 6.28f);
        }
        return st;
    }

    static Branch[] buildRiftBranches(Geom g, long seed) {
        int n = g.n;
        Branch[] list = new Branch[7];
        for (int k = 0; k < 7; k++) {
            int bi = 3 + (int) Math.floor(Mathf.randomSeed(seed + k * 41L + 9, 0f, n - 6.01f));
            int side = Mathf.randomSeed(seed + k * 41L + 10, -1f, 1f) >= 0 ? 1 : -1;
            float dir = Mathf.angle(g.nx[bi] * side, g.ny[bi] * side) + Mathf.randomSeed(seed + k * 41L + 11, -40f, 40f);
            Branch b = new Branch();
            b.bi = bi;
            b.side = side;
            for (int sg = 0; sg < 3; sg++) {
                b.cx[sg] = Angles.trnsx(dir, 1f);
                b.cy[sg] = Angles.trnsy(dir, 1f);
                b.len[sg] = Mathf.randomSeed(seed + k * 41L + sg * 3L + 12, 9f, 22f);
                dir += Mathf.randomSeed(seed + k * 41L + sg * 3L + 13, -32f, 32f);
            }
            list[k] = b;
        }
        return list;
    }

    static Debris[] buildRiftDebris(Geom g, long seed) {
        int n = g.n;
        Debris[] list = new Debris[12];
        for (int d = 0; d < 12; d++) {
            int di = 2 + (int) Math.floor(Mathf.randomSeed(seed + d * 23L + 21, 0f, n - 4.01f));
            int side = Mathf.randomSeed(seed + d * 23L + 22, -1f, 1f) >= 0 ? 1 : -1;
            float dirA = Mathf.angle(g.nx[di] * side, g.ny[di] * side) + Mathf.randomSeed(seed + d * 23L + 23, -55f, 55f);
            Debris b = new Debris();
            b.di = di;
            b.side = side;
            b.cx = Angles.trnsx(dirA, 1f);
            b.cy = Angles.trnsy(dirA, 1f);
            b.reach = Mathf.randomSeed(seed + d * 23L + 24, 14f, 46f);
            list[d] = b;
        }
        return list;
    }

    static void quad(float a, float b, float c, float d, float e, float f, float g, float h) {
        Fill.quad(a, b, c, d, e, f, g, h);
    }

    static void drawSpaceRift(Effect.EffectContainer e, boolean isArc, float length, float halfWidth, int lod) {
        float fin = e.fin();
        float body = riftBody(fin);
        if (body <= 0.01f) return;

        int eff = Math.max(lod, riftCrowdLod());
        long seed = (long) e.id * 977;
        RiftData d = e.data instanceof RiftData ? (RiftData) e.data : null;
        Geom g = d != null ? d.geom : null;
        if (g == null) {
            g = buildRiftGeom(e, isArc, length, halfWidth, lod >= 2 ? 16 : RIFT_MAX_POINTS);
            if (d != null) d.geom = g;
        }

        int n = g.n;
        riftColors();
        float[] px = g.px, py = g.py, nx = g.nx, ny = g.ny;
        float grow = clamp01(fin / 0.1f);
        float closeK = fin < 0.4f ? 0f : (fin - 0.4f) / 0.6f;

        for (int i = 0; i <= n; i++) {
            float rv = (grow - g.u[i]) * 8f + 0.0001f;
            float reveal = rv < 0 ? 0f : (rv > 1 ? 1f : rv);
            float shape = body * reveal * (1f - closeK * (1f - g.mid[i]) * 0.85f);
            riftHl[i] = g.wl[i] * shape;
            riftHr[i] = g.wr[i] * shape;
        }

        if (eff < 3) {
            Draw.z(Layer.effect + 0.02f);
            Draw.color(cHalo);
            Draw.alpha(0.5f * body);
            float pad = 1.5f * body;
            for (int i = 0; i < n; i++) {
                if (riftHl[i] <= 0.05f && riftHl[i + 1] <= 0.05f) continue;
                float w0l = riftHl[i] * 1.55f + pad, w0r = riftHr[i] * 1.55f + pad;
                float w1l = riftHl[i + 1] * 1.55f + pad, w1r = riftHr[i + 1] * 1.55f + pad;
                quad(px[i] + nx[i] * w0l, py[i] + ny[i] * w0l,
                     px[i + 1] + nx[i + 1] * w1l, py[i + 1] + ny[i + 1] * w1l,
                     px[i + 1] - nx[i + 1] * w1r, py[i + 1] - ny[i + 1] * w1r,
                     px[i] - nx[i] * w0r, py[i] - ny[i] * w0r);
            }
        }

        Draw.z(Layer.effect + 0.022f);
        Draw.color(cFill);
        Draw.alpha(0.97f);
        for (int i = 0; i < n; i++) {
            if (riftHl[i] <= 0.05f && riftHl[i + 1] <= 0.05f) continue;
            quad(px[i] + nx[i] * riftHl[i], py[i] + ny[i] * riftHl[i],
                 px[i + 1] + nx[i + 1] * riftHl[i + 1], py[i + 1] + ny[i + 1] * riftHl[i + 1],
                 px[i + 1] - nx[i + 1] * riftHr[i + 1], py[i + 1] - ny[i + 1] * riftHr[i + 1],
                 px[i] - nx[i] * riftHr[i], py[i] - ny[i] * riftHr[i]);
        }

        if (eff < 3) {
            Draw.z(Layer.effect + 0.0225f);
            for (int i = 2; i < n - 1; i += 4) {
                float hw = Math.max(riftHl[i], riftHr[i]);
                if (hw <= 1f) continue;
                Draw.color(i % 8 == 2 ? cGlowA : cGlowB);
                Draw.alpha(0.3f * body);
                Fill.circle(px[i], py[i], hw * 0.9f);
            }
        }

        Draw.blend(Blending.additive);
        Draw.z(Layer.effect + 0.021f);

        if (eff < 3) {
            Draw.color(cEdge);
            Draw.alpha(0.28f * body);
            float wp = (4f * body + 0.6f) * 0.85f;
            for (int i = 0; i < n; i++) {
                float l0 = riftHl[i], l1 = riftHl[i + 1];
                if (l0 > 0.15f || l1 > 0.15f) {
                    float a0 = wp * (l0 > 2f ? 1f : l0 * 0.5f);
                    float a1 = wp * (l1 > 2f ? 1f : l1 * 0.5f);
                    quad(px[i] + nx[i] * l0, py[i] + ny[i] * l0,
                         px[i + 1] + nx[i + 1] * l1, py[i + 1] + ny[i + 1] * l1,
                         px[i + 1] + nx[i + 1] * (l1 + a1), py[i + 1] + ny[i + 1] * (l1 + a1),
                         px[i] + nx[i] * (l0 + a0), py[i] + ny[i] * (l0 + a0));
                }
                float r0 = riftHr[i], r1 = riftHr[i + 1];
                if (r0 > 0.15f || r1 > 0.15f) {
                    float b0 = wp * (r0 > 2f ? 1f : r0 * 0.5f);
                    float b1 = wp * (r1 > 2f ? 1f : r1 * 0.5f);
                    quad(px[i] - nx[i] * r0, py[i] - ny[i] * r0,
                         px[i + 1] - nx[i + 1] * r1, py[i + 1] - ny[i + 1] * r1,
                         px[i + 1] - nx[i + 1] * (r1 + b1), py[i + 1] - ny[i + 1] * (r1 + b1),
                         px[i] - nx[i] * (r0 + b0), py[i] - ny[i] * (r0 + b0));
                }
            }
        }

        Draw.color(cCoreA, cCoreB, fin);
        Draw.alpha(0.95f * Math.min(1f, body * 1.6f));
        float coreStroke = 1.3f * body + 0.35f;
        Lines.stroke(coreStroke);
        float off = coreStroke * 0.5f;
        for (int i = 0; i < n; i++) {
            float l0 = riftHl[i], l1 = riftHl[i + 1];
            if (l0 > 0.15f || l1 > 0.15f) {
                float o0 = l0 + (l0 > 2f ? off : off * l0 * 0.5f);
                float o1 = l1 + (l1 > 2f ? off : off * l1 * 0.5f);
                Lines.line(px[i] + nx[i] * o0, py[i] + ny[i] * o0, px[i + 1] + nx[i + 1] * o1, py[i + 1] + ny[i + 1] * o1);
            }
            float r0 = riftHr[i], r1 = riftHr[i + 1];
            if (r0 > 0.15f || r1 > 0.15f) {
                float q0 = r0 + (r0 > 2f ? off : off * r0 * 0.5f);
                float q1 = r1 + (r1 > 2f ? off : off * r1 * 0.5f);
                Lines.line(px[i] - nx[i] * q0, py[i] - ny[i] * q0, px[i + 1] - nx[i + 1] * q1, py[i + 1] - ny[i + 1] * q1);
            }
        }

        int branchCount = eff >= 2 ? 0 : (eff == 1 ? 3 : 7);
        float branchGrow = clamp01((fin - 0.04f) / 0.16f);
        if (branchCount > 0 && branchGrow > 0) {
            if (g.br == null) g.br = buildRiftBranches(g, seed);
            float brAlpha = 0.85f * pw(body, 0.8f);

            for (int k = 0; k < branchCount; k++) {
                Branch b = g.br[k];
                int bi = b.bi, side = b.side;
                float hw = side > 0 ? riftHl[bi] : riftHr[bi];
                if (hw < 0.8f) continue;

                float bx = px[bi] + nx[bi] * hw * side;
                float by = py[bi] + ny[bi] * hw * side;
                int segs = 3;

                for (int sg = 0; sg < segs; sg++) {
                    if (branchGrow < ((float) sg / segs)) break;
                    float segLen = b.len[sg] * clamp01(branchGrow * segs - sg + 0.2f);
                    float ex = bx + b.cx[sg] * segLen;
                    float ey = by + b.cy[sg] * segLen;
                    Draw.color(cBrA, cBrB, 1f - (float) sg / segs);
                    Draw.alpha(brAlpha);
                    Lines.stroke(1.5f - sg * 0.45f);
                    Lines.line(bx, by, ex, ey);
                    bx = ex;
                    by = ey;
                }
            }
        }

        if (eff == 0 && fin < 0.55f) {
            if (g.db == null) g.db = buildRiftDebris(g, seed);
            float t = fin / 0.55f;
            float reach = (float) Math.sqrt(t);
            float sparkLen = 3f + 4f * (1f - t);
            Draw.color(cDbA, cDbB, t);
            Draw.alpha(1f - t);
            Lines.stroke(1.2f * (1f - t) + 0.2f);

            for (int k = 0; k < 12; k++) {
                Debris b = g.db[k];
                float hw = b.side > 0 ? riftHl[b.di] : riftHr[b.di];
                if (hw < 0.8f) continue;
                float dist = reach * b.reach;
                float dx = px[b.di] + nx[b.di] * hw * b.side + b.cx * dist;
                float dy = py[b.di] + ny[b.di] * hw * b.side + b.cy * dist;
                Lines.line(dx, dy, dx + b.cx * sparkLen, dy + b.cy * sparkLen);
            }
        }

        Draw.z(Layer.effect + 0.023f);

        int starCount = eff >= 3 ? 0 : (eff == 2 ? 8 : (eff == 1 ? 18 : 34));
        if (starCount > 0) {
            if (g.st == null) g.st = buildRiftStars(g, seed);
            Stars st = g.st;
            float starBody = Math.min(1f, body * 1.4f);

            for (int kind = 0; kind < 4; kind++) {
                Draw.color(cStars[kind]);
                for (int s = kind; s < starCount; s += 4) {
                    int i = st.i[s];
                    float f = st.f[s];
                    float v = st.v[s];
                    float half = (v >= 0 ? riftHl[i] : riftHr[i]) * (1f - f) + (v >= 0 ? riftHl[i + 1] : riftHr[i + 1]) * f;
                    if (half < 0.8f) continue;

                    float sx = st.cx[s] + st.nx[s] * half * v;
                    float sy = st.cy[s] + st.ny[s] * half * v;
                    float tw = 0.55f + 0.45f * (float) Math.sin(e.time * (0.25f + (s % 5) * 0.07f) + st.ph[s]);

                    Draw.alpha(tw * starBody);
                    Fill.circle(sx, sy, (0.5f + (s % 3) * 0.35f) * (0.6f + 0.4f * tw));

                    if (s % 7 == 0 && eff == 0) {
                        Lines.stroke(0.7f);
                        Lines.line(sx - 2.8f * tw, sy, sx + 2.8f * tw, sy);
                        Lines.line(sx, sy - 2.8f * tw, sx, sy + 2.8f * tw);
                    }
                }
            }
        }

        if (fin < 0.18f && eff < 3) {
            float flash = 1f - fin / 0.18f;
            Draw.color(Color.white);
            Draw.alpha(flash);
            Lines.stroke(1.5f + 5f * flash);
            for (int i = 0; i < n; i++) {
                if (riftHl[i] <= 0.05f && riftHl[i + 1] <= 0.05f) continue;
                Lines.line(px[i], py[i], px[i + 1], py[i + 1]);
            }
        }

        Draw.blend();
        Draw.reset();
    }

    static int lodOf(Effect.EffectContainer e) {
        return e.data instanceof RiftData ? ((RiftData) e.data).lod : 0;
    }

    static void riftArcDraw(Effect.EffectContainer e) {
        drawSpaceRift(e, true, 340f, 13f, lodOf(e));
    }

    static void riftCutDraw(Effect.EffectContainer e) {
        RiftData d = e.data instanceof RiftData ? (RiftData) e.data : null;
        drawSpaceRift(e, false, d != null && d.len != null ? d.len : 240f, 8.5f, lodOf(e));
    }

    static void riftBigDraw(Effect.EffectContainer e) {
        RiftData d = e.data instanceof RiftData ? (RiftData) e.data : null;
        drawSpaceRift(e, false, d != null && d.len != null ? d.len : 340f, d != null && d.hw != null ? d.hw : 15f, lodOf(e));
    }

    static final Effect spaceRiftArcFx = new Effect(60f, 700f, Swordoder::riftArcDraw);
    static final Effect spaceRiftCutFx = new Effect(50f, 600f, Swordoder::riftCutDraw);
    static final Effect spaceRiftBigFx = new Effect(75f, 700f, Swordoder::riftBigDraw);
    static final Effect spaceRiftArcFxFast = new Effect(30f, 700f, Swordoder::riftArcDraw);
    static final Effect spaceRiftCutFxFast = new Effect(25f, 600f, Swordoder::riftCutDraw);
    static final Effect spaceRiftBigFxFast = new Effect(38f, 700f, Swordoder::riftBigDraw);

    static void spawnRift(Effect full, Effect fast, float x, float y, float rot, Float len, Float hw) {
        if (Vars.headless) return;
        if (!Core.settings.getBool("effects", true)) return;

        arc.graphics.Camera cam = Core.camera;
        if (cam != null) {
            float clip = full.clip > 0 ? full.clip : 700f;
            if (Math.abs(x - cam.position.x) > cam.width * 0.5f + clip * 0.5f) return;
            if (Math.abs(y - cam.position.y) > cam.height * 0.5f + clip * 0.5f) return;
        }

        float now = Time.time;
        for (int i = riftAliveUntil.size() - 1; i >= 0; i--) {
            float until = riftAliveUntil.get(i);
            if (until <= now || until - now > 200f) riftAliveUntil.remove(i);
        }
        if (riftAliveUntil.size() >= RIFT_MAX_ALIVE) return;

        Effect fx = isLightOn() ? full : fast;
        riftAliveUntil.add(now + fx.lifetime);

        RiftData data = new RiftData();
        data.lod = riftDetail();
        data.len = len;
        data.hw = hw;
        fx.at(x, y, rot, Color.white, data);
    }

    static final Effect samuraiCutFx = new Effect(20f, 600f, e -> {
        RiftData d = e.data instanceof RiftData ? (RiftData) e.data : null;
        float len = d != null && d.len != null ? d.len : 240f;
        float fin = e.fin();
        float rot = e.rotation;

        float head = 1f - pw(1f - clamp01(fin / 0.3f), 3f);
        float tail = clamp01((fin - 0.12f) / 0.88f);
        tail = tail * tail;

        float ax = e.x - Angles.trnsx(rot, len / 2f);
        float ay = e.y - Angles.trnsy(rot, len / 2f);
        float bx = e.x + Angles.trnsx(rot, len / 2f);
        float by = e.y + Angles.trnsy(rot, len / 2f);

        float p0x = ax + (bx - ax) * tail;
        float p0y = ay + (by - ay) * tail;
        float p1x = ax + (bx - ax) * head;
        float p1y = ay + (by - ay) * head;
        if (head <= tail) return;

        float mx = (p0x + p1x) / 2f;
        float my = (p0y + p1y) / 2f;
        float nx = -Angles.trnsy(rot, 1f);
        float ny = Angles.trnsx(rot, 1f);
        float w = 6f * pw(1f - fin, 1.3f);

        Draw.z(Layer.effect + 0.03f);
        Draw.blend(Blending.additive);

        Draw.color(PerfCore.color("#7dd3fc"));
        Draw.alpha(0.8f * (1f - fin));
        Fill.tri(p0x, p0y, mx + nx * w * 1.8f, my + ny * w * 1.8f, mx - nx * w * 1.8f, my - ny * w * 1.8f);
        Fill.tri(p1x, p1y, mx + nx * w * 1.8f, my + ny * w * 1.8f, mx - nx * w * 1.8f, my - ny * w * 1.8f);

        Draw.color(Color.white);
        Draw.alpha(1f - fin * 0.6f);
        Fill.tri(p0x, p0y, mx + nx * w * 0.8f, my + ny * w * 0.8f, mx - nx * w * 0.8f, my - ny * w * 0.8f);
        Fill.tri(p1x, p1y, mx + nx * w * 0.8f, my + ny * w * 0.8f, mx - nx * w * 0.8f, my - ny * w * 0.8f);

        if (fin < 0.5f) {
            float k = fin / 0.5f;
            Draw.color(PerfCore.color("#e0f2ff"), PerfCore.color("#a855f7"), k);
            Draw.alpha(1f - k);
            Lines.stroke(1.4f * (1f - k) + 0.2f);
            for (int i = 0; i < 6; i++) {
                float a = rot + 90f + Mathf.randomSeed(e.id * 7L + i, -70f, 70f) + (i % 2 == 0 ? 0f : 180f);
                float dist = 6f + k * Mathf.randomSeed(e.id * 7L + i + 100, 14f, 36f);
                float sx = p1x + Angles.trnsx(a, dist);
                float sy = p1y + Angles.trnsy(a, dist);
                Lines.line(sx, sy, sx + Angles.trnsx(a, 4f), sy + Angles.trnsy(a, 4f));
            }
        }

        Draw.blend();
        Draw.reset();
    });

    // ---------- đạn ----------
    /** JS: b.data = {slashed, mult}. */
    static final class SlashData {
        boolean slashed;
        float mult;
        SlashData(boolean slashed, float mult) { this.slashed = slashed; this.mult = mult; }
    }

    static void triggerSlashArea(Bullet b) {
        if (b == null) return;
        if (b.data instanceof SlashData) {
            SlashData sd = (SlashData) b.data;
            if (sd.slashed) return;
            sd.slashed = true;
            float slashDmg = sd.mult * SLASH_DAMAGE;
            spawnRift(spaceRiftArcFx, spaceRiftArcFxFast, b.x, b.y, b.rotation(), null, null);
            damageArc(b.team, b.x, b.y, b.rotation(), 240f, 90f, slashDmg, b.owner);
        } else if (Boolean.TRUE.equals(b.data)) {
            return;
        } else {
            b.data = new SlashData(true, 1f);
            spawnRift(spaceRiftArcFx, spaceRiftArcFxFast, b.x, b.y, b.rotation(), null, null);
            damageArc(b.team, b.x, b.y, b.rotation(), 240f, 90f, SLASH_DAMAGE, b.owner);
        }
    }

    /**
     * Bài 98: JS khai thêm `collided(b, other)` nhưng BulletType v157 KHÔNG có hàm này (Rhino bỏ qua) →
     * chém khi trúng đích và checkExecute khi va chạm chưa từng chạy; chỉ `despawned` (hết tầm) kích hoạt chém. Java giữ y nguyên.
     */
    static final class SwordBullet extends BasicBulletType {
        SwordBullet() {
            super(6f, 500f);
            width = 24f;
            height = 24f;
            hitEffect = vSlashHitFx;
            despawnEffect = vSlashHitFx;
            buildingDamageMultiplier = 1f;
        }

        @Override
        public void despawned(Bullet b) {
            super.despawned(b);
            triggerSlashArea(b);
        }

        @Override
        public void draw(Bullet b) {
            Draw.z(Layer.bullet);
            TextureRegion reg = swordRegion();
            if (reg.found()) {
                Draw.rect(reg, b.x, b.y, reg.width * Draw.scl, reg.height * Draw.scl, b.rotation() - 90f);
            } else {
                super.draw(b);
            }
            Draw.reset();
        }
    }

    static final class Cut {
        float t, x, y, ang, dmg;
        boolean big, mk2b;
    }

    // ---------- block ----------
    public static void create() {
        swordoderBulletBase = new SwordBullet();

        swordoder = new ItemTurret("swordoder");
        swordoder.configurable = true;
        swordoder.health = 867000;
        swordoder.range = 210f;
        swordoder.reload = 120f;
        swordoder.recoil = 8f;
        swordoder.unitSort = UnitSorts.closest;

        swordoder.config(Integer.class, (SwordBuild tile, Integer value) -> {
            if (tile != null) tile.setTier(value);
        });
        swordoder.buildType = () -> new SwordBuild(swordoder);
    }

    public static class SwordBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;
        float subBulletTaskTimer = 0f;
        int pendingSubBullets = 0;
        float customRecoil = 0f;
        final ArrayList<Cut> comboQueue = new ArrayList<>();
        float comboClock = 0f;
        final ArrayList<Unit> lockedTargets = new ArrayList<>();
        float mk2bTimer = 60f;
        float mk2bScanTimer = Mathf.random(0f, 10f);

        public SwordBuild(ItemTurret outer) {
            outer.super();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (!comboQueue.isEmpty()) {
                comboClock += Time.delta;
                while (!comboQueue.isEmpty() && comboQueue.get(0).t <= comboClock) {
                    executeSamuraiCut(comboQueue.remove(0));
                }
                if (comboQueue.isEmpty()) comboClock = 0f;
            }

            if (customRecoil > 0) customRecoil = Math.max(0f, customRecoil - Time.delta * 0.15f);

            if (pendingSubBullets > 0) {
                subBulletTaskTimer -= Time.delta;
                if (subBulletTaskTimer <= 0) {
                    fire1BSidePattern(30f);
                    pendingSubBullets = 0;
                }
            }

            if (getTier() == 2) {
                updateMk2bStrike();
                return;
            }

            float currentReload = 120f;
            if (hasAmmo()) {
                if (timer.get(0, currentReload)) {
                    findTarget();
                    boolean isControlled = isControlled() || logicControlled();

                    if (target != null) {
                        wasShooting = true;
                        turnToTarget(angleTo(target));
                        shoot(swordoderBulletBase);
                    } else if (isControlled && isShooting()) { // bài 10: JS `this.isShooting` thiếu () nên luôn truthy
                        wasShooting = true;
                        shoot(swordoderBulletBase);
                    } else {
                        wasShooting = false;
                    }
                }
            }
        }

        @Override
        public void draw() {
            super.draw();

            TextureRegion reg = swordRegion();
            if (reg.found()) {
                Draw.z(Layer.turret + 0.01f);
                float offset = -customRecoil * swordoder.recoil;
                float rx = x + Angles.trnsx(rotation, offset);
                float ry = y + Angles.trnsy(rotation, offset);
                Draw.rect(reg, rx, ry, rotation - 90f);
            }

            if (getTier() == 2 && !lockedTargets.isEmpty()) {
                Draw.z(Layer.effect);
                Draw.color(Pal.remove);
                Lines.stroke(1.5f);
                for (Unit lockU : lockedTargets) {
                    if (lockU != null && lockU.isValid() && !lockU.dead) {
                        Lines.circle(lockU.x, lockU.y, lockU.hitSize / 2f + 6f);
                    }
                }
                Draw.reset();
            }
        }

        int getTier() { return tierState; }

        void setTier(int val) {
            if (val == 1 || val == 2) {
                if (getTier() == 0) {
                    tierState = val;
                    lockedTargets.clear();
                    mk2bTimer = 60f;
                }
            }
        }

        @Override
        public Object config() {
            return Integer.valueOf(getTier());
        }

        @Override
        public float range() {
            if (getTier() == 2) return BASE_RANGE * 4.8f; // MK2B
            return BASE_RANGE;
        }

        float rollCrit() {
            if (getTier() == 1 && Mathf.chance(MK2_CRIT_CHANCE)) return MK2_CRIT_MULT;
            return 1f;
        }

        void updateMk2bStrike() {
            mk2bTimer = Math.min(60f, mk2bTimer + Time.delta);
            mk2bScanTimer += Time.delta;

            if (mk2bScanTimer >= 10f) {
                mk2bScanTimer = 0f;
                final float rng = range();
                final float rng2 = rng * rng;
                final Unit[] nearU = new Unit[3];
                final float[] nearD = new float[3];
                final int[] nearCount = {0};

                Units.nearbyEnemies(team, x - rng, y - rng, rng * 2f, rng * 2f, u -> {
                    if (!u.isValid() || u.dead) return;
                    float d2 = dst2(u);
                    if (d2 > rng2) return;
                    if (nearCount[0] == 3 && d2 >= nearD[2]) return;

                    int pos = nearCount[0] < 3 ? nearCount[0] : 2;
                    while (pos > 0 && nearD[pos - 1] > d2) {
                        nearU[pos] = nearU[pos - 1];
                        nearD[pos] = nearD[pos - 1];
                        pos--;
                    }
                    nearU[pos] = u;
                    nearD[pos] = d2;
                    if (nearCount[0] < 3) nearCount[0]++;
                });

                lockedTargets.clear();
                for (int i = 0; i < nearCount[0]; i++) lockedTargets.add(nearU[i]);
            }

            if (!lockedTargets.isEmpty()) turnToTarget(angleTo(lockedTargets.get(0)));

            if (mk2bTimer >= 60f && !lockedTargets.isEmpty() && hasAmmo()) {
                mk2bTimer = 0f;
                customRecoil = 1f;
                useAmmo();

                for (Unit lockU : lockedTargets) {
                    if (lockU != null && lockU.isValid() && !lockU.dead) {
                        Cut c = new Cut();
                        c.x = lockU.x;
                        c.y = lockU.y;
                        c.ang = Mathf.random(0f, 180f);
                        c.big = true;
                        c.mk2b = true;
                        c.dmg = SLASH_DAMAGE * 0.4f; // MK2B: -60% sát thương
                        executeSamuraiCut(c);
                    }
                }
            }
        }

        void fire1BSidePattern(float angleOffset) {
            float currentRange = range();
            float targetDist = Math.max(1f, currentRange - 1f);

            if (targetPos != null && (target != null || isControlled())) {
                float distToTarget = Mathf.dst(x, y, targetPos.x, targetPos.y);
                targetDist = Math.min(distToTarget, targetDist);
            }

            float baseAngle = rotation + angleOffset;
            float critMult = rollCrit();
            float rndSpeed = 6f;
            float calculatedLifetime = targetDist / rndSpeed;

            Bullet b = swordoderBulletBase.create(this, team, x, y, baseAngle);
            if (b != null) {
                b.data = new SlashData(false, critMult);
                b.vel.setLength(rndSpeed);
                b.lifetime = calculatedLifetime;
                b.damage = BULLET_DAMAGE * critMult;
            }
        }

        void startSamuraiCombo() {
            if (!comboQueue.isEmpty()) return;

            float range = range();
            float aimAngle = rotation;
            float dist = range * 0.75f;

            if (target != null) {
                aimAngle = angleTo(target);
                dist = Math.min(range, Mathf.dst(x, y, target.getX(), target.getY()));
            } else if (targetPos != null) {
                aimAngle = Mathf.angle(targetPos.x - x, targetPos.y - y);
                dist = Math.min(range, Mathf.dst(x, y, targetPos.x, targetPos.y));
            }

            float cx = x + Angles.trnsx(aimAngle, dist);
            float cy = y + Angles.trnsy(aimAngle, dist);

            float baseAng = Mathf.random(0f, 180f);
            float travelDelay = dist / 6f;
            // {da, d, big}
            float[][] pattern = {
                {-38, 0, 0}, {38, 7, 0}, {90, 14, 0}, {0, 21, 0}, {-64, 28, 0}, {64, 35, 0}, {96, 46, 1}
            };

            for (float[] step : pattern) {
                boolean big = step[2] > 0;
                Cut c = new Cut();
                c.t = comboClock + travelDelay + step[1];
                c.x = cx + (big ? 0f : Mathf.range(14f));
                c.y = cy + (big ? 0f : Mathf.range(14f));
                c.ang = baseAng + step[0];
                c.big = big;
                c.dmg = SLASH_DAMAGE * rollCrit() * (big ? 3f : 1f);
                comboQueue.add(c);
            }
        }

        void executeSamuraiCut(Cut cut) {
            if (cut == null || !isValid()) return;

            float len = cut.mk2b ? 170f : (cut.big ? 340f : 240f); // MK2B: nhát chém nhỏ đi một nửa

            RiftData cd = new RiftData();
            cd.len = len;
            samuraiCutFx.at(cut.x, cut.y, cut.ang, Color.white, cd);
            if (cut.big) {
                spawnRift(spaceRiftBigFx, spaceRiftBigFxFast, cut.x, cut.y, cut.ang, len, cut.mk2b ? Float.valueOf(7.5f) : null);
            } else {
                spawnRift(spaceRiftCutFx, spaceRiftCutFxFast, cut.x, cut.y, cut.ang, len, null);
            }

            if (!Vars.headless) {
                Effect.shake(cut.big ? 5f : 1.8f, cut.big ? 12f : 5f, cut.x, cut.y);
            }

            if (!Vars.net.client()) {
                damageCut(team, cut.x, cut.y, cut.ang, len, cut.dmg, this, cut.mk2b ? 14f : 28f);
            }
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            if (tier == 2) return;

            customRecoil = 1f;

            if (Mathf.chance(0.5)) startSamuraiCombo();

            if (tier == 1) {
                fire1BSidePattern(-30f);
                pendingSubBullets = 1;
                subBulletTaskTimer = 12f;
                useAmmo();
                return;
            }

            float currentRange = range();
            float targetDist = Math.max(1f, currentRange - 1f);

            if (targetPos != null && (target != null || isControlled())) {
                float distToTarget = Mathf.dst(x, y, targetPos.x, targetPos.y);
                targetDist = Math.min(distToTarget, targetDist);
            }

            float rndSpeed = 6f;
            float calculatedLifetime = targetDist / rndSpeed;

            Bullet b = swordoderBulletBase.create(this, team, x, y, rotation);
            if (b != null) {
                b.data = new SlashData(false, 1f);
                b.vel.setLength(rndSpeed);
                b.lifetime = calculatedLifetime;
                b.damage = BULLET_DAMAGE;
            }

            useAmmo();
        }

        void tryUpgrade(BaseDialog dialog, int targetTier, Item[] items, int[] amounts) {
            CoreBuild core = team.core();
            boolean enough = core != null;
            for (int i = 0; enough && i < items.length; i++) {
                if (core.items.get(items[i]) < amounts[i]) enough = false;
            }

            if (enough && getTier() == 0) {
                for (int i = 0; i < items.length; i++) NexNet.take(core, items[i], amounts[i]);
                NexNet.configure(this, Integer.valueOf(targetTier));
                NexNet.fx(Fx.upgradeCore, x, y);
                NexNet.shake(4f, 4f, x, y);
                dialog.hide();
                deselect();
            } else {
                Vars.ui.showInfo(t("[red]Không đủ tài nguyên để nâng cấp![]", "[red]Not enough resources to upgrade![]"));
            }
        }

        void openUpgradeDialog() {
            final BaseDialog dialog = new BaseDialog(t("Trung tâm nâng cấp pháo Swordoder", "Swordoder Turret Upgrade Center"));

            String snap;
            CoreBuild core = team.core();
            if (core == null) {
                snap = t("[red]Không tìm thấy Lõi Đội![]", "[red]Team Core not found![]");
            } else {
                int cCop = core.items.get(Items.copper);
                int cLea = core.items.get(Items.lead);
                int cSil = core.items.get(Items.silicon);
                int cTit = core.items.get(Items.titanium);
                int cTho = core.items.get(Items.thorium);
                int cGra = core.items.get(Items.graphite);

                String colCop = cCop >= reqMK2.copper ? "[green]" : "[red]";
                String colLea = cLea >= reqMK2.lead ? "[green]" : "[red]";
                String colSil = cSil >= reqMK2.silicon ? "[green]" : "[red]";

                String colTit = cTit >= reqMK2B.titanium ? "[green]" : "[red]";
                String colTho = cTho >= reqMK2B.thorium ? "[green]" : "[red]";
                String colGra = cGra >= reqMK2B.graphite ? "[green]" : "[red]";

                snap = t(
                    "[yellow]YÊU CẦU TÀI NGUYÊN LÕI (CẤP MK1):[]\n" +
                    "[cyan]Nhánh MK2:[]\n" +
                    " • Đồng: " + colCop + cCop + "[] / " + reqMK2.copper + "\n" +
                    " • Chì: " + colLea + cLea + "[] / " + reqMK2.lead + "\n" +
                    " • Silicon: " + colSil + cSil + "[] / " + reqMK2.silicon + "\n" +
                    "[cyan]Nhánh MK2B:[]\n" +
                    " • Titan: " + colTit + cTit + "[] / " + reqMK2B.titanium + "\n" +
                    " • Thorium: " + colTho + cTho + "[] / " + reqMK2B.thorium + "\n" +
                    " • Than chì: " + colGra + cGra + "[] / " + reqMK2B.graphite,

                    "[yellow]CORE RESOURCE REQUIREMENTS (MK1 TIER):[]\n" +
                    "[cyan]MK2 Branch:[]\n" +
                    " • Copper: " + colCop + cCop + "[] / " + reqMK2.copper + "\n" +
                    " • Lead: " + colLea + cLea + "[] / " + reqMK2.lead + "\n" +
                    " • Silicon: " + colSil + cSil + "[] / " + reqMK2.silicon + "\n" +
                    "[cyan]MK2B Branch:[]\n" +
                    " • Titanium: " + colTit + cTit + "[] / " + reqMK2B.titanium + "\n" +
                    " • Thorium: " + colTho + cTho + "[] / " + reqMK2B.thorium + "\n" +
                    " • Graphite: " + colGra + cGra + "[] / " + reqMK2B.graphite
                );
            }
            arc.scene.ui.layout.Cell<?> reqCell = dialog.cont.add(snap);
            reqCell.width(380f);
            ((arc.scene.ui.Label) reqCell.get()).setWrap(true);
            ((arc.scene.ui.Label) reqCell.get()).setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table mainTable = new Table();
            int tier = getTier();

            Table boxA = new Table();
            boxA.background(Styles.black6);
            boxA.margin(12f);
            boxA.add(t("[yellow]===(MK2)===[]", "[yellow]===(MK2)===[]")).row();

            String txtA = tier == 1 ? t("[green]✔ ĐÃ NÂNG CẤP MK2[]\n", "[green]✔ MK2 UPGRADED[]\n") : "";
            txtA += t(
                "Kiếm sĩ song hướng:\n" +
                " [white]• Kiểu bắn mới: [green]2 bên góc 30°[] (30° trước, 30° sau [yellow]0.2s[])\n" +
                " • Tỉ lệ bạo kích: [green]+50%[]\n" +
                " • Sát thương bạo kích: [green]+145%[]\n" +
                " • Áp dụng cho đạn và vệt chém[]",

                "Dual-side swordsman:\n" +
                " [white]• New firing pattern: [green]both sides at 30°[] (30° front, 30° back after [yellow]0.2s[])\n" +
                " • Crit chance: [green]+50%[]\n" +
                " • Crit damage: [green]+145%[]\n" +
                " • Applies to bullets and slashes[]"
            );
            arc.scene.ui.layout.Cell<?> txtACell = boxA.add(txtA);
            txtACell.width(340f);
            ((arc.scene.ui.Label) txtACell.get()).setWrap(true);
            ((arc.scene.ui.Label) txtACell.get()).setAlignment(Align.left);
            boxA.row();

            if (tier == 0) {
                boxA.button(t("[yellow]NÂNG CẤP MK2[]", "[yellow]UPGRADE MK2[]"), () -> tryUpgrade(dialog, 1,
                    new Item[]{Items.copper, Items.lead, Items.silicon},
                    new int[]{reqMK2.copper, reqMK2.lead, reqMK2.silicon})).size(280f, 40f);
            }

            mainTable.add(boxA).width(360f).row();
            mainTable.add().height(12f).row();

            Table boxB = new Table();
            boxB.background(Styles.black6);
            boxB.margin(12f);
            boxB.add(t("[cyan]===(MK2B)===[]", "[cyan]===(MK2B)===[]")).row();

            String txtB = tier == 2 ? t("[green]✔ ĐÃ NÂNG CẤP MK2B[]\n", "[green]✔ MK2B UPGRADED[]\n") : "";
            txtB += t(
                "Trảm kiếm tầm xa:\n" +
                " [white]• Tầm bắn: [green]+500%[] (1,260px)\n" +
                " • Mục tiêu vào tầm bắn: lập tức [yellow]mỗi 1s[] tạo nhát chém ngay tại vị trí mục tiêu\n" +
                " • Khóa tối đa [green]3 mục tiêu[]\n" +
                " • Không còn bắn đạn thường[]",

                "Long-range slayer:\n" +
                " [white]• Range: [green]+500%[] (1,260px)\n" +
                " • Target in range: instantly [yellow]every 1s[] creates a slash at the target position\n" +
                " • Locks up to [green]3 targets[]\n" +
                " • No longer fires normal bullets[]"
            );
            arc.scene.ui.layout.Cell<?> txtBCell = boxB.add(txtB);
            txtBCell.width(340f);
            ((arc.scene.ui.Label) txtBCell.get()).setWrap(true);
            ((arc.scene.ui.Label) txtBCell.get()).setAlignment(Align.left);
            boxB.row();

            if (tier == 0) {
                boxB.button(t("[cyan]NÂNG CẤP MK2B[]", "[cyan]UPGRADE MK2B[]"), () -> tryUpgrade(dialog, 2,
                    new Item[]{Items.titanium, Items.thorium, Items.graphite},
                    new int[]{reqMK2B.titanium, reqMK2B.thorium, reqMK2B.graphite})).size(280f, 40f);
            }

            mainTable.add(boxB).width(360f);

            ScrollPane scroll = new ScrollPane(mainTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        void openInfoDialog() {
            int tier = getTier();
            String tierName = tier == 2 ? "Mk2B" : (tier == 1 ? "Mk2" : "Mk1");
            String title = t(" Thông số pháo Swordoder " + tierName + " ", " Swordoder " + tierName + " Stats ");

            int rangePx = tier == 2 ? 1260 : 210;
            String rangeTiles = String.format(Locale.US, "%.1f", rangePx / 8f);

            String descStr = t(
                "[gold]⚡ THÔNG SỐ PHÁO SWORDODER (" + tierName.toUpperCase() + ") ⚡[]\n" +
                "[lightgray]Máu:[] [green]867,000[]\n" +
                "[lightgray]Tầm bắn:[] [green]" + rangePx + "px (" + rangeTiles + " ô)[]\n" +
                "[lightgray]Sát thương gốc:[] [green]500[] (đạn) + [green]1,200[] (chém)\n" +
                "[red]• Tận diệt:[] Mục tiêu còn [yellow]<= 15% máu[] chịu sát thương bằng [yellow]100% máu tối đa[]\n" +
                "[white]• Khi đạn chạm hoặc hết tầm tạo vệt chém diện rộng[]",

                "[gold]⚡ SWORDODER TURRET STATS (" + tierName.toUpperCase() + ") ⚡[]\n" +
                "[lightgray]Health:[] [green]867,000[]\n" +
                "[lightgray]Range:[] [green]" + rangePx + "px (" + rangeTiles + " tiles)[]\n" +
                "[lightgray]Base Damage:[] [green]500[] (bullet) + [green]1,200[] (slash)\n" +
                "[red]• Execute:[] Targets at [yellow]<= 15% HP[] take damage equal to [yellow]100% max HP[]\n" +
                "[white]• On hit or when range ends, bullets create an area slash[]"
            );
            if (tier == 1) {
                descStr += t(
                    "\n\n[yellow]===(MK2)===[]\n[white]• Bắn 2 bên góc 30° (30° trước, 30° sau 0.2s)\n• Bạo kích: [green]50%[] tỉ lệ, [green]+145%[] sát thương[]",
                    "\n\n[yellow]===(MK2)===[]\n[white]• Fires both sides at 30° (30° front, 30° back after 0.2s)\n• Crit: [green]50%[] chance, [green]+145%[] damage[]"
                );
            }

            if (tier == 2) {
                descStr += t(
                    "\n\n[cyan]===(MK2B)===[]\n[white]• Tầm bắn +500%\n• Mỗi 1s tạo nhát chém tại vị trí mục tiêu\n• Khóa tối đa 3 mục tiêu[]",
                    "\n\n[cyan]===(MK2B)===[]\n[white]• Range +500%\n• Every 1s creates a slash at target position\n• Locks up to 3 targets[]"
                );
            }

            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            arc.scene.ui.layout.Cell<?> cell = infoTable.add(descStr).width(360f);
            ((arc.scene.ui.Label) cell.get()).setWrap(true);
            ((arc.scene.ui.Label) cell.get()).setAlignment(Align.left);

            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog)
                .size(50f, 40f).tooltip(t("Trung tâm nâng cấp pháo Swordoder", "Swordoder Turret Upgrade Center"));
            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog)
                .size(50f, 40f).tooltip(t("Xem thông số pháo Swordoder", "View Swordoder Stats"));
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(100 + getTier());
            write.b(0);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int storedTier = read.b();
            read.b();
            tierState = (storedTier >= 100 && storedTier <= 102) ? (storedTier - 100) : 0;
        }
    }
}
