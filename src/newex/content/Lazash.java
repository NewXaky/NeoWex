package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ lazash.js. Pháo laser/chém đánh công trình địch, ghép chuỗi với pháo kề sát (4 hướng),
 * 2 bảng phúc lợi A/B quay ngẫu nhiên bằng tài nguyên lõi. Block do Java tạo (hjson KHÔNG có `type:` cấp cao nhất).
 */
public final class Lazash {
    private static final float RANGE = 10605f;
    private static final int[] REQ_A = {2000, 2000, 2000}; // đồng, chì, silicon
    private static final int[] REQ_B = {1000, 1000, 1000}; // titan, thorium, than chì

    public static LazashTurret lazash;

    private Lazash() {}

    /** Dữ liệu hiệu ứng nhát chém (thay cho object literal {len, widthMult} của JS). */
    private static final class SlashData {
        final float len, widthMult;
        SlashData(float len, float widthMult) { this.len = len; this.widthMult = widthMult; }
    }

    private static boolean isEn() {
        String loc = String.valueOf(Core.settings.getString("locale", "vi"));
        return loc.startsWith("en");
    }

    private static String t(String vi, String en) {
        return isEn() ? en : vi;
    }

    private static final Effect vChainLinkFx = new Effect(15f, e -> {
        Draw.z(Layer.effect + 0.05f);
        float fout = e.fout();
        if (e.data instanceof Vec2) {
            float tx = ((Vec2) e.data).x, ty = ((Vec2) e.data).y;
            Draw.color(PerfCore.color("#ff2222"), Color.white, e.fin());
            Lines.stroke(4f * fout);
            Lines.line(e.x, e.y, tx, ty);
            Draw.color(Color.white);
            Lines.stroke(1.5f * fout);
            Lines.line(e.x, e.y, tx, ty);
            Fill.circle(e.x, e.y, 5f * fout);
            Fill.circle(tx, ty, 5f * fout);
        }
        Draw.reset();
    });

    private static final Effect vChargeFx = new Effect(20f, e -> {
        Draw.z(Layer.effect + 0.01f);
        float fin = e.fin();
        float rot = e.rotation;
        float slashLength = e.data instanceof Number ? ((Number) e.data).floatValue() : 1100f;
        float endX = e.x + Angles.trnsx(rot, slashLength);
        float endY = e.y + Angles.trnsy(rot, slashLength);
        Draw.color(PerfCore.color("#ff4444"), Color.white, fin);
        Draw.alpha(fin * 0.5f);
        Lines.stroke(1.2f * fin);
        Lines.line(e.x, e.y, endX, endY);
        Draw.reset();
    });

    private static final Effect vSlashHitFx = new Effect(25f, e -> {
        Draw.z(Layer.effect + 0.02f);
        float fin = e.fin(), fout = e.fout(), rot = e.rotation;
        SlashData d = e.data instanceof SlashData ? (SlashData) e.data : null;
        float slashLength = (d != null && d.len != 0f) ? d.len : 1100f;
        float wMult = (d != null && d.widthMult != 0f) ? d.widthMult : 1f;
        float endX = e.x + Angles.trnsx(rot, slashLength * Math.min(1f, fin * 1.5f));
        float endY = e.y + Angles.trnsy(rot, slashLength * Math.min(1f, fin * 1.5f));

        Draw.color(PerfCore.color("#ff3333"), PerfCore.color("#ff6644"), fin);
        Draw.alpha(fout * 0.7f);
        Lines.stroke(24f * wMult * fout);
        Lines.line(e.x, e.y, endX, endY);

        Draw.color(Color.white);
        Draw.alpha(fout);
        Lines.stroke(4f * wMult * fout);
        Lines.line(e.x, e.y, endX, endY);
        Draw.reset();
    });

