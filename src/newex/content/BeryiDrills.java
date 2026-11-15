package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ beryi-drills.js. Máy khoan tự động diện rộng: mỗi giây khai thác ngẫu nhiên 1 ô trong vuông 51x51 ô quanh nó.
 * Nước +50%, Cryofluid +100%, Sallowyr +500% (10 giây), Copes Block nhân thêm. Đủ 1000 vật phẩm thì hiện nút "V"
 * để đổi thành Core Raykstone.
 */
public final class BeryiDrills {
    private static final String NAME = "newex-beryi-drills";
    private static final int RADIUS_TILES = 25;

    private static final Effect customExplosion = new Effect(35f, e -> {
        if (Vars.state.isPaused()) {
            e.time -= Time.delta;
        }
        Draw.color(Color.purple, Color.lightGray, e.fin());
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, e.fin() * 24f);
    });

    private BeryiDrills() {}

    private static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
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

                drill.buildType = BeryiBuild::new;
            }
        });

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && NAME.equals(build.name)) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    int minX = tile.x - RADIUS_TILES;
                    int maxX = tile.x + RADIUS_TILES;
                    int minY = tile.y - RADIUS_TILES;
                    int maxY = tile.y + RADIUS_TILES;

                    float worldLeft = minX * 8;
                    float worldBottom = minY * 8;
                    float boxWidth = (maxX - minX + 1) * 8;
                    float boxHeight = (maxY - minY + 1) * 8;

                    Draw.color(Pal.accent, 0.12f);
                    Fill.rect(worldLeft + boxWidth / 2f, worldBottom + boxHeight / 2f, boxWidth, boxHeight);
                    Draw.color(Pal.accent);
                    Lines.stroke(1.5f);
                    Lines.rect(worldLeft, worldBottom, boxWidth, boxHeight);
                    Draw.reset();
                }
            }
        });
    }

    public static class BeryiBuild extends Building implements DrillBoostable {
        float mineTimer = 0f;
        /** JS gốc có thuộc tính riêng rotation = 90 (không đổi), nên nòng luôn vẽ với góc 90 - 90 = 0. */
        final float barrelRotation = 90f;

        Seq<Tile> cachedTiles = new Seq<>();
        float sallowyrTimer = 0f;
        float copesBoostTime = 0f;
        float copesBoostIntensity = 1f;

        @Override
        public void applyBoost(float intensity, float duration) {
            copesBoostIntensity = intensity;
            copesBoostTime = duration;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(sallowyrTimer);
            write.f(copesBoostTime);
            write.f(copesBoostIntensity);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            sallowyrTimer = read.f();
            copesBoostTime = read.f();
            copesBoostIntensity = read.f();
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
        }

        @Override
        public void created() {
            super.created();
            Core.app.post(() -> {
                if (isAdded()) {
                    scanAndCacheTiles();
                }
            });
        }

        @Override
        public void onDestroyed() {
            cachedTiles = new Seq<>();
            super.onDestroyed();
        }

        void scanAndCacheTiles() {
            cachedTiles = new Seq<>();
            int tX = tileX();
            int tY = tileY();

            for (int dx = -RADIUS_TILES; dx <= RADIUS_TILES; dx++) {
                for (int dy = -RADIUS_TILES; dy <= RADIUS_TILES; dy++) {
                    Tile t = Vars.world.tile(tX + dx, tY + dy);
                    if (t != null && DrillUtil.getTileDrop(t) != null) {
                        cachedTiles.add(t);
                    }
                }
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clearChildren();

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String infoText = isEn()
                    ? "[cyan]● Automatic Square Area Mining:[ ] Automatically mines random resources within a perfectly grid-aligned 25-tile square range.\n\n"
                        + "[yellow]● Yield Boost Mechanics:[ ] Base yield is 2 items per second.\n"
                        + "  - [white]Water Supply:[ ] Boosts yield to [green]3 items/s (+50%)[ ].\n"
                        + "  - [white]Cryofluid Supply:[ ] Boosts yield to [green]4 items/s (+100%)[ ].\n"
                        + "  - [white]Sallowyr Item Absorption:[ ] Absorbs 1 [accent]Sallowyr[ ] to boost yield to [orange]12 items/s (+500%)[ ] for [stat]10 seconds[ ].\n\n"
                        + "[lightgray]Note: When inventory reaches 1000 items, a 'V' conversion button appears![ ]"
                    : "[cyan]● Khai thác tự động vùng hình vuông:[ ] Tự động ngẫu nhiên khai thác tài nguyên trong phạm vi hình vuông 25 ô.\n\n"
                        + "[yellow]● Cơ chế Tăng Số Lượng Thu Hoạch:[ ] Tốc độ cơ bản thu 2 item/giây.\n"
                        + "  - [white]Cấp Nước (Water):[ ] Tăng sản lượng lên [green]3 item/giây (+50%)[ ].\n"
                        + "  - [white]Cấp Chất làm lạnh (Cryofluid):[ ] Tăng sản lượng lên [green]4 item/giây (+100%)[ ].\n"
                        + "  - [white]Hấp thụ Item Sallowyr:[ ] Tăng sản lượng lên [orange]12 item/giây (+500%)[ ] trong [stat]10 giây[ ].\n\n"
                        + "[lightgray]Lưu ý: Khi kho chứa đạt đủ 1000 item, nút chuyển đổi 'V' sẽ xuất hiện![ ]";

                DrillUtil.showManual(
                    isEn() ? "[accent]Beryi Drill Manual[]" : "[accent]Hướng Dẫn Sử Dụng Khối Khoan Beryi[]",
                    infoText,
                    isEn() ? "Close" : "Đóng");
            }).size(44f).pad(2f);

            if (items.total() >= 1000) {
                table.button("V", () -> {
                    if (!Vars.net.client()) {
                        Block coreBlock = Vars.content.block("newex-core-raykstone");
                        Tile centerTile = Vars.world.tileWorld(x, y);

                        if (centerTile != null) {
                            remove();

                            if (coreBlock != null) {
                                centerTile.setBlock(coreBlock, team, 0);
                            }

                            NexNet.fx(customExplosion, x, y, 0f, Color.purple);
                        }
                    }
                }).size(44f).pad(2f);
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

                if (copesBoostTime > 0) {
                    copesBoostTime -= Time.delta;
                    boostMultiplier *= copesBoostIntensity;
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

                if (efficiency > 0 && cachedTiles.size > 0) {
                    mineTimer += Time.delta * efficiency;

                    if (mineTimer >= 60f) {
                        mineTimer -= 60f;

                        int idx = (int) Math.floor(Mathf.random(0f, cachedTiles.size));
                        Tile randomTile = idx >= 0 && idx < cachedTiles.size ? cachedTiles.get(idx) : null;
                        Item item = DrillUtil.getTileDrop(randomTile);

                        if (item != null) {
                            int amountToAdd = (int) Math.max(1, Math.floor(2 * boostMultiplier));
                            items.add(item, amountToAdd);

                            try {
                                Call.effect(Fx.mine, randomTile.worldx(), randomTile.worldy(), 0f, item.color);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }
        }

        private boolean hasDropAt(int tx, int ty, int minX, int maxX, int minY, int maxY) {
            Tile t = Vars.world.tile(tx, ty);
            if (t == null) return false;
            if (t.x < minX || t.x > maxX || t.y < minY || t.y > maxY) return false;
            Item d = DrillUtil.getTileDrop(t);
            return d != null && d != Items.sand;
        }

        @Override
        public void drawSelect() {
            int tX = tileX();
            int tY = tileY();
            int minX = tX - RADIUS_TILES;
            int maxX = tX + RADIUS_TILES;
            int minY = tY - RADIUS_TILES;
            int maxY = tY + RADIUS_TILES;

            float worldLeft = minX * 8;
            float worldBottom = minY * 8;
            float boxWidth = (maxX - minX + 1) * 8;
            float boxHeight = (maxY - minY + 1) * 8;

            Draw.color(Pal.accent, 0.12f);
            Fill.rect(worldLeft + boxWidth / 2f, worldBottom + boxHeight / 2f, boxWidth, boxHeight);

            Draw.color(Pal.accent);
            Lines.stroke(1.5f);
            Lines.rect(worldLeft, worldBottom, boxWidth, boxHeight);

            if (cachedTiles != null && cachedTiles.size > 0) {
                Draw.color(Pal.accent, 0.85f);
                Lines.stroke(1f);
                for (int i = 0; i < cachedTiles.size; i++) {
                    Tile t = cachedTiles.get(i);
                    if (t != null) {
                        Item itemDrop = DrillUtil.getTileDrop(t);

                        if (itemDrop == Items.sand) continue;

                        float wx = t.worldx();
                        float wy = t.worldy();

                        boolean hasRight = hasDropAt(t.x + 1, t.y, minX, maxX, minY, maxY);
                        boolean hasLeft = hasDropAt(t.x - 1, t.y, minX, maxX, minY, maxY);
                        boolean hasTop = hasDropAt(t.x, t.y + 1, minX, maxX, minY, maxY);
                        boolean hasBottom = hasDropAt(t.x, t.y - 1, minX, maxX, minY, maxY);

                        if (!hasLeft) Lines.line(wx - 4, wy - 4, wx - 4, wy + 4);
                        if (!hasRight) Lines.line(wx + 4, wy - 4, wx + 4, wy + 4);
                        if (!hasBottom) Lines.line(wx - 4, wy - 4, wx + 4, wy - 4);
                        if (!hasTop) Lines.line(wx - 4, wy + 4, wx + 4, wy + 4);
                    }
                }
            }
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();

            TextureRegion region = PerfCore.region(block.name + "-barr");
            if (region.found()) {
                Draw.rect(region, x, y, barrelRotation - 90f);
            }
        }
    }
}
