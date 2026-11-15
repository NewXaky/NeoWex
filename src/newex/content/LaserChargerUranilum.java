package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.GenericCrafter;
import newex.PerfCore;

/**
 * Chuyển từ laser-charger-uranilum.js. Bộ nạp laser: nạp 90 tick rồi bắn tia vào Uranilum Reactor
 * cách 15 ô theo 1 trong 4 hướng (cần điện >= 90% và 5 Uranilum).
 */
public final class LaserChargerUranilum {
    private static final int RANGE_IN_TILES = 15;
    private static final float CHARGE_TIME = 90f;
    private static final Color lightningColor = Color.valueOf("7fd4ff");
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @SuppressWarnings("unchecked")
    private static final Effect lyvervonLightningEffect = new Effect(14f, e -> {
        if (!(e.data instanceof Seq)) return;
        Seq<Vec2> points = (Seq<Vec2>) e.data;
        float thickness = e.rotation > 0 ? e.rotation : 2.8f;
        Draw.color(lightningColor, Color.white, e.fin());
        Lines.stroke(thickness * e.fout());
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
    });

    private LaserChargerUranilum() {}

    private static void createLyvervonLightning(float x1, float y1, float x2, float y2, float thickness) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(4, (int) Math.floor(dst / 7f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);

        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(x1, x2, t);
            float py = Mathf.lerp(y1, y2, t);

            float jitter = 10f;
            float noise = Mathf.range(jitter);

            Tmp.v1.trns(angle + 90f, noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));
        lyvervonLightningEffect.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, points);
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.block("newex-laser-charger-uranilum");
            if (b instanceof GenericCrafter) {
                final GenericCrafter block = (GenericCrafter) b;
                block.buildType = () -> new LaserChargerBuild(block);
            }
        });
    }

    /** GenericCrafterBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class LaserChargerBuild extends GenericCrafter.GenericCrafterBuild {
        float chargeProgress = 0f;
        float animTime = 0f;

        public LaserChargerBuild(GenericCrafter outer) {
            outer.super();
        }

        Item getUranilumItem() {
            return Vars.content.item("newex-uranilum");
        }

        boolean hasRequiredResources() {
            Item uranilum = getUranilumItem();
            boolean hasItem = uranilum != null ? items.get(uranilum) >= 5 : true;
            boolean hasPower = power != null && power.status >= 0.9f;

            return hasItem && hasPower;
        }

        Building findTargetReactor() {
            float dist = RANGE_IN_TILES * Vars.tilesize;

            for (int i = 0; i < DIRECTIONS.length; i++) {
                float tx = x + (DIRECTIONS[i][0] * dist);
                float ty = y + (DIRECTIONS[i][1] * dist);

                Tile tile = Vars.world.tileWorld(tx, ty);
                if (tile != null && tile.build != null && tile.build.block.name.endsWith("uranilum-reactor")) {
                    if (Math.abs(tile.build.x - tx) < 8 && Math.abs(tile.build.y - ty) < 8) {
                        return tile.build;
                    }
                }
            }
            return null;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (!Vars.state.isPaused()) {
                animTime += Time.delta;
            }

            Building reactorBuild = findTargetReactor();

            if (reactorBuild != null && hasRequiredResources()) {
                if (chargeProgress < 1f) {
                    chargeProgress = Math.min(1f, chargeProgress + (1f / CHARGE_TIME));
                } else {
                    if (reactorBuild instanceof LaserReceiver) {
                        ((LaserReceiver) reactorBuild).addLaser();
                    }
                }
            } else {
                chargeProgress = Math.max(0f, chargeProgress - 0.05f);
            }
        }

        @Override
        public void draw() {
            super.draw();

            Building reactorBuild = findTargetReactor();

            if (chargeProgress > 0) {
                Draw.z(Layer.power + 1);

                float orbPulse = Mathf.absin(animTime, 1.5f, 0.8f);
                float orbRadius = (chargeProgress * 6.5f) + orbPulse * chargeProgress;

                Draw.color(PerfCore.color("389bfb"), 0.35f * chargeProgress);
                Fill.circle(x, y, orbRadius + 5f);

                Draw.color(PerfCore.color("66b1ff"), 0.7f * chargeProgress);
                Fill.circle(x, y, orbRadius + 2f);

                Draw.color(Color.white, chargeProgress);
                Fill.circle(x, y, orbRadius * 0.55f);

                if (chargeProgress < 1f) {
                    for (int i = 0; i < 3; i++) {
                        float angle = (animTime * 4f + i * 120f) % 360f;
                        float dist = (1f - ((animTime * 0.05f + i * 0.33f) % 1f)) * 18f;
                        float px = x + Angles.trnsx(angle, dist);
                        float py = y + Angles.trnsy(angle, dist);

                        Draw.color(PerfCore.color("66b1ff"), chargeProgress);
                        Fill.circle(px, py, 1.5f);
                    }
                }

                if (chargeProgress >= 1f && reactorBuild != null) {
                    Draw.z(Layer.power + 2);

                    float pulse = Mathf.absin(animTime, 1.2f, 1.8f);
                    float baseWidth = 7f + pulse;

                    Draw.color(PerfCore.color("1f65ca"), 0.25f);
                    Lines.stroke(baseWidth + 12f);
                    Lines.line(x, y, reactorBuild.x, reactorBuild.y);

                    Draw.color(PerfCore.color("389bfb"), 0.6f);
                    Lines.stroke(baseWidth + 5f);
                    Lines.line(x, y, reactorBuild.x, reactorBuild.y);

                    Draw.color(PerfCore.color("66b1ff"));
                    Lines.stroke(baseWidth);
                    Lines.line(x, y, reactorBuild.x, reactorBuild.y);

                    Draw.color(Color.white);
                    Lines.stroke(baseWidth * 0.4f);
                    Lines.line(x, y, reactorBuild.x, reactorBuild.y);

                    if (Mathf.chance(0.6) && !Vars.state.isPaused()) {
                        float p1 = Mathf.random(0.1f, 0.4f);
                        float p2 = p1 + Mathf.random(0.3f, 0.5f);

                        float lx1 = Mathf.lerp(x, reactorBuild.x, p1);
                        float ly1 = Mathf.lerp(y, reactorBuild.y, p1);
                        float lx2 = Mathf.lerp(x, reactorBuild.x, p2);
                        float ly2 = Mathf.lerp(y, reactorBuild.y, p2);

                        createLyvervonLightning(lx1, ly1, lx2, ly2, 2.5f);
                    }

                    Draw.color(PerfCore.color("66b1ff"), 0.7f);
                    Fill.circle(reactorBuild.x, reactorBuild.y, 7f + pulse);
                    Draw.color(Color.white);
                    Fill.circle(reactorBuild.x, reactorBuild.y, 3.5f + pulse * 0.5f);
                }

                Draw.reset();
            }
        }
    }
}
