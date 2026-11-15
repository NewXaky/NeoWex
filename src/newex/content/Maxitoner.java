package newex.content;

import arc.Core;
import arc.audio.Sound;
import arc.func.Func;
import arc.graphics.Blending;
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
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.units.StatusEntry;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;

/** maxitoner.js → Java. Pháo Metaglass tích tầng Nitoui, đủ tầng thì nổ diện rộng; 3 nhóm phúc lợi A/B/C. */
public final class Maxitoner {
    // Hàm ngôn ngữ ĐÚNG của file JS này: locale bắt đầu bằng "en" (bài học 8)
    static boolean isEn() { return String.valueOf(Core.settings.getString("locale", "")).startsWith("en"); }

    static final Color EXPLODE_COLOR = Color.orange;
    static final Color EXPLODE_COLOR_LIGHT = Color.valueOf("#ffdfa9");

    // reqPerkA/B/C
    static final int A_COPPER = 1500, A_LEAD = 1500, A_SILICON = 1500;
    static final int B_TITANIUM = 1500, B_THORIUM = 1500, B_SILICON = 1500;
    static final int C_SURGE = 500, C_PHASE = 500, C_SILICON = 2000;

    static Sound shootSound;
    static ItemTurret maxitoner;
    static StatusEffect nitouiStatus;
    static MaxBullet maxitonerBulletBase;

    static final class Stack { int count; }
    static final java.util.WeakHashMap<Unit, Stack> nitouiMap = new java.util.WeakHashMap<>();

