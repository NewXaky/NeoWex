package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;

/**
 * Chuyển từ persefer.js. Gatling phòng không: bắn càng lâu càng chuyển sang đạn Star (xuyên khiên, -99 HP),
 * sát thương lên không quân cộng dồn; MK2 (cường hóa AA) và MK2B (tầm xa +50%, buff bạo kích cộng dồn).
 */
public final class Persefer {
    private Persefer() {}

    private static final Color RED_BLOOD = Color.valueOf("#b30000"), RED_BRIGHT = Color.valueOf("#ff1a40"), ORANGE_GLOW = Color.valueOf("#ff8000");

    // MK2: thorium, silicon | MK2B: surge, silicon, phase
    private static final int MK2_THORIUM = 400, MK2_SILICON = 300;
    private static final int MK2B_SURGE = 150, MK2B_SILICON = 500, MK2B_PHASE = 100;

    private static BulletType starBullet, copperBullet, graphiteBullet, siliconBullet, thoriumBullet;
    public static ItemTurret persefer;

    // Persefer JS: isEn() = locale không rỗng và bắt đầu bằng "en" (cùng nghĩa với các file khác).
    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static final Effect critEffect = new Effect(20f, e -> {
        Draw.color(RED_BRIGHT, ORANGE_GLOW, e.fin());
        for (int i = 0; i < 4; i++) {
            float angle = i * 90f + 45f;
            float len = 3f + e.fin() * 12f;
            Drawf.tri(e.x + Mathf.cosDeg(angle) * (len * 0.2f), e.y + Mathf.sinDeg(angle) * (len * 0.2f), 3.5f * e.fout(), len, angle);
        }
    });

    private static final Effect starHitEffect = new Effect(20f, e -> {
        float s = e.fout();
        Draw.color(RED_BLOOD);
        Lines.stroke(2.5f * s);
        Lines.circle(e.x, e.y, 8f * s);
        Draw.color(RED_BRIGHT);
        for (int i = 0; i < 4; i++) Drawf.tri(e.x, e.y, 4f * s, 14f * s, i * 90f);
        Draw.color(ORANGE_GLOW);
        for (int i = 0; i < 4; i++) Drawf.tri(e.x, e.y, 2.5f * s, 9f * s, i * 90f + 45f);
        Fill.circle(e.x, e.y, 3f * s);
        Draw.reset();
    });

    /** Đạn xuyên, chỉ đánh không quân; khi trúng cộng buff cho pháo và nhân sát thương theo airDmgBonus. */
    private static final class PerseferBullet extends BasicBulletType {
        final boolean star;

        PerseferBullet(float speed, float dmg, float life, boolean star) {
            super(speed, dmg);
            this.star = star;
            lifetime = life;
            pierce = true;
            pierceCap = 999;
            collidesGround = false;
            collidesAir = true;
        }

        /** JS gốc ghi đè hit() mà không gọi super (không splash/âm thanh mặc định) nên giữ nguyên. */
        @Override
        public void hit(Bullet b, float x, float y) {
            if (hitEffect != null) hitEffect.at(x, y, b.rotation(), backColor);

            Unit target = Units.closestEnemy(b.team, x, y, 24f, u -> true);
            if (target == null || !target.isFlying()) return;

            PerseferBuild owner = b.owner instanceof PerseferBuild ? (PerseferBuild) b.owner : null;
            float mult = owner != null ? owner.airDmgBonus : 1.5f;

            if (owner != null) {
                float maxCap = owner.getTier() == 1 ? 20f : 10f;
                if (Mathf.chance(0.05)) owner.addAirDmgBuff(0.05f, maxCap);
                if (owner.getTier() == 2 && Mathf.chance(0.01)) owner.triggerMk2bBuff();
            }

            float total = b.damage * mult;
            target.damage(total);
            if (!star) return;

            if (target.shield > 0f) {
                target.shield = Math.max(0f, target.shield - total * 5f);
                Fx.shieldBreak.at(target.x, target.y, 15f, ORANGE_GLOW);
            }

            target.health = Math.max(0f, target.health - 99f);
            if (target.health <= 0f) target.kill();

            Lightning.create(b.team, ORANGE_GLOW, 12f, target.x, target.y, b.rotation(), 2);
            Fx.hitLancer.at(target.x, target.y, ORANGE_GLOW);
            Call.label(t(en(), "[orange]-99 HP Energy (Shield Pierce)![]", "[orange]-99 HP Energy (Xuyên Khiên)![]"), 1f, target.x, target.y + 12f);
        }

