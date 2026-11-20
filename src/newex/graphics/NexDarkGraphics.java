package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Đồ họa viền đen NeoWex:
 * 1) Áp viền đen đậm (outlineColor + outlineRadius) cho toàn bộ pháo và unit NeoWex giống phong cách đậm nét.
 * 2) Vẽ khung viền đen (vignette) tinh tế quanh 4 cạnh màn hình khi đang trong trận (có thể bật/tắt trong Cài đặt Newex).
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final Color DARK_OUTLINE = Color.valueOf("1b1b26");

    private static boolean initialized = false;
    private static final Color tmpEdge = new Color(0f, 0f, 0f, 0.72f);
    private static final Color tmpZero = new Color(0f, 0f, 0f, 0f);

    private NexDarkGraphics() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(ContentInitEvent.class, e -> applyDarkOutlines());

        if (!Vars.headless) {
            Events.run(Trigger.uiDrawEnd, NexDarkGraphics::drawDarkBorder);
        }
    }

    /** Gán màu viền đen đậm và độ dày viền rõ nét cho pháo & unit NeoWex. */
    private static void applyDarkOutlines() {
        try {
            for (Block blk : Vars.content.blocks()) {
                if (blk instanceof Turret && blk.name != null && blk.name.startsWith("newex-")) {
                    blk.outlineColor = DARK_OUTLINE;
                    if (blk.outlineRadius < 3) blk.outlineRadius = 3;
                }
            }
            for (UnitType ut : Vars.content.units()) {
                if (ut != null && ut.name != null && ut.name.startsWith("newex-")) {
                    ut.outlineColor = DARK_OUTLINE;
                    if (ut.outlineRadius < 3) ut.outlineRadius = 3;
                }
            }
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] applyDarkOutlines failed", err);
        }
    }

    /** Vẽ viền đen mềm quanh 4 cạnh màn hình để tạo chiều sâu và độ tương phản cao. */
    private static void drawDarkBorder() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
        if (!Core.settings.getBool(BORDER_KEY, true)) return;

        float w = Core.graphics.getWidth();
        float h = Core.graphics.getHeight();
        if (w <= 0f || h <= 0f) return;

        float borderY = Math.max(24f, h * 0.055f);
        float borderX = Math.max(24f, w * 0.04f);

        float edgeCol = tmpEdge.toFloatBits();
        float clearCol = tmpZero.toFloatBits();

        Draw.flush();
        // Cạnh dưới
        Fill.quad(0f, 0f, edgeCol, w, 0f, edgeCol, w, borderY, clearCol, 0f, borderY, clearCol);
        // Cạnh trên
        Fill.quad(0f, h, edgeCol, w, h, edgeCol, w, h - borderY, clearCol, 0f, h - borderY, clearCol);
        // Cạnh trái
        Fill.quad(0f, 0f, edgeCol, 0f, h, edgeCol, borderX, h, clearCol, borderX, 0f, clearCol);
        // Cạnh phải
        Fill.quad(w, 0f, edgeCol, w, h, edgeCol, w - borderX, h, clearCol, w - borderX, 0f, clearCol);
        Draw.reset();
    }
}
