package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec2;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;

/**
 * Chuyển từ dor.js. Pháo "Dor" 6 cấp (MK1, MK2, MK2B, MK3, MK2B1, MK3B).
 * Block do Java tạo bằng new ItemTurret("dor") nên dor.hjson KHÔNG có `type:` cấp cao nhất (bài học 29a).
 * hjson nạp SAU và ghi đè size 2 / reload 20 / range 300 / ammoTypes (copper) — cùng thứ tự với JS (bài học 23).
 */
public final class Dor {
    private Dor() {}

    // đồng, chì, titan
    private static final int[] REQ_MK2 = {200, 200, 0};
    private static final int[] REQ_MK2B = {200, 200, 100};
    private static final int[] REQ_MK3 = {400, 400, 200};
    private static final int[] REQ_MK2B1 = {400, 400, 200};
    private static final int[] REQ_MK3B = {800, 800, 400};

    private static final float CHARGE_TIME_MK1 = 60f, CHARGE_TIME_MK2 = 48f, CHARGE_TIME_MK2B = 72f;
    private static final float BERSERK_TIME_MK1 = 300f, BERSERK_TIME_MK2 = 360f, BERSERK_TIME_MK3 = 486f;

    private static BulletType dorNormalBullet, dorSmallSprayBullet, dormk2NormalBullet, dormk2SmallSprayBullet,
        dormk3NormalBullet, dormk3SmallSprayBullet, normalBulletB, laserBulletB, normalBulletB1, laserBulletB1,
        shotgunBulletB3, laserBulletB3;

    public static ItemTurret dor;

    /** JS: isEn = locale bắt đầu bằng "en" (bài học 8: giữ đúng hàm của từng file). */
    private static boolean isEn() { return Core.settings.getString("locale", "").startsWith("en"); }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static BasicBulletType bb(float speed, float damage, float w, float h, float life, String front, String back,
                                      String trail, float trailW, int trailL, float homingPower, float homingRange) {
        BasicBulletType b = new BasicBulletType(speed, damage);
        b.width = w; b.height = h; b.lifetime = life;
        b.frontColor = Color.valueOf(front); b.backColor = Color.valueOf(back); b.trailColor = Color.valueOf(trail);
        b.trailWidth = trailW; b.trailLength = trailL;
        b.hitEffect = Fx.hitBulletColor; b.despawnEffect = Fx.hitBulletColor;
        if (homingPower > 0f) { b.homingPower = homingPower; b.homingRange = homingRange; }
        return b;
    }

