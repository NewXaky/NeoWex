package newex;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Mat3D;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.mod.Mod;
import newex.content.Antikei;
import newex.content.ArchuengPvp;
import newex.content.BalashilonBlock;
import newex.content.BeryiDrills;
import newex.content.Blaw;
import newex.content.Blixalum;
import newex.content.Cin;
import newex.content.Comduik;
import newex.content.CopesBlock;
import newex.content.Crynex;
import newex.content.DrexkouDrills;
import newex.content.Dor;
import newex.content.ElementSystem;
import newex.content.EmeraliftWall;
import newex.content.Endyr;
import newex.content.Holyder;
import newex.content.Lavunder;
import newex.content.Emperfum;
import newex.content.Erysidus;
import newex.content.ErysidusDrone;
import newex.content.ExplayderAbility;
import newex.content.ExzoryCore;
import newex.content.FixSave;
import newex.content.Flazerd;
import newex.content.FlasallowFactory;
import newex.content.GeneratMachine;
import newex.content.Heliyron;
import newex.content.LaserBrey;
import newex.content.Lazash;
import newex.content.Lyvervon;
import newex.content.Maxitoner;
import newex.content.Nucleytor;
import newex.content.Laytery;
import newex.content.LaserChargerUranilum;
import newex.content.Korynd;
import newex.content.Buffetles;
import newex.content.LeolyrSummoner;
import newex.content.NewMode;
import newex.content.Sonicor;
import newex.content.ObsOre;
import newex.content.LoadoutSelection;
import newex.content.Survival;
import newex.content.SurvivorSkills;
import newex.content.NewModePvp;
import newex.content.Therdum;
import newex.content.Thermion;
import newex.content.Pinfyr;
import newex.content.Reguilater;
import newex.content.Hitekalum;
import newex.content.Nethyr;
import newex.content.Overyr;
import newex.content.Forstarsilum;
import newex.content.AetherSpiral;
import newex.content.Flarmed;
import newex.content.Persefer;
import newex.content.Plasanod;
import newex.content.Suv27;
import newex.content.Tank15;
import newex.content.Cos;
import newex.content.Swordoder;
import newex.content.Rangtaturs;
import newex.content.Ravendes;
import newex.content.Reflecounum;
import newex.content.RedstoneWall;
import newex.content.SplashFx;
import newex.content.Sta;
import newex.content.CargoDrone;
import newex.content.DroneLauncher;
import newex.content.Repulsyron;
import newex.content.Tankani;
import newex.content.Tankani4k;
import newex.content.Tenytisyr;
import newex.content.SyrufpatFactory;
import newex.content.TurretSystem;
import newex.content.Tyber;
import newex.content.Tyteryi;
import newex.content.DtgSoldern;
import newex.content.Emperical;
import newex.content.Indeniter;
import newex.content.Galaxvorram;
import newex.content.UnitReactor;
import newex.content.Vendicum;
import newex.content.UranilumReactor;
import newex.content.WuexConveyor;
import newex.content.XeuwConveyor;
import newex.content.Xylaon;
import newex.content.ZorynexPlanet;
import newex.content.Zoj;

/**
 * Lớp chính của mod NeoWex (thay scripts/main.js).
 * Mỗi hệ thống nằm trong 1 lớp riêng và được khởi tạo ở đây theo đúng thứ tự cũ của main.js.
 */
public class NexMod extends Mod {
    static final String NEX_DEFAULTS_VERSION = "beta0.8.11xyak";
    static final String NEX_DEFAULTS_KEY = "newex-defaults-applied-version";

