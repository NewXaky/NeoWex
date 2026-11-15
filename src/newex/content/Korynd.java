package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import newex.PerfCore;

/**
 * Chuyển từ korynd.js. Máy khoan tự động: ngẫu nhiên sinh Than; đủ 2 Than thì chế Than chì.
 * Đứng cạnh "crynex" sẽ được cộng tốc độ chế tạo; có Cryofluid thì nhanh hơn nhiều.
 */
public final class Korynd {
    private static final Effect koryndMineFx = new Effect(25f, e -> {
        Draw.color(PerfCore.color("3a3a3a"), Color.gray, e.fin());
        Rand rand = new Rand(e.id);
        for (int i = 0; i < 6; i++) {
            float v = rand.range(360f);
            float len = rand.random(12f) * e.finpow();
            float x = e.x + Angles.trnsx(v, len);
            float y = e.y + Angles.trnsy(v, len);
            Fill.square(x, y, 1.5f * e.fout(), 45f);
        }
    });

    private static final Effect koryndCraftFx = new Effect(30f, e -> {
        Draw.color(PerfCore.color("5b637a"), PerfCore.color("98a2b3"), e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, 8f * e.finpow());
        Fill.circle(e.x, e.y, 3f * e.fout());
    });

    private Korynd() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = Vars.content.getByName(ContentType.block, "newex-korynd");
            if (b instanceof GenericCrafter) {
                final GenericCrafter block = (GenericCrafter) b;
                block.craftEffect = koryndCraftFx;
                block.buildType = () -> new KoryndBuild(block);
            }
        });
    }

    /** GenericCrafterBuild là lớp lồng (inner class) nên phải kế thừa qua {@code outer.super()}. */
    public static class KoryndBuild extends GenericCrafter.GenericCrafterBuild {
        float mineTimer = 0f;
        float nextMineDelay = 60f;
        float proxTimer = 0f;
        boolean crynexChecked = false;
        boolean hasCrynexCached = false;

        public KoryndBuild(GenericCrafter outer) {
            outer.super();
        }

        @Override
        public void created() {
            super.created();
            nextMineDelay = Mathf.random(6f, 120f);
        }

        @Override
        public boolean acceptItem(Building source, Item item) {
            if (item == Items.coal) {
                return items.get(Items.coal) < block.itemCapacity;
            }
            return super.acceptItem(source, item);
        }

        @Override
        public void updateTile() {
            proxTimer += Time.delta;
            if (proxTimer >= 30f || !crynexChecked) {
                proxTimer = 0f;
                crynexChecked = true;
                hasCrynexCached = false;
                proximity.each(other -> {
                    if (other.block != null && "newex-crynex".equals(other.block.name)) {
                        hasCrynexCached = true;
                    }
                });
            }
            boolean hasCrynex = hasCrynexCached;

            boolean hasCryo = liquids != null && liquids.get(Liquids.cryofluid) > 0;

            float speedMultiplier = hasCryo ? 4f : 1f;

            if (enabled) {
                mineTimer += Time.delta * speedMultiplier;
                if (mineTimer >= nextMineDelay) {
                    mineTimer = 0f;
                    nextMineDelay = Mathf.random(6f, 120f);

                    if (items.get(Items.coal) < block.itemCapacity) {
                        items.add(Items.coal, 1);
                        koryndMineFx.at(x, y);
                    }
                }
            }

            if (hasCrynex && shouldConsume()) {
                float bonusBoost = hasCryo ? 3f : 0f;
                progress += delta() * (1f + bonusBoost);
            }

            super.updateTile();
        }

        @Override
        public boolean shouldConsume() {
            return enabled && items.get(Items.coal) >= 2;
        }

        @Override
        public void craft() {
            if (items.get(Items.coal) >= 2) {
                items.remove(Items.coal, 2);
                items.add(Items.graphite, 1);
                koryndCraftFx.at(x, y);
            }
        }
    }
}
