package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.func.Cons;
import arc.func.Floatp;
import arc.func.Prov;
import arc.graphics.Camera;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.IntFloatMap;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.bullet.SapBulletType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.TileChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.Team;
import mindustry.game.Teams.BlockPlan;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ reflecounum.js. Pháo khiên lục giác: khiên bị trừ trước, hết khiên mới trừ máu; chặn/phản đạn;
 * có nhánh MK2/MK2B và 6 phúc lợi ngẫu nhiên. NexReflect (global JS cũ) nằm ở đây; Nethyr/Endyr/Overyr dùng
 * qua {@code Endyr.reflectProtected} (gán trong create()).
 */
public final class Reflecounum {
    private Reflecounum() {}

    static final Color reflectorColor = Color.valueOf("#d000ff");
    static final Color reflectorGlow = Color.valueOf("#00f0ff");
    static final String OPT_FX_KEY = "reflecounum-opt-fx";

    static final class Req {
        final int copper, lead, silicon, titanium, thorium, plastanium, surgeAlloy;
        Req(int copper, int lead, int silicon, int titanium, int thorium, int plastanium, int surgeAlloy) {
            this.copper = copper; this.lead = lead; this.silicon = silicon; this.titanium = titanium;
            this.thorium = thorium; this.plastanium = plastanium; this.surgeAlloy = surgeAlloy;
        }
    }
    static final Req reqMK2 = new Req(0, 0, 300, 400, 200, 0, 0);
    static final Req reqMK2B = new Req(0, 0, 500, 600, 0, 350, 150);
    static final Req reqSpecial = new Req(4000, 4000, 4000, 0, 0, 0, 0);

    // isEn của reflecounum.js: locale bắt đầu bằng "en" (mặc định "" => tiếng Việt)
    static boolean isEn() {
        String loc = Core.settings != null ? Core.settings.getString("locale", "") : "";
        return loc.startsWith("en");
    }

    static boolean readOptFx() {
        try {
            if (Core.settings != null) return Core.settings.getBool(OPT_FX_KEY, true);
        } catch (Throwable ignored) {}
        return true;
    }

    static final BasicBulletType perk4Bullet = new BasicBulletType(8f, 30f) {{
        lifetime = 180f;
        homingPower = 0.20f;
        homingRange = 300f;
        pierce = true;
        pierceCap = 4;
        frontColor = reflectorGlow;
        backColor = Color.white;
    }};

    static final BasicBulletType perk6Bullet = new BasicBulletType(18f, 150f) {{
        lifetime = 60f;
        homingPower = 0.15f;
        homingRange = 400f;
        pierce = true;
        pierceCap = 20;
        splashDamage = 120f;
        splashDamageRadius = 20f;
        frontColor = reflectorColor;
        backColor = Color.white;
    }};

    static final Effect reflectEffect = new Effect(15f, e -> {
        Draw.color(reflectorGlow, reflectorColor, e.fout());
        Lines.stroke(e.fout() * 2.5f);
        Lines.poly(e.x, e.y, 6, e.fin() * 12f);
    });

