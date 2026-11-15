package newex.content;

import arc.Core;
import arc.Events;
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
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Call;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ blaw.js (bản tối ưu, giữ nguyên cơ chế gốc). Pháo Blaw 2 nòng: nòng TRÁI bắn đạn Băng (đóng băng, xuyên),
 * nòng PHẢI bắn đạn Hỏa (nổ lan). Khuếch đại sát thương theo HP mục tiêu + bạo kích 80% x2.2.
 * Nâng cấp MK2 (tầm xa, nổ lan mạnh, +20% tốc nạp) hoặc MK2B (máu +150%, tầm ngắn, bắn chùm shotgun theo đợt,
 * hồi chiêu 3 giây sau mỗi 2 đợt).
 *
 * SỬA SO VỚI BẢN CŨ:
 *  - size: blaw.hjson (nạp SAU Java) đặt size 2 và sprite blaw/blaw-ap/blaw-at đều 64x64 = 2x2 ô. Bản cũ đặt size 3 rồi vẽ nền
 *    "reinforced-block-3" (96x96) đè lên pháo 2x2 nên nhìn như bị sai kích thước. Giờ size = 2 và nền lấy theo size THẬT trong load().
 *  - Mất đạn đỏ: mk() đặt pierceCap = 0 cho đạn Hỏa (không xuyên) nên đạn bị huỷ ngay tick đầu. Giờ chỉ đặt pierceCap cho đạn xuyên.
 *  - read(): bản cũ gọi setTier() nên mỗi lần load save máu bị hồi đầy. Giờ chỉ khôi phục cấp.
 */
public final class Blaw {
    private Blaw() {}

    /** false (mặc định, đúng JS gốc): luân phiên trái (Băng) -> phải (Hỏa) -> trái ... mỗi lượt 1 viên. true: cả 2 nòng bắn cùng lúc. */
    private static final boolean DUAL_BARREL = false;
    /** MK2B: số viên/nòng/đợt. Luân phiên = 10; bắn đôi = 5 (đủ 10 viên/đợt, 2 đợt = 20 viên đúng mô tả). */
    private static final int SHOTGUN_PELLETS = DUAL_BARREL ? 5 : 10;

    private static final int[] REQ_MK2 = {4000, 4000, 0};   // đồng, chì, titan
    private static final int[] REQ_MK2B = {4000, 4000, 2000};

    private static final float HP_MK1 = 950f, HP_MK2B = 1425f;
    private static final float RANGE_MK1 = 442f, RANGE_MK2 = 663f, RANGE_MK2B = 309.4f;
    private static final float BARREL_OFFSET = 6f, RECOIL_LEN = 4f;

    private static Effect hitFx;
    private static BasicBulletType blueMk1, redMk1, blueMk2, redMk2, blueMk2b, redMk2b;
    private static Sound shootSoundCannon;
    private static BlawBlock base;

    private static boolean en() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static String t(String enText, String viText) {
        return en() ? enText : viText;
    }

    /** Block riêng để nạp sprite theo size THẬT (sau khi hjson đã ghi đè), giống JS: load() + customBaseRegion. */
    public static class BlawBlock extends ItemTurret {
        TextureRegion leftBarrel, rightBarrel, customBase;

        public BlawBlock(String name) { super(name); }

        @Override
        public void load() {
            super.load();
            leftBarrel = PerfCore.region("newex-blaw-at", "blaw-at");
            rightBarrel = PerfCore.region("newex-blaw-ap", "blaw-ap");
            customBase = PerfCore.region("newex-reinforced-block-" + size, "reinforced-block-" + size);
        }
    }

