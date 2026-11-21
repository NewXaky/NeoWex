package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import newex.NexNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Kỹ năng LƯỚT (Dash) của Bulvasyr trong NewMode PvP.
 * Unit không dịch chuyển tức thời: gia tốc dần, lướt nhanh rồi giảm tốc (Interp.smoother) trong ~0,43s.
 * Máy chủ/chủ unit di chuyển thật; mọi máy vẽ vệt sáng, bóng ảnh và hiệu ứng điện ảnh.
 */
public final class BulvasyrDash {
    private BulvasyrDash() {}

    static final String REQ = "newex-bulvasyr-dash", FX = "newex-bulvasyr-dash-fx", KEY = "newex-bulvasyr-dash-btn";
    public static final String KEYBIND_SETTING = "newex-pvp-bulvasyr-dash-key";

    static final float CD = 420f;     // hồi chiêu 7s
    static final float DUR = 26f;     // thời gian lướt (tick)
    static final float DIST = 210f;   // quãng đường lướt (đơn vị thế giới, ~26 ô)

    static final Color COL = Color.valueOf("7a68ff"), COL2 = Color.valueOf("5fe3ff"), STEEL = Color.valueOf("c9d0e0");
    static final Color RED = Color.valueOf("e0303c"), YEL = Color.valueOf("ffd84a");

    static boolean netOk = false;
    static float cdEnd = 0f;
    static Table btn = null;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();

    // ---------- chuyển động thật ----------
    static final class Dash { int uid; float ang, t, prevF; }
    static final HashMap<Integer, Dash> dashes = new HashMap<>();

    // ---------- hình ảnh ----------
    static final class Vis {
        int uid; float ang, t; boolean ended;
        final float[] tx = new float[44], ty = new float[44];
        int n = 0; float ghostAcc = 0f;
    }
    static final class Ghost { float x, y, rot, t, life; TextureRegion region; }
    static final ArrayList<Vis> vis = new ArrayList<>();
    static final ArrayList<Ghost> ghosts = new ArrayList<>();

