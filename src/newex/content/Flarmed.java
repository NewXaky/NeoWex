package newex.content;

import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Chuyển từ flarmed.js (cập nhật 14/11). Súng phun lửa: block do flarmed.hjson tạo (kiểu b, bài 29b),
 * Java chỉ vá đạn ở ContentInitEvent, KHÔNG `new` block. Có 2 StatusEffect mới: bong, viem-hoa.
 */
public final class Flarmed {
    private Flarmed() {}

    private static final Color cRed = Color.valueOf("e8321a"), cOr = Color.valueOf("ff8c1f"),
        cYel = Color.valueOf("ffd23a"), cWht = Color.valueOf("fff6b0"),
        cSoot = Color.valueOf("120a08"), cGry = Color.valueOf("5c5a58");
    private static final float WIND = 8f, GROW = 22f, FADE = 30f;

    private static StatusEffect sBong, sViem;

    /** Gọi trong NexMod.loadContent(): StatusEffect phải tạo ở đây, KHÔNG tạo trong constructor NexMod (import mod sẽ trùng tên). */
    public static void create() {
        sBong = new StatusEffect("bong");
        sBong.localizedName = "Bỏng";
        sBong.color = cOr;
        sBong.damage = 10f / 60f;
        sBong.permanent = false;

        // Bài 65: v157 là update(Unit, StatusEntry), không phải (Unit, float time) như JS.
        sViem = new StatusEffect("viem-hoa") {
            @Override
            public void update(Unit u, StatusEntry entry) {
                u.damageContinuousPierce((100f + u.maxHealth * 0.01f) / 60f);
            }
        };
        sViem.localizedName = "Viêm hỏa";
        sViem.color = cRed;
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        Block b = Vars.content.getByName(ContentType.block, "newex-flarmed");
        if (!(b instanceof ItemTurret)) return;
        ItemTurret t = (ItemTurret) b;
        t.ammoTypes.put(Items.coal, new FlameBullet(12f, 240f, t.range, 300f));
        t.ammoTypes.put(Items.pyratite, new FlameBullet(18f, 360f, t.range, 300f));
    }

    // ---------- helper hình học / vẽ ----------
    private static float widthAt(float u) { return 4f + 20f * (float) Math.pow(u, 1.2); }

    private static float[] pt(float mx, float my, float r, float d, float off) {
        return new float[] {
            mx + Angles.trnsx(r, d) + Angles.trnsx(r + 90f, off),
            my + Angles.trnsy(r, d) + Angles.trnsy(r + 90f, off)
        };
    }

    private static float ease(float x) { return Interp.smooth.apply(Mathf.clamp(x)); }

