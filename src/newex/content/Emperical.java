package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
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
import arc.struct.Seq;
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
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Physicsc;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BuildVisibility;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ emperical.js. Pháo Emperical (giới hạn 1 pháo/phe): loạt đạn hỗn hợp, cộng dồn tốc bắn,
 * cánh + vòng phép, nâng cấp MK2 / MK2B.
 * Block do emperical.hjson tạo (`type: ItemTurret`) nên Java chỉ vá ở ContentInitEvent, GIỮ hjson (bài 29b).
 */
public final class Emperical {
    private Emperical() {}

    static final Color COLOR = Color.valueOf("ffff00");
    static final Color COLOR_ALT = Color.valueOf("e6005c");
    static final Color WHITE = Color.valueOf("ffffff");

    /** Giữ dạng `reqEmpericalMK2.copper` để chép nguyên văn khối chuỗi UI (bài 60). */
    static final class Req {
        final int copper, lead, titanium, thorium, surgeAlloy;
        Req(int copper, int lead, int titanium, int thorium, int surgeAlloy) {
            this.copper = copper; this.lead = lead; this.titanium = titanium;
            this.thorium = thorium; this.surgeAlloy = surgeAlloy;
        }
    }

    static final Req reqEmpericalMK2 = new Req(8000, 8000, 4000, 2000, 0);
    static final Req reqEmpericalMK2B = new Req(8000, 8000, 4000, 0, 15000);

    private static TextureRegion wing1Region = null;
    private static TextureRegion wing2Region = null;

    private static Effect circleGatherEffect, circleDisperseEffect, explosionEffect, forceEffect;
    private static Effect wingChargeLaserEffect, customTrailEffect;
    private static StatusEffect paralyzed, oppressive;
    private static BulletType bFrag, bInterval, bMain, bMissile, bHook, bLaser, bLightning;

    /** JS: isVietnamese() — "vi" nếu locale (hoặc language) bắt đầu bằng vi, mặc định "en" (bài 8). */
    private static boolean isVietnamese() {
        String loc = Core.settings.getString("locale", "en");
        if (loc == null || loc.isEmpty()) loc = Core.settings.getString("language", "en");
        return loc != null && loc.toLowerCase().startsWith("vi");
    }

    private static void loadWingRegions() {
        if (wing1Region == null || !wing1Region.found()) {
            wing1Region = PerfCore.region("newex-emperical-wing1");
            if (!wing1Region.found()) wing1Region = PerfCore.region(Vars.content.transformName("newex-emperical-wing1"));
        }
        if (wing2Region == null || !wing2Region.found()) {
            wing2Region = PerfCore.region("newex-emperical-wing2");
            if (!wing2Region.found()) wing2Region = PerfCore.region(Vars.content.transformName("newex-emperical-wing2"));
        }
    }

    private static Block empericalBlock() {
        Block b = Vars.content.block("newex-emperical");
        if (b == null) b = Vars.content.block("emperical");
        return b;
    }

    private static void updateMenuVisibility() {
        Block block = empericalBlock();
        if (Vars.player == null || block == null) return;

        Team playerTeam = Vars.player.team();
        int maxAllowed = 1;
        int currentCount = PerfCore.teamCount(playerTeam, block);

        block.buildVisibility = currentCount < maxAllowed ? BuildVisibility.shown : BuildVisibility.hidden;
    }

    private static float maxHealthOf(Object o) {
        return o instanceof Healthc ? ((Healthc) o).maxHealth() : 0f;
    }

    private static void applyDamage(Object o, float amount) {
        if (o instanceof Healthc && amount > 0f) ((Healthc) o).damage(amount);
    }

    private static void healOwner(Bullet b, float frac) {
        if (b.owner instanceof Healthc) {
            Healthc h = (Healthc) b.owner;
            h.heal(h.maxHealth() * frac);
        }
    }

    private static void handleCustomHit(Bullet bullet, Object other, float hitX, float hitY) {
        if (bullet == null || bullet.owner == null) return;

        int tier = bullet.owner instanceof EmpericalBuild ? ((EmpericalBuild) bullet.owner).getTier() : -1;
        float maxHp = maxHealthOf(other);

        if (other != null && maxHp > 0f && Mathf.chance(0.5)) {
            applyDamage(other, maxHp * 0.05f);
        }

        if (tier == 1 && maxHp > 0f && other != null) {
            applyDamage(other, maxHp * 0.01f);
        }

        if (tier == 2) {
            float splashDmg = bullet.damage * 5f;
            float splashRadius = 50f * 8f;
            Damage.damage(bullet.team, hitX, hitY, splashRadius, splashDmg);
            // backColor chỉ có ở BasicBulletType (bài 33a); loại khác → JS truyền undefined
            SplashFx.ring(hitX, hitY, splashRadius,
                bullet.type instanceof BasicBulletType ? ((BasicBulletType) bullet.type).backColor : null);
            if (Mathf.chance(0.3)) {
                explosionEffect.at(hitX, hitY);
            }
        }
    }

    // hitTile ở v157 chỉ có bản 6 tham số (Bullet, Building, x, y, health, direct); JS viết 3 tham số
    // nên super$hitTile(b, tile, health) sai chữ ký (bài 82). Java gọi đúng bản thật rồi chạy handleCustomHit.
    private static void tileHit(Bullet b, Building build) {
        if (build != null) handleCustomHit(b, build, build.x, build.y);
    }

