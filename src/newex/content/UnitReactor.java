package newex.content;

import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.Payload;
import mindustry.world.blocks.payloads.PayloadVoid;
import mindustry.world.blocks.payloads.UnitPayload;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ unit-reactor.js. Lò phản ứng unit: hấp thụ VĨNH VIỄN unit (payload) để tạo điện theo cấp T1-T5.
 * Mỗi cấp có giới hạn số lượng; đủ cả 5 cấp thì cộng hưởng +20%. Bị phá hủy sẽ nổ theo lượng điện đang sinh.
 * Chỉnh cân bằng ở 3 hằng số POWER_PER_TIER, CAP_PER_TIER, SYNERGY (điện tính theo /tick, x60 = /giây).
 */
public final class UnitReactor {
    private static final float[] POWER_PER_TIER = {0.4f, 1.5f, 6f, 20f, 50f};
    private static final int[] CAP_PER_TIER = {64, 32, 16, 8, 4};
    private static final float SYNERGY = 1.2f; // có đủ 5 cấp -> +20%

    private static final String[] TIER_COLORS = {"a0e6ff", "7fffa0", "ffe066", "ff9a4d", "ff5c8a"};

    private static final Map<String, Integer> TIER_BY_NAME = new HashMap<>();
    private static final float MAXP;

    static {
        String[][] tiers = {
            // Serpulo
            {"dagger", "nova", "crawler", "flare", "mono", "risso", "retusa"},
            {"mace", "pulsar", "atrax", "horizon", "poly", "minke", "oxynoe"},
            {"fortress", "quasar", "spiroct", "zenith", "mega", "bryde", "cyerce"},
            {"scepter", "vela", "arkyid", "antumbra", "quad", "sei", "aegires"},
            {"reign", "corvus", "toxopid", "eclipse", "oct", "omura", "navanax"},
            // Erekir
            {"stell", "merui", "elude"},
            {"locus", "cleroi", "avert"},
            {"precept", "anthicus", "obviate"},
            {"vanquish", "tecta", "quell"},
            {"conquer", "collaris", "disrupt"}
        };
        for (int i = 0; i < tiers.length; i++) {
            for (String n : tiers[i]) TIER_BY_NAME.put(n, (i % 5) + 1);
        }

        float m = 0f;
        for (int i = 0; i < 5; i++) m += CAP_PER_TIER[i] * POWER_PER_TIER[i];
        MAXP = m * SYNERGY;
    }

    static int tierOf(UnitType type) {
        Integer t = TIER_BY_NAME.get(type.name);
        if (t != null && t != 0) return t;
        float h = type.health; // unit mod/NeoWex: suy theo máu
        return h <= 300 ? 1 : h <= 1000 ? 2 : h <= 5000 ? 3 : h <= 15000 ? 4 : 5;
    }

    // ================= HIỆU ỨNG ĐIỆN ẢNH VŨ TRỤ =================
    private static final Color WHITE = Color.white;
    private static final float COS45 = (float) Math.sqrt(0.5);

    private static TextureRegion iconOf(UnitType type) {
        if (type.fullIcon != null) return type.fullIcon;
        return type.region;
    }

    // 1) Unit -> đốm sáng (0.2s) -> bay lên phải-trên, để lại vệt sáng mờ dần
    private static final float FLIGHT_LIFE = 135f, T_SHRINK = 12f, T_FLY = 75f, FLY_DIST = 1020f, FLY_ARC = 95f;
    private static final float LASER_T0 = T_FLY + 4f, APPLY_DELAY = LASER_T0 + 6f;

    private static float[] flightPos(float x, float y, float s) { // s: 0..1 dọc quỹ đạo cong nhẹ
        float arc = Mathf.sin(s * Mathf.PI) * FLY_ARC;
        return new float[]{x + COS45 * (s * FLY_DIST) - COS45 * arc, y + COS45 * (s * FLY_DIST) + COS45 * arc};
    }

