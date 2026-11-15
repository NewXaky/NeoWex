package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import newex.NexNet;
import newex.PerfCore;

/**
 * Chuyển từ laser-brey.js. Laser phá địa hình: bắn 2 tia song song phía trước (tầm 5 ô), mỗi giây có thể sinh Raykstone
 * (hoặc Uranilum Shard 10%) từ tường tĩnh / Raykstone, và phá ô sau 60 giây. Đứng gần Emeralift Wall (bán kính 5, không tính phía trước)
 * thì mở rộng thành vùng vuông tự tìm mục tiêu và tăng tốc.
 */
public final class LaserBrey {
    private static final String RAYKSTONE_NAME = "newex-raykstone";
    private static final String CORE_RAYKSTONE_NAME = "newex-core-raykstone";
    private static final String[] EMERALIFT_NAMES = {"emeralift-wall", "newex-emeralift-wall"};
    private static final float BREAK_TIME = 60f * 60f;
    private static final int BUFF_RADIUS = 5;

    private static final int[] DIR_X = {1, 0, -1, 0};
    private static final int[] DIR_Y = {0, 1, 0, -1};

    private static final Seq<Block> emeraliftBlocks = new Seq<>();
    private static Item raykItem = null;
    private static Item uraniumShardItem = null;

    private LaserBrey() {}

