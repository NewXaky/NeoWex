package newex;

import arc.Events;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import arc.struct.Seq;

/** Chuyển từ free-mode.js (global.NexFree). Chế độ tài nguyên vô hạn / editor: luôn đổ đầy lõi. */
public final class NexFree {
    private static final int TARGET_AMOUNT = 100000;
    private static final float CHECK_INTERVAL = 5f;
    private static float lastCheck = 0f;

    private NexFree() {}

    public static void init() {
        Events.run(Trigger.update, NexFree::tick);
    }

    public static boolean active() {
        Rules rules = Vars.state != null ? Vars.state.rules : null;
        return rules != null && (rules.infiniteResources || rules.editor);
    }

    private static void fillCore(CoreBuild core) {
        if (core == null || core.items == null) return;
        int target = Math.max(TARGET_AMOUNT, core.storageCapacity);
        Seq<Item> items = Vars.content.items();
        for (int i = 0; i < items.size; i++) {
            Item item = items.get(i);
            if (item == null || item.hidden) continue;
            if (core.items.get(item) < target) core.items.set(item, target);
        }
    }

    public static void fill(Team team) {
        if (team == null) return;
        Seq<CoreBuild> cores = team.cores();
        if (cores == null) return;
        for (int i = 0; i < cores.size; i++) fillCore(cores.get(i));
    }

    private static void tick() {
        if (Vars.net.client() || !Vars.state.isGame()) return;
        if (Time.time - lastCheck < CHECK_INTERVAL && Time.time >= lastCheck) return;
        lastCheck = Time.time;
        if (!active()) return;

        fill(Vars.state.rules.defaultTeam);
        if (Vars.player != null && Vars.player.team() != Vars.state.rules.defaultTeam) {
            fill(Vars.player.team());
        }
    }
}
