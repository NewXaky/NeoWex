package newex;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.entities.Effect;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import arc.graphics.Blending;
import mindustry.ui.Fonts;

import java.util.HashMap;
import java.util.Map;

/** Hiện thanh máu cho đơn vị trong 5 giây sau khi bị thương (thay show-hp.js). Cài đặt: "newex-hp-style" = show-hp | hp | off. */
public final class ShowHp {
    static final String STYLE_KEY = "newex-hp-style";
    static final float SHOW_TIME = 300f;            // 5s sau lần mất/hồi máu cuối
    static final float SHATTER_LIFE = 18f, CRACK_FRAC = 0.22f;

    static final Color BG_BAR = Color.valueOf("0d0d11");
    static final Color BG_BAR_SMALL = Color.valueOf("1f1f1f");
    static final Color SHIELD = Color.valueOf("4ba3e3");
    static final Color RED = Color.valueOf("ff0000"), YELLOW = Color.valueOf("ffff00"), GREEN = Color.valueOf("00ff00");

    static final Map<Integer, Float> smoothHp = new HashMap<>();
    static final Map<Integer, Float> smoothShield = new HashMap<>();
    static final Map<Integer, Float> lastHp = new HashMap<>();
    static final Map<Integer, Float> lastDamageTime = new HashMap<>();
    static final Map<Integer, String> textCache = new HashMap<>();
    static final Map<Integer, Float> textCacheHp = new HashMap<>();

    static final Rect rect = new Rect();

    /** Dữ liệu truyền cho hiệu ứng vỡ thanh máu. */
    static class ShatterData {
        float w, h, fillW, hx;
        Color col;
        ShatterData(float w, float h, float fillW, float hx, Color col) {
            this.w = w; this.h = h; this.fillW = fillW; this.hx = hx; this.col = col;
        }
    }

    static final Effect shatterFx = new Effect(SHATTER_LIFE, 200f, e -> {
        if (!(e.data instanceof ShatterData)) return;
        ShatterData d = (ShatterData) e.data;
        float W = d.w, H = d.h, k = e.fin(), hx = d.hx;
        Color hpCol = d.col;
        float fillL = -W / 2f + 0.8f, fillR = fillL + d.fillW;
        Draw.z(117f);

        // Giai đoạn rạn nứt: thanh còn nguyên + vết nứt tỏa ra từ điểm va
        if (k < CRACK_FRAC) {
            float q = k / CRACK_FRAC;
            Draw.color(BG_BAR); Draw.alpha(0.8f); Fill.rect(e.x, e.y, W, H);
            Draw.color(hpCol); Draw.alpha(0.9f); Fill.rect(e.x + (fillL + fillR) / 2f, e.y, d.fillW, H - 1.6f);
            Draw.color(Color.white); Lines.stroke(0.5f);
            for (int i = 0; i < 8; i++) {
                float a = i * 45f + Mathf.randomSeed(e.id * 7L + i) * 30f;
                float len = (3f + Mathf.randomSeed(e.id * 11L + i) * 9f) * q;
                Draw.alpha(0.9f);
                Lines.lineAngle(e.x + hx, e.y, a, len);
            }
            Draw.alpha(0.8f * (1f - q)); Fill.circle(e.x + hx, e.y, 2.2f * (1f - q) + 0.6f);
            Draw.reset();
            return;
        }

        float t = (k - CRACK_FRAC) / (1f - CRACK_FRAC);   // 0..1 sau khi vỡ
        float tau = (k - CRACK_FRAC) * SHATTER_LIFE;      // tick kể từ lúc vỡ
        float alpha = 1f - (float) Math.pow(t, 1.6f);
        int cols = Math.max(5, Math.round(W / 3.6f)), rows = 2;
        float cw = W / cols, rh = H / rows;

        // Lưới đỉnh lệch ngẫu nhiên (cố định theo id) để mảnh kính không đều
        float[] gx = new float[(rows + 1) * (cols + 1)], gy = new float[(rows + 1) * (cols + 1)];
        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                boolean inner = c > 0 && c < cols, innerY = r > 0 && r < rows;
                int n = r * (cols + 1) + c;
                gx[n] = -W / 2f + c * cw + (inner ? (Mathf.randomSeed(e.id * 131L + c * 17L + r * 31L) - 0.5f) * cw * 0.7f : 0f);
                gy[n] = -H / 2f + r * rh + (innerY ? (Mathf.randomSeed(e.id * 137L + c * 19L + r * 29L) - 0.5f) * rh * 0.7f : 0f);
            }
        }

