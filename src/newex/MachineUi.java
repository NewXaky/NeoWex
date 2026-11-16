package newex;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Giao diện "máy móc" cho các hộp thoại nâng cấp (nút ^) và thông tin (nút i) của pháo/khối newex.
 *
 * Chỉ bật khi chơi trên mobile và màn hình đang khóa NGANG. Khi đó mỗi BaseDialog mở ra từ bảng cấu hình của một khối
 * newex được dựng lại thành 2 nửa màn hình:
 *   - nửa trái : kho tài nguyên lõi (số lượng từng loại) + phần "tài nguyên cần để nâng cấp" mà hộp thoại gốc có sẵn;
 *   - nửa phải : phần nâng cấp / nút nâng cấp / roll phúc lợi / thông tin chi tiết.
 * Cả hai nửa đều là ScrollPane kéo thả tự do (cả ngang lẫn dọc) để đọc nội dung dài.
 *
 * Làm theo cách "hậu xử lý" để không phải sửa hơn 30 file pháo: mọi hộp thoại gốc đều có dạng
 * cont = [bảng/nhãn yêu cầu] + ScrollPane(nội dung), nên chỉ cần lấy các phần tử ra và xếp lại.
 */
public final class MachineUi {
    private MachineUi() {}

    private static final Set<BaseDialog> done = Collections.newSetFromMap(new WeakHashMap<BaseDialog, Boolean>());
    private static final Color STEEL = Color.valueOf("8a97a8"), AMBER = Color.valueOf("ffb347"), PANEL = Color.valueOf("12161c");

    public static void init() {
        Events.run(Trigger.update, MachineUi::tick);
    }

    static boolean isEn() { return Core.settings.getString("locale", "vi").startsWith("en"); }
    static String T(String vi, String en) { return isEn() ? en : vi; }

    /** mobile + màn hình ngang */
    public static boolean active() {
        return Vars.mobile && !Core.graphics.isPortrait();
    }

    private static boolean fromNewexConfig() {
        try {
            Building sel = Vars.control.input.config.getSelected();
            return sel != null && sel.block != null && sel.block.name.startsWith("newex-");
        } catch (Throwable ignored) { return false; }
    }

    private static void tick() {
        if (Vars.headless || Core.scene == null || !Vars.state.isGame() || !active() || !fromNewexConfig()) return;
        Seq<Element> kids = Core.scene.root.getChildren();
        for (int i = 0; i < kids.size; i++) {
            Element e = kids.get(i);
            if (e == null || e.getClass() != BaseDialog.class) continue;
            BaseDialog d = (BaseDialog) e;
            if (done.contains(d)) continue;
            done.add(d);
            try { restyle(d); } catch (Throwable err) { arc.util.Log.err("[MachineUi] không dựng lại được hộp thoại, giữ giao diện cũ", err); }
        }
    }

    // ------------------------------------------------------------------ nhận dạng phần tử

    private static boolean hasButton(Element e) {
        if (e instanceof Button) return true;
        if (e instanceof Group) {
            Seq<Element> ch = ((Group) e).getChildren();
            for (int i = 0; i < ch.size; i++) if (hasButton(ch.get(i))) return true;
        }
        return false;
    }

    private static boolean isSpacer(Element e) {
        if (e == null) return true;
        if (e.getClass() == Element.class) return true;
        return e instanceof Table && ((Table) e).getChildren().size == 0;
    }

    // ------------------------------------------------------------------ dựng lại

    private static void restyle(final BaseDialog d) {
        // lấy các phần tử gốc ra khỏi cont
        final Seq<Element> originals = new Seq<>();
        Seq<arc.scene.ui.layout.Cell> cells = d.cont.getCells();
        for (int i = 0; i < cells.size; i++) {
            Element el = cells.get(i).get();
            if (!isSpacer(el)) originals.add(el);
        }
        if (originals.isEmpty()) return;

        final Table leftContent = new Table(), rightContent = new Table();
        leftContent.top().left(); rightContent.top().left();

        final float sceneW = Core.scene.getWidth(), sceneH = Core.scene.getHeight();
        final float half = Math.max(240f, (sceneW - 56f) / 2f), paneW = half - 20f;
        final float totalH = Math.max(200f, sceneH - 150f);

        // tách: ScrollPane + thứ có nút -> phải; còn lại (nhãn/bảng yêu cầu) -> trái
        final Seq<Element> toRemove = new Seq<>(originals);
        for (Element el : toRemove) el.remove();
        d.cont.clear();

        for (Element el : originals) {
            if (el instanceof ScrollPane) {
                Element w = ((ScrollPane) el).getWidget();
                if (w != null) { w.remove(); rightContent.add(w).growX().left().padBottom(6f).row(); }
            } else if (hasButton(el)) {
                if (el instanceof Label) ((Label) el).setWrap(true);
                rightContent.add(el).growX().left().padBottom(6f).row();
            } else {
                if (el instanceof Label) { ((Label) el).setWrap(true); ((Label) el).setAlignment(Align.left); leftContent.add(el).width(paneW).left().padBottom(6f).row(); }
                else leftContent.add(el).left().padBottom(6f).row();
            }
        }

        // ---- nửa trái: kho lõi + yêu cầu
        Table left = new Table();
        left.add(coreStorage(paneW)).growX().left().padBottom(8f).row();
        if (leftContent.hasChildren()) {
            left.add(sectionHeader(T("YÊU CẦU NÂNG CẤP", "UPGRADE REQUIREMENTS"), AMBER)).growX().padBottom(4f).row();
            left.add(leftContent).growX().left().row();
        }

        d.cont.add(machinePanel(T("LÕI // KHO TÀI NGUYÊN", "CORE // STORAGE"), Pal.accent, left, half, totalH)).size(half, totalH).padRight(8f);
        d.cont.add(machinePanel(T("MODULE // NÂNG CẤP & THÔNG TIN", "MODULE // UPGRADE & INFO"), AMBER, rightContent, half, totalH)).size(half, totalH);
    }

