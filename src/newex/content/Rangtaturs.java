package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
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
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Icon;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;

/**
 * Chuyển từ rangtaturs.js. Shotgun 7 hiệu ứng ngẫu nhiên + laser định kỳ + Cuồng nộ (MK1/MK2) hoặc bão hỗn hợp (MK2B).
 * Các khối chuỗi song ngữ được ghép nguyên văn từ JS (xem MIGRATION bài học 21).
 */
public final class Rangtaturs {
    private Rangtaturs() {}

    static final class Req {
        final int copper, lead, silicon;
        Req(int c, int l, int s) { copper = c; lead = l; silicon = s; }
    }

    static final int chargeTimeMax = 180, berserkTimeMax = 300, chargeTimeMaxMK2 = 140, berserkTimeMaxMK2 = 360;
    static final Req reqRangtatursMK2 = new Req(1120, 980, 0);
    static final Req reqRangtatursMK2B = new Req(840, 665, 385);

    public static ItemTurret rangtaturs;

    /** Thứ tự 7 trạng thái dùng chung cho mọi nhóm đạn/laser: blasted, melting, burning, freezing, shocked, wet, corroded. */
    static final StatusEffect[] STATUS = {
        StatusEffects.blasted, StatusEffects.melting, StatusEffects.burning, StatusEffects.freezing,
        StatusEffects.shocked, StatusEffects.wet, StatusEffects.corroded };
    static Effect[] FX;
    /** [0]=MK1, [1]=MK2, [2]=MK2B. */
    static final BasicBulletType[][] POOLS = new BasicBulletType[3][7];
    static final LaserBulletType[][] LASERS = new LaserBulletType[3][7];

    static BasicBulletType shot(int set, int i, float speed, float w, float h, float life, float tw, int tl,
                                float sd, float sr, float kb, float sdur, String front, String back) {
        BasicBulletType b = new BasicBulletType();
        b.speed = speed; b.damage = 1f; b.width = w; b.height = h; b.lifetime = life;
        b.frontColor = Color.valueOf(front); b.backColor = Color.valueOf(back); b.trailColor = Color.valueOf(back);
        b.trailWidth = tw; b.trailLength = tl;
        b.splashDamage = sd; b.splashDamageRadius = sr; b.knockback = kb; b.statusDuration = sdur;
        b.hitEffect = FX[i]; b.despawnEffect = FX[i]; b.status = STATUS[i];
        POOLS[set][i] = b;
        return b;
    }

    static void laser(int set, int i, float dmg, float w, float lt, Color[] col) {
        LaserBulletType l = new LaserBulletType();
        l.length = 240f; l.damage = dmg; l.width = w; l.lifetime = lt; l.colors = col;
        l.status = STATUS[i]; l.statusDuration = 180f;
        l.hitEffect = FX[i]; l.smokeEffect = Fx.smoke;
        // JS còn gán chargeEffect = Fx.lancerLaserCharge: bỏ vì pháo không dùng firstShotDelay nên không bao giờ phát (và không chắc trường có ở v157). Bài học 24.
        LASERS[set][i] = l;
    }

