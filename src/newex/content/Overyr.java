package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Boolf;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
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
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.DamageDisplay;
import newex.PerfCore;

import java.util.ArrayList;

/**
 * Chuyển từ overyr.js. Pháo chỉ định tay (block do overyr.hjson tạo, KHÔNG tạo bằng Java): vá lúc ContentInitEvent (bài học 29b).
 * Dựng từ Nethyr.java (cùng hệ 4 ô trang bị); khác: laser chỉ định + mưa laser/đạn vật lý rơi từ trời, tầm 760, vùng 10 ô, không kẹp điểm chọn vào tầm.
 */
public final class Overyr {
    private Overyr() {}

    static ItemTurret overyr;
    static BasicBulletType dummyBullet;
    static Effect customNoneFx, physicalImpactFx, executeFx, miniCrosshairWarningFx, skyLaserStrikeFx;
    static LaserBulletType directBeamLaser;

    /** Endyr JS: isEn = locale bắt đầu bằng "en" (thêm kiểm tra null). */
    static boolean isEn() {
        String loc = Core.settings.getString("locale");
        return loc != null && loc.startsWith("en");
    }

    /** Mô tả buff của từng vật phẩm (ItemStats trong JS; chỉ trường desc được dùng). Trả null nếu không có trang bị đó. */
    static String statDesc(String clean) {
        boolean en = isEn();
        switch (clean) {
            case "copper": return en ? "[stat]+5% Dmg[]" : "[stat]+5% Sát thương[]";
            case "silicon": return en ? "[stat]+5% Fire Rate[]" : "[stat]+5% Tốc độ bắn[]";
            case "thorium": return en ? "[stat]+5% Range[]" : "[stat]+5% Tầm bắn[]";
            case "sallowyr": return en ? "[stat]Bonus 1 physical projectile (150% Base Dmg) when target HP <80%[]" : "[stat]Bắn thêm 1 đạn vật lý (150% ST gốc) khi máu <80%[]";
            case "obsidis": return en ? "[stat]+10% Dmg/FireRate, +50% Range[]" : "[stat]+10% TC/TĐ, +50% Tầm bắn[]";
            case "starlight": return en ? "[stat]+5% Crit | +15% CritDmg\nExecute targets below 5% HP (+1000 Copper)[]" : "[stat]+5% Bạo | +15% ST Bạo\nKết liễu mục tiêu <5% HP (+1000 Đồng)[]";
            default: return null;
        }
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> setup());
        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && build.name.equals("newex-overyr")) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
                if (tile != null) {
                    float centerX = tile.drawx() + build.offset, centerY = tile.drawy() + build.offset;
                    Draw.z(Layer.overlayUI);
                    Drawf.dashCircle(centerX, centerY, 760f, Pal.accent);
                    Draw.reset();
                }
            }
        });
    }

    private static void makeEffects() {
        customNoneFx = new Effect(0f, e -> {});
        physicalImpactFx = new Effect(25f, e -> {
            Draw.color(Pal.lightOrange, Color.gray, e.fin());
            Lines.stroke(e.fout() * 2.5f);
            Lines.circle(e.x, e.y, e.finpow() * 16f);
            Draw.color(Pal.accent);
            Fill.circle(e.x, e.y, e.fout() * 5f);
        });
        executeFx = new Effect(25f, e -> {
            Draw.color(Pal.accent, Color.white, e.fin());
            Lines.stroke(e.fout() * 3f);
            Lines.circle(e.x, e.y, e.finpow() * 25f);
            Draw.color(Color.gold);
            for (int i = 0; i < 4; i++) Lines.lineAngle(e.x, e.y, i * 90 + 45, e.finpow() * 20f);
        });
        miniCrosshairWarningFx = new Effect(60f, e -> {
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
            Lines.stroke(1f * fout, Color.white);
            Lines.line(e.x - crossSize * 0.6f, e.y, e.x + crossSize * 0.6f, e.y);
            Lines.line(e.x, e.y - crossSize * 0.6f, e.x, e.y + crossSize * 0.6f);
            Fill.circle(e.x, e.y, 3f * fout);
            Draw.reset();
        });
        skyLaserStrikeFx = new Effect(30f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect + 2f);
            float fout = e.fout();
            float startX = e.x - 200f, startY = e.y + 500f;
            Lines.stroke(12f * fout, Pal.accent);
            Lines.line(startX, startY, e.x, e.y);
            Lines.stroke(4f * fout, Color.white);
            Lines.line(startX, startY, e.x, e.y);
            Fill.circle(e.x, e.y, 8f * fout);
            Draw.reset();
        });
    }

    private static void setup() {
        makeEffects();
        directBeamLaser = new LaserBulletType(150f);
        directBeamLaser.length = 120f; directBeamLaser.width = 12f; directBeamLaser.lifetime = 20f;
        directBeamLaser.colors = new Color[]{Pal.accent, Color.white};
        dummyBullet = new BasicBulletType();
        dummyBullet.speed = 0f; dummyBullet.lifetime = 0f; dummyBullet.damage = 0f; dummyBullet.collides = false;
        dummyBullet.hitEffect = customNoneFx; dummyBullet.despawnEffect = customNoneFx;

        Block b = Vars.content.getByName(ContentType.block, "newex-overyr");
        if (!(b instanceof ItemTurret)) return;
        overyr = (ItemTurret) b;
        overyr.configurable = true;
        if (overyr.ammoTypes != null) overyr.ammoTypes.put(Items.silicon, dummyBullet);
        overyr.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof OveryrBuild) ((OveryrBuild) tile).handleEquipConfig(value);
        });
        overyr.buildType = () -> new OveryrBuild(overyr);
    }

    static void applyDamageWithStarlight(OveryrBuild src, Unit target, float baseDmg) {
        if (target == null || !target.isValid() || target.dead) return;
        if (TurretSystem.elementHit != null) TurretSystem.elementHit.hit(src, target, baseDmg);

        float critChance = src.getCritChance(), critDmgMult = src.getCritMultiplier();
        float hpRatioBeforeHit = target.health / target.maxHealth;
        float finalDmg = baseDmg * Mathf.random(0.5f, 2.0f);
        if (Mathf.chance(critChance)) {
            finalDmg *= critDmgMult;
            DamageDisplay.crit(target);
        }
        target.damage(finalDmg);

        if (src.hasStarlightPassive() && target.isValid() && !target.dead && !(Endyr.reflectProtected != null && Endyr.reflectProtected.get(target))) {
            if (hpRatioBeforeHit <= 0.05f || target.health <= target.maxHealth * 0.05f) {
                target.kill();
                executeFx.at(target.x, target.y);
                CoreBuild core = src.team.core();
                if (core != null) {
                    Item copperItem = Vars.content.getByName(ContentType.item, "copper");
                    if (copperItem == null) copperItem = Items.copper;
                    core.items.add(copperItem, 1000);
                    Call.label("+1000 Copper", 1.5f, target.x, target.y);
                }
            }
        }
    }

    // ------------------------------------------------------------------ UI trang bị (UniversalItemUI)

    static Item getItemBySlotType(int type) {
        switch (type) {
            case 1: { Item i = Vars.content.getByName(ContentType.item, "copper"); return i != null ? i : Items.copper; }
            case 2: { Item i = Vars.content.getByName(ContentType.item, "silicon"); return i != null ? i : Items.silicon; }
            case 3: { Item i = Vars.content.getByName(ContentType.item, "thorium"); return i != null ? i : Items.thorium; }
            case 4: return Vars.content.getByName(ContentType.item, "newex-sallowyr");
            case 5: return Vars.content.getByName(ContentType.item, "newex-obsidis");
            case 6: return Vars.content.getByName(ContentType.item, "newex-starlight");
            default: return null;
        }
    }

    static int getSlotTypeByItem(Item item) {
        if (item == null) return 0;
        switch (item.name.replace("newex-", "")) {
            case "copper": return 1;
            case "silicon": return 2;
            case "thorium": return 3;
            case "sallowyr": return 4;
            case "obsidis": return 5;
            case "starlight": return 6;
            default: return 0;
        }
    }

    static int maskOf(int[] s) {
        return (s[0] & 7) | ((s[1] & 7) << 3) | ((s[2] & 7) << 6) | ((s[3] & 7) << 9) | (1 << 15);
    }

    static void buildSlotUI(Table table, final OveryrBuild build, final Runnable onConfigChange) {
        Table slotsTable = new Table();
        slotsTable.background(Styles.black6);
        slotsTable.margin(6f);
        CoreBuild core = build.team.core();

        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            final Item currentItem = getItemBySlotType(build.equipSlots[slotIndex]);

            Button slotBtn = slotsTable.button((Cons<Button>) b -> {
                b.clearChildren();
                if (currentItem != null) b.image(currentItem.uiIcon).size(28f);
                else b.add("[gray]+[]").fontScale(1.2f);
            }, Styles.defaultb, () -> {
                int[] next = build.equipSlots.clone();
                if (currentItem != null) {
                    next[slotIndex] = 0;
                    build.configure(Integer.valueOf(maskOf(next)));
                    if (onConfigChange != null) onConfigChange.run();
                } else {
                    showItemSelectionDialog(build, selectedType -> {
                        int[] n2 = build.equipSlots.clone();
                        n2[slotIndex] = selectedType;
                        build.configure(Integer.valueOf(maskOf(n2)));
                        if (onConfigChange != null) onConfigChange.run();
                    });
                }
            }).size(44f).pad(3f).get();

            if (currentItem != null) {
                String cleanName = currentItem.name.replace("newex-", "");
                String d = statDesc(cleanName);
                String buffText = d != null ? d : "[gray]Không có chỉ số buff[]";
                final String tooltipText = "[accent]" + currentItem.localizedName + "[]\n" + buffText;
                boolean hasItemInCore = core != null && core.items.has(currentItem, 1);
                slotBtn.addListener(new Tooltip(t -> {
                    t.background(Styles.black6).margin(8f);
                    t.add(tooltipText);
                }));
                slotBtn.setColor(hasItemInCore ? Pal.heal : Pal.remove);
            } else {
                slotBtn.addListener(new Tooltip(t -> {
                    t.background(Styles.black6).margin(8f);
                    t.add(isEn() ? "[gray]Click to equip item[]" : "[gray]Nhấp để gắn trang bị[]");
                }));
                slotBtn.setColor(Color.white);
            }
        }
        table.add(slotsTable);
    }

    static void showItemSelectionDialog(final OveryrBuild build, final Cons<Integer> onSelect) {
        final BaseDialog dialog = new BaseDialog(isEn() ? "Equipment Storage" : "Kho Trang Bị");
        dialog.addCloseButton();
        final float dialogWidth = Math.min(Core.graphics.getWidth() * 0.85f, 360f);

        dialog.cont.pane(p -> {
            p.margin(6f);
            CoreBuild core = build.team.core();
            String[] validItems = {"copper", "silicon", "thorium", "newex-sallowyr", "newex-obsidis", "newex-starlight"};
            for (String itemName : validItems) {
                final Item item = Vars.content.getByName(ContentType.item, itemName);
                if (item == null) continue;
                String statDesc = statDesc(item.name.replace("newex-", ""));
                if (statDesc == null) continue;

                int count = core != null ? core.items.get(item) : 0;
                final boolean hasItem = count > 0;

                Button btn = new Button(Styles.cleart);
                btn.margin(8f);
                btn.add(new Image(item.uiIcon)).size(32f).padRight(10f).top();

                Table infoTable = new Table();
                infoTable.left();
                String statusColor = hasItem ? "[#84f491]" : "[#ff795e]";
                Label titleLabel = infoTable.add(item.localizedName + " " + statusColor + "(Lõi: " + count + ")[]").left().growX().get();
                titleLabel.setWrap(true);
                infoTable.row();
                Label buffLabel = infoTable.add(statDesc).fontScale(0.85f).left().growX().get();
                buffLabel.setWrap(true);

                btn.add(infoTable).growX().width(dialogWidth - 80f);
                btn.setColor(hasItem ? Pal.heal : Pal.remove);
                btn.clicked(() -> {
                    if (hasItem) {
                        onSelect.get(getSlotTypeByItem(item));
                        dialog.hide();
                    } else {
                        Vars.ui.showInfoToast(isEn() ? "[red]Core missing this item![]" : "[red]Lõi không có nguyên liệu này![]", 2f);
                    }
                });
                p.add(btn).width(dialogWidth - 20f).pad(3f).row();
            }
        }).scrollX(false).grow();
        dialog.show();
    }

    // ------------------------------------------------------------------ Build

    public static class OveryrBuild extends ItemTurret.ItemTurretBuild {
        float targetX = 0f, targetY = 0f, baseMaxRange = 760f;
        boolean selectingTarget = false, hasTargetSet = false;
        int burstQueue = 0;
        float burstDelayTimer = 0f, spinAngle = 0f;
        final ArrayList<Orb> orbs = new ArrayList<>();
        int[] equipSlots = {0, 0, 0, 0};

        public OveryrBuild(ItemTurret outer) { outer.super(); }

        @Override
        public void created() {
            super.created();
            targetX = x; targetY = y; hasTargetSet = true; orbs.clear();
        }

        @Override
        public void placed() {
            super.placed();
            targetX = x; targetY = y; hasTargetSet = true; orbs.clear();
        }

        int getItemCount(int type) {
            int count = 0;
            for (int i = 0; i < 4; i++) if (equipSlots[i] == type) count++;
            return count;
        }

        void handleEquipConfig(int mask) {
            if ((mask & (1 << 15)) != 0) {
                CoreBuild core = team.core();
                for (int i = 0; i < 4; i++) {
                    int newType = (mask >> (i * 3)) & 7;
                    int oldType = equipSlots[i];
                    if (oldType != newType) {
                        if (oldType != 0 && core != null) {
                            Item oldItem = getItemBySlotType(oldType);
                            if (oldItem != null) core.items.add(oldItem, 1);
                        }
                        if (newType != 0 && core != null) {
                            Item newItem = getItemBySlotType(newType);
                            if (newItem != null && core.items.has(newItem, 1)) {
                                core.items.remove(newItem, 1);
                                equipSlots[i] = newType;
                            } else {
                                equipSlots[i] = 0;
                            }
                        } else {
                            equipSlots[i] = 0;
                        }
                    }
                }
            } else {
                Point2 pos = Point2.unpack(mask);
                float destX = pos.x * Vars.tilesize, destY = pos.y * Vars.tilesize;
                if (Mathf.dst(x, y, destX, destY) <= range()) {
                    targetX = destX; targetY = destY;
                    hasTargetSet = true;
                    burstDelayTimer = Mathf.random(12f, 120f);
                    burstQueue = 1;
                    Fx.select.at(destX, destY);
                } else {
                    Fx.smeltsmoke.at(destX, destY);
                }
            }
        }

        void setSlotConfig(int mask) {
            for (int i = 0; i < 4; i++) equipSlots[i] = (mask >> (i * 3)) & 7;
        }

        int getSlotConfig() {
            int mask = 0;
            for (int i = 0; i < 4; i++) mask |= ((equipSlots[i] & 7) << (i * 3));
            return mask;
        }

        float getDamageMultiplier() { return 1f + getItemCount(1) * 0.05f + getItemCount(5) * 0.10f; }
        float getSpeedMultiplier() { return 1f + getItemCount(2) * 0.05f + getItemCount(5) * 0.10f; }
        float getRangeMultiplier() { return 1f + getItemCount(3) * 0.05f + getItemCount(5) * 0.50f; }
        float getCritChance() { return getItemCount(6) * 0.05f; }
        float getCritMultiplier() { return 1f + getItemCount(6) * 0.15f; }
        boolean hasStarlightPassive() { return getItemCount(6) > 0; }
        boolean hasSallowyrPassive() { return getItemCount(4) > 0; }

        @Override
        public float range() { return baseMaxRange * getRangeMultiplier(); }

        @Override
        protected void updateShooting() { reloadCounter = 0f; }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            Table topRowTable = new Table();
            topRowTable.background(Styles.black6);
            topRowTable.margin(0f);

            topRowTable.button(Icon.commandRally, Styles.cleari, 40, () -> {
                deselect();
                selectingTarget = true;
                Vars.ui.hudfrag.showToast(isEn() ? "Click target location within range!" : "Nhấp vào vị trí mục tiêu bắn trong tầm hoạt động!");
            }).size(50, 40).tooltip(isEn() ? "Set Target Location" : "Đặt vị trí mục tiêu bắn");

            topRowTable.button(Icon.info, Styles.cleari, 40, () -> {
                String title = isEn() ? " Overyr Turret Stats " : " Thông số pháo Overyr ";
                int statDmgPercent = Math.round(getDamageMultiplier() * 100);
                int statSpeedPercent = Math.round(getSpeedMultiplier() * 100);
                int statRangePercent = Math.round(getRangeMultiplier() * 100);
                int currentRangePx = Math.round(range());
                int critChance = Math.round(getCritChance() * 100);
                int critDmgPercent = Math.round(getCritMultiplier() * 100);

                    String descStr = isEn() ?
                        "[gold]⚡ TURRET EQUIPMENT STATS ⚡[]\n" +
                        "[lightgray]Copper:[] [yellow]" + getItemCount(1) + " / 4[] | " +
                        "[lightgray]Silicon:[] [yellow]" + getItemCount(2) + " / 4[] | " +
                        "[lightgray]Thorium:[] [yellow]" + getItemCount(3) + " / 4[]\n" +
                        "[lightgray]Sallowyr:[] [yellow]" + getItemCount(4) + " / 4[] | " +
                        "[lightgray]Obsidis:[] [yellow]" + getItemCount(5) + " / 4[] | " +
                        "[lightgray]Starlight:[] [yellow]" + getItemCount(6) + " / 4[]\n\n" +
                        "[lightgray]Attack Multiplier:[] [green]" + statDmgPercent + "%[]\n" +
                        "[lightgray]Fire Rate Multiplier:[] [green]" + statSpeedPercent + "%[]\n" +
                        "[lightgray]Effective Range:[] [orange]" + currentRangePx + " px[] [lime](" + statRangePercent + "%)[]\n" +
                        "[lightgray]Crit Chance:[] [cyan]" + critChance + "%[]\n" +
                        "[lightgray]Crit Damage:[] [cyan]" + critDmgPercent + "%[]\n\n" +
                        "[sky]⚡ ITEM BUFF MECHANIC:[]\n" +
                        "• [yellow]Copper / Silicon / Thorium:[] +5% Dmg / FireRate / Range per slot.\n" +
                        "• [yellow]Sallowyr:[] Passive: Fires 1 extra physical projectile dealing 150% base dmg when target HP < 80%.\n" +
                        "• [yellow]Obsidis:[] +10% Attack, +10% Speed, +50% Range.\n" +
                        "• [yellow]Starlight:[] +5% Crit Chance, +15% Crit Dmg, Execute targets below 5% HP (+1000 Copper)." :
                        "[gold]⚡ THÔNG SỐ TRANG BỊ THÁP PHÁO ⚡[]\n" +
                        "[lightgray]Đồng:[] [yellow]" + getItemCount(1) + " / 4[] | " +
                        "[lightgray]Silicon:[] [yellow]" + getItemCount(2) + " / 4[] | " +
                        "[lightgray]Thorium:[] [yellow]" + getItemCount(3) + " / 4[]\n" +
                        "[lightgray]Sallowyr:[] [yellow]" + getItemCount(4) + " / 4[] | " +
                        "[lightgray]Obsidis:[] [yellow]" + getItemCount(5) + " / 4[] | " +
                        "[lightgray]Starlight:[] [yellow]" + getItemCount(6) + " / 4[]\n\n" +
                        "[lightgray]Tấn công:[] [green]" + statDmgPercent + "%[]\n" +
                        "[lightgray]Tốc độ bắn:[] [green]" + statSpeedPercent + "%[]\n" +
                        "[lightgray]Tầm bắn hiệu dụng:[] [orange]" + currentRangePx + " pixel[] [lime](" + statRangePercent + "%)[]\n" +
                        "[lightgray]Tỉ lệ bạo kích:[] [cyan]" + critChance + "%[]\n" +
                        "[lightgray]Sát thương bạo kích:[] [cyan]" + critDmgPercent + "%[]\n\n" +
                        "[sky]⚡ CƠ CHẾ BUFF TRANG BỊ:[]\n" +
                        "• [yellow]Đồng / Silicon / Thorium:[] +5% Tấn công / Tốc độ / Tầm bắn mỗi ô.\n" +
                        "• [yellow]Sallowyr:[] Nội tại: Bắn thêm 1 đạn vật lý 150% ST gốc khi máu mục tiêu < 80%.\n" +
                        "• [yellow]Obsidis:[] +10% TC/TĐ, +50% PB.\n" +
                        "• [yellow]Starlight:[] +5% Bạo, +15% ST Bạo, Kết liễu mục tiêu <5% HP (+1000 Đồng).";
                BaseDialog dialog = new BaseDialog(title);
                Table infoTable = new Table();
                Cell<Label> cell = infoTable.add(descStr).width(360f);
                cell.get().setWrap(true); cell.get().setAlignment(Align.left);
                ScrollPane scroll = new ScrollPane(infoTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton(); dialog.show();
            }).size(50, 40).tooltip(isEn() ? "View detailed stats" : "Xem thông số chi tiết hệ thống");

            table.add(topRowTable).center().padBottom(3f).row();
            buildSlotUI(table, this, () -> deselect());
        }

        @Override
        public Object config() { return Integer.valueOf(getSlotConfig()); }

        // JS còn override configured() gọi handleEquipConfig khi `typeof value === "number"`: giá trị Integer của Java
        // trong Rhino là "object" nên nhánh đó không bao giờ chạy; config(Integer.class) ở trên đã xử lý. Bài học 29.

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(getSlotConfig());
            write.f(targetX);
            write.f(targetY);
            write.bool(hasTargetSet);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setSlotConfig(read.s());
            targetX = read.f();
            targetY = read.f();
            hasTargetSet = read.bool();
        }

        /** JS: object {x,y,targetX,targetY,timer,state:"sky_fall",damage,isExtra,rotation}; state chỉ có một giá trị nên bỏ. */
        static final class Orb {
            float x, y, targetX, targetY, timer, damage, rotation;
            boolean isExtra;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            float dt = Time.delta;
            spinAngle = (spinAngle - 1.5f * dt) % 360f;

            if (selectingTarget && Core.input.keyTap(KeyCode.mouseLeft)) {
                Vec2 raw = Core.camera.unproject(Core.input.mouse());
                Tile tile = Vars.world.tileWorld(raw.x, raw.y);
                if (tile != null) configure(Integer.valueOf(Point2.pack(tile.x, tile.y)));
                selectingTarget = false;
            }

            float speedMultiplier = getSpeedMultiplier();
            float adjustedDt = dt * speedMultiplier;
            final float zoneRadius = 10f * Vars.tilesize;

            if (hasTargetSet) {
                final boolean[] found = {false};
                Units.nearbyEnemies(team, targetX - zoneRadius, targetY - zoneRadius, zoneRadius * 2f, zoneRadius * 2f, u -> {
                    if (u.within(targetX, targetY, zoneRadius) && !u.dead) found[0] = true;
                });
                if (found[0]) {
                    if (burstQueue > 0) {
                        if (burstDelayTimer > 0f) {
                            burstDelayTimer -= adjustedDt;
                        } else if (isReadyToShoot()) {
                            fireAtTarget();
                            if (hasAmmo()) useAmmo();
                            burstQueue--;
                        }
                    } else {
                        burstDelayTimer = Mathf.random(12f, 120f) / speedMultiplier;
                        burstQueue = 1;
                    }
                }
            }

            for (int i = orbs.size() - 1; i >= 0; i--) {
                final Orb b = orbs.get(i);
                b.timer += dt;

                float flySpeed = 16f * dt;
                float angle = Angles.angle(b.x, b.y, b.targetX, b.targetY);
                b.x += Angles.trnsx(angle, flySpeed);
                b.y += Angles.trnsy(angle, flySpeed);
                b.rotation = angle;
                if (Mathf.chance(0.3)) Fx.shootHeal.at(b.x, b.y, angle, Pal.accent);

                if (Mathf.dst(b.x, b.y, b.targetX, b.targetY) <= flySpeed) {
                    physicalImpactFx.at(b.targetX, b.targetY);

                    final Unit[] chosen = {null};
                    Units.nearbyEnemies(team, b.targetX - zoneRadius, b.targetY - zoneRadius, zoneRadius * 2f, zoneRadius * 2f, u -> {
                        if (chosen[0] == null && u.within(b.targetX, b.targetY, zoneRadius) && !u.dead) chosen[0] = u;
                    });

                    if (alive(chosen[0])) {
                        float enemyHpRatio = chosen[0].health / chosen[0].maxHealth;
                        applyDamageWithStarlight(this, chosen[0], b.damage);
                        if (!b.isExtra && hasSallowyrPassive() && enemyHpRatio < 0.80f) {
                            float extraDmg = 80f * 1.50f * getDamageMultiplier();
                            spawnSkyPhysicalBullet(b.targetX, b.targetY, extraDmg, true);
                        }
                    } else {
                        Damage.damage(team, b.targetX, b.targetY, zoneRadius, b.damage);
                    }
                    orbs.remove(i); // thêm đạn mới ở cuối khi duyệt ngược vẫn an toàn (bài học 40)
                }
            }
        }

        static boolean alive(Unit u) { return u != null && u.isValid() && !u.dead; }

        void spawnSkyPhysicalBullet(float tx, float ty, float baseDmg, boolean isExtra) {
            Orb o = new Orb();
            o.x = tx - 200f + Mathf.range(30f);
            o.y = ty + 500f + Mathf.range(30f);
            o.targetX = tx; o.targetY = ty; o.damage = baseDmg; o.isExtra = isExtra;
            o.rotation = Angles.angle(o.x, o.y, tx, ty);
            orbs.add(o);
        }

        void fireAtTarget() {
            final float subBeamDmg = 80f * getDamageMultiplier();
            float aimAngle = Angles.angle(x, y, targetX, targetY);

            // 7 tham số = (velocityScl, lifetimeScl), KHÔNG phải sát thương: laser luôn 150 sát thương gốc (bài học 3).
            directBeamLaser.create(this, team, x, y, aimAngle, subBeamDmg, 1f);
            miniCrosshairWarningFx.at(targetX, targetY);

            final float cx = targetX, cy = targetY;
            final float splashRadius = 10f * Vars.tilesize;

            Time.run(60f, () -> {
                for (int l = 0; l < 5; l++) {
                    Time.run(Mathf.random(12f, 48f), () -> {
                        float lx = cx + Mathf.range(splashRadius * 0.6f);
                        float ly = cy + Mathf.range(splashRadius * 0.6f);
                        skyLaserStrikeFx.at(lx, ly);
                        Damage.damage(team, lx, ly, splashRadius, subBeamDmg * 1.5f);
                    });
                }
                for (int i = 0; i < 20; i++) {
                    Time.run(Mathf.random(12f, 48f), () -> {
                        float tx = cx + Mathf.range(splashRadius * 0.8f);
                        float ty = cy + Mathf.range(splashRadius * 0.8f);
                        spawnSkyPhysicalBullet(tx, ty, subBeamDmg, false);
                    });
                }
            });
        }

        boolean isReadyToShoot() { return isValid() && hasAmmo(); }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Draw.z(Layer.overlayUI);
            Drawf.dashCircle(x, y, block.size * 4f, Color.green);
            Draw.reset();
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            float currentRange = range();
            float zoneRadius = 10f * Vars.tilesize;
            Draw.z(Layer.overlayUI);
            Drawf.dashCircle(x, y, currentRange, Pal.accent);
            if (hasTargetSet && Mathf.dst(x, y, targetX, targetY) <= currentRange) {
                Drawf.dashLine(Pal.accent, x, y, targetX, targetY);
                Drawf.square(targetX, targetY, 6f, 0f, Pal.accent);
                Drawf.dashCircle(targetX, targetY, zoneRadius, Color.valueOf("fef08a"));
            }
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();
            // JS: block.turretRegion không tồn tại ở v157 → luôn dùng block.region (bài học 13).
            Draw.rect(block.region, x, y, spinAngle);

            TextureRegion bulletRegion = PerfCore.region("newex-overyr-bullet");
            for (int i = 0; i < orbs.size(); i++) {
                Orb b = orbs.get(i);
                float rot = b.rotation;
                Drawf.light(b.x, b.y, 50f, Pal.accent, 0.8f);
                if (bulletRegion.found()) {
                    Draw.color(Pal.accent);
                    Draw.rect(bulletRegion, b.x, b.y, bulletRegion.width * 1.25f / 4f, bulletRegion.height * 1.25f / 4f, rot - 90f);
                    Draw.color();
                    Draw.rect(bulletRegion, b.x, b.y, rot - 90f);
                } else {
                    Draw.color(Pal.accent, Color.white, 0.4f);
                    Lines.stroke(3f);
                    Lines.poly(b.x, b.y, 5, 6f, rot);
                    Draw.color(Pal.accent);
                    Lines.stroke(1.8f);
                    Lines.poly(b.x, b.y, 5, 5f, rot);
                    Draw.color(Color.white);
                    Lines.stroke(1.2f);
                    Lines.poly(b.x, b.y, 5, 2.5f, rot);
                }
            }

            if (selectingTarget) {
                float currentRange = range();
                float zoneRadius = 10f * Vars.tilesize;
                Draw.z(Layer.overlayUI);
                Drawf.dashCircle(x, y, currentRange, Pal.accent);

                Vec2 mouseWorld = Core.camera.unproject(Core.input.mouse());
                float mx = mouseWorld.x, my = mouseWorld.y;
                boolean isWithinRange = Mathf.dst(x, y, mx, my) <= currentRange;
                Color targetColor = isWithinRange ? Pal.accent : Color.red;
                Color zoneColor = isWithinRange ? Color.valueOf("fef08a") : Color.red;

                Drawf.dashLine(targetColor, x, y, mx, my);
                Tile mouseTile = Vars.world.tileWorld(mx, my);
                if (mouseTile != null) {
                    Drawf.square(mouseTile.drawx(), mouseTile.drawy(), 6f, 0f, targetColor);
                    Drawf.dashCircle(mouseTile.drawx(), mouseTile.drawy(), zoneRadius, zoneColor);
                } else {
                    Drawf.square(mx, my, 6f, 0f, targetColor);
                    Drawf.dashCircle(mx, my, zoneRadius, zoneColor);
                }
                Draw.reset();
            }
        }
    }
}
