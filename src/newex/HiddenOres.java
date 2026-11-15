package newex;

import mindustry.type.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Thay cho biến JS global.newexHiddenOres: bản đồ "x,y" -> vật phẩm của quặng đang bị ẩn
 * (do loadout-selection.js ghi, các máy khoan đọc).
 */
public final class HiddenOres {
    private static final Map<String, Item> ores = new HashMap<>();

    private HiddenOres() {}

    public static String key(int x, int y) {
        return x + "," + y;
    }

    public static Item get(int x, int y) {
        return ores.get(key(x, y));
    }

    public static void put(int x, int y, Item item) {
        ores.put(key(x, y), item);
    }

    public static void clear() {
        ores.clear();
    }

    public static boolean isEmpty() {
        return ores.isEmpty();
    }

    /** Dùng khi cần duyệt toàn bộ (key dạng "x,y"). */
    public static Map<String, Item> all() {
        return ores;
    }
}
