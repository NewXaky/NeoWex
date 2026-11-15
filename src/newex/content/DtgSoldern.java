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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ dtg-soldern.js. Pháo DTG Soldern: đạn đơn tầm xa, shotgun + vuốt khi địch áp sát,
 * khiên lục giác chặn đạn phía trước khi đang bắn; nâng cấp MK2 / MK2B.
 * Block do Java tạo (`new ItemTurret`) → dtg-soldern.hjson không có `type:` cấp cao nhất (bài 29a).
 */
public final class DtgSoldern {
    private DtgSoldern() {}

    /** Giữ dạng `reqMK2.titanium` để chép nguyên văn khối chuỗi UI (bài 60). */
    static final class Req {
        final int titanium, silicon, plastanium;
        Req(int titanium, int silicon, int plastanium) {
            this.titanium = titanium; this.silicon = silicon; this.plastanium = plastanium;
        }
    }

    static final Req reqMK2 = new Req(400, 400, 0);
    static final Req reqMK2B = new Req(880, 640, 420);

    static final float maxShieldRadius = 48f;
    static final float shieldAngleWidth = 120f;
    static final float zoomSpeed = 0.08f;
    static final float REGEN_COOLDOWN = 60f * 10f;
    static final float hexSize = 5f;

    // int để nối chuỗi ra "5000" (không phải "5000.0", bài 21)
    static final int shieldHealthMK1 = 5000;
    static final int shieldHealthMK2 = 6500;
    static final int shieldHealthMK3 = 12000;

    static final int turretHealthMK1 = 5500;
    static final int turretHealthMK2 = 7150;
    static final int turretHealthMK3 = 11000;

    static final Color COL_RED = Color.valueOf("ff1744");
    static final Color COL_CYAN = Color.valueOf("00bcd4");
    static final Color COL_ORANGE = Color.valueOf("ff9d00");

    private static Effect dtgHitEffectMk1, dtgHitEffectMk2, dtgHitEffectMk2b;
    private static Effect dtgSprayHitEffect, dtgSprayHitEffectMk2, dtgSprayHitEffectMk2b;
    private static BasicBulletType dtgSoldernNormalBullet, dtgSoldernSmallSprayBullet;
    private static BasicBulletType dtgSoldernmk2NormalBullet, dtgSoldernmk2SmallSprayBullet;
    private static BasicBulletType dtgSoldernmk2bSmallSprayBullet, spraySpreadB;
    private static LaserBulletType laserBulletB;
    private static Sound shootSoundAetherod;
    private static ItemTurret turret;

    // Vùng ảnh: tra một lần ở lần vẽ đầu (bài 67: tiền tố "newex-" kèm dự phòng không tiền tố).
    private static boolean regionsReady = false;
    private static TextureRegion rBody, rSubLeft, rSubRight, rSubFallback, rMain, rBarrel, rB1, rClawR1, rClawR2, rTop;

    private static TextureRegion reg(String n) { return PerfCore.region("newex-" + n, n); }

    private static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    static int getMaxShieldHealth(int tier) {
        if (tier == 1) return shieldHealthMK2;
        if (tier == 2) return shieldHealthMK3;
        return shieldHealthMK1;
    }

    static int getMaxTurretHealth(int tier) {
        if (tier == 1) return turretHealthMK2;
        if (tier == 2) return turretHealthMK3;
        return turretHealthMK1;
    }

    static float getDynamicRange(int tier) {
        if (tier == 1) return 468f;
        if (tier == 2) return 252f;
        return 360f;
    }

    static float getAngleDiff(float angle1, float angle2) {
        float diff = (angle1 - angle2 + 180f) % 360f - 180f;
        if (diff < -180f) diff += 360f;
        return Math.abs(diff);
    }

    static void dashCircle(float x, float y, float radius, Color color) {
        Draw.color(color);
        int segments = 48;
        for (int i = 0; i < segments; i += 2) {
            float a1 = i / (float) segments * 360f;
            float a2 = (i + 1) / (float) segments * 360f;
            Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius),
                       x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
        Draw.reset();
    }

    private static Effect hitFx(float life, int tier, float damage) {
        return new Effect(life, e -> {
            float baseSize = 2f + damage * 0.03f;
            float progress = e.fin();
            float zoomRadius = baseSize * (2.2f - 1.9f * Interp.pow2Out.apply(progress));
            float innerRadius = baseSize * (0.3f + 0.5f * Interp.pow3Out.apply(progress));
            float alpha = e.fout();

            Color mainColor, secColor;
            if (tier == 0) {
                mainColor = Color.valueOf("ffa500");
                secColor = Color.valueOf("ffff00");
            } else if (tier == 1) {
                mainColor = Color.valueOf("ffff00");
                secColor = Color.valueOf("00ffff");
            } else {
                mainColor = Color.valueOf("00ffff");
                secColor = Color.valueOf("ffff00");
            }

            Draw.color(mainColor, alpha);
            Lines.stroke((0.5f * alpha) + 0.1f);
            Lines.poly(e.x, e.y, 6, zoomRadius);

            float secondaryZoom = baseSize * (2.6f - 2.1f * Interp.pow2Out.apply(progress));
            if (secondaryZoom > 0f) {
                Draw.color(secColor, alpha * 0.5f);
                Lines.stroke(0.3f * alpha);
                Lines.poly(e.x, e.y, 6, secondaryZoom);
            }

            Draw.color(secColor, alpha);
            Lines.stroke(0.4f * alpha);
            Lines.poly(e.x, e.y, 6, innerRadius);

            Draw.color(Color.white, alpha);
            Fill.poly(e.x, e.y, 6, baseSize * 0.25f * alpha);
            Draw.reset();
        });
    }