    private static void buildBullets() {
        // JS: acidCorrosionEffect (Rhino chọn overload float cho Mathf.rand.random(a, b): bài học 20)
        Effect acid = new Effect(30f, e -> {
            Draw.color(Color.valueOf("#a3e635"), Color.valueOf("#65a30d"), e.fin());
            Lines.stroke(e.fout() * 2f);
            Mathf.rand.setSeed(e.id);
            for (int i = 0; i < 5; i++) {
                float len = Mathf.rand.random(2f, 14f) * e.fin();
                float angle = Mathf.rand.random(360f);
                float size = Mathf.rand.random(1f, 4f) * e.fout();
                Lines.circle(e.x + Angles.trnsx(angle, len), e.y + Angles.trnsy(angle, len), size);
            }
        });
        FX = new Effect[]{ Fx.blastExplosion, Fx.melting, Fx.fire, Fx.freezing, Fx.lightning, Fx.freezing, acid };

        // set, idx, speed, w, h, life, trailW, trailLen, splashDmg, splashR, knock, statusDur, front, back
        shot(0, 0, 7f, 7f, 18f, 43f, 1.5f, 5, 12f, 24f, 1.2f, 140f, "#ffab40", "#ff6d00");
        shot(0, 1, 7f, 7f, 18f, 43f, 1.5f, 5, 12f, 24f, 0.5f, 140f, "#ffa726", "#f57c00");
        shot(0, 2, 7f, 7f, 18f, 43f, 1.5f, 5, 12f, 24f, 0.5f, 140f, "#ff7043", "#d84315");
        shot(0, 3, 7f, 7f, 18f, 43f, 1.5f, 5, 12f, 24f, 0.5f, 140f, "#29b6f6", "#0288d1");
        shot(0, 4, 7f, 7f, 18f, 43f, 1.5f, 5, 12f, 24f, 0.5f, 140f, "#e1bee7", "#ba68c8");
        shot(0, 5, 7.2f, 6.5f, 17f, 43f, 1.4f, 5, 10f, 24f, 0.6f, 180f, "#60a5fa", "#2563eb");
        shot(0, 6, 6.8f, 7f, 17f, 43f, 1.5f, 6, 14f, 26f, 0.3f, 160f, "#bef264", "#65a30d");

        shot(1, 0, 7.7f, 7.7f, 19.8f, 43f, 1.65f, 6, 15f, 28f, 1.4f, 140f, "#ffab40", "#ff6d00");
        shot(1, 1, 7.7f, 7.7f, 19.8f, 43f, 1.65f, 6, 15f, 28f, 0.6f, 140f, "#ffa726", "#f57c00");
        shot(1, 2, 7.7f, 7.7f, 19.8f, 43f, 1.65f, 6, 15f, 28f, 0.6f, 140f, "#ff7043", "#d84315");
        shot(1, 3, 7.7f, 7.7f, 19.8f, 43f, 1.65f, 6, 15f, 28f, 0.6f, 140f, "#29b6f6", "#0288d1");
        shot(1, 4, 7.7f, 7.7f, 19.8f, 43f, 1.65f, 6, 15f, 28f, 0.6f, 140f, "#e1bee7", "#ba68c8");
        shot(1, 5, 7.9f, 7.2f, 18.5f, 43f, 1.55f, 6, 12f, 28f, 0.7f, 180f, "#60a5fa", "#2563eb");
        shot(1, 6, 7.5f, 7.7f, 18.5f, 43f, 1.65f, 7, 17f, 30f, 0.4f, 160f, "#bef264", "#65a30d");

        shot(2, 0, 8.5f, 8f, 20f, 40f, 2f, 6, 22f, 36f, 1.8f, 180f, "#ff5252", "#ff1744");
        shot(2, 1, 8.5f, 8f, 20f, 40f, 2f, 6, 22f, 36f, 0.8f, 180f, "#ffa726", "#f57c00");
        shot(2, 2, 8.5f, 8f, 20f, 40f, 2f, 6, 22f, 36f, 0.8f, 180f, "#ff7043", "#d84315");
        shot(2, 3, 8.5f, 8f, 20f, 40f, 2f, 6, 22f, 36f, 0.8f, 180f, "#29b6f6", "#0288d1");
        shot(2, 4, 8.5f, 8f, 20f, 40f, 2f, 6, 22f, 36f, 0.8f, 180f, "#e1bee7", "#ba68c8");
        shot(2, 5, 8.7f, 7.5f, 19f, 40f, 1.9f, 6, 18f, 36f, 0.9f, 220f, "#93c5fd", "#1d4ed8");
        shot(2, 6, 8.3f, 8f, 19f, 40f, 2f, 7, 26f, 38f, 0.5f, 200f, "#bef264", "#4d7c0f");

        Color[] orange = { Color.valueOf("#ffab40"), Color.valueOf("#ff6d00"), Color.white };
        Color[] blue = { Color.valueOf("#60a5fa"), Color.valueOf("#2563eb"), Color.white };
        Color[] green = { Color.valueOf("#bef264"), Color.valueOf("#4d7c0f"), Color.white };
        Color[] red = { Color.valueOf("#ff1744"), Color.valueOf("#b71c1c"), Color.white };
        // set, damage ×5 trạng thái đầu, width, lifetime, màu; rồi wet và corroded có số riêng
        float[][] lv = {
            // dmg, width, life, wetDmg, wetW, corDmg, corW
            { 45f, 24f, 30f, 42f, 26f, 48f, 22f },
            { 50f, 26.4f, 33f, 46f, 28.5f, 54f, 24f },
            { 65f, 28f, 30f, 60f, 30f, 70f, 26f } };
        for (int s = 0; s < 3; s++) {
            Color[] main = s == 2 ? red : orange;
            float[] p = lv[s];
            for (int i = 0; i < 5; i++) laser(s, i, p[0], p[1], p[2], main);
            laser(s, 5, p[3], p[4], p[2], blue);
            laser(s, 6, p[5], p[6], p[2], green);
        }
    }

