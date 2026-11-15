package newex.content;

import arc.scene.ui.Dialog;
import mindustry.content.Items;
import mindustry.type.Item;
import mindustry.world.Tile;
import newex.HiddenOres;

/** Tiện ích dùng chung của Beryi / Drexkou (trích từ phần lặp lại trong beryi-drills.js và drexkou-drills.js). */
public final class DrillUtil {
    private DrillUtil() {}

    /** Vật phẩm mà ô này khai thác được: quặng đang hiện, quặng bị ẩn, hoặc tường graphite đặc biệt. */
    public static Item getTileDrop(Tile t) {
        if (t == null) return null;

        Item drop = t.drop();
        if (drop != null) return drop;

        Item hidden = HiddenOres.get(t.x, t.y);
        if (hidden != null) return hidden;

        if (t.block() != null) {
            String name = t.block().name;
            if ("graphitic-wall".equals(name) || "ore-wall-graphite".equals(name) || "graphite-wall".equals(name)) {
                return Items.graphite;
            }
        }

        return null;
    }

    public static boolean isGraphiteWallName(String name) {
        return "graphitic-wall".equals(name) || "ore-wall-graphite".equals(name) || "graphite-wall".equals(name);
    }

    /** Hộp thoại hướng dẫn dùng chung. */
    public static void showManual(String title, String body, String closeLabel) {
        final Dialog dialog = new Dialog(title);
        dialog.cont.margin(15f);
        dialog.cont.add(body).width(380f).wrap().get();
        dialog.buttons.button(closeLabel, () -> dialog.hide()).size(140f, 50f);
        dialog.show();
    }
}
