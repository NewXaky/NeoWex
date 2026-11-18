package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import newex.NexNet;

/** Kén triệu hồi: Tank-15, Archueng, Honivery, Aureon, Zeralion (khối newex-spawner đặt sẵn trong content hjson). */
public final class LeolyrSummoner {
    private LeolyrSummoner() {}

    private static Effect orbitalLockOnEffect, satelliteImpactEffect;

    private static boolean en() {
        String l = Core.settings.getString("locale", "");
        return l != null && l.startsWith("en");
    }

    private static String t(String enText, String viText) { return en() ? enText : viText; }

    // ------------------------------------------------------------------ danh sách unit triệu hồi được
    private static final class Entry {
        final String id, color, viName, enName, viDesc, enDesc;
        final ItemStack[] cost;
        Entry(String id, String color, String viName, String enName, String viDesc, String enDesc, ItemStack... cost) {
            this.id = id; this.color = color; this.viName = viName; this.enName = enName; this.viDesc = viDesc; this.enDesc = enDesc; this.cost = cost;
        }
        String name() { return t(enName, viName); }
    }

    private static Entry[] entries;

    /** Bảng giá: muốn chỉnh giá / thêm unit thì sửa ở đây (tạo trễ để chắc chắn Items đã nạp xong). */
    private static Entry[] entries() {
        if (entries != null) return entries;
        entries = new Entry[]{
            new Entry("newex-tank-15", "ff9f43", "TANK-15", "TANK-15",
                "Súng Rail Laser: bắn cặp tia laser xuyên giáp, tốc độ nạp tăng tới x5 khi bắn liên tục.",
                "Rail Laser: fires piercing laser pairs; reload speeds up to x5 while firing.",
                new ItemStack(Items.copper, 4000), new ItemStack(Items.silicon, 500)),
            new Entry("newex-archueng", "ff6b6b", "ARCHUENG", "ARCHUENG",
                "Unit chân nhện hạng nặng, giáp dày, có kĩ năng chủ động riêng.",
                "Heavy legged unit with thick armor and its own active skill.",
                new ItemStack(Items.copper, 5000), new ItemStack(Items.silicon, 2000), new ItemStack(Items.titanium, 1500), new ItemStack(Items.thorium, 800)),
            new Entry("newex-honivery", "7df9ff", "HONIVERY", "HONIVERY",
                "Unit chiến tranh tầm cao: súng Gatling cùng kiếm, có khiên Aegis và kĩ năng riêng.",
                "High-tier war unit: Gatling weapon plus sword, Aegis shield and its own skill.",
                new ItemStack(Items.copper, 6000), new ItemStack(Items.silicon, 3000), new ItemStack(Items.titanium, 2500), new ItemStack(Items.plastanium, 1000), new ItemStack(Items.thorium, 1500)),
            new Entry("newex-aureon", "ffd34d", "AUREON", "AUREON",
                "Unit chân vàng kim, khóa mục tiêu và giáng sét.",
                "Gold legged unit that locks onto targets and calls lightning.",
                new ItemStack(Items.silicon, 4000), new ItemStack(Items.titanium, 3000), new ItemStack(Items.thorium, 2500), new ItemStack(Items.plastanium, 1500), new ItemStack(Items.surgeAlloy, 1000)),
            new Entry("newex-zeralion", "ffb300", "ZERALION", "ZERALION",
                "Unit chiến tranh tối thượng: bắn từng viên thẳng hàng, laser vàng khóa mục tiêu.",
                "Ultimate war unit: single-file shots and a gold laser that locks onto targets.",
                new ItemStack(Items.silicon, 5000), new ItemStack(Items.thorium, 4000), new ItemStack(Items.plastanium, 2500), new ItemStack(Items.surgeAlloy, 2000), new ItemStack(Items.phaseFabric, 1500))
        };
        return entries;
    }

    private static UnitType unitOf(String id) {
        UnitType ut = Vars.content.getByName(ContentType.unit, id);
        if (ut == null && id.startsWith("newex-")) ut = Vars.content.getByName(ContentType.unit, id.substring(6));
        return ut;
    }

