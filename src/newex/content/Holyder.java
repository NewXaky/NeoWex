package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
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
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ holyder-system.js. Pháo năng lượng tự sạc + khiên; updateTile thay hẳn logic Turret (JS không gọi super). */
public final class Holyder {
    private Holyder() {}

    static final Color laserAimColor = Color.valueOf("#ff8888");
    static final Color secondaryLaserAimColor = Color.valueOf("#ffaa00");
    static final Color bulletColor = Color.valueOf("#00ffff");

    static final class Req { final int titanium, silicon, plastanium; Req(int t, int s, int p) { titanium = t; silicon = s; plastanium = p; } }
    static final Req reqMK2 = new Req(400, 400, 0);
    static final Req reqMK2B = new Req(880, 640, 420);

    static Effect mainBulletHitFX, muzzleFlashFX, shieldHitFX;
    static BasicBulletType mainBullet, mainBulletMK2B, secondaryBullet;
    static BulletType holyderlaser;
    public static PowerTurret holyder;

    /** JS: Core.settings.get("locale","default"); Vars.ui.getLanguage không tồn tại ở v157 nên nhánh đó chưa từng chạy. */
    static boolean isEnglish() {
        try {
            String loc = Core.settings.getString("locale", "default");
            return loc != null && loc.startsWith("en");
        } catch (Exception e) {
            return false;
        }
    }

    static boolean isDead(Object o) { return o instanceof Healthc && ((Healthc) o).dead(); }

    static BasicBulletType mainBullet(boolean mk2b) {
        BasicBulletType b = new BasicBulletType();
        b.speed = 35f; b.lifetime = 15f; b.width = 12f; b.height = 16f;
        b.frontColor = Color.white; b.backColor = bulletColor;
        b.hitEffect = mainBulletHitFX; b.despawnEffect = Fx.none;
        b.collides = true; b.collidesTiles = true; b.shrinkX = 0f; b.shrinkY = 0f;
        if (mk2b) b.pierce = false;
        return b;
    }

