package newex;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.func.Cons2;
import arc.graphics.Color;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ctype.ContentType;
import mindustry.entities.Effect;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ServerLoadEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ net-sync.js (global.NexNet).
 * Lớp đồng bộ mạng: client gửi yêu cầu lên server (REQ), server phát lại cho client (TOAST/SHAKE/CFG/RPC/BCAST).
 * Gói tin dạng "kind|payload".
 */
public final class NexNet {
    private static final String REQ = "newex-req";
    private static final String TOAST = "newex-toast";
    private static final String SHAKE = "newex-shake";
    private static final String CFG = "newex-cfg";
    private static final String RPC = "newex-rpc";       // server -> 1 client (unicast)
    private static final String BCAST = "newex-bcast";   // server -> tất cả client (broadcast)

    private static final class HandlerEntry {
        final Cons2<Player, String> fn;
        final boolean restricted;

        HandlerEntry(Cons2<Player, String> fn, boolean restricted) {
            this.fn = fn;
            this.restricted = restricted;
        }
    }

    private static final Map<String, HandlerEntry> handlers = new HashMap<>();
    private static final Map<String, Integer> cooldowns = new HashMap<>();
    private static final Map<String, Long> lastUse = new HashMap<>();
    private static final Map<String, Cons<String>> clientHandlers = new HashMap<>();
    private static final Map<String, Cons<String>> broadcastHandlers = new HashMap<>();
    private static boolean registered = false;
    private static boolean inited = false;

    private NexNet() {}

    /** Gọi 1 lần từ NexMod. */
    public static void init() {
        if (inited) return;
        inited = true;

        registerDefaultHandlers();
        register();
        Events.on(ClientLoadEvent.class, e -> register());
        Events.on(ServerLoadEvent.class, e -> register());
        Events.on(WorldLoadEvent.class, e -> register());
    }

    // ------------------------------------------------------------------ tiện ích parse

