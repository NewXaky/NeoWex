package newex;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Decal;
import mindustry.gen.Entityc;
import mindustry.gen.Groups;

/**
 * Rút ngắn thời gian tồn tại của các vết đen dưới đất (scorch/rubble khi unit hoặc công trình bị hạ) để đỡ lag.
 * Mặc định của game là 60 giây; ở đây đặt lại theo cài đặt (0 = không giữ vết nào, 60 = như game gốc),
 * đồng thời giới hạn tổng số vết cùng lúc (tự động chặt hơn trên Mobile).
 */
public final class DecalLimiter {
    private DecalLimiter() {}

    public static final String KEY_LIFE = "newex-decal-life";
    public static final int DEFAULT_SECONDS = 8;
    public static final int MAX_SECONDS = 60;
    /** Số vết tối đa tồn tại cùng lúc trên PC / Mobile (xoá vết cũ nhất khi vượt). */
    static final int CAP_PC = 80;
    static final int CAP_MOBILE = 40;

    private static final Seq<Decal> tmp = new Seq<>();
    private static float scanAcc = 0f;

    public static int lifeSeconds() {
        return Math.max(0, Math.min(MAX_SECONDS, Core.settings.getInt(KEY_LIFE, DEFAULT_SECONDS)));
    }

    public static void setLifeSeconds(int s) {
        Core.settings.put(KEY_LIFE, Math.max(0, Math.min(MAX_SECONDS, s)));
    }

    public static void init() {
        if (Vars.headless) return;
        Events.run(Trigger.update, DecalLimiter::tick);
        Events.on(WorldLoadEvent.class, e -> {
            scanAcc = 0f;
            tmp.clear();
        });
    }

    private static void tick() {
        if (Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) return;
        final float interval = Vars.mobile ? 20f : 24f;
        scanAcc += Time.delta;
        if (scanAcc < interval) return;
        scanAcc = 0f;

        final int sec = lifeSeconds();
        if (sec >= MAX_SECONDS) return;
        final float max = sec * 60f;
        final int cap = Vars.mobile ? CAP_MOBILE : CAP_PC;

        // Decal không có nhóm riêng (Groups.decal không tồn tại) nên lọc từ Groups.all
        tmp.clear();
        for (int i = 0, n = Groups.all.size(); i < n; i++) {
            Entityc e = Groups.all.index(i);
            if (e instanceof Decal) tmp.add((Decal) e);
        }
        if (tmp.isEmpty()) return;
        for (int i = 0; i < tmp.size; i++) {
            Decal d = tmp.get(i);
            if (sec <= 0) { d.remove(); continue; }
            if (d.lifetime > max) d.lifetime = max;
        }
        if (sec > 0 && tmp.size > cap) {
            // vết già nhất (đã tồn tại lâu nhất) bị xoá trước
            tmp.sort((a, b) -> Float.compare(b.time, a.time));
            for (int i = 0, r = tmp.size - cap; i < r; i++) tmp.get(i).remove();
        }
        tmp.clear();
    }
}
