package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.struct.Seq;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import newex.MachineUi;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.ui.Styles;

import arc.Events;
import arc.scene.Element;
import mindustry.type.StatusEffect;
import newex.NexNet;
import arc.audio.Sound;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Hệ thống VẬT PHẨM TRANG BỊ của chế độ CHIẾN TRANH (NewMode PvP khi Hỗn loạn tắt).
 *
 * - Quái đợt bị người chơi hạ gục có xác suất rơi vật phẩm theo độ hiếm (càng hiếm càng khó rơi) vào Túi đồ của người đó.
 * - 6 ô trang bị: Vũ khí, Mũ, Giáp, Giày, Nhẫn, Bùa. Chạm ô -> mở Túi đồ -> bấm Lắp.
 * - Mỗi vật phẩm cộng/trừ cấp cho các chỉ số của trang chỉ số cũ (Máu, Giáp, Tấn công...). Trang chỉ số chỉ còn khi bật Hỗn loạn.
 * - Icon lấy từ khối / vật phẩm của game và của mod (thử tên của mod trước, không có thì dùng khối gốc).
 */
final class GearItems {
    private GearItems() {}

    static final String REQ_GEAR = "newex-pvp-gear";
    static final int MAXSLOTS = 24, TYPES = 6, INV_CAP = 80;
    static final String KEY_SLOTS = "newex-pvp-gear-slots", TAG_SLOTS = "newex-pvp-gear-slots-t", FX_KIND = "newex-pvp-gearfx";
    static final int S_WEAPON = 0, S_HELM = 1, S_ARMOR = 2, S_BOOTS = 3, S_RING = 4, S_CHARM = 5;
    static final int R_COMMON = 0, R_RARE = 1, R_EPIC = 2, R_LEGEND = 3, R_MYTHIC = 4, RARITIES = 5, R_SHOP = 5;
    static final int K_TP = 1, K_LUCK = 2, K_HASTE = 3;                 // loại vật phẩm cửa hàng: dịch chuyển / may mắn / tăng tốc (tiêu hao)
    static final int SK_MOVE_ATK = 1, SK_KILL_SPD = 2, SK_KILL_SKILL_ATK = 3, SK_MIN_PT = 4, SK_DEATH_CDR = 5;
    static final int SK_EXEC50 = 6, SK_SLOW_ALL1 = 7, SK_LOWHP_SHIELD = 8, SK_SKILL_EXP = 9, SK_CORE_LASER = 10, SK_TEAM_BUFF = 11, SK_BASIC_ZERA = 12;
    static final int PORT_TICKS = 180, PORT_CD = 1800, HASTE_TICKS = 600;
    static final int MOVE_ATK_STEP = 300, MOVE_ATK_MAX = 15, KILL_SPD_TICKS = 360, SKILL_ATK_MAX = 500;
    static final int LOWHP_SHIELD_CD = 45 * 60, SKILL_EXP_TICKS = 600, RESONANCE_MAX = 1000, RESONANCE_TICKS = 20 * 60;
    static final float MIN_PT_TICKS = 3600f, SLOW_SPEED_LIMIT = 6.9f, CORE_LASER_DPS = 1000f, CORE_LASER_RANGE = 480f;
    static final float DROP_BONUS = 0.10f;                              // +10% (điểm phần trăm) tỉ lệ rơi mỗi Bùa May Mắn
    static StatusEffect hasteStatus, killSpeedStatus;                   // tạo trong NewModePvp.loadStatuses()
    static int teamBuffDefId = -1;                                      // ID của vật phẩm SK_TEAM_BUFF để vẽ icon nhảy lên khi đồng minh nhận buff

