package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.world.Block;
import newex.PerfCore;

/**
 * Chuyển từ emeralift-wall.js. Tường tồn tại 300 giây rồi tự biến mất (nhanh hơn x1.8 khi bị Comduik hút),
 * đổi màu dần từ trắng sang xám.
 */
public final class EmeraliftWall {
    private static final float LIFETIME = 300f * 60f;

    private static final Effect circleWaveFx = new Effect(30f, e -> {
        Draw.color(PerfCore.color("84e070"));
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 4f + e.fin() * 16f);
        Draw.reset();
    });

    private EmeraliftWall() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block wall = Vars.content.block("newex-emeralift-wall");
            if (wall != null) {
                wall.update = true;
                wall.buildType = EmeraliftBuild::new;
            }
        });
    }

    public static class EmeraliftBuild extends Building implements ComduikDrainable {
        float timeRemaining = LIFETIME;
        float shockTimer = 0f;
        float nextShockTime = 60f;
        int drainTicks = 0;

        @Override
        public void placed() {
            super.placed();
            timeRemaining = LIFETIME;
            nextShockTime = Mathf.random(60f, 120f);
        }

        @Override
        public void applyComduikDrain() {
            drainTicks = 2;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            float decayMultiplier = (drainTicks > 0) ? 1.8f : 1f;
            if (drainTicks > 0) drainTicks--;

            timeRemaining -= Time.delta * decayMultiplier;

            if (timeRemaining <= 0) {
                tile.setAir();
                return;
            }

            shockTimer += Time.delta;
            if (shockTimer >= nextShockTime) {
                shockTimer = 0f;
                nextShockTime = Mathf.random(60f, 120f);

                circleWaveFx.at(x, y);
            }
        }

        @Override
        public void draw() {
            float progress = Math.max(0f, timeRemaining / LIFETIME);
            Color gray = Tmp.c1.set(0.4f, 0.4f, 0.4f, 1f);
            Color current = Tmp.c2.set(Color.white).lerp(gray, 1f - progress);

            Draw.color(current);
            Draw.rect(block.region, x, y);
            Draw.reset();
        }
    }
}
