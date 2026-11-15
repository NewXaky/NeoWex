package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ balashilon.js. Trả 200 Đồng để gọi thiên thạch: sau 3-6 giây một thiên thạch rơi xuống điểm ngẫu nhiên
 * trong bán kính 150 ô, phá hủy vùng 30 ô và sinh quặng ngẫu nhiên; 3 giây sau khối bắn viên đạn "xây dựng" tới điểm rơi
 * để đặt Blast Drill + Large Battery + Container.
 */
public final class BalashilonBlock {
    private static final int METEOR_COST = 200;

    private static Block balashilonBlock = null;
    private static boolean balashilonReady = false;
    private static BulletType builderBulletType;

    // ------------------------------------------------------------------ hiệu ứng

    private static void draw3DRotatedEllipseWave(float centerX, float centerY, float radiusX, float radiusY, float rotationDeg) {
        int points = 20;
        float rotationRad = rotationDeg * Mathf.degRad;
        float cosRot = Mathf.cos(rotationRad);
        float sinRot = Mathf.sin(rotationRad);

        float localX = radiusX;
        float localY = 0f;
        float lastX = centerX + (localX * cosRot - localY * sinRot);
        float lastY = centerY + (localX * sinRot + localY * cosRot);

        for (int i = 1; i <= points; i++) {
            float angle = (i * 360f / points) * Mathf.degRad;
            localX = Mathf.cos(angle) * radiusX;
            localY = Mathf.sin(angle) * radiusY;

            float nextX = centerX + (localX * cosRot - localY * sinRot);
            float nextY = centerY + (localX * sinRot + localY * cosRot);

            Lines.line(lastX, lastY, nextX, nextY);

            lastX = nextX;
            lastY = nextY;
        }
    }

    private static final Effect customShockwaveEffect = new Effect(40f, e -> {
        Draw.z(Layer.effect + 4f);
        float f = e.fin();
        float alpha = 1f - Interp.pow3Out.apply(f);
        float radius = 10f + (240f * Interp.pow2Out.apply(f));

        Lines.stroke(6f * alpha, PerfCore.color("ffb380"));
        Lines.circle(e.x, e.y, radius);

        Lines.stroke(3f * alpha, Color.white);
        Lines.circle(e.x, e.y, radius * 0.85f);

        Draw.reset();
    });

    private static final Effect balashilonLaserBeamEffect = new Effect(50f, e -> {
        Draw.z(Layer.effect + 3f);
        float f = e.fin();
        float alpha = 1f - Interp.pow3Out.apply(f);

        Color mainColor = PerfCore.color("00f0ff");
        Color coreColor = Color.white;

        float farX = e.x + 1000f;
        float farY = e.y + 1500f;

        Lines.stroke(18f * alpha, mainColor);
        Lines.line(farX, farY, e.x, e.y);
        Lines.stroke(8f * alpha, coreColor);
        Lines.line(farX, farY, e.x, e.y);

        Lines.stroke(4f * (1f - f), mainColor);
        Lines.circle(e.x, e.y, 8f + (60f * f));

        Draw.reset();
    });

    private static final Effect meteorFallingEffect = new Effect(50f, e -> {
        Draw.z(Layer.effect + 5f);
        float f = e.fin();

        float portalX = e.x - 220f;
        float portalY = e.y + 450f;

        float currentX = Mathf.lerp(portalX, e.x, f);
        float currentY = Mathf.lerp(portalY, e.y, f);

        if (f < 0.85f) {
            float portalAlpha = f < 0.15f ? f / 0.15f : (0.85f - f) / 0.7f;

            TextureRegion starRegion = PerfCore.region("newex-star-field");
            if (!starRegion.found()) starRegion = PerfCore.region("star-field");

            if (starRegion.found()) {
                Draw.color(Color.white);
                Draw.alpha(portalAlpha * 0.8f);
                Draw.rect(starRegion, portalX, portalY, 108f, 108f, Time.time * 3f);
            }

            TextureRegion blackholeRegion = PerfCore.region("newex-blackhole-pulse");
            if (!blackholeRegion.found()) blackholeRegion = PerfCore.region("blackhole-pulse");

            if (blackholeRegion.found()) {
                Draw.color(Color.white);
                Draw.alpha(portalAlpha);
                Draw.rect(blackholeRegion, portalX, portalY, 90f, 90f, Time.time * -6f);
            }

            Draw.color(PerfCore.color("1a1721"));
            Draw.alpha(portalAlpha * 0.6f);
            Fill.circle(portalX, portalY, 18f + Mathf.sin(Time.time * 0.15f) * 2f);

            Draw.color(Color.black);
            Draw.alpha(portalAlpha);
            Fill.circle(portalX, portalY, 14f);
        }

        float waveAngle = 33f;
        float waveRadius = 6f + (28f * Interp.pow2Out.apply(f));
        Lines.stroke(2f * (1f - f), PerfCore.color("ffb380"));
        draw3DRotatedEllipseWave(currentX, currentY, waveRadius, waveRadius * 0.4f, waveAngle);

        float flightAngle = Angles.angle(portalX, portalY, e.x, e.y);
        float tailAngle = flightAngle + 180f;

        if (Mathf.chance(0.8)) {
            float tailX = currentX + Angles.trnsx(tailAngle, 6f);
            float tailY = currentY + Angles.trnsy(tailAngle, 6f);
            Fx.smoke.at(tailX + Mathf.range(3f), tailY + Mathf.range(3f));
        }

        TextureRegion meteorRegion = PerfCore.region("newex-basalt-bluff");
        if (!meteorRegion.found()) {
            meteorRegion = PerfCore.region("basalt-bluff");
        }

        if (meteorRegion.found()) {
            Draw.color(Color.white);
            float rotation = Time.time * 15f;
            Draw.rect(meteorRegion, currentX, currentY, 24f, 24f, rotation);
        } else {
            Draw.color(PerfCore.color("594e48"));
            Fill.circle(currentX, currentY, 9f);
        }

        Draw.reset();
    });

