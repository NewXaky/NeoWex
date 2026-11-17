package newex;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
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
 * Giao diện "máy móc" cho các hộp thoại của newex, chạy trên cả mobile (ngang) lẫn PC.
 * Bố cục: thanh trên (tiêu đề kéo được + tab gạch chân + nút đóng) -> dải tài nguyên lõi -> 2 cột thẻ viền góc.
 * Tab 1 "NÂNG CẤP": dải tài nguyên + cột Yêu cầu | cột Thao tác. Tab 2 "KHO LÕI": lưới tài nguyên đầy đủ.
 *
 * Hộp thoại nào được áp dụng:
 *  1) Hộp thoại cấu hình của khối "newex-*" đang được chọn (như cũ).
 *  2) Mọi trang của newmode-pvp: tự nhận khi bản đồ có tên chứa "pvpnewex", hoặc khi gọi setPvpMode(true).
 *  3) Hộp thoại được đăng ký tay bằng track(d) / dialog(title). Muốn loại một hộp thoại ra thì gọi skip(d).
 *
 * Trang nào bị mod xóa cont rồi dựng lại (ví dụ sau khi mua/nâng cấp) sẽ được tự dựng lại giao diện, giữ nguyên tab đang xem.
 *
 * Mobile: bố cục dựng theo chiều cao chuẩn REF_H rồi phóng/thu cả hộp thoại cho vừa màn hình (chữ luôn vừa mắt).
 * PC: bố cục dùng kích thước thật (không phóng to), chỉ tự thu nhỏ khi cửa sổ quá bé.
 */
public final class MachineUi {
    private MachineUi() {}

    private static final Map<BaseDialog, State> states = new WeakHashMap<>();
    private static final Set<BaseDialog> tracked = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<BaseDialog> skipped = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Color STEEL = Color.valueOf("8a97a8"), AMBER = Color.valueOf("ffb347"), PANEL = Color.valueOf("12161c");

    /** Chiều cao chuẩn của bố cục trên mobile (đơn vị ảo). Nhỏ hơn -> chữ to hơn; lớn hơn -> chữ nhỏ hơn. */
    private static final float REF_H = 520f;
    /** Hộp thoại chiếm tối đa tỉ lệ này của màn hình (cả chiều ngang lẫn dọc). */
    private static final float FIT_LIMIT = 0.97f;
    /** Giới hạn phóng/thu để không méo giao diện trên màn hình quá lạ. */
    private static final float MIN_SCALE = 0.4f, MAX_SCALE = 4f;

    private static boolean pvpFlag = false;
    private static Object lastMap;
    private static boolean lastMapPvp;

    /** Trạng thái theo từng hộp thoại. shell giữ bằng WeakReference để WeakHashMap không giữ hộp thoại mãi. */
    private static final class State {
        WeakReference<Element> shellRef;
        float extraH;
        float maxScale = MAX_SCALE;
        boolean styled, failed, buttonsRemoved;
        int tab;

        Element shell() { return shellRef == null ? null : shellRef.get(); }
    }

    public static void init() {
        Events.run(Trigger.update, MachineUi::tick);
    }

    // ------------------------------------------------------------------ API công khai

    /** Bật/tắt chế độ PvP thủ công: bật thì mọi trang của mod (BaseDialog) đều được áp dụng giao diện này. */
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

    /** Đăng ký một hộp thoại có sẵn để luôn dùng giao diện này (không phụ thuộc bản đồ/khối đang chọn). */
    public static void track(BaseDialog d) {
        if (d != null) tracked.add(d);
    }

    /** Tạo BaseDialog mới và đăng ký luôn. Dùng thay cho new BaseDialog(title). */
    public static BaseDialog dialog(String title) {
        BaseDialog d = new BaseDialog(title);
        track(d);
        return d;
    }

    /** Loại một hộp thoại khỏi giao diện này (giữ nguyên giao diện gốc). */
    public static void skip(BaseDialog d) {
        if (d != null) skipped.add(d);
    }

    static boolean isEn() { return Core.settings.getString("locale", "vi").startsWith("en"); }
    static String T(String vi, String en) { return isEn() ? en : vi; }

    /** Mobile: chỉ khi màn hình ngang. PC: luôn bật. */
    public static boolean active() {
        return !Vars.mobile || !Core.graphics.isPortrait();
    }

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
        if (Vars.headless || Core.scene == null || !Vars.state.isGame() || !active()) return;
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
                Element shell = st.shell();
                if (shell == null || hasForeign(d, shell)) restyle(d, st);
                applyFit(d, st);
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

