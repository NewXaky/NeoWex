package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Sized;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.entities.units.AIController;
import mindustry.entities.units.StatusEntry;
import mindustry.entities.units.WeaponMount;
import mindustry.game.Team;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.EntityMapping;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.MechUnit;
import mindustry.gen.Mechc;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Env;
import newex.NexNet;
import newex.UnitSkillInfo;

import java.util.HashMap;

/**
 * Unit Lancyron (Lancyron) - Cơ giáp 2 chân hạng nặng phong cách vàng kim - carbon đen.
 * - Tầm bắn trội hơn Bulvasyr 22% (463.6 đơn vị ~ 58 ô).
 * - Khiên vật lý trước ngực (550% HP) vẽ tách lớp riêng (tấm chính + 2 cánh giáp bản lề) có animation cơ học riêng:
 *   nhấp nhô theo bước chân, đẩy thủy lực & mở rộng cánh khiên khi giao tranh/khai hỏa, giật lùi & tóe tia vàng khi đỡ đạn.
 * - 3 khe vũ khí:
 *   + 2 khe hông (vũ khí nhẹ): bắn nhanh loạt tên lửa tốc độ cao, sát thương nhỏ hơn nhưng liên thanh dồn dập.
 *   + 1 khe tâm unit (vũ khí nặng): pháo shotgun siêu tốc tầm ngắn-trung, tốc bay cực cao nhưng thời gian tồn tại thấp, sát thương nổ dồn cực lớn.
 * - Kỹ năng nút (hồi chiêu 5s): Roll con số 1-2-3 trên đầu unit trong 1s (đồng bộ toàn bộ player trong host) và kích hoạt 1 trong 3 hiệu ứng:
 *   1) +10% tốc độ bắn, toàn bộ khe vũ khí bắn liên kích (từ 1 trong 1 đòn thành 2 trong 1 đòn).
 *   2) Tạo 1 unit phân thân ngay vị trí đứng, bản thân tàng hình 3s và +50% tốc độ di chuyển (chỉ phe ta thấy bản tàng hình, địch không thấy).
 *   3) Khi máu < 99%: phá hủy khiên vật lý và +5000 HP vào Max HP; khi độ bền khiên < 99%: phá tấm khiên hiện tại và đợi 1s tái tạo lại 100% khiên.
 */
public final class Lancyron {
    public static UnitType lancyron;
    public static StatusEffect rollBuffStatus;

    /** Tầm bắn trội hơn Bulvasyr đúng +22% (380 * 1.22 = 463.6 ~ 58 ô). */
    public static final float RANGE = Bulvasyr.RANGE * 1.22f;

    /** Thông số 2 bệ tên lửa nhẹ kiểu Orkan hai bên hông: ổ 20 viên, nạp 5 viên/giây, xả 10 viên/giây. */
    public static final int ORKAN_MAG = 20;
    public static final float ORKAN_RELOAD_TICKS = 60f / 5f; // 12 tick/viên = 5 viên/giây
    public static final float ORKAN_FIRE_TICKS = 60f / 10f;  // 6 tick/viên = 10 viên/giây

    /** Thông số Khiên Vật Lý trước ngực. */
    public static final float SHIELD_HP_MULT = 5.5f;         // 550% HP
    public static final float SHIELD_ANGLE_OFFSET = 0f;      // 0° = CHÍNH DIỆN trước ngực
    public static final float SHIELD_HALF_ARC = 66f;         // che phủ toàn bộ mặt trước (±66°)

    /** Thông số Kỹ năng nút Roll 1-2-3. */
    public static final float SKILL_CD = 300f;               // hồi chiêu 5.0s (300 tick)
    public static final float ROLL_DURATION = 60f;           // thời gian roll số trên đầu: 1.0s (60 tick)
    public static final float ROLL1_DURATION = 300f;         // thời gian kỹ năng 1 (Liên kích x2 + 10% tốc bắn): 5.0s (300 tick)
    public static final float STEALTH_DURATION = 180f;       // tàng hình + 50% tốc chạy: 3.0s (180 tick)
    public static final float SHIELD_REFORGE_DELAY = 60f;    // đợi 1.0s (60 tick) tái tạo lại khiên
    public static final float BONUS_MAX_HP_ADD = 5000f;      // +5000 HP thêm vào Max HP khi máu < 99%
    public static final float CLONE_LIFETIME = 720f;         // thời gian tồn tại của phân thân (12s)

    public static final String KEYBIND_SETTING = "newex-pvp-lancyron-skill-key";
    static final String HUD_KEY = "newex-lancyron-skill-btn";
    static final String REQ_SKILL = "newex-lancyron-roll-req";
    static final String BCAST_ROLL = "newex-lancyron-roll-start";
    static final String BCAST_RESOLVE = "newex-lancyron-roll-res";

    // Bảng màu Lancyron (carbon đen + vàng kim vân đá + tia cam cháy + LED xanh tím)
    private static final Color OUTLINE_COL = Color.valueOf("1b1f27");
    private static final Color CARBON_DARK = Color.valueOf("272d37");
    private static final Color STEEL_LIGHT = Color.valueOf("b8c4d8");
    private static final Color GOLD_DARK   = Color.valueOf("a66308");
    private static final Color GOLD_MID    = Color.valueOf("f2a81d");
    private static final Color GOLD_BRIGHT = Color.valueOf("ffe37a");
    private static final Color GOLD_CORE   = Color.valueOf("fff8d6");
    private static final Color SOLAR_RED   = Color.valueOf("ff5a26");
    private static final Color LED_BLUE    = Color.valueOf("7a9aff");
    private static final Color STEALTH_CYAN = Color.valueOf("4ef2ff");
    private static final Color AEGIS_GREEN  = Color.valueOf("5bff96");

    private static boolean initialized = false;
    private static boolean netRegistered = false;
    private static TextureRegion shieldCenterRegion;
    private static TextureRegion shieldWingRegion;
    private static final Seq<LancyronUnit> activeLancyrons = new Seq<>(false, 8);
    private static Table hudBtn = null;
    private static float localCdEnd = 0f;
    private static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();

    // Hiệu ứng chiến đấu cực ngầu & gây nghiện
    private static Effect shieldHitFx;
    private static Effect shieldBreakFx;
    private static Effect shieldVitalityFx;
    private static Effect shieldReforgeCompleteFx;
    private static Effect roll1OverdriveFx;
    private static Effect roll2CloneMirageFx;
    private static Effect stealthAfterimageFx;
    private static Effect cloneDespawnFx;
    private static Effect footstepFx;
    private static Effect thrusterSteamFx;
    private static Effect missileMuzzleFx;
    private static Effect missileTrailFx;
    private static Effect missileHitFx;
    private static Effect shotgunMuzzleFx;
    private static Effect shotgunTrailFx;
    private static Effect shotgunHitFx;
    private static Effect shotgunDespawnFx;

    private Lancyron() {}

    static void drawStar4(float x, float y, float rot, float len, float wid, float shortLen) {
        Drawf.tri(x, y, wid, len, rot);
        Drawf.tri(x, y, wid, len, rot + 180f);
        Drawf.tri(x, y, wid * 0.75f, shortLen, rot + 90f);
        Drawf.tri(x, y, wid * 0.75f, shortLen, rot - 90f);
    }

    public static boolean isLancyron(Unit u) {
        return u != null && lancyron != null && u.type == lancyron;
    }

    public static boolean isCloneUnit(Unit u) {
        return u instanceof LancyronUnit && ((LancyronUnit) u).isClone;
    }

    /** Kiểm tra bản thể Lancyron đang trong trạng thái tàng hình (không thể bắn). */
    public static boolean isStealthedUnit(Unit u) {
        return u instanceof LancyronUnit && ((LancyronUnit) u).isStealthed();
    }

    /** Lấy phân thân đang hoạt động của Lancyron (nếu có). */
    public static Unit getActiveClone(Unit u) {
        if (!(u instanceof LancyronUnit)) return null;
        LancyronUnit lu = (LancyronUnit) u;
        if (lu.isClone || lu.activeCloneId <= 0) return null;
        Unit c = Groups.unit.getByID(lu.activeCloneId);
        if (c instanceof LancyronUnit && ((LancyronUnit) c).isClone && !c.dead && c.isValid()) {
            return c;
        }
        return null;
    }

    /** Địch hoàn toàn không nhìn thấy Lancyron đang tàng hình; phe ta vẫn thấy bản tàng hình bán trong suốt. */
    public static boolean isHiddenFromViewer(Unit u) {
        if (!(u instanceof LancyronUnit)) return false;
        LancyronUnit lu = (LancyronUnit) u;
        if (!lu.isStealthed()) return false;
        return Vars.player != null && Vars.player.team() != lu.team;
    }

    /** Lượng Max HP cộng thêm từ hiệu ứng Roll #3 (+5000 HP mỗi lần phá khiên khi máu < 99%). */
    public static float getBonusMaxHp(Unit u) {
        return (u instanceof LancyronUnit) ? ((LancyronUnit) u).bonusMaxHp : 0f;
    }

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
        row.add("[#F2A81D]Lancyron[] - Phím Roll Kỹ Năng 1-2-3 (PC):").left().growX().padRight(10f);
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

