package newex;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bộ đo hiệu năng dạng lấy mẫu (sampling) để tìm phần nào của mod gây lag.
 * Một luồng nền chụp stack của luồng chính mỗi ~10ms, đếm class của mod (newex.*) xuất hiện.
 * Cứ mỗi 60 giây (chỉ khi đang trong ván) ghi báo cáo vào Log và file "newex-profile.txt" trong thư mục dữ liệu game.
 * Tắt bằng cài đặt: Core.settings.put("newex-profiler", false).
 */
public final class NexProfiler {
    private NexProfiler() {}

    public static final String KEY = "newex-profiler";
    private static final long SAMPLE_MS = 10L;
    private static final float REPORT_SECONDS = 60f;

    private static Thread mainThread;
    private static volatile boolean running = false;

    // chỉ luồng lấy mẫu ghi vào các map này; luồng báo cáo cũng chính là luồng lấy mẫu nên không cần khóa
    private static final Map<String, Integer> self = new HashMap<>();
    private static final Map<String, Integer> incl = new HashMap<>();
    private static int total = 0, inMod = 0, fpsSum = 0, fpsN = 0;
    private static long lastReport = 0L;
    private static long gcCount0 = 0L, gcMs0 = 0L;

    public static void init() {
        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> {
            if (running || !Core.settings.getBool(KEY, true)) return;
            mainThread = Thread.currentThread();
            running = true;
            Thread t = new Thread(NexProfiler::loop, "newex-profiler");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        });
    }

    private static void loop() {
        lastReport = System.currentTimeMillis();
        long[] gc = gcTotals();
        gcCount0 = gc[0];
        gcMs0 = gc[1];
        while (running) {
            try {
                Thread.sleep(SAMPLE_MS);
                if (!Core.settings.getBool(KEY, true)) continue;
                if (Vars.state == null || !Vars.state.isGame() || Vars.state.isPaused()) {
                    lastReport = System.currentTimeMillis(); // không tính thời gian pause
                    continue;
                }
                sample();
                long now = System.currentTimeMillis();
                if (now - lastReport >= (long) (REPORT_SECONDS * 1000f)) {
                    report(now - lastReport);
                    lastReport = now;
                }
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sample() {
        Thread mt = mainThread;
        if (mt == null) return;
        StackTraceElement[] st = mt.getStackTrace();
        total++;
        if (Core.graphics != null) {
            fpsSum += Core.graphics.getFramesPerSecond();
            fpsN++;
        }
        boolean first = true;
        boolean any = false;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < st.length; i++) {
            String cn = st[i].getClassName();
            if (!cn.startsWith("newex.")) continue;
            any = true;
            String cls = cn;
            int dollar = cls.indexOf('$');
            String outer = dollar > 0 ? cls.substring(0, dollar) : cls;
            if (first) {
                first = false;
                self.merge(shortName(cn) + "." + st[i].getMethodName(), 1, Integer::sum);
            }
            if (seen.add(outer)) incl.merge(shortName(outer), 1, Integer::sum);
        }
        if (any) inMod++;
    }

    private static String shortName(String cn) {
        int dot = cn.lastIndexOf('.');
        String s = dot >= 0 ? cn.substring(dot + 1) : cn;
        int dollar = s.indexOf('$');
        if (dollar > 0) {
            // giữ lại class lồng (ví dụ CargoDrone$Foo) nhưng cắt phần lambda dài
            String rest = s.substring(dollar);
            if (rest.contains("$$Lambda")) s = s.substring(0, dollar) + "$lambda";
        }
        return s;
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> m, int n) {
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return l.size() > n ? l.subList(0, n) : l;
    }

    private static long[] gcTotals() {
        long c = 0, ms = 0;
        try {
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (b.getCollectionCount() > 0) c += b.getCollectionCount();
                if (b.getCollectionTime() > 0) ms += b.getCollectionTime();
            }
        } catch (Throwable ignored) {
        }
        return new long[]{c, ms};
    }

    private static void report(long windowMs) {
        if (total < 50) { reset(); return; }
        long[] gc = gcTotals();
        long gcN = gc[0] - gcCount0, gcMs = gc[1] - gcMs0;
        gcCount0 = gc[0];
        gcMs0 = gc[1];
        int avgFps = fpsN > 0 ? fpsSum / fpsN : -1;

        StringBuilder sb = new StringBuilder();
        sb.append("==== NexProfiler | cửa sổ ").append(windowMs / 1000).append("s | FPS TB ").append(avgFps)
          .append(" | mẫu ").append(total).append(" | phần trăm thời gian nằm trong code NeoWex: ")
          .append(total > 0 ? inMod * 100 / total : 0).append("% ====\n");
        if (gcN > 0 || gcMs > 0) {
            sb.append("GC: ").append(gcN).append(" lần, ").append(gcMs).append("ms (")
              .append(windowMs > 0 ? gcMs * 100 / windowMs : 0).append("% thời gian)\n");
        }
        try {
            sb.append("Unit: ").append(mindustry.gen.Groups.unit.size())
              .append(" | Công trình: ").append(mindustry.gen.Groups.build.size())
              .append(" | Đạn: ").append(mindustry.gen.Groups.bullet.size())
              .append(" | Tổng entity: ").append(mindustry.gen.Groups.all.size()).append('\n');
        } catch (Throwable ignored) {
        }
        sb.append("-- Class NeoWex xuất hiện nhiều nhất trong stack (% mẫu):\n");
        for (Map.Entry<String, Integer> en : top(incl, 12))
            sb.append(String.format("  %5.1f%%  %s%n", en.getValue() * 100f / total, en.getKey()));
        sb.append("-- Hàm NeoWex đang chạy trực tiếp (% mẫu):\n");
        for (Map.Entry<String, Integer> en : top(self, 12))
            sb.append(String.format("  %5.1f%%  %s%n", en.getValue() * 100f / total, en.getKey()));

        String text = sb.toString();
        Log.info("[NexProfiler]\n" + text);
        try {
            Fi f = Vars.dataDirectory.child("newex-profile.txt");
            f.writeString(text + "\n", true);
            if (f.length() > 512 * 1024) f.writeString(text + "\n", false); // chống file phình to
        } catch (Throwable ignored) {
        }
        final List<Map.Entry<String, Integer>> t1 = top(incl, 1);
        final String toast = "NexProfiler: FPS " + avgFps + (t1.isEmpty() ? ""
                : " | nặng nhất: " + t1.get(0).getKey() + " " + (t1.get(0).getValue() * 100 / total) + "%")
                + " (chi tiết: newex-profile.txt)";
        Core.app.post(() -> {
            try { Vars.ui.showInfoToast(toast, 6f); } catch (Throwable ignored) {}
        });
        reset();
    }

    private static void reset() {
        self.clear();
        incl.clear();
        total = 0;
        inMod = 0;
        fpsSum = 0;
        fpsN = 0;
    }
}