    private static BasicBulletType bullet(float speed, float damage, float width, float height, float lifetime,
                                          String front, String back, String trail,
                                          float trailWidth, int trailLength, Effect fx) {
        BasicBulletType b = new BasicBulletType();
        b.speed = speed;
        b.damage = damage;
        b.width = width;
        b.height = height;
        b.lifetime = lifetime;
        b.frontColor = Color.valueOf(front);
        b.backColor = Color.valueOf(back);
        b.trailColor = Color.valueOf(trail);
        b.trailWidth = trailWidth;
        b.trailLength = trailLength;
        b.hitEffect = fx;
        b.despawnEffect = fx;
        return b;
    }

    public static void create() {
        dtgHitEffectMk1 = hitFx(20f, 0, 220.5f);
        dtgHitEffectMk2 = hitFx(22f, 1, 196f);
        dtgHitEffectMk2b = hitFx(26f, 2, 427f);
        dtgSprayHitEffect = hitFx(14f, 0, 73.5f);
        dtgSprayHitEffectMk2 = hitFx(14f, 1, 107.1f);
        dtgSprayHitEffectMk2b = hitFx(14f, 2, 80.5f);

        dtgSoldernNormalBullet = bullet(7f, 220.5f, 9f, 22f, 52f, "#e0f7fa", "#00bcd4", "#80deea", 1.8f, 6, dtgHitEffectMk1);
        dtgSoldernSmallSprayBullet = bullet(8.5f, 73.5f, 3.5f, 9f, 12f, "#e0f7fa", "#00bcd4", "#80deea", 0.75f, 3, dtgSprayHitEffect);
        dtgSoldernmk2NormalBullet = bullet(10.4f, 196f, 9.5f, 24f, 45f, "#e0f7fa", "#00bcd4", "#80deea", 2.0f, 7, dtgHitEffectMk2);
        dtgSoldernmk2SmallSprayBullet = bullet(9.0f, 107.1f, 3.85f, 10f, 12f, "#e0f7fa", "#00bcd4", "#80deea", 0.82f, 4, dtgSprayHitEffectMk2);
        dtgSoldernmk2bSmallSprayBullet = bullet(8.5f, 80.5f, 3.5f, 9f, 14f, "#ff8a80", "#ff1744", "#ff5252", 0.8f, 3, dtgSprayHitEffectMk2b);
        spraySpreadB = bullet(11.0f, 80.5f, 4.0f, 12f, 20f, "#ff5252", "#b71c1c", "#ff1744", 1.0f, 5, dtgSprayHitEffectMk2b);

        laserBulletB = new LaserBulletType();
        laserBulletB.length = 252f;
        laserBulletB.damage = 427f;
        laserBulletB.width = 24f;
        laserBulletB.lifetime = 25f;
        laserBulletB.colors = new Color[]{Color.valueOf("#ff1744"), Color.valueOf("#b71c1c"), Color.white};
        laserBulletB.hitEffect = dtgHitEffectMk2b;
        laserBulletB.smokeEffect = Fx.smoke;

        shootSoundAetherod = Vars.tree.loadSound("aetherod1");

        turret = new ItemTurret("dtg-soldern") {
            @Override
            public void drawPlace(int x, int y, int rotation, boolean valid) {
                super.drawPlace(x, y, rotation, valid);
                Draw.color(Pal.remove);
                Lines.stroke(1.5f);
                dashCircle(x * Vars.tilesize, y * Vars.tilesize, 100f, Pal.remove);
                dashCircle(x * Vars.tilesize, y * Vars.tilesize, 145f, Pal.remove);
                Draw.reset();
            }
        };
        turret.squareSprite = false;
        turret.health = turretHealthMK1;
        turret.size = 3;
        turret.reload = 40f;
        turret.configurable = true;
        turret.category = Category.turret;
        turret.shootSound = shootSoundAetherod;
        turret.ammoTypes.put(Items.silicon, dtgSoldernNormalBullet);
        turret.config(Integer.class, (DtgSoldernBuild t, Integer v) -> {
            if (t != null) t.setTier(v);
        });
        turret.buildType = () -> new DtgSoldernBuild(turret);
    }

    public static class DtgSoldernBuild extends ItemTurret.ItemTurretBuild {
        int dtgSoldernInternalTier = 0;
        boolean wasInShotgunRange = false;
        boolean canInstantRegenShield = true;

        float mainWeaponVisual = 0f, subWeaponVisual = 0f;
        float sideRecoil = 0f, shotgunBarrelRecoil = 0f, mainWeaponRecoil = 0f;

        float shotgunMagTimer = 0f;
        int firedMagazines = 0;
        float shotgunReloadTimer = 0f;

        float shieldHealth = shieldHealthMK1;
        float shieldHitTime = 0f;
        float shieldScale = 0f;
        float regenTimer = REGEN_COOLDOWN;

        float energyBallCharge = 0f;
        float particleTimer = 0f;
        boolean hadEnergyBall = false;

        public DtgSoldernBuild(ItemTurret outer) {
            outer.super();
        }

        @Override
        public float range() {
            return getDynamicRange(getTier());
        }

        void setTier(int val) {
            dtgSoldernInternalTier = val;
            maxHealth = getMaxTurretHealth(val);
            health = maxHealth;
        }

        int getTier() { return dtgSoldernInternalTier; }

