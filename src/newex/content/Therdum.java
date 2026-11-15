package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ therdum.js. Pháo shotgun Therdum + hệ thống Phúc lợi A (roll ngẫu nhiên 1A/2A/3A). */
public final class Therdum {
    private Therdum() {}

    private static final int REQ = 2000; // đồng / chì / silicon cho Phúc lợi A
    private static final float BASE_RANGE = 105f;

    public static ItemTurret block;
    /** Thay cho global.NexTherdum (newmode-pvp.js dùng). */
    public static TherdumBullet bullet;
    public static Effect muzzleFx;
    private static Effect smokeHitFx;

    /** Lưu ý: tham số t(vi, en) — tiếng Việt trước, giống bản JS. */
    private static String t(String vi, String en) {
        String loc = Core.settings.getString("locale", "vi");
        return loc != null && loc.startsWith("en") ? en : vi;
    }

    public static class TherdumBullet extends BasicBulletType {
        public TherdumBullet() {
            speed = 6f;
            damage = 24f;
            width = 10f;
            height = 14f;
            hitEffect = smokeHitFx;
            despawnEffect = smokeHitFx;
        }

        @Override
        public void draw(Bullet b) {
            Draw.z(Layer.bullet);
            Draw.color(Color.valueOf("#ff5500"));
            Draw.alpha(0.6f);
            Fill.circle(b.x, b.y, 4.5f);
            Draw.color(Color.valueOf("#ffaa00"));
            Draw.alpha(1f);
            Fill.circle(b.x, b.y, 2.5f);
            Draw.reset();
        }