    /** Khung kiểu máy: viền kim loại, thanh tiêu đề có đèn báo, nội dung kéo thả được cả 2 chiều. */
    private static Table machinePanel(String title, Color accent, Table content, float w, float h) {
        Table root = new Table();
        root.background(Styles.black8);
        root.margin(0f);

        // thanh tiêu đề
        Table head = new Table();
        head.setBackground(Tex.whiteui);
        head.setColor(new Color(PANEL).lerp(accent, 0.18f));
        final Image led = head.image(Tex.whiteui).size(10f).color(accent).padLeft(8f).padRight(8f).get();
        led.update(() -> led.setColor(accent.r, accent.g, accent.b, 0.55f + 0.45f * (float) Math.sin(Time.time * 0.08f)));
        head.add("[#" + accent.toString() + "]" + title).left().growX().padTop(5f).padBottom(5f);
        root.add(head).growX().row();
        root.image(Tex.whiteui, accent).growX().height(3f).row();

        // thân: kéo thả tự do
        ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
        sp.setScrollingDisabled(false, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(true, true);
        sp.setFadeScrollBars(true);
        Table body = new Table();
        body.margin(8f);
        body.add(sp).grow();
        root.add(body).grow().row();

        // chân: đường vạch kiểu đinh tán
        Table foot = new Table();
        for (int i = 0; i < 6; i++) foot.image(Tex.whiteui).size(5f).color(STEEL).pad(3f);
        root.add(foot).left().padLeft(6f).padBottom(2f).row();
        return root;
    }

    private static Table sectionHeader(String text, Color c) {
        Table t = new Table();
        t.image(Tex.whiteui, c).size(4f, 16f).padRight(6f);
        t.add("[#" + c.toString() + "]" + text).left().growX();
        t.row();
        t.image(Tex.whiteui, new Color(c).a(0.5f)).colspan(2).growX().height(1.5f).padTop(3f);
        return t;
    }

    /** Lưới tài nguyên của lõi đội người chơi, tự làm mới khi số lượng đổi. */
    private static Table coreStorage(final float width) {
        final Table root = new Table();
        root.left().top();
        root.add(sectionHeader(T("TÀI NGUYÊN LÕI", "CORE RESOURCES"), Pal.accent)).growX().padBottom(4f).row();
        final Table grid = new Table();
        grid.left().top();
        root.add(grid).growX().left().row();
        final int[] sig = {Integer.MIN_VALUE};
        grid.update(() -> {
            CoreBuild core = Vars.player != null ? Vars.player.team().core() : null;
            int s = 0;
            if (core != null) {
                Seq<Item> items = Vars.content.items();
                for (int i = 0; i < items.size; i++) { Item it = items.get(i); if (!it.hidden) s = s * 31 + core.items.get(it) + i; }
            } else s = -1;
            if (s == sig[0]) return;
            sig[0] = s;
            grid.clear();
            if (core == null) { grid.add(T("[scarlet]Không thấy lõi đội![]", "[scarlet]No team core found![]")).left(); return; }
            int cols = width > 330f ? 2 : 1, n = 0;
            Seq<Item> items = Vars.content.items();
            for (int i = 0; i < items.size; i++) {
                Item it = items.get(i);
                if (it.hidden || !core.items.has(it)) continue;
                Table cell = new Table();
                cell.background(Styles.black6);
                cell.add(new Image(it.uiIcon)).size(22f).pad(3f);
                cell.add(it.localizedName).color(Color.lightGray).left().growX().padLeft(4f).get().setFontScale(0.8f);
                cell.add("[accent]" + core.items.get(it) + "[]").right().padRight(6f);
                grid.add(cell).growX().pad(2f).uniformX();
                if (++n % cols == 0) grid.row();
            }
        });
        return root;
    }
}