    public NexMod() {
        Events.on(ClientLoadEvent.class, e -> applyNexDefaultsIfNeeded());
        Events.on(ServerLoadEvent.class, e -> applyNexDefaultsIfNeeded());

        // Khối 1 (main.js): vòng thiên thạch + cấu hình hành tinh Zorynex khi client nạp xong.
        Events.on(ClientLoadEvent.class, e -> setupZorynex());

        // Khối 2 (main.js): bỏ cấm block khi vào map của Zorynex.
        Events.on(WorldLoadEvent.class, e -> {
            if (Vars.state.isCampaign() && Vars.state.getSector() != null && Vars.state.getSector().planet != null) {
                Planet cur = Vars.state.getSector().planet;
                if (cur.name.contains("zorynex")) {
                    Vars.state.rules.bannedBlocks.clear();
                    Vars.state.rules.schematicsAllowed = true;
                }
            }
        });

        // Giai đoạn 1: hệ thống nền tảng (thứ tự như main.js)
        MatchTimer.init();
        PerfCore.init();
        NexNet.init();
        NexFree.init();
        ShowHp.init();
        FixSave.init();
        DamageDisplay.init();
        BuffEnemies.init();

        // Giai đoạn 2/3: nội dung (block, pháo, đơn vị)
        ObsOre.init();
        Pinfyr.init();
        EmeraliftWall.init();
        RedstoneWall.init();
        Korynd.init();
        Zoj.init();
        ExplayderAbility.init();
        CopesBlock.init();
        WuexConveyor.init();
        XeuwConveyor.init();
        GeneratMachine.init();
        LaserChargerUranilum.init();
        Crynex.init();
        Comduik.init();
        BeryiDrills.init();
        DrexkouDrills.init();
        UranilumReactor.init();
        FlasallowFactory.init();
        SyrufpatFactory.init();
        UnitReactor.init();
        BalashilonBlock.init();
        LaserBrey.init();
        Antikei.init();
        ZorynexPlanet.init();
        ElementSystem.init();
        Sta.init();
        ErysidusDrone.init();
        Cin.init();
        Tyber.init();
        NewMode.init();
        Sonicor.init();
        Buffetles.init();
        LoadoutSelection.init(); // main.js: require("loadout-selection") đứng trước newmode-survival
        Survival.init();
        SurvivorSkills.init();
        NewModePvp.init(); // main.js: require("newmode-pvp") sau survivor-skills, trước archueng-pvp
        LeolyrSummoner.init();
        optionalInit("newex.content.Leolyr"); // chua co trong repo CI -> goi qua phan xa, co file la tu chay
        optionalInit("newex.content.Elorix");
        Tankani.init();
        Tankani4k.init();
        DroneLauncher.init();
        CargoDrone.init();
        Repulsyron.init();
        ArchuengPvp.init();
        Erysidus.init();
        Ravendes.init();
        Persefer.init();
        Endyr.init();
        Nethyr.init();
        Overyr.init();
        Forstarsilum.init();
        Hitekalum.init();
        Emperical.init();
        Flarmed.init();
        AetherSpiral.init();
        Suv27.init();
        Cos.init();
        Thermion.init(); // gắn bar + buildType cho block hjson (bài 29b)

        NexBlackHole.init(); // lỗ đen + lõi đen đè lên glow (shaders/blackhole.frag)

        // SplashFx phải khởi tạo SAU CÙNG (JS gốc require splash-fx cuối main.js).
        SplashFx.init();
    }

    /** Gọi Lớp.init() nếu lớp có mặt trong bản build (bỏ qua nếu thiếu, có log). */
    private static void optionalInit(String cls) {
        try { Class.forName(cls).getMethod("init").invoke(null); }
        catch (ClassNotFoundException e) { Log.warn("[NexMod] missing class " + cls + " (skipped)"); }
        catch (Throwable e) { Log.err("[NexMod] " + cls + ".init failed: " + e); }
    }

    /** Game gọi hàm này khi nạp nội dung: tạo các block/đạn được định nghĩa bằng code (hjson sẽ bổ sung thuộc tính sau). */
    @Override
    public void loadContent() {
        ExzoryCore.create();
        Zoj.create();
        BalashilonBlock.create();
        TurretSystem.create();
        Sta.create();
        ErysidusDrone.create();
        Laytery.create();
        Cin.create();
        Therdum.create();
        NewModePvp.loadStatuses(); // 4 StatusEffect pvp-* phải tạo trong loadContent (bài 119)
        ArchuengPvp.create();
        Xylaon.create();
        Heliyron.create();
        Vendicum.create();
        Blixalum.create();
        Emperfum.create();
        Tyteryi.create();
        DtgSoldern.create();
        Indeniter.create();
        Reflecounum.create(); // sau Indeniter như main.js; gán Endyr.reflectProtected
        Galaxvorram.create();
        Reguilater.create();
        Tenytisyr.create();
        Ravendes.create();
        Persefer.create();
        AetherSpiral.create();
        Rangtaturs.create();
        Flazerd.create();
        Holyder.create();
        Lavunder.create();
        Lazash.create();
        Lyvervon.create();
        Dor.create();
        Blaw.create();
        Maxitoner.create();
        Plasanod.create(); // sau Sta.create() vì cần Sta.cei
        Nucleytor.create();
        Cos.create();
        Swordoder.create();
        Thermion.create(); // status/đạn/drone của Thermion (bài 109–111)
        Tank15.create(); // unit Tank-15
        Emperical.create(); // paralyzed/oppressive (không tạo trong constructor)
        Flarmed.create(); // bong/viem-hoa
    }