        /** Chữ "YÊU CẦU TÀI NGUYÊN" trong hộp nâng cấp (chép nguyên văn từ JS). */
        private String reqSnapshot() {
            CoreBuild core = team.core();
            if (core == null) return isEn() ? "[red]Team Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";

            int currentTitanium = core.items.get(Items.titanium);
            int currentSilicon = core.items.get(Items.silicon);
            int currentPlastanium = core.items.get(Items.plastanium);

            String titColor1 = currentTitanium >= reqMK2.titanium ? "[green]" : "[red]";
            String silColor1 = currentSilicon >= reqMK2.silicon ? "[green]" : "[red]";

            String titColor2 = currentTitanium >= reqMK2B.titanium ? "[green]" : "[red]";
            String silColor2 = currentSilicon >= reqMK2B.silicon ? "[green]" : "[red]";
            String plaColor2 = currentPlastanium >= reqMK2B.plastanium ? "[green]" : "[red]";

            if (isEn()) {
                        return "[yellow]CORE VAULT REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Branch:[]\n" +
                               " • Titanium: " + titColor1 + currentTitanium + "[] / " + reqMK2.titanium + "\n" +
                               " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                               "[purple]MK2B Branch:[]\n" +
                               " • Titanium: " + titColor2 + currentTitanium + "[] / " + reqMK2B.titanium + "\n" +
                               " • Silicon: " + silColor2 + currentSilicon + "[] / " + reqMK2B.silicon + "\n" +
                               " • Plastanium: " + plaColor2 + currentPlastanium + "[] / " + reqMK2B.plastanium;
            } else {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Titan: " + titColor1 + currentTitanium + "[] / " + reqMK2.titanium + "\n" +
                               " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Titan: " + titColor2 + currentTitanium + "[] / " + reqMK2B.titanium + "\n" +
                               " • Silicon: " + silColor2 + currentSilicon + "[] / " + reqMK2B.silicon + "\n" +
                               " • Nhựa: " + plaColor2 + currentPlastanium + "[] / " + reqMK2B.plastanium;
            }
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(isEn() ? "DTG Soldern Upgrade Center" : "Trung tâm nâng cấp pháo DTG Soldern");

            Cell<Label> reqCell = dialog.cont.add(reqSnapshot());
            reqCell.width(360f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branchesTable = new Table();

            Table b1 = new Table();
            b1.background(Styles.black6);
            b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
            String b1Text = isEn() ?
                    "Firepower Enhancement & Pulse Energy Upgrades:\n" +
                    " [white]• Structure HP greatly increased to [green]7150 HP[] (+30%).[]\n" +
                    " [white]• Shield core expanded, boosting matrix durability to [cyan]6500[].[]\n" +
                    " [white]• Long-range targeting drastically expanded to [green]468 pixels[] (+30%).[]\n" +
                    " [white]• Ranged ammo upgraded to MK2 variant (Speed: 10.4, Damage: 196).[]\n" +
                    " [white]• Close Combat: Retains 100 range, boosts shotgun damage to [yellow]107.1[].[]\n" +
                    " [white]• Special: Instantly recharges shield when target enters close-range for the first time.[]" :
                    "Cải tiến năng lực hỏa lực và năng lượng xung kích:\n" +
                    " [white]• Kết cấu HP pháo tăng mạnh lên [green]7150 HP[] (+30%).[]\n" +
                    " [white]• Lõi khiên mở rộng, gia tăng độ bền màng chắn lên [cyan]6500[].[]\n" +
                    " [white]• Tầm bắn tầm xa mở rộng vượt trội đạt [green]468 pixel[] (+30%).[]\n" +
                    " [white]• Đạn tầm xa nâng cấp lên dạng MK2 (Vận tốc: 10.4, Sát thương: 196).[]\n" +
                    " [white]• Cận chiến: Giữ nguyên cự ly áp sát 100, cường hóa đạn rải lên [yellow]107.1[].[]\n" +
                    " [white]• Cơ chế đặc biệt: Cho phép sạc đầy lại màng khiên tức thời khi mục tiêu lọt vào tầm gần lần đầu.[]";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f).get().setWrap(true);
            b1D.get().setAlignment(Align.left);
            b1.row();
            b1.button(isEn() ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.titanium) >= reqMK2.titanium && core.items.get(Items.silicon) >= reqMK2.silicon) {
                    NexNet.take(core, Items.titanium, reqMK2.titanium);
                    NexNet.take(core, Items.silicon, reqMK2.silicon);
                    NexNet.fx(Fx.upgradeCore, x, y);
                    NexNet.fx(Fx.mineHuge, x, y);
                    NexNet.shake(5f, 5f, x, y);
                    configure(Integer.valueOf(1));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(isEn() ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
                }
            }).size(180f, 38f);

