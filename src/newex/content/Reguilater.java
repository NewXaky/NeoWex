package newex.content;

import arc.Core;
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
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import newex.NexNet;

/**
 * Chuyển từ reguilater.js. Pháo laser điện: khóa mục tiêu rồi bắn laser tức thời với sát thương ngẫu nhiên
 * (thường / bạo kích / nổ / siêu bạo kích); MK2 bắn bồi, MK2B tách tia laser phụ.
 */
public final class Reguilater {
    private Reguilater() {}

    private static final int[] REQ_MK2 = {400, 750};      // đồng, silicon
    private static final int[] REQ_MK3 = {1200, 400};     // thori, titan

    private static final Color aimColor = new Color(0f, 1f, 0.66f, 0.27f);
    private static final Color colorNormal = Color.valueOf("#00ffaa");
    private static final Color colorCrit = Color.valueOf("#ffaa00");
    private static final Color colorExplode = Color.valueOf("#d35400");
    private static final Color colorUltimate = Color.valueOf("#ff3333");

    private static Effect muzzleFx, hitFx, areaExplosionFx;
    private static LaserBulletType splitLaser;
    private static BulletType fakeBullet;
    public static PowerTurret reguilater;

    private static boolean en() {
        try {
            String loc = String.valueOf(Core.settings.get("locale", "default"));
            return loc.startsWith("en");
        } catch (Throwable ignored) { }
        return false;
    }

    private static String t(boolean en, String vi, String e) { return en ? e : vi; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static void drawRing(float cx, float cy, float radX, float radY, float laserAngle, float strokeWidth, Color color, boolean fill) {
        Draw.color(color);
        if (!fill) Lines.stroke(strokeWidth);
        int steps = 24;
        float lastX = 0f, lastY = 0f;
        float cosA = (float) Math.cos(laserAngle * Mathf.degRad), sinA = (float) Math.sin(laserAngle * Mathf.degRad);
        if (fill) {
            for (float r = radY; r > 0f; r -= 1.5f) {
                float curX = (r / radY) * radX;
                for (int i = 0; i <= steps; i++) {
                    float a = (i * (360f / steps)) * Mathf.degRad;
                    float lx = (float) Math.cos(a) * curX, ly = (float) Math.sin(a) * r;
                    float px = cx + (lx * cosA - ly * sinA), py = cy + (lx * sinA + ly * cosA);
                    if (i > 0) Lines.line(lastX, lastY, px, py);
                    lastX = px;
                    lastY = py;
                }
            }
        } else {
            for (int i = 0; i <= steps; i++) {
                float a = (i * (360f / steps)) * Mathf.degRad;
                float lx = (float) Math.cos(a) * radX, ly = (float) Math.sin(a) * radY;
                float px = cx + (lx * cosA - ly * sinA), py = cy + (lx * sinA + ly * cosA);
                if (i > 0) Lines.line(lastX, lastY, px, py);
                lastX = px;
                lastY = py;
            }
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        muzzleFx = new Effect(15f, e -> {
            Color c = e.data instanceof Color ? (Color) e.data : colorNormal;
            Draw.color(c);
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * 16f);
            Draw.reset();
        });

        hitFx = new Effect(20f, e -> {
            Color c = e.data instanceof Color ? (Color) e.data : colorNormal;
            Draw.color(Color.white, c, e.fin());
            Lines.stroke(2f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * 20f);
            Angles.randLenVectors(e.id, 8, 26f * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, 2f * e.fout()));
            Draw.reset();
        });

        areaExplosionFx = new Effect(25f, e -> {
            Draw.color(colorExplode, Color.white, e.fin());
            Lines.stroke(3f * e.fout());
            Lines.circle(e.x, e.y, e.fin() * 40f);
            Angles.randLenVectors(e.id, 12, 35f * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, 3f * e.fout()));
            Draw.reset();
        });

        splitLaser = new LaserBulletType(60f);
        splitLaser.length = 160f;
        splitLaser.width = 5f;
        splitLaser.lifetime = 14f;
        splitLaser.colors = new Color[]{new Color(1f, 0.2f, 0.2f, 0.2f), colorUltimate, Color.white};

