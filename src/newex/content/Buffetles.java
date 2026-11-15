package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.core.World;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Chuyển từ buffetles.js. Pháo Buffetles: bạo kích dồn stack, nhánh MK2 / MK2B có sát thương chuẩn. */
public final class Buffetles {
    private Buffetles() {}

    private static final int[] REQ_MK2 = {400, 300};          // thorium, silicon
    private static final int[] REQ_MK2B = {150, 500, 100};    // surge, silicon, phase

    private static Effect critEffect, speedBuffEffect, resetUpgradeEffect;
    private static ItemTurret block;

    private static boolean en() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static String t(String enText, String viText) { return en() ? enText : viText; }

    private static String pct(float v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static void wrapLeft(Table table, String text, float width) {
        Label l = new Label(text);
        l.setWrap(true);
        l.setAlignment(Align.left);
        table.add(l).width(width);
    }

    public static void init() {
        critEffect = new Effect(20f, e -> {
            Draw.color(Color.gold, Color.scarlet, e.fin());
            for (int i = 0; i < 4; i++) {
                float angle = i * 90f + 45f;
                float len = 3f + e.fin() * 10f;
                Drawf.tri(e.x + Mathf.cosDeg(angle) * (len * 0.2f), e.y + Mathf.sinDeg(angle) * (len * 0.2f),
                          3f * e.fout(), len, angle);
            }
        });
        speedBuffEffect = new Effect(30f, e -> {
            Draw.color(Color.gold, Color.orange, e.fin());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, 6f + e.fin() * 14f);
        });
        resetUpgradeEffect = new Effect(30f, e -> {
            Draw.color(Color.gold, Color.white, e.fin());
            Lines.stroke(3f * e.fout());
            Lines.circle(e.x, e.y, 8f + e.fin() * 20f);
        });

        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        Block found = Vars.content.blocks().find(b -> b != null && b.name != null && b.name.endsWith("buffetles"));
        if (!(found instanceof ItemTurret)) return;
        block = (ItemTurret) found;
        block.configurable = true;

        try {
            block.addBar("crit_rate", (BuffBuild b) -> new Bar(
                () -> "CRIT CHANCE: " + pct(b.getCritRate() * 100f) + "%",
                () -> Color.scarlet,
                () -> b.getCritRate()));
            block.addBar("crit_damage", (BuffBuild b) -> new Bar(
                () -> "CRIT DMG: +" + pct(b.getCritDamageMultiplier() * 100f) + "%",
                () -> Color.gold,
                () -> Math.min(b.getCritDamageMultiplier() / 3f, 1f)));
        } catch (Throwable ignored) {}

        block.config(Integer.class, (BuffBuild tile, Integer value) -> {
            if (tile != null) tile.setTier(value);
        });
        block.buildType = () -> new BuffBuild(block);
    }

    private static class Tracked {
        final Bullet bullet;
        final float trueDmg;
        Tracked(Bullet bullet, float trueDmg) { this.bullet = bullet; this.trueDmg = trueDmg; }
    }

    public static class BuffBuild extends ItemTurret.ItemTurretBuild {
        int critStacks = 0;
        float extraCritDamage = 0f;
        int tierState = 0;
        float speedBuffTimer = 0f;
        final List<Tracked> activeBullets = new ArrayList<>();

        public BuffBuild(ItemTurret outer) {
            outer.super();
        }

        int getTier() { return tierState; }

        void setTier(int val) {
            tierState = val;
            Fx.upgradeCore.at(x, y);
        }

        float getCritRate() {
            float perStack = getTier() == 1 ? 0.005f : 0.001f;
            return 0.05f + critStacks * perStack;
        }

        float getCritDamageMultiplier() { return 0.5f + extraCritDamage; }

        void addStack() {
            critStacks++;
            if (getCritRate() >= 1f) {
                critStacks = 0;
                extraCritDamage += 0.15f;
                resetUpgradeEffect.at(x, y);
            }
        }

        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
            if (bullet == null) return;

            int tier = getTier();
            float finalDamage = bullet.damage;
            if (tier == 2) finalDamage *= 1.5f;

            if (Mathf.chance(getCritRate())) {
                finalDamage *= (1f + getCritDamageMultiplier());
                critEffect.at(bullet.x, bullet.y);
            }

            addStack();

            if (tier == 1 && Mathf.chance(0.20)) extraCritDamage += 0.01f;