            Table b2 = new Table();
            b2.background(Styles.black6);
            b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
            String b2Text = isEn() ?
                    "Complete Conversion to Compressed Heavy Laser Variant:\n" +
                    " [white]• Reinforced frame boosts turret HP to ultra tier [green]11000 HP[] (+100%).[]\n" +
                    " [white]• Shield matrix capacity maxes out at [cyan]12000[] health points.[]\n" +
                    " [white]• Ranged attack distance reduced to [red]252 pixels[], but converts to [orange]Piercing Laser Beams[] with [red]427 heavy damage[].[]\n" +
                    " [white]• Close Combat: Shotgun trigger threshold expanded to [yellow]145 pixels[].[]\n" +
                    " [white]• Hybrid Shotgun Fire: Simultaneously launches [scarlet]14 red pellets[] and [orange]6 wide-spread spread beams[] to clear enemies.[]" :
                    "Chuyển đổi toàn diện sang dạng Trọng Pháo Laser bạo nén:\n" +
                    " [white]• Kết cấu khung vững chắc nâng HP pháo lên mức siêu cấp [green]11000 HP[] (+100%).[]\n" +
                    " [white]• Lõi nén lá chắn hấp thụ đạt mốc tối đa [cyan]12000[] độ bền.[]\n" +
                    " [white]• Tầm bắn tầm xa thu hẹp còn [red]252 pixel[], bù lại chuyển sang dạng [orange]Chùm tia Laser xuyên phá[] tầm trung cực đại [red]427 sát thương[].[]\n" +
                    " [white]• Cận chiến: Tầm quét kích hoạt shotgun mở rộng lên [yellow]145 pixel[].[]\n" +
                    " [white]• Giải phóng hỏa lực shotgun hỗn hợp: Phóng đồng thời [scarlet]14 đạn rải đỏ[] kèm [orange]6 tia đạn tỏa diện rộng[] quét sạch vật cản xung quanh.[]";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f).get().setWrap(true);
            b2D.get().setAlignment(Align.left);
            b2.row();
            b2.button(isEn() ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.titanium) >= reqMK2B.titanium && core.items.get(Items.silicon) >= reqMK2B.silicon && core.items.get(Items.plastanium) >= reqMK2B.plastanium) {
                    NexNet.take(core, Items.titanium, reqMK2B.titanium);
                    NexNet.take(core, Items.silicon, reqMK2B.silicon);
                    NexNet.take(core, Items.plastanium, reqMK2B.plastanium);
                    NexNet.fx(Fx.bigShockwave, x, y);
                    NexNet.fx(Fx.mineHuge, x, y);
                    NexNet.shake(5f, 5f, x, y);
                    configure(Integer.valueOf(2));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(isEn() ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
                }
            }).size(180f, 38f);

            branchesTable.add(b1).width(340f);
            branchesTable.row();
            branchesTable.add().height(12f).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = isEn() ? " DTG Soldern Stats: " : " Thông số pháo DTG Soldern: ";
            String descStr = "";
            int currentTier = getTier();

            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = isEn() ?
                          "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "• [lightgray]Turret Health:[] [green]5500 HP[]\n" +
                          "• [lightgray]Shield Health:[] [cyan]" + shieldHealthMK1 + "[]\n" +
                          "• [lightgray]Effective Range:[] [orange]" + Math.round(range()) + " pixels[]\n" +
                          "• [lightgray]Ranged Damage:[] [yellow]220.5[] (Bullet Speed: 7)\n\n" +
                          "[sky]⚡ UTILITY MECHANICS:[]\n" +
                          "• [lightgray]Energy Shield:[] Automatically deploys a 120° shield blocking incoming projectiles in aiming direction.\n" +
                          "• [lightgray]Close Combat Mode (Shotgun):[] Deploys mechanical claws when enemies step within [yellow]100 pixels[]. Fires a burst of 14 pellets (Damage: 73.5) across 10 rapid magazines." :
                          "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "• [lightgray]Máu tháp pháo:[] [green]5500 HP[]\n" +
                          "• [lightgray]Độ bền lá chắn:[] [cyan]" + shieldHealthMK1 + "[]\n" +
                          "• [lightgray]Tầm bắn hiệu dụng:[] [orange]" + Math.round(range()) + " pixel[]\n" +
                          "• [lightgray]Sát thương tầm xa:[] [yellow]220.5[] (Tốc độ đạn: 7)\n\n" +
                          "[sky]⚡ CƠ CHẾ ĐA DỤNG:[]\n" +
                          "• [lightgray]Màng chắn năng lượng:[] Tự động bung góc chặn 120° theo hướng nòng súng khi bắn để triệt tiêu đạn địch.\n" +
                          "• [lightgray]Chế độ Cận chiến (Shotgun):[] Kích hoạt vuốt máy khi mục tiêu lọt vào cự ly [yellow]100 pixel[]. Xả loạt 14 đạn rải (Sát thương: 73.5) liên thanh liên tục trong 10 băng đạn.";
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = isEn() ?
                          "[cyan]⚡ UPGRADED CONFIGURATION STATS (MK2) ⚡[]\n" +
                          "• [lightgray]Turret Health:[] [green]7150 HP [yellow](+30%)[]\n" +
                          "• [lightgray]Shield Health:[] [cyan]" + shieldHealthMK2 + "[]\n" +
                          "• [lightgray]Effective Range:[] [orange]" + Math.round(range()) + " pixels [yellow](+30%)[]\n" +
                          "• [lightgray]Ranged Damage:[] [yellow]196.0[] (Velocity boosted: 10.4)\n\n" +
                          "[lime]⚡ ADDITIONAL UTILITY MECHANICS:[]\n" +
                          "• [lightgray]Instant Shield Recharge:[] Shield instantly recharges to full capacity when an enemy enters close-range for the first time.\n" +
                          "• [lightgray]Enhanced Shotgun:[] Significantly increases pellet shotgun damage to [yellow]107.1[]." :
                          "[cyan]⚡ THÔNG SỐ CẤU HÌNH NÂNG CẤP (MK2) ⚡[]\n" +
                          "• [lightgray]Máu tháp pháo:[] [green]7150 HP [yellow](+30%)[]\n" +
                          "• [lightgray]Độ bền lá chắn:[] [cyan]" + shieldHealthMK2 + "[]\n" +
                          "• [lightgray]Tầm bắn hiệu dụng:[] [orange]" + Math.round(range()) + " pixel [yellow](+30%)[]\n" +
                          "• [lightgray]Sát thương tầm xa:[] [yellow]196.0[] (Gia tốc đạn tăng vọt: 10.4)\n\n" +
                          "[lime]⚡ CƠ CHẾ ĐA DỤNG BỔ SUNG:[]\n" +
                          "• [lightgray]Nạp khiên tức thời:[] Khiên tự động sạc đầy lại hoàn toàn khi kẻ địch lần đầu tiên bước vào vùng cận chiến.\n" +
                          "• [lightgray]Hỏa lực shotgun cải tiến:[] Tăng mạnh sát thương mỗi viên đạn rải trong loạt tỏa cận chiến lên [yellow]107.1[].";
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = isEn() ?
                          "[purple]⚡ HEAVY GRAVITY VARIANT STATS (MK2B) ⚡[]\n" +
                          "• [lightgray]Turret Health:[] [green]11000 HP [yellow](+100%)[]\n" +
                          "• [lightgray]Shield Health:[] [cyan]" + shieldHealthMK3 + "[]\n" +
                          "• [lightgray]Effective Range:[] [orange]" + Math.round(range()) + " pixels [coral](Reduced)[]\n" +
                          "• [lightgray]Ranged Mode:[] Replaced with [orange]Destructive Laser Beam[] dealing [red]427 direct damage[].\n\n" +
                          "[purple]🔥 ULTIMATE CLOSE-RANGE POWER:[]\n" +
                          "• [lightgray]Expanded Shotgun Range:[] Threshold expanded to [yellow]145 pixels[].\n" +
                          "• [lightgray]Hybrid Firepower:[] Fires a combined combo of [scarlet]14 red pellets[] (Damage: 80.5) and [sharp]6 angled spread beams (12°)[] (Damage: 80.5) to obliterate nearby targets instantly." :
                          "[purple]⚡ THÔNG SỐ BIẾN THỂ TRỌNG LỰC (MK2B) ⚡[]\n" +
                          "• [lightgray]Máu tháp pháo:[] [green]11000 HP [yellow](+100%)[]\n" +
                          "• [lightgray]Độ bền lá chắn:[] [cyan]" + shieldHealthMK3 + "[]\n" +
                          "• [lightgray]Tầm bắn hiệu dụng:[] [orange]" + Math.round(range()) + " pixel [coral](Thu hẹp)[]\n" +
                          "• [lightgray]Chế độ tầm xa:[] Chuyển hẳn thành [orange]Laser hủy diệt[] nén điểm gây [red]427 sát thương[] trực diện.\n\n" +
                          "[purple]🔥 SỨC MẠNH ÁP SÁT TỐI THƯỢNG:[]\n" +
                          "• [lightgray]Mở rộng tầm Shotgun:[] Ngưỡng kích hoạt tăng lên [yellow]145 pixel[].\n" +
                          "• [lightgray]Hỏa lực hỗn hợp:[] Khai hỏa đồng loạt tổ hợp bạo kích gồm [scarlet]14 đạn rải đỏ[] (Sát thương: 80.5) đan xem [sharp]6 tia đạn tỏa chéo dạt góc 12°[] (Sát thương: 80.5) để xóa sổ quân địch trong tích tắc.";
            }

            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true);
            cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            int tier = getTier();

            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openUpgradeDialog)
                    .size(50f, 40f).tooltip(isEn() ? "Evolve DTG Soldern Turret" : "Tiến hóa tháp pháo DTG Soldern");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> {
                if(isEn()){
                    Vars.ui.showInfo(this.getTier() == 1 ? "[cyan]SYSTEM IS RUNNING ON MK2 STANDARD CONFIGURATION![]" : "[purple]SYSTEM IS RUNNING ON MK2B HEAVY GRAVITY LASER VARIANT![]");
                } else {
                    Vars.ui.showInfo(this.getTier() == 1 ? "[cyan]HỆ THỐNG ĐANG HOẠT ĐỘNG Ở CẤU HÌNH TIÊU CHUẨN MK2![]" : "[purple]HỆ THỐNG ĐANG HOẠT ĐỘNG Ở BIẾN THỂ TRỌNG LỰC LASER MK2B![]");
                }
                }).size(50f, 40f).tooltip(isEn() ? "Evolution branch locked" : "Hệ thống đã khóa nhánh tiến hóa");
            }

            table.button(Icon.info, Styles.cleari, 40, this::openInfoDialog)
                .size(50f, 40f).tooltip(isEn() ? "View Detailed System Stats" : "Xem chi tiết thông số trạng thái");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void updateTile() {
            int tier = getTier();

            super.updateTile();

            int currentMaxShield = getMaxShieldHealth(tier);
            boolean targetInShotgunRange = false;
            float activationThreshold = (tier == 2) ? 145f : 100f;

            if (target != null) {
                float distance = Mathf.dst(x, y, target.getX(), target.getY());
                if (distance < activationThreshold) {
                    targetInShotgunRange = true;
                }
            }

            float visualSpeed = 0.05f * Time.delta;
            if (targetInShotgunRange) {
                subWeaponVisual = Mathf.approach(subWeaponVisual, 1f, visualSpeed);
                mainWeaponVisual = Mathf.approach(mainWeaponVisual, 0f, visualSpeed);
            } else {
                subWeaponVisual = Mathf.approach(subWeaponVisual, 0f, visualSpeed);
                mainWeaponVisual = Mathf.approach(mainWeaponVisual, 1f, visualSpeed);
            }

            sideRecoil = Mathf.approach(sideRecoil, 0f, 0.08f * Time.delta);
            shotgunBarrelRecoil = Mathf.approach(shotgunBarrelRecoil, 0f, 0.12f * Time.delta);
            mainWeaponRecoil = Mathf.approach(mainWeaponRecoil, 0f, 0.12f * Time.delta);

            float rad = rotation * Mathf.degRad;
            float cosR = (float) Math.cos(rad);
            float sinR = (float) Math.sin(rad);
            float muzzleDist = 13f;
            float muzzleX = x + cosR * muzzleDist;
            float muzzleY = y + sinR * muzzleDist;

            float ballX = x - cosR * 12f;
            float ballY = y - sinR * 12f;
            Color ballColor = (tier == 2) ? COL_RED : COL_CYAN;

            if (subWeaponVisual >= 0.95f) {
                energyBallCharge = Mathf.approach(energyBallCharge, 1f, 0.05f * Time.delta);
                hadEnergyBall = true;
                particleTimer += Time.delta;

                if (shotgunReloadTimer > 0) {
                    if (particleTimer >= 7) {
                        particleTimer = 0f;
                        Fx.lightningCharge.at(ballX + Mathf.range(5f), ballY + Mathf.range(5f), rotation, ballColor);
                        if (Mathf.chance(0.4)) {
                            Fx.smoke.at(ballX, ballY, rotation, ballColor);
                        }
                    }
                } else {
                    if (particleTimer >= 30) {
                        particleTimer = 0f;
                        Fx.lightningCharge.at(ballX, ballY, rotation, ballColor);
                    }
                }
            } else {
                if (hadEnergyBall && subWeaponVisual < 0.2f) {
                    energyBallCharge = 0f;
                    hadEnergyBall = false;
                    Fx.shieldBreak.at(ballX, ballY, rotation, ballColor);
                } else {
                    energyBallCharge = Mathf.approach(energyBallCharge, 0f, 0.08f * Time.delta);
                }
            }

            if (targetInShotgunRange && !wasInShotgunRange) {
                if (tier == 1 && canInstantRegenShield && shieldHealth > 0) {
                    shieldHealth = currentMaxShield;
                    canInstantRegenShield = false;
                    Fx.shieldBreak.at(x, y);
                }
            }
            wasInShotgunRange = targetInShotgunRange;

            // JS: `this.isShooting` thiếu `()` (luôn truthy) → Java gọi isShooting() thật (bài 10/17/79).
            if (targetInShotgunRange && isShooting() && hasAmmo()) {
                if (shotgunReloadTimer > 0) {
                    shotgunReloadTimer -= Time.delta;
                } else {
                    shotgunMagTimer += Time.delta;
                    if (shotgunMagTimer >= 12) {
                        shotgunMagTimer = 0f;
                        shotgunBarrelRecoil = 1f;

                        if (shootSoundAetherod != null) shootSoundAetherod.at(x, y, Mathf.random(0.9f, 1.1f));

                        if (tier == 2) {
                            for (int i = 0; i < 14; i++) {
                                float angleOffset = Mathf.range(15f);
                                mindustry.gen.Bullet b = dtgSoldernmk2bSmallSprayBullet.create(this, team, muzzleX, muzzleY, rotation + angleOffset);
                                if (b != null) b.vel.setLength(Mathf.random(5f, 12f));
                            }
                            for (int j = 0; j < 6; j++) {
                                float angleOffset = (j - 2.5f) * 8f;
                                spraySpreadB.create(this, team, muzzleX, muzzleY, rotation + angleOffset);
                            }
                        } else {
                            BulletType baseBulletType = (tier == 1) ? dtgSoldernmk2SmallSprayBullet : dtgSoldernSmallSprayBullet;
                            for (int i = 0; i < 14; i++) {
                                float angleOffset = Mathf.range(15f);
                                mindustry.gen.Bullet b = baseBulletType.create(this, team, muzzleX, muzzleY, rotation + angleOffset);
                                if (b != null) b.vel.setLength(Mathf.random(5f, 12f));
                            }
                        }

                        Fx.shootBig.at(muzzleX, muzzleY, rotation);

                        if (ammo.size > 0) {
                            ItemTurret.ItemEntry entry = (ItemTurret.ItemEntry) ammo.peek();
                            entry.amount--;
                            totalAmmo--;
                            if (entry.amount <= 0) {
                                ammo.pop();
                            }
                        }

                        firedMagazines++;
                        if (firedMagazines >= 10) {
                            shotgunReloadTimer = 180f;
                            firedMagazines = 0;
                        }
                    }
                }
            } else {
                if (shotgunReloadTimer > 0) shotgunReloadTimer -= Time.delta;
            }

            if (targetInShotgunRange) {
                reloadCounter = 0f;
            } else {
                // block dùng chung nên các pháo khác tầng ghi đè nhau (giữ nguyên như JS, bài 49/76)
                turret.reload = (tier == 2) ? 60f : 40f;
            }

            if (shieldHealth < currentMaxShield) {
                regenTimer += Time.delta;
                if (regenTimer >= REGEN_COOLDOWN) {
                    shieldHealth = currentMaxShield;
                    regenTimer = REGEN_COOLDOWN;
                    if (tier == 1) canInstantRegenShield = true;
                    Fx.shieldBreak.at(x, y);
                }
            }

            // JS: `this.isShooting` thiếu `()` lần nữa → khiên luôn bung; Java: chỉ bung khi đang bắn (đúng mô tả hjson).
            if (isShooting() && shieldHealth > 0) {
                shieldScale = Math.min(1f, shieldScale + zoomSpeed * Time.delta);
            } else {
                shieldScale = Math.max(0f, shieldScale - zoomSpeed * Time.delta);
            }

            final float currentRadius = maxShieldRadius * shieldScale;

            if (shieldHealth > 0 && shieldScale > 0.2f) {
                Groups.bullet.intersect(x - currentRadius - 16f, y - currentRadius - 16f,
                    (currentRadius + 16f) * 2f, (currentRadius + 16f) * 2f, b -> {
                        if (b.team != team && b.type != null && b.type.damage > 0) {
                            float dst = Mathf.dst(x, y, b.x, b.y);
                            if (dst >= (currentRadius - 12f) && dst <= (currentRadius + 8f)) {
                                float angleToBullet = Mathf.angle(b.x - x, b.y - y);
                                float angleDiff = getAngleDiff(rotation, angleToBullet);
                                if (angleDiff <= (shieldAngleWidth / 2f)) {
                                    shieldHealth = Math.max(0f, shieldHealth - b.damage);
                                    shieldHitTime = 5f;
                                    regenTimer = 0f;
                                    if (shieldHealth <= 0) {
                                        Fx.shieldBreak.at(x, y);
                                    } else {
                                        dtgHitEffectMk1.at(b.x, b.y, rotation);
                                    }
                                    b.remove();
                                }
                            }
                        }
                    });
            }
            if (shieldHitTime > 0) shieldHitTime -= Time.delta;
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            float activationThreshold = (tier == 2) ? 145f : 100f;

            if (target != null) {
                float distance = Mathf.dst(x, y, target.getX(), target.getY());
                if (distance < activationThreshold) {
                    return;
                }
            }

            sideRecoil = 1f;
            mainWeaponRecoil = 1f;

            float rad = rotation * Mathf.degRad;
            float muzzleDist = 13f;
            float muzzleX = x + (float) Math.cos(rad) * muzzleDist;
            float muzzleY = y + (float) Math.sin(rad) * muzzleDist;

            if (tier == 2) {
                laserBulletB.create(this, team, muzzleX, muzzleY, rotation);
                Fx.lightningCharge.at(muzzleX, muzzleY);
            } else {
                BulletType activeNormalBullet = (tier == 1) ? dtgSoldernmk2NormalBullet : dtgSoldernNormalBullet;
                activeNormalBullet.create(this, team, muzzleX, muzzleY, rotation);
            }

            useAmmo();

            if (shootSoundAetherod != null) shootSoundAetherod.at(x, y, Mathf.random(0.9f, 1.1f));

            Fx.shootBig.at(muzzleX, muzzleY, rotation);
        }

        @Override
        public void write(Writes w) {
            super.write(w);
            w.b(getTier());
        }

        @Override
        public void read(Reads r, byte revision) {
            super.read(r, revision);
            setTier(r.b());
            shotgunMagTimer = 0f; firedMagazines = 0; shotgunReloadTimer = 0f;
            shieldHealth = getMaxShieldHealth(getTier());
            shieldScale = 0f; regenTimer = REGEN_COOLDOWN;
            wasInShotgunRange = false;
            canInstantRegenShield = true;
            mainWeaponVisual = 0f; subWeaponVisual = 0f;
            sideRecoil = 0f; shotgunBarrelRecoil = 0f; mainWeaponRecoil = 0f;
            energyBallCharge = 0f; particleTimer = 0f; hadEnergyBall = false;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            float currentThreshold = (getTier() == 2) ? 145f : 100f;
            dashCircle(x, y, currentThreshold, Pal.remove);
        }

        @Override
        public void draw() {
            if (!regionsReady) {
                regionsReady = true;
                rBody = reg("dtg-soldern-body");
                rSubLeft = reg("dtg-soldern-sub-left");
                rSubRight = reg("dtg-soldern-sub-right");
                rSubFallback = reg("dtg-soldern-sub");
                rMain = reg("dtg-soldern-main");
                rBarrel = reg("dtg-soldern-barrel");
                rB1 = reg("dtg-soldern-b1");
                rClawR1 = reg("dtg-soldern-claw-r1");
                rClawR2 = reg("dtg-soldern-claw-r2");
                rTop = reg("dtg-soldern-top");
            }

            // JS: `block.basePrefix` không tồn tại → vùng "undefinedblock-3" không bao giờ thấy → luôn super.draw() (bài 13).
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cosR = (float) Math.cos(rad);
            float sinR = (float) Math.sin(rad);

            float maxSideRecoilDistance = -4f;
            float maxBarrelRecoilDistance = -4.5f;

            if (block.region.found()) {
                Draw.rect(block.region, x, y, rotation);
            }

            if (rBody.found()) {
                Draw.rect(rBody, x, y, rotation);
            }

            float spreadX = subWeaponVisual * 5f;
            if (rSubLeft.found() && rSubRight.found()) {
                float lx = x - spreadX * sinR;
                float ly = y + spreadX * cosR;
                Draw.rect(rSubLeft, lx, ly, rotation);

                float rx = x + spreadX * sinR;
                float ry = y - spreadX * cosR;
                Draw.rect(rSubRight, rx, ry, rotation);
            } else {
                if (rSubFallback.found()) Draw.rect(rSubFallback, x, y, rotation);
            }

            float pushY = mainWeaponVisual * 4.5f;
            if (rMain.found()) {
                float mx = x + pushY * cosR;
                float my = y + pushY * sinR;
                Draw.rect(rMain, mx, my, rotation);
            }

            float barrelOffset = shotgunBarrelRecoil * maxBarrelRecoilDistance;
            if (rBarrel.found()) {
                float bx = x + barrelOffset * cosR;
                float by = y + barrelOffset * sinR;
                Draw.rect(rBarrel, bx, by, rotation);
            }

            float b1Offset = mainWeaponRecoil * maxBarrelRecoilDistance;
            if (rB1.found()) {
                float b1ax = x + b1Offset * cosR;
                float b1ay = y + b1Offset * sinR;
                Draw.rect(rB1, b1ax, b1ay, rotation);
            }

            float clawRecoilOffset = sideRecoil * maxSideRecoilDistance;
            float clawSideMove = subWeaponVisual * 2f;

            float clawForwardMove = 0f;
            float t = subWeaponVisual;

            if (t > 0f) {
                if (t <= 0.5f) {
                    clawForwardMove = (t / 0.5f) * 4.5f;
                } else {
                    float factor = (t - 0.5f) / 0.5f;
                    clawForwardMove = 4.5f - (factor * 8.5f);
                }
            }

            Draw.z(Layer.turret + 0.01f);

            if (rClawR1.found()) {
                float cx = x + ((clawRecoilOffset + clawForwardMove) * cosR) - (clawSideMove * sinR);
                float cy = y + ((clawRecoilOffset + clawForwardMove) * sinR) + (clawSideMove * cosR);
                Draw.rect(rClawR1, cx, cy, rotation);
            }

            if (rClawR2.found()) {
                float cx = x + ((clawRecoilOffset + clawForwardMove) * cosR) + (clawSideMove * sinR);
                float cy = y + ((clawRecoilOffset + clawForwardMove) * sinR) - (clawSideMove * cosR);
                Draw.rect(rClawR2, cx, cy, rotation);
            }

            Draw.z(Layer.turret);

            if (rTop.found()) {
                Draw.rect(rTop, x, y, rotation);
            }

            if (energyBallCharge > 0.01f) {
                Draw.z(Layer.turret + 0.05f);
                float ballX = x - cosR * 12f;
                float ballY = y - sinR * 12f;
                Color ballColor = (getTier() == 2) ? COL_RED : COL_CYAN;

                float chargePulse = 0f;
                if (shotgunReloadTimer > 0) {
                    chargePulse = Math.abs((float) Math.sin(Time.time * 0.3f)) * 0.5f * (shotgunReloadTimer / 180f);
                }

                float currentBallRadius = (3.2f + chargePulse + Mathf.range(0.2f)) * energyBallCharge;

                Draw.color(ballColor, (0.35f + (shotgunReloadTimer > 0 ? 0.25f : 0f)) * energyBallCharge);
                Fill.circle(ballX, ballY, currentBallRadius * 1.7f);

                Draw.color(Color.white, 0.9f * energyBallCharge);
                Fill.circle(ballX, ballY, currentBallRadius);

                Draw.reset();
            }

            // JS: `Vars.control.input.selected` không có ở v157 → điều kiện chưa từng đúng; vòng tròn này
            // đã do drawSelect() vẽ rồi nên bỏ (bài 80).

            int currentMaxShield = getMaxShieldHealth(getTier());

            if (shieldHealth > 0 && shieldScale > 0.05f) {
                Draw.z(Layer.effect + 5f);
                float glitchAmount = shieldHitTime > 0 ? Mathf.range(1.2f) : Mathf.range(0.3f);
                float currentRadius = (maxShieldRadius + glitchAmount) * shieldScale;
                Color baseColor = (getTier() == 2) ? COL_RED : COL_ORANGE;
                float alpha = (0.28f + (shieldHealth / currentMaxShield) * 0.42f) * shieldScale;

                if (shieldHitTime > 0 && Mathf.chance(0.3)) { Draw.color(Color.white); } else { Draw.color(baseColor, alpha); }
                Lines.stroke(1f * shieldScale);

                float hSpacing = hexSize * 1.5f;
                float vSpacing = hexSize * (float) Math.sqrt(3);

                for (float xOffset = -currentRadius; xOffset <= currentRadius; xOffset += hSpacing) {
                    for (float yOffset = -currentRadius; yOffset <= currentRadius; yOffset += vSpacing) {
                        int col = Mathf.floor((xOffset + currentRadius) / hSpacing);
                        float finalYOffset = yOffset + ((col % 2 == 0) ? 0f : vSpacing / 2f);
                        float cellGlitchX = (shieldHitTime > 0 || Mathf.chance(0.02)) ? Mathf.range(0.4f) : 0f;
                        float checkX = x + xOffset + cellGlitchX;
                        float checkY = y + finalYOffset;

                        float dst = Mathf.dst(x, y, checkX, checkY);

                        if (dst < currentRadius && dst > 14f) {
                            float angle = Mathf.angle(checkX - x, checkY - y);
                            float diff = getAngleDiff(rotation, angle);
                            if (diff <= (shieldAngleWidth / 2f)) {
                                float edgeFade = Math.min(1f, (currentRadius - dst) / 6f);
                                float cellAlpha = alpha * (1f - (dst / currentRadius) * 0.35f) * edgeFade;
                                if (shieldHitTime > 0 && Mathf.chance(0.2)) {
                                    Draw.color(Color.white, cellAlpha * 1.2f);
                                } else {
                                    Draw.color(baseColor, cellAlpha);
                                }
                                Lines.poly(checkX, checkY, 6, hexSize * shieldScale);
                            }
                        }
                    }
                }
                Draw.reset();
            }

            Draw.z(Layer.effect + 1f);
            float barY = y - 14f;
            float barWidth = 24f;
            float barHeight = 2f;
            Draw.color(Color.black, 0.5f);
            Lines.stroke(barHeight + 1f);
            Lines.line(x - (barWidth / 2f), barY, x + (barWidth / 2f), barY);
            if (shieldHealth <= 0) {
                Draw.color(Color.cyan);
                float regenProgress = regenTimer / REGEN_COOLDOWN;
                Lines.stroke(barHeight);
                Lines.line(x - (barWidth / 2f), barY, x - (barWidth / 2f) + (barWidth * regenProgress), barY);
            } else {
                Draw.color((getTier() == 2) ? COL_RED : Pal.lightOrange);
                float healthProgress = shieldHealth / currentMaxShield;
                Lines.stroke(barHeight);
                Lines.line(x - (barWidth / 2f), barY, x - (barWidth / 2f) + (barWidth * healthProgress), barY);
            }
            Draw.reset();
        }
    }
}
