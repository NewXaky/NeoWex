package newex.content;

import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * Chuyển từ fix-save.js. Khi tải save, game chỉ tạo lại unit bằng lớp gốc (mất lớp tùy chỉnh): sau 15 tick,
 * thay các unit leolyr / elorix / suv-27 / reykor chưa phải {@link LeveledUnit} bằng unit mới (giữ vị trí, máu,
 * cấp độ lưu trong stack Spore Pod, và quyền điều khiển của người chơi).
 */
public final class FixSave {
    private static final String[] TARGET_UNIT_NAMES = {
        "newex-suv-27",
        "newex-reykor"
    };

    private FixSave() {}

    private static final class Replace {
        UnitType type;
        float x, y, rotation, health;
        Team team;
        int level;
        boolean isPlayer;
        Unit rawUnit;
    }

    public static void init() {
        Events.on(WorldLoadEvent.class, e -> Time.run(15f, FixSave::replaceUnits));
    }

    private static void replaceUnits() {
        if (Vars.state.isMenu() || Groups.unit == null) return;

        final Seq<UnitType> validTypes = new Seq<>();
        for (String name : TARGET_UNIT_NAMES) {
            UnitType type = Vars.content.getByName(ContentType.unit, name);
            if (type != null) {
                validTypes.add(type);
            }
        }

        if (validTypes.size == 0) return;

        final Seq<Replace> targetsToReplace = new Seq<>();

        Groups.unit.each(u -> {
            if (u != null && validTypes.contains(u.type, true)) {
                if (!(u instanceof LeveledUnit)) {
                    int savedLevel = 0;

                    try {
                        if (u.stack != null && u.stack.item != null) {
                            if ("spore-pod".equals(u.stack.item.name) || u.stack.item == Items.sporePod) {
                                savedLevel = u.stack.amount;
                            }
                        }
                    } catch (Throwable err) {
                        savedLevel = 0;
                    }

                    Replace r = new Replace();
                    r.type = u.type;
                    r.x = u.x;
                    r.y = u.y;
                    r.rotation = u.rotation;
                    r.team = u.team;
                    r.health = u.health;
                    r.level = savedLevel;
                    r.isPlayer = Vars.player != null && Vars.player.unit() == u;
                    r.rawUnit = u;
                    targetsToReplace.add(r);
                }
            }
        });

        for (int i = 0; i < targetsToReplace.size; i++) {
            Replace t = targetsToReplace.get(i);
            try {
                t.rawUnit.remove();

                Unit newUnit = t.type.spawn(t.team, t.x, t.y);
                if (newUnit != null) {
                    newUnit.rotation = t.rotation;
                    newUnit.health = Math.min(t.health, newUnit.maxHealth);

                    if (t.level > 0) {
                        int level = Math.min(10, t.level);
                        if (newUnit instanceof LeveledUnit) {
                            ((LeveledUnit) newUnit).setLevel(level);
                        }
                        if (newUnit.stack != null) {
                            newUnit.stack.item = Items.sporePod;
                            newUnit.stack.amount = level;
                        }
                    }

                    if (t.isPlayer) {
                        Vars.player.unit(newUnit);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