    /** Đạn BasicBulletType dùng chung cho Frag / Interval / Main / Hook (khác nhau ở heal và hook). */
    static class EBasic extends BasicBulletType {
        final float healFrac;
        final boolean hook;
        EBasic(float healFrac, boolean hook) { this.healFrac = healFrac; this.hook = hook; }

        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            if (hook) {
                healOwner(b, healFrac);
                if (b.owner instanceof Building && entity instanceof Physicsc) {
                    Building own = (Building) b.owner;
                    float angleToTurret = Mathf.angle(own.x - entity.getX(), own.y - entity.getY());
                    ((Physicsc) entity).impulse(Angles.trnsx(angleToTurret, 400f), Angles.trnsy(angleToTurret, 400f));
                }
                super.hitEntity(b, entity, health);
            } else {
                super.hitEntity(b, entity, health);
                if (healFrac > 0f) healOwner(b, healFrac);
            }
            handleCustomHit(b, entity, entity.getX(), entity.getY());
        }

        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            tileHit(b, build);
        }
    }

    static class EMain extends EBasic {
        EMain() { super(0.001f, false); }

        @Override
        public void despawned(Bullet b) {
            super.despawned(b);
            if (!b.hit) {
                float splashRadius = 15f * 8f;
                float splashDmg = damage * 1.5f;
                Damage.damage(b.team, b.x, b.y, splashRadius, splashDmg);
                SplashFx.ring(b.x, b.y, splashRadius, backColor);
            }
        }
    }

    static class EMissile extends MissileBulletType {
        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            healOwner(b, 0.001f);
            handleCustomHit(b, entity, entity.getX(), entity.getY());
        }

        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            super.hitTile(b, build, x, y, initialHealth, direct);
            tileHit(b, build);
        }
    }

    static class ELaser extends LaserBulletType {
        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            healOwner(b, 0.025f);
            handleCustomHit(b, entity, entity.getX(), entity.getY());
        }

        // JS không gọi super ở hitTile của Laser/Lightning
        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            tileHit(b, build);
        }
    }

    static class ELightning extends LightningBulletType {
        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            super.hitEntity(b, entity, health);
            handleCustomHit(b, entity, entity.getX(), entity.getY());
        }

        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            tileHit(b, build);
        }
    }

    private static void createEffects() {
        circleGatherEffect = new Effect(12f, e -> {
            Draw.z(Layer.effect);
            Draw.color(COLOR, COLOR_ALT, e.fin());
            for (int i = 0; i < 4; i++) {
                float angle = e.id * 90f + i * 90f + e.fin() * 180f;
                float dist = 25f * e.fout();
                float px = e.x + Angles.trnsx(angle, dist);
                float py = e.y + Angles.trnsy(angle, dist);
                Fill.circle(px, py, 2f * e.fin());
            }
        });

        circleDisperseEffect = new Effect(15f, e -> {
            Draw.z(Layer.effect);
            Draw.color(COLOR_ALT, COLOR, e.fin());
            for (int i = 0; i < 6; i++) {
                float angle = e.id * 60f + i * 60f;
                float dist = 30f * e.finpow();
                float px = e.x + Angles.trnsx(angle, dist);
                float py = e.y + Angles.trnsy(angle, dist);
                Fill.circle(px, py, 2.5f * e.fout());
            }
        });

        new Effect(30f, e -> {   // EmpericalExplosionCircleEffect: JS khai báo nhưng không nơi nào dùng → bỏ
        });

        customTrailEffect = new Effect(16f, e -> {
            Draw.color(COLOR, COLOR_ALT, e.fin());
            Lines.stroke(3f * e.fout());
            Lines.lineAngle(e.x, e.y, e.rotation + 180f, 8f * e.fout());
            Fill.circle(e.x, e.y, 2.5f * e.fout());
        });

        forceEffect = new Effect(50f, e -> {
            Draw.color(COLOR, COLOR_ALT, e.fin());
            Lines.stroke(5f * e.fout());
            Lines.circle(e.x, e.y, e.finpow() * 150f);
        });

        explosionEffect = new Effect(60f, e -> {
            Draw.color(COLOR_ALT);
            Lines.stroke(4f * e.fout());
            Lines.circle(e.x, e.y, e.finpow() * 400f);
            Draw.color(COLOR);
            Fill.circle(e.x, e.y, e.fout() * 80f);
        });

        wingChargeLaserEffect = new Effect(20f, e -> {
            if (!(e.data instanceof Vec2)) return;
            Vec2 d = (Vec2) e.data;
            Draw.z(Layer.effect);
            Draw.color(COLOR_ALT, COLOR, e.fin());
            Lines.stroke(2f * e.fout());
            Lines.line(e.x, e.y, d.x, d.y);
            Fill.circle(d.x, d.y, 3f * e.fout());
            Draw.reset();
        });
    }

    private static void drawMagicCircle(float x, float y, float radius, float intensity) {
        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);

        Draw.color(COLOR_ALT, 0.35f * intensity);
        Lines.stroke(6f * intensity);
        Lines.poly(x, y, 3, radius * 1.15f, Time.time);
        Lines.poly(x, y, 3, radius * 1.15f, -Time.time);
        Lines.poly(x, y, 24, radius * 1.1f, 0f);

        Draw.color(COLOR, intensity);
        Lines.stroke(2f * intensity);
        Lines.poly(x, y, 3, radius, Time.time);
        Lines.poly(x, y, 3, radius, -Time.time);
        Lines.poly(x, y, 24, radius, 0f);

        Draw.blend();
        Draw.reset();
    }

    private static void drawAura(float x, float y, float radius, float intensity) {
        float angle2 = Time.time;
        float angle3 = Time.time / 2f;
        float x2 = x + (radius / 2f) * Mathf.cosDeg(angle2);
        float y2 = y + (radius / 2f) * Mathf.sinDeg(angle2);
        float x3 = x2 + (radius / 4f) * Mathf.cosDeg(angle3);
        float y3 = y2 + (radius / 4f) * Mathf.sinDeg(angle3);

        Draw.z(Layer.effect);
        Draw.blend(Blending.additive);

        Draw.color(COLOR_ALT, 0.3f * intensity);
        Lines.stroke(5f * intensity);
        Lines.poly(x, y, 16, radius * 1.1f, 0f);
        Lines.poly(x2, y2, 16, (radius / 2f) * 1.15f, 0f);
        Draw.alpha(0.3f * intensity);
        Fill.circle(x3, y3, (radius / 4f) * 1.3f);

        Draw.color(COLOR, intensity);
        Lines.stroke(2f * intensity);
        Lines.poly(x, y, 16, radius, 0f);
        Lines.poly(x2, y2, 16, radius / 2f, 0f);
        Fill.circle(x3, y3, radius / 4f);
        Lines.poly(x, y, 3, radius, Time.time + 90f);
        Lines.poly(x, y, 3, radius, -Time.time - 90f);
        // JS: Lines.poly(x2, y2, radius / 2, Time.time) thiếu số cạnh → gọi sai overload (bài 83); thêm 3 cạnh như hai dòng trên.
        Lines.poly(x2, y2, 3, radius / 2f, Time.time);
        Lines.poly(x2, y2, 3, radius / 2f, Time.time + 180f);

        Draw.blend();
        Draw.reset();
    }

    /** Gọi trong NexMod.loadContent(): StatusEffect PHẢI tạo ở đây (nhận tiền tố mod, không chạy lúc import mod). */
    public static void create() {
        paralyzed = new StatusEffect("paralyzed");
        paralyzed.localizedName = "Paralyzed";
        paralyzed.speedMultiplier = 0.6f;
        paralyzed.reloadMultiplier = 0.5f;

        oppressive = new StatusEffect("oppressive");
        oppressive.localizedName = "Oppressive";
        oppressive.speedMultiplier = 0f;
        oppressive.reloadMultiplier = 0f;

        // init() (constructor) chạy trước create() (loadContent) nên gán status cho đạn ở đây.
        if (bLaser != null) bLaser.status = oppressive;
        if (bLightning != null) bLightning.status = paralyzed;
    }

    public static void init() {
        createEffects();

        EBasic frag = new EBasic(0f, false);
        frag.damage = 0f; frag.speed = 1f; frag.lifetime = 14.4f; frag.width = 0f; frag.height = 0f;
        frag.lightning = 2; frag.lightningDamage = 85f; frag.lightningLength = 7; frag.lightningColor = COLOR;
        bFrag = frag;

        EBasic interval = new EBasic(0f, false);
        interval.damage = 0f; interval.speed = 0f; interval.lifetime = 0f; interval.width = 0f; interval.height = 0f;
        interval.lightning = 2; interval.lightningDamage = 35f; interval.lightningLength = 7; interval.lightningColor = COLOR;
        bInterval = interval;

        EMain main = new EMain();
        main.damage = 500f; main.speed = 5f; main.lifetime = 72f; main.width = 20f; main.height = 20f;
        main.trailWidth = 0f; main.trailLength = 0; main.trailEffect = customTrailEffect; main.trailInterval = 4f;
        main.frontColor = COLOR; main.backColor = COLOR_ALT; main.trailColor = COLOR_ALT;
        main.status = StatusEffects.shocked; main.statusDuration = 60f;
        main.homingPower = 0.05f; main.homingRange = 360f; main.homingDelay = 14.4f;
        main.splashDamage = 180f; main.splashDamageRadius = 60f;
        main.lightning = 3; main.lightningDamage = 95f; main.lightningLength = 10; main.lightningColor = COLOR;
        main.pierce = true; main.pierceCap = 1;
        main.fragBullets = 2; main.fragBullet = frag;
        main.bulletInterval = 6f; main.intervalBullet = interval;
        bMain = main;

        EMissile missile = new EMissile();
        missile.damage = 205f; missile.speed = 10f; missile.lifetime = 36f; missile.width = 5f; missile.height = 15f;
        missile.trailWidth = 0f; missile.trailLength = 0; missile.trailEffect = customTrailEffect; missile.trailInterval = 4f;
        missile.frontColor = COLOR; missile.backColor = COLOR_ALT; missile.trailColor = COLOR_ALT;
        missile.homingPower = 1f; missile.homingRange = 180f;
        missile.lightning = 2; missile.lightningDamage = 120f; missile.lightningLength = 7; missile.lightningColor = COLOR;
        bMissile = missile;

        EBasic hook = new EBasic(0.001f, true);
        hook.damage = 60f; hook.speed = 20f; hook.lifetime = 18f; hook.width = 10f; hook.height = 10f;
        hook.trailWidth = 0f; hook.trailLength = 0; hook.trailEffect = customTrailEffect; hook.trailInterval = 3f;
        hook.frontColor = WHITE; hook.backColor = COLOR_ALT; hook.trailColor = COLOR;
        hook.homingPower = 1f; hook.homingRange = 90f;
        hook.lightning = 2; hook.lightningDamage = 165f; hook.lightningLength = 7; hook.lightningColor = COLOR;
        bHook = hook;

        ELaser laser = new ELaser();
        laser.damage = 900f; laser.lifetime = 30f; laser.width = 56f; laser.length = 480f;
        laser.pierce = true; laser.pierceCap = 999; laser.pierceArmor = true;
        laser.colors = new Color[]{COLOR_ALT, COLOR, WHITE};
        laser.statusDuration = 120f;
        bLaser = laser;

        ELightning lightning = new ELightning();
        lightning.damage = 60f; lightning.lightningLength = 45; lightning.lightningColor = COLOR_ALT;
        lightning.statusDuration = 90f;
        bLightning = lightning;

        Events.on(WorldLoadEvent.class, e -> Time.run(10f, Emperical::updateMenuVisibility));
        Events.on(BlockBuildEndEvent.class, e -> updateMenuVisibility());
        Events.on(BlockDestroyEvent.class, e -> {
            Block block = empericalBlock();
            if (block == null) return;

            if (e.tile == null || e.tile.build == null) return;
            Building destroyedBuild = e.tile.build;
            Team victimTeam = destroyedBuild.team;

            if (destroyedBuild.block instanceof CoreBlock) {
                int maxAllowed = 1;
                Seq<Building> teamBlocks = new Seq<>();
                Groups.build.each(b -> {
                    if (b.block == block && b.team == victimTeam) teamBlocks.add(b);
                });

                if (teamBlocks.size > maxAllowed) {
                    int toDestroy = teamBlocks.size - maxAllowed;
                    for (int i = 0; i < toDestroy; i++) {
                        Building lastBlock = teamBlocks.pop();
                        Call.sendMessage("[red]Đội " + victimTeam.name + " vượt quá giới hạn 1 Pháo Emperical duy nhất! Pháo thừa đã tự hủy![]");
                        lastBlock.kill();
                    }
                }
            }

            updateMenuVisibility();
        });

        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        loadWingRegions();

        Block blk = empericalBlock();
        if (!(blk instanceof ItemTurret)) return;
        ItemTurret emperical = (ItemTurret) blk;

        emperical.configurable = true;

        emperical.addBar("atkSpeed", (Building atk) -> new Bar(
            () -> {
                int count = atk instanceof EmpericalBuild ? ((EmpericalBuild) atk).getShootC() : 0;
                return "Atk Speed: +" + (count * 30) + "%";
            },
            () -> Color.cyan,
            () -> {
                int count = atk instanceof EmpericalBuild ? ((EmpericalBuild) atk).getShootC() : 0;
                return count / 30f;
            }));

        emperical.config(Integer.class, (EmpericalBuild tile, Integer value) -> {
            if (tile != null) tile.setTier(value);
        });

        emperical.buildType = () -> new EmpericalBuild(emperical);
    }

    public static class EmpericalBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;
        float mainReloadTimer = 0f;
        float mainReloadTime = 60f;

        float supReloadTimer = 0f;
        float supReloadTime = 120f;

        int shootCount = 0;
        float shootCountTimer = 0f;
        float stopShootTimer = 0f;

        float circleIntroTimer = 0f;
        boolean hasCreatedIntro = false;
        boolean wasActiveLastFrame = false;

        float wingAlpha = 0f;
        float wingProgress = 0f;
        float wingChargeTimer = 0f;

        public EmpericalBuild(ItemTurret outer) {
            outer.super();
        }

        int getTier() { return tierState; }

        void setTier(int val) {
            tierState = val;
            shootCount = 0;
            Fx.upgradeCore.at(x, y);
        }

        @Override
        public float range() {
            float baseRange = super.range();
            return (getTier() == 2) ? baseRange * 1.5f : baseRange;
        }

        @Override
        public Object config() {
            return Integer.valueOf(getTier());
        }

        private String reqSnapshot(boolean vi) {
            CoreBuild core = team != null ? team.core() : null;
            if (core == null) return vi ? "[red]Không tìm thấy Lõi Đội![]" : "[red]Team Core not found![]";
            int currentCopper = core.items.get(Items.copper);
            int currentLead = core.items.get(Items.lead);
            int currentTitanium = core.items.get(Items.titanium);
            int currentThorium = core.items.get(Items.thorium);
            int currentSurge = core.items.get(Items.surgeAlloy);

            String copColor1 = currentCopper >= reqEmpericalMK2.copper ? "[green]" : "[red]";
            String leaColor1 = currentLead >= reqEmpericalMK2.lead ? "[green]" : "[red]";
            String titColor1 = currentTitanium >= reqEmpericalMK2.titanium ? "[green]" : "[red]";
            String thoColor1 = currentThorium >= reqEmpericalMK2.thorium ? "[green]" : "[red]";

            String copColor2 = currentCopper >= reqEmpericalMK2B.copper ? "[green]" : "[red]";
            String leaColor2 = currentLead >= reqEmpericalMK2B.lead ? "[green]" : "[red]";
            String titColor2 = currentTitanium >= reqEmpericalMK2B.titanium ? "[green]" : "[red]";
            String surColor2 = currentSurge >= reqEmpericalMK2B.surgeAlloy ? "[green]" : "[red]";

            if (vi) {
                                return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                                       "[cyan]Nhánh Nâng Cấp MK2[]\n • Đồng: " + copColor1 + currentCopper + "[] / " + reqEmpericalMK2.copper + "\n • Chì: " + leaColor1 + currentLead + "[] / " + reqEmpericalMK2.lead + "\n • Titan: " + titColor1 + currentTitanium + "[] / " + reqEmpericalMK2.titanium + "\n • Thorium: " + thoColor1 + currentThorium + "[] / " + reqEmpericalMK2.thorium + "\n" +
                                       "[purple]Nhánh Tầm Xa MK2B[]\n • Đồng: " + copColor2 + currentCopper + "[] / " + reqEmpericalMK2B.copper + "\n • Chì: " + leaColor2 + currentLead + "[] / " + reqEmpericalMK2B.lead + "\n • Titan: " + titColor2 + currentTitanium + "[] / " + reqEmpericalMK2B.titanium + "\n • Hợp kim Surge: " + surColor2 + currentSurge + "[] / " + reqEmpericalMK2B.surgeAlloy;
            } else {
                                return "[yellow]CORE VAULT RESOURCE REQUIREMENTS:[]\n" +
                                       "[cyan]MK2 Upgrade Path[]\n • Copper: " + copColor1 + currentCopper + "[] / " + reqEmpericalMK2.copper + "\n • Lead: " + leaColor1 + currentLead + "[] / " + reqEmpericalMK2.lead + "\n • Titanium: " + titColor1 + currentTitanium + "[] / " + reqEmpericalMK2.titanium + "\n • Thorium: " + thoColor1 + currentThorium + "[] / " + reqEmpericalMK2.thorium + "\n" +
                                       "[purple]MK2B Long-Range Path[]\n • Copper: " + copColor2 + currentCopper + "[] / " + reqEmpericalMK2B.copper + "\n • Lead: " + leaColor2 + currentLead + "[] / " + reqEmpericalMK2B.lead + "\n • Titanium: " + titColor2 + currentTitanium + "[] / " + reqEmpericalMK2B.titanium + "\n • Surge Alloy: " + surColor2 + currentSurge + "[] / " + reqEmpericalMK2B.surgeAlloy;
            }
        }

        private void openUpgradeDialog() {
            boolean vi = isVietnamese();
            String dialogTitle = vi ? "Trung tâm nâng cấp Emperical" : "Emperical Upgrade Center";
            BaseDialog dialog = new BaseDialog(dialogTitle);

            Cell<Table> reqCell = dialog.cont.add(new Table());
            reqCell.get().add(new Label(reqSnapshot(vi))).growX();

            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branchesTable = new Table();

            Table b1 = new Table();
            b1.background(Styles.black6);
            b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
            String b1Text = vi ? "[white]• Sát thương gốc: [green]+50%[]\n" +
                                           "[white]• Cơ chế đòn đánh: Bắn trúng gây thêm [yellow]1% Max HP[] của mục tiêu thành sát thương bổ sung.[]"
                                        : "[white]• Base Damage: [green]+50%[]\n" +
                                           "[white]• Attack Mechanism: Hits deal extra [yellow]1% Max HP[] of target as bonus damage.[]";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f).get().setWrap(true);
            b1D.get().setAlignment(Align.left);
            b1.row();

            b1.button(vi ? "[green]KÍCH HOẠT MK2[]" : "[green]ACTIVATE MK2[]", () -> {
                CoreBuild core = team != null ? team.core() : null;
                if (core != null &&
                    core.items.get(Items.copper) >= reqEmpericalMK2.copper &&
                    core.items.get(Items.lead) >= reqEmpericalMK2.lead &&
                    core.items.get(Items.titanium) >= reqEmpericalMK2.titanium &&
                    core.items.get(Items.thorium) >= reqEmpericalMK2.thorium) {

                    NexNet.take(core, Items.copper, reqEmpericalMK2.copper);
                    NexNet.take(core, Items.lead, reqEmpericalMK2.lead);
                    NexNet.take(core, Items.titanium, reqEmpericalMK2.titanium);
                    NexNet.take(core, Items.thorium, reqEmpericalMK2.thorium);

                    NexNet.fx(Fx.upgradeCore, x, y);
                    NexNet.fx(Fx.mineHuge, x, y);
                    NexNet.shake(5f, 5f, x, y);

                    configure(Integer.valueOf(1));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(vi ? "[red]Không đủ tài nguyên nâng cấp MK2![]" : "[red]Not enough resources for MK2![]");
                }
            }).size(180f, 38f);

            Table b2 = new Table();
            b2.background(Styles.black6);
            b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
            String b2Text = vi ? "[white]• Sát thương gốc: [red]-20%[]\n" +
                                           "[white]• Tầm bắn: [green]+50%[] (Lifetime đạn đồng bộ tầm bắn mới)\n" +
                                           "[white]• Hiệu ứng nổ lan: Bắn trúng mục tiêu gây nổ lan [orange]500% Sát thương gốc[] trong phạm vi [yellow]50 ô (400px)[]![]"
                                        : "[white]• Base Damage: [red]-20%[]\n" +
                                           "[white]• Attack Range: [green]+50%[] (Bullet lifetime synced to new range)\n" +
                                           "[white]• Splash Blast: On-hit deals [orange]500% Base DMG[] splash blast in [yellow]50 tiles (400px)[] radius![]";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f).get().setWrap(true);
            b2D.get().setAlignment(Align.left);
            b2.row();

            b2.button(vi ? "[orange]KÍCH HOẠT MK2B[]" : "[orange]ACTIVATE MK2B[]", () -> {
                CoreBuild core = team != null ? team.core() : null;
                if (core != null &&
                    core.items.get(Items.copper) >= reqEmpericalMK2B.copper &&
                    core.items.get(Items.lead) >= reqEmpericalMK2B.lead &&
                    core.items.get(Items.titanium) >= reqEmpericalMK2B.titanium &&
                    core.items.get(Items.surgeAlloy) >= reqEmpericalMK2B.surgeAlloy) {

                    NexNet.take(core, Items.copper, reqEmpericalMK2B.copper);
                    NexNet.take(core, Items.lead, reqEmpericalMK2B.lead);
                    NexNet.take(core, Items.titanium, reqEmpericalMK2B.titanium);
                    NexNet.take(core, Items.surgeAlloy, reqEmpericalMK2B.surgeAlloy);

                    NexNet.fx(Fx.bigShockwave, x, y);
                    NexNet.fx(Fx.mineHuge, x, y);
                    NexNet.shake(5f, 5f, x, y);

                    configure(Integer.valueOf(2));
                    dialog.hide();
                    deselect();
                } else {
                    Vars.ui.showInfo(vi ? "[red]Không đủ tài nguyên nâng cấp MK2B![]" : "[red]Not enough resources for MK2B![]");
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

        private void openInfoDialog(int tier) {
            boolean vi = isVietnamese();
            String infoTitle = vi ? "Thông tin pháo Emperical" : "Emperical Turret Specs";
            String curTierStr = tier == 0 ? "[white]Gốc (Tier 1)[]" : (tier == 1 ? "[cyan]MK2[]" : "[purple]MK2B[]");

            String descStr = "";
            if (tier == 0) {
                descStr = vi ? "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                                   "[lightgray]Giới hạn xây dựng:[] [red]1 Pháo / Phe (Duy nhất)[]\n" +
                                   "[lightgray]Cấu hình hiện tại:[] " + curTierStr + "\n" +
                                   "[lightgray]Tầm bắn gốc:[] [orange]360 pixel[]\n" +
                                   "[lightgray]Gia tốc bắn tối đa:[] [cyan]+900% (30 cộng dồn)[]\n\n" +
                                   "[cyan]⚡ CƠ CHẾ KĨ NĂNG:[]\n" +
                                   "• Hỏa lực hỗn hợp: Khai hỏa đạn năng lượng, tên lửa, móc kéo, sấm sét và Laser áp chế.\n" +
                                   "• Sát thương % HP: [yellow]50% tỉ lệ mỗi viên đạn bắn ra sẽ gây thêm 5% Max HP[] trực tiếp vào mục tiêu!\n" +
                                   "• Hồi phục bản thân & Sóng xung kích triệt tiêu đạn địch."
                                 : "[gold]⚡ BASE SPECS (MK1) ⚡[]\n" +
                                   "[lightgray]Build Limit:[] [red]1 Turret / Team (Unique)[]\n" +
                                   "[lightgray]Current Config:[] " + curTierStr + "\n" +
                                   "[lightgray]Base Range:[] [orange]360 pixels[]\n" +
                                   "[lightgray]Max Speed Accelerate:[] [cyan]+900% (30 stacks)[]\n\n" +
                                   "[cyan]⚡ SPECIAL MECHANICS:[]\n" +
                                   "• Mixed Salvo: Fires Orbs, Missiles, Hooks, Lightning, and Oppressive Lasers.\n" +
                                   "• % Max HP Damage: [yellow]50% chance for each bullet hit to deal 5% Max HP[] bonus damage!\n" +
                                   "• Self-Healing & Bullet-Canceling Shockwaves.";
            } else if (tier == 1) {
                descStr = vi ? "[cyan]⚡ CẤU HÌNH CẢI TIẾN MK2 ⚡[]\n" +
                                   "[lightgray]Giới hạn xây dựng:[] [red]1 Pháo / Phe (Duy nhất)[]\n" +
                                   "[lightgray]Cấu hình hiện tại:[] " + curTierStr + "\n" +
                                   "[lightgray]Sát thương tổng:[] [green]+50%[]\n" +
                                   "[lightgray]Tầm bắn:[] [orange]360 pixel[]\n\n" +
                                   "[cyan]⚡ CƠ CHẾ ĐẶC BIỆT MK2:[]\n" +
                                   "• Bắn trúng gây thêm [yellow]1% Max HP[] cố định + [yellow]50% tỉ lệ gây 5% Max HP[] trực tiếp.\n" +
                                   "• Giữ nguyên cơ chế sóng xung kích triệt tiêu đạn."
                                 : "[cyan]⚡ ENHANCED SPECS MK2 ⚡[]\n" +
                                   "[lightgray]Build Limit:[] [red]1 Turret / Team (Unique)[]\n" +
                                   "[lightgray]Current Config:[] " + curTierStr + "\n" +
                                   "[lightgray]Overall Damage:[] [green]+50%[]\n" +
                                   "[lightgray]Effective Range:[] [orange]360 pixels[]\n\n" +
                                   "[cyan]⚡ MK2 SPECIAL MECHANICS:[]\n" +
                                   "• On-hit deals extra [yellow]1% Max HP[] fixed + [yellow]50% chance to deal 5% Max HP[] bonus damage.\n" +
                                   "• Retains bullet-canceling shockwaves.";
            } else if (tier == 2) {
                descStr = vi ? "[purple]⚡ CẤU HÌNH TẦM XA MK2B ⚡[]\n" +
                                   "[lightgray]Giới hạn xây dựng:[] [red]1 Pháo / Phe (Duy nhất)[]\n" +
                                   "[lightgray]Cấu hình hiện tại:[] " + curTierStr + "\n" +
                                   "[lightgray]Sát thương gốc:[] [red]-20%[]\n" +
                                   "[lightgray]Tầm bắn hiệu dụng:[] [green]540 pixel (+50%)[]\n\n" +
                                   "[purple]🔥 CƠ CHẾ ĐẶC BIỆT MK2B:[]\n" +
                                   "• Siêu bão nổ lan: Đạn đánh trúng gây [orange]500% Sát thương gốc[] trong phạm vi rộng [yellow]50 ô (400px)[]!\n" +
                                   "• Giữ nguyên cơ chế [yellow]50% tỉ lệ gây thêm 5% Max HP[] khi bắn trúng."
                                 : "[purple]⚡ LONG-RANGE SPECS MK2B ⚡[]\n" +
                                   "[lightgray]Build Limit:[] [red]1 Turret / Team (Unique)[]\n" +
                                   "[lightgray]Base Damage:[] [red]-20%[]\n" +
                                   "[lightgray]Effective Range:[] [green]540 pixels (+50%)[]\n\n" +
                                   "[purple]🔥 MK2B SPECIAL MECHANICS:[]\n" +
                                   "• Massive Splash Blast: On-hit triggers [orange]500% Base DMG[] splash blast in [yellow]50 tiles (400px)[] radius!\n" +
                                   "• Retains [yellow]50% chance to deal 5% Max HP[] bonus damage.";
            }

            BaseDialog infoDialog = new BaseDialog(infoTitle);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true);
            cell.get().setAlignment(Align.left);

            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            infoDialog.cont.add(scroll).maxHeight(400f);
            infoDialog.addCloseButton();
            infoDialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            if (table == null) return;
            table.clear();
            table.row();
            int tier = getTier();
            boolean vi = isVietnamese();

            if (tier == 0) {
                table.button(Icon.upOpen, Styles.cleari, 44, this::openUpgradeDialog)
                    .size(44f, 44f).tooltip(vi ? "Nâng cấp tháp pháo" : "Upgrade turret");
            } else {
                table.button(Icon.lock, Styles.cleari, 44, () -> {
                    String activeMsg = vi ? (tier == 1 ? "[cyan]ĐANG HOẠT ĐỘNG Ở CẤU HÌNH EMPERICAL MK2![]" : "[purple]ĐANG HOẠT ĐỘNG Ở CẤU HÌNH EMPERICAL MK2B![]")
                                       : (tier == 1 ? "[cyan]CURRENTLY ACTIVE IN EMPERICAL MK2![]" : "[purple]CURRENTLY ACTIVE IN EMPERICAL MK2B![]");
                    Vars.ui.showInfo(activeMsg);
                }).size(44f, 44f).tooltip(vi ? "Cấu hình hiện tại" : "Current config");
            }

            table.button(Icon.info, Styles.cleari, 44, () -> openInfoDialog(tier))
                .size(44f, 44f).tooltip(vi ? "Thông tin pháo" : "Turret Info");
        }

        void shootPos(BulletType bulletType, float x, float y, float rot) {
            int tier = getTier();
            Bullet bullet = bulletType.create(this, team, x, y, rot);
            if (bullet == null) return;

            float dmgMult = (tier == 1) ? 1.5f : ((tier == 2) ? 0.8f : 1f);
            bullet.damage = bulletType.damage * dmgMult;

            if (tier == 2) {
                bullet.lifetime = bulletType.lifetime * 1.5f;
            }
        }

        int getShootC() { return shootCount; }

        void force(float fx, float fy, float radius) {
            forceEffect.at(fx, fy);
            if (Sounds.explosionReactor2 != null) Sounds.explosionReactor2.at(fx, fy, 0.75f, 1f);
            float radSq = radius * radius;

            Groups.bullet.intersect(fx - radius, fy - radius, radius * 2f, radius * 2f, b -> {
                if (b.team != team && Mathf.len2(b.x - fx, b.y - fy) <= radSq) {
                    b.remove();
                }
            });
        }

        void shootSalvo(float sx, float sy, float soundPitch) {
            if (Sounds.shootSmite != null) Sounds.shootSmite.at(sx, sy, soundPitch, 1f);
            shootPos(bMain, sx, sy, Mathf.random(rotation - 45f, rotation + 45f));
            shootPos(bLightning, sx, sy, rotation);

            if (Mathf.chance(0.5)) shootPos(bHook, sx, sy, Mathf.random(rotation - 45f, rotation + 45f));
            if (Mathf.chance(0.6)) shootPos(bMissile, sx, sy, Mathf.random(rotation - 45f, rotation + 45f));
            if (Mathf.chance(0.15)) {
                shootPos(bLaser, sx, sy, rotation);
                if (Sounds.shootForeshadow != null) Sounds.shootForeshadow.at(sx, sy, soundPitch, 1f);
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();

            boolean isActiveNow = (shootCount > 0);

            if (isActiveNow) {
                wingAlpha = Mathf.approachDelta(wingAlpha, 1f, 0.05f);
                wingProgress = Mathf.approachDelta(wingProgress, 1f, 0.06f);

                if (wingProgress > 0.8f) {
                    wingChargeTimer += Time.delta;
                    if (wingChargeTimer >= 60f) {
                        wingChargeTimer = 0f;

                        float targetForward = 40f - 22f;
                        float targetSide = 40f;

                        float wing2X = x + Angles.trnsx(rotation, targetForward, targetSide);
                        float wing2Y = y + Angles.trnsy(rotation, targetForward, targetSide);

                        float wing1X = x + Angles.trnsx(rotation, targetForward, -targetSide);
                        float wing1Y = y + Angles.trnsy(rotation, targetForward, -targetSide);

                        wingChargeLaserEffect.at(x, y, 0f, new Vec2(wing2X, wing2Y));
                        wingChargeLaserEffect.at(x, y, 0f, new Vec2(wing1X, wing1Y));
                    }
                }
            } else {
                wingAlpha = Mathf.approachDelta(wingAlpha, 0f, 0.05f);
                wingProgress = Mathf.approachDelta(wingProgress, 0f, 0.08f);
                wingChargeTimer = 0f;
            }

            float ex1 = x + Angles.trnsx(rotation, 40f, 0f);
            float ey1 = y + Angles.trnsy(rotation, 40f, 0f);
            float ex2 = x + Angles.trnsx(rotation, 40f, 40f);
            float ey2 = y + Angles.trnsy(rotation, 40f, 40f);
            float ex3 = x + Angles.trnsx(rotation, 40f, -40f);
            float ey3 = y + Angles.trnsy(rotation, 40f, -40f);

            if (isActiveNow && !wasActiveLastFrame) {
                circleIntroTimer = 12f;
                hasCreatedIntro = false;
            } else if (!isActiveNow && wasActiveLastFrame) {
                circleDisperseEffect.at(ex1, ey1);
                circleDisperseEffect.at(ex2, ey2);
                circleDisperseEffect.at(ex3, ey3);
            }

            if (circleIntroTimer > 0f) {
                circleIntroTimer -= Time.delta;
                if (!hasCreatedIntro) {
                    circleGatherEffect.at(ex1, ey1);
                    circleGatherEffect.at(ex2, ey2);
                    circleGatherEffect.at(ex3, ey3);
                    hasCreatedIntro = true;
                }
            }

            wasActiveLastFrame = isActiveNow;

            // JS: `this.isShooting` thiếu `()` (luôn truthy, pháo bắn khi chỉ cần có đạn) → Java gọi isShooting() thật (bài 84).
            boolean firing = isShooting() && hasAmmo();

            if (firing) {
                float speedFactor = 1f / (1f + (shootCount * 0.3f));

                if (mainReloadTimer >= mainReloadTime * speedFactor) {
                    mainReloadTimer = 0f;
                    if (shootCount < 30) shootCount++;

                    float shootx = x + Angles.trnsx(rotation, 40f, 0f);
                    float shooty = y + Angles.trnsy(rotation, 40f, 0f);

                    shootSalvo(shootx, shooty, Mathf.random(0.9f, 1.1f));
                    if (Mathf.chance(0.03)) force(x, y, 160f);
                } else {
                    mainReloadTimer += Time.delta;
                }

                if (supReloadTimer >= supReloadTime * speedFactor) {
                    supReloadTimer = 0f;

                    float shootx1 = x + Angles.trnsx(rotation, 40f, 40f);
                    float shooty1 = y + Angles.trnsy(rotation, 40f, 40f);
                    float shootx2 = x + Angles.trnsx(rotation, 40f, -40f);
                    float shooty2 = y + Angles.trnsy(rotation, 40f, -40f);

                    shootSalvo(shootx1, shooty1, 0.5f);
                    if (Mathf.chance(0.03)) force(x, y, 80f);

                    shootSalvo(shootx2, shooty2, 0.5f);
                    if (Mathf.chance(0.03)) force(x, y, 80f);
                } else {
                    supReloadTimer += Time.delta;
                }
            } else {
                mainReloadTimer = 0f;
                supReloadTimer = 0f;

                if (shootCount > 0) {
                    if (stopShootTimer >= 120f) {
                        if (shootCountTimer >= 6f) {
                            shootCount--;
                            shootCountTimer = 0f;
                        } else {
                            shootCountTimer += Time.delta;
                        }
                    } else {
                        stopShootTimer += Time.delta;
                    }
                }
            }

            if (firing) stopShootTimer = 0f;
        }

        @Override
        public void draw() {
            super.draw();

            if (wingAlpha > 0.01f) {
                loadWingRegions();

                float prog = Interp.pow2Out.apply(wingProgress);

                float targetForward = 40f - 22f;
                float targetSide = 40f;

                float curForward = targetForward * prog;
                float curSide = targetSide * prog;

                float ex2 = x + Angles.trnsx(rotation, curForward, curSide);
                float ey2 = y + Angles.trnsy(rotation, curForward, curSide);

                float ex3 = x + Angles.trnsx(rotation, curForward, -curSide);
                float ey3 = y + Angles.trnsy(rotation, curForward, -curSide);

                Draw.z(Layer.turret + 0.01f);
                Draw.color(WHITE);
                Draw.alpha(wingAlpha);

                if (wing2Region != null && wing2Region.found()) {
                    Draw.rect(wing2Region, ex2, ey2, rotation - 90f);
                }
                if (wing1Region != null && wing1Region.found()) {
                    Draw.rect(wing1Region, ex3, ey3, rotation - 90f);
                }

                Draw.reset();
            }

            if (shootCount > 0 && wingProgress > 0.3f) {
                float introFactor = (circleIntroTimer > 0f) ? Math.max(0f, 1f - (circleIntroTimer / 12f)) : 1f;
                float intensity = (Mathf.clamp(shootCount, 0, 10) / 10f) * introFactor * Math.min(1f, (wingProgress - 0.3f) / 0.7f);

                float ex1 = x + Angles.trnsx(rotation, 40f, 0f);
                float ey1 = y + Angles.trnsy(rotation, 40f, 0f);

                float ex2 = x + Angles.trnsx(rotation, 40f, 40f);
                float ey2 = y + Angles.trnsy(rotation, 40f, 40f);

                float ex3 = x + Angles.trnsx(rotation, 40f, -40f);
                float ey3 = y + Angles.trnsy(rotation, 40f, -40f);

                float ex4 = x + Angles.trnsx(rotation, -60f, 0f);
                float ey4 = y + Angles.trnsy(rotation, -60f, 0f);

                drawMagicCircle(ex1, ey1, 16f, intensity);
                drawMagicCircle(ex2, ey2, 12f, intensity);
                drawMagicCircle(ex3, ey3, 12f, intensity);
                drawAura(ex4, ey4, 30f, intensity);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(getTier());
            write.f(mainReloadTimer);
            write.f(supReloadTimer);
            write.i(shootCount);
            write.f(stopShootTimer);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            tierState = read.i();
            mainReloadTimer = read.f();
            supReloadTimer = read.f();
            shootCount = read.i();
            stopShootTimer = read.f();
        }
    }
}
