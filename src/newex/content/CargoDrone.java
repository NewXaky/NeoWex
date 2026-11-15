package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import newex.PerfCore;

/**
 * Chuyển từ cargo-drone.js (2560 dòng): AI + hiệu ứng của Cargo Drone / MK2 / Sword Drone (brawler) / Master Sword Drone.
 * Bảng dùng chung (launcherMap, mk2bUnits, recallSet) nằm ở {@link DroneLauncher}.
 * CHƯA biên dịch thật (sandbox không có jar Mindustry) – xem MIGRATION.md bài 113–118.
 */
public final class CargoDrone {
    private CargoDrone() {}

    // ===== Màu =====
    static final Color coalGlowColor = Color.valueOf("888888");
    static final Color coalColor = Color.valueOf("272727");
    static final Color masterPurpleColor = Color.valueOf("2b003b");
    static final Color masterGlowColor = Color.valueOf("a832d7");

    static boolean isBloomEnabled() {
        try { return Core.settings.getBool("bloom", true); } catch (Throwable t) { return true; }
    }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    // ===== Dữ liệu truyền vào Effect (thay object literal JS) =====
    static final class LineData {
        final float x2, y2, w; final boolean taper, head;
        LineData(float x2, float y2, float w, boolean taper, boolean head) { this.x2 = x2; this.y2 = y2; this.w = w; this.taper = taper; this.head = head; }
    }
    static final class WarpBlur {
        final TextureRegion region; final float rot, blur, alpha;
        WarpBlur(TextureRegion region, float rot, float blur, float alpha) { this.region = region; this.rot = rot; this.blur = blur; this.alpha = alpha; }
    }
    static final class SkyRain {
        final float skyX, skyY, targetX, targetY; final boolean hasLaser;
        SkyRain(float skyX, float skyY, float targetX, float targetY, boolean hasLaser) { this.skyX = skyX; this.skyY = skyY; this.targetX = targetX; this.targetY = targetY; this.hasLaser = hasLaser; }
    }

