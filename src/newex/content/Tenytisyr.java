package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.Label;
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
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.PerfCore;

/**
 * Chuyển từ tenytisyr.js. Pháo "càng bắn càng nóng": tốc bắn khởi đầu +400% giảm dần, sát thương cộng dồn tới +5000%;
 * nâng cấp MK2 (xuyên + 1% máu tối đa khi đầy nhiệt) hoặc MK2B (tự dẫn + nổ diện rộng 0,5% máu tối đa).
 */
public final class Tenytisyr {
    private Tenytisyr() {}

    private static final int[] REQ_MK2 = {500, 300, 0};    // titan, silicon, plastanium
    private static final int[] REQ_MK2B = {800, 400, 200};
    private static final float MAX_STACK = 50f;
    private static final float EXPLOSION_RADIUS = 280f;

    private static BulletType silicon1, silicon2, silicon2b, copper1, copper2, copper2b;
    public static ItemTurret tenytisyr;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    /**
     * Hiệu ứng nổ MK2B. JS gốc có dòng {@code Lines.circle(ringColors[i])} (truyền Color vào Lines.circle,
     * không có overload này) — Rhino sẽ ném lỗi khi vẽ; bản Java bỏ dòng đó (xem MIGRATION bài học 10).
     */
    private static final Effect explosionFx = new Effect(50f, e -> {
        Draw.z(Layer.effect + 0.1f);
        float alpha = 1f - e.fin();
        Color col = Color.valueOf("#ff3300");

        Draw.color(col);
        Draw.alpha(alpha * 0.35f);
        Fill.circle(e.x, e.y, EXPLOSION_RADIUS);

        Draw.color(col);
        Draw.alpha(alpha * 0.7f);
        Lines.stroke(2.5f * alpha);
        Lines.circle(e.x, e.y, EXPLOSION_RADIUS);

        Color[] ring = {Color.valueOf("#ffffff"), Color.valueOf("#ffcc00"), col};
        for (int i = 0; i < 3; i++) {
            float delay = i * 0.12f;
            if (e.fin() > delay) {
                float progress = (e.fin() - delay) / (1f - delay);
                float smooth = Interp.pow3Out.apply(progress);
                Draw.color(ring[i]);
                Draw.alpha(alpha * (1f - smooth));
                Lines.stroke((14f - i * 3f) * (1f - smooth));
                Lines.circle(e.x, e.y, EXPLOSION_RADIUS * smooth);
            }
        }
        Draw.reset();
    });

    /** Đạn có hiệu ứng khi đầy nhiệt: MK2 gây thêm 1% máu tối đa, MK2B nổ diện rộng. */
    private static final class HeatBullet extends BasicBulletType {
        HeatBullet(float speed, float dmg) { super(speed, dmg); }

        @Override
        public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other == null || b == null || !(b.owner instanceof TenytisyrBuild)) return;

            TenytisyrBuild turret = (TenytisyrBuild) b.owner;
            int tier = turret.getTier();
            boolean maxCharged = turret.stackDmgBonus >= MAX_STACK;
            if (!maxCharged) return;

            if (tier == 1 && other instanceof Healthc) {
                Healthc h = (Healthc) other;
                h.damage(h.maxHealth() * 0.01f);
            }

