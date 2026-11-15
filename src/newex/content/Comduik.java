package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.FloatSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.world.Block;

/**
 * Chuyển từ comduik.js. Hấp thụ điện từ Redstone Wall ở mặt trước (vùng 16x40 px) và truyền điện
 * sang các công trình ở 3 mặt còn lại. Cài đặt "newex-redstone-fx": tắt = cập nhật 12 tick/lần (nhẹ máy),
 * bật = hiệu ứng và tính toán đầy đủ mỗi tick.
 */
public final class Comduik {
    private static final int[] DIR_X = {1, 0, -1, 0};
    private static final int[] DIR_Y = {0, 1, 0, -1};
    private static final float MAX_CAPACITY = 100000000f;

    private static final Color lyvervonLightningColor = Color.valueOf("7fd4ff");
    private static final Color transferColor = Color.valueOf("00ffcc");

    private static final FloatSeq lightningPointsFloat = new FloatSeq();

    @SuppressWarnings("unchecked")
    private static final Effect lyvervonLightningEffectOld = new Effect(10f, e -> {
        if (!(e.data instanceof Seq)) return;
        Seq<Vec2> points = (Seq<Vec2>) e.data;
        float thickness = e.rotation > 0 ? e.rotation : 2.5f;
        Draw.color(lyvervonLightningColor, Color.white, e.fin());
        Lines.stroke(thickness * e.fout());
        for (int i = 0; i < points.size - 1; i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get(i + 1);
            Lines.line(a.x, a.y, b.x, b.y, false);
        }
        Draw.reset();
    });

    private static final Effect comduikTransferFxOld = new Effect(30f, e -> {
        Draw.z(Layer.effect + 0.05f);
        float targetSize = (e.data instanceof Number) ? ((Number) e.data).floatValue() : 16f;
        float baseRadius = (targetSize * (2f / 3f)) / 2f;
        float pulse = Mathf.sin(e.fin() * Mathf.PI * 2f) * (baseRadius * 0.25f);
        float currentRadius = Math.max(2f, baseRadius + pulse);
        float alpha = 0.8f * (1f - e.fin());

        Draw.color(transferColor);
        Draw.alpha(alpha * 0.25f);
        Fill.circle(e.x, e.y, currentRadius);

        Draw.color(transferColor);
        Draw.alpha(alpha);
        Lines.stroke(2f * (1f - e.fin()));
        Lines.circle(e.x, e.y, currentRadius);
        Draw.reset();
    });

    private static final Effect helTransferOld = new Effect(25f, e -> {
        Draw.z(Layer.effect + 0.01f);
        if (!(e.data instanceof float[])) return;
        float[] t = (float[]) e.data;

        float x1 = e.x, y1 = e.y;
        float x2 = t[0], y2 = t[1];
        float alpha = 1f - e.fin();

        float colorRand = Mathf.randomSeed(e.id + (long) Math.floor(e.fin() * 10));
        Draw.color(transferColor, Color.white, colorRand);
        Lines.stroke(1.2f * alpha);

        int segments = 6;
        float lastX = x1, lastY = y1;

        for (int i = 1; i <= segments; i++) {
            float progress = (float) i / segments;
            float nx = Mathf.lerp(x1, x2, progress);
            float ny = Mathf.lerp(y1, y2, progress);

            if (i < segments) {
                float offset = (1f - Math.abs(progress - 0.5f) * 2f) * 4f;
                float randX = Mathf.randomSeed(e.id * 100L + i + (long) Math.floor(e.fin() * 5), -offset, offset);
                float randY = Mathf.randomSeed(e.id * 200L + i + (long) Math.floor(e.fin() * 5), -offset, offset);
                nx += randX;
                ny += randY;
            }

            Lines.line(lastX, lastY, nx, ny);
            lastX = nx;
            lastY = ny;
        }
        Draw.reset();
    });

    private static final Effect lyvervonLightningEffectNew = new Effect(8f, e -> {
        if (!(e.data instanceof FloatSeq)) return;
        FloatSeq points = (FloatSeq) e.data;
        if (points.size < 4) return;

        Draw.color(lyvervonLightningColor, Color.white, e.fin());
        Lines.stroke((e.rotation > 0 ? e.rotation : 2f) * e.fout());
        for (int i = 0; i < points.size - 2; i += 2) {
            Lines.line(points.get(i), points.get(i + 1), points.get(i + 2), points.get(i + 3), false);
        }
        Draw.reset();
    });

