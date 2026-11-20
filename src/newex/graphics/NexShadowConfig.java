package newex.graphics;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectMap;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.Junction;
import mindustry.world.blocks.distribution.OverflowGate;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.distribution.Sorter;
import mindustry.world.blocks.distribution.StackConveyor;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.environment.TallBlock;
import mindustry.world.blocks.environment.TreeBlock;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.logic.MemoryBlock;
import mindustry.world.blocks.payloads.PayloadConveyor;
import mindustry.world.blocks.power.LightBlock;
import mindustry.world.blocks.power.PowerNode;

/**
 * Cấu hình phân tầng Z-layer, phân loại block/prop và vẽ hình khối đổ bóng cho NexDynamicShadows.
 */
final class NexShadowConfig {
    static final int NUM_TIERS = 5;
    static final int TIER_SMALL = 0;
    static final int TIER_MED = 1;
    static final int TIER_LARGE = 2;
    static final int TIER_XL = 3;
    static final int TIER_ENV = 4;

    static final float Z_GROUND = 29.0f;
    static final float Z_TIER1 = 29.2f;
    static final float Z_TIER2 = 32.5f;
    static final float Z_CRYSTAL = 33.0f;
    static final float Z_TIER3 = 33.5f;
    static final float Z_MOUNTAIN = 34.5f;
    static final float Z_TREE = 71.0f;

    enum PropType { TREE, ORB, SPIKE, GENERIC }

    private static final ObjectIntMap<String> tierOverrides = new ObjectIntMap<>();
    private static final ObjectMap<Block, Boolean> mountainWallCache = new ObjectMap<>(128);
    private static final ObjectFloatMap<Block> modCache = new ObjectFloatMap<>(128);
    private static final ObjectMap<UnitType, TextureRegion> unitShadowCache = new ObjectMap<>(64);
    private static final ObjectMap<Block, PropType> propTypeCache = new ObjectMap<>(64);

    static {
        tierOverrides.put("mass-driver", TIER_LARGE);
        tierOverrides.put("payload-mass-driver", TIER_LARGE);
        tierOverrides.put("large-payload-mass-driver", TIER_XL);
        tierOverrides.put("impetus", TIER_LARGE);
        tierOverrides.put("logic-display", TIER_SMALL);
        tierOverrides.put("large-logic-display", TIER_SMALL);
        tierOverrides.put("memory-cell", TIER_SMALL);
        tierOverrides.put("memory-bank", TIER_SMALL);
        tierOverrides.put("core-shard", TIER_LARGE);
        tierOverrides.put("core-foundation", TIER_XL);
        tierOverrides.put("core-nucleus", TIER_XL);
        tierOverrides.put("core-bastion", TIER_LARGE);
        tierOverrides.put("core-citadel", TIER_XL);
        tierOverrides.put("core-acropolis", TIER_XL);
        tierOverrides.put("container", TIER_MED);
        tierOverrides.put("vault", TIER_LARGE);
        tierOverrides.put("unloader", TIER_SMALL);
        tierOverrides.put("liquid-container", TIER_MED);
        tierOverrides.put("liquid-tank", TIER_LARGE);
        tierOverrides.put("reinforced-container", TIER_MED);
        tierOverrides.put("reinforced-vault", TIER_LARGE);
        tierOverrides.put("reinforced-liquid-container", TIER_MED);
        tierOverrides.put("reinforced-liquid-tank", TIER_LARGE);
        tierOverrides.put("reinforced-pump", TIER_MED);
        tierOverrides.put("overdrive-projector", TIER_MED);
        tierOverrides.put("overdrive-dome", TIER_LARGE);
        tierOverrides.put("force-projector", TIER_LARGE);
        tierOverrides.put("large-force-projector", TIER_XL);
        tierOverrides.put("mender", TIER_SMALL);
        tierOverrides.put("mend-projector", TIER_MED);
        tierOverrides.put("repair-tower", TIER_MED);
        tierOverrides.put("repair-turret", TIER_MED);
        tierOverrides.put("launch-pad", TIER_LARGE);
        tierOverrides.put("landing-pad", TIER_LARGE);
        tierOverrides.put("interplanetary-accelerator", TIER_XL);
        tierOverrides.put("additive-reconstructor", TIER_LARGE);
        tierOverrides.put("multiplicative-reconstructor", TIER_XL);
        tierOverrides.put("exponential-reconstructor", TIER_XL);
        tierOverrides.put("tetrative-reconstructor", TIER_XL);
    }

    private NexShadowConfig() {}

