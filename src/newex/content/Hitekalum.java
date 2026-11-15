package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Func;
import arc.func.Intp;
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
import arc.struct.IntSet;
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
import mindustry.content.StatusEffects;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;

/**
 * Chuyển từ hitekalum.js. Pháo điện từ PowerTurret, 3 bảng phúc lợi A/B/C quay ngẫu nhiên, buff từ Redstone Wall ở góc.
 * Block do hitekalum.hjson tạo (GIỮ `type:`), Java vá lúc ContentInitEvent (bài học 29b).
 */
public final class Hitekalum {
    private Hitekalum() {}

    // {copper, lead, obsidis} / {titanium, thorium, obsidis} / {surge, phase, obsidis}
    static final int[] REQ_A = {1000, 1000, 375}, REQ_B = {1000, 500, 500}, REQ_C = {400, 400, 625};

    static final Color hitekalumColor = Color.valueOf("#ff1a1a");
    static final Color lightningColor = Color.valueOf("#ff3333");
    static final Color subLightningColor = Color.valueOf("#ff9999");

    static PowerTurret hitekalum;
    static Block redstoneWall;
    static BulletType bulletSystem;

    static boolean isEn() {
        String loc = String.valueOf(Core.settings.get("locale", ""));
        return loc.startsWith("en");
    }