    /** Giả ngẫu nhiên theo seed (double như JS). */
    private static float rnd(float s) {
        double x = Math.sin(s * 12.9898 + 78.233) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    private static float sin(double x) { return (float) Math.sin(x); }

    /** Draw.color(a, b, c, s) của JS: tự cài để khỏi phụ thuộc overload 3 màu (không có jar để kiểm tra): a→b rồi b→c. */
    private static void mix3(Color a, Color b, Color c, float s) {
        if (s < 0.5f) Tmp.c1.set(a).lerp(b, s * 2f);
        else Tmp.c1.set(b).lerp(c, (s - 0.5f) * 2f);
        Draw.color(Tmp.c1);
    }

    private static void drawJet(float mx, float my, float r, float len, float a, float T, int id, boolean main) {
        int N = 16;
        Color[] cols = {cRed, cOr, cYel, cWht};
        float[] sc = {1f, 0.75f, 0.5f, 0.24f};
        for (int l = 0; l < 4; l++) {
            Draw.color(cols[l], a * (l == 0 ? 0.8f : 1f));
            float[] prev = null;
            for (int i = 0; i <= N; i++) {
                float u = i / (float) N, tap = 1f - 0.7f * (float) Math.pow(u, 5);
                float w = widthAt(u) * sc[l] * 0.5f * tap * (0.85f + 0.15f * sin(T * 0.35 - i * 0.6));
                float off = sin(T * 0.3 - i * 0.7) * widthAt(u) * 0.15f;
                float[] p1 = pt(mx, my, r, len * u, off + w), p2 = pt(mx, my, r, len * u, off - w);
                if (prev != null) Fill.quad(prev[0], prev[1], prev[2], prev[3], p2[0], p2[1], p1[0], p1[1]);
                prev = new float[] {p1[0], p1[1], p2[0], p2[1]};
            }
        }
        Draw.blend(Blending.additive);
        if (main) for (int j = 0; j < 3; j++) {
            Draw.color(j == 1 ? cWht : cOr, 0.5f * a);
            float lx = mx, ly = my;
            for (int i = 1; i <= 16; i++) {
                float u = i / 16f;
                float[] p = pt(mx, my, r, len * u, sin(T * 0.22 + j * 2.1 + u * 7) * widthAt(u) * 0.55f);
                Lines.stroke(2.4f * (1f - u * 0.6f));
                Lines.line(lx, ly, p[0], p[1]);
                lx = p[0]; ly = p[1];
            }
        }
        int n = main ? 40 : 14;
        for (int k = 0; k < n; k++) {
            float s = id * 7 + k * 13, p = (T * 0.045f + rnd(s)) % 1f, sd = rnd(s + 1) * 2f - 1f;
            float[] q = pt(mx, my, r, len * p, sd * widthAt(p) * 0.45f + sin(T * 0.2 + k) * 2f);
            mix3(cWht, cYel, cOr, p);
            Draw.alpha(a * (float) Math.pow(1f - p, 0.7) * 0.7f);
            Fill.circle(q[0], q[1], 2f + widthAt(p) * 0.3f * (1f - p * 0.4f));
        }
        Draw.blend();
    }

    private static boolean inCone(float tx, float ty, float hs, float mx, float my, float cr, float sr, float len, float range) {
        float dx = tx - mx, dy = ty - my, al = dx * cr + dy * sr, pp = Math.abs(-dx * sr + dy * cr);
        return al > 0f && al <= len + hs && pp <= widthAt(Math.min(al / range, 1f)) * 0.6f + hs;
    }

    /** Một đạn dùng cho cả 2 loại nhiên liệu (JS: makeFlame(dmg, burnTime, range, lifetime)). */
    private static final class FlameBullet extends BulletType {
        final float dmg, burn, reach, life;

        FlameBullet(float dmg, float burn, float reach, float life) {
            this.dmg = dmg; this.burn = burn; this.reach = reach; this.life = life;
            speed = 0f; damage = 0f; lifetime = life;
            collides = false; collidesTiles = false; collidesAir = false; collidesGround = false;
            absorbable = false; hittable = false; reflectable = false; keepVelocity = false;
            hitEffect = Fx.none; despawnEffect = Fx.none; shootEffect = Fx.none; smokeEffect = Fx.none;
            drawSize = reach * 3f; ammoMultiplier = 2f;
        }

        @Override
        public void update(Bullet b) {
            if (Vars.state.isPaused() || Time.delta <= 0f) return;
            if (!(b.owner instanceof Turret.TurretBuild) || !((Turret.TurretBuild) b.owner).isValid()) { b.remove(); return; }
            Turret.TurretBuild o = (Turret.TurretBuild) b.owner;
            if (b.data == null) b.data = Float.valueOf(o.rotation);
            float r = b.data instanceof Float ? (Float) b.data : o.rotation;
            o.rotation = r; // khóa hướng khi đang phun
            float t = b.time, mx = o.x + Angles.trnsx(r, 14f), my = o.y + Angles.trnsy(r, 14f);
            float grow = ease((t - WIND) / GROW), len = reach * grow;
            if (grow <= 0f) return;
            float[] tp = pt(mx, my, r, len, 0f);
            if (b.timer.get(4, 30f)) Effect.shake(0.8f * grow, 10f, tp[0], tp[1]);
            if (Vars.net.client() || grow < 0.2f || !b.timer.get(0, 6f)) return;
            final float cr = (float) Math.cos(r * Mathf.degRad), sr = (float) Math.sin(r * Mathf.degRad);
            final float fmx = mx, fmy = my, flen = len;
            Units.nearbyEnemies(b.team, mx - reach, my - reach, reach * 2f, reach * 2f, u -> {
                if (u.dead() || u.isFlying() || !inCone(u.x, u.y, u.hitSize / 2f, fmx, fmy, cr, sr, flen, reach)) return;
                u.damage(dmg);
                u.apply(sBong, burn); u.apply(sViem, burn);
                u.apply(StatusEffects.melting, burn); u.apply(StatusEffects.burning, burn); u.apply(StatusEffects.sapped, burn);
            });
            final Team team = b.team;
            Units.nearbyBuildings(mx, my, reach + 16f, bd -> {
                if (bd.team == team || bd.team == Team.derelict || !inCone(bd.x, bd.y, bd.block.size * 4f, fmx, fmy, cr, sr, flen, reach)) return;
                bd.damage(team, dmg * 0.7f);
            });
        }

        @Override
        public void draw(Bullet b) {
            if (!(b.owner instanceof Turret.TurretBuild) || !((Turret.TurretBuild) b.owner).isValid()) return;
            Turret.TurretBuild o = (Turret.TurretBuild) b.owner;
            float T = b.time;
            float r = b.data instanceof Float ? (Float) b.data : o.rotation;
            o.rotation = r;
            float mx = o.x + Angles.trnsx(r, 14f), my = o.y + Angles.trnsy(r, 14f);
            float grow = ease((T - WIND) / GROW), pilot = ease(T / WIND);
            float a = 1f - Mathf.clamp((T - (life - FADE)) / FADE);
            float len = reach * grow;

            Draw.z(Layer.bullet + 1f);
            Draw.blend(Blending.additive);
            float nz = pilot * a * (1f + 0.12f * sin(T * 0.3));
            Draw.color(cOr, 0.22f * nz); Fill.circle(mx, my, 20f * nz);
            Draw.color(cYel, 0.4f * nz); Fill.circle(mx, my, 10f * nz);
            Draw.color(cWht, 0.8f * nz); Fill.circle(mx, my, 4.5f * nz);
            Draw.blend();
            if (grow <= 0f) { Draw.reset(); return; }

            float[] tc = pt(mx, my, r, len, sin(T * 0.3 - 16 * 0.7) * widthAt(1f) * 0.15f);
            float fl = 1f + 0.1f * sin(T * 0.5);

            Draw.z(Layer.bullet);
            for (int k = 0; k < 16; k++) {
                float s = b.id * 5 + k * 17, p = (T * 0.016f + rnd(s)) % 1f;
                float[] q = pt(tc[0], tc[1], r, 40f * p + (rnd(s + 1) * 2f - 1f) * 10f, (rnd(s + 2) * 2f - 1f) * 10f + sin(T * 0.04 + k) * 6f * p);
                Draw.color(cSoot, cGry, p);
                Draw.alpha(0.55f * a * sin(Math.PI * Math.pow(p, 0.6)));
                Fill.circle(q[0], q[1] + 12f * p, (8f + 20f * p) * (0.5f + 0.5f * grow));
            }
            Draw.z(Layer.bullet + 1f);

            drawJet(mx, my, r, len, a, T, b.id, true);

            Draw.blend(Blending.additive);
            float S = 26f * grow * (0.4f + 0.6f * a) * fl;
            Color[] lc = {cRed, cOr, cYel, cWht};
            float[] ls = {1f, 0.75f, 0.5f, 0.25f};
            for (int l = 0; l < 4; l++) {
                Draw.color(lc[l], a * 0.55f);
                for (int k = 0; k < 7; k++) {
                    float s = b.id * 31 + k, ang = k * 51 + T * 0.6f * (k % 2 != 0 ? 1f : -1f), d = S * (0.2f + rnd(s) * 0.35f);
                    Fill.circle(tc[0] + Angles.trnsx(ang, d), tc[1] + Angles.trnsy(ang, d), S * (0.4f + rnd(s + 1) * 0.2f) * ls[l]);
                }
            }
            Draw.color(cOr, 0.14f * a); Fill.circle(tc[0], tc[1], S * 2.2f);
            Draw.color(cYel, 0.18f * a); Fill.circle(tc[0], tc[1], S * 1.2f);
            float sw = (T - WIND - GROW * 0.7f) / 40f;
            if (sw > 0f && sw < 1f) {
                Lines.stroke(4f * (1f - sw)); Draw.color(cYel, 0.7f * (1f - sw));
                Lines.circle(tc[0], tc[1], S * (0.8f + 2f * sw));
            }
            Drawf.light(tc[0], tc[1], S * 6f + 20f, cOr, 0.7f * a);
            for (int i = 1; i <= 4; i++) {
                float u = i / 4f;
                float[] p = pt(mx, my, r, len * u, 0f);
                Drawf.light(p[0], p[1], widthAt(u) * 3f + 8f, cOr, 0.3f * a);
            }
            for (int k = 0; k < 30; k++) {
                float s = b.id * 3 + k * 11, p = (T * 0.02f + rnd(s)) % 1f;
                float[] e = pt(mx, my, r, len * (0.3f + 0.85f * p), (rnd(s + 1) * 2f - 1f) * widthAt(p) * 1.4f + sin(T * 0.05 + k) * 4f);
                Draw.color(k % 3 == 0 ? cWht : cYel, a * (1f - p));
                Fill.circle(e[0], e[1] - p * p * 10f, 1.8f * (1f - p) + 0.4f);
            }
            Draw.blend(); Draw.reset();
        }
    }
}
