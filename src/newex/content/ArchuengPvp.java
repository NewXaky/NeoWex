package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
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
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import newex.NexNet;
import newex.SfxCompat;
import newex.UnitSkillInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Archueng PvP - Kỹ năng kiếm khí hình quạt:
 * - Gây sát thương kỹ năng: 500% Tấn công + 5% Máu tối đa.
 * - Hưởng chỉ số từ trang nâng cấp NewMode PvP: Tấn công (% và flat), Bạo kích, Sát thương theo giáp (qua damageMultiplier),
 *   Xuyên giáp, Sát thương % máu tối đa, Sát thương quái spawn ngẫu nhiên, Đẩy lùi (qua NewModePvp.skillDamage).
 * - Khi chém hụt mục tiêu, nhát chém kết thúc giảm 65% thời gian hồi chiêu.
 * - Tùy chỉnh phím bấm PC trong Cài đặt NewEx -> NewMode PvP.
 */
public final class ArchuengPvp {
    private ArchuengPvp() {}

    public static BooleanSupplier pvpActive = () -> false;

    static final String REQ = "newex-archueng-skill", FAN = "newex-archueng-fan", KEY = "newex-archueng-btn";
    public static final String KEYBIND_SETTING = "newex-pvp-archueng-skill-key";

    static final float CD_T = 600f, WINDUP = 42f, SLOW_T = 72f, FAST_T = 60f, FADE_T = 45f, MARK_T = 180f, MARK_SLOW = 0.5f;
    static final float SLOW_SPD = 0.6f, FAST_SPD = 9f;
    static final float DMG = 500f, ATK_PCT = 5.0f, MAX_HP_PCT = 0.05f, MISS_CD_REFUND = 0.65f;
    static final float HALF = 40f, RAD = 70f;
    static final Color COL = Color.valueOf("5ab4ff"), COL2 = Color.valueOf("bff4ff"), GOLD = Color.valueOf("ffc83c");
    static final Color FIRE = Color.valueOf("ff8a3c"), FIRE2 = Color.valueOf("ff3c1e");

    static StatusEffect markStatus;
    static UnitType arch = null, orig = null;
    static boolean swapped = false, netOk = false;
    static final ArrayList<Fan> fans = new ArrayList<>();
    static final HashMap<Integer, Spin> spins = new HashMap<>();
    static float cdEnd = 0;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();

    static int phase = 0;
    static float aimAng = 0;

    static int firePhase() { return Vars.mobile ? 3 : 2; }

    public static KeyCode getSkillKey() {
        String name = Core.settings.getString(KEYBIND_SETTING, KeyCode.f.name());
        try {
            return KeyCode.valueOf(name);
        } catch (Throwable ignored) {
            return KeyCode.f;
        }
    }

    public static void setSkillKey(KeyCode key) {
        if (key != null) Core.settings.put(KEYBIND_SETTING, key.name());
    }

