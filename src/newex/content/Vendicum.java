package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.PerfCore;

/** Chuyển từ vendicum.js. Pháo năng lượng: càng đầy điện sát thương/tốc bắn càng lớn, mỗi phát tiêu hao điện; nâng cấp MK2 (xuyên) / MK2B (tự dẫn). */
public final class Vendicum {
    private Vendicum() {}

    private static final int[] REQ_MK2 = {500, 300, 0};    // titan, silicon, plastanium
    private static final int[] REQ_MK2B = {800, 400, 200};

    private static final float[] LOSS = {0.01f, 0.005f, 0.003f};
    private static final float[] REGEN = {0.20f, 0.34f, 0.40f};

    private static BulletType silicon1, silicon2, silicon2b, copper1, copper2, copper2b;
    public static ItemTurret vendicum;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }

    private static void wrap(arc.scene.ui.layout.Cell<arc.scene.ui.Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    /** pierceCap = 0 nghĩa là không xuyên. */
    private static BasicBulletType bullet(float speed, float dmg, float life, float w, float h, String back,
                                          int pierceCap, float knock, float trailChance, String trailColor, boolean homing) {
        BasicBulletType b = new BasicBulletType(speed, dmg);
        b.lifetime = life;
        b.width = w;
        b.height = h;
        b.frontColor = Color.white;
        b.backColor = Color.valueOf(back);
        if (pierceCap > 0) { b.pierce = true; b.pierceCap = pierceCap; b.pierceBuilding = true; }
        else { b.pierce = false; b.pierceBuilding = false; }
        b.knockback = knock;
        b.impact = true;
        b.hitEffect = Fx.disperseTrail;
        b.despawnEffect = Fx.disperseTrail;
        b.trailEffect = Fx.none;
        b.trailChance = trailChance;
        if (trailColor != null) b.trailColor = Color.valueOf(trailColor);
        if (homing) { b.homingPower = 0.15f; b.homingRange = 200f; }
        return b;
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        silicon1 = bullet(8f, 67.5f, 48f, 11f, 16f, "#e0b080", 3, 1f, 0.2f, null, false);
        silicon2 = bullet(10f, 97.5f, 45f, 13f, 20f, "#ffaa66", 5, 1.4f, 0.4f, null, false);
        silicon2b = bullet(9f, 183.75f, 50f, 5f, 64f, "#831006", 0, 2.8f, 0.4f, "#ff2525", true);
        copper1 = bullet(8f, 33.75f, 48f, 11f, 16f, "#d99d73", 3, 1f, 0.2f, null, false);
        copper2 = bullet(10f, 48.75f, 45f, 13f, 20f, "#d99d73", 5, 1.4f, 0.4f, null, false);
        copper2b = bullet(9f, 91.875f, 50f, 5f, 64f, "#b85b37", 0, 2.8f, 0.4f, "#d99d73", true);

        vendicum = new ItemTurret("vendicum");
        vendicum.configurable = true;
        vendicum.category = Category.turret;

        vendicum.ammoTypes.put(Items.silicon, silicon1);
        vendicum.ammoTypes.put(Items.copper, copper1);

        vendicum.addBar("dmg_bonus", (Building e) -> {
            VendicumBuild b = (VendicumBuild) e;
            return new Bar(
                () -> "DMG: +" + Mathf.floor((b.getDmgRatio() * 10f + b.stackDmgBonus) * 100f) + "%",
                () -> Color.orange,
                () -> Math.min(1f, (b.getDmgRatio() * 10f + b.stackDmgBonus) / 19.99f));
        });

        vendicum.addBar("as_bonus", (Building e) -> {
            VendicumBuild b = (VendicumBuild) e;
            return new Bar(
                () -> {
                    int v = Mathf.floor(b.getAsRatio() * 250f);
                    return "AS: " + (v >= 0 ? "+" : "") + v + "%";
                },
                () -> Color.cyan,
                () -> Math.max(b.getAsRatio(), 0f));
        });

        vendicum.config(Integer.class, (Building tile, Integer value) -> {
            if (!(tile instanceof VendicumBuild)) return;
            VendicumBuild b = (VendicumBuild) tile;
            if (value == 1 || value == 2) b.tryUpgrade(value);
            else b.setTier(value);
        });

        vendicum.buildType = () -> new VendicumBuild(vendicum);
    }

    public static class VendicumBuild extends ItemTurret.ItemTurretBuild {
        final ItemTurret blockRef;
        float energyState = 1f, customRecoil = 0f, stackDmgBonus = 0f;
        int tierState = 0, paidTitanium = 0, paidSilicon = 0, paidPlastanium = 0;

        public VendicumBuild(ItemTurret outer) { outer.super(); this.blockRef = outer; }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            paidTitanium = 0; paidSilicon = 0; paidPlastanium = 0;
            if (val == 0) health = 5500f;
            if (val == 1) health = 8250f;
            if (val == 2) health = 7333f;
            maxHealth = health;
        }

        public float getDmgRatio() { return energyState; }
        public float getAsRatio() { return energyState; }

        @Override
        public BulletType peekAmmo() {
            int tier = getTier();
            boolean isCopper = false;
            if (hasAmmo()) {
                ItemTurret.ItemEntry entry = (ItemTurret.ItemEntry) ammo.peek();
                if (entry != null && entry.item == Items.copper) isCopper = true;
            }
            if (tier == 1) return isCopper ? copper2 : silicon2;
            if (tier == 2) return isCopper ? copper2b : silicon2b;
            return isCopper ? copper1 : silicon1;
        }

        public boolean tryUpgrade(int targetTier) {
            if (getTier() != 0) return false;
            int[] req = targetTier == 1 ? REQ_MK2 : (targetTier == 2 ? REQ_MK2B : null);
            if (req == null) return false;
            Building core = team.core();
            if (core == null) return false;

            int coreT = core.items.get(Items.titanium), coreS = core.items.get(Items.silicon), coreP = core.items.get(Items.plastanium);
            if (coreT >= req[0] && coreS >= req[1] && coreP >= req[2]) {
                if (req[0] > 0) core.items.remove(Items.titanium, req[0]);
                if (req[1] > 0) core.items.remove(Items.silicon, req[1]);
                if (req[2] > 0) core.items.remove(Items.plastanium, req[2]);
                setTier(targetTier);
                if (targetTier == 1) Fx.upgradeCore.at(x, y); else Fx.bigShockwave.at(x, y);
                Fx.mineHuge.at(x, y);
                Effect.shake(4f, 4f, x, y);
                return true;
            }
            if (!Vars.headless && Vars.player != null) {
                Vars.ui.showInfo(t(en(), "[scarlet]Not enough resources in core![]", "[scarlet]Không đủ tài nguyên trong lõi![]"));
            }
            return false;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 1) return 538.1f;
            if (tier == 2) return 461.2f;
            return 410f;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            float regen = REGEN[getTier()];
            if (!isShooting() || !hasAmmo() || !isActive()) {
                if (energyState < 1f) energyState = Math.min(energyState + (regen * Time.delta / 60f), 1f);
            }
            customRecoil = Mathf.approach(customRecoil, 0f, 0.12f * Time.delta);
        }

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            energyState = Math.max(energyState - LOSS[getTier()], 0f);
            if (energyState <= 0f) {
                stackDmgBonus = Math.min(stackDmgBonus + 0.09f, 9.99f);
                energyState = 1f;
            }
            customRecoil = 1f;
        }

        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            if (bullet != null) {
                float mul = 1f + energyState * 10f + stackDmgBonus;
                bullet.damage = bullet.type.damage * mul;
                bullet.data = this;
            }
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
        }

        @Override
        public float baseReloadSpeed() { return efficiency * (1f + energyState * 2.5f); }

        @Override
        public void draw() {
            String modName = block.name.split("-")[0];
            TextureRegion baseRegion = PerfCore.region("reinforced-" + block.size);
            if (baseRegion.found()) Draw.rect(baseRegion, x, y);
            else super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
            float side = (1f - energyState) * 4f;

            TextureRegion barrel1 = PerfCore.region(modName + "-vendicum-barrel1");
            if (barrel1.found()) Draw.rect(barrel1, x - side * sin, y + side * cos, rotation);

            TextureRegion barrel2 = PerfCore.region(modName + "-vendicum-barrel2");
            if (barrel2.found()) Draw.rect(barrel2, x + side * sin, y - side * cos, rotation);

            float off = customRecoil * -5f;
            TextureRegion b1 = PerfCore.region(modName + "-vendicum-b1");
            if (b1.found()) Draw.rect(b1, x + off * cos, y + off * sin, rotation);
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.i(paidTitanium);
            write.i(paidSilicon);
            write.i(paidPlastanium);
            write.f(energyState);
            write.f(stackDmgBonus);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            paidTitanium = read.i();
            paidSilicon = read.i();
            paidPlastanium = read.i();
            if (revision >= 1) energyState = read.f();
            if (revision >= 2) stackDmgBonus = read.f(); else stackDmgBonus = 0f;
            customRecoil = 0f;
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade Vendicum system", "Nâng cấp hệ thống Vendicum"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]VENDICUM HAS REACHED MAX EVOLUTION LEVEL![]",
                    "[scarlet]HỆ THỐNG VENDICUM ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Max level reached", "Đã đạt cấp tối đa"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(t(isEn, "View detailed stats", "Xem thông số chi tiết hệ thống"));
        }

        private String c(int have, int need) { return (have >= need ? "[green]" : "[scarlet]") + have + "[] / " + need; }

        private void doUpgrade(int tier) {
            if (Vars.net.active()) Call.tileConfig(Vars.player, this, Integer.valueOf(tier));
            else tryUpgrade(tier);
        }

        private void openUpgrade(boolean isEn) {
            Building core = team.core();
            int aT = core != null ? core.items.get(Items.titanium) : 0;
            int aS = core != null ? core.items.get(Items.silicon) : 0;
            int aP = core != null ? core.items.get(Items.plastanium) : 0;

            BaseDialog dialog = new BaseDialog(t(isEn, "Vendicum Upgrade Center", "Trung tâm nâng cấp pháo Vendicum"));

            String reqStr = isEn
                ? "[yellow]CORE RESOURCES / REQUIRED FOR UPGRADE:[]\n[cyan]MK2 Branch:[]\n • Titanium: " + c(aT, REQ_MK2[0]) + "\n • Silicon: " + c(aS, REQ_MK2[1])
                    + "\n[purple]MK2B Branch:[]\n • Titanium: " + c(aT, REQ_MK2B[0]) + "\n • Silicon: " + c(aS, REQ_MK2B[1]) + "\n • Plastanium: " + c(aP, REQ_MK2B[2])
                : "[yellow]TÀI NGUYÊN TRONG LÕI / CẦN NÂNG CẤP:[]\n[cyan]Nhánh MK2:[]\n • Titan: " + c(aT, REQ_MK2[0]) + "\n • Silicon: " + c(aS, REQ_MK2[1])
                    + "\n[purple]Nhánh MK2B:[]\n • Titan: " + c(aT, REQ_MK2B[0]) + "\n • Silicon: " + c(aS, REQ_MK2B[1]) + "\n • Nhựa Plastanium: " + c(aP, REQ_MK2B[2]);
            wrap(dialog.cont.add(reqStr), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add("[cyan]===(MK2 - PENETRATION)===[]").row();
            wrap(b1.add(t(isEn,
                "[white]• Health: [green]+50%[] (8,250 HP)\n• Range: [green]+31.25%[] (538.1 px)\n• Base Damage: [green]+44.4%[] (97.5 DMG)\n• [orange]Penetrating Magnetic Acceleration:[] Expands pierce cap to 5 enemy targets or structures\n• Built-in supercharge circuit reduces full energy recharge time to 3.0s",
                "[white]• Máu cấu trúc: [green]+50%[] (8,250 HP)\n• Tầm bắn: [green]+31.25%[] (538.1 px)\n• Sát thương gốc: [green]+44.4%[] (97.5 DMG)\n• [orange]Gia Tốc Từ Tính Xuyên Thấu:[] Đạn mở rộng khả năng xuyên qua tối đa 5 mục tiêu kẻ địch hoặc công trình\n• Tích hợp mạch siêu sạc giúp rút ngắn thời gian hồi đầy năng lượng xuống 3.0 giây")), 340);
            b1.row();
            b1.button(t(isEn, "[green]UPGRADE MK2[]", "[green]NÂNG CẤP MK2[]"), () -> { doUpgrade(1); dialog.hide(); deselect(); }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add("[purple]===(MK2B - HOMING)===[]").row();
            wrap(b2.add(t(isEn,
                "[white]• Health: [green]+33.3%[] (7,333 HP)\n• Range: [green]+12.5%[] (461.2 px)\n• Base Damage: [green]+172.2%[] (183.75 DMG)\n• [orange]Homing Heat-Seeking Impulse:[] Removes piercing to integrate smart sensors that automatically steer towards nearby targets\n• Saving 70% energy per shot",
                "[white]• Máu cấu trúc: [green]+33.3%[] (7,333 HP)\n• Tầm bắn: [green]+12.5%[] (461.2 px)\n• Sát thương gốc: [green]+172.2%[] (183.75 DMG)\n• [orange]Xung Kích Tầm Nhiệt Truy Đuổi:[] Loại bỏ xuyên thấu để tích hợp chip cảm biến thông minh tự động bẻ lái truy đuổi mục tiêu xung quanh\n• Tiết kiệm 70% năng lượng mỗi phát bắn")), 340);
            b2.row();
            b2.button(t(isEn, "[orange]UPGRADE MK2B[]", "[orange]NÂNG CẤP MK2B[]"), () -> { doUpgrade(2); dialog.hide(); deselect(); }).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = t(isEn, " Vendicum Turret Stats: ", " Thông số pháo Vendicum: ");
            String desc = "";
            int tier = getTier();
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = isEn
                    ? "[gold]⚡ BASE STATS (MK1) ⚡[]\n[lightgray]Turret HP:[] [green]5,500[]\n[lightgray]Effective Range:[] [orange]410 px[]\n[lightgray]Base Damage (Silicon):[] [yellow]67.50 DMG[]\n[lightgray]Base Damage (Copper):[] [orange]33.75 DMG[]\n[lightgray]Penetration:[] [white]3 targets[]\n\n[sky]⚡ CONSUMPTION MECHANIC:[]\n• [lightgray]Energy Loss:[] Each shot consumes [red]1.0%[] stored core energy. Damage scales directly with current energy.\n• [lightgray]Recharge Speed:[] Takes about [yellow]5.0s[] to recharge from 0% to 100% when idle."
                    : "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]5,500[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]410 pixel[]\n[lightgray]Sát thương Silicon:[] [yellow]67.50 DMG[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]33.75 DMG[]\n[lightgray]Khả năng xuyên thấu:[] [white]3 mục tiêu[]\n\n[sky]⚡ CƠ CHẾ NĂNG LƯỢNG TIÊU HAO:[]\n• [lightgray]Tiêu hao (Energy Loss):[] Mỗi phát bắn làm tiêu trừ [red]1.0%[] năng lượng tích lũy của lõi. Sát thương đầu ra tỷ lệ thuận với lượng điện tích hiện có.\n• [lightgray]Tốc độ tái nạp:[] Mất khoảng [yellow]5.0 giây[] để nạp đầy lại từ thanh trống (0% -> 100%) khi ngừng khai hỏa.";
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = isEn
                    ? "[cyan]⚡ BASE STATS (MK2) ⚡[]\n[lightgray]Turret HP:[] [green]8,250 [lime](+50%)[]\n[lightgray]Effective Range:[] [orange]538.1 px [lime](+31.2%)[]\n[lightgray]Base Damage (Silicon):[] [yellow]97.50 DMG [lime](+44.4%)[]\n[lightgray]Base Damage (Copper):[] [orange]48.75 DMG[]\n[lightgray]Penetration:[] [yellow]5 targets [lime](+2 targets)[]\n\n[lime]⚡ CONSUMPTION MECHANIC:[]\n• [lightgray]Consumption Optimization:[] Reduces energy loss down to [red]0.5%[] per shot (-50%).\n• [lightgray]Jet Supercharge:[] Recharge speed significantly increased, taking only [green]3.0s[] to fully recharge."
                    : "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]8,250 [lime](+50%)[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]538.1 pixel [lime](+31.2%)[]\n[lightgray]Sát thương Silicon:[] [yellow]97.50 DMG [lime](+44.4%)[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]48.75 DMG[]\n[lightgray]Khả năng xuyên thấu:[] [yellow]5 mục tiêu [lime](+2 mục tiêu)[]\n\n[lime]⚡ CƠ CHẾ NĂNG LƯỢNG TIÊU HAO:[]\n• [lightgray]Tối ưu tiêu hao:[] Giảm thiểu mức tiêu hao năng lượng xuống chỉ còn [red]0.5%[] cho mỗi phát bắn (Giảm -50%).\n• [lightgray]Siêu sạc phản lực:[] Tốc độ nạp năng lượng đẩy mạnh vượt trội, chỉ mất [green]3.0 giây[] để hồi đầy bình tích lũy.";
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = isEn
                    ? "[purple]⚡ BASE STATS (MK2B) ⚡[]\n[lightgray]Turret HP:[] [green]7,333 [lime](+33.3%)[]\n[lightgray]Effective Range:[] [orange]461.2 px [lime](+12.5%)[]\n[lightgray]Base Damage (Silicon):[] [red]183.75 DMG (+172.2%)[]\n[lightgray]Base Damage (Copper):[] [orange]91.875 DMG[]\n[lightgray]Penetration:[] [red]None (Lost piercing)[]\n\n[purple]🔥 CONSUMPTION MECHANIC:[]\n• [lightgray]Homing Circuit:[] Replaces pierce with homing sensors, bullets [pink]automatically steer toward targets[] within 200px.\n• [lightgray]High Stability:[] Energy loss per shot reduced to [green]0.3%[] for extremely stable sustained fire."
                    : "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]7,333 [lime](+33.3%)[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]461.2 pixel [lime](+12.5%)[]\n[lightgray]Sát thương Silicon:[] [red]183.75 DMG (+172.2%)[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]91.875 DMG[]\n[lightgray]Khả năng xuyên thấu:[] [red]Không (Mất khả năng xuyên)[]\n\n[purple]🔥 CƠ CHẾ NĂNG LƯỢNG TIÊU HAO:[]\n• [lightgray]Mạch định vị:[] Đổi khả năng xuyên lấy cảm biến tích hợp, đạn [pink]tự động bẻ lái tìm mục tiêu[] trong phạm vi 200 pixel.\n• [lightgray]Mức ổn định cao:[] Năng lượng tiêu hao mỗi phát bắn giảm cực hạn xuống còn [green]0.3%[] giúp duy trì hỏa lực cực kỳ ổn định và lâu dài.";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }
}
