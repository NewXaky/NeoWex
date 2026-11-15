package newex.content;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.scene.ui.Label;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ blixalum.js. Pháo điện từ: nạp 2 giây rồi bắn đạn nổ diện rộng, càng nhiều địch càng bắn nhanh;
 * nâng cấp MK2 (tầm xa, nổ rộng hơn) hoặc MK2B (thêm 4 tia laser mỗi 5 giây).
 */
public final class Blixalum {
    private Blixalum() {}

    private static final int[] REQ_MK2 = {4000, 4000, 0};    // đồng, chì, titan
    private static final int[] REQ_MK2B = {4000, 4000, 2000};

    private static Sound chargeSound, shootSound;
    private static Effect muzzleDistort, circleOut;
    private static BasicBulletType mk1Bullet, mk2Bullet, mk2bBullet;
    private static LaserBulletType laser;
    public static BlixalumBlock blixalum;

    /** Giống isVietnamese() của JS: mọi ngôn ngữ không phải "en" đều hiện tiếng Việt. */
    private static boolean vi() {
        String lang = "";
        try { lang = String.valueOf(Core.settings.getString("locale", "")); } catch (Throwable ignored) { lang = ""; }
        if (lang.isEmpty() || lang.equals("null") || lang.equals("undefined") || lang.equals("default")) {
            try { lang = String.valueOf(Core.bundle.getLocale().getLanguage()); } catch (Throwable ignored) { lang = ""; }
        }
        if (lang.equals("null") || lang.equals("undefined")) lang = "";
        return !lang.toLowerCase().startsWith("en");
    }

    private static String t(boolean v, String vi, String en) { return v ? vi : en; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static TextureRegion modRegion(String n) {
        if (Core.atlas.has(n)) return PerfCore.region(n);
        if (Core.atlas.has("newex-" + n)) return PerfCore.region("newex-" + n);
        if (Core.atlas.has("reinforced-" + n)) return PerfCore.region("reinforced-" + n);
        return PerfCore.region("clear");
    }

    private static void drawWindRing(float cx, float cy, float rx, float ry, float angle, float stroke, Color color) {
        Draw.color(color);
        Lines.stroke(stroke);
        int steps = 8;
        float lastX = 0f, lastY = 0f;
        float rad = angle * Mathf.degRad;
        float cosA = (float) Math.cos(rad), sinA = (float) Math.sin(rad);
        for (int i = 0; i <= steps; i++) {
            float a = (i * (360f / steps)) * Mathf.degRad;
            float lx = (float) Math.cos(a) * rx, ly = (float) Math.sin(a) * ry;
            float px = cx + (lx * cosA - ly * sinA), py = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, px, py);
            lastX = px;
            lastY = py;
        }
        Draw.reset();
    }

    private static BasicBulletType makeBullet(float baseDmg, float splashDmg, float splashRad, Color color, float life) {
        final Color col = color;
        BasicBulletType b = new BasicBulletType(2.2f, baseDmg) {
            @Override
            public void draw(Bullet bl) {
                super.draw(bl);
                float ang = bl.rotation();
                float prog = (bl.time * 0.05f) % 1f;
                float fout = 1f - prog;
                float off = 16f - prog * 33f;
                float rx = bl.x + Angles.trnsx(ang, off), ry = bl.y + Angles.trnsy(ang, off);
                float zoom = prog * 1.5f;
                if (fout > 0.05f) drawWindRing(rx, ry, 2f + zoom * 4f, 4f + zoom * 8f, ang, 1.5f * fout, col);
            }
        };
        b.width = 12f;
        b.height = 28f;
        b.lifetime = life;
        b.frontColor = color;
        b.backColor = Color.white;
        b.hitEffect = circleOut;
        b.despawnEffect = circleOut;
        b.hitColor = color;
        b.splashDamage = splashDmg;
        b.splashDamageRadius = splashRad;
        return b;
    }

    public static class BlixalumBlock extends ItemTurret {
        TextureRegion customBase, barrelRegion, w1, w2, wing1, wing2, wa1, wa2, winga1, winga2;

        public BlixalumBlock(String name) { super(name); }

