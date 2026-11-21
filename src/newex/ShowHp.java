package newex;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.struct.IntFloatMap;
import arc.struct.IntMap;
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
import mindustry.ui.Fonts;

/** Hiện thanh máu cho đơn vị trong 5 giây sau khi bị thương (thay show-hp.js). Cài đặt: "newex-hp-style" = show-hp | hp | off. */
public final class ShowHp {
    static final String STYLE_KEY = "newex-hp-style";
    static final String MAXHP_KIND = "newex-maxhp-sync";
    static final float SHOW_TIME = 300f;            // 5s sau lần mất/hồi máu cuối
    static final float SHATTER_LIFE = 18f, CRACK_FRAC = 0.22f;
    static final float MISSING = -1e30f;

    static final Color BG_BAR = Color.valueOf("0d0d11");
    static final Color BG_BAR_SMALL = Color.valueOf("1f1f1f");
    static final Color SHIELD = Color.valueOf("4ba3e3");
    static final Color RED = Color.valueOf("ff0000"), YELLOW = Color.valueOf("ffff00"), GREEN = Color.valueOf("00ff00");

    // Dùng IntFloatMap / IntMap của Arc -> KHÔNG cấp phát Integer/Float mỗi khung hình (cắt toàn bộ rác GC!)
    static final IntFloatMap smoothHp = new IntFloatMap();
    static final IntFloatMap smoothShield = new IntFloatMap();
    static final IntFloatMap lastHp = new IntFloatMap();
    static final IntFloatMap lastDamageTime = new IntFloatMap();
    static final IntMap<String> textCache = new IntMap<>();
    static final IntFloatMap textCacheHp = new IntFloatMap();
    static final IntFloatMap textCacheMax = new IntFloatMap();
    static final IntFloatMap peakMaxHp = new IntFloatMap();
    static final IntFloatMap syncedMaxHp = new IntFloatMap();
    static boolean netRegistered = false;
    static float maxHpSyncAcc = 0f;
    static int maxHpFullTick = 0;