    /** Dữ liệu cho hiệu ứng bay lên. */
    private static final class FlightData {
        final TextureRegion icon;
        final float w, h, rot;
        final String c;

        FlightData(TextureRegion icon, float w, float h, float rot, String c) {
            this.icon = icon;
            this.w = w;
            this.h = h;
            this.rot = rot;
            this.c = c;
        }
    }

    private static final Effect absorbFlight = new Effect(FLIGHT_LIFE, 1500f, e -> {
        if (!(e.data instanceof FlightData)) return;
        FlightData d = (FlightData) e.data;
        Color col = PerfCore.color(d.c);
        float T = e.fin() * FLIGHT_LIFE;
        Draw.z(Layer.effect + 0.5f);

        if (T < T_SHRINK) { // giai đoạn 1: thu nhỏ thành đốm sáng
            float k = T / T_SHRINK;
            if (d.icon != null) {
                Draw.mixcol(col, Math.min(1f, k * 1.6f));
                Draw.alpha(1f - k * 0.5f);
                float sc = 1f - k * k;
                Draw.rect(d.icon, e.x, e.y, d.w * sc, d.h * sc, d.rot - 90f);
                Draw.mixcol();
            }
            Draw.blend(Blending.additive);
            Draw.color(col);
            Draw.alpha(0.25f + 0.5f * k);
            Fill.circle(e.x, e.y, 5f + 7f * k);
            Draw.color(WHITE);
            Draw.alpha(0.6f + 0.4f * k);
            Fill.circle(e.x, e.y, 1.5f + 2.5f * k);
            Draw.blend();
            Draw.reset();
            return;
        }

        float f = Math.min(1f, (T - T_SHRINK) / (T_FLY - T_SHRINK));
        float head = f * f * (0.4f + 0.6f * f);                       // tăng tốc dần
        float zoom = 1f + 5f * (float) Math.pow(f, 1.5);              // phóng to dần: như rời khỏi quỹ đạo
        float headA = T < T_FLY ? 1f - (float) Math.pow(f, 2.2) : 0f; // mờ dần rồi biến mất
        float fade = T < T_FLY ? 1f : Math.max(0f, 1f - (T - T_FLY) / (FLIGHT_LIFE - T_FLY));
        fade = fade * fade * (3f - 2f * fade);                        // vệt mờ dần mượt

        Draw.blend(Blending.additive);
        final int N = 28;
        for (int pass = 0; pass < 2; pass++) {
            float[] prev = flightPos(e.x, e.y, 0f);
            for (int i = 1; i <= N; i++) {
                float q = (float) i / N;
                float[] cur = flightPos(e.x, e.y, head * q);
                float a = (float) Math.pow(q, 1.6) * fade;
                if (pass == 0) {
                    Draw.color(col);
                    Draw.alpha(a * 0.35f);
                    Lines.stroke((1.5f + 6f * q * (0.6f + 0.4f * zoom)) * (0.4f + 0.6f * fade));
                } else {
                    Draw.color(WHITE);
                    Draw.alpha(a * 0.9f);
                    Lines.stroke((0.4f + 1.6f * q) * fade);
                }
                Lines.line(prev[0], prev[1], cur[0], cur[1], false);
                prev = cur;
            }
        }
        for (int i = 0; i < 10; i++) { // tia lấp lánh dọc vệt
            float q = Mathf.randomSeed(e.id * 13L + i);
            float[] p = flightPos(e.x, e.y, head * q);
            float off = (Mathf.randomSeed(e.id * 29L + i) - 0.5f) * 14f;
            float tw = 0.5f + 0.5f * Mathf.sin(T * 0.35f + i * 2.1f);
            Draw.color(col, WHITE, 0.5f);
            Draw.alpha(q * fade * tw);
            Fill.circle(p[0] - COS45 * off, p[1] + COS45 * off, 0.8f + 1.2f * tw);
        }
        if (headA > 0) { // đốm sáng đầu: to dần + mờ dần
            float[] h = flightPos(e.x, e.y, head);
            float r = 2.5f * zoom;
            Draw.color(col);
            Draw.alpha(0.35f * headA);
            Fill.circle(h[0], h[1], r * 3f);
            Draw.color(col, WHITE, 0.5f);
            Draw.alpha(0.55f * headA);
            Fill.circle(h[0], h[1], r * 1.6f);
            Draw.color(WHITE);
            Draw.alpha(headA);
            Fill.circle(h[0], h[1], r);
        }
        if (T >= T_FLY && T < T_FLY + 12f) { // chớp ngôi sao 2 cánh 0.2s tại điểm biến mất = dịch chuyển đi
            float k = (T - T_FLY) / 12f, o = 1f - k;
            float[] h = flightPos(e.x, e.y, 1f);
            float len = 192f * Mathf.sin(Mathf.PI * (float) Math.pow(k, 0.6)), wd = 14f * o + 2f;
            float dx = COS45, dy = COS45, px = -COS45, py = COS45;
            for (int side = -1; side <= 1; side += 2) {
                Draw.color(col);
                Draw.alpha(0.6f * o);
                Fill.tri(h[0] + px * wd * 2f, h[1] + py * wd * 2f, h[0] - px * wd * 2f, h[1] - py * wd * 2f, h[0] + dx * len * side, h[1] + dy * len * side);
                Draw.color(WHITE);
                Draw.alpha(o);
                Fill.tri(h[0] + px * wd, h[1] + py * wd, h[0] - px * wd, h[1] - py * wd, h[0] + dx * len * 0.75f * side, h[1] + dy * len * 0.75f * side);
            }
            Draw.color(col);
            Draw.alpha(0.5f * o);
            Fill.circle(h[0], h[1], 36f * o);
            Draw.color(WHITE);
            Draw.alpha(o);
            Fill.circle(h[0], h[1], 14f * o);
        }
        Draw.blend();
        Draw.reset();
    });

