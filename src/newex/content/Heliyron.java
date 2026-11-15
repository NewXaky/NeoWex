package newex.content;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
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
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ heliyron.js. Pháo càng mất máu càng mạnh: tự gây sát thương lên bản thân, bắn đạn phụ truy đuổi; nâng cấp MK2 / MK2B. */
public final class Heliyron {
    private Heliyron() {}

    private static final int[] REQ_MK2 = {8000, 4000, 0};    // đồng, chì, titan
    private static final int[] REQ_MK2B = {8000, 4000, 2000};

    private static Sound shootSound;
    private static Effect particleFx;
    private static BasicBulletType mk1Bullet, mk2Bullet, mk2bBullet, subBullet, subBullet2;
    public static HeliyronBlock heliyron;

    private static boolean vn() { return Core.settings.getString("locale", "en").startsWith("vi"); }
    private static String t(boolean v, String vi, String en) { return v ? vi : en; }

    private static void wrap(arc.scene.ui.layout.Cell<arc.scene.ui.Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static TextureRegion modRegion(String n) {
        if (Core.atlas.has(n)) return PerfCore.region(n);
        if (Core.atlas.has("newex-" + n)) return PerfCore.region("newex-" + n);
        if (Core.atlas.has("reinforced-" + n)) return PerfCore.region("reinforced-" + n);
        return PerfCore.region("clear");
    }

    /** Đạn chính: tự gây hao máu cho pháo mỗi lần trúng. */
    private static BasicBulletType mainBullet(float homingPower, float homingRange) {
        BasicBulletType b = new BasicBulletType(17f, 150f) {
            @Override
            public void hitEntity(Bullet bl, Hitboxc entity, float health) {
                super.hitEntity(bl, entity, health);
                if (bl.owner instanceof HeliyronBuild) ((HeliyronBuild) bl.owner).damageSelfPercent(1f);
            }
        };
        b.width = 7f; b.height = 38f; b.lifetime = 33f;
        b.frontColor = Color.valueOf("#e0f7fa"); b.backColor = Color.valueOf("#d47c00");
        b.hitEffect = Fx.hitBulletColor; b.despawnEffect = Fx.hitBulletColor;
        b.trailEffect = particleFx; b.trailInterval = 2f;
        if (homingPower > 0) { b.homingPower = homingPower; b.homingRange = homingRange; }
        return b;
    }

    private static BasicBulletType sub(float speed, String front, String back, float hp, float hr) {
        BasicBulletType b = new BasicBulletType(speed, 120f);
        b.width = 5f; b.height = 26f; b.lifetime = 50f;
        b.frontColor = Color.valueOf(front); b.backColor = Color.valueOf(back);
        b.hitEffect = Fx.hitBulletColor; b.despawnEffect = Fx.hitBulletColor;
        b.homingPower = hp; b.homingRange = hr;
        b.trailEffect = particleFx; b.trailInterval = 3f;
        return b;
    }

    /** Khối pháo tạo bằng code (hjson cùng tên "heliyron" bổ sung thuộc tính sau). */
    public static class HeliyronBlock extends ItemTurret {
        TextureRegion bodyRegion, c1Region, c2Region, efcoreRegion;

        public HeliyronBlock(String name) { super(name); }

        @Override
        public void load() {
            super.load();
            bodyRegion = modRegion("heliyron-body");
            c1Region = modRegion("heliyron-c1");
            c2Region = modRegion("heliyron-c2");
            efcoreRegion = modRegion("heliyron-efcore");
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        shootSound = Vars.tree.loadSound("loudly-blaster-shot-1");

        particleFx = new Effect(18, e -> {
            Draw.color(e.color);
            float[] angles = {0, 90, 180, 270};
            for (int i = 0; i < angles.length; i++) {
                Angles.randLenVectors(e.id + i * 10, 3, 8 + e.fin() * 12, e.rotation + angles[i], 25, (x, y) -> {
                    Fill.circle(e.x + x, e.y + y, 1.5f * e.fout());
                });
            }
        });

        mk1Bullet = mainBullet(0f, 0f);
        mk2Bullet = mainBullet(0f, 0f);
        mk2bBullet = mainBullet(0.12f, 160f);
        subBullet = sub(15f, "#e0f7fa", "#ff9800", 0.35f, 80f);
        subBullet2 = sub(16f, "#ffffff", "#00e5ff", 0.25f, 240f);

        heliyron = new HeliyronBlock("heliyron");
        heliyron.health = 3625;
        heliyron.size = 3;
        heliyron.reload = 35f;
        heliyron.recoil = 0f;
        heliyron.configurable = true;
        heliyron.category = Category.turret;
        heliyron.shootSound = shootSound;

        heliyron.ammoTypes.put(Items.copper, mk1Bullet);
        heliyron.ammoTypes.put(Items.lead, mk1Bullet);

        heliyron.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof HeliyronBuild) ((HeliyronBuild) tile).setTier(value);
        });

