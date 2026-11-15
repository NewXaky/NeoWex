package newex.content;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.DamageDisplay;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ turret-system.js. Hệ trang bị dùng chung của pháo: mỗi pháo có 4 ô gắn vật phẩm (lấy từ lõi), mỗi vật phẩm
 * cộng buff (sát thương, tốc bắn, tầm, chí mạng...) và đặt vị trí mục tiêu.
 *
 * JS gốc dùng "mixin" (setupBaseTurret trả về object trộn vào build). Trong Java, mỗi build giữ 1 {@link TurretState}
 * (do {@link #setup} tạo) và gọi các hàm tĩnh ở đây: {@link #buildConfiguration}, {@link #writeState}, {@link #readState},
 * và triển khai {@link EquippedTurret} để các hệ thống khác đọc được chí mạng/passive.
 */
public final class TurretSystem {
    public static final int MAX_SLOTS = 4;

    /** Móc tới ElementSystem.hit (JS: global.NexElement.hit). Null nếu hệ nguyên tố chưa nạp. */
    public interface ElementHit {
        void hit(Building source, Unit target, float baseDmg);
    }

    public static ElementHit elementHit = null;

    /** Pháo có hệ trang bị (thay cho duck-typing getCritChance/... của JS). */
    public interface EquippedTurret {
        TurretState turretState();
    }

    public interface OnItemChange {
        void changed(Building build, int slotIndex, Item item);
    }

    /** Kho trang bị của 1 pháo. */
    public static final class EquipmentStorage {
        public final Item[] slots = new Item[MAX_SLOTS];
        public final float[] timers = new float[MAX_SLOTS];
    }

    /** Trạng thái chung của pháo (các trường của object mà setupBaseTurret trả về trong JS). */
    public static final class TurretState {
        public EquipmentStorage equipmentStorage = null;
        public float targetX = 0f, targetY = 0f;
        public boolean selectingTarget = false;
        public float baseMaxRange;
        public boolean hasTargetSet = false;
        public int burstQueue = 0;
        public float burstDelayTimer = 0f;
        public float spinAngle = 0f;
        public final Seq<Object> orbitingBullets = new Seq<>();

        public TurretState(float baseMaxRange) {
            this.baseMaxRange = baseMaxRange;
        }

        public EquipmentStorage initEq() {
            if (equipmentStorage == null) {
                equipmentStorage = new EquipmentStorage();
            }
            return equipmentStorage;
        }

        public int getItemCountByName(String targetName) {
            initEq();
            int count = 0;
            for (int i = 0; i < MAX_SLOTS; i++) {
                Item item = equipmentStorage.slots[i];
                if (item != null && item.name.replace("newex-", "").equals(targetName)) count++;
            }
            return count;
        }

        public float getDamageMultiplier() {
            return 1f + getItemCountByName("obsidis") * 0.10f + getItemCountByName("copper") * 0.05f;
        }

        public float getSpeedMultiplier() {
            return 1f + getItemCountByName("obsidis") * 0.10f + getItemCountByName("silicon") * 0.05f;
        }

        public float getRangeMultiplier() {
            return 1f + getItemCountByName("obsidis") * 0.50f + getItemCountByName("thorium") * 0.05f;
        }

        public float getCritChance() {
            return getItemCountByName("starlight") * 0.15f;
        }

        public float getCritMultiplier() {
            return 1f + getItemCountByName("starlight") * 0.50f;
        }

        public boolean hasStarlightPassive() {
            return getItemCountByName("starlight") > 0;
        }

        public boolean hasSallowyrPassive() {
            return getItemCountByName("sallowyr") > 0;
        }

        public float range() {
            return baseMaxRange * getRangeMultiplier();
        }
    }

    // ------------------------------------------------------------------ hiệu ứng

    private static final Effect customNoneFx = new Effect(0f, e -> {});

    private static final Effect critEffect = new Effect(20f, e -> {
        Draw.color(Color.gold, Color.scarlet, e.fin());
        for (int i = 0; i < 4; i++) {
            float angle = i * 90 + 45;
            float len = 3f + e.fin() * 10f;
            Drawf.tri(e.x + Mathf.cosDeg(angle) * (len * 0.2f), e.y + Mathf.sinDeg(angle) * (len * 0.2f), 3f * e.fout(), len, angle);
        }
    });

    private static final Effect executeFx = new Effect(25f, e -> {
        Draw.color(Pal.accent, Color.white, e.fin());
        Lines.stroke(e.fout() * 3f);
        Lines.circle(e.x, e.y, e.finpow() * 25f);
        Draw.color(Color.gold);
        for (int i = 0; i < 4; i++) {
            Lines.lineAngle(e.x, e.y, i * 90 + 45, e.finpow() * 20f);
        }
    });

    public static final Effect physicalImpactFx = new Effect(25f, e -> {
        Draw.color(Pal.lightOrange, Color.gray, e.fin());
        Lines.stroke(e.fout() * 2.5f);
        Lines.circle(e.x, e.y, e.finpow() * 16f);
        Draw.color(Pal.accent);
        Fill.circle(e.x, e.y, e.fout() * 5f);
    });

    public static final Effect miniCrosshairWarningFx = new Effect(60f, e -> {
        if (Vars.state.isPaused()) return;
        Draw.z(Layer.effect + 0.01f);
        float fout = e.fout(), fin = e.fin();
        Color color = Pal.accent;
        Draw.color(color);
        Lines.stroke(1.8f * fout);
        Lines.circle(e.x, e.y, 30f * fin);
        float crossSize = 16f * fout;
        Lines.stroke(2f * fout, color);
        Lines.line(e.x - crossSize, e.y, e.x + crossSize, e.y);
        Lines.line(e.x, e.y - crossSize, e.x, e.y + crossSize);
        Fill.circle(e.x, e.y, 3f * fout);
        Draw.reset();
    });

    public static final Effect chargedExplosionFx = new Effect(35f, e -> {
        Draw.color(PerfCore.color("a855f7"), PerfCore.color("fef08a"), e.fin());
        Lines.stroke(e.fout() * 4f);
        Lines.circle(e.x, e.y, e.finpow() * 20f);
        Lines.stroke(e.fout() * 2f);
        Lines.circle(e.x, e.y, e.finpow() * 12f);
        Draw.color(PerfCore.color("fef08a"));
        Fill.circle(e.x, e.y, e.fout() * 8f);
    });

    /** Data của effect là vị trí đích (Building, Unit hoặc Vec2). */
    public static final Effect laserBeamFx = new Effect(20f, e -> {
        if (!(e.data instanceof Position)) return;
        Position to = (Position) e.data;
        Draw.color(PerfCore.color("fef08a"), PerfCore.color("a855f7"), e.fin());
        Lines.stroke(e.fout() * 3.5f);
        Lines.line(e.x, e.y, to.getX(), to.getY());
        Fill.circle(e.x, e.y, e.fout() * 4f);
        Fill.circle(to.getX(), to.getY(), e.fout() * 5f);
    });

    public static final Effect mainToSpawnLaserFx = new Effect(20f, e -> {
        if (!(e.data instanceof Position)) return;
        Position to = (Position) e.data;
        Draw.color(PerfCore.color("fef08a"), Color.white, e.fin());
        Lines.stroke(e.fout() * 3f);
        Lines.line(e.x, e.y, to.getX(), to.getY());
        Fill.circle(e.x, e.y, e.fout() * 5f);
    });

    // ------------------------------------------------------------------ đạn dùng chung (tạo trong loadContent)

    public static BulletType starSwordBullet;
    public static BulletType directBeamLaser;
    public static BulletType dummyBullet;

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        starSwordBullet = new BasicBulletType(9f, 120f) {
            {
                lifetime = 45f;
                width = 8f;
                height = 12f;
                frontColor = Color.valueOf("#ffffff");
                backColor = Color.valueOf("#ffd700");
                trailColor = Color.valueOf("#75e3ff");
                trailEffect = Fx.disperseTrail;
                trailChance = 0.3f;
                pierce = true;
                pierceCap = 2;
                hitEffect = critEffect;
                despawnEffect = critEffect;
            }

            @Override
            public void draw(Bullet b) {
                arc.graphics.g2d.TextureRegion region = PerfCore.region("newex-starsword", "starsword");
                if (region.found()) Draw.rect(region, b.x, b.y, width, height, b.rotation() - 90f);
                else super.draw(b);
            }
        };

        directBeamLaser = new LaserBulletType(150f) {
            {
                length = 120f;
                width = 12f;
                lifetime = 20f;
                colors = new Color[]{Pal.accent, Color.white};
            }
        };

        dummyBullet = new BasicBulletType(0f, 0f) {
            {
                lifetime = 0f;
                collides = false;
                hitEffect = customNoneFx;
                despawnEffect = customNoneFx;
            }
        };
    }

    // ------------------------------------------------------------------ chỉ số vật phẩm

    public static final class ItemStat {
        public final String desc;

        ItemStat(String desc) {
            this.desc = desc;
        }
    }

    private static final Map<String, ItemStat> ITEM_STATS = new HashMap<>();

    static {
        ITEM_STATS.put("copper", new ItemStat("[stat]+5% Sát thương[]"));
        ITEM_STATS.put("silicon", new ItemStat("[stat]+5% Tốc độ bắn[]"));
        ITEM_STATS.put("thorium", new ItemStat("[stat]+5% Tầm bắn[]"));
        ITEM_STATS.put("starlight", new ItemStat("[stat]+15% Chí mạng | +50% ST Chí mạng\nBắn kèm đạn Kiếm Sao (150% + 120 ST)[]"));
        ITEM_STATS.put("sallowyr", new ItemStat("[stat]Bắn thêm 1 đạn/laser (150% ST gốc) khi HP <80%[]"));
        ITEM_STATS.put("obsidis", new ItemStat("[stat]+10% TC/TĐ, +50% Tầm bắn[]"));
    }

    private TurretSystem() {}

    public static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    // ------------------------------------------------------------------ sát thương có chí mạng

    /** Gây sát thương lên đơn vị mục tiêu có tính chí mạng, passive Starlight (hành quyết khi < 5% máu) và nguyên tố. */
    public static void applyDamageWithStarlight(Building sourceBuild, Unit targetUnit, float baseDmg) {
        if (targetUnit == null || !targetUnit.isValid() || targetUnit.dead) return;
        if (elementHit != null) elementHit.hit(sourceBuild, targetUnit, baseDmg);

        float critChance = 0f;
        float critDmgMult = 1f;
        boolean hasStarlightPassive = false;
        if (sourceBuild instanceof EquippedTurret) {
            TurretState st = ((EquippedTurret) sourceBuild).turretState();
            critChance = st.getCritChance();
            critDmgMult = st.getCritMultiplier();
            hasStarlightPassive = st.hasStarlightPassive();
        }

        float hpRatioBeforeHit = targetUnit.health / targetUnit.maxHealth;
        float finalDmg = baseDmg * Mathf.random(0.5f, 2.0f);

        if (Mathf.chance(critChance)) {
            finalDmg *= critDmgMult;
            DamageDisplay.crit(targetUnit);
        }
        targetUnit.damage(finalDmg);

        if (hasStarlightPassive && targetUnit.isValid() && !targetUnit.dead) {
            if (hpRatioBeforeHit <= 0.05f || targetUnit.health <= targetUnit.maxHealth * 0.05f) {
                targetUnit.kill();
                executeFx.at(targetUnit.x, targetUnit.y);
                CoreBuild core = sourceBuild.team.core();
                if (core != null) {
                    Item copperItem = Vars.content.getByName(ContentType.item, "copper");
                    if (copperItem == null) copperItem = Items.copper;
                    if (copperItem != null) {
                        core.items.add(copperItem, 1000);
                        Call.label("+1000 Copper", 1.5f, targetUnit.x, targetUnit.y);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ giao diện ô trang bị

    public static void buildSlotUI(Table table, final Building build, final EquipmentStorage itemStorage, final OnItemChange onItemChange) {
        Table slotsTable = new Table();
        slotsTable.background(Styles.black6);
        slotsTable.margin(6f);

        int maxSlots = MAX_SLOTS;
        CoreBuild core = build.team.core();

        for (int i = 0; i < maxSlots; i++) {
            final int slotIndex = i;
            final Item currentItem = itemStorage.slots[slotIndex];
            boolean hasItemInCore = currentItem != null && core != null && core.items.has(currentItem, 1);

            Button slotBtn = slotsTable.button((Cons<Button>) b -> {
                b.clearChildren();
                if (currentItem != null) {
                    b.image(currentItem.uiIcon).size(28f);
                } else {
                    b.add("[gray]+[]").fontScale(1.2f);
                }
            }, Styles.defaultb, () -> {
                if (currentItem != null) {
                    CoreBuild currentCore = build.team.core();
                    if (currentCore != null) currentCore.items.add(currentItem, 1);
                    itemStorage.slots[slotIndex] = null;
                    if (onItemChange != null) onItemChange.changed(build, slotIndex, null);
                } else {
                    showItemSelectionDialog(build, selectedItem -> {
                        itemStorage.slots[slotIndex] = selectedItem;
                        if (onItemChange != null) onItemChange.changed(build, slotIndex, selectedItem);
                    });
                }
            }).size(44f).pad(3f).get();

            if (currentItem != null) {
                String cleanName = currentItem.name.replace("newex-", "");
                ItemStat stat = ITEM_STATS.get(cleanName);
                String buffText = stat != null ? stat.desc : "[gray]Không có chỉ số buff[]";
                final String tooltipText = "[accent]" + currentItem.localizedName + "[]\n" + buffText;
                slotBtn.addListener(new Tooltip(t -> {
                    t.background(Styles.black6).margin(8f);
                    t.add(tooltipText);
                }));
                slotBtn.setColor(hasItemInCore ? Pal.heal : Pal.remove);
            } else {
                slotBtn.addListener(new Tooltip(t -> {
                    t.background(Styles.black6).margin(8f);
                    t.add("[gray]Nhấp để gắn trang bị[]");
                }));
                slotBtn.setColor(Color.white);
            }
        }
        table.add(slotsTable).padLeft(6f);
    }

    public static void showItemSelectionDialog(final Building build, final Cons<Item> onSelect) {
        final BaseDialog dialog = new BaseDialog("Kho Trang Bị");
        dialog.addCloseButton();
        final float dialogWidth = Math.min(Core.graphics.getWidth() * 0.85f, 360f);

        dialog.cont.pane(p -> {
            p.margin(6f);
            final CoreBuild core = build.team.core();

            Vars.content.items().each(item -> {
                String cleanName = item.name.replace("newex-", "");
                ItemStat statData = ITEM_STATS.get(cleanName);
                if (statData == null) return;

                final boolean hasItem = core != null && core.items.has(item, 1);
                Button btn = new Button(Styles.cleart);
                btn.margin(8f);
                btn.add(new Image(item.uiIcon)).size(32f).padRight(10f).top();

                Table infoTable = new Table();
                infoTable.left();
                int count = core != null ? core.items.get(item) : 0;
                String statusColor = hasItem ? "[#84f491]" : "[#ff795e]";

                Label titleLabel = infoTable.add(item.localizedName + " " + statusColor + "(Lõi: " + count + ")[]").left().growX().get();
                titleLabel.setWrap(true);
                infoTable.row();

                Label buffLabel = infoTable.add(statData.desc).fontScale(0.85f).left().growX().get();
                buffLabel.setWrap(true);

                btn.add(infoTable).growX().width(dialogWidth - 80f);
                btn.setColor(hasItem ? Pal.heal : Pal.remove);

                btn.clicked(() -> {
                    if (hasItem) {
                        core.items.remove(item, 1);
                        onSelect.get(item);
                        dialog.hide();
                    } else {
                        Vars.ui.showInfoToast("[red]Lõi không có nguyên liệu này![]", 2f);
                    }
                });
                p.add(btn).width(dialogWidth - 20f).pad(3f).row();
            });
        }).scrollX(false).grow();
        dialog.show();
    }

    // ------------------------------------------------------------------ thiết lập cho từng pháo

    /** Thiết lập 1 khối pháo (tương đương setupBaseTurret): cho cấu hình được và cho nạp Silicon làm đạn giả. */
    public static TurretState setup(Block turretBlock, float baseMaxRange) {
        turretBlock.configurable = true;

        if (turretBlock instanceof ItemTurret) {
            ((ItemTurret) turretBlock).ammoTypes.put(Items.silicon, dummyBullet);
        }

        return new TurretState(baseMaxRange);
    }

    /** Dùng trong buildConfiguration của build pháo. */
    public static void buildConfiguration(final Building self, final TurretState state, Table table) {
        state.initEq();
        table.clear();
        table.row();

        table.button(Icon.commandRally, Styles.cleari, 40f, () -> {
            self.deselect();
            state.selectingTarget = true;
            Vars.ui.hudfrag.showToast(isEn() ? "Click target location within range!" : "Nhấp vào vị trí mục tiêu bắn trong tầm hoạt động!");
        }).size(50f, 40f).tooltip(isEn() ? "Set Target Location" : "Đặt vị trí mục tiêu bắn");

        buildSlotUI(table, self, state.equipmentStorage, (b, idx, item) -> b.deselect());
    }

    /** Gọi trong write() của build pháo, sau super.write(). */
    public static void writeState(Writes write, TurretState state) {
        state.initEq();
        write.i(MAX_SLOTS);
        for (int i = 0; i < MAX_SLOTS; i++) {
            Item item = state.equipmentStorage.slots[i];
            write.s(item != null ? item.id : -1);
        }
        write.f(state.targetX);
        write.f(state.targetY);
        write.bool(state.hasTargetSet);
    }

    /** Gọi trong read() của build pháo, sau super.read(). */
    public static void readState(Reads read, TurretState state) {
        state.initEq();
        int count = read.i();
        for (int i = 0; i < count; i++) {
            int itemId = read.s();
            if (i < MAX_SLOTS) state.equipmentStorage.slots[i] = (itemId != -1) ? Vars.content.item(itemId) : null;
        }
        state.targetX = read.f();
        state.targetY = read.f();
        state.hasTargetSet = read.bool();
    }
}
