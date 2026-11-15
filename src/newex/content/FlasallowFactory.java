package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * Chuyển từ flasallow-factory.js. Mỗi mẻ (185 tick): 50 Đồng/Chì/Titan/Silicon/Thorium + 150 Dầu mỏ
 * ra 100 Phase Fabric + 100 Surge Alloy + 100 Plastanium. Đặt cạnh Redstone Wall x5 Surge, cạnh Emeralift Wall x5 Plastanium
 * (mất tường khi đang bật buff thì nổ). Nạp newex-obs để tăng tốc x16, nhưng sau 5 phút sẽ nổ bán kính 1600 px.
 */
public final class FlasallowFactory {
    private static final Color COLOR_SURGE = Color.valueOf("f3e979");
    private static final Color COLOR_EMERALIFT = Color.valueOf("38e250");
    private static final Color COLOR_REDSTONE = Color.valueOf("ff3b3b");

    private static final float BASE_CRAFT_TIME = 185f;
    private static final float OBS_FUSE = 5f * 60f * 60f;

    private static final Effect doubleSuperExplosionFx = new Effect(80f, e -> {
        float maxRadius = 1600f;
        float innerRadius = maxRadius * e.fin();

        Draw.color(Color.scarlet);
        Draw.alpha(0.4f * e.fout());

        float fillThickness = maxRadius - innerRadius;
        if (fillThickness > 0) {
            Lines.stroke(fillThickness);
            Lines.circle(e.x, e.y, innerRadius + fillThickness / 2f);
        }

        Draw.color(Color.white);
        Draw.alpha(e.fout());
        Lines.stroke(10f * e.fout());
        Lines.circle(e.x, e.y, maxRadius);

        Draw.color(Color.orange);
        Draw.alpha(e.fout());
        Lines.stroke(6f * e.fout());
        Lines.circle(e.x, e.y, innerRadius);
    });

    private static Block emeraliftWall, redstoneWall;
    private static Item itemNewexObs, itemNewexSallowyr;

    private FlasallowFactory() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-flasallow-factory");
            if (!(b instanceof GenericCrafter)) return;
            final GenericCrafter factory = (GenericCrafter) b;

            factory.configurable = true;
            factory.hasLiquids = true;
            factory.hasItems = true;

            if (factory.liquidCapacity <= 0) {
                factory.liquidCapacity = 500f;
            }

            emeraliftWall = Vars.content.getByName(ContentType.block, "newex-emeralift-wall");
            redstoneWall = Vars.content.getByName(ContentType.block, "newex-redstone-wall");
            itemNewexObs = Vars.content.getByName(ContentType.item, "newex-obs");
            itemNewexSallowyr = Vars.content.getByName(ContentType.item, "newex-sallowyr");

            factory.setBars();
            factory.addBar("obs_status", (Building building) -> {
                final FlasBuild fb = (FlasBuild) building;
                return new Bar(
                    () -> {
                        if (fb.obsTimer > 0) {
                            return FactoryFx.isEn()
                                ? "[scarlet]EXPLODE DUE TO OBS IN: " + (long) Math.ceil(fb.obsTimer / 60.0) + "s[]"
                                : "[scarlet]NỔ DO OBS TRONG: " + (long) Math.ceil(fb.obsTimer / 60.0) + "s[]";
                        }
                        return FactoryFx.isEn() ? "STATUS: SAFE" : "TRẠNG THÁI: AN TOÀN";
                    },
                    () -> fb.obsTimer > 0 ? Color.red : Color.green,
                    () -> fb.obsTimer > 0 ? (fb.obsTimer / OBS_FUSE) : 1f
                );
            });

