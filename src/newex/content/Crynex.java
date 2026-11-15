package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * Chuyển từ crynex.js. Khối tạo Cryofluid: chọn 1 vùng mục tiêu (vuông 51x51 ô quanh khối),
 * khi đứng cạnh Korynd và có hơn 10 Cryofluid thì "dịch chuyển" Cryofluid sang công trình đồng minh trong vùng mục tiêu.
 */
public final class Crynex {
    private static final float BASE_MAX_RANGE = 204f;
    private static final float TARGET_BOX_RADIUS = 84f;

    private static final Effect customLaserEffect = new Effect(60f, e -> {
        Draw.color(Color.cyan);
        Lines.stroke(3f * e.fout());
        if (e.data instanceof Float) {
            Lines.line(e.x, e.y, e.rotation, (Float) e.data);
        }
        Draw.reset();
    });

    private Crynex() {}

    private static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-crynex");
            if (!(b instanceof GenericCrafter)) return;
            final GenericCrafter block = (GenericCrafter) b;

            block.hasLiquids = true;
            block.liquidCapacity = 5000f;
            block.configurable = true;

            block.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof CrynexBuild) {
                    ((CrynexBuild) tile).setTargetPos(value);
                }
            });

            block.buildType = () -> new CrynexBuild(block);
        });

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && "newex-crynex".equals(build.name)) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    float cx = tile.drawx() + build.offset;
                    float cy = tile.drawy() + build.offset;
                    float halfSize = 204f;

                    Drawf.dashLine(Color.cyan, cx - halfSize, cy - halfSize, cx + halfSize, cy - halfSize);
                    Drawf.dashLine(Color.cyan, cx + halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
                    Drawf.dashLine(Color.cyan, cx + halfSize, cy + halfSize, cx - halfSize, cy + halfSize);
                    Drawf.dashLine(Color.cyan, cx - halfSize, cy + halfSize, cx - halfSize, cy - halfSize);

                    Drawf.square(cx, cy, 4f, 0f, Color.cyan);
                }
            }
        });
    }

    /** GenericCrafterBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class CrynexBuild extends GenericCrafter.GenericCrafterBuild {
        float targetX = 0f;
        float targetY = 0f;
        boolean hasTargetSet = false;
        boolean selectingTarget = false;
        float laserTimer = 0f;

        float scanTimer = 0f;
        Seq<Building> cachedTargets = null;
        float cachedX = Float.NaN;
        float cachedY = Float.NaN;
        boolean hasKoryndCached = false;

        public CrynexBuild(GenericCrafter outer) {
            outer.super();
        }

        @Override
        public void created() {
            super.created();
            targetX = x;
            targetY = y;
        }

        @Override
        public void placed() {
            super.placed();
            targetX = x;
            targetY = y;
        }

        public float range() {
            return BASE_MAX_RANGE;
        }

        public void setTargetPos(int val) {
            Point2 pos = Point2.unpack(val);
            float destX = pos.x * Vars.tilesize;
            float destY = pos.y * Vars.tilesize;

            if (Math.abs(destX - x) <= range() && Math.abs(destY - y) <= range()) {
                targetX = destX;
                targetY = destY;
                hasTargetSet = true;
            }
        }

        @Override
        public Object config() {
            if (hasTargetSet) {
                int tileX = (int) Math.floor(targetX / Vars.tilesize);
                int tileY = (int) Math.floor(targetY / Vars.tilesize);
                return Integer.valueOf(Point2.pack(tileX, tileY));
            }
            return null;
        }

        @Override
        public void configured(Unit builder, Object value) {
            super.configured(builder, value);
            if (value instanceof Number) {
                setTargetPos(((Number) value).intValue());
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(targetX);
            write.f(targetY);
            write.bool(hasTargetSet);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            targetX = read.f();
            targetY = read.f();
            hasTargetSet = read.bool();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (selectingTarget) {
                if (Core.input.keyTap(KeyCode.mouseLeft)) {
                    Vec2 worldVec = Core.camera.unproject(Core.input.mouse());
                    Tile tile = Vars.world.tileWorld(worldVec.x, worldVec.y);

                    if (tile != null) {
                        configure(Integer.valueOf(Point2.pack(tile.x, tile.y)));
                    }
                    selectingTarget = false;
                }
            }

            float cryoAmount = liquids.get(Liquids.cryofluid);

            laserTimer += delta();
            boolean shouldShowLaser = laserTimer >= 60f;

            scanTimer += delta();
            if (scanTimer >= 30 || cachedTargets == null || cachedX != targetX || cachedY != targetY) {
                scanTimer = 0f;
                cachedX = targetX;
                cachedY = targetY;
                hasKoryndCached = false;
                if (proximity != null) {
                    proximity.each(other -> {
                        if (other != null && other.block != null && "newex-korynd".equals(other.block.name)) {
                            hasKoryndCached = true;
                        }
                    });
                }

                final Seq<Building> found = new Seq<>();
                cachedTargets = found;
                if (hasKoryndCached && hasTargetSet) {
                    Units.nearbyBuildings(targetX, targetY, 84f * 1.5f, f -> {
                        if (f != null) found.add(f);
                    });
                }
            }

            boolean hasKoryndAdjacent = hasKoryndCached;

            if (hasKoryndAdjacent && cryoAmount > 10 && hasTargetSet) {
                Seq<Building> targetList = cachedTargets;

                for (int ci = 0; ci < targetList.size; ci++) {
                    Building other = targetList.get(ci);
                    if (other != null && other.team == team && other.isValid() && other != this) {
                        if (Math.abs(other.x - targetX) <= TARGET_BOX_RADIUS && Math.abs(other.y - targetY) <= TARGET_BOX_RADIUS) {

                            if (other.block.hasLiquids && other.liquids != null) {
                                float maxCap = other.block.liquidCapacity;
                                float currentLiquid = other.liquids.get(Liquids.cryofluid);

                                if (currentLiquid < maxCap) {
                                    double transferRate = (100.0 / 60.0) * delta();
                                    double amountToMove = Math.min(transferRate, liquids.get(Liquids.cryofluid) - 10.0);
                                    amountToMove = Math.min(amountToMove, maxCap - currentLiquid);

                                    if (amountToMove > 0) {
                                        liquids.remove(Liquids.cryofluid, (float) amountToMove);
                                        other.handleLiquid(this, Liquids.cryofluid, (float) amountToMove);

                                        if (shouldShowLaser) {
                                            customLaserEffect.at(x, y, other.x, Float.valueOf(other.y));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (shouldShowLaser) {
                laserTimer = 0f;
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();

            table.button(Icon.commandRally, Styles.cleari, 40f, () -> {
                deselect();
                selectingTarget = true;
                Vars.ui.hudfrag.showToast(isEn()
                    ? "Select target location to teleport Cryofluid!"
                    : "Chọn vị trí mục tiêu để dịch chuyển Chất làm lạnh!");
            }).size(50f, 40f).tooltip(isEn() ? "Set Teleport Target Zone" : "Đặt vị trí Dịch chuyển Chất làm lạnh");

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String descStr = isEn()
                    ? "[accent]Crynex Teleport Info[]\n\n"
                        + "• Liquid Capacity: [cyan]5000[] units.\n"
                        + "• Range: [cyan]51x51 blocks[].\n"
                        + "• Transfer Speed: [yellow]100 units/s[].\n"
                        + "• When holding [cyan]> 10 Cryofluid[], teleports Cryofluid directly to allied buildings in target zone."
                    : "[accent]Thông số khối Crynex[]\n\n"
                        + "• Sức chứa chất lỏng: [cyan]5000[] units.\n"
                        + "• Phạm vi hoạt động: [cyan]Vùng vuông 51x51 ô[].\n"
                        + "• Tốc độ dịch chuyển: [yellow]100 đơn vị/giây[].\n"
                        + "• Khi có [cyan]> 10 Chất làm lạnh[], tự động dịch chuyển đến công trình đồng minh tại khu vực mục tiêu.";

                BaseDialog dialog = new BaseDialog(isEn() ? " Crynex Stats " : " Thông số Crynex ");
                Table infoTable = new Table();
                Cell<Label> cell = infoTable.add(descStr).width(360f);
                cell.get().setWrap(true);
                cell.get().setAlignment(Align.left);
                ScrollPane scroll = new ScrollPane(infoTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton();
                dialog.show();
            }).size(50f, 40f).tooltip(isEn() ? "View detailed info" : "Mô tả chi tiết khối Crynex");
        }

        void drawSquareRange(float cx, float cy, float halfSize, Color color) {
            Drawf.dashLine(color, cx - halfSize, cy - halfSize, cx + halfSize, cy - halfSize);
            Drawf.dashLine(color, cx + halfSize, cy - halfSize, cx + halfSize, cy + halfSize);
            Drawf.dashLine(color, cx + halfSize, cy + halfSize, cx - halfSize, cy + halfSize);
            Drawf.dashLine(color, cx - halfSize, cy + halfSize, cx - halfSize, cy - halfSize);
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawSquareRange(x, y, range(), Color.cyan);
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            float currentRange = range();

            drawSquareRange(x, y, currentRange, Color.cyan);

            if (hasTargetSet && Math.abs(targetX - x) <= currentRange && Math.abs(targetY - y) <= currentRange) {
                Drawf.dashLine(Color.cyan, x, y, targetX, targetY);
                drawSquareRange(targetX, targetY, 84f, Color.cyan);
                Drawf.square(targetX, targetY, 6f, 0f, Color.cyan);
            }
        }

        @Override
        public void draw() {
            super.draw();

            if (selectingTarget) {
                float currentRange = range();
                drawSquareRange(x, y, currentRange, Color.cyan);

                Vec2 mouseWorld = Core.camera.unproject(Core.input.mouse());
                boolean isWithinRange = Math.abs(mouseWorld.x - x) <= currentRange && Math.abs(mouseWorld.y - y) <= currentRange;
                Color targetColor = isWithinRange ? Color.cyan : Color.red;

                Drawf.dashLine(targetColor, x, y, mouseWorld.x, mouseWorld.y);
                drawSquareRange(mouseWorld.x, mouseWorld.y, 84f, targetColor);

                Tile mouseTile = Vars.world.tileWorld(mouseWorld.x, mouseWorld.y);
                if (mouseTile != null) {
                    Drawf.square(mouseTile.drawx(), mouseTile.drawy(), 4f, 0f, targetColor);
                }
            }
        }
    }
}