        @Override
        public void draw(Bullet b) {
            if (!star) { super.draw(b); return; }
            float x = b.x, y = b.y, rot = b.time * 12f;
            float z = Draw.z();
            Draw.z(Layer.bullet);

            Draw.color(RED_BLOOD);
            Lines.stroke(2f);
            Lines.circle(x, y, 5f + Mathf.absin(b.time, 2f, 2f));
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 4.5f, 15f, rot + i * 90f);

            Draw.color(RED_BRIGHT);
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 3f, 11f, rot + i * 90f);

            Draw.color(ORANGE_GLOW);
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 2f, 7f, rot + i * 90f + 45f);
            Fill.circle(x, y, 2.5f);

            Draw.z(z);
            Draw.reset();
        }
    }

    private static PerseferBullet normal(float speed, float dmg, float life, float ammoMul, float reloadMul, float homing) {
        PerseferBullet b = new PerseferBullet(speed, dmg, life, false);
        b.ammoMultiplier = ammoMul;
        b.reloadMultiplier = reloadMul;
        if (homing > 0f) { b.homingPower = homing; b.homingRange = 120f; }
        return b;
    }

    /** Gọi từ NexMod.loadContent(). Thuộc tính còn lại (kích thước, drawer, ...) do persefer.hjson đặt. */
    public static void create() {
        PerseferBullet star = new PerseferBullet(18f, 45f, 50f, true);
        star.width = 14f;
        star.height = 14f;
        star.shrinkX = 0f;
        star.shrinkY = 0f;
        star.ammoMultiplier = 4f;
        star.reloadMultiplier = 1.3f;
        star.pierceArmor = true;
        star.frontColor = ORANGE_GLOW;
        star.backColor = RED_BRIGHT;
        star.lightRadius = 35f;
        star.lightOpacity = 0.8f;
        star.lightColor = RED_BRIGHT;
        star.hitEffect = starHitEffect;
        star.despawnEffect = starHitEffect;
        starBullet = star;

        copperBullet = normal(12f, 22f, 75f, 2f, 0.6f, 0f);
        graphiteBullet = normal(14f, 35f, 65f, 3f, 0.5f, 0f);
        siliconBullet = normal(13f, 26f, 70f, 3f, 0.6f, 0.12f);
        thoriumBullet = normal(15f, 40f, 60f, 4f, 0.8f, 0f);

        persefer = new ItemTurret("persefer");
        persefer.configurable = true;
        persefer.category = Category.turret;
        putAmmo();

        persefer.addBar("starConvert", (Building e) -> {
            PerseferBuild b = (PerseferBuild) e;
            return new Bar(
                () -> t(en(), "Star Ammo Conversion Ratio: ", "Tỉ lệ đạn Star: ") + Math.round(b.getStarConversionProgress() * 100f) + "%",
                () -> ORANGE_GLOW,
                () -> b.getStarConversionProgress());
        });

        persefer.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof PerseferBuild) ((PerseferBuild) tile).setTier(value);
        });

        persefer.buildType = () -> new PerseferBuild(persefer);
    }

    private static void putAmmo() {
        persefer.ammoTypes.clear();
        persefer.ammoTypes.put(Items.copper, copperBullet);
        persefer.ammoTypes.put(Items.graphite, graphiteBullet);
        persefer.ammoTypes.put(Items.silicon, siliconBullet);
        persefer.ammoTypes.put(Items.thorium, thoriumBullet);
    }

    /** persefer.hjson cũng khai báo ammoTypes; JS gốc xóa và đặt lại sau khi nạp nội dung nên ta làm lại ở ContentInitEvent. */
    public static void init() {
        Events.on(ContentInitEvent.class, e -> { if (persefer != null) putAmmo(); });
    }

    public static class PerseferBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0, mk2bStacks = 0;
        float continuousShootTime = 0f, airDmgBonus = 1.5f, haloAlpha = 0f, mk2bTimer = 0f;
        boolean nextBulletIsStar = false;

        public PerseferBuild(ItemTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            Fx.upgradeCore.at(x, y);
        }

        float getCritRate() {
            float base = 0.05f;
            if (getTier() == 1) base += 0.15f;
            float stack = mk2bTimer > 0f ? mk2bStacks * 0.01f : 0f;
            return Math.min(1f, base + stack);
        }

        float getCritDamageMultiplier() {
            float base = 0.5f;
            if (getTier() == 1) base += 0.3f;
            float stack = mk2bTimer > 0f ? mk2bStacks * 0.02f : 0f;
            return base + stack;
        }

        void triggerMk2bBuff() {
            if (mk2bStacks < 100) mk2bStacks++;
            mk2bTimer = 40f * 60f;
            Fx.heal.at(x, y);
            Call.label(t(en(), "[gold]+1% Critical![]\n[orange](" + mk2bStacks + "/100)[]", "[gold]+1% Bạo Kích![]\n[orange](" + mk2bStacks + "/100)[]"), 1.2f, x, y + 12f);
        }

        void addAirDmgBuff(float amount, float maxCap) {
            if (airDmgBonus < maxCap) airDmgBonus = Math.min(maxCap, airDmgBonus + amount);
        }

        float getStarConversionProgress() {
            float delay = 120f;
            float ramp = getTier() == 0 ? 1200f : 600f;
            if (continuousShootTime <= delay) return 0f;
            return Math.min(1f, (continuousShootTime - delay) / ramp);
        }

        @Override
        public float range() {
            float base = super.range();
            return getTier() == 2 ? base * 1.5f : base;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (mk2bTimer > 0f) {
                mk2bTimer -= Time.delta;
                if (mk2bTimer <= 0f) mk2bStacks = 0;
            }

            float maxTime = getTier() == 0 ? 1320f : 720f;
            // JS gốc viết `this.isShooting` (thiếu ngoặc, luôn truthy) nên halo/đạn Star tăng liên tục kể cả khi không bắn.
            // Bản Java gọi isShooting() thật theo đúng ý tưởng (MIGRATION bài học 10, 17).
            if (isShooting()) {
                continuousShootTime = Math.min(maxTime, continuousShootTime + Time.delta);
                haloAlpha = Math.min(1f, haloAlpha + Time.delta * 0.05f);
            } else {
                continuousShootTime = Math.max(0f, continuousShootTime - Time.delta * 1.5f);
                haloAlpha = Math.max(0f, haloAlpha - Time.delta * 0.03f);
            }
        }

        private void haloLine(float hx, float hy, float cos, float sin, int points) {
            float lastX = 0f, lastY = 0f;
            for (int i = 0; i <= points; i++) {
                float rad = (i * (360f / points)) * Mathf.degRad;
                float lx = (float) Math.cos(rad) * 10f, ly = (float) Math.sin(rad) * 3.2f;
                float px = hx + (lx * cos - ly * sin), py = hy + (lx * sin + ly * cos);
                if (i > 0) Lines.line(lastX, lastY, px, py);
                lastX = px;
                lastY = py;
            }
        }

        @Override
        public void draw() {
            super.draw();
            if (haloAlpha <= 0.001f) return;

            final float hx = x + Angles.trnsx(rotation + 180f, 8f), hy = y + Angles.trnsy(rotation + 180f, 8f);
            final float rotRad = (rotation + 90f) * Mathf.degRad;
            final float cos = (float) Math.cos(rotRad), sin = (float) Math.sin(rotRad);
            final float a = haloAlpha;

            Draw.draw(Layer.effect + 0.1f, () -> {
                Draw.color(RED_BLOOD); Draw.alpha(a * 0.7f); Lines.stroke(3.5f);
                haloLine(hx, hy, cos, sin, 24);
                Draw.color(RED_BRIGHT); Draw.alpha(a * 0.9f); Lines.stroke(2f);
                haloLine(hx, hy, cos, sin, 24);
                Draw.color(ORANGE_GLOW); Draw.alpha(a); Lines.stroke(0.9f);
                haloLine(hx, hy, cos, sin, 24);
                Draw.reset();
            });
        }

        @Override
        public BulletType useAmmo() {
            nextBulletIsStar = Mathf.chance(getStarConversionProgress());
            return super.useAmmo();
        }

        @Override
        public BulletType peekAmmo() {
            BulletType base = super.peekAmmo();
            if (base == null) return null;
            return nextBulletIsStar ? starBullet : base;
        }

        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
            if (bullet == null) return;
            if (getTier() == 1) bullet.damage *= 1.5f;
            if (getTier() == 2) bullet.lifetime *= 1.5f;
            if (Mathf.chance(getCritRate())) {
                bullet.damage *= (1f + getCritDamageMultiplier());
                critEffect.at(bullet.x, bullet.y);
            }
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(getTier());
            write.f(airDmgBonus);
            write.i(mk2bStacks);
            write.f(mk2bTimer);
            write.f(continuousShootTime);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            tierState = read.i();
            airDmgBonus = read.f();
            mk2bStacks = read.i();
            mk2bTimer = read.f();
            continuousShootTime = read.f();
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade Persefer System", "Nâng cấp hệ thống Persefer"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]PERSEFER SYSTEM HAS REACHED MAXIMUM EVOLUTION LEVEL![]",
                    "[scarlet]HỆ THỐNG PERSEFER ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Max Level Reached", "Đã đạt cấp tối đa"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(t(isEn, "View detailed stats", "Xem thông số chi tiết"));
        }

        private String col(int have, int need) { return (have >= need ? "[green]" : "[red]") + have + "[] / " + need; }

        private String requirementText(boolean isEn) {
            Building core = team != null ? team.core() : null;
            if (core == null) return t(isEn, "[red]Team Core Not Found![]", "[red]Không tìm thấy Lõi Đội![]");
            int th = core.items.get(Items.thorium), si = core.items.get(Items.silicon);
            int su = core.items.get(Items.surgeAlloy), ph = core.items.get(Items.phaseFabric);
            return isEn
                ? "[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Branch:[]\n • Thorium: " + col(th, MK2_THORIUM) + "\n • Silicon: " + col(si, MK2_SILICON)
                    + "\n[purple]MK2B Branch:[]\n • Surge Alloy: " + col(su, MK2B_SURGE) + "\n • Silicon: " + col(si, MK2B_SILICON) + "\n • Phase Fabric: " + col(ph, MK2B_PHASE)
                : "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh MK2:[]\n • Thorium: " + col(th, MK2_THORIUM) + "\n • Silicon: " + col(si, MK2_SILICON)
                    + "\n[purple]Nhánh MK2B:[]\n • Kim loại Surge: " + col(su, MK2B_SURGE) + "\n • Silicon: " + col(si, MK2B_SILICON) + "\n • Vải Phase: " + col(ph, MK2B_PHASE);
        }

        private void applyUpgrade(int tier, BaseDialog dialog, boolean isEn) {
            Building core = team != null ? team.core() : null;
            boolean ok;
            if (tier == 1) {
                ok = core != null && core.items.get(Items.thorium) >= MK2_THORIUM && core.items.get(Items.silicon) >= MK2_SILICON;
                if (ok) { NexNet.take(core, Items.thorium, MK2_THORIUM); NexNet.take(core, Items.silicon, MK2_SILICON); NexNet.fx(Fx.upgradeCore, x, y); }
            } else {
                ok = core != null && core.items.get(Items.surgeAlloy) >= MK2B_SURGE && core.items.get(Items.silicon) >= MK2B_SILICON && core.items.get(Items.phaseFabric) >= MK2B_PHASE;
                if (ok) { NexNet.take(core, Items.surgeAlloy, MK2B_SURGE); NexNet.take(core, Items.silicon, MK2B_SILICON); NexNet.take(core, Items.phaseFabric, MK2B_PHASE); NexNet.fx(Fx.bigShockwave, x, y); }
            }
            if (ok) {
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(4f, 4f, x, y);
                configure(Integer.valueOf(tier));
                dialog.hide(); deselect();
            } else {
                Vars.ui.showInfo(tier == 1 ? t(isEn, "[red]Not enough resources for MK2![]", "[red]Không đủ tài nguyên cho nhánh MK2![]")
                                           : t(isEn, "[red]Not enough resources for MK2B![]", "[red]Không đủ tài nguyên cho nhánh MK2B![]"));
            }
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, "Persefer Turret Upgrade Center", "Trung tâm nâng cấp pháo Persefer"));

            Table req = new Table();
            req.add(new Label(requirementText(isEn))).growX();
            dialog.cont.add(req);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add(t(isEn, "[cyan]===(MK2 - AA ENHANCEMENT)===[]", "[cyan]===(MK2 - CƯỜNG HÓA PHÒNG KHÔNG)===[]")).row();
            wrap(b1.add(t(isEn,
                "[white]• Base bullet damage: [green]+50%[]\n• Crit chance: [orange]+15%[] | Crit damage: [yellow]+30%[]\n• Reduce Star bullet convert time to: [gold]10s[]\n• Air damage stack cap: [scarlet]2000%[]",
                "[white]• Sát thương gốc đạn: [green]+50%[]\n• Tỉ lệ bạo kích: [orange]+15%[] | Sát thương bạo kích: [yellow]+30%[]\n• Giảm thời gian biến đổi đạn Star xuống: [gold]10 giây[]\n• Giới hạn cộng dồn sát thương không quân: [scarlet]2000%[]")), 340);
            b1.row();
            b1.button(t(isEn, "[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), () -> applyUpgrade(1, dialog, isEn)).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add(t(isEn, "[purple]===(MK2B - DAMAGE MULTIPLIER)===[]", "[purple]===(MK2B - BỘI PHÁT SÁT THƯƠNG)===[]")).row();
            wrap(b2.add(t(isEn,
                "[white]• Range & Lifetime: [green]+50%[]\n• Reduce Star bullet convert time to: [gold]10s[]\n• On hit: [gold]1% chance[] to stack buff (lasts 40s):\n  + Gain [orange]+1% Crit Chance[] (Max 100%)\n  + Gain [yellow]+2% Crit Damage[] (Max 200%)",
                "[white]• Tầm bắn & Thời gian bay của đạn: [green]+50%[]\n• Giảm thời gian biến đổi đạn Star xuống: [gold]10 giây[]\n• Trúng đạn: [gold]1% cơ hội[] tích buff (tồn tại 40s):\n  + Tăng [orange]+1% tỉ lệ bạo kích[] (Tối đa 100%)\n  + Tăng [yellow]+2% sát thương bạo kích[] (Tối đa 200%)")), 340);
            b2.row();
            b2.button(t(isEn, "[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), () -> applyUpgrade(2, dialog, isEn)).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            int bonusPct = Math.round((airDmgBonus - 1.5f) * 100f);
            String critRate = Strings.fixed(getCritRate() * 100f, 1);
            String critDmg = Strings.fixed(getCritDamageMultiplier() * 100f, 1);
            String remaining = Strings.fixed(mk2bTimer / 60f, 1);
            int convertPct = Math.round(getStarConversionProgress() * 100f);
            String transform = getTier() == 0 ? t(isEn, "20 seconds", "20 giây") : t(isEn, "10 seconds", "10 giây");

            String desc = isEn
                ? "[red]⚡ HALO AA SYSTEM ⚡[]\n[lightgray]Current Crit Chance:[] [orange]" + critRate + "%[]\n[lightgray]Current Crit Damage:[] [yellow]+" + critDmg + "%[]\n[lightgray]Star Ammo Convert Ratio:[] [gold]" + convertPct + "%[]\n\n"
                    + "• Default [scarlet]+150%[] extra damage against air targets.\n• [gold]5% chance[] per hit to add +5% air damage (Current: +" + bonusPct + "%).\n"
                    + "• Normal fire for 2s, then converts to [gold]Star Ammo[] over the next " + transform + ".\n• [gold]Star Ammo deals +400% Shield Damage & Pierces Shield to deal -99 HP.[]\n"
                : "[red]⚡ HỆ THỐNG PHÒNG KHÔNG HALO ⚡[]\n[lightgray]Tỉ lệ bạo kích hiện tại:[] [orange]" + critRate + "%[]\n[lightgray]Sát thương bạo kích hiện tại:[] [yellow]+" + critDmg + "%[]\n[lightgray]Tỉ lệ chuyển đổi đạn Star:[] [gold]" + convertPct + "%[]\n\n"
                    + "• Mặc định tăng [scarlet]+150%[] sát thương lên không quân.\n• [gold]5% cơ hội[] mỗi đạn trúng tăng +5% sát thương không quân (Đã tích: +" + bonusPct + "%).\n"
                    + "• Bắn 2s đầu, sau đó chuyển đổi thành [gold]Đạn Star[] trong " + transform + " tiếp theo.\n• [gold]Đạn Star gây +400% Sát Thương Khiên & Xuyên Khiên trừ 99 HP.[]\n";

            if (getTier() == 2) {
                desc += isEn
                    ? "\n[purple]⚡ MK2B STACK BUFFS ⚡[]\n• Stack Count: [gold]" + mk2bStacks + " / 100 stacks[]\n• Time Remaining: [cyan]" + (mk2bTimer > 0f ? remaining + "s" : "Expired") + "[]"
                    : "\n[purple]⚡ BUFF TÍCH LŨY MK2B ⚡[]\n• Số điểm tích lũy: [gold]" + mk2bStacks + " / 100 stack[]\n• Thời gian còn lại: [cyan]" + (mk2bTimer > 0f ? remaining + "s" : "Hết hiệu lực") + "[]";
            }

            BaseDialog dialog = new BaseDialog(t(isEn, " Persefer Turret Stats: ", " Thông số pháo Persefer: "));
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }
}
