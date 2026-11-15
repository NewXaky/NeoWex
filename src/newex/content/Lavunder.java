package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Chuyển từ lavunder.js. Laser bám mục tiêu mặt đất, tích điểm đến 100 thì nổ; 50% bắn thêm tia Exo quét ngang.
 * Trạng thái theo id pháo (7 ObjectMap của JS) gộp thành lớp State.
 */
public final class Lavunder {
    private Lavunder() {}

    static final float deadZone = 40f;
    static final Color laserColor = Color.valueOf("bf7fff");
    static final Color particleColor = Color.valueOf("e8bfff");
    static final Color greenLaserColor = Color.valueOf("33ff55");
    static final Color amberTextColor = Color.valueOf("ffaa00");

    static final class Req { final int copper, graphite, lead, silicon; Req(int c, int g, int l, int s) { copper = c; graphite = g; lead = l; silicon = s; } }
    static final Req reqMK2 = new Req(400, 400, 0, 0);
    static final Req reqMK3 = new Req(0, 0, 450, 570);

    static final class Green { float x, y; int duration; Green(float x, float y, int d) { this.x = x; this.y = y; duration = d; } }
    static final class Triple { int count, timer; float x, y; Triple(int c, int t, float x, float y) { count = c; timer = t; this.x = x; this.y = y; } }
    static final class State {
        int tier = 1, burstTimer = 0, milestone = 0, exoCount = 0;
        float charge = 0f, dmgTimer = 0f;
        Triple triple = null;
        Green green = null;
    }
    static final HashMap<Integer, State> states = new HashMap<>();
    static State state(int id) { State s = states.get(id); if (s == null) { s = new State(); states.put(id, s); } return s; }

    static boolean isBloomEnabled() {
        try { return Core.settings.getBool("bloom", true); } catch (Exception e) { return true; }
    }

    static void dashCircle(float x, float y, float radius, Color color) {
        Draw.color(color);
        int segments = 48;
        for (int i = 0; i < segments; i += 2) {
            float a1 = (float) i / segments * 360f, a2 = (float) (i + 1) / segments * 360f;
            Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius), x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
        Draw.reset();
    }

