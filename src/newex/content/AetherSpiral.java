package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BuildVisibility;
import newex.DamageDisplay;
import newex.PerfCore;

import java.util.ArrayList;

/**
 * Chuyển từ aether-spiral.js. Pháo cỡ lớn (block do aether-spiral.hjson tạo, kiểu b - bài 29b): Java chỉ vá lúc ContentInitEvent.
 * 4 ô trang bị (cùng hệ Endyr), bắn 5 loạt thường (4 ngôi sao góc) -> 3 phát sạc State2 -> 4 phát State3.
 * Giới hạn số pháo = số lõi của đội.
 */
public final class AetherSpiral {
    private AetherSpiral() {}

    static final float GOLDEN_RATIO = 1.61803398875f, GOLDEN_ANGLE_DEG = 137.507764f;
    static ItemTurret aether;
    static BulletType[] normalPool;
    static BulletType darkVoidBullet, chargedState2, chargedState3;

    static boolean isEn() {
        String loc = Core.settings.getString("locale");
        return loc != null && loc.startsWith("en");
    }

    /** ItemStats của JS (chỉ trường desc dùng). Null nếu không có. */
    static String statDesc(String clean) {
        boolean en = isEn();
        switch (clean) {
            case "copper": return en ? "[stat]+5% Dmg[]" : "[stat]+5% Sát thương[]";
            case "silicon": return en ? "[stat]+5% Fire Rate[]" : "[stat]+5% Tốc độ bắn[]";
            case "thorium": return en ? "[stat]+5% Range[]" : "[stat]+5% Tầm bắn[]";
            case "sallowyr": return en ? "[stat]+1 Extra Bullet per shot[]" : "[stat]+1 Đạn mỗi lần bắn[]";
            case "obsidis": return en ? "[stat]+10% Dmg/FireRate, +50% Range[]" : "[stat]+10% TC/TĐ, +50% Tầm bắn[]";
            case "starlight": return en ? "[stat]+5% Crit | +15% CritDmg\nExecute targets below 5% HP (+1000 Copper)[]" : "[stat]+5% Bạo | +15% ST Bạo\nKết liễu mục tiêu <5% HP (+1000 Đồng)[]";
            default: return null;
        }
    }

    // ---------------------------------------------------------------- hiệu ứng
    static final Effect absorbEnergyFx = new Effect(20f, e -> {
        Draw.color(Color.cyan, Color.white, e.fout());
        Lines.stroke(1.8f * e.fout());
        Mathf.rand.setSeed(e.id);
        float len = Mathf.rand.random(4f, 12f);
        float angle = Mathf.rand.random(360f); // bài 20: Rhino chọn overload float
        Lines.lineAngle(e.x, e.y, angle, len * e.fin());
        Fill.circle(e.x, e.y, 1.5f * e.fout());
    });

    static final Effect muzzleSparkFx = new Effect(15f, e -> {
        Draw.color(Color.white, Color.cyan, e.fout());
        Lines.stroke(1.5f * e.fout());
        Mathf.rand.setSeed(e.id);
        for (int i = 0; i < 3; i++) {
            float len = Mathf.rand.random(6f, 16f) * e.fin();
            float ang = e.rotation + Mathf.rand.range(20f);
            Lines.lineAngle(e.x, e.y, ang, len);
        }
    });