    static float getZ(int tier) {
        switch (tier) {
            case 0: return Z_GROUND;
            case 1: return Z_TIER1;
            case 2: return Z_TIER2;
            case 3: return Z_TIER3;
            case 4: return Z_MOUNTAIN;
            default: return Z_GROUND;
        }
    }

    static int unitTier(float elevation) {
        return Mathf.clamp((int) (elevation * NUM_TIERS), 0, NUM_TIERS - 1);
    }

    static int getTier(Block b) {
        if (b == null) return TIER_SMALL;
        if (isBridge(b) || b instanceof PowerNode || isDistribution(b) || isLogicOrMemory(b)) return TIER_SMALL;
        String key = b.name != null ? b.name.toLowerCase() : "";
        if (tierOverrides.containsKey(key)) return Mathf.clamp(tierOverrides.get(key, TIER_SMALL), 0, NUM_TIERS - 1);
        if (isMountainOrWall(b) || isPine(b)) return TIER_ENV;
        if (b instanceof Prop || b instanceof TreeBlock || b instanceof TallBlock || isCrystal(b) || isTree(b)) {
            return isTree(b) ? TIER_MED : TIER_SMALL;
        }
        if (b.size <= 1) return TIER_SMALL;
        if (b.size == 2) return TIER_MED;
        if (b.size == 3) return TIER_LARGE;
        return TIER_XL;
    }

    static boolean isPine(Block blk) {
        if (blk == null || blk instanceof TreeBlock) return false;
        String n = blk.name != null ? blk.name.toLowerCase() : "";
        return n.contains("pine") && !n.contains("spore");
    }

    static boolean isMountainOrWall(Block b) {
        if (b == null) return false;
        Boolean cached = mountainWallCache.get(b);
        if (cached != null) return cached;
        boolean res = false;
        if (b.isStatic() && b.solid) {
            if (b instanceof StaticWall) res = true;
            else if (!(b instanceof TreeBlock || isTree(b) || b instanceof TallBlock || isCrystal(b) || b instanceof Prop)) {
                String n = b.name != null ? b.name.toLowerCase() : "";
                res = n.contains("wall") || n.contains("mountain") || n.contains("cliff");
            }
        }
        mountainWallCache.put(b, res);
        return res;
    }

    static boolean isCrystal(Block b) {
        if (b == null || b instanceof StaticWall || b instanceof Floor) return false;
        if (b instanceof TallBlock) return true;
        String n = b.name != null ? b.name.toLowerCase() : "";
        return n.contains("crystal") || n.contains("spike") || n.contains("chunk") || n.contains("orb");
    }

    static boolean isTree(Block b) {
        if (b == null) return false;
        if (b instanceof TreeBlock) return true;
        String n = b.name != null ? b.name.toLowerCase() : "";
        if (n.contains("wall") || n.contains("floor") || n.contains("press") || n.contains("moss")) return false;
        return n.contains("tree") || n.contains("deathtree") || (n.contains("spore") && (n.contains("pine") || n.contains("tree") || n.contains("wood")));
    }

    static boolean isDistribution(Block b) {
        return b instanceof Conveyor || b instanceof Router || b instanceof Sorter || b instanceof Junction
            || b instanceof StackConveyor || b instanceof OverflowGate || b instanceof ItemBridge || b instanceof PayloadConveyor;
    }

    static boolean isBridge(Block b) {
        if (b == null) return false;
        if (b instanceof ItemBridge) return true;
        String n = b.name != null ? b.name.toLowerCase() : "";
        return n.contains("bridge");
    }

    static boolean isLogicOrMemory(Block b) {
        return b instanceof LogicDisplay || b instanceof MemoryBlock;
    }

    static boolean isMine(Block b) {
        if (b == null) return false;
        if (b instanceof mindustry.world.blocks.defense.ShockMine) return true;
        String n = b.name != null ? b.name.toLowerCase() : "";
        return n.equals("shock-mine") || n.endsWith("-mine");
    }

    static float getModifier(Block blk) {
        if (modCache.containsKey(blk)) return modCache.get(blk, 1f);
        float mod;
        if (blk instanceof PowerNode || blk instanceof LightBlock || isMine(blk)) mod = 0.0f;
        else if (isPine(blk)) mod = 0.15f;
        else if (isLogicOrMemory(blk)) mod = 0.04f;
        else if (blk.isStatic() && blk.solid) mod = 0.20f;
        else {
            switch (blk.size) {
                case 1: mod = 0.04f; break;
                case 2: mod = 0.08f; break;
                case 3: mod = 0.06f; break;
                case 4: mod = 0.10f; break;
                case 5: mod = 0.135f; break;
                case 6: mod = 0.16f; break;
                case 7: mod = 0.18f; break;
                case 8: mod = 0.20f; break;
                case 9: mod = 0.22f; break;
                default: mod = 0.24f; break;
            }
        }
        modCache.put(blk, mod);
        return mod;
    }