        @Override
        public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other != null && b.owner instanceof TherdumBuild) {
                ((TherdumBuild) b.owner).handleHit(other);
            }
        }
    }

    /** Gọi trong NexMod.loadContent(). hjson (therdum.hjson) sẽ ghi đè chỉ số lên block này. */
    public static void create() {
        smokeHitFx = new Effect(30f, e -> {
            Draw.z(Layer.effect + 0.01f);
            float alpha = 1f - e.fin();
            Draw.color(Color.gray, Color.darkGray, e.fin());
            Draw.alpha(alpha * 0.7f);
            for (int i = 0; i < 3; i++) {
                float angle = Mathf.randomSeed(e.id + i, 0f, 360f);
                float dist = Mathf.randomSeed(e.id * 2L + i, 2f, 8f) * e.fin();
                float size = Mathf.randomSeed(e.id * 3L + i, 2.5f, 4.5f) * (1f - e.fin() * 0.3f);
                Fill.circle(e.x + Angles.trnsx(angle, dist), e.y + Angles.trnsy(angle, dist), size);
            }
            Draw.reset();
        });

        muzzleFx = new Effect(12f, e -> {
            if (Vars.state.isPaused()) return;
            Draw.z(Layer.effect);
            Draw.color(PerfCore.color("#ffcc44"), PerfCore.color("#ff5500"), e.fin());
            Lines.stroke((1f - e.fin()) * 1.8f);
            for (int i = 0; i < 5; i++) {
                float angle = e.rotation + Mathf.randomSeedRange(e.id + i, 16f);
                float lenStart = Mathf.randomSeed(e.id * 2L + i, 2f, 6f) + e.fin() * 6f;
                float lenEnd = lenStart + Mathf.randomSeed(e.id * 3L + i, 4f, 10f) * (1f - e.fin() * 0.5f);
                Lines.line(e.x + Angles.trnsx(angle, lenStart), e.y + Angles.trnsy(angle, lenStart),
                           e.x + Angles.trnsx(angle, lenEnd), e.y + Angles.trnsy(angle, lenEnd));
            }
            Draw.reset();
        });

        bullet = new TherdumBullet();

        block = new ItemTurret("therdum");
        block.configurable = true;
        block.health = 17000;
        block.range = BASE_RANGE;
        block.reload = 120f;
        block.shootY = 12f; // shootOffset trong JS
        block.config(Integer.class, (TherdumBuild tile, Integer value) -> {
            if (tile != null && value >= 10 && value < 20) tile.perkA = value - 10;
        });
        block.buildType = () -> new TherdumBuild(block);
        NewModePvp.therdumFx = muzzleFx;      // JS global.NexTherdum {fx, bullet}
        NewModePvp.therdumBullet = bullet;
    }

    public static class TherdumBuild extends ItemTurret.ItemTurretBuild {
        int perkA = 0;

        public TherdumBuild(ItemTurret outer) {
            outer.super();
        }

        @Override
        public float range() {
            switch (perkA) {
                case 1: return BASE_RANGE * 1.5f;
                case 2: return BASE_RANGE * 1.2f;
                case 3: return BASE_RANGE * 0.7f;
                default: return BASE_RANGE;
            }
        }

        void handleHit(Hitboxc target) {
            if (perkA == 2 && target != null) {
                Damage.damage(team, target.x(), target.y(), 10f, 5f, false, true);
            }
        }

        @Override
        protected void shoot(BulletType type) {
            if (!hasAmmo()) return;

            float baseDmg = 24f;
            int bulletCount = 40;
            float spread = 12f;
            float curRange = range();
            float offset = 12f;

            float sx = x + Angles.trnsx(rotation, offset), sy = y + Angles.trnsy(rotation, offset);
            muzzleFx.at(x + Angles.trnsx(rotation, offset - 6f), y + Angles.trnsy(rotation, offset - 6f), rotation);

            if (perkA == 1) { baseDmg *= 1.5f; bulletCount += 10; }
            else if (perkA == 2) baseDmg *= 1.2f;
            else if (perkA == 3) baseDmg *= 3f;

            for (int i = 0; i < bulletCount; i++) {
                float rndSpeed = Mathf.random(3f, 9f);
                float angle = rotation + Mathf.range(spread);
                Bullet b = bullet.create(this, team, sx, sy, angle);
                if (b != null) {
                    b.vel.setLength(rndSpeed);
                    b.lifetime = curRange / rndSpeed;
                    b.damage = baseDmg;
                }
            }
            useAmmo();
        }

        @Override
        public void updateTile() {
            super.updateTile();
            // Phúc lợi 3A: nạp đạn nhanh gấp đôi (2s -> 1s)
            if (perkA == 3 && reloadCounter < ((ItemTurret) block).reload) {
                reloadCounter += delta() * baseReloadSpeed();
            }
        }

        private static String col(int have) { return have >= REQ ? "[green]" : "[red]"; }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(t("Trung tâm nâng cấp pháo Therdum", "Therdum Turret Upgrade Center"));

            CoreBuild core = team.core();
            String snap;
            if (core == null) {
                snap = t("[red]Không tìm thấy Lõi Đội![]", "[red]Team Core not found![]");
            } else {
                int cu = core.items.get(Items.copper), pb = core.items.get(Items.lead), si = core.items.get(Items.silicon);
                snap = t(
                    "[gold]YÊU CẦU TÀI NGUYÊN LÕI (CẤP MK1):[]\n[yellow]★ ROLL PHÚC LỢI A:[] Đồng: " + col(cu) + cu + "[]/2000 | Chì: " + col(pb) + pb + "[]/2000 | Silicon: " + col(si) + si + "[]/2000\n[gray](Pháo Therdum Mk1 nâng cấp trực tiếp qua hệ thống Phúc lợi)[]",
                    "[gold]CORE RESOURCE REQUIREMENTS (MK1 TIER):[]\n[yellow]★ ROLL PERK A:[] Copper: " + col(cu) + cu + "[]/2000 | Lead: " + col(pb) + pb + "[]/2000 | Silicon: " + col(si) + si + "[]/2000\n[gray](Therdum Mk1 upgrades directly via Perk system)[]");
            }
            dialog.cont.add(snap).width(380f).get().setWrap(true);
            ((arc.scene.ui.Label) dialog.cont.getCells().peek().get()).setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table boxA = new Table();
            boxA.background(Styles.black6);
            boxA.margin(12f);
            boxA.add(t("[yellow]★ ROLL PHÚC LỢI A (NGẪU NHIÊN) ★[]", "[yellow]★ ROLL PERK A (RANDOM) ★[]")).row();

            if (perkA == 0) {
                boxA.add(t(
                    "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi A:\n • [green]Phúc lợi 1A:[] +50% Sát thương, Tầm bắn +50% (157.5px), +10 Viên đạn.\n • [green]Phúc lợi 2A:[] +20% Sát thương, Tầm bắn +20% (126px), +5 Dmg lan (10px).\n • [green]Phúc lợi 3A:[] +200% Sát thương, Tầm bắn -30% (73.5px), Nạp đạn nhanh (2s -> 1s).",
                    "Activate upgrade protocol to randomly get 1 of 3 Perk A choices:\n • [green]Perk 1A:[] +50% Damage, Range +50% (157.5px), +10 Bullets.\n • [green]Perk 2A:[] +20% Damage, Range +20% (126px), +5 Splash Dmg (10px).\n • [green]Perk 3A:[] +200% Damage, Range -30% (73.5px), Fast Reload (2s -> 1s)."
                )).width(340f).get().setWrap(true);
                ((arc.scene.ui.Label) boxA.getCells().peek().get()).setAlignment(Align.left);
                boxA.row();

                boxA.button(t("[yellow]QUAY PHÚC LỢI A (2K Đồng/Chì/Silicon)[]", "[yellow]ROLL PERK A (2K Cop/Lead/Sil)[]"), () -> {
                    CoreBuild c = team.core();
                    if (c != null && c.items.get(Items.copper) >= REQ && c.items.get(Items.lead) >= REQ && c.items.get(Items.silicon) >= REQ) {
                        NexNet.take(c, Items.copper, REQ);
                        NexNet.take(c, Items.lead, REQ);
                        NexNet.take(c, Items.silicon, REQ);

                        int res = (int) Math.floor(Mathf.random(1f, 3.99f));
                        perkA = res;
                        configure(Integer.valueOf(10 + res));

                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(4f, 4f, x, y);
                        Vars.ui.showInfo(t("[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[yellow]PHÚC LỢI " + res + "A[]", "[gold]YOU ROLLED:[]\n[yellow]PERK " + res + "A[]"));
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(t("[red]Không đủ tài nguyên roll Phúc lợi A![]", "[red]Not enough resources to roll Perk A![]"));
                    }
                }).size(280f, 40f);
            } else {
                String txt = "";
                if (perkA == 1) txt = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1A\n• Sát thương +50% (36)\n• Tầm bắn +50% (157.5px)\n• Số đạn +10 (50 viên)[]", "[green]✔ ACTIVATED: PERK 1A\n• Damage +50% (36)\n• Range +50% (157.5px)\n• Bullets +10 (50 count)[]");
                if (perkA == 2) txt = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2A\n• Sát thương +20% (28.8)\n• Tầm bắn +20% (126px)\n• Gây thêm 5 Dmg lan (Phạm vi 10px)[]", "[green]✔ ACTIVATED: PERK 2A\n• Damage +20% (28.8)\n• Range +20% (126px)\n• +5 Splash Dmg (10px Radius)[]");
                if (perkA == 3) txt = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3A\n• Sát thương +200% (72)\n• Tầm bắn -30% (73.5px)\n• Tốc độ nạp đạn tăng 100% (2s -> 1s)[]", "[green]✔ ACTIVATED: PERK 3A\n• Damage +200% (72)\n• Range -30% (73.5px)\n• Reload Speed +100% (2s -> 1s)[]");
                boxA.add(txt).width(340f).get().setWrap(true);
                ((arc.scene.ui.Label) boxA.getCells().peek().get()).setAlignment(Align.left);
            }

            Table mainTable = new Table();
            mainTable.add(boxA).width(360f);
            ScrollPane scroll = new ScrollPane(mainTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String desc = t(
                "[gold]⚡ THÔNG SỐ CƠ BẢN PHÁO THERDUM (MK1) ⚡[]\n• Máu: 17,000 | Tầm bắn: 105px (13.1 ô) | Sát thương gốc: 24.0\n• Cơ chế: Bắn shotgun tỏa 40 viên đạn, lifetime đạn tự điều chỉnh chuẩn theo tầm bắn.\n• Nâng cấp: Nâng cấp trực tiếp chỉ số và kỹ năng qua hệ thống Phúc lợi A.",
                "[gold]⚡ THERDUM TURRET BASIC STATS (MK1) ⚡[]\n• Health: 17,000 | Range: 105px (13.1 tiles) | Base Damage: 24.0\n• Mechanism: Shotgun spread firing 40 bullets, bullet lifetime auto-adjusts to range.\n• Upgrades: Directly upgrade stats and skills via Perk A system.");

            if (perkA > 0) {
                desc += t("\n\n[yellow]★ ĐÃ KÍCH HOẠT PHÚC LỢI A ★[]", "\n\n[yellow]★ PERK A ACTIVATED ★[]");
                if (perkA == 1) desc += t(
                    "\n[green]• Phúc lợi 1A: Sát thương +50% (36), Tầm bắn +50% (157.5px), Số đạn +10 (50 viên).[]\n[white]• Tăng quy mô hỏa lực diện rộng và khoảng cách áp chế.[]",
                    "\n[green]• Perk 1A: Damage +50% (36), Range +50% (157.5px), Bullets +10 (50 count).[]\n[white]• Increases wide-area firepower and suppression distance.[]");
                if (perkA == 2) desc += t(
                    "\n[green]• Phúc lợi 2A: Sát thương +20% (28.8), Tầm bắn +20% (126px).[]\n[white]• Đạn gây thêm 5 Dmg lan trong phạm vi 10px quanh mục tiêu.[]",
                    "\n[green]• Perk 2A: Damage +20% (28.8), Range +20% (126px).[]\n[white]• Bullets deal +10 splash damage in 10px radius around target.[]");
                if (perkA == 3) desc += t(
                    "\n[green]• Phúc lợi 3A: Sát thương +200% (72), Tầm bắn -30% (73.5px), Nạp đạn nhanh +100% (1s).[]\n[white]• Biến thành pháo cận chiến siêu sát thương với tốc độ xả đạn cực nhanh.[]",
                    "\n[green]• Perk 3A: Damage +200% (72), Range -30% (73.5px), Fast Reload +100% (1s).[]\n[white]• Transforms into ultra-high damage melee turret with extreme burst rate.[]");
            }

            BaseDialog dialog = new BaseDialog(t(" Thông số pháo Therdum Mk1 ", " Therdum Mk1 Stats "));
            Table info = new Table();
            info.add(desc).width(360f).get().setWrap(true);
            ((arc.scene.ui.Label) info.getCells().peek().get()).setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog)
                .size(50f, 40f).tooltip(t("Trung tâm nâng cấp pháo Therdum", "Therdum Turret Upgrade Center"));
            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog)
                .size(50f, 40f).tooltip(t("Xem thông số pháo Therdum Mk1", "View Therdum Mk1 Stats"));
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(perkA);
            write.b(0);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            perkA = read.b();
            read.b();
        }
    }
}