    /** Chớp khởi động: vòng xung kích, luồng khí văng ngược và ngôi sao 2 cánh. */
    static final Effect startFx = new Effect(36f, 220f, e -> {
        float a = e.fout(Interp.pow2);
        Draw.blend(Blending.additive);
        Draw.color(COL2, 0.75f * a);
        Lines.stroke(3.2f * a + 0.4f);
        Lines.circle(e.x, e.y, 4f + e.fin(Interp.pow3Out) * 48f);
        Draw.color(COL, 0.55f * a);
        Lines.stroke(1.8f * a);
        Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow2Out) * 30f);
        Angles.randLenVectors(e.id, 12, 10f + 80f * e.finpow(), e.rotation + 180f, 30f, (x, y) -> {
            Draw.color(STEEL, 0.6f * e.fout());
            Lines.stroke(1.6f * e.fout() + 0.2f);
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4f + 18f * e.fout());
        });
        Draw.color(Color.white, a);
        Bulvasyr.drawStar(e.x, e.y, 0f, 6f + 34f * e.fout(Interp.pow2Out), 0.6f + 4.4f * e.fout(), 9f * e.fout());
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 90f * e.fout(), COL2, 0.7f * e.fout());
    }).layer(Layer.groundUnit - 1f);

    /** Tiếp đất: sóng chấn, bụi bung, bụi sao tung toé. */
    static final Effect endFx = new Effect(44f, 240f, e -> {
        float a = e.fout(Interp.pow2);
        Draw.blend(Blending.additive);
        Draw.color(COL2, 0.7f * a);
        Lines.stroke(3f * a + 0.3f);
        Lines.circle(e.x, e.y, 3f + e.fin(Interp.pow3Out) * 56f);
        Draw.color(COL, 0.5f * a);
        Lines.stroke(1.6f * a);
        Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow2Out) * 34f);
        Draw.color(YEL, RED, e.fin());
        Lines.stroke(1.4f * a);
        Angles.randLenVectors(e.id + 4, 12, 6f + 52f * e.finpow(), (x, y) ->
            Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 1f + 6f * a));
        Draw.color(Color.white, a);
        Bulvasyr.drawStar(e.x, e.y, 0f, 4f + 26f * e.fin(Interp.pow2Out), 3.6f * a + 0.3f, 8f * a);
        Draw.blend();
        Draw.color(Color.valueOf("8d93a6"), 0.45f * e.fout());
        Angles.randLenVectors(e.id, 9, 6f + 34f * e.finpow(), (x, y) -> arc.graphics.g2d.Fill.circle(e.x + x, e.y + y, 2f + 4f * e.fin()));
        Draw.reset();
    }).layer(Layer.groundUnit - 1f);

    // ---------- phím ----------
    public static KeyCode getSkillKey() {
        String name = Core.settings.getString(KEYBIND_SETTING, KeyCode.h.name());
        try { return KeyCode.valueOf(name); } catch (Throwable ignored) { return KeyCode.h; }
    }

    public static void setSkillKey(KeyCode key) {
        if (key != null) Core.settings.put(KEYBIND_SETTING, key.name());
    }

    public static void buildKeybindRow(Table table) {
        if (table == null) return;
        final boolean[] listening = {false};
        Table row = new Table(Styles.black6);
        row.margin(8f);
        row.add("[#7A68FF]Bulvasyr[] - Phím Lướt (PC):").left().growX().padRight(10f);
        final TextButton keyBtn = new TextButton(getSkillKey().value, Styles.defaultt);
        keyBtn.update(() -> {
            if (listening[0]) {
                keyBtn.setText("[accent]> Nhấn phím bất kỳ... <[]");
                for (KeyCode k : KeyCode.all) {
                    if (k == KeyCode.unknown || k == KeyCode.mouseLeft || k == KeyCode.mouseRight || k == KeyCode.mouseMiddle) continue;
                    if (Core.input.keyTap(k)) {
                        if (k != KeyCode.escape) setSkillKey(k);
                        listening[0] = false;
                        keyBtn.setText(getSkillKey().value);
                        break;
                    }
                }
            } else keyBtn.setText(getSkillKey().value);
        });
        keyBtn.clicked(() -> listening[0] = !listening[0]);
        row.add(keyBtn).width(170f).height(38f).right();
        table.add(row).growX().padBottom(6f).row();
    }

    static boolean isBul(Unit u) {
        return u != null && Bulvasyr.bulvasyr != null && u.type == Bulvasyr.bulvasyr;
    }

    static boolean isActive() {
        try { return NewModePvp.apiIsActive(); } catch (Throwable err) { return false; }
    }

    // ---------- mạng ----------
    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ, BulvasyrDash::onReq, false);
        NexNet.onBroadcast(FX, BulvasyrDash::onFx);
    }

    /** Client: tính hướng lướt rồi gửi yêu cầu. PC lướt về phía chuột, mobile lướt theo hướng đang chạy. */
    static void castFromClient() {
        if (Vars.state == null || Vars.state.isPaused() || Vars.player == null || Time.time < cdEnd) return;
        Unit u = Vars.player.unit();
        if (!isBul(u) || u.dead || dashes.containsKey(u.id)) return;
        float ang;
        if (Vars.mobile) {
            ang = u.vel.len() > 0.15f ? u.vel.angle() : u.rotation;
        } else {
            Vec2 m = Core.input.mouseWorld();
            ang = Angles.angle(u.x, u.y, m.x, m.y);
        }
        cdEnd = Time.time + CD;
        if (Vars.net.client()) { // máy khách điều khiển unit của mình: tự lướt cục bộ để không bị kéo lại
            Dash d = new Dash();
            d.uid = u.id; d.ang = ang;
            dashes.put(u.id, d);
        }
        NexNet.send(REQ, String.valueOf(ang));
    }

    static void onReq(Player player, String payload) {
        if (!isActive() || player == null) return;
        Unit u = player.unit();
        if (!isBul(u) || u.dead || dashes.containsKey(u.id)) return;
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        float ang;
        try { ang = Float.parseFloat(payload); } catch (Throwable err) { return; }
        cdEndSrv.put(player.id, Time.time + CD);
        try { GearItems.onUnitSkillCast(u); } catch (Throwable ignored) {}
        Dash d = new Dash();
        d.uid = u.id; d.ang = ang;
        dashes.put(u.id, d);
        NexNet.broadcast(FX, u.id + "," + ang + "," + u.x + "," + u.y);
    }

    static void onFx(String payload) {
        if (Vars.headless) return;
        try {
            String[] p = payload.split(",");
            Vis v = new Vis();
            v.uid = Integer.parseInt(p[0]);
            v.ang = Float.parseFloat(p[1]);
            float x = Float.parseFloat(p[2]), y = Float.parseFloat(p[3]);
            vis.add(v);
            startFx.at(x, y, v.ang);
            Effect.shake(3.4f, 16f, x, y);
            Sounds.shootReign.at(x, y, 1.6f, 0.7f);
        } catch (Throwable err) {
            Log.err("[BulvasyrDash] fx: " + err);
        }
    }

    // ---------- vòng lặp ----------
    static void tickDash() {
        Iterator<Dash> it = dashes.values().iterator();
        while (it.hasNext()) {
            Dash d = it.next();
            Unit u = Groups.unit.getByID(d.uid);
            if (u == null || u.dead || !u.isValid()) { it.remove(); continue; }
            d.t += Time.delta;
            float p = Mathf.clamp(d.t / DUR);
            float f = Interp.smoother.apply(p);       // vận tốc dạng chuông: tăng dần, lướt nhanh, giảm dần
            float step = (f - d.prevF) * DIST;
            d.prevF = f;
            u.move(Mathf.cosDeg(d.ang) * step, Mathf.sinDeg(d.ang) * step);
            u.vel.setZero();
            u.rotation = Angles.moveToward(u.rotation, d.ang, 30f * Time.delta);
            if (p >= 1f) it.remove();
        }
    }

    static void update() {
        tryNet();
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!dashes.isEmpty()) dashes.clear();
            if (!vis.isEmpty()) vis.clear();
            if (!ghosts.isEmpty()) ghosts.clear();
            return;
        }
        if (Vars.state.isPaused()) return;
        if (!Vars.headless && !Vars.mobile && isActive() && Vars.player != null && Core.scene != null
            && !Core.scene.hasField() && Core.input.keyTap(getSkillKey())) {
            castFromClient();
        }
        tickDash();
        if (Vars.headless) return;

        for (int i = vis.size() - 1; i >= 0; i--) {
            Vis v = vis.get(i);
            Unit u = Groups.unit.getByID(v.uid);
            v.t += Time.delta;
            if (u != null && !u.dead && v.t < DUR + 2f) {
                // lưu vệt đường đi (mới nhất ở đầu mảng)
                System.arraycopy(v.tx, 0, v.tx, 1, v.tx.length - 1);
                System.arraycopy(v.ty, 0, v.ty, 1, v.ty.length - 1);
                v.tx[0] = u.x; v.ty[0] = u.y;
                v.n = Math.min(v.n + 1, v.tx.length);
                // bóng ảnh: dày hơn khi đang lướt nhanh
                float sp = Mathf.sin(Mathf.clamp(v.t / DUR) * Mathf.PI);
                v.ghostAcc += Time.delta * (0.4f + sp * 1.4f);
                while (v.ghostAcc >= 1f) {
                    v.ghostAcc -= 1f;
                    Ghost g = new Ghost();
                    g.x = u.x; g.y = u.y; g.rot = u.rotation; g.life = 22f; g.region = u.type.fullIcon;
                    ghosts.add(g);
                }
            } else if (!v.ended) {
                v.ended = true;
                if (u != null) {
                    endFx.at(u.x, u.y, v.ang);
                    Effect.shake(2.6f, 12f, u.x, u.y);
                    Sounds.explosion.at(u.x, u.y, 1.5f, 0.5f);
                }
            }
            if (v.t > DUR + 26f) vis.remove(i);
        }
        for (int i = ghosts.size() - 1; i >= 0; i--) {
            Ghost g = ghosts.get(i);
            g.t += Time.delta;
            if (g.t >= g.life) ghosts.remove(i);
        }
    }

    static void draw() {
        if (Vars.state == null || !Vars.state.isGame() || (vis.isEmpty() && ghosts.isEmpty())) return;
        float z = Draw.z();
        Draw.z(Layer.groundUnit - 0.4f);
        Draw.blend(Blending.additive);
        // bóng ảnh để lại sau lưng
        for (Ghost g : ghosts) {
            if (g.region == null) continue;
            float p = g.t / g.life;
            Draw.mixcol(Tmp2.lerp(COL, COL2, p), 1f);
            Draw.alpha(0.5f * (1f - p) * (1f - p));
            Draw.rect(g.region, g.x, g.y, g.rot - 90f);
        }
        Draw.mixcol();
        for (Vis v : vis) {
            Unit u = Groups.unit.getByID(v.uid);
            float fade = v.t > DUR ? Mathf.clamp(1f - (v.t - DUR) / 26f) : 1f;
            // dải sáng đuôi: nhọn dần về phía sau, đổi màu từ xanh lam sang tím
            for (int i = 0; i < v.n - 1; i++) {
                float k = 1f - i / (float) v.n;
                Draw.color(Tmp2.lerp(COL, COL2, k), 0.75f * k * fade);
                Lines.stroke(2f + 9f * k * k);
                Lines.line(v.tx[i], v.ty[i], v.tx[i + 1], v.ty[i + 1]);
                Draw.color(Color.white, 0.5f * k * fade);
                Lines.stroke(0.8f + 2.2f * k * k);
                Lines.line(v.tx[i], v.ty[i], v.tx[i + 1], v.ty[i + 1]);
            }
            // vệt tốc độ mảnh chạy dọc hai bên thân
            if (u != null && !u.dead && v.t < DUR + 2f) {
                float sp = Mathf.sin(Mathf.clamp(v.t / DUR) * Mathf.PI);
                float c = Mathf.cosDeg(v.ang), s = Mathf.sinDeg(v.ang);
                for (int k = 0; k < 9; k++) {
                    float lat = Mathf.sin(k * 2.399f + v.uid) * u.hitSize * 0.75f;
                    float len = (14f + 70f * sp) * (0.6f + 0.4f * Mathf.absin(k * 1.7f + v.t * 0.35f, 4f, 1f));
                    float sx = u.x - s * lat - c * (k * 3f), sy = u.y + c * lat - s * (k * 3f);
                    Draw.color(k % 3 == 0 ? COL2 : STEEL, 0.5f * sp);
                    Lines.stroke(0.9f + 0.5f * (k % 2));
                    Lines.line(sx, sy, sx - c * len, sy - s * len);
                }
                Drawf.light(u.x, u.y, 56f + 50f * sp, COL2, 0.55f * sp);
            }
        }
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }

    /** Màu trộn dùng chung (tránh tạo Color mỗi khung hình). */
    static final class Tmp2 {
        static final Color c = new Color();
        static Color lerp(Color a, Color b, float t) { return c.set(a).lerp(b, Mathf.clamp(t)); }
    }

    // ---------- nút HUD ----------
    static void clamp(Table t) {
        t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
    }

    static void build() {
        if (btn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        Table t = new Table(Styles.black6);
        btn = t;
        t.name = "newex-bulvasyr-dash";
        t.margin(6);
        t.touchable = Touchable.enabled;
        float sz = Vars.mobile ? 56 : 48;
        if (Bulvasyr.bulvasyr != null) t.image(Bulvasyr.bulvasyr.uiIcon).size(sz).row();
        Label lbl = t.add("").get();
        lbl.setFontScale(0.9f);
        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0) {
                lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s");
                lbl.setColor(Color.gray);
            } else {
                lbl.setText(Vars.mobile ? "LƯỚT" : "LƯỚT [" + getSkillKey().value + "]");
                lbl.setColor(COL);
            }
        });
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown && Vars.player != null && isBul(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f + 170);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false; offX = x; offY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging) {
                    Core.settings.put(KEY + "-x", Math.round(t.x));
                    Core.settings.put(KEY + "-y", Math.round(t.y));
                } else castFromClient();
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    public static void init() {
        Events.run(Trigger.update, BulvasyrDash::update);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try { build(); } catch (Throwable err) { Log.err("[BulvasyrDash] ui: " + err); }
            });
            Events.run(Trigger.draw, BulvasyrDash::draw);
        }
        Events.on(WorldLoadEvent.class, e -> {
            cdEnd = 0f;
            cdEndSrv.clear();
            dashes.clear();
            vis.clear();
            ghosts.clear();
        });
    }
}