    public static void create() {
        hitFx = new Effect(40f, e -> {
            Draw.color(e.color);
            float p = Interp.pow2Out.apply(e.fin());
            Draw.alpha(0.35f * e.fout());
            Fill.circle(e.x, e.y, 35f * p);
            Lines.stroke(2.5f * e.fout());
            Lines.circle(e.x, e.y, 40f * p);
            Lines.stroke(1.5f * e.fout());
            Lines.circle(e.x, e.y, 20f * p);
            Draw.reset();
        });

        StatusEffect freeze = StatusEffects.freezing, blast = StatusEffects.blasted;
        //            speed  dmg  w   h  màu       nổ  bán kính  status  thời gian  xuyên  cap  lifetime
        blueMk1 = mk(3.25f, 25f, 9f, 22f, "0031FFFF", 0f, 0f, freeze, 600f, true, 3, 70f);
        redMk1 = mk(3.25f, 25f, 9f, 22f, "FF0000FF", 40f, 24f, blast, 80f, false, 0, 70f);
        blueMk2 = mk(3.64f, 50f, 10f, 24f, "0031FFFF", 0f, 0f, freeze, 600f, true, 3, 70f);
        redMk2 = mk(3.64f, 50f, 10f, 24f, "FF0000FF", 120f, 32f, blast, 80f, false, 0, 70f);
        blueMk2b = mk(15.86f, 550f, 5f, 12f, "0031FFFF", 0f, 0f, freeze, 300f, true, 2, 19.51f);
        redMk2b = mk(15.86f, 550f, 5f, 12f, "FF5A00FF", 40f, 16f, blast, 80f, false, 0, 19.51f);
        blueMk1.sprite = "newex-diamond-shard";

        // Block do Java tạo nên blaw.hjson KHÔNG có `type:`. hjson nạp SAU và ghi đè các giá trị dưới đây
        // (size 2 / reload 20 / health 950 ...), nên đặt cùng giá trị để Java và hjson không mâu thuẫn.
        base = new BlawBlock("blaw");
        base.squareSprite = false;
        base.health = 950;
        base.size = 2;
        base.reload = 20f;
        base.configurable = true;
        base.category = Category.turret;
        base.config(Integer.class, (BlawBuild tile, Integer value) -> {
            if (tile != null) tile.setTier(value);
        });
        base.buildType = () -> new BlawBuild(base);

        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static BasicBulletType mk(float speed, float damage, float width, float height, String hex,
                                       float splashDmg, float splashRad, StatusEffect status, float statusDur,
                                       boolean pierce, int pierceCap, float lifetime) {
        BasicBulletType b = new BasicBulletType();
        Color c = Color.valueOf(hex);
        b.buildingDamageMultiplier = 0.01f;
        b.speed = speed; b.damage = damage; b.width = width; b.height = height; b.lifetime = lifetime;
        b.frontColor = c; b.backColor = Color.white; b.hitColor = c;
        b.hitEffect = hitFx; b.despawnEffect = hitFx;
        if (splashDmg > 0f) { b.splashDamage = splashDmg; b.splashDamageRadius = splashRad; }
        b.status = status; b.statusDuration = statusDur;
        // QUAN TRỌNG: đạn không xuyên (Hỏa) phải để pierceCap mặc định -1. Đặt 0 khiến v157 coi "đã va chạm >= 0 mục tiêu"
        // và huỷ viên đạn ngay khi vừa sinh ra -> chỉ thấy đạn Băng, không thấy đạn Hỏa. JS gốc không hề đặt pierceCap cho đạn Hỏa.
        if (pierce) { b.pierce = true; b.pierceCap = pierceCap; }
        return b;
    }

    private static void setup() {
        shootSoundCannon = Vars.tree.loadSound("cannon-shot-1");
        if (base == null) return;
        base.shootSound = shootSoundCannon;
        base.ammoTypes.put(Items.titanium, blueMk1);   // đặt lại sau hjson (bài 18)
    }

    public static class BlawBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;          // 0 = MK1, 1 = MK2, 2 = MK2B
        boolean barrelSide = false; // chỉ dùng khi DUAL_BARREL = false
        float shotgunTimer = 0f;
        int shotCount = 0;
        float recoilLeft = 0f, recoilRight = 0f;

        public BlawBuild(ItemTurret outer) {
            outer.super();
        }

        int getTier() { return tierState; }

        /** Nâng cấp: đổi cấp và hồi đầy máu theo cấp mới (giống JS). */
        void setTier(int val) {
            tierState = val;
            health = maxHealth();
            shotCount = 0;
        }

        @Override
        public float maxHealth() {
            return tierState == 2 ? HP_MK2B : HP_MK1;
        }

        @Override
        public float range() {
            return tierState == 1 ? RANGE_MK2 : (tierState == 2 ? RANGE_MK2B : RANGE_MK1);
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (tierState == 2 && shotgunTimer > 0f) shotgunTimer -= Time.delta;
            if (recoilLeft > 0f) recoilLeft = Math.max(0f, recoilLeft - Time.delta * 0.1f);
            if (recoilRight > 0f) recoilRight = Math.max(0f, recoilRight - Time.delta * 0.1f);

            if (isShooting() && hasAmmo()) {
                // xung nhịp ngẫu nhiên 0%..200%, MK2 cộng cố định +20%
                float boost = Mathf.random(0f, 2f) + (tierState == 1 ? 0.2f : 0f);
                reloadCounter += Time.delta * boost * efficiency;
            }
        }

        /** Hệ số khuếch đại sát thương theo HP mục tiêu (chưa tính bạo kích). */
        private float hpAmplify(int tier) {
            if (!(target instanceof Healthc)) return 1f;
            float hp = ((Healthc) target).health();
            float amp = 1f;
            if (hp > 100f) amp += Mathf.floor((hp - 100f) / 100f) * (tier == 2 ? 0.05f : (tier == 1 ? 0.02f : 0.01f));
            if (tier == 1 && hp > 1000f) amp += Mathf.floor((hp - 1000f) / 1000f) * 0.01f;
            return amp;
        }

        /** Bắn từ 1 nòng: left = nòng Băng (trái), !left = nòng Hỏa (phải). */
        private void fireBarrel(boolean left, int tier, float amp) {
            float crit = Mathf.chance(0.80) ? 2.20f : 1f;
            float ang = rotation + (left ? 90f : -90f);
            float sx = x + Angles.trnsx(ang, BARREL_OFFSET), sy = y + Angles.trnsy(ang, BARREL_OFFSET);

            if (left) recoilLeft = 1f; else recoilRight = 1f;

            if (tier == 2) {
                BasicBulletType b = left ? blueMk2b : redMk2b;
                float dmg = 550f * amp * crit;
                for (int i = 0; i < SHOTGUN_PELLETS; i++) {
                    Call.createBullet(b, team, sx, sy, rotation + Mathf.range(15f), dmg, Mathf.random(1.5f, 3.5f), 1f);
                }
            } else {
                BasicBulletType b = tier == 1 ? (left ? blueMk2 : redMk2) : (left ? blueMk1 : redMk1);
                // JS truyền b.speed vào velocityScl (bài học 4) -> giữ nguyên tốc độ thực tế
                Call.createBullet(b, team, sx, sy, rotation, b.damage * amp * crit, b.speed, 1f);
            }
        }

        @Override
        public void shoot(BulletType type) {
            int tier = tierState;
            if (tier == 2 && shotgunTimer > 0f) return;

            float amp = hpAmplify(tier);

            if (DUAL_BARREL) {
                fireBarrel(true, tier, amp);
                fireBarrel(false, tier, amp);
            } else {
                barrelSide = !barrelSide;
                fireBarrel(barrelSide, tier, amp);
            }

            if (shootSoundCannon != null) shootSoundCannon.at(x, y, Mathf.random(0.9f, 1.1f));

            if (tier == 2 && ++shotCount >= 2) {
                shotgunTimer = 180f;
                shotCount = 0;
            }
            useAmmo();
        }

        @Override
        public void draw() {
            BlawBlock b = (BlawBlock) block;
            float rot = rotation, ang = rot * Mathf.degRad;
            float cos = Mathf.cos(ang), sin = Mathf.sin(ang);

            if (b.customBase != null && b.customBase.found()) Draw.rect(b.customBase, x, y);
            else Draw.rect(Core.atlas.find("block-" + b.size), x, y);

            float pz = Draw.z();
            Draw.z(Layer.turret);

            float lx = x - cos * recoilLeft * RECOIL_LEN, ly = y - sin * recoilLeft * RECOIL_LEN;
            float rx = x - cos * recoilRight * RECOIL_LEN, ry = y - sin * recoilRight * RECOIL_LEN;

            if (b.leftBarrel != null && b.leftBarrel.found()) Draw.rect(b.leftBarrel, lx, ly, rot - 90f);
            if (b.rightBarrel != null && b.rightBarrel.found()) Draw.rect(b.rightBarrel, rx, ry, rot - 90f);
            if (b.region != null && b.region.found()) Draw.rect(b.region, x, y, rot - 90f);

            // đốm sáng đầu nòng (Băng trái, Hỏa phải)
            float sideAng = (rot + 90f) * Mathf.degRad;
            float sx = Mathf.cos(sideAng) * BARREL_OFFSET, sy = Mathf.sin(sideAng) * BARREL_OFFSET;
            Draw.z(Layer.effect + 1f);
            glow(lx + sx - cos * 3f, ly + sy - sin * 3f, recoilLeft, "0031FFFF");
            glow(rx - sx - cos * 3f, ry - sy - sin * 3f, recoilRight, "FF3B00FF");
            Draw.reset();
            Draw.z(pz);
        }

        private void glow(float gx, float gy, float recoil, String hex) {
            float r = 1.5f * (1f + recoil * 1.5f);
            Draw.color(PerfCore.color(hex), 0.35f);
            Fill.circle(gx, gy, r * 2f);
            Draw.color(Color.white);
            Fill.circle(gx, gy, r);
        }

        @Override
        public Object config() {
            return tierState;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(tierState);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            tierState = read.b();   // KHÔNG gọi setTier(): giữ máu đã lưu
        }

        // ------------------------------------------------------------- giao diện nâng cấp

        private static String col(int have, int need) {
            return have >= need ? "[green]" : "[red]";
        }

        private String requirementText() {
            CoreBuild core = team.core();
            if (core == null) return t("[red]Team Core not found![]", "[red]Không tìm thấy Lõi Đội![]");
            int cu = core.items.get(Items.copper), le = core.items.get(Items.lead), ti = core.items.get(Items.titanium);
            return t("[yellow]CORE VAULT RESOURCE REQUIREMENTS:[]\n", "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n")
                + t("[cyan]MK2 Configuration Path[]\n", "[cyan]Nhánh Cấu Hình MK2[]\n")
                + t(" • Copper: ", " • Đồng: ") + col(cu, REQ_MK2[0]) + cu + "[] / " + REQ_MK2[0] + "\n"
                + t(" • Lead: ", " • Chì: ") + col(le, REQ_MK2[1]) + le + "[] / " + REQ_MK2[1] + "\n"
                + t("[purple]MK2B Variant Path[]\n", "[purple]Nhánh Biến Thể MK2B[]\n")
                + t(" • Copper: ", " • Đồng: ") + col(cu, REQ_MK2B[0]) + cu + "[] / " + REQ_MK2B[0] + "\n"
                + t(" • Lead: ", " • Chì: ") + col(le, REQ_MK2B[1]) + le + "[] / " + REQ_MK2B[1] + "\n"
                + t(" • Titanium: ", " • Titan: ") + col(ti, REQ_MK2B[2]) + ti + "[] / " + REQ_MK2B[2];
        }

        private boolean canPay(CoreBuild core, int[] req) {
            return core != null && core.items.get(Items.copper) >= req[0] && core.items.get(Items.lead) >= req[1]
                && core.items.get(Items.titanium) >= req[2];
        }

        private void pay(CoreBuild core, int[] req) {
            NexNet.take(core, Items.copper, req[0]);
            NexNet.take(core, Items.lead, req[1]);
            if (req[2] > 0) NexNet.take(core, Items.titanium, req[2]);
        }

        private Table branch(String title, String desc, String btnText, int[] req, int target, BaseDialog dialog) {
            Table b = new Table();
            b.background(Styles.black6);
            b.margin(12);
            b.add(title).row();
            b.add(desc).width(340).get().setWrap(true);
            ((arc.scene.ui.Label) b.getCells().peek().get()).setAlignment(Align.left);
            b.row();
            b.button(btnText, () -> {
                CoreBuild core = team.core();
                if (canPay(core, req)) {
                    pay(core, req);
                    NexNet.fx(target == 1 ? Fx.upgradeCore : Fx.bigShockwave, x, y);
                    NexNet.fx(Fx.mineHuge, x, y);
                    NexNet.shake(5f, 5f, x, y);
                    NexNet.configure(this, Integer.valueOf(target));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(target == 1
                        ? t("[red]Not enough resources for MK2 path![]", "[red]Không đủ tài nguyên cho nhánh MK2![]")
                        : t("[red]Not enough resources for MK2B path![]", "[red]Không đủ tài nguyên cho nhánh MK2B![]"));
                }
            }).size(180, 38);
            return b;
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(t("Turret Upgrade Center", "Trung tâm nâng cấp pháo"));
            dialog.cont.add(requirementText()).width(360).get().setWrap(true);
            ((arc.scene.ui.Label) dialog.cont.getCells().peek().get()).setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10).row();

            Table branches = new Table();
            Table b1 = branch("[cyan]===(MK2)===[]",
                t("[white]• Range: [green]663 px[]\n• Fire barrel splash DMG: [green]+50%[]\n• Reload speed: [green]+20%[]\n"
                    + "• Critical: [green]80% Rate | +120% Crit DMG[]\n• [orange]Enhanced Frostfire:[] Ice barrel freezes and pierces 3 targets\n"
                    + "• Fire barrel deals 120 splash DMG\n• Greatly amplifies DMG based on target HP",
                  "[white]• Tầm bắn: [green]663 px[]\n• Sát thương nổ nòng Hỏa: [green]+50%[]\n• Tốc độ nạp đạn: [green]+20%[]\n"
                    + "• Bạo kích: [green]80% Tỉ lệ | +120% ST Bạo kích[]\n• [orange]Cải Tiến Băng Hỏa:[] Nòng Băng đóng băng và xuyên 3 mục tiêu\n"
                    + "• Nòng Hỏa gây nổ lan 120 DMG\n• Tăng mạnh khả năng khuếch đại sát thương theo HP đối thủ"),
                t("[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), REQ_MK2, 1, dialog);

            Table b2 = branch("[purple]===(MK2B)===[]",
                t("[white]• Health: [green]+150%[] (1,425 HP)\n• Range: [orange]309.4 px[]\n• Base Damage: [green]550 DMG[] / bullet\n"
                    + "• Critical: [green]80% Rate | +120% Crit DMG[]\n• [orange]Shotgun Melee:[] Fires a spread burst of 20 ultimate Frostfire bullets\n"
                    + "• Optimized for close-range Boss killing with Burst-Cooldown mechanics",
                  "[white]• Máu cấu trúc: [green]+150%[] (1,425 HP)\n• Tầm bắn: [orange]309.4 px[]\n• Sát thương gốc: [green]550 DMG[] / viên\n"
                    + "• Bạo kích: [green]80% Tỉ lệ | +120% ST Bạo kích[]\n• [orange]Cận Chiến Shotgun:[] Bắn tỏa chùm 20 viên đạn hỗn hợp Băng Hỏa cực đại\n"
                    + "• Tối ưu diệt Boss tầm gần kèm cơ chế Xả-Hồi"),
                t("[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), REQ_MK2B, 2, dialog);

            branches.add(b1).width(340).row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton();
            dialog.show();
        }

        private String statsText(int tier) {
            if (tier == 0) {
                return t("[gold]⚡ BASE SPECS (MK1) ⚡[]\n[lightgray]Structure Health:[] [green]950 HP[]\n"
                    + "[lightgray]Effective Range:[] [orange]442 pixels[]\n[lightgray]Base Damage:[] [white]25 DMG / bullet[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ SPECIAL SKILL MECHANICS:[]\n• Dual Frostfire Barrels: Alternates firing left barrel (Freezing + Pierce 3) and right barrel (40 Splash DMG).\n"
                    + "• Damage Amplification: Adds +1% DMG for every 100 target HP when HP > 100.\n• Random firing speed pulse from 0% to 200%.",
                  "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n[lightgray]Máu cấu trúc:[] [green]950 HP[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]442 pixel[]\n[lightgray]Sát thương gốc:[] [white]25 DMG / viên[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n• Băng Hỏa Song Nòng: Luân phiên bắn nòng trái (Đóng băng + Xuyên 3 mục tiêu) và nòng phải (Nổ lan 40 DMG).\n"
                    + "• Khuếch đại sát thương: Tăng +1% DMG cho mỗi 100 HP của mục tiêu khi HP > 100.\n• Xung nhịp tốc độ bắn ngẫu nhiên từ 0% đến 200%.");
            } else if (tier == 1) {
                return t("[cyan]⚡ UPGRADE SPECS MK2 ⚡[]\n[lightgray]Structure Health:[] [green]950 HP[]\n"
                    + "[lightgray]Effective Range:[] [orange]663 pixels[]\n[lightgray]Base Damage:[] [white]50 DMG / bullet[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ SPECIAL SKILL MECHANICS:[]\n• Enhanced Frostfire: Fire barrel splash DMG increased to 120 DMG / 32px.\n"
                    + "• Boss Executer Damage: Adds +2% DMG per 100 target HP, plus +1% DMG per 1,000 HP when HP > 1,000.\n"
                    + "• Flat +20% reload speed combined with random pulse.",
                  "[cyan]⚡ THÔNG SỐ NÂNG CẤP MK2 ⚡[]\n[lightgray]Máu cấu trúc:[] [green]950 HP[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [orange]663 pixel[]\n[lightgray]Sát thương gốc:[] [white]50 DMG / viên[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[cyan]⚡ CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n• Băng Hỏa Tăng Cường: Nổ lan nòng Hỏa tăng lên 120 DMG / 32px.\n"
                    + "• Sát thương Trảm Tướng: Tăng +2% DMG cho mỗi 100 HP mục tiêu, cộng thêm +1% DMG mỗi 1,000 HP khi HP > 1,000.\n"
                    + "• Tăng cố định +20% tốc độ nạp đạn kết hợp xung nhịp ngẫu nhiên.");
            } else {
                return t("[purple]⚡ UPGRADE SPECS MK2B ⚡[]\n[lightgray]Structure Health:[] [green]1,425 HP (+150%)[]\n"
                    + "[lightgray]Effective Range:[] [red]309.4 pixels[]\n[lightgray]Base Damage:[] [white]550 DMG / bullet[]\n"
                    + "[lightgray]Crit Chance:[] [yellow]80%[]\n[lightgray]Crit Damage:[] [orange]220% (120% bonus)[]\n\n"
                    + "[purple]🔥 SPECIAL SKILL MECHANICS:[]\n• Shotgun Melee: Fires a burst of 20 mixed Frostfire bullets (2 waves x 10 bullets).\n"
                    + "• Boss Killer Damage: Adds +5% DMG for every 100 target HP when HP > 100.\n• Burst-Cooldown: Enters a 3-second cooldown after every 2 burst salvos.",
                  "[purple]⚡ THÔNG SỐ NÂNG CẤP MK2B ⚡[]\n[lightgray]Máu cấu trúc:[] [green]1,425 HP (+150%)[]\n"
                    + "[lightgray]Tầm bắn hiệu dụng:[] [red]309.4 pixel[]\n[lightgray]Sát thương gốc:[] [white]550 DMG / viên[]\n"
                    + "[lightgray]Tỉ lệ bạo kích:[] [yellow]80%[]\n[lightgray]Sát thương bạo kích:[] [orange]220% (120% bonus)[]\n\n"
                    + "[purple]🔥 CƠ CHẾ KỸ NĂNG ĐẶC BIỆT:[]\n• Cận chiến Shotgun: Bắn tỏa chùm 20 viên đạn hỗn hợp Băng Hỏa (2 đợt x 10 viên).\n"
                    + "• Sát thương Diệt Boss: Tăng +5% DMG cho mỗi 100 HP của mục tiêu khi HP > 100.\n"
                    + "• Cơ chế Xả-Hồi: Đi vào thời gian hồi chiêu 3 giây sau mỗi 2 đợt xả chùm đạn.");
            }
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            int tier = getTier();

            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, this::openUpgradeDialog)
                    .size(50, 40).tooltip(t("Upgrade turret", "Nâng cấp tháp pháo lên"));
            } else {
                int ft = tier;
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(ft == 1
                    ? t("[cyan]CURRENTLY ACTIVE IN BLAW MK2 CONFIG![]", "[cyan]ĐANG HOẠT ĐỘNG Ở CẤU HÌNH BLAW MK2![]")
                    : t("[purple]CURRENTLY ACTIVE IN BLAW MK2B CONFIG![]", "[purple]ĐANG HOẠT ĐỘNG Ở CẤU HÌNH BLAW MK2B![]")))
                    .size(50, 40).tooltip(t("Upgrade turret", "Nâng cấp tháp pháo"));
            }

            table.button(Icon.info, Styles.cleari, 40, () -> {
                String title = t(" Turret Specs \"Blaw\": ", " Thông số pháo \"Blaw\": ")
                    + (tier == 0 ? "[yellow](MK1)[]" : tier == 1 ? t("[cyan]UPGRADE SPECS MK2[]", "[cyan]THÔNG SỐ NÂNG CẤP MK2[]")
                        : t("[purple]UPGRADE SPECS MK2B[]", "[purple]THÔNG SỐ NÂNG CẤP MK2B[]"));
                BaseDialog dialog = new BaseDialog(title);
                Table info = new Table();
                info.add(statsText(tier)).width(360).get().setWrap(true);
                ((arc.scene.ui.Label) info.getCells().peek().get()).setAlignment(Align.left);
                ScrollPane scroll = new ScrollPane(info);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400);
                dialog.addCloseButton();
                dialog.show();
            }).size(50, 40).tooltip(t("Turret Upgrade Center", "Trung tâm nâng cấp pháo"));
        }
    }
}
