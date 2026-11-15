package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Floatp;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
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
import mindustry.core.World;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.effect.ParticleEffect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;

/**
 * Chuyển từ forstarsilum.js. Block do forstarsilum.hjson tạo (type: PowerTurret) nên Java chỉ vá ở ContentInitEvent
 * và GIỮ nguyên hjson (bài học 29b). Pháo Starsword: tích buff theo từng phát, đủ 100% thì tụ kiếm 1s bắn Ult.
 */
public final class Forstarsilum {
    private Forstarsilum() {}

    /** JS: { titanium, silicon, plastanium } — giữ tên trường để chép nguyên văn khối chuỗi. */
    static final class Req {
        final int titanium, silicon, plastanium;
        Req(int t, int s, int p) { titanium = t; silicon = s; plastanium = p; }
    }

    static final Req reqMK2 = new Req(500, 300, 0);
    static final Req reqMK2B = new Req(800, 400, 200);

    static final float gainPerShotMK1 = 0.10f, decaySpeedMK1 = 0.15f;
    static final float gainPerShotMK2 = 0.20f, decaySpeedMK2 = 0.08f;
    static final float gainPerShotMK2B = 0.15f, decaySpeedMK2B = 0.10f;

    static final String modPrefix = "newex";
    static final float ULT_RANGE = 750f;

    static PowerTurret forstarsilum;
    static Effect galileoHitEffect, ultShockwaveEffect, ultBlastEffect, ultParticlesEffect;
    static BulletType forstarsilumBullet, forstarsilumMK2Bullet, forstarsilumMK2BBullet, ultBullet;

    /** JS: Core.settings.get("locale","").startsWith("en"). */
    static boolean isEn() { return Core.settings.getString("locale", "").startsWith("en"); }

    static void tryRewardStarlight(Team team) {
        if (Vars.net.client()) return;
        if (team == null) return;
        CoreBuild core = team.core();
        if (core == null) return;
        Item starlightItem = Vars.content.getByName(ContentType.item, modPrefix + "-starlight");
        if (starlightItem != null && Mathf.chance(0.09)) {
            core.items.add(starlightItem, 1);
            try { Call.effect(Fx.itemTransfer, core.x, core.y, 0f, Color.valueOf("ffda66")); } catch (Throwable err) {}
        }
    }

    static void onStarHit(Bullet b, Hitboxc entity, boolean wasDead) {
        if (!(entity instanceof Healthc) || wasDead) return;
        Healthc h = (Healthc) entity;
        if (!Vars.net.client() && Mathf.chance(0.10)) h.damagePierce(h.maxHealth() * 0.01f);
        if (h.dead() || h.health() <= 0f) tryRewardStarlight(b.team);
    }

    /** JS bullet: ghi đè hitEntity; ult (flag) thêm giáp x0.5 + 50% đòn chí mạng x4 + hiệu ứng. */
    static final class StarBullet extends BasicBulletType {
        final boolean ult;
        StarBullet(float speed, float damage, boolean ult) { super(speed, damage); this.ult = ult; }

        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            boolean wasDead = entity instanceof Healthc && (((Healthc) entity).dead() || ((Healthc) entity).health() <= 0f);
            super.hitEntity(b, entity, health);

