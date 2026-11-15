package newex.content;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.entities.units.WeaponMount;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ explayder.js. Năng lực của đơn vị lõi "explayder":
 * di chuyển liên tục 3 giây thì tăng tốc x2.5 trong 20 giây; tia laser mạnh và dài hơn khi mất máu.
 */
public final class ExplayderAbility {
    private static final class Data {
        float movingTimer = 0f;
        float speedBoostTimer = 0f;
    }

    private static final Map<Integer, Data> unitDataMap = new HashMap<>();

    private ExplayderAbility() {}

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> setup());
    }

    private static void setup() {
        UnitType explayder = Vars.content.getByName(ContentType.unit, "newex-explayder");
        if (explayder == null) return;

        Events.on(UnitDestroyEvent.class, event -> {
            if (event.unit != null) unitDataMap.remove(event.unit.id);
        });

        explayder.abilities.add(new Ability() {
            @Override
            public void update(Unit unit) {
                if (unit == null) return;

                int id = unit.id;

                Data data = unitDataMap.get(id);
                if (data == null) {
                    data = new Data();
                    unitDataMap.put(id, data);
                }

                boolean isMoving = unit.vel.len() > 0.1f;

                if (data.speedBoostTimer > 0) {
                    data.speedBoostTimer -= Time.delta / 60f;

                    unit.speedMultiplier *= 2.5f;

                    if (Mathf.chance(0.3)) {
                        try {
                            Fx.trailFade.at(unit.x, unit.y, 4f, PerfCore.color("66B1FF"));
                        } catch (Throwable ignored) {
                        }
                    }

                    if (data.speedBoostTimer <= 0) {
                        data.speedBoostTimer = 0f;
                        data.movingTimer = 0f;
                    }
                } else {
                    if (isMoving) {
                        data.movingTimer += Time.delta / 60f;
                        if (data.movingTimer >= 3f) {
                            data.speedBoostTimer = 20f;

                            try {
                                Fx.heal.at(unit.x, unit.y, 0f, PerfCore.color("66B1FF"));
                            } catch (Throwable ignored) {
                            }
                        }
                    } else {
                        data.movingTimer = 0f;
                    }
                }

                float missingHealthRatio = 1f - (unit.health / unit.maxHealth);

                if (unit.mounts != null) {
                    for (int i = 0; i < unit.mounts.length; i++) {
                        WeaponMount mount = unit.mounts[i];
                        if (mount != null && mount.weapon != null && mount.weapon.bullet != null) {
                            BulletType bullet = mount.weapon.bullet;
                            if (bullet instanceof LaserBulletType) {
                                ((LaserBulletType) bullet).length = 130f * (1f + missingHealthRatio);
                            }
                            bullet.damage = 16f * (1f + missingHealthRatio);
                        }
                    }
                }
            }
        });
    }
}