        heliyron.buildType = () -> new HeliyronBuild(heliyron);
    }

    public static class HeliyronBuild extends ItemTurret.ItemTurretBuild {
        final HeliyronBlock hb;
        int tierState = 0;
        float customRecoil = 0f, coreScaleVisual = 0.3f;

        public HeliyronBuild(HeliyronBlock outer) { outer.super(); this.hb = outer; }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            if (val == 0) health = 3625f;
            if (val == 1) health = 5438f;
            if (val == 2) health = 6525f;
            maxHealth = health;
        }

        public void damageSelfPercent(float percent) {
            float minLimit = maxHealth * 0.01f;
            if (health > minLimit) {
                health -= maxHealth * (percent / 100f);
                if (health < minLimit) health = minLimit;
            }
        }

        public float getDamageMultiplier() {
            float missing = Math.max(0f, (1f - health / maxHealth) * 100f);
            int steps = Mathf.floor(missing / 10f);
            return 1f + steps * 0.5f;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 2) return 560f;
            if (tier == 1) return 570f;
            return 550f;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            customRecoil = Mathf.approach(customRecoil, 0f, 0.08f * Time.delta);

            float missingRatio = Math.max(0f, 1f - health / maxHealth);
            float targetScale = 0.3f + missingRatio * 0.7f;
            coreScaleVisual = Mathf.approach(coreScaleVisual, targetScale, 0.05f * Time.delta);

            if (getTier() == 2 && health / maxHealth < 0.5f) {
                if (isShooting() && hasAmmo()) reloadCounter += Time.delta * (efficiency * 2f);
            }
        }

        @Override
        protected void shoot(mindustry.entities.bullet.BulletType type) {
            int tier = getTier();
            BasicBulletType bullet = mk1Bullet;
            if (tier == 1) bullet = mk2Bullet;
            if (tier == 2) bullet = mk2bBullet;

            customRecoil = 1f;
            float mul = getDamageMultiplier();

            if (shootSound != null) shootSound.at(x, y, Mathf.random(0.9f, 1.1f));

            Bullet b = bullet.create(this, team, x, y, rotation);
            if (b != null) b.damage = bullet.damage * mul;

            if (Mathf.chance(0.4)) {
                float rad = rotation * Mathf.degRad;
                float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);
                float subDmg = bullet.damage * 0.8f * mul;

                float[] side1 = {-2f, 2f};
                for (float side : side1) {
                    Bullet sb = subBullet.create(this, team, x + (-sin * side), y + (cos * side), rotation);
                    if (sb != null) sb.damage = subDmg;
                }

                float[] side3 = {-3f, 3f};
                float back3 = -3f;
                for (float side : side3) {
                    Bullet sb = subBullet.create(this, team, x + (-sin * side) + (cos * back3), y + (cos * side) + (sin * back3), rotation);
                    if (sb != null) sb.damage = subDmg;
                }

                float[] side10 = {-10f, 10f}, ang10 = {-25f, 25f};
                for (int i = 0; i < 2; i++) {
                    float side = side10[i];
                    Bullet sb = subBullet2.create(this, team, x + (-sin * side), y + (cos * side), rotation + ang10[i]);
                    if (sb != null) sb.damage = subDmg;
                }

                float[] side1b = {-1f, 1f}, ang45 = {-45f, 45f};
                float back5 = -5f;
                for (int i = 0; i < 2; i++) {
                    float side = side1b[i];
                    Bullet sb = subBullet2.create(this, team, x + (-sin * side) + (cos * back5), y + (cos * side) + (sin * back5), rotation + ang45[i]);
                    if (sb != null) sb.damage = subDmg;
                }

                Fx.shootBig.at(x, y, rotation);
                useAmmo();
            }
        }

        @Override
        public void draw() {
            super.draw();

            float rot = rotation - 90f;
            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

            if (hb.bodyRegion != null && hb.bodyRegion.found()) Draw.rect(hb.bodyRegion, x, y, rot);

            float rd = customRecoil * 5f;
            float c1X = x + (-sin * rd) - (cos * rd), c1Y = y + (cos * rd) - (sin * rd);
            float c2X = x + (sin * rd) - (cos * rd), c2Y = y + (-cos * rd) - (sin * rd);

            if (hb.c1Region != null && hb.c1Region.found()) Draw.rect(hb.c1Region, c1X, c1Y, rot);
            if (hb.c2Region != null && hb.c2Region.found()) Draw.rect(hb.c2Region, c2X, c2Y, rot);

            if (hb.efcoreRegion != null && hb.efcoreRegion.found()) {
                Draw.z(Layer.turret + 0.01f);
                float cw = hb.efcoreRegion.width * Draw.scl * coreScaleVisual;
                float ch = hb.efcoreRegion.height * Draw.scl * coreScaleVisual;
                Draw.blend(Blending.additive);
                Draw.rect(hb.efcoreRegion, x, y, cw, ch, rot);
                Draw.blend();
                Draw.z(Layer.turret);
            }
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            customRecoil = 0f;
            coreScaleVisual = 0.3f;
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean v = vn();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(v))
                    .size(50, 40).tooltip(t(v, "Nâng cấp pháo Heliyron", "Upgrade Heliyron turret"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(v,
                    "[scarlet]PHÁO HELIYRON ĐÃ ĐẠT CẤP TỐI ĐA TRONG NHÁNH![]", "[scarlet]HELIYRON HAS REACHED MAXIMUM TIER![]")))
                    .size(50, 40).tooltip(t(v, "Đã đạt cấp tối đa", "Max tier reached"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(v))
                .size(50, 40).tooltip(t(v, "Xem thông số pháo Heliyron", "View Heliyron stats"));
        }

        private String requirementText(boolean v) {
            Building core = team.core();
            if (core == null) return t(v, "[red]Không tìm thấy Lõi Đội![]", "[red]No Team Core Found![]");
            int cu = core.items.get(Items.copper), pb = core.items.get(Items.lead), ti = core.items.get(Items.titanium);
            String c1 = cu >= REQ_MK2[0] ? "[green]" : "[red]", l1 = pb >= REQ_MK2[1] ? "[green]" : "[red]";
            String c2 = cu >= REQ_MK2B[0] ? "[green]" : "[red]", l2 = pb >= REQ_MK2B[1] ? "[green]" : "[red]", t2 = ti >= REQ_MK2B[2] ? "[green]" : "[red]";
            if (v) {
                return "[yellow]YÊU CẦU TÀI NGUYÊN NÂNG CẤP:[]\n"
                    + "[cyan]Nhánh MK2:[] " + c1 + "Đồng " + cu + "/" + REQ_MK2[0] + "[] | " + l1 + "Chì " + pb + "/" + REQ_MK2[1] + "[]\n"
                    + "[purple]Nhánh MK2B:[] " + c2 + "Đồng " + cu + "/" + REQ_MK2B[0] + "[] | " + l2 + "Chì " + pb + "/" + REQ_MK2B[1] + "[] | " + t2 + "Titan " + ti + "/" + REQ_MK2B[2] + "[]";
            }
            return "[yellow]UPGRADE RESOURCE REQUIREMENTS:[]\n"
                + "[cyan]MK2 Branch:[] " + c1 + "Copper " + cu + "/" + REQ_MK2[0] + "[] | " + l1 + "Lead " + pb + "/" + REQ_MK2[1] + "[]\n"
                + "[purple]MK2B Branch:[] " + c2 + "Copper " + cu + "/" + REQ_MK2B[0] + "[] | " + l2 + "Lead " + pb + "/" + REQ_MK2B[1] + "[] | " + t2 + "Titanium " + ti + "/" + REQ_MK2B[2] + "[]";
        }

        private void openUpgrade(boolean v) {
            BaseDialog dialog = new BaseDialog(t(v, "Trung tâm Nâng cấp Heliyron", "Heliyron Upgrade Center"));
            wrap(dialog.cont.add(requirementText(v)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add(t(v, "[cyan]===(CẤU HÌNH MK2)===[]", "[cyan]===(MK2 CONFIG)===[]")).row();
            wrap(b1.add(t(v,
                " [white]• Máu: [green]+50%[] (lên 5,438 HP)\n [white]• Sát thương gốc: [lightgray]150[]\n [white]• Phạm vi bắn: [orange]+3.6%[] (lên 570 px)\n [white]• Nội tại: [yellow]+50% Sát thương[] với mỗi [red]10% HP mất[]\n [white]• Cơ chế: Mất [red]1% HP[] khi trúng địch | 40% cơ hội bắn thêm 8 đạn phụ (4 thẳng 80% SD, 4 chéo 80% SD tầm xa)",
                " [white]• Health: [green]+50%[] (up to 5,438 HP)\n [white]• Base Damage: [lightgray]150[]\n [white]• Range: [orange]+3.6%[] (up to 570 px)\n [white]• Passive: [yellow]+50% Damage[] per [red]10% missing HP[]\n [white]• Mechanism: Lose [red]1% HP[] on hit | 40% chance for 8 extra bullets (4 straight 80% DMG, 4 diagonal long-range)")), 340);
            b1.row();
            b1.button(t(v, "[green]KÍCH HOẠT MK2[]", "[green]ACTIVATE MK2[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_MK2[0] && core.items.get(Items.lead) >= REQ_MK2[1]) {
                    NexNet.take(core, Items.copper, REQ_MK2[0]); NexNet.take(core, Items.lead, REQ_MK2[1]);
                    NexNet.fx(Fx.upgradeCore, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    configure(Integer.valueOf(1)); dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(t(v, "[red]Không đủ tài nguyên cho MK2![]", "[red]Not enough resources for MK2![]"));
                }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add(t(v, "[purple]===(CẤU HÌNH MK2B)===[]", "[purple]===(MK2B CONFIG)===[]")).row();
            wrap(b2.add(t(v,
                " [white]• Máu: [green]+80%[] (lên 6,525 HP)\n [white]• Sát thương gốc: [lightgray]150[] (Đạn tự dẫn đường 12%)\n [white]• Phạm vi bắn: [orange]+1.8%[] (lên 560 px)\n [white]• Nội tại Cuồng Bạo: Khi Máu [red]< 50% HP[] -> [yellow]+200% Tốc bắn (x3)[]\n [white]• Cơ chế: Mất [red]1% HP[] khi trúng địch | 40% cơ hội bắn thêm 8 đạn phụ (4 thẳng 80% SD, 4 chéo 80% SD tầm xa)",
                " [white]• Health: [green]+80%[] (up to 6,525 HP)\n [white]• Base Damage: [lightgray]150[] (12% Homing)\n [white]• Range: [orange]+1.8%[] (up to 560 px)\n [white]• Berserk Passive: When HP [red]< 50%[] -> [yellow]+200% Fire Rate (x3)[]\n [white]• Mechanism: Lose [red]1% HP[] on hit | 40% chance for 8 extra bullets (4 straight 80% DMG, 4 diagonal long-range)")), 340);
            b2.row();
            b2.button(t(v, "[orange]KÍCH HOẠT MK2B[]", "[orange]ACTIVATE MK2B[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_MK2B[0] && core.items.get(Items.lead) >= REQ_MK2B[1] && core.items.get(Items.titanium) >= REQ_MK2B[2]) {
                    NexNet.take(core, Items.copper, REQ_MK2B[0]); NexNet.take(core, Items.lead, REQ_MK2B[1]); NexNet.take(core, Items.titanium, REQ_MK2B[2]);
                    NexNet.fx(Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    configure(Integer.valueOf(2)); dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(t(v, "[red]Không đủ tài nguyên cho MK2B![]", "[red]Not enough resources for MK2B![]"));
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

        private String mech(boolean v, boolean berserk, boolean excl) {
            String e = excl ? "!" : ".";
            if (v) {
                return "[yellow]CƠ CHẾ BẮN & NỘI TẠI:[]\n• Mất [red]10% HP[] -> [yellow]+50% Sát thương[] cho mọi đạn" + e + "\n"
                    + (berserk ? "• [orange]Nội tại Cuồng Bạo:[] Khi Máu [red]< 50% HP[] -> [yellow]+200% Tốc bắn (x3)[]!\n" : "")
                    + "• Tự tổn hại: Mất [red]1% HP[] hiện tại mỗi khi bắn trúng mục tiêu.\n"
                    + "• Đạn phụ: [cyan]40% cơ hội[] kích hoạt 8 đạn phụ có sát thương bằng [yellow]80%[] đạn gốc:\n"
                    + "   - [orange]4 viên bắn thẳng:[] Phạm vi đuổi ngắn.\n   - [cyan]4 viên bắn chéo:[] Phạm vi truy đuổi rộng (tầm xa).";
            }
            return "[yellow]MECHANICS & PASSIVE:[]\n• Lost [red]10% HP[] -> [yellow]+50% Damage[] for all bullets" + e + "\n"
                + (berserk ? "• [orange]Berserk Passive:[] When HP [red]< 50%[] -> [yellow]+200% Fire Rate (x3)[]!\n" : "")
                + "• Self-damage: Lose [red]1% current HP[] whenever hitting a target.\n"
                + "• Sub-bullets: [cyan]40% chance[] to fire 8 sub-bullets dealing [yellow]80%[] base damage:\n"
                + "   - [orange]4 straight bullets:[] Short homing range.\n   - [cyan]4 diagonal bullets:[] Long homing range.";
        }

        private void openInfo(boolean v) {
            int tier = getTier();
            String title = t(v, " Thông số Heliyron: ", " Heliyron Stats: ");
            String desc = "";
            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = t(v,
                    "[gold]⚡ THÔNG SỐ HELIYRON MK1 ⚡[]\n[lightgray]Máu tối đa:[] [green]3,625 HP[]\n[lightgray]Sát thương gốc:[] [orange]150[]\n[lightgray]Phạm vi bắn:[] [orange]550 px[]\n\n",
                    "[gold]⚡ HELIYRON MK1 STATS ⚡[]\n[lightgray]Max Health:[] [green]3,625 HP[]\n[lightgray]Base Damage:[] [orange]150[]\n[lightgray]Range:[] [orange]550 px[]\n\n") + mech(v, false, false);
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = t(v,
                    "[cyan]⚡ THÔNG SỐ HELIYRON MK2 ⚡[]\n[lightgray]Máu tối đa:[] [green]5,438 HP[] [lime](+50%)[]\n[lightgray]Sát thương gốc:[] [orange]150[]\n[lightgray]Phạm vi bắn:[] [orange]570 px[] [lime](+3.6%)[]\n\n",
                    "[cyan]⚡ HELIYRON MK2 STATS ⚡[]\n[lightgray]Max Health:[] [green]5,438 HP[] [lime](+50%)[]\n[lightgray]Base Damage:[] [orange]150[]\n[lightgray]Range:[] [orange]570 px[] [lime](+3.6%)[]\n\n") + mech(v, false, true);
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = t(v,
                    "[purple]⚡ THÔNG SỐ HELIYRON MK2B ⚡[]\n[lightgray]Máu tối đa:[] [green]6,525 HP[] [lime](+80%)[]\n[lightgray]Sát thương gốc:[] [orange]150[] [cyan](Đạn tự dẫn đường 12%)[]\n[lightgray]Phạm vi bắn:[] [orange]560 px[] [lime](+1.8%)[]\n\n",
                    "[purple]⚡ HELIYRON MK2B STATS ⚡[]\n[lightgray]Max Health:[] [green]6,525 HP[] [lime](+80%)[]\n[lightgray]Base Damage:[] [orange]150[] [cyan](12% Homing)[]\n[lightgray]Range:[] [orange]560 px[] [lime](+1.8%)[]\n\n") + mech(v, true, false);
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
