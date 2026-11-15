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
import mindustry.content.UnitTypes;
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
 * Dua unit Honivery (xem {@link Honivery}) vao "trang unit" (tab Unit) cua NewMode PvP,
 * theo dung cach {@link ArchuengPvp} dang lam voi disrupt: khi PvP dang chay, tam thoi
 * thay UnitType cua 1 bac (tier) bang Honivery, tra lai unit goc khi PvP ket thuc.
 * Khong can sua them file NewModePvp.java: isActive() goi thang NewModePvp.apiIsActive().
 *
 * Bac dang dung: TIER = 5 (UnitTypes.collaris, bac cao nhat trong 5 bac thuong 1-5).
 * Bac 6 (reign) va bac 7 (disrupt, da bi ArchuengPvp chiem) khong dung vi reign dang
 * duoc Cin.java dung rieng (kiem tra u.type == UnitTypes.reign). Muon doi bac khac, chi
 * can doi hang so TIER ben duoi.
 *
 * Da co nut skill tren HUD (keo tha duoc), hoi chieu 1 giay. Noi dung ky nang se dien
 * sau vao {@link #onReq} / {@link #onFx} (hien chi la cho trong, bam nut moi co hieu ung
 * nhap nhay bao da kich hoat).
 */
public final class HoniveryPvp {
    private HoniveryPvp() {}

    /** Bac (tier 1-5) ma Honivery se thay the khi PvP dang hoat dong. Doi so nay de chuyen bac khac. */
    static final int TIER = 5;

    static final String REQ = "newex-honivery-skill", FX = "newex-honivery-fx", KEY = "newex-honivery-btn";
    /** 1 giay hoi chieu (60 tick/s). */
    static final float CD_T = 60f;
    static final Color COL = Color.valueOf("ffd27a");

    static UnitType orig = null;
    static boolean swapped = false, netOk = false;
    static float cdEnd = 0;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    static Table btn = null;

    static boolean isActive() {
        try { return NewModePvp.apiIsActive(); } catch (Throwable err) { return false; }
    }

    /** UnitType vanilla dang nam o bac TIER (goc, truoc khi thay the). */
    static UnitType slotType() {
        switch (TIER) {
            case 1: return UnitTypes.merui;
            case 2: return UnitTypes.cleroi;
            case 3: return UnitTypes.anthicus;
            case 4: return UnitTypes.tecta;
            default: return UnitTypes.collaris;
        }
    }

    static void setSlotType(UnitType t) {
        switch (TIER) {
            case 1: UnitTypes.merui = t; break;
            case 2: UnitTypes.cleroi = t; break;
            case 3: UnitTypes.anthicus = t; break;
            case 4: UnitTypes.tecta = t; break;
            default: UnitTypes.collaris = t; break;
        }
    }

    /** Thay the vanilla <-> Honivery khi PvP bat/tat (giong ArchuengPvp.syncType()). */
    static void syncType() {
        tryNet();
        UnitType h = Honivery.honivery;
        if (h == null) return;
        try {
            if (orig == null) orig = slotType();
            boolean want = isActive();
            if (want != swapped) { setSlotType(want ? h : orig); swapped = want; }
        } catch (Throwable err) { Log.err("[HoniveryPvp] swap failed: " + err); }
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
