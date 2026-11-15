package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.LegsUnit;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.entities.units.WeaponMount;
import mindustry.ui.Styles;
import newex.PerfCore;

import java.util.HashMap;

/**
 * Chuyển từ suv-27.js. Hai đơn vị do hjson tạo (suv-27 bay, vus-27 chân); Java vá lúc ClientLoadEvent:
 * đổi `constructor` sang lớp con có kỹ năng, 3 nút kỹ năng + cần điều khiển trên điện thoại, lệnh chat /suv_action.
 */
public final class Suv27 {
    private Suv27() {}

    static final float TRANSFORM_DELAY = 60f, TRANSFORM_COOLDOWN = 180f, CONE_SHOT_COOLDOWN = 180f, HYBRID_SKILL_COOLDOWN = 60f, DOUBLE_TAP_COOLDOWN = 60f;

    static BulletType suvShotgunBulletType = null, pulseBulletType = null, rapid8mmBulletType = null;
    static Effect pulseBurstEffect = null, healEffect = null;
    static float moveX = 0f, moveY = 0f;

    static final class UnitData {
        int level = 0, maxLevel = 10, leadAbsorbed = 0, siliconAbsorbed = 0, gunMode = 0;
        float transformTimer = 0f, transformCooldown = 0f, coneShotCooldown = 0f, hybridSkillCooldown = 0f, doubleTapCooldown = 0f;
        float firingDuration = 0f, overdriveTimer = 0f, autoPulseTimer = 0f, flightAccelTimer = 0f;
        boolean wasMoving = false;
        long lastTapTimeInternal = 0L;
    }
    static final HashMap<Integer, UnitData> unitDataMap = new HashMap<>();

    static UnitData getUnitData(Unit unit) {
        if (unit == null) return null;
        UnitData d = unitDataMap.get(unit.id);
        if (d == null) { d = new UnitData(); unitDataMap.put(unit.id, d); }
        return d;
    }

    static int leadNeeded(int level) { return 340 + level * 100; }
    static int siliconNeeded(int level) { return 400 + level * 120; }

    // ------------------------------------------------------------------ khởi tạo

    public static void init() {
        Events.on(PlayerChatEvent.class, event -> {
            if (event.message != null && event.message.startsWith("/suv_action ")) {
                String action = event.message.replace("/suv_action ", "").trim();
                Player player = event.player;
                if (player != null && player.unit() != null) executeUnitAction(player.unit(), action);
            }
        });
        Events.on(ClientLoadEvent.class, e -> setup());
    }

    static UnitType unitByName(String name) {
        UnitType t = Vars.content.getByName(ContentType.unit, "newex-" + name);
        if (t == null) t = Vars.content.getByName(ContentType.unit, name);
        return t;
    }

    private static void setup() {
        pulseBurstEffect = new Effect(30f, e -> {
            Draw.color(PerfCore.color("8aa3f4"), PerfCore.color("c084fc"), e.fin());
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, 60f * e.fin());
            Draw.color(Color.white, e.fout());
            Fill.circle(e.x, e.y, 18f * e.fout());
        });
        healEffect = new Effect(40f, e -> {
            Draw.color(PerfCore.color("84cc16"), Color.white, e.fin());
            Lines.stroke(3f * e.fout());
            Lines.circle(e.x, e.y, 25f * e.fin());
            Fill.circle(e.x, e.y, 10f * e.fout());
        });

        BasicBulletType pulse = new BasicBulletType(3.5f, 25f);
        pulse.sprite = "circle"; pulse.lifetime = 115f; pulse.drag = -0.01f; pulse.width = 9f; pulse.height = 9f;
        pulse.hitColor = Color.valueOf("8aa3f4"); pulse.backColor = Color.valueOf("8aa3f4"); pulse.frontColor = Color.white;
        pulse.lightning = 3; pulse.lightningLength = 12; pulse.lightningColor = Color.valueOf("8aa3f4"); pulse.lightningDamage = 8f;
        pulse.load();
        pulseBulletType = pulse;

