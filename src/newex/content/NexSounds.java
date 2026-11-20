package newex.content;

import arc.Events;
import arc.audio.Sound;
import arc.files.Fi;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.FileTreeInitEvent;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.blocks.defense.turrets.LaserTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Hệ thống âm thanh NeoWex (chuyển đổi & đổi tên hoàn toàn sang newex).
 * Nạp 24 file âm thanh trong assets/sounds/ và tự động gán vào toàn bộ pháo của NeoWex.
 */
public final class NexSounds {
    public static Sound
        alert2 = new Sound(),
        launch = new Sound(),
        hugeBlast = new Sound(),
        jump = new Sound(),
        blast = new Sound(),
        energyShoot = new Sound(),
        machineGunShoot = new Sound(),
        sniperShoot = new Sound(),
        laser2 = new Sound(),
        laser3 = new Sound(),
        laser4 = new Sound(),
        laser5 = new Sound(),
        largeBeam = new Sound(),
        abyssalGlareLoop = new Sound(),
        cryoflamerLoop = new Sound(),
        heavyAdjudicatorFire01 = new Sound(),
        highIntensLaserLoop = new Sound(),
        hypervelDriverFire01 = new Sound(),
        kineticBlaster01 = new Sound(),
        pulseLaserFire01 = new Sound(),
        voltaicCannonFire01 = new Sound(),
        rifleLaser = new Sound(),
        boomLaser = new Sound(),
        shootGunDouble = new Sound();

    private static boolean initialized = false;
    private static boolean loaded = false;

    private NexSounds() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        Events.on(FileTreeInitEvent.class, e -> {
            if (!Vars.headless) load();
        });
        Events.on(ClientLoadEvent.class, e -> {
            if (!Vars.headless) {
                if (!loaded) load();
                applyToTurrets();
            }
        });
    }

    public static void load() {
        if (loaded || Vars.headless) return;
        loaded = true;
        alert2 = loadOgg("alert2");
        launch = loadOgg("launch");
        hugeBlast = loadOgg("hugeBlast");
        jump = loadOgg("jump");
        blast = loadOgg("DBZ1");
        energyShoot = loadOgg("JG1");
        machineGunShoot = loadOgg("mgsvGatling");
        sniperShoot = loadWav("sniper-shoot");
        laser2 = loadOgg("laser2");
        laser3 = loadOgg("laser3");
        laser4 = loadOgg("laser4");
        laser5 = loadOgg("laser5");
        largeBeam = loadOgg("largeBeam");
        abyssalGlareLoop = loadOgg("abyssal_glare_loop");
        cryoflamerLoop = loadOgg("cryoflamer_loop");
        heavyAdjudicatorFire01 = loadOgg("heavy_adjudicator_fire_01");
        highIntensLaserLoop = loadOgg("high_intens_laser_loop");
        hypervelDriverFire01 = loadOgg("hypervel_driver_fire_01");
        kineticBlaster01 = loadOgg("kinetic_blaster_01");
        pulseLaserFire01 = loadOgg("pulse_laser_fire_01");
        voltaicCannonFire01 = loadOgg("voltaic_cannon_fire_01");
        rifleLaser = loadWav("rifle-laser");
        boomLaser = loadOgg("boom_lazer");
        shootGunDouble = loadOgg("shoot-gun-double");
    }

    private static Sound loadOgg(String name) {
        try {
            Fi f = Vars.tree.get("sounds/" + name + ".ogg");
            return f != null && f.exists() ? new Sound(f) : new Sound();
        } catch (Throwable err) {
            Log.err("[NexSounds] Failed to load ogg: " + name, err);
            return new Sound();
        }
    }

    private static Sound loadWav(String name) {
        try {
            Fi f = Vars.tree.get("sounds/" + name + ".wav");
            return f != null && f.exists() ? new Sound(f) : new Sound();
        } catch (Throwable err) {
            Log.err("[NexSounds] Failed to load wav: " + name, err);
            return new Sound();
        }
    }

    /** Gán âm thanh phù hợp cho từng pháo thuộc mod NeoWex. */
    public static void applyToTurrets() {
        try {
            for (Block blk : Vars.content.blocks()) {
                if (!(blk instanceof Turret) || blk.name == null) continue;
                if (!blk.name.startsWith("newex-")) continue;
                Turret t = (Turret) blk;
                String id = blk.name.substring("newex-".length()).toLowerCase();

                Sound chosen = pickShootSound(id, t);
                if (chosen != null) t.shootSound = chosen;

                if (t instanceof ContinuousTurret || t instanceof LaserTurret) {
                    Sound lp = pickLoopSound(id);
                    if (lp != null) t.loopSound = lp;
                }
            }
        } catch (Throwable err) {
            Log.err("[NexSounds] applyToTurrets failed", err);
        }
    }

    private static Sound pickShootSound(String id, Turret t) {
        if (id.contains("thermion")) return hugeBlast;
        if (id.contains("nucleytor") || id.contains("emperfum")) return blast;
        if (id.contains("indeniter") || id.contains("xylaon")) return heavyAdjudicatorFire01;
        if (id.contains("vendicum") || id.contains("reguilater")) return hypervelDriverFire01;
        if (id.contains("tenytisyr") || id.contains("reflecounum")) return sniperShoot;
        if (id.contains("flazerd") || id.contains("lazash")) return boomLaser;
        if (id.contains("laytery") || id.contains("holyder")) return pulseLaserFire01;
        if (id.contains("galaxvorram") || id.contains("aether")) return largeBeam;
        if (id.contains("lavunder")) return laser4;
        if (id.contains("lyvervon")) return laser5;
        if (id.contains("laser")) return rifleLaser;
        if (id.contains("plasanod") || id.contains("blixalum")) return voltaicCannonFire01;
        if (id.contains("blaw") || id.contains("maxitoner")) return shootGunDouble;
        if (id.contains("heliyron") || id.contains("swordoder")) return energyShoot;
        if (id.contains("dtg") || id.contains("dor")) return kineticBlaster01;
        if (id.contains("cos") || id.contains("rangtaturs")) return machineGunShoot;
        if (id.contains("ravendes") || id.contains("persefer")) return launch;
        if (id.contains("therdum")) return laser3;

        if (t instanceof LaserTurret) return largeBeam;
        if (t instanceof ContinuousTurret) return laser2;
        if (t instanceof PowerTurret) return pulseLaserFire01;
        if (t.size >= 4) return heavyAdjudicatorFire01;
        if (t.size >= 3) return kineticBlaster01;
        return machineGunShoot;
    }

    private static Sound pickLoopSound(String id) {
        if (id.contains("cryo") || id.contains("plasanod") || id.contains("lavunder")) return cryoflamerLoop;
        if (id.contains("galax") || id.contains("abyss") || id.contains("thermion")) return abyssalGlareLoop;
        return highIntensLaserLoop;
    }
}