            if (tier == 2) {
                final float exX = other.getX(), exY = other.getY();
                explosionFx.at(exX, exY);
                Effect.shake(6f, 6f, exX, exY);
                Groups.unit.intersect(exX - EXPLOSION_RADIUS, exY - EXPLOSION_RADIUS, EXPLOSION_RADIUS * 2f, EXPLOSION_RADIUS * 2f, u -> {
                    if (u != null && u.isValid() && u.within(exX, exY, EXPLOSION_RADIUS) && u.team != turret.team) {
                        u.damage(u.maxHealth * 0.005f);
                    }
                });
            }
        }
    }

    /** pierceCap = 0 nghĩa là không xuyên. */
    private static BulletType bullet(float speed, float dmg, float life, int pierceCap, float knock,
                                     float trailChance, String trailColor, boolean homing) {
        BasicBulletType b = new HeatBullet(speed, dmg);
        b.lifetime = life;
        b.width = 6f;
        b.height = 20f;
        b.frontColor = Color.white;
        b.backColor = Color.valueOf("#0ee05f");
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

    /** Gọi từ NexMod.loadContent(). Các thuộc tính còn lại (shoot, drawer, unitSort...) do tenytisyr.hjson đặt. */
    public static void create() {
        silicon1 = bullet(8f, 67.5f, 48f, 3, 1f, 0.2f, null, false);
        silicon2 = bullet(10f, 97.5f, 45f, 5, 1.4f, 0.4f, null, false);
        silicon2b = bullet(9f, 183.75f, 50f, 0, 2.8f, 0.4f, "#80e0a5", true);
        copper1 = bullet(8f, 33.75f, 48f, 3, 1f, 0.2f, null, false);
        copper2 = bullet(10f, 48.75f, 45f, 5, 1.4f, 0.4f, null, false);
        copper2b = bullet(9f, 91.875f, 50f, 0, 2.8f, 0.4f, "#80e0a5", true);

        tenytisyr = new ItemTurret("tenytisyr");
        tenytisyr.configurable = true;
        tenytisyr.category = Category.turret;

        tenytisyr.ammoTypes.put(Items.silicon, silicon1);
        tenytisyr.ammoTypes.put(Items.copper, copper1);

        tenytisyr.addBar("dmg_bonus", (Building e) -> {
            TenytisyrBuild b = (TenytisyrBuild) e;
            return new Bar(
                () -> "DMG: +" + Mathf.floor((b.getDmgRatio() * 5f + b.stackDmgBonus) * 100f) + "%",
                () -> Color.orange,
                () -> Math.min(1f, (b.getDmgRatio() * 5f + b.stackDmgBonus) / 14.99f));
        });

        tenytisyr.addBar("as_bonus", (Building e) -> {
            TenytisyrBuild b = (TenytisyrBuild) e;
            return new Bar(
                () -> {
                    int v = Mathf.floor(b.getAsRatio() * 250f);
                    return "AS: " + (v >= 0 ? "+" : "") + v + "%";
                },
                () -> Color.cyan,
                () -> Math.max(b.getAsRatio(), 0f));
        });

        tenytisyr.config(Integer.class, (Building tile, Integer value) -> {
            if (!(tile instanceof TenytisyrBuild)) return;
            TenytisyrBuild b = (TenytisyrBuild) tile;
            if (value == 1 || value == 2) b.tryUpgrade(value);
            else b.setTier(value);
        });

        tenytisyr.buildType = () -> new TenytisyrBuild(tenytisyr);
    }

    public static class TenytisyrBuild extends ItemTurret.ItemTurretBuild {
        float energyState = 1f, customRecoil = 0f;
        /** Public: các hệ thống khác có thể đọc. */
        public float stackDmgBonus = 0f;
        int tierState = 0, paidTitanium = 0, paidSilicon = 0, paidPlastanium = 0;

        public TenytisyrBuild(ItemTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            paidTitanium = 0; paidSilicon = 0; paidPlastanium = 0;
            if (val == 0) health = 3000f;
            if (val == 1) health = 4500f;
            if (val == 2) health = 4000f;
            maxHealth = health;
        }

        /** Giữ đúng JS gốc: getDmgRatio trả về stackDmgBonus (không phải energyState). */
        public float getDmgRatio() { return stackDmgBonus; }
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
            if (tier == 1) return 420f;
            if (tier == 2) return 360f;
            return 320f;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            // JS gốc viết `this.isShooting` (không có ngoặc) nên luôn truthy; bản Java gọi hàm thật (bài học 10).
            if (isShooting() && hasAmmo() && isActive()) {
                energyState = Math.max(0f, energyState - (0.05f * Time.delta / 60f));
                stackDmgBonus = Math.min(MAX_STACK, stackDmgBonus + (0.50f * Time.delta / 60f));
            } else {
                if (energyState < 1f) energyState = Math.min(1f, energyState + (0.20f * Time.delta / 60f));
                if (stackDmgBonus > 0f) stackDmgBonus = Math.max(0f, stackDmgBonus - (0.50f * Time.delta / 60f));
            }

            customRecoil = Mathf.approach(customRecoil, 0f, 0.12f * Time.delta);
        }

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            customRecoil = 1f;
        }

        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            if (bullet != null) {
                bullet.damage = bullet.type.damage * (1f + stackDmgBonus);
                bullet.owner = this;
            }
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
        }

        @Override
        public float baseReloadSpeed() { return efficiency * (1f + energyState * 4f); }

        @Override
        public void draw() {
            // JS gốc: PerfCore.region(block.basePrefix + size) — Turret v157 không có basePrefix nên region không bao giờ tìm thấy
            // và luôn rơi vào super.draw() (bài học 1). Gọi thẳng super.draw() cho đúng hành vi thực tế.
            super.draw();

            String modName = block.name.split("-")[0];
            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
            float side = energyState * 4f;

            TextureRegion barrel1 = PerfCore.region(modName + "-tenytisyr-barrel1");
            if (barrel1.found()) Draw.rect(barrel1, x - side * sin, y + side * cos, rotation);

            TextureRegion barrel2 = PerfCore.region(modName + "-tenytisyr-barrel2");
            if (barrel2.found()) Draw.rect(barrel2, x + side * sin, y - side * cos, rotation);

            float off = customRecoil * -5f;
            TextureRegion b1 = PerfCore.region(modName + "-tenytisyr-b1");
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
                    .size(50, 40).tooltip(t(isEn, "Upgrade tenytisyr system", "Nâng cấp hệ thống tenytisyr"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]tenytisyr HAS REACHED MAX EVOLUTION LEVEL![]",
                    "[scarlet]HỆ THỐNG tenytisyr ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
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

            BaseDialog dialog = new BaseDialog(t(isEn, "tenytisyr Upgrade Center", "Trung tâm nâng cấp pháo tenytisyr"));

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
                "[white]• Health: [green]+50%[] (4,500 HP)\n• Range: [green]+31.25%[] (420 px)\n• Base Damage: [green]+44.4%[] (97.5 DMG)\n• [orange]Full Heat Empowerment:[] Reaching max stack adds +1% Max HP true damage per bullet",
                "[white]• Máu cấu trúc: [green]+50%[] (4,500 HP)\n• Tầm bắn: [green]+31.25%[] (420 px)\n• Sát thương gốc: [green]+44.4%[] (97.5 DMG)\n• [orange]Nội tại nâng cấp:[] Tích đủ tần giới hạn\n• Đạn bắn ra sẽ gây thêm 1% Max HP của mục tiêu")), 340);
            b1.row();
            b1.button(t(isEn, "[green]UPGRADE MK2[]", "[green]NÂNG CẤP MK2[]"), () -> { doUpgrade(1); dialog.hide(); deselect(); }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add("[purple]===(MK2B - HOMING)===[]").row();
            wrap(b2.add(t(isEn,
                "[white]• Health: [green]+33.3%[] (4,000 HP)\n• Range: [green]+12.5%[] (360 px)\n• Base Damage: [green]+172.2%[] (183.75 DMG)\n• [orange]Full Heat Blast:[] Reaching max stack causes bullet hits to trigger a 35-tile explosion dealing 0.5% Max HP",
                "[white]• Máu cấu trúc: [green]+33.3%[] (4,000 HP)\n• Tầm bắn: [green]+12.5%[] (360 px)\n• Sát thương gốc: [green]+172.2%[] (183.75 DMG)\n• [orange]Nội tại nâng cấp:[] Tích đủ tần giới hạn\n• Đạn bắn ra nổ phạm vi 35 ô với 0.5% Max HP")), 340);
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
            String title = t(isEn, " tenytisyr Turret Stats: ", " Thông số pháo tenytisyr: ");
            String desc = "";
            int tier = getTier();
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = isEn
                    ? "[gold]⚡ BASE STATS (MK1) ⚡[]\n[lightgray]Turret HP:[] [green]3,000[]\n[lightgray]Effective Range:[] [orange]320 px[]\n[lightgray]Base Damage (Silicon):[] [yellow]67.50 DMG[]\n[lightgray]Base Damage (Copper):[] [orange]33.75 DMG[]\n[lightgray]Penetration:[] [white]3 targets[]\n\n[sky]⚡ MECHANIC:[]\n• Starts at 500% Attack Speed, decreasing by 5%/s while firing.\n• Damage starts at 0% bonus and increases by 50%/s up to 5000%."
                    : "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]3,000[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]320 pixel[]\n[lightgray]Sát thương Silicon:[] [yellow]67.50 DMG[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]33.75 DMG[]\n[lightgray]Khả năng xuyên thấu:[] [white]3 mục tiêu[]\n\n[sky]⚡ CƠ CHẾ BẮN VÀ TÍCH LŨY:[]\n• Tốc độ bắn ban đầu là 500%, giảm dần 5%/giây khi liên tục bắn.\n• Sát thương tăng dần 50%/giây khi bắn, tối đa cộng thêm +5000% DMG.";
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = isEn
                    ? "[cyan]⚡ BASE STATS (MK2) ⚡[]\n[lightgray]Turret HP:[] [green]4,500 [lime](+50%)[]\n[lightgray]Effective Range:[] [orange]420 px [lime](+31.2%)[]\n[lightgray]Base Damage (Silicon):[] [yellow]97.50 DMG [lime](+44.4%)[]\n[lightgray]Base Damage (Copper):[] [orange]48.75 DMG[]\n[lightgray]Penetration:[] [yellow]5 targets[]\n\n[lime]⚡ SPECIAL EFFECT:[]\n• Firing until max stack deals [green]+1% Max HP[] bonus damage."
                    : "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]4,500 [lime](+50%)[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]420 pixel [lime](+31.2%)[]\n[lightgray]Sát thương Silicon:[] [yellow]97.50 DMG [lime](+44.4%)[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]48.75 DMG[]\n[lightgray]Khả năng xuyên thấu:[] [yellow]5 mục tiêu[]\n\n[lime]⚡ HIỆU ỨNG ĐẶC BIỆT:[]\n• Khi đạt đủ mốc giới hạn tích lũy, đạn bắn ra gây thêm [green]1% Max HP[] của mục tiêu.";
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = isEn
                    ? "[purple]⚡ BASE STATS (MK2B) ⚡[]\n[lightgray]Turret HP:[] [green]4,000 [lime](+33.3%)[]\n[lightgray]Effective Range:[] [orange]360 px [lime](+12.5%)[]\n[lightgray]Base Damage (Silicon):[] [red]183.75 DMG (+172.2%)[]\n[lightgray]Base Damage (Copper):[] [orange]91.875 DMG[]\n\n[purple]🔥 SPECIAL EFFECT:[]\n• Firing until max stack triggers a 35-tile AOE explosion dealing [pink]0.5% Max HP[]."
                    : "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]4,000 [lime](+33.3%)[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]360 pixel [lime](+12.5%)[]\n[lightgray]Sát thương Silicon:[] [red]183.75 DMG (+172.2%)[]\n[lightgray]Sát thương Đồng (Copper):[] [orange]91.875 DMG[]\n\n[purple]🔥 HIỆU ỨNG ĐẶC BIỆT:[]\n• Khi đạt đủ mốc giới hạn tích lũy, đạn bắn ra nổ phạm vi 35 ô với [pink]0.5% Max HP[].";
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