        BasicBulletType rapid = new BasicBulletType(12f, 45f);
        rapid.lifetime = 50f; rapid.width = 4f; rapid.height = 14f;
        rapid.frontColor = Color.white; rapid.backColor = Color.valueOf("f59e0b");
        rapid.trailLength = 8; rapid.trailWidth = 2f; rapid.trailColor = Color.valueOf("f59e0b");
        rapid.load();
        rapid8mmBulletType = rapid;

        UnitType flareType = Vars.content.getByName(ContentType.unit, "flare");
        if (flareType != null && flareType.weapons.size > 0) {
            suvShotgunBulletType = flareType.weapons.get(0).bullet.copy();
            suvShotgunBulletType.damage = 20f; suvShotgunBulletType.speed = 5.5f; suvShotgunBulletType.lifetime = 60f;
        }

        for (String name : new String[]{"suv-27", "newex-suv-27"}) {
            UnitType uType = Vars.content.getByName(ContentType.unit, name);
            if (uType != null) {
                uType.health = 780f;
                uType.omniMovement = true;
                uType.constructor = SuvUnit::new;
            }
        }

        for (String name : new String[]{"vus-27", "newex-vus-27"}) {
            UnitType uType = Vars.content.getByName(ContentType.unit, name);
            if (uType != null) {
                uType.health = 24000f;
                uType.omniMovement = true;
                uType.constructor = VusUnit::new;

                if (uType.weapons.size >= 2) {
                    Weapon wLeft = uType.weapons.get(0);
                    wLeft.reload = 10f;
                    if (flareType != null && flareType.weapons.size > 0) {
                        BulletType bLeft = flareType.weapons.get(0).bullet.copy();
                        bLeft.damage = 25f; bLeft.speed = 6.5f; bLeft.lifetime = 35f;
                        // BulletType không có frontColor/backColor (chỉ BasicBulletType có): bài học 33
                        if (bLeft instanceof BasicBulletType) {
                            ((BasicBulletType) bLeft).frontColor = Color.white;
                            ((BasicBulletType) bLeft).backColor = Color.valueOf("c084fc");
                        }
                        wLeft.bullet = bLeft;
                    }
                    Weapon wRight = uType.weapons.get(1);
                    wRight.reload = 72f;
                    LaserBulletType bRight = new LaserBulletType(160f);
                    bRight.length = 240f; bRight.width = 20f;
                    bRight.colors = new Color[]{Color.valueOf("7e22ce"), Color.valueOf("c084fc"), Color.white};
                    wRight.bullet = bRight;
                }
            }
        }