    private BalashilonBlock() {}

    // ------------------------------------------------------------------ nội dung

    /** Gọi từ NexMod.loadContent(): tạo viên đạn "xây dựng" trước khi game khởi tạo nội dung. */
    public static void create() {
        builderBulletType = new BasicBulletType(8f, 0f) {
            {
                width = 12f;
                height = 24f;
                shrinkY = 0f;
                lifetime = 180f;
                frontColor = Color.valueOf("00f0ff");
                backColor = Color.valueOf("00a2ff");
            }

            @Override
            public void despawned(Bullet b) {
                super.despawned(b);

                float tx = b.x;
                float ty = b.y;
                Team team = b.team;

                Fx.spawn.at(tx, ty);
                Fx.placeBlock.at(tx, ty, 4f);

                Tile centerTile = Vars.world.tileWorld(tx, ty);
                if (centerTile == null) return;

                Block blastDrill = Blocks.blastDrill;
                if (blastDrill != null) {
                    centerTile.setNet(blastDrill, team, 0);
                }

                Tile leftTile = Vars.world.tile(centerTile.x - 3, centerTile.y);
                Block batteryLarge = Vars.content.getByName(ContentType.block, "battery-large");

                if (leftTile != null && batteryLarge != null) {
                    leftTile.setNet(batteryLarge, team, 0);

                    Building pinBuild = leftTile.build;
                    if (pinBuild != null && pinBuild.power != null) {
                        pinBuild.power.status = 1f;
                    }
                    // JS gốc còn kiểm tra thuộc tính "energy" của Building (không tồn tại nên không bao giờ chạy).
                }

                Tile rightTile = Vars.world.tile(centerTile.x + 3, centerTile.y);
                Block container = Blocks.container;
                if (rightTile != null && container != null) {
                    rightTile.setNet(container, team, 0);
                }
            }
        };
    }

    public static void init() {
        NexNet.on("balashilon", (player, payload) -> {
            int pos;
            try {
                pos = Integer.parseInt(payload.trim());
            } catch (NumberFormatException err) {
                return;
            }
            Building b = Vars.world.build(pos);
            if (!(b instanceof BalashilonBuild)) return;
            if (player != null && b.team != player.team()) return;

            CoreBuild core = b.team.core();
            if (core == null || !core.items.has(Items.copper, METEOR_COST)) {
                NexNet.toastTo(player, "[red]Không đủ 200 Đồng![]", 2f);
                return;
            }

            core.items.remove(Items.copper, METEOR_COST);
            ((BalashilonBuild) b).queueMeteor();
        }, true, 250);

        Events.on(ClientLoadEvent.class, e -> setupBalashilon());
        Events.on(ServerLoadEvent.class, e -> setupBalashilon());
    }

    private static void setupBalashilon() {
        if (balashilonReady) return;

        balashilonBlock = Vars.content.getByName(ContentType.block, "newex-balashilon");
        if (balashilonBlock == null) {
            balashilonBlock = Vars.content.getByName(ContentType.block, "balashilon");
        }
        if (balashilonBlock == null) return;

        balashilonReady = true;
        balashilonBlock.configurable = true;
        balashilonBlock.update = true;

        balashilonBlock.buildType = BalashilonBuild::new;
    }

    private static final class Meteor {
        float timer;
        boolean falling;
        final float x, y;

