package newex.content;

import arc.Core;
import arc.func.Floatp;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;

/**
 * Chuyển từ nucleytor.js. Pháo "Nucleytor" 3 cấp (MK1, MK2, MK2B) dùng cơ chế tích năng lượng
 * (energyState 0..1, mỗi phát bắn tăng dần) và trạng thái "Nuclear Amplifier" khi đầy 100%.
 * Block được Java tạo bằng {@code new ItemTurret("nucleytor")} nên nucleytor.hjson KHÔNG có
 * `type:` cấp cao nhất (bài học 29a) — hjson nạp SAU và ghi đè health/size/reload/range/shoot/...
 * ammoTypes KHÔNG có trong hjson nên đặt trong code (giống dor/lazash).
 */
public final class Nucleytor {
    private Nucleytor() {}

    private static final int[] REQ_MK2 = {500, 300, 0};       // titanium, silicon, plastanium
    private static final int[] REQ_MK2B = {800, 400, 200};

    private static final float GAIN_MK1 = 0.015f, COOL_MK1 = 0.15f;
    private static final float GAIN_MK2 = 0.02f, COOL_MK2 = 0.12f;
    private static final float GAIN_MK2B = 0.03f, COOL_MK2B = 0.08f;

    private static Effect wingParticleEffect, mk1TrailEffect, mk2TrailEffect, mk2bTrailEffect,
        mk3HitElectricEffect, nucleytorBuffEffect;

    private static BasicBulletType nucleytorBullet, nucleytorMK2Bullet, nucleytorMK2BBullet;

    public static ItemTurret nucleytor;

    /** JS: isVN() = locale bắt đầu bằng "vi" (bài học 8: giữ đúng hàm của từng file, không gộp chung). */
    private static boolean isVN() { return Core.settings.getString("locale", "en").startsWith("vi"); }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    /** JS `applyGradiusAcceleration`: đạn ì ạch 30 tick đầu rồi tăng tốc gấp 9 lần trong 10 tick. */
    private static void applyGradiusAcceleration(Bullet b, float baseSpeed) {
        float t = b.time;
        float slowSpeed = baseSpeed * 0.15f;
        if (t < 30f) {
            b.vel.setLength(slowSpeed);
        } else {
            float fastSpeed = slowSpeed * 9f;
            float timePassed = t - 30f;
            float progress = Math.min(timePassed / 10f, 1f);
            b.vel.setLength(Mathf.lerp(slowSpeed, fastSpeed, progress));
        }
    }