            if (speedBuffTimer <= 0f && Mathf.chance(0.02)) {
                speedBuffTimer = 60f;
                speedBuffEffect.at(x, y);
            }

            if (tier > 0) {
                float normal = finalDamage * 0.5f, trueDmg = finalDamage * 0.5f;
                bullet.damage = normal;
                if (bullet.type != null) {
                    bullet.type.absorbable = false;
                    bullet.type.hittable = false;
                    bullet.type.reflectable = false;
                    bullet.type.pierceArmor = true;
                }
                activeBullets.add(new Tracked(bullet, trueDmg));
            } else {
                bullet.damage = finalDamage;
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (speedBuffTimer > 0f) speedBuffTimer -= Time.delta;

            if (speedBuffTimer > 0f && isShooting() && hasAmmo()) {
                reloadCounter += Time.delta * 5f * efficiency;
            }

            if (getTier() > 0 && !activeBullets.isEmpty()) {
                for (int i = activeBullets.size() - 1; i >= 0; i--) {
                    Tracked entry = activeBullets.get(i);
                    Bullet b = entry.bullet;
                    if (b == null || !b.isAdded() || b.lifetime <= 0f) {
                        activeBullets.remove(i);
                        continue;
                    }

                    float radius = (b.type != null ? b.type.hitSize : 8f) + 4f;
                    float trueDmg = entry.trueDmg;

                    Groups.unit.intersect(b.x - radius, b.y - radius, radius * 2f, radius * 2f, u -> {
                        if (u != null && u.isValid() && u.team != team && Mathf.dst(b.x, b.y, u.x, u.y) <= radius + u.hitSize) {
                            u.health -= trueDmg;
                            Fx.hitBulletSmall.at(u.x, u.y);
                            if (u.health <= 0f) u.kill();
                            b.remove();
                        }
                    });

                    if (b.isAdded()) {
                        Building tb = Vars.world.build(World.toTile(b.x), World.toTile(b.y));
                        if (tb != null && tb.team != team) {
                            tb.health -= trueDmg;
                            Fx.hitBulletSmall.at(tb.x, tb.y);
                            if (tb.health <= 0f) tb.kill();
                            b.remove();
                        }
                    }
                }
            }
        }

        @Override
        public void draw() {
            super.draw();
            if (speedBuffTimer > 0f) {
                Draw.color(Color.gold, Color.orange, Mathf.absin(Time.time, 4f, 1f));
                Lines.stroke(1.5f);
                Lines.circle(x, y, 10f);
                Draw.reset();
            }
        }

        private static String col(int have, int need) { return have >= need ? "[green]" : "[red]"; }

        private String requirementText() {
            CoreBuild core = team != null ? team.core() : null;
            if (core == null) return t("[red]Team Core not found![]", "[red]Không tìm thấy Lõi Đội![]");
            int th = core.items.get(Items.thorium), si = core.items.get(Items.silicon);
            int su = core.items.get(Items.surgeAlloy), ph = core.items.get(Items.phaseFabric);
            return t("[yellow]CORE RESOURCE REQUIREMENTS:[]\n[cyan]MK2 Branch:[]\n", "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh MK2:[]\n")
                + " • Thorium: " + col(th, REQ_MK2[0]) + th + "[] / " + REQ_MK2[0] + "\n"
                + " • Silicon: " + col(si, REQ_MK2[1]) + si + "[] / " + REQ_MK2[1] + "\n"
                + t("[purple]MK2B Branch:[]\n", "[purple]Nhánh MK2B:[]\n")
                + t(" • Surge Alloy: ", " • Kim loại Surge: ") + col(su, REQ_MK2B[0]) + su + "[] / " + REQ_MK2B[0] + "\n"
                + " • Silicon: " + col(si, REQ_MK2B[1]) + si + "[] / " + REQ_MK2B[1] + "\n"
                + t(" • Phase Fabric: ", " • Vải Phase: ") + col(ph, REQ_MK2B[2]) + ph + "[] / " + REQ_MK2B[2];
        }

