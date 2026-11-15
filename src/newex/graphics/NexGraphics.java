package newex.graphics;

import arc.Core;
import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;

/**
 * Điểm khởi tạo duy nhất cho toàn bộ đồ họa lấy từ WarHammer (shader, lỗ đen, thấu kính lồi, khiên void...).
 * Gọi NexGraphics.init() một lần trong NexMod (NexBlackHole.init() đã gọi hộ).
 *
 * Thứ tự giống WarHammerMod: sau FileTreeInitEvent -> post -> NexShaders.init(), NexVoidShieldRenderer.init(), NexRenderer.init().
 * PlanetSectorNumberOverlay (số sector trên bản đồ hành tinh) KHÔNG bật mặc định; muốn bật gọi PlanetSectorNumberOverlay.init().
 */
public final class NexGraphics {
    private NexGraphics() {}

    private static boolean started;

    public static void init() {
        if (started || Vars.headless) return;
        started = true;

        Events.on(EventType.FileTreeInitEvent.class, e -> Core.app.post(() -> {
            try { NexGfxFx.load(); } catch (Throwable t) { Log.err("NexGraphics: NexGfxFx.load thất bại.", t); }
            try { NexShaders.init(); } catch (Throwable t) { Log.err("NexGraphics: NexShaders.init thất bại.", t); }
            try { NexVoidShieldRenderer.init(); } catch (Throwable t) { Log.err("NexGraphics: NexVoidShieldRenderer.init thất bại.", t); }
            try { NexRenderer.init(); } catch (Throwable t) { Log.err("NexGraphics: NexRenderer.init thất bại.", t); }
        }));
    }
}