    static final Effect customShootFx = new Effect(20f, e -> {
        Draw.color(Color.cyan, Color.white, e.fout());
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, 18f * e.fin());
        Lines.lineAngle(e.x, e.y, e.rotation, 15f * e.fout());
    });

    static final Effect acidCorrosionEffect = new Effect(30f, e -> {
        Draw.color(PerfCore.color("#a3e635"), PerfCore.color("#65a30d"), e.fin());
        Lines.stroke(e.fout() * 2f);
        Mathf.rand.setSeed(e.id);
        for (int i = 0; i < 5; i++) {
            float len = Mathf.rand.random(2f, 14f) * e.fin();
            float angle = Mathf.rand.random(360f);
            float size = Mathf.rand.random(1f, 4f) * e.fout();
            Lines.circle(e.x + Angles.trnsx(angle, len), e.y + Angles.trnsy(angle, len), size);
        }
    });

    static final Effect mergeEnergyFx = new Effect(35f, e -> {
        Draw.color(Color.cyan, Color.white, e.fout());
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, 32f * e.fin());
        Fill.circle(e.x, e.y, 12f * e.fout());
        for (int i = 0; i < 6; i++) {
            float ang = i * 60f + Mathf.randomSeed(e.id + i, 0f, 360f);
            float len = 20f * e.fin();
            Lines.lineAngle(e.x + Mathf.cosDeg(ang) * len, e.y + Mathf.sinDeg(ang) * len, ang, 8f * e.fout());
        }
    });

    /** Vẽ một elip xoay (24 đoạn) – dùng chung cho 2 sóng xung kích. */
    static void ellipseLine(float cx, float cy, float rx, float ry, float cosA, float sinA) {
        int steps = 24;
        float lastX = 0f, lastY = 0f;
        for (int i = 0; i <= steps; i++) {
            float angle = (i * (360f / steps)) * Mathf.degRad;
            float lx = (float) Math.cos(angle) * rx;
            float ly = (float) Math.sin(angle) * ry;
            float px = cx + (lx * cosA - ly * sinA);
            float py = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, px, py);
            lastX = px; lastY = py;
        }
    }

    static final Effect distortedShockwaveFx = new Effect(35f, e -> {
        float fin = e.fin(), fout = e.fout();
        float rad = e.rotation * Mathf.degRad;
        float cosA = (float) Math.cos(rad), sinA = (float) Math.sin(rad);
        Draw.color(Color.white, Color.cyan, fin);
        Lines.stroke(3.5f * fout);
        for (int layer = 0; layer < 2; layer++) {
            float layerOffset = layer * 12f;
            float radiusX = (fin * 25f) + layerOffset * 0.2f;
            float radiusY = (fin * 65f) + layerOffset * 0.5f;
            float offset = (fin * 50f) + layerOffset;
            ellipseLine(e.x + cosA * offset, e.y + sinA * offset, radiusX, radiusY, cosA, sinA);
        }
        Draw.reset();
    });

    static final Effect state3ShockwaveFx = new Effect(30f, e -> {
        float fin = e.fin(), fout = e.fout();
        float rad = e.rotation * Mathf.degRad;
        float cosA = (float) Math.cos(rad), sinA = (float) Math.sin(rad);
        Draw.color(Color.white, Color.cyan, fin);
        Lines.stroke(3.0f * fout);
        float offset = (1f - fin) * 60f;
        ellipseLine(e.x + cosA * offset, e.y + sinA * offset, fin * 30f, fin * 70f, cosA, sinA);
        Draw.reset();
    });

    static final Effect lightningAroundTurretFx = new Effect(30f, e -> {
        Draw.color(Color.cyan, Color.white, e.fout());
        Lines.stroke(2.5f * e.fout());
        Mathf.rand.setSeed(e.id);
        for (int i = 0; i < 8; i++) {
            float angle = i * 45f + Mathf.rand.random(-15f, 15f);
            float len = Mathf.rand.random(30f, 70f);
            float px = e.x + Angles.trnsx(angle, len * e.fin());
            float py = e.y + Angles.trnsy(angle, len * e.fin());
            Lines.lineAngle(e.x, e.y, angle, len * e.fin());
            Fill.circle(px, py, 3f * e.fout());
        }
    });

    static final Effect chargeHitFx = new Effect(45f, e -> {
        Draw.color(Color.cyan, Color.white, e.fout());
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, 60f * e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 35f * e.fin());
        for (int i = 0; i < 16; i++) {
            float ang = i * 22.5f + Mathf.randomSeed(e.id + i, 0f, 360f);
            float len = 48f * e.fin();
            Fill.circle(e.x + Mathf.cosDeg(ang) * len, e.y + Mathf.sinDeg(ang) * len, 5f * e.fout());
        }
    });

    static final Effect darkVoidHitFx = new Effect(30f, e -> {
        Draw.color(PerfCore.color("#2e1065"), PerfCore.color("#a855f7"), e.fin());
        Lines.stroke(3f * e.fout());
        Lines.circle(e.x, e.y, 25f * e.fin());
        Fill.circle(e.x, e.y, 8f * e.fout());
    });

    /** Ngôi sao bất đối xứng: 8 đỉnh, tam giác nền (backColor) rồi tam giác thu 65% (frontColor). */
    static void drawAsymmetricStar(float x, float y, float rotation, float scale, Color frontColor, Color backColor) {
        float rad = rotation * Mathf.degRad;
        float cos = Mathf.cos(rad), sin = Mathf.sin(rad);
        float[][] pts = {
            {20f * scale, 0f}, {2f * scale, 3f * scale}, {0f, 7f * scale}, {-2f * scale, 3f * scale},
            {-8f * scale, 0f}, {-2f * scale, -3f * scale}, {0f, -7f * scale}, {2f * scale, -3f * scale}
        };
        int n = pts.length;
        float[] wx = new float[n], wy = new float[n];
        for (int i = 0; i < n; i++) {
            wx[i] = x + pts[i][0] * cos - pts[i][1] * sin;
            wy[i] = y + pts[i][0] * sin + pts[i][1] * cos;
        }
        Draw.color(backColor);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Fill.tri(x, y, wx[i], wy[i], wx[j], wy[j]);
        }
        Draw.color(frontColor);
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Fill.tri(x, y, Mathf.lerp(x, wx[i], 0.65f), Mathf.lerp(y, wy[i], 0.65f),
                           Mathf.lerp(x, wx[j], 0.65f), Mathf.lerp(y, wy[j], 0.65f));
        }
    }

    // ---------------------------------------------------------------- sát thương
    static float dmgMult(Object owner) { return owner instanceof AetherBuild ? ((AetherBuild) owner).getDamageMultiplier() : 1f; }

    static void applyDamageWithStarlight(Object owner, Unit target, float baseDamage) {
        if (target == null || target.dead) return;
        float finalDamage = baseDamage;
        AetherBuild ab = owner instanceof AetherBuild ? (AetherBuild) owner : null;
        if (ab != null && Mathf.chance(ab.getCritChance())) {
            finalDamage *= ab.getCritMultiplier();
            DamageDisplay.crit(target); // global.newexCrit
        }
        target.damage(finalDamage);
        if (ab != null && ab.hasStarlightPassive()) {
            if (target.health / target.maxHealth <= 0.05f) {
                target.kill();
                CoreBuild core = ab.team.core();
                if (core != null) {
                    Item copper = Vars.content.getByName(ContentType.item, "copper");
                    if (copper == null) copper = Items.copper;
                    core.items.add(copper, 1000);
                }
            }
        }
    }

    // ---------------------------------------------------------------- đạn
    /** Sao thường (7 biến thể màu/trạng thái). Giá trị đặt trong init() như JS (sau super.init()). */
    static final class StarBullet extends BasicBulletType {
        final Color front, back; final Effect hitEf; final StatusEffect statusEff;
        StarBullet(Color front, Color back, Effect hitEf, StatusEffect statusEff) {
            this.front = front; this.back = back; this.hitEf = hitEf; this.statusEff = statusEff;
        }
        @Override public void init() {
            super.init();
            speed = 5.5f; damage = 215f; lifetime = 65f;
            hitEffect = hitEf; despawnEffect = hitEf;
            status = statusEff; statusDuration = 140f;
            lightColor = front; lightOpacity = 0.7f; lightRadius = 25f;
            homingPower = 0.08f; homingRange = 220f; drawSize = 25f;
        }
        @Override public void update(Bullet b) {
            super.update(b);
            float curveStrength = 3.2f * (1f - (b.time / b.lifetime));
            float waveOffset = Mathf.sin(b.time * 0.25f + (b.id % 8) * GOLDEN_RATIO) * curveStrength;
            float currentAngle = b.rotation();
            b.vel.setAngle(Mathf.slerp(currentAngle, currentAngle + waveOffset, 0.4f));
        }
        @Override public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (b != null && b.owner != null && other instanceof Unit)
                applyDamageWithStarlight(b.owner, (Unit) other, damage * dmgMult(b.owner));
        }
        @Override public void draw(Bullet b) { drawAsymmetricStar(b.x, b.y, b.rotation(), 0.8f, front, back); }
    }

    /** Sao Đen Tím (10% mỗi viên): thêm 50% máu tối đa của mục tiêu. */
    static final class DarkVoidBullet extends BasicBulletType {
        @Override public void init() {
            super.init();
            speed = 6f; damage = 215f; lifetime = 65f;
            hitEffect = darkVoidHitFx; despawnEffect = darkVoidHitFx;
            status = StatusEffects.unmoving; statusDuration = 60f;
            lightColor = Color.valueOf("#a855f7"); lightOpacity = 0.9f; lightRadius = 30f;
            homingPower = 0.12f; homingRange = 250f; drawSize = 30f;
        }
        @Override public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other instanceof Unit) {
                Unit u = (Unit) other;
                if (u.isValid() && !u.dead) {
                    u.damage(u.maxHealth * 0.50f);
                    darkVoidHitFx.at(u.x, u.y);
                }
                if (b != null && b.owner != null) applyDamageWithStarlight(b.owner, u, damage * dmgMult(b.owner));
            }
        }
        @Override public void draw(Bullet b) {
            drawAsymmetricStar(b.x, b.y, b.rotation(), 1f, Color.valueOf("#18002e"), Color.valueOf("#7e22ce"));
        }
    }

    /** Đạn sạc State2 (1075) và State3 (1827.5): chỉ khác sát thương. */
    static final class ChargedBullet extends BasicBulletType {
        final float dmg;
        ChargedBullet(float dmg) { this.dmg = dmg; }
        @Override public void init() {
            super.init();
            speed = 8f; damage = dmg; lifetime = 50f;
            splashDamage = 450f; splashDamageRadius = 40f;
            hitEffect = chargeHitFx; despawnEffect = chargeHitFx;
            pierce = true; pierceCap = 3;
            status = StatusEffects.shocked; statusDuration = 120f;
            lightColor = Color.cyan; lightOpacity = 0.9f; lightRadius = 50f; drawSize = 40f;
        }
        @Override public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (b != null && b.owner != null && other instanceof Unit)
                applyDamageWithStarlight(b.owner, (Unit) other, damage * dmgMult(b.owner));
        }
        @Override public void draw(Bullet b) { drawAsymmetricStar(b.x, b.y, b.rotation(), 1.5f, Color.white, Color.cyan); }
    }

    // ---------------------------------------------------------------- khởi tạo
    /** Gọi trong NexMod.loadContent(): tạo đạn lúc nạp nội dung để init() của chúng được game gọi (như JS tạo ở thời điểm nạp script). */
    public static void create() {
        normalPool = new BulletType[] {
            new StarBullet(Color.valueOf("#ffab40"), Color.valueOf("#ff6d00"), darkVoidHitFx, StatusEffects.blasted),
            new StarBullet(Color.valueOf("#ffa726"), Color.valueOf("#f57c00"), darkVoidHitFx, StatusEffects.melting),
            new StarBullet(Color.valueOf("#ff7043"), Color.valueOf("#d84315"), darkVoidHitFx, StatusEffects.burning),
            new StarBullet(Color.valueOf("#29b6f6"), Color.valueOf("#0288d1"), darkVoidHitFx, StatusEffects.freezing),
            new StarBullet(Color.valueOf("#e1bee7"), Color.valueOf("#ba68c8"), darkVoidHitFx, StatusEffects.shocked),
            new StarBullet(Color.valueOf("#60a5fa"), Color.valueOf("#2563eb"), darkVoidHitFx, StatusEffects.wet),
            new StarBullet(Color.valueOf("#bef264"), Color.valueOf("#65a30d"), acidCorrosionEffect, StatusEffects.corroded)
        };
        darkVoidBullet = new DarkVoidBullet();
        chargedState2 = new ChargedBullet(1075f);
        chargedState3 = new ChargedBullet(1827.5f);
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> setup());
        Events.on(WorldLoadEvent.class, e -> Time.run(10f, AetherSpiral::updateVisibility));
        Events.on(BlockBuildEndEvent.class, e -> updateVisibility());
        Events.on(BlockDestroyEvent.class, e -> onBlockDestroy(e));
    }

    static Block findBlock() {
        Block b = Vars.content.block("newex-aether-spiral");
        return b != null ? b : Vars.content.block("aether-spiral");
    }

    private static void setup() {
        Block b = findBlock();
        if (!(b instanceof ItemTurret)) return;
        aether = (ItemTurret) b;
        aether.configurable = true;
        if (aether.ammoTypes != null) {
            aether.ammoTypes.put(Items.surgeAlloy, normalPool[0]);
            aether.ammoTypes.put(Items.silicon, normalPool[0]);
        }
        // JS còn override configured() cho `typeof value === "number"` (Integer Java trong Rhino không khớp nên chưa từng chạy); config(Integer) đã xử lý.
        aether.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof AetherBuild) ((AetherBuild) tile).applyEquipConfig(value);
        });
        aether.buildType = () -> new AetherBuild(aether);
    }

    /** Mỗi đội chỉ được xây tối đa (số lõi) pháo. */
    static void updateVisibility() {
        Block t = findBlock();
        if (Vars.player == null || t == null) return;
        Team team = Vars.player.team();
        int maxAllowed = team.cores().size;
        int cur = PerfCore.teamCount(team, t);
        t.buildVisibility = cur < maxAllowed ? BuildVisibility.shown : BuildVisibility.hidden;
    }

    static void onBlockDestroy(BlockDestroyEvent event) {
        Block t = findBlock();
        if (t == null) return;
        if (event.tile == null || event.tile.build == null) return;
        Building destroyed = event.tile.build;
        final Team victim = destroyed.team;
        if (destroyed.block instanceof CoreBlock) {
            int maxAllowed = victim.data().cores.size - 1;
            if (maxAllowed < 0) maxAllowed = 0;
            final ArrayList<Building> list = new ArrayList<>();
            Groups.build.each(bd -> { if (bd.block == t && bd.team == victim) list.add(bd); });
            if (list.size() > maxAllowed) {
                int toDestroy = list.size() - maxAllowed;
                for (int i = 0; i < toDestroy; i++) {
                    Building last = list.remove(list.size() - 1);
                    Call.sendMessage("[red]Đội " + victim.name + " bị mất Lõi! Pháo Aether Spiral thừa đã tự hủy![]");
                    last.kill();
                }
            }
        }
        updateVisibility();
    }

    // ---------------------------------------------------------------- UI trang bị (UniversalItemUI)
    static int maskOf(int[] s) {
        return (s[0] & 7) | ((s[1] & 7) << 3) | ((s[2] & 7) << 6) | ((s[3] & 7) << 9) | (1 << 15);
    }

    static void buildSlotUI(Table table, final AetherBuild build, final Runnable onConfigChange) {
        Table slotsTable = new Table();
        slotsTable.background(Styles.black6);
        slotsTable.margin(6f);
        CoreBuild core = build.team.core();
        for (int i = 0; i < 4; i++) {
            final int slotIndex = i;
            final Item currentItem = Endyr.getItemBySlotType(build.equipSlots[slotIndex]);
            Button slotBtn = slotsTable.button((Cons<Button>) b -> {
                b.clearChildren();
                if (currentItem != null) b.image(currentItem.uiIcon).size(28f);
                else b.add("[gray]+[]").fontScale(1.2f);
            }, Styles.defaultb, () -> {
                int[] next = build.equipSlots.clone();
                if (currentItem != null) {
                    next[slotIndex] = 0;
                    build.configure(Integer.valueOf(maskOf(next)));
                    if (onConfigChange != null) onConfigChange.run();
                } else {
                    showItemSelectionDialog(build, selectedType -> {
                        int[] n2 = build.equipSlots.clone();
                        n2[slotIndex] = selectedType;
                        build.configure(Integer.valueOf(maskOf(n2)));
                        if (onConfigChange != null) onConfigChange.run();
                    });
                }
            }).size(44f).pad(3f).get();

            if (currentItem != null) {
                String d = statDesc(currentItem.name.replace("newex-", ""));
                String buffText = d != null ? d : "[gray]Không có chỉ số buff[]";
                final String tooltipText = "[accent]" + currentItem.localizedName + "[]\n" + buffText;
                boolean hasItemInCore = core != null && core.items.has(currentItem, 1);
                slotBtn.addListener(new Tooltip(t -> { t.background(Styles.black6).margin(8f); t.add(tooltipText); }));
                slotBtn.setColor(hasItemInCore ? Pal.heal : Pal.remove);
            } else {
                slotBtn.addListener(new Tooltip(t -> {
                    t.background(Styles.black6).margin(8f);
                    t.add(isEn() ? "[gray]Click to equip item[]" : "[gray]Nhấp để gắn trang bị[]");
                }));
                slotBtn.setColor(Color.white);
            }
        }
        table.add(slotsTable);
    }

    static void showItemSelectionDialog(final AetherBuild build, final Cons<Integer> onSelect) {
        final BaseDialog dialog = new BaseDialog(isEn() ? "Equipment Storage" : "Kho Trang Bị");
        dialog.addCloseButton();
        final float dialogWidth = Math.min(Core.graphics.getWidth() * 0.85f, 360f);
        dialog.cont.pane(p -> {
            p.margin(6f);
            CoreBuild core = build.team.core();
            String[] validItems = {"copper", "silicon", "thorium", "newex-sallowyr", "newex-obsidis", "newex-starlight"};
            for (String itemName : validItems) {
                final Item item = Vars.content.getByName(ContentType.item, itemName);
                if (item == null) continue;
                String sd = statDesc(item.name.replace("newex-", ""));
                if (sd == null) continue;
                int count = core != null ? core.items.get(item) : 0;
                final boolean hasItem = count > 0;

                Button btn = new Button(Styles.cleart);
                btn.margin(8f);
                btn.add(new Image(item.uiIcon)).size(32f).padRight(10f).top();
                Table infoTable = new Table();
                infoTable.left();
                String statusColor = hasItem ? "[#84f491]" : "[#ff795e]";
                Label titleLabel = infoTable.add(item.localizedName + " " + statusColor + "(Lõi: " + count + ")[]").left().growX().get();
                titleLabel.setWrap(true);
                infoTable.row();
                Label buffLabel = infoTable.add(sd).fontScale(0.85f).left().growX().get();
                buffLabel.setWrap(true);
                btn.add(infoTable).growX().width(dialogWidth - 80f);
                btn.setColor(hasItem ? Pal.heal : Pal.remove);
                btn.clicked(() -> {
                    if (hasItem) {
                        onSelect.get(Endyr.getSlotTypeByItem(item));
                        dialog.hide();
                    } else {
                        Vars.ui.showInfoToast(isEn() ? "[red]Core missing this item![]" : "[red]Lõi không có nguyên liệu này![]", 2f);
                    }
                });
                p.add(btn).width(dialogWidth - 20f).pad(3f).row();
            }
        }).scrollX(false).grow();
        dialog.show();
    }

    // ---------------------------------------------------------------- Build
    public static class AetherBuild extends ItemTurret.ItemTurretBuild {
        int normalShotCount = 0, chargeShotCount = 0, chargeState = 0;
        float chargeTimer = 0f, chargeDuration = 50f, cornerOffset = 16f, convergenceDistance = 24f;
        float baseMaxRange = 320f;
        int[] equipSlots = {0, 0, 0, 0};
        int state2SubStage = 0, state2ShotIndex = 0;
        float state2MoveTimer = 0f, state2MoveDuration = 10f;
        float customRecoil = 0f, orbVisualRadius = 0f;
        /** JS: trường rotateSpeed: 5.0 trên đối tượng Build (đọc trong updateTile). */
        static final float ROT_SPEED = 5f;
        TextureRegion topRegion; boolean regionsReady = false;

        public AetherBuild(ItemTurret outer) { outer.super(); }

        int getItemCount(int type) {
            int c = 0;
            for (int i = 0; i < 4; i++) if (equipSlots[i] == type) c++;
            return c;
        }

        void applyEquipConfig(int mask) {
            if ((mask & (1 << 15)) == 0) { setSlotConfig(mask); return; }
            CoreBuild core = team.core();
            if (core == null) return; // không có lõi thì không đổi trang bị (tránh mất vật phẩm)
            for (int i = 0; i < 4; i++) {
                int newType = (mask >> (i * 3)) & 7;
                int oldType = equipSlots[i];
                if (oldType == newType) continue;
                if (oldType != 0) {
                    Item oldItem = Endyr.getItemBySlotType(oldType);
                    if (oldItem != null) core.items.add(oldItem, 1);
                }
                if (newType != 0) {
                    Item newItem = Endyr.getItemBySlotType(newType);
                    if (newItem != null && core.items.has(newItem, 1)) {
                        core.items.remove(newItem, 1);
                        equipSlots[i] = newType;
                    } else equipSlots[i] = 0;
                } else equipSlots[i] = 0;
            }
        }

        void setSlotConfig(int mask) { for (int i = 0; i < 4; i++) equipSlots[i] = (mask >> (i * 3)) & 7; }

        int getSlotConfig() {
            int mask = 0;
            for (int i = 0; i < 4; i++) mask |= ((equipSlots[i] & 7) << (i * 3));
            return mask;
        }

        float getDamageMultiplier() { return 1f + getItemCount(1) * 0.05f + getItemCount(5) * 0.10f; }
        float getSpeedMultiplier() { return 1f + getItemCount(2) * 0.05f + getItemCount(5) * 0.10f; }
        float getRangeMultiplier() { return 1f + getItemCount(3) * 0.05f + getItemCount(5) * 0.50f; }
        float getCritChance() { return getItemCount(6) * 0.05f; }
        float getCritMultiplier() { return 1f + getItemCount(6) * 0.15f; }
        boolean hasStarlightPassive() { return getItemCount(6) > 0; }
        int getExtraBullets() { return getItemCount(4); }

        @Override public float range() { return baseMaxRange * getRangeMultiplier(); }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            Table topRowTable = new Table();
            topRowTable.background(Styles.black6);
            topRowTable.margin(0f);

            topRowTable.button(Icon.info, Styles.cleari, 40, () -> {
                String title = isEn() ? " Aether Spiral Turret Stats " : " Thông số pháo Aether Spiral ";
                int statDmgPercent = Math.round(getDamageMultiplier() * 100);
                int statSpeedPercent = Math.round(getSpeedMultiplier() * 100);
                int statRangePercent = Math.round(getRangeMultiplier() * 100);
                int currentRangePx = Math.round(range());
                int critChance = Math.round(getCritChance() * 100);
                int critDmgPercent = Math.round(getCritMultiplier() * 100);

                String descStr = isEn() ?
                "[gold]⚡ TURRET EQUIPMENT STATS ⚡[]\n" +
                "[lightgray]Copper:[] [yellow]" + getItemCount(1) + " / 4[] | " +
                "[lightgray]Silicon:[] [yellow]" + getItemCount(2) + " / 4[] | " +
                "[lightgray]Thorium:[] [yellow]" + getItemCount(3) + " / 4[]\n" +
                "[lightgray]Sallowyr:[] [yellow]" + getItemCount(4) + " / 4[] | " +
                "[lightgray]Obsidis:[] [yellow]" + getItemCount(5) + " / 4[] | " +
                "[lightgray]Starlight:[] [yellow]" + getItemCount(6) + " / 4[]\n\n" +
                "[lightgray]Attack Multiplier:[] [green]" + statDmgPercent + "%[]\n" +
                "[lightgray]Fire Rate Multiplier:[] [green]" + statSpeedPercent + "%[]\n" +
                "[lightgray]Effective Range:[] [orange]" + currentRangePx + " px[] [lime](" + statRangePercent + "%)[]\n" +
                "[lightgray]Crit Chance:[] [cyan]" + critChance + "%[]\n" +
                "[lightgray]Crit Damage:[] [cyan]" + critDmgPercent + "%[]\n\n" +
                "[sky]⚡ SPECIAL ABILITY:[]\n" +
                "• Normal shots have [purple]10% chance[] to fire Dark Void Star deals [scarlet]50% Target Max HP Damage[].\n\n" +
                "[sky]⚡ ITEM BUFF MECHANIC:[]\n" +
                "• [yellow]Copper / Silicon / Thorium:[] +5% Dmg / FireRate / Range per slot.\n" +
                "• [yellow]Sallowyr:[] +1 Extra Bullet per slot equipped.\n" +
                "• [yellow]Obsidis:[] +10% Attack, +10% Speed, +50% Range.\n" +
                "• [yellow]Starlight:[] +5% Crit Chance, +15% Crit Dmg, Execute targets below 5% HP (+1000 Copper)." :
                "[gold]⚡ THÔNG SỐ TRANG BỊ THÁP PHÁO ⚡[]\n" +
                "[lightgray]Đồng:[] [yellow]" + getItemCount(1) + " / 4[] | " +
                "[lightgray]Silicon:[] [yellow]" + getItemCount(2) + " / 4[] | " +
                "[lightgray]Thorium:[] [yellow]" + getItemCount(3) + " / 4[]\n" +
                "[lightgray]Sallowyr:[] [yellow]" + getItemCount(4) + " / 4[] | " +
                "[lightgray]Obsidis:[] [yellow]" + getItemCount(5) + " / 4[] | " +
                "[lightgray]Starlight:[] [yellow]" + getItemCount(6) + " / 4[]\n\n" +
                "[lightgray]Tấn công:[] [green]" + statDmgPercent + "%[]\n" +
                "[lightgray]Tốc độ bắn:[] [green]" + statSpeedPercent + "%[]\n" +
                "[lightgray]Tầm bắn hiệu dụng:[] [orange]" + currentRangePx + " pixel[] [lime](" + statRangePercent + "%)[]\n" +
                "[lightgray]Tỉ lệ bạo kích:[] [cyan]" + critChance + "%[]\n" +
                "[lightgray]Sát thương bạo kích:[] [cyan]" + critDmgPercent + "%[]\n\n" +
                "[sky]⚡ KỸ NĂNG ĐẶC BIỆT:[]\n" +
                "• Bắn thường có [purple]10% tỉ lệ[] bắn ra Sao Đen Tím gây [scarlet]50% Max HP của mục tiêu[].\n\n" +
                "[sky]⚡ CƠ CHẾ BUFF TRANG BỊ:[]\n" +
                "• [yellow]Đồng / Silicon / Thorium:[] +5% Tấn công / Tốc độ / Tầm bắn mỗi ô.\n" +
                "• [yellow]Sallowyr:[] +1 Đạn bắn ra cho mỗi trang bị.\n" +
                "• [yellow]Obsidis:[] +10% TC/TĐ, +50% PB.\n" +
                "• [yellow]Starlight:[] +5% Bạo, +15% ST Bạo, Kết liễu mục tiêu <5% HP (+1000 Đồng).";

                BaseDialog dialog = new BaseDialog(title);
                Table infoTable = new Table();
                Cell<Label> cell = infoTable.add(descStr).width(360f);
                cell.get().setWrap(true); cell.get().setAlignment(Align.left);
                ScrollPane scroll = new ScrollPane(infoTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400f);
                dialog.addCloseButton(); dialog.show();
            }).size(50, 40).tooltip(isEn() ? "View detailed stats" : "Xem thông số chi tiết hệ thống");

            table.add(topRowTable).center().padBottom(3f).row();
            buildSlotUI(table, this, () -> deselect());
        }

        @Override public Object config() { return Integer.valueOf(getSlotConfig()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.s(getSlotConfig());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setSlotConfig(read.s());
        }

        void spawnCustomBullet(BulletType type, float x, float y, float angle) {
            Bullet b = type.create(this, team, x, y, angle);
            if (b != null) b.lifetime = type.lifetime * getRangeMultiplier();
        }

        void applyRecoil(float amount) { customRecoil = Math.min(6f, customRecoil + amount); }

        @Override
        public void updateTile() {
            super.updateTile();

            boolean directControlled = isControlled() || logicControlled();
            float targetAngle = rotation;
            if (directControlled) {
                if (unit != null) {
                    targetAngle = angleTo(unit.aimX(), unit.aimY());
                    // Bài 10/17 (lần 11): JS `this.isShooting` thiếu () nên luôn truthy → tự bắn khi điều khiển; Java gọi isShooting() thật.
                    if (isShooting() && reloadCounter <= 0f && hasAmmo()) shoot(peekAmmo());
                }
            }
            if (hasAmmo()) rotation = Angles.moveToward(rotation, targetAngle, ROT_SPEED * Time.delta);

            float dt = Time.delta * getSpeedMultiplier();
            customRecoil = Mathf.lerpDelta(customRecoil, 0f, 0.12f);

            float targetOrbRadius = 0f, maxOrb = 9f, midOrb = 6f;
            if (chargeState == 0) {
                targetOrbRadius = (normalShotCount / 5f) * maxOrb;
            } else if (chargeState == 1) {
                float done = Mathf.clamp(chargeShotCount, 0f, 3f);
                targetOrbRadius = maxOrb - (done / 3f) * (maxOrb - midOrb);
            } else if (chargeState == 2) {
                float done = Mathf.clamp(state2ShotIndex, 0f, 4f);
                targetOrbRadius = midOrb - (done / 4f) * midOrb;
            }
            orbVisualRadius = Mathf.lerpDelta(orbVisualRadius, targetOrbRadius, 0.08f);

            float orbX = x - Mathf.cosDeg(rotation) * 5f, orbY = y - Mathf.sinDeg(rotation) * 5f;

            if (chargeState == 1) {
                chargeTimer += dt;
                float muzzleX = x + Mathf.cosDeg(rotation) * convergenceDistance;
                float muzzleY = y + Mathf.sinDeg(rotation) * convergenceDistance;
                if (Mathf.chanceDelta(0.6f)) {
                    float randAng = Mathf.rand.random(360f), randDist = Mathf.rand.random(8f, 20f);
                    absorbEnergyFx.at(orbX + Angles.trnsx(randAng, randDist), orbY + Angles.trnsy(randAng, randDist));
                }
                if (Mathf.chanceDelta(0.4f)) muzzleSparkFx.at(muzzleX, muzzleY, rotation);
                if (chargeTimer >= chargeDuration) finishChargeAndShoot(muzzleX, muzzleY);
            }

            if (chargeState == 2) {
                chargeTimer += dt;
                if (state2SubStage == 0) {
                    if (chargeTimer >= 40f) { state2SubStage = 1; state2ShotIndex = 0; state2MoveTimer = 0f; }
                } else if (state2SubStage == 1) {
                    state2MoveTimer += dt;
                    if (Mathf.chanceDelta(0.5f)) {
                        float ang = Mathf.rand.random(360f), len = Mathf.rand.random(10f, 25f);
                        absorbEnergyFx.at(orbX + Angles.trnsx(ang, len), orbY + Angles.trnsy(ang, len));
                    }
                    if (state2MoveTimer >= state2MoveDuration) {
                        float muzzleX = x + Mathf.cosDeg(rotation) * convergenceDistance;
                        float muzzleY = y + Mathf.sinDeg(rotation) * convergenceDistance;
                        float lightningX = muzzleX + Mathf.cosDeg(rotation) * 5f;
                        float lightningY = muzzleY + Mathf.sinDeg(rotation) * 5f;

                        spawnCustomBullet(chargedState3, muzzleX, muzzleY, rotation);
                        applyRecoil(5f);
                        state3ShockwaveFx.at(muzzleX, muzzleY, rotation);
                        lightningAroundTurretFx.at(lightningX, lightningY);
                        Effect.shake(6f, 8f, x, y);
                        customShootFx.at(muzzleX, muzzleY, rotation);

                        state2ShotIndex++;
                        state2MoveTimer = 0f;
                        if (state2ShotIndex >= 4) {
                            chargeState = 0; normalShotCount = 0; chargeShotCount = 0; state2SubStage = 0;
                        }
                    }
                }
            }
        }

        @Override
        protected void shoot(BulletType type) {
            if (chargeState != 0) return;
            if (normalShotCount < 5) {
                fireFourCornerBullets();
                normalShotCount++;
                if (normalShotCount >= 5) { chargeState = 1; chargeTimer = 0f; }
            }
            useAmmo();
        }

        BulletType randomStar() {
            // Math.random() giữ nguyên như JS (không đổi sang Mathf.random)
            return Mathf.chance(0.10) ? darkVoidBullet : normalPool[(int) Math.floor(Math.random() * normalPool.length)];
        }

        void fireFourCornerBullets() {
            float rad = rotation * Mathf.degRad;
            float cos = Mathf.cos(rad), sin = Mathf.sin(rad);
            float[][] offsets = {{0f, cornerOffset}, {0f, -cornerOffset}, {-cornerOffset, 0f}, {cornerOffset, 0f}};
            for (int i = 0; i < 4; i++) {
                float rx = offsets[i][0] * cos - offsets[i][1] * sin;
                float ry = offsets[i][0] * sin + offsets[i][1] * cos;
                float angleSpread = (i - 1.5f) * (GOLDEN_ANGLE_DEG * 0.05f);
                BulletType sel = Mathf.chance(0.10) ? darkVoidBullet : normalPool[(int) Math.floor(Math.random() * normalPool.length)];
                spawnCustomBullet(sel, x + rx, y + ry, rotation + angleSpread);
            }
            int extra = getExtraBullets();
            for (int k = 0; k < extra; k++) {
                float spread = (k + 1) * 8f;
                BulletType left = randomStar(), right = randomStar();
                spawnCustomBullet(left, x, y, rotation + spread);
                spawnCustomBullet(right, x, y, rotation - spread);
            }
            customShootFx.at(x, y, rotation);
        }

        void finishChargeAndShoot(float tx, float ty) {
            mergeEnergyFx.at(tx, ty);
            spawnCustomBullet(chargedState2, tx, ty, rotation);
            applyRecoil(4f);
            distortedShockwaveFx.at(tx, ty, rotation);
            Effect.shake(6f, 8f, x, y);
            chargeShotCount++;
            chargeTimer = 0f;
            if (chargeShotCount >= 3) { chargeState = 2; state2SubStage = 0; state2ShotIndex = 0; }
            else chargeState = 1;
        }

        @Override
        public void draw() {
            super.draw();
            float rad = rotation * Mathf.degRad;
            float cos = Mathf.cos(rad), sin = Mathf.sin(rad);

            if (!regionsReady) { // bài 67: tên có tiền tố mod; tra một lần
                topRegion = PerfCore.region("newex-aether-spiral-top", "aether-spiral-top");
                regionsReady = true;
            }
            if (topRegion != null && topRegion.found()) { // JS `.found` thiếu () luôn truthy (bài 51); sprite có sẵn nên kết quả như cũ
                float drawOffset = 10f - customRecoil;
                Draw.z(Layer.turret + 0.1f);
                Draw.rect(topRegion, x + cos * drawOffset, y + sin * drawOffset, rotation - 90f);
            }

            float muzzleX = x + Mathf.cosDeg(rotation) * convergenceDistance;
            float muzzleY = y + Mathf.sinDeg(rotation) * convergenceDistance;
            float orbX = x - cos * 5f, orbY = y - sin * 5f;

            if (orbVisualRadius > 0.2f) {
                Draw.z(Layer.bullet + 0.9f);
                Draw.color(Color.cyan);
                Draw.alpha(0.6f + 0.2f * Mathf.sin(Time.time * 0.1f));
                Fill.circle(orbX, orbY, orbVisualRadius * 1.25f);
                Draw.color(Color.white);
                Draw.alpha(0.9f);
                Fill.circle(orbX, orbY, orbVisualRadius * 0.65f);
                Draw.reset();
            }

            if (chargeState == 1) {
                float progress = Mathf.clamp(chargeTimer / chargeDuration);
                float[][] offsets = {{0f, cornerOffset}, {0f, -cornerOffset}, {-cornerOffset, 0f}, {cornerOffset, 0f}};
                Draw.z(Layer.bullet + 1f);
                for (int i = 0; i < 4; i++) {
                    float rx = offsets[i][0] * cos - offsets[i][1] * sin;
                    float ry = offsets[i][0] * sin + offsets[i][1] * cos;
                    float ease = Mathf.pow(progress, 2f);
                    drawAsymmetricStar(Mathf.lerp(x + rx, muzzleX, ease), Mathf.lerp(y + ry, muzzleY, ease),
                        rotation, progress * 0.8f, Color.white, Color.cyan);
                }
                Draw.reset();
            }

            if (chargeState == 2) {
                float d = 18f;
                float[][] corners = {{d, d}, {-d, d}, {-d, -d}, {d, -d}};
                Draw.z(Layer.bullet + 1f);
                for (int i = 0; i < 4; i++) {
                    if (state2SubStage == 1 && i < state2ShotIndex) continue;
                    float rx = corners[i][0] * cos - corners[i][1] * sin;
                    float ry = corners[i][0] * sin + corners[i][1] * cos;
                    float startX = x + rx, startY = y + ry;
                    if (state2SubStage == 0) {
                        float progress = Mathf.clamp(chargeTimer / 40f);
                        drawAsymmetricStar(startX, startY, rotation, progress * 1.1f, Color.white, Color.sky);
                    } else if (state2SubStage == 1) {
                        if (i == state2ShotIndex) {
                            float mp = Mathf.clamp(state2MoveTimer / state2MoveDuration);
                            drawAsymmetricStar(Mathf.lerp(startX, muzzleX, mp), Mathf.lerp(startY, muzzleY, mp),
                                rotation, 1.2f, Color.white, Color.cyan);
                        } else {
                            drawAsymmetricStar(startX, startY, rotation, 1.1f, Color.white, Color.sky);
                        }
                    }
                }
                Draw.reset();
            }
        }
    }
}
