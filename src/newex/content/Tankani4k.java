package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.effect.MultiEffect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;

/** Chuyển từ tankani4k.js. */
public final class Tankani4k {
    private static final int HITS_PER_STACK = 10, MAX_STACK = 111;
    private static final int REQ_CU = 6000, REQ_PB = 6000, REQ_TI = 3000;
    private static final int[] TIER_HP = {3913, 5089, 6575};
    private static final int DMG = 1673, SPLASH_MK2 = 837, SPLASH_MK2B = 1070;

    private static Effect trailFx, despawnFx;
    private static BulletType normal, mk2, mk2b;

    private Tankani4k() {}

    private static void buildFx() {
        Effect crumble = new Effect(40, e -> {
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
        Effect circle = new Effect(30, e -> {
            Draw.color(Pal.accent, Color.white, e.fin());
            Lines.stroke(e.fout() * 3f);
            Lines.circle(e.x, e.y, Interp.circleOut.apply(e.fin()) * 40f);
        });
        Effect spark = new Effect(40, e -> {
            Draw.color(e.color, Color.white, e.fout() * 0.3f);
            Lines.stroke(e.fout() * 1.6f);
            Mathf.rand.setSeed(e.id);
            for (int i = 0; i < 18; i++) {
                float len = e.finpow() * 27f * Mathf.rand.random(0.2f, 1f);
                float ang = Mathf.rand.random(360f);
                Lines.lineAngle(e.x + Angles.trnsx(ang, len), e.y + Angles.trnsy(ang, len), ang,
                    e.fout() * Mathf.rand.random(4f, 8f) + 2f);
            }
        });
        despawnFx = new MultiEffect(crumble, circle, spark);
        trailFx = new Effect(18, e -> {
            Draw.z(Layer.bullet - 0.1f);
            Draw.color(e.color); Draw.alpha(0.6f * e.fout()); Lines.stroke(6f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, 24f);
            Draw.color(Color.white); Draw.alpha(0.9f * e.fout()); Lines.stroke(2.5f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, 24f);
            Draw.reset();
        });
    }

    private static BulletType makeBullet(int kind) {
        BasicBulletType t = new BasicBulletType(kind == 2 ? 20f : 15f, kind == 2 ? 0.8f : DMG) {
            @Override
            public void update(Bullet b) {
                super.update(b);
                trailFx.at(b.x, b.y, b.rotation(), trailColor);
            }

            @Override
            public void hitEntity(Bullet b, Hitboxc entity, float health) {
                if (b.owner instanceof Tankani4kBuild) {
                    Tankani4kBuild o = (Tankani4kBuild) b.owner;
                    o.addHitPoint();
                    int pairs = o.damageStack / 2;
                    if (pairs > 0 && entity instanceof Healthc) {
                        Healthc h = (Healthc) entity;
                        h.damage(h.maxHealth() * (pairs * 0.002f));
                    }
                }
                super.hitEntity(b, entity, health);
            }
        };
        t.width = 10f; t.height = 33f; t.lifetime = 60f;
        t.hitEffect = despawnFx; t.despawnEffect = despawnFx;
        if (kind == 0) {
            t.frontColor = Color.valueOf("#e0ea87"); t.backColor = Color.valueOf("#e5ff00");
            t.trailColor = Color.valueOf("#daea80");
        } else if (kind == 1) {
            t.frontColor = Color.valueOf("#e0ea87"); t.backColor = Color.valueOf("#00ff4c");
            t.trailColor = Color.valueOf("#daea80");
            t.splashDamageRadius = 50f; t.splashDamage = SPLASH_MK2;
        } else {
            t.frontColor = Color.valueOf("#ff8a80"); t.backColor = Color.valueOf("#ff1744");
            t.trailColor = Color.valueOf("#ff5252");
            t.splashDamageRadius = 150f; t.splashDamage = SPLASH_MK2B;
            t.collidesGround = true; t.collidesAir = true;
        }
        return t;
    }

    public static void init() {
        buildFx();
        normal = makeBullet(0);
        mk2 = makeBullet(1);
        mk2b = makeBullet(2);

        Events.on(ContentInitEvent.class, e -> {
            Block blk = Vars.content.getByName(ContentType.block, "newex-tankani4k");
            if (!(blk instanceof ItemTurret)) return;
            ItemTurret turret = (ItemTurret) blk;
            turret.configurable = true;
            turret.inaccuracy = 0f;

            turret.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof Tankani4kBuild) ((Tankani4kBuild) tile).setTier(value);
            });

            turret.addBar("damageStack", (Building b) -> {
                Tankani4kBuild t = (Tankani4kBuild) b;
                return new Bar(
                    () -> {
                        String s = "DMG: +" + (int) Math.floor(t.getStackRatio() * (MAX_STACK * 90)) + "%";
                        if (t.damageStack >= MAX_STACK) s += en() ? " [cyan](ARMOR PIERCE & SHRED)[]" : " [cyan](XUYÊN GIÁP & PHÁ GIÁP)[]";
                        return s;
                    },
                    () -> t.damageStack >= MAX_STACK ? Color.cyan : Color.orange,
                    t::getStackRatio);
            });

            turret.buildType = () -> new Tankani4kBuild(turret);
        });
    }

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }

    private static String t(boolean isEn, String e, String vi) { return isEn ? e : vi; }

    private static String col(int have, int need) { return have >= need ? "[green]" : "[red]"; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    public static class Tankani4kBuild extends ItemTurret.ItemTurretBuild {
        public int tierState = 0, hitPoints = 0, damageStack = 0;
        float maxHp = 0f;
        final ItemTurret blockRef;

        public Tankani4kBuild(ItemTurret outer) {
            outer.super();
            blockRef = outer;
        }

        public int getTier() { return tierState; }

        public float getStackRatio() { return damageStack / (float) MAX_STACK; }

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

        public int getModifiedDamage(float base) { return Math.round(base * (1f + damageStack * 0.9f)); }

        private BulletType current() {
            int tier = getTier();
            return tier == 1 ? mk2 : tier == 2 ? mk2b : normal;
        }

        /** JS gốc: luôn trả đạn theo tier, không tiêu hao ammo. */
        @Override
        public BulletType peekAmmo() { return current(); }

        @Override
        public BulletType useAmmo() { return current(); }

        @Override
        public float range() { return getTier() == 2 ? super.range() * 2f : super.range(); }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            boolean special = damageStack >= MAX_STACK;
            BulletType b = current();
            if (tier == 1) {
                b.damage = getModifiedDamage(DMG);
                b.splashDamage = getModifiedDamage(SPLASH_MK2);
            } else if (tier == 2) {
                b.damage = getModifiedDamage(0.8f);
                b.splashDamage = getModifiedDamage(SPLASH_MK2B);
            } else {
                b.damage = getModifiedDamage(DMG);
            }
            b.pierceArmor = special;
            b.armorMultiplier = special ? 1.5f : 1f;
            b.status = StatusEffects.none;
            super.shoot(b);
            if (tier == 2) reloadCounter = -blockRef.reload * 0.667f;
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
            String c = col(cu, REQ_CU), l = col(pb, REQ_PB), tt = col(ti, REQ_TI);
            return isEn
                ? "[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Configuration Branch:[]\n"
                    + " • Copper: " + c + cu + "[] / " + REQ_CU + "\n • Lead: " + l + pb + "[] / " + REQ_PB + "\n"
                    + "[purple]MK2B Enhanced Variant Branch:[]\n"
                    + " • Copper: " + c + cu + "[] / " + REQ_CU + "\n • Lead: " + l + pb + "[] / " + REQ_PB + "\n"
                    + " • Titanium: " + tt + ti + "[] / " + REQ_TI
                : "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh Cấu Hình MK2:[]\n"
                    + " • Đồng: " + c + cu + "[] / " + REQ_CU + "\n • Chì: " + l + pb + "[] / " + REQ_PB + "\n"
                    + "[purple]Nhánh Biến Thể Cường Hóa MK2B:[]\n"
                    + " • Đồng: " + c + cu + "[] / " + REQ_CU + "\n • Chì: " + l + pb + "[] / " + REQ_PB + "\n"
                    + " • Titan: " + tt + ti + "[] / " + REQ_TI;
        }

        private Table branch(BaseDialog dialog, boolean isEn, int tier, String head, String text, String btn) {
            Table tb = new Table(); tb.background(Styles.black6); tb.margin(12);
            tb.add(head).row();
            wrap(tb.add(text), 340); tb.row();
            tb.button(btn, () -> {
                Building core = team.core();
                boolean ok = core != null && core.items.get(Items.copper) >= REQ_CU && core.items.get(Items.lead) >= REQ_PB
                    && (tier == 1 || core.items.get(Items.titanium) >= REQ_TI);
                if (!ok) {
                    Vars.ui.showInfo(t(isEn, "[red]Not enough resources for " + (tier == 1 ? "MK2" : "MK2B") + "![]",
                        "[red]Không đủ tài nguyên cho nhánh " + (tier == 1 ? "MK2" : "MK2B") + "![]"));
                    return;
                }
                NexNet.take(core, Items.copper, REQ_CU); NexNet.take(core, Items.lead, REQ_PB);
                if (tier == 2) NexNet.take(core, Items.titanium, REQ_TI);
                NexNet.fx(tier == 1 ? Fx.upgradeCore : Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                setTier(tier);
                NexNet.configure(this, Integer.valueOf(tier));
                dialog.hide(); deselect();
            }).size(180, 38);
            return tb;
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, "Tankani-4k Upgrade Center", "Trung tâm nâng cấp Tankani-4k"));
            wrap(dialog.cont.add(requirementText(isEn)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            String t1 = isEn
                ? "Integrated area explosive technology:\n [white]• Increases HP to [green]5089 HP[].[]\n"
                    + " [white]• Retains multi-target (Ground & Air) and base range.[]\n"
                    + " [white]• Bullets explode in a [orange]50 pixel[] radius on hit.[]\n"
                    + " [white]• Max damage stack: [yellow]" + MAX_STACK + " stacks (+9990% DMG)[].[]"
                : "Tích hợp công nghệ nổ mảnh diện rộng:\n [white]• Tăng lượng máu chống chịu lên [green]5089 HP[].[]\n"
                    + " [white]• Giữ nguyên cơ chế bắn đa mục tiêu (Đất & Không) và tầm bắn gốc.[]\n"
                    + " [white]• Đạn chạm mục tiêu kích nổ lan phạm vi [orange]50 pixel[].[]\n"
                    + " [white]• Tích tầng sát thương tối đa: [yellow]" + MAX_STACK + " tầng (+9990% DMG)[].[]";
            String t2 = isEn
                ? "Converts to long-range super-growth mortar:\n [white]• Increases max HP to [green]6575 HP[].[]\n"
                    + " [white]• Extends effective range by [ultra-light]2x [green](x2 Range)[][].[]\n"
                    + " [white]• Fire rate [red]reduced by 40%[][white], attacks [orange]Ground & Air[].[]\n"
                    + " [white]• Max stack breakthrough up to [gold]" + MAX_STACK + " stacks (Max +9990% DMG)[].[]\n"
                    + " [white]• Pressure wave blast area up to [pink]150 pixels[].[]"
                : "Chuyển đổi sang pháo cối tầm xa siêu tăng trưởng:\n [white]• Gia tăng lượng máu tối đa lên cực đại [green]6575 HP[].[]\n"
                    + " [white]• Mở rộng [ultra-light]gấp đôi tầm bắn hiệu dụng [green](x2 Range)[][].[]\n"
                    + " [white]• Tốc độ bắn [red]giảm 40%[][white], tấn công cả [orange]Đất & Không[].[]\n"
                    + " [white]• Giới hạn tích tầng đột phá lên tới [gold]" + MAX_STACK + " tầng (Tối đa +9990% DMG)[].[]\n"
                    + " [white]• Đạn nổ áp suất tạo vùng sát thương lan rộng tới [pink]150 pixel[].[]";

            Table all = new Table();
            all.add(branch(dialog, isEn, 1, "[cyan]===(MK2)===[]", t1, t(isEn, "[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"))).width(340);
            all.row(); all.add().height(12).row();
            all.add(branch(dialog, isEn, 2, "[purple]===(MK2B)===[]", t2, t(isEn, "[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"))).width(340);

            ScrollPane scroll = new ScrollPane(all);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = t(isEn, " Tankani-4k Stats: ", " Thông số Tankani-4k: ");
            int tier = getTier();
            String stack = t(isEn, "\n[scarlet]⚡ EVOLUTION MECHANIC (HITS) ⚡[]\n", "\n[scarlet]⚡ CƠ CHẾ TIẾN HÓA (HITS) ⚡[]\n")
                + t(isEn, "[lightgray]Hits landed:[] [yellow]", "[lightgray]Số phát bắn trúng:[] [yellow]") + hitPoints + " Hits[]\n"
                + t(isEn, "[lightgray]Power level:[] [orange]Tier ", "[lightgray]Cấp độ tầng lực:[] [orange]Tầng ") + damageStack + " / " + MAX_STACK + "[]\n"
                + t(isEn, "[lightgray]Bonus damage:[] [green]+", "[lightgray]Sát thương cộng thêm:[] [green]+") + (damageStack * 90) + "%[] ("
                + t(isEn, "Max +", "Tối đa +") + (MAX_STACK * 90) + "%)\n"
                + t(isEn, "[lightgray]Max HP Bonus DMG:[] [green]+", "[lightgray]ST thêm theo Max HP:[] [green]+")
                + String.format("%.1f", (damageStack / 2) * 0.2f) + "% Max HP[]\n"
                + (damageStack >= MAX_STACK ? t(isEn,
                    "[cyan]⚡ Max damage reach: Triggers 40% armor pierce & Armor reduction (armorMultiplier = 1.5)![]\n",
                    "[cyan]⚡ Đạt mức tối đa Sát thương: Kích hoạt đạn xuyên giáp 40% & Giảm hiệu quả giáp mục tiêu (armorMultiplier = 1.5)![]\n") : "");

            String bd = t(isEn, " Damage[] (Base: ", " Sát thương[] (Gốc: ");
            String desc;
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = t(isEn, "[gold]⚡ UNUPGRADED BASE STATS (MK1) ⚡[]\n[lightgray]Turret Health:[] [green]3913 HP[]\n[lightgray]Target Type:[] Ground & Air\n[lightgray]Single Target Damage:[] [red]",
                    "[gold]⚡ THÔNG SỐ GỐC CHƯA NÂNG CẤP (MK1) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]3913 HP[]\n[lightgray]Trạng thái mục tiêu:[] Đất & Không\n[lightgray]Sát thương mục tiêu đơn:[] [red]")
                    + getModifiedDamage(DMG) + bd + DMG + ")\n" + stack;
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = t(isEn, "[cyan]⚡ AREA DAMAGE CONFIG (MK2) ⚡[]\n[lightgray]Turret Health:[] [green]5089 HP[]\n[lightgray]Splash Area:[] [orange]50 Pixels[]\n[lightgray]Direct Damage:[] [red]",
                    "[cyan]⚡ CẤU HÌNH ĐẠN DIỆN RỘNG (MK2) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]5089 HP[]\n[lightgray]Vùng nổ lan (Splash):[] [orange]50 Pixel[]\n[lightgray]Sát thương trực diện:[] [red]")
                    + getModifiedDamage(DMG) + bd + DMG + ")\n"
                    + t(isEn, "[lightgray]Splash Damage:[] [yellow]", "[lightgray]Sát thương nổ lan:[] [yellow]")
                    + getModifiedDamage(SPLASH_MK2) + bd + SPLASH_MK2 + ")\n" + stack;
            } else {
                title += "[purple](MK2B)[]";
                desc = t(isEn, "[purple]⚡ ENHANCED LONG RANGE ARTILLERY CONFIG (MK2B) ⚡[]\n[lightgray]Turret Health:[] [green]6575 HP[]\n[lightgray]Range:[] [green]Double Base Range (x2 Range)[]\n[lightgray]Attack Speed:[] [red]40% Slower[]\n[lightgray]Target Type:[] Ground & Air\n[lightgray]Pressure Splash Area:[] [pink]150 Pixels[]\n[lightgray]Splash Damage (-20% Base):[] [orange]",
                    "[purple]⚡ CẤU HÌNH PHÁO TẦM XA CƯỜNG HÓA (MK2B) ⚡[]\n[lightgray]Máu tháp pháo:[] [green]6575 HP[]\n[lightgray]Tầm bắn:[] [green]Gấp đôi tầm bắn gốc (x2 Range)[]\n[lightgray]Tốc độ bắn:[] [red]Chậm hơn 40%[]\n[lightgray]Trạng thái mục tiêu:[] Đất & Không\n[lightgray]Vùng nổ lan áp suất:[] [pink]150 Pixel[]\n[lightgray]Sát thương nổ lan (Đã giảm 20% gốc):[] [orange]")
                    + getModifiedDamage(SPLASH_MK2B) + bd + SPLASH_MK2B + ")\n" + stack;
            }

            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }
}
