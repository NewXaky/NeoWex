package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import newex.NexNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Honivery la unit THEM MOI (bac dac biet 8) trong tab Unit cua NewMode PvP: xem
 * NewModePvp.tierType(8) / TIER_MAX. Khong thay the unit nao (Collaris van la bac 5).
 * Skill (nut HUD keo tha duoc): tung 1 combo {@link #COUNT} cu chem cung trang lien tiep, xen ke 2 huong,
 * cu cuoi la vong chem 360 do to hon va dau hon (theo video mau). Moi cu chem tu nham muc tieu gan nhat
 * (neu co), gay sat thuong hinh quat len unit va cong trinh dich. Hoi chieu 1 giay tinh tu luc combo ket thuc.
 * Server tinh sat thuong + phat tung cu chem qua broadcast; moi may tu ve hieu ung.
 */
public final class HoniveryPvp {
    private HoniveryPvp() {}

    static final String REQ = "newex-honivery-skill", FX = "newex-honivery-fx", KEY = "newex-honivery-btn";
    /** 1 giay hoi chieu (60 tick/s), tinh sau khi combo ket thuc. */
    static final float CD_T = 60f;
    /** So cu chem (cu cuoi la cu to), khoang cach giua 2 cu (tick). */
    static final int COUNT = 8;
    static final float GAP = 15f;
    /** Thoi gian combo, va tong thoi gian truoc khi dung skill lai duoc. */
    static final float COMBO_T = (COUNT - 1) * GAP, CD_TOTAL = COMBO_T + CD_T;
    /** Ban kinh / nua goc quat cua cu chem thuong; tam chem dich ra truoc unit toi da LEAD; tam nham muc tieu trong SEEK. */
    static final float RAD = 48f, HALF = 80f, LEAD = 44f, SEEK = 88f, FIN_RAD_MUL = 1.45f;
    /** Sat thuong moi cu (nhan damageMultiplier cua unit); cu cuoi nhan FIN_DMG_MUL. */
    static final float DMG = 120f, FIN_DMG_MUL = 2.5f;
    static final float LIFE = 16f, FIN_LIFE = 26f;
    static final Color COL = Color.valueOf("ffd27a"), COL_A = Color.valueOf("7fd8ff"), COL_B = Color.valueOf("ff9ad0");

    static boolean netOk = false;
    static float cdEnd = 0;
    /** Server: combo dang chay theo player.id. Client: cac vet chem dang ve. */
    static final HashMap<Integer, Combo> combos = new HashMap<>();
    static final ArrayList<Slash> slashes = new ArrayList<>();

    static final class Combo {
        final Player pl; float t = 0f; int idx = 0;
        Combo(Player pl) { this.pl = pl; }
    }
    static final class Slash {
        float x, y, ang, t = 0f, life; int dir; boolean big;
    }
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    static Table btn = null;

    static boolean isActive() {
        try { return NewModePvp.apiIsActive(); } catch (Throwable err) { return false; }
    }

    static void update() {
        tryNet();
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!combos.isEmpty()) combos.clear();
            if (!slashes.isEmpty()) slashes.clear();
            return;
        }
        if (Vars.state.isPaused()) return;
        if (!Vars.net.client()) tickCombos();
        for (int i = slashes.size() - 1; i >= 0; i--) {
            Slash sl = slashes.get(i);
            sl.t += Time.delta;
            if (sl.t >= sl.life) slashes.remove(i);
        }
    }

    static boolean isHon(Unit u) { return u != null && Honivery.honivery != null && u.type == Honivery.honivery; }

    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ, HoniveryPvp::onReq, false);
        NexNet.onBroadcast(FX, HoniveryPvp::onFx);
    }

    // ---- server: nhan yeu cau dung skill ----
    static void onReq(Player player, String payload) {
        if (!isActive()) return;
        Unit u = player.unit();
        if (!isHon(u) || u.dead) return;
        if (combos.containsKey(player.id)) return;
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + CD_TOTAL);
        combos.put(player.id, new Combo(player));
    }

    /** Server: moi tick chay cac combo, den gio thi tung cu chem tiep theo. */
    static void tickCombos() {
        if (combos.isEmpty()) return;
        for (Iterator<Combo> it = combos.values().iterator(); it.hasNext();) {
            Combo c = it.next();
            Unit u = c.pl.unit();
            if (!isActive() || !isHon(u) || u.dead) { it.remove(); continue; }
            while (c.idx < COUNT && c.t >= c.idx * GAP) { doSlash(u, c.idx); c.idx++; }
            c.t += Time.delta;
            if (c.idx >= COUNT) it.remove();
        }
    }

    static boolean inSector(float cx, float cy, float face, float half, float rad, float x, float y) {
        float d = Mathf.dst(cx, cy, x, y);
        if (d > rad) return false;
        return half >= 180f || d < 14f || Math.abs(Angles.angleDist(Angles.angle(cx, cy, x, y), face)) <= half;
    }

    /** Server: 1 cu chem. Tam chem nam giua unit va muc tieu gan nhat (toi da LEAD tu unit); cu cuoi la vong 360 do. */
    static void doSlash(Unit u, int idx) {
        boolean fin = idx == COUNT - 1;
        int dir = (idx % 2 == 0) ? 1 : -1;
        mindustry.gen.Teamc tg = null;
        try { tg = Units.closestTarget(u.team, u.x, u.y, SEEK, e -> !e.dead, b -> true); } catch (Throwable err) {}
        float ang = tg != null ? Angles.angle(u.x, u.y, tg.x(), tg.y()) : u.rotation;
        float reach = tg != null ? Math.min(Mathf.dst(u.x, u.y, tg.x(), tg.y()), LEAD) : LEAD * 0.5f;
        final float cx = u.x + Angles.trnsx(ang, reach), cy = u.y + Angles.trnsy(ang, reach);
        final float face = fin ? ang : ang + dir * 28f;
        final float half = fin ? 180f : HALF;
        final float rad = fin ? RAD * FIN_RAD_MUL : RAD;
        final float dmg = DMG * (fin ? FIN_DMG_MUL : 1f) * Math.max(0.1f, u.damageMultiplier);
        final Team team = u.team;
        final float reachAll = rad + 16f;
        Units.nearbyEnemies(team, cx - reachAll, cy - reachAll, reachAll * 2, reachAll * 2, e -> {
            if (e.dead || !inSector(cx, cy, face, half, rad + e.hitSize / 2f, e.x, e.y)) return;
            e.damage(dmg);
            try { Call.effect(Fx.hitLaserBlast, e.x, e.y, face, dir > 0 ? COL_A : COL_B); Call.effect(Fx.sparkShoot, e.x, e.y, face, Color.white); } catch (Throwable err) {}
        });
        Units.nearbyBuildings(cx, cy, reachAll, b -> {
            if (b.team == team || !inSector(cx, cy, face, half, rad + b.hitSize() / 2f, b.x, b.y)) return;
            b.damage(dmg);
            try { Call.effect(Fx.hitLaserBlast, b.x, b.y, face, dir > 0 ? COL_A : COL_B); } catch (Throwable err) {}
        });
        NexNet.broadcast(FX, cx + "," + cy + "," + face + "," + dir + "," + (fin ? 1 : 0));
    }

    // ---- moi may: nhan 1 cu chem -> tao vet chem de ve ----
    static void onFx(String payload) {
        if (Vars.headless) return;
        try {
            String[] p = String.valueOf(payload).split(",");
            Slash sl = new Slash();
            sl.x = Float.parseFloat(p[0]);
            sl.y = Float.parseFloat(p[1]);
            sl.ang = Float.parseFloat(p[2]);
            sl.dir = Integer.parseInt(p[3]);
            sl.big = Integer.parseInt(p[4]) == 1;
            sl.life = sl.big ? FIN_LIFE : LIFE;
            slashes.add(sl);
            Effect.shake(sl.big ? 5f : 2f, sl.big ? 12f : 6f, sl.x, sl.y);
            Sounds.shootReign.at(sl.x, sl.y, sl.big ? 0.7f : 1.3f + sl.dir * 0.08f, 0.5f);
            Fx.sparkShoot.at(sl.x, sl.y, sl.ang, sl.dir > 0 ? COL_A : COL_B);
        } catch (Throwable err) { Log.err("[HoniveryPvp] fx: " + err); }
    }

    // ---- ve vet chem cung trang: dau sang quet qua, duoi mo dan ----
    static void drawArc(Slash s, Color col, float rMul, float wMul) {
        float p = s.t / s.life;
        float head = Interp.pow2Out.apply(Mathf.clamp(p / 0.4f));
        float tail = head * Mathf.clamp((p - 0.1f) / 0.9f);
        float fade = 1f - Mathf.clamp((p - 0.45f) / 0.55f);
        if (head - tail < 0.01f || fade <= 0f) return;
        float half = s.big ? 180f : 95f;
        float R = (s.big ? RAD * FIN_RAD_MUL : RAD) * rMul;
        float W = R * (s.big ? 0.30f : 0.40f) * wMul;
        int N = s.big ? 36 : 20;
        for (int k = 0; k < N; k++) {
            float f1 = Mathf.lerp(tail, head, k / (float) N), f2 = Mathf.lerp(tail, head, (k + 1) / (float) N);
            float tp = (k + 0.5f) / N;
            float a1 = s.ang + s.dir * Mathf.lerp(-half, half, f1), a2 = s.ang + s.dir * Mathf.lerp(-half, half, f2);
            float w1 = W * (float) Math.pow(Mathf.sinDeg(f1 * 180f), 0.7) * (0.25f + 0.75f * (k / (float) N));
            float w2 = W * (float) Math.pow(Mathf.sinDeg(f2 * 180f), 0.7) * (0.25f + 0.75f * ((k + 1) / (float) N));
            Draw.color(col, Color.white, tp);
            Draw.alpha(fade * 0.9f);
            Fill.quad(
                s.x + Angles.trnsx(a1, R), s.y + Angles.trnsy(a1, R),
                s.x + Angles.trnsx(a2, R), s.y + Angles.trnsy(a2, R),
                s.x + Angles.trnsx(a2, R - w2), s.y + Angles.trnsy(a2, R - w2),
                s.x + Angles.trnsx(a1, R - w1), s.y + Angles.trnsy(a1, R - w1));
        }
    }

    static void draw() {
        if (slashes.isEmpty() || Vars.state == null || !Vars.state.isGame()) return;
        float z = Draw.z();
        Draw.z(Layer.effect + 1f);
        Draw.blend(Blending.additive);
        for (Slash s : slashes) {
            if (s.big) {
                drawArc(s, COL_B, 1f, 1f);
                drawArc(s, COL_A, 0.86f, 0.8f);
            } else {
                drawArc(s, s.dir > 0 ? COL_A : COL_B, 1f, 1f);
            }
        }
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }

    // ---------------- UI ----------------
    static void clamp(Table t) {
        t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
    }

    static void build() {
        if (btn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        UnitType h = Honivery.honivery;
        Table t = new Table(Styles.black6);
        btn = t;
        t.name = "newex-honivery-skill";
        t.margin(6);
        t.touchable = Touchable.enabled;
        float sz = Vars.mobile ? 56 : 48;
        if (h != null) t.image(h.uiIcon).size(sz).row();
        Label lbl = t.add("").get();
        lbl.setFontScale(0.9f);
        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0) { lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s"); lbl.setColor(Color.gray); }
            else { lbl.setText("SKILL"); lbl.setColor(COL); }
        });
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown && Vars.player != null && isHon(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f + 90);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                dragging = false; offX = x; offY = y; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                if (dragging) {
                    Core.settings.put(KEY + "-x", Math.round(t.x));
                    Core.settings.put(KEY + "-y", Math.round(t.y));
                } else if (!Vars.state.isPaused() && Time.time >= cdEnd) {
                    Unit u = Vars.player.unit();
                    if (isHon(u) && !u.dead) { cdEnd = Time.time + CD_TOTAL; NexNet.send(REQ, ""); }
                }
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    public static void init() {
        Events.run(Trigger.update, HoniveryPvp::update);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try { build(); } catch (Throwable err) { Log.err("[HoniveryPvp] ui: " + err); }
            });
            Events.run(Trigger.draw, HoniveryPvp::draw);
            Events.on(WorldLoadEvent.class, e -> { cdEnd = 0; cdEndSrv.clear(); combos.clear(); slashes.clear(); });
        } else {
            Events.on(WorldLoadEvent.class, e -> { cdEndSrv.clear(); combos.clear(); });
        }
    }
}
