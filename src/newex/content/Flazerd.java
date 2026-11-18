// ... existing code ...
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;
import java.util.HashMap;

/** Chuyển từ flazerd.js. Laser bám mục tiêu, tích điểm sạc 0-200%, xả 3 tia hội tụ + nổ tím và ngưng 2s. */
public final class Flazerd {
    private Flazerd() {}

    static final float deadZone = 40f;
    static final Color laserColor = Color.valueOf("bf7fff");
    static final Color particleColor = Color.valueOf("e8bfff");
    static final Color deepPurple = Color.valueOf("6a1b9a");
    static final Color greenLaserColor = Color.valueOf("33ff55");

    static final class Req { final int copper, graphite, lead, silicon; Req(int c, int g, int l, int s) { copper = c; graphite = g; lead = l; silicon = s; } }
    static final Req reqMK2 = new Req(400, 400, 0, 0);
    static final Req reqMK3 = new Req(0, 0, 450, 570);

    static final class Sub { float x, y; Sub(float x, float y) { this.x = x; this.y = y; } }
    /** Trạng thái pháo: burstTimer (>0: đang bắn 3 tia hội tụ), cooldownTimer (>0: ngưng bắn 2s). */
    static final class State {
        int tier = 1;
        float charge = 0f;
        int burstTimer = 0;
        int cooldownTimer = 0;
        final ArrayList<Sub> subs = new ArrayList<>();
    }
    static final HashMap<Integer, State> states = new HashMap<>();
    static State state(int id) { State s = states.get(id); if (s == null) { s = new State(); states.put(id, s); } return s; }

    /** Hiệu ứng nổ đa lớp Pyroclastic Impact màu tím (Vòng xung kích, khói bốc lên, cầu lửa, tia lửa văng và lõi chớp sáng). */
    static final Effect purplePyroclasticExplosion = new Effect(55f, 120f, e -> {
        float fin = e.fin();
        float fout = e.fout();

        // 1. Rising Billowing Smoke (Khói tím sẫm cuộn lên)
        Draw.color(deepPurple, Color.darkGray, fin);
        Draw.alpha(fout * 0.65f);
        Angles.randLenVectors(e.id + 1, 10, 12f + fin * 34f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y + fin * 16f, 4f + fout * 9f);
        });

        // 2. Radial Shockwave & Dust (Sóng xung kích lan tỏa)
        Draw.color(laserColor, particleColor, fin);
        Lines.stroke(fout * 3.5f);
        Lines.circle(e.x, e.y, fin * 56f);
        Lines.stroke(fout * 1.5f);
        Lines.circle(e.x, e.y, fin * 40f);

        // 3. Rolling Fireball Volumetrics (Cầu lửa năng lượng tím)
        Draw.color(particleColor, laserColor, fin);
        Draw.alpha(fout * 0.9f);
        Angles.randLenVectors(e.id + 2, 8, fin * 24f, (x, y) -> {
            Fill.circle(e.x + x, e.y + y, fout * 11f);
        });

        // 4. Sparks & Flying Debris (Tia lửa & mảnh vỡ văng ra)
        Draw.color(Color.white, laserColor, fin);
        Lines.stroke(fout * 2f);
        Angles.randLenVectors(e.id + 3, 16, 8f + fin * 65f, (x, y) -> {
            float ang = Mathf.angle(x, y);
            Lines.lineAngle(e.x + x, e.y + y, ang, fout * 9f);
            Fill.square(e.x + x * 0.85f, e.y + y * 0.85f, fout * 2.2f, ang + fin * 180f);
        });

        // 5. Core Flash & Glow (Chớp sáng lõi trắng-tím)
        if (fin < 0.45f) {
            float flashOut = 1f - (fin / 0.45f);
            Draw.color(Color.white);
            Fill.circle(e.x, e.y, flashOut * 16f);
            Drawf.light(e.x, e.y, flashOut * 75f, laserColor, 0.9f);
        }
        Draw.reset();
    });

    static TextureRegion bodyRegion, downxRegion, a1Region, a2Region;
