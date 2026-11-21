package newex.content;

import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.effect.MultiEffect;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.Turret;
import newex.PerfCore;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Chuyển từ splash-fx.js. Hiệu ứng nổ dùng chung:
 * - {@link #ring}: vòng nổ đúng bán kính sát thương, sống 0.25 giây, viền các vòng gần nhau liền mạch.
 * - Tự gắn vòng nổ (đạn nổ lan) hoặc tia lửa (đạn vật lý) vào mọi đạn của pháo và đơn vị, màu theo màu đạn.
 * Tối ưu hiệu năng: không cấp phát bộ nhớ trong render, dùng khoảng cách bình phương, và rút gọn vẽ khi trên Mobile/FPS thấp.
 */
public final class SplashFx {
    private static final float LIFE = 15f;
    private static final int MAXN = 32;

    private static final Map<String, Effect> cache = new HashMap<>();
    /** Các vòng đang sống: IntMap + Seq không boxing, không tạo Live mới mỗi frame. */
    private static final IntMap<Live> liveMap = new IntMap<>();
    private static final Seq<Live> liveList = new Seq<>(false, 48);
    private static final Seq<Live> livePool = new Seq<>(false, 48);
    private static final Live[] nbBuf = new Live[6];

    private static final class Live {
        float x, y, r, end;
        int id;
    }

    private SplashFx() {}

    private static Live obtainLive(int id, float x, float y, float r, float end) {
        Live l = livePool.size > 0 ? livePool.pop() : new Live();
        l.id = id; l.x = x; l.y = y; l.r = r; l.end = end;
        return l;
    }

    private static void clearLive() {
        for (int i = 0; i < liveList.size; i++) {
            if (livePool.size < 64) livePool.add(liveList.get(i));
        }
        liveList.clear();
        liveMap.clear();
    }

    private static Effect ringFx(float radius, final Color col) {
        final float r = Math.max(8f, Math.round(radius / 2f) * 2f);
        String key = ((int) r) + (col != null ? col.toString() : "");
        Effect existing = cache.get(key);
        if (existing != null) return existing;

        Effect fx = new Effect(LIFE, r * 2f + 100f, e -> {
            float f = e.fout();
            Color c = col != null ? col : e.color;
            int perf = PerfCore.level();
            boolean fastMode = Vars.mobile || perf >= 1;

            int nbCount = 0;
            boolean inside = false;

            if (!fastMode) {
                float now = Time.time;
                Live self = liveMap.get(e.id);
                if (self == null) {
                    self = obtainLive(e.id, e.x, e.y, r, now - e.time + LIFE);
                    liveMap.put(e.id, self);
                    liveList.add(self);
                } else {
                    self.x = e.x; self.y = e.y; self.r = r; self.end = now - e.time + LIFE;
                }

                int cnt = 0;
                for (int i = liveList.size - 1; i >= 0; i--) {
                    Live o = liveList.get(i);
                    if (o.end < now) {
                        liveList.remove(i);
                        liveMap.remove(o.id);
                        if (livePool.size < 64) livePool.add(o);
                        continue;
                    }
                    cnt++;
                    if (o.id == e.id) continue;
                    float dx = e.x - o.x, dy = e.y - o.y;
                    float d2 = dx * dx + dy * dy;
                    float sum = r + o.r;
                    if (d2 < sum * sum) {
                        if (nbCount < 6) nbBuf[nbCount++] = o;
                        if (o.id < e.id && r <= o.r) {
                            float diff = o.r - r;
                            if (d2 <= diff * diff) inside = true;
                        }
                    }
                }
                if (cnt > MAXN) nbCount = 0;
            }

            Draw.z(Layer.effect + 0.1f);
            if (!inside) {
                Draw.color(c);
                Draw.alpha(0.22f * f);
                Fill.circle(e.x, e.y, r);
            }
            Draw.color(Color.white, c, e.fin());
            Draw.alpha(0.9f * f);
            Lines.stroke(3f * f + 0.5f);
            Lines.circle(e.x, e.y, r * (float) Math.sqrt(e.fin()));
            Draw.color(c);
            Draw.alpha(f);
            Lines.stroke(1.5f * f);
            if (nbCount == 0) {
                Lines.circle(e.x, e.y, r);
            } else {
                int n = Math.max(12, Math.min(36, Math.round(r / 4.5f)));
                float st = 360f / n;
                for (int i = 0; i < n; i++) {
                    float a0 = i * st, a1 = a0 + st, am = a0 + st * 0.5f;
                    float mx = e.x + Mathf.cosDeg(am) * r, my = e.y + Mathf.sinDeg(am) * r;
                    boolean hide = false;
                    for (int j = 0; j < nbCount; j++) {
                        Live nbj = nbBuf[j];
                        float rx = mx - nbj.x, ry = my - nbj.y;
                        float lim = nbj.r - 0.5f;
                        if (rx * rx + ry * ry < lim * lim) {
                            hide = true;
                            break;
                        }
                    }
                    if (!hide) {
                        Lines.line(e.x + Mathf.cosDeg(a0) * r, e.y + Mathf.sinDeg(a0) * r,
                            e.x + Mathf.cosDeg(a1) * r, e.y + Mathf.sinDeg(a1) * r);
                    }
                }
            }
            Draw.alpha(f * 0.85f);
            Fill.circle(e.x, e.y, Math.min(r * 0.25f, 10f) * f);
            Draw.reset();
        });
        cache.put(key, fx);
        return fx;
    }

    private static Color toColor(Object c) {
        if (c == null) return Color.white;
        if (c instanceof String) return Color.valueOf((String) c);
        return (Color) c;
    }

    /** Vòng nổ bán kính {@code radius} tại (x, y), màu trắng. */
    public static void ring(float x, float y, float radius) {
        ringFx(radius, null).at(x, y, 0f, Color.white);
    }

    /** Vòng nổ có màu (Color hoặc chuỗi "#hex"; null = trắng). */
    public static void ring(float x, float y, float radius, Object color) {
        ringFx(radius, null).at(x, y, 0f, toColor(color));
    }

    // ------------------------------------------------------------------ tia lửa cho đạn vật lý

    private static final float SPARK_LIFE = 12f, SPREAD = 8f;
    private static final Map<String, Effect> sparkCache = new HashMap<>();
    private static final Rand rnd = new Rand();

    private static Effect sparkFx(float dmg, final Color col) {
        final float sz = Math.max(18, Math.min(48, Math.round((16 + dmg * 0.5f) / 4f) * 4));
        final int n = Math.max(5, Math.min(9, Math.round(4 + dmg / 10f)));
        String key = ((int) sz) + "_" + n + col.toString();
        Effect existing = sparkCache.get(key);
        if (existing != null) return existing;

        Effect fx = new Effect(SPARK_LIFE, sz * 2f, e -> {
            int perf = PerfCore.level();
            if (perf >= 2) return;
            float fin = e.fin(), fo = e.fout(), g = Interp.pow2Out.apply(fin);
            Draw.z(Layer.effect + 0.2f);
            Draw.blend(Blending.additive);
            rnd.setSeed(e.id);
            int cnt = n + 2 + Math.min(2, (int) Math.floor(rnd.random(0f, 3f)));
            if (Vars.mobile || perf >= 1) cnt = Math.max(3, cnt / 2);
            Draw.color(Color.white, col, fin);
            Draw.alpha(fo);
            Lines.stroke(1.8f * fo + 0.4f);
            for (int i = 0; i < cnt; i++) {
                float ang = e.rotation + rnd.range(SPREAD);
                float far = sz * rnd.random(0.55f, 1f);
                float ln = sz * rnd.random(0.25f, 0.5f) * fo + 1f;
                float head = far * g;
                float tail = Math.max(0f, head - ln);
                float c = Mathf.cosDeg(ang), s = Mathf.sinDeg(ang);
                Lines.line(e.x + c * tail, e.y + s * tail, e.x + c * head, e.y + s * head);
            }
            Draw.color(Color.white);
            Draw.alpha(fo * fo);
            Fill.circle(e.x, e.y, sz * 0.12f * fo);
            Draw.blend();
            Draw.reset();
        });
        sparkCache.put(key, fx);
        return fx;
    }

    /** Màu đạn: hitColor (nếu khác trắng) > backColor > trắng. */
    private static Color bulletColor(BulletType b) {
        if (b.hitColor != null && !b.hitColor.equals(Color.white)) return b.hitColor;
        if (b instanceof BasicBulletType) {
            Color back = ((BasicBulletType) b).backColor;
            if (back != null) return back;
        }
        return Color.white;
    }

    private static final IdentityHashMap<BulletType, Boolean> seen = new IdentityHashMap<>();

    private static boolean isNone(Effect e) {
        return e == null || e == Fx.none;
    }

    private static void patch(BulletType b) {
        if (b == null || seen.containsKey(b)) return;
        seen.put(b, Boolean.TRUE);
        if (b.splashDamage > 0 && b.splashDamageRadius > 0) {
            Effect fx = ringFx(b.splashDamageRadius, bulletColor(b));
            b.hitEffect = isNone(b.hitEffect) ? fx : new MultiEffect(b.hitEffect, fx);
            if (b.despawnHit) b.despawnEffect = isNone(b.despawnEffect) ? fx : new MultiEffect(b.despawnEffect, fx);
        } else if (b instanceof BasicBulletType && b.collides && b.damage > 0) {
            Effect sx = sparkFx(b.damage, bulletColor(b));
            b.hitEffect = isNone(b.hitEffect) ? sx : new MultiEffect(b.hitEffect, sx);
            // hết thời gian tồn tại: despawnHit đã gọi hitEffect nên chỉ thêm khi không có despawnHit (tránh hiện đôi)
            if (!b.despawnHit) b.despawnEffect = isNone(b.despawnEffect) ? sx : new MultiEffect(b.despawnEffect, sx);
        }
        patch(b.fragBullet);
        patch(b.intervalBullet);
    }

    /** Đọc trường theo tên (JS truy cập thuộc tính động; mỗi loại pháo có trường khác nhau). */
    private static Object readField(Object o, String name) {
        try {
            return o.getClass().getField(name).get(o);
        } catch (Throwable err) {
            return null;
        }
    }

    private static void applyAll() {
        try {
            Vars.content.blocks().each(bl -> {
                if (!(bl instanceof Turret)) return;
                Object ammo = readField(bl, "ammoTypes");
                if (ammo instanceof ObjectMap) {
                    for (Object v : ((ObjectMap<?, ?>) ammo).values()) {
                        if (v instanceof BulletType) patch((BulletType) v);
                    }
                }
                Object shoot = readField(bl, "shootType");
                if (shoot instanceof BulletType) patch((BulletType) shoot);
            });
            Vars.content.units().each(u -> u.weapons.each(w -> patch(w.bullet)));
        } catch (Throwable err) {
            Log.err("splash-fx: " + err);
        }
    }

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> applyAll());
        Events.on(ServerLoadEvent.class, e -> applyAll());
        Events.on(WorldLoadEvent.class, e -> clearLive());
    }
}
