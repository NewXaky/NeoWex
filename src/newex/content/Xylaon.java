package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
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
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ xylaon.js. Pháo nhiệt: bắn càng lâu càng nhanh, quá nhiệt thì bị khóa để làm nguội; nâng cấp MK2 / MK2B trả dần tài nguyên. */
public final class Xylaon {
    private Xylaon() {}

    private static final int[] REQ_MK2 = {400, 400, 0};      // titan, silicon, plastanium
    private static final int[] REQ_MK2B = {500, 400, 200};

    private static final float[] OVERHEAT_CAP = {60f * 9, 60f * 8, 60f * 8};
    private static final float[] COOLDOWN_CAP = {60f * 4, 60f * 3, 60f * 1.5f};
    private static final float[] MAX_AS_CAP = {60f * 3, 60f * 3, 60f * 3};
    private static final float[] AS_MUL = {3.5f, 4.5f, 9.99f};

    private static Effect critFx, hitBulletFx, hitLancerFx;
    private static BulletType graphiteBullet, copperBullet, mk2Bullet, mk2bBullet;
    public static ItemTurret xylaon;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }

    private static void wrap(arc.scene.ui.layout.Cell<arc.scene.ui.Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static BasicBulletType makeBullet(float speed, float damage, float lifetime, float width, float height, String trail, String back) {
        BasicBulletType b = new BasicBulletType(speed, damage) {
            @Override
            public void hitEntity(Bullet bl, Hitboxc entity, float health) {
                super.hitEntity(bl, entity, health);
                if (Mathf.chance(0.5) && entity instanceof Healthc) {
                    ((Healthc) entity).damage(bl.damage * 0.8f);
                    critFx.at(bl.x, bl.y, bl.rotation());
                }
            }
        };
        b.lifetime = lifetime;
        b.width = width;
        b.height = height;
        b.trailChance = 0.2f;
        b.trailColor = Color.valueOf(trail);
        b.backColor = Color.valueOf(back);
        b.frontColor = Color.white;
        b.pierce = true;
        b.pierceCap = 3;
        b.homingPower = 0.04f;
        b.homingRange = 120f;
        return b;
    }

    /** Gọi từ NexMod.loadContent(): tạo block "xylaon" (hjson cùng tên bổ sung thuộc tính sau). */
    public static void create() {
        critFx = new Effect(15, e -> {
            Draw.color(Color.yellow, Color.white, e.fin());
            Lines.stroke(e.fout() * 1.5f);
            Lines.lineAngle(e.x, e.y, e.rotation, e.fout() * 12f);
        });
        hitBulletFx = new Effect(14, e -> {
            Draw.color(Color.orange, Color.white, e.fin());
            Lines.stroke(e.fout() * 2f);
            Lines.circle(e.x, e.y, e.fin() * 10f);
        });
        hitLancerFx = new Effect(20, e -> {
            Draw.color(PerfCore.color("#b92eff"), Color.white, e.fin());
            Lines.stroke(e.fout() * 2.5f);
            Lines.circle(e.x, e.y, e.fin() * 14f);
        });

        graphiteBullet = makeBullet(12f, 20f, 35f, 4f, 18f, "#66ccff", "#88ddff");

        copperBullet = makeBullet(11f, 12f, 38f, 3.5f, 15f, "#d99d73", "#ebaa78");
        copperBullet.trailChance = 0.15f;

        BasicBulletType m2 = makeBullet(15.6f, 26f, 35f, 5.2f, 23.4f, "#ffaa66", "#ffcc88");
        m2.hitSize = 13f;
        m2.despawnEffect = hitBulletFx;
        m2.knockback = 1.3f;
        m2.impact = true;
        mk2Bullet = m2;

        BasicBulletType m2b = makeBullet(16.5f, 13f, 18f, 4.5f, 25f, "#b92eff", "#e09cf1");
        m2b.trailChance = 0.1f;
        m2b.hitSize = 11f;
        m2b.despawnEffect = hitLancerFx;
        m2b.knockback = 0.8f;
        m2b.impact = true;
        mk2bBullet = m2b;

        xylaon = new ItemTurret("xylaon");
        xylaon.health = 500;
        xylaon.size = 4;
        xylaon.reload = 20f;
        xylaon.range = 420f;
        xylaon.configurable = true;
        xylaon.category = Category.turret;
        xylaon.itemCapacity = 30;
        xylaon.ammoPerShot = 1;
        xylaon.inaccuracy = 4.5f;

        xylaon.ammoTypes.put(Items.graphite, graphiteBullet);
        xylaon.ammoTypes.put(Items.copper, copperBullet);

        xylaon.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof XylaonBuild) ((XylaonBuild) tile).setTier(value);
        });

        xylaon.addBar("heat", (Building e) -> {
            XylaonBuild x = (XylaonBuild) e;
            return new Bar(
                () -> {
                    float heat = x.getHeat();
                    return heat < 0
                        ? "COOLING: " + (Mathf.floor(Math.abs(heat / 6f)) / 10f) + "s"
                        : "HEAT: " + (Mathf.floor(heat / 6f) / 10f) + "s";
                },
                () -> x.getHeat() < 0 ? Pal.heal : Pal.lightOrange,
                () -> {
                    float heat = x.getHeat();
                    int tier = x.getTier();
                    return heat < 0 ? Math.min(Math.abs(heat) / COOLDOWN_CAP[tier], 1f) : Math.min(heat / OVERHEAT_CAP[tier], 1f);
                });
        });

        xylaon.addBar("as", (Building e) -> {
            XylaonBuild x = (XylaonBuild) e;
            return new Bar(
                () -> {
                    int tier = x.getTier();
                    float heat = Math.max(x.getHeat(), 0f);
                    float bonus = Math.min(heat / MAX_AS_CAP[tier], 1f) * AS_MUL[tier] * 100f;
                    return "+" + Mathf.floor(bonus) + "% AS";
                },
                () -> Color.cyan,
                () -> Math.min(Math.max(x.getHeat(), 0f) / MAX_AS_CAP[x.getTier()], 1f));
        });

        xylaon.buildType = () -> new XylaonBuild(xylaon);
    }

    public static class XylaonBuild extends ItemTurret.ItemTurretBuild {
        float thermalstate = 0f;
        boolean thermalInit = false;
        int tierState = 0, paidTitanium = 0, paidSilicon = 0, paidPlastanium = 0;

        public XylaonBuild(ItemTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            paidTitanium = 0; paidSilicon = 0; paidPlastanium = 0;
            if (val == 0) health = 500f;
            if (val == 1) health = 650f;
            if (val == 2) health = 781f;
            maxHealth = health;
        }

        public float getHeat() { return thermalstate; }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 2) return 294f;
            if (tier == 1) return 544f;
            return 420f;
        }

        @Override
        public void updateTile() {
            if (!thermalInit) { thermalInit = true; thermalstate = -COOLDOWN_CAP[getTier()]; }
            if (!isShooting() || thermalstate < 0.2f) {
                float sign = thermalstate > 0 ? 1f : -1f;
                thermalstate -= Math.min(Math.abs(thermalstate), Time.delta) * sign;
            }
            super.updateTile();
        }

        @Override
        public float baseReloadSpeed() {
            if (thermalstate < 0) return 0f;
            int tier = getTier();
            float bonus = Math.min(Math.max(thermalstate, 0f) / MAX_AS_CAP[tier], 1f);
            return efficiency * (1f + bonus * AS_MUL[tier]);
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            thermalstate += OVERHEAT_CAP[tier] / 100f;
            if (thermalstate >= OVERHEAT_CAP[tier]) thermalstate = -COOLDOWN_CAP[tier];
            BulletType active = type;
            if (tier == 1) active = mk2Bullet;
            if (tier == 2) active = mk2bBullet;
            super.shoot(active);
        }

        @Override
        protected void updateReload() {
            if (getHeat() >= 0) super.updateReload();
            else reloadCounter = 0f;
        }

        @Override
        public boolean shouldTurn() { return thermalstate >= 0; }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(thermalstate);
            write.b(getTier());
            write.i(paidTitanium);
            write.i(paidSilicon);
            write.i(paidPlastanium);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            thermalstate = read.f();
            thermalInit = true;
            setTier(read.b());
            paidTitanium = read.i();
            paidSilicon = read.i();
            paidPlastanium = read.i();
        }

        // ---------------- nâng cấp ----------------

        boolean processPartialUpgrade(int targetTier, int[] req) {
            Building core = team.core();
            boolean isEn = en();
            if (core == null) {
                Vars.ui.showInfo(t(isEn, "[red]Team Core Not Found![]", "[red]Không tìm thấy Lõi Đội![]"));
                return false;
            }
            int reqT = req[0], reqS = req[1], reqP = req[2];
            int remT = reqT - paidTitanium, remS = reqS - paidSilicon, remP = reqP - paidPlastanium;
            int curT = core.items.get(Items.titanium), curS = core.items.get(Items.silicon), curP = core.items.get(Items.plastanium);
            String tc = curT >= remT ? "[green]" : "[red]", sc = curS >= remS ? "[green]" : "[red]", pc = curP >= remP ? "[green]" : "[red]";

            String scan;
            if (isEn) {
                scan = "[yellow]SINGLE SCAN - CORE ITEMS VS REQUIRED:[]\n";
                if (reqT > 0) scan += " • Titanium: " + tc + curT + "[] / " + remT + "\n";
                if (reqS > 0) scan += " • Silicon: " + sc + curS + "[] / " + remS + "\n";
                if (reqP > 0) scan += " • Plastanium: " + pc + curP + "[] / " + remP + "\n";
            } else {
                scan = "[yellow]KẾT QUẢ QUÉT LÕI - TÀI NGUYÊN / YÊU CẦU:[]\n";
                if (reqT > 0) scan += " • Titan: " + tc + curT + "[] / " + remT + "\n";
                if (reqS > 0) scan += " • Silicon: " + sc + curS + "[] / " + remS + "\n";
                if (reqP > 0) scan += " • Nhựa Plastanium: " + pc + curP + "[] / " + remP + "\n";
            }

            int takeT = Math.min(curT, Math.max(0, remT));
            int takeS = Math.min(curS, Math.max(0, remS));
            int takeP = Math.min(curP, Math.max(0, remP));
            if (takeT > 0) { NexNet.take(core, Items.titanium, takeT); paidTitanium += takeT; }
            if (takeS > 0) { NexNet.take(core, Items.silicon, takeS); paidSilicon += takeS; }
            if (takeP > 0) { NexNet.take(core, Items.plastanium, takeP); paidPlastanium += takeP; }

            if (paidTitanium >= reqT && paidSilicon >= reqS && paidPlastanium >= reqP) {
                if (targetTier == 1) NexNet.fx(Fx.upgradeCore, x, y); else NexNet.fx(Fx.bigShockwave, x, y);
                NexNet.fx(Fx.mineHuge, x, y);
                NexNet.shake(5, 5, x, y);
                if (Vars.net.active()) Call.tileConfig(Vars.player, this, Integer.valueOf(targetTier));
                else configure(Integer.valueOf(targetTier));
                return true;
            }
            Vars.ui.showInfo(scan + t(isEn, "\n[red]Not enough resources in Core![]", "\n[red]Thiếu tài nguyên trong Lõi![]"));
            return false;
        }

        private String cl(int c, int n) { return (c >= n ? "[green]" : "[red]") + c + "[]"; }

        private String requirementText(boolean isEn) {
            Building core = team.core();
            int curT = core == null ? 0 : core.items.get(Items.titanium);
            int curS = core == null ? 0 : core.items.get(Items.silicon);
            int curP = core == null ? 0 : core.items.get(Items.plastanium);
            int n2T = Math.max(0, REQ_MK2[0] - paidTitanium), n2S = Math.max(0, REQ_MK2[1] - paidSilicon);
            int nbT = Math.max(0, REQ_MK2B[0] - paidTitanium), nbS = Math.max(0, REQ_MK2B[1] - paidSilicon), nbP = Math.max(0, REQ_MK2B[2] - paidPlastanium);
            if (isEn) {
                return "[yellow]RESOURCE REQUIREMENTS FOR UPGRADE:[]\n[cyan]MK2 Branch:[]\n"
                    + " • Titanium: " + cl(curT, n2T) + " / " + n2T + "\n • Silicon: " + cl(curS, n2S) + " / " + n2S + "\n"
                    + "[purple]MK2B Branch:[]\n"
                    + " • Titanium: " + cl(curT, nbT) + " / " + nbT + "\n • Silicon: " + cl(curS, nbS) + " / " + nbS + "\n • Plastanium: " + cl(curP, nbP) + " / " + nbP;
            }
            return "[yellow]YÊU CẦU TÀI NGUYÊN NÂNG CẤP:[]\n[cyan]Nhánh MK2:[]\n"
                + " • Titan: " + cl(curT, n2T) + " / " + n2T + "\n • Silicon: " + cl(curS, n2S) + " / " + n2S + "\n"
                + "[purple]Nhánh MK2B:[]\n"
                + " • Titan: " + cl(curT, nbT) + " / " + nbT + "\n • Silicon: " + cl(curS, nbS) + " / " + nbS + "\n • Nhựa Plastanium: " + cl(curP, nbP) + " / " + nbP;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade Xylaon turret", "Nâng cấp tháp pháo Xylaon"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[red]XYLAON HAS REACHED MAX EVOLUTION LEVEL![]",
                    "[red]HỆ THỐNG XYLAON ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Max level reached", "Đã đạt cấp tối đa"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(t(isEn, "View detailed stats", "Xem thông số chi tiết hệ thống"));
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, "Xylaon Upgrade Center", "Trung tâm nâng cấp pháo Xylaon"));
            wrap(dialog.cont.add(requirementText(isEn)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add("[cyan]===(MK2)===[]").row();
            wrap(b1.add(t(isEn,
                "[white]• Health: [green]+30%[] ([green]650[] HP)\n• Range: [green]+29.5%[] ([orange]544[] px)\n• Base Damage: [green]+30%[] ([yellow]26[] DMG/bullet)\n• [orange]Accelerated Semiconductor Cooling:[] Attack speed increases up to [green]+450%[] based on heat buildup\n• While reducing cooldown lock time down to [orange]3.0s[]",
                "[white]• Máu cấu trúc: [green]+30%[] ([green]650[] HP)\n• Tầm bắn: [green]+29.5%[] ([orange]544[] px)\n• Sát thương gốc: [green]+30%[] ([yellow]26[] DMG/viên)\n• [orange]Tản Nhiệt Bán Dẫn Gia Tốc:[] Tốc độ xả đạn gia tăng tối đa [green]+450%[] theo nhiệt tích lũy\n• Đồng thời giảm thời gian khóa xả nhiệt xuống chỉ còn [orange]3.0[] giây")), 340);
            b1.row();
            b1.button(t(isEn, "[green]UPGRADE MK2[]", "[green]NÂNG CẤP MK2[]"), () -> {
                if (processPartialUpgrade(1, REQ_MK2)) { dialog.hide(); deselect(); }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add("[purple]===(MK2B)===[]").row();
            wrap(b2.add(t(isEn,
                "[white]• Health: [green]+56.25%[] ([green]781[] HP)\n• Range: [red]-30%[] ([orange]294[] px)\n• Base Damage: [red]-35%[] ([yellow]13[] DMG/bullet)\n• [orange]Cyclic Super-Impulse Burst:[] Accelerated fire rate hits an explosive [green]+999%[]\n• Automatically triggering ultra-fast [orange]1.5s[] cooling to maintain relentless close-range firepower",
                "[white]• Máu cấu trúc: [green]+56.25%[] ([green]781[] HP)\n• Tầm bắn: [red]-30%[] ([orange]294[] px)\n• Sát thương gốc: [red]-35%[] ([yellow]13[] DMG/viên)\n• [orange]Siêu Xung Bùng Nổ Chu Kỳ Tốc Độ:[] Tốc độ bắn gia tốc chạm mốc bùng nổ [green]+999%[]\n• Tự động kích hoạt xả nhiệt cực nhanh chỉ trong [orange]1.5[] giây để duy trì mật độ hỏa lực tầm gần liên tục")), 340);
            b2.row();
            b2.button(t(isEn, "[orange]UPGRADE MK2B[]", "[orange]NÂNG CẤP MK2B[]"), () -> {
                if (processPartialUpgrade(2, REQ_MK2B)) { dialog.hide(); deselect(); }
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
            String title = t(isEn, "Xylaon Turret Stats: ", "Thông số pháo Xylaon: ");
            String desc = "";
            int tier = getTier();
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = isEn
                    ? "[yellow]⚡ BASE STATS (MK1) ⚡[]\n[white]Turret HP: [green]500[]\nRange: [orange]420[] px\nBase Damage (Graphite): [yellow]20[] DMG/bullet\nBase Damage (Copper): [yellow]12[] DMG/bullet[]\n• [orange]Thermal Circuit System:[] Each shot builds [orange]1%[] heat ([yellow]540[] max)\n• Overheat forces a [orange]4.0s[] cooling shutdown\n• Sustained firing for [orange]3.0s[] grants up to [green]+350%[] attack speed"
                    : "[yellow]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n[white]Máu tháp pháo: [green]500[]\nTầm bắn: [orange]420[] px\nSát thương gốc (Graphite): [yellow]20[] DMG/viên\nSát thương gốc (Chì/Đồng): [yellow]12[] DMG/viên[]\n• [orange]Hệ Thống Nhiệt Mạch:[] Mỗi phát bắn tích lũy [orange]1%[] nhiệt lượng ([yellow]540[] điểm)\n• Quá nhiệt sẽ khóa pháo [orange]4.0[] giây để làm mát\n• Duy trì bắn liên tục trong [orange]3.0[] giây gia tăng tối đa [green]+350%[] tốc độ bắn";
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = isEn
                    ? "[cyan]⚡ UPGRADE STATS (MK2) ⚡[]\n[white]Turret HP: [green]650[] ([green]+30%[])\nRange: [orange]544[] px ([green]+29.5%[])\nBase Damage: [yellow]26[] DMG/bullet ([green]+30%[])[]\n• [orange]Enhanced Semiconductor Cooling:[] Max heat reduced to [yellow]480[] points\n• Cooling system lock duration reduced to [orange]3.0s[]\n• Peak attack speed increased to [green]+450%[]"
                    : "[cyan]⚡ THÔNG SỐ NÂNG CẤP (MK2) ⚡[]\n[white]Máu tháp pháo: [green]650[] ([green]+30%[])\nTầm bắn: [orange]544[] px ([green]+29.5%[])\nSát thương gốc: [yellow]26[] DMG/viên ([green]+30%[])[]\n• [orange]Tản Nhiệt Tăng Cường:[] Giới hạn chịu nhiệt giảm còn [yellow]480[] điểm\n• Thời gian khóa xả nhiệt rút ngắn còn [orange]3.0[] giây\n• Tốc độ bắn gia tốc cực đại đạt [green]+450%[]";
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = isEn
                    ? "[purple]⚡ SUPER-IMPULSE STATS (MK2B) ⚡[]\n[white]Turret HP: [green]781[] ([green]+56.25%[])\nRange: [orange]294[] px ([red]-30%[])\nBase Damage: [yellow]13[] DMG/bullet ([red]-35%[])[]\n• [orange]Cyclic Super-Impulse System:[] Reaches an explosive [green]+999%[] fire rate\n• System lock duration to flush heat is reduced to an extreme [orange]1.5s[] for continuous close-range output"
                    : "[purple]⚡ THÔNG SỐ SIÊU XUNG (MK2B) ⚡[]\n[white]Máu tháp pháo: [green]781[] ([green]+56.25%[])\nTầm bắn: [orange]294[] px ([red]-30%[])\nSát thương gốc: [yellow]13[] DMG/viên ([red]-35%[])[]\n• [orange]Hệ Thống Siêu Xung Chu Kỳ:[] Tốc độ bắn gia tốc bùng nổ lên mốc [green]+999%[]\n• Thời gian khóa xả sạch nhiệt giảm cực hạn xuống còn [orange]1.5[] giây giúp duy trì hỏa lực tầm gần liên tục";
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
