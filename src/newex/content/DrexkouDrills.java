package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import newex.PerfCore;

/**
 * Chuyển từ drexkou-drills.js. Máy khoan laser tầm xa (bán kính 200px): người chơi chọn 1 loại tài nguyên,
 * máy khóa 1 ô chứa tài nguyên đó (mỗi ô chỉ 1 máy khóa) và khai thác 100 vật phẩm/giây.
 * Nước +50%, Cryofluid +100%, Sallowyr +500% (10 giây).
 */
public final class DrexkouDrills {
    private static final String NAME = "newex-drexkou-drills";
    private static final float RANGE = 200f;

    /** Các ô đang bị máy khoan nào đó khóa (khóa theo (x << 16) | (y & 0xFFFF)). */
    private static final IntSet globalLockedTiles = new IntSet();

    private DrexkouDrills() {}

    private static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static int getTileKey(int x, int y) {
        return (x << 16) | (y & 0xFFFF);
    }

    private static Item sallowyrItem() {
        Item item = Vars.content.item("newex-sallowyr");
        return item != null ? item : Vars.content.item("sallowyr");
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block drill = Vars.content.block(NAME);
            if (drill != null) {
                drill.configurable = true;

                drill.hasItems = true;
                drill.itemCapacity = 1000;
                drill.hasLiquids = true;
                drill.liquidCapacity = 30f;

                drill.buildType = DrexkouBuild::new;
            }
        });

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && NAME.equals(build.name)) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    float centerX = tile.drawx() + build.offset;
                    float centerY = tile.drawy() + build.offset;
                    Drawf.dashCircle(centerX, centerY, 200f, Pal.accent);
                }
            }
        });
    }

    public static class DrexkouBuild extends Building {
        Tile targetTile = null;
        Item selectedItem = null;
        float mineTimer = 0f;
        /** Góc xoay nòng (JS gốc dùng thuộc tính riêng "rotation" = 90, tách khỏi rotation của công trình). */
        float barrelRotation = 90f;

        Seq<Tile> cachedTiles = new Seq<>();
        float sallowyrTimer = 0f;
        float retargetTimer = 0f;

        @Override
        public Object config() {
            return selectedItem;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(selectedItem != null ? selectedItem.id : -1);
            write.f(sallowyrTimer);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int itemId = read.s();
            if (itemId != -1) {
                selectedItem = Vars.content.item(itemId);
            } else {
                selectedItem = null;
            }
            sallowyrTimer = read.f();
        }

        @Override
        public void configured(Unit builder, Object value) {
            super.configured(builder, value);
            if (value instanceof Item) {
                selectedItem = (Item) value;
            } else {
                selectedItem = null;
            }

            releaseTarget();
            findTarget();
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return block.hasLiquids && (liquid == Liquids.water || liquid == Liquids.cryofluid);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            Item sallowyr = sallowyrItem();
            if (item == sallowyr) {
                return sallowyrTimer <= 0;
            }
            return false;
        }

        @Override
        public void placed() {
            super.placed();
            scanAndCacheTiles();
            findTarget();
        }

        @Override
        public void created() {
            super.created();
            Core.app.post(() -> {
                if (isAdded()) {
                    scanAndCacheTiles();
                    findTarget();
                }
            });
        }

        @Override
        public void onDestroyed() {
            releaseTarget();
            cachedTiles = new Seq<>();
            super.onDestroyed();
        }

        void scanAndCacheTiles() {
            cachedTiles = new Seq<>();
            int rangeTiles = (int) Math.ceil(RANGE / 8f);
            int tileX = (int) Math.floor(x / 8f);
            int tileY = (int) Math.floor(y / 8f);

            for (int dx = -rangeTiles; dx <= rangeTiles; dx++) {
                for (int dy = -rangeTiles; dy <= rangeTiles; dy++) {
                    Tile t = Vars.world.tile(tileX + dx, tileY + dy);
                    if (t != null && within(t.worldx(), t.worldy(), RANGE)) {
                        if (DrillUtil.getTileDrop(t) != null) {
                            cachedTiles.add(t);
                        }
                    }
                }
            }
        }

        void releaseTarget() {
            if (targetTile != null) {
                int key = getTileKey(targetTile.x, targetTile.y);
                globalLockedTiles.remove(key);
                targetTile = null;
            }
        }

        void setTarget(Tile tile) {
            releaseTarget();
            if (tile != null) {
                int key = getTileKey(tile.x, tile.y);
                globalLockedTiles.add(key);
                targetTile = tile;
            }
        }

        boolean isValidTarget(Tile tile) {
            if (tile == null) return false;
            if (selectedItem == null) return false;

            Item drop = DrillUtil.getTileDrop(tile);
            if (drop == null || drop != selectedItem) return false;

            if (tile.build != null) {
                if (!DrillUtil.isGraphiteWallName(tile.block().name)) return false;
            }

            if (items.get(drop) >= block.itemCapacity) {
                return false;
            }

            int key = getTileKey(tile.x, tile.y);
            if (globalLockedTiles.contains(key) && targetTile != tile) {
                return false;
            }

            return true;
        }

        void findTarget() {
            if (selectedItem == null || cachedTiles.size == 0) {
                setTarget(null);
                return;
            }

            for (int i = 0; i < cachedTiles.size; i++) {
                Tile t = cachedTiles.get(i);
                if (isValidTarget(t)) {
                    setTarget(t);
                    return;
                }
            }

            setTarget(null);
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();

            Seq<Item> itemsInArea = new Seq<>();
            for (int i = 0; i < cachedTiles.size; i++) {
                Item drop = DrillUtil.getTileDrop(cachedTiles.get(i));
                if (drop != null && !itemsInArea.contains(drop, true)) {
                    itemsInArea.add(drop);
                }
            }

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String infoText = isEn()
                    ? "[cyan]● Select Resource:[ ] Tap resource icons to select drill target. The drill will only operate after a resource is selected.\n\n"
                        + "[yellow]● Yield Boost Mechanics:[ ] Base yield is 100 items per second.\n"
                        + "  - [white]Water Supply:[ ] Boosts yield to [green]150 items/s (+50%)[ ].\n"
                        + "  - [white]Cryofluid Supply:[ ] Boosts yield to [green]200 items/s (+100%)[ ].\n"
                        + "  - [white]Sallowyr Item Absorption:[ ] Absorbs 1 [accent]Sallowyr[ ] to boost yield to [orange]600 items/s (+500%)[ ] for [stat]10 seconds[ ].\n\n"
                        + "[lightgray]Note: Combine liquid supply with Sallowyr to maximize drill output![ ]"
                    : "[cyan]● Chọn tài nguyên:[ ] Bấm vào các biểu tượng tài nguyên bên cạnh để bắt đầu khai thác. Máy chỉ hoạt động sau khi người chơi chủ động chọn tài nguyên.\n\n"
                        + "[yellow]● Cơ chế Tăng Số Lượng Thu Hoạch:[ ] Tốc độ cơ bản thu 100 item/giây.\n"
                        + "  - [white]Cấp Nước (Water):[ ] Tăng sản lượng lên [green]150 item/giây (+50%)[ ].\n"
                        + "  - [white]Cấp Chất làm lạnh (Cryofluid):[ ] Tăng sản lượng lên [green]200 item/giây (+100%)[ ].\n"
                        + "  - [white]Hấp thụ Item Sallowyr:[ ] Tăng sản lượng lên [orange]600 item/giây (+500%)[ ] trong [stat]10 giây[ ].\n\n"
                        + "[lightgray]Lưu ý: Kết hợp Chất lưu và Sallowyr cùng lúc để tối đa hóa lượng item thu được![ ]";

                DrillUtil.showManual(
                    isEn() ? "[accent]Drill Block Manual[ ]" : "[accent]Hướng Dẫn Sử Dụng Khối Khoan[ ]",
                    infoText,
                    isEn() ? "Close" : "Đóng");
            }).size(44f).pad(2f);

            int count = 1;
            for (int i = 0; i < itemsInArea.size; i++) {
                final Item item = itemsInArea.get(i);

                ImageButton btn = table.button(new TextureRegionDrawable(item.uiIcon), Styles.clearTogglei, 40f, () -> {
                    Item nextItem = (selectedItem == item) ? null : item;
                    configure(nextItem);
                    deselect();
                }).size(44f).pad(2f).get();

                btn.setChecked(selectedItem == item);

                count++;
                if (count % 4 == 0) table.row();
            }
        }

        @Override
        public void updateTile() {
            if (!Vars.net.client()) {
                Item sallowyr = sallowyrItem();

                if (sallowyr != null && items.has(sallowyr)) {
                    items.remove(sallowyr, 1);
                    sallowyrTimer = 600f;
                    Call.effect(Fx.upgradeCore, x, y, 0f, Color.sky);
                }

                double boostMultiplier = 1.0;

                if (liquids.get(Liquids.cryofluid) > 0.01f) {
                    boostMultiplier += 1.0;
                    liquids.remove(Liquids.cryofluid, 0.15f * Time.delta);
                } else if (liquids.get(Liquids.water) > 0.01f) {
                    boostMultiplier += 0.5;
                    liquids.remove(Liquids.water, 0.2f * Time.delta);
                }

                if (sallowyrTimer > 0) {
                    sallowyrTimer -= Time.delta;
                    boostMultiplier += 5.0;

                    if (Mathf.chance(0.1)) {
                        Call.effect(Fx.reactorsmoke, x + Mathf.range(4f), y + Mathf.range(4f), 0f, Color.sky);
                    }
                }

                if (items.total() > 0) {
                    dump();
                }

                if (efficiency > 0 && selectedItem != null) {
                    if (!isValidTarget(targetTile)) {
                        retargetTimer += Time.delta;
                        if (retargetTimer >= 20) {
                            retargetTimer = 0f;
                            releaseTarget();
                            findTarget();
                        }
                    } else {
                        retargetTimer = 0f;
                    }

                    if (targetTile != null) {
                        Item item = DrillUtil.getTileDrop(targetTile);

                        if (item != null && items.get(item) < block.itemCapacity) {

                            mineTimer += Time.delta * efficiency;

                            if (mineTimer >= 60f) {
                                mineTimer -= 60f;

                                int amountToAdd = (int) Math.floor(100 * boostMultiplier);
                                items.add(item, amountToAdd);

                                try {
                                    Call.effect(Fx.mine, targetTile.worldx(), targetTile.worldy(), 0f, item.color);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }
            }

            if (targetTile != null) {
                float tx = targetTile.worldx();
                float ty = targetTile.worldy();
                float targetAngle = Angles.angle(x, y, tx, ty);
                barrelRotation = Angles.moveToward(barrelRotation, targetAngle, 5f);
            }
        }

        @Override
        public void drawSelect() {
            Drawf.dashCircle(x, y, RANGE, Pal.accent);
        }

        @Override
        public void draw() {
            super.draw();

            TextureRegion region = PerfCore.region(block.name + "-barr");
            if (region.found()) {
                Draw.rect(region, x, y, barrelRotation - 90f);
            }

            Item currentItem = targetTile != null ? DrillUtil.getTileDrop(targetTile) : null;

            if (efficiency > 0 && targetTile != null) {
                float tx = targetTile.worldx();
                float ty = targetTile.worldy();

                Draw.z(Layer.power + 1);

                Color laserColor = currentItem != null ? currentItem.color : Pal.accent;
                float basePulse = Mathf.absin(Time.time, 4f, 0.2f);

                Draw.color(laserColor, 0.35f + basePulse);
                Lines.stroke(5.5f);
                Lines.line(x, y, tx, ty);

                Draw.color(laserColor, 0.85f);
                Lines.stroke(3f);
                Lines.line(x, y, tx, ty);

                Draw.color(Color.white, 0.9f);
                Lines.stroke(1.2f);
                Lines.line(x, y, tx, ty);

                Draw.color(laserColor, 0.4f);
                Fill.circle(tx, ty, 6f + Mathf.absin(Time.time, 3f, 2f));
                Draw.color(Color.white);
                Fill.circle(tx, ty, 2.5f);

                boolean showGlowParticles = Core.settings.getBool("bloom", true) && Core.settings.getBool("effects", true);

                if (showGlowParticles) {
                    float laserAngle = Angles.angle(tx, ty, x, y);
                    float cosA = Mathf.cosDeg(laserAngle + 90f);
                    float sinA = Mathf.sinDeg(laserAngle + 90f);

                    for (int i = 0; i < 2; i++) {
                        float progress = (Time.time * 0.025f + i * 0.5f) % 1f;
                        float baseX = Mathf.lerp(tx, x, progress);
                        float baseY = Mathf.lerp(ty, y, progress);

                        float offset = Mathf.sin(Time.time * 0.15f + i * 2f) * 6f;

                        float px = baseX + cosA * offset;
                        float py = baseY + sinA * offset;

                        float particleSpin = Time.time * 6f + i * 90f;

                        Draw.color(laserColor, 0.4f);
                        Fill.poly(px, py, 3, 4.8f, particleSpin);

                        Draw.color(laserColor, 0.95f);
                        Fill.poly(px, py, 3, 2.8f, particleSpin);
                    }
                }

                Draw.reset();
            }
        }
    }
}
