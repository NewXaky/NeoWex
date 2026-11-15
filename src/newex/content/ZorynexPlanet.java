package newex.content;

import arc.Events;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.type.Planet;

/** Chuyển từ planets.js. Cấu hình hành tinh Zorynex: cây công nghệ, luật chơi, lưới 3D và bỏ cấm block. */
public final class ZorynexPlanet {
    private ZorynexPlanet() {}

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Planet zorynex = Vars.content.getByName(ContentType.planet, "newex-zorynex");
            if (zorynex == null) zorynex = Vars.content.getByName(ContentType.planet, "zorynex");
            if (zorynex == null) return;

            zorynex.techTree = Planets.serpulo.techTree;
            zorynex.defaultEnv = Planets.serpulo.defaultEnv;

            zorynex.allowLaunchLoadout = true;
            zorynex.allowLaunchSchematics = true;
            zorynex.allowLaunchToNumbered = true;
            zorynex.allowSectorInvasion = true;

            zorynex.ruleSetter = r -> {
                r.bannedBlocks.clear();
                r.schematicsAllowed = true;
            };

            GenericMesh[] meshList = new GenericMesh[]{
                new NoiseMesh(zorynex, 123456, 6, 0.62f, 3, 0.4f, 0.8f, 0.1f, Color.valueOf("#2d3142"), Color.valueOf("#4f5d75"), 1, 0.5f, 1.2f, 0.5f),
                new NoiseMesh(zorynex, 987654, 6, 0.64f, 3, 0.5f, 1.2f, 0.2f, Color.valueOf("#2a9d8f"), Color.valueOf("#e9c46a"), 1, 0.5f, 2.0f, 0.55f),
                new NoiseMesh(zorynex, 456789, 6, 0.65f, 5, 0.8f, 0.5f, 1.2f, Color.valueOf("#1d2d44"), Color.valueOf("#748cab"), 2, 0.6f, 1.5f, 0.45f),
                new NoiseMesh(zorynex, 777888, 6, 0.66f, 6, 0.85f, 0.35f, 2.2f, Color.valueOf("#e0f7fa"), Color.valueOf("#80deea"), 2, 0.7f, 0.8f, 0.35f)
            };

            zorynex.mesh = new MultiMesh(meshList);
        });

        Events.on(WorldLoadEvent.class, e -> {
            if (Vars.state.isCampaign() && Vars.state.getSector() != null && Vars.state.getSector().planet != null) {
                if (Vars.state.getSector().planet.name.contains("zorynex")) {
                    Vars.state.rules.bannedBlocks.clear();
                }
            }
        });
    }
}
