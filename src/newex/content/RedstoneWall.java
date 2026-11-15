package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Lightning;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.world.Block;
import newex.PerfCore;

/**
 * Chuyển từ redstone-wall.js. Giống Emeralift nhưng phóng tia sét đỏ. Cài đặt "newex-redstone-fx":
 * tắt (mặc định) = cập nhật 6 tick/lần cho nhẹ máy, bật = hiệu ứng đầy đủ.
 */
public final class RedstoneWall {
    private static final float LIFETIME = 300f * 60f;

    private RedstoneWall() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block wall = Vars.content.block("newex-redstone-wall");
            if (wall != null) {
                wall.update = true;
                wall.buildType = RedstoneBuild::new;
            }
        });
    }

    public static class RedstoneBuild extends Building implements ComduikDrainable {
        float timeRemaining = LIFETIME;
        float shockTimer = 0f;
        float nextShockTime = 60f;
        int drainTicks = 0;
        float cachedProgress = 1f;
        int updateInterval = 0;

        @Override
        public void placed() {
            super.placed();
            timeRemaining = LIFETIME;
            nextShockTime = Mathf.random(60f, 120f);
        }

        @Override
        public void applyComduikDrain() {
            drainTicks = 10;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            boolean useFullFx = Core.settings.getBool("newex-redstone-fx", false);

            if (!useFullFx) {
                updateInterval++;
                if (updateInterval < 6) return;
                int deltaMultiplier = updateInterval;
                updateInterval = 0;

                float decayMultiplier = (drainTicks > 0) ? 1.8f : 1f;
                if (drainTicks > 0) drainTicks -= deltaMultiplier;

                timeRemaining -= Time.delta * deltaMultiplier * decayMultiplier;

                if (timeRemaining <= 0) {
                    tile.setAir();
                    return;
                }

                cachedProgress = Math.max(0f, timeRemaining / LIFETIME);

                shockTimer += deltaMultiplier;
                if (shockTimer >= nextShockTime) {
                    shockTimer = 0f;
                    nextShockTime = Mathf.random(200f, 400f);

                    try {
                        Fx.generatespark.at(x, y);
                        Fx.lightning.at(x, y, Mathf.random(360f), PerfCore.color("ff4444"));
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                float decayMultiplier = (drainTicks > 0) ? 1.8f : 1f;
                if (drainTicks > 0) drainTicks--;

                timeRemaining -= Time.delta * decayMultiplier;

                if (timeRemaining <= 0) {
                    tile.setAir();
                    return;
                }

                float lifeProgress = Math.max(0f, timeRemaining / LIFETIME);

                shockTimer += Time.delta;
                if (shockTimer >= nextShockTime) {
                    shockTimer = 0f;
                    nextShockTime = Mathf.random(60f, 120f);

                    int maxBolts = (int) Math.floor(1 + lifeProgress * 4);
                    int boltCount = (int) Math.floor(Mathf.random(1f, maxBolts + 1f));

                    for (int i = 0; i < boltCount; i++) {
                        float randomAngle = Mathf.random(360f);
                        Lightning.create(team, PerfCore.color("ff4444"), 20f, x, y, randomAngle, 5);
                    }

                    try {
                        Fx.generatespark.at(x, y);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

        @Override
        public void draw() {
            boolean useFullFx = Core.settings.getBool("newex-redstone-fx", false);
            float progress = useFullFx ? Math.max(0f, timeRemaining / LIFETIME) : cachedProgress;
            Color gray = Tmp.c1.set(0.4f, 0.4f, 0.4f, 1f);
            Color current = Tmp.c2.set(Color.white).lerp(gray, 1f - progress);

            Draw.color(current);
            Draw.rect(block.region, x, y);
            Draw.reset();
        }
    }
}
