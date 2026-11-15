package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.entities.UnitSorts;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.PointLaserBulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

/** plasanod.js → Java. Pháo laser liên tục: 2 tia quét tay (tự tính sát thương), đạn vật lý xuyên, gắn CEI, sét; nhánh MK2/MK2B + 6 phúc lợi. */
public final class Plasanod {
    // Hàm ngôn ngữ ĐÚNG của file JS này (bài học 8)
    static boolean isEn() {
        return "en".equals(Core.settings.getString("locale", "en")) || Core.settings.getString("locale", "").startsWith("en");
    }

    static String trP(String key) { return trP(key, "", ""); }

    static String trP(String key, String viText, String enText) {
        boolean currentIsEn = isEn();
        if (key.equals("req_core")) {
            return currentIsEn ? "[yellow]CORE STORAGE RESOURCE REQUIREMENTS:[]\n" : "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n";
        }
        if (key.equals("req_sp")) {
            return currentIsEn ? "[gold]★ SPECIAL PERKS:[] Copper: " : "[gold]★ PHÚC LỢI ĐẶC BIỆT:[] Đồng: ";
        }
        if (key.equals("req_lead")) return currentIsEn ? " | Lead: " : " | Chì: ";
        if (key.equals("req_mk2")) return currentIsEn ? "[cyan]MK2 Branch:[] Silicon: " : "[cyan]Nhánh MK2:[] Silicon: ";
        if (key.equals("req_mk2b")) return currentIsEn ? "[purple]MK2B Branch:[] Silicon: " : "[purple]Nhánh MK2B:[] Silicon: ";
        if (key.equals("no_core")) return currentIsEn ? "[red]Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";
        if (key.equals("sp_title")) return currentIsEn ? "[gold]★ SPECIAL UPGRADE PERKS (RANDOM) ★[]" : "[gold]★ PHÚC LỢI NÂNG CẤP ĐẶC BIỆT (NGẪU NHIÊN) ★[]";
        if (key.equals("sp_desc")) {
            return currentIsEn ?
                "Activate random protocol to get 1 of 6 permanent perks:\n" +
                " • [yellow]Perk 1 (~19.6%):[] +215% Base Damage, Faster Laser charge speed.\n" +
                " • [orange]Perk 2 (~29.4%):[] +50% Effective Range, +150% Energy Beam Damage.\n" +
                " • [cyan]Perk 3 (~19.6%):[] +50% All Stats (DPS, Range, Lightning Rate).\n" +
                " • [purple]Perk 4 (~29.4%):[] +50% Effective Range, Auto fire Dual Piercing Physical Bullets.\n" +
                " • [green]Perk 5 (1% SUPER RARE):[] Omnidirectional Lasers, Expand Elemental Blast & Buff nearby allies.\n" +
                " • [red]Perk 6 (1% SUPER RARE):[] +500% Base Damage, Scorching foes with continuous Demon Lightning blasts!"
                :
                "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 6 phúc lợi vĩnh viễn:\n" +
                " • [yellow]Phúc lợi 1 (~19.6%):[] +215% Sát thương gốc, Tăng tốc độ nạp chùm tia Laser.\n" +
                " • [orange]Phúc lợi 2 (~29.4%):[] +50% Tầm bắn hiệu dụng, Tăng 150% Sát thương Tia Năng Lượng.\n" +
                " • [cyan]Phúc lợi 3 (~19.6%):[] +50% Tất cả chỉ số (DPS, Phạm vi bắn, Tỷ lệ phóng sét lan).\n" +
                " • [purple]Phúc lợi 4 (~29.4%):[] +50% Tầm bắn hiệu dụng, Tự động phóng đạn Vật Lý Kép xuyên phá.\n" +
                " • [green]Phúc lợi 5 (1% SIÊU HIẾM):[] Phóng chùm Laser đa hướng, Mở rộng phạm vi nổ nguyên tố & Buff pháo đồng minh gần nhất.\n" +
                " • [red]Phúc lợi 6 (1% SIÊU HIẾM):[] +500% Sát thương gốc, Thiêu rụi đối phương với chuỗi bộc phá Sét Ma Quỷ liên tục!";
        }
        if (key.equals("btn_roll")) return currentIsEn ? "[gold]ROLL PERK (4K Copper/Lead/Silicon)[]" : "[gold]QUAY PHÚC LỢI (4K Đồng/Chì/Silicon)[]";
        if (key.equals("won")) return currentIsEn ? "[gold]YOU WON:[]\n" : "[gold]BẠN ĐÃ TRÚNG:[]\n";
        if (key.equals("no_res_sp")) return currentIsEn ? "[red]Not enough resources for Special Perks![]" : "[red]Không đủ tài nguyên cho Phúc Lợi Đặc Biệt![]";
        if (key.equals("active_p1")) return currentIsEn ? "[yellow]✔ ACTIVATED: PERK 1\n• Base Damage +215%\n• Faster Laser sweep & focus[]" : "[yellow]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1\n• Sát thương gốc +215%\n• Tăng tốc độ quét & hội tụ chùm Laser[]";
        if (key.equals("active_p2")) return currentIsEn ? "[orange]✔ ACTIVATED: PERK 2\n• Effective Range +50%\n• Superconductive Laser Damage +150%[]" : "[orange]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2\n• Tầm bắn hiệu dụng +50%\n• Sát thương Laser Siêu Dẫn +150%[]";
        if (key.equals("active_p3")) return currentIsEn ? "[cyan]✔ ACTIVATED: PERK 3\n• +50% All turret stats & explosion effects[]" : "[cyan]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3\n• Tăng 50% Mọi chỉ số pháo & hiệu ứng bộc phá[]";
        if (key.equals("active_p4")) return currentIsEn ? "[purple]✔ ACTIVATED: PERK 4\n• Effective Range +50%\n• Fire extra Dual Piercing Physical Bullets[]" : "[purple]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 4\n• Tầm bắn hiệu dụng +50%\n• Kích hoạt bắn thêm đạn Vật Lý Kép xuyên phá[]";
        if (key.equals("active_p5")) return currentIsEn ? "[green]✔ ACTIVATED: PERK 5 (1% SUPER RARE)\n• Split multi-directional Lasers\n• Expand Gravity Vortex radius\n• Energy Buff for 4 nearby ally turrets[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 5 (1% SIÊU HIẾM)\n• Chùm Laser đa hướng tự động phân tách\n• Mở rộng bán kính Xoáy Trọng Lực\n• Buff năng lượng cho 4 pháo đồng minh lân cận[]";
        if (key.equals("active_p6")) return currentIsEn ? "[red]✔ ACTIVATED: PERK 6 (1% SUPER RARE)\n• Base Damage +500%\n• Auto continuous Demon Lightning blasts![]" : "[red]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 6 (1% SIÊU HIẾM)\n• Sát thương gốc +500%\n• Tự động xả chuỗi bộc phá Sét Ma Quỷ diện rộng liên tục![]";

        if (key.equals("mk2_desc")) {
            return currentIsEn ?
                "[white]• Structure HP: [gray]+0%[] (10,500 HP)\n" +
                "• Range: [green]+30%[] (364 px)\n" +
                "• Base Damage: [green]+300%[] (140 DPS)\n" +
                "• [orange]Long-Range Superconductive Beam Config:[] Focuses long-range energy beam\n• Adding Shocked effect with 15% lightning chain chance"
                :
                "[white]• Máu cấu trúc: [gray]+0%[] (10,500 HP)\n" +
                "• Tầm bắn: [green]+30%[] (364 px)\n" +
                "• Sát thương gốc: [green]+300%[] (140 DPS)\n" +
                "• [orange]Cấu Hình Tia Siêu Dẫn Tầm Xa:[] Tập trung chùm tia năng lượng siêu dẫn tầm xa\n• Bổ sung hiệu ứng Giật Điện (Shocked) kèm 15% tỷ lệ phóng sét lan rộng sang các mục tiêu lân cận";
        }
        if (key.equals("mk2_btn")) return currentIsEn ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]";
        if (key.equals("no_res_mk2")) return currentIsEn ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]";

        if (key.equals("mk2b_desc")) {
            return currentIsEn ?
                "[white]• Structure HP: [green]+55%[] (16,250 HP)\n" +
                "• Range: [gray]+0%[] (280 px)\n" +
                "• Base Damage: [green]+357%[] (160 DPS)\n" +
                "• [orange]Melee Gravity Vortex Variant:[] Optimizes turret at close range\n• Continuously applying +3 CEI stacks/sec (Melting, Electrified, Demon Lightning)"
                :
                "[white]• Máu cấu trúc: [green]+55%[] (16,250 HP)\n" +
                "• Tầm bắn: [gray]+0%[] (280 px)\n" +
                "• Sát thương gốc: [green]+357%[] (160 DPS)\n" +
                "• [orange]Biến Thể Xoáy Trọng Lực Cận Chiến:[] Tối ưu hóa pháo ở tầm gần\n• Gắn liên tục +3 tầng hiệu ứng CEI/giây (bao gồm Nóng Chảy, Điện Hóa và Sét Ma Quỷ) lên mục tiêu";
        }
        if (key.equals("mk2b_btn")) return currentIsEn ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]";
        if (key.equals("no_res_mk2b")) return currentIsEn ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]";

        if (key.equals("status_mk2")) return currentIsEn ? "[cyan]UPGRADED TO MK2 TURRET[]" : "[cyan]ĐÃ NÂNG CẤP THÀNH PHÁO MK2[]";
        if (key.equals("status_mk2b")) return currentIsEn ? "[purple]UPGRADED TO MK2B TURRET[]" : "[purple]ĐÃ NÂNG CẤP THÀNH PHÁO MK2B[]";
        if (key.equals("dialog_title")) return currentIsEn ? "Plasanod Upgrade Center" : "Trung tâm nâng cấp pháo Plasanod";
        if (key.equals("info_title")) return currentIsEn ? " Plasanod Turret Stats " : " Thông số pháo Plasanod ";
        if (key.equals("perk_bonus_range")) return currentIsEn ? " [gold](+50% from Perk)[]" : " [gold](+50% từ Phúc Lợi)[]";

        if (key.equals("info_mk1")) {
            return currentIsEn ?
                "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                "[lightgray]Turret HP:[] [green]10,500 HP[]\n" +
                "[lightgray]Effective Range:[] [orange]" + viText + " pixels[]" + enText + "\n" +
                "[lightgray]Base Damage:[] [yellow]210.00 DPS (Per beam)[]\n\n" +
                "[sky]⚡ SKILL MECHANICS:[]\n" +
                "• Sweeps 2 Continuous Energy Lasers combined with Piercing Long Physical Bullets.\n" +
                "• Passive: +50% Damage per 10% missing HP."
                :
                "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                "[lightgray]Máu tháp pháo:[] [green]10,500 HP[]\n" +
                "[lightgray]Tầm bắn hiệu dụng:[] [orange]" + viText + " pixel[]" + enText + "\n" +
                "[lightgray]Sát thương gốc:[] [yellow]210.00 DPS (Mỗi tia)[]\n\n" +
                "[sky]⚡ CƠ CHẾ KĨ NĂNG:[]\n" +
                "• Quét 2 chùm Laser Năng Lượng Liên Tục kết hợp xả đạn Vật Lý Dài xuyên phá.\n" +
                "• Nội tại: Mất 10% HP sẽ tăng +50% sát thương cho mọi loại đạn.";
        }
        if (key.equals("info_mk2")) {
            return currentIsEn ?
                "[cyan]⚡ BASE STATS (MK2) ⚡[]\n" +
                "[lightgray]Turret HP:[] [green]10,500 HP[]\n" +
                "[lightgray]Effective Range:[] [orange]" + viText + " pixels [lime](+30%)[]" + enText + "\n" +
                "[lightgray]Base Damage:[] [yellow]140.00 DPS [lime](+300%)[]\n\n" +
                "[lime]⚡ SKILL MECHANICS:[]\n" +
                "• [lightgray]Long-Range Superconductive Laser:[] Inflicts Shocked effect with 15% chance to chain lightning.\n" +
                "• Passive: +50% Damage per 10% missing HP."
                :
                "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                "[lightgray]Máu tháp pháo:[] [green]10,500 HP[]\n" +
                "[lightgray]Tầm bắn hiệu dụng:[] [orange]" + viText + " pixel [lime](+30%)[]" + enText + "\n" +
                "[lightgray]Sát thương gốc:[] [yellow]140.00 DPS [lime](+300%)[]\n\n" +
                "[lime]⚡ CƠ CHẾ KĨ NĂNG:[]\n" +
                "• [lightgray]Laser Siêu Dẫn Tầm Xa:[] Gây hiệu ứng Giật Điện (Shocked) kèm 15% tỷ lệ phóng sét lan rộng sang các mục tiêu.\n" +
                "• Nội tại: Mất 10% HP sẽ tăng +50% sát thương cho mọi loại đạn.";
        }
        if (key.equals("info_mk2b")) {
            return currentIsEn ?
                "[purple]⚡ BASE STATS (MK2B) ⚡[]\n" +
                "[lightgray]Turret HP:[] [green]16,250 HP [lime](+55%)[]\n" +
                "[lightgray]Effective Range:[] [orange]" + viText + " pixels[]" + enText + "\n" +
                "[lightgray]Base Damage:[] [red]160.00 DPS [lime](+357%)[]\n\n" +
                "[purple]🔥 SPECIAL SKILL MECHANICS:[]\n" +
                "• [lightgray]Melee Gravity Vortex:[] Applies Melting & Electrified, continuously adding +3 CEI stacks/sec.\n" +
                "• Passive: +50% Damage per 10% missing HP."
                :
                "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                "[lightgray]Máu tháp pháo:[] [green]16,250 HP [lime](+55%)[]\n" +
                "[lightgray]Tầm bắn hiệu dụng:[] [orange]" + viText + " pixel[]" + enText + "\n" +
                "[lightgray]Sát thương gốc:[] [red]160.00 DPS [lime](+357%)[]\n\n" +
                "[purple]🔥 CƠ CHẾ KĨ NĂNG ĐẶC BIỆT:[]\n" +
                "• [lightgray]Xoáy Trọng Lực Cận Chiến:[] Áp dụng đồng thời trạng thái Nóng Chảy & Điện Hóa, liên tục cộng dồn +3 tầng CEI/giây.\n" +
                "• Nội tại: Mất 10% HP sẽ tăng +50% sát thương cho mọi loại đạn.";
        }
        if (key.equals("info_perk_header")) return currentIsEn ? "\n\n[gold]★ SPECIAL PERKS ACTIVATED ★[]" : "\n\n[gold]★ ĐÃ KÍCH HOẠT PHÚC LỢI ĐẶC BIỆT ★[]";
        if (key.equals("info_p1")) return currentIsEn ? "\n[yellow]• Perk 1: Base Damage +215%, Faster Laser focus.[]" : "\n[yellow]• Phúc lợi 1: Sát thương gốc +215%, Tăng tốc độ hội tụ chùm Laser.[]";
        if (key.equals("info_p2")) return currentIsEn ? "\n[orange]• Perk 2: Range +50%, Superconductive Laser Damage +150%.[]" : "\n[orange]• Phúc lợi 2: Tầm bắn +50%, Sát thương Laser Siêu Dẫn +150%.[]";
        if (key.equals("info_p3")) return currentIsEn ? "\n[cyan]• Perk 3: +50% All stats & blast effects.[]" : "\n[cyan]• Phúc lợi 3: +50% Mọi chỉ số pháo & hiệu ứng bộc phá.[]";
        if (key.equals("info_p4")) return currentIsEn ? "\n[purple]• Perk 4: Range +50%, Fires extra Piercing Dual Bullets.[]" : "\n[purple]• Phúc lợi 4: Tầm bắn +50%, Bắn thêm đạn Vật Lý Kép xuyên phá.[]";
        if (key.equals("info_p5")) return currentIsEn ? "\n[green]• Perk 5 (1%): Multi-laser, Expand Vortex, Energy Buff to 4 nearby allies.[]" : "\n[green]• Phúc lợi 5 (1%): Laser đa hướng, Mở rộng Xoáy Trọng Lực, Buff năng lượng cho 4 pháo đồng minh lân cận.[]";
        if (key.equals("info_p6")) return currentIsEn ? "\n[red]• Perk 6 (1%): Base Damage +500%, Continuous Demon Lightning blasts![]" : "\n[red]• Phúc lợi 6 (1%): Sát thương gốc +500%, Bộc phá Sét Ma Quỷ diện rộng liên tục![]";

        return viText;
    }

    static final class ReqMK2 { final int silicon = 3000, plastanium = 1500, surgeAlloy = 500; }
    static final class ReqMK2B { final int silicon = 4000, thorium = 2000, phaseFabric = 800; }
    static final class ReqSp { final int copper = 4000, lead = 4000, silicon = 4000; }
    static final ReqMK2 reqPlasanodMK2 = new ReqMK2();
    static final ReqMK2B reqPlasanodMK2B = new ReqMK2B();
    static final ReqSp reqSpecialPlasanod = new ReqSp();

    static final Effect ringExpandFx = new Effect(35f, e -> {
        Draw.z(Layer.effect + 0.05f);
        float fin = e.fin(), fout = e.fout();
        Color baseColor = e.color != null ? e.color : Color.valueOf("00ffcc");
        float maxRadius = e.data instanceof Number ? ((Number) e.data).floatValue() : 12.0f;
        float currentRadius = fin * maxRadius;
        float strokeWidth = (1.5f + 1.5f * fout) * fout;
        Draw.color(baseColor, Color.white, fin * 0.3f);
        Lines.stroke(strokeWidth);
        Lines.circle(e.x, e.y, currentRadius);
        Draw.color(Color.white, baseColor, fin);
        Fill.circle(e.x, e.y, 2.5f * fout);
        Draw.reset();
    });

    static float distToSegment(float x, float y, float x1, float y1, float x2, float y2) {
        float l2 = Mathf.dst2(x1, y1, x2, y2);
        if (l2 == 0f) return Mathf.dst(x, y, x1, y1);
        float t = ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2;
        t = Math.max(0f, Math.min(1f, t));
        float projX = x1 + t * (x2 - x1);
        float projY = y1 + t * (y2 - y1);
        return Mathf.dst(x, y, projX, projY);
    }

    static BasicBulletType physicalBullet;
    static PlasanodBlock plasanod;

    /** Khối riêng để nạp sprite nền "reinforced-block-<size>" (JS: load() + customBaseRegion). */
    static final class PlasanodBlock extends ContinuousTurret {
        TextureRegion customBaseRegion;
        PlasanodBlock(String name) { super(name); }
        @Override public void load() {
            super.load();
            customBaseRegion = PerfCore.region("reinforced-" + "block-" + size);
        }
    }

    static PointLaserBulletType createPlasanodLaser(float baseDmg, String hitHex, float beamLen) {
        PointLaserBulletType b = new PointLaserBulletType();
        b.damage = baseDmg;
        // JS gán beamLength: PointLaserBulletType v157 không có trường này (bài học 69) → bỏ; tầm tia do range() của pháo quyết định.
        b.buildingDamageMultiplier = 0.4f;
        b.hitColor = Color.valueOf(hitHex);
        b.status = Sta.cei != null ? Sta.cei : StatusEffects.freezing;
        b.statusDuration = 7200f;
        return b;
    }

    /** Gọi từ NexMod.loadContent() SAU Sta.create() (cần Sta.cei). */
    public static void create() {
        physicalBullet = new BasicBulletType();
        physicalBullet.speed = 9f;
        physicalBullet.damage = 150f;
        physicalBullet.pierce = true;
        physicalBullet.pierceCap = 20;
        physicalBullet.pierceBuilding = true;
        physicalBullet.lifetime = 600f / 9f;
        physicalBullet.width = 14f;
        physicalBullet.height = 28f;
        physicalBullet.frontColor = Color.valueOf("ffcc66");
        physicalBullet.backColor = Color.valueOf("e67e22");
        physicalBullet.splashDamage = 120f;
        physicalBullet.splashDamageRadius = 20f;
        physicalBullet.status = Sta.cei != null ? Sta.cei : StatusEffects.freezing;
        physicalBullet.statusDuration = 7200f;
        physicalBullet.hitEffect = Fx.select;
        physicalBullet.despawnEffect = Fx.select;

        // JS còn tạo laserMK2/laserMK2B nhưng không dùng (chỉ laserMK1 gán cho shootType) → bỏ.
        PointLaserBulletType laserMK1 = createPlasanodLaser(210f, "84e184", 600f);

        plasanod = new PlasanodBlock("plasanod");
        plasanod.squareSprite = false;
        plasanod.health = 10500;
        plasanod.size = 3;
        plasanod.range = 280f;
        plasanod.targetAir = true;
        plasanod.targetGround = true;
        plasanod.configurable = true;
        plasanod.playerControllable = true;
        plasanod.unitSort = UnitSorts.strongest;
        plasanod.category = Category.turret;
        plasanod.shootType = laserMK1;
        plasanod.scaleDamageEfficiency = true;
        plasanod.shootWarmupSpeed = 0.08f;
        plasanod.shootCone = 360f;
        plasanod.aimChangeSpeed = 0.9f;
        plasanod.rotateSpeed = 0.9f;
        plasanod.shootY = 0.5f;
        plasanod.shootSound = Sounds.none;
        plasanod.loopSoundVolume = 1.0f;
        plasanod.loopSound = Sounds.beamLustre;
        plasanod.hasPower = true;
        plasanod.hasLiquids = true;
        plasanod.consumePower(15.0f);
        plasanod.consumeLiquid(Liquids.cryofluid, 12.0f / 60.0f);

        plasanod.config(Integer.class, (Building tile, Integer value) -> {
            if (tile instanceof PlasanodBuild) {
                PlasanodBuild t = (PlasanodBuild) tile;
                int val = value;
                if (val >= 10) t.setPerkTier(val - 10);
                else t.setTier(val);
            }
        });
        plasanod.buildType = () -> new PlasanodBuild(plasanod);
    }

    public static class PlasanodBuild extends ContinuousTurret.ContinuousTurretBuild {
        int tierState, perkTierState;
        float aimTimer, physShootTimer, ceiStackTimer, ringFxTimer, lastRotation, ceiAddTimer;
        float maxHpOverride = -1f;

        public PlasanodBuild(ContinuousTurret outer) { outer.super(); }

        float getDamageMultiplier() {
            float missingHpPercent = Math.max(0f, (1f - (health / maxHealth())) * 100f);
            int steps = (int) Math.floor(missingHpPercent / 10f);
            return 1f + steps * 0.50f;
        }

        int getPerkTier() { return perkTierState; }
        int getTier() { return tierState; }

        void setPerkTier(int val) {
            perkTierState = val;
            setTier(getTier());
        }

        /** Máu tối đa theo tier (JS: this.maxHealth = this.health; Building v157 chỉ có hàm maxHealth() nên override – bài học 66). */
        @Override public float maxHealth() {
            return maxHpOverride > 0f ? maxHpOverride : super.maxHealth();
        }

        void setTier(int val) {
            tierState = val;
            float baseH = (val == 2) ? 6500f : 4200f;
            if (getPerkTier() == 1) baseH = Math.round(baseH * 1.50f);
            health = baseH;
            maxHpOverride = baseH;
            // JS còn tạo laser theo tier gán vào `this.shootType` (Build không có trường này → thuộc tính rác, laser thật vẫn là shootType của block) → bỏ (bài học 66).
        }

        @Override public float range() {
            float baseR = 280f;
            int perk = getPerkTier();
            if (getTier() == 1) baseR = 364f;
            if (perk == 2 || perk == 4) baseR += 140f;
            return baseR;
        }

        // ===== Giao diện =====
        private String reqSnapshot() {
            CoreBuild core = team.core();
            if (core == null) return trP("no_core");
            int cCop = core.items.get(Items.copper), cLea = core.items.get(Items.lead), cSil = core.items.get(Items.silicon);
            int cPla = core.items.get(Items.plastanium), cSur = core.items.get(Items.surgeAlloy);
            int cTho = core.items.get(Items.thorium), cPha = core.items.get(Items.phaseFabric);
            String copCol = cCop >= reqSpecialPlasanod.copper ? "[green]" : "[red]";
            String leaCol = cLea >= reqSpecialPlasanod.lead ? "[green]" : "[red]";
            String silColSp = cSil >= reqSpecialPlasanod.silicon ? "[green]" : "[red]";
            String silColor1 = cSil >= reqPlasanodMK2.silicon ? "[green]" : "[red]";
            String plaColor1 = cPla >= reqPlasanodMK2.plastanium ? "[green]" : "[red]";
            String surColor1 = cSur >= reqPlasanodMK2.surgeAlloy ? "[green]" : "[red]";
            String silColor2 = cSil >= reqPlasanodMK2B.silicon ? "[green]" : "[red]";
            String thoColor2 = cTho >= reqPlasanodMK2B.thorium ? "[green]" : "[red]";
            String phaColor2 = cPha >= reqPlasanodMK2B.phaseFabric ? "[green]" : "[red]";
                return trP("req_core") +
                       trP("req_sp") + copCol + cCop + "[]/4000" + trP("req_lead") + leaCol + cLea + "[]/4000 | Silicon: " + silColSp + cSil + "[]/4000\n" +
                       trP("req_mk2") + silColor1 + cSil + "[]/" + reqPlasanodMK2.silicon + " | Plastanium: " + plaColor1 + cPla + "[]/" + reqPlasanodMK2.plastanium + " | Surge Alloy: " + surColor1 + cSur + "[]/" + reqPlasanodMK2.surgeAlloy + "\n" +
                       trP("req_mk2b") + silColor2 + cSil + "[]/" + reqPlasanodMK2B.silicon + " | Thorium: " + thoColor2 + cTho + "[]/" + reqPlasanodMK2B.thorium + " | Phase Fabric: " + phaColor2 + cPha + "[]/" + reqPlasanodMK2B.phaseFabric;
        }

        private void openUpgradeDialog() {
            final BaseDialog dialog = new BaseDialog(trP("dialog_title"));
            Cell<Label> reqCell = dialog.cont.add(reqSnapshot());
            reqCell.width(380f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branchesTable = new Table();
            Table spBox = new Table();
            spBox.background(Styles.black6);
            spBox.margin(12f);
            spBox.add(trP("sp_title")).row();

            int currentPerk = getPerkTier();
            int tier = getTier();

            if (currentPerk == 0) {
                Cell<Label> spD = spBox.add(trP("sp_desc"));
                spD.width(360f).get().setWrap(true);
                spD.get().setAlignment(Align.left);
                spBox.row();
                spBox.button(trP("btn_roll"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.copper) >= 4000 && core.items.get(Items.lead) >= 4000 && core.items.get(Items.silicon) >= 4000) {
                        NexNet.take(core, Items.copper, 4000);
                        NexNet.take(core, Items.lead, 4000);
                        NexNet.take(core, Items.silicon, 4000);

                        float rand = Mathf.random(100f);
                        int resultPerk;
                        if (rand < 1.0f) resultPerk = 5;
                        else if (rand < 2.0f) resultPerk = 6;
                        else if (rand < 2.0f + 19.6f) resultPerk = 1;
                        else if (rand < 2.0f + 19.6f + 29.4f) resultPerk = 2;
                        else if (rand < 2.0f + 19.6f + 29.4f + 19.6f) resultPerk = 3;
                        else resultPerk = 4;

                        setPerkTier(resultPerk);
                        configure(Integer.valueOf(10 + resultPerk));

                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(6f, 6f, x, y);

                        String perkName = "";
                        if (resultPerk == 1) perkName = isEn() ? "[yellow]PERK 1[]" : "[yellow]PHÚC LỢI 1[]";
                        else if (resultPerk == 2) perkName = isEn() ? "[orange]PERK 2 (+50% Range)[]" : "[orange]PHÚC LỢI 2 (+50% Tầm xa)[]";
                        else if (resultPerk == 3) perkName = isEn() ? "[cyan]PERK 3[]" : "[cyan]PHÚC LỢI 3[]";
                        else if (resultPerk == 4) perkName = isEn() ? "[purple]PERK 4 (+50% Range)[]" : "[purple]PHÚC LỢI 4 (+50% Tầm xa)[]";
                        else if (resultPerk == 5) perkName = isEn() ? "[green]★ PERK 5 (1% SUPER RARE) ★[]" : "[green]★ PHÚC LỢI 5 (1% SIÊU HIẾM) ★[]";
                        else perkName = isEn() ? "[red]★ PERK 6 (1% SUPER RARE) ★[]" : "[red]★ PHÚC LỢI 6 (1% SIÊU HIẾM) ★[]";
                        Vars.ui.showInfo(trP("won") + perkName);
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(trP("no_res_sp"));
                    }
                }).size(300f, 40f);
            } else {
                String perkText = "";
                if (currentPerk >= 1 && currentPerk <= 6) perkText = trP("active_p" + currentPerk);
                Cell<Label> spD = spBox.add(perkText);
                spD.width(360f).get().setWrap(true);
                spD.get().setAlignment(Align.left);
            }

            branchesTable.add(spBox).width(360f);
            branchesTable.row();
            branchesTable.add().height(12f).row();

            if (tier == 0) {
                Table b1 = new Table();
                b1.background(Styles.black6);
                b1.margin(12f);
                b1.add("[cyan]===(MK2)===[]").row();
                Cell<Label> b1D = b1.add(trP("mk2_desc"));
                b1D.width(340f).get().setWrap(true);
                b1D.get().setAlignment(Align.left);
                b1.row();
                b1.button(trP("mk2_btn"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.silicon) >= reqPlasanodMK2.silicon && core.items.get(Items.plastanium) >= reqPlasanodMK2.plastanium && core.items.get(Items.surgeAlloy) >= reqPlasanodMK2.surgeAlloy) {
                        NexNet.take(core, Items.silicon, reqPlasanodMK2.silicon);
                        NexNet.take(core, Items.plastanium, reqPlasanodMK2.plastanium);
                        NexNet.take(core, Items.surgeAlloy, reqPlasanodMK2.surgeAlloy);
                        setTier(1);
                        configure(Integer.valueOf(1));
                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.fx(Fx.impactReactorExplosion, x, y);
                        NexNet.shake(8f, 8f, x, y);
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(trP("no_res_mk2"));
                    }
                }).size(180f, 38f);

                Table b2 = new Table();
                b2.background(Styles.black6);
                b2.margin(12f);
                b2.add("[purple]===(MK2B)===[]").row();
                Cell<Label> b2D = b2.add(trP("mk2b_desc"));
                b2D.width(340f).get().setWrap(true);
                b2D.get().setAlignment(Align.left);
                b2.row();
                b2.button(trP("mk2b_btn"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.silicon) >= reqPlasanodMK2B.silicon && core.items.get(Items.thorium) >= reqPlasanodMK2B.thorium && core.items.get(Items.phaseFabric) >= reqPlasanodMK2B.phaseFabric) {
                        NexNet.take(core, Items.silicon, reqPlasanodMK2B.silicon);
                        NexNet.take(core, Items.thorium, reqPlasanodMK2B.thorium);
                        NexNet.take(core, Items.phaseFabric, reqPlasanodMK2B.phaseFabric);
                        setTier(2);
                        configure(Integer.valueOf(2));
                        NexNet.fx(Fx.bigShockwave, x, y);
                        NexNet.fx(Fx.reactorExplosion, x, y);
                        NexNet.shake(10f, 10f, x, y);
                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(trP("no_res_mk2b"));
                    }
                }).size(180f, 38f);

                branchesTable.add(b1).width(360f);
                branchesTable.row();
                branchesTable.add().height(12f).row();
                branchesTable.add(b2).width(360f);
            } else {
                String statusLabel = (tier == 1) ? trP("status_mk2") : trP("status_mk2b");
                branchesTable.add(statusLabel).row();
            }

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = trP("info_title");
            String descStr = "";
            int currentTier = getTier();
            int currentPerk = getPerkTier();
            String bonusRangeText = (currentPerk == 2 || currentPerk == 4) ? trP("perk_bonus_range") : "";
            // JS nối số vào chuỗi: 280 → "280" (Java float ra "280.0") → làm tròn (bài học 21)
            String rangeStr = String.valueOf(Math.round(range()));
            if (currentTier == 0) {
                title += "[yellow](MK1)[]";
                descStr = trP("info_mk1", rangeStr, bonusRangeText);
            } else if (currentTier == 1) {
                title += "[cyan](MK2)[]";
                descStr = trP("info_mk2", rangeStr, bonusRangeText);
            } else if (currentTier == 2) {
                title += "[purple](MK2B)[]";
                descStr = trP("info_mk2b", rangeStr, bonusRangeText);
            }
            if (currentPerk > 0) {
                descStr += trP("info_perk_header");
                if (currentPerk >= 1 && currentPerk <= 6) descStr += trP("info_p" + currentPerk);
            }
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true);
            cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            table.button(Icon.upOpen, Styles.cleari, 40f, this::openUpgradeDialog).size(50f, 40f).tooltip(trP("dialog_title"));
            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog).size(50f, 40f).tooltip(isEn() ? "View detailed Plasanod stats" : "Xem thông số chi tiết pháo Plasanod");
        }

        @Override public Object config() { return Integer.valueOf(getTier()); }

        @Override public void updateTile() {
            super.updateTile();

            // JS: `this.isShooting` thiếu () → luôn truthy; Java gọi isShooting() thật (bài học 67).
            if (efficiency > 0f && isShooting()) {
                final float rot = rotation;
                final float dmgMult = getDamageMultiplier();

                if (Math.abs(Angles.angleDist(rot, lastRotation)) < 0.2f) {
                    aimTimer += Time.delta;
                } else {
                    aimTimer = 0f; physShootTimer = 0f; ceiStackTimer = 0f; ringFxTimer = 0f;
                }
                lastRotation = rot;

                final float maxLen = range();
                final float bx = x, by = y;
                final float startX = bx + Angles.trnsx(rot, 6f);
                final float startY = by + Angles.trnsy(rot, 6f);

                if (aimTimer >= 60f) {
                    physShootTimer += Time.delta;
                    ceiStackTimer += Time.delta;
                    ringFxTimer += Time.delta;

                    if (ringFxTimer >= 42f) {
                        float o1X = bx + Angles.trnsx(rot + 90f, 6f), o1Y = by + Angles.trnsy(rot + 90f, 6f);
                        float o2X = bx + Angles.trnsx(rot - 90f, 6f), o2Y = by + Angles.trnsy(rot - 90f, 6f);
                        float randRadius1 = Mathf.random(8f, 20f);
                        float randRadius2 = Mathf.random(8f, 20f);
                        ringExpandFx.at(o1X, o1Y, rot, Color.valueOf("00ffcc"), (Object) Float.valueOf(randRadius1));
                        ringExpandFx.at(o2X, o2Y, rot, Color.valueOf("ffcc66"), (Object) Float.valueOf(randRadius2));
                        ringFxTimer = 0f;
                    }

                    if (physShootTimer >= 24f) {
                        Bullet pb = physicalBullet.create(this, team, startX, startY, rot);
                        if (pb != null) pb.damage = physicalBullet.damage * dmgMult;
                        physShootTimer = 0f;
                    }

                    final float dpsDamage = (200f / 60f) * Time.delta * efficiency * dmgMult;

                    final float off1X = bx + Angles.trnsx(rot + 90f, 6f), off1Y = by + Angles.trnsy(rot + 90f, 6f);
                    final float e1X = off1X + Angles.trnsx(rot, maxLen), e1Y = off1Y + Angles.trnsy(rot, maxLen);
                    final float off2X = bx + Angles.trnsx(rot - 90f, 6f), off2Y = by + Angles.trnsy(rot - 90f, 6f);
                    final float e2X = off2X + Angles.trnsx(rot, maxLen), e2Y = off2Y + Angles.trnsy(rot, maxLen);

                    final boolean applyCeiBonus = getTier() == 2 && ceiStackTimer >= 60f;

                    ceiAddTimer += Time.delta;
                    boolean addStacks = false;
                    if (ceiAddTimer >= 60f) { addStacks = true; ceiAddTimer = 0f; }
                    final boolean addCeiStacks = addStacks;

                    // nearbyEnemies(team,x,y,bán kính) = hình vuông + lọc within(r + hitSize/2)
                    Units.nearbyEnemies(team, bx - maxLen, by - maxLen, maxLen * 2f, maxLen * 2f, u -> {
                        if (!u.within(bx, by, maxLen + u.hitSize / 2f)) return;
                        if (u.checkTarget(true, true)) {
                            boolean hitTia1 = distToSegment(u.x, u.y, off1X, off1Y, e1X, e1Y) <= u.hitSize + 4f;
                            boolean hitTia2 = distToSegment(u.x, u.y, off2X, off2Y, e2X, e2Y) <= u.hitSize + 4f;
                            if (hitTia1 || hitTia2) {
                                int totalHits = (hitTia1 ? 1 : 0) + (hitTia2 ? 1 : 0);
                                u.damage(dpsDamage * totalHits);
                                if (Sta.cei != null) {
                                    u.apply(Sta.cei, 7200f);
                                    if (addCeiStacks) {
                                        Sta.ceiStacks.merge(u.id, 20, Integer::sum);
                                        Sta.ceiLastAppliedTurret.put(u.id, this);
                                    }
                                    if (applyCeiBonus) {
                                        for (int i = 0; i < 3; i++) u.apply(Sta.cei, 7200f);
                                    }
                                }
                            }
                        }
                    });

                    if (applyCeiBonus) ceiStackTimer = 0f;
                }

                final float endX = startX + Angles.trnsx(rot, maxLen);
                final float endY = startY + Angles.trnsy(rot, maxLen);
                Unit targetUnit = Units.closestEnemy(team, startX, startY, maxLen,
                        u -> u.checkTarget(true, true) && distToSegment(u.x, u.y, startX, startY, endX, endY) <= u.hitSize + 4f);

                if (targetUnit != null) {
                    if (Sta.cei != null) {
                        targetUnit.apply(Sta.cei, 7200f);
                        Sta.ceiLastAppliedTurret.put(targetUnit.id, this);
                    }
                    int tier = getTier();
                    Color themeColor = (tier == 2) ? Color.valueOf("bf40bf") : ((tier == 1) ? Color.valueOf("00ffcc") : Color.valueOf("84e184"));
                    if (Mathf.chanceDelta(0.12)) {
                        Lightning.create(team, themeColor, 20f * dmgMult, targetUnit.x, targetUnit.y, Mathf.random(360f), 5);
                    }
                }
            } else {
                aimTimer = 0f; physShootTimer = 0f; ceiStackTimer = 0f; ringFxTimer = 0f;
            }
        }

        @Override public void draw() {
            // JS còn nhánh `plasanod.baseRegion` (không có ở v157 – bài học 1/13) → chỉ vẽ nền khi sprite tìm thấy.
            if (plasanod.customBaseRegion != null && plasanod.customBaseRegion.found()) {
                Draw.rect(plasanod.customBaseRegion, x, y);
            }
            if (plasanod.region != null && plasanod.region.found()) {
                Draw.rect(plasanod.region, x, y, rotation - 90f);
            }

            if (efficiency > 0f && isShooting()) {
                int tier = getTier();
                final Color tmpColor = (tier == 2) ? Color.valueOf("bf40bf") : ((tier == 1) ? Color.valueOf("00ffcc") : Color.valueOf("84e184"));
                final float bx = x, by = y, rot = rotation;

                if (aimTimer >= 60f) {
                    Draw.draw(Layer.effect, () -> {
                        Draw.color(tmpColor, Color.white, Mathf.absin(Time.time, 2f, 0.4f));
                        Lines.stroke(1.5f);
                        Lines.circle(bx, by, 10f + Mathf.absin(Time.time, 3f, 2f));
                        Draw.reset();
                    });

                    Draw.draw(Layer.bullet, () -> {
                        float len = range();
                        float off1X = bx + Angles.trnsx(rot + 90f, 6f), off1Y = by + Angles.trnsy(rot + 90f, 6f);
                        float e1X = off1X + Angles.trnsx(rot, len), e1Y = off1Y + Angles.trnsy(rot, len);
                        float off2X = bx + Angles.trnsx(rot - 90f, 6f), off2Y = by + Angles.trnsy(rot - 90f, 6f);
                        float e2X = off2X + Angles.trnsx(rot, len), e2Y = off2Y + Angles.trnsy(rot, len);

                        Draw.color(PerfCore.color("00ffcc"), Color.white, 0.8f + Mathf.absin(Time.time, 1f, 0.2f));
                        Lines.stroke(2.5f + Mathf.absin(Time.time, 2f, 1.0f));
                        Lines.line(off1X, off1Y, e1X, e1Y);
                        Fill.circle(e1X, e1Y, 3f + Mathf.absin(Time.time, 2f, 1.5f));

                        Draw.color(PerfCore.color("ffcc66"), Color.white, 0.8f + Mathf.absin(Time.time, 1f, 0.2f));
                        Lines.stroke(2.5f + Mathf.absin(Time.time, 2f, 1.0f));
                        Lines.line(off2X, off2Y, e2X, e2Y);
                        Fill.circle(e2X, e2Y, 3f + Mathf.absin(Time.time, 2f, 1.5f));
                        Draw.reset();
                    });
                }

                Draw.draw(Layer.effect, () -> {
                    int particles = 64;
                    float particleLife = 60.0f, particleLen = 6.0f;
                    Rand rand = new Rand(id);
                    float base = Time.time / particleLife;
                    for (int i = 0; i < particles; i++) {
                        float fin = (rand.random(1.0f) + base) % 1.0f;
                        float fout = 1.0f - fin;
                        float fslope = (fin < 0.5f) ? fin * 2.0f : (1.0f - fin) * 2.0f;
                        float particleLength = rand.random(particleLen * 0.7f, particleLen * 1.3f) * Mathf.curve(fin, 0.2f, 0.9f) * (efficiency / 2.5f + 1.0f);
                        float centerDeg = rand.random(Mathf.pi);
                        float pow2OutVal = 1.0f - (1.0f - fin) * (1.0f - fin);
                        float pow3InVal = fin * fin * fin;

                        Tmp.v1.trns(rot, pow3InVal * rand.random(30f, 60f) - rand.range(8f) - 6f,
                                (((rand.random(18f, 28f) * (fout + 1.0f) / 2.0f + 2.0f) / (3.0f * fin / 7.0f + 1.3f) - 1.0f) + rand.range(3f)) * (float) Math.cos(centerDeg));
                        float angle = Mathf.slerp(Tmp.v1.angle() - 180f, rot, pow2OutVal);
                        Tmp.v1.scl(efficiency / 3.7f + 1.0f);
                        Tmp.v1.add(bx, by);

                        Draw.color(Tmp.c2.set(tmpColor), Color.white, fin * 0.7f);
                        Lines.stroke(Mathf.curve(fslope, 0f, 0.42f) * 1.2f * Mathf.curve(fin, 0f, 0.6f));
                        Lines.lineAngleCenter(Tmp.v1.x, Tmp.v1.y, angle, particleLength);
                    }
                    Draw.reset();
                });
            }
        }

        @Override public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.b(getPerkTier());
        }

        @Override public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            if (revision >= 1) setPerkTier(read.b());
            else perkTierState = 0;
        }
    }
}
