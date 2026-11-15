package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
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
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import newex.DamageDisplay;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ erysidus.js. Pháo Erysidus: tia Repulsor, đạn cận chiến, tích điểm hạ gục + bạo kích, nâng cấp MK2 / MK2B, rơi drone khi bị phá. */
public final class Erysidus {
    private Erysidus() {}

    private static final int[] REQ_MK2 = {2500, 2000, 1500};        // đồng, chì, silicon
    private static final int[] REQ_MK2B = {3500, 3000, 2500, 1500}; // đồng, chì, silicon, titan

    private static Effect customHitEffect, critEffect;
    private static LaserBulletType ironManBeam;
    private static BasicBulletType closeRangeBullet;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }
    private static String col(int have, int need) { return have >= need ? "[green]" : "[red]"; }

    private static void wrap(arc.scene.ui.layout.Cell<arc.scene.ui.Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static float applyCritDamage(Hitboxc target, float base, ErysidusBuild owner) {
        if (target == null || owner == null) return base;
        int stacks = owner.killStacks;
        float critChance = 0.05f + stacks * 0.01f;
        float mul = stacks >= 50 ? 2f : 1.5f;
        float dmg = base;
        if (Mathf.chance(critChance)) {
            dmg *= mul;
            critEffect.at(target.x(), target.y());
            if (target instanceof arc.math.geom.Position) DamageDisplay.crit((mindustry.gen.Posc) target);
        }
        return dmg;
    }

    private static void buildBullets() {
        customHitEffect = new Effect(20, e -> {
            Draw.color(PerfCore.color("90caf9"), Color.white, e.fout());
            Lines.stroke(e.fout() * 2f);
            Lines.circle(e.x, e.y, e.fin() * 12f);
        });
        critEffect = new Effect(25, e -> {
            Draw.color(Color.gold, Color.orange, e.fout());
            Lines.stroke(e.fout() * 3f);
            Lines.circle(e.x, e.y, e.fin() * 18f);
        });

        ironManBeam = new LaserBulletType(45f) {
            @Override
            public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
                if (other == null || b == null || !(b.owner instanceof ErysidusBuild) || !(other instanceof Healthc)) return;
                ErysidusBuild o = (ErysidusBuild) b.owner;
                Healthc h = (Healthc) other;
                float currentHp = h.health();
                float dmg = b.damage * (1f + o.killStacks * 0.01f);
                if (other instanceof Unit && ((Unit) other).isFlying()) dmg *= 2f;
                float fin = applyCritDamage(other, dmg, o);
                h.damage(fin);
                if (currentHp <= fin || h.dead() || !h.isAdded()) o.addKillStack();
            }
        };
        ironManBeam.length = 700f;
        ironManBeam.width = 28f;
        ironManBeam.lifetime = 4f;
        ironManBeam.colors = new Color[]{Color.valueOf("90caf9"), Color.valueOf("e3f2fd"), Color.white};
        ironManBeam.sideAngle = 45f;
        ironManBeam.sideWidth = 1.8f;
        ironManBeam.sideLength = 22f;
        ironManBeam.hitEffect = customHitEffect;
        ironManBeam.despawnEffect = Fx.none;
        ironManBeam.drawSize = 720f;
        ironManBeam.pierce = true;

        closeRangeBullet = new BasicBulletType(7f, 78f) {
            @Override
            public void draw(Bullet b) {
                Draw.color(PerfCore.color("90caf9"));
                Lines.stroke(3.5f);
                Lines.lineAngle(b.x, b.y, b.rotation(), 14f);
                Draw.color(PerfCore.color("e3f2fd"));
                Lines.stroke(1.5f);
                Lines.lineAngle(b.x, b.y, b.rotation(), 10f);
                Draw.reset();
            }

            @Override
            public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
                if (other == null || b == null || !(b.owner instanceof ErysidusBuild) || !(other instanceof Healthc)) return;
                ErysidusBuild o = (ErysidusBuild) b.owner;
                float fin = applyCritDamage(other, b.damage * (1f + o.killStacks * 0.01f), o);
                ((Healthc) other).damage(fin);
            }
        };
        closeRangeBullet.absorbable = true;
        closeRangeBullet.lifetime = 97f;
        closeRangeBullet.height = 20f;
        closeRangeBullet.width = 8f;
        closeRangeBullet.hitEffect = customHitEffect;
        closeRangeBullet.despawnEffect = customHitEffect;
        closeRangeBullet.pierce = true;
    }

    private static void spawnDrone(Team team, float x, float y, float rotation, int tier) {
        if (Vars.net.client()) return;
        UnitType type = Vars.content.getByName(ContentType.unit, "newex-erysidus-drone");
        if (type == null) type = Vars.content.getByName(ContentType.unit, "erysidus-drone");
        if (type == null) return;
        Unit drone = type.create(team);
        if (drone == null) return;
        drone.set(x, y);
        drone.rotation = rotation;
        float hp = tier == 1 ? 1650f : tier == 2 ? 2100f : 1250f;
        drone.maxHealth = hp;
        drone.health = hp;
        drone.add();
        Fx.spawn.at(x, y);
    }

    public static void init() {
        buildBullets();
        Events.on(ContentInitEvent.class, e -> {
            Events.on(BlockDestroyEvent.class, ev -> {
                if (ev.tile != null && ev.tile.build instanceof ErysidusBuild) {
                    ErysidusBuild b = (ErysidusBuild) ev.tile.build;
                    spawnDrone(b.team, b.x, b.y, b.rotation, b.getTier());
                }
            });

            Block blk = Vars.content.getByName(ContentType.block, "newex-erysidus");
            if (!(blk instanceof ItemTurret)) return;
            ItemTurret turret = (ItemTurret) blk;
            turret.configurable = true;
            turret.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof ErysidusBuild) ((ErysidusBuild) tile).setTier(value);
            });
            turret.buildType = () -> new ErysidusBuild(turret);
        });
    }

    public static class ErysidusBuild extends ItemTurret.ItemTurretBuild {
        final ItemTurret blockRef;
        int tierState = 0, killStacks = 0;
        float subGunTimer = 0f, beamChargeTimer = 0f, beamDurationTimer = 0f;
        boolean beamShot = false;

        public ErysidusBuild(ItemTurret outer) {
            outer.super();
            this.blockRef = outer;
        }

        public int getTier() { return tierState; }

        public void setTier(int v) {
            tierState = v;
            if (v == 0) health = 3125f;
            if (v == 1) health = 4125f;
            if (v == 2) health = 5250f;
            maxHealth = health;
        }

        public void addKillStack() {
            if (killStacks < 50) {
                killStacks++;
                Fx.heal.at(x, y);
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            int tier = getTier();

            Teamc beamTarget = Units.bestTarget(team, x, y, 700f, e -> e.checkTarget(true, true), tb -> true, (u, ux, uy) -> -u.health);
            boolean inBeamRange = beamTarget != null || (target != null && dst(target) <= 700f);

            if (hasAmmo()) {
                if (beamDurationTimer <= 0) {
                    if (beamChargeTimer < 300) beamChargeTimer += arc.util.Time.delta;
                    if (beamChargeTimer >= 300 && inBeamRange) {
                        beamChargeTimer = 0;
                        beamDurationTimer = 42;
                    }
                }
            } else if (beamDurationTimer <= 0) {
                beamChargeTimer = Math.max(0, beamChargeTimer - arc.util.Time.delta);
            }

            if (beamDurationTimer > 0 && hasAmmo()) {
                beamDurationTimer -= arc.util.Time.delta;
                float targetAngle = rotation;
                if (beamTarget != null) targetAngle = angleTo(beamTarget);
                else if (target != null) targetAngle = angleTo(target);
                rotation = Mathf.slerpDelta(rotation, targetAngle, 0.2f);

                if (!beamShot) {
                    beamShot = true;
                    float beamDmg = tier == 2 ? 68f : 45f;
                    Bullet beam = ironManBeam.create(this, team, x, y, targetAngle, 1f, 1f);
                    if (beam != null) beam.damage = beamDmg;
                    Effect.shake(1.5f, 1.5f, x, y);
                }
            }

            if (beamDurationTimer <= 0) beamShot = false;

            if (beamDurationTimer <= 0 && hasAmmo() && isShooting() && target != null) {
                if (dst(target) <= 675f) {
                    subGunTimer += arc.util.Time.delta;
                    float delay = tier == 1 ? 6f : 12f;
                    if (subGunTimer >= delay) {
                        subGunTimer = 0;
                        float angleOffset = Mathf.range(4f);
                        Vec2 tr = new Vec2();
                        tr.trns(rotation, 12f);
                        float bulletDmg = tier == 2 ? 117f : 78f;
                        Bullet bl = closeRangeBullet.create(this, team, x + tr.x, y + tr.y, rotation + angleOffset, 1f, 1f);
                        if (bl != null) bl.damage = bulletDmg;
                        customHitEffect.at(x + tr.x, y + tr.y);
                    }
                } else {
                    subGunTimer = 0;
                }
            } else if (beamDurationTimer > 0) {
                subGunTimer = 0;
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Draw.color(PerfCore.color("90caf9"));
            Lines.stroke(1f);
            Drawf.dashCircle(x, y, 700f, PerfCore.color("90caf9"));
            Draw.reset();
        }

        @Override
        protected void shoot(BulletType type) {
            int tier = getTier();
            super.shoot(type);
            if (tier == 1) reloadCounter += blockRef.reload * (1.3f / 2.3f);
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.f(beamChargeTimer);
            write.f(beamDurationTimer);
            write.i(killStacks);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            beamChargeTimer = read.f();
            beamDurationTimer = read.f();
            killStacks = read.i();
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade Erysidus firepower structure", "Nâng cấp cấu trúc hỏa lực Erysidus"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]ERYSIDUS SYSTEM HAS REACHED MAX EVOLUTION![]",
                    "[scarlet]HỆ THỐNG ERYSIDUS ĐÃ ĐẠT GIỚI HẠN TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Reached maximum level", "Đã đạt cấp tối đa"));
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(50, 40).tooltip(t(isEn, "View detailed system stats", "Xem chi tiết thông số hệ thống"));
        }

        private String requirementText(boolean isEn) {
            Building core = team.core();
            if (core == null) return t(isEn, "[red]Team Core not found![]", "[red]Không tìm thấy Lõi Đội![]");
            int cu = core.items.get(Items.copper), pb = core.items.get(Items.lead), si = core.items.get(Items.silicon), ti = core.items.get(Items.titanium);
            String c1 = col(cu, REQ_MK2[0]), l1 = col(pb, REQ_MK2[1]), s1 = col(si, REQ_MK2[2]);
            String c2 = col(cu, REQ_MK2B[0]), l2 = col(pb, REQ_MK2B[1]), s2 = col(si, REQ_MK2B[2]), t2 = col(ti, REQ_MK2B[3]);
            if (isEn) {
                return "[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Config Branch (Speed):[]\n"
                    + " • Copper: " + c1 + cu + "[] / " + REQ_MK2[0] + "\n • Lead: " + l1 + pb + "[] / " + REQ_MK2[1] + "\n • Silicon: " + s1 + si + "[] / " + REQ_MK2[2] + "\n"
                    + "[purple]MK2B Config Branch (Damage):[]\n"
                    + " • Copper: " + c2 + cu + "[] / " + REQ_MK2B[0] + "\n • Lead: " + l2 + pb + "[] / " + REQ_MK2B[1] + "\n • Silicon: " + s2 + si + "[] / " + REQ_MK2B[2] + "\n • Titanium: " + t2 + ti + "[] / " + REQ_MK2B[3];
            }
            return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh Cấu Hình MK2 (Tốc độ):[]\n"
                + " • Đồng: " + c1 + cu + "[] / " + REQ_MK2[0] + "\n • Chì: " + l1 + pb + "[] / " + REQ_MK2[1] + "\n • Silicon: " + s1 + si + "[] / " + REQ_MK2[2] + "\n"
                + "[purple]Nhánh Cấu Hình MK2B (Sát Thương):[]\n"
                + " • Đồng: " + c2 + cu + "[] / " + REQ_MK2B[0] + "\n • Chì: " + l2 + pb + "[] / " + REQ_MK2B[1] + "\n • Silicon: " + s2 + si + "[] / " + REQ_MK2B[2] + "\n • Titan: " + t2 + ti + "[] / " + REQ_MK2B[3];
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, "Erysidus Upgrade Center", "Trung tâm nâng cấp Erysidus"));
            wrap(dialog.cont.add(requirementText(isEn)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add(t(isEn, "[cyan]===(MK2 - SUPER FIREPOWER SPEED)===[]", "[cyan]===(MK2 - SIÊU TỐC HỎA LỰC)===[]")).row();
            wrap(b1.add(t(isEn,
                "Optimize reload system and firepower output:\n [white]• Max health increased to [green]4125 HP[].[]\n [white]• [cyan]+130% missile turret speed[].[]\n [white]• Ultra-fast melee ammo ([yellow]0.1s/shot[]).[]",
                "Tối ưu hóa hệ thống nạp đạn và xả hỏa lực:\n [white]• Tăng lượng máu tối đa lên [green]4125 HP[].[]\n [white]• Tăng [cyan]130% tốc độ pháo tên lửa[].[]\n [white]• Đạn cận chiến bắn siêu tốc ([yellow]0.1s/viên[]).[]")), 340);
            b1.row();
            b1.button(t(isEn, "[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_MK2[0] && core.items.get(Items.lead) >= REQ_MK2[1] && core.items.get(Items.silicon) >= REQ_MK2[2]) {
                    NexNet.take(core, Items.copper, REQ_MK2[0]); NexNet.take(core, Items.lead, REQ_MK2[1]); NexNet.take(core, Items.silicon, REQ_MK2[2]);
                    NexNet.fx(Fx.upgradeCore, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    setTier(1);
                    NexNet.configure(this, Integer.valueOf(1));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(t(isEn, "[red]Not enough resources for MK2 branch![]", "[red]Không đủ tài nguyên cho nhánh MK2![]"));
                }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add(t(isEn, "[purple]===(MK2B - DAMAGE ENHANCEMENT)===[]", "[purple]===(MK2B - CƯỜNG HÓA SÁT THƯƠNG)===[]")).row();
            wrap(b2.add(t(isEn,
                "Breakthrough in energy enhancement technology:\n [white]• Max health increased to [green]5250 HP[].[]\n [white]• [red]+50% damage[] for all sub-bullets and Repulsor Beam.[]",
                "Đột phá công nghệ cường hóa năng lượng:\n [white]• Gia tăng lượng máu lên tối đa [green]5250 HP[].[]\n [white]• Tăng [red]50% sát thương[] cho tất cả đạn phụ và Chưởng Repulsor.[]")), 340);
            b2.row();
            b2.button(t(isEn, "[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"), () -> {
                Building core = team.core();
                if (core != null && core.items.get(Items.copper) >= REQ_MK2B[0] && core.items.get(Items.lead) >= REQ_MK2B[1]
                    && core.items.get(Items.silicon) >= REQ_MK2B[2] && core.items.get(Items.titanium) >= REQ_MK2B[3]) {
                    NexNet.take(core, Items.copper, REQ_MK2B[0]); NexNet.take(core, Items.lead, REQ_MK2B[1]);
                    NexNet.take(core, Items.silicon, REQ_MK2B[2]); NexNet.take(core, Items.titanium, REQ_MK2B[3]);
                    NexNet.fx(Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                    setTier(2);
                    NexNet.configure(this, Integer.valueOf(2));
                    dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfo(t(isEn, "[red]Not enough resources for MK2B branch![]", "[red]Không đủ tài nguyên cho nhánh MK2B![]"));
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

        private void openInfo(boolean isEn) {
            String title = t(isEn, " Erysidus Stats: ", " Thông số Erysidus: ");
            String desc = "";
            int tier = getTier();
            int stacks = killStacks;
            int critChance = 5 + stacks;
            int critDmg = stacks >= 50 ? 100 : 50;
            String maxStatus = stacks >= 50 ? t(isEn, " [gold](MAX!)[]", " [gold](TỐI ĐA!)[]") : "";
            String plus50 = stacks >= 50 ? " [gold](+50% Max Stack)[]" : "";

            String buff = isEn
                ? "\n\n[gold]⭐ KILL BUFF STATS ⭐[]\n[lightgray]Kill Buff Stacks:[] [cyan]" + stacks + "/50 Stacks[]" + maxStatus
                    + "\n[lightgray]Crit Rate:[] [yellow]" + critChance + "%[] (Base 5% + " + stacks + "%)\n[lightgray]Crit Damage:[] [orange]+" + critDmg + "%[]" + plus50
                : "\n\n[gold]⭐ THÔNG SỐ BUFF KẾT LIỄU ⭐[]\n[lightgray]Tầng Buff tiêu diệt:[] [cyan]" + stacks + "/50 Stack[]" + maxStatus
                    + "\n[lightgray]Tỉ lệ Bạo kích (Crit Rate):[] [yellow]" + critChance + "%[] (Gốc 5% + " + stacks + "%)\n[lightgray]Sát thương Bạo kích (Crit DMG):[] [orange]+" + critDmg + "%[]" + plus50;

            if (tier == 0) {
                title += "[yellow](MK1)[]";
                desc = isEn
                    ? "[gold]⚡ BASE STATS (MK1) ⚡[]\n[lightgray]Health:[] [green]3125 HP[]\n[lightgray]Melee Ammo:[] Fires every 0.2s\n[lightgray]Repulsor Beam:[] Emits every 5s when target acquired" + buff
                    : "[gold]⚡ THÔNG SỐ GỐC (MK1) ⚡[]\n[lightgray]Máu:[] [green]3125 HP[]\n[lightgray]Đạn cận chiến:[] Bắn mỗi 0.2s\n[lightgray]Tia Repulsor:[] Phát mỗi 5s khi có mục tiêu" + buff;
            } else if (tier == 1) {
                title += "[cyan](MK2)[]";
                desc = isEn
                    ? "[cyan]⚡ SPEED CONFIG (MK2) ⚡[]\n[lightgray]Health:[] [green]4125 HP[]\n[lightgray]Fire Rate:[] [green]+130% Fire Rate[]\n[lightgray]Melee Ammo:[] Fires every 0.1s" + buff
                    : "[cyan]⚡ CẤU HÌNH TỐC ĐỘ (MK2) ⚡[]\n[lightgray]Máu:[] [green]4125 HP[]\n[lightgray]Tốc độ xả đạn:[] [green]+130% Tốc độ bắn[]\n[lightgray]Đạn cận chiến:[] Bắn mỗi 0.1s" + buff;
            } else if (tier == 2) {
                title += "[purple](MK2B)[]";
                desc = isEn
                    ? "[purple]⚡ DAMAGE CONFIG (MK2B) ⚡[]\n[lightgray]Health:[] [green]5250 HP[]\n[lightgray]Repulsor Beam:[] [orange]+50% Base Damage[]" + buff
                    : "[purple]⚡ CẤU HÌNH SÁT THƯƠNG (MK2B) ⚡[]\n[lightgray]Máu:[] [green]5250 HP[]\n[lightgray]Chưởng Repulsor:[] [orange]+50% Sát thương gốc[]" + buff;
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