    // ------------------------------------------------------------------ dựng lại hộp thoại

    private static void restyle(final BaseDialog d, final State st) {
        final Element oldShell = st.shell();
        final Seq<Element> originals = new Seq<>();
        Seq<Cell> cells = d.cont.getCells();
        for (int i = 0; i < cells.size; i++) {
            Element el = cells.get(i).get();
            if (el == null || el == oldShell || isSpacer(el)) continue;
            originals.add(el);
        }
        if (originals.isEmpty()) return;

        final String title = titleOf(d);
        // Bảng nút gốc chỉ có 1 nút (thường là "Quay lại") thì bỏ vì đã có nút X; nhiều nút hơn thì giữ lại làm chân trang.
        final boolean keepButtons = !st.buttonsRemoved && d.buttons != null && countButtons(d.buttons) > 1;

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
        st.buttonsRemoved = !keepButtons;

        // ---- kích thước bố cục
        final float sceneW = Core.scene.getWidth();
        final float sceneH = Core.scene.getHeight();
        final boolean scaled = Vars.mobile;
        final float W, H;
        if (scaled) {
            // mobile: dựng theo chiều cao chuẩn, rộng theo tỉ lệ khung hình, rồi phóng/thu bằng applyFit
            H = REF_H;
            W = Math.max(REF_H * 1.4f, Math.min(REF_H * 3f, REF_H * sceneW / sceneH));
        } else {
            // PC: kích thước thật, không phóng to
            H = Math.max(300f, Math.min(sceneH * 0.9f, 780f));
            W = Math.max(480f, Math.min(sceneW * 0.85f, 1180f));
        }
        st.maxScale = scaled ? MAX_SCALE : 1f;

        final float btnH = keepButtons ? Math.max(46f, d.buttons.getPrefHeight()) : 0f;
        final float topH = 38f, line = 2.5f, pad = 6f, gap = 6f, stripH = 30f;
        final float shellH = H - btnH;
        final float areaH = shellH - topH - line;
        final float pw = W - pad * 2f;                 // bề rộng trang
        final float ph = areaH - pad * 2f;             // chiều cao trang

        // ---- phân loại nội dung gốc: trái = yêu cầu/thông tin, phải = thao tác (có nút)
        final Seq<Element> leftEls = new Seq<>(), rightEls = new Seq<>();
        for (Element el : originals) el.remove();
        d.cont.clear();
        for (Element el : originals) {
            if (el instanceof ScrollPane) {
                Element w = ((ScrollPane) el).getWidget();
                if (w != null) {
                    w.remove();
                    rightEls.add(w);
                }
            } else if (hasButton(el)) {
                rightEls.add(el);
            } else {
                leftEls.add(el);
            }
        }
        final boolean hasLeft = leftEls.size > 0;

        final float colW = hasLeft ? (pw - gap) / 2f : pw;
        final float colH = Math.max(100f, ph - stripH - gap);
        final float cw = Math.max(150f, colW - 12f);   // trừ lề cột
        final float ew = Math.max(130f, cw - 12f);     // trừ lề thẻ

        // ---- trang 1: NÂNG CẤP
        final Table page1 = new Table();
        page1.top().left();
        page1.add(coreStrip(stripH)).size(pw, stripH).padBottom(gap).row();

        Table cols = new Table();
        cols.top().left();
        if (hasLeft) {
            Table lc = column(T("YÊU CẦU", "REQUIREMENTS"), AMBER, cardStack(leftEls, AMBER, ew, cw));
            cols.add(lc).size(colW, colH).padRight(gap);
        }
        Table rc = column(T("THAO TÁC", "ACTIONS"), Pal.accent, cardStack(rightEls, Pal.accent, ew, cw));
        cols.add(rc).size(colW, colH);
        page1.add(cols).left().top();

        // ---- trang 2: KHO LÕI
        final Table page2 = new Table();
        page2.top().left();
        page2.add(column(T("KHO LÕI", "CORE STORAGE"), Pal.accent, coreView(false, pw - 12f))).size(pw, ph);

        final Table[] pages = {page1, page2};

        // ---- thân + chuyển tab (nhớ tab đang xem qua các lần dựng lại)
        final Table body = new Table();
        body.margin(pad);
        body.top().left();
        final int[] cur = {Math.max(0, Math.min(pages.length - 1, st.tab))};
        final Runnable pick = () -> {
            st.tab = cur[0];
            body.clearChildren();
            body.add(pages[cur[0]]).size(pw, ph).top().left();
        };

        // ---- thanh trên
        Table top = new Table();
        top.setBackground(Tex.whiteui);
        top.setColor(new Color(PANEL).lerp(AMBER, 0.14f));

        Table zone = new Table();            // vùng giữ & kéo để di chuyển hộp thoại
        zone.touchable = Touchable.enabled;
        final Image led = zone.image(Tex.whiteui).size(8f).color(AMBER).padLeft(8f).padRight(7f).get();
        led.update(() -> led.setColor(AMBER.r, AMBER.g, AMBER.b, 0.55f + 0.45f * (float) Math.sin(Time.time * 0.08f)));
        Label tl = zone.add(title.isEmpty() ? "NEWEX" : title).left().growX().get();
        tl.setColor(Color.lightGray);
        tl.setFontScale(0.85f);
        tl.setEllipsis(true);
        dragHandle(zone, d);
        top.add(zone).growX().height(topH);

        Table tabs = new Table();
        tabs.add(tabButton(T("NÂNG CẤP", "UPGRADE"), 0, cur, pick)).growY();
        tabs.add(tabButton(T("KHO LÕI", "CORE"), 1, cur, pick)).growY();
        top.add(tabs).growY().padLeft(6f).padRight(4f);

        top.button(Icon.cancel, Styles.cleari, () -> d.hide()).size(topH);

        // ---- khung ngoài (kích thước cố định, không phụ thuộc nội dung bên trong)
        final Fixed shell = new Fixed(Styles.black8, W, shellH);
        shell.setClip(true);
        shell.add(top).growX().height(topH).row();
        shell.image(Tex.whiteui, AMBER).growX().height(line).row();
        shell.add(body).size(W, areaH);

        pick.run();

        d.cont.clearChildren();
        d.cont.add(shell).size(W, shellH);
        d.pack();
        // lần đầu thì căn giữa; dựng lại sau đó giữ nguyên vị trí người dùng đã kéo
        if (!st.styled) d.setPosition(Math.round((sceneW - d.getWidth()) / 2f), Math.round((sceneH - d.getHeight()) / 2f));

        st.shellRef = new WeakReference<>(shell);
        st.extraH = btnH;
        st.styled = true;
    }