    static void drawLaserRing(float cx, float cy, float radiusX, float radiusY, float laserAngle, float strokeWidth, Color color) {
        Draw.color(color); Lines.stroke(strokeWidth);
        int steps = 16; float lastX = 0f, lastY = 0f;
        float cosA = (float) Math.cos(laserAngle * Mathf.degRad), sinA = (float) Math.sin(laserAngle * Mathf.degRad);
        for (int i = 0; i <= steps; i++) {
            float angle = (i * (360f / steps)) * Mathf.degRad;
            float lx = (float) Math.cos(angle) * radiusX, ly = (float) Math.sin(angle) * radiusY;
            float rx = cx + (lx * cosA - ly * sinA), ry = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, rx, ry);
            lastX = rx; lastY = ry;
        }
    }

    // ===================================================================== TIA LASER KHỔNG LỒ (Exo Beam)
    static final float EXO_CHANCE = 0.5f;
    static final int EXO_PRE_TICKS = 12, EXO_DURATION = 96, EXO_PULSE_TICKS = 6;
    static final float EXO_SWEEP_DEG = 90f, EXO_LEN = 420f, EXO_W0 = 34f, EXO_W1 = 110f, EXO_DMG_FLAT = 150f, EXO_DMG_PCT = 0.005f;

    static final Color exoColGlow = Color.valueOf("ff2fa8"), exoColPink = Color.valueOf("ff6fd0"), exoColLight = Color.valueOf("ffc4ee"),
        exoColGold = Color.valueOf("ffd98a"), exoColCyan = Color.valueOf("35f2e0"), exoColTeal = Color.valueOf("17b8c9"), exoColPurple = Color.valueOf("b47bff");

    static final class Beam {
        int id, tier, side;
        Building owner; Team team;
        float x, y, base, t, pulse, seed;
        boolean started;
    }
    static final class ExoData { final float t, seed; ExoData(float t, float seed) { this.t = t; this.seed = seed; } }

    static final ArrayList<Beam> exoBeamList = new ArrayList<>();
    static Effect exoBeamFx;

    static float exoMult(int tier) { return tier == 2 ? 3f : (tier == 3 ? 2.5f : 1f); }
    static float exoClamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    static float exoEnv(float t) {
        if (t < 0f) return 0f;
        return exoClamp(t / 6f) * exoClamp((EXO_DURATION - t) / 22f);
    }

    static float exoBeamAngle(Beam bm) {
        float k = exoClamp(bm.t / EXO_DURATION);
        float sm = k * k * (3f - 2f * k);
        return bm.base + bm.side * (-EXO_SWEEP_DEG / 2f + EXO_SWEEP_DEG * sm);
    }

    /** 50% bắn tia khi đạt 100 sạc; quyết định theo seed (id tháp + số lần) để server/client thống nhất. */
    static void triggerExoBeam(Bullet b, int turretId, int tier, Teamc target) {
        if (!(b.owner instanceof Building) || target == null) return;
        Building owner = (Building) b.owner;
        State st = state(turretId);
        int cnt = st.exoCount;
        st.exoCount = cnt + 1;
        if (Mathf.randomSeed(turretId * 131L + cnt * 17L + 5L) >= EXO_CHANCE) return;
        for (int i = 0; i < exoBeamList.size(); i++) if (exoBeamList.get(i).id == turretId) return;

        Beam bm = new Beam();
        bm.id = turretId; bm.owner = owner; bm.team = b.team; bm.tier = tier;
        bm.x = owner.x; bm.y = owner.y;
        bm.base = Angles.angle(owner.x, owner.y, target.getX(), target.getY());
        bm.side = Mathf.randomSeed(turretId * 71L + cnt * 13L + 9L) < 0.5f ? 1 : -1;
        bm.t = -EXO_PRE_TICKS; bm.pulse = 0f;
        bm.seed = turretId * 0.137f + cnt * 3.1f;
        bm.started = false;
        exoBeamList.add(bm);
    }

    static void exoPulse(final Beam bm, float ang) {
        final float cosA = (float) Math.cos(ang * Mathf.degRad), sinA = (float) Math.sin(ang * Mathf.degRad);
        final float flat = EXO_DMG_FLAT * exoMult(bm.tier);
        float r = EXO_LEN + 60f;

        Units.nearbyEnemies(bm.team, bm.x - r, bm.y - r, r * 2f, r * 2f, u -> {
            if (u == null || u.dead || u.isFlying()) return;
            float dx = u.x - bm.x, dy = u.y - bm.y;
            float s = dx * cosA + dy * sinA;
            if (s < 0f || s > EXO_LEN) return;
            float perp = Math.abs(-dx * sinA + dy * cosA);
            float hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5f + u.hitSize / 2f;
            if (perp > hw) return;
            u.damage(flat + u.maxHealth * EXO_DMG_PCT);
            Fx.hitLancer.at(u.x, u.y, exoColPink);
        });

        HashSet<Integer> seen = new HashSet<>();
        for (float s = 0f; s <= EXO_LEN; s += 14f) {
            float hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5f;
            for (int q = -1; q <= 1; q++) {
                float off = q * hw * 0.7f;
                float px = bm.x + cosA * s - sinA * off;
                float py = bm.y + sinA * s + cosA * off;
                Building bld = Vars.world.buildWorld(px, py);
                if (bld != null && bld.team != bm.team && !seen.contains(bld.pos())) {
                    seen.add(bld.pos());
                    bld.damage(flat + bld.maxHealth * EXO_DMG_PCT);
                    Fx.hitLancer.at(bld.x, bld.y, exoColPink);
                }
            }
        }
    }

    static void exoStrip(float cx, float cy, float ux, float uy, float len, int seg, float wScale, float env, Color color, float alphaBase) {
        float nx = -uy, ny = ux;
        Draw.color(color);
        for (int i = 0; i < seg; i++) {
            float s0 = len * i / seg, s1 = len * (i + 1) / seg;
            float h0 = Mathf.lerp(EXO_W0, EXO_W1, s0 / EXO_LEN) * 0.5f * wScale * env;
            float h1 = Mathf.lerp(EXO_W0, EXO_W1, s1 / EXO_LEN) * 0.5f * wScale * env;
            float a = (1f - 0.5f * ((i + 0.5f) / seg)) * Math.min(1f, (seg - i) / 3f);
            Draw.alpha(alphaBase * a);
            Fill.quad(
                cx + ux * s0 + nx * h0, cy + uy * s0 + ny * h0,
                cx + ux * s1 + nx * h1, cy + uy * s1 + ny * h1,
                cx + ux * s1 - nx * h1, cy + uy * s1 - ny * h1,
                cx + ux * s0 - nx * h0, cy + uy * s0 - ny * h0);
        }
    }


    static void makeExoFx() {
        exoBeamFx = new Effect(2f, 1500f, e -> {
            if (!(e.data instanceof ExoData)) return;
            ExoData d = (ExoData) e.data;
            float t = d.t;
            float env = exoEnv(t);
            float flare = t < 0f ? exoClamp((t + EXO_PRE_TICKS) / EXO_PRE_TICKS) * 0.8f : Math.max(env, 0f);
            if (flare <= 0.01f) return;

            int lod = PerfCore.level();
            float ang = e.rotation;
            float cx = e.x + Angles.trnsx(ang, 8f), cy = e.y + Angles.trnsy(ang, 8f);
            float ux = Angles.trnsx(ang, 1f), uy = Angles.trnsy(ang, 1f);
            float nx = -uy, ny = ux;
            float flick = 0.9f + 0.1f * (float) Math.sin(t * 1.7f);

            Draw.z(Layer.effect + 0.2f);
            Draw.blend(Blending.additive);

            if (env > 0.01f) {
                float len = EXO_LEN * (0.35f + 0.65f * exoClamp(t / 5f));
                int seg = lod >= 2 ? 10 : 22;
                exoStrip(cx, cy, ux, uy, len, seg, 1.9f * flick, env, exoColGlow, 0.26f);
                exoStrip(cx, cy, ux, uy, len, seg, 1.25f * flick, env, exoColPink, 0.5f);
                exoStrip(cx, cy, ux, uy, len, seg, 0.78f, env, exoColLight, 0.75f);
                exoStrip(cx, cy, ux, uy, len, seg, 0.4f * flick, env, Color.white, 0.95f);

                Draw.color(exoColGold);
                Draw.alpha(0.6f * env);
                Lines.stroke(2f);
                float eh0 = EXO_W0 * 0.5f * 0.95f * env, eh1 = EXO_W1 * 0.5f * 0.95f * env * (len / EXO_LEN);
                Lines.line(cx + nx * eh0, cy + ny * eh0, cx + ux * len + nx * eh1, cy + uy * len + ny * eh1);
                Lines.line(cx - nx * eh0, cy - ny * eh0, cx + ux * len - nx * eh1, cy + uy * len - ny * eh1);

                int shards = lod >= 2 ? 10 : 36;
                for (int j = 0; j < shards; j++) {
                    float ph = (float) ((d.seed * 0.37 + j * 0.6180339 + t * (0.012 + (j % 5) * 0.004)) % 1.0);
                    float s = ph * len * 0.97f;
                    float hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5f * env;
                    float lat = Mathf.randomSeed((long) Math.floor(d.seed * 100) + j * 7L, -1f, 1f) * hw * 1.7f + (float) Math.sin(t * 0.3 + j) * 3f;
                    float px = cx + ux * s + nx * lat, py = cy + uy * s + ny * lat;
                    Color col = (j % 4 == 0) ? exoColPurple : ((j % 4 == 1) ? exoColTeal : ((j % 4 == 2) ? exoColCyan : Color.white));
                    Draw.color(col);
                    Draw.alpha(env * (0.55f + 0.45f * (float) Math.sin(t * 0.5 + j * 1.9)) * (1f - ph * 0.5f));
                    Fill.square(px, py, 1.1f + (j % 3) * 0.9f, t * 3f + j * 40f);
                }
            }

            // chớp sao 4 cánh ở gốc
            float L = 210f * flare * flick;
            float wd = 8f * flare + 1f;
            Draw.color(exoColPink);
            Draw.alpha(0.4f * flare);
            float L2 = L * 1.18f, wd2 = wd * 2f;
            Fill.tri(cx - L2, cy, cx, cy + wd2, cx, cy - wd2);
            Fill.tri(cx + L2, cy, cx, cy + wd2, cx, cy - wd2);
            Fill.tri(cx, cy - L2 * 0.9f, cx + wd2, cy, cx - wd2, cy);
            Fill.tri(cx, cy + L2 * 0.9f, cx + wd2, cy, cx - wd2, cy);
            Draw.color(Color.white);
            Draw.alpha(0.95f * flare);
            Fill.tri(cx - L, cy, cx, cy + wd, cx, cy - wd);
            Fill.tri(cx + L, cy, cx, cy + wd, cx, cy - wd);
            Fill.tri(cx, cy - L * 0.85f, cx + wd, cy, cx - wd, cy);
            Fill.tri(cx, cy + L * 0.85f, cx + wd, cy, cx - wd, cy);
            float Ld = L * 0.4f, wdd = wd * 0.6f;
            for (int q = 0; q < 2; q++) {
                float a = 45f + q * 90f;
                float dx = Angles.trnsx(a, Ld), dy = Angles.trnsy(a, Ld);
                float px = Angles.trnsx(a + 90f, wdd), py = Angles.trnsy(a + 90f, wdd);
                Fill.tri(cx - dx, cy - dy, cx + px, cy + py, cx - px, cy - py);
                Fill.tri(cx + dx, cy + dy, cx + px, cy + py, cx - px, cy - py);
            }

            Draw.color(exoColPink);
            Draw.alpha(0.35f * flare);
            Fill.circle(cx, cy, 70f * flare);
            Draw.color(Color.white);
            Draw.alpha(0.9f * flare);
            Fill.circle(cx, cy, 30f * flare * flick);
            Draw.alpha(0.5f * flare);
            Lines.stroke(2f);
            Lines.circle(cx, cy, 52f * flare + 6f * (float) Math.sin(t * 0.4));

            int sp = lod >= 2 ? 3 : 9;
            for (int j = 0; j < sp; j++) {
                float a = j * (360f / sp) + t * (2f + (j % 3));
                float r = 34f + (j % 4) * 16f + 6f * (float) Math.sin(t * 0.3 + j);
                float sx = cx + Angles.trnsx(a, r), sy = cy + Angles.trnsy(a, r);
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 0.6 + j * 2.3);
                float sz = (3f + (j % 3) * 2f) * tw * flare;
                Draw.color(j % 2 == 0 ? exoColPurple : Color.white);
                Draw.alpha(flare * tw);
                Lines.stroke(1.6f);
                Lines.line(sx - sz, sy, sx + sz, sy);
                Lines.line(sx, sy - sz, sx, sy + sz);
            }
            Draw.blend();

            if (isBloomEnabled()) {
                Drawf.light(cx, cy, 220f * flare, exoColPink, 0.9f * flare);
                if (env > 0.01f) {
                    for (int q = 1; q <= 3; q++) {
                        Drawf.light(cx + ux * (EXO_LEN * q / 3.5f), cy + uy * (EXO_LEN * q / 3.5f), 130f * env, exoColPink, 0.6f * env);
                    }
                }
            }
            Draw.reset();
        });
    }

    static void updateBeams() {
        if (exoBeamList.isEmpty() || Vars.state.isPaused()) return;
        for (int i = exoBeamList.size() - 1; i >= 0; i--) {
            Beam bm = exoBeamList.get(i);
            Building o = bm.owner;
            if (o == null || !o.isValid() || bm.t >= EXO_DURATION) {
                exoBeamList.remove(i);
                continue;
            }
            bm.x = o.x; bm.y = o.y;
            bm.t += Time.delta;
            float ang = exoBeamAngle(bm);

            if (bm.t >= 0f && !bm.started) {
                bm.started = true;
                if (!Vars.headless) {
                    Effect.shake(12f, 22f, bm.x, bm.y);
                    // JS: Sounds.laserbig không tồn tại ở v157 → .at() ném lỗi bị try/catch nuốt, chưa từng phát tiếng (bài học 33).
                }
            }

            exoBeamFx.at(bm.x, bm.y, ang, new ExoData(bm.t, bm.seed));

            if (bm.t >= 0f) {
                if (!Vars.headless && Mathf.chance(0.35)) Effect.shake(3.5f, 5f, bm.x, bm.y);
                bm.pulse += Time.delta;
                if (bm.pulse >= EXO_PULSE_TICKS) {
                    bm.pulse = 0f;
                    if (!Vars.net.client()) exoPulse(bm, ang);
                }
            }
        }
    }

    // ===================================================================== đạn laser chính
    static final class LaserData { boolean hasTarget = false; float lastX, lastY; }

    static boolean groundUnit(mindustry.gen.Unit u) { return u != null && !u.dead && !u.isFlying(); }

    static void blast(Bullet b, final float cx, final float cy, final float radius, final float damage) {
        Units.nearbyEnemies(b.team, cx - radius, cy - radius, radius * 2f, radius * 2f, u -> {
            if (groundUnit(u) && Mathf.dst(cx, cy, u.x, u.y) <= radius) u.damage(damage);
        });
    }

    static final class LaserBullet extends BulletType {
        LaserBullet() { speed = 0f; damage = 112f; lifetime = 12f; collides = false; }

        @Override
        public void init(Bullet b) {
            if (b == null) return;
            LaserData d = new LaserData();
            d.lastX = b.x + Angles.trnsx(b.rotation(), 260f);
            d.lastY = b.y + Angles.trnsy(b.rotation(), 260f);
            b.data = d;
        }

        @Override
        public void update(final Bullet b) {
            if (b == null || b.owner == null || !(b.data instanceof LaserData)) return;
            LaserData data = (LaserData) b.data;
            int turretId = b.owner.id();
            final float range = 260f;

            State st = state(turretId);
            int tier = st.tier;
            float currentPoints = st.charge;
            float dmgTimer = st.dmgTimer;

            if (st.burstTimer > 0) st.burstTimer--;

            if (st.green != null) {
                if (st.green.duration > 0) st.green.duration--;
                else st.green = null;
            }

            float baseDamage = 112f;
            if (tier == 3) baseDamage = 112f * 1.71f;
            float currentTickDamage = baseDamage * (1f + (currentPoints / 100f));

            if (st.triple != null) {
                Triple ex = st.triple;
                if (ex.count > 0) {
                    ex.timer--;
                    if (ex.timer <= 0) {
                        st.green = new Green(ex.x, ex.y, 12);
                        Fx.scatheExplosion.at(ex.x, ex.y);
                        Effect.shake(6f, 6f, ex.x, ex.y);
                        blast(b, ex.x, ex.y, 40f, 2400f + (currentTickDamage * 3f));
                        ex.count--;
                        ex.timer = 30;
                    }
                } else {
                    st.triple = null;
                }
            }

            final Teamc target = Units.closestTarget(b.team, b.x, b.y, range,
                u -> groundUnit(u) && Mathf.dst(b.x, b.y, u.x, u.y) >= deadZone, t -> true);

            if (target != null) {
                final float tx = target.getX(), ty = target.getY();
                data.hasTarget = true;
                data.lastX = tx;
                data.lastY = ty;

                dmgTimer++;
                if (dmgTimer >= 60f) {
                    if (target instanceof Healthc) ((Healthc) target).damage(currentTickDamage);
                    dmgTimer = 0f;
                }
                st.dmgTimer = dmgTimer;

                if (currentPoints < 100f) {
                    float chargeSpeedMultiplier = (float) (1 + Math.pow(currentPoints / 50f, 2));
                    float speedBonus = tier == 2 ? 3.8f : 1f;
                    currentPoints += (15f / 60f) * chargeSpeedMultiplier * speedBonus;

                    if (tier == 1 || tier == 2) {
                        if (currentPoints >= 100f) {
                            float refundPoints = (tier == 2 && Mathf.chance(0.50)) ? 80f : 0f;
                            currentPoints = refundPoints;
                            st.burstTimer = 14;

                            float explosionRadius = tier == 2 ? 40f : 60f;
                            float hpPercent = tier == 2 ? 0.02f : 0.01f;
                            final float flatDamage = tier == 2 ? 3200f : 1000f;
                            final float er = explosionRadius, hp = hpPercent;

                            Units.nearbyEnemies(b.team, tx - er, ty - er, er * 2f, er * 2f, u -> {
                                if (groundUnit(u) && Mathf.dst(tx, ty, u.x, u.y) <= er) u.damage((u.maxHealth * hp) + flatDamage);
                            });

                            Fx.scatheExplosion.at(tx, ty);
                            Effect.shake(tier == 2 ? 8f : 6f, tier == 2 ? 8f : 6f, tx, ty);

                            triggerExoBeam(b, turretId, tier, target);
                        }
                    } else if (tier == 3) {
                        int[] milestones = {20, 40, 60, 80};
                        int lastScannedMilestone = st.milestone;

                        for (int i = 0; i < milestones.length; i++) {
                            int stone = milestones[i];
                            if (currentPoints >= stone && lastScannedMilestone < stone) {
                                st.milestone = stone;
                                if (Mathf.chance(0.70)) {
                                    st.green = new Green(tx, ty, 12);
                                    Fx.scatheExplosion.at(tx, ty);
                                    blast(b, tx, ty, 40f, 800f + (currentTickDamage * 15f));
                                }
                            }
                        }

                        if (currentPoints >= 100f) {
                            currentPoints = 0f;
                            st.milestone = 0;
                            st.burstTimer = 14;

                            if (Mathf.chance(0.70)) {
                                st.green = new Green(tx, ty, 12);
                                Fx.scatheExplosion.at(tx, ty);
                                blast(b, tx, ty, 40f, 2400f + (currentTickDamage * 3f));
                            }

                            if (Mathf.chance(0.80)) st.triple = new Triple(3, 30, tx, ty);

                            triggerExoBeam(b, turretId, tier, target);
                        }
                    }
                    st.charge = currentPoints;
                }

                if (Mathf.chance(0.25)) Fx.hitLancer.at(tx, ty, laserColor);
            } else {
                data.hasTarget = false;
                data.lastX = b.x + Angles.trnsx(b.rotation(), range);
                data.lastY = b.y + Angles.trnsy(b.rotation(), range);

                st.dmgTimer = 0f;

                if (currentPoints > 0f) {
                    // JS: (2 / 60) là chia số thực = 0.0333 mỗi tick.
                    currentPoints -= 2f / 60f;
                    if (currentPoints < 0f) currentPoints = 0f;
                    st.charge = currentPoints;
                    if (currentPoints == 0f) st.milestone = 0;
                }
            }
        }

        @Override
        public void draw(Bullet b) {
            if (b == null || !(b.data instanceof LaserData) || b.owner == null) return;
            LaserData data = (LaserData) b.data;
            int turretId = b.owner.id();
            float startX = b.x, startY = b.y, endX = data.lastX, endY = data.lastY;
            float dist = Mathf.dst(startX, startY, endX, endY);
            float laserAngle = Angles.angle(startX, startY, endX, endY);

            State st = states.get(turretId);
            float currentPoints = st != null ? st.charge : 0f;
            int burstTimer = st != null ? st.burstTimer : 0;

            if (st != null && st.green != null) {
                Green g = st.green;
                float zoom = g.duration / 12f;
                Draw.color(greenLaserColor);
                Lines.stroke(16f * zoom);
                Lines.line(startX, startY, g.x, g.y);
                Draw.color(Color.white);
                Lines.stroke(5f * zoom);
                Lines.line(startX, startY, g.x, g.y);
            }

            if (burstTimer > 0) {
                float zoomFactor = burstTimer / 14f;
                Draw.color(Color.white);
                Lines.stroke(9f * zoomFactor);
                Lines.line(startX, startY, endX, endY);
            } else {
                Draw.color(laserColor); Lines.stroke(0.3f); Lines.line(startX, startY, endX, endY);
                Draw.color(Color.white); Lines.stroke(0.1f); Lines.line(startX, startY, endX, endY);
            }

            if (currentPoints > 2f) {
                float scaleFactor = 1f + (currentPoints / 180f);
                int ringCount = 2;
                for (int r = 0; r < ringCount; r++) {
                    float ringProgress = ((Time.time * 0.8f) + (r * (dist / ringCount))) % dist;
                    float rt = ringProgress / dist;
                    float rx = Mathf.lerp(startX, endX, rt), ry = Mathf.lerp(startY, endY, rt);
                    drawLaserRing(rx, ry, 1.5f * scaleFactor, 4f * scaleFactor, laserAngle, 0.4f * Mathf.sin(rt * Mathf.PI), laserColor);
                }

                int particleCount = (int) Math.floor(Mathf.lerp(4f, 16f, currentPoints / 100f));
                Draw.color(particleColor);
                for (int i = 0; i < particleCount; i++) {
                    float progress = ((Time.time * (0.4f + (i % 3) * 0.25f)) + (i * (dist / particleCount))) % dist;
                    float t = progress / dist;
                    float px = Mathf.lerp(startX, endX, t), py = Mathf.lerp(startY, endY, t);
                    float currentAngle = (Time.time * (1f + (i % 4) * 0.7f)) + (i * (360f / particleCount));
                    float rad = currentAngle * Mathf.degRad;
                    float la = laserAngle * Mathf.degRad;
                    float ox = px + (((float) Math.cos(rad) * 1.2f * scaleFactor) * (float) Math.cos(la) - ((float) Math.sin(rad) * 3.5f * scaleFactor) * (float) Math.sin(la));
                    float oy = py + (((float) Math.cos(rad) * 1.2f * scaleFactor) * (float) Math.sin(la) + ((float) Math.sin(rad) * 3.5f * scaleFactor) * (float) Math.cos(la));
                    Fill.circle(ox, oy, 0.4f + ((i % 3) * 0.2f));
                }
            }

            if (data.hasTarget) {
                float baseRadius = 3.5f + (currentPoints * 0.05f);
                float ringRadius = baseRadius * 1.6f;

                Draw.color(laserColor);
                Lines.stroke(0.8f + (Mathf.sin(Time.time * 0.15f) * 0.3f));
                Lines.circle(endX, endY, ringRadius);

                Draw.color(particleColor);
                Fill.circle(endX, endY, baseRadius);
                Draw.color(Color.white);
                Fill.circle(endX, endY, baseRadius * 0.55f);

                int pCount = (int) Math.floor(Mathf.lerp(3f, 8f, currentPoints / 100f));
                Draw.color(particleColor);
                for (int i = 0; i < pCount; i++) {
                    float hSeed = i * 45.7f + turretId;
                    float baseProgress = (Time.time * 0.4f + hSeed) % 1f;
                    float acceleratedProgress = (float) Math.pow(baseProgress, 3);
                    float invT = 1f - acceleratedProgress;
                    float pAngle = (hSeed + i * (360f / pCount)) % 360f;
                    float curDist = ringRadius * 3.5f * invT;
                    float finalAngle = pAngle + (acceleratedProgress * 80f);
                    Fill.circle(endX + Angles.trnsx(finalAngle, curDist), endY + Angles.trnsy(finalAngle, curDist), 0.3f + (acceleratedProgress * 0.5f));
                }
            }

            if (currentPoints > 0f && b.owner instanceof Building) {
                Building o = (Building) b.owner;
                Draw.color(amberTextColor);
                Font font = Fonts.outline;
                float prevScale = font.getData().scaleX;
                font.getData().setScale(0.25f);
                // JS: java.lang.String.valueOf(Math.floor(x)) → bản double nên hiển thị "12.0"
                font.draw(String.valueOf(Math.floor(currentPoints)), o.x, o.y + 3f, Align.center);
                font.getData().setScale(prevScale);
            }
            Draw.reset();
        }
    }

    // ===================================================================== block
    static TextureRegion bodyRegion, downxRegion, a1Region, a2Region;
    public static PowerTurret lavunder;
    static LaserBullet laserBullet;

    static boolean localeEn() {
        String loc = Core.settings.getString("locale");
        return loc != null && loc.startsWith("en");
    }

    public static void create() {
        makeExoFx();
        Events.run(Trigger.update, Lavunder::updateBeams);
        laserBullet = new LaserBullet();

        lavunder = new PowerTurret("lavunder") {
            @Override
            public void load() {
                super.load();
                bodyRegion = PerfCore.region(name + "-body");
                downxRegion = PerfCore.region(name + "-downx");
                a1Region = PerfCore.region(name + "-a1");
                a2Region = PerfCore.region(name + "-a2");
            }

            @Override
            public void drawPlace(int x, int y, int rotation, boolean valid) {
                super.drawPlace(x, y, rotation, valid);
                Draw.color(Pal.remove); Lines.stroke(2f);
                dashCircle(x * Vars.tilesize, y * Vars.tilesize, deadZone, Pal.remove);
                Draw.reset();
            }
        };
        lavunder.health = 3750; lavunder.size = 4;
        lavunder.targetAir = false; lavunder.targetGround = true;
        lavunder.range = 260f; lavunder.reload = 10f;
        lavunder.shootType = laserBullet;
        lavunder.recoil = 0f;
        lavunder.consumePower(22f);
        lavunder.configurable = true;

        lavunder.config(Integer.class, (Building tile, Integer value) -> {
            if (tile == null || value == null) return;
            state(tile.id).tier = value;
            if (value == 1) tile.health = 3750;
            else if (value == 2) tile.health = 5707;
            else if (value == 3) tile.health = 6848;
        });
        lavunder.buildType = () -> new LavunderBuild(lavunder);
    }

    public static class LavunderBuild extends PowerTurret.PowerTurretBuild {
        float downxOffset = 0f, wingsOffset = 0f;

        public LavunderBuild(PowerTurret outer) { outer.super(); }

        @Override
        public boolean canControl() { return false; }

        @Override
        public void placed() {
            super.placed();
            downxOffset = 0f; wingsOffset = 0f;
        }

        /** JS override `destroyed()` không tồn tại ở v157 (chưa từng chạy) → dùng onRemoved() để dọn 8 map (bài học 25). */
        @Override
        public void onRemoved() {
            super.onRemoved();
            states.remove(id);
        }

        @Override
        public void updateTile() {
            super.updateTile();
            boolean hasTarget = Units.closestTarget(team, x, y, 260f, u -> !u.dead && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone, b -> true) != null;
            if (hasTarget) {
                downxOffset = Mathf.lerpDelta(downxOffset, -4f, 0.15f);
                wingsOffset = Mathf.lerpDelta(wingsOffset, 1f, 0.15f);
            } else {
                downxOffset = Mathf.lerpDelta(downxOffset, 0f, 0.1f);
                wingsOffset = Mathf.lerpDelta(wingsOffset, 0f, 0.1f);
            }
        }

        @Override
        public void draw() {
            // JS: lavunder.basePrefix không tồn tại → "undefinedblock-4" không bao giờ thấy → luôn super.draw() (bài học 13).
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

            if (bodyRegion != null && bodyRegion.found()) Draw.rect(bodyRegion, x, y, rotation);
            if (downxRegion != null && downxRegion.found()) Draw.rect(downxRegion, x + downxOffset * cos, y + downxOffset * sin, rotation);

            float wingBackOffset = -4f * wingsOffset, combatSideMove = 4f * wingsOffset;
            if (a1Region != null && a1Region.found()) {
                Draw.rect(a1Region, x + (wingBackOffset * cos) - (combatSideMove * sin), y + (wingBackOffset * sin) + (combatSideMove * cos), rotation);
            }
            if (a2Region != null && a2Region.found()) {
                Draw.rect(a2Region, x + (wingBackOffset * cos) + (combatSideMove * sin), y + (wingBackOffset * sin) - (combatSideMove * cos), rotation);
            }
            Draw.reset();
        }

        private String requirementText(boolean isEn) {
            CoreBuild core = team.core();
            if (core == null) return isEn ? "[red]Team Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";
            int copperAmt = core.items.get(Items.copper);
            int graphiteAmt = core.items.get(Items.graphite);
            int leadAmt = core.items.get(Items.lead);
            int siliconAmt = core.items.get(Items.silicon);
            String copperColor = copperAmt >= reqMK2.copper ? "[green]" : "[red]";
            String graphiteColor = graphiteAmt >= reqMK2.graphite ? "[green]" : "[red]";
            String leadColor = leadAmt >= reqMK3.lead ? "[green]" : "[red]";
            String siliconColor = siliconAmt >= reqMK3.silicon ? "[green]" : "[red]";
            if (isEn) {
                        return "[yellow]CORE VAULT REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Branch:[]\n" +
                               " • Copper: " + copperColor + copperAmt + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graphiteColor + graphiteAmt + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]MK2B Branch:[]\n" +
                               " • Lead: " + leadColor + leadAmt + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + siliconColor + siliconAmt + "[] / " + reqMK3.silicon;
            } else {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Đồng: " + copperColor + copperAmt + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graphiteColor + graphiteAmt + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Chì: " + leadColor + leadAmt + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + siliconColor + siliconAmt + "[] / " + reqMK3.silicon;
            }
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(isEn ? "Lavunder Upgrade Center" : "Trung tâm nâng cấp pháo Lavunder");
            Cell<Label> reqCell = dialog.cont.add(requirementText(isEn));
            reqCell.width(360f); reqCell.get().setWrap(true); reqCell.get().setAlignment(Align.left);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branchesTable = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
                String b1Text = isEn ?
                    "Sequential Firepower Pulse Module:\n" +
                    " [white]• Base Damage: [yellow]112.00 DMG/sec[] (deals damage every 1s).[]\n" +
                    " [white]• Energy point charging rate accelerated by [green]+380% (3.8x)[].[]\n" +
                    " [white]• At [yellow]100 Charge[]: Deals [red]3,200 flat DMG[] + [cyan]2% Max HP[] (40px radius).[]\n" +
                    " [white]• [green]Feedback Loop[]: [orange]50%[] chance to refund [yellow]80 charge points[] upon detonation.[]\n" +
                    " [white]• [green]+52.17% HP[] (5,707) and expanded [green]+6.25% Range[] (276.2 px)." :
                    "Mô-đun kích xung hỏa lực liên hoàn:\n" +
                    " [white]• Sát thương cơ bản: [yellow]112.00 ST/giây[] (gây dmg mỗi 1s).[]\n" +
                    " [white]• Tốc độ gia tốc sạc điểm năng lượng tăng trưởng nhanh [green]+380% (3.8x)[].[]\n" +
                    " [white]• Khi đạt [yellow]100 sạc[]: Gây [red]3,200 ST flat[] + [cyan]2% HP tối đa[] (bán kính 40 px).[]\n" +
                    " [white]• [green]Mạch Phản Hồi[]: Có [orange]50%[] tỷ lệ hoàn trả [yellow]80 điểm sạc[] ngay sau nổ.[]\n" +
                    " [white]• Tăng [green]+52.17% Máu[] (5,707) và mở rộng [green]+6.25% Tầm bắn[] (276.2 px).";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f); b1D.get().setWrap(true); b1D.get().setAlignment(Align.left); b1.row();
            b1.button(isEn ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.copper) >= reqMK2.copper && core.items.get(Items.graphite) >= reqMK2.graphite) {
                    NexNet.take(core, Items.copper, reqMK2.copper);
                    NexNet.take(core, Items.graphite, reqMK2.graphite);
                    configure(Integer.valueOf(2));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
                }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
                String b2Text = isEn ?
                    "Area Destructive Spectral Focus Core:\n" +
                    " [white]• Base Damage: [pink]191.52 DMG/sec[] (deals damage every 1s).[]\n" +
                    " [white]• [cyan]Multi-Tier Charge[]: At thresholds [yellow]20,40,60,80[] has a [white]70%[] chance for secondary beam blasts.[]\n" +
                    " [white]• At [yellow]100 Charge[]: Triggers a sequence of [orange]3 consecutive explosive blasts[].[]\n" +
                    " [white]• Super reinforced structure grants [green]+82.6% HP[] (6,848).[]\n" +
                    " [white]• Maintains base [yellow]Range[] (260 px)." :
                    "Lõi hội tụ quang phổ hủy diệt diện rộng:\n" +
                    " [white]• Sát thương cơ bản: [pink]191.52 ST/giây[] (gây dmg mỗi 1s).[]\n" +
                    " [white]• [cyan]Cơ chế Sạc Đa Tầng[]: Tại mốc [yellow]20,40,60,80[] có [white]70%[] tỷ lệ nổ phụ tia.[]\n" +
                    " [white]• Khi chạm [yellow]100 sạc[]: Kích hoạt chuỗi [orange]3 loạt bộc phá liên tiếp[] hủy diệt.[]\n" +
                    " [white]• Siêu gia cố cấu trúc tăng [green]+82.6% Máu[] (6,848).[]\n" +
                    " [white]• Giữ nguyên [yellow]Tầm bắn[] (260 px).";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f); b2D.get().setWrap(true); b2D.get().setAlignment(Align.left); b2.row();
            b2.button(isEn ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.lead) >= reqMK3.lead && core.items.get(Items.silicon) >= reqMK3.silicon) {
                    NexNet.take(core, Items.lead, reqMK3.lead);
                    NexNet.take(core, Items.silicon, reqMK3.silicon);
                    configure(Integer.valueOf(3));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
                }
            }).size(180, 38);

            branchesTable.add(b1).width(340f); branchesTable.row();
            branchesTable.add().height(12).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = isEn ? " Lavunder Turret Stats: " : " Thông số pháo Lavunder: ";
            String descStr = "";
            State st = states.get(id);
            int currentTier = st != null ? st.tier : 1;

            if (currentTier == 1) {
                title += "[yellow](MK1)[]";
                descStr = isEn ?
                          "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]3,750[]\n" +
                          "[lightgray]Effective Range:[] [orange]260 pixels[] (Deadzone: <40 px)\n" +
                          "[lightgray]Base Damage:[] [white]112.00 firepower/sec (Interval: 1s/hit)[]\n" +
                          "[lightgray]Power Consumption:[] [lightgray]22/sec[]" :
                          "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]3,750[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]260 pixel[] (Vùng mù: <40 px)\n" +
                          "[lightgray]Sát thương cơ bản:[] [white]112.00 hỏa lực/giây (Chu kỳ: 1s/lần)[]\n" +
                          "[lightgray]Năng lượng tiêu thụ:[] [lightgray]22/giây[]";
            } else if (currentTier == 2) {
                title += "[cyan](MK2)[]";
                descStr = isEn ?
                          "[cyan]⚡ UPGRADED CONFIGURATION STATS (MK2) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]5,707 (+52.17%)[]\n" +
                          "[lightgray]Effective Range:[] [orange]276.2 pixels (+6.25%)[]\n" +
                          "[lightgray]Base Damage:[] [white]112.00 firepower/sec[]\n" +
                          "[lightgray]Charge Acceleration:[] [green]3.8x speed[]\n" +
                          "[lightgray]Detonation Effect:[] [red]3,200 flat DMG[] + [cyan]2% Max HP[]\n" +
                          "[lightgray]Feedback Rate:[] [orange]50% chance[] to refund 80 charge" :
                          "[cyan]⚡ THÔNG SỐ CẤU HÌNH NÂNG CẤP (MK2) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]5,707 (+52.17%)[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]276.2 pixel (+6.25%)[]\n" +
                          "[lightgray]Sát thương cơ bản:[] [white]112.00 hỏa lực/giây[]\n" +
                          "[lightgray]Gia tốc sạc điểm:[] [green]Tốc độ x3.8[]\n" +
                          "[lightgray]Hiệu ứng nổ bộc phá:[] [red]3,200 ST flat[] + [cyan]2% HP max[]\n" +
                          "[lightgray]Tỷ lệ hồi sạc:[] [orange]50% cơ hội[] hoàn 80 điểm sạc";
            } else if (currentTier == 3) {
                title += "[purple](MK2B)[]";
                descStr = isEn ?
                          "[purple]⚡ SPECTRAL VARIANT STATS (MK2B) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]6,848 (+82.6%)[]\n" +
                          "[lightgray]Effective Range:[] [orange]260 pixels[]\n" +
                          "[lightgray]Base Damage:[] [pink]191.52 firepower/sec (+71%)[]\n" +
                          "[lightgray]Multi-Tier Charge:[] Blast sub-beams at [yellow]20,40,60,80 charge[]\n" +
                          "[lightgray]Max Charge Burst:[] Trigger [orange]3 consecutive explosive blasts[]" :
                          "[purple]⚡ THÔNG SỐ BIẾN THỂ QUANG PHỔ (MK2B) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]6,848 (+82.6%)[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]260 pixel[]\n" +
                          "[lightgray]Sát thương cơ bản:[] [pink]191.52 hỏa lực/giây (+71%)[]\n" +
                          "[lightgray]Sạc Đa Tầng:[] Nổ tia phụ tại các mốc [yellow]20,40,60,80 sạc[]\n" +
                          "[lightgray]Bộc phá Tối đại:[] Kích hoạt chuỗi [orange]3 loạt nổ liên tiếp[]";
            }

            descStr += isEn ?
                "\n[pink]Exo Beam:[] At [yellow]100 charge[] there is a [orange]50%[] chance to fire a giant sweeping laser (pink-white, ~420 px, 1.6s)." :
                "\n[pink]Tia Exo:[] Khi đạt [yellow]100 sạc[] có [orange]50%[] tỷ lệ bắn một tia laser khổng lồ quét ngang (hồng-trắng, ~420 px, 1.6 giây).";
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true); cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            State st = states.get(id);
            int tier = st != null ? st.tier : 1;
            final boolean isEn = localeEn();
            if (tier == 1) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(isEn ? "Upgrade Lavunder Turret" : "Nâng cấp tháp pháo Lavunder");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(isEn
                    ? "[scarlet]LAVUNDER SYSTEM HAS REACHED MAXIMUM EVOLUTION TIER![]"
                    : "[scarlet]HỆ THỐNG LAVUNDER ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]"))
                    .size(50, 40).tooltip(isEn ? "Max level reached" : "Đã đạt cấp tối đa");
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(isEn ? "View Detailed System Stats" : "Xem chi tiết thông số trạng thái");
        }
    }
}
