package newex;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Decal;
import mindustry.gen.Groups;

/**
 * Rút ngắn thời gian tồn tại của các vết đen dưới đất (scorch/rubble khi unit hoặc công trình bị hạ) để đỡ lag.
 * Mặc định của game là 60 giây; ở đây đặt lại theo cài đặt (0 = không giữ vết nào, 60 = như game gốc),
 * đồng thời giới hạn tổng số vết cùng lúc.
 */
public final class DecalLimiter {
    private DecalLimiter() {}

    public static final String KEY_LIFE = "newex-decal-life";
    public static final int DEFAULT_SECONDS = 8;
    public static final int MAX_SECONDS = 60;
    /** Số vết tối đa tồn tại cùng lúc (xoá vết cũ nhất khi vượt). */
    static final int CAP = 80;

    private static final Seq<Decal> tmp = new Seq<>();

    public static int lifeSeconds() {
        return Math.max(0, Math.min(MAX_SECONDS, Core.settings.getInt(KEY_LIFE, DEFAULT_SECONDS)));
    }

    public static void setLifeSeconds(int s) {
        Core.settings.put(KEY_LIFE, Math.max(0, Math.min(MAX_SECONDS, s)));
    }

    public static void init() {
        if (Vars.headless) return;
        Events.run(Trigger.update, DecalLimiter::tick);
    }

    private static void tick() {
        if (Vars.state == null || !Vars.state.isGame() || Groups.decal == null) return;
        // quét 4 lần/giây là đủ: vết mới sinh ra sẽ bị cắt ngắn trong tối đa ~15 tick
        if (Time.time % 15f > Time.delta) return;
        final int sec = lifeSeconds();
        if (sec >= MAX_SECONDS) return;
        final float max = sec * 60f;
        for (int i = Groups.decal.size() - 1; i >= 0; i--) {
            Decal d = Groups.decal.index(i);
            if (d == null) continue;
            if (sec <= 0) { d.remove(); continue; }
            if (d.lifetime > max) d.lifetime = max;
        }
        int n = Groups.decal.size();
        if (n > CAP) {
            tmp.clear();
            for (int i = 0; i < n; i++) {
                Decal d = Groups.decal.index(i);
                if (d != null) tmp.add(d);
            }
            // vết già nhất (đã tồn tại lâu nhất) bị xoá trước
            tmp.sort((a, b) -> Float.compare(b.time, a.time));
            for (int i = 0, r = n - CAP; i < r && i < tmp.size; i++) tmp.get(i).remove();
            tmp.clear();
        }
    }
}
