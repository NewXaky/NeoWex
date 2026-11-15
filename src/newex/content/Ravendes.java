package newex.content;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
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
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Chuyển từ ravendes.js. Pháo hoàng gia bắn đạn sao 4 cánh: sát thương nhân theo khoảng cách tới mục tiêu,
 * có nhánh MK2 (tầm xa + 20% bắn thêm 3 viên) và MK2B (shotgun 20 viên mỗi 5s, vùng gần mới đau).
 */
public final class Ravendes {
    private Ravendes() {}

    private static final int[] REQ_MK2 = {500, 300, 0};    // titan, silicon, plastanium
    private static final int[] REQ_MK2B = {800, 400, 200};

    private static final String GOLD_LIGHT = "#ffffff", GOLD_MAIN = "#ffe066", GOLD_DARK = "#ffd700", GOLD_DEEP = "#ffaa00";

    private static BulletType bulletMK1, bulletMK2, bulletMK2B;
    private static Sound shootSound = Sounds.none;
    public static PowerTurret ravendes;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(boolean e, String en, String vi) { return e ? en : vi; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static Color col(String hex) { return Color.valueOf(hex); }

    // ---------------- hiệu ứng ----------------

    private static final Effect muzzleFx = new Effect(15f, e -> {
        Draw.color(col(GOLD_MAIN), col(GOLD_DARK), e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 10f * e.finpow());
    });

    private static final Effect hitFx = new Effect(20f, e -> {
        Draw.color(col(GOLD_LIGHT), col(GOLD_MAIN), e.fin());
        Lines.stroke(1.8f * e.fout());
        Lines.circle(e.x, e.y, 14f * e.finpow());
        Angles.randLenVectors(e.id, 6, 20f * e.finpow(), (x, y) -> {
            Draw.color(col(GOLD_MAIN));
            Fill.circle(e.x + x, e.y + y, 1.8f * e.fout());
        });
    });

    private static final Effect starExplosionFx = new Effect(25f, e -> {
        float size = 22f * e.finpow();
        float spinRot = e.rotation + e.fin() * 45f;

        Draw.color(col(GOLD_DEEP));
        Draw.alpha(0.8f * e.fout());
        for (int i = 0; i < 4; i++) Drawf.tri(e.x, e.y, 6f * e.fout(), size * 1.3f, spinRot + i * 90f);

        Draw.color(col(GOLD_MAIN));
        Draw.alpha(1f * e.fout());
        for (int i = 0; i < 4; i++) Drawf.tri(e.x, e.y, 4.5f * e.fout(), size, spinRot + i * 90f);

        Draw.color(col(GOLD_LIGHT));
        for (int i = 0; i < 4; i++) Drawf.tri(e.x, e.y, 2.5f * e.fout(), size * 0.6f, spinRot + 45f + i * 90f);

        Angles.randLenVectors(e.id, 8, 25f * e.finpow(), (x, y) -> {
            Draw.color(col(GOLD_LIGHT), col(GOLD_MAIN), e.fin());
            Fill.circle(e.x + x, e.y + y, 2f * e.fout());
        });
        Draw.reset();
    });

    private static final Effect critHitFx = new Effect(18f, e -> {
        Draw.color(col("#ffcc00"), Color.white, e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 12f * e.finpow());
        Angles.randLenVectors(e.id, 5, 16f * e.finpow(), (x, y) -> Fill.circle(e.x + x, e.y + y, 1.5f * e.fout()));
    });

    private static final Effect sparkTrailFx = new Effect(25f, e -> {
        Draw.color(col(GOLD_MAIN), col(GOLD_DEEP), e.fin());
        Draw.alpha(0.85f * e.fout());

        float spin = e.rotation + e.fin() * 120f;
        final float[] pos = {e.x, e.y};
        Angles.randLenVectors(e.id, 1, 3f, (x, y) -> { pos[0] += x; pos[1] += y; });
        float size = (1.2f + (e.id % 5) * 0.2f) * e.fout();

        for (int i = 0; i < 4; i++) Drawf.tri(pos[0], pos[1], size * 0.8f, size * 3.5f, spin + i * 90f);
    });

    // ---------------- đạn ----------------

    /** Đạn sao 4 cánh + nhân sát thương theo khoảng cách (tier 0/1/2). */
    private static final class StarBullet extends BasicBulletType {
        final int tier;
        final Color coreColor, outerColor, deepColor = col(GOLD_DEEP);
        final float starSize;

        StarBullet(int tier, float speed, float dmg, float w, float h, String outer, float star,
                   float splash, float splashR, float statusDur, float trailInterval, float trailParam) {
            super(speed, dmg);
            this.tier = tier;
            this.coreColor = col(GOLD_LIGHT);
            this.outerColor = col(outer);
            this.starSize = star;
            width = w;
            height = h;
            shrinkX = 0f;
            shrinkY = 0f;
            homingPower = 0f;
            frontColor = col(GOLD_LIGHT);
            backColor = col(outer);
            splashDamage = splash;
            splashDamageRadius = splashR;
            shootEffect = muzzleFx;
            hitEffect = hitFx;
            despawnEffect = starExplosionFx;
            status = StatusEffects.melting;
            statusDuration = statusDur;
            trailEffect = sparkTrailFx;
            this.trailInterval = trailInterval;
            this.trailParam = trailParam;
        }

        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            applyDistanceDamageMultiplier(b, entity, tier);
        }

        @Override
        public void draw(Bullet b) {
            float x = b.x, y = b.y;
            float spinRot = b.rotation() + b.time * 8f;

            Draw.color(deepColor);
            Draw.alpha(0.6f + Mathf.absin(b.time, 1.5f, 0.3f));
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 4.5f, starSize * 1.25f, spinRot + i * 90f);

            Draw.color(outerColor);
            Draw.alpha(0.95f);
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 3.8f, starSize, spinRot + i * 90f);

