package newex;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.struct.IntFloatMap;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType.BuildDamageEvent;
import mindustry.game.EventType.StateChangeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.ui.Fonts;

/**
 * Chuyển từ damage-display.js (global.newexCrit).
 * Hiện sát thương nổi trên đầu mục tiêu. Chữ CRIT (tím nhạt) nảy ngay sau số dmg khi bạo kích.
 * Tối ưu hiệu năng: dùng IntFloatMap/IntMap (0 boxing), pool Popup, giới hạn số popup trên Mobile/FPS thấp và cache chuỗi hiển thị.
 */
public final class DamageDisplay {
    private static final float MISSING = -1e30f;
    private static final Rect rect = new Rect();
    private static final Seq<Popup> popups = new Seq<>();
    private static final Seq<Popup> popupPool = new Seq<>();
    private static final Color tmp = new Color();
    private static final GlyphLayout layout = new GlyphLayout();

    private static final IntFloatMap hpCache = new IntFloatMap();
    private static final IntMap<Dmg> dmgCache = new IntMap<>();
    private static final IntFloatMap critMap = new IntFloatMap();
    private static final IntFloatMap lastDmgAt = new IntFloatMap();

    private static final String CRIT = "[#d9b3ff]CRIT![]";

    private static final int TYPE_EAT = 0, TYPE_CRIT = 1, TYPE_FLOAT = 2;

    /** Tổng sát thương cộng dồn của 1 mục tiêu ở chế độ "eatsuki". */
    private static final class Dmg {
        float total = 0f;
        float idle = 0f;
        Popup popup = null;
    }

    private static final class Popup {
        int type;
        int id;
        float x, y, size;
        boolean isUnit;
        float total, amount, dx;
        boolean heal;
        int lane;
        float life, max, critAge;
        String cachedTxt;
        float cachedVal = -1f;
        boolean cachedShort = true;
    }

    private DamageDisplay() {}

    private static Popup obtainPopup() {
        Popup p = popupPool.size > 0 ? popupPool.pop() : new Popup();
        p.cachedTxt = null;
        p.cachedVal = -1f;
        return p;
    }

    private static void freePopup(Popup p) {
        if (p == null) return;
        p.cachedTxt = null;
        if (popupPool.size < 96) popupPool.add(p);
    }

    private static int maxPopups() {
        if (PerfCore.level() >= 2) return 18;
        if (PerfCore.level() == 1) return 32;
        return Vars.mobile ? 44 : 80;
    }

    private static boolean canAddPopup() {
        return popups.size < maxPopups();
    }

    /** Gọi ngay khi tung đòn bạo kích lên mục tiêu (đơn vị hoặc công trình). */
    public static void crit(Posc e) {
        if (e != null) critMap.put(e.id(), Time.time);
    }