    /** `chargeEffect: Fx.lancerLaserCharge` của JS bỏ qua: pháo không dùng firstShotDelay nên chưa từng phát (bài học 24). */
    private static LaserBulletType laser(float length, float damage, float width, String c1, String c2) {
        LaserBulletType l = new LaserBulletType(damage);
        l.length = length; l.width = width; l.lifetime = 25f;
        l.colors = new Color[]{Color.valueOf(c1), Color.valueOf(c2), Color.white};
        l.hitEffect = Fx.hitLaserColor; l.smokeEffect = Fx.smoke;
        return l;
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        String cy1 = "#e0f7fa", cy2 = "#00bcd4", cy3 = "#80deea";
        String yl1 = "#fff59d", yl2 = "#fbc02d", yl3 = "#ffee58";
        String rd1 = "#ff8a80", rd2 = "#ff1744", rd3 = "#ff5252";
        dorNormalBullet = bb(7f, 12f, 7f, 18f, 42.86f, cy1, cy2, cy3, 1.5f, 5, 0f, 0f);
        dorSmallSprayBullet = bb(8.5f, 21f, 3.5f, 9f, 35.29f, cy1, cy2, cy3, 0.75f, 3, 0f, 0f);
        dormk2NormalBullet = bb(7.7f, 21f, 7.7f, 19.8f, 50.65f, cy1, cy2, cy3, 1.65f, 6, 0f, 0f);
        dormk2SmallSprayBullet = bb(9.0f, 25.5f, 3.85f, 10f, 43.33f, cy1, cy2, cy3, 0.82f, 4, 0f, 0f);
        dormk3NormalBullet = bb(8.5f, 28.35f, 8.5f, 21.5f, 61.94f, yl1, yl2, yl3, 1.8f, 7, 0f, 0f);
        dormk3SmallSprayBullet = bb(9.5f, 34.425f, 4.2f, 11f, 55.42f, yl1, yl2, yl3, 0.9f, 5, 0f, 0f);
        normalBulletB = bb(8f, 27f, 8f, 20f, 30f, rd1, rd2, rd3, 2f, 6, 0.12f, 160f);
        laserBulletB = laser(240f, 18f, 24f, "#ff1744", "#b71c1c");
        normalBulletB1 = bb(8.8f, 29.7f, 8.8f, 22f, 30f, rd1, rd2, rd3, 2.2f, 7, 0.15f, 180f);
        laserBulletB1 = laser(264f, 19.8f, 26.4f, "#ff1744", "#b71c1c");
        shotgunBulletB3 = bb(9.5f, 81f, 9f, 18f, 37.89f, "#ea80fc", "#aa00ff", "#e040fb", 2.5f, 6, 0.2f, 220f);
        laserBulletB3 = laser(360f, 27f, 36f, "#ea80fc", "#aa00ff");

        dor = new ItemTurret("dor");
        dor.health = 950;
        dor.size = 3;
        dor.reload = 40f;
        dor.configurable = true;
        dor.category = Category.turret;
        dor.ammoTypes.put(Items.lead, dorNormalBullet);

        dor.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof DorBuild) ((DorBuild) tile).setTier(value);
        });

        dor.buildType = () -> new DorBuild(dor);
    }

    public static class DorBuild extends ItemTurret.ItemTurretBuild {
        float chargeTimer = 0f, berserkTimer = 0f, customReloadTimer = 0f;
        int superShotCount = 0, tierState = 0, laserCount = 0, burstTimer = 0, burstShotsFired = 0;
        int paidCopper = 0, paidLead = 0, paidTitanium = 0;

        public DorBuild(ItemTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            paidCopper = 0; paidLead = 0; paidTitanium = 0;
            if (val == 0) health = 950f;
            if (val == 1) health = 1235f;
            if (val == 2) health = 1710f;
            if (val == 3) health = 1667f;
            if (val == 4) health = 1881f;
            if (val == 5) health = 2565f;
            maxHealth = health;
        }

        boolean processPartialUpgrade(int targetTier, int[] req) {
            Building core = team.core();
            if (core == null) {
                Vars.ui.showInfo(isEn() ? "[red]Team Core Not Found![]" : "[red]Không tìm thấy Lõi Đội![]");
                return false;
            }
            int reqC = req[0], reqL = req[1], reqT = req[2];
            int remC = reqC - paidCopper, remL = reqL - paidLead, remT = reqT - paidTitanium;
            int curC = core.items.get(Items.copper), curL = core.items.get(Items.lead), curT = core.items.get(Items.titanium);
            String cColor = curC >= remC ? "[green]" : "[red]";
            String lColor = curL >= remL ? "[green]" : "[red]";
            String tColor = curT >= remT ? "[green]" : "[red]";

            String scanMsg;
            if (isEn()) {
                scanMsg = "[yellow]SINGLE SCAN - CORE ITEMS VS REQUIRED:[]\n";
                if (reqC > 0) scanMsg += " • Copper: " + cColor + curC + "[] / " + remC + "\n";
                if (reqL > 0) scanMsg += " • Lead: " + lColor + curL + "[] / " + remL + "\n";
                if (reqT > 0) scanMsg += " • Titanium: " + tColor + curT + "[] / " + remT + "\n";
            } else {
                scanMsg = "[yellow]KẾT QUẢ QUÉT LÕI - TÀI NGUYÊN / YÊU CẦU:[]\n";
                if (reqC > 0) scanMsg += " • Đồng: " + cColor + curC + "[] / " + remC + "\n";
                if (reqL > 0) scanMsg += " • Chì: " + lColor + curL + "[] / " + remL + "\n";
                if (reqT > 0) scanMsg += " • Titan: " + tColor + curT + "[] / " + remT + "\n";
            }

            int takeC = Math.min(curC, Math.max(0, remC));
            int takeL = Math.min(curL, Math.max(0, remL));
            int takeT = Math.min(curT, Math.max(0, remT));
            if (takeC > 0) { NexNet.take(core, Items.copper, takeC); paidCopper += takeC; }
            if (takeL > 0) { NexNet.take(core, Items.lead, takeL); paidLead += takeL; }
            if (takeT > 0) { NexNet.take(core, Items.titanium, takeT); paidTitanium += takeT; }

            if (paidCopper >= reqC && paidLead >= reqL && paidTitanium >= reqT) {
                if (targetTier == 1 || targetTier == 3) NexNet.fx(Fx.upgradeCore, x, y);
                else NexNet.fx(Fx.bigShockwave, x, y);
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(6, 6, x, y);
                if (Vars.net.active()) Call.tileConfig(Vars.player, this, Integer.valueOf(targetTier));
                else configure(Integer.valueOf(targetTier));
                return true;
            }
            Vars.ui.showInfo(scanMsg + (isEn() ? "\n[red]Not enough resources in Core![]" : "\n[red]Thiếu tài nguyên trong Lõi![]"));
            return false;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 5) return 360f;
            if (tier == 4) return 264f;
            if (tier == 3) return 526.5f;
            if (tier == 2) return 240f;
            if (tier == 1) return 390f;
            return 300f;
        }

        private int scan(int item) {
            Building core = team.core();
            if (core == null) return 0;
            return core.items.get(item == 0 ? Items.copper : item == 1 ? Items.lead : Items.titanium);
        }

        private Table branchBox(String header) {
            Table b = new Table(); b.background(Styles.black6); b.margin(12);
            b.add(header).row();
            return b;
        }

        private void finishDialog(BaseDialog dialog, Table content) {
            ScrollPane scroll = new ScrollPane(content);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openMk1Upgrade() {
            int scanC = scan(0), scanL = scan(1), scanT = scan(2);
            BaseDialog dialog = new BaseDialog(isEn() ? "Dor Turret Upgrade Center" : "Trung tâm nâng cấp pháo Dor");

            int cMK2 = Math.max(0, REQ_MK2[0] - paidCopper);
            int lMK2 = Math.max(0, REQ_MK2[1] - paidLead);
            int cMK2B = Math.max(0, REQ_MK2B[0] - paidCopper);
            int lMK2B = Math.max(0, REQ_MK2B[1] - paidLead);
            int tMK2B = Math.max(0, REQ_MK2B[2] - paidTitanium);

            String ccMK2 = scanC >= cMK2 ? "[green]" : "[red]";
            String clMK2 = scanL >= lMK2 ? "[green]" : "[red]";
            String ccMK2B = scanC >= cMK2B ? "[green]" : "[red]";
            String clMK2B = scanL >= lMK2B ? "[green]" : "[red]";
            String ctMK2B = scanT >= tMK2B ? "[green]" : "[red]";

            String reqText = isEn() ?
                    ("[yellow]CORE / REQUIRED COST:[]\n" +
                     "[cyan]MK2 Branch:[]\n" +
                     " • Copper: " + ccMK2 + scanC + "[] / " + cMK2 + "\n" +
                     " • Lead: " + clMK2 + scanL + "[] / " + lMK2 + "\n" +
                     "[purple]MK2B Branch:[]\n" +
                     " • Copper: " + ccMK2B + scanC + "[] / " + cMK2B + "\n" +
                     " • Lead: " + clMK2B + scanL + "[] / " + lMK2B + "\n" +
                     " • Titanium: " + ctMK2B + scanT + "[] / " + tMK2B) :
                    ("[yellow]TÀI NGUYÊN LÕI / YÊU CẦU:[]\n" +
                     "[cyan]Nhánh MK2:[]\n" +
                     " • Đồng: " + ccMK2 + scanC + "[] / " + cMK2 + "\n" +
                     " • Chì: " + clMK2 + scanL + "[] / " + lMK2 + "\n" +
                     "[purple]Nhánh MK2B:[]\n" +
                     " • Đồng: " + ccMK2B + scanC + "[] / " + cMK2B + "\n" +
                     " • Chì: " + clMK2B + scanL + "[] / " + lMK2B + "\n" +
                     " • Titan: " + ctMK2B + scanT + "[] / " + tMK2B);

            wrap(dialog.cont.add(reqText), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = branchBox("[cyan]===(MK2)===[]");
            wrap(b1.add(isEn() ?
                                 "[white]• Health: [green]+30%[] ([green]1,235[] HP)\n" +
                                 "• Range: [green]+30%[] ([orange]390[] px)\n" +
                                 "• Base Damage: [green]+75%[] ([yellow]21[] DMG/bullet)\n" +
                                 "• [orange]Pulse Charge Core:[] Charges in [orange]0.8s[] to unleash [yellow]20[] spray bullets\n• Triggering [yellow]2[] bursts activates Berserk Mode Lvl 2 for [orange]6s[] (x[yellow]2.6[] Fire Rate)" :
                                 "[white]• Máu cấu trúc: [green]+30%[] ([green]1,235[] HP)\n" +
                                 "• Tầm bắn: [green]+30%[] ([orange]390[] px)\n" +
                                 "• Sát thương gốc: [green]+75%[] ([yellow]21[] DMG/viên)\n" +
                                 "• [orange]Mạch Tích Xung Điện:[] Tích sạc [orange]0.8s[] xả loạt [yellow]20[] viên đạn tỏa\n• Bắn đủ [yellow]2[] đợt kích hoạt Điên Cường Cấp 2 trong [orange]6[] giây (x[yellow]2.6[] tốc bắn)"), 340);
            b1.row();
            b1.button(isEn() ? "[green]UPGRADE MK2[]" : "[green]NÂNG CẤP MK2[]", () -> {
                if (processPartialUpgrade(1, REQ_MK2)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            Table b2 = branchBox("[purple]===(MK2B)===[]");
            wrap(b2.add(isEn() ?
                                 "[white]• Health: [green]+80%[] ([green]1,710[] HP)\n" +
                                 "• Range: [red]-20%[] ([orange]240[] px)\n" +
                                 "• Base Damage: [yellow]27[] DMG/bullet\n" +
                                 "• [orange]Gravity Core Cycle:[] Fires [yellow]3[] consecutive Gravity Lasers ([yellow]18[] DMG, pierces [orange]240[]px)\n• Followed by [yellow]100[] Homing Heavy Bullets" :
                                 "[white]• Máu cấu trúc: [green]+80%[] ([green]1,710[] HP)\n" +
                                 "• Tầm bắn: [red]-20%[] ([orange]240[] px)\n" +
                                 "• Sát thương gốc: [yellow]27[] DMG/viên\n" +
                                 "• [orange]Tuần Hoàn Lõi Trọng Lực:[] Bắn [yellow]3[] phát Laser Trọng Lực liên tiếp ([yellow]18[] DMG, xuyên thấu [orange]240[]px)\n• Sau đó xả [yellow]100[] viên Trọng Đạn tự dẫn đường"), 340);
            b2.row();
            b2.button(isEn() ? "[orange]UPGRADE MK2B[]" : "[orange]NÂNG CẤP MK2B[]", () -> {
                if (processPartialUpgrade(2, REQ_MK2B)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);
            finishDialog(dialog, branches);
        }

        private void openMk3Upgrade() {
            int scanC = scan(0), scanL = scan(1), scanT = scan(2);
            BaseDialog dialog = new BaseDialog(isEn() ? "Dor MK3 Evolution Center" : "Trung tâm tiến hóa MK3 - Dor");

            int cMK3 = Math.max(0, REQ_MK3[0] - paidCopper);
            int lMK3 = Math.max(0, REQ_MK3[1] - paidLead);
            int tMK3 = Math.max(0, REQ_MK3[2] - paidTitanium);

            String ccMK3 = scanC >= cMK3 ? "[green]" : "[red]";
            String clMK3 = scanL >= lMK3 ? "[green]" : "[red]";
            String ctMK3 = scanT >= tMK3 ? "[green]" : "[red]";

            String reqText = isEn() ?
                    ("[yellow]CORE / REQUIRED MK3 COST:[]\n" +
                     " • Copper: " + ccMK3 + scanC + "[] / " + cMK3 + "\n" +
                     " • Lead: " + clMK3 + scanL + "[] / " + lMK3 + "\n" +
                     " • Titanium: " + ctMK3 + scanT + "[] / " + tMK3) :
                    ("[yellow]TÀI NGUYÊN LÕI / YÊU CẦU MK3:[]\n" +
                     " • Đồng: " + ccMK3 + scanC + "[] / " + cMK3 + "\n" +
                     " • Chì: " + clMK3 + scanL + "[] / " + lMK3 + "\n" +
                     " • Titan: " + ctMK3 + scanT + "[] / " + tMK3);

            wrap(dialog.cont.add(reqText), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table b3 = branchBox("[gold]===(MK3 EVOLUTION)===[]");
            wrap(b3.add(isEn() ?
                                 "[white]• Health: [green]+35%[] ([green]1,667[] HP)\n" +
                                 "• Range: [green]+35%[] ([orange]526.5[] px)\n" +
                                 "• Base Damage: [green]+35%[] ([yellow]28.35[] DMG/bullet)\n" +
                                 "• [orange]Ultimate Rampage Burst:[] Spread storm of [yellow]27[] bullets/salvo ([yellow]34.42[] DMG)\n• Triggers Ultimate Rampage state for [orange]8.1s[] (x[yellow]3.51[] Fire Rate)" :
                                 "[white]• Máu cấu trúc: [green]+35%[] ([green]1,667[] HP)\n" +
                                 "• Tầm bắn: [green]+35%[] ([orange]526.5[] px)\n" +
                                 "• Sát thương gốc: [green]+35%[] ([yellow]28.35[] DMG/viên)\n" +
                                 "• [orange]Bão Nộ Tối Thượng:[] Bão đạn tỏa [yellow]27[] viên/loạt ([yellow]34.42[] DMG)\n• Kích hoạt Cuồng Báo Tối Thượng kéo dài [orange]8.1[] giây (x[yellow]3.51[] tốc bắn)"), 340);
            b3.row();
            b3.button(isEn() ? "[gold]UPGRADE MK3[]" : "[gold]NÂNG CẤP MK3[]", () -> {
                if (processPartialUpgrade(3, REQ_MK3)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            dialog.cont.add(b3).width(360);
            dialog.addCloseButton(); dialog.show();
        }

        private void openMk2bUpgrade() {
            int scanC = scan(0), scanL = scan(1), scanT = scan(2);
            BaseDialog dialog = new BaseDialog(isEn() ? "MK2B Branch Evolution Center" : "Trung tâm Rẽ Nhánh Tiến Hóa MK2B");

            int cMK2B1 = Math.max(0, REQ_MK2B1[0] - paidCopper);
            int lMK2B1 = Math.max(0, REQ_MK2B1[1] - paidLead);
            int tMK2B1 = Math.max(0, REQ_MK2B1[2] - paidTitanium);
            int cMK3B = Math.max(0, REQ_MK3B[0] - paidCopper);
            int lMK3B = Math.max(0, REQ_MK3B[1] - paidLead);
            int tMK3B = Math.max(0, REQ_MK3B[2] - paidTitanium);

            String cc2 = scanC >= cMK2B1 ? "[green]" : "[red]";
            String cl2 = scanL >= lMK2B1 ? "[green]" : "[red]";
            String ct2 = scanT >= tMK2B1 ? "[green]" : "[red]";
            String cc3 = scanC >= cMK3B ? "[green]" : "[red]";
            String cl3 = scanL >= lMK3B ? "[green]" : "[red]";
            String ct3 = scanT >= tMK3B ? "[green]" : "[red]";

            String reqText = isEn() ?
                    ("[yellow]CORE / REQUIRED EVOLUTION COST:[]\n" +
                     "[purple]MK2B1 Config:[]\n" +
                     " • Copper: " + cc2 + scanC + "[]/" + cMK2B1 + " | Lead: " + cl2 + scanL + "[]/" + lMK2B1 + " | Titanium: " + ct2 + scanT + "[]/" + tMK2B1 + "\n" +
                     "[pink]Ultimate MK3B:[]\n" +
                     " • Copper: " + cc3 + scanC + "[]/" + cMK3B + " | Lead: " + cl3 + scanL + "[]/" + lMK3B + " | Titanium: " + ct3 + scanT + "[]/" + tMK3B) :
                    ("[yellow]TÀI NGUYÊN LÕI / YÊU CẦU:[]\n" +
                     "[purple]Cấu Hình MK2B1:[]\n" +
                     " • Đồng: " + cc2 + scanC + "[]/" + cMK2B1 + " | Chì: " + cl2 + scanL + "[]/" + lMK2B1 + " | Titan: " + ct2 + scanT + "[]/" + tMK2B1 + "\n" +
                     "[pink]Cấu Hình MK3B:[]\n" +
                     " • Đồng: " + cc3 + scanC + "[]/" + cMK3B + " | Chì: " + cl3 + scanL + "[]/" + lMK3B + " | Titan: " + ct3 + scanT + "[]/" + tMK3B);

            wrap(dialog.cont.add(reqText), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b4 = branchBox("[purple]===(BRANCH 1: MK2B1)===[]");
            wrap(b4.add(isEn() ?
                                 "[white]• Health: [green]+10%[] ([green]1,881[] HP)\n" +
                                 "• Range: [green]+10%[] ([orange]264[] px)\n" +
                                 "• Base Damage: [green]+10%[] ([yellow]29.7[] DMG/bullet)\n" +
                                 "• [orange]Dual Laser Barrage:[] Fires [yellow]2[] parallel Lasers ([yellow]19.8[] DMG) simultaneously\n• Followed by a double storm of [yellow]200[] Homing Heavy Bullets" :
                                 "[white]• Máu cấu trúc: [green]+10%[] ([green]1,881[] HP)\n" +
                                 "• Tầm bắn: [green]+10%[] ([orange]264[] px)\n" +
                                 "• Sát thương gốc: [green]+10%[] ([yellow]29.7[] DMG/viên)\n" +
                                 "• [orange]Bão Laser Kép:[] Bắn [yellow]2[] tia Laser song song cùng lúc ([yellow]19.8[] DMG)\n• Sau đó xả nhân đôi lên [yellow]200[] viên Trọng Đạn tự dẫn đường"), 340);
            b4.row();
            b4.button(isEn() ? "[purple]UPGRADE MK2B1[]" : "[purple]NÂNG CẤP MK2B1[]", () -> {
                if (processPartialUpgrade(4, REQ_MK2B1)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            Table b5 = branchBox("[pink]===(BRANCH 2: ULTIMATE MK3B)===[]");
            wrap(b5.add(isEn() ?
                                 "[white]• Health: [green]+50%[] ([green]2,565[] HP)\n" +
                                 "• Range: [green]+50%[] ([orange]360[] px)\n" +
                                 "• Shotgun Damage: [green]+200%[] ([yellow]81[] DMG/bullet)\n" +
                                 "• [orange]Extreme Gravity Storm:[] Fires [yellow]3[] wide Supercharged Lasers ([yellow]27[] DMG)\n• Replaces single bursts with a [yellow]100[]-bullet Shotgun Storm Salvo (FPS Optimized)" :
                                 "[white]• Máu cấu trúc: [green]+50%[] ([green]2,565[] HP)\n" +
                                 "• Tầm bắn: [green]+50%[] ([orange]360[] px)\n" +
                                 "• Sát thương Shotgun: [green]+200%[] ([yellow]81[] DMG/viên)\n" +
                                 "• [orange]Bão Cực Hạn Trọng Lực:[] Bắn [yellow]3[] đợt Laser Siêu Tải chùm rộng ([yellow]27[] DMG)\n• Chuyển đạn xả lẻ thành loạt Shotgun Bão Tỏa [yellow]100[] viên (Đã tối ưu FPS)"), 340);
            b5.row();
            b5.button(isEn() ? "[pink]UPGRADE MK3B[]" : "[pink]NÂNG CẤP MK3B[]", () -> {
                if (processPartialUpgrade(5, REQ_MK3B)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            branches.add(b4).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b5).width(340);
            finishDialog(dialog, branches);
        }

        private void openInfo() {
            int currentTier = getTier();
            String title = isEn() ? "Dor Turret Stats: " : "Thông số pháo Dor: ";
            String descStr = "";
            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = isEn() ?
                          "[yellow]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "[white]Turret HP: [green]950[]\n" +
                          "Range: [orange]300[] px\n" +
                          "Base Damage: [yellow]12[] DMG/bullet[]\n" +
                          "• [orange]Pulse Charge Burst:[] Normal shots deal [yellow]12[] DMG\n• Charges for [orange]1.0s[] to fire [yellow]10[] spread bullets ([yellow]21[] DMG)\n• Firing [yellow]3[] bursts activates Berserk State for [orange]5s[] (x[yellow]1.5[] Fire Rate)" :
                          "[yellow]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]950[]\n" +
                          "Tầm bắn: [orange]300[] px\n" +
                          "Sát thương gốc: [yellow]12[] DMG/viên[]\n" +
                          "• [orange]Tích Sạc Tỏa Đạn:[] Bắn thường gây [yellow]12[] DMG\n• Tích sạc [orange]1.0s[] xả loạt [yellow]10[] viên đạn tỏa ([yellow]21[] DMG)\n• Bắn đủ [yellow]3[] đợt kích hoạt Trạng Thái Điên Cường trong [orange]5[] giây (x[yellow]1.5[] tốc bắn)";
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = isEn() ?
                          "[cyan]⚡ UPGRADE STATS (MK2) ⚡[]\n" +
                          "[white]Turret HP: [green]1,235[] ([green]+30%[])\n" +
                          "Range: [orange]390[] px ([green]+30%[])\n" +
                          "Base Damage: [yellow]21[] DMG/bullet ([green]+75%[])[]\n" +
                          "• [orange]Enhanced Pulse Charge:[] Fast [orange]0.8s[] charge fires [yellow]20[] spread bullets ([yellow]25.5[] DMG)\n• Firing [yellow]2[] bursts activates Berserk Lvl 2 for [orange]6s[] (x[yellow]2.6[] Fire Rate)" :
                          "[cyan]⚡ THÔNG SỐ NÂNG CẤP (MK2) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]1,235[] ([green]+30%[])\n" +
                          "Tầm bắn: [orange]390[] px ([green]+30%[])\n" +
                          "Sát thương gốc: [yellow]21[] DMG/viên ([green]+75%[])[]\n" +
                          "• [orange]Tích Sạc Tăng Cường:[] Tích sạc [orange]0.8s[] xả loạt [yellow]20[] viên đạn tỏa ([yellow]25.5[] DMG)\n• Bắn đủ [yellow]2[] đợt kích hoạt Điên Cường Cấp 2 trong [orange]6[] giây (x[yellow]2.6[] tốc bắn)";
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = isEn() ?
                          "[purple]⚡ GRAVITY STATS (MK2B) ⚡[]\n" +
                          "[white]Turret HP: [green]1,710[] ([green]+80%[])\n" +
                          "Range: [orange]240[] px ([red]-20%[])\n" +
                          "Base Damage: [yellow]27[] DMG/bullet[]\n" +
                          "• [orange]Gravity Cycle Mode:[] Fires [yellow]3[] Gravity Lasers ([yellow]18[] DMG, [orange]240[]px piercing)\n• Followed immediately by [yellow]100[] Homing Heavy Bullets ([yellow]27[] DMG/bullet)" :
                          "[purple]⚡ THÔNG SỐ TRỌNG LỰC (MK2B) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]1,710[] ([green]+80%[])\n" +
                          "Tầm bắn: [orange]240[] px ([red]-20%[])\n" +
                          "Sát thương gốc: [yellow]27[] DMG/viên[]\n" +
                          "• [orange]Chế Độ Tuần Hoàn Trọng Lực:[] Bắn [yellow]3[] phát Laser Trọng Lực ([yellow]18[] DMG, xuyên thấu [orange]240[]px)\n• Sau đó xả [yellow]100[] viên Trọng Đạn tự dẫn đường ([yellow]27[] DMG/viên)";
            } else if (currentTier == 3) {
                title += "[gold](MK3)[]";
                descStr = isEn() ?
                          "[gold]⚡ EVOLUTION STATS (MK3) ⚡[]\n" +
                          "[white]Turret HP: [green]1,667[] ([green]+35%[])\n" +
                          "Range: [orange]526.5[] px ([green]+35%[])\n" +
                          "Base Damage: [yellow]28.35[] DMG/bullet[]\n" +
                          "• [orange]Ultimate Burst Storm:[] Fires spread storm of [yellow]27[] bullets/salvo ([yellow]34.42[] DMG)\n• Ultimate Rampage state lasts [orange]8.1s[] (x[yellow]3.51[] Fire Rate)" :
                          "[gold]⚡ THÔNG SỐ TIẾN HÓA (MK3) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]1,667[] ([green]+35%[])\n" +
                          "Tầm bắn: [orange]526.5[] px ([green]+35%[])\n" +
                          "Sát thương gốc: [yellow]28.35[] DMG/viên[]\n" +
                          "• [orange]Bão Siêu Nộ Tối Thượng:[] Bão đạn tỏa [yellow]27[] viên/loạt ([yellow]34.42[] DMG)\n• Trạng thái Cuồng Báo Tối Thượng kéo dài [orange]8.1[] giây (x[yellow]3.51[] tốc bắn)";
            } else if (currentTier == 4) {
                title += "[purple](MK2B1)[]";
                descStr = isEn() ?
                          "[purple]⚡ CONFIGURATION STATS (MK2B1) ⚡[]\n" +
                          "[white]Turret HP: [green]1,881[] ([green]+10%[])\n" +
                          "Range: [orange]264[] px ([green]+10%[])\n" +
                          "Base Damage: [yellow]29.7[] DMG/bullet[]\n" +
                          "• [orange]Dual Breakthrough Mode:[] Fires [yellow]2[] parallel Lasers ([yellow]19.8[] DMG)\n• Releases a double storm of [yellow]200[] Homing Heavy Bullets ([yellow]29.7[] DMG/bullet)" :
                          "[purple]⚡ THÔNG SỐ CẤU HÌNH (MK2B1) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]1,881[] ([green]+10%[])\n" +
                          "Tầm bắn: [orange]264[] px ([green]+10%[])\n" +
                          "Sát thương gốc: [yellow]29.7[] DMG/viên[]\n" +
                          "• [orange]Chế Độ Đột Phá Kép:[] Bắn [yellow]2[] tia Laser song song ([yellow]19.8[] DMG)\n• Xả bão đạn nhân đôi [yellow]200[] viên Trọng Đạn tự dẫn đường ([yellow]29.7[] DMG/viên)";
            } else if (currentTier == 5) {
                title += "[pink](MK3B)[]";
                descStr = isEn() ?
                          "[pink]⚡ ULTIMATE STATS (MK3B) ⚡[]\n" +
                          "[white]Turret HP: [green]2,565[] ([green]+50%[])\n" +
                          "Range: [orange]360[] px ([green]+50%[])\n" +
                          "Shotgun Damage: [yellow]81[] DMG/bullet[]\n" +
                          "• [orange]Extreme Gravity Mode:[] Fires [yellow]3[] wide Supercharged Lasers ([yellow]27[] DMG)\n• Replaces burst shots with [yellow]100[]-bullet Shotgun Storm Salvos (FPS Optimized)" :
                          "[pink]⚡ THÔNG SỐ TỐI THƯỢNG (MK3B) ⚡[]\n" +
                          "[white]Máu tháp pháo: [green]2,565[] ([green]+50%[])\n" +
                          "Tầm bắn: [orange]360[] px ([green]+50%[])\n" +
                          "Sát thương Shotgun: [yellow]81[] DMG/viên[]\n" +
                          "• [orange]Chế Độ Cực Hạn Trọng Lực:[] Bắn [yellow]3[] đợt Laser Siêu Tải chùm rộng ([yellow]27[] DMG)\n• Chuyển đạn xả lẻ thành Loạt Shotgun Bão Tỏa [yellow]100[] viên (Đã tối ưu FPS)";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            wrap(infoTable.add(descStr), 360);
            finishDialog(dialog, infoTable);
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            int tier = getTier();
            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openMk1Upgrade)
                    .size(50, 40).tooltip(isEn() ? "Upgrade Dor turret" : "Nâng cấp tháp pháo Dor");
            } else if (tier == 1) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openMk3Upgrade)
                    .size(50, 40).tooltip(isEn() ? "Upgrade turret to MK3" : "Nâng cấp pháo lên MK3");
            } else if (tier == 2) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openMk2bUpgrade)
                    .size(50, 40).tooltip(isEn() ? "Select evolution branch for MK2B" : "Lựa chọn nhánh nâng cấp cho MK2B");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(isEn() ? "[red]DOR SYSTEM HAS REACHED MAX EVOLUTION LEVEL![]" : "[red]HỆ THỐNG DOR ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA CẤP CAO![]"))
                    .size(50, 40).tooltip(isEn() ? "Reached max level of branch" : "Đã đạt cấp tối đa của nhánh");
            }
            table.button(Icon.info, Styles.cleari, 40, this::openInfo)
                .size(50, 40).tooltip(isEn() ? "View detailed stats" : "Xem thông số chi tiết hệ thống");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void updateTile() {
            super.updateTile();
            int tier = getTier();

            if (tier == 2 || tier == 4 || tier == 5) {
                if (burstTimer > 0) {
                    // JS: `this.isShooting && ...` (thiếu ngoặc, luôn truthy) -> Java gọi isShooting() thật (bài học 10/17/35).
                    if (isShooting() && hasAmmo()) reloadCounter += ((ItemTurret) block).reload;
                } else if (customReloadTimer > 0f) {
                    customReloadTimer -= Time.delta;
                    reloadCounter = 0f;
                }
            } else {
                float speedBoost = (tier == 3) ? 3.51f : ((tier == 1) ? 2.6f : 1.5f);
                float currentChargeMax = (tier == 1 || tier == 3) ? CHARGE_TIME_MK2 : CHARGE_TIME_MK1;

                if (berserkTimer > 0f) {
                    berserkTimer -= Time.delta;
                    if (berserkTimer < 0f) berserkTimer = 0f;
                    if (isShooting() && hasAmmo()) reloadCounter += Time.delta * (efficiency * speedBoost);
                } else if (chargeTimer < currentChargeMax) {
                    chargeTimer += Time.delta;
                    if (chargeTimer > currentChargeMax) chargeTimer = currentChargeMax;
                }
            }
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();

            if (tier == 2 || tier == 4 || tier == 5) {
                if (burstTimer > 0) {
                    if (tier == 5) {
                        for (int i = 0; i < 20; i++) {
                            float angleOffset = Mathf.range(35f);
                            shotgunBulletB3.create(this, team, x, y, rotation + angleOffset);
                        }
                        burstShotsFired += 20;
                        if (burstShotsFired >= 100) endBurst();
                    } else {
                        BulletType activeBullet = (tier == 4) ? normalBulletB1 : normalBulletB;
                        int maxShots = (tier == 4) ? 200 : 100;
                        super.shoot(activeBullet);
                        rotation += Mathf.range(45f);
                        burstShotsFired++;
                        if (burstShotsFired >= maxShots) endBurst();
                    }
                } else if (customReloadTimer <= 0f) {
                    if (tier == 4) {
                        Vec2 tr = new Vec2();
                        tr.trns(rotation + 90f, 8f);
                        laserBulletB1.create(this, team, x + tr.x, y + tr.y, rotation);
                        laserBulletB1.create(this, team, x - tr.x, y - tr.y, rotation);
                    } else if (tier == 5) {
                        super.shoot(laserBulletB3);
                    } else {
                        super.shoot(laserBulletB);
                    }

                    Fx.lightningCharge.at(x, y);
                    laserCount++;
                    customReloadTimer = CHARGE_TIME_MK2B;

                    if (laserCount >= 3) {
                        burstTimer = 1;
                        customReloadTimer = 0f;
                        Fx.bigShockwave.at(x, y);
                    }
                }
                return;
            }

            float currentChargeMax = (tier == 1 || tier == 3) ? CHARGE_TIME_MK2 : CHARGE_TIME_MK1;
            float currentBerserkMax = (tier == 3) ? BERSERK_TIME_MK3 : ((tier == 1) ? BERSERK_TIME_MK2 : BERSERK_TIME_MK1);

            BulletType activeNormalBullet = dorNormalBullet;
            if (tier == 1) activeNormalBullet = dormk2NormalBullet;
            if (tier == 3) activeNormalBullet = dormk3NormalBullet;

            int requiredSuperShots = (tier == 1 || tier == 3) ? 2 : 3;

            if (chargeTimer >= currentChargeMax && berserkTimer <= 0f) {
                Fx.lightningCharge.at(x, y);

                if (tier == 3) {
                    for (int i = 0; i < 27; i++) dormk3SmallSprayBullet.create(this, team, x, y, rotation + Mathf.range(8f));
                } else if (tier == 1) {
                    for (int i = 0; i < 20; i++) dormk2SmallSprayBullet.create(this, team, x, y, rotation + Mathf.range(6f));
                } else {
                    for (int i = 0; i < 10; i++) dorSmallSprayBullet.create(this, team, x, y, rotation + Mathf.range(4f));
                }

                superShotCount++;
                chargeTimer = 0f;

                if (superShotCount >= requiredSuperShots) {
                    berserkTimer = currentBerserkMax;
                    superShotCount = 0;
                    if (tier == 1 || tier == 3) Fx.upgradeCore.at(x, y);
                    else Fx.bigShockwave.at(x, y);
                }
            } else {
                super.shoot(activeNormalBullet);
            }
        }

        private void endBurst() {
            burstTimer = 0;
            burstShotsFired = 0;
            laserCount = 0;
            customReloadTimer = CHARGE_TIME_MK2B;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.i(paidCopper);
            write.i(paidLead);
            write.i(paidTitanium);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            paidCopper = read.i();
            paidLead = read.i();
            paidTitanium = read.i();
            chargeTimer = 0f; berserkTimer = 0f; superShotCount = 0;
            laserCount = 0; burstTimer = 0; burstShotsFired = 0; customReloadTimer = 0f;
        }
    }
}