    /** Gọi từ NexMod() constructor để đăng ký EntityMapping, sự kiện khiên & kỹ năng nút Roll 1-2-3. */
    public static void init() {
        if (initialized) return;
        initialized = true;
        int ensureEntityRegistered = LancyronUnit.CLASS_ID;

        Events.on(WorldLoadEvent.class, e -> {
            activeLancyrons.clear();
            localCdEnd = 0f;
            cdEndSrv.clear();
        });

        Events.on(UnitDamageEvent.class, e -> {
            if (e.unit == null || e.bullet == null) return;
            if (e.unit instanceof LancyronUnit) {
                ((LancyronUnit) e.unit).handleBulletDamageEvent(e.bullet);
            } else if (!activeLancyrons.isEmpty() && Bulvasyr.isBeamOrInstantType(e.bullet.type) && !Bulvasyr.isExplosiveBullet(e.bullet.type)) {
                tryBlockLaserBehindShield(e.unit, e.bullet);
            }
        });

        Events.run(Trigger.update, Lancyron::updateGlobalSkill);

        Events.run(Trigger.afterGameUpdate, () -> {
            for (int i = activeLancyrons.size - 1; i >= 0; i--) {
                LancyronUnit lu = activeLancyrons.get(i);
                if (lu == null || !lu.isAdded() || lu.dead) {
                    activeLancyrons.remove(i);
                    continue;
                }
                lu.flushDeferredLethal();
            }
        });

        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try { buildHudButton(); } catch (Throwable err) { Log.err("[Lancyron] hud: " + err); }
            });
            Events.run(Trigger.draw, Lancyron::drawAllOverheads);
        }
    }

    static void tryRegisterNet() {
        if (netRegistered) return;
        netRegistered = true;
        NexNet.on(REQ_SKILL, Lancyron::onSkillReq, false);
        NexNet.onBroadcast(BCAST_ROLL, Lancyron::onRollBroadcast);
        NexNet.onBroadcast(BCAST_RESOLVE, Lancyron::onResolveBroadcast);
    }

    static void updateGlobalSkill() {
        tryRegisterNet();
        if (Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;
        if (!Vars.headless && !Vars.mobile && Vars.player != null && Core.scene != null
            && !Core.scene.hasField() && Core.input.keyTap(getSkillKey())) {
            castSkillFromClient();
        }
    }

    static void castSkillFromClient() {
        if (Vars.state == null || Vars.state.isPaused() || Vars.player == null || Time.time < localCdEnd) return;
        Unit u = Vars.player.unit();
        if (!(u instanceof LancyronUnit) || u.dead || ((LancyronUnit) u).isClone) return;
        if (Zeralion.isBound(u)) return;
        LancyronUnit lu = (LancyronUnit) u;
        if (lu.rollTimer > 0f) return;

        localCdEnd = Time.time + SKILL_CD;
        NexNet.send(REQ_SKILL, "");
    }

    static void onSkillReq(Player player, String payload) {
        if (player == null) return;
        Unit u = player.unit();
        if (!(u instanceof LancyronUnit) || u.dead || ((LancyronUnit) u).isClone) return;
        if (Zeralion.isBound(u)) return;
        LancyronUnit lu = (LancyronUnit) u;
        if (lu.rollTimer > 0f) return;

        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + SKILL_CD);
        try { GearItems.onUnitSkillCast(u); } catch (Throwable ignored) {}

        int outcome = Mathf.random(1, 3);
        NexNet.broadcast(BCAST_ROLL, lu.id + "," + outcome);
    }

    static void onRollBroadcast(String payload) {
        if (payload == null || payload.isEmpty()) return;
        try {
            String[] p = payload.split(",");
            int uid = Integer.parseInt(p[0].trim());
            int outcome = Mathf.clamp(Integer.parseInt(p[1].trim()), 1, 3);
            Unit u = Groups.unit.getByID(uid);
            if (u instanceof LancyronUnit && !u.dead) {
                ((LancyronUnit) u).beginOverheadRoll(outcome);
            }
        } catch (Throwable err) {
            Log.err("[Lancyron] roll broadcast failed: " + err);
        }
    }

    static void onResolveBroadcast(String payload) {
        if (payload == null || payload.isEmpty()) return;
        try {
            String[] p = payload.split(",");
            int uid = Integer.parseInt(p[0].trim());
            int outcome = Integer.parseInt(p[1].trim());
            int cloneId = Integer.parseInt(p[2].trim());
            boolean didHpBoost = Integer.parseInt(p[3].trim()) != 0;
            boolean didReforge = Integer.parseInt(p[4].trim()) != 0;
            float totalBonusHp = Float.parseFloat(p[5].trim());

            Unit u = Groups.unit.getByID(uid);
            if (u instanceof LancyronUnit && !u.dead) {
                ((LancyronUnit) u).applyResolvedOutcome(outcome, cloneId, didHpBoost, didReforge, totalBonusHp);
            } else if (cloneId > 0) {
                Unit cu = Groups.unit.getByID(cloneId);
                if (cu instanceof LancyronUnit) {
                    ((LancyronUnit) cu).isClone = true;
                    ((LancyronUnit) cu).cloneLife = CLONE_LIFETIME;
                }
            }
        } catch (Throwable err) {
            Log.err("[Lancyron] resolve broadcast failed: " + err);
        }
    }

    private static void tryBlockLaserBehindShield(Unit victim, Bullet b) {
        if (victim == null || victim.dead || b == null || b.team == victim.team) return;
        float srcX = b.x, srcY = b.y;
        if (b.owner instanceof Position) {
            Position p = (Position) b.owner;
            srcX = p.getX();
            srcY = p.getY();
        }
        for (int i = activeLancyrons.size - 1; i >= 0; i--) {
            LancyronUnit lu = activeLancyrons.get(i);
            if (lu == null || !lu.isAdded() || lu.dead) {
                activeLancyrons.remove(i);
                continue;
            }
            if (lu.team != victim.team || !lu.isShieldActive()) continue;
            if (!lu.doesSegmentHitShieldPlate(srcX, srcY, victim.x, victim.y)) continue;
            float angToSrc = Angles.angle(lu.x, lu.y, srcX, srcY);
            if (!lu.isAngleCoveredByShield(angToSrc)) continue;

            float mult = Bulvasyr.shieldDamageMultiplier(b.type);
            float rawDmg = Math.max(0f, b.damage);
            if (rawDmg <= 0f) return;

            float blockedRaw = rawDmg;
            if (!lu.wasBeamChargedThisTick(b.id)) {
                lu.markBeamChargedThisTick(b.id);
                float absorbed = lu.absorbPhysicalShield(rawDmg * mult, Tmp.v1.x, Tmp.v1.y, angToSrc);
                blockedRaw = absorbed / mult;
            }
            if (blockedRaw > 0.01f && !victim.dead) {
                float healBack = Math.min(blockedRaw, Math.max(0f, victim.maxHealth - victim.health));
                victim.health += healBack;
            }
            return;
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        init();
        createEffects();

        rollBuffStatus = new StatusEffect("lancyron-roll-buff") {
            {
                permanent = true;
                show = false;
            }

            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                if (unit instanceof LancyronUnit) {
                    LancyronUnit lu = (LancyronUnit) unit;
                    if (lu.isOverdriveActive()) {
                        unit.reloadMultiplier *= 1.10f;
                    }
                    if (lu.isStealthed()) {
                        unit.speedMultiplier *= 1.50f;
                    }
                }
            }
        };

        final OrkanRocketBulletType lightRocket = new OrkanRocketBulletType();
        final RelicShotgunBulletType heavyShotgun = new RelicShotgunBulletType();

        lancyron = new UnitType("lancyron") {
            @Override
            public void drawShadow(Unit unit) {
                if (isHiddenFromViewer(unit)) return;
                if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) return;
                super.drawShadow(unit);
            }

            @Override
            public void applyOutlineColor(Unit unit) {
                super.applyOutlineColor(unit);
                if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) {
                    float a = 0.25f + 0.08f * Mathf.sin(Time.time * 0.25f);
                    Draw.color(STEALTH_CYAN, a);
                }
            }

            @Override
            public void applyColor(Unit unit) {
                super.applyColor(unit);
                if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) {
                    float a = 0.36f + 0.10f * Mathf.sin(Time.time * 0.25f);
                    Draw.color(Tmp.c1.set(STEALTH_CYAN).lerp(GOLD_BRIGHT, 0.3f), a);
                    Draw.mixcol(STEALTH_CYAN, 0.42f);
                }
            }

            @Override
            public void drawMech(Mechc mech) {
                Unit unit = (Unit) mech;
                if (isHiddenFromViewer(unit)) return;
                Draw.reset();
                float e = unit.elevation;
                float sin = Mathf.lerp(Mathf.sin(mech.walkExtend(true), 2f / Mathf.pi, 1f), 0f, e);
                float extension = Mathf.lerp(mech.walkExtend(false), 0f, e);
                float boostTrns = e * 2f;

                boolean stealthed = (unit instanceof LancyronUnit) && ((LancyronUnit) unit).isStealthed();
                float stealthA = stealthed ? (0.36f + 0.10f * Mathf.sin(Time.time * 0.25f)) : 1f;

                if (stealthed) {
                    Draw.color(Tmp.c1.set(STEALTH_CYAN).lerp(GOLD_BRIGHT, 0.3f), stealthA);
                    Draw.mixcol(STEALTH_CYAN, 0.42f);
                } else {
                    // Xóa hiệu ứng pha xám (mechLegColor) làm mờ chân khi di chuyển — giữ chân sắc nét 100%!
                    Draw.mixcol(Color.white, Mathf.clamp(unit.hitTime));
                }
                for (int i : Mathf.signs) {
                    Draw.rect(legRegion,
                        unit.x + Angles.trnsx(mech.baseRotation(), extension * i - boostTrns, -boostTrns * i),
                        unit.y + Angles.trnsy(mech.baseRotation(), extension * i - boostTrns, -boostTrns * i),
                        legRegion.width * legRegion.scl() * i,
                        legRegion.height * legRegion.scl() * (1f - Math.max(-sin * i, 0f) * 0.5f),
                        mech.baseRotation() - 90f);
                }
                if (stealthed) {
                    Draw.color(Tmp.c1.set(STEALTH_CYAN).lerp(GOLD_BRIGHT, 0.3f), stealthA);
                } else {
                    Draw.color(Color.white);
                }
                Draw.rect(baseRegion, unit, mech.baseRotation() - 90f);
                Draw.mixcol();
                Draw.reset();
            }

            {
                constructor = LancyronUnit::new;

            localizedName = "Lancyron";
            description = "Cơ giáp 2 chân hạng nặng mang khiên vật lý trước ngực (550% HP), 2 bệ tên lửa Orkan xả nhanh bên hông, pháo shotgun Thunder ở tâm và kỹ năng Roll Vận Mệnh 1-2-3.";
            details = "Tầm bắn trội hơn Bulvasyr 22% (" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô). "
                + "2 khe vũ khí nhẹ Orkan hai bên hông: ổ 20 viên/khẩu, nạp liên tục 5 viên/giây, xả 10 viên/giây đạn tên lửa vật lý bay thẳng nổ phạm vi. "
                + "Khe vũ khí nặng Thunder ở tâm bắn đạn shotgun siêu tốc thời gian tồn tại ngắn gây sát thương dồn cực lớn. "
                + "Khiên vật lý trước ngực cố định (550% HP) chặn đạn, mảnh đạn và laser chính diện. "
                + "Kỹ năng nút (hồi chiêu 5s): Roll số 1-2-3 trên đầu trong 1s: (1) Trong 5s +10% tốc bắn & toàn bộ khe vũ khí bắn liên kích 2 đòn trong 1; (2) Tạo 1 phân thân tự di chuyển & tấn công, bản thân tàng hình 3s +50% tốc chạy và không thể bắn; (3) Máu <99% phá khiên +5000 Max HP, khiên <99% phá khiên hiện tại và đợi 1s hồi 100% khiên.";

            health = 48000f;
            armor = 22f;
            hitSize = 35f;
            speed = 0.74f;
            rotateSpeed = 2.75f;
            range = RANGE;
            maxRange = RANGE;
            targetAir = true;
            targetGround = true;
            faceTarget = true;
            envDisabled = Env.none;
            drawCell = false;

            // Cơ giáp 2 chân hạng nặng bước luân phiên trái/phải giống Bulvasyr
            hovering = false;
            canBoost = false;
            mechStride = 11.5f;
            mechFrontSway = 0.90f;
            mechSideSway = 0.58f;
            mechStepParticles = true;
            stepShake = 1.5f;

            outlineRadius = 3;
            outlineColor = OUTLINE_COL;

            abilities.add(new FrontShieldAbility());

            // 2 khe vũ khí NHẸ 2 bên hông (Orkan): ổ 20 viên, nạp 5 viên/s, xả 10 viên/s đạn tên lửa vật lý bay thẳng nổ diện rộng
            weapons.add(new OrkanWeapon("newex-lancyron-weapon-light") {{
                x = 20.2f;
                y = -1.0f;
                mirror = true;
                alternate = false;
                top = true;
                rotate = true;
                rotateSpeed = 9.5f;
                rotationLimit = 68f;
                shootCone = 30f;
                layerOffset = 0.04f;
                shootX = 0f;
                shootY = 9.2f;
                recoil = 2.0f;
                recoilTime = 12f;
                reload = ORKAN_FIRE_TICKS;
                shootSound = Sounds.none;
                ejectEffect = Fx.none;
                bullet = lightRocket;
            }});

            // 1 khe vũ khí NẶNG ở giữa tâm unit (Thunder): bắn chậm hơn, đạn shotgun tốc bay cực cao nhưng thời gian tồn tại thấp, sát thương cực lớn
            weapons.add(new ThunderShotgunWeapon("newex-lancyron-weapon-heavy") {{
                x = 2.5f;   // lệch phải 1 chút cho cân đối ngoại hình
                y = 2.5f;
                mirror = false;
                top = true;
                rotate = true;
                rotateSpeed = 5.4f;
                rotationLimit = 115f;
                shootCone = 24f;
                layerOffset = 0.08f;
                shootX = 0f;
                shootY = 14.5f;
                recoil = 5.5f;
                recoilTime = 42f;
                reload = 108f;
                shake = 3.2f;
                inaccuracy = 3.5f;
                velocityRnd = 0.14f;
                shoot = new ShootSpread() {{
                    shots = 9;
                    spread = 2.3f;
                }};
                shootSound = Sounds.shootSpectre;
                ejectEffect = Fx.none;
                bullet = heavyShotgun;
            }});
            }
        };

        UnitSkillInfo.registerLancyron();
    }

    private static void createEffects() {
        // Khiên trước ngực đỡ đạn: vòng xung kích tròn mượt + sao 4 cánh + hạt sáng tròn (tuyệt đối không dùng đường thẳng quanh vòng tròn)
        shieldHitFx = new Effect(18f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(GOLD_CORE, GOLD_MID, e.fin());
            Lines.stroke(2.0f * e.fout());
            Lines.circle(e.x, e.y, 2.5f + e.fin(Interp.pow2Out) * 12f);
            drawStar4(e.x, e.y, e.rotation, 8f * e.fout(), 2.2f * e.fout(), 4f * e.fout());
            Angles.randLenVectors(e.id, 6, 16f * e.finpow(), e.rotation, 72f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.9f * e.fout())
            );
            Draw.blend();
            Draw.reset();
        });

        // Vỡ khiên vật lý trước ngực: BẮN TUNG CÁC HẠT MẢNH VỠ KHIÊN SẮC NHỌN VỀ PHÍA TRƯỚC (không vẽ vòng tròn xung kích, không đường thẳng quanh vòng tròn!)
        shieldBreakFx = new Effect(56f, 300f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            float fwdAng = e.rotation;
            float fwdX = Angles.trnsx(fwdAng, 1f), fwdY = Angles.trnsy(fwdAng, 1f);
            float rightX = Angles.trnsx(fwdAng - 90f, 1f), rightY = Angles.trnsy(fwdAng - 90f, 1f);

            // 1) Khói áp suất thủy lực phụt thẳng về phía trước mặt khiên
            for (int k = 0; k < 10; k++) {
                int s0 = e.id * 31 + k * 97;
                float lat = Mathf.randomSeed(s0, -14f, 14f);
                float spr = Mathf.randomSeed(s0 + 1, -34f, 34f);
                float dist = (6f + Mathf.randomSeed(s0 + 2, 24f, 74f)) * Interp.pow3Out.apply(fin);
                float ang = fwdAng + spr;
                float px = e.x + rightX * lat + Angles.trnsx(ang, dist);
                float py = e.y + rightY * lat + Angles.trnsy(ang, dist);
                Draw.color(Tmp.c1.set(STEEL_LIGHT).lerp(CARBON_DARK, fin), 0.36f * fout);
                Fill.circle(px, py, 2.2f + 5.2f * Interp.pow2Out.apply(fin));
            }

            // 2) Luồng xung lực hình lưỡi liềm hẹp đẩy văng mảnh giáp về phía trước
            if (fin < 0.55f) {
                float p = fin / 0.55f;
                float fa = 1f - p;
                Draw.blend(Blending.additive);
                float cresR = 8f + Interp.pow3Out.apply(p) * 58f;
                int segs = 12;
                float halfCone = 42f;
                for (int i = 0; i < segs; i++) {
                    float f1 = (float) i / segs, f2 = (float) (i + 1) / segs;
                    float a1 = fwdAng - halfCone + halfCone * 2f * f1;
                    float a2 = fwdAng - halfCone + halfCone * 2f * f2;
                    float env1 = Mathf.sin(f1 * Mathf.PI), env2 = Mathf.sin(f2 * Mathf.PI);
                    float th1 = (9.5f * fa + 1.5f) * env1, th2 = (9.5f * fa + 1.5f) * env2;
                    Draw.color(SOLAR_RED, GOLD_BRIGHT, f1);
                    Draw.alpha(0.72f * fa * ((env1 + env2) * 0.5f));
                    Fill.quad(
                        e.x + Angles.trnsx(a1, cresR - th1), e.y + Angles.trnsy(a1, cresR - th1),
                        e.x + Angles.trnsx(a1, cresR), e.y + Angles.trnsy(a1, cresR),
                        e.x + Angles.trnsx(a2, cresR), e.y + Angles.trnsy(a2, cresR),
                        e.x + Angles.trnsx(a2, cresR - th2), e.y + Angles.trnsy(a2, cresR - th2)
                    );
                }
                // Chớp lửa vỡ tấm khiên hướng thẳng ra trước
                Draw.color(GOLD_BRIGHT, 0.9f * fa);
                Drawf.tri(e.x, e.y, 20f * fa, 36f * (1f - 0.4f * p), fwdAng);
                Draw.color(GOLD_CORE, fa);
                Drawf.tri(e.x, e.y, 10f * fa, 20f * (1f - 0.4f * p), fwdAng);
                Draw.blend();
            }

            // 3) 36 HẠT MẢNH VỠ KHIÊN ĐA GIÁC SẮC NHỌN BẮN Vọt RA PHÍA TRƯỚC & XOAY LẬT 3D
            for (int i = 0; i < 36; i++) {
                int seed = e.id * 53 + i * 131;
                // Phân bố ngang dọc theo bề rộng tấm khiên trước ngực rồi bắn vọt ra phía trước
                float startLat = Mathf.randomSeed(seed, -17.5f, 17.5f);
                float coneAng = fwdAng + startLat * 1.45f + Mathf.randomSeed(seed + 1, -18f, 18f);
                float maxDist = Mathf.randomSeed(seed + 2, 38f, 138f);
                float travel = Interp.pow4Out.apply(fin) * maxDist;

                float sx = e.x + rightX * startLat + Angles.trnsx(coneAng, travel);
                float sy = e.y + rightY * startLat + Angles.trnsy(coneAng, travel);

                float spinSpeed = Mathf.randomSeed(seed + 3, -340f, 340f);
                float shardRot = coneAng + Mathf.randomSeed(seed + 4, 0f, 360f) + fin * spinSpeed;
                float flip3D = 0.22f + 0.78f * Math.abs(Mathf.cos(fin * Mathf.randomSeed(seed + 5, 6f, 13f) + i));
                float sz = Mathf.randomSeed(seed + 6, 2.6f, 6.8f) * (0.35f + 0.65f * fout);

                // Vệt gió lửa ngắn bám sau mảnh vỡ đang lao nhanh về phía trước
                if (fin < 0.62f) {
                    float wakeA = (1f - fin / 0.62f) * 0.65f;
                    Draw.blend(Blending.additive);
                    Draw.color((i % 3 == 0) ? SOLAR_RED : GOLD_BRIGHT, wakeA);
                    Drawf.tri(sx, sy, sz * 0.72f, sz * 3.4f * (1f - fin), coneAng + 180f);
                    Draw.blend();
                }

                float rx = Angles.trnsx(shardRot, 1f), ry = Angles.trnsy(shardRot, 1f);
                float px = Angles.trnsx(shardRot - 90f, 1f) * flip3D, py = Angles.trnsy(shardRot - 90f, 1f) * flip3D;

                Color faceCol = (i % 4 == 0) ? GOLD_CORE : ((i % 2 == 0) ? GOLD_BRIGHT : ((i % 3 == 0) ? CARBON_DARK : GOLD_MID));

                if ((i & 1) == 0) {
                    // Mảnh giáp tứ giác vát nhọn bất đối xứng
                    Draw.color(OUTLINE_COL, 0.9f * fout);
                    float o = 1.24f;
                    Fill.quad(
                        sx + rx * sz * 1.35f * o, sy + ry * sz * 1.35f * o,
                        sx + px * sz * 0.75f * o + rx * sz * 0.2f, sy + py * sz * 0.75f * o + ry * sz * 0.2f,
                        sx - rx * sz * 1.05f * o, sy - ry * sz * 1.05f * o,
                        sx - px * sz * 0.55f * o - rx * sz * 0.15f, sy - py * sz * 0.55f * o - ry * sz * 0.15f
                    );
                    Draw.color(faceCol, 0.96f * fout);
                    Fill.quad(
                        sx + rx * sz * 1.35f, sy + ry * sz * 1.35f,
                        sx + px * sz * 0.75f + rx * sz * 0.2f, sy + py * sz * 0.75f + ry * sz * 0.2f,
                        sx - rx * sz * 1.05f, sy - ry * sz * 1.05f,
                        sx - px * sz * 0.55f - rx * sz * 0.15f, sy - py * sz * 0.55f - ry * sz * 0.15f
                    );
                } else {
                    // Mảnh dăm kim loại tam giác sắc lẹm
                    Draw.color(OUTLINE_COL, 0.9f * fout);
                    Drawf.tri(sx, sy, sz * 1.25f * flip3D, sz * 1.85f, shardRot);
                    Drawf.tri(sx, sy, sz * 1.25f * flip3D, sz * 0.75f, shardRot + 180f);
                    Draw.color(faceCol, 0.96f * fout);
                    Drawf.tri(sx, sy, sz * 0.95f * flip3D, sz * 1.55f, shardRot);
                    Drawf.tri(sx, sy, sz * 0.95f * flip3D, sz * 0.55f, shardRot + 180f);
                }
            }

            // 4) 20 dăm tinh thể vàng nóng chảy siêu tốc vọt xa hơn về phía trước
            Draw.blend(Blending.additive);
            for (int j = 0; j < 20; j++) {
                int s2 = e.id * 71 + j * 193;
                float coneAng = fwdAng + Mathf.randomSeed(s2, -32f, 32f);
                float dist = Mathf.randomSeed(s2 + 1, 52f, 162f) * Interp.pow3Out.apply(fin);
                float jx = e.x + Angles.trnsx(coneAng, dist);
                float jy = e.y + Angles.trnsy(coneAng, dist);
                float fsz = Mathf.randomSeed(s2 + 2, 1.8f, 3.6f) * fout;
                Draw.color((j & 1) == 0 ? GOLD_CORE : SOLAR_RED, 0.92f * a);
                Drawf.tri(jx, jy, fsz, fsz * 2.8f, coneAng);
                Drawf.tri(jx, jy, fsz * 0.8f, fsz * 1.2f, coneAng + 180f);
            }
            Draw.blend();

            Drawf.light(e.x + fwdX * 26f, e.y + fwdY * 26f, 110f * a, GOLD_BRIGHT, 0.92f * a);
            Draw.reset();
        }).layer(Layer.effect + 0.26f);

        // Hiệu ứng Roll #3 (Máu < 99%): Hấp thụ mảnh khiên hóa thành +5000 Max HP (chuỗi hạt cầu tròn xoắn ốc mượt mà hội tụ vào lõi unit, KHÔNG dùng đường thẳng!)
        shieldVitalityFx = new Effect(58f, 220f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);

            // 10 luồng hạt cầu tròn nhỏ dần phía sau xoắn ốc cuốn ngược vào tâm cơ giáp
            for (int i = 0; i < 10; i++) {
                for (int tail = 0; tail < 4; tail++) {
                    float tf = Mathf.clamp(fin * 1.35f - tail * 0.045f);
                    float spiral = Interp.pow2In.apply(1f - tf);
                    float ang = e.rotation + i * 36f + tf * 260f;
                    float rad = 46f * spiral;
                    float px = e.x + Angles.trnsx(ang, rad);
                    float py = e.y + Angles.trnsy(ang, rad);
                    float tFrac = 1f - tail * 0.22f;
                    Draw.color(i % 2 == 0 ? AEGIS_GREEN : GOLD_BRIGHT, 0.82f * a * tFrac);
                    Fill.circle(px, py, (2.2f + 2.4f * fout) * tFrac);
                }
            }

            // Vòng sóng tròn sinh mệnh nở kép mượt mà
            Lines.stroke(3.0f * a);
            Draw.color(AEGIS_GREEN, GOLD_BRIGHT, fin);
            Lines.circle(e.x, e.y, 10f + e.fin(Interp.pow3Out) * 46f);

            Lines.stroke(1.8f * a);
            Draw.color(GOLD_CORE, AEGIS_GREEN, fin);
            Lines.circle(e.x, e.y, 6f + e.fin(Interp.pow2Out) * 34f);

            drawStar4(e.x, e.y, 0f, 32f * a, 5.2f * a, 16f * a);
            Draw.blend();
            Drawf.light(e.x, e.y, 96f * a, AEGIS_GREEN, 0.9f * a);
            Draw.reset();
        }).layer(Layer.flyingUnit + 2.0f);

        // Hiệu ứng Roll #3 (Khiên < 99%, sau 1s tái tạo xong): Đập lắp khiên Aegis mới 100% (vòng sóng tròn + sao 4 cánh + hạt cầu sáng, KHÔNG dùng đường thẳng!)
        shieldReforgeCompleteFx = new Effect(48f, 220f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            float rot = e.rotation;
            Draw.blend(Blending.additive);

            // Chớp bóng tấm khiên phóng đại rồi khóa chặt vào ngực
            if (fin < 0.45f) {
                float p = fin / 0.45f;
                float sc = 1.65f - 0.65f * Interp.pow3Out.apply(p);
                float fa = 1f - p;
                if (shieldCenterRegion != null && shieldCenterRegion.found()) {
                    Draw.color(GOLD_BRIGHT, 0.85f * fa);
                    Draw.rect(shieldCenterRegion, e.x, e.y,
                        shieldCenterRegion.width * Draw.scl * sc,
                        shieldCenterRegion.height * Draw.scl * sc, rot - 90f);
                }
            }

            // Vòng sóng tròn đồng tâm vàng kim & xanh lục bảo
            Lines.stroke(3.6f * a);
            Draw.color(GOLD_CORE, GOLD_MID, fin);
            Lines.circle(e.x, e.y, 8f + e.fin(Interp.pow3Out) * 50f);

            Lines.stroke(2.0f * a);
            Draw.color(AEGIS_GREEN, LED_BLUE, fin);
            Lines.circle(e.x, e.y, 5f + e.fin(Interp.pow2Out) * 38f);

            // Ngôi sao 4 cánh rực sáng tại tâm khiên mới
            Draw.color(GOLD_BRIGHT, 0.95f * a);
            drawStar4(e.x, e.y, rot, 42f * a + 6f, 7.2f * a, 20f * a);
            Draw.color(GOLD_CORE, a);
            drawStar4(e.x, e.y, rot + 45f, 26f * a, 4.0f * a, 13f * a);
            Fill.circle(e.x, e.y, 9.5f * a);

            // Hạt cầu sáng bung quanh hai cánh khiên (không dùng đường thẳng)
            Angles.randLenVectors(e.id + 9, 12, 10f + 42f * e.finpow(), rot, 75f, (x, y) -> {
                Draw.color(GOLD_CORE, GOLD_MID, fin);
                Fill.circle(e.x + x, e.y + y, 2.2f * fout);
            });

            Draw.blend();
            Drawf.light(e.x, e.y, 110f * a, GOLD_BRIGHT, 0.95f * a);
            Draw.reset();
        }).layer(Layer.effect + 0.28f);

        // Hiệu ứng Roll #1: Kích hoạt Liên Kích (2 trong 1 đòn) & +10% Tốc Bắn
        roll1OverdriveFx = new Effect(42f, 180f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);

            Lines.stroke(3.2f * a);
            Draw.color(SOLAR_RED, GOLD_BRIGHT, fin);
            Lines.circle(e.x, e.y, 8f + e.fin(Interp.pow3Out) * 46f);

            // 2 cánh sao chéo tượng trưng cho Song Kích (2 đòn trong 1)
            for (int s = -1; s <= 1; s += 2) {
                float ang = e.rotation + s * 28f;
                Draw.color(s < 0 ? SOLAR_RED : GOLD_BRIGHT, 0.92f * a);
                drawStar4(e.x, e.y, ang, 38f * e.fin(Interp.pow2Out) + 8f, 5.5f * a, 16f * a);
            }
            Draw.color(GOLD_CORE, a);
            Fill.circle(e.x, e.y, 8f * a);
            Draw.blend();
            Drawf.light(e.x, e.y, 90f * a, SOLAR_RED, 0.88f * a);
            Draw.reset();
        }).layer(Layer.flyingUnit + 1.8f);

        // Hiệu ứng Roll #2: Phân thân & Tàng hình (vòng sóng tròn mịn + sao 4 cánh, không dùng đường đứt nét/đường thẳng)
        roll2CloneMirageFx = new Effect(44f, 180f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);

            Lines.stroke(3.0f * a);
            Draw.color(STEALTH_CYAN, LED_BLUE, fin);
            Lines.circle(e.x, e.y, 6f + e.fin(Interp.pow3Out) * 46f);

            Lines.stroke(1.6f * a);
            Draw.color(GOLD_BRIGHT, STEALTH_CYAN, fin);
            Lines.circle(e.x, e.y, 10f + e.fin(Interp.pow2Out) * 32f);

            Draw.color(STEALTH_CYAN, 0.9f * a);
            drawStar4(e.x, e.y, e.rotation, 34f * a + 5f, 5.2f * a, 17f * a);
            Draw.color(Color.white, a);
            Fill.circle(e.x, e.y, 7.5f * a);

            Draw.blend();
            Drawf.light(e.x, e.y, 85f * a, STEALTH_CYAN, 0.85f * a);
            Draw.reset();
        }).layer(Layer.flyingUnit + 1.8f);

        // Dư ảnh bóng ma cơ giáp khi tàng hình lướt đi (CHỈ phe đồng minh nhìn thấy, địch hoàn toàn không thấy!)
        stealthAfterimageFx = new Effect(22f, 90f, e -> {
            if (Vars.player != null && e.data instanceof Team && Vars.player.team() != e.data) return;
            if (lancyron == null || lancyron.region == null || !lancyron.region.found()) return;
            float fout = e.fout(Interp.pow2);
            Draw.z(Layer.groundUnit - 0.15f);
            Draw.blend(Blending.additive);
            Draw.color(STEALTH_CYAN, LED_BLUE, e.fin());
            Draw.alpha(0.38f * fout);
            float sc = 1f + 0.06f * e.fin();
            Draw.rect(lancyron.region, e.x, e.y,
                lancyron.region.width * Draw.scl * sc,
                lancyron.region.height * Draw.scl * sc,
                e.rotation - 90f);
            Draw.blend();
            Draw.reset();
        });

        // Hiệu ứng Phân thân tan biến khi hết giờ (bóng cơ giáp hóa quang ảnh tan biến mượt mà)
        cloneDespawnFx = new Effect(34f, 140f, e -> {
            float fin = e.fin(), fout = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            if (lancyron != null && lancyron.region != null && lancyron.region.found()) {
                float sc = 1f + 0.28f * Interp.pow2Out.apply(fin);
                Draw.color(STEALTH_CYAN, GOLD_BRIGHT, fin);
                Draw.alpha(0.58f * fout);
                Draw.rect(lancyron.region, e.x, e.y,
                    lancyron.region.width * Draw.scl * sc,
                    lancyron.region.height * Draw.scl * sc,
                    e.rotation - 90f);
            }
            Draw.color(STEALTH_CYAN, GOLD_CORE, fin);
            Draw.alpha(0.85f * fout);
            drawStar4(e.x, e.y, e.rotation, 30f * fout, 5f * fout, 15f * fout);
            Draw.blend();
            Draw.reset();
        }).layer(Layer.flyingUnit + 1.2f);

        // Bước chân nặng: vòng chấn + bụi công nghiệp + hạt sáng vàng/xanh LED (không dùng đường thẳng quanh vòng tròn)
        footstepFx = new Effect(32f, 75f, e -> {
            Draw.color(STEEL_LIGHT, 0.50f * e.fout());
            Lines.stroke(1.7f * e.fout());
            Lines.circle(e.x, e.y, 2.5f + e.fin(Interp.pow2Out) * 16f);
            Angles.randLenVectors(e.id, 6, 3f + 15f * e.finpow(), (x, y) -> {
                Draw.color(Tmp.c1.set(Color.valueOf("9ea6b3")).lerp(Color.gray, e.fin()), 0.44f * e.fout());
                Fill.circle(e.x + x, e.y + y, 1.8f + 3.0f * e.fin());
            });
            Draw.color(GOLD_BRIGHT, LED_BLUE, e.fin());
            Angles.randLenVectors(e.id + 3, 5, 4f + 13f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.5f * e.fout())
            );
            Draw.reset();
        }).layer(Layer.groundUnit - 1f);

        // Phụt khói & tia plasma trắng-vàng từ ống xả thruster sau lưng khi di chuyển (như ảnh 1 trailer)
        thrusterSteamFx = new Effect(26f, 60f, e -> {
            float fin = e.fin(), fout = e.fout();
            float dist = 12f * e.fin(Interp.pow2Out);
            float sx = e.x + Angles.trnsx(e.rotation, dist);
            float sy = e.y + Angles.trnsy(e.rotation, dist);
            Draw.color(Tmp.c1.set(Color.white).lerp(Color.lightGray, fin), 0.36f * fout);
            Fill.circle(sx, sy, 2.0f + 4.4f * fin);
            if (fin < 0.35f) {
                float fa = 1f - fin / 0.35f;
                Draw.blend(Blending.additive);
                Draw.color(GOLD_BRIGHT, SOLAR_RED, fin * 2.5f);
                Draw.alpha(0.75f * fa);
                Drawf.tri(e.x, e.y, 3.4f * fa, 10f * fa, e.rotation);
                Draw.blend();
            }
            Draw.reset();
        }).layer(Layer.groundUnit - 0.5f);

        // === VŨ KHÍ NHẸ ORKAN (2 BÊN HÔNG): TÊN LỬA VẬT LÝ XẢ LIÊN THANH ĐIỆN ẢNH ===
        missileMuzzleFx = new Effect(22f, 80f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            // Tia lửa phụt nòng vàng-cam sắc lẹm
            Draw.color(SOLAR_RED, 0.78f * a);
            Drawf.tri(e.x, e.y, 7.2f * a, 28f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_BRIGHT, 0.96f * a);
            Drawf.tri(e.x, e.y, 4.0f * a, 18f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_CORE, a);
            Drawf.tri(e.x, e.y, 2.0f * a, 10f * e.fout(Interp.pow2Out), e.rotation);
            // Vòng xung kích nhỏ tại họng phóng Orkan
            Lines.stroke(1.6f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow3Out) * 14f);
            Draw.blend();
            // Khói phụt ngược điện ảnh phía sau ống phóng
            Angles.randLenVectors(e.id, 6, 22f * e.finpow(), e.rotation + 180f, 38f, (x, y) -> {
                Draw.color(Tmp.c1.set(Color.lightGray).lerp(Color.darkGray, fin), 0.44f * fout);
                Fill.circle(e.x + x, e.y + y, 1.5f + 2.8f * fin);
            });
            Drawf.light(e.x, e.y, 38f * a, GOLD_BRIGHT, 0.72f * a);
            Draw.reset();
        });

        missileTrailFx = new Effect(28f, 48f, e -> {
            float fin = e.fin(), fout = e.fout();
            // Làn khói tên lửa đậm dần rồi tan mượt kiểu điện ảnh
            Draw.color(Tmp.c1.set(GOLD_MID).lerp(Color.valueOf("50545c"), Math.min(1f, fin * 1.4f)), 0.42f * fout);
            Fill.circle(e.x, e.y, 1.3f + 3.2f * e.fin(Interp.pow2Out));
            // Hạt cầu sáng & sao nhỏ lấp lánh chạy dọc đuôi tên lửa (không dùng đường thẳng)
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Draw.alpha(0.88f * fout);
            Fill.circle(e.x, e.y, 1.5f * fout + 0.4f);
            if ((e.id & 1) == 0) {
                drawStar4(e.x, e.y, e.rotation + 45f, 3.8f * fout + 0.4f, 0.9f * fout + 0.15f, 1.8f * fout);
            }
            Draw.blend();
            Draw.reset();
        });

        missileHitFx = new Effect(38f, 150f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            // Đám khói chiến trường nở ra sau vụ nổ splash Orkan
            Angles.randLenVectors(e.id + 3, 7, 5f + 24f * e.finpow(), (x, y) -> {
                Draw.color(Tmp.c1.set(Color.valueOf("4a484c")).lerp(Color.darkGray, fin), 0.48f * fout);
                Fill.circle(e.x + x, e.y + y, 2.2f + 5.2f * e.fin(Interp.pow2Out));
            });
            Draw.blend(Blending.additive);
            // Vụ nổ chéo 4 cánh rực lửa + lõi trắng nóng chảy
            Draw.color(SOLAR_RED, 0.78f * a);
            drawStar4(e.x, e.y, e.rotation + 45f, 26f * e.fin(Interp.pow2Out) + 7f, 5.8f * a, 13f * e.fin(Interp.pow2Out) + 3.5f);
            Draw.color(GOLD_BRIGHT, 0.98f * a);
            drawStar4(e.x, e.y, e.rotation, 21f * e.fin(Interp.pow2Out) + 5f, 3.6f * a, 10f * e.fin(Interp.pow2Out) + 2f);
            Draw.color(GOLD_CORE, a);
            Fill.circle(e.x, e.y, 8.5f * a);
            // Sóng xung kích kép phủ đúng phạm vi nổ splash (1.5 ô = 12u)
            Lines.stroke(2.2f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow3Out) * 15f);
            Lines.stroke(1.1f * a);
            Draw.color(GOLD_CORE, 0.85f * a);
            Lines.circle(e.x, e.y, 1.5f + e.fin(Interp.pow2Out) * 10f);
            // Hạt cầu lửa nóng văng ra (không dùng đường thẳng quanh vòng tròn)
            Draw.color(GOLD_CORE, SOLAR_RED, fin);
            Angles.randLenVectors(e.id + 11, 10, 7f + 32f * e.finpow(), (x, y) ->
                Fill.circle(e.x + x, e.y + y, 2.0f * fout)
            );
            Draw.blend();
            Drawf.light(e.x, e.y, 68f * a, GOLD_MID, 0.90f * a);
            Draw.reset();
        });

        // === VŨ KHÍ NẶNG (TÂM UNIT): SHOTGUN SIÊU TỐC ===
        shotgunMuzzleFx = new Effect(28f, 130f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            // Chớp nòng hình nón rộng cực gắt
            Draw.color(SOLAR_RED, 0.72f * a);
            Drawf.tri(e.x, e.y, 24f * a, 52f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_BRIGHT, 0.95f * a);
            Drawf.tri(e.x, e.y, 15f * a, 38f * e.fout(Interp.pow2Out), e.rotation);
            Draw.color(GOLD_CORE, a);
            Drawf.tri(e.x, e.y, 7f * a, 22f * e.fout(Interp.pow2Out), e.rotation);

            // 2 vòng sóng âm nén phóng về phía trước
            float r1 = 6f + e.fin(Interp.pow3Out) * 30f;
            float r2 = 4f + e.fin(Interp.pow2Out) * 18f;
            float ox1 = e.x + Angles.trnsx(e.rotation, 8f + fin * 20f);
            float oy1 = e.y + Angles.trnsy(e.rotation, 8f + fin * 20f);
            Lines.stroke(2.8f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(ox1, oy1, r2);
            Lines.stroke(1.6f * a);
            Draw.color(GOLD_CORE, 0.8f * a);
            Lines.circle(e.x, e.y, r1);

            // Hạt plasma vàng tóe theo hình nón shotgun (không dùng đường thẳng)
            Draw.color(GOLD_CORE, GOLD_MID, fin);
            Angles.randLenVectors(e.id + 17, 12, 10f + 44f * e.finpow(), e.rotation, 24f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 2.2f * fout)
            );
            Draw.blend();

            // Khói nóng phụt ngược 2 bên họng pháo
            Angles.randLenVectors(e.id + 31, 8, 22f * e.finpow(), e.rotation + 180f, 55f, (x, y) -> {
                Draw.color(Color.lightGray, 0.42f * fout);
                Fill.circle(e.x + x, e.y + y, 1.8f + 3.6f * fin);
            });
            Drawf.light(e.x, e.y, 82f * a, GOLD_BRIGHT, 0.95f * a);
            Draw.reset();
        });

        shotgunTrailFx = new Effect(10f, 36f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, e.fin());
            Draw.alpha(0.75f * fout);
            Fill.circle(e.x, e.y, 2.2f * fout);
            Draw.blend();
            Draw.reset();
        });

        shotgunHitFx = new Effect(24f, 110f, e -> {
            float fin = e.fin(), fout = e.fout();
            float a = e.fout(Interp.pow2);
            Draw.blend(Blending.additive);
            Draw.color(SOLAR_RED, 0.7f * a);
            drawStar4(e.x, e.y, e.rotation + 22f, 20f * e.fin(Interp.pow2Out) + 6f, 4.5f * a, 9f * e.fin(Interp.pow2Out) + 3f);
            Draw.color(GOLD_CORE, a);
            drawStar4(e.x, e.y, e.rotation, 14f * e.fin(Interp.pow2Out) + 4f, 2.2f * a, 6f * e.fin(Interp.pow2Out) + 2f);
            Lines.stroke(2.0f * a);
            Draw.color(GOLD_BRIGHT, SOLAR_RED, fin);
            Lines.circle(e.x, e.y, 2f + e.fin(Interp.pow3Out) * 20f);
            Draw.color(GOLD_CORE, LED_BLUE, fin * 0.7f);
            Angles.randLenVectors(e.id + 5, 7, 6f + 24f * e.finpow(), e.rotation + 180f, 52f, (x, y) ->
                Fill.circle(e.x + x, e.y + y, 1.8f * fout)
            );
            Draw.blend();
            Drawf.light(e.x, e.y, 44f * a, GOLD_BRIGHT, 0.8f * a);
            Draw.reset();
        });

        shotgunDespawnFx = new Effect(14f, 40f, e -> {
            float fout = e.fout();
            Draw.blend(Blending.additive);
            Draw.color(GOLD_MID, SOLAR_RED, e.fin());
            Draw.alpha(0.65f * fout);
            Fill.circle(e.x, e.y, 2.6f * fout);
            Draw.blend();
            Draw.reset();
        });
    }

    // ================================================================== Vũ khí nặng Thunder ở tâm (hỗ trợ Liên Kích: bắn 2 phát trong 1 đòn khi kích hoạt Roll #1)

    public static class ThunderShotgunWeapon extends Weapon {
        private boolean firingDouble = false;

        public ThunderShotgunWeapon(String name) {
            super(name);
        }

        @Override
        protected void shoot(Unit unit, WeaponMount mount, float shootX, float shootY, float rotation) {
            if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) return;
            super.shoot(unit, mount, shootX, shootY, rotation);
            if (!firingDouble && unit instanceof LancyronUnit && ((LancyronUnit) unit).isOverdriveActive()) {
                Time.run(6.5f, () -> {
                    if (unit == null || !unit.isValid() || unit.dead) return;
                    if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) return;
                    float wRot = unit.rotation - 90f + (rotate ? mount.rotation : baseRotation);
                    float mx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
                    float my = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
                    float sx = mx + Angles.trnsx(wRot, this.shootX, this.shootY);
                    float sy = my + Angles.trnsy(wRot, this.shootX, this.shootY);
                    float sAng = rotate ? wRot + 90f : unit.rotation + baseRotation;
                    firingDouble = true;
                    try {
                        super.shoot(unit, mount, sx, sy, sAng);
                        mount.recoil = 1f;
                    } finally {
                        firingDouble = false;
                    }
                });
            }
        }
    }

    // ================================================================== Vũ khí nhẹ Orkan (Ổ 20 viên, nạp 5/s, xả 10/s; khi Roll #1 Liên Kích sẽ bắn 2 quả trong 1 đòn!)

    public static class OrkanMount extends WeaponMount {
        public int ammo = ORKAN_MAG;
        public float regenAcc = 0f;
        public float fireCd = 0f;
        public int tubeIdx = 0;

        public OrkanMount(Weapon weapon) {
            super(weapon);
        }
    }

    public static class OrkanWeapon extends Weapon {
        public OrkanWeapon(String name) {
            super(name);
            mountType = OrkanMount::new;
        }

        private void fireSingleRocket(Unit unit, OrkanMount mount, float weaponRot, float mx, float my) {
            float ox = ((mount.tubeIdx++ % 4) - 1.5f) * 1.5f;
            float sx = mx + Angles.trnsx(weaponRot - 90f, ox, shootY);
            float sy = my + Angles.trnsy(weaponRot - 90f, ox, shootY);
            float fireAng = weaponRot + Mathf.range(3.0f);

            bullet.create(unit, unit.team, sx, sy, fireAng, -1f, 1f + Mathf.range(0.04f), 1f, null);
            missileMuzzleFx.at(sx, sy, fireAng);
            Effect.shake(0.75f, 4f, sx, sy);
            if (!Vars.headless) {
                Sounds.shootSmite.at(sx, sy, Mathf.random(1.05f, 1.22f), 0.42f);
            }
        }

        @Override
        public void update(Unit unit, WeaponMount m) {
            OrkanMount mount = (OrkanMount) m;
            float rMul = Math.max(0.25f, unit.reloadMultiplier);

            // Nạp liên tục 5 viên/giây (mỗi 12 tick +1 viên, tối đa 20 viên)
            if (mount.ammo < ORKAN_MAG) {
                mount.regenAcc += Time.delta * rMul;
                while (mount.regenAcc >= ORKAN_RELOAD_TICKS && mount.ammo < ORKAN_MAG) {
                    mount.regenAcc -= ORKAN_RELOAD_TICKS;
                    mount.ammo++;
                }
                if (mount.ammo >= ORKAN_MAG) mount.regenAcc = 0f;
            }

            if (mount.fireCd > 0f) {
                mount.fireCd = Math.max(0f, mount.fireCd - Time.delta * rMul);
            }

            boolean stealthed = (unit instanceof LancyronUnit) && ((LancyronUnit) unit).isStealthed();
            boolean wantShoot = !stealthed && mount.shoot;
            mount.shoot = false; // tự quản lý nhịp xả 10 viên/giây, không để vanilla bắn đúp
            super.update(unit, mount);

            if (wantShoot && mount.ammo > 0 && mount.fireCd <= 0f && !unit.dead && unit.isValid()) {
                float weaponRot = unit.rotation + mount.rotation;
                float mx = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
                float my = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
                float aimAng = Angles.angle(mx, my, mount.aimX, mount.aimY);

                if (Angles.within(weaponRot, aimAng, shootCone)) {
                    mount.ammo--;
                    mount.fireCd = ORKAN_FIRE_TICKS;
                    mount.recoil = 1f;

                    fireSingleRocket(unit, mount, weaponRot, mx, my);

                    // Hiệu ứng Roll #1 (Liên Kích 5s): bắn từ 1 trong 1 đòn thành 2 trong 1 đòn!
                    if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isOverdriveActive()) {
                        Time.run(2.4f, () -> {
                            if (unit == null || !unit.isValid() || unit.dead) return;
                            if (unit instanceof LancyronUnit && ((LancyronUnit) unit).isStealthed()) return;
                            float wRot2 = unit.rotation + mount.rotation;
                            float mx2 = unit.x + Angles.trnsx(unit.rotation - 90f, x, y);
                            float my2 = unit.y + Angles.trnsy(unit.rotation - 90f, x, y);
                            mount.recoil = 1f;
                            fireSingleRocket(unit, mount, wRot2, mx2, my2);
                        });
                    }
                }
            }
        }
    }

    private static class OrkanRocketBulletType extends BasicBulletType {
        OrkanRocketBulletType() {
            super(12.4f, 55f);
            lifetime = RANGE / 12.4f + 1.0f; // Đạt đủ tầm 463.6 (+22% hơn Bulvasyr)
            width = 7f;
            height = 13f;
            hitSize = 5f;
            splashDamage = 90f;
            splashDamageRadius = 1.5f * Vars.tilesize; // Phạm vi nổ 1.5 ô (12 đơn vị)
            pierce = false;
            pierceArmor = false;
            collidesAir = true;
            collidesGround = true;
            homingPower = 0f; // Đạn vật lý bay thẳng, KHÔNG truy đuổi!

            trailLength = 16;
            trailWidth = 2.3f;
            trailColor = GOLD_MID;
            trailInterval = 1.4f;
            trailEffect = missileTrailFx;
            trailChance = 0f;

            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            hitEffect = missileHitFx;
            despawnEffect = missileHitFx;
            hitColor = GOLD_BRIGHT;
            hitSound = Sounds.explosion;
            hitSoundVolume = 0.34f;
            lightRadius = 36f;
            lightOpacity = 0.68f;
            lightColor = GOLD_BRIGHT;
        }

        @Override
        public void draw(Bullet b) {
            drawTrail(b);
            float rot = b.rotation();
            float fx = Angles.trnsx(rot, 1f), fy = Angles.trnsy(rot, 1f);
            float lx = Angles.trnsx(rot + 90f, 1f), ly = Angles.trnsy(rot + 90f, 1f);
            float pulse = 0.84f + 0.16f * Mathf.sin(Time.time * 0.95f + b.id);

            // Đuôi lửa động cơ tên lửa điện ảnh (đỏ cam -> vàng rực -> lõi trắng)
            Draw.blend(Blending.additive);
            float tailX = b.x - fx * 6.5f, tailY = b.y - fy * 6.5f;
            Draw.color(SOLAR_RED, 0.78f);
            Drawf.tri(tailX, tailY, 5.2f * pulse, 17f * pulse, rot + 180f);
            Draw.color(GOLD_BRIGHT, 0.95f);
            Drawf.tri(tailX, tailY, 3.0f * pulse, 11f * pulse, rot + 180f);
            Draw.color(GOLD_CORE, 1f);
            Drawf.tri(tailX, tailY, 1.5f, 6.0f * pulse, rot + 180f);
            Draw.blend();

            // Cánh lái tên lửa thép tối viền vàng
            Draw.color(OUTLINE_COL);
            Drawf.tri(b.x - fx * 4.5f, b.y - fy * 4.5f, 7.6f, 5.4f, rot + 180f);
            Draw.color(GOLD_DARK);
            Drawf.tri(b.x - fx * 4.2f, b.y - fy * 4.2f, 5.6f, 3.8f, rot + 180f);

            // Thân tên lửa Orkan (thép đen + đầu đạn vàng công nghiệp)
            Draw.color(CARBON_DARK);
            Fill.quad(
                b.x + fx * 3.5f + lx * 2.0f, b.y + fy * 3.5f + ly * 2.0f,
                b.x + fx * 3.5f - lx * 2.0f, b.y + fy * 3.5f - ly * 2.0f,
                b.x - fx * 5.8f - lx * 2.2f, b.y - fy * 5.8f - ly * 2.2f,
                b.x - fx * 5.8f + lx * 2.2f, b.y - fy * 5.8f + ly * 2.2f
            );
            Draw.color(GOLD_MID);
            Drawf.tri(b.x + fx * 3.0f, b.y + fy * 3.0f, 4.0f, 5.8f, rot);
            Draw.color(GOLD_CORE);
            Drawf.tri(b.x + fx * 4.2f, b.y + fy * 4.2f, 2.0f, 3.8f, rot);

            Draw.blend(Blending.additive);
            Draw.color(GOLD_BRIGHT, 0.72f);
            Fill.circle(b.x + fx * 5.6f, b.y + fy * 5.6f, 2.2f);
            Draw.blend();
            Draw.reset();
        }
    }

    // ================================================================== Đạn Shotgun Siêu Tốc (Khe Vũ Khí Nặng Ở Tâm)

    private static class RelicShotgunBulletType extends BasicBulletType {
        RelicShotgunBulletType() {
            super(24.0f, 235f);
            lifetime = RANGE / 24.0f; // Bay nhanh (24.0) và thời gian tồn tại vừa đúng phạm vi bắn RANGE (463.6)
            drag = 0f;
            width = 6f;
            height = 18f;
            hitSize = 5.5f;
            pierce = true;
            pierceCap = 2;
            pierceArmor = true;
            knockback = 2.8f;
            collidesAir = true;
            collidesGround = true;

            trailLength = 9;
            trailWidth = 2.0f;
            trailColor = GOLD_BRIGHT;
            trailInterval = 1.2f;
            trailEffect = shotgunTrailFx;
            trailChance = 0f;

            shootEffect = shotgunMuzzleFx;
            smokeEffect = Fx.none;
            hitEffect = shotgunHitFx;
            despawnEffect = shotgunDespawnFx;
            hitColor = GOLD_BRIGHT;
            lightRadius = 38f;
            lightOpacity = 0.75f;
            lightColor = GOLD_BRIGHT;
        }

        @Override
        public void draw(Bullet b) {
            drawTrail(b);
            float rot = b.rotation();
            float fout = 0.45f + 0.55f * b.fout();

            Draw.blend(Blending.additive);
            // Tia plasma vàng-cam siêu tốc kiểu mũi lao shotgun
            Draw.color(SOLAR_RED, 0.65f * fout);
            Drawf.tri(b.x, b.y, 6.2f * fout, 22f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 5.2f * fout, 10f * fout, rot);

            Draw.color(GOLD_BRIGHT, 0.95f);
            Drawf.tri(b.x, b.y, 3.6f * fout, 17f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 3.4f * fout, 8.5f * fout, rot);

            Draw.color(GOLD_CORE, 1f);
            Drawf.tri(b.x, b.y, 1.8f, 11f * fout, rot + 180f);
            Drawf.tri(b.x, b.y, 1.8f, 5.5f, rot);

            // Chấm điện xanh-trắng nhỏ nhấp nháy ở đầu đạn
            if (((int) (Time.time + b.id) & 1) == 0) {
                Draw.color(LED_BLUE, 0.55f * fout);
                Fill.circle(b.x, b.y, 2.4f);
            }
            Draw.blend();
            Draw.reset();
        }
    }

    // ================================================================== Custom MechUnit với Khiên Vật Lý Trước Ngực + Kỹ Năng Roll 1-2-3

    public static class LancyronUnit extends MechUnit {
        public static final int CLASS_ID = EntityMapping.register("newex-lancyron-unit", LancyronUnit::new);

        @Override
        public int classId() {
            return CLASS_ID;
        }

        public float physShieldMax = -1f;
        public float physShieldHp = 0f;
        public boolean physShieldDamaged = false;
        public float shieldFlash = 0f;
        public float shieldOpen = 0f; // 0 = đóng khi không bắn, 1 = mở khiên + 2 cánh giáp khi đang bắn
        private float openHold = 0f;

        // Trạng thái kỹ năng nút Roll 1-2-3
        public float rollTimer = 0f;         // > 0: đang quay số 1-2-3 trên đầu trong 1s (60 tick)
        public int pendingOutcome = 0;       // kết quả 1, 2 hoặc 3 đã được server chốt
        public int shownOutcome = 0;         // số hiển thị sau khi quay xong
        public float rollRevealTimer = 0f;   // thời gian giữ bảng kết quả trên đầu sau khi chốt số
        public String revealSubText = "";
        private int lastTickSlot = -1;

        // Hiệu ứng 1 (kéo dài 5s): +10% tốc bắn & bắn liên kích (2 đòn trong 1)
        public float overdriveTimer = 0f;

        // Hiệu ứng 2: Phân thân & Tàng hình 3s + 50% tốc chạy (bản thật tàng hình không thể bắn)
        public float stealthTimer = 0f;
        public boolean isClone = false;
        public int cloneOwnerId = -1;
        public float cloneLife = 0f;
        public int activeCloneId = -1;
        private float ghostTrailAcc = 0f;

        // Hiệu ứng 3: Máu < 99% phá khiên +5000 Max HP; Khiên < 99% phá khiên & đợi 1s hồi lại khiên
        public float bonusMaxHp = 0f;
        public float shieldReforgeTimer = 0f;

        private float thrusterTimer = 0f;
        private int lastStep = -1;
        private int ageTicks = 0;

        private final int[] handledBulletIds = new int[16];
        private int handledBulletIdx = 0;
        private final int[] handledExplosiveIds = new int[16];
        private int handledExplosiveIdx = 0;
        private final int[] beamTickBulletIds = new int[12];
        private int beamTickStamp = -1;
        private int beamTickCount = 0;

        {
            java.util.Arrays.fill(handledBulletIds, -1);
            java.util.Arrays.fill(handledExplosiveIds, -1);
        }

        private float hpBeforeRaw = -1f;
        private float shieldBeforeRaw = -1f;
        private float lastRawAmount = 0f;
        private int lastRawTick = -1;
        private boolean deferredLethal = false;
        private boolean applyingDeferredLethal = false;

        public boolean isOverdriveActive() {
            return overdriveTimer > 0.01f;
        }

        public boolean isStealthed() {
            return stealthTimer > 0.01f && !isClone;
        }

        @Override
        public boolean targetable(Team targeter) {
            if (isStealthed() && targeter != team) return false;
            return super.targetable(targeter);
        }

        public void beginOverheadRoll(int outcome) {
            this.pendingOutcome = Mathf.clamp(outcome, 1, 3);
            this.rollTimer = ROLL_DURATION;
            this.rollRevealTimer = 0f;
            this.lastTickSlot = -1;
            if (!Vars.headless) {
                newex.SfxCompat.at("message", x, y, 1.35f, 0.75f);
            }
        }

        public float shieldSurfaceDist() {
            return 15.2f + shieldOpen * 3.2f;
        }

        public void shatterShieldCinematic() {
            float plateDist = shieldSurfaceDist();
            float plateX = x + Angles.trnsx(rotation, plateDist);
            float plateY = y + Angles.trnsy(rotation, plateDist);
            physShieldHp = 0f;
            physShieldDamaged = true;
            shieldFlash = 1f;
            shieldBreakFx.at(plateX, plateY, rotation);
            dealShieldShatterDamage(plateX, plateY, rotation);
            Effect.shake(5.5f, 24f, plateX, plateY);
            if (!Vars.headless) {
                Sounds.explosion.at(plateX, plateY, 0.82f, 1.0f);
                Sounds.shootSmite.at(plateX, plateY, 0.74f, 0.88f);
            }
        }

        /** Khi khiên vỡ (kỹ năng phá khiên / phân thân nổ khiên / khiên bị bắn vỡ), chùm mảnh giáp văng ra trước gây sát thương diện nón. */
        private void dealShieldShatterDamage(float originX, float originY, float fwdAng) {
            if (Vars.net.client()) return;
            final float coneRange = 160f;
            final float halfArc = 46f;
            final float baseDmg = 2800f * Math.max(1f, damageMultiplier) + maxHealth * 0.08f;

            try {
                Units.nearbyEnemies(team, originX - coneRange - 24f, originY - coneRange - 24f, (coneRange + 24f) * 2f, (coneRange + 24f) * 2f, eu -> {
                    if (eu == null || eu.dead || !eu.isValid() || !eu.targetable(team)) return;
                    float pad = eu.hitSize * 0.5f;
                    float dst = Mathf.dst(originX, originY, eu.x, eu.y);
                    if (dst - pad > coneRange) return;
                    float ang = Angles.angle(originX, originY, eu.x, eu.y);
                    boolean inCloseFace = dst <= 24f + pad && Angles.within(fwdAng, ang, 78f);
                    if (!inCloseFace && !Angles.within(fwdAng, ang, halfArc)) return;

                    float falloff = 1f - 0.35f * Mathf.clamp((dst - pad) / coneRange);
                    eu.damagePierce(baseDmg * falloff * 0.35f);
                    eu.damage(baseDmg * falloff * 0.65f);
                    float imp = 26f * falloff * Math.max(1f, eu.mass() * 0.015f);
                    eu.impulse(Angles.trnsx(fwdAng, imp), Angles.trnsy(fwdAng, imp));
                    shieldHitFx.at(eu.x, eu.y, fwdAng);
                });
            } catch (Throwable ignored) {}

            try {
                final float r2Max = (coneRange + 24f) * (coneRange + 24f);
                Groups.build.each(b -> {
                    if (b == null || !b.isValid() || b.team == team || b.team == Team.derelict) return;
                    if (Mathf.dst2(originX, originY, b.x, b.y) > r2Max) return;
                    float pad = b.block != null ? b.block.size * Vars.tilesize * 0.5f : 4f;
                    float dst = Mathf.dst(originX, originY, b.x, b.y);
                    if (dst - pad > coneRange) return;
                    float ang = Angles.angle(originX, originY, b.x, b.y);
                    boolean inCloseFace = dst <= 24f + pad && Angles.within(fwdAng, ang, 78f);
                    if (!inCloseFace && !Angles.within(fwdAng, ang, halfArc)) return;

                    float falloff = 1f - 0.35f * Mathf.clamp((dst - pad) / coneRange);
                    b.damage(team, baseDmg * falloff);
                    shieldHitFx.at(b.x, b.y, fwdAng);
                });
            } catch (Throwable ignored) {}
        }

        private void resolveSkillRollServer() {
            if (Vars.net.client() || dead || !isAdded()) return;
            int out = Mathf.clamp(pendingOutcome, 1, 3);
            int spawnedCloneId = -1;
            boolean didHpBoost = false;
            boolean didReforge = false;

            if (out == 2) {
                LancyronUnit clone = spawnCloneServer();
                if (clone != null) spawnedCloneId = clone.id;
            } else if (out == 3) {
                ensureShieldInit();
                boolean lowHp = health < maxHealth * 0.99f;
                boolean lowShield = shieldRatio() < 0.99f;
                if (lowHp) {
                    didHpBoost = true;
                    // Khi máu < 99%, phá hủy khiên vật lý khiến độ bền khiên về 0% (< 99%) -> kích hoạt luôn chu trình 1s tái tạo khiên
                    lowShield = true;
                }
                if (!lowHp && !lowShield) {
                    // Nếu cả máu lẫn khiên đều đầy 100%, vẫn cho phá tấm khiên hiện tại và đợi 1s tái tạo lại cực ngầu
                    lowShield = true;
                }
                didReforge = lowShield;
            }

            float nextBonusHp = bonusMaxHp + (didHpBoost ? BONUS_MAX_HP_ADD : 0f);
            NexNet.broadcast(BCAST_RESOLVE, id + "," + out + "," + spawnedCloneId + "," + (didHpBoost ? 1 : 0) + "," + (didReforge ? 1 : 0) + "," + Math.round(nextBonusHp));
        }

        private LancyronUnit spawnCloneServer() {
            if (Vars.net.client() || lancyron == null) return null;
            // Xóa phân thân cũ của cùng unit nếu vẫn còn sống để tránh dồn quá nhiều phân thân
            if (activeCloneId > 0) {
                Unit old = Groups.unit.getByID(activeCloneId);
                if (old instanceof LancyronUnit && ((LancyronUnit) old).isClone && !old.dead) {
                    cloneDespawnFx.at(old.x, old.y, old.rotation);
                    old.remove();
                }
            }
            Unit u = lancyron.create(team);
            if (!(u instanceof LancyronUnit)) return null;
            LancyronUnit clone = (LancyronUnit) u;
            clone.isClone = true;
            clone.cloneOwnerId = this.id;
            clone.cloneLife = CLONE_LIFETIME;
            clone.maxHealth = this.maxHealth;
            clone.health = Math.max(1f, this.health);
            clone.armor = this.armor;
            clone.damageMultiplier = this.damageMultiplier;
            clone.bonusMaxHp = this.bonusMaxHp;
            clone.overdriveTimer = this.overdriveTimer;
            clone.physShieldMax = this.physShieldMax > 0f ? this.physShieldMax : this.maxHealth * SHIELD_HP_MULT;
            clone.physShieldHp = this.physShieldHp > 0f ? this.physShieldHp : clone.physShieldMax;
            clone.physShieldDamaged = this.physShieldDamaged;
            clone.shieldOpen = this.shieldOpen;
            clone.set(this.x, this.y);
            clone.rotation = this.rotation;
            clone.baseRotation = this.baseRotation;
            clone.controller(new CloneCombatAI(this.rotation));
            clone.add();
            if (rollBuffStatus != null) {
                clone.apply(rollBuffStatus, Float.MAX_VALUE);
            }
            this.activeCloneId = clone.id;
            return clone;
        }

        public void applyResolvedOutcome(int outcome, int cloneId, boolean didHpBoost, boolean didReforge, float totalBonusHp) {
            this.rollTimer = 0f;
            this.shownOutcome = outcome;
            this.rollRevealTimer = 105f;
            if (rollBuffStatus != null && !hasEffect(rollBuffStatus)) {
                apply(rollBuffStatus, Float.MAX_VALUE);
            }

            if (outcome == 1) {
                this.overdriveTimer = ROLL1_DURATION;
                this.revealSubText = "LIÊN KÍCH x2 · +10% TỐC BẮN (5S)";
                roll1OverdriveFx.at(x, y, rotation);
                Effect.shake(3.0f, 14f, x, y);
                if (!Vars.headless) {
                    Sounds.shootSpectre.at(x, y, 1.32f, 0.8f);
                }
            } else if (outcome == 2) {
                this.stealthTimer = STEALTH_DURATION;
                this.activeCloneId = cloneId;
                this.isShooting = false;
                if (this.mounts != null) {
                    for (WeaponMount m : this.mounts) m.shoot = false;
                }
                this.revealSubText = "PHÂN THÂN · TÀNG HÌNH 3S (+50% TỐC)";
                roll2CloneMirageFx.at(x, y, rotation);
                Effect.shake(2.6f, 12f, x, y);
                if (!Vars.headless) {
                    Sounds.shootLancer.at(x, y, 1.4f, 0.75f);
                }
                if (cloneId > 0) {
                    Unit cu = Groups.unit.getByID(cloneId);
                    if (cu instanceof LancyronUnit) {
                        LancyronUnit clone = (LancyronUnit) cu;
                        clone.isClone = true;
                        clone.cloneOwnerId = this.id;
                        clone.cloneLife = CLONE_LIFETIME;
                        clone.overdriveTimer = this.overdriveTimer;
                        clone.physShieldMax = this.physShieldMax > 0f ? this.physShieldMax : this.maxHealth * SHIELD_HP_MULT;
                        clone.physShieldHp = this.physShieldHp > 0f ? this.physShieldHp : clone.physShieldMax;
                        if (!(clone.controller() instanceof CloneCombatAI)) {
                            clone.controller(new CloneCombatAI(this.rotation));
                        }
                        // Nếu người xem là phe địch: chuyển bảng hiển thị số "2" sang đứng trên đầu Phân Thân để không lộ vị trí bản thể tàng hình!
                        clone.shownOutcome = 2;
                        clone.rollRevealTimer = 105f;
                        clone.revealSubText = this.revealSubText;
                    }
                }
                breakEnemyTargetLocks(cloneId);
            } else if (outcome == 3) {
                ensureShieldInit();
                shatterShieldCinematic();

                if (didHpBoost) {
                    float prevBonus = this.bonusMaxHp;
                    this.bonusMaxHp = Math.max(this.bonusMaxHp + BONUS_MAX_HP_ADD, totalBonusHp);
                    float deltaHp = Math.max(BONUS_MAX_HP_ADD, this.bonusMaxHp - prevBonus);
                    this.maxHealth += deltaHp;
                    if (!Vars.net.client()) {
                        this.health = Math.min(this.maxHealth, this.health + deltaHp);
                    }
                    this.physShieldMax = this.maxHealth * SHIELD_HP_MULT;
                    shieldVitalityFx.at(x, y, rotation);
                }

                if (didReforge) {
                    this.shieldReforgeTimer = SHIELD_REFORGE_DELAY;
                }

                if (didHpBoost && didReforge) {
                    this.revealSubText = "+5000 MAX HP · TÁI TẠO KHIÊN (1S)";
                } else if (didHpBoost) {
                    this.revealSubText = "PHÁ KHIÊN · +5000 MAX HP";
                } else {
                    this.revealSubText = "TÁI ĐÚC KHIÊN AEGIS (1S)";
                }
            }
        }

        /** Khi bắt đầu tàng hình, cắt khóa mục tiêu của pháo và unit địch đang nhắm vào bản thể (chuyển sang phân thân nếu có). */
        private void breakEnemyTargetLocks(int cloneId) {
            final Unit decoy = cloneId > 0 ? Groups.unit.getByID(cloneId) : null;
            float r = RANGE * 1.4f;
            float r2 = r * r;
            try {
                // Groups.build không dùng QuadTree (tree == null) nên phải dùng Groups.build.each thay vì intersect!
                Groups.build.each(b -> {
                    if (b instanceof Turret.TurretBuild && b.team != team && Mathf.dst2(x, y, b.x, b.y) <= r2) {
                        Turret.TurretBuild tb = (Turret.TurretBuild) b;
                        if (tb.target == this) {
                            tb.target = decoy;
                        }
                    }
                });
            } catch (Throwable ignored) {}
            try {
                Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, eu -> {
                    if (eu != null && eu.mounts != null) {
                        for (WeaponMount m : eu.mounts) {
                            if (m.target == this) m.target = decoy;
                        }
                    }
                });
            } catch (Throwable ignored) {}
        }

        private boolean wasBulletHandled(int id) {
            for (int i = 0; i < handledBulletIds.length; i++) if (handledBulletIds[i] == id) return true;
            return false;
        }

        private void markBulletHandled(int id) {
            handledBulletIds[handledBulletIdx] = id;
            handledBulletIdx = (handledBulletIdx + 1) & 15;
        }

        private boolean wasExplosiveCharged(int id) {
            for (int i = 0; i < handledExplosiveIds.length; i++) if (handledExplosiveIds[i] == id) return true;
            return false;
        }

        private void markExplosiveCharged(int id) {
            handledExplosiveIds[handledExplosiveIdx] = id;
            handledExplosiveIdx = (handledExplosiveIdx + 1) & 15;
        }

        boolean wasBeamChargedThisTick(int id) {
            int tick = (int) Time.time;
            if (beamTickStamp != tick) {
                beamTickStamp = tick;
                beamTickCount = 0;
                return false;
            }
            for (int i = 0; i < beamTickCount; i++) if (beamTickBulletIds[i] == id) return true;
            return false;
        }

        void markBeamChargedThisTick(int id) {
            int tick = (int) Time.time;
            if (beamTickStamp != tick) {
                beamTickStamp = tick;
                beamTickCount = 0;
            }
            if (beamTickCount < beamTickBulletIds.length) beamTickBulletIds[beamTickCount++] = id;
        }

        public void ensureShieldInit() {
            if (maxHealth <= 0f) return;
            float targetMax = maxHealth * SHIELD_HP_MULT;
            if (physShieldMax < 0f || (!physShieldDamaged && ageTicks < 20 && Math.abs(physShieldMax - targetMax) > 1f)) {
                physShieldMax = targetMax;
                physShieldHp = targetMax;
            }
        }

        public float shieldRatio() {
            return physShieldMax > 0f ? Mathf.clamp(physShieldHp / physShieldMax) : 0f;
        }

        public boolean isShieldActive() {
            return physShieldHp > 0.01f && shieldReforgeTimer <= 0f;
        }

        /** Kiểm tra hướng nguồn sát thương có rơi vào mặt TRƯỚC có khiên ngực hay không. */
        public boolean isAngleCoveredByShield(float angleFromUnitToSource) {
            return Angles.within(rotation + SHIELD_ANGLE_OFFSET, angleFromUnitToSource, SHIELD_HALF_ARC);
        }

        /** Đoạn thẳng (ax, ay) -> (bx, by) có cắt ngang mặt cong thực tế của tấm khiên vật lý trước ngực Lancyron không (ghi điểm chạm gần nhất vào Tmp.v1). */
        public boolean doesSegmentHitShieldPlate(float ax, float ay, float bx, float by) {
            float fwdX = Angles.trnsx(rotation, 1f), fwdY = Angles.trnsy(rotation, 1f);
            float rightX = Angles.trnsx(rotation - 90f, 1f), rightY = Angles.trnsy(rotation - 90f, 1f);
            float dNose = shieldSurfaceDist();
            float noseHalf = 8.4f;
            float wingHalf = 17.2f + shieldOpen * 1.8f;
            float dWing = dNose - 4.8f;

            // 4 đỉnh tạo thành đường viền cong 3 đoạn ôm sát sprite tấm khiên chính + 2 cánh giáp bản lề
            float lxOuter = x + fwdX * dWing - rightX * wingHalf;
            float lyOuter = y + fwdY * dWing - rightY * wingHalf;
            float lxInner = x + fwdX * dNose - rightX * noseHalf;
            float lyInner = y + fwdY * dNose - rightY * noseHalf;
            float rxInner = x + fwdX * dNose + rightX * noseHalf;
            float ryInner = y + fwdY * dNose + rightY * noseHalf;
            float rxOuter = x + fwdX * dWing + rightX * wingHalf;
            float ryOuter = y + fwdY * dWing + rightY * wingHalf;

            boolean hit = false;
            float bestD2 = Float.MAX_VALUE;
            float bestX = 0f, bestY = 0f;

            if (Intersector.intersectSegments(ax, ay, bx, by, lxInner, lyInner, rxInner, ryInner, Tmp.v2)) {
                float d2 = Mathf.dst2(ax, ay, Tmp.v2.x, Tmp.v2.y);
                if (d2 < bestD2) { bestD2 = d2; bestX = Tmp.v2.x; bestY = Tmp.v2.y; hit = true; }
            }
            if (Intersector.intersectSegments(ax, ay, bx, by, lxOuter, lyOuter, lxInner, lyInner, Tmp.v2)) {
                float d2 = Mathf.dst2(ax, ay, Tmp.v2.x, Tmp.v2.y);
                if (d2 < bestD2) { bestD2 = d2; bestX = Tmp.v2.x; bestY = Tmp.v2.y; hit = true; }
            }
            if (Intersector.intersectSegments(ax, ay, bx, by, rxInner, ryInner, rxOuter, ryOuter, Tmp.v2)) {
                float d2 = Mathf.dst2(ax, ay, Tmp.v2.x, Tmp.v2.y);
                if (d2 < bestD2) { bestD2 = d2; bestX = Tmp.v2.x; bestY = Tmp.v2.y; hit = true; }
            }
            if (hit) Tmp.v1.set(bestX, bestY);
            return hit;
        }

        /** Kẹp tọa độ va chạm nằm sát ngay trên bề mặt tấm khiên vật lý để hiệu ứng nổ không bao giờ lơ lửng xa bên ngoài. */
        private void snapPointToShieldSurface(float px, float py) {
            float ang = Angles.angle(x, y, px, py);
            float clampedAng = Angles.moveToward(rotation, ang, SHIELD_HALF_ARC);
            float rel = Angles.angleDist(rotation, clampedAng);
            float d = shieldSurfaceDist() - 4.4f * (rel / SHIELD_HALF_ARC);
            Tmp.v1.set(x + Angles.trnsx(clampedAng, d), y + Angles.trnsy(clampedAng, d));
        }

        public float absorbPhysicalShield(float shieldDmg, float hitX, float hitY, float hitAngle) {
            ensureShieldInit();
            if (shieldDmg <= 0f || physShieldHp <= 0f) return 0f;

            physShieldDamaged = true;
            shieldFlash = 1f;

            float absorbed = Math.min(physShieldHp, shieldDmg);
            physShieldHp -= absorbed;

            if (physShieldHp <= 0.01f) {
                shieldHitFx.at(hitX, hitY, hitAngle);
                shatterShieldCinematic();
            } else {
                shieldHitFx.at(hitX, hitY, hitAngle);
            }
            return absorbed;
        }

        private boolean isMovingBulletFromShieldSide(Bullet b, float hitX, float hitY) {
            if (isAngleCoveredByShield(Angles.angle(x, y, hitX, hitY))) return true;
            if (isAngleCoveredByShield(b.rotation() + 180f)) return true;
            float step = Math.max(1f, Time.delta);
            return doesSegmentHitShieldPlate(
                b.x - b.vel.x * step, b.y - b.vel.y * step,
                b.x + b.vel.x * step, b.y + b.vel.y * step
            );
        }

        private boolean processShieldProjectile(Bullet b, float hx, float hy) {
            if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return false;
            if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return true;

            float angToHit = Angles.angle(x, y, hx, hy);
            float mult = Bulvasyr.shieldDamageMultiplier(b.type);

            if (Bulvasyr.isExplosiveBullet(b.type)) {
                markExplosiveCharged(b.id);
                float directDmg = Math.max(0f, b.damage);
                float splashDmg = Math.max(0f, b.type.splashDamage);
                float shieldCost = (directDmg + splashDmg) * mult;
                if (shieldCost > 0f) absorbPhysicalShield(shieldCost, hx, hy, angToHit);

                if (!Vars.headless) {
                    if (b.type.hitEffect != null) b.type.hitEffect.at(hx, hy, b.rotation(), b.type.hitColor);
                    if (b.type.hitSound != null && b.type.hitSound != Sounds.none) {
                        b.type.hitSound.at(hx, hy, Mathf.random(0.9f, 1.1f), b.type.hitSoundVolume);
                    }
                }
                if (splashDmg > 0f && b.type.splashDamageRadius > 0f) {
                    Damage.damage(b.team, hx, hy, b.type.splashDamageRadius, splashDmg,
                        b.type.pierceArmor, b.type.collidesAir, b.type.collidesGround, b.type.scaledSplashDamage, b);
                }
                b.absorb();
                return true;
            }

            float rawDmg = Math.max(0f, b.damage);
            if (rawDmg <= 0f) {
                markBulletHandled(b.id);
                b.absorb();
                return true;
            }
            float shieldCost = rawDmg * mult;
            float absorbedShield = absorbPhysicalShield(shieldCost, hx, hy, angToHit);
            float overflowShield = shieldCost - absorbedShield;

            if (overflowShield <= 0.01f) {
                markBulletHandled(b.id);
                b.absorb();
                return true;
            } else {
                markBulletHandled(b.id);
                b.damage = overflowShield / mult;
                return false;
            }
        }

        @Override
        public void add() {
            super.add();
            if (!activeLancyrons.contains(this, true)) activeLancyrons.add(this);
            if (rollBuffStatus != null) {
                apply(rollBuffStatus, Float.MAX_VALUE);
            }
        }

        @Override
        public void remove() {
            activeLancyrons.remove(this, true);
            super.remove();
        }

        @Override
        public void draw() {
            if (isHiddenFromViewer(this)) return;
            // Bóng quang ảnh kép lệch pha cực ngầu bám sát thân cơ giáp khi đang tàng hình (chỉ đồng minh thấy, KHÔNG dùng hạt tròn quay quanh!)
            if (isStealthed() && lancyron != null && lancyron.region != null && lancyron.region.found()) {
                float frac = Mathf.clamp(stealthTimer / STEALTH_DURATION);
                float shift = (2.2f + 3.8f * frac) * Mathf.sin(Time.time * 0.22f);
                float ox = Angles.trnsx(rotation - 90f, shift);
                float oy = Angles.trnsy(rotation - 90f, shift);
                float zPrev = Draw.z();
                Draw.z(Layer.groundUnit - 0.08f);
                Draw.blend(Blending.additive);
                Draw.color(STEALTH_CYAN, 0.26f * frac);
                Draw.rect(lancyron.region, x + ox, y + oy, rotation - 90f);
                Draw.color(LED_BLUE, 0.22f * frac);
                Draw.rect(lancyron.region, x - ox, y - oy, rotation - 90f);
                Draw.blend();
                Draw.z(zPrev);
                Draw.reset();
            }
            super.draw();
            // Hào quang nhiệt đỏ-vàng rực trên 2 vai vũ khí trong 5s kích hoạt Kỹ năng 1 (Liên Kích)
            if (isOverdriveActive() && !isStealthed()) {
                float ofrac = Mathf.clamp(overdriveTimer / ROLL1_DURATION);
                float pulse = 0.72f + 0.28f * Mathf.sin(Time.time * 0.45f);
                float zPrev = Draw.z();
                Draw.z(Layer.groundUnit + 0.12f);
                Draw.blend(Blending.additive);
                for (int s : Mathf.signs) {
                    float wx = x + Angles.trnsx(rotation - 90f, s * 20.2f, -1.0f);
                    float wy = y + Angles.trnsy(rotation - 90f, s * 20.2f, -1.0f);
                    Draw.color(SOLAR_RED, GOLD_BRIGHT, 0.5f);
                    Draw.alpha(0.62f * pulse * Math.min(1f, ofrac * 3f));
                    Drawf.tri(wx, wy, 5.2f * pulse, 15f * pulse, rotation + 180f - s * 18f);
                }
                Draw.blend();
                Draw.z(zPrev);
                Draw.reset();
            }
            FrontShieldAbility.render(this);
        }

        private void footstepAndThrusterCinematic() {
            if (Vars.headless || isFlying() || isHiddenFromViewer(this)) return;
            if (isStealthed()) {
                // Dải dư ảnh bóng ma cơ giáp (Sandevistan style) lướt theo sau khi tàng hình di chuyển — chỉ phe đồng minh thấy!
                if (vel.len() >= 0.12f) {
                    ghostTrailAcc += Time.delta;
                    if (ghostTrailAcc >= 3.8f) {
                        ghostTrailAcc = 0f;
                        stealthAfterimageFx.at(x, y, rotation, STEALTH_CYAN, team);
                    }
                }
                return;
            }
            float spd = vel.len();
            if (spd >= 0.12f) {
                int step = (int) Math.floor(walkExtend(true) / 2f);
                if (lastStep == -1) {
                    lastStep = step;
                } else if (step != lastStep) {
                    lastStep = step;
                    float side = (step & 1) == 0 ? 1f : -1f;
                    float fx = x + Angles.trnsx(baseRotation + 90f, side * 10f) + Angles.trnsx(baseRotation, 8.5f);
                    float fy = y + Angles.trnsy(baseRotation + 90f, side * 10f) + Angles.trnsy(baseRotation, 8.5f);
                    footstepFx.at(fx, fy, baseRotation);
                    Effect.shake(1.15f, 6f, fx, fy);
                }
            }
            // Phụt hơi/lửa đẩy từ 2 ống thruster sau lưng khi di chuyển (giống ảnh 1 Lancyron)
            if (spd >= 0.25f) {
                thrusterTimer += Time.delta;
                if (thrusterTimer >= 4.5f) {
                    thrusterTimer = 0f;
                    float backAng = rotation + 180f;
                    float side = Mathf.chance(0.5) ? 1f : -1f;
                    float tx = x + Angles.trnsx(backAng, 14.5f) + Angles.trnsx(rotation + 90f, side * 5.5f);
                    float ty = y + Angles.trnsy(backAng, 14.5f) + Angles.trnsy(rotation + 90f, side * 5.5f);
                    thrusterSteamFx.at(tx, ty, backAng + Mathf.range(10f));
                }
            }
        }

        private void suppressStealthWeapons() {
            isShooting = false;
            if (mounts != null) {
                for (WeaponMount m : mounts) {
                    m.shoot = false;
                }
            }
        }

        @Override
        public void update() {
            if (!dead && isAdded() && !activeLancyrons.contains(this, true)) activeLancyrons.add(this);
            flushDeferredLethal();
            ageTicks++;

            if (rollBuffStatus != null && (overdriveTimer > 0f || stealthTimer > 0f) && !hasEffect(rollBuffStatus)) {
                apply(rollBuffStatus, Float.MAX_VALUE);
            }

            // Đếm ngược 5s Kỹ năng 1 (Liên Kích x2 + 10% tốc bắn)
            if (overdriveTimer > 0f) {
                overdriveTimer = Math.max(0f, overdriveTimer - Time.delta);
            }

            // 1) Tiến trình Roll số 1-2-3 trên đầu trong 1 giây (60 tick)
            if (rollTimer > 0f) {
                rollTimer -= Time.delta;
                float elapsed = ROLL_DURATION - Math.max(0f, rollTimer);
                int slot = (int) (elapsed / 4.2f);
                if (slot != lastTickSlot) {
                    lastTickSlot = slot;
                    if (!Vars.headless && PerfCoreSafeOnScreen(x, y)) {
                        newex.SfxCompat.at("message", x, y, 1.35f + (slot % 3) * 0.15f, 0.35f);
                    }
                }
                if (rollTimer <= 0f) {
                    if (!Vars.net.client()) {
                        resolveSkillRollServer();
                    } else {
                        // Trên client đợi gói BCAST_RESOLVE từ server để đồng bộ tuyệt đối
                        rollTimer = 0.01f;
                    }
                }
            }

            if (rollRevealTimer > 0f) {
                rollRevealTimer = Math.max(0f, rollRevealTimer - Time.delta);
            }

            // 2) Đếm ngược Tàng hình 3s (Roll #2) — bản thật đang tàng hình KHÔNG THỂ BẮN!
            if (stealthTimer > 0f) {
                stealthTimer = Math.max(0f, stealthTimer - Time.delta);
                if (isStealthed()) {
                    suppressStealthWeapons();
                }
                if (!Vars.net.client() && (ageTicks & 7) == 0) {
                    breakEnemyTargetLocks(activeCloneId);
                }
            }

            // 3) Đếm ngược thời gian tồn tại & AI chiến đấu tự động của Phân thân (Roll #2)
            if (isClone) {
                cloneLife -= Time.delta;
                if (cloneLife <= 0f) {
                    if (physShieldHp > 0.01f) {
                        shatterShieldCinematic();
                    }
                    cloneDespawnFx.at(x, y, rotation);
                    if (!Vars.net.client()) {
                        remove();
                    }
                    return;
                }
                if (!isPlayer() && !(controller() instanceof CloneCombatAI)) {
                    controller(new CloneCombatAI(rotation));
                }
            }

            // 4) Đếm ngược 1s tái tạo Khiên Vật Lý (Roll #3)
            if (shieldReforgeTimer > 0f) {
                shieldReforgeTimer -= Time.delta;
                if (shieldReforgeTimer <= 0f) {
                    shieldReforgeTimer = 0f;
                    physShieldMax = maxHealth * SHIELD_HP_MULT;
                    physShieldHp = physShieldMax;
                    physShieldDamaged = false;
                    shieldOpen = 1f;
                    shieldFlash = 1f;
                    openHold = 55f;
                    float plateDist = shieldSurfaceDist();
                    float plateX = x + Angles.trnsx(rotation, plateDist);
                    float plateY = y + Angles.trnsy(rotation, plateDist);
                    shieldReforgeCompleteFx.at(plateX, plateY, rotation);
                    Effect.shake(4.5f, 18f, plateX, plateY);
                    if (!Vars.headless) {
                        Sounds.shootSpectre.at(plateX, plateY, 1.18f, 0.92f);
                    }
                }
            }

            footstepAndThrusterCinematic();
            ensureShieldInit();
            if (shieldFlash > 0f) shieldFlash = Math.max(0f, shieldFlash - Time.delta / 10f);

            // Chỉ mở khiên vật lý khi đang bắn (khi tàng hình thì không bắn nên đóng gọn)
            boolean firing = !isStealthed() && isShooting();
            if (!firing && !isStealthed() && mounts != null) {
                for (WeaponMount m : mounts) {
                    if (m.recoil > 0.12f || m.shoot) { firing = true; break; }
                }
            }
            if (firing) openHold = Math.max(openHold, 40f);
            if (openHold > 0f) openHold = Math.max(0f, openHold - Time.delta);
            float targetOpen = (openHold > 0f && !isStealthed()) ? 1f : 0f;
            shieldOpen = Mathf.lerpDelta(shieldOpen, targetOpen, targetOpen > shieldOpen ? 0.18f : 0.07f);

            if (isShieldActive() && !dead) {
                interceptFrontBullets();
            }

            super.update();

            // Khóa chặt cò súng sau cả super.update() để bản thật tuyệt đối không thể bắn khi đang tàng hình
            if (isStealthed()) {
                suppressStealthWeapons();
            }
        }

        private void interceptFrontBullets() {
            float dNose = shieldSurfaceDist();
            float r = dNose + 28f;
            final float plateX = x + Angles.trnsx(rotation, dNose);
            final float plateY = y + Angles.trnsy(rotation, dNose);

            Groups.bullet.intersect(x - r, y - r, r * 2f, r * 2f, b -> {
                if (b == null || !b.isAdded() || b.team == team || !isShieldActive()) return;

                if (b.type instanceof PointLaserBulletType) {
                    float srcX = b.x, srcY = b.y;
                    if (b.owner instanceof Position) {
                        srcX = ((Position) b.owner).getX();
                        srcY = ((Position) b.owner).getY();
                    }
                    if (isAngleCoveredByShield(Angles.angle(x, y, srcX, srcY)) && Mathf.within(x, y, b.aimX, b.aimY, hitSize * 0.85f)) {
                        snapPointToShieldSurface(srcX, srcY);
                        b.aimX = Tmp.v1.x;
                        b.aimY = Tmp.v1.y;
                    }
                    return;
                }

                if (Bulvasyr.isBeamOrInstantType(b.type)) return;
                if (wasBulletHandled(b.id) || wasExplosiveCharged(b.id)) return;

                float step = Math.max(1f, Time.delta);
                boolean hitSeg = doesSegmentHitShieldPlate(
                    b.x - b.vel.x * step, b.y - b.vel.y * step,
                    b.x + b.vel.x * 0.25f, b.y + b.vel.y * 0.25f
                );

                float hx, hy;
                if (hitSeg) {
                    hx = Tmp.v1.x;
                    hy = Tmp.v1.y;
                } else {
                    // Chỉ chặn khi viên đạn đã bay sát vào bên trong bề mặt tấm khiên thực tế (tuyệt đối không nổ xa ngoài không trung!)
                    float dstCenter = Mathf.dst(x, y, b.x, b.y);
                    if (dstCenter > dNose + 1.2f) return;
                    if (!isAngleCoveredByShield(Angles.angle(x, y, b.x, b.y))) return;
                    snapPointToShieldSurface(b.x, b.y);
                    hx = Tmp.v1.x;
                    hy = Tmp.v1.y;
                }

                processShieldProjectile(b, hx, hy);
            });
        }

        @Override
        public boolean canPass(int tileX, int tileY) {
            return super.canPass(tileX, tileY);
        }

        @Override
        public boolean canPassOn() {
            // Khi đang tàng hình hoặc là phân thân mới tạo, cho phép lướt thoát qua phân thân mà không bị kẹt hitbox
            if (isStealthed() || (isClone && cloneLife > CLONE_LIFETIME - 60f)) return true;
            return super.canPassOn();
        }

        @Override
        public void collision(Hitboxc other, float cx, float cy) {
            if (other instanceof Bullet) {
                Bullet b = (Bullet) other;
                if (!dead && isShieldActive() && b.team != team && !Bulvasyr.isBeamOrInstantType(b.type)) {
                    if (isMovingBulletFromShieldSide(b, cx, cy)) {
                        snapPointToShieldSurface(cx, cy);
                        if (processShieldProjectile(b, Tmp.v1.x, Tmp.v1.y)) return;
                    }
                }
            }
            super.collision(other, cx, cy);
        }

        void flushDeferredLethal() {
            if (!deferredLethal || dead) return;
            deferredLethal = false;
            applyingDeferredLethal = true;
            try {
                health = 0f;
                kill();
            } finally {
                applyingDeferredLethal = false;
            }
        }

        private void applyOverflowDamage(float amt) {
            if (amt <= 0f || dead) return;
            applyingDeferredLethal = true;
            try {
                super.rawDamage(amt);
            } finally {
                applyingDeferredLethal = false;
            }
        }

        @Override
        public void rawDamage(float amount) {
            if (applyingDeferredLethal) {
                super.rawDamage(amount);
                return;
            }
            hpBeforeRaw = health;
            shieldBeforeRaw = shield;
            lastRawAmount = amount;
            lastRawTick = (int) Time.time;
            deferredLethal = false;

            if (isShieldActive() && amount > 0f && (health + shield - amount) <= 0f) {
                deferredLethal = true;
                float safeAmt = Math.max(0f, health + shield - 0.01f);
                super.rawDamage(safeAmt);
                if (health <= 0f) health = 0.01f;
                return;
            }

            super.rawDamage(amount);
        }

        public void handleBulletDamageEvent(Bullet b) {
            if (b == null || b.team == team) return;
            if (dead && !deferredLethal) return;
            if (!isShieldActive()) {
                flushDeferredLethal();
                return;
            }

            float lostHp = lastRawTick == (int) Time.time && hpBeforeRaw >= 0f ? Math.max(0f, hpBeforeRaw - health) : 0f;
            float lostEnergyShield = lastRawTick == (int) Time.time && shieldBeforeRaw >= 0f ? Math.max(0f, shieldBeforeRaw - shield) : 0f;
            float totalLost = deferredLethal ? Math.max(lostHp + lostEnergyShield, lastRawAmount) : (lostHp + lostEnergyShield);

            if (wasBulletHandled(b.id)) {
                if (lastRawTick == (int) Time.time && hpBeforeRaw >= 0f) {
                    deferredLethal = false;
                    health = Math.min(maxHealth, hpBeforeRaw);
                    shield = Math.max(shield, shieldBeforeRaw);
                }
                return;
            }

            float plateDist = shieldSurfaceDist();
            float plateX = x + Angles.trnsx(rotation, plateDist);
            float plateY = y + Angles.trnsy(rotation, plateDist);

            if (Bulvasyr.isExplosiveBullet(b.type)) {
                if (!wasExplosiveCharged(b.id) && totalLost > 0f) {
                    markExplosiveCharged(b.id);
                    float ang = Angles.angle(x, y, b.x, b.y);
                    absorbPhysicalShield(totalLost * Bulvasyr.shieldDamageMultiplier(b.type), plateX, plateY, ang);
                }
                flushDeferredLethal();
                return;
            }

            float srcX = b.x, srcY = b.y;
            if (b.owner instanceof Position) {
                Position p = (Position) b.owner;
                srcX = p.getX();
                srcY = p.getY();
            }
            float angToSource = Angles.angle(x, y, srcX, srcY);
            boolean covered = isAngleCoveredByShield(angToSource)
                || (!Bulvasyr.isBeamOrInstantType(b.type) && isMovingBulletFromShieldSide(b, b.x, b.y));
            if (!covered) {
                flushDeferredLethal();
                return;
            }

            if (totalLost <= 0f) return;

            markBeamChargedThisTick(b.id);
            if (b.type instanceof PointLaserBulletType) {
                b.aimX = plateX;
                b.aimY = plateY;
            }

            float mult = Bulvasyr.shieldDamageMultiplier(b.type);
            float absorbedShield = absorbPhysicalShield(totalLost * mult, plateX, plateY, angToSource);
            float rawBlocked = absorbedShield / mult;

            if (deferredLethal) {
                deferredLethal = false;
                float overflowRaw = Math.max(0f, totalLost - rawBlocked);
                health = Math.min(maxHealth, hpBeforeRaw);
                shield = shieldBeforeRaw;
                if (overflowRaw > 0.01f) applyOverflowDamage(overflowRaw);
            } else {
                float refundHp = Math.min(lostHp, rawBlocked);
                health = Math.min(maxHealth, health + refundHp);
                float rem = rawBlocked - refundHp;
                if (rem > 0f && lostEnergyShield > 0f) {
                    shield += Math.min(lostEnergyShield, rem);
                }
            }
        }
    }

    // ================================================================== AI Chiến Đấu Tự Động Cho Phân Thân (Di chuyển & Tấn công như thật!)

    public static class CloneCombatAI extends AIController {
        private float roamAngle;
        private float strafeSign;
        private float strafeSwapTimer = 0f;

        public CloneCombatAI(float initialAngle) {
            this.roamAngle = initialAngle;
            this.strafeSign = Mathf.chance(0.5) ? 1f : -1f;
        }

        @Override
        public void updateUnit() {
            if (unit == null || unit.dead || !unit.isValid()) return;

            if (timer.get(timerTarget, 10f)) {
                target = Units.closestTarget(unit.team, unit.x, unit.y, RANGE * 1.35f,
                    u -> u.checkTarget(true, true) && u.targetable(unit.team),
                    b -> true
                );
                if (target == null) {
                    float mapSearch = Math.max(RANGE * 8f, 4500f);
                    target = Units.closestTarget(unit.team, unit.x, unit.y, mapSearch,
                        u -> u.checkTarget(true, true) && u.targetable(unit.team),
                        b -> true
                    );
                }
            }

            strafeSwapTimer += Time.delta;
            if (strafeSwapTimer >= 95f) {
                strafeSwapTimer = 0f;
                strafeSign = -strafeSign;
            }

            if (target != null) {
                float hitPad = (target instanceof Sized) ? ((Sized) target).hitSize() * 0.5f : 8f;
                float dst = unit.dst(target);
                float ang = unit.angleTo(target);

                if (dst > RANGE * 0.72f) {
                    moveTo(target, RANGE * 0.65f, 14f);
                } else if (dst < RANGE * 0.34f) {
                    unit.movePref(Tmp.v1.trns(ang + 180f + strafeSign * 35f, unit.speed() * 0.92f));
                } else {
                    unit.movePref(Tmp.v1.trns(ang + strafeSign * 82f, unit.speed() * 0.88f));
                }

                unit.lookAt(target);
                boolean inRange = (dst - hitPad) <= RANGE * 1.02f;
                unit.aim(target);
                unit.controlWeapons(true, inRange);
            } else {
                // Nếu chưa có địch trên bản đồ (hoặc test sandbox), phân thân vẫn tự động bước đi tuần tra linh hoạt như người thật
                roamAngle += Mathf.sin(Time.time * 0.035f + unit.id) * 0.48f * Time.delta;
                float mapW = Vars.world != null ? Vars.world.unitWidth() : 0f;
                float mapH = Vars.world != null ? Vars.world.unitHeight() : 0f;
                float pad = 56f;
                if (mapW > pad * 2f && mapH > pad * 2f) {
                    if (unit.x < pad || unit.x > mapW - pad || unit.y < pad || unit.y > mapH - pad) {
                        roamAngle = Angles.moveToward(roamAngle, Angles.angle(unit.x, unit.y, mapW * 0.5f, mapH * 0.5f), 6.5f * Time.delta);
                    }
                }
                Tmp.v1.trns(roamAngle, unit.speed() * 0.92f);
                unit.movePref(Tmp.v1);
                unit.lookAt(unit.x + Tmp.v1.x * 24f, unit.y + Tmp.v1.y * 24f);
                unit.controlWeapons(true, false);
            }
        }
    }

    private static boolean PerfCoreSafeOnScreen(float x, float y) {
        try { return newex.PerfCore.onScreen(x, y, 80f); } catch (Throwable ignored) { return true; }
    }

    // ================================================================== Vẽ Số Roll 1-2-3 & Thanh Thời Gian Hiệu Lực Kỹ Năng Cực Ngầu (KHÔNG dùng hạt tròn bay quanh!)

    private static void drawAllOverheads() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame() || activeLancyrons.isEmpty()) return;

        float zPrev = Draw.z();
        for (int i = 0; i < activeLancyrons.size; i++) {
            LancyronUnit lu = activeLancyrons.get(i);
            if (lu == null || !lu.isAdded() || lu.dead) continue;
            if (!PerfCoreSafeOnScreen(lu.x, lu.y)) continue;

            // Thanh năng lượng thời gian tồn tại phong cách Mecha vát chéo (chỉ phe đồng minh thấy để địch không phân biệt được Phân thân!)
            if (Vars.player != null && lu.team == Vars.player.team()) {
                drawAllySkillDurationBars(lu);
            }

            // Vẽ con số Roll 1-2-3 trên đầu unit (mọi player trong host đều thấy; khi bản thể đang tàng hình thì địch chỉ thấy số trên đầu Phân Thân!)
            if ((lu.rollTimer > 0f || lu.rollRevealTimer > 0f) && !isHiddenFromViewer(lu)) {
                drawUnitRollBadge(lu);
            }
        }
        Draw.z(zPrev);
        Draw.reset();
    }

    /** Thanh đồng hồ năng lượng vát chéo sắc lẹm phong cách sci-fi mecha (thay thế hoàn toàn kiểu hạt tròn bay quanh unit!). */
    private static void drawSlantedGauge(float cx, float cy, float w, float h, float frac, Color mainCol, Color glowCol, String tag) {
        frac = Mathf.clamp(frac);
        float skew = 3.2f;
        float hw = w * 0.5f, hh = h * 0.5f;

        // Nền carbon vát chéo sắc nét
        Draw.color(0.05f, 0.07f, 0.11f, 0.86f);
        Fill.quad(
            cx - hw - 1.4f - skew, cy - hh - 1.2f,
            cx + hw + 1.4f - skew, cy - hh - 1.2f,
            cx + hw + 1.4f + skew, cy + hh + 1.2f,
            cx - hw - 1.4f + skew, cy + hh + 1.2f
        );

        // Lõi thanh năng lượng rút dần mượt mà + chóp sáng rực ở mũi cắt
        float fw = w * frac;
        if (fw > 0.4f) {
            float rx = -hw + fw;
            Draw.color(mainCol, 0.95f);
            Fill.quad(
                cx - hw - skew * 0.8f, cy - hh,
                cx + rx - skew * 0.8f, cy - hh,
                cx + rx + skew * 0.8f, cy + hh,
                cx - hw + skew * 0.8f, cy + hh
            );

            Draw.blend(Blending.additive);
            Draw.color(glowCol, 0.85f);
            float tipW = Math.min(3.2f, fw);
            Fill.quad(
                cx + rx - tipW - skew * 0.8f, cy - hh - 0.6f,
                cx + rx - skew * 0.8f, cy - hh - 0.6f,
                cx + rx + skew * 0.8f, cy + hh + 0.6f,
                cx + rx - tipW + skew * 0.8f, cy + hh + 0.6f
            );
            Draw.blend();
        }

        if (tag != null && !tag.isEmpty()) {
            Font font = Fonts.outline;
            float ox = font.getData().scaleX, oy = font.getData().scaleY;
            font.getData().setScale(0.20f);
            font.setColor(glowCol.r, glowCol.g, glowCol.b, 0.95f);
            font.draw(tag, cx, cy + hh + 5.2f, 0, Align.center, false);
            font.setColor(Color.white);
            font.getData().setScale(ox, oy);
        }
    }

    private static void drawAllySkillDurationBars(LancyronUnit lu) {
        Draw.z(Layer.overlayUI + 0.2f);
        float baseY = lu.y - lu.hitSize * 0.85f - 6.8f;
        float barW = lu.hitSize * 1.16f;

        if (lu.isClone) {
            float sec = Math.max(0f, lu.cloneLife / 60f);
            drawSlantedGauge(lu.x, baseY, barW, 3.2f, lu.cloneLife / CLONE_LIFETIME,
                STEALTH_CYAN, Color.white, "PHÂN THÂN " + Strings.fixed(sec, 1) + "s");
            return;
        }

        if (lu.isStealthed()) {
            float sec = Math.max(0f, lu.stealthTimer / 60f);
            drawSlantedGauge(lu.x, baseY, barW, 3.4f, lu.stealthTimer / STEALTH_DURATION,
                STEALTH_CYAN, Color.white, "TÀNG HÌNH " + Strings.fixed(sec, 1) + "s");
            baseY -= 9.5f;
        }

        if (lu.isOverdriveActive()) {
            float sec = Math.max(0f, lu.overdriveTimer / 60f);
            drawSlantedGauge(lu.x, baseY, barW, 3.2f, lu.overdriveTimer / ROLL1_DURATION,
                SOLAR_RED, GOLD_BRIGHT, "LIÊN KÍCH " + Strings.fixed(sec, 1) + "s");
        }
        Draw.reset();
    }

    private static void drawUnitRollBadge(LancyronUnit lu) {
        Draw.z(Layer.flyingUnit + 4.2f);
        boolean rolling = lu.rollTimer > 0f;
        int digit;
        float progress;
        float bounceScale = 1f;

        if (rolling) {
            progress = 1f - Mathf.clamp(lu.rollTimer / ROLL_DURATION);
            if (progress >= 0.88f) {
                digit = lu.pendingOutcome > 0 ? lu.pendingOutcome : 1;
                bounceScale = 1.25f;
            } else {
                int step = (int) (progress * progress * 14f + progress * 8f) + (lu.id & 3);
                digit = (step % 3) + 1;
                float sub = (progress * 18f) % 1f;
                bounceScale = 0.92f + 0.28f * Mathf.sin(sub * Mathf.PI);
            }
        } else {
            digit = lu.shownOutcome > 0 ? lu.shownOutcome : 1;
            progress = 1f;
            float revProg = 1f - Mathf.clamp(lu.rollRevealTimer / 105f);
            if (revProg < 0.18f) {
                bounceScale = 1.45f - 0.45f * Interp.pow2Out.apply(revProg / 0.18f);
            }
        }

        float fade = rolling ? 1f : Mathf.clamp(lu.rollRevealTimer / 22f);
        Color accent = digit == 1 ? SOLAR_RED : (digit == 2 ? STEALTH_CYAN : AEGIS_GREEN);
        float bx = lu.x;
        float by = lu.y + lu.hitSize * 0.78f + 19f;
        float rad = 13.0f * bounceScale;

        // 1) Khung huy hiệu tối viền sáng mịn (tuyệt đối KHÔNG dùng hạt tròn quay quanh hay đường thẳng quanh vòng tròn!)
        Draw.color(0.04f, 0.05f, 0.09f, 0.84f * fade);
        Fill.circle(bx, by, rad + 2f);

        Draw.blend(Blending.additive);
        Draw.color(accent, 0.30f * fade);
        Fill.circle(bx, by, rad);

        Lines.stroke(2.0f * fade);
        Draw.color(rolling ? GOLD_BRIGHT : accent, 0.95f * fade);
        Lines.circle(bx, by, rad);

        Lines.stroke(1.1f * fade);
        Draw.color(GOLD_CORE, (rolling ? (0.45f + 0.35f * Mathf.sin(Time.time * 0.4f)) : 0.80f) * fade);
        Lines.circle(bx, by, rad + 2.6f);
        Draw.blend();

        // 2) Vẽ con số 1 / 2 / 3 to rõ ở chính giữa khung trên đầu unit
        Font font = Fonts.outline;
        float oldX = font.getData().scaleX, oldY = font.getData().scaleY;
        font.getData().setScale(0.62f * bounceScale);
        font.setColor(GOLD_CORE.r, GOLD_CORE.g, GOLD_CORE.b, fade);
        font.draw(String.valueOf(digit), bx, by + font.getCapHeight() * 0.48f, 0, Align.center, false);

        // 3) Dòng mô tả ngắn ngay dưới con số sau khi quay xong
        if (!rolling && lu.revealSubText != null && !lu.revealSubText.isEmpty()) {
            font.getData().setScale(0.24f);
            font.setColor(accent.r, accent.g, accent.b, fade);
            font.draw(lu.revealSubText, bx, by - rad - 4.5f, 0, Align.center, false);
        }

        font.setColor(Color.white);
        font.getData().setScale(oldX, oldY);
        Draw.reset();
    }

    // ================================================================== Nút HUD Kéo Thả (Hồi chiêu 5s)

    private static void clampHud(Table t) {
        t.setPosition(
            Mathf.clamp(t.x, 0f, Math.max(0f, Core.graphics.getWidth() - t.getWidth())),
            Mathf.clamp(t.y, 0f, Math.max(0f, Core.graphics.getHeight() - t.getHeight()))
        );
    }

    private static void buildHudButton() {
        if (hudBtn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        Table t = new Table(Styles.black6);
        hudBtn = t;
        t.name = "newex-lancyron-skill-btn";
        t.margin(6f);
        t.touchable = Touchable.enabled;

        float sz = Vars.mobile ? 56f : 48f;
        if (lancyron != null && lancyron.uiIcon != null) {
            t.image(lancyron.uiIcon).size(sz).row();
        }
        Label lbl = t.add("").get();
        lbl.setFontScale(0.88f);
        t.row();
        Label subLbl = t.add("").get();
        subLbl.setFontScale(0.72f);

        t.update(() -> {
            Unit u = Vars.player != null ? Vars.player.unit() : null;
            LancyronUnit lu = (u instanceof LancyronUnit) ? (LancyronUnit) u : null;
            float left = localCdEnd - Time.time;

            if (lu != null && lu.rollTimer > 0f) {
                lbl.setText("ROLL...");
                lbl.setColor(GOLD_BRIGHT);
            } else if (left > 0f) {
                lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s");
                lbl.setColor(Color.gray);
            } else {
                lbl.setText(Vars.mobile ? "ROLL 1-3" : "ROLL [" + getSkillKey().value + "]");
                lbl.setColor(GOLD_MID);
            }

            if (lu != null && lu.isStealthed()) {
                subLbl.setText("ẨN " + Strings.fixed(lu.stealthTimer / 60f, 1) + "s");
                subLbl.setColor(STEALTH_CYAN);
            } else if (lu != null && lu.isOverdriveActive()) {
                subLbl.setText("x2 " + Strings.fixed(lu.overdriveTimer / 60f, 1) + "s");
                subLbl.setColor(SOLAR_RED);
            } else {
                subLbl.setText("5s CD");
                subLbl.setColor(Color.lightGray);
            }
        });

        t.visible(() -> Vars.ui.hudfrag.shown && Vars.player != null && isLancyron(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();

        if (Core.settings.has(HUD_KEY + "-x") && Core.settings.has(HUD_KEY + "-y")) {
            t.setPosition(Core.settings.getInt(HUD_KEY + "-x"), Core.settings.getInt(HUD_KEY + "-y"));
        } else {
            t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20f, Core.graphics.getHeight() / 3f + 95f);
        }
        clampHud(t);

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
                    t.setPosition(t.x + x - offX, t.y + y - offY);
                    clampHud(t);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging) {
                    Core.settings.put(HUD_KEY + "-x", Math.round(t.x));
                    Core.settings.put(HUD_KEY + "-y", Math.round(t.y));
                } else {
                    castSkillFromClient();
                }
                dragging = false;
            }
        });

        Vars.ui.hudGroup.addChild(t);
    }

    // ================================================================== Nội tại: Vẽ & Animation Khiên Vật Lý Trước Ngực Tách Lớp + Hologram Tái Đúc 1s

    public static class FrontShieldAbility extends Ability {
        @Override
        public void draw(Unit unit) { /* vẽ trực tiếp trong LancyronUnit.draw() để luôn khớp lớp độ sâu */ }

        public static void render(Unit unit) {
            if (!(unit instanceof LancyronUnit)) return;
            LancyronUnit lu = (LancyronUnit) unit;
            if (isHiddenFromViewer(lu)) return;
            lu.ensureShieldInit();

            if (!Vars.headless) {
                if (shieldCenterRegion == null) shieldCenterRegion = Core.atlas.find("newex-lancyron-shield");
                if (shieldWingRegion == null) shieldWingRegion = Core.atlas.find("newex-lancyron-shield-wing");
            }

            // Nếu đang trong 1s đợi tái tạo khiên (Roll #3): vẽ hiệu ứng Hologram đúc khiên bằng tia laser vàng kim trước ngực!
            if (lu.shieldReforgeTimer > 0f) {
                renderReforgeHologram(lu);
                return;
            }

            if (!lu.isShieldActive()) return;

            float ratio = lu.shieldRatio();
            float rot = lu.rotation;
            float fwdX = Angles.trnsx(rot, 1f), fwdY = Angles.trnsy(rot, 1f);
            float rightX = Angles.trnsx(rot - 90f, 1f), rightY = Angles.trnsy(rot - 90f, 1f);

            boolean stealthed = lu.isStealthed();
            float stealthA = stealthed ? (0.36f + 0.10f * Mathf.sin(Time.time * 0.25f)) : 1f;

            // Khi bắn: khiên đẩy tiến & mở 2 cánh giáp bản lề như bản cũ
            float open = lu.shieldOpen;
            float sx = lu.x + fwdX * (15.8f + open * 3.4f);
            float sy = lu.y + fwdY * (15.8f + open * 3.4f);

            float zPrev = Draw.z();
            Draw.z(Layer.groundUnit + 0.05f);

            Color tint = stealthed
                ? Tmp.c1.set(STEALTH_CYAN).lerp(GOLD_BRIGHT, 0.3f)
                : Tmp.c1.set(Color.white).lerp(GOLD_CORE, lu.shieldFlash * 0.72f);
            if (!stealthed && lu.shieldFlash > 0.01f) {
                Draw.mixcol(GOLD_CORE, lu.shieldFlash * 0.55f);
            }

            // 1) Vẽ 2 cánh giáp bản lề trái/phải mở rộng khi bắn (như khiên vật lý Bulvasyr, KHÔNG vẽ đường thẳng lên mặt khiên!)
            if (shieldWingRegion != null && shieldWingRegion.found()) {
                float wingOut = 10.6f + open * 2.1f;
                float wingBack = -2.8f + open * 1.1f;
                float wingAngle = 6f + open * 18f;
                float ww = shieldWingRegion.width * Draw.scl;
                float wh = shieldWingRegion.height * Draw.scl;

                Draw.color(tint, stealthA);
                float rx = sx + rightX * wingOut + fwdX * wingBack;
                float ry = sy + rightY * wingOut + fwdY * wingBack;
                Draw.rect(shieldWingRegion, rx, ry, ww, wh, rot - 90f - wingAngle);

                float lx = sx - rightX * wingOut + fwdX * wingBack;
                float ly = sy - rightY * wingOut + fwdY * wingBack;
                Draw.rect(shieldWingRegion, lx, ly, -ww, wh, rot - 90f + wingAngle);
            }

            // 2) Vẽ tấm khiên chính trước ngực
            if (shieldCenterRegion != null && shieldCenterRegion.found()) {
                Draw.color(tint, stealthA);
                Draw.rect(shieldCenterRegion, sx, sy, rot - 90f);
            }
            Draw.mixcol();

            // Thanh độ bền khiên (chỉ đồng minh thấy, giống Bulvasyr)
            if (Vars.player != null && lu.team == Vars.player.team()) {
                Draw.z(Layer.overlayUI);
                float bw = lu.hitSize * 1.18f;
                float bh = 3.2f;
                float by = lu.y - lu.hitSize * 0.85f;
                Draw.color(Color.black, 0.65f);
                Fill.rect(lu.x, by, bw + 2f, bh + 2f);
                Draw.color(ratio > 0.3f ? GOLD_MID : SOLAR_RED, 0.95f);
                Fill.rect(lu.x - (bw * (1f - ratio)) * 0.5f, by, bw * ratio, bh);
            }

            Draw.z(zPrev);
            Draw.reset();
        }

        /** Hiệu ứng Hologram 1 giây đúc lại tấm khiên vật lý trước ngực (Roll #3, không dùng tia đường thẳng). */
        private static void renderReforgeHologram(LancyronUnit lu) {
            float prog = 1f - Mathf.clamp(lu.shieldReforgeTimer / SHIELD_REFORGE_DELAY);
            float ease = Interp.pow3Out.apply(prog);
            float rot = lu.rotation;
            float fwdX = Angles.trnsx(rot, 1f), fwdY = Angles.trnsy(rot, 1f);
            float rightX = Angles.trnsx(rot - 90f, 1f), rightY = Angles.trnsy(rot - 90f, 1f);

            float sx = lu.x + fwdX * (25f - 8.5f * ease);
            float sy = lu.y + fwdY * (25f - 8.5f * ease);

            float zPrev = Draw.z();
            Draw.z(Layer.groundUnit + 0.08f);
            Draw.blend(Blending.additive);

            // 1) Quầng sáng hội tụ tại tâm khiên đang đúc (không kẻ đường thẳng từ ngực tới vòng tròn)
            float scanSweep = Mathf.sin(prog * Mathf.PI * 3f) * 11f;
            float scanX = sx + rightX * scanSweep;
            float scanY = sy + rightY * scanSweep;
            Draw.color(GOLD_BRIGHT, 0.45f);
            Fill.circle(scanX, scanY, 5.2f);
            Draw.color(GOLD_CORE, 0.95f);
            Fill.circle(scanX, scanY, 2.4f);

            // 2) 3 mảnh khiên hologram (2 cánh + tấm giữa) bay hội tụ vào khớp bản lề
            float holoAlpha = 0.25f + 0.65f * prog;
            Color holoCol = Tmp.c1.set(AEGIS_GREEN).lerp(GOLD_BRIGHT, prog);
            if (shieldWingRegion != null && shieldWingRegion.found()) {
                float wingOut = 10.6f + (1f - ease) * 14f;
                float wingBack = -2.2f + (1f - ease) * 6f;
                float wingAngle = 14f + (1f - ease) * 38f;
                float ww = shieldWingRegion.width * Draw.scl;
                float wh = shieldWingRegion.height * Draw.scl;

                Draw.color(holoCol, holoAlpha);
                Draw.rect(shieldWingRegion, sx + rightX * wingOut + fwdX * wingBack, sy + rightY * wingOut + fwdY * wingBack, ww, wh, rot - 90f - wingAngle);
                Draw.rect(shieldWingRegion, sx - rightX * wingOut + fwdX * wingBack, sy - rightY * wingOut + fwdY * wingBack, -ww, wh, rot - 90f + wingAngle);
            }
            if (shieldCenterRegion != null && shieldCenterRegion.found()) {
                Draw.color(holoCol, holoAlpha);
                Draw.rect(shieldCenterRegion, sx, sy, rot - 90f);
            }

            // Thanh tiến độ đúc khiên 1s dưới chân
            if (Vars.player != null && lu.team == Vars.player.team()) {
                Draw.z(Layer.overlayUI);
                float bw = lu.hitSize * 1.18f;
                float bh = 3.2f;
                float by = lu.y - lu.hitSize * 0.85f;
                Draw.blend();
                Draw.color(Color.black, 0.65f);
                Fill.rect(lu.x, by, bw + 2f, bh + 2f);
                Draw.color(AEGIS_GREEN, GOLD_BRIGHT, prog);
                Fill.rect(lu.x - (bw * (1f - prog)) * 0.5f, by, bw * prog, bh);
            }

            Draw.blend();
            Draw.z(zPrev);
            Draw.reset();
        }

        @Override
        public String localized() {
            return "Khiên Vật Lý Trước Ngực (Lancyron Aegis)";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Độ bền khiên trước ngực: [accent]" + Math.round(SHIELD_HP_MULT * 100f) + "% HP").row();
            t.add("[lightgray]Góc che chắn chính diện: [white]±" + Math.round(SHIELD_HALF_ARC) + "°").row();
            t.add("[lightgray]Tầm bắn (+22% Bulvasyr): [white]" + Strings.autoFixed(RANGE / Vars.tilesize, 1) + " ô").row();
            t.add("[lightgray]Kỹ năng nút (5s CD): [gold]Roll 1-2-3 trên đầu trong 1s[] (1: Trong 5s +10% tốc bắn & Liên kích x2 | 2: Phân thân tự đánh & Tàng hình 3s +50% tốc, không bắn | 3: Phá khiên bắn mảnh vỡ về trước, máu <99% +5000 Max HP, khiên <99% đợi 1s hồi khiên)");
        }
    }
}