    static final Effect shockwaveFx = new Effect(30f, e -> {
        Draw.color(reflectorColor, reflectorGlow, e.fout());
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, e.fin() * 200f);
        Draw.reset();
    });

    /** Khiên khác chồng lên (dùng cho viền cắt ở hiệu ứng cũ). */
    static final class Ov {
        final float x, y, r;
        Ov(float x, float y, float r) { this.x = x; this.y = y; this.r = r; }
    }

    static boolean insideHex(float px, float py, float cx, float cy, float r) {
        float ax = Math.abs(px - cx);
        float ay = Math.abs(py - cy);
        return ay <= r * 0.866f && 0.866f * ax + 0.5f * ay <= r * 0.866f;
    }

    static void drawHexClipped(float x, float y, float r, java.util.List<Ov> others) {
        int steps = 4;
        for (int i = 0; i < 6; i++) {
            float x1 = x + Mathf.cosDeg(i * 60) * r;
            float y1 = y + Mathf.sinDeg(i * 60) * r;
            float x2 = x + Mathf.cosDeg((i + 1) * 60) * r;
            float y2 = y + Mathf.sinDeg((i + 1) * 60) * r;
            for (int k = 0; k < steps; k++) {
                float ax = x1 + (x2 - x1) * ((float) k / steps);
                float ay = y1 + (y2 - y1) * ((float) k / steps);
                float bx = x1 + (x2 - x1) * ((float) (k + 1) / steps);
                float by = y1 + (y2 - y1) * ((float) (k + 1) / steps);
                float mx = (ax + bx) / 2f;
                float my = (ay + by) / 2f;
                boolean covered = false;
                for (int j = 0; j < others.size(); j++) {
                    Ov o = others.get(j);
                    if (insideHex(mx, my, o.x, o.y, o.r - 1f)) { covered = true; break; }
                }
                if (!covered) Lines.line(ax, ay, bx, by);
            }
        }
    }

    static boolean camVisible(float x, float y, float r) {
        Camera cam = Core.camera;
        if (cam == null) return true;
        float hw = cam.width / 2f + r;
        float hh = cam.height / 2f + r;
        return Math.abs(x - cam.position.x) <= hw && Math.abs(y - cam.position.y) <= hh;
    }

    // ===================================================================== NexReflect

    interface Visit { void get(Teamc e, ReflecounumTurret.ReflecounumBuild s, int id, boolean isB, int phase); }

    static final class LineInfo { boolean line; float len, half; }
    static final class Line { float sx, sy, ex, ey, half; ReflecounumTurret.ReflecounumBuild shield; }
    static final class Buf { Unit unit; ReflecounumTurret.ReflecounumBuild shield; float base, buf; }
    static final class BBuf { Building build; ReflecounumTurret.ReflecounumBuild shield; float base, pre; }
    static final class BlockCache {
        final Seq<Building> arr; final int ver; final float q; final float at;
        BlockCache(Seq<Building> arr, int ver, float q, float at) { this.arr = arr; this.ver = ver; this.q = q; this.at = at; }
    }

    // trạng thái dùng chung cho visit() (đúng như object V của JS)
    static ReflecounumTurret.ReflecounumBuild vS;
    static Visit vCb;
    static int vPhase;
    static float vR, vSx, vSy;
    static final IntSet vSeen = new IntSet();
    static Seq<Building> collectArr = null;

    static final Cons<Unit> unitCons = u -> {
        if (u == null || !u.isValid() || u.dead || vSeen.contains(u.id)) return;
        boolean inDisk = Mathf.within(vSx, vSy, u.x, u.y, vR + u.hitSize / 2f);
        if (vPhase == 0 ? !inDisk : inDisk) return;
        vSeen.add(u.id);
        vCb.get(u, vS, u.id, false, vPhase);
    };
    static final Boolf<Building> boolAll = b -> true;
    static final Cons<Building> collectCons = b -> {
        if (b == null || !b.isValid()) return;
        if (b.block.name.startsWith("build")) return;
        collectArr.add(b);
    };

    static final Seq<ReflecounumTurret.ReflecounumBuild> list = new Seq<>();
    static final Seq<Buf> buffers = new Seq<>();
    static int bufN = 0;
    static final Seq<BBuf> blockBuffers = new Seq<>();
    static int blockBufN = 0;
    static IntFloatMap baseline = new IntFloatMap();
    static IntFloatMap nextBaseline = new IntFloatMap();
    static Seq<Line> lines = new Seq<>();
    static final IntSet lineShields = new IntSet();
    static final IntMap<LineInfo> lineCache = new IntMap<>();
    static final float EXT = 320f;
    static boolean hideFx = false;
    static boolean optFx = readOptFx();
    static int fpsLow = 0, fpsHigh = 0;
    static Seq<ReflecounumTurret.ReflecounumBuild> activeList = new Seq<>();
    static float activeAt = -1f;
    static boolean dirty = true;
    static int blockVer = 0;

    static void setOptFx(boolean v) {
        optFx = v;
        try {
            if (Core.settings != null) Core.settings.put(OPT_FX_KEY, optFx);
        } catch (Throwable ignored) {}
    }

    static void register(ReflecounumTurret.ReflecounumBuild build) {
        for (int i = 0; i < list.size; i++) {
            if (list.get(i).id == build.id) return;
        }
        list.add(build);
        dirty = true;
    }

    /** Cache danh sách khiên đang hoạt động trong cùng 1 frame. */
    static Seq<ReflecounumTurret.ReflecounumBuild> active() {
        if (!dirty && activeAt == Time.time) return activeList;
        Seq<ReflecounumTurret.ReflecounumBuild> out = new Seq<>();
        for (int i = list.size - 1; i >= 0; i--) {
            ReflecounumTurret.ReflecounumBuild s = list.get(i);
            if (s == null || !s.isValid()) {
                list.remove(i);
                continue;
            }
            if (!s.isShieldBroken() && s.getShieldHp() > 0) out.add(s);
        }
        activeList = out;
        activeAt = Time.time;
        dirty = false;
        return out;
    }

    /** Thay cho global.NexReflect.isProtected (Endyr/Nethyr/Overyr gọi qua Endyr.reflectProtected). */
    static boolean isProtected(Unit e) {
        if (e == null) return false;
        Seq<ReflecounumTurret.ReflecounumBuild> shields = active();
        for (int i = 0; i < shields.size; i++) {
            ReflecounumTurret.ReflecounumBuild s = shields.get(i);
            if (s.team == e.team && Mathf.within(s.x, s.y, e.x, e.y, s.getShieldRadius())) return true;
        }
        return false;
    }

    static float sizeOf(Teamc e, boolean isB) {
        return isB ? ((Building) e).block.size * 8f : ((Unit) e).hitSize;
    }

    static float hp(Teamc e) { return e instanceof Building ? ((Building) e).health : ((Unit) e).health; }
    static void setHp(Teamc e, float v) { if (e instanceof Building) ((Building) e).health = v; else ((Unit) e).health = v; }
    static float xOf(Teamc e) { return e.getX(); }
    static float yOf(Teamc e) { return e.getY(); }

    /** Đọc trường số public theo tên (JS: Number(type.length) -> NaN nếu không có). */
    static float numField(Object o, String name) {
        try {
            Object v = o.getClass().getField(name).get(o);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignored) {}
        return Float.NaN;
    }

    static LineInfo lineInfo(BulletType type) {
        int id = type.id;
        LineInfo c = lineCache.get(id);
        if (c != null) return c;
        boolean line = type instanceof LaserBulletType || type instanceof ContinuousBulletType || type instanceof RailBulletType
            || type instanceof PointLaserBulletType || type instanceof SapBulletType;
        float len = numField(type, "length");
        if (!(len > 0)) len = type.speed * type.lifetime;
        float w = numField(type, "width");
        float half = (w > 0 ? w * 0.5f : 8f) + 4f;
        c = new LineInfo();
        c.line = line; c.len = len; c.half = half;
        lineCache.put(id, c);
        return c;
    }

    static Seq<Line> collectLines(Seq<ReflecounumTurret.ReflecounumBuild> shields) {
        Seq<Line> out = new Seq<>();
        lineShields.clear();
        int n = Groups.bullet.size();
        for (int i = 0; i < n; i++) {
            Bullet b = Groups.bullet.index(i);
            if (b == null) continue;
            LineInfo info = lineInfo(b.type);
            if (!info.line || !(info.len > 0)) continue;

            double ang = b.rotation() * Mathf.degRad;
            double dx = Math.cos(ang);
            double dy = Math.sin(ang);
            float ox = b.x;
            float oy = b.y;

            for (int j = 0; j < shields.size; j++) {
                ReflecounumTurret.ReflecounumBuild s = shields.get(j);
                if (b.team == s.team) continue;
                double R = s.getShieldRadius();
                double rx = s.x - ox;
                double ry = s.y - oy;
                double proj = rx * dx + ry * dy;
                double perp2 = rx * rx + ry * ry - proj * proj;
                if (perp2 > R * R) continue;
                double t1 = proj - Math.sqrt(R * R - perp2);
                if (t1 < 0 || t1 > info.len) continue;
                Line l = new Line();
                l.sx = (float) (ox + dx * t1);
                l.sy = (float) (oy + dy * t1);
                l.ex = (float) (ox + dx * info.len);
                l.ey = (float) (oy + dy * info.len);
                l.half = info.half;
                l.shield = s;
                out.add(l);
                lineShields.add(s.id);
            }
        }
        return out;
    }

    static boolean hasLines(ReflecounumTurret.ReflecounumBuild s) { return lineShields.contains(s.id); }

    static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay, wx = px - ax, wy = py - ay;
        float c1 = vx * wx + vy * wy;
        if (c1 <= 0) return (float) Math.sqrt(wx * wx + wy * wy);
        float c2 = vx * vx + vy * vy;
        if (c2 <= c1) {
            float ux = px - bx, uy = py - by;
            return (float) Math.sqrt(ux * ux + uy * uy);
        }
        float t = c1 / c2;
        float qx = ax + t * vx, qy = ay + t * vy;
        return (float) Math.sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy));
    }

    static boolean inCorridor(float x, float y, float size, ReflecounumTurret.ReflecounumBuild s) {
        for (int i = 0; i < lines.size; i++) {
            Line l = lines.get(i);
            if (l.shield.id != s.id) continue;
            if (segDist(x, y, l.sx, l.sy, l.ex, l.ey) <= l.half + size / 2f) return true;
        }
        return false;
    }

    /** Cache danh sách block quanh khiên, chỉ quét lại khi map đổi hoặc hết hạn (90 tick). */
    static Seq<Building> blocksOf(ReflecounumTurret.ReflecounumBuild s, int phase, float query) {
        BlockCache c = phase == 0 ? s.cb0 : s.cb1;
        float now = Time.time;
        if (c == null || c.ver != blockVer || c.q != query || now - c.at > 90f || now < c.at) {
            Seq<Building> arr = new Seq<>();
            collectArr = arr;
            Vars.indexer.eachBlock(s.team, s.x, s.y, query, boolAll, collectCons);
            collectArr = null;
            c = new BlockCache(arr, blockVer, query, now);
            if (phase == 0) s.cb0 = c; else s.cb1 = c;
        }
        return c.arr;
    }

    static void visit(Visit cb, boolean ringOnlyWithLines) {
        Seq<ReflecounumTurret.ReflecounumBuild> shields = active();
        if (shields.size == 0) return;
        IntSet seen = vSeen;
        seen.clear();
        vCb = cb;

        for (int phase = 0; phase < 2; phase++) {
            for (int i = 0; i < shields.size; i++) {
                ReflecounumTurret.ReflecounumBuild s = shields.get(i);
                if (phase == 1 && ringOnlyWithLines && !hasLines(s)) continue;
                float R = s.getShieldRadius();
                float sx = s.x, sy = s.y;
                float query = phase == 0 ? R : R + EXT;

                vS = s; vPhase = phase; vR = R; vSx = sx; vSy = sy;

                Units.nearby(s.team, sx - query, sy - query, query * 2f, query * 2f, unitCons);

                Seq<Building> arr = blocksOf(s, phase, query);
                for (int k = 0; k < arr.size; k++) {
                    Building b = arr.get(k);
                    if (!b.isValid() || seen.contains(b.id)) continue;
                    boolean inDisk = Mathf.within(sx, sy, b.x, b.y, R + b.block.size * 4f);
                    if (phase == 0 ? !inDisk : inDisk) continue;
                    seen.add(b.id);
                    cb.get(b, s, b.id, true, phase);
                }
            }
        }
        vCb = null;
        vS = null;
    }

    static void addBuf(Unit unit, ReflecounumTurret.ReflecounumBuild shield, float base, float buf) {
        Buf r;
        if (bufN < buffers.size) r = buffers.get(bufN);
        else { r = new Buf(); buffers.add(r); }
        r.unit = unit; r.shield = shield; r.base = base; r.buf = buf;
        bufN++;
    }

    static void addBlockBuf(Building build, ReflecounumTurret.ReflecounumBuild shield, float base, float pre) {
        BBuf r;
        if (blockBufN < blockBuffers.size) r = blockBuffers.get(blockBufN);
        else { r = new BBuf(); blockBuffers.add(r); }
        r.build = build; r.shield = shield; r.base = base; r.pre = pre;
        blockBufN++;
    }

    static void strip() {
        int n = bufN;
        bufN = 0;
        for (int i = 0; i < n; i++) {
            Buf r = buffers.get(i);
            Unit u = r.unit;
            if (u != null && u.isValid() && !u.dead) u.shield = r.base;
            r.unit = null;
            r.shield = null;
        }
        int bn = blockBufN;
        blockBufN = 0;
        for (int i = 0; i < bn; i++) {
            BBuf r = blockBuffers.get(i);
            Building b = r.build;
            if (b != null && b.isValid()) b.health = r.base;
            r.build = null;
            r.shield = null;
        }
    }

    static void applyBuffers() {
        strip();
        if (Vars.net.client()) return;
        Seq<ReflecounumTurret.ReflecounumBuild> shields = active();
        if (shields.size == 0) return;

        visit((e, s, id, isB, phase) -> {
            if (phase == 1 && !inCorridor(xOf(e), yOf(e), sizeOf(e, isB), s)) return;
            float cap = s.getShieldHp();

            if (!isB) {
                Unit u = (Unit) e;
                float base = u.shield;
                u.shield = base + cap;
                addBuf(u, s, base, cap);
                return;
            }

            Building b = (Building) e;
            float base = b.health;
            float max = b.maxHealth();
            float target = base >= max - 0.05f ? base + cap : Math.min(base + cap, max * 0.75f);
            if (target <= base) return;
            b.health = target;
            addBlockBuf(b, s, base, target);
        }, true);
    }

    static float pay(ReflecounumTurret.ReflecounumBuild s, float amount) {
        if (s == null || !s.isValid() || s.isShieldBroken()) return 0f;
        float p = Math.min(amount, s.getShieldHp());
        if (p > 0) {
            s.damageShield(p);
            s.hitTimer = 25f;
        }
        return p;
    }

    static void sync(Building b) {
        // JS gọi Call.tileDamage, hàm này không có ở v157 (bài 101): bỏ, máu công trình do game tự đồng bộ.
    }

    static void settle() {
        if (Vars.net.client()) {
            bufN = 0;
            blockBufN = 0;
            baseline.clear();
            lines = new Seq<>();
            lineShields.clear();
            return;
        }

        int n = bufN;
        bufN = 0;
        IntFloatMap excess = null;

        for (int i = 0; i < n; i++) {
            Buf r = buffers.get(i);
            Unit u = r.unit;
            if (u == null || !u.isValid() || u.dead) continue;

            float after = u.shield;
            float consumed = Math.max(0f, r.base + r.buf - after);
            float fromBuf = Math.min(consumed, r.buf);
            u.shield = consumed > r.buf ? Math.max(0f, after) : r.base;

            if (fromBuf > 0.001f) {
                float paid = pay(r.shield, fromBuf);
                if (fromBuf - paid > 0.001f) {
                    if (excess == null) excess = new IntFloatMap();
                    excess.put(u.id, fromBuf - paid);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            Buf r = buffers.get(i);
            r.unit = null;
            r.shield = null;
        }

        int bn = blockBufN;
        blockBufN = 0;
        for (int i = 0; i < bn; i++) {
            BBuf r = blockBuffers.get(i);
            Building b = r.build;
            if (b == null || !b.isValid()) continue;

            float net = b.health - r.pre;
            if (net < 0) {
                float loss = -net;
                float paid = pay(r.shield, loss);
                b.health = r.base;
                float rest = loss - paid;
                if (rest > 0.001f) {
                    float hpLeft = r.base - rest;
                    if (hpLeft > 0) {
                        b.health = hpLeft;
                        sync(b);
                    } else {
                        b.health = 0f;
                        b.kill();
                    }
                } else {
                    sync(b);
                }
            } else {
                b.health = r.base + net;
                if (net > 0) sync(b);
            }
        }
        for (int i = 0; i < bn; i++) {
            BBuf r = blockBuffers.get(i);
            r.build = null;
            r.shield = null;
        }

        Seq<ReflecounumTurret.ReflecounumBuild> shields = active();
        if (shields.size > 0) {
            lines = collectLines(shields);
        } else {
            lines = new Seq<>();
            lineShields.clear();
        }

        final IntFloatMap nb = nextBaseline;
        final IntFloatMap excessMap = excess;

        visit((e, s, id, isB, phase) -> {
            float prev = baseline.get(id, -1e30f);
            float cur = hp(e);

            if (prev > -1e29f && cur < prev - 0.01f && !s.isShieldBroken()) {
                if (phase == 0 || inCorridor(xOf(e), yOf(e), sizeOf(e, isB), s)) {
                    float paid = pay(s, prev - cur);
                    if (paid > 0.01f) {
                        setHp(e, cur + paid);
                        if (isB) sync((Building) e);
                    }
                }
            }

            if (!isB && excessMap != null && excessMap.containsKey(id)) {
                ((Unit) e).damagePierce(excessMap.get(id, 0f));
            }

            nb.put(id, hp(e));
        }, true);

        IntFloatMap t = baseline;
        baseline = nb;
        nextBaseline = t;
        t.clear();
    }

    // ===================================================================== khởi tạo

    public static ReflecounumTurret reflecounum;

    /** Gọi từ NexMod.loadContent(). hjson reflecounum không có `type:` nên Java tạo block (bài 29a). */
    public static void create() {
        reflecounum = new ReflecounumTurret("reflecounum");

        reflecounum.addBar("shield", (ReflecounumTurret.ReflecounumBuild e) -> new Bar(
            (Prov<CharSequence>) () -> {
                String labelText = isEn() ? "Shield: " : "Khiên: ";
                String brokenText = isEn() ? "Shield: [BROKEN]" : "Khiên: [ĐÃ VỠ]";
                if (e == null) return labelText + "0 / 0";
                if (e.isShieldBroken()) return brokenText;
                int cur = (int) Math.floor(e.getShieldHp());
                int max = (int) Math.floor(e.getMaxShieldHp());
                return labelText + cur + " / " + max;
            },
            (Prov<Color>) () -> (e != null && e.isShieldBroken()) ? Color.gray : reflectorColor,
            (Floatp) () -> {
                if (e == null || e.isShieldBroken()) return 0f;
                float cur = e.getShieldHp();
                float max = e.getMaxShieldHp();
                if (max <= 0) return 0f;
                return Math.max(0f, Math.min(1f, cur / max));
            }));

        Endyr.reflectProtected = Reflecounum::isProtected;

        // Khi map đổi (xây/phá block) thì đánh dấu để cache block quét lại
        Events.on(TileChangeEvent.class, e -> blockVer++);
        Events.on(BlockBuildEndEvent.class, e -> blockVer++);

        Events.run(Trigger.update, () -> {
            if (Core.graphics == null || !Vars.state.isGame() || Time.time % 30f > Time.delta) return;
            int fps = Core.graphics.getFramesPerSecond();
            if (fps < 25) {
                fpsLow++;
                fpsHigh = 0;
                if (fpsLow >= 2) hideFx = true;
            } else {
                fpsLow = 0;
                fpsHigh++;
                if (fpsHigh >= 3) hideFx = false;
            }
        });

        Events.run(Trigger.beforeGameUpdate, () -> {
            try { applyBuffers(); } catch (Throwable err) { Log.err("[NexReflect] " + err); }
        });
        Events.run(Trigger.afterGameUpdate, () -> {
            try { settle(); } catch (Throwable err) { Log.err("[NexReflect] " + err); }
        });

        // Chia sẻ sát thương Perk 5: chỉ duyệt danh sách Reflecounum
        Events.on(UnitDamageEvent.class, e -> {
            Unit unit = e.unit;
            if (unit == null || !unit.isValid()) return;
            for (int i = 0; i < list.size; i++) {
                ReflecounumTurret.ReflecounumBuild build = list.get(i);
                if (build == null || !build.isValid() || build.team != unit.team) continue;
                if (build.getPerkTier() != 5 || build.isShieldBroken()) continue;
                if (!Mathf.within(build.x, build.y, unit.x, unit.y, 250f)) continue;
                if (e.bullet != null) build.damageShield(e.bullet.damage * 0.50f);
            }
        });
    }

    static void nearbyDisk(Team team, float x, float y, float r, Cons<Unit> cons) {
        Units.nearby(team, x - r, y - r, r * 2f, r * 2f, u -> {
            if (Mathf.within(x, y, u.x, u.y, r + u.hitSize / 2f)) cons.get(u);
        });
    }

    static void nearbyEnemyDisk(Team team, float x, float y, float r, Cons<Unit> cons) {
        Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
            if (Mathf.within(x, y, u.x, u.y, r + u.hitSize / 2f)) cons.get(u);
        });
    }

    static String c(boolean ok) { return ok ? "[green]" : "[red]"; }

    public static class ReflecounumTurret extends Turret {
        public ReflecounumTurret(String name) {
            super(name);
            size = 3;
            health = 23000;
            hasPower = true;
            hasItems = true;
            configurable = true;
            saveConfig = true;

            config(Integer.class, (ReflecounumBuild tile, Integer value) -> {
                if (tile != null && value != null) {
                    int val = value;
                    if (val >= 10) tile.setPerkTier(val - 10);
                    else tile.setTier(val);
                }
            });
            buildType = ReflecounumBuild::new;
        }

        public class ReflecounumBuild extends TurretBuild {
            int tier = 0, perkTier = 0;
            float maxShieldHp = 5000f, shieldHp = 5000f, absorbedDamage = 0f;
            boolean shieldBroken = false;
            float cooldownTimer = 0f, hitTimer = 0f;
            float perk1Timer = 0f, perk2Timer = 0f, rebuildTimer = 0f, perk5Timer = 0f, perk6Absorbed = 0f;
            int orbitingBullets = 0;
            float radius = -1f;
            boolean registered = false;
            BlockCache cb0, cb1;
            float ovAt = -1e9f;
            java.util.List<Ov> ov = new java.util.ArrayList<>();
            final Cons<Bullet> bulletCons = b -> onBullet(b);

            @Override
            public void created() {
                super.created();
                updateMaxShield();
                shieldHp = maxShieldHp;
                register(this);
                registered = true;
            }

            public float getMaxShieldHp() {
                if (Float.isNaN(maxShieldHp) || maxShieldHp <= 0) updateMaxShield();
                return maxShieldHp > 0 ? maxShieldHp : 5000f;
            }

            public float getShieldHp() {
                if (Float.isNaN(shieldHp)) shieldHp = getMaxShieldHp();
                return Math.max(0f, shieldHp);
            }

            public boolean isShieldBroken() { return shieldBroken; }
            public int getTier() { return tier; }
            public void setTier(int val) { tier = val; updateMaxShield(); }
            public int getPerkTier() { return perkTier; }
            public void setPerkTier(int val) { perkTier = val; updateMaxShield(); }

            public void damageShield(float amount) {
                if (isShieldBroken()) return;
                shieldHp = getShieldHp() - amount;
                absorbedDamage += amount;
                if (shieldHp <= 0) {
                    shieldHp = 0f;
                    shieldBroken = true;
                    cooldownTimer = 0f;
                    dirty = true;
                    Fx.shieldBreak.at(x, y, getShieldRadius(), reflectorColor);
                }
            }

            float calcRadius() {
                float baseRadius = (block.size * Vars.tilesize) / 2f + 8f;
                float perkMult = 3.0f;
                int p = getPerkTier();
                if (p == 1) perkMult += 1.50f;
                else if (p == 2) perkMult += 0.50f;
                else if (p == 3) perkMult += 0.30f;
                else if (p == 4) perkMult += 1.00f;
                else if (p == 5) perkMult += 2.00f;
                else if (p == 6) perkMult += 2.00f;
                return baseRadius * perkMult;
            }

            public float getShieldRadius() {
                if (radius < 0) radius = calcRadius();
                return radius;
            }

            void updateMaxShield() {
                int t = getTier();
                float baseMax = 5000f;
                if (t == 1) baseMax = 7500f;
                else if (t == 2) baseMax = 15000f;

                float perkMult = 1.0f;
                int p = getPerkTier();
                if (p == 1) perkMult += 0.50f;
                else if (p == 2) perkMult += 0.90f;
                else if (p == 3) perkMult += 0.30f;
                else if (p == 4) perkMult += 1.00f;
                else if (p == 5) perkMult += 3.00f;
                else if (p == 6) perkMult += 3.00f;

                float oldMax = maxShieldHp > 0 ? maxShieldHp : baseMax;
                maxShieldHp = baseMax * perkMult;
                radius = calcRadius();

                if (!shieldBroken) {
                    shieldHp = Math.min(maxShieldHp, (shieldHp / oldMax) * maxShieldHp);
                }
            }

            float getAbsorbRatio() {
                int t = getTier();
                if (t == 1) return 0.80f;
                if (t == 2) return 0.40f;
                return 0.50f;
            }

            float getReflectingChance() {
                int t = getTier();
                if (t == 1) return 0.75f;
                if (t == 2) return 1.00f;
                return 0.60f;
            }

            /** Xử lý 1 viên đạn đi vào khiên. */
            void onBullet(Bullet b) {
                if (isShieldBroken()) return;
                if (b == null || b.team == team || b.type == null) return;

                float currentRadius = getShieldRadius();
                if (!Mathf.within(x, y, b.x, b.y, currentRadius)) return;

                int perk = getPerkTier();

                hitTimer = 25f;
                float rawDamage = b.damage;
                float absorbRatio = getAbsorbRatio();
                float absorbed = rawDamage * absorbRatio;

                damageShield(absorbed);

                if (isShieldBroken()) return;

                if (perk == 3 && absorbedDamage >= 1000f) {
                    shockwaveFx.at(x, y);
                    nearbyEnemyDisk(team, x, y, 200f, enemy -> {
                        if (enemy != null && enemy.isValid()) enemy.damage(100f);
                    });
                    nearbyDisk(team, x, y, 200f, ally -> {
                        if (ally != null && ally.isValid()) ally.heal(100f);
                    });
                    Vars.indexer.eachBlock(team, x, y, 200f, bb -> true, build -> {
                        if (build != null && build.isValid()) build.heal(100f);
                    });
                }

                if (perk == 6) {
                    perk6Absorbed += absorbed;
                    if (perk6Absorbed >= 100f && orbitingBullets < 8) {
                        orbitingBullets++;
                        perk6Absorbed -= 100f;
                    }

                    if (absorbedDamage >= 1000f && orbitingBullets >= 8) {
                        Teamc target = Units.bestTarget(team, x, y, 600f, e -> true, bb -> true, (u, ux, uy) -> -u.maxHealth);
                        float targetX = target != null ? target.getX() : x + 100f;
                        float targetY = target != null ? target.getY() : y + 100f;

                        for (int i = 0; i < 8; i++) {
                            float angle = (i / 8f) * 360f;
                            float bx = x + Mathf.cosDeg(angle) * 20f;
                            float by = y + Mathf.sinDeg(angle) * 20f;
                            perk6Bullet.create(this, team, bx, by, Angles.angle(bx, by, targetX, targetY));
                        }
                        orbitingBullets = 0;
                    }
                }

                if (absorbedDamage >= 1000f) absorbedDamage -= 1000f;

                if (Mathf.chance(getReflectingChance())) {
                    float oldX = b.x;
                    float oldY = b.y;
                    float oldRot = b.rotation();

                    if (perk == 4) {
                        b.remove();
                        Unit target = Units.closestEnemy(team, oldX, oldY, 300f, e -> true);
                        float targetAngle = target != null ? Angles.angle(oldX, oldY, target.x, target.y) : oldRot + 180f;
                        perk4Bullet.damage = rawDamage;
                        perk4Bullet.create(this, team, oldX, oldY, targetAngle);
                    } else {
                        b.team = team;
                        b.rotation(oldRot + 180f);
                        b.vel.trns(oldRot + 180f, b.vel.len());
                    }
                    reflectEffect.at(oldX, oldY, oldRot);
                } else {
                    b.remove();
                }
            }

            @Override
            public void updateTile() {
                super.updateTile();

                if (!registered) {
                    register(this);
                    registered = true;
                }

                if (hitTimer > 0) hitTimer -= Time.delta;

                if (isShieldBroken()) {
                    shieldHp = 0f;
                    cooldownTimer += Time.delta;
                    if (cooldownTimer >= 300f) {
                        updateMaxShield();
                        shieldBroken = false;
                        shieldHp = getMaxShieldHp();
                        cooldownTimer = 0f;
                        dirty = true;
                        Fx.shieldWave.at(x, y, getShieldRadius(), reflectorColor);
                    }
                    return;
                }

                int perk = getPerkTier();
                float currentRadius = getShieldRadius();

                if (perk == 1) {
                    perk1Timer += Time.delta;
                    if (perk1Timer >= 30f) {
                        perk1Timer = 0f;
                        nearbyDisk(team, x, y, 200f, u -> {
                            if (u != null && u.isValid()) {
                                u.heal(u.maxHealth * 0.10f);
                                Fx.heal.at(u.x, u.y);
                            }
                        });
                    }
                }

                if (perk == 2) {
                    perk2Timer += Time.delta;
                    if (perk2Timer >= 60f) {
                        perk2Timer = 0f;
                        Vars.indexer.eachBlock(team, x, y, 350f, bb -> bb.damaged(), bb -> {
                            bb.heal(bb.maxHealth * 0.05f);
                            Fx.healBlock.at(bb.x, bb.y);
                        });
                    }

                    rebuildTimer += Time.delta;
                    if (rebuildTimer >= 300f) {
                        TeamData teamData = team.data();
                        if (teamData != null && teamData.plans != null && teamData.plans.size > 0) {
                            float range = 350f;
                            boolean rebuiltAny = false;

                            for (int i = teamData.plans.size - 1; i >= 0; i--) {
                                BlockPlan plan = teamData.plans.get(i);
                                Block pb = plan != null ? plan.block : null;
                                if (pb == null) continue;
                                float wx = plan.x * Vars.tilesize;
                                float wy = plan.y * Vars.tilesize;
                                if (!Mathf.within(x, y, wx, wy, range)) continue;
                                Tile tile = Vars.world.tile(plan.x, plan.y);
                                if (tile == null) continue;
                                if (tile.block() == pb) {
                                    teamData.plans.removeIndex(i);
                                    continue;
                                }
                                tile.setNet(pb, team, plan.rotation);
                                Fx.chainLightning.at(x, y, 0f, reflectorGlow, new Vec2(wx, wy));
                                Fx.placeBlock.at(wx, wy, pb.size);
                                teamData.plans.removeIndex(i);
                                rebuiltAny = true;
                                break;
                            }
                            if (rebuiltAny) rebuildTimer = 0f;
                        }
                    }
                }

                if (perk == 5) {
                    perk5Timer += Time.delta;
                    if (perk5Timer >= 300f) {
                        perk5Timer = 0f;
                        nearbyDisk(team, x, y, 500f, u -> {
                            if (u != null && u.isValid()) {
                                u.heal(u.maxHealth * 0.10f);
                                Fx.heal.at(u.x, u.y);
                            }
                        });
                        Vars.indexer.eachBlock(team, x, y, 500f, bb -> bb.damaged(), bb -> {
                            if (bb != null && bb.isValid()) {
                                bb.heal(bb.maxHealth * 0.10f);
                                Fx.healBlock.at(bb.x, bb.y);
                            }
                        });
                    }
                }

                if (Groups.bullet != null && Groups.bullet.size() > 0) {
                    try {
                        Groups.bullet.intersect(x - currentRadius, y - currentRadius, currentRadius * 2f, currentRadius * 2f, bulletCons);
                    } catch (Throwable ignored) {}
                }
            }

            java.util.List<Ov> getOverlaps(float r) {
                if (Time.time - ovAt > 20f || Time.time < ovAt) {
                    ovAt = Time.time;
                    java.util.List<Ov> out = new java.util.ArrayList<>();
                    for (int i = 0; i < list.size; i++) {
                        ReflecounumBuild o = list.get(i);
                        if (o.id == id || !o.isValid() || o.team != team) continue;
                        if (o.isShieldBroken() || o.getShieldHp() <= 0) continue;
                        float orad = o.getShieldRadius();
                        if (Mathf.dst(x, y, o.x, o.y) < r + orad) out.add(new Ov(o.x, o.y, orad));
                    }
                    ov = out;
                }
                return ov;
            }

            /** Hiệu ứng khi trúng đạn + đạn năng lượng quay quanh (dùng chung 2 chế độ). */
            void drawShieldExtras(float currentRadius, boolean isHit, float hitFactor) {
                if (isHit) {
                    Draw.color(reflectorGlow, hitFactor);
                    Lines.stroke(1.2f);
                    float innerRad = currentRadius * 0.65f;
                    Lines.poly(x, y, 6, innerRad);
                    for (int i = 0; i < 6; i++) {
                        float a1 = i * 60, a2 = (i + 1) * 60;
                        Lines.line(
                            x + Mathf.cosDeg(a1) * innerRad, y + Mathf.sinDeg(a1) * innerRad,
                            x + Mathf.cosDeg(a2) * currentRadius, y + Mathf.sinDeg(a2) * currentRadius);
                    }
                }
                if (getPerkTier() == 6 && orbitingBullets > 0) {
                    Draw.color(reflectorGlow);
                    for (int i = 0; i < orbitingBullets; i++) {
                        float angle = (i / 8f) * 360f + Time.time * 2f;
                        Fill.circle(x + Mathf.cosDeg(angle) * 20f, y + Mathf.sinDeg(angle) * 20f, 3.5f);
                    }
                }
            }

            /** Effect khiên CŨ (chưa tối ưu) - dùng khi nút tối ưu đang TẮT. */
            void drawShieldLegacy(float currentRadius) {
                if (!PerfCore.onScreen(x, y, currentRadius + 16f)) return;

                boolean isHit = hitTimer > 0;
                float hitFactor = isHit ? (hitTimer / 25f) : 0f;
                boolean animated = Vars.renderer != null && Vars.renderer.animateShields;

                if (animated) {
                    Draw.z(Layer.shields + 0.001f * hitFactor);
                    Draw.color(reflectorColor, Color.white, hitFactor);
                    Fill.poly(x, y, 6, currentRadius);
                    Draw.z(Layer.shields + 2f);
                } else {
                    Draw.z(Layer.shields);
                    java.util.List<Ov> others = getOverlaps(currentRadius);

                    float coreAlpha = 0.08f + (hitFactor * 0.25f);
                    Draw.color(isHit ? reflectorGlow : reflectorColor, coreAlpha);
                    Fill.poly(x, y, 6, currentRadius);

                    Lines.stroke(isHit ? 2.8f : 1.5f);
                    Draw.color(isHit ? Color.white : reflectorColor, 0.8f + (hitFactor * 0.2f));
                    if (others.isEmpty()) Lines.poly(x, y, 6, currentRadius);
                    else drawHexClipped(x, y, currentRadius, others);

                    Draw.color(reflectorGlow, 0.5f + (hitFactor * 0.5f));
                    Lines.stroke(1.0f);
                    if (others.isEmpty()) Lines.poly(x, y, 6, currentRadius - 3f);
                    else drawHexClipped(x, y, currentRadius - 3f, others);
                }
                drawShieldExtras(currentRadius, isHit, hitFactor);
                Draw.reset();
            }

            /** Effect khiên TỐI ƯU - dùng khi nút tối ưu đang BẬT. */
            void drawShieldOpt(float currentRadius) {
                if (!camVisible(x, y, currentRadius + 16f)) return;

                boolean isHit = hitTimer > 0;
                float hitFactor = isHit ? (hitTimer / 25f) : 0f;
                boolean animated = Vars.renderer != null && Vars.renderer.animateShields;

                if (animated) {
                    Draw.z(Layer.shields + 0.001f * hitFactor);
                    Draw.color(reflectorColor, Color.white, hitFactor);
                    Fill.poly(x, y, 6, currentRadius);
                    Draw.z(Layer.shields + 2f);
                } else {
                    Draw.z(Layer.shields);
                    float coreAlpha = 0.08f + (hitFactor * 0.25f);
                    Draw.color(isHit ? reflectorGlow : reflectorColor, coreAlpha);
                    Fill.poly(x, y, 6, currentRadius);

                    Lines.stroke(isHit ? 2.8f : 1.5f);
                    Draw.color(isHit ? Color.white : reflectorColor, 0.8f + (hitFactor * 0.2f));
                    Lines.poly(x, y, 6, currentRadius);

                    float scale = Vars.renderer != null ? Vars.renderer.getScale() : 1f;
                    if (scale >= 0.9f) {
                        Draw.color(reflectorGlow, 0.5f + (hitFactor * 0.5f));
                        Lines.stroke(1.0f);
                        Lines.poly(x, y, 6, currentRadius - 3f);
                    }
                }
                drawShieldExtras(currentRadius, isHit, hitFactor);
                Draw.reset();
            }

            @Override
            public void draw() {
                super.draw();
                if (isShieldBroken() || getShieldHp() <= 0) return;
                if (hideFx) return;
                float currentRadius = getShieldRadius();
                if (optFx) drawShieldOpt(currentRadius);
                else drawShieldLegacy(currentRadius);
            }

            String reqSnapshot() {
                CoreBuild core = team.core();
                if (core == null) return isEn() ? "[red]Team Core Not Found![]" : "[red]Không tìm thấy Lõi Đội![]";

                int currentCopper = core.items.get(Items.copper);
                int currentLead = core.items.get(Items.lead);
                int currentTitanium = core.items.get(Items.titanium);
                int currentSilicon = core.items.get(Items.silicon);
                int currentThorium = core.items.get(Items.thorium);
                int currentPlastanium = core.items.get(Items.plastanium);
                int currentSurge = core.items.get(Items.surgeAlloy);

                String copCol = c(currentCopper >= reqSpecial.copper);
                String leaCol = c(currentLead >= reqSpecial.lead);
                String silColSp = c(currentSilicon >= reqSpecial.silicon);
                String titColor1 = c(currentTitanium >= reqMK2.titanium);
                String silColor1 = c(currentSilicon >= reqMK2.silicon);
                String thoColor1 = c(currentThorium >= reqMK2.thorium);
                String titColor2 = c(currentTitanium >= reqMK2B.titanium);
                String silColor2 = c(currentSilicon >= reqMK2B.silicon);
                String plaColor2 = c(currentPlastanium >= reqMK2B.plastanium);
                String surColor2 = c(currentSurge >= reqMK2B.surgeAlloy);

                if (isEn()) {
                    return "[gold]CORE RESOURCE REQUIREMENTS:[]\n" +
                           "[orange]★ SPECIAL PERK:[] Copper: " + copCol + currentCopper + "[]/4000 | Lead: " + leaCol + currentLead + "[]/4000 | Silicon: " + silColSp + currentSilicon + "[]/4000\n" +
                           "[cyan]MK2 Branch:[] Titanium: " + titColor1 + currentTitanium + "[]/" + reqMK2.titanium + " | Silicon: " + silColor1 + currentSilicon + "[]/" + reqMK2.silicon + " | Thorium: " + thoColor1 + currentThorium + "[]/" + reqMK2.thorium + "\n" +
                           "[purple]MK2B Branch:[] Titanium: " + titColor2 + currentTitanium + "[]/" + reqMK2B.titanium + " | Silicon: " + silColor2 + currentSilicon + "[]/" + reqMK2B.silicon + " | Plastanium: " + plaColor2 + currentPlastanium + "[]/" + reqMK2B.plastanium + " | Surge: " + surColor2 + currentSurge + "[]/" + reqMK2B.surgeAlloy;
                }
                return "[gold]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                       "[orange]★ PHÚC LỢI ĐẶC BIỆT:[] Đồng: " + copCol + currentCopper + "[]/4000 | Chì: " + leaCol + currentLead + "[]/4000 | Silicon: " + silColSp + currentSilicon + "[]/4000\n" +
                       "[cyan]Nhánh MK2:[] Titan: " + titColor1 + currentTitanium + "[]/" + reqMK2.titanium + " | Silicon: " + silColor1 + currentSilicon + "[]/" + reqMK2.silicon + " | Thorium: " + thoColor1 + currentThorium + "[]/" + reqMK2.thorium + "\n" +
                       "[purple]Nhánh MK2B:[] Titan: " + titColor2 + currentTitanium + "[]/" + reqMK2B.titanium + " | Silicon: " + silColor2 + currentSilicon + "[]/" + reqMK2B.silicon + " | Nhựa: " + plaColor2 + currentPlastanium + "[]/" + reqMK2B.plastanium + " | Surge: " + surColor2 + currentSurge + "[]/" + reqMK2B.surgeAlloy;
            }

            void openUpgradeDialog() {
                final int tier = getTier();
            String dialogTitle = isEn() ? "Reflecounum Turret Upgrade Center" : "Trung tâm nâng cấp pháo Reflecounum";
                BaseDialog dialog = new BaseDialog(dialogTitle);

                Cell<?> reqCell = dialog.cont.add(reqSnapshot());
                reqCell.width(380f);
                ((arc.scene.ui.Label) reqCell.get()).setWrap(true);
                ((arc.scene.ui.Label) reqCell.get()).setAlignment(Align.left);
                dialog.cont.row();
                dialog.cont.add().height(10f).row();

                Table branchesTable = new Table();

                Table spBox = new Table();
                spBox.background(Styles.black6);
                spBox.margin(12f);
                spBox.add(isEn() ? "[gold]★ SPECIAL UPGRADE PERKS (RANDOM) ★[]" : "[gold]★ PHÚC LỢI NÂNG CẤP ĐẶC BIỆT (NGẪU NHIÊN) ★[]").row();

                int currentPerk = getPerkTier();

                if (currentPerk == 0) {
                    String spDText = isEn() ?
                    "Activate random protocol to obtain 1 of 6 permanent perks:\n" +
                    " • [yellow]Perk 1 (~19.6%):[] Shield radius +150%, Shield HP +50%, Heal 10% HP/0.5s for Units within 200px.\n" +
                    " • [orange]Perk 2 (~29.4%):[] Shield radius +50%, Shield HP +90%, Auto rebuild walls & turrets within 350px (every 5s).\n" +
                    " • [cyan]Perk 3 (~19.6%):[] Shield radius +30%, Shield HP +30%, Store 1000 Dmg to release 100 Dmg shockwave (200px) & heal allies 100 HP.\n" +
                    " • [purple]Perk 4 (~29.4%):[] Shield radius +100%, Shield HP +100%, Reflected bullets gain Homing & Pierce 4.\n" +
                    " • [green]Perk 5 (1% ULTRA RARE):[] Shield radius +200%, Shield HP +300%, Heal 10% HP/5s within 500px, Share 50% ally damage to shield.\n" +
                    " • [red]Perk 6 (1% ULTRA RARE):[] Shield radius +200%, Shield HP +300%, Store 100 Dmg to spawn orbiting energy, 1000 Dmg fires 8 piercing bullets at highest HP target!" :
                    "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 6 phúc lợi vĩnh viễn:\n" +
                    " • [yellow]Phúc lợi 1 (~19.6%):[] Bán kính khiên +150%, HP khiên +50%, Hồi 10% HP/0.5s cho Unit trong 200px.\n" +
                    " • [orange]Phúc lợi 2 (~29.4%):[] Bán kính khiên +50%, HP khiên +90%, Tự động xây lại tường & pháo bị nổ trong 350px (mỗi 5s).\n" +
                    " • [cyan]Phúc lợi 3 (~19.6%):[] Bán kính khiên +30%, HP khiên +30%, Tích 1000 Dmg nổ sóng âm 100 Dmg (200px) & hồi 100 HP cho đồng minh.\n" +
                    " • [purple]Phúc lợi 4 (~29.4%):[] Bán kính khiên +100%, HP khiên +100%, Đạn phản có Truy đuổi & Xuyên thấu 4.\n" +
                    " • [green]Phúc lợi 5 (1% SIÊU HIẾM):[] Bán kính khiên +200%, HP khiên +300%, Hồi 10% HP/5s trong 500px, Chia sẻ 50% Dmg từ đồng minh vào khiên.\n" +
                    " • [red]Phúc lợi 6 (1% SIÊU HIẾM):[] Bán kính khiên +200%, HP khiên +300%, Tích 100 Dmg đẻ 1 đạn năng lượng quanh pháo, Đủ 1000 Dmg bắn 8 đạn xé gió mục tiêu HP cao nhất!";

                    Cell<?> spD = spBox.add(spDText);
                    spD.width(360f);
                    ((arc.scene.ui.Label) spD.get()).setWrap(true);
                    ((arc.scene.ui.Label) spD.get()).setAlignment(Align.left);
                    spBox.row();

                String rollBtnText = isEn() ? "[gold]ROLL PERK (4K Copper/Lead/Silicon)[]" : "[gold]QUAY PHÚC LỢI (4K Đồng/Chì/Silicon)[]";
                    spBox.button(rollBtnText, () -> {
                        CoreBuild core = team.core();
                        if (core != null && core.items.get(Items.copper) >= 4000 && core.items.get(Items.lead) >= 4000 && core.items.get(Items.silicon) >= 4000) {
                            NexNet.take(core, Items.copper, 4000);
                            NexNet.take(core, Items.lead, 4000);
                            NexNet.take(core, Items.silicon, 4000);

                            float rand = Mathf.random(100f);
                            int resultPerk;
                            if (rand < 1.0f) resultPerk = 5;
                            else if (rand < 2.0f) resultPerk = 6;
                            else if (rand < 2.0f + 19.6f) resultPerk = 1;
                            else if (rand < 2.0f + 19.6f + 29.4f) resultPerk = 2;
                            else if (rand < 2.0f + 19.6f + 29.4f + 19.6f) resultPerk = 3;
                            else resultPerk = 4;

                            setPerkTier(resultPerk);
                            configure(Integer.valueOf(10 + resultPerk));

                            NexNet.fx(Fx.upgradeCore, x, y);
                            NexNet.shake(6f, 6f, x, y);

                            String perkName = "";
                        if (isEn()) {
                            if (resultPerk == 1) perkName = "[yellow]PERK 1[]";
                            else if (resultPerk == 2) perkName = "[orange]PERK 2[]";
                            else if (resultPerk == 3) perkName = "[cyan]PERK 3[]";
                            else if (resultPerk == 4) perkName = "[purple]PERK 4[]";
                            else if (resultPerk == 5) perkName = "[green]★ PERK 5 (1% ULTRA RARE) ★[]";
                            else perkName = "[red]★ PERK 6 (1% ULTRA RARE) ★[]";
                            Vars.ui.showInfo("[gold]YOU OBTAINED:[]\n" + perkName);
                        } else {
                            if (resultPerk == 1) perkName = "[yellow]PHÚC LỢI 1[]";
                            else if (resultPerk == 2) perkName = "[orange]PHÚC LỢI 2[]";
                            else if (resultPerk == 3) perkName = "[cyan]PHÚC LỢI 3[]";
                            else if (resultPerk == 4) perkName = "[purple]PHÚC LỢI 4[]";
                            else if (resultPerk == 5) perkName = "[green]★ PHÚC LỢI 5 (1% SIÊU HIẾM) ★[]";
                            else perkName = "[red]★ PHÚC LỢI 6 (1% SIÊU HIẾM) ★[]";
                            Vars.ui.showInfo("[gold]BẠN ĐÃ TRÚNG:[]\n" + perkName);
                        }

                            dialog.hide();
                            deselect();
                        } else {
                        Vars.ui.showInfo(isEn() ? "[red]Not enough resources for Special Perk![]" : "[red]Không đủ tài nguyên cho Phúc Lợi Đặc Biệt![]");
                        }
                    }).size(300f, 40f);
                } else {
                    String perkText = "";
                if (isEn()) {
                    if (currentPerk == 1) perkText = "[yellow]✔ ACTIVATED: PERK 1\n• Shield radius +150%\n• Shield HP +50%\n• Heal 10% HP/0.5s for ally Units in 200px[]";
                    if (currentPerk == 2) perkText = "[orange]✔ ACTIVATED: PERK 2\n• Shield radius +50%\n• Shield HP +90%\n• Auto rebuild destroyed walls & turrets in 350px (every 5s)[]";
                    if (currentPerk == 3) perkText = "[cyan]✔ ACTIVATED: PERK 3\n• Shield radius +30%\n• Shield HP +30%\n• Store 1000 Dmg to release 100 Dmg shockwave (200px) & heal allies 100 HP[]";
                    if (currentPerk == 4) perkText = "[purple]✔ ACTIVATED: PERK 4\n• Shield radius +100%\n• Shield HP +100%\n• Reflected bullets gain Homing & Pierce 4[]";
                    if (currentPerk == 5) perkText = "[green]✔ ACTIVATED: PERK 5 (1% ULTRA RARE)\n• Shield radius +200%\n• Shield HP +300%\n• Heal 10% HP/5s for Units & Structures in 500px\n• Share 50% ally damage taken in 250px[]";
                    if (currentPerk == 6) perkText = "[red]✔ ACTIVATED: PERK 6 (1% ULTRA RARE)\n• Shield radius +200%\n• Shield HP +300%\n• Store 1000 Dmg to fire 8 piercing energy bullets (Pierce 20, Splash Dmg 20px) at highest HP target![]";
                } else {
                    if (currentPerk == 1) perkText = "[yellow]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1\n• Bán kính khiên +150%\n• Độ bền khiên +50%\n• Hồi 10% HP/0.5s cho Unit đồng minh trong 200px[]";
                    if (currentPerk == 2) perkText = "[orange]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2\n• Bán kính khiên +50%\n• Độ bền khiên +90%\n• Tự động xây lại tường & pháo bị hủy hoàn toàn trong 350px (mỗi 5s)[]";
                    if (currentPerk == 3) perkText = "[cyan]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3\n• Bán kính khiên +30%\n• Độ bền khiên +30%\n• Tích 1000 Dmg nổ sóng âm 100 Dmg (200px) & hồi 100 HP cho đồng minh[]";
                    if (currentPerk == 4) perkText = "[purple]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 4\n• Bán kính khiên +100%\n• Độ bền khiên +100%\n• Đạn phản ngược có Truy đuổi & Xuyên thấu 4[]";
                    if (currentPerk == 5) perkText = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 5 (1% SIÊU HIẾM)\n• Bán kính khiên +200%\n• Độ bền khiên +300%\n• Hồi 10% HP/5s cho Unit & Công trình trong 500px\n• Chia sẻ 50% Dmg nhận vào từ đồng minh trong 250px[]";
                    if (currentPerk == 6) perkText = "[red]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 6 (1% SIÊU HIẾM)\n• Bán kính khiên +200%\n• Độ bền khiên +300%\n• Đủ 1000 Dmg hấp thụ bắn 8 đạn năng lượng xé gió (Xuyên 20, Splash Dmg 20px) vào mục tiêu HP cao nhất![]";
                }

                    Cell<?> spD = spBox.add(perkText);
                    spD.width(360f);
                    ((arc.scene.ui.Label) spD.get()).setWrap(true);
                    ((arc.scene.ui.Label) spD.get()).setAlignment(Align.left);
                }

                branchesTable.add(spBox).width(360f);
                branchesTable.row();
                branchesTable.add().height(12f).row();

                if (tier == 0) {
                    Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
                b1.add(isEn() ? "[cyan]===(MK2 - SHIELD DEFENDER)===[]" : "[cyan]===(MK2 - TRỐNG THỦ KHIÊN)===[]").row();

                    String b1DText = isEn() ?
                    "[white]• Shield Dmg Absorb: [green]80%[] (+30%)\n" +
                    "• Base Shield HP: [green]7,500 HP[] (+50%)\n" +
                    "• Reflection Chance: [yellow]75%[] (+15%)\n" +
                    "• Shield Cooldown: [white]5.0 seconds[]" :
                    "[white]• Hấp thụ sát thương khiên: [green]80%[] (+30%)\n" +
                    "• Độ bền khiên gốc: [green]7,500 HP[] (+50%)\n" +
                    "• Tỷ lệ phản ngược đạn: [yellow]75%[] (+15%)\n" +
                    "• Thời gian hồi khi khiên vỡ: [white]5.0 giây[]";

                    Cell<?> b1D = b1.add(b1DText);
                    b1D.width(340f);
                    ((arc.scene.ui.Label) b1D.get()).setWrap(true);
                    ((arc.scene.ui.Label) b1D.get()).setAlignment(Align.left);
                    b1.row();

                String b1Btn = isEn() ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]";
                    b1.button(b1Btn, () -> {
                        CoreBuild core = team.core();
                        if (core != null && core.items.get(Items.titanium) >= reqMK2.titanium && core.items.get(Items.silicon) >= reqMK2.silicon && core.items.get(Items.thorium) >= reqMK2.thorium) {
                            NexNet.take(core, Items.titanium, reqMK2.titanium);
                            NexNet.take(core, Items.silicon, reqMK2.silicon);
                            NexNet.take(core, Items.thorium, reqMK2.thorium);

                            NexNet.fx(Fx.upgradeCore, x, y);
                            NexNet.fx(Fx.shieldWave, x, y, getShieldRadius(), reflectorColor);
                            NexNet.shake(4f, 4f, x, y);
                            setTier(1);
                            configure(Integer.valueOf(1));
                            dialog.hide();
                            deselect();
                        } else {
                            Vars.ui.showInfo(isEn() ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
                        }
                    }).size(180f, 38f);

                    Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
                b2.add(isEn() ? "[purple]===(MK2B - ABSOLUTE REFLECTION)===[]" : "[purple]===(MK2B - PHẢN XẠ TUYỆT ĐỐI)===[]").row();

                    String b2DText = isEn() ?
                    "[white]• Shield Dmg Absorb: [red]40%[] (-10%)\n" +
                    "• Base Shield HP: [green]15,000 HP[] (+200%)\n" +
                    "• Reflection Chance: [gold]100% ABSOLUTE[]\n" +
                    "• Shield Cooldown: [green]2.0 seconds[] (Super Fast)" :
                    "[white]• Hấp thụ sát thương khiên: [red]40%[] (-10%)\n" +
                    "• Độ bền khiên gốc: [green]15,000 HP[] (+200%)\n" +
                    "• Tỷ lệ phản ngược đạn: [gold]100% TUYỆT ĐỐI[]\n" +
                    "• Thời gian hồi khi khiên vỡ: [green]2.0 giây[] (Siêu tốc)";

                    Cell<?> b2D = b2.add(b2DText);
                    b2D.width(340f);
                    ((arc.scene.ui.Label) b2D.get()).setWrap(true);
                    ((arc.scene.ui.Label) b2D.get()).setAlignment(Align.left);
                    b2.row();

                String b2Btn = isEn() ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]";
                    b2.button(b2Btn, () -> {
                        CoreBuild core = team.core();
                        if (core != null && core.items.get(Items.titanium) >= reqMK2B.titanium && core.items.get(Items.silicon) >= reqMK2B.silicon && core.items.get(Items.plastanium) >= reqMK2B.plastanium && core.items.get(Items.surgeAlloy) >= reqMK2B.surgeAlloy) {
                            NexNet.take(core, Items.titanium, reqMK2B.titanium);
                            NexNet.take(core, Items.silicon, reqMK2B.silicon);
                            NexNet.take(core, Items.plastanium, reqMK2B.plastanium);
                            NexNet.take(core, Items.surgeAlloy, reqMK2B.surgeAlloy);

                            NexNet.fx(Fx.bigShockwave, x, y);
                            NexNet.fx(Fx.shieldWave, x, y, getShieldRadius(), reflectorColor);
                            NexNet.shake(4f, 4f, x, y);
                            setTier(2);
                            configure(Integer.valueOf(2));
                            dialog.hide();
                            deselect();
                        } else {
                            Vars.ui.showInfo(isEn() ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
                        }
                    }).size(180f, 38f);

                    branchesTable.add(b1).width(340f); branchesTable.row();
                    branchesTable.add().height(12f).row();
                    branchesTable.add(b2).width(340f);
                } else {
                    String statusLabel = "";
                if (isEn()) {
                    statusLabel = (tier == 1) ? "[cyan]UPGRADED TO MK2 TURRET[]" : "[purple]UPGRADED TO MK2B TURRET[]";
                } else {
                    statusLabel = (tier == 1) ? "[cyan]ĐÃ NÂNG CẤP THÀNH PHÁO MK2[]" : "[purple]ĐÃ NÂNG CẤP THÀNH PHÁO MK2B[]";
                }
                    branchesTable.add(statusLabel).row();
                }

                ScrollPane scroll = new ScrollPane(branchesTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(420f);
                dialog.addCloseButton();
                dialog.show();
            }

            void openInfoDialog() {
            String title = isEn() ? " Reflecounum Turret Stats " : " Thông số pháo Reflecounum ";
                String descStr = "";
                int currentTier = getTier();
            if (isEn()) {
                if (currentTier == 0) descStr = "[gold]⚡ BASE STATS (MK1) ⚡[]\n• Shield HP: 5,000 HP | Absorb Rate: 50%\n• Reflect Chance: 60% | Recharge Cooldown: 5.0s";
                else if (currentTier == 1) descStr = "[cyan]⚡ BASE STATS (MK2) ⚡[]\n• Shield HP: 7,500 HP | Absorb Rate: 80%\n• Reflect Chance: 75% | Recharge Cooldown: 5.0s";
                else if (currentTier == 2) descStr = "[purple]⚡ BASE STATS (MK2B) ⚡[]\n• Shield HP: 15,000 HP | Absorb Rate: 40%\n• Reflect Chance: 100% ABSOLUTE | Recharge Cooldown: 2.0s";

                int perk = this.getPerkTier();
                if (perk > 0) {
                    descStr += "\n\n[gold]★ ACTIVATED SPECIAL PERK ★[]";
                    if (perk == 1) descStr += "\n[yellow]• Perk 1: Shield radius +150%, Shield HP +50%, Heal 10% HP/0.5s for Units in 200px.[]";
                    if (perk == 2) descStr += "\n[orange]• Perk 2: Shield radius +50%, Shield HP +90%, Auto rebuild walls & turrets in 350px (every 5s).[]";
                    if (perk == 3) descStr += "\n[cyan]• Perk 3: Shield radius +30%, Shield HP +30%, Store 1000 Dmg shockwave 100 Dmg & heal 100 HP to allies.[]";
                    if (perk == 4) descStr += "\n[purple]• Perk 4: Shield radius +100%, Shield HP +100%, Reflected bullets gain Homing & Pierce 4.[]";
                    if (perk == 5) descStr += "\n[green]• Perk 5 (1%): Shield radius +200%, Shield HP +300%, Heal 10% HP/5s in 500px, Share 50% ally Dmg to shield.[]";
                    if (perk == 6) descStr += "\n[red]• Perk 6 (1%): Shield radius +200%, Shield HP +300%, Store 1000 Dmg to fire 8 piercing bullets at highest HP target.[]";
                }
            } else {
                if (currentTier == 0) descStr = "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n• Độ bền khiên: 5,000 HP | Tỷ lệ hấp thụ: 50%\n• Tỷ lệ phản đạn: 60% | Tái nạp khi vỡ: 5.0s";
                else if (currentTier == 1) descStr = "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n• Độ bền khiên: 7,500 HP | Tỷ lệ hấp thụ: 80%\n• Tỷ lệ phản đạn: 75% | Tái nạp khi vỡ: 5.0s";
                else if (currentTier == 2) descStr = "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n• Độ bền khiên: 15,000 HP | Tỷ lệ hấp thụ: 40%\n• Tỷ lệ phản đạn: 100% TUYỆT ĐỐI | Tái nạp khi vỡ: 2.0s";

                int perk = this.getPerkTier();
                if (perk > 0) {
                    descStr += "\n\n[gold]★ ĐÃ KÍCH HOẠT PHÚC LỢI ĐẶC BIỆT ★[]";
                    if (perk == 1) descStr += "\n[yellow]• Phúc lợi 1: Bán kính khiên +150%, HP khiên +50%, Hồi 10% HP/0.5s cho Unit trong 200px.[]";
                    if (perk == 2) descStr += "\n[orange]• Phúc lợi 2: Bán kính khiên +50%, HP khiên +90%, Tự động xây lại tường & pháo bị hủy hoàn toàn trong 350px (mỗi 5s).[]";
                    if (perk == 3) descStr += "\n[cyan]• Phúc lợi 3: Bán kính khiên +30%, HP khiên +30%, Tích 1000 Dmg nổ sóng âm 100 Dmg & hồi 100 HP cho đồng minh.[]";
                    if (perk == 4) descStr += "\n[purple]• Phúc lợi 4: Bán kính khiên +100%, HP khiên +100%, Đạn phản ngược có Truy đuổi & Xuyên 4.[]";
                    if (perk == 5) descStr += "\n[green]• Phúc lợi 5 (1%): Bán kính khiên +200%, HP khiên +300%, Hồi 10% HP/5s trong 500px, Chia sẻ 50% Dmg từ đồng minh vào khiên.[]";
                    if (perk == 6) descStr += "\n[red]• Phúc lợi 6 (1%): Bán kính khiên +200%, HP khiên +300%, Tích 1000 Dmg bắn 8 đạn xé gió mục tiêu HP cao nhất.[]";
                }
            }

                BaseDialog dialog = new BaseDialog(title);
                Table infoTable = new Table();
                Cell<?> cell = infoTable.add(descStr).width(360f);
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

                table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog).size(50f, 40f)
                    .tooltip(isEn() ? "Upgrade Reflecounum System" : "Nâng cấp hệ thống Reflecounum");
                table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog).size(50f, 40f)
                    .tooltip(isEn() ? "View Detailed Turret Stats" : "Xem thông số chi tiết pháo");

                // Nút bật/tắt effect khiên tối ưu (áp dụng cho mọi khối Reflecounum, được lưu lại)
                table.button(Icon.eye, Styles.clearTogglei, 40f, () -> {
                    boolean on = !optFx;
                    setOptFx(on);
                    if (isEn()) {
                        Vars.ui.showInfoToast(on ? "[green]Optimized shield effect: ON[]" : "[orange]Optimized shield effect: OFF (old effect)[]", 2f);
                    } else {
                        Vars.ui.showInfoToast(on ? "[green]Effect khiên tối ưu: BẬT[]" : "[orange]Effect khiên tối ưu: TẮT (effect cũ)[]", 2f);
                    }
                }).size(50f, 40f).update(b -> b.setChecked(optFx))
                    .tooltip(isEn() ? "Toggle optimized shield effect" : "Bật/tắt effect khiên tối ưu");
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.b(getTier());
                write.b(getPerkTier());
                write.f(getShieldHp());
                write.f(absorbedDamage);
                write.bool(isShieldBroken());
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                tier = read.b();
                // JS đọc byte phúc lợi chỉ khi revision >= 1 nhưng version() không bao giờ >= 1 => lệch luồng (bài 101).
                perkTier = read.b();
                shieldHp = read.f();
                absorbedDamage = read.f();
                shieldBroken = read.bool();

                radius = -1f;
                updateMaxShield();
                if (Float.isNaN(shieldHp)) shieldHp = getMaxShieldHp();
            }
        }
    }
}
