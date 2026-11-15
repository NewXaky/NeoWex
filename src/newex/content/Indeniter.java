package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Chuyển từ indeniter.js. Pháo Indeniter: tích tầng Bemod rồi nổ, 6 phúc lợi ngẫu nhiên, nhánh MK2 / MK2B.
 * Block do Java tạo (`new ItemTurret`) → indeniter.hjson không có `type:` cấp cao nhất (bài 29a).
 * global.bemodStacks → Sta.bemodStacks, status bemod → Sta.bemod; global.cornerBuffedTurrets → map tĩnh ở đây.
 */
public final class Indeniter {
    private Indeniter() {}

    static final class Req {
        final int copper, lead, titanium, silicon, plastanium;
        Req(int copper, int lead, int titanium, int silicon, int plastanium) {
            this.copper = copper; this.lead = lead; this.titanium = titanium;
            this.silicon = silicon; this.plastanium = plastanium;
        }
    }

    static final Req reqMK2 = new Req(0, 0, 500, 300, 0);
    static final Req reqMK2B = new Req(0, 0, 800, 400, 200);
    static final Req reqSpecial = new Req(4000, 4000, 0, 4000, 0);

    /** JS: global.cornerBuffedTurrets (id pháo được buff → Indeniter nguồn). */
    static final Map<Integer, IndeniterBuild> cornerBuffedTurrets = new HashMap<>();

    private static Effect bulletCircleTrailFx, buffTurretPulseFx, hel;
    private static BasicBulletType perk5ShotgunBullet, indeniterBullet, indeniterMK2Bullet, indeniterMK2BBullet;
    private static ItemTurret turret;

    private static boolean regionsReady = false;
    private static TextureRegion rWing1, rWing2, rNon, rBarrel1, rBarrel2, rB1;

    private static boolean isEn() {
        String loc = Core.settings.getString("locale", "en");
        return loc.equals("en") || loc.startsWith("en");
    }