        // Đạn giả: tự hủy ngay khi tạo, pháo tự gây sát thương bằng code.
        fakeBullet = new BulletType() {
            @Override public void init(Bullet b) { if (b != null) b.remove(); }
            @Override public void draw(Bullet b) { }
        };
        fakeBullet.speed = 0f;
        fakeBullet.lifetime = 1f;

        reguilater = new PowerTurret("reguilater");
        reguilater.health = 13000;
        reguilater.size = 4;
        reguilater.range = 260f;
        reguilater.reload = 9999f;
        reguilater.shootType = fakeBullet;
        reguilater.consumePower(12f);
        reguilater.targetAir = true;
        reguilater.targetGround = true;
        reguilater.configurable = true;
        reguilater.category = Category.turret;

        reguilater.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof ReguBuild) {
                ((ReguBuild) tile).setTier(value);
                if (value == 3) tile.health = 13000f;
            }
        });
        reguilater.buildType = () -> new ReguBuild(reguilater);
    }

    public static class ReguBuild extends PowerTurret.PowerTurretBuild {
        int reguTier = 1;
        float lockTimer = 0f, shootCooldown = 0f, laserActiveTimer = 0f, laserEndX = 0f, laserEndY = 0f;
        Color currentLaserColor = colorNormal;
        float currentLaserSize = 5f;
        float continuousShotCount = 0f;
        final float maxContinuousBonus = 25f;
        float idleResetTimer = 0f;
        boolean isBurstShooting = false;

        public ReguBuild(PowerTurret outer) { outer.super(); }

        @Override public boolean canControl() { return false; }

        public void setTier(int val) { reguTier = val; }
        public int getTier() { return reguTier; }

        @Override public Object config() { return getTier(); }

        @Override
        public float range() {
            return getTier() == 2 ? super.range() * 1.5f : super.range();
        }

        // ---------------- giao diện ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            final boolean en = en();
            if (getTier() == 1) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(en))
                    .size(50, 40).tooltip(t(en, "Nâng cấp tháp pháo lên", "Upgrade turret tier"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(en,
                    "[scarlet]Nâng cấp tháp pháo đã đạt giới hạn![]", "[scarlet]Turret upgrade reached max tier![]")))
                    .size(50, 40).tooltip(t(en, "Nâng cấp tháp pháo", "Turret Upgrade"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(en))
                .size(50, 40).tooltip(t(en, "Trung tâm nâng cấp pháo", "Turret Upgrade Center"));
        }

        private String requirementText(boolean en) {
            Building core = team.core();
            if (core == null) return t(en, "[red]Không tìm thấy Lõi Đội![]", "[red]Team Core Not Found![]");
            int co = core.items.get(Items.copper), si = core.items.get(Items.silicon);
            int th = core.items.get(Items.thorium), ti = core.items.get(Items.titanium);
            String coC = co >= REQ_MK2[0] ? "[green]" : "[red]", siC = si >= REQ_MK2[1] ? "[green]" : "[red]";
            String thC = th >= REQ_MK3[0] ? "[green]" : "[red]", tiC = ti >= REQ_MK3[1] ? "[green]" : "[red]";
            if (en) {
                return "[yellow]CORE STORAGE REQUIREMENTS:[]\n"
                    + "[cyan]MK2 Configuration Branch[]\n"
                    + " • Copper: " + coC + co + "[] / " + REQ_MK2[0] + "\n"
                    + " • Silicon: " + siC + si + "[] / " + REQ_MK2[1] + "\n"
                    + "[purple]MK2B Variant Branch[]\n"
                    + " • Thorium: " + thC + th + "[] / " + REQ_MK3[0] + "\n"
                    + " • Titanium: " + tiC + ti + "[] / " + REQ_MK3[1];
            }
            return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n"
                + "[cyan]Nhánh Cấu Hình MK2[]\n"
                + " • Đồng: " + coC + co + "[] / " + REQ_MK2[0] + "\n"
                + " • Silicon: " + siC + si + "[] / " + REQ_MK2[1] + "\n"
                + "[purple]Nhánh Biến Thể MK2B[]\n"
                + " • Thori: " + thC + th + "[] / " + REQ_MK3[0] + "\n"
                + " • Titan: " + tiC + ti + "[] / " + REQ_MK3[1];
        }

        private void openUpgrade(boolean en) {
            BaseDialog dialog = new BaseDialog(t(en, "Trung tâm nâng cấp pháo", "Turret Upgrade Center"));
            wrap(dialog.cont.add(requirementText(en)), 360);
            dialog.cont.row();
            dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table();
            b1.background(Styles.black6);
            b1.margin(12);
            b1.add("[cyan]===(MK2)===[]").row();
            wrap(b1.add(t(en,
                "[white]• Tầm bắn: [green]+50%[]\n"
                + "• sát thương gốc: [green]+150%[]\n"
                + "• Tốc độ ngắm: [green]+60%[]\n"
                + "• [orange]Nén Áp Suất Kép:[] Mở khóa cơ chế bắn bồi Burst-Shot siêu tốc và bộc phát đòn bạo kích Crit gây gấp đôi sát thương",
                "[white]• Range: [green]+50%[]\n"
                + "• Base Damage: [green]+150%[]\n"
                + "• Aim Speed: [green]+60%[]\n"
                + "• [orange]Dual Pressure Compression:[] Unlocks high-speed Burst-Shot mechanism and Crit strike bursts dealing double damage")), 340);
            b1.row();
            b1.button(t(en, "[green]KÍCH HOẠT MK2[]", "[green]ACTIVATE MK2[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_MK2[0] && core.items.get(Items.silicon) >= REQ_MK2[1]) {
                    NexNet.take(core, Items.copper, REQ_MK2[0]);
                    NexNet.take(core, Items.silicon, REQ_MK2[1]);
                    NexNet.configure(this, 2);
                    NexNet.fx(Fx.upgradeCore, x, y);
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(t(en, "[red]Không đủ tài nguyên cho nhánh MK2![]", "[red]Not enough resources for MK2 branch![]"));
                }
            }).size(180, 38);

            Table b2 = new Table();
            b2.background(Styles.black6);
            b2.margin(12);
            b2.add("[purple]===(MK2B)===[]").row();
            wrap(b2.add(t(en,
                "[white]• Máu cấu trúc: [green]13,000 HP[]\n"
                + "• Tầm bắn: [red]-30%[]\n"
                + "• sát thương gốc: [green]+500%[]\n"
                + "• [orange]Mạch Bão Hòa Hủy Diệt:[] Tự động phân tách chuỗi liên laser phụ (tối đa 6 tia) thiêu rụi mục tiêu lân cận khi đánh trúng kẻ địch",
                "[white]• Structure Health: [green]13,000 HP[]\n"
                + "• Range: [red]-30%[]\n"
                + "• Base Damage: [green]+500%[]\n"
                + "• [orange]Destructive Saturation Circuit:[] Automatically splits sub-laser chains (up to 6 rays) to incinerate nearby targets upon hit")), 340);
            b2.row();
            b2.button(t(en, "[orange]KÍCH HOẠT MK2B[]", "[orange]ACTIVATE MK2B[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.thorium) >= REQ_MK3[0] && core.items.get(Items.titanium) >= REQ_MK3[1]) {
                    NexNet.take(core, Items.thorium, REQ_MK3[0]);
                    NexNet.take(core, Items.titanium, REQ_MK3[1]);
                    NexNet.configure(this, 3);
                    NexNet.fx(Fx.bigShockwave, x, y);
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(t(en, "[red]Không đủ tài nguyên cho nhánh MK2B![]", "[red]Not enough resources for MK2B branch![]"));
                }
            }).size(180, 38);

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

        private void openInfo(boolean en) {
            String title = t(en, " Thông số pháo \"Reguilater\": ", " \"Reguilater\" Turret Stats: ");
            String desc = "";
            int tier = getTier();
            if (tier == 1) {
                title += "[yellow](MK1)[]";
                desc = t(en,
                    "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]13,000[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]260 pixel[] (35 Ô)\n"
                    + "[lightgray]sát thương gốc:[] [yellow]120 DMG / phát bắn[]\n"
                    + "[lightgray]Năng lượng yêu cầu:[] [gainsboro]12.00 đơn vị/giây[]\n\n"
                    + "[sky]⚡ CƠ CHẾ KĨ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Tập trung năng lượng khóa và bắn mục tiêu hỗn hợp Không & Đất.\n"
                    + "• Tốc độ xoay nòng tăng tiến liên tục theo thời gian bám đuổi mục tiêu.\n"
                    + "• Sát thương tăng tiến tuyến tính, đạt tối đa +100% khi duy trì xả súng liên tục.",
                    "[gold]⚡ BASIC STATS (MK1) ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]13,000[]\n"
                    + "[lightgray]Effective Range:[] [orange]260 pixels[] (35 Tiles)\n"
                    + "[lightgray]Base Damage:[] [yellow]120 DMG / shot[]\n"
                    + "[lightgray]Power Required:[] [gainsboro]12.00 units/sec[]\n\n"
                    + "[sky]⚡ SPECIAL SKILL MECHANICS:[]\n"
                    + "• Focuses energy to lock and fire at mixed Air & Ground targets.\n"
                    + "• Barrel rotation speed continuously increases while tracking targets.\n"
                    + "• Damage increases linearly up to +100% during sustained firing.");
            } else if (tier == 2) {
                title += t(en, "[cyan]THÔNG SỐ NÂNG CẤP MK2[]", "[cyan]MK2 UPGRADE STATS[]");
                desc = t(en,
                    "[cyan]⚡ THÔNG SỐ NÂNG CẤP MK2 ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]13,000[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]390 pixel (+50%)[]\n"
                    + "[lightgray]sát thương gốc:[] [yellow]300 DMG / phát bắn (+150%)[]\n"
                    + "[lightgray]Năng lượng yêu cầu:[] [gainsboro]12.00 đơn vị/giây[]\n\n"
                    + "[lime]⚡ CƠ CHẾ KĨ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Nén áp suất mật độ hạt trường điện từ giúp mở rộng cự ly bắn cực đại.\n"
                    + "• Kích hoạt tỷ lệ bắn bồi Burst-Shot siêu tốc.\n"
                    + "• Tự động tích tụ hạt chuyển màu cam, mở khóa tỷ lệ đòn bạo kích Crit gây gấp đôi sát thương.",
                    "[cyan]⚡ MK2 UPGRADE STATS ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]13,000[]\n"
                    + "[lightgray]Effective Range:[] [orange]390 pixels (+50%)[]\n"
                    + "[lightgray]Base Damage:[] [yellow]300 DMG / shot (+150%)[]\n"
                    + "[lightgray]Power Required:[] [gainsboro]12.00 units/sec[]\n\n"
                    + "[lime]⚡ SPECIAL SKILL MECHANICS:[]\n"
                    + "• Electromagnetic particle density compression expands maximum firing range.\n"
                    + "• Triggers high-speed Burst-Shot rate.\n"
                    + "• Automatically accumulates orange particles, unlocking Crit chance dealing double damage.");
            } else if (tier == 3) {
                title += t(en, "[purple]THÔNG SỐ NÂNG CẤP MK2B[]", "[purple]MK2B UPGRADE STATS[]");
                desc = t(en,
                    "[purple]⚡ THÔNG SỐ NÂNG CẤP MK2B ⚡[]\n"
                    + "[lightgray]Máu cấu trúc:[] [green]13,000[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [red]260 pixel (-30%)[]\n"
                    + "[lightgray]sát thương gốc:[] [pink]720 DMG / phát bắn (+500%)[]\n"
                    + "[lightgray]Năng lượng yêu cầu:[] [gainsboro]12.00 đơn vị/giây[]\n\n"
                    + "[purple]🔥 CƠ CHẾ KĨ NĂNG ĐẶC BIỆT:[]\n"
                    + "• Tích tụ quả cầu hạt nhân năng lượng tối thượng tại đồng trục nòng pháo.\n"
                    + "• Khi bắn trúng kẻ địch chính, tự động phân tách chuỗi liên laser phụ (tối đa 6 tia) thiêu rụi các mục tiêu lân cận.\n"
                    + "• Hy sinh cự ly bắn để tập trung mật độ phá hủy tối đa ở tầm gần.",
                    "[purple]⚡ MK2B UPGRADE STATS ⚡[]\n"
                    + "[lightgray]Structure Health:[] [green]13,000[]\n"
                    + "[lightgray]Effective Range:[] [red]260 pixels (-30%)[]\n"
                    + "[lightgray]Base Damage:[] [pink]720 DMG / shot (+500%)[]\n"
                    + "[lightgray]Power Required:[] [gainsboro]12.00 units/sec[]\n\n"
                    + "[purple]🔥 SPECIAL SKILL MECHANICS:[]\n"
                    + "• Charges ultimate energy nuclear sphere coaxial with the barrel.\n"
                    + "• On hitting primary target, splits sub-laser chains (up to 6 rays) burning nearby enemies.\n"
                    + "• Sacrifices firing range to focus maximum destructive density at close range.");
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

        private float requiredLockTime() {
            return isBurstShooting ? 2f : (getTier() == 2 ? 24f : 60f);
        }

        @Override
        public void updateTile() {
            if (!enabled) return;
            if (shootCooldown > 0f) shootCooldown -= Time.delta;
            if (laserActiveTimer > 0f) laserActiveTimer -= Time.delta;

            if (target == null || shootCooldown > 40f) {
                idleResetTimer += Time.delta;
                if (idleResetTimer >= 150f) continuousShotCount = Math.max(0f, continuousShotCount - Time.delta * 0.15f);
            } else {
                idleResetTimer = 0f;
            }

            if (power != null && power.status > 0f) {
                Teamc found = Units.closestTarget(team, x, y, range(), u -> u != null && !u.dead, b -> b != null && b.isValid());
                if (found instanceof Healthc && !((Healthc) found).dead()) {
                    final Healthc ct = (Healthc) found;
                    target = found;
                    rotation = Angles.angle(x, y, found.getX(), found.getY());

                    if (shootCooldown <= 0f) {
                        lockTimer += Time.delta;
                        if (lockTimer >= requiredLockTime()) {
                            lockTimer = 0f;
                            laserActiveTimer = 16f;
                            laserEndX = found.getX();
                            laserEndY = found.getY();
                            int tier = getTier();

                            if (tier == 2) {
                                if (isBurstShooting) {
                                    shootCooldown = 32f;
                                    isBurstShooting = false;
                                } else if (Mathf.random(100f) <= 50f) {
                                    shootCooldown = 4f;
                                    isBurstShooting = true;
                                    Fx.lightningCharge.at(x, y);
                                } else {
                                    shootCooldown = 32f;
                                }
                            } else {
                                shootCooldown = 80f;
                            }

                            if (continuousShotCount < maxContinuousBonus) continuousShotCount++;
                            float dmgMultiplier = 1f + (continuousShotCount / maxContinuousBonus);
                            float baseDmg = (tier == 3 ? 720f : (tier == 2 ? 300f : 120f)) * dmgMultiplier;
                            float chance = Mathf.random(100f);
                            float tx = found.getX(), ty = found.getY();

                            if (chance <= 1f) {
                                currentLaserColor = colorUltimate;
                                currentLaserSize = 12f;
                                ct.damage((baseDmg * 5f) + (ct.maxHealth() * 0.1f));
                                hitFx.at(tx, ty, 0f, colorUltimate);
                                Effect.shake(6f, 6f, x, y);
                            } else if (chance <= 34f) {
                                currentLaserColor = colorNormal;
                                currentLaserSize = 5f;
                                ct.damage(baseDmg + (ct.health() * 0.01f));
                                hitFx.at(tx, ty, 0f, colorNormal);
                                Effect.shake(2f, 2f, x, y);
                            } else if (chance <= 67f) {
                                currentLaserColor = colorCrit;
                                currentLaserSize = 8f;
                                ct.damage(baseDmg * 2f);
                                hitFx.at(tx, ty, 0f, colorCrit);
                                Effect.shake(4f, 4f, x, y);
                            } else {
                                currentLaserColor = colorExplode;
                                currentLaserSize = 6.5f;
                                ct.damage(baseDmg * 0.5f);
                                Damage.damage(team, tx, ty, 40f, baseDmg, false, true);
                                areaExplosionFx.at(tx, ty, 0f, colorExplode);
                                Effect.shake(3f, 3f, x, y);
                            }

                            if (tier == 3) {
                                final Seq<mindustry.gen.Unit> targets = new Seq<>();
                                float len = splitLaser.length;
                                Units.nearbyEnemies(team, tx - len, ty - len, len * 2f, len * 2f, u -> {
                                    if (u != null && u != found && !u.dead) targets.add(u);
                                });
                                if (targets.size > 0) {
                                    targets.shuffle();
                                    int fired = Math.min(6, targets.size);
                                    for (int i = 0; i < fired; i++) {
                                        mindustry.gen.Unit e = targets.get(i);
                                        float ang = Angles.angle(tx, ty, e.x, e.y);
                                        splitLaser.create(this, team, tx, ty, ang, 1f, 1f);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    resetReguStatus();
                }
            } else {
                resetReguStatus();
            }
        }

        void resetReguStatus() {
            target = null;
            lockTimer = Math.max(lockTimer - Time.delta * 2f, 0f);
            isBurstShooting = false;
        }

        @Override
        public void draw() {
            super.draw();
            float gx = x, gy = y;

            if (target instanceof Healthc && !((Healthc) target).dead() && shootCooldown <= 0f) {
                float chargeProgress = Math.min(1f, lockTimer / requiredLockTime());
                if (chargeProgress > 0.05f) {
                    Draw.z(Layer.bullet + 1f);
                    float curRadius = 12f * chargeProgress + Mathf.absin(Time.time, 1.5f, 1f);
                    Color ballColor = continuousShotCount > 15f ? colorCrit : colorNormal;
                    drawRing(gx, gy, curRadius * 0.6f, curRadius * 0.6f, rotation + Time.time * 2f, 0f, ballColor, true);
                    drawRing(gx, gy, curRadius, curRadius, rotation - Time.time * 3f, 1.5f, ballColor, false);
                    Draw.reset();
                }
                Draw.z(Layer.bullet + 1f);
                Draw.color(aimColor);
                Lines.stroke(1f + Mathf.absin(Time.time, 2f, 0.3f));
                Lines.line(gx, gy, target.getX(), target.getY());
                Fill.circle(target.getX(), target.getY(), 2f + Mathf.absin(Time.time, 1.5f, 1f));
                Draw.reset();
            }

            if (laserActiveTimer > 0f) {
                Draw.z(Layer.bullet + 2f);
                float progress = laserActiveTimer / 16f;
                float tt = 1f - progress;
                float zoomStroke = currentLaserSize * (1f + Mathf.absin(Time.time, 1f, 0.2f)) * progress;

                Draw.color(Color.white);
                Lines.stroke(zoomStroke * 0.35f);
                Lines.line(gx, gy, laserEndX, laserEndY);
                Draw.color(currentLaserColor, progress);
                Lines.stroke(zoomStroke);
                Lines.line(gx, gy, laserEndX, laserEndY);

                float laserAngle = Angles.angle(gx, gy, laserEndX, laserEndY);
                float curOffset = Mathf.lerp(10f + 40f, 10f - 24f, tt);
                float ringX = gx + Angles.trnsx(laserAngle, curOffset), ringY = gy + Angles.trnsy(laserAngle, curOffset);
                drawRing(ringX, ringY, 2f * (1f + tt * 6f), 5f * (1f + tt * 6f), laserAngle, 1.5f * progress, currentLaserColor, false);

                float fwd = Mathf.lerp(0f, 45f, tt);
                float bx = gx + Angles.trnsx(laserAngle, fwd), by = gy + Angles.trnsy(laserAngle, fwd);
                drawRing(bx, by, 12f * progress * 1.9f, 12f * progress * 0.2f, laserAngle, 0f, currentLaserColor, true);
                Draw.reset();
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(getTier());
            write.f(continuousShotCount);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.i());
            continuousShotCount = read.f();
        }
    }
}