    static final String[] SLOT_VI = {"Vũ khí", "Mũ", "Giáp", "Giày", "Nhẫn", "Bùa"};
    static final String[] SLOT_EN = {"Weapon", "Helm", "Armor", "Boots", "Ring", "Charm"};
    static final String[] SLOT_ICON = {"duo", "mender", "thorium-wall", "conveyor", "silicon", "phase-fabric"};
    static final String[] RAR_VI = {"Thường", "Hiếm", "Sử thi", "Huyền thoại", "Thần thoại", "Cửa hàng"};
    static final String[] RAR_EN = {"Common", "Rare", "Epic", "Legendary", "Mythic", "Shop"};
    static final String[] RAR_HEX = {"b8bcc4", "4da6ff", "c084fc", "ffb347", "ff5a5a", "2ee6c8"};
    /** Trọng số rơi theo độ hiếm: càng hiếm càng thấp (tổng 1000 -> 60% / 25% / 10% / 4% / 1%). */
    static final int[] RAR_W = {600, 250, 100, 40, 10};
    /** Sàn cấp hiệu dụng của từng chỉ số (cho phép trừ ở Máu/Giáp/Tấn công/Tốc độ/Tốc độ bắn, tránh chỉ số âm vô nghĩa). */
    static final int[] FLOOR = {-4, -6, -10, -8, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    static final class Def {
        final int id, slot, rarity; final String vi, en; final String[] icons; final int[] mods; // mods: cặp {chỉ số, cấp}
        TextureRegion reg;
        int kind, fixed, skill; String dvi, den, svi, sen;   // kind>0: vật phẩm cửa hàng; skill>0: kỹ năng nội tại khi lắp
        Def(int id, int slot, int rarity, String vi, String en, String icons, int... mods) {
            this.id = id; this.slot = slot; this.rarity = rarity; this.vi = vi; this.en = en; this.icons = icons.split(","); this.mods = mods;
        }
    }

    // chỉ số: 0 Máu, 1 Giáp, 2 Tấn công, 3 Tốc độ di chuyển, 4 Tốc độ bắn, 5 Tỉ lệ bạo kích, 6 ST bạo kích, 7 Xuyên giáp,
    // 8 Hồi máu, 9 ST % máu tối đa, 10 ST theo giáp, 11 Đẩy lùi, 12 Tầm bắn lính, 13 ST quái spawn ngẫu nhiên, 14 Giảm hồi máu
    static final Def[] DEFS = new Def[64];
    static int n = 0;
    static void d(int slot, int rarity, String vi, String en, String icons, int... mods) { DEFS[n] = new Def(n, slot, rarity, vi, en, icons, mods); n++; }
    static {
        // ---- Vũ khí
        d(S_WEAPON, 0, "Súng đồng", "Copper Gun", "duo", 2, 3, 3, -1);
        d(S_WEAPON, 0, "Nỏ gỉ sét", "Rusty Crossbow", "hail", 4, 3, 5, 1);
        d(S_WEAPON, 1, "Pháo tia lửa", "Spark Cannon", "arc,lancer", 2, 5, 4, 3, 14, 4);
        d(S_WEAPON, 1, "Kiếm laser", "Laser Sword", "swordoder,lancer", 2, 4, 6, 3, 14, 4);
        d(S_WEAPON, 2, "Búa Therdum", "Therdum Hammer", "therdum,salvo", 2, 7, 9, 4, 3, -2, 14, 8);
        d(S_WEAPON, 3, "Pháo Xylaon", "Xylaon Cannon", "xylaon,ripple", 2, 10, 7, 6, 13, 6, 3, -3, 14, 14);
        d(S_WEAPON, 4, "Thần kiếm Heliyron", "Heliyron Godblade", "heliyron,spectre", 2, 16, 4, 8, 6, 8, 9, 6, 3, -4, 1, -3, 14, 22);
        // ---- Mũ
        d(S_HELM, 0, "Mũ đồng", "Copper Cap", "copper-wall,mender", 0, 2, 7, 1);
        d(S_HELM, 0, "Kính ngắm vụn", "Scrap Scope", "scrap-wall,scrap", 5, 3, 12, 1);
        d(S_HELM, 1, "Mũ chiến binh", "Warrior Helm", "titanium-wall,mender", 0, 3, 7, 3, 8, 1);
        d(S_HELM, 1, "Kính xạ thủ", "Marksman Visor", "lancer,mender", 5, 4, 7, 4);
        d(S_HELM, 2, "Mũ Korynd", "Korynd Helm", "korynd,thorium-wall", 0, 5, 1, 3, 12, 4, 3, -2);
        d(S_HELM, 3, "Vương miện Sallowyr", "Sallowyr Crown", "sallowyr,force-projector", 7, 8, 8, 5, 5, 4, 4, -3);
        d(S_HELM, 4, "Mão Hư Vô", "Void Diadem", "newex,surge-wall", 7, 12, 5, 8, 12, 6, 0, -3, 4, -3);
        // ---- Giáp
        d(S_ARMOR, 0, "Áo vải", "Cloth Vest", "copper-wall", 0, 2, 1, 1, 3, -1);
        d(S_ARMOR, 0, "Giáp da", "Leather Armor", "scrap-wall-large,copper-wall-large", 1, 3, 0, 1);
        d(S_ARMOR, 1, "Giáp titan", "Titan Plate", "titanium-wall-large,titanium-wall", 1, 4, 0, 3, 3, -1);
        d(S_ARMOR, 1, "Áo giáp phản lực", "Reactive Vest", "plastanium-wall,thorium-wall", 1, 3, 11, 4, 4, -1);
        d(S_ARMOR, 2, "Giáp Thorium", "Thorium Mail", "thorium-wall-large", 0, 6, 1, 6, 10, 3, 3, -3);
        d(S_ARMOR, 3, "Giáp Nethyr", "Nethyr Plate", "nethyr,plastanium-wall-large", 0, 8, 1, 9, 8, 4, 11, 4, 3, -4);
        d(S_ARMOR, 4, "Chiến giáp Raykstone", "Raykstone Battle Armor", "raykstone,surge-wall-large", 0, 13, 1, 12, 10, 8, 8, 5, 3, -4, 2, -3);
        // ---- Giày
        d(S_BOOTS, 0, "Giày vải", "Cloth Boots", "conveyor", 3, 3, 0, -1);
        d(S_BOOTS, 0, "Giày băng chuyền", "Conveyor Boots", "titanium-conveyor,conveyor", 3, 2, 4, 1);
        d(S_BOOTS, 1, "Giày nhẹ", "Light Boots", "junction,router", 3, 5, 4, 2);
        d(S_BOOTS, 1, "Giày thúc đẩy", "Thruster Boots", "overflow-gate,titanium-conveyor", 3, 4, 11, 3, 1, -1);
        d(S_BOOTS, 2, "Giày Zoj", "Zoj Boots", "zoj,plastanium-conveyor", 3, 8, 4, 5, 11, 2, 1, -2);
        d(S_BOOTS, 3, "Giày Plasanod", "Plasanod Boots", "plasanod,armored-conveyor", 3, 11, 4, 8, 8, 3, 0, -3, 1, -2);
        d(S_BOOTS, 4, "Giày Ánh Sáng", "Lightstep Boots", "starlight,phase-conveyor", 3, 16, 4, 12, 8, 5, 11, 3, 2, -4, 0, -4);
        // ---- Nhẫn
        d(S_RING, 0, "Nhẫn đồng", "Copper Ring", "copper", 6, 2, 8, 1);
        d(S_RING, 0, "Nhẫn chì", "Lead Ring", "lead", 9, 1, 8, 2);
        d(S_RING, 1, "Nhẫn bạc", "Silver Ring", "silicon", 6, 4, 9, 2);
        d(S_RING, 1, "Nhẫn titan", "Titan Ring", "titanium", 8, 4, 2, 2, 3, -1);
        d(S_RING, 2, "Nhẫn Uranilum", "Uranilum Ring", "uranilum,thorium", 6, 7, 9, 3, 8, 3, 2, -2);
        d(S_RING, 3, "Nhẫn Obsidis", "Obsidis Ring", "obsidis,surge-alloy", 6, 10, 9, 6, 8, 6, 4, -3);
        d(S_RING, 4, "Nhẫn Starlight", "Starlight Ring", "starlight,phase-fabric", 6, 16, 9, 9, 5, 8, 8, 8, 2, -3, 0, -3);
        // ---- Bùa
        d(S_CHARM, 0, "Bùa bào tử", "Spore Charm", "spore-pod", 8, 2, 12, 2, 1, -1);
        d(S_CHARM, 0, "Bùa than", "Coal Charm", "coal", 13, 3, 11, 1);
        d(S_CHARM, 1, "Bùa plastanium", "Plastanium Charm", "plastanium", 12, 4, 13, 3, 3, -1);
        d(S_CHARM, 1, "Bùa kích thích", "Stimulant Charm", "pyratite,blast-compound", 4, 4, 13, 4);
        d(S_CHARM, 2, "Bùa Berrybyr", "Berrybyr Charm", "berrybyr,phase-fabric", 13, 8, 12, 4, 8, 3, 0, -2);
        d(S_CHARM, 3, "Bùa Sallowyr", "Sallowyr Charm", "sallowyr,surge-alloy", 13, 12, 12, 6, 9, 5, 2, -3, 3, -3, 14, 10);
        d(S_CHARM, 4, "Bùa Pha Lê Hư Vô", "Void Crystal Charm", "newex-obsidis,newex-starlight", 13, 18, 12, 10, 9, 8, 7, 8, 1, -4, 3, -4);
        // ---- Vật phẩm cửa hàng (chỉ mua, không rơi từ quái đợt)
        shop("Bùa Dịch Chuyển", "Recall Charm", "phase-fabric,phase-conveyor", K_TP, 1,
            "Kỹ năng: đứng yên 3s để dịch chuyển về căn cứ (hồi 30s). Nút hiện ở góc dưới bên phải khi có trong túi hoặc đã lắp.", "Skill: stand still 3s to teleport to base (30s cooldown). Button at bottom-right while in bag or equipped.");
        shop("Bùa May Mắn", "Lucky Charm", "surge-alloy,plastanium", K_LUCK, 3,
            "Nội tại (cần lắp): +10% tỉ lệ kích hoạt rơi vật phẩm từ quái đợt.", "Passive (equip): +10% item drop chance from wave monsters.");
        shop("Nước Tăng Tốc", "Haste Potion", "pyratite,overdrive-projector", K_HASTE, 1,
            "Tiêu hao: +150% tốc độ di chuyển trong 10s. Nút ở góc dưới bên phải khi có trong túi.", "Consumable: +150% move speed for 10s. Button at bottom-right while in bag.");
        // ---- Vật phẩm kỹ năng chế độ Chiến tranh (giữ ID sau 44 để tương thích dữ liệu lưu cũ)
        sk(S_BOOTS, R_EPIC, "Giày Xung Phong", "Charge Boots", "plastanium-conveyor,titanium-conveyor", SK_MOVE_ATK,
            "Kỹ năng: Khi di chuyển, mỗi 5s tăng 1% tấn công (tối đa 15%).",
            "Skill: While moving, +1% attack every 5s (max 15%).", 3, 5, 2, 3);
        sk(S_WEAPON, R_EPIC, "Song Đao Truy Kích", "Pursuit Blades", "swordoder,arc", SK_KILL_SPD,
            "Kỹ năng: Khi tiêu diệt mục tiêu, nhận tăng tốc độ 50% trong 6s.",
            "Skill: On kill, gain +50% speed for 6s.", 2, 6, 4, 4);
        sk(S_RING, R_MYTHIC, "Nhẫn Huyết Chú", "Bloodspell Ring", "starlight,surge-alloy", SK_KILL_SKILL_ATK,
            "Kỹ năng: Khi tiêu diệt mục tiêu, tăng 1% tấn công cho các kỹ năng của unit player (tối đa 500%).",
            "Skill: On kill, +1% attack for player unit skills (max 500%).", 2, 6, 6, 6, 7, 4);
        sk(S_CHARM, R_LEGEND, "Bùa Tài Lộc", "Fortune Charm", "surge-alloy,phase-fabric", SK_MIN_PT,
            "Kỹ năng: Mỗi phút nhận 1 điểm.",
            "Skill: Gain 1 point every minute.", 8, 3, 12, 3);
        sk(S_ARMOR, R_LEGEND, "Giáp Phục Hận", "Vengeance Armor", "surge-wall-large,thorium-wall-large", SK_DEATH_CDR,
            "Kỹ năng: Khi bị kẻ địch là player tiêu diệt, giảm 50% thời gian hồi sinh và hồi chiêu kỹ năng pilot.",
            "Skill: When killed by an enemy player, -50% respawn time and pilot skill cooldown.", 0, 5, 1, 6);
        sk(S_WEAPON, R_EPIC, "Kiếm Kết Liễu", "Executioner Blade", "swordoder,thorium", SK_EXEC50,
            "Kỹ năng: Gây thêm 15% sát thương lên mục tiêu dưới 50% máu.",
            "Skill: Deal +15% bonus damage to targets below 50% HP.", 2, 5, 5, 3);
        sk(S_ARMOR, R_LEGEND, "Giáp Trọng Lực", "Gravity Plate", "raykstone,plastanium-wall-large", SK_SLOW_ALL1,
            "Kỹ năng: Khi tốc độ di chuyển dưới 6,9 ô/giây, tất cả chỉ số tăng +1 cấp.",
            "Skill: While move speed is below 6.9 tiles/s, all stats gain +1 level.", 0, 4, 1, 5, 3, -2);
        sk(S_HELM, R_LEGEND, "Khiên Sinh Mệnh", "Lifeline Aegis", "force-projector,phase-fabric", SK_LOWHP_SHIELD,
            "Kỹ năng: Khi máu dưới 50%, nhận 1 lớp khiên bằng 50% máu tối đa (hồi chiêu 45s).",
            "Skill: Below 50% HP, gain a shield equal to 50% max HP (45s cooldown).", 0, 5, 8, 3);
        sk(S_CHARM, R_EPIC, "Ngọc Tinh Thông", "Mastery Orb", "starlight,silicon", SK_SKILL_EXP,
            "Kỹ năng: Tiêu diệt mục tiêu bằng kỹ năng của unit nhận +150 EXP; khi nhấn dùng kỹ năng nhận +50% EXP trong 10s (không cộng dồn).",
            "Skill: Killing a target with a unit skill grants +150 EXP; casting a skill grants +50% EXP for 10s (non-stacking).", 4, 3, 6, 3);
        sk(S_RING, R_MYTHIC, "Lõi Quang Tuyến", "Prism Core", "newex-starlight,lancer", SK_CORE_LASER,
            "Kỹ năng: Giảm mọi chỉ số -1 cấp; đổi lại Lõi bắn 1 tia laser gây 1000 ST/giây (+1% máu tối đa lên unit player, giảm 20% lên unit lính).",
            "Skill: -1 to all stats; in exchange your Core fires a laser dealing 1000 dmg/s (+1% max HP vs player units, -20% vs soldiers).", 2, 1);
        teamBuffDefId = n;
        sk(S_CHARM, R_MYTHIC, "Ấn Quang Huy", "Radiant Crest", "newex-berrybyr,surge-alloy", SK_TEAM_BUFF,
            "Kỹ năng: Đánh trúng mục tiêu tích 1 tầng Tinh Quang Cộng Hưởng; đủ 1000 tầng giúp cả phe nhận +10% Tấn công & +10% Giáp trong 20s (chỉ tích lại sau khi hết buff).",
            "Skill: On hit, gain 1 stack of Astral Resonance; at 1000 stacks, grants the whole team +10% ATK & +10% Armor for 20s (re-stacks only after buff ends).",
            0, 1, 1, 1, 2, 1, 3, 1, 4, 1, 5, 1, 6, 1, 7, 1, 8, 1, 9, 1, 10, 1, 11, 1, 12, 1, 13, 1, 14, 1);
        sk(S_WEAPON, R_MYTHIC, "Pháo Ấn Sư Tử", "Lionstrike Core", "zeralion,spectre,lancer", SK_BASIC_ZERA,
            "Kỹ năng: Tăng 50% sát thương gây ra cho các đòn đánh thường; nếu đang dùng unit Zeralion thì tăng thêm 100%.",
            "Skill: +50% damage for basic attacks; +100% additional damage when piloting Zeralion.", 2, 6, 4, 4);
    }
    static void shop(String vi, String en, String icons, int kind, int price, String dvi, String den) {
        Def x = new Def(n, S_CHARM, R_SHOP, vi, en, icons);
        x.kind = kind; x.fixed = price; x.dvi = dvi; x.den = den;
        DEFS[n] = x; n++;
    }
    static void sk(int slot, int rarity, String vi, String en, String icons, int skill, String svi, String sen, int... mods) {
        Def x = new Def(n, slot, rarity, vi, en, icons, mods);
        x.skill = skill; x.svi = svi; x.sen = sen;
        DEFS[n] = x; n++;
    }

    // ------------------------------------------------------------------ truy cập chung
    static String tr(String vi, String en) { return NewModePvp.tr(vi, en); }
    /** Chế độ CHIẾN TRANH: trận PvP đang chạy và Hỗn loạn tắt. */
    static boolean war() {
        try { return NewModePvp.isActive() && !ChaosMode.on(); } catch (Throwable ignored) { return false; }
    }
    static String col(int r) { return "[#" + RAR_HEX[r] + "]"; }
    static Color colorOf(int r) { return Color.valueOf(RAR_HEX[Math.max(0, Math.min(RAR_HEX.length - 1, r))]); }
    static String name(Def d) { return tr(d.vi, d.en); }
    static String slotName(int s) { return tr(SLOT_VI[s % TYPES], SLOT_EN[s % TYPES]) + (s >= TYPES ? " " + (s / TYPES + 1) : ""); }
    static int[] newEq() { int[] a = new int[MAXSLOTS]; Arrays.fill(a, -1); return a; }
    static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    /** Số ô trang bị (6 - 24): máy chủ ghi vào rules.tags lúc vào trận để mọi người dùng chung giá trị. */
    static int slots() {
        try {
            arc.struct.StringMap tags = NewModePvp.getTags();
            if (tags != null && tags.containsKey(TAG_SLOTS)) return clampI(Integer.parseInt(tags.get(TAG_SLOTS, String.valueOf(MAXSLOTS))), TYPES, MAXSLOTS);
        } catch (Throwable ignored) {}
        return clampI(Core.settings.getInt(KEY_SLOTS, MAXSLOTS), TYPES, MAXSLOTS);
    }
    static void stampTag() { try { Vars.state.rules.tags.put(TAG_SLOTS, String.valueOf(clampI(Core.settings.getInt(KEY_SLOTS, MAXSLOTS), TYPES, MAXSLOTS))); } catch (Throwable ignored) {} }
    static String rarName(int r) { return tr(RAR_VI[r], RAR_EN[r]); }
    static Def def(int id) { return id >= 0 && id < n ? DEFS[id] : null; }

    // ------------------------------------------------------------------ icon
    static UnlockableContent find(String nm) {
        ContentType[] ts = {ContentType.block, ContentType.item, ContentType.unit};
        String[] names = {nm, "newex-" + nm};
        for (String s : names) for (ContentType t : ts) {
            try {
                Content c = Vars.content.getByName(t, s);
                if (c instanceof UnlockableContent) {
                    UnlockableContent u = (UnlockableContent) c;
                    if (u.uiIcon != null && Core.atlas.isFound(u.uiIcon)) return u;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
    static TextureRegion icon(Def d) {
        if (d == null) return Core.atlas.find("error");
        if (d.reg != null) return d.reg;
        for (String s : d.icons) {
            UnlockableContent u = find(s.trim());
            if (u != null) { d.reg = u.uiIcon; return d.reg; }
        }
        UnlockableContent u = find(SLOT_ICON[d.slot]);
        if (u == null) u = find("copper");
        TextureRegion r = u != null ? u.uiIcon : Core.atlas.find("error");
        d.reg = r;
        return r;
    }
    static TextureRegion slotIcon(int s) {
        UnlockableContent u = find(SLOT_ICON[s]);
        return u != null ? u.uiIcon : Core.atlas.find("error");
    }

    // ------------------------------------------------------------------ chỉ số cộng thêm
    static void refreshDelta(NewModePvp.St st) {
        int giftHash = st.pgift == null ? 0 : st.pgift.hashCode();
        int key = (((Arrays.hashCode(st.gEq) * 31 + slots()) * 31 + st.pi) * 31 + giftHash) * 2 + (st.gSlowBonus ? 1 : 0);
        if (key == st.gKey) return;
        st.gKey = key;
        Arrays.fill(st.gDelta, 0);
        boolean any = false;
        int mask = 0;
        for (int s = 0, ns = slots(); s < ns; s++) {
            Def d = def(st.gEq[s]);
            if (d == null) continue;
            if (d.skill > 0) mask |= (1 << d.skill);
            for (int i = 0; i + 1 < d.mods.length; i += 2) {
                if (d.mods[i] >= 0 && d.mods[i] < st.gDelta.length) { st.gDelta[d.mods[i]] += d.mods[i + 1]; any = true; }
            }
        }
        // Quà đã tặng cho pilot đang chiêu mộ: chỉ lấy chỉ số buff dương, bỏ toàn bộ debuff âm!
        if (st.pi > 0 && st.pgift != null) {
            for (int i = 0; i < st.pgift.size(); i++) {
                int code = st.pgift.get(i);
                if ((code >> 8) != st.pi) continue;
                Def gd = def(code & 0xFF);
                if (gd == null) continue;
                for (int j = 0; j + 1 < gd.mods.length; j += 2) {
                    int idx = gd.mods[j], val = gd.mods[j + 1];
                    if (idx >= 0 && idx < st.gDelta.length && val > 0) { st.gDelta[idx] += val; any = true; }
                }
            }
        }
        // Giáp Trọng Lực: tốc độ < 6.9 ô/s -> tăng +1 cấp mọi chỉ số (hiện rõ trong bảng thông tin)
        if (st.gSlowBonus && (mask & (1 << SK_SLOW_ALL1)) != 0) {
            for (int i = 0; i < st.gDelta.length; i++) st.gDelta[i] += 1;
            any = true;
        }
        // Lõi Quang Tuyến: giảm mọi chỉ số -1 cấp
        if ((mask & (1 << SK_CORE_LASER)) != 0) {
            for (int i = 0; i < st.gDelta.length; i++) st.gDelta[i] -= 1;
            any = true;
        }
        st.gAny = any;
        st.gSkills = mask;
    }
    /** Kiểm tra người chơi có đang lắp vật phẩm mang kỹ năng sk không (chỉ ở chế độ Chiến tranh). */
    static boolean hasSkill(NewModePvp.St st, int sk) {
        if (st == null || !war()) return false;
        refreshDelta(st);
        return (st.gSkills & (1 << sk)) != 0;
    }
    /** Hệ số tấn công cộng thêm từ kỹ năng di chuyển (+1% mỗi 5s, tối đa +15%). */
    static float moveAtkMul(NewModePvp.St st) {
        if (st == null || st.gMoveAtk <= 0 || !hasSkill(st, SK_MOVE_ATK)) return 1f;
        return 1f + Math.min(MOVE_ATK_MAX, st.gMoveAtk) / 100f;
    }
    /** Hệ số tấn công cộng thêm cho kỹ năng của unit player (+1% mỗi lần tiêu diệt, tối đa +500%). */
    static float skillAtkMul(Unit owner) {
        if (owner == null || !war()) return 1f;
        Player op = NewModePvp.ownerPl(owner);
        if (op == null) return 1f;
        NewModePvp.St st = NewModePvp.byId.get(op.id);
        if (st == null || st.gSkillAtk <= 0 || !hasSkill(st, SK_KILL_SKILL_ATK)) return 1f;
        return 1f + Math.min(SKILL_ATK_MAX, st.gSkillAtk) / 100f;
    }
    static boolean hasDeathCdr(NewModePvp.St st) { return hasSkill(st, SK_DEATH_CDR); }
    /** Mảng cấp cộng thêm từ vật phẩm; null nếu không ở chế độ chiến tranh hoặc chưa lắp gì. */
    static int[] delta(NewModePvp.St st) {
        if (st == null || !war()) return null;
        refreshDelta(st);
        return st.gAny ? st.gDelta : null;
    }
    /** Mảng chỉ số (st.s + vật phẩm) cho những chỗ đọc chỉ số thô; không cấp phát. */
    static int[] eff(NewModePvp.St st) {
        if (st == null) return null;
        int[] g = delta(st);
        if (g == null) return st.s;
        int[] out = st.gEff;
        for (int i = 0; i < out.length; i++) {
            int v = (i < st.s.length ? st.s[i] : 0) + (i < g.length ? g[i] : 0);
            out[i] = Math.max(FLOOR[Math.min(i, FLOOR.length - 1)], v);
        }
        return out;
    }
    static int floorOf(int i) { return FLOOR[Math.max(0, Math.min(FLOOR.length - 1, i))]; }

    // ------------------------------------------------------------------ lưu / đồng bộ
    static String enc(NewModePvp.St st) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.gInv.size(); i++) { if (i > 0) sb.append(','); sb.append(st.gInv.get(i)); }
        sb.append(';');
        for (int i = 0; i < MAXSLOTS; i++) { if (i > 0) sb.append(','); sb.append(st.gEq[i]); }
        if (st.gMoveAtk > 0 || st.gSkillAtk > 0) sb.append(';').append(st.gMoveAtk).append(',').append(st.gSkillAtk);
        return sb.toString();
    }
    static void dec(NewModePvp.St st, String s) {
        st.gInv.clear(); Arrays.fill(st.gEq, -1); st.gKey = Integer.MIN_VALUE; st.gSkills = 0; st.gMoveAtk = 0; st.gSkillAtk = 0;
        if (s == null || s.isEmpty()) return;
        try {
            String[] p = s.split(";", -1);
            if (p.length > 0 && !p[0].isEmpty()) for (String c : p[0].split(",")) { int id = Integer.parseInt(c.trim()); if (def(id) != null) st.gInv.add(id); }
            if (p.length > 1 && !p[1].isEmpty()) {
                String[] q = p[1].split(",");
                for (int i = 0; i < MAXSLOTS && i < q.length; i++) { int id = Integer.parseInt(q[i].trim()); st.gEq[i] = (def(id) != null && def(id).slot == i % TYPES) ? id : -1; }
            }
            if (p.length > 2 && !p[2].isEmpty()) {
                String[] r = p[2].split(",");
                if (r.length > 0) st.gMoveAtk = clampI(Integer.parseInt(r[0].trim()), 0, MOVE_ATK_MAX);
                if (r.length > 1) st.gSkillAtk = clampI(Integer.parseInt(r[1].trim()), 0, SKILL_ATK_MAX);
            }
        } catch (Throwable ignored) {}
    }
    static String sig(NewModePvp.St st) { return st == null ? "" : st.gInv + "|" + Arrays.toString(st.gEq) + "|" + st.gMoveAtk + "|" + st.gSkillAtk; }

    // ------------------------------------------------------------------ rơi vật phẩm
    static Player playerById(int pid) {
        final Player[] out = {null};
        try { NewModePvp.eachPl(p -> { if (p != null && p.id == pid) out[0] = p; }); } catch (Throwable ignored) {}
        return out[0];
    }
    /** Xác suất rơi theo máu tối đa của quái: yếu ~6%, càng trâu càng cao, tối đa 60%. */
    static float dropChance(Unit u) { return Math.min(0.6f, 0.06f + u.maxHealth / 4000f * 0.12f); }
    static float luckBonus(NewModePvp.St st) {
        int c = 0;
        for (int i = 0, ns = slots(); i < ns; i++) { Def d = def(st.gEq[i]); if (d != null && d.kind == K_LUCK) c++; }
        return c * DROP_BONUS;
    }
    static int rollRarity(float hp) {
        float boost = Math.min(3f, hp / 4000f);            // quái trâu đẩy trọng số lên các bậc hiếm
        float[] w = new float[RARITIES];
        float tot = 0;
        for (int i = 0; i < RARITIES; i++) { w[i] = RAR_W[i] * (1f + boost * i * 0.4f); tot += w[i]; }
        float r = (float) Math.random() * tot;
        for (int i = 0; i < RARITIES; i++) { r -= w[i]; if (r <= 0) return i; }
        return 0;
    }
    static Def rollDef(int rarity) {
        ArrayList<Def> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) if (DEFS[i].rarity == rarity) pool.add(DEFS[i]);
        return pool.isEmpty() ? DEFS[0] : pool.get((int) (Math.random() * pool.size()));
    }
    /** Máy chủ gọi khi quái đợt chết: nếu người chơi pid là người hạ gục thì có thể nhận 1 vật phẩm. */
    static void onWaveKill(Unit u, int pid) {
        if (Vars.net.client() || u == null || !NewModePvp.isFull() || !war() || Vars.state.gameOver) return;
        Integer cid = carry.remove(u.id);
        NewModePvp.St st = pid < 0 ? null : NewModePvp.byId.get(pid);
        if (st == null) return;
        if (cid != null) {                                   // quái mang vật phẩm bị bỏ: người hạ gục nhận chắc chắn
            Def cd = def(cid);
            if (cd == null) return;
            st.gInv.add(cd.id);
            Player cp = playerById(pid);
            if (cp != null) {
                if (NewModePvp.isBotPl(cp)) botAutoEquip(st);
                else NewModePvp.toast(cp, tr("[accent]Nhận vật phẩm từ quái: ", "[accent]Item from monster: ") + col(cd.rarity) + name(cd) + " [" + rarName(cd.rarity) + "][]");
                announcePickup(cp, cd);
            }
            playFx(u, pid, cd.rarity);
            NewModePvp.changed();
            return;
        }
        if (Math.random() >= Math.min(0.95f, dropChance(u) + luckBonus(st))) return;
        if (st.gInv.size() >= INV_CAP) return;
        Def d = rollDef(rollRarity(u.maxHealth));
        st.gInv.add(d.id);
        Player pl = playerById(pid);
        if (pl != null) {
            if (NewModePvp.isBotPl(pl)) botAutoEquip(st);
            else NewModePvp.toast(pl, tr("[accent]Nhận vật phẩm: ", "[accent]Item received: ") + col(d.rarity) + name(d) + " [" + rarName(d.rarity) + "][]" + tr(" - xem trong Túi đồ", " - see your Bag"));
            announcePickup(pl, d);
        }
        playFx(u, pid, d.rarity);
        NewModePvp.changed();
    }
    static int power(Def d) { int p = 0; for (int i = 0; i + 1 < d.mods.length; i += 2) p += d.mods[i + 1]; return p + d.rarity * 3 + (d.skill > 0 ? 6 : 0); }
    /** Bot tự lắp vật phẩm tốt hơn vào ô tương ứng. */
    static void botAutoEquip(NewModePvp.St st) {
        for (int k = st.gInv.size() - 1; k >= 0; k--) {
            Def d = def(st.gInv.get(k));
            if (d == null || d.rarity >= R_SHOP) continue;
            Def cur = def(st.gEq[d.slot]);
            if (cur == null || power(d) > power(cur)) {
                st.gInv.remove(k);
                if (cur != null) st.gInv.add(cur.id);
                st.gEq[d.slot] = d.id;
            }
        }
    }

    // ------------------------------------------------------------------ hiệu ứng tia sáng bay về người chơi
    /** Điểm sáng màu theo độ hiếm bay cong (lên/xuống ngẫu nhiên) từ xác quái tới người nhận; e.rotation = độ cong, e.data = đích.
     *  Bay chậm lúc đầu rồi tăng tốc dần (ease-in) để tạo cảm giác "hút" vật phẩm về phía mình; vệt theo sau là chuỗi hạt tròn mờ dần
     *  (không dùng đường thẳng quanh điểm sáng) để trông mượt và sạch hơn, giống hiệu ứng thu thập trong các game gacha/idle. */
    static final Effect beamFx = new Effect(48f, 900f, e -> {
        if (!(e.data instanceof Position)) return;
        Position t = (Position) e.data;
        float x0 = e.x, y0 = e.y, x1 = t.getX(), y1 = t.getY();
        float dx = x1 - x0, dy = y1 - y0, dist = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float cx = (x0 + x1) / 2f - dy / dist * e.rotation * dist * 0.5f, cy = (y0 + y1) / 2f + dx / dist * e.rotation * dist * 0.5f;
        float lin = Mathf.clamp(e.fin());
        float travel = Mathf.clamp(lin / 0.87f);             // mốc 0.87 = lúc chạm đích, sau đó chỉ còn mờ dần
        float eased = travel * travel * travel;               // ease-in: chậm hẳn lúc đầu rồi tăng tốc liên tục tới khi chạm đích
        float head = Mathf.clamp(eased);
        float fade = 1f - Mathf.clamp((lin - 0.85f) / 0.15f);
        // vệt sáng: chuỗi hạt tròn nhỏ dần + mờ dần phía sau, tuyệt đối không dùng đường thẳng quanh điểm sáng
        int trailN = 8;
        for (int k = trailN; k >= 1; k--) {
            float q = Math.max(0f, head - k * 0.05f), iq = 1f - q;
            float bx = iq * iq * x0 + 2f * iq * q * cx + q * q * x1, by = iq * iq * y0 + 2f * iq * q * cy + q * q * y1;
            float frac = 1f - k / (float) (trailN + 1);
            Draw.color(e.color, fade * frac * 0.65f);
            Fill.circle(bx, by, Math.max(0.6f, 3.4f * frac));
        }
        // điểm sáng ở đầu: quầng sáng mờ (glow) + lõi màu + tâm trắng sáng, lớn nhẹ lúc gần chạm đích cho cảm giác "nạp năng lượng"
        float iq0 = 1f - head;
        float hx = iq0 * iq0 * x0 + 2f * iq0 * head * cx + head * head * x1;
        float hy = iq0 * iq0 * y0 + 2f * iq0 * head * cy + head * head * y1;
        float pulse = 1f + 0.35f * Mathf.clamp(head) * head;
        Draw.color(e.color, fade * 0.3f);
        Fill.circle(hx, hy, 6.6f * pulse);
        Draw.color(e.color, fade);
        Fill.circle(hx, hy, 3.6f * pulse);
        Draw.color(1f, 1f, 1f, fade);
        Fill.circle(hx, hy, 1.7f * pulse);
        Draw.reset();
    });
    static void showFx(float x, float y, int rarity, float curve, Unit target) {
        if (Vars.headless || target == null) return;
        try { beamFx.at(x, y, curve, colorOf(rarity), target); } catch (Throwable ignored) {}
        try { if (Vars.player != null && target == Vars.player.unit()) Time.run(40f, () -> playGet(rarity)); } catch (Throwable ignored) {}
    }
    /** Máy chủ/host: phát tia ở máy mình và gửi cho mọi client để ai cũng thấy. */
    static void playFx(Unit src, int pid, int rarity) {
        try {
            Unit tg = NewModePvp.plUnit(pid);
            if (tg == null || !tg.isValid()) return;
            float curve = (0.3f + (float) Math.random() * 0.5f) * (Math.random() < 0.5 ? -1f : 1f);
            showFx(src.x, src.y, rarity, curve, tg);
            if (Vars.net.server()) Call.clientPacketReliable(FX_KIND, pid + "|" + src.x + "|" + src.y + "|" + rarity + "|" + curve);
        } catch (Throwable ignored) {}
    }
    static void onFxPacket(String content) {
        if (!Vars.net.client()) return;   // host đã tự phát ở playFx
        try {
            String[] a = content.split("\\|");
            Unit tg = NewModePvp.plUnit(Integer.parseInt(a[0]));
            if (tg != null) showFx(Float.parseFloat(a[1]), Float.parseFloat(a[2]), Integer.parseInt(a[3]), Float.parseFloat(a[4]), tg);
        } catch (Throwable ignored) {}
    }


    // ------------------------------------------------------------------ quái mang vật phẩm (bỏ vật phẩm -> hóa quái)
    static final String RING_KIND = "newex-pvp-gearring";
    /** id quái -> id vật phẩm nó đang mang (máy chủ). Không tính vào giới hạn quái đợt. */
    static final HashMap<Integer, Integer> carry = new HashMap<>();
    /** Vòng tròn màu độ hiếm dưới chân quái; vẽ theo vị trí thật của quái (e.data = id quái), nên sống tới khi quái chết. */
    static final Effect ringFx = new Effect(1e6f, 1e6f, e -> {
        if (!(e.data instanceof Integer)) return;
        Unit u = Groups.unit.getByID((Integer) e.data);
        if (u == null || !u.isValid()) return;
        float t = Time.time, rad = Math.max(12f, u.hitSize * 0.8f + 4f), cx = u.x, cy = u.y - u.hitSize * 0.12f;
        float pulse = 0.5f + 0.5f * Mathf.sin(t / 18f), k = (t / 70f) % 1f;
        Draw.color(e.color, 0.10f + 0.07f * pulse);
        Fill.circle(cx, cy, rad);
        Lines.stroke(1.8f);
        Draw.color(e.color, 0.9f);
        Lines.circle(cx, cy, rad);
        Lines.stroke(1.4f * (1f - k));
        Draw.color(e.color, 0.7f * (1f - k));
        Lines.circle(cx, cy, rad * (0.6f + 0.9f * k));
        Lines.stroke(2.4f);
        Draw.color(Color.white, 0.75f);
        for (int i = 0; i < 4; i++) Lines.arc(cx, cy, rad + 3f, 0.12f, t * 1.6f + i * 90f);
        Draw.reset();
    });
    static { ringFx.layer = Layer.groundUnit - 1f; }
    static void showRing(int uid, int rarity) {
        if (Vars.headless) return;
        try { ringFx.at(0f, 0f, 0f, colorOf(rarity), uid); } catch (Throwable ignored) {}
    }
    static void onRingPacket(String content) {
        if (!Vars.net.client()) return;
        try { String[] a = content.split("\\|"); showRing(Integer.parseInt(a[0]), Integer.parseInt(a[1])); } catch (Throwable ignored) {}
    }
    /** Chọn quái đợt ngẫu nhiên (độ hiếm cao -> loại trâu hơn), đặt xuống bản đồ, gắn vật phẩm. Bỏ qua giới hạn 200 quái. */
    static boolean spawnCarrier(Def d) {
        try {
            ArrayList<UnitType> ts = new ArrayList<>();
            for (NewModePvp.Wave g : NewModePvp.WAVES) {
                UnitType t = Vars.content.getByName(ContentType.unit, g.t);
                if (t != null && !t.flying && !t.naval) ts.add(t);
            }
            if (ts.isEmpty()) return false;
            ts.sort((a, b) -> Float.compare(a.health, b.health));
            int idx = Mathf.clamp(Math.round(d.rarity / (float) (RARITIES - 1) * (ts.size() - 1) + ((float) Math.random() - 0.5f) * 1.6f), 0, ts.size() - 1);
            UnitType type = ts.get(idx);
            NewModePvp.allowWaveUnits(Vars.state.rules);
            NewModePvp.prepareWaveType(type);
            float[] pos = NewModePvp.spawnPos(type);
            if (pos == null) return false;
            Unit u = type.create(Vars.state.rules.waveTeam);
            u.shield = Math.max(0, Vars.state.wave - 1) * 6f + Math.min(d.rarity, RARITIES - 1) * 150f;
            u.set(pos[0], pos[1]);
            u.apply(StatusEffects.invincible, 60f);
            u.add();
            carry.put(u.id, d.id);
            Events.fire(new mindustry.game.EventType.UnitSpawnEvent(u));
            NewModePvp.fx(pos[0], pos[1]);
            showRing(u.id, d.rarity);
            if (Vars.net.server()) Call.clientPacketReliable(RING_KIND, u.id + "|" + d.rarity);
            return true;
        } catch (Throwable err) { return false; }
    }

    // ------------------------------------------------------------------ âm thanh nhận vật phẩm (sounds/item-get-0..4.ogg, càng hiếm càng dài/hay)
    static final Sound[] getSnd = new Sound[RARITIES];
    static void playGet(int r) {
        r = Math.max(0, Math.min(RARITIES - 1, r));
        try {
            if (getSnd[r] == null) getSnd[r] = Vars.tree.loadSound("item-get-" + r);
            getSnd[r].play(0.5f + 0.1f * r);
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ viền vật phẩm động theo độ hiếm
    static final float[] PT = new float[2];
    static final Color TC = new Color(), TW = new Color();
    /** Điểm trên chu vi hình chữ nhật, s in [0,1). */
    static void per(float x, float y, float w, float h, float s) {
        float d = (((s % 1f) + 1f) % 1f) * 2f * (w + h);
        if (d < w) { PT[0] = x + d; PT[1] = y; return; } d -= w;
        if (d < h) { PT[0] = x + w; PT[1] = y + d; return; } d -= h;
        if (d < w) { PT[0] = x + w - d; PT[1] = y + h; return; } d -= w;
        PT[0] = x; PT[1] = y + h - d;
    }
    /** Hiếm: thở nhẹ · Sử thi: +quầng sáng, 1 tia chạy · Huyền thoại: 2 tia + lấp lánh · Thần thoại: đổi màu cầu vồng, 3 tia, nhiều sao. */
    static void drawRarity(float x, float y, float w, float h, int r, float seed) {
        if (r <= 0) return;
        int q = r == R_SHOP ? 2 : r;
        float t = Time.time + seed * 37f;
        Color c = TC;
        if (q >= 4) c.fromHsv((t * 1.1f) % 360f, 0.6f, 1f); else c.set(colorOf(r));
        TW.set(c).lerp(Color.white, 0.65f);
        float pulse = 0.5f + 0.5f * Mathf.sin(t / (q >= 3 ? 16f : 26f));
        if (q >= 2) { Lines.stroke(5f); Draw.color(c, 0.08f + 0.12f * pulse); Lines.rect(x - 1.5f, y - 1.5f, w + 3f, h + 3f); }
        Lines.stroke(q >= 3 ? 2.2f : 1.6f);
        Draw.color(c, 0.4f + 0.45f * pulse);
        Lines.rect(x + 1f, y + 1f, w - 2f, h - 2f);
        int runners = q >= 4 ? 3 : q >= 3 ? 2 : q >= 2 ? 1 : 0;
        for (int j = 0; j < runners; j++) {
            float head = t / (q >= 3 ? 140f : 210f) + j / (float) runners;
            for (int k = 0; k < 10; k++) {
                per(x + 1f, y + 1f, w - 2f, h - 2f, head - k * 0.006f);
                float f = 1f - k / 10f;
                Draw.color(TW, f * 0.9f);
                Fill.circle(PT[0], PT[1], 2.4f * f + 0.4f);
            }
        }
        int sp = q >= 4 ? 6 : q >= 3 ? 3 : 0;
        for (int i = 0; i < sp; i++) {
            float ph = t / 70f + i * 0.317f + seed;
            float a = Mathf.sin((ph % 1f) * Mathf.pi), s = 3.5f * a;
            per(x, y, w, h, (i * 0.173f + (float) Math.floor(ph) * 0.37f) % 1f);
            Lines.stroke(1f);
            Draw.color(Color.white, a);
            Lines.line(PT[0] - s, PT[1], PT[0] + s, PT[1]);
            Lines.line(PT[0], PT[1] - s, PT[0], PT[1] + s);
            Draw.color(c, a * 0.8f);
            Fill.circle(PT[0], PT[1], 1.3f * a);
        }
        Draw.reset();
    }
    static final class RarBorder extends Table {
        final int r; final float seed = (float) Math.random() * 100f;
        RarBorder(int r, float a) { super(tinted(colorOf(r).cpy().a(a))); this.r = r; }
        @Override public void draw() { super.draw(); drawRarity(x, y, width, height, r, seed); }
    }


    // ------------------------------------------------------------------ vật phẩm cửa hàng: dịch chuyển / tăng tốc / nút kỹ năng
    static final String PORT_KIND = "newex-pvp-gearport";
    static final HashMap<Integer, float[]> port = new HashMap<>();     // pid -> {tick bắt đầu, x, y} (máy chủ)
    static final HashMap<Integer, Float> portCd = new HashMap<>();     // pid -> tick dịch chuyển xong gần nhất (máy chủ)
    static float tpCdAt = -1e9f;                                        // client: tick dịch chuyển xong gần nhất

    /** Vòng tròn vẽ dần quanh chân người chơi trong 3s; hết vòng = dịch chuyển. Tự tắt nếu người chơi di chuyển. e.data = {id đơn vị, x, y}. */
    static final Effect portFx = new Effect(PORT_TICKS, 1e6f, e -> {
        if (!(e.data instanceof float[])) return;
        float[] a = (float[]) e.data;
        Unit u = Groups.unit.getByID((int) a[0]);
        if (u == null || !u.isValid() || Mathf.dst(u.x, u.y, a[1], a[2]) > 12f) return;
        float f = Mathf.clamp(e.fin()), rad = Math.max(14f, u.hitSize * 0.9f + 6f), cx = u.x, cy = u.y - u.hitSize * 0.12f;
        Color c = e.color;
        Draw.color(c, 0.06f + 0.16f * f);
        Fill.circle(cx, cy, rad * f);
        Draw.color(c, 0.22f);
        Lines.stroke(1.2f);
        Lines.circle(cx, cy, rad);
        Draw.color(c, 1f);
        Lines.stroke(2.6f);
        Lines.arc(cx, cy, rad, f, 90f);
        Draw.color(Color.white, 0.9f);
        float ang = 90f + f * 360f;
        Fill.circle(cx + Mathf.cosDeg(ang) * rad, cy + Mathf.sinDeg(ang) * rad, 2.6f);
        if (f > 0.93f) { Draw.color(Color.white, (f - 0.93f) / 0.07f * 0.6f); Fill.circle(cx, cy, rad); }
        Draw.reset();
    });
    static { portFx.layer = Layer.groundUnit - 1f; }
    static void showPort(int uid, float x, float y) {
        if (Vars.headless) return;
        try { portFx.at(x, y, 0f, colorOf(R_SHOP), new float[]{uid, x, y}); } catch (Throwable ignored) {}
    }
    static void onPortPacket(String content) {
        if (!Vars.net.client()) return;
        try { String[] a = content.split("\\|"); showPort(Integer.parseInt(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2])); } catch (Throwable ignored) {}
    }
    /** Máy chủ: theo dõi đứng yên; đủ 3s thì dịch chuyển về lõi phe mình. */
    static void tickPort() {
        if (Vars.net.client() || port.isEmpty() || Vars.state.isPaused()) return;
        for (java.util.Iterator<java.util.Map.Entry<Integer, float[]>> it = port.entrySet().iterator(); it.hasNext();) {
            java.util.Map.Entry<Integer, float[]> e = it.next();
            float[] a = e.getValue();
            Player p = playerById(e.getKey());
            Unit u = p == null ? null : p.unit();
            if (!war() || u == null || u.dead() || Mathf.dst(u.x, u.y, a[1], a[2]) > 10f) {
                it.remove();
                if (p != null) NewModePvp.toast(p, tr("[scarlet]Dịch chuyển bị hủy (di chuyển).", "[scarlet]Teleport cancelled (moved)."));
                continue;
            }
            if (Time.time - a[0] < PORT_TICKS) continue;
            it.remove();
            mindustry.world.blocks.storage.CoreBlock.CoreBuild core = p.team().core();
            if (core == null) continue;
            NewModePvp.fx(u.x, u.y);
            u.set(core.x, core.y);
            u.vel.setZero();
            NewModePvp.fx(core.x, core.y);
            portCd.put(p.id, Time.time);
            NexNet.sendTo(p, "newex-gear-tp", "tp|" + core.x + "|" + core.y);
        }
    }
    static void useHaste(Player pl, NewModePvp.St st, int idx) {
        Unit u = pl.unit();
        if (u == null || u.dead() || hasteStatus == null) return;
        st.gInv.remove(idx);
        u.apply(hasteStatus, HASTE_TICKS);
        NexNet.sendTo(pl, "newex-gear-tp", "hs|" + HASTE_TICKS);
        NewModePvp.toast(pl, tr("[accent]Tăng tốc +150% trong 10s!", "[accent]+150% speed for 10s!"));
    }
    /** Client nhận lệnh từ máy chủ: tp = đặt vị trí + bắt đầu hồi chiêu, hs/ks = áp trạng thái tăng tốc, pc = đồng bộ hồi chiêu pilot. */
    static void onTpMsg(String s) {
        try {
            String[] a = s.split("\\|");
            Unit u = Vars.player == null ? null : Vars.player.unit();
            if (a[0].equals("tp")) {
                tpCdAt = Time.time;
                if (u != null) { u.set(Float.parseFloat(a[1]), Float.parseFloat(a[2])); u.vel.setZero(); }
            } else if (a[0].equals("hs") && u != null && hasteStatus != null) u.apply(hasteStatus, Float.parseFloat(a[1]));
            else if (a[0].equals("ks") && u != null && killSpeedStatus != null) u.apply(killSpeedStatus, Float.parseFloat(a[1]));
            else if (a[0].equals("pc") && Vars.player != null && a.length >= 3) {
                NewModePvp.PilotCd pc = NewModePvp.pilotCdFor(Integer.parseInt(a[1]));
                if (pc != null) pc.at.put(Vars.player.id, Time.time - Float.parseFloat(a[2]));
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ kỹ năng nội tại / kích hoạt của vật phẩm Chiến tranh
    static final HashMap<Integer, float[]> moveSt = new HashMap<>();                  // pid -> {lastX, lastY, accTicks}
    static final HashMap<Integer, Float> minPtAcc = new HashMap<>();                  // pid -> tích lũy tick tới 60s
    static final java.util.HashSet<Integer> pilotHalfNext = new java.util.HashSet<>(); // pid -> giảm 50% CD cho lần kích hoạt pilot tiếp theo

    static final HashMap<Integer, Float> lowHpShieldAt = new HashMap<>();
    static final HashMap<Integer, Float> skillExpUntil = new HashMap<>();
    static final HashMap<Integer, Integer> resonanceStacks = new HashMap<>();
    static final HashMap<Integer, Float> teamResonanceUntil = new HashMap<>();
    static float coreLaserAcc = 0f;

    /** Gửi thông báo vào bảng Thông báo HUD khi player nhặt được vật phẩm (không spam thanh chat). */
    static void announcePickup(Player pl, Def d) {
        if (Vars.net.client() || pl == null || d == null) return;
        try {
            String pname = pl.name != null ? pl.name : "Player";
            NewModePvp.pushNotif(tr("[gold]★ [white]", "[gold]★ [white]") + pname + "[] "
                + tr("đã nhặt ", "picked up ")
                + col(d.rarity) + name(d) + " [" + rarName(d.rarity) + "][]");
        } catch (Throwable ignored) {}
    }

    /** Hiệu ứng tia laser từ Lõi (Lõi Quang Tuyến): e.x/y = tâm lõi, e.rotation = góc, e.data = Float khoảng cách. */
    static final Effect coreLaserFx = new Effect(18f, 600f, e -> {
        float len = e.data instanceof Float ? (Float) e.data : 160f;
        float ex = e.x + Angles.trnsx(e.rotation, len), ey = e.y + Angles.trnsy(e.rotation, len);
        Draw.z(Layer.bullet + 1f);
        Lines.stroke(8f * e.fout());
        Draw.color(Color.valueOf("ff6b9d"), 0.55f * e.fout());
        Lines.line(e.x, e.y, ex, ey);
        Lines.stroke(3.2f * e.fout());
        Draw.color(Color.white, 0.95f * e.fout());
        Lines.line(e.x, e.y, ex, ey);
        Fill.circle(ex, ey, 6f * e.fout());
        Draw.reset();
    });

    /** Hiệu ứng icon vật phẩm Ấn Quang Huy nhảy lên trên đầu đồng minh nhận buff Tinh Quang Cộng Hưởng. */
    static final Effect resonanceIconFx = new Effect(68f, e -> {
        float fin = e.fin(), fout = e.fout();
        float jump = Mathf.sin(Math.min(1f, fin * 1.6f) * Mathf.PI) * 18f + fin * 16f;
        float alpha = fin < 0.15f ? fin / 0.15f : fout;
        float sz = 18f + 4f * Mathf.sin(fin * Mathf.PI);
        Draw.z(Layer.flyingUnit + 2f);
        Draw.color(Color.valueOf("ffd86b"), 0.45f * alpha);
        Fill.circle(e.x, e.y + 12f + jump, sz * 0.72f);
        Def bd = def(teamBuffDefId);
        TextureRegion r = bd != null ? icon(bd) : null;
        if (r != null) {
            Draw.color(Color.white, alpha);
            Draw.rect(r, e.x, e.y + 12f + jump, sz, sz);
        }
        Draw.reset();
    });

    static void reset() {
        carry.clear(); port.clear(); portCd.clear(); tpCdAt = -1e9f;
        moveSt.clear(); minPtAcc.clear(); pilotHalfNext.clear();
        lowHpShieldAt.clear(); skillExpUntil.clear(); resonanceStacks.clear(); teamResonanceUntil.clear();
        coreLaserAcc = 0f;
    }
    static void tickUpdate() {
        tickPort();
        tickPassives();
    }
    /** Máy chủ: cập nhật các kỹ năng nội tại của vật phẩm. */
    static void tickPassives() {
        if (Vars.net.client() || !NewModePvp.isFull() || !war() || Vars.state.gameOver || Vars.state.isPaused()) return;
        final float dt = Time.delta;
        if (dt <= 0f) return;
        final java.util.HashSet<Integer> coreLaserTeams = new java.util.HashSet<>();
        NewModePvp.eachPl(pl -> {
            if (pl == null) return;
            NewModePvp.St st = NewModePvp.byId.get(pl.id);
            if (st == null) return;
            Unit u = pl.unit();
            if (hasSkill(st, SK_MOVE_ATK)) {
                if (u != null && u.isValid() && !u.dead()) {
                    float[] ms = moveSt.get(pl.id);
                    if (ms == null) {
                        moveSt.put(pl.id, new float[]{u.x, u.y, 0f});
                    } else {
                        float d2 = Mathf.dst2(u.x, u.y, ms[0], ms[1]);
                        ms[0] = u.x; ms[1] = u.y;
                        boolean moved = (d2 > 0.04f && d2 < 40000f) || u.vel.len2() > 0.04f;
                        if (moved && st.gMoveAtk < MOVE_ATK_MAX) {
                            ms[2] += dt;
                            if (ms[2] >= MOVE_ATK_STEP) {
                                int add = (int) (ms[2] / MOVE_ATK_STEP);
                                ms[2] -= add * MOVE_ATK_STEP;
                                int nv = Math.min(MOVE_ATK_MAX, st.gMoveAtk + add);
                                if (nv != st.gMoveAtk) { st.gMoveAtk = nv; NewModePvp.changed(); }
                            }
                        }
                    }
                }
            } else {
                moveSt.remove(pl.id);
                if (st.gMoveAtk != 0) { st.gMoveAtk = 0; NewModePvp.changed(); }
            }
            if (hasSkill(st, SK_MIN_PT)) {
                float acc = minPtAcc.getOrDefault(pl.id, 0f) + dt;
                if (acc >= MIN_PT_TICKS) {
                    int pts = (int) (acc / MIN_PT_TICKS);
                    acc -= pts * MIN_PT_TICKS;
                    st.pts += pts;
                    NewModePvp.changed();
                    if (!NewModePvp.isBotPl(pl)) NewModePvp.toast(pl, tr("[yellow]+" + pts + " điểm (Bùa Tài Lộc)!", "[yellow]+" + pts + " point (Fortune Charm)!"));
                }
                minPtAcc.put(pl.id, acc);
            } else {
                minPtAcc.remove(pl.id);
            }
            // Khiên Sinh Mệnh: máu < 50% -> nhận khiên = 50% máu tối đa (hồi chiêu 45s)
            if (hasSkill(st, SK_LOWHP_SHIELD) && u != null && u.isValid() && !u.dead() && u.maxHealth > 0 && u.health / u.maxHealth < 0.5f) {
                Float last = lowHpShieldAt.get(pl.id);
                if (last == null || Time.time - last >= LOWHP_SHIELD_CD || Time.time < last) {
                    lowHpShieldAt.put(pl.id, Time.time);
                    u.shield += u.maxHealth * 0.5f;
                    try { Call.effect(Fx.healWaveMend, u.x, u.y, u.hitSize, Color.sky); } catch (Throwable ignored) {}
                    if (!NewModePvp.isBotPl(pl)) NewModePvp.toast(pl, tr("[sky]Khiên Sinh Mệnh kích hoạt (+50% máu khiên)!", "[sky]Lifeline Aegis triggered (+50% max HP shield)!"));
                }
            }
            if (hasSkill(st, SK_CORE_LASER) && pl.team() != null) coreLaserTeams.add(pl.team().id);
        });
        // Lõi Quang Tuyến: mỗi 0.25s bắn 1 tia laser từ lõi gây 1000 ST/giây (+1% max HP lên unit player, -20% lên unit lính)
        if (!coreLaserTeams.isEmpty()) {
            coreLaserAcc += dt;
            if (coreLaserAcc >= 15f) {
                float sec = coreLaserAcc / 60f;
                coreLaserAcc = 0f;
                for (int tid : coreLaserTeams) {
                    Team tm = Team.get(tid);
                    Seq<mindustry.world.blocks.storage.CoreBlock.CoreBuild> cores = tm.cores();
                    for (int ci = 0; ci < cores.size; ci++) {
                        mindustry.world.blocks.storage.CoreBlock.CoreBuild c = cores.get(ci);
                        if (c == null || !c.isValid()) continue;
                        final Unit[] best = {null};
                        final float[] bestD2 = {CORE_LASER_RANGE * CORE_LASER_RANGE};
                        Units.nearbyEnemies(tm, c.x - CORE_LASER_RANGE, c.y - CORE_LASER_RANGE, CORE_LASER_RANGE * 2f, CORE_LASER_RANGE * 2f, e -> {
                            if (e == null || e.dead || !e.isValid()) return;
                            float d2 = Mathf.dst2(c.x, c.y, e.x, e.y);
                            if (d2 < bestD2[0]) { bestD2[0] = d2; best[0] = e; }
                        });
                        Unit tgt = best[0];
                        if (tgt != null) {
                            float dmg = CORE_LASER_DPS * sec;
                            boolean isPlayerUnit = tgt.isPlayer() || NewModePvp.botUnitOf(tgt) != null;
                            boolean isSoldier = NewModePvp.soldierOwner.containsKey(tgt.id);
                            if (isPlayerUnit && tgt.maxHealth > 0) dmg += tgt.maxHealth * 0.01f * sec;
                            if (isSoldier) dmg *= 0.8f;
                            try { tgt.damagePierce(dmg); } catch (Throwable err) { try { tgt.damage(dmg); } catch (Throwable ignored) {} }
                            float ang = Angles.angle(c.x, c.y, tgt.x, tgt.y);
                            float dst = Mathf.dst(c.x, c.y, tgt.x, tgt.y);
                            try { Call.effect(coreLaserFx, c.x, c.y, ang, Color.white, Float.valueOf(dst)); } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        } else {
            coreLaserAcc = 0f;
        }
    }
    /** Gọi khi player gây sát thương lên mục tiêu: kích hoạt Kiếm Kết Liễu (+15% ST dưới 50% máu) & Ấn Quang Huy (tích 100 tầng Tinh Quang Cộng Hưởng). */
    static void onDamage(int attackerPid, Unit tgt, float baseDmg) {
        if (Vars.net.client() || tgt == null || tgt.dead || !tgt.isValid() || attackerPid < 0 || !war()) return;
        NewModePvp.St st = NewModePvp.byId.get(attackerPid);
        if (st == null) return;
        if (hasSkill(st, SK_EXEC50) && baseDmg > 0f && tgt.maxHealth > 0f && tgt.health / tgt.maxHealth < 0.5f) {
            try { tgt.damagePierce(baseDmg * 0.15f); } catch (Throwable err) { try { tgt.damage(baseDmg * 0.15f); } catch (Throwable ignored) {} }
        }
        if (hasSkill(st, SK_TEAM_BUFF)) {
            Player ap = playerById(attackerPid);
            if (ap != null && ap.team() != null && hasTeamResonance(ap.team())) return; // Phải hết thời gian buff mới tích/kích hoạt lại
            int stk = resonanceStacks.getOrDefault(attackerPid, 0) + 1;
            if (stk >= RESONANCE_MAX) {
                resonanceStacks.put(attackerPid, 0);
                if (ap != null && ap.team() != null) {
                    final Team tm = ap.team();
                    teamResonanceUntil.put(tm.id, Time.time + RESONANCE_TICKS);
                    Groups.unit.each(u -> {
                        if (u != null && u.isValid() && !u.dead && u.team == tm) {
                            try { Call.effect(resonanceIconFx, u.x, u.y, 0f, Color.white); } catch (Throwable ignored) {}
                        }
                    });
                    NewModePvp.pushNotif(tr("[gold]✦ Tinh Quang Cộng Hưởng[] kích hoạt cho phe [accent]", "[gold]✦ Astral Resonance[] activated for team [accent]")
                        + (ap.name != null ? ap.name : tm.name) + "[]: [scarlet]+10% Tấn công[] & [sky]+10% Giáp[] (20s)");
                }
            } else {
                resonanceStacks.put(attackerPid, stk);
            }
        }
    }
    static boolean hasTeamResonance(Team team) {
        if (team == null || !war()) return false;
        Float until = teamResonanceUntil.get(team.id);
        return until != null && Time.time < until;
    }
    /** Pháo Ấn Sư Tử: +50% sát thương đòn đánh thường; nếu dùng unit Zeralion tăng thêm +100% (tổng +150%). */
    static float basicAtkMul(NewModePvp.St st, Unit u) {
        if (!hasSkill(st, SK_BASIC_ZERA)) return 1f;
        boolean isZera = u != null && Zeralion.zeralion != null && u.type == Zeralion.zeralion;
        return isZera ? 2.5f : 1.5f;
    }
    /** Ngọc Tinh Thông: gọi khi unit player nhấn dùng kỹ năng -> +50% EXP trong 10s (không cộng dồn). */
    public static void onUnitSkillCast(Unit owner) {
        if (Vars.net.client() || owner == null || !war()) return;
        Player op = NewModePvp.ownerPl(owner);
        if (op == null) return;
        NewModePvp.St st = NewModePvp.byId.get(op.id);
        if (!hasSkill(st, SK_SKILL_EXP)) return;
        skillExpUntil.put(op.id, Time.time + SKILL_EXP_TICKS);
    }
    /** Ngọc Tinh Thông: gọi khi tiêu diệt mục tiêu bằng kỹ năng của unit -> nhận ngay +150 EXP. */
    public static void onUnitSkillKill(Unit owner) {
        if (Vars.net.client() || owner == null || !war()) return;
        Player op = NewModePvp.ownerPl(owner);
        if (op == null) return;
        NewModePvp.St st = NewModePvp.byId.get(op.id);
        if (!hasSkill(st, SK_SKILL_EXP)) return;
        NewModePvp.addExp(op, 150f);
    }
    static float expBonusMul(int pid) {
        if (!war()) return 1f;
        Float until = skillExpUntil.get(pid);
        return (until != null && Time.time < until) ? 1.5f : 1f;
    }
    /** Máy chủ gọi khi bất kỳ unit địch nào bị killerPid hạ gục: kích hoạt tăng tốc 50%/6s và +1% tấn công kỹ năng unit player. */
    static void onAnyKill(Unit victim, int killerPid) {
        if (Vars.net.client() || victim == null || killerPid < 0 || !NewModePvp.isFull() || !war() || Vars.state.gameOver) return;
        Player kp = playerById(killerPid);
        if (kp == null || kp.team() == victim.team) return;
        NewModePvp.St st = NewModePvp.byId.get(killerPid);
        if (st == null) return;
        if (hasSkill(st, SK_KILL_SPD) && killSpeedStatus != null) {
            Unit ku = kp.unit();
            if (ku != null && ku.isValid() && !ku.dead()) {
                ku.apply(killSpeedStatus, KILL_SPD_TICKS);
                NexNet.sendTo(kp, "newex-gear-tp", "ks|" + KILL_SPD_TICKS);
            }
        }
        if (hasSkill(st, SK_KILL_SKILL_ATK) && st.gSkillAtk < SKILL_ATK_MAX) {
            st.gSkillAtk = Math.min(SKILL_ATK_MAX, st.gSkillAtk + 1);
            NewModePvp.changed();
        }
    }
    /** Máy chủ gọi khi người chơi victim bị kẻ địch là player tiêu diệt: giảm 50% hồi chiêu kỹ năng pilot (đang hồi hoặc lần kế tiếp). */
    static void onKilledByEnemyPlayer(Player victim, NewModePvp.St st) {
        if (Vars.net.client() || victim == null || st == null || !hasDeathCdr(st)) return;
        NewModePvp.PilotCd pc = NewModePvp.pilotCdFor(st.pi);
        if (pc == null) return;
        Float at = pc.at.get(victim.id);
        float cd = ChaosMode.cd(pc.cd);
        float elapsed = at != null ? (Time.time - at) : cd;
        if (at != null && elapsed >= 0f && elapsed < cd) {
            float newElapsed = cd - (cd - elapsed) * 0.5f;
            pc.at.put(victim.id, Time.time - newElapsed);
            NexNet.sendTo(victim, "newex-gear-tp", "pc|" + st.pi + "|" + newElapsed);
        } else {
            pilotHalfNext.add(victim.id);
        }
    }
    /** Máy chủ gọi ngay sau khi kỹ năng pilot bắt đầu hồi chiêu để áp dụng giảm 50% CD đã nạp từ Giáp Phục Hận. */
    static void afterPilotCdStart(int pid, int pi) {
        if (Vars.net.client() || !pilotHalfNext.remove(pid)) return;
        NewModePvp.PilotCd pc = NewModePvp.pilotCdFor(pi);
        if (pc == null) return;
        float half = ChaosMode.cd(pc.cd) * 0.5f;
        pc.at.put(pid, Time.time - half);
        Player p = playerById(pid);
        if (p != null) NexNet.sendTo(p, "newex-gear-tp", "pc|" + pi + "|" + half);
    }

    // nút kỹ năng ở góc dưới bên phải (cạnh HUD hồi chiêu pilot)
    static Def heldDef(int kind) {
        NewModePvp.St st = NewModePvp.localState();
        if (st == null || !war()) return null;
        for (int id : st.gInv) { Def d = def(id); if (d != null && d.kind == kind) return d; }
        if (kind == K_TP) for (int i = 0, ns = slots(); i < ns; i++) { Def d = def(st.gEq[i]); if (d != null && d.kind == kind) return d; }
        return null;
    }
    static int bagCount(int kind) { NewModePvp.St st = NewModePvp.localState(); int c = 0; if (st != null) for (int id : st.gInv) { Def d = def(id); if (d != null && d.kind == kind) c++; } return c; }
    static float tpLeft() { return Math.max(0f, PORT_CD - (Time.time - tpCdAt)); }
    static final class SkillBtn extends Element {
        final int kind;
        SkillBtn(int kind) { this.kind = kind; touchable = Touchable.enabled; clicked(this::press); }
        void press() {
            Def d = heldDef(kind);
            if (d == null) return;
            if (kind == K_TP) { if (tpLeft() <= 0f) NewModePvp.send(REQ_GEAR, "t|" + d.id); }
            else NewModePvp.send(REQ_GEAR, "e|" + d.id);
        }
        @Override public void act(float delta) { super.act(delta); place(); }
        boolean place() {
            Def d = heldDef(kind);
            touchable = d != null ? Touchable.enabled : Touchable.disabled;
            if (d == null) return false;
            float u = NewModePvp.uiScale(), sz = (Vars.mobile ? 56 : 44) * u, mg = 16 * u;
            NewModePvp.St st = NewModePvp.localState();
            int shift = 0;
            if (st != null && st.pi > 0 && NewModePvp.pilotCdFor(st.pi) != null) shift++;
            Unit pu = Vars.player == null ? null : Vars.player.unit();
            if (pu != null && Zeralion.zeralion != null && pu.type == Zeralion.zeralion) shift++;
            if (kind == K_HASTE && heldDef(K_TP) != null) shift++;
            setBounds(NewModePvp.screenW() - mg - sz - shift * (sz + mg * 0.5f), mg, sz, sz);
            return true;
        }
        @Override public void draw() {
            Def d = heldDef(kind);
            if (d == null || !place()) return;
            float u = NewModePvp.uiScale(), cx = x + width / 2f, cy = y + height / 2f;
            float left = kind == K_TP ? tpLeft() : 0f, frac = Mathf.clamp(left / PORT_CD);
            Draw.color();
            Draw.alpha(frac > 0 ? 0.45f : 1f);
            Draw.rect(icon(d), cx, cy, width, height);
            if (frac > 0) { Draw.color(Color.black, 0.65f); float h = height * frac; Fill.rect(cx, y + height - h / 2f, width, h); }
            Draw.color(frac > 0 ? Color.lightGray : colorOf(R_SHOP), 1f);
            Lines.stroke(2 * u);
            Lines.rect(x, y, width, height);
            Draw.reset();
            float ts = 0.45f * u * (Vars.mobile ? 1.2f : 1f);
            if (frac > 0) NewModePvp.drawTxt(NewModePvp.fmtCd(left), cx, cy + 6 * u, ts, Color.white);
            else if (kind == K_HASTE) NewModePvp.drawTxt("x" + bagCount(K_HASTE), cx, y + 12 * u, ts, Color.white);
        }
    }
    static void addHud() {
        if (Vars.headless) return;
        try {
            for (int k : new int[]{K_TP, K_HASTE}) {
                SkillBtn b = new SkillBtn(k);
                b.name = "newex-gear-skill-" + k;
                Vars.ui.hudGroup.addChild(b);
            }
        } catch (Throwable err) {}
    }

    // ------------------------------------------------------------------ cửa hàng (giá theo tốc độ lên cấp/điểm của từng phe)
    static final int[] BASE_PRICE = {2, 5, 12, 28, 60};
    static Team teamOfId(int id) {
        Player p = Groups.player.getByID(id);
        if (p == null) { PvpBotAI.Agent b = NewModePvp.botOf(id); if (b != null) p = b.botPl; }
        return p != null ? p.team() : null;
    }
    /** Hệ số giá của phe: phe tăng điểm (cấp) nhanh hơn trung bình thì đắt hơn, chậm hơn thì rẻ hơn (0.7 - 1.5). */
    static float priceMul(Team t) {
        if (t == null) return 1f;
        java.util.HashMap<Integer, Float> tot = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, NewModePvp.St> e : NewModePvp.byId.entrySet()) {
            Team tm = teamOfId(e.getKey());
            if (tm != null) tot.merge(tm.id, (float) e.getValue().lv, Float::sum);
        }
        if (tot.size() < 2) return 1f;
        float sum = 0; for (float v : tot.values()) sum += v;
        float avg = sum / tot.size(), mine = tot.getOrDefault(t.id, avg);
        return avg <= 0f ? 1f : Mathf.clamp(mine / avg, 0.7f, 1.5f);
    }
    static int price(Def d, Team t) { if (d.fixed > 0) return d.fixed; return Math.max(1, Math.round(BASE_PRICE[d.rarity] * priceMul(t))); }
    static int sellPrice(Def d, Team t) { if (d.fixed > 0) return d.fixed / 2; return Math.max(1, Math.round(price(d, t) * 0.6f)); }

    static mindustry.ui.dialogs.BaseDialog shopDialog = null;
    static int shopCategory = -1; // -1 = danh sách vai trò, 0..5 = slot trang bị, 100 = vật phẩm cửa hàng, 101 = bán đồ trong túi
    static void openShop() {
        if (Vars.headless || !war()) return;
        if (shopDialog != null && shopDialog.isShown()) return;
        shopCategory = -1;
        final MachineUi.Panel p = MachineUi.panel(tr("Cửa hàng vật phẩm", "Item shop"));
        p.onBuild(() -> buildShop(p));
        final String[] last = {NewModePvp.curVer()};
        p.onTick(() -> {
            if (!NewModePvp.nearOwnCore()) { p.hide(); return; }
            String cv = NewModePvp.curVer();
            if (!cv.equals(last[0]) && !Core.input.isTouched()) { last[0] = cv; p.rebuild(); }
        });
        shopDialog = p.dialog;
        p.show();
    }
    static void buildShop(final MachineUi.Panel pnl) {
        Table h = pnl.header(), c = pnl.content();
        h.clear(); c.clear();
        NewModePvp.St st = NewModePvp.localState();
        if (st == null) { c.add(tr("Đang đồng bộ dữ liệu...", "Syncing data...")).pad(20).row(); return; }
        final float W = pnl.innerW();
        final boolean narrow = NewModePvp.narrowAt(W);
        final Team t = Vars.player != null ? Vars.player.team() : null;
        float m = priceMul(t);
        Table head = new Table(Styles.black6);
        head.margin(8);
        head.add("[yellow]" + tr("Điểm: ", "Points: ") + st.pts + "[]").left().expandX();
        head.add("[lightgray]" + tr("Hệ số giá: ", "Price x") + "[white]x" + arc.util.Strings.fixed(m, 2) + "[]").right();
        head.row();
        head.add("[lightgray]" + (m > 1.02f ? tr("Phe bạn tăng điểm nhanh hơn → giá đắt hơn.", "Your team gains points faster → higher prices.")
            : m < 0.98f ? tr("Phe bạn tăng điểm chậm hơn → giá rẻ hơn.", "Your team gains points slower → lower prices.") : tr("Hai phe đang ngang nhau.", "Teams are even.")) + "[]").colspan(2).left().growX().wrap().minWidth(0).padTop(2);
        h.add(head).growX().pad(4).row();

        // Trang chọn vai trò (mặc định khi mở Cửa hàng)
        if (shopCategory < 0) {
            c.add("[accent]" + tr("Chọn vai trò vật phẩm muốn xem:", "Select an item category:") + "[]").left().padLeft(6).padTop(6).padBottom(6).row();
            int shopCnt = 0;
            for (int i = 0; i < n; i++) if (DEFS[i].kind > 0) shopCnt++;
            int sellCnt = 0;
            for (int id : st.gInv) { Def dd = def(id); if (dd != null && dd.kind <= 0) sellCnt++; }

            Table grid = new Table();
            int cols = narrow ? 1 : 2;
            float btnW = narrow ? (W - 12f) : (W - 20f) / 2f;
            float btnH = narrow ? 50f : 56f;
            int idx = 0;
            // 6 vai trò trang bị: Vũ khí, Mũ, Giáp, Giày, Nhẫn, Bùa
            for (int ty = 0; ty < TYPES; ty++) {
                final int cat = ty;
                int cnt = 0;
                for (int i = 0; i < n; i++) if (DEFS[i].slot == ty && DEFS[i].kind <= 0) cnt++;
                TextButton b = grid.button("[accent]" + tr(SLOT_VI[ty], SLOT_EN[ty]) + "[]  [lightgray](" + cnt + ")[]", Styles.flatBordert, () -> {
                    shopCategory = cat;
                    pnl.rebuild();
                }).width(btnW).height(btnH).pad(4).get();
                b.getLabel().setFontScale(0.95f);
                if (++idx % cols == 0) grid.row();
            }
            // Vật phẩm đặc biệt cửa hàng (tiêu hao / mua 1 lần)
            grid.button("[#2ee6c8]" + tr("Vật phẩm đặc biệt", "Special Shop") + "[]  [lightgray](" + shopCnt + ")[]", Styles.flatBordert, () -> {
                shopCategory = 100;
                pnl.rebuild();
            }).width(btnW).height(btnH).pad(4);
            if (++idx % cols == 0) grid.row();
            // Bán vật phẩm trong túi
            grid.button("[orange]" + tr("Bán đồ trong túi", "Sell Bag Items") + "[]  [lightgray](" + sellCnt + ")[]", Styles.flatBordert, () -> {
                shopCategory = 101;
                pnl.rebuild();
            }).width(btnW).height(btnH).pad(4);
            c.add(grid).growX().row();
            return;
        }

        // Nút quay lại trang danh sách vai trò
        c.button("← " + tr("Quay lại danh sách vai trò", "Back to categories"), Styles.flatBordert, () -> {
            shopCategory = -1;
            pnl.rebuild();
        }).width(W - 8f).height(narrow ? 36f : 40f).pad(4).row();

        if (shopCategory == 100) {
            c.add("[#2ee6c8]" + tr("Vật phẩm cửa hàng (mua 1 lần)", "Shop items (one-time)") + "[]").left().padLeft(6).padTop(4).row();
            for (int i = 0; i < n; i++) {
                final Def d = DEFS[i];
                if (d.kind <= 0) continue;
                boolean owned = st.gInv.contains(d.id);
                for (int k = 0, ns = slots(); k < ns && !owned; k++) if (st.gEq[k] == d.id) owned = true;
                c.add(itemRow(d, W, narrow, owned ? tr("Đã có", "Owned") : tr("Mua ", "Buy ") + price(d, t), owned ? () -> {} : () -> NewModePvp.send(REQ_GEAR, "b|" + d.id), null, null, false)).width(W).pad(3).row();
            }
        } else if (shopCategory == 101) {
            c.add("[orange]" + tr("Bán vật phẩm trong túi", "Sell bag items") + "[]").left().padLeft(6).padTop(4).row();
            boolean any = false;
            for (int id : new ArrayList<>(st.gInv)) {
                final Def d = def(id);
                if (d == null || d.kind > 0) continue;
                any = true;
                c.add(itemRow(d, W, narrow, tr("Bán +", "Sell +") + sellPrice(d, t), () -> NewModePvp.send(REQ_GEAR, "s|" + d.id), null, null, false)).width(W).pad(3).row();
            }
            if (!any) c.add("[lightgray]" + tr("Túi không có vật phẩm.", "No items in your bag.") + "[]").left().padLeft(6).pad(8).row();
        } else if (shopCategory >= 0 && shopCategory < TYPES) {
            c.add("[accent]" + tr("Mua - ", "Buy - ") + tr(SLOT_VI[shopCategory], SLOT_EN[shopCategory]) + "[]").left().padLeft(6).padTop(4).row();
            ArrayList<Def> l = new ArrayList<>();
            for (int i = 0; i < n; i++) if (DEFS[i].slot == shopCategory && DEFS[i].kind <= 0) l.add(DEFS[i]);
            l.sort((x, y) -> y.rarity - x.rarity);
            for (final Def d : l) c.add(itemRow(d, W, narrow, tr("Mua ", "Buy ") + price(d, t), () -> NewModePvp.send(REQ_GEAR, "b|" + d.id), null, null, false)).width(W).pad(3).row();
        }
    }

    // ------------------------------------------------------------------ yêu cầu từ client
    static boolean hooked = false;
    static void define() {
        if (!hooked) { hooked = true; Events.run(mindustry.game.EventType.Trigger.update, GearItems::tickUpdate); }
        NewModePvp.onReq(REQ_GEAR, 120, (player, payload) -> {
            if (!war()) return;
            String[] a = payload.split("\\|");
            if (a.length < 2) return;
            Integer v = NewModePvp.leadInt(a[1]);
            if (v == null) return;
            String op = a[0];
            // Bùa Dịch Chuyển và Nước Tăng Tốc dùng được ở mọi khoảng cách (không cần gần lõi)
            boolean anyDist = op.equals("t");
            if (op.equals("e")) { Def dq = def(v); anyDist = dq != null && dq.kind == K_HASTE; }
            NewModePvp.St st = NewModePvp.reqState(player, anyDist);
            if (st == null) return;
            if (op.equals("e")) {                         // lắp vật phẩm id=v từ túi
                int idx = st.gInv.indexOf(v);
                Def d = def(v);
                if (idx < 0 || d == null) return;
                if (d.kind == K_HASTE) { useHaste(player, st, idx); return; }
                int ns = slots(), tgt = -1;
                Integer want = a.length > 2 ? NewModePvp.leadInt(a[2]) : null;
                if (want != null && want >= 0 && want < ns && want % TYPES == d.slot) tgt = want;
                for (int k = d.slot; tgt < 0 && k < ns; k += TYPES) if (st.gEq[k] < 0) tgt = k;   // ô trống cùng loại
                if (tgt < 0) tgt = d.slot;                                                      // hết ô trống: thay ô đầu tiên của loại
                st.gInv.remove(idx);
                int old = st.gEq[tgt];
                if (old >= 0) st.gInv.add(old);
                st.gEq[tgt] = d.id;
                NewModePvp.toast(player, tr("[accent]Đã lắp ", "[accent]Equipped ") + col(d.rarity) + name(d) + "[]");
            } else if (op.equals("t")) {                  // bắt đầu dịch chuyển về căn cứ (cần đang lắp Bùa Dịch Chuyển)
                boolean has = false;
                for (int id : st.gInv) { Def q = def(id); if (q != null && q.kind == K_TP) has = true; }
                for (int k = 0, ns = slots(); k < ns && !has; k++) { Def q = def(st.gEq[k]); if (q != null && q.kind == K_TP) has = true; }
                Unit u = player.unit();
                if (!has || u == null || u.dead() || port.containsKey(player.id) || player.team().core() == null) return;
                Float cd = portCd.get(player.id);
                if (cd != null && Time.time - cd < PORT_CD) { NewModePvp.toast(player, tr("[scarlet]Kỹ năng đang hồi chiêu!", "[scarlet]Skill on cooldown!")); return; }
                port.put(player.id, new float[]{Time.time, u.x, u.y});
                showPort(u.id, u.x, u.y);
                if (Vars.net.server()) Call.clientPacketReliable(PORT_KIND, u.id + "|" + Math.round(u.x) + "|" + Math.round(u.y));
                NewModePvp.toast(player, tr("[accent]Đứng yên 3 giây để dịch chuyển...", "[accent]Stand still for 3s to teleport..."));
            } else if (op.equals("u")) {                  // tháo ô v
                if (v < 0 || v >= MAXSLOTS || st.gEq[v] < 0) return;
                if (st.gInv.size() >= INV_CAP) return;
                st.gInv.add(st.gEq[v]);
                st.gEq[v] = -1;
            } else if (op.equals("d")) {                  // bỏ vật phẩm id=v trong túi
                int idx = st.gInv.indexOf(v);
                Def dd = def(v);
                if (idx < 0 || dd == null) return;
                if (dd.kind > 0) { st.gInv.remove(idx); NewModePvp.toast(player, tr("[accent]Đã bỏ vật phẩm cửa hàng (không hóa quái).", "[accent]Shop item discarded (no monster).")); }
                else if (spawnCarrier(dd)) {
                    st.gInv.remove(idx);
                    NewModePvp.toast(player, tr("[accent]Vật phẩm hóa thành quái trên bản đồ - hạ nó để nhận!", "[accent]The item became a monster on the map - kill it to claim!"));
                } else NewModePvp.toast(player, tr("[scarlet]Không tìm được chỗ spawn, giữ lại vật phẩm.", "[scarlet]No spawn spot found, item kept."));
            } else if (op.equals("b")) {                  // mua vật phẩm mẫu id=v bằng điểm
                Def d = def(v);
                if (d == null || st.gInv.size() >= INV_CAP) return;
                if (d.kind > 0 && st.gBought.contains(d.id)) { NewModePvp.toast(player, tr("[scarlet]Vật phẩm cửa hàng chỉ mua được 1 lần!", "[scarlet]Shop items can only be bought once!")); return; }
                int pr = price(d, player.team());
                if (st.pts < pr) { NewModePvp.toast(player, tr("[scarlet]Không đủ điểm!", "[scarlet]Not enough points!")); return; }
                st.pts -= pr; st.gInv.add(d.id);
                if (d.kind > 0) st.gBought.add(d.id);
                String bpName = player.name != null ? player.name : "Player";
                NewModePvp.pushNotif(tr("[cyan]◆ [white]", "[cyan]◆ [white]") + bpName + "[] " + tr("đã mua ", "bought ") + col(d.rarity) + name(d) + " [" + rarName(d.rarity) + "][]");
            } else if (op.equals("s")) {                  // bán vật phẩm id=v trong túi lấy điểm
                int idx = st.gInv.indexOf(v);
                Def d = def(v);
                if (idx < 0 || d == null || d.kind > 0) return;
                st.gInv.remove(idx); st.pts += sellPrice(d, player.team());
            } else return;
            NewModePvp.changed();
        });
    }

    // ------------------------------------------------------------------ giao diện
    static int filterSlot = -1, pendingFilter = -1;
    /** Chạm vào một ô trang bị: mở Túi đồ, danh sách vật phẩm của ô đó lên đầu. */
    static void openBagFor(int slot) { pendingFilter = slot; NewModePvp.openBag(); }

    /** Nền trắng nhuộm màu (Tex.whiteui khai báo kiểu Drawable nên phải ép sang TextureRegionDrawable mới có tint). */
    static arc.scene.style.Drawable tinted(Color c) {
        return ((arc.scene.style.TextureRegionDrawable) Tex.whiteui).tint(c);
    }

    static String statText(int[] mods) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < mods.length; i += 2) {
            int v = mods[i + 1];
            if (i > 0) sb.append("  ");
            sb.append(v >= 0 ? "[#7cfc00]+" : "[scarlet]").append(v).append(' ').append(tr(NewModePvp.STATS[mods[i]].vi, NewModePvp.STATS[mods[i]].en)).append("[]");
        }
        return sb.toString();
    }

    /** Thẻ vật phẩm: icon + tên + độ hiếm + chỉ số; tối đa 2 nút. */
    static Table itemRow(Def d, float W, boolean narrow, String b1, Runnable r1, String b2, Runnable r2, boolean glow) {
        Table border = new RarBorder(d.rarity, glow ? 0.95f : 0.55f);
        border.margin(2f);
        Table row = new Table(Styles.black8);
        row.margin(8);
        float iconSz = narrow ? 36 : 44;
        row.add(new Image(icon(d))).size(iconSz).padRight(10);
        String body = d.dvi != null ? "[#7cfc00]" + tr(d.dvi, d.den) + "[]" : statText(d.mods);
        if (d.svi != null) body += (body.isEmpty() ? "" : "\n") + "[#2ee6c8]" + tr(d.svi, d.sen) + "[]";
        row.add(col(d.rarity) + name(d) + "[]  [lightgray]" + slotName(d.slot) + " · " + rarName(d.rarity) + "[]\n" + body).left().growX().wrap().minWidth(0);
        Table bt = row;
        if (narrow) { row.row(); bt = new Table(); }
        float bw = narrow ? 64 : 72;
        if (b1 != null) bt.button(b1, r1).size(bw, NewModePvp.btnH()).padLeft(6);
        if (b2 != null) bt.button(b2, r2).size(bw, NewModePvp.btnH()).padLeft(4);
        if (narrow) row.add(bt).colspan(2).growX().padTop(6).row();
        border.add(row).growX();
        return border;
    }

    static int ns() { return slots(); }
    static final boolean[] roleOpen = {true, true, true, true, true, true};
    /** Một vai trò: nút mở rộng/thu gọn + toàn bộ ô trang bị của vai trò đó (slot % 6 == vai trò). */
    static Table roleBlock(final NewModePvp.St st, final int role, final float cw) {
        final Table box = new Table();
        final Runnable[] fill = {null};
        fill[0] = () -> {
            box.clear();
            int tot = 0, used = 0;
            for (int s = role, ns = slots(); s < ns; s += TYPES) { tot++; if (st.gEq[s] >= 0) used++; }
            box.button((roleOpen[role] ? "^  " : "v  ") + tr(SLOT_VI[role], SLOT_EN[role]) + "  " + used + "/" + tot, Styles.flatBordert, () -> { roleOpen[role] = !roleOpen[role]; fill[0].run(); })
                .width(cw).height(NewModePvp.btnH() * 0.9f).row();
            if (!roleOpen[role]) return;
            final float tw = cw / 2f - 3f;
            Table g = new Table();
            for (int k = 0, s = role; s < ns(); k++, s += TYPES) {      // mỗi vai trò chia thành 4 ô (2x2)
                g.add(slotTile(st, s, tw)).width(tw).minHeight(tw * 1.05f).pad(1.5f);
                if (k % 2 == 1) g.row();
            }
            box.add(g).padTop(4).row();
        };
        fill[0].run();
        return box;
    }

    /** Một ô trang bị trên trang chính. */
    static Table slotTile(NewModePvp.St st, final int slot, float w) {
        Def d = def(st.gEq[slot]);
        Color c = d != null ? colorOf(d.rarity) : Color.valueOf("5b6070");
        Table border = d != null ? new RarBorder(d.rarity, 0.95f) : new Table(tinted(c.cpy().a(0.5f)));
        border.margin(2f);
        Table in = new Table(Styles.black8);
        in.margin(6);
        in.add("[lightgray]" + "#" + (slot / TYPES + 1) + "[]").top().row();
        if (d != null) {
            in.add(new Image(icon(d))).size(Math.min(52f, w * 0.6f)).pad(2).row();
            in.add(col(d.rarity) + name(d) + "[]").growX().wrap().minWidth(0).labelAlign(arc.util.Align.center).row();
            in.add("[lightgray]" + rarName(d.rarity) + "[]").row();
        } else {
            Image im = new Image(slotIcon(slot % TYPES));
            im.setColor(1f, 1f, 1f, 0.28f);
            in.add(im).size(Math.min(52f, w * 0.6f)).pad(2).row();
            in.add("[gray]" + (w < 100f ? tr("Trống", "Empty") : tr("Trống - chạm để lắp", "Empty - tap to equip")) + "[]").growX().wrap().minWidth(0).labelAlign(arc.util.Align.center).row();
        }
        border.add(in).grow();
        border.touchable = Touchable.enabled;
        border.clicked(() -> openBagFor(slot));
        return border;
    }

    /** Nội dung tab "TRANG BỊ" (thay cho tab CHỈ SỐ khi Hỗn loạn tắt). */
    static void buildTab(Table c, NewModePvp.St st, float W) {
        Table hd = new Table(Styles.black6);
        hd.margin(8);
        hd.add("[accent]" + tr("Trang bị vật phẩm", "Item equipment") + "[]").left().row();
        hd.add("[lightgray]" + tr("Hạ quái đợt để nhận vật phẩm rơi vào Túi đồ (càng hiếm càng khó rơi). Chạm một ô để mở Túi đồ rồi bấm Lắp. Vật phẩm cộng/trừ cấp chỉ số và một số vật phẩm có kỹ năng đặc biệt.",
            "Kill wave monsters to get item drops in your Bag (rarer = lower chance). Tap a slot to open the Bag then press Equip. Items modify stat levels and some grant special skills.") + "[]").left().growX().wrap().minWidth(0).row();
        c.add(hd).width(W).pad(3).row();
        c.button(tr("Cửa hàng", "Shop"), Styles.flatBordert, GearItems::openShop).width(W - 8f).height(NewModePvp.btnH() * 0.9f).pad(3).row();
        c.button(tr("Chỉ số", "Stats"), Styles.flatBordert, NewModePvp::openStatsView).width(W - 8f).height(NewModePvp.btnH() * 0.9f).pad(3).row();

        int cols = W < 440f ? 2 : 3;
        float cw = Math.max(110f, (W - 8f * cols - 8f) / cols);
        Table grid = new Table();
        for (int r = 0; r < TYPES; r++) {
            grid.add(roleBlock(st, r, cw)).width(cw).top().pad(4);
            if ((r + 1) % cols == 0) grid.row();
        }
        c.add(grid).width(W).row();

        Table tot = new Table(Styles.black6);
        tot.margin(8);
        tot.add("[accent]" + tr("Chỉ số & kỹ năng từ vật phẩm", "Bonus stats & skills from items") + "[]").left().row();
        int[] g = delta(st);
        boolean any = false;
        if (g != null) for (int i = 0; i < g.length; i++) {
            if (g[i] == 0) continue;
            any = true;
            int base = st.s != null && i < st.s.length ? st.s[i] : 0;
            int lv = Math.max(floorOf(i), base + g[i]);
            tot.add("[white]" + tr(NewModePvp.STATS[i].vi, NewModePvp.STATS[i].en) + "[]  " + (g[i] > 0 ? "[#7cfc00]+" : "[scarlet]") + g[i] + "[]  [lightgray]→ " + NewModePvp.STATS[i].f.get(lv) + "[]")
                .left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (hasSkill(st, SK_MOVE_ATK)) {
            any = true;
            tot.add("[#2ee6c8]" + tr("Xung phong (di chuyển): ", "Charge (moving): ") + "[#7cfc00]+" + st.gMoveAtk + "%[] [lightgray](" + tr("tối đa 15%", "max 15%") + ")[]").left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (hasSkill(st, SK_KILL_SPD)) {
            any = true;
            tot.add("[#2ee6c8]" + tr("Truy kích: ", "Pursuit: ") + "[lightgray]" + tr("tiêu diệt mục tiêu → +50% tốc độ trong 6s", "on kill → +50% speed for 6s") + "[]").left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (hasSkill(st, SK_KILL_SKILL_ATK)) {
            any = true;
            tot.add("[#2ee6c8]" + tr("Huyết chú (kỹ năng unit player): ", "Bloodspell (player unit skills): ") + "[#7cfc00]+" + st.gSkillAtk + "%[] [lightgray](" + tr("tối đa 500%", "max 500%") + ")[]").left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (hasSkill(st, SK_MIN_PT)) {
            any = true;
            tot.add("[#2ee6c8]" + tr("Tài lộc: ", "Fortune: ") + "[lightgray]" + tr("+1 điểm mỗi phút", "+1 point every minute") + "[]").left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (hasSkill(st, SK_DEATH_CDR)) {
            any = true;
            tot.add("[#2ee6c8]" + tr("Phục hận: ", "Vengeance: ") + "[lightgray]" + tr("bị player địch hạ → -50% hồi sinh & kỹ năng pilot", "killed by enemy player → -50% respawn & pilot skill CD") + "[]").left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (!any) tot.add("[lightgray]" + tr("Chưa lắp vật phẩm nào.", "No items equipped yet.") + "[]").left().row();
        c.add(tot).width(W).pad(3).row();
    }

    /** Mục "Vật phẩm" ở đầu trang Túi đồ. */
    static void bagSection(Table c, NewModePvp.St st, float W, boolean narrow) {
        Table hd = new Table(Styles.black6);
        hd.margin(8);
        hd.add("[accent]" + tr("Vật phẩm", "Items") + "[]").left().expandX();
        hd.add("[lightgray]" + st.gInv.size() + "/" + INV_CAP + "[]").right();
        c.add(hd).width(W).pad(3).row();

        final int fs = filterSlot;
        if (fs >= 0) {
            c.add("[lightgray]" + tr("Đang chọn ô: ", "Selected slot: ") + "[white]" + slotName(fs) + "[]").left().padLeft(6).padTop(2).row();
        }
        // đang lắp
        boolean anyEq = false;
        for (int s = 0, ns = slots(); s < ns; s++) {
            final int slot = s;
            Def d = def(st.gEq[s]);
            if (d == null || (fs >= 0 && fs != s)) continue;
            if (!anyEq) { anyEq = true; c.add("[lightgray]" + tr("Đang lắp", "Equipped") + "[]").left().padLeft(6).padTop(4).row(); }
            c.add(itemRow(d, W, narrow, tr("Tháo", "Remove"), () -> NewModePvp.send(REQ_GEAR, "u|" + slot), null, null, true)).width(W).pad(3).row();
        }
        // trong túi: vật phẩm của ô đang chọn lên đầu, rồi theo độ hiếm
        ArrayList<Def> inv = new ArrayList<>();
        for (int id : st.gInv) { Def d = def(id); if (d != null && (fs < 0 || d.slot == fs % TYPES)) inv.add(d); }   // chọn ô vai trò nào chỉ hiện item vai trò đó
        inv.sort((x, y) -> {
            int fx = fs >= 0 && x.slot == fs % TYPES ? 0 : 1, fy = fs >= 0 && y.slot == fs % TYPES ? 0 : 1;
            if (fx != fy) return fx - fy;
            if (x.rarity != y.rarity) return y.rarity - x.rarity;
            return x.id - y.id;
        });
        if (inv.isEmpty()) {
            c.add("[lightgray]" + (fs >= 0 ? tr("Chưa có vật phẩm vai trò " + SLOT_VI[fs % TYPES] + " trong túi.", "No " + SLOT_EN[fs % TYPES] + " items in your bag.") : tr("Chưa có vật phẩm nào trong túi. Hãy hạ quái đợt!", "No items in your bag yet. Kill wave monsters!")) + "[]").left().padLeft(6).padTop(4).width(W - 12).wrap().row();
            return;
        }
        c.add("[lightgray]" + tr("Trong túi", "In bag") + "[]").left().padLeft(6).padTop(4).row();
        for (final Def d : inv) {
            boolean match = fs < 0 || d.slot == fs % TYPES;
            Table r = itemRow(d, W, narrow, d.kind == K_HASTE ? tr("Dùng", "Use") : tr("Lắp", "Equip"), () -> NewModePvp.send(REQ_GEAR, "e|" + d.id + (match && fs >= 0 ? "|" + fs : "")), tr("Bỏ", "Drop"), () -> NewModePvp.send(REQ_GEAR, "d|" + d.id), match && fs >= 0);
            c.add(r).width(W).pad(3).row();
        }
    }
}
