package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;

/** Chuyển từ tankani2k.js + tankani500.js (2 bản chỉ khác số liệu nên dùng chung 1 lớp). */
public final class Tankani {
    private static final int HITS_PER_STACK = 10;
    private static final int MAX_STACK = 111;
    private static final int[] TIER_HP = {1413, 1838, 2375};

    private static final int REQ_CU = 4000, REQ_PB = 4000, REQ_TI_B = 2000;

    private static Effect crumbleFx, trailFx;

    private Tankani() {}

    /** Số liệu từng biến thể. */
    private static final class V {
        final String block, label;
        final int dmg, mk2Splash, mk2bSplash;
        final boolean small; // tankani500: pierce + chỉnh chỉ số
        BulletType normal, mk2, mk2b;

        V(String block, String label, int dmg, int mk2Splash, int mk2bSplash, boolean small) {
            this.block = block; this.label = label; this.dmg = dmg;
            this.mk2Splash = mk2Splash; this.mk2bSplash = mk2bSplash; this.small = small;
        }
    }

    private static final V V2K = new V("newex-tankani2k", "Tankani-2k", 1115, 558, 892, false);
    private static final V V500 = new V("newex-tankani500", "Tankani-500", 558, 279, 446, true);

    private static void buildFx() {
        crumbleFx = new Effect(40, e -> {
            float pz = Draw.z();
            Draw.z(Layer.effect);
            Draw.color(Pal.redDust, Pal.redderDust, e.fin());
            Lines.stroke(e.fout() * 6f);
            Lines.circle(e.x, e.y, Interp.pow3Out.apply(e.fin()) * 45f);
            Color flash = Color.white.cpy().lerp(Pal.redDust, e.fin());
            flash.a = e.fout();
            Draw.color(flash);
            Lines.circle(e.x, e.y, Interp.pow2Out.apply(e.fin()) * 35f);
            Draw.z(pz);
            Draw.reset();
        });
        trailFx = new Effect(18, e -> {
            Draw.z(Layer.bullet - 0.1f);
            Draw.color(e.color); Draw.alpha(0.6f * e.fout()); Lines.stroke(6f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, 24f);
            Draw.color(Color.white); Draw.alpha(0.9f * e.fout()); Lines.stroke(2.5f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, 24f);
            Draw.reset();
        });
    }

    private static void applyMaxHpDamage(Bullet b, Hitboxc entity) {
        if (b.owner instanceof TankaniBuild) {
            TankaniBuild o = (TankaniBuild) b.owner;
            o.addHitPoint();
            int pairs = o.damageStack / 2;
            if (pairs > 0 && entity instanceof Healthc) {
                Healthc h = (Healthc) entity;
                h.damage(h.maxHealth() * (pairs * 0.002f));
            }
        }
    }

    private static BulletType makeBullet(V v, int kind) {
        // kind 0 = thường, 1 = MK2, 2 = MK2B
        BasicBulletType t = new BasicBulletType(kind == 2 ? 20f : 15f, kind == 2 ? 1f : v.dmg) {
            @Override
            public void update(Bullet b) {
                super.update(b);
                trailFx.at(b.x, b.y, b.rotation(), trailColor);
            }

            @Override
            public void hitEntity(Bullet b, Hitboxc entity, float health) {
                applyMaxHpDamage(b, entity);
                super.hitEntity(b, entity, health);
            }
        };
        t.width = 10f; t.height = 33f; t.lifetime = 60f;
        if (kind == 0) {
            t.frontColor = Color.valueOf("#e0ea87"); t.backColor = Color.valueOf("#e5ff00");
            t.trailColor = Color.valueOf("#daea80");
        } else if (kind == 1) {
            t.frontColor = Color.valueOf("#e0ea87"); t.backColor = Color.valueOf("#00ff4c");
            t.trailColor = Color.valueOf("#daea80");
            t.splashDamageRadius = 50f; t.splashDamage = v.mk2Splash;
            t.hitEffect = crumbleFx; t.despawnEffect = crumbleFx;
        } else {
            t.frontColor = Color.valueOf("#ff8a80"); t.backColor = Color.valueOf("#ff1744");
            t.trailColor = Color.valueOf("#ff5252");
            t.splashDamageRadius = 150f; t.splashDamage = v.mk2bSplash;
            t.collidesGround = false; t.collidesAir = true;
            t.hitEffect = crumbleFx; t.despawnEffect = crumbleFx;
        }
        if (v.small) { t.pierce = true; t.pierceCap = 99; t.pierceBuilding = true; }
        return t;
    }

