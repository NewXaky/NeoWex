package newex;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.Element;
import arc.scene.Group;
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
 * Giao diện "máy móc" 2 cột cho các hộp thoại của khối newex trên mobile màn hình ngang.
 * Đã tối ưu chống tràn viền ngang/dọc và tự động ép xuống dòng nội dung dài.
 */
public final class MachineUi {
    private MachineUi() {}

    private static final Set<BaseDialog> done = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Color STEEL = Color.valueOf("8a97a8"), AMBER = Color.valueOf("ffb347"), PANEL = Color.valueOf("12161c");

    public static void init() {
        Events.run(Trigger.update, MachineUi::tick);
    }

    static boolean isEn() { return Core.settings.getString("locale", "vi").startsWith("en"); }
    static String T(String vi, String en) { return isEn() ? en : vi; }

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
            try {
                restyle(d);
            } catch (Throwable err) {
                arc.util.Log.err("[MachineUi] Không dựng lại được hộp thoại, giữ giao diện cũ", err);
            }
        }
    }

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
        return e instanceof Table && ((Table) e).getChildren().isEmpty();
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

    private static void restyle(final BaseDialog d) {
        final Seq<Element> originals = new Seq<>();
        Seq<Cell> cells = d.cont.getCells();
        for (int i = 0; i < cells.size; i++) {
            Element el = cells.get(i).get();
            if (!isSpacer(el)) originals.add(el);
        }
        if (originals.isEmpty()) return;

        d.setFillParent(false);
        d.setKeepWithinStage(false);
        d.setMovable(false);
        d.margin(6f);
        d.cont.margin(4f);
        if (d.buttons != null) d.buttons.margin(4f);
        d.pack();

        final float sceneW = Core.scene.getWidth();
        final float sceneH = Core.scene.getHeight();
        final float titleH = d.titleTable != null ? Math.max(34f, d.titleTable.getPrefHeight()) : 38f;
        final float btnH = d.buttons != null ? Math.max(46f, d.buttons.getPrefHeight()) : 52f;

        final float halfW = Math.max(210f, (sceneW * 0.92f - 28f) / 2f);
        final float innerW = Math.max(180f, halfW - 22f);
        final float totalH = Math.max(170f, sceneH * 0.92f - titleH - btnH - 26f);

        final Table leftContent = new Table(), rightContent = new Table();
        leftContent.top().left();
        rightContent.top().left();

        for (Element el : originals) el.remove();
        d.cont.clear();

        for (Element el : originals) {
            if (el instanceof ScrollPane) {
                Element w = ((ScrollPane) el).getWidget();
                if (w != null) {
                    w.remove();
                    fitRecursive(w, innerW);
                    rightContent.add(w).width(innerW).left().padBottom(6f).row();
                }
            } else if (hasButton(el)) {
                fitRecursive(el, innerW);
                rightContent.add(el).width(innerW).left().padBottom(6f).row();
            } else {
                fitRecursive(el, innerW);
                leftContent.add(el).width(innerW).left().padBottom(6f).row();
            }
        }

        Table left = new Table();
        left.top().left();
        left.add(coreStorage(innerW)).width(innerW).left().padBottom(8f).row();
        if (leftContent.hasChildren()) {
            left.add(sectionHeader(T("YÊU CẦU NÂNG CẤP", "UPGRADE REQUIREMENTS"), AMBER)).width(innerW).padBottom(4f).row();
            left.add(leftContent).width(innerW).left().row();
        }

        d.cont.clearChildren();
        d.cont.add(machinePanel(d, T("LÕI // KHO TÀI NGUYÊN", "CORE // STORAGE"), Pal.accent, left, halfW, totalH))
            .size(halfW, totalH).padRight(6f);
        d.cont.add(machinePanel(d, T("MODULE // NÂNG CẤP & THÔNG TIN", "MODULE // UPGRADE & INFO"), AMBER, rightContent, halfW, totalH))
            .size(halfW, totalH);
        if (d.titleTable != null) dragHandle(d.titleTable, d);
        d.pack();
        d.setPosition(Math.round((sceneW - d.getWidth()) / 2f), Math.round((sceneH - d.getHeight()) / 2f));
    }

    /** Kéo thả hộp thoại theo mọi hướng bằng cách giữ & kéo thanh tiêu đề (chừa lại 48px để không mất hộp thoại). */
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
                float nx = d.x + v.x - lx, ny = d.y + v.y - ly;
                nx = Math.max(m - d.getWidth(), Math.min(Core.scene.getWidth() - m, nx));
                ny = Math.max(m - d.getHeight(), Math.min(Core.scene.getHeight() - m, ny));
                d.setPosition(nx, ny);
                lx = v.x; ly = v.y;
            }
        });
    }

    private static Table machinePanel(BaseDialog d, String title, Color accent, Table content, float w, float h) {
        Table root = new Table();
        root.background(Styles.black8);
        root.margin(0f);
        root.setClip(true);

        Table head = new Table();
        head.setBackground(Tex.whiteui);
        head.setColor(new Color(PANEL).lerp(accent, 0.18f));
        final Image led = head.image(Tex.whiteui).size(9f).color(accent).padLeft(7f).padRight(7f).get();
        led.update(() -> led.setColor(accent.r, accent.g, accent.b, 0.55f + 0.45f * (float) Math.sin(Time.time * 0.08f)));
        Label titleLbl = head.add("[#" + accent.toString() + "]" + title).left().growX().padTop(4f).padBottom(4f).get();
        titleLbl.setFontScale(0.88f);
        titleLbl.setEllipsis(true);
        dragHandle(head, d);
        root.add(head).growX().row();
        root.image(Tex.whiteui, accent).growX().height(2.5f).row();

        ScrollPane sp = new ScrollPane(content, Styles.noBarPane);
        sp.setScrollingDisabled(true, false);
        sp.setFlickScroll(true);
        sp.setOverscroll(false, true);
        sp.setFadeScrollBars(true);
        sp.setClip(true);

        Table body = new Table();
        body.margin(6f);
        body.setClip(true);
        body.add(sp).grow();
        root.add(body).grow().row();

        Table foot = new Table();
        for (int i = 0; i < 6; i++) foot.image(Tex.whiteui).size(4f).color(STEEL).pad(2.5f);
        root.add(foot).left().padLeft(6f).padBottom(2f).row();
        return root;
    }

    private static Table sectionHeader(String text, Color c) {
        Table t = new Table();
        t.image(Tex.whiteui, c).size(3.5f, 14f).padRight(5f);
        Label l = t.add("[#" + c.toString() + "]" + text).left().growX().get();
        l.setFontScale(0.85f);
        t.row();
        t.image(Tex.whiteui, new Color(c).a(0.5f)).colspan(2).growX().height(1.5f).padTop(2f);
        return t;
    }

    private static Table coreStorage(final float width) {
        final Table root = new Table();
        root.left().top();
        root.add(sectionHeader(T("TÀI NGUYÊN LÕI", "CORE RESOURCES"), Pal.accent)).width(width).padBottom(4f).row();
        final Table grid = new Table();
        grid.left().top();
        root.add(grid).width(width).left().row();

        final int[] sig = {Integer.MIN_VALUE};
        grid.update(() -> {
            CoreBuild core = Vars.player != null ? Vars.player.team().core() : null;
            int s = 0;
            Seq<Item> items = Vars.content.items();
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

            grid.clear();
            if (core == null) {
                grid.add(T("[scarlet]Không thấy lõi đội![]", "[scarlet]No team core found![]")).left();
                return;
            }
            int cols = width >= 250f ? 2 : 1;
            int n = 0;
            for (int i = 0; i < items.size; i++) {
                Item it = items.get(i);
                if (it.hidden || !core.items.has(it)) continue;
                Table cell = new Table();
                cell.background(Styles.black6);
                cell.add(new Image(it.uiIcon)).size(18f).pad(2f);
                Label nameLbl = cell.add(it.localizedName).color(Color.lightGray).left().growX().padLeft(3f).get();
                nameLbl.setFontScale(0.75f);
                nameLbl.setEllipsis(true);
                Label amtLbl = cell.add("[accent]" + core.items.get(it) + "[]").right().padRight(4f).get();
                amtLbl.setFontScale(0.78f);
                grid.add(cell).growX().pad(1.5f).uniformX();
                if (++n % cols == 0) grid.row();
            }
        });
        return root;
    }
}