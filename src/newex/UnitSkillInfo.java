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
import newex.content.Bulvasyr;
import newex.content.BulvasyrDash;
import newex.content.Honivery;
import newex.content.HoniveryPvp;
import newex.content.Lancyron;
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
            "[cyan]10.0s[] ([green]-25%[] nếu chém hụt)",
            "[scarlet]1500%[] Tấn Công + [green]5%[] Máu Tối Đa",
            "Vận khí hóa lưỡi liềm kiếm khí tầm rộng, lướt chậm rồi bứt tốc cực đại. Ai trúng chiêu sẽ nhận sát thương kỹ năng và [accent]ấn làm chậm[].",
            "[gold]Thu Kiếm Hồi Khí[]: chém hụt hoàn toàn thì kiếm khí quay về, hồi chiêu rút ngắn [green]25%[].",
            () -> "Hiệu ứng hồi chiêu khi hụt: [accent]-25% CD[] (còn 7.5 giây)",
            () -> "PC: Nhấn phím [accent][" + ArchuengPvp.getSkillKey().value + "][] (Mở ngắm -> Click hướng -> Tung) | Mobile: Bấm SKILL -> Chạm chọn hướng -> KHÓA -> TUNG"
        ));
    }

    public static void registerAureon() {
        register(new SkillProfile(
            "Aureon",
            () -> newex.content.aureon.aureon,
            "HỘI TỤ HỒ QUANG · QUÁ TẢI CẬN ĐIỆN",
            DamageKind.BASIC_ATTACK,
            Color.valueOf("66D9FF"),
            "Nội tại khóa: [cyan]1.5s[] | Chuyển khóa tức thì khi hạ gục",
            "[gold]4 Pháo Hồ Quang Điện[]: mỗi tia gây [scarlet]60%[] Tấn Công + [sky]60%[] Giáp mỗi giây (tổng 4 tia = [scarlet]240%[] Tấn Công + [sky]240%[] Giáp / giây)\n"
                + "[gold]Quá Tải Cận Điện[] (Nội tại khoảng cách): sát thương cả 4 khe vũ khí điện tự động tăng thêm từ [green]+0%[] (ở tầm xa nhất [cyan]42.5[] ô) lên tối đa [scarlet]+120%[] khi thu hẹp khoảng cách áp sát mục tiêu ([cyan]≤ 8[] ô)!\n"
                + "[sky]Hiệu ứng đòn đánh[]: Gây trạng thái [cyan]Giật Điện (Shocked)[] liên tục lên mục tiêu.",
            "Cần [cyan]1.5 giây[] khóa mục tiêu trước khi đồng loạt khai hỏa cả 4 khe pháo hồ quang điện cao áp tự động bám dính mục tiêu (không cần giữ nút bắn). Tính là [accent]Đòn Đánh Thường[].",
            "[gold]1. Quá Tải Cận Điện (Mới)[]: Khoảng cách giữa Aureon và mục tiêu càng thu hẹp, điện áp của cả 4 khe vũ khí càng tăng mạnh — cộng thêm tối đa [scarlet]+120% sát thương[] (gấp [gold]2.2 lần[]) khi áp sát!\n"
                + "[gold]2. Nối Mạch Tức Thì[]: Nếu mục tiêu bị hạ gục sau khi đã khóa đủ [cyan]100%[], mục tiêu kế tiếp trong tầm sẽ được [green]khóa ngay lập tức[] (bỏ qua 1.5s chờ khóa).",
            null,
            () -> "Tự động khóa mục tiêu gần nhất; trên PC/Mobile có thể rê tâm ngắm vào mục tiêu muốn ưu tiên khóa."
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

    public static void registerBulvasyr() {
        register(new SkillProfile(
            "Bulvasyr",
            () -> Bulvasyr.bulvasyr,
            "LƯỚT VÔ ẢNH · CHÙM SAO TÊN LỬA",
            DamageKind.BASIC_ATTACK,
            Color.valueOf("7A68FF"),
            "Lướt: [cyan]7.0s[] hồi chiêu, kéo dài [cyan]0.43s[]",
            "[gold]Chùm sao[]: mỗi đợt 4 tên lửa hình sao 2 cánh, bay xoắn rồi uốn cong vào mục tiêu\n[gold]Va chạm[]: nổ diện rộng, ngôi sao phóng to rồi tan dần\n[sky]Khiên vật lý mạn trái[]: chặn mọi đạn, mảnh đạn, laser & tia laser nhắm thẳng (không bắn xuyên được); chỉ vụ nổ mới gây sát thương cho cả khiên lẫn unit. Khiên nhận [scarlet]+50%[] sát thương từ đạn vật lý và [green]-50%[] từ đạn năng lượng/laser",
            "[gold]Lướt[]: không dịch chuyển tức thời. Unit tăng tốc dần, [cyan]lướt nhanh[] rồi giảm tốc, để lại vệt sáng và bóng ảnh xanh tím. PC lướt về phía chuột, mobile lướt theo hướng đang chạy.",
            "[gold]Tự khóa[]: tự khóa mục tiêu gần nhất rồi bắn chùm tên lửa, không cần giữ nút bắn.",
            null,
            () -> "PC: Lướt - phím [accent][" + BulvasyrDash.getSkillKey().value + "][] (đổi trong Cài đặt)\nMobile: nút LƯỚT trên màn hình"
        ));
    }

    public static void registerLancyron() {
        register(new SkillProfile(
            "Lancyron",
            () -> Lancyron.lancyron,
            "ROLL VẬN MỆNH 1-2-3 · KHIÊN VẬT LÝ AEGIS",
            DamageKind.BASIC_ATTACK,
            Color.valueOf("F2A81D"),
            "Roll 1-2-3: [cyan]5.0s[] hồi chiêu, quay số [cyan]1.0s[] trên đầu unit",
            "[gold]2 Bệ Tên Lửa Nhẹ Orkan[] (hông): ổ [cyan]20[] viên/khẩu, nạp [green]5[] viên/giây, xả [scarlet]10[] viên/giây tên lửa vật lý nổ diện rộng\n"
                + "[gold]Pháo Shotgun Nặng Thunder[] (tâm): bắn chùm [cyan]9[] viên đạn plasma siêu tốc xuyên giáp, sát thương dồn cực lớn\n"
                + "[sky]Khiên Vật Lý Trước Ngực[]: độ bền [green]550% HP[], che phủ mặt trước ([cyan]±66°[]), chặn hoàn toàn đạn, mảnh đạn & laser chính diện",
            "Khi kích hoạt sẽ hiện con số trên đầu unit và [gold]roll trong 1 giây[] (mọi người chơi trong host đều nhìn thấy), ngẫu nhiên 1 trong 3 hiệu ứng:\n"
                + "[#ff5a26]1. Song Kích Liên Thanh (5 giây)[]: Trong [cyan]5 giây[], tăng tốc độ bắn thêm [green]+10%[] và toàn bộ khe vũ khí của unit bắn [gold]liên kích[] (từ [white]1 trong 1 đòn[] thành [accent]2 trong 1 đòn[]).\n"
                + "[#4ef2ff]2. Ảo Ảnh Tàng Hình[]: Tạo ngay [cyan]1 unit phân thân[] tự động di chuyển và tấn công như bình thường; bản thân [sky]tàng hình trong 3 giây[], tăng [green]+50%[] tốc độ di chuyển và [lightgray]không thể bắn khi đang tàng hình[] (chỉ phe ta thấy bản tàng hình, địch hoàn toàn không thấy).\n"
                + "[#5bff96]3. Huyết Khiên & Tái Đúc Aegis[]: Phá khiên bắn tung mảnh giáp ra phía trước; nếu máu dưới [scarlet]99%[] cộng thêm [green]+5000 HP[] vào Max HP; nếu độ bền khiên dưới [scarlet]99%[] sẽ đợi [cyan]1 giây[] để tái đúc lại [green]100%[] khiên vật lý mới!",
            "[gold]Khiên Vật Lý Tách Lớp[]: Tấm khiên chính và 2 cánh giáp bản lề tự động đẩy thủy lực mở rộng khi khai hỏa. Nhận [scarlet]+50%[] sát thương từ đạn vật lý và giảm [green]-50%[] sát thương từ đạn năng lượng/laser.",
            null,
            () -> "PC: Roll 1-2-3 - phím [accent][" + Lancyron.getSkillKey().value + "][] (đổi trong Cài đặt -> Phím kĩ năng)\nMobile: nút ROLL 1-3 trên màn hình"
        ));
    }

    public static void init() {
        registerHonivery();
        registerAureon();
        registerBulvasyr();
        registerLancyron();
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