// ... existing code ...
        @Override
        public void update(Bullet b) {
            if (b == null || !(b.owner instanceof Building) || !(b.data instanceof LaserData)) return;
            Building o = (Building) b.owner;
            LaserData data = (LaserData) b.data;
            final float range = 260f, centerX = o.x, centerY = o.y;

            State st = state(o.id);
            int tier = st.tier;

            // Đang trong thời gian ngưng bắn 2 giây (120 ticks) sau khi xả 3 tia
            if (st.cooldownTimer > 0) {
                st.subs.clear();
                return;
            }

            float currentPoints = st.charge;
            float baseDamage = 33f;
            if (tier == 2) baseDamage = 33f * 3.5f;
            else if (tier == 3) baseDamage = 33f * 6.0f;
            float currentTickDamage = baseDamage * (1f + currentPoints * 0.5f);

            Teamc mainTarget = null;
            if (data.mainTargetId != -1) {
                Unit potential = Groups.unit.getByID(data.mainTargetId);
                if (isValidTarget(potential, centerX, centerY, range)) mainTarget = potential;
            }
            if (mainTarget == null) {
                mainTarget = Units.closestTarget(b.team, centerX, centerY, range, u -> isValidTarget(u, centerX, centerY, range), t -> true);
                data.mainTargetId = mainTarget != null ? mainTarget.id() : -1;
            }

            // Trạng thái đang bắn 3 tia hội tụ (25 ticks)
            if (st.burstTimer > 0) {
                if (mainTarget != null) {
                    data.lastX = mainTarget.getX();
                    data.lastY = mainTarget.getY();
                }
                st.burstTimer--;
                // Khi tia laser 3 chùm bắn xong tới mục tiêu -> tạo vụ nổ tím + gây sát thương diện rộng + vào 2s nghỉ
                if (st.burstTimer == 0) {
                    float ex = data.lastX, ey = data.lastY;
                    purplePyroclasticExplosion.at(ex, ey);
                    Sounds.explosionbig.at(ex, ey);
                    Damage.damage(b.team, ex, ey, 56f, baseDamage * 4.5f);
                    st.charge = 0f;
                    st.cooldownTimer = 120; // Ngưng bắn 2 giây
                    st.subs.clear();
                }
                return;
            }

            if (mainTarget != null) {
                final Teamc mt = mainTarget;
                data.lastX = mt.getX();
                data.lastY = mt.getY();
                if (mt instanceof Healthc) ((Healthc) mt).damage(currentTickDamage / 60f);

                // Tốc độ tích sạc tăng thêm 120% (nhân 2.2f)
                float chargeSpeedMultiplier = (float) (1 + Math.pow(currentPoints / 100f, 2));
                float speedBonus = (tier == 2 ? 1.5f : 1.0f) * 2.2f;
                currentPoints += (1f / 60f) * chargeSpeedMultiplier * speedBonus;

                if (currentPoints >= 200f) {
                    currentPoints = 200f;
                    st.charge = 200f;
                    st.burstTimer = 25; // Kích hoạt trạng thái bắn 3 tia hội tụ
                    Sounds.lasercharge2.at(centerX, centerY);
                    return;
                }
                st.charge = currentPoints;

                if (tier == 3) {
                    st.subs.clear();
                    int activeMilestones = 0;
                    for (int k = 1; k <= 10; k++) if (currentPoints >= k * 20) activeMilestones++;

                    if (activeMilestones > 0) {
                        final ArrayList<Unit> pool = new ArrayList<>();
                        Units.nearbyEnemies(b.team, centerX - range, centerY - range, range * 2f, range * 2f, u -> {
                            if (isValidTarget(u, centerX, centerY, range) && u.id() != mt.id()) pool.add(u);
                        });
                        if (pool.size() > 0) {
                            pool.sort((o1, o2) -> {
                                float d1 = Mathf.dst(mt.getX(), mt.getY(), o1.x, o1.y);
                                float d2 = Mathf.dst(mt.getX(), mt.getY(), o2.x, o2.y);
                                return d1 < d2 ? -1 : (d1 > d2 ? 1 : 0);
                            });
                            float subDamageTick = (currentTickDamage * 0.5f) / 60f;
                            int countToShoot = Math.min(activeMilestones, pool.size());
                            for (int i = 0; i < countToShoot; i++) {
                                Unit chosen = pool.get(i);
                                st.subs.add(new Sub(chosen.x, chosen.y));
                                chosen.damage(subDamageTick);
                                if (Mathf.chance(0.15)) Fx.hitLancer.at(chosen.x, chosen.y, greenLaserColor);
                            }
                        }
                    }
                }
                if (Mathf.chance(0.25)) Fx.hitLancer.at(mt.getX(), mt.getY(), laserColor);
            } else {
                data.mainTargetId = -1;
                st.subs.clear();
                data.lastX = centerX + Angles.trnsx(b.rotation(), range);
                data.lastY = centerY + Angles.trnsy(b.rotation(), range);
                if (currentPoints > 0f) {
                    currentPoints -= 3f / 60f;
                    if (currentPoints < 0f) currentPoints = 0f;
                    st.charge = currentPoints;
                }
            }
        }

        @Override
        public void draw(Bullet b) {
            if (b == null || !(b.data instanceof LaserData) || !(b.owner instanceof Building)) return;
            Building o = (Building) b.owner;
            LaserData data = (LaserData) b.data;
            State st = states.get(o.id);
            if (st != null && st.cooldownTimer > 0) return; // Ngưng vẽ tia trong 2s nghỉ

            int tier = st != null ? st.tier : 1;
            float startX = o.x, startY = o.y, endX = data.lastX, endY = data.lastY;
            float dist = Mathf.dst(startX, startY, endX, endY);
            float laserAngle = Angles.angle(startX, startY, endX, endY);
            float currentPoints = st != null ? st.charge : 0f;

            // Hiệu ứng 3 tia hội tụ khi sạc đầy (giống video)
            if (st != null && st.burstTimer > 0) {
                float progress = 1f - (st.burstTimer / 25f); // 0 -> 1
                float spread = (1f - progress) * 28f; // 2 tia ngoài ép sát vào tia giữa
                float beamAlpha = Mathf.clamp(progress * 2.5f);

                // Vòng năng lượng tụ ở đầu nòng & dọc trục
                Draw.color(laserColor);
                Draw.alpha(beamAlpha);
                Lines.stroke(2f);
                Lines.circle(startX + Angles.trnsx(laserAngle, 14f), startY + Angles.trnsy(laserAngle, 14f), 6f + (1f - progress) * 14f);

                // Vẽ 3 tia: 1 tia chính giữa + 2 tia phụ hai bên hội tụ về mục tiêu
                for (int side = -1; side <= 1; side++) {
                    float sx = startX + Angles.trnsx(laserAngle + 90f, side * spread);
                    float sy = startY + Angles.trnsy(laserAngle + 90f, side * spread);
                    float width = (side == 0) ? (4.5f + progress * 4f) : (2.2f + progress * 2f);

                    Draw.color(laserColor);
                    Draw.alpha(beamAlpha);
                    Lines.stroke(width);
                    Lines.line(sx, sy, endX, endY);

                    Draw.color(Color.white);
                    Draw.alpha(beamAlpha);
                    Lines.stroke(width * 0.4f);
                    Lines.line(sx, sy, endX, endY);
                }
                Draw.reset();
                return;
            }

            float thicknessScale = 1.0f + (currentPoints * 0.02f * 0.5f);

            Draw.color(laserColor); Lines.stroke(3.6f * thicknessScale); Lines.line(startX, startY, endX, endY);
            Draw.color(Color.white); Lines.stroke(1.2f * thicknessScale); Lines.line(startX, startY, endX, endY);

            if (tier == 2 && currentPoints > 2f) {
                Draw.color(particleColor);
                float subThickness = 0.8f * thicknessScale;
                drawCurvedSubLaser(startX, startY, endX, endY, laserAngle, subThickness, 1f);
                drawCurvedSubLaser(startX, startY, endX, endY, laserAngle, subThickness, -1f);
            }

            if (tier == 3 && st != null) {
                for (int m = 0; m < st.subs.size(); m++) {
                    Sub node = st.subs.get(m);
                    Draw.color(greenLaserColor);
                    Lines.stroke(1.8f * thicknessScale);
                    Lines.line(endX, endY, node.x, node.y);
                    Draw.color(Color.white);
                    Lines.stroke(0.5f * thicknessScale);
                    Lines.line(endX, endY, node.x, node.y);
                }
            }

            if (currentPoints > 2f) {
                float scaleFactor = 1f + (currentPoints / 240f);
                float distortionFactor = Math.max(0.05f, 1.0f - (currentPoints / 200f));
                int ringCount = 3;
                for (int r = 0; r < ringCount; r++) {
                    float ringProgress = ((Time.time * 0.8f) + (r * (dist / ringCount))) % dist;
                    float rt = ringProgress / dist;
                    float rx = Mathf.lerp(startX, endX, rt), ry = Mathf.lerp(startY, endY, rt);
                    float rxSize = 1.5f * scaleFactor;
                    float rySize = 4.0f * scaleFactor * distortionFactor;
                    float strokeWidth = 0.4f * Mathf.sin(rt * Mathf.PI) * thicknessScale;
                    drawLaserRing(rx, ry, rxSize, rySize, laserAngle, strokeWidth, laserColor);
                }
            }

            if (currentPoints > 0f) {
                Draw.z(Layer.effect + 10f);
                Draw.color(Color.white);
                Font font = Fonts.outline;
                font.getData().setScale(0.12f);
                String displayString = (int) Math.floor(currentPoints) + "%";
                font.draw(displayString, o.x, o.y, Align.center);
                font.getData().setScale(1f);
            }
            Draw.reset();
        }
    }

    public static void create() {
// ... existing code ...
        @Override
        public void updateTile() {
            State st = state(id);
            // Đếm ngược thời gian ngưng bắn 2s (120 ticks) và khóa cơ chế bắn mới của turret
            if (st.cooldownTimer > 0) {
                st.cooldownTimer--;
                reloadCounter = 0f;
                wingsOffset = Mathf.lerpDelta(wingsOffset, 0f, 0.1f);
                return;
            }
            super.updateTile();
            boolean hasTarget = Units.closestTarget(team, x, y, 260f, u -> !u.dead() && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone, bb -> true) != null;
            wingsOffset = hasTarget ? Mathf.lerpDelta(wingsOffset, 1f, 0.15f) : Mathf.lerpDelta(wingsOffset, 0f, 0.1f);
        }

        @Override
        public void draw() {
            super.draw();
// ... existing code ...
            if (a2Region != null && a2Region.found()) {
                Draw.rect(a2Region, x + (wingBackOffset * cos) + (combatSideMove * sin), y + (wingBackOffset * sin) - (combatSideMove * cos), rotation);
            }

            // Hiển thị đếm ngược 2s nghỉ trên thân pháo
            State st = states.get(id);
            if (st != null && st.cooldownTimer > 0) {
                Draw.z(Layer.effect + 10f);
                Font font = Fonts.outline;
                font.getData().setScale(0.12f);
                String cdStr = String.format("[scarlet]%.1fs[]", st.cooldownTimer / 60f);
                font.draw(cdStr, x, y, Align.center);
                font.getData().setScale(1f);
            }
            Draw.reset();
        }

        @Override
        public void buildConfiguration(Table table) {
// ... existing code ...