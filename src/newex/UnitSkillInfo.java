package newex;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import newex.content.ArchuengPvp;
import newex.content.Honivery;
import newex.content.HoniveryPvp;
import newex.content.Zeralion;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class UnitSkillInfo {
    private UnitSkillInfo() {}

    public enum DamageKind {
        BASIC_ATTACK("ĐÒN ĐÁNH THƯỜNG", "#FFE07D"),
        SKILL_ATTACK("ĐÒN ĐÁNH KỸ NĂNG", "#5AB4FF");

        public final String label;
        public final String hexColor;

        DamageKind(String label, String hexColor) {
            this.label = label;
            this.hexColor = hexColor;
        }
    }

    public static class SkillProfile {
        public final String unitName;
        public final Supplier<UnitType> unitSupplier;
        public final String skillName;
        public final DamageKind damageKind;
        public final Color accent;
        public final String cooldownText;
        public final String formulaText;
        public final String activeDesc;
        public final String passiveDesc;
        public final Supplier<String> dynamicStatus;
        public final Supplier<String> keybindHint;

        public SkillProfile(
            String unitName,
            Supplier<UnitType> unitSupplier,
            String skillName,
            DamageKind damageKind,
            Color accent,
            String cooldownText,
            String formulaText,
            String activeDesc,
            String passiveDesc,
            Supplier<String> dynamicStatus,
            Supplier<String> keybindHint
        ) {
            this.unitName = unitName;
            this.unitSupplier = unitSupplier;
            this.skillName = skillName;
            this.damageKind = damageKind;
            this.accent = accent;
            this.cooldownText = cooldownText;
            this.formulaText = formulaText;
            this.activeDesc = activeDesc;
            this.passiveDesc = passiveDesc;
            this.dynamicStatus = dynamicStatus;
            this.keybindHint = keybindHint;
        }
    }

    private static final Map<String, SkillProfile> profiles = new HashMap<>();
    private static final Map<Integer, DamageKind> lastHitKinds = new HashMap<>();

    public static void markLastDamageBasic(int targetUnitId) {
        lastHitKinds.put(targetUnitId, DamageKind.BASIC_ATTACK);
    }

    public static void markLastDamageSkill(int targetUnitId) {
        lastHitKinds.put(targetUnitId, DamageKind.SKILL_ATTACK);
    }

    public static DamageKind getLastDamageKind(int targetUnitId) {
        return lastHitKinds.get(targetUnitId);
    }

    public static void register(SkillProfile profile) {
        if (profile == null || profile.unitName == null) return;
        profiles.put(profile.unitName, profile);
    }

    public static void registerHonivery() {
        register(new SkillProfile(
            "Honivery",
            () -> Honivery.honivery,
            "TINH QUANG TRẢM · COMBO 8 NHÁT",
            DamageKind.SKILL_ATTACK,
            Color.valueOf("D0C2FF"),
            "Tinh Quang Trảm: [cyan]8.0s[] | Combo 8 nhát: [cyan]2.75s[] (1.75s tung chiêu + 1s hồi)",
            "[gold]Tinh Quang Trảm[]: [scarlet]90%[] Tấn Công + [green]10%[] Máu Hiện Tại (nhân với % Nội Tại)\n"
                + "[sky]Khiên Tinh Quang[] [cyan]6s[]: giảm [green]72%[] sát thương nhận, phản [scarlet]70%[] về kẻ tấn công ([scarlet]28%[] +150% nội tại)\n"
                + "[gold]Vòng Tinh Quang[] [cyan]5s[]: địch trong vòng nhận [scarlet]100%[] Tấn Công mỗi giây và chậm [orange]-50%[]\n"
                + "[gold]Combo 8 nhát[]: mỗi nhát tăng dần [scarlet]50% → 120%[] Tấn Công, nhát cuối xoay [cyan]360°[]\n"
                + "[gold]Nhát đặc biệt[] (lốc xoáy phong kiếm ngọc lục bảo): [scarlet]430%[] Tấn Công",
            "[gold]Kỹ năng 1 · Tinh Quang Trảm[]\n"
                + "Dồn năng lượng vào Kiếm Sao 4 cánh, tung sóng kiếm hình quạt 3 tầng xé toạc không gian phía trước. Tính là [accent]Đòn Đánh Kỹ Năng[].\n"
                + "[gold]Kỹ năng 2 · Combo[]\n"
                + "8 nhát song tinh vân nối nhau, nhát cuối quét vòng [cyan]360°[]. Trong lúc chém có cơ hội tung nhát đặc biệt: [gold]2%[] khi bấm, [green]+10%[] mỗi nhát trúng (tổng [gold]12%[]), và [green]chắc chắn[] tung một nhát về phía mục tiêu khi hạ gục nó.",
            "[gold]Tinh Hồn Thôn Phệ[]: mỗi lần Tinh Quang Trảm hạ gục mục tiêu, kỹ năng này vĩnh viễn [green]+1%[] sát thương (tối đa [gold]999%[]). Cứ mỗi [cyan]10.000[] HP tối đa của mục tiêu bị hạ, cộng thêm [green]+1%[]. Hạ gục bằng Combo 8 nhát cũng được tính.",
            () -> "Cộng dồn hiện tại: [accent]+" + Honivery.getPassiveBonusPct() + "%[] / 999%",
            () -> "PC: Tinh Quang Trảm - phím [accent][" + Honivery.getSkillKey().value + "][] | Combo 8 nhát - phím [accent][" + HoniveryPvp.getSkillKey().value + "][] (đổi trong Cài đặt -> Phím kĩ năng)\n"
                + "Mobile: nút CHÉM = Tinh Quang Trảm | nút COMBO = Combo 8 nhát"
        ));
    }

    public static void registerArchueng() {
        register(new SkillProfile(
            "Archueng",
            ArchuengPvp::findArch,
            "THIÊN PHONG KIẾM KHÍ",
            DamageKind.SKILL_ATTACK,
            Color.valueOf("5AB4FF"),
            "[cyan]10.0s[] ([green]-65%[] nếu chém hụt)",
            "[scarlet]500%[] Tấn Công + [green]5%[] Máu Tối Đa",
            "Vận khí hóa lưỡi liềm kiếm khí tầm rộng, lướt chậm rồi bứt tốc cực đại. Ai trúng chiêu sẽ nhận sát thương kỹ năng và [accent]ấn làm chậm[].",
            "[gold]Thu Kiếm Hồi Khí[]: chém hụt hoàn toàn thì kiếm khí quay về, hồi chiêu rút ngắn [green]65%[].",
            () -> "Hiệu ứng hồi chiêu khi hụt: [accent]-65% CD[] (còn 3.5 giây)",
            () -> "PC: Nhấn phím [accent][" + ArchuengPvp.getSkillKey().value + "][] (Mở ngắm -> Click hướng -> Tung) | Mobile: Bấm SKILL -> Chạm chọn hướng -> KHÓA -> TUNG"
        ));
    }

    public static void registerZeralion() {
        register(new SkillProfile(
            "Zeralion",
            () -> Zeralion.zeralion,
            "PHÁO VAI 3 NHỊP · PHONG ẤN KHÓA MỤC TIÊU",
            DamageKind.BASIC_ATTACK,
            Color.valueOf("FFC933"),
            "Nội tại: khóa [cyan]1.5s[] rồi bắn laser, hồi [cyan]8.0s[]",
            "[gold]Nhịp 1[]: 3 viên, mỗi viên [scarlet]50%[] Tấn Công\n"
                + "[gold]Nhịp 2[]: 2 viên, mỗi viên [scarlet]125%[] Tấn Công + [green]10%[] Máu Hiện Tại\n"
                + "[gold]Nhịp 3[]: 1 viên, [scarlet]270%[] Tấn Công + [green]30%[] Máu Hiện Tại (tụ lực gần 1 giây)\n"
                + "Đạn [orange]không[] gây sát thương trực tiếp: nổ lan, bán kính theo nhịp khoảng [cyan]3.4 / 5.1 / 6.8[] ô, càng xa tâm càng yếu (còn [orange]40%[] ở rìa).",
            "Hai pháo vai bắn luân phiên theo chu kỳ 3 nhịp, nhịp sau dữ hơn nhịp trước. Đạn bay thẳng hàng nối đuôi nhau, chạm mục tiêu hay vật cản là nổ vàng kim lan khắp vùng. Tính là [accent]Đòn Đánh Thường[].",
            "[gold]Phong Ấn Khóa Mục Tiêu[]: tự khóa kẻ gần nhất trong [cyan]1.5s[] (có thể rê chuột chọn mục tiêu), rồi bắn tia laser vàng kim giam chặt mục tiêu [scarlet]0.9s[]: không bắn, không chạy, không xoay, không dùng kỹ năng (pilot vẫn hoạt động). Sau mỗi phát hồi [cyan]8s[], thời gian hồi hiện ở góc dưới bên phải màn hình.",
            null,
            null
        ));
    }

    public static void init() {
        registerHonivery();
        registerArchueng();
        registerZeralion();
    }

    /** Có hồ sơ kỹ năng cho unit này không (dùng để hiện nút "i"). */
    public static boolean has(String unitName) {
        return unitName != null && profiles.containsKey(unitName);
    }

    public static void showDialog(String unitName) {
        showDialog(unitName == null ? null : profiles.get(unitName));
    }

    public static void showDialog(SkillProfile p) {
        if (p == null) return;
        try { Sounds.uiButton.play(); } catch (Throwable ignored) {}

        BaseDialog dialog = new BaseDialog("[#" + p.accent.toString() + "]HỒ SƠ KỸ NĂNG // " + p.unitName.toUpperCase());
        dialog.addCloseButton();

        Table card = new Table(Styles.black8);
        card.margin(14f);

        Table header = new Table();
        header.setBackground(Tex.whiteui);
        header.setColor(new Color(0.08f, 0.10f, 0.14f, 0.95f));
        header.margin(10f);

        UnitType ut = p.unitSupplier != null ? p.unitSupplier.get() : null;
        if (ut != null && ut.uiIcon != null) {
            Table iconFrame = new Table(Styles.black6);
            iconFrame.margin(6f);
            iconFrame.add(new Image(ut.uiIcon)).size(68f);
            header.add(iconFrame).padRight(14f);
        }

        Table titleCol = new Table();
        titleCol.left();
        Label skillTitle = titleCol.add("[#" + p.accent.toString() + "]" + p.skillName + "[]").left().get();
        skillTitle.setFontScale(1.2f);
        titleCol.row();

        Label badge = titleCol.add("[" + p.damageKind.hexColor + "][" + p.damageKind.label + "][]  •  [lightgray]Hồi chiêu: [white]" + p.cooldownText + "[]").left().padTop(4f).get();
        badge.setFontScale(0.92f);
        titleCol.row();

        if (p.dynamicStatus != null) {
            Label statusLbl = titleCol.add(p.dynamicStatus.get()).left().padTop(4f).get();
            statusLbl.setFontScale(0.9f);
        }
        header.add(titleCol).growX().left();
        card.add(header).growX().padBottom(10f).row();
        card.image(Tex.whiteui, p.accent).growX().height(2.5f).padBottom(10f).row();

        Table body = new Table();
        body.top().left();

        addSection(body, "SÁT THƯƠNG TÍNH THẾ NÀO", p.formulaText, Color.valueOf("FFE07D"));
        addSection(body, "TUYỆT KỸ", p.activeDesc, p.accent);
        addSection(body, "NỘI TẠI ĐẶC BIỆT", p.passiveDesc, Color.valueOf("7CE8FF"));
        if (p.keybindHint != null) {
            addSection(body, "RA CHIÊU & PHÍM TẮT", p.keybindHint.get(), Color.lightGray);
        }

        ScrollPane pane = new ScrollPane(body, Styles.noBarPane);
        pane.setScrollingDisabled(true, false);
        card.add(pane).grow().row();

        float w = Math.min(Core.graphics.getWidth() * 0.88f, 640f);
        float h = Math.min(Core.graphics.getHeight() * 0.78f, 460f);
        dialog.cont.add(card).size(w, h);
        dialog.show();
    }

    private static void addSection(Table parent, String title, String content, Color titleCol) {
        Table box = new Table(Styles.black6);
        box.margin(9f);
        box.left();

        Label h = box.add("[#" + titleCol.toString() + "]" + title + "[]").left().growX().padBottom(4f).get();
        h.setFontScale(0.9f);
        box.row();

        Label c = box.add(content).left().growX().get();
        c.setWrap(true);
        c.setAlignment(Align.left);
        c.setFontScale(0.92f);

        parent.add(box).growX().padBottom(8f).row();
    }
}