            factory.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof FlasBuild) {
                    int val = value;
                    if (val == 0 || val == 1) {
                        ((FlasBuild) tile).setBuffEnabled(val == 1);
                    }
                }
            });

            factory.buildType = () -> new FlasBuild(factory);
        });
    }

    /** GenericCrafterBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class FlasBuild extends GenericCrafter.GenericCrafterBuild {
        private final GenericCrafter factory;

        float craftTimer = 0f;
        float obsTimer = 0f;
        float sallowyrCounter = 0f;

        boolean buffEnabled = true;
        boolean hasEmeraliftNearby = false;
        boolean hasRedstoneNearby = false;

        int lastEmeraliftCount = 0;
        int lastRedstoneCount = 0;

        public FlasBuild(GenericCrafter outer) {
            outer.super();
            this.factory = outer;
        }

        @Override
        public void created() {
            super.created();
            craftTimer = 0f;
            obsTimer = 0f;
            sallowyrCounter = 0f;

            buffEnabled = true;
            hasEmeraliftNearby = false;
            hasRedstoneNearby = false;

            lastEmeraliftCount = 0;
            lastRedstoneCount = 0;
        }

        public void setBuffEnabled(boolean enabled) {
            buffEnabled = enabled;
            if (!enabled) {
                lastEmeraliftCount = 0;
                lastRedstoneCount = 0;
                hasEmeraliftNearby = false;
                hasRedstoneNearby = false;
            }
        }

        public boolean isBuffEnabled() {
            return buffEnabled;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items == null) return false;

            if (itemNewexObs != null && item == itemNewexObs) {
                return items.get(itemNewexObs) < factory.itemCapacity;
            }

            if (item == Items.copper || item == Items.lead || item == Items.titanium || item == Items.silicon || item == Items.thorium) {
                return items.get(item) < factory.itemCapacity;
            }

            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return (liquid == Liquids.cryofluid || liquid == Liquids.oil)
                && liquids != null && liquids.get(liquid) < factory.liquidCapacity;
        }

        void checkNearbyWalls() {
            if (!buffEnabled) {
                hasEmeraliftNearby = false;
                hasRedstoneNearby = false;
                lastEmeraliftCount = 0;
                lastRedstoneCount = 0;
                return;
            }

            int currentEmeralift = 0;
            int currentRedstone = 0;

            int range = (int) Math.ceil(factory.size / 2.0) + 1;
            int startX = tileX() - range;
            int startY = tileY() - range;
            int endX = tileX() + range;
            int endY = tileY() + range;

            for (int x = startX; x <= endX; x++) {
                for (int y = startY; y <= endY; y++) {
                    Tile tile = Vars.world.tile(x, y);
                    if (tile != null && tile.build != null && tile.build != this) {
                        Block block = tile.build.block;
                        if (emeraliftWall != null && block == emeraliftWall) {
                            currentEmeralift++;
                        }
                        if (redstoneWall != null && block == redstoneWall) {
                            currentRedstone++;
                        }
                    }
                }
            }

            if (currentEmeralift < lastEmeraliftCount || currentRedstone < lastRedstoneCount) {
                triggerDoubleSuperExplosion();
                return;
            }

            lastEmeraliftCount = currentEmeralift;
            lastRedstoneCount = currentRedstone;
            hasEmeraliftNearby = currentEmeralift > 0;
            hasRedstoneNearby = currentRedstone > 0;
        }

        void triggerDoubleSuperExplosion() {
            doubleSuperExplosionFx.at(x, y);
            FactoryFx.annihilate(this, 1600f, 50f, 160f);
        }

        @Override
        public void draw() {
            if (factory.drawer != null) {
                factory.drawer.draw(this);
            } else {
                super.draw();
            }

            if (efficiency > 0) {
                float progress = Math.min(1f, craftTimer / BASE_CRAFT_TIME);
                float radius = 25f * (1f - progress);

                Draw.color(COLOR_SURGE);
                Lines.stroke(2f * (1f - progress));
                Lines.circle(x, y, radius);
            }

            if (buffEnabled) {
                Draw.draw(Layer.overlayUI, () -> {
                    float detectRadius = (factory.size / 2f + 1f) * Vars.tilesize;

                    if (hasEmeraliftNearby) {
                        Draw.color(COLOR_EMERALIFT);
                        Draw.alpha(0.6f + 0.4f * Mathf.absin(Time.time, 6f, 1f));
                        Lines.stroke(2.5f);
                        Lines.circle(x, y, detectRadius);

                        Draw.alpha(0.15f);
                        Fill.circle(x, y, detectRadius);
                    }

                    if (hasRedstoneNearby) {
                        Draw.color(COLOR_REDSTONE);
                        Draw.alpha(0.6f + 0.4f * Mathf.absin(Time.time, 6f, 1f));
                        Lines.stroke(2.5f);
                        Lines.square(x, y, detectRadius);

                        Draw.alpha(0.15f);
                        Fill.square(x, y, detectRadius);
                    }
                    Draw.reset();
                });
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            if (buffEnabled) {
                Draw.draw(Layer.overlayUI, () -> {
                    float detectRadius = (factory.size / 2f + 1f) * Vars.tilesize;

                    Draw.color(COLOR_EMERALIFT);
                    Lines.stroke(2f);
                    Lines.circle(x, y, detectRadius);

                    Draw.color(COLOR_REDSTONE);
                    Lines.stroke(2f);
                    Lines.square(x, y, detectRadius);
                    Draw.reset();
                });
            }
        }

        @Override
        public void updateTile() {
            if (liquids == null || items == null) return;

            if (timer.get(0, 10f)) {
                checkNearbyWalls();
            }

            if (itemNewexObs != null && items.get(itemNewexObs) > 0) {
                if (obsTimer <= 0) {
                    obsTimer = OBS_FUSE;
                }

                sallowyrCounter += Time.delta;
                if (sallowyrCounter >= 60f) {
                    sallowyrCounter -= 60f;

                    items.remove(itemNewexObs, 1);

                    if (itemNewexSallowyr != null) {
                        items.add(itemNewexSallowyr, 1);
                    }
                }
            } else {
                sallowyrCounter = 0f;
            }

            boolean isObsActive = obsTimer > 0;
            if (isObsActive) {
                obsTimer -= Time.delta;

                if (obsTimer <= 0) {
                    triggerDoubleSuperExplosion();
                    return;
                }
            }

            if (items.get(Items.phaseFabric) > 0) dump(Items.phaseFabric);
            if (items.get(Items.surgeAlloy) > 0) dump(Items.surgeAlloy);
            if (items.get(Items.plastanium) > 0) dump(Items.plastanium);
            if (itemNewexSallowyr != null && items.get(itemNewexSallowyr) > 0) dump(itemNewexSallowyr);

            if (efficiency <= 0) return;

            float speedMultiplier = isObsActive ? 16f : 1f;

            if (items.get(Items.copper) >= 50
                && items.get(Items.lead) >= 50
                && items.get(Items.titanium) >= 50
                && items.get(Items.silicon) >= 50
                && items.get(Items.thorium) >= 50
                && liquids.get(Liquids.oil) >= 150) {

                craftTimer += Time.delta * speedMultiplier;

                if (craftTimer >= BASE_CRAFT_TIME) {
                    craftTimer = 0f;

                    items.remove(Items.copper, 50);
                    items.remove(Items.lead, 50);
                    items.remove(Items.titanium, 50);
                    items.remove(Items.silicon, 50);
                    items.remove(Items.thorium, 50);
                    liquids.remove(Liquids.oil, 150f);

                    int phaseAmount = 100;
                    int surgeAmount = (buffEnabled && hasRedstoneNearby) ? 100 * 5 : 100;
                    int plastAmount = (buffEnabled && hasEmeraliftNearby) ? 100 * 5 : 100;

                    items.add(Items.phaseFabric, phaseAmount);
                    items.add(Items.surgeAlloy, surgeAmount);
                    items.add(Items.plastanium, plastAmount);

                    FactoryFx.expandCircleFx.at(x, y, 0f, COLOR_SURGE);
                    Effect.shake(3f, 8f, x, y);
                }
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            table.button(Icon.settings, Styles.cleari, 40f, () -> {
                boolean newState = !buffEnabled;
                setBuffEnabled(newState);
                configure(Integer.valueOf(newState ? 1 : 0));
            }).update(btn -> {
                btn.getStyle().imageUpColor = buffEnabled ? Color.lime : Color.gray;
            }).size(50f, 40f);

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String title = FactoryFx.isEn() ? " Flasallow Factory Specs " : " Thông số Nhà máy Flasallow Factory ";
                String descStr = FactoryFx.isEn()
                    ? "[gold]⚡ CONSUMPTION & OUTPUT ⚡[]\n\n"
                        + "• [cyan]Recipe/batch:[] 50 Copper + 50 Lead + 50 Titanium + 50 Silicon + 50 Thorium + 150 Oil\n"
                        + "• [green]Base yield:[] 100 Phase Fabric + 100 Surge Alloy + 100 Plastanium\n\n"
                        + "[gold]⚡ NEARBY BUFF FEATURE ⚡[]\n"
                        + "• Press [yellow]Gear Button[] to [lime]Enable[] or [red]Disable[] Buff Detection & Range display.\n"
                        + "• Placed next to [green]Emeralift Wall[] ➔ Produces [lime]x5 Plastanium[] (500/batch)\n"
                        + "• Placed next to [red]Redstone Wall[] ➔ Produces [yellow]x5 Surge Alloy[] (500/batch)\n"
                        + "• [scarlet]⚠ WARNING:[] If connected buff wall is missing/destroyed while BUFF IS ON ➔ [red]INSTANT SUPEREXPLOSION![] (Disable buff mode before removing walls).\n\n"
                        + "[scarlet]⚡ NEWEX-OBS OVERLOAD ⚡[]\n"
                        + "• Consumes [accent]1 newex-obs/s[] to produce [cyan]1 newex-sallowyr[]\n"
                        + "  - Speeds up production by [yellow]1500%[] (x16 speed)\n"
                        + "  - [red]Triggers 5-min countdown (300s). Explodes when timer reaches 0![]\n\n"
                        + "[scarlet]⚠ EXPLOSION RADIUS ⚠[]\n"
                        + "• [red]EXPLOSION RADIUS: 1600 TILES![] Obliterates all surrounding Units & Blocks!"
                    : "[gold]⚡ NGUYÊN LIỆU TIÊU THỤ & ĐẦU RA ⚡[]\n\n"
                        + "• [cyan]Nguyên liệu/mẻ:[] 50 Đồng + 50 Chì + 50 Titanium + 50 Silicon + 50 Thorium + 150 Dầu mỏ\n"
                        + "• [green]Sản lượng cơ sở:[] 100 Sợi lượng tử + 100 Hợp kim + 100 Nhựa\n\n"
                        + "[gold]⚡ TÍNH NĂNG NHẬN BUFF LÂN CẬN ⚡[]\n"
                        + "• Nhấn [yellow]Nút Con Vít[] để [lime]Bật[] hoặc [red]Tắt[] tính năng nhận Buff & Hiển thị phạm vi.\n"
                        + "• Đặt cạnh [green]Emeralift Wall[] ➔ Sản xuất Plastanium [lime]gấp 5 lần[] (500/mẻ)\n"
                        + "• Đặt cạnh [red]Redstone Wall[] ➔ Sản xuất Surge Alloy [yellow]gấp 5 lần[] (500/mẻ)\n"
                        + "• [scarlet]⚠ CẢNH BÁO:[] Nếu khối tường buff lân cận bị phá hủy/mất khi BẬT BUFF ➔ [red]NHÀ MÁY LẬP TỨC NỔ SIÊU CẤP![] (Tắt nhận buff trước khi phá tường để an toàn).\n\n"
                        + "[scarlet]⚡ QUÁ TẢI TIÊU THỤ NEWEX-OBS ⚡[]\n"
                        + "• Tiêu thụ [accent]1 newex-obs/giây[] để tạo ra [cyan]1 newex-sallowyr[]\n"
                        + "  - Tăng tốc độ sản xuất lên [yellow]1500%[] (x16 lần tốc độ)\n"
                        + "  - [red]Kích hoạt đếm ngược 5 phút (300s). Hết 5 phút sẽ nổ siêu cấp![]\n\n"
                        + "[scarlet]⚠ BÁN KÍNH NỔ ⚠[]\n"
                        + "• [red]BÁN KÍNH NỔ: 1600 Ô![] San phẳng toàn bộ Unit & Block xung quanh!";

                FactoryFx.showSpecs(title, descStr);
            }).size(50f, 40f);
        }

        @Override
        public Object config() {
            return Integer.valueOf(buffEnabled ? 1 : 0);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(buffEnabled);
            write.f(obsTimer);
            write.f(sallowyrCounter);
            write.i(lastEmeraliftCount);
            write.i(lastRedstoneCount);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            buffEnabled = read.bool();
            obsTimer = read.f();
            sallowyrCounter = read.f();
            lastEmeraliftCount = read.i();
            lastRedstoneCount = read.i();
        }
    }
}