    static final Effect nitouiExplodeFx = new Effect(35f, e -> {
        Draw.z(Layer.effect + 0.1f);
        float radius = e.rotation, fin = e.fin(), fout = e.fout();
        Draw.blend(Blending.additive);
        float zoomProgress = (float) Math.sin(fin * Math.PI);
        float coreScale = 0.2f + 0.8f * zoomProgress;
        float coreRadius = (radius * 0.3f) * coreScale;
        Draw.color(EXPLODE_COLOR); Draw.alpha(0.5f * fout); Fill.circle(e.x, e.y, coreRadius * 3.5f);
        Draw.color(EXPLODE_COLOR_LIGHT); Draw.alpha(0.8f * fout); Fill.circle(e.x, e.y, coreRadius * 2.0f);
        Draw.color(EXPLODE_COLOR); Draw.alpha(1.0f * fout); Fill.circle(e.x, e.y, coreRadius * 1.3f);
        float ringStroke = 3.0f * (float) Math.sin(fin * Math.PI);
        Draw.color(EXPLODE_COLOR); Draw.alpha(0.6f * fout); Lines.stroke(ringStroke * 2.5f); Lines.circle(e.x, e.y, radius * fin);
        Draw.color(EXPLODE_COLOR_LIGHT); Draw.alpha(0.9f * fout); Lines.stroke(ringStroke); Lines.circle(e.x, e.y, radius * fin);
        int quadCount = 12;
        for (int i = 0; i < quadCount; i++) {
            long seed = e.id + i * 123L;
            float angle = Mathf.randomSeed(seed, 0f, 360f);
            float dist = Mathf.randomSeed(seed + 1, 5f, radius * 0.85f) * fin;
            float px = e.x + Angles.trnsx(angle, dist), py = e.y + Angles.trnsy(angle, dist);
            float quadSize = Mathf.randomSeed(seed + 2, 2.5f, 7.0f) * fout;
            float rot = Mathf.randomSeed(seed + 3, 0f, 360f) + Time.time * 2f;
            Draw.color(EXPLODE_COLOR); Draw.alpha(0.7f * fout);
            Fill.poly(px, py, 4, quadSize * 2.2f, rot);
        }
        Draw.blend();
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, coreRadius * 0.6f * fout);
        for (int i = 0; i < quadCount; i++) {
            long seed = e.id + i * 123L;
            float angle = Mathf.randomSeed(seed, 0f, 360f);
            float dist = Mathf.randomSeed(seed + 1, 5f, radius * 0.85f) * fin;
            float px = e.x + Angles.trnsx(angle, dist), py = e.y + Angles.trnsy(angle, dist);
            float quadSize = Mathf.randomSeed(seed + 2, 2.5f, 7.0f) * fout;
            float rot = Mathf.randomSeed(seed + 3, 0f, 360f) + Time.time * 2f;
            Draw.color(EXPLODE_COLOR_LIGHT, EXPLODE_COLOR, Mathf.randomSeed(seed + 4, 0f, 1f));
            if (i % 2 == 0) {
                Fill.poly(px, py, 4, quadSize, rot);
            } else {
                Lines.stroke(1.2f * fout);
                Lines.poly(px, py, 4, quadSize, rot);
            }
        }
        Draw.reset();
    });

    static final Effect nitouiIngatherFx = new Effect(20f, e -> {
        Draw.z(Layer.effect + 0.05f);
        long seed = e.id;
        float startDist = 12.0f + Mathf.randomSeed(seed, 0f, 6f);
        float dist = startDist * (1.0f - e.fin());
        float angle = Mathf.randomSeed(seed + 1, 0f, 360f);
        float px = e.x + Angles.trnsx(angle, dist), py = e.y + Angles.trnsy(angle, dist);
        float size = (0.4f + Mathf.randomSeed(seed + 2, 0f, 0.6f)) * e.fout();
        float rot = Mathf.randomSeed(seed + 3, 0f, 360f) + Time.time * 4f;
        Draw.blend(Blending.additive);
        Draw.color(EXPLODE_COLOR); Draw.alpha(0.8f * e.fout());
        Fill.poly(px, py, 4, size * 1.8f, rot);
        Draw.blend();
        Draw.color(EXPLODE_COLOR_LIGHT); Draw.alpha(1.0f * e.fout());
        Fill.poly(px, py, 4, size, rot);
        Draw.reset();
    });

    static final Effect maxitonerShootFx = new Effect(25f, e -> {
        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);
        Draw.color(EXPLODE_COLOR); Draw.alpha(0.5f * e.fout());
        Fill.poly(e.x, e.y, 4, (6f + 4f * e.fin()), 0f);
        Draw.blend();
        Draw.color(EXPLODE_COLOR_LIGHT, EXPLODE_COLOR, e.fin());
        Lines.stroke(1.5f * e.fout());
        float size = 5f + 3f * e.fin();
        Lines.poly(e.x, e.y, 4, size, 0f);
        Draw.color(Color.white, EXPLODE_COLOR, e.fin());
        Fill.poly(e.x, e.y, 4, (size * 0.4f) * e.fout(), 0f);
        Draw.reset();
    });

    static final Effect maxitonerShootFxSmall = new Effect(20f, e -> {
        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);
        Draw.color(EXPLODE_COLOR); Draw.alpha(0.4f * e.fout());
        Fill.poly(e.x, e.y, 4, (4f + 3f * e.fin()), 0f);
        Draw.blend();
        Draw.color(EXPLODE_COLOR_LIGHT, EXPLODE_COLOR, e.fin());
        Lines.stroke(1.0f * e.fout());
        float size = 3f + 2f * e.fin();
        Lines.poly(e.x, e.y, 4, size, 0f);
        Draw.color(Color.white, EXPLODE_COLOR, e.fin());
        Fill.poly(e.x, e.y, 4, (size * 0.4f) * e.fout(), 0f);
        Draw.reset();
    });

    static final class MaxBullet extends BasicBulletType {
        MaxBullet() {
            speed = 7.0f; damage = 10f; width = 8f; height = 12f;
            frontColor = EXPLODE_COLOR_LIGHT; backColor = EXPLODE_COLOR;
            shootEffect = maxitonerShootFx;
        }
        @Override public void draw(Bullet b) {
            Draw.z(Layer.bullet);
            Draw.blend(Blending.additive);
            Draw.color(EXPLODE_COLOR); Draw.alpha(0.5f);
            Fill.poly(b.x, b.y, 4, 8.0f, b.rotation());
            Draw.blend();
            Draw.color(backColor);
            Fill.poly(b.x, b.y, 4, 5.0f, b.rotation());
            Draw.color(frontColor);
            Fill.poly(b.x, b.y, 4, 3.0f, b.rotation());
            Draw.reset();
        }
        @Override public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other != null && b.owner instanceof MaxitonerBuild) ((MaxitonerBuild) b.owner).handleMaxitonerHit(b, other);
        }
    }

    /** Gọi từ NexMod.loadContent() (JS chạy ngay khi require). */
    public static void create() {
        shootSound = Vars.tree.loadSound("shotbatt");
        nitouiStatus = new StatusEffect("nitoui-status") {
            { color = EXPLODE_COLOR; }
            @Override public void init() {
                super.init();
                uiIcon = StatusEffects.shocked.uiIcon;
                fullIcon = StatusEffects.shocked.fullIcon;
            }
            @Override public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                Stack data = nitouiMap.get(unit);
                if (data != null && data.count > 0) {
                    for (int i = 0; i < data.count; i++) nitouiIngatherFx.at(unit.x, unit.y);
                }
            }
        };
        maxitonerBulletBase = new MaxBullet();

        maxitoner = new ItemTurret("maxitoner");
        maxitoner.configurable = true;
        maxitoner.health = 300;
        maxitoner.range = 350f;
        maxitoner.reload = 48f;
        maxitoner.recoil = 3.0f;
        maxitoner.shootX = 0f;
        maxitoner.shootY = 5f;
        // JS gán maxitoner.shootEffect: Turret v157 không có trường này (bài học 24) → bỏ.
        maxitoner.ammo(Items.metaglass, maxitonerBulletBase);
        maxitoner.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof MaxitonerBuild) {
                MaxitonerBuild t = (MaxitonerBuild) tile;
                int val = value;
                if (val >= 30) t.perkC = val - 30;
                else if (val >= 20) t.perkB = val - 20;
                else if (val >= 10) t.perkA = val - 10;
            }
        });
        maxitoner.buildType = () -> new MaxitonerBuild(maxitoner);
    }

    public static class MaxitonerBuild extends ItemTurret.ItemTurretBuild {
        int perkA, perkB, perkC;
        float autoFireTimer;

        public MaxitonerBuild(ItemTurret outer) { outer.super(); }

        @Override public float maxHealth() {
            float hp = 300f;
            if (perkB == 1) hp *= 6.0f;
            if (perkB == 3) hp *= 0.5f;
            return hp;
        }

        @Override public float range() {
            float baseR = 350f;
            if (perkA == 1) baseR *= 1.5f;
            if (perkA == 3) baseR *= 1.2f;
            if (perkB == 1) baseR *= 0.8f;
            if (perkB == 2) baseR *= 3.6f;
            return baseR;
        }

        @Override protected void findTarget() {
            if (perkB == 1) {
                final float r = range();
                final Unit[] best = {null};
                final float[] maxHp = {-1f};
                Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, e -> {
                    if (e.within(x, y, r) && e.health > maxHp[0]) { maxHp[0] = e.health; best[0] = e; }
                });
                if (best[0] != null) { target = best[0]; return; }
            }
            super.findTarget();
        }

        void handleMaxitonerHit(Bullet bullet, Hitboxc other) {
            if (!(other instanceof Unit)) return;
            Unit target = (Unit) other;
            if (perkC == 3) {
                Damage.damage(team, target.x, target.y, 100f, 500f, false, true);
                nitouiExplodeFx.at(target.x, target.y, 100f);
            }
            int addStacks = 1;
            if (perkB == 2) addStacks += 3;
            addNitouiStack(target, addStacks);
        }

        void addNitouiStack(final Unit target, int amount) {
            int reqStacks = (perkB == 3) ? 7 : 9;
            Stack data = nitouiMap.get(target);
            if (data == null) { data = new Stack(); nitouiMap.put(target, data); }
            data.count += amount;
            target.apply(nitouiStatus, 60f);

            if (data.count >= reqStacks) {
                nitouiMap.remove(target);
                float currentBaseDmg = 10f;
                if (perkB == 3) currentBaseDmg *= 25.0f;
                if (perkC == 3) currentBaseDmg *= 6.0f;
                float explosionDmg = currentBaseDmg * 42.0f;
                if (perkA == 1) explosionDmg *= 2.5f;
                if (perkA == 3) explosionDmg *= 1.2f;
                final float explosionRadius = perkC == 2 ? 200f : 100f;
                final float dmg = explosionDmg;

                Damage.damage(team, target.x, target.y, explosionRadius, dmg, false, true);
                nitouiExplodeFx.at(target.x, target.y, explosionRadius);

                if (perkA == 2) {
                    final Unit[] tankiest = {null};
                    final float[] maxHp = {-1f};
                    Units.nearbyEnemies(team, target.x - explosionRadius, target.y - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, e -> {
                        if (e != target && e.within(target.x, target.y, explosionRadius) && e.health > maxHp[0]) { maxHp[0] = e.health; tankiest[0] = e; }
                    });
                    if (tankiest[0] != null) addNitouiStack(tankiest[0], 7);
                }
                if (perkA == 3) {
                    for (int i = 0; i < 9; i++) {
                        float angle = Mathf.random(360f);
                        Lightning.create(team, EXPLODE_COLOR, 50f, target.x, target.y, angle, 10);
                    }
                }
                if (perkC == 2) {
                    Units.nearbyEnemies(team, target.x - explosionRadius, target.y - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, e -> {
                        if (e != target && e.within(target.x, target.y, explosionRadius)) {
                            Stack sub = nitouiMap.get(e);
                            if (sub == null) sub = new Stack();
                            sub.count += 2;
                            if (sub.count >= 9) {
                                Damage.damage(team, e.x, e.y, explosionRadius, dmg, false, true);
                                nitouiExplodeFx.at(e.x, e.y, explosionRadius);
                                nitouiMap.remove(e);
                            } else {
                                nitouiMap.put(e, sub);
                            }
                        }
                    });
                }
            }
        }

        void spawnBullet(float angleOffset, float extraDmgMult, boolean pierce, boolean homing, boolean isExtraBullet) {
            float bulletSpeed = 7.0f;
            float calculatedLifetime = range() / bulletSpeed;
            float baseDmg = 10f;
            if (perkB == 3) baseDmg *= 25.0f;
            if (perkC == 3) baseDmg *= 6.0f;

            Bullet b = maxitonerBulletBase.create(this, team, x, y, rotation + angleOffset);
            if (b != null) {
                b.vel.setLength(bulletSpeed);
                b.lifetime = calculatedLifetime;
                b.damage = baseDmg * extraDmgMult;
                // JS sửa thẳng b.type (dùng chung cho MỌI viên đạn) nên 3C bật xuyên/đuổi vĩnh viễn cho cả đạn của pháo khác (giữ nguyên).
                if (pierce) { b.type.pierce = true; b.type.pierceCap = 20; }
                if (homing) { b.type.homingPower = 0.08f; b.type.homingRange = 150f; }
                if (isExtraBullet) maxitonerShootFxSmall.at(x, y);
            }
        }

        @Override protected void shoot(BulletType type) {
            if (!hasAmmo()) return;
            maxitonerShootFx.at(x, y);
            if (shootSound != null) shootSound.at(x, y, Mathf.random(0.9f, 1.1f));
            int extraBullets = 0;
            if (perkB == 3) extraBullets += 2;
            if (perkC == 1) extraBullets += 5;
            int totalBullets = 1 + extraBullets;
            float spread = totalBullets > 1 ? 10.0f : 0.0f;
            boolean is3C = (perkC == 3);
            spawnBullet(0f, 1.0f, is3C, is3C, false);
            for (int i = 1; i < totalBullets; i++) {
                spawnBullet(Mathf.range(spread), 1.0f, is3C, is3C, true);
            }
            useAmmo();
        }

        @Override public void updateTile() {
            super.updateTile();
            // JS: `this.isShooting` thiếu () → luôn truthy; Java gọi isShooting() thật (bài học 61).
            if (perkC == 1 && isShooting() && hasAmmo()) {
                autoFireTimer += Time.delta;
                if (autoFireTimer >= 60f) {
                    autoFireTimer = 0f;
                    for (int i = 0; i < 3; i++) spawnBullet(Mathf.range(4.0f), 1.0f, false, false, true);
                }
            }
            // JS tính reloadMult rồi gán `this.reloadTime` – trường này KHÔNG có ở v157 nên tốc bắn của phúc lợi chưa từng đổi (bài học 62) → bỏ.
        }

        // ===== Giao diện =====
        private String reqSnapshot() {
            CoreBuild core = team.core();
            if (core == null) return isEn() ? "[red]Team Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";
            int cCop = core.items.get(Items.copper), cLea = core.items.get(Items.lead), cSil = core.items.get(Items.silicon);
            int cTit = core.items.get(Items.titanium), cTho = core.items.get(Items.thorium);
            int cSur = core.items.get(Items.surgeAlloy), cPha = core.items.get(Items.phaseFabric);
            String colCopA = cCop >= A_COPPER ? "[green]" : "[red]";
            String colLeaA = cLea >= A_LEAD ? "[green]" : "[red]";
            String colSilA = cSil >= A_SILICON ? "[green]" : "[red]";
            String colTitB = cTit >= B_TITANIUM ? "[green]" : "[red]";
            String colThoB = cTho >= B_THORIUM ? "[green]" : "[red]";
            String colSilB = cSil >= B_SILICON ? "[green]" : "[red]";
            String colSurC = cSur >= C_SURGE ? "[green]" : "[red]";
            String colPhaC = cPha >= C_PHASE ? "[green]" : "[red]";
            String colSilC = cSil >= C_SILICON ? "[green]" : "[red]";
            if (isEn()) {
                    return "[gold]CORE RESOURCE REQUIREMENTS (MAXITONER):[]\n" +
                           "[yellow]★ PERK ROLL A:[] Copper: " + colCopA + cCop + "[]/1500 | Lead: " + colLeaA + cLea + "[]/1500 | Silicon: " + colSilA + cSil + "[]/1500\n" +
                           "[cyan]★ PERK ROLL B:[] Titanium: " + colTitB + cTit + "[]/1500 | Thorium: " + colThoB + cTho + "[]/1500 | Silicon: " + colSilB + cSil + "[]/1500\n" +
                           "[purple]★ PERK ROLL C:[] Surge: " + colSurC + cSur + "[]/500 | Phase: " + colPhaC + cPha + "[]/500 | Silicon: " + colSilC + cSil + "[]/2000";
            } else {
                    return "[gold]YÊU CẦU TÀI NGUYÊN LÕI (MAXITONER):[]\n" +
                           "[yellow]★ ROLL PHÚC LỢI A:[] Đồng: " + colCopA + cCop + "[]/1500 | Chì: " + colLeaA + cLea + "[]/1500 | Silicon: " + colSilA + cSil + "[]/1500\n" +
                           "[cyan]★ ROLL PHÚC LỢI B:[] Titan: " + colTitB + cTit + "[]/1500 | Thorium: " + colThoB + cTho + "[]/1500 | Silicon: " + colSilB + cSil + "[]/1500\n" +
                           "[purple]★ ROLL PHÚC LỢI C:[] Surge: " + colSurC + cSur + "[]/500 | Phase: " + colPhaC + cPha + "[]/500 | Silicon: " + colSilC + cSil + "[]/2000";
            }
        }

        private void setPerk(int base, int res) {
            if (base == 10) perkA = res; else if (base == 20) perkB = res; else perkC = res;
        }

        /** Một hộp phúc lợi: đã có → hiện mô tả; chưa có → nút quay ngẫu nhiên (trừ tài nguyên lõi). */
        private Table perkBox(final BaseDialog dialog, String header, int perk, String desc, String active, String btnLabel, String notEnough,
                              final Item[] items, final int[] req, final float t1, final float t2, final int cfgBase, final Func<Integer, String> resMsg) {
            Table box = new Table();
            box.background(Styles.black6);
            box.margin(12f);
            box.add(header).row();
            if (perk == 0) {
                Cell<Label> cell = box.add(desc);
                cell.width(340f).get().setWrap(true);
                cell.get().setAlignment(Align.left);
                box.row();
                final String fail = notEnough;
                box.button(btnLabel, () -> {
                    CoreBuild core = team.core();
                    boolean ok = core != null;
                    for (int i = 0; ok && i < items.length; i++) ok = core.items.get(items[i]) >= req[i];
                    if (ok) {
                        for (int i = 0; i < items.length; i++) NexNet.take(core, items[i], req[i]);
                        float chance = Mathf.random(100f);
                        int res = chance < t1 ? 1 : (chance < t2 ? 2 : 3);
                        setPerk(cfgBase, res);
                        configure(Integer.valueOf(cfgBase + res));
                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(4f, 4f, x, y);
                        Vars.ui.showInfo(resMsg.get(res));
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(fail);
                    }
                }).size(280f, 40f);
            } else {
                Cell<Label> cell = box.add(active);
                cell.width(340f).get().setWrap(true);
                cell.get().setAlignment(Align.left);
            }
            return box;
        }

        private void openUpgradeDialog() {
            String dialogTitle = isEn() ? "Maxitoner Upgrade Center" : "Trung tâm nâng cấp pháo Maxitoner";
            final BaseDialog dialog = new BaseDialog(dialogTitle);
            Cell<Label> reqCell = dialog.cont.add(reqSnapshot());
            reqCell.width(380f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table mainTable = new Table();

            // ---- A ----
            int perkA = this.perkA;
            String txtA = "";
                if (isEn()) {
                    if (perkA == 1) txtA = "[green]✔ ACTIVATED: PERK 1A\n• Range +50% (525px)\n• Nitoui explosion DMG +150%[]";
                    if (perkA == 2) txtA = "[green]✔ ACTIVATED: PERK 2A\n• Fire rate +80%\n• Nitoui explosion spreads 7 stacks to nearby tankiest enemy[]";
                    if (perkA == 3) txtA = "[green]✔ ACTIVATED: PERK 3A\n• Range +20%, Fire rate +20%, Explosion DMG +20%\n• Nitoui explosion shoots 9 extra lasers (50 Dmg)[]";
                } else {
                    if (perkA == 1) txtA = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1A\n• Tầm bắn +50% (525px)\n• Sát thương nổ Nitoui +150%[]";
                    if (perkA == 2) txtA = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2A\n• Tốc độ bắn +80%\n• Vụ nổ Nitoui lây 7 tầng cho kẻ địch trâu nhất gần đó[]";
                    if (perkA == 3) txtA = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3A\n• Tầm bắn +20%, Tốc bắn +20%, Dmg nổ +20%\n• Vụ nổ Nitoui phóng thêm 9 tia laze (50 Dmg)[]";
                }
            String descA = isEn() ?
                    "Activate random upgrade to get 1 of 3 Perks A:\n" +
                    " • [yellow]Perk 1A (50%):[] Range +50%, Nitoui explosion DMG +150%.\n" +
                    " • [yellow]Perk 2A (30%):[] Fire rate +80%, Nitoui explosion randomly attaches 7 stacks to tankiest enemy.\n" +
                    " • [yellow]Perk 3A (20%):[] Range +20%, Fire rate +20%, Explosion DMG +20%, Shoots 9 lasers on explosion." :
                    "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi A:\n" +
                    " • [yellow]Phúc lợi 1A (50%):[] Tầm bắn +50%, Sát thương nổ Nitoui +150%.\n" +
                    " • [yellow]Phúc lợi 2A (30%):[] Tốc bắn +80%, Nổ Nitoui ngẫu nhiên gắn 7 tầng cho trâu nhất.\n" +
                    " • [yellow]Phúc lợi 3A (20%):[] Tầm bắn +20%, Tốc bắn +20%, Dmg nổ +20%, Bắn 9 tia laze khi nổ.";
            mainTable.add(perkBox(dialog, isEn() ? "[yellow]★ PERK ROLL A (RANDOM) ★[]" : "[yellow]★ ROLL PHÚC LỢI A (NGẪU NHIÊN) ★[]", perkA, descA, txtA,
                    isEn() ? "[yellow]ROLL PERK A (1.5K Cop/Lead/Sil)[]" : "[yellow]QUAY PHÚC LỢI A (1.5K Đồng/Chì/Silicon)[]",
                    isEn() ? "[red]Not enough resources to roll Perk A![]" : "[red]Không đủ tài nguyên roll Phúc lợi A![]",
                    new Item[]{Items.copper, Items.lead, Items.silicon}, new int[]{A_COPPER, A_LEAD, A_SILICON}, 50f, 80f, 10,
                    res -> isEn() ? "[gold]YOU ROLLED:[]\n[yellow]PERK " + res + "A[]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[yellow]PHÚC LỢI " + res + "A[]")).width(360f).row();
            mainTable.add().height(12f).row();

            // ---- B ----
            int perkB = this.perkB;
            String txtB = "";
                if (isEn()) {
                    if (perkB == 1) txtB = "[green]✔ ACTIVATED: PERK 1B\n• Fire rate +120%, HP +500% (1800 HP), Range -20%\n• Locks target on highest HP unit[]";
                    if (perkB == 2) txtB = "[green]✔ ACTIVATED: PERK 2B\n• Range +260% (1260px), Fire rate -50%\n• Bullet hit instantly applies +3 Nitoui stacks[]";
                    if (perkB == 3) txtB = "[green]✔ ACTIVATED: PERK 3B\n• Base DMG +2400% (250 Dmg), Shoots +2 extra bullets\n• HP -50% (150 HP), Reduces Nitoui explosion threshold to 7 stacks[]";
                } else {
                    if (perkB == 1) txtB = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1B\n• Tốc bắn +120%, Máu +500% (1800 HP), Tầm bắn -20%\n• Khóa mục tiêu nhiều máu nhất[]";
                    if (perkB == 2) txtB = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2B\n• Tầm bắn +260% (1260px), Tốc bắn -50%\n• Đạn trúng mục tiêu gắn ngay +3 tầng Nitoui[]";
                    if (perkB == 3) txtB = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3B\n• Sát thương gốc +2400% (250 Dmg), Bắn +2 đạn phụ\n• Máu -50% (150 HP), Giảm mốc nổ Nitoui xuống 7 tầng[]";
                }
            String descB = isEn() ?
                    "Activate random upgrade to get 1 of 3 Perks B:\n" +
                    " • [cyan]Perk 1B (50%):[] Fire rate +120%, HP +500%, Range -20%, Targets highest HP enemy.\n" +
                    " • [cyan]Perk 2B (20%):[] Range +260%, Fire rate -50%, Hit adds +3 Nitoui stacks.\n" +
                    " • [cyan]Perk 3B (10%):[] Lower fire rate, HP -50%, Base DMG +2400%, Shoots 2 extra bullets, Explosion threshold reduced to 7 stacks." :
                    "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi B:\n" +
                    " • [cyan]Phúc lợi 1B (50%):[] Tốc bắn +120%, Máu +500%, Tầm bắn -20%, Nhắm địch nhiều máu nhất.\n" +
                    " • [cyan]Phúc lợi 2B (20%):[] Tầm bắn +260%, Tốc bắn -50%, Bắn trúng +3 tầng Nitoui.\n" +
                    " • [cyan]Phúc lợi 3B (10%):[] Tốc bắn giảm, Máu -50%, Sát thương gốc +2400%, Bắn thêm 2 đạn, Mốc nổ giảm xuống 7 tầng.";
            mainTable.add(perkBox(dialog, isEn() ? "[cyan]★ PERK ROLL B (RANDOM) ★[]" : "[cyan]★ ROLL PHÚC LỢI B (NGẪU NHIÊN) ★[]", perkB, descB, txtB,
                    isEn() ? "[cyan]ROLL PERK B (1.5K Tit/Tho/Sil)[]" : "[cyan]QUAY PHÚC LỢI B (1.5K Titan/Thorium/Silicon)[]",
                    isEn() ? "[red]Not enough resources to roll Perk B![]" : "[red]Không đủ tài nguyên roll Phúc lợi B![]",
                    new Item[]{Items.titanium, Items.thorium, Items.silicon}, new int[]{B_TITANIUM, B_THORIUM, B_SILICON}, 50f, 70f, 20,
                    res -> isEn() ? "[gold]YOU ROLLED:[]\n[cyan]PERK " + res + "B[]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[cyan]PHÚC LỢI " + res + "B[]")).width(360f).row();
            mainTable.add().height(12f).row();

            // ---- C ----
            int perkC = this.perkC;
            String txtC = "";
                if (isEn()) {
                    if (perkC == 1) txtC = "[green]✔ ACTIVATED: PERK 1C\n• Shoots +5 extra bullets\n• Auto-fires 3 extra sub-bullets every 1 second[]";
                    if (perkC == 2) txtC = "[green]✔ ACTIVATED: PERK 2C\n• Nitoui explosion radius +100% (200px)\n• Nitoui explosion spreads 2 stacks to enemies in radius[]";
                    if (perkC == 3) txtC = "[green]✔ ACTIVATED: PERK 3C\n• Base bullet +500% Damage (60 Dmg), Pierces 20 targets, Homing\n• Hits cause 500 Dmg explosion (100px radius)[]";
                } else {
                    if (perkC == 1) txtC = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1C\n• Bắn thêm +5 đạn phụ\n• Mỗi 1 giây tự động xả thêm 3 viên đạn phụ[]";
                    if (perkC == 2) txtC = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2C\n• Phạm vi nổ Nitoui +100% (200px)\n• Nổ Nitoui lây ngay 2 tầng cho kẻ địch trong phạm vi[]";
                    if (perkC == 3) txtC = "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3C\n• Đạn gốc +500% Sát thương (60 Dmg), Xuyên 20 mục tiêu, Truy đuổi\n• Đạn trúng mục tiêu gây nổ 500 Dmg (Bán kính 100px)[]";
                }
            String descC = isEn() ?
                    "Activate random upgrade to get 1 of 3 Perks C:\n" +
                    " • [purple]Perk 1C (98%):[] Shoots +5 extra bullets, auto-fires 3 extra bullets every 1s.\n" +
                    " • [purple]Perk 2C (1%):[] Nitoui explosion radius +100% (200px), spreads +2 stacks to surrounding enemies.\n" +
                    " • [purple]Perk 3C (1%):[] Base bullet +500% DMG, pierces 20 targets, homing, triggers 500 DMG explosion on hit." :
                    "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi C:\n" +
                    " • [purple]Phúc lợi 1C (98%):[] Bắn thêm +5 đạn phụ, mỗi 1s tự động bắn thêm 3 đạn.\n" +
                    " • [purple]Phúc lợi 2C (1%):[] Phạm vi nổ Nitoui +100% (200px), lây +2 tầng cho kẻ địch xung quanh.\n" +
                    " • [purple]Phúc lợi 3C (1%):[] Đạn gốc +500% Dmg, xuyên 20 mục tiêu, đuổi địch, kích nổ 500 Dmg khi trúng.";
            mainTable.add(perkBox(dialog, isEn() ? "[purple]★ PERK ROLL C (RANDOM) ★[]" : "[purple]★ ROLL PHÚC LỢI C (NGẪU NHIÊN) ★[]", perkC, descC, txtC,
                    isEn() ? "[purple]ROLL PERK C (500 Sur/Pha, 2K Sil)[]" : "[purple]QUAY PHÚC LỢI C (500 Surge/Phase, 2K Silicon)[]",
                    isEn() ? "[red]Not enough resources to roll Perk C![]" : "[red]Không đủ tài nguyên roll Phúc lợi C![]",
                    new Item[]{Items.surgeAlloy, Items.phaseFabric, Items.silicon}, new int[]{C_SURGE, C_PHASE, C_SILICON}, 98f, 99f, 30,
                    res -> isEn() ? "[gold]YOU ROLLED:[]\n[purple]PERK " + res + "C[]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[purple]PHÚC LỢI " + res + "C[]")).width(360f);

            ScrollPane scroll = new ScrollPane(mainTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = isEn() ? " Maxitoner Cannon Stats " : " Thông số pháo Maxitoner ";
            String descStr = "";
            if (isEn()) {
                descStr = "[gold]⚡ MAXITONER BASE STATS ⚡[]\n" +
                          "• HP: 300 HP | Range: 350px (43.7 tiles) | Bullet Damage: 10.0\n" +
                          "• Mechanism: Applies Nitoui stacks on hit. Reaching 9 stacks triggers an AoE explosion equal to 4200% of base damage (100px radius).\n" +
                          "• Upgrades: Up to 3 independent Perk groups A, B, C for diverse tactical builds.";
            } else {
                descStr = "[gold]⚡ THÔNG SỐ CƠ BẢN PHÁO MAXITONER ⚡[]\n" +
                          "• Máu: 300 HP | Tầm bắn: 350px (43.7 ô) | Sát thương đạn: 10.0\n" +
                          "• Cơ chế: Bắn tích tầng hiệu ứng Nitoui lên mục tiêu. Đủ 9 tầng gây nổ diện rộng bằng 4200% Dmg gốc của pháo (Bán kính 100px).\n" +
                          "• Nâng cấp: Tối đa 3 nhóm Phúc lợi độc lập A, B, C nâng cấp đa dạng chiến thuật.";
            }
            int perkA = this.perkA, perkB = this.perkB, perkC = this.perkC;
            if (perkA > 0) {
                descStr += isEn() ? "\n\n[yellow]★ ACTIVATED PERK A ★[]" : "\n\n[yellow]★ ĐÃ KÍCH HOẠT PHÚC LỢI A ★[]";
                if (isEn()) {
                    if (perkA == 1) descStr += "\n[green]• Perk 1A: Range +50% (525px), Nitoui explosion DMG +150%.[]";
                    if (perkA == 2) descStr += "\n[green]• Perk 2A: Fire rate +80%, Nitoui explosion spreads 7 stacks to tankiest enemy.[]";
                    if (perkA == 3) descStr += "\n[green]• Perk 3A: Range +20%, Fire rate +20%, Explosion DMG +20%, Shoots 9 lasers on explosion.[]";
                } else {
                    if (perkA == 1) descStr += "\n[green]• Phúc lợi 1A: Tầm bắn +50% (525px), Dmg nổ Nitoui +150%.[]";
                    if (perkA == 2) descStr += "\n[green]• Phúc lợi 2A: Tốc bắn +80%, Nổ Nitoui ngẫu nhiên lây 7 tầng cho kẻ địch trâu nhất.[]";
                    if (perkA == 3) descStr += "\n[green]• Phúc lợi 3A: Tầm bắn +20%, Tốc bắn +20%, Dmg nổ +20%, Nổ Nitoui bắn 9 tia laze.[]";
                }
            }

            if (perkB > 0) {
                descStr += isEn() ? "\n\n[cyan]★ ACTIVATED PERK B ★[]" : "\n\n[cyan]★ ĐÃ KÍCH HOẠT PHÚC LỢI B ★[]";
                if (isEn()) {
                    if (perkB == 1) descStr += "\n[green]• Perk 1B: Fire rate +120%, HP +500% (1800 HP), Range -20%, Targets highest HP enemy.[]";
                    if (perkB == 2) descStr += "\n[green]• Perk 2B: Range +260% (1260px), Fire rate -50%, Hit adds +3 Nitoui stacks.[]";
                    if (perkB == 3) descStr += "\n[green]• Perk 3B: Base DMG +2400%, Shoots +2 extra bullets, HP -50%, Explosion threshold reduced to 7 stacks.[]";
                } else {
                    if (perkB == 1) descStr += "\n[green]• Phúc lợi 1B: Tốc bắn +120%, Máu +500% (1800 HP), Tầm bắn -20%, Nhắm địch trâu nhất.[]";
                    if (perkB == 2) descStr += "\n[green]• Phúc lợi 2B: Tầm bắn +260% (1260px), Tốc bắn -50%, Bắn trúng +3 tầng Nitoui.[]";
                    if (perkB == 3) descStr += "\n[green]• Phúc lợi 3B: Sát thương gốc +2400%, Bắn thêm 2 đạn, Máu -50%, Mốc nổ giảm còn 7 tầng.[]";
                }
            }

            if (perkC > 0) {
                descStr += isEn() ? "\n\n[purple]★ ACTIVATED PERK C ★[]" : "\n\n[purple]★ ĐÃ KÍCH HOẠT PHÚC LỢI C ★[]";
                if (isEn()) {
                    if (perkC == 1) descStr += "\n[green]• Perk 1C: Shoots +5 extra bullets, auto-fires 3 extra sub-bullets every 1s.[]";
                    if (perkC == 2) descStr += "\n[green]• Perk 2C: Nitoui explosion radius +100% (200px), spreads 2 stacks to surrounding enemies.[]";
                    if (perkC == 3) descStr += "\n[green]• Perk 3C: Base bullet +500% DMG (60 Dmg), Pierces 20 targets, Homing, 500 DMG explosion on hit.[]";
                } else {
                    if (perkC == 1) descStr += "\n[green]• Phúc lợi 1C: Bắn thêm +5 đạn, mỗi 1s tự bắn thêm 3 đạn phụ.[]";
                    if (perkC == 2) descStr += "\n[green]• Phúc lợi 2C: Phạm vi nổ Nitoui +100% (200px), lây 2 tầng cho kẻ địch xung quanh.[]";
                    if (perkC == 3) descStr += "\n[green]• Phúc lợi 3C: Đạn gốc +500% Dmg (60 Dmg), Xuyên 20 mục tiêu, Đuổi địch, Nổ 500 Dmg khi trúng.[]";
                }
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

        @Override public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog).size(50f, 40f).tooltip(isEn() ? "Maxitoner Upgrade Center" : "Trung tâm nâng cấp pháo Maxitoner");
            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog).size(50f, 40f).tooltip(isEn() ? "View Maxitoner Cannon Stats" : "Xem thông số pháo Maxitoner");
        }

        @Override public void write(Writes write) {
            super.write(write);
            write.b(perkA);
            write.b(perkB);
            write.b(perkC);
        }

        @Override public void read(Reads read, byte revision) {
            super.read(read, revision);
            perkA = read.b();
            perkB = read.b();
            perkC = read.b();
        }
    }
}