    // ===== Hiệu ứng master =====
    static final Effect masterLowParticleImpactEffect = new Effect(15f, e -> {
        Draw.z(Layer.effect + 0.1f);
        Draw.color(masterGlowColor, Color.white, e.fout());
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 10f * e.fin());
        Draw.reset();
    });

    static final Effect swordWindSlashFx = new Effect(18f, 700f, e -> {
        if (!(e.data instanceof LineData)) return;
        LineData d = (LineData) e.data;
        float ax = e.x, ay = e.y, bx = d.x2, by = d.y2;
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;

        float ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
        float fin = e.fin(), fout = e.fout();
        float w = d.w * (float) Math.pow(fout, 1.3);
        if (w < 0.05f) return;

        float tail = d.taper ? fin * 0.55f : 0f;
        float sx = ax + dx * tail, sy = ay + dy * tail;
        float slen = len * (1f - tail);
        int seed = e.id * 37;
        Color black = PerfCore.color("#000000"), white = PerfCore.color("#ffffff"), grey = PerfCore.color("#d0d0d0");

        Draw.z(Layer.effect + 0.06f);

        if (d.taper) {
            float mx = sx + ux * slen * 0.38f, my = sy + uy * slen * 0.38f;
            Draw.color(grey); Draw.alpha(0.18f * fout);
            Fill.tri(sx, sy, mx + nx * w * 2.6f, my + ny * w * 2.6f, mx - nx * w * 2.6f, my - ny * w * 2.6f);
            Fill.tri(bx, by, mx + nx * w * 2.6f, my + ny * w * 2.6f, mx - nx * w * 2.6f, my - ny * w * 2.6f);

            Draw.color(black); Draw.alpha(0.95f);
            Fill.tri(sx, sy, mx + nx * w, my + ny * w, mx - nx * w, my - ny * w);
            Fill.tri(bx, by, mx + nx * w, my + ny * w, mx - nx * w, my - ny * w);

            Draw.color(white); Draw.alpha(fout);
            Lines.stroke(1.3f * fout + 0.25f);
            Lines.line(sx, sy, mx + nx * w, my + ny * w);
            Lines.line(mx + nx * w, my + ny * w, bx, by);
            Lines.line(sx, sy, mx - nx * w, my - ny * w);
            Lines.line(mx - nx * w, my - ny * w, bx, by);
        } else {
            Draw.color(grey); Draw.alpha(0.16f * fout);
            Fill.quad(sx + nx * w * 2.4f, sy + ny * w * 2.4f, bx + nx * w * 2.4f, by + ny * w * 2.4f,
                      bx - nx * w * 2.4f, by - ny * w * 2.4f, sx - nx * w * 2.4f, sy - ny * w * 2.4f);

            Draw.color(black); Draw.alpha(0.95f);
            Fill.quad(sx + nx * w, sy + ny * w, bx + nx * w * 0.8f, by + ny * w * 0.8f,
                      bx - nx * w * 0.8f, by - ny * w * 0.8f, sx - nx * w, sy - ny * w);

            Draw.color(white); Draw.alpha(fout);
            Lines.stroke(1.2f * fout + 0.2f);
            Lines.line(sx + nx * w, sy + ny * w, bx + nx * w * 0.8f, by + ny * w * 0.8f);
            Lines.line(sx - nx * w, sy - ny * w, bx - nx * w * 0.8f, by - ny * w * 0.8f);
        }

        int streaks = PerfCore.level() >= 1 ? 2 : 4;
        Draw.color(white);
        Lines.stroke(0.8f * fout + 0.15f);
        for (int i = 0; i < streaks; i++) {
            float side = (i % 2 == 0) ? 1f : -1f;
            float off = side * w * (1.8f + Mathf.randomSeed(seed + i * 5, 0f, 2.2f));
            float t0 = Mathf.randomSeed(seed + i * 5 + 1, 0f, 0.4f) + fin * 0.35f;
            float t1 = Math.min(1f, t0 + Mathf.randomSeed(seed + i * 5 + 2, 0.35f, 0.6f));
            Draw.alpha(0.85f * fout);
            Lines.line(sx + ux * slen * t0 + nx * off, sy + uy * slen * t0 + ny * off,
                       sx + ux * slen * t1 + nx * off, sy + uy * slen * t1 + ny * off);
        }

        if (d.head) {
            float ang = Angles.angle(ax, ay, bx, by) + 180f;
            float vLen = Math.min(slen * 0.3f, 70f);
            Draw.color(white); Draw.alpha(fout);
            Lines.stroke(1.4f * fout + 0.2f);
            Lines.line(bx, by, bx + Angles.trnsx(ang + 15f, vLen), by + Angles.trnsy(ang + 15f, vLen));
            Lines.line(bx, by, bx + Angles.trnsx(ang - 15f, vLen), by + Angles.trnsy(ang - 15f, vLen));
        }
        Draw.reset();
    });

    static final Effect masterKeySwordFlyEffect = new Effect(25f, e -> {
        Draw.z(Layer.flyingUnit + 2.9f);
        if (!(e.data instanceof float[])) return;
        float[] d = (float[]) e.data;   // startX, startY, targetX, targetY
        float startX = d[0], startY = d[1], targetX = d[2], targetY = d[3];
        float angle = Angles.angle(startX, startY, targetX, targetY);
        float curX = Mathf.lerp(startX, targetX, e.fin());
        float curY = Mathf.lerp(startY, targetY, e.fin());
        float trailLen = 60f * e.fout();
        float backX = curX - Angles.trnsx(angle, trailLen);
        float backY = curY - Angles.trnsy(angle, trailLen);

        Draw.color(Color.black, e.fout());
        Lines.stroke(6f * e.fout());
        Lines.line(curX, curY, backX, backY);
        Draw.color(Color.white, e.fout());
        Lines.stroke(2.5f * e.fout());
        Lines.line(curX, curY, backX, backY);

        drawSwordStar(curX, curY, angle, 1.3f, 1f, true, e.id, false);

        if (e.fin() >= 0.85f) {
            if (isBloomEnabled()) Drawf.light(targetX, targetY, 150f, masterGlowColor, 1f);
            Lines.stroke(3f * e.fout());
            Lines.circle(targetX, targetY, 40f * (1f - e.fout()));
        }
        Draw.reset();
    });

    static final Effect masterShockwaveRingEffect = new Effect(60f, e -> {
        Draw.z(Layer.effect + 0.1f);
        float maxRadius = e.rotation;
        float waveCount = e.data instanceof Number ? ((Number) e.data).floatValue() : 1f;
        for (int i = 0; i < waveCount; i++) {
            float offsetProg = (e.fin() + i * 0.4f) % 1f;
            float radius = maxRadius * offsetProg;
            float alpha = (1f - offsetProg) * e.fout();
            Draw.color(masterGlowColor);
            Lines.stroke(4f * alpha);
            Lines.circle(e.x, e.y, radius);
            Draw.color(masterPurpleColor);
            Lines.stroke(2f * alpha);
            Lines.circle(e.x, e.y, radius);
        }
        Draw.reset();
    });

    static final Effect swordImpactShockwaveEffect = new Effect(45f, e -> {
        Draw.z(Layer.effect + 0.2f);
        float radius = 120f * e.fin();
        Draw.color(masterGlowColor);
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, radius);
        Draw.color(masterPurpleColor);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, radius * 0.8f);
        Draw.color(Color.white);
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, radius);
        Draw.reset();
    });

    static final Effect masterBeamEffect = new Effect(40f, e -> {
        Draw.z(Layer.effect + 0.5f);
        float nwX = e.x - 400f, nwY = e.y + 400f;
        if (isBloomEnabled()) Drawf.light(e.x, e.y, 160f * e.fout(), masterGlowColor, 0.9f * e.fout());
        Draw.color(masterPurpleColor, Color.black, e.fout());
        Lines.stroke(18f * e.fout());
        Lines.line(nwX, nwY, e.x, e.y);
        Draw.color(masterGlowColor, Color.white, e.fout());
        Lines.stroke(9f * e.fout());
        Lines.line(nwX, nwY, e.x, e.y);
        Draw.color(Color.white, e.fout());
        Lines.stroke(3f * e.fout());
        Lines.line(nwX, nwY, e.x, e.y);
    });

    static final Effect masterSwordsSummonEffect = new Effect(70f, e -> {
        Draw.z(Layer.flyingUnit + 2.5f);
        float neAngle = 45f, baseOffsetAngle = neAngle + 90f;
        float[] seeds = e.data instanceof float[] ? (float[]) e.data : new float[]{0f, 0f, 0f};
        for (int i = -1; i <= 1; i++) {
            float totalShift = i * 22f + seeds[i + 1];
            float posX = e.x + Angles.trnsx(baseOffsetAngle, totalShift);
            float posY = e.y + Angles.trnsy(baseOffsetAngle, totalShift);
            if (isBloomEnabled()) Drawf.light(posX, posY, 60f * e.fout(), masterGlowColor, 0.8f * e.fout());
            drawSwordStar(posX, posY, neAngle, 1.3f, 0.9f * e.fout(), true, e.id + i * 5, false);
        }
        Draw.reset();
    });

    static final Effect skySwordHoverEffect = new Effect(450f, e -> {
        Draw.z(Layer.flyingUnit + 2.8f);
        float alpha = Math.min(1f, e.fin() * 6f) * e.fout();
        drawSwordStar(e.x, e.y, e.rotation, 1.3f, alpha, true, e.id, false);
        Draw.reset();
    });

    static final Effect skySwordRainEffect = new Effect(14f, e -> {
        Draw.z(Layer.flyingUnit + 3f);
        if (!(e.data instanceof SkyRain)) return;
        SkyRain d = (SkyRain) e.data;
        float angle = e.rotation;
        float currentX = Mathf.lerp(d.skyX, d.targetX, e.fin());
        float currentY = Mathf.lerp(d.skyY, d.targetY, e.fin());

        if (d.hasLaser) {
            Draw.z(Layer.effect + 0.4f);
            float skyTopX = d.targetX - Angles.trnsx(angle, 600f);
            float skyTopY = d.targetY - Angles.trnsy(angle, 600f);
            Draw.color(Color.black, Color.white, e.fout());
            Lines.stroke(6f * e.fout());
            Lines.line(skyTopX, skyTopY, d.targetX, d.targetY);
            Draw.color(Color.white, e.fout());
            Lines.stroke(2f * e.fout());
            Lines.line(skyTopX, skyTopY, d.targetX, d.targetY);
            Draw.z(Layer.flyingUnit + 3f);
        }

        float trailLen = 120f;
        float backX = currentX - Angles.trnsx(angle, trailLen);
        float backY = currentY - Angles.trnsy(angle, trailLen);
        Draw.color(Color.black, e.fout());
        Lines.stroke(6f * e.fout());
        Lines.line(currentX, currentY, backX, backY);
        Draw.color(Color.white, e.fout());
        Lines.stroke(2.2f * e.fout());
        Lines.line(currentX, currentY, backX, backY);

        drawSwordStar(currentX, currentY, angle, 1.35f, 1f, true, e.id, false);

        if (e.fin() >= 0.9f && isBloomEnabled()) Drawf.light(d.targetX, d.targetY, 120f, masterGlowColor, 0.9f);
        Draw.reset();
    });

    /** forward/side của 3 (bên trái) + 2 (bên phải) kiếm gắn trên master. */
    static final float[][] MASTER_OFFSETS = {{6f, 14f}, {-6f, 18f}, {-18f, 22f}, {6f, -14f}, {-6f, -18f}};

    static final Effect renderAttachedMasterSwordsEffect = new Effect(2f, e -> {
        Draw.z(Layer.flyingUnit + 2f);
        float unitAngle = e.rotation, sideAngle = unitAngle - 90f;
        int hideIndex = e.data instanceof Integer ? (Integer) e.data : -1;
        for (int i = 0; i < 3; i++) {
            if (i == hideIndex) continue;
            float[] off = MASTER_OFFSETS[i];
            float sx = e.x + Angles.trnsx(unitAngle, off[0]) + Angles.trnsx(sideAngle, off[1]);
            float sy = e.y + Angles.trnsy(unitAngle, off[0]) + Angles.trnsy(sideAngle, off[1]);
            if (isBloomEnabled()) Drawf.light(sx, sy, 35f, masterGlowColor, 0.8f);
            float renderAngle = unitAngle + 90f;
            drawSwordStar(sx, sy, renderAngle + 90f, 1.05f, 1f, true, i * 7 + 3, false);
            drawSwordSparkles(sx, sy, renderAngle + 90f, 1.05f, 1f, i * 7 + 3, true);
        }
        Draw.reset();
    });

    static final Effect recallLaserEffect = new Effect(30f, e -> {
        float originX = e.data instanceof float[] ? ((float[]) e.data)[0] : e.x;
        float originY = e.data instanceof float[] ? ((float[]) e.data)[1] : e.y;
        Draw.color(Color.sky, Color.white, e.fout());
        Lines.stroke(4f * e.fout());
        Lines.line(originX, originY, e.x, e.y);
        Draw.color(Color.white);
        Lines.stroke(1.5f * e.fout());
        Lines.line(originX, originY, e.x, e.y);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, 16f * e.fin());
    });

    /** JS khai báo nhưng không nơi nào gọi (giữ cho đủ bộ, parity). */
    static final Effect swordDashTrailEffect = new Effect(45f, e -> {
        Draw.z(Layer.flyingUnit - 0.1f);
        float tailLen = 90f;
        float backX = e.x - Angles.trnsx(e.rotation, tailLen);
        float backY = e.y - Angles.trnsy(e.rotation, tailLen);
        if (isBloomEnabled()) {
            Drawf.light(e.x, e.y, 120f * e.fout(), Color.white, e.fout());
            Drawf.light(backX, backY, 90f * e.fout(), coalGlowColor, 0.8f * e.fout());
        }
        Draw.color(Color.white, coalGlowColor, e.fin());
        Draw.alpha(0.6f * e.fout());
        Fill.circle(e.x, e.y, 22f * e.fout());
        Fill.circle(backX, backY, 12f * e.fout());
        Draw.color(coalColor, Color.black, e.fout());
        Lines.stroke(20f * e.fout());
        Lines.line(e.x, e.y, backX, backY);
        Draw.color(coalGlowColor, Color.white, e.fout());
        Lines.stroke(10f * e.fout());
        Lines.line(e.x, e.y, backX, backY);
        Draw.color(Color.white, e.fout());
        Lines.stroke(3.5f * e.fout());
        Lines.line(e.x, e.y, backX, backY);
    });

    // ===== DASH kiểu "nhảy siêu không gian" =====
    static final float WARP_DASH_TICKS = 26f;
    static final float WARP_EASE_POW = 4f;

    static final Effect warpBlurFx = new Effect(3f, 400f, e -> {
        if (!(e.data instanceof WarpBlur)) return;
        WarpBlur d = (WarpBlur) e.data;
        if (d.region == null) return;
        float a = e.fout() * d.alpha;
        if (a <= 0.01f) return;
        int n = PerfCore.level() >= 2 ? 3 : 7;
        float ang = e.rotation;
        Draw.z(Layer.flyingUnit - 0.05f);
        Draw.mixcol(Color.white, 0.55f);
        for (int i = 1; i <= n; i++) {
            float f = i / (float) n;
            Draw.alpha(a * (1f - f) * 0.7f);
            Draw.rect(d.region, e.x - Angles.trnsx(ang, d.blur * f), e.y - Angles.trnsy(ang, d.blur * f), d.rot - 90f);
        }
        Draw.mixcol();
        Draw.reset();
    });

    static final Effect warpDepartFx = new Effect(26f, 1200f, e -> {
        if (!(e.data instanceof float[])) return;
        float[] d = (float[]) e.data;   // x2, y2
        float fin = e.fin(), fout = e.fout();
        float dx = d[0] - e.x, dy = d[1] - e.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float ux = dx / len, uy = dy / len, nx = -uy, ny = ux;
        int lines = PerfCore.level() >= 2 ? 3 : 7;

        Draw.z(Layer.effect + 0.08f);
        if (isBloomEnabled()) Drawf.light(e.x, e.y, 150f * fout, Color.white, fout);

        Draw.color(Color.white);
        Draw.alpha(0.85f * fout * fout);
        Fill.circle(e.x, e.y, 30f * fout);
        Lines.stroke(3.5f * fout);
        Lines.circle(e.x, e.y, 80f * fin);

        float head = clamp01(fin * 3.5f);
        float tail = clamp01(fin * 1.6f - 0.15f);
        for (int i = 0; i < lines; i++) {
            float off = (i - (lines - 1) / 2f) * 7f + Mathf.randomSeed(e.id * 7 + i, -3f, 3f);
            float l0 = tail * len * (0.9f + 0.1f * Mathf.randomSeed(e.id + i * 3, 0f, 1f));
            float l1 = head * len;
            if (l1 <= l0) continue;
            float w = 1f - Math.abs(off) / 30f;
            Draw.color(Color.black);
            Draw.alpha(0.6f * fout);
            Lines.stroke((4f * w + 1f) * fout);
            Lines.line(e.x + ux * l0 + nx * off, e.y + uy * l0 + ny * off, e.x + ux * l1 + nx * off, e.y + uy * l1 + ny * off);
            Draw.color(Color.white);
            Draw.alpha(fout);
            Lines.stroke((1.6f * w + 0.4f) * fout);
            Lines.line(e.x + ux * l0 + nx * off, e.y + uy * l0 + ny * off, e.x + ux * l1 + nx * off, e.y + uy * l1 + ny * off);
        }
        Draw.reset();
    });

    static final Effect warpArriveFx = new Effect(30f, 400f, e -> {
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.effect + 0.08f);
        if (isBloomEnabled()) Drawf.light(e.x, e.y, 120f * fout, Color.white, 0.9f * fout);
        Draw.color(Color.white);
        Draw.alpha(0.6f * fout * fout);
        Fill.circle(e.x, e.y, 24f * fout);
        Draw.alpha(fout);
        Lines.stroke(3f * fout);
        Lines.circle(e.x, e.y, 70f * (float) Math.sqrt(fin));
        Draw.color(Color.black);
        Draw.alpha(0.7f * fout);
        Lines.stroke(1.6f * fout);
        Lines.circle(e.x, e.y, 52f * (float) Math.sqrt(fin));
        Draw.reset();
    });

    static final Effect swordDissolveSparkEffect = new Effect(40f, e -> {
        Draw.z(Layer.flyingUnit + 2.1f);
        Draw.color(coalGlowColor, Color.black, e.fout());
        Fill.circle(e.x, e.y, 3.5f * e.fout());
    });

    // ===== Sao 8 cánh =====
    static final float[] starVx = new float[8], starVy = new float[8];
    static final float[] STAR_RADII_UNIT = {30f, 3.2f, 10f, 3.2f, 10f, 3.2f, 10f, 3.2f};

    static float starRingAlpha(float seed) {
        float s1 = (float) Math.sin(Time.time * 0.045f + seed * 1.37f);
        if (s1 <= 0f) return 0f;
        return (float) Math.pow(s1, 0.8) * (0.6f + 0.4f * (float) Math.sin(Time.time * 0.021f + seed * 3.1f));
    }

    static void drawSwordStar(float cx, float cy, float angle, float scale, float alpha, boolean master, float seed, boolean noRing) {
        for (int k = 0; k < 8; k++) {
            float a = angle + 45f * k;
            starVx[k] = cx + Angles.trnsx(a, STAR_RADII_UNIT[k] * scale);
            starVy[k] = cy + Angles.trnsy(a, STAR_RADII_UNIT[k] * scale);
        }
        Color body = master ? masterPurpleColor : PerfCore.color("#0b0b0e");
        Color edge = master ? masterGlowColor : Color.white;

        Draw.color(body);
        Draw.alpha(alpha);
        for (int k = 0; k < 8; k++) {
            int n = (k + 1) % 8;
            Fill.tri(cx, cy, starVx[k], starVy[k], starVx[n], starVy[n]);
        }

        Draw.color(edge);
        Draw.alpha(alpha);
        Lines.stroke(Math.max(0.6f, 0.9f * scale));
        for (int k = 0; k < 8; k++) {
            int n = (k + 1) % 8;
            Lines.line(starVx[k], starVy[k], starVx[n], starVy[n]);
        }

        Draw.color(Color.white);
        Draw.alpha(0.85f * alpha);
        Lines.stroke(Math.max(0.5f, 0.7f * scale));
        Lines.line(cx, cy, cx + (starVx[0] - cx) * 0.9f, cy + (starVy[0] - cy) * 0.9f);
        Fill.circle(cx, cy, 1.5f * scale);

        if (!noRing) {
            float ra = starRingAlpha(seed) * alpha;
            if (ra > 0.02f) {
                float ringAng = angle + 55f;
                float ca = Angles.trnsx(ringAng, 1f), sa = Angles.trnsy(ringAng, 1f);
                float ra1 = 9f * scale, rb1 = 3.3f * scale;
                float px = 0f, py = 0f;
                Draw.color(master ? masterGlowColor : Color.white);
                Draw.alpha(ra);
                Lines.stroke(Math.max(0.5f, 0.8f * scale));
                for (int j = 0; j <= 20; j++) {
                    double th = (j / 20.0) * Math.PI * 2.0;
                    float lx = (float) Math.cos(th) * ra1, ly = (float) Math.sin(th) * rb1;
                    float wx = cx + ca * lx - sa * ly;
                    float wy = cy + sa * lx + ca * ly;
                    if (j > 0) Lines.line(px, py, wx, wy);
                    px = wx;
                    py = wy;
                }
            }
        }
    }

    // ===== Lấp lánh pixel =====
    static final float SPARKLE_PERIOD = 140f;
    /** {bắt đầu, thời lượng, loại, u (0..1 dọc lưỡi), v (lệch ngang)} */
    static final float[][] SPARKLE_EVENTS = {
        {0, 16, 0, 0.62f, 7}, {8, 10, 1, 0.25f, 4}, {18, 12, 3, 0.75f, 8}, {26, 16, 2, 0.50f, 5},
        {30, 22, 0, 0.40f, 8}, {48, 14, 2, 0.85f, 3}, {60, 16, 0, 0.70f, 6}, {70, 12, 1, 0.55f, 6},
        {82, 18, 3, 0.30f, 7}, {92, 20, 0, 0.50f, 8}, {106, 12, 2, 0.20f, 4}, {116, 16, 1, 0.80f, 5},
        {124, 16, 0, 0.35f, 6}
    };

    static void drawSwordSparkles(float cx, float cy, float angle, float scale, float alpha, float seed, boolean master) {
        if (alpha <= 0.02f) return;
        float T = (Time.time + seed * 17f) % SPARKLE_PERIOD;
        Color col = master ? masterGlowColor : Color.white;
        float px = Math.max(0.7f, 0.8f * scale);
        float sideA = angle + 90f;
        for (int i = 0; i < SPARKLE_EVENTS.length; i++) {
            float[] ev = SPARKLE_EVENTS[i];
            float k = (T - ev[0]) / ev[1];
            if (k < 0f || k > 1f) continue;
            float pulse = (float) Math.sin(Math.PI * Math.pow(k, 0.7));
            int type = (int) ev[2];
            for (int side = -1; side <= 1; side += 2) {
                float ex = cx + Angles.trnsx(angle, ev[3] * 30f * scale) + Angles.trnsx(sideA, ev[4] * side * scale);
                float ey = cy + Angles.trnsy(angle, ev[3] * 30f * scale) + Angles.trnsy(sideA, ev[4] * side * scale);
                Draw.color(col, Color.white, 0.6f);
                Draw.alpha(alpha);
                if (type == 0) {
                    float rot = angle + 45f * k, L = 5.5f * scale * pulse, w = 0.9f * scale * pulse;
                    for (int a = 0; a < 4; a++) {
                        float ra = rot + a * 90f, nx = Angles.trnsx(ra + 90f, w), ny = Angles.trnsy(ra + 90f, w);
                        Fill.tri(ex + nx, ey + ny, ex - nx, ey - ny, ex + Angles.trnsx(ra, L), ey + Angles.trnsy(ra, L));
                    }
                    Fill.rect(ex - px * 0.5f, ey - px * 0.5f, px, px);
                } else if (type == 1) {
                    float L = 9f * scale * Math.min(1f, k * 3f), w = 0.7f * scale * (1f - k);
                    float dx = Angles.trnsx(sideA, 1f), dy = Angles.trnsy(sideA, 1f);
                    float nx = Angles.trnsx(angle, w), ny = Angles.trnsy(angle, w);
                    float mx = ex + dx * L * 0.3f, my = ey + dy * L * 0.3f;
                    Fill.tri(mx - dx * L, my - dy * L, mx + nx, my + ny, mx - nx, my - ny);
                    Fill.tri(mx + dx * L, my + dy * L, mx + nx, my + ny, mx - nx, my - ny);
                } else if (type == 2) {
                    float L = 7f * scale * pulse, w = 1.3f * scale * (1f - k * 0.6f);
                    float dx = Angles.trnsx(angle, 1f), dy = Angles.trnsy(angle, 1f);
                    float nx = Angles.trnsx(sideA, w), ny = Angles.trnsy(sideA, w);
                    Fill.tri(ex - dx * L * 0.4f, ey - dy * L * 0.4f, ex + nx, ey + ny, ex + dx * L, ey + dy * L);
                    Fill.tri(ex - dx * L * 0.4f, ey - dy * L * 0.4f, ex - nx * 0.3f, ey - ny * 0.3f, ex + dx * L, ey + dy * L);
                } else {
                    float L = 2.2f * scale * pulse;
                    Lines.stroke(Math.max(0.5f, 0.7f * scale));
                    Lines.line(ex + Angles.trnsx(angle + 45f, L), ey + Angles.trnsy(angle + 45f, L), ex - Angles.trnsx(angle + 45f, L), ey - Angles.trnsy(angle + 45f, L));
                    Lines.line(ex + Angles.trnsx(angle + 135f, L), ey + Angles.trnsy(angle + 135f, L), ex - Angles.trnsx(angle + 135f, L), ey - Angles.trnsy(angle + 135f, L));
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            // Mathf.randomSeed(seed) một tham số trong JS = randomSeed(long) (nửa mở [0,1)); dùng bản 3 tham số cho chắc overload
            float ph = ((T / SPARKLE_PERIOD) * 3f + Mathf.randomSeed((long) (seed * 7 + i), 0f, 1f)) % 1f;
            if (ph > 0.35f) continue;
            float u = Mathf.randomSeed((long) (seed * 11 + i * 3), 0f, 1f) * 1.05f;
            float v = (Mathf.randomSeed((long) (seed * 13 + i * 5), 0f, 1f) - 0.5f) * 22f;
            float dx = cx + Angles.trnsx(angle, u * 30f * scale) + Angles.trnsx(sideA, v * scale);
            float dy = cy + Angles.trnsy(angle, u * 30f * scale) + Angles.trnsy(sideA, v * scale);
            Draw.color(Color.white);
            Draw.alpha(alpha * (1f - ph / 0.35f));
            Fill.rect(dx - px * 0.4f, dy - px * 0.4f, px * 0.8f, px * 0.8f);
        }
        Draw.color(Color.white);
        Draw.alpha(1f);
    }

    static final Effect swordHitSlashFx = new Effect(30f, 400f, e -> {
        float size = e.data instanceof Float ? (Float) e.data : 10f;
        float fout = e.fout();
        Draw.z(Layer.effect + 0.07f);
        Draw.color(Color.white);
        Draw.alpha(0.6f * fout * fout);
        Fill.circle(e.x, e.y, (6f + size * 0.6f) * fout);
        Draw.reset();
    });

    static void spawnSwordHitSlash(final float x, final float y, float dashAngle, float size) {
        final float len = 80f + size * 2f;
        float side = Mathf.chance(0.5) ? 1f : -1f;
        float a1 = dashAngle + 55f * side;
        final float a2 = dashAngle - 40f * side;

        swordHitSlashFx.at(x, y, 0f, (Object) Float.valueOf(size));
        swordWindSlashFx.at(x - Angles.trnsx(a1, len / 2f), y - Angles.trnsy(a1, len / 2f), 0f,
            (Object) new LineData(x + Angles.trnsx(a1, len / 2f), y + Angles.trnsy(a1, len / 2f), 7.5f, true, true));

        Time.run(3f, () -> {
            float l2 = len * 0.75f;
            swordWindSlashFx.at(x - Angles.trnsx(a2, l2 / 2f), y - Angles.trnsy(a2, l2 / 2f), 0f,
                (Object) new LineData(x + Angles.trnsx(a2, l2 / 2f), y + Angles.trnsy(a2, l2 / 2f), 5f, true, false));
        });
    }

    // ===== Đạn của master =====
    static final BasicBulletType masterHomingSwordBullet = new BasicBulletType(18f, 1500f) {{
        lifetime = 3600f; width = 14f; height = 40f; shrinkY = 0f;
        pierce = true; pierceBuilding = true; pierceCap = 6;
        collidesAir = true; collidesGround = true; collidesTiles = true;
        homingPower = 0.15f; homingRange = 1000f; homingDelay = 2f;
        frontColor = Color.white; backColor = masterGlowColor;
    }
        @Override
        public void update(Bullet b) {
            super.update(b);
            if (PerfCore.level() >= 2 || !b.timer.get(1, 2f)) return;
            float ang = b.rotation();
            float seg = b.vel.len() * 2f + 6f;
            swordWindSlashFx.at(b.x - Angles.trnsx(ang, seg), b.y - Angles.trnsy(ang, seg), 0f,
                (Object) new LineData(b.x, b.y, 5.5f, false, false));
        }

        @Override
        public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other instanceof Healthc) {
                Healthc h = (Healthc) other;
                h.health(h.health() - h.maxHealth() * 0.10f);
                if (h.health() <= 0f) h.kill();
            }
        }

        @Override
        public void draw(Bullet b) {
            float angle = b.rotation();
            Draw.z(Layer.bullet);
            drawSwordStar(b.x, b.y, angle, 1.3f, 1f, true, b.id, false);
            Draw.reset();

            float trailLen = 45f;
            float backX = b.x - Angles.trnsx(angle, trailLen), backY = b.y - Angles.trnsy(angle, trailLen);
            Draw.z(Layer.bullet + 0.01f);
            Draw.color(Color.black); Lines.stroke(8f); Lines.line(b.x, b.y, backX, backY);
            Draw.color(Color.lightGray); Lines.stroke(4f); Lines.line(b.x, b.y, backX, backY);
            Draw.color(Color.white); Lines.stroke(1.8f); Lines.line(b.x, b.y, backX, backY);
            Draw.reset();
        }
    };

    static final BasicBulletType masterBurstAuraSwordBullet = new BasicBulletType(10f, 900f) {{
        lifetime = 12f; width = 10f; height = 30f; shrinkY = 0f;
        pierce = false; pierceBuilding = false;
        collidesAir = true; collidesGround = true; collidesTiles = true;
        homingPower = 0f; homingRange = 0f;
        frontColor = Color.white; backColor = masterGlowColor;
    }
        @Override
        public void update(Bullet b) {
            super.update(b);
            if (PerfCore.level() >= 1 || !b.timer.get(1, 2f)) return;
            float ang = b.rotation();
            float seg = b.vel.len() * 2f + 4f;
            swordWindSlashFx.at(b.x - Angles.trnsx(ang, seg), b.y - Angles.trnsy(ang, seg), 0f,
                (Object) new LineData(b.x, b.y, 3.2f, false, false));
        }

        @Override
        public void draw(Bullet b) {
            float angle = b.rotation();
            Draw.z(Layer.bullet);
            drawSwordStar(b.x, b.y, angle, 0.95f, 1f, true, b.id, false);
            Draw.reset();

            float trailLen = 20f;
            float backX = b.x - Angles.trnsx(angle, trailLen), backY = b.y - Angles.trnsy(angle, trailLen);
            Draw.z(Layer.bullet + 0.01f);
            Draw.color(Color.black); Lines.stroke(4f); Lines.line(b.x, b.y, backX, backY);
            Draw.color(Color.lightGray); Lines.stroke(2f); Lines.line(b.x, b.y, backX, backY);
            Draw.color(Color.white); Lines.stroke(1f); Lines.line(b.x, b.y, backX, backY);
            Draw.reset();
        }
    };

    // ===== Quạt chém (fan slash) =====
    static final float SWORD_FAN_RADIUS = 120f;
    static final float SWORD_FAN_CRACK_WIDTH = 46f;
    static final float SWORD_FAN_SWEEP_TICKS = 30f;
    static final float SWORD_FAN_BURST_AT = 60f;
    static final float SWORD_FAN_CRACK_LIFE = 75f;
    static final int FAN_CRACK_MAX_POINTS = 30;

    static final float[] fcOx = new float[FAN_CRACK_MAX_POINTS + 1], fcOy = new float[FAN_CRACK_MAX_POINTS + 1];
    static final float[] fcIx = new float[FAN_CRACK_MAX_POINTS + 1], fcIy = new float[FAN_CRACK_MAX_POINTS + 1];
    static final float[] fcRx = new float[FAN_CRACK_MAX_POINTS + 1], fcRy = new float[FAN_CRACK_MAX_POINTS + 1];
    static final float[] fcW = new float[FAN_CRACK_MAX_POINTS + 1];
    static final float[] fcPt = new float[3];

    static final class FanData {
        final float cx, cy, a0, sweep, R, W, sweepTicks, burstAt;
        FanData(float cx, float cy, float a0, float sweep, float R, float W, float sweepTicks, float burstAt) {
            this.cx = cx; this.cy = cy; this.a0 = a0; this.sweep = sweep; this.R = R; this.W = W; this.sweepTicks = sweepTicks; this.burstAt = burstAt;
        }
    }

    static float[] fanCrackPoint(float u, float v, int n) {
        float idx = u * n;
        int i = Math.min(n - 1, Math.max(0, (int) Math.floor(idx)));
        float f = idx - i;
        float ox = fcOx[i] + (fcOx[i + 1] - fcOx[i]) * f;
        float oy = fcOy[i] + (fcOy[i + 1] - fcOy[i]) * f;
        float ix = fcIx[i] + (fcIx[i + 1] - fcIx[i]) * f;
        float iy = fcIy[i] + (fcIy[i + 1] - fcIy[i]) * f;
        fcPt[0] = ix + (ox - ix) * v;
        fcPt[1] = iy + (oy - iy) * v;
        fcPt[2] = fcW[i] + (fcW[i + 1] - fcW[i]) * f;
        return fcPt;
    }

    static final BasicBulletType swordStarBullet = new BasicBulletType(5.5f, 140f) {{
        lifetime = 70f; drag = 0.045f; width = 8f; height = 8f; hitSize = 7f;
        collidesAir = true; collidesGround = true; collidesTiles = false;
        pierce = true; pierceCap = 2;
        hitEffect = Fx.none; despawnEffect = Fx.none; shootEffect = Fx.none; smokeEffect = Fx.none;
        frontColor = Color.white; backColor = Color.black;
    }
        @Override
        public void draw(Bullet b) {
            float f = b.fout();
            float ang = b.rotation();
            float scl = 0.28f + 0.62f * f;
            float a = (float) Math.pow(f, 0.5);
            float backLen = 22f * f;
            Draw.z(Layer.bullet + 0.05f);
            Draw.color(Color.white);
            Draw.alpha(0.55f * a);
            Lines.stroke(2.2f * f + 0.3f);
            Lines.line(b.x, b.y, b.x - Angles.trnsx(ang, backLen), b.y - Angles.trnsy(ang, backLen));
            drawSwordStar(b.x, b.y, ang, scl, a, false, b.id, true);
            Draw.reset();
        }
    };

    static final Effect swordFanCrackFx = new Effect(SWORD_FAN_CRACK_LIFE, 700f, e -> {
        if (!(e.data instanceof FanData)) return;
        FanData d = (FanData) e.data;
        float t = e.time;
        int lod = PerfCore.level();
        int n = lod >= 2 ? 18 : FAN_CRACK_MAX_POINTS;
        int seed = e.id * 211;
        float k = t > d.burstAt ? clamp01((t - d.burstAt) / (SWORD_FAN_CRACK_LIFE - d.burstAt)) : 0f;
        float closeF = (float) Math.pow(1f - k, 1.8);
        if (closeF <= 0.01f) return;
        float pre = clamp01((t - (d.burstAt - 18f)) / 18f);

        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            float ang = d.a0 + d.sweep * u;
            float cs = Angles.trnsx(ang, 1f), sn = Angles.trnsy(ang, 1f);
            float sh = (float) Math.pow(Math.sin(Math.PI * u), 0.8);
            float open = clamp01((t - u * d.sweepTicks) / 6f);
            open = open * open * (3f - 2f * open);
            float w = d.W * sh * open * closeF * (1f + 0.12f * pre * (float) Math.sin(t * 1.7f + i));
            float jag = clamp01(w / 3f);
            float ro = d.R + Mathf.randomSeed(seed + i * 31, -1f, 1f) * 2f * jag;
            float ri = ro - w + Mathf.randomSeed(seed + i * 53 + 7, -1f, 1f) * 3.5f * jag;
            if (w < 0.05f) ri = ro;
            fcRx[i] = cs; fcRy[i] = sn;
            fcOx[i] = d.cx + cs * ro; fcOy[i] = d.cy + sn * ro;
            fcIx[i] = d.cx + cs * ri; fcIy[i] = d.cy + sn * ri;
            fcW[i] = ro - ri;
        }

        Color black = PerfCore.color("#02010a");
        Draw.z(Layer.effect + 0.02f);

        Draw.color(PerfCore.color("#000008"));
        Draw.alpha(0.5f * closeF);
        for (int i = 0; i < n; i++) {
            if (fcW[i] <= 0.1f && fcW[i + 1] <= 0.1f) continue;
            Fill.quad(
                fcOx[i] + fcRx[i] * 2.2f, fcOy[i] + fcRy[i] * 2.2f,
                fcOx[i + 1] + fcRx[i + 1] * 2.2f, fcOy[i + 1] + fcRy[i + 1] * 2.2f,
                fcIx[i + 1] - fcRx[i + 1] * 3.2f, fcIy[i + 1] - fcRy[i + 1] * 3.2f,
                fcIx[i] - fcRx[i] * 3.2f, fcIy[i] - fcRy[i] * 3.2f);
        }

        Draw.color(black);
        Draw.alpha(0.97f);
        for (int i = 0; i < n; i++) {
            if (fcW[i] <= 0.1f && fcW[i + 1] <= 0.1f) continue;
            Fill.quad(fcOx[i], fcOy[i], fcOx[i + 1], fcOy[i + 1], fcIx[i + 1], fcIy[i + 1], fcIx[i], fcIy[i]);
        }

        Draw.blend(Blending.additive);

        for (int i = 3; i < n - 2; i += 5) {
            if (fcW[i] < 6f) continue;
            float mx = (fcOx[i] + fcIx[i]) / 2f, my = (fcOy[i] + fcIy[i]) / 2f;
            Draw.color(i % 10 == 3 ? PerfCore.color("#5b21b6") : PerfCore.color("#1d4ed8"));
            Draw.alpha(0.3f * closeF);
            Fill.circle(mx, my, fcW[i] * 0.55f);
        }

        int starCount = lod >= 2 ? 8 : (lod == 1 ? 16 : 30);
        for (int s = 0; s < starCount; s++) {
            float u = Mathf.randomSeed(seed + s * 13 + 1, 0.04f, 0.96f);
            float v = Mathf.randomSeed(seed + s * 13 + 2, 0.08f, 0.92f);
            float[] pt = fanCrackPoint(u, v, n);
            if (pt[2] < 2.5f) continue;
            float phase = Mathf.randomSeed(seed + s * 13 + 3, 0f, 6.28f);
            float tw = 0.55f + 0.45f * (float) Math.sin(t * (0.25f + (s % 5) * 0.07f) + phase);
            int kind = s % 4;
            Draw.color(kind == 0 ? PerfCore.color("#ffffff") : (kind == 1 ? PerfCore.color("#aee5ff") : (kind == 2 ? PerfCore.color("#ffd1f5") : PerfCore.color("#fff4b0"))));
            Draw.alpha(tw * closeF);
            Fill.circle(pt[0], pt[1], (0.55f + (s % 3) * 0.35f) * (0.6f + 0.4f * tw));
            if (s % 6 == 0 && lod == 0) {
                Lines.stroke(0.7f);
                Lines.line(pt[0] - 3f * tw, pt[1], pt[0] + 3f * tw, pt[1]);
                Lines.line(pt[0], pt[1] - 3f * tw, pt[0], pt[1] + 3f * tw);
            }
        }

        if (lod < 2) {
            for (int m = 0; m < 4; m++) {
                float ph = (t * 0.032f + Mathf.randomSeed(seed + m * 17 + 600, 0f, 1f)) % 1f;
                float u0 = Mathf.randomSeed(seed + m * 17 + 601, 0.15f, 0.7f);
                float hu = u0 + 0.12f * ph, hv = 0.93f - 0.84f * ph;
                float tp = Math.max(0f, ph - 0.18f);
                float tu = u0 + 0.12f * tp, tv = 0.93f - 0.84f * tp;
                float[] p1 = fanCrackPoint(Math.min(0.98f, hu), hv, n);
                float hx = p1[0], hy = p1[1], hw = p1[2];
                float[] p2 = fanCrackPoint(Math.min(0.98f, tu), tv, n);
                float tx = p2[0], ty = p2[1];
                if (hw < 4f) continue;
                Draw.color(PerfCore.color("#ffffff"));
                Draw.alpha(0.9f * closeF * (1f - ph * 0.5f));
                Lines.stroke(1.1f);
                Lines.line(hx, hy, tx, ty);
                Fill.circle(hx, hy, 0.9f);
            }
        }

        if (t >= d.burstAt && t < d.burstAt + 8f) {
            float fl = 1f - (t - d.burstAt) / 8f;
            Draw.color(Color.white);
            Draw.alpha(0.35f * fl);
            for (int i = 0; i < n; i++) {
                if (fcW[i] <= 0.1f && fcW[i + 1] <= 0.1f) continue;
                Fill.quad(fcOx[i], fcOy[i], fcOx[i + 1], fcOy[i + 1], fcIx[i + 1], fcIy[i + 1], fcIx[i], fcIy[i]);
            }
        }

        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
                Draw.color(PerfCore.color("#a855f7"));
                Draw.alpha((0.28f + 0.3f * pre) * closeF);
                Lines.stroke(4f * closeF + 0.6f + 2f * pre);
            } else {
                Draw.color(PerfCore.color("#ffffff"), PerfCore.color("#8fd8ff"), clamp01(t / d.burstAt));
                Draw.alpha(Math.min(1f, 0.95f * closeF * 1.6f));
                Lines.stroke(1.3f * closeF + 0.35f);
            }
            for (int i = 0; i < n; i++) {
                if (fcW[i] <= 0.15f && fcW[i + 1] <= 0.15f) continue;
                Lines.line(fcOx[i], fcOy[i], fcOx[i + 1], fcOy[i + 1]);
                Lines.line(fcIx[i], fcIy[i], fcIx[i + 1], fcIy[i + 1]);
            }
        }

        if (lod == 0) {
            for (int b = 0; b < 6; b++) {
                int bi = 3 + (int) Math.floor(Mathf.randomSeed(seed + b * 41 + 9, 0f, n - 6.01f));
                if (fcW[bi] < 8f) continue;
                boolean outer = Mathf.randomSeed(seed + b * 41 + 10, -1f, 1f) >= 0f;
                float bx = outer ? fcOx[bi] : fcIx[bi];
                float by = outer ? fcOy[bi] : fcIy[bi];
                float dir = Mathf.angle(fcRx[bi], fcRy[bi]) + (outer ? 0f : 180f) + Mathf.randomSeed(seed + b * 41 + 11, -35f, 35f);
                float grow = clamp01((t - ((float) bi / n) * d.sweepTicks - 4f) / 10f);
                if (grow <= 0f) continue;
                for (int sg = 0; sg < 3; sg++) {
                    if (grow < sg / 3f) break;
                    float segLen = Mathf.randomSeed(seed + b * 41 + sg * 3 + 12, 7f, 14f) * clamp01(grow * 3f - sg + 0.2f);
                    float ex = bx + Angles.trnsx(dir, segLen), ey = by + Angles.trnsy(dir, segLen);
                    Draw.color(PerfCore.color("#d8b4fe"), PerfCore.color("#ffffff"), 1f - sg / 3f);
                    Draw.alpha(0.85f * closeF);
                    Lines.stroke(1.4f - sg * 0.4f);
                    Lines.line(bx, by, ex, ey);
                    bx = ex; by = ey;
                    dir += Mathf.randomSeed(seed + b * 41 + sg * 3 + 13, -30f, 30f);
                }
            }
        }

        Draw.blend();
        Draw.reset();
    });

    static void startSwordFan(final Unit unit, DroneData data, float dashAngle, final boolean isMK2B) {
        final float cx = unit.x, cy = unit.y;
        final float a0 = dashAngle + 80f;
        final float sweep = -160f;

        data.fanX = cx; data.fanY = cy; data.fanA0 = a0; data.fanSweep = sweep; data.fanTimer = 0f;
        data.fanHitU.clear(); data.fanHitB.clear();

        swordFanCrackFx.at(cx, cy, 0f, (Object) new FanData(cx, cy, a0, sweep, SWORD_FAN_RADIUS, SWORD_FAN_CRACK_WIDTH, SWORD_FAN_SWEEP_TICKS, SWORD_FAN_BURST_AT));

        final Team team = unit.team;
        Time.run(SWORD_FAN_BURST_AT, () -> {
            for (int i = 0; i < 12; i++) {
                float u = Mathf.random(0.06f, 0.94f);
                float sh = (float) Math.pow(Math.sin(Math.PI * u), 0.8);
                float r = SWORD_FAN_RADIUS - SWORD_FAN_CRACK_WIDTH * sh * Mathf.random(0.1f, 0.9f);
                float ang = a0 + sweep * u;
                float bx = cx + Angles.trnsx(ang, r), by = cy + Angles.trnsy(ang, r);
                Bullet shot = swordStarBullet.create(unit, team, bx, by, Mathf.random(360f));
                if (shot != null && isMK2B) shot.damage *= 2f;
            }
        });
    }

    static void swordFanDamage(final Unit unit, final DroneData data, final float prog) {
        if (Vars.net.client()) return;
        float aNow = data.fanA0 + data.fanSweep * prog;
        final float cx = data.fanX, cy = data.fanY;
        final float reach = SWORD_FAN_RADIUS;

        Units.nearbyEnemies(unit.team, cx, cy, reach + 30f, enemy -> {
            if (enemy == null || !enemy.isValid() || enemy.dead || data.fanHitU.contains(enemy.id)) return;
            if (!enemy.within(cx, cy, reach + enemy.hitSize / 2f)) return;
            float ang = Angles.angle(cx, cy, enemy.x, enemy.y);
            float rel = ((ang - data.fanA0) % 360f + 540f) % 360f - 180f;
            float frac = rel / data.fanSweep;
            if (frac < -0.04f || frac > prog + 0.04f) return;
            data.fanHitU.add(enemy.id);
            enemy.damagePierce(enemy.maxHealth * 0.10f);
        });

        for (float r = 0.3f; r <= 1.0f; r += 0.35f) {
            float bx = cx + Angles.trnsx(aNow, reach * r), by = cy + Angles.trnsy(aNow, reach * r);
            Building bld = Vars.world.buildWorld(bx, by);
            if (bld != null && bld.team != unit.team && !data.fanHitB.contains(bld.pos())) {
                data.fanHitB.add(bld.pos());
                bld.damage(bld.maxHealth * 0.10f);
            }
        }
    }

    // ===== Vẽ kiếm trên Sword Drone =====
    static final class SwordRender {
        boolean isCharging, isDashing, isFan;
        float chargeProgress, dashProgress, alpha, seed, fanAngle, fanDist = 48f, fanScale = 2.4f;
        SwordRender(float seed, boolean charging, boolean dashing, float chargeProgress, float dashProgress, float alpha) {
            this.seed = seed; this.isCharging = charging; this.isDashing = dashing; this.chargeProgress = chargeProgress; this.dashProgress = dashProgress; this.alpha = alpha;
        }
        /** Kiếm xoay quanh (quạt chém / brawler); fanDist, fanScale mặc định 48 / 2.4 như JS khi không truyền. */
        static SwordRender fan(float seed, float fanAngle, float alpha, float fanDist, float fanScale) {
            SwordRender r = new SwordRender(seed, false, false, 0f, 0f, alpha);
            r.isFan = true; r.fanAngle = fanAngle; r.fanDist = fanDist; r.fanScale = fanScale;
            return r;
        }
        static SwordRender fan(float seed, float fanAngle) { return fan(seed, fanAngle, 1f, 48f, 2.4f); }
    }

    static final Effect renderSwordEffect = new Effect(2f, e -> {
        if (!(e.data instanceof SwordRender)) return;
        SwordRender data = (SwordRender) e.data;
        float rot = e.rotation, rx = e.x, ry = e.y;
        float alpha = data.alpha, seed = data.seed;
        float swordScale = 1f;

        Draw.z(Layer.flyingUnit + 2f);

        float swordX = rx, swordY = ry, swordAngle = rot;

        if (data.isCharging) {
            float sideAngle = rot - 90f;
            swordX = rx + Angles.trnsx(rot, 4f) + Angles.trnsx(sideAngle, 12f);
            swordY = ry + Angles.trnsy(rot, 4f) + Angles.trnsy(sideAngle, 12f);
            swordAngle = rot + 20f;
            float circleRadius = 20f * (1f - data.chargeProgress);
            Draw.color(coalColor, alpha);
            Lines.stroke(3f * alpha);
            Lines.circle(swordX, swordY, circleRadius);
            Draw.color(Color.black, alpha * 0.6f);
            Fill.circle(swordX, swordY, circleRadius * 0.7f);
        } else if (data.isFan) {
            float fDist = data.fanDist;
            swordScale = data.fanScale;
            swordAngle = data.fanAngle;
            swordX = rx + Angles.trnsx(swordAngle, fDist);
            swordY = ry + Angles.trnsy(swordAngle, fDist);
        } else if (data.isDashing) {
            float t = Math.min(1f, Math.max(0f, data.dashProgress));
            swordAngle = rot - Mathf.lerp(0f, 140f, t);
            swordX = rx; swordY = ry;
            int ghostCount = 5;
            for (int g = 1; g <= ghostCount; g++) {
                float ghostT = Math.max(0f, t - g * 0.05f);
                float gAngle = rot - Mathf.lerp(0f, 140f, ghostT);
                float ghostAlpha = alpha * (1f - (g / (float) (ghostCount + 1))) * 0.6f;
                drawSwordStar(rx, ry, gAngle, 1f, ghostAlpha * 0.7f, false, seed, true);
            }
        } else {
            float sideAngle = rot + 90f;
            swordX = rx + Angles.trnsx(sideAngle, 12f);
            swordY = ry + Angles.trnsy(sideAngle, 12f);
            swordAngle = rot - 140f;
        }

        if (alpha <= 0.001f) return;

        if (isBloomEnabled()) Drawf.light(swordX, swordY, 50f * alpha, Color.white, 0.9f * alpha);

        drawSwordStar(swordX, swordY, swordAngle, swordScale, alpha, false, seed, false);
        drawSwordSparkles(swordX, swordY, swordAngle, swordScale, alpha, seed, false);
        Draw.reset();
    });

    // ===== Đạn drone thường =====
    static final BasicBulletType droneBulletNormal = new BasicBulletType(23.5f, 60f) {{
        lifetime = 35f; width = 5f; height = 29f;
        frontColor = Color.valueOf("ffe265"); backColor = Color.valueOf("d29600");
    }};

    static final BasicBulletType droneBulletMK2B = new BasicBulletType(26f, 120f) {{
        lifetime = 60f; width = 6f; height = 32f;
        frontColor = Color.valueOf("ff5252"); backColor = Color.valueOf("ff1744");
    }};

    // ===== Dạng BRAWLER (cận chiến) của Cargo Sword Drone =====
    static final float BRAWLER_CHANCE = 0.5f;
    static final float BRAWLER_HP_MULT = 6f;
    static final float BRAWLER_SPEED_MULT = 1.2f;
    static final float BRAWLER_SLASH_SCALE = 6f;
    static final float BRAWLER_SLASH_R = 56f * BRAWLER_SLASH_SCALE;
    static final float BRAWLER_SLASH_W = 30f * BRAWLER_SLASH_SCALE;
    static final float BRAWLER_SWORD_SCALE = 8f;

    static final float BRAWLER_ENGAGE = BRAWLER_SLASH_R - BRAWLER_SLASH_W * 0.5f;
    static final float BRAWLER_SWEEP_DEG = 200f;
    static final float BRAWLER_WINDUP_TICKS = 14f;
    static final float BRAWLER_SWEEP_TICKS = 14f;
    static final float BRAWLER_REST_TICKS = 30f;
    static final float BRAWLER_COOLDOWN_TICKS = 15f;
    static final float BRAWLER_FX_LIFE = 34f;
    static final float BRAWLER_SLASH_DMG = 500f;
    static final float BRAWLER_SLASH_PCT = 0.05f;
    static final String BRAWLER_COL_MAIN = "#28d0ff", BRAWLER_COL_DARK = "#0a1c4a", BRAWLER_COL_EDGE = "#02050f",
        BRAWLER_COL_LIGHT = "#c8f8ff", BRAWLER_COL_MAGENTA = "#ff4df2", BRAWLER_COL_CYAN = "#00ffff", BRAWLER_COL_VOID = "#000814";

    static final int BS_MAXN = 64;
    static final float[] bsCs = new float[BS_MAXN + 1], bsSn = new float[BS_MAXN + 1], bsWp = new float[BS_MAXN + 1], bsJg = new float[BS_MAXN + 1];
    static final float[] bsCs2 = new float[BS_MAXN + 1], bsSn2 = new float[BS_MAXN + 1], bsWp2 = new float[BS_MAXN + 1];

    static final class BShape {
        final float head, tail, W, fadeT;
        BShape(float head, float tail, float W, float fadeT) { this.head = head; this.tail = tail; this.W = W; this.fadeT = fadeT; }
    }
    /** Dữ liệu vệt chém: góc bắt đầu + chiều quét (+1/-1). */
    static final class SlashData {
        final float a0, side;
        SlashData(float a0, float side) { this.a0 = a0; this.side = side; }
    }

    static float brawlerEase(float p) { return 1f - (float) Math.pow(1f - p, 2.2); }

    static BShape brawlerShapeAt(float t) {
        float pe = brawlerEase(clamp01(t / BRAWLER_SWEEP_TICKS));
        float fadeStart = BRAWLER_SWEEP_TICKS * 0.6f;
        float fadeT = clamp01((t - fadeStart) / (BRAWLER_FX_LIFE - fadeStart));
        return new BShape(BRAWLER_SWEEP_DEG * pe, BRAWLER_SWEEP_DEG * clamp01((t - 3f) / 30f) * 0.8f, BRAWLER_SLASH_W * (1f - 0.45f * fadeT), fadeT);
    }

    static float brawlerProfile(float u) {
        float w = (float) Math.pow(u, 1.35);
        if (u > 0.955f) w *= (float) Math.sqrt(Math.max(0f, 1f - Math.pow((u - 0.955f) / 0.045f, 2.0)));
        return w;
    }

    static void bsQuad(float cx, float cy, int i, float ro0, float ro1, float ri0, float ri1) {
        Fill.quad(cx + bsCs[i] * ro0, cy + bsSn[i] * ro0, cx + bsCs[i + 1] * ro1, cy + bsSn[i + 1] * ro1,
                  cx + bsCs[i + 1] * ri1, cy + bsSn[i + 1] * ri1, cx + bsCs[i] * ri0, cy + bsSn[i] * ri0);
    }

    static final Effect brawlerSlashFx = new Effect(BRAWLER_FX_LIFE, 1100f, e -> {
        if (!(e.data instanceof SlashData)) return;
        SlashData d = (SlashData) e.data;
        float t = e.time;
        BShape sh = brawlerShapeAt(t);
        float alpha = (float) Math.pow(1f - sh.fadeT, 1.3);
        if (alpha <= 0.02f) return;

        int lod = PerfCore.level();
        int n = lod >= 2 ? 24 : 56;
        float side = d.side;
        float cx = e.x, cy = e.y;
        float R = BRAWLER_SLASH_R, W = sh.W, S = BRAWLER_SLASH_SCALE;
        int seed = e.id * 53;
        Color main = PerfCore.color(BRAWLER_COL_MAIN), dark = PerfCore.color(BRAWLER_COL_DARK);
        Color edge = PerfCore.color(BRAWLER_COL_EDGE), light = PerfCore.color(BRAWLER_COL_LIGHT);

        if (t < 16f) {
            float k = t / 16f;
            Draw.z(Layer.effect + 0.01f);
            Draw.color(main);
            Draw.alpha(0.55f * (1f - k));
            Lines.stroke(7f * (1f - k) + 0.5f);
            Lines.circle(cx, cy, R * 0.95f * brawlerEase(k));
            Draw.color(Color.white);
            Draw.alpha(0.5f * (1f - k));
            Lines.stroke(2f * (1f - k) + 0.3f);
            Lines.circle(cx, cy, R * 0.85f * brawlerEase(k));
        }

        float span = sh.head - sh.tail;
        if (span < 0.5f) { Draw.reset(); return; }

        Draw.z(Layer.effect + 0.005f);
        Draw.color(PerfCore.color(BRAWLER_COL_VOID));
        Draw.alpha(0.34f * alpha);
        int nd = lod >= 2 ? 10 : 22;
        for (int i = 0; i < nd; i++) {
            float a1 = d.a0 + side * (sh.head * i / nd), a2 = d.a0 + side * (sh.head * (i + 1) / nd);
            Fill.tri(cx, cy, cx + Angles.trnsx(a1, R), cy + Angles.trnsy(a1, R), cx + Angles.trnsx(a2, R), cy + Angles.trnsy(a2, R));
        }

        int flick = (int) Math.floor(t * 0.5f);
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            float ang = d.a0 + side * (sh.tail + span * u);
            bsCs[i] = Angles.trnsx(ang, 1f);
            bsSn[i] = Angles.trnsy(ang, 1f);
            bsWp[i] = W * brawlerProfile(u);
            bsJg[i] = (1f - u) * W * 0.32f * Mathf.randomSeed(seed + i * 7 + flick, 0.25f, 1f);
        }

        Draw.z(Layer.effect + 0.09f);

        Draw.color(edge, dark, 0.5f);
        Draw.alpha(0.5f * alpha);
        for (int i = 0; i < n; i++) {
            if (bsJg[i] < 1f && bsJg[i + 1] < 1f) continue;
            bsQuad(cx, cy, i, R + bsJg[i], R + bsJg[i + 1], R - bsWp[i] * 0.3f, R - bsWp[i + 1] * 0.3f);
        }

        float M = 8f;
        Draw.color(edge);
        Draw.alpha(0.95f * alpha);
        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.05f && bsWp[i + 1] < 0.05f) continue;
            bsQuad(cx, cy, i, R + M, R + M, R - bsWp[i] - M, R - bsWp[i + 1] - M);
        }

        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.05f && bsWp[i + 1] < 0.05f) continue;
            float f = (i + 0.5f) / n;
            Draw.color(dark, main, (float) Math.pow(f, 0.7));
            Draw.alpha(alpha);
            bsQuad(cx, cy, i, R, R, R - bsWp[i], R - bsWp[i + 1]);
        }

        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.5f && bsWp[i + 1] < 0.5f) continue;
            float f = (i + 0.5f) / n;
            Draw.color(main, light, f);
            Draw.alpha(0.9f * alpha * (0.35f + 0.65f * f));
            bsQuad(cx, cy, i, R, R, R - bsWp[i] * 0.55f, R - bsWp[i + 1] * 0.55f);
        }

        Draw.color(Color.white);
        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.5f && bsWp[i + 1] < 0.5f) continue;
            float f = (i + 0.5f) / n;
            Draw.alpha(0.95f * alpha * (0.2f + 0.8f * f));
            bsQuad(cx, cy, i, R, R, R - bsWp[i] * 0.16f, R - bsWp[i + 1] * 0.16f);
        }

        Draw.color(Color.white);
        Lines.stroke(2.4f);
        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.5f && bsWp[i + 1] < 0.5f) continue;
            Draw.alpha(alpha * (i + 0.5f) / n);
            Lines.line(cx + bsCs[i] * R, cy + bsSn[i] * R, cx + bsCs[i + 1] * R, cy + bsSn[i + 1] * R);
        }

        BShape sh2 = brawlerShapeAt(Math.max(0f, t - 3.5f));
        float tail2 = sh2.tail + (sh2.head - sh2.tail) * 0.35f;
        float span2 = sh2.head - tail2;
        if (span2 > 1f) {
            int n2 = n / 2;
            float Rc = R - W * 0.62f;
            for (int i = 0; i <= n2; i++) {
                float u = i / (float) n2;
                float ang = d.a0 + side * (tail2 + span2 * u);
                bsCs2[i] = Angles.trnsx(ang, 1f);
                bsSn2[i] = Angles.trnsy(ang, 1f);
                bsWp2[i] = W * 0.26f * brawlerProfile(u);
            }
            Draw.color(light);
            for (int i = 0; i < n2; i++) {
                if (bsWp2[i] < 0.3f && bsWp2[i + 1] < 0.3f) continue;
                Draw.alpha(0.6f * alpha * (0.3f + 0.7f * (i + 0.5f) / n2));
                Fill.quad(cx + bsCs2[i] * Rc, cy + bsSn2[i] * Rc, cx + bsCs2[i + 1] * Rc, cy + bsSn2[i + 1] * Rc,
                          cx + bsCs2[i + 1] * (Rc - bsWp2[i + 1]), cy + bsSn2[i + 1] * (Rc - bsWp2[i + 1]),
                          cx + bsCs2[i] * (Rc - bsWp2[i]), cy + bsSn2[i] * (Rc - bsWp2[i]));
            }
        }

        Draw.blend(Blending.additive);
        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.5f && bsWp[i + 1] < 0.5f) continue;
            float f = (i + 0.5f) / n;
            Draw.color(main);
            Draw.alpha(0.22f * alpha * f);
            bsQuad(cx, cy, i, R + 14f, R + 14f, R - bsWp[i] - 14f, R - bsWp[i + 1] - 14f);
        }

        Color magenta = PerfCore.color(BRAWLER_COL_MAGENTA), cyanPure = PerfCore.color(BRAWLER_COL_CYAN);
        for (int i = 0; i < n; i++) {
            if (bsWp[i] < 0.5f && bsWp[i + 1] < 0.5f) continue;
            float f = (i + 0.5f) / n;
            Draw.color(magenta);
            Draw.alpha(0.5f * alpha * f);
            bsQuad(cx, cy, i, R + 14f, R + 14f, R + 8f, R + 8f);
            Draw.color(cyanPure);
            Draw.alpha(0.4f * alpha * f);
            bsQuad(cx, cy, i, R + 8f, R + 8f, R + 3f, R + 3f);
        }

        int shards = lod >= 2 ? 6 : 20;
        for (int j = 0; j < shards; j++) {
            float angDeg = Mathf.randomSeed(seed + j * 11 + 31, 0f, 1f) * BRAWLER_SWEEP_DEG * 0.98f;
            if (angDeg > sh.head || angDeg < sh.tail) continue;
            float x = Math.min(angDeg / BRAWLER_SWEEP_DEG, 0.999f);
            float age = t - (1f - (float) Math.pow(1f - x, 1.0 / 2.2)) * BRAWLER_SWEEP_TICKS;
            if (age < 0f || age > 20f) continue;
            float k = age / 20f;
            float ang = d.a0 + side * angDeg;
            float r0 = R - W * Mathf.randomSeed(seed + j * 11 + 33, 0f, 0.9f);
            float rr = r0 + age * (5f + Mathf.randomSeed(seed + j * 11 + 32, 0f, 7f));
            float ls = 26f * (1f - k) + 5f;
            Draw.color(light, main, k);
            Draw.alpha(0.95f * (1f - k) * alpha);
            Lines.stroke(2.6f * (1f - k) + 0.5f);
            Lines.line(cx + Angles.trnsx(ang, rr), cy + Angles.trnsy(ang, rr), cx + Angles.trnsx(ang, rr + ls), cy + Angles.trnsy(ang, rr + ls));
        }

        int ih = (int) Math.floor(n * 0.97);
        float hr = R - bsWp[ih] * 0.5f;
        float hx = cx + bsCs[ih] * hr, hy = cy + bsSn[ih] * hr;
        float hk = clamp01(1f - t / (BRAWLER_SWEEP_TICKS + 8f));
        if (hk > 0f) {
            Draw.color(Color.white);
            Draw.alpha(0.85f * hk);
            Fill.circle(hx, hy, 34f * hk + 4f);
            float L = 14f * S * hk + 10f;
            Lines.stroke(3f);
            Lines.line(hx - L, hy, hx + L, hy);
            Lines.line(hx, hy - L, hx, hy + L);
            Draw.color(main);
            Draw.alpha(0.7f * hk);
            Lines.stroke(4f * hk + 0.8f);
            Lines.line(hx - 520f * hk - 40f, hy, hx + 520f * hk + 40f, hy);
            Draw.color(Color.white);
            Draw.alpha(0.9f * hk);
            Lines.stroke(1.4f);
            Lines.line(hx - 300f * hk - 20f, hy, hx + 300f * hk + 20f, hy);
        }
        Draw.blend();

        if (isBloomEnabled()) Drawf.light(hx, hy, 170f * alpha, main, 0.85f * alpha);

        if (t < BRAWLER_SWEEP_TICKS + 10f) {
            float ak = alpha * clamp01(1f - (t - BRAWLER_SWEEP_TICKS) / 10f);
            float hang = d.a0 + side * sh.head;
            float tang = hang + side * 90f;
            int lines = lod >= 2 ? 2 : 7;
            for (int j = 0; j < lines; j++) {
                float rr = R - W * Mathf.randomSeed(seed + j * 3 + 1, 0.05f, 1f);
                float len = Mathf.randomSeed(seed + j * 3 + 2, 50f, 170f);
                float px = cx + Angles.trnsx(hang, rr), py = cy + Angles.trnsy(hang, rr);
                Draw.color(Color.white);
                Draw.alpha(0.85f * ak);
                Lines.stroke(2.2f + (j % 3) * 0.8f);
                Lines.line(px + Angles.trnsx(tang, 6f), py + Angles.trnsy(tang, 6f), px + Angles.trnsx(tang, len), py + Angles.trnsy(tang, len));
            }
        }

        int sparks = lod >= 2 ? 3 : 10;
        for (int j = 0; j < sparks; j++) {
            float u = Mathf.randomSeed(seed + j * 5 + 9, 0.35f, 1f);
            float ang = d.a0 + side * (sh.tail + span * u);
            float rr = R - W * brawlerProfile(u) * Mathf.randomSeed(seed + j * 5 + 10, 0.1f, 1f);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.6f + j * 2.1f);
            float sz = (4f + (j % 3) * 3f) * tw;
            float sx = cx + Angles.trnsx(ang, rr), sy = cy + Angles.trnsy(ang, rr);
            Draw.color(Color.white);
            Draw.alpha(alpha * tw);
            Lines.stroke(2f);
            Lines.line(sx - sz, sy, sx + sz, sy);
            Lines.line(sx, sy - sz, sx, sy + sz);
            Fill.circle(sx, sy, 1.6f * tw);
        }
        Draw.reset();
    });

    static final class ChargeData {
        final float k, tx, ty;
        ChargeData(float k, float tx, float ty) { this.k = k; this.tx = tx; this.ty = ty; }
    }

    // Giơ kiếm: vòng rune xoay dưới chân, tia năng lượng hút về lưỡi kiếm, quầng sáng tăng dần
    static final Effect brawlerChargeFx = new Effect(2f, 900f, e -> {
        if (!(e.data instanceof ChargeData)) return;
        ChargeData d = (ChargeData) e.data;
        float k = d.k;
        int lod = PerfCore.level();
        Color main = PerfCore.color(BRAWLER_COL_MAIN);
        float cx = e.x, cy = e.y;

        Draw.z(Layer.flyingUnit - 0.3f);
        Draw.color(main);
        Draw.alpha(0.4f * k);
        Lines.stroke(2f);
        Lines.circle(cx, cy, 36f + 12f * k);
        Draw.alpha(0.75f * k);
        Lines.stroke(2.6f);
        for (int i = 0; i < 8; i++) Lines.arc(cx, cy, 58f + 10f * k, 0.07f, Time.time * 5f + i * 45f);
        Draw.alpha(0.4f * k);
        Lines.stroke(1.4f);
        for (int i = 0; i < 6; i++) Lines.arc(cx, cy, 84f - 10f * k, 0.05f, -Time.time * 7f + i * 60f);

        Draw.z(Layer.effect + 0.05f);
        int streaks = lod >= 2 ? 4 : 12;
        for (int j = 0; j < streaks; j++) {
            float ph = (k * 1.4f + j / (float) streaks) % 1f;
            float rad = (1f - ph) * 190f + 24f;
            float ang = j * (360f / streaks) + Time.time * 2f;
            Draw.color(Color.white, main, ph);
            Draw.alpha(0.9f * ph * k);
            Lines.stroke(1.2f + 1.6f * ph);
            Lines.line(d.tx + Angles.trnsx(ang, rad), d.ty + Angles.trnsy(ang, rad), d.tx + Angles.trnsx(ang, rad * 0.8f), d.ty + Angles.trnsy(ang, rad * 0.8f));
        }

        Draw.blend(Blending.additive);
        Draw.color(main);
        Draw.alpha(0.3f * k);
        Fill.circle(d.tx, d.ty, 34f * k + 6f);
        Draw.blend();
        if (isBloomEnabled()) Drawf.light(d.tx, d.ty, 140f * k + 30f, main, 0.9f * k);
        Draw.reset();
    });

    // Trúng đòn: chớp sáng, vòng sóng, chém chữ X lớn, tia lửa tỏa ra
    static final Effect brawlerHitFx = new Effect(28f, 600f, e -> {
        float size = e.data instanceof Float ? (Float) e.data : 10f;
        float fin = e.fin(), fout = e.fout();
        Color main = PerfCore.color(BRAWLER_COL_MAIN);
        int seed = e.id * 29;
        float base = e.rotation;

        Draw.z(Layer.effect + 0.12f);
        if (isBloomEnabled()) Drawf.light(e.x, e.y, (110f + size * 2f) * fout, main, fout);

        Draw.color(Color.white);
        Draw.alpha(0.9f * fout * fout);
        Fill.circle(e.x, e.y, (12f + size * 0.8f) * fout);

        Draw.color(main);
        Draw.alpha(fout);
        Lines.stroke(5f * fout + 0.5f);
        Lines.circle(e.x, e.y, (34f + size) * (float) Math.sqrt(fin));
        Draw.color(Color.white);
        Lines.stroke(1.8f * fout);
        Lines.circle(e.x, e.y, (26f + size * 0.8f) * (float) Math.sqrt(fin));

        float L = (70f + size * 1.8f) * Math.min(1f, fin * 5f);
        for (int q = 0; q < 2; q++) {
            float a = base + (q == 0 ? 38f : -52f);
            float dx = Angles.trnsx(a, L), dy = Angles.trnsy(a, L);
            Draw.color(Color.black);
            Draw.alpha(0.6f * fout);
            Lines.stroke(8f * fout + 0.6f);
            Lines.line(e.x - dx, e.y - dy, e.x + dx, e.y + dy);
            Draw.color(Color.white);
            Draw.alpha(fout);
            Lines.stroke(3.2f * fout + 0.4f);
            Lines.line(e.x - dx, e.y - dy, e.x + dx, e.y + dy);
        }

        int sparks = PerfCore.level() >= 2 ? 4 : 10;
        for (int j = 0; j < sparks; j++) {
            float a = Mathf.randomSeed(seed + j * 3, 0f, 360f);
            float r0 = (14f + size * 0.5f) + fin * Mathf.randomSeed(seed + j * 3 + 1, 50f, 130f);
            float ls = 16f * fout + 2f;
            Draw.color(Color.white, main, fin);
            Draw.alpha(fout);
            Lines.stroke(2f * fout + 0.4f);
            Lines.line(e.x + Angles.trnsx(a, r0), e.y + Angles.trnsy(a, r0), e.x + Angles.trnsx(a, r0 + ls), e.y + Angles.trnsy(a, r0 + ls));
        }
        Draw.reset();
    });

    // Vòng sáng nhỏ nhấp nháy dưới chân brawler để nhận biết
    static final Effect brawlerAuraFx = new Effect(24f, e -> {
        Draw.z(Layer.flyingUnit - 0.2f);
        Draw.color(PerfCore.color(BRAWLER_COL_MAIN));
        Draw.alpha(0.55f * e.fout());
        Lines.stroke(1.6f * e.fout());
        Lines.circle(e.x, e.y, 8f + 16f * e.fin());
        Draw.reset();
    });

    // ===== Tiện ích mục tiêu (Teamc có thể là Unit hoặc Building – bài 5/26) =====
    static float px(Teamc t) { return ((Posc) t).getX(); }
    static float py(Teamc t) { return ((Posc) t).getY(); }
    static boolean isDead(Teamc t) { return t instanceof Healthc && ((Healthc) t).dead(); }

    /** JS khai báo nhưng không nơi nào gọi (parity). */
    static float brawlerTargetRadius(Teamc t) {
        if (t == null) return 0f;
        if (t instanceof Building) return ((Building) t).block.size * 4f;
        return t instanceof Hitboxc ? ((Hitboxc) t).hitSize() / 2f : 0f;
    }

    static float brawlerSwordDist(float scale) {
        return Math.max(24f, BRAWLER_SLASH_R - 30f * scale * 0.95f);
    }

    /** Sát thương = đúng hình vệt chém đang hiển thị (dải tail..head, dày theo brawlerProfile). */
    static void brawlerSlashDamage(final Unit unit, final DroneData data, boolean isMK2B) {
        if (Vars.net.client()) return;
        BShape sh = brawlerShapeAt(data.bsTimer);
        final float head = sh.head, tail = sh.tail, W = sh.W;
        final float cx = data.bsX, cy = data.bsY, R = BRAWLER_SLASH_R;
        final float mult = isMK2B ? 2f : 1f;
        if (head - tail < 1f) { data.bsPrev = head; return; }

        Units.nearbyEnemies(unit.team, cx, cy, R + 80f, enemy -> {
            if (enemy == null || !enemy.isValid() || enemy.dead || data.bsHitU.contains(enemy.id)) return;
            float rad = enemy.hitSize / 2f;
            float dist = Mathf.dst(cx, cy, enemy.x, enemy.y);
            if (dist > R + rad) return;
            float ang = Angles.angle(cx, cy, enemy.x, enemy.y);
            float dg = (((ang - data.bsA0) * data.bsSide) % 360f + 360f) % 360f;
            float angPad = Math.min(25f, (float) (Math.atan2(rad + 4f, Math.max(dist, 1f)) * 57.2958));
            if (dg < tail - angPad || dg > head + angPad) return;
            float u = clamp01((dg - tail) / Math.max(1f, head - tail));
            float w = Math.max(14f, W * brawlerProfile(u));
            if (dist < R - w - rad) return;
            data.bsHitU.add(enemy.id);
            enemy.damagePierce(BRAWLER_SLASH_DMG * mult + enemy.maxHealth * BRAWLER_SLASH_PCT);
            spawnSwordHitSlash(enemy.x, enemy.y, ang + 90f * data.bsSide, enemy.hitSize);
            brawlerHitFx.at(enemy.x, enemy.y, ang + 90f * data.bsSide, (Object) Float.valueOf(enemy.hitSize));
            if (!Vars.headless && !data.bsShook) {
                data.bsShook = true;
                Effect.shake(15f, 24f, enemy.x, enemy.y);
            }
        });

        // công trình: lấy mẫu trong dải vừa quét qua trong tick này
        for (float dg = data.bsPrev; dg <= head + 0.01f; dg += 3f) {
            float u = clamp01((dg - tail) / Math.max(1f, head - tail));
            float w = Math.max(12f, W * brawlerProfile(u));
            float ang = data.bsA0 + data.bsSide * dg;
            for (float r = R - w; r <= R; r += 10f) {
                float bx = cx + Angles.trnsx(ang, r), by = cy + Angles.trnsy(ang, r);
                Building bld = Vars.world.buildWorld(bx, by);
                if (bld != null && bld.team != unit.team && !data.bsHitB.contains(bld.pos())) {
                    data.bsHitB.add(bld.pos());
                    bld.damage(BRAWLER_SLASH_DMG * mult + bld.maxHealth * BRAWLER_SLASH_PCT);
                    spawnSwordHitSlash(bld.x, bld.y, ang + 90f * data.bsSide, bld.block.size * 8f);
                    brawlerHitFx.at(bld.x, bld.y, ang + 90f * data.bsSide, (Object) Float.valueOf(bld.block.size * 8f));
                    if (!Vars.headless && !data.bsShook) {
                        data.bsShook = true;
                        Effect.shake(12f, 20f, bld.x, bld.y);
                    }
                }
            }
        }
        data.bsPrev = head;
    }

    static void updateBrawler(Unit unit, DroneData data, Teamc target, Building launcher, float maxRange, float moveSpeed, boolean isMK2B) {
        float spd = moveSpeed * BRAWLER_SPEED_MULT;
        String st = data.swordState;
        if (!st.equals("idle") && !st.equals("bwindup") && !st.equals("bslash") && !st.equals("resting") && !st.equals("cooldown")) {
            st = data.swordState = "idle";
        }

        data.auraTimer += Time.delta;
        if (data.auraTimer >= 28f) {
            data.auraTimer = 0f;
            brawlerAuraFx.at(unit.x, unit.y);
        }

        if (st.equals("bwindup")) {
            data.bTimer += Time.delta;
            unit.vel.set(0f, 0f);
            if (target != null && !isDead(target)) data.bAim = Angles.angle(unit.x, unit.y, px(target), py(target));
            unit.rotation = data.bAim;

            float side = data.bSide;
            float a0 = data.bAim - side * BRAWLER_SWEEP_DEG / 2f;
            float k = clamp01(data.bTimer / BRAWLER_WINDUP_TICKS);
            float wScale = BRAWLER_SWORD_SCALE * (0.35f + 0.65f * k);
            float wAng = a0 - side * 14f * k;
            float wDist = 40f + (brawlerSwordDist(BRAWLER_SWORD_SCALE) - 40f) * k;
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) SwordRender.fan(unit.id, wAng, 1f, wDist, wScale));
            brawlerChargeFx.at(unit.x, unit.y, 0f, (Object) new ChargeData(k, unit.x + Angles.trnsx(wAng, wDist), unit.y + Angles.trnsy(wAng, wDist)));

            if (data.bTimer >= BRAWLER_WINDUP_TICKS) {
                data.bsX = unit.x; data.bsY = unit.y; data.bsSide = side; data.bsA0 = a0;
                data.bsSweep = side * BRAWLER_SWEEP_DEG;
                data.bsTimer = 0f; data.bsPrev = 0f;
                data.bsHitU.clear(); data.bsHitB.clear();
                data.bsShook = false;
                brawlerSlashFx.at(unit.x, unit.y, 0f, (Object) new SlashData(a0, side));
                if (!Vars.headless) Effect.shake(9f, 18f, unit.x, unit.y);
                warpDepartFx.at(unit.x, unit.y, data.bAim, (Object) new float[]{
                    unit.x + Angles.trnsx(data.bAim, BRAWLER_ENGAGE), unit.y + Angles.trnsy(data.bAim, BRAWLER_ENGAGE)});
                data.swordState = "bslash";
            }
            return;
        }

        if (st.equals("bslash")) {
            data.bsTimer += Time.delta;
            unit.vel.set(0f, 0f);
            float p = clamp01(data.bsTimer / BRAWLER_SWEEP_TICKS);
            float headDeg = brawlerShapeAt(data.bsTimer).head;
            renderSwordEffect.at(data.bsX, data.bsY, unit.rotation,
                (Object) SwordRender.fan(unit.id, data.bsA0 + data.bsSide * headDeg, 1f, brawlerSwordDist(BRAWLER_SWORD_SCALE), BRAWLER_SWORD_SCALE));
            int ghosts = PerfCore.level() >= 2 ? 1 : 3;
            for (int g = 1; g <= ghosts; g++) {
                renderSwordEffect.at(data.bsX, data.bsY, unit.rotation,
                    (Object) SwordRender.fan(unit.id + g, data.bsA0 + data.bsSide * Math.max(0f, headDeg - g * 9f), 0.5f / g, brawlerSwordDist(BRAWLER_SWORD_SCALE), BRAWLER_SWORD_SCALE));
            }
            brawlerSlashDamage(unit, data, isMK2B);
            if (p >= 1f) {
                data.swordState = "resting";
                data.restTimer = 0f;
            }
            return;
        }

        if (st.equals("resting")) {
            data.restTimer += Time.delta;
            unit.vel.scl(0.8f);
            float alpha = Math.max(0f, 1f - data.restTimer / BRAWLER_REST_TICKS);
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, false, false, 1f, 0f, alpha));
            if (Mathf.chance(0.3)) swordDissolveSparkEffect.at(unit.x + Mathf.range(12f), unit.y + Mathf.range(12f));
            if (data.restTimer >= BRAWLER_REST_TICKS) {
                data.swordState = "cooldown";
                data.bTimer = 0f;
            }
            return;
        }

        float swordAlpha = 1f;
        if (st.equals("cooldown")) {
            data.bTimer += Time.delta;
            swordAlpha = clamp01(data.bTimer / BRAWLER_COOLDOWN_TICKS);
            if (data.bTimer >= BRAWLER_COOLDOWN_TICKS) data.swordState = "idle";
        }
        renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, false, false, 1f, 0f, swordAlpha));

        if (target != null) {
            float tx = px(target), ty = py(target);
            float dst = Mathf.dst(unit.x, unit.y, tx, ty);
            unit.lookAt(tx, ty);

            if (dst <= BRAWLER_ENGAGE) {
                if (data.swordState.equals("idle")) {
                    data.swordState = "bwindup";
                    data.bTimer = 0f;
                    data.bSide = Mathf.chance(0.5) ? 1 : -1;
                    data.bAim = Angles.angle(unit.x, unit.y, tx, ty);
                    unit.vel.set(0f, 0f);
                } else {
                    unit.vel.scl(0.8f);
                }
            } else {
                unit.moveAt(Tmp.v1.set(tx - unit.x, ty - unit.y).limit(spd));
            }
        } else {
            data.repathTimer += Time.delta;
            if (data.repathTimer >= 120f || Mathf.dst2(unit.x, unit.y, data.targetX, data.targetY) < 400f) {
                data.repathTimer = 0f;
                float randomAngle = Mathf.random(360f);
                float randomDist = Mathf.random(20f, maxRange - 40f);
                data.targetX = launcher.x + Angles.trnsx(randomAngle, randomDist);
                data.targetY = launcher.y + Angles.trnsy(randomAngle, randomDist);
            }
            unit.moveAt(Tmp.v1.set(data.targetX - unit.x, data.targetY - unit.y).limit(spd));
            unit.lookAt(data.targetX, data.targetY);
        }
    }

    // ===== Trạng thái từng drone (JS: droneData[id]) =====
    static final class MasterPos {
        final float skyX, skyY, targetX, targetY, angle; final boolean hasLaser, special;
        MasterPos(float skyX, float skyY, float targetX, float targetY, float angle, boolean hasLaser, boolean special) {
            this.skyX = skyX; this.skyY = skyY; this.targetX = targetX; this.targetY = targetY; this.angle = angle; this.hasLaser = hasLaser; this.special = special;
        }
    }

    static final class MasterSpawn { float timer; boolean triggered; }

    static final class DroneData {
        float shootTimer = 0f, targetX, targetY, repathTimer = 0f;
        boolean initializedMK2B = false;
        String aiMode = null;
        float orbitAngle = Mathf.random(360f);
        int strafeDir = Mathf.chance(0.5) ? 1 : -1;
        int targetId = -1;
        String swordState = "idle";
        float chargeTimer = 0f, dashTimer = 0f, maxDashTime = 9.33f, restTimer = 0f, dashDirX = 0f, dashDirY = 0f;
        boolean hasAttachedSwords = false;
        String masterAttackState = "idle";
        float masterChargeTimer = 0f, masterBarrageTimer = 0f;
        int masterSwordsSpawned = 0, masterTotalSwords = 0;
        final Seq<MasterPos> masterSwordPositions = new Seq<>();
        float masterIdleTimer = 0f, masterMoveTargetX, masterMoveTargetY, masterHomingShootTimer = 0f;
        int activeKeySwordIndex = -1;
        boolean brawler = false, brawlerInit = false;
        float bTimer = 0f, bSide = 1f, bAim = 0f, auraTimer = 0f;
        // dash
        float dashPrevE = 0f, dashDist = 400f;
        final IntSet dashHitU = new IntSet(), dashHitB = new IntSet();
        int dashHitCount = 0;
        boolean fanRolled = false;
        // quạt chém
        float fanX, fanY, fanA0, fanSweep, fanTimer;
        final IntSet fanHitU = new IntSet(), fanHitB = new IntSet();
        // brawler slash
        float bsX, bsY, bsSide, bsA0, bsSweep, bsTimer, bsPrev;
        final IntSet bsHitU = new IntSet(), bsHitB = new IntSet();
        boolean bsShook = false;
    }

    static final IntMap<DroneData> droneData = new IntMap<>();
    static final IntMap<MasterSpawn> masterDroneSpawnTimers = new IntMap<>();

    static void forget(int id) {
        droneData.remove(id);
        DroneLauncher.mk2bUnits.remove(id);
        DroneLauncher.launcherMap.remove(id);
        DroneLauncher.recallSet.remove(id);
        masterDroneSpawnTimers.remove(id);
    }

    // ===== Loại unit (tra theo tên, nhiều tên dự phòng như JS) =====
    static UnitType normalType, mk2Type, swordType, masterType;
    static boolean typesCached = false;

    static UnitType unitType(String... names) {
        for (String n : names) {
            UnitType t = Vars.content.getByName(ContentType.unit, n);
            if (t != null) return t;
        }
        return null;
    }

    static Teamc findTarget(final int id) {
        Unit u = Groups.unit.find(x -> x.id == id);
        if (u != null) return u;
        return Groups.build.find(b -> b.id == id);
    }

    /** Mục tiêu đang giữ (nếu còn sống và là địch), ngược lại null. */
    static Teamc keptTarget(DroneData data, Unit unit) {
        if (data.targetId == -1) return null;
        Teamc t = findTarget(data.targetId);
        if (t != null && !isDead(t) && t.team() != unit.team) return t;
        return null;
    }

    public static void init() {
        Events.on(UnitDestroyEvent.class, event -> {
            if (event.unit != null && droneData.containsKey(event.unit.id)) forget(event.unit.id);
        });
        Events.run(Trigger.update, CargoDrone::update);
    }

    static void update() {
        if (Vars.state.isPaused()) return;

        if (!typesCached) {
            normalType = unitType("newex-combat-drone", "combat-drone", "newex-cargo-drone", "cargo-drone");
            mk2Type = unitType("newex-cargo-drone-mk2", "cargo-drone-mk2", "newex-cargo-dronemk2", "cargo-dronemk2");
            swordType = unitType("newex-cargo-sword-drone", "cargo-sword-drone", "cargo-sworddrone");
            masterType = unitType("cargo-master-sword-drone", "newex-cargo-master-sword-drone");
            if (normalType != null || mk2Type != null || swordType != null || masterType != null) typesCached = true;
        }
        final UnitType cargoDroneType = normalType, cargoDroneMK2Type = mk2Type, swordDroneType = swordType, masterSwordDroneType = masterType;
        if (cargoDroneType == null && cargoDroneMK2Type == null && swordDroneType == null && masterSwordDroneType == null) return;

        Groups.unit.each(unit -> {
            boolean isMasterSwordDrone = masterSwordDroneType != null && unit.type == masterSwordDroneType;
            boolean isSwordDrone = swordDroneType != null && unit.type == swordDroneType;
            boolean isNormalDrone = unit.type == cargoDroneType || unit.type == cargoDroneMK2Type;
            if (!((isNormalDrone || isSwordDrone || isMasterSwordDrone) && !unit.dead)) return;

            final int id = unit.id;

            // MK2B mới sinh: 10% đổi thành Master Sword Drone (mỗi pháo chỉ 1 master)
            if (DroneLauncher.mk2bUnits.contains(id) && !droneData.containsKey(id) && masterSwordDroneType != null) {
                final Integer currentLauncherId = DroneLauncher.launcherMap.get(id);
                boolean hasMasterAlready = false;
                boolean rollMaster = !Vars.net.client() && Mathf.chance(0.10) && unit.type != masterSwordDroneType;
                if (rollMaster && currentLauncherId != null && currentLauncherId != 0) {
                    for (Unit u : Groups.unit) {
                        Integer lid = DroneLauncher.launcherMap.get(u.id);
                        if (u.type == masterSwordDroneType && !u.dead && lid != null && lid.equals(currentLauncherId)) hasMasterAlready = true;
                    }
                }
                if (!hasMasterAlready && rollMaster) {
                    Unit newUnit = masterSwordDroneType.create(unit.team);
                    newUnit.set(unit.x, unit.y);
                    newUnit.add();
                    DroneLauncher.mk2bUnits.add(newUnit.id);
                    if (currentLauncherId != null) DroneLauncher.launcherMap.put(newUnit.id, currentLauncherId);
                    forget(id);
                    unit.remove();
                    return;
                }
            }

            boolean isMK2BUnit = DroneLauncher.mk2bUnits.contains(id);

            if (!droneData.containsKey(id)) {
                DroneData nd = new DroneData();
                nd.targetX = unit.x; nd.targetY = unit.y;
                nd.masterMoveTargetX = unit.x; nd.masterMoveTargetY = unit.y;
                // 50% khi spawn là brawler (chọn theo id để server và client luôn đồng nhất)
                nd.brawler = isSwordDrone && Mathf.randomSeed(id * 7919L + 17L, 0f, 1f) < BRAWLER_CHANCE;
                droneData.put(id, nd);
                if (isMasterSwordDrone) masterDroneSpawnTimers.put(id, new MasterSpawn());
            }

            if (isMasterSwordDrone && masterDroneSpawnTimers.containsKey(id)) {
                MasterSpawn st = masterDroneSpawnTimers.get(id);
                if (!st.triggered) {
                    st.timer += Time.delta;
                    if (st.timer >= 30f) {
                        st.triggered = true;
                        masterBeamEffect.at(unit.x, unit.y);
                        float[] randOffsets = {Mathf.range(8f), Mathf.range(8f), Mathf.range(8f)};
                        masterSwordsSummonEffect.at(unit.x, unit.y, 0f, (Object) randOffsets);
                        droneData.get(id).hasAttachedSwords = true;
                    }
                }
            }

            final DroneData data = droneData.get(id);

            if (isMK2BUnit && !data.initializedMK2B) {
                data.initializedMK2B = true;
                unit.maxHealth(unit.type.health * 2f);
                unit.health(unit.maxHealth());
                unit.armor(unit.type.armor * 2f);
            }

            if (data.brawler && !data.brawlerInit) {
                data.brawlerInit = true;
                unit.maxHealth(unit.type.health * (isMK2BUnit ? 2f : 1f) * BRAWLER_HP_MULT);
                unit.health(unit.maxHealth());
            }

            Integer launcherId = DroneLauncher.launcherMap.get(id);
            Building launcher = (launcherId != null && launcherId != 0) ? findBuild(launcherId) : null;
            boolean isBeingRecalled = DroneLauncher.recallSet.contains(id);

            if (launcher == null || launcher.dead || !launcher.isValid()) {
                recallLaserEffect.at(unit.x, unit.y, 0f, (Object) new float[]{unit.x - 300f, unit.y + 300f});
                forget(id);
                unit.remove();
                return;
            }

            boolean shouldRecall = (isSwordDrone || isMasterSwordDrone) ? isBeingRecalled : (unit.health < unit.maxHealth || isBeingRecalled);
            if (shouldRecall) {
                if (launcher instanceof DroneLauncher.DroneLauncherBuild) ((DroneLauncher.DroneLauncherBuild) launcher).triggerDoor();
                recallLaserEffect.at(unit.x, unit.y, 0f, (Object) new float[]{launcher.x, launcher.y});
                forget(id);
                unit.remove();
                return;
            }

            // JS: launcher.getRange() = block.range (x2.2 nếu MK2B) = DroneLauncherBuild.range()
            float maxRange = launcher instanceof DroneLauncher.DroneLauncherBuild ? ((DroneLauncher.DroneLauncherBuild) launcher).range() : 380f;
            float moveSpeed = isMK2BUnit ? unit.type.speed * 1.2f : unit.type.speed;

            if (isMasterSwordDrone) {
                updateMaster(unit, data, launcher, moveSpeed);
                return;
            }

            if (isSwordDrone) {
                Teamc target = keptTarget(data, unit);
                if (target == null) {
                    target = Units.closestTarget(unit.team, launcher.x, launcher.y, maxRange);
                    if (target != null) data.targetId = target.id();
                }
                if (data.brawler) {
                    updateBrawler(unit, data, target, launcher, maxRange, moveSpeed, isMK2BUnit);
                    return;
                }
                updateSword(unit, data, target, launcher, maxRange, moveSpeed, isMK2BUnit);
                return;
            }

            updateNormal(unit, data, launcher, maxRange, moveSpeed, isMK2BUnit);
        });
    }

    static Building findBuild(final int id) { return Groups.build.find(b -> b.id == id); }

    // ===== MASTER SWORD DRONE =====
    static void updateMaster(final Unit unit, DroneData data, Building launcher, float moveSpeed) {
        if (data.hasAttachedSwords) {
            renderAttachedMasterSwordsEffect.at(unit.x, unit.y, unit.rotation, (Object) Integer.valueOf(data.activeKeySwordIndex));
        }

        if (data.masterAttackState.equals("charging") || data.masterAttackState.equals("barrage")) {
            unit.vel.set(0f, 0f);
        } else {
            data.masterIdleTimer += Time.delta;
            if (data.masterIdleTimer >= 120f + Mathf.random(120f) || Mathf.dst2(unit.x, unit.y, data.masterMoveTargetX, data.masterMoveTargetY) < 100f) {
                data.masterIdleTimer = 0f;
                if (Mathf.chance(0.35)) {
                    data.masterMoveTargetX = unit.x;
                    data.masterMoveTargetY = unit.y;
                } else {
                    float randAngle = Mathf.random(360f), randDist = Mathf.random(20f, 80f);
                    data.masterMoveTargetX = launcher.x + Angles.trnsx(randAngle, randDist);
                    data.masterMoveTargetY = launcher.y + Angles.trnsy(randAngle, randDist);
                }
            }
            unit.moveAt(Tmp.v1.set(data.masterMoveTargetX - unit.x, data.masterMoveTargetY - unit.y).limit(moveSpeed));
        }

        Teamc target = Units.closestTarget(unit.team, unit.x, unit.y, 900f);
        if (target != null) unit.lookAt(px(target), py(target));

        if (data.masterAttackState.equals("charging")) {
            data.masterChargeTimer += Time.delta;

            if (((int) Math.floor(data.masterChargeTimer)) % 30 == 0) {
                int waves = Mathf.chance(0.5) ? 1 : 2;
                masterShockwaveRingEffect.at(unit.x, unit.y, 900f, (Object) Integer.valueOf(waves));
            }

            float attackAngle = target != null ? Angles.angle(unit.x, unit.y, px(target), py(target)) : unit.rotation;

            final Seq<Unit> tankyEnemies = new Seq<>();
            if (data.masterSwordsSpawned < 3) {
                Units.nearbyEnemies(unit.team, unit.x - 1100f, unit.y - 1100f, 2200f, 2200f, u -> {
                    if (!u.dead && Mathf.dst(unit.x, unit.y, u.x, u.y) <= 1100f) tankyEnemies.add(u);
                });
                tankyEnemies.sort((a, b) -> Float.compare(b.maxHealth, a.maxHealth));
            }

            int swordsPerFrame = 14;
            for (int s = 0; s < swordsPerFrame; s++) {
                if (data.masterSwordsSpawned < data.masterTotalSwords) {
                    data.masterSwordsSpawned++;

                    float targetX = 0f, targetY = 0f;
                    boolean isSpecialTankTarget = false;

                    if (data.masterSwordsSpawned <= 3) {
                        int tankIdx = data.masterSwordsSpawned - 1;
                        if (tankIdx < tankyEnemies.size && tankyEnemies.get(tankIdx).isValid()) {
                            Unit tankUnit = tankyEnemies.get(tankIdx);
                            targetX = tankUnit.x + Mathf.range(15f);
                            targetY = tankUnit.y + Mathf.range(15f);
                            isSpecialTankTarget = true;
                        }
                    }

                    if (!isSpecialTankTarget) {
                        float targetDist = Mathf.random(50f, 750f);
                        float targetAngle = attackAngle + Mathf.range(35f);
                        targetX = unit.x + Angles.trnsx(targetAngle, targetDist);
                        targetY = unit.y + Angles.trnsy(targetAngle, targetDist);
                    }

                    float skyX = targetX - Angles.trnsx(attackAngle, 350f);
                    float skyY = targetY - Angles.trnsy(attackAngle, 350f);

                    if (data.activeKeySwordIndex != -1) {
                        float unitAngle = unit.rotation, sideAngle = unitAngle - 90f;
                        float[] off = MASTER_OFFSETS[data.activeKeySwordIndex];
                        float startX = unit.x + Angles.trnsx(unitAngle, off[0]) + Angles.trnsx(sideAngle, off[1]);
                        float startY = unit.y + Angles.trnsy(unitAngle, off[0]) + Angles.trnsy(sideAngle, off[1]);
                        masterKeySwordFlyEffect.at(skyX, skyY, 0f, (Object) new float[]{startX, startY, skyX, skyY});
                    }

                    boolean isLaserSword = Mathf.chance(0.20);
                    data.masterSwordPositions.add(new MasterPos(skyX, skyY, targetX, targetY, attackAngle, isLaserSword, isSpecialTankTarget));

                    if (isBloomEnabled() || Mathf.chance(0.50)) skySwordHoverEffect.at(skyX, skyY, attackAngle);
                }
            }

            if (data.masterChargeTimer >= 180f || data.masterSwordsSpawned >= data.masterTotalSwords) {
                data.masterAttackState = "barrage";
                data.masterChargeTimer = 0f;
                data.masterBarrageTimer = 0f;
                data.activeKeySwordIndex = -1;
                for (int i = data.masterSwordPositions.size - 1; i > 0; i--) {
                    int j = (int) Math.floor(Mathf.random(i + 1f));
                    data.masterSwordPositions.swap(i, j);
                }
            }
            return;
        }

        if (data.masterAttackState.equals("barrage")) {
            data.masterBarrageTimer += Time.delta;
            unit.vel.set(0f, 0f);

            int strikeBatch = 40;
            for (int k = 0; k < strikeBatch; k++) {
                if (data.masterSwordPositions.size > 0) {
                    MasterPos pos = data.masterSwordPositions.pop();
                    if (pos != null) {
                        float finalTargetX = pos.targetX, finalTargetY = pos.targetY, finalAngle = pos.angle;

                        if (!pos.special && Mathf.chance(0.30)) {
                            float extraDist = Mathf.random(250f, 500f);
                            float extraAngleSpread = Mathf.range(35f);
                            finalAngle += extraAngleSpread;
                            finalTargetX += Angles.trnsx(finalAngle, extraDist);
                            finalTargetY += Angles.trnsy(finalAngle, extraDist);
                        }

                        if (isBloomEnabled() || Mathf.chance(0.50)) {
                            skySwordRainEffect.at(finalTargetX, finalTargetY, finalAngle, (Object) new SkyRain(pos.skyX, pos.skyY, finalTargetX, finalTargetY, pos.hasLaser));
                        }

                        final Team uTeam = unit.team;
                        final float tx = finalTargetX, ty = finalTargetY;
                        final boolean isSpecial = pos.special;

                        Time.run(10f, () -> {
                            Units.nearbyEnemies(uTeam, tx, ty, 450f, enemy -> {
                                if (enemy != null && enemy.isValid()) {
                                    enemy.health -= 500f;
                                    if (enemy.health <= 0f) enemy.kill();
                                }
                            });

                            Damage.damage(uTeam, tx, ty, 450f, 500f, false, true);
                            SplashFx.ring(tx, ty, 450f);

                            if (isBloomEnabled()) {
                                Fx.blastExplosion.at(tx, ty);
                                swordImpactShockwaveEffect.at(tx, ty);
                            } else {
                                masterLowParticleImpactEffect.at(tx, ty);
                            }

                            if (isSpecial) {
                                for (int auraI = 0; auraI < 40; auraI++) {
                                    float auraAngle = (360f / 40f) * auraI + Mathf.range(1.5f);
                                    masterBurstAuraSwordBullet.create(unit, uTeam, tx, ty, auraAngle);
                                }
                            }
                        });
                    }
                }
            }

            if (data.masterSwordPositions.size == 0 || data.masterBarrageTimer >= 300f) {
                data.masterAttackState = "homing_burst";
                data.masterHomingShootTimer = 0f;
            }
            return;
        }

        if (data.masterAttackState.equals("homing_burst")) {
            data.masterHomingShootTimer += Time.delta;

            if (data.masterHomingShootTimer >= 120f) {
                data.masterHomingShootTimer = 0f;
                float baseAngle = target != null ? Angles.angle(unit.x, unit.y, px(target), py(target)) : unit.rotation;
                float unitAngle = unit.rotation, sideAngle = unitAngle - 90f;
                for (int i = 0; i < 5; i++) {
                    float[] off = MASTER_OFFSETS[i];
                    float spawnX = unit.x + Angles.trnsx(unitAngle, off[0]) + Angles.trnsx(sideAngle, off[1]);
                    float spawnY = unit.y + Angles.trnsy(unitAngle, off[0]) + Angles.trnsy(sideAngle, off[1]);
                    float spreadAngle = baseAngle + (i - 2) * 15f + Mathf.range(4f);
                    masterHomingSwordBullet.create(unit, unit.team, spawnX, spawnY, spreadAngle);
                }
            }

            data.masterChargeTimer += Time.delta;
            if (data.masterChargeTimer >= 600f) {
                data.masterAttackState = "cooldown";
                data.masterChargeTimer = 0f;
            }
        }

        if (data.masterAttackState.equals("cooldown")) {
            data.masterChargeTimer += Time.delta;
            if (data.masterChargeTimer >= 180f) {
                data.masterAttackState = "idle";
                data.masterChargeTimer = 0f;
            }
        }

        if (target != null && data.masterAttackState.equals("idle")) {
            data.masterAttackState = "charging";
            data.masterChargeTimer = 0f;
            data.masterSwordsSpawned = 0;
            data.masterTotalSwords = isBloomEnabled() ? (int) Math.floor(Mathf.random(1000f, 1100f)) : (int) Math.floor(Mathf.random(500f, 550f));
            data.masterSwordPositions.clear();
            data.activeKeySwordIndex = (int) Math.floor(Mathf.random(0f, 3f));
        }
    }

    // ===== SWORD DRONE (dạng kiếm lướt) =====
    static void updateSword(Unit unit, DroneData data, Teamc target, Building launcher, float maxRange, float moveSpeed, boolean isMK2BUnit) {
        String ss = data.swordState;

        if (ss.equals("charging")) {
            float prog = Math.min(1f, data.chargeTimer / 72f);
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, true, false, prog, 0f, prog));
        } else if (ss.equals("dashing")) {
            float dashLin = Math.min(1f, data.dashTimer / data.maxDashTime);
            float dashProg = 1f - (float) Math.pow(1f - dashLin, 3.0);
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, false, true, 1f, dashProg, 1f));
        } else if (ss.equals("fanslash")) {
            float fanProg = Math.min(1f, data.fanTimer / SWORD_FAN_SWEEP_TICKS);
            renderSwordEffect.at(data.fanX, data.fanY, unit.rotation, (Object) SwordRender.fan(unit.id, data.fanA0 + data.fanSweep * fanProg));
        } else if (ss.equals("resting")) {
            float alpha = Math.max(0f, 1f - (data.restTimer / 60f));
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, false, false, 1f, 0f, alpha));
            if (Mathf.chance(0.35)) swordDissolveSparkEffect.at(unit.x + Mathf.range(12f), unit.y + Mathf.range(12f));
        } else if (ss.equals("idle")) {
            renderSwordEffect.at(unit.x, unit.y, unit.rotation, (Object) new SwordRender(unit.id, false, false, 1f, 0f, 1f));
        }

        if (ss.equals("charging")) {
            data.chargeTimer += Time.delta;
            if (target != null) unit.lookAt(px(target), py(target));

            if (data.chargeTimer >= 72f) {
                data.swordState = "dashing";
                data.chargeTimer = 0f;
                data.dashTimer = 0f;
                data.maxDashTime = WARP_DASH_TICKS;
                data.dashPrevE = 0f;
                data.dashHitU.clear(); data.dashHitB.clear();
                data.dashHitCount = 0;
                data.fanRolled = false;

                float angle = target != null ? Angles.angle(unit.x, unit.y, px(target), py(target)) : unit.rotation;
                data.dashDirX = Angles.trnsx(angle, 1f);
                data.dashDirY = Angles.trnsy(angle, 1f);
                // quãng lướt theo khoảng cách thật tới mục tiêu (+ vượt qua một đoạn), tối thiểu 400
                data.dashDist = target != null ? Math.min(900f, Math.max(400f, Mathf.dst(unit.x, unit.y, px(target), py(target)) + 150f)) : 400f;
                warpDepartFx.at(unit.x, unit.y, angle, (Object) new float[]{unit.x + data.dashDirX * data.dashDist, unit.y + data.dashDirY * data.dashDist});
            }
            return;

        } else if (ss.equals("dashing")) {
            data.dashTimer += Time.delta;

            float totalDistance = data.dashDist != 0f ? data.dashDist : 400f;
            float tNorm = Math.min(1f, data.dashTimer / data.maxDashTime);
            float easeNow = 1f - (float) Math.pow(1f - tNorm, WARP_EASE_POW);
            float mv = totalDistance * (easeNow - data.dashPrevE);
            data.dashPrevE = easeNow;
            float curSpeed = mv / Math.max(0.001f, Time.delta);

            float prevX = unit.x, prevY = unit.y;

            if (tNorm < 0.25f && target != null && !isDead(target) && data.dashHitCount == 0) {
                float want = Angles.angle(unit.x, unit.y, px(target), py(target));
                float cur = Angles.angle(0f, 0f, data.dashDirX, data.dashDirY);
                float nxt = Angles.moveToward(cur, want, 20f * Time.delta);
                data.dashDirX = Angles.trnsx(nxt, 1f);
                data.dashDirY = Angles.trnsy(nxt, 1f);
            }
            unit.set(unit.x + data.dashDirX * mv, unit.y + data.dashDirY * mv);
            // vel = 0: tránh engine cộng thêm vel*delta lần nữa
            unit.vel.set(0f, 0f);
            final float dashAngle = Angles.angle(0f, 0f, data.dashDirX, data.dashDirY);
            unit.rotation = dashAngle;

            if (unit.type.region != null) {
                warpBlurFx.at(unit.x, unit.y, dashAngle, (Object) new WarpBlur(unit.type.region, dashAngle,
                    Math.min(260f, 20f + curSpeed * 1.4f), 0.35f + 0.6f * (float) Math.pow(1f - tNorm, 2.0)));
            }

            float swordDmg = 150f * (isMK2BUnit ? 2f : 1f);
            final float DASH_HIT_R = 60f;
            int steps = Math.max(6, (int) Math.ceil(mv / 20f));
            for (int i = 0; i <= steps; i++) {
                final float sampleX = Mathf.lerp(prevX, unit.x, i / (float) steps);
                final float sampleY = Mathf.lerp(prevY, unit.y, i / (float) steps);
                Damage.damage(unit.team, sampleX, sampleY, DASH_HIT_R, swordDmg, true, true);

                if (data.dashHitCount < 14) {
                    final DroneData dd = data;
                    Units.nearbyEnemies(unit.team, sampleX, sampleY, DASH_HIT_R + 16f, enemy -> {
                        if (enemy == null || !enemy.isValid() || dd.dashHitU.contains(enemy.id)) return;
                        if (!enemy.within(sampleX, sampleY, DASH_HIT_R + enemy.hitSize / 2f)) return;
                        dd.dashHitU.add(enemy.id);
                        dd.dashHitCount++;
                        spawnSwordHitSlash(enemy.x, enemy.y, dashAngle, enemy.hitSize);
                    });

                    Building hitBuild = Vars.world.buildWorld(sampleX, sampleY);
                    if (hitBuild != null && hitBuild.team != unit.team && !data.dashHitB.contains(hitBuild.pos())) {
                        data.dashHitB.add(hitBuild.pos());
                        data.dashHitCount++;
                        spawnSwordHitSlash(hitBuild.x, hitBuild.y, dashAngle, hitBuild.block.size * 8f);
                    }
                }
            }

            if (data.dashHitCount > 0 && !data.fanRolled) {
                data.fanRolled = true;
                if (Mathf.chance(0.20)) {
                    unit.vel.set(0f, 0f);
                    startSwordFan(unit, data, dashAngle, isMK2BUnit);
                    data.swordState = "fanslash";
                    return;
                }
            }

            if (data.dashTimer >= data.maxDashTime) {
                warpArriveFx.at(unit.x, unit.y, dashAngle);
                data.swordState = "resting";
                data.restTimer = 0f;
                data.dashTimer = 0f;
            }
            return;

        } else if (ss.equals("fanslash")) {
            data.fanTimer += Time.delta;
            unit.vel.set(0f, 0f);
            float fanProg = Math.min(1f, data.fanTimer / SWORD_FAN_SWEEP_TICKS);
            swordFanDamage(unit, data, fanProg);
            if (data.fanTimer >= SWORD_FAN_SWEEP_TICKS) {
                data.swordState = "resting";
                data.restTimer = 0f;
                data.dashTimer = 0f;
            }
            return;

        } else if (ss.equals("resting")) {
            data.restTimer += Time.delta;
            if (data.restTimer >= 60f) {
                data.swordState = "cooldown";
                data.dashTimer = 0f;
            }
            return;

        } else if (ss.equals("cooldown")) {
            data.dashTimer += Time.delta;
            if (data.dashTimer >= 30f) {
                data.swordState = "idle";
                data.dashTimer = 0f;
            }
        }

        if (target != null) {
            float tx = px(target), ty = py(target);
            float dst = Mathf.dst(unit.x, unit.y, tx, ty);
            unit.lookAt(tx, ty);

            if (dst <= 320f && data.swordState.equals("idle")) {
                data.swordState = "charging";
                data.chargeTimer = 0f;
            } else {
                float angleToTarget = Angles.angle(unit.x, unit.y, tx, ty);
                float destX = tx - Angles.trnsx(angleToTarget, 140f);
                float destY = ty - Angles.trnsy(angleToTarget, 140f);
                unit.moveAt(Tmp.v1.set(destX - unit.x, destY - unit.y).limit(moveSpeed));
            }
        } else {
            wander(unit, data, launcher, maxRange, moveSpeed);
        }
    }

    /** Đi lang thang quanh pháo khi chưa có mục tiêu. */
    static void wander(Unit unit, DroneData data, Building launcher, float maxRange, float moveSpeed) {
        data.repathTimer += Time.delta;
        if (data.repathTimer >= 120f || Mathf.dst2(unit.x, unit.y, data.targetX, data.targetY) < 400f) {
            data.repathTimer = 0f;
            float randomAngle = Mathf.random(360f);
            float randomDist = Mathf.random(20f, maxRange - 40f);
            data.targetX = launcher.x + Angles.trnsx(randomAngle, randomDist);
            data.targetY = launcher.y + Angles.trnsy(randomAngle, randomDist);
        }
        unit.moveAt(Tmp.v1.set(data.targetX - unit.x, data.targetY - unit.y).limit(moveSpeed));
        unit.lookAt(data.targetX, data.targetY);
    }

    // ===== DRONE THƯỜNG / MK2 =====
    static void updateNormal(Unit unit, DroneData data, Building launcher, float maxRange, float moveSpeed, boolean isMK2BUnit) {
        Teamc target = null;
        if (data.aiMode != null) target = keptTarget(data, unit);
        if (target == null) target = Units.closestTarget(unit.team, launcher.x, launcher.y, maxRange);

        if (target != null) {
            float tx = px(target), ty = py(target);
            if (data.targetId != target.id()) {
                data.targetId = target.id();
                float rand = Mathf.random(1f);
                if (rand < 0.40f) data.aiMode = "strafe";
                else if (rand < 0.80f) data.aiMode = "orbit";
                else data.aiMode = "pursue";
            }

            float destX = tx, destY = ty;

            if (data.aiMode.equals("strafe")) {
                float angleToTarget = Angles.angle(unit.x, unit.y, tx, ty);
                float sideAngle = angleToTarget + (90f * data.strafeDir);
                destX = tx - Angles.trnsx(angleToTarget, 100f) + Angles.trnsx(sideAngle, 50f);
                destY = ty - Angles.trnsy(angleToTarget, 100f) + Angles.trnsy(sideAngle, 50f);
                if (Mathf.chance(0.02)) data.strafeDir *= -1;
            } else if (data.aiMode.equals("orbit")) {
                data.orbitAngle += (moveSpeed / 1.5f);
                float orbitRadius = 120f;
                destX = tx + Angles.trnsx(data.orbitAngle, orbitRadius);
                destY = ty + Angles.trnsy(data.orbitAngle, orbitRadius);
            } else {
                float safeDistance = 110f;
                float angleToTarget = Angles.angle(unit.x, unit.y, tx, ty);
                destX = tx - Angles.trnsx(angleToTarget, safeDistance);
                destY = ty - Angles.trnsy(angleToTarget, safeDistance);
            }

            if (Mathf.dst2(destX, destY, launcher.x, launcher.y) > maxRange * maxRange) {
                float angleFromLauncher = Angles.angle(launcher.x, launcher.y, destX, destY);
                destX = launcher.x + Angles.trnsx(angleFromLauncher, maxRange - 20f);
                destY = launcher.y + Angles.trnsy(angleFromLauncher, maxRange - 20f);
            }

            unit.moveAt(Tmp.v1.set(destX - unit.x, destY - unit.y).limit(moveSpeed));

            BasicBulletType bulletToShoot = isMK2BUnit ? droneBulletMK2B : droneBulletNormal;
            float bulletSpeed = bulletToShoot.speed;
            float aimX = tx, aimY = ty;

            if (target instanceof Hitboxc) {
                float dst = Mathf.dst(unit.x, unit.y, tx, ty);
                float timeToTarget = dst / bulletSpeed;
                aimX += ((Hitboxc) target).deltaX() * timeToTarget * 0.8f;
                aimY += ((Hitboxc) target).deltaY() * timeToTarget * 0.8f;
            }

            unit.lookAt(aimX, aimY);

            data.shootTimer += Time.delta;
            if (data.shootTimer >= 15f) {
                data.shootTimer = 0f;
                bulletToShoot.create(unit, unit.team, unit.x, unit.y, Angles.angle(unit.x, unit.y, aimX, aimY) + Mathf.range(3f));
            }
        } else {
            data.aiMode = null;
            data.targetId = -1;
            wander(unit, data, launcher, maxRange, moveSpeed);
        }
    }
}
