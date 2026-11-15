package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.power.Battery;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.blocks.power.PowerGraph;
import newex.PerfCore;

/**
 * Chuyển từ uranilum-reactor.js. Lò phản ứng Uranilum: nhận tia từ 4 Laser Charger (mỗi tick addLaser()),
 * đủ 4 tia thì tích điện 15 giây rồi đạt công suất tối đa (x10), truyền 10k điện/giây cho khối mục tiêu trong 150 ô.
 * Mất đủ 4 tia khi đã tích hơn 5 giây, hoặc bị phá khi đang tích điện, thì tạo quả cầu bất ổn phát nổ sau 60 giây.
 */
public final class UranilumReactor {
    private static final Color lightningColor = Color.valueOf("7fd4ff");
    private static final Color decayLightningColor = Color.valueOf("a957ff");

    private static final Effect shockwaveEffect = new Effect(20f, e -> {
        Draw.color(e.color, Color.white, e.fin());
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 4f * Vars.tilesize);
    });

    private static final Effect absorbLightningEffect = new Effect(25f, e -> {
        Draw.color(e.color, Color.white, e.fout());
        Lines.stroke(2f * e.fout());
        float radius = (1f - e.fin()) * 4f * Vars.tilesize;
        Lines.circle(e.x, e.y, radius);
    });

    @SuppressWarnings("unchecked")
    private static final Effect progressiveLightningEffect = new Effect(18f, e -> {
        if (e.data == null || !(e.data instanceof Seq)) return;
        Seq<Vec2> points = (Seq<Vec2>) e.data;
        if (points.size <= 1) return;

        float thickness = e.rotation > 0 ? e.rotation : 2.8f;
        float progress = Math.min(1f, e.fin() * 1.5f);
        int maxIndex = (int) Math.floor((points.size - 1) * progress);

        Draw.color(e.color, Color.white, e.fin());
        Lines.stroke(thickness * e.fout());

        for (int i = 0; i < maxIndex; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
    });

    private static void createProgressiveLightning(float x1, float y1, float x2, float y2, float thickness, Color color) {
        float dst = Mathf.dst(x1, y1, x2, y2);
        int segs = Math.max(5, (int) Math.floor(dst / 7f));
        Seq<Vec2> points = new Seq<>();
        points.add(new Vec2(x1, y1));
        float angle = Angles.angle(x1, y1, x2, y2);

        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(x1, x2, t);
            float py = Mathf.lerp(y1, y2, t);

            float jitter = 10f;
            float noise = Mathf.range(jitter);

            Tmp.v1.trns(angle + 90f, noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(x2, y2));

        Color effColor = color != null ? color : lightningColor;
        progressiveLightningEffect.at((x1 + x2) / 2f, (y1 + y2) / 2f, thickness, effColor, points);
    }

    private static final Effect unstableOrbEffect = new Effect(3600f, e -> {
        float chargeProgress = 1f - e.fin();
        float mainOrbRadius = (15f + Mathf.sin(e.time / 8f) * 0.8f) * Math.max(0.3f, chargeProgress);

        Color cOuter = Color.valueOf("8b38fb");
        Color cMid = Color.valueOf("b866ff");
        Color cCore = Color.valueOf("6a1fca");
        Color cLightning = decayLightningColor;

        Draw.z(Layer.effect + 2f);
        if (mainOrbRadius > 0.5f) {
            Draw.color(cOuter, 0.3f * chargeProgress);
            Fill.circle(e.x, e.y, mainOrbRadius + 10f * chargeProgress);

            Draw.color(cMid, 0.5f * chargeProgress);
            Fill.circle(e.x, e.y, mainOrbRadius + 4f * chargeProgress);

            Draw.color(cCore);
            Fill.circle(e.x, e.y, mainOrbRadius);

            Draw.color(cMid, 0.8f);
            Fill.circle(e.x, e.y, mainOrbRadius * 0.65f);

            Draw.color(Color.white, 0.8f * chargeProgress);
            Fill.circle(e.x, e.y, mainOrbRadius * 0.25f);

            if (Mathf.chance(0.45 * chargeProgress) && !Vars.state.isPaused()) {
                float randAngle = Mathf.random(360f);
                float randDist = Mathf.random(45f, 105f) * chargeProgress * Vars.tilesize;

                final float targetX = e.x + Angles.trnsx(randAngle, randDist);
                final float targetY = e.y + Angles.trnsy(randAngle, randDist);

                createProgressiveLightning(e.x, e.y, targetX, targetY, 3.5f * chargeProgress, cLightning);

                if (Mathf.chance(0.5)) {
                    shockwaveEffect.at(targetX, targetY, 0f, cLightning);
                }

                Fx.blastExplosion.at(targetX, targetY);

                final float destroyRadius = 45f;
                Vars.indexer.allBuildings(targetX, targetY, destroyRadius, build -> build.kill());

                Units.nearby(targetX - destroyRadius, targetY - destroyRadius, destroyRadius * 2f, destroyRadius * 2f, unit -> {
                    if (unit.within(targetX, targetY, destroyRadius)) {
                        unit.kill();
                    }
                });
            }
        }

        if (e.time >= 3590f) {
            Fx.impactReactorExplosion.at(e.x, e.y);

            final float ex = e.x, ey = e.y;
            final float explosionRadius = 60f * Vars.tilesize;

            Vars.indexer.allBuildings(ex, ey, explosionRadius, build -> build.kill());

            Units.nearby(ex - explosionRadius, ey - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, unit -> {
                if (unit.within(ex, ey, explosionRadius)) {
                    unit.kill();
                }
            });
        }
    });

    private UranilumReactor() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.block("newex-uranilum-reactor");
            if (b instanceof ImpactReactor) {
                final ImpactReactor reactor = (ImpactReactor) b;
                reactor.configurable = true;
                reactor.buildType = () -> new ReactorBuild(reactor);
            }
        });
    }

    private static final class MiniOrb {
        final float speedX, speedY, phase, radius;

        MiniOrb(float speedX, float speedY, float phase, float radius) {
            this.speedX = speedX;
            this.speedY = speedY;
            this.phase = phase;
            this.radius = radius;
        }
    }

    /** ImpactReactorBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class ReactorBuild extends ImpactReactor.ImpactReactorBuild implements LaserReceiver {
        int activeLasers = 0;
        boolean isSupercharged = false;
        Building targetBuilding = null;

        float chargeTimer = 0f;
        final float chargeMaxTime = 15f * 60f;

        Seq<MiniOrb> miniOrbs = new Seq<>();
        float seedOffset = 0f;
        float animTime = 0f;

        public ReactorBuild(ImpactReactor outer) {
            outer.super();
        }

        @Override
        public void created() {
            super.created();
            seedOffset = Mathf.random(1000f);
            initMiniOrbs();
        }

        void initMiniOrbs() {
            float count = Mathf.random(1f, 3f);
            miniOrbs = new Seq<>();
            for (int i = 0; i < count; i++) {
                miniOrbs.add(new MiniOrb(
                    Mathf.random(0.5f, 1.5f),
                    Mathf.random(0.5f, 1.5f),
                    Mathf.random(100f),
                    Mathf.random(2f, 3.5f)
                ));
            }
        }

        @Override
        public void onDestroyed() {
            if (activeLasers >= 4 || chargeTimer > 60) {
                unstableOrbEffect.at(x, y);
            }
            super.onDestroyed();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (!Vars.state.isPaused()) {
                animTime += Time.delta;
            }

            boolean has4Lasers = activeLasers >= 4;
            boolean isGeneratingPower = warmup > 0.5f && productionEfficiency > 0;

            if (has4Lasers && isGeneratingPower) {
                if (chargeTimer < chargeMaxTime) {
                    chargeTimer += Time.delta;
                }

                float chargeProgress = Math.min(1f, chargeTimer / chargeMaxTime);
                productionEfficiency = 1f + (chargeProgress * 9f);

                if (chargeProgress >= 1f) {
                    if (!isSupercharged) {
                        initMiniOrbs();
                        Call.announce("[gold]LÒ PHẢN ỨNG ĐÃ ĐẠT CÔNG SUẤT TỐI ĐA![]");
                    }
                    isSupercharged = true;

                    if (targetBuilding != null) {
                        float maxDist = 150f * Vars.tilesize;
                        if (!targetBuilding.isValid() || dst(targetBuilding) > maxDist) {
                            targetBuilding = null;
                        }
                    }

                    if (targetBuilding != null && targetBuilding.power != null) {
                        double transferAmount = 10000.0 / 60.0;
                        PowerGraph graph = targetBuilding.power.graph;

                        if (graph != null && graph.batteries != null && graph.batteries.size > 0) {
                            double pPerBattery = transferAmount / graph.batteries.size;
                            for (int i = 0; i < graph.batteries.size; i++) {
                                Building bat = graph.batteries.get(i);
                                if (bat != null && bat.power != null) {
                                    bat.power.status = (float) Math.min(1.0, bat.power.status + (pPerBattery / bat.block.consPower.capacity));
                                }
                            }
                        } else {
                            targetBuilding.power.status = 1f;
                        }
                    }
                }
            } else {
                if (chargeTimer > 300 && !has4Lasers) {
                    unstableOrbEffect.at(x, y);
                    kill();
                    return;
                } else {
                    chargeTimer = Math.max(0f, chargeTimer - Time.delta * 1.5f);
                }
                isSupercharged = false;
            }

            activeLasers = 0;
        }

        @Override
        public void addLaser() {
            activeLasers++;
        }

        @Override
        public void buildConfiguration(Table table) {
            super.buildConfiguration(table);

            table.button(Icon.power, Styles.cleari, 40f, () -> {
                configure(targetBuilding != null ? null : Integer.valueOf(pos()));
            }).size(40f).tooltip("Chọn khối mục tiêu trong phạm vi 150 ô để truyền 10k điện/s");
        }

        @Override
        public boolean onConfigureBuildTapped(Building other) {
            if (this == other) {
                targetBuilding = null;
                return true;
            }

            if (other != null && other.power != null) {
                if (dst(other) <= 150f * Vars.tilesize) {
                    targetBuilding = other;
                    return false;
                }
            }

            return true;
        }

        @Override
        public void draw() {
            super.draw();

            if (chargeTimer > 0) {
                Draw.z(Layer.effect + 2f);

                float chargeProgress = Math.min(1f, chargeTimer / chargeMaxTime);
                float mainOrbRadius = (15f + Mathf.sin(animTime / 8f) * 0.8f) * chargeProgress;

                Color cOuter = Color.valueOf("389bfb");
                Color cMid = Color.valueOf("66b1ff");
                Color cCore = Color.valueOf("1f65ca");
                Color cLightning = lightningColor;

                if (mainOrbRadius > 0.5f) {
                    Draw.color(cOuter, 0.25f * chargeProgress);
                    Fill.circle(x, y, mainOrbRadius + 10f * chargeProgress);

                    Draw.color(cMid, 0.45f * chargeProgress);
                    Fill.circle(x, y, mainOrbRadius + 4f * chargeProgress);

                    Draw.color(cCore);
                    Fill.circle(x, y, mainOrbRadius);

                    Draw.color(cMid, 0.75f);
                    Fill.circle(x, y, mainOrbRadius * 0.65f);

                    Draw.color(Color.white, 0.7f * chargeProgress);
                    Fill.circle(x, y, mainOrbRadius * 0.25f);

                    if (Mathf.chance(0.4 * chargeProgress) && !Vars.state.isPaused()) {
                        float randAngle = Mathf.random(360f);
                        float randDist = Mathf.random(15f, 35f) * chargeProgress * Vars.tilesize;

                        float targetX = x + Angles.trnsx(randAngle, randDist);
                        float targetY = y + Angles.trnsy(randAngle, randDist);

                        createProgressiveLightning(x, y, targetX, targetY, 2.8f * chargeProgress, cLightning);

                        boolean isGroundHit = Mathf.chance(0.5);
                        if (isGroundHit) {
                            shockwaveEffect.at(targetX, targetY, 0f, cLightning);
                        }

                        Vars.indexer.eachBlock(team, targetX, targetY, 4f * Vars.tilesize, b -> true, targetBuild -> {
                            boolean isCharged = false;

                            if (targetBuild.block instanceof Battery) {
                                float capacity = targetBuild.block.consPower.capacity;
                                if (capacity > 0 && targetBuild.power != null) {
                                    float addStatus = 1000f / capacity;
                                    targetBuild.power.status = Math.min(1f, targetBuild.power.status + addStatus);
                                    isCharged = true;
                                }
                            }

                            if (isCharged) {
                                absorbLightningEffect.at(targetBuild.x, targetBuild.y, 0f, cLightning);
                            }
                        });
                    }

                    if (isSupercharged) {
                        float maxAllowedOffset = mainOrbRadius * 0.4f;

                        for (int i = 0; i < miniOrbs.size; i++) {
                            MiniOrb orb = miniOrbs.get(i);

                            float offsetX = Mathf.sin((animTime * 0.04f * orb.speedX) + orb.phase + seedOffset) * maxAllowedOffset;
                            float offsetY = Mathf.cos((animTime * 0.03f * orb.speedY) + orb.phase + seedOffset) * maxAllowedOffset;

                            float mx = x + offsetX;
                            float my = y + offsetY;

                            Draw.color(PerfCore.color("99d1ff"));
                            Fill.circle(mx, my, orb.radius + 1f);

                            Draw.color(Color.white);
                            Fill.circle(mx, my, orb.radius);
                        }
                    }
                }

                if (isSupercharged && targetBuilding != null && targetBuilding.isValid()) {
                    Draw.z(Layer.power + 2f);

                    float beamWidth = 5f + Mathf.absin(animTime, 1.5f, 1.5f);

                    Draw.color(PerfCore.color("389bfb"), 0.4f);
                    Lines.stroke(beamWidth + 4f);
                    Lines.line(x, y, targetBuilding.x, targetBuilding.y);

                    Draw.color(PerfCore.color("66b1ff"));
                    Lines.stroke(beamWidth);
                    Lines.line(x, y, targetBuilding.x, targetBuilding.y);

                    Draw.color(Color.white);
                    Lines.stroke(beamWidth * 0.4f);
                    Lines.line(x, y, targetBuilding.x, targetBuilding.y);

                    if (Mathf.chance(0.5) && !Vars.state.isPaused()) {
                        createProgressiveLightning(x, y, targetBuilding.x, targetBuilding.y, 3.2f, lightningColor);
                    }
                }

                Draw.reset();
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawLaserPositions();
            drawTargetLink();
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            drawLaserPositions();
            drawTargetLink();

            Draw.z(Layer.power);
            Draw.color(PerfCore.color("66b1ff"), 0.35f);
            Lines.stroke(1.5f);
            Lines.circle(x, y, 150f * Vars.tilesize);
            Draw.reset();
        }

        void drawTargetLink() {
            if (targetBuilding != null && targetBuilding.isValid()) {
                Draw.z(Layer.power);
                Draw.color(PerfCore.color("66b1ff"));
                Lines.stroke(2f);
                Lines.square(targetBuilding.x, targetBuilding.y, (targetBuilding.block.size * Vars.tilesize) / 2f + 2f);
                Lines.dashLine(x, y, targetBuilding.x, targetBuilding.y, 8);
                Draw.reset();
            }
        }

        void drawLaserPositions() {
            final float dist = 15f * Vars.tilesize;
            final float[][] targetPositions = {
                {x - dist, y},
                {x + dist, y},
                {x, y - dist},
                {x, y + dist}
            };

            Draw.z(Layer.power);

            for (int i = 0; i < targetPositions.length; i++) {
                float wx = targetPositions[i][0];
                float wy = targetPositions[i][1];

                Tile tile = Vars.world.tileWorld(wx, wy);
                boolean isPlaced = tile != null && tile.build != null
                    && tile.build.block.name.endsWith("laser-charger-uranilum")
                    && Math.abs(tile.build.x - wx) < 8 && Math.abs(tile.build.y - wy) < 8;

                if (isPlaced) {
                    Draw.color(PerfCore.color("66b1ff"));
                } else {
                    Draw.color(Color.orange, Color.white, Mathf.absin(animTime, 4f, 0.3f));
                }

                Lines.stroke(1.5f);
                Lines.dashLine(x, y, wx, wy, 12);
                Lines.stroke(2f);
                Lines.square(wx, wy, Vars.tilesize);
            }

            Draw.reset();
        }
    }
}
