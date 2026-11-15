package newex;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.UnitSpawnEvent;
import mindustry.game.Team;
import mindustry.gen.Unit;

import java.util.HashSet;
import java.util.Set;

/**
 * Chuyển từ buff-enemies.js.
 * Quái địch khi xuất hiện được tăng máu theo số đợt: +X% mỗi đợt (cài đặt "newex-hp-per-wave-percent", mặc định 10).
 */
public final class BuffEnemies {
    private static final Set<Integer> buffedUnitIds = new HashSet<>();

    private BuffEnemies() {}

    public static void init() {
        Events.on(UnitSpawnEvent.class, e -> applyHealthBuff(e.unit));
        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit != null) {
                buffedUnitIds.remove(e.unit.id);
            }
        });
    }

    private static double getWaveHealthMultiplier() {
        int hpPercentPerWave = Core.settings.getInt("newex-hp-per-wave-percent", 10);

        if (hpPercentPerWave <= 0) return 1.0;
        if (hpPercentPerWave > 999) hpPercentPerWave = 999;

        int currentWave = (Vars.state != null && Vars.state.wave > 0) ? Vars.state.wave : 1;
        double extraMultiplier = (currentWave * (double) hpPercentPerWave) / 100.0;
        return 1.0 + extraMultiplier;
    }

    private static void applyHealthBuff(Unit unit) {
        if (unit == null) return;
        // JS gốc dùng Vars.player.team() trực tiếp; thêm kiểm tra null để server không bị lỗi.
        Team playerTeam = Vars.player != null ? Vars.player.team() : Vars.state.rules.defaultTeam;
        if (unit.team == playerTeam) return;

        int id = unit.id;

        if (!buffedUnitIds.contains(id)) {
            buffedUnitIds.add(id);

            double multiplier = getWaveHealthMultiplier();
            if (multiplier > 1.0) {
                unit.maxHealth = (float) (unit.maxHealth * multiplier);
                unit.health = unit.maxHealth;
            }
        }
    }
}