            Draw.color(outerColor);
            Draw.alpha(0.7f);
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 2.2f, starSize * 0.55f, spinRot + 45f + i * 90f);

            Draw.color(coreColor);
            Draw.alpha(1f);
            for (int i = 0; i < 4; i++) Drawf.tri(x, y, 2f, starSize * 0.6f, spinRot + i * 90f);

            Fill.circle(x, y, 2.2f);
            Draw.reset();
        }
    }

    private static void applyDistanceDamageMultiplier(Bullet b, Hitboxc entity, int tier) {
        if (entity == null || b.owner == null || !(entity instanceof Healthc)) return;
        Healthc target = (Healthc) entity;

        float maxRange = 1200f;
        float sx = 0f, sy = 0f;
        if (b.owner instanceof Turret.TurretBuild) {
            Turret.TurretBuild shooter = (Turret.TurretBuild) b.owner;
            maxRange = shooter.range();
            sx = shooter.x;
            sy = shooter.y;
        } else if (b.owner instanceof Building) {
            sx = ((Building) b.owner).x;
            sy = ((Building) b.owner).y;
        }
        float ex = entity.getX(), ey = entity.getY();
        float dist = Mathf.dst(sx, sy, ex, ey);

        if (tier == 2) {
            if (dist < maxRange * 0.4f) { target.damage(b.damage * 9f); critHitFx.at(ex, ey); }
            else if (dist < maxRange * 0.6f) { target.damage(b.damage * 5.55f); critHitFx.at(ex, ey); }
        } else {
            if (dist >= maxRange * 0.4f && dist < maxRange * 0.6f) { target.damage(b.damage * 5.55f); critHitFx.at(ex, ey); }
            else if (dist >= maxRange * 0.6f) { target.damage(b.damage * 9f); critHitFx.at(ex, ey); }
        }

        // JS gốc: type.name == "akenewm-inventer" || unit.isGalileoJS (nay là LeveledUnit).
        if (entity instanceof Unit) {
            Unit u = (Unit) entity;
            if ((u.type != null && "akenewm-inventer".equals(u.type.name)) || u instanceof LeveledUnit) {
                target.damage(b.damage * 5f);
                Fx.itemTransfer.at(ex, ey, 10f, col(GOLD_MAIN));
            }
        }
    }

    /** Nhân hệ số theo khoảng cách cho thanh hiển thị: trả {nhân, tỉ lệ thanh}. */
    private static float[] distanceMult(RavendesBuild e) {
        if (e.target == null) return new float[]{1f, 0.2f};
        float maxR = e.range();
        float dist = Mathf.dst(e.x, e.y, e.target.getX(), e.target.getY());
        if (e.getTier() == 2) {
            if (dist < maxR * 0.4f) return new float[]{9f, 1f};
            if (dist < maxR * 0.6f) return new float[]{5.55f, 0.6f};
        } else {
            if (dist >= maxR * 0.6f) return new float[]{9f, 1f};
            if (dist >= maxR * 0.4f) return new float[]{5.55f, 0.6f};
        }
        return new float[]{1f, 0.2f};
    }

    /** Gọi từ NexMod.loadContent(). Các thuộc tính còn lại do ravendes.hjson đặt. */
    public static void create() {
        bulletMK1 = new StarBullet(0, 20.8f, 75f, 4f, 16f, GOLD_MAIN, 16f, 12f, 20f, 80f, 2f, 1.5f);
        bulletMK2 = new StarBullet(1, 23.4f * 1.5f, 105f * 1.5f, 4.5f, 20f, GOLD_DARK, 18f, 20f * 1.5f, 28f, 120f, 1.5f, 2f);
        bulletMK2B = new StarBullet(2, 25f, 140f, 5f, 22f, "#ff2525", 20f, 30f, 35f, 150f, 1f, 2.5f);

        ravendes = new PowerTurret("ravendes");
        ravendes.category = Category.turret;
        ravendes.configurable = true;
        ravendes.shootSound = Sounds.none;
        ravendes.reload = 6f;
        ravendes.targetAir = true;
        ravendes.targetGround = true;
        ravendes.shootType = bulletMK1;

        ravendes.config(Integer.class, (Building tile, Integer value) -> {
            if (!(tile instanceof RavendesBuild)) return;
            RavendesBuild b = (RavendesBuild) tile;
            if (value == 1 || value == 2) b.tryUpgrade(value);
            else b.setTier(value);
        });

        ravendes.addBar("dmg_bonus", (Building e) -> {
            RavendesBuild b = (RavendesBuild) e;
            return new Bar(
                () -> b.target == null ? "DMG Multiplier: 100% (Base)" : "DMG Multiplier: " + Mathf.floor(distanceMult(b)[0] * 100f) + "%",
                () -> Color.orange,
                () -> distanceMult(b)[1]);
        });

        ravendes.addBar("power_status", (Building e) -> {
            return new Bar(
                () -> (e.power != null && e.power.status > 0f) ? "Năng lượng: Đủ điều kiện hoạt động" : "Thiếu điện năng",
                () -> (e.power != null && e.power.status > 0f) ? Color.gold : Color.scarlet,
                () -> e.power != null ? e.power.status : 0f);
        });

        ravendes.buildType = () -> new RavendesBuild(ravendes);
    }

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> {
            try { shootSound = Vars.tree.loadSound("ravendesshoot"); } catch (Throwable ignored) { }
        });
    }

    public static class RavendesBuild extends PowerTurret.PowerTurretBuild {
        int tierState = 0, paidTitanium = 0, paidSilicon = 0, paidPlastanium = 0;
        float mk2bTimer = 0f, mk2bDelayTimer = 0f;
        boolean mk2bPending = false;

        public RavendesBuild(PowerTurret outer) { outer.super(); }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            paidTitanium = 0; paidSilicon = 0; paidPlastanium = 0;
            if (val == 0) health = 1200f;
            if (val == 1) health = 1800f * 1.5f;
            if (val == 2) health = 1600f;
            maxHealth = health;
        }

        @Override
        public BulletType peekAmmo() {
            int tier = getTier();
            if (tier == 1) return bulletMK2;
            if (tier == 2) return bulletMK2B;
            return bulletMK1;
        }

        @Override
        public float range() {
            int tier = getTier();
            if (tier == 1) return 1200f * 1.5f;
            if (tier == 2) return 1300f;
            return 1200f;
        }

        @Override
        public void updateTile() {
            int tier = getTier();
            boolean hasPower = power != null && power.status > 0f;

            if (tier == 1) {
                reloadCounter += Time.delta * 0.5f;
                if (target != null) rotation = Angles.moveToward(rotation, angleTo(target), 4f * 1.5f * Time.delta);
            }

            if (tier == 2 && hasPower) {
                if (target != null) {
                    mk2bTimer += Time.delta;
                    if (mk2bTimer >= 300f) {
                        mk2bTimer = 0f;
                        fireShotgunBurst();
                        mk2bPending = true;
                        mk2bDelayTimer = 0f;
                    }
                }
                if (mk2bPending) {
                    mk2bDelayTimer += Time.delta;
                    if (mk2bDelayTimer >= 30f) {
                        mk2bPending = false;
                        fireShotgunBurst();
                    }
                }
            } else {
                mk2bTimer = 0f;
                mk2bPending = false;
                mk2bDelayTimer = 0f;
            }

            super.updateTile();
        }

        /**
         * Giữ đúng JS gốc: {@code create(owner, team, x, y, angle, -1, randSpeed)} là bản 7 tham số
         * (velocityScl = -1, lifetimeScl = randSpeed), nên đạn shotgun bay NGƯỢC hướng nòng và lifetime bị đặt lại ngay sau đó.
         * Muốn bắn thẳng + tốc độ ngẫu nhiên: dùng bản 9 tham số {@code create(..., angle, -1f, randScl, 1f, null)} (MIGRATION bài học 3, 11).
         */
        void fireShotgunBurst() {
            if (shootSound != null && shootSound != Sounds.none) shootSound.at(x, y, 1f, 1f);
            muzzleFx.at(x, y, rotation);

            BulletType type = bulletMK2B;
            float baseSpeed = type.speed;
            float baseLife = range() / baseSpeed;

            for (int i = 0; i < 20; i++) {
                float randSpeed = baseSpeed * Mathf.random(0.5f, 1.5f);
                float angleOffset = Mathf.range(18f);
                Bullet b = type.create(this, team, x, y, rotation + angleOffset, -1f, randSpeed);
                if (b != null) b.lifetime = baseLife;
            }
        }

        public boolean tryUpgrade(int targetTier) {
            if (getTier() != 0) return false;
            int[] req = targetTier == 1 ? REQ_MK2 : (targetTier == 2 ? REQ_MK2B : null);
            if (req == null) return false;
            Building core = team.core();
            if (core == null) return false;

            int coreT = core.items.get(Items.titanium), coreS = core.items.get(Items.silicon), coreP = core.items.get(Items.plastanium);
            if (coreT >= req[0] && coreS >= req[1] && coreP >= req[2]) {
                if (req[0] > 0) core.items.remove(Items.titanium, req[0]);
                if (req[1] > 0) core.items.remove(Items.silicon, req[1]);
                if (req[2] > 0) core.items.remove(Items.plastanium, req[2]);
                setTier(targetTier);
                if (targetTier == 1) Fx.upgradeCore.at(x, y); else Fx.bigShockwave.at(x, y);
                Fx.mineHuge.at(x, y);
                Effect.shake(4f, 4f, x, y);
                return true;
            }
            if (!Vars.headless && Vars.player != null) {
                Vars.ui.showInfo(t(en(), "[scarlet]Not enough resources in core![]", "[scarlet]Không đủ tài nguyên trong lõi![]"));
            }
            return false;
        }

        @Override
        public void drawSelect() {
            super.drawSelect();

            float maxR = range();
            Draw.color(col("#4488ff"));
            Lines.dashCircle(x, y, maxR * 0.4f);

            Draw.color(col("#00ffff"));
            Lines.stroke(1.5f);
            Lines.dashCircle(x, y, maxR * 0.6f);

            Draw.color(col("#ff3300"));
            Lines.stroke(2f);
            Lines.dashCircle(x, y, maxR);

            Draw.color(col("#ffaa00"));
            Draw.alpha(0.08f);
            Fill.circle(x, y, maxR);
            Draw.reset();
        }

        // JS gốc có override shouldShoot() (tier 2 trả false) nhưng TurretBuild v157 không có hàm này,
        // nên trong Rhino nó không bao giờ được gọi. Bỏ đi để giữ đúng hành vi thực tế (MIGRATION bài học 15).

        @Override
        protected void shoot(BulletType type) {
            if (shootSound != null && shootSound != Sounds.none) shootSound.at(x, y, 1f, 1f);

            int tier = getTier();
            BulletType bt = peekAmmo();
            float exactLifetime = range() / bt.speed;

            Bullet b = bt.create(this, team, x, y, rotation);
            if (b != null) b.lifetime = exactLifetime;

            if (tier == 1 && Mathf.chance(0.2)) {
                for (int i = 0; i < 3; i++) {
                    Bullet extra = bt.create(this, team, x, y, rotation + Mathf.range(18f));
                    if (extra != null) extra.lifetime = exactLifetime;
                }
            }
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.i(paidTitanium);
            write.i(paidSilicon);
            write.i(paidPlastanium);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            paidTitanium = read.i();
            paidSilicon = read.i();
            paidPlastanium = read.i();
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            boolean isEn = en();
            if (getTier() == 0) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(50, 40).tooltip(t(isEn, "Upgrade Ravendes system", "Nâng cấp hệ thống Ravendes"));
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]RAVENDES HAS REACHED MAX EVOLUTION LEVEL![]",
                    "[scarlet]HỆ THỐNG RAVENDES ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]")))
                    .size(50, 40).tooltip(t(isEn, "Max level reached", "Đã đạt cấp tối đa"));
            }
            table.button(Icon.info, Styles.cleari, 40, this::openInfo)
                .size(50, 40).tooltip(t(isEn, "View detailed stats", "Xem thông số chi tiết hệ thống"));
        }

        private String c(int have, int need) { return (have >= need ? "[green]" : "[scarlet]") + have + "[] / " + need; }

        private void doUpgrade(int tier) {
            if (Vars.net.active()) Call.tileConfig(Vars.player, this, Integer.valueOf(tier));
            else tryUpgrade(tier);
        }

        private void openUpgrade(boolean isEn) {
            Building core = team.core();
            int aT = core != null ? core.items.get(Items.titanium) : 0;
            int aS = core != null ? core.items.get(Items.silicon) : 0;
            int aP = core != null ? core.items.get(Items.plastanium) : 0;

            BaseDialog dialog = new BaseDialog(t(isEn, "Ravendes Upgrade Center", "Trung tâm nâng cấp pháo Ravendes"));

            String reqStr = isEn
                ? "[yellow]CORE RESOURCES / REQUIRED FOR UPGRADE:[]\n[cyan]MK2 Branch:[]\n • Titanium: " + c(aT, REQ_MK2[0]) + "\n • Silicon: " + c(aS, REQ_MK2[1])
                    + "\n[purple]MK2B Branch:[]\n • Titanium: " + c(aT, REQ_MK2B[0]) + "\n • Silicon: " + c(aS, REQ_MK2B[1]) + "\n • Plastanium: " + c(aP, REQ_MK2B[2])
                : "[yellow]TÀI NGUYÊN TRONG LÕI / CẦN NÂNG CẤP:[]\n[cyan]Nhánh MK2:[]\n • Titan: " + c(aT, REQ_MK2[0]) + "\n • Silicon: " + c(aS, REQ_MK2[1])
                    + "\n[purple]Nhánh MK2B:[]\n • Titan: " + c(aT, REQ_MK2B[0]) + "\n • Silicon: " + c(aS, REQ_MK2B[1]) + "\n • Nhựa Plastanium: " + c(aP, REQ_MK2B[2]);
            wrap(dialog.cont.add(reqStr), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branches = new Table();

            // Văn bản nhánh nâng cấp trong JS gốc chỉ có tiếng Việt (không phân en/vi) nên giữ nguyên.
            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
            b1.add("[#ffd700]👑 PHÂN NHÁNH A: PROTOCOL MK2 [ROYAL LONG-RANGE][]").row();
            wrap(b1.add("Tối ưu hóa tầm xa siêu cấp và gia tăng độ bền tháp.\n • [#ffe066]Khung vách:[] Titan cường lực [green]2,700 HP[] (+50%).\n • [#ffe066]Tầm bắn siêu xa:[] Tối đa [yellow]1800px[]."), 340);
            b1.row();
            b1.button("[#ffd700]KÍCH HOẠT MK2[]", () -> { doUpgrade(1); dialog.hide(); deselect(); }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
            b2.add("[#ffaa00]⚡ PHÂN NHÁNH B: PROTOCOL MK2B [GOLDEN OVERCLOCK][]").row();
            wrap(b2.add("Sấy đạn ngôi sao hoàng gia cực nhanh càn quét mục tiêu.\n • [#ffe066]Khung vách:[] Ép xung bền bỉ [green]1,600 HP[].\n • [#ffe066]Tầm bắn:[] [yellow]1300px[] kèm đạn shotgun định kỳ."), 340);
            b2.row();
            b2.button("[#ffaa00]KÍCH HOẠT MK2B[]", () -> { doUpgrade(2); dialog.hide(); deselect(); }).size(180, 38);

            branches.add(b1).width(340); branches.row();
            branches.add().height(12).row();
            branches.add(b2).width(340);

            ScrollPane scroll = new ScrollPane(branches);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo() {
            String desc = "[#ffd700]⚙️ THÔNG TIN TẦM BẮN & SÁT THƯƠNG:[]\n"
                + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                + " • [#ffe066]Năng lượng:[] Tháp tự động hoạt động khi có điện lưới.\n"
                + " • [#ffe066]MK2:[] Tăng 50% mọi chỉ số và 20% tỉ lệ bắn thêm 3 viên (lệch 18°).\n"
                + " • [#ffe066]MK2B:[] Đảo ngược vùng sát thương (càng gần càng to) và tự động bắn shotgun 20 viên mỗi 5s (2 lượt cách nhau 0.5s).";
            BaseDialog dialog = new BaseDialog("📡 HỆ THỐNG PHÁO RAVENDES ROYAL");
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }
}