    /** Trả Integer.MIN_VALUE nếu không đọc được. */
    private static int parseInt(String s) {
        if (s == null) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            try {
                // JS parseInt("12.5") = 12
                double d = Double.parseDouble(s.trim());
                if (Double.isNaN(d) || Double.isInfinite(d)) return Integer.MIN_VALUE;
                return (int) d;
            } catch (NumberFormatException e2) {
                return Integer.MIN_VALUE;
            }
        }
    }

    /** Trả NaN nếu không đọc được. */
    private static float parseFloat(String s) {
        if (s == null) return Float.NaN;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static String arg(String[] a, int i) {
        return i < a.length ? a[i] : null;
    }

    private static String[] split(String payload) {
        return payload.split("\\|", -1);
    }

    private static String[] splitKind(Object content) {
        String s = String.valueOf(content);
        int i = s.indexOf('|');
        return new String[]{i < 0 ? s : s.substring(0, i), i < 0 ? "" : s.substring(i + 1)};
    }

    // ------------------------------------------------------------------ client -> server

    public static boolean isHostPlayer(Player player) {
        return player == null || player == Vars.player;
    }

    private static void dispatch(Player player, String content) {
        String[] kp = splitKind(content);
        String kind = kp[0], payload = kp[1];
        HandlerEntry entry = handlers.get(kind);
        if (entry == null) return;

        if (!isHostPlayer(player)) {
            if (entry.restricted && !Core.settings.getBool("newex-allow-client-actions", true)) return;
            long now = Time.millis();
            String key = player.id + ":" + kind;
            Integer w = cooldowns.get(kind);
            int wait = w != null ? w : 120;
            Long last = lastUse.get(key);
            if (last != null && now - last < wait) return;
            lastUse.put(key, now);
        }

        try {
            entry.fn.get(player, payload);
        } catch (Throwable err) {
            Log.err("[NexNet] " + kind + ": " + err);
        }
    }

    /** Đăng ký handler phía server cho loại gói {@code kind}. */
    public static void on(String kind, Cons2<Player, String> fn, boolean restricted) {
        handlers.put(kind, new HandlerEntry(fn, restricted));
    }

    public static void on(String kind, Cons2<Player, String> fn, boolean restricted, int cooldown) {
        handlers.put(kind, new HandlerEntry(fn, restricted));
        cooldowns.put(kind, cooldown);
    }

    public static void send(String kind, Object payload) {
        String body = kind + "|" + (payload == null ? "" : String.valueOf(payload));
        if (Vars.net.client()) {
            Call.serverPacketReliable(REQ, body);
        } else {
            dispatch(Vars.player, body);
        }
    }

    // ------------------------------------------------------------------ server -> 1 client (unicast)

    private static void clientDispatch(Object content) {
        String[] p = splitKind(content);
        Cons<String> fn = clientHandlers.get(p[0]);
        if (fn == null) return;
        try {
            fn.get(p[1]);
        } catch (Throwable err) {
            Log.err("[NexNet] rpc " + p[0] + ": " + err);
        }
    }

    public static void onClient(String kind, Cons<String> fn) {
        clientHandlers.put(kind, fn);
    }

    /** Gửi 1 message riêng từ server tới đúng 1 client. Chỉ server mới được gọi. */
    public static void sendTo(Player player, String kind, Object payload) {
        String body = kind + "|" + (payload == null ? "" : String.valueOf(payload));
        if (Vars.net.client()) return;
        if (isHostPlayer(player)) {
            clientDispatch(body);
            return;
        }
        if (player.con != null) {
            Call.clientPacketReliable(player.con, RPC, body);
        }
    }

    // ------------------------------------------------------------------ server -> tất cả client (broadcast)

    private static void broadcastDispatch(Object content) {
        String[] p = splitKind(content);
        Cons<String> fn = broadcastHandlers.get(p[0]);
        if (fn == null) return;
        try {
            fn.get(p[1]);
        } catch (Throwable err) {
            Log.err("[NexNet] bcast " + p[0] + ": " + err);
        }
    }

    public static void onBroadcast(String kind, Cons<String> fn) {
        broadcastHandlers.put(kind, fn);
    }

    /** Chỉ server mới được gọi. Host cũng tự nhận (vừa là server vừa là client hiển thị). */
    public static void broadcast(String kind, Object payload) {
        if (Vars.net.client()) return;
        String body = kind + "|" + (payload == null ? "" : String.valueOf(payload));
        broadcastDispatch(body);
        if (Vars.net.server()) {
            Call.clientPacketReliable(BCAST, body);
        }
    }

    // ------------------------------------------------------------------ toast / shake / fx

    private static void showToast(String text, float dur) {
        if (Vars.headless || Vars.ui == null) return;
        Vars.ui.showInfoToast(text, dur);
    }

    public static void toastTo(Player player, String text, float dur) {
        if (isHostPlayer(player)) {
            showToast(text, dur);
            return;
        }
        if (player.con != null) {
            Call.clientPacketReliable(player.con, TOAST, dur + "|" + text);
        }
    }

    public static void toastAll(String text, float dur) {
        showToast(text, dur);
        if (Vars.net.server()) {
            Call.clientPacketReliable(TOAST, dur + "|" + text);
        }
    }

    public static void shake(float intensity, float duration, float x, float y) {
        if (!Vars.headless) Effect.shake(intensity, duration, x, y);
        if (Vars.net.server()) {
            Call.clientPacketReliable(SHAKE, intensity + "|" + duration + "|" + x + "|" + y);
        }
    }

    public static void fx(Effect effect, float x, float y) {
        fx(effect, x, y, 0f, Color.white);
    }

    public static void fx(Effect effect, float x, float y, float rot) {
        fx(effect, x, y, rot, Color.white);
    }

    public static void fx(Effect effect, float x, float y, float rot, Color color) {
        if (effect == null) return;
        if (Vars.net.client()) {
            send("fx", effect.id + "|" + x + "|" + y + "|" + rot);
            return;
        }
        Call.effect(effect, x, y, rot, color == null ? Color.white : color);
    }

    // ------------------------------------------------------------------ tài nguyên

    /** Trừ {@code amount} {@code item} khỏi lõi (client thì gửi yêu cầu lên server). Bỏ qua khi chế độ free. */
    public static void take(Building core, Item item, double amount) {
        if (core == null || item == null) return;
        if (NexFree.active()) return;
        double n = Math.floor(amount);
        if (!(n > 0)) return;
        if (Vars.net.client()) {
            send("take", item.id + "|" + (long) n);
        } else if (core.items != null) {
            core.items.remove(item, (int) n);
        }
    }

    public static void give(Building core, Item item, double amount) {
        if (core == null || item == null) return;
        double n = Math.floor(amount);
        if (!(n > 0)) return;
        if (!Vars.net.client() && core.items != null) core.items.add(item, (int) n);
    }

    // ------------------------------------------------------------------ cấu hình công trình

    private static void applyConfig(Building build, int n) {
        if (build == null || !build.isValid()) return;
        try {
            build.configured(null, Integer.valueOf(n));
        } catch (Throwable err) {
            Log.err("[NexNet] config: " + err);
        }
    }

    public static void configure(Building build, Object value) {
        if (build == null) return;
        if (!(value instanceof Number)) {
            if (Vars.net.active()) {
                Call.tileConfig(Vars.player, build, value);
            } else {
                build.configure(value);
            }
            return;
        }
        double d = ((Number) value).doubleValue();
        if (Double.isNaN(d)) return;
        long n = (long) Math.floor(d);
        send("cfg", build.pos() + "|" + n);
    }

    public static void setOverlay(Tile tile, Block block) {
        if (tile == null || Vars.net.client()) return;
        tile.setOverlayNet(block);
    }

    public static void setBlock(Tile tile, Block block, Team team, int rotation) {
        if (tile == null || Vars.net.client()) return;
        tile.setNet(block, team == null ? Team.derelict : team, rotation);
    }

    public static void setBlock(Tile tile, Block block, Team team) {
        setBlock(tile, block, team, 0);
    }

    // ------------------------------------------------------------------ handler mặc định

    private static void registerDefaultHandlers() {
        on("take", (player, payload) -> {
            if (player == null) return;
            String[] a = split(payload);
            int id = parseInt(arg(a, 0));
            int amount = parseInt(arg(a, 1));
            if (id == Integer.MIN_VALUE || amount == Integer.MIN_VALUE) return;
            Item item = Vars.content.getByID(ContentType.item, id);
            mindustry.world.blocks.storage.CoreBlock.CoreBuild core = player.team().core();
            if (core == null || item == null || !(amount > 0) || amount > 5000000) return;
            if (NexFree.active()) return;
            if (core.items.has(item, amount)) core.items.remove(item, amount);
        }, false, 0);

        on("cfg", (player, payload) -> {
            String[] a = split(payload);
            int pos = parseInt(arg(a, 0));
            int n = parseInt(arg(a, 1));
            if (pos == Integer.MIN_VALUE || n == Integer.MIN_VALUE) return;
            Building build = Vars.world.build(pos);
            if (build == null) return;
            if (player != null && !isHostPlayer(player) && build.team != player.team()) return;
            applyConfig(build, n);
            if (Vars.net.server()) {
                Call.clientPacketReliable(CFG, pos + "|" + n);
            }
        }, false, 0);

        on("fx", (player, payload) -> {
            String[] a = split(payload);
            int id = parseInt(arg(a, 0));
            float x = parseFloat(arg(a, 1));
            float y = parseFloat(arg(a, 2));
            float r = parseFloat(arg(a, 3));
            if (id == Integer.MIN_VALUE || Float.isNaN(x) || Float.isNaN(y)) return;
            if (id < 0 || id >= Effect.all.size) return;
            Call.effect(Effect.all.get(id), x, y, Float.isNaN(r) ? 0f : r, Color.white);
        }, false, 0);
    }

    // ------------------------------------------------------------------ đăng ký packet handler

    private static void register() {
        if (registered) return;
        try {
            if (Vars.netServer == null || Vars.netClient == null) return;

            Vars.netServer.addPacketHandler(REQ, (player, content) -> dispatch(player, content));

            Vars.netClient.addPacketHandler(TOAST, content -> {
                String s = String.valueOf(content);
                int i = s.indexOf('|');
                if (i < 0) return;
                float d = parseFloat(s.substring(0, i));
                if (Float.isNaN(d) || d == 0f) d = 2f;
                showToast(s.substring(i + 1), d);
            });

            Vars.netClient.addPacketHandler(CFG, content -> {
                String[] a = split(String.valueOf(content));
                int pos = parseInt(arg(a, 0));
                int n = parseInt(arg(a, 1));
                if (pos == Integer.MIN_VALUE || n == Integer.MIN_VALUE) return;
                applyConfig(Vars.world.build(pos), n);
            });

            Vars.netClient.addPacketHandler(SHAKE, content -> {
                if (Vars.headless) return;
                String[] a = split(String.valueOf(content));
                if (a.length < 4) return;
                Effect.shake(parseFloat(a[0]), parseFloat(a[1]), parseFloat(a[2]), parseFloat(a[3]));
            });

            Vars.netClient.addPacketHandler(RPC, content -> clientDispatch(content));

            Vars.netClient.addPacketHandler(BCAST, content -> broadcastDispatch(content));

            registered = true;
        } catch (Throwable err) {
            Log.err("[NexNet] register failed: " + err);
        }
    }
}