    static TextureRegion getOriginalShadow(UnitType type) {
        if (type == null) return null;
        TextureRegion cached = unitShadowCache.get(type);
        if (cached != null) return cached;
        TextureRegion clearReg = Core.atlas.find("clear");
        TextureRegion res = null;
        if (type.shadowRegion != null && type.shadowRegion.found() && type.shadowRegion != clearReg) res = type.shadowRegion;
        else if (type.fullIcon != null && type.fullIcon.found()) res = type.fullIcon;
        else if (type.region != null && type.region.found()) res = type.region;
        if (res != null) unitShadowCache.put(type, res);
        return res;
    }

    static PropType getPropType(Block block, TextureRegion region) {
        PropType cached = propTypeCache.get(block);
        if (cached != null) return cached;
        PropType t;
        String name = block.name != null ? block.name.toLowerCase() : "";
        if (block instanceof TreeBlock || isTree(block)) t = PropType.TREE;
        else if (name.contains("orb") || name.contains("sphere") || name.contains("ball")) t = PropType.ORB;
        else if (name.contains("spike") || name.contains("thorn") || name.contains("needle") || name.contains("cluster")) t = PropType.SPIKE;
        else if (region != null && region.found() && (region.height / (float) Math.max(region.width, 1)) > 1.4f) t = PropType.TREE;
        else t = PropType.GENERIC;
        propTypeCache.put(block, t);
        return t;
    }

    static void drawExtruded(float cx, float cy, float len, float cosA, float sinA, TextureRegion region, float rawSize) {
        float rhs = rawSize * 0.5f;
        float sdx = cosA * len, sdy = sinA * len;
        float ax = cx - rhs, ay = cy + rhs;
        float bx = cx + rhs, by = cy + rhs;
        float cx2 = cx + rhs, cy2 = cy - rhs;
        float dx = cx - rhs, dy = cy - rhs;
        float axP = ax + sdx, ayP = ay + sdy;
        float bxP = bx + sdx, byP = by + sdy;
        float cxP = cx2 + sdx, cyP = cy2 + sdy;
        float dxP = dx + sdx, dyP = dy + sdy;

        Fill.quad(ax, ay, bx, by, cx2, cy2, dx, dy);
        if (sdy > 0f) Fill.quad(ax, ay, bx, by, bxP, byP, axP, ayP);
        if (sdy < 0f) Fill.quad(cx2, cy2, dx, dy, dxP, dyP, cxP, cyP);
        if (sdx > 0f) Fill.quad(bx, by, cx2, cy2, cxP, cyP, bxP, byP);
        if (sdx < 0f) Fill.quad(dx, dy, ax, ay, axP, ayP, dxP, dyP);

        if (region != null && region.found()) {
            Draw.rect(region, cx + sdx, cy + sdy, rawSize, rawSize);
        } else {
            Fill.quad(axP, ayP, bxP, byP, cxP, cyP, dxP, dyP);
        }
    }

    static void drawPropShadow(float cx, float cy, TextureRegion region, PropType type,
                               float propLen, float cosA, float sinA, float angle,
                               float contactAlpha, float alphaMult) {
        float propW = region.width * Draw.scl;
        if (contactAlpha > 0.005f) {
            Draw.color(0.02f, 0.015f, 0.04f, contactAlpha * alphaMult);
            Fill.circle(cx, cy, propW * 0.40f);
        }
        Draw.color(0.04f, 0.03f, 0.08f, alphaMult);
        switch (type) {
            case ORB:
                Fill.circle(cx + cosA * propLen * 0.35f, cy + sinA * propLen * 0.35f, propW * 0.30f);
                break;
            case SPIKE:
                Draw.rect(region, cx + cosA * propLen * 0.49f, cy + sinA * propLen * 0.49f, propW * 0.40f, propLen, angle - 90f);
                break;
            case TREE:
                Draw.rect(region, cx + cosA * propLen * 0.49f, cy + sinA * propLen * 0.49f, propW * 0.85f, propLen, angle - 90f);
                break;
            default: {
                float actualLen = propLen * 0.65f;
                Draw.rect(region, cx + cosA * actualLen * 0.49f, cy + sinA * actualLen * 0.49f, propW * 0.85f, actualLen, angle - 90f);
                break;
            }
        }
    }
}