    /** Lấy hiệu ứng theo tên trong Fx; trả null nếu không có (tương đương JS: truy cập thuộc tính không tồn tại). */
    private static Effect fxByName(String name) {
        try {
            return (Effect) Fx.class.getField(name).get(null);
        } catch (Throwable err) {
            return null;
        }
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block laserBrey = Vars.content.block("newex-laser-brey");

            if (laserBrey != null) {
                laserBrey.rotate = true;
                laserBrey.drawArrow = false;
                laserBrey.hasItems = true;
                laserBrey.itemCapacity = 100;

                laserBrey.buildType = BreyBuild::new;
            }
        });

        Events.run(Trigger.draw, LaserBrey::drawPlacement);
    }

    private static void drawPlacement() {
        if (Vars.control == null || Vars.control.input == null) return;
        Block block = Vars.control.input.block;
        if (block == null || !"newex-laser-brey".equals(block.name)) return;

        float cursorX = Core.input.mouseWorldX();
        float cursorY = Core.input.mouseWorldY();

        int tileX = (int) Math.floor(cursorX / 8f);
        int tileY = (int) Math.floor(cursorY / 8f);
        float worldX = tileX * 8 + 4;
        float worldY = tileY * 8 + 4;

        int rot = Vars.control.input.rotation & 3;
        int dirX = DIR_X[rot];
        int dirY = DIR_Y[rot];

        float startX = worldX + dirX * 8;
        float startY = worldY + dirY * 8;

        float pX = -DIR_Y[rot] * 4;
        float pY = DIR_X[rot] * 4;
        float range = 5 * 8;

        Drawf.dashLine(Pal.accent, startX + pX, startY + pY, startX + pX + dirX * range, startY + pY + dirY * range);
        Drawf.dashLine(Pal.accent, startX - pX, startY - pY, startX - pX + dirX * range, startY - pY + dirY * range);

        Draw.z(Layer.turret + 2f);
        Drawf.dashCircle(worldX, worldY, BUFF_RADIUS * 8, PerfCore.color("#10b981"));

        int rSq = BUFF_RADIUS * BUFF_RADIUS;
        for (int dx = -BUFF_RADIUS; dx <= BUFF_RADIUS; dx++) {
            int dxSq = dx * dx;
            boolean isFrontX = DIR_X[rot] != 0 && Integer.signum(dx) == DIR_X[rot];

            for (int dy = -BUFF_RADIUS; dy <= BUFF_RADIUS; dy++) {
                if (dxSq + dy * dy > rSq) continue;

                boolean isFrontY = DIR_Y[rot] != 0 && Integer.signum(dy) == DIR_Y[rot];
                if (isFrontX || isFrontY) continue;

                Tile checkTile = Vars.world.tile(tileX + dx, tileY + dy);
                if (checkTile != null && checkTile.build != null) {
                    String bName = checkTile.build.block.name;
                    boolean isEmeralift = false;
                    for (String name : EMERALIFT_NAMES) {
                        if (bName.equals(name) || bName.endsWith("/" + name)) {
                            isEmeralift = true;
                            break;
                        }
                    }
                    if (isEmeralift) {
                        Lines.stroke(1.5f, PerfCore.color("#10b981"));
                        Lines.dashLine(worldX, worldY, checkTile.build.x, checkTile.build.y, 4);
                    }
                }
            }
        }
        Draw.reset();
    }

    public static class BreyBuild extends Building {
        Tile target1 = null;
        Tile target2 = null;
        /** Thời gian đã phá mỗi mục tiêu (timer1, timer2 của JS). */
        final float[] breakTimer = {0f, 0f};
        /** Bộ đếm sinh Raykstone mỗi mục tiêu (itemTimer1, itemTimer2). */
        final float[] itemTimer = {0f, 0f};
        /** Bộ đếm sinh Uranilum Shard mỗi mục tiêu (shardTimer1, shardTimer2). */
        final float[] shardTimer = {0f, 0f};
        final int baseMaxTiles = 5;
        boolean hasBuff = false;
        int buffCount = 0;

        float buffCheckTimer = 0f;
        float targetSearchTimer = 0f;

        @Override
        public void created() {
            super.created();
            if (emeraliftBlocks.size == 0) {
                for (String name : EMERALIFT_NAMES) {
                    Block b = Vars.content.block(name);
                    if (b != null) emeraliftBlocks.add(b);
                }
            }
            if (raykItem == null) {
                raykItem = Vars.content.item(RAYKSTONE_NAME);
            }
            if (uraniumShardItem == null) {
                uraniumShardItem = Vars.content.item("newex-uranilum-shard");
            }
        }

        boolean isEmeraliftBlock(Block block) {
            if (block == null) return false;
            for (int i = 0; i < emeraliftBlocks.size; i++) {
                if (block == emeraliftBlocks.get(i)) return true;
            }
            return false;
        }

        boolean checkEmeraldBuff() {
            int rot = rotation & 3;
            int count = 0;
            int rSq = BUFF_RADIUS * BUFF_RADIUS;

            for (int dx = -BUFF_RADIUS; dx <= BUFF_RADIUS; dx++) {
                int dxSq = dx * dx;
                boolean isFrontX = DIR_X[rot] != 0 && Integer.signum(dx) == DIR_X[rot];

                for (int dy = -BUFF_RADIUS; dy <= BUFF_RADIUS; dy++) {
                    if (dxSq + dy * dy > rSq) continue;

                    boolean isFrontY = DIR_Y[rot] != 0 && Integer.signum(dy) == DIR_Y[rot];
                    if (isFrontX || isFrontY) continue;

                    Tile neighborTile = Vars.world.tile(tile.x + dx, tile.y + dy);
                    if (neighborTile != null && neighborTile.build != null) {
                        if (isEmeraliftBlock(neighborTile.build.block)) {
                            count++;
                        }
                    }
                }
            }
            buffCount = count;
            hasBuff = count > 0;
            return hasBuff;
        }

        boolean isRaykstone(Tile t) {
            if (t == null) return false;
            Block b = t.block();
            Block f = t.floor();
            Block o = t.overlay();

            return (b != null && RAYKSTONE_NAME.equals(b.name))
                || (f != null && RAYKSTONE_NAME.equals(f.name))
                || (o != null && RAYKSTONE_NAME.equals(o.name));
        }

        boolean isCoreRaykstone(Tile t) {
            if (t == null) return false;
            Block b = t.block();
            return b != null && CORE_RAYKSTONE_NAME.equals(b.name);
        }

        boolean isVanillaWall(Tile t) {
            if (t == null) return false;
            Block b = t.block();
            return b != null && b.isStatic() && !b.synthetic()
                && !RAYKSTONE_NAME.equals(b.name) && !CORE_RAYKSTONE_NAME.equals(b.name);
        }

        boolean isMineable(Tile t) {
            return isRaykstone(t) || isCoreRaykstone(t) || isVanillaWall(t);
        }

        void findTargets() {
            if (hasBuff) {
                int baseRadius = 15;
                int areaRadius = (int) Math.floor(baseRadius * (1 + 0.20 * buffCount));
                Seq<Tile> targets = new Seq<>();

                for (int dx = -areaRadius; dx <= areaRadius; dx++) {
                    for (int dy = -areaRadius; dy <= areaRadius; dy++) {
                        Tile checkTile = Vars.world.tile(tile.x + dx, tile.y + dy);
                        if (isMineable(checkTile)) {
                            targets.add(checkTile);
                            if (targets.size >= 2) break;
                        }
                    }
                    if (targets.size >= 2) break;
                }

                target1 = targets.size > 0 ? targets.get(0) : null;
                target2 = targets.size > 1 ? targets.get(1) : null;
            } else {
                int rot = rotation & 3;
                int dirX = DIR_X[rot];
                int dirY = DIR_Y[rot];

                float pX = -DIR_Y[rot] * 4;
                float pY = DIR_X[rot] * 4;

                float startX = x + dirX * 8;
                float startY = y + dirY * 8;

                target1 = null;
                for (int i = 1; i <= baseMaxTiles; i++) {
                    float checkX = startX + pX + dirX * (i * 8 - 4);
                    float checkY = startY + pY + dirY * (i * 8 - 4);
                    Tile checkTile = Vars.world.tileWorld(checkX, checkY);

                    if (isMineable(checkTile)) {
                        target1 = checkTile;
                        break;
                    }
                }

                target2 = null;
                for (int i = 1; i <= baseMaxTiles; i++) {
                    float checkX = startX - pX + dirX * (i * 8 - 4);
                    float checkY = startY - pY + dirY * (i * 8 - 4);
                    Tile checkTile = Vars.world.tileWorld(checkX, checkY);

                    if (isMineable(checkTile)) {
                        target2 = checkTile;
                        break;
                    }
                }
            }
        }

        /**
         * Sinh vật phẩm từ mục tiêu thứ {@code idx} (0 hoặc 1).
         * Ghi chú: JS gốc có gọi Fx.itemTransfer.at(..., item, this) nhưng truyền Item vào tham số màu nên luôn lỗi
         * và bị try/catch nuốt; bản Java bỏ qua hiệu ứng đó để giữ nguyên hành vi thực tế.
         */
        void handleMining(Tile t, int idx, float progress) {
            if (t == null) return;

            boolean isRayk = isRaykstone(t);
            boolean isCore = isCoreRaykstone(t);
            boolean isVanilla = isVanillaWall(t);

            shardTimer[idx] += progress;
            if (shardTimer[idx] >= 60) {
                shardTimer[idx] %= 60;
                if (uraniumShardItem != null && Mathf.chance(0.10)) {
                    handleItem(this, uraniumShardItem);
                }
            }

            itemTimer[idx] += progress;
            if (itemTimer[idx] >= 60) {
                itemTimer[idx] %= 60;

                if (raykItem != null) {
                    if (isRayk || isCore) {
                        handleItem(this, raykItem);
                    } else if (isVanilla) {
                        double dropChance = hasBuff ? 0.60 : 0.40;
                        if (Mathf.chance(dropChance)) {
                            handleItem(this, raykItem);
                        }
                    }
                }
            }
        }

        /** Trả về lượng tiến độ cộng thêm, hoặc NaN nếu ô đã bị phá (tương đương null trong JS). */
        float processTarget(Tile t, int idx, float progress) {
            if (t == null) return 0f;

            if (Mathf.chance(0.25)) {
                Effect mine = fxByName("mine");
                if (mine != null) mine.at(t.worldx(), t.worldy());
            }

            if (isCoreRaykstone(t)) {
                return progress;
            }

            if (breakTimer[idx] + progress >= BREAK_TIME) {
                float tx = t.worldx();
                float ty = t.worldy();

                NexNet.setBlock(t, Blocks.air, Team.derelict, 0);

                try {
                    Effect smallExplosion = fxByName("smallExplosion");
                    if (smallExplosion != null) {
                        smallExplosion.at(tx, ty);
                        Effect.scorch(tx, ty, 2);
                        Damage.damage(tx, ty, 15f, 0f);
                    }
                } catch (Throwable ignored) {
                }

                breakTimer[idx] = 0f;
                return Float.NaN;
            }
            return progress;
        }

        @Override
        public void updateTile() {
            if (efficiency <= 0 || !shouldConsume()) return;

            buffCheckTimer += Time.delta;
            if (buffCheckTimer >= 30) {
                checkEmeraldBuff();
                buffCheckTimer = 0f;
            }

            float liquidBoost = (liquids != null && liquids.currentAmount() > 0) ? 0.5f : 0f;
            float progress = delta() * efficiency * (1f + liquidBoost);

            if (!isMineable(target1) || !isMineable(target2)) {
                targetSearchTimer += Time.delta;
                if (targetSearchTimer >= 20) {
                    findTargets();
                    targetSearchTimer = 0f;
                }
            }

            float vanillaSpeedMult = hasBuff ? (float) (1.75 * (1 + 0.50 * buffCount)) : 1.75f;

            if (target1 != null) {
                handleMining(target1, 0, progress);

                float targetProgress1 = isVanillaWall(target1) ? progress * vanillaSpeedMult : progress;

                float added = processTarget(target1, 0, targetProgress1);
                if (Float.isNaN(added)) {
                    target1 = null;
                    itemTimer[0] = 0f;
                    shardTimer[0] = 0f;
                } else {
                    breakTimer[0] += added;
                }
            } else {
                breakTimer[0] = 0f;
                itemTimer[0] = 0f;
                shardTimer[0] = 0f;
            }

            if (target2 != null) {
                handleMining(target2, 1, progress);

                float targetProgress2 = isVanillaWall(target2) ? progress * vanillaSpeedMult : progress;

                float added = processTarget(target2, 1, targetProgress2);
                if (Float.isNaN(added)) {
                    target2 = null;
                    itemTimer[1] = 0f;
                    shardTimer[1] = 0f;
                } else {
                    breakTimer[1] += added;
                }
            } else {
                breakTimer[1] = 0f;
                itemTimer[1] = 0f;
                shardTimer[1] = 0f;
            }

            dumpAccumulate();
        }

        @Override
        public void drawSelect() {
            int rot = rotation & 3;
            Color lineColor = hasBuff ? PerfCore.color("#10b981") : Pal.accent;

            if (hasBuff) {
                int totalTiles = (int) Math.floor(31 * (1 + 0.20 * buffCount));
                float size = totalTiles * 8;
                Drawf.dashSquare(lineColor, x, y, size);
            } else {
                int dirX = DIR_X[rot];
                int dirY = DIR_Y[rot];
                float startX = x + dirX * 8;
                float startY = y + dirY * 8;
                float pX = -DIR_Y[rot] * 4;
                float pY = DIR_X[rot] * 4;
                float range = baseMaxTiles * 8;

                Drawf.dashLine(lineColor, startX + pX, startY + pY, startX + pX + dirX * range, startY + pY + dirY * range);
                Drawf.dashLine(lineColor, startX - pX, startY - pY, startX - pX + dirX * range, startY - pY + dirY * range);
            }

            Draw.z(Layer.turret + 2f);
            Drawf.dashCircle(x, y, BUFF_RADIUS * 8, PerfCore.color("#10b981"));

            int rSq = BUFF_RADIUS * BUFF_RADIUS;
            for (int dx = -BUFF_RADIUS; dx <= BUFF_RADIUS; dx++) {
                int dxSq = dx * dx;
                boolean isFrontX = DIR_X[rot] != 0 && Integer.signum(dx) == DIR_X[rot];

                for (int dy = -BUFF_RADIUS; dy <= BUFF_RADIUS; dy++) {
                    if (dxSq + dy * dy > rSq) continue;

                    boolean isFrontY = DIR_Y[rot] != 0 && Integer.signum(dy) == DIR_Y[rot];
                    if (isFrontX || isFrontY) continue;

                    Tile checkTile = Vars.world.tile(tile.x + dx, tile.y + dy);
                    if (checkTile != null && checkTile.build != null) {
                        if (isEmeraliftBlock(checkTile.build.block)) {
                            Lines.stroke(1.5f, PerfCore.color("#10b981"));
                            Lines.dashLine(x, y, checkTile.build.x, checkTile.build.y, 4);
                        }
                    }
                }
            }
            Draw.reset();
        }

        @Override
        public void draw() {
            Draw.z(Layer.turret);
            super.draw();

            if (items != null) {
                int totalItems = items.total();
                if (totalItems > 0) {
                    TextureRegion storageRegion = PerfCore.region("newex-storage", "storage");
                    if (storageRegion != null && storageRegion.found()) {
                        int tier = totalItems / 10;
                        float calculatedSize = 4f + (tier * 1.2f);
                        float size = Math.min(calculatedSize, 16f);

                        Draw.z(Layer.turret + 0.1f);
                        Draw.rect(storageRegion, x, y, size, size);
                    }
                }
            }

            if (efficiency <= 0 || !shouldConsume()) return;

            int rot = rotation & 3;
            int dirX = DIR_X[rot];
            int dirY = DIR_Y[rot];

            float startX = x + dirX * 8;
            float startY = y + dirY * 8;

            float pX = -DIR_Y[rot] * 4;
            float pY = DIR_X[rot] * 4;

            Color laserColor = hasBuff ? PerfCore.color("#10b981") : PerfCore.color("#ffd37f");
            float pulse = Mathf.absin(Time.time, 4f, 0.2f);

            Draw.z(Layer.turret + 1f);

            if (target1 != null) {
                float endLineX = target1.worldx();
                float endLineY = target1.worldy();
                float startLineX = hasBuff ? x : startX + pX;
                float startLineY = hasBuff ? y : startY + pY;

                Lines.stroke(2.2f, laserColor);
                Lines.line(startLineX, startLineY, endLineX, endLineY);
                Lines.stroke(0.8f, Color.white);
                Lines.line(startLineX, startLineY, endLineX, endLineY);
                Fill.circle(endLineX, endLineY, 2f + pulse);
            }

            if (target2 != null) {
                float endLineX = target2.worldx();
                float endLineY = target2.worldy();
                float startLineX = hasBuff ? x : startX - pX;
                float startLineY = hasBuff ? y : startY - pY;

                Lines.stroke(2.2f, laserColor);
                Lines.line(startLineX, startLineY, endLineX, endLineY);
                Lines.stroke(0.8f, Color.white);
                Lines.line(startLineX, startLineY, endLineX, endLineY);
                Fill.circle(endLineX, endLineY, 2f + pulse);
            }
            Draw.reset();
        }
    }
}
