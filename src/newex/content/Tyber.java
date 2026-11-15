package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.audio.Sound;
import arc.scene.ui.ScrollPane;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootPattern;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ tyber.js. Pháo Tyber: phóng tên lửa phụ + xung nổ quanh tâm, nâng cấp MK2 / MK2B. */
public final class Tyber {
    private Tyber() {}

    private static final int[] REQ_MK2 = {4000, 4000, 0};      // titanium, silicon, plastanium
    private static final int[] REQ_MK2B = {6250, 8200, 4100};

    private static Effect explosionFx;
    private static Effect explosionBigFx;
    private static ShootPattern shootMK1, shootMK2, shootMK2B;

    private static Sound sShootSecondary, sExplosionNormal, sExplosionBig;
    private static UnitType rocket2bUnit;
    private static ItemTurret base;

    private static boolean en() {
        String l = Core.settings.getString("locale", "");
        return l != null && l.startsWith("en");
    }

    private static String t(String enText, String viText) {
        return en() ? enText : viText;
    }

    public static void init() {
        explosionFx = new Effect(40f, e -> {
            Draw.color(PerfCore.color("d1efff"), PerfCore.color("66b1ff"), e.fin());
            Lines.stroke(3f * e.fout());
            Lines.circle(e.x, e.y, 80f * e.fin());
            Angles.randLenVectors(e.id, 15, 80f * e.fin(), e.rotation, 360f,
                (x, y) -> Fill.circle(e.x + x, e.y + y, 1f + 3f * e.fout()));
        });

        explosionBigFx = new Effect(50f, e -> {
            Draw.color(PerfCore.color("ff8a80"), PerfCore.color("ff1744"), e.fin());
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, 136f * e.fin());
            Angles.randLenVectors(e.id, 25, 136f * e.fin(), e.rotation, 360f,
                (x, y) -> Fill.circle(e.x + x, e.y + y, 1f + 4f * e.fout()));
        });

        ShootAlternate a = new ShootAlternate();
        a.shots = 3; a.shotDelay = 6; a.barrels = 3; a.spread = 5;
        shootMK1 = a;
        ShootAlternate b = new ShootAlternate();
        b.shots = 5; b.shotDelay = 4; b.barrels = 5; b.spread = 4;
        shootMK2 = b;
        shootMK2B = new ShootPattern();
        shootMK2B.shots = 1;

        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        Sound sShootMain = Vars.tree.loadSound("shootBig");
        sShootSecondary = Vars.tree.loadSound("rocklaunch");
        sExplosionNormal = Vars.tree.loadSound("explosion");
        sExplosionBig = Vars.tree.loadSound("explosionbig");

        UnitType mainRocket = Vars.content.getByName(ContentType.unit, "newex-tyberrocket");
        rocket2bUnit = Vars.content.getByName(ContentType.unit, "newex-tyberrocket2b");

        BasicBulletType bullet = new BasicBulletType();
        bullet.damage = 0f;
        bullet.speed = 0.01f;
        bullet.instantDisappear = true;
        if (sShootMain != null) bullet.shootSound = sShootMain;
        bullet.spawnUnit = mainRocket;
        bullet.shootEffect = Fx.shootBig;
        bullet.smokeEffect = Fx.shootSmokeMissile;