        buildHud();
    }

    // ------------------------------------------------------------------ kỹ năng

    static void sendSkillCommand(String action) {
        if (Vars.net.client()) {
            Call.sendChatMessage("/suv_action " + action);
        } else if (Vars.player != null && Vars.player.unit() != null) {
            executeUnitAction(Vars.player.unit(), action);
        }
    }

    static boolean isSuv(Unit unit) {
        return unit.type != null && unit.type.name != null && unit.type.name.contains("suv-27") && !unit.type.name.contains("vus-27");
    }

    static void transformToUnit(Unit oldUnit, String targetUnitName, float remainingCooldown) {
        if (Vars.net.client() || oldUnit == null || !oldUnit.isValid()) return;
        UnitType targetType = unitByName(targetUnitName);
        if (targetType == null) return;

        UnitData oldData = getUnitData(oldUnit);
        int savedLevel = oldData != null ? oldData.level : 0;
        int savedLead = oldData != null ? oldData.leadAbsorbed : 0;
        int savedSilicon = oldData != null ? oldData.siliconAbsorbed : 0;

        final Unit old = oldUnit;
        Player controllingPlayer = Groups.player.find(p -> p.unit() == old);

        float oldX = oldUnit.x, oldY = oldUnit.y, oldRot = oldUnit.rotation;
        float healthPercent = oldUnit.maxHealth > 0f ? (oldUnit.health / oldUnit.maxHealth) : 1f;

        Unit newUnit = targetType.create(oldUnit.team);
        newUnit.set(oldX, oldY);
        newUnit.rotation = oldRot;
        newUnit.health = newUnit.maxHealth * healthPercent;
        newUnit.add();

        if (controllingPlayer != null) controllingPlayer.unit(newUnit);

        unitDataMap.remove(oldUnit.id);
        Call.unitDestroy(oldUnit.id);
        if (oldUnit.isAdded()) oldUnit.remove();

        UnitData newData = getUnitData(newUnit);
        if (newData != null) {
            newData.level = savedLevel;
            newData.leadAbsorbed = savedLead;
            newData.siliconAbsorbed = savedSilicon;
            newData.transformCooldown = remainingCooldown;
        }
    }

    static void fireSUVShotgun(Unit unit) {
        if (suvShotgunBulletType == null) return;
        int bulletCount = (int) Mathf.random(12f, 16f);
        for (int i = 0; i < bulletCount; i++) {
            float spreadAngle = unit.rotation + Mathf.range(10f);
            Call.createBullet(suvShotgunBulletType, unit.team, unit.x, unit.y, spreadAngle, suvShotgunBulletType.damage, 1f, 1f);
        }
    }

    static void executeUnitAction(Unit unit, String action) {
        if (unit == null) return;
        UnitData data = getUnitData(unit);
        if (data == null) return;
        boolean isSUV = isSuv(unit);

        if (action.equals("transform")) {
            if (data.transformCooldown <= 0f && data.transformTimer <= 0f) {
                if (isSUV && data.flightAccelTimer > 30f) fireSUVShotgun(unit);
                data.transformTimer = TRANSFORM_DELAY;
                data.transformCooldown = TRANSFORM_COOLDOWN;
            }
        } else if (action.equals("pulse_cone")) {
            if (data.coneShotCooldown <= 0f) {
                data.coneShotCooldown = CONE_SHOT_COOLDOWN;
                if (pulseBurstEffect != null) Call.effect(pulseBurstEffect, unit.x, unit.y, 0f, Color.white);
                if (pulseBulletType != null) {
                    float spread = 12f;
                    for (int i = 0; i < 20; i++) {
                        float fireAngle = unit.rotation + Mathf.range(spread / 2f);
                        Call.createBullet(pulseBulletType, unit.team, unit.x, unit.y, fireAngle, pulseBulletType.damage, 1f, 1f);
                    }
                }
            }
        } else if (action.equals("hybrid_skill")) {
            if (data.hybridSkillCooldown <= 0f) {
                if (isSUV) {
                    data.gunMode = data.gunMode == 0 ? 1 : 0;
                    data.hybridSkillCooldown = HYBRID_SKILL_COOLDOWN;
                } else {
                    data.hybridSkillCooldown = HYBRID_SKILL_COOLDOWN;
                    unit.health = Math.min(unit.maxHealth, unit.health + unit.maxHealth * 0.01f);
                    if (healEffect != null) Call.effect(healEffect, unit.x, unit.y, 0f, Color.white);
                }
            }
        } else if (action.equals("double_tap")) {
            if (data.doubleTapCooldown <= 0f) {
                data.doubleTapCooldown = DOUBLE_TAP_COOLDOWN;
                if (isSUV) {
                    UnitType rocketType = unitByName("vus-27-rocket");
                    if (rocketType != null) {
                        try {
                            float sideOffset = 12f;
                            Unit r1 = rocketType.create(unit.team);
                            r1.set(unit.x + Angles.trnsx(unit.rotation + 90f, sideOffset), unit.y + Angles.trnsy(unit.rotation + 90f, sideOffset));
                            r1.rotation = unit.rotation;
                            r1.add();
                            Unit r2 = rocketType.create(unit.team);
                            r2.set(unit.x + Angles.trnsx(unit.rotation - 90f, sideOffset), unit.y + Angles.trnsy(unit.rotation - 90f, sideOffset));
                            r2.rotation = unit.rotation;
                            r2.add();
                        } catch (Exception e) {}
                    }
                    Call.effect(Fx.shootBig, unit.x, unit.y, unit.rotation, Color.white);
                } else {
                    unit.vel.add(Angles.trnsx(unit.rotation, 8f), Angles.trnsy(unit.rotation, 8f));
                    unit.health = Math.min(unit.maxHealth, unit.health + unit.maxHealth * 0.01f);
                    Call.effect(Fx.sparkExplosion, unit.x, unit.y, unit.rotation, Color.white);
                    if (healEffect != null) Call.effect(healEffect, unit.x, unit.y, 0f, Color.white);
                }
            }
        }
    }

    // ------------------------------------------------------------------ logic dùng chung cho 2 lớp đơn vị (applyTransformLogic)

    /** Thân của update() JS (sau super.update()). */
    static void updateLogic(Unit self, boolean isSUV, String targetUnitName) {
        UnitData data = getUnitData(self);
        if (data == null) return;

        if (data.transformCooldown > 0f) data.transformCooldown -= Time.delta;
        if (data.coneShotCooldown > 0f) data.coneShotCooldown -= Time.delta;
        if (data.hybridSkillCooldown > 0f) data.hybridSkillCooldown -= Time.delta;
        if (data.doubleTapCooldown > 0f) data.doubleTapCooldown -= Time.delta;

        if (data.transformTimer > 0f) {
            data.transformTimer -= Time.delta;
            self.vel.set(0f, 0f);
            if (data.transformTimer <= 0f) {
                data.transformTimer = 0f;
                if (!Vars.net.client()) transformToUnit(self, targetUnitName, data.transformCooldown);
                return;
            }
        }

        if (Vars.player != null && Vars.player.unit() == self) {
            if (Core.input.keyTap(KeyCode.num1)) sendSkillCommand("transform");
            if (Core.input.keyTap(KeyCode.num2)) sendSkillCommand("pulse_cone");
            if (Core.input.keyTap(KeyCode.num3)) sendSkillCommand("hybrid_skill");

            if (Core.input.justTouched()) {
                long now = Time.millis();
                if (now - data.lastTapTimeInternal < 300L) sendSkillCommand("double_tap");
                data.lastTapTimeInternal = now;
            }

            if (Vars.mobile) {
                Core.camera.position.set(self.x, self.y);
                if (moveX != 0f || moveY != 0f) {
                    float moveSpeed = self.speed();
                    self.moveAt(Tmp.v1.set(moveX, moveY).setLength(moveSpeed));
                }
            }
        }

        if (Vars.net.client()) return;

        float currentRange = isSUV ? (data.gunMode == 1 ? 600f : 400f) : 200f;
        final Unit me = self;
        mindustry.gen.Teamc tgt = Units.closestTarget(self.team, self.x, self.y, currentRange, u -> u.checkTarget(me.type.targetAir, me.type.targetGround));

        if (tgt != null) {
            float tx = tgt.getX(), ty = tgt.getY();
            float targetAngle = Angles.angle(self.x, self.y, tx, ty);
            self.rotation = Angles.moveToward(self.rotation, targetAngle, self.type.rotateSpeed * Time.delta);

            self.aim(tx, ty);
            self.isShooting = true;

            if (isSUV) {
                data.autoPulseTimer += Time.delta;
                if (data.gunMode == 0) {
                    if (data.autoPulseTimer >= 30f) {
                        data.autoPulseTimer = 0f;
                        if (pulseBulletType != null) Call.createBullet(pulseBulletType, self.team, self.x, self.y, targetAngle, pulseBulletType.damage, 1f, 1f);
                    }
                } else if (data.autoPulseTimer >= 5f) {
                    data.autoPulseTimer = 0f;
                    if (rapid8mmBulletType != null) {
                        float spreadAngle = targetAngle + Mathf.range(3.5f);
                        Call.createBullet(rapid8mmBulletType, self.team, self.x, self.y, spreadAngle, rapid8mmBulletType.damage, 1f, 1f);
                    }
                }
            }

            if (!isSUV && self.mounts != null && self.mounts.length > 0) {
                for (WeaponMount mount : self.mounts) {
                    mount.shoot = true; mount.rotate = true; mount.aimX = tx; mount.aimY = ty;
                }
            }
        } else {
            if (Vars.mobile && (moveX != 0f || moveY != 0f)) {
                float moveAngle = Tmp.v1.set(moveX, moveY).angle();
                self.rotation = Angles.moveToward(self.rotation, moveAngle, self.type.rotateSpeed * Time.delta);
            }
            self.isShooting = false;
            data.autoPulseTimer = 0f;
            if (self.mounts != null && self.mounts.length > 0) {
                for (WeaponMount mount : self.mounts) mount.shoot = false;
            }
        }

        if (!isSUV) {
            if (self.isShooting) {
                if (data.overdriveTimer <= 0f) {
                    data.firingDuration += Time.delta;
                    if (data.firingDuration >= 120f) {
                        data.overdriveTimer = 60f;
                        data.firingDuration = 0f;
                    }
                }
            } else {
                data.firingDuration = Math.max(0f, data.firingDuration - Time.delta * 2f);
            }

            if (data.overdriveTimer > 0f) {
                data.overdriveTimer -= Time.delta;
                if (self.mounts != null && self.mounts.length > 0) {
                    for (WeaponMount mount : self.mounts) {
                        if (mount.reload > 0f) mount.reload = Math.max(0f, mount.reload - (Time.delta * 4f));
                    }
                }
            }
        }

        if (isSUV) {
            boolean isCurrentlyMoving = self.moving() || (self.vel != null && self.vel.len() > 0.2f);
            if (isCurrentlyMoving) {
                data.flightAccelTimer = Math.min(180f, data.flightAccelTimer + Time.delta);
                data.wasMoving = true;
            } else {
                if (data.wasMoving && data.flightAccelTimer > 30f) fireSUVShotgun(self);
                data.flightAccelTimer = 0f;
                data.wasMoving = false;
            }
        }

        int leadNeed = leadNeeded(data.level), siliconNeed = siliconNeeded(data.level);
        if (data.level < data.maxLevel && self.stack != null) {
            if (data.leadAbsorbed < leadNeed && self.stack.item == Items.lead && self.stack.amount > 0) {
                int consumeAmt = Math.min(2, self.stack.amount);
                self.stack.amount -= consumeAmt;
                data.leadAbsorbed += consumeAmt;
            } else if (data.siliconAbsorbed < siliconNeed && self.stack.item == Items.silicon && self.stack.amount > 0) {
                int consumeAmt = Math.min(2, self.stack.amount);
                self.stack.amount -= consumeAmt;
                data.siliconAbsorbed += consumeAmt;
            }

            if (data.leadAbsorbed >= leadNeed && data.siliconAbsorbed >= siliconNeed) {
                data.leadAbsorbed = 0;
                data.siliconAbsorbed = 0;
                data.level++;
            }
        }
    }

    /** Thân của draw() JS (sau super.draw()). */
    static void drawLogic(Unit self, boolean isSUV) {
        UnitData data = getUnitData(self);
        if (data == null) return;

        if (Vars.player != null && Vars.player.unit() == self) {
            float currentRange = isSUV ? (data.gunMode == 1 ? 600f : 400f) : 200f;
            Draw.z(Layer.power + 10f);
            Draw.color(data.gunMode == 1 ? Color.valueOf("f59e0b") : Color.valueOf("c084fc"), 0.45f);
            Lines.stroke(2f);
            Lines.dashCircle(self.x, self.y, currentRange);
            Draw.reset();
        }

        if (data.transformTimer > 0f) {
            float progress = 1f - (data.transformTimer / TRANSFORM_DELAY);
            float scale = (float) Math.sin(progress * Math.PI);
            float maxRadius = 26f * scale;

            Draw.z(Layer.effect + 0.1f);
            Draw.color(PerfCore.color("c084fc"));
            Lines.stroke(3f * scale);
            Lines.circle(self.x, self.y, maxRadius + 2f);
            Draw.color(PerfCore.color("7e22ce"));
            Fill.circle(self.x, self.y, maxRadius);
            Draw.color(Color.black);
            Fill.circle(self.x, self.y, maxRadius * 0.60f);
            Draw.reset();
        }
    }

    static float levelMaxHealth(Unit self) {
        UnitData data = getUnitData(self);
        int lv = data != null ? data.level : 0;
        return self.type.health * (1f + lv * 0.20f);
    }

    static float levelSpeed(Unit self, boolean isSUV) {
        UnitData data = getUnitData(self);
        int lv = data != null ? data.level : 0;
        float baseSpeed = self.type.speed * (1f + lv * 0.08f);
        if (isSUV && data != null) {
            float accelBonus = (data.flightAccelTimer / 180f) * 3f;
            return baseSpeed * (1f + accelBonus);
        }
        return baseSpeed;
    }

    /** JS: extend(UnitEntity, applyTransformLogic(...)). classId kế thừa của UnitEntity nên lưu/nạp y như bản JS. */
    public static class SuvUnit extends UnitEntity {
        @Override public void update() { super.update(); updateLogic(this, true, "vus-27"); }
        @Override public void draw() { super.draw(); drawLogic(this, true); }
        @Override public float maxHealth() { return levelMaxHealth(this); }
        @Override public float speed() { return levelSpeed(this, true); }
    }

    public static class VusUnit extends LegsUnit {
        @Override public void update() { super.update(); updateLogic(this, false, "suv-27"); }
        @Override public void draw() { super.draw(); drawLogic(this, false); }
        @Override public float maxHealth() { return levelMaxHealth(this); }
        @Override public float speed() { return levelSpeed(this, false); }
    }

    // ------------------------------------------------------------------ giao diện (nút kỹ năng + cần điều khiển)

    static boolean isControllingSpecialUnit() {
        if (!Vars.state.isGame() || Vars.player == null) return false;
        Unit unit = Vars.player.unit();
        if (unit == null || unit.type == null) return false;
        String name = unit.type.name;
        return name.equals("suv-27") || name.equals("newex-suv-27") || name.equals("vus-27") || name.equals("newex-vus-27");
    }

    static float movePointerKnobX = 0f, movePointerKnobY = 0f, moveStartX = 0f, moveStartY = 0f;
    static int movePointer = -1;

    private static void buildHud() {
        final float outerRadius = 70f, innerRadius = 28f;

        if (Vars.mobile) {
            Table moveContainer = new Table();
            moveContainer.setFillParent(true);
            moveContainer.bottom().left();

            Element moveJoystick = new Element() {
                @Override
                public void draw() {
                    super.draw();
                    float cx = x + outerRadius, cy = y + outerRadius;
                    Draw.color(Color.black, 0.4f);
                    Fill.circle(cx, cy, outerRadius);
                    Draw.color(PerfCore.color("c084fc"), 0.8f);
                    Lines.stroke(3f);
                    Lines.circle(cx, cy, outerRadius);
                    Draw.color(PerfCore.color("7e22ce"), 0.9f);
                    Fill.circle(cx + movePointerKnobX, cy + movePointerKnobY, innerRadius);
                    Draw.color(Color.white, 0.9f);
                    Lines.stroke(2f);
                    Lines.circle(cx + movePointerKnobX, cy + movePointerKnobY, innerRadius);
                    Draw.reset();
                }
            };

            moveJoystick.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (movePointer != -1) return false;
                    movePointer = pointer;
                    moveStartX = x; moveStartY = y;
                    movePointerKnobX = 0f; movePointerKnobY = 0f;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (pointer != movePointer) return;
                    float dx = x - moveStartX, dy = y - moveStartY;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    if (dist > outerRadius) {
                        dx = (dx / dist) * outerRadius;
                        dy = (dy / dist) * outerRadius;
                    }
                    movePointerKnobX = dx; movePointerKnobY = dy;
                    moveX = dx / outerRadius; moveY = dy / outerRadius;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (pointer != movePointer) return;
                    movePointer = -1;
                    movePointerKnobX = 0f; movePointerKnobY = 0f;
                    moveX = 0f; moveY = 0f;
                }
            });

            moveContainer.add(moveJoystick).size(outerRadius * 2f, outerRadius * 2f).pad(25f);
            moveContainer.visibility = Suv27::isControllingSpecialUnit;
            Core.scene.add(moveContainer);
        }

        Table skillContainer = new Table();
        skillContainer.setFillParent(true);
        skillContainer.bottom().left();
        skillContainer.margin(0f, 150f, 15f, 0f);

        final TextButton btn1 = skillContainer.button("[1] TRANSFORM", Icon.refresh, Styles.defaultt, () -> sendSkillCommand("transform"))
            .size(120, 42).pad(2).color(Color.valueOf("c084fc")).get();
        btn1.update(() -> {
            if (Vars.player != null && Vars.player.unit() != null) {
                UnitData data = getUnitData(Vars.player.unit());
                float cd = data != null ? data.transformCooldown : 0f;
                if (cd > 0f) { btn1.setText("[1] (" + Strings.fixed(cd / 60f, 1) + "s)"); btn1.setDisabled(true); }
                else { btn1.setText("[1] TRANSFORM"); btn1.setDisabled(false); }
            }
        });

        final TextButton btn2 = skillContainer.button("[2] PULSE CONE", Icon.commandRally, Styles.defaultt, () -> sendSkillCommand("pulse_cone"))
            .size(120, 42).pad(2).color(Color.valueOf("8aa3f4")).get();
        btn2.update(() -> {
            if (Vars.player != null && Vars.player.unit() != null) {
                UnitData data = getUnitData(Vars.player.unit());
                float cd = data != null ? data.coneShotCooldown : 0f;
                if (cd > 0f) { btn2.setText("[2] (" + Strings.fixed(cd / 60f, 1) + "s)"); btn2.setDisabled(true); }
                else { btn2.setText("[2] PULSE CONE"); btn2.setDisabled(false); }
            }
        });

        final TextButton btn3 = skillContainer.button("[3] SKILL 3", Icon.up, Styles.defaultt, () -> sendSkillCommand("hybrid_skill"))
            .size(125, 42).pad(2).color(Color.valueOf("f59e0b")).get();
        btn3.update(() -> {
            if (Vars.player != null && Vars.player.unit() != null) {
                Unit unit = Vars.player.unit();
                UnitData data = getUnitData(unit);
                String unitName = unit.type != null ? unit.type.name : "";
                boolean isSUVUnit = unitName.contains("suv-27") && !unitName.contains("vus-27");
                float cd = data != null ? data.hybridSkillCooldown : 0f;
                int mode = data != null ? data.gunMode : 0;
                if (cd > 0f) {
                    String secondsLeft = Strings.fixed(cd / 60f, 1);
                    String modeText = isSUVUnit ? (mode == 1 ? "8MM" : "PULSE") : "REPAIR";
                    btn3.setText("[3] " + modeText + " (" + secondsLeft + "s)");
                    btn3.setDisabled(true);
                } else {
                    if (isSUVUnit) btn3.setText(mode == 1 ? "[3] GUN: 8MM" : "[3] GUN: PULSE");
                    else btn3.setText("[3] REPAIR 1%");
                    btn3.setDisabled(false);
                }
            }
        });

        skillContainer.visibility = Suv27::isControllingSpecialUnit;
        Core.scene.add(skillContainer);
    }
}