    public static void create() {
        mainBulletHitFX = new Effect(18f, e -> {
            Draw.color(bulletColor, Color.white, e.fin());
            Lines.stroke(2.5f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * 18f);
            Angles.randLenVectors(e.id, 8, 26f * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, 2f * e.fout()));
            Draw.reset();
        });
        muzzleFlashFX = new Effect(12f, e -> {
            Draw.color(bulletColor);
            Lines.stroke(3f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * 12f);
            Draw.reset();
        });
        shieldHitFX = new Effect(10f, e -> {
            Draw.color(bulletColor, Color.white, e.fin());
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * e.rotation);
            Draw.reset();
        });

        mainBullet = mainBullet(false);
        mainBulletMK2B = mainBullet(true);
        secondaryBullet = new BasicBulletType();
        secondaryBullet.speed = 35f; secondaryBullet.lifetime = 18f; secondaryBullet.width = 8f; secondaryBullet.height = 11f;
        secondaryBullet.frontColor = Color.white; secondaryBullet.backColor = Color.valueOf("#ffaa00");
        secondaryBullet.hitEffect = Fx.hitBulletSmall; secondaryBullet.despawnEffect = Fx.none;

        holyderlaser = new BulletType() {
            @Override public void init(Bullet b) { if (b != null) b.remove(); }
            @Override public void draw(Bullet b) {}
        };
        holyderlaser.speed = 0f; holyderlaser.lifetime = 1f;

        holyder = new PowerTurret("holyder");
        holyder.health = 550; holyder.size = 3; holyder.range = 410f; holyder.reload = 9999f;
        holyder.shootType = holyderlaser;
        holyder.consumePower(15f);
        holyder.targetAir = false; holyder.targetGround = true; holyder.configurable = true;
        holyder.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof HolyderBuild) ((HolyderBuild) tile).setTier(value);
        });
        holyder.buildType = () -> new HolyderBuild(holyder);
    }

    public static class HolyderBuild extends PowerTurret.PowerTurretBuild {
        int holyderInternalTier = 1;
        float laserCharge = 0f, lockTimer = 0f, shootCooldown = 0f;
        boolean isNewTargetLocking = false;
        Teamc secondaryTarget = null;
        float shieldHealth = 0f, maxShield = 54000f, shieldRadius = 70.4f;
        boolean shieldActive = false;
        float shieldVisualScale = 0f, shieldHitTime = 0f;
        float regenTimer = 0f, maxRegenCooldown = 600f;
        boolean isShieldBroken = false;
        float animRecoil = 0f;

        public HolyderBuild(PowerTurret outer) { outer.super(); }

        /** Bài học 6: canControl là phương thức. */
        @Override
        public boolean canControl() { return false; }

        void setTier(int val) {
            holyderInternalTier = val;
            if (val == 1) { maxShield = 54000f; health = 550f; }
            else if (val == 2) { maxShield = 54000f; health = 704f; }
            else if (val == 3) { maxShield = 6000f; health = 880f; }
            shieldHealth = maxShield;
        }

        int getTier() { return holyderInternalTier; }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        private String requirementText(boolean en) {
            CoreBuild core = team.core();
            if (core == null) return en ? "[red]Team Core Not Found![]" : "[red]Không tìm thấy Lõi Đội![]";
            int currenttitanium = core.items.get(Items.titanium);
            int currentsilicon = core.items.get(Items.silicon);
            int currentplastanium = core.items.get(Items.plastanium);
            String titColor1 = currenttitanium >= reqMK2.titanium ? "[green]" : "[red]";
            String silColor1 = currentsilicon >= reqMK2.silicon ? "[green]" : "[red]";
            String titColor2 = currenttitanium >= reqMK2B.titanium ? "[green]" : "[red]";
            String silColor2 = currentsilicon >= reqMK2B.silicon ? "[green]" : "[red]";
            String plaColor2 = currentplastanium >= reqMK2B.plastanium ? "[green]" : "[red]";
            if (en) {
                            return "[yellow]CORE STORAGE REQUIREMENTS:[]\n" +
                                   "[cyan]MK2 Branch:[]\n" +
                                   " • Titanium: " + titColor1 + currenttitanium + "[] / " + reqMK2.titanium + "\n" +
                                   " • Silicon: " + silColor1 + currentsilicon + "[] / " + reqMK2.silicon + "\n" +
                                   "[purple]MK2B Branch:[]\n" +
                                   " • Titanium: " + titColor2 + currenttitanium + "[] / " + reqMK2B.titanium + "\n" +
                                   " • Silicon: " + silColor2 + currentsilicon + "[] / " + reqMK2B.silicon + "\n" +
                                   " • Plastanium: " + plaColor2 + currentplastanium + "[] / " + reqMK2B.plastanium;
            } else {
                            return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                                   "[cyan]Nhánh MK2:[]\n" +
                                   " • Titan: " + titColor1 + currenttitanium + "[] / " + reqMK2.titanium + "\n" +
                                   " • Silicon: " + silColor1 + currentsilicon + "[] / " + reqMK2.silicon + "\n" +
                                   "[purple]Nhánh MK2B:[]\n" +
                                   " • Titan: " + titColor2 + currenttitanium + "[] / " + reqMK2B.titanium + "\n" +
                                   " • Silicon: " + silColor2 + currentsilicon + "[] / " + reqMK2B.silicon + "\n" +
                                   " • Nhựa: " + plaColor2 + currentplastanium + "[] / " + reqMK2B.plastanium;
            }
        }

        private void upgrade(int tier, BaseDialog dialog, boolean en) {
            CoreBuild core = team.core();
            Req r = tier == 2 ? reqMK2 : reqMK2B;
            if (core != null && core.items.get(Items.titanium) >= r.titanium && core.items.get(Items.silicon) >= r.silicon
                && core.items.get(Items.plastanium) >= r.plastanium) {
                NexNet.take(core, Items.titanium, r.titanium);
                NexNet.take(core, Items.silicon, r.silicon);
                if (tier == 3) NexNet.take(core, Items.plastanium, r.plastanium);
                NexNet.fx(tier == 2 ? Fx.upgradeCore : Fx.bigShockwave, x, y);
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(5f, 5f, x, y);
                configure(Integer.valueOf(tier));
                dialog.hide(); deselect();
            } else if (tier == 2) {
                Vars.ui.showInfo(en ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
            } else {
                Vars.ui.showInfo(en ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
            }
        }

        private void openUpgrade(boolean en) {
            BaseDialog dialog = new BaseDialog(en ? "Holyder Turret Upgrade Center" : "Trung tâm nâng cấp tháp pháo Holyder");
            Cell<Label> reqCell = dialog.cont.add(requirementText(en));
            reqCell.width(360f); reqCell.get().setWrap(true); reqCell.get().setAlignment(Align.left);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branchesTable = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
                    String b1Text = en ?
                        "Expanded standard firepower module:\n" +
                        " [white]• Max impulse charge limit increased by [yellow]+200%[].[]\n" +
                        " [white]• Activates auxiliary barrel firing support dealing [orange]50% main bullet damage[].[]\n" +
                        " [white]• Increases Health by [green]+28%[] and Range by [green]+6.25%[]." :
                        "Mô-đun cấu hình hỏa lực tiêu chuẩn mở rộng:\n" +
                        " [white]• Giới hạn tích lũy điểm xung kích cực đại tăng vọt [yellow]+200%[].[]\n" +
                        " [white]• Kích hoạt nòng hỏa lực phụ tự động bắn hỗ trợ ngẫu nhiên [orange]50% sát thương đạn chính[].[]\n" +
                        " [white]• Tăng [green]+28% Máu[] và tăng [green]+6.25% Tầm bắn[].";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f); b1D.get().setWrap(true); b1D.get().setAlignment(Align.left); b1.row();
            b1.button(en ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> upgrade(2, dialog, en)).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
                    String b2Text = en ?
                        "Armored heavy impulse circuit configuration:\n" +
                        " [white]• Charge threshold reduced to [yellow]50%[] for ultra-fast firing cycles.[]\n" +
                        " [white]• Overload Trait: When shield breaks, charge rate and reload cycle increase by [red]+500%[].[]\n" +
                        " [white]• Increases System Health by [green]+60%[] and Range by [green]+12.5%[], but reduces shield core durability." :
                        "Cấu hình mạch xung kích bến thế trọng pháo giáp:\n" +
                        " [white]• Giới hạn sạc điểm giảm xuống mốc [yellow]50%[] hỗ trợ xả đạn thần tốc cực hạn.[]\n" +
                        " [white]• Đặc tính Quá Tải: Khi khiên vỡ, tốc độ sạc và chu kỳ hồi nạp đạn [red]tăng đột biến +500%[].[]\n" +
                        " [white]• Tăng [green]+60% Máu[] hệ thống và tăng [green]+12.5% Tầm bắn[], nhưng giảm sức bền của lõi khiên.";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f); b2D.get().setWrap(true); b2D.get().setAlignment(Align.left); b2.row();
            b2.button(en ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> upgrade(3, dialog, en)).size(180, 38);

            branchesTable.add(b1).width(340f); branchesTable.row();
            branchesTable.add().height(12).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean en) {
            String title = en ? " Holyder Turret Stats: " : " Thông số pháo Holyder: ";
            String descStr = "";
            int currentTier = getTier();
            if (currentTier == 1) {
                title += "[yellow](MK1)[]";
                    descStr = en ?
                        "[gold]⚡ BASIC STATS (MK1) ⚡[]\n" +
                        "[lightgray]Turret Health:[] [green]550 HP (3x3 Size)[]\n" +
                        "Raw Damage:[] [purple]380 / shot[] (Damage scales with charge ratio)\n" +
                        "Effective Range:[] [orange]410 pixels[] | [lightgray]Target:[] Ground\n\n" +
                        "[sky]⚡ THERMAL CIRCUIT & SHIELD MECHANICS:[]\n" +
                        "• [lightgray]Targeting:[] Uses red laser beam to lock position before charging.\n" +
                        "• [lightgray]Energy Shield:[] Automatically activates shield absorbing [teal]54,000 HP[]. Requires [yellow]10.0 seconds[] to restart core when broken." :
                        "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                        "[lightgray]Máu tháp pháo:[] [green]550 HP (Quy cách 3x3)[]\n" +
                        "Sát thương thô:[] [purple]380 / viên[] (Sát thương tăng theo tỷ lệ sạc hỏa lực)\n" +
                        "Tầm bắn hiệu dụng:[] [orange]410 pixel[] | [lightgray]Mục tiêu:[] Mặt đất\n\n" +
                        "[sky]⚡ CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH & KHIÊN:[]\n" +
                        "• [lightgray]Định vị mục tiêu:[] Sử dụng tia laser đỏ để khóa cứng vị trí trước khi tích sạc.\n" +
                        "• [lightgray]Trường khiên năng lượng:[] Tự động kích hoạt khiên hấp thụ [teal]54,000 HP[]. Khi vỡ cần [yellow]10.0 giây[] để tái khởi động lõi khiên.";
            } else if (currentTier == 2) {
                title += "[cyan](MK2)[]";
                    descStr = en ?
                        "[cyan]⚡ BASIC STATS (MK2) ⚡[]\n" +
                        "[lightgray]Turret Health:[] [green]704 HP [lime](+28%)[]\n" +
                        "Primary Damage:[] [purple]380[] + [orange]50% secondary barrel damage[]\n" +
                        "Effective Range:[] [orange]340 pixels [lime](+6.25%)[]\n\n" +
                        "[lime]⚡ THERMAL CIRCUIT & SHIELD MECHANICS:[]\n" +
                        "• [lightgray]Max Impulse:[] Enhanced charge accumulation limit reaches [yellow]200%[].\n" +
                        "• [lightgray]Multi-Target:[] Activates secondary accelerated barrel, firing support rounds at a secondary target.\n" +
                        "• [lightgray]Energy Shield:[] Maintains strong core shield protecting [teal]54,000 HP[]." :
                        "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                        "[lightgray]Máu tháp pháo:[] [green]704 HP [lime](+28%)[]\n" +
                        "Sát thương chính:[] [purple]380[] + [orange]50% sát thương từ nòng phụ[]\n" +
                        "Tầm bắn hiệu dụng:[] [orange]340 pixel [lime](+6.25%)[]\n\n" +
                        "[lime]⚡ CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH & KHIÊN:[]\n" +
                        "• [lightgray]Xung kích cực đại:[] Giới hạn tích lũy điểm xung sạc nâng cao chạm mốc [yellow]200%[].\n" +
                        "• [lightgray]Đa mục tiêu:[] Kích hoạt thêm nòng phụ gia tốc, tự động xả đạn hỗ trợ vào 1 mục tiêu phụ lân cận.\n" +
                        "• [lightgray]Trường khiên năng lượng:[] Duy trì hệ thống lõi khiên bảo vệ vững chắc [teal]54,000 HP[].";
            } else if (currentTier == 3) {
                title += "[purple](MK2B)[]";
                    descStr = en ?
                        "[purple]⚡ BASIC STATS (MK2B) ⚡[]\n" +
                        "[lightgray]Reinforced Health:[] [green]880 HP [lime](+60%)[]\n" +
                        "Impulse Damage:[] [pink]Max scaled by charge progress[]\n" +
                        "Effective Range:[] [orange]360 pixels [lime](+12.5%)[]\n\n" +
                        "[purple]🔥 THERMAL CIRCUIT & SHIELD MECHANICS:[]\n" +
                        "• [lightgray]Rapid Fire:[] Charge threshold reduced to [yellow]50%[] for fast fire cycles.\n" +
                        "• [lightgray]Overloaded Circuit:[] Energy shield core reduced to [purple]6,000 HP[] (Recovers 12s after break). When broken, charge speed and firing cycle boost by [red]+500%[] (5x speed)." :
                        "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                        "[lightgray]Máu siêu gia cố:[] [green]880 HP [lime](+60%)[]\n" +
                        "Sát thương xung kích:[] [pink]Tối đa theo tiến trình nạp sạc[]\n" +
                        "Tầm bắn hiệu dụng:[] [orange]360 pixel [lime](+12.5%)[]\n\n" +
                        "[purple]🔥 CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH & KHIÊN:[]\n" +
                        "• [lightgray]Xả đạn thần tốc:[] Giới hạn sạc điểm giảm xuống mốc [yellow]50%[] hỗ trợ chu kỳ xả đạn cực nhanh.\n" +
                        "• [lightgray]Mạch Quá Tải (Overloaded):[] Lõi khiên năng lượng chủ động giảm tải chỉ còn [purple]6,000 HP[] (Hồi khiên sau 12 giây vỡ). Khi khiên vỡ, tốc độ sạc và chu kỳ nạp bắn tăng mạnh [red]+500%[] (Gấp 5 lần bình thường).";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true); cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            final boolean en = isEnglish();
            if (getTier() == 1) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(en))
                    .size(50, 40).tooltip(en ? "Upgrade Holyder turret" : "Nâng cấp tháp pháo Holyder");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(en
                    ? "[scarlet]SYSTEM HAS REACHED MAX EVOLUTION FOR THIS BRANCH![]"
                    : "[scarlet]HỆ THỐNG ĐÃ ĐẠT GIỚI HẠN TIẾN HÓA CỦA NHÁNH ĐÃ CHỌN![]"))
                    .size(50, 40).tooltip(en ? "Reached max tier" : "Đã đạt cấp tối đa");
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(en))
                .size(50, 40).tooltip(en ? "View detailed system stats" : "Xem thông số chi tiết hệ thống");
        }

        void resetTargetsAndCharge() {
            target = null;
            secondaryTarget = null;
            isNewTargetLocking = false;
            laserCharge = Math.max(laserCharge - (Time.delta / 60f) * 15f, 0f);
            lockTimer = 0f;
            shieldActive = false;
            shieldVisualScale = Math.max(shieldVisualScale - Time.delta * 0.05f, 0f);
        }

        /** JS không gọi super$updateTile: toàn bộ ngắm/bắn do đoạn này tự xử lý. */
        @Override
        public void updateTile() {
            if (!enabled) return;

            int tier = getTier();
            maxShield = tier == 3 ? 6000f : 54000f;

            if (shieldHitTime > 0f) shieldHitTime -= Time.delta;

            if (shootCooldown > 0f) {
                if (tier == 3 && isShieldBroken) shootCooldown -= Time.delta * 5f;
                else shootCooldown -= Time.delta;
            }

            if (isShieldBroken) {
                regenTimer += Time.delta;
                if (regenTimer >= maxRegenCooldown) {
                    isShieldBroken = false;
                    shieldActive = true;
                    shieldHealth = tier == 3 ? 1200f : 6000f;
                    regenTimer = 0f;
                }
            }

            final float range = ((PowerTurret) block).range;
            if (power != null && power.status > 0f) {
                final Teamc currentTarget = Units.closestTarget(team, x, y, range, u -> u != null && !u.dead && !u.isFlying(), b -> b != null && !b.dead());

                if (currentTarget != null && !isDead(currentTarget)) {
                    if (target != currentTarget) {
                        target = currentTarget;
                        isNewTargetLocking = true;
                    }

                    // JS truyền cả đối tượng vào turnToTarget(float) (không hợp lệ); ý định là quay về hướng mục tiêu. Bài học 30.
                    turnToTarget(Angles.angle(x, y, currentTarget.getX(), currentTarget.getY()));

                    if (tier == 2) {
                        secondaryTarget = Units.closestTarget(team, x, y, range,
                            u -> u != null && !u.dead && u != currentTarget && !u.isFlying(),
                            b -> b != null && !b.dead() && b != currentTarget);
                    } else {
                        secondaryTarget = null;
                    }

                    if (!shieldActive && !isShieldBroken) {
                        shieldActive = true;
                        if (shieldHealth <= 0f) shieldHealth = tier == 3 ? 1200f : 6000f;
                    }

                    if (shieldActive && shieldHealth > 0f && !isShieldBroken) shieldVisualScale = Math.min(shieldVisualScale + Time.delta * 0.08f, 1f);
                    else shieldVisualScale = Math.max(shieldVisualScale - Time.delta * 0.08f, 0f);

                    float maxCharge = tier == 2 ? 200f : (tier == 3 ? 50f : 100f);
                    float chargeSpeed = (tier == 3 && isShieldBroken) ? 5f : 1f;
                    laserCharge = Math.min(laserCharge + (Time.delta / 60f) * 10f * chargeSpeed, maxCharge);

                    if (Mathf.chance(0.12)) Fx.lightningCharge.at(x + Mathf.range(6f), y + Mathf.range(6f), bulletColor);

                    if (shootCooldown <= 0f) {
                        isNewTargetLocking = false;
                        if (tier == 3 && isShieldBroken) lockTimer += Time.delta * 5f;
                        else lockTimer += Time.delta;

                        if (lockTimer >= 60f) {
                            lockTimer = 0f;
                            shootCooldown = 60f;
                            animRecoil = 1f;
                            // JS còn gán this.recoil = 1.0: thuộc tính này không có tác dụng trên Build ở v157 nên bỏ (bài học 30).

                            float angle = Angles.angle(x, y, currentTarget.getX(), currentTarget.getY());
                            float finalDamage = 380f * (1f + laserCharge / 100f);
                            if (Mathf.chance(0.9)) {
                                finalDamage *= 3.5f;
                                Fx.hitLaserBlast.at(x + Angles.trnsx(angle, 24f), y + Angles.trnsy(angle, 24f), angle, bulletColor);
                            }

                            BulletType bulletType = tier == 3 ? mainBulletMK2B : mainBullet;
                            Bullet bullet = bulletType.create(this, team, x, y, angle);
                            if (bullet != null) bullet.damage = finalDamage;

                            if (tier == 2 && secondaryTarget != null) {
                                float subAngle = Angles.angle(x, y, secondaryTarget.getX(), secondaryTarget.getY());
                                Bullet subBullet = secondaryBullet.create(this, team, x, y, subAngle);
                                if (subBullet != null) subBullet.damage = finalDamage * 0.5f;
                            }

                            if (shieldActive && !isShieldBroken && shieldHealth > 0f) {
                                shieldHealth = Math.min(shieldHealth + 2700f, maxShield);
                            }

                            muzzleFlashFX.at(x, y);
                            Effect.shake(2.5f + laserCharge / 30f, 2.5f + laserCharge / 30f, x, y);
                        }
                    } else {
                        lockTimer = 0f;
                    }
                } else {
                    resetTargetsAndCharge();
                }
            } else {
                resetTargetsAndCharge();
            }

            if (shieldActive && shieldHealth > 0f && !isShieldBroken) {
                final float currentRadius = shieldRadius * shieldVisualScale;
                Groups.bullet.intersect(x - currentRadius, y - currentRadius, currentRadius * 2f, currentRadius * 2f, b -> {
                    if (b.team != team && b.type != null && Mathf.dst(x, y, b.x, b.y) <= currentRadius) {
                        shieldHealth -= b.damage;
                        shieldHitTime = 6f;
                        shieldHitFX.at(b.x, b.y, currentRadius);
                        b.remove();

                        if (shieldHealth <= 0f) {
                            shieldHealth = 0f;
                            shieldActive = false;
                            isShieldBroken = true;
                            regenTimer = 0f;
                            Fx.shieldBreak.at(x, y, currentRadius, bulletColor);
                        }
                    }
                });
            }
        }

        // ------------------------------------------------------------------ vẽ

        void drawExpandingBarrels() {
            animRecoil = Mathf.lerpDelta(animRecoil, 0f, 0.08f);
            float openOffset = animRecoil * 12f;
            float baseAngle = rotation;
            Draw.z(Layer.turret + 0.1f);
            String[] spriteNames = {"holyderwing2", "holyderwing1", "holyderwing3", "holyderwing4"};
            for (int i = 0; i < 4; i++) {
                float finalAngle = baseAngle + 45f + i * 90f;
                float drawX = x + Angles.trnsx(finalAngle, openOffset);
                float drawY = y + Angles.trnsy(finalAngle, openOffset);
                TextureRegion region = PerfCore.region("newex-" + spriteNames[i]);
                if (!region.found()) region = PerfCore.region(spriteNames[i]);
                if (region.found()) Draw.rect(region, drawX, drawY, baseAngle);
            }
            Draw.reset();
        }

        void drawGatlingRotation() {
            float rotationSpeed = 2f + laserCharge * 0.18f;
            float currentRotation = (Time.time * rotationSpeed) % 360f;
            Draw.z(Layer.bullet + 1f);
            Draw.color(Color.white);
            float gunX = x + Angles.trnsx(rotation, 11f), gunY = y + Angles.trnsy(rotation, 11f);
            Fill.circle(gunX, gunY, 2.5f);
            Draw.color(bulletColor);
            for (int i = 0; i < 4; i++) {
                float orbitAngle = currentRotation + i * 90f;
                Fill.circle(gunX + Angles.trnsx(orbitAngle, 4f), gunY + Angles.trnsy(orbitAngle, 4f), 1.2f);
            }
            Draw.reset();
        }

        void drawHexagonAperture() {
            float scale = 1f - (lockTimer / 60f * 0.4f);
            if (lockTimer == 0f && target != null) scale = 1.6f;
            float apertureRadius = (7f * scale) * shieldVisualScale;
            float spinAngle = -Time.time * 1.5f;
            Draw.z(Layer.bullet + 1f);
            Draw.color(bulletColor, 0.55f);
            Lines.stroke(1f);
            Lines.poly(x + Angles.trnsx(rotation, 13f), y + Angles.trnsy(rotation, 13f), 6, apertureRadius, spinAngle);
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();
            int tier = getTier();

            if (target != null && !isDead(target) && laserCharge > 0f && !isNewTargetLocking) {
                Draw.z(Layer.bullet + 1f);
                float finalThinStroke = 0.2f + Mathf.absin(Time.time, 2.5f, 0.05f);
                Draw.color(laserAimColor);
                Lines.stroke(finalThinStroke);
                Lines.line(x, y, target.getX(), target.getY());
                if (tier == 2 && secondaryTarget != null && !isDead(secondaryTarget)) {
                    Draw.color(secondaryLaserAimColor);
                    Lines.stroke(finalThinStroke * 0.8f);
                    Lines.line(x, y, secondaryTarget.getX(), secondaryTarget.getY());
                }
                Draw.reset();
            }

            if (shieldVisualScale > 0f && shieldHealth > 0f && !isShieldBroken) {
                Draw.z(Layer.bullet + 2f);
                float currentRadius = shieldRadius * shieldVisualScale;
                Draw.color(bulletColor, 0.12f + Mathf.absin(Time.time, 3f, 0.04f));
                Fill.circle(x, y, currentRadius);
                Draw.color(shieldHitTime > 0f ? Color.white : bulletColor, 0.7f);
                Lines.stroke(1.2f);
                Lines.circle(x, y, currentRadius);
                Draw.reset();
            }

            if (laserCharge > 0f) {
                drawExpandingBarrels();
                drawGatlingRotation();
                drawHexagonAperture();

                Draw.z(Layer.effect + 10f);
                Draw.color(Color.white);
                Font font = Fonts.outline;
                float prevScale = font.getData().scaleX; // JS không trả lại cỡ chữ → làm nhỏ mọi chữ outline toàn game; Java khôi phục (bài học 30).
                font.getData().setScale(0.12f);
                font.draw((int) Math.floor(laserCharge) + "%", x, y, Align.center);

                float barWidth = 28f;
                if (isShieldBroken) {
                    int timeLeft = (int) Math.ceil((maxRegenCooldown - regenTimer) / 60f);
                    font.draw("[scarlet]OVERLOADED (5x ATK):[] " + timeLeft + "s", x, y - 20f, Align.center);
                    float progress = regenTimer / maxRegenCooldown;
                    Draw.color(Color.black, 0.5f); Lines.stroke(3f);
                    Lines.line(x - barWidth / 2f, y - 28f, x + barWidth / 2f, y - 28f);
                    Draw.color(Color.cyan, 0.7f); Lines.stroke(2f);
                    Lines.line(x - barWidth / 2f, y - 28f, x - barWidth / 2f + barWidth * progress, y - 28f);
                } else if (shieldHealth > 0f) {
                    float progress = shieldHealth / maxShield;
                    Draw.color(Color.black, 0.5f); Lines.stroke(3f);
                    Lines.line(x - barWidth / 2f, y - 28f, x + barWidth / 2f, y - 28f);
                    Draw.color(bulletColor); Lines.stroke(2f);
                    Lines.line(x - barWidth / 2f, y - 28f, x - barWidth / 2f + barWidth * progress, y - 28f);
                }
                font.getData().setScale(prevScale);
                Draw.reset();
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