        private void upgrade(BaseDialog dialog, int target) {
            CoreBuild core = team != null ? team.core() : null;
            boolean ok;
            if (target == 1) {
                ok = core != null && core.items.get(Items.thorium) >= REQ_MK2[0] && core.items.get(Items.silicon) >= REQ_MK2[1];
                if (ok) {
                    NexNet.take(core, Items.thorium, REQ_MK2[0]);
                    NexNet.take(core, Items.silicon, REQ_MK2[1]);
                    NexNet.fx(Fx.upgradeCore, x, y);
                }
            } else {
                ok = core != null && core.items.get(Items.surgeAlloy) >= REQ_MK2B[0]
                    && core.items.get(Items.silicon) >= REQ_MK2B[1] && core.items.get(Items.phaseFabric) >= REQ_MK2B[2];
                if (ok) {
                    NexNet.take(core, Items.surgeAlloy, REQ_MK2B[0]);
                    NexNet.take(core, Items.silicon, REQ_MK2B[1]);
                    NexNet.take(core, Items.phaseFabric, REQ_MK2B[2]);
                    NexNet.fx(Fx.bigShockwave, x, y);
                }
            }
            if (ok) {
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(4f, 4f, x, y);
                configure(Integer.valueOf(target));
                dialog.hide();
                deselect();
            } else {
                Vars.ui.showInfo(target == 1
                    ? t("[red]Not enough resources for MK2![]", "[red]Không đủ tài nguyên cho nhánh MK2![]")
                    : t("[red]Not enough resources for MK2B![]", "[red]Không đủ tài nguyên cho nhánh MK2B![]"));
            }
        }