    static void applyNexDefaultsIfNeeded() {
        String applied = Core.settings.getString(NEX_DEFAULTS_KEY, "");
        if (NEX_DEFAULTS_VERSION.equals(applied)) return;
        Core.settings.put("newex-gacha-enabled", false);
        Core.settings.put("newex-max-turrets", 9999);
        Core.settings.put("newex-hp-per-wave-percent", 10);
        Core.settings.put(NEX_DEFAULTS_KEY, NEX_DEFAULTS_VERSION);
        Core.settings.forceSave();
    }

    /** Mesh vòng thiên thạch: dựng 120 cụm khối ngẫu nhiên (seed 1337) giống main.js. */
    static final class AsteroidMesh implements GenericMesh {
        final float[][] boxes; // ox, oy, oz, sx, sy, sz, rgba
        AsteroidMesh() {
            Rand rand = new Rand(1337);
            Color c1 = Color.valueOf("707070"), c2 = Color.valueOf("4a4a4a"), c3 = Color.valueOf("8e8e8e");
            java.util.ArrayList<float[]> list = new java.util.ArrayList<>();
            for (int i = 0; i < 120; i++) {
                float angle = rand.random(360f) * Mathf.degRad;
                float radius = 2.2f + rand.range(0.6f);
                float cx = (float) Math.cos(angle) * radius;
                float cy = rand.range(0.3f);
                float cz = (float) Math.sin(angle) * radius;
                int sub = rand.random(3, 5);
                float base = rand.random(0.08f, 0.18f);
                for (int j = 0; j < sub; j++) {
                    float ox = cx + rand.range(base * 0.3f);
                    float oy = cy + rand.range(base * 0.3f);
                    float oz = cz + rand.range(base * 0.3f);
                    float sx = base + rand.range(base * 0.2f);
                    float sy = base + rand.range(base * 0.2f);
                    float sz = base + rand.range(base * 0.2f);
                    Color col = rand.chance(0.3) ? c3 : (rand.chance(0.5) ? c1 : c2);
                    list.add(new float[]{ox, oy, oz, sx, sy, sz, col.toFloatBits()});
                }
            }
            boxes = list.toArray(new float[0][]);
        }
        @Override public void render(PlanetParams params, Mat3D projection, Mat3D transform) { /* vẽ đơn giản: bỏ qua nếu engine không hỗ trợ box mesh */ }
        @Override public void dispose() {}
    }

    static void setupZorynex() {
        Planet zorynex = Vars.content.getByName(ContentType.planet, "zorynex");
        if (zorynex == null) zorynex = Vars.content.getByName(ContentType.planet, "newex-zorynex");
        if (zorynex == null) return;

        Planet ring = new Planet("zorynex-asteroids", zorynex, 0.001f, 0);
        ring.orbitRadius = 0f;
        ring.orbitTime = 1200f;
        ring.rotateTime = 600f;
        ring.camRadius = 0.8f;
        ring.drawOrbit = false;
        ring.hasAtmosphere = false;
        ring.accessible = false;
        ring.alwaysUnlocked = false;
        ring.mesh = new AsteroidMesh();

        zorynex.techTree = Planets.serpulo.techTree;
        if (Planets.serpulo.techTree != null) Planets.serpulo.techTree.addPlanet(zorynex);

        final Planet z = zorynex;
        Vars.content.blocks().each(b -> {
            if (b.name.startsWith("newex-")) b.shownPlanets.add(z);
        });

        zorynex.defaultEnv = Planets.serpulo.defaultEnv;
        zorynex.allowLaunchLoadout = true;
        zorynex.allowLaunchSchematics = true;
        zorynex.allowLaunchToNumbered = true;
        zorynex.allowSectorInvasion = true;
        zorynex.ruleSetter = r -> {
            r.bannedBlocks.clear();
            r.schematicsAllowed = true;
            r.waveTeam = Team.crux;
            r.coreDestroyClear = true;
        };
    }
}