    static final Rect rect = new Rect();
    // Mảng dùng lại cho shatterFx (tránh new float[]/int[][] hàng nghìn lần khi nhiều quái chết)
    static final int MAX_SHATTER_COLS = 14;
    static final float[] SHATTER_GX = new float[3 * (MAX_SHATTER_COLS + 1)];
    static final float[] SHATTER_GY = new float[3 * (MAX_SHATTER_COLS + 1)];
    static final float[] SHATTER_VX = new float[3];
    static final float[] SHATTER_VY = new float[3];
    static final int[] SHATTER_TRI = new int[3];

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
            int cracks = Vars.mobile ? 5 : 8;
            for (int i = 0; i < cracks; i++) {
                float a = i * (360f / cracks) + Mathf.randomSeed(e.id * 7L + i) * 30f;
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
        int maxCols = Vars.mobile ? 6 : MAX_SHATTER_COLS;
        int cols = Mathf.clamp(Math.round(W / 3.6f), 4, maxCols);
        int rows = 2;
        float cw = W / cols, rh = H / rows;

        // Lưới đỉnh lệch ngẫu nhiên (cố định theo id) dùng mảng tĩnh không cấp phát
        for (int r = 0; r <= rows; r++) {
            for (int c = 0; c <= cols; c++) {
                boolean inner = c > 0 && c < cols, innerY = r > 0 && r < rows;
                int n = r * (cols + 1) + c;
                SHATTER_GX[n] = -W / 2f + c * cw + (inner ? (Mathf.randomSeed(e.id * 131L + c * 17L + r * 31L) - 0.5f) * cw * 0.7f : 0f);
                SHATTER_GY[n] = -H / 2f + r * rh + (innerY ? (Mathf.randomSeed(e.id * 137L + c * 19L + r * 29L) - 0.5f) * rh * 0.7f : 0f);
            }
        }

        boolean drawEdges = !Vars.mobile && PerfCore.level() == 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i00 = r * (cols + 1) + c;
                int i01 = i00 + 1;
                int i10 = (r + 1) * (cols + 1) + c;
                int i11 = i10 + 1;
                for (int ti = 0; ti < 2; ti++) {
                    if (ti == 0) { SHATTER_TRI[0] = i00; SHATTER_TRI[1] = i01; SHATTER_TRI[2] = i10; }
                    else { SHATTER_TRI[0] = i01; SHATTER_TRI[1] = i11; SHATTER_TRI[2] = i10; }
                    long seed = e.id * 193L + (r * cols + c) * 2L + ti;
                    float cxl = (SHATTER_GX[SHATTER_TRI[0]] + SHATTER_GX[SHATTER_TRI[1]] + SHATTER_GX[SHATTER_TRI[2]]) / 3f;
                    float cyl = (SHATTER_GY[SHATTER_TRI[0]] + SHATTER_GY[SHATTER_TRI[1]] + SHATTER_GY[SHATTER_TRI[2]]) / 3f;
                    float dx = cxl - hx, dy = cyl + 0.6f;
                    float dl = Math.max(0.5f, (float) Math.sqrt(dx * dx + dy * dy));
                    float sp = 0.35f + Mathf.randomSeed(seed) * 0.9f;
                    float px = cxl + (dx / dl) * sp * tau * 1.1f;
                    float py = cyl + (dy / dl) * sp * tau * 0.8f - 0.045f * tau * tau;
                    float rot = (Mathf.randomSeed(seed + 5) - 0.5f) * 0.35f * tau;
                    float cr = Mathf.cos(rot), sr = Mathf.sin(rot);
                    float sc = 1f - 0.3f * t;
                    for (int v = 0; v < 3; v++) {
                        float ox = (SHATTER_GX[SHATTER_TRI[v]] - cxl) * sc, oy = (SHATTER_GY[SHATTER_TRI[v]] - cyl) * sc;
                        SHATTER_VX[v] = e.x + px + ox * cr - oy * sr;
                        SHATTER_VY[v] = e.y + py + ox * sr + oy * cr;
                    }
                    boolean inFill = cxl >= fillL && cxl <= fillR && Math.abs(cyl) < H / 2f - 0.8f;
                    Draw.color(inFill ? hpCol : BG_BAR); Draw.alpha((inFill ? 0.95f : 0.8f) * alpha);
                    Fill.tri(SHATTER_VX[0], SHATTER_VY[0], SHATTER_VX[1], SHATTER_VY[1], SHATTER_VX[2], SHATTER_VY[2]);
                    if (drawEdges) {
                        Draw.color(Color.white); Draw.alpha(0.55f * alpha); Lines.stroke(0.35f);
                        Lines.line(SHATTER_VX[0], SHATTER_VY[0], SHATTER_VX[1], SHATTER_VY[1]);
                        Lines.line(SHATTER_VX[1], SHATTER_VY[1], SHATTER_VX[2], SHATTER_VY[2]);
                        Lines.line(SHATTER_VX[2], SHATTER_VY[2], SHATTER_VX[0], SHATTER_VY[0]);
                    }
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
            boolean skipShatter = PerfCore.level() >= 2 || (Vars.mobile && PerfCore.level() >= 1);
            if (!skipShatter && !Vars.headless) {
                String style = style();
                float lastDmg = lastDamageTime.get(id, -9999f);
                if (!style.equals("off") && Time.time - lastDmg <= SHOW_TIME && smoothHp.containsKey(id) && PerfCore.onScreen(u.x, u.y, 40f)) {
                    float pct = Math.max(0f, smoothHp.get(id, 0f) / effectiveMaxHp(u) * 100f);
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
            }
            forget(id);
        });

        Events.run(mindustry.game.EventType.Trigger.update, ShowHp::syncMaxHealthTick);
        Events.run(mindustry.game.EventType.Trigger.draw, ShowHp::drawBars);
    }

