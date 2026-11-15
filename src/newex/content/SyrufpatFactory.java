package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
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
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;

/**
 * Chuyển từ syrufpat-factory.js. Nhà máy sản xuất tuần tự mỗi giai đoạn 1 giây (Graphite, Silicon, Dầu, Plastanium)
 * rồi lặp lại; người chơi chọn sản phẩm xả ra băng chuyền (hoặc "xả tất cả" bằng Thorium).
 * Cần Cryofluid làm mát; kho vượt 1100 vật phẩm hoặc thiếu Cryofluid thì đếm ngược 60 giây rồi nổ bán kính 800 px.
 */
public final class SyrufpatFactory {
    private static final Color COLOR_GRAPHITE = Color.valueOf("92a4bd");
    private static final Color COLOR_SILICON = Color.valueOf("535661");
    private static final Color COLOR_OIL = Color.valueOf("313131");
    private static final Color COLOR_PLAST = Color.valueOf("cbd97f");

    private static final float CRAFT_TIME = 1f * 60f;

    private static final Effect superExplosionFx = new Effect(60f, e -> {
        float maxRadius = 800f;
        float innerRadius = maxRadius * e.fin();

        Draw.color(Color.scarlet);
        Draw.alpha(0.35f * e.fout());

        float fillThickness = maxRadius - innerRadius;
        if (fillThickness > 0) {
            Lines.stroke(fillThickness);
            Lines.circle(e.x, e.y, innerRadius + fillThickness / 2f);
        }

        Draw.color(Color.white);
        Draw.alpha(e.fout());
        Lines.stroke(6f * e.fout());
        Lines.circle(e.x, e.y, maxRadius);

        Draw.color(Color.scarlet);
        Draw.alpha(e.fout());
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, innerRadius);
    });

    private SyrufpatFactory() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-syrufpat-factory");
            if (!(b instanceof GenericCrafter)) return;
            final GenericCrafter factory = (GenericCrafter) b;

            factory.configurable = true;
            factory.hasLiquids = true;
            factory.hasItems = true;

            if (factory.liquidCapacity <= 0) {
                factory.liquidCapacity = 500f;
            }

            factory.addBar("overheat_status", (Building building) -> {
                final SyrBuild sb = (SyrBuild) building;
                return new Bar(
                    () -> {
                        long timeLeft = sb.getOverheatTimeLeft();
                        if (sb.overheatTimer <= 0) return FactoryFx.isEn() ? "STATUS: SAFE" : "TRẠNG THÁI: AN TOÀN";
                        if (sb.isOverItemLimit()) {
                            return FactoryFx.isEn()
                                ? "[scarlet]FULL STORAGE, EXPLODE IN: " + timeLeft + "s[]"
                                : "[scarlet]KHO ĐẦY, NỔ TRONG: " + timeLeft + "s[]";
                        }
                        return (FactoryFx.isEn() ? "EXPLOSION WARNING: " : "CẢNH BÁO NỔ: ") + timeLeft + "s";
                    },
                    () -> Color.yellow.cpy().lerp(Color.red, sb.getOverheatRatio()),
                    () -> sb.getOverheatRatio()
                );
            });

            factory.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof SyrBuild) ((SyrBuild) tile).setOutputSelection(value);
            });

            factory.buildType = () -> new SyrBuild(factory);
        });
    }

    /** GenericCrafterBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class SyrBuild extends GenericCrafter.GenericCrafterBuild {
        private final GenericCrafter factory;

        float craftTimer = 0f;
        int selectedOutput = 0;
        float overheatTimer = 0f;
        boolean hasStartedBefore = false;

        int productionStage = 0;

        int dumpGraphiteLeft = 0;
        int dumpSiliconLeft = 0;
        int dumpPlastaniumLeft = 0;

        public SyrBuild(GenericCrafter outer) {
            outer.super();
            this.factory = outer;
        }

        @Override
        public void created() {
            super.created();
            craftTimer = 0f;
            selectedOutput = 0;
            overheatTimer = 0f;
            hasStartedBefore = false;

            productionStage = 0;

            dumpGraphiteLeft = 0;
            dumpSiliconLeft = 0;
            dumpPlastaniumLeft = 0;
        }

        float getOverheatRatio() {
            return (float) Math.min(1.0, Math.max(0.0, overheatTimer / (60.0 * 60.0)));
        }

        long getOverheatTimeLeft() {
            return (long) Math.max(0, Math.ceil(60 - (overheatTimer / 60.0)));
        }

        public void setOutputSelection(int val) {
            selectedOutput = val;
        }

        public int getOutputSelection() {
            return selectedOutput;
        }

        boolean isOverItemLimit() {
            if (items == null) return false;
            return items.total() > 1100;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (items == null) return false;

            if (item == Items.thorium) {
                return getOutputSelection() == 3 && items.get(Items.thorium) < factory.itemCapacity;
            }

            if (item == Items.coal || item == Items.sand || item == Items.lead || item == Items.titanium) {
                return items.get(item) < factory.itemCapacity;
            }

            return false;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid) {
            return liquid == Liquids.cryofluid && liquids != null && liquids.get(liquid) < factory.liquidCapacity;
        }

        @Override
        public void draw() {
            super.draw();

            if (items == null || liquids == null) return;

            Color pColor = COLOR_GRAPHITE;
            if (productionStage == 0) pColor = COLOR_GRAPHITE;
            else if (productionStage == 1) pColor = COLOR_SILICON;
            else if (productionStage == 2) pColor = COLOR_OIL;
            else if (productionStage == 3) pColor = COLOR_PLAST;

            if (efficiency > 0) {
                float progress = Math.min(1f, craftTimer / CRAFT_TIME);
                float radius = 25f * (1f - progress);

                Draw.color(pColor);
                Lines.stroke(2f * (1f - progress));
                Lines.circle(x, y, radius);
            }
        }

        void triggerInstantDeathExplosion() {
            superExplosionFx.at(x, y);
            FactoryFx.annihilate(this, 800f, 25f, 80f);
        }

        /** Hoàn thành 1 giai đoạn: dùng chung cho 4 giai đoạn, trả về true nếu đã sản xuất. */
        private boolean finishStage(int stage, Color fxColor) {
            if (craftTimer < CRAFT_TIME) return false;
            craftTimer = 0f;
            FactoryFx.expandCircleFx.at(x, y, 0f, fxColor);
            Effect.shake(2f, 5f, x, y);
            return true;
        }

        @Override
        public void updateTile() {
            if (liquids == null || items == null) return;

            if (efficiency > 0) {
                hasStartedBefore = true;
            }

            float requiredCryoPerTick = 100f / 60f;
            boolean hasEnoughCryo = liquids.get(Liquids.cryofluid) >= requiredCryoPerTick;
            boolean overLimit = isOverItemLimit();

            if (hasStartedBefore) {
                if (overLimit || (!hasEnoughCryo && efficiency > 0)) {
                    overheatTimer += Time.delta;

                    if (overheatTimer >= 60f * 60f) {
                        triggerInstantDeathExplosion();
                        return;
                    }
                } else if (!overLimit && hasEnoughCryo) {
                    liquids.remove(Liquids.cryofluid, requiredCryoPerTick);
                    overheatTimer = Math.max(0f, overheatTimer - Time.delta);
                }
            }

            int currentSelection = getOutputSelection();

            if (currentSelection == 0) {
                if (items.get(Items.graphite) > 0) dump(Items.graphite);
            } else if (currentSelection == 1) {
                if (items.get(Items.silicon) > 0) dump(Items.silicon);
            } else if (currentSelection == 2) {
                if (items.get(Items.plastanium) > 0) dump(Items.plastanium);
            } else if (currentSelection == 3) {
                if (items.get(Items.thorium) >= 1) {
                    items.remove(Items.thorium, 1);
                    dumpGraphiteLeft += 1;
                    dumpSiliconLeft += 1;
                    dumpPlastaniumLeft += 1;
                } else if (items.get(Items.thorium) <= 0) {
                    dumpGraphiteLeft = 0;
                    dumpSiliconLeft = 0;
                    dumpPlastaniumLeft = 0;
                }

                if (dumpGraphiteLeft > 0 && items.get(Items.graphite) > 0) {
                    if (dump(Items.graphite)) dumpGraphiteLeft--;
                }
                if (dumpSiliconLeft > 0 && items.get(Items.silicon) > 0) {
                    if (dump(Items.silicon)) dumpSiliconLeft--;
                }
                if (dumpPlastaniumLeft > 0 && items.get(Items.plastanium) > 0) {
                    if (dump(Items.plastanium)) dumpPlastaniumLeft--;
                }
            }

            if (liquids.get(Liquids.oil) > 0) {
                dumpLiquid(Liquids.oil);
            }

            if (efficiency <= 0) return;

            craftTimer += Time.delta;

            boolean baseInputs = items.get(Items.coal) >= 10 && items.get(Items.sand) >= 10 && items.get(Items.titanium) >= 10;

            if (productionStage == 0) {
                if (baseInputs && craftTimer >= CRAFT_TIME) {
                    items.remove(Items.coal, 10);
                    items.remove(Items.sand, 10);
                    items.remove(Items.titanium, 10);
                    items.add(Items.graphite, 100);
                    finishStage(0, COLOR_GRAPHITE);
                    productionStage = 1;
                }
                return;
            }

            if (productionStage == 1) {
                if (baseInputs && craftTimer >= CRAFT_TIME) {
                    items.remove(Items.coal, 10);
                    items.remove(Items.sand, 10);
                    items.remove(Items.titanium, 10);
                    items.add(Items.silicon, 100);
                    finishStage(1, COLOR_SILICON);
                    productionStage = 2;
                }
                return;
            }

            if (productionStage == 2) {
                if (baseInputs && craftTimer >= CRAFT_TIME) {
                    items.remove(Items.coal, 10);
                    items.remove(Items.sand, 10);
                    items.remove(Items.titanium, 10);
                    liquids.add(Liquids.oil, 100f);
                    finishStage(2, COLOR_OIL);
                    productionStage = 3;
                }
                return;
            }

            if (productionStage == 3) {
                if (liquids.get(Liquids.oil) >= 10 && baseInputs && craftTimer >= CRAFT_TIME) {
                    liquids.remove(Liquids.oil, 10f);
                    items.remove(Items.coal, 10);
                    items.remove(Items.sand, 10);
                    items.remove(Items.titanium, 10);
                    items.add(Items.plastanium, 100);
                    finishStage(3, COLOR_PLAST);
                    productionStage = 0;
                }
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();

            table.button(Icon.settings, Styles.cleari, 40f, () -> {
                final BaseDialog dialog = new BaseDialog(FactoryFx.isEn() ? "Output Selection Center" : "Trung tâm Tùy chọn Đầu ra");
                final Table optTable = new Table();

                optTable.add(FactoryFx.isEn() ? "[yellow]SELECT CONVEYOR OUTPUT ITEM:[]" : "[yellow]CHỌN SẢN PHẨM ĐẨY RA BĂNG CHUYỀN:[]").padBottom(10f).row();
                addOptionButton(optTable, dialog, FactoryFx.isEn() ? "Graphite" : "Than chì (Graphite)", 0);
                addOptionButton(optTable, dialog, "Silicon", 1);
                addOptionButton(optTable, dialog, FactoryFx.isEn() ? "Plastanium" : "Nhựa (Plastanium)", 2);
                addOptionButton(optTable, dialog, FactoryFx.isEn() ? "Dump All (Consumes Thorium)" : "Xả toàn bộ sản phẩm (Tiêu thụ Thorium)", 3);

                ScrollPane scroll = new ScrollPane(optTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(350f);
                dialog.addCloseButton();
                dialog.show();
            }).size(50f, 40f);

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String title = FactoryFx.isEn() ? " Syrufpat Factory Specs " : " Thông số Nhà máy Syrufpat ";
                String descStr = FactoryFx.isEn()
                    ? "[gold]⚡ SEQUENTIAL PRODUCTION PROCESS (1s/STAGE) ⚡[]\n\n"
                        + "[cyan]• Stage 1 (Graphite):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Graphite[] (1s)\n"
                        + "[cyan]• Stage 2 (Silicon):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Silicon[] (1s)\n"
                        + "[cyan]• Stage 3 (Oil):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Oil[] (1s)\n"
                        + "[cyan]• Stage 4 (Plastanium):[] 10 Oil + 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Plastanium[] (1s)\n\n"
                        + "[lightgray]* Continuous loop Stage 1 ➔ 2 ➔ 3 ➔ 4 ➔ 1 regardless of output dumping.[]\n\n"
                        + "[gold]⚡ OUTPUT BYPASS OPTION ⚡[]\n"
                        + "• Each [accent]1 Thorium[] loaded allows simultaneous dumping of [green]1 Graphite, 1 Silicon, 1 Plastanium[].\n"
                        + "• Dumping stops immediately when Thorium is depleted.\n\n"
                        + "[scarlet]⚠ APOCALYPTIC EXPLOSION MECHANIC ⚠[]\n"
                        + "• Requires [cyan]500 Cryofluid/s[] for cooling.\n"
                        + "• 60s explosion countdown if storage [red]exceeds 1100 items[] or lacks Cryofluid.\n"
                        + "• [red]EXPLOSION: INSTANTLY DESTROYS ALL Units and Blocks[] within a 100-tile radius!"
                    : "[gold]⚡ QUY TRÌNH SẢN XUẤT TUẦN TỰ (1s/GIAI ĐOẠN) ⚡[]\n\n"
                        + "[cyan]• Lớp 1 (Than chì):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Graphite[] (1s)\n"
                        + "[cyan]• Lớp 2 (Silicon):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Silicon[] (1s)\n"
                        + "[cyan]• Lớp 3 (Dầu mỏ):[] 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Oil[] (1s)\n"
                        + "[cyan]• Lớp 4 (Nhựa Plast):[] 10 Oil + 10 Coal + 10 Sand + 10 Titanium ➔ [green]100 Plastanium[] (1s)\n\n"
                        + "[lightgray]* Quy trình xoay vòng liên tục Lớp 1 ➔ 2 ➔ 3 ➔ 4 ➔ 1 bất kể xả hàng hay không.[]\n\n"
                        + "[gold]⚡ TÙY CHỌN BỎ QUA LỰA CHỌN ⚡[]\n"
                        + "• Mỗi [accent]1 Thorium[] nạp vào cho phép xả đồng loạt [green]1 Graphite, 1 Silicon, 1 Plastanium[] ra ngoài.\n"
                        + "• Hết Thorium nhà máy sẽ ngưng xả hàng ngay lập tức.\n\n"
                        + "[scarlet]⚠ CƠ CHẾ NỔ HỦY DIỆT TẬN THẾ ⚠[]\n"
                        + "• Yêu cầu [cyan]500 Cryofluid/s[] để hạ nhiệt.\n"
                        + "• Đếm ngược 60s nổ khi kho chứa [red]vượt quá 1100 item[] hoặc thiếu Cryofluid.\n"
                        + "• [red]SỨC NỔ: Tiêu diệt NGAY LẬP TỨC toàn bộ Unit và Block[] trong bán kính 100 ô!";

                FactoryFx.showSpecs(title, descStr);
            }).size(50f, 40f);
        }

        private void addOptionButton(Table optTable, final BaseDialog dialog, String name, final int index) {
            boolean isCurrent = getOutputSelection() == index;
            String prefix = isCurrent ? "[lime]✓ " : "[lightgray]";
            Cell<?> btn = optTable.button(prefix + name + "[]", () -> {
                setOutputSelection(index);
                configure(Integer.valueOf(index));
                dialog.hide();
            }).size(280f, 42f).margin(4f);
            btn.row();
        }

        @Override
        public Object config() {
            return Integer.valueOf(getOutputSelection());
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getOutputSelection());
            write.f(overheatTimer);
            write.bool(hasStartedBefore);
            write.b(productionStage);
            write.i(dumpGraphiteLeft);
            write.i(dumpSiliconLeft);
            write.i(dumpPlastaniumLeft);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setOutputSelection(read.b());
            overheatTimer = read.f();
            hasStartedBefore = read.bool();
            productionStage = read.b();
            dumpGraphiteLeft = read.i();
            dumpSiliconLeft = read.i();
            dumpPlastaniumLeft = read.i();
        }
    }
}
