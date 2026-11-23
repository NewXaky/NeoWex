package newex;

import arc.Core;
import arc.Events;
import arc.func.Intc;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.Scene;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Giao diện "máy móc" duy nhất của newex, chạy trên mobile (ngang + dọc) lẫn PC.
 *
 * Khung chung {@link Panel}:
 *   thanh trên (đèn LED + tiêu đề kéo được + tab gạch chân + nút đóng)
 *   -> vùng header cố định (tuỳ chọn) -> vùng nội dung cuộn được -> vùng footer cố định (tuỳ chọn).
 * Đổi tab / đổi trang đi qua {@link Panel#go(int, Runnable)}: trang cũ trượt ra (90ms), đổi nội dung, trang mới trượt vào (170ms).
 *
 * Canh màn hình (mọi nền tảng, không còn điều kiện active()):
 *   - Bố cục được dựng lại theo kích thước + tỉ lệ khung hình thật của màn hình (ngang / dọc / cửa sổ PC),
 *     rồi phóng/thu cả hộp thoại cho vừa màn hình (trừ vùng tai thỏ nếu đọc được), luôn nằm trọn trong màn hình.
 *   - Đổi kích thước cửa sổ / xoay máy: tự dựng lại bố cục và nội dung.
 *
 * Hai cách dùng:
 *   1) Dựng mới:  Panel p = MachineUi.panel("Tiêu đề"); ... p.show();   (NewModePvp dùng cách này)
 *   2) Tự bọc hộp thoại có sẵn: hộp thoại cấu hình khối "newex-*", mọi trang newmode-pvp khi bản đồ chứa "pvpnewex"
 *      hoặc setPvpMode(true), hoặc hộp thoại đăng ký bằng track(d) / dialog(title). Loại ra bằng skip(d).
 */
public final class MachineUi {
    private MachineUi() {}

    private static final Map<BaseDialog, State> states = new WeakHashMap<>();
    private static final Set<BaseDialog> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<BaseDialog> skipped = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Color STEEL = Color.valueOf("8a97a8"), AMBER = Color.valueOf("ffb347"), PANEL = Color.valueOf("12161c");

    // ===== Tùy chỉnh giao diện MachineUi trong Cài đặt > Trò chơi =====
    public static final String KEY_SCALE = "newex-mui-scale";   // 60..150 (%)
    public static final String KEY_WIDTH = "newex-mui-width";   // 60..150 (%)
    public static final String KEY_HEIGHT = "newex-mui-height"; // 60..150 (%)
    public static final String KEY_BLUR = "newex-mui-blur";     // 0..100 (%) nền mờ / dim phía sau
    public static final String KEY_BLOOM = "newex-mui-bloom";   // 0..100 (%) độ phát sáng viền bloom
    public static final String KEY_ALPHA = "newex-mui-alpha";   // 15..100 (%) độ đục / trong suốt nền khung
    public static final String KEY_THEME = "newex-mui-theme";   // 0..7 tông màu viền khung

    private static final Color[] THEME_COLORS = {
        Color.valueOf("ffb347"), // 0: Hổ phách (Amber)
        Color.valueOf("38e8ff"), // 1: Xanh Cyan
        Color.valueOf("38ef7d"), // 2: Xanh Ngọc
        Color.valueOf("ff4d6d"), // 3: Đỏ Neon
        Color.valueOf("c77dff"), // 4: Tím Điện
        Color.valueOf("ffd166"), // 5: Vàng Kim
        Color.valueOf("4ea8de"), // 6: Xanh Dương
        Color.valueOf("e2e8f0")  // 7: Trắng Bạc
    };
    private static final String[] THEME_VI = {"Hổ phách", "Xanh Cyan", "Xanh Ngọc", "Đỏ Neon", "Tím Điện", "Vàng Kim", "Xanh Dương", "Trắng Bạc"};
    private static final String[] THEME_EN = {"Amber", "Cyan", "Emerald", "Neon Red", "Violet", "Gold", "Azure", "Silver"};
    private static final Color accentTmp = new Color(AMBER);
    private static long cfgRefreshAt = -1L;
    private static int cScale = 100, cWidth = 100, cHeight = 100, cBlur = 45, cBloom = 55, cAlpha = 88, cTheme = 0, cHash = 0;

    private static void refreshCfg() {
        long now = Time.millis();
        if (cfgRefreshAt >= 0L && now - cfgRefreshAt < 250L) return;
        cfgRefreshAt = now;
        cScale = Mathf.clamp(Core.settings.getInt(KEY_SCALE, 100), 60, 150);
        cWidth = Mathf.clamp(Core.settings.getInt(KEY_WIDTH, 100), 60, 150);
        cHeight = Mathf.clamp(Core.settings.getInt(KEY_HEIGHT, 100), 60, 150);
        cBlur = Mathf.clamp(Core.settings.getInt(KEY_BLUR, 45), 0, 100);
        cBloom = Mathf.clamp(Core.settings.getInt(KEY_BLOOM, 55), 0, 100);
        cAlpha = Mathf.clamp(Core.settings.getInt(KEY_ALPHA, 88), 15, 100);
        cTheme = Mathf.clamp(Core.settings.getInt(KEY_THEME, 0), 0, THEME_COLORS.length - 1);
        cHash = cScale * 31 + cWidth * 131 + cHeight * 521 + cTheme * 17;
    }

    public static Color accentColor() { refreshCfg(); return accentTmp.set(THEME_COLORS[cTheme]); }
    public static float scaleMul() { refreshCfg(); return cScale / 100f; }
    public static float widthMul() { refreshCfg(); return cWidth / 100f; }
    public static float heightMul() { refreshCfg(); return cHeight / 100f; }
    public static float blurAmt() { refreshCfg(); return cBlur / 100f; }
    public static float bloomAmt() { refreshCfg(); return cBloom / 100f; }
    public static float bgAlpha() { refreshCfg(); return cAlpha / 100f; }
    private static int cfgHash() { refreshCfg(); return cHash; }

    /** Chiều cao chuẩn (mobile ngang) / chiều rộng chuẩn (mobile dọc) của bố cục; nhỏ hơn -> chữ to hơn. */
    private static final float REF_H = 520f, REF_W = 400f;
    /** Hộp thoại chiếm tối đa tỉ lệ này của vùng khả dụng (cả ngang lẫn dọc). */
    private static final float FIT_LIMIT = 0.97f;
    /** Giới hạn phóng/thu để không méo giao diện trên màn hình quá lạ. */
    private static final float MIN_SCALE = 0.35f, MAX_SCALE = 4f;
    /** Thời gian trượt trang (ms): thoát / vào. Giữ y như giao diện cũ. */
    public static final int EXIT_MS = 90, ENTER_MS = 170;
    /** Thời gian animation mở khung MachineUi (ms): pha 1 vẽ 2 tia dọc từ tâm, pha 2 tách sang 2 bên trái/phải. */
    private static final int OPEN_P1_MS = 150, OPEN_P2_MS = 220, OPEN_TOTAL_MS = OPEN_P1_MS + OPEN_P2_MS;

    private static boolean pvpFlag = false;
    private static Object lastMap;
    private static boolean lastMapPvp;

    /** Trạng thái theo từng hộp thoại được bọc tự động. */
    private static final class State {
        /** Giữ yếu: Panel -> dialog là khóa của WeakHashMap, giữ mạnh thì hộp thoại không bao giờ được giải phóng. */
        WeakReference<Panel> panelRef;
        boolean failed, buttonsRemoved;

        Panel panel() { return panelRef == null ? null : panelRef.get(); }
    }

    private static boolean gameSettingsAdded = false;