    static void ensureNetRegistered() {
        if (netRegistered || Vars.netClient == null) return;
        netRegistered = true;
        try {
            Vars.netClient.addPacketHandler(MAXHP_KIND, content -> {
                if (content == null || content.isEmpty()) return;
                String[] parts = content.split(",");
                for (String p : parts) {
                    int c = p.indexOf(':');
                    if (c <= 0) continue;
                    try {
                        int id = Integer.parseInt(p.substring(0, c));
                        float mh = Float.parseFloat(p.substring(c + 1));
                        if (mh > 0f) {
                            peakMaxHp.put(id, mh);
                            Unit u = Groups.unit.getByID(id);
                            if (u != null && u.isValid()) u.maxHealth = mh;
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /** Host gửi maxHealth của các unit có máu tối đa khác mặc định sang client (Mindustry mặc định chỉ sync u.health, không sync u.maxHealth). */
    static void syncMaxHealthTick() {
        ensureNetRegistered();
        if (!Vars.net.server() || Vars.state == null || !Vars.state.isGame() || Groups.unit.isEmpty()) return;
        maxHpSyncAcc += Time.delta;
        if (maxHpSyncAcc < 20f) return;
        maxHpSyncAcc = 0f;
        boolean full = (++maxHpFullTick >= 9); // gửi lại toàn bộ mỗi ~3s cho client mới vào
        if (full) maxHpFullTick = 0;
        final StringBuilder sb = new StringBuilder();
        Groups.unit.each(u -> {
            if (u == null || u.dead || !u.isValid() || u.type == null) return;
            boolean hasSynced = syncedMaxHp.containsKey(u.id);
            if (Math.abs(u.maxHealth - u.type.health) <= 1f && !hasSynced) return;
            float prev = syncedMaxHp.get(u.id, MISSING);
            if (!full && prev > MISSING * 0.5f && Math.abs(prev - u.maxHealth) <= 1f) return;
            syncedMaxHp.put(u.id, u.maxHealth);
            if (sb.length() > 0) sb.append(',');
            sb.append(u.id).append(':').append(Math.round(u.maxHealth));
            if (sb.length() > 1400) {
                try { mindustry.gen.Call.clientPacketUnreliable(MAXHP_KIND, sb.toString()); } catch (Throwable ignored) {}
                sb.setLength(0);
            }
        });
        if (sb.length() > 0) {
            try { mindustry.gen.Call.clientPacketUnreliable(MAXHP_KIND, sb.toString()); } catch (Throwable ignored) {}
        }
    }

    /** Máu tối đa hiệu dụng: trên client giữ mốc cao nhất đã nhận (hoặc giá trị host sync) để không bị tụt cùng máu hiện tại. */
    static float effectiveMaxHp(Unit u) {
        float curHp = Math.max(0f, u.health);
        float maxHp = u.maxHealth;
        if (!(maxHp > 0f)) maxHp = Math.max(1f, curHp);
        if (Vars.net.client()) {
            float peak = peakMaxHp.get(u.id, 0f);
            if (maxHp > peak) peak = maxHp;
            if (curHp > peak) peak = curHp;
            if (peak > 0f) {
                peakMaxHp.put(u.id, peak);
                maxHp = peak;
            }
        } else if (curHp > maxHp) {
            maxHp = curHp;
        }
        return Math.max(1f, maxHp);
    }

    static String style() {
        return Core.settings.getString(STYLE_KEY, "show-hp");
    }

    static void clearCache() {
        smoothHp.clear(); smoothShield.clear(); lastHp.clear(); lastDamageTime.clear();
        textCache.clear(); textCacheHp.clear(); textCacheMax.clear(); peakMaxHp.clear(); syncedMaxHp.clear();
        maxHpSyncAcc = 0f; maxHpFullTick = 0;
    }

    static void forget(int id) {
        smoothHp.remove(id, 0f); smoothShield.remove(id, 0f); lastHp.remove(id, 0f); lastDamageTime.remove(id, 0f);
        textCache.remove(id); textCacheHp.remove(id, 0f); textCacheMax.remove(id, 0f); peakMaxHp.remove(id, 0f); syncedMaxHp.remove(id, 0f);
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
            int t = Math.round(val / 100000f);
            return (t % 10 == 0 ? String.valueOf(t / 10) : (t / 10) + "." + (t % 10)) + "M";
        } else if (val >= 1000) {
            int t = Math.round(val / 100f);
            return (t % 10 == 0 ? String.valueOf(t / 10) : (t / 10) + "." + (t % 10)) + "k";
        }
        return String.valueOf(val);
    }

    private static String activeStyle = "show-hp";
    private static final Cons<Unit> drawUnitCons = u -> drawUnit(u, activeStyle);

    static void drawBars() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame() || Groups.unit.isEmpty()) return;
        if (Core.camera != null && Core.camera.width > 1600f) return;
        String style = style();
        if (style.equals("off")) return;
        activeStyle = style;

        Rect bounds = Core.camera.bounds(rect);
        Groups.unit.intersect(bounds.x, bounds.y, bounds.width, bounds.height, drawUnitCons);
    }

    static void drawUnit(Unit u, String style) {
        if (u == null || !u.isValid() || u.dead || !u.isAdded()) return;

        int id = u.id;
        float curHp = u.health, maxHp = effectiveMaxHp(u), curShield = u.shield;

        float prevHp = lastHp.get(id, MISSING);
        if (prevHp <= MISSING * 0.5f) {
            lastHp.put(id, curHp);
            prevHp = curHp;
        }
        if (Math.abs(curHp - prevHp) > 0.1f) {
            lastDamageTime.put(id, Time.time);
            lastHp.put(id, curHp);
        }

        float timeDiff = Time.time - lastDamageTime.get(id, -9999f);
        if (timeDiff > SHOW_TIME) {
            if (smoothHp.containsKey(id)) {
                smoothHp.remove(id, 0f); smoothShield.remove(id, 0f); textCache.remove(id); textCacheHp.remove(id, 0f);
            }
            return;
        }

        float smoothHpVal = smoothHp.get(id, curHp);
        if (!Vars.state.isPaused()) {
            // Máu tăng lên đột ngột do sync -> cập nhật nhanh; mất máu -> trượt vệt trắng mượt
            smoothHpVal = curHp > smoothHpVal ? curHp : Mathf.lerpDelta(smoothHpVal, curHp, 0.14f);
            smoothHp.put(id, smoothHpVal);
        }
        float realPct = Math.max(0f, Math.min(100f, curHp / maxHp * 100f));
        float pct = Math.max(realPct, Math.min(100f, smoothHpVal / maxHp * 100f));

        if (style.equals("show-hp")) {
            float sh = smoothShield.get(id, curShield);
            if (!Vars.state.isPaused()) {
                sh = Mathf.lerpDelta(sh, curShield, 0.15f);
                smoothShield.put(id, sh);
            }

            float cx = u.x;
            float baseY = u.y + u.hitSize / 2f + 6f;
            float barW = Math.max(24f, u.hitSize * 1.25f), barH = 4.2f, halfW = barW / 2f;

            Draw.z(115f);
            Draw.color(BG_BAR, 0.85f);
            Fill.rect(cx, baseY, barW + 2f, barH + 2f);

            // Vệt trắng giảm máu mượt phía sau thanh máu thật
            if (pct > realPct + 0.5f) {
                float trailW = barW * (pct / 100f);
                Draw.color(Color.white, 0.55f);
                Fill.rect(cx - halfW + trailW / 2f, baseY, trailW, barH);
            }

            Color hpColor = realPct < 25f ? Color.red : realPct < 55f ? Color.yellow : Color.green;
            Draw.color(hpColor);
            Draw.alpha(realPct < 20f ? 0.65f + Mathf.absin(Time.time, 5f, 0.35f) : 0.94f);
            float curW = barW * (realPct / 100f);
            Fill.rect(cx - halfW + curW / 2f, baseY, curW, barH);

            if (sh > 0.5f) {
                float shW = Math.min(barW, barW * (sh / maxHp));
                Draw.color(SHIELD, 0.9f);
                Fill.rect(cx - halfW + shW / 2f, baseY + barH / 2f + 0.9f, shW, 1.3f);
            }

            Draw.z(116f);
            Draw.color(Color.white, 0.35f);
            Fill.rect(cx, baseY + barH / 2f - 0.5f, barW, 0.7f);

            Font font = Fonts.outline;
            float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
            font.getData().setScale(Math.min(0.2f, Math.max(0.1f, 0.08f * (u.hitSize / 8f))));
            font.setColor(hpColor);

            String text = textCache.get(id);
            float cachedHp = textCacheHp.get(id, MISSING);
            float cachedMax = textCacheMax.get(id, MISSING);
            if (text == null || cachedHp <= MISSING * 0.5f || cachedMax <= MISSING * 0.5f
                    || Math.abs(cachedHp - curHp) > Math.max(4f, maxHp * 0.005f)
                    || Math.abs(cachedMax - maxHp) > 1f) {
                text = formatHP(curHp) + " / " + formatHP(maxHp);
                textCache.put(id, text);
                textCacheHp.put(id, curHp);
                textCacheMax.put(id, maxHp);
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
