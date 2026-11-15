package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
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
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import newex.NexNet;

import java.util.HashMap;

/**
 * Honivery la unit THEM MOI (bac dac biet 8) trong tab Unit cua NewMode PvP: xem
 * NewModePvp.tierType(8) / TIER_MAX. Khong thay the unit nao (Collaris van la bac 5).
 * Da co nut skill tren HUD (keo tha duoc), hoi chieu 1 giay. Noi dung ky nang se dien
 * sau vao {@link #onReq} / {@link #onFx}.
 */
public final class HoniveryPvp {
    private HoniveryPvp() {}

    static final String REQ = "newex-honivery-skill", FX = "newex-honivery-fx", KEY = "newex-honivery-btn";
    /** 1 giay hoi chieu (60 tick/s). */
    static final float CD_T = 60f;
    static final Color COL = Color.valueOf("ffd27a");

    static boolean netOk = false;
    static float cdEnd = 0;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    static Table btn = null;

    static boolean isActive() {
        try { return NewModePvp.apiIsActive(); } catch (Throwable err) { return false; }
    }

    static void syncType() {
        tryNet();
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
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + CD_T);
        // TODO: noi dung ky nang Honivery se mo ta sau - hien chi phat broadcast de bao da kich hoat.
        NexNet.broadcast(FX, u.id + "," + u.x + "," + u.y);
    }

    // ---- moi may: nhan hieu ung (cho trong, se thay bang hieu ung ky nang that) ----
    static void onFx(String payload) {
        // TODO: khi co mo ta ky nang, dung payload (uid,x,y) de ve hieu ung / gay sat thuong o day.
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
                    if (isHon(u) && !u.dead) { cdEnd = Time.time + CD_T; NexNet.send(REQ, ""); }
                }
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    public static void init() {
        Events.run(Trigger.update, HoniveryPvp::syncType);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try { build(); } catch (Throwable err) { Log.err("[HoniveryPvp] ui: " + err); }
            });
            Events.on(WorldLoadEvent.class, e -> { cdEnd = 0; cdEndSrv.clear(); });
        } else {
            Events.on(WorldLoadEvent.class, e -> cdEndSrv.clear());
        }
    }
}