    /** Thêm dòng tùy chỉnh nút bấm skill Archueng cho PC vào bảng cài đặt NewEx -> NewMode PvP. */
    public static void buildKeybindRow(Table table) {
        if (table == null) return;
        final boolean[] listening = {false};
        Table row = new Table(Styles.black6);
        row.margin(8f);
        row.add("[#5ab4ff]Archueng[] - Phím dùng Skill (PC):").left().growX().padRight(10f);

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
            } else {
                keyBtn.setText(getSkillKey().value);
            }
        });
        keyBtn.clicked(() -> listening[0] = !listening[0]);
        row.add(keyBtn).width(170f).height(38f).right();
        table.add(row).growX().padBottom(6f).row();
    }

    static float[] touchWorld() {
        try {
            if (Vars.mobile) {
                arc.math.geom.Vec2 w = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY());
                return new float[]{w.x, w.y};
            }
        } catch (Throwable err) {}
        return new float[]{Vars.player.mouseX, Vars.player.mouseY};
    }

    static boolean touchOnUi() {
        try { return Vars.mobile && Core.scene != null && Core.scene.hasMouse(Core.input.mouseX(), Core.input.mouseY()); }
        catch (Throwable err) { return false; }
    }

    static boolean uiTapConsumed = false, wasTouchedPrev = false;
    static Table btn = null, cancelBtn = null;

    static class Spin {
        float t, a;
        Spin(float a) { this.a = a; }
    }

    static class Fan {
        int uid, ownerPlayerId, team;
        float ang, x, y, t, dmg, lx, ly;
        boolean landed;
        final java.util.HashSet<String> hit = new java.util.HashSet<>();
    }

    public static void create() {
        markStatus = new StatusEffect("archueng-mark");
        markStatus.speedMultiplier = MARK_SLOW;
        markStatus.show = false;
        markStatus.color = COL;
        UnitSkillInfo.registerArchueng();
    }

    public static UnitType findArch() {
        if (arch != null) return arch;
        try {
            for (UnitType u : Vars.content.units()) {
                String n = u.name;
                if (n.equals("archueng") || n.endsWith("-archueng")) {
                    arch = u;
                    UnitSkillInfo.registerArchueng();
                    break;
                }
            }
        } catch (Throwable err) {}
        return arch;
    }

    static boolean isActive() {
        try { return pvpActive != null && pvpActive.getAsBoolean(); } catch (Throwable err) { return false; }
    }

    static void syncType() {
        UnitType a = findArch();
        if (a == null) return;
        try {
            if (orig == null) orig = UnitTypes.disrupt;
            boolean want = isActive();
            if (want != swapped) { UnitTypes.disrupt = want ? a : orig; swapped = want; }
        } catch (Throwable err) { Log.err("[Archueng] swap failed: " + err); }
    }

    public static boolean isArch(Unit u) {
        findArch();
        return u != null && arch != null && u.type == arch;
    }

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
        NexNet.broadcast(FAN, u.id + "," + u.team.id + "," + (Math.round(ang * 10) / 10f) + "," + u.x + "," + u.y + "," + player.id);
    }

    /** Sát thương kỹ năng = 500% Tấn công (đã gồm chỉ số Tấn công từ trang nâng cấp) + 5% Máu tối đa. */
    static float calcDmg(int id) {
        Unit o = Groups.unit.getByID(id);
        if (o == null) return DMG * ATK_PCT;
        return (DMG + NewModePvp.skillFlatAtk(o)) * ATK_PCT * Math.max(0.1f, o.damageMultiplier) + o.maxHealth * MAX_HP_PCT;
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
        f.ownerPlayerId = p.length >= 6 ? Integer.parseInt(p[5]) : -1;
        f.t = -WINDUP;
        f.dmg = calcDmg(f.uid);
        spins.put(f.uid, new Spin(f.ang));
        fans.add(f);

        if (!Vars.headless) {
            try {
                SfxCompat.at("lasercharge", f.x, f.y, 1.3f, 0.9f);
                SfxCompat.at("spark", f.x, f.y, 1.15f, 0.85f);
            } catch (Throwable ignored) {}
        }
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
        final Unit owner = Groups.unit.getByID(f.uid);
        Units.nearbyEnemies(team, f.x - reach, f.y - reach, reach * 2, reach * 2, e -> {
            if (e.dead || f.hit.contains("u" + e.id) || !inFan(f, e.x, e.y)) return;
            f.hit.add("u" + e.id);
            UnitSkillInfo.markLastDamageSkill(e.id);
            NewModePvp.skillDamage(owner, e, f.dmg, f.x, f.y);
            try { if (markStatus != null) e.apply(markStatus, MARK_T); } catch (Throwable err) {}
            try {
                Call.effect(Fx.hitLaserBlast, e.x, e.y, f.ang, COL2);
                Call.effect(Fx.sparkShoot, e.x, e.y, f.ang, GOLD);
                if (!Vars.headless) SfxCompat.at("hitLaser", e.x, e.y, 1.2f, 0.85f);
            } catch (Throwable err) {}
        });
        Units.nearbyBuildings(f.x, f.y, reach, b -> {
            if (b.team == team || f.hit.contains("b" + b.pos()) || !inFan(f, b.x, b.y)) return;
            f.hit.add("b" + b.pos());
            NewModePvp.skillDamage(owner, b, f.dmg);
            try { Call.effect(Fx.hitLaserBlast, b.x, b.y, f.ang, COL2); } catch (Throwable err) {}
        });
    }

    static float rx(float s) { return Mathf.range(RAD * s); }

    static void advanceSkillPhase(Unit u) {
        if (!isArch(u) || u.dead) return;
        if (phase == 0) {
            if (Time.time >= cdEnd) phase = 1;
        } else if (phase == 1) {
            if (!Vars.mobile) {
                aimAng = Angles.angle(u.x, u.y, Vars.player.mouseX, Vars.player.mouseY);
                phase = 2;
            } else {
                phase = 0;
            }
        } else if (phase == 2 && Vars.mobile) {
            phase = 3;
        } else if (phase == firePhase()) {
            cdEnd = Time.time + CD_T;
            if (Vars.net.client()) {
                NexNet.send(REQ, String.valueOf(aimAng));
            } else {
                int pid = Vars.player != null ? Vars.player.id : -1;
                cdEndSrv.put(pid, Time.time + CD_T);
                NexNet.broadcast(FAN, u.id + "," + u.team.id + "," + (Math.round(aimAng * 10) / 10f) + "," + u.x + "," + u.y + "," + pid);
            }
            phase = 0;
        }
    }

    static void update() {
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!fans.isEmpty()) { fans.clear(); spins.clear(); }
            return;
        }
        tryNet();
        syncType();
        if (Vars.state.isPaused()) return;

        // Hỗ trợ nhấn phím tắt PC để điều khiển các bước dùng skill của Archueng
        if (!Vars.headless && !Vars.mobile && Vars.player != null && isActive() && Core.scene != null && !Core.scene.hasField()) {
            Unit pu = Vars.player.unit();
            if (isArch(pu) && !pu.dead && Core.input.keyTap(getSkillKey())) {
                advanceSkillPhase(pu);
            }
        }

        for (int i = fans.size() - 1; i >= 0; i--) {
            Fan f = fans.get(i);
            float prevT = f.t;
            f.t += Time.delta;
            if (f.t < 0) {
                Unit u = Groups.unit.getByID(f.uid);
                if (u != null) { f.x = u.x; f.y = u.y; }
                continue;
            }
            // Vừa kết thúc tụ lực -> phóng kiếm khí
            if (prevT < 0 && f.t >= 0 && !Vars.headless) {
                try {
                    SfxCompat.at("shootBig", f.x, f.y, 1.35f, 0.95f);
                    SfxCompat.at("plasmaflame", f.x, f.y, 1.25f, 0.85f);
                } catch (Throwable ignored) {}
            }

            float fl = f.t - (SLOW_T + FAST_T), prog = fl > 0 ? Mathf.clamp(fl / FADE_T) : 0f;
            float spd = f.t < SLOW_T ? SLOW_SPD : FAST_SPD * (1 - prog) * (1 - prog);
            f.x += Angles.trnsx(f.ang, spd * Time.delta);
            f.y += Angles.trnsy(f.ang, spd * Time.delta);
            try {
                if (prevT < 0) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL); Effect.shake(3.5f, 8f, f.x, f.y); }
                if (f.t > SLOW_T && !f.landed) {
                    f.landed = true;
                    f.lx = f.x;
                    f.ly = f.y;
                    if (!Vars.headless) {
                        Sounds.explosion.at(f.x, f.y, 1.45f, 0.75f);
                    }
                }
                if (f.t > SLOW_T && prog == 0) {
                    if (Mathf.chance(0.75)) Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, rx(0.8f)), f.y + Angles.trnsy(f.ang + 90, rx(0.8f)), f.ang, Mathf.chance(0.35) ? GOLD : COL2);
                } else if (Mathf.chance(0.28)) {
                    Fx.sparkShoot.at(f.x + Angles.trnsx(f.ang + 90, rx(0.7f)), f.y + Angles.trnsy(f.ang + 90, rx(0.7f)), f.ang, GOLD);
                }
                if (prevT <= SLOW_T && f.t > SLOW_T) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL2); Effect.shake(5.5f, 10f, f.x, f.y); }
            } catch (Throwable err) {}
            if (prog > 0 && Mathf.chance(0.5)) {
                try { Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, rx(0.8f)), f.y + Angles.trnsy(f.ang + 90, rx(0.8f)), f.ang, Mathf.chance(0.3) ? GOLD : COL2); } catch (Throwable err) {}
            }
            if (!Vars.net.client() && prog == 0) hitFan(f);

            // Nhát chém kết thúc: nếu chém hụt hoàn toàn -> giảm 65% thời gian hồi chiêu
            if (f.t >= SLOW_T + FAST_T + FADE_T) {
                if (f.hit.isEmpty()) {
                    float refund = CD_T * MISS_CD_REFUND;
                    if (f.ownerPlayerId >= 0 && cdEndSrv.containsKey(f.ownerPlayerId)) {
                        cdEndSrv.put(f.ownerPlayerId, Math.max(Time.time, cdEndSrv.get(f.ownerPlayerId) - refund));
                    }
                    Unit ownerUnit = Groups.unit.getByID(f.uid);
                    if (!Vars.headless && Vars.player != null && ownerUnit != null && Vars.player.unit() == ownerUnit) {
                        cdEnd = Math.max(Time.time, cdEnd - refund);
                        try {
                            Call.label("[#bff4ff]CHÉM HỤT: -65% HỒI CHIÊU[]", 1.2f, ownerUnit.x, ownerUnit.y + 16f);
                            SfxCompat.at("plasmadrop", ownerUnit.x, ownerUnit.y, 1.35f, 0.9f);
                        } catch (Throwable ignored) {}
                    }
                }
                fans.remove(i);
            }
        }

        for (Iterator<Map.Entry<Integer, Spin>> it = spins.entrySet().iterator(); it.hasNext();) {
            Spin s = it.next().getValue();
            s.t += Time.delta;
            if (s.t > WINDUP) it.remove();
        }

        if (!Vars.headless && Vars.player != null) {
            Unit pu = Vars.player.unit();
            if (phase != 0 && (!isArch(pu) || pu.dead)) phase = 0;
            boolean touched = Core.input.isTouched();
            if (touched && !wasTouchedPrev && !uiTapConsumed && isArch(pu) && !pu.dead) {
                if (Vars.mobile) {
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

    // ---------------- Vẽ Đồ Họa ----------------
    static void crescent(float x, float y, float ang, float rad, float half, float thick, Color cO, float aO, Color cI, float aI) {
        Tmp.c1.set(cO); Tmp.c1.a = aO; Tmp.c2.set(cI); Tmp.c2.a = aI;
        float bo = Tmp.c1.toFloatBits(), bi = Tmp.c2.toFloatBits();
        int N = 14;
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

        if (Vars.player != null && phase != 0 && !(Vars.mobile && phase == 1)) {
            Unit pu = Vars.player.unit();
            if (isArch(pu) && !pu.dead) {
                boolean locked = phase == firePhase();
                float ang = phase >= 2 ? aimAng : Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                float ex = pu.x + Angles.trnsx(ang, 165), ey = pu.y + Angles.trnsy(ang, 165);
                Draw.z(Layer.flyingUnit + 2);
                Drawf.dashLine(locked ? FIRE : COL2, pu.x, pu.y, ex, ey);
                Draw.blend(Blending.additive);
                Draw.color(locked ? FIRE : COL2, 0.45f);
                Lines.stroke(1.5f);
                Lines.arc(pu.x, pu.y, RAD, HALF * 2f / 360f, ang - HALF);
                Draw.blend();
                Draw.reset();
            }
        }

        for (Map.Entry<Integer, Spin> en : spins.entrySet()) {
            Unit u = Groups.unit.getByID(en.getKey());
            Spin s = en.getValue();
            if (u == null || u.dead) continue;
            float fr = Mathf.clamp(s.t / WINDUP);
            Draw.z(Layer.flyingUnit + 1);
            float sw = Interp.pow2Out.apply(fr), a0 = s.a - HALF, cur = a0 + HALF * 2 * sw, L = 66f;
            float rr = 26 + 18 * fr;

            Draw.z(Layer.groundUnit - 1);
            Draw.blend(Blending.additive);
            Draw.color(COL, 0.5f * fr);
            Lines.stroke(2.4f);
            Lines.circle(u.x, u.y, rr * 1.15f);
            for (int q = 0; q < 16; q++) {
                float ph = ((s.t * 0.05f) + q * 0.0625f) % 1f, qa = q * 22.5f + s.t * 2.2f, r0 = rr * 1.95f * (1 - ph) + 6, r1 = r0 + 8 + 10 * ph;
                Draw.color(q % 3 == 0 ? FIRE2 : (q % 3 == 1 ? FIRE : GOLD), ph * (0.5f + 0.5f * fr));
                Lines.stroke(0.9f + ph * 1.5f);
                Lines.line(u.x + Angles.trnsx(qa, r0), u.y + Angles.trnsy(qa, r0), u.x + Angles.trnsx(qa, r1), u.y + Angles.trnsy(qa, r1));
            }
            Draw.blend(); Draw.reset();

            Draw.z(Layer.flyingUnit + 1);
            int segs = 16;
            float step = (cur - a0) / segs;
            for (int q = 0; q < segs; q++) {
                float t1 = a0 + step * q, t2 = t1 + step, al = (float) Math.pow((q + 1f) / segs, 1.5) * 0.9f * (1 - fr * 0.45f);
                float cIn = Tmp.c1.set(0.1f, 0.4f, 0.95f, 0f).toFloatBits(), cOut = Tmp.c2.set(0.55f, 0.95f, 1f, al).toFloatBits(), cMid = Tmp.c3.set(0.15f, 0.55f, 1f, al * 0.65f).toFloatBits();
                float r0 = 10, r1 = L * 0.55f, r2 = L * 1.08f;
                Fill.quad(u.x + Angles.trnsx(t1, r0), u.y + Angles.trnsy(t1, r0), cIn, u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid,
                          u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid, u.x + Angles.trnsx(t2, r0), u.y + Angles.trnsy(t2, r0), cIn);
                Fill.quad(u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid, u.x + Angles.trnsx(t1, r2), u.y + Angles.trnsy(t1, r2), cOut,
                          u.x + Angles.trnsx(t2, r2), u.y + Angles.trnsy(t2, r2), cOut, u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid);
            }
            Draw.color();
            float bx = u.x + Angles.trnsx(cur, L), by = u.y + Angles.trnsy(cur, L), ba = 1 - fr * 0.35f;
            Draw.blend(Blending.additive);
            Draw.color(COL, 0.4f * ba); Lines.stroke(7.5f); Lines.line(u.x + Angles.trnsx(cur, 12), u.y + Angles.trnsy(cur, 12), bx, by);
            Draw.color(Color.white, 0.98f * ba); Lines.stroke(2.4f); Lines.line(u.x + Angles.trnsx(cur, 12), u.y + Angles.trnsy(cur, 12), bx, by);
            for (int g = 0; g < 4; g++) { Draw.color(g % 2 == 1 ? GOLD : COL2, 0.92f * ba); Drawf.tri(bx, by, 5.5f - (g % 2) * 2, g % 2 == 1 ? 12 : 26, cur + g * 90); }
            Draw.color(Color.white, ba); Fill.circle(bx, by, 3.5f);
            Draw.blend(); Draw.reset();

            if (wreg.found() && fr < 1) {
                Draw.blend(Blending.additive);
                for (int g = 4; g >= 1; g--) {
                    float ca = cur - HALF * 0.15f * g * (1 - fr * 0.5f), gl = wreg.height / 4f, go = Math.max(10f, gl * 0.5f);
                    Draw.color(COL2, 0.25f * (5 - g) / 4f);
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
            float sc = RAD * 2 * (fast ? 1 + 0.32f * pg : 1 + Mathf.sin(f.t, 6, 0.04f)) * (fast ? 1.18f : 1f);
            Draw.blend(Blending.additive);
            if (slash.found()) {
                Draw.color(COL, 0.34f * fade); Draw.rect(slash, f.x, f.y, sc * 1.55f, sc * 1.55f, f.ang + Mathf.sin(f.t, 9, 1.5f));
                Draw.color(GOLD, 0.25f * fade); Draw.rect(slash, f.x, f.y, sc * 1.25f, sc * 1.25f, f.ang - Mathf.sin(f.t, 11, 1.2f));
            }
            if (fast) for (int g = 1; g <= 6; g++) {
                float d = g * 13 * (1 - pg * 0.5f);
                crescent(f.x - Angles.trnsx(f.ang, d), f.y - Angles.trnsy(f.ang, d), f.ang, RAD * (1 - 0.045f * g), HALF * (1 + 0.04f * g), 0.38f, COL2, 0.58f * fade / g, COL, 0f);
            }
            crescent(f.x, f.y, f.ang, RAD, HALF, 0.5f, Color.white, fade, COL, 0.16f * fade);
            crescent(f.x, f.y, f.ang, RAD * 0.9f, HALF * 0.85f, 0.2f, GOLD, 0.6f * fade, GOLD, 0f);
            Draw.color(COL, 0.38f * fade); Lines.stroke(6.5f); Lines.arc(f.x, f.y, RAD, HALF * 2 / 360f, f.ang - HALF);
            Draw.color(Color.white, fade); Lines.stroke(2.5f); Lines.arc(f.x, f.y, RAD, HALF * 2 / 360f, f.ang - HALF);
            for (int sd = -1; sd <= 1; sd += 2) {
                float hx = f.x + Angles.trnsx(f.ang + HALF * sd, RAD), hy = f.y + Angles.trnsy(f.ang + HALF * sd, RAD);
                Draw.color(COL2, 0.85f * fade); Fill.circle(hx, hy, 3.8f + Mathf.sin(f.t, 4, 1)); Draw.color(Color.white, fade); Fill.circle(hx, hy, 2f);
            }
            if (fast) for (int q = 0; q < 9; q++) {
                float la = f.ang + (q / 8f - 0.5f) * HALF * 1.7f, st = RAD * (0.55f + 0.4f * ((q * 37) % 10) / 10f), len = (24 + (q % 3) * 14) * fade;
                Draw.color(q % 2 == 1 ? COL2 : GOLD, 0.65f * fade); Lines.stroke(1.2f);
                Lines.line(f.x + Angles.trnsx(la, st), f.y + Angles.trnsy(la, st), f.x + Angles.trnsx(la, st) - Angles.trnsx(f.ang, len), f.y + Angles.trnsy(la, st) - Angles.trnsy(f.ang, len));
            }
            for (int q = 0; q < 10; q++) {
                float off = (q / 9f - 0.5f) * HALF * 2.1f + Mathf.sin(f.t * 0.08f + q * 2, 1, 6);
                float rd = RAD * (0.95f + 0.25f * Mathf.sin(f.t * 0.12f + q, 1, 1)) + (q % 2) * 8;
                Draw.color(q % 2 == 1 ? GOLD : COL2, (0.55f + 0.45f * Mathf.sin(f.t * 0.2f + q, 1, 1)) * fade);
                Fill.square(f.x + Angles.trnsx(f.ang + off, rd), f.y + Angles.trnsy(f.ang + off, rd), 1.9f + (q % 3) * 0.6f, f.t * 4 + q * 40);
            }
            if (f.landed && f.t - SLOW_T < 28) {
                float rp = (f.t - SLOW_T) / 28f, ip = Interp.pow3Out.apply(rp);
                Draw.color(COL2, (1 - rp) * 0.95f); Lines.stroke(3.2f * (1 - rp) + 0.5f); Lines.circle(f.lx, f.ly, 12 + 105 * ip);
                Draw.color(GOLD, (1 - rp) * 0.75f); Lines.stroke(1.7f * (1 - rp) + 0.3f); Lines.circle(f.lx, f.ly, 8 + 68 * Interp.pow2Out.apply(rp));
                for (int q = 0; q < 14; q++) {
                    float qa = q * (360f / 14f) + 7, r0 = 16 + 90 * ip;
                    Draw.color(q % 2 == 1 ? Color.white : COL2, (1 - rp) * 0.9f); Lines.stroke(2f * (1 - rp) + 0.3f);
                    Lines.line(f.lx + Angles.trnsx(qa, r0), f.ly + Angles.trnsy(qa, r0), f.lx + Angles.trnsx(qa, r0 + 34 * (1 - rp)), f.ly + Angles.trnsy(qa, r0 + 34 * (1 - rp)));
                }
            }
            Draw.blend(); Draw.reset();
            try { Drawf.light(f.x, f.y, RAD * 1.9f, COL, 0.65f * fade); } catch (Throwable err) {}
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
        lbl.setFontScale(0.85f);
        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0) { lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s"); lbl.setColor(Color.gray); }
            else if (phase == 1) { lbl.setText("CHỌN HƯỚNG"); lbl.setColor(COL2); }
            else if (phase == 2 && Vars.mobile) { lbl.setText("KHÓA"); lbl.setColor(GOLD); }
            else if (phase == firePhase()) { lbl.setText("TUNG"); lbl.setColor(FIRE); }
            else { lbl.setText(Vars.mobile ? "SKILL" : "SKILL [" + getSkillKey().value + "]"); lbl.setColor(COL2); }
        });
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown && Vars.player != null && isArch(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false; offX = x; offY = y; uiTapConsumed = true; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging) {
                    Core.settings.put(KEY + "-x", Math.round(t.x));
                    Core.settings.put(KEY + "-y", Math.round(t.y));
                } else if (!Vars.state.isPaused() && Vars.player != null) {
                    advanceSkillPhase(Vars.player.unit());
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
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) { uiTapConsumed = true; return true; }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) { phase = 0; }
        });
        Vars.ui.hudGroup.addChild(c);
    }

    public static void init() {
        Events.run(Trigger.update, ArchuengPvp::update);
        UnitSkillInfo.registerArchueng();

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