    private static boolean takeCrit(int id) {
        float t = critMap.get(id, MISSING);
        if (t <= MISSING * 0.5f) return false;
        critMap.remove(id, 0f);
        return Time.time - t < 6f;
    }

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> clearAll());
        Events.on(StateChangeEvent.class, e -> {
            if (e.to == GameState.State.playing && e.from != GameState.State.paused) clearAll();
        });
        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit == null) return;
            int id = e.unit.id;
            Dmg d = dmgCache.get(id);
            hpCache.remove(id, 0f);
            lastDmgAt.remove(id, 0f);
            critMap.remove(id, 0f);
            if (d != null) {
                if (d.popup != null) {
                    popups.remove(d.popup, true);
                    freePopup(d.popup);
                }
                dmgCache.remove(id);
            }
        });

        Events.run(Trigger.update, DamageDisplay::update);
        Events.run(Trigger.draw, DamageDisplay::draw);

        Events.on(BuildDamageEvent.class, e -> onBuildDamage(e));
    }

    private static void clearAll() {
        for (int i = 0; i < popups.size; i++) freePopup(popups.get(i));
        popups.clear();
        hpCache.clear();
        lastDmgAt.clear();
        dmgCache.clear();
        critMap.clear();
    }

    private static String mode() {
        if (Vars.headless || Core.camera == null || Core.camera.width > 1024f) return "none";
        if (Core.settings.getBool("show-hp-popup-eatsuki-tt", true)) return "eatsuki";
        return Core.settings.getBool("show-damage-popup", false) ? "popup" : "none";
    }

    private static String fmt(float n, boolean shortFmt) {
        float a = Math.abs(n);
        if (shortFmt) {
            if (a >= 1e6f) return Strings.fixed(a / 1e6f, 1) + "M";
            if (a >= 1e3f) return Strings.fixed(a / 1e3f, 1) + "k";
        }
        return a < 1f ? Strings.fixed(a, 1) : String.valueOf(Math.round(a));
    }

    /** Độ cao (từ tâm) phía trên thanh máu để số dmg không đè lên chữ HP. */
    private static float topOff(boolean isUnit, float size) {
        if (isUnit) return ShowHp.hpTop(size);
        return size / 2f + 4f;
    }

    private static void addEat(Posc ent, float dmg, float size, boolean isUnit, boolean crit) {
        if (dmg < 0.1f) return;
        int entId = ent.id();
        Dmg d = dmgCache.get(entId);
        if (d == null) {
            if (!canAddPopup()) return;
            d = new Dmg();
            dmgCache.put(entId, d);
        }
        d.total += dmg;
        d.idle = 0f;
        Popup p = d.popup;
        if (p == null || !popups.contains(p, true)) {
            if (!canAddPopup()) return;
            p = obtainPopup();
            p.type = TYPE_EAT;
            p.id = entId;
            p.life = 45f;
            p.max = 45f;
            p.critAge = -1f;
            d.popup = p;
            popups.add(p);
        }
        p.x = ent.x();
        p.y = ent.y();
        p.size = size;
        p.isUnit = isUnit;
        p.total = d.total;
        p.life = 45f;
        if (crit) p.critAge = 0f;
    }

    /** Đòn bạo kích: số dmg nảy lên, rơi xuống rồi biến mất, kèm chữ CRIT tím nhạt. */
    private static void addCrit(Posc ent, float amount, float size, boolean isUnit) {
        if (!canAddPopup()) return;
        Popup p = obtainPopup();
        p.type = TYPE_CRIT;
        p.id = 0;
        p.x = ent.x();
        p.y = ent.y();
        p.size = size;
        p.isUnit = isUnit;
        p.amount = amount;
        p.dx = Mathf.random(-14f, 14f);
        p.life = 45f;
        p.max = 45f;
        p.critAge = 0f;
        popups.add(p);
    }

    private static void addFloat(Posc ent, float amount, float size, boolean isUnit, boolean crit) {
        if (Math.abs(amount) < 0.5f || !canAddPopup()) return;
        boolean heal = amount > 0;
        int n = Mathf.rand.nextInt(99999);
        Popup p = obtainPopup();
        p.type = TYPE_FLOAT;
        p.id = n;
        p.x = ent.x();
        p.y = ent.y();
        p.size = size;
        p.isUnit = isUnit;
        p.amount = Math.abs(amount);
        p.heal = heal;
        p.lane = (n % 3) - 1;
        p.life = 40f;
        p.max = 40f;
        p.critAge = crit ? 0f : -1f;
        popups.add(p);
    }

    private static String scanMode = "none";
    private static boolean scanShowHeal = false;
    private static final Cons<Unit> scanUnitCons = u -> scanUnit(u, scanMode, scanShowHeal);

    private static void update() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
        final String m = mode();
        if (m.equals("none")) {
            if (popups.size > 0) clearAll();
            return;
        }
        if (Vars.state.isPaused()) return;
        if (!Groups.unit.isEmpty()) {
            Rect b = Core.camera.bounds(rect);
            scanMode = m;
            scanShowHeal = m.equals("popup") && Core.settings.getBool("show-heal-popup", true);
            Groups.unit.intersect(b.x, b.y, b.width, b.height, scanUnitCons);
        }

        for (int i = popups.size - 1; i >= 0; i--) {
            Popup p = popups.get(i);
            if (p.critAge >= 0) p.critAge += Time.delta;
            p.life -= Time.delta;
            if (p.type == TYPE_EAT) {
                Dmg d = dmgCache.get(p.id);
                if (d == null) {
                    popups.remove(i);
                    freePopup(p);
                    continue;
                }
                d.idle += Time.delta;
                if (d.idle >= 90 || p.life <= 0) {
                    dmgCache.remove(p.id);
                    popups.remove(i);
                    freePopup(p);
                }
            } else if (p.life <= 0) {
                popups.remove(i);
                freePopup(p);
            }
        }
    }

    private static void scanUnit(Unit u, String m, boolean showHeal) {
        if (!u.isValid() || u.health == Float.POSITIVE_INFINITY) return;
        float last = hpCache.get(u.id, MISSING);
        if (last > MISSING * 0.5f) {
            float diff = last - u.health;
            if (diff >= 0.8f) {
                lastDmgAt.put(u.id, Time.time);
                boolean crit = takeCrit(u.id);
                if (crit) addCrit(u, diff, u.hitSize, true);
                if (m.equals("eatsuki")) addEat(u, diff, u.hitSize, true, false);
                else if (m.equals("popup") && !crit) addFloat(u, -diff, u.hitSize, true, false);
            } else if (diff <= -0.8f && showHeal) {
                // Chặn lỗi nhảy số hồi máu ảo trên client do đồng bộ snapshot ngay sau khi gây sát thương
                boolean recentDmg = Vars.net.client() && (Time.time - lastDmgAt.get(u.id, -999f) < 28f);
                if (!recentDmg) addFloat(u, -diff, u.hitSize, true, false);
            }
        }
        hpCache.put(u.id, u.health);
    }

    /** Vẽ "số" + (nếu crit) chữ CRIT nảy ngay sau số, căn giữa cả cụm. */
    private static void drawLine(Font font, String text, float x, float y, boolean crit, float age, float a) {
        layout.setText(font, text);
        float w1 = layout.width;
        float w2 = 0f, gap = 3f;
        if (crit) {
            layout.setText(font, CRIT);
            w2 = layout.width;
        } else {
            gap = 0f;
        }
        float x0 = x - (w1 + gap + w2) / 2f;
        tmp.set(Color.white);
        tmp.a = a;
        font.setColor(tmp);
        font.draw(text, x0, y, 0f, Align.left, false);
        if (crit) {
            float k = Math.max(0f, 1f - age / 20f);
            float bounce = Math.abs(Mathf.sin(age * 0.4f)) * 7f * k;
            font.draw(CRIT, x0 + w1 + gap, y + bounce, 0f, Align.left, false);
        }
    }

    private static void draw() {
        if (popups.size == 0 || Vars.headless) return;
        String m = mode();
        if (m.equals("none")) return;
        boolean shortFmt = Core.settings.getBool("show-damage-short-format", true);
        Draw.z(116f);
        Font font = Fonts.outline;
        float ox = font.getData().scaleX, oy = font.getData().scaleY;
        for (int i = 0; i < popups.size; i++) {
            Popup p = popups.get(i);
            float f = p.life / p.max;
            boolean crit = p.critAge >= 0;
            if (p.type == TYPE_CRIT) {
                float t = 1f - f;
                float base = p.y + topOff(p.isUnit, p.size) + 8f;
                font.getData().setScale(0.34f + 0.14f * Math.max(0f, 1f - t * 6f));
                if (p.cachedTxt == null || p.cachedShort != shortFmt) {
                    p.cachedTxt = "[#d9b3ff]" + fmt(p.amount, shortFmt) + "[]";
                    p.cachedShort = shortFmt;
                }
                drawLine(font, p.cachedTxt, p.x + p.dx * t, base + 4f * 26f * t * (1f - t),
                    true, p.critAge, t < 0.7f ? 1f : (1f - t) / 0.3f);
            } else if (p.type == TYPE_EAT && m.equals("eatsuki")) {
                font.getData().setScale(0.32f);
                if (p.cachedTxt == null || p.cachedVal != p.total || p.cachedShort != shortFmt) {
                    p.cachedTxt = "[scarlet]" + fmt(p.total, shortFmt) + "[]";
                    p.cachedVal = p.total;
                    p.cachedShort = shortFmt;
                }
                drawLine(font, p.cachedTxt, p.x, p.y + topOff(p.isUnit, p.size) + 8f,
                    crit, p.critAge, f * f);
            } else if (p.type == TYPE_FLOAT && m.equals("popup")) {
                float t = 1f - f;
                boolean big = p.amount >= 1000f;
                font.getData().setScale(Math.max(0.15f, (big ? 0.42f : 0.28f) * (1f - t * t)));
                if (p.cachedTxt == null || p.cachedShort != shortFmt) {
                    p.cachedTxt = p.heal
                        ? "[lime]+" + fmt(p.amount, shortFmt) + "[]"
                        : (big ? "[scarlet]" : "[orange]") + fmt(p.amount, shortFmt) + "[]";
                    p.cachedShort = shortFmt;
                }
                drawLine(font, p.cachedTxt, p.x + p.lane * 14f, p.y + topOff(p.isUnit, p.size) + 6f + 16f * (1f - f * f),
                    crit, p.critAge, f * f * f);
            }
        }
        font.getData().setScale(ox, oy);
        Draw.reset();
    }

    private static void onBuildDamage(BuildDamageEvent e) {
        if (Vars.headless) return;
        String m = mode();
        Building b = e.build;
        if (m.equals("none") || b == null || !b.isValid() || b.health == Float.POSITIVE_INFINITY) return;
        float last = hpCache.get(b.id, MISSING);
        float diff = 0f;
        if (last > MISSING * 0.5f) diff = last - b.health;
        else if (e.source != null) diff = Math.min(e.source.damage, b.health);
        hpCache.put(b.id, b.health);
        if (Math.abs(diff) < 0.8f) return;
        float size = b.block.size * Vars.tilesize;
        boolean crit = diff > 0 && takeCrit(b.id);
        if (crit) addCrit(b, diff, size, false);
        if (m.equals("eatsuki")) addEat(b, diff, size, false, false);
        else if (!crit) addFloat(b, -diff, size, false, false);
    }
}