        Block blk = Vars.content.getByName(ContentType.block, "newex-tyber");
        if (!(blk instanceof ItemTurret)) return;
        base = (ItemTurret) blk;
        base.configurable = true;
        base.ammoTypes.put(Items.titanium, bullet);
        base.config(Integer.class, (TyberBuild tile, Integer value) -> {
            if (tile != null) tile.evolution = value;
        });
        base.buildType = () -> new TyberBuild(base);
    }

    public static class TyberBuild extends ItemTurret.ItemTurretBuild {
        float shootingTimer = 0f, lastRocketTime = 0f, lastExplosionTime = 0f;
        int evolution = 0;

        public TyberBuild(ItemTurret outer) {
            outer.super();
        }

        int lastAppliedEvo = -1;

        @Override
        public float range() {
            return evolution == 1 ? super.range() * 1.5f : super.range();
        }

        @Override
        public BulletType peekAmmo() {
            BulletType ammo = super.peekAmmo();
            if (ammo != null && lastAppliedEvo != evolution) {
                lastAppliedEvo = evolution;
                if (evolution == 1) { base.shoot = shootMK2; base.reload = 80f; }
                else if (evolution == 2) { base.shoot = shootMK2B; base.reload = 40f; }
                else { base.shoot = shootMK1; base.reload = 80f; }
            }
            return ammo;
        }

        private float[] customPos(float sOffset, float fOffset) {
            float r = rotation * Mathf.degRad;
            float cos = (float) Math.cos(r), sin = (float) Math.sin(r);
            return new float[]{x + (fOffset * cos - sOffset * sin), y + (fOffset * sin + sOffset * cos)};
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (isShooting() && hasAmmo() && isActive()) {
                shootingTimer += Time.delta;

                float rocketCooldown = evolution == 1 ? 240f : evolution == 2 ? 180f : 300f;
                if (shootingTimer - lastRocketTime >= rocketCooldown) {
                    lastRocketTime = shootingTimer;
                    if (rocket2bUnit != null) {
                        if (evolution == 2) {
                            float[] l = customPos(-6, 4), r = customPos(6, 4);
                            Unit ua = rocket2bUnit.spawn(team, l[0], l[1]);
                            if (ua != null) ua.rotation = rotation - 12f;
                            Unit ub = rocket2bUnit.spawn(team, r[0], r[1]);
                            if (ub != null) ub.rotation = rotation + 12f;
                        } else {
                            float[] m = customPos(0, 6);
                            Unit u = rocket2bUnit.spawn(team, m[0], m[1]);
                            if (u != null) u.rotation = rotation;
                        }
                        if (sShootSecondary != null) sShootSecondary.at(x, y, 1.2f, 0.8f);
                    }
                }

                float explosionCooldown = evolution == 1 ? 360f : evolution == 2 ? 180f : 420f;
                if (shootingTimer - lastExplosionTime >= explosionCooldown) {
                    lastExplosionTime = shootingTimer;
                    if (evolution == 2) {
                        explosionBigFx.at(x, y);
                        if (sExplosionBig != null) sExplosionBig.at(x, y);
                        Damage.damage(team, x, y, 1700f, 176f, false, true);
                        SplashFx.ring(x, y, 1700f);
                    } else {
                        explosionFx.at(x, y);
                        if (evolution == 1 && sExplosionBig != null) sExplosionBig.at(x, y);
                        else if (sExplosionNormal != null) sExplosionNormal.at(x, y);
                        Damage.damage(team, x, y, 1700f, 176f, false, true);
                    }
                }
            } else if (shootingTimer > 0f) {
                shootingTimer = 0f; lastRocketTime = 0f; lastExplosionTime = 0f;
            }
        }

        @Override
        public Object config() {
            return evolution;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(evolution);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            evolution = read.b();
        }

        private static String col(int have, int need) {
            return have >= need ? "[green]" : "[red]";
        }

        private String requirementText() {
            CoreBuild core = team.core();
            if (core == null) return t("[red]Team Core Not Found![]", "[red]Không tìm thấy Lõi Đội![]");
            int ti = core.items.get(Items.titanium), si = core.items.get(Items.silicon), pl = core.items.get(Items.plastanium);
            return t("[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Branch:[]\n", "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh MK2:[]\n")
                + t(" • Titanium: ", " • Titan: ") + col(ti, REQ_MK2[0]) + ti + "[] / " + REQ_MK2[0] + "\n"
                + " • Silicon: " + col(si, REQ_MK2[1]) + si + "[] / " + REQ_MK2[1] + "\n"
                + t("[purple]MK2B Branch:[]\n", "[purple]Nhánh MK2B:[]\n")
                + t(" • Titanium: ", " • Titan: ") + col(ti, REQ_MK2B[0]) + ti + "[] / " + REQ_MK2B[0] + "\n"
                + " • Silicon: " + col(si, REQ_MK2B[1]) + si + "[] / " + REQ_MK2B[1] + "\n"
                + t(" • Plastanium: ", " • Nhựa: ") + col(pl, REQ_MK2B[2]) + pl + "[] / " + REQ_MK2B[2];
        }

        private boolean canPay(CoreBuild core, int[] req) {
            return core != null && core.items.get(Items.titanium) >= req[0]
                && core.items.get(Items.silicon) >= req[1] && core.items.get(Items.plastanium) >= req[2];
        }

        private void pay(CoreBuild core, int[] req) {
            NexNet.take(core, Items.titanium, req[0]);
            NexNet.take(core, Items.silicon, req[1]);
            if (req[2] > 0) NexNet.take(core, Items.plastanium, req[2]);
        }

        private Table branch(String title, String desc, String btn, int[] req, int target, BaseDialog dialog, arc.graphics.Color unused) {
            Table b = new Table();
            b.background(Styles.black6);
            b.margin(12);
            b.add(title).row();
            b.add(desc).width(340).get().setWrap(true);
            ((arc.scene.ui.Label) b.getCells().peek().get()).setAlignment(Align.left);
            b.row();
            b.button(btn, () -> {
                CoreBuild core = team.core();
                if (canPay(core, req)) {
                    pay(core, req);
                    NexNet.fx(target == 1 ? Fx.upgradeCore : Fx.bigShockwave, x, y);
                    NexNet.configure(this, Integer.valueOf(target));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(target == 1
                        ? t("[red]Not enough resources for MK2![]", "[red]Không đủ tài nguyên cho cấu hình MK2![]")
                        : t("[red]Not enough resources for MK2B![]", "[red]Không đủ tài nguyên cho cấu hình MK2B![]"));
                }
            }).size(180f, 38f);
            return b;
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(t("Tyber Turret Upgrade Center", "Trung tâm nâng cấp pháo Tyber"));
            dialog.cont.add(requirementText()).width(360f).get().setWrap(true);
            ((arc.scene.ui.Label) dialog.cont.getCells().peek().get()).setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branches = new Table();
            Table b1 = branch("[cyan]===(MK2)===[]",
                t("Upgrades system to 5 alternating barrels:\n [white]• Total range increased by [green]+50%[] reaching [orange]930 pixels[].[]\n [white]• Auto-fires long-range light homing missiles (Speed: 3).[]\n [white]• Missile impacts unleash large AOE up to [yellow]300 radius[] crowd control.[]\n [white]• Center burst pulse recharge time reduced to [pink]6.0s[].[]",
                  "Cải tiến hệ thống lên 5 nòng xả đạn luân phiên:\n [white]• Tầm bắn tổng lực gia tăng thêm [green]+50%[] đạt mốc [orange]930 pixel[].[]\n [white]• Tự động kích hoạt phóng Tên lửa tầm nhiệt hạng nhẹ tầm xa (Tốc độ: 3).[]\n [white]• Tên lửa va chạm giải phóng bão kích diện rộng lên tới [yellow]300 bán kính[] để quét sạch đám đông.[]\n [white]• Thời gian sạc xung nổ đại địa tại tâm rút xuống còn [pink]6.0 giây[].[]"),
                t("[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), REQ_MK2, 1, dialog, null);
            Table b2 = branch("[purple]===(MK2B)===[]",
                t("Compresses fire into a single concentrated core barrel:\n [white]• Core reload speed massively boosted to [green]40 ticks[] (2x Fire rate).[]\n [white]• Fires dual high-speed side missiles (Speed: 6) every [pink]3.0s[].[]\n [white]• Concentrated blast deals [red]1350 crit damage[] in small [yellow]30 radius[].[]\n [white]• Center burst pulse charges in [pink]3.0s[], releasing nuclear shockwaves.[]",
                  "Nén loạt bắn thành 1 nòng tâm bạo kích hội tụ:\n [white]• Tốc độ nạp đạn cốt lõi gia tăng thần tốc, giảm Reload xuống [green]40 tích tắc[] (Bắn nhanh gấp đôi).[]\n [white]• Khai hỏa đồng thời cặp tên lửa sườn xé gió siêu tốc (Tốc độ: 6) định kỳ mỗi [pink]3.0 giây[].[]\n [white]• Đòn nổ tập trung nén điểm gây [red]1350 sát thương bạo kích[] trong bán kính hẹp [yellow]30[].[]\n [white]• Xung nổ hỏa lực tại tâm nạp siêu tốc [pink]3.0 giây/lần[], giải phóng sóng chấn động tỏa hạt đỏ hạt nhân.[]"),
                t("[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), REQ_MK2B, 2, dialog, null);

            branches.add(b1).width(340f).row();
            branches.add().height(12f).row();
            branches.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        private String statsText(int ev) {
            boolean e1 = ev == 1;
            if (ev == 2) {
                return t("[purple]⚡ VARIANT STATS (MK2B) ⚡[]\n[lightgray]Turret Health:[] [green]1413 HP[]\n[lightgray]Effective Range:[] [orange]620 pixels[]\n\n[purple]🔥 CONCENTRATED HEAVY FIREPOWER:[]\n• [lightgray]Core Barrel Mode:[] Focuses fire into [red]1 single crit bullet[], reducing reload to [green]40 ticks[] (Double fire rate).\n\n[lime]⚡ AUTOMATIC SECONDARY SYSTEM (MK2B):[]\n• [lightgray]Dual Crit Side Missiles (Speed 6):[] Fires [yellow]2 side missiles at 12° angle[] simultaneously every [green]3.0s[]. Concentrated hit deals massive [red]1350 crit damage[] in small [lightgray]30[] area.\n• [lightgray]Rapid Ground Pulse:[] Center burst recharge time reduced to [green]3.0s/pulse[], releasing nuclear shockwaves dealing [red]1700 Area Damage[] within [lightgray]176 tiles[].",
                    "[purple]⚡ THÔNG SỐ BIẾN THỂ (MK2B) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]1413 HP[]\n[lightgray]Tầm bắn hiệu dụng:[] [orange]620 pixel[]\n\n[purple]🔥 HỎA LỰC HỘI TỤ SIÊU TRỌNG:[]\n• [lightgray]Chế độ nòng tâm:[] Gom hỏa lực xả [red]1 viên bạo kích duy nhất[] tập trung, giảm Reload xuống chỉ còn [green]40 tích tắc[] (Tốc độ bắn nhanh gấp đôi).\n\n[lime]⚡ HỆ THỐNG PHỤ TRỢ TỰ ĐỘNG (MK2B):[]\n• [lightgray]Cặp tên lửa bạo kích phụ (Tốc độ 6):[] Khai hỏa đồng thời [yellow]2 tên lửa phụ dạt góc 12°[] liên tục mỗi [green]3.0 giây[]. Đòn nổ nén điểm tập trung gây [red]1350 sát thương bạo kích[] cực lớn trong phạm vi hẹp [lightgray]30[].\n• [lightgray]Xung nổ đại địa cấp tốc:[] Tốc độ nạp xung kích tại tâm rút ngắn kỷ lục xuống còn [green]3.0 giây/lần[], giải phóng sóng chấn động tỏa hạt đỏ hạt nhân, gây [red]1700 Sát thương[] diện rộng phạm vi [lightgray]176 ô[].");
            }
            String head = ev == 0 ? t("[gold]⚡ BASE STATS (MK1) ⚡[]\n", "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n")
                                  : t("[cyan]⚡ BASE STATS (MK2) ⚡[]\n", "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n");
            return head + t(
                "[lightgray]Turret Health:[] [green]1413 HP[]\n[lightgray]Effective Range:[] " + (e1 ? "[orange]930 pixels [lime](+50%)[]" : "[orange]620 pixels[]") + "\n\n[sky]⚡ MAIN FIREPOWER MECHANIC:[]\n• [lightgray]Barrel System:[] " + (e1 ? "Upgraded [cyan]5 parallel barrels[] alternating fire (Delay: 4)." : "Default [yellow]3 core barrels[] alternating fire (Delay: 6).") + "\n• [lightgray]Main Ammo (Titanium):[] Summons long-range Tyber Heavy Missiles.\n\n[lime]⚡ AUTOMATIC SECONDARY SYSTEM:[]\n• [lightgray]Secondary Homing Missile (Speed 3):[] Launches side homing missiles every " + (e1 ? "[green]4.0s[]" : "[yellow]5.0s[]") + ". Impact creates massive AOE explosion up to [orange]300 radius[] (Blast damage: 235).\n• [lightgray]Ground Blast Pulse:[] Detonates core fusion at center every " + (e1 ? "[green]6.0s[]" : "[yellow]7.0s[]") + ", dealing [red]1700 Area Damage[] within [lightgray]176 tiles[] to deter close threats.",
                "[lightgray]Máu tháp pháo:[] [green]1413 HP[]\n[lightgray]Tầm bắn hiệu dụng:[] " + (e1 ? "[orange]930 pixel [lime](+50%)[]" : "[orange]620 pixel[]") + "\n\n[sky]⚡ CƠ CHẾ HỎA LỰC CHÍNH:[]\n• [lightgray]Hệ thống nòng súng:[] " + (e1 ? "Cải tiến [cyan]5 nòng song song[] bắn luân phiên liên tục (Giãn cách: 4)." : "Mặc định gồm [yellow]3 nòng cốt lõi[] xả loạt đạn luân phiên (Giãn cách: 6).") + "\n• [lightgray]Đạn chính (Titanium):[] Triệu hồi Tên lửa hạng nặng Tyber vọt tầm xa.\n\n[lime]⚡ HỆ THỐNG PHỤ TRỢ TỰ ĐỘNG:[]\n• [lightgray]Tên lửa định vị phụ (Tốc độ 3):[] Phóng tầm nhiệt sườn định kỳ mỗi " + (e1 ? "[green]4.0 giây[]" : "[yellow]5.0 giây[]") + ". Khi chạm nổ giải phóng phá hủy diện rộng cực đại lên tới [orange]300 bán kính[] (Sát thương nổ: 235).\n• [lightgray]Xung nổ đại địa:[] Kích nổ lõi nhiệt hạch tại tâm pháo định kỳ mỗi " + (e1 ? "[green]6.0 giây[]" : "[yellow]7.0 giây[]") + ", gây [red]1700 Sát thương diện rộng[] trong phạm vi [lightgray]176 ô[] giúp chống tiếp cận.");
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            final int ev = evolution;

            if (ev == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog)
                    .size(50f, 40f).tooltip(t("Upgrade Tyber turret structure", "Nâng cấp cấu trúc tháp pháo Tyber"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40f, () -> Vars.ui.showInfo(ev == 1
                    ? t("[cyan]SYSTEM OPERATING AT TYBER MK2 CONFIG![]", "[cyan]HỆ THỐNG ĐANG HOẠT ĐỘNG Ở CẤU HÌNH TYBER MK2![]")
                    : t("[orange]SYSTEM OPERATING AT TYBER MK2B CONFIG![]", "[orange]HỆ THỐNG ĐANG HOẠT ĐỘNG Ở CẤU HÌNH TYBER MK2B![]")))
                    .size(50f, 40f).tooltip(t("Evolution branch locked", "Đã khóa nhánh tiến hóa"));
            }

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String title = t(" Tyber Turret Stats: ", " Thông số pháo Tyber: ")
                    + (ev == 0 ? "[yellow](MK1)[]" : ev == 1 ? "[cyan](MK2)[]" : "[purple](MK2B)[]");
                BaseDialog dialog = new BaseDialog(title);
                Table info = new Table();
                info.add(statsText(ev)).width(360f).get().setWrap(true);
                ((arc.scene.ui.Label) info.getCells().peek().get()).setAlignment(Align.left);
                ScrollPane scroll = new ScrollPane(info);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton();
                dialog.show();
            }).size(50f, 40f).tooltip(t("View detailed status stats", "Xem chi tiết thông số trạng thái"));
        }
    }
}
