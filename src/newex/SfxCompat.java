package newex;

import arc.audio.Sound;
import mindustry.gen.Sounds;

import java.lang.reflect.Field;
import java.util.HashMap;

/**
 * Phát âm thanh của game theo TÊN bằng reflection, vì tên trong mindustry.gen.Sounds đổi giữa các bản
 * (v157 không còn spark, lasercharge, laserblast...). So khớp không phân biệt hoa/thường và thử vài tên thay thế;
 * không có tên nào khớp thì im lặng (Sounds.none) thay vì lỗi biên dịch/crash.
 */
public final class SfxCompat {
    private SfxCompat() {}

    private static final HashMap<String, Sound> cache = new HashMap<>();

    private static final HashMap<String, String[]> alias = new HashMap<>();
    static {
        alias.put("spark", new String[]{"spark", "sparkShoot", "shootArc", "electricHit"});
        alias.put("lasercharge", new String[]{"lasercharge", "laserCharge", "chargeLancer", "chargeLaser"});
        alias.put("lasercharge2", new String[]{"lasercharge2", "laserCharge2", "laserCharge", "chargeLaser"});
        alias.put("laserblast", new String[]{"laserblast", "laserBlast", "laserbig", "laserBig"});
        alias.put("message", new String[]{"message", "uiMessage", "chatMessage", "uiButton"});
        alias.put("hitLaser", new String[]{"hitLaser", "laserHit", "laserbig"});
        alias.put("shootBig", new String[]{"shootBig", "shootAltLong", "shootAlt", "shoot"});
        alias.put("plasmaflame", new String[]{"plasmaflame", "plasmaFlame", "flame", "shootSmite"});
        alias.put("plasmadrop", new String[]{"plasmadrop", "plasmaDrop", "explosion"});
    }

    private static Sound find(String name) {
        Sound cached = cache.get(name);
        if (cached != null) return cached;
        String[] names = alias.containsKey(name) ? alias.get(name) : new String[]{name};
        Sound found = null;
        outer:
        for (String n : names) {
            for (Field f : Sounds.class.getFields()) {
                if (f.getName().equalsIgnoreCase(n) && Sound.class.isAssignableFrom(f.getType())) {
                    try { found = (Sound) f.get(null); } catch (Throwable ignored) {}
                    if (found != null) break outer;
                }
            }
        }
        if (found == null) found = Sounds.none;
        cache.put(name, found);
        return found;
    }

    public static void at(String name, float x, float y, float pitch, float volume) {
        try { find(name).at(x, y, pitch, volume); } catch (Throwable ignored) {}
    }
}
