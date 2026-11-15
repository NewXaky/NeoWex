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
 * Chuyển từ xeuw-conveyor.js. Cổng Xeuw mk1: đẩy tối đa 9 vật phẩm mỗi tick từ cổng sang
 * công trình ở vị trí mục tiêu (trong tầm 380px).
 */
public final class XeuwConveyor {
    private static final String NAME = "newex-xeuw-conveyor-mk1";

    private static final Effect teleportEffect = new Effect(20f, e -> {
        Draw.color(Color.sky, Color.white, e.fin());
        float rad = e.rotation * Mathf.degRad;
        float speed = 25f * e.fin();
        float px = e.x + Mathf.cos(rad) * speed;
        float py = e.y + Mathf.sin(rad) * speed;
        Fill.poly(px, py, 3, 5f * e.fout(), e.rotation);
    });

    private static final Effect receiveEffect = new Effect(25f, e -> {
        Draw.color(Color.sky, Pal.heal, e.fin());
        float radius = 14f * e.fout();
        float angle = e.rotation + e.fin() * 180f;
        float rad = angle * Mathf.degRad;
        float rx = e.x + Mathf.cos(rad) * radius;
        float ry = e.y + Mathf.sin(rad) * radius;
        Fill.poly(rx, ry, 3, 4f * e.fout(), angle);
    });

    private XeuwConveyor() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.block(NAME);
            if (b instanceof StorageBlock) {
                final StorageBlock block = (StorageBlock) b;
                block.buildType = () -> new XeuwBuild(block);
            }
        });

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && NAME.equals(build.name)) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    float centerX = tile.drawx() + build.offset;
                    float centerY = tile.drawy() + build.offset;
                    Drawf.dashCircle(centerX, centerY, GateBuild.MAX_RANGE, Color.sky);
                    Drawf.square(centerX, centerY, 4f, 0f, Color.sky);
                }
            }
        });
    }

    public static class XeuwBuild extends GateBuild {
        private final StorageBlock gateBlock;

        public XeuwBuild(StorageBlock outer) {
            super(outer);
            this.gateBlock = outer;
        }

        @Override
        protected String selectToast() {
            return "Nhấp vào điểm đích trong tầm 380px!";
        }

        @Override
        protected String rangeToast() {
            return "Mục tiêu vượt quá phạm vi 380px của Mk1!";
        }

        @Override
        public void updateTile() {
            super.updateTile();

            updateTargetSelection();

            isTeleporting = false;

            float dst = Mathf.dst(x, y, targetX, targetY);
            if (items != null && items.total() > 0 && dst > 1 && dst <= maxRange) {
                Tile destTile = Vars.world.tileWorld(targetX, targetY);

                if (destTile != null && destTile.build != null) {
                    Building targetBuild = destTile.build;
                    Item itemToTransfer = items.first();

                    if (itemToTransfer != null && targetBuild.acceptItem(this, itemToTransfer)) {
                        int transferAmount = Math.min(items.get(itemToTransfer), (int) Math.ceil(500.0 / 60.0));

                        if (transferAmount > 0) {
                            items.remove(itemToTransfer, transferAmount);
                            targetBuild.handleItem(this, itemToTransfer);
                            isTeleporting = true;

                            float angleToTarget = Angles.angle(x, y, targetBuild.x, targetBuild.y);

                            if (Mathf.chance(0.4)) {
                                teleportEffect.at(
                                    x + Mathf.range(3f),
                                    y + Mathf.range(3f),
                                    angleToTarget + Mathf.range(12f)
                                );
                            }

                            if (Mathf.chance(0.4)) {
                                receiveEffect.at(targetBuild.x, targetBuild.y, Mathf.random(360f));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void draw() {
            Draw.rect(gateBlock.region, x, y);
            drawTeleportingMark(Color.sky);
            drawSelecting(Color.sky, false);
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            drawConfigured(Color.sky, false);
        }
    }
}
