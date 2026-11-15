package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawMulti;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import newex.NexNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Chuyển từ survivor-skills.js (1493 dòng). Hệ thống "pháo kĩ năng" (orbiter) cho chế độ
 * {@link Survival}: mỗi khi lên cấp, người chơi được chọn 1 trong 3 thẻ (pháo mới hoặc +1 chỉ số).
 * Mỗi pháo được chọn bay lượn quanh unit của người chơi như drone và tự bắn kẻ địch; nếu tìm được
 * khối turret thật (newex-&lt;id&gt;) trong content thì dùng building ảo (không gắn tile thật) để
 * mô phỏng hành vi 1:1 như turret gốc ("real"), nếu lỗi liên tục thì rơi về bắn đạn đơn giản
 * ("simple") rồi thử quay lại "real" sau một thời gian.
 *
 * CHƯA BIÊN DỊCH THẬT (bài 93/95/103): sandbox không tải được server-release.jar nên chỉ kiểm cú
 * pháp thủ công. Khối nghi vấn nhất là {@link #createVirtual} (building không gắn tile thật) và các
 * trường LightningBulletType (lightningLength/lightningLengthRand/lightningColor) — xem ghi chú
 * MIGRATION.md khi build thật.
 */
public final class SurvivorSkills {
    private SurvivorSkills() {}

    // ------------------------------------------------------------ cấu hình
    static final int KILL_CREDIT_RADIUS = 260;
    static final int SKILL_CHOICE_COUNT = 3;
    static final int MAX_TIER_SHOTS = 4;
    static final int MAX_BULLETS_PER_VOLLEY = 12;
    static final float MIN_RELOAD = 6f;

    static final float DRONE_R_MIN = 22f, DRONE_R_MAX = 66f;
    static final float DRONE_WANDER_MIN = 45f, DRONE_WANDER_MAX = 140f;
    static final float DRONE_SPRING = 0.012f, DRONE_DRAG = 0.92f, DRONE_MAX_SPEED = 4.2f;
    static final float DRONE_LEASH = 150f, DRONE_TELEPORT = 520f, DRONE_SEPARATION = 16f;

    static final boolean REAL_TURRETS = true;
    static final float MAX_CREDIT_RADIUS = 2000f;

    static boolean isEn() { return Core.settings.getString("locale", "en").startsWith("en"); }

    // ------------------------------------------------------------ dữ liệu pháo (SKILL_SRC)
    static final class Variant {
        Float dmg, w, splash, radius;
        String back, status;
        float statusTime = 90, pierce = 0;
    }

    static final class SkillSrc {
        String id, vi, en, kind = "bullet", front = "ffffff", back = "ffffff", mid, status;
        float speed, dmg, life, w = 7, h = 10, len, pierce, knock;
        float reload = 20, range = 300, homing, homingRange = 160, splash, radius = 30;
        float statusTime = 90, lightning, lightningLen = 6, lightningDmg = -1;
        float pellets = 1, spread = 4;
        boolean cycle = false;
        List<Variant> variants;
    }

    static Variant v(float dmg, float w) { Variant r = new Variant(); r.dmg = dmg; r.w = w; return r; }

    static Variant v(float dmg, String back, String status, float statusTime) {
        Variant r = new Variant(); r.dmg = dmg; r.back = back; r.status = status; r.statusTime = statusTime; return r;
    }

    static SkillSrc sk(String id, String vi, String en, Consumer<SkillSrc> cfg) {
        SkillSrc s = new SkillSrc();
        s.id = id; s.vi = vi; s.en = en;
        cfg.accept(s);
        return s;
    }

    static final List<SkillSrc> SKILL_SRC = new ArrayList<>();
    static {
        SKILL_SRC.add(sk("nucleytor", "Đạn xuyên 3 mục tiêu", "Pierces 3 targets", s -> {
            s.speed = 8; s.dmg = 24; s.life = 55; s.w = 9; s.h = 14; s.back = "ffef9e"; s.pierce = 3; s.knock = 0.5f; s.reload = 22; s.range = 440;
        }));
        SKILL_SRC.add(sk("emperfum", "Đạn nặng nổ diện rộng, chậm", "Heavy slow shell, big blast", s -> {
            s.speed = 6; s.dmg = 40; s.life = 75; s.w = 12; s.h = 12; s.back = "c77dff"; s.splash = 90; s.radius = 44; s.homing = 0.03f; s.reload = 75; s.range = 430;
        }));
        SKILL_SRC.add(sk("galaxvorram", "Laser lớn, sát thương cao", "Big laser, high damage", s -> {
            s.kind = "laser"; s.dmg = 85; s.len = 330; s.w = 16; s.back = "ff4444"; s.mid = "ff8866"; s.spread = 3; s.reload = 110; s.range = 320;
        }));
        SKILL_SRC.add(sk("dor", "Đạn nhanh, hơi tự dẫn", "Fast shots, slight homing", s -> {
            s.speed = 7; s.dmg = 16; s.life = 45; s.w = 7; s.h = 18; s.front = "e0f7fa"; s.back = "00bcd4"; s.homing = 0.05f; s.reload = 16; s.range = 300;
        }));
        SKILL_SRC.add(sk("flazerd", "Laser mảnh bắn dồn dập", "Rapid thin laser", s -> {
            s.kind = "laser"; s.dmg = 14; s.len = 250; s.w = 6; s.back = "ff9d3a"; s.mid = "ffd27f"; s.spread = 3; s.reload = 12; s.range = 240;
        }));
        SKILL_SRC.add(sk("erysidus", "Tên lửa tự dẫn tầm xa", "Long-range homing missile", s -> {
            s.kind = "missile"; s.speed = 5; s.dmg = 22; s.life = 100; s.w = 8; s.h = 12; s.front = "e3f2fd"; s.back = "90caf9"; s.splash = 30; s.radius = 28; s.homing = 0.09f; s.homingRange = 200; s.reload = 40; s.range = 480;
        }));
        SKILL_SRC.add(sk("blixalum", "Đạn điện nổ lan, gây giật", "Shock blast, area damage", s -> {
            s.speed = 8; s.dmg = 14; s.life = 50; s.w = 10; s.h = 14; s.back = "a1ff9a"; s.splash = 34; s.radius = 36; s.status = "shocked"; s.statusTime = 60; s.reload = 24; s.range = 380;
        }));
        SKILL_SRC.add(sk("heliyron", "Đạn nặng tự dẫn mạnh", "Heavy homing shots", s -> {
            s.speed = 12; s.dmg = 38; s.life = 36; s.w = 7; s.h = 30; s.front = "e0f7fa"; s.back = "d47c00"; s.homing = 0.1f; s.homingRange = 150; s.reload = 26; s.range = 410;
        }));
        SKILL_SRC.add(sk("persefer", "Gatling bắn cực nhanh", "Very fast gatling", s -> {
            s.speed = 14; s.dmg = 8; s.life = 28; s.w = 5; s.h = 12; s.front = "ffd27f"; s.back = "ff5a3a"; s.pierce = 2; s.reload = 7; s.range = 370;
        }));
        SKILL_SRC.add(sk("tankani2k", "Phát đạn mạnh, xuyên 2", "Strong shot, pierces 2", s -> {
            s.speed = 15; s.dmg = 105; s.life = 30; s.w = 10; s.h = 33; s.front = "e0ea87"; s.back = "e5ff00"; s.pierce = 2; s.reload = 70; s.range = 420;
        }));
        SKILL_SRC.add(sk("zoj", "Đạn nhẹ tự dẫn, bắn dày", "Light homing, rapid fire", s -> {
            s.speed = 10; s.dmg = 8; s.life = 28; s.w = 4; s.h = 12; s.front = "e0ea87"; s.back = "e5ff00"; s.homing = 0.25f; s.homingRange = 200; s.reload = 9; s.range = 260;
        }));
        SKILL_SRC.add(sk("reflecounum", "Đạn phản chiếu xuyên 4", "Reflected shot, pierces 4", s -> {
            s.speed = 8; s.dmg = 20; s.life = 45; s.w = 8; s.h = 12; s.back = "7ee9ff"; s.pierce = 4; s.homing = 0.1f; s.reload = 26; s.range = 330;
        }));
        SKILL_SRC.add(sk("swordoder", "Nhát chém xuyên, sát thương lớn", "Piercing slash, heavy damage", s -> {
            s.speed = 7; s.dmg = 60; s.life = 30; s.w = 22; s.h = 22; s.back = "ff4d4d"; s.pierce = 5; s.reload = 55; s.range = 195;
        }));
        SKILL_SRC.add(sk("buffetles", "Đạn xuyên 2, bắn đều", "Steady shots, pierce 2", s -> {
            s.speed = 9; s.dmg = 26; s.life = 45; s.w = 5; s.h = 15; s.front = "dae1ee"; s.back = "7d89d8"; s.pierce = 2; s.reload = 22; s.range = 370;
        }));
        SKILL_SRC.add(sk("maxitoner", "Đạn thủy tinh làm chậm", "Glass shots slow enemies", s -> {
            s.speed = 7; s.dmg = 14; s.life = 50; s.w = 8; s.h = 12; s.front = "e2f3f5"; s.back = "22d1ee"; s.status = "slow"; s.statusTime = 90; s.reload = 28; s.range = 320;
        }));
        SKILL_SRC.add(sk("xylaon", "Đạn nóng, gây cháy", "Hot shots, burn enemies", s -> {
            s.speed = 11; s.dmg = 9; s.life = 32; s.w = 4; s.h = 12; s.front = "ffe2a8"; s.back = "feb380"; s.status = "burning"; s.statusTime = 120; s.reload = 8; s.range = 320;
        }));
        SKILL_SRC.add(sk("lyvervon", "Sét hồ quang liên hoàn", "Chain arc lightning", s -> {
            s.kind = "lightning"; s.dmg = 20; s.len = 40; s.back = "9be7ff"; s.reload = 24; s.range = 140;
        }));
        SKILL_SRC.add(sk("vendicum", "Đạn xuyên 3, rất mạnh", "Strong shot, pierces 3", s -> {
            s.speed = 9; s.dmg = 72; s.life = 45; s.w = 11; s.h = 16; s.back = "e0b080"; s.pierce = 3; s.knock = 1; s.reload = 55; s.range = 380;
        }));
        SKILL_SRC.add(sk("drone-launcher", "Đạn drone đuổi theo địch", "Drone shots chase enemies", s -> {
            s.speed = 5; s.dmg = 11; s.life = 110; s.w = 6; s.h = 6; s.back = "9fd7ff"; s.homing = 0.3f; s.homingRange = 350; s.reload = 12; s.range = 500;
        }));
        SKILL_SRC.add(sk("holyder", "Đạn cực nhanh, xuyên 4", "Ultra-fast, pierces 4", s -> {
            s.speed = 28; s.dmg = 46; s.life = 18; s.w = 12; s.h = 16; s.back = "7ad0ff"; s.pierce = 4; s.reload = 40; s.range = 470;
        }));
        SKILL_SRC.add(sk("plasanod", "Plasma xuyên, làm nóng chảy", "Plasma pierce + melt", s -> {
            s.speed = 9; s.dmg = 30; s.life = 40; s.w = 12; s.h = 22; s.front = "ffcc66"; s.back = "e67e22"; s.pierce = 5; s.splash = 20; s.radius = 20; s.status = "melting"; s.statusTime = 90; s.reload = 26; s.range = 340;
        }));
        SKILL_SRC.add(sk("tankani4k", "Pháo nặng, nổ lan", "Heavy shell, area blast", s -> {
            s.speed = 14; s.dmg = 120; s.life = 34; s.w = 10; s.h = 33; s.front = "e0ea87"; s.back = "00ff4c"; s.splash = 100; s.radius = 46; s.reload = 80; s.range = 440;
        }));
        SKILL_SRC.add(sk("therdum", "Shotgun tầm gần", "Close-range shotgun", s -> {
            s.speed = 9; s.dmg = 10; s.life = 14; s.w = 6; s.h = 10; s.front = "ffaa00"; s.back = "ff5500"; s.pellets = 3; s.spread = 16; s.reload = 38; s.range = 115;
        }));
        SKILL_SRC.add(sk("lazash", "Laser đỏ, sát thương vừa", "Red laser, steady damage", s -> {
            s.kind = "laser"; s.dmg = 24; s.len = 270; s.w = 8; s.back = "ff4444"; s.mid = "ff8866"; s.spread = 3; s.reload = 30; s.range = 260;
        }));
        SKILL_SRC.add(sk("forstarsilum", "Kiếm sao xuyên 5 mục tiêu", "Star sword, pierces 5", s -> {
            s.speed = 10; s.dmg = 40; s.life = 45; s.w = 14; s.h = 34; s.front = "bbdefb"; s.back = "90caf9"; s.pierce = 5; s.reload = 50; s.range = 420;
        }));
        SKILL_SRC.add(sk("hitekalum", "Đạn điện kèm tia sét", "Shock shot + lightning", s -> {
            s.speed = 9; s.dmg = 18; s.life = 42; s.w = 8; s.h = 12; s.back = "ff5a5a"; s.status = "shocked"; s.statusTime = 60; s.lightning = 2; s.lightningLen = 6; s.lightningDmg = 12; s.reload = 26; s.range = 350;
        }));
        SKILL_SRC.add(sk("reguilater", "Laser sát thương ngẫu nhiên", "Laser, random damage", s -> {
            s.kind = "laser"; s.len = 220; s.back = "ff3333"; s.mid = "ff8080"; s.spread = 3; s.reload = 28; s.range = 215;
            s.variants = List.of(v(20, 6), v(40, 8), v(75, 12));
        }));
        SKILL_SRC.add(sk("lavunder", "Đạn photon nổ quang phổ", "Photon blast shots", s -> {
            s.speed = 8; s.dmg = 14; s.life = 40; s.w = 10; s.h = 10; s.back = "c7a0ff"; s.splash = 46; s.radius = 38; s.homing = 0.03f; s.reload = 26; s.range = 300;
        }));
        SKILL_SRC.add(sk("blaw", "Luân phiên đạn Băng / Hỏa", "Alternates Ice / Fire", s -> {
            s.cycle = true; s.speed = 6; s.life = 60; s.w = 9; s.h = 22; s.reload = 20; s.range = 340;
            Variant ice = new Variant(); ice.dmg = 20f; ice.back = "6fa8ff"; ice.status = "freezing"; ice.statusTime = 120; ice.pierce = 2;
            Variant fire = v(16, "ff5a2a", "burning", 100); fire.splash = 40f; fire.radius = 30f;
            s.variants = List.of(ice, fire);
        }));
        SKILL_SRC.add(sk("dtg-soldern", "Chùm 3 đạn tản nhẹ", "3-shot light spread", s -> {
            s.speed = 8; s.dmg = 13; s.life = 40; s.w = 4; s.h = 10; s.front = "e0f7fa"; s.back = "00bcd4"; s.pellets = 3; s.spread = 9; s.reload = 22; s.range = 300;
        }));
        SKILL_SRC.add(sk("indeniter", "Đạn xuyên, nổ lan", "Piercing shot, splash", s -> {
            s.speed = 11; s.dmg = 20; s.life = 34; s.w = 10; s.h = 14; s.front = "00ffff"; s.back = "0088ff"; s.pierce = 6; s.splash = 24; s.radius = 28; s.reload = 26; s.range = 350;
        }));
        SKILL_SRC.add(sk("rangtaturs", "Đạn mảnh 7 hiệu ứng ngẫu nhiên", "Random 7-effect shards", s -> {
            s.speed = 7; s.dmg = 12; s.life = 45; s.w = 5; s.h = 12; s.splash = 14; s.radius = 24; s.pellets = 2; s.spread = 14; s.reload = 16; s.range = 290;
            s.variants = List.of(
                v(0, "ffab40", "blasted", 80), v(0, "ff6d00", "burning", 120), v(0, "8fd0ff", "freezing", 120),
                v(0, "fff27a", "shocked", 80), v(0, "4d9aff", "wet", 120), v(0, "a6ff6a", "corroded", 120), v(0, "ffa726", "melting", 90)
            );
        }));
        SKILL_SRC.add(sk("tyber", "3 tên lửa tầm nhiệt", "3 heat-seeking missiles", s -> {
            s.kind = "missile"; s.speed = 4.5f; s.dmg = 14; s.life = 90; s.w = 8; s.h = 12; s.back = "ffb74d"; s.splash = 40; s.radius = 34; s.homing = 0.12f; s.homingRange = 220; s.pellets = 3; s.spread = 12; s.reload = 60; s.range = 380;
        }));
        SKILL_SRC.add(sk("endyr", "Chùm 4 đạn năng lượng nổ", "4 energy blasts", s -> {
            s.speed = 6; s.dmg = 8; s.life = 55; s.w = 8; s.h = 8; s.back = "9be7ff"; s.splash = 30; s.radius = 30; s.pellets = 4; s.spread = 24; s.reload = 55; s.range = 310;
        }));
        SKILL_SRC.add(sk("sonicor", "Sóng âm xuyên, đẩy lùi", "Sonic wave, knockback", s -> {
            s.speed = 10; s.dmg = 7; s.life = 30; s.w = 14; s.h = 4; s.back = "cfe9ff"; s.pierce = 6; s.knock = 5; s.reload = 20; s.range = 280;
        }));
        SKILL_SRC.add(sk("spawner", "Mảnh tinh thể tự dẫn", "Homing crystal shards", s -> {
            s.speed = 3.5f; s.dmg = 16; s.life = 130; s.w = 7; s.h = 7; s.back = "c8ff7a"; s.homing = 0.22f; s.homingRange = 260; s.pellets = 2; s.spread = 40; s.reload = 28; s.range = 400;
        }));
    }

    // ------------------------------------------------------------ chỉ số cộng điểm (STAT_SRC)
    static final class StatSrc {
        String id, stat, vi, en, vid, end;
    }

    static StatSrc stat(String id, String st, String vi, String en, String vid, String end) {
        StatSrc s = new StatSrc(); s.id = id; s.stat = st; s.vi = vi; s.en = en; s.vid = vid; s.end = end; return s;
    }

    // Lưu ý: JS khai thêm trường "icon" cho mỗi STAT_SRC nhưng không nơi nào trong file đọc lại
    // (grep ".icon" ra rỗng) -> dữ liệu chết, Java bỏ hẳn trường này (xem MIGRATION.md).
    static final List<StatSrc> STAT_SRC = List.of(
        stat("stat-hp", "hp", "Máu", "Health", "Tăng [green]6%[] máu tối đa của unit", "Increases unit max health by [green]6%[]"),
        stat("stat-armor", "armor", "Giáp", "Armor", "Tăng [green]+1.2[] giáp cho unit", "Increases unit armor by [green]+1.2[]"),
        stat("stat-build", "build", "Tốc độ xây", "Build Speed", "Tăng [green]8%[] tốc độ xây dựng", "Increases build speed by [green]8%[]"),
        stat("stat-dmg", "dmg", "Sát thương", "Damage", "Tăng [green]8%[] sát thương các pháo kĩ năng", "Increases skill-cannon damage by [green]8%[]"),
        stat("stat-firerate", "firerate", "Tốc độ bắn", "Fire Rate", "Tăng [green]7%[] tốc độ bắn các pháo kĩ năng", "Increases skill-cannon fire rate by [green]7%[]"),
        stat("stat-range", "range", "Phạm vi bắn", "Range", "Tăng [green]6%[] tầm bắn các pháo kĩ năng", "Increases skill-cannon range by [green]6%[]"),
        stat("stat-crit-chance", "critChance", "Tỉ lệ bạo kích", "Crit Chance", "Tăng [green]4%[] tỉ lệ bạo kích", "Increases crit chance by [green]4%[]"),
        stat("stat-crit-dmg", "critDmg", "Sát thương bạo kích", "Crit Damage", "Tăng [green]15%[] sát thương khi bạo kích", "Increases crit damage bonus by [green]15%[]"),
        stat("stat-shred", "shred", "Giảm giáp mục tiêu", "Armor Shred", "Giảm [green]1.0[] giáp của mục tiêu bị bắn trúng", "Reduces target armor by [green]1.0[] on hit")
    );

    static final Map<String, Float> STAT_PER_POINT = new HashMap<>();
    static {
        STAT_PER_POINT.put("hp", 0.06f);
        STAT_PER_POINT.put("armor", 1.2f);
        STAT_PER_POINT.put("build", 0.08f);
        STAT_PER_POINT.put("dmg", 0.08f);
        STAT_PER_POINT.put("firerate", 0.07f);
        STAT_PER_POINT.put("range", 0.06f);
        STAT_PER_POINT.put("critChance", 0.04f);
        STAT_PER_POINT.put("critDmg", 0.15f);
        STAT_PER_POINT.put("shred", 1.0f);
    }
    static final String[] STAT_KEYS = {"hp", "armor", "build", "dmg", "firerate", "range", "critChance", "critDmg", "shred"};

    static String statDesc(StatSrc s) { return isEn() ? s.end : s.vid; }
    static String statName(StatSrc s) { return isEn() ? s.en : s.vi; }

    static final Map<String, String[]> DESC = new HashMap<>();
    static {
        DESC.put("nucleytor", new String[]{"Bắn tích năng lượng, đầy 100% thì quá tải: tăng tốc bắn", "Charges up while firing; at 100% it overloads for big buffs"});
        DESC.put("emperfum", new String[]{"Pháo hạng nặng bắn chậm, đạn cực mạnh", "Heavy slow-firing cannon, very strong shells"});
        DESC.put("galaxvorram", new String[]{"Pháo khổng lồ bắn đạn hủy diệt, nhiều loại đạn", "Huge cannon with devastating multi-type shots"});
        DESC.put("dor", new String[]{"Pháo đầu tay: bắn thường xen loạt chùm, đủ loạt vào Cuồng Bạo", "Starter gun: normal shots + burst volleys, enters Frenzy"});
        DESC.put("flazerd", new String[]{"Laser khóa mục tiêu, bắn càng lâu càng mạnh", "Locked-on laser, grows stronger the longer it fires"});
        DESC.put("erysidus", new String[]{"Tên lửa quét mục tiêu, đủ năng lượng tung chưởng Repulsor", "Scanning missiles; charged Repulsor blast (+200% vs air)"});
        DESC.put("blixalum", new String[]{"Đạn điện từ nổ diện rộng, địch càng đông bắn càng nhanh", "Electromagnetic area blasts, fires faster vs crowds"});
        DESC.put("heliyron", new String[]{"Mất máu càng nhiều càng mạnh, bắn mưa đạn truy đuổi", "Stronger as it loses HP; homing bullet rain"});
        DESC.put("persefer", new String[]{"Gatling 6 nòng, bắn càng lâu càng nhanh", "6-barrel gatling that spins up the longer it fires"});
        DESC.put("tankani2k", new String[]{"Bắn chính xác 1.115 sát thương, trúng liên tục thì sạc thêm", "Precise 1,115-dmg shots, charges up on consecutive hits"});
        DESC.put("zoj", new String[]{"Pháo làm vội: bắn trúng nhận Copper cho lõi", "Cheap turret: each hit gives Copper to the core"});
        DESC.put("reflecounum", new String[]{"Dựng khiên năng lượng chặn và phản đạn địch", "Energy shield that blocks and reflects enemy bullets"});
        DESC.put("swordoder", new String[]{"Pháo kiếm: bắn đạn và chém, tận diệt mục tiêu dưới 15% máu", "Sword turret: shots + slashes, executes targets under 15% HP"});
        DESC.put("buffetles", new String[]{"Dùng nhiều loại đạn, có thể bùng nổ sức mạnh trong chốc lát", "Multi-ammo turret with a short burst of power"});
        DESC.put("maxitoner", new String[]{"Đạn thủy tinh tích tầng Nitoui, đủ tầng nổ diện rộng", "Glass shots stack Nitoui; full stacks explode in an area"});
        DESC.put("xylaon", new String[]{"Bắn càng lâu càng nóng, tốc bắn tối đa +350%", "Heats up while firing, up to +350% fire rate"});
        DESC.put("lyvervon", new String[]{"Cột thu lôi: hồ quang chính xác, bắn liên tục", "Lightning rod: precise arc strikes, constant fire"});
        DESC.put("vendicum", new String[]{"Đầy năng lượng: +1000% sát thương, +250% tốc bắn", "At full charge: +1000% damage, +250% fire rate"});
        DESC.put("drone-launcher", new String[]{"Triệu hồi drone chiến đấu, tự hủy sau 10 giây", "Launches combat drones that self-destruct after 10s"});
        DESC.put("holyder", new String[]{"Tự sạc tăng sát thương, dựng khiên chặn đạn", "Self-charging heavy energy cannon with a bullet shield"});
        DESC.put("plasanod", new String[]{"Hai tia laser quét liên tục, ngắm lâu gọi sét nổ", "Twin sweeping lasers; long aim calls blast lightning"});
        DESC.put("tankani4k", new String[]{"Bản tối thượng Tankani, 1.673 sát thương, sạc đầy xuyên giáp", "Ultimate Tankani, 1,673 dmg, armor-piercing when fully charged"});
        DESC.put("therdum", new String[]{"Shotgun tầm ngắn", "Close-range shotgun"});
        DESC.put("lazash", new String[]{"Chiếu laser vào mục tiêu", "Beam laser on targets"});
        DESC.put("forstarsilum", new String[]{"Đạn Starsword chính xác, bắn liên tục tích buff", "Accurate Starsword shots; sustained fire stacks buffs"});
        DESC.put("hitekalum", new String[]{"Pháo điện từ cao cấp, sát thương rất lớn", "Advanced electromagnetic cannon, very high damage"});
        DESC.put("reguilater", new String[]{"Ngắm tích sức rồi bắn laser mạnh, sát thương ngẫu nhiên", "Charges its aim, then fires a strong random-damage laser"});
        DESC.put("lavunder", new String[]{"Laser Photon mạnh dần, đầy năng lượng nổ quang phổ lớn", "Photon laser that strengthens; big blast at full charge"});
        DESC.put("blaw", new String[]{"Luân phiên đạn Băng / Hỏa, làm chậm và nổ diện rộng", "Alternates Ice / Fire shells: slow + area damage"});
        DESC.put("dtg-soldern", new String[]{"Đạn đơn khi địch ở xa, shotgun khi địch tới gần", "Single shots at range, shotgun up close"});
        DESC.put("indeniter", new String[]{"Tầm xa nổ lớn, tích tầng Bemod rồi phát nổ lan", "Long-range blasts; Bemod stacks detonate and spread"});
        DESC.put("rangtaturs", new String[]{"Shotgun đạn mảnh 7 hiệu ứng ngẫu nhiên, có Cuồng Nộ", "Shotgun of random 7-effect shards, with Rage mode"});
        DESC.put("tyber", new String[]{"Tên lửa 3 nòng, tự bắn tầm nhiệt và nổ xung kích", "3-barrel missiles, extra heat-seekers and shock pulses"});
        DESC.put("endyr", new String[]{"Xả đạn năng lượng / laser diện rộng vào vùng mục tiêu", "Area energy and laser barrages on a target zone"});
        DESC.put("sonicor", new String[]{"Sóng âm xuyên, đẩy lùi mục tiêu (bản rút gọn)", "Piercing sonic wave with knockback (simplified)"});
        DESC.put("spawner", new String[]{"Mảnh tinh thể tự dẫn (bản rút gọn)", "Homing crystal shards (simplified)"});
    }

    static String descOf(SkillDef def) {
        String[] d = DESC.get(def.name);
        if (d != null) return isEn() ? d[1] : d[0];
        return isEn() ? def.en : def.vi;
    }

    // ------------------------------------------------------------ pool / def
    static final class SkillDef {
        String name, vi, en;
        SkillSrc src;
        Block block;
        Color color;
        float reload, range, pellets, spread;
        boolean cycle, isTurret, isItem;
        List<BulletType> bullets;
        Item ammoItem; // null = chưa tra; dùng ammoResolved để phân biệt "đã tra nhưng không có"
        boolean ammoResolved = false;
    }

    static List<SkillDef> skillPool;

    static List<SkillDef> buildSkillPool() {
        if (skillPool != null) return skillPool;
        skillPool = new ArrayList<>();
        for (SkillSrc src : SKILL_SRC) {
            try {
                Block block = (Block) Vars.content.getByName(ContentType.block, "newex-" + src.id);
                if (block == null) continue;
                skillPool.add(buildDef(src, block));
            } catch (Throwable ignored) {}
        }
        return skillPool;
    }

    static SkillDef buildDef(SkillSrc src, Block block) {
        SkillDef d = new SkillDef();
        d.name = src.id; d.src = src; d.block = block;
        d.vi = src.vi; d.en = src.en;
        d.color = colorFromName(src.id);
        d.reload = src.reload;
        d.pellets = src.pellets <= 0 ? 1 : src.pellets;
        d.spread = src.spread;
        d.cycle = src.cycle;
        d.range = src.range;
        d.bullets = null;
        d.isTurret = block instanceof Turret;
        d.isItem = block instanceof ItemTurret;
        return d;
    }

    static Color colorFromName(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) hash = (hash * 31 + name.charAt(i)) & 0xffffff;
        float r = 0.35f + ((hash & 0xff) / 255f) * 0.65f;
        float g = 0.35f + (((hash >> 8) & 0xff) / 255f) * 0.65f;
        float b = 0.35f + (((hash >> 16) & 0xff) / 255f) * 0.65f;
        return new Color(r, g, b, 1f);
    }

    // ------------------------------------------------------------ xây đạn
    static Color c(String hex) { return Color.valueOf(hex); }

    static SkillSrc merged(SkillSrc base, Variant v) {
        // Java không có object-merge như JS: tạo bản sao SkillSrc rồi đè các trường variant có đặt.
        SkillSrc o = new SkillSrc();
        o.id = base.id; o.vi = base.vi; o.en = base.en; o.kind = base.kind;
        o.front = base.front; o.back = base.back; o.mid = base.mid; o.status = base.status;
        o.speed = base.speed; o.dmg = base.dmg; o.life = base.life; o.w = base.w; o.h = base.h; o.len = base.len;
        o.pierce = base.pierce; o.knock = base.knock; o.reload = base.reload; o.range = base.range;
        o.homing = base.homing; o.homingRange = base.homingRange; o.splash = base.splash; o.radius = base.radius;
        o.statusTime = base.statusTime; o.lightning = base.lightning; o.lightningLen = base.lightningLen; o.lightningDmg = base.lightningDmg;
        o.pellets = base.pellets; o.spread = base.spread; o.cycle = base.cycle;
        if (v != null) {
            if (v.dmg != null) o.dmg = v.dmg;
            if (v.w != null) o.w = v.w;
            if (v.back != null) o.back = v.back;
            if (v.status != null) { o.status = v.status; o.statusTime = v.statusTime; }
            if (v.splash != null) o.splash = v.splash;
            if (v.radius != null) o.radius = v.radius;
            if (v.pierce > 0) o.pierce = v.pierce;
        }
        return o;
    }

    static BulletType decorate(BulletType b, SkillSrc o) {
        if (o.pierce > 0) { b.pierce = true; b.pierceCap = (int) o.pierce; }
        if (o.homing > 0) { b.homingPower = o.homing; b.homingRange = o.homingRange > 0 ? o.homingRange : 160; }
        if (o.splash > 0) { b.splashDamage = o.splash; b.splashDamageRadius = o.radius > 0 ? o.radius : 30; }
        if (o.knock > 0) b.knockback = o.knock;
        if (o.status != null) {
            StatusEffect st = statusByName(o.status);
            if (st != null) { b.status = st; b.statusDuration = o.statusTime > 0 ? o.statusTime : 90; }
        }
        if (o.lightning > 0) {
            b.lightning = (int) o.lightning;
            b.lightningLength = o.lightningLen > 0 ? (int) o.lightningLen : 6;
            b.lightningDamage = o.lightningDmg >= 0 ? o.lightningDmg : Math.max(1, o.dmg * 0.6f);
            b.lightningColor = c(o.back);
        }
        return b;
    }

    /** JS tra "StatusEffects[o.status]" theo tên động; Java không có đó nên tra bằng switch thủ công. */
    static StatusEffect statusByName(String name) {
        switch (name) {
            case "shocked": return StatusEffects.shocked;
            case "slow": return StatusEffects.slow;
            case "burning": return StatusEffects.burning;
            case "melting": return StatusEffects.melting;
            case "freezing": return StatusEffects.freezing;
            case "blasted": return StatusEffects.blasted;
            case "wet": return StatusEffects.wet;
            case "corroded": return StatusEffects.corroded;
            default: return null;
        }
    }

    static BulletType mkBullet(SkillSrc o) {
        BasicBulletType b = "missile".equals(o.kind) ? new MissileBulletType(o.speed, o.dmg) : new BasicBulletType(o.speed, o.dmg);
        b.lifetime = o.life;
        b.width = o.w > 0 ? o.w : 7;
        b.height = o.h > 0 ? o.h : 10;
        b.frontColor = c(o.front);
        b.backColor = c(o.back);
        b.hitColor = c(o.back);
        b.trailColor = c(o.back);
        b.hitEffect = Fx.hitBulletColor;
        b.despawnEffect = Fx.hitBulletColor;
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        return decorate(b, o);
    }

    static BulletType mkLaser(SkillSrc o) {
        LaserBulletType b = new LaserBulletType(o.dmg);
        b.length = o.len;
        b.width = o.w > 0 ? o.w : 10;
        try { b.colors = new Color[]{c(o.back), c(o.mid != null ? o.mid : o.back), Color.white}; } catch (Throwable ignored) {}
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        return decorate(b, o);
    }

    static BulletType mkLightning(SkillSrc o) {
        LightningBulletType b = new LightningBulletType();
        b.damage = o.dmg;
        b.lightningLength = (int) o.len;
        b.lightningLengthRand = (int) (o.len / 4f);
        b.lightningColor = c(o.back);
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        if (o.status != null) {
            StatusEffect st = statusByName(o.status);
            if (st != null) { b.status = st; b.statusDuration = o.statusTime > 0 ? o.statusTime : 90; }
        }
        return b;
    }

    static BulletType prep(BulletType b) {
        try { b.init(); } catch (Throwable ignored) {}
        try { b.load(); } catch (Throwable ignored) {}
        return b;
    }

    static BulletType buildBullet(SkillSrc o) {
        if ("laser".equals(o.kind)) return prep(mkLaser(o));
        if ("lightning".equals(o.kind)) return prep(mkLightning(o));
        return prep(mkBullet(o));
    }

    static BulletType fallbackBullet(SkillSrc src) {
        SkillSrc o = new SkillSrc();
        o.speed = 8; o.dmg = src.dmg > 0 ? src.dmg : 15; o.life = 36; o.w = 7; o.h = 10;
        o.front = "ffffff"; o.back = src.back != null ? src.back : "ffd37f";
        return prep(mkBullet(o));
    }

    static float reachOf(SkillSrc o) {
        if ("laser".equals(o.kind) || "lightning".equals(o.kind)) return Float.MAX_VALUE;
        return o.speed * o.life * 0.95f;
    }

    static void ensureSimpleBullets(SkillDef def) {
        if (def.bullets != null) return;
        SkillSrc src = def.src;
        List<BulletType> list = new ArrayList<>();
        float reach = Float.MAX_VALUE;
        List<Variant> variants = src.variants != null ? src.variants : List.of((Variant) null);
        try {
            for (Variant v : variants) {
                SkillSrc o = merged(src, v);
                list.add(buildBullet(o));
                reach = Math.min(reach, reachOf(o));
            }
            def.bullets = list;
        } catch (Throwable err) {
            def.bullets = List.of(fallbackBullet(src));
            reach = 8 * 36 * 0.95f;
        }
        def.range = Math.min(src.range, reach);
    }

    // ------------------------------------------------------------ building ảo (virtual turret)
    static Item ammoItemFor(Block block) {
        if (!(block instanceof ItemTurret)) return null;
        Item best = null;
        try {
            ItemTurret it = (ItemTurret) block;
            for (ObjectMap.Entry<Item, BulletType> e : it.ammoTypes) {
                Item item = e.key;
                BulletType bt = e.value;
                if (bt == null) continue;
                if (best == null) best = item;
                if (!isDummyBullet(bt)) { best = item; break; }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    static boolean isDummyBullet(BulletType bt) {
        try { return bt.speed == 0 && bt.lifetime == 0 && bt.damage == 0; } catch (Throwable e) { return false; }
    }

    /**
     * Tạo building ảo không gắn tile thật, chỉ để mô phỏng hành vi turret gốc (JS: createVirtual).
     * CHƯA KIỂM: {@code block.newBuilding().create(block, team)} rồi {@code init(tile, team, false, 0)}
     * là cách Mindustry dựng Building nhưng ở đây dùng Tile rời (toạ độ gạch 0,0) chứ không gắn vào
     * bản đồ thật — nếu v157.4 có validate tile khác, cần sửa khi build thật (xem MIGRATION.md).
     */
    static Building createVirtual(SkillDef def, mindustry.game.Team team, float x, float y) {
        Block block = def.block;
        Building b = block.newBuilding();
        b.create(block, team);
        Tile tile = new Tile(0, 0);
        tile.build = b;
        b.init(tile, team, false, 0);
        b.set(x, y);
        return b;
    }

    static void disposeVirtual(Building b) {
        if (b == null) return;
        try { if (b.tile != null && b.tile.build == b) b.tile.build = null; } catch (Throwable ignored) {}
    }

    static void disposeAll(Map<Integer, PlayerState> states) {
        try {
            for (PlayerState st : states.values()) {
                if (st == null || st.skills == null) continue;
                for (Skill sk : st.skills) { disposeVirtual(sk.virt); sk.virt = null; }
            }
        } catch (Throwable ignored) {}
    }

    static void feedAmmo(Building b, SkillDef def) {
        if (!def.isItem || def.ammoItem == null) return;
        int max = def.block instanceof Turret ? ((Turret) def.block).maxAmmo : 30;
        int guard = 0;
        while (((ItemTurret.ItemTurretBuild) b).totalAmmo < max * 0.7f && guard < 6) {
            guard++;
            int before = ((ItemTurret.ItemTurretBuild) b).totalAmmo;
            b.handleItem(b, def.ammoItem);
            if (((ItemTurret.ItemTurretBuild) b).totalAmmo <= before) break;
        }
    }

    static void autoAim(Building b, Skill sk, Unit unit) {
        Turret.TurretBuild tb;
        try { tb = (Turret.TurretBuild) b; } catch (Throwable e) { return; }
        sk.hookT -= Time.delta;
        if (sk.hookT > 0) return;
        sk.hookT = 10;
        float r = 400;
        try { r = tb.range(); } catch (Throwable ignored) {}
        Unit t = Units.closestEnemy(unit.team, sk.px, sk.py, r, e -> e != null && !e.dead);
        if (t != null) {
            tb.target = t; tb.targetPos.set(t.x, t.y);
        } else {
            tb.target = null;
        }
    }

    static void driveReal(Skill sk, Unit unit, int tier) {
        SkillDef def = sk.def;
        if (sk.virt == null) {
            if (!def.ammoResolved) { def.ammoItem = ammoItemFor(def.block); def.ammoResolved = true; }
            sk.virt = createVirtual(def, unit.team, sk.px, sk.py);
            sk.noAmmo = 0; sk.zeroEff = 0;
        }
        Building b = sk.virt;
        b.set(sk.px, sk.py);

        try { b.enabled = true; } catch (Throwable ignored) {}
        try { b.wasVisible = true; } catch (Throwable ignored) {}
        try { if (b.power != null) b.power.status = 1; } catch (Throwable ignored) {}

        float fireRateMult = statMult(sk.ownerState, "firerate");
        try { b.applyBoost(fireRateMult, 5f); } catch (Throwable ignored) {}

        Turret.TurretBuild tb = b instanceof Turret.TurretBuild ? (Turret.TurretBuild) b : null;
        try { if (tb != null && tb.totalShots > 50) tb.totalShots = 0; } catch (Throwable ignored) {}

        feedAmmo(b, def);
        autoAim(b, sk, unit);

        if (sk.forceEff) {
            try { b.efficiency = 1; } catch (Throwable ignored) {}
            try { b.optionalEfficiency = 1; } catch (Throwable ignored) {}
            try { b.potentialEfficiency = 1; } catch (Throwable ignored) {}
            b.updateTile();
        } else {
            b.update();
            float eff = 1;
            try { eff = b.efficiency; } catch (Throwable ignored) {}
            if (tb != null && tb.target != null && !(eff > 0.001f)) {
                sk.zeroEff += Time.delta;
                if (sk.zeroEff > 60) sk.forceEff = true;
            } else {
                sk.zeroEff = 0;
            }
        }

        if (b.health < def.block.health * 0.01f) b.health = def.block.health;

        if (tb != null && tb.target != null) { sk.aim = tb.rotation; sk.noTargetTicks = 0; }
        try { sk.rangeNow = tb != null ? tb.range() : def.range; } catch (Throwable ignored) {}

        if (!b.isValid()) {
            disposeVirtual(b);
            sk.virt = null;
            sk.deaths++;
            if (sk.deaths >= 3) throw new RuntimeException("building destroyed");
            return;
        }

        if (def.isItem) {
            ItemTurret.ItemTurretBuild ib = (ItemTurret.ItemTurretBuild) b;
            if (!ib.hasAmmo()) {
                sk.noAmmo += Time.delta;
                if (sk.noAmmo > 180) throw new RuntimeException("no ammo");
            } else {
                sk.noAmmo = 0;
            }
        }
    }

    static DrawBlock noBodyDrawer;
    static boolean noBodyDrawerTried = false;

    static DrawBlock getNoBodyDrawer() {
        if (noBodyDrawerTried) return noBodyDrawer;
        noBodyDrawerTried = true;
        try { noBodyDrawer = new DrawMulti(); } catch (Throwable ignored) {}
        if (noBodyDrawer == null) {
            try { noBodyDrawer = new DrawBlock() { public void draw(Building build) {} }; } catch (Throwable ignored) {}
        }
        return noBodyDrawer;
    }

    static void drawReal(Skill sk) {
        Building b = sk.virt;
        if (b == null || sk.drawErr >= 3) return;
        DrawBlock drawer = getNoBodyDrawer();
        if (drawer == null) return;
        if (!(sk.def.block instanceof Turret)) return;
        Turret blk = (Turret) sk.def.block;
        DrawBlock old = null;
        boolean swapped = false;
        try {
            old = blk.drawer;
            blk.drawer = drawer;
            swapped = true;
            b.set(sk.px, sk.py);
            b.draw();
        } catch (Throwable err) {
            sk.drawErr++;
        }
        if (swapped) { try { blk.drawer = old; } catch (Throwable ignored) {} }
        Draw.reset();
    }

    static void switchToSimple(Skill sk, String why) {
        sk.why = why.length() > 70 ? why.substring(0, 70) : why;
        sk.retryT = 600;
        try { Log.warn("[NexSkills] " + sk.name + " -> simple: " + sk.why); } catch (Throwable ignored) {}
        sk.mode = Mode.SIMPLE;
        disposeVirtual(sk.virt);
        sk.virt = null;
        sk.timer = 0;
        ensureSimpleBullets(sk.def);
    }

    // ------------------------------------------------------------ trạng thái người chơi
    enum Mode { REAL, SIMPLE }

    static final class Skill {
        String name;
        SkillDef def;
        int tier = 1;
        Mode mode;
        float timer, shot;
        boolean inited = false;
        float px, py, vx, vy;
        float offA = 0, offR = 30, wanderT = 0, aim = 0, aimDraw = 0;
        final List<float[]> trail = new ArrayList<>(); // {x,y}
        float trailT = 0, noTargetTicks = 999;
        Building virt;
        int errs = 0, errRun = 0, deaths = 0;
        float noAmmo = 0, hookT = 0, rangeNow = 0, zeroEff = 0;
        boolean forceEff = false;
        int drawErr = 0;
        String why = "";
        int retryLeft = 2;
        float retryT = 0;
        PlayerState ownerState;
    }

    static final class PlayerState {
        int level = 0; float exp = 0;
        final List<Skill> skills = new ArrayList<>();
        float maxRange = 0; int rerollPts = 0;
        final Map<String, Float> stats = new HashMap<>();
        float baseMaxHealth = 0, baseArmor = -1;
        boolean baseArmorSet = false;
        int pending = 0;
        List<Object> offer = null; // SkillDef hoặc Pick
        boolean offerOut = false, dirty = true;
        String lastMe = "";
        PlayerState() { for (String k : STAT_KEYS) stats.put(k, 0f); }
    }

    /** Một lựa chọn chỉ số (JS: randomStatPick()/resolvePickById("stat:...")). */
    static final class Pick {
        String name, stat, vi, en;
        StatSrc srcStat;
    }

    static final Map<Integer, PlayerState> playerStates = new HashMap<>();
    static final Map<String, PlayerState> savedByUuid = new HashMap<>();
    static BaseDialog listDialog;
    static MeInfo meInfo;
    static Object currentOffer; // handle trả về bởi showSkillDialog (có hide())

    static final class MeInfo { int level, exp, need, maxLevel, reroll; }

    interface Handle { void hide(); }

    static String getPlayerId(Player player) { return String.valueOf(player.id); }

    static String uuidOf(Player player) {
        try { return String.valueOf(player.uuid()); } catch (Throwable e) { return "id:" + player.id; }
    }

    static PlayerState newState() { return new PlayerState(); }

    static PlayerState getState(Player player) {
        int pid = player.id;
        PlayerState s = playerStates.get(pid);
        if (s == null) {
            if (!Vars.net.client()) {
                String u = uuidOf(player);
                s = savedByUuid.get(u);
                if (s == null) { s = newState(); savedByUuid.put(u, s); }
                s.offerOut = false;
                s.dirty = true;
            } else {
                s = newState();
            }
            playerStates.put(pid, s);
        }
        return s;
    }

    static float levelCost(int n) {
        if (n <= 0) return 0;
        return Math.round(35 + 20 * (n - 1) + 2.5f * (n - 1) * (n - 2));
    }

    static int getMaxLevel() {
        int v;
        try { v = Core.settings.getInt("newex-survival-max-level", 10); } catch (Throwable e) { v = 10; }
        if (v < 1) v = 10;
        return Math.min(999, v);
    }

    static int getExpPercent() {
        int v;
        try { v = Core.settings.getInt("newex-survival-exp-percent", 100); } catch (Throwable e) { v = 100; }
        if (v < 0) v = 100;
        return Math.min(1000, v);
    }

    static boolean isExpByHp() {
        try { return Core.settings.getBool("newex-survival-exp-by-hp", false); } catch (Throwable e) { return false; }
    }

    static float expForKill(Unit u) {
        float base = 6 + Vars.state.wave / 8f;
        float mult = getExpPercent() / 100f;
        if (isExpByHp() && u != null) {
            float hp = 0;
            try { hp = u.maxHealth; } catch (Throwable ignored) {}
            if (hp > 0) mult *= Math.max(1, Math.min(50, (float) Math.sqrt(hp / 130f)));
        }
        return base * mult;
    }

    static Skill findSkill(PlayerState state, String name) {
        for (Skill s : state.skills) if (s.name.equals(name)) return s;
        return null;
    }

    static boolean isStat(Object pick) { return pick instanceof Pick; }

    static Pick randomStatPick() {
        StatSrc s = STAT_SRC.get(Mathf.random(STAT_SRC.size() - 1));
        Pick p = new Pick(); p.name = s.id; p.stat = s.stat; p.vi = s.vi; p.en = s.en; p.srcStat = s;
        return p;
    }

    static List<Object> rollSkillChoices(PlayerState state) {
        List<SkillDef> pool = buildSkillPool();
        List<SkillDef> open = new ArrayList<>();
        for (SkillDef d : pool) if (findSkill(state, d.name) == null) open.add(d);

        int want = SKILL_CHOICE_COUNT;
        List<Object> picks = new ArrayList<>();
        int cannonWant = Math.min(want, open.size());
        int tries = 0;
        while (picks.size() < cannonWant && tries < 60) {
            tries++;
            SkillDef cand = open.get(Mathf.random(open.size() - 1));
            if (!picks.contains(cand)) picks.add(cand);
        }
        while (picks.size() < want) picks.add(randomStatPick());
        // trộn ngẫu nhiên vị trí hiển thị (Fisher-Yates như JS)
        for (int i = picks.size() - 1; i > 0; i--) {
            int j = Mathf.random(i);
            Object tmp = picks.get(i); picks.set(i, picks.get(j)); picks.set(j, tmp);
        }
        return picks;
    }

    static String pickId(Object c) {
        if (c instanceof Pick) return "stat:" + ((Pick) c).stat;
        return ((SkillDef) c).name;
    }

    static Object resolvePickById(String id) {
        if (id == null) return null;
        if (id.startsWith("stat:")) {
            String key = id.substring(5);
            for (StatSrc s : STAT_SRC) {
                if (s.stat.equals(key)) {
                    Pick p = new Pick(); p.name = s.id; p.stat = s.stat; p.vi = s.vi; p.en = s.en; p.srcStat = s;
                    return p;
                }
            }
            return null;
        }
        for (SkillDef d : buildSkillPool()) if (d.name.equals(id)) return d;
        return null;
    }

    static void applyStatPick(Player player, PlayerState state, Pick pick) {
        state.stats.merge(pick.stat, 1f, Float::sum);
        if (player != null) {
            NexNet.toastTo(player, (isEn() ? "[accent]Stat: " : "[accent]Chỉ số: ") + statName(pick.srcStat), 2.5f);
        }
    }

    /** Chỉ chạy trên SERVER (host / dedicated). Client chỉ gửi yêu cầu chọn. */
    static void applySkill(Player player, PlayerState state, Object c) {
        if (c instanceof Pick) {
            applyStatPick(player, state, (Pick) c);
        } else {
            SkillDef def = (SkillDef) c;
            if (findSkill(state, def.name) != null) return;
            addSkill(state, def);
            if (player != null) {
                NexNet.toastTo(player, (isEn() ? "[accent]Skill: " : "[accent]Kĩ năng: ") + def.block.localizedName, 2.5f);
            }
        }
        state.dirty = true;
    }

    static void addSkill(PlayerState state, SkillDef def) {
        boolean real = REAL_TURRETS && def.isTurret;
        if (!real) ensureSimpleBullets(def);
        Skill sk = new Skill();
        sk.name = def.name; sk.def = def; sk.tier = 1;
        sk.mode = real ? Mode.REAL : Mode.SIMPLE;
        sk.timer = Mathf.random(def.reload);
        sk.ownerState = state;
        state.skills.add(sk);
    }

    // ------------------------------------------------------------ hộp thoại chọn thẻ
    static Handle showSkillDialog(List<Object> choices, LevelInfoLite info, Consumer<Object> onPick, Runnable onReroll, Runnable onAbort) {
        BaseDialog dialog = new BaseDialog(isEn() ? "Level Up!" : "Lên Cấp!");
        dialog.cont.clear();
        dialog.cont.add(isEn() ? "Choose one - Level " + info.level + "/" + info.maxLevel : "Chọn một - Cấp " + info.level + "/" + info.maxLevel).pad(8).row();

        Table row = new Table();
        boolean[] finished = {false};
        Handle handle = () -> { if (!finished[0]) { finished[0] = true; dialog.hide(); } };

        for (Object c : choices) {
            Table card = new Table(Styles.black6);
            card.margin(8f);

            if (c instanceof Pick) {
                Pick p = (Pick) c;
                card.add(isEn() ? "[accent]STAT" : "[accent]CHỈ SỐ").pad(4f).row();
                Label nameLabel = card.add(statName(p.srcStat)).wrap().width(150f).pad(2f).get();
                nameLabel.setAlignment(Align.center);
                card.row();
                Label noteLabel = card.add("[lightgray]" + statDesc(p.srcStat) + "[]").wrap().width(150f).pad(2f).get();
                noteLabel.setAlignment(Align.center);
                noteLabel.setFontScale(0.85f);
                card.row();
                float have = info.stats != null ? info.stats.getOrDefault(p.stat, 0f) : 0f;
                card.add((isEn() ? "[accent]Owned: " : "[accent]Đã có: ") + (int) have).pad(2f).row();
            } else {
                SkillDef d = (SkillDef) c;
                card.add(new Image(d.block.uiIcon)).size(40f).pad(4f).row();
                Label nameLabel = card.add(d.block.localizedName).wrap().width(150f).pad(2f).get();
                nameLabel.setAlignment(Align.center);
                card.row();
                Label noteLabel = card.add("[lightgray]" + descOf(d) + "[]").wrap().width(150f).pad(2f).get();
                noteLabel.setAlignment(Align.center);
                noteLabel.setFontScale(0.85f);
                card.row();
                card.add(isEn() ? "[accent]New Cannon" : "[accent]Pháo mới").pad(2f).row();
            }

            card.button(isEn() ? "Choose" : "Chọn", () -> {
                if (finished[0]) return;
                finished[0] = true;
                dialog.hide();
                onPick.accept(c);
            }).size(130f, 42f).pad(4f);
            row.add(card).size(180f, 280f).pad(6f);
        }

        dialog.cont.add(row).row();

        Table rerollRow = new Table();
        rerollRow.add((isEn() ? "Reroll points: " : "Điểm đổi: ") + info.reroll).pad(6f);
        rerollRow.button(isEn() ? "Reroll choices" : "Đổi lựa chọn", () -> {
            if (finished[0] || info.reroll <= 0) return;
            finished[0] = true;
            dialog.hide();
            if (onReroll != null) onReroll.run();
        }).size(170f, 42f).pad(6f);
        dialog.cont.add(rerollRow).row();

        dialog.hidden(() -> {
            if (finished[0]) return;
            finished[0] = true;
            if (onAbort != null) onAbort.run();
        });
        dialog.show();
        return handle;
    }

    static final class LevelInfoLite {
        int level, maxLevel, reroll;
        Map<String, Float> stats;
    }

    // ---- SERVER là nơi duy nhất giữ exp / cấp / lựa chọn của từng người chơi ----
    static void showOfferLocal(Player player, PlayerState st) {
        if (st.offer == null) return;
        LevelInfoLite info = new LevelInfoLite();
        info.level = st.level; info.maxLevel = getMaxLevel(); info.reroll = st.rerollPts; info.stats = st.stats;
        showSkillDialog(st.offer, info, c -> resolvePick(player, st, c), () -> doReroll(player, st), () -> st.offerOut = false);
    }

    static void serveOffer(Player player, PlayerState st) {
        if (st.offer == null) {
            if (st.pending <= 0) return;
            st.offer = rollSkillChoices(st);
            if (st.offer.isEmpty()) { st.pending = 0; st.offer = null; return; }
        }
        st.offerOut = true;
        if (!Vars.headless && player == Vars.player) {
            showOfferLocal(player, st);
        } else if (player.con != null) {
            List<String> ids = new ArrayList<>();
            for (Object c : st.offer) ids.add(pickId(c));
            NexNet.sendTo(player, "newex-survival-offer", String.join(",", ids) + "~" + st.rerollPts + "~" + st.level + "~" + getMaxLevel());
        } else {
            resolvePick(player, st, st.offer.get(Mathf.random(st.offer.size() - 1)));
        }
    }

    static void resolvePick(Player player, PlayerState st, Object c) {
        applySkill(player, st, c);
        st.pending = Math.max(0, st.pending - 1);
        st.offer = null;
        st.offerOut = false;
        st.dirty = true;
        if (st.pending > 0) serveOffer(player, st);
    }

    static void doReroll(Player player, PlayerState st) {
        if (st.offer == null || st.rerollPts <= 0) { st.offerOut = false; return; }
        st.rerollPts--;
        st.offer = rollSkillChoices(st);
        serveOffer(player, st);
    }

    static void onLevelUp(Player player, PlayerState state) {
        state.rerollPts++;
        state.pending++;
        if (!state.offerOut) serveOffer(player, state);
    }

    static void grantExp(Player player, float amount) {
        if (player == null) return;
        PlayerState state = getState(player);
        int maxLv = getMaxLevel();
        if (state.level >= maxLv) return;
        state.exp += amount;
        while (state.level < maxLv && state.exp >= levelCost(state.level + 1)) {
            state.exp -= levelCost(state.level + 1);
            state.level++;
            onLevelUp(player, state);
        }
    }

    static void sendMe(Player pl, PlayerState st) {
        int maxLv = getMaxLevel();
        int need = st.level < maxLv ? (int) levelCost(st.level + 1) : 0;
        String s = st.level + "|" + (int) st.exp + "|" + need + "|" + maxLv + "|" + st.rerollPts;
        if (s.equals(st.lastMe)) return;
        st.lastMe = s;
        NexNet.sendTo(pl, "newex-survival-me", s);
    }

    // ------------------------------------------------------------ mạng
    static void registerNet() {
        NexNet.on("newex-survival-pick", (player, payload) -> {
            if (player == null || Vars.net.client()) return;
            PlayerState st = getState(player);
            if (st.offer == null) return;
            Object c = null;
            for (Object o : st.offer) if (pickId(o).equals(payload)) { c = o; break; }
            if (c == null) { st.offerOut = false; return; }
            resolvePick(player, st, c);
        }, true, 50);

        NexNet.on("newex-survival-reroll", (player, payload) -> {
            if (player == null || Vars.net.client()) return;
            doReroll(player, getState(player));
        }, true, 50);

        NexNet.on("newex-survival-reopen", (player, payload) -> {
            if (player == null || Vars.net.client()) return;
            getState(player).offerOut = false;
        }, true, 200);

        NexNet.onClient("newex-survival-me", payload -> {
            String[] a = payload.split("\\|");
            MeInfo m = new MeInfo();
            m.level = parseIntSafe(a, 0); m.exp = parseIntSafe(a, 1); m.need = parseIntSafe(a, 2);
            m.maxLevel = parseIntSafe(a, 3); m.reroll = parseIntSafe(a, 4);
            meInfo = m;
        });

        NexNet.onClient("newex-survival-offer", payload -> {
            if (Vars.headless || Vars.player == null) return;
            String[] seg = payload.split("~");
            String[] ids = seg[0].split(",");
            List<Object> choices = new ArrayList<>();
            for (String id : ids) {
                Object d = resolvePickById(id);
                if (d != null) choices.add(d);
            }
            if (choices.isEmpty()) return;
            if (currentOffer != null) { ((Handle) currentOffer).hide(); currentOffer = null; }
            PlayerState mine = playerStates.get(Vars.player.id);
            LevelInfoLite info = new LevelInfoLite();
            info.level = seg.length > 2 ? parseIntSafe(seg, 2) : 0;
            info.maxLevel = seg.length > 3 ? parseIntSafe(seg, 3) : 0;
            info.reroll = seg.length > 1 ? parseIntSafe(seg, 1) : 0;
            info.stats = mine != null ? mine.stats : null;
            currentOffer = showSkillDialog(choices, info,
                c -> { currentOffer = null; NexNet.send("newex-survival-pick", pickId(c)); },
                () -> { currentOffer = null; NexNet.send("newex-survival-reroll", ""); },
                () -> { currentOffer = null; NexNet.send("newex-survival-reopen", ""); });
        });

        NexNet.onBroadcast("newex-survival-roster", payload -> {
            if (!Vars.net.client() || payload == null || payload.isEmpty()) return;
            String[] parts = payload.split("\\|");
            for (String part : parts) {
                String[] seg = part.split("\\^");
                if (seg.length < 3) continue;
                Player pl = Groups.player.getByID(Integer.parseInt(seg[0]));
                if (pl == null) continue;
                PlayerState st = getState(pl);
                String[] sv = seg[1].split(",");
                for (int k = 0; k < STAT_KEYS.length && k < sv.length; k++) {
                    float f; try { f = Float.parseFloat(sv[k]); } catch (Throwable e) { f = 0; }
                    st.stats.put(STAT_KEYS[k], f);
                }
                List<String> names = seg[2].isEmpty() ? List.of() : List.of(seg[2].split(","));
                for (int j = st.skills.size() - 1; j >= 0; j--) {
                    if (!names.contains(st.skills.get(j).name)) {
                        disposeVirtual(st.skills.get(j).virt);
                        st.skills.remove(j);
                    }
                }
                for (String name : names) {
                    if (findSkill(st, name) != null) continue;
                    Object d = resolvePickById(name);
                    if (d instanceof SkillDef) addSkill(st, (SkillDef) d);
                }
            }
        });
    }

    static int parseIntSafe(String[] a, int i) {
        try { return i < a.length ? Integer.parseInt(a[i].trim()) : 0; } catch (Throwable e) { return 0; }
    }

    static void purgeStates() {
        playerStates.entrySet().removeIf(e -> {
            if (Groups.player.getByID(e.getKey()) != null) return false;
            PlayerState st = e.getValue();
            if (st != null) for (Skill sk : st.skills) { disposeVirtual(sk.virt); sk.virt = null; }
            return true;
        });
    }

    // ------------------------------------------------------------ chỉ số & bắn
    static float statMult(PlayerState state, String key) {
        if (state == null) return 1;
        float pts = state.stats.getOrDefault(key, 0f);
        float per = STAT_PER_POINT.getOrDefault(key, 0f);
        return 1 + pts * per;
    }

    static void applyPlayerStats(Player pl, Unit unit, PlayerState state) {
        if (state == null) return;
        try {
            if (state.baseMaxHealth <= 0) state.baseMaxHealth = unit.maxHealth;
            float hpMult = statMult(state, "hp");
            float newMax = state.baseMaxHealth * hpMult;
            if (unit.maxHealth < newMax) {
                float diff = newMax - unit.maxHealth;
                unit.maxHealth = newMax;
                unit.health += diff;
            } else if (unit.maxHealth != newMax) {
                unit.maxHealth = newMax;
            }
        } catch (Throwable ignored) {}
        try {
            if (!state.baseArmorSet) { state.baseArmor = unit.armor; state.baseArmorSet = true; }
            unit.armor = state.baseArmor + state.stats.getOrDefault("armor", 0f) * STAT_PER_POINT.get("armor");
        } catch (Throwable ignored) {}
        try { unit.buildSpeedMultiplier = statMult(state, "build"); } catch (Throwable ignored) {}
    }

    static BulletType pickBullet(SkillDef def, Skill sk) {
        int n = def.bullets.size();
        if (n == 1) return def.bullets.get(0);
        if (def.cycle) return def.bullets.get((int) sk.shot % n);
        return def.bullets.get(Mathf.random(n - 1));
    }

    static void fireSkill(Unit unit, Skill sk, float ox, float oy, float angle, Unit target) {
        SkillDef def = sk.def;
        int tier = Math.min(MAX_TIER_SHOTS, sk.tier);
        int count = Math.min(MAX_BULLETS_PER_VOLLEY, (int) (def.pellets * tier));
        PlayerState state = sk.ownerState;
        float dmgMult = statMult(state, "dmg");
        float critChance = state != null ? state.stats.getOrDefault("critChance", 0f) * STAT_PER_POINT.get("critChance") : 0;
        float critMult = 1 + (state != null ? state.stats.getOrDefault("critDmg", 0f) * STAT_PER_POINT.get("critDmg") : 0);
        float shred = state != null ? state.stats.getOrDefault("shred", 0f) * STAT_PER_POINT.get("shred") : 0;
        try {
            for (int s = 0; s < count; s++) {
                BulletType b = pickBullet(def, sk);
                Bullet bullet = b.create(unit, unit.team, ox, oy, angle + Mathf.range(def.spread));
                try {
                    if (bullet != null && dmgMult != 1) bullet.damage *= dmgMult;
                    if (bullet != null && critChance > 0 && Mathf.chance(critChance)) bullet.damage *= critMult;
                } catch (Throwable ignored) {}
            }
            if (shred > 0 && target != null) {
                try { target.armor = Math.max(0, target.armor - shred); } catch (Throwable ignored) {}
            }
            sk.shot++;
        } catch (Throwable err) {
            def.bullets = List.of(fallbackBullet(def.src != null ? def.src : new SkillSrc()));
            def.range = Math.min(def.range, 280);
        }
    }

    static void updateSimple(Unit unit, Skill sk) {
        sk.timer -= Time.delta;
        if (sk.timer > 0) return;
        PlayerState state = sk.ownerState;
        float rangeMult = statMult(state, "range");
        float range = sk.def.range * rangeMult;
        Unit target = Units.closestEnemy(unit.team, sk.px, sk.py, range, t -> t != null && !t.dead);
        if (target != null) {
            float angle = Angles.angle(sk.px, sk.py, target.x, target.y);
            sk.aim = angle;
            sk.noTargetTicks = 0;
            fireSkill(unit, sk, sk.px, sk.py, angle, target);
            float fireRateMult = statMult(state, "firerate");
            sk.timer = Math.max(MIN_RELOAD, (sk.def.reload * (1 - (sk.tier - 1) * 0.12f)) / fireRateMult);
        } else {
            sk.timer = 12;
        }
    }

    // ------------------------------------------------------------ drone bay quanh người chơi
    static void pickWander(Skill sk) {
        sk.offA = Mathf.random(360f);
        sk.offR = DRONE_R_MIN + Mathf.random(DRONE_R_MAX - DRONE_R_MIN);
        sk.wanderT = DRONE_WANDER_MIN + Mathf.random(DRONE_WANDER_MAX - DRONE_WANDER_MIN);
    }

    static void initDrone(Skill sk, Unit unit) {
        float a = Mathf.random(360f);
        sk.px = unit.x + Mathf.cosDeg(a) * 14;
        sk.py = unit.y + Mathf.sinDeg(a) * 14;
        sk.vx = 0; sk.vy = 0;
        sk.aim = a; sk.aimDraw = a;
        pickWander(sk);
        sk.inited = true;
    }

    static void moveDrone(Skill sk, Unit unit, List<Skill> all) {
        float dt = Time.delta;
        sk.wanderT -= dt;
        sk.noTargetTicks += dt;
        if (sk.wanderT <= 0) pickWander(sk);

        float tx = unit.x + Mathf.cosDeg(sk.offA) * sk.offR + unit.vel.x * 10;
        float ty = unit.y + Mathf.sinDeg(sk.offA) * sk.offR + unit.vel.y * 10;
        float dx = tx - sk.px;
        float dy = ty - sk.py;

        float pdx = sk.px - unit.x;
        float pdy = sk.py - unit.y;
        float pd = Mathf.sqrt(pdx * pdx + pdy * pdy);
        if (pd > DRONE_TELEPORT) { initDrone(sk, unit); return; }

        float k = DRONE_SPRING * (pd > DRONE_LEASH ? 3 : 1);
        sk.vx += dx * k * dt;
        sk.vy += dy * k * dt;

        sk.vx += (Mathf.random(1f) - 0.5f) * 0.07f * dt;
        sk.vy += (Mathf.random(1f) - 0.5f) * 0.07f * dt;

        for (Skill o : all) {
            if (o == sk || !o.inited) continue;
            float sx = sk.px - o.px, sy = sk.py - o.py;
            float sd = Mathf.sqrt(sx * sx + sy * sy);
            if (sd < DRONE_SEPARATION) {
                if (sd < 0.01f) { sx = Mathf.random(1f) - 0.5f; sy = Mathf.random(1f) - 0.5f; sd = 0.7f; }
                float push = (DRONE_SEPARATION - sd) * 0.03f * dt;
                sk.vx += (sx / sd) * push;
                sk.vy += (sy / sd) * push;
            }
        }

        if (pd < 14) {
            float nx = pd > 0.01f ? pdx / pd : 1;
            float ny = pd > 0.01f ? pdy / pd : 0;
            float push = (14 - pd) * 0.05f * dt;
            sk.vx += nx * push;
            sk.vy += ny * push;
        }

        float drag = (float) Math.pow(DRONE_DRAG, dt);
        sk.vx *= drag; sk.vy *= drag;

        float sp = Mathf.sqrt(sk.vx * sk.vx + sk.vy * sk.vy);
        float maxSp = DRONE_MAX_SPEED * (pd > DRONE_LEASH ? 2.2f : 1);
        if (sp > maxSp) { sk.vx *= maxSp / sp; sk.vy *= maxSp / sp; }

        sk.px += sk.vx * dt;
        sk.py += sk.vy * dt;

        if (sp > 0.6f && sk.noTargetTicks > 20) sk.aim = Mathf.atan2(sk.vy, sk.vx) * Mathf.radDeg;

        sk.trailT -= dt;
        if (sk.trailT <= 0) {
            sk.trailT = 3;
            sk.trail.add(new float[]{sk.px, sk.py});
            if (sk.trail.size() > 7) sk.trail.remove(0);
        }
    }

    static void updateOrbiters(Player player, Unit unit, PlayerState state) {
        List<Skill> skills = state.skills;
        float maxRange = 0;
        for (Skill sk : skills) {
            if (!sk.inited) initDrone(sk, unit);
            moveDrone(sk, unit, skills);

            if (sk.mode == Mode.SIMPLE && REAL_TURRETS && sk.def.isTurret && sk.retryLeft > 0) {
                sk.retryT -= Time.delta;
                if (sk.retryT <= 0) {
                    sk.retryLeft--;
                    sk.mode = Mode.REAL;
                    sk.errs = 0; sk.errRun = 0; sk.deaths = 0; sk.noAmmo = 0; sk.zeroEff = 0; sk.forceEff = false; sk.drawErr = 0;
                }
            }

            if (sk.mode == Mode.REAL) {
                try {
                    driveReal(sk, unit, Math.min(MAX_TIER_SHOTS, sk.tier));
                    sk.errRun = 0;
                    if (sk.rangeNow > maxRange) maxRange = sk.rangeNow;
                } catch (Throwable err) {
                    sk.errs++; sk.errRun++;
                    String msg = String.valueOf(err.getMessage());
                    if (sk.errRun >= 30 || sk.errs >= 400 || msg.contains("destroyed")) {
                        switchToSimple(sk, msg);
                    }
                }
            } else {
                updateSimple(unit, sk);
                if (sk.def.range > maxRange) maxRange = sk.def.range;
            }
        }
        state.maxRange = maxRange;
    }

    // ------------------------------------------------------------ đồng bộ roster + tick server
    static float rosterT = 0, serveT = 0, purgeT = 0;
    static final Map<Integer, Boolean> scaledIds = new HashMap<>();
    static float scaleClean = 0;

    static String buildRoster() {
        List<String> parts = new ArrayList<>();
        Groups.player.each(pl -> {
            PlayerState st = playerStates.get(pl.id);
            if (st == null) return;
            List<String> sv = new ArrayList<>();
            boolean any = false;
            for (String k : STAT_KEYS) {
                float v = st.stats.getOrDefault(k, 0f);
                if (v > 0) any = true;
                sv.add(trimNum(v));
            }
            List<String> names = new ArrayList<>();
            for (Skill sk : st.skills) names.add(sk.name);
            if (!any && names.isEmpty()) return;
            parts.add(getPlayerId(pl) + "^" + String.join(",", sv) + "^" + String.join(",", names));
        });
        return String.join("|", parts);
    }

    static String trimNum(float v) { return v == (long) v ? String.valueOf((long) v) : String.valueOf(v); }

    static void serverTick() {
        serveT -= Time.delta;
        if (serveT <= 0) {
            serveT = 30;
            Groups.player.each(pl -> {
                PlayerState st = playerStates.get(pl.id);
                if (st == null) return;
                if (st.pending > 0 && !st.offerOut) serveOffer(pl, st);
                if (pl != Vars.player && pl.con != null) sendMe(pl, st);
            });
        }
        if (!Vars.net.server()) return;
        rosterT -= Time.delta;
        boolean dirty = false;
        for (PlayerState st : playerStates.values()) if (st.dirty) { dirty = true; st.dirty = false; }
        if (dirty || rosterT <= 0) { rosterT = 180; NexNet.broadcast("newex-survival-roster", buildRoster()); }
    }

    static final class VirtRef { Building b; PlayerState st; VirtRef(Building b, PlayerState st) { this.b = b; this.st = st; } }

    /** Pháo thật tự tạo đạn nên sát thương/bạo kích/giảm giáp phải áp lên đạn mới sinh (không qua fireSkill). */
    static void scaleRealBullets(List<VirtRef> list) {
        if (list.isEmpty()) return;
        boolean need = false;
        for (VirtRef r : list) {
            Map<String, Float> s = r.st.stats;
            if (s.getOrDefault("dmg", 0f) > 0 || s.getOrDefault("critChance", 0f) > 0 || s.getOrDefault("shred", 0f) > 0) { need = true; break; }
        }
        if (!need) return;
        Groups.bullet.each(bu -> {
            if (bu.time > 4 || scaledIds.containsKey(bu.id)) return;
            Object o = bu.owner;
            for (VirtRef r : list) {
                if (r.b != o) continue;
                scaledIds.put(bu.id, true);
                PlayerState st = r.st;
                try {
                    float dm = statMult(st, "dmg");
                    if (dm != 1) bu.damage *= dm;
                    float cc = st.stats.getOrDefault("critChance", 0f) * STAT_PER_POINT.get("critChance");
                    if (cc > 0 && Mathf.chance(cc)) bu.damage *= 1 + st.stats.getOrDefault("critDmg", 0f) * STAT_PER_POINT.get("critDmg");
                    float shred = st.stats.getOrDefault("shred", 0f) * STAT_PER_POINT.get("shred");
                    Object tgRaw = r.b instanceof Turret.TurretBuild ? ((Turret.TurretBuild) r.b).target : null;
                    if (shred > 0 && tgRaw instanceof Unit) ((Unit) tgRaw).armor = Math.max(0, ((Unit) tgRaw).armor - shred);
                } catch (Throwable ignored) {}
                break;
            }
        });
        scaleClean -= Time.delta;
        if (scaleClean <= 0) { scaledIds.clear(); scaleClean = 600; }
    }

    // ------------------------------------------------------------ vẽ icon drone (ngôi sao 4 cánh)
    static void fillStar4(float x, float y, float outer, float inner, float rot) {
        for (int i = 0; i < 4; i++) {
            float a0 = rot + i * 90, a1 = a0 + 45, a2 = a0 + 90;
            float tx0 = x + Mathf.cosDeg(a0) * outer, ty0 = y + Mathf.sinDeg(a0) * outer;
            float vx = x + Mathf.cosDeg(a1) * inner, vy = y + Mathf.sinDeg(a1) * inner;
            float tx1 = x + Mathf.cosDeg(a2) * outer, ty1 = y + Mathf.sinDeg(a2) * outer;
            Fill.tri(x, y, tx0, ty0, vx, vy);
            Fill.tri(x, y, vx, vy, tx1, ty1);
        }
    }

    // ------------------------------------------------------------ API công khai (thay cho global.NexSkills)
    static final class LevelInfoFull { int level, maxLevel, exp, need, reroll; Map<String, Float> stats; }

    static boolean isSurvivalActive() { return Survival.isActive(); }

    static LevelInfoFull getLocalLevelInfoFull() {
        if (Vars.player == null || !isSurvivalActive()) return null;
        if (Vars.net.client()) {
            MeInfo m = meInfo;
            LevelInfoFull r = new LevelInfoFull();
            if (m == null) { r.level = 0; r.maxLevel = getMaxLevel(); r.exp = 0; r.need = (int) levelCost(1); r.reroll = 0; return r; }
            r.level = m.level; r.maxLevel = m.maxLevel; r.exp = m.exp; r.need = m.need; r.reroll = m.reroll;
            return r;
        }
        int maxLv = getMaxLevel();
        PlayerState s = playerStates.get(Vars.player.id);
        LevelInfoFull r = new LevelInfoFull();
        if (s == null) { r.level = 0; r.maxLevel = maxLv; r.exp = 0; r.need = (int) levelCost(1); r.reroll = 0; return r; }
        r.level = s.level; r.maxLevel = maxLv; r.exp = (int) s.exp;
        r.need = s.level < maxLv ? (int) levelCost(s.level + 1) : 0;
        r.reroll = s.rerollPts; r.stats = s.stats;
        return r;
    }

    /** Dùng cho {@link Survival#levelInfoProvider} (chỉ cần level/exp/need). */
    public static Survival.LevelInfo getLocalLevelInfo() {
        LevelInfoFull f = getLocalLevelInfoFull();
        if (f == null) return null;
        Survival.LevelInfo i = new Survival.LevelInfo();
        i.level = f.level; i.exp = f.exp; i.need = f.need;
        return i;
    }

    static final class UiSkill {
        String name; Block block; int tier, maxTier; String desc, why; boolean simple;
    }

    static List<UiSkill> getLocalSkills() {
        if (Vars.player == null || !isSurvivalActive()) return List.of();
        PlayerState s = playerStates.get(Vars.player.id);
        if (s == null) return List.of();
        List<UiSkill> res = new ArrayList<>();
        for (Skill sk : s.skills) {
            UiSkill u = new UiSkill();
            u.name = sk.name; u.block = sk.def.block;
            u.tier = Math.min(MAX_TIER_SHOTS, sk.tier); u.maxTier = MAX_TIER_SHOTS;
            u.desc = descOf(sk.def); u.why = sk.why; u.simple = sk.mode != Mode.REAL;
            res.add(u);
        }
        return res;
    }

    static void showSkillList() {
        if (Vars.headless || !isSurvivalActive()) return;
        if (listDialog != null && listDialog.isShown()) { listDialog.hide(); return; }
        if (Core.scene.hasDialog()) return;

        BaseDialog dialog = new BaseDialog(isEn() ? "Equipped skills" : "Kĩ năng đã trang bị");
        dialog.addCloseButton();
        listDialog = dialog;

        LevelInfoFull info = getLocalLevelInfoFull();
        PlayerState state = Vars.player != null ? playerStates.get(Vars.player.id) : null;
        if (info != null) {
            String head = (isEn() ? "Level " : "Cấp ") + info.level + "/" + info.maxLevel
                + (info.need > 0 ? "   EXP " + info.exp + "/" + info.need : "   MAX")
                + ("   " + (isEn() ? "Reroll pts: " : "Điểm đổi: ") + info.reroll);
            dialog.cont.add("[accent]" + head).pad(6f).row();
        }

        if (state != null) {
            List<String> statsLine = new ArrayList<>();
            for (StatSrc s : STAT_SRC) {
                float v = state.stats.getOrDefault(s.stat, 0f);
                if (v > 0) statsLine.add(statName(s) + " +" + (int) v);
            }
            if (!statsLine.isEmpty()) {
                dialog.cont.add("[lightgray]" + String.join("   ", statsLine)).wrap().width(380f).pad(6f).row();
            }
        }

        List<UiSkill> list = getLocalSkills();
        if (list.isEmpty()) {
            dialog.cont.add("[lightgray]" + (isEn() ? "No skills yet. Kill enemies to level up!" : "Chưa có kĩ năng nào. Diệt quái để lên cấp!")).pad(14f).row();
        } else {
            dialog.cont.pane(p -> {
                p.margin(4f);
                for (UiSkill s : list) {
                    Table row = new Table(Styles.black6);
                    row.margin(8f);
                    row.add(new Image(s.block.uiIcon)).size(38f).padRight(10f).top();

                    Table col = new Table();
                    col.left();
                    String simpleTag = s.simple ? "   [scarlet]" + (isEn() ? "(simplified fallback" : "(bản rút gọn") + (s.why != null && !s.why.isEmpty() ? ": " + s.why : "") + ")[]" : "";
                    col.add("[accent]" + s.block.localizedName + "[]" + simpleTag).left().row();
                    Label d = col.add("[lightgray]" + s.desc + "[]").left().growX().get();
                    d.setWrap(true);
                    d.setFontScale(0.9f);
                    row.add(col).growX().width(300f);

                    p.add(row).width(390f).pad(3f).row();
                }
            }).scrollX(false).maxHeight(Core.graphics.getHeight() * 0.65f);
        }
        dialog.show();
    }

    public static List<String> listSkills() {
        List<String> res = new ArrayList<>();
        for (SkillDef d : buildSkillPool()) res.add(d.name);
        return res;
    }

    // ------------------------------------------------------------ init
    public static void init() {
        Survival.levelInfoProvider = SurvivorSkills::getLocalLevelInfo;
        Survival.skillListOpener = SurvivorSkills::showSkillList;
        registerNet();

        Events.on(UnitDestroyEvent.class, e -> {
            if (Vars.net.client() || !isSurvivalActive() || Vars.state.gameOver) return;
            Unit u = e.unit;
            if (u == null || u.team != Vars.state.rules.waveTeam) return;
            float gained = expForKill(u);
            float rx = u.x, ry = u.y;
            Groups.player.each(pl -> {
                Unit pu = pl.unit();
                if (pu == null || !pu.isValid() || pu.dead) return;
                PlayerState st = playerStates.get(pl.id);
                float rad = Math.min(MAX_CREDIT_RADIUS, Math.max(KILL_CREDIT_RADIUS, st != null ? st.maxRange : 0));
                float dx = pu.x - rx, dy = pu.y - ry;
                if (dx * dx + dy * dy <= rad * rad) grantExp(pl, gained);
            });
        });

        Events.on(WorldLoadEvent.class, e -> {
            disposeAll(playerStates);
            playerStates.clear();
            savedByUuid.clear();
            meInfo = null;
            currentOffer = null;
            rosterT = 0; serveT = 0;
            if (listDialog != null) { try { listDialog.hide(); } catch (Throwable ignored) {} listDialog = null; }
        });

        Events.run(Trigger.update, () -> {
            if (!isSurvivalActive() || Vars.state.gameOver || Vars.state.isPaused()) return;
            boolean server = !Vars.net.client();
            List<VirtRef> virts = new ArrayList<>();
            Groups.player.each(pl -> {
                Unit u = pl.unit();
                if (u == null || !u.isValid() || u.dead) return;
                PlayerState state = server ? getState(pl) : playerStates.get(pl.id);
                if (state == null) return;
                if (server || pl == Vars.player) applyPlayerStats(pl, u, state);
                if (state.skills.isEmpty()) return;
                updateOrbiters(pl, u, state);
                for (Skill sk : state.skills) if (sk.mode == Mode.REAL && sk.virt != null) virts.add(new VirtRef(sk.virt, state));
            });
            scaleRealBullets(virts);
            if (server) serverTick();
            purgeT -= Time.delta;
            if (purgeT <= 0) { purgeT = 120; purgeStates(); }
        });

        Events.run(Trigger.draw, () -> {
            if (Vars.headless || !isSurvivalActive()) return;
            Groups.player.each(pl -> {
                Unit u = pl.unit();
                if (u == null || !u.isValid() || u.dead) return;
                PlayerState state = playerStates.get(pl.id);
                if (state == null || state.skills.isEmpty()) return;
                for (int i = 0; i < state.skills.size(); i++) {
                    Skill sk = state.skills.get(i);
                    if (!sk.inited) continue;

                    if (sk.mode == Mode.REAL) drawReal(sk);

                    Draw.z(Layer.flyingUnit + 1);
                    Color col = sk.def.color;
                    int tier = Math.min(MAX_TIER_SHOTS, sk.tier);
                    float outer = 5 + tier * 1.3f;
                    float inner = outer * 0.34f;
                    float bob = Mathf.sin(Time.time * 0.08f + i * 1.7f) * 0.8f;
                    float x = sk.px;
                    float y = sk.py + bob;
                    float spin = Time.time * 1.6f + i * 37;

                    int n = sk.trail.size();
                    for (int j = 0; j < n; j++) {
                        float f = (j + 1) / (float) (n + 1);
                        Draw.color(col, f * 0.3f);
                        Fill.circle(sk.trail.get(j)[0], sk.trail.get(j)[1], outer * 0.22f * f);
                    }

                    Draw.color(Color.black, 0.22f);
                    fillStar4(x + 3, y - 5, outer * 0.9f, inner * 0.9f, spin);

                    Draw.color(col, 0.2f);
                    Fill.circle(x, y, outer * 1.15f);
                    Draw.color(col);
                    fillStar4(x, y, outer, inner, spin);
                    Draw.color(Color.white, 0.9f);
                    fillStar4(x, y, outer * 0.55f, inner * 0.55f, spin);
                    Draw.color(Color.white);
                    Fill.circle(x, y, outer * 0.14f);
                }
                Draw.reset();
            });
        });
    }
}
