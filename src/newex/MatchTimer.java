package newex;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.serialization.Jval;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.SectorCaptureEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.type.Sector;
import mindustry.ui.Styles;

/**
 * Chuyển từ match_timer.js (global.matchTimer).
 * Đồng hồ đếm thời gian ván chơi, lưu vào settings để tiếp tục nếu vào lại trong 30 phút.
 * Shift + 1: ẩn/hiện và đưa đồng hồ ra giữa màn hình. Kéo thả để di chuyển.
 */
public final class MatchTimer {
    private static final String SETTINGS_KEY = "match-timer-state";
    private static final long RESUME_WINDOW_MS = 30L * 60L * 1000L;
    private static final boolean COUNT_WHILE_AWAY = false;
    private static final float SAVE_INTERVAL = 300f;
    private static final String TIMER_LABEL = "Time";

    private static final class Session {
        String key;
        double elapsed;
        int wave;
        long lastSeen;
        boolean ended;
        boolean resumed;
    }

    private static Session session = null;
    private static boolean pendingInit = true;
    private static float saveTimer = 0f;
    private static Table timerContainer = null;
    private static boolean timerVisible = true;

    private static float timerX;
    private static float timerY;

    // trạng thái kéo thả
    private static boolean isDragging = false;
    private static float dragOffsetX = 0f;
    private static float dragOffsetY = 0f;

    private MatchTimer() {}

    public static void init() {
        timerX = Core.settings.getFloat("newex-timer-x", 200f);
        timerY = Core.settings.getFloat("newex-timer-y", 200f);

        Events.run(Trigger.update, MatchTimer::update);
        Events.on(WorldLoadEvent.class, e -> pendingInit = true);
        Events.on(GameOverEvent.class, e -> endSession());
        Events.on(SectorCaptureEvent.class, e -> endSession());
        Events.on(ClientLoadEvent.class, e -> buildUi());
    }

    // ------------------------------------------------------------------ API công khai (global.matchTimer)

    public static double getSeconds() {
        return session == null ? 0 : session.elapsed;
    }

    public static String format(double total) {
        return formatTime(total);
    }

    public static boolean isEnded() {
        return session != null && session.ended;
    }

    public static void reset() {
        if (session == null) return;
        session.elapsed = 0;
        session.ended = false;
        session.lastSeen = System.currentTimeMillis();
        writeSaved(true);
    }

    // ------------------------------------------------------------------ nội bộ

    private static String pad(long n) {
        return n < 10 ? "0" + n : "" + n;
    }

    private static String formatTime(double total) {
        long s = (long) Math.max(0, Math.floor(total));
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return h > 0 ? pad(h) + ":" + pad(m) + ":" + pad(sec) : pad(m) + ":" + pad(sec);
    }

    private static String currentKey() {
        String prefix = Vars.net.active() ? "net:" : "local:";
        Sector sector = Vars.state.rules.sector;
        if (sector != null) {
            return prefix + "sector:" + sector.planet.name + ":" + sector.id;
        }
        String name = "unknown";
        try {
            name = String.valueOf(Vars.state.map.name());
        } catch (Throwable ignored) {
        }
        return prefix + "map:" + name;
    }