            if (ult) {
                if (entity instanceof Unit) ((Unit) entity).armor = ((Unit) entity).armor * 0.5f;
                if (Mathf.chance(0.50)) {
                    float extraCritDmg = b.damage * 4f;
                    if (!Vars.net.client() && entity instanceof Healthc) ((Healthc) entity).damage(extraCritDmg);
                    ultShockwaveEffect.at(b.x, b.y, b.rotation());
                    ultBlastEffect.at(b.x, b.y);
                    ultParticlesEffect.at(b.x, b.y);
                    Effect.shake(14f, 14f, b.x, b.y);
                }
            }
            onStarHit(b, entity, wasDead);
        }
    }

    static StarBullet star(float speed, float damage, float w, float h, String front, String back, float knockback, float homingPower, float homingRange, boolean ult) {
        StarBullet b = new StarBullet(speed, damage, ult);
        b.lifetime = 30f; b.width = w; b.height = h;
        b.shrinkX = 0f; b.shrinkY = 0f;
        b.frontColor = Color.valueOf(front); b.backColor = Color.valueOf(back);
        b.sprite = modPrefix + "-starsword";
        b.pierce = true; b.pierceCap = 999; b.pierceBuilding = true; b.knockback = knockback; b.impact = true;
        if (homingPower > 0f) { b.homingPower = homingPower; b.homingRange = homingRange; }
        b.hitEffect = galileoHitEffect; b.despawnEffect = galileoHitEffect;
        return b;
    }

    static void makeContent() {
        ParticleEffect g = new ParticleEffect();
        g.line = true; g.particles = 8; g.lifetime = 26f; g.length = 35f; g.cone = -40f; g.lenFrom = 6f; g.lenTo = 6f;
        g.colorFrom = Color.valueOf("A9D8FF"); g.colorTo = Color.valueOf("66B1FF");
        galileoHitEffect = g;

        ultShockwaveEffect = new Effect(40f, e -> {
            float fin = e.fin(), fout = e.fout();
            int steps = 30;
            float rad = e.rotation * Mathf.degRad;
            float cosA = (float) Math.cos(rad), sinA = (float) Math.sin(rad);

            Draw.color(Color.white, PerfCore.color("66B1FF"), fin);
            Lines.stroke(5f * fout);
            float radiusX = fin * 30f, radiusY = fin * 80f, offset = fin * 35f;
            float cx = e.x + cosA * offset, cy = e.y + sinA * offset;
            float lastX = 0f, lastY = 0f;
            for (int i = 0; i <= steps; i++) {
                float angle = (i * (360f / steps)) * Mathf.degRad;
                float lx = (float) Math.cos(angle) * radiusX, ly = (float) Math.sin(angle) * radiusY;
                float rx = cx + (lx * cosA - ly * sinA), ry = cy + (lx * sinA + ly * cosA);
                if (i > 0) Lines.line(lastX, lastY, rx, ry);
                lastX = rx; lastY = ry;
            }

            Draw.color(PerfCore.color("A9D8FF"), PerfCore.color("3399FF"), fin);
            Lines.stroke(3f * fout);
            float radiusX2 = fin * 20f, radiusY2 = fin * 50f, offset2 = fin * 65f;
            float cx2 = e.x + cosA * offset2, cy2 = e.y + sinA * offset2;
            float lastX2 = 0f, lastY2 = 0f;
            for (int i = 0; i <= steps; i++) {
                float angle = (i * (360f / steps)) * Mathf.degRad;
                float lx = (float) Math.cos(angle) * radiusX2, ly = (float) Math.sin(angle) * radiusY2;
                float rx2 = cx2 + (lx * cosA - ly * sinA), ry2 = cy2 + (lx * sinA + ly * cosA);
                if (i > 0) Lines.line(lastX2, lastY2, rx2, ry2);
                lastX2 = rx2; lastY2 = ry2;
            }
            Draw.reset();
        });

        ParticleEffect blast = new ParticleEffect();
        blast.line = true; blast.particles = 32; blast.lifetime = 55f; blast.length = 130f; blast.cone = 360f;
        blast.lenFrom = 20f; blast.lenTo = 2f; blast.strokeFrom = 7f; blast.strokeTo = 0f;
        blast.colorFrom = Color.white; blast.colorTo = Color.valueOf("3399FF");
        ultBlastEffect = blast;

        ParticleEffect parts = new ParticleEffect();
        parts.particles = 24; parts.lifetime = 60f; parts.length = 90f; parts.cone = 360f;
        parts.sizeFrom = 6f; parts.sizeTo = 0f;
        parts.colorFrom = Color.valueOf("A9D8FF"); parts.colorTo = Color.valueOf("1a4a88");
        ultParticlesEffect = parts;

        forstarsilumBullet = star(10f, 78f, 20f, 40f, "bbdefb", "90caf9", 1f, 0f, 0f, false);
        forstarsilumMK2Bullet = star(12.5f, 105f, 22f, 44f, "bbdefb", "90caf9", 1.4f, 0f, 0f, false);
        forstarsilumMK2BBullet = star(11.5f, 165f, 24f, 48f, "ff8888", "dd4444", 2f, 0.18f, 300f, false);
        // JS còn đặt `armorPiercing: 0.80` — không phải trường của BulletType v157 nên Rhino bỏ qua; Java không gán (bài học 24).
        ultBullet = star(25f, 850f, 35f, 70f, "ffffff", "bbdefb", 4f, 0f, 0f, true);
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        makeContent();
        Block blk = Vars.content.getByName(ContentType.block, modPrefix + "-forstarsilum");
        if (!(blk instanceof PowerTurret)) return;
        forstarsilum = (PowerTurret) blk;

        forstarsilum.configurable = true;
        forstarsilum.shootType = forstarsilumBullet;

        forstarsilum.addBar("dmg_bonus", (Building e) -> {
            FsBuild f = (FsBuild) e;
            return new Bar(
                (Prov<CharSequence>) () -> "DMG: +" + Mathf.floor(f.getDmgRatio() * 500f) + "%",
                (Prov<Color>) () -> Color.orange,
                (Floatp) () -> f.getDmgRatio());
        });
        forstarsilum.addBar("as_bonus", (Building e) -> {
            FsBuild f = (FsBuild) e;
            return new Bar(
                (Prov<CharSequence>) () -> "AS: " + (Mathf.floor(f.getAsRatio() * 250f) >= 0 ? "+" : "") + Mathf.floor(f.getAsRatio() * 250f) + "%",
                (Prov<Color>) () -> Color.cyan,
                (Floatp) () -> Math.max(f.getAsRatio(), 0f));
        });

        forstarsilum.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof FsBuild) ((FsBuild) tile).setTier(value);
        });
        forstarsilum.buildType = () -> new FsBuild(forstarsilum);
    }

    static final class UltEntry {
        final Bullet bullet; final float trueDmg;
        UltEntry(Bullet b, float t) { bullet = b; trueDmg = t; }
    }

    public static class FsBuild extends PowerTurret.PowerTurretBuild {
        float energyState = 0f, customRecoil = 0f, chargeTimer = 0f, noResetChanceMK2B = 0.80f;
        int tierState = 0;
        boolean isChargingUlt = false;
        final ArrayList<UltEntry> activeUltBullets = new ArrayList<>();

        public FsBuild(PowerTurret outer) { outer.super(); }

        @Override
        public BulletType peekAmmo() {
            int tier = getTier();
            if (tier == 1) return forstarsilumMK2Bullet;
            if (tier == 2) return forstarsilumMK2BBullet;
            return forstarsilumBullet;
        }

        @Override
        public BulletType useAmmo() { return peekAmmo(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            if (val == 0) health = 3000f;
            if (val == 1) health = 4500f;
            if (val == 2) health = 4000f;
            maxHealth = health;
        }

        @Override
        public float range() { return ULT_RANGE; }

        private String reqSnapshot(boolean english) {
            CoreBuild core = team.core();
            if (core == null) return english ? "[red]Team Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";
            int currentTitanium = core.items.get(Items.titanium);
            int currentSilicon = core.items.get(Items.silicon);
            int currentPlastanium = core.items.get(Items.plastanium);

            String titColor1 = currentTitanium >= reqMK2.titanium ? "[green]" : "[red]";
            String silColor1 = currentSilicon >= reqMK2.silicon ? "[green]" : "[red]";
            String titColor2 = currentTitanium >= reqMK2B.titanium ? "[green]" : "[red]";
            String silColor2 = currentSilicon >= reqMK2B.silicon ? "[green]" : "[red]";
            String plaColor2 = currentPlastanium >= reqMK2B.plastanium ? "[green]" : "[red]";

            if (english) {
                                return "[yellow]CORE STORAGE REQUIREMENTS:[]\n" +
                                       "[cyan]MK2 Branch:[]\n" +
                                       " • Titanium: " + titColor1 + currentTitanium + "[] / " + reqMK2.titanium + "\n" +
                                       " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                                       "[purple]MK2B Branch:[]\n" +
                                       " • Titanium: " + titColor2 + currentTitanium + "[] / " + reqMK2B.titanium + "\n" +
                                       " • Silicon: " + silColor2 + currentSilicon + "[] / " + reqMK2B.silicon + "\n" +
                                       " • Plastanium: " + plaColor2 + currentPlastanium + "[] / " + reqMK2B.plastanium;
            }

                            return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                                   "[cyan]Nhánh MK2:[]\n" +
                                   " • Titan: " + titColor1 + currentTitanium + "[] / " + reqMK2.titanium + "\n" +
                                   " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                                   "[purple]Nhánh MK2B:[]\n" +
                                   " • Titan: " + titColor2 + currentTitanium + "[] / " + reqMK2B.titanium + "\n" +
                                   " • Silicon: " + silColor2 + currentSilicon + "[] / " + reqMK2B.silicon + "\n" +
                                   " • Nhựa: " + plaColor2 + currentPlastanium + "[] / " + reqMK2B.plastanium;
        }

        private boolean tryUpgrade(Req r, int tier, boolean english) {
            CoreBuild core = team.core();
            if (core != null && core.items.get(Items.titanium) >= r.titanium && core.items.get(Items.silicon) >= r.silicon && core.items.get(Items.plastanium) >= r.plastanium) {
                NexNet.take(core, Items.titanium, r.titanium); NexNet.take(core, Items.silicon, r.silicon);
                if (r.plastanium > 0) NexNet.take(core, Items.plastanium, r.plastanium);
                NexNet.fx(ultShockwaveEffect, x, y, rotation); NexNet.shake(4, 4, x, y);
                configure(Integer.valueOf(tier));
                return true;
            }
            return false;
        }

        private void openUpgrade(boolean english) {
            String dialogTitle = english ? "Forstarsilum Upgrade Center" : "Trung tâm nâng cấp pháo Forstarsilum";
            BaseDialog dialog = new BaseDialog(dialogTitle);

            String reqSnapshot = reqSnapshot(english);
            arc.scene.ui.layout.Cell<arc.scene.ui.Label> reqCell = dialog.cont.add(reqSnapshot);
            reqCell.width(360f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row(); dialog.cont.add().height(10f).row();

            Table branchesTable = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
            b1.add(english ? "[cyan]===(MK2 - ENERGY PIERCING)===[]" : "[cyan]===(MK2 - XUYÊN PHÁ NĂNG LƯỢNG)===[]").row();
            String b1Text = english ?
                            "[white]• Max Health: [green]+50%[] (4,500 HP)\n" +
                            "• Range: [gold]750 px[]\n" +
                            "• Base Damage: [green]+34.6%[] (105 DMG)\n" +
                            "• [orange]Magnetic Acceleration:[] Doubles buff accumulation (+20%/shot)\n• [gold]40% chance to fire charged attack (1s charge, deals 75% True Damage)[] per regular shot" :
                            "[white]• Máu cấu trúc: [green]+50%[] (4,500 HP)\n" +
                            "• Tầm bắn: [gold]750 px[]\n" +
                            "• Sát thương gốc: [green]+34.6%[] (105 DMG)\n" +
                            "• [orange]Gia Tốc Từ Tính:[] Tích lũy buff gấp đôi (+20%/viên)\n• [gold]40% cơ hội bắn đòn dồn lực (tụ kiếm 1s, gây 75% True Damage)[] theo mỗi phát bắn thường";
            arc.scene.ui.layout.Cell<arc.scene.ui.Label> b1D = b1.add(b1Text);
            b1D.width(340f).get().setWrap(true); b1D.get().setAlignment(Align.left); b1.row();
            b1.button(english ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> {
                if (tryUpgrade(reqMK2, 1, english)) { dialog.hide(); deselect(); }
                else Vars.ui.showInfo(english ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
            }).size(180f, 38f);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
            b2.add(english ? "[purple]===(MK2B - THERMAL TRACKING)===[]" : "[purple]===(MK2B - TRUY ĐUỔI TẦM NHIỆT)===[]").row();
            String b2Text = english ?
                            "[white]• Max Health: [green]+33.3%[] (4,000 HP)\n" +
                            "• Range: [gold]750 px[]\n" +
                            "• Base Damage: [green]+111.5%[] (165 DMG)\n" +
                            "• [orange]Energy Retention:[] After firing charged attack (deals 75% True Damage)\n• [gold]has a chance to NOT RESET buff bar (80% -> 70% -> ... -> 0%)[]" :
                            "[white]• Máu cấu trúc: [green]+33.3%[] (4,000 HP)\n" +
                            "• Tầm bắn: [gold]750 px[]\n" +
                            "• Sát thương gốc: [green]+111.5%[] (165 DMG)\n" +
                            "• [orange]Duy Trì Năng Lượng:[] Sau khi bắn đòn dồn lực (gây 75% True Damage)\n• [gold]có xác suất KHÔNG RESET thanh buff (80% -> 70% -> ... -> 0%)[]";
            arc.scene.ui.layout.Cell<arc.scene.ui.Label> b2D = b2.add(b2Text);
            b2D.width(340f).get().setWrap(true); b2D.get().setAlignment(Align.left); b2.row();
            b2.button(english ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> {
                if (tryUpgrade(reqMK2B, 2, english)) { dialog.hide(); deselect(); }
                else Vars.ui.showInfo(english ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
            }).size(180f, 38f);

            branchesTable.add(b1).width(340f); branchesTable.row();
            branchesTable.add().height(12f).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean english) {
            String title = english ? " Forstarsilum Stats: " : " Thông số pháo Forstarsilum: ";
            String descStr = "";
            int currentTier = getTier();

            String rewardDesc = english ?
                        "\n\n[gold]🎁 DROP BONUS:[]\n• Has a [stat]9% chance[] to gain 1x [accent]Starlight[] directly to Core when Ultimate kills an enemy unit/structure." :
                        "\n\n[gold]🎁 PHẦN THƯỞNG PHỤ:[]\n• Có [stat]9% cơ hội[] nhận 1x [accent]Starlight[] trực tiếp vào Lõi khi đòn Ult tiêu diệt mục tiêu.";

            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = (english ?
                            "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                            "[lightgray]Turret Health:[] [green]3,000[]\n" +
                            "[lightgray]Effective Range:[] [orange]750 pixels[]\n" +
                            "[lightgray]Base Damage:[] [yellow]78.00 DMG[]\n" +
                            "[lightgray]Charged Strike (Sword):[] [scarlet]75% True Damage (Pierces armor, shields & Vela mechanics)[]\n" +
                            "[lightgray]Fire Rate:[] [white]1 shot / 1.0 seconds[]\n\n" +
                            "[sky]⚡ ACCUMULATED ENERGY MECHANIC (BUFF):[]\n" +
                            "• [lightgray]Buff Accumulation:[] Each hit/activation gains [green]+10.0%[] buff.\n" +
                            "• [lightgray]Decay:[] Automatically decays over time when not attacking targets." :
                            "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                            "[lightgray]Máu tháp pháo:[] [green]3,000[]\n" +
                            "[lightgray]Tầm bắn hiệu dụng:[] [orange]750 pixel[]\n" +
                            "[lightgray]Sát thương gốc:[] [yellow]78.00 DMG[]\n" +
                            "[lightgray]Đòn Tụ Lực (Kiếm):[] [scarlet]75% True Damage (Xuyên giáp, khiên & cơ chế Vela)[]\n" +
                            "[lightgray]Tốc độ bắn:[] [white]1 phát / 1.0 giây[]\n\n" +
                            "[sky]⚡ CƠ CHẾ NĂNG LƯỢNG TÍCH LŨY (BUFF):[]\n" +
                            "• [lightgray]Tích lũy Buff:[] Mỗi phát bắn trúng/kích hoạt sẽ tăng [green]+10.0%[] buff.\n" +
                            "• [lightgray]Suy giảm:[] Tự động suy giảm dần theo thời gian khi không bắn mục tiêu.") + rewardDesc;
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = (english ?
                            "[cyan]⚡ BASE STATS (MK2) ⚡[]\n" +
                            "[lightgray]Turret Health:[] [green]4,500 [lime](+50%)[]\n" +
                            "[lightgray]Effective Range:[] [orange]750 pixels[]\n" +
                            "[lightgray]Base Damage:[] [yellow]105.00 DMG [lime](+34.6%)[]\n" +
                            "[lightgray]Charged Strike (Sword):[] [scarlet]75% True Damage (Pierces armor, shields & Vela mechanics)[]\n\n" +
                            "[lime]⚡ MK2 SPECIAL MECHANIC:[]\n" +
                            "• [lightgray]Ult Chance:[] Has a [gold]40% chance[] to trigger 1s sword charge to fire ultimate per regular shot.\n" +
                            "• [lightgray]Accumulation Speed:[] Gains [green]+20.0%[] buff per shot.\n" +
                            "• [lightgray]Stable Retention:[] Buff drop rate when idle reduced to [green]0.08/s[]." :
                            "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                            "[lightgray]Máu tháp pháo:[] [green]4,500 [lime](+50%)[]\n" +
                            "[lightgray]Tầm bắn hiệu dụng:[] [orange]750 pixel[]\n" +
                            "[lightgray]Sát thương gốc:[] [yellow]105.00 DMG [lime](+34.6%)[]\n" +
                            "[lightgray]Đòn Tụ Lực (Kiếm):[] [scarlet]75% True Damage (Xuyên giáp, khiên & cơ chế Vela)[]\n\n" +
                            "[lime]⚡ CƠ CHẾ ĐẶC BIỆT MK2:[]\n" +
                            "• [lightgray]Xác suất Ult:[] Có [gold]40% cơ hội[] kích hoạt tụ kiếm 1s bắn đòn dồn lực theo mỗi phát bắn thường.\n" +
                            "• [lightgray]Tốc độ tích tụ:[] Tăng [green]+20.0%[] buff mỗi phát bắn.\n" +
                            "• [lightgray]Duy trì ổn định:[] Tốc độ rớt buff khi dừng bắn giảm còn [green]0.08/s[].") + rewardDesc;
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = (english ?
                            "[purple]⚡ BASE STATS (MK2B) ⚡[]\n" +
                            "[lightgray]Turret Health:[] [green]4,000 [lime](+33.3%)[]\n" +
                            "[lightgray]Effective Range:[] [orange]750 pixels[]\n" +
                            "[lightgray]Base Damage:[] [red]165.00 DMG (+111.5%)[]\n" +
                            "[lightgray]Charged Strike (Sword):[] [scarlet]75% True Damage (Pierces armor, shields & Vela mechanics)[]\n\n" +
                            "[purple]🔥 MK2B SPECIAL MECHANIC:[]\n" +
                            "• [lightgray]No Buff Reset:[] After ultimate shot, has a chance to [gold]keep 100% buff[] without resetting (80% -> 70% -> ... -> 0%).\n" +
                            "• [lightgray]Homing Circuit:[] Starsword ammo [pink]automatically homes into targets[] (300px).\n" +
                            "• [lightgray]Firepower Stacking:[] Gains [green]+15.0%[] buff/shot." :
                            "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                            "[lightgray]Máu tháp pháo:[] [green]4,000 [lime](+33.3%)[]\n" +
                            "[lightgray]Tầm bắn hiệu dụng:[] [orange]750 pixel[]\n" +
                            "[lightgray]Sát thương gốc:[] [red]165.00 DMG (+111.5%)[]\n" +
                            "[lightgray]Đòn Tụ Lực (Kiếm):[] [scarlet]75% True Damage (Xuyên giáp, khiên & cơ chế Vela)[]\n\n" +
                            "[purple]🔥 CƠ CHẾ ĐẶC BIỆT MK2B:[]\n" +
                            "• [lightgray]Không Reset Buff:[] Sau đòn dồn lực, có cơ hội [gold]giữ nguyên 100% buff[] không bị reset (80% -> 70% -> ... -> 0%).\n" +
                            "• [lightgray]Mạch định vị:[] Đạn Starsword [pink]tự động bẻ lái tìm mục tiêu[] (300px).\n" +
                            "• [lightgray]Tích lũy hỏa lực:[] Tăng [green]+15.0%[] buff/viên.") + rewardDesc;
            }

            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            arc.scene.ui.layout.Cell<arc.scene.ui.Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true); cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            int tier = getTier();
            final boolean english = isEn();

            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(english))
                    .size(50, 40).tooltip(english ? "Upgrade Forstarsilum system" : "Nâng cấp hệ thống Forstarsilum");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(english ? "[scarlet]FORSTARSILUM SYSTEM HAS REACHED MAX EVOLUTION LIMIT![]" : "[scarlet]HỆ THỐNG FORSTARSILUM ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]"))
                    .size(50, 40).tooltip(english ? "Reached maximum level" : "Đã đạt cấp tối đa");
            }

            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(english))
                .size(50, 40).tooltip(english ? "View detailed system stats" : "Xem thông số chi tiết hệ thống");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        void executeUltFire(float muzzleX, float muzzleY) {
            // 7 tham số = (velocityScl, lifetimeScl) (bài học 3): đạn giữ sát thương gốc 850 rồi gán lại bên dưới.
            Bullet bullet = ultBullet.create(this, team, muzzleX, muzzleY, rotation, 1f, 1f);
            if (bullet != null) {
                float totalDmg = bullet.damage * (1f + energyState * 9f);
                float trueDmgVal = totalDmg * 0.75f;
                float normalDmgVal = totalDmg * 0.25f;
                bullet.damage = normalDmgVal;

                if (bullet.type != null) {
                    bullet.type.absorbable = false;
                    bullet.type.hittable = false;
                    bullet.type.reflectable = false;
                    bullet.type.pierceArmor = true;
                }
                activeUltBullets.add(new UltEntry(bullet, trueDmgVal));
            }
            ultShockwaveEffect.at(muzzleX, muzzleY, rotation);
            Effect.shake(5f, 5f, x, y);
        }

        void startUltCharge() {
            isChargingUlt = true;
            chargeTimer = 0f;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            int tier = getTier();

            float currentDecay = decaySpeedMK1;
            if (tier == 1) currentDecay = decaySpeedMK2;
            if (tier == 2) currentDecay = decaySpeedMK2B;

            // JS: `!this.isShooting` (thiếu ngoặc, luôn truthy) => điều kiện luôn sai, buff KHÔNG BAO GIỜ tự suy giảm.
            // Java gọi isShooting() thật nên buff suy giảm khi không bắn (đúng mô tả trong UI; bài học 58).
            if ((!isShooting() || !isActive()) && !isChargingUlt) {
                if (energyState > 0f) energyState = Math.max(energyState - (currentDecay * Time.delta / 60f), 0f);
            }

            if (energyState >= 0.999f && !isChargingUlt) startUltCharge();

            if (isChargingUlt) {
                chargeTimer += Time.delta;
                if (chargeTimer >= 60f) {
                    float rad = rotation * Mathf.degRad;
                    float muzzleX = x + (float) Math.cos(rad) * 12f;
                    float muzzleY = y + (float) Math.sin(rad) * 12f;
                    executeUltFire(muzzleX, muzzleY);

                    if (tier == 2 && Mathf.chance(noResetChanceMK2B)) {
                        energyState = 1f;
                        noResetChanceMK2B = Math.max(noResetChanceMK2B - 0.10f, 0f);
                    } else {
                        energyState = 0f;
                        noResetChanceMK2B = 0.80f;
                    }
                    chargeTimer = 0f;
                    isChargingUlt = false;
                }
            }

            customRecoil = Mathf.approach(customRecoil, 0f, 0.12f * Time.delta);

            if (!Vars.net.client() && !activeUltBullets.isEmpty()) {
                for (int i = activeUltBullets.size() - 1; i >= 0; i--) {
                    UltEntry entry = activeUltBullets.get(i);
                    final Bullet b = entry.bullet;
                    final float trueDmg = entry.trueDmg;

                    if (b == null || !b.isAdded() || b.lifetime <= 0f) { activeUltBullets.remove(i); continue; }

                    final float radius = (b.type != null ? b.type.hitSize : 12f) + 4f;
                    final Team bTeam = team;

                    Groups.unit.intersect(b.x - radius, b.y - radius, radius * 2f, radius * 2f, u -> {
                        if (u != null && u.isValid() && u.team != bTeam && Mathf.dst(b.x, b.y, u.x, u.y) <= radius + u.hitSize) {
                            u.health -= trueDmg;
                            Fx.hitBulletSmall.at(u.x, u.y);
                            if (u.health <= 0f) {
                                u.kill();
                                tryRewardStarlight(bTeam);
                            }
                        }
                    });

                    if (b.isAdded()) {
                        Building tileBuild = Vars.world.build(World.toTile(b.x), World.toTile(b.y));
                        if (tileBuild != null && tileBuild.team != bTeam) {
                            tileBuild.health -= trueDmg;
                            Fx.hitBulletSmall.at(tileBuild.x, tileBuild.y);
                            if (tileBuild.health <= 0f) {
                                tileBuild.kill();
                                tryRewardStarlight(bTeam);
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void shoot(BulletType type) {
            if (isChargingUlt) return;
            super.shoot(type);

            int tier = getTier();
            float currentGain = gainPerShotMK1;
            if (tier == 1) currentGain = gainPerShotMK2;
            if (tier == 2) currentGain = gainPerShotMK2B;

            if (tier == 1 && Mathf.chance(0.40)) startUltCharge();

            energyState = Math.min(energyState + currentGain, 1f);
            customRecoil = 1f;
        }

        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            if (bullet != null) bullet.damage = bullet.type.damage * (1f + energyState * 9f);
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);
        }

        @Override
        public float baseReloadSpeed() { return efficiency * (1f + energyState * 2.5f); }

        float getDmgRatio() { return energyState; }
        float getAsRatio() { return energyState; }

        @Override
        public void draw() {
            // JS: `block.basePrefix` không tồn tại ở v157 => "undefined3" không tìm thấy => luôn super.draw() (bài học 13).
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

            float maxBarrelRecoilDistance = -5f;
            float sideMoveDistance = energyState * 4f;

            TextureRegion barrel1Region = PerfCore.region(modPrefix + "-forstarsilum-barrel1");
            if (barrel1Region.found()) Draw.rect(barrel1Region, x - (sideMoveDistance * sin), y + (sideMoveDistance * cos), rotation);

            TextureRegion barrel2Region = PerfCore.region(modPrefix + "-forstarsilum-barrel2");
            if (barrel2Region.found()) Draw.rect(barrel2Region, x + (sideMoveDistance * sin), y - (sideMoveDistance * cos), rotation);

            float b1Offset = customRecoil * maxBarrelRecoilDistance;
            TextureRegion b1Region = PerfCore.region(modPrefix + "-forstarsilum-b1");
            if (b1Region.found()) Draw.rect(b1Region, x + b1Offset * cos, y + b1Offset * sin, rotation);

            if (isChargingUlt) {
                TextureRegion swordRegion = PerfCore.region(modPrefix + "-starsword");
                if (swordRegion.found()) {
                    Draw.z(Layer.effect + 1f);
                    float progress = Math.min(chargeTimer / 60f, 1f);
                    float muzzleX = x + cos * 16f, muzzleY = y + sin * 16f;
                    float baseRadius = 35f * (1f - progress * 0.7f);

                    for (int i = 0; i < 5; i++) {
                        float angleOffset = (i - 2) * 25f * (1f - progress);
                        float swordRad = (rotation + angleOffset) * Mathf.degRad;
                        float currentX = Mathf.lerp(x + (float) Math.cos(swordRad) * baseRadius, muzzleX, progress);
                        float currentY = Mathf.lerp(y + (float) Math.sin(swordRad) * baseRadius, muzzleY, progress);
                        Draw.color(Color.white);
                        Draw.alpha(0.4f + progress * 0.6f);
                        Draw.rect(swordRegion, currentX, currentY, 35f, 50f, rotation - 90f);
                    }
                    Draw.reset();
                }
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.f(energyState);
            write.f(noResetChanceMK2B);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            energyState = read.f();
            noResetChanceMK2B = read.f();
            customRecoil = 0f;
            activeUltBullets.clear();
        }
    }
}
