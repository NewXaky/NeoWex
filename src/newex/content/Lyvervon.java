package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.IntFloatMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ lyvervon-system.js. Cột thu lôi PowerTurret, 3 cấp (MK1/MK2/MK2B), vùng mù 80px, hồ quang xanh nhiều tia.
 * Block do Java tạo (lyvervon.hjson KHÔNG có `type:` cấp cao nhất); hjson nạp sau nên ghi đè size/health/range/consumes.
 */
public final class Lyvervon {
    private Lyvervon() {}

    static final Color greenColor = Color.valueOf("7fd4ff");
    static final Color lyvervonColor = Color.valueOf("#a488ff");
    static final float deadZone = 80f;

    /** Giá nâng cấp; tên trường giữ như JS (reqMK2.copper...) để ghép nguyên văn chuỗi. */
    static final class Req {
        final int copper, silicon, lead;
        Req(int copper, int silicon, int lead) { this.copper = copper; this.silicon = silicon; this.lead = lead; }
    }
    static final Req reqMK2 = new Req(120, 250, 0), reqMK3 = new Req(200, 0, 290);

    public static LyvervonTurret lyvervon;
    static final IntFloatMap turretChargeMap = new IntFloatMap();

    static final BulletType lyvervonBulletSystem = new BulletType() {
        @Override public void init(Bullet b) { if (b != null) b.remove(); }
        @Override public void draw(Bullet b) {}
    };
    static {
        lyvervonBulletSystem.speed = 0f;
        lyvervonBulletSystem.lifetime = 1f;
        lyvervonBulletSystem.collides = false;
    }

