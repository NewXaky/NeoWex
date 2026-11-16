package newex;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import newex.content.ArchuengPvp;
import newex.content.Honivery;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Hệ thống trang mô tả kỹ năng của Unit:
 * Khi người chơi nhấn vào icon của unit có kỹ năng (trên HUD hoặc giao diện thông tin),
 * sẽ mở trang hồ sơ kỹ năng chi tiết của unit đó.
 */
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
    private static boolean listenerAttached = false;

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
            "TINH QUANG TRẢM",
            DamageKind.BASIC_ATTACK,
            Color.valueOf("D0C2FF"),
            "8.0 giây",
            "90% Tấn Công + 10% Máu Hiện Tại (Nhân với % Nội Tại)",
            "Tụ năng lượng vào Kiếm Sao 4 cánh và phóng ra sóng kiếm hình quạt 3 tầng xé rách không gian phía trước. Đòn chém được tính là Đòn Đánh Thường của unit.",
            "Tinh Hồn Thôn Phệ: Khi dùng kỹ năng tiêu diệt mục tiêu, tăng vĩnh viễn +1% sát thương cho kỹ năng này (tối đa 999%), đồng thời cứ mỗi 10.000 HP tối đa của mục tiêu bị hạ gục sẽ cộng thêm +1%.",
            () -> "Cộng dồn hiện tại: [accent]+" + Honivery.getPassiveBonusPct() + "%[] / 999%",
            () -> "PC: Nhấn phím [accent][" + Honivery.getSkillKey().value + "][] hoặc bấm nút Skill | Mobile: Bấm nút CHÉM"
        ));
    }

    public static void registerArchueng() {
        register(new SkillProfile(
            "Archueng",
            ArchuengPvp::findArch,
            "THIÊN PHONG KIẾM KHÍ",
            DamageKind.SKILL_ATTACK,
            Color.valueOf("5AB4FF"),
            "10.0 giây (Giảm 65% nếu chém hụt)",
            "500% Tấn Công + 5% Máu Tối Đa",
            "Vận khí tạo lưỡi liềm kiếm khí tầm rộng lướt chậm rồi bứt tốc cực đại, gây sát thương kỹ năng và khắc ấn làm chậm kẻ địch trúng chiêu.",
            "Thu Kiếm Hồi Khí: Khi nhát chém kiếm khí kết thúc mà chém hụt hoàn toàn mục tiêu, lập tức giảm 65% thời gian hồi chiêu của kỹ năng.",
            () -> "Hiệu ứng hồi chiêu khi hụt: [accent]-65% CD[] (còn 3.5 giây)",
            () -> "PC: Nhấn phím [accent][" + ArchuengPvp.getSkillKey().value + "][] (Mở ngắm -> Click hướng -> Tung) | Mobile: Bấm SKILL -> Chạm chọn hướng -> KHÓA -> TUNG"
        ));
    }

    public static void init() {
        registerHonivery();
        registerArchueng();

        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> attachSceneListener());
        attachSceneListener();
    }

    private static void attachSceneListener() {
        if (listenerAttached || Core.scene == null) return;
        listenerAttached = true;

        Core.scene.root.addCaptureListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                Element target = event.targetActor;
                if (target == null) return false;

                if (isInsideSkillCastButton(target)) return false;

                SkillProfile matched = findProfileFromElement(target);
                if (matched != null) {
                    event.stop();
                    showDialog(matched);
                    return true;
                }
                return false;
            }
        });
    }

    private static boolean isInsideSkillCastButton(Element el) {
        Element cur = el;
        while (cur != null) {
            String n = cur.name;
            if ("newex-honivery-skill-btn".equals(n)
                || "newex-archueng-skill".equals(n)
                || "newex-archueng-cancel".equals(n)) {
                return true;
            }
            cur = cur.parent;
        }
        return false;
    }

    private static SkillProfile findProfileFromElement(Element target) {
        if (target instanceof Image) {
            SkillProfile p = matchDrawable(((Image) target).getDrawable());
            if (p != null) return p;
        }
        if (target instanceof Group) {
            for (Element child : ((Group) target).getChildren()) {
                if (child instanceof Image) {
                    SkillProfile p = matchDrawable(((Image) child).getDrawable());
                    if (p != null) return p;
                }
            }
        }
        return null;
    }

    private static SkillProfile matchDrawable(arc.scene.style.Drawable drawable) {
        if (drawable == null) return null;
        String dStr = String.valueOf(drawable);
        for (SkillProfile p : profiles.values()) {
            UnitType ut = p.unitSupplier != null ? p.unitSupplier.get() : null;
            if (ut == null) continue;
            TextureRegion ui = ut.uiIcon;
            TextureRegion full = ut.fullIcon;
            if ((ui != null && dStr.contains(String.valueOf(ui)))
                || (full != null && dStr.contains(String.valueOf(full)))
                || dStr.contains(ut.name)) {
                return p;
            }
        }
        return null;
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

        addSection(body, "CÔNG THỨC SÁT THƯƠNG", p.formulaText, Color.valueOf("FFE07D"));
        addSection(body, "CHỦ ĐỘNG — MÔ TẢ KỸ NĂNG", p.activeDesc, p.accent);
        addSection(body, "NỘI TẠI ĐẶC BIỆT", p.passiveDesc, Color.valueOf("7CE8FF"));
        if (p.keybindHint != null) {
            addSection(body, "ĐIỀU KHIỂN & PHÍM TẮT", p.keybindHint.get(), Color.lightGray);
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