        Meteor(float timer, float x, float y) {
            this.timer = timer;
            this.falling = false;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Shot {
        float timer;
        final float x, y;

        Shot(float timer, float x, float y) {
            this.timer = timer;
            this.x = x;
            this.y = y;
        }
    }

    public static class BalashilonBuild extends Building {
        final Seq<Meteor> meteorQueue = new Seq<>();
        final Seq<Shot> shootQueue = new Seq<>();

        public void queueMeteor() {
            NexNet.fx(balashilonLaserBeamEffect, x, y);
            NexNet.fx(Fx.shieldApply, x, y, 0f, PerfCore.color("00f0ff"));

            float delayTicks = Mathf.random(180f, 360f);
            float maxRadiusPixels = 150f * Vars.tilesize;
            float angle = Mathf.random(360f);
            float distance = Mathf.random(16f, maxRadiusPixels);

            meteorQueue.add(new Meteor(delayTicks, x + Angles.trnsx(angle, distance), y + Angles.trnsy(angle, distance)));
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();

            table.button(Icon.ok, Styles.cleari, 40f, () -> {
                CoreBuild teamCore = team.core();
                if (teamCore == null || !teamCore.items.has(Items.copper, METEOR_COST)) {
                    Vars.ui.showInfoToast("[red]Không đủ 200 Đồng![]", 2f);
                    return;
                }

                NexNet.send("balashilon", pos());
                deselect();
            }).size(50f, 40f).tooltip("Kích hoạt gọi Thiên thạch (Tốn 200 Đồng)");
        }

        @Override
        public void updateTile() {
            super.updateTile();
            if (Vars.net.client()) return;

            for (int i = meteorQueue.size - 1; i >= 0; i--) {
                Meteor item = meteorQueue.get(i);
                item.timer -= Time.delta;

                if (!item.falling && item.timer <= 50) {
                    item.falling = true;
                    NexNet.fx(meteorFallingEffect, item.x, item.y);
                }

                if (item.timer <= 0) {
                    spawnRandomMeteorOre(item.x, item.y);

                    shootQueue.add(new Shot(180f, item.x, item.y));

                    meteorQueue.remove(i);
                }
            }

            for (int i = shootQueue.size - 1; i >= 0; i--) {
                Shot s = shootQueue.get(i);
                s.timer -= Time.delta;

                if (s.timer <= 0) {
                    float angle = Angles.angle(x, y, s.x, s.y);
                    float dst = Mathf.dst(x, y, s.x, s.y);
                    float lifeScale = dst / (builderBulletType.speed * builderBulletType.lifetime);

                    Call.createBullet(builderBulletType, team, x, y, angle, 0f, 1f, lifeScale);

                    shootQueue.remove(i);
                }
            }
        }

        void spawnRandomMeteorOre(float tx, float ty) {
            Tile targetTile = Vars.world.tileWorld(tx, ty);
            if (targetTile == null) return;

            NexNet.fx(Fx.reactorExplosion, tx, ty);
            NexNet.fx(Fx.dynamicExplosion, tx, ty);
            NexNet.fx(Fx.smokeCloud, tx, ty);
            NexNet.fx(customShockwaveEffect, tx, ty);
            NexNet.shake(14f, 14f, tx, ty);

            Damage.damage(tx, ty, 30f * Vars.tilesize, 1000f);

            int destroyRadius = 15;
            int outerRadius = 30;

            for (int rx = -outerRadius; rx <= outerRadius; rx++) {
                for (int ry = -outerRadius; ry <= outerRadius; ry++) {
                    double dist = Math.sqrt(rx * rx + ry * ry);
                    Tile t = Vars.world.tile(targetTile.x + rx, targetTile.y + ry);

                    if (t != null && t.build != null) {
                        Building b = t.build;

                        if (dist <= destroyRadius) {
                            b.kill();
                        } else if (dist <= outerRadius) {
                            b.damage(1000f);
                        }
                    }
                }
            }

            Seq<Block> availableOres = new Seq<>();
            availableOres.add(Blocks.oreCopper);
            availableOres.add(Blocks.oreLead);
            availableOres.add(Blocks.oreCoal);
            availableOres.add(Blocks.oreTitanium);
            availableOres.add(Blocks.oreThorium);
            availableOres.add(Blocks.oreScrap);
            if (Blocks.oreBeryllium != null) availableOres.add(Blocks.oreBeryllium);
            if (Blocks.oreTungsten != null) availableOres.add(Blocks.oreTungsten);

            int oreIndex = (int) Math.floor(Mathf.random(0f, availableOres.size));
            Block selectedOre = oreIndex >= 0 && oreIndex < availableOres.size ? availableOres.get(oreIndex) : null;

            if (selectedOre == null) return;

            int oreRadius = (int) Math.floor(Mathf.random(2f, 5f));

            for (int rx = -oreRadius; rx <= oreRadius; rx++) {
                for (int ry = -oreRadius; ry <= oreRadius; ry++) {
                    double dist = Math.sqrt(rx * rx + ry * ry);

                    if (dist <= oreRadius && Mathf.chance(1.0 - (dist / (oreRadius + 1.0)))) {
                        Tile t = Vars.world.tile(targetTile.x + rx, targetTile.y + ry);

                        if (t != null && t.floor() != null && !t.floor().isLiquid) {
                            NexNet.setOverlay(t, selectedOre);
                        }
                    }
                }
            }
        }
    }
}