    // 1b) Sau khi đốm sáng biến mất: laser từ trái-trên bắn xuống tâm khối
    private static final Effect absorbLaser = new Effect(FLIGHT_LIFE, 2600f, e -> {
        Color col = PerfCore.color(String.valueOf(e.data));
        float T = e.fin() * FLIGHT_LIFE;
        if (T < LASER_T0) return;
        float tt = T - LASER_T0, grow = Math.min(1f, tt / 6f);
        float fd = tt < 14 ? 1f : Math.max(0f, 1f - (tt - 14f) / 22f);
        if (fd <= 0) return;
        float sx = e.x - 1500f, sy = e.y + 1700f; // dài gấp 10 lần bản trước
        float ex = sx + (e.x - sx) * grow, ey = sy + (e.y - sy) * grow;
        float w = (1.5f + 4f * fd) * (1f + 0.12f * Mathf.sin(tt * 1.7f));
        Draw.z(Layer.effect + 0.6f);
        Draw.blend(Blending.additive);
        Draw.color(col);
        Draw.alpha(0.25f * fd);
        Lines.stroke(w * 3f);
        Lines.line(sx, sy, ex, ey, false);
        Draw.alpha(0.6f * fd);
        Lines.stroke(w * 1.5f);
        Lines.line(sx, sy, ex, ey, false);
        Draw.color(WHITE);
        Draw.alpha(fd);
        Lines.stroke(w * 0.6f);
        Lines.line(sx, sy, ex, ey, false);
        Draw.color(col);
        Draw.alpha(0.5f * fd);
        Fill.circle(sx, sy, 6f * fd);
        Draw.color(WHITE);
        Draw.alpha(fd);
        Fill.circle(sx, sy, 2.5f * fd);
        if (grow >= 1f) { // điểm chạm ở tâm lò
            Draw.color(col);
            Draw.alpha(0.45f * fd);
            Fill.circle(e.x, e.y, 14f + 10f * fd);
            Draw.color(WHITE);
            Draw.alpha(0.9f * fd);
            Fill.circle(e.x, e.y, 5f + 5f * fd);
        }
        Draw.blend();
        Draw.reset();
    });

