package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.StorageBlock;

/**
 * Chuyển từ wuex-conveyor.js. Cổng Wuex mk1: tự hút 1 vật phẩm mỗi tick từ công trình ở vị trí mục tiêu
 * (trong tầm 380px) về cổng, rồi xả ra xung quanh.
 */
public final class WuexConveyor {
    private static final String NAME = "newex-wuex-conveyor-mk1";

    private static final Effect teleportEffect = new Effect(20f, e -> {
        Draw.color(Pal.heal, Color.white, e.fin());
        float rad = e.rotation * Mathf.degRad;
        float speed = 25f * e.fin();
        float px = e.x + Mathf.cos(rad) * speed;
        float py = e.y + Mathf.sin(rad) * speed;
        Fill.poly(px, py, 3, 5f * e.fout(), e.rotation);
    });

    private static final Effect receiveEffect = new Effect(25f, e -> {
        Draw.color(Pal.heal, Color.green, e.fin());
        float radius = 14f * e.fout();
        float angle = e.rotation + e.fin() * 180f;
        float rad = angle * Mathf.degRad;
        float rx = e.x + Mathf.cos(rad) * radius;
        float ry = e.y + Mathf.sin(rad) * radius;
        Fill.poly(rx, ry, 3, 4f * e.fout(), angle);
    });

    private WuexConveyor() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.block(NAME);
            if (b instanceof StorageBlock) {
                final StorageBlock block = (StorageBlock) b;
                block.buildType = () -> new WuexBuild(block);
            }
        });

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && NAME.equals(build.name)) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    float centerX = tile.drawx() + build.offset;
                    float centerY = tile.drawy() + build.offset;
                    Drawf.dashCircle(centerX, centerY, GateBuild.MAX_RANGE, Pal.heal);
                    Drawf.square(centerX, centerY, 4f, 0f, Pal.heal);
                }
            }
        });
    }

    public static class WuexBuild extends GateBuild {
        public WuexBuild(StorageBlock outer) {
            super(outer);
        }

        @Override
        protected String selectToast() {
            return "Nhấp vào nguồn lấy item trong tầm 380px!";
        }

        @Override
        protected String rangeToast() {
            return "Mục tiêu vượt quá phạm vi 380px!";
        }

        @Override
        public void updateTile() {
            super.updateTile();

            updateTargetSelection();

            isTeleporting = false;

            float dst = Mathf.dst(x, y, targetX, targetY);
            if (dst > 1 && dst <= maxRange) {
                Tile sourceTile = Vars.world.tileWorld(targetX, targetY);

                if (sourceTile != null && sourceTile.build != null) {
                    Building sourceBuild = sourceTile.build;

                    if (sourceBuild.items != null && sourceBuild.items.total() > 0) {
                        Item itemToPull = sourceBuild.items.first();

                        if (itemToPull != null && acceptItem(sourceBuild, itemToPull)
                            && items.get(itemToPull) < getMaximumAccepted(itemToPull)) {
                            sourceBuild.items.remove(itemToPull, 1);
                            handleItem(sourceBuild, itemToPull);
                            isTeleporting = true;

                            float angleToThis = Angles.angle(sourceBuild.x, sourceBuild.y, x, y);

                            if (Mathf.chance(0.3)) {
                                teleportEffect.at(
                                    sourceBuild.x + Mathf.range(3f),
                                    sourceBuild.y + Mathf.range(3f),
                                    angleToThis + Mathf.range(12f)
                                );
                            }

                            if (Mathf.chance(0.3)) {
                                receiveEffect.at(x, y, Mathf.random(360f));
                            }
                        }
                    }
                }
            }

            if (items != null && items.total() > 0) {
                dump();
            }
        }

        @Override
        public void draw() {
            super.draw();
            drawTeleportingMark(Pal.heal);
            drawSelecting(Pal.heal, true);
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            drawConfigured(Pal.heal, true);
        }
    }
}