    /** Dùng chung cho mọi cấp: bắn loạt đạn mảnh từ nòng. poolType: 0=MK1, 1=MK2, 2=MK2B. */
    static void handleShotgunShoot(RangBuild build, int poolType, int pellets, float spread, float damageMultiplier, float speedMultiplier) {
        float muzzleX = build.x + Angles.trnsx(build.rotation, 12f);
        float muzzleY = build.y + Angles.trnsy(build.rotation, 12f);
        for (int i = 0; i < pellets; i++) {
            float angle = build.rotation + Mathf.range(spread);
            int r = (int) Math.floor(Math.random() * 7);
            Bullet b = POOLS[poolType][r].create(build, build.team, muzzleX, muzzleY, angle);
            if (b != null) {
                b.damage *= damageMultiplier;
                b.vel.scl((1f + Mathf.range(0.3f)) * speedMultiplier);
                b.lifetime *= Mathf.random(0.7f, 1.2f);
            }
        }
    }

    /** Rangtaturs JS: Core.settings.getString("locale").startsWith("en") (thêm kiểm tra null). */
    static boolean isEn() {
        String loc = Core.settings.getString("locale");
        return loc != null && loc.startsWith("en");
    }

    public static void create() {
        buildBullets();
        rangtaturs = new ItemTurret("rangtaturs");
        rangtaturs.squareSprite = false;
        rangtaturs.health = 1400; rangtaturs.size = 3; rangtaturs.reload = 95f; rangtaturs.range = 240f; rangtaturs.configurable = true;
        // hjson (ammoTypes: titanium, health 950, reload 20, range 352...) nạp SAU và ghi đè các giá trị này, y như bản JS. Bài học 23.
        rangtaturs.ammo(Items.silicon, POOLS[0][0]);
        rangtaturs.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof RangBuild) ((RangBuild) tile).setTier(value);
        });
        rangtaturs.buildType = () -> new RangBuild(rangtaturs);
    }

    public static class RangBuild extends ItemTurret.ItemTurretBuild {
        int evolutionTier = 0, superShotCount = 0, laserCount = 0, burstTimer = 0;
        float chargeTimer = 0f, berserkTimer = 0f, customReloadTimer = 0f;

        public RangBuild(ItemTurret outer) { outer.super(); }

        void setTier(int tierValue) {
            if (tierValue == 1 && evolutionTier == 0) {
                evolutionTier = 1;
                Fx.upgradeCore.at(x, y);
            } else if (tierValue == 2 && evolutionTier == 0) {
                evolutionTier = 2;
                Fx.bigShockwave.at(x, y);
            }
        }

        @Override
        public Object config() { return Integer.valueOf(evolutionTier); }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = isEn();
            if (evolutionTier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(isEn ? "Upgrade Rangtaturs Turret" : "Nâng cấp tháp pháo Rangtaturs");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(isEn
                    ? "[scarlet]RANGTATURS SYSTEM HAS REACHED MAXIMUM EVOLUTION TIER![]"
                    : "[scarlet]HỆ THỐNG RANGTATURS ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]"))
                    .size(50, 40).tooltip(isEn ? "Max level reached" : "Đã đạt cấp tối đa");
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(isEn ? "View Detailed System Stats" : "Xem thông số chi tiết hệ thống");
        }

        private String requirementText() {
            boolean isEn = isEn();
            CoreBuild core = team.core();
            if (core == null) return isEn ? "[red]Core Vault not found![]" : "[red]Không tìm thấy Kho cốt lõi![]";
            int currentCopper = core.items.get(Items.copper);
            int currentLead = core.items.get(Items.lead);
            int currentSilicon = core.items.get(Items.silicon);
            String copColor1 = currentCopper >= reqRangtatursMK2.copper ? "[green]" : "[red]";
            String ledColor1 = currentLead >= reqRangtatursMK2.lead ? "[green]" : "[red]";
            String copColor2 = currentCopper >= reqRangtatursMK2B.copper ? "[green]" : "[red]";
            String ledColor2 = currentLead >= reqRangtatursMK2B.lead ? "[green]" : "[red]";
            String silColor2 = currentSilicon >= reqRangtatursMK2B.silicon ? "[green]" : "[red]";
            if (isEn) {
                        return "[yellow]CORE VAULT REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Branch:[]\n" +
                               " • Copper: " + copColor1 + currentCopper + "[] / " + reqRangtatursMK2.copper + "\n" +
                               " • Lead: " + ledColor1 + currentLead + "[] / " + reqRangtatursMK2.lead + "\n" +
                               "[purple]MK2B Branch:[]\n" +
                               " • Copper: " + copColor2 + currentCopper + "[] / " + reqRangtatursMK2B.copper + "\n" +
                               " • Lead: " + ledColor2 + currentLead + "[] / " + reqRangtatursMK2B.lead + "\n" +
                               " • Silicon: " + silColor2 + currentSilicon + "[] / " + reqRangtatursMK2B.silicon;
            } else {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Đồng: " + copColor1 + currentCopper + "[] / " + reqRangtatursMK2.copper + "\n" +
                               " • Chì: " + ledColor1 + currentLead + "[] / " + reqRangtatursMK2.lead + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Đồng: " + copColor2 + currentCopper + "[] / " + reqRangtatursMK2B.copper + "\n" +
                               " • Chì: " + ledColor2 + currentLead + "[] / " + reqRangtatursMK2B.lead + "\n" +
                               " • Silic: " + silColor2 + currentSilicon + "[] / " + reqRangtatursMK2B.silicon;
            }
        }

        private void upgrade(int tier, BaseDialog dialog, boolean isEn) {
            CoreBuild core = team.core();
            Req r = tier == 1 ? reqRangtatursMK2 : reqRangtatursMK2B;
            if (core != null && core.items.get(Items.copper) >= r.copper && core.items.get(Items.lead) >= r.lead
                && core.items.get(Items.silicon) >= r.silicon) {
                NexNet.take(core, Items.copper, r.copper);
                NexNet.take(core, Items.lead, r.lead);
                if (tier == 2) NexNet.take(core, Items.silicon, r.silicon);
                NexNet.configure(this, Integer.valueOf(tier));
                dialog.hide(); deselect();
            } else if (tier == 1) {
                Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên nâng cấp cho nhánh MK2![]");
            } else {
                Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên nâng cấp cho nhánh MK2B![]");
            }
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(isEn ? "Rangtaturs Upgrade Center" : "Trung tâm nâng cấp pháo Rangtaturs");
            Cell<Label> reqCell = dialog.cont.add(requirementText());
            reqCell.width(360f);
            reqCell.get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branchesTable = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
                String b1Text = isEn ?
                    "Firepower Density Boost Configuration:\n" +
                    " [white]• Status Volley: Increases to [green]53 shrapnel bullets[].[]\n" +
                    " [white]• Super Laser Charge: Charge time reduced to [yellow]2.33s (140 ticks)[] [lime](-22.2%) [].[]\n" +
                    " [white]• Laser Detonation: Exponential damage spikes to [red]310%[] base firepower.[]\n" +
                    " [white]• Berserk Cycle: Duration extended to [orange]6.0s[] [lime](+20%)[], boosting standard fire rate to [red]250%[].[]" :
                    "Cấu hình tăng cường mật độ mảnh hỏa lực:\n" +
                    " [white]• Mưa đạn trạng thái: Đẩy mạnh lên [green]53 viên đạn mảnh[].[]\n" +
                    " [white]• Siêu Laser tích tụ: Thời gian sạc rút ngắn còn [yellow]2.33 giây (140 tick)[] [lime](Giảm -22.2%)[].[]\n" +
                    " [white]• Laze kích nổ: Sát thương đột biến nhân tiến đạt mức [red]310% hỏa lực[] cơ bản.[]\n" +
                    " [white]• Chu kỳ Cuồng nộ: Kéo dài thời gian bộc phá lên [orange]6.0 giây[] [lime](+20%)[], đẩy tốc bắn thường lên [red]250%[].[]";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f); b1D.get().setWrap(true); b1D.get().setAlignment(Align.left);
            b1.row();
            b1.button(isEn ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> upgrade(1, dialog, isEn)).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
                String b2Text = isEn ?
                    "Ultimate Area Storm Destruction Configuration:\n" +
                    " [white]• Hybrid Superstorm: Shotgun burst pellet count increased to [green]68 bullets[].[]\n" +
                    " [white]• Overclock Cycle: Fixed standard reload time to [yellow]1.16s (70 ticks)[].[]\n" +
                    " [white]• Burst Mode: Fire 6 shots to unleash a massive unipolar storm of [orange]135 compressed bullets[].[]\n" +
                    " [white]• Thermal Overload: Forced cooling system locks barrels for [purple]3.0s (180 ticks)[].[]" :
                    "Cấu hình tối thượng hủy diệt bão đạn diện rộng:\n" +
                    " [white]• Siêu bão hỗn hợp: Loạt bắn shotgun tăng số lượng mảnh lên [green]68 viên đạn[].[]\n" +
                    " [white]• Chu kỳ nén ép xung: Khóa cố định thời gian năng lượng hồi loạt bắn thường còn [yellow]1.16 giây (70 tick)[].[]\n" +
                    " [white]• Tuyệt chiêu Xả Bão (Burst): Bắn đủ 6 phát giải phóng bão đơn cực cực đại [orange]135 viên đạn nén[].[]\n" +
                    " [white]• Quá tải nhiệt: Hệ thống rơi vào trạng thái làm mát cưỡng bức, ngắt nòng trong [purple]3.0 giây (180 tick)[].[]";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f); b2D.get().setWrap(true); b2D.get().setAlignment(Align.left);
            b2.row();
            b2.button(isEn ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> upgrade(2, dialog, isEn)).size(180, 38);

            branchesTable.add(b1).width(340f); branchesTable.row();
            branchesTable.add().height(12).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = isEn ? " Rangtaturs Turret Stats: " : " Thông số pháo Rangtaturs: ";
            String descStr = "";
            int currentTier = evolutionTier;
            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = isEn ?
                          "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Block Size:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Effective Range:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixels[]\n" +
                          "Target Type:[] [yellow]Ground targets only[]\n\n" +
                          "[sky]⚡ FIREPOWER & MECHANICS:[]\n" +
                          "• [lightgray]Standard Shotgun:[] Fires a spread of [green]39 shrapnel bullets[] applying random status effects from a 7-debuff pool.\n" +
                          "• [lightgray]Charge Cycle:[] Continuous standard fire for [yellow]3.0s (180 ticks)[] activates Grand Laser Beam dealing [gold]280%[] exponential damage.\n" +
                          "• [lightgray]Berserk Protocol:[] Firing 3 Laser shots overcharges the core for [orange]5.0s (300 ticks)[], granting [green]+150%[] reload speed." :
                          "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Kích thước khối:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixel[]\n" +
                          "Mục tiêu phát xạ:[] [yellow]Mặt đất (Không bắn phòng không)[]\n\n" +
                          "[sky]⚡ ĐẶC TÍNH HỎA LỰC VÀ CHU KỲ CƠ CHẾ:[]\n" +
                          "• [lightgray]Bắn Shotgun thường:[] Phóng loạt gồm [green]39 viên đạn mảnh[] mang ngẫu nhiên hiệu ứng bộ 7 trạng thái bất lợi.\n" +
                          "• [lightgray]Chu kỳ Tích tụ (Sạc điểm):[] Bắn thường liên tục trong [yellow]3.0 giây (180 tick)[] kích hoạt Đại Pháo Laser gây sát thương nhân tiến lên [gold]280%[].[]\n" +
                          "• [lightgray]Mạch Cuồng nộ (Berserk):[] Tích đủ 3 phát Laze đưa lõi pháo vào trạng thái quá tải trong [orange]5.0 giây (300 tick)[], tăng tốc độ hồi đạn bắn thường lên [green]+150%[].";
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = isEn ?
                          "[cyan]⚡ BASE STATS (MK2) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Block Size:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Effective Range:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixels[]\n\n" +
                          "[lime]⚡ FIREPOWER & MECHANICS:[]\n" +
                          "• [lightgray]Status Barrage:[] Shotgun pellet count increased to [green]53 bullets[].\n" +
                          "• [lightgray]Short-Circuit Charge:[] Laser charge time reduced to [yellow]2.33s (140 ticks) [lime](-22.2%)[], Laser core damage boosted to [red]310%[].[]\n" +
                          "• [lightgray]Super Berserk:[] Overcharge duration increased to [orange]6.0s (360 ticks) [lime](+20%)[], driving normal reload speed up to [red]250%[].":
                          "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Kích thước khối:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixel[]\n\n" +
                          "[lime]⚡ ĐẶC TÍNH HỎA LỰC VÀ CHU KỲ CƠ CHẾ:[]\n" +
                          "• [lightgray]Mưa đạn trạng thái:[] Số lượng đạn Shotgun tăng lên [green]53 viên mảnh[].\n" +
                          "• [lightgray]Sạc xung ngắn mạch:[] Thời gian tích tụ năng lượng Laser rút xuống còn [yellow]2.33 giây (140 tick) [lime](Giảm -22.2%)[], sát thương Laser lõi tăng vọt đạt [red]310%[].[]\n" +
                          "• [lightgray]Chu kỳ Siêu cuồng nộ:[] Thời gian bộc phá tăng lên [orange]6.0 giây (360 tick) [lime](+20%)[], ép tốc độ xả đạn bắn thường lên mức cực đại [red]250%[].";
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = isEn ?
                          "[purple]⚡ BASE STATS (MK2B) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Block Size:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Effective Range:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixels[]\n\n" +
                          "[purple]🔥 🔥 GRAVITY STATUS SUPERSTORM MECHANICS:[]\n" +
                          "• Removes old point-charging and Berserk gauge mechanisms completely.\n" +
                          "• [lightgray]Hybrid Shotgun Storm:[] Fires a compressed Laser beam accompanied by a heavy density shotgun spray of up to [green]68 bullets[].\n" +
                          "• [lightgray]Overclock Cycle:[] Locks normal reload intervals strictly to [yellow]1.16s (70 ticks)[].[]\n" +
                          "• [lightgray]Burst Mode:[] Accumulates 6 shots to automatically unleash a hyper storm of [red]135 hybrid bullets[], followed by a forced cooling lockdown for [white]3.0s (180 ticks)[].":
                          "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]" + Math.round(health) + "[]\n" +
                          "📐 Kích thước khối:[] [white]" + block.size + "x" + block.size + "[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]" + Math.round(((ItemTurret) block).range) + " pixel[]\n\n" +
                          "[purple]🔥 🔥 CƠ CHẾ SIÊU BÃO LOẠN TRẠNG THÁI TRỌNG LỰC:[]\n" +
                          "• Hệ thống loại bỏ hoàn toàn cơ chế sạc tích tụ điểm và thanh cuồng nộ cũ.\n" +
                          "• [lightgray]Bão Shotgun hỗn hợp:[] Phóng ra tia Laze nén kèm chùm đạn shotgun tập trung mật độ cao lên tới [green]68 viên đạn[].\n" +
                          "• [lightgray]Cơ chế nén ép xung:[] Khóa cố định chu kỳ thời gian hồi giữa các loạt bắn thông thường thành [yellow]1.16 giây (70 tick)[].[]\n" +
                          "• [lightgray]Tuyệt chiêu Xả Bão (Burst Mode):[] Tích lũy đủ 6 phát bắn, pháo tự động giải phóng một siêu bão tổng lực cực đại gồm [red]135 viên đạn hỗn hợp[], sau đó rơi vào trạng thái đóng băng hệ thống làm mát ngắt nòng trong [white]3.0 giây (180 tick)[].";
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
        public void updateTile() {
            super.updateTile();

            if (evolutionTier == 2) {
                if (customReloadTimer > 0f) {
                    customReloadTimer -= Time.delta;
                    reloadCounter = 0f;
                }
                return;
            }

            int maxCharge = evolutionTier == 1 ? chargeTimeMaxMK2 : chargeTimeMax;
            float efficiencyMultiplier = evolutionTier == 1 ? 2.5f : 1.5f;

            if (berserkTimer > 0f) {
                berserkTimer -= Time.delta;
                if (berserkTimer < 0f) berserkTimer = 0f;
                // JS: this.isShooting thiếu () nên luôn truthy; Java dùng isShooting() thật (bài học 10/17).
                if (isShooting() && hasAmmo()) reloadCounter += Time.delta * (efficiency * efficiencyMultiplier);
            } else if (chargeTimer < maxCharge) {
                chargeTimer += Time.delta;
                if (chargeTimer > maxCharge) chargeTimer = maxCharge;
            }
        }

        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);

            int rLaser = (int) Math.floor(Math.random() * 7);
            float muzzleX = x + Angles.trnsx(rotation, 12f);
            float muzzleY = y + Angles.trnsy(rotation, 12f);
            BulletType cur = peekAmmo();
            float baseDmg = cur != null ? cur.damage : type.damage;

            if (evolutionTier == 2) {
                if (customReloadTimer <= 0f) {
                    if (burstTimer == 1) {
                        handleShotgunShoot(this, 2, 135, 35f, 1f, 1.2f);
                        Fx.bigShockwave.at(x, y);
                        burstTimer = 0;
                        laserCount = 0;
                        customReloadTimer = 180f;
                    } else {
                        LaserBulletType targetLaser = LASERS[2][rLaser];
                        targetLaser.damage = baseDmg * 3.1f;
                        targetLaser.create(this, team, muzzleX, muzzleY, rotation);
                        Fx.lightningCharge.at(x, y);

                        handleShotgunShoot(this, 2, 68, 4f, 1f, 1.1f);

                        laserCount++;
                        customReloadTimer = 70f;
                        if (laserCount >= 6) burstTimer = 1;
                    }
                }
                return;
            }

            int maxCharge = evolutionTier == 1 ? chargeTimeMaxMK2 : chargeTimeMax;
            int maxBerserk = evolutionTier == 1 ? berserkTimeMaxMK2 : berserkTimeMax;
            float laserDmgMultiplier = evolutionTier == 1 ? 3.1f : 2.8f;

            if (chargeTimer >= maxCharge && berserkTimer <= 0f) {
                Fx.lightningCharge.at(x, y);
                LaserBulletType targetLaserBullet = LASERS[evolutionTier == 1 ? 1 : 0][rLaser];
                targetLaserBullet.damage = baseDmg * laserDmgMultiplier;
                targetLaserBullet.create(this, team, muzzleX, muzzleY, rotation);

                superShotCount++;
                chargeTimer = 0f;
                if (superShotCount >= 3) {
                    berserkTimer = maxBerserk;
                    superShotCount = 0;
                    Fx.upgradeCore.at(x, y);
                }
            } else if (evolutionTier == 1) {
                handleShotgunShoot(this, 1, 53, 8f, 0.95f, 1f);
            } else {
                handleShotgunShoot(this, 0, 39, 8f, 1f, 1f);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(evolutionTier);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            evolutionTier = read.b();
        }
    }
}
