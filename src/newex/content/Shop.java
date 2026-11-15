package newex.content;

import arc.Core;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

/**
 * Chuyển từ shop.js (module): cửa hàng điểm + gacha tháp pháo.
 * API giữ như bản JS: getGachaPoints, saveGachaPoints, getUnlockedTurrets, saveUnlockedTurrets,
 * unlockAllTurrets, showShopDialog, showGachaDialog. Không cần init.
 */
public final class Shop {
    private static final String UNLOCKED_KEY = "newex-unlocked-turrets";
    private static final String POINTS_KEY = "newex-gacha-points";

    private Shop() {}

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }

    private static String t(String e, String vi) { return en() ? e : vi; }

    private static boolean mobile() { return Core.graphics.isPortrait() || Vars.mobile; }

    // ---------------- dữ liệu ----------------

    public static int getGachaPoints() { return Core.settings.getInt(POINTS_KEY, 0); }

    public static void saveGachaPoints(int pts) { Core.settings.put(POINTS_KEY, pts); }

    private static Seq<Block> filtered(Seq<Block> list) {
        Seq<Block> out = new Seq<>();
        if (list != null) {
            list.each(b -> {
                String n = b.name.toLowerCase();
                if (!n.contains("repulsyron") && !n.contains("emperical") && !n.contains("aether-spiral")
                    && !n.contains("aetherspiral") && !n.contains("aether spiral")) out.add(b);
            });
        }
        return out;
    }

    public static Seq<String> getUnlockedTurrets(Seq<Block> list) {
        Seq<Block> active = filtered(list);
        String saved = Core.settings.getString(UNLOCKED_KEY, "NOT_SET");
        Seq<String> seq = new Seq<>();
        if (saved.equals("NOT_SET")) {
            if (active.size > 0) {
                int limit = Math.min(3, active.size);
                for (int i = 0; i < limit; i++) seq.add(active.get(i).name);
            } else {
                seq.add("newex-nucleytor", "newex-emperfum", "newex-galaxvorram");
            }
            saveUnlockedTurrets(seq);
        } else if (!saved.isEmpty()) {
            for (String p : saved.split(",")) seq.add(p);
        }
        return seq;
    }

    public static void saveUnlockedTurrets(Seq<String> seq) {
        Core.settings.put(UNLOCKED_KEY, seq.toString(","));
    }

    public static void unlockAllTurrets(Seq<Block> list) {
        Seq<String> seq = new Seq<>();
        filtered(list).each(b -> seq.add(b.name));
        saveUnlockedTurrets(seq);
    }

    // ---------------- tiện ích UI ----------------

    private static Label label(Table parent, String text, float scale, float pad) {
        Cell<Label> c = parent.add(text);
        Label l = c.get();
        l.setFontScale(scale);
        c.pad(pad);
        return l;
    }

    private static Label wrapLabel(Table parent, String text, float scale) {
        Cell<Label> c = parent.add(text);
        Label l = c.get();
        l.setFontScale(scale);
        l.setWrap(true);
        l.setAlignment(Align.center);
        c.padBottom(6).growX();
        return l;
    }

    private static String ownedText(int owned, int total) {
        return t("Owned: " + owned + " / " + total + " turrets", "Đã sở hữu: " + owned + " / " + total + " tháp pháo");
    }

    private static void setupKeyListener(BaseDialog dialog, Seq<Block> list, Runnable onUpdate) {
        KeyCode[] code = {KeyCode.x, KeyCode.a, KeyCode.k, KeyCode.y};
        KeyCode[] full = {KeyCode.x, KeyCode.a, KeyCode.k, KeyCode.y, KeyCode.f, KeyCode.u, KeyCode.l, KeyCode.l};
        int[] idx = {0, 0};
        long[] last = {0};

        dialog.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, KeyCode key) {
                long now = Time.millis();
                if (now - last[0] > 1500) { idx[0] = 0; idx[1] = 0; }
                last[0] = now;

                if (key == full[idx[1]]) {
                    idx[1]++;
                    if (idx[1] >= full.length) {
                        idx[0] = 0; idx[1] = 0;
                        unlockAllTurrets(list);
                        Vars.ui.showInfoToast(t("[lime]🎉 Unlocked all turrets! (xakyfull)[]",
                            "[lime]🎉 Đã mở khóa toàn bộ tất cả tháp pháo! (xakyfull)[]"), 3f);
                        if (onUpdate != null) onUpdate.run();
                        return false;
                    }
                } else {
                    idx[1] = key == full[0] ? 1 : 0;
                }

                if (key == code[idx[0]]) {
                    idx[0]++;
                    if (idx[0] >= code.length) {
                        idx[0] = 0;
                        saveGachaPoints(getGachaPoints() + 10);
                        Vars.ui.showInfoToast(t("[green]+10 points! (xaky code entered successfully)[]",
                            "[green]+10 điểm! (Nhập mã xaky thành công)[]"), 2f);
                        if (onUpdate != null) onUpdate.run();
                    }
                } else {
                    idx[0] = key == code[0] ? 1 : 0;
                }
                return false;
            }
        });
    }

    // ---------------- cửa hàng ----------------

    public static void showShopDialog(Seq<Block> list, Runnable loader) {
        if (list.isEmpty() && loader != null) loader.run();

        Seq<Block> active = filtered(list);
        boolean mob = mobile();

        BaseDialog dialog = new BaseDialog(t("Turret Shop", "Cửa Hàng Tháp Pháo (Shop)"));
        dialog.setFillParent(true);
        Table content = dialog.cont;
        content.clear();

        Table main = new Table();
        main.top().margin(8);

        label(main, t("[accent]-- TURRET SHOP --[]", "[accent]-- CỬA HÀNG THÁP PHÁO --[]"), mob ? 1f : 1.2f, 6);
        main.row();
        wrapLabel(main, t("Purchase turrets directly with points or try your luck with Gacha!",
            "Mua trực tiếp tháp pháo bằng điểm hoặc thử vận may với Gacha!"), mob ? 0.75f : 0.9f);
        main.row();

        Label pointsLabel = label(main, "", mob ? 0.85f : 1f, 3);
        main.row();
        Label statusLabel = label(main, "", mob ? 0.8f : 0.95f, 3);
        main.row();

        Table top = new Table();
        top.button(t("🎲 Go to Random Gacha (10pts/spin)", "🎲 Chuyển đến Gacha Ngẫu Nhiên (10đ/lượt)"), Styles.flatTogglet, () -> {
            dialog.hide();
            showGachaDialog(list, loader);
        }).size(mob ? 260 : 320, mob ? 40 : 45).pad(4);
        main.add(top).pad(4).row();

        Table listTable = new Table();
        Runnable[] refresh = {null};

        Table io = new Table();
        io.button(t("📤 Export to File", "📤 Xuất Ra File"), Styles.cleart, () -> {
            Seq<String> unlocked = getUnlockedTurrets(list);
            StringBuilder sb = new StringBuilder().append(getGachaPoints());
            for (int i = 0; i < active.size; i++) {
                if (unlocked.contains(active.get(i).name)) sb.append('.').append(i);
            }
            try {
                Core.files.local("newex_shop_save.txt").writeString(sb.toString());
                Vars.ui.showInfo(t("[green]Successfully exported to file:\nconfig/newex_shop_save.txt[]",
                    "[green]Đã xuất thành công ra file:\nconfig/newex_shop_save.txt[]"));
            } catch (Exception ex) {
                Vars.ui.showInfo(t("[red]Export file error: " + ex + "[]", "[red]Lỗi xuất file: " + ex + "[]"));
            }
        }).size(mob ? 125 : 155, mob ? 36 : 40).pad(3);

        io.button(t("📥 Import from File", "📥 Nhập Từ File"), Styles.cleart, () -> {
            try {
                arc.files.Fi file = Core.files.local("newex_shop_save.txt");
                if (!file.exists()) {
                    Vars.ui.showInfo(t("[red]File config/newex_shop_save.txt not found! Please export first.[]",
                        "[red]Không tìm thấy file config/newex_shop_save.txt! Hãy xuất file trước![]"));
                    return;
                }
                String[] parts = file.readString().trim().split("\\.");
                int pts;
                try { pts = Integer.parseInt(parts[0].trim()); } catch (Exception ex) {
                    Vars.ui.showInfo(t("[red]Invalid number format in file![]", "[red]Định dạng mã số trong file không hợp lệ![]"));
                    return;
                }
                saveGachaPoints(pts);
                Seq<String> now = new Seq<>();
                for (int i = 1; i < parts.length; i++) {
                    try {
                        int k = Integer.parseInt(parts[i].trim());
                        if (k >= 0 && k < active.size) now.add(active.get(k).name);
                    } catch (Exception ignored) {}
                }
                saveUnlockedTurrets(now);
                Vars.ui.showInfo(t("[green]Successfully imported data from file![]", "[green]Đã nhập dữ liệu từ file thành công![]"));
                refresh[0].run();
            } catch (Exception ex) {
                Vars.ui.showInfo(t("[red]Read file error: " + ex + "[]", "[red]Lỗi đọc file: " + ex + "[]"));
            }
        }).size(mob ? 125 : 155, mob ? 36 : 40).pad(3);
        main.add(io).pad(4).row();

        label(main, t("[accent]Direct Purchase List (Price: 20 pts/each):[]", "[accent]Danh sách mua trực tiếp (Giá: 20 điểm/cái):[]"),
            mob ? 0.85f : 1f, 0);
        main.row();

        refresh[0] = () -> {
            listTable.clear();
            Seq<String> cur = getUnlockedTurrets(list);
            pointsLabel.setText(t("[yellow]Current Points: " + getGachaPoints() + " pts[]", "[yellow]Điểm hiện có: " + getGachaPoints() + " điểm[]"));
            statusLabel.setText(ownedText(cur.size, active.size));

            active.each(block -> {
                boolean owned = cur.contains(block.name);
                Table card = new Table(Tex.button);
                card.margin(mob ? 4 : 6);

                String name = block.localizedName;
                if (block.name.endsWith("-dor") || block.name.endsWith("-xylaon")) name += " ";

                Table left = new Table();
                left.left();
                if (block.uiIcon != null) {
                    left.image(new TextureRegionDrawable(block.uiIcon)).size(mob ? 20 : 24).padRight(6);
                }
                Label nl = left.add("[white]" + name + "[]").left().get();
                nl.setFontScale(mob ? 0.7f : 0.82f);
                nl.setWrap(false);
                card.add(left).left().growX().padLeft(4);

                if (owned) {
                    Label ol = card.add(t("[green]✓ Owned[]", "[green]✓ Đã sở hữu[]")).right().padRight(6).get();
                    ol.setFontScale(mob ? 0.7f : 0.85f);
                } else {
                    card.button(t("Buy (20pt)", "Mua (20đ)"), () -> {
                        int pts = getGachaPoints();
                        if (pts < 20) {
                            Vars.ui.showInfoToast(t("[red]Not enough points! Need 20 points for direct purchase.[]",
                                "[red]Không đủ điểm! Cần 20 điểm để mua trực tiếp.[]"), 3f);
                            return;
                        }
                        saveGachaPoints(pts - 20);
                        Seq<String> u = getUnlockedTurrets(list);
                        u.add(block.name);
                        saveUnlockedTurrets(u);
                        Vars.ui.showInfoToast(t("[green]Successfully purchased: " + block.localizedName + "![]",
                            "[green]Mua thành công: " + block.localizedName + "![]"), 2f);
                        refresh[0].run();
                    }).size(mob ? 85 : 100, mob ? 32 : 36).right().padRight(2);
                }
                listTable.add(card).growX().height(mob ? 48 : 52).pad(2).row();
            });

            Table resetCard = new Table(Tex.button);
            resetCard.margin(6);
            resetCard.button(t("[red]Reset Shop Data (+20 pts)[]", "[red]Xóa dữ liệu shop (+20 điểm)[]"), Styles.flatTogglet, () -> {
                Core.settings.put(UNLOCKED_KEY, "");
                saveGachaPoints(20);
                Vars.ui.showInfoToast(t("[red]Shop data reset (+20 pts)![]", "[red]Đã reset dữ liệu shop (+20 điểm)![]"), 2f);
                refresh[0].run();
            }).growX().height(mob ? 40 : 48).get().getLabel().setFontScale(mob ? 0.8f : 0.95f);
            listTable.add(resetCard).growX().pad(6).row();
        };

        listTable.top().margin(4);
        refresh[0].run();
        main.add(listTable).growX().row();

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFadeScrollBars(false);
        content.add(scroll).grow().row();

        setupKeyListener(dialog, list, refresh[0]);
        dialog.addCloseButton();
        dialog.show();
    }

    // ---------------- gacha ----------------

    public static void showGachaDialog(Seq<Block> list, Runnable loader) {
        if (list.isEmpty() && loader != null) loader.run();

        Seq<Block> active = filtered(list);
        boolean mob = mobile();

        BaseDialog dialog = new BaseDialog(t("Turret Gacha Spin", "Quay Gacha Tháp Pháo"));
        dialog.setFillParent(true);
        Table content = dialog.cont;
        content.clear();

        Table main = new Table();
        main.top().margin(8);

        label(main, t("[accent]-- RANDOM GACHA SYSTEM --[]", "[accent]-- HỆ THỐNG GACHA NGẪU NHIÊN --[]"), mob ? 1f : 1.2f, 6);
        main.row();
        wrapLabel(main, t("Try your luck spinning for turrets at a lower cost (10 pts/spin)!",
            "Thử vận may quay tháp pháo với chi phí rẻ hơn (10 điểm/lượt)!"), mob ? 0.75f : 0.9f);
        main.row();

        Label pointsLabel = label(main, "", mob ? 0.8f : 1f, 3);
        main.row();
        Label statusLabel = label(main, "", mob ? 0.8f : 0.95f, 3);
        main.row();

        Runnable updatePoints = () -> {
            int p = getGachaPoints();
            pointsLabel.setText(t("[yellow]Gacha Points: " + p + " pts [gray](Requires 10pts/spin)[]",
                "[yellow]Điểm Gacha hiện có: " + p + " điểm [gray](Cần 10đ/lượt)[]"));
        };
        Runnable updateStatus = () -> statusLabel.setText(ownedText(getUnlockedTurrets(list).size, active.size));
        updatePoints.run();
        updateStatus.run();

        Table backTable = new Table();
        Label backLabel = backTable.button(t("⬅ Back to Shop", "⬅ Quay lại Cửa Hàng Shop"), Styles.flatTogglet, () -> {
            dialog.hide();
            showShopDialog(list, loader);
        }).size(mob ? 240 : 280, mob ? 38 : 42).pad(3).get().getLabel();
        backLabel.setFontScale(mob ? 0.75f : 0.9f);
        backLabel.setWrap(false);
        main.add(backTable).pad(4).row();

        Table result = new Table(Tex.button);
        result.margin(10);

        // text + block (có thể null)
        java.util.function.BiConsumer<String, Block> showResult = (text, block) -> {
            result.clear();
            Table inner = new Table();
            inner.center();
            if (block != null && block.uiIcon != null) {
                inner.image(new TextureRegionDrawable(block.uiIcon)).size(mob ? 26 : 32).padRight(8);
            }
            Label l = inner.add(text).left().get();
            l.setFontScale(mob ? 0.75f : 0.9f);
            l.setWrap(false);
            result.add(inner).center();
        };
        showResult.accept(t("[yellow]Click 'Spin Turret' to start![]", "[yellow]Nhấn 'Quay Tháp Pháo' để bắt đầu![]"), null);
        main.add(result).growX().minHeight(mob ? 65 : 75).pad(8).row();

        Table btnTable = new Table();
        boolean[] spinning = {false};
        btnTable.button(t("Spin Turret (10pt)", "Quay Tháp Pháo (10đ)"), () -> {
            if (spinning[0]) return;
            if (list.isEmpty() && loader != null) loader.run();
            int pts = getGachaPoints();
            if (pts < 10) {
                Vars.ui.showInfoToast(t("[red]Not enough points! Need at least 10 points.[]", "[red]Không đủ điểm! Cần ít nhất 10 điểm.[]"), 3f);
                return;
            }

            Seq<String> unlocked = getUnlockedTurrets(list);
            Seq<Block> locked = new Seq<>();
            active.each(b -> { if (!unlocked.contains(b.name)) locked.add(b); });

            if (locked.isEmpty()) {
                showResult.accept(t("[green]You have unlocked all turrets![]", "[green]Bạn đã mở khóa toàn bộ tháp pháo![]"), null);
                Vars.ui.showInfoToast(t("Unlocked all turrets!", "Đã mở khóa tất cả tháp pháo!"), 2f);
                return;
            }

            saveGachaPoints(pts - 10);
            updatePoints.run();

            locked.shuffle();
            Block won = locked.first();
            int[] frame = {0};
            spinning[0] = true;

            Runnable[] anim = {null};
            anim[0] = () -> {
                if (frame[0] < 15) {
                    Block r = active.random();
                    showResult.accept(t("[orange]✦ Spinning... [cyan]" + r.localizedName + " ✦[]",
                        "[orange]✦ Đang quay... [cyan]" + r.localizedName + " ✦[]"), r);
                    frame[0]++;
                    Time.run(2f, anim[0]);
                } else {
                    unlocked.add(won.name);
                    saveUnlockedTurrets(unlocked);
                    showResult.accept(t("[lime]🎉 Congratulations! Unlocked: [white]" + won.localizedName + "[]",
                        "[lime]🎉 Chúc mừng! Mở khóa: [white]" + won.localizedName + "[]"), won);
                    statusLabel.setText(ownedText(unlocked.size, active.size));
                    Vars.ui.showInfoToast(t("[green]Gacha success: " + won.localizedName + "![]",
                        "[green]Gacha thành công: " + won.localizedName + "![]"), 2f);
                    spinning[0] = false;
                }
            };
            anim[0].run();
        }).size(mob ? 210 : 250, mob ? 42 : 48).pad(4).get().getLabel().setFontScale(mob ? 0.85f : 1f);
        main.add(btnTable).pad(6).row();

        ScrollPane scroll = new ScrollPane(main);
        scroll.setFadeScrollBars(false);
        content.add(scroll).grow().row();

        setupKeyListener(dialog, list, updatePoints);
        dialog.addCloseButton();
        dialog.show();
    }
}
