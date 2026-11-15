package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ erysidus-drone.js. Drone của Erysidus: bay vòng quanh mục tiêu; khi có mục tiêu trong 280px thì bắn chùm laser
 * trong 42 tick (45 sát thương, 68 nếu máu tối đa > 2000), gấp đôi sát thương lên mục tiêu bay, rồi hồi 300 tick.
 */
public final class ErysidusDrone {
    private static final Color mainColor = Color.valueOf("90caf9");
    private static final Color lightColor = Color.valueOf("e3f2fd");
    private static final Color coreColor = Color.white;

    private static final Effect customHitEffect = new Effect(24f, e -> {
        Draw.color(mainColor, lightColor, e.fout());
        arc.graphics.g2d.Lines.stroke(e.fout() * 2.5f);
        arc.graphics.g2d.Lines.circle(e.x, e.y, e.fin() * 14f);

        int count = 6;
        for (int i = 0; i < count; i++) {
            float ang = (360f / count) * i + Mathf.randomSeed(e.id + i, -15f, 15f);
            float len = 2f + 16f * e.finpow();
            float px = e.x + Angles.trnsx(ang, len);
            float py = e.y + Angles.trnsy(ang, len);

            arc.graphics.g2d.Lines.stroke(e.fout() * 2f);
            arc.graphics.g2d.Lines.lineAngle(px, py, ang, 1f + e.fout() * 3f);
        }

        Drawf.light(e.x, e.y, 40f, mainColor, 0.7f * e.fout());
    });

    private static LaserBulletType droneBeam;

    private static final class Data {
        float beamDurationTimer = 0f;
        float beamCooldownTimer = 0f;
        float targetTimer = 0f;
        float scanTimer = 0f;
        Teamc cachedTarget = null;
    }

    private static final Map<Integer, Data> droneStorage = new HashMap<>();

    private ErysidusDrone() {}

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        droneBeam = new LaserBulletType() {
            {
                damage = 45f;
                length = 280f;
                width = 28f;
                lifetime = 5f;
                layer = 115f;
                colors = new Color[]{mainColor, lightColor, coreColor};
                sideAngle = 45f;
                sideWidth = 2.2f;
                sideLength = 26f;
                hitEffect = customHitEffect;
                despawnEffect = Fx.none;
                drawSize = 300f;
                pierce = true;
            }

            @Override
            public void draw(Bullet b) {
                super.draw(b);
                Drawf.light(b.x, b.y, b.x + Angles.trnsx(b.rotation(), length), b.y + Angles.trnsy(b.rotation(), length), width * 2.5f, mainColor, 0.9f);
            }

            @Override
            public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
                super.hitEntity(b, other, initialHealth);
                if (other instanceof Unit && ((Unit) other).isFlying()) {
                    ((Unit) other).damage(damage * 2f);
                }
            }
        };
    }

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> {
            UnitType found = Vars.content.getByName(ContentType.unit, "newex-erysidus-drone");
            if (found == null) found = Vars.content.getByName(ContentType.unit, "erysidus-drone");
            final UnitType droneType = found;

            if (droneType != null) {
                droneType.aiController = () -> new FlyingAI() {
                    @Override
                    public void updateMovement() {
                        if (target != null) {
                            circleAttack(200f);
                        } else {
                            mindustry.gen.Building core = unit.closestCore();
                            if (core != null) {
                                moveTo(core, 80f);
                            }
                        }
                    }
                };
            }

            Events.run(Trigger.update, () -> {
                if (Vars.state.isMenu()) return;

                Groups.unit.each(u -> {
                    if (u != null && u.type == droneType && u.isValid()) {
                        Data cached = droneStorage.get(u.id);
                        if (cached == null) {
                            cached = new Data();
                            droneStorage.put(u.id, cached);
                        }
                        final Data data = cached;
                        int tier = u.maxHealth > 2000 ? 2 : (u.maxHealth > 1500 ? 1 : 0);

                        if (PerfCore.onScreen(u.x, u.y, 120f)) {
                            Draw.draw(Layer.flyingUnit - 0.01f, () -> {
                                Drawf.light(u.x, u.y, 50f, mainColor, 0.6f);

                                if (data.beamDurationTimer > 0) {
                                    Drawf.light(u.x, u.y, 90f, mainColor, 0.95f);
                                }
                            });
                        }

                        if (data.beamDurationTimer > 0) {
                            data.beamDurationTimer -= Time.delta;

                            data.targetTimer -= Time.delta;
                            if (data.targetTimer <= 0) {
                                data.targetTimer = 6f;
                                // JS gốc truyền hàm 2 tham số (a, b) => b.health - a.health vào Sortf 3 tham số nên chi phí luôn là NaN:
                                // kết quả thực tế là "mục tiêu đầu tiên tìm thấy". Giữ nguyên hành vi đó.
                                data.cachedTarget = Units.bestTarget(u.team, u.x, u.y, 280f,
                                    en -> en.checkTarget(true, true), b -> true, (unit, x, y) -> Float.NaN);
                            }
                            Teamc target = data.cachedTarget;
                            if (target != null && Units.invalidateTarget((Posc) target, u.team, u.x, u.y)) target = null;
                            float targetAngle = u.rotation;
                            if (target != null) {
                                targetAngle = u.angleTo((Position) target);
                                u.rotation = Mathf.slerpDelta(u.rotation, targetAngle, 0.25f);
                            }

                            if (Mathf.mod(data.beamDurationTimer, 3f) < Time.delta) {
                                float beamDmg = (tier == 2) ? 68f : 45f;
                                Bullet beam = droneBeam.create(u, u.team, u.x, u.y, targetAngle, 1f, 1f);
                                if (beam != null) beam.damage = beamDmg;
                                Effect.shake(1.5f, 1.5f, u.x, u.y);
                            }

                            if (data.beamDurationTimer <= 0) {
                                data.beamCooldownTimer = 300f;
                            }
                        } else if (data.beamCooldownTimer > 0) {
                            data.beamCooldownTimer = Math.max(0f, data.beamCooldownTimer - Time.delta);
                        } else {
                            data.scanTimer -= Time.delta;
                            if (data.scanTimer <= 0) {
                                data.scanTimer = 10f;
                                Teamc target = Units.closestTarget(u.team, u.x, u.y, 280f);
                                if (target != null) {
                                    data.beamDurationTimer = 42f;
                                    data.targetTimer = 0f;
                                }
                            }
                        }
                    }
                });
            });

            Events.on(UnitDestroyEvent.class, event -> {
                if (event.unit != null) droneStorage.remove(event.unit.id);
            });
        });
    }
}