    // 2) Bùng nổ sóng xung kích tại tâm (rotation = hệ số kích thước)
    private static final Effect absorbBurst = new Effect(34f, 400f, e -> {
        Color col = PerfCore.color(String.valueOf(e.data));
        float sc = e.rotation > 0 ? e.rotation : 1f, k = e.fin(), o = 1f - k;
        Draw.z(Layer.effect + 0.4f);
        Draw.blend(Blending.additive);
        Draw.color(WHITE);
        Draw.alpha(o * o);
        Fill.circle(e.x, e.y, 14f * sc * o);
        Draw.color(col);
        Draw.alpha(o * 0.45f);
        Fill.circle(e.x, e.y, 24f * sc * (0.3f + 0.7f * k));
        Lines.stroke(2.5f * o * sc);
        Draw.color(col, WHITE, 0.3f);
        Draw.alpha(o * 0.9f);
        Lines.circle(e.x, e.y, 38f * sc * (1f - (float) Math.pow(o, 3)));
        Draw.blend();
        Draw.reset();
    });

    // 3) Hạt bụi sao bay lơ lửng về phía phải-trên quanh lò
    private static final Effect cosmicMote = new Effect(50f, 100f, e -> {
        Color col = PerfCore.color(String.valueOf(e.data));
        float k = e.fin(), o = e.fout();
        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);
        float x = e.x + COS45 * 22f * k, y = e.y + COS45 * 22f * k;
        Draw.color(col);
        Draw.alpha(0.35f * o);
        Lines.stroke(1.2f * o);
        Lines.line(x, y, x - COS45 * 7f * o, y - COS45 * 7f * o, false);
        Draw.color(WHITE);
        Draw.alpha(o * (0.6f + 0.4f * Mathf.sin(e.time)));
        Fill.circle(x, y, 0.4f + 1.1f * o);
        Draw.blend();
        Draw.reset();
    });

    /** Điểm trên vành quỹ đạo elip nghiêng. */
    private static float[] ellipsePt(float cx, float cy, float a, float b, float tilt, float t) {
        float ca = Mathf.cos(tilt), sa = Mathf.sin(tilt), px = Mathf.cos(t) * a, py = Mathf.sin(t) * b;
        return new float[]{cx + px * ca - py * sa, cy + px * sa + py * ca};
    }

    private UnitReactor() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-unit-reactor");
            if (!(b instanceof PayloadVoid)) return;
            final PayloadVoid block = (PayloadVoid) b;

            block.addBar("nex-ur-power", (Building building) -> {
                final ReactorBuild rb = (ReactorBuild) building;
                return new Bar(
                    () -> "⚡ " + Math.round(rb.p * 60f) + " /s",
                    () -> Pal.powerBar,
                    () -> Math.min(1f, rb.p / MAXP)
                );
            });
            for (int i = 0; i < 5; i++) {
                final int k = i;
                block.addBar("nex-ur-t" + (k + 1), (Building building) -> {
                    final ReactorBuild rb = (ReactorBuild) building;
                    return new Bar(
                        () -> "T" + (k + 1) + ": " + rb.c[k] + " / " + CAP_PER_TIER[k],
                        () -> PerfCore.color(TIER_COLORS[k]),
                        () -> (float) rb.c[k] / CAP_PER_TIER[k]
                    );
                });
            }

            block.buildType = () -> new ReactorBuild(block);
        });
    }

    /** Một unit đang chờ laser chạm tâm để cộng điện. */
    private static final class Pending {
        final int t;
        float d;

        Pending(int t, float d) {
            this.t = t;
            this.d = d;
        }
    }

    /** PayloadVoidBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class ReactorBuild extends PayloadVoid.PayloadVoidBuild {
        private final PayloadVoid reactor;

        /** Số unit đã hấp thụ mỗi cấp. */
        final int[] c = {0, 0, 0, 0, 0};
        /** Số unit đang chờ áp dụng mỗi cấp. */
        final int[] pc = {0, 0, 0, 0, 0};
        /** Điện sinh ra mỗi tick. */
        float p = 0f;
        final Seq<Pending> q = new Seq<>();
        float burstTimer = 0f;

        public ReactorBuild(PayloadVoid outer) {
            outer.super();
            this.reactor = outer;
        }

        void recalc() {
            float sum = 0f;
            boolean all = true;
            for (int i = 0; i < 5; i++) {
                sum += c[i] * POWER_PER_TIER[i];
                if (c[i] <= 0) all = false;
            }
            p = all ? sum * SYNERGY : sum;
        }

        @Override
        public boolean acceptPayload(Building source, Payload payload) {
            if (this.payload != null || !enabled) return false;
            if (!(payload instanceof UnitPayload)) return false;
            int t = tierOf(((UnitPayload) payload).unit.type);
            return c[t - 1] + pc[t - 1] < CAP_PER_TIER[t - 1];
        }

        @Override
        public void updateTile() {
            float f = Math.min(1f, p / MAXP);
            if (!Vars.headless && p > 0) {
                if (Mathf.chance(0.04 + 0.18 * f)) {
                    int ti = (int) Math.floor(Mathf.random(5f));
                    if (ti >= 0 && ti < 5 && c[ti] > 0) {
                        cosmicMote.at(x + Mathf.range(15f), y + Mathf.range(15f), 0f, TIER_COLORS[ti]);
                    }
                }
                burstTimer += Time.delta;
                if (burstTimer > 200f - 90f * f) {
                    burstTimer = 0f;
                    absorbBurst.at(x, y, 0.45f + 0.4f * f, "66ccff");
                }
            }
            for (int i = q.size - 1; i >= 0; i--) { // áp dụng điện khi laser chạm tâm
                Pending it = q.get(i);
                it.d -= Time.delta;
                if (it.d <= 0) {
                    q.remove(i);
                    pc[it.t - 1]--;
                    c[it.t - 1]++;
                    recalc();
                    if (!Vars.headless) absorbBurst.at(x, y, 0.6f + 0.15f * it.t, TIER_COLORS[it.t - 1]);
                    try {
                        Effect.shake(1f + it.t * 1.2f, 10f + it.t * 3f, x, y);
                    } catch (Throwable ignored) {
                    }
                }
            }
            if (payload == null) return;
            if (moveInPayload(false)) {
                Unit u = ((UnitPayload) payload).unit;
                UnitType ty = u.type;
                int t = tierOf(ty);
                if (c[t - 1] + pc[t - 1] < CAP_PER_TIER[t - 1]) {
                    pc[t - 1]++;
                    q.add(new Pending(t, APPLY_DELAY));
                    payload = null;
                    TextureRegion ic = iconOf(ty);
                    float w = 16f, h = 16f;
                    if (ic != null) {
                        w = ic.width / 4f;
                        h = ic.height / 4f;
                    }
                    absorbFlight.at(x, y, 0f, new FlightData(ic, w, h, u.rotation, TIER_COLORS[t - 1]));
                    absorbLaser.at(x, y, 0f, TIER_COLORS[t - 1]);
                }
            }
        }

        @Override
        public float getPowerProduction() {
            return enabled ? p : 0f;
        }

        @Override
        public void draw() {
            Draw.rect(reactor.region, x, y);
            Draw.z(Layer.blockOver);
            payRotation = 0f;
            drawPayload();
            Draw.z(Layer.blockOver + 0.1f);
            Draw.rect(reactor.topRegion, x, y);

            float f = Math.min(1f, p / MAXP), tm = Time.time;
            float pulse = 1f + 0.08f * Mathf.sin(tm / 18f), cx = x, cy = y;
            float live = p > 0 ? 1f : 0f, glow = 0.25f + 0.75f * live;
            Draw.z(Layer.blockOver + 0.2f);
            Draw.blend(Blending.additive);

            // quỹ đạo elip nghiêng cho từng cấp: sáng theo số lượng, hành tinh = unit đã nạp
            for (int i = 0; i < 5; i++) {
                float fill = (float) c[i] / CAP_PER_TIER[i], A = 8f + i * 2.2f, B = A * (0.3f + 0.04f * i);
                float tilt = (i * 0.63f) + tm * (0.004f + 0.002f * i) * (i % 2 != 0 ? -1f : 1f);
                Color col = PerfCore.color(TIER_COLORS[i]);
                Draw.color(col);
                Draw.alpha(0.08f + 0.4f * fill);
                Lines.stroke(0.8f + 0.7f * fill);
                float[] pv = ellipsePt(cx, cy, A, B, tilt, 0f);
                for (int j = 1; j <= 32; j++) {
                    float[] pt = ellipsePt(cx, cy, A, B, tilt, j / 32f * 6.2832f);
                    Lines.line(pv[0], pv[1], pt[0], pt[1], false);
                    pv = pt;
                }
                int n = Math.min(4, (int) Math.ceil(fill * 4f));
                for (int j = 0; j < n; j++) {
                    float[] pt = ellipsePt(cx, cy, A, B, tilt, tm * (0.03f - 0.004f * i) + j * 6.2832f / n);
                    Draw.color(col);
                    Draw.alpha(0.5f);
                    Fill.circle(pt[0], pt[1], 2f + i * 0.25f);
                    Draw.color(WHITE);
                    Draw.alpha(0.9f);
                    Fill.circle(pt[0], pt[1], 0.9f + i * 0.1f);
                }
            }

            // lõi sao: nhiều lớp hào quang xanh -> tím -> trắng
            float R = (3.5f + 5f * f) * pulse;
            Draw.color(PerfCore.color("5a3cff"));
            Draw.alpha(0.12f * glow);
            Fill.circle(cx, cy, R * 3.4f);
            Draw.color(PerfCore.color("8a6bff"));
            Draw.alpha(0.2f * glow);
            Fill.circle(cx, cy, R * 2.3f);
            Draw.color(PerfCore.color("66ccff"));
            Draw.alpha(0.45f * glow);
            Fill.circle(cx, cy, R * 1.5f);
            Draw.color(WHITE);
            Draw.alpha(0.9f * glow);
            Fill.circle(cx, cy, R * 0.8f);

            // tia sáng xoay + lens flare chữ thập
            Draw.color(PerfCore.color("9fe3ff"));
            Lines.stroke(0.7f);
            for (int i = 0; i < 10; i++) {
                Draw.alpha((0.12f + 0.25f * f) * glow * (0.6f + 0.4f * Mathf.sin(tm / 12f + i)));
                Lines.lineAngle(cx, cy, tm * 0.4f + i * 36f, 14f + 30f * f + (i % 2) * 10f);
            }
            Draw.color(WHITE);
            Lines.stroke(1.1f);
            Draw.alpha((0.25f + 0.35f * f) * glow);
            float L = 22f + 55f * f * pulse;
            Lines.lineAngle(cx, cy, 0f, L);
            Lines.lineAngle(cx, cy, 180f, L);
            Lines.lineAngle(cx, cy, 90f, L * 0.55f);
            Lines.lineAngle(cx, cy, 270f, L * 0.55f);
            Draw.blend();
            Draw.reset();
        }

        @Override
        public void drawLight() {
            float f = Math.min(1f, p / MAXP);
            Drawf.light(x, y, 50f + 110f * f, PerfCore.color("66ccff"), 0.45f + 0.4f * f);
        }

        @Override
        public void onDestroyed() {
            float pw = p;
            super.onDestroyed();
            if (pw > 0) {
                Damage.dynamicExplosion(x, y, 0f, Math.min(pw * 2f, 3000f), Math.min(pw * 10f, 20000f), Vars.tilesize * 5f, Vars.state.rules.damageExplosions);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(1);
            for (int i = 0; i < 5; i++) write.s(c[i] + pc[i]);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            if (read.b() >= 1) {
                for (int i = 0; i < 5; i++) c[i] = read.s();
                recalc();
            }
        }
    }
}
