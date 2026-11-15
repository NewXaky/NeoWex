package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
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
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import newex.NexNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Chuyển từ archueng-pvp.js. Trong NewMode PvP: thay unit disrupt (cấp 7) bằng archueng
 * + kỹ năng kiếm khí hình quạt (nút HUD kéo thả được, bấm 3 lần: mở chọn hướng -> chốt hướng -> tung).
 * Thay cho global.NexPvp.isActive() là {@link #pvpActive} (khi chuyển newmode-pvp sang Java thì gán vào đây).
 */
public final class ArchuengPvp {
    private ArchuengPvp() {}

    public static BooleanSupplier pvpActive = () -> false;

    static final String REQ = "newex-archueng-skill", FAN = "newex-archueng-fan", KEY = "newex-archueng-btn";
    static final float CD_T = 600f, WINDUP = 42f, SLOW_T = 72f, FAST_T = 60f, FADE_T = 45f, MARK_T = 180f, MARK_SLOW = 0.5f;
    static final float SLOW_SPD = 0.6f, FAST_SPD = 9f;
    static final float DMG = 500f, ATK_PCT = 1.6f, ARM_PCT = 0.5f, HALF = 40f, RAD = 70f;
    static final Color COL = Color.valueOf("5ab4ff"), COL2 = Color.valueOf("bff4ff"), GOLD = Color.valueOf("ffc83c");
    static final Color FIRE = Color.valueOf("ff8a3c"), FIRE2 = Color.valueOf("ff3c1e");

    static StatusEffect markStatus;
    static UnitType arch = null, orig = null;
    static boolean swapped = false, netOk = false;
    static final ArrayList<Fan> fans = new ArrayList<>();
    static final HashMap<Integer, Spin> spins = new HashMap<>();
    static float cdEnd = 0;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    /**
     * PC (bấm 3 lần): 0 = rảnh, 1 = chờ bấm lần 2 chọn hướng, 2 = đã chọn hướng, chờ bấm lần 3 để tung.
     * MOBILE (bấm 4 lần): 0 = rảnh, 1 = chờ chạm màn hình chọn hướng, 2 = đã đặt hướng (chạm tiếp để đổi, nút hiện "KHÓA"),
     * 3 = đã khóa hướng (nút hiện "TUNG"), bấm nút lần nữa mới tung.
     */
    static int phase = 0;
    static float aimAng = 0;

    /** Phase "sẵn sàng tung": mobile = 3 (sau khi khóa), PC = 2. */
    static int firePhase() { return Vars.mobile ? 3 : 2; }

    /** Điểm thế giới vừa chạm (mobile lấy từ vị trí chạm, PC lấy từ chuột). */
    static float[] touchWorld() {
        try {
            if (Vars.mobile) {
                arc.math.geom.Vec2 w = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
                return new float[] {w.x, w.y};
            }
        } catch (Throwable err) {}
        return new float[] {Vars.player.mouseX, Vars.player.mouseY};
    }

    /** Lần chạm rơi vào UI (joystick, nút, dialog...) thì không tính là chạm chọn hướng. */
    static boolean touchOnUi() {
        try { return Vars.mobile && Core.scene != null && Core.scene.hasMouse(Core.input.mouseX(), Core.input.mouseY()); }
        catch (Throwable err) { return false; }
    }
    static boolean uiTapConsumed = false, wasTouchedPrev = false;
    static Table btn = null, cancelBtn = null;

    static class Spin { float t, a; Spin(float a) { this.a = a; } }

    static class Fan {
        int uid, team;
        float ang, x, y, t, dmg, lx, ly;
        boolean landed;
        final java.util.HashSet<String> hit = new java.util.HashSet<>();
    }

    /** Gọi từ NexMod.loadContent(): tạo status effect "ấn làm chậm". */
    public static void create() {
        markStatus = new StatusEffect("archueng-mark");
        markStatus.speedMultiplier = MARK_SLOW;
        markStatus.show = false;
        markStatus.color = COL;
    }

    static UnitType findArch() {
        if (arch != null) return arch;
        try {
            for (UnitType u : Vars.content.units()) {
                String n = u.name;
                if (n.equals("archueng") || n.endsWith("-archueng")) { arch = u; break; }
            }
        } catch (Throwable err) {}
        return arch;
    }

    static boolean isActive() {
        try { return pvpActive != null && pvpActive.getAsBoolean(); } catch (Throwable err) { return false; }
    }

    /** Thay disrupt -> archueng khi mode đang chạy. */
    static void syncType() {
        UnitType a = findArch();
        if (a == null) return;
        try {
            if (orig == null) orig = UnitTypes.disrupt;
            boolean want = isActive();
            if (want != swapped) { UnitTypes.disrupt = want ? a : orig; swapped = want; }
        } catch (Throwable err) { Log.err("[Archueng] swap failed: " + err); }
    }

    static boolean isArch(Unit u) { return u != null && arch != null && u.type == arch; }

    // ---- server: nhận yêu cầu dùng skill ----
    static void onReq(Player player, String payload) {
        if (!isActive()) return;
        Unit u = player.unit();
        if (!isArch(u) || u.dead) return;
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + CD_T);
        float ang;
        try { ang = Float.parseFloat(String.valueOf(payload)); } catch (Throwable e) { ang = u.rotation; }
        if (Float.isNaN(ang)) ang = u.rotation;
        NexNet.broadcast(FAN, u.id + "," + u.team.id + "," + (Math.round(ang * 10) / 10f) + "," + u.x + "," + u.y);
    }

    /** Sát thương = 160% tấn công + 50% giáp. */
    static float calcDmg(int id) {
        Unit o = Groups.unit.getByID(id);
        return o != null ? DMG * ATK_PCT * Math.max(0.1f, o.damageMultiplier) + ARM_PCT * o.armor : DMG * ATK_PCT;
    }

    // ---- mọi máy: nhận lệnh tạo quạt ----
    static void onFan(String payload) {
        String[] p = String.valueOf(payload).split(",");
        Fan f = new Fan();
        f.uid = Integer.parseInt(p[0]);
        f.team = Integer.parseInt(p[1]);
        f.ang = Float.parseFloat(p[2]);
        f.x = Float.parseFloat(p[3]);
        f.y = Float.parseFloat(p[4]);
        f.t = -WINDUP;
        f.dmg = calcDmg(f.uid);
        spins.put(f.uid, new Spin(f.ang));
        fans.add(f);
    }

    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ, ArchuengPvp::onReq, false);
        NexNet.onBroadcast(FAN, ArchuengPvp::onFan);
    }

    static boolean inFan(Fan f, float x, float y) {
        float d = Mathf.dst(f.x, f.y, x, y);
        return d <= RAD + 6 && Math.abs(Angles.angleDist(Angles.angle(f.x, f.y, x, y), f.ang)) <= HALF + (d < 12 ? 180 : 0);
    }

    static void hitFan(Fan f) {
        Team team = Team.get(f.team);
        float reach = RAD + 6;
        Units.nearbyEnemies(team, f.x - reach, f.y - reach, reach * 2, reach * 2, e -> {
            if (e.dead || f.hit.contains("u" + e.id) || !inFan(f, e.x, e.y)) return;
            f.hit.add("u" + e.id);
            e.damage(f.dmg);
            try { if (markStatus != null) e.apply(markStatus, MARK_T); } catch (Throwable err) {}
            try { Call.effect(Fx.hitLaserBlast, e.x, e.y, f.ang, COL2); Call.effect(Fx.sparkShoot, e.x, e.y, f.ang, GOLD); } catch (Throwable err) {}
        });
        Units.nearbyBuildings(f.x, f.y, reach, b -> {
            if (b.team == team || f.hit.contains("b" + b.pos()) || !inFan(f, b.x, b.y)) return;
            f.hit.add("b" + b.pos());
            b.damage(f.dmg);
            try { Call.effect(Fx.hitLaserBlast, b.x, b.y, f.ang, COL2); } catch (Throwable err) {}
        });
    }

    static float rx(float s) { return Mathf.range(RAD * s); }

    static void update() {
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!fans.isEmpty()) { fans.clear(); spins.clear(); }
            return;
        }
        tryNet();
        syncType();
        if (Vars.state.isPaused()) return;

        for (int i = fans.size() - 1; i >= 0; i--) {
            Fan f = fans.get(i);
            f.t += Time.delta;
            if (f.t < 0) {
                Unit u = Groups.unit.getByID(f.uid);
                if (u != null) { f.x = u.x; f.y = u.y; }
                continue;
            }
            float fl = f.t - (SLOW_T + FAST_T), prog = fl > 0 ? Mathf.clamp(fl / FADE_T) : 0f;
            float spd = f.t < SLOW_T ? SLOW_SPD : FAST_SPD * (1 - prog) * (1 - prog);
            f.x += Angles.trnsx(f.ang, spd * Time.delta);
            f.y += Angles.trnsy(f.ang, spd * Time.delta);
            try {
                if (f.t - Time.delta < 0) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL); Effect.shake(2, 6, f.x, f.y); }
                if (f.t > SLOW_T && !f.landed) { f.landed = true; f.lx = f.x; f.ly = f.y; }
                if (f.t > SLOW_T && prog == 0) {
                    if (Mathf.chance(0.7)) Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, rx(0.8f)), f.y + Angles.trnsy(f.ang + 90, rx(0.8f)), f.ang, Mathf.chance(0.3) ? GOLD : COL2);
                } else if (Mathf.chance(0.25)) {
                    Fx.sparkShoot.at(f.x + Angles.trnsx(f.ang + 90, rx(0.7f)), f.y + Angles.trnsy(f.ang + 90, rx(0.7f)), f.ang, GOLD);
                }
                if (f.t - Time.delta <= SLOW_T && f.t > SLOW_T) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL2); Effect.shake(4, 8, f.x, f.y); }
            } catch (Throwable err) {}
            if (prog > 0 && Mathf.chance(0.5)) {
                try { Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, rx(0.8f)), f.y + Angles.trnsy(f.ang + 90, rx(0.8f)), f.ang, Mathf.chance(0.3) ? GOLD : COL2); } catch (Throwable err) {}
            }
            if (!Vars.net.client() && prog == 0) hitFan(f);
            if (f.t >= SLOW_T + FAST_T + FADE_T) fans.remove(i);
        }
        for (Iterator<Map.Entry<Integer, Spin>> it = spins.entrySet().iterator(); it.hasNext();) {
            Spin s = it.next().getValue();
            s.t += Time.delta;
            if (s.t > WINDUP) it.remove();
        }

        // lần bấm thứ 2: chạm/bấm chuột ở vị trí muốn đánh để chốt hướng
        if (!Vars.headless && Vars.player != null) {
            Unit pu = Vars.player.unit();
            if (phase != 0 && (!isArch(pu) || pu.dead)) phase = 0;
            boolean touched = Core.input.isTouched();
            if (touched && !wasTouchedPrev && !uiTapConsumed && isArch(pu) && !pu.dead) {
                if (Vars.mobile) {
                    // mobile: lần chạm thứ 2 đặt hướng; chạm thêm (khi chưa khóa) để đổi hướng
                    if ((phase == 1 || phase == 2) && !touchOnUi()) {
                        float[] tp = touchWorld();
                        aimAng = Angles.angle(pu.x, pu.y, tp[0], tp[1]);
                        phase = 2;
                    }
                } else if (phase == 1) {
                    aimAng = Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                    phase = 2;
                }
            }
            if (!touched) uiTapConsumed = false;
            wasTouchedPrev = touched;
        }
    }

    // ---------------- vẽ ----------------
    /** Lưỡi liềm kiếm khí: mép ngoài sáng rực, mỏng dần về hai đầu nhọn, mờ vào trong. */
    static void crescent(float x, float y, float ang, float rad, float half, float thick, Color cO, float aO, Color cI, float aI) {
        Tmp.c1.set(cO); Tmp.c1.a = aO; Tmp.c2.set(cI); Tmp.c2.a = aI;
        float bo = Tmp.c1.toFloatBits(), bi = Tmp.c2.toFloatBits();
        int N = 12;
        for (int q = 0; q < N; q++) {
            float f1 = (float) q / N, f2 = (float) (q + 1) / N;
            float a1 = ang - half + half * 2 * f1, a2 = ang - half + half * 2 * f2;
            float r1 = rad * (1 - thick * (float) Math.sin(Math.PI * f1)), r2 = rad * (1 - thick * (float) Math.sin(Math.PI * f2));
            Fill.quad(x + Angles.trnsx(a1, r1), y + Angles.trnsy(a1, r1), bi, x + Angles.trnsx(a1, rad), y + Angles.trnsy(a1, rad), bo,
                      x + Angles.trnsx(a2, rad), y + Angles.trnsy(a2, rad), bo, x + Angles.trnsx(a2, r2), y + Angles.trnsy(a2, r2), bi);
        }
    }

    static void draw() {
        if (arch == null) return;
        TextureRegion wreg = Core.atlas.find("newex-archueng-weapon"), slash = Core.atlas.find("newex-archueng-slash");
        if (!wreg.found()) wreg = Core.atlas.find("archueng-weapon");

        // đường ngắm. Mobile: phase 1 chưa có hướng nên không vẽ; phase 2 = đang chỉnh (xanh); phase 3 = đã khóa (cam)
        if (Vars.player != null && phase != 0 && !(Vars.mobile && phase == 1)) {
            Unit pu = Vars.player.unit();
            if (isArch(pu) && !pu.dead) {
                boolean locked = phase == firePhase();
                float ang = phase >= 2 ? aimAng : Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                float ex = pu.x + Angles.trnsx(ang, 160), ey = pu.y + Angles.trnsy(ang, 160);
                Draw.z(Layer.flyingUnit + 2);
                Drawf.dashLine(locked ? FIRE : COL2, pu.x, pu.y, ex, ey);
                Draw.reset();
            }
        }

        for (Map.Entry<Integer, Spin> en : spins.entrySet()) {
            Unit u = Groups.unit.getByID(en.getKey());
            Spin s = en.getValue();
            if (u == null || u.dead) continue;
            float fr = Mathf.clamp(s.t / WINDUP);
            Draw.z(Layer.flyingUnit + 1);
            float sw = Interp.pow2Out.apply(fr), a0 = s.a - HALF, cur = a0 + HALF * 2 * sw, L = 64f;
            float rr = 26 + 16 * fr;
            Draw.z(Layer.groundUnit - 1);
            Draw.blend(Blending.additive);
            for (int q = 0; q < 14; q++) {
                float ph = ((s.t * 0.05f) + q * 0.0714f) % 1f, qa = q * 25.7f + s.t * 2, r0 = rr * 1.9f * (1 - ph) + 6, r1 = r0 + 7 + 9 * ph;
                Draw.color(q % 3 == 0 ? FIRE2 : (q % 3 == 1 ? FIRE : GOLD), ph * (0.45f + 0.55f * fr));
                Lines.stroke(0.8f + ph * 1.4f);
                Lines.line(u.x + Angles.trnsx(qa, r0), u.y + Angles.trnsy(qa, r0), u.x + Angles.trnsx(qa, r1), u.y + Angles.trnsy(qa, r1));
            }
            Draw.blend(); Draw.reset();
            Draw.z(Layer.flyingUnit + 1);
            int segs = 14;
            float step = (cur - a0) / segs;
            for (int q = 0; q < segs; q++) {
                float t1 = a0 + step * q, t2 = t1 + step, al = (float) Math.pow((q + 1f) / segs, 1.6) * 0.85f * (1 - fr * 0.5f);
                float cIn = Tmp.c1.set(0.1f, 0.4f, 0.95f, 0f).toFloatBits(), cOut = Tmp.c2.set(0.55f, 0.95f, 1f, al).toFloatBits(), cMid = Tmp.c3.set(0.15f, 0.55f, 1f, al * 0.6f).toFloatBits();
                float r0 = 10, r1 = L * 0.55f, r2 = L * 1.05f;
                Fill.quad(u.x + Angles.trnsx(t1, r0), u.y + Angles.trnsy(t1, r0), cIn, u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid,
                          u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid, u.x + Angles.trnsx(t2, r0), u.y + Angles.trnsy(t2, r0), cIn);
                Fill.quad(u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid, u.x + Angles.trnsx(t1, r2), u.y + Angles.trnsy(t1, r2), cOut,
                          u.x + Angles.trnsx(t2, r2), u.y + Angles.trnsy(t2, r2), cOut, u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid);
            }
            Draw.color();
            float bx = u.x + Angles.trnsx(cur, L), by = u.y + Angles.trnsy(cur, L), ba = 1 - fr * 0.4f;
            Draw.blend(Blending.additive);
            Draw.color(COL, 0.35f * ba); Lines.stroke(7); Lines.line(u.x + Angles.trnsx(cur, 12), u.y + Angles.trnsy(cur, 12), bx, by);
            Draw.color(Color.white, 0.95f * ba); Lines.stroke(2.2f); Lines.line(u.x + Angles.trnsx(cur, 12), u.y + Angles.trnsy(cur, 12), bx, by);
            for (int g = 0; g < 4; g++) { Draw.color(g % 2 == 1 ? GOLD : COL2, 0.9f * ba); Drawf.tri(bx, by, 5 - (g % 2) * 2, g % 2 == 1 ? 11 : 24, cur + g * 90); }
            Draw.color(Color.white, ba); Fill.circle(bx, by, 3.2f);
            Draw.blend(); Draw.reset();
            if (wreg.found() && fr < 1) {
                Draw.blend(Blending.additive);
                for (int g = 3; g >= 1; g--) {
                    float ca = cur - HALF * 0.16f * g * (1 - fr * 0.5f), gl = wreg.height / 4f, go = Math.max(10f, gl * 0.5f);
                    Draw.color(COL2, 0.22f * (4 - g) / 3f);
                    Draw.rect(wreg, u.x + Angles.trnsx(ca, go), u.y + Angles.trnsy(ca, go), wreg.width / 4f, gl, ca - 90);
                }
                Draw.blend(); Draw.reset();
            }
            if (wreg.found()) {
                float wl = wreg.height / 4f, wo = Math.max(10f, wl * 0.5f);
                Draw.rect(wreg, u.x + Angles.trnsx(cur, wo), u.y + Angles.trnsy(cur, wo), wreg.width / 4f, wl, cur - 90);
            }
            try {
                float tx = u.x + Angles.trnsx(cur, L), ty = u.y + Angles.trnsy(cur, L);
                if (Mathf.chance(0.8)) Fx.hitBulletColor.at(tx, ty, cur + 90, COL);
                if (Mathf.chance(0.35)) Fx.sparkShoot.at(tx, ty, cur, GOLD);
                if (Mathf.chance(0.3)) Fx.missileTrail.at(tx, ty, 3, COL2);
            } catch (Throwable err) {}
            Draw.reset();
        }

        for (Fan f : fans) {
            if (f.t < 0) continue;
            boolean fast = f.t > SLOW_T;
            float pg = Mathf.clamp((f.t - SLOW_T - FAST_T) / FADE_T), fade = 1 - pg * pg;
            Draw.z(Layer.bullet + 2);
            float sc = RAD * 2 * (fast ? 1 + 0.3f * pg : 1 + Mathf.sin(f.t, 6, 0.04f)) * (fast ? 1.15f : 1f);
            Draw.blend(Blending.additive);
            if (slash.found()) {
                Draw.color(COL, 0.3f * fade); Draw.rect(slash, f.x, f.y, sc * 1.5f, sc * 1.5f, f.ang + Mathf.sin(f.t, 9, 1.5f));
                Draw.color(GOLD, 0.22f * fade); Draw.rect(slash, f.x, f.y, sc * 1.2f, sc * 1.2f, f.ang - Mathf.sin(f.t, 11, 1.2f));
            }
            if (fast) for (int g = 1; g <= 5; g++) {
                float d = g * 13 * (1 - pg * 0.5f);
                crescent(f.x - Angles.trnsx(f.ang, d), f.y - Angles.trnsy(f.ang, d), f.ang, RAD * (1 - 0.05f * g), HALF * (1 + 0.04f * g), 0.38f, COL2, 0.55f * fade / g, COL, 0f);
            }
            crescent(f.x, f.y, f.ang, RAD, HALF, 0.5f, Color.white, fade, COL, 0.14f * fade);
            crescent(f.x, f.y, f.ang, RAD * 0.9f, HALF * 0.85f, 0.2f, GOLD, 0.55f * fade, GOLD, 0f);
            Draw.color(COL, 0.35f * fade); Lines.stroke(6); Lines.arc(f.x, f.y, RAD, HALF * 2 / 360f, f.ang - HALF);
            Draw.color(Color.white, fade); Lines.stroke(2.4f); Lines.arc(f.x, f.y, RAD, HALF * 2 / 360f, f.ang - HALF);
            for (int sd = -1; sd <= 1; sd += 2) {
                float hx = f.x + Angles.trnsx(f.ang + HALF * sd, RAD), hy = f.y + Angles.trnsy(f.ang + HALF * sd, RAD);
                Draw.color(COL2, 0.8f * fade); Fill.circle(hx, hy, 3.5f + Mathf.sin(f.t, 4, 1)); Draw.color(Color.white, fade); Fill.circle(hx, hy, 1.8f);
            }
            if (fast) for (int q = 0; q < 8; q++) {
                float la = f.ang + (q / 7f - 0.5f) * HALF * 1.7f, st = RAD * (0.55f + 0.4f * ((q * 37) % 10) / 10f), len = (22 + (q % 3) * 14) * fade;
                Draw.color(q % 2 == 1 ? COL2 : GOLD, 0.6f * fade); Lines.stroke(1.1f);
                Lines.line(f.x + Angles.trnsx(la, st), f.y + Angles.trnsy(la, st), f.x + Angles.trnsx(la, st) - Angles.trnsx(f.ang, len), f.y + Angles.trnsy(la, st) - Angles.trnsy(f.ang, len));
            }
            for (int q = 0; q < 9; q++) {
                float off = (q / 8f - 0.5f) * HALF * 2.1f + Mathf.sin(f.t * 0.08f + q * 2, 1, 6);
                float rd = RAD * (0.95f + 0.25f * Mathf.sin(f.t * 0.12f + q, 1, 1)) + (q % 2) * 8;
                Draw.color(q % 2 == 1 ? GOLD : COL2, (0.5f + 0.5f * Mathf.sin(f.t * 0.2f + q, 1, 1)) * fade);
                Fill.square(f.x + Angles.trnsx(f.ang + off, rd), f.y + Angles.trnsy(f.ang + off, rd), 1.8f + (q % 3) * 0.6f, f.t * 4 + q * 40);
            }
            if (f.landed && f.t - SLOW_T < 26) {
                float rp = (f.t - SLOW_T) / 26f, ip = Interp.pow3Out.apply(rp);
                Draw.color(COL2, (1 - rp) * 0.9f); Lines.stroke(3 * (1 - rp) + 0.5f); Lines.circle(f.lx, f.ly, 12 + 95 * ip);
                Draw.color(GOLD, (1 - rp) * 0.7f); Lines.stroke(1.5f * (1 - rp) + 0.3f); Lines.circle(f.lx, f.ly, 8 + 60 * Interp.pow2Out.apply(rp));
                for (int q = 0; q < 12; q++) {
                    float qa = q * 30 + 7, r0 = 16 + 85 * ip;
                    Draw.color(q % 2 == 1 ? Color.white : COL2, (1 - rp) * 0.85f); Lines.stroke(1.8f * (1 - rp) + 0.3f);
                    Lines.line(f.lx + Angles.trnsx(qa, r0), f.ly + Angles.trnsy(qa, r0), f.lx + Angles.trnsx(qa, r0 + 30 * (1 - rp)), f.ly + Angles.trnsy(qa, r0 + 30 * (1 - rp)));
                }
            }
            Draw.blend(); Draw.reset();
            try { Drawf.light(f.x, f.y, RAD * 1.8f, COL, 0.55f * fade); } catch (Throwable err) {}
        }
    }

    // ---------------- UI ----------------
    static void clamp(Table t) {
        t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
    }

    static void build() {
        if (btn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        UnitType a = findArch();
        Table t = new Table(Styles.black6);
        btn = t;
        t.name = "newex-archueng-skill";
        t.margin(6);
        t.touchable = Touchable.enabled;
        float sz = Vars.mobile ? 56 : 48;
        if (a != null) t.image(a.uiIcon).size(sz).row();
        Label lbl = t.add("").get();
        lbl.setFontScale(0.9f);
        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0) { lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s"); lbl.setColor(Color.gray); }
            else if (phase == 1) { lbl.setText("CHỌN HƯỚNG"); lbl.setColor(COL2); }
            else if (phase == 2 && Vars.mobile) { lbl.setText("KHÓA"); lbl.setColor(GOLD); }
            else if (phase == firePhase()) { lbl.setText("TUNG"); lbl.setColor(FIRE); }
            else { lbl.setText("SKILL"); lbl.setColor(COL2); }
        });
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown && Vars.player != null && isArch(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                dragging = false; offX = x; offY = y; uiTapConsumed = true; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                if (dragging) {
                    Core.settings.put(KEY + "-x", Math.round(t.x));
                    Core.settings.put(KEY + "-y", Math.round(t.y));
                } else if (!Vars.state.isPaused()) {
                    Unit u = Vars.player.unit();
                    if (phase == 0) {
                        if (Time.time >= cdEnd && isArch(u) && !u.dead) phase = 1;
                    } else if (phase == 1) {
                        phase = 0;
                    } else if (phase == 2 && Vars.mobile) {
                        phase = 3; // mobile, lần bấm 3: khóa hướng (nút đổi thành "TUNG"), chưa tung
                    } else if (phase == firePhase()) {
                        // PC lần 3 / mobile lần 4: tung theo hướng đã chốt, giờ mới tính hồi chiêu
                        cdEnd = Time.time + CD_T;
                        NexNet.send(REQ, String.valueOf(aimAng));
                        phase = 0;
                    }
                }
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    static void buildCancel() {
        if (cancelBtn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        Table c = new Table(Styles.black6);
        cancelBtn = c;
        c.name = "newex-archueng-cancel";
        c.margin(6);
        c.touchable = Touchable.enabled;
        Label lbl = c.add("HỦY").get();
        lbl.setFontScale(0.9f);
        lbl.setColor(Color.scarlet);
        c.pack();
        c.update(() -> {
            if (btn == null) return;
            c.pack();
            c.setPosition(btn.x + (btn.getWidth() - c.getWidth()) / 2f, btn.y - c.getHeight() - 6);
        });
        c.visible(() -> {
            Unit u = Vars.player != null ? Vars.player.unit() : null;
            return isActive() && Vars.ui.hudfrag.shown && phase != 0 && isArch(u) && !u.dead;
        });
        c.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) { uiTapConsumed = true; return true; }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) { phase = 0; }
        });
        Vars.ui.hudGroup.addChild(c);
    }

    public static void init() {
        Events.run(Trigger.update, ArchuengPvp::update);

        if (!Vars.headless) {
            Events.run(Trigger.draw, ArchuengPvp::draw);
            Events.on(ClientLoadEvent.class, e -> {
                try { build(); buildCancel(); } catch (Throwable err) { Log.err("[Archueng] ui: " + err); }
            });
            Events.on(WorldLoadEvent.class, e -> {
                cdEnd = 0; cdEndSrv.clear(); fans.clear(); spins.clear(); phase = 0; uiTapConsumed = false; wasTouchedPrev = false;
            });
        } else {
            Events.on(WorldLoadEvent.class, e -> { cdEndSrv.clear(); fans.clear(); spins.clear(); });
        }
    }
}
