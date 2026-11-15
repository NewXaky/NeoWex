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
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import newex.NexNet;

/** Kén triệu hồi Tank-15 (đặt sẵn trong content hjson). */
public final class LeolyrSummoner {
    private LeolyrSummoner() {}

    private static Effect orbitalLockOnEffect, satelliteImpactEffect;

    private static boolean en() {
        String l = Core.settings.getString("locale", "");
        return l != null && l.startsWith("en");
    }

    private static String t(String enText, String viText) { return en() ? enText : viText; }

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
        String selectedUnit = "newex-tank-15";

        @Override
        public boolean acceptItem(Building source, Item item) {
            return items.get(item) < block.itemCapacity;
        }

        /** {copper, silicon} */
        int[] getRequirements() {
            return new int[]{4000, 500};
        }

        private void startSummon() {
            summoning = true;
            summonTimer = 300f;
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
                int[] req = getRequirements();
                if (!core.items.has(Items.copper, req[0]) || !core.items.has(Items.silicon, req[1])) {
                    Vars.ui.showInfo(t(
                        "[scarlet]Not enough resources in Core![]\nRequired: [accent]" + req[0] + " Copper[] and [accent]" + req[1] + " Silicon[] in Core.",
                        "[scarlet]Không đủ tài nguyên trong Lõi![]\nCần có: [accent]" + req[0] + " Copper[] và [accent]" + req[1] + " Silicon[] trong Core."));
                    return;
                }
                NexNet.take(core, Items.copper, req[0]);
                NexNet.take(core, Items.silicon, req[1]);
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

            unitEntry(card, dialog,
                t("[orange]⚡ SELECT SUMMON: TANK-15[]", "[orange]⚡ CHỌN TRIỆU HỒI: TANK-15[]"), "newex-tank-15",
                t("[orange]Target set: Tank-15[]", "[orange]Đã cài đặt mục tiêu: Tank-15[]"),
                t("[gold]📊 TANK-15 ANALYSIS DATA:[]\n• [accent]Summon Cost:[] [white]4000 Copper[] + [white]500 Silicon[]\n• [pink]Rail Laser:[] Fires piercing laser pairs; reload speeds up to x5 while firing.",
                  "[gold]📊 DỮ LIỆU PHÂN TÍCH TANK-15:[]\n• [accent]Chi phí triệu hồi:[] [white]4000 Copper[] + [white]500 Silicon[]\n• [pink]Súng Rail Laser:[] Bắn cặp tia laser xuyên giáp, tốc độ nạp tăng tới x5 khi bắn liên tục."));

            ScrollPane scroll = new ScrollPane(card);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f).width(340f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void unitEntry(Table card, BaseDialog dialog, String btn, String unitId, String toast, String desc) {
            card.button(btn, () -> {
                selectedUnit = unitId;
                Vars.ui.showInfo(toast);
                dialog.hide();
            }).size(300f, 42f).row();
            card.add().height(6f).row();
            card.add(desc).width(320f).get().setWrap(true);
            ((arc.scene.ui.Label) card.getCells().peek().get()).setAlignment(Align.left);
            card.row();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (!summoning) {
                int[] req = getRequirements();
                if (items.has(Items.copper, req[0]) && items.has(Items.silicon, req[1])) {
                    items.remove(Items.copper, req[0]);
                    items.remove(Items.silicon, req[1]);
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

                    UnitType ut = Vars.content.getByName(ContentType.unit, selectedUnit);
                    if (ut == null && selectedUnit.startsWith("newex-")) {
                        ut = Vars.content.getByName(ContentType.unit, selectedUnit.replace("newex-", ""));
                    }
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