    private static final Effect comduikTransferFxNew = new Effect(15f, e -> {
        Draw.z(Layer.effect + 0.05f);
        float targetSize = (e.data instanceof Number) ? ((Number) e.data).floatValue() : 16f;
        float baseRadius = targetSize * 0.33f;

        Draw.color(transferColor);
        Draw.alpha(0.5f * e.fout());
        Lines.stroke(1.2f * e.fout());
        Lines.circle(e.x, e.y, baseRadius);
        Draw.reset();
    });

    private static Block redstoneWall = null;

    private Comduik() {}

    private static void createLyvervonLightningReverseOld(float wallX, float wallY, float comduikX, float comduikY, float thickness) {
        float dst = Mathf.dst(wallX, wallY, comduikX, comduikY);
        int segs = Math.max(4, (int) Math.floor(dst / 6f));
        Seq<Vec2> points = new Seq<>();

        points.add(new Vec2(wallX, wallY));
        float angle = Angles.angle(wallX, wallY, comduikX, comduikY);

        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(wallX, comduikX, t);
            float py = Mathf.lerp(wallY, comduikY, t);

            float noise = Mathf.range(8f);
            Tmp.v1.trns(angle + 90f, noise);
            points.add(new Vec2(px + Tmp.v1.x, py + Tmp.v1.y));
        }
        points.add(new Vec2(comduikX, comduikY));

