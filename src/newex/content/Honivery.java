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
import arc.math.Rand;
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
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.Predict;
import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.LegsUnit;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Styles;
import mindustry.world.meta.Env;
import newex.NexNet;
import newex.SfxCompat;
import newex.UnitSkillInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Unit Honivery - Cơ giáp 4 chân trang bị súng Gatling quá nhiệt & Kiếm Sao Tinh Quang.
 * Tích hợp kỹ năng Tinh Quang Trảm (đòn đánh kĩ năng), nội tại hấp thụ sức mạnh hạ gục tối đa 999%,
 * tùy chỉnh phím bấm PC trong Cài đặt NewEx -> NewMode PvP và hiệu ứng hình ảnh/âm thanh điện ảnh.
 */
public final class Honivery {
    public static UnitType honivery;

    private static final Color HONIVERY_COLOR = Color.valueOf("D0C2FF");
    private static final Color STAR_CYAN = Color.valueOf("7CE8FF");
    private static final Color STAR_GOLD = Color.valueOf("FFE07D");
    private static final Color VOID_PURPLE = Color.valueOf("6E3AFF");
    private static final Rand fxRand = new Rand();
    private static final Vec2 fxV = new Vec2();

    // ---- Hằng số kỹ năng & mạng ----
    static final String REQ = "newex-honivery-skill";
    static final String CAST = "newex-honivery-cast";
    static final String HUD_KEY = "newex-honivery-btn";
    public static final String KEYBIND_SETTING = "newex-pvp-honivery-skill-key";

    static final float SKILL_CD = 480f;      // 8 giây hồi chiêu
    static final float CHARGE_T = 14f;       // tụ lực rút kiếm sao
    static final float SLASH_T = 26f;        // thời gian sóng kiếm lao tới
    static final float SLASH_RANGE = 155f;   // tầm chém
    static final float SLASH_HALF_ANG = 48f; // góc quét hình quạt
    static final float BASE_ATK = 300f;      // chỉ số tấn công chuẩn của skill
    static final float ATK_RATIO = 0.90f;    // 90% tấn công
    static final float HP_RATIO = 0.10f;     // 10% HP hiện tại
    static final int MAX_PASSIVE_PCT = 999;  // giới hạn nội tại 999%

    static boolean netOk = false, initialized = false;
    static float cdEnd = 0f;
    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    static final HashMap<Integer, Integer> passiveStacksSrv = new HashMap<>();
    static int localPassivePct = 0;

    static final ArrayList<StarSlash> slashes = new ArrayList<>();
    static final HashMap<Integer, StarCharge> charges = new HashMap<>();
    static Table hudBtn = null;

    private Honivery() {}

    static class StarCharge {
        float t, ang;
        StarCharge(float ang) { this.ang = ang; }
    }

    static class StarSlash {
        int uid, team;
        float x, y, ang, t, dmg;
        boolean struck;
        final HashSet<String> hit = new HashSet<>();
    }

    // ================================================================== API Công Khai & Cài Đặt Phím

    public static KeyCode getSkillKey() {
        String name = Core.settings.getString(KEYBIND_SETTING, KeyCode.r.name());
        try {
            return KeyCode.valueOf(name);
        } catch (Throwable ignored) {
            return KeyCode.r;
        }
    }

    public static void setSkillKey(KeyCode key) {
        if (key != null) Core.settings.put(KEYBIND_SETTING, key.name());
    }

    public static int getPassiveBonusPct() {
        return localPassivePct;
    }

    public static boolean isHonivery(Unit u) {
        return u != null && honivery != null && u.type == honivery;
    }