        private Table branch(BaseDialog dialog, String title, String desc, String btn, int target) {
            Table b = new Table();
            b.background(Styles.black6);
            b.margin(12f);
            b.add(title).row();
            wrapLeft(b, desc, 340f);
            b.row();
            b.button(btn, () -> upgrade(dialog, target)).size(180f, 38f);
            return b;
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(t("Buffetles Upgrade Center", "Trung tâm nâng cấp pháo Buffetles"));
            dialog.cont.add(new Label(requirementText())).growX();
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branches = new Table();
            Table b1 = branch(dialog,
                t("[cyan]===(MK2 - SUPER SPEED CRIT)===[]", "[cyan]===(MK2 - BẠO KÍCH SIÊU TỐC)===[]"),
                t("[white]• Crit chance gain/stack: [green]+0.5%[] (base 0.1%)\n• TRUE DAMAGE: [scarlet]50% True Damage (Bypasses armor, shield, Vela mechanism)[]\n• [orange]Special Ability:[] Accelerates crit chance stacking speed by 5x\n• On shoot, 20% chance to permanently gain +1% crit damage. 2% chance to gain 500% fire rate for 1s",
                  "[white]• Tăng tỉ lệ bạo kích/stack: [green]+0.5%[] (gốc 0.1%)\n• SÁT THƯƠNG CHUẨN: [scarlet]50% True Damage (Xuyên giáp, khiên, cơ chế Vela)[]\n• [orange]Kỹ năng đặc biệt:[] Tăng tốc tích lũy tỉ lệ bạo kích gấp 5 lần\n• Khi bắn đạn có 20% tỉ lệ tăng vĩnh viễn +1% sát thương bạo kích\n• Khi bắn có 2% tỉ lệ tăng 500% tốc bắn trong 1s"),
                t("[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), 1);
            Table b2 = branch(dialog,
                t("[purple]===(MK2B - DAMAGE MULTIPLIER)===[]", "[purple]===(MK2B - BỘI PHÁT SÁT THƯƠNG)===[]"),
                t("[white]• Base bullet damage: [green]+50.0%[]\n• TRUE DAMAGE: [scarlet]50% True Damage (Bypasses armor, shield, Vela mechanism)[]\n• [orange]Special Ability:[] Maintains maximum crit mechanism and 2% chance to gain 500% fire rate for 1s on shot",
                  "[white]• Sát thương gốc của đạn: [green]+50.0%[]\n• SÁT THƯƠNG CHUẨN: [scarlet]50% True Damage (Xuyên giáp, khiên, cơ chế Vela)[]\n• [orange]Kỹ năng đặc biệt:[] Duy trì cơ chế bạo kích cực đại và khi bắn có 2% cơ hội tăng 500% tốc bắn trong 1s"),
                t("[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), 2);

            branches.add(b1).width(340f).row();
            branches.add().height(12f).row();
            branches.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = t(" Buffetles Stats: ", " Thông số pháo Buffetles: ");
            String desc = "";
            int tier = getTier();
            String cd = pct(getCritDamageMultiplier() * 100f);

            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = t("[gold]⚡ BASE STATS (MK1) ⚡[]\n[lightgray]Starting Crit Chance:[] [orange]5.0%[]\n[lightgray]Stack Gain:[] [white]+0.1% / stack[]\n[lightgray]Crit Damage:[] [yellow]+" + cd + "%[]\n\n[sky]⚡ STACKING MECHANISM:[]\n• Every shot increases crit stacks.\n• On shot: 2% chance to gain +500% fire rate for 1 second.\n• When crit rate reaches 100%, stacks reset and grants +15% permanent crit damage.",
                         "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n[lightgray]Tỉ lệ bạo kích khởi điểm:[] [orange]5.0%[]\n[lightgray]Mức tích lũy:[] [white]+0.1% / stack[]\n[lightgray]Sát thương bạo kích:[] [yellow]+" + cd + "%[]\n\n[sky]⚡ CƠ CHẾ DỒN ĐẠP (STACKS):[]\n• Mỗi lần bắn sẽ tăng stack bạo kích.\n• Khi phát bắn diễn ra: 2% cơ hội tăng +500% tốc bắn trong 1 giây.\n• Khi tỉ lệ bạo kích đạt 100%, reset stack và cộng thêm 15% sát thương bạo kích vĩnh viễn.");
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = t("[cyan]⚡ BASE STATS (MK2) ⚡[]\n[lightgray]Damage Type:[] [scarlet]50% True Damage (Pierces Shield, Armor, Vela)[]\n[lightgray]Crit Stack Rate:[] [lime]+0.5% / stack (5x speed)[]\n[lightgray]Current Crit Damage:[] [yellow]+" + cd + "%[]\n\n[lime]⚡ MK2 SPECIAL MECHANISM:[]\n• On shot: [yellow]20% chance[] to add [green]+1% permanent crit damage[].\n• On shot: [gold]2% chance[] to activate [orange]+500% fire rate[] for 1 second.",
                         "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n[lightgray]Loại Sát Thương:[] [scarlet]50% Sát thương Chuẩn (Xuyên Vela Shield, Giáp, Khiên)[]\n[lightgray]Mức tích lũy bạo kích:[] [lime]+0.5% / stack (x5 tốc độ)[]\n[lightgray]Sát thương bạo kích hiện tại:[] [yellow]+" + cd + "%[]\n\n[lime]⚡ CƠ CHẾ ĐẶC BIỆT MK2:[]\n• Khi bắn có [yellow]20% tỉ lệ[] tăng thêm [green]+1% sát thương bạo kích[] vĩnh viễn.\n• Khi phát bắn diễn ra: [gold]2% cơ hội[] kích hoạt [orange]+500% tốc bắn[] trong 1 giây.");
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = t("[purple]⚡ BASE STATS (MK2B) ⚡[]\n[lightgray]Damage Type:[] [scarlet]50% True Damage (Pierces Shield, Armor, Vela)[]\n[lightgray]Base Bullet DMG:[] [lime]+50.0% DMG[]\n\n[purple]🔥 MK2B SPECIAL MECHANISM:[]\n• On shot: [gold]2% chance[] to gain [orange]+500% fire rate[] for 1 second.",
                         "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n[lightgray]Loại Sát Thương:[] [scarlet]50% Sát thương Chuẩn (Xuyên Vela Shield, Giáp, Khiên)[]\n[lightgray]Sát thương gốc đạn:[] [lime]+50.0% DMG[]\n\n[purple]🔥 CƠ CHẾ ĐẶC BIỆT MK2B:[]\n• Khi phát bắn diễn ra: [gold]2% tỉ lệ[] tăng [orange]+500% tốc bắn[] trong 1 giây.");
            }

            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            wrapLeft(info, desc, 360f);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            if (table == null) return;
            table.clear();
            table.row();

            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog)
                    .size(50f, 40f).tooltip(t("Upgrade Buffetles System", "Nâng cấp hệ thống Buffetles"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40f, () -> Vars.ui.showInfo(
                    t("[scarlet]BUFFETLES SYSTEM HAS REACHED MAX EVOLUTION LEVEL![]", "[scarlet]HỆ THỐNG BUFFETLES ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
                    .size(50f, 40f).tooltip(t("Max level reached", "Đã đạt cấp tối đa"));
            }

            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog)
                .size(50f, 40f).tooltip(t("View Detailed System Stats", "Xem thông số chi tiết hệ thống"));
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(critStacks);
            write.f(extraCritDamage);
            write.i(getTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            critStacks = read.i();
            extraCritDamage = read.f();
            tierState = read.i();
        }
    }
}
