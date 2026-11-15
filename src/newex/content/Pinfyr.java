package newex.content;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.world.Block;

/**
 * Chuyển từ pinfyr.js. Khối lưu trữ điện: mỗi tick chuyển điện sang 4 công trình kề bên (tối đa 800/60 mỗi tick mỗi bên).
 */
public final class Pinfyr {
    private static Block pinfyr = null;

    private Pinfyr() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            pinfyr = Vars.content.block("newex-pinfyr");
            if (pinfyr != null) {
                pinfyr.update = true;
                pinfyr.buildType = PinfyrBuild::new;
            }
        });
    }

    public static class PinfyrBuild extends Building {
        @Override
        public void updateTile() {
            super.updateTile();

            if (power != null && power.status > 0 && block.consPower != null) {
                float capacity = block.consPower.capacity;
                float currentStored = power.status * capacity;

                for (int i = 0; i < 4; i++) {
                    if (currentStored <= 0) break;

                    Building target = nearby(i);
                    if (target != null && target.team == team && target.power != null && target.block.consPower != null) {

                        if (target.block == pinfyr) continue;

                        float targetCap = target.block.consPower.capacity;
                        if (targetCap > 0) {
                            float targetCurrent = target.power.status * targetCap;
                            float needed = targetCap - targetCurrent;

                            if (needed > 0) {
                                float transfer = Math.min(currentStored, Math.min(needed, 800f / 60f));
                                currentStored -= transfer;
                                target.power.status = Math.min(1f, (targetCurrent + transfer) / targetCap);
                            }
                        }
                    }
                }

                power.status = currentStored / capacity;
            }
        }
    }
}