    static final Effect arcmotgreenEffect = new Effect(14f, e -> {
        if (!(e.data instanceof Seq)) return;
        @SuppressWarnings("unchecked") Seq<Vec2> points = (Seq<Vec2>) e.data;
        float thickness = e.rotation > 0f ? e.rotation : 2.8f;
        Draw.color(greenColor, Color.white, e.fin());
        Lines.stroke(thickness * e.fout());
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i), b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
    });

    static final Effect sonicShockwaveEffect = new Effect(45f, e -> {
        Draw.color(greenColor);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 32f);
        Draw.alpha(e.fout() * 0.4f);
        Fill.circle(e.x, e.y, e.fin() * 20f);
        Draw.reset();
    });

    static final Effect mk3ExplosionFX = new Effect(30f, e -> {
        Draw.color(lyvervonColor, Color.white, e.fin());
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 45f);
        // Giữ nguyên lỗi JS: toạ độ y vẽ là `y + y` (bài học 43).
        Angles.randLenVectors(e.id, 20, 45f * e.fin(), (px, py) -> Fill.circle(e.x + px, py + py, 3.5f * e.fout()));
        Draw.reset();
    });

    /** JS còn thử Vars.ui.getLanguage (không tồn tại ở v157 → luôn false), nên chỉ cần kiểm tra locale. */
    static boolean isEnglish() {
        String loc = String.valueOf(Core.settings.get("locale", "default"));
        return loc.startsWith("en");
    }

    static void dashCircle(float x, float y, float radius, Color color) {
        Draw.color(color);
        int segments = 24;
        for (int i = 0; i < segments; i += 2) {
            float a1 = (float) i / segments * 360f, a2 = (float) (i + 1) / segments * 360f;
            Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius), x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
        Draw.reset();
    }

    static void drawPolyHex(float x, float y, float radius, float stroke, float rotation, Color color) {
        Draw.color(color);
        Lines.stroke(stroke);
        int sides = 6;
        for (int i = 0; i < sides; i++) {
            float a1 = rotation + (i * 360f / sides), a2 = rotation + ((i + 1) * 360f / sides);
            Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius), x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
        Draw.reset();
    }

    static void creategreenStandard(float x1, float y1, float x2, float y2, float thickness) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(4, (int) Math.floor(dst / 7f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);
        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(x1, x2, t), py = Mathf.lerp(y1, y2, t);
            float arc = Mathf.sin(t * (float) Math.PI) * 18f;
            float noise = Mathf.range(13f) * (1f - t);
            Tmp.v1.trns(angle + 90f, arc + noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));
        arcmotgreenEffect.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, points);
    }

    static void creategreenChaos(float x1, float y1, float x2, float y2, float thickness) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(5, (int) Math.floor(dst / 5f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);
        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(x1, x2, t), py = Mathf.lerp(y1, y2, t);
            float maxCurve = 22f + (float) Math.random() * 12f;
            float jitter = 16f + (float) Math.random() * 10f;
            float arc = Mathf.sin(t * (float) Math.PI) * (Math.random() > 0.5 ? maxCurve : -maxCurve);
            float noise = Mathf.range(jitter);
            Tmp.v1.trns(angle + 90f, arc + noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));
        arcmotgreenEffect.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, points);
    }

    static Unit findSubTarget(float x, float y, float range, Seq<Unit> exclude, mindustry.game.Team team) {
        final Unit[] best = {null};
        final float[] maxHp = {-1f};
        Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, u -> {
            if (!u.dead && !u.type.flying && Mathf.dst(x, y, u.x, u.y) <= range && !exclude.contains(u)) {
                if (u.health > maxHp[0]) { maxHp[0] = u.health; best[0] = u; }
            }
        });
        return best[0];
    }

    static Seq<Unit> findHighHpTargetsForMK2(float x, float y, float range, Seq<Unit> exclude, mindustry.game.Team team, int maxCount) {
        final Seq<Unit> list = new Seq<>();
        Units.nearbyEnemies(team, x - range, y - range, range * 2f, range * 2f, u -> {
            if (!u.dead && !u.type.flying && Mathf.dst(x, y, u.x, u.y) <= range && !exclude.contains(u)) list.add(u);
        });
        list.sort(u -> -u.health);
        Seq<Unit> result = new Seq<>();
        for (int i = 0; i < Math.min(maxCount, list.size); i++) result.add(list.get(i));
        return result;
    }

    static boolean isDead(Posc t) { return t instanceof Healthc && ((Healthc) t).dead(); }

    static void damageTarget(Posc t, float amount) { if (t instanceof Healthc) ((Healthc) t).damage(amount); }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        Log.info("LYVERVON SYSTEM CORE - BREATHING HALO ENERGY UPDATE");
        lyvervon = new LyvervonTurret("lyvervon");
    }

    public static class LyvervonTurret extends PowerTurret {
        public LyvervonTurret(String name) {
            super(name);
            health = 2475;
            size = 4;
            targetAir = false;
            targetGround = true;
            range = 260f;
            reload = 6f;
            shootType = lyvervonBulletSystem;
            consumePower(100f / 60f);
            configurable = true;
            config(Integer.class, (LyvervonBuild tile, Integer value) -> tile.setTier(value));
            buildType = LyvervonBuild::new;
        }

        @Override
        public void drawPlace(int x, int y, int rotation, boolean valid) {
            super.drawPlace(x, y, rotation, valid);
            Draw.color(Pal.remove);
            Lines.stroke(2f);
            dashCircle(x * Vars.tilesize, y * Vars.tilesize, deadZone, Pal.remove);
            Draw.reset();
        }

        public class LyvervonBuild extends PowerTurretBuild {
            int lyvervonTier = 1, mk3ChargePoints;
            float damageBonus, mk3Timer, zoomAnimation;

            @Override
            public void placed() {
                super.placed();
                setTier(getTier());
            }

            @Override
            public boolean canControl() { return false; }

            /** Lưu ý (giữ nguyên JS): đổi trường của block dùng chung cho MỌI pháo Lyvervon; JS cũng gán consPower.usage nhưng
             *  trường đó là final nên Rhino bỏ qua → điện tiêu thụ luôn theo hjson (bài học 48). */
            void setTier(int val) {
                lyvervonTier = val;
                PowerTurret b = (PowerTurret) block;
                if (val == 1) {
                    health = 2475f; b.reload = 6f; b.range = 260f;
                } else if (val == 2) {
                    health = 4295f; b.reload = 6f; b.range = 303.3f;
                } else if (val == 3) {
                    health = 5241f; b.reload = 6f; b.range = 329.3f;
                }
            }

            int getTier() { return lyvervonTier; }

            @Override
            public Object config() { return Integer.valueOf(getTier()); }

            @Override
            protected void shoot(BulletType type) {
                int tier = getTier();
                final Posc tg = target;
                boolean hasTarget = tg != null && !isDead(tg);

                float endX = x, endY = y;
                if (hasTarget) {
                    endX = tg.getX(); endY = tg.getY();
                } else {
                    float r = ((PowerTurret) block).range;
                    endX = x + Angles.trnsx(rotation, r);
                    endY = y + Angles.trnsy(rotation, r);
                }

                if (tier == 1) {
                    if (hasTarget) damageTarget(tg, 510f);
                    creategreenStandard(x, y, endX, endY, 2.8f);
                    Fx.hitLancer.at(endX, endY, greenColor);
                    Effect.shake(2f, 2f, endX, endY);
                } else if (tier == 2) {
                    float baseDamage = 408f;
                    final float finalDamage = baseDamage * (1f + damageBonus / 100f);
                    float thicknessProgress = damageBonus / 1499f;
                    float greenThickness = 2.8f * (1f + (thicknessProgress * 0.5f));
                    float rangeSub = 180f;

                    if (hasTarget) damageTarget(tg, finalDamage);
                    creategreenStandard(x, y, endX, endY, greenThickness);

                    if (hasTarget) {
                        final int turretId = id;
                        final float currentSubBonus = turretChargeMap.get(turretId, 0f);
                        final float subDamage = finalDamage * 0.7f;
                        float subThickness = 2.5f + (currentSubBonus / 200f);

                        Seq<Unit> excludeList = new Seq<>();
                        if (tg instanceof Unit) excludeList.add((Unit) tg);

                        // JS: applySubCharge — luôn tính từ currentSubBonus đã chốt đầu lượt bắn (không cộng dồn trong lượt).
                        arc.func.Cons<Unit> applySubCharge = targetUnit -> {
                            targetUnit.damage(subDamage);
                            float charge = currentSubBonus + 40f;
                            if (charge >= 100f) {
                                turretChargeMap.put(turretId, 0f);
                                sonicShockwaveEffect.at(targetUnit.x, targetUnit.y);
                            } else {
                                turretChargeMap.put(turretId, charge);
                            }
                        };

                        // Chuỗi B → C → D → E (JS lồng 4 tầng if); extra chỉ chạy khi tìm đủ 4 mắt xích.
                        float px = tg.getX(), py = tg.getY();
                        Unit last = null;
                        int chain = 0;
                        for (int step = 0; step < 4; step++) {
                            Unit next = findSubTarget(px, py, rangeSub, excludeList, team);
                            if (next == null) break;
                            excludeList.add(next);
                            applySubCharge.get(next);
                            creategreenStandard(px, py, next.x, next.y, subThickness);
                            px = next.x; py = next.y; last = next; chain++;
                        }
                        if (chain == 4) {
                            Seq<Unit> extraTargets = findHighHpTargetsForMK2(last.x, last.y, rangeSub, excludeList, team, 5);
                            for (int i = 0; i < extraTargets.size; i++) {
                                Unit extraTarget = extraTargets.get(i);
                                applySubCharge.get(extraTarget);
                                creategreenStandard(last.x, last.y, extraTarget.x, extraTarget.y, subThickness * 0.9f);
                                Fx.hitLancer.at(extraTarget.x, extraTarget.y, greenColor);
                            }
                        }
                    }
                    Fx.hitLancer.at(endX, endY, greenColor);
                    float shakeIntensity = 2f + (finalDamage / 500f);
                    Effect.shake(shakeIntensity, shakeIntensity, endX, endY);
                } else if (tier == 3) {
                    float thicknessProgressMK3 = damageBonus / 1499f;
                    float greenThicknessMK3 = 3.5f * (1f + (thicknessProgressMK3 * 0.5f));
                    if (hasTarget) damageTarget(tg, 612f);
                    creategreenStandard(x, y, endX, endY, greenThicknessMK3);
                    Fx.hitLancer.at(endX, endY, lyvervonColor);
                    Effect.shake(3f, 3f, endX, endY);
                }
            }

            private String reqText(boolean en) {
                CoreBuild core = team.core();
                if (core == null) return en ? "[red]Team Core Not Found![]" : "[red]Không tìm thấy Lõi Đội![]";
                int currentCopper = core.items.get(Items.copper);
                int currentSilicon = core.items.get(Items.silicon);
                int currentLead = core.items.get(Items.lead);

                String copColor1 = currentCopper >= reqMK2.copper ? "[green]" : "[red]";
                String silColor1 = currentSilicon >= reqMK2.silicon ? "[green]" : "[red]";
                String copColor2 = currentCopper >= reqMK3.copper ? "[green]" : "[red]";
                String leaColor2 = currentLead >= reqMK3.lead ? "[green]" : "[red]";

                                    if (en) {
                        return "[yellow]CORE RESOURCE REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Branch:[]\n" +
                               " • Copper: " + copColor1 + currentCopper + "[] / " + reqMK2.copper + "\n" +
                               " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                               "[purple]MK2B Branch:[]\n" +
                               " • Copper: " + copColor2 + currentCopper + "[] / " + reqMK3.copper + "\n" +
                               " • Lead: " + leaColor2 + currentLead + "[] / " + reqMK3.lead;
                    } else {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Đồng: " + copColor1 + currentCopper + "[] / " + reqMK2.copper + "\n" +
                               " • Silicon: " + silColor1 + currentSilicon + "[] / " + reqMK2.silicon + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Đồng: " + copColor2 + currentCopper + "[] / " + reqMK3.copper + "\n" +
                               " • Chì: " + leaColor2 + currentLead + "[] / " + reqMK3.lead;
                    }
            }

            private void openUpgradeDialog(boolean en) {
                final BaseDialog dialog = new BaseDialog(en ? "Lyvervon Upgrade Center" : "Trung tâm nâng cấp pháo Lyvervon");

                Cell<Label> reqCell = dialog.cont.add(reqText(en));
                reqCell.width(360f).get().setWrap(true);
                reqCell.get().setAlignment(Align.left);
                dialog.cont.row();
                dialog.cont.add().height(10f).row();

                Table branchesTable = new Table();

                Table b1 = new Table();
                b1.background(Styles.black6);
                b1.margin(12f);
                b1.add("[cyan]===(MK2)===[]").row();
                String b1Text = en ?
                    "[white]• Health: [green]+73.5%[] (4,295 HP)\n" +
                    "• Range: [green]+16.6%[] (303.3 px)\n" +
                    "• Power Usage: [orange]+1100%[] (1,200/s)\n" +
                    "• [orange]Chain green Discharge:[] Continuous firing increases damage up to +1499%\n• Green chains across up to 5 targets\n• Triggering Sonic Shockwaves at max charge" :
                    "[white]• Máu cấu trúc: [green]+73.5%[] (4,295 HP)\n" +
                    "• Tầm bắn: [green]+16.6%[] (303.3 px)\n" +
                    "• Tiêu thụ điện: [orange]+1100%[] (1,200/s)\n" +
                    "• [orange]Chuỗi Điện Truyền Dẫn:[] Bắn liên tục tăng tiến sát thương lên tới +1499%\n• Sét tự động nảy qua 5 mục tiêu và kích nổ xung Sonic khi tích đủ điểm";
                Cell<Label> b1D = b1.add(b1Text);
                b1D.width(340f).get().setWrap(true);
                b1D.get().setAlignment(Align.left);
                b1.row();
                b1.button(en ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.copper) >= reqMK2.copper && core.items.get(Items.silicon) >= reqMK2.silicon) {
                        NexNet.take(core, Items.copper, reqMK2.copper);
                        NexNet.take(core, Items.silicon, reqMK2.silicon);
                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(5f, 5f, x, y);
                        configure(Integer.valueOf(2));
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(en ? "[red]Not enough resources for MK2![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
                    }
                }).size(180f, 38f);

                Table b2 = new Table();
                b2.background(Styles.black6);
                b2.margin(12f);
                b2.add("[purple]===(MK2B)===[]").row();
                String b2Text = en ?
                    "[white]• Health: [green]+111.7%[] (5,241 HP)\n" +
                    "• Range: [green]+26.6%[] (329.3 px)\n" +
                    "• Power Usage: [orange]+750%[] (850/s)\n" +
                    "• [orange]Dual Chaos Explosion:[] Charges 5 pts/sec while firing\n• Reaching max charge triggers simultaneous explosions at both turret and target\n• Releasing 13 Chaos green bolts" :
                    "[white]• Máu cấu trúc: [green]+111.7%[] (5,241 HP)\n" +
                    "• Tầm bắn: [green]+26.6%[] (329.3 px)\n" +
                    "• Tiêu thụ điện: [orange]+750%[] (850/s)\n" +
                    "• [orange]Kích Nổ Kép Chaos:[] Bắn duy trì nạp 5 điểm/giây\n• Đạt mốc kích nổ đồng thời tại tâm pháo và mục tiêu\n• Tung 13 luồng sét Chaos diện rộng";
                Cell<Label> b2D = b2.add(b2Text);
                b2D.width(340f).get().setWrap(true);
                b2D.get().setAlignment(Align.left);
                b2.row();
                b2.button(en ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.copper) >= reqMK3.copper && core.items.get(Items.lead) >= reqMK3.lead) {
                        NexNet.take(core, Items.copper, reqMK3.copper);
                        NexNet.take(core, Items.lead, reqMK3.lead);
                        NexNet.fx(Fx.bigShockwave, x, y);
                        NexNet.shake(6f, 6f, x, y);
                        configure(Integer.valueOf(3));
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(en ? "[red]Not enough resources for MK2B![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
                    }
                }).size(180f, 38f);

                branchesTable.add(b1).width(340f);
                branchesTable.row();
                branchesTable.add().height(12f).row();
                branchesTable.add(b2).width(340f);

                ScrollPane scroll = new ScrollPane(branchesTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton();
                dialog.show();
            }

            private void openInfoDialog(boolean en) {
                String title = en ? "📊 LYVERVON STATS: " : "📊 THÔNG SỐ PHÁO LYVERVON: ";
                String descStr = "";
                int currentTier = getTier();

                            if (currentTier == 1) {
                title += en ? "[yellow]Base Config (MK1)[]" : "[yellow]Cấu hình gốc (MK1)[]";
                descStr = en ?
                          "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]2,475[]\n" +
                          "[gray]📐 Block Size:[] [white]4x4[]\n" +
                          "[lightgray]Effective Range:[] [orange]260 px[]\n" +
                          "[lightgray]Base Beam Damage:[] [purple]510 DMG / pulse[]\n" +
                          "[green] Fire Rate:[] [white]0.1s / shot[]\n" +
                          "[green] Power Usage:[] [white]100 / sec[]\n" +
                          "[scarlet] Deadzone Radius:[] [white]80 px[]\n\n" +
                          "[sky]⚡ SYSTEM MECHANIC:[]\n" +
                          "• [lightgray]Targeting:[] Only locks onto [lightgray]Ground[] targets outside the deadzone.\n" +
                          "• [lightgray]Energy Core:[] Internal battery holds up to 5,000 units." :
                          "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]2,475[]\n" +
                          "[gray]📐 Kích thước khối:[] [white]4x4[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]260 pixel[]\n" +
                          "[lightgray]Sát thương gốc:[] [purple]510 DMG / xung[]\n" +
                          "[green] Tốc độ xả đạn:[] [white]0.1s / phát[]\n" +
                          "[green] Tiêu thụ điện:[] [white]100 / giây[]\n" +
                          "[scarlet] Vùng mù (Deadzone):[] [white]80 pixel[]\n\n" +
                          "[sky]⚡ CƠ CHẾ HOẠT ĐỘNG:[]\n" +
                          "• [lightgray]Khóa mục tiêu:[] Chỉ tấn công kẻ địch [lightgray]Mặt Đất[] nằm ngoài vùng mù.\n" +
                          "• [lightgray]Lõi năng lượng:[] Trữ lượng pin nội bộ tối đa 5,000 đơn vị.";
            }
            else if (currentTier == 2) {
                title += en ? "[cyan]STANDARD CONFIG (MK2)[]" : "[cyan]CẤU HÌNH TIÊU CHUẨN (MK2)[]";
                descStr = en ?
                          "[cyan]⚡ BASE STATS (MK2) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]4,295 [lime](+73.5%)[]\n" +
                          "[gray]📐 Block Size:[] [white]4x4[]\n" +
                          "[lightgray]Effective Range:[] [orange]303.3 px [lime](+16.6%)[]\n" +
                          "[lightgray]Base Beam Damage:[] [purple]408 DMG / pulse[]\n" +
                          "[green] Power Usage:[] [orange]1,200 / sec [red](+1100%)[]\n" +
                          "[green] Max Charge Boost:[] [yellow]Up to +1499% DMG[]\n\n" +
                          "[sky]⚡ CHAIN green MECHANIC:[]\n" +
                          "• [lightgray]Damage Ramp-up:[] Continuous firing boosts damage up to [yellow]+1499%[] and expands beam size by [orange]+150%[].\n" +
                          "• [lightgray]Multi-Chain:[] green chains sequentially through 5 targets within [lightgray]180 px[].\n" +
                          "• [lightgray]Sonic Finish:[] Chain links deal 70% DMG. Reaching 100 pts triggers a sonic shockwave launching 5 sub-bolts at high HP targets." :
                          "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]4,295 [lime](+73.5%)[]\n" +
                          "[gray]📐 Kích thước khối:[] [white]4x4[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]303.3 pixel [lime](+16.6%)[]\n" +
                          "[lightgray]Sát thương gốc:[] [purple]408 DMG / xung[]\n" +
                          "[green] Tiêu thụ điện:[] [orange]1,200 / giây [red](+1100%)[]\n" +
                          "[green] Gia tốc tích tụ:[] [yellow]Tối đa +1499% Sát thương[]\n\n" +
                          "[sky]⚡ CƠ CHẾ CHUỖI SÉT TRUYỀN DẪN:[]\n" +
                          "• [lightgray]Tăng tiến hỏa lực:[] Duy trì bắn liên tục tăng hỏa lực lên đến [yellow]+1499%[] và phình to tia sét [orange]+150%[].\n" +
                          "• [lightgray]Nảy chuỗi:[] Tự động nối mạch qua 5 mục tiêu liên tiếp trong bán kính [lightgray]180 pixel[].\n" +
                          "• [lightgray]Xung Sonic:[] Tia phụ gây 70% DMG. Đạt 100 điểm kích nổ sóng chấn phóng thêm 5 tia phụ ghim vào kẻ địch máu cao.";
            }
            else if (currentTier == 3) {
                title += en ? "[purple]CHAOS EXPLOSION VARIANT (MK2B)[]" : "[purple]BIẾN THỂ KÍCH NỔ CHAOS (MK2B)[]";
                descStr = en ?
                          "[purple]⚡ BASE STATS (MK2B) ⚡[]\n" +
                          "[lightgray]Turret HP:[] [green]5,241 [lime](+111.7%)[]\n" +
                          "[gray]📐 Block Size:[] [white]4x4[]\n" +
                          "[lightgray]Effective Range:[] [orange]329.3 px [lime](+26.6%)[]\n" +
                          "[lightgray]Base Beam Damage:[] [purple]612 DMG / pulse[]\n" +
                          "[green] Power Usage:[] [orange]850 / sec [red](+750%)[]\n" +
                          "[green] Explosion Cycle:[] [pink]Every 5 pts (1 sec)[]\n\n" +
                          "[purple]🔥 DUAL CHAOS EXPLOSION MECHANIC:[]\n" +
                          "• [lightgray]Charge Speed:[] Automatically gains [green]5 pts / sec[] while continuously firing.\n" +
                          "• [lightgray]Dual Detonation:[] Reaching 5 pts triggers dual explosions at both [yellow]Turret Center[] and [yellow]Target Position[].\n" +
                          "• [lightgray]Chaos Scatter:[] Each blast deals 1,530 DMG (2.5x) and releases [orange]13 Chaos sub-bolts[] within a 45 px radius." :
                          "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]5,241 [lime](+111.7%)[]\n" +
                          "[gray]📐 Kích thước khối:[] [white]4x4[]\n" +
                          "Tầm bắn hiệu dụng:[] [orange]329.3 pixel [lime](+26.6%)[]\n" +
                          "[lightgray]Sát thương gốc:[] [purple]612 DMG / xung[]\n" +
                          "[green] Tiêu thụ điện:[] [orange]850 / giây [red](+750%)[]\n" +
                          "[green] Chu kỳ kích nổ:[] [pink]Mỗi 5 điểm (1 giây)[]\n\n" +
                          "[purple]🔥 CƠ CHẾ KÍCH NỔ KÉP CHAOS:[]\n" +
                          "• [lightgray]Tốc độ nạp điểm:[] Tự động tích [green]5 điểm / giây[] khi duy trì xả đạn liên tục.\n" +
                          "• [lightgray]Nổ đồng thời:[] Đạt 5 điểm tự động kích nổ đồng thời tại [yellow]Tâm tháp pháo[] và [yellow]Kẻ địch bị khóa[].\n" +
                          "• [lightgray]Sét Chaos gãy khúc:[] Mỗi tâm nổ gây 1,530 DMG (gấp 2.5 lần) và tỏa ra [orange]13 tia sét Chaos[] bán kính 45 pixel.";
            }

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
            public void buildConfiguration(Table table) {
                table.clear();
                table.row();
                final boolean en = isEnglish();

                if (getTier() == 1) {
                    table.button(Icon.upOpen, Styles.cleari, 40f, () -> openUpgradeDialog(en)).size(50f, 40f).tooltip(en ? "Upgrade Lyvervon turret" : "Nâng cấp tháp pháo Lyvervon");
                } else {
                    table.button(Icon.lock, Styles.cleari, 40f, () -> Vars.ui.showInfo(en ? "[scarlet]LYVERVON HAS REACHED MAX EVOLUTION LEVEL![]" : "[scarlet]HỆ THỐNG LYVERVON ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")).size(50f, 40f).tooltip(en ? "Max level reached" : "Đã đạt cấp tối đa");
                }
                table.button(Icon.info, Styles.cleari, 40f, () -> openInfoDialog(en)).size(50f, 40f).tooltip(en ? "View detailed stats" : "Xem thông số chi tiết hệ thống");
            }

            @Override
            protected void findTarget() {
                final float currentRange = ((PowerTurret) block).range;
                if (getTier() == 1) {
                    target = Units.closestTarget(team, x, y, currentRange,
                        u -> !u.dead && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone,
                        b -> Mathf.dst(x, y, b.x, b.y) >= deadZone);
                } else {
                    final Unit[] closeTarget = {null};
                    final float[] minHp = {Float.POSITIVE_INFINITY};
                    Units.nearbyEnemies(team, x - currentRange, y - currentRange, currentRange * 2f, currentRange * 2f, u -> {
                        if (!u.dead && !u.type.flying && dst(u) <= currentRange && dst(u) >= deadZone) {
                            if (u.health < minHp[0]) { minHp[0] = u.health; closeTarget[0] = u; }
                        }
                    });
                    target = closeTarget[0];
                }
            }

            @Override
            public void updateTile() {
                super.updateTile();
                int tier = getTier();

                if (power == null || power.status <= 0f) {
                    mk3ChargePoints = 0; mk3Timer = 0f;
                    zoomAnimation = Math.max(0f, zoomAnimation - Time.delta / 8f);
                    return;
                }

                boolean firing = isShooting() && target != null && !isDead(target);
                if (firing) {
                    zoomAnimation = Math.min(1f, zoomAnimation + Time.delta / 10f);
                    if (tier == 2 || tier == 3) {
                        damageBonus += Time.delta / 60f * 60f;
                        if (damageBonus >= 1499f) damageBonus = 0f;
                    }
                } else {
                    zoomAnimation = Math.max(0f, zoomAnimation - Time.delta / 8f);
                }

                float currentRange = ((PowerTurret) block).range;
                Teamc anyEnemy = Units.closestTarget(team, x, y, currentRange, u -> !u.dead && !u.type.flying, b -> !b.dead);
                if (anyEnemy == null) {
                    mk3ChargePoints = 0; mk3Timer = 0f;
                    return;
                }

                if (tier == 3 && firing) {
                    mk3Timer += Time.delta;
                    if (mk3Timer >= 60f) {
                        mk3ChargePoints += 5;
                        mk3Timer = 0f;

                        if (mk3ChargePoints >= 5) {
                            mk3ChargePoints = 0;
                            float explosionDamage = 612f * 2.5f;

                            float targetX = target.getX(), targetY = target.getY();
                            float turretX = x, turretY = y;

                            Damage.damage(team, targetX, targetY, 45f, explosionDamage, false, true);
                            mk3ExplosionFX.at(targetX, targetY);
                            Effect.shake(5f, 5f, targetX, targetY);

                            int lits = 13;
                            for (int i = 0; i < lits; i++) {
                                float randAngle = (float) Math.random() * 360f;
                                float randDist = 15f + (float) Math.random() * 75f;
                                float extX = targetX + Angles.trnsx(randAngle, randDist);
                                float extY = targetY + Angles.trnsy(randAngle, randDist);

                                Teamc subEnemy = Units.closestTarget(team, extX, extY, 45f, u -> !u.dead && !u.type.flying, b -> !b.dead);
                                if (subEnemy != null) {
                                    damageTarget(subEnemy, explosionDamage * 0.4f);
                                    creategreenChaos(targetX, targetY, subEnemy.getX(), subEnemy.getY(), 2.5f);
                                    Fx.hitLancer.at(subEnemy.getX(), subEnemy.getY(), lyvervonColor);
                                } else {
                                    creategreenChaos(targetX, targetY, extX, extY, 2f);
                                }
                            }

                            Damage.damage(team, turretX, turretY, 45f, explosionDamage, false, true);
                            mk3ExplosionFX.at(turretX, turretY);
                            Effect.shake(5f, 5f, turretX, turretY);

                            for (int i = 0; i < lits; i++) {
                                float randAngle = (float) Math.random() * 360f;
                                float randDist = 15f + (float) Math.random() * 75f;
                                float extX = turretX + Angles.trnsx(randAngle, randDist);
                                float extY = turretY + Angles.trnsy(randAngle, randDist);

                                Teamc subEnemy = Units.closestTarget(team, extX, extY, 45f,
                                    u -> !u.dead && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone, b -> !b.dead);
                                if (subEnemy != null) {
                                    damageTarget(subEnemy, explosionDamage * 0.4f);
                                    creategreenChaos(turretX, turretY, subEnemy.getX(), subEnemy.getY(), 2.5f);
                                    Fx.hitLancer.at(subEnemy.getX(), subEnemy.getY(), lyvervonColor);
                                } else {
                                    creategreenChaos(turretX, turretY, extX, extY, 2f);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void draw() {
                // JS: `baseRegion.found` thiếu () nên luôn truthy; Java dùng found() thật để rơi về ảnh dự phòng khi thiếu sprite.
                TextureRegion baseRegion = PerfCore.region(block.name + "-base");
                if (!baseRegion.found()) baseRegion = PerfCore.region("lyvervon-base");
                if (!baseRegion.found()) baseRegion = PerfCore.region("block-" + block.size);

                Draw.z(Layer.turret - 1f);
                if (baseRegion.found()) Draw.rect(baseRegion, x, y);
                Draw.reset();

                int tier = getTier();
                boolean firing = isShooting() && target != null && !isDead(target);

                if (zoomAnimation > 0f) {
                    Draw.z(Layer.turret + 1f);

                    if (firing) {
                        if (Mathf.chance(0.12)) Fx.smoke.at(x + Mathf.range(2f), y + Mathf.range(2f));

                        Color fxColor = (tier == 3) ? lyvervonColor : greenColor;
                        float currentZoom = Interp.pow3Out.apply(0f, 1f, zoomAnimation);
                        float pulse = Vars.state.isPaused() ? 0f : Mathf.absin(Time.time, 4f, 3.5f);
                        float haloRadius = (14f + pulse) * currentZoom;

                        Draw.color(fxColor);
                        Draw.alpha(0.24f);
                        Lines.stroke(4f * currentZoom);
                        Lines.circle(x, y, haloRadius);

                        Draw.color(fxColor, Color.white, 0.3f);
                        Draw.alpha(0.6f);
                        Lines.stroke(1.8f * currentZoom);
                        Lines.circle(x, y, haloRadius);

                        Draw.color(Color.white);
                        Draw.alpha(0.95f);
                        Lines.stroke(0.6f * currentZoom);
                        Lines.circle(x, y, haloRadius);
                        Draw.reset();
                    }

                    Color ballColor = (tier == 3) ? lyvervonColor : greenColor;
                    float currentBallSize = Interp.pow3Out.apply(0f, 1f, zoomAnimation);
                    float baseRadius = 2f * currentBallSize;
                    if (isShooting() && zoomAnimation >= 0.9f && !Vars.state.isPaused()) baseRadius += Mathf.absin(Time.time, 3f, 0.4f);

                    Draw.color(ballColor);
                    Fill.circle(x, y, baseRadius + 1f);
                    Draw.color(Color.white);
                    Fill.circle(x, y, baseRadius);
                    Draw.reset();
                }

                if ((tier == 2 || tier == 3) && zoomAnimation > 0f) {
                    Color hexColor = (tier == 2) ? Color.valueOf("#ffd84d") : lyvervonColor;
                    float currentZoomSize = Interp.pow3Out.apply(0f, 1f, zoomAnimation);
                    float radius = (12f + Mathf.absin(Time.time, 4f, 1.5f)) * currentZoomSize;
                    float stroke = 1.6f * currentZoomSize;
                    float rotationAngle = Vars.state.isPaused() ? id * 15f : Time.time * 2.2f;
                    Draw.z(Layer.effect + 5f);
                    drawPolyHex(x, y, radius, stroke, rotationAngle, hexColor);
                    Draw.reset();
                }

                if (tier == 3 && mk3ChargePoints > 0) {
                    Draw.z(Layer.effect + 10f);
                    float barWidth = 28f;
                    float progress = mk3ChargePoints / 5f;
                    Draw.color(Color.black, 0.5f);
                    Lines.stroke(3f);
                    Lines.line(x - barWidth / 2f, y - 22f, x + barWidth / 2f, y - 22f);
                    Draw.color(lyvervonColor);
                    Lines.stroke(2f);
                    Lines.line(x - barWidth / 2f, y - 22f, x - barWidth / 2f + (barWidth * progress), y - 22f);
                    Draw.reset();
                }
            }

            @Override
            public void drawSelect() {
                super.drawSelect();
                dashCircle(x, y, deadZone, Pal.remove);
            }

            @Override
            public void write(Writes write) {
                super.write(write);
                write.b(getTier());
            }

            @Override
            public void read(Reads read, byte revision) {
                super.read(read, revision);
                setTier(read.b());
            }
        }
    }
}