        lyvervonLightningEffectOld.at((wallX + comduikX) / 2f, (wallY + comduikY) / 2f, thickness, points);
    }

    private static void createLyvervonLightningReverseNew(float wallX, float wallY, float comduikX, float comduikY, float thickness) {
        float dst = Mathf.dst(wallX, wallY, comduikX, comduikY);
        int segs = Math.max(2, (int) Math.floor(dst / 10f));

        lightningPointsFloat.clear();
        lightningPointsFloat.add(wallX, wallY);
        float angle = Angles.angle(wallX, wallY, comduikX, comduikY);

        for (int i = 1; i < segs; i++) {
            float t = (float) i / segs;
            float px = Mathf.lerp(wallX, comduikX, t);
            float py = Mathf.lerp(wallY, comduikY, t);

            float noise = Mathf.range(5f);
            Tmp.v1.trns(angle + 90f, noise);
            lightningPointsFloat.add(px + Tmp.v1.x, py + Tmp.v1.y);
        }
        lightningPointsFloat.add(comduikX, comduikY);

        lyvervonLightningEffectNew.at((wallX + comduikX) / 2f, (wallY + comduikY) / 2f, thickness, lightningPointsFloat);
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block comduik = Vars.content.block("newex-comduik");
            redstoneWall = Vars.content.block("newex-redstone-wall");

            if (comduik != null && redstoneWall != null) {
                comduik.rotate = true;
                comduik.update = true;
                comduik.hasPower = true;

                comduik.consumePowerBuffered(MAX_CAPACITY);

                comduik.addBar("power", (Building b) -> new Bar(
                    () -> "Power: " + (long) Math.floor((double) b.power.status * b.block.consPower.capacity)
                        + " / " + (long) b.block.consPower.capacity,
                    () -> Pal.powerBar,
                    () -> b.power.status
                ));

                comduik.buildType = ComduikBuild::new;
            }
        });
    }

    /** Vùng hấp thụ phía trước (pixel thế giới). */
    private static final class Bounds {
        final float minX, minY, maxX, maxY;

        Bounds(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }
    }

    public static class ComduikBuild extends Building {
        float lightningTimer = 0f;
        float transferTimer = 0f;
        final Seq<Building> activeTargetsList = new Seq<>();
        Seq<Building> activeTargets = new Seq<>();
        int updateInterval = 0;

        Bounds getRangeBounds() {
            int rot = rotation & 3;
            int dx = DIR_X[rot];
            int dy = DIR_Y[rot];

            float minX = (dx > 0) ? x + 8 : ((dx < 0) ? x - 48 : x - 8);
            float minY = (dy > 0) ? y + 8 : ((dy < 0) ? y - 48 : y - 8);
            float maxX = minX + ((dx != 0) ? 40 : 16);
            float maxY = minY + ((dy != 0) ? 40 : 16);

            return new Bounds(minX, minY, maxX, maxY);
        }

        @Override
        public void updateTile() {
            super.updateTile();

            boolean useFullFx = Core.settings.getBool("newex-redstone-fx", false);

            if (!useFullFx) {
                updateOptimized();
            } else {
                updateFull();
            }
        }

        private void updateOptimized() {
            updateInterval++;
            if (updateInterval < 12) return;
            int deltaMultiplier = updateInterval;
            updateInterval = 0;

            Bounds bounds = getRangeBounds();
            activeTargetsList.clear();

            int startTileX = (int) Math.floor(bounds.minX / Vars.tilesize);
            int startTileY = (int) Math.floor(bounds.minY / Vars.tilesize);
            int endTileX = (int) Math.floor(bounds.maxX / Vars.tilesize);
            int endTileY = (int) Math.floor(bounds.maxY / Vars.tilesize);

            for (int tx = startTileX; tx <= endTileX; tx++) {
                for (int ty = startTileY; ty <= endTileY; ty++) {
                    Building build = Vars.world.build(tx, ty);
                    if (build != null && build.block == redstoneWall && !activeTargetsList.contains(build, true)) {
                        activeTargetsList.add(build);
                    }
                }
            }

            int targetCount = activeTargetsList.size;
            if (targetCount > 0) {
                if (power != null) {
                    double capacity = block.consPower.capacity;
                    double currentPower = power.status * capacity;
                    double newPower = Math.min(capacity, currentPower + (800.0 / 60.0) * targetCount * deltaMultiplier);
                    power.status = (float) (newPower / capacity);
                }

                for (int i = 0; i < targetCount; i++) {
                    Building b = activeTargetsList.get(i);
                    if (b instanceof ComduikDrainable) ((ComduikDrainable) b).applyComduikDrain();
                }

                lightningTimer += deltaMultiplier;
                if (lightningTimer >= 30) {
                    lightningTimer = 0f;
                    Building randomTarget = activeTargetsList.random();
                    if (randomTarget != null) {
                        createLyvervonLightningReverseNew(randomTarget.x, randomTarget.y, x, y, 2f);
                    }
                }
            }

            if (power != null && power.status > 0) {
                double capacity = block.consPower.capacity;
                double currentStored = power.status * capacity;

                transferTimer += deltaMultiplier;
                boolean canPlayFx = false;
                if (transferTimer >= 24) {
                    transferTimer = 0f;
                    canPlayFx = true;
                }

                int rot = rotation & 3;
                int[] sideDirections = {(rot + 1) % 4, (rot + 2) % 4, (rot + 3) % 4};

                for (int i = 0; i < sideDirections.length; i++) {
                    int dir = sideDirections[i];
                    int checkTileX = tileX() + DIR_X[dir] * block.size;
                    int checkTileY = tileY() + DIR_Y[dir] * block.size;

                    Building target = Vars.world.build(checkTileX, checkTileY);
                    if (target != null && target != this && target.team == team && target.power != null && target.block.consPower != null) {
                        double targetCap = target.block.consPower.capacity;
                        if (targetCap > 0) {
                            double targetCurrent = target.power.status * targetCap;
                            double needed = targetCap - targetCurrent;

                            if (needed > 0 && currentStored > 0) {
                                double transfer = Math.min(currentStored, Math.min(needed, (800.0 / 60.0) * deltaMultiplier));
                                currentStored -= transfer;
                                target.power.status = (float) Math.min(1.0, (targetCurrent + transfer) / targetCap);

                                if (canPlayFx) {
                                    comduikTransferFxNew.at(target.x, target.y, 0f, Integer.valueOf(target.block.size * Vars.tilesize));
                                }
                            }
                        }
                    }
                }
                power.status = (float) (currentStored / capacity);
            }
        }

        private void updateFull() {
            final Bounds bounds = getRangeBounds();
            activeTargets = new Seq<>();
            final Seq<Building> found = activeTargets;

            Units.nearbyBuildings(
                (bounds.minX + bounds.maxX) / 2f,
                (bounds.minY + bounds.maxY) / 2f,
                48f,
                b -> {
                    if (b != null && b.block == redstoneWall) {
                        if (b.x >= bounds.minX && b.x <= bounds.maxX && b.y >= bounds.minY && b.y <= bounds.maxY) {
                            found.add(b);

                            if (power != null) {
                                double capacity = block.consPower.capacity;
                                double currentPower = power.status * capacity;
                                double newPower = Math.min(capacity, currentPower + (800.0 / 60.0));
                                power.status = (float) (newPower / capacity);
                            }

                            if (b instanceof ComduikDrainable) {
                                ((ComduikDrainable) b).applyComduikDrain();
                            }

                            lightningTimer += Time.delta;
                            if (lightningTimer >= 30) {
                                lightningTimer = 0f;
                                createLyvervonLightningReverseOld(b.x, b.y, x, y, 2.5f);
                                try {
                                    Fx.hitLancer.at(x, y, lyvervonLightningColor);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                }
            );

            if (power != null && power.status > 0) {
                final double capacity = block.consPower.capacity;
                final double[] currentStored = {power.status * capacity};

                int rot = rotation & 3;
                final int[] sideDirections = {(rot + 1) % 4, (rot + 2) % 4, (rot + 3) % 4};

                transferTimer += Time.delta;
                final boolean[] canPlayFx = {false};
                if (transferTimer >= 15) {
                    transferTimer = 0f;
                    canPlayFx[0] = true;
                }

                final IntSet processedTargets = new IntSet();

                Units.nearbyBuildings(x, y, (block.size * 8) + 16f, target -> {
                    if (target == null || target == this || target.team != team || processedTargets.contains(target.id)) return;

                    boolean isSideTarget = false;
                    for (int i = 0; i < sideDirections.length; i++) {
                        int dir = sideDirections[i];
                        float checkX = x + DIR_X[dir] * (block.size * 4 + 4);
                        float checkY = y + DIR_Y[dir] * (block.size * 4 + 4);

                        if (target.dst(checkX, checkY) <= (target.block.size * 8)) {
                            isSideTarget = true;
                            break;
                        }
                    }

                    if (isSideTarget && target.power != null && target.block.consPower != null) {
                        processedTargets.add(target.id);
                        double targetCap = target.block.consPower.capacity;

                        if (targetCap > 0) {
                            double targetCurrent = target.power.status * targetCap;
                            double needed = targetCap - targetCurrent;

                            if (needed > 0 && currentStored[0] > 0) {
                                double transfer = Math.min(currentStored[0], Math.min(needed, 800.0 / 60.0));
                                currentStored[0] -= transfer;

                                target.power.status = (float) Math.min(1.0, (targetCurrent + transfer) / targetCap);

                                if (canPlayFx[0]) {
                                    int targetPixelSize = target.block.size * Vars.tilesize;
                                    comduikTransferFxOld.at(target.x, target.y, 0f, Integer.valueOf(targetPixelSize));
                                    helTransferOld.at(x, y, 0f, new float[]{target.x, target.y});
                                }
                            }
                        }
                    }
                });

                power.status = (float) (currentStored[0] / capacity);
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Bounds bounds = getRangeBounds();
            boolean useFullFx = Core.settings.getBool("newex-redstone-fx", false);
            int targetCount = useFullFx ? (activeTargets != null ? activeTargets.size : 0) : activeTargetsList.size;
            Color outlineColor = (targetCount > 0) ? Pal.accent : Color.scarlet;

            Draw.z(Layer.power + 1);
            Drawf.dashLine(outlineColor, bounds.minX, bounds.minY, bounds.maxX, bounds.minY);
            Drawf.dashLine(outlineColor, bounds.maxX, bounds.minY, bounds.maxX, bounds.maxY);
            Drawf.dashLine(outlineColor, bounds.maxX, bounds.maxY, bounds.minX, bounds.maxY);
            Drawf.dashLine(outlineColor, bounds.minX, bounds.maxY, bounds.minX, bounds.minY);
            Draw.reset();
        }
    }
}
