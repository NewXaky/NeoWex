package newex.content;

import arc.Events;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;

/**
 * Chuyển từ obs-ore.js. Xây công trình lên quặng "obs" sẽ biến quặng đó thành "obsidis",
 * công trình vừa xây bị phá hủy.
 */
public final class ObsOre {
    private static Block obsOre = null;
    private static Block obsidisOre = null;

    private ObsOre() {}

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> {
            obsOre = Vars.content.block("newex-obs-ore");
            obsidisOre = Vars.content.block("newex-ore-obsidis");
        });

        Events.on(BlockBuildBeginEvent.class, ObsOre::onBuildBegin);
    }

    private static void onBuildBegin(BlockBuildBeginEvent event) {
        if (obsOre == null) obsOre = Vars.content.block("newex-obs-ore");
        if (obsidisOre == null) obsidisOre = Vars.content.block("newex-ore-obsidis");

        if (obsOre == null || obsidisOre == null) return;

        final Tile tile = event.tile;
        if (tile == null || event.breaking) return;

        Building build = tile.build;
        Block block = build != null ? build.block : tile.block();

        int size = (block != null && block.size > 0) ? block.size : 1;

        int startX = tile.x - (size - 1) / 2;
        int startY = tile.y - (size - 1) / 2;

        boolean hasObsUnderneath = false;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Tile currentTile = Vars.world.tile(startX + x, startY + y);

                if (currentTile != null) {
                    boolean isObs = (currentTile.overlay() == obsOre) || (currentTile.floor() == obsOre);

                    if (isObs) {
                        hasObsUnderneath = true;

                        if (currentTile.overlay() == obsOre) {
                            currentTile.setOverlayNet(obsidisOre);
                        } else {
                            currentTile.setFloorNet(obsidisOre);
                        }

                        try {
                            Fx.explosion.at(currentTile.worldx(), currentTile.worldy());
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }

        if (hasObsUnderneath) {
            Time.run(1f, () -> {
                if (tile.build != null) {
                    tile.build.kill();
                } else {
                    tile.setNet(Blocks.air);
                }
            });
        }
    }
}
