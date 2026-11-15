package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.Rand;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;
import newex.PerfCore;

/**
 * Chuyển từ copesblock.js. Tường hỗ trợ: tăng tốc máy khoan đồng minh trong vuông 96px (applyBoost 1.5x / 35 tick),
 * mỗi đội chỉ được xây tối đa số khối bằng số lõi đang có; mất lõi thì khối thừa tự hủy.
 */
public final class CopesBlock {
    private static final float RANGE_SIZE = 96f;

    private static final Effect copesBuffCircleEffect = new Effect(35f, e -> {
        Rand rand = new Rand(e.id);
        float zoomSpeed = rand.random(6f, 14f);
        float zoomAmplitude = rand.random(1.5f, 3.5f);
        float currentRadius = 5f + Mathf.absin(Time.time, zoomSpeed, zoomAmplitude);

        Lines.stroke(1.5f * e.fout());
        Draw.color(Color.purple);
        Lines.circle(e.x, e.y, currentRadius);
        Draw.reset();
    });

    private CopesBlock() {}

    private static Block findBlock() {
        Block b = Vars.content.block("newex-copesblock");
        return b != null ? b : Vars.content.block("copesblock");
    }

    private static void updateMenuVisibility() {
        Block wallBuff = findBlock();
        if (Vars.player == null || wallBuff == null) return;

        Team playerTeam = Vars.player.team();
        int maxAllowed = playerTeam.cores().size;

        int currentCount = PerfCore.teamCount(playerTeam, wallBuff);

        if (currentCount < maxAllowed) {
            wallBuff.buildVisibility = BuildVisibility.shown;
        } else {
            wallBuff.buildVisibility = BuildVisibility.hidden;
        }
    }

    public static void init() {
        Events.on(ContentInitEvent.class, e -> {
            Block b = findBlock();
            if (b instanceof Wall) {
                final Wall wallBuff = (Wall) b;
                wallBuff.update = true;
                wallBuff.buildType = () -> new CopesBuild(wallBuff);
            }
        });

        Events.on(WorldLoadEvent.class, event -> Time.run(10f, CopesBlock::updateMenuVisibility));

        Events.on(BlockBuildEndEvent.class, event -> updateMenuVisibility());

        Events.on(BlockDestroyEvent.class, CopesBlock::onBlockDestroy);

        Events.run(Trigger.draw, () -> {
            Block build = Vars.control.input.block;
            if (build != null && ("newex-copesblock".equals(build.name) || "copesblock".equals(build.name))) {
                Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());

                if (tile != null) {
                    float centerX = tile.drawx() + build.offset;
                    float centerY = tile.drawy() + build.offset;

                    Drawf.dashSquare(Pal.accent, centerX, centerY, RANGE_SIZE * 2f);
                }
            }
        });
    }

    private static void onBlockDestroy(BlockDestroyEvent event) {
        final Block wallBuff = findBlock();
        if (wallBuff == null) return;

        Tile destroyedTile = event.tile;
        if (destroyedTile == null || destroyedTile.build == null) return;

        Building destroyedBuild = destroyedTile.build;
        final Team victimTeam = destroyedBuild.team;

        if (destroyedBuild.block instanceof CoreBlock) {
            int maxAllowed = victimTeam.data().cores.size - 1;
            if (maxAllowed < 0) maxAllowed = 0;

            final Seq<Building> teamBlocks = new Seq<>();
            Groups.build.each(b -> {
                if (b.block == wallBuff && b.team == victimTeam) {
                    teamBlocks.add(b);
                }
            });

            if (teamBlocks.size > maxAllowed) {
                int toDestroy = teamBlocks.size - maxAllowed;
                for (int i = 0; i < toDestroy; i++) {
                    Building lastBlock = teamBlocks.pop();
                    Call.sendMessage("[red]Đội " + victimTeam.name + " bị mất Lõi! Khối copesblock thừa đã tự hủy![]");
                    lastBlock.kill();
                }
            }
        }

        updateMenuVisibility();
    }

    /** WallBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class CopesBuild extends Wall.WallBuild {
        public final float range = RANGE_SIZE;
        float boostTimer = 0f;

        public CopesBuild(Wall outer) {
            outer.super();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            boostTimer += Time.delta;
            if (boostTimer >= 30f) {
                boostTimer = 0f;
                applyPerformanceBoost();
            }
        }

        public void applyPerformanceBoost() {
            Groups.build.each(building -> {
                if (building == this) return;

                boolean inSquare = Math.abs(building.x - x) <= range && Math.abs(building.y - y) <= range;

                if (inSquare && building.team == team) {
                    boolean isDrill = building instanceof Drill.DrillBuild;
                    boolean isBeryiDrill = building.block != null
                        && ("newex-beryi-drills".equals(building.block.name) || "beryi-drills".equals(building.block.name));

                    if (isDrill || isBeryiDrill) {
                        if (building instanceof DrillBoostable) {
                            ((DrillBoostable) building).applyBoost(1.5f, 35f);
                        }
                        if (Mathf.chance(0.45)) {
                            copesBuffCircleEffect.at(building.x, building.y);
                        }
                    }
                }
            });
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            Drawf.dashSquare(Pal.accent, x, y, range * 2f);
        }
    }
}
