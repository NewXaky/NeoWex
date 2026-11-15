package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import newex.NexNet;

/**
 * Chuyển từ generat-machine.js. Máy tạo tường: tiêu Raykstone làm nhiên liệu, định kỳ đặt 1 Redstone Wall
 * (hoặc Emeralift Wall khi có Pinfyr kề bên) lên ô phía trước; nhiều máy cùng nhắm 1 ô thì nhanh hơn.
 */
public final class GeneratMachine {
    private static final int[] DIR_X = {1, 0, -1, 0};
    private static final int[] DIR_Y = {0, 1, 0, -1};

    private static Block genMachine, redstoneWall, emeraliftWall, pinfyr;
    private static Item raykstone;

    private GeneratMachine() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            genMachine = Vars.content.block("newex-generat-machine");
            redstoneWall = Vars.content.block("newex-redstone-wall");
            emeraliftWall = Vars.content.block("newex-emeralift-wall");
            pinfyr = Vars.content.block("newex-pinfyr");
            raykstone = Vars.content.item("newex-raykstone");

            if (genMachine != null && redstoneWall != null && emeraliftWall != null && pinfyr != null && raykstone != null) {
                genMachine.rotate = true;
                genMachine.update = true;
                genMachine.hasItems = true;

                genMachine.buildType = GeneratBuild::new;
            }
        });
    }

    public static class GeneratBuild extends Building {
        float spawnTimer = 0f;
        float nextSpawnTime = 120f * 60f;
        float fuelTimer = 0f;
        boolean isPinfyrNearby = false;
        float checkTimer = 0f;

        @Override
        public void placed() {
            super.placed();
            resetTimer();
        }

        void resetTimer() {
            spawnTimer = 0f;
            if (isPinfyrNearby) {
                nextSpawnTime = 120f * 60f;
            } else {
                nextSpawnTime = Mathf.random(120f * 60f, 300f * 60f);
            }
        }

        boolean checkPinfyrNearby() {
            Tile targetTile = getTargetTile();
            int targetX = targetTile != null ? targetTile.x : -1;
            int targetY = targetTile != null ? targetTile.y : -1;

            boolean found = false;

            if (proximity != null) {
                for (int i = 0; i < proximity.size; i++) {
                    Building other = proximity.get(i);
                    if (other != null && other.block == pinfyr) {
                        boolean isTargetLocation = other.tileX() == targetX && other.tileY() == targetY;
                        if (!isTargetLocation) {
                            found = true;
                            break;
                        }
                    }
                }
            }
            return found;
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            return item == raykstone && items.get(raykstone) < block.itemCapacity;
        }

        Tile getTargetTile() {
            int rot = rotation & 3;
            int dx = DIR_X[rot];
            int dy = DIR_Y[rot];

            int tx = (int) Math.floor((x + dx * 16) / 8f);
            int ty = (int) Math.floor((y + dy * 16) / 8f);

            return Vars.world.tile(tx, ty);
        }

        int getFacingMachinesCount() {
            Tile target = getTargetTile();
            if (target == null) return 1;

            final int[] count = {0};
            final float targetX = target.worldx();
            final float targetY = target.worldy();

            Units.nearbyBuildings(targetX, targetY, 32f, b -> {
                if (b != null && b.block == genMachine) {
                    int rot = b.rotation & 3;
                    int bDx = DIR_X[rot];
                    int bDy = DIR_Y[rot];

                    float bTargetX = b.x + bDx * 16;
                    float bTargetY = b.y + bDy * 16;

                    if (Math.abs(bTargetX - targetX) < 4 && Math.abs(bTargetY - targetY) < 4) {
                        count[0]++;
                    }
                }
            });

            return Math.max(1, count[0]);
        }

        boolean canSpawn() {
            int rot = rotation & 3;
            int dx = DIR_X[rot];
            int dy = DIR_Y[rot];

            float spawnX = x + dx * 16;
            float spawnY = y + dy * 16;

            int startTileX = (int) Math.floor((spawnX - 8 + 4) / 8f);
            int startTileY = (int) Math.floor((spawnY - 8 + 4) / 8f);

            for (int tx = 0; tx < 2; tx++) {
                for (int ty = 0; ty < 2; ty++) {
                    Tile checkTile = Vars.world.tile(startTileX + tx, startTileY + ty);
                    if (checkTile == null) return false;

                    if (checkTile.build != null || checkTile.solid() || checkTile.block() != Blocks.air) {
                        return false;
                    }
                }
            }

            final boolean[] hasUnit = {false};
            Units.nearbyEnemies(team, spawnX - 8, spawnY - 8, 16f, 16f, u -> hasUnit[0] = true);

            if (!hasUnit[0]) {
                Units.nearby(team, spawnX - 8, spawnY - 8, 16f, 16f, u -> hasUnit[0] = true);
            }

            return !hasUnit[0];
        }

        boolean consumeFuel() {
            if (fuelTimer > 0) {
                fuelTimer -= Time.delta;
                return true;
            }

            if (items.has(raykstone, 1)) {
                items.remove(raykstone, 1);
                fuelTimer += 60f;
                return true;
            }

            return false;
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (efficiency <= 0) return;

            checkTimer += Time.delta;
            if (checkTimer >= 15) {
                checkTimer = 0f;
                boolean pinfyrDetected = checkPinfyrNearby();
                if (pinfyrDetected != isPinfyrNearby) {
                    isPinfyrNearby = pinfyrDetected;
                    resetTimer();
                }
            }

            if (canSpawn()) {
                boolean hasPowerFuel = consumeFuel();

                if (hasPowerFuel) {
                    int multiplier = getFacingMachinesCount();
                    spawnTimer += Time.delta * multiplier;

                    if (spawnTimer >= nextSpawnTime) {
                        Tile target = getTargetTile();
                        if (target != null) {
                            Block spawnBlock = isPinfyrNearby ? emeraliftWall : redstoneWall;
                            NexNet.setBlock(target, spawnBlock, team, 0);
                        }
                        resetTimer();
                    }
                }
            }
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            drawRangeOutline();
        }

        void drawRangeOutline() {
            int rot = rotation & 3;
            int dx = DIR_X[rot];
            int dy = DIR_Y[rot];

            float spawnX = x + dx * 16;
            float spawnY = y + dy * 16;

            float minX = spawnX - 8;
            float minY = spawnY - 8;
            float maxX = spawnX + 8;
            float maxY = spawnY + 8;

            boolean isReady = canSpawn() && (fuelTimer > 0 || items.has(raykstone, 1));
            Color outlineColor = isReady ? Pal.accent : Color.scarlet;

            Draw.z(Layer.power + 1);
            Drawf.dashLine(outlineColor, minX, minY, maxX, minY);
            Drawf.dashLine(outlineColor, maxX, minY, maxX, maxY);
            Drawf.dashLine(outlineColor, maxX, maxY, minX, maxY);
            Drawf.dashLine(outlineColor, minX, maxY, minX, minY);
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();

            if (efficiency > 0) {
                drawRangeOutline();

                if (canSpawn() && (fuelTimer > 0 || items.has(raykstone, 1))) {
                    int rot = rotation & 3;
                    int dx = DIR_X[rot];
                    int dy = DIR_Y[rot];

                    float spawnX = x + dx * 16;
                    float spawnY = y + dy * 16;

                    float progress = Math.min(1f, Math.max(0f, spawnTimer / nextSpawnTime));
                    Block spawnBlock = isPinfyrNearby ? emeraliftWall : redstoneWall;
                    TextureRegion region = spawnBlock.region;

                    if (region != null && region.found()) {
                        Draw.z(Layer.blockOver);
                        Draw.color(Color.white, progress);
                        Draw.rect(region, spawnX, spawnY, 16f * progress, 16f * progress);
                        Draw.reset();
                    }
                }
            }
        }
    }
}