    private static String costText(ItemStack[] cost) {
        StringBuilder sb = new StringBuilder();
        for (ItemStack s : cost) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append("[white]").append(s.amount).append(' ').append(s.item.localizedName).append("[]");
        }
        return sb.toString();
    }

    private static void drawEllipseWave(float cx, float cy, float rx, float ry, float rotDeg) {
        int points = 24;
        float rot = rotDeg * Mathf.degRad;
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        float lastX = cx + rx * cos, lastY = cy + rx * sin;
        for (int i = 1; i <= points; i++) {
            float a = (i * 360f / points) * Mathf.degRad;
            float lx = (float) Math.cos(a) * rx, ly = (float) Math.sin(a) * ry;
            float nx = cx + (lx * cos - ly * sin), ny = cy + (lx * sin + ly * cos);
            Lines.line(lastX, lastY, nx, ny);
            lastX = nx; lastY = ny;
        }
    }

    public static void init() {
        final Color purple = Color.valueOf("c084fc");
        final Color pink = Color.valueOf("e879f9");

        orbitalLockOnEffect = new Effect(40f, e -> {
            float progress = e.data instanceof Float ? (Float) e.data : 1f;
            Draw.z(Layer.effect + 2f);
            Color c = purple.cpy().lerp(pink, 1f - progress);

            Lines.stroke(1.2f, c);
            Lines.circle(e.x, e.y, 24f * progress);
            Lines.circle(e.x, e.y, 12f * progress);

            float size = 14f + 16f * progress, len = 6f;
            Lines.stroke(1.6f, c);
            Lines.line(e.x + size, e.y + size, e.x + size - len, e.y + size);
            Lines.line(e.x + size, e.y + size, e.x + size, e.y + size - len);
            Lines.line(e.x - size, e.y + size, e.x - size + len, e.y + size);
            Lines.line(e.x - size, e.y + size, e.x - size, e.y + size - len);
            Lines.line(e.x + size, e.y - size, e.x + size - len, e.y - size);
            Lines.line(e.x + size, e.y - size, e.x + size, e.y - size + len);
            Lines.line(e.x - size, e.y - size, e.x - size + len, e.y - size);
            Lines.line(e.x - size, e.y - size, e.x - size, e.y - size + len);

            Lines.stroke(1f, c);
            Lines.line(e.x - 4f, e.y, e.x + 4f, e.y);
            Lines.line(e.x, e.y - 4f, e.x, e.y + 4f);

            Lines.stroke((0.4f + 0.7f * (1f - progress)) * progress, c);
            Lines.line(e.x - 140f, e.y + 250f, e.x, e.y);
            Draw.reset();
        });

        satelliteImpactEffect = new Effect(60f, e -> {
            Draw.z(Layer.effect + 3f);
            float f = e.fin();
            float alpha = 1f - Interp.pow3Out.apply(f);

            Lines.stroke(18f * alpha, purple);
            Lines.line(e.x - 140f, e.y + 250f, e.x, e.y);
            Lines.stroke(8f * alpha, Color.white);
            Lines.line(e.x - 140f, e.y + 250f, e.x, e.y);

            Lines.stroke(3.5f * (1f - f), pink);
            Lines.circle(e.x, e.y, 8f + 55f * f);

            Draw.color(Color.white, purple, f);
            Rand rand = new Rand(e.id);
            for (int i = 0; i < 18; i++) {
                float angle = rand.random(360f);
                float dist = rand.random(2f, 6f) * f * 10f;
                Fill.circle(e.x + Angles.trnsx(angle, dist), e.y + Angles.trnsy(angle, dist), 2.5f * (1f - f));
            }

            float laserAngle = 33f;
            float mx = -140f * Interp.pow2Out.apply(f), my = 250f * Interp.pow2Out.apply(f);
            float r1 = 4f + 48f * Interp.pow3Out.apply(f);

            Lines.stroke(2.5f * (1f - f), purple.cpy().mul(1f - f));
            drawEllipseWave(e.x + mx, e.y + my, r1, r1 * 0.45f, laserAngle);

            float r2 = r1 * (2f / 3f);
            Lines.stroke(1.8f * (1f - f), pink.cpy().mul(1f - f));
            drawEllipseWave(e.x + mx * 0.7f, e.y + my * 0.7f, r2, r2 * 0.45f, laserAngle);
            Draw.reset();
        });

        Events.on(ClientLoadEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-spawner");
            if (b == null) b = Vars.content.getByName(ContentType.block, "spawner");
            if (b == null) return;

            b.configurable = true;
            b.update = true;
            b.hasItems = true;
            b.itemCapacity = 10000;
            b.acceptsItems = true;
            b.buildType = SummonerBuild::new;
        });
    }

    public static class SummonerBuild extends Building {
        boolean summoning = false;
        float summonTimer = 0f;
        int selected = 0;        // vị trí unit đang chọn trong entries()
        int summonIdx = 0;       // unit đang được triệu hồi (chốt lúc bắt đầu)

        @Override
        public boolean acceptItem(Building source, Item item) {
            return items.get(item) < block.itemCapacity;
        }

        private Entry sel() {
            Entry[] es = entries();
            return es[Mathf.clamp(selected, 0, es.length - 1)];
        }

        private void startSummon() {
            summoning = true;
            summonTimer = 300f;
            summonIdx = Mathf.clamp(selected, 0, entries().length - 1);
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            if (summoning) return;
            table.row();

            table.button(Icon.ok, Styles.cleari, 40f, () -> {
                Building core = team.core();
                if (core == null) {
                    Vars.ui.showInfo(t("[scarlet]Core not found![]", "[scarlet]Không tìm thấy nhà chính (Core)![]"));
                    return;
                }
                Entry en = sel();
                if (!core.items.has(en.cost)) {
                    Vars.ui.showInfo(t(
                        "[scarlet]Not enough resources in Core![]\nRequired for " + en.name() + ": " + costText(en.cost),
                        "[scarlet]Không đủ tài nguyên trong Lõi![]\nCần cho " + en.name() + ": " + costText(en.cost)));
                    return;
                }
                for (ItemStack s : en.cost) NexNet.take(core, s.item, s.amount);
                startSummon();
                NexNet.fx(Fx.shieldApply, x, y, 0f, purple());
                deselect();
            }).size(50f, 40f).tooltip(t("Manual Summon (Consume Core resources & Wait 5s)", "Triệu hồi thủ công (Rút tài nguyên từ Lõi Core & Chờ 5s)"));

            table.button(Icon.add, Styles.cleari, 40f, this::openSelectDialog)
                .size(50f, 40f).tooltip(t("Select unit & View stats", "Chọn đơn vị triệu hồi & Xem phân tích thông số"));
        }

        private static Color purple() { return Color.valueOf("c084fc"); }

        private void openSelectDialog() {
            BaseDialog dialog = new BaseDialog(t("Summoning Cocoon System", "Hệ Thống Kén Triệu Hồi"));
            dialog.cont.add(t("[yellow]AVAILABLE UNITS FOR SUMMONING:[]", "[yellow]DANH SÁCH ĐƠN VỊ CÓ THỂ TRIỆU HỒI:[]")).row();
            dialog.cont.add().height(10f).row();

            Table card = new Table();
            card.background(Styles.black6);
            card.margin(10f);

            Entry[] es = entries();
            for (int i = 0; i < es.length; i++) unitEntry(card, dialog, i, es[i]);

            ScrollPane scroll = new ScrollPane(card);
            scroll.setScrollingDisabled(true, false);
            scroll.setFlickScroll(true);
            scroll.setOverscroll(true, true);
            dialog.cont.add(scroll).maxHeight(Math.max(150f, Math.min(420f, Core.scene.getHeight() - 170f))).width(340f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void unitEntry(Table card, BaseDialog dialog, final int idx, final Entry en) {
            card.button("[#" + en.color + "]⚡ " + t("SELECT SUMMON: ", "CHỌN TRIỆU HỒI: ") + en.name() + "[]", () -> {
                selected = idx;
                Vars.ui.showInfo(t("[orange]Target set: " + en.name() + "[]", "[orange]Đã cài đặt mục tiêu: " + en.name() + "[]"));
                dialog.hide();
            }).size(300f, 42f).row();
            card.add().height(6f).row();

            String stats = "";
            UnitType ut = unitOf(en.id);
            if (ut != null) {
                stats = "\n• [accent]" + t("Stats:", "Thông số:") + "[] " + t("HP ", "Máu ") + "[white]" + (int) ut.health + "[]  " + t("Armor ", "Giáp ") + "[white]" + (int) ut.armor + "[]";
            }
            String desc = "[gold]📊 " + t("ANALYSIS: ", "PHÂN TÍCH: ") + en.name() + "[]\n• [accent]" + t("Summon Cost:", "Chi phí triệu hồi:") + "[] " + costText(en.cost)
                + stats + "\n• [pink]" + t("Info:", "Mô tả:") + "[] " + t(en.enDesc, en.viDesc);
            card.add(desc).width(320f).get().setWrap(true);
            ((arc.scene.ui.Label) card.getCells().peek().get()).setAlignment(Align.left);
            card.row();
            card.add().height(12f).row();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (!summoning) {
                Entry en = sel();
                if (items.has(en.cost)) {
                    items.remove(en.cost);
                    startSummon();
                    Fx.shieldApply.at(x, y, 0f, purple());
                }
            }

            if (summoning) {
                summonTimer -= Time.delta;
                float progress = Math.max(0f, summonTimer / 300f);

                if (Mathf.chance(0.65)) orbitalLockOnEffect.at(x, y, 0f, Float.valueOf(progress));

                if (summonTimer <= 0f) {
                    summoning = false;

                    UnitType ut = unitOf(entries()[Mathf.clamp(summonIdx, 0, entries().length - 1)].id);
                    if (ut != null) {
                        Unit unit = ut.create(team);
                        unit.set(x, y);
                        unit.add();
                    }

                    satelliteImpactEffect.at(x, y);
                    Fx.smokeCloud.at(x, y);
                    Fx.spawnShockwave.at(x, y);
                    Effect.shake(7f, 7f, x, y);

                    tile.setAir();
                }
            }
        }
    }
}
