package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import newex.NexNet;
import newex.content.Rexavyr.RexavyrUnit;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Kỹ năng chủ động của Rexavyr (hoạt động ở mọi chế độ, không cần NewMode PvP):
 *  [1] LAO TỚI + CẮN (hồi 8s)  [2] GẦM (hồi 20s).
 * Máy chủ/host xử lý sát thương & di chuyển; mọi máy nhận FX để vẽ animation + hiệu ứng.
 */
public final class RexavyrSkill {
    private RexavyrSkill() {}

    static final String REQ_DASH = "newex-rex-dash", REQ_ROAR = "newex-rex-roar", FX = "newex-rex-fx";
    public static final String[] KEY_SETTING = {"newex-rex-key-dash", "newex-rex-key-roar"};
    static final KeyCode[] KEY_DEF = {KeyCode.z, KeyCode.x};
    static final String[] NAME = {"LAO + CẮN", "GẦM"};
    static final Color COL = Color.valueOf("ff8a1f");

    static boolean netOk = false;
    static final float[] cdEnd = new float[2];
    static final Table[] btns = new Table[2];

    static final class Dash { int uid; float ang, t, prevF; boolean bit; }
    static final HashMap<Integer, Dash> dashes = new HashMap<>();

    // ---------- phím ----------
    public static KeyCode getKey(int i) {
        try { return KeyCode.valueOf(Core.settings.getString(KEY_SETTING[i], KEY_DEF[i].name())); }
        catch (Throwable ignored) { return KEY_DEF[i]; }
    }