        float[] vx = new float[3], vy = new float[3];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int[][] tris = {
                    {r * (cols + 1) + c, r * (cols + 1) + c + 1, (r + 1) * (cols + 1) + c},
                    {r * (cols + 1) + c + 1, (r + 1) * (cols + 1) + c + 1, (r + 1) * (cols + 1) + c}
                };
                for (int ti = 0; ti < 2; ti++) {
                    int[] tr = tris[ti];
                    long seed = e.id * 193L + (r * cols + c) * 2L + ti;
                    float cxl = (gx[tr[0]] + gx[tr[1]] + gx[tr[2]]) / 3f, cyl = (gy[tr[0]] + gy[tr[1]] + gy[tr[2]]) / 3f;
                    // Hướng bay: từ điểm va ra ngoài + trọng lực kéo xuống
                    float dx = cxl - hx, dy = cyl + 0.6f;
                    float dl = Math.max(0.5f, (float) Math.sqrt(dx * dx + dy * dy));
                    float sp = 0.35f + Mathf.randomSeed(seed) * 0.9f;
                    float px = cxl + (dx / dl) * sp * tau * 1.1f;
                    float py = cyl + (dy / dl) * sp * tau * 0.8f - 0.045f * tau * tau;
                    float rot = (Mathf.randomSeed(seed + 5) - 0.5f) * 0.35f * tau;
                    float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
                    float sc = 1f - 0.3f * t;
                    for (int v = 0; v < 3; v++) {
                        float ox = (gx[tr[v]] - cxl) * sc, oy = (gy[tr[v]] - cyl) * sc;
                        vx[v] = e.x + px + ox * cr - oy * sr;
                        vy[v] = e.y + py + ox * sr + oy * cr;
                    }
                    boolean inFill = cxl >= fillL && cxl <= fillR && Math.abs(cyl) < H / 2f - 0.8f;
                    Draw.color(inFill ? hpCol : BG_BAR); Draw.alpha((inFill ? 0.95f : 0.8f) * alpha);
                    Fill.tri(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2]);
                    Draw.color(Color.white); Draw.alpha(0.55f * alpha); Lines.stroke(0.35f);
                    Lines.line(vx[0], vy[0], vx[1], vy[1]);
                    Lines.line(vx[1], vy[1], vx[2], vy[2]);
                    Lines.line(vx[2], vy[2], vx[0], vy[0]);
                }
            }
        }

        // Chớp sáng lúc vỡ
        if (t < 0.35f) {
            float fl = 1f - t / 0.35f;
            Draw.blend(Blending.additive);
            Draw.color(Color.white); Draw.alpha(0.8f * fl); Fill.circle(e.x + hx, e.y, 2.5f * fl + 0.5f);
            Lines.stroke(0.6f); Draw.alpha(0.6f * fl);
            Lines.lineAngle(e.x + hx, e.y, 0f, 7f * fl); Lines.lineAngle(e.x + hx, e.y, 180f, 7f * fl);
            Lines.lineAngle(e.x + hx, e.y, 90f, 4f * fl); Lines.lineAngle(e.x + hx, e.y, 270f, 4f * fl);
            Draw.blend();
        }
        Draw.reset();
    });

    private ShowHp() {}

    /** Gọi 1 lần từ NexMod. */
    public static void init() {
        // Xóa bộ nhớ đệm khi đổi map / bắt đầu lại ván
        Events.on(WorldLoadEvent.class, e -> clearCache());
        Events.on(StateChangeEvent.class, e -> {
            if (e.to != GameState.State.playing || e.from == GameState.State.paused) return;
            clearCache();
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit == null) return;
            Unit u = e.unit;
            int id = u.id;
            String style = style();
            float lastDmg = lastDamageTime.getOrDefault(id, -9999f);
            if (!style.equals("off") && Time.time - lastDmg <= SHOW_TIME && smoothHp.containsKey(id) && !Vars.headless) {
                float pct = Math.max(0f, smoothHp.get(id) / u.maxHealth * 100f);
                float W, H, by;
                Color col;
                if (style.equals("show-hp")) {
                    W = Math.max(22f, u.hitSize * 1.2f) + 1.6f; H = 5.6f; by = u.y + u.hitSize / 2f + 6f;
                    col = pct < 25f ? RED : pct < 55f ? YELLOW : GREEN;
                } else {
                    W = Math.max(18f, u.hitSize) + 1.2f; H = 4.2f; by = u.y + u.hitSize / 2f + 4f;
                    col = pct < 20f ? RED : pct < 50f ? YELLOW : GREEN;
                }
                float fillW = Math.max(0.5f, (W - 1.6f) * Math.min(1f, pct / 100f));
                float hx = Math.max(-W / 2f + 2f, Math.min(W / 2f - 2f, -W / 2f + 0.8f + fillW * 0.85f));
                shatterFx.at(u.x, by, 0f, new ShatterData(W, H, fillW, hx, col));
            }
            forget(id);
        });

        Events.run(mindustry.game.EventType.Trigger.draw, ShowHp::drawBars);
    }

    static String style() {
        return Core.settings.getString(STYLE_KEY, "show-hp");
    }

    static void clearCache() {
        smoothHp.clear(); smoothShield.clear(); lastHp.clear(); lastDamageTime.clear(); textCache.clear(); textCacheHp.clear();
    }

    static void forget(int id) {
        smoothHp.remove(id); smoothShield.remove(id); lastHp.remove(id); lastDamageTime.remove(id); textCache.remove(id); textCacheHp.remove(id);
    }

    /** Độ cao (từ tâm đơn vị) tới đỉnh phần hiển thị máu, để số sát thương xếp ngay trên mà không đè.
     *  Dùng bởi DamageDisplay. */
    public static float hpTop(float size) {
        String style = style();
        if (style.equals("off")) return size / 2f + 4f;
        Font font = Fonts.outline;
        float ox = font.getData().scaleX, oy = font.getData().scaleY;
        font.getData().setScale(style.equals("hp") ? 0.11f * (size / 8f) : Math.min(0.2f, Math.max(0.1f, 0.08f * (size / 8f))));
        float ch = font.getCapHeight();
        font.getData().setScale(ox, oy);
        return size / 2f + (style.equals("hp") ? 4f + 4.5f + ch : 6f + 6.5f + ch);
    }

    static String formatHP(float value) {
        int val = Math.max(0, (int) Math.floor(value));
        if (val >= 1000000) {
            float m = val / 1000000f;
            return (m % 1f == 0f ? String.valueOf((int) m) : String.format("%.1f", m)) + "M";
        } else if (val >= 1000) {
            float k = val / 1000f;
            return (k % 1f == 0f ? String.valueOf((int) k) : String.format("%.1f", k)) + "k";
        }
        return String.valueOf(val);
    }

    static void drawBars() {
        String style = style();
        if (style.equals("off") || Vars.headless) return;

        Rect bounds = Core.camera.bounds(rect);
        Groups.unit.intersect(bounds.x, bounds.y, bounds.width, bounds.height, (Cons<Unit>) u -> drawUnit(u, style));
    }

    static void drawUnit(Unit u, String style) {
        if (u == null || !u.isValid() || u.dead || !u.isAdded()) return;

        int id = u.id;
        float curHp = u.health, maxHp = u.maxHealth, curShield = u.shield;

        if (!lastHp.containsKey(id)) lastHp.put(id, curHp);
        if (Math.abs(curHp - lastHp.get(id)) > 0.1f) {
            lastDamageTime.put(id, Time.time);
            lastHp.put(id, curHp);
        }

        float timeDiff = Time.time - lastDamageTime.getOrDefault(id, -9999f);
        if (timeDiff > SHOW_TIME) {
            smoothHp.remove(id); smoothShield.remove(id); textCache.remove(id); textCacheHp.remove(id);
            return;
        }

        float smoothHpVal = smoothHp.getOrDefault(id, curHp);
        if (!Vars.state.isPaused()) {
            smoothHpVal = Mathf.lerpDelta(smoothHpVal, curHp, 0.1f);
            smoothHp.put(id, smoothHpVal);
        }
        float pct = Math.max(0f, smoothHpVal / maxHp * 100f);

        if (style.equals("show-hp")) {
            float sh = smoothShield.getOrDefault(id, curShield);
            if (!Vars.state.isPaused()) {
                sh = Mathf.lerpDelta(sh, curShield, 0.1f);
                smoothShield.put(id, sh);
            }

            float cx = u.x;
            float baseY = u.y + u.hitSize / 2f + 6f;
            float barW = Math.max(22f, u.hitSize * 1.2f), barH = 4f, halfW = barW / 2f;

            Draw.z(115f);
            Draw.color(BG_BAR, 0.8f);
            Fill.rect(cx, baseY, barW + 1.6f, barH + 1.6f);

            Color hpColor = pct < 25f ? Color.red : pct < 55f ? Color.yellow : Color.green;
            Draw.color(hpColor);
            Draw.alpha(pct < 20f ? 0.6f + Mathf.absin(Time.time, 5f, 0.4f) : 0.9f);
            float curW = barW * (Math.max(0f, pct) / 100f);
            Fill.rect(cx - halfW + curW / 2f, baseY, curW, barH);

            if (sh > 0.5f) {
                float shW = Math.min(barW, barW * (sh / maxHp));
                Draw.color(SHIELD, 0.85f);
                Fill.rect(cx - halfW + shW / 2f, baseY + barH / 2f + 0.8f, shW, 1.2f);
            }

            Draw.z(116f);
            Draw.color(Color.white, 0.45f);
            Fill.rect(cx, baseY + barH / 2f - 0.5f, barW, 0.8f);

            Font font = Fonts.outline;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            font.getData().setScale(Math.min(0.2f, Math.max(0.1f, 0.08f * (u.hitSize / 8f))));
            font.setColor(hpColor);

            String text = textCache.get(id);
            Float cachedHp = textCacheHp.get(id);
            if (text == null || cachedHp == null || Math.abs(cachedHp - smoothHpVal) > 5f) {
                text = formatHP(smoothHpVal) + " / " + formatHP(maxHp);
                textCache.put(id, text);
                textCacheHp.put(id, smoothHpVal);
            }
            font.draw(text, cx, baseY + barH / 2f + 3.5f + font.getCapHeight(), 0, Align.center, false);

            font.getData().setScale(oldX, oldY);
            Draw.reset();
        } else if (style.equals("hp")) {
            int hpPct = (int) Math.floor(pct);
            float x = u.x, baseY = u.y + u.hitSize / 2f + 4f;
            Color hpColor = hpPct < 20 ? Color.red : hpPct < 50 ? Color.yellow : Color.green;
            float barW = Math.max(18f, u.hitSize), barH = 3f;

            Draw.z(115f);
            Draw.color(BG_BAR_SMALL, 0.85f);
            Fill.rect(x, baseY, barW + 1.2f, barH + 1.2f);
            Draw.color(hpColor);
            if (hpPct <= 20) Draw.alpha(0.5f + Mathf.absin(Time.time, 6f, 0.5f));
            float w = barW * Math.max(0f, pct) / 100f;
            Fill.rect(x - barW / 2f + w / 2f, baseY, w, barH);

            Draw.z(116f);
            Font font = Fonts.outline;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            font.getData().setScale(0.11f * (u.hitSize / 8f));
            font.setColor(hpColor);
            font.draw(hpPct + "%", x, baseY + barH / 2f + 2.5f + font.getCapHeight(), 0, Align.center, false);
            font.getData().setScale(oldX, oldY);
            Draw.reset();
        }
    }
}