    /** JS `drawNucleytorWindRing`: vòng elip xoay theo góc bắn, vẽ bằng các đoạn thẳng nối tiếp. */
    private static void drawWindRing(float cx, float cy, float radiusX, float radiusY, float angle, float strokeWidth, Color color) {
        Draw.color(color);
        Lines.stroke(strokeWidth);
        int steps = 16;
        float lastX = 0f, lastY = 0f;
        float cosA = Mathf.cos(angle * Mathf.degRad), sinA = Mathf.sin(angle * Mathf.degRad);
        for (int i = 0; i <= steps; i++) {
            float a = (i * (360f / steps)) * Mathf.degRad;
            float lx = Mathf.cos(a) * radiusX, ly = Mathf.sin(a) * radiusY;
            float rx = cx + (lx * cosA - ly * sinA), ry = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, rx, ry);
            lastX = rx; lastY = ry;
        }
        Draw.reset();
    }

    /** Cả 3 loại đạn dùng chung cơ chế gia tốc Gradius + vòng gió lúc tăng tốc (bài học 21: tái dùng khối chung). */
    private static final class NucleytorBullet extends BasicBulletType {
        final Effect trailEffect;

        NucleytorBullet(float speed, float damage, float w, float h, float life, String front, String back, Effect trailEffect) {
            super(speed, damage);
            width = w; height = h; lifetime = life;
            frontColor = Color.valueOf(front); backColor = Color.valueOf(back);
            hitEffect = Fx.smoke; despawnEffect = Fx.smoke;
            this.trailEffect = trailEffect;
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            applyGradiusAcceleration(b, speed);
            if (Mathf.chance(0.40)) trailEffect.at(b.x, b.y, b.rotation());
        }

        @Override
        public void draw(Bullet b) {
            super.draw(b);
            if (b.time >= 26f) {
                float bAngle = b.rotation();
                float effectTime = b.time - 24f;
                if (effectTime <= 12f) {
                    float progress = effectTime / 20f;
                    float fout = 1f - progress;
                    float offset = 42f - (progress * 90f);
                    float rx = b.x + Angles.trnsx(bAngle, offset);
                    float ry = b.y + Angles.trnsy(bAngle, offset);
                    float zoomRadiusX = 1f + (progress * 9f);
                    float zoomRadiusY = 2f + (progress * 16f);
                    float thickness = 2f * fout;
                    drawWindRing(rx, ry, zoomRadiusX, zoomRadiusY, bAngle, thickness, backColor);
                }
            }
        }
    }

    /** `nucleytorMK2BBullet` ghi đè hitEffect/despawnEffect = mk3HitElectricEffect, nên tách constructor riêng. */
    private static NucleytorBullet withHitEffect(NucleytorBullet b, Effect hit) {
        b.hitEffect = hit; b.despawnEffect = hit;
        return b;
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        wingParticleEffect = new Effect(35, e -> {
            Draw.color(Color.lightGray, Color.gray, e.fin());
            float size = 1.8f * e.fout();
            float speed = 1.4f;
            float rad = e.rotation * Mathf.degRad;
            float px = e.x + Mathf.cos(rad) * speed * e.time;
            float py = e.y + Mathf.sin(rad) * speed * e.time;
            Fill.circle(px, py, size);
            Draw.reset();
        });

        mk1TrailEffect = new Effect(22, e -> {
            Draw.color(Color.white, PerfCore.color("#fff6c8"), e.fin());
            Lines.stroke(1.5f * e.fout());
            float len = 14f * e.fout();
            float rad = e.rotation * Mathf.degRad;
            Lines.line(e.x, e.y, e.x - Mathf.cos(rad) * len, e.y - Mathf.sin(rad) * len);
            Draw.reset();
        });

        mk2TrailEffect = new Effect(22, e -> {
            Draw.color(Color.white, PerfCore.color("#b4baff"), e.fin());
            Lines.stroke(1.5f * e.fout());
            float len = 14f * e.fout();
            float rad = e.rotation * Mathf.degRad;
            Lines.line(e.x, e.y, e.x - Mathf.cos(rad) * len, e.y - Mathf.sin(rad) * len);
            Draw.reset();
        });

        mk2bTrailEffect = new Effect(22, e -> {
            Draw.color(Color.white, PerfCore.color("#ff2525"), e.fin());
            Lines.stroke(1.5f * e.fout());
            float len = 14f * e.fout();
            float rad = e.rotation * Mathf.degRad;
            Lines.line(e.x, e.y, e.x - Mathf.cos(rad) * len, e.y - Mathf.sin(rad) * len);
            Draw.reset();
        });

        mk3HitElectricEffect = new Effect(15, e -> {
            Draw.color(Color.white, Color.sky, e.fin());
            Rand rand = new Rand(e.id);
            Lines.stroke(1f * e.fout());
            for (int i = 0; i < 3; i++) {
                float angle = rand.random(360f);
                float len = rand.random(5f, 15f);
                float tx = e.x + Angles.trnsx(angle, len);
                float ty = e.y + Angles.trnsy(angle, len);
                Lines.line(e.x, e.y, tx, ty);
            }
            Draw.reset();
        });

        // Dùng cho hiệu ứng buff pháo đồng minh khi Nucleytor đang ở trạng thái Amplifier.
        nucleytorBuffEffect = new Effect(40, e -> {
            float size = e.rotation > 0 ? e.rotation : 16f;
            float halfSize = size / 2f;
            Rand rand = new Rand(e.id);
            for (int i = 0; i < 3; i++) {
                float rx = rand.random(-halfSize, halfSize);
                float pLife = rand.random(0.5f, 1f);
                float progress = Math.min(e.fin() / pLife, 1f);
                if (progress < 1f) {
                    float fout = 1f - progress;
                    float travelDistance = rand.random(size * 0.8f, size * 1.3f);
                    float ry = -halfSize + (progress * travelDistance);
                    Color startColor = Color.valueOf("39e75f");
                    Color endColor = Color.valueOf("00a2ff");
                    Color currentColor = startColor.cpy().lerp(endColor, progress);
                    Draw.color(currentColor);
                    float lineLength = 8f * fout;
                    Lines.stroke(1f * fout);
                    Lines.line(e.x + rx, e.y + ry - lineLength / 2f, e.x + rx, e.y + ry + lineLength / 2f);
                }
            }
            Draw.reset();
        });

        nucleytorBullet = new NucleytorBullet(8f, 9f, 9f, 14f, 72f, "ffffff", "ffef9e", mk1TrailEffect);
        nucleytorBullet.pierce = true; nucleytorBullet.pierceCap = 3; nucleytorBullet.pierceBuilding = true;
        nucleytorBullet.knockback = 1f; nucleytorBullet.impact = true;

        nucleytorMK2Bullet = new NucleytorBullet(10f, 9f, 13f, 20f, 78f, "ffeb3a", "50dbc4", mk2TrailEffect);
        nucleytorMK2Bullet.maxRange = 643.1f;
        nucleytorMK2Bullet.pierce = true; nucleytorMK2Bullet.pierceCap = 5; nucleytorMK2Bullet.pierceBuilding = true;
        nucleytorMK2Bullet.knockback = 1.4f; nucleytorMK2Bullet.impact = true;

        nucleytorMK2BBullet = withHitEffect(new NucleytorBullet(9f, 9f, 5f, 64f, 75f, "ffffff", "831006", mk2bTrailEffect), mk3HitElectricEffect);
        nucleytorMK2BBullet.trailChance = 2f; nucleytorMK2BBullet.trailColor = Color.valueOf("ff2525");
        nucleytorMK2BBullet.pierce = false; nucleytorMK2BBullet.pierceBuilding = false;
        nucleytorMK2BBullet.knockback = 2.8f; nucleytorMK2BBullet.impact = true;
        // JS `workspace: true` không phải trường có thật của BulletType (Rhino lặng lẽ bỏ qua, chưa từng
        // có tác dụng) — bỏ khi chuyển sang Java (bài học mới, xem mục "Bài học" bên dưới).

        nucleytor = new ItemTurret("nucleytor");
        nucleytor.health = 5500;
        nucleytor.size = 3;
        nucleytor.reload = 20f;
        nucleytor.range = 490f;
        nucleytor.recoil = 0f;
        nucleytor.configurable = true;
        nucleytor.category = Category.turret;
        nucleytor.ammoTypes.put(Items.silicon, nucleytorBullet);

        nucleytor.addBar("dmg_bonus", (Building e) -> {
            NucleytorBuild f = (NucleytorBuild) e;
            return new Bar(
                (Prov<CharSequence>) () -> "DMG: +" + (int) Math.floor(f.getDmgRatio() * 1200f) + "%",
                (Prov<Color>) () -> Color.orange,
                (Floatp) f::getDmgRatio);
        });
        nucleytor.addBar("as_bonus", (Building e) -> {
            NucleytorBuild f = (NucleytorBuild) e;
            return new Bar(
                (Prov<CharSequence>) () -> "AS: " + ((int) Math.floor(f.getAsRatio() * 50f) >= 0 ? "+" : "") + (int) Math.floor(f.getAsRatio() * 50f) + "%",
                (Prov<Color>) () -> Color.cyan,
                (Floatp) () -> Math.max(f.getAsRatio(), 0f));
        });

        nucleytor.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof NucleytorBuild) ((NucleytorBuild) tile).setTier(value);
        });

        nucleytor.buildType = () -> new NucleytorBuild(nucleytor);
    }

    /** Một "viên đạn" đã nổ công suất đang chờ xử lý sát thương chuẩn (true damage) từng tick (bài học true-dmg). */
    static final class ActiveEntry {
        final Bullet bullet; final float trueDmg;
        ActiveEntry(Bullet b, float t) { bullet = b; trueDmg = t; }
    }

    public static class NucleytorBuild extends ItemTurret.ItemTurretBuild {
        float energyState = 0f;
        int tierState = 0;
        float limitCheck = 0f;
        float customRecoil = 0f;
        float combatProgress = 0f;
        float coreOpen = 0f;
        float amplifierTimer = 0f;
        boolean isAmplified = false;
        float maxAmplifierDuration = 30f * 60f;
        final ArrayList<ActiveEntry> activeBullets = new ArrayList<>();

        public NucleytorBuild(ItemTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            if (val == 0) health = 5500f;
            if (val == 1) health = 8250f;
            if (val == 2) health = 7333f;
            maxHealth = health;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 1) return 643.1f;
            if (tier == 2) return 551.2f;
            return 490f;
        }

        // armor() JS (super$armor + 20 khi khuếch đại): Building v157 không có armor() nên chưa từng chạy (bài 75) → bỏ.

        private String reqText() {
            Building core = team.core();
            if (core == null) return isVN() ? "[red]Không tìm thấy Lõi Đội![]" : "[red]No Team Core Found![]";
            int curTitanium = core.items.get(Items.titanium);
            int curSilicon = core.items.get(Items.silicon);
            int curPlastanium = core.items.get(Items.plastanium);

            String titColor1 = curTitanium >= REQ_MK2[0] ? "[green]" : "[red]";
            String silColor1 = curSilicon >= REQ_MK2[1] ? "[green]" : "[red]";
            String titColor2 = curTitanium >= REQ_MK2B[0] ? "[green]" : "[red]";
            String silColor2 = curSilicon >= REQ_MK2B[1] ? "[green]" : "[red]";
            String plaColor2 = curPlastanium >= REQ_MK2B[2] ? "[green]" : "[red]";

            if (isVN()) {
                return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                    "[cyan]Nhánh MK2:[]\n" +
                    " • Titan: " + titColor1 + curTitanium + "[] / " + REQ_MK2[0] + "\n" +
                    " • Silicon: " + silColor1 + curSilicon + "[] / " + REQ_MK2[1] + "\n" +
                    "[purple]Nhánh MK2B:[]\n" +
                    " • Titan: " + titColor2 + curTitanium + "[] / " + REQ_MK2B[0] + "\n" +
                    " • Silicon: " + silColor2 + curSilicon + "[] / " + REQ_MK2B[1] + "\n" +
                    " • Nhựa: " + plaColor2 + curPlastanium + "[] / " + REQ_MK2B[2];
            } else {
                return "[yellow]CORE RESOURCE REQUIREMENTS:[]\n" +
                    "[cyan]MK2 Branch:[]\n" +
                    " • Titanium: " + titColor1 + curTitanium + "[] / " + REQ_MK2[0] + "\n" +
                    " • Silicon: " + silColor1 + curSilicon + "[] / " + REQ_MK2[1] + "\n" +
                    "[purple]MK2B Branch:[]\n" +
                    " • Titanium: " + titColor2 + curTitanium + "[] / " + REQ_MK2B[0] + "\n" +
                    " • Silicon: " + silColor2 + curSilicon + "[] / " + REQ_MK2B[1] + "\n" +
                    " • Plastanium: " + plaColor2 + curPlastanium + "[] / " + REQ_MK2B[2];
            }
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(isVN() ? "Trung tâm nâng cấp pháo Nucleytor" : "Nucleytor Upgrade Center");
            wrap(dialog.cont.add(reqText()), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add(isVN() ? "[cyan]===(MK2 - XUYÊN PHÁ)===[]" : "[cyan]===(MK2 - PIERCING)===[]").row();
            wrap(b1.add(isVN() ?
                "Cải tiến cấu trúc nòng hạt nhân gia tốc:\n" +
                " [white]• Sát thương cơ bản điều chỉnh thành [green]9 đơn vị[] và tầm bắn rộng [green]643.1 pixel[].[]\n" +
                " [white]• SÁT THƯƠNG CHUẨN: [scarlet]100% True Damage (Trừ thẳng Máu, xuyên giáp, khiên & cơ chế Vela)[]\n" +
                " [white]• Đạn xuyên qua tối đa [yellow]5 mục tiêu[].[]\n" +
                " [white]• Tối ưu hóa tản nhiệt, giữ gia tốc lâu hơn khi dừng bắn.[]\n" +
                " [white]• Nâng cấp giáp tháp pháo, tăng [green]+50% Máu[].[]" :
                "Accelerated nuclear barrel structure:\n" +
                " [white]• Base damage set to [green]9[] with [green]643.1 px[] range.[]\n" +
                " [white]• TRUE DAMAGE: [scarlet]100% True Damage (Bypasses armor, shields & Vela mechanics)[]\n" +
                " [white]• Pierces up to [yellow]5 targets[].[]\n" +
                " [white]• Optimized cooling, retains charge longer when idle.[]\n" +
                " [white]• Enhanced armor, [green]+50% HP[].[]"), 340);
            b1.row();
            b1.button(isVN() ? "[green]KÍCH HOẠT MK2[]" : "[green]ACTIVATE MK2[]", () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.titanium) >= REQ_MK2[0] && core.items.get(Items.silicon) >= REQ_MK2[1]) {
                    NexNet.take(core, Items.titanium, REQ_MK2[0]); NexNet.take(core, Items.silicon, REQ_MK2[1]);
                    NexNet.fx(Fx.upgradeCore, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(4, 4, x, y);
                    configure(Integer.valueOf(1));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(isVN() ? "[red]Không đủ tài nguyên cho nhánh MK2![]" : "[red]Not enough resources for MK2![]");
                }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add(isVN() ? "[purple]===(MK2B - TIẾN HÓA)===[]" : "[purple]===(MK2B - EVOLUTION)===[]").row();
            wrap(b2.add(isVN() ?
                "Chuyển đổi sang lõi nhiệt phân rã:\n" +
                " [white]• Sát thương tinh chỉnh thành [green]9 đơn vị[], gia tốc nhiệt lượng cực nhanh ([green]+3% mỗi phát bắn[]).[]\n" +
                " [white]• SÁT THƯƠNG CHUẨN: [scarlet]100% True Damage (Trừ thẳng Máu, xuyên giáp, khiên & cơ chế Vela)[]\n" +
                " [white]• Tầm bắn đạt [green]551.2 pixel[].[]\n" +
                " [white]• Loại bỏ hoàn toàn khả năng xuyên thấu và tự dẫn đường.[]" :
                "Converted to thermal decay core:\n" +
                " [white]• Base damage set to [green]9[], hyper-fast thermal charge ([green]+3% per shot[]).[]\n" +
                " [white]• TRUE DAMAGE: [scarlet]100% True Damage (Bypasses armor, shields & Vela mechanics)[]\n" +
                " [white]• Range reaches [green]551.2 px[].[]\n" +
                " [white]• Removes piercing and homing capabilities completely.[]"), 340);
            b2.row();
            b2.button(isVN() ? "[orange]KÍCH HOẠT MK2B[]" : "[orange]ACTIVATE MK2B[]", () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.titanium) >= REQ_MK2B[0] && core.items.get(Items.silicon) >= REQ_MK2B[1] && core.items.get(Items.plastanium) >= REQ_MK2B[2]) {
                    NexNet.take(core, Items.titanium, REQ_MK2B[0]); NexNet.take(core, Items.silicon, REQ_MK2B[1]); NexNet.take(core, Items.plastanium, REQ_MK2B[2]);
                    NexNet.fx(Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(4, 4, x, y);
                    configure(Integer.valueOf(2));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(isVN() ? "[red]Không đủ tài nguyên cho nhánh MK2B![]" : "[red]Not enough resources for MK2B![]");
                }
            }).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfoDialog() {
            String title = isVN() ? " Thông số pháo Nucleytor: " : " Nucleytor Turret Stats: ";
            String descStr = "";
            int currentTier = getTier();

            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = isVN() ?
                    "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                    "[lightgray]Máu tháp pháo:[] [green]5,500[]\n" +
                    "[lightgray]Tầm bắn hiệu dụng:[] [orange]490 pixel[]\n" +
                    "[lightgray]Sát thương cơ bản:[] [yellow]9.00[]\n" +
                    "[lightgray]Khả năng xuyên thấu:[] [white]3 mục tiêu[]\n" +
                    "[scarlet]⚠ Giới hạn: Tối đa 10 cấu trúc trên sân[]\n\n" +
                    "[sky]⚡ CƠ CHẾ GIA TỐC HẠT NHÂN:[]\n" +
                    "• [lightgray]Cơ chế bắn tích năng:[] Tháp pháo khởi đầu ở trạng thái yếu nhất. Mỗi phát bắn sẽ tích tụ [green]+1.5%[] gia tốc lõi hạt nhân.\n" +
                    "• [lightgray]Hiệu ứng cực đại:[] Sát thương tăng [orange]+1200%[] và tốc độ bắn tăng [cyan]+50%[].\n" +
                    "• [orange]Trạng thái Nuclear Amplifier:[] Khi tích đầy 100% năng lượng, pháo vào trạng thái [yellow]Nuclear Amplifier trong 30s[]. Nhận thêm [green]+20 Giáp[] và [cyan]+50% Tốc độ bắn[] cho các tháp pháo đồng minh lân cận bán kính 100 pixel.\n" +
                    "• [lightgray]Hiệu ứng đạn:[] Đạn bay chậm trong [yellow]0.5 giây đầu[] để tích năng lượng, sau đó bung xòe vòng từ trường phản lực rồi phóng vụt đi với hiệu ứng vệt xé gió màu vàng nhạt." :
                    "[gold]⚡ BASIC STATS (MK1) ⚡[]\n" +
                    "[lightgray]Turret HP:[] [green]5,500[]\n" +
                    "[lightgray]Effective Range:[] [orange]490 px[]\n" +
                    "[lightgray]Base Damage:[] [yellow]9.00[]\n" +
                    "[lightgray]Piercing Limit:[] [white]3 targets[]\n" +
                    "[scarlet]⚠ Limit: Maximum 10 turrets built[]\n\n" +
                    "[sky]⚡ NUCLEAR ACCELERATION MECHANIC:[]\n" +
                    "• [lightgray]Energy Charging:[] Starts at lowest power. Each shot gains [green]+1.5%[] core acceleration.\n" +
                    "• [lightgray]Peak Effect:[] Damage increases up to [orange]+1200%[] and fire rate up to [cyan]+50%[].\n" +
                    "• [orange]Nuclear Amplifier State:[] At 100% energy, enters [yellow]Nuclear Amplifier for 30s[]. Gains [green]+20 Armor[] and grants [cyan]+50% Attack Speed[] to nearby allied turrets in 100px radius.\n" +
                    "• [lightgray]Bullet Effect:[] Travels slowly for [yellow]first 0.5s[] to charge energy, then emits a magnetic ring and accelerates with pale yellow trails.";
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = isVN() ?
                    "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                    "[lightgray]Máu tháp pháo:[] [green]8,250 [lime](+50%)[]\n" +
                    "[lightgray]Tầm bắn hiệu dụng:[] [orange]643.1 pixel [lime](+31.2%)[]\n" +
                    "[lightgray]Loại Sát Thương:[] [scarlet]100% True Damage (Xuyên giáp, khiên, cơ chế Vela)[]\n" +
                    "[lightgray]Sát thương cơ bản:[] [yellow]9.00[]\n" +
                    "[lightgray]Khả năng xuyên thấu:[] [yellow]5 mục tiêu [lime](+2)[]\n" +
                    "[scarlet]⚠ Giới hạn: Tối đa 10 cấu trúc trên sân[]\n\n" +
                    "[lime]⚡ CƠ CHẾ GIA TỐC HẠT NHÂN (MK2):[]\n" +
                    "• [lightgray]Bắn tích năng:[] Mỗi phát bắn gia tốc thêm [green]+2.0%[] năng lượng tích lũy.\n" +
                    "• [lightgray]Hiệu ứng cực đại:[] Sát thương tăng [orange]+1200%[] và tốc độ bắn tăng [cyan]+50%[].\n" +
                    "• [orange]Trạng thái Nuclear Amplifier:[] Khi tích đầy 100% năng lượng, pháo vào trạng thái [yellow]Nuclear Amplifier trong 60s[]. Nhận thêm [green]+20 Giáp[] và [cyan]+50% Tốc độ bắn[] cho các tháp pháo đồng minh lân cận bán kính 100 pixel.\n" +
                    "• [lightgray]Bộ giữ nhiệt cải tiến:[] Khi dừng bắn, năng lượng hạ nhiệt chậm hơn giúp duy trì trạng thái quá tải lâu hơn." :
                    "[cyan]⚡ BASIC STATS (MK2) ⚡[]\n" +
                    "[lightgray]Turret HP:[] [green]8,250 [lime](+50%)[]\n" +
                    "[lightgray]Effective Range:[] [orange]643.1 px [lime](+31.2%)[]\n" +
                    "[lightgray]Damage Type:[] [scarlet]100% True Damage (Bypasses armor, shields, Vela mechanics)[]\n" +
                    "[lightgray]Base Damage:[] [yellow]9.00[]\n" +
                    "[lightgray]Piercing Limit:[] [yellow]5 targets [lime](+2)[]\n" +
                    "[scarlet]⚠ Limit: Maximum 10 turrets built[]\n\n" +
                    "[lime]⚡ NUCLEAR ACCELERATION MECHANIC (MK2):[]\n" +
                    "• [lightgray]Energy Charging:[] Each shot grants [green]+2.0%[] stored energy.\n" +
                    "• [lightgray]Peak Effect:[] Damage increases up to [orange]+1200%[] and fire rate up to [cyan]+50%[].\n" +
                    "• [orange]Nuclear Amplifier State:[] At 100% energy, enters [yellow]Nuclear Amplifier for 60s[]. Gains [green]+20 Armor[] and grants [cyan]+50% Attack Speed[] to nearby allied turrets in 100px radius.\n" +
                    "• [lightgray]Thermal Retainer:[] Cools down slower when idle, sustaining peak power longer.";
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = isVN() ?
                    "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                    "[lightgray]Máu tháp pháo:[] [green]7,333 [lime](+33.3%)[]\n" +
                    "[lightgray]Tầm bắn hiệu dụng:[] [orange]551.2 pixel [lime](+12.5%)[]\n" +
                    "[lightgray]Loại Sát Thương:[] [scarlet]100% True Damage (Xuyên giáp, khiên, cơ chế Vela)[]\n" +
                    "[lightgray]Sát thương cơ bản:[] [red]9.00[]\n" +
                    "[lightgray]Khả năng xuyên thấu:[] [red]Không (Mất khả năng xuyên)[]\n" +
                    "[scarlet]⚠ Giới hạn: Tối đa 10 cấu trúc trên sân[]\n\n" +
                    "[purple]🔥 CƠ CHẾ GIA TỐC HẠT NHÂN (MK2B):[]\n" +
                    "• [lightgray]Siêu kích phát nổ:[] Mỗi phát bắn gia tốc thần tốc [pink]+3.0%[] nhiệt lượng. Súng đạt đỉnh công suất cực nhanh.\n" +
                    "• [lightgray]Hiệu ứng cực đại:[] Sát thương tăng [orange]+1200%[] và tốc độ bắn tăng [cyan]+50%[].\n" +
                    "• [orange]Trạng thái Nuclear Amplifier:[] Khi tích đầy 100% năng lượng, pháo vào trạng thái [yellow]Nuclear Amplifier trong 30s[]. Nhận thêm [green]+20 Giáp[] và [cyan]+50% Tốc độ bắn[] cho các tháp pháo đồng minh lân cận bán kính 100 pixel." :
                    "[purple]⚡ BASIC STATS (MK2B) ⚡[]\n" +
                    "[lightgray]Turret HP:[] [green]7,333 [lime](+33.3%)[]\n" +
                    "[lightgray]Effective Range:[] [orange]551.2 px [lime](+12.5%)[]\n" +
                    "[lightgray]Damage Type:[] [scarlet]100% True Damage (Bypasses armor, shields, Vela mechanics)[]\n" +
                    "[lightgray]Base Damage:[] [red]9.00[]\n" +
                    "[lightgray]Piercing Limit:[] [red]None (Lost piercing)[]\n" +
                    "[scarlet]⚠ Limit: Maximum 10 turrets built[]\n\n" +
                    "[purple]🔥 NUCLEAR ACCELERATION MECHANIC (MK2B):[]\n" +
                    "• [lightgray]Hyper Charge:[] Each shot charges [pink]+3.0%[] energy rapidly. Reaches max output quickly.\n" +
                    "• [lightgray]Peak Effect:[] Damage increases up to [orange]+1200%[] and fire rate up to [cyan]+50%[].\n" +
                    "• [orange]Nuclear Amplifier State:[] At 100% energy, enters [yellow]Nuclear Amplifier for 30s[]. Gains [green]+20 Armor[] and grants [cyan]+50% Attack Speed[] to nearby allied turrets in 100px radius.";
            }

            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            wrap(infoTable.add(descStr), 360);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            int tier = getTier();

            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openUpgradeDialog)
                    .size(50, 40).tooltip(isVN() ? "Nâng cấp hệ thống Nucleytor" : "Upgrade Nucleytor system");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () ->
                    Vars.ui.showInfo(isVN() ? "[scarlet]HỆ THỐNG NUCLEYTOR ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]" : "[scarlet]NUCLEYTOR HAS REACHED MAXIMUM EVOLUTION TIER![]")
                ).size(50, 40).tooltip(isVN() ? "Đã đạt cấp tối đa" : "Max tier reached");
            }

            table.button(Icon.info, Styles.cleari, 40, this::openInfoDialog)
                .size(50, 40).tooltip(isVN() ? "Xem thông số chi tiết hệ thống" : "View system details");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        /**
         * JS: zero hoá sát thương va chạm mặc định rồi tự tay gây "true damage" từng tick trong updateTile()
         * (bỏ qua giáp/khiên/cơ chế Vela). Vì vòng lặp updateTile() gọi {@code b.remove()} ngay khi trúng MỤC
         * TIÊU ĐẦU TIÊN, đạn coi như chỉ nổ 1 lần — cờ pierce/pierceCap của BulletType (3/5 mục tiêu) chỉ còn
         * tác dụng cho hiệu ứng va chạm hình ảnh của engine (hitEffect, v.v.), không ảnh hưởng số lần gây
         * sát thương thật (xem bài học mới bên dưới). Giữ nguyên hành vi này.
         */
        @Override
        protected void handleBullet(Bullet bullet, float offsetX, float offsetY, float angleOffset) {
            super.handleBullet(bullet, offsetX, offsetY, angleOffset);

            if (bullet != null) {
                float trueDamageVal = bullet.type.damage * (1f + energyState * 12f);
                bullet.damage = 0f;

                if (bullet.type != null) {
                    bullet.type.absorbable = false;
                    bullet.type.hittable = false;
                    bullet.type.reflectable = false;
                    bullet.type.pierceArmor = true;
                }

                activeBullets.add(new ActiveEntry(bullet, trueDamageVal));
            }
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            BulletType bulletToShoot = nucleytorBullet;
            float currentGain = GAIN_MK1;

            if (tier == 1) { bulletToShoot = nucleytorMK2Bullet; currentGain = GAIN_MK2; }
            else if (tier == 2) { bulletToShoot = nucleytorMK2BBullet; currentGain = GAIN_MK2B; }

            super.shoot(bulletToShoot);

            if (!isAmplified) energyState = Math.min(energyState + currentGain, 1f);

            customRecoil = 1f;
        }

        @Override
        public float baseReloadSpeed() { return efficiency * (1f + energyState * 0.5f); }

        float getDmgRatio() { return energyState; }
        float getAsRatio() { return energyState; }

        @Override
        public void updateTile() {
            limitCheck += Time.delta;
            if (limitCheck >= 15f) {
                limitCheck = 0f;
                int count = PerfCore.teamCount(team, nucleytor);
                Building firstBuild = PerfCore.firstOf(team, nucleytor);
                if (count > 10 && this != firstBuild) {
                    mindustry.gen.Call.sendMessage(isVN() ? "[red]Giới hạn: Chỉ được đặt tối đa 10 pháo Nucleytor!" : "[red]Limit: Maximum 10 Nucleytor turrets allowed!");
                    kill();
                    return;
                }
            }

            super.updateTile();
            int tier = getTier();

            // JS: `this.isShooting` thiếu ngoặc đổi hành vi (các bài học 10/17/35...) — ở đây JS đã gọi
            // `isShooting` CÓ ngoặc đúng cách nên không nằm trong nhóm lỗi đó; giữ y nguyên isShooting().
            boolean inCombat = isShooting() || (isActive() && target != null);
            if (inCombat) combatProgress = Mathf.approach(combatProgress, 1f, 0.05f * Time.delta);
            else combatProgress = Mathf.approach(combatProgress, 0f, 0.03f * Time.delta);

            if (inCombat && !isAmplified && Mathf.chance(0.35 * Time.delta)) {
                float rad = rotation * Mathf.degRad;
                float cos = Mathf.cos(rad), sin = Mathf.sin(rad);
                float sideDist = 5f;

                float wingLeftX = x + (sideDist * -sin), wingLeftY = y + (sideDist * cos);
                float wingRightX = x + (-sideDist * -sin), wingRightY = y + (-sideDist * cos);

                float leftAngle = rotation + 145f + Mathf.range(10f);
                float rightAngle = rotation - 145f + Mathf.range(10f);

                wingParticleEffect.at(wingLeftX, wingLeftY, leftAngle);
                wingParticleEffect.at(wingRightX, wingRightY, rightAngle);
            }

            if (energyState >= 1f && !isAmplified) {
                isAmplified = true;
                float durationSeconds = (tier == 1) ? 60f : 30f;
                maxAmplifierDuration = durationSeconds * 60f;
                amplifierTimer = maxAmplifierDuration;

                Fx.shockwave.at(x, y);
                Effect.shake(6f, 6f, x, y);
            }

            if (isAmplified) {
                energyState = 1f;
                amplifierTimer -= Time.delta;

                Units.nearbyBuildings(x, y, 100f, other -> {
                    if (other == null || other == this) return;
                    if (other.team == team && other instanceof Turret.TurretBuild) {
                        Turret.TurretBuild tb = (Turret.TurretBuild) other;
                        if (tb.hasAmmo()) {
                            float speedMultiplier = 0.5f;
                            tb.reloadCounter += Time.delta * Math.max(tb.efficiency, 0.1f) * speedMultiplier;
                        }
                        if (Math.random() < 0.08) {
                            nucleytorBuffEffect.at(other.x, other.y, other.block.size * 8);
                        }
                    }
                });

                if (amplifierTimer <= 0f) {
                    isAmplified = false;
                    energyState = 0f;
                    Fx.blastExplosion.at(x, y);
                    Effect.shake(1.5f, 1.5f, x, y);
                }
            }

            float currentCoolSpeed = COOL_MK1;
            if (tier == 1) currentCoolSpeed = COOL_MK2;
            if (tier == 2) currentCoolSpeed = COOL_MK2B;

            if (!isShooting() || !hasAmmo() || !isActive()) {
                if (energyState > 0f && !isAmplified) {
                    energyState = Math.max(energyState - (currentCoolSpeed * Time.delta / 60f), 0f);
                }
            }

            coreOpen = Mathf.approach(coreOpen, isAmplified ? 1f : 0f, 0.08f * Time.delta);
            customRecoil = Mathf.approach(customRecoil, 0f, 0.12f * Time.delta);

            for (int i = activeBullets.size() - 1; i >= 0; i--) {
                ActiveEntry entry = activeBullets.get(i);
                Bullet b = entry.bullet;
                float trueDmg = entry.trueDmg;

                if (b == null || !b.isAdded() || b.lifetime <= 0f) {
                    activeBullets.remove(i);
                    continue;
                }

                float radius = (b.type != null ? b.type.hitSize : 8f) + 4f;
                Team bTeam = team;

                Groups.unit.intersect(b.x - radius, b.y - radius, radius * 2f, radius * 2f, u -> {
                    if (u != null && u.isValid() && u.team != bTeam && Mathf.dst(b.x, b.y, u.x, u.y) <= radius + u.hitSize) {
                        u.health -= trueDmg;
                        Fx.hitBulletSmall.at(u.x, u.y);
                        if (u.health <= 0f) u.kill();
                        b.remove();
                    }
                });

                if (b.isAdded()) {
                    Building tileBuild = Vars.world.build(World.toTile(b.x), World.toTile(b.y));
                    if (tileBuild != null && tileBuild.team != bTeam) {
                        tileBuild.health -= trueDmg;
                        Fx.hitBulletSmall.at(tileBuild.x, tileBuild.y);
                        if (tileBuild.health <= 0f) tileBuild.kill();
                        b.remove();
                    }
                }
            }
        }

        @Override
        public void draw() {
            // JS: `block.basePrefix` không tồn tại trên Turret v157 (thuộc DrawTurret) => baseRegion không
            // bao giờ tìm thấy => nhánh else (super$draw()) luôn chạy (bài học 13). Java gọi thẳng super.draw().
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = Mathf.cos(rad), sin = Mathf.sin(rad);
            String mod = "nucleytor";

            // Quy ước đặt tên atlas của mod Java "newex": sprite được đóng gói với tiền tố "newex-",
            // dự phòng về tên gốc không tiền tố (giống Elorix/Holyder/Forstarsilum...).
            TextureRegion bodyReg = PerfCore.region("newex-" + mod + "-body", mod + "-body");
            TextureRegion por1Reg = PerfCore.region("newex-" + mod + "-por1", mod + "-por1");
            TextureRegion por2Reg = PerfCore.region("newex-" + mod + "-por2", mod + "-por2");
            TextureRegion wing1Reg = PerfCore.region("newex-" + mod + "-wing1", mod + "-wing1");
            TextureRegion wing2Reg = PerfCore.region("newex-" + mod + "-wing2", mod + "-wing2");
            TextureRegion arr1Reg = PerfCore.region("newex-" + mod + "-arr1", mod + "-arr1");
            TextureRegion arr2Reg = PerfCore.region("newex-" + mod + "-arr2", mod + "-arr2");
            TextureRegion cored1Reg = PerfCore.region("newex-" + mod + "-cored1", mod + "-cored1");
            TextureRegion cored2Reg = PerfCore.region("newex-" + mod + "-cored2", mod + "-cored2");

            if (wing1Reg.found() && wing2Reg.found()) {
                float wSide = combatProgress * 1.414f;
                float wBack = combatProgress * -1.414f;

                float w1x = x + (wSide * -sin) + (wBack * cos);
                float w1y = y + (wSide * cos) + (wBack * sin);
                Draw.rect(wing1Reg, w1x, w1y, rotation - 90);

                float w2x = x + (-wSide * -sin) + (wBack * cos);
                float w2y = y + (-wSide * cos) + (wBack * sin);
                Draw.rect(wing2Reg, w2x, w2y, rotation - 90);
            }

            if (por1Reg.found() && por2Reg.found()) {
                float pSide = combatProgress * 1f;
                float pBack = customRecoil * -8f;

                float p1x = x + (pSide * -sin) + (pBack * cos);
                float p1y = y + (pSide * cos) + (pBack * sin);
                Draw.rect(por1Reg, p1x, p1y, rotation - 90);

                float p2x = x + (-pSide * -sin) + (pBack * cos);
                float p2y = y + (-pSide * cos) + (pBack * sin);
                Draw.rect(por2Reg, p2x, p2y, rotation - 90);
            }

            if (bodyReg.found()) Draw.rect(bodyReg, x, y, rotation - 90);

            if (arr1Reg.found() && arr2Reg.found()) {
                float aForward = combatProgress * 2f;
                float a1x = x + (aForward * cos), a1y = y + (aForward * sin);
                Draw.rect(arr1Reg, a1x, a1y, rotation - 90);
                float a2x = x + (aForward * cos), a2y = y + (aForward * sin);
                Draw.rect(arr2Reg, a2x, a2y, rotation - 90);
            }

            if (coreOpen > 0.01f) {
                Draw.blend(Blending.additive);

                Color coreColor = Color.valueOf("39e75f");
                if (getTier() == 1) coreColor = Color.sky;
                if (getTier() == 2) coreColor = Color.valueOf("ff2525");

                float coreOffsetY = -5f;
                float coreX = x + (coreOffsetY * cos), coreY = y + (coreOffsetY * sin);

                float baseSize = 7f;
                float pulse = Mathf.absin(Time.time, 5f, 0.8f);
                float hexRadius = (baseSize + pulse) * coreOpen;
                float hexRotation = Time.time * 0.8f;

                Draw.color(coreColor);
                Lines.stroke(1.5f * coreOpen);
                Lines.poly(coreX, coreY, 6, hexRadius, hexRotation);
                Fill.poly(coreX, coreY, 6, hexRadius * 0.5f, -hexRotation);

                Draw.blend();
                Draw.reset();
            }

            if (cored1Reg.found() && cored2Reg.found()) {
                float cSide = coreOpen * 3f;
                float c1x = x + (cSide * -sin), c1y = y + (cSide * cos);
                Draw.rect(cored1Reg, c1x, c1y, rotation - 90);
                float c2x = x + (-cSide * -sin), c2y = y + (-cSide * cos);
                Draw.rect(cored2Reg, c2x, c2y, rotation - 90);
            }

            if (isAmplified) {
                float maxRadius = 100f;
                float radius = maxRadius;
                float elapsed = maxAmplifierDuration - amplifierTimer;
                float animDuration = 45f;

                if (elapsed < animDuration) {
                    float progress = elapsed / animDuration;
                    radius = maxRadius * Mathf.pow(progress, 0.5f);
                } else if (amplifierTimer < animDuration) {
                    float progress = amplifierTimer / animDuration;
                    radius = maxRadius * Mathf.pow(progress, 0.5f);
                }

                if (radius > 0.1f) {
                    Color greenColor = Color.valueOf("39e75f");
                    Color yellowBorder = Color.valueOf("ffeb82");
                    float strokeWidth = 1.2f + Mathf.absin(Time.time, 8f, 0.5f);

                    int segments = 60;
                    float step = 360f / segments;

                    Draw.color(yellowBorder);
                    Lines.stroke(strokeWidth + 1.2f);
                    for (int i = 0; i < segments; i += 2) {
                        float angle1 = i * step * Mathf.degRad;
                        float angle2 = (i + 1) * step * Mathf.degRad;
                        Lines.line(x + Mathf.cos(angle1) * radius, y + Mathf.sin(angle1) * radius,
                                   x + Mathf.cos(angle2) * radius, y + Mathf.sin(angle2) * radius);
                    }

                    Draw.color(greenColor);
                    Lines.stroke(strokeWidth);
                    for (int i = 0; i < segments; i += 2) {
                        float angle1 = i * step * Mathf.degRad;
                        float angle2 = (i + 1) * step * Mathf.degRad;
                        Lines.line(x + Mathf.cos(angle1) * radius, y + Mathf.sin(angle1) * radius,
                                   x + Mathf.cos(angle2) * radius, y + Mathf.sin(angle2) * radius);
                    }
                    Draw.reset();
                }
            }
        }
    }
}