    /** Thêm dòng tùy chỉnh nút bấm skill Honivery cho PC vào bảng cài đặt NewEx -> NewMode PvP. */
    public static void buildKeybindRow(Table table) {
        if (table == null) return;
        final boolean[] listening = {false};
        Table row = new Table(Styles.black6);
        row.margin(8f);
        row.add("[#D0C2FF]Honivery[] - Phím Tinh Quang Trảm (chém 1 đòn, PC):").left().growX().padRight(10f);

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

    // ================================================================== Hiệu Ứng Cơ Bản & Điện Ảnh

    private static Effect hitBulletFx(float circleRad, int lines, float linesRad) {
        final Interp inter = circleRad > 20f ? Interp.pow3Out : Interp.linear;
        final float da = circleRad > 20f ? 0.5f : 0f;
        final float l = Math.max(0f, (circleRad - 10f) / 4f);
        return new Effect(14f + l, e -> {
            Draw.color(Color.white, e.color, e.fin());
            e.scaled(7f + l / 2f, s -> {
                Draw.alpha(1f - s.fin() * da);
                Lines.stroke(0.1f + inter.apply(1f - s.fin()));
                Lines.circle(e.x, e.y, s.fin(inter) * circleRad);
            });
            Lines.stroke(0.3f + inter.apply(1f - e.fin()));
            Angles.randLenVectors(e.id, lines, e.fin(inter) * linesRad, (x, y) -> {
                float ang = Mathf.angle(x, y);
                Lines.lineAngle(e.x + x, e.y + y, ang, e.fout() * 3f * (linesRad / 16f) + 0.2f);
            });
            Drawf.light(e.x, e.y, 20f, e.color, 0.6f * e.fout());
        });
    }

    private static Effect shootFx() {
        return new Effect(20f, e -> {
            Draw.color(e.color, Color.white, e.fin() * 0.5f);
            fxRand.setSeed(e.id);
            for (int i = 0; i < 7; i++) {
                float dr = fxRand.range(20f);
                fxV.trns(e.rotation + dr, (fxRand.random(6f) + 3f) * e.finpow());
                Fill.poly(e.x + fxV.x, e.y + fxV.y, 4, e.fout() * 3f);
            }
        });
    }

    /** Hiệu ứng bùng nổ sao 4 cánh khi chém trúng mục tiêu. */
    private static final Effect starSlashHitFx = new Effect(26f, e -> {
        float fin = e.fin(), fout = e.fout();
        Draw.blend(Blending.additive);
        Draw.color(HONIVERY_COLOR, fout);
        Lines.stroke(2.5f * fout);
        Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 28f);

        for (int i = 0; i < 4; i++) {
            float a = e.rotation + i * 90f + fin * 35f;
            float len = (i % 2 == 0 ? 32f : 18f) * Interp.pow2Out.apply(fin);
            Draw.color(i % 2 == 0 ? Color.white : STAR_CYAN, fout);
            Drawf.tri(e.x, e.y, 4.5f * fout, len, a);
            Drawf.tri(e.x, e.y, 4.5f * fout, len * 0.45f, a + 180f);
        }
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 42f, HONIVERY_COLOR, 0.8f * fout);
    });

    /** Hiệu ứng chữ/hào quang hấp thụ nội tại khi hạ gục mục tiêu. */
    private static final Effect soulAbsorbFx = new Effect(38f, e -> {
        float fin = e.fin(), fout = e.fout();
        Draw.blend(Blending.additive);
        Draw.color(STAR_GOLD, fout);
        Lines.stroke(3f * fout);
        Lines.circle(e.x, e.y, 8f + Interp.pow3Out.apply(fin) * 44f);
        for (int i = 0; i < 6; i++) {
            float a = i * 60f + fin * 140f;
            float r = 36f * (1f - Interp.pow2In.apply(fin));
            Draw.color(i % 2 == 0 ? STAR_GOLD : HONIVERY_COLOR, fout);
            Fill.circle(e.x + Angles.trnsx(a, r), e.y + Angles.trnsy(a, r), 3.2f * fout);
        }
        Draw.blend();
        Draw.reset();
    });

    // ================================================================== Khởi Tạo Unit & Logic Skill

    public static void create() {
        final Effect hitFx = hitBulletFx(5f, 8, 10f);
        final Effect despawnFx = hitBulletFx(12f, 10, 18f);
        final Effect honiveryShootFx = shootFx();

        honivery = new UnitType("honivery") {{
            constructor = LegsUnit::create;

            localizedName = "Honivery";
            description = "Cơ giáp 4 chân tinh nhuệ trang bị súng máy Gatling quá nhiệt và Kiếm Sao Tinh Quang.";
            details = "Kỹ năng Tinh Quang Trảm (Đòn Đánh Kĩ Năng): Gây 90% Tấn công + 10% HP hiện tại. "
                + "Nội tại: Hạ gục mục tiêu tăng vĩnh viễn +1% sát thương skill, cộng thêm +1% mỗi 10.000 HP tối đa của mục tiêu (tối đa 999%).";

            outlineColor = Pal.darkOutline;
            envDisabled = Env.none;
            researchCostMultiplier = 7.5f;

            hitSize = 18f;
            health = 3500f;
            armor = 10f;
            speed = 3.4f / 7.5f;

            legCount = 4;
            legLength = 10f;
            legLengthScl = 1.2f;
            legBaseOffset = 0.1f;
            legForwardScl = 0.3f;
            legExtension = 3f;
            legStraightness = 0.2f;

            weapons.addAll(new HoniveryGatlingWeapon("newex-honivery-weapon") {{
                x = 49 / 4f;
                y = -6 / 4f;
                top = false;
                bx = -0.5f / 4f;
                by = 29 / 4f;
                width = 9 / 4f;
                inclineRotation = -37f;
                mirror = false;
                rotate = true;
                rotationLimit = 16;
                rotateSpeed = 1f;
                shootCone = 80;
                recoils = 4;
                reload = 3;
                shootWarmupSpeed = 0.025f;
                shootY = 48 / 4f;
                shootX = -3.5f / 4f;
                shootSoundVolume = 1f;
                shootSound = Sounds.shootReign;
                soundPitchMax = 1.1f;
                soundPitchMin = 0.85f;
                shoot.shots = 1;
                overheatSpeed = 0.02f;
                overheatDurationTime = 5 * 60f;
                targetInterval = 0;
                targetSwitchInterval = 0;
                inversion = true;
                inaccuracy = 2;
                bullet = new BasicBulletType(12f, 25f) {{
                    hitEffect = hitFx;
                    despawnEffect = despawnFx;
                    shootEffect = honiveryShootFx;
                    smokeEffect = Fx.none;
                    width = 7f;
                    height = 7f;
                    shrinkX = 0.2f;
                    shrinkY = 0f;
                    backColor = trailColor = hitColor = HONIVERY_COLOR;
                    trailLength = 5;
                    trailWidth = 1.3f;
                    lifetime = 18.66f;
                    armorMultiplier = 0.5f;
                }};
            }}, new HoniverySwordDrawer("newex-honivery-sword") {{
                x = -49 / 4f;
                y = -6 / 4f;
            }});
        }};

        init();
        UnitSkillInfo.registerHonivery();
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.run(Trigger.update, Honivery::updateSkill);
        if (!Vars.headless) {
            Events.run(Trigger.draw, Honivery::drawSkill);
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    buildHudButton();
                    registerSettings();
                } catch (Throwable err) {
                    Log.err("[Honivery] UI/Settings init error", err);
                }
            });
            Events.on(WorldLoadEvent.class, e -> resetState());
        } else {
            Events.on(WorldLoadEvent.class, e -> resetState());
        }
    }

    private static void resetState() {
        cdEnd = 0f;
        cdEndSrv.clear();
        passiveStacksSrv.clear();
        localPassivePct = 0;
        slashes.clear();
        charges.clear();
    }

    private static void registerSettings() {
        if (Vars.ui == null || Vars.ui.settings == null) return;
        Vars.ui.settings.addCategory("Phím kĩ năng", t -> {
            t.add("[accent]=== PHÍM KĨ NĂNG (PC) ===[]").left().padBottom(10f).row();
            buildKeybindRow(t);
            HoniveryPvp.buildKeybindRow(t);
            ArchuengPvp.buildKeybindRow(t);
        });
    }

    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ, Honivery::onSkillReq, false);
        NexNet.onBroadcast(CAST, Honivery::onSkillCast);
    }

    /** Tính sát thương đòn đánh kĩ năng (Tinh Quang Trảm) của Honivery: (90% ATK + 10% HP) * (1 + nội tại%). */
    static float calcBasicSlashDamage(Unit u, int passivePct) {
        if (u == null) return BASE_ATK * ATK_RATIO;
        float atk = BASE_ATK * Math.max(0.1f, u.damageMultiplier);
        float raw = atk * ATK_RATIO + Math.max(0f, u.health) * HP_RATIO;
        float mult = 1f + Math.min(MAX_PASSIVE_PCT, Math.max(0, passivePct)) / 100f;
        return raw * mult;
    }

    static void onSkillReq(Player player, String payload) {
        Unit u = player.unit();
        if (!isHonivery(u) || u.dead) return;
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + SKILL_CD);

        float ang;
        try {
            ang = Float.parseFloat(String.valueOf(payload));
        } catch (Throwable e) {
            ang = u.rotation;
        }
        if (Float.isNaN(ang)) ang = u.rotation;

        int stack = passiveStacksSrv.getOrDefault(u.id, 0);
        NexNet.broadcast(CAST, u.id + "," + u.team.id + "," + (Math.round(ang * 10f) / 10f) + "," + u.x + "," + u.y + "," + stack);
    }

    static void onSkillCast(String payload) {
        String[] p = String.valueOf(payload).split(",");
        if (p.length < 5) return;
        StarSlash s = new StarSlash();
        s.uid = Integer.parseInt(p[0]);
        s.team = Integer.parseInt(p[1]);
        s.ang = Float.parseFloat(p[2]);
        s.x = Float.parseFloat(p[3]);
        s.y = Float.parseFloat(p[4]);
        int stack = p.length >= 6 ? Integer.parseInt(p[5]) : 0;

        Unit caster = Groups.unit.getByID(s.uid);
        s.t = -CHARGE_T;
        s.dmg = calcBasicSlashDamage(caster, stack);

        charges.put(s.uid, new StarCharge(s.ang));
        slashes.add(s);

        // Âm thanh tụ năng lượng & tuốt kiếm sao
        if (!Vars.headless) {
            try {
                SfxCompat.at("spark", s.x, s.y, 1.35f, 0.95f);
                SfxCompat.at("lasercharge2", s.x, s.y, 1.45f, 0.75f);
            } catch (Throwable ignored) {}
        }
    }

    static void triggerCastFromClient(Unit u, float ang) {
        if (!isHonivery(u) || u.dead || Time.time < cdEnd) return;
        cdEnd = Time.time + SKILL_CD;
        if (Vars.net.client()) {
            NexNet.send(REQ, String.valueOf(ang));
        } else {
            int stack = passiveStacksSrv.getOrDefault(u.id, localPassivePct);
            NexNet.broadcast(CAST, u.id + "," + u.team.id + "," + (Math.round(ang * 10f) / 10f) + "," + u.x + "," + u.y + "," + stack);
        }
    }

    static void applySlashHits(StarSlash s, float reach) {
        Team team = Team.get(s.team);
        Unit caster = Groups.unit.getByID(s.uid);

        Units.nearbyEnemies(team, s.x - reach, s.y - reach, reach * 2f, reach * 2f, e -> {
            if (e.dead || s.hit.contains("u" + e.id)) return;
            float dst = Mathf.dst(s.x, s.y, e.x, e.y);
            if (dst > reach + e.hitSize / 2f) return;
            float diff = Math.abs(Angles.angleDist(Angles.angle(s.x, s.y, e.x, e.y), s.ang));
            if (diff > SLASH_HALF_ANG && dst > 22f) return;

            s.hit.add("u" + e.id);
            float targetMaxHp = e.maxHealth;

            // Đánh dấu đây là ĐÒN ĐÁNH KĨ NĂNG (giống Archueng)
            UnitSkillInfo.markLastDamageSkill(e.id);
            e.damage(s.dmg);

            try {
                Call.effect(starSlashHitFx, e.x, e.y, s.ang, HONIVERY_COLOR);
                if (!Vars.headless) SfxCompat.at("laserblast", e.x, e.y, Mathf.random(1.1f, 1.35f), 0.75f);
            } catch (Throwable ignored) {}

            // Kiểm tra hạ gục mục tiêu để kích hoạt nội tại
            if (e.dead || e.health <= 0f) {
                int extraFromHp = targetMaxHp > 10000f ? (int) (targetMaxHp / 10000f) : 0;
                int gain = 1 + extraFromHp;

                int cur = passiveStacksSrv.getOrDefault(s.uid, 0);
                int next = Math.min(MAX_PASSIVE_PCT, cur + gain);
                passiveStacksSrv.put(s.uid, next);

                if (caster != null && Vars.player != null && Vars.player.unit() == caster) {
                    localPassivePct = next;
                }

                try {
                    Call.effect(soulAbsorbFx, e.x, e.y, 0f, STAR_GOLD);
                    if (!Vars.headless) {
                        Call.label("[#FFE07D]+ " + gain + "% ATK SKILL[] [lightgray](" + next + "%/999%)[]", 1.3f, e.x, e.y + 12f);
                        SfxCompat.at("message", e.x, e.y, 1.4f, 0.9f);
                    }
                } catch (Throwable ignored) {}
            }
        });

        Units.nearbyBuildings(s.x, s.y, reach, b -> {
            if (b.team == team || s.hit.contains("b" + b.pos())) return;
            float dst = Mathf.dst(s.x, s.y, b.x, b.y);
            if (dst > reach) return;
            float diff = Math.abs(Angles.angleDist(Angles.angle(s.x, s.y, b.x, b.y), s.ang));
            if (diff > SLASH_HALF_ANG && dst > 22f) return;

            s.hit.add("b" + b.pos());
            b.damage(s.dmg);
            try {
                Call.effect(starSlashHitFx, b.x, b.y, s.ang, STAR_CYAN);
            } catch (Throwable ignored) {}
        });
    }

    static void updateSkill() {
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!slashes.isEmpty()) slashes.clear();
            if (!charges.isEmpty()) charges.clear();
            return;
        }
        tryNet();
        if (Vars.state.isPaused()) return;

        // Kích hoạt bằng phím tùy chỉnh trên PC
        if (!Vars.headless && !Vars.mobile && Vars.player != null && Core.scene != null && !Core.scene.hasField()) {
            Unit pu = Vars.player.unit();
            if (isHonivery(pu) && !pu.dead && Core.input.keyTap(getSkillKey())) {
                float aim = Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                triggerCastFromClient(pu, aim);
            }
        }

        for (int i = slashes.size() - 1; i >= 0; i--) {
            StarSlash s = slashes.get(i);
            s.t += Time.delta;
            Unit caster = Groups.unit.getByID(s.uid);

            if (s.t < 0f) {
                if (caster != null && !caster.dead) {
                    s.x = caster.x;
                    s.y = caster.y;
                }
                continue;
            }

            if (!s.struck) {
                s.struck = true;
                if (!Vars.headless) {
                    try {
                        Effect.shake(5.5f, 12f, s.x, s.y);
                        Sounds.shootSpectre.at(s.x, s.y, 1.25f, 1.0f);
                        Sounds.explosionQuad.at(s.x, s.y, 1.4f, 0.7f);
                    } catch (Throwable ignored) {}
                }
            }

            float prog = Mathf.clamp(s.t / SLASH_T);
            float reach = 28f + (SLASH_RANGE - 28f) * Interp.pow3Out.apply(prog);

            if (!Vars.net.client()) {
                applySlashHits(s, reach);
            }

            if (s.t >= SLASH_T) {
                slashes.remove(i);
            }
        }

        for (Iterator<Map.Entry<Integer, StarCharge>> it = charges.entrySet().iterator(); it.hasNext();) {
            StarCharge c = it.next().getValue();
            c.t += Time.delta;
            if (c.t > CHARGE_T) it.remove();
        }
    }

    // ================================================================== Vẽ Đồ Họa Điện Ảnh Cho Skill

    static void drawSkill() {
        if (honivery == null) return;

        // 1. Vẽ pha tụ lực sao 4 cánh & dư ảnh kiếm
        for (Map.Entry<Integer, StarCharge> en : charges.entrySet()) {
            Unit u = Groups.unit.getByID(en.getKey());
            StarCharge c = en.getValue();
            if (u == null || u.dead) continue;

            float fr = Mathf.clamp(c.t / CHARGE_T);
            float inv = 1f - fr;
            Draw.z(Layer.flyingUnit + 1.5f);
            Draw.blend(Blending.additive);

            // Vòng ấn sao xoay dưới chân unit
            float ringR = 14f + 26f * inv;
            Draw.color(VOID_PURPLE, 0.7f * fr);
            Lines.stroke(2.2f);
            Lines.circle(u.x, u.y, ringR);

            for (int k = 0; k < 4; k++) {
                float a = c.ang + k * 90f + fr * 180f;
                float len = (k == 0 ? 34f : 18f) * (0.4f + 0.8f * fr);
                Draw.color(k == 0 ? Color.white : HONIVERY_COLOR, 0.85f * fr);
                Drawf.tri(u.x, u.y, 3.8f * fr, len, a);
            }

            // Bóng kiếm sao phóng đại
            for (Weapon w : honivery.weapons) {
                if (w instanceof HoniverySwordDrawer) {
                    ((HoniverySwordDrawer) w).drawGhost(u, 1f + 1.4f * fr, STAR_CYAN, 0.45f * fr);
                }
            }

            Draw.blend();
            Draw.reset();
            Drawf.light(u.x, u.y, 55f * fr, HONIVERY_COLOR, 0.8f * fr);
        }

        // 2. Vẽ sóng kiếm Tinh Quang Trảm 3 tầng
        for (StarSlash s : slashes) {
            if (s.t < 0f) continue;
            float pg = Mathf.clamp(s.t / SLASH_T);
            float ease = Interp.pow3Out.apply(pg);
            float fade = 1f - Interp.pow2In.apply(pg);
            float curR = 26f + (SLASH_RANGE - 26f) * ease;

            Draw.z(Layer.bullet + 3f);
            Draw.blend(Blending.additive);

            // Vệt rách không gian chữ X tại tâm xuất chiêu
            for (int d = -1; d <= 1; d += 2) {
                float slashAng = s.ang + d * 34f;
                float len = curR * 0.92f;
                float cx = s.x + Angles.trnsx(s.ang, curR * 0.42f);
                float cy = s.y + Angles.trnsy(s.ang, curR * 0.42f);
                Draw.color(VOID_PURPLE, 0.65f * fade);
                Drawf.tri(cx, cy, 14f * fade, len * 0.55f, slashAng);
                Drawf.tri(cx, cy, 14f * fade, len * 0.55f, slashAng + 180f);
                Draw.color(Color.white, 0.95f * fade);
                Drawf.tri(cx, cy, 4.5f * fade, len * 0.52f, slashAng);
                Drawf.tri(cx, cy, 4.5f * fade, len * 0.52f, slashAng + 180f);
            }

            // Lưỡi liềm kiếm khí chính 3 lớp màu (Tím vũ trụ -> Xanh tinh quang -> Lõi trắng)
            drawStarCrescent(s.x, s.y, s.ang, curR, SLASH_HALF_ANG, 0.46f, VOID_PURPLE, 0.75f * fade);
            drawStarCrescent(s.x, s.y, s.ang, curR * 0.96f, SLASH_HALF_ANG * 0.92f, 0.34f, STAR_CYAN, 0.85f * fade);
            drawStarCrescent(s.x, s.y, s.ang, curR * 0.93f, SLASH_HALF_ANG * 0.85f, 0.18f, Color.white, 0.98f * fade);

            // Các mảnh bụi sao & tia sáng dọc cung chém
            for (int q = 0; q < 10; q++) {
                float a = s.ang - SLASH_HALF_ANG + (SLASH_HALF_ANG * 2f) * (q / 9f);
                float px = s.x + Angles.trnsx(a, curR * (0.85f + 0.18f * Mathf.sin(s.t + q)));
                float py = s.y + Angles.trnsy(a, curR * (0.85f + 0.18f * Mathf.sin(s.t + q)));
                Draw.color(q % 2 == 0 ? STAR_GOLD : Color.white, fade);
                Drawf.tri(px, py, 3.2f * fade, 14f * fade, a);
                Drawf.tri(px, py, 3.2f * fade, 14f * fade, a + 180f);
            }

            Draw.blend();
            Draw.reset();
            Drawf.light(s.x + Angles.trnsx(s.ang, curR * 0.6f), s.y + Angles.trnsy(s.ang, curR * 0.6f), curR * 1.4f, HONIVERY_COLOR, 0.85f * fade);
        }
    }

    private static void drawStarCrescent(float x, float y, float ang, float rad, float half, float thick, Color col, float alpha) {
        Tmp.c1.set(col).a(alpha);
        Tmp.c2.set(col).a(0f);
        float cOut = Tmp.c1.toFloatBits();
        float cIn = Tmp.c2.toFloatBits();
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            float f1 = (float) i / segs;
            float f2 = (float) (i + 1) / segs;
            float a1 = ang - half + half * 2f * f1;
            float a2 = ang - half + half * 2f * f2;
            float r1 = rad * (1f - thick * (float) Math.sin(Math.PI * f1));
            float r2 = rad * (1f - thick * (float) Math.sin(Math.PI * f2));
            Fill.quad(
                x + Angles.trnsx(a1, r1), y + Angles.trnsy(a1, r1), cIn,
                x + Angles.trnsx(a1, rad), y + Angles.trnsy(a1, rad), cOut,
                x + Angles.trnsx(a2, rad), y + Angles.trnsy(a2, rad), cOut,
                x + Angles.trnsx(a2, r2), y + Angles.trnsy(a2, r2), cIn
            );
        }
    }

    // ================================================================== Nút HUD Kéo Thả

    private static void buildHudButton() {
        if (hudBtn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        Table t = new Table(Styles.black6);
        hudBtn = t;
        t.name = "newex-honivery-skill-btn";
        t.margin(6f);
        t.touchable = Touchable.enabled;

        float sz = Vars.mobile ? 56f : 48f;
        if (honivery != null) t.image(honivery.uiIcon).size(sz).row();

        Label lbl = t.add("").get();
        lbl.setFontScale(0.85f);
        Label stackLbl = t.add("").get();
        stackLbl.setFontScale(0.75f);

        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0f) {
                lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s");
                lbl.setColor(Color.gray);
            } else {
                lbl.setText(Vars.mobile ? "CHÉM" : "SKILL [" + getSkillKey().value + "]");
                lbl.setColor(HONIVERY_COLOR);
            }
            stackLbl.setText("+" + localPassivePct + "%");
            stackLbl.setColor(localPassivePct > 0 ? STAR_GOLD : Color.lightGray);
        });

        t.visible(() -> Vars.ui.hudfrag.shown && Vars.player != null && isHonivery(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();

        if (Core.settings.has(HUD_KEY + "-x") && Core.settings.has(HUD_KEY + "-y")) {
            t.setPosition(Core.settings.getInt(HUD_KEY + "-x"), Core.settings.getInt(HUD_KEY + "-y"));
        } else {
            t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20f, Core.graphics.getHeight() / 3f);
        }

        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0f, offY = 0f;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging = false;
                offX = x;
                offY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5f || Math.abs(y - offY) > 5f) dragging = true;
                if (dragging) {
                    float nx = Mathf.clamp(t.x + x - offX, 0f, Core.graphics.getWidth() - t.getWidth());
                    float ny = Mathf.clamp(t.y + y - offY, 0f, Core.graphics.getHeight() - t.getHeight());
                    t.setPosition(nx, ny);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging) {
                    Core.settings.put(HUD_KEY + "-x", Math.round(t.x));
                    Core.settings.put(HUD_KEY + "-y", Math.round(t.y));
                } else if (!Vars.state.isPaused() && Vars.player != null) {
                    Unit u = Vars.player.unit();
                    if (isHonivery(u) && !u.dead) {
                        float aim = Vars.mobile ? u.rotation : Angles.angle(u.x, u.y, Vars.player.mouseX, Vars.player.mouseY);
                        triggerCastFromClient(u, aim);
                    }
                }
                dragging = false;
            }
        });

        Vars.ui.hudGroup.addChild(t);
    }

    // ================================================================== Thanh Kiếm Hình Sao (Trang Trí & Dư Ảnh)

    public static class HoniverySwordDrawer extends Weapon {
        public float armLen = 5f, bladeMul = 3f;
        public float innerRad = 1.7f;
        public float outline = 1f;

        public HoniverySwordDrawer(String name) {
            super(name);
            mirror = false;
            rotate = false;
            controllable = false;
            autoTarget = false;
            alternate = false;
            top = false;
        }

        @Override
        public void update(Unit unit, WeaponMount mount) {
            mount.shoot = false;
            mount.rotate = false;
        }

        @Override
        public void draw(Unit unit, WeaponMount mount) {
            float rot = unit.rotation - 90f;
            float cx = unit.x + Angles.trnsx(rot, x, y);
            float cy = unit.y + Angles.trnsy(rot, x, y);
            float z = Draw.z();
            Draw.z(z + 0.1f);
            drawStar(cx, cy, unit.rotation, 1f, outline, Pal.darkOutline, 1f);
            drawStar(cx, cy, unit.rotation, 1f, 0f, HONIVERY_COLOR, 1f);
            Draw.color(Color.white);
            Fill.circle(cx, cy, innerRad * 0.8f);
            Draw.reset();
            Draw.z(z);
        }

        public void drawGhost(Unit unit, float scale, Color col, float alpha) {
            float rot = unit.rotation - 90f;
            float cx = unit.x + Angles.trnsx(rot, x, y);
            float cy = unit.y + Angles.trnsy(rot, x, y);
            drawStar(cx, cy, unit.rotation, scale, 0f, col, alpha);
            Draw.color(Color.white);
            Draw.alpha(alpha);
            Fill.circle(cx, cy, innerRad * 0.8f * scale);
            Draw.reset();
        }

        private void drawStar(float cx, float cy, float rot, float scale, float grow, Color col, float alpha) {
            Draw.color(col);
            Draw.alpha(alpha);
            for (int i = 0; i < 4; i++) {
                float a = rot + i * 90f;
                float len = (i == 0 ? armLen * bladeMul : armLen) * scale + grow;
                float ir = innerRad * scale + grow;
                float lx = cx + Angles.trnsx(a - 45f, ir * 1.4142f), ly = cy + Angles.trnsy(a - 45f, ir * 1.4142f);
                float rx = cx + Angles.trnsx(a + 45f, ir * 1.4142f), ry = cy + Angles.trnsy(a + 45f, ir * 1.4142f);
                float tx = cx + Angles.trnsx(a, len), ty = cy + Angles.trnsy(a, len);
                Fill.quad(cx, cy, lx, ly, tx, ty, rx, ry);
            }
        }
    }

    // ================================================================== Súng Gatling Quá Nhiệt

    public static class HoniveryGatlingWeapon extends Weapon {
        public float recoilTravel = 2f;
        public float baseAngle = 0f;
        public float shootAngle = 180f;
        public float inclineRotation = 0f;
        public float bx, by, width;
        public float keepWarmup = 45f;
        public float overheatSpeed = 0.01f, overheatDurationTime = 3 * 60f;
        public float lifeRnd = 0f;
        public float extraLife = 0f;
        public float minShootVelocity = 0f;
        public float maxShootVelocity = -1f;
        public boolean inversion = false;
        public Color overheatColor = Pal.turretHeat;
        public TextureRegion barrel, barrelHeat;

        public HoniveryGatlingWeapon(String name) {
            super(name);
            shootWarmupSpeed = 0.03f;
            mountType = HoniveryGatlingMount::new;
        }

        @Override
        public void load() {
            super.load();
            barrel = Core.atlas.find(name + "-barrel");
            barrelHeat = Core.atlas.find(name + "-barrel-heat");
        }

        @Override
        public void flip() {
            super.flip();
            inclineRotation *= -1;
            bx *= -1;
            shootAngle = Mathf.mod(180 - shootAngle, 360f);
            inversion = !inversion;
        }

        @Override
        protected void bullet(Unit unit, WeaponMount m, float xOffset, float yOffset, float angleOffset, Mover mover) {
            if (!unit.isAdded()) return;
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            mount.charging = false;
            float xSpread = Mathf.range(xRand),
                ySpread = Mathf.range(yRand),
                weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
                mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y),
                bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX + xOffset + xSpread, this.shootY + yOffset + ySpread),
                bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX + xOffset + xSpread, this.shootY + yOffset + ySpread),
                shootAng = bulletRotation(unit, mount, bulletX, bulletY) + angleOffset,
                baseLife = (1f - lifeRnd) + Mathf.random(lifeRnd) + extraLife,
                lifeScl = bullet.scaleLife ? baseLife * Mathf.clamp(Mathf.dst(bulletX, bulletY, mount.aimX, mount.aimY) / bullet.range) : baseLife,
                angle = shootAng + Mathf.range(inaccuracy + bullet.inaccuracy);

            mount.bullet = bullet.create(unit, unit.team, bulletX, bulletY, angle, -1f,
                (1f - velocityRnd) + Mathf.random(velocityRnd) + extraVelocity, lifeScl, null, mover, mount.aimX, mount.aimY);
            handleBullet(unit, mount, mount.bullet);

            if (!continuous) shootSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);
            else initialShootSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);

            if (mount.allowShootEffects) {
                ejectEffect.at(mountX, mountY, angle * Mathf.sign(this.x));
                bullet.shootEffect.at(bulletX, bulletY, angle, bullet.hitColor, unit);
                bullet.smokeEffect.at(bulletX, bulletY, angle, bullet.hitColor, unit);
            }

            unit.vel.add(Tmp.v1.trns(shootAng + 180f, bullet.recoil));
            Effect.shake(shake, shake, bulletX, bulletY);
            mount.recoil = 1f;
            if (recoils > 0) {
                if (mount.recoils == null) mount.recoils = new float[recoils];
                float step = 360f / recoils;
                float delta = Mathf.mod(this.shootAngle - mount.angle, 360f);
                int index = Mathf.round(delta / step) % recoils;
                mount.recoils[index] = 1f;
            }
            mount.heat = 1f;
            mount.overheat += overheatSpeed;
        }

        @Override
        public void update(Unit unit, WeaponMount m) {
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            if (mount.overheat > 1f) mount.isOverheat = true;
            if (mount.overheat > 0) {
                mount.overheat -= Time.delta * (1 / overheatDurationTime) * (1 - mount.warmup);
                if (mount.overheat <= 0) {
                    mount.overheat = 0;
                    mount.isOverheat = false;
                }
            }

            boolean can = unit.canShoot() && !mount.isOverheat;

            float lastReload = mount.reload;
            mount.angle = (mount.angle + (inversion ? -1 : 1) * (360f / reload) * Time.delta * unit.reloadMultiplier * mount.warmup / recoils * shoot.shots) % 360f;
            mount.reload = Math.max(mount.reload - Time.delta * unit.reloadMultiplier * mount.warmup, 0);
            mount.recoil = Mathf.approachDelta(mount.recoil, 0, unit.reloadMultiplier / recoilTime);
            if (recoils > 0) {
                if (mount.recoils == null) mount.recoils = new float[recoils];
                for (int i = 0; i < recoils; i++) {
                    mount.recoils[i] = Mathf.approachDelta(mount.recoils[i], 0, unit.reloadMultiplier / recoilTime);
                }
            }
            mount.smoothReload = Mathf.lerpDelta(mount.smoothReload, mount.reload / reload, smoothReloadSpeed);
            mount.charge = mount.charging && shoot.firstShotDelay > 0 ? Mathf.approachDelta(mount.charge, 1, 1 / shoot.firstShotDelay) : 0;

            float warmupTarget = (can && mount.shoot && !mount.isOverheat) || (continuous && mount.bullet != null) || mount.charging ? 1f : 0f;
            mount.keepWarmup = Mathf.approachDelta(mount.keepWarmup, warmupTarget, 1 / keepWarmup);
            warmupTarget = mount.keepWarmup > 0.01f ? 1f : warmupTarget;
            if (linearWarmup) mount.warmup = Mathf.approachDelta(mount.warmup, warmupTarget, shootWarmupSpeed);
            else mount.warmup = Mathf.lerpDelta(mount.warmup, warmupTarget, shootWarmupSpeed);

            float mountX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                mountY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);

            if (!controllable && autoTarget) {
                if ((mount.retarget -= Time.delta) <= 0f) {
                    mount.target = findTarget(unit, mountX, mountY, bullet.range, bullet.collidesAir, bullet.collidesGround);
                    mount.retarget = mount.target == null ? targetInterval : targetSwitchInterval;
                }
                if (mount.target != null && checkTarget(unit, mount.target, mountX, mountY, bullet.range)) mount.target = null;

                boolean shootNow = false;
                if (mount.target != null) {
                    shootNow = mount.target.within(mountX, mountY, bullet.range + Math.abs(shootY) + (mount.target instanceof Sized ? ((Sized) mount.target).hitSize() / 2f : 0f)) && can;
                    if (predictTarget) {
                        Vec2 to = Predict.intercept(unit, mount.target, bullet);
                        mount.aimX = to.x;
                        mount.aimY = to.y;
                    } else {
                        mount.aimX = mount.target.x();
                        mount.aimY = mount.target.y();
                    }
                }
                mount.shoot = mount.rotate = shootNow;
            }

            if (rotate && (mount.rotate || mount.shoot) && can) {
                float axisX = unit.x + Angles.trnsx(unit.rotation - 90, x, y),
                    axisY = unit.y + Angles.trnsy(unit.rotation - 90, x, y);
                mount.targetRotation = Angles.angle(axisX, axisY, mount.aimX, mount.aimY) - unit.rotation;
                mount.rotation = Angles.moveToward(mount.rotation, mount.targetRotation, rotateSpeed * Time.delta);
                if (rotationLimit < 360) {
                    float dst = Angles.angleDist(mount.rotation, baseRotation);
                    if (dst > rotationLimit / 2f) {
                        mount.rotation = Angles.moveToward(mount.rotation, baseRotation, dst - rotationLimit / 2f);
                    }
                }
            } else if (!rotate) {
                mount.rotation = baseRotation;
                mount.targetRotation = unit.angleTo(mount.aimX, mount.aimY);
            }

            float weaponRotation = unit.rotation - 90 + (rotate ? mount.rotation : baseRotation),
                bulletX = mountX + Angles.trnsx(weaponRotation, this.shootX, this.shootY),
                bulletY = mountY + Angles.trnsy(weaponRotation, this.shootX, this.shootY),
                shootAng = bulletRotation(unit, mount, bulletX, bulletY);

            if (alwaysShooting) mount.shoot = true;

            if (mount.isOverheat) mount.heat = Mathf.approachDelta(mount.heat, mount.overheat, 1 / 30f);
            else mount.heat = Math.max(mount.heat - Time.delta * unit.reloadMultiplier / cooldownTime, 0);
            if (mount.sound != null) mount.sound.update(bulletX, bulletY, false);

            boolean wasFlipped = mount.side;
            if (otherSide >= 0 && alternate && mount.side == flipSprite && otherSide < unit.mounts.length
                && mount.reload <= reload / 2f && lastReload > reload / 2f) {
                unit.mounts[otherSide].side = !unit.mounts[otherSide].side;
                mount.side = !mount.side;
            }

            if (!Vars.headless && activeSound != Sounds.none && mount.shoot && can && mount.warmup >= minWarmup) {
                Vars.control.sound.loop(activeSound, unit, activeSoundVolume);
            }

            float velLen = unit.isRemote() ? unit.vel.len() : unit.deltaLen() / Time.delta;

            if (mount.shoot && can
                && (!alternate || wasFlipped == flipSprite)
                && mount.warmup >= minWarmup
                && velLen >= minShootVelocity && (maxShootVelocity == -1 || velLen <= maxShootVelocity)
                && mount.reload <= 0.0001f
                && (alwaysShooting || Angles.within(rotate ? mount.rotation : unit.rotation + baseRotation, mount.targetRotation, shootCone))) {
                shoot(unit, mount, bulletX, bulletY, shootAng);
                mount.reload = reload;
            }
        }

        @Override
        public void draw(Unit unit, WeaponMount m) {
            HoniveryGatlingMount mount = (HoniveryGatlingMount) m;
            if (recoils > 0 && mount.recoils == null) mount.recoils = new float[recoils];

            float rotation = unit.rotation - 90,
                realRecoil = Mathf.pow(mount.recoil, recoilPow) * recoil,
                weaponRotation = rotation + (rotate ? mount.rotation : baseRotation),
                wx = unit.x + Angles.trnsx(rotation, x, y) + Angles.trnsx(weaponRotation, 0, -realRecoil),
                wy = unit.y + Angles.trnsy(rotation, x, y) + Angles.trnsy(weaponRotation, 0, -realRecoil),
                tx = wx + Angles.trnsx(weaponRotation, bx, by),
                ty = wy + Angles.trnsy(weaponRotation, bx, by);
            float z = Draw.z();
            float w = this.width / Mathf.cosDeg(inclineRotation);
            if (barrel != null && barrel.found()) {
                for (int i = 0; i < recoils; i++) {
                    float angle = Mathf.mod(mount.angle + (360f / recoils) * i, 360f);
                    Vec2 v = Tmp.v2.trns(weaponRotation + inclineRotation, w / 2f)
                        .scl(w / 2f * Mathf.cosDeg(angle))
                        .add(tx, ty).add(Tmp.v3.trns(weaponRotation - 90, mount.recoils[i] * recoilTravel));
                    float s = Mathf.sinDeg(angle);
                    Draw.z(z + s * 0.04f + 0.05f);
                    Draw.color(Color.white, Color.black, 0.10f - s * 0.09f);
                    Draw.rect(barrel, v.x, v.y, weaponRotation);
                    if (mount.overheat > 0.01f && barrelHeat.found()) {
                        Draw.color(overheatColor, mount.overheat);
                        Draw.blend(Blending.additive);
                        Draw.rect(barrelHeat, v.x, v.y, weaponRotation);
                        Draw.blend();
                    }
                }
            }
            Draw.reset();
            Draw.z(z + 0.1f);
            super.draw(unit, m);
        }

        public static class HoniveryGatlingMount extends WeaponMount {
            public float angle;
            public float keepWarmup = 0f;
            public float overheat = 0f;
            public boolean isOverheat;

            public HoniveryGatlingMount(Weapon weapon) {
                super(weapon);
                angle = ((HoniveryGatlingWeapon) weapon).baseAngle;
            }
        }
    }
}