    private static final BulletType energyOrbBullet = new BasicBulletType(7f, 35f) {
        {
            lifetime = 60f;
            width = 14f;
            height = 14f;
            shrinkX = 0f;
            shrinkY = 0f;
        }

        @Override
        public void draw(Bullet b) {
            Draw.z(Layer.bullet);
            Draw.color(PerfCore.color("#ff3333"));
            Fill.circle(b.x, b.y, 8f);
            Draw.color(Color.white);
            Fill.circle(b.x, b.y, 3f);
            Draw.reset();
        }
    };

    private static void damageStraightSlash(mindustry.game.Team team, float startX, float startY, float rotation, float length, float damageAmount) {
        int steps = (int) Math.floor(length / 30f);
        float stepDist = length / steps;
        float hitRadius = 24f;
        for (int i = 0; i <= steps; i++) {
            float px = startX + Angles.trnsx(rotation, stepDist * i);
            float py = startY + Angles.trnsy(rotation, stepDist * i);
            Damage.damage(team, px, py, hitRadius, damageAmount, true, true);
        }
    }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        lazash = new LazashTurret("lazash");
    }

    public static class LazashTurret extends PowerTurret {
        public LazashTurret(String name) {
            super(name);
            configurable = true;
            health = 17000;
            range = RANGE;
            reload = 120f;
            targetAir = false;
            targetGround = false;
            shootType = new LaserBulletType(10f) {{
                length = RANGE;
                width = 18f;
                colors = new Color[]{Color.valueOf("#ff4444"), Color.valueOf("#ff8866"), Color.white};
            }};
            consumePower(12f);

            config(Integer.class, (LazashBuild tile, Integer value) -> {
                if (tile != null && value != null) {
                    int val = value;
                    if (val >= 20) tile.perkB = val - 20;
                    else if (val >= 10) tile.perkA = val - 10;
                }
            });
            buildType = LazashBuild::new;
        }

        public class LazashBuild extends PowerTurretBuild {
            int perkA, perkB;
            float chargeTimer, reloadTimer, laserTimer;
            boolean isCharging;
            int slashCount2B, frenzyShotsLeft;
            boolean isFrenzy2B;
            float frenzyTimer, targetScanTimer;
            boolean isChainFiring;
            int slaveTicks;
            float slaveTargetAngle;
            boolean slaveIsShooting;

            void setSlaveCommand(float angle, boolean shooting) {
                slaveTicks = 5;
                slaveTargetAngle = angle;
                slaveIsShooting = shooting;
            }

            @Override
            public float range() {
                return perkA == 2 ? RANGE * 1.12f : RANGE;
            }

            @Override
            protected void findTarget() {
                float r = range();
                Seq<TeamData> active = Vars.state.teams.present;

                if (perkA == 3) {
                    CoreBuild enemyCore = null;
                    float minDist = r;
                    for (int i = 0; i < active.size; i++) {
                        TeamData td = active.get(i);
                        if (td.team != team) {
                            CoreBuild core = td.core();
                            if (core != null && core.isValid() && within(core, minDist)) {
                                enemyCore = core;
                                minDist = dst(core);
                            }
                        }
                    }
                    if (enemyCore != null) {
                        target = enemyCore;
                        return;
                    }
                }

                Building bestTarget = null;
                double bestVal = -1;
                for (int i = 0; i < active.size; i++) {
                    TeamData td = active.get(i);
                    if (td.team != team && td.buildings != null) {
                        for (int j = 0; j < td.buildings.size; j++) {
                            Building build = td.buildings.get(j);
                            if (build != null && build.isValid() && within(build, r)) {
                                double score;
                                if (perkA == 1) score = 9999999d - build.health;
                                else if (perkA == 2) score = build.maxHealth;
                                else score = 999999d - dst(build);
                                if (score > bestVal) {
                                    bestVal = score;
                                    bestTarget = build;
                                }
                            }
                        }
                    }
                }
                target = bestTarget;
            }

            void executeSlash(float angleOffset) {
                float angle = rotation + angleOffset;
                float slashLen = range();
                float dmgMult = 1f;
                if (perkA == 1) dmgMult += 0.50f;
                if (perkA == 2) dmgMult += 0.08f;
                if (perkB == 1) dmgMult += 0.50f;
                if (perkB == 3) dmgMult += 2.00f;
                float widthMult = (perkB == 3) ? 2.5f : 1f;

                float spawnX = x + Angles.trnsx(angle, 13f);
                float spawnY = y + Angles.trnsy(angle, 13f);

                vSlashHitFx.at(spawnX, spawnY, angle, new SlashData(slashLen, widthMult));
                damageStraightSlash(team, spawnX, spawnY, angle, slashLen, 49f * dmgMult);

                if (perkB == 2 && !isFrenzy2B) {
                    slashCount2B++;
                    if (slashCount2B >= 5) {
                        slashCount2B = 0;
                        isFrenzy2B = true;
                        frenzyShotsLeft = 100;
                    }
                }
            }

            /** Pháo kề sát vách 4 hướng, cùng loại, cùng đội. */
            private boolean isAdjacentLink(Building b, float adjacentRadius) {
                if (b == null || b == this || b.block != block || b.team != team) return false;
                float dstX = Math.abs(x - b.x), dstY = Math.abs(y - b.y);
                return (dstX < 4f && dstY <= adjacentRadius) || (dstY < 4f && dstX <= adjacentRadius);
            }

            void syncChainCommand(float targetAngle, boolean shooting) {
                if (isChainFiring) return;
                isChainFiring = true;
                final float adjacentRadius = (block.size * 8) + 8f;

                Units.nearbyBuildings(x, y, adjacentRadius, b -> {
                    if (isAdjacentLink(b, adjacentRadius) && b instanceof LazashBuild) {
                        LazashBuild lb = (LazashBuild) b;
                        lb.setSlaveCommand(targetAngle, shooting);
                        if (shooting) vChainLinkFx.at(x, y, 0f, new Vec2(lb.x, lb.y));
                        lb.syncChainCommand(targetAngle, shooting);
                    }
                });

                isChainFiring = false;
            }

            @Override
            public void drawSelect() {
                super.drawSelect();
                final float adjacentRadius = (block.size * 8) + 8f;

                Units.nearbyBuildings(x, y, adjacentRadius, b -> {
                    if (isAdjacentLink(b, adjacentRadius)) {
                        Draw.z(Layer.power + 1);
                        Draw.color(PerfCore.color("#ff2222"));
                        Lines.stroke(2.5f);
                        Lines.line(x, y, b.x, b.y);
                        Draw.color(Color.white);
                        Lines.stroke(1f);
                        Lines.line(x, y, b.x, b.y);
                        Draw.color(PerfCore.color("#ff4444"));
                        Fill.circle(b.x, b.y, 3.5f);
                        Draw.reset();
                    }
                });
            }

            @Override
            public void updateTile() {
                super.updateTile();

                if (slaveTicks > 0) slaveTicks--;

                float speedMult = 1f;
                if (perkA == 3) speedMult *= 1.5f;
                if (perkB == 1) speedMult *= 1.428f;
                if (perkB == 3) speedMult *= 0.5f;

                if (reloadTimer > 0) reloadTimer -= Time.delta * speedMult;

                boolean isDirectControlled = isControlled() || logicControlled();
                boolean isSlaveControlled = slaveTicks > 0;
                boolean hasPower = power != null && power.status > 0.1f;

                float spawnX = x + Angles.trnsx(rotation, 13f);
                float spawnY = y + Angles.trnsy(rotation, 13f);

                if (isFrenzy2B) {
                    if (hasPower) {
                        frenzyTimer += Time.delta;
                        if (frenzyTimer >= 3f) {
                            frenzyTimer = 0f;
                            float targetAngle = rotation;
                            if (target != null) targetAngle = angleTo(target);

                            float spreadAngle = targetAngle + Mathf.range(8f);
                            float orbSpawnX = x + Angles.trnsx(spreadAngle, 13f);
                            float orbSpawnY = y + Angles.trnsy(spreadAngle, 13f);
                            energyOrbBullet.create(this, team, orbSpawnX, orbSpawnY, spreadAngle);

                            frenzyShotsLeft--;
                            if (frenzyShotsLeft <= 0) isFrenzy2B = false;
                        }
                    }
                    return;
                }

                if (isDirectControlled) {
                    slaveTicks = 0;
                    if (unit != null) {
                        float aimAngle = angleTo(unit.aimX(), unit.aimY());
                        rotation = aimAngle;
                        syncChainCommand(aimAngle, isShooting());
                    }
                } else if (isSlaveControlled) {
                    rotation = slaveTargetAngle;
                } else {
                    targetScanTimer += Time.delta;
                    if (targetScanTimer >= 15f) {
                        targetScanTimer = 0f;
                        findTarget();
                    }
                    if (hasPower && target != null && within(target, range())) {
                        turnToTarget(angleTo(target));
                    }
                }

                boolean canShoot = false;
                if (hasPower) {
                    if (isDirectControlled) canShoot = isShooting();
                    else if (isSlaveControlled) canShoot = slaveIsShooting;
                    else canShoot = target != null && within(target, range());
                }

                if (canShoot) {
                    laserTimer += Time.delta;
                    if (laserTimer >= 12f) {
                        laserTimer = 0f;
                        if (shootType instanceof LaserBulletType) ((LaserBulletType) shootType).length = range();
                        shootType.create(this, team, spawnX, spawnY, rotation);
                    }
                } else {
                    laserTimer = 0f;
                }

                if (canShoot && reloadTimer <= 0 && !isCharging) {
                    isCharging = true;
                    chargeTimer = 25f;
                }

                if (isCharging) {
                    chargeTimer -= Time.delta;
                    if (Mathf.chance(0.2)) vChargeFx.at(spawnX, spawnY, rotation, (Object) Float.valueOf(range()));
                    if (chargeTimer <= 0) {
                        isCharging = false;
                        reloadTimer = 120f;
                        executeSlash(0f);
                    }
                }
            }

            private String reqText() {
                CoreBuild core = team.core();
                if (core == null) return t("[red]Không tìm thấy Lõi Đội![]", "[red]Team Core not found![]");

                int cCop = core.items.get(Items.copper);
                int cLea = core.items.get(Items.lead);
                int cSil = core.items.get(Items.silicon);
                int cTit = core.items.get(Items.titanium);
                int cTho = core.items.get(Items.thorium);
                int cGra = core.items.get(Items.graphite);

                String colCop = cCop >= REQ_A[0] ? "[green]" : "[red]";
                String colLea = cLea >= REQ_A[1] ? "[green]" : "[red]";
                String colSil = cSil >= REQ_A[2] ? "[green]" : "[red]";
                String colTit = cTit >= REQ_B[0] ? "[green]" : "[red]";
                String colTho = cTho >= REQ_B[1] ? "[green]" : "[red]";
                String colGra = cGra >= REQ_B[2] ? "[green]" : "[red]";

                return t(
                    "[gold]YÊU CẦU TÀI NGUYÊN LÕI:[]\n" +
                    "[yellow]★ ROLL PHÚC LỢI A:[] Đồng: " + colCop + cCop + "[]/2000 | Chì: " + colLea + cLea + "[]/2000 | Silicon: " + colSil + cSil + "[]/2000\n" +
                    "[cyan]★ ROLL PHÚC LỢI B:[] Titan: " + colTit + cTit + "[]/1000 | Thorium: " + colTho + cTho + "[]/1000 | Than chì: " + colGra + cGra + "[]/1000\n",

                    "[gold]CORE RESOURCE REQUIREMENTS:[]\n" +
                    "[yellow]★ ROLL PERK A:[] Copper: " + colCop + cCop + "[]/2000 | Lead: " + colLea + cLea + "[]/2000 | Silicon: " + colSil + cSil + "[]/2000\n" +
                    "[cyan]★ ROLL PERK B:[] Titanium: " + colTit + cTit + "[]/1000 | Thorium: " + colTho + cTho + "[]/1000 | Graphite: " + colGra + cGra + "[]/1000\n"
                );
            }

            @Override
            public void buildConfiguration(Table table) {
                table.clear();
                table.row();
                table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog).size(50f, 40f).tooltip(t("Trung tâm nâng cấp pháo Lazash", "Lazash Turret Upgrade Center"));
                table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog).size(50f, 40f).tooltip(t("Xem thông số pháo Lazash", "View Lazash Turret Stats"));
            }

            private void openUpgradeDialog() {
                BaseDialog dialog = new BaseDialog(t("Trung tâm nâng cấp pháo Lazash", "Lazash Turret Upgrade Center"));

                Cell<Label> reqCell = dialog.cont.add(reqText());
                reqCell.width(380f).get().setWrap(true);
                reqCell.get().setAlignment(Align.left);
                dialog.cont.row();
                dialog.cont.add().height(10f).row();

                Table mainTable = new Table();

                Table boxA = new Table();
                boxA.background(Styles.black6);
                boxA.margin(12f);
                boxA.add(t("[yellow]★ ROLL PHÚC LỢI A (NGẪU NHIÊN) ★[]", "[yellow]★ ROLL PERK A (RANDOM) ★[]")).row();

                if (perkA == 0) {
                    Cell<Label> txtADesc = boxA.add(t(
                    "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi A (Tỉ lệ 50% mỗi Option):\n" +
                    " • [green]Phúc lợi 1A:[] Ưu tiên bắn các công trình máu giấy, tăng 50% dmg gốc.\n" +
                    " • [green]Phúc lợi 2A:[] Tăng 12% phạm vi bắn, 8% dmg gốc và dmg phụ, ưu tiên bắn các công trình có máu trâu.\n" +
                    " • [green]Phúc lợi 3A:[] Ưu tiên bắn base địch, tăng tốc độ bắn.",

                    "Activate upgrade protocol to randomly get 1 of 3 Perk A choices (50% chance per Option):\n" +
                    " • [green]Perk 1A:[] Prioritizes low-HP structures, +50% base dmg.\n" +
                    " • [green]Perk 2A:[] Range +12%, +8% base & sub Dmg, prioritizes high-HP structures.\n" +
                    " • [green]Perk 3A:[] Prioritizes enemy core/base, increases fire rate."
                ));
                    txtADesc.width(340f).get().setWrap(true);
                    txtADesc.get().setAlignment(Align.left);
                    boxA.row();

                    boxA.button(t("[yellow]QUAY PHÚC LỢI A (2K Đồng/Chì/Silicon)[]", "[yellow]ROLL PERK A (2K Cop/Lead/Sil)[]"), () -> {
                        CoreBuild core = team.core();
                        if (core != null && core.items.get(Items.copper) >= REQ_A[0] && core.items.get(Items.lead) >= REQ_A[1] && core.items.get(Items.silicon) >= REQ_A[2]) {
                            NexNet.take(core, Items.copper, REQ_A[0]);
                            NexNet.take(core, Items.lead, REQ_A[1]);
                            NexNet.take(core, Items.silicon, REQ_A[2]);

                            int[] options = new int[3];
                            int n = 0;
                            if (Mathf.chance(0.50)) options[n++] = 1;
                            if (Mathf.chance(0.50)) options[n++] = 2;
                            if (Mathf.chance(0.50)) options[n++] = 3;

                            int res;
                            if (n > 0) res = options[(int) Math.floor(Mathf.random(0f, n - 0.01f))];
                            else res = (int) Math.floor(Mathf.random(1f, 3.99f));

                            perkA = res;
                            configure(Integer.valueOf(10 + res));

                            NexNet.fx(Fx.upgradeCore, x, y);
                            NexNet.shake(4f, 4f, x, y);
                            Vars.ui.showInfo(t("[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[yellow]PHÚC LỢI " + res + "A[]", "[gold]YOU ROLLED:[]\n[yellow]PERK " + res + "A[]"));
                            dialog.hide();
                            deselect();
                        } else {
                            Vars.ui.showInfo(t("[red]Không đủ tài nguyên roll Phúc lợi A![]", "[red]Not enough resources to roll Perk A![]"));
                        }
                    }).size(280f, 40f);
                } else {
                    String txtA = "";
                    if (perkA == 1) txtA = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1A\n• Ưu tiên bắn công trình máu giấy\n• Sát thương gốc +50%[]", "[green]✔ ACTIVATED: PERK 1A\n• Targets low-HP structures\n• Base Damage +50%[]");
                    if (perkA == 2) txtA = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2A\n• Ưu tiên bắn công trình máu trâu\n• Tầm bắn +12%\n• Sát thương gốc & phụ +8%[]", "[green]✔ ACTIVATED: PERK 2A\n• Targets high-HP structures\n• Range +12%\n• Base & Sub Damage +8%[]");
                    if (perkA == 3) txtA = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3A\n• Ưu tiên bắn Base/Lõi địch\n• Tăng 50% tốc độ bắn[]", "[green]✔ ACTIVATED: PERK 3A\n• Targets enemy Core/Base\n• Fire rate +50%[]");

                    Cell<Label> txtACell = boxA.add(txtA);
                    txtACell.width(340f).get().setWrap(true);
                    txtACell.get().setAlignment(Align.left);
                }

                mainTable.add(boxA).width(360f).row();
                mainTable.add().height(12f).row();

                Table boxB = new Table();
                boxB.background(Styles.black6);
                boxB.margin(12f);
                boxB.add(t("[cyan]★ ROLL PHÚC LỢI B (NGẪU NHIÊN) ★[]", "[cyan]★ ROLL PERK B (RANDOM) ★[]")).row();

                if (perkB == 0) {
                    Cell<Label> txtBDesc = boxB.add(t(
                    "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi B:\n" +
                    " • [cyan]Phúc lợi 1B:[] Giảm 30% thời gian bắn vSlashHitFx, +50% Sát thương.\n" +
                    " • [cyan]Phúc lợi 2B:[] Bắn đủ 5 tia vSlashHitFx -> Tự động xả loạn 100 viên cầu năng lượng (độ lệch 8°).\n" +
                    " • [cyan]Phúc lợi 3B:[] Kích thước diện rộng vSlashHitFx +150%, +200% Sát thương, Giảm 50% tốc độ bắn.",

                    "Activate upgrade protocol to randomly get 1 of 3 Perk B choices:\n" +
                    " • [cyan]Perk 1B:[] Reduces slash attack delay by 30%, +50% Damage.\n" +
                    " • [cyan]Perk 2B:[] After 5 slashes -> Automatically unleashes 100 energy orbs (8° spread).\n" +
                    " • [cyan]Perk 3B:[] Slash width +150%, +200% Damage, Fire Rate -50%."
                ));
                    txtBDesc.width(340f).get().setWrap(true);
                    txtBDesc.get().setAlignment(Align.left);
                    boxB.row();

                    boxB.button(t("[cyan]QUAY PHÚC LỢI B (1K Titan/Thorium/Graphite)[]", "[cyan]ROLL PERK B (1K Tit/Tho/Graph)[]"), () -> {
                        CoreBuild core = team.core();
                        if (core != null && core.items.get(Items.titanium) >= REQ_B[0] && core.items.get(Items.thorium) >= REQ_B[1] && core.items.get(Items.graphite) >= REQ_B[2]) {
                            NexNet.take(core, Items.titanium, REQ_B[0]);
                            NexNet.take(core, Items.thorium, REQ_B[1]);
                            NexNet.take(core, Items.graphite, REQ_B[2]);

                            int res = (int) Math.floor(Mathf.random(1f, 3.99f));
                            perkB = res;
                            configure(Integer.valueOf(20 + res));

                            NexNet.fx(Fx.upgradeCore, x, y);
                            NexNet.shake(4f, 4f, x, y);
                            Vars.ui.showInfo(t("[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[cyan]PHÚC LỢI " + res + "B[]", "[gold]YOU ROLLED:[]\n[cyan]PERK " + res + "B[]"));
                            dialog.hide();
                            deselect();
                        } else {
                            Vars.ui.showInfo(t("[red]Không đủ tài nguyên roll Phúc lợi B![]", "[red]Not enough resources to roll Perk B![]"));
                        }
                    }).size(280f, 40f);
                } else {
                    String txtB = "";
                    if (perkB == 1) txtB = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1B\n• Giảm 30% thời gian bắn nhát chém\n• Sát thương vSlashHitFx +50%[]", "[green]✔ ACTIVATED: PERK 1B\n• Slash delay reduced by 30%\n• Slash Damage +50%[]");
                    if (perkB == 2) txtB = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2B\n• Bắn đủ 5 nhát chém -> Bắn loạn 100 viên cầu năng lượng[]", "[green]✔ ACTIVATED: PERK 2B\n• After 5 slashes -> Fires 100 energy orbs burst[]");
                    if (perkB == 3) txtB = t("[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3B\n• Kích thước diện rộng vSlashHitFx +150%\n• Sát thương vSlashHitFx +200%\n• Tốc độ bắn -50%[]", "[green]✔ ACTIVATED: PERK 3B\n• Slash AoE size +150%\n• Slash Damage +200%\n• Fire Rate -50%[]");

                    Cell<Label> txtBCell = boxB.add(txtB);
                    txtBCell.width(340f).get().setWrap(true);
                    txtBCell.get().setAlignment(Align.left);
                }

                mainTable.add(boxB).width(360f);

                ScrollPane scroll = new ScrollPane(mainTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(420f);
                dialog.addCloseButton();
                dialog.show();
            }

            private void openInfoDialog() {
                String title = t(" Thông số pháo Lazash ", " Lazash Turret Stats ");
                String descStr = t(
                "[gold]⚡ THÔNG SỐ CƠ BẢN PHÁO LAZASH ⚡[]\n" +
                "• Máu: 17,000 | Tầm bắn mặc định: 105px\n" +
                "• Mục tiêu: Chỉ bắn Công Trình phe địch\n" +
                "• Sát thương vSlashHitFx: 49.0 | Sát thương Laser Vanilla: 10.0 (0.2s/lần)\n" +
                "• [cyan]Cơ chế liên kết:[] Xây các pháo kề sát vách 4 hướng. Khi player nhảy vào điều khiển 1 pháo, toàn bộ pháo liên kết sẽ ngưng tự bắn, tự động xoay và chỉ xả đạn đồng loạt khi player bấm nút bắn!",

                "[gold]⚡ LAZASH TURRET BASIC STATS ⚡[]\n" +
                "• Health: 17,000 | Default Range: 105px\n" +
                "• Target: Enemy Structures Only\n" +
                "• Slash Damage: 49.0 | Vanilla Laser Damage: 10.0 (0.2s tick)\n" +
                "• [cyan]Link Mechanism:[] Place turrets adjacent in 4 directions. When a player manually controls 1 turret, all linked turrets stop auto-firing, rotate automatically, and sync fire when player shoots!"
            );

                BaseDialog dialog = new BaseDialog(title);
                Table infoTable = new Table();
                Cell<Label> cell = infoTable.add(descStr).width(360f);
                cell.get().setWrap(true);
                cell.get().setAlignment(Align.left);

                ScrollPane scroll = new ScrollPane(infoTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton();
                dialog.show();
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.b(perkA);
                write.b(perkB);
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                perkA = read.b();
                perkB = read.b();
            }
        }
    }
}