    public static void init() {
        buildFx();
        for (V v : new V[]{V2K, V500}) {
            v.normal = makeBullet(v, 0);
            v.mk2 = makeBullet(v, 1);
            v.mk2b = makeBullet(v, 2);
        }
        Events.on(ContentInitEvent.class, e -> {
            setup(V2K);
            setup(V500);
        });
    }

    private static void setup(V v) {
        Block blk = Vars.content.getByName(ContentType.block, v.block);
        if (!(blk instanceof ItemTurret)) return;
        ItemTurret turret = (ItemTurret) blk;

        turret.configurable = true;
        turret.inaccuracy = 0f;
        if (v.small) {
            turret.itemCapacity = 1;
            turret.reload = turret.reload * 0.8f;
            turret.range = turret.range * 0.8f;
        }

        turret.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof TankaniBuild) ((TankaniBuild) tile).setTier(value);
        });

        turret.addBar("damageStack", (Building e) -> new Bar(
            () -> "DMG: +" + (int) Math.floor(((TankaniBuild) e).getStackRatio() * (MAX_STACK * 90)) + "%",
            () -> Color.orange,
            () -> ((TankaniBuild) e).getStackRatio()));

        turret.buildType = () -> new TankaniBuild(turret, v);
    }

    private static boolean en() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    private static String col(int have, int need) {
        return have >= need ? "[green]" : "[red]";
    }

    public static class TankaniBuild extends ItemTurret.ItemTurretBuild {
        final V v;
        public int tierState = 0, hitPoints = 0, damageStack = 0;
        float maxHp = 0f;

        public TankaniBuild(ItemTurret outer, V v) {
            outer.super();
            this.v = v;
        }

        public float getStackRatio() { return damageStack / (float) MAX_STACK; }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            if (val >= 0 && val < TIER_HP.length) health = TIER_HP[val];
            maxHp = health;
        }

        @Override
        public float maxHealth() { return maxHp > 0f ? maxHp : super.maxHealth(); }

        public void addHitPoint() {
            if (damageStack >= MAX_STACK) return;
            hitPoints++;
            int ns = hitPoints / HITS_PER_STACK;
            if (ns > damageStack) {
                damageStack = Math.min(ns, MAX_STACK);
                Fx.upgradeCore.at(x, y);
            }
        }

        public int getModifiedDamage(float base) {
            return Math.round(base * (1f + damageStack * 0.9f));
        }

        @Override
        public float range() {
            return getTier() == 2 ? super.range() * 2f : super.range();
        }

        @Override
        protected void findTarget() {
            super.findTarget();
            if (getTier() == 2 && target != null) {
                if (!(target instanceof Unit) || !((Unit) target).isFlying()) target = null;
            }
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            if (tier == 1) {
                v.mk2.damage = getModifiedDamage(v.dmg);
                v.mk2.splashDamage = getModifiedDamage(v.mk2Splash);
                super.shoot(v.mk2);
            } else if (tier == 2) {
                v.mk2b.damage = getModifiedDamage(1);
                v.mk2b.splashDamage = getModifiedDamage(v.mk2bSplash);
                super.shoot(v.mk2b);
            } else {
                v.normal.damage = getModifiedDamage(v.dmg);
                super.shoot(v.normal);
            }
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.i(hitPoints);
            write.b(damageStack);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            hitPoints = read.i();
            damageStack = read.b();
        }

        // ---------------- UI ----------------

        private static String t(boolean isEn, String e, String vi) { return isEn ? e : vi; }


        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();

            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade turret firepower structure", "Nâng cấp cấu trúc hỏa lực tháp pháo"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]TANKANI SYSTEM HAS REACHED EVOLUTION LIMIT![]",
                    "[scarlet]HỆ THỐNG TANKANI ĐÃ ĐẠT GIỚI HẠN TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Max level reached", "Đã đạt cấp tối đa"));
            }

            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(t(isEn, "View detailed system stats", "Xem chi tiết thông số hệ thống"));
        }

        private String requirementText(boolean isEn) {
            Building core = team.core();
            if (core == null) return t(isEn, "[red]Team Core Not Found![]", "[red]Không tìm thấy Lõi Đội![]");
            int cu = core.items.get(Items.copper), pb = core.items.get(Items.lead), ti = core.items.get(Items.titanium);
            String c1 = col(cu, REQ_CU), l1 = col(pb, REQ_PB), c2 = col(cu, REQ_CU), l2 = col(pb, REQ_PB), t2 = col(ti, REQ_TI_B);
            return isEn
                ? "[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Configuration Branch:[]\n"
                    + " • Copper: " + c1 + cu + "[] / " + REQ_CU + "\n • Lead: " + l1 + pb + "[] / " + REQ_PB + "\n"
                    + "[purple]MK2B Anti-Air Variant Branch:[]\n"
                    + " • Copper: " + c2 + cu + "[] / " + REQ_CU + "\n • Lead: " + l2 + pb + "[] / " + REQ_PB + "\n"
                    + " • Titanium: " + t2 + ti + "[] / " + REQ_TI_B
                : "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh Cấu Hình MK2:[]\n"
                    + " • Đồng: " + c1 + cu + "[] / " + REQ_CU + "\n • Chì: " + l1 + pb + "[] / " + REQ_PB + "\n"
                    + "[purple]Nhánh Biến Thể Chuyên Không MK2B:[]\n"
                    + " • Đồng: " + c2 + cu + "[] / " + REQ_CU + "\n • Chì: " + l2 + pb + "[] / " + REQ_PB + "\n"
                    + " • Titan: " + t2 + ti + "[] / " + REQ_TI_B;
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, v.label + " Upgrade Center", "Trung tâm nâng cấp " + v.label));
            Cell0.wrap(dialog.cont.add(requirementText(isEn)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add("[cyan]===(MK2)===[]").row();
            String b1Text = isEn
                ? "Integrated area explosive technology:\n [white]• Increases HP to [green]1838 HP[].[]\n"
                    + " [white]• Retains multi-target (Ground & Air) and base range.[]\n"
                    + " [white]• Bullets explode in a [orange]50 pixel[] radius on hit.[]\n"
                    + " [white]• Primary target takes [red]" + v.dmg + " base damage[] directly.[]\n"
                    + " [white]• Nearby enemies take [yellow]" + v.mk2Splash + " splash damage[] (50%).[]"
                : "Tích hợp công nghệ nổ mảnh diện rộng:\n [white]• Tăng lượng máu chống chịu lên [green]1838 HP[].[]\n"
                    + " [white]• Giữ nguyên cơ chế bắn đa mục tiêu (Đất & Không) và tầm bắn gốc.[]\n"
                    + " [white]• Đạn chạm mục tiêu kích nổ lan phạm vi [orange]50 pixel[].[]\n"
                    + " [white]• Mục tiêu chính nhận [red]" + v.dmg + " sát thương gốc[] trực diện.[]\n"
                    + " [white]• Kẻ địch lân cận chịu [yellow]" + v.mk2Splash + " sát thương nổ lan[] (50%).[]";
            Cell0.wrap(b1.add(b1Text), 340); b1.row();
            b1.button(t(isEn, "[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_CU && core.items.get(Items.lead) >= REQ_PB) {
                    NexNet.take(core, Items.copper, REQ_CU); NexNet.take(core, Items.lead, REQ_PB);
                    NexNet.fx(Fx.upgradeCore, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    setTier(1);
                    NexNet.configure(this, Integer.valueOf(1));
                    dialog.hide(); deselect();
                } else Vars.ui.showInfo(t(isEn, "[red]Not enough resources for MK2![]", "[red]Không đủ tài nguyên cho nhánh MK2![]"));
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add("[purple]===(MK2B)===[]").row();
            String b2Text = isEn
                ? "Converts to heavy anti-air artillery complex:\n [white]• Increases max HP to [green]2375 HP[].[]\n"
                    + " [white]• Extends effective range by [ultra-light]2x [green](x2 Range)[][].[]\n"
                    + " [white]• [red]Completely removes ground targeting capability[].[]\n"
                    + " [white]• Increases bullet speed to [sky]20.0[] dedicated anti-air.[]\n"
                    + " [white]• Pressure wave blast area up to [pink]150 pixels[].[]\n"
                    + " [white]• Deals [orange]" + v.mk2bSplash + " AOE damage[] (80% of base damage).[]"
                : "Chuyển đổi sang tổ hợp pháo phòng không hạng nặng:\n [white]• Gia tăng lượng máu tối đa lên cực đại [green]2375 HP[].[]\n"
                    + " [white]• Mở rộng [ultra-light]gấp đôi tầm bắn hiệu dụng [green](x2 Range)[][].[]\n"
                    + " [white]• [red]Loại bỏ hoàn toàn khả năng bắn mục tiêu mặt đất[].[]\n"
                    + " [white]• Tăng tốc độ đạn bay lên [sky]20.0[] chuyên dụng diệt Không Quân.[]\n"
                    + " [white]• Đạn nổ áp suất tạo vùng sát thương lan rộng tới [pink]150 pixel[].[]\n"
                    + " [white]• Gây [orange]" + v.mk2bSplash + " sát thương diện rộng[] (80% lực bắn gốc).[]";
            Cell0.wrap(b2.add(b2Text), 340); b2.row();
            b2.button(t(isEn, "[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_CU && core.items.get(Items.lead) >= REQ_PB
                    && core.items.get(Items.titanium) >= REQ_TI_B) {
                    NexNet.take(core, Items.copper, REQ_CU); NexNet.take(core, Items.lead, REQ_PB);
                    NexNet.take(core, Items.titanium, REQ_TI_B);
                    NexNet.fx(Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    setTier(2);
                    NexNet.configure(this, Integer.valueOf(2));
                    dialog.hide(); deselect();
                } else Vars.ui.showInfo(t(isEn, "[red]Not enough resources for MK2B![]", "[red]Không đủ tài nguyên cho nhánh MK2B![]"));
            }).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = t(isEn, " " + v.label + " Stats: ", " Thông số " + v.label + ": ");
            int tier = getTier();
            String stack = t(isEn, "\n[scarlet]⚡ EVOLUTION MECHANIC (HITS) ⚡[]\n", "\n[scarlet]⚡ CƠ CHẾ TIẾN HÓA (HITS) ⚡[]\n")
                + t(isEn, "[lightgray]Hits landed:[] [yellow]", "[lightgray]Số phát bắn trúng:[] [yellow]") + hitPoints + " Hits[]\n"
                + t(isEn, "[lightgray]Power level:[] [orange]Tier ", "[lightgray]Cấp độ tầng lực:[] [orange]Tầng ") + damageStack + " / " + MAX_STACK + "[]\n"
                + t(isEn, "[lightgray]Bonus damage:[] [green]+", "[lightgray]Sát thương cộng thêm:[] [green]+") + (damageStack * 90) + "%[] ("
                + t(isEn, "Max +", "Tối đa +") + (MAX_STACK * 90) + "%)\n"
                + t(isEn, "[lightgray]Max HP Bonus DMG:[] [green]+", "[lightgray]ST thêm theo Max HP:[] [green]+")
                + String.format("%.1f", (damageStack / 2) * 0.2f) + "% Max HP[]\n";

            String desc;
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = t(isEn, "[gold]⚡ UNUPGRADED BASE STATS (MK1) ⚡[]\n[lightgray]Turret Health:[] [green]1413 HP[]\n[lightgray]Target Type:[] Ground & Air\n[lightgray]Single Target Damage:[] [red]",
                    "[gold]⚡ THÔNG SỐ GỐC CHƯA NÂNG CẤP (MK1) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]1413 HP[]\n[lightgray]Trạng thái mục tiêu:[] Đất & Không\n[lightgray]Sát thương mục tiêu đơn:[] [red]")
                    + getModifiedDamage(v.dmg) + t(isEn, " Damage[] (Base: ", " Sát thương[] (Gốc: ") + v.dmg + ")\n" + stack;
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = t(isEn, "[cyan]⚡ AREA DAMAGE CONFIG (MK2) ⚡[]\n[lightgray]Turret Health:[] [green]1838 HP[]\n[lightgray]Splash Area:[] [orange]50 Pixels[]\n[lightgray]Direct Damage:[] [red]",
                    "[cyan]⚡ CẤU HÌNH ĐẠN DIỆN RỘNG (MK2) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]1838 HP[]\n[lightgray]Vùng nổ lan (Splash):[] [orange]50 Pixel[]\n[lightgray]Sát thương trực diện:[] [red]")
                    + getModifiedDamage(v.dmg) + t(isEn, " Damage[] (Base: ", " Sát thương[] (Gốc: ") + v.dmg + ")\n"
                    + t(isEn, "[lightgray]Splash Damage:[] [yellow]", "[lightgray]Sát thương nổ lan:[] [yellow]")
                    + getModifiedDamage(v.mk2Splash) + t(isEn, " Damage[] (Base: ", " Sát thương[] (Gốc: ") + v.mk2Splash + ")\n" + stack;
            } else {
                title += "[purple](MK2B)[]";
                desc = t(isEn, "[purple]⚡ HEAVY ANTI-AIR CONFIG (MK2B) ⚡[]\n[lightgray]Turret Health:[] [green]2375 HP[]\n[lightgray]Range:[] [green]Double Base Range (x2 Range)[]\n[lightgray]Target Type:[] [red]AIR ONLY[]\n[lightgray]Pressure Splash Area:[] [pink]150 Pixels[]\n[lightgray]Anti-Air Splash Damage:[] [orange]",
                    "[purple]⚡ CẤU HÌNH PHÒNG KHÔNG HẠNG NẶNG (MK2B) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]2375 HP[]\n[lightgray]Tầm bắn:[] [green]Gấp đôi tầm bắn gốc (x2 Range)[]\n[lightgray]Trạng thái mục tiêu:[] [red]Chỉ bắn KHÔNG QUÂN[]\n[lightgray]Vùng nổ lan áp suất:[] [pink]150 Pixel[]\n[lightgray]Sát thương nổ phòng không:[] [orange]")
                    + getModifiedDamage(v.mk2bSplash) + t(isEn, " Damage[] (Base: ", " Sát thương[] (Gốc: ") + v.mk2bSplash + ")\n" + stack;
            }

            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            Cell0.wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }

    /** Tiện ích: ô Label tự xuống dòng, canh trái. */
    private static final class Cell0 {
        static void wrap(arc.scene.ui.layout.Cell<arc.scene.ui.Label> c, float w) {
            c.width(w).get().setWrap(true);
            c.get().setAlignment(Align.left);
        }
    }
}
