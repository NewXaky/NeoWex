package newex;

import arc.Core;
import arc.Events;
import arc.func.Intc;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
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
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
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

    /** Chiều cao chuẩn (mobile ngang) / chiều rộng chuẩn (mobile dọc) của bố cục; nhỏ hơn -> chữ to hơn. */
    private static final float REF_H = 520f, REF_W = 400f;
    /** Hộp thoại chiếm tối đa tỉ lệ này của vùng khả dụng (cả ngang lẫn dọc). */
    private static final float FIT_LIMIT = 0.97f;
    /** Giới hạn phóng/thu để không méo giao diện trên màn hình quá lạ. */
    private static final float MIN_SCALE = 0.35f, MAX_SCALE = 4f;
    /** Thời gian trượt trang (ms): thoát / vào. Giữ y như giao diện cũ. */
    public static final int EXIT_MS = 90, ENTER_MS = 170;

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

    public static void init() {
        Events.run(Trigger.update, MachineUi::tick);
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
        private boolean moved;
        private float maxScale = MAX_SCALE;
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
            if (refill != null) refill.run();
            dialog.show();
        }

        public void hide() { dialog.hide(); }

        /** Dựng lại nội dung ngay, không hiệu ứng (giữ nguyên vị trí cuộn). */
        public void rebuild() {
            float keep = scroll != null ? scroll.getScrollY() : 0f;
            if (refill != null) refill.run();
            restoreScroll(keep);
        }

        /**
         * Dựng lại nội dung xóa sạch rồi thêm lại nên chiều cao tụt về 0 và ScrollPane tự kẹp scrollY về 0 (đang lướt xuống bị giật lên đầu).
         * Ép bố cục tính lại ngay rồi đặt lại vị trí cũ, và giữ thêm vài khung hình phòng khi bố cục chậm một nhịp.
         */
        private void restoreScroll(float y) {
            if (scroll == null || y <= 0.5f) return;
            try { scroll.validate(); } catch (Throwable ignored) {}
            scroll.setScrollY(y);
            snapScroll();
            restoreY = y;
            restoreN = 4;
        }

        /** Dựng lại cả khung lẫn nội dung (khi kích thước màn hình đổi). */
        public void relayout() {
            float keep = scroll != null ? scroll.getScrollY() : 0f;
            layout();
            if (refill != null) refill.run();
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

        private void tick() {
            if (Core.scene == null || shell == null) return;
            float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
            if (sw != lastSw || sh != lastSh) {
                moved = false;
                if (exiting) flush();
                relayout();
            }
            fit();
            place();
            if (restoreN > 0 && scroll != null) {
                restoreN--;
                if (Math.abs(scroll.getScrollY() - restoreY) > 0.5f) { scroll.setScrollY(restoreY); snapScroll(); }
            }
            if (ticker != null) ticker.run();
        }

        /** Phóng/thu cả hộp thoại quanh tâm để khung (cộng chân trang nếu có) nằm trọn trong vùng khả dụng. */
        void fit() {
            Element sh = shell;
            if (sh == null || Core.scene == null) return;
            float nw = sh.getWidth(), nh = sh.getHeight() + extraH;
            if (nw <= 0f || nh <= 0f) return;
            float[] a = avail();
            float k = Math.min(FIT_LIMIT * a[0] / nw, FIT_LIMIT * a[1] / nh);
            k = clamp(k, MIN_SCALE, maxScale);
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

        /** Tính kích thước bố cục {rộng, cao} theo hướng và tỉ lệ màn hình, rồi dựng lại khung. */
        private void layout() {
            if (Core.scene == null) return;
            prepareDialog();
            final float[] av = avail();
            lastSw = Core.scene.getWidth();
            lastSh = Core.scene.getHeight();
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
            maxScale = pc ? 1f : MAX_SCALE;
            W = w;
            H = h;

            final float topH = Vars.mobile ? 44f : 38f, line = 2.5f, pad = 6f;
            final boolean tabsInBar = tabNames.length > 0 && W >= 560f;
            final float tabsH = (tabNames.length > 0 && !tabsInBar) ? Vars.mobile ? 40f : 34f : 0f;
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

            Fixed sh = new Fixed(Styles.black8, W, shellH);
            sh.setClip(true);
            sh.add(topBar(topH, tabsInBar)).growX().height(topH).row();
            sh.image(Tex.whiteui, AMBER).growX().height(line).row();
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
            Table top = new Table();
            top.setBackground(Tex.whiteui);
            top.setColor(new Color(PANEL).lerp(AMBER, 0.14f));

            Table zone = new Table();            // vùng giữ & kéo để di chuyển hộp thoại
            zone.touchable = Touchable.enabled;
            final Image led = zone.image(Tex.whiteui).size(8f).color(AMBER).padLeft(8f).padRight(7f).get();
            led.update(() -> led.setColor(AMBER.r, AMBER.g, AMBER.b, 0.55f + 0.45f * (float) Math.sin(Time.time * 0.08f)));
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
                Cell<Table> c = t.add(tabButton(tabNames[i], i, this, fill)).growY();
                if (fill) c.growX().uniformX();
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
    private static int labelAlign(Label l) {
        try {
            java.lang.reflect.Field f = Label.class.getDeclaredField("labelAlign");
            f.setAccessible(true);
            return f.getInt(l);
        } catch (Throwable ignored) { return -1; }
    }

    /** Cell.getMaxWidth() không có ở v157 -> đọc field maxWidth bằng reflection (đọc không được thì trả 0 = chưa đặt). */
    private static float cellMaxWidth(Cell c) {
        try {
            java.lang.reflect.Field f = Cell.class.getDeclaredField("maxWidth");
            f.setAccessible(true);
            Object v = f.get(c);
            if (v instanceof Number) { float w = ((Number) v).floatValue(); return (Float.isNaN(w) || Float.isInfinite(w)) ? 0f : w; }
        } catch (Throwable ignored) {}
        return 0f;
    }

    /** Ép mọi Label bên trong tự động xuống dòng và giới hạn chiều rộng để không tràn khỏi khung. */
    private static void fitRecursive(Element el, float maxW) {
        if (el == null) return;
        if (el instanceof Label) {
            Label l = (Label) el;
            l.setWrap(true);
            if (labelAlign(l) == Align.center) l.setAlignment(Align.left);
        } else if (el instanceof Table) {
            Table t = (Table) el;
            Seq<Cell> cells = t.getCells();
            for (int i = 0; i < cells.size; i++) {
                Cell c = cells.get(i);
                Element child = c.get();
                if (child instanceof Label) {
                    ((Label) child).setWrap(true);
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
                Table lc = column(T("YÊU CẦU", "REQUIREMENTS"), AMBER, cardStack(p.autoLeft, AMBER, ew, cw));
                cols.add(lc).size(colW, colH).padRight(gap);
            }
            Table rc = column(T("THAO TÁC", "ACTIONS"), Pal.accent, cardStack(p.autoRight, Pal.accent, ew, cw));
            cols.add(rc).size(colW, colH);
            page.add(cols).left().top();
            c.add(page).size(pw, ph).top().left();
        } else {
            Table page = new Table();
            page.top().left();
            page.add(column(T("KHO LÕI", "CORE STORAGE"), Pal.accent, coreView(false, pw - 12f))).size(pw, ph);
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

    /** Thẻ nền tối, viền mảnh mờ + 4 góc đậm kiểu khung máy. */
    private static class Card extends Table {
        private final Color edge;

        Card(Color edge) {
            super(Styles.black6);
            this.edge = edge;
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
            t.add(c).width(cw).left().padBottom(6f).row();
        }
        return t;
    }

    /** Cột nền tối: tiêu đề mục + vùng cuộn tự do cả dọc lẫn ngang (kéo theo mọi hướng, lăn chuột trên PC, có nảy ở mép). */
    private static Table column(String title, Color accent, Table content) {
        Table col = new Table(Styles.black8);
        col.margin(6f);
        col.top();
        col.setClip(true);
        col.add(sectionHeader(title, accent)).growX().padBottom(5f).row();

        final ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(true, true);
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
        l.setEllipsis(true);
        t.row();
        final Image bar = t.image(Tex.whiteui).growX().height(3f).get();
        t.update(() -> {
            boolean on = p.tab == idx;
            l.setColor(on ? Color.white : Color.gray);
            bar.setColor(on ? AMBER : Color.clear);
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