    private static Session readSaved() {
        try {
            String raw = Core.settings.getString(SETTINGS_KEY, "");
            if (raw == null || raw.length() == 0) return null;
            Jval data = Jval.read(raw);
            if (data == null || !data.isObject()) return null;
            String key = data.getString("key", null);
            if (key == null) return null;
            Session s = new Session();
            s.key = key;
            s.elapsed = data.getDouble("elapsed", 0);
            s.wave = (int) data.getDouble("wave", 0);
            s.lastSeen = (long) data.getDouble("lastSeen", 0);
            s.ended = data.getBool("ended", false);
            return s;
        } catch (Throwable err) {
            return null;
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static void writeSaved(boolean force) {
        if (session == null) return;
        try {
            String json = "{\"key\":" + jsonString(session.key)
                + ",\"elapsed\":" + session.elapsed
                + ",\"wave\":" + session.wave
                + ",\"lastSeen\":" + session.lastSeen
                + ",\"ended\":" + session.ended + "}";
            Core.settings.put(SETTINGS_KEY, json);
            if (force) Core.settings.forceSave();
        } catch (Throwable ignored) {
        }
    }

    private static void initSession() {
        long now = System.currentTimeMillis();
        String key = currentKey();
        int wave = Vars.state.wave;
        Session saved = readSaved();

        boolean resume = saved != null
            && saved.key.equals(key)
            && !saved.ended
            && wave >= saved.wave
            && (RESUME_WINDOW_MS <= 0 || now - saved.lastSeen <= RESUME_WINDOW_MS);

        double elapsed = 0;
        if (resume) {
            elapsed = saved.elapsed;
            if (COUNT_WHILE_AWAY) {
                elapsed += Math.max(0, (now - saved.lastSeen) / 1000.0);
            }
        }

        Session s = new Session();
        s.key = key;
        s.elapsed = elapsed;
        s.wave = wave;
        s.lastSeen = now;
        s.ended = false;
        s.resumed = resume;
        session = s;
        pendingInit = false;
        saveTimer = 0f;
        writeSaved(false);
    }

    private static void endSession() {
        if (session == null || session.ended) return;
        session.ended = true;
        session.lastSeen = System.currentTimeMillis();
        writeSaved(true);
    }

    private static String timerText() {
        if (session == null) return TIMER_LABEL + " [white]" + formatTime(0);
        String color = session.ended ? "[scarlet]" : "[accent]";
        return "[lightgray]" + TIMER_LABEL + " " + color + formatTime(session.elapsed);
    }

    private static void centerTimer() {
        if (timerContainer == null) return;
        float screenW = Core.graphics.getWidth();
        float screenH = Core.graphics.getHeight();
        float containerW = timerContainer.getWidth() != 0 ? timerContainer.getWidth() : 120f;
        float containerH = timerContainer.getHeight() != 0 ? timerContainer.getHeight() : 40f;

        timerX = (screenW - containerW) / 2f;
        timerY = (screenH - containerH) / 2f;

        timerContainer.setPosition(timerX, timerY);

        Core.settings.put("newex-timer-x", timerX);
        Core.settings.put("newex-timer-y", timerY);
    }

    private static void update() {
        boolean shiftPressed = Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight);
        if (shiftPressed && Core.input.keyTap(KeyCode.num1)) {
            timerVisible = !timerVisible;
            if (timerVisible) {
                centerTimer();
            }
        }

        if (!Vars.state.isGame()) {
            if (session != null) {
                session.lastSeen = System.currentTimeMillis();
                writeSaved(true);
                session = null;
            }
            pendingInit = true;
            return;
        }

        if (session == null || pendingInit) {
            if (session != null) {
                session.lastSeen = System.currentTimeMillis();
                writeSaved(false);
            }
            initSession();
        }

        if (session.ended || Vars.state.isPaused() || Vars.state.gameOver) return;

        session.elapsed += Time.delta / 60.0;
        session.wave = Vars.state.wave;
        session.lastSeen = System.currentTimeMillis();

        saveTimer += Time.delta;
        if (saveTimer >= SAVE_INTERVAL) {
            saveTimer = 0f;
            writeSaved(false);
        }
    }

    private static void buildUi() {
        Table container = new Table();
        timerContainer = container;
        container.setPosition(timerX, timerY);

        Table innerTable = new Table(Styles.black6);
        innerTable.label(() -> timerText()).pad(6f);
        container.add(innerTable);

        container.name = "match-timer";
        container.touchable = Touchable.enabled;
        container.visibility = () -> Vars.state.isGame() && Vars.ui.hudfrag.shown && timerVisible;

        container.pack();

        container.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                isDragging = false;
                dragOffsetX = x;
                dragOffsetY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - dragOffsetX) > 5 || Math.abs(y - dragOffsetY) > 5) {
                    isDragging = true;
                }
                if (isDragging) {
                    float rawX = container.x + x - dragOffsetX;
                    float rawY = container.y + y - dragOffsetY;

                    float maxX = Math.max(0f, Core.graphics.getWidth() - container.getWidth());
                    float maxY = Math.max(0f, Core.graphics.getHeight() - container.getHeight());

                    float newX = Mathf.clamp(rawX, 0f, maxX);
                    float newY = Mathf.clamp(rawY, 0f, maxY);

                    container.setPosition(newX, newY);

                    timerX = newX;
                    timerY = newY;
                    Core.settings.put("newex-timer-x", timerX);
                    Core.settings.put("newex-timer-y", timerY);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                isDragging = false;
            }
        });

        Vars.ui.hudGroup.addChild(container);
    }
}