    public static void buildKeybindRow(Table table) {
        if (table == null) return;
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            final boolean[] listening = {false};
            Table row = new Table(Styles.black6);
            row.margin(8f);
            row.add("[#FF8A1F]Rexavyr[] - " + NAME[i] + " (PC):").left().growX().padRight(10f);
            final TextButton kb = new TextButton(getKey(idx).value, Styles.defaultt);
            kb.update(() -> {
                if (listening[0]) {
                    kb.setText("[accent]> Nhấn phím bất kỳ... <[]");
                    for (KeyCode k : KeyCode.all) {
                        if (k == KeyCode.unknown || k == KeyCode.mouseLeft || k == KeyCode.mouseRight || k == KeyCode.mouseMiddle) continue;
                        if (Core.input.keyTap(k)) {
                            if (k != KeyCode.escape) Core.settings.put(KEY_SETTING[idx], k.name());
                            listening[0] = false;
                            kb.setText(getKey(idx).value);
                            break;
                        }
                    }
                } else kb.setText(getKey(idx).value);
            });
            kb.clicked(() -> listening[0] = !listening[0]);
            row.add(kb).width(170f).height(38f).right();
            table.add(row).growX().padBottom(6f).row();
        }
    }

    static boolean isRex(Unit u) { return u instanceof RexavyrUnit; }
    public static boolean isDashing(Unit u) { return u != null && dashes.containsKey(u.id); }

    // ---------- mạng ----------
    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ_DASH, RexavyrSkill::onDashReq, false);
        NexNet.on(REQ_ROAR, (p, s) -> { if (p != null && isRex(p.unit())) startRoar(p.unit()); }, false);
        NexNet.onBroadcast(FX, RexavyrSkill::onFx);
    }

    /** Client: gửi yêu cầu kỹ năng i (0 = lao+cắn, 1 = gầm). */
    static void cast(int i) {
        if (Vars.state == null || Vars.state.isPaused() || Vars.player == null || Time.time < cdEnd[i]) return;
        Unit u = Vars.player.unit();
        if (!isRex(u) || u.dead) return;
        if (i == 0) {
            if (dashes.containsKey(u.id)) return;
            float ang;
            if (Vars.mobile) ang = u.vel.len() > 0.15f ? u.vel.angle() : u.rotation;
            else { Vec2 m = Core.input.mouseWorld(); ang = Angles.angle(u.x, u.y, m.x, m.y); }
            cdEnd[0] = Time.time + Rexavyr.DASH_CD;
            if (Vars.net.client()) { Dash d = new Dash(); d.uid = u.id; d.ang = ang; d.bit = true; dashes.put(u.id, d); } // dự đoán cục bộ
            NexNet.send(REQ_DASH, String.valueOf(ang));
        } else {
            cdEnd[1] = Time.time + Rexavyr.ROAR_CD;
            NexNet.send(REQ_ROAR, "");
        }
    }

    static void onDashReq(Player p, String payload) {
        if (p == null || !isRex(p.unit())) return;
        try { startDash(p.unit(), Float.parseFloat(payload)); } catch (Throwable ignored) {}
    }

    // ---------- logic phía máy chủ ----------
    public static void startDash(Unit u, float ang) {
        if (!(u instanceof RexavyrUnit) || u.dead || dashes.containsKey(u.id)) return;
        RexavyrUnit r = (RexavyrUnit) u;
        if (r.cdDash > 0f) return;
        r.cdDash = Rexavyr.DASH_CD;
        r.dashT = Time.time;
        try { GearItems.onUnitSkillCast(u); } catch (Throwable ignored) {}
        Dash d = new Dash(); d.uid = u.id; d.ang = ang;
        dashes.put(u.id, d);
        NexNet.broadcast(FX, u.id + ",0," + ang + ",0,0");
    }

    public static void startRoar(Unit u) {
        if (!(u instanceof RexavyrUnit) || u.dead) return;
        RexavyrUnit r = (RexavyrUnit) u;
        if (r.cdRoar > 0f) return;
        r.cdRoar = Rexavyr.ROAR_CD;
        r.roarT = Time.time;
        try { GearItems.onUnitSkillCast(u); } catch (Throwable ignored) {}
        NexNet.broadcast(FX, u.id + ",1,0,0,0");
        final float rad = Rexavyr.ROAR_RADIUS, dmg = Rexavyr.calcAtk(u) * Rexavyr.ROAR_RATIO;
        final Unit self = u;
        Damage.damage(u.team, u.x, u.y, rad, dmg, false, true, true);
        Groups.unit.intersect(u.x - rad, u.y - rad, rad * 2f, rad * 2f, e -> {
            if (e.team == self.team || e.dead || !e.within(self.x, self.y, rad + e.hitSize / 2f)) return;
            e.apply(StatusEffects.disarmed, 90f);   // choáng: không bắn được 1.5s
            e.apply(StatusEffects.sapped, 360f);    // yếu giáp & chậm 6s
            e.apply(StatusEffects.slow, 240f);
            e.vel.add(Tmp.v1.set(e.x - self.x, e.y - self.y).nor().scl(Mathf.clamp(40f / e.hitSize, 0.2f, 1.5f) * 3.5f));
        });
        final float br = rad * 0.7f;
        Groups.bullet.intersect(u.x - br, u.y - br, br * 2f, br * 2f, b -> {
            if (b.team != self.team && b.type.hittable && b.within(self.x, self.y, br)) b.remove();
        });
        u.apply(StatusEffects.overdrive, 240f);
    }

    static void bite(Unit u) {
        final float dmg = Rexavyr.calcAtk(u) * Rexavyr.BITE_RATIO;
        float bx = u.x + Angles.trnsx(u.rotation, Rexavyr.HEAD_TIP + 14f);
        float by = u.y + Angles.trnsy(u.rotation, Rexavyr.HEAD_TIP + 14f);
        if (u instanceof RexavyrUnit && ((RexavyrUnit) u).mawValid) {
            RexavyrUnit ru = (RexavyrUnit) u;
            bx = ru.mawX + Angles.trnsx(ru.mawAng, 14f);
            by = ru.mawY + Angles.trnsy(ru.mawAng, 14f);
        }
        final float px = bx, py = by;
        final float rad = 82f;
        final int[] hits = {0};
        Groups.unit.intersect(px - rad, py - rad, rad * 2f, rad * 2f, e -> {
            if (e.team != u.team && !e.dead && e.within(px, py, rad + e.hitSize / 2f)) { hits[0]++; e.apply(StatusEffects.slow, 90f); }
        });
        Damage.damage(u.team, px, py, rad, dmg, false, true, true);
        if (hits[0] > 0) u.heal(Math.min(u.maxHealth * 0.08f, hits[0] * dmg * 0.1f));
        NexNet.broadcast(FX, u.id + ",3," + px + "," + py + "," + u.rotation);
    }

    static void tickDash() {
        Iterator<Dash> it = dashes.values().iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            Unit u = Groups.unit.getByID(d.uid);
            if (u == null || u.dead || !u.isValid()) { it.remove(); continue; }
            d.t += Time.delta;
            float p = Mathf.clamp(d.t / Rexavyr.DASH_DUR);
            float f = Interp.smoother.apply(p);
            float step = (f - d.prevF) * Rexavyr.DASH_DIST;
            d.prevF = f;
            u.move(Mathf.cosDeg(d.ang) * step, Mathf.sinDeg(d.ang) * step);
            u.vel.setZero();
            u.rotation = Angles.moveToward(u.rotation, d.ang, 30f * Time.delta);
            if (!d.bit && p >= 0.85f) { d.bit = true; if (!Vars.net.client()) bite(u); }
            if (p >= 1f) it.remove();
        }
    }

    // ---------- FX (mọi máy) ----------
    static void onFx(String payload) {
        try {
            String[] p = payload.split(",");
            Unit u = Groups.unit.getByID(Integer.parseInt(p[0]));
            int kind = Integer.parseInt(p[1]);
            float a = Float.parseFloat(p[2]), b = Float.parseFloat(p[3]), c = Float.parseFloat(p[4]);
            RexavyrUnit r = u instanceof RexavyrUnit ? (RexavyrUnit) u : null;
            if (kind == 0 && r != null) r.dashT = Time.time;
            else if (kind == 1 && r != null) r.roarT = Time.time;
            else if (kind == 2 && r != null) { r.tailT = Time.time; r.tailSide = a; }
            if (Vars.headless) return;
            if (kind == 0 && u != null) {
                Rexavyr.dashStartFx.at(u.x, u.y, a);
                Effect.shake(3.4f, 16f, u.x, u.y);
                Sounds.shootReign.at(u.x, u.y, 1.5f, 0.6f);
            } else if (kind == 1 && u != null) {
                Rexavyr.roarFx.at(u.x, u.y);
                Effect.shake(7f, 50f, u.x, u.y);
                Sounds.shootReign.at(u.x, u.y, 2f, 0.4f);
                Sounds.explosionReactor.at(u.x, u.y, 1.2f, 0.5f);
            } else if (kind == 3) {
                Rexavyr.biteFx.at(a, b, c);
                Effect.shake(6f, 20f, a, b);
                Sounds.explosion.at(a, b, 1.6f, 0.55f);
            } else if (kind == 4) {
                Rexavyr.tailFx.at(a, b, c);
                Effect.shake(3.5f, 14f, a, b);
                Sounds.explosion.at(a, b, 1.0f, 0.9f);
            }
        } catch (Throwable err) {
            Log.err("[RexavyrSkill] fx: " + err);
        }
    }

    // ---------- vòng lặp ----------
    static void update() {
        tryNet();
        if (Vars.state == null || !Vars.state.isGame()) { if (!dashes.isEmpty()) dashes.clear(); return; }
        if (Vars.state.isPaused()) return;
        if (!Vars.headless && !Vars.mobile && Vars.player != null && Core.scene != null && !Core.scene.hasField()) {
            for (int i = 0; i < 2; i++) if (Core.input.keyTap(getKey(i))) cast(i);
        }
        tickDash();
    }

    // ---------- nút HUD (kéo thả được, nhớ vị trí) ----------
    static void clamp(Table t) {
        t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
    }

    static void build(final int i) {
        if (btns[i] != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        final String key = "newex-rex-btn" + i;
        final Table t = new Table(Styles.black6);
        btns[i] = t;
        t.name = key;
        t.margin(6);
        t.touchable = Touchable.enabled;
        float sz = Vars.mobile ? 56 : 48;
        if (Rexavyr.rexavyr != null) t.image(Rexavyr.rexavyr.uiIcon).size(sz).row();
        final Label lbl = t.add("").get();
        lbl.setFontScale(0.9f);
        t.update(() -> {
            float left = cdEnd[i] - Time.time;
            if (left > 0) { lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s"); lbl.setColor(Color.gray); }
            else { lbl.setText(Vars.mobile ? NAME[i] : NAME[i] + " [" + getKey(i).value + "]"); lbl.setColor(COL); }
        });
        t.visible(() -> Vars.ui.hudfrag.shown && Vars.player != null && isRex(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(key + "-x") && Core.settings.has(key + "-y")) t.setPosition(Core.settings.getInt(key + "-x"), Core.settings.getInt(key + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f + 170 + i * 110f);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) { dragging = false; offX = x; offY = y; return true; }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging) { Core.settings.put(key + "-x", Math.round(t.x)); Core.settings.put(key + "-y", Math.round(t.y)); }
                else cast(i);
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    public static void init() {
        Events.run(Trigger.update, RexavyrSkill::update);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try { build(0); build(1); } catch (Throwable err) { Log.err("[RexavyrSkill] ui: " + err); }
            });
        }
        Events.on(WorldLoadEvent.class, e -> { cdEnd[0] = 0f; cdEnd[1] = 0f; dashes.clear(); });
    }
}