    public static void init() {
        Events.run(Trigger.update, MachineUi::tick);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> registerGameSettings());
        }
    }

    private static void registerGameSettings() {
        if (gameSettingsAdded || Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.game == null) return;
        gameSettingsAdded = true;
        try {
            Core.bundle.getProperties().put("setting." + KEY_SCALE + ".name", T("MachineUi: Kích thước tổng (%)", "MachineUi: Overall size (%)"));
            Core.bundle.getProperties().put("setting." + KEY_WIDTH + ".name", T("MachineUi: Độ rộng khung (%)", "MachineUi: Frame width (%)"));
            Core.bundle.getProperties().put("setting." + KEY_HEIGHT + ".name", T("MachineUi: Độ dài/cao khung (%)", "MachineUi: Frame height (%)"));
            Core.bundle.getProperties().put("setting." + KEY_BLUR + ".name", T("MachineUi: Độ mờ nền phía sau (Blur %)", "MachineUi: Backdrop blur/dim (%)"));
            Core.bundle.getProperties().put("setting." + KEY_BLOOM + ".name", T("MachineUi: Độ phát sáng viền (Bloom %)", "MachineUi: Border bloom glow (%)"));
            Core.bundle.getProperties().put("setting." + KEY_ALPHA + ".name", T("MachineUi: Độ đục / trong suốt nền (%)", "MachineUi: Background opacity (%)"));
            Core.bundle.getProperties().put("setting." + KEY_THEME + ".name", T("MachineUi: Tông màu viền khung", "MachineUi: Border color theme"));

            Vars.ui.settings.game.sliderPref(KEY_SCALE, 100, 60, 150, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_WIDTH, 100, 60, 150, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_HEIGHT, 100, 60, 150, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_BLUR, 45, 0, 100, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_BLOOM, 55, 0, 100, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_ALPHA, 88, 15, 100, 5, i -> i + "%");
            Vars.ui.settings.game.sliderPref(KEY_THEME, 0, 0, THEME_COLORS.length - 1, 1, i -> {
                int idx = Mathf.clamp(i, 0, THEME_COLORS.length - 1);
                return "[#" + THEME_COLORS[idx].toString() + "]" + (isEn() ? THEME_EN[idx] : THEME_VI[idx]) + "[]";
            });
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ API công khai

    /** Giữ lại cho tương thích mã cũ: giao diện này chạy trên mọi nền tảng và mọi hướng màn hình nên luôn là true. */
    public static boolean active() { return true; }

    /** Bật/tắt chế độ PvP thủ công: bật thì mọi trang của mod (BaseDialog) đều được bọc tự động. */
    public static void setPvpMode(boolean on) { pvpFlag = on; }

    /** Chế độ PvP đang bật thủ công, hoặc bản đồ hiện tại có tên chứa "pvpnewex". */
    public static boolean pvpMode() {
        if (pvpFlag) return true;
        try {
            Object m = Vars.state.map;
            if (m == null) return false;
            if (m != lastMap) {
                lastMap = m;
                String n = Vars.state.map.name();
                String f = Vars.state.map.file != null ? Vars.state.map.file.nameWithoutExtension() : "";
                lastMapPvp = norm(n).contains("pvpnewex") || norm(f).contains("pvpnewex");
            }
            return lastMapPvp;
        } catch (Throwable ignored) { return false; }
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Đăng ký một hộp thoại có sẵn để luôn được bọc giao diện này. */
    public static void track(BaseDialog d) {
        if (d != null) tracked.add(d);
    }

    /** Tạo BaseDialog mới và đăng ký luôn (được bọc tự động). Muốn tự điều khiển tab/nội dung thì dùng {@link #panel(String)}. */
    public static BaseDialog dialog(String title) {
        BaseDialog d = new BaseDialog(title);
        track(d);
        return d;
    }

    /** Loại một hộp thoại khỏi việc bọc tự động. */
    public static void skip(BaseDialog d) {
        if (d != null) skipped.add(d);
    }

    /** Dựng một hộp thoại MachineUi mới hoàn toàn. */
    public static Panel panel(String title) {
        return new Panel(new BaseDialog(title), title, true);
    }

    static boolean isEn() { return Core.settings.getString("locale", "vi").startsWith("en"); }
    static String T(String vi, String en) { return isEn() ? en : vi; }

    // ------------------------------------------------------------------ canh màn hình

    private static java.lang.reflect.Field[] insetFields;

    /** Vùng khả dụng của màn hình: {rộng, cao, lề trái, lề dưới}. Trừ vùng tai thỏ nếu Scene có các field margin*. */
    private static float[] avail() {
        float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
        float l = 0f, r = 0f, t = 0f, b = 0f;
        try {
            if (insetFields == null) {
                String[] names = {"marginLeft", "marginRight", "marginTop", "marginBottom"};
                java.lang.reflect.Field[] fs = new java.lang.reflect.Field[4];
                for (int i = 0; i < 4; i++) {
                    try {
                        fs[i] = Scene.class.getDeclaredField(names[i]);
                        fs[i].setAccessible(true);
                    } catch (Throwable ignored) { fs[i] = null; }
                }
                insetFields = fs;
            }
            float[] v = new float[4];
            for (int i = 0; i < 4; i++) {
                if (insetFields[i] == null) continue;
                float x = insetFields[i].getFloat(Core.scene);
                v[i] = (Float.isNaN(x) || x < 0f || x > Math.min(sw, sh) * 0.25f) ? 0f : x;
            }
            l = v[0]; r = v[1]; t = v[2]; b = v[3];
        } catch (Throwable ignored) {}
        float aw = Math.max(100f, sw - l - r), ah = Math.max(100f, sh - t - b);
        return new float[]{aw, ah, l, b};
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ khung dùng chung

    /**
     * Một hộp thoại MachineUi. Nội dung do người gọi tự điền vào header()/content()/footer();
     * Panel lo khung, tab, trượt trang, canh màn hình, kéo thả, đóng.
     */
    public static final class Panel {
        public final BaseDialog dialog;
        private final boolean owned;

        private String title;
        private String[] tabNames = new String[0];
        private int tab;
        private Intc onTab;
        private boolean bigTabs;
        private String[] tabCaps;
        private java.util.function.IntFunction<String> tabBadge;
        private Runnable refill, ticker;
        private boolean scrollable = true, scrollX = false;
        private float maxW = 900f;
        float extraH;
        boolean keepButtons;
        /** Phần tử gốc của hộp thoại bọc tự động: trái = yêu cầu/thông tin, phải = thao tác (nằm trong Panel để không bị State giữ mạnh). */
        final Seq<Element> autoLeft = new Seq<>(), autoRight = new Seq<>();

        private final Table header = new Table(), content = new Table(), footer = new Table();
        private ScrollPane scroll;
        private Slide slide;
        Element shell;
        private float W, H, pageW, pageH;
        private float lastSw = -1f, lastSh = -1f;
        private int lastCfg = Integer.MIN_VALUE;
        private boolean moved;
        private float maxScale = MAX_SCALE;
        // animation mở giao diện (tâm -> 2 tia lên/xuống -> tách sang 2 bên trái/phải)
        long openT0 = 0L;
        boolean opening = false;
        // khôi phục vị trí cuộn sau khi dựng lại nội dung
        private float restoreY;
        private int restoreN;

        // chuyển trang
        private boolean exiting;
        private Runnable pendingApply;
        private int pendingDir;

        Panel(BaseDialog d, String title, boolean owned) {
            this.dialog = d;
            this.owned = owned;
            this.title = title == null ? "" : title;
            header.top().left();
            content.top().left();
            footer.top().left();
            if (owned) {
                skip(d);
                d.closeOnBack();
            }
            d.shown(this::startOpenAnim);
            d.hidden(this::onHidden);
            // chạy mỗi khung hình trên phần tử con bền vững (Dialog.show() xóa action của chính hộp thoại, không đụng tới con)
            d.cont.update(this::tick);
            // hộp thoại bọc tự động: người gọi đặt keepButtons/extraH rồi mới gọi relayout()
            if (owned) layout();
        }

        // ---- cấu hình

        public Panel title(String t) { this.title = t == null ? "" : t; return this; }

        /** Thanh tab trên đầu. onTab được gọi khi đã đổi tab (sau nửa trượt thoát), ngay trước khi dựng lại nội dung. */
        public Panel tabs(String[] names, int selected, Intc onTab) {
            this.tabNames = names == null ? new String[0] : names;
            this.tab = Math.max(0, Math.min(Math.max(0, tabNames.length - 1), selected));
            this.onTab = onTab;
            if (shell != null) layout();
            return this;
        }

        /** Tab lớn dễ thấy: hàng riêng dưới thanh tiêu đề, mỗi tab có chú thích nhỏ và huy hiệu (badge) tùy chọn. */
        public Panel bigTabs(String[] captions, java.util.function.IntFunction<String> badge) {
            this.bigTabs = true;
            this.tabCaps = captions;
            this.tabBadge = badge;
            if (shell != null) layout();
            return this;
        }

        /** Hàm điền nội dung; được gọi lại khi đổi tab, đổi kích thước màn hình, hoặc rebuild(). */
        public Panel onBuild(Runnable r) { this.refill = r; return this; }

        /** Hàm chạy mỗi khung hình khi hộp thoại đang mở (ví dụ kiểm tra phiên bản dữ liệu rồi rebuild()). */
        public Panel onTick(Runnable r) { this.ticker = r; return this; }

        /** false: nội dung đặt thẳng vào thân (không có cuộn chung), tự quản lý cuộn/kích thước. */
        public Panel scrollable(boolean v) { this.scrollable = v; if (shell != null) layout(); return this; }

        /** Cho phép cuộn ngang (mặc định chỉ cuộn dọc). */
        public Panel scrollX(boolean v) { this.scrollX = v; if (shell != null) layout(); return this; }

        /** Chiều rộng bố cục tối đa (đơn vị ảo). */
        public Panel maxWidth(float w) { this.maxW = w; if (shell != null) layout(); return this; }

        public Table header() { return header; }
        public Table content() { return content; }
        public Table footer() { return footer; }

        /** Bề rộng trang (đã trừ lề). */
        public float pageW() { return pageW; }
        /** Chiều cao trang (đã trừ lề, tính cả header/footer). */
        public float pageH() { return pageH; }
        /** Bề rộng nên dùng cho mỗi thẻ/hàng trong content() khi để pad(4) hai bên. */
        public float innerW() { return Math.max(120f, pageW - 8f); }
        public int tab() { return tab; }

        public void show() {
            if (lastCfg != cfgHash()) layout();
            if (refill != null) {
                refill.run();
                autoWrapPanel();
            }
            startOpenAnim();
            dialog.show();
        }

        void startOpenAnim() {
            openT0 = Time.millis();
            opening = true;
        }

        private void autoWrapPanel() {
            float mw = Math.max(110f, pageW - 8f);
            wrapOverflowLabels(header, mw);
            wrapOverflowLabels(content, mw);
            wrapOverflowLabels(footer, mw);
        }

        public void hide() { dialog.hide(); }

        /** Dựng lại nội dung ngay, không hiệu ứng (giữ nguyên vị trí cuộn). */
        public void rebuild() {
            float keep = scroll != null ? scroll.getScrollY() : 0f;
            if (refill != null) {
                refill.run();
                autoWrapPanel();
            }
            restoreScroll(keep);
        }

        /**
         * Dựng lại nội dung xóa sạch rồi thêm lại nên chiều cao tụt về 0 và ScrollPane tự kẹp scrollY về 0 (đang lướt xuống bị giật lên đầu).
         * Ép bố cục tính lại ngay rồi đặt lại vị trí cũ, và giữ thêm vài khung hình phòng khi bố cục chậm một nhịp.
         */
        private void restoreScroll(float y) {
            if (scroll == null || y <= 0.5f) return;
            // Hủy đà vuốt (fling) còn sót từ lần chạm trước
            stopFling();
            try { scroll.validate(); } catch (Throwable ignored) {}
            scroll.setScrollY(y);
            snapScroll();
            restoreY = y;
            restoreN = 4;
        }

        private static java.lang.reflect.Field[] flingFields;

        /** Đặt đà vuốt của ScrollPane về 0 (đọc field bằng reflection; không có field thì bỏ qua). */
        private void stopFling() {
            if (scroll == null) return;
            try {
                if (flingFields == null) {
                    String[] names = {"flingTimer", "velocityX", "velocityY"};
                    java.lang.reflect.Field[] fs = new java.lang.reflect.Field[names.length];
                    for (int i = 0; i < names.length; i++) {
                        try {
                            fs[i] = ScrollPane.class.getDeclaredField(names[i]);
                            fs[i].setAccessible(true);
                        } catch (Throwable ignored) { fs[i] = null; }
                    }
                    flingFields = fs;
                }
                for (java.lang.reflect.Field f : flingFields) if (f != null) f.setFloat(scroll, 0f);
            } catch (Throwable ignored) {}
        }

        /** Dựng lại cả khung lẫn nội dung (khi kích thước màn hình hoặc cài đặt đổi). */
        public void relayout() {
            float keep = scroll != null ? scroll.getScrollY() : 0f;
            layout();
            if (refill != null) {
                refill.run();
                autoWrapPanel();
            }
            restoreScroll(keep);
        }

        /** Cuộn mượt có vị trí hiển thị riêng; ép nó khớp ngay để không thấy cảnh nhảy lên đầu rồi trượt xuống lại. */
        private void snapScroll() {
            try { scroll.updateVisualScroll(); } catch (Throwable ignored) {}
        }

        public void scrollTop() {
            restoreN = 0;
            if (scroll != null) scroll.setScrollY(0f);
        }

        /** Chọn tab có hiệu ứng trượt; chọn lại tab đang xem thì chỉ cuộn lên đầu. */
        public void selectTab(final int ix) {
            if (ix < 0 || ix >= tabNames.length) return;
            if (ix == tab) { scrollTop(); return; }
            final int dir = ix > tab ? 1 : -1;
            go(dir, () -> {
                tab = ix;
                if (onTab != null) onTab.get(ix);
            });
        }

        /**
         * Chuyển trang có hiệu ứng: trượt thoát -> apply.run() -> dựng lại nội dung -> trượt vào.
         * dir > 0: trang mới đến từ bên phải; dir < 0: từ bên trái.
         */
        public void go(int dir, Runnable apply) {
            if (slide == null) { apply.run(); rebuild(); return; }
            if (exiting) {              // đang thoát dở: chốt luôn trang trước rồi chạy tiếp
                flush();
                apply.run();
                rebuild();
                scrollTop();
                slide.run(dir, true, ENTER_MS, null);
                return;
            }
            exiting = true;
            pendingApply = apply;
            pendingDir = dir;
            slide.run(dir, false, EXIT_MS, this::flush);
        }

        private void flush() {
            if (!exiting) return;
            exiting = false;
            Runnable a = pendingApply;
            int d = pendingDir;
            pendingApply = null;
            if (a != null) a.run();
            rebuild();
            scrollTop();
            if (slide != null) slide.run(d, true, ENTER_MS, null);
        }

        private void onHidden() {
            exiting = false;
            pendingApply = null;
            if (slide != null) slide.stop();
        }

        // ---- vòng đời mỗi khung hình

        /** Kiểm tra hộp thoại này có đang bị 1 BaseDialog khác mở đè lên trên không (tránh vẽ & tick chồng 2-3 trang). */
        boolean isCoveredByDialog() {
            if (Core.scene == null || Core.scene.root == null) return false;
            Seq<Element> kids = Core.scene.root.getChildren();
            boolean seenSelf = false;
            for (int i = 0; i < kids.size; i++) {
                Element e = kids.get(i);
                if (e == dialog) { seenSelf = true; continue; }
                if (seenSelf && e instanceof BaseDialog && e.visible) return true;
            }
            return false;
        }

        private void tick() {
            if (Core.scene == null || shell == null) return;
            if (isCoveredByDialog()) return;
            float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
            int ch = cfgHash();
            if (sw != lastSw || sh != lastSh || ch != lastCfg) {
                moved = false;
                if (exiting) flush();
                relayout();
            }
            fit();
            place();
            if (restoreN > 0 && scroll != null) {
                // Người dùng đang chạm/kéo: không giành quyền cuộn với họ
                if (Core.input.isTouched()) restoreN = 0;
                else {
                    restoreN--;
                    if (Math.abs(scroll.getScrollY() - restoreY) > 0.5f) { stopFling(); scroll.setScrollY(restoreY); snapScroll(); }
                }
            }
            if (ticker != null) ticker.run();
        }

        /** Phóng/thu cả hộp thoại quanh tâm để khung (cộng chân trang nếu có) nằm trọn trong vùng khả dụng. */
        void fit() {
            Element sh = shell;
            if (sh == null || Core.scene == null) return;
            float nw = sh.getWidth(), nh = sh.getHeight() + extraH;
            // hàng nút giữ lại có thể rộng hơn khung -> tính vào để không tràn khỏi màn hình
            if (keepButtons && dialog.buttons != null) nw = Math.max(nw, dialog.buttons.getPrefWidth() + 8f);
            if (nw <= 0f || nh <= 0f) return;
            float[] a = avail();
            float sm = scaleMul();
            float maxK = Math.min(FIT_LIMIT * a[0] / nw, FIT_LIMIT * a[1] / nh);
            float k = Math.min(maxK, maxScale * sm);
            k = clamp(k, MIN_SCALE, Math.max(MIN_SCALE, maxK));
            if (Math.abs(dialog.scaleX - k) > 0.002f || Math.abs(dialog.scaleY - k) > 0.002f) {
                dialog.setTransform(true);
                dialog.setOrigin(Align.center);
                dialog.setScale(k);
            }
        }

        /** Căn giữa vùng khả dụng, trừ khi người dùng đã kéo đi chỗ khác. */
        void place() {
            if (moved || Core.scene == null) return;
            float[] a = avail();
            float nx = Math.round(a[2] + a[0] / 2f - dialog.getWidth() / 2f);
            float ny = Math.round(a[3] + a[1] / 2f - dialog.getHeight() / 2f);
            if (Math.abs(dialog.x - nx) > 0.5f || Math.abs(dialog.y - ny) > 0.5f) dialog.setPosition(nx, ny);
        }

        // ---- dựng khung

        private void prepareDialog() {
            BaseDialog d = dialog;
            d.setFillParent(false);
            d.setKeepWithinStage(false);
            d.setMovable(false);
            d.margin(0f);
            d.cont.margin(0f);
            if (d.getCell(d.cont) != null) d.getCell(d.cont).pad(0f);
            if (d.titleTable != null) d.titleTable.remove();
            if (d.buttons != null) {
                if (keepButtons) d.buttons.margin(4f);
                else d.buttons.remove();
            }
        }

        /** Tính kích thước bố cục {rộng, cao} theo hướng, tỉ lệ màn hình và thanh kéo cài đặt, rồi dựng lại khung. */
        private void layout() {
            if (Core.scene == null) return;
            prepareDialog();
            final float[] av = avail();
            lastSw = Core.scene.getWidth();
            lastSh = Core.scene.getHeight();
            lastCfg = cfgHash();
            final boolean pc = !Vars.mobile;
            final float cap = Math.max(maxW, REF_H * 1.4f);
            float w, h;
            if (av[1] > av[0]) {                       // màn hình dọc
                if (pc) {
                    w = clamp(av[0] * 0.92f, 320f, Math.min(cap, 720f));
                    h = clamp(av[1] * 0.9f, 420f, 1000f);
                } else {
                    w = clamp(av[0] / 1.15f, 340f, 480f);
                    h = clamp(w * av[1] / av[0], 520f, 1100f);
                }
            } else {                                   // màn hình ngang
                if (pc) {
                    h = clamp(av[1] * 0.9f, 300f, 780f);
                    w = clamp(av[0] * 0.85f, 480f, cap);
                } else {
                    h = REF_H;
                    w = clamp(REF_H * av[0] / av[1], REF_H * 1.4f, cap);
                }
            }
            // Áp dụng tùy chỉnh % độ rộng & độ dài (chiều cao) từ Cài đặt > Trò chơi
            w = clamp(w * widthMul(), 280f, Math.max(280f, av[0] * 0.96f));
            h = clamp(h * heightMul(), 240f, Math.max(240f, av[1] * 0.96f));
            maxScale = pc ? 1f : MAX_SCALE;
            W = w;
            H = h;

            final float topH = Vars.mobile ? 44f : 38f, line = 2.5f, pad = 6f;
            final boolean tabsInBar = !bigTabs && tabNames.length > 0 && W >= 560f;
            final float tabsH = (tabNames.length > 0 && !tabsInBar) ? (bigTabs ? (Vars.mobile ? 60f : 54f) : (Vars.mobile ? 40f : 34f)) : 0f;
            final float shellH = Math.max(160f, H - extraH);
            final float areaH = shellH - topH - line - tabsH;
            pageW = W - pad * 2f;
            pageH = areaH - pad * 2f;

            // gỡ phần bền vững khỏi khung cũ trước khi dựng khung mới
            header.remove();
            content.remove();
            footer.remove();

            slide = new Slide();
            if (scrollable) {
                ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
                sp.setScrollingDisabled(!scrollX, false);
                sp.setFlickScroll(true);
                sp.setOverscroll(false, true);
                sp.setFadeScrollBars(true);
                sp.setSmoothScrolling(true);
                sp.setClip(true);
                final ScrollPane spf = sp;
                sp.addListener(new InputListener() {
                    @Override
                    public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                        if (pointer == -1 && Core.scene != null) Core.scene.setScrollFocus(spf);
                    }
                });
                float keep = scroll != null ? scroll.getScrollY() : 0f;
                scroll = sp;
                slide.add(sp).grow();
                slide.target = sp;
                sp.setScrollY(keep);
            } else {
                scroll = null;
                slide.add(content).grow();
                slide.target = content;
            }

            Table body = new Table();
            body.margin(pad);
            body.top().left();
            body.add(header).growX().row();
            body.add(slide).grow().row();
            body.add(footer).growX();

            final Color acc = new Color(accentColor());
            ShellFrame sh = new ShellFrame(this, W, shellH);
            sh.setClip(false);
            sh.add(topBar(topH, tabsInBar)).growX().height(topH).row();
            final Image divLine = sh.image(Tex.whiteui, acc).growX().height(line).get();
            divLine.update(() -> divLine.setColor(accentColor()));
            sh.row();
            if (tabsH > 0f) {
                Table row = new Table(Styles.black6);
                tabsInto(row, true);
                sh.add(row).growX().height(tabsH).row();
            }
            sh.add(body).size(W, areaH);

            shell = sh;
            dialog.cont.clearChildren();
            dialog.cont.add(sh).size(W, shellH);
            dialog.pack();
            place();
            fit();
        }

        private Table topBar(float topH, boolean tabsInBar) {
            final Table top = new Table();
            top.setBackground(Tex.whiteui);
            final Color topCol = new Color();
            top.update(() -> {
                topCol.set(PANEL).lerp(accentColor(), 0.16f);
                topCol.a = Math.min(1f, bgAlpha() + 0.08f);
                top.setColor(topCol);
            });

            Table zone = new Table();            // vùng giữ & kéo để di chuyển hộp thoại
            zone.touchable = Touchable.enabled;
            final Image led = zone.image(Tex.whiteui).size(8f).color(accentColor()).padLeft(8f).padRight(7f).get();
            led.update(() -> {
                Color ac = accentColor();
                led.setColor(ac.r, ac.g, ac.b, 0.55f + 0.45f * (float) Math.sin(Time.time * 0.08f));
            });
            Label tl = zone.add(title.isEmpty() ? "NEWEX" : title).left().growX().minWidth(0f).get();
            tl.setColor(Color.lightGray);
            tl.setFontScale(0.85f);
            tl.setEllipsis(true);
            dragHandle(zone, this);
            top.add(zone).growX().height(topH).minWidth(0f);

            if (tabsInBar) {
                Table tabs = new Table();
                tabsInto(tabs, false);
                top.add(tabs).growY().padLeft(6f).padRight(4f);
            }
            top.button(Icon.cancel, Styles.cleari, () -> dialog.hide()).size(topH);
            return top;
        }

        private void tabsInto(Table t, boolean fill) {
            for (int i = 0; i < tabNames.length; i++) {
                Cell<Table> c = t.add(bigTabs ? bigTabButton(i, this) : tabButton(tabNames[i], i, this, fill)).growY();
                if (fill) c.growX().uniformX();
                if (bigTabs) c.pad(3f);
            }
        }
    }

    // ------------------------------------------------------------------ hiệu ứng trượt trang

    /**
     * Vùng thân có xén: phần tử bên trong (target) trượt ngang + mờ dần (phủ lớp nền tối) khi đổi trang.
     * Công thức giống PageNav cũ: thoát nhanh (bậc 2), vào chậm dần (bậc 3).
     */
    private static final class Slide extends Table {
        Element target;
        float alpha = 1f;
        private boolean running, enter;
        private int dir;
        private int ms;
        private long t0;
        private Runnable done;

        Slide() {
            setClip(true);
            update(this::step);
        }

        @Override public float getPrefWidth() { return 0f; }
        @Override public float getPrefHeight() { return 0f; }
        @Override public float getMinWidth() { return 0f; }
        @Override public float getMinHeight() { return 0f; }

        void run(int dir, boolean enter, int ms, Runnable done) {
            this.dir = dir;
            this.enter = enter;
            this.ms = Math.max(1, ms);
            this.done = done;
            this.t0 = Time.millis();
            this.running = true;
            step();
        }

        void stop() {
            running = false;
            done = null;
            alpha = 1f;
            if (target != null) target.translation.set(0f, 0f);
        }

        private void step() {
            if (!running) return;
            float dist = Vars.mobile ? 28f : 40f;
            float k = Math.min(1f, (Time.millis() - t0) / (float) ms);
            float sh = enter ? (1f - (float) Math.pow(1f - k, 3)) : (1f - k * k);
            float off = (enter ? 1f : -1f) * (1f - sh) * dir * dist;
            alpha = sh;
            if (target != null) target.translation.set(off, 0f);
            if (k >= 1f) {
                running = false;
                if (enter) {
                    alpha = 1f;
                    if (target != null) target.translation.set(0f, 0f);
                }
                Runnable d = done;
                done = null;
                if (d != null) d.run();
            }
        }

        @Override
        public void draw() {
            super.draw();
            if (alpha < 0.995f) {
                Draw.color(0f, 0f, 0f, (1f - alpha) * 0.85f);
                Fill.crect(x, y, width, height);
                Draw.reset();
            }
        }
    }

    // ------------------------------------------------------------------ bọc tự động hộp thoại có sẵn

    private static boolean fromNewexConfig() {
        try {
            Building sel = Vars.control.input.config.getSelected();
            return sel != null && sel.block != null && sel.block.name.startsWith("newex-");
        } catch (Throwable ignored) { return false; }
    }

    private static boolean eligible(BaseDialog d, boolean cfg, boolean pvp) {
        if (skipped.contains(d)) return false;
        if (tracked.contains(d)) return true;
        if (d.getClass() == BaseDialog.class) return cfg || pvp;
        // lớp con do mod tự viết (không thuộc gói mindustry.*) cũng dùng trong chế độ PvP
        return pvp && !d.getClass().getName().startsWith("mindustry.");
    }

    private static void tick() {
        if (Vars.headless || Core.scene == null || !Vars.state.isGame()) return;
        boolean cfg = fromNewexConfig();
        boolean pvp = pvpMode();

        Seq<Element> kids = Core.scene.root.getChildren();
        for (int i = 0; i < kids.size; i++) {
            Element e = kids.get(i);
            if (!(e instanceof BaseDialog)) continue;
            BaseDialog d = (BaseDialog) e;

            State st = states.get(d);
            if (st == null) {
                if (!eligible(d, cfg, pvp)) continue;
                st = new State();
                states.put(d, st);
            } else if (st.failed) {
                continue;
            }

            try {
                Panel pn = st.panel();
                Element shell = pn == null ? null : pn.shell;
                if (shell == null || hasForeign(d, shell)) restyle(d, st);
                pn = st.panel();
                if (pn != null) { pn.fit(); pn.place(); }
            } catch (Throwable err) {
                st.failed = true;
                arc.util.Log.err("[MachineUi] Không dựng lại được hộp thoại, giữ giao diện cũ", err);
            }
        }
    }

    /** Trong d.cont có phần tử nào ngoài khung của mình không (mod vừa dựng lại nội dung). */
    private static boolean hasForeign(BaseDialog d, Element shell) {
        Seq<Cell> cells = d.cont.getCells();
        for (int i = 0; i < cells.size; i++) {
            Element el = cells.get(i).get();
            if (el != null && el != shell && !isSpacer(el)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ tiện ích dò phần tử

    private static boolean hasButton(Element e) {
        if (e instanceof Button) return true;
        if (e instanceof Group) {
            Seq<Element> ch = ((Group) e).getChildren();
            for (int i = 0; i < ch.size; i++) if (hasButton(ch.get(i))) return true;
        }
        return false;
    }

    private static int countButtons(Element e) {
        int n = e instanceof Button ? 1 : 0;
        if (e instanceof Group) {
            Seq<Element> ch = ((Group) e).getChildren();
            for (int i = 0; i < ch.size; i++) n += countButtons(ch.get(i));
        }
        return n;
    }

    private static boolean isSpacer(Element e) {
        if (e == null) return true;
        if (e.getClass() == Element.class) return true;
        return e instanceof Table && ((Table) e).getChildren().isEmpty();
    }

    /** Lấy chữ tiêu đề của hộp thoại gốc (label đầu tiên trong titleTable). */
    private static String titleOf(BaseDialog d) {
        try {
            if (d.titleTable != null) {
                Seq<Element> ch = d.titleTable.getChildren();
                for (int i = 0; i < ch.size; i++) {
                    if (ch.get(i) instanceof Label) return ((Label) ch.get(i)).getText().toString();
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    /** Label.getAlignment() không có ở v157 -> đọc field labelAlign bằng reflection (không đọc được thì coi như không phải center). */
    private static java.lang.reflect.Field labelAlignField, cellMaxWField;
    private static boolean labelAlignTried, cellMaxWTried;
    /** Các Label đã được ép wrap rồi: tránh gọi setWrap lặp lại làm bố cục giật. */
    private static final Set<Label> fitted = Collections.newSetFromMap(new WeakHashMap<>());

    private static int labelAlign(Label l) {
        try {
            if (!labelAlignTried) {
                labelAlignTried = true;
                try {
                    labelAlignField = Label.class.getDeclaredField("labelAlign");
                    labelAlignField.setAccessible(true);
                } catch (Throwable ignored) { labelAlignField = null; }
            }
            return labelAlignField == null ? -1 : labelAlignField.getInt(l);
        } catch (Throwable ignored) { return -1; }
    }

    /** Cell.getMaxWidth() không có ở v157 -> đọc field maxWidth bằng reflection (đọc không được thì trả 0 = chưa đặt). */
    private static float cellMaxWidth(Cell c) {
        try {
            if (!cellMaxWTried) {
                cellMaxWTried = true;
                try {
                    cellMaxWField = Cell.class.getDeclaredField("maxWidth");
                    cellMaxWField.setAccessible(true);
                } catch (Throwable ignored) { cellMaxWField = null; }
            }
            java.lang.reflect.Field f = cellMaxWField;
            if (f == null) return 0f;
            Object v = f.get(c);
            if (v instanceof Number) { float w = ((Number) v).floatValue(); return (Float.isNaN(w) || Float.isInfinite(w)) ? 0f : w; }
        } catch (Throwable ignored) {}
        return 0f;
    }

    private static void wrapLabel(Label l) {
        if (!fitted.add(l)) return;
        l.setWrap(true);
        if (labelAlign(l) == Align.center) l.setAlignment(Align.left);
    }

    /** Ép mọi Label bên trong tự động xuống dòng và giới hạn chiều rộng để không tràn khỏi khung. */
    private static void fitRecursive(Element el, float maxW) {
        if (el == null) return;
        if (el instanceof Label) {
            wrapLabel((Label) el);
        } else if (el instanceof Table) {
            Table t = (Table) el;
            Seq<Cell> cells = t.getCells();
            for (int i = 0; i < cells.size; i++) {
                Cell c = cells.get(i);
                Element child = c.get();
                if (child instanceof Label) {
                    wrapLabel((Label) child);
                    if (cellMaxWidth(c) <= 0f) c.maxWidth(maxW);
                }
                fitRecursive(child, maxW);
            }
        } else if (el instanceof Group) {
            Seq<Element> ch = ((Group) el).getChildren();
            for (int i = 0; i < ch.size; i++) fitRecursive(ch.get(i), maxW);
        }
    }

    private static void restyle(final BaseDialog d, final State st) {
        Panel pnl = st.panel();
        final Element oldShell = pnl == null ? null : pnl.shell;
        final Seq<Element> originals = new Seq<>();
        Seq<Cell> cells = d.cont.getCells();
        for (int i = 0; i < cells.size; i++) {
            Element el = cells.get(i).get();
            if (el == null || el == oldShell || isSpacer(el)) continue;
            originals.add(el);
        }
        if (originals.isEmpty()) return;

        // Bảng nút gốc chỉ có 1 nút (thường là "Quay lại") thì bỏ vì đã có nút X; nhiều nút hơn thì giữ lại làm chân trang.
        final boolean keepButtons = !st.buttonsRemoved && d.buttons != null && countButtons(d.buttons) > 1;
        final float btnH = keepButtons ? Math.max(46f, d.buttons.getPrefHeight()) : 0f;
        st.buttonsRemoved = !keepButtons;

        if (pnl == null) {
            final Panel p = new Panel(d, titleOf(d), false);
            pnl = p;
            st.panelRef = new WeakReference<>(p);
            p.scrollable(false).maxWidth(1180f);
            p.tabs(new String[]{T("NÂNG CẤP", "UPGRADE"), T("KHO LÕI", "CORE")}, 0, null);
            p.onBuild(() -> buildAutoPages(p));
        }

        // phân loại nội dung gốc: trái = yêu cầu/thông tin, phải = thao tác (có nút)
        pnl.autoLeft.clear();
        pnl.autoRight.clear();
        for (Element el : originals) el.remove();
        d.cont.clear();
        for (Element el : originals) {
            if (el instanceof ScrollPane) {
                Element w = ((ScrollPane) el).getWidget();
                if (w != null) {
                    w.remove();
                    pnl.autoRight.add(w);
                }
            } else if (hasButton(el)) {
                pnl.autoRight.add(el);
            } else {
                pnl.autoLeft.add(el);
            }
        }

        pnl.keepButtons = keepButtons;
        pnl.extraH = btnH;
        pnl.relayout();
    }

    /** Điền trang hiện tại của hộp thoại được bọc tự động: trang 1 = NÂNG CẤP (2 cột thẻ), trang 2 = KHO LÕI. */
    private static void buildAutoPages(Panel p) {
        final float pw = p.pageW(), ph = p.pageH();
        final float gap = 6f, stripH = 30f;
        Table c = p.content();
        c.clearChildren();

        if (p.tab() == 0) {
            final boolean hasLeft = p.autoLeft.size > 0;
            final float colW = hasLeft ? (pw - gap) / 2f : pw;
            final float colH = Math.max(100f, ph - stripH - gap);
            final float cw = Math.max(150f, colW - 12f);   // trừ lề cột
            final float ew = Math.max(130f, cw - 12f);     // trừ lề thẻ

            Table page = new Table();
            page.top().left();
            page.add(coreStrip(stripH)).size(pw, stripH).padBottom(gap).row();

            Table cols = new Table();
            cols.top().left();
            if (hasLeft) {
                Table lc = column(T("YÊU CẦU", "REQUIREMENTS"), AMBER, cardStack(p.autoLeft, AMBER, ew, cw), false);
                cols.add(lc).size(colW, colH).padRight(gap);
            }
            Table rc = column(T("THAO TÁC", "ACTIONS"), Pal.accent, cardStack(p.autoRight, Pal.accent, ew, cw), false);
            cols.add(rc).size(colW, colH);
            page.add(cols).left().top();
            c.add(page).size(pw, ph).top().left();
        } else {
            Table page = new Table();
            page.top().left();
            page.add(column(T("KHO LÕI", "CORE STORAGE"), Pal.accent, coreView(false, pw - 12f), false)).size(pw, ph);
            c.add(page).size(pw, ph).top().left();
        }
    }

    // ------------------------------------------------------------------ thành phần giao diện

    /** Kéo thả hộp thoại theo mọi hướng bằng cách giữ & kéo vùng tiêu đề (chừa lại 48px để không mất hộp thoại). */
    private static void dragHandle(Element handle, final Panel p) {
        final BaseDialog d = p.dialog;
        handle.addListener(new InputListener() {
            float lx, ly;
            @Override
            public boolean touchDown(InputEvent e, float x, float y, int pointer, KeyCode button) {
                Vec2 v = e.listenerActor.localToStageCoordinates(Tmp.v1.set(x, y));
                lx = v.x; ly = v.y;
                return true;
            }
            @Override
            public void touchDragged(InputEvent e, float x, float y, int pointer) {
                Vec2 v = e.listenerActor.localToStageCoordinates(Tmp.v1.set(x, y));
                float m = 48f;
                // hộp thoại scale quanh tâm: tính theo khung nhìn thực tế (đã scale)
                float s = d.scaleX;
                float ox = d.getWidth() * (1f - s) / 2f, oy = d.getHeight() * (1f - s) / 2f;
                float vw = d.getWidth() * s, vh = d.getHeight() * s;
                float nx = d.x + v.x - lx, ny = d.y + v.y - ly;
                float vx = Math.max(m - vw, Math.min(Core.scene.getWidth() - m, nx + ox));
                float vy = Math.max(m - vh, Math.min(Core.scene.getHeight() - m, ny + oy));
                d.setPosition(vx - ox, vy - oy);
                p.moved = true;
                lx = v.x; ly = v.y;
            }
        });
    }

    /** Khung có kích thước cố định: cha luôn thấy đúng w x h, bất kể nội dung bên trong dài/rộng cỡ nào. */
    private static class Fixed extends Table {
        private final float fw, fh;

        Fixed(Drawable bg, float fw, float fh) {
            super(bg);
            this.fw = fw;
            this.fh = fh;
        }

        @Override public float getPrefWidth() { return fw; }
        @Override public float getPrefHeight() { return fh; }
        @Override public float getMinWidth() { return fw; }
        @Override public float getMinHeight() { return fh; }
    }

    /**
     * Tự động cho xuống dòng mọi Label tràn ra khỏi chiều rộng khung MachineUi (kể cả trong Table con & TextButton).
     */
    private static void wrapOverflowLabels(Element el, float maxW) {
        if (el == null || maxW <= 40f) return;
        if (el instanceof Label) {
            Label l = (Label) el;
            if (l.getText().length() > 16 && l.getPrefWidth() > maxW - 8f) {
                l.setWrap(true);
            }
            return;
        }
        if (el instanceof TextButton) {
            TextButton tb = (TextButton) el;
            Label l = tb.getLabel();
            if (l != null && l.getText().length() > 16) {
                float limit = tb.getWidth() > 20f ? Math.min(maxW, tb.getWidth()) : maxW;
                if (l.getPrefWidth() > limit - 8f) l.setWrap(true);
            }
            return;
        }
        if (el instanceof Table) {
            Table t = (Table) el;
            for (Cell<?> cell : t.getCells()) {
                Element ch = cell.get();
                if (ch == null) continue;
                float cw = maxW - 8f;
                if (ch instanceof Label) {
                    Label l = (Label) ch;
                    if (l.getText().length() > 16 && l.getPrefWidth() > cw) {
                        l.setWrap(true);
                        cell.maxWidth(Math.max(80f, cw));
                    }
                } else {
                    wrapOverflowLabels(ch, Math.max(80f, cw));
                }
            }
            return;
        }
        if (el instanceof Group) {
            for (Element ch : ((Group) el).getChildren()) {
                wrapOverflowLabels(ch, Math.max(80f, maxW - 8f));
            }
        }
    }

    /**
     * Khung chính MachineUi:
     * - Nền mờ (Blur/dim) + nền bán trong suốt tùy chỉnh (%)
     * - Viền khung đổi màu theo tông màu + hiệu ứng phát sáng Bloom nhẹ không flush GPU (chạy mượt 60 FPS kể cả OpenGL 2 Intel!)
     * - Animation mở UI: từ chấm tâm -> vẽ 2 đường thẳng lên/xuống cùng lúc -> tách sang 2 bên trái/phải mở khung UI!
     */
    private static final class ShellFrame extends Fixed {
        private final Panel panel;
        private final Vec2 vBl = new Vec2(), vTr = new Vec2();

        ShellFrame(Panel panel, float fw, float fh) {
            super(null, fw, fh);
            this.panel = panel;
        }

        private void drawBackdropBlur(float blur) {
            if (blur <= 0.01f) return;
            float pad = 12f + 24f * blur;
            Draw.color(0.02f, 0.03f, 0.05f, blur * 0.42f);
            Fill.crect(x - pad, y - pad, width + pad * 2f, height + pad * 2f);
            Draw.reset();
        }

        private void drawBorderAndBloom(float rx, float ry, float rw, float rh, Color ac, float bloom) {
            // Dùng alpha-blend ngay trong cùng SpriteBatch (KHÔNG Draw.flush / đổi blend state mỗi frame -> hết lag OpenGL 2!)
            if (bloom > 0.01f) {
                for (int i = 1; i <= 2; i++) {
                    float exp = i * (2.5f + 2.5f * bloom);
                    float a = bloom * (0.28f / i);
                    Lines.stroke(2.0f + i * 2.4f);
                    Draw.color(ac, a);
                    Lines.rect(rx - exp, ry - exp, rw + exp * 2f, rh + exp * 2f);
                }
            }
            // Viền khung sắc nét + 4 góc đậm kiểu máy móc
            Lines.stroke(2.0f);
            Draw.color(ac, 0.90f);
            Lines.rect(rx, ry, rw, rh);
            float cl = Math.min(18f, Math.min(rw, rh) * 0.22f);
            Lines.stroke(3.0f);
            Draw.color(Color.white, 0.55f);
            Lines.line(rx, ry + rh, rx + cl, ry + rh);
            Lines.line(rx, ry + rh, rx, ry + rh - cl);
            Lines.line(rx + rw, ry + rh, rx + rw - cl, ry + rh);
            Lines.line(rx + rw, ry + rh, rx + rw, ry + rh - cl);
            Lines.line(rx, ry, rx + cl, ry);
            Lines.line(rx, ry, rx, ry + cl);
            Lines.line(rx + rw, ry, rx + rw - cl, ry);
            Lines.line(rx + rw, ry, rx + rw, ry + cl);
            Draw.reset();
        }

        @Override
        public void draw() {
            if (panel.isCoveredByDialog()) return;
            Color ac = accentColor();
            float alpha = bgAlpha();
            float blur = blurAmt();
            float bloom = bloomAmt();

            if (panel.opening) {
                long dt = Math.max(0L, Time.millis() - panel.openT0);
                float cx = x + width * 0.5f;
                float cy = y + height * 0.5f;

                if (dt < OPEN_P1_MS) {
                    // Pha 1: từ 1 chấm nhỏ ở tâm -> vẽ 2 đường thẳng lên trên và xuống dưới cùng lúc
                    float t = clamp(dt / (float) OPEN_P1_MS, 0f, 1f);
                    float ease = 1f - (1f - t) * (1f - t);
                    float halfH = height * 0.5f * ease;

                    if (bloom > 0.01f) {
                        Lines.stroke(6f + 6f * bloom);
                        Draw.color(ac, 0.32f * bloom);
                        Lines.line(cx, cy, cx, cy + halfH);
                        Lines.line(cx, cy, cx, cy - halfH);
                        Fill.circle(cx, cy, 7f + 4f * bloom);
                    }
                    Lines.stroke(2.6f);
                    Draw.color(ac, 0.98f);
                    Lines.line(cx, cy, cx, cy + halfH);
                    Lines.line(cx, cy, cx, cy - halfH);
                    Lines.stroke(1.2f);
                    Draw.color(Color.white, 0.95f);
                    Lines.line(cx, cy - halfH, cx, cy + halfH);
                    Draw.color(Color.white, 1f);
                    Fill.circle(cx, cy, 3.6f * (1f - 0.35f * t));
                    Fill.circle(cx, cy + halfH, 2.8f);
                    Fill.circle(cx, cy - halfH, 2.8f);
                    Draw.reset();
                    return;
                } else if (dt < OPEN_TOTAL_MS) {
                    // Pha 2: 2 đường thẳng tách mở sang 2 bên trái & phải -> lộ dần toàn bộ giao diện MachineUi bên trong!
                    float t = clamp((dt - OPEN_P1_MS) / (float) OPEN_P2_MS, 0f, 1f);
                    float ease = 1f - (float) Math.pow(1f - t, 3);
                    float halfW = width * 0.5f * ease;
                    float rx = cx - halfW;
                    float rw = halfW * 2f;

                    drawBackdropBlur(blur * ease);

                    Draw.color(PANEL.r, PANEL.g, PANEL.b, alpha);
                    Fill.crect(rx, y, rw, height);
                    Draw.reset();

                    if (rw > 4f && Core.scene != null) {
                        vBl.set(rx, y);
                        vTr.set(rx + rw, y + height);
                        localToStageCoordinates(vBl);
                        localToStageCoordinates(vTr);
                        float sx = Core.graphics.getWidth() / Math.max(1f, Core.scene.getWidth());
                        float sy = Core.graphics.getHeight() / Math.max(1f, Core.scene.getHeight());
                        int scX = Math.max(0, Math.round(Math.min(vBl.x, vTr.x) * sx));
                        int scY = Math.max(0, Math.round(Math.min(vBl.y, vTr.y) * sy));
                        int scW = Math.max(1, Math.round(Math.abs(vTr.x - vBl.x) * sx));
                        int scH = Math.max(1, Math.round(Math.abs(vTr.y - vBl.y) * sy));
                        Draw.flush();
                        Gl.enable(GL20.GL_SCISSOR_TEST);
                        Gl.scissor(scX, scY, scW, scH);
                        super.draw();
                        Draw.flush();
                        Gl.disable(GL20.GL_SCISSOR_TEST);
                    }

                    drawBorderAndBloom(rx, y, rw, height, ac, Math.max(0.45f, bloom));
                    Lines.stroke(2.8f);
                    Draw.color(Color.white, 0.85f * (1f - t * 0.45f));
                    Lines.line(rx, y, rx, y + height);
                    Lines.line(rx + rw, y, rx + rw, y + height);
                    Draw.reset();
                    return;
                } else {
                    panel.opening = false;
                }
            }

            drawBackdropBlur(blur);
            Draw.color(PANEL.r, PANEL.g, PANEL.b, alpha);
            Fill.crect(x, y, width, height);
            Draw.reset();
            super.draw();
            drawBorderAndBloom(x, y, width, height, ac, bloom);
        }
    }

    /** Thẻ nền tối, viền mảnh mờ + 4 góc đậm kiểu khung máy. */
    private static class Card extends Table {
        private final Color edge;

        private Element target;
        private float fitW;
        private int frame;

        Card(Color edge) {
            super(Styles.black6);
            this.edge = edge;
            setClip(true);
        }

        /** Mod có thể thêm Label mới sau khi mở: cứ ~20 khung hình ép wrap lại một lần. */
        void keepFit(Element el, float w) {
            target = el;
            fitW = w;
            update(() -> { if (++frame % 20 == 0) fitRecursive(target, fitW); });
        }

        @Override
        public void draw() {
            super.draw();
            float x0 = x + 1f, y0 = y + 1f, x1 = x + width - 1f, y1 = y + height - 1f;
            float l = Math.min(12f, Math.min(width, height) / 3f);

            Draw.color(edge.r, edge.g, edge.b, 0.22f);
            Lines.stroke(1f);
            Lines.rect(x0, y0, x1 - x0, y1 - y0);

            Draw.color(edge);
            Lines.stroke(2f);
            Lines.line(x0, y0, x0 + l, y0); Lines.line(x0, y0, x0, y0 + l);
            Lines.line(x1, y0, x1 - l, y0); Lines.line(x1, y0, x1, y0 + l);
            Lines.line(x0, y1, x0 + l, y1); Lines.line(x0, y1, x0, y1 - l);
            Lines.line(x1, y1, x1 - l, y1); Lines.line(x1, y1, x1, y1 - l);
            Draw.reset();
        }
    }

    /** Mỗi phần tử gốc được bọc trong một thẻ Card, xếp dọc. */
    private static Table cardStack(Seq<Element> els, Color edge, float ew, float cw) {
        Table t = new Table();
        t.top().left();
        for (Element el : els) {
            el.remove();
            fitRecursive(el, ew);
            Card c = new Card(edge);
            c.margin(6f);
            c.add(el).width(ew).left();
            c.keepFit(el, ew);
            t.add(c).width(cw).left().padBottom(6f).row();
        }
        return t;
    }

    /** Cột nền tối: tiêu đề mục + vùng cuộn tự do cả dọc lẫn ngang (kéo theo mọi hướng, lăn chuột trên PC, có nảy ở mép). */
    private static Table column(String title, Color accent, Table content, boolean scrollX) {
        Table col = new Table(Styles.black8);
        col.margin(6f);
        col.top();
        col.setClip(true);
        col.add(sectionHeader(title, accent)).growX().padBottom(5f).row();

        final ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
        sp.setScrollingDisabled(!scrollX, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(scrollX, true);
        sp.setFadeScrollBars(true);
        sp.setClip(true);
        // PC: rê chuột vào là lăn chuột cuộn được ngay, không cần bấm
        sp.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Element fromActor) {
                if (pointer == -1 && Core.scene != null) Core.scene.setScrollFocus(sp);
            }
        });
        col.add(sp).grow();
        return col;
    }

    private static Table sectionHeader(String text, Color c) {
        Table t = new Table();
        t.image(Tex.whiteui, c).size(3.5f, 14f).padRight(5f);
        Label l = t.add(text).left().growX().get();
        l.setColor(c);
        l.setFontScale(0.85f);
        t.row();
        t.image(Tex.whiteui, new Color(c).a(0.5f)).colspan(2).growX().height(1.5f).padTop(2f);
        return t;
    }

    /** Tab phẳng: chữ + gạch chân sáng khi được chọn. Bấm đi qua Panel.selectTab nên có hiệu ứng trượt. */
    private static Table tabButton(String text, final int idx, final Panel p, boolean fill) {
        final Table t = new Table();
        t.touchable = Touchable.enabled;
        float padX = fill ? 4f : 12f;
        final Label l = t.add(text).grow().padLeft(padX).padRight(padX).get();
        l.setFontScale(0.82f);
        l.setAlignment(Align.center);
        // tab trên thanh tiêu đề (fill = false) có bề rộng = chữ, nên không cắt "..."; tab chia đều (fill) mới cần cắt
        l.setEllipsis(fill);
        t.row();
        final Image bar = t.image(Tex.whiteui).growX().height(3f).get();
        t.update(() -> {
            boolean on = p.tab == idx;
            l.setColor(on ? Color.white : Color.gray);
            bar.setColor(on ? accentColor() : Color.clear);
        });
        t.clicked(() -> p.selectTab(idx));
        return t;
    }

    /** Tab lớn: tên đậm + chú thích nhỏ; tab đang chọn tô vàng, tab chưa chọn có nền sáng viền rõ, tab có huy hiệu nhấp nháy. */
    private static Table bigTabButton(final int idx, final Panel p) {
        final Table t = new Table(Tex.whiteui);
        t.touchable = Touchable.enabled;
        final Label l = new Label(p.tabNames[idx]);
        l.setFontScale(Vars.mobile ? 1.1f : 1f);
        l.setAlignment(Align.center);
        l.setEllipsis(true);
        final Label badge = new Label("");
        badge.setFontScale(0.85f);
        Table top = new Table();
        top.add(l).growX().minWidth(0f);
        top.add(badge).padLeft(4f);
        t.add(top).growX().minWidth(0f).row();
        final Label cl = new Label(p.tabCaps != null && idx < p.tabCaps.length ? p.tabCaps[idx] : "");
        cl.setFontScale(0.62f);
        cl.setAlignment(Align.center);
        cl.setEllipsis(true);
        t.add(cl).growX().minWidth(0f);
        final Color tmp = new Color();
        t.update(() -> {
            boolean on = p.tab == idx;
            String b = p.tabBadge != null ? p.tabBadge.apply(idx) : "";
            boolean hasBadge = b != null && b.length() > 0;
            badge.setText(hasBadge ? b : "");
            float pulse = 0.5f + 0.5f * (float) Math.sin(Time.time * 0.12f);
            Color ac = accentColor();
            if (on) tmp.set(ac);
            else if (hasBadge) tmp.set(PANEL).lerp(ac, 0.2f + 0.25f * pulse);
            else tmp.set(PANEL).lerp(STEEL, 0.28f);
            t.setColor(tmp);
            l.setColor(on ? Color.black : Color.white);
            cl.setColor(on ? Color.darkGray : Color.lightGray);
        });
        t.clicked(() -> p.selectTab(idx));
        return t;
    }

    /** Dải tài nguyên lõi nằm ngang (vuốt ngang để xem hết). */
    private static Table coreStrip(float h) {
        Table strip = new Table(Styles.black6);
        strip.margin(2f);
        strip.image(Tex.whiteui, Pal.accent).size(3f, 16f).padLeft(4f).padRight(5f);
        Label l = strip.add(T("LÕI", "CORE")).left().get();
        l.setColor(Pal.accent);
        l.setFontScale(0.8f);

        ScrollPane sp = new ScrollPane(coreView(true, 0f), Styles.noBarPane);
        sp.setScrollingDisabled(false, true);
        sp.setFlickScroll(true);
        sp.setOverscroll(true, false);
        strip.add(sp).growX().height(h - 4f).padLeft(6f);
        return strip;
    }

    private static String fmt(int n) {
        if (n >= 1000000) return (Math.round(n / 100000f) / 10f) + "M";
        if (n >= 10000) return (Math.round(n / 100f) / 10f) + "k";
        return String.valueOf(n);
    }

    /**
     * Hiển thị tài nguyên lõi, tự dựng lại khi số lượng đổi.
     * strip = true: một hàng chip gọn; strip = false: lưới có tên vật phẩm.
     */
    private static Table coreView(final boolean strip, final float width) {
        final Table root = new Table();
        if (strip) root.left(); else root.left().top();

        final int[] sig = {Integer.MIN_VALUE};
        root.update(() -> {
            CoreBuild core = Vars.player != null ? Vars.player.team().core() : null;
            Seq<Item> items = Vars.content.items();
            int s = 0;
            if (core != null) {
                for (int i = 0; i < items.size; i++) {
                    Item it = items.get(i);
                    if (!it.hidden) s = s * 31 + core.items.get(it) + i;
                }
            } else {
                s = -1;
            }
            if (s == sig[0]) return;
            sig[0] = s;

            root.clear();
            if (strip) root.left(); else root.left().top();

            if (core == null) {
                root.add(T("Không thấy lõi đội!", "No team core found!")).color(Color.scarlet).left();
                return;
            }

            int cols = Math.max(2, (int) (width / 118f));
            int n = 0;
            for (int i = 0; i < items.size; i++) {
                Item it = items.get(i);
                if (it.hidden || !core.items.has(it)) continue;
                int amount = core.items.get(it);

                if (strip) {
                    Table chip = new Table(Styles.black8);
                    chip.image(it.uiIcon).size(16f).pad(3f);
                    Label a = chip.add(fmt(amount)).color(Color.white).padRight(5f).get();
                    a.setFontScale(0.78f);
                    root.add(chip).padRight(4f);
                    n++;
                } else {
                    Table cell = new Table(Styles.black8);
                    cell.image(Tex.whiteui, STEEL).width(2f).growY().padRight(3f);
                    cell.image(it.uiIcon).size(18f).pad(2f);
                    Label nameLbl = cell.add(it.localizedName).color(Color.lightGray).left().growX().padLeft(3f).get();
                    nameLbl.setFontScale(0.75f);
                    nameLbl.setEllipsis(true);
                    Label amtLbl = cell.add(String.valueOf(amount)).color(Pal.accent).right().padRight(4f).get();
                    amtLbl.setFontScale(0.78f);
                    root.add(cell).growX().pad(1.5f).uniformX();
                    if (++n % cols == 0) root.row();
                }
            }
            if (n == 0) root.add(T("Trống", "Empty")).color(Color.lightGray).left();
        });
        return root;
    }
}