    private static TextureRegion reg(String n) { return PerfCore.region("newex-" + n, n); }

private static String tr(String key) {
    boolean currentIsEn = isEn();
    if (key.equals("dialog_title")) return currentIsEn ? "Indeniter Upgrade Center" : "Trung tâm nâng cấp pháo Indeniter";
    if (key.equals("no_core")) return currentIsEn ? "[red]Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";
    if (key.equals("req_core")) return currentIsEn ? "[gold]CORE STORAGE RESOURCE REQUIREMENTS:[]\n" : "[gold]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n";
    if (key.equals("req_sp")) return currentIsEn ? "[orange]★ SPECIAL PERKS:[] Copper: " : "[orange]★ PHÚC LỢI ĐẶC BIỆT:[] Đồng: ";
    if (key.equals("req_lead")) return currentIsEn ? " | Lead: " : " | Chì: ";
    if (key.equals("req_mk2")) return currentIsEn ? "[cyan]MK2 Branch:[] Titanium: " : "[cyan]Nhánh MK2:[] Titan: ";
    if (key.equals("req_mk2b")) return currentIsEn ? "[purple]MK2B Branch:[] Titanium: " : "[purple]Nhánh MK2B:[] Titan: ";
    if (key.equals("req_plast")) return currentIsEn ? " | Plastanium: " : " | Nhựa: ";
    if (key.equals("sp_title")) return currentIsEn ? "[gold]★ SPECIAL UPGRADE PERKS (RANDOM) ★[]" : "[gold]★ PHÚC LỢI NÂNG CẤP ĐẶC BIỆT (NGẪU NHIÊN) ★[]";
    if (key.equals("sp_desc")) {
        return currentIsEn ?
            "Activate random protocol to get 1 of 6 permanent perks:\n" +
            " • [yellow]Perk 1 (~19.6%):[] +215% Damage, Explosion adds 10 sub Bemod stacks, Explosion threshold reduced to 7.\n" +
            " • [orange]Perk 2 (~29.4%):[] Explosion adds 5 sub Bemod stacks in AOE, +150% Explosion Damage.\n" +
            " • [cyan]Perk 3 (~19.6%):[] +50% All Stats (Dmg, Chance, Range, Explosion Radius).\n" +
            " • [purple]Perk 4 (~29.4%):[] 500 Pure Dmg Explosion, Chain explodes to furthest target (150px), Fires dual bullets.\n" +
            " • [green]Perk 5 (1% SUPER RARE):[] 80% Extra bullet, 10% Shotgun 40 pellets, Explosion x3 times, Buff 4 nearby ally turrets.\n" +
            " • [red]Perk 6 (1% SUPER RARE):[] +500% Base Damage, Bemod explosion applies 10 stacks for infinite chain explosions!"
            :
            "Kích hoạt giao thức nâng cấp ngẫu nhiên nhận 1 trong 6 phúc lợi vĩnh viễn:\n" +
            " • [yellow]Phúc lợi 1 (~19.6%):[] +215% Sát thương, Nổ gán 10 tầng Bemod phụ, Giảm mốc nổ còn 7.\n" +
            " • [orange]Phúc lợi 2 (~29.4%):[] Nổ gán 5 tầng Bemod phụ diện rộng, +150% Sát thương vụ nổ.\n" +
            " • [cyan]Phúc lợi 3 (~19.6%):[] +50% Tất cả chỉ số (Dmg, Tỉ lệ, Tầm bắn, Phạm vi nổ).\n" +
            " • [purple]Phúc lợi 4 (~29.4%):[] Nổ 500 Dmg thuần, Nổ lây mục tiêu xa nhất 150px, Bắn đạn kép.\n" +
            " • [green]Phúc lợi 5 (1% SIÊU HIẾM):[] 80% Bắn thêm đạn, 10% Shotgun 40 viên, Nổ x3 lần, Buff cho 4 pháo đồng minh gần nhất (tia điện hel + vòng Zoom 2/3 size).\n" +
            " • [red]Phúc lợi 6 (1% SIÊU HIẾM):[] +500% Sát thương gốc, Vụ nổ Bemod gán ngay 10 tầng tạo chuỗi nổ dây chuyền vĩnh viễn!";
    }
    if (key.equals("btn_roll")) return currentIsEn ? "[gold]ROLL PERK (4K Copper/Lead/Silicon)[]" : "[gold]QUAY PHÚC LỢI (4K Đồng/Chì/Silicon)[]";
    if (key.equals("won")) return currentIsEn ? "[gold]YOU WON:[]\n" : "[gold]BẠN ĐÃ TRÚNG:[]\n";
    if (key.equals("no_res_sp")) return currentIsEn ? "[red]Not enough resources for Special Perks![]" : "[red]Không đủ tài nguyên cho Phúc Lợi Đặc Biệt![]";

    if (key.equals("active_p1")) return currentIsEn ? "[yellow]✔ ACTIVATED: PERK 1\n• Damage +215%\n• Explosion adds 10 sub Bemod stacks\n• Explosion threshold reduced to 7 stacks[]" : "[yellow]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 1\n• Sát thương +215%\n• Nổ gán 10 tầng Bemod phụ\n• Mốc nổ giảm còn 7 tầng[]";
    if (key.equals("active_p2")) return currentIsEn ? "[orange]✔ ACTIVATED: PERK 2\n• Explosion adds 5 sub Bemod stacks around\n• Explosion Damage +150%[]" : "[orange]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 2\n• Nổ gán 5 tầng Bemod phụ xung quanh\n• Sát thương nổ +150%[]";
    if (key.equals("active_p3")) return currentIsEn ? "[cyan]✔ ACTIVATED: PERK 3\n• +50% All turret stats & explosion effects[]" : "[cyan]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 3\n• Tăng 50% Mọi chỉ số pháo & hiệu ứng nổ[]";
    if (key.equals("active_p4")) return currentIsEn ? "[purple]✔ ACTIVATED: PERK 4\n• 500 Pure Dmg Explosion (75px Radius)\n• Chain explodes to furthest target (150px)\n• Fires dual bullets every 0.7s[]" : "[purple]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 4\n• Nổ 500 Dmg thuần (Bán kính 75px)\n• Nổ lây mục tiêu xa nhất 150px\n• Bắn đạn kép mỗi 0.7s[]";
    if (key.equals("active_p5")) return currentIsEn ? "[green]✔ ACTIVATED: PERK 5 (1% SUPER RARE)\n• 80% Extra bullet\n• 10% Shotgun 40 pellets, random speed\n• Bemod explosion x3 times (+10% Max HP)\n• Buff 4 nearby ally turrets[]" : "[green]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 5 (1% SIÊU HIẾM)\n• 80% Bắn thêm 1 đạn phụ\n• 10% Shotgun 40 viên, tốc độ ngẫu nhiên\n• Bemod nổ x3 lần (+10% Max HP)\n• Buff 4 pháo đồng minh gần nhất (Tia điện hel, nổ 500 Dmg + 2% Max HP)[]";
    if (key.equals("active_p6")) return currentIsEn ? "[red]✔ ACTIVATED: PERK 6 (1% SUPER RARE)\n• Base Damage +500%\n• Bemod explosion applies 10 stacks for infinite chain explosions![]" : "[red]✔ ĐÃ KÍCH HOẠT: PHÚC LỢI 6 (1% SIÊU HIẾM)\n• Sát thương gốc +500%\n• Vụ nổ Bemod gán ngay 10 tầng Bemod tạo chuỗi nổ dây chuyền vĩnh viễn![]";

    if (key.equals("mk2_desc")) {
        return currentIsEn ?
            "Firepower Microchip Acceleration Upgrade:\n" +
            " • Range increased [green]574 pixels (+40%)[]\n" +
            " • Damage increased [green]12.15 (+35%)[]\n" +
            " • Fire rate increased [pink]+15%[]\n" +
            " • Bemod apply chance: [yellow]45% (+15%)[]\n" +
            " • Bemod Explosion Dmg: [red]500 + 3% Max HP[]"
            :
            "Nâng cấp vi mạch gia tốc hỏa lực:\n" +
            " • Tầm bắn tăng [green]574 pixel (+40%)[]\n" +
            " • Sát thương tăng [green]12.15 (+35%)[]\n" +
            " • Tốc độ bắn tăng [pink]+15%[]\n" +
            " • Tỉ lệ gán Bemod: [yellow]45% (+15%)[]\n" +
            " • Sát thương Bemod nổ: [red]500 + 3% Max HP[]";
    }
    if (key.equals("mk2_btn")) return currentIsEn ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]";
    if (key.equals("no_res_mk2")) return currentIsEn ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]";

    if (key.equals("mk2b_desc")) {
        return currentIsEn ?
            "Long-Range Targeting Bombardment Config:\n" +
            " • Ultra long range [green]1230 pixels (+200%)[]\n" +
            " • Burst fire [orange]10 consecutive shots[] with [pink]3s[] cooldown\n" +
            " • Strong homing bullets [red](homingPower = 0.25)[]\n" +
            " • Bemod apply chance: [yellow]100% absolute[]"
            :
            "Cấu hình Oanh tạc Tầm xa Định vị:\n" +
            " • Phạm vi bắn siêu xa [green]1230 pixel (+200%)[]\n" +
            " • Bắn loạt [orange]10 viên liên tiếp[] nghỉ [pink]3 giây[]\n" +
            " • Đạn truy đuổi mục tiêu mạnh [red](homingPower = 0.25)[]\n" +
            " • Tỉ lệ gán Bemod: [yellow]100% tuyệt đối[]";
    }
    if (key.equals("mk2b_btn")) return currentIsEn ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]";
    if (key.equals("no_res_mk2b")) return currentIsEn ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]";

    if (key.equals("status_mk2")) return currentIsEn ? "[cyan]UPGRADED TO MK2 TURRET[]" : "[cyan]ĐÃ NÂNG CẤP THÀNH PHÁO MK2[]";
    if (key.equals("status_mk2b")) return currentIsEn ? "[purple]UPGRADED TO MK2B TURRET[]" : "[purple]ĐÃ NÂNG CẤP THÀNH PHÁO MK2B[]";

    if (key.equals("info_title")) return currentIsEn ? " Indeniter Turret Stats " : " Thông số pháo Indeniter ";
    if (key.equals("info_mk1")) return currentIsEn ? "[gold]⚡ BASE STATS (MK1) ⚡[]\n• Health: 5,500 | Range: 410px | Bullet Damage: 9.0\n• Bemod Chance: 30%" : "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n• Máu: 5,500 | Tầm bắn: 410px | Sát thương đạn: 9.0\n• Tỉ lệ gán Bemod: 30%";
    if (key.equals("info_mk2")) return currentIsEn ? "[cyan]⚡ BASE STATS (MK2) ⚡[]\n• Health: 8,250 | Range: 574px | Bullet Damage: 12.15\n• Bemod Chance: 45% | Bemod Explosion: 500 + 3% Max HP" : "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n• Máu: 8,250 | Tầm bắn: 574px | Sát thương đạn: 12.15\n• Tỉ lệ gán Bemod: 45% | Nổ Bemod: 500 + 3% Max HP";
    if (key.equals("info_mk2b")) return currentIsEn ? "[purple]⚡ BASE STATS (MK2B) ⚡[]\n• Health: 7,333 | Range: 1230px | Bullet Damage: 9.0\n• Mode: 10-shot Burst | Bemod Chance: 100%" : "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n• Máu: 7,333 | Tầm bắn: 1230px | Sát thương đạn: 9.0\n• Chế độ: Bắn loạt 10 viên | Tỉ lệ gán Bemod: 100%";

    if (key.equals("info_perk_header")) return currentIsEn ? "\n\n[gold]★ SPECIAL PERKS ACTIVATED ★[]" : "\n\n[gold]★ ĐÃ KÍCH HOẠT PHÚC LỢI ĐẶC BIỆT ★[]";
    if (key.equals("info_p1")) return currentIsEn ? "\n[yellow]• Perk 1: Damage +215%, Explosion adds 10 sub stacks, Threshold: 7 stacks.[]" : "\n[yellow]• Phúc lợi 1: Sát thương +215%, Nổ gán 10 tầng Bemod phụ, Giới hạn nổ: 7 tầng.[]";
    if (key.equals("info_p2")) return currentIsEn ? "\n[orange]• Perk 2: Explosion adds 5 sub stacks around, Explosion Dmg +150%.[]" : "\n[orange]• Phúc lợi 2: Nổ gán 5 tầng Bemod phụ xung quanh, Sát thương nổ +150%.[]";
    if (key.equals("info_p3")) return currentIsEn ? "\n[cyan]• Perk 3: +50% All turret stats & explosion effects.[]" : "\n[cyan]• Phúc lợi 3: +50% Mọi chỉ số pháo & hiệu ứng nổ.[]";
    if (key.equals("info_p4")) return currentIsEn ? "\n[purple]• Perk 4: 500 Pure Dmg Explosion (75px), Chain explodes (150px), Dual bullets every 0.7s.[]" : "\n[purple]• Phúc lợi 4: Nổ 500 Dmg thuần (75px), Nổ lây mục tiêu xa nhất 150px, Bắn đạn kép mỗi 0.7s.[]";
    if (key.equals("info_p5")) return currentIsEn ? "\n[green]• Perk 5 (1%): 80% Extra bullet, 10% Shotgun 40 pellets, Explosion x3, Buff 4 nearby allies.[]" : "\n[green]• Phúc lợi 5 (1%): 80% Bắn đạn phụ, 10% Shotgun 40 viên, Nổ x3 lần, Buff 4 pháo đồng minh gần nhất (Quét mỗi 5s + Tia điện hel & Vòng Zoom 2/3 size).[]";
    if (key.equals("info_p6")) return currentIsEn ? "\n[red]• Perk 6 (1%): +500% Base Damage, Bemod explosion applies 10 stacks for infinite chain explosions![]" : "\n[red]• Phúc lợi 6 (1%): +500% Sát thương gốc, Vụ nổ Bemod gán 10 tầng tạo chuỗi nổ dây chuyền vĩnh viễn![]";

    return "";
}

    private static void ringAt(float radius, float x, float y) {
        SplashFx.ring(x, y, radius);   // JS: tham số màu thứ 2 của createExplosionEffect bị bỏ qua (bài 85)
    }

    private static void triggerBemodExplosion(Building building, Unit targetUnit, int perkTier, boolean isMK2, boolean isSubExplosion) {
        triggerBemodExplosion(building, targetUnit, perkTier, isMK2, isSubExplosion, false);
    }

    private static void triggerBemodExplosion(Building building, Unit targetUnit, int perkTier, boolean isMK2,
                                              boolean isSubExplosion, boolean isCornerTurret) {
        if (targetUnit == null || !targetUnit.isValid()) return;

        final float explosionX = targetUnit.x;
        final float explosionY = targetUnit.y;

        float baseRadius = 150f;
        float er = baseRadius;
        if (isCornerTurret) er = 50f;
        else if (perkTier == 3) er = baseRadius * 1.5f;
        else if (perkTier == 4) er = baseRadius * 0.5f;
        final float explosionRadius = er;

        if (isCornerTurret) ringAt(50f, explosionX, explosionY);
        else if (perkTier == 6) ringAt(200f, explosionX, explosionY);
        else if (perkTier == 5) ringAt(150f, explosionX, explosionY);
        else if (perkTier == 4) ringAt(75f, explosionX, explosionY);
        else if (perkTier == 3) ringAt(225f, explosionX, explosionY);
        else ringAt(150f, explosionX, explosionY);
        Effect.shake(5f, 5f, explosionX, explosionY);

        final StatusEffect bemodStatus = Sta.bemod;

        targetUnit.unapply(bemodStatus);
        Sta.bemodStacks.remove(targetUnit.id);

        if (isCornerTurret) {
            Groups.unit.intersect(explosionX - 50f, explosionY - 50f, 100f, 100f, nearUnit -> {
                if (nearUnit != null && nearUnit.isValid() && nearUnit.within(explosionX, explosionY, 50f) && nearUnit.team != building.team) {
                    float totalDmg = 500f + (nearUnit.maxHealth() * 0.02f);
                    nearUnit.damage(totalDmg);
                }
            });
            return;
        }

        if (perkTier == 4) {
            Groups.unit.intersect(explosionX - explosionRadius, explosionY - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, nearUnit -> {
                if (nearUnit != null && nearUnit.isValid() && nearUnit.within(explosionX, explosionY, explosionRadius) && nearUnit.team != building.team) {
                    nearUnit.damage(500f);
                }
            });
        } else {
            float baseFlatDmg = isMK2 ? 500f : 100f;
            float basePercentDmg = isMK2 ? 0.03f : 0.01f;

            float dmgMult = 1f;
            if (perkTier == 2) dmgMult = 2.5f;
            if (perkTier == 3) dmgMult = 1.5f;

            final float finalFlatDmg = baseFlatDmg * dmgMult;
            final float finalPercentDmg = basePercentDmg * dmgMult;

            final int reqStacks = (perkTier == 1) ? 7 : 10;

            Groups.unit.intersect(explosionX - explosionRadius, explosionY - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, nearUnit -> {
                if (nearUnit != null && nearUnit.isValid() && nearUnit.within(explosionX, explosionY, explosionRadius) && nearUnit.team != building.team) {

                    float totalDamage = finalFlatDmg + (nearUnit.maxHealth() * finalPercentDmg);
                    nearUnit.damage(totalDamage);

                    if (!isSubExplosion && bemodStatus != null) {
                        int addStacks = 0;
                        if (perkTier == 1) addStacks = 10;
                        if (perkTier == 2) addStacks = 5;
                        if (perkTier == 6) addStacks = 10;

                        if (addStacks > 0) {
                            nearUnit.apply(bemodStatus, 60f * 10f);
                            int nid = nearUnit.id;
                            int now = Sta.bemodStacks.getOrDefault(nid, 0) + addStacks;
                            Sta.bemodStacks.put(nid, now);

                            if (now >= reqStacks) {
                                Time.run(1f, () -> {
                                    if (nearUnit != null && nearUnit.isValid()) {
                                        boolean subFlag = (perkTier == 6) ? false : true;
                                        triggerBemodExplosion(building, nearUnit, perkTier, isMK2, subFlag);
                                    }
                                });
                            }
                        }
                    }
                }
            });
        }

        if (perkTier == 5) {
            for (int extra = 1; extra <= 2; extra++) {
                Time.run(extra * 12f, () -> {
                    if (targetUnit != null) {
                        ringAt(150f, explosionX, explosionY);
                        Effect.shake(3f, 3f, explosionX, explosionY);

                        Groups.unit.intersect(explosionX - explosionRadius, explosionY - explosionRadius, explosionRadius * 2f, explosionRadius * 2f, nearUnit -> {
                            if (nearUnit != null && nearUnit.isValid() && nearUnit.within(explosionX, explosionY, explosionRadius) && nearUnit.team != building.team) {
                                nearUnit.damage(nearUnit.maxHealth() * 0.10f);
                            }
                        });
                    }
                });
            }
        }

        if (perkTier == 4 && !isSubExplosion) {
            final Unit[] furthestTarget = {null};
            final float[] maxDist = {-1f};

            Groups.unit.intersect(explosionX - 150f, explosionY - 150f, 300f, 300f, nearUnit -> {
                if (nearUnit != null && nearUnit.isValid() && nearUnit.id != targetUnit.id && nearUnit.within(explosionX, explosionY, 150f) && nearUnit.team != building.team) {
                    float dist = nearUnit.dst(explosionX, explosionY);
                    if (dist > maxDist[0]) {
                        maxDist[0] = dist;
                        furthestTarget[0] = nearUnit;
                    }
                }
            });

            if (furthestTarget[0] != null) {
                if (bemodStatus != null) {
                    furthestTarget[0].apply(bemodStatus, 60f * 10f);
                    Sta.bemodStacks.put(furthestTarget[0].id, 10);
                }
                triggerBemodExplosion(building, furthestTarget[0], perkTier, isMK2, true);
            }
        }
    }

    /** JS: createCustomBulletType — đạn gọi handleBulletHit của pháo chủ sau hitEntity. */
    static class IBullet extends BasicBulletType {
        @Override
        public void hitEntity(Bullet b, Hitboxc other, float initialHealth) {
            super.hitEntity(b, other, initialHealth);
            if (other instanceof Unit && b.owner instanceof IndeniterBuild) {
                ((IndeniterBuild) b.owner).handleBulletHit(b, (Unit) other);
            }
        }
    }

    private static IBullet makeBullet(float speed, float damage, float lifetime, float width, float height,
                                      Color front, Color back, float trailInterval) {
        IBullet b = new IBullet();
        b.speed = speed; b.damage = damage; b.lifetime = lifetime; b.width = width; b.height = height;
        b.frontColor = front; b.backColor = back;
        b.pierce = false;
        b.hitEffect = Fx.disperseTrail; b.despawnEffect = Fx.disperseTrail;
        b.trailEffect = bulletCircleTrailFx;
        b.trailInterval = trailInterval;
        return b;
    }

    public static void create() {
        bulletCircleTrailFx = new Effect(12f, e -> {
            Draw.z(Layer.bullet - 0.01f);

            Color col = Color.valueOf("#ffcc00");
            float randomMaxRadius = Mathf.randomSeed(e.id, 10, 20);
            float currentRadius = randomMaxRadius * Interp.pow2Out.apply(e.fin());
            float alpha = 0.6f * (1f - e.fin());

            Draw.color(col);
            Draw.alpha(alpha * 0.4f);
            Fill.circle(e.x, e.y, currentRadius);

            Draw.color(col);
            Draw.alpha(alpha);
            Lines.stroke(3f * (1f - e.fin()));
            Lines.circle(e.x, e.y, currentRadius);

            Draw.reset();
        });

        buffTurretPulseFx = new Effect(40f, e -> {
            Draw.z(Layer.effect + 0.05f);

            Color col = Color.valueOf("#00ffcc");
            float targetSize = (e.data instanceof Number) ? ((Number) e.data).floatValue() : 16f;
            float baseRadius = (targetSize * (2f / 3f)) / 2f;

            float pulse = (float) Math.sin(e.fin() * Math.PI * 2) * (baseRadius * 0.25f);
            float currentRadius = Math.max(2f, baseRadius + pulse);
            float alpha = 0.8f * (1f - e.fin());

            Draw.color(col);
            Draw.alpha(alpha * 0.25f);
            Fill.circle(e.x, e.y, currentRadius);

            Draw.color(col);
            Draw.alpha(alpha);
            Lines.stroke(2f * (1f - e.fin()));
            Lines.circle(e.x, e.y, currentRadius);

            Draw.reset();
        });

        hel = new Effect(35f, e -> {
            Draw.z(Layer.effect + 0.01f);

            if (!(e.data instanceof Vec2)) return;
            Vec2 d = (Vec2) e.data;

            float x1 = e.x, y1 = e.y;
            float x2 = d.x, y2 = d.y;
            float alpha = 1f - e.fin();

            float colorRand = Mathf.randomSeed(e.id + Mathf.floor(e.fin() * 10f));
            Draw.color(PerfCore.color("#00ffcc"), PerfCore.color("#ffffff"), colorRand);
            Lines.stroke(1.2f * alpha);

            int segments = 8;
            float lastX = x1, lastY = y1;

            for (int i = 1; i <= segments; i++) {
                float progress = i / (float) segments;
                float nx = Mathf.lerp(x1, x2, progress);
                float ny = Mathf.lerp(y1, y2, progress);

                if (i < segments) {
                    float offset = (1f - Math.abs(progress - 0.5f) * 2f) * 6f;
                    float randX = Mathf.randomSeed(e.id * 100L + i + Mathf.floor(e.fin() * 5f), -offset, offset);
                    float randY = Mathf.randomSeed(e.id * 200L + i + Mathf.floor(e.fin() * 5f), -offset, offset);
                    nx += randX;
                    ny += randY;
                }

                Lines.line(lastX, lastY, nx, ny);

                float chanceRand = Mathf.randomSeed(e.id * 300L + i);
                if (chanceRand < 0.3f) {
                    Fill.circle(nx, ny, 1f * alpha);
                }

                lastX = nx;
                lastY = ny;
            }

            Draw.reset();
        });

        perk5ShotgunBullet = new BasicBulletType();
        perk5ShotgunBullet.speed = 11f; perk5ShotgunBullet.damage = 18f; perk5ShotgunBullet.lifetime = 30f;
        perk5ShotgunBullet.width = 10f; perk5ShotgunBullet.height = 14f;
        perk5ShotgunBullet.pierce = true; perk5ShotgunBullet.pierceCap = 10; perk5ShotgunBullet.pierceBuilding = true;
        perk5ShotgunBullet.frontColor = Color.valueOf("#00ffff"); perk5ShotgunBullet.backColor = Color.valueOf("#0088ff");
        perk5ShotgunBullet.hitEffect = Fx.hitBulletSmall; perk5ShotgunBullet.despawnEffect = Fx.hitBulletSmall;

        indeniterBullet = makeBullet(8f, 9f, 35f, 16f, 16f, Color.white, Color.valueOf("#ff6b35"), 3f);
        indeniterMK2Bullet = makeBullet(9.5f, 12.15f, 40f, 20f, 20f, Color.valueOf("#ffcc00"), Color.valueOf("#ff3300"), 2.5f);
        indeniterMK2BBullet = makeBullet(8.5f, 9f, 90f, 20f, 20f, Color.white, Color.valueOf("#d63031"), 2f);
        indeniterMK2BBullet.homingPower = 0.25f;
        indeniterMK2BBullet.homingRange = 574f;

        turret = new ItemTurret("indeniter");
        turret.configurable = true;
        turret.category = Category.turret;
        turret.ammoTypes.put(Items.silicon, indeniterBullet);

        turret.config(Integer.class, (IndeniterBuild tile, Integer value) -> {
            if (tile != null) {
                int val = value;
                if (val >= 10) {
                    tile.setPerkTier(val - 10);
                } else {
                    tile.setTier(val);
                }
            }
        });

        turret.buildType = () -> new IndeniterBuild(turret);

        Events.on(UnitDamageEvent.class, e -> {
            Unit unit = e.unit;
            if (unit == null || !unit.isValid()) return;

            if (cornerBuffedTurrets.isEmpty()) return;
            Iterator<Map.Entry<Integer, IndeniterBuild>> it = cornerBuffedTurrets.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, IndeniterBuild> en = it.next();
                IndeniterBuild sourceIndeniter = en.getValue();
                // JS dùng Vars.world.build(id) với id thực thể (không phải vị trí ô) → luôn null; Java tra theo id thực thể (bài 86).
                Building build = Groups.build.getByID(en.getKey());

                if (build != null && build.isValid() && sourceIndeniter != null && sourceIndeniter.isValid()) {
                    float range = build instanceof ItemTurret.ItemTurretBuild ? ((ItemTurret.ItemTurretBuild) build).range() : 0f;
                    if (unit.team != build.team && build.team == sourceIndeniter.team && build.dst(unit) <= range + 20f) {
                        StatusEffect status = Sta.bemod;

                        if (status != null && Mathf.chance(0.35)) {
                            unit.apply(status, 60f * 10f);
                            int uid = unit.id;

                            int now = Sta.bemodStacks.getOrDefault(uid, 0) + 1;
                            Sta.bemodStacks.put(uid, now);

                            if (now >= 10) {
                                triggerBemodExplosion(build, unit, 0, false, true, true);
                            }
                        }
                    }
                } else {
                    it.remove();
                }
            }
        });
    }

    public static class IndeniterBuild extends ItemTurret.ItemTurretBuild {
        int tierState = 0;
        int perkTierState = 0;
        float customRecoil = 0f;
        float nonRecoil = 0f;
        float shootingVisual = 0f;
        float energyCharge = 0f;
        boolean isBursting = false;
        float subBulletTimer = 0f;

        public IndeniterBuild(ItemTurret outer) {
            outer.super();
        }

        int getPerkTier() { return perkTierState; }

        void setPerkTier(int val) {
            perkTierState = val;
            setTier(getTier());
        }

        @Override
        public BulletType peekAmmo() {
            int tier = getTier();
            if (tier == 1) return indeniterMK2Bullet;
            if (tier == 2) return indeniterMK2BBullet;
            return indeniterBullet;
        }

        int getTier() { return tierState; }

        void setTier(int val) {
            tierState = val;
            isBursting = false;

            int perk = getPerkTier();
            float pMult = (perk == 3) ? 1.5f : 1f;

            // JS còn gán `this.reload = 60/pMult…` nhưng Build v157 không có trường `reload` (bài 62/87) → bỏ.
            if (tierState == 0) health = Math.round(5500 * pMult);
            if (tierState == 1) health = Math.round(8250 * pMult);
            if (tierState == 2) health = Math.round(7333 * pMult);
            maxHealth = health;
        }

        @Override
        public float range() {
            int tier = getTier();
            float baseR = 410f;
            if (tier == 1) baseR = 574f;
            if (tier == 2) baseR = 1230f;

            return (getPerkTier() == 3) ? baseR * 1.5f : baseR;
        }

        void handleBulletHit(Bullet b, Unit other) {
            if (other == null) return;
            StatusEffect status = Sta.bemod;

            int perkTier = getPerkTier();
            float baseDmg = b.type.damage;
            float dmgMult = 1f;
            if (perkTier == 1) dmgMult += 2.15f;
            if (perkTier == 3) dmgMult += 0.5f;
            if (perkTier == 6) dmgMult += 5f;

            float finalDmg = baseDmg * dmgMult;
            other.damage(finalDmg);

            if (perkTier == 5) {
                if (Mathf.chance(0.8)) {
                    BulletType bulletType = peekAmmo();
                    bulletType.create(this, team, x, y, rotation + Mathf.range(5f));
                }

                if (Mathf.chance(0.1)) {
                    for (int i = 0; i < 40; i++) {
                        float spreadAngle = rotation + Mathf.range(8f);
                        Bullet bullet = perk5ShotgunBullet.create(this, team, x, y, spreadAngle);
                        if (bullet != null) {
                            bullet.vel.setLength(Mathf.random(15f, 45f));
                        }
                    }
                }
            }

            float chance = 0.3f;
            int tier = getTier();
            if (tier == 1) chance = 0.45f;
            if (tier == 2) chance = 1f;

            if (perkTier == 3) chance = Math.min(1f, chance * 1.5f);

            if (Mathf.chance(chance) && status != null) {
                other.apply(status, 60f * 10f);
                int id = other.id;
                int now = Sta.bemodStacks.getOrDefault(id, 0) + 1;
                Sta.bemodStacks.put(id, now);

                int reqStacks = (perkTier == 1) ? 7 : 10;

                if (now >= reqStacks) {
                    triggerBemodExplosion(this, other, perkTier, tier == 1, false);
                }
            }
        }

        @Override
        protected void shoot(BulletType type) {
            if (!hasAmmo()) return;

            int tier = getTier();

            if (tier == 2) {
                if (isBursting) return;
                isBursting = true;

                for (int i = 0; i < 10; i++) {
                    Time.run(i * 5f, () -> {
                        if (isValid() && hasAmmo()) {
                            super.shoot(type);
                            customRecoil = 1f;
                            nonRecoil = 1f;
                        }
                    });
                }

                Time.run(45f, () -> { if (isValid()) reloadCounter = 0f; });
                Time.run(225f, () -> { if (isValid()) isBursting = false; });
            } else {
                super.shoot(type);
                customRecoil = 1f;
                nonRecoil = 1f;
            }
        }

        @Override
        public void updateTile() {
            super.updateTile();

            customRecoil = Mathf.approach(customRecoil, 0f, 0.12f * Time.delta);
            nonRecoil = Mathf.approach(nonRecoil, 0f, 0.12f * Time.delta);

            // JS: `this.isShooting` thiếu `()` (luôn truthy, bài 88) → Java gọi isShooting() thật.
            boolean shooting = isShooting();
            boolean isTargeting = (target != null || shooting);
            float visualSpeed = 0.05f * Time.delta;
            if (isTargeting) {
                shootingVisual = Mathf.approach(shootingVisual, 1f, visualSpeed);
            } else {
                shootingVisual = Mathf.approach(shootingVisual, 0f, visualSpeed);
            }

            boolean activeShooting = (shooting || isBursting) && hasAmmo();
            float chargeSpeed = activeShooting ? 0.1f * Time.delta : 0.08f * Time.delta;
            energyCharge = Mathf.approach(energyCharge, activeShooting ? 1f : 0f, chargeSpeed);

            int perk = getPerkTier();

            if (perk == 4 && hasAmmo() && shooting) {
                subBulletTimer += Time.delta;
                if (subBulletTimer >= 42f) {
                    subBulletTimer = 0f;
                    float bulletAngle = rotation;
                    float offset = 4f;
                    float cos = (float) Math.cos(bulletAngle * Mathf.degRad);
                    float sin = (float) Math.sin(bulletAngle * Mathf.degRad);

                    BulletType bulletType = peekAmmo();
                    bulletType.create(this, team, x + sin * offset, y - cos * offset, bulletAngle);
                    bulletType.create(this, team, x - sin * offset, y + cos * offset, bulletAngle);
                }
            } else {
                subBulletTimer = 0f;
            }

            if (perk == 5 && timer.get(0, 300f)) {
                Seq<Building> candidates = new Seq<>();
                Set<Integer> processedIds = new HashSet<>();

                int tileX = tileX();
                int tileY = tileY();
                int radiusTiles = 4;

                for (int dx = -radiusTiles; dx <= radiusTiles; dx++) {
                    for (int dy = -radiusTiles; dy <= radiusTiles; dy++) {
                        Building otherBuild = Vars.world.build(tileX + dx, tileY + dy);

                        if (otherBuild != null && !processedIds.contains(otherBuild.id)) {
                            processedIds.add(otherBuild.id);

                            if (otherBuild != this && otherBuild.team == team && otherBuild instanceof ItemTurret.ItemTurretBuild) {
                                float d = dst(otherBuild);
                                if (d <= 32f) {
                                    candidates.add(otherBuild);
                                }
                            }
                        }
                    }
                }

                candidates.sort(b -> dst(b));

                int limit = Math.min(4, candidates.size);
                for (int i = 0; i < limit; i++) {
                    Building targetBuild = candidates.get(i);
                    cornerBuffedTurrets.put(targetBuild.id, this);

                    float turretPixelSize = targetBuild.block.size * Vars.tilesize;
                    buffTurretPulseFx.at(targetBuild.x, targetBuild.y, 0f, Float.valueOf(turretPixelSize));

                    hel.at(x, y, 0f, new Vec2(targetBuild.x, targetBuild.y));
                }
            }
        }

        /** Chuỗi "YÊU CẦU TÀI NGUYÊN" của hộp nâng cấp (chép nguyên văn từ JS). */
        private String reqSnapshot() {
            CoreBuild core = team.core();
            if (core == null) return tr("no_core");
            int cCop = core.items.get(Items.copper);
            int cLea = core.items.get(Items.lead);
            int cTit = core.items.get(Items.titanium);
            int cSil = core.items.get(Items.silicon);
            int cPla = core.items.get(Items.plastanium);

            String copCol = cCop >= reqSpecial.copper ? "[green]" : "[red]";
            String leaCol = cLea >= reqSpecial.lead ? "[green]" : "[red]";
            String silColSp = cSil >= reqSpecial.silicon ? "[green]" : "[red]";

            String titColor1 = cTit >= reqMK2.titanium ? "[green]" : "[red]";
            String silColor1 = cSil >= reqMK2.silicon ? "[green]" : "[red]";

            String titColor2 = cTit >= reqMK2B.titanium ? "[green]" : "[red]";
            String silColor2 = cSil >= reqMK2B.silicon ? "[green]" : "[red]";
            String plaColor2 = cPla >= reqMK2B.plastanium ? "[green]" : "[red]";

                return tr("req_core") +
                       tr("req_sp") + copCol + cCop + "[]/4000" + tr("req_lead") + leaCol + cLea + "[]/4000 | Silicon: " + silColSp + cSil + "[]/4000\n" +
                       tr("req_mk2") + titColor1 + cTit + "[]/" + reqMK2.titanium + " | Silicon: " + silColor1 + cSil + "[]/" + reqMK2.silicon + "\n" +
                       tr("req_mk2b") + titColor2 + cTit + "[]/" + reqMK2B.titanium + " | Silicon: " + silColor2 + cSil + "[]/" + reqMK2B.silicon + tr("req_plast") + plaColor2 + cPla + "[]/" + reqMK2B.plastanium;
        }

        private void openUpgradeDialog() {
            BaseDialog dialog = new BaseDialog(tr("dialog_title"));

            Cell<Label> reqCell = dialog.cont.add(reqSnapshot());
            reqCell.width(380f).get().setWrap(true);
            reqCell.get().setAlignment(Align.left);
            dialog.cont.row();
            dialog.cont.add().height(10f).row();

            Table branchesTable = new Table();

            Table spBox = new Table();
            spBox.background(Styles.black6);
            spBox.margin(12f);
            spBox.add(tr("sp_title")).row();

            int currentPerk = getPerkTier();
            int tier = getTier();

            if (currentPerk == 0) {
                Cell<Label> spD = spBox.add(tr("sp_desc"));
                spD.width(360f).get().setWrap(true);
                spD.get().setAlignment(Align.left);
                spBox.row();

                spBox.button(tr("btn_roll"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.copper) >= 4000 && core.items.get(Items.lead) >= 4000 && core.items.get(Items.silicon) >= 4000) {
                        NexNet.take(core, Items.copper, 4000);
                        NexNet.take(core, Items.lead, 4000);
                        NexNet.take(core, Items.silicon, 4000);

                        double rand = Mathf.random(100f);
                        int resultPerk;

                        if (rand < 1.0) {
                            resultPerk = 5;
                        } else if (rand < 2.0) {
                            resultPerk = 6;
                        } else if (rand < 2.0 + 19.6) {
                            resultPerk = 1;
                        } else if (rand < 2.0 + 19.6 + 29.4) {
                            resultPerk = 2;
                        } else if (rand < 2.0 + 19.6 + 29.4 + 19.6) {
                            resultPerk = 3;
                        } else {
                            resultPerk = 4;
                        }

                        setPerkTier(resultPerk);
                        configure(Integer.valueOf(10 + resultPerk));

                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.shake(6f, 6f, x, y);

                        String perkName = "";
                        if (resultPerk == 1) perkName = isEn() ? "[yellow]PERK 1[]" : "[yellow]PHÚC LỢI 1[]";
                        else if (resultPerk == 2) perkName = isEn() ? "[orange]PERK 2[]" : "[orange]PHÚC LỢI 2[]";
                        else if (resultPerk == 3) perkName = isEn() ? "[cyan]PERK 3[]" : "[cyan]PHÚC LỢI 3[]";
                        else if (resultPerk == 4) perkName = isEn() ? "[purple]PERK 4[]" : "[purple]PHÚC LỢI 4[]";
                        else if (resultPerk == 5) perkName = isEn() ? "[green]★ PERK 5 (1% SUPER RARE) ★[]" : "[green]★ PHÚC LỢI 5 (1% SIÊU HIẾM) ★[]";
                        else perkName = isEn() ? "[red]★ PERK 6 (1% SUPER RARE) ★[]" : "[red]★ PHÚC LỢI 6 (1% SIÊU HIẾM) ★[]";

                        Vars.ui.showInfo(tr("won") + perkName);

                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(tr("no_res_sp"));
                    }
                }).size(300f, 40f);
            } else {
                String perkText = "";
                if (currentPerk == 1) perkText = tr("active_p1");
                if (currentPerk == 2) perkText = tr("active_p2");
                if (currentPerk == 3) perkText = tr("active_p3");
                if (currentPerk == 4) perkText = tr("active_p4");
                if (currentPerk == 5) perkText = tr("active_p5");
                if (currentPerk == 6) perkText = tr("active_p6");

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
                Cell<Label> b1D = b1.add(tr("mk2_desc"));
                b1D.width(340f).get().setWrap(true);
                b1D.get().setAlignment(Align.left);
                b1.row();
                b1.button(tr("mk2_btn"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.titanium) >= reqMK2.titanium && core.items.get(Items.silicon) >= reqMK2.silicon) {
                        NexNet.take(core, Items.titanium, reqMK2.titanium);
                        NexNet.take(core, Items.silicon, reqMK2.silicon);

                        setTier(1);
                        configure(Integer.valueOf(1));

                        NexNet.fx(Fx.upgradeCore, x, y);
                        NexNet.fx(Fx.mineHuge, x, y);
                        NexNet.shake(4f, 4f, x, y);

                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(tr("no_res_mk2"));
                    }
                }).size(180f, 38f);

                Table b2 = new Table();
                b2.background(Styles.black6);
                b2.margin(12f);
                b2.add("[purple]===(MK2B)===[]").row();
                Cell<Label> b2D = b2.add(tr("mk2b_desc"));
                b2D.width(340f).get().setWrap(true);
                b2D.get().setAlignment(Align.left);
                b2.row();
                b2.button(tr("mk2b_btn"), () -> {
                    CoreBuild core = team.core();
                    if (core != null && core.items.get(Items.titanium) >= reqMK2B.titanium && core.items.get(Items.silicon) >= reqMK2B.silicon && core.items.get(Items.plastanium) >= reqMK2B.plastanium) {
                        NexNet.take(core, Items.titanium, reqMK2B.titanium);
                        NexNet.take(core, Items.silicon, reqMK2B.silicon);
                        NexNet.take(core, Items.plastanium, reqMK2B.plastanium);

                        setTier(2);
                        configure(Integer.valueOf(2));

                        NexNet.fx(Fx.bigShockwave, x, y);
                        NexNet.fx(Fx.mineHuge, x, y);
                        NexNet.shake(4f, 4f, x, y);

                        dialog.hide();
                        deselect();
                    } else {
                        Vars.ui.showInfo(tr("no_res_mk2b"));
                    }
                }).size(180f, 38f);

                branchesTable.add(b1).width(360f);
                branchesTable.row();
                branchesTable.add().height(12f).row();
                branchesTable.add(b2).width(360f);
            } else {
                String statusLabel = (tier == 1) ? tr("status_mk2") : tr("status_mk2b");
                branchesTable.add(statusLabel).row();
            }

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(420f);
            dialog.addCloseButton();
            dialog.show();
        }

        private void openInfoDialog() {
            String title = tr("info_title");
            String descStr = "";
            int currentTier = getTier();

            if (currentTier == 0) descStr = tr("info_mk1");
            else if (currentTier == 1) descStr = tr("info_mk2");
            else if (currentTier == 2) descStr = tr("info_mk2b");

            int perk = getPerkTier();
            if (perk > 0) {
                descStr += tr("info_perk_header");
                if (perk == 1) descStr += tr("info_p1");
                if (perk == 2) descStr += tr("info_p2");
                if (perk == 3) descStr += tr("info_p3");
                if (perk == 4) descStr += tr("info_p4");
                if (perk == 5) descStr += tr("info_p5");
                if (perk == 6) descStr += tr("info_p6");
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

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();

            table.button(Icon.upOpen, Styles.cleari, 40, this::openUpgradeDialog)
                .size(50f, 40f).tooltip(tr("dialog_title"));

            table.button(Icon.info, Styles.cleari, 40, this::openInfoDialog)
                .size(50f, 40f).tooltip(isEn() ? "View detailed system stats" : "Xem thông số chi tiết hệ thống");
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void draw() {
            if (!regionsReady) {
                regionsReady = true;
                rWing1 = reg("indeniter-wing1");
                rWing2 = reg("indeniter-wing2");
                rNon = reg("indeniter-non");
                rBarrel1 = reg("indeniter-barrel1");
                rBarrel2 = reg("indeniter-barrel2");
                rB1 = reg("indeniter-b1");
            }

            // JS: `block.basePrefix + size` = "undefined4" không bao giờ thấy → luôn super.draw() (bài 13).
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            float baseZ = Layer.turret;
            Draw.z(baseZ - 0.01f);

            float wingSpread = shootingVisual * 15f;

            if (rWing1.found()) {
                float w1x = x - wingSpread * sin;
                float w1y = y + wingSpread * cos;
                Draw.rect(rWing1, w1x, w1y, rotation);
            }

            if (rWing2.found()) {
                float w2x = x + wingSpread * sin;
                float w2y = y - wingSpread * cos;
                Draw.rect(rWing2, w2x, w2y, rotation);
            }

            float maxNonRecoilDistance = -8f;
            float nonRecoilOffset = nonRecoil * maxNonRecoilDistance;
            float nonBaseBack = -8.8f;
            float nonBaseLeft = 8.8f;

            if (rNon.found()) {
                float nonX = x + (nonBaseBack + nonRecoilOffset) * cos - nonBaseLeft * sin;
                float nonY = y + (nonBaseBack + nonRecoilOffset) * sin + nonBaseLeft * cos;

                Draw.rect(rNon, nonX, nonY, rotation);
            }

            Draw.z(baseZ);

            if (rBarrel1.found()) Draw.rect(rBarrel1, x, y, rotation);
            if (rBarrel2.found()) Draw.rect(rBarrel2, x, y, rotation);

            float b1Offset = customRecoil * -5f;
            if (rB1.found()) {
                float b1ax = x + b1Offset * cos;
                float b1ay = y + b1Offset * sin;
                Draw.rect(rB1, b1ax, b1ay, rotation);
            }

            if (energyCharge > 0.001f) {
                Draw.z(Layer.turret + 0.01f);

                BulletType bulletType = peekAmmo();
                Color energyColor = (bulletType instanceof BasicBulletType && ((BasicBulletType) bulletType).backColor != null)
                    ? ((BasicBulletType) bulletType).backColor : Color.valueOf("#ffcc00");

                float barrelOffset = 14f + (customRecoil * -5f);
                float ballX = x + barrelOffset * cos;
                float ballY = y + barrelOffset * sin;

                float pulse = Mathf.absin(Time.time, 3f, 1.5f);
                float baseRadius = (5f + pulse) * energyCharge;

                Draw.color(energyColor);
                Draw.alpha(0.35f * energyCharge);
                Fill.circle(ballX, ballY, baseRadius * 1.8f);

                Draw.color(energyColor);
                Draw.alpha(0.8f * energyCharge);
                Lines.stroke(1.5f * energyCharge);
                Lines.circle(ballX, ballY, baseRadius * 1.3f);

                Draw.color(Color.white);
                Draw.alpha(0.9f * energyCharge);
                Fill.circle(ballX, ballY, baseRadius * 0.7f);
            }

            Draw.reset();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
            write.b(getPerkTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
            if (revision >= 1) {
                setPerkTier(read.b());
            } else {
                perkTierState = 0;
            }
            customRecoil = 0f;
            nonRecoil = 0f;
            shootingVisual = 0f;
            energyCharge = 0f;
            isBursting = false;
            subBulletTimer = 0f;
        }
    }
}