    /**
     * Phóng/thu cả hộp thoại quanh tâm sao cho khung (cộng thêm chân trang nếu có) chiếm tối đa FIT_LIMIT màn hình.
     * Chạy mỗi frame từ tick (không dùng action của hộp thoại vì Dialog.show() xóa hết action).
     */
    private static void applyFit(BaseDialog d, State st) {
        Element shell = st.shell();
        if (shell == null || Core.scene == null) return;
        float nw = shell.getWidth(), nh = shell.getHeight() + st.extraH;
        if (nw <= 0f || nh <= 0f) return;

        float sw = Core.scene.getWidth(), sh = Core.scene.getHeight();
        float k = Math.min(FIT_LIMIT * sw / nw, FIT_LIMIT * sh / nh);
        k = Math.max(MIN_SCALE, Math.min(st.maxScale, k));

        if (Math.abs(d.scaleX - k) > 0.002f || Math.abs(d.scaleY - k) > 0.002f) {
            d.setTransform(true);
            d.setOrigin(Align.center);
            d.setScale(k);
        }
    }

    /** Kéo thả hộp thoại theo mọi hướng bằng cách giữ & kéo vùng tiêu đề (chừa lại 48px để không mất hộp thoại). */
    private static void dragHandle(Element handle, final BaseDialog d) {
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
                lx = v.x; ly = v.y;
            }
        });
    }

    // ------------------------------------------------------------------ thành phần giao diện

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

    /** Tab phẳng: chữ + gạch chân sáng khi được chọn. */
    private static Table tabButton(String text, final int idx, final int[] cur, final Runnable onPick) {
        final Table t = new Table();
        t.touchable = Touchable.enabled;
        final Label l = t.add(text).grow().padLeft(12f).padRight(12f).get();
        l.setFontScale(0.82f);
        t.row();
        final Image bar = t.image(Tex.whiteui).growX().height(3f).get();
        t.update(() -> {
            boolean on = cur[0] == idx;
            l.setColor(on ? Color.white : Color.gray);
            bar.setColor(on ? AMBER : Color.clear);
        });
        t.clicked(() -> {
            if (cur[0] == idx) return;
            cur[0] = idx;
            onPick.run();
        });
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