    static final Effect arcmotLightningEffect = new Effect(14f, e -> {
        if (!(e.data instanceof Seq)) return;
        @SuppressWarnings("unchecked") Seq<Vec2> points = (Seq<Vec2>) e.data;
        float thickness = e.rotation > 0f ? e.rotation : 2.8f;
        Draw.color(lightningColor, Color.white, e.fin());
        Lines.stroke(thickness * e.fout());
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i), b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
    });

    static final Effect microLightningEffect = new Effect(8f, e -> {
        if (!(e.data instanceof Seq)) return;
        @SuppressWarnings("unchecked") Seq<Vec2> points = (Seq<Vec2>) e.data;
        Draw.color(subLightningColor, Color.white, e.fin());
        Lines.stroke(1f * e.fout());
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i), b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
    });

    static final Effect sonicShockwaveEffect = new Effect(45f, e -> {
        Draw.color(lightningColor);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 32f);
        Draw.alpha(e.fout() * 0.4f);
        Fill.circle(e.x, e.y, e.fin() * 20f);
        Draw.reset();
    });

    static final Effect mk3ExplosionFX = new Effect(30f, e -> {
        Draw.color(hitekalumColor, Color.white, e.fin());
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 45f);
        // Giữ nguyên lỗi JS: toạ độ y vẽ là `y + y` (bài học 43).
        Angles.randLenVectors(e.id, 20, 45f * e.fin(), (px, py) -> Fill.circle(e.x + px, py + py, 3.5f * e.fout()));
        Draw.reset();
    });

    static final Effect laserBeamEffect = new Effect(15f, e -> {
        if (!(e.data instanceof Vec2)) return;
        Vec2 t = (Vec2) e.data;
        Draw.color(hitekalumColor, Color.white, e.fin());
        Lines.stroke(4f * e.fout());
        Lines.line(e.x, e.y, t.x, t.y);
        Fill.circle(t.x, t.y, 6f * e.fout());
        Draw.reset();
    });

    static void createLightningStandard(float x1, float y1, float x2, float y2, float thickness, boolean isMicro) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(4, (int) Math.floor(dst / 7f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);
        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(x1, x2, t), py = Mathf.lerp(y1, y2, t);
            float jitter = isMicro ? 4f : 10f;
            float noise = Mathf.range(jitter) * (1f - t);
            Tmp.v1.trns(angle + 90f, noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));
        // Giữ nguyên lỗi JS: micro dùng (x2 + x2) / 2 làm toạ độ y (bài học 43).
        if (isMicro) microLightningEffect.at((x1 + x2) / 2f, (x2 + x2) / 2f, thickness, points);
        else arcmotLightningEffect.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, points);
    }

    static boolean isDead(Posc t) { return t instanceof Healthc && ((Healthc) t).dead(); }

    public static void init() {
        Log.info("HITEKALUM SYSTEM INITIALIZED - EVENT-BASED PERK SYSTEM READY");
        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        bulletSystem = new BulletType() {
            @Override public void init(Bullet b) { if (b != null) b.remove(); }
            @Override public void draw(Bullet b) {}
        };
        bulletSystem.speed = 0f;
        bulletSystem.lifetime = 1f;
        bulletSystem.collides = false;

        Block b = Vars.content.getByName(ContentType.block, "newex-hitekalum");
        redstoneWall = Vars.content.getByName(ContentType.block, "newex-redstone-wall");
        if (!(b instanceof PowerTurret)) return;
        hitekalum = (PowerTurret) b;
        hitekalum.shootType = bulletSystem;
        hitekalum.configurable = true;
        hitekalum.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof HitekalumBuild) ((HitekalumBuild) tile).setPerkData(value);
        });
        hitekalum.buildType = () -> new HitekalumBuild(hitekalum);
    }

    static Item obsidis() { return Vars.content.getByName(ContentType.item, "newex-obsidis"); }

    static boolean has(CoreBuild core, Item item, int amount) { return item != null && core.items.get(item) >= amount; }

    public static class HitekalumBuild extends PowerTurret.PowerTurretBuild {
        int perkTier1, perkTier2, perkCState, redstoneBuffStacks;
        float laserTimer;
        final IntSet executedTargets = new IntSet();

        public HitekalumBuild(PowerTurret outer) { outer.super(); }

        int checkRedstoneWallCorners() {
            if (redstoneWall == null) return 0;
            final IntSet found = new IntSet();
            float scanRadius = (block.size * Vars.tilesize / 2f) + 16f;
            Units.nearbyBuildings(x, y, scanRadius, b -> {
                if (b != null && b.block == redstoneWall && b.team == team) found.add(b.id);
            });
            return Math.min(4, found.size);
        }

        int getPerkA() { return perkTier1; }
        int getPerkB() { return perkTier2; }
        int getPerkC() { return perkCState; }

        void applyStatsFromPerk() {
                        // JS gán this.healthMultiplier nhưng Building v157 KHÔNG có trường này (chỉ Unit có) nên +HP của phúc lợi 1B/3C/4C chưa bao giờ có tác dụng (bài học 46). Giữ nguyên: không làm gì.
        }

        void setPerkData(int code) {
            if (code >= 30) perkCState = code - 30;
            else if (code >= 20) perkTier2 = code - 20;
            else if (code >= 10) perkTier1 = code - 10;
            applyStatsFromPerk();
        }

        // Giữ nguyên JS: config() trả A*100+B*10+C nhưng setPerkData lại đọc mốc 10/20/30 (bài học 44).
        @Override
        public Object config() { return Integer.valueOf(getPerkA() * 100 + getPerkB() * 10 + getPerkC()); }

        @Override
        public float range() {
            float rangeMult = 1f;
            if (getPerkA() == 1) rangeMult += 0.40f;
            if (getPerkC() == 3) rangeMult += 0.10f;
            if (getPerkC() == 4) rangeMult += 1f;
            if (redstoneBuffStacks > 0) rangeMult += 0.10f * redstoneBuffStacks;
            return 320f * rangeMult;
        }

        float getRawDamage() {
            float dmgMult = 1f;
            if (getPerkA() == 3) dmgMult += 0.30f;
            if (getPerkC() == 3) dmgMult += 0.10f;
            if (getPerkC() == 4) dmgMult += 1f;
            if (redstoneBuffStacks > 0) dmgMult += 0.50f * redstoneBuffStacks;
            float finalDmg = 720f * Math.max(0.1f, dmgMult);

            float critChance = 0.05f, critDmgMult = 1.50f;
            if (getPerkC() == 1) { critChance += 0.10f; critDmgMult += 1.50f; }
            if (getPerkC() == 3) { critChance += 0.20f; critDmgMult += 0.50f; }
            if (redstoneBuffStacks > 0) {
                critChance += 0.15f * redstoneBuffStacks;
                critDmgMult += 0.30f * redstoneBuffStacks;
            }
            if (Mathf.chance(critChance)) finalDmg *= critDmgMult;
            return finalDmg;
        }

        // JS: `typeof other.damage === "function"` trên Bullet là setter damage(float) → gán sát thương đạn = 90% máu tối đa
        // của pháo (sau khi pháo đã nhận đòn). Giữ nguyên hành vi; xem bài học 45.
        @Override
        public boolean collision(Bullet other) {
            boolean r = super.collision(other);
            if (getPerkB() == 1 && other != null && other.team != team) {
                other.damage(maxHealth * 0.9f);
                Fx.sparkShoot.at(other.x, other.y); // JS gọi Fx.spark (không tồn tại ở v157 → TypeError mỗi lần trúng đạn, bài học 45)
            }
            return r;
        }

        void applyHitekalumDamage(Posc tg, float baseDamage) {
            if (tg == null || isDead(tg)) return;
            // JS gọi hasEffect/apply trên mọi mục tiêu (sẽ lỗi khi mục tiêu là công trình); Java: công trình chỉ nhận sát thương.
            if (!(tg instanceof Unit)) {
                if (tg instanceof Healthc) ((Healthc) tg).damage(baseDamage);
                return;
            }
            final Unit targetUnit = (Unit) tg;
            float finalDmg = baseDamage;
            StatusEffect electrified = Vars.content.getByName(ContentType.status, "electrified");
            if (electrified == null) electrified = StatusEffects.shocked;

            if (targetUnit.hasEffect(electrified)) finalDmg *= (getPerkA() == 2) ? 4f : 2f;
            if (targetUnit.armor > 0f) targetUnit.armor = Math.max(0f, targetUnit.armor * 0.95f);

            targetUnit.damage(finalDmg);

            if (getPerkC() == 2) {
                targetUnit.apply(electrified, 60f * 5f);
                final float slowRange = 50f;
                Units.nearbyEnemies(team, targetUnit.x - slowRange, targetUnit.y - slowRange, slowRange * 2f, slowRange * 2f, u -> {
                    if (!u.dead && targetUnit.dst(u) <= slowRange) u.apply(StatusEffects.slow, 60f * 3f);
                });
            }
        }

        @Override
        protected void shoot(BulletType type) {
            final Posc tg = target;
            if (tg == null || isDead(tg)) return;
            float tx = tg.getX(), ty = tg.getY();
            float currentDmg = getRawDamage();

            createLightningStandard(x, y, tx, ty, 3.2f, false);
            sonicShockwaveEffect.at(tx, ty);
            mk3ExplosionFX.at(tx, ty);
            Effect.shake(3f, 3f, tx, ty);
            applyHitekalumDamage(tg, currentDmg);

            if (getPerkA() == 1) {
                Unit subTarget = Units.closestEnemy(team, tx, ty, 120f, u -> !u.dead && u != tg);
                if (subTarget != null) {
                    createLightningStandard(tx, ty, subTarget.x, subTarget.y, 2f, false);
                    applyHitekalumDamage(subTarget, currentDmg * 0.8f);
                }
            }

            if (getPerkB() == 2) {
                final float rangeVal = range();
                final Seq<Unit> list = new Seq<>();
                Units.nearbyEnemies(team, x - rangeVal, y - rangeVal, rangeVal * 2f, rangeVal * 2f, u -> {
                    if (!u.dead && dst(u) <= rangeVal) list.add(u);
                });
                list.sort(u -> -u.maxHealth);
                int count = 0;
                for (int i = 0; i < list.size; i++) {
                    if (count >= 3) break;
                    Unit tanky = list.get(i);
                    createLightningStandard(x, y, tanky.x, tanky.y, 2.5f, true);
                    applyHitekalumDamage(tanky, (currentDmg * 0.20f) + (tanky.maxHealth * 0.06f));
                    count++;
                }
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();
            redstoneBuffStacks = checkRedstoneWallCorners();
            if (power == null || power.status <= 0f) return;

            float reloadBonus = 0.05f * redstoneBuffStacks;
            if (getPerkB() == 3 && reloadCounter > 0f) reloadCounter += Time.delta * (1f + reloadBonus);
            else if (reloadBonus > 0f && reloadCounter > 0f) reloadCounter += Time.delta * reloadBonus;

            Posc tg = target;
            boolean isTargetValid = tg != null && !isDead(tg);
            final float currentRange = range();

            if (getPerkB() == 3 && isTargetValid && isShooting()) {
                laserTimer += Time.delta;
                if (laserTimer >= 90f) {
                    laserTimer = 0f;
                    float laserDmg = getRawDamage() * 1.50f;
                    laserBeamEffect.at(x, y, 0f, new Vec2(tg.getX(), tg.getY()));
                    applyHitekalumDamage(tg, laserDmg);
                    Effect.shake(4f, 4f, tg.getX(), tg.getY());
                }
            }

            Units.nearbyEnemies(team, x - currentRange, y - currentRange, currentRange * 2f, currentRange * 2f, u -> {
                if (!u.dead && dst(u) <= currentRange && (u.health / u.maxHealth) < 0.05f && !executedTargets.contains(u.id)) {
                    executedTargets.add(u.id);
                    if (getPerkC() == 4) {
                        createLightningStandard(x, y, u.x, u.y, 6f, false);
                        laserBeamEffect.at(x, y, 0f, new Vec2(u.x, u.y));
                        u.damage((720f * 50f) + (u.maxHealth * 0.3f));
                    } else {
                        createLightningStandard(x, y, u.x, u.y, 5f, false);
                        u.damage((720f * 10f) + (u.maxHealth * 0.3f));
                    }
                    mk3ExplosionFX.at(u.x, u.y);
                    Effect.shake(6f, 6f, u.x, u.y);
                }
            });
        }

        // ------------------------------------------------------------------ UI

        private String reqText() {
            CoreBuild core = team.core();
            if (core == null) return isEn() ? "[red]Team Core Not Found![]" : "[red]Không tìm thấy Lõi Đội![]";

            Item itemObsidis = obsidis();
            int cCop = core.items.get(Items.copper);
            int cLea = core.items.get(Items.lead);
            int cObs = itemObsidis != null ? core.items.get(itemObsidis) : 0;
            int cTit = core.items.get(Items.titanium);
            int cTho = core.items.get(Items.thorium);
            int cSur = core.items.get(Items.surgeAlloy);
            int cPha = core.items.get(Items.phaseFabric);

            String colCopA = cCop >= REQ_A[0] ? "[green]" : "[red]";
            String colLeaA = cLea >= REQ_A[1] ? "[green]" : "[red]";
            String colObsA = cObs >= REQ_A[2] ? "[green]" : "[red]";
            String colTitB = cTit >= REQ_B[0] ? "[green]" : "[red]";
            String colThoB = cTho >= REQ_B[1] ? "[green]" : "[red]";
            String colObsB = cObs >= REQ_B[2] ? "[green]" : "[red]";
            String colSurC = cSur >= REQ_C[0] ? "[green]" : "[red]";
            String colPhaC = cPha >= REQ_C[1] ? "[green]" : "[red]";
            String colObsC = cObs >= REQ_C[2] ? "[green]" : "[red]";

            return isEn() ?
                            "[gold]CORE RESOURCE REQUIREMENTS (HITEKALUM):[]\n" +
                            "[yellow]★ ROLL PERK A:[] Copper: " + colCopA + cCop + "[]/1000 | Lead: " + colLeaA + cLea + "[]/1000 | Obsidis: " + colObsA + cObs + "[]/375\n" +
                            "[cyan]★ ROLL PERK B:[] Titanium: " + colTitB + cTit + "[]/1000 | Thorium: " + colThoB + cTho + "[]/500 | Obsidis: " + colObsB + cObs + "[]/500\n" +
                            "[purple]★ ROLL PERK C:[] Surge: " + colSurC + cSur + "[]/400 | Phase: " + colPhaC + cPha + "[]/400 | Obsidis: " + colObsC + cObs + "[]/625" :
                            "[gold]YÊU CẦU TÀI NGUYÊN LÕI (HITEKALUM):[]\n" +
                            "[yellow]★ ROLL PHÚC LỢI A:[] Đồng: " + colCopA + cCop + "[]/1000 | Chì: " + colLeaA + cLea + "[]/1000 | Obsidis: " + colObsA + cObs + "[]/375\n" +
                            "[cyan]★ ROLL PHÚC LỢI B:[] Titan: " + colTitB + cTit + "[]/1000 | Thorium: " + colThoB + cTho + "[]/500 | Obsidis: " + colObsB + cObs + "[]/500\n" +
                            "[purple]★ ROLL PHÚC LỢI C:[] Surge: " + colSurC + cSur + "[]/400 | Phase: " + colPhaC + cPha + "[]/400 | Obsidis: " + colObsC + cObs + "[]/625";
        }

        /** Một hộp phúc lợi (A/B/C): đã có phúc lợi → hiện mô tả; chưa có → nút quay ngẫu nhiên tốn tài nguyên lõi. */
        private Table perkBox(final BaseDialog dialog, String header, int perk, String descText, String[] activeTxt, String btnLabel,
                              String notEnough, Boolp canPay, Runnable pay, Intp roll, final int cfgBase, Func<Integer, String> popTitle, String[] descMap) {
            Table box = new Table();
            box.background(Styles.black6);
            box.margin(12f);
            box.add(header).row();

            if (perk == 0) {
                Cell<Label> desc = box.add(descText);
                desc.width(340f).get().setWrap(true);
                desc.get().setAlignment(Align.left);
                box.row();
                box.button(btnLabel, () -> {
                    CoreBuild core = team.core();
                    if (core != null && canPay.get()) {
                        pay.run();
                        int res = roll.get();
                        NexNet.configure(this, Integer.valueOf(cfgBase + res));
                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(4f, 4f, x, y);
                        Vars.ui.showInfo(popTitle.get(res) + descMap[res]);
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(notEnough);
                    }
                }).size(280f, 40f);
            } else {
                Cell<Label> c = box.add(activeTxt[perk]);
                c.width(340f).get().setWrap(true);
                c.get().setAlignment(Align.left);
            }
            return box;
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(isEn() ? "Hitekalum Turret Upgrade Center" : "Trung tâm nâng cấp pháo Hitekalum");

            Cell<Label> reqCell = dialog.cont.add(reqText());
            reqCell.width(380f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table mainTable = new Table();

            String[] enA = {"", "• Range +40%\n• Chain lightning jumps to 1 nearby target.", "• Save 50% power consumption.\n• Increase damage to Electrified targets from x2 -> x4.", "• +30% Base Damage\n• Attacks ignore 100% armor."};
            String[] viA = {"", "• Tầm bắn +40%\n• Đạn giật điện lây sang 1 mục tiêu gần đó.", "• Tiết kiệm 50% điện năng tiêu thụ.\n• Tăng sát thương gây ra lên mục tiêu bị Nhiễm Điện từ x2 -> x4.", "• +30% Sát thương gốc\n• Sát thương bỏ qua 100% giáp kẻ địch."};
            String[] actA = {"", isEn() ? "[green]✔ ACTIVATED: PERK 1A\n• Range +40%\n• Chain lightning jumps to 1 target[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1A\n• Tầm bắn +40%\n• Đạn giật điện lây sang 1 mục tiêu gần đó[]", isEn() ? "[green]✔ ACTIVATED: PERK 2A\n• Saves 50% power\n• x4 Damage to Electrified enemies[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2A\n• Tiết kiệm 50% điện tiêu thụ\n• x4 Sát thương lên kẻ địch Nhiễm Điện[]", isEn() ? "[green]✔ ACTIVATED: PERK 3A\n• +30% Damage\n• 100% Armor Penetration[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3A\n• +30% Sát thương\n• Sát thương xuyên giáp 100%[]"};
            mainTable.add(perkBox(dialog, isEn() ? "[yellow]★ ROLL PERK A (RANDOM) ★[]" : "[yellow]★ ROLL PHÚC LỢI A (NGẪU NHIÊN) ★[]", getPerkA(), isEn() ?
                            "Activate random upgrade to receive 1 of 3 Perk A bonuses:\n" +
                            " • [yellow]Perk 1A:[] Range +40%, lightning chain hits 1 extra target.\n" +
                            " • [yellow]Perk 2A:[] Save 50% power, 4x Damage against Electrified targets.\n" +
                            " • [yellow]Perk 3A:[] +30% Damage, ignores 100% armor." :
                            "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi A:\n" +
                            " • [yellow]Phúc lợi 1A:[] Tầm bắn +40%, đạn giật điện lây sang 1 mục tiêu.\n" +
                            " • [yellow]Phúc lợi 2A:[] Tiết kiệm 50% điện năng, x4 Sát thương lên mục tiêu Nhiễm Điện.\n" +
                            " • [yellow]Phúc lợi 3A:[] +30% Sát thương, gây dmg xuyên giáp 100%.", actA, isEn() ? "[yellow]ROLL PERK A[]" : "[yellow]QUAY PHÚC LỢI A[]", isEn() ? "[red]Not enough resources in Core to roll Perk A![]" : "[red]Không đủ tài nguyên trong Lõi để roll Phúc lợi A![]",
                () -> { Item o = obsidis(); CoreBuild c = team.core(); return has(c, Items.copper, REQ_A[0]) && has(c, Items.lead, REQ_A[1]) && has(c, o, REQ_A[2]); },
                () -> { CoreBuild c = team.core(); NexNet.take(c, Items.copper, REQ_A[0]); NexNet.take(c, Items.lead, REQ_A[1]); NexNet.take(c, obsidis(), REQ_A[2]); },
                () -> Mathf.rand.nextInt(3) + 1, 10, res -> isEn() ? "[gold]YOU ROLLED:[]\n[yellow]PERK " + res + "A[]\n\n[white]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[yellow]PHÚC LỢI " + res + "A[]\n\n[white]", isEn() ? enA : viA)).width(360f).row();
            mainTable.add().height(12f).row();

            String[] enB = {"", "• +200% Turret HP\n• Reflects 15% melee damage.", "• Fires 3 extra lightning arcs at targets with highest HP\n• Damage: 20% Base Dmg + 1% Target Max HP.", "• Main arc fire rate: x2 (1s -> 0.5s/shot)\n• Secondary laser deals 150% Dmg every 1.5s."};
            String[] viB = {"", "• Máu pháo +200%\n• Tạo khiên phản 15% sát thương cận chiến.", "• Bắn thêm 3 tia điện tới 3 kẻ địch có Máu lớn nhất\n• Sát thương: 20% Dmg gốc + 1% Max HP mục tiêu.", "• Tốc độ bắn tia điện chính: x2 (1s -> 0.5s/bắn)\n• Đòn đánh Laser phụ gây 150% Dmg mỗi 1.5 giây."};
            String[] actB = {"", isEn() ? "[green]✔ ACTIVATED: PERK 1B\n• +200% HP\n• Reflects 15% melee damage[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1B\n• Máu +200%\n• Phản 15% sát thương cận chiến[]", isEn() ? "[green]✔ ACTIVATED: PERK 2B\n• Fires 3 arcs to tankiest targets\n• Deals 20% Base Dmg + 1% Max HP[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2B\n• Bắn 3 tia điện tới 3 mục tiêu trâu nhất\n• Gây 20% Dmg gốc + 1% Max HP[]", isEn() ? "[green]✔ ACTIVATED: PERK 3B\n• Arc fire rate: x2 (0.5s/shot)\n• Secondary Laser deals 150% Dmg every 1.5s[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3B\n• Tốc độ bắn tia điện: x2 (0.5s/bắn)\n• Đòn Laser phụ 150% Dmg mỗi 1.5s[]"};
            mainTable.add(perkBox(dialog, isEn() ? "[cyan]★ ROLL PERK B (RANDOM) ★[]" : "[cyan]★ ROLL PHÚC LỢI B (NGẪU NHIÊN) ★[]", getPerkB(), isEn() ?
                            "Activate random upgrade to receive 1 of 3 Perk B bonuses:\n" +
                            " • [cyan]Perk 1B:[] +200% Max HP, reflects 15% melee damage.\n" +
                            " • [cyan]Perk 2B:[] Fires 3 additional lightning arcs at the 3 tankiest targets (20% Base Dmg + 1% Max HP).\n" +
                            " • [cyan]Perk 3B:[] 2x Fire rate, secondary laser attack deals 150% dmg every 1.5s." :
                            "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 3 phúc lợi B:\n" +
                            " • [cyan]Phúc lợi 1B:[] +200% Máu pháo, phản 15% sát thương cận chiến.\n" +
                            " • [cyan]Phúc lợi 2B:[] Bắn thêm 3 tia điện đến 3 mục tiêu trâu nhất (20% Dmg gốc + 1% Max HP).\n" +
                            " • [cyan]Phúc lợi 3B:[] Tăng x2 tốc độ bắn tia điện, đòn laser 150% dmg mỗi 1.5s.", actB, isEn() ? "[cyan]ROLL PERK B[]" : "[cyan]QUAY PHÚC LỢI B[]", isEn() ? "[red]Not enough resources in Core to roll Perk B![]" : "[red]Không đủ tài nguyên trong Lõi để roll Phúc lợi B![]",
                () -> { Item o = obsidis(); CoreBuild c = team.core(); return has(c, Items.titanium, REQ_B[0]) && has(c, Items.thorium, REQ_B[1]) && has(c, o, REQ_B[2]); },
                () -> { CoreBuild c = team.core(); NexNet.take(c, Items.titanium, REQ_B[0]); NexNet.take(c, Items.thorium, REQ_B[1]); NexNet.take(c, obsidis(), REQ_B[2]); },
                () -> Mathf.rand.nextInt(3) + 1, 20, res -> isEn() ? "[gold]YOU ROLLED:[]\n[cyan]PERK " + res + "B[]\n\n[white]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[cyan]PHÚC LỢI " + res + "B[]\n\n[white]", isEn() ? enB : viB)).width(360f).row();
            mainTable.add().height(12f).row();

            String[] enC = {"", "• Crit Damage +150%\n• Crit Chance +10%.", "• Applies Electrified status to target\n• Shockwave slows enemies by 80% within 50px radius.", "• +10% Dmg, Range, HP\n• +20% Crit Rate, +50% Crit Damage.", "• +100% Dmg, Range, HP\n• Executes enemies <5% HP with Lightning + Laser (5000% Dmg + 5% max HP)."};
            String[] viC = {"", "• Sát thương bạo kích +150%\n• Tỉ lệ bạo kích +10%.", "• Đòn đánh gắn ấn Nhiễm Điện (Electrified) cho mục tiêu\n• Sóng xung kích làm chậm 80% kẻ địch trong phạm vi 50px.", "• +10% Dmg, Phạm vi, Máu\n• +20% Tỉ lệ bạo kích, +50% Sát thương bạo kích.", "• +100% Dmg, Phạm vi, Máu\n• Kết liễu địch <5% HP bằng Sét + Laser (5000% Dmg + 5% max HP)."};
            String[] actC = {"", isEn() ? "[green]✔ ACTIVATED: PERK 1C\n• Crit Damage +150%\n• Crit Rate +10%[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1C\n• Sát thương bạo kích +150%\n• Tỉ lệ bạo kích +10%[]", isEn() ? "[green]✔ ACTIVATED: PERK 2C\n• Applies Electrified status\n• Shockwave 80% slow around 50px[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2C\n• Gắn ấn Nhiễm Điện cho mục tiêu\n• Xung kích làm chậm 80% xung quanh 50px[]", isEn() ? "[green]✔ ACTIVATED: PERK 3C\n• +10% Dmg, Range, HP\n• +20% Crit Rate, +50% Crit Dmg[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3C\n• +10% Dmg, Phạm vi, Máu\n• +20% Tỉ lệ bạo kích, +50% Sát thương bạo kích[]", isEn() ? "[green]✔ ACTIVATED: PERK 4C\n• +100% Dmg, Range, HP\n• Super Lightning + Laser Execution (5000% Dmg + 5% max HP)[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 4C\n• +100% Dmg, Phạm vi, Máu\n• Siêu đòn kết liễu Sét + Laser (5000% Dmg + 5% max HP)[]"};
            mainTable.add(perkBox(dialog, isEn() ? "[purple]★ ROLL PERK C (RANDOM) ★[]" : "[purple]★ ROLL PHÚC LỢI C (NGẪU NHIÊN) ★[]", getPerkC(), isEn() ?
                            "Activate random upgrade to receive 1 of 4 Perk C bonuses:\n" +
                            " • [purple]Perk 1C (40%):[] Crit Damage +150%, Crit Chance +10%.\n" +
                            " • [purple]Perk 2C (30%):[] Applies Electrified status, shockwave slows 80% around 50px.\n" +
                            " • [purple]Perk 3C (20%):[] +10% Dmg, Range, HP | +20% Crit Rate, +50% Crit Dmg.\n" +
                            " • [purple]Perk 4C (10%):[] +100% Dmg, Range, HP. Super Lightning + Laser execute when enemy <5% HP." :
                            "Kích hoạt nâng cấp ngẫu nhiên nhận 1 trong 4 phúc lợi C:\n" +
                            " • [purple]Phúc lợi 1C (40%):[] Sát thương bạo kích +150%, Tỉ lệ bạo kích +10%.\n" +
                            " • [purple]Phúc lợi 2C (30%):[] Gắn ấn Nhiễm Điện lên kẻ địch, xung kích làm chậm 80% xung quanh 50px.\n" +
                            " • [purple]Phúc lợi 3C (20%):[] +10% Dmg, Range, HP | +20% Crit Rate, +50% Crit Dmg.\n" +
                            " • [purple]Phúc lợi 4C (10%):[] +100% Dmg, Range, HP. Siêu đòn kết liễu Sét + Laser khi địch <5% HP.", actC, isEn() ? "[purple]ROLL PERK C[]" : "[purple]QUAY PHÚC LỢI C[]", isEn() ? "[red]Not enough resources in Core to roll Perk C![]" : "[red]Không đủ tài nguyên trong Lõi để roll Phúc lợi C![]",
                () -> { Item o = obsidis(); CoreBuild c = team.core(); return has(c, Items.surgeAlloy, REQ_C[0]) && has(c, Items.phaseFabric, REQ_C[1]) && has(c, o, REQ_C[2]); },
                () -> { CoreBuild c = team.core(); NexNet.take(c, Items.surgeAlloy, REQ_C[0]); NexNet.take(c, Items.phaseFabric, REQ_C[1]); NexNet.take(c, obsidis(), REQ_C[2]); },
                () -> { int chance = Mathf.rand.nextInt(100); return chance < 40 ? 1 : (chance < 70 ? 2 : (chance < 90 ? 3 : 4)); },
                30, res -> isEn() ? "[gold]YOU ROLLED:[]\n[purple]PERK " + res + "C[]\n\n[white]" : "[gold]BẠN ĐÃ ROLL TRÚNG:[]\n[purple]PHÚC LỢI " + res + "C[]\n\n[white]", isEn() ? enC : viC)).width(360f);

            ScrollPane scroll = new ScrollPane(mainTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = isEn() ? " Hitekalum Turret Stats " : " Thông số pháo Hitekalum ";
            int curHp = Math.round(health);
            int maxHp = Math.round(maxHealth);
            int curRng = Math.round(range() / 8f);
            int curDmg = Math.round(getRawDamage());

            String redstoneStatus = isEn() ?
                        ((this.redstoneBuffStacks > 0)
                            ? "[green]✔ REDSTONE WALL ACTIVATED (Stacks: " + this.redstoneBuffStacks + "/4 corners)[]\n  └ (Per stack: +10% Range, +5% Fire Rate, +15% Crit Rate, +30% Crit Dmg, +50% Dmg)"
                            : "[red]✘ No Redstone Wall placed at corners[]") :
                        ((this.redstoneBuffStacks > 0)
                            ? "[green]✔ ĐÃ KÍCH HOẠT KHỐI REDSTONE WALL (Cộng dồn: " + this.redstoneBuffStacks + "/4 góc)[]\n  └ (Mỗi stack: +10% Range, +5% Tốc bắn, +15% Crit Rate, +30% Crit Dmg, +50% Dmg)"
                            : "[red]✘ Chưa đặt khối Redstone Wall ở góc nào[]");

            String descStr = isEn() ?
                        "[gold]⚡ CURRENT STATS TABLE ⚡[]\n" +
                        "• [white]Base HP:[] [green]" + curHp + "/" + maxHp + " HP[]\n" +
                        "• [white]Range:[] [cyan]" + curRng + " Tiles[]\n" +
                        "• [white]Base Damage:[] [orange]" + curDmg + " Dmg[]\n" +
                        "• [white]Corner Redstone Wall:[] " + redstoneStatus + "\n" +
                        "• [white]Passive Traits:[] [lightgray]5% Crit Rate, 50% Crit Dmg, Hits reduce target armor by 5%[]\n" +
                        "• [white]Electrified Passive:[] [lightgray]Deals x2 Dmg (x4 with Perk 2A) against Electrified targets[]\n\n" +
                        "[gold]PERK UPGRADE STATUS:[]" :
                        "[gold]⚡ BẢNG THÔNG SỐ HIỆN TẠI ⚡[]\n" +
                        "• [white]Máu cơ bản:[] [green]" + curHp + "/" + maxHp + " HP[]\n" +
                        "• [white]Tầm bắn:[] [cyan]" + curRng + " Ô (Tiles)[]\n" +
                        "• [white]Sát thương cơ bản:[] [orange]" + curDmg + " Dmg[]\n" +
                        "• [white]Tường Redstone 4 góc:[] " + redstoneStatus + "\n" +
                        "• [white]Nội tại cố định:[] [lightgray]5% Tỉ lệ Bạo kích, 50% Dmg Bạo kích, Trúng đạn giảm 5% giáp địch[]\n" +
                        "• [white]Nội tại Nhiễm điện:[] [lightgray]Gây x2 Dmg (x4 nếu có Phúc lợi 2A) khi đánh kẻ địch bị Nhiễm Điện[]\n\n" +
                        "[gold]TRẠNG THÁI NÂNG CẤP PHÚC LỢI:[]";

            int perkA = getPerkA(), perkB = getPerkB(), perkC = getPerkC();

            if (perkA > 0) {
                descStr += isEn() ? "\n\n[yellow]★ PERK A:[] " : "\n\n[yellow]★ PHÚC LỢI A:[] ";
                if (perkA == 1) descStr += isEn() ? "[green]Perk 1A[]\n  └ Range +40%, Chain lightning jumps to 1 target." : "[green]Phúc lợi 1A[]\n  └ Tầm bắn +40%, Đạn giật điện lây sang 1 mục tiêu.";
                if (perkA == 2) descStr += isEn() ? "[green]Perk 2A[]\n  └ Saves 50% power, x4 Dmg to Electrified targets." : "[green]Phúc lợi 2A[]\n  └ Tiết kiệm 50% điện, x4 Dmg lên kẻ địch Nhiễm Điện.";
                if (perkA == 3) descStr += isEn() ? "[green]Perk 3A[]\n  └ Damage +30%, Ignores 100% armor." : "[green]Phúc lợi 3A[]\n  └ Sát thương +30%, Bỏ qua 100% giáp.";
            } else {
                descStr += isEn() ? "\n\n[yellow]★ PERK A:[] [lightgray]Inactive[]" : "\n\n[yellow]★ PHÚC LỢI A:[] [lightgray]Chưa kích hoạt[]";
            }

            if (perkB > 0) {
                descStr += isEn() ? "\n\n[cyan]★ PERK B:[] " : "\n\n[cyan]★ PHÚC LỢI B:[] ";
                if (perkB == 1) descStr += isEn() ? "[green]Perk 1B[]\n  └ HP +200%, Reflects 15% melee damage." : "[green]Phúc lợi 1B[]\n  └ Máu +200%, Phản 15% sát thương cận chiến.";
                if (perkB == 2) descStr += isEn() ? "[green]Perk 2B[]\n  └ Fires 3 arcs to tankiest enemies (20% Dmg + 1% Max HP)." : "[green]Phúc lợi 2B[]\n  └ Bắn 3 tia điện đến 3 kẻ địch trâu nhất (20% Dmg + 1% Max HP).";
                if (perkB == 3) descStr += isEn() ? "[green]Perk 3B[]\n  └ Arc fire rate: x2 (0.5s/shot), Laser 150% Dmg every 1.5s." : "[green]Phúc lợi 3B[]\n  └ Tốc độ bắn tia điện: x2 (0.5s/bắn), Laser 150% Dmg mỗi 1.5s.";
            } else {
                descStr += isEn() ? "\n\n[cyan]★ PERK B:[] [lightgray]Inactive[]" : "\n\n[cyan]★ PHÚC LỢI B:[] [lightgray]Chưa kích hoạt[]";
            }

            if (perkC > 0) {
                descStr += isEn() ? "\n\n[purple]★ PERK C:[] " : "\n\n[purple]★ PHÚC LỢI C:[] ";
                if (perkC == 1) descStr += isEn() ? "[green]Perk 1C[]\n  └ Crit Dmg +150%, Crit Rate +10%." : "[green]Phúc lợi 1C[]\n  └ Crit Dmg +150%, Crit Rate +10%.";
                if (perkC == 2) descStr += isEn() ? "[green]Perk 2C[]\n  └ Applies Electrified, 80% slow shockwave within 50px." : "[green]Phúc lợi 2C[]\n  └ Gắn ấn Nhiễm Điện, Xung kích làm chậm 80% xung quanh 50px.";
                if (perkC == 3) descStr += isEn() ? "[green]Perk 3C[]\n  └ +10% All Stats, +20% Crit Rate, +50% Crit Dmg." : "[green]Phúc lợi 3C[]\n  └ +10% All Stats, +20% Crit Rate, +50% Crit Dmg.";
                if (perkC == 4) descStr += isEn() ? "[green]Perk 4C[]\n  └ +100% All Stats, Super Lightning + Laser execute." : "[green]Phúc lợi 4C[]\n  └ +100% All Stats, Siêu đòn kết liễu Sét + Laser.";
            } else {
                descStr += isEn() ? "\n\n[purple]★ PERK C:[] [lightgray]Inactive[]" : "\n\n[purple]★ PHÚC LỢI C:[] [lightgray]Chưa kích hoạt[]";
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
            table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog).size(50f, 40f).tooltip(isEn() ? "Hitekalum Upgrade Center" : "Trung tâm nâng cấp pháo Hitekalum");
            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog).size(50f, 40f).tooltip(isEn() ? "View Hitekalum turret stats" : "Xem thông số pháo Hitekalum");
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getPerkA());
            write.b(getPerkB());
            write.b(getPerkC());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            perkTier1 = read.b();
            perkTier2 = read.b();
            perkCState = read.b();
            applyStatsFromPerk();
        }
    }
}