        @Override
        public void load() {
            super.load();
            customBase = PerfCore.region("reinforced-block-" + size);
            barrelRegion = modRegion("blixalum-barrel");
            region = modRegion("blixalum-body");
            w1 = modRegion("blixalum-w1");
            w2 = modRegion("blixalum-w2");
            wing1 = modRegion("blixalum-wing1");
            wing2 = modRegion("blixalum-wing2");
            wa1 = modRegion("blixalum-wa1");
            wa2 = modRegion("blixalum-wa2");
            winga1 = modRegion("blixalum-winga1");
            winga2 = modRegion("blixalum-winga2");
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        chargeSound = Vars.tree.loadSound("plasma-charge");
        shootSound = Vars.tree.loadSound("plasma-shot-3");

        muzzleDistort = new Effect(16f, e -> {
            Color c = e.data instanceof Color ? (Color) e.data : Color.valueOf("#00d0ff");
            float fwd = 12f - e.fin() * 24f;
            float bx = e.x + Angles.trnsx(e.rotation, fwd), by = e.y + Angles.trnsy(e.rotation, fwd);
            drawWindRing(bx, by, 2f + e.fin() * 10f, 4f + e.fin() * 18f, e.rotation, 2f * e.fout(), c);
        });

        circleOut = new Effect(45f, 500f, e -> {
            float maxR = (e.rotation > 0 && e.rotation <= 120) ? e.rotation : 56f;
            float cur = maxR * e.fin(Interp.pow3Out);
            Draw.color(e.color);
            Fill.circle(e.x, e.y, (maxR * 0.35f) * e.fout());
            Lines.stroke(8f * e.fout(), e.color);
            Lines.circle(e.x, e.y, cur);
            Lines.stroke(4f * e.fout(), Color.white);
            Lines.circle(e.x, e.y, cur * 0.85f);
        });

        mk1Bullet = makeBullet(625f, 875f, 64f, Color.valueOf("#00ddff"), 120f);
        mk2Bullet = makeBullet(625f, 875f, 80f, Color.valueOf("#00ffffff"), 120f);
        mk2bBullet = makeBullet(750f, 150f, 48f, Color.valueOf("#eaff00"), 120f);

        laser = new LaserBulletType(1375f);
        laser.length = 442f;
        laser.width = 8f;
        laser.lifetime = 16f;
        laser.colors = new Color[]{Color.valueOf("#a1ff9a").cpy().mul(0.3f), Color.valueOf("#b1ffae"), Color.white};

        blixalum = new BlixalumBlock("blixalum");
        blixalum.squareSprite = false;
        blixalum.health = 9500;
        blixalum.size = 3;
        blixalum.reload = 60f;
        blixalum.configurable = true;
        blixalum.category = Category.turret;
        blixalum.ammoTypes.put(Items.titanium, mk1Bullet);

        blixalum.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof BlixalumBuild) ((BlixalumBuild) tile).setTier(value);
        });
        blixalum.buildType = () -> new BlixalumBuild(blixalum);
    }

    public static class BlixalumBuild extends ItemTurret.ItemTurretBuild {
        final BlixalumBlock bb;
        int tierState = 0;
        float chargeTimer = 0f, laserTimer = 0f, dynamicSpeedBonus = 1f, wingAnimation = 0f, customRecoil = 0f;
        float scanTimer = 0f, chargeSoundTimer = 0f;
        boolean isCharged = false;

        public BlixalumBuild(BlixalumBlock outer) { outer.super(); this.bb = outer; }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            chargeTimer = 0f;
            isCharged = false;
            laserTimer = 0f;
        }

        @Override
        public float range() { return getTier() == 1 ? 578f : 442f; }

        @Override
        public Object config() { return getTier(); }

        // ---------------- giao diện cấu hình ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            final boolean v = vi();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(v))
                    .size(50, 40).tooltip(t(v, "Nâng cấp tháp pháo lên", "Upgrade turret"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(v,
                    "[scarlet]Nâng cấp tháp pháo đã đạt giới hạn![]", "[scarlet]Turret upgrade reached max tier![]")))
                    .size(50, 40).tooltip(t(v, "Nâng cấp tháp pháo", "Upgrade turret"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(v))
                .size(50, 40).tooltip(t(v, "Trung tâm nâng cấp pháo", "Turret Upgrade Center"));
        }

        private String requirementText(boolean v) {
            Building core = team.core();
            if (core == null) return t(v, "[red]Không tìm thấy Lõi Đội![]", "[red]Team Core not found![]");
            int c = core.items.get(Items.copper), l = core.items.get(Items.lead), ti = core.items.get(Items.titanium);
            String c1 = c >= REQ_MK2[0] ? "[green]" : "[red]", l1 = l >= REQ_MK2[1] ? "[green]" : "[red]";
            String c2 = c >= REQ_MK2B[0] ? "[green]" : "[red]", l2 = l >= REQ_MK2B[1] ? "[green]" : "[red]";
            String t2 = ti >= REQ_MK2B[2] ? "[green]" : "[red]";
            if (v) {
                return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n"
                    + "[cyan]Nhánh Cấu Hình MK2[]\n"
                    + " • Đồng: " + c1 + c + "[] / " + REQ_MK2[0] + "\n"
                    + " • Chì: " + l1 + l + "[] / " + REQ_MK2[1] + "\n"
                    + "[purple]Nhánh Biến Thể MK2B[]\n"
                    + " • Đồng: " + c2 + c + "[] / " + REQ_MK2B[0] + "\n"
                    + " • Chì: " + l2 + l + "[] / " + REQ_MK2B[1] + "\n"
                    + " • Titan: " + t2 + ti + "[] / " + REQ_MK2B[2];
            }
            return "[yellow]CORE VAULT RESOURCE REQUIREMENTS:[]\n"
                + "[cyan]MK2 Configuration Path[]\n"
                + " • Copper: " + c1 + c + "[] / " + REQ_MK2[0] + "\n"
                + " • Lead: " + l1 + l + "[] / " + REQ_MK2[1] + "\n"
                + "[purple]MK2B Variant Path[]\n"
                + " • Copper: " + c2 + c + "[] / " + REQ_MK2B[0] + "\n"
                + " • Lead: " + l2 + l + "[] / " + REQ_MK2B[1] + "\n"
                + " • Titanium: " + t2 + ti + "[] / " + REQ_MK2B[2];
        }

        private void tryUpgrade(BaseDialog dialog, int[] req, int newTier, boolean v) {
            Building core = team.core();
            if (core != null && core.items.get(Items.copper) >= req[0] && core.items.get(Items.lead) >= req[1]
                && core.items.get(Items.titanium) >= req[2]) {
                NexNet.take(core, Items.copper, req[0]);
                NexNet.take(core, Items.lead, req[1]);
                if (req[2] > 0) NexNet.take(core, Items.titanium, req[2]);
                NexNet.fx(newTier == 1 ? Fx.upgradeCore : Fx.bigShockwave, x, y);
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(5f, 5f, x, y);
                NexNet.configure(this, newTier);
                dialog.hide();
                deselect();
            } else {
                Vars.ui.showInfo(newTier == 1
                    ? t(v, "[red]Không đủ tài nguyên cho nhánh MK2![]", "[red]Not enough resources for MK2 path![]")
                    : t(v, "[red]Không đủ tài nguyên cho nhánh MK2B![]", "[red]Not enough resources for MK2B path![]"));
            }
        }

        private void openUpgrade(boolean v) {
            BaseDialog dialog = new BaseDialog(t(v, "Trung tâm nâng cấp pháo", "Turret Upgrade Center"));
            wrap(dialog.cont.add(requirementText(v)), 360);
            dialog.cont.row();
            dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table();
            b1.background(Styles.black6);
            b1.margin(12);
            b1.add("[cyan]===(MK2)===[]").row();
            wrap(b1.add(t(v,
                "[white]• Tầm bắn: [green]578 px[]\n"
                + "• Tốc độ bắn tối đa: [green]+200%[]\n"
                + "• Bán kính nổ lan: [green]+25%[] (80 px)\n"
                + "• Bạo kích: [yellow]80% Tỉ lệ | 120% ST Bạo kích[]\n"
                + "• [orange]Tần Tốc Thông Minh:[] Tự động quét từ trường xung quanh và gia tăng tốc độ hỏa lực đột biến theo mật độ kẻ địch trong tầm bắn",
                "[white]• Range: [green]578 px[]\n"
                + "• Max Fire Rate: [green]+200%[]\n"
                + "• Splash Damage Radius: [green]+25%[] (80 px)\n"
                + "• Critical: [yellow]80% Rate | 120% Crit DMG[]\n"
                + "• [orange]Smart Speed Frequency:[] Automatically scans surrounding magnetic fields and surges firing speed according to enemy density within range")), 340);
            b1.row();
            b1.button(t(v, "[green]KÍCH HOẠT MK2[]", "[green]ACTIVATE MK2[]"), () -> tryUpgrade(dialog, REQ_MK2, 1, v)).size(180, 38);

            Table b2 = new Table();
            b2.background(Styles.black6);
            b2.margin(12);
            b2.add("[purple]===(MK2B)===[]").row();
            wrap(b2.add(t(v,
                "[white]• Sát thương gốc: [green]750 DMG[]\n"
                + "• Bán kính nổ lan: [red]-25%[] (48 px)\n"
                + "• Tốc độ bắn tối đa: [red]-20%[]\n"
                + "• Bạo kích: [yellow]80% Tỉ lệ | 120% ST Bạo kích[]\n"
                + "• [orange]Bảo Táp Laser:[] Tích tụ ma trận lõi năng lượng và tự động kích hoạt phóng 4 tia Laser hội tụ thiêu rụi mục tiêu mỗi 5 giây",
                "[white]• Base Damage: [green]750 DMG[]\n"
                + "• Splash Damage Radius: [red]-25%[] (48 px)\n"
                + "• Max Fire Rate: [red]-20%[]\n"
                + "• Critical: [yellow]80% Rate | 120% Crit DMG[]\n"
                + "• [orange]Laser Tempest:[] Charges core energy matrix and automatically fires 4 focused Laser beams to incinerate targets every 5 seconds")), 340);
            b2.row();
            b2.button(t(v, "[orange]KÍCH HOẠT MK2B[]", "[orange]ACTIVATE MK2B[]"), () -> tryUpgrade(dialog, REQ_MK2B, 2, v)).size(180, 38);

            branches.add(b1).width(340);
            branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfo(boolean v) {
            int tier = getTier();
            String title = t(v, " Thông số pháo \"Blixalum\": ", " Turret Specs \"Blixalum\": ");
            String desc = "";
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = t(v,
                    "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]9,500 HP[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]442 pixel[]\n"
                    + "[lightgray]Sát thương gốc:[] [white]625 DMG / phát bắn[]\n"
                    + "[lightgray]Sát thương nổ lan:[] [white]875 DMG (64 px)[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n"
                    + "[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Tích Năng Lượng Từ Trường: Cần 2 giây nạp sạc trước khi xả đạn xung kích.\n"
                    + "• Tốc hỏa thích ứng: Tự động tăng +10% tốc độ bắn với mỗi kẻ địch xuất hiện trong tầm bắn (tối đa +100%).",
                    "[gold]⚡ BASE SPECS (MK1) ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]9,500 HP[]\n"
                    + "[lightgray]Effective Range:[] [orange]442 pixels[]\n"
                    + "[lightgray]Base Damage:[] [white]625 DMG / shot[]\n"
                    + "[lightgray]Splash Damage:[] [white]875 DMG (64 px)[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n"
                    + "[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ SPECIAL SKILL MECHANICS:[]\n"
                    + "• Magnetic Energy Charge: Requires 2s charging time before releasing shockwave bullets.\n"
                    + "• Adaptive Fire Rate: Automatically increases +10% fire rate per enemy within range (max +100%).");
            } else if (tier == 1) {
                title += t(v, "[cyan]THÔNG SỐ NÂNG CẤP MK2[]", "[cyan]UPGRADE SPECS MK2[]");
                desc = t(v,
                    "[cyan]⚡ THÔNG SỐ NÂNG CẤP MK2 ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]9,500 HP[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]578 pixel[]\n"
                    + "[lightgray]Sát thương gốc:[] [white]625 DMG / phát bắn[]\n"
                    + "[lightgray]Sát thương nổ lan:[] [white]875 DMG (80 px) (+25%)[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n"
                    + "[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Mở Rộng Tần Tốc Thông Minh: Cải tiến lõi từ trường tối ưu tần suất quét tự động.\n"
                    + "• Tốc hỏa đột biến: Tăng +10% tốc độ bắn cho mỗi kẻ địch trong tầm bắn (tối đa +300%).",
                    "[cyan]⚡ UPGRADE SPECS MK2 ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]9,500 HP[]\n"
                    + "[lightgray]Effective Range:[] [orange]578 pixels[]\n"
                    + "[lightgray]Base Damage:[] [white]625 DMG / shot[]\n"
                    + "[lightgray]Splash Damage:[] [white]875 DMG (80 px) (+25%)[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n"
                    + "[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ SPECIAL SKILL MECHANICS:[]\n"
                    + "• Smart Frequency Expansion: Enhanced magnetic core optimizing auto-scan frequency.\n"
                    + "• Fire Rate Surge: Increases +10% fire rate per enemy in range (max +300%).");
            } else if (tier == 2) {
                title += t(v, "[purple]THÔNG SỐ NÂNG CẤP MK2B[]", "[purple]UPGRADE SPECS MK2B[]");
                desc = t(v,
                    "[purple]⚡ THÔNG SỐ NÂNG CẤP MK2B ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]9,500 HP[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [red]442 pixel[]\n"
                    + "[lightgray]Sát thương gốc:[] [white]750 DMG / phát bắn[]\n"
                    + "[lightgray]Sát thương nổ lan:[] [white]150 DMG (48 px) (-25%)[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n"
                    + "[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[purple]🔥 CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Bảo Táp Laser Đột Phá: Hợp nhất ma trận lõi năng lượng laser phá hủy cơ động.\n"
                    + "• Xung kích phụ: Mỗi 5 giây nạp sạc sẽ tự động bắn 4 tia Laser (375 DMG/tia) dội thẳng vào mục tiêu.",
                    "[purple]⚡ UPGRADE SPECS MK2B ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]9,500 HP[]\n"
                    + "[lightgray]Effective Range:[] [red]442 pixels[]\n"
                    + "[lightgray]Base Damage:[] [white]750 DMG / shot[]\n"
                    + "[lightgray]Splash Damage:[] [white]150 DMG (48 px) (-25%)[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n"
                    + "[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[purple]🔥 SPECIAL SKILL MECHANICS:[]\n"
                    + "• Laser Tempest Breakthrough: Merges mobile laser core matrix for heavy fire.\n"
                    + "• Auxiliary Impulse: Every 5s charge automatically fires 4 Laser beams (375 DMG/beam) directly into targets.");
            }
            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton();
            dialog.show();
        }

        // ---------------- logic ----------------

        @Override
        public void updateTile() {
            super.updateTile();
            int tier = getTier();

            customRecoil = Mathf.lerpDelta(customRecoil, 0f, 0.12f);
            wingAnimation = Mathf.lerpDelta(wingAnimation, target != null ? 1f : 0f, 0.05f);

            scanTimer += Time.delta;
            if (scanTimer >= 15f) {
                scanTimer = 0f;
                final int[] count = {0};
                final float r = range();
                Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
                    if (u != null && !u.dead && dst(u) <= r) count[0]++;
                });
                float maxBonus = tier == 1 ? 3f : (tier == 2 ? 0.8f : 1f);
                dynamicSpeedBonus = 1f + Math.min(maxBonus, count[0] * 0.1f);
            }

            if (isShooting() && hasAmmo()) {
                reloadCounter += Time.delta * (dynamicSpeedBonus - 1f) * efficiency;
                if (!isCharged) {
                    if (reloadCounter > 0f) reloadCounter = 0f;
                    chargeTimer += Time.delta * efficiency;

                    chargeSoundTimer += Time.delta;
                    if (chargeSoundTimer >= 30f && chargeSound != null) {
                        chargeSoundTimer = 0f;
                        chargeSound.at(x, y, 0.8f + (chargeTimer / 120f) * 0.4f);
                    }
                    if (chargeTimer >= 120f) isCharged = true;
                }
                if (tier == 2) {
                    laserTimer += Time.delta * efficiency;
                    if (laserTimer >= 300f) {
                        laserTimer = 0f;
                        fireTier2BLasers();
                    }
                }
            } else {
                chargeTimer = Math.max(0f, chargeTimer - Time.delta * 1.5f);
                isCharged = false;
                laserTimer = 0f;
                chargeSoundTimer = 0f;
            }
        }

        void fireTier2BLasers() {
            if (target == null) return;
            float targetAngle = Angles.angle(x, y, target.getX(), target.getY());
            float[] offsets = {8f, 3f, -3f, -8f};
            for (int i = 0; i < 4; i++) {
                float sx = x + Angles.trnsx(rotation, -5f, offsets[i]);
                float sy = y + Angles.trnsy(rotation, -5f, offsets[i]);
                // JS gốc truyền 1.0 vào tham số "velocityScl" (bản 7 tham số của create), không phải sát thương: giữ nguyên.
                laser.create(this, team, sx, sy, targetAngle, 1f, 1f);
            }
        }

        @Override
        protected void shoot(BulletType type) {
            if (!isCharged) return;
            int tier = getTier();
            BasicBulletType sel = tier == 1 ? mk2Bullet : (tier == 2 ? mk2bBullet : mk1Bullet);
            float sx = x + Angles.trnsx(rotation, 8f), sy = y + Angles.trnsy(rotation, 8f);

            float mult = Mathf.chance(0.80) ? 2.20f : 1f;
            float finalDmg = sel.damage * mult;

            Call.createBullet(sel, team, sx, sy, rotation, finalDmg, sel.speed, 1f);
            Color tierColor = tier == 1 ? Color.valueOf("#00ffff") : (tier == 2 ? Color.valueOf("#33ddff") : Color.valueOf("#e5ff00"));
            muzzleDistort.at(sx, sy, rotation, tierColor);

            if (shootSound != null) shootSound.at(x, y, Mathf.random(0.9f, 1.1f));
            customRecoil = 1f;
            useAmmo();
        }

        private void rect(TextureRegion r, float px, float py, float rot) {
            if (r != null && r.found()) Draw.rect(r, px, py, rot);
        }

        @Override
        public void draw() {
            if (bb.customBase != null && bb.customBase.found()) Draw.rect(bb.customBase, x, y);

            float s = rotation, da = s - 90f, anim = wingAnimation;

            float w1Back = -5f * anim;
            float w2Back = -6.5f * anim, w2Side = 1.5f * anim;
            float wa2Back = -5f * anim;
            float wa1Back = -6.5f * anim, wa1Side = 1.5f * anim;
            float wing1Back = -2f, wing1Side = 1.5f * anim, wing2Side = 1f * anim;
            float winga1Side = 1f * anim;
            float winga2Back = -2f * anim, winga2Side = 1.5f * anim;
            float barrelRecoil = -7.5f * customRecoil;

            float w1X = x + Angles.trnsx(s, w1Back), w1Y = y + Angles.trnsy(s, w1Back);
            float w2X = x + Angles.trnsx(s, w2Back) + Angles.trnsx(s - 90f, w2Side);
            float w2Y = y + Angles.trnsy(s, w2Back) + Angles.trnsy(s - 90f, w2Side);
            float wing1X = x + Angles.trnsx(s, wing1Back) + Angles.trnsx(s - 90f, wing1Side);
            float wing1Y = y + Angles.trnsy(s, wing1Back) + Angles.trnsy(s - 90f, wing1Side);
            float wing2X = x + Angles.trnsx(s - 90f, wing2Side), wing2Y = y + Angles.trnsy(s - 90f, wing2Side);
            float wa2X = x + Angles.trnsx(s, wa2Back), wa2Y = y + Angles.trnsy(s, wa2Back);
            float wa1X = x + Angles.trnsx(s, wa1Back) + Angles.trnsx(s + 90f, wa1Side);
            float wa1Y = y + Angles.trnsy(s, wa1Back) + Angles.trnsy(s + 90f, wa1Side);
            float winga1X = x + Angles.trnsx(s + 90f, winga1Side), winga1Y = y + Angles.trnsy(s + 90f, winga1Side);
            float winga2X = x + Angles.trnsx(s, winga2Back) + Angles.trnsx(s + 90f, winga2Side);
            float winga2Y = y + Angles.trnsy(s, winga2Back) + Angles.trnsy(s + 90f, winga2Side);
            float btuX = x + Angles.trnsx(s, barrelRecoil), btuY = y + Angles.trnsy(s, barrelRecoil);

            rect(bb.wing2, wing2X, wing2Y, da);
            rect(bb.winga2, winga2X, winga2Y, da);
            rect(bb.wing1, wing1X, wing1Y, da);
            rect(bb.winga1, winga1X, winga1Y, da);
            rect(bb.w2, w2X, w2Y, da);
            rect(bb.wa2, wa2X, wa2Y, da);
            rect(bb.w1, w1X, w1Y, da);
            rect(bb.wa1, wa1X, wa1Y, da);
            rect(bb.barrelRegion, btuX, btuY, da);
            rect(bb.region, x, y, da);

            if (isShooting() && !isCharged && hasAmmo()) {
                final float progress = chargeTimer / 120f;
                final float mx = x + Angles.trnsx(rotation, 10f), my = y + Angles.trnsy(rotation, 10f);
                Draw.draw(Layer.effect + 1f, () -> Fill.circle(mx, my, 2f * progress));
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
        }
    }
}
