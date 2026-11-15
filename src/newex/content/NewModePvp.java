package newex.content;

import arc.Core;
import arc.Events;
import arc.func.Func;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.*;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.entities.units.StatusEntry;

import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.ctype.ContentType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.consumers.*;
import arc.func.Cons;
import arc.math.geom.Vec2;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Fires;
import mindustry.entities.Predict;
import mindustry.entities.Units;
import mindustry.entities.bullet.LiquidBulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Bullet;
import mindustry.gen.Teamc;
import mindustry.type.StatusEffect;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.util.Align;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.world.blocks.ConstructBlock;
import arc.graphics.Blending;
import arc.math.Interp;
import mindustry.entities.Damage;
import mindustry.gen.Entityc;
import mindustry.ai.types.GroundAI;
import mindustry.ai.types.CommandAI;
import arc.util.Tmp;
import arc.func.Boolc;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import mindustry.ui.Styles;
import arc.input.KeyCode;
import arc.scene.ui.layout.Cell;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.func.Floatp;
import arc.scene.ui.Label;
import mindustry.ui.Bar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * newmode-pvp.js -> Java (chia phan, ghep boi build.py). Phan 1: hang so, trang thai nguoi choi, cai dat, luu/dong bo.
 * Quy uoc: so thuc JS (double) -> float; CHI cho tinh toan nhay lam tron (barrackCost) dung double (bai hoc 113).
 */
public class NewModePvp {

    static final String MAP_ID = "pvpnewex3110";
    static final int REWARD_POINTS = 30;
    static final String TAG_ACTIVE = "newex-pvp";
    static final String TAG_STORE = "newex-pvp-store";
    static final String KEY_EXP = "newex-pvp-exp-percent";
    static final String KEY_MULTI = "newex-pvp-multi-turret";
    static final String KEY_PVB = "newex-pvp-player-vs-bot";
    static final String KEY_BVB = "newex-pvp-bot-vs-bot";
    static final String KEY_BUILT = "newex-pvp-max-built";
    static final int DEFAULT_BUILT = 3;
    static final int LIMIT_BUILT = 12;
    static final String KEY_EXT = "newex-pvp-ext";
    static final String KEY_BZONE = "newex-pvp-build-zone";
    static final String KEY_RZONE = "newex-pvp-return-zone";
    static final String KEY_UPANY = "newex-pvp-up-anywhere";
    static final String KEY_WRAND = "newex-pvp-wave-random";
    static final String KEY_TERR = "newex-pvp-territory";
    static final int LIMIT_TERR = 100;
    static final int EXT_BUILD_RANGE = 100;
    static final String KEY_SHIELD = "newex-pvp-shield-min";
    static final String KEY_HUD = "newex-pvp-hud";
    static final String KEY_HUD_X = "newex-pvp-hud-x";
    static final String KEY_HUD_Y = "newex-pvp-hud-y";
    static final String KEY_JOY = "newex-pvp-joystick";
    static final int[] RESPAWN_SEC = new int[] {2, 4, 6, 9, 12};
    static final int DEFAULT_EXP = 100;
    static final int LIMIT_EXP = 1000;
    static final int DEFAULT_SHIELD_MIN = 15;
    static final int LIMIT_SHIELD_MIN = 9999;
    static final int SHIELD_FOREVER_TICKS = 1000000000;
    static final int SHIELD_HP = 15000;
    static final int SHIELD_BUFFER = 1000000;
    static final int SHIELD_SYNC_TICKS = 30;
    static final String SHIELD_KIND = "newex-pvp-shield";
    static final String EXP_KIND = "newex-pvp-exp";
    static final String PFX_KIND = "newex-pvp-pfx";
    static final String CORE_KIND = "newex-pvp-coredie";
    static final Color SHIELD_COLOR = Color.valueOf("#d000ff");
    static final Color SHIELD_GLOW = Color.valueOf("#00f0ff");
    static final Color SHIELD_GOLD = Color.valueOf("#ffe58a");
    static final Color COL_EMPIX_PLUS = Color.valueOf("4cff5a");
    static final Color COL_CMD_X = Color.valueOf("ffd34d");
    static final Color COL_CMD_O = Color.valueOf("4dff88");
    static final Color COL_BUILD_OK = Color.valueOf("4dff88");
    static final Color COL_BUILD_BAD = Color.valueOf("ff4d4d");
    static final String SYNC_KIND = "newex-pvp-sync";
    static final String REQ_UP = "newex-pvp-up";
    static final String REQ_BUY = "newex-pvp-buy";
    static final String REQ_SELL = "newex-pvp-sell";
    static final String REQ_UNIT = "newex-pvp-unit";
    static final String REQ_PILOT = "newex-pvp-pilot";
    static final int PILOT_XIKI = 1;
    static final int PILOT_XAKY = 3;
    static final int PILOT_HIST = 4;
    static final int HIST_STAT_PCT = 0;
    static final int HIST_PIERCE_ADD = 0;
    static final int HIST_SHIELD_PCT = 0;
    static final int HIST_REGEN_PCT = 0;
    static final int HIST_SOLDIER_DMG = 0;
    static final int HIST_WAVE_DMG = 0;
    static final int HIST_PLAYER_DMG = 0;
    static final int HIST_ARMOR_SHRED = 0;
    static final int XAKY_MIN_LEVEL = 11;
    static final float XAKY_HP_MUL = 0.8f;
    static final float XAKY_SPD_MUL = 1.2f;
    static final int XAKY_CD = 10 * 60;
    static final int XAKY_DASH = 96;
    static final int PILOT_EMPIX = 2;
    static final int EMPIX_MIN_LEVEL = 11;
    static final float EMPIX_ATK_PCT = 0.05f;
    static final float EMPIX_HP_PCT = 0.05f;
    static final float EMPIX_HEAL_HP = 0.5f;
    static final float EMPIX_HEAL_CHANCE = 0.5f;
    static final int EMPIX_HEAL_CD = 5 * 60 * 60;
    static final int PILOT_XAKYNEO = 5;
    static final float XAKYNEO_ATK_PCT = 0.05f;
    static final int XAKYNEO_CRIT_BONUS_LV = 1;
    static final float XAKYNEO_RAMP_PCT_MIN = 0.1f;
    static final int XAKYNEO_RAMP_MAX = 100;
    static final float XAKYNEO_HIGHHP_RATIO = 0.5f;
    static final float XAKYNEO_HIGHHP_DMG = 0.35f;
    static final float XAKYNEO_EXECUTE_RATIO = 0.16f;
    static final float XAKYNEO_EXECUTE_SELF_PCT = 0.09f;
    static final float NEO_BLAST_R = 52.5f;
    static final int NEO_BLAST_ATK = 2;
    static final float NEO_BLAST_ARMOR = 0.5f;
    static final float NEO_BLAST_HP = 0.5f;
    static final int PILOT_VANHAU = 6;
    static final float VANHAU_TRIGGER_HP = 0.7f;
    static final float VANHAU_SHIELD_PCT = 1.2f;
    static final int VANHAU_CD = 3 * 60 * 60;
    static final String VHS_KIND = "newex-pvp-vhs";
    static final int CORE_DIE_TICKS = 240;
    static final float XIKI_EXP_PCT = 0.05f;
    static final float XIKI_LVUP_PCT = 0.05f;
    static final int XIKI_LVUP_FLAT = 500;
    static final String REQ_MODE = "newex-pvp-mode";
    static final String REQ_CHEAT = "newex-pvp-cheat";
    static final String REQ_PT = "newex-pvp-pt";
    static final String REQ_RESET = "newex-pvp-reset";
    static final String[] CHEAT_SEQ = new String[] {"x", "a", "k", "y", "f", "u", "l", "l"};
    static final int CHEAT_POINTS = 999;
    static final int CHEAT_GAP_TICKS = 180;
    static final int MAX_LEVEL = 99;
    static final String KEY_MAXLV = "newex-pvp-max-level";
    static final int LIMIT_MAXLV = 999;
    static final int FREE_SWITCH_LEVEL = 5;
    static final int STAT_PER_PAGE = 4;
    static final int TURRET_PER_PAGE = 5;
    static final int UNIT_PER_PAGE = 5;
    static final int TIER_MAX = 7;
    static final int STAT_MAX = 20;
    static final int STAT_MAIN = 12;
    static final int STAT_ARMY = 12;
    static final int RANGE_PCT = 5;
    static final int LIFE_PCT = 10;
    static final int START_POINTS = 3;
    static final int PASSIVE_EXP = 60;
    static final int PASSIVE_INTERVAL = 3600;
    static final int MAX_WAVE_UNITS = 200;
    static final float REIGN_HP_MUL = 1.5f;
    static final float DISRUPT_SPEED_MUL = 2.2f;
    static final int TERRITORY_TILES = 100;
    static final int TERRITORY_RADIUS = TERRITORY_TILES * Vars.tilesize;
    static final float TERRITORY_SPEED_MUL = 1.5f;
    static final int TERRITORY_EXP_PCT = 50; // trong lanh tho: +50% EXP nhan them
    static final int LATE_EXP_MINUTES = 30; // sau bao nhieu phut tran dau thi bat bonus EXP
    static final int LATE_EXP_PCT = 2000; // +2000% EXP nhan them (cong don, = x21 neu chi co bonus nay)
    static final int LATE_EXP_TICKS = LATE_EXP_MINUTES * 3600;
    static final float EXP_PER_HP = 0.1f;
    static final int DEATH_EXP_PCT = 10;
    static final int SOLDIER_EXP_PCT = 10;
    static final int KILL_EXP_PCT = 20;
    static final int SPAWN_GUARD_TICKS = 600;
    static final int SPAWN_GUARD_DAMAGE_CUT = 99;
    static final int SPAWN_RAGE_TICKS = 180;
    static final int SPAWN_RAGE_MAXHP_PCT = 100;
    static final int EXP_HP_BONUS_REF = 5000;
    static final float EXP_HP_BONUS_MAX = 1.5f;
    static final int CREDIT_RADIUS = 420;
    static final int NEAR_CORE = 240;
    static final int AURA_BURN_TICKS = 600;
    static final int WAVE_SPACING = 2400;
    static final int FIRST_WAVE = 1200;
    static final int WAVE_UNIT_CAP = 400;
    static final int BONUS_WAVE_INTERVAL = 8 * 60 * 60;
    static final int BONUS_WAVE_TIMES = 5;
    static final int BONUS_WAVE_MAX_NEW = 150;
    static final int EQUIPPED = -1;
    static final int IN_BAG = -2; // phao trong tui do
    static final String BUILD_FLOOR = "core-zone"; // chi xay phao tren nen dat loi ("" = moi nen)
    static final int EQUIP_SLOT_CAP = 12;
    static final String REQ_BARR = "newex-pvp-barr";
    static final String REQ_WALL = "newex-pvp-wall";
    static final int WALL_NONE = -3;
    static final String[] WALL_CHAIN = new String[] {"copper-wall", "titanium-wall", "plastanium-wall", "thorium-wall", "phase-wall", "surge-wall"};
    static final int[] WALL_COST = new int[] {2, 3, 5, 8, 12, 16};
    static final String WALL_BUILD_ID = "__wall";
    static final String REQ_CMD = "newex-pvp-cmd";
    static final int ARMY_PER_PAGE = 5;
    static final float BARRACKS_DISCOUNT = 0.8f;
    static final int HP_PCT = 12;
    static final int HP_FLAT = 20;
    static final int ARM_FLAT = 1;
    static final float ARM_CUT = 1.5f;
    static final int ARM_CUT_MAX = 50;
    static final int ATK_PCT = 8;
    static final float ATK_FLAT = 0.3f;
    static final int SPD_PCT = 4;
    static final float SPD_FLAT = 0.01f;
    static final float REGEN_PCT = 0.25f;
    static final float REGEN_THRESHOLD = 0.99f;
    static final float DMG_PCT_MAXHP = 0.05f;
    static final float DMG_PER_ARMOR = 0.03f;
    static final int WRAND_DMG_PCT = 5;
    static final float KNOCK_PER = 1.0f;
    static final String ROLE_BRAWLER = "brawler";
    static final String ROLE_ATTACK = "attack";
    static final String ROLE_DEFENSE = "defense";
    static final String ROLE_DEALER = "dealer";
    static final int ROLE_ATK_PCT = 5;
    static final int ROLE_HP_PCT = 15;
    static final int ROLE_ARM_PCT = 10;
    static final int ROLE_ARM_FLAT = 5;
    static final int ROLE_CUT_PCT = 5;
    static final int ROLE_CUT_CAP = 60;
    static final int ROLE_ATK_MAX = 50;
    static final int ROLE_HP_MAX = 100;
    static final String MALIGN_ID = "malign";
    static final float MALIGN_SPEED_MUL = 0.5f;
    static final int MALIGN_ATK_PCT = 5;
    static final float MALIGN_RELOAD_MUL = 0.2f;
    static final float MALIGN_LIFE_MUL = 0.9f;
    static final String SMITE_ID = "smite";
    static final String LUSTRE_ID = "lustre";
    static final int LUSTRE_ATK_PCT = 18;
    static final float SMITE_RELOAD_MUL = 0.5f;
    static final int SMITE_ATK_PCT = 18;
    static final int LIGHT_SPD_PCT = 8;
    static final String THERDUM_ID = "therdum";
    static final int THERDUM_PRICE = 10;
    static final int THERDUM_HIT_CAP = 100;


    // ===== bang du lieu (JS object/array nhieu dong) =====
    static final String[] DRONE_IDS = {
        "duo", "scatter", "scorch", "hail", "wave", "lancer", "arc", "parallax", "swarmer",
        "salvo", "segment", "tsunami", "fuse", "ripple", "cyclone", "foreshadow", "spectre", "meltdown",
        "breach", "diffuse", "sublimate", "titan", "disperse", "afflict", "lustre", "scathe", "smite", "malign", "therdum"
    };
    static final class Barrack {
        final String t; final int sec, n, cap, cost;
        Barrack(String t, int sec, int n, int cap, int cost) { this.t = t; this.sec = sec; this.n = n; this.cap = cap; this.cost = cost; }
    }
    static final Barrack[] BARRACKS = {
        new Barrack("stell", 10, 1, 4, 3), new Barrack("stell", 8, 1, 6, 4), new Barrack("stell", 7, 2, 8, 5),
        new Barrack("locus", 12, 1, 6, 7), new Barrack("locus", 9, 1, 8, 9), new Barrack("locus", 7, 2, 10, 11),
        new Barrack("precept", 14, 1, 6, 14), new Barrack("precept", 10, 1, 8, 18),
        new Barrack("vanquish", 25, 1, 4, 24), new Barrack("vanquish", 20, 1, 5, 28),
        new Barrack("conquer", 45, 1, 2, 32), new Barrack("conquer", 35, 1, 3, 40)
    };
    static final class Wave {
        final String t; final int b, e, sp, n, sc, mx;
        Wave(String t, int b, int e, int sp, int n, int sc, int mx) { this.t = t; this.b = b; this.e = e; this.sp = sp; this.n = n; this.sc = sc; this.mx = mx; }
    }
    static final Wave[] WAVES = {
        new Wave("dagger", 1, 60, 1, 2, 2, 16), new Wave("mace", 8, 90, 2, 1, 2, 10), new Wave("fortress", 18, 120, 3, 1, 2, 8),
        new Wave("quasar", 32, 999, 4, 1, 3, 6), new Wave("scepter", 45, 999, 6, 1, 4, 3), new Wave("reign", 80, 999, 10, 1, 5, 2)
    };
    static final HashMap<String, String> TURRET_ROLES = new HashMap<>();
    static {
        for (String id : new String[]{"duo", "scorch", "wave", "arc", "parallax", "tsunami", "fuse", "meltdown", "diffuse", "sublimate", "therdum"}) TURRET_ROLES.put(id, ROLE_BRAWLER);
        for (String id : new String[]{"scatter", "hail", "lancer", "swarmer", "salvo", "ripple", "cyclone", "spectre", "breach", "titan", "disperse", "afflict", "lustre", "smite"}) TURRET_ROLES.put(id, ROLE_ATTACK);
        TURRET_ROLES.put("segment", ROLE_DEFENSE);
        for (String id : new String[]{"foreshadow", "scathe", "malign"}) TURRET_ROLES.put(id, ROLE_DEALER);
    }
    static final HashMap<String, Integer> PRICE_OVERRIDE = new HashMap<>();
    static { PRICE_OVERRIDE.put(THERDUM_ID, THERDUM_PRICE); }

    // ===== cooldown phi cong =====
    static final HashMap<Integer, Float> xakyCdAt = new HashMap<>(), vanhauCdAt = new HashMap<>(), empixCdAt = new HashMap<>(), pilotFxAt = new HashMap<>();
    static final class PilotCd {
        final int pi, cd; final HashMap<Integer, Float> at;
        PilotCd(int pi, HashMap<Integer, Float> at, int cd) { this.pi = pi; this.at = at; this.cd = cd; }
    }
    static final PilotCd[] PILOT_CDS = {
        new PilotCd(PILOT_XAKY, xakyCdAt, XAKY_CD), new PilotCd(PILOT_VANHAU, vanhauCdAt, VANHAU_CD), new PilotCd(PILOT_EMPIX, empixCdAt, EMPIX_HEAL_CD)
    };
    static PilotCd pilotCdFor(int pi) {
        for (PilotCd c : PILOT_CDS) if (c.pi == pi) return c;
        return null;
    }
    static final class VanhauField { int uid; float fl, st, hp = Float.NaN, max, r, at; }
    static final HashMap<Integer, VanhauField> vanhauField = new HashMap<>();

    // ===== STATS =====
    static final class Stat {
        final String vi, en; final Func<Integer, String> f;
        Stat(String vi, String en, Func<Integer, String> f) { this.vi = vi; this.en = en; this.f = f; }
    }
    static final Stat[] STATS = {
        new Stat("Máu", "Health", l -> "+" + (l * HP_PCT) + "%  +" + (l * HP_FLAT) + " " + tr("máu", "HP")),
        new Stat("Giáp", "Armor", l -> "+" + (l * ARM_FLAT) + " " + tr("giáp", "armor") + "  -" + Strings.fixed(armorCut(l), 1) + "% " + tr("sát thương nhận", "damage taken")),
        new Stat("Tấn công", "Attack", l -> "+" + (l * ATK_PCT) + "%  +" + Strings.fixed(l * ATK_FLAT, 1) + " " + tr("sát thương/phát", "dmg/hit")),
        new Stat("Tốc độ di chuyển", "Move speed", l -> "+" + (l * SPD_PCT) + "%  +" + Strings.fixed(l * SPD_FLAT, 2) + " " + tr("tốc", "speed")),
        new Stat("Tốc độ bắn", "Fire rate", l -> "+" + (l * 6) + "%"),
        new Stat("Tỉ lệ bạo kích", "Crit chance", l -> (l * 2) + "%"),
        new Stat("Sát thương bạo kích", "Crit damage", l -> (150 + l * 12) + "%"),
        new Stat("Xuyên giáp", "Armor pierce", l -> (l * 5) + "%"),
        new Stat("Hồi máu", "Regeneration", l -> "+" + Strings.fixed(l * REGEN_PCT, 2) + tr("% máu tối đa mỗi giây (khi máu dưới 99%)", "% max HP per second (when below 99% HP)")),
        new Stat("Sát thương % máu tối đa", "Max HP % damage", l -> "+" + Strings.fixed(l * DMG_PCT_MAXHP, 2) + "% " + tr("máu tối đa của mục tiêu mỗi lần trúng", "of target max HP per hit")),
        new Stat("Sát thương theo giáp", "Armor-based damage", l -> "+" + Strings.fixed(l * DMG_PER_ARMOR * 10, 1) + "% " + tr("mỗi 10 giáp hiện tại", "per 10 current armor")),
        new Stat("Đẩy lùi", "Knockback", l -> "+" + Strings.fixed(l * KNOCK_PER, 2) + " " + tr("lực đẩy lùi", "knockback")),
        new Stat("Tầm bắn & đạn của lính", "Soldier range & bullet life", l -> "+" + (l * RANGE_PCT) + "% " + tr("tầm bắn & tốc độ bay đạn", "range & bullet speed") + "  +" + (l * LIFE_PCT) + "% " + tr("thời gian tồn tại đạn", "bullet lifetime")),
        new Stat("Sát thương quái spawn ngẫu nhiên", "Random-spawn monster damage", l -> "+" + (l * WRAND_DMG_PCT) + "% " + tr("sát thương khi đánh trúng quái từ đợt spawn ngẫu nhiên", "damage when hitting monsters from random spawn waves"))
    };
    /** Hai chi so dau (Mau, Giap) khong gioi han; JS: STATS[i].mx khong ton tai nen cac chi so khac = STAT_MAX. */
    static boolean statUnlimited(int i) { return i == 0 || i == 1; }
    static int statMax(int i) { return statUnlimited(i) ? Integer.MAX_VALUE : STAT_MAX; }
    static String statMaxLabel(int i) { return statUnlimited(i) ? "\u221e" : String.valueOf(STAT_MAX); }
    static float armorCut(int l) { return Math.min(ARM_CUT_MAX, l * ARM_CUT); }

    // ===== tuong / linh =====
    static Block[] wallBlocks = null;
    static Block wallBlock(int i) {
        if (wallBlocks == null) {
            wallBlocks = new Block[WALL_CHAIN.length];
            for (int k = 0; k < WALL_CHAIN.length; k++) wallBlocks[k] = Vars.content.block(WALL_CHAIN[k]);
        }
        return wallBlocks[Math.max(0, Math.min(WALL_CHAIN.length - 1, i))];
    }
    static int wallBonus(St st) { return (st == null || st.w == WALL_NONE) ? 0 : 1 + st.wl; }
    /** JS: Math.ceil(cost * (1 - 0.8)) bang double; dung float se ra 2 thay vi 1 (vd cost=5). */
    static int barrackCost(int i) { return Math.max(1, (int) Math.ceil(BARRACKS[i].cost * (1.0 - 0.8))); }

    // ===== role =====
    static String roleText(String id) {
        String r = TURRET_ROLES.get(id);
        if (ROLE_BRAWLER.equals(r)) return "[orange]" + tr("Cận chiến", "Brawler") + "[]";
        if (ROLE_ATTACK.equals(r)) return "[scarlet]" + tr("Tấn công", "Attack") + "[]";
        if (ROLE_DEFENSE.equals(r)) return "[sky]" + tr("Phòng thủ", "Defense") + "[]";
        if (ROLE_DEALER.equals(r)) return "[gold]" + tr("Sát thương", "Damage") + "[]";
        return "";
    }
    static String roleDesc(String id) {
        String s = roleDescBase(id);
        if (THERDUM_ID.equals(id)) s += tr(". Nội tại: sát thương tối đa nhận vào mỗi hit là " + THERDUM_HIT_CAP, ". Passive: max damage taken per hit is " + THERDUM_HIT_CAP);
        if (LUSTRE_ID.equals(id)) s += tr(". Mỗi lustre trang bị thêm không bắn thêm tia, chỉ +" + LUSTRE_ATK_PCT + "% tấn công", ". Extra copies fire no extra beams, only +" + LUSTRE_ATK_PCT + "% attack each");
        if ("duo".equals(id) || "breach".equals(id))
            s += tr(". Thêm: +" + LIGHT_SPD_PCT + "% tốc độ di chuyển (không cộng dồn)", ". Also: +" + LIGHT_SPD_PCT + "% move speed (does not stack)");
        return s;
    }
    static String roleDescBase(String id) {
        if (SMITE_ID.equals(id)) return tr("Trang bị: +" + ROLE_ATK_PCT + "% tấn công (tối đa " + ROLE_ATK_MAX + "%). Giảm 50% tốc độ bắn. Mỗi pháo trang bị thêm không bắn thêm đạn, chỉ +" + SMITE_ATK_PCT + "% tấn công",
            "Equipped: +" + ROLE_ATK_PCT + "% attack (max " + ROLE_ATK_MAX + "%). -50% fire rate. Extra copies fire no extra bullets, only +" + SMITE_ATK_PCT + "% attack each");
        if (MALIGN_ID.equals(id)) {
            int sp = Math.round((1 - MALIGN_SPEED_MUL) * 100), rl = Math.round((1 - MALIGN_RELOAD_MUL) * 100), lf = Math.round((1 - MALIGN_LIFE_MUL) * 100);
            return tr("Trang bị: -" + sp + "% tốc độ di chuyển, +" + MALIGN_ATK_PCT + "% tấn công, -" + rl + "% tốc độ bắn, -" + lf + "% thời gian tồn tại của đạn",
                "Equipped: -" + sp + "% move speed, +" + MALIGN_ATK_PCT + "% attack, -" + rl + "% fire rate, -" + lf + "% bullet lifetime");
        }
        String r = TURRET_ROLES.get(id);
        if (ROLE_ATTACK.equals(r)) return tr("Trang bị: +" + ROLE_ATK_PCT + "% tấn công (tối đa " + ROLE_ATK_MAX + "%)", "Equipped: +" + ROLE_ATK_PCT + "% attack (max " + ROLE_ATK_MAX + "%)");
        if (ROLE_BRAWLER.equals(r)) return tr("Trang bị: +" + ROLE_HP_PCT + "% máu (tối đa " + ROLE_HP_MAX + "%)", "Equipped: +" + ROLE_HP_PCT + "% HP (max " + ROLE_HP_MAX + "%)");
        if (ROLE_DEFENSE.equals(r)) return tr("Trang bị: +" + ROLE_ARM_PCT + "% giáp, +" + ROLE_ARM_FLAT + " giáp, -" + ROLE_CUT_PCT + "% sát thương nhận (tối đa " + ROLE_CUT_CAP + "%)",
            "Equipped: +" + ROLE_ARM_PCT + "% armor, +" + ROLE_ARM_FLAT + " armor, -" + ROLE_CUT_PCT + "% damage taken (max " + ROLE_CUT_CAP + "%)");
        return "";
    }

    // ===== trang thai nguoi choi =====
    /** JS newState(): {lv,exp,pts,u,s,d,p,b,pi,kc,w,wl}; cmd chi co khi da dat. eqC/hs/xns la cache KHONG luu (JS loai khoi JSON). */
    static final class St {
        int lv = 1, pts = START_POINTS, u = 0, b = 0, pi = 0, w = WALL_NONE, wl = 0, cmd = 0;
        float exp = 0;
        int[] s = new int[STATS.length];
        int[] kc = {0, 0, 0};
        ArrayList<String> d = new ArrayList<>();
        ArrayList<Integer> p = new ArrayList<>();
        Object eqC, hs, xns;
        void padStats() { if (s.length < STATS.length) s = Arrays.copyOf(s, STATS.length); }
    }

    static LinkedHashMap<String, St> store = new LinkedHashMap<>();
    static HashMap<Integer, St> byId = new HashMap<>();
    static Seq<Team> teamsCache = null;
    static boolean dirty = false, registered = false;
    static int stVer = 0;
    static final HashMap<String, Boolean> logged = new HashMap<>();
    static final HashMap<String, Boolean> cfgBool = new HashMap<>();
    static final HashMap<String, Float> cfgBoolAt = new HashMap<>(), cfgIntAt = new HashMap<>();
    static final HashMap<String, Integer> cfgInt = new HashMap<>();
    static Boolean enCache = null;
    static float enAt = 0;

    static void invalidateCfgCache() { cfgBoolAt.clear(); cfgIntAt.clear(); enAt = 0; }
    static boolean isEn() {
        float now = Time.time;
        if (enCache != null && now >= enAt && now - enAt < 120) return enCache;
        enAt = now;
        try { enCache = String.valueOf(Core.settings.getString("locale", "")).startsWith("en"); } catch (Throwable err) { enCache = false; }
        return enCache;
    }
    static String tr(String vi, String en) { return isEn() ? en : vi; }

    static arc.struct.StringMap getTags() {
        mindustry.core.GameState gs = Vars.state;
        return (gs != null && gs.rules != null) ? gs.rules.tags : null;
    }
    static void fx(float x, float y) {
        try {
            if (!Vars.net.client()) { Call.effect(Fx.spawn, x, y, 0, Color.white); return; }
        } catch (Throwable err) {}
        try { Fx.spawn.at(x, y); } catch (Throwable err) {}
    }
    static void spawnFx(float x, float y) {
        fx(x, y);
        try { if (!Vars.net.client()) Call.effect(Fx.spawn, x, y, 90, Color.white); } catch (Throwable err) {}
    }
    static void syncRules() { try { if (Vars.net.server()) Call.setRules(Vars.state.rules); } catch (Throwable err) {} }

    static Seq<Team> pvpTeams() {
        if (teamsCache != null) return teamsCache;
        Seq<Team> out = new Seq<>();
        for (Team t : Team.all) if (t != Team.derelict && t != Team.crux && t.cores().size > 0) out.add(t);
        Team[] fill = {Team.sharded, Team.blue, Team.green, Team.malis};
        for (int k = 0; k < fill.length && out.size < 2; k++) if (!out.contains(fill[k])) out.add(fill[k]);
        teamsCache = out;
        return out;
    }
    static boolean isPvpMap() {
        try {
            mindustry.maps.Map m = Vars.state != null ? Vars.state.map : null;
            if (m == null || m.file == null) return false;
            return MAP_ID.equals(m.file.nameWithoutExtension());
        } catch (Throwable err) {}
        return false;
    }
    static boolean isFull() {
        if (Vars.state == null || !Vars.state.isGame()) return false;
        arc.struct.StringMap tags = getTags();
        if (tags != null && "1".equals(tags.get(TAG_ACTIVE, "0"))) return true;
        return isPvpMap();
    }
    static boolean setBool(String k, boolean d) {
        float now = Time.time; Float at = cfgBoolAt.get(k);
        if (at != null && now >= at && now - at < 60) return cfgBool.get(k);
        boolean v = d;
        try { v = Core.settings.getBool(k, d); } catch (Throwable err) {}
        cfgBool.put(k, v); cfgBoolAt.put(k, now);
        return v;
    }
    static int getIntCached(String k, int d) {
        float now = Time.time; Float at = cfgIntAt.get(k);
        if (at != null && now >= at && now - at < 60) return cfgInt.get(k);
        int v = d;
        try { v = Core.settings.getInt(k, d); } catch (Throwable err) {}
        cfgInt.put(k, v); cfgIntAt.put(k, now);
        return v;
    }
    static boolean buildZoneOn() { return setBool(KEY_BZONE, true); }
    static boolean returnZoneOn() { return setBool(KEY_RZONE, true); }
    static boolean upAnywhere() { return setBool(KEY_UPANY, false); }
    static boolean waveRandomOn() { return setBool(KEY_WRAND, true); }
    static int terrRadius() {
        int v = getIntCached(KEY_TERR, 0);
        if (!(v > 0)) return isFull() ? 0 : EXT_BUILD_RANGE;
        return Math.min(LIMIT_TERR, v);
    }
    static boolean extEnabled() { return setBool(KEY_EXT, true); }
    static boolean isExt() {
        try {
            if (Vars.state == null || !Vars.state.isGame() || Vars.state.isEditor()) return false;
            if (!extEnabled() || Vars.state.rules.pvp || isFull()) return false;
            return true;
        } catch (Throwable err) {}
        return false;
    }
    /** JS global.newexNeoMark: file khac goi (Java: NewModePvp.neoMark()). */
    public static float neoMark() {
        try {
            if (!isActive()) return 0f;
            St st = byId.get(Vars.player.id);
            return (st != null && st.pi == PILOT_XAKYNEO) ? XAKYNEO_EXECUTE_RATIO : 0f;
        } catch (Throwable err) { return 0f; }
    }
    static boolean isActive() { return isFull() || isExt(); }
    static boolean isTierUnit(Unit u) {
        if (u == null) return false;
        for (int lv = 1; lv <= TIER_MAX; lv++) if (u.type == tierType(lv)) return true;
        return false;
    }
    static boolean pvpOn(Player pl) {
        if (isFull()) return true;
        try { return pl != null && isTierUnit(pl.unit()); } catch (Throwable err) {}
        return false;
    }
    static boolean pvpCtl() {
        try { return isFull() || (isActive() && Vars.player != null && isTierUnit(Vars.player.unit())); } catch (Throwable err) {}
        return false;
    }
    static int maxLevel() {
        int v = getIntCached(KEY_MAXLV, MAX_LEVEL);
        if (!(v >= 1)) v = MAX_LEVEL;
        return Math.min(LIMIT_MAXLV, v);
    }
    static int expPercent() {
        int v = getIntCached(KEY_EXP, DEFAULT_EXP);
        if (!(v >= 0)) v = DEFAULT_EXP;
        return Math.min(LIMIT_EXP, v);
    }
    static int maxBuilt() {
        int v = getIntCached(KEY_BUILT, DEFAULT_BUILT);
        if (!(v >= 0)) v = DEFAULT_BUILT;
        return Math.min(LIMIT_BUILT, v);
    }
    static boolean multiTurretOn() { return setBool(KEY_MULTI, true); }
    static int shieldMinutes() {
        int v = getIntCached(KEY_SHIELD, DEFAULT_SHIELD_MIN);
        if (!(v >= 0)) v = DEFAULT_SHIELD_MIN;
        return Math.min(LIMIT_SHIELD_MIN, v);
    }

    static String getUid(Player pl) {
        try { String u = String.valueOf(pl.uuid()); if (u != null && u.length() > 0) return u; } catch (Throwable err) {}
        return "id:" + pl.id;
    }
    static St bind(Player pl) {
        St st = byId.get(pl.id);
        if (st != null) return st;
        String uid = getUid(pl);
        st = store.get(uid);
        if (st == null) { st = new St(); store.put(uid, st); }
        st.padStats();
        byId.put(pl.id, st);
        dirty = true;
        return st;
    }
    static void changed() { dirty = true; stVer++; }

    static UnitType[] tierList = null;
    static UnitType tierType(int lv) {
        if (lv == 6) return UnitTypes.reign;
        if (lv == 7) return UnitTypes.disrupt;
        if (tierList == null) tierList = new UnitType[]{UnitTypes.merui, UnitTypes.cleroi, UnitTypes.anthicus, UnitTypes.tecta, UnitTypes.collaris};
        return tierList[Math.min(5, Math.max(1, lv)) - 1];
    }
    static boolean isExtraTier(int lv) { return lv == 6 || lv == 7; }
    static int tierCap(int lv) { return Math.min(5, Math.max(1, lv)); }
    static int activeTier(St st) {
        int cap = tierCap(st.lv);
        if (st.lv < FREE_SWITCH_LEVEL) return cap;
        int u = st.u;
        if (!(u >= 1) || u > TIER_MAX) return cap;
        return u;
    }
    static int expNeed(int lv) { int n = lv - 1; return Math.round(100 + 70 * n + 12 * n * n); }

    static void logOnce(String key, String msg, boolean info) {
        if (logged.get(key) != null) return;
        logged.put(key, true);
        if (info) Log.info("[NexPvp] " + msg); else Log.err("[NexPvp] " + msg);
    }
    /** JS: NexNet.toastTo(pl, text, 3). Java NexNet chua co toastTo (bai 111i) -> dung toast cua game. */
    static void toast(Player pl, String text) {
        try {
            if (pl == null) return;
            if (pl == Vars.player || Vars.headless == false && pl.con == null) Vars.ui.showInfoToast(text, 3f);
            else if (pl.con != null) Call.infoToast(pl.con, text, 3f);
        } catch (Throwable err) {}
    }
    /** JS: new Packages.mindustry.ai.types.NoAI() - lop NoAI khong con o v157.4 -> bo dieu khien khong lam gi (script tu lai bang botTick/soldierAI). */
    static final class NoAI extends mindustry.entities.units.AIController { @Override public void updateUnit() {} }

    static boolean lateExpAnnounced = false;
    /** Da qua LATE_EXP_MINUTES phut ke tu luc bat dau tran (chi tinh o map PvP, chay tren may chu/host). */
    static boolean lateExpOn() { return isFull() && matchT >= LATE_EXP_TICKS; }
    static void lateExpCheck() {
        if (lateExpAnnounced || !lateExpOn()) return;
        lateExpAnnounced = true;
        toastAll(tr("[accent]Đã qua " + LATE_EXP_MINUTES + " phút! Mọi người nhận thêm +" + LATE_EXP_PCT + "% EXP.",
            "[accent]" + LATE_EXP_MINUTES + " minutes in! Everyone now gets +" + LATE_EXP_PCT + "% EXP."), 6f);
    }
    /** He so EXP cong don: 1 + (50% neu dang trong lanh tho phe minh) + (2000% sau 30 phut). Chi ap dung o map PvP. */
    static float expBonusMul(Player pl) {
        if (!isFull()) return 1f;
        float bonus = 0f;
        try {
            Unit u = pl.unit();
            if (u != null && u.isValid() && inOwnTerritory(u)) bonus += TERRITORY_EXP_PCT / 100f;
        } catch (Throwable err) {}
        if (lateExpOn()) bonus += LATE_EXP_PCT / 100f;
        return 1f + bonus;
    }
    static void addExp(Player pl, float amount) { addExp(pl, amount, false); }
    static void addExp(Player pl, float amount, boolean quiet) {
        St st = bind(pl);
        if (st.lv >= maxLevel()) return;
        if (st.pi == PILOT_XIKI) amount = amount * (1 + XIKI_EXP_PCT);
        if (st.pi == PILOT_HIST) amount = amount * (1 + HIST_STAT_PCT);
        amount = amount * expBonusMul(pl);
        st.exp += amount;
        if (!quiet) notifyExp(pl, amount);
        int before = st.lv, up = 0;
        while (st.lv < maxLevel() && st.exp >= expNeed(st.lv)) {
            st.exp -= expNeed(st.lv);
            st.lv++; st.pts++; up++;
            if (st.pi == PILOT_XIKI && st.lv < maxLevel()) st.exp += XIKI_LVUP_FLAT + expNeed(st.lv) * XIKI_LVUP_PCT;
        }
        if (st.lv >= maxLevel()) st.exp = 0;
        if (up > 0) {
            toast(pl, tr("[accent]Lên cấp ", "[accent]Level up ") + st.lv + " - +" + up + tr(" điểm nâng cấp và ô lắp pháo. Về lõi để nâng cấp!", " upgrade point(s) and turret slot(s). Return to your core!") +
                (before < FREE_SWITCH_LEVEL && st.lv >= FREE_SWITCH_LEVEL ? tr(" Đã mở đổi unit trong bảng nâng cấp!", " You can now switch units in the upgrade menu!") : ""));
            stVer++;
        }
        dirty = true;
    }

    // ===== luu / khoi phuc / dong bo (JSON giu nguyen dinh dang JS de save cu doc duoc) =====
    static void arr(StringBuilder sb, int[] a) {
        sb.append('[');
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        sb.append(']');
    }
    static void strs(StringBuilder sb, ArrayList<String> a) {
        sb.append('[');
        for (int i = 0; i < a.size(); i++) { if (i > 0) sb.append(','); sb.append('"').append(a.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
        sb.append(']');
    }
    static void ints(StringBuilder sb, ArrayList<Integer> a) {
        sb.append('[');
        for (int i = 0; i < a.size(); i++) { if (i > 0) sb.append(','); sb.append(a.get(i)); }
        sb.append(']');
    }
    static void persistStore() {
        try {
            if (Vars.net.client()) return;
            arc.struct.StringMap tags = getTags();
            if (tags == null) return;
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, St> e : store.entrySet()) {
                St s = e.getValue();
                if (!first) sb.append(','); first = false;
                sb.append('"').append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":{\"lv\":").append(s.lv)
                    .append(",\"exp\":").append(Float.isNaN(s.exp) ? 0f : s.exp).append(",\"pts\":").append(s.pts).append(",\"u\":").append(s.u).append(",\"s\":");
                arr(sb, s.s); sb.append(",\"d\":"); strs(sb, s.d); sb.append(",\"p\":"); ints(sb, s.p);
                sb.append(",\"b\":").append(s.b).append(",\"pi\":").append(s.pi).append(",\"kc\":"); arr(sb, s.kc);
                sb.append(",\"w\":").append(s.w).append(",\"wl\":").append(s.wl).append(",\"cmd\":").append(s.cmd).append('}');
            }
            tags.put(TAG_STORE, sb.append('}').toString());
        } catch (Throwable err) { logOnce("persiststore", "persist store failed: " + err, false); }
    }
    static int[] readInts(JsonValue v, int min, int[] def) {
        if (v == null || !v.isArray()) return def;
        int[] a = new int[Math.max(min, v.size)];
        int i = 0;
        for (JsonValue c = v.child; c != null; c = c.next) a[i++] = (int) c.asDouble();
        return a;
    }
    /** Doc 1 doi tuong nguoi choi dang JS (object co khoa) tu JsonValue. */
    static St readStObj(JsonValue o) {
        St s = new St();
        s.lv = o.getInt("lv", 1); s.exp = o.getFloat("exp", 0f); s.pts = o.getInt("pts", START_POINTS); s.u = o.getInt("u", 0);
        s.s = readInts(o.get("s"), STATS.length, s.s);
        JsonValue d = o.get("d");
        if (d != null && d.isArray()) for (JsonValue c = d.child; c != null; c = c.next) s.d.add(c.asString());
        JsonValue p = o.get("p");
        if (p != null && p.isArray()) for (JsonValue c = p.child; c != null; c = c.next) s.p.add((int) c.asDouble());
        s.b = o.getInt("b", 0); s.pi = o.getInt("pi", 0);
        int[] kc = readInts(o.get("kc"), 3, null);
        if (kc != null) s.kc = kc;
        s.w = o.getInt("w", WALL_NONE); s.wl = o.getInt("wl", 0); s.cmd = o.getInt("cmd", 0);
        return s;
    }
    static void restoreStore() {
        try {
            arc.struct.StringMap tags = getTags();
            String raw = tags != null ? tags.get(TAG_STORE, "") : "";
            store = new LinkedHashMap<>();
            if (raw != null && raw.length() > 0) {
                JsonValue root = new JsonReader().parse(raw);
                for (JsonValue c = root.child; c != null; c = c.next) store.put(c.name, readStObj(c));
            }
        } catch (Throwable err) {
            logOnce("restorestore", "restore store failed: " + err, false);
            store = new LinkedHashMap<>();
        }
    }
    static void broadcast() {
        StringBuilder sb = new StringBuilder("{\"d\":{");
        boolean first = true;
        for (Map.Entry<Integer, St> e : byId.entrySet()) {
            St s = e.getValue();
            if (!first) sb.append(','); first = false;
            sb.append('"').append(e.getKey()).append("\":[").append(s.lv).append(',').append((int) Math.floor(s.exp)).append(',').append(s.pts).append(',');
            arr(sb, s.s); sb.append(','); strs(sb, s.d); sb.append(',').append(s.u).append(','); ints(sb, s.p);
            sb.append(',').append(s.b).append(',').append(s.pi).append(','); arr(sb, s.kc);
            sb.append(',').append(s.cmd).append(',').append(s.w).append(',').append(s.wl).append(']');
        }
        sb.append("}}");
        if (Vars.net.server()) { try { Call.clientPacketReliable(SYNC_KIND, sb.toString()); } catch (Throwable err) {} }
        stVer++;
    }
    static void onSync(String content) {
        try {
            JsonValue d = new JsonReader().parse(content).get("d");
            HashMap<Integer, St> n = new HashMap<>();
            for (JsonValue a = d.child; a != null; a = a.next) {
                St s = new St();
                s.lv = a.get(0).asInt(); s.exp = a.get(1).asFloat(); s.pts = a.get(2).asInt();
                s.s = readInts(a.get(3), STATS.length, s.s);
                for (JsonValue c = a.get(4).child; c != null; c = c.next) s.d.add(c.asString());
                s.u = a.get(5).asInt();
                JsonValue pp = a.get(6);
                if (pp != null && pp.size == a.get(4).size) for (JsonValue c = pp.child; c != null; c = c.next) s.p.add((int) c.asDouble());
                s.b = a.get(7).asInt(); s.pi = a.get(8).asInt();
                int[] kc = readInts(a.get(9), 3, null);
                if (kc != null) s.kc = kc;
                s.cmd = a.get(10).asInt(); s.w = a.get(11).asInt(); s.wl = a.get(12).asInt();
                n.put(Integer.parseInt(a.name), s);
            }
            byId = n;
            stVer++;
        } catch (Throwable err) {}
    }

    /** parseInt cua JS: lay phan so nguyen dau chuoi ("12x" -> 12); khong co so -> null. */
    static Integer leadInt(String s) {
        int i = 0, n = s.length();
        while (i < n && Character.isWhitespace(s.charAt(i))) i++;
        int st = i;
        if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
        int ds = i;
        while (i < n && Character.isDigit(s.charAt(i))) i++;
        if (i == ds) return null;
        try { return Integer.parseInt(s.substring(st, i)); } catch (Throwable e) { return null; }
    }
    static float pf(String s) { try { return Float.parseFloat(s.trim()); } catch (Throwable e) { return Float.NaN; } }

    static void register() {
        if (registered) return;
        try {
            if (Vars.netClient == null) return;
            Vars.netClient.addPacketHandler(SYNC_KIND, content -> onSync(content));
            Vars.netClient.addPacketHandler(SHIELD_KIND, content -> onShieldSync(content));
            Vars.netClient.addPacketHandler(PFX_KIND, content -> {
                try {
                    Integer id = leadInt(content);
                    if (id == null) return;
                    pilotFxAt.put(id, Time.time);
                    if (content.indexOf("x") >= 0) xakyCdAt.put(id, Time.time);
                    else if (content.indexOf("v") >= 0) vanhauCdAt.put(id, Time.time);
                    else if (content.indexOf("n") < 0) empixCdAt.put(id, Time.time);
                } catch (Throwable err) {}
            });
            Vars.netClient.addPacketHandler(VHS_KIND, content -> {
                try {
                    String[] a = content.split("\\|");
                    Integer id = leadInt(a[0]), uid = leadInt(a[1]);
                    float hp = pf(a[2]);
                    if (id == null || uid == null) return;
                    if (!(hp > 0)) { vanhauField.remove(id); return; }
                    VanhauField f = vanhauField.get(id);
                    if (f == null || f.uid != uid) { f = new VanhauField(); vanhauField.put(id, f); }
                    if (!Float.isNaN(f.hp) && hp < f.hp) f.fl = 1;
                    f.uid = uid; f.hp = hp; f.max = pf(a[3]); f.r = pf(a[4]); f.at = Time.time;
                } catch (Throwable err) {}
            });
            Vars.netClient.addPacketHandler(CORE_KIND, content -> {
                try { String[] a = content.split("\\|"); coreDeathFx(pf(a[0]), pf(a[1]), pf(a[2])); } catch (Throwable err) {}
            });
            Vars.netClient.addPacketHandler(EXP_KIND, content -> onExpPacket(content));
            registered = true;
        } catch (Throwable err) {}
    }
    /** Goi tu NexMod/init: JS chay register() ngay + 3 su kien. */
    public static void initBase() {
        register();
        Events.on(ClientLoadEvent.class, e -> register());
        Events.on(ServerLoadEvent.class, e -> register());
        Events.on(WorldLoadEvent.class, e -> register());
    }


    // ===== PHAN 2: dinh nghia phao, cap dan, dat/go phao, mount (JS 924-1542) =====
    // JS isCoreHost chi duoc DOC (khong dong nao gan) -> luon undefined: bo nhanh core-host (parity).
    static final class Def {
        final String id; final Block block; final int cost; final boolean equip = true, mount; final String kind;
        final HashMap<String, Object> ov = new HashMap<>();
        Item ammoItem; boolean ammoItemSet, errLogged, warned;
        BulletType eqBullet; boolean eqBulletSet;
        Def(String id, Block block, int cost, boolean mount, String kind) { this.id = id; this.block = block; this.cost = cost; this.mount = mount; this.kind = kind; }
    }
    static final class Defs { final ArrayList<Def> list = new ArrayList<>(); final HashMap<String, Def> map = new HashMap<>(); }
    static Defs defsCache = null;
    static final HashMap<String, Boolean> NO_MOUNT = new HashMap<>();
    static { NO_MOUNT.put("scathe", true); }
    static final HashMap<Integer, Integer> hostOwner = new HashMap<>();

    // --- phan xa (JS o[k] voi try/catch) ---
    static final HashMap<Class<?>, HashMap<String, java.lang.reflect.Field>> fieldCache = new HashMap<>();
    static java.lang.reflect.Field findField(Object o, String k) {
        Class<?> c = o.getClass();
        HashMap<String, java.lang.reflect.Field> m = fieldCache.get(c);
        if (m == null) { m = new HashMap<>(); fieldCache.put(c, m); }
        if (m.containsKey(k)) return m.get(k);
        java.lang.reflect.Field f = null;
        try { f = c.getField(k); } catch (Throwable e) {}
        m.put(k, f);
        return f;
    }
    static Object fld(Object o, String k, Object d) {
        try {
            if (o == null) return d;
            java.lang.reflect.Field f = findField(o, k);
            if (f == null) return d;
            Object v = f.get(o);
            return v == null ? d : v;
        } catch (Throwable err) { return d; }
    }
    static float fldF(Object o, String k, float d) { Object v = fld(o, k, null); return v instanceof Number ? ((Number) v).floatValue() : d; }
    static boolean fldB(Object o, String k, boolean d) { Object v = fld(o, k, null); return v instanceof Boolean ? (Boolean) v : d; }
    static void setf(Object o, String k, Object v) {
        try { java.lang.reflect.Field f = findField(o, k); if (f != null) f.set(o, v); } catch (Throwable err) {}
    }

    static Block droneBlock(String id) {
        Block b = null;
        if (id.equals("parallax") || id.equals("segment")) b = Vars.content.getByName(ContentType.block, id);
        if (b == null) b = Vars.content.getByName(ContentType.block, "newex-" + id);
        if (b == null) b = Vars.content.getByName(ContentType.block, id);
        if (b == null) return null;
        return (b instanceof BaseTurret) ? b : null;
    }
    static int costOf(Block blk) {
        double w = 0;
        try { for (ItemStack rq : blk.requirements) w += rq.amount * (double) rq.item.cost; } catch (Throwable err) {}
        return Math.max(1, (int) Math.round(Math.sqrt(w) / 8));
    }
    static String normId(String id) {
        String x = id.trim();
        if (x.indexOf("newex-") == 0) x = x.substring(6);
        return x;
    }
    static String className(Object o) { try { return o.getClass().getSimpleName(); } catch (Throwable err) { return ""; } }
    static String turretKind(Block blk) {
        if (blk instanceof TractorBeamTurret) return "tractor";
        if (blk instanceof PointDefenseTurret) return "point";
        if (blk instanceof LaserTurret) return "laser";
        if (blk instanceof ContinuousTurret || blk instanceof ContinuousLiquidTurret) return "cont";
        return "shoot";
    }
    static Defs droneDefs() {
        if (defsCache != null) return defsCache;
        Defs r = new Defs();
        for (String raw : DRONE_IDS) {
            String id = normId(raw);
            if (id.length() == 0 || r.map.containsKey(id)) continue;
            Block blk = droneBlock(id);
            if (blk == null) { Log.warn("[NexPvp] DRONE_IDS: không tìm thấy pháo \"" + id + "\" (newex-" + id + " hoặc " + id + "), bỏ qua"); continue; }
            Integer po = PRICE_OVERRIDE.get(id);
            Def d = new Def(id, blk, po != null ? po : costOf(blk), NO_MOUNT.get(id) == null, turretKind(blk));
            r.list.add(d); r.map.put(d.id, d);
        }
        defsCache = r;
        return r;
    }
    static boolean isDummyBullet(BulletType bt) { try { return bt.speed == 0 && bt.lifetime == 0 && bt.damage == 0; } catch (Throwable err) { return false; } }

    static final class Pair { final Object k; final BulletType v; Pair(Object k, BulletType v) { this.k = k; this.v = v; } }
    /** ItemTurret/LiquidTurret co ammoTypes; PowerTurret... khong (JS: undefined -> rong). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static ObjectMap<Object, BulletType> ammoMap(Block blk) {
        if (blk instanceof ItemTurret) return (ObjectMap) ((ItemTurret) blk).ammoTypes;
        if (blk instanceof LiquidTurret) return (ObjectMap) ((LiquidTurret) blk).ammoTypes;
        return null;
    }
    static ArrayList<Pair> ammoPairs(Block blk) {
        ArrayList<Pair> out = new ArrayList<>();
        ObjectMap<Object, BulletType> map = ammoMap(blk);
        if (map == null) return out;
        for (Item k : Vars.content.items()) { BulletType v = map.get(k); if (v != null) out.add(new Pair(k, v)); }
        for (Liquid k : Vars.content.liquids()) { BulletType v = map.get(k); if (v != null) out.add(new Pair(k, v)); }
        return out;
    }
    static Item ammoItemFor(Block block) {
        Object best = null;
        for (Pair p : ammoPairs(block)) {
            if (best == null) best = p.k;
            if (!isDummyBullet(p.v)) { best = p.k; break; }
        }
        return best instanceof Item ? (Item) best : null;
    }
    static boolean hasAmmoSafe(Building b) {
        try { return !(b instanceof Turret.TurretBuild) || ((Turret.TurretBuild) b).hasAmmo(); } catch (Throwable err) { return true; }
    }
    static void fillAmmo(Building b, Def def) {
        Block blk = def.block;
        if (blk instanceof ItemTurret) {
            ItemTurret it = (ItemTurret) blk;
            ItemTurret.ItemTurretBuild tb = (ItemTurret.ItemTurretBuild) b;
            if (!def.ammoItemSet) { def.ammoItem = ammoItemFor(blk); def.ammoItemSet = true; }
            Item item = def.ammoItem;
            if (item == null) return;
            int max = it.maxAmmo, guard = 0;
            while (guard < 300 && tb.totalAmmo < max) {
                guard++;
                int before = tb.totalAmmo;
                tb.handleItem(tb, item);
                if (tb.totalAmmo <= before) break;
            }
            // JS con nhanh du phong `new ItemEntry(item, max)`: constructor khong public o v157 -> bo (chi chay khi handleItem that bai).
            return;
        }
        try {
            if (b.liquids == null) return;
            for (Pair p : ammoPairs(blk)) {
                if (p.k instanceof Liquid) {
                    float room = (blk.liquidCapacity > 0 ? blk.liquidCapacity : 30) - b.liquids.currentAmount();
                    if (room > 0.1f) b.liquids.add((Liquid) p.k, room);
                    break;
                }
            }
        } catch (Throwable err) {}
    }
    static void fillConsumers(Building b, Def def) {
        Block blk = def.block;
        Consume[] list = blk.consumers;
        if (list == null) return;
        for (Consume c : list) {
            if (c == null) continue;
            try {
                if (c.optional) continue;
                String cn = className(c);
                if (cn.equals("ConsumeItems") && b.items != null) {
                    for (ItemStack s : ((ConsumeItems) c).items) {
                        int need = Math.min(blk.itemCapacity, s.amount * 2);
                        if (b.items.get(s.item) < need) b.items.set(s.item, need);
                    }
                } else if (cn.equals("ConsumeLiquid") && b.liquids != null) {
                    Liquid lq = ((ConsumeLiquid) c).liquid;
                    float cap = blk.liquidCapacity;
                    if (b.liquids.get(lq) < cap * 0.9f) b.liquids.set(lq, cap);
                } else if ((cn.equals("ConsumeCoolant") || cn.equals("ConsumeLiquidFilter")) && b.liquids != null) {
                    float capW = blk.liquidCapacity;
                    if (b.liquids.get(Liquids.water) < capW * 0.9f) b.liquids.set(Liquids.water, capW);
                }
            } catch (Throwable err) {}
        }
    }
    static void supplyTurret(Building b, Def def, boolean tick, Player pl) {
        try { if (b.power != null) b.power.status = 1f; } catch (Throwable err) {}
        try {
            float hr = fldF(def.block, "heatRequirement", 0f);
            if (hr > 0) {
                float full = hr * Math.max(1f, fldF(def.block, "maxHeatEfficiency", 1f));
                setf(b, "heatReq", full);
                Object sh = fld(b, "sideHeat", null);
                if (sh instanceof float[]) Arrays.fill((float[]) sh, full);
            }
        } catch (Throwable err) {}
        boolean isT = def.block instanceof Turret;
        boolean low = isT && !hasAmmoSafe(b);
        if (!tick && !low) return;
        try { fillAmmo(b, def); } catch (Throwable err) {
            if (!def.errLogged) { def.errLogged = true; Log.err("[NexPvp] fillAmmo " + def.id + ": " + err); }
        }
        try { fillConsumers(b, def); } catch (Throwable err) {}
        if (isT && !hasAmmoSafe(b) && !def.warned) {
            def.warned = true;
            if (pl != null) toast(pl, "[scarlet]" + def.block.localizedName + tr(" không nhận được đạn tự động", " could not be auto-supplied"));
        }
    }

    // ===== vung xay / dat phao =====
    static Block coreZoneFloor = null; static boolean coreZoneChecked = false;
    static boolean isCoreZoneFloor(Block fl) {
        if (fl == null) return false;
        if (!coreZoneChecked) { coreZoneChecked = true; try { coreZoneFloor = Vars.content.getByName(ContentType.block, BUILD_FLOOR); } catch (Throwable err) {} }
        if (coreZoneFloor != null) return fl == coreZoneFloor;
        return BUILD_FLOOR.equals(fl.name);
    }
    static final class EcEnt { float t; ArrayList<Tile> list; }
    static final HashMap<Integer, EcEnt> ecCache = new HashMap<>();
    static ArrayList<Tile> enemyCores(Team team) {
        float now = Time.time;
        EcEnt ent = ecCache.get(team.id);
        if (ent != null && now >= ent.t && now - ent.t < 20) return ent.list;
        final ArrayList<Tile> out = new ArrayList<>();
        Groups.build.each(b -> { try { if (b.block instanceof CoreBlock && b.team != team && b.team != Team.derelict) out.add(b.tile); } catch (Throwable err) {} });
        EcEnt n = new EcEnt(); n.t = now; n.list = out;
        ecCache.put(team.id, n);
        return out;
    }
    static boolean ourSide(CoreBlock.CoreBuild core, ArrayList<Tile> ec, int tx, int ty) {
        float d0 = Mathf.dst2(tx, ty, core.tile.x, core.tile.y);
        for (Tile e : ec) if (Mathf.dst2(tx, ty, e.x, e.y) <= d0) return false;
        return true;
    }
    static boolean spotFree(int tx, int ty, int size, int off) {
        boolean checkFloor = BUILD_FLOOR.length() > 0 && isFull() && buildZoneOn();
        for (int dx = 0; dx < size; dx++) for (int dy = 0; dy < size; dy++) {
            Tile t = Vars.world.tile(tx - off + dx, ty - off + dy);
            if (t == null || t.solid() || t.build != null || t.floor().isDeep()) return false;
            if (checkFloor && !isCoreZoneFloor(t.floor())) return false;
        }
        return true;
    }
    static boolean canBuildAt(CoreBlock.CoreBuild core, ArrayList<Tile> ec, Block blk, int tx, int ty) {
        int off = (blk.size - 1) / 2, rr = terrRadius();
        if (rr > 0) { if (Mathf.dst(tx, ty, core.tile.x, core.tile.y) > rr) return false; }
        else if (!ourSide(core, ec, tx, ty)) return false;
        return spotFree(tx, ty, blk.size, off);
    }
    static int placeTurret(Player pl, Def def, int tx, int ty) {
        CoreBlock.CoreBuild core = pl.team().core();
        if (core == null) return -1;
        if (!canBuildAt(core, enemyCores(core.team), def.block, tx, ty)) return -1;
        Tile tile = Vars.world.tile(tx, ty);
        if (tile == null) return -1;
        tile.setNet(def.block, pl.team(), 0);
        Building b = tile.build;
        if (b == null || b.block != def.block) return -1;
        supplyTurret(b, def, true, pl);
        hostOwner.put(b.id, pl.id);
        fx(b.x, b.y);
        return tile.pos();
    }
    static void removeTurret(int pos, Block blk) {
        if (!(pos >= 0)) return;
        Building b = Vars.world.build(pos);
        if (b != null && blk != null && b.block == blk) b.tile.setNet(Blocks.air, Team.derelict, 0);
    }
    static void removeTurret(int pos, Def def) { removeTurret(pos, def == null ? null : def.block); }
    static boolean turretAlive(int pos, Def def, Team team) {
        Building b = Vars.world.build(pos);
        return b != null && b.isValid() && b.block == def.block && b.team == team;
    }
    static void feedTurrets(Player pl, St st, boolean tick, boolean auth) {
        if (st.p.size() != st.d.size()) return;
        Defs defs = droneDefs();
        for (int i = st.d.size() - 1; i >= 0; i--) {
            Def def = defs.map.get(st.d.get(i));
            int pos = st.p.get(i);
            if (def == null) {
                if (tick && auth) {
                    Block ob = droneBlock(st.d.get(i));
                    int refund = ob != null ? costOf(ob) : 0;
                    if (pos >= 0) removeTurret(pos, ob);
                    st.d.remove(i); st.p.remove(i);
                    st.pts += refund;
                    toast(pl, tr("[accent]Pháo đã bị gỡ khỏi danh sách, hoàn lại ", "[accent]Turret removed from the list, refunded ") + refund + tr(" điểm", " pt"));
                    changed();
                }
                continue;
            }
            if (!(pos >= 0)) continue;
            if (tick && auth && !turretAlive(pos, def, pl.team())) {
                st.p.set(i, IN_BAG);
                toast(pl, tr("[scarlet]Pháo bị phá hủy, đã trả về túi đồ: ", "[scarlet]Turret destroyed, returned to your bag: ") + def.block.localizedName);
                changed();
                continue;
            }
            Building b = Vars.world.build(pos);
            if (b == null || b.block != def.block) continue;
            supplyTurret(b, def, tick, auth ? pl : null);
            hostOwner.put(b.id, pl.id);
            if (isFull() && inOwnTerritory(b.team, b.x, b.y)) territoryFx(b.x, b.y, b.block.size * Vars.tilesize);
            if (st.s[4] > 0) {
                float rm = 1 + st.s[4] * 0.06f;
                try { b.applyBoost(rm, 5f); } catch (Throwable err) {}
            }
            if (auth) preferEnemyPlayer(b, def);
        }
    }

    // ===== uu tien ban nguoi choi dich =====
    static final class Fept { float bx, by, minR, range, bd = 1e18f; Team team; boolean ta = true, tg = true; Unit best; }
    static final Fept feptCtx = new Fept();
    static void feptCheck(Unit e) {
        Fept c = feptCtx;
        if (e == null || e.team == c.team || e.team == Team.derelict || e.dead || !e.isValid()) return;
        if (e.isFlying() ? !c.ta : !c.tg) return;
        try { if (!e.targetable(c.team)) return; } catch (Throwable err1) {}
        float d = Mathf.dst(c.bx, c.by, e.x, e.y);
        if (d - e.hitSize / 2 > c.range || d < c.minR) return;
        if (d < c.bd) { c.bd = d; c.best = e; }
    }
    static Unit findEnemyPlayerTarget(Building b, Def def, float range) {
        Block blk = def.block;
        Fept c = feptCtx;
        c.bx = b.x; c.by = b.y; c.team = b.team;
        c.ta = fldB(blk, "targetAir", true); c.tg = fldB(blk, "targetGround", true);
        c.minR = fldF(blk, "minRange", 0f); c.range = range; c.best = null; c.bd = 1e18f;
        try {
            Groups.player.each(p -> feptCheck(p.unit()));
            for (int bi = 0; bi < bots.size(); bi++) feptCheck(bots.get(bi).botPl.unit());
        } catch (Throwable err) { logOnce("fept", "findEnemyPlayerTarget: " + err, false); }
        return c.best;
    }
    static final HashMap<Integer, Float> playerPrioT = new HashMap<>();
    static final float PLAYER_PRIO_INTERVAL = 15;
    static void preferEnemyPlayer(Building b, Def def) {
        if (def.kind.equals("point")) return;
        try {
            Float l0 = playerPrioT.get(b.id);
            float left = (l0 == null ? 0 : l0) - Time.delta;
            if (left > 0) { playerPrioT.put(b.id, left); return; }
            playerPrioT.put(b.id, PLAYER_PRIO_INTERVAL);
            Unit t = findEnemyPlayerTarget(b, def, turretRange(def));
            if (t != null && b instanceof Turret.TurretBuild) ((Turret.TurretBuild) b).target = t;
        } catch (Throwable err) { logOnce("prioplayer:" + def.id, "prioritize enemy player failed: " + err, false); }
    }

    // ===== dan trang bi =====
    static final HashMap<Integer, Boolean> loadedBt = new HashMap<>();
    static void ensureLoaded(BulletType bt) {
        if (bt == null) return;
        try {
            if (loadedBt.get((int) bt.id) == null) {
                loadedBt.put((int) bt.id, true);
                // JS: backRegion/frontRegion chi co o BasicBulletType; loai khac -> undefined == null -> luon load()
                boolean need = !(bt instanceof BasicBulletType) || ((BasicBulletType) bt).backRegion == null || ((BasicBulletType) bt).frontRegion == null;
                if (need) bt.load();
            }
        } catch (Throwable err) { logOnce("ld:" + bt, "bullet load failed: " + err, false); }
        try { ensureLoaded(bt.fragBullet); } catch (Throwable err) {}
    }
    static BulletType fallbackBullet(Block blk) {
        BulletType bt = null;
        try {
            float range = fldF(blk, "range", 0f); if (!(range > 0)) range = 160f;
            BasicBulletType b = new BasicBulletType(6f, 14f);
            b.lifetime = range / 6; b.width = 7; b.height = 10;
            bt = b;
            logOnce("fb:" + blk.name, "no ammo data for " + blk.name + ", using fallback bullet", false);
        } catch (Throwable err) {}
        return bt;
    }
    static BulletType equipBullet(Def def) {
        if (def.eqBulletSet) return def.eqBullet;
        Block blk = def.block;
        BulletType bt = null;
        for (Pair p : ammoPairs(blk)) {
            if (bt == null) bt = p.v;
            if (!isDummyBullet(p.v)) { bt = p.v; break; }
        }
        if (bt == null) { Object st = fld(blk, "shootType", null); if (st instanceof BulletType) bt = (BulletType) st; }
        if (bt == null) bt = fallbackBullet(blk);
        ensureLoaded(bt);
        def.eqBullet = bt; def.eqBulletSet = true;
        return bt;
    }

    // ===== equipSummary (JS 301-358, 390-394) =====
    static final class Rc { int atk, brl, def; }
    static final Rc EMPTY_RC = new Rc();
    static final class EquipSum {
        int ver; boolean multi, defsReady, malign, lightSpd, hasTherdum, hasEq;
        Rc rc; HashMap<String, Integer> counts; int nLu, nSm; ArrayList<Def> eqList;
    }
    static int cnt(HashMap<String, Integer> m, String k) { Integer v = m.get(k); return v == null ? 0 : v; }
    static EquipSum equipSummary(St st) {
        if (st == null || st.p.size() != st.d.size()) return null;
        boolean multi = multiTurretOn();
        EquipSum c = (EquipSum) st.eqC;
        if (c != null && c.ver == stVer && c.multi == multi) return c;
        Rc rc = new Rc();
        HashMap<String, Integer> counts = new HashMap<>();
        ArrayList<Def> eqList = new ArrayList<>();
        HashMap<String, Boolean> seen = new HashMap<>();
        boolean hasEq = false;
        Defs defs = defsCache;
        for (int i = 0; i < st.d.size(); i++) {
            if (st.p.get(i) != EQUIPPED) continue;
            String id = st.d.get(i);
            counts.put(id, cnt(counts, id) + 1);
            String r = TURRET_ROLES.get(id);
            if (ROLE_ATTACK.equals(r)) rc.atk++; else if (ROLE_BRAWLER.equals(r)) rc.brl++; else if (ROLE_DEFENSE.equals(r)) rc.def++;
            if (defs != null) {
                Def def = defs.map.get(id);
                if (def != null && def.equip && def.mount) {
                    hasEq = true;
                    if (!((!multi || id.equals(SMITE_ID) || id.equals(LUSTRE_ID)) && seen.get(id) != null)) { seen.put(id, true); eqList.add(def); }
                }
            }
        }
        c = new EquipSum();
        c.ver = stVer; c.multi = multi; c.defsReady = defs != null; c.rc = rc; c.counts = counts;
        c.malign = cnt(counts, MALIGN_ID) > 0; c.nLu = cnt(counts, LUSTRE_ID); c.nSm = cnt(counts, SMITE_ID);
        c.lightSpd = cnt(counts, "duo") > 0 || cnt(counts, "breach") > 0; c.hasTherdum = cnt(counts, THERDUM_ID) > 0;
        c.eqList = eqList; c.hasEq = hasEq;
        st.eqC = c;
        return c;
    }
    static boolean malignEquipped(St st) { EquipSum c = equipSummary(st); return c != null && c.malign; }
    static int equippedCount(St st, String id) { EquipSum c = equipSummary(st); return c != null ? cnt(c.counts, id) : 0; }
    static Rc roleCounts(St st) { EquipSum c = equipSummary(st); return c != null ? c.rc : EMPTY_RC; }

    // ===== aim / mount (JS 1348-1542) =====
    static final class Aim { boolean shoot; float x, y; }
    static Aim aimState(Unit u, Player pl) {
        Aim a = new Aim();
        try { a.x = u.aimX(); a.y = u.aimY(); a.shoot = u.isShooting(); } catch (Throwable err) {}
        try { if (pl != null && pl.shooting) { a.shoot = true; a.x = u.aimX(); a.y = u.aimY(); } } catch (Throwable err) {}
        try {
            mindustry.entities.units.WeaponMount[] m = u.mounts();
            if (m != null) for (mindustry.entities.units.WeaponMount w : m) if (w.shoot) { a.shoot = true; a.x = w.aimX; a.y = w.aimY; break; }
        } catch (Throwable err) {}
        return a;
    }
    static final ArrayList<Def> EMPTY_DEFS = new ArrayList<>();
    static ArrayList<Def> equipList(St st, Defs defs) {
        if (st == null || st.p.size() != st.d.size()) return EMPTY_DEFS;
        EquipSum c = equipSummary(st);
        if (c != null && c.defsReady) return c.eqList;
        ArrayList<Def> out = new ArrayList<>();
        boolean multi = multiTurretOn();
        HashMap<String, Boolean> seen = new HashMap<>();
        for (int i = 0; i < st.d.size(); i++) {
            if (st.p.get(i) != EQUIPPED) continue;
            Def def = defs.map.get(st.d.get(i));
            if (def == null || !def.equip || !def.mount) continue;
            if ((!multi || def.id.equals(SMITE_ID) || def.id.equals(LUSTRE_ID)) && seen.get(def.id) != null) continue;
            seen.put(def.id, true);
            out.add(def);
        }
        return out;
    }
    static Aim hostAim(Unit h, Player pl) { return aimState(h, pl); }
    static final HashMap<Integer, ArrayList<float[]>> weaponCache = new HashMap<>();
    static ArrayList<float[]> weaponMounts(UnitType type) {
        ArrayList<float[]> c = weaponCache.get((int) type.id);
        if (c == null) {
            c = new ArrayList<>();
            try {
                for (int i = 0; i < type.weapons.size; i++) {
                    mindustry.type.Weapon w = type.weapons.get(i);
                    if (w == null || w.bullet == null) continue;
                    if (!w.controllable) continue;
                    c.add(new float[]{w.x, w.y});
                }
            } catch (Throwable err) {}
            weaponCache.put((int) type.id, c);
        }
        return c;
    }
    static final class MpOut { float x, y; boolean w; }
    static final MpOut mpOut = new MpOut();
    static MpOut mountPos(Unit u, int k, int n) {
        try {
            ArrayList<float[]> wm = weaponMounts(u.type);
            if (wm.size() > 0) {
                float[] m = wm.get(k % wm.size());
                float rot = u.rotation - 90;
                mpOut.x = u.x + Angles.trnsx(rot, m[0], m[1]);
                mpOut.y = u.y + Angles.trnsy(rot, m[0], m[1]);
                mpOut.w = true;
                return mpOut;
            }
        } catch (Throwable err) {}
        mpOut.w = false;
        if (n <= 1) { mpOut.x = u.x; mpOut.y = u.y; return mpOut; }
        int inner = Math.min(n, 6), ring = k < inner ? 0 : 1, cnt = ring == 0 ? inner : n - inner, idx = ring == 0 ? k : k - inner;
        float a = u.rotation + ((float) idx / cnt) * 360 + ring * (180f / cnt);
        float r = u.hitSize * (ring == 0 ? 0.28f : 0.5f);
        if (n <= 6) r = u.hitSize * 0.3f;
        mpOut.x = u.x + Mathf.cosDeg(a) * r;
        mpOut.y = u.y + Mathf.sinDeg(a) * r;
        return mpOut;
    }
    static float mountScale(Unit u, Def def, int n) {
        float base = def.block.size * Vars.tilesize;
        float f = n <= 1 ? 0.6f : (n <= 6 ? 0.38f : 0.28f);
        return Math.max(0.15f, Math.min(1f, u.hitSize * f / base));
    }
    static final int ESCALATE_PCT = 100;
    static int turretCostAt(Def def, int owned) {
        if (!"malign".equals(def.id)) return def.cost;
        return (int) Math.ceil(def.cost * Math.pow(1 + ESCALATE_PCT / 100.0, Math.min(owned, 30)));
    }
    static int ownedCount(St st, String id) {
        int c = 0;
        if (st == null) return 0;
        for (String s : st.d) if (s.equals(id)) c++;
        return c;
    }
    static int turretCost(St st, Def def) { return turretCostAt(def, ownedCount(st, def.id)); }
    static int turretSellValue(St st, Def def) { return turretCostAt(def, Math.max(0, ownedCount(st, def.id) - 1)); }
    static float turretRange(Def def) { float r = fldF(def.block, "range", 0f); if (!(r > 0)) r = 160f; return r; }
    static float ammoRange(Def def, BulletType bt) {
        float r = turretRange(def);
        if (bt != null) r += fldF(bt, "rangeChange", 0f);
        return r;
    }


    // ===== PHAN 3: nham / ban phao gan tren unit (JS 1544-2139) =====
    /** Hook cho Therdum.java (JS global.NexTherdum {fx, bullet}); null -> dung fireTurret thuong nhu JS khi thieu global. */
    public static Effect therdumFx;
    public static BulletType therdumBullet;

    static final class MState {
        float rel, ang, tt, life, charge, bLife; int q, barrel, k, n = 1;
        Tgt tgt; Bullet ptgt; Bullet b;
    }
    static final HashMap<String, MState> mountSt = new HashMap<>();
    static final HashMap<String, Float> equipAng = new HashMap<>();
    static final HashMap<String, float[]> equipBeam = new HashMap<>();
    static MState mstate(String key, Unit u) {
        MState s = mountSt.get(key);
        if (s == null) { s = new MState(); try { s.ang = u.rotation; } catch (Throwable err) {} mountSt.put(key, s); }
        return s;
    }
    /** Muc tieu: don vi / cong trinh (e) hoac o dang chay (ft, JS object {x,y,ft,tx,ty}). */
    static final class Tgt {
        Teamc e; boolean ft; int tx, ty; float fx, fy;
        float x() { return ft ? fx : e.getX(); }
        float y() { return ft ? fy : e.getY(); }
        Vec2 vel() { return e instanceof Unit ? ((Unit) e).vel : null; }
        float hitSize() { return e instanceof Unit ? ((Unit) e).hitSize : 0f; }
    }
    static boolean entValid(Teamc e) {
        if (e instanceof Unit) return ((Unit) e).isValid();
        if (e instanceof Building) return ((Building) e).isValid();
        return false;
    }

    static Tgt findFire(Unit u, Def def, MpOut mp, float range) {
        Block blk = def.block;
        if (!(blk instanceof LiquidTurret) || !fldB(blk, "extinguish", true)) return null;
        BulletType bt = equipBullet(def);
        try {
            Liquid lq = ((LiquidBulletType) bt).liquid;
            if (lq == null || !lq.canExtinguish()) return null;
        } catch (Throwable err) { return null; }
        int ts = Vars.tilesize, r = (int) Math.floor(range / ts), cx = Math.round(mp.x / ts), cy = Math.round(mp.y / ts);
        Tgt best = null; float bd = 0;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) {
            Tile tile = Vars.world.tile(cx + dx, cy + dy);
            if (tile == null) continue;
            if (!Fires.has(tile.x, tile.y)) continue;
            float d2 = Mathf.dst2(mp.x, mp.y, tile.worldx(), tile.worldy());
            if (d2 > range * range) continue;
            if (tile.build != null && tile.team() != u.team) continue;
            if (best == null || d2 < bd) {
                bd = d2;
                best = new Tgt(); best.ft = true; best.fx = tile.worldx(); best.fy = tile.worldy(); best.tx = tile.x; best.ty = tile.y;
            }
        }
        return best;
    }

    static final class Ft { float mx, my, range, minR, bc = 1e18f; Team team; boolean ta = true, tg = true; Units.Sortf sorter; Unit best; }
    static final Ft ftCtx = new Ft();
    static void ftCheck(Unit e) {
        Ft ctx = ftCtx;
        if (e == null || e.team == ctx.team || e.team == Team.derelict || e.dead || !e.isValid()) return;
        if (e.isFlying() ? !ctx.ta : !ctx.tg) return;
        try { if (!e.targetable(ctx.team)) return; } catch (Throwable err0) {}
        float d = Mathf.dst(ctx.mx, ctx.my, e.x, e.y);
        if (d - e.hitSize / 2 > ctx.range || d < ctx.minR) return;
        float c = d;
        if (ctx.sorter != null) { try { c = ctx.sorter.cost(e, ctx.mx, ctx.my); } catch (Throwable err1) { c = d; } }
        if (c < ctx.bc) { ctx.bc = c; ctx.best = e; }
    }
    static Teamc findTarget(Unit u, Def def, MpOut mp, float range) {
        Block blk = def.block;
        Ft ctx = ftCtx;
        ctx.mx = mp.x; ctx.my = mp.y; ctx.team = u.team;
        ctx.ta = fldB(blk, "targetAir", true); ctx.tg = fldB(blk, "targetGround", true);
        ctx.range = range; ctx.minR = fldF(blk, "minRange", 0f);
        Object so = fld(blk, "unitSort", null);
        ctx.sorter = so instanceof Units.Sortf ? (Units.Sortf) so : null;
        ctx.best = null; ctx.bc = 1e18f;
        try {
            float rr0 = range + 64;
            try { Units.nearbyEnemies(ctx.team, ctx.mx - rr0, ctx.my - rr0, rr0 * 2, rr0 * 2, e -> ftCheck(e)); }
            catch (Throwable err9) { Groups.unit.each(e -> ftCheck(e)); }
        } catch (Throwable err) { logOnce("tgt1", "findTarget (unit): " + err, false); }
        if (ctx.best != null) return ctx.best;
        if (ctx.tg) {
            try { return Units.closestTarget(ctx.team, ctx.mx, ctx.my, range, e -> false, b -> true); }
            catch (Throwable err) { logOnce("tgt2", "findTarget (building): " + err, false); }
        }
        return null;
    }
    static Tgt wrap(Teamc e) { if (e == null) return null; Tgt t = new Tgt(); t.e = e; return t; }

    static boolean targetValid(Tgt t, Unit u, Def def, MpOut mp, float range) {
        if (t == null) return false;
        try {
            if (t.ft) return Fires.has(t.tx, t.ty) && Mathf.dst(mp.x, mp.y, t.x(), t.y()) <= range;
            Teamc e = t.e;
            if (e.team() == u.team || e.team() == Team.derelict || !entValid(e)) return false;
            if (e instanceof Unit && ((Unit) e).dead) return false;
            Block blk = def.block;
            boolean ta = fldB(blk, "targetAir", true), tg = fldB(blk, "targetGround", true);
            float d = Mathf.dst(mp.x, mp.y, t.x(), t.y());
            if (e instanceof Unit) {
                Unit tu = (Unit) e;
                if (tu.isFlying() ? !ta : !tg) return false;
                return d - tu.hitSize / 2 <= range && d >= fldF(blk, "minRange", 0f);
            }
            if (!tg) return false;
            return d - ((Building) e).block.size * Vars.tilesize / 2f <= range;
        } catch (Throwable err) { return false; }
    }
    static float[] leadPos(MpOut mp, Tgt t, BulletType bt) {
        float tx = t.x(), ty = t.y();
        try {
            Vec2 vel = t.vel();
            if (bt.speed > 0.01f && vel != null) {
                Vec2 v = Predict.intercept(mp.x, mp.y, t.x(), t.y(), vel.x, vel.y, bt.speed);
                if (v != null && !Float.isNaN(v.x) && !Float.isNaN(v.y)) { tx = v.x; ty = v.y; }
            }
        } catch (Throwable err) {}
        return new float[]{tx, ty};
    }
    static final HashMap<String, Boolean> FORCE_AUTO_AIM = new HashMap<>();
    static { FORCE_AUTO_AIM.put("lustre", true); }
    static final class AimRes { float tx, ty; Tgt t; }
    static AimRes resolveAim(Unit u, Def def, MpOut mp, Aim ast, MState stt, BulletType bt, float range) {
        AimRes r = new AimRes();
        if (ast.shoot && FORCE_AUTO_AIM.get(def.id) == null) { r.tx = ast.x; r.ty = ast.y; return r; }
        Block blk = def.block;
        if (!targetValid(stt.tgt, u, def, mp, range)) stt.tgt = null;
        stt.tt -= Time.delta;
        if (stt.tt <= 0) {
            float iv = fldF(blk, "targetInterval", 20f);
            if (stt.tgt == null) { float niv = fldF(blk, "newTargetInterval", -1f); if (niv > 0) iv = niv; }
            stt.tt = iv;
            Tgt nt = null;
            try { if (Groups.fire.size() > 0) nt = findFire(u, def, mp, range); } catch (Throwable err) {}
            if (nt == null) nt = wrap(findTarget(u, def, mp, range));
            stt.tgt = nt;
        }
        Tgt t = stt.tgt;
        if (t == null) return null;
        float tx = t.x(), ty = t.y();
        if (bt != null && fldB(blk, "predictTarget", true)) { float[] lp = leadPos(mp, t, bt); tx = lp[0]; ty = lp[1]; }
        r.tx = tx; r.ty = ty; r.t = t;
        return r;
    }
    static float[] muzzle(Unit u, Block blk, MpOut mp, float rot) {
        float off = mp.w ? 0f : Math.min(blk.size * Vars.tilesize * 0.5f, u.hitSize * 0.5f);
        return new float[]{mp.x + Mathf.cosDeg(rot) * off, mp.y + Mathf.sinDeg(rot) * off};
    }

    static void fireTurret(Unit u, Def def, BulletType bt, MState stt, float tx, float ty, Cons<Bullet> onBullet) {
        Block blk = def.block;
        Object pat = fld(blk, "shoot", null);
        float inacc = fldF(blk, "inaccuracy", 0f) + fldF(bt, "inaccuracy", 0f);
        float vrnd = fldF(blk, "velocityRnd", 0f), minR = fldF(blk, "minRange", 0f), rng = ammoRange(def, bt);
        ensureLoaded(bt);

        boolean delayed = fldF(pat, "firstShotDelay", 0f) > 0;
        if (!Vars.headless && delayed) {
            try {
                MpOut mp0 = mountPos(u, stt.k, stt.n);
                Object cs = fld(blk, "chargeSound", null);
                if (cs instanceof arc.audio.Sound) ((arc.audio.Sound) cs).at(mp0.x, mp0.y, 1f);
                Object ce = fld(bt, "chargeEffect", null);
                if (ce instanceof Effect) ((Effect) ce).at(mp0.x, mp0.y, stt.ang);
            } catch (Throwable err) {}
        }
        // handler.shoot(xo, yo, ao, delay, mover): sinh dan (JS spawn); mover khong can ten kieu.
        ShootPattern.BulletHandler handler = (xo, yo, ao, delay, mover) -> {
            stt.q++;
            Runnable spawn = () -> {
                stt.q = Math.max(0, stt.q - 1);
                try {
                    if (!u.isValid() || u.dead) return;
                    MpOut mp = mountPos(u, stt.k, stt.n);
                    float rot = stt.ang;
                    float[] mz = muzzle(u, blk, mp, rot);
                    float bx = mz[0] + Angles.trnsx(rot - 90, xo, yo), by = mz[1] + Angles.trnsy(rot - 90, xo, yo);
                    float a = rot + ao + (float) (Math.random() * 2 - 1) * inacc;
                    float ls = 1;
                    try {
                        if (fldB(bt, "scaleLife", false)) {
                            float r0 = fldF(bt, "range", -1f);
                            float br = r0 > 0 ? r0 : bt.speed * bt.lifetime;
                            if (br > 0) ls = Math.max(minR / br, Math.min(rng / br, Mathf.dst(bx, by, tx, ty) / br));
                        }
                    } catch (Throwable err) {}
                    float vs = (1 - vrnd) + (float) Math.random() * vrnd;
                    Bullet blt = null;
                    try { blt = bt.create(u, u.team, bx, by, a, -1f, vs, ls, null, mover, tx, ty); }
                    catch (Throwable err) { logOnce("create:" + def.id, "bullet create failed for " + def.id + ": " + err, false); }
                    if (blt != null && onBullet != null) onBullet.get(blt);
                    if (!Vars.headless) {
                        try {
                            Object se = fld(blk, "shootEffect", null);
                            if (se == null) se = bt.shootEffect;
                            ((Effect) se).at(bx, by, rot + ao, bt.hitColor);
                        } catch (Throwable err) {}
                        try { bt.smokeEffect.at(bx, by, rot + ao, bt.hitColor); } catch (Throwable err) {}
                        try {
                            float p0 = fldF(blk, "soundPitchMin", 0.9f), p1 = fldF(blk, "soundPitchMax", 1.1f);
                            Object ss = fld(blk, "shootSound", null);
                            if (ss instanceof arc.audio.Sound) ((arc.audio.Sound) ss).at(bx, by, p0 + (float) Math.random() * (p1 - p0));
                        } catch (Throwable err) {}
                    }
                } catch (Throwable err) { logOnce("spawn:" + def.id, "shot failed for " + def.id + ": " + err, false); }
            };
            if (delay > 0) Time.run(delay, spawn); else spawn.run();
        };
        boolean done = false;
        if (pat instanceof ShootPattern) {
            try { ((ShootPattern) pat).shoot(stt.barrel, handler, () -> stt.barrel++); done = true; }
            catch (Throwable err4) { logOnce("pat:" + def.id, "shoot pattern failed for " + def.id + ": " + err4, false); }
        }
        if (!done) {
            int shots = 1; float spread = 0;
            try {
                shots = Math.min(12, Math.max(1, (int) fldF(pat, "shots", 1f)));
                if (className(pat).equals("ShootSpread")) spread = fldF(pat, "spread", 0f);
            } catch (Throwable err) {}
            for (int s = 0; s < shots; s++) handler.shoot(0f, 0f, (s - (shots - 1) / 2f) * spread, 0f, null);
        }
        logOnce("fired:" + def.id, "equip turret fired: " + def.id, true);
    }

    static void therdumFire(Unit u, Def def, MState stt, Aim aim) {
        MpOut mp = mountPos(u, stt.k, stt.n);
        float rot = stt.ang;
        float[] mz = muzzle(u, def.block, mp, rot);
        float sx = mz[0] + Angles.trnsx(rot, 6f), sy = mz[1] + Angles.trnsy(rot, 6f);
        float range = turretRange(def), dm = Math.max(0.1f, u.damageMultiplier);
        if (!Vars.headless) { try { therdumFx.at(sx, sy, rot); } catch (Throwable err) {} }
        for (int i = 0; i < 40; i++) {
            float sp = Mathf.random(3f, 9f);
            Bullet b = therdumBullet.create(u, u.team, sx, sy, rot + Mathf.range(12f));
            if (b != null) { b.vel.setLength(sp); b.lifetime = range / sp; b.damage = 24 * dm; }
        }
    }
    static void shootTurret(Unit u, Def def, MpOut mp, Aim ast, MState stt, String key) {
        Block blk = def.block;
        BulletType bt = equipBullet(def);
        if (bt == null) { logOnce("nobt:" + def.id, "no bullet type found for equipped turret " + def.id, false); return; }
        float reload = Math.max(1f, fldF(blk, "reload", 20f));
        stt.rel = Math.min(reload, stt.rel + Time.delta * Math.max(0.1f, u.reloadMultiplier) * fldF(bt, "reloadMultiplier", 1f));
        float range = ammoRange(def, bt);
        AimRes aim = resolveAim(u, def, mp, ast, stt, bt, range);
        if (aim == null) { equipAng.put(key, stt.ang); return; }
        float want = Angles.angle(mp.x, mp.y, aim.tx, aim.ty);
        stt.ang = Angles.moveToward(stt.ang, want, fldF(blk, "rotateSpeed", 5f) * Time.delta);
        equipAng.put(key, stt.ang);
        if (stt.q > 0 && fldF(fld(blk, "shoot", null), "firstShotDelay", 0f) > 0) return;
        if (Angles.angleDist(stt.ang, want) < fldF(blk, "shootCone", 8f) && stt.rel >= reload) {
            if (def.id.equals(THERDUM_ID) && therdumBullet != null) therdumFire(u, def, stt, ast);
            else fireTurret(u, def, bt, stt, aim.tx, aim.ty, null);
            stt.rel = stt.rel % reload;
        }
    }
    static void contTurret(Unit u, Def def, MpOut mp, Aim ast, MState stt, String key, boolean laser) {
        Block blk = def.block;
        BulletType bt = equipBullet(def);
        if (bt == null) { logOnce("nobt:" + def.id, "no bullet type found for equipped turret " + def.id, false); return; }
        float reload = Math.max(1f, fldF(blk, "reload", 20f)), range = ammoRange(def, bt), eff = Math.max(0.1f, u.reloadMultiplier);
        Bullet b = stt.b; boolean alive = false;
        if (b != null) {
            try { alive = b.isAdded() && b.type != null; } catch (Throwable err) { alive = false; }
            if (!alive) { stt.b = null; if (laser) stt.rel = reload; }
        }
        if (laser && !alive && stt.rel > 0) stt.rel -= Time.delta * eff;
        AimRes aim = resolveAim(u, def, mp, ast, stt, bt, range);
        float want = stt.ang, rs = fldF(blk, "rotateSpeed", 5f) * Time.delta;
        if (laser && alive) rs *= fldF(blk, "firingMoveFract", 0.25f);
        if (aim != null) { want = Angles.angle(mp.x, mp.y, aim.tx, aim.ty); stt.ang = Angles.moveToward(stt.ang, want, rs); }
        equipAng.put(key, stt.ang);
        if (alive) {
            boolean keep = aim != null;
            if (laser) { stt.life -= Time.delta; keep = stt.life > 0; }
            float[] mz = muzzle(u, blk, mp, stt.ang);
            try {
                b.rotation(stt.ang);
                b.set(mz[0], mz[1]);
                if (aim != null) { b.aimX = aim.tx; b.aimY = aim.ty; }
                if (keep) {
                    float base = stt.bLife > 0 ? stt.bLife : fldF(bt, "lifetime", 100f);
                    b.time = base * fldF(b.type, "optimalLifeFract", 0.5f);
                }
            } catch (Throwable err) {}
            if (!keep) { try { b.remove(); } catch (Throwable err) {} stt.b = null; stt.bLife = 0; if (laser) stt.rel = reload; }
            return;
        }
        if (aim == null) return;
        if (laser && stt.rel > 0) return;
        if (stt.q > 0) return;
        if (Angles.angleDist(stt.ang, want) >= fldF(blk, "shootCone", 8f)) return;
        fireTurret(u, def, bt, stt, aim.tx, aim.ty, blt -> {
            stt.b = blt;
            stt.life = fldF(blk, "shootDuration", 100f);
            try { stt.bLife = blt.lifetime; } catch (Throwable err) { stt.bLife = fldF(bt, "lifetime", 100f); }
        });
    }
    static void tractorTurret(Unit u, Def def, MpOut mp, MState stt, String key) {
        Block blk = def.block;
        float range = turretRange(def), eff = Math.max(0.1f, u.reloadMultiplier);
        if (!targetValid(stt.tgt, u, def, mp, range)) stt.tgt = null;
        stt.tt -= Time.delta;
        if (stt.tt <= 0) { stt.tt = fldF(blk, "retargetTime", 5f); stt.tgt = wrap(findTarget(u, def, mp, range)); }
        Tgt tg = stt.tgt;
        if (tg == null || !(tg.e instanceof Unit)) { equipBeam.remove(key); return; }
        Unit t = (Unit) tg.e;
        float dest = Angles.angle(mp.x, mp.y, t.x, t.y);
        stt.ang = Angles.moveToward(stt.ang, dest, fldF(blk, "rotateSpeed", 10f) * Time.delta * eff);
        equipAng.put(key, stt.ang);
        equipBeam.put(key, new float[]{t.x, t.y});
        if (Angles.angleDist(stt.ang, dest) >= fldF(blk, "shootCone", 6f)) return;
        float dmg = fldF(blk, "damage", 0f);
        if (dmg > 0) { try { t.damageContinuous(dmg * eff); } catch (Throwable err) {} }
        try {
            Object st2 = fld(blk, "status", null);
            if (st2 instanceof StatusEffect && st2 != StatusEffects.none) t.apply((StatusEffect) st2, fldF(blk, "statusDuration", 300f));
        } catch (Throwable err) {}
        try {
            float dx = mp.x - t.x, dy = mp.y - t.y, dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 0.01f) {
                float f = (fldF(blk, "force", 12f) + (1 - dist / range) * fldF(blk, "scaledForce", 6f)) * Time.delta * eff;
                float mag = Math.min(dist, f);
                t.impulse(dx / dist * mag, dy / dist * mag);
            }
        } catch (Throwable err) {}
    }
    static final class Pd { float mx, my, range, bd = 1e18f; Team team; Bullet best; }
    static final Pd pdCtx = new Pd();
    static void pdCheck(Bullet bl) {
        Pd ctx = pdCtx;
        if (bl == null || bl.team == ctx.team || !bl.isAdded()) return;
        try { if (!bl.type.hittable) return; } catch (Throwable err0) { return; }
        float d2 = Mathf.dst2(ctx.mx, ctx.my, bl.x, bl.y), lim = ctx.range + bl.hitSize / 2;
        if (d2 > lim * lim) return;
        if (d2 < ctx.bd) { ctx.bd = d2; ctx.best = bl; }
    }
    static void pointTurret(Unit u, Def def, MpOut mp, MState stt, String key) {
        Block blk = def.block;
        float range = turretRange(def), reload = Math.max(1f, fldF(blk, "reload", 8f)), eff = Math.max(0.1f, u.reloadMultiplier);
        Team team = u.team;
        Bullet t = stt.ptgt;
        if (t != null) {
            try { if (!t.isAdded() || t.team == team || !t.type.hittable || Mathf.dst(mp.x, mp.y, t.x, t.y) > range + t.hitSize / 2) t = null; } catch (Throwable err) { t = null; }
            stt.ptgt = t;
        }
        stt.tt -= Time.delta;
        if (stt.tt <= 0) {
            stt.tt = fldF(blk, "retargetTime", 5f);
            Pd ctx = pdCtx;
            ctx.mx = mp.x; ctx.my = mp.y; ctx.team = team; ctx.range = range; ctx.best = null; ctx.bd = 1e18f;
            try { Groups.bullet.intersect(mp.x - range - 16, mp.y - range - 16, (range + 16) * 2, (range + 16) * 2, (Cons<Bullet>) b -> pdCheck(b)); }
            catch (Throwable err8) { try { Groups.bullet.each((Cons<Bullet>) b -> pdCheck(b)); } catch (Throwable e2) {} }
            stt.ptgt = ctx.best; t = ctx.best;
        }
        if (t == null) { equipAng.put(key, stt.ang); return; }
        float dest = Angles.angle(mp.x, mp.y, t.x, t.y);
        stt.ang = Angles.moveToward(stt.ang, dest, fldF(blk, "rotateSpeed", 20f) * Time.delta);
        equipAng.put(key, stt.ang);
        stt.rel += Time.delta * eff;
        if (Angles.angleDist(stt.ang, dest) < fldF(blk, "shootCone", 8f) && stt.rel >= reload) {
            float bdmg = fldF(blk, "bulletDamage", 30f);
            try { if (t.damage > bdmg) t.damage = t.damage - bdmg; else t.remove(); }
            catch (Throwable err) { try { t.remove(); } catch (Throwable err2) {} }
            if (!Vars.headless) {
                try {
                    float sl = fldF(blk, "shootLength", 5f), sx = mp.x + Angles.trnsx(stt.ang, sl), sy = mp.y + Angles.trnsy(stt.ang, sl);
                    Object co = fld(blk, "color", Color.white);
                    Color col = co instanceof Color ? (Color) co : Color.white;
                    Object be = fld(blk, "beamEffect", null), se = fld(blk, "shootEffect", null), he = fld(blk, "hitEffect", null), ss = fld(blk, "shootSound", null);
                    try { ((Effect) be).at(sx, sy, stt.ang, col, new Vec2(t.x, t.y)); } catch (Throwable err) {}
                    try { ((Effect) se).at(sx, sy, stt.ang, col); } catch (Throwable err) {}
                    try { ((Effect) he).at(t.x, t.y, col); } catch (Throwable err) {}
                    try { ((arc.audio.Sound) ss).at(sx, sy, 0.9f + (float) Math.random() * 0.2f); } catch (Throwable err) {}
                } catch (Throwable err) {}
            }
            stt.rel = 0;
        }
    }
    static void mountFire(Player pl, ArrayList<Def> list, Unit u, String pfx) {
        int n = list.size();
        if (n == 0) return;
        Aim ast = null;
        for (int k = 0; k < n; k++) {
            Def def = list.get(k);
            String key = pl.id + ":" + pfx + k + ":" + def.id;
            MpOut mp = mountPos(u, k, n);
            MState stt = mstate(key, u);
            stt.k = k; stt.n = n;
            if (ast == null) ast = hostAim(u, pl);
            try {
                if (def.kind.equals("tractor")) tractorTurret(u, def, mp, stt, key);
                else if (def.kind.equals("point")) pointTurret(u, def, mp, stt, key);
                else if (def.kind.equals("laser")) contTurret(u, def, mp, ast, stt, key, true);
                else if (def.kind.equals("cont")) contTurret(u, def, mp, ast, stt, key, false);
                else shootTurret(u, def, mp, ast, stt, key);
            } catch (Throwable err) { logOnce("mf:" + def.id, "mount update failed for " + def.id + ": " + err, false); }
        }
    }

    // ===== suc manh (JS 2096-2139 + hpPower 1487) =====
    /** JS: isCoreHost luon sai -> chu so huu = chinh unit. */
    static Unit ownerOf(Player pl, Unit u) { return u; }
    static final HashMap<Integer, Float> baseAtkCache = new HashMap<>();
    static float baseAttack(UnitType type) {
        Float cached = baseAtkCache.get((int) type.id);
        if (cached != null) return cached;
        float best = 0;
        try {
            for (int i = 0; i < type.weapons.size; i++) {
                BulletType bt = type.weapons.get(i).bullet;
                if (bt == null) continue;
                float d = Math.max(bt.damage, bt.splashDamage);
                if (d > best) best = d;
            }
        } catch (Throwable err) {}
        float res = best > 0 ? best : 20f;
        baseAtkCache.put((int) type.id, res);
        return res;
    }
    static float attackPower(Player pl, Unit u) {
        St st = byId.get(pl.id);
        float flat = st != null ? st.s[2] * ATK_FLAT : 0f;
        return (baseAttack(u.type) + flat) * Math.max(0.1f, u.damageMultiplier);
    }
    static float armorPower(Player pl, Unit u) { return Math.max(0f, u.armor); }
    static float hpPower(Player pl, Unit u) { return Math.max(1f, u.maxHealth); }


    // ===== PHAN 4: ve phi cong, khien loi, chet loi (JS 2140-2788) =====
    // JS isCoreHost chua tung true -> drawMounts chi ve tia keo (tractor); phan ve phao/ban-tay bi `continue` truoc do.
    static void knockUnit(Player pl, Unit e, float fx, float fy) {
        St st = byId.get(pl.id);
        if (st == null || !(st.s[11] > 0)) return;
        try {
            float dx = e.x - fx, dy = e.y - fy, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.01f) return;
            float f = st.s[11] * KNOCK_PER * 80;
            e.impulse(dx / d * f, dy / d * f);
        } catch (Throwable err) {}
    }
    static final HashMap<String, BulletType> knockCache = new HashMap<>();
    static BulletType knockType(BulletType t, int lv) {
        String key = t.id + ":" + lv + (t.pierceArmor ? "p" : "");
        if (knockCache.containsKey(key)) return knockCache.get(key);
        BulletType c;
        try { c = t.copy(); c.knockback = t.knockback + lv * KNOCK_PER; ensureLoaded(c); } catch (Throwable err) { c = null; }
        knockCache.put(key, c);
        return c;
    }
    static void eachEquip(Player pl) {
        if (!pvpOn(pl)) return;
        St st = byId.get(pl.id);
        if (st == null || st.d.size() == 0 || st.p.size() != st.d.size()) return;
        Defs defs = droneDefs();
        Unit u = pl.unit();
        if (u != null && u.isValid() && !u.dead) mountFire(pl, equipList(st, defs), u, "u");
    }
    static void eachDrawEquip(Player pl) {
        if (!pvpOn(pl)) return;
        St st = byId.get(pl.id);
        if (st == null || st.d.size() == 0 || st.p.size() != st.d.size()) return;
        Unit u = pl.unit();
        if (u != null && u.isValid()) drawMounts(pl, u, equipList(st, droneDefs()), "u");
    }
    static void drawMounts(Player pl, Unit u, ArrayList<Def> list, String pfx) {
        int n = list.size();
        if (n == 0) return;
        float scl = Draw.scl;
        for (int k = 0; k < n; k++) {
            Def def = list.get(k);
            Block blk = def.block;
            String key = pl.id + ":" + pfx + k + ":" + def.id;
            Draw.reset();
            MpOut mp = mountPos(u, k, n);
            if (def.kind.equals("tractor")) {
                float[] bp = equipBeam.get(key);
                if (bp != null) {
                    boolean ok = false;
                    try {
                        TractorBeamTurret tb = (TractorBeamTurret) blk;
                        if (tb.laser != null && tb.laserEnd != null && tb.laser.found() && tb.laserEnd.found()) {
                            Draw.z(Layer.flyingUnit + 1);
                            Draw.mixcol(tb.laserColor, 0.5f);
                            Drawf.laser(tb.laser, tb.laserEnd, mp.x, mp.y, bp[0], bp[1], tb.laserWidth);
                            Draw.mixcol();
                            ok = true;
                        }
                    } catch (Throwable err) { Draw.reset(); }
                    if (!ok) {
                        try {
                            Draw.z(Layer.flyingUnit + 1);
                            Draw.color(Pal.lancerLaser);
                            Lines.stroke(3 * scl);
                            Lines.line(mp.x, mp.y, bp[0], bp[1]);
                            Draw.reset();
                        } catch (Throwable err) {}
                    }
                }
            }
        }
    }

    // ===== bieu tuong phi cong =====
    static final int PILOT_FX_TICKS = 132;
    static final HashMap<Integer, Integer> pilotLastLv = new HashMap<>();
    static void drawPilotFor(Player pl, boolean ui) {
        St st = byId.get(pl.id);
        if (st == null) return;
        Integer prev = pilotLastLv.get(pl.id);
        pilotLastLv.put(pl.id, st.lv);
        if (st.pi == PILOT_XIKI && prev != null && st.lv > prev) pilotFxAt.put(pl.id, Time.time);
        if (!ui) return;
        Float at = pilotFxAt.get(pl.id);
        if (at == null || !(st.pi > 0)) return;
        float t = Time.time - at;
        if (t < 0 || t >= PILOT_FX_TICKS) { if (t >= PILOT_FX_TICKS) pilotFxAt.remove(pl.id); return; }
        Unit u = pl.unit();
        if (u == null || !u.isValid() || u.dead) return;
        TextureRegion reg = pilotIconRegion(st.pi);
        if (reg == null) return;
        float p = t / PILOT_FX_TICKS, h = (float) Math.sin(p * Math.PI);
        float k = (Core.graphics.getWidth() / Core.camera.width) / uiScale() * (Vars.mobile ? 3 : 1);
        float size = 36 * k * Math.min(1f, h * 3);
        Vec2 sv = expStagePos(u.x, u.y + u.hitSize / 2 + (6 + h * 42) * (Vars.mobile ? 3 : 1));
        if (sv == null) return;
        float cx = sv.x, cy = sv.y;
        Draw.reset(); Draw.color();
        Draw.rect(reg, cx, cy, size, size);
        if (st.pi == PILOT_EMPIX) {
            for (int i = 0; i < 7; i++) {
                float delay = i * 0.07f, q = (p - delay) / 0.6f;
                if (q <= 0 || q >= 1) continue;
                float ox = ((i * 37 % 11) - 5) * 3.2f * k;
                float px = cx + ox + (float) Math.sin(q * 6 + i) * 3 * k, py = cy + (8 + q * 46) * k;
                float a = 1 - q * q, r = (3 + (i % 3)) * k;
                Draw.color(COL_EMPIX_PLUS, a);
                Lines.stroke(2.2f * k);
                Lines.line(px - r, py, px + r, py);
                Lines.line(px, py - r, px, py + r);
            }
            Draw.reset();
            return;
        }
        if (st.pi == PILOT_XAKYNEO) { Draw.reset(); return; }
        boolean left = p < 0.5f;
        float sp = left ? p * 2 : (p - 0.5f) * 2, fade = (float) Math.sin(sp * Math.PI);
        if (fade > 0.02f) {
            float[] angs = left ? new float[]{160, 135, 110} : new float[]{20, 45, 70};
            Draw.color(Pal.accent, fade);
            Lines.stroke(3.6f * k);
            for (int i = 0; i < 3; i++) {
                float a = angs[i], r1 = (27 + sp * 9) * k, r2 = r1 + 15 * k;
                Lines.line(cx + Angles.trnsx(a, r1), cy + Angles.trnsy(a, r1), cx + Angles.trnsx(a, r2), cy + Angles.trnsy(a, r2));
            }
        }
        Draw.reset();
    }
    static void drawPilotUI() {
        Groups.player.each(pl -> drawPilotFor(pl, true));
        for (int bi = 0; bi < bots.size(); bi++) drawPilotFor(bots.get(bi).botPl, true);
    }
    static void drawTxt(String str, float x, float y, float scale, Color col) {
        try {
            arc.graphics.g2d.Font fo = Fonts.outline;
            float os = fo.getData().scaleX;
            fo.getData().setScale(scale);
            fo.setColor(col);
            fo.draw(str, x, y, 0, Align.center, false);
            fo.getData().setScale(os);
            fo.setColor(Color.white);
        } catch (Throwable err) { logOnce("drawtxt", "draw text failed: " + err, false); }
    }
    static String fmtCd(float ticks) {
        int sec = (int) Math.ceil(ticks / 60);
        return sec >= 60 ? (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60) : sec + "s";
    }
    static void drawPilotCdHud() {
        if (Vars.player == null) return;
        St st = localState();
        if (st == null || !(st.pi > 0)) return;
        PilotCd e = pilotCdFor(st.pi);
        if (e == null) return;
        TextureRegion reg = pilotIconRegion(st.pi);
        if (reg == null) return;
        Float at = e.at.get(Vars.player.id);
        float left = at != null ? Math.max(0f, e.cd - (Time.time - at)) : 0f;
        float frac = e.cd > 0 ? Math.max(0f, Math.min(1f, left / e.cd)) : 0f;
        float sz = (Vars.mobile ? 56 : 44) * uiScale(), margin = 16 * uiScale();
        float cx = screenW() - margin - sz / 2, cy = margin + sz / 2;
        Draw.color();
        Draw.alpha(frac > 0 ? 0.45f : 1f);
        Draw.rect(reg, cx, cy, sz, sz);
        if (frac > 0) {
            Draw.color(Color.black, 0.65f);
            float h = sz * frac;
            Fill.rect(cx, cy + sz / 2 - h / 2, sz, h);
        }
        Draw.color(frac > 0 ? Color.lightGray : Pal.accent, 1f);
        Lines.stroke(2 * uiScale());
        Lines.rect(cx - sz / 2, cy - sz / 2, sz, sz);
        Draw.reset();
        if (frac > 0) drawTxt(fmtCd(left), cx, cy + 6 * uiScale(), 0.45f * uiScale() * (Vars.mobile ? 1.2f : 1f), Color.white);
    }
    static Element pilotOverlay = null;
    static void drawVanhauFields() {
        java.util.Iterator<Map.Entry<Integer, VanhauField>> it = vanhauField.entrySet().iterator();
        while (it.hasNext()) {
            VanhauField f = it.next().getValue();
            Unit u = Groups.unit.getByID(f.uid);
            if (u == null || !u.isValid() || u.dead || (Vars.net.client() && Time.time - f.at > 180)) {
                if (Vars.net.client()) it.remove();
                continue;
            }
            f.fl = Math.max(0f, f.fl - Time.delta / 20);
            float ratio = Math.max(0f, Math.min(1f, f.hp / Math.max(1f, f.max)));
            float R = (f.r != 0 ? f.r : 60f) * (0.97f + 0.03f * (float) Math.sin(Time.time * 0.08));
            Draw.z(Layer.shields);
            Draw.color(SHIELD_GLOW, SHIELD_COLOR, 1 - ratio);
            Draw.alpha(0.1f + f.fl * 0.3f);
            Fill.poly(u.x, u.y, 6, R);
            Lines.stroke(1.5f + f.fl * 1.5f);
            Draw.alpha(0.85f);
            Lines.poly(u.x, u.y, 6, R);
            float bw = Math.max(28f, u.hitSize * 1.1f), bh = 3.5f, by = u.y + u.hitSize / 2 + 12;
            Draw.z(Layer.shields + 1);
            Draw.color(Color.black, 0.7f);
            Fill.rect(u.x, by, bw + 2, bh + 2);
            Draw.color(SHIELD_GLOW, SHIELD_COLOR, 1 - ratio);
            Fill.rect(u.x - bw / 2 + bw * ratio / 2, by, Math.max(0.01f, bw * ratio), bh);
            Draw.reset();
        }
    }
    static void drawPilotIcons() {
        try { drawVanhauFields(); } catch (Throwable err) { logOnce("vhfield", "vanhau field draw failed: " + err, false); }
        Groups.player.each(pl -> drawPilotFor(pl, false));
        for (int bi = 0; bi < bots.size(); bi++) drawPilotFor(bots.get(bi).botPl, false);
    }

    // ===== khien loi =====
    static float matchT = 0, shieldMinT = 0, shieldSyncT = 0;
    static int shieldMinCache = DEFAULT_SHIELD_MIN, shieldStamp = 0;
    static boolean shieldEnded = false;
    static final class ShieldRec {
        float hp = SHIELD_HP, base, pre, syncAt = -100; boolean broken, inv, prot; int hitN, seen; CoreBlock.CoreBuild b;
    }
    static final HashMap<Integer, ShieldRec> coreShield = new HashMap<>();
    static final ArrayList<ShieldRec> shieldRecs = new ArrayList<>();
    static final class ShieldView { float inv, at; final HashMap<Integer, ShieldRec> cores = new HashMap<>(); }
    static ShieldView shieldView = new ShieldView();
    static final HashMap<Integer, Float> shieldFlash = new HashMap<>();
    static final HashMap<Integer, Integer> shieldSeenHit = new HashMap<>();
    static final class CoreDie { CoreBlock.CoreBuild b; float t; }
    static final HashMap<Integer, CoreDie> coreDying = new HashMap<>();
    static final HashMap<Integer, Boolean> coreKilled = new HashMap<>();
    static final class CoreCine { float x, y, t, dur, fx; }
    static CoreCine coreCine = null;
    static final float CORE_DIE_TICKS_F = CORE_DIE_TICKS;

    static float invulTicksLeft() {
        if (Vars.net.client()) { float l = shieldView.inv - (Time.time - shieldView.at); return l > 0 ? l : 0; }
        if (shieldMinT <= 0) { shieldMinCache = shieldMinutes(); shieldMinT = 120; }
        if (shieldMinCache >= LIMIT_SHIELD_MIN) return SHIELD_FOREVER_TICKS;
        float left = shieldMinCache * 3600f - matchT;
        return left > 0 ? left : 0;
    }
    static String fmtTicks(float t) {
        if (t >= SHIELD_FOREVER_TICKS / 2) return "\u221e";
        int sec = (int) Math.ceil(t / 60), m = sec / 60, r = sec % 60;
        return m + ":" + (r < 10 ? "0" : "") + r;
    }
    static float shieldRadius(CoreBlock.CoreBuild cb) { return cb.block.size * 7 + 10; }
    static ShieldRec shieldRec(int id) { ShieldRec r = coreShield.get(id); if (r == null) { r = new ShieldRec(); coreShield.put(id, r); } return r; }
    /** JS global.NexReflect.isProtected -> Endyr.reflectProtected (bai 29/42), gan khi chuyen reflecounum. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static boolean shieldProtectedByReflect(Building cb) {
        try { return Endyr.reflectProtected != null && ((arc.func.Boolf) Endyr.reflectProtected).get(cb); } catch (Throwable err) {}
        return false;
    }
    /** JS: Call.tileDamage khong ton tai o v157 (bai 101) nen syncCore chua tung dong bo gi -> chi giu moc thoi gian. */
    static void syncCore(CoreBlock.CoreBuild cb, ShieldRec r) {
        if (!Vars.net.server()) return;
        if (Time.time - r.syncAt < 6 && Time.time >= r.syncAt) return;
        r.syncAt = Time.time;
    }
    static void sendShield(float left) {
        if (!Vars.net.server()) return;
        StringBuilder sb = new StringBuilder("{\"i\":").append(Math.round(left)).append(",\"c\":[");
        boolean first = true;
        for (Map.Entry<Integer, ShieldRec> e : coreShield.entrySet()) {
            ShieldRec r = e.getValue();
            if (!first) sb.append(','); first = false;
            sb.append('[').append(e.getKey()).append(',').append(Math.round(r.hp)).append(',').append(r.broken ? 1 : 0).append(',').append(r.hitN).append(']');
        }
        try { Call.clientPacketUnreliable(SHIELD_KIND, sb.append("]}").toString()); } catch (Throwable err) {}
    }
    static void onShieldSync(String content) {
        try {
            JsonValue o = new JsonReader().parse(content);
            ShieldView v = new ShieldView();
            v.inv = o.getFloat("i", 0f); v.at = Time.time;
            for (JsonValue a = o.get("c").child; a != null; a = a.next) {
                ShieldRec r = new ShieldRec();
                r.hp = a.get(1).asFloat(); r.broken = a.get(2).asInt() == 1; r.hitN = a.get(3).asInt();
                v.cores.put(a.get(0).asInt(), r);
            }
            shieldView = v;
        } catch (Throwable err) {}
    }
    /** JS NexNet.toastAll(text, sec) -> Java NexNet chua co (bai 111i). */
    static void toastAll(String text, float sec) {
        try { Call.infoToast(text, sec); } catch (Throwable err) {}
        try { if (!Vars.headless) Vars.ui.showInfoToast(text, sec); } catch (Throwable err) {}
    }
    static void shieldBreakFx(CoreBlock.CoreBuild cb) {
        try { Call.effect(Fx.shieldBreak, cb.x, cb.y, shieldRadius(cb), SHIELD_COLOR); } catch (Throwable err) {}
        try { Groups.player.each(pl -> { if (pl.team() == cb.team) toast(pl, tr("[scarlet]Khiên lõi đã vỡ!", "[scarlet]Core shield broken!")); }); } catch (Throwable err) {}
        shieldSyncT = 0;
    }
    static void shieldBefore() {
        shieldRecs.clear();
        if (Vars.net.client() || !isFull() || Vars.state.gameOver) return;
        matchT += Time.delta; shieldMinT -= Time.delta; shieldStamp++;
        float left = invulTicksLeft();
        boolean inv = left > 0;
        if (inv) shieldEnded = false;
        else if (!shieldEnded) {
            shieldEnded = true;
            if (shieldMinCache > 0) toastAll(tr("[accent]Khiên bất tử của lõi đã hết! Chuyển sang khiên hấp thụ " + SHIELD_HP + " sát thương.",
                "[accent]Core invincible shield ended! Now an absorb shield of " + SHIELD_HP + " damage."), 5f);
        }
        Seq<Team> tl = pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            Seq<CoreBlock.CoreBuild> cores = tl.get(i).cores();
            for (int j = 0; j < cores.size; j++) {
                CoreBlock.CoreBuild cb = cores.get(j);
                if (cb == null || !cb.isValid()) continue;
                if (coreDying.containsKey(cb.id)) { cb.health = cb.maxHealth; continue; }
                ShieldRec r = shieldRec(cb.id);
                r.seen = shieldStamp;
                if (inv && cb.health < cb.maxHealth) cb.health = cb.maxHealth;
                boolean prot = shieldProtectedByReflect(cb);
                r.prot = prot; r.b = cb; r.base = cb.health; r.inv = inv;
                r.pre = cb.health + (inv || prot ? SHIELD_BUFFER : Math.max(r.hp, 0f) + SHIELD_BUFFER);
                cb.health = r.pre;
                shieldRecs.add(r);
            }
        }
        shieldSyncT -= Time.delta;
        if (shieldSyncT <= 0) {
            shieldSyncT = SHIELD_SYNC_TICKS;
            coreShield.entrySet().removeIf(e -> e.getValue().seen != shieldStamp);
            sendShield(left);
        }
    }
    static void startCoreDeath(CoreBlock.CoreBuild cb) {
        if (coreDying.containsKey(cb.id)) return;
        CoreDie d = new CoreDie(); d.b = cb; coreDying.put(cb.id, d);
        coreDeathFx(cb.x, cb.y, CORE_DIE_TICKS);
        try { if (Vars.net.server()) Call.clientPacketReliable(CORE_KIND, Math.round(cb.x) + "|" + Math.round(cb.y) + "|" + CORE_DIE_TICKS); } catch (Throwable err) {}
    }
    static void coreDeathFx(float x, float y, float dur) {
        if (Vars.headless) return;
        CoreCine c = new CoreCine(); c.x = x; c.y = y; c.dur = dur; coreCine = c;
    }
    static boolean isModeTurretPos(int pos) {
        for (St st : byId.values()) if (st != null && st.p != null && st.p.contains(pos)) return true;
        return false;
    }
    static void shieldAfter() {
        for (int i = 0; i < shieldRecs.size(); i++) {
            ShieldRec r = shieldRecs.get(i);
            CoreBlock.CoreBuild cb = r.b;
            r.b = null;
            if (cb == null || !cb.isValid()) continue;
            float loss = r.pre - cb.health;
            if (loss <= 0.001f) { cb.health = r.base; continue; }
            if (r.prot) {
                float hp0 = r.base - loss;
                if (hp0 > 0) cb.health = hp0; else { cb.health = cb.maxHealth; startCoreDeath(cb); }
                continue;
            }
            r.hitN++;
            if (r.inv) { cb.health = r.base; syncCore(cb, r); continue; }
            float paid = Math.min(loss, r.hp);
            r.hp -= paid;
            float rest = loss - paid;
            if (r.hp <= 0.001f) { r.hp = 0; if (!r.broken) { r.broken = true; shieldBreakFx(cb); } }
            if (rest > 0.001f) {
                float hp = r.base - rest;
                if (hp > 0) { cb.health = hp; syncCore(cb, r); } else { cb.health = cb.maxHealth; startCoreDeath(cb); }
            } else { cb.health = r.base; syncCore(cb, r); }
        }
        shieldRecs.clear();
    }
    static boolean camVisible(float x, float y, float r) {
        arc.graphics.Camera cam = Core.camera;
        if (cam == null) return true;
        return Math.abs(x - cam.position.x) <= cam.width / 2 + r && Math.abs(y - cam.position.y) <= cam.height / 2 + r;
    }
    static ShieldRec shieldInfo(int id) { return Vars.net.client() ? shieldView.cores.get(id) : coreShield.get(id); }
    static void drawCoreShields() {
        boolean inv = invulTicksLeft() > 0;
        Seq<Team> tl = pvpTeams();
        float T = Time.time;
        for (int i = 0; i < tl.size; i++) {
            Seq<CoreBlock.CoreBuild> cores = tl.get(i).cores();
            for (int j = 0; j < cores.size; j++) {
                CoreBlock.CoreBuild cb = cores.get(j);
                if (cb == null || !cb.isValid()) continue;
                ShieldRec info = shieldInfo(cb.id);
                if (!inv && info != null && (info.broken || info.hp <= 0)) continue;
                if (!inv && info == null && Vars.net.client()) continue;
                float R = shieldRadius(cb);
                if (!camVisible(cb.x, cb.y, R + 16)) continue;
                int hn = info != null ? info.hitN : 0;
                Integer seen = shieldSeenHit.get(cb.id);
                if (seen == null) seen = hn;
                if (hn != seen) shieldFlash.put(cb.id, 25f);
                shieldSeenHit.put(cb.id, hn);
                Float f0 = shieldFlash.get(cb.id);
                float ft = f0 == null ? 0f : Math.max(0f, f0 - Time.delta);
                shieldFlash.put(cb.id, ft);
                float hf = ft / 25;
                boolean hit = hf > 0;
                Color col = inv ? SHIELD_GOLD : SHIELD_COLOR;
                float pulse = inv ? 0.5f + 0.5f * (float) Math.sin(T * 0.06) : 0f;
                try {
                    Draw.z(Layer.shields);
                    Draw.color(hit ? SHIELD_GLOW : col, 0.08f + hf * 0.25f + pulse * 0.07f);
                    Fill.poly(cb.x, cb.y, 6, R);
                    Lines.stroke(hit ? 2.8f : 1.5f);
                    Draw.color(hit ? Color.white : col, 0.8f + hf * 0.2f);
                    Lines.poly(cb.x, cb.y, 6, R);
                    Draw.color(SHIELD_GLOW, 0.5f + hf * 0.5f);
                    Lines.stroke(1.0f);
                    Lines.poly(cb.x, cb.y, 6, R - 3);
                    if (hit) {
                        Draw.color(SHIELD_GLOW, hf);
                        float inner = R * 0.65f;
                        Lines.poly(cb.x, cb.y, 6, inner);
                        for (int q = 0; q < 6; q++)
                            Lines.line(cb.x + Mathf.cosDeg(q * 60) * inner, cb.y + Mathf.sinDeg(q * 60) * inner, cb.x + Mathf.cosDeg((q + 1) * 60) * R, cb.y + Mathf.sinDeg((q + 1) * 60) * R);
                    }
                    Draw.reset();
                } catch (Throwable err) { Draw.reset(); }
            }
        }
    }

    /** Dang ky su kien phan 4 (JS chay ngay luc nap script). Goi tu init() tong. */
    public static void initDrawShield() {
        Events.on(BlockBuildBeginEvent.class, e -> {
            try {
                if (Vars.net.client() || !isActive() || !e.breaking || e.tile == null) return;
                if (!(e.tile.build instanceof ConstructBlock.ConstructBuild)) return;
                Seq<Building> prev = ((ConstructBlock.ConstructBuild) e.tile.build).prevBuild;
                if (prev == null || prev.size < 1) return;
                Building ob = prev.first();
                if (ob == null) return;
                if (!hostOwner.containsKey(ob.id) && !isModeTurretPos(ob.tile.pos())) return;
                e.tile.setNet(Blocks.air, Team.derelict, 0);
            } catch (Throwable err) { logOnce("nodecon", "no-refund failed: " + err, false); }
        });
        Events.on(BlockDestroyEvent.class, e -> {
            try {
                if (Vars.net.client() || !isFull()) return;
                Building b = e.tile.build;
                if (b == null || !(b.block instanceof CoreBlock)) return;
                if (coreKilled.remove(b.id) != null) return;
                coreDeathFx(b.x, b.y, 30);
                if (Vars.net.server()) Call.clientPacketReliable(CORE_KIND, Math.round(b.x) + "|" + Math.round(b.y) + "|30");
            } catch (Throwable err) {}
        });
        Events.run(Trigger.update, () -> {
            if (Vars.net.client()) return;
            java.util.Iterator<Map.Entry<Integer, CoreDie>> it = coreDying.entrySet().iterator();
            while (it.hasNext()) {
                CoreDie d = it.next().getValue();
                d.t += Time.delta;
                CoreBlock.CoreBuild cb = d.b;
                if (cb == null || !cb.isValid()) { it.remove(); continue; }
                cb.health = cb.maxHealth;
                if (d.t >= CORE_DIE_TICKS) {
                    it.remove();
                    coreKilled.put(cb.id, true);
                    try { cb.health = 0; cb.kill(); } catch (Throwable err) {}
                }
            }
        });
        Events.run(Trigger.beforeGameUpdate, () -> { try { shieldBefore(); } catch (Throwable err) { logOnce("shb", "core shield (before): " + err, false); } });
        Events.run(Trigger.afterGameUpdate, () -> { try { shieldAfter(); } catch (Throwable err) { logOnce("sha", "core shield (after): " + err, false); } });
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    pilotOverlay = new Element() {
                        @Override public void draw() {
                            if (!isActive()) return;
                            try { drawPilotUI(); } catch (Throwable err) { logOnce("pilotui", "pilot ui failed: " + err, false); }
                            try { drawPilotCdHud(); } catch (Throwable err) { logOnce("pilotcdhud", "pilot cd hud failed: " + err, false); }
                            Draw.reset();
                        }
                    };
                    pilotOverlay.touchable = Touchable.disabled;
                    pilotOverlay.name = "newex-pvp-pilotfx";
                    pilotOverlay.setSize(screenW(), screenH());
                    Vars.ui.hudGroup.addChild(pilotOverlay);
                    pilotOverlay.toFront();
                } catch (Throwable err) { Log.err("[NexPvp] cannot add pilot overlay: " + err); }
            });
            Events.run(Trigger.postDraw, () -> {
                if (!isActive()) return;
                try {
                    Groups.player.each(pl -> eachDrawEquip(pl));
                    drawBuildPreview();
                    drawCmdMark();
                    drawCoreShields();
                    drawPilotIcons();
                    Draw.reset();
                } catch (Throwable err) {}
            });
        }
    }


    // ===== PHAN 5: bot ctx (khung), chi so hieu dung, ky nang phi cong Xaky/Neo/Vanhau (JS 426-525, 2789-3129) =====
    /** JS newBotCtx(): kieu Object cho truong chua ro; tinh chinh o phan bot. `_unit` cua JS -> botUnit. */
    static final class Seen { int u, pi; }
    static final class BotMem { String id; int pos; }
    static final class BotIdle { String id; float t; }
    static final class BotCtx {
        Player botPl;
        float botBuyT, botScanT, botDbgT, botRetreatT, botNoBuyT, botNoAtkT, botPlanT, botBaseT, botIncomeT, botMin, botCoreHit, botCoreHp,
            botAlertT, botFightT, botMissionT, botRebuildCd, botSkipT, botStuckT, botLastX, botLastY, botIdleT, botSaveCost, botSeenAt = -1e9f, botSeenT,
            botSwapT0, botScoutT0, botSm = 1, botNoShotT, botResetT0, botDeadT = 60,
            botPathT, botPathGx, botPathGy, botPathDx = Float.NaN, botPathDy,
            botEpT, botEncT, botHitT, botFleeT, botRevengeT, botNpT, botEngR, botEngScanT;
        int botEnc;
        boolean botEngOn, botHome, botBuyTrip, botAttack, botSwapPend, botScout, botResetPend, botPathOk = true, botPathWait;
        Unit botSkip, botTarget, botEp, botNp, botHuntU, botEngTgt; Entityc botHitBy; Integer botKillerId;
        Anch botBase, botEnemyPos; Seen botSeen;
        final HashMap<Integer, BotMem> botMem = new HashMap<>();
        final HashMap<Integer, BotIdle> botIdle = new HashMap<>();
        final HashMap<String, Boolean> botBad = new HashMap<>();
        final HashMap<String, Float> botBan = new HashMap<>();
    }
    static final ArrayList<BotCtx> bots = new ArrayList<>();
    static BotCtx BC = new BotCtx();
    static final int BOT_MAX = 8;
    static final HashMap<Integer, Integer> soldierOwner = new HashMap<>();
    static boolean pvbOn() { return setBool(KEY_PVB, false); }
    static boolean bvbOn() { return setBool(KEY_BVB, false); }
    static BotCtx botOf(int id) { for (int i = 0; i < bots.size(); i++) if (bots.get(i).botPl.id == id) return bots.get(i); return null; }
    static BotCtx botUnitOf(Unit u) { if (u == null) return null; for (int i = 0; i < bots.size(); i++) { Player bp = bots.get(i).botPl; if (bp instanceof BotPlayer && ((BotPlayer) bp)._unit == u) return bots.get(i); } return null; }
    static boolean isBotId(int id) { return id <= BOT_ID && id > BOT_ID - 64; }
    static boolean isBotPl(Player p) { return p != null && isBotId(p.id) && botOf(p.id) != null; }
    static Player ownerPl(Unit u) {
        if (u == null) return null;
        Player p = null;
        try { p = u.getPlayer(); } catch (Throwable err) {}
        if (p == null && bots.size() > 0) { BotCtx b = botUnitOf(u); if (b != null) p = b.botPl; }
        return p;
    }
    static int botSmart() {
        int n = 0; Team t = BC.botPl.team();
        for (int i = 0; i < bots.size(); i++) if (bots.get(i).botPl.team() == t) n++;
        return Math.max(1, n);
    }

    // ===== chi so hieu dung =====
    static int[] effectiveStats(St st) {
        if (st == null) return null;
        if (st.pi == PILOT_HIST) {
            int[] h = (int[]) st.hs;
            if (h == null) { h = st.s.clone(); st.hs = h; }
            for (int i = 0; i < h.length; i++) h[i] = i != STAT_ARMY ? Math.min(st.s[i] + 1, statMax(i)) : st.s[i];
            return h;
        }
        if (st.pi == PILOT_XAKYNEO) {
            int[] xn = (int[]) st.xns;
            if (xn == null) { xn = st.s.clone(); st.xns = xn; }
            for (int i = 0; i < xn.length; i++) xn[i] = st.s[i];
            xn[5] = Math.min(st.s[5] + XAKYNEO_CRIT_BONUS_LV, statMax(5));
            xn[6] = Math.min(st.s[6] + XAKYNEO_CRIT_BONUS_LV, statMax(6));
            xn[8] = Math.min(st.s[8] + XAKYNEO_CRIT_BONUS_LV, statMax(8));
            return xn;
        }
        return st.s;
    }
    static int[] statsOf(Unit unit) { return effectiveStats(statsStateOf(unit)); }
    static St statsStateOf(Unit unit) {
        Player p = ownerPl(unit);
        if (p != null) return byId.get(p.id);
        Integer pid = soldierOwner.get(unit.id);
        return pid != null ? byId.get(pid) : null;
    }

    // ===== Xaky: lao khi mat mau =====
    static final class XPrev { int u; float hp, mh; }
    static final HashMap<Integer, XPrev> xakyPrev = new HashMap<>();
    static final HashMap<Integer, Float> xakyNeoStart = new HashMap<>();
    static float xakyDashDist(Unit u, float ang) {
        float d = XAKY_DASH;
        if (u.isFlying()) return d;
        for (float l = 4; l <= d; l += 4) {
            Tile t = Vars.world.tileWorld(u.x + Angles.trnsx(ang, l), u.y + Angles.trnsy(ang, l));
            if (t == null || t.solid()) return Math.max(0f, l - 4 - 8 - u.hitSize / 2);
        }
        return d;
    }
    static void xakyCheck(Unit unit, Player pl, St st) {
        int id = pl.id;
        float hp = unit.health + unit.shield;
        XPrev r = xakyPrev.get(id);
        XPrev n = new XPrev(); n.u = unit.id; n.hp = hp; n.mh = unit.maxHealth;
        xakyPrev.put(id, n);
        if (r == null || r.u != unit.id || r.mh != unit.maxHealth || hp >= r.hp - 0.01f) return;
        if (st.lv < XAKY_MIN_LEVEL || unit.dead) return;
        Float at = xakyCdAt.get(id);
        if (at != null && Time.time >= at && Time.time - at < XAKY_CD) return;
        float ang = unit.vel.len() > 0.1f ? unit.vel.angle() : unit.rotation;
        float d = xakyDashDist(unit, ang);
        if (d < 4) return;
        float ox = unit.x, oy = unit.y, nx = ox + Angles.trnsx(ang, d), ny = oy + Angles.trnsy(ang, d);
        nx = Mathf.clamp(nx, 0f, Vars.world.width() * Vars.tilesize);
        ny = Mathf.clamp(ny, 0f, Vars.world.height() * Vars.tilesize);
        unit.set(nx, ny);
        xakyCdAt.put(id, Time.time);
        pilotFxAt.put(id, Time.time);
        try { Call.effect(Fx.spawnShockwave, ox, oy, 0, Color.white); Call.effect(Fx.spawnShockwave, nx, ny, 0, Color.white); } catch (Throwable err) {}
        try { if (Vars.net.server()) Call.clientPacketReliable(PFX_KIND, id + "x"); } catch (Throwable err) {}
    }
    static float xakyNeoRampPct(Unit unit, Player pl) {
        if (pl == null) pl = ownerPl(unit);
        if (pl == null) return 0f;
        int id = pl.id;
        Float t0 = xakyNeoStart.get(id);
        if (t0 == null) { xakyNeoStart.put(id, Time.time); return 0f; }
        float minutes = (Time.time - t0) / 3600f;
        return Math.min((float) XAKYNEO_RAMP_MAX, minutes * XAKYNEO_RAMP_PCT_MIN) / 100f;
    }

    // ===== hieu ung ket lieu cua Neo: JS tu dung "container" gia -> Java dung NeoCtx + ham ve rieng (khong can Effect that) =====
    static final Color NEO_CYAN = Color.valueOf("#7df9ff"), NEO_BLUE = Color.valueOf("#3a7bff"), NEO_GOLD = Color.valueOf("#ffd34d"), NEO_WHITE = Color.valueOf("#ffffff");
    static final float NEO_S = 0.75f, NEO_T = 0.4375f, NEO_LEN = 1100, NEO_ANG = 118;
    static float neoClamp(float v) { return Math.min(1f, Math.max(0f, v)); }
    static final class NeoCtx {
        int kind, id; float life, t, x, y, rotation;
        float fin() { return Math.min(1f, t / life); }
        float fout() { return 1 - fin(); }
        float finpow() { return Interp.pow3Out.apply(fin()); }
    }
    static void neoSafe(String key, Runnable fn) { try { fn.run(); } catch (Throwable err) { Draw.reset(); logOnce("neofx-" + key, "neo fx section '" + key + "' failed: " + err, false); } }
    static final int NEO_EXEC = 0, NEO_STARS = 1, NEO_LASER = 2, NEO_MUZZLE = 3;
    static void neoRender(NeoCtx e) {
        if (e.kind == NEO_EXEC) neoExecRender(e);
        else if (e.kind == NEO_STARS) neoStarsRender(e);
        else if (e.kind == NEO_LASER) neoLaserRender(e);
        else neoMuzzleRender(e);
    }
    static void neoExecRender(NeoCtx e) {
        final float S = NEO_S, T = NEO_T, fi = e.fin(), fo = e.fout();
        Draw.z(Layer.effect + 2);
        neoSafe("core", () -> {
            Draw.color(NEO_GOLD, NEO_WHITE, fi);
            Lines.stroke(fo * 6 * T);
            Lines.circle(e.x, e.y, e.finpow() * 70 * S);
            Draw.color(NEO_WHITE, NEO_CYAN, fi); Draw.alpha(fo);
            for (int k = 0; k < 8; k++) Drawf.tri(e.x, e.y, (5 * fo + 1) * S, (24 + 76 * fi) * S, k * 45 + fi * 25);
        });
        neoSafe("flash", () -> {
            if (fi < 0.3f) { float q = fi / 0.3f; Draw.color(NEO_WHITE, NEO_CYAN, q); Draw.alpha(1 - q); Fill.circle(e.x, e.y, S * (10 + q * 40)); }
        });
        neoSafe("cross", () -> {
            float sw = Interp.pow3Out.apply(neoClamp(fi * 1.6f));
            Draw.color(NEO_CYAN, NEO_WHITE, fo); Draw.alpha(Math.min(1f, fo * 1.8f));
            Fill.rect(e.x, e.y, S * 170 * sw, T * (3 * fo + 0.6f));
            Fill.rect(e.x, e.y, T * (4 * fo + 0.6f), S * 130 * sw);
        });
        neoSafe("frame", () -> {
            if (fi >= 0.6f) return;
            float q2 = fi / 0.6f, sz = S * (58 - 34 * Interp.pow3Out.apply(q2)), arm = S * 10;
            Draw.color(NEO_GOLD); Draw.alpha(1 - q2 * q2);
            Lines.stroke(T * 2.4f);
            for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
                Lines.line(e.x + sx * sz, e.y + sy * sz, e.x + sx * (sz - arm), e.y + sy * sz);
                Lines.line(e.x + sx * sz, e.y + sy * sz, e.x + sx * sz, e.y + sy * (sz - arm));
            }
        });
        neoSafe("hex", () -> {
            Draw.color(NEO_CYAN, NEO_BLUE, fi);
            Lines.stroke(T * fo * 5);
            Lines.poly(e.x, e.y, 6, e.finpow() * 74 * S, fi * 70);
            float f2 = neoClamp(fi * 1.5f - 0.15f);
            if (f2 > 0 && f2 < 1) { Draw.color(NEO_WHITE, NEO_CYAN, f2); Lines.stroke(T * (1 - f2) * 3.2f); Lines.poly(e.x, e.y, 6, (float) Math.pow(f2, 0.5) * 104 * S, -f2 * 90); }
        });
        neoSafe("arcs", () -> {
            Draw.color(NEO_GOLD, NEO_CYAN, fi);
            Lines.stroke(T * (fo * 3 + 0.4f));
            for (int k = 0; k < 6; k++) Lines.arc(e.x, e.y, S * (30 + 30 * Interp.pow2Out.apply(fi)), 0.11f, k * 60 + fi * 220);
        });
        neoSafe("debris", () -> {
            Draw.color(NEO_WHITE, NEO_CYAN, fi);
            Angles.randLenVectors(e.id + 7, 22, e.finpow() * 105 * S, (x, y) -> Fill.square(e.x + x, e.y + y, S * (fo * 3.2f + 0.6f), fi * 240 + x));
            Draw.color(NEO_GOLD, NEO_WHITE, fo);
            Lines.stroke(T * fo * 2);
            Angles.randLenVectors(e.id + 31, 18, e.finpow() * 90 * S, (x, y) -> Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), S * (fo * 10 + 1)));
        });
        Draw.reset();
    }
    static void neoStarsRender(NeoCtx e) {
        try {
            Draw.z(Layer.effect + 2);
            Draw.blend(Blending.additive);
            for (int i = 0; i < 24; i++) {
                float pf = Math.min(1f, e.fin() * (0.7f + ((i * 53) % 10) * 0.05f));
                if (pf <= 0) continue;
                final float pfo = 1 - pf, sz = (1.4f + ((i * 37) % 5) * 0.6f) * 5, pff = pf;
                Angles.randLenVectors(e.id + i * 911L, 1, Mathf.pow(pf, 0.6f) * 240, (x, y) -> {
                    float rot = Mathf.angle(x, y) + pff * 50;
                    Draw.color(NEO_WHITE, NEO_GOLD, Math.min(1f, pff * 1.4f)); Draw.alpha(Math.min(1f, pfo * 1.6f));
                    for (int k = 0; k < 4; k++) Drawf.tri(e.x + x, e.y + y, sz * 0.42f, sz, rot + k * 90);
                });
            }
            Draw.blend();
            Draw.reset();
        } catch (Throwable err) { Draw.blend(); Draw.reset(); logOnce("neofx3", "neo stars fx failed: " + err, false); }
    }
    static void neoLaserRender(NeoCtx e) {
        neoSafe("laser", () -> {
            float fi = e.fin(), k = fi < 0.1f ? fi / 0.1f : (float) Math.pow((1 - fi) / 0.9f, 1.4);
            float sx = e.x + Angles.trnsx(NEO_ANG, NEO_LEN), sy = e.y + Angles.trnsy(NEO_ANG, NEO_LEN);
            Draw.z(Layer.effect + 3);
            Draw.color(NEO_CYAN); Draw.alpha(0.4f * k);
            Lines.stroke(44 * k); Lines.line(sx, sy, e.x, e.y);
            Draw.alpha(0.85f * k);
            Lines.stroke(22 * k); Lines.line(sx, sy, e.x, e.y);
            Draw.color(NEO_WHITE); Draw.alpha(1f);
            Lines.stroke(9 * k); Lines.line(sx, sy, e.x, e.y);
            Fill.circle(e.x, e.y, 26 * k);
            Draw.reset();
        });
    }
    static void neoMuzzleRender(NeoCtx e) {
        try {
            Draw.z(Layer.effect + 2);
            Draw.blend(Blending.additive);
            float k = e.fout();
            Draw.color(NEO_WHITE, NEO_CYAN, e.fin());
            Drawf.tri(e.x, e.y, 12 * k + 2, 80 * k + 8, e.rotation);
            Drawf.tri(e.x, e.y, 12 * k + 2, 80 * k + 8, e.rotation + 180);
            Fill.circle(e.x, e.y, 11 * k);
            Draw.blend();
            Draw.reset();
        } catch (Throwable err) { Draw.blend(); Draw.reset(); logOnce("neofx2", "neo muzzle fx failed: " + err, false); }
    }
    static void neoFxAt(Effect fx, float x, float y, float rot) {
        try {
            if (Vars.headless) { if (Vars.net.server()) Call.effect(fx, x, y, rot, Color.white); return; }
            if (Vars.net.active()) Call.effect(fx, x, y, rot, Color.white); else fx.at(x, y, rot, Color.white);
        } catch (Throwable err) {
            logOnce("neofxcall", "neo Call.effect failed: " + err, false);
            try { if (!Vars.headless) fx.at(x, y, rot, Color.white); } catch (Throwable err2) {}
        }
    }
    static HashMap<Integer, Float> neoFxMark = new HashMap<>();
    static int neoFxCnt = 0, neoDiagN = 0;
    static final ArrayList<NeoCtx> neoLive = new ArrayList<>();
    static boolean neoDrawErr = false;
    static void neoLocalFx(int kind, float life, float x, float y, float rot) {
        if (Vars.headless) return;
        NeoCtx c = new NeoCtx(); c.kind = kind; c.life = life; c.x = x; c.y = y; c.rotation = rot; c.id = (int) Math.floor(Time.time) % 9973;
        neoLive.add(c);
    }
    static void neoDrawLive() {
        if (neoLive.size() == 0) return;
        float dt = Vars.state.isPaused() ? 0f : Time.delta;
        for (int i = neoLive.size() - 1; i >= 0; i--) {
            NeoCtx o = neoLive.get(i);
            if (o.t >= o.life) { neoLive.remove(i); continue; }
            try { neoRender(o); } catch (Throwable err) { Draw.reset(); if (!neoDrawErr) { neoDrawErr = true; Log.err("[NexPvp] neo draw failed: " + err); } }
            o.t += dt;
        }
        Draw.reset();
    }
    static void neoDiag(float ex, float ey) {
        try {
            if (Vars.headless || Core.camera == null || neoDiagN >= 3) return;
            neoDiagN++;
            Vec2 cp = Core.camera.position;
            Log.info("[NexPvp] neo fx #" + neoDiagN + ": cách tâm camera " + Math.round(Mathf.dst(cp.x, cp.y, ex, ey)) + " đv (nửa khung ~" + Math.round(Core.camera.width / 2) + "), effects=" + Vars.renderer.enableEffects + ", net.active=" + Vars.net.active());
        } catch (Throwable err) {}
    }
    /** Tra false neu vua phat cho cung muc tieu (chong lap). b/o co the null (nhanh quet). */
    static boolean neoExecuteShow(int apid, int tid, float ex, float ey, Bullet b, Unit o, boolean isUnitOwner) {
        float nowT = Time.time;
        Float lm = neoFxMark.get(tid);
        if (lm != null && nowT - lm < 20) return false;
        if (++neoFxCnt > 300) { neoFxCnt = 0; neoFxMark = new HashMap<>(); }
        neoFxMark.put(tid, nowT);
        try { Call.label("[#7df9ff]KẾT LIỄU", 1.4f, ex, ey + 14); } catch (Throwable err) { logOnce("neolabel", "neo label failed: " + err, false); }
        neoFxAt(Fx.massiveExplosion, ex, ey, 0);
        neoLocalFx(NEO_EXEC, 90, ex, ey, 0);
        neoLocalFx(NEO_STARS, 55, ex, ey, 0);
        neoLocalFx(NEO_LASER, 48, ex, ey, 0);
        if (isUnitOwner && b != null && o != null) {
            try {
                float mx = b.originX, my = b.originY, br = b.rotation();
                if (mx == 0 && my == 0) { mx = o.x + Angles.trnsx(br, o.hitSize * 0.8f); my = o.y + Angles.trnsy(br, o.hitSize * 0.8f); }
                neoLocalFx(NEO_MUZZLE, 12, mx, my, br + 90);
            } catch (Throwable err) {}
        }
        neoDiag(ex, ey);
        pilotFxAt.put(apid, Time.time);
        try { if (Vars.net.server()) Call.clientPacketReliable(PFX_KIND, apid + "n"); } catch (Throwable err) {}
        return true;
    }
    static void neoFinisher(Bullet b, Unit tgt) {
        Entityc o = b.owner;
        if (o == null || !(o instanceof Teamc) || ((Teamc) o).team() == tgt.team || !(tgt.maxHealth > 0)) return;
        boolean isUnitOwner = o instanceof Unit;
        Integer apid;
        if (isUnitOwner) {
            if (soldierOwner.containsKey(o.id())) return;
            Player ap = ownerPl((Unit) o);
            if (ap == null) return;
            apid = ap.id;
        } else apid = hostOwner.get(o.id());
        if (apid == null) return;
        St st = byId.get(apid);
        if (st == null || st.pi != PILOT_XAKYNEO) return;
        float dealt = b.damage;
        try { dealt = Damage.applyArmor(b.damage, tgt.armor) / Math.max(0.01f, tgt.healthMultiplier) / Math.max(0.01f, Vars.state.rules.unitHealth(tgt.team)); } catch (Throwable err) {}
        if ((tgt.health + dealt) / tgt.maxHealth >= XAKYNEO_EXECUTE_RATIO) return;
        if (neoExecuteShow(apid, tgt.id, tgt.x, tgt.y, b, isUnitOwner ? (Unit) o : null, isUnitOwner)) logOnce("neo-run2", "neo execute (đòn kết thúc) on " + tgt.type.name, true);
    }
    static void neoExecuteAfter(Unit su, int apid, float ex, float ey, float atk) {
        if (su == null || su.dead) return;
        try {
            float amt = su.maxHealth * XAKYNEO_EXECUTE_SELF_PCT;
            if (equippedCount(byId.get(apid), THERDUM_ID) > 0) su.heal(amt); else su.damage(amt);
        } catch (Throwable err) {}
        try {
            float dmg = NEO_BLAST_ATK * atk + NEO_BLAST_ARMOR * su.armor + NEO_BLAST_HP * su.maxHealth;
            Damage.damage(su.team, ex, ey, NEO_BLAST_R, dmg, false, true, true);
        } catch (Throwable err) { logOnce("neoblast", "neo blast failed: " + err, false); }
    }
    static final class NeoMk { float t, d; int pid; }
    static final HashMap<Integer, NeoMk> neoMarks = new HashMap<>();
    static float neoScanT = 0;
    static void neoExecuteUnit(Unit u, int apid, float atk) {
        float ex = u.x, ey = u.y; int tid = u.id;
        if (!u.dead) { try { u.damagePierce(u.health + u.maxHealth); } catch (Throwable err) { try { u.kill(); } catch (Throwable err2) {} } }
        logOnce("neo-scan", "neo execute (quét) on " + u.type.name + " hp=" + Math.round(u.health) + "/" + Math.round(u.maxHealth), true);
        if (neoExecuteShow(apid, tid, ex, ey, null, null, false)) neoExecuteAfter(plUnit(apid), apid, ex, ey, atk);
    }
    static void neoScan() {
        try {
            if (Vars.net.client() || !isActive() || Vars.state.isPaused()) return;
            neoScanT += Time.delta;
            if (neoScanT < 3) return;
            neoScanT = 0;
            final ArrayList<Object[]> todo = new ArrayList<>();
            final ArrayList<Integer> stale = new ArrayList<>();
            final float now = Time.time;
            Groups.unit.each(u -> {
                NeoMk m = neoMarks.get(u.id);
                if (m == null) return;
                if (u.dead || !u.isValid() || now - m.t > 900) { stale.add(u.id); return; }
                if (u.maxHealth > 0 && u.health / u.maxHealth < XAKYNEO_EXECUTE_RATIO) {
                    St st = byId.get(m.pid);
                    if (st != null && st.pi == PILOT_XAKYNEO) todo.add(new Object[]{u, m.pid, m.d});
                    stale.add(u.id);
                }
            });
            for (Integer id : stale) neoMarks.remove(id);
            for (Object[] t : todo) neoExecuteUnit((Unit) t[0], (Integer) t[1], (Float) t[2]);
        } catch (Throwable err) { logOnce("neoscan", "neo scan failed: " + err, false); }
    }

    // ===== Vanhau: khien luc =====
    static final Effect vanhauShieldFx = new Effect(28f, e -> {
        Draw.color(SHIELD_GLOW, Color.white, e.fin());
        Lines.stroke(e.fout() * 3.0f);
        Lines.circle(e.x, e.y, e.finpow() * 11.0f);
        Draw.color(SHIELD_COLOR, SHIELD_GLOW, e.fin());
        Lines.stroke(e.fout() * 1.6f);
        Lines.circle(e.x, e.y, e.finpow() * 11.0f);
        Draw.reset();
    });
    static final class VhCtx { VanhauField f; Team team; float x, y, r2; }
    static VhCtx vhCtx = null;
    static void vhEach(Bullet b) {
        VhCtx c = vhCtx;
        if (c.f.hp <= 0 || b.team == c.team || !b.type.absorbable) return;
        if (Mathf.dst2(b.x, b.y, c.x, c.y) > c.r2) return;
        c.f.hp -= Math.max(1f, b.damage);
        c.f.fl = 1;
        try { b.absorb(); } catch (Throwable err) {}
        if (!Vars.headless) { try { Fx.absorb.at(b.x, b.y); } catch (Throwable err) {} }
    }
    public static void initPilot() {
        Log.info("[NexPvp] neo fx v9 loaded");
        Events.run(Trigger.update, () -> neoScan());
        if (!Vars.headless) Events.run(Trigger.draw, () -> neoDrawLive());
    }


    // ===== PHAN 6: Vanhau/Empix, applyStats, trang thai, ban bonus (JS 3130-3465) =====
    static final HashMap<Integer, Boolean> vanhauShieldOn = new HashMap<>();
    static final HashMap<Integer, Integer> empixLow = new HashMap<>();
    static final HashMap<Integer, Unit> prevUnit = new HashMap<>();
    static final HashMap<Integer, Float> thHp = new HashMap<>();
    static final HashMap<Integer, Float> spawnBuff = new HashMap<>();
    static final HashMap<Integer, Boolean> rolled = new HashMap<>();
    static int rolledN = 0;
    static final HashMap<Integer, BulletType> pierceCache = new HashMap<>();

    static float vanhauR(Unit u) { return Math.max(34f, u.hitSize * 0.66f + 17); }
    static void vanhauSend(int id, Unit u, VanhauField f) {
        try { if (Vars.net.server()) Call.clientPacketUnreliable(VHS_KIND, id + "|" + u.id + "|" + Math.round(f.hp) + "|" + Math.round(f.max) + "|" + Math.round(f.r)); } catch (Throwable err) {}
    }
    static boolean vanhauTick(Unit u, int id, VanhauField f) {
        if (f.uid != u.id || u.dead) { vanhauField.remove(id); return false; }
        f.r = vanhauR(u);
        VhCtx c = new VhCtx(); c.f = f; c.team = u.team; c.x = u.x; c.y = u.y; c.r2 = f.r * f.r;
        vhCtx = c;
        try { Groups.bullet.intersect(u.x - f.r, u.y - f.r, f.r * 2, f.r * 2, (Cons<Bullet>) b -> vhEach(b)); }
        catch (Throwable err) { Groups.bullet.each((Cons<Bullet>) b -> vhEach(b)); }
        if (f.hp <= 0) {
            f.hp = 0;
            vanhauSend(id, u, f);
            vanhauField.remove(id);
            try { Call.effect(Fx.shieldBreak, u.x, u.y, f.r, SHIELD_COLOR); } catch (Throwable err) {}
            return false;
        }
        f.st -= Time.delta;
        if (f.st <= 0) { f.st = 6; vanhauSend(id, u, f); }
        return true;
    }
    static void vanhauCheck(Unit unit, Player pl, St st) {
        if (pl == null || unit.dead || unit.health <= 0) return;
        int id = pl.id;
        VanhauField vf = vanhauField.get(id);
        if (vf != null && vanhauTick(unit, id, vf)) return;
        if (vanhauShieldOn.get(id) != null && vanhauShieldOn.get(id)) vanhauShieldOn.put(id, false);
        if (unit.health >= unit.maxHealth * VANHAU_TRIGGER_HP) return;
        Float at = vanhauCdAt.get(id);
        if (at != null && Time.time >= at && Time.time - at < VANHAU_CD) return;
        VanhauField f = new VanhauField();
        f.uid = unit.id; f.hp = unit.maxHealth * VANHAU_SHIELD_PCT; f.max = f.hp; f.r = vanhauR(unit); f.fl = 0; f.st = 0;
        vanhauField.put(id, f);
        vanhauShieldOn.put(id, true);
        vanhauCdAt.put(id, Time.time);
        pilotFxAt.put(id, Time.time);
        try { Call.effect(vanhauShieldFx, unit.x, unit.y, 0, Color.white); } catch (Throwable err) {}
        try { if (Vars.net.server()) Call.clientPacketReliable(PFX_KIND, id + "v"); } catch (Throwable err) {}
    }

    static void empixFx(int id, boolean cd) {
        pilotFxAt.put(id, Time.time);
        if (cd) empixCdAt.put(id, Time.time);
        try { if (Vars.net.server()) Call.clientPacketReliable(PFX_KIND, cd ? String.valueOf(id) : id + "n"); } catch (Throwable err) {}
    }
    static int empixUsed(int id) {
        Integer n0 = empixLow.get(id);
        int n = n0 == null ? 0 : n0;
        if (n >= 2) {
            Float at = empixCdAt.get(id);
            if (at != null && Time.time >= at && Time.time - at < EMPIX_HEAL_CD) return 2;
            empixLow.put(id, 0); n = 0;
        }
        return n;
    }
    static void empixHeal(Unit unit) {
        Player pl = ownerPl(unit);
        if (pl == null || unit.dead || unit.health <= 0) return;
        int id = pl.id;
        if (empixUsed(id) != 0 || unit.health >= unit.maxHealth * EMPIX_HEAL_HP) return;
        empixLow.put(id, 1);
        unit.health = unit.maxHealth;
        empixFx(id, false);
        try { Fx.healWaveMend.at(unit.x, unit.y, unit.hitSize); } catch (Throwable err) {}
        toast(pl, tr("[accent]Empixeand Pilot hồi đầy máu (còn 1 lần hồi sinh khi chạm 0 máu)!", "[accent]Empixeand Pilot fully healed you (1 revive left at 0 HP)!"));
    }
    static boolean empixRevive(Unit u) {
        Player found = ownerPl(u);
        if (found == null) {
            for (Player p : Groups.player) if (found == null && prevUnit.get(p.id) == u) found = p;
            for (int i = 0; found == null && i < bots.size(); i++) if (prevUnit.get(bots.get(i).botPl.id) == u) found = bots.get(i).botPl;
        }
        if (found == null) return false;
        final Player pl = found;
        St st = byId.get(pl.id);
        if (st == null || st.pi != PILOT_EMPIX) return false;
        final int id = pl.id;
        if (empixUsed(id) >= 2) return false;
        empixLow.put(id, 2);
        empixCdAt.put(id, Time.time);
        final UnitType type = u.type; final Team team = u.team; final float x = u.x, y = u.y, rot = u.rotation;
        boolean sbc0 = true;
        try { sbc0 = u.spawnedByCore; } catch (Throwable err) {}
        final boolean sbc = sbc0;
        Time.run(1f, () -> {
            try {
                Unit cur = pl.unit();
                if (cur != null && cur.isValid() && !cur.dead && cur.type != UnitTypes.block) cur.remove();
                Unit nu = type.create(team);
                nu.set(x, y);
                nu.rotation = rot;
                try { nu.spawnedByCore = sbc; } catch (Throwable err) {}
                nu.add();
                nu.health = nu.maxHealth;
                pl.unit(nu);
                prevUnit.put(pl.id, nu);
                empixFx(id, true);
                try { Fx.healWaveMend.at(x, y, nu.hitSize); } catch (Throwable err) {}
                toast(pl, tr("[accent]Empixeand Pilot đã hồi sinh bạn khi chạm 0 máu! (hồi chiêu 5 phút)", "[accent]Empixeand Pilot revived you at 0 HP! (5 min cooldown)"));
            } catch (Throwable err) { logOnce("empixrevive", "empix revive failed: " + err, false); }
        });
        return true;
    }

    static boolean inOwnTerritory(Team team, float x, float y) {
        Seq<CoreBlock.CoreBuild> cores = team.cores();
        for (int i = 0; i < cores.size; i++) {
            CoreBlock.CoreBuild c = cores.get(i);
            if (c != null && c.isValid() && Mathf.dst(x, y, c.x, c.y) <= TERRITORY_RADIUS) return true;
        }
        return false;
    }
    static boolean inOwnTerritory(Unit unit) { return inOwnTerritory(unit.team, unit.x, unit.y); }

    // Hieu ung buff lanh tho: giong nucleytorBuffEffect cua Nucleytor (3 soc sang xanh la -> xanh duong bay len tren unit/khoi phao).
    static final Color TERR_FX_A = Color.valueOf("39e75f"), TERR_FX_B = Color.valueOf("00a2ff");
    static final Effect territoryBuffFx = new Effect(40f, e -> {
        float size = e.rotation > 0 ? e.rotation : 16f, half = size / 2f;
        arc.math.Rand rand = new arc.math.Rand(e.id);
        for (int i = 0; i < 3; i++) {
            float rx = rand.random(-half, half), pLife = rand.random(0.5f, 1f);
            float progress = Math.min(e.fin() / pLife, 1f);
            if (progress >= 1f) continue;
            float fout = 1f - progress, travel = rand.random(size * 0.8f, size * 1.3f);
            float ry = -half + progress * travel, len = 8f * fout;
            Draw.color(Tmp.c1.set(TERR_FX_A).lerp(TERR_FX_B, progress));
            Lines.stroke(fout);
            Lines.line(e.x + rx, e.y + ry - len / 2f, e.x + rx, e.y + ry + len / 2f);
        }
        Draw.reset();
    });
    /** Chi ve o may co man hinh (headless bo qua); 8%/tick giong Nucleytor. size = hitSize unit hoac size*8 cua khoi. */
    static void territoryFx(float x, float y, float size) {
        if (Vars.headless || !(Math.random() < 0.08)) return;
        try { territoryBuffFx.at(x, y, size); } catch (Throwable err) {}
    }
    static void applyStats(Unit unit) {
        if (!isActive() || (!isFull() && !isTierUnit(unit))) return;
        Player pl = ownerPl(unit);
        St stOwn = null;
        if (pl != null) stOwn = byId.get(pl.id);
        else { Integer pid = soldierOwner.get(unit.id); if (pid != null) stOwn = byId.get(pid); }
        int[] s = effectiveStats(stOwn);
        if (s == null) return;
        EquipSum eqc = equipSummary(stOwn);
        Rc rc = eqc != null ? eqc.rc : EMPTY_RC;

        boolean isPl = pl != null;
        float tHp = unit.type.health;
        if (isPl && unit.type == UnitTypes.reign) tHp *= REIGN_HP_MUL;
        float hpT = (tHp * (1 + s[0] * HP_PCT / 100f) + s[0] * HP_FLAT) * (1 + Math.min(ROLE_HP_MAX, rc.brl * ROLE_HP_PCT) / 100f);
        int pi = isPl ? stOwn.pi : 0;
        boolean empixOn = pi == PILOT_EMPIX;
        if (empixOn) hpT *= 1 + EMPIX_HP_PCT;
        boolean xakyOn = pi == PILOT_XAKY;
        if (xakyOn) hpT *= XAKY_HP_MUL;
        boolean histOn = pi == PILOT_HIST;
        if (histOn) hpT *= 1 + HIST_STAT_PCT;
        boolean xakyNeoOn = pi == PILOT_XAKYNEO, vanhauOn = pi == PILOT_VANHAU;
        if (Math.abs(unit.maxHealth - hpT) > 0.5f) {
            float diff = hpT - unit.maxHealth;
            unit.maxHealth = hpT;
            if (diff > 0) unit.health += diff; else if (unit.health > hpT) unit.health = hpT;
        }

        unit.armor = (unit.type.armor + s[1] * ARM_FLAT) * (1 + rc.def * ROLE_ARM_PCT / 100f) + rc.def * ROLE_ARM_FLAT;
        if (histOn) unit.armor *= 1 + HIST_STAT_PCT;
        float cut = Math.min((float) ROLE_CUT_CAP, armorCut(s[1]) + rc.def * ROLE_CUT_PCT);
        if (cut > 0) unit.healthMultiplier *= 1 / (1 - cut / 100f);

        float dm = (1 + s[2] * ATK_PCT / 100f) * (1 + Math.min(ROLE_ATK_MAX, rc.atk * ROLE_ATK_PCT) / 100f);
        if (s[5] > 0 && Math.random() < s[5] * 0.02) dm *= 1.5f + s[6] * 0.12f;
        if (empixOn) dm *= 1 + EMPIX_ATK_PCT;
        if (histOn) dm *= 1 + HIST_STAT_PCT;
        if (xakyNeoOn) dm *= 1 + XAKYNEO_ATK_PCT + xakyNeoRampPct(unit, pl);
        unit.damageMultiplier *= dm;
        boolean isServer = !Vars.net.client();
        if (empixOn && isServer) empixHeal(unit);
        float base = Math.max(0.1f, unit.type.speed);
        unit.speedMultiplier *= (1 + s[3] * SPD_PCT / 100f) * (1 + s[3] * SPD_FLAT / base);
        if (isPl && unit.type == UnitTypes.disrupt) unit.speedMultiplier *= DISRUPT_SPEED_MUL;
        if (xakyOn) unit.speedMultiplier *= XAKY_SPD_MUL;
        if (histOn) unit.speedMultiplier *= 1 + HIST_STAT_PCT;
        if (inOwnTerritory(unit)) {
            unit.speedMultiplier *= TERRITORY_SPEED_MUL;
            territoryFx(unit.x, unit.y, unit.hitSize);
        }
        unit.reloadMultiplier *= 1 + s[4] * 0.06f;
        if (histOn) {
            unit.reloadMultiplier *= 1 + HIST_STAT_PCT;
            if (isServer && unit.health > 0) {
                if (unit.health < unit.maxHealth) unit.health = Math.min(unit.maxHealth, unit.health + unit.maxHealth * HIST_REGEN_PCT / 100f * Time.delta / 60f);
                float shCap = unit.maxHealth * HIST_SHIELD_PCT;
                if (unit.shield < shCap) unit.shield = Math.min(shCap, unit.shield + shCap * 0.1f * Time.delta / 60f);
            }
        }
        if (s[8] > 0 && isServer && unit.health > 0) {
            float regenAmt = unit.maxHealth * s[8] * REGEN_PCT / 100f * Time.delta / 60f;
            VanhauField vhF = vanhauOn ? vanhauField.get(pl.id) : null;
            if (vhF != null && vhF.hp > 0 && vhF.hp < vhF.max) vhF.hp = Math.min(vhF.max, vhF.hp + regenAmt);
            else if (unit.health < unit.maxHealth * REGEN_THRESHOLD) unit.health = Math.min(unit.maxHealth, unit.health + regenAmt);
        }
        if (s[10] > 0) unit.damageMultiplier *= 1 + s[10] * DMG_PER_ARMOR * unit.armor;
        if (eqc != null) {
            if (isPl && eqc.malign) {
                unit.speedMultiplier *= MALIGN_SPEED_MUL;
                unit.damageMultiplier *= 1 + MALIGN_ATK_PCT / 100f;
                unit.reloadMultiplier *= MALIGN_RELOAD_MUL;
            }
            if (eqc.nLu > 1) unit.damageMultiplier *= 1 + (eqc.nLu - 1) * LUSTRE_ATK_PCT / 100f;
            if (eqc.nSm > 0) unit.reloadMultiplier *= SMITE_RELOAD_MUL;
            if (eqc.nSm > 1) unit.damageMultiplier *= 1 + (eqc.nSm - 1) * SMITE_ATK_PCT / 100f;
            if (eqc.lightSpd) unit.speedMultiplier *= 1 + LIGHT_SPD_PCT / 100f;
        }
        if (isServer) {
            int uid = unit.id;
            if (eqc != null && eqc.hasTherdum && !unit.dead) {
                Float pv = thHp.get(uid);
                if (pv != null && pv - unit.health > THERDUM_HIT_CAP && unit.health > 0) unit.health = pv - THERDUM_HIT_CAP;
                thHp.put(uid, unit.health);
            } else thHp.remove(uid);
        }
        if (xakyOn && isServer) xakyCheck(unit, pl, stOwn);
        if (vanhauOn && isServer) vanhauCheck(unit, pl, stOwn);
    }

    // Trang thai: JS extend(StatusEffect,...) tao luc nap script -> Java tao trong loadContent() cua mod (de ten co tien to mod)
    public static StatusEffect buffStatus, equipStatus, spawnGuardStatus, spawnRageStatus;
    public static void loadStatuses() {
        buffStatus = new StatusEffect("pvp-buff") {
            @Override public void update(Unit unit, StatusEntry entry) { super.update(unit, entry); applyStats(unit); }
        };
        buffStatus.permanent = true; buffStatus.show = false;
        equipStatus = new StatusEffect("pvp-equip");
        equipStatus.permanent = true; equipStatus.show = false;
        spawnGuardStatus = new StatusEffect("pvp-spawn-guard");
        spawnGuardStatus.healthMultiplier = 1f / (1 - SPAWN_GUARD_DAMAGE_CUT / 100f);
        spawnGuardStatus.permanent = false; spawnGuardStatus.show = true;
        try { spawnGuardStatus.color = Color.valueOf("7fd8ff"); } catch (Throwable err) {}
        spawnRageStatus = new StatusEffect("pvp-spawn-rage");
        spawnRageStatus.permanent = false; spawnRageStatus.show = true;
        try { spawnRageStatus.color = Color.valueOf("ff5a5a"); } catch (Throwable err) {}
    }
    static float spawnLeft(int pid, float dur) {
        Float t = spawnBuff.get(pid);
        return t == null ? 0f : dur - (Time.time - t);
    }
    static boolean hasEquipped(St st) {
        if (st == null || st.d.size() == 0 || st.p.size() != st.d.size()) return false;
        droneDefs();
        EquipSum c = equipSummary(st);
        return c != null && c.hasEq;
    }
    static BulletType pierceType(BulletType t) {
        if (pierceCache.containsKey((int) t.id)) return pierceCache.get((int) t.id);
        BulletType c;
        try { c = t.copy(); c.pierceArmor = true; ensureLoaded(c); } catch (Throwable err) { c = null; }
        pierceCache.put((int) t.id, c);
        return c;
    }
    static void eachBullet(Bullet b) {
        if (b.time > 3) return;
        Entityc o = b.owner;
        if (o == null) return;
        if (rolled.get(b.id) != null) return;
        St st;
        boolean isUnitOwner = o instanceof Unit;
        if (isUnitOwner) st = statsStateOf((Unit) o);
        else { Integer hp = hostOwner.get(o.id()); if (hp == null) return; st = byId.get(hp); }
        int[] s = st != null ? st.s : null;
        if (s == null) return;
        rolled.put(b.id, true);
        rolledN++;
        if (isUnitOwner) {
            Unit ou = (Unit) o;
            boolean isSoldier = soldierOwner.containsKey(ou.id);
            boolean isPl = !isSoldier && (ou.isPlayer() || (bots.size() > 0 && botUnitOf(ou) != null));
            if (s[STAT_ARMY] > 0 && isSoldier) {
                float rangeMul = 1 + s[STAT_ARMY] * RANGE_PCT / 100f, lifeMul = 1 + s[STAT_ARMY] * LIFE_PCT / 100f;
                try { b.lifetime *= lifeMul; } catch (Throwable err) {}
                try { b.vel.scl(rangeMul); } catch (Throwable err) {}
            }
            if (isPl && malignEquipped(st)) { try { b.lifetime *= MALIGN_LIFE_MUL; } catch (Throwable err) {} }
            if (isPl && st.pi == PILOT_HIST) { try { b.lifetime *= 1 + HIST_STAT_PCT; } catch (Throwable err) {} }
        }
        if (s[2] > 0) { try { b.damage += s[2] * ATK_FLAT; } catch (Throwable err) {} }
        if (!isUnitOwner) {
            try {
                if (s[5] > 0 && Math.random() < s[5] * 0.02) b.damage *= 1.5f + s[6] * 0.12f;
                if (s[10] > 0) b.damage *= 1 + s[10] * DMG_PER_ARMOR * (s[1] * ARM_FLAT);
            } catch (Throwable err) {}
        }
        boolean histPierce = isUnitOwner && st.pi == PILOT_HIST;
        if ((s[7] > 0 || histPierce) && !b.type.pierceArmor && Math.random() < s[7] * 0.05 + (histPierce ? HIST_PIERCE_ADD : 0)) {
            BulletType c = pierceType(b.type);
            if (c != null) { try { b.type = c; } catch (Throwable err) {} }
        }
        if (s[11] > 0) {
            BulletType kt = knockType(b.type, s[11]);
            if (kt != null) { try { b.type = kt; } catch (Throwable err) {} }
        }
    }
    static boolean anyBulletStat() {
        for (St st : byId.values()) {
            int[] s = st.s;
            if (s[2] > 0 || s[5] > 0 || s[7] > 0 || s[10] > 0 || s[11] > 0) return true;
            if (malignEquipped(st)) return true;
            if (st.pi == PILOT_HIST) return true;
        }
        return false;
    }


    // ===== PHAN 7: vi tri hoi sinh, doi unit, chia doi, lock hoi sinh (JS 3467-3789) =====
    static final HashMap<Integer, Team> seenPl = new HashMap<>();
    static HashMap<String, Integer> assignCache = null;
    static final HashMap<Integer, Float> respawnLock = new HashMap<>();
    static final HashMap<Integer, Boolean> extPvp = new HashMap<>();
    static final class Pend { UnitType want; float t; }
    static final HashMap<Integer, Pend> swapPending = new HashMap<>();
    static ArrayList<UnitType> orphanTypes = null;
    static Boolean deathOk = null;

    static final class Pos { float x, y, rot; }
    static Pos coreSpawnPos(Player pl, UnitType type) { return corePosOf(pl.team().core(), type); }
    static Pos corePosOf(CoreBlock.CoreBuild core, UnitType type) {
        if (core == null) return null;
        float cx = Vars.world.width() * Vars.tilesize / 2f, cy = Vars.world.height() * Vars.tilesize / 2f;
        float a = Angles.angle(core.x, core.y, cx, cy) + (float) (Math.random() - 0.5) * 80;
        float r = core.block.size * Vars.tilesize / 2f + 28 + type.hitSize / 2;
        Pos p = new Pos();
        p.x = core.x + Mathf.cosDeg(a) * r; p.y = core.y + Mathf.sinDeg(a) * r; p.rot = a;
        return p;
    }
    static void swapUnit(Player pl, Unit old, UnitType want) {
        boolean fromTier = false;
        for (int lv = 1; lv <= TIER_MAX; lv++) if (old.type == tierType(lv)) { fromTier = true; break; }
        float x = old.x, y = old.y, rot = old.rotation, frac = 1;
        if (fromTier && old.team != pl.team()) fromTier = false;
        if (fromTier) frac = old.maxHealth > 0 ? old.health / old.maxHealth : 1f;
        else {
            Pos pos = coreSpawnPos(pl, want);
            if (pos != null) { x = pos.x; y = pos.y; rot = pos.rot; }
        }
        try { Call.unitDespawn(old); } catch (Throwable err) { old.remove(); }
        Unit nu = want.create(pl.team());
        nu.set(x, y);
        nu.rotation = rot;
        try { nu.spawnedByCore = !isBotPl(pl); } catch (Throwable err) {}
        nu.add();
        nu.health = nu.maxHealth * frac;
        pl.unit(nu);
        prevUnit.put(pl.id, nu);
        spawnFx(x, y);
    }
    static boolean onCoreZone(Unit unit) {
        if (BUILD_FLOOR.length() == 0) return true;
        try { Tile t = unit.tileOn(); return t != null && isCoreZoneFloor(t.floor()); } catch (Throwable err) {}
        return false;
    }
    static boolean canReturnCore(Unit pv, CoreBlock.CoreBuild core) {
        if (!returnZoneOn()) return true;
        if (!onCoreZone(pv)) return false;
        try { return ourSide(core, enemyCores(core.team), pv.tileX(), pv.tileY()); } catch (Throwable err) {}
        return false;
    }
    static boolean isPvpTeam(Team t) {
        Seq<Team> tl = pvpTeams();
        for (int i = 0; i < tl.size; i++) if (tl.get(i) == t) return true;
        return false;
    }
    static HashMap<String, Integer> getAssign() { if (assignCache == null) assignCache = new HashMap<>(); return assignCache; }
    static ArrayList<CoreBlock.CoreBuild> derelictCores() {
        final ArrayList<CoreBlock.CoreBuild> out = new ArrayList<>();
        try { Groups.build.each(bd -> { if (bd != null && bd.isValid() && bd.team == Team.derelict && bd.block instanceof CoreBlock) out.add((CoreBlock.CoreBuild) bd); }); } catch (Throwable err) {}
        if (out.size() == 0) {
            try { Seq<CoreBlock.CoreBuild> cs = Team.derelict.cores(); for (int i = 0; i < cs.size; i++) if (cs.get(i) != null) out.add(cs.get(i)); } catch (Throwable err) {}
        }
        return out;
    }
    static boolean teamFree(Team t) {
        if (t == Team.derelict || t == Team.crux || t == Vars.state.rules.waveTeam) return false;
        if (isPvpTeam(t)) return false;
        return t.cores().size == 0;
    }
    static Team freshTeam() {
        Team[] cand = {Team.green, Team.blue, Team.malis, Team.sharded};
        for (Team t : cand) if (teamFree(t)) return t;
        for (int id = 6; id < Team.all.length; id++) { Team t = Team.get(id); if (t != null && teamFree(t)) return t; }
        return null;
    }
    static float coreScore(CoreBlock.CoreBuild c, Seq<Team> tl) {
        float md = Float.POSITIVE_INFINITY;
        for (int i = 0; i < tl.size; i++) {
            Seq<CoreBlock.CoreBuild> cores = tl.get(i).cores();
            for (int j = 0; j < cores.size; j++) { CoreBlock.CoreBuild o = cores.get(j); if (o != null) md = Math.min(md, Mathf.dst(c.x, c.y, o.x, o.y)); }
        }
        return md == Float.POSITIVE_INFINITY ? 0f : md;
    }
    static Team claimCore() {
        ArrayList<CoreBlock.CoreBuild> cores = derelictCores();
        if (cores.size() == 0) return null;
        Team t = freshTeam();
        if (t == null) return null;
        Seq<Team> tl = pvpTeams();
        CoreBlock.CoreBuild best = null; float bd = -1;
        for (CoreBlock.CoreBuild c : cores) { float sc = coreScore(c, tl); if (sc > bd) { bd = sc; best = c; } }
        if (best == null) return null;
        Tile tile = best.tile; Block blk = best.block;
        try { tile.setNet(blk, t, 0); } catch (Throwable err) { return null; }
        Building nb = tile.build;
        if (nb == null || nb.team != t) return null;
        tl.add(t);
        normalizeRules(Vars.state.rules, new Team[]{t});
        syncRules();
        fx(nb.x, nb.y);
        return t;
    }
    static void evictRed() {
        Seq<CoreBlock.CoreBuild> cores = Team.crux.cores();
        if (cores == null || cores.size == 0) return;
        Seq<Team> tl = pvpTeams();
        Team target = null;
        for (int i = 0; i < tl.size; i++) if (tl.get(i).cores().size == 0) { target = tl.get(i); break; }
        boolean added = false;
        if (target == null) { target = freshTeam(); added = true; }
        if (target == null) return;
        ArrayList<CoreBlock.CoreBuild> list = new ArrayList<>();
        for (int j = 0; j < cores.size; j++) if (cores.get(j) != null) list.add(cores.get(j));
        for (CoreBlock.CoreBuild c : list) { Tile tile = c.tile; Block blk = c.block; try { tile.setNet(blk, target, 0); } catch (Throwable err) {} }
        if (added) tl.add(target);
        normalizeRules(Vars.state.rules, new Team[]{target});
    }
    static Team balanceTeam(Player pl) {
        Seq<Team> tl = pvpTeams();
        Team best = null; int bn = Integer.MAX_VALUE;
        for (int i = 0; i < tl.size; i++) {
            Team t = tl.get(i);
            int n = t.cores().size > 0 ? 0 : 1000;
            for (Player o : Groups.player) if (o != pl && o.team() == t) n++;
            if (n < bn) { bn = n; best = t; }
        }
        return best != null ? best : tl.get(0);
    }
    static void assignTeam(Player pl) {
        Team prev = seenPl.get(pl.id);
        if (prev != null) { if (pl.team() != prev) pl.team(prev); return; }
        HashMap<String, Integer> asg = getAssign();
        String uid = getUid(pl);
        Team team = null;
        boolean isHost = Vars.player != null && pl == Vars.player, claimed = false;
        boolean spec = bvbOn();
        boolean fromBot = !spec && !pvbOn() && bots.size() > 0 && bots.get(0).botPl.team().cores().size > 0;
        if (spec) team = Team.derelict;
        else if (pvbOn()) team = pvbHumanTeam();
        else if (fromBot) team = bots.get(0).botPl.team();
        else if (isHost) team = isPvpTeam(pl.team()) ? pl.team() : balanceTeam(pl);
        else {
            Integer sid = asg.get(uid);
            if (sid != null) { Team old = Team.get(sid); if (old != null && isPvpTeam(old) && old.cores().size > 0) team = old; }
            if (team == null) {
                team = claimCore();
                claimed = team != null;
                if (team == null) team = balanceTeam(pl);
            }
        }
        if (!spec) asg.put(uid, team.id);
        seenPl.put(pl.id, team);
        if (pl.team() != team) pl.team(team);
        if (spec) { try { Unit u0 = pl.unit(); if (u0 != null && !u0.dead) u0.remove(); } catch (Throwable err) {} }
        if (fromBot) replaceBot(pl);
        if (claimed) {
            String nm = String.valueOf(pl.name);
            toastAll("[#" + team.color + "]■[] [accent]" + nm + tr(" đã nhận lõi bỏ hoang, lập đội mới!", " claimed a derelict core and formed a new team!"), 4f);
        }
    }
    /** pvpTeams() gom doi cua bot; JS pvbHumanTeam() (JS 483-500). */
    static Team pvbHumanTeam() {
        Team bt = bots.size() > 0 ? bots.get(0).botPl.team() : null;
        Seq<Team> tl = pvpTeams();
        Team found = null;
        for (Player p : Groups.player) {
            if (found != null) break;
            Team t = seenPl.get(p.id);
            if (t != null && t != bt && isPvpTeam(t)) found = t;
        }
        if (found != null) return found;
        if (Vars.player != null && isPvpTeam(Vars.player.team()) && Vars.player.team() != bt) return Vars.player.team();
        for (int i = 0; i < tl.size; i++) if (tl.get(i) != bt && tl.get(i).cores().size > 0) return tl.get(i);
        return tl.get(0);
    }
    static void eachOrphan(Unit u) {
        if (!isPvpTeam(u.team)) return;
        if (u.isPlayer()) return;
        if (botUnitOf(u) != null) return;
        if (orphanTypes == null) { orphanTypes = new ArrayList<>(); for (int k = 1; k <= TIER_MAX; k++) orphanTypes.add(tierType(k)); }
        for (UnitType t : orphanTypes) if (u.type == t) { try { Call.unitDespawn(u); } catch (Throwable err) { u.remove(); } return; }
    }
    static void holdDeath(Player pl) {
        if (deathOk == null || deathOk) {
            try { pl.deathTimer = 0; deathOk = true; return; }
            catch (Throwable err) { deathOk = false; logOnce("deathtimer", "cannot hold player deathTimer, using despawn fallback: " + err, false); }
        }
        try { Unit u = pl.unit(); if (u != null && u.isValid()) { try { Call.unitDespawn(u); } catch (Throwable err) { u.remove(); } } } catch (Throwable err) {}
    }
    static void startRespawnLock(Player pl) {
        St st = bind(pl);
        int tier = activeTier(st);
        int sec = RESPAWN_SEC[Math.min(RESPAWN_SEC.length, Math.max(1, tier)) - 1];
        int ticks = Math.max(0, sec * 60 - 60);
        if (ticks <= 0) return;
        respawnLock.put(pl.id, (float) ticks);
        toast(pl, tr("[scarlet]Hồi sinh sau ", "[scarlet]Respawn in ") + sec + tr(" giây", " s"));
    }
    /** Map ngoai: bam "bien ve" lan 1 -> drone vanilla (xay dung), lan 2 -> unit PvP, lap lai. */
    static void extSwap(Player pl, St st, Unit u) {
        CoreBlock.CoreBuild cr = pl.team().core();
        boolean isCoreUnit = cr != null && u.type == ((CoreBlock) cr.block).unitType;
        boolean isTier = isTierUnit(u);
        Boolean m0 = extPvp.get(pl.id);
        boolean mode = m0 == null ? true : m0;
        Unit pv = prevUnit.get(pl.id);
        if (isTier) mode = true;
        else if (isCoreUnit && pv != null && pv != u && !pv.dead && pv.team == pl.team()) mode = !mode;
        extPvp.put(pl.id, mode);
        if (!mode || (!isTier && !isCoreUnit)) { swapPending.remove(pl.id); prevUnit.put(pl.id, u); return; }
        UnitType want = tierType(activeTier(st));
        if (u.type == want && u.team == pl.team()) { swapPending.remove(pl.id); prevUnit.put(pl.id, u); return; }
        Pend pend = swapPending.get(pl.id);
        if (pend != null && pend.want == want) { pend.t -= Time.delta; if (pend.t > 0) return; }
        Pend np = new Pend(); np.want = want; np.t = 10;
        swapPending.put(pl.id, np);
        swapUnit(pl, u, want);
    }


    // ===== PHAN 8: vong lap may chu moi nguoi choi, don dep, linh/trai linh (JS 3791-4087) =====
    static final HashMap<Integer, Integer> deadUnit = new HashMap<>();
    static final HashMap<Integer, ArrayList<Integer>> soldiers = new HashMap<>();
    static final HashMap<Integer, Float> barrackT = new HashMap<>();
    static float turretT = 0;

    /** JS eachPl(cons): nguoi choi that (bvb: bo qua doi derelict) + bot. */
    static void eachPl(Cons<Player> cons) {
        if (bvbOn()) Groups.player.each(p -> { if (p.team() != Team.derelict) cons.get(p); });
        else Groups.player.each(cons);
        for (int i = 0; i < bots.size(); i++) if (bots.get(i) != null) cons.get(bots.get(i).botPl);
    }

    static void eachPlayerServer(Player pl) {
        if (isFull()) assignTeam(pl);
        St st = bind(pl);
        Float lk = respawnLock.get(pl.id);
        if (lk != null) {
            lk -= Time.delta;
            if (lk <= 0) respawnLock.remove(pl.id);
            else { respawnLock.put(pl.id, lk); holdDeath(pl); return; }
        }
        Unit u = pl.unit();
        if (u == null || !u.isValid()) return;
        if (!isFull()) { extSwap(pl, st, u); return; }
        Integer dead = deadUnit.get(pl.id);
        if (dead != null && u.id != dead && !u.dead) { deadUnit.remove(pl.id); spawnBuff.put(pl.id, Time.time); }
        float gl = spawnLeft(pl.id, SPAWN_GUARD_TICKS);
        if (gl > 0) { if (!u.hasEffect(spawnGuardStatus)) u.apply(spawnGuardStatus, gl); }
        else if (spawnBuff.get(pl.id) != null && spawnLeft(pl.id, SPAWN_RAGE_TICKS) <= 0) spawnBuff.remove(pl.id);
        if (spawnLeft(pl.id, SPAWN_RAGE_TICKS) > 0) { if (!u.hasEffect(spawnRageStatus)) u.apply(spawnRageStatus, spawnLeft(pl.id, SPAWN_RAGE_TICKS)); }
        UnitType want = tierType(activeTier(st));
        Unit pv = prevUnit.get(pl.id);
        if (pv != null && pv != u) {
            try {
                CoreBlock.CoreBuild cr0 = pl.team().core();
                boolean toCore = cr0 != null && u.type == ((CoreBlock) cr0.block).unitType;
                if (toCore && pv.isValid() && !pv.dead && pv.team == pl.team() && !canReturnCore(pv, cr0)) {
                    try { Call.unitDespawn(u); } catch (Throwable err) { u.remove(); }
                    pl.unit(pv);
                    toast(pl, tr("[scarlet]Chỉ về lõi được khi đứng trên nền đặt lõi ở nửa map phe ta!", "[scarlet]You can only return to core on core-zone floor on your half of the map!"));
                    return;
                }
            } catch (Throwable err) {}
        }
        if (u.type == want && u.team == pl.team()) { swapPending.remove(pl.id); prevUnit.put(pl.id, u); return; }
        boolean isTier = false;
        for (int lv = 1; lv <= TIER_MAX; lv++) if (u.type == tierType(lv)) { isTier = true; break; }
        boolean isCoreUnit = false;
        try { CoreBlock.CoreBuild cr = pl.team().core(); isCoreUnit = cr != null && u.type == ((CoreBlock) cr.block).unitType; } catch (Throwable err) {}
        if (!isTier && !isCoreUnit) {
            swapPending.remove(pl.id);
            if (pv != null && pv != u && pv.isValid() && !pv.dead && pv.team == pl.team()) {
                pl.unit(pv);
                toast(pl, tr("[scarlet]Không được điều khiển pháo hoặc lính!", "[scarlet]You cannot control turrets or soldiers!"));
            }
            return;
        }
        Pend pend = swapPending.get(pl.id);
        if (pend != null && pend.want == want) { pend.t -= Time.delta; if (pend.t > 0) return; }
        Pend np = new Pend(); np.want = want; np.t = 10;
        swapPending.put(pl.id, np);
        swapUnit(pl, u, want);
    }

    static HashMap<Integer, int[]> coreBest = null;
    static void coreStatsTick(float dt) {
        final HashMap<Integer, int[]> best = new HashMap<>();
        coreBest = best;
        eachPl(pl -> {
            St st = byId.get(pl.id);
            if (st == null) return;
            Team tm = pl.team();
            if (tm == null || tm == Team.derelict) return;
            // CONG DON chi so cua moi nguoi choi (va bot) cung phe: nhieu nguoi vao host thi loi huong tong chi so da nang cua ho.
            int[] b = best.get(tm.id);
            if (b == null) best.put(tm.id, st.s.clone());
            else for (int i = 0; i < st.s.length && i < b.length; i++) b[i] += st.s[i];
        });
        // Doi khong con nguoi choi/bot nao: tra he so khoi/loi ve mac dinh.
        Seq<Team> allTeams = pvpTeams();
        for (int ti = 0; ti < allTeams.size; ti++) {
            Team t0 = allTeams.get(ti);
            if (best.containsKey(t0.id)) continue;
            try {
                mindustry.game.Rules.TeamRule r0 = Vars.state.rules.teams.get(t0);
                r0.blockHealthMultiplier = 1f; r0.blockDamageMultiplier = 1f;
            } catch (Throwable err) {}
        }
        for (Map.Entry<Integer, int[]> e : best.entrySet()) {
            try {
                Team team = Team.get(e.getKey());
                int[] sv = e.getValue();
                mindustry.game.Rules.TeamRule rule = Vars.state.rules.teams.get(team);
                float hpMul = (1 + sv[0] * HP_PCT / 100f) / (1 - armorCut(sv[1]) / 100f);
                rule.blockHealthMultiplier = hpMul;
                rule.blockDamageMultiplier = 1 + sv[2] * ATK_PCT / 100f;
                if (sv[8] > 0) {
                    Seq<CoreBlock.CoreBuild> cores = team.cores();
                    for (int i = 0; i < cores.size; i++) {
                        CoreBlock.CoreBuild cb = cores.get(i);
                        if (cb == null || !cb.isValid()) continue;
                        float mh = cb.maxHealth();
                        if (cb.health < mh * REGEN_THRESHOLD) cb.heal(mh * sv[8] * REGEN_PCT / 100f * dt / 60f);
                    }
                }
            } catch (Throwable err) { logOnce("corestats", "core stats failed: " + err, false); }
        }
    }
    static void eachPassiveExp(Player pl) { addExp(pl, PASSIVE_EXP, true); }
    static void eachPlayerCommon(Player pl) {
        if (!pvpOn(pl)) return;
        Unit u = pl.unit();
        if (u == null || !u.isValid()) return;
        if (!u.hasEffect(buffStatus)) u.apply(buffStatus, 1f);
        if (u.hasEffect(equipStatus)) u.unapply(equipStatus);
    }
    static void eachTurretOwner(Player pl) {
        St st = byId.get(pl.id);
        if (st == null || st.d.size() == 0) return;
        feedTurrets(pl, st, turretT <= 0, !Vars.net.client());
    }

    static int keyPid(String key) { Integer v = leadInt(key.split(":")[0]); return v == null ? Integer.MIN_VALUE : v; }
    static void prune() {
        final java.util.HashSet<Integer> present = new java.util.HashSet<>();
        eachPl(pl -> present.add(pl.id));
        byId.keySet().removeIf(id -> !present.contains(id));
        seenPl.keySet().removeIf(id -> !present.contains(id));
        equipAng.keySet().removeIf(k -> !present.contains(keyPid(k)));
        equipBeam.keySet().removeIf(k -> !present.contains(keyPid(k)));
        deadUnit.keySet().removeIf(id -> !present.contains(id));
        spawnBuff.keySet().removeIf(id -> !present.contains(id));
        swapPending.keySet().removeIf(id -> !present.contains(id));
        for (Integer sid : new ArrayList<>(soldiers.keySet())) {
            if (!present.contains(sid)) {
                if (!Vars.net.client()) removeSoldiers(sid);
                soldiers.remove(sid);
                barrackT.remove(sid);
            }
        }
        mountSt.keySet().removeIf(k -> !present.contains(keyPid(k)));
        if (rolledN > 3000) { rolled.clear(); rolledN = 0; }
        final float nowT = Time.time;
        prevUnit.keySet().removeIf(id -> !present.contains(id));
        extPvp.keySet().removeIf(id -> !present.contains(id));
        pilotLastLv.keySet().removeIf(id -> !present.contains(id));
        pilotFxAt.keySet().removeIf(id -> !present.contains(id));
        xakyPrev.keySet().removeIf(id -> !present.contains(id));
        teamHit.values().removeIf(h -> nowT - h.t > KILL_CREDIT_TICKS);
        lastHitBy.values().removeIf(h -> nowT - h.t > KILL_CREDIT_TICKS);
        soldierAI.keySet().removeIf(k -> Groups.unit.getByID(k) == null);
        thHp.keySet().removeIf(k -> { Unit u4 = Groups.unit.getByID(k); return u4 == null || u4.dead; });
        playerPrioT.keySet().removeIf(k -> Vars.world.build(k) == null);
        neoMarks.values().removeIf(m -> nowT - m.t > 900);
    }

    static ArrayList<Integer> aliveSoldiers(int pid) {
        ArrayList<Integer> list = soldiers.get(pid), out = new ArrayList<>();
        if (list != null) {
            for (Integer id : list) {
                Unit u = Groups.unit.getByID(id);
                if (u != null && u.isValid() && !u.dead) { out.add(id); if (!u.hasEffect(buffStatus)) u.apply(buffStatus, 1f); }
                else soldierOwner.remove(id);
            }
        }
        soldiers.put(pid, out);
        return out;
    }
    static boolean soldierWallBetween(Unit u, Teamc tg) {
        try {
            if (u.isFlying()) return false;
            float d = Mathf.dst(u.x, u.y, tg.getX(), tg.getY());
            for (float l = 12; l < d - 16; l += 8) {
                float f = l / d;
                Tile t = Vars.world.tileWorld(u.x + (tg.getX() - u.x) * f, u.y + (tg.getY() - u.y) * f);
                if (t != null && t.solid() && (t.build == null || (t.build != tg && t.build.team != u.team))) return true;
            }
        } catch (Throwable err) {}
        return false;
    }
    static GroundAI groundAI() { try { return new GroundAI(); } catch (Throwable err) { return null; } }
    static Unit spawnSoldier(Player pl, UnitType type) {
        Pos pos = coreSpawnPos(pl, type);
        if (pos == null) return null;
        prepareWaveType(type);
        Unit nu = type.create(pl.team());
        nu.set(pos.x, pos.y);
        nu.rotation = pos.rot;
        GroundAI ai = groundAI();
        if (ai != null) { try { nu.controller(ai); } catch (Throwable err) {} }
        nu.add();
        soldierOwner.put(nu.id, pl.id);
        nu.apply(buffStatus, 1f);
        spawnFx(pos.x, pos.y);
        return nu;
    }
    static void removeSoldiers(int pid) {
        ArrayList<Integer> list = soldiers.get(pid);
        if (list == null) return;
        for (Integer id : list) {
            Unit u = Groups.unit.getByID(id);
            if (u == null) continue;
            try { Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable err2) {} }
        }
    }
    static void eachBarracks(Player pl) {
        St st = byId.get(pl.id);
        if (st == null || !(st.b >= 1)) return;
        Barrack cfg = BARRACKS[Math.min(st.b, BARRACKS.length) - 1];
        UnitType type = Vars.content.getByName(ContentType.unit, cfg.t);
        if (type == null || pl.team().core() == null) return;
        Float t0 = barrackT.get(pl.id);
        float t = t0 == null ? cfg.sec * 60f : t0;
        t -= Time.delta;
        if (t > 0) { barrackT.put(pl.id, t); return; }
        ArrayList<Integer> alive = aliveSoldiers(pl.id);
        int room = cfg.cap + wallBonus(st) - alive.size();
        if (room <= 0) { barrackT.put(pl.id, 0f); return; }
        int n = Math.min(cfg.n, room);
        for (int i = 0; i < n; i++) { Unit nu = spawnSoldier(pl, type); if (nu != null) alive.add(nu.id); }
        soldiers.put(pl.id, alive);
        barrackT.put(pl.id, cfg.sec * 60f);
    }


    // ===== PHAN 9: AI linh, tuong, dieu kien thang (JS 4090-4280) =====
    static final int BOT_ID = -4242, BOT_SPOT_TRIES = 400, BOT_SPOT_RANGE = 80;
    static final float BOT_LEASH = 3200, BOT_HOME_R = 110;
    static boolean botNoTurret(String kind) { return kind.equals("tractor") || kind.equals("point"); }
    static final Vec2 botDest = new Vec2(), botGoal = new Vec2();
    static boolean winning = false, seenBoth = false;

    static final class Rally { float x, y; Integer uid, bpos; }
    static final HashMap<Integer, Rally> rally = new HashMap<>();
    static final class Anch { float x, y; Anch(float x, float y) { this.x = x; this.y = y; } }
    static final class SAI {
        float t, cmdT, rT, hp = Float.NaN, hurt, cgT, wbT, aimT, pT, lx = Float.NaN, ly = Float.NaN, r0 = Float.NaN, px = Float.NaN, py = Float.NaN;
        int approachLv; boolean hold, isCmd, wb; String cmdKey; Teamc tg, cg, tgRef;
    }
    static final HashMap<Integer, SAI> soldierAI = new HashMap<>();

    /** JS Vars.controlPath.getPathPosition(u, goal, goal) tra object {unreachable, move, dest}; API v157 chua kiem -> phan xa, null neu khong ro. */
    static java.lang.reflect.Method pathM; static boolean pathMTried;
    static float[] pathStep(Unit u, Vec2 goal) {
        try {
            if (!pathMTried) {
                pathMTried = true;
                for (java.lang.reflect.Method m : Vars.controlPath.getClass().getMethods())
                    if (m.getName().equals("getPathPosition") && m.getParameterCount() == 3) { pathM = m; break; }
            }
            if (pathM == null) return null;
            Object r = pathM.invoke(Vars.controlPath, u, goal, goal);
            if (r == null || fldB(r, "unreachable", false)) return null;
            if (fldB(r, "move", false)) { Object d = fld(r, "dest", null); if (d instanceof Vec2) return new float[]{((Vec2) d).x, ((Vec2) d).y}; }
            return new float[]{u.x, u.y};
        } catch (Throwable err) { return null; }
    }
    static void soldierGo(Unit u, SAI s, float tx, float ty, float stopDist) {
        Vec2 v = Tmp.v1;
        if (Mathf.dst(u.x, u.y, tx, ty) <= stopDist) { v.setZero(); u.movePref(v); return; }
        s.pT -= Time.delta;
        if (s.pT <= 0 || Float.isNaN(s.px)) {
            s.pT = 10 + (float) Math.floor(Math.random() * 4);
            s.px = tx; s.py = ty;
            botGoal.set(tx, ty);
            float[] r = pathStep(u, botGoal);
            if (r != null) { s.px = r[0]; s.py = r[1]; }
        }
        v.set(s.px - u.x, s.py - u.y);
        if (v.len() > 0.1f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
    }
    static void eachSoldierAI(Player pl) {
        ArrayList<Integer> list = soldiers.get(pl.id);
        if (list == null) return;
        St stO = byId.get(pl.id);
        boolean cmdOn = stO != null && stO.cmd == 1, hasRally = false;
        Anch anchor = null; Teamc atk = null;
        if (cmdOn) {
            Rally rl = rally.get(pl.id);
            hasRally = rl != null;
            if (rl != null) anchor = new Anch(rl.x, rl.y);
            if (rl != null && (rl.uid != null || rl.bpos != null)) {
                Teamc tu2 = rl.uid != null ? Groups.unit.getByID(rl.uid) : Vars.world.build(rl.bpos);
                if (tu2 != null && entValid(tu2) && !(tu2 instanceof Unit && ((Unit) tu2).dead) && tu2.team() != pl.team()) { anchor = new Anch(tu2.getX(), tu2.getY()); atk = tu2; }
                else { rally.remove(pl.id); anchor = null; }
            }
            if (anchor == null) { Unit ou = pl.unit(); if (ou != null && ou.isValid() && !ou.dead) anchor = new Anch(ou.x, ou.y); }
        }
        for (int i = 0; i < list.size(); i++) {
            Unit u = Groups.unit.getByID(list.get(i));
            if (u == null || !u.isValid() || u.dead) { soldierAI.remove(list.get(i)); continue; }
            try {
                SAI s = soldierAI.get(u.id);
                if (s == null) { s = new SAI(); s.t = (float) Math.floor(Math.random() * 10); soldierAI.put(u.id, s); }
                if (cmdOn) {
                    CommandAI ai = s.isCmd && u.controller() instanceof CommandAI ? (CommandAI) u.controller() : null;
                    if (ai == null) { ai = new CommandAI(); u.controller(ai); s.isCmd = true; s.hold = false; s.cmdKey = null; s.cmdT = 0; }
                    if (anchor != null) {
                        String key = atk != null ? "a" + atk.id() : (hasRally ? "p" + Math.round(anchor.x) + "," + Math.round(anchor.y) : "f");
                        s.cmdT -= Time.delta;
                        if (!key.equals(s.cmdKey) || (key.equals("f") && s.cmdT <= 0 && (Float.isNaN(s.lx) || Mathf.dst(anchor.x, anchor.y, s.lx, s.ly) > 64))) {
                            s.cmdKey = key;
                            s.cmdT = 45 + (float) Math.floor(Math.random() * 30);
                            s.lx = anchor.x; s.ly = anchor.y;
                            if (atk != null) ai.commandTarget(atk, false);
                            else if (key.equals("f") && Mathf.dst(u.x, u.y, anchor.x, anchor.y) <= 110) ai.clearCommands();
                            else ai.commandPosition(Tmp.v2.set(anchor.x, anchor.y));
                        }
                    }
                    continue;
                }
                if (s.isCmd) { s.isCmd = false; s.hold = false; GroundAI g0 = groundAI(); if (g0 != null) u.controller(g0); }
                s.rT -= Time.delta;
                if (s.rT <= 0 || Float.isNaN(s.r0)) { s.rT = 60; s.r0 = u.range(); }
                float rng = s.r0;
                Integer oid = soldierOwner.get(u.id);
                St sst = oid != null ? byId.get(oid) : null;
                if (sst != null && sst.s[STAT_ARMY] > 0) rng *= 1 + sst.s[STAT_ARMY] * RANGE_PCT / 100f;
                if (!Float.isNaN(s.hp) && u.health < s.hp - 0.01f) s.hurt = 300;
                s.hp = u.health;
                if (s.hurt > 0) {
                    s.hurt -= Time.delta;
                    s.cgT -= Time.delta;
                    if (s.cg != null && Units.invalidateTarget(s.cg, u.team, u.x, u.y)) s.cg = null;
                    if (s.cg == null && s.cgT <= 0) {
                        s.cgT = 10;
                        s.cg = Units.closestTarget(u.team, u.x, u.y, Math.max(rng * 2.5f, 480f), e -> true, b -> true);
                    }
                    if (s.cg != null) {
                        if (!s.hold) { s.hold = true; u.controller(new NoAI()); }
                        float cgx = s.cg.getX(), cgy = s.cg.getY(), cd = Mathf.dst(u.x, u.y, cgx, cgy);
                        u.lookAt(cgx, cgy);
                        u.aim(cgx, cgy);
                        u.controlWeapons(true, true);
                        s.wbT -= Time.delta;
                        if (s.wbT <= 0) { s.wbT = 6; s.wb = soldierWallBetween(u, s.cg); }
                        if (s.wb) u.movePref(Tmp.v1.setZero());
                        else {
                            if (cd > rng * 0.85f) soldierGo(u, s, cgx, cgy, rng * 0.75f);
                            else if (cd < rng * 0.55f && cd > 1) soldierGo(u, s, cgx + (u.x - cgx) / cd * rng * 0.7f, cgy + (u.y - cgy) / cd * rng * 0.7f, 8f);
                            else u.movePref(Tmp.v1.setZero());
                        }
                        continue;
                    }
                }
                if (s.tg != null && Units.invalidateTarget(s.tg, u.team, u.x, u.y, rng)) s.tg = null;
                s.t -= Time.delta;
                if (s.tg == null && s.t <= 0) {
                    s.t = 10;
                    s.tg = Units.closestTarget(u.team, u.x, u.y, rng, e -> true, b -> true);
                }
                if (s.tg != null) {
                    if (!s.hold) { s.hold = true; u.controller(new NoAI()); }
                    if (s.tgRef != s.tg) { s.tgRef = s.tg; s.aimT = 0; s.approachLv = 0; }
                    float tgx = s.tg.getX(), tgy = s.tg.getY();
                    u.lookAt(tgx, tgy);
                    u.aim(tgx, tgy);
                    u.controlWeapons(true, true);
                    s.aimT += Time.delta;
                    if (s.aimT > 180 && s.approachLv < 2) { s.aimT = 0; s.approachLv++; }
                    if (s.approachLv > 0) {
                        float closeDist = rng * (s.approachLv == 1 ? 0.5f : 0.22f);
                        if (Mathf.dst(u.x, u.y, tgx, tgy) > closeDist) soldierGo(u, s, tgx, tgy, closeDist);
                        else u.movePref(Tmp.v1.setZero());
                    }
                } else if (cmdOn) {
                    if (!s.hold) { s.hold = true; u.controller(new NoAI()); }
                    u.controlWeapons(false, false);
                    if (anchor != null && Mathf.dst(u.x, u.y, anchor.x, anchor.y) > 110) soldierGo(u, s, anchor.x, anchor.y, 90f);
                    else u.movePref(Tmp.v1.setZero());
                } else if (s.hold) {
                    s.hold = false;
                    u.controlWeapons(false, false);
                    GroundAI ai = groundAI();
                    if (ai != null) u.controller(ai);
                }
            } catch (Throwable err) { logOnce("soldierai", "soldier AI failed: " + err, false); }
        }
    }
    static int placeWall(Player pl, int lv, int tx, int ty) {
        CoreBlock.CoreBuild core = pl.team().core();
        Block blk = wallBlock(lv);
        if (core == null || blk == null) return -1;
        if (!canBuildAt(core, enemyCores(core.team), blk, tx, ty)) return -1;
        Tile tile = Vars.world.tile(tx, ty);
        if (tile == null) return -1;
        tile.setNet(blk, pl.team(), 0);
        Building b = tile.build;
        if (b == null || b.block != blk) return -1;
        fx(b.x, b.y);
        return tile.pos();
    }
    static void eachWall(Player pl) {
        St st = byId.get(pl.id);
        if (st == null || !(st.w >= 0)) return;
        Block blk = wallBlock(st.wl);
        Building b = Vars.world.build(st.w);
        if (b == null || !b.isValid() || b.block != blk || b.team != pl.team()) {
            st.w = IN_BAG;
            toast(pl, tr("[scarlet]Khối tường bị phá, đã về túi đồ.", "[scarlet]Wall destroyed, returned to your bag."));
            changed();
        }
    }
    static void checkWin() {
        if (winning || Vars.state.gameOver) return;
        Seq<Team> tl = pvpTeams();
        ArrayList<Team> alive = new ArrayList<>();
        for (int i = 0; i < tl.size; i++) if (tl.get(i).cores().size > 0) alive.add(tl.get(i));
        if (alive.size() >= 2) seenBoth = true;
        if (!seenBoth) return;
        if (alive.size() == 1) win(alive.get(0));
    }
    static void win(Team team) {
        winning = true;
        Time.run(5f, () -> {
            try { Vars.logic.gameOver(team); }
            catch (Throwable err) { Vars.state.gameOver = true; Events.fire(new GameOverEvent(team)); }
        });
    }


    // ===== PHAN 10: Bot A.I (1) - nguoi choi gia, tao/xoa/thay bot, chon muc tieu, di chuyen, ban/hoc pháo (JS 4303-4684) =====
    /** JS makeBotPl: object gia {id,name,admin,shooting,deathTimer,team(),unit(),uuid()} -> lop con cua Player (khong bao gio add vao Groups). */
    static final class BotPlayer extends Player {
        Team _team; Unit _unit;
        @Override public Team team() { return _team; }
        @Override public void team(Team t) { _team = t; }
        @Override public Unit unit() { return _unit; }
        @Override public void unit(Unit x) {
            _unit = x;
            if (x != null) { try { x.controller(new NoAI()); } catch (Throwable err) {} }
        }
        @Override public String uuid() { return "bot:" + id; }
    }
    static Player makeBotPl(Team team, int id, int k) {
        BotPlayer o = new BotPlayer();
        o.id = id;
        o.name = "[orange]BOT[] NexBot" + (k > 0 ? " " + (k + 1) : "");
        o.admin = false; o.shooting = false; o.deathTimer = 0; o._team = team; o._unit = null;
        return o;
    }
    static boolean plPending() {
        for (Player p : Groups.player) if (!seenPl.containsKey(p.id)) return true;
        return false;
    }
    static Team botTeam() {
        if (plPending()) return null;
        Seq<Team> tl = pvpTeams();
        for (int i = 0; i < tl.size; i++) {
            Team t = tl.get(i);
            if (t.cores().size == 0) continue;
            boolean human = false;
            for (Player p : Groups.player) if (p.team() == t) { human = true; break; }
            if (!human) return t;
        }
        return null;
    }
    static void wipeBuilt(St st) {
        Defs defs = droneDefs();
        for (int i = 0; i < st.p.size(); i++) if (st.p.get(i) >= 0) removeTurret(st.p.get(i), defs.map.get(st.d.get(i)));
    }
    static void createBot(Team team) {
        int k = 0;
        while (botOf(BOT_ID - k) != null) k++;
        BotCtx ctx = new BotCtx();
        ctx.botPl = makeBotPl(team, BOT_ID - k, k);
        bots.add(ctx);
        Player pl = ctx.botPl;
        seenPl.put(pl.id, team);
        St st = bind(pl);
        int[] pils = {PILOT_XIKI, PILOT_XAKY, PILOT_HIST};
        st.pi = pils[k % pils.length];
        St src = null;
        for (int i = 0; i < bots.size(); i++) {
            if (bots.get(i) == ctx || bots.get(i).botPl.team() != team) continue;
            St s0 = byId.get(bots.get(i).botPl.id);
            if (s0 != null && (src == null || s0.lv > src.lv)) src = s0;
        }
        if (src != null) { st.lv = src.lv; st.s = src.s.clone(); st.b = src.b; st.pts = (int) Math.floor(src.pts * 0.5); changed(); }
        if (bots.size() == 1) toastAll("[#" + team.color + "]■[] " + tr("[accent]Bot NexBot giữ chỗ đối thủ - có người vào sẽ nhường chỗ.", "[accent]NexBot holds the opponent slot - it leaves when a player joins."), 4f);
    }
    static void removeBot(boolean wipe, BotCtx ctx) {
        if (ctx == null) ctx = BC;
        Player p = ctx.botPl;
        if (p == null) return;
        bots.remove(ctx);
        St st = byId.get(p.id);
        if (wipe && st != null) { try { wipeBuilt(st); } catch (Throwable err) {} }
        Unit u = ((BotPlayer) p)._unit;
        try { if (u != null && u.isValid()) Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable err2) {} }
        byId.remove(p.id); seenPl.remove(p.id); prevUnit.remove(p.id); store.remove(p.uuid());
        if (BC == ctx) BC = new BotCtx();
        changed();
    }
    static void replaceBot(Player pl) {
        if (bots.size() == 0) return;
        BotCtx ctx = bots.get(0);
        Player bp = ctx.botPl;
        St bst = byId.get(bp.id), hst = bind(pl);
        boolean inherit = bst != null && hst.lv <= 1 && hst.exp <= 0 && hst.d.size() == 0;
        if (inherit) {
            byId.put(pl.id, bst);
            store.put(getUid(pl), bst);
            for (int i = 0; i < bst.p.size(); i++) {
                if (!(bst.p.get(i) >= 0)) continue;
                Building b = Vars.world.build(bst.p.get(i));
                if (b != null) hostOwner.put(b.id, pl.id);
            }
        }
        removeBot(!inherit, ctx);
    }
    static ArrayList<Team> botDesired() {
        ArrayList<Team> out = new ArrayList<>();
        Seq<Team> tl = pvpTeams();
        if (bvbOn()) { for (int i = 0; i < tl.size; i++) if (tl.get(i).cores().size > 0) out.add(tl.get(i)); return out; }
        int n = Groups.player.size();
        if (n < 1) return out;
        if (pvbOn()) {
            Team bt = bots.size() > 0 ? bots.get(0).botPl.team() : null;
            if (bt == null || bt.cores().size == 0) {
                Team ht = pvbHumanTeam();
                bt = null;
                for (int i = 0; i < tl.size; i++) if (tl.get(i) != ht && tl.get(i).cores().size > 0) { bt = tl.get(i); break; }
                if (bt == null) bt = claimCore();
            }
            if (bt != null) for (int i = 0; i < Math.min(n, BOT_MAX); i++) out.add(bt);
            return out;
        }
        if (n != 1) return out;
        if (bots.size() > 0) { out.add(bots.get(0).botPl.team()); return out; }
        Team t = botTeam();
        if (t == null) t = claimCore();
        if (t == null) logOnce("botnoteam", "bot: không có đội/lõi trống để đặt bot (cores: " + derelictCores().size() + ")", true);
        else out.add(t);
        return out;
    }
    static void botManage() {
        if (!isFull() || Vars.state.gameOver || winning) { logOnce("botinactive", "bot: chế độ chưa active (isFull=" + isFull() + ")", true); return; }
        if (plPending()) return;
        ArrayList<Team> want = botDesired();
        HashMap<Integer, Integer> need = new HashMap<>();
        for (Team t : want) need.merge(t.id, 1, Integer::sum);
        for (int i = bots.size() - 1; i >= 0; i--) {
            Team t = bots.get(i).botPl.team();
            Integer c = need.get(t.id);
            if (c != null && c > 0) need.put(t.id, c - 1); else removeBot(true, bots.get(i));
        }
        for (Map.Entry<Integer, Integer> e : need.entrySet()) {
            Team tm = Team.get(e.getKey());
            for (int k = 0; k < e.getValue(); k++) { createBot(tm); logOnce("botmade" + tm.id + "_" + k, "bot đã vào đội " + tm.name, true); }
        }
    }

    static final float BOT_PLAYER_AGGRO = 900;
    static boolean botEnemyOk(Unit e, Unit u) { return e != null && e != BC.botSkip && e.isValid() && !e.dead && e.team != u.team; }
    static final class Pw { Unit u, best; CoreBlock.CoreBuild core; Team wt; float bd, pr2; }
    static Unit pickWave(Unit u, CoreBlock.CoreBuild core, float pr) {
        final Pw c = new Pw();
        c.u = u; c.core = core; c.wt = Vars.state.rules.waveTeam; c.best = null; c.bd = BOT_LEASH * BOT_LEASH;
        Groups.unit.each(e -> {
            if (!botEnemyOk(e, c.u)) return;
            boolean isWave = e.team == c.wt, isSold = !isWave && soldierOwner.containsKey(e.id);
            if (!isWave && !isSold) return;
            if (Mathf.dst2(c.core.x, c.core.y, e.x, e.y) > BOT_LEASH * BOT_LEASH) return;
            float d = Mathf.dst2(c.u.x, c.u.y, e.x, e.y);
            if (isSold) { if (d > BOT_PLAYER_AGGRO * BOT_PLAYER_AGGRO) return; d *= 0.6f; }
            d *= 0.5f + 0.5f * Math.max(0f, Math.min(1f, e.health / Math.max(1f, e.maxHealth)));
            if (d < c.bd) { c.bd = d; c.best = e; }
        });
        float pr0 = pr > 0 ? pr : BOT_PLAYER_AGGRO;
        c.pr2 = pr0 * pr0;
        eachPl(p -> {
            Unit e = p.unit();
            if (e == null || !botEnemyOk(e, c.u) || e.team == c.wt) return;
            float d = Mathf.dst2(c.u.x, c.u.y, e.x, e.y);
            if (d <= c.pr2 && d < c.bd) { c.bd = d; c.best = e; }
        });
        return c.best;
    }
    static boolean botUnfavorable(Unit u) {
        float hp = u.health + u.shield;
        if (u.health / u.maxHealth < 0.3f) return true;
        float myDps = Math.max(1f, u.type.estimateDps());
        final float[] acc = new float[3];
        Groups.unit.each(e -> {
            if (e.team == u.team || e.dead || !e.isValid() || Mathf.dst2(u.x, u.y, e.x, e.y) > 450 * 450) return;
            if (e.team == Team.derelict) return;
            acc[0] += e.health + e.shield; acc[1] += Math.max(1f, e.type.estimateDps()); acc[2]++;
        });
        if (acc[2] == 0) return false;
        float tDie = hp / acc[1], tKill = acc[0] / myDps;
        return tDie < tKill * 0.8f;
    }
    /** Ket qua getPathPosition (API v157 chua kiem; phan xa). null = khong goi duoc. */
    static final class PathRes { boolean unreachable, move; float dx, dy; }
    static PathRes pathQuery(Unit u, Vec2 goal) {
        try {
            if (!pathMTried) { pathMTried = true; for (java.lang.reflect.Method m : Vars.controlPath.getClass().getMethods()) if (m.getName().equals("getPathPosition") && m.getParameterCount() == 3) { pathM = m; break; } }
            if (pathM == null) return null;
            Object r = pathM.invoke(Vars.controlPath, u, goal, goal);
            if (r == null) return null;
            PathRes p = new PathRes();
            p.unreachable = fldB(r, "unreachable", false); p.move = fldB(r, "move", false);
            Object d = fld(r, "dest", null);
            if (d instanceof Vec2) { p.dx = ((Vec2) d).x; p.dy = ((Vec2) d).y; }
            return p;
        } catch (Throwable err) { logOnce("botpath", "controlPath lỗi, dùng đi thẳng: " + err, false); return null; }
    }
    static boolean botGo(Unit u, float tx, float ty, float stopDist) {
        Vec2 v = Tmp.v1;
        float dist = Mathf.dst(u.x, u.y, tx, ty);
        if (dist <= stopDist) { v.setZero(); u.movePref(v); return true; }
        BC.botPathT -= Time.delta;
        boolean needQuery = BC.botPathT <= 0 || Float.isNaN(BC.botPathDx) || Math.abs(tx - BC.botPathGx) > 24 || Math.abs(ty - BC.botPathGy) > 24;
        if (needQuery) {
            BC.botPathT = 6; BC.botPathGx = tx; BC.botPathGy = ty; BC.botPathOk = true; BC.botPathWait = false; BC.botPathDx = tx; BC.botPathDy = ty;
            botGoal.set(tx, ty);
            PathRes r = pathQuery(u, botGoal);
            if (r != null) {
                if (r.unreachable) BC.botPathOk = false;
                else if (r.move) { BC.botPathDx = r.dx; BC.botPathDy = r.dy; }
                else BC.botPathWait = true;
            }
        }
        if (BC.botPathWait) { v.setZero(); u.movePref(v); return true; }
        v.set(BC.botPathDx - u.x, BC.botPathDy - u.y);
        if (v.len() > 0.1f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
        return BC.botPathOk;
    }
    static void spawnBotUnit() {
        CoreBlock.CoreBuild core = BC.botPl.team().core();
        if (core == null) return;
        St st = bind(BC.botPl);
        UnitType t = tierType(activeTier(st));
        Pos pos = corePosOf(core, t);
        if (pos == null) return;
        Unit nu = t.create(BC.botPl.team());
        nu.set(pos.x, pos.y);
        nu.rotation = pos.rot;
        try { nu.spawnedByCore = false; } catch (Throwable err) {}
        nu.add();
        BC.botPl.unit(nu);
        prevUnit.put(BC.botPl.id, nu);
        spawnFx(pos.x, pos.y);
    }
    static int botBagCount(St st) { int n = 0; for (int i = 0; i < st.p.size(); i++) if (st.p.get(i) == IN_BAG) n++; return n; }

    static final int BOT_IDLE_SELL = 5400, BOT_BAN_TICKS = 7200;
    static void botSellIdle(Player pl, St st) {
        if (st.p.size() != st.d.size()) return;
        Defs defs = droneDefs();
        float now = Time.time; boolean sold = false;
        for (int i = st.d.size() - 1; i >= 0; i--) {
            int pos = st.p.get(i);
            if (!(pos >= 0)) continue;
            Def d = defs.map.get(st.d.get(i));
            Building b = Vars.world.build(pos);
            if (d == null || b == null || b.block != d.block) continue;
            boolean act = true;
            if (b instanceof Turret.TurretBuild) { Turret.TurretBuild tb = (Turret.TurretBuild) b; try { act = tb.isShooting() || tb.target != null; } catch (Throwable err) {} } // JS: b.isShooting() tren khoi khong phai phao nem loi -> act giu true
            BotIdle r = BC.botIdle.get(pos);
            if (r == null || !r.id.equals(d.id) || act) { BotIdle n = new BotIdle(); n.id = d.id; n.t = now; BC.botIdle.put(pos, n); continue; }
            if (now - r.t < Math.max(2400f, BOT_IDLE_SELL - 600 * (BC.botSm - 1))) continue;
            st.pts += turretSellValue(st, d);
            BC.botBad.put(b.tile.x + "," + b.tile.y, true);
            BC.botBan.put(d.id, now + BOT_BAN_TICKS);
            removeTurret(pos, d);
            st.d.remove(i); st.p.remove(i);
            BC.botIdle.remove(pos);
            sold = true;
            Log.info("[NexPvp] bot bán " + d.id + " (không bắn), điểm=" + st.pts);
        }
        if (sold) { BC.botMem.clear(); BC.botNoBuyT = 0; changed(); }
    }

    static final class LearnPos { String id; int dx, dy; }
    static final class Learn { final HashMap<String, Integer> b = new HashMap<>(), e = new HashMap<>(); final ArrayList<LearnPos> pos = new ArrayList<>(); }
    static Learn botLearn(Player pl) {
        Learn out = new Learn();
        BotCtx bo = botOf(pl.id);
        final Integer kid = bo != null ? bo.botKillerId : null;
        final Player[] best = {null}; final int[] bn = {-1};
        eachPl(p -> {
            if (p == pl || p.team() == pl.team() || p.team() == Vars.state.rules.waveTeam) return;
            St s0 = byId.get(p.id);
            if (kid != null && p.id == kid && s0 != null) { best[0] = p; bn[0] = 1000000000; return; }
            if (s0 != null && s0.d.size() > bn[0]) { bn[0] = s0.d.size(); best[0] = p; }
        });
        if (best[0] == null) return out;
        St s1 = byId.get(best[0].id);
        CoreBlock.CoreBuild ec = best[0].team().core();
        for (int i = 0; i < s1.d.size(); i++) {
            String id = s1.d.get(i);
            int pp = s1.p.get(i);
            if (pp == EQUIPPED) out.e.merge(id, 1, Integer::sum);
            else if (pp >= 0) {
                out.b.merge(id, 1, Integer::sum);
                Building b = Vars.world.build(pp);
                if (b != null && ec != null) { LearnPos lp = new LearnPos(); lp.id = id; lp.dx = b.tile.x - ec.tile.x; lp.dy = b.tile.y - ec.tile.y; out.pos.add(lp); }
            }
        }
        return out;
    }
    static final int[][] BOT_JIT = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
    static int[] botLearnSpot(CoreBlock.CoreBuild core, ArrayList<Tile> ec, Def d, Learn L) {
        for (int sg = -1; sg <= 1; sg += 2)
            for (LearnPos lp : L.pos) {
                if (!lp.id.equals(d.id)) continue;
                for (int[] j : BOT_JIT) {
                    int tx = core.tile.x + sg * lp.dx + j[0], ty = core.tile.y + sg * lp.dy + j[1];
                    if (canBuildAt(core, ec, d.block, tx, ty)) return new int[]{tx, ty};
                }
            }
        return null;
    }
    /** Can: builtCount(St) (phan Cua hang) va botFindSpot(Player, Def) -> int[]{x,y} (phan bot 2). */
    static boolean botRebuild(Player pl) {
        St st = bind(pl);
        CoreBlock.CoreBuild core = pl.team().core();
        if (core == null || st.p.size() != st.d.size()) return true;
        Defs defs = droneDefs();
        ArrayList<Tile> ec = enemyCores(core.team);
        Learn L = botLearn(pl);
        boolean ok = true, did = false;
        for (int i = 0; i < st.d.size(); i++) {
            if (st.p.get(i) != IN_BAG) continue;
            if (builtCount(st) >= maxBuilt()) break;
            Def d = defs.map.get(st.d.get(i));
            if (d == null) continue;
            int pos = -1;
            BotMem m = BC.botMem.get(i);
            if (m != null && m.id.equals(st.d.get(i)) && m.pos >= 0) { Tile tl = Vars.world.tile(m.pos); if (tl != null) pos = placeTurret(pl, d, tl.x, tl.y); }
            if (pos < 0) {
                int[] sp = botLearnSpot(core, ec, d, L);
                if (sp == null) sp = botFindSpot(pl, d);
                if (sp != null) pos = placeTurret(pl, d, sp[0], sp[1]);
            }
            if (pos >= 0) { st.p.set(i, pos); did = true; } else ok = false;
        }
        if (did) changed();
        return ok;
    }


    // ===== PHAN 11: Bot A.I (2) - vong lap bot, nhin/doi theo dich, ve nha, duoi/ne (JS 4686-5030) =====
    static final float BOT_COMBAT_R = 650, BOT_SEE_R = 640, BOT_NOSHOT_RESET = 2700, BOT_SCOUT_EVERY = 5400, BOT_ENG_SCAN = 300;
    static Unit botPickDefend(Unit u, CoreBlock.CoreBuild core) {
        final Unit[] best = {null}, pb = {null};
        final float[] bd = {1e12f}, pbd = {1e12f};
        final float corePad = core.block.size * Vars.tilesize / 2f + 48;
        Groups.unit.each(e -> {
            if (!botEnemyOk(e, u) || e.team == Team.derelict) return;
            float d = Mathf.dst2(core.x, core.y, e.x, e.y), lim = e.range() + corePad;
            if (d > lim * lim) return;
            boolean isP = e.isPlayer() || (bots.size() > 0 && botUnitOf(e) != null);
            if (isP) { if (d < pbd[0]) { pbd[0] = d; pb[0] = e; } }
            else if (d < bd[0]) { bd[0] = d; best[0] = e; }
        });
        return pb[0] != null ? pb[0] : best[0];
    }
    static void botTick() {
        for (int i = 0; i < bots.size(); i++) {
            BC = bots.get(i);
            try { botTickOne(); } catch (Throwable err) { logOnce("bottick", "bot tick failed: " + err, false); }
        }
    }
    static float[] botGearRanges(Player pl, Unit u) {
        St st = byId.get(pl.id);
        float mx = 0, mn = 1e18f;
        if (st != null) for (Def d : equipList(st, droneDefs())) { float r = turretRange(d); if (r > mx) mx = r; if (r < mn) mn = r; }
        if (mx <= 0) { try { mx = u.type.maxRange; } catch (Throwable err) {} mn = mx; }
        return new float[]{Math.max(48f, mx), Math.max(48f, mn)};
    }
    static void botTickOne() {
        Player pl = BC.botPl;
        BC.botSm = botSmart();
        if (pl == null || !isFull() || Vars.state.isPaused()) return;
        final CoreBlock.CoreBuild core = pl.team().core();
        if (core == null) return;
        BotPlayer bpl = (BotPlayer) pl;
        Unit u = bpl._unit;
        if (u != null && (!u.isValid() || u.dead)) {
            bpl._unit = null; u = null; BC.botTarget = null;
            St st0 = bind(pl);
            BC.botDeadT = Math.max(60, RESPAWN_SEC[Math.min(RESPAWN_SEC.length, Math.max(1, activeTier(st0))) - 1] * 60);
        }
        if (u == null) { BC.botDeadT -= Time.delta; if (BC.botDeadT <= 0) spawnBotUnit(); return; }
        float[] gr = botGearRanges(pl, u);
        float maxR = gr[0], minR = gr[1];
        BC.botDbgT -= Time.delta;
        if (BC.botDbgT <= 0) {
            BC.botDbgT = 600;
            final int[] acc = {0, 99999};
            final Team wt0 = Vars.state.rules.waveTeam;
            Groups.unit.each(e -> { if (e.team == wt0 && !e.dead) { acc[0]++; int dd = Math.round(Mathf.dst(core.x, core.y, e.x, e.y)); if (dd < acc[1]) acc[1] = dd; } });
            St st1 = bind(pl);
            Log.info("[NexPvp] botdbg unit=" + u.type.name + " pos=" + u.tileX() + "," + u.tileY() + " core=" + core.tileX() + "," + core.tileY() + " wave=" + Vars.state.wave + " waveUnits=" + acc[0] + " nearest=" + acc[1]
                + " target=" + (BC.botTarget != null) + " pts=" + st1.pts + " turrets=" + builtCount(st1) + " ctrl=" + u.controller().getClass().getSimpleName() + " paused=" + Vars.state.isPaused());
        }
        BC.botScanT -= Time.delta;
        if (BC.botTarget == null || !BC.botTarget.isValid() || BC.botTarget.dead || BC.botScanT <= 0) {
            if (BC.botScanT <= 0) BC.botScanT = 20;
            Unit nt = BC.botFightT > 0 ? botPickDefend(u, core) : null;
            if (nt == null) nt = pickWave(u, core, maxR);
            BC.botTarget = nt;
            if (nt != null) logOnce("botfound", "bot: thấy unit wave " + nt.type.name + " cách " + Math.round(Mathf.dst(core.x, core.y, nt.x, nt.y)), true);
        }
        BC.botSeenT -= Time.delta;
        if (BC.botSeenT <= 0) { BC.botSeenT = 30; try { botSee(u); } catch (Throwable err) { logOnce("botsee", "bot see failed: " + err, false); } }
        boolean shotNow = false;
        try { shotNow = u.isShooting(); } catch (Throwable err) {}
        if (shotNow || BC.botAttack || BC.botScout || BC.botHome || BC.botBuyTrip || BC.botFightT > 0 || BC.botSwapPend || BC.botResetPend) BC.botNoShotT = 0;
        else if ((BC.botNoShotT += Time.delta) > BOT_NOSHOT_RESET) { BC.botNoShotT = 0; BC.botResetPend = true; BC.botResetT0 = Time.time; Log.info("[NexPvp] bot không bắn gì, về nhà biến về để reset"); }
        BC.botIncomeT += Time.delta;
        if (BC.botIncomeT >= 3600) {
            BC.botIncomeT = 0; BC.botMin++;
            St stI = bind(pl);
            stI.pts += Math.round((1 + (float) Math.floor(BC.botMin / 5)) * (1 + 0.35f * (BC.botSm - 1)));
            changed();
        }
        BC.botIdleT -= Time.delta;
        if (BC.botIdleT <= 0) { BC.botIdleT = 60; try { botSellIdle(pl, bind(pl)); } catch (Throwable err) { logOnce("botidle", "bot idle sell failed: " + err, false); } }
        if (BC.botSkipT > 0) { BC.botSkipT -= Time.delta; if (BC.botSkipT <= 0) BC.botSkip = null; }
        if (BC.botRetreatT > 0) BC.botRetreatT -= Time.delta;
        if (BC.botNoBuyT > 0) BC.botNoBuyT -= Time.delta;
        if (BC.botNoAtkT > 0) BC.botNoAtkT -= Time.delta;
        if (BC.botFightT > 0) BC.botFightT -= Time.delta;
        if (BC.botRebuildCd > 0) BC.botRebuildCd -= Time.delta;

        St stM = bind(pl);
        for (int mi = 0; mi < stM.p.size(); mi++) if (stM.p.get(mi) >= 0) { BotMem bm = new BotMem(); bm.id = stM.d.get(mi); bm.pos = stM.p.get(mi); BC.botMem.put(mi, bm); }
        ShieldRec shR = coreShield.get(core.id);
        int shN = shR != null ? shR.hitN : 0;
        if ((BC.botCoreHp > 0 && core.health < BC.botCoreHp - 0.5f) || shN != BC.botCoreHit) {
            if (BC.botFightT <= 0) BC.botAlertT = 900; else BC.botFightT = 900;
        }
        BC.botCoreHit = shN;
        BC.botCoreHp = core.health;
        boolean needRb = BC.botRebuildCd <= 0 && botBagCount(stM) > 0 && builtCount(stM) < maxBuilt();
        if (BC.botAlertT > 0 || needRb) {
            BC.botMissionT += Time.delta;
            if (BC.botMissionT > 1500) { BC.botMissionT = 0; BC.botAlertT = 0; BC.botRebuildCd = 1800; }
            else {
                BC.botBaseT -= Time.delta;
                if (BC.botBase == null || BC.botBaseT <= 0) { BC.botBase = botFindBase(u, core); BC.botBaseT = 120; }
                u.controlWeapons(false, false);
                botGo(u, BC.botBase != null ? BC.botBase.x : core.x, BC.botBase != null ? BC.botBase.y : core.y, 8);
                if (botIsBase(u)) {
                    if (needRb && !botRebuild(pl)) BC.botRebuildCd = 1800;
                    boolean wasAlert = BC.botAlertT > 0;
                    if (botRecall(pl, u, core)) { BC.botMissionT = 0; BC.botAlertT = 0; if (wasAlert) BC.botFightT = 900; }
                }
                BC.botLastX = u.x; BC.botLastY = u.y;
                return;
            }
        } else BC.botMissionT = 0;
        float hpf = u.health / u.maxHealth;
        Unit t = BC.botTarget;
        boolean combat = t != null && t.isValid() && !t.dead && Mathf.dst(u.x, u.y, t.x, t.y) <= BOT_COMBAT_R;

        BC.botEpT -= Time.delta;
        if (BC.botEpT <= 0) { BC.botEpT = 20; BC.botEp = botNearestPlayer(u); }
        Unit ep = BC.botEp;
        boolean epNear = ep != null && ep.isValid() && !ep.dead && Mathf.dst(u.x, u.y, ep.x, ep.y) <= BOT_SEE_R * 0.7f;
        if (BC.botEncT > 0) BC.botEncT -= Time.delta;
        if (BC.botEnc == 3 && (BC.botHuntU == null || !BC.botHuntU.isValid() || BC.botHuntU.dead || hpf < 0.15f)) { BC.botEnc = 0; BC.botHuntU = null; BC.botEncT = 600; }
        if (BC.botEnc != 3 && BC.botEncT <= 0 && ep != null && (epNear || BC.botHitT > 0)) {
            double rr = Math.random();
            BC.botEnc = rr < 0.2 ? 1 : (rr < 0.5 ? 2 : 3);
            BC.botEncT = BC.botEnc == 2 ? 1800 : 3600;
            if (BC.botEnc == 1) { BC.botRetreatT = 240; BC.botFleeT = Math.max(BC.botFleeT, 300f); }
            else if (BC.botEnc == 3) { BC.botHuntU = ep; BC.botRetreatT = 0; BC.botFleeT = 0; }
        }
        boolean quiet = BC.botEnc == 3 || (BC.botEnc == 2 && BC.botEncT > 0);
        if (combat && botUnfavorable(u) && !(quiet && epNear)) { BC.botRetreatT = 240; BC.botFleeT = Math.max(BC.botFleeT, 300f); }
        if (BC.botHitT > 0) {
            BC.botHitT -= Time.delta;
            if (BC.botEnc == 1) { if (BC.botRetreatT < 120) BC.botRetreatT = 120; BC.botFleeT = 600; }
        } else if (BC.botRevengeT > 0) BC.botRevengeT -= Time.delta;
        if (BC.botFleeT > 0) BC.botFleeT -= Time.delta;
        BC.botPlanT -= Time.delta;
        if (BC.botPlanT <= 0) { BC.botPlanT = Math.max(45f, 120 - 20 * (BC.botSm - 1)); botPlan(pl, u, core, combat, hpf); }
        boolean onBase = botIsBase(u);
        if (BC.botHome || BC.botRetreatT > 0 || BC.botFleeT > 0 || BC.botBuyTrip) {
            BC.botBaseT -= Time.delta;
            if (BC.botBase == null || BC.botBaseT <= 0) { BC.botBase = botFindBase(u, core); BC.botBaseT = 120; }
        }
        float bx = BC.botBase != null ? BC.botBase.x : core.x, by = BC.botBase != null ? BC.botBase.y : core.y;

        if (BC.botEnc == 3 && !BC.botHome && BC.botHuntU != null) {
            botChase(u, BC.botHuntU, maxR, minR);
        } else if (BC.botHome || BC.botRetreatT > 0 || BC.botFleeT > 0) {
            botGo(u, bx, by, 8);
            if (onBase && ((BC.botHome && (hpf < 0.7f || BC.botSwapPend || BC.botResetPend)) || BC.botFleeT > 0 || BC.botRetreatT > 0) && botRecall(pl, u, core)) return;
            BC.botNpT -= Time.delta;
            if (BC.botNpT <= 0) { BC.botNpT = 10; BC.botNp = botNearestPlayer(u); }
            Unit at = (BC.botNp != null && BC.botNp.isValid() && !BC.botNp.dead) ? BC.botNp : t;
            if (at != null && at.isValid() && !at.dead && Mathf.dst(u.x, u.y, at.x, at.y) <= maxR) { u.lookAt(at.x, at.y); u.aim(at.x, at.y); u.controlWeapons(true, true); }
            else u.controlWeapons(false, false);
        } else if (combat) {
            botChase(u, t, maxR, minR);
        } else if (BC.botBuyTrip) {
            botGo(u, bx, by, 8);
            u.controlWeapons(false, false);
            if (onBase) {
                BC.botBuyT -= Time.delta;
                if (BC.botBuyT <= 0) { BC.botBuyT = 60; if (!botShop(pl)) BC.botNoBuyT = 1800; }
            }
        } else if (BC.botAttack && BC.botEnemyPos != null) {
            float gx = BC.botEnemyPos.x, gy = BC.botEnemyPos.y;
            Unit ge = botEnemyUnit(u);
            if (ge != null) { gx = ge.x; gy = ge.y; }
            float dist = Mathf.dst(u.x, u.y, gx, gy);
            if (!botGo(u, gx, gy, maxR * 0.75f)) { BC.botAttack = false; BC.botNoAtkT = 1800; }
            u.lookAt(gx, gy); u.aim(gx, gy);
            u.controlWeapons(true, dist <= maxR);
        } else if (BC.botScout) {
            Unit ge = botEnemyUnit(u);
            if (ge == null || !botGo(u, ge.x, ge.y, BOT_SEE_R * 0.8f)) { BC.botScout = false; BC.botSeenAt = Time.time - BOT_SCOUT_EVERY + 1800; }
            u.controlWeapons(false, false);
        } else if (t != null) {
            botChase(u, t, maxR, minR);
        } else {
            botGo(u, core.x, core.y, BOT_HOME_R);
            u.controlWeapons(false, false);
        }
        BC.botLastX = u.x; BC.botLastY = u.y;
    }

    static Seen botSwapWant(St st) {
        if (BC.botSeen == null || st.lv < FREE_SWITCH_LEVEL) return null;
        int k = Math.min(TIER_MAX, Math.max(1, BC.botSeen.u));
        int nu = (k == tierCap(st.lv)) ? 0 : k;
        int pi = BC.botSeen.pi;
        if (pi == PILOT_EMPIX && st.lv < EMPIX_MIN_LEVEL) pi = st.pi;
        if (nu == st.u && pi == st.pi) return null;
        Seen w = new Seen(); w.u = nu; w.pi = pi;
        return w;
    }
    static void botSee(Unit u) {
        final Seen[] seen = {null};
        eachPl(p -> {
            if (seen[0] != null || p == BC.botPl || p.team() == u.team || p.team() == Vars.state.rules.waveTeam) return;
            Unit e = p.unit(); St s0 = byId.get(p.id);
            if (e == null || e.dead || !e.isValid() || s0 == null) return;
            if (Mathf.dst2(u.x, u.y, e.x, e.y) > BOT_SEE_R * BOT_SEE_R) return;
            Seen s = new Seen(); s.u = activeTier(s0); s.pi = s0.pi;
            seen[0] = s;
        });
        if (seen[0] == null) return;
        BC.botSeen = seen[0];
        BC.botSeenAt = Time.time;
        BC.botScout = false;
        if (!BC.botSwapPend && botSwapWant(bind(BC.botPl)) != null) { BC.botSwapPend = true; BC.botSwapT0 = Time.time; }
    }
    static void botApplySwap(Player pl) {
        if (!BC.botSwapPend) return;
        BC.botSwapPend = false;
        St st = bind(pl);
        Seen w = botSwapWant(st);
        if (w == null) return;
        st.u = w.u; st.pi = w.pi;
        changed();
        Log.info("[NexPvp] bot đổi theo địch: unit=" + activeTier(st) + " pilot=" + st.pi);
    }
    static boolean botRecall(Player pl, Unit u, CoreBlock.CoreBuild core) {
        if (!canReturnCore(u, core)) return false;
        spawnFx(u.x, u.y);
        try { Call.unitDespawn(u); } catch (Throwable err) { try { u.remove(); } catch (Throwable e2) {} }
        ((BotPlayer) pl)._unit = null;
        BC.botTarget = null; BC.botHome = false; BC.botRetreatT = 0; BC.botEnc = 0; BC.botHuntU = null;
        try { botApplySwap(pl); } catch (Throwable err) { logOnce("botswap", "bot swap failed: " + err, false); }
        BC.botResetPend = false; BC.botFleeT = 0; BC.botNoShotT = 0; BC.botDeadT = 90;
        return true;
    }
    static void botStrafe(Unit u, Unit t, float maxR, float dist) {
        float dx = t.x - u.x, dy = t.y - u.y, len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        int sgn = (((int) Math.floor(Time.time / 100) + (u.id & 1)) % 2 == 0) ? 1 : -1;
        float px = -dy / len * sgn, py = dx / len * sgn;
        float back = dist < maxR * 0.45f ? -0.9f : (dist > maxR * 0.7f ? 0.5f : 0f);
        Vec2 v = Tmp.v1;
        v.set(px * 0.8f + dx / len * back, py * 0.8f + dy / len * back);
        if (v.len() > 0.05f) v.setLength(u.speed()); else v.setZero();
        u.movePref(v);
    }
    static void botChase(Unit u, Unit t, float maxR, float minR) {
        if (BC.botEngTgt != t) { BC.botEngTgt = t; BC.botEngR = 0; BC.botEngOn = false; }
        float useR = BC.botEngR > 0 ? BC.botEngR : maxR, dist = Mathf.dst(u.x, u.y, t.x, t.y);
        if (useR >= 110 && dist <= useR * 0.9f && dist > 20) {
            if (minR < maxR) {
                if (!BC.botEngOn) { BC.botEngOn = true; BC.botEngScanT = BOT_ENG_SCAN; }
                else if ((BC.botEngScanT -= Time.delta) <= 0) { BC.botEngScanT = BOT_ENG_SCAN; if (dist > minR) BC.botEngR = minR; }
            }
            BC.botStuckT = 0;
            u.lookAt(t.x, t.y); u.aim(t.x, t.y); u.controlWeapons(true, true);
            if (t.team != Vars.state.rules.waveTeam) botStrafe(u, t, useR, dist);
            return;
        }
        BC.botEngOn = false;
        boolean reach = botGo(u, t.x, t.y, useR * 0.75f);
        if (!reach) { BC.botSkip = t; BC.botSkipT = 180; BC.botTarget = null; }
        if (dist > useR * 0.75f && Mathf.dst(u.x, u.y, BC.botLastX, BC.botLastY) < 0.3f) BC.botStuckT += Time.delta; else BC.botStuckT = 0;
        if (BC.botStuckT > 300) { BC.botSkip = t; BC.botSkipT = 180; BC.botTarget = null; BC.botStuckT = 0; }
        u.lookAt(t.x, t.y); u.aim(t.x, t.y);
        u.controlWeapons(true, dist <= useR);
    }
    static boolean botIsBase(Unit u) { Tile t = Vars.world.tileWorld(u.x, u.y); return t != null && isCoreZoneFloor(t.floor()); }
    static Anch botFindBase(Unit u, CoreBlock.CoreBuild core) {
        ArrayList<Tile> ec = enemyCores(core.team);
        int tx = u.tileX(), ty = u.tileY(), bd = Integer.MAX_VALUE;
        Tile best = null;
        for (int dx = -60; dx <= 60; dx += 2) for (int dy = -60; dy <= 60; dy += 2) {
            Tile t = Vars.world.tile(tx + dx, ty + dy);
            if (t == null || t.solid() || t.build != null || !isCoreZoneFloor(t.floor())) continue;
            if (!ourSide(core, ec, t.x, t.y)) continue;
            int d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = t; }
        }
        return best == null ? null : new Anch(best.worldx(), best.worldy());
    }


    // ===== PHAN 12: Bot A.I (3) - suc manh, mua sam, nang chi so, tim cho xay (JS 5032-5402) =====
    // Can (phan Cua hang, JS 6187-6210): builtCount(St), equipCount(St), equipSlots(St) -> int.
    static float botPower(St st) {
        Defs defs = droneDefs();
        float w = st.lv * 10 + st.b * 8;
        for (int i = 0; i < st.d.size(); i++) { Def d = defs.map.get(st.d.get(i)); if (d != null && st.p.get(i) >= 0) w += d.cost * 6; }
        return w;
    }
    static float botEnemyPower(Player pl) {
        final Team tm = pl.team();
        final float[] best = {0};
        eachPl(p -> { if (p.team() != tm) best[0] = Math.max(best[0], botPower(bind(p))); });
        return best[0];
    }
    static Unit botNearestPlayer(Unit u) {
        final Unit[] r = {null}; final float[] bd = {1e12f};
        eachPl(p -> {
            if (p.team() == u.team || p.team() == Vars.state.rules.waveTeam) return;
            Unit e = p.unit();
            if (e == null || e.dead || !e.isValid()) return;
            float d = Mathf.dst2(u.x, u.y, e.x, e.y);
            if (d < bd[0]) { bd[0] = d; r[0] = e; }
        });
        return r[0];
    }
    static Unit botEnemyUnit(Unit u) {
        final Unit[] r = {null};
        eachPl(p -> { if (r[0] != null) return; Unit e = p.unit(); if (e != null && botEnemyOk(e, u) && e.team != Vars.state.rules.waveTeam) r[0] = e; });
        return r[0];
    }

    static int botOwn(St st, String id, boolean eq) {
        int c = 0;
        for (int k = 0; k < st.d.size(); k++) if (st.d.get(k).equals(id) && (eq ? st.p.get(k) == EQUIPPED : st.p.get(k) >= 0)) c++;
        return c;
    }
    static int botLrn(St st, Learn L, Def d, boolean slotFree) {
        return Math.max(0, cnt(L.b, d.id) - botOwn(st, d.id, false)) + (slotFree ? Math.max(0, cnt(L.e, d.id) - botOwn(st, d.id, true)) : 0);
    }
    static final class LnInfo { int need, gain; }
    static LnInfo botLearnInfo(Player pl, St st) {
        LnInfo out = new LnInfo();
        if (st.p.size() != st.d.size()) return out;
        Defs defs = droneDefs();
        Learn L = botLearn(pl);
        boolean multi = multiTurretOn();
        float now = Time.time;
        boolean hasEx = false;
        for (int i = 0; i < st.d.size(); i++) {
            Def dd = defs.map.get(st.d.get(i));
            if (!(st.p.get(i) >= 0) || dd == null || botOwn(st, st.d.get(i), false) <= cnt(L.b, st.d.get(i))) continue;
            out.gain += turretSellValue(st, dd);
            hasEx = true;
        }
        boolean slotFree = equipCount(st) < equipSlots(st), full = builtCount(st) >= maxBuilt();
        for (Def d : defs.list) {
            if (botNoTurret(d.kind) || !d.equip) continue;
            if (!multi && ownedCount(st, d.id) >= 1) continue;
            Float bn = BC.botBan.get(d.id);
            if (bn != null && bn > now) continue;
            boolean eq = slotFree && cnt(L.e, d.id) > botOwn(st, d.id, true);
            boolean bd = cnt(L.b, d.id) > botOwn(st, d.id, false);
            if (!eq && !(bd && (!full || hasEx))) continue;
            int c = turretCost(st, d);
            if (out.need == 0 || c < out.need) out.need = c;
        }
        return out;
    }
    static boolean botWantsBuy(St st, LnInfo ln) {
        if (ln != null && ln.need > 0) return st.pts + ln.gain >= ln.need;
        Defs defs = droneDefs();
        int minC = 1000000000;
        if (builtCount(st) < maxBuilt()) {
            for (Def d : defs.list) {
                if (botNoTurret(d.kind) || !d.equip) continue;
                if (!multiTurretOn() && ownedCount(st, d.id) >= 1) continue;
                minC = Math.min(minC, turretCost(st, d));
            }
        }
        if (st.b < BARRACKS.length) minC = Math.min(minC, barrackCost(st.b));
        return st.pts >= minC;
    }
    static final int[] BOT_STAT_W = {3, 2, 3, 1, 2, 1, 1, 1, 1, 1, 0};
    static void botSpendStats(Player pl, St st, int reserve) {
        int guard = 60;
        while (st.pts - reserve >= 1 && guard-- > 0) {
            int bk = -1; float bv = 1e12f;
            for (int k = 0; k < STATS.length && k < BOT_STAT_W.length; k++) {
                if (!(BOT_STAT_W[k] > 0) || st.s[k] >= statMax(k)) continue;
                float v = (float) st.s[k] / BOT_STAT_W[k];
                if (v < bv) { bv = v; bk = k; }
            }
            if (bk < 0) break;
            st.pts--; st.s[bk]++;
        }
        changed();
    }
    static void botPlan(Player pl, Unit u, CoreBlock.CoreBuild core, boolean combat, float hpf) {
        BC.botHome = hpf < 0.7f;
        if (BC.botSwapPend) { if (Time.time - BC.botSwapT0 > 3600) BC.botSwapPend = false; else BC.botHome = true; }
        if (BC.botResetPend) { if (Time.time - BC.botResetT0 > 3600) BC.botResetPend = false; else BC.botHome = true; }
        St st = bind(pl);
        LnInfo ln = botLearnInfo(pl, st);
        BC.botSaveCost = (ln.need > 0 && st.pts + ln.gain < ln.need) ? ln.need : 0;
        BC.botBuyTrip = !combat && !BC.botHome && BC.botNoBuyT <= 0 && botWantsBuy(st, ln);
        if (st.pts - ln.need >= 1 && !BC.botBuyTrip && (BC.botNoBuyT > 0 || !botWantsBuy(st, ln))) {
            try { botSpendStats(pl, st, ln.need); } catch (Throwable err) { logOnce("botstats", "bot stats failed: " + err, false); }
        }
        final Team wt = Vars.state.rules.waveTeam;
        final int[] cntw = {0};
        Groups.unit.each(e -> { if (e.team == wt && !e.dead && Mathf.dst2(core.x, core.y, e.x, e.y) < 900 * 900) cntw[0]++; });
        int nearWave = cntw[0];
        BC.botAttack = false;
        Unit sgE = botEnemyUnit(u);
        if (BC.botScout) {
            if (sgE == null || hpf < 0.7f || nearWave >= 6 || Time.time - BC.botScoutT0 > 3600) { BC.botScout = false; BC.botSeenAt = Time.time - BOT_SCOUT_EVERY + 1800; }
        } else if (sgE != null && !combat && !BC.botHome && !BC.botSwapPend && hpf > 0.8f && nearWave < 4 && Time.time - BC.botSeenAt > BOT_SCOUT_EVERY) {
            BC.botScout = true; BC.botScoutT0 = Time.time;
        }
    }
    static int[] botFindSpot(Player pl, Def def) {
        CoreBlock.CoreBuild core = pl.team().core();
        if (core == null) return null;
        ArrayList<Tile> ec = enemyCores(core.team);
        Block blk = def.block;
        int minR = (int) (Math.ceil(core.block.size / 2.0) + Math.ceil(blk.size / 2.0) + 1);
        for (int i = 0; i < BOT_SPOT_TRIES; i++) {
            double a = Math.random() * 360, r = minR + Math.random() * Math.random() * BOT_SPOT_RANGE;
            int tx = (int) Math.round(core.tile.x + Mathf.cosDeg((float) a) * r), ty = (int) Math.round(core.tile.y + Mathf.sinDeg((float) a) * r);
            if (canBuildAt(core, ec, blk, tx, ty)) return new int[]{tx, ty};
        }
        return null;
    }
    static boolean botBadSpot(int[] sp) { return sp != null && BC.botBad.get(sp[0] + "," + sp[1]) != null; }

    static final class Cand { Def d; boolean eq; int ls; }
    static final class Ex { int i, v; }
    static ArrayList<Ex> botExcess(St st, Defs defs, Learn L) {
        ArrayList<Ex> out = new ArrayList<>();
        for (int i = 0; i < st.d.size(); i++) {
            Def dd = defs.map.get(st.d.get(i));
            if (!(st.p.get(i) >= 0) || dd == null || botOwn(st, st.d.get(i), false) <= cnt(L.b, st.d.get(i))) continue;
            Ex e = new Ex(); e.i = i; e.v = turretSellValue(st, dd); out.add(e);
        }
        out.sort((a, b) -> a.v - b.v);
        return out;
    }
    /** 1 = da mua, 2 = dang de danh, 0 = khong co gi hoc duoc. */
    static int botBuyLearned(Player pl, St st, Defs defs, Learn L, CoreBlock.CoreBuild core0, boolean slotFree) {
        BC.botSaveCost = 0;
        boolean multi = multiTurretOn();
        float now = Time.time;
        ArrayList<Tile> ec = enemyCores(core0.team);
        ArrayList<Cand> list = new ArrayList<>();
        for (Def d : defs.list) {
            if (botNoTurret(d.kind) || !d.equip) continue;
            if (!multi && ownedCount(st, d.id) >= 1) continue;
            Float bn = BC.botBan.get(d.id);
            if ((bn != null && bn > now) || botLrn(st, L, d, slotFree) <= 0) continue;
            Cand c = new Cand(); c.d = d; c.eq = slotFree && cnt(L.e, d.id) > botOwn(st, d.id, true); list.add(c);
        }
        list.sort((a, b) -> { int x = (b.eq ? 1 : 0) - (a.eq ? 1 : 0); return x != 0 ? x : a.d.cost - b.d.cost; });
        for (Cand cd : list) {
            Def d = cd.d; boolean isEq = cd.eq; int[] spot = null;
            if (!isEq) {
                spot = botLearnSpot(core0, ec, d, L);
                if (botBadSpot(spot)) spot = null;
                for (int k = 0; k < 6 && spot == null; k++) { int[] sp = botFindSpot(pl, d); if (sp != null && !botBadSpot(sp)) spot = sp; }
                if (spot == null) { logOnce("botnospot:" + d.id, "bot: không có chỗ xây " + d.id + ", thử loại khác", true); continue; }
            }
            int need = turretCost(st, d);
            boolean needSlot = !isEq && builtCount(st) >= maxBuilt();
            ArrayList<Ex> ex = botExcess(st, defs, L);
            int gain = 0;
            for (Ex e : ex) gain += e.v;
            if (needSlot && ex.size() == 0) continue;
            if (st.pts < need && st.pts + gain < need) { if (BC.botSaveCost == 0 || need < BC.botSaveCost) BC.botSaveCost = need; continue; }
            int guard = 0;
            while ((st.pts < need || (needSlot && builtCount(st) >= maxBuilt())) && guard++ < 12) {
                ex = botExcess(st, defs, L);
                if (ex.size() == 0) break;
                int k = ex.get(0).i;
                Def dd = defs.map.get(st.d.get(k));
                st.pts += turretSellValue(st, dd);
                removeTurret(st.p.get(k), dd);
                Log.info("[NexPvp] bot bán " + dd.id + " (thừa so với địch) lấy điểm mua " + d.id);
                st.d.remove(k); st.p.remove(k);
                BC.botMem.clear();
                need = turretCost(st, d);
            }
            if (st.pts < need || (needSlot && builtCount(st) >= maxBuilt())) { changed(); continue; }
            if (isEq) {
                st.pts -= need; st.d.add(d.id); st.p.add(EQUIPPED);
                Log.info("[NexPvp] bot gắn " + d.id + " (học theo địch)");
                changed();
                return 1;
            }
            int pos = placeTurret(pl, d, spot[0], spot[1]);
            if (pos < 0) { changed(); continue; }
            Log.info("[NexPvp] bot xây " + d.id + " tại " + spot[0] + "," + spot[1] + " (học theo địch, còn " + (st.pts - need) + " điểm)");
            st.pts -= need; st.d.add(d.id); st.p.add(pos);
            changed();
            return 1;
        }
        return BC.botSaveCost > 0 ? 2 : 0;
    }
    static boolean botShop(Player pl) {
        St st = bind(pl);
        if (st.p.size() != st.d.size()) return false;
        Defs defs = droneDefs();
        int nb = builtCount(st);
        boolean full = nb >= maxBuilt();
        int bl = st.b;
        LnInfo lnI = botLearnInfo(pl, st);
        if (bl < BARRACKS.length && st.pts - barrackCost(bl) >= lnI.need) {
            boolean weaker = botEnemyPower(pl) > botPower(st);
            if (full || bl < 1 + nb / 2 || weaker) {
                st.pts -= barrackCost(bl);
                st.b = bl + 1;
                Log.info("[NexPvp] bot nâng nhà máy lính lên cấp " + st.b);
                changed();
                return true;
            }
        }
        int wi = -1;
        if (full) {
            for (int i = 0; i < st.d.size(); i++) {
                Def dd = defs.map.get(st.d.get(i));
                if (!(st.p.get(i) >= 0) || dd == null) continue;
                if (wi < 0 || dd.cost < defs.map.get(st.d.get(wi)).cost) wi = i;
            }
            if (wi < 0) return false;
        }
        int wc = full ? defs.map.get(st.d.get(wi)).cost : 0;
        int wRefund = full ? turretSellValue(st, defs.map.get(st.d.get(wi))) : 0;
        Learn L = botLearn(pl);
        CoreBlock.CoreBuild core0 = pl.team().core();
        if (core0 == null) return false;
        boolean slotFree = equipCount(st) < equipSlots(st);
        int rl = botBuyLearned(pl, st, defs, L, core0, slotFree);
        if (rl == 1) return true;
        if (rl == 2) return false;
        if (slotFree) {
            int enE = 0;
            for (int v : L.e.values()) enE += v;
            if (enE == 0) {
                ArrayList<Def> rc = new ArrayList<>();
                float tn = Time.time;
                for (Def d : defs.list) {
                    if (botNoTurret(d.kind) || !d.equip) continue;
                    if (!multiTurretOn() && ownedCount(st, d.id) >= 1) continue;
                    Float bn = BC.botBan.get(d.id);
                    if ((bn != null && bn > tn) || turretCost(st, d) > st.pts) continue;
                    rc.add(d);
                }
                if (rc.size() > 0) {
                    Def rd = rc.get((int) Math.floor(Math.random() * rc.size()));
                    st.pts -= turretCost(st, rd); st.d.add(rd.id); st.p.add(EQUIPPED);
                    Log.info("[NexPvp] bot gắn ngẫu nhiên " + rd.id + " (địch chưa trang bị)");
                    changed();
                    return true;
                }
            }
        }
        ArrayList<Cand> cands = new ArrayList<>();
        float now = Time.time;
        for (Def d : defs.list) {
            if (botNoTurret(d.kind) || !d.equip) continue;
            if (turretCost(st, d) > st.pts + wRefund) continue;
            if (full && d.cost < wc * Math.max(1.2f, 2 - 0.15f * (BC.botSm - 1))) continue;
            if (full && d.id.equals(st.d.get(wi))) continue;
            if (!multiTurretOn() && ownedCount(st, d.id) >= 1) continue;
            Float bn = BC.botBan.get(d.id);
            if (bn != null && bn > now) continue;
            Cand c = new Cand(); c.d = d; c.ls = botLrn(st, L, d, slotFree) > 0 ? 1 : 0; cands.add(c);
        }
        cands.sort((x, y) -> { int s = y.ls - x.ls; return s != 0 ? s : y.d.cost - x.d.cost; });
        ArrayList<Tile> ec0 = enemyCores(core0.team);
        for (Cand cd : cands) {
            Def best = cd.d;
            if (slotFree && cnt(L.e, best.id) > botOwn(st, best.id, true)) {
                int ep = turretCost(st, best);
                if (ep > st.pts) continue;
                st.pts -= ep; st.d.add(best.id); st.p.add(EQUIPPED);
                Log.info("[NexPvp] bot gắn " + best.id + " (học theo địch)");
                changed();
                return true;
            }
            int[] spot = botLearnSpot(core0, ec0, best, L);
            if (botBadSpot(spot)) spot = null;
            if (spot == null) for (int k = 0; k < 6 && spot == null; k++) { int[] sp = botFindSpot(pl, best); if (sp != null && !botBadSpot(sp)) spot = sp; }
            if (spot == null) { logOnce("botnospot:" + best.id, "bot: không có chỗ xây " + best.id + ", thử loại khác", true); continue; }
            int pos = placeTurret(pl, best, spot[0], spot[1]);
            if (pos < 0) continue;
            int bPrice = turretCost(st, best);
            if (full) {
                st.pts += wRefund;
                removeTurret(st.p.get(wi), defs.map.get(st.d.get(wi)));
                st.d.remove(wi); st.p.remove(wi);
            }
            Log.info("[NexPvp] bot xây " + best.id + " tại " + spot[0] + "," + spot[1] + " (còn " + (st.pts - bPrice) + " điểm)");
            st.pts -= bPrice; st.d.add(best.id); st.p.add(pos);
            changed();
            return true;
        }
        return false;
    }


    // ===== PHAN 13: vong cap nhat chinh, wave ngau nhien, sat thuong phu (JS 5405-5760) =====
    static final int KILL_CREDIT_TICKS = 900;
    static float botT, bulletStatT, wallT, syncT, coreT, passiveT, orphanT, winT, pruneT, bonusWaveT;
    static boolean bulletStatOn = false, autoPauseFixed = false;
    static final class Hit { Team team; int pid; float t; }
    static final HashMap<Integer, Hit> lastHitBy = new HashMap<>(), teamHit = new HashMap<>();

    static boolean nearPos(float x, float y) {
        Seq<Team> teams = pvpTeams();
        for (int t = 0; t < teams.size; t++) {
            Seq<CoreBlock.CoreBuild> cores = teams.get(t).cores();
            for (int i = 0; i < cores.size; i++) if (Mathf.dst(x, y, cores.get(i).x, cores.get(i).y) < 200) return true;
        }
        return false;
    }
    static boolean tileClear(Tile t) {
        if (t == null) return false;
        if (t.solid() || t.build != null) return false;
        if (t.block() != Blocks.air) return false;
        return !t.floor().isDeep();
    }
    static float[] spawnPos(UnitType type) {
        int ts = Vars.tilesize, w = Vars.world.width(), h = Vars.world.height();
        if (w < 5 || h < 5) return null;
        int rad = Math.min(3, Math.max(0, (int) Math.ceil(type.hitSize / ts / 2) - 1));
        for (int i = 0; i < 120; i++) {
            int tx = 2 + (int) Math.floor(Math.random() * (w - 4)), ty = 2 + (int) Math.floor(Math.random() * (h - 4));
            if (!waveRandomOn()) { tx = (int) Math.floor(w * 0.35 + Math.random() * w * 0.3); ty = (int) Math.floor(h * 0.35 + Math.random() * h * 0.3); }
            boolean ok = true;
            for (int dx = -rad; dx <= rad && ok; dx++) for (int dy = -rad; dy <= rad; dy++) if (!tileClear(Vars.world.tile(tx + dx, ty + dy))) { ok = false; break; }
            if (!ok) continue;
            Tile t = Vars.world.tile(tx, ty);
            float wx = t.worldx(), wy = t.worldy();
            if (nearPos(wx, wy)) continue;
            return new float[]{wx, wy};
        }
        return null;
    }
    static int waveCount(Wave g, int w) {
        if (w < g.b || w > g.e || (w - g.b) % g.sp != 0) return 0;
        return Math.min(g.mx, g.n + (int) Math.floor(((w - g.b) / (double) g.sp) / g.sc));
    }
    static void allowWaveUnits(mindustry.game.Rules r) { try { if (!(r.unitCap >= WAVE_UNIT_CAP)) r.unitCap = WAVE_UNIT_CAP; } catch (Throwable err) {} }
    static void prepareWaveType(UnitType type) {
        try { type.useUnitCap = false; } catch (Throwable err) {}
        try {
            int env = Vars.state.rules.env;
            if (!type.supportsEnv(env)) { type.envEnabled |= env; type.envDisabled &= ~env; type.envRequired = 0; }
        } catch (Throwable err) {}
    }
    static int spawnWave(int w, int capNew) {
        boolean fresh = capNew >= 0;
        int maxUnits = fresh ? capNew : MAX_WAVE_UNITS, spawned = 0;
        if (Groups.player.size() == 0) return 0;
        allowWaveUnits(Vars.state.rules);
        final Team team = Vars.state.rules.waveTeam;
        final int[] alive = {0};
        if (!fresh) Groups.unit.each(u -> { if (u.team == team) alive[0]++; });
        for (Wave g : WAVES) {
            UnitType type = Vars.content.getByName(ContentType.unit, g.t);
            if (type == null) continue;
            if (type.flying || type.naval) continue;
            int n = waveCount(g, w);
            if (n <= 0) continue;
            prepareWaveType(type);
            for (int i = 0; i < n; i++) {
                if (alive[0] >= maxUnits) return spawned;
                float[] pos = spawnPos(type);
                if (pos == null) continue;
                Unit unit = type.create(team);
                unit.shield = Math.max(0, (w - g.b) * 6);
                unit.set(pos[0], pos[1]);
                unit.apply(StatusEffects.invincible, 60f);
                unit.add();
                alive[0]++; spawned++;
                Events.fire(new UnitSpawnEvent(unit));
                fx(pos[0], pos[1]);
            }
        }
        return spawned;
    }
    static int spawnWave(int w) { return spawnWave(w, -1); }

    // ===== Pvp killer / exp =====
    static Player pvpKiller(Unit u) {
        Hit rec = lastHitBy.remove(u.id);
        if (rec == null || Time.time - rec.t > KILL_CREDIT_TICKS) return null;
        for (Player p : Groups.player) if (p.id == rec.pid) return p;
        return null;
    }
    static float killExp(Unit u) {
        float hp = u.maxHealth, bonus = 1 + Math.min(EXP_HP_BONUS_MAX, hp / EXP_HP_BONUS_REF);
        return hp * EXP_PER_HP * bonus * expPercent() / 100f;
    }
    static Team teamOf(Entityc o) { return o instanceof Teamc ? ((Teamc) o).team() : null; }
    /** JS plUnit(pid): don vi cua nguoi choi/bot theo id (JS 7081). */
    static Unit plUnit(int pid) {
        for (int i = 0; i < bots.size(); i++) if (bots.get(i).botPl.id == pid) return bots.get(i).botPl.unit();
        Player p = Groups.player.getByID(pid);
        return p != null ? p.unit() : null;
    }

    static void damageEvent(UnitDamageEvent e) {
        if (Vars.net.client() || !isActive()) return;
        Bullet b = e.bullet; Unit tgt = e.unit;
        if (b == null || tgt == null) return;
        if (tgt.dead || !tgt.isValid()) { try { neoFinisher(b, tgt); } catch (Throwable err) {} return; }
        float neoHp0 = tgt.health;
        Float shred = shredMap.get(b.type);
        if (shred != null && shred != 0) { try { tgt.damagePierce(b.damage * shred); } catch (Throwable err) { try { tgt.damage(b.damage * shred); } catch (Throwable err2) {} } }
        Entityc o = b.owner;
        if (b.team != null && b.team != tgt.team) { Hit h = new Hit(); h.team = b.team; h.t = Time.time; teamHit.put(tgt.id, h); }
        if (o == null || teamOf(o) == tgt.team) return;
        try {
            BotCtx bb = botUnitOf(tgt);
            if (bb != null && teamOf(o) != Vars.state.rules.waveTeam && o instanceof Unit && ownerPl((Unit) o) != null) { bb.botHitT = 300; bb.botHitBy = o; }
        } catch (Throwable err) {}
        boolean isUnitOwner = o instanceof Unit;
        Integer apid;
        if (isUnitOwner) { Player ap = ownerPl((Unit) o); apid = ap != null ? Integer.valueOf(ap.id) : soldierOwner.get(o.id()); }
        else apid = hostOwner.get(o.id());
        if (apid == null) return;
        try {
            St ost = byId.get(apid);
            int[] os = ost != null ? ost.s : null;
            if (os != null && os[9] > 0 && tgt.maxHealth > 0) { float ex = tgt.maxHealth * os[9] * DMG_PCT_MAXHP / 100f; try { tgt.damagePierce(ex); } catch (Throwable err) { tgt.damage(ex); } }
            if (os != null && os[13] > 0 && tgt.team == Vars.state.rules.waveTeam) { float exw = b.damage * os[13] * WRAND_DMG_PCT / 100f; try { tgt.damagePierce(exw); } catch (Throwable err) { tgt.damage(exw); } }
        } catch (Throwable err) {}
        try {
            St hst = byId.get(apid);
            if (hst != null && hst.pi == PILOT_HIST) {
                float bonus = 0;
                if (tgt.team == Vars.state.rules.waveTeam) bonus = HIST_WAVE_DMG;
                else if (soldierOwner.containsKey(tgt.id)) bonus = HIST_SOLDIER_DMG;
                else if (tgt.isPlayer() || botUnitOf(tgt) != null) bonus = HIST_PLAYER_DMG;
                if (bonus > 0) tgt.damage(b.damage * bonus);
                if (tgt.armor > 0) tgt.damagePierce(Math.min(b.damage, tgt.armor * HIST_ARMOR_SHRED));
            }
        } catch (Throwable err) {}
        try {
            St xnst = byId.get(apid);
            boolean notSoldier = !isUnitOwner || !soldierOwner.containsKey(o.id());
            if (xnst != null && xnst.pi == PILOT_XAKYNEO && notSoldier) { NeoMk mk = new NeoMk(); mk.pid = apid; mk.t = Time.time; mk.d = b.damage; neoMarks.put(tgt.id, mk); }
            if (xnst != null && xnst.pi == PILOT_XAKYNEO && !tgt.dead && tgt.isValid() && tgt.maxHealth > 0 && tgt.health / tgt.maxHealth > XAKYNEO_HIGHHP_RATIO) {
                float exn = b.damage * XAKYNEO_HIGHHP_DMG;
                try { tgt.damagePierce(exn); } catch (Throwable err) { tgt.damage(exn); }
            }
            // JS: dung mau thap nhat giua "ngay sau don" va "hien tai", khong doi !dead (sat thuong cong them co the da ha muc tieu)
            if (notSoldier && xnst != null && xnst.pi == PILOT_XAKYNEO && tgt.maxHealth > 0 && Math.min(neoHp0, tgt.health) / tgt.maxHealth < XAKYNEO_EXECUTE_RATIO) {
                Hit h = new Hit(); h.pid = apid; h.t = Time.time; lastHitBy.put(tgt.id, h);
                float ex = tgt.x, ey = tgt.y;
                if (!tgt.dead) { try { tgt.damagePierce(tgt.health + tgt.maxHealth); } catch (Throwable err) { try { tgt.kill(); } catch (Throwable err2) {} } }
                logOnce("neo-run", "neo execute fired on " + tgt.type.name + " hp=" + Math.round(tgt.health) + "/" + Math.round(tgt.maxHealth), true);
                if (neoExecuteShow(apid, tgt.id, ex, ey, b, isUnitOwner ? (Unit) o : null, isUnitOwner)) neoExecuteAfter(isUnitOwner ? (Unit) o : plUnit(apid), apid, ex, ey, b.damage);
            }
        } catch (Throwable err) { logOnce("neoexec", "neo execute block failed: " + err, false); }
        try { Hit h = new Hit(); h.pid = apid; h.t = Time.time; lastHitBy.put(tgt.id, h); } catch (Throwable err) {}
        if (!isUnitOwner) return;
        try {
            Player rp = ((Unit) o).getPlayer();
            if (rp != null && isPvpTeam(tgt.team) && spawnLeft(rp.id, SPAWN_RAGE_TICKS) > 0) {
                float rx = tgt.maxHealth * SPAWN_RAGE_MAXHP_PCT / 100f;
                if (rx > 0) { try { tgt.damagePierce(rx); } catch (Throwable err) { tgt.damage(rx); } }
            }
        } catch (Throwable err) {}
    }

    /** Dang ky su kien phan 13. Phan UnitDestroyEvent (JS 5778+) o phan sau. */
    public static void initUpdate() {
        Events.run(Trigger.update, () -> {
            if (autoPauseFixed || Vars.net.client() || !isFull()) return;
            autoPauseFixed = true;
            try {
                mindustry.game.Rules r = Vars.state.rules;
                if (r.pvpAutoPause) { r.pvpAutoPause = false; syncRules(); }
                if (Vars.state.isPaused()) Vars.state.set(mindustry.core.GameState.State.playing);
            } catch (Throwable err) { logOnce("autopause", "cannot disable pvp auto pause: " + err, false); }
        });
        Events.run(Trigger.update, () -> {
            if (!isActive() || Vars.state.isPaused()) return;
            boolean auth = !Vars.net.client(), full = isFull();
            if (auth) {
                eachPl(NewModePvp::eachPlayerServer);
                botT -= Time.delta;
                if (full && botT <= 0) { botT = 60; try { botManage(); } catch (Throwable err) { logOnce("botmanage", "bot manage failed: " + err, false); } }
                if (full) botTick();
                if (full) lateExpCheck();
                if (full) eachPl(NewModePvp::eachBarracks);
                if (full) eachPl(NewModePvp::eachSoldierAI);
                bulletStatT -= Time.delta;
                if (bulletStatT <= 0) { bulletStatT = 30; bulletStatOn = anyBulletStat(); }
                if (bulletStatOn) Groups.bullet.each(NewModePvp::eachBullet);
                wallT -= Time.delta;
                if (full && wallT <= 0) { wallT = 30; eachPl(NewModePvp::eachWall); }
                syncT -= Time.delta;
                if (dirty && syncT <= 0) { dirty = false; syncT = 20; broadcast(); persistStore(); }
                else if (syncT < -300) { syncT = 20; broadcast(); persistStore(); }
                coreT -= Time.delta;
                if (full && coreT <= 0) { coreT = 30; coreStatsTick(30f); }
                passiveT -= Time.delta;
                if (full && passiveT <= 0) { passiveT = PASSIVE_INTERVAL; eachPl(NewModePvp::eachPassiveExp); }
                orphanT -= Time.delta;
                if (orphanT <= 0) { orphanT = 30; Groups.unit.each(NewModePvp::eachOrphan); }
                winT -= Time.delta;
                if (full && winT <= 0) { winT = 60; checkWin(); }
                pruneT -= Time.delta;
                if (pruneT <= 0) { pruneT = 300; prune(); }
            }
            eachPl(NewModePvp::eachPlayerCommon);
            turretT -= Time.delta;
            eachPl(NewModePvp::eachTurretOwner);
            if (turretT <= 0) turretT = 15;
            eachPl(NewModePvp::eachEquip);
            if (!auth) { pruneT -= Time.delta; if (pruneT <= 0) { pruneT = 300; prune(); } }
        });
        Events.run(Trigger.update, () -> {
            if (Vars.net.client() || !isFull() || Vars.state.gameOver || Vars.state.isPaused()) return;
            if (Groups.player.size() == 0) return;
            bonusWaveT += Time.delta;
            if (bonusWaveT < BONUS_WAVE_INTERVAL) return;
            bonusWaveT = 0;
            int w = Math.max(1, Vars.state.wave - 1);
            final Team wt = Vars.state.rules.waveTeam;
            final ArrayList<Unit> olds = new ArrayList<>();
            Groups.unit.each(u -> { if (u.team == wt && !u.dead && u.isValid()) olds.add(u); });
            int left = BONUS_WAVE_MAX_NEW;
            for (int i = 0; i < BONUS_WAVE_TIMES && left > 0; i++) left -= spawnWave(w, left);
            for (Unit ou : olds) {
                if (ou == null || !ou.isValid()) continue;
                try { Call.unitDespawn(ou); } catch (Throwable err) { try { ou.remove(); } catch (Throwable err2) {} }
            }
            try { Call.sendMessage(tr("[scarlet]Đã tự động spawn x" + BONUS_WAVE_TIMES + " đợt quái!", "[scarlet]Auto-spawned x" + BONUS_WAVE_TIMES + " waves!")); } catch (Throwable err) {}
        });
        Events.on(WaveEvent.class, e -> {
            if (Vars.net.client() || !isFull() || Vars.state.gameOver) return;
            spawnWave(Vars.state.wave - 1);
        });
        Events.on(UnitDamageEvent.class, e -> damageEvent(e));
    }


    // ===== PHAN 14: UnitDestroy/WorldLoad, yeu cau tu client (NexNet.on), dem pháo (JS 5778-6220) =====
    // Java NexNet chua co on()/toast (bai 111i) -> tu dang ky packet handler may chu: Vars.netServer.addPacketHandler(kind, (player, payload) -> ...)
    interface Req { void run(Player p, String payload); }
    static final LinkedHashMap<String, Req> reqHandlers = new LinkedHashMap<>();
    static final HashMap<String, Integer> reqCd = new HashMap<>();
    static final HashMap<String, Long> reqLast = new HashMap<>();
    static boolean reqRegistered = false;
    static void onReq(String kind, int cdMs, Req r) { reqHandlers.put(kind, r); reqCd.put(kind, cdMs); }
    /** Chạy một yêu cầu (cooldown + try/catch + log lần đầu). Dùng chung cho packet từ client và cho gọi cục bộ (một mình / host). */
    static void runReq(String kind, Player player, String payload) {
        try {
            if (player == null) { logOnce("reqnull:" + kind, "request " + kind + ": player null", false); return; }
            Req r = reqHandlers.get(kind);
            if (r == null) { logOnce("reqnone:" + kind, "request " + kind + ": chua co handler", false); return; }
            long now = Time.millis(); String key = kind + ":" + player.id;
            Long last = reqLast.get(key);
            Integer cd = reqCd.get(kind);
            if (last != null && cd != null && now - last < cd) return;
            reqLast.put(key, now);
            logOnce("reqrun:" + kind, "request " + kind + " dang chay (payload=" + payload + ")", true);
            r.run(player, payload == null ? "" : payload);
        } catch (Throwable err) { logOnce("req:" + kind, "request " + kind + " failed: " + err, false); }
    }
    static void registerReq() {
        if (reqRegistered) return;
        try {
            if (Vars.netServer == null) return;
            for (Map.Entry<String, Req> e : reqHandlers.entrySet()) {
                final String kind = e.getKey();
                Vars.netServer.addPacketHandler(kind, (player, payload) -> runReq(kind, player, payload));
            }
            reqRegistered = true;
            Log.info("[NexPvp] da dang ky " + reqHandlers.size() + " handler yeu cau");
        } catch (Throwable err) { logOnce("regreq", "register requests failed: " + err, false); }
    }

    static final java.util.HashSet<Integer> expGot = new java.util.HashSet<>(), expGotTeam = new java.util.HashSet<>();
    static void destroyEvent(UnitDestroyEvent e) {
        if (Vars.net.client() || !isActive() || Vars.state.gameOver) return;
        final Unit u = e.unit;
        if (u == null) return;
        if (empixRevive(u)) return;
        try {
            Hit kr = lastHitBy.get(u.id);
            Player up0 = u.getPlayer();
            BotCtx bu0 = botUnitOf(u);
            Integer upid = up0 != null ? Integer.valueOf(up0.id) : (bu0 != null ? Integer.valueOf(bu0.botPl.id) : null);
            if (upid != null) bumpKc(upid, 2);
            if (bu0 != null && kr != null && Time.time - kr.t <= KILL_CREDIT_TICKS && kr.pid != bu0.botPl.id) {
                bu0.botKillerId = kr.pid;
                Log.info("[NexPvp] bot bị hạ bởi player " + kr.pid + ", quét và học theo pháo của người đó");
            }
            if (kr != null && Time.time - kr.t <= KILL_CREDIT_TICKS) bumpKc(kr.pid, upid != null ? 1 : 0);
        } catch (Throwable err) {}
        Hit th = teamHit.remove(u.id);
        Team hitTeam = (th != null && Time.time - th.t <= KILL_CREDIT_TICKS) ? th.team : null;
        try {
            Integer sPid = soldierOwner.remove(u.id);
            if (sPid != null) {
                final float gx = killExp(u) * SOLDIER_EXP_PCT / 100f;
                final int sp = sPid;
                Groups.player.each(p -> { if (p.id == sp && gx > 0) addExp(p, gx); });
                Player sk = pvpKiller(u);
                if (sk != null && sk.team() != u.team) { float skx = killExp(u) * KILL_EXP_PCT / 100f; if (skx > 0) addExp(sk, skx); }
            }
            Player dp = u.getPlayer();
            lastHitBy.remove(u.id);
            if (dp != null && isFull() && isPvpTeam(u.team)) {
                startRespawnLock(dp);
                deadUnit.put(dp.id, u.id);
                spawnBuff.remove(dp.id);
                Player killer = pvpKiller(u);
                if (killer != null && killer.team() != dp.team()) {
                    float dx = killExp(u) * DEATH_EXP_PCT / 100f;
                    if (dx > 0) addExp(dp, dx);
                    float kx = killExp(u) * KILL_EXP_PCT / 100f;
                    if (kx > 0) addExp(killer, kx);
                }
            }
        } catch (Throwable err) {}
        if (u.team == Vars.state.rules.waveTeam) {
            final float gained = killExp(u);
            if (gained > 0) {
                final float rx = u.x, ry = u.y;
                expGot.clear(); expGotTeam.clear();
                if (hitTeam != null) expGotTeam.add(hitTeam.id);
                eachPl(pl -> {
                    Unit pu = pl.unit();
                    if (pu == null || !pu.isValid()) return;
                    float dx = pu.x - rx, dy = pu.y - ry;
                    if (dx * dx + dy * dy <= CREDIT_RADIUS * CREDIT_RADIUS) { addExp(pl, gained); expGot.add(pl.id); expGotTeam.add(pl.team().id); }
                });
                eachPl(pl -> { if (!expGot.contains(pl.id) && expGotTeam.contains(pl.team().id)) addExp(pl, gained); });
            }
        }
    }

    static void worldLoad() {
        byId = new HashMap<>(); teamsCache = null; assignCache = null; coreZoneFloor = null; coreZoneChecked = false;
        ecCache.clear(); seenPl.clear(); defsCache = null; equipAng.clear(); equipBeam.clear(); hostOwner.clear();
        if (!Vars.headless) stopBuild();
        bonusWaveT = 0; matchT = 0; lateExpAnnounced = false; shieldMinT = 0; coreShield.clear(); shieldRecs.clear(); shieldEnded = false; shieldSyncT = 0;
        shieldView = new ShieldView(); shieldFlash.clear(); shieldSeenHit.clear();
        vanhauField.clear();
        resetUiState();
        respawnLock.clear(); deadUnit.clear(); spawnBuff.clear(); swapPending.clear(); prevUnit.clear(); extPvp.clear();
        barrackT.clear(); rally.clear(); soldiers.clear(); soldierOwner.clear(); mountSt.clear(); lastHitBy.clear(); teamHit.clear();
        passiveT = PASSIVE_INTERVAL; logged.clear(); rolled.clear(); rolledN = 0; winning = false; seenBoth = false; dirty = false; syncT = 0; stVer++;
        bots.clear(); BC = new BotCtx(); autoPauseFixed = false;
        restoreStore();
        if (!Vars.net.client()) {
            Time.run(2f, () -> {
                if (Vars.state == null || !Vars.state.isGame() || !isPvpMap()) return;
                arc.struct.StringMap tg = getTags();
                if (tg != null && "1".equals(String.valueOf(tg.get(TAG_ACTIVE, "0")))) return;
                onBegin();
            });
        }
    }

    static St reqState(Player player, boolean anyDist) {
        if (!isActive() || player == null) return null;
        Unit u = player.unit();
        CoreBlock.CoreBuild core = player.team().core();
        if (core == null) return null;
        if (u == null || !u.isValid() || u.dead) return bind(player);
        if (!anyDist && !upAnywhere() && Mathf.dst(u.x, u.y, core.x, core.y) > core.block.size * Vars.tilesize / 2f + NEAR_CORE) {
            toast(player, tr("[scarlet]Hãy về gần lõi để nâng cấp!", "[scarlet]Return near your core to upgrade!"));
            return null;
        }
        return bind(player);
    }
    static St reqState(Player player) { return reqState(player, false); }
    static int builtCount(St st) { int n = 0; for (int i = 0; i < st.p.size(); i++) if (st.p.get(i) >= 0) n++; return n; }
    static int equipCount(St st) { int n = 0; for (int i = 0; i < st.p.size(); i++) if (st.p.get(i) == EQUIPPED) n++; return n; }
    static int equipSlots(St st) { return Math.min(EQUIP_SLOT_CAP, Math.max(1, st.lv)); }
    static St localState() { return Vars.player == null ? null : byId.get(Vars.player.id); }
    static boolean nearOwnCore() {
        if (Vars.player == null) return false;
        if (upAnywhere() && Vars.player.team().core() != null) return true;
        Unit u = Vars.player.unit();
        CoreBlock.CoreBuild core = Vars.player.team().core();
        if (core == null) return false;
        if (u == null || !u.isValid() || u.dead || Vars.player.dead()) return true;
        return Mathf.dst(u.x, u.y, core.x, core.y) <= core.block.size * Vars.tilesize / 2f + NEAR_CORE;
    }
    static boolean validPilot(int k) { return k == 0 || k == PILOT_XIKI || k == PILOT_EMPIX || k == PILOT_XAKY || k == PILOT_HIST || k == PILOT_XAKYNEO || k == PILOT_VANHAU; }
    static String pilotToast(int k) {
        return k == PILOT_XIKI ? tr("[accent]Đã chiêu mộ Xiki Pilot!", "[accent]Recruited Xiki Pilot!") :
            k == PILOT_XAKY ? tr("[accent]Đã chiêu mộ Xaky Pilot!", "[accent]Recruited Xaky Pilot!") :
            k == PILOT_HIST ? tr("[accent]Đã chiêu mộ HistekaIMsp Pilot!", "[accent]Recruited HistekaIMsp Pilot!") :
            k == PILOT_EMPIX ? tr("[accent]Đã chiêu mộ Empixeand Pilot!", "[accent]Recruited Empixeand Pilot!") :
            k == PILOT_XAKYNEO ? tr("[accent]Đã chiêu mộ Xaky(Neo) Pilot!", "[accent]Recruited Xaky(Neo) Pilot!") :
            k == PILOT_VANHAU ? tr("[accent]Đã chiêu mộ VanHau Pilot!", "[accent]Recruited VanHau Pilot!") :
            tr("[lightgray]Đã cho pilot nghỉ.", "[lightgray]Pilot dismissed.");
    }
    static final String BAD_SPOT = "[scarlet]Vị trí không hợp lệ: phải là nền đặt lõi còn trống ở nửa map phe ta!";
    static void defineRequests() {
        onReq(REQ_UP, 60, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            Integer k = leadInt(payload);
            if (k == null || !(k >= 0 && k < STATS.length)) return;
            if (st.pts < 1 || st.s[k] >= statMax(k)) return;
            st.pts--; st.s[k]++; changed();
        });
        onReq(REQ_PILOT, 60, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            Integer k0 = leadInt(payload);
            if (k0 == null || !validPilot(k0)) return;
            int k = k0;
            if (st.pi == k) return;
            if (k == PILOT_EMPIX && st.lv < EMPIX_MIN_LEVEL) return;
            if (st.pi == PILOT_XAKYNEO) xakyNeoStart.remove(player.id);
            if (st.pi == PILOT_VANHAU) { vanhauCdAt.remove(player.id); vanhauShieldOn.remove(player.id); vanhauField.remove(player.id); }
            st.pi = k;
            if (k == PILOT_XAKYNEO) xakyNeoStart.put(player.id, Time.time);
            toast(player, pilotToast(k));
            changed();
        });
        onReq(REQ_UNIT, 400, (player, payload) -> {
            St st = reqState(player);
            if (st == null || st.lv < FREE_SWITCH_LEVEL) return;
            Integer k0 = leadInt(payload);
            if (k0 == null || !(k0 >= 1 && k0 <= TIER_MAX)) return;
            int k = k0, nu = (k == tierCap(st.lv)) ? 0 : k;
            if (st.u == nu) return;
            st.u = nu;
            toast(player, tr("[accent]Đã đổi sang unit: ", "[accent]Switched unit: ") + tierType(k).localizedName);
            changed();
        });
        onReq(REQ_BUY, 60, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            Def d = droneDefs().map.get(payload.split("\\|")[0]);
            if (d == null) return;
            try { if (Vars.state.isCampaign() && !d.block.unlockedNowHost()) { toast(player, tr("[scarlet]Pháo này chưa được nghiên cứu!", "[scarlet]This turret is not researched yet!")); return; } } catch (Throwable err) {}
            if (!multiTurretOn() && ownedCount(st, d.id) >= 1) { toast(player, tr("[scarlet]Đang tắt chế độ nhiều pháo cùng loại (Cài đặt Newex)!", "[scarlet]Multiple turrets of the same type is turned off (Newex settings)!")); return; }
            int price = turretCost(st, d);
            if (st.pts < price) return;
            if (!d.equip) { toast(player, tr("[scarlet]Pháo này không thể gắn lên unit!", "[scarlet]This turret cannot be mounted!")); return; }
            st.pts -= price; st.d.add(d.id); st.p.add(IN_BAG);
            toast(player, tr("[accent]Đã mua " + d.block.localizedName + " - xem trong Túi đồ.", "[accent]Bought " + d.block.localizedName + " - check your Bag."));
            changed();
        });
        // payload "idx|mode": e = lap len unit, b = xay quanh loi, u (mac dinh) = go/thu hoi ve tui
        onReq(REQ_MODE, 60, (player, payload) -> {
            String[] parts = payload.split("\\|");
            St st = reqState(player, parts.length > 1 && parts[1].equals("b")); if (st == null) return;
            Integer idx0 = leadInt(parts[0]);
            String mode = parts.length > 1 ? parts[1] : "u";
            if (idx0 == null || !(idx0 >= 0 && idx0 < st.d.size())) return;
            int idx = idx0;
            Def d = droneDefs().map.get(st.d.get(idx));
            if (d == null) return;
            int cur = st.p.get(idx);
            if (mode.equals("u")) {
                if (cur == EQUIPPED) st.p.set(idx, IN_BAG);
                else if (cur >= 0) { removeTurret(cur, d); st.p.set(idx, IN_BAG); }
                else return;
                changed();
                return;
            }
            if (cur >= 0 || cur == EQUIPPED) return;
            if (mode.equals("e")) {
                if (!d.equip || !d.mount) { toast(player, tr("[scarlet]Pháo này không thể gắn lên unit!", "[scarlet]This turret cannot be mounted!")); return; }
                if (equipCount(st) >= equipSlots(st)) { toast(player, tr("[scarlet]Hết ô gắn!", "[scarlet]No free mount slot!")); return; }
                st.p.set(idx, EQUIPPED);
            } else if (mode.equals("b")) {
                if (builtCount(st) >= maxBuilt()) { toast(player, tr("[scarlet]Đã đạt giới hạn pháo xây!", "[scarlet]Built turret limit reached!")); return; }
                Integer tx = parts.length > 2 ? leadInt(parts[2]) : null, ty = parts.length > 3 ? leadInt(parts[3]) : null;
                int pos = (tx == null || ty == null) ? -1 : placeTurret(player, d, tx, ty);
                if (pos < 0) { toast(player, tr(BAD_SPOT, "[scarlet]Invalid spot: needs free core-zone floor on your half of the map!")); return; }
                st.p.set(idx, pos);
            } else return;
            changed();
        });
        onReq(REQ_RESET, 300, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            int refund = 0;
            if (payload.equals("stat")) {
                for (int k = 0; k < STATS.length; k++) { if (k == STAT_ARMY) continue; refund += st.s[k]; st.s[k] = 0; }
            } else {
                refund += st.s[STAT_ARMY]; st.s[STAT_ARMY] = 0;
                for (int i = 0; i < st.b; i++) refund += barrackCost(i);
                if (st.b > 0) { st.b = 0; try { removeSoldiers(player.id); } catch (Throwable err) {} barrackT.remove(player.id); }
                if (st.w != WALL_NONE) {
                    for (int wi = 0; wi <= st.wl; wi++) refund += WALL_COST[wi];
                    if (st.w >= 0) removeTurret(st.w, wallBlock(st.wl));
                    st.w = WALL_NONE; st.wl = 0;
                }
            }
            if (refund <= 0) return;
            st.pts += refund; st.exp = 0;
            toast(player, tr("[accent]Đã reset, hoàn ", "[accent]Reset, refunded ") + refund + tr(" điểm. EXP cấp hiện tại = 0.", " pts. Current level EXP = 0."));
            changed();
        });
        onReq(REQ_CMD, 100, (player, payload) -> {
            St st = reqState(player, true);
            if (st == null || st.b < 1) return;
            String c = payload; int pid = player.id;
            if (c.equals("t")) {
                st.cmd = st.cmd == 1 ? 0 : 1;
                rally.remove(pid);
                toast(player, st.cmd == 1 ? tr("[accent]Ra lệnh cho lính: BẬT (lính theo bạn; Shift+9 tập kết tại con trỏ, Shift+0 theo bạn)", "[accent]Soldier commands: ON (soldiers follow you; Shift+9 rally at cursor, Shift+0 follow)")
                    : tr("[accent]Ra lệnh cho lính: TẮT (lính tự đi đánh)", "[accent]Soldier commands: OFF (soldiers march automatically)"));
            } else if (st.cmd == 1) {
                if (c.equals("f")) rally.remove(pid);
                else if (c.equals("h")) { Unit u = player.unit(); if (u != null && u.isValid()) { Rally r = new Rally(); r.x = u.x; r.y = u.y; rally.put(pid, r); } }
                else if (c.startsWith("au:")) {
                    Integer id = leadInt(c.substring(3));
                    Unit tu = id != null ? Groups.unit.getByID(id) : null;
                    if (tu != null && tu.isValid() && !tu.dead && tu.team != player.team()) { Rally r = new Rally(); r.x = tu.x; r.y = tu.y; r.uid = tu.id; rally.put(pid, r); }
                } else if (c.startsWith("ab:")) {
                    Integer bp = leadInt(c.substring(3));
                    Building tb = bp != null ? Vars.world.build(bp) : null;
                    if (tb != null && tb.isValid() && tb.team != player.team()) { Rally r = new Rally(); r.x = tb.x; r.y = tb.y; r.bpos = bp; rally.put(pid, r); }
                } else if (c.startsWith("r:")) {
                    String[] xy = c.substring(2).split(",");
                    float rx = xy.length > 0 ? pf(xy[0]) : Float.NaN, ry = xy.length > 1 ? pf(xy[1]) : Float.NaN;
                    if (!Float.isNaN(rx) && !Float.isNaN(ry) && !Float.isInfinite(rx) && !Float.isInfinite(ry)) {
                        Rally r = new Rally(); r.x = Mathf.clamp(rx, 0f, Vars.world.width() * Vars.tilesize); r.y = Mathf.clamp(ry, 0f, Vars.world.height() * Vars.tilesize); rally.put(pid, r);
                    }
                }
            }
            changed();
        });
        onReq(REQ_BARR, 300, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            int lv = st.b;
            if (lv >= BARRACKS.length) return;
            int cost = barrackCost(lv);
            if (st.pts < cost) return;
            st.pts -= cost; st.b = lv + 1;
            toast(player, tr("[accent]Nhà máy lính cấp ", "[accent]Barracks level ") + st.b);
            changed();
        });
        onReq(REQ_WALL, 60, (player, payload) -> {
            String[] parts = payload.split("\\|");
            String mode = parts[0];
            St st = reqState(player, mode.equals("b")); if (st == null) return;
            int w = st.w, lv = st.wl;
            if (mode.equals("buy")) {
                if (w != WALL_NONE || st.pts < WALL_COST[0]) return;
                st.pts -= WALL_COST[0]; st.w = IN_BAG; st.wl = 0;
                toast(player, tr("[accent]Đã mua copper-wall (+1 giới hạn lính) - xem trong Túi đồ.", "[accent]Bought copper-wall (+1 soldier cap) - check your Bag."));
                changed();
            } else if (mode.equals("b")) {
                if (w != IN_BAG) return;
                Integer tx = parts.length > 1 ? leadInt(parts[1]) : null, ty = parts.length > 2 ? leadInt(parts[2]) : null;
                int pos = (tx == null || ty == null) ? -1 : placeWall(player, lv, tx, ty);
                if (pos < 0) { toast(player, tr(BAD_SPOT, "[scarlet]Invalid spot: needs free core-zone floor on your half of the map!")); return; }
                st.w = pos; changed();
            } else if (mode.equals("up")) {
                if (!(w >= 0) || lv >= WALL_CHAIN.length - 1) return;
                int cost = WALL_COST[lv + 1];
                if (st.pts < cost) return;
                Building b = Vars.world.build(w); Block nb = wallBlock(lv + 1);
                if (b == null || !b.isValid() || nb == null) return;
                Tile tile = b.tile;
                tile.setNet(nb, player.team(), 0);
                if (tile.build == null || tile.build.block != nb) return;
                st.pts -= cost; st.wl = lv + 1;
                toast(player, tr("[accent]Nâng cấp tường: ", "[accent]Wall upgraded: ") + nb.localizedName + " (+" + wallBonus(st) + tr(" giới hạn lính)", " soldier cap)"));
                changed();
            }
        });
        onReq(REQ_SELL, 60, (player, payload) -> {
            St st = reqState(player); if (st == null) return;
            Integer idx0 = leadInt(payload);
            if (idx0 == null || !(idx0 >= 0 && idx0 < st.d.size())) return;
            int idx = idx0;
            Def d = droneDefs().map.get(st.d.get(idx));
            if (d == null) return;
            int sellVal = turretSellValue(st, d);
            removeTurret(st.p.get(idx), d);
            st.d.remove(idx); st.p.remove(idx);
            st.pts += sellVal;
            changed();
        });
        onReq(REQ_PT, 100, (player, payload) -> {
            if (!isActive() || player == null) return;
            Team tm = player.team();
            if (payload.equals("foe")) {
                Team found = null;
                for (Player o : Groups.player) if (o.team() != tm) found = o.team();
                for (int bi = 0; bi < bots.size(); bi++) if (found == null && bots.get(bi).botPl.team() != tm) found = bots.get(bi).botPl.team();
                if (found == null) return;
                tm = found;
            }
            for (Player o : Groups.player) if (o.team() == tm) bind(o).pts++;
            for (int bi = 0; bi < bots.size(); bi++) if (bots.get(bi).botPl.team() == tm) bind(bots.get(bi).botPl).pts++;
            changed();
        });
        onReq(REQ_CHEAT, 500, (player, payload) -> {
            if (!isActive() || player == null) return;
            St st = bind(player);
            st.pts += CHEAT_POINTS;
            toast(player, tr("[accent]+" + CHEAT_POINTS + " điểm nâng cấp!", "[accent]+" + CHEAT_POINTS + " upgrade points!"));
            changed();
        });
    }
    /** Dang ky su kien phan 14. Gom: initBase + initDrawShield + initPilot + initUpdate + initNet (init() tong o phan cuoi). */
    public static void initNet() {
        defineRequests();
        Events.on(UnitDestroyEvent.class, e -> destroyEvent(e));
        Events.on(WorldLoadEvent.class, e -> worldLoad());
        Events.on(ServerLoadEvent.class, e -> registerReq());
        Events.on(ClientLoadEvent.class, e -> registerReq());
        Events.on(WorldLoadEvent.class, e -> registerReq());
        registerReq();
    }


    // ===== PHAN 15: thong ke kill, normalizeRules, API cong khai, cai dat Newex (JS 396, 7066-7092, 8630-8833) =====
    /** JS `let shredMap = new Map()` chi doc, khong bao gio ghi -> luon rong (parity). */
    static final HashMap<BulletType, Float> shredMap = new HashMap<>();
    static void bumpKc(int pid, int i) {
        St st = byId.get(pid);
        if (st == null) return;
        st.kc[i]++;
        dirty = true;
    }
    static String plName(int pid) {
        if (isBotId(pid)) return "[orange]NexBot" + (BOT_ID - pid + 1) + "[]";
        String n = "#" + pid;
        for (Player p : Groups.player) if (p.id == pid) n = String.valueOf(p.name);
        return n;
    }
    static String kcText(St st) {
        int[] k = st.kc;
        return "[lightgray]" + tr("Diệt mục tiêu: ", "Target kills: ") + "[white]" + k[0] + "[]  [lightgray]" + tr("Diệt player: ", "Player kills: ") + "[white]" + k[1] + "[]  [lightgray]" + tr("Bị hạ: ", "Deaths: ") + "[white]" + k[2] + "[]";
    }
    static void normalizeRules(mindustry.game.Rules r, Team[] tl) {
        setf(r, "possessionAllowed", false);
        setf(r, "pvpAutoPause", false);
        try { r.infiniteResources = false; } catch (Throwable err) {}
        for (Team t : tl) {
            try {
                mindustry.game.Rules.TeamRule rule = r.teams.get(t);
                rule.infiniteResources = false;
                rule.fillItems = false;
                setf(rule, "infiniteAmmo", true);
                setf(rule, "cheat", true);
            } catch (Throwable err) {}
        }
    }
    static void normalizeRules(mindustry.game.Rules r, Seq<Team> tl) { normalizeRules(r, tl.toArray(Team.class)); }

    // --- API cong khai (JS global.NexPvp): file khac goi NewModePvp.xxx ---
    public static final String API_MAP_ID = MAP_ID;
    public static boolean apiIsActive() { return isActive(); }
    public static void apiPersistStore() { persistStore(); }
    public static void onBegin() {
        winning = false; seenBoth = false; byId = new HashMap<>(); teamsCache = null;
        mindustry.game.Rules r = Vars.state.rules;
        r.tags.put(TAG_ACTIVE, "1");
        store = new LinkedHashMap<>();
        r.tags.put(TAG_STORE, "{}");
        matchT = 0; lateExpAnnounced = false; shieldMinT = 0; coreShield.clear(); shieldEnded = false;
        r.waves = true; r.waveTimer = true; r.attackMode = false; r.pvp = true; r.winWave = 0; r.waitEnemies = false; r.dropZoneRadius = 0;
        teamsCache = null; assignCache = null; seenPl.clear();
        Seq<Team> tl = pvpTeams();
        r.defaultTeam = tl.get(0);
        r.waveTeam = Team.crux;
        normalizeRules(r, tl);
        allowWaveUnits(r);
        evictRed();
        r.waveSpacing = WAVE_SPACING;
        r.initialWaveSpacing = FIRST_WAVE;
        r.spawns = new Seq<>();
        r.bannedBlocks.addAll(Vars.content.blocks());
        Vars.state.wave = 1;
        Vars.state.wavetime = FIRST_WAVE;
        syncRules();
        stVer++;
    }
    public static void onResume() {
        winning = false; seenBoth = false; teamsCache = null;
        Vars.state.rules.waveTeam = Team.crux;
        normalizeRules(Vars.state.rules, pvpTeams());
        allowWaveUnits(Vars.state.rules);
        evictRed();
        syncRules();
        Vars.ui.showInfoToast(tr("PvP Cày Cấp: đã tiếp tục trận đấu", "PvP Leveling: match resumed"), 3f);
    }
    /** JS api.addSettings(outer): them nut vao bang cai dat; tra ve ham don dep (rong). */
    public static Runnable addSettings(Table outer) {
        outer.button(tr("NewMode PvP - Tùy chỉnh", "NewMode PvP - Settings"), Icon.settings, () -> {
            BaseDialog d = new BaseDialog("NewMode PvP");
            Table c = new Table();
            c.top().margin(10);
            final Runnable save = buildSettings(c);
            d.cont.add(new ScrollPane(c)).grow().row();
            d.hidden(() -> { try { save.run(); } catch (Throwable err) {} });
            d.addCloseButton();
            d.show();
        }).size(280, 54).pad(6).row();
        return () -> {};
    }
    static TextField numField(Table content, String label, int cur, int maxLen, String suffix, boolean wrap) {
        content.add(label).padBottom(4).row();
        Table t = new Table();
        TextField f = t.field(String.valueOf(cur), text -> {}).width(120).get();
        f.setFilter(TextField.TextFieldFilter.digitsOnly);
        f.setMaxLength(maxLen);
        if (wrap) t.add(suffix).padLeft(8).wrap().width(260); else t.add(suffix).padLeft(8);
        content.add(t).pad(5).row();
        return f;
    }
    static int clampParse(TextField f, int def, int min, int max) {
        Integer v = leadInt(f.getText());
        int x = (v == null || v < min) ? def : v;
        return Math.min(x, max);
    }
    static Runnable buildSettings(Table content) {
        content.add(tr("[accent]-- PVP CÀY CẤP --[]", "[accent]-- PVP LEVELING --[]")).padTop(10).row();
        final TextField expField = numField(content, tr("% EXP nhận vào (PvP):", "EXP received (%) (PvP):"), expPercent(), 4, tr("% (100 = bình thường)", "% (100 = normal)"), false);
        final boolean[] v = {multiTurretOn(), buildZoneOn(), returnZoneOn(), upAnywhere(), waveRandomOn(), extEnabled(), pvbOn(), bvbOn()};
        final String[] vi = {"Cho phép trang bị nhiều pháo cùng loại (PvP)", "Chỉ xây pháo đã mua trên nền đặt lõi", "Cần đứng trên nền đặt lõi để biến về", "Không cần ở gần lõi vẫn vào trang nâng cấp", "Địch từ wave spawn ngẫu nhiên khắp bản đồ (tắt = chỉ ở giữa)"};
        final String[] en = {"Allow multiple turrets of the same type (PvP)", "Build owned turrets only on core-zone floor", "Must stand on core-zone to return to core", "Open upgrade page without being near core", "Wave enemies spawn randomly across the map (off = center only)"};
        for (int i = 0; i < 4; i++) { final int k = i; content.check(tr(vi[k], en[k]), v[k], (Boolc) b -> v[k] = b).pad(5).left().row(); }
        content.check(tr(vi[4], en[4]), v[4], (Boolc) b -> v[4] = b).pad(5).left().row();
        final TextField tField = numField(content, tr("Địa bàn mỗi phe (ô quanh lõi, 0 = nửa map; tối đa " + LIMIT_TERR + "):", "Territory per team (tiles around core, 0 = half map; max " + LIMIT_TERR + "):"),
            Core.settings.getInt(KEY_TERR, 0), 3, tr("ô (map ngoài: 0 = " + EXT_BUILD_RANGE + ")", "tiles (other maps: 0 = " + EXT_BUILD_RANGE + ")"), false);
        content.check(tr("Dùng NewMode ở map khác (EXP diệt quái, biến về đổi unit, xây dựng)", "Use NewMode on other maps (kill EXP, return toggles unit, building)"), v[5], (Boolc) b -> v[5] = b).pad(5).left().row();
        content.check(tr("Player vs Bot: mọi người chơi cùng 1 phe đấu phe bot (thêm người = thêm bot, bot càng thông minh)", "Player vs Bot: all players on one side vs the bot team (more players = more bots, smarter bots)"), v[6], (Boolc) b -> v[6] = b).pad(5).left().row();
        content.check(tr("Bot vs Bot: người chơi chỉ xem, 2 phe đều là bot (ưu tiên hơn Player vs Bot; áp dụng khi mở map; phím 6 đổi camera bot)", "Bot vs Bot: players only watch, both sides are bots (overrides Player vs Bot; applies on map open; key 6 cycles bot cameras)"), v[7], (Boolc) b -> v[7] = b).pad(5).left().row();
        final TextField drField = numField(content, tr("Giới hạn pháo xây gần lõi (PvP):", "Built turret limit (PvP):"), maxBuilt(), 2, tr("pháo (0 - " + LIMIT_BUILT + ")", "turrets (0 - " + LIMIT_BUILT + ")"), false);
        final TextField mlField = numField(content, tr("Cấp tối đa (PvP):", "Max level (PvP):"), maxLevel(), 3, tr("cấp (1 - " + LIMIT_MAXLV + ", mặc định " + MAX_LEVEL + ")", "level (1 - " + LIMIT_MAXLV + ", default " + MAX_LEVEL + ")"), false);
        final TextField shField = numField(content, tr("Thời gian khiên bất tử của lõi đầu trận (phút, 0 = tắt):", "Core invincible shield time at match start (minutes, 0 = off):"), shieldMinutes(), 4,
            tr("phút (0 - " + LIMIT_SHIELD_MIN + ", mặc định " + DEFAULT_SHIELD_MIN + "; " + LIMIT_SHIELD_MIN + " = lõi bất tử vĩnh viễn)", "min (0 - " + LIMIT_SHIELD_MIN + ", default " + DEFAULT_SHIELD_MIN + "; " + LIMIT_SHIELD_MIN + " = core invincible forever)"), true);
        return () -> {
            Core.settings.put(KEY_SHIELD, clampParse(shField, DEFAULT_SHIELD_MIN, 0, LIMIT_SHIELD_MIN));
            Core.settings.put(KEY_MAXLV, clampParse(mlField, MAX_LEVEL, 1, LIMIT_MAXLV));
            Core.settings.put(KEY_EXP, clampParse(expField, DEFAULT_EXP, 0, LIMIT_EXP));
            Core.settings.put(KEY_BUILT, clampParse(drField, DEFAULT_BUILT, 0, LIMIT_BUILT));
            Core.settings.put(KEY_BZONE, v[1]); Core.settings.put(KEY_RZONE, v[2]); Core.settings.put(KEY_UPANY, v[3]); Core.settings.put(KEY_WRAND, v[4]);
            Core.settings.put(KEY_TERR, clampParse(tField, 0, 0, LIMIT_TERR));
            Core.settings.put(KEY_EXT, v[5]); Core.settings.put(KEY_MULTI, v[0]); Core.settings.put(KEY_PVB, v[6]); Core.settings.put(KEY_BVB, v[7]);
            invalidateCfgCache();
        };
    }


    // ===== PHAN 16: tien ich giao dien client (JS 6222-6496) =====
    static int ver = 0, lastUpTab = 0;
    @SuppressWarnings("unchecked")
    static ArrayList<Object>[] recentUp = new ArrayList[]{new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>()};
    @SuppressWarnings("unchecked")
    static ArrayList<Object>[] viewUp = new ArrayList[]{new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>()};

    static String sigCache = ""; static long sigAt = 0; static int sigN = 0;
    /** JS curVer(): chuoi phien ban giao dien = ver + chu ky trang thai (bo qua exp/cache) de biet khi nao can dung lai. */
    static String curVer() {
        long t = Time.millis();
        if (t - sigAt > 200) {
            sigAt = t;
            String sg = "";
            try {
                St st = localState();
                if (st != null) sg = st.lv + "|" + st.pts + "|" + st.u + "|" + Arrays.toString(st.s) + "|" + st.d + "|" + st.p + "|" + st.b + "|" + st.pi + "|" + Arrays.toString(st.kc) + "|" + st.w + "|" + st.wl + "|" + st.cmd;
            } catch (Throwable err) {}
            if (!sg.equals(sigCache)) { sigCache = sg; sigN++; }
        }
        return ver + "|" + sigN;
    }
    static void touchUp(int tab, Object key) {
        ArrayList<Object> a = recentUp[tab];
        a.remove(key);
        a.add(0, key);
        lastUpTab = tab;
    }
    static void noteTouch(String kind, String payload) {
        try {
            String[] parts = payload.split("\\|");
            St st = localState();
            if (kind.equals(REQ_BARR)) touchUp(3, "b");
            else if (kind.equals(REQ_PILOT)) { Integer pk = leadInt(parts[0]); if (pk != null && pk == 0 && st != null) pk = st.pi; if (pk != null && pk > 0) pilotTouch(pk); }
            else if (kind.equals(REQ_UP)) { Integer k = leadInt(parts[0]); if (k != null) touchUp(1, k); }
            else if (kind.equals(REQ_UNIT)) { Integer k = leadInt(parts[0]); if (k != null) touchUp(0, k); }
            else if (kind.equals(REQ_BUY)) touchUp(2, parts[0]);
            else if ((kind.equals(REQ_MODE) || kind.equals(REQ_SELL)) && st != null) {
                Integer idx = leadInt(parts[0]);
                if (idx != null && idx >= 0 && idx < st.d.size()) touchUp(2, st.d.get(idx));
            }
        } catch (Throwable err) {}
    }
    static ArrayList<Object> orderedBy(int tab, ArrayList<Object> base) {
        ArrayList<Object> seen = viewUp[tab], out = new ArrayList<>();
        for (Object o : seen) if (base.contains(o)) out.add(o);
        for (Object o : base) if (!out.contains(o)) out.add(o);
        return out;
    }
    /** JS send(kind,payload): ghi nho thao tac + gui len may chu (Java: Call.serverPacketReliable). */
    static void send(String kind, String payload) {
        if (kind.equals(REQ_CMD)) cmdMark = null;
        noteTouch(kind, payload);
        logOnce("send:" + kind, "send " + kind + " payload=" + payload + " client=" + Vars.net.client(), true);
        // Một mình / host (không phải client từ xa): chạy handler ngay tại chỗ, không phụ thuộc đường đi của packet.
        if (!Vars.net.client()) { runReq(kind, Vars.player, payload); return; }
        try { Call.serverPacketReliable(kind, payload); } catch (Throwable err) { logOnce("sendreq", "send " + kind + " failed: " + err, false); }
    }

    static float uiScale() { float s = 1; try { s = Scl.scl(1f); } catch (Throwable err) {} return s > 0 ? s : 1f; }
    static float screenW() {
        try { float w = Core.scene.getWidth(); if (w > 0) return w; } catch (Throwable err) {}
        return Core.graphics.getWidth() / uiScale();
    }
    static float screenH() {
        try { float h = Core.scene.getHeight(); if (h > 0) return h; } catch (Throwable err) {}
        return Core.graphics.getHeight() / uiScale();
    }
    static boolean shortScreen() { return screenH() < 500; }
    static boolean narrowScreen() { return screenW() < 520 || screenH() > screenW(); }
    static boolean portraitScreen() { return screenH() > screenW(); }
    static float panelW() {
        float margin = narrowScreen() ? 90 : (shortScreen() ? 24 : 40);
        float w = Math.max(200f, Math.min(screenW() - margin, narrowScreen() ? 480f : 640f));
        return Math.min(w, Math.max(160f, screenW() - (narrowScreen() ? 90 : 40)));
    }
    static float btnH() { if (!Vars.mobile) return 38f; return shortScreen() ? 44f : 52f; }
    static float headH() { return (shortScreen() ? 58 : 80) + btnH() + 16; }
    static float listH(Float extra) {
        float sh = screenH();
        float reserve = (shortScreen() ? 40 : 56) + (shortScreen() ? 52 : 72) + 24 + (extra == null ? headH() : extra) + (Vars.mobile ? 32 : 0);
        return Math.max(100f, Math.min(sh - reserve, sh * 0.8f));
    }
    static float listH() { return listH(null); }
    static void addClose(BaseDialog d) {
        if (!shortScreen()) { d.addCloseButton(); return; }
        d.buttons.defaults().size(160, 44);
        d.buttons.button("@back", Icon.left, () -> d.hide());
        d.closeOnBack();
    }
    static int gridCols() { return !narrowScreen() && panelW() >= 560 ? 2 : 1; }
    static int cardW() { return (int) Math.floor((panelW() - 36) / gridCols()) - 8; }
    static float cardMinW() { return narrowScreen() ? 170f : 260f; }
    static float measureHeight(Cons<Table> buildFn) {
        Table probe = new Table();
        buildFn.get(probe);
        probe.pack();
        return probe.getPrefHeight();
    }

    static String numStr(float v) {
        String s = Strings.fixed(v, 2);
        if (s.indexOf('.') >= 0) { s = s.replaceAll("0+$", ""); if (s.endsWith(".")) s = s.substring(0, s.length() - 1); }
        return s;
    }
    static void unitRow(Table p, St st, int t, int cur, float w) {
        UnitType ut = tierType(t);
        Table row = new Table(Styles.black6);
        row.margin(8);
        float selW = narrowScreen() ? 84 : 110;
        row.add(new Image(ut.uiIcon)).size(narrowScreen() ? 32 : 40).padRight(narrowScreen() ? 6 : 10);
        Table col = new Table();
        col.left();
        col.add("[white]" + ut.localizedName + "[]").left().row();
        col.add("[lightgray]" + (isExtraTier(t) ? tr("Đặc biệt", "Special") : (tr("Bậc ", "Tier ") + t)) + "[]").left();
        row.add(col).growX().left().minWidth(0);
        if (t == cur) row.add("[accent]" + tr("Đang dùng", "In use") + "[]").width(selW).center();
        else row.button(tr("Chọn", "Select"), () -> send(REQ_UNIT, String.valueOf(t))).size(selW, btnH());
        p.add(row).width(w).pad(4).row();
    }
    static void resetBtn(Table p, St st, float w, String mode) {
        int refund = 0;
        if (mode.equals("stat")) { for (int k = 0; k < STATS.length; k++) if (k != STAT_ARMY) refund += st.s[k]; }
        else {
            refund += st.s[STAT_ARMY];
            for (int i = 0; i < st.b; i++) refund += barrackCost(i);
        }
        TextButton b = p.button(tr("Reset (hoàn " + refund + " điểm, EXP cấp hiện tại về 0)", "Reset (refund " + refund + " pt, current level EXP -> 0)"), () ->
            Vars.ui.showConfirm(tr("Reset", "Reset"), tr("Hoàn lại điểm đã nâng và đặt EXP cấp hiện tại về 0. Tiếp tục?", "Refund spent points and set current level EXP to 0. Continue?"), () -> send(REQ_RESET, mode))
        ).width(w).height(btnH()).pad(4).get();
        b.setDisabled(refund <= 0);
        b.row();
    }
    static void statHeader(Table p, St st, float w) {
        resetBtn(p, st, w, "stat");
        p.button(tr("Xem chi tiết chỉ số", "View stat details"), () -> statDetailDialog(st)).width(w).height(btnH()).pad(4).row();
    }
    static void statRow(Table p, St st, int i, float w) {
        int lv = st.s[i] + (st.pi == PILOT_HIST && i != STAT_ARMY ? 1 : 0) + (st.pi == PILOT_XAKYNEO && (i == 5 || i == 6 || i == 8) ? XAKYNEO_CRIT_BONUS_LV : 0);
        Table row = new Table(Styles.black6);
        row.margin(8);
        Table col = new Table();
        col.left();
        col.add("[white]" + tr(STATS[i].vi, STATS[i].en) + "[]   [lightgray]" + lv + "/" + statMaxLabel(i) + "[]").left().wrap().growX().row();
        col.add("[lightgray]" + STATS[i].f.get(lv) + (lv < statMax(i) ? "  >  " + STATS[i].f.get(lv + 1) : "  MAX") + "[]").left().wrap().growX();
        row.add(col).growX().left();
        TextButton btn = row.button("+", () -> send(REQ_UP, String.valueOf(i))).size(narrowScreen() ? 52 : 64, btnH()).padLeft(narrowScreen() ? 4 : 8).get();
        btn.setDisabled(st.pts < 1 || lv >= statMax(i));
        p.add(row).width(w).pad(4).row();
    }
    static String turretDesc(Def def) {
        Block blk = def.block;
        String kind = def.kind;
        String rng = numStr(Math.round(turretRange(def) / Vars.tilesize * 10f) / 10f);
        String rangeTxt = tr(" Tầm: " + rng + " ô.", " Range: " + rng + " tiles.");
        String autoTxt = FORCE_AUTO_AIM.get(def.id) != null ? tr(" [sky]Tự động bắn (không điều khiển được).[]", " [sky]Auto-fire (not player-controlled).[]") : "";
        if (!def.mount) autoTxt += tr(" [gray]Chỉ xây được, không thể lắp lên unit.[]", " [gray]Build only, cannot be mounted on a unit.[]");
        if (kind.equals("tractor")) {
            String dps = numStr(Math.round(fldF(blk, "damage", 0f) * 60 * 10) / 10f), force = numStr(fldF(blk, "force", 0f));
            return tr("Tia kéo: kéo máy bay lại gần, gây " + dps + " sát thương/giây, lực kéo " + force + "." + rangeTxt,
                "Tractor beam: pulls air units closer, deals " + dps + " dmg/s, force " + force + "." + rangeTxt) + autoTxt;
        }
        if (kind.equals("point")) {
            String rl = numStr(fldF(blk, "reload", 0f)), bd = numStr(fldF(blk, "bulletDamage", 0f));
            return tr("Phòng thủ điểm: bắn hạ đạn địch, mỗi " + rl + " tick trừ " + bd + " sát thương đạn." + rangeTxt,
                "Point defense: shoots down enemy bullets, every " + rl + " ticks removes " + bd + " bullet damage." + rangeTxt) + autoTxt;
        }
        if (kind.equals("laser")) {
            String sd = numStr(Math.round(fldF(blk, "shootDuration", 0f) / 6f) / 10f), rl = numStr(Math.round(fldF(blk, "reload", 0f) / 6f) / 10f);
            return tr("Laser liên tục " + sd + "s rồi nạp lại " + rl + "s." + rangeTxt, "Continuous laser for " + sd + "s, then recharges " + rl + "s." + rangeTxt) + autoTxt;
        }
        if (kind.equals("cont")) return tr("Tia bắn liên tục khi có mục tiêu." + rangeTxt, "Fires a continuous beam while a target is in range." + rangeTxt) + autoTxt;
        ArrayList<String> parts = new ArrayList<>();
        if (fldB(blk, "targetGround", true)) parts.add(tr("bộ binh", "ground"));
        if (fldB(blk, "targetAir", true)) parts.add(tr("máy bay", "air"));
        return (parts.size() > 0 ? tr("Bắn: ", "Targets: ") + String.join(", ", parts) + "." : tr("Pháo tiêu chuẩn.", "Standard turret.")) + rangeTxt + autoTxt;
    }
    static int turretDmg(Def def) {
        try {
            if (def.kind.equals("tractor") || def.kind.equals("point")) return 0;
            BulletType bt = equipBullet(def);
            if (bt != null && bt.damage > 0) return Math.round(bt.damage);
        } catch (Throwable err) {}
        return 0;
    }
    static void turretHeader(Table p, St st, float w, int cols) {
        p.add("[lightgray]" + tr("Pháo mua xong vào Túi đồ. Lắp: ", "Bought turrets go to Bag. Mounted: ") + "[white]" + equipCount(st) + "/" + equipSlots(st) + "[]  [lightgray]" + tr("Xây: ", "Built: ") + "[white]" + builtCount(st) + "/" + maxBuilt() + "[]  [lightgray]"
            + (multiTurretOn() ? tr("Mua được nhiều pháo cùng loại.", "Same turret can be bought many times.") : tr("Mỗi loại chỉ mua 1.", "1 per turret type.")) + "[]").colspan(cols).width(w).left().wrap().minWidth(0).pad(2).row();
    }
    static Table turretCard(St st, Def def, float cw) {
        float inner = Math.max(narrowScreen() ? 130 : 150, cw - 20);
        int ownedN = ownedCount(st, def.id);
        Table card = new Table(Styles.black6);
        card.margin(8);
        card.top().left();
        boolean single = !multiTurretOn() && ownedN > 0;
        int price = turretCost(st, def);
        float iconSz = narrowScreen() ? 30 : (Vars.mobile ? 40 : 34);
        Table top = new Table();
        top.left();
        top.add(new Image(def.block.uiIcon)).size(iconSz).padRight(8);
        top.add("[white]" + def.block.localizedName + "[]" + (ownedN > 0 ? "  [accent]x" + ownedN + "[]" : "")).left().growX().minWidth(0).wrap();
        TextButton buyBtn = top.button(tr("Mua", "Buy") + " " + price, () -> send(REQ_BUY, def.id)).size(narrowScreen() ? 72 : 86, btnH()).padLeft(6).get();
        buyBtn.setDisabled(st.pts < price || !def.equip || single);
        card.add(top).width(inner).left().row();
        String rd = roleDesc(def.id);
        card.add(roleText(def.id) + (rd.length() > 0 ? "  [green]" + rd + "[]" : "")).width(inner).left().wrap().minWidth(0).padTop(4).row();
        card.add("[lightgray]" + turretDesc(def) + "[]").width(inner).left().wrap().minWidth(0).padTop(2).row();
        int dmg = turretDmg(def);
        if (dmg > 0) card.add("[scarlet]" + tr("Sát thương: ", "Damage: ") + dmg + "[]").width(inner).left().wrap().minWidth(0).padTop(2).row();
        return card;
    }


    // ===== PHAN 17: chuyen trang co hoat anh, Tui do, Pilot (JS 6499-6930, 7256-7317) =====
    static final int UP_EXIT_MS = 90, UP_ENTER_MS = 170, BAG_PER_PAGE = 5;
    static void slideEl(Element el, int dir, boolean enter, int ms, Runnable done) {
        final long t0 = Time.millis();
        final float dist = Vars.mobile ? 28 : 40;
        final boolean[] fin = {false};
        final Runnable[] step = new Runnable[1];
        step[0] = () -> {
            if (fin[0]) return;
            float k = Math.min(1f, (Time.millis() - t0) / (float) ms);
            float sh = enter ? (1 - (float) Math.pow(1 - k, 3)) : (1 - k * k);
            float off = (enter ? 1 : -1) * (1 - sh) * dir * dist;
            try { el.color.a = sh; } catch (Throwable err) {}
            try { el.translation.set(off, 0); } catch (Throwable err) {}
            if (k >= 1) {
                fin[0] = true;
                if (enter) { try { el.color.a = 1; } catch (Throwable err) {} try { el.translation.set(0, 0); } catch (Throwable err) {} }
                if (done != null) done.run();
            }
        };
        try { el.update(step[0]); } catch (Throwable err) {}
        step[0].run();
    }
    /** JS newPageNav(): chuyen trang co hoat anh truot. go(dir, apply) -> thoat, doi trang, show(el) -> vao. */
    static final class PageNav {
        Element body; Pend2 pending; int enterDir;
        static final class Pend2 { Runnable apply; int dir; }
        void go(int dir, Runnable apply) {
            if (body == null || pending != null) {
                if (pending != null) flush();
                apply.run();
                enterDir = dir;
                return;
            }
            Pend2 p = new Pend2(); p.apply = apply; p.dir = dir; pending = p;
            slideEl(body, dir, false, UP_EXIT_MS, () -> flush());
        }
        void flush() {
            Pend2 pd = pending;
            if (pd == null) return;
            pending = null;
            pd.apply.run();
            enterDir = pd.dir;
        }
        void show(Element el) {
            body = el;
            if (enterDir != 0) { int ed = enterDir; enterDir = 0; slideEl(el, ed, true, UP_ENTER_MS, null); }
        }
        void reset() { body = null; pending = null; enterDir = 0; }
    }
    static PageNav newPageNav() { return new PageNav(); }
    static final PageNav bagNav = newPageNav(), pilotNav = newPageNav(), upNav = newPageNav();

    // ===== Tui do =====
    static BaseDialog bagDialog = null;
    static int bagPage = 0;
    static ArrayList<String> bagRecent = new ArrayList<>(), viewBag = new ArrayList<>();
    static void bagTouch(String id) { bagRecent.remove(id); bagRecent.add(0, id); }
    static final class BagGroup { Def def; final ArrayList<Integer> bag = new ArrayList<>(), on = new ArrayList<>(), built = new ArrayList<>(); int n; }
    static void buildBag(BaseDialog d) {
        Table c = d.cont;
        c.clear();
        St st = localState();
        if (st == null) { c.add(tr("Đang đồng bộ dữ liệu...", "Syncing data...")).pad(20).row(); return; }
        Defs defs = droneDefs();
        float W = Math.min(panelW(), 520f);
        boolean slotFull = equipCount(st) >= equipSlots(st), builtFull = builtCount(st) >= maxBuilt();
        Table head = new Table(Styles.black6);
        head.margin(8);
        head.add("[accent]" + tr("Túi đồ", "Bag") + "[]").left().expandX();
        head.add("[lightgray]" + tr("Đang lắp: ", "Mounted: ") + "[white]" + equipCount(st) + "/" + equipSlots(st) + "[]  [lightgray]" + tr("Xây: ", "Built: ") + "[white]" + builtCount(st) + "/" + maxBuilt() + "[]").right();
        c.add(head).width(W).pad(4).row();

        ArrayList<BagGroup> groups = new ArrayList<>();
        HashMap<String, BagGroup> byType = new HashMap<>();
        for (int i = 0; i < st.d.size(); i++) {
            Def def = defs.map.get(st.d.get(i));
            if (def == null) continue;
            BagGroup g = byType.get(def.id);
            if (g == null) { g = new BagGroup(); g.def = def; g.n = groups.size(); byType.put(def.id, g); groups.add(g); }
            int pp = st.p.get(i);
            if (pp == EQUIPPED) g.on.add(i); else if (pp >= 0) g.built.add(i); else g.bag.add(i);
        }
        groups.sort((x, y) -> {
            int ix = viewBag.indexOf(x.def.id), iy = viewBag.indexOf(y.def.id);
            if (ix < 0) ix = 9999;
            if (iy < 0) iy = 9999;
            return ix != iy ? ix - iy : x.n - y.n;
        });
        if (groups.size() == 0) { c.add("[lightgray]" + tr("Túi đồ trống. Hãy mua pháo ở tab Pháo.", "Your bag is empty. Buy turrets in the Turrets tab.") + "[]").width(W).wrap().pad(20).row(); return; }

        float rowH = btnH() + 22 + (narrowScreen() ? btnH() + 6 : 0);
        float availH = Math.max(60f, listH(btnH() * 2 + 40));
        int perPage = Math.min(BAG_PER_PAGE, Math.max(1, (int) Math.floor(availH / rowH)));
        int totalPages = Math.max(1, (int) Math.ceil(groups.size() / (double) perPage));
        if (bagPage < 0) bagPage = 0;
        if (bagPage > totalPages - 1) bagPage = totalPages - 1;
        int start = bagPage * perPage, end = Math.min(groups.size(), start + perPage);
        float btnW = narrowScreen() ? 50 : (Vars.mobile ? 70 : 62), iconSz = narrowScreen() ? 32 : (Vars.mobile ? 44 : 36);
        Table list = new Table();
        list.top();
        for (int n = start; n < end; n++) {
            BagGroup g = groups.get(n);
            Def def = g.def;
            int total = g.bag.size() + g.on.size() + g.built.size();
            Table row = new Table(Styles.black6);
            row.margin(8);
            row.add(new Image(def.block.uiIcon)).size(iconSz).padRight(10);
            String nm = "[white]" + def.block.localizedName + "[]  [accent]x" + total + "[]"
                + (g.on.size() > 0 ? "\n[lightgray]" + tr("Đang lắp: ", "Mounted: ") + "[white]" + g.on.size() + "[]" : "")
                + (g.built.size() > 0 ? "\n[lightgray]" + tr("Đã xây: ", "Built: ") + "[white]" + g.built.size() + "[]" : "");
            row.add(nm).left().growX().wrap().minWidth(0);
            Table bt = row;
            if (narrowScreen()) { row.row(); bt = new Table(); }
            final int bagIdx = g.bag.size() > 0 ? g.bag.get(0) : -1;
            final int backIdx = g.on.size() > 0 ? g.on.get(g.on.size() - 1) : (g.built.size() > 0 ? g.built.get(g.built.size() - 1) : -1);
            final int sellIdx = bagIdx >= 0 ? bagIdx : backIdx;
            final String did = def.id;
            TextButton ub = bt.button(tr("Tháo", "Unmount"), () -> { bagTouch(did); send(REQ_MODE, backIdx + "|u"); }).size(btnW, btnH()).padLeft(6).get();
            ub.setDisabled(backIdx < 0);
            TextButton mb = bt.button(tr("Lắp", "Mount"), () -> { bagTouch(did); send(REQ_MODE, bagIdx + "|e"); }).size(btnW, btnH()).padLeft(4).get();
            mb.setDisabled(bagIdx < 0 || slotFull || !def.equip || !def.mount);
            TextButton bb = bt.button(tr("Xây", "Build"), () -> { bagTouch(did); startBuild(did); }).size(btnW, btnH()).padLeft(4).get();
            bb.setDisabled(bagIdx < 0 || builtFull);
            bt.button(tr("Bán", "Sell"), () -> { bagTouch(did); send(REQ_SELL, String.valueOf(sellIdx)); }).size(btnW, btnH()).padLeft(4);
            if (narrowScreen()) row.add(bt).colspan(2).growX().padTop(6).row();
            list.add(row).width(W - 24).pad(3).row();
        }
        c.add(list).width(W).top().row();
        bagNav.show(list);
        if (st.w == IN_BAG) {
            Table wr = new Table(Styles.black6);
            wr.margin(8);
            Block wb0 = wallBlock(st.wl);
            if (wb0 != null) wr.add(new Image(wb0.uiIcon)).size(32).padRight(10);
            wr.add("[white]" + wallName(st.wl) + "[]  [accent]+" + wallBonus(st) + tr(" lính", " cap") + "[]").left().growX().wrap().minWidth(0);
            wr.button(tr("Xây", "Build"), () -> startWallBuild()).size(narrowScreen() ? 72 : 90, btnH()).padLeft(6);
            c.add(wr).width(W - 24).pad(3).row();
        }
        if (totalPages > 1) {
            Table nav = new Table();
            float navBtnW = narrowScreen() ? 56 : 72;
            final int tp = totalPages;
            TextButton prevBtn = nav.button("<", () -> { if (bagPage > 0) bagNav.go(-1, () -> { bagPage--; ver++; }); }).size(navBtnW, btnH()).get();
            prevBtn.setDisabled(bagPage <= 0);
            nav.add("[white]" + (bagPage + 1) + "/" + totalPages + "[]").width(80).center();
            TextButton nextBtn = nav.button(">", () -> { if (bagPage < tp - 1) bagNav.go(1, () -> { bagPage++; ver++; }); }).size(navBtnW, btnH()).get();
            nextBtn.setDisabled(bagPage >= totalPages - 1);
            c.add(nav).width(W).pad(4).row();
        }
    }
    static void openBag() {
        if (Vars.headless || !isActive()) return;
        if (bagDialog != null && bagDialog.isShown()) return;
        BaseDialog dialog = new BaseDialog(tr("Túi đồ", "Bag"));
        addClose(dialog);
        bagPage = 0;
        bagNav.reset();
        viewBag = new ArrayList<>(bagRecent);
        final String[] lastVer = {null}; final long[] lastW = {-1};
        dialog.update(() -> {
            if (!nearOwnCore()) { dialog.hide(); return; }
            long w = (long) Core.graphics.getWidth() * 10000 + Core.graphics.getHeight();
            String cv = curVer();
            boolean changed = !cv.equals(lastVer[0]) || lastW[0] != w;
            if (bagNav.pending != null && changed) bagNav.flush();
            if (changed) { lastVer[0] = cv; lastW[0] = w; buildBag(dialog); }
        });
        bagDialog = dialog;
        dialog.show();
    }

    // ===== Pilot =====
    static final HashMap<Integer, TextureRegion> pilotRegCache = new HashMap<>();
    static String atlasFirst(String a, String b) { if (Core.atlas.has(a)) return a; if (Core.atlas.has(b)) return b; return null; }
    static TextureRegion pilotIconRegion(int pi) {
        TextureRegion cached = pilotRegCache.get(pi);
        if (cached != null) return cached;
        TextureRegion reg = null;
        try {
            String n = pi == PILOT_EMPIX ? atlasFirst("newex-empixeand", "empixeand") : pi == PILOT_XAKY ? atlasFirst("newex-xakypilot", "xakypilot")
                : pi == PILOT_HIST ? atlasFirst("newex-histeka", "histeka") : pi == PILOT_XAKYNEO ? atlasFirst("newex-xakyneopilot", "xakyneopilot")
                : pi == PILOT_VANHAU ? atlasFirst("newex-vanhaupilot", "vanhaupilot") : atlasFirst("newex-xikipilot", "xikipilot");
            if (n != null) reg = Core.atlas.find(n);
        } catch (Throwable err) {}
        if (reg != null) pilotRegCache.put(pi, reg);
        return reg;
    }
    static void openPilotZoom(int pi, String name) {
        if (Vars.headless) return;
        final TextureRegion reg = pilotIconRegion(pi);
        if (reg == null) return;
        BaseDialog dlg = new BaseDialog(name);
        final float t0 = Time.time;
        final float[] s = new float[4]; // tx, ty, cx, cy
        final boolean[] down = {false};
        final Element[] view = new Element[1];
        view[0] = new Element() {
            @Override public void draw() {
                try {
                    float w = getWidth(), h = getHeight();
                    float age = Mathf.clamp((Time.time - t0) / 24f), k = Interp.swingOut.apply(age);
                    if (!down[0]) { s[0] = Mathf.lerp(s[0], 0f, 0.07f * Time.delta); s[1] = Mathf.lerp(s[1], 0f, 0.07f * Time.delta); }
                    s[2] = Mathf.lerp(s[2], s[0], 0.22f * Time.delta);
                    s[3] = Mathf.lerp(s[3], s[1], 0.22f * Time.delta);
                    float base = Math.min(w, h) * 0.92f * k, ratio = reg.height > 0 ? (float) reg.width / reg.height : 1f;
                    float rot = Mathf.clamp(-s[2] * 0.06f, -16f, 16f), sq = 1 - Math.min(0.14f, Math.abs(s[3]) * 0.0016f), sh = 1 - Math.min(0.10f, Math.abs(s[2]) * 0.0010f);
                    float px = x + w / 2 + s[2] * 0.25f, py = y + h / 2 + s[3] * 0.25f;
                    Draw.reset();
                    Draw.color(0f, 0f, 0f, 0.45f * Math.min(1f, k));
                    Fill.rect(px + 5, py - 5, base * ratio * sh, base * sq);
                    Draw.color(Color.white);
                    Draw.alpha(Math.min(1f, age * 3));
                    Draw.rect(reg, px, py, base * ratio * sh, base * sq, rot);
                    Draw.reset();
                } catch (Throwable err) { logOnce("pilotzoom", "pilot zoom failed: " + err, false); }
            }
        };
        view[0].touchable = Touchable.enabled;
        view[0].addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                down[0] = true; s[0] = x - view[0].getWidth() / 2; s[1] = y - view[0].getHeight() / 2; return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                s[0] = Mathf.clamp(x - view[0].getWidth() / 2, -view[0].getWidth() / 2, view[0].getWidth() / 2);
                s[1] = Mathf.clamp(y - view[0].getHeight() / 2, -view[0].getHeight() / 2, view[0].getHeight() / 2);
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) { down[0] = false; }
        });
        dlg.cont.add(view[0]).grow();
        dlg.cont.row();
        dlg.cont.add("[lightgray]" + tr("Chạm và lướt để xoay ảnh", "Touch and drag to tilt the image") + "[]").padTop(4);
        dlg.addCloseButton();
        dlg.show();
    }
    static int pilotPage = 0; static long pilotLastW = -1;
    static ArrayList<Integer> pilotRecent = new ArrayList<>(), viewPilot = new ArrayList<>();
    static void pilotTouch(int pi) { pilotRecent.remove(Integer.valueOf(pi)); pilotRecent.add(0, pi); }
    static final class PilotDef { final int pi, minLv; final String name, vi, en; PilotDef(int pi, String name, String vi, String en, int minLv) { this.pi = pi; this.name = name; this.vi = vi; this.en = en; this.minLv = minLv; } }
    static ArrayList<PilotDef> pilotOrdered(ArrayList<PilotDef> list) {
        ArrayList<PilotDef> out = new ArrayList<>();
        for (int v : viewPilot) for (PilotDef d : list) if (d.pi == v) out.add(d);
        for (PilotDef d : list) if (!out.contains(d)) out.add(d);
        return out;
    }
    static ArrayList<PilotDef> pilotList() {
        ArrayList<PilotDef> l = new ArrayList<>();
        l.add(new PilotDef(PILOT_XIKI, "Xiki Pilot", "+5% EXP mỗi lần nhận EXP. Khi lên cấp: +5% EXP cấp mới và +500 EXP, kèm hoạt ảnh icon.",
            "+5% EXP on every EXP gain. On level up: +5% of new level EXP and +500 EXP, with an icon animation.", 1));
        l.add(new PilotDef(PILOT_EMPIX, "Empixeand Pilot", "+5% tấn công và +5% máu. Tụt 50% máu: hồi đầy máu 1 lần; chạm 0 máu: hồi đầy máu thêm 1 lần (hồi chiêu 5 phút sau khi dùng cả 2).",
            "+5% attack and +5% HP. At 50% HP: instantly fully heal once; at 0 HP: fully heal again once (5 min cooldown after both).", EMPIX_MIN_LEVEL));
        l.add(new PilotDef(PILOT_XAKY, "Xaky Pilot", "-20% máu, +20% tốc độ di chuyển. Từ cấp " + XAKY_MIN_LEVEL + ": khi nhận sát thương sẽ Dash theo hướng di chuyển (hồi chiêu 10s). Bộ binh dừng cách tường/công trình 1 ô, không quân bay xuyên.",
            "-20% HP, +20% move speed. From level " + XAKY_MIN_LEVEL + ": taking damage makes you Dash in your move direction (10s cooldown). Ground units stop 1 tile short of walls/buildings; air units fly through.", 1));
        l.add(new PilotDef(PILOT_HIST, "HistekaIMsp Pilot", "Mạnh toàn diện, không điểm yếu! +1 cấp cho mọi thẻ ở trang chỉ số.", "All-rounder, no weakness! +1 level on every stat card in the stats page.", 1));
        l.add(new PilotDef(PILOT_XAKYNEO, "Xaky(Neo) Pilot",
            "+5% tấn công (mỗi phút +0,1%, tối đa +100%). +1 điểm tỉ lệ bạo kích, sát thương bạo kích & hồi máu. +35% sát thương lên mục tiêu trên 50% máu. Kết liễu mục tiêu dưới 16% máu (bản thân mất 9% máu tối đa; áp dụng cả đạn pháo trang bị và pháo xây). Khi lắp pháo therdum, hiệu ứng bất lợi từ kết liễu (tụt máu) đảo ngược thành hồi máu tức thì. Kết liễu còn tạo vụ nổ bán kính, sát thương 200% tấn công + 50% giáp + 50% máu.",
            "+5% attack (ramps +0,1%/min, up to +100%). +1 point to crit chance, crit damage & regeneration. +35% damage to targets above 50% HP. Executes targets below 16% HP (costs 9% of your own max HP; applies to unit, mounted and built turret bullets). While a therdum turret is equipped, the execute drawback (HP loss) is reversed into an instant heal. Execute also causes an explosion with a radius, dealing 200% attack + 50% armor + 50% HP.", 1));
        l.add(new PilotDef(PILOT_VANHAU, "VanHau Pilot",
            "Máu dưới 70%: tự tạo khiên lực (như khối tạo khiên) bao quanh unit, chặn đạn địch, độ bền 120% máu tối đa (hồi chiêu 3 phút), tồn tại đến khi bị phá. Chỉ số hồi máu ưu tiên hồi độ bền khiên khi khiên còn tồn tại.",
            "Below 70% HP: auto-creates a force-field shield (like a force projector) around you that blocks enemy bullets, 120% max HP durability (3 min cooldown), lasting until broken. HP regen stat heals the shield first while it's up.", 1));
        return l;
    }
    static String pilotStatus(St st, PilotDef d) {
        boolean on = st.pi == d.pi, locked = st.lv < d.minLv;
        return on ? "[green]" + tr("Đã chiêu mộ", "Recruited") + "[]" : (locked ? "[scarlet]" + tr("Cần cấp ", "Need Lv ") + d.minLv + "[]" : "[lightgray]" + tr("Miễn phí", "Free") + "[]");
    }
    static String pilotTag(St st, PilotDef d) { return "[accent]" + d.name + "[]  " + pilotStatus(st, d); }
    static void infoSection(Table t, String text) {
        t.add("[accent]" + text + "[]").left().padTop(8).row();
        t.image().color(Color.gray).growX().height(2).padBottom(4).row();
    }
    static void openPilotInfo(int pi) {
        PilotDef d = null;
        for (PilotDef x : pilotList()) if (x.pi == pi) d = x;
        if (d == null) return;
        final PilotDef dd = d;
        BaseDialog dlg = new BaseDialog(tr("Thông tin ", "Info ") + d.name);
        addClose(dlg);
        float W = Math.min(panelW(), 520f) - 40;
        Table body = new Table();
        body.top().left();
        body.defaults().left().pad(3);
        Table head = new Table();
        float isz = narrowScreen() ? 56 : 72;
        TextureRegion reg = pilotIconRegion(pi);
        if (reg != null) head.image(reg).size(isz).padRight(10); else head.add().size(isz).padRight(10);
        Table nm = new Table(); nm.left();
        nm.add("[accent]" + d.name + "[]").left().row();
        nm.label(() -> { St st = localState(); return st == null ? "" : pilotStatus(st, dd); }).left();
        head.add(nm);
        body.add(head).left().padBottom(4).row();
        infoSection(body, tr("Tổng quan", "Overview"));
        body.add("[lightgray]" + tr("Yêu cầu cấp: ", "Required level: ") + "[]" + d.minLv).left().row();
        infoSection(body, tr("Hiệu ứng", "Effects"));
        for (String raw : tr(d.vi, d.en).split("\\.\\s+")) {
            String ln = raw.trim(); if (ln.length() == 0) continue;
            if (ln.charAt(ln.length() - 1) != '.') ln += ".";
            body.add("[lightgray]- []" + ln).left().wrap().width(W).padLeft(10).row();
        }
        if (pi == PILOT_EMPIX || pi == PILOT_XAKY || pi == PILOT_VANHAU) {
            infoSection(body, tr("Hồi chiêu", "Cooldown"));
            body.label(() -> {
                PilotCd pc = pilotCdFor(pi);
                Float at = Vars.player != null ? pc.at.get(Vars.player.id) : null;
                int left = at == null ? 0 : (int) Math.ceil((pc.cd - (Time.time - at)) / 60);
                if (at == null || left <= 0 || Time.time < at) return "[green]" + tr("Sẵn sàng", "Ready") + "[]";
                return "[scarlet]" + (left / 60) + ":" + (left % 60 < 10 ? "0" : "") + (left % 60) + "[]";
            }).left().padLeft(10).row();
        }
        arc.scene.ui.ScrollPane sp = dlg.cont.pane(body).width(W + 24).maxHeight(Math.max(100f, listH())).get();
        sp.setScrollingDisabled(true, false);
        sp.setFadeScrollBars(false);
        dlg.show();
    }
    static void pilotCard(Table p, St st, float W, PilotDef d) {
        final int pi = d.pi; int minLv = d.minLv;
        final boolean on = st.pi == pi, locked = st.lv < minLv;
        Table box = new Table(Styles.black6);
        box.margin(8);
        float isz = narrowScreen() ? 48 : 64;
        TextureRegion reg = pilotIconRegion(pi);
        if (reg != null) {
            Image iconImg = box.image(reg).size(isz).top().left().padRight(8).get();
            iconImg.touchable = Touchable.enabled;
            iconImg.clicked(() -> openPilotZoom(pi, d.name));
        } else box.add().size(isz).padRight(8);
        Table info = new Table();
        info.left();
        info.add(pilotTag(st, d)).left().wrap().growX().minWidth(0).row();
        info.button(tr("Chi tiết thông tin", "Details"), Styles.flatBordert, () -> openPilotInfo(pi)).growX().height(btnH() * 0.8f).padTop(4);
        box.add(info).growX().minWidth(0).top();
        box.row();
        String txt = on ? tr("Cho nghỉ", "Dismiss") : (locked ? tr("Chưa đủ cấp " + minLv, "Need level " + minLv) : tr("Chiêu mộ", "Recruit"));
        TextButton b = box.button(txt, Styles.flatBordert, () -> { if (!on && locked) return; send(REQ_PILOT, on ? "0" : String.valueOf(pi)); }).growX().height(btnH()).colspan(2).padTop(6).get();
        if (!on && locked) b.setDisabled(true);
        p.add(box).width(W).pad(3).row();
    }
    static void buildPilot(BaseDialog dialog) {
        Table c = dialog.cont;
        c.clear();
        final St st = localState();
        if (st == null) return;
        final float W = Math.min(panelW(), 520f) - 24;
        ArrayList<PilotDef> list = pilotOrdered(pilotList());
        float navH = btnH() + 12, availH = Math.max(60f, listH() - navH), cardH = 0;
        for (PilotDef d : list) { float h = measureHeight(p -> pilotCard(p, st, W, d)); if (h > cardH) cardH = h; }
        int perPage = Math.max(1, Math.min(list.size(), (int) Math.floor(availH / Math.max(1f, cardH + 6))));
        int totalPages = Math.max(1, (int) Math.ceil(list.size() / (double) perPage));
        if (pilotPage < 0) pilotPage = 0;
        if (pilotPage > totalPages - 1) pilotPage = totalPages - 1;
        int start = pilotPage * perPage, end = Math.min(list.size(), start + perPage);
        Table body = new Table();
        body.top();
        for (int i = start; i < end; i++) pilotCard(body, st, W, list.get(i));
        arc.scene.ui.ScrollPane sp = c.pane(body).width(W + 24).maxHeight(Math.max(100f, listH() - (totalPages > 1 ? navH : 0))).get();
        sp.setScrollingDisabled(true, false);
        sp.setFadeScrollBars(false);
        sp.setOverscroll(false, false);
        c.row();
        pilotNav.show(sp);
        if (totalPages > 1) {
            Table nav = new Table();
            float nb = narrowScreen() ? 56 : 72;
            final int tp = totalPages;
            TextButton pv = nav.button("<", () -> { if (pilotPage > 0) pilotNav.go(-1, () -> { pilotPage--; ver++; }); }).size(nb, btnH()).get();
            pv.setDisabled(pilotPage <= 0);
            nav.add("[white]" + (pilotPage + 1) + "/" + totalPages + "[]").width(narrowScreen() ? 56 : 80).center();
            TextButton nx = nav.button(">", () -> { if (pilotPage < tp - 1) pilotNav.go(1, () -> { pilotPage++; ver++; }); }).size(nb, btnH()).get();
            nx.setDisabled(pilotPage >= totalPages - 1);
            c.add(nav).width(W + 24).pad(4).row();
        }
    }
    static void openPilot() {
        if (Vars.headless || !isActive()) return;
        BaseDialog dialog = new BaseDialog(tr("Chiêu mộ pilot", "Recruit pilot"));
        pilotPage = 0; pilotLastW = -1;
        pilotNav.reset();
        viewPilot = new ArrayList<>(pilotRecent);
        addClose(dialog);
        final String[] lastVer = {null};
        dialog.update(() -> {
            if (!nearOwnCore()) { dialog.hide(); return; }
            long w0 = (long) Core.graphics.getWidth() * 10000 + Core.graphics.getHeight();
            String cv = curVer();
            boolean changed = !cv.equals(lastVer[0]) || pilotLastW != w0;
            if (pilotNav.pending != null && changed) pilotNav.flush();
            if (changed) { lastVer[0] = cv; pilotLastW = w0; buildPilot(dialog); }
        });
        dialog.show();
    }


    // ===== PHAN 18: tuong/linh, quet unit, danh sach unit, chi tiet chi so (JS 6930-7253) =====
    static String armyDesc(int i) {
        Barrack cfg = BARRACKS[i];
        UnitType ut = Vars.content.getByName(ContentType.unit, cfg.t);
        String name = ut != null ? ut.localizedName : cfg.t;
        return name + "  x" + cfg.n + " / " + cfg.sec + "s  " + tr("tối đa ", "max ") + cfg.cap;
    }
    static String wallName(int i) { Block b = wallBlock(i); return b != null ? b.localizedName : WALL_CHAIN[i]; }
    static void wallCard(Table p, St st, float w) {
        int ws = st.w, lv = st.wl, mx = WALL_CHAIN.length - 1;
        Table box = new Table(Styles.black6);
        box.margin(10);
        box.add("[accent]" + tr("Khối tường: tăng giới hạn lính", "Wall block: raises soldier cap") + "[]").left().row();
        box.add("[lightgray]" + tr("Mỗi cấp +1 giới hạn lính sinh từ lõi.", "Each level adds +1 to the soldier cap.") + "[]").left().wrap().growX().padBottom(6).row();
        if (ws == WALL_NONE) {
            box.add("[white]" + tr("Chưa mua. Mua ", "Not bought. Buy ") + wallName(0) + " (+1)[]").left().wrap().growX().row();
            TextButton bb = box.button(tr("Mua", "Buy") + " (" + WALL_COST[0] + tr(" điểm)", " pt)"), () -> send(REQ_WALL, "buy")).growX().height(btnH()).padTop(8).get();
            bb.setDisabled(st.pts < WALL_COST[0]);
        } else {
            box.add("[white]" + wallName(lv) + "  " + tr("Cấp ", "Level ") + lv + "/" + mx + "  [accent]+" + (1 + lv) + tr(" lính", " cap") + "[]").left().wrap().growX().row();
            if (ws == IN_BAG) {
                box.add("[lightgray]" + tr("Đang trong Túi đồ.", "In your Bag.") + "[]").left().row();
                box.button(tr("Xây", "Build"), () -> startWallBuild()).growX().height(btnH()).padTop(6).row();
            } else if (lv < mx) {
                int cost = WALL_COST[lv + 1];
                box.add("[lightgray]" + tr("Tiếp theo: ", "Next: ") + wallName(lv + 1) + "[]").left().row();
                TextButton ub = box.button(tr("Nâng cấp", "Upgrade") + " (" + cost + tr(" điểm)", " pt)"), () -> send(REQ_WALL, "up")).growX().height(btnH()).padTop(6).get();
                ub.setDisabled(st.pts < cost);
            } else box.add("[accent]MAX[]").left().padTop(4).row();
        }
        p.add(box).width(w).pad(4).row();
    }
    static void armyHeader(Table p, St st, float w) {
        int lv = st.b;
        Table box = new Table(Styles.black6);
        box.margin(10);
        box.add("[accent]" + tr("Nhà máy lính tự động", "Auto barracks") + "[]").left().row();
        box.add("[lightgray]" + tr("Sinh lính tại lõi, tự đi đánh lõi phe khác.", "Spawns soldiers at your core that march on enemy cores.") + "[]").left().wrap().growX().padBottom(6).row();
        if (lv < 1) box.add("[white]" + tr("Chưa mua.", "Not purchased.") + "[]").left().row();
        else box.add("[white]" + tr("Cấp ", "Level ") + lv + "/" + BARRACKS.length + "[]  [accent]" + armyDesc(lv - 1) + "[]").left().wrap().growX().row();
        if (lv >= 1) {
            boolean co = st.cmd == 1;
            box.button(tr("Ra lệnh cho lính: ", "Soldier commands: ") + (co ? tr("[green]BẬT[]", "[green]ON[]") : tr("[scarlet]TẮT[]", "[scarlet]OFF[]")), () -> send(REQ_CMD, "t")).growX().height(btnH()).padTop(6).row();
            if (co) {
                box.add("[lightgray]" + tr("Lính đi theo bạn, thấy địch trong tầm thì bắn. Shift+9: tập kết tại con trỏ. Shift+0: theo bạn.", "Soldiers follow you and shoot enemies in range. Shift+9: rally at cursor. Shift+0: follow you.") + "[]").left().wrap().growX().padTop(4).row();
                box.button(tr("Tập kết tại vị trí tôi", "Rally at my position"), () -> send(REQ_CMD, "h")).growX().height(btnH()).padTop(4).row();
                box.button(tr("Lính theo tôi", "Soldiers follow me"), () -> send(REQ_CMD, "f")).growX().height(btnH()).padTop(4).row();
            }
        }
        if (lv < BARRACKS.length) {
            int cost = barrackCost(lv);
            box.add("[lightgray]" + tr("Tiếp theo: ", "Next: ") + armyDesc(lv) + "[]").left().wrap().growX().padTop(4).row();
            TextButton b = box.button((lv < 1 ? tr("Mua", "Buy") : tr("Nâng cấp", "Upgrade")) + " (" + cost + tr(" điểm)", " pt)"), () -> send(REQ_BARR, "1")).growX().height(btnH()).padTop(8).get();
            b.setDisabled(st.pts < cost);
        } else box.add("[accent]MAX[]").left().padTop(4).row();
        p.add(box).width(w).pad(4).row();
        wallCard(p, st, w);
        statRow(p, st, STAT_ARMY, w);
        resetBtn(p, st, w, "army");
    }
    static void armyList(Table p, St st, float w, int start, int end) {
        int lv = st.b;
        Table list = new Table(Styles.black6);
        list.margin(10);
        for (int i = start; i < end; i++) {
            String c = (i + 1 == lv) ? "[accent]" : "[lightgray]";
            list.add(c + (i + 1) + ". " + armyDesc(i) + "  (" + barrackCost(i) + tr(" điểm)", " pt)") + "[]").left().wrap().growX().row();
        }
        p.add(list).width(w).pad(4).row();
    }

    static ArrayList<String[]> scanLines(St st) { Unit u = null; try { u = Vars.player.unit(); } catch (Throwable err) {} return scanLines(st, u, true); }
    static ArrayList<String[]> scanLines(St st, Unit u) { return scanLines(st, u, false); }
    static ArrayList<String[]> scanLines(St st, Unit u, boolean local) {
        if (u == null || !u.isValid()) return null;
        ArrayList<String[]> out = new ArrayList<>();
        UnitType ut = u.type;
        int[] s = st.s;
        out.add(new String[]{tr("Unit đang điều khiển", "Controlled unit"), "[accent]" + ut.localizedName + "[]  " + tr("(cấp ", "(tier ") + activeTier(st) + ")"});
        out.add(new String[]{tr("Máu", "Health"), Math.round(u.health) + " / " + Math.round(u.maxHealth) + "  [lightgray]" + tr("(gốc ", "(base ") + Math.round(ut.health) + ")[]"});
        if (st.pi == PILOT_VANHAU) {
            Player vp = null;
            try { vp = ownerPl(u); } catch (Throwable err) {}
            int vid = vp != null ? vp.id : (local ? Vars.player.id : -1);
            VanhauField vf = vanhauField.get(vid);
            String vTxt;
            if (vf != null && vf.hp > 0) vTxt = "[cyan]" + Math.round(vf.hp) + " / " + Math.round(vf.max) + "[]  [lightgray]" + Math.round(vf.hp / Math.max(1f, vf.max) * 100) + "%[]";
            else {
                Float at = vanhauCdAt.get(vid);
                int left = at == null ? 0 : (int) Math.ceil((VANHAU_CD - (Time.time - at)) / 60);
                vTxt = left > 0 ? "[lightgray]" + tr("Không có khiên (hồi chiêu ", "No shield (cooldown ") + left + "s)[]" : "[lightgray]" + tr("Chưa kích hoạt (tự bật khi máu dưới 70%)", "Inactive (auto-activates below 70% HP)") + "[]";
            }
            out.add(new String[]{tr("Khiên VanHau (độ bền)", "VanHau shield (durability)"), vTxt});
        }
        float hm = u.healthMultiplier > 0 ? u.healthMultiplier : 1f;
        out.add(new String[]{tr("Giáp", "Armor"), numStr(u.armor) + "  [lightgray]" + tr("(gốc ", "(base ") + numStr(ut.armor) + ")[]  " + tr("giảm sát thương nhận ", "damage taken reduced ") + numStr((1 - 1 / hm) * 100) + "%"});
        out.add(new String[]{tr("Tấn công", "Attack"), numStr((baseAttack(ut) + s[2] * ATK_FLAT) * u.damageMultiplier) + tr(" sát thương/phát", " dmg/hit") + "  [lightgray]" + tr("(gốc ", "(base ") + numStr(baseAttack(ut)) + ")  x" + numStr(u.damageMultiplier) + "[]"});
        out.add(new String[]{tr("Tốc độ di chuyển", "Move speed"), numStr(ut.speed * u.speedMultiplier) + "  [lightgray]" + tr("(gốc ", "(base ") + numStr(ut.speed) + ")  x" + numStr(u.speedMultiplier) + "[]"});
        out.add(new String[]{tr("Tốc độ bắn", "Fire rate"), "x" + numStr(u.reloadMultiplier)});
        out.add(new String[]{tr("Tỉ lệ bạo kích", "Crit chance"), (s[5] * 2) + "%"});
        out.add(new String[]{tr("Sát thương bạo kích", "Crit damage"), (150 + s[6] * 12) + "%"});
        out.add(new String[]{tr("Xuyên giáp", "Armor pierce"), (s[7] * 5) + "%"});
        out.add(new String[]{tr("Hồi máu", "Regeneration"), numStr(u.maxHealth * s[8] * REGEN_PCT / 100) + tr(" máu/giây (khi dưới 99%)", " HP/s (below 99%)")});
        out.add(new String[]{tr("Sát thương % máu tối đa", "Max HP % damage"), "+" + numStr(s[9] * DMG_PCT_MAXHP) + tr("% máu tối đa mục tiêu/lần trúng", "% target max HP per hit")});
        out.add(new String[]{tr("Sát thương theo giáp", "Armor-based damage"), "+" + numStr(s[10] * DMG_PER_ARMOR * u.armor * 100) + "%"});
        out.add(new String[]{tr("Đẩy lùi", "Knockback"), "+" + numStr(s[11] * KNOCK_PER)});
        out.add(new String[]{tr("Pháo gắn", "Mounted turrets"), "unit " + equipCount(st) + "/" + equipSlots(st) + tr(", xây ", ", built ") + builtCount(st) + "/" + maxBuilt()});
        return out;
    }

    static final String KEY_ULIST = "newex-pvp-unitlist";
    static Table ulistPanel = null; static int ulistW = 0, ulistH = 0;
    static void ulistClamp() {
        Table t = ulistPanel;
        if (t == null) return;
        t.setPosition(Mathf.clamp(t.x, 0f, Math.max(0f, screenW() - t.getWidth())), Mathf.clamp(t.y, 0f, Math.max(0f, screenH() - t.getHeight())));
        try { Core.settings.put(KEY_ULIST + "-x", t.x); Core.settings.put(KEY_ULIST + "-y", t.y); } catch (Throwable err) {}
    }
    static boolean ulistOn() { return setBool(KEY_ULIST, true); }

    static void addScanLines(Table p, ArrayList<String[]> lines, float pad) {
        for (String[] ln : lines) {
            p.add("[lightgray]" + ln[0] + "[]").left().wrap().growX().row();
            p.add("[white]" + ln[1] + "[]").left().wrap().growX().padBottom(pad).row();
        }
    }
    static void openUnitDetail(int pid) {
        BaseDialog d = new BaseDialog(tr("Chi tiết unit", "Unit details"));
        addClose(d);
        float W = Math.min(panelW(), 520f);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            d.cont.clear();
            d.cont.button(tr("Quét lại", "Rescan"), () -> fill[0].run()).width(W).height(btnH()).pad(4).row();
            d.cont.pane((Cons<Table>) p -> {
                p.margin(10);
                p.top();
                St st = byId.get(pid);
                if (st == null) { p.add("[scarlet]" + tr("Người chơi đã rời đi.", "Player left.") + "[]").row(); return; }
                p.add("[accent]" + plName(pid) + "[]  [white]" + tr("Cấp ", "Lv ") + st.lv + "[]").left().wrap().growX().row();
                p.add(kcText(st)).left().wrap().growX().padBottom(6).row();
                Unit u = plUnit(pid);
                if (u != null && !(u.isValid() && !u.dead)) u = null;
                ArrayList<String[]> lines = u != null ? scanLines(st, u) : null;
                if (lines == null) p.add("[lightgray]" + tr("Unit: ", "Unit: ") + "[white]" + tierType(activeTier(st)).localizedName + "[]  [scarlet]" + tr("(không quét được: unit đang chết/chưa hồi sinh)", "(cannot scan: unit dead/respawning)") + "[]").left().wrap().growX().padBottom(6).row();
                else addScanLines(p, lines, 4f);
                p.add("[accent]" + tr("Chỉ số đã nâng", "Upgraded stats") + "[]").left().padTop(6).row();
                boolean any = false;
                for (int i = 0; i < STATS.length; i++) {
                    if (!(st.s[i] > 0)) continue;
                    any = true;
                    p.add("[lightgray]" + tr(STATS[i].vi, STATS[i].en) + "[]  [white]" + st.s[i] + "/" + statMaxLabel(i) + "[]").left().wrap().growX().row();
                }
                if (!any) p.add("[lightgray]-[]").left().row();
                p.add("[accent]" + tr("Pháo lắp trên unit ", "Turrets mounted ") + equipCount(st) + "/" + equipSlots(st) + "[]").left().padTop(8).row();
                Defs defs = droneDefs();
                if (st.p.size() == st.d.size()) {
                    for (int i = 0; i < st.d.size(); i++) {
                        if (st.p.get(i) != EQUIPPED) continue;
                        Def def = defs.map.get(st.d.get(i));
                        if (def == null) continue;
                        Table row = new Table();
                        row.add(new Image(def.block.uiIcon)).size(28).padRight(6);
                        row.add("[white]" + def.block.localizedName + "[]  " + roleText(def.id)).left();
                        p.add(row).left().padTop(2).row();
                    }
                }
                p.add("[lightgray]" + tr("Pháo xây: ", "Built: ") + "[white]" + builtCount(st) + "/" + maxBuilt() + "[]  " + tr("Nhà máy lính: ", "Barracks: ") + "[white]" + st.b + "[]").left().padTop(6).row();
            }).width(W).maxHeight(listH(btnH() + 12)).get().setScrollingDisabled(true, false);
        };
        fill[0].run();
        d.show();
    }
    static ArrayList<Integer> orderedIds() {
        ArrayList<Integer> ids = new ArrayList<>(byId.keySet());
        ids.sort((a, b) -> { boolean na = a < 0, nb = b < 0; if (na != nb) return na ? 1 : -1; return na ? b - a : a - b; });
        return ids;
    }
    static void openUnitList() {
        if (Vars.headless || !isActive()) return;
        BaseDialog d = new BaseDialog(tr("Unit người chơi", "Player units"));
        addClose(d);
        float W = Math.min(panelW(), 520f);
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            d.cont.clear();
            d.cont.button(tr("Quét lại", "Rescan"), () -> fill[0].run()).width(W).height(btnH()).pad(4).row();
            d.cont.pane((Cons<Table>) p -> {
                p.top();
                int n = 0;
                for (int pid : orderedIds()) {
                    St st = byId.get(pid);
                    if (st == null) continue;
                    n++;
                    Table row = new Table(Styles.black6);
                    row.margin(8);
                    row.touchable = Touchable.enabled;
                    row.add(new Image(tierType(activeTier(st)).uiIcon)).size(narrowScreen() ? 32 : 40).padRight(10);
                    Table col = new Table();
                    col.left();
                    col.add("[white]" + plName(pid) + "[]  [accent]" + tr("Cấp ", "Lv ") + st.lv + "[]").left().wrap().growX().row();
                    col.add(kcText(st)).left().wrap().growX();
                    row.add(col).growX().left().minWidth(0);
                    row.clicked(() -> openUnitDetail(pid));
                    p.add(row).width(W - 24).pad(4).row();
                }
                if (n == 0) p.add("[lightgray]" + tr("Chưa có dữ liệu.", "No data yet.") + "[]").pad(10).row();
            }).width(W).maxHeight(listH(btnH() + 12)).get().setScrollingDisabled(true, false);
        };
        fill[0].run();
        d.show();
    }
    static void statDetailDialog(St st0) {
        BaseDialog dialog = new BaseDialog(tr("Chi tiết chỉ số", "Stat details"));
        addClose(dialog);
        float W2 = Math.min(480f, panelW());
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            dialog.cont.clear();
            St cur = localState();
            ArrayList<String[]> lines = cur != null ? scanLines(cur) : null;
            dialog.cont.button(tr("Quét lại unit", "Rescan unit"), () -> fill[0].run()).width(W2).height(btnH()).pad(4).row();
            dialog.cont.pane((Cons<Table>) p -> {
                p.margin(10);
                p.top();
                if (lines == null) { p.add("[scarlet]" + tr("Không quét được: bạn chưa điều khiển unit nào.", "Scan failed: you are not controlling a unit.") + "[]").left().wrap().growX().row(); return; }
                addScanLines(p, lines, 8f);
            }).width(W2).maxHeight(listH(btnH() + 12)).get().setScrollingDisabled(true, false);
        };
        fill[0].run();
        dialog.show();
    }
    /** Nut "Unit" keo tha tren HUD + muc cai dat (JS 7180-7225). */
    public static void initUnitListUi() {
        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> {
            try {
                Core.bundle.getProperties().put("setting." + KEY_ULIST + ".name", tr("Hiện nút danh sách unit player (PvP)", "Show PvP player unit list button"));
                Vars.ui.settings.game.checkPref(KEY_ULIST, true);
            } catch (Throwable err) { Log.err("[NexPvp] cannot add unit list setting: " + err); }
            try {
                final Table t = new Table(Styles.black6);
                ulistPanel = t;
                t.margin(6);
                t.name = "newex-pvp-unitlist";
                t.touchable = Touchable.enabled;
                t.image(Icon.players).size(Vars.mobile ? 26 : 22).padRight(6);
                t.add(tr("Unit", "Units")).get().setFontScale(0.9f);
                t.visibility = () -> isActive() && Vars.ui.hudfrag.shown && ulistOn();
                t.pack();
                if (Core.settings.has(KEY_ULIST + "-x") && Core.settings.has(KEY_ULIST + "-y")) t.setPosition(Core.settings.getFloat(KEY_ULIST + "-x", 0f), Core.settings.getFloat(KEY_ULIST + "-y", 0f));
                else t.setPosition(4, (screenH() - t.getHeight()) / 2);
                ulistClamp();
                final boolean[] dragging = {false}; final float[] off = new float[2];
                t.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) { dragging[0] = false; off[0] = x; off[1] = y; return true; }
                    @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                        if (Math.abs(x - off[0]) > 5 || Math.abs(y - off[1]) > 5) dragging[0] = true;
                        if (dragging[0]) { t.setPosition(t.x + x - off[0], t.y + y - off[1]); ulistClamp(); }
                    }
                    @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) { if (!dragging[0]) openUnitList(); dragging[0] = false; }
                });
                Vars.ui.hudGroup.addChild(t);
                ulistW = Core.graphics.getWidth(); ulistH = Core.graphics.getHeight();
                Events.run(Trigger.update, () -> {
                    int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
                    if (w != ulistW || h != ulistH) { ulistW = w; ulistH = h; ulistClamp(); }
                });
            } catch (Throwable err) { Log.err("[NexPvp] cannot add unit list button: " + err); }
        });
    }


    // ===== PHAN 19: bang nang cap chinh, popup EXP (JS 7320-7690) =====
    static BaseDialog upDialog = null;
    static int upTab = 0, upPage = 0;
    static String turCardKey = ""; static float turCardH = 0;

    static void buildDialog(BaseDialog d) {
        Table c = d.cont;
        c.clear();
        final St st = localState();
        if (st == null) { c.add(tr("Đang đồng bộ dữ liệu...", "Syncing data...")).pad(20).row(); return; }
        final float W = panelW();
        Defs defs = droneDefs();
        if (Vars.state.isCampaign()) {
            Defs fd = new Defs();
            for (Def df : defs.list) { try { if (df.block.unlockedNow()) fd.list.add(df); } catch (Throwable err) {} }
            fd.map.putAll(defs.map);
            defs = fd;
        }
        final Defs fdefs = defs;
        Table head = new Table(Styles.black6);
        head.margin(shortScreen() ? 4 : 8);
        float bagBtnH = shortScreen() ? 32 : (Vars.mobile ? 40 : 32);
        String bagLabel = tr("Túi đồ", "Bag") + " (" + st.d.size() + ")", pilotLabel = "Pilot";
        if (narrowScreen()) {
            head.add("[accent]" + tr("Cấp ", "Level ") + st.lv + "/" + maxLevel() + "[]").left().minWidth(0);
            head.add("[white]" + tierType(activeTier(st)).localizedName + "[]").expandX().right().minWidth(0);
            head.row();
            head.add("[yellow]" + tr("Điểm: ", "Points: ") + st.pts + "[]").left().minWidth(0).padTop(2);
            Table nb = new Table();
            nb.button(pilotLabel, () -> openPilot()).height(bagBtnH).minWidth(0);
            nb.button(bagLabel, () -> openBag()).height(bagBtnH).minWidth(0).padLeft(4);
            head.add(nb).right().padTop(2);
            head.row();
        } else {
            head.add("[accent]" + tr("Cấp ", "Level ") + st.lv + "/" + maxLevel() + "[]").left();
            head.add("[white]" + tierType(activeTier(st)).localizedName + "[]").expandX().center();
            Table ptsBox = new Table();
            ptsBox.add("[yellow]" + tr("Điểm: ", "Points: ") + st.pts + "[]").right();
            ptsBox.button(pilotLabel, () -> openPilot()).height(bagBtnH).padLeft(8);
            ptsBox.button(bagLabel, () -> openBag()).height(bagBtnH).padLeft(4);
            head.add(ptsBox).right();
            head.row();
        }
        head.add(new Bar(() -> "EXP " + expText(), () -> Pal.accent, () -> expFrac())).growX().height(shortScreen() ? 16 : (Vars.mobile ? 26 : 20)).colspan(narrowScreen() ? 2 : 3).padTop(shortScreen() ? 2 : 6);
        c.add(head).width(W).pad(4).row();

        if (upTab < 0 || upTab > 3) upTab = 0;
        String[] names = {tr("Unit", "Unit"), tr("Chỉ số", "Stats"), tr("Pháo", "Turrets"), tr("Lính", "Army")};
        arc.scene.ui.TextButton.TextButtonStyle tabStyle = Styles.togglet != null ? Styles.togglet : Styles.defaultt;
        Table tabs = new Table();
        final ArrayList<TextButton> tabBtns = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final int ix = i;
            TextButton tb = tabs.button(names[i], tabStyle, () -> {
                if (ix == upTab) { upPage = 0; ver++; return; }
                int dir = ix > upTab ? 1 : -1;
                for (int j = 0; j < tabBtns.size(); j++) tabBtns.get(j).setChecked(j == ix);
                upNav.go(dir, () -> { upTab = ix; upPage = 0; ver++; });
            }).growX().uniformX().minWidth(0).height(narrowScreen() ? Math.min(btnH(), 44f) : btnH()).pad(2).get();
            try { if (narrowScreen()) tb.getLabel().setFontScale(0.85f); } catch (Throwable err) {}
            tb.setChecked(upTab == i);
            tabBtns.add(tb);
        }
        c.add(tabs).width(W).row();

        final int tab = upTab, cols = gridCols(), cw = cardW();
        final float innerW = W - 24, navH = btnH() + 12, availH = Math.max(60f, listH() - navH);
        ArrayList<Object> unitBase = new ArrayList<>();
        for (int k = 1; k <= 7; k++) unitBase.add(k);
        final ArrayList<Object> unitList = (tab == 0 && st.lv >= FREE_SWITCH_LEVEL) ? orderedBy(0, unitBase) : null;
        ArrayList<Object> statBase = new ArrayList<>();
        for (int q = 0; q < STATS.length; q++) if (q != STAT_ARMY) statBase.add(q);
        final ArrayList<Object> statOrder = orderedBy(1, statBase);
        ArrayList<Object> turBase = new ArrayList<>();
        for (Def df : fdefs.list) turBase.add(df.id);
        final ArrayList<Object> turOrder = orderedBy(2, turBase);
        int itemCount = 1, perPage = 1;
        if (tab == 0) {
            if (unitList != null) {
                itemCount = unitList.size();
                float rowH = measureHeight(p -> unitRow(p, st, (Integer) unitList.get(0), activeTier(st), innerW));
                perPage = Math.min(UNIT_PER_PAGE, Math.max(1, (int) Math.floor(availH / Math.max(1f, rowH))));
            }
        } else if (tab == 1) {
            itemCount = statOrder.size();
            float hH = measureHeight(p -> statHeader(p, st, innerW)), rowH = 0;
            for (Object o : statOrder) { final int idx = (Integer) o; float h = measureHeight(p -> statRow(p, st, idx, innerW)); if (h > rowH) rowH = h; }
            perPage = Math.min(STAT_PER_PAGE, Math.max(1, (int) Math.floor(Math.max(0f, availH - hH) / Math.max(1f, rowH))));
        } else if (tab == 2) {
            itemCount = fdefs.list.size();
            final float cwM = Math.max(cw, cardMinW());
            float hH = measureHeight(p -> turretHeader(p, st, innerW, cols)), cardH = 0;
            String ckey = cwM + "|" + cols + "|" + (Vars.mobile ? 1 : 0) + "|" + tr("v", "e") + "|" + fdefs.list.size();
            if (turCardKey.equals(ckey)) cardH = turCardH;
            else {
                for (Def df : fdefs.list) { Table card = turretCard(st, df, cwM); card.pack(); if (card.getPrefHeight() > cardH) cardH = card.getPrefHeight(); }
                turCardKey = ckey; turCardH = cardH;
            }
            int rows = Math.max(1, (int) Math.floor(Math.max(0f, availH - hH) / Math.max(1f, cardH + 8)));
            perPage = Math.min(TURRET_PER_PAGE, rows) * cols;
        } else {
            itemCount = BARRACKS.length;
            float hH = measureHeight(p -> armyHeader(p, st, innerW)), rowH = measureHeight(p -> armyList(p, st, innerW, 0, 1));
            perPage = Math.min(ARMY_PER_PAGE, Math.max(1, (int) Math.floor(Math.max(0f, availH - hH) / Math.max(1f, rowH))));
        }
        final int totalPages = Math.max(1, (int) Math.ceil(itemCount / (double) perPage));
        if (upPage < 0) upPage = 0;
        if (upPage > totalPages - 1) upPage = totalPages - 1;
        int start = upPage * perPage, end = Math.min(itemCount, start + perPage);

        Table body = new Table();
        body.margin(4);
        body.top();
        if (tab == 0) {
            if (st.lv < FREE_SWITCH_LEVEL) body.add("[lightgray]" + tr("Đạt cấp " + FREE_SWITCH_LEVEL + " để được đổi unit tùy ý.", "Reach level " + FREE_SWITCH_LEVEL + " to switch units freely.") + "[]").pad(10).row();
            else { int cur = activeTier(st); for (int idx = start; idx < end; idx++) unitRow(body, st, (Integer) unitList.get(idx), cur, innerW); }
        } else if (tab == 1) {
            if (start == 0) statHeader(body, st, innerW);
            for (int i = start; i < end; i++) statRow(body, st, (Integer) statOrder.get(i), innerW);
        } else if (tab == 2) {
            float cwE = Math.max(cw, cardMinW()), bodyW = (cwE + 8) * cols;
            if (start == 0) turretHeader(body, st, Math.max(innerW, bodyW - 8), cols);
            int colCount = 0;
            for (int j = start; j < end; j++) {
                body.add(turretCard(st, fdefs.map.get((String) turOrder.get(j)), cwE)).width(cwE).pad(4).top().left();
                colCount++;
                if (colCount % cols == 0) body.row();
            }
        } else {
            if (start == 0) armyHeader(body, st, innerW);
            armyList(body, st, innerW, start, end);
        }
        Table nav = null;
        if (totalPages > 1) {
            nav = new Table();
            float navBtnW = narrowScreen() ? 56 : 72;
            TextButton prevBtn = nav.button("<", () -> { if (upPage > 0) upNav.go(-1, () -> { if (upPage > 0) upPage--; ver++; }); }).size(navBtnW, btnH()).get();
            prevBtn.setDisabled(upPage <= 0);
            nav.add("[white]" + (upPage + 1) + "/" + totalPages + "[]").width(narrowScreen() ? 56 : 80).center();
            TextButton nextBtn = nav.button(">", () -> { if (upPage < totalPages - 1) upNav.go(1, () -> { if (upPage < totalPages - 1) upPage++; ver++; }); }).size(navBtnW, btnH()).get();
            nextBtn.setDisabled(upPage >= totalPages - 1);
        }
        if (nav != null && Vars.mobile) c.add(nav).width(W).pad(4).row();
        boolean hs;
        if (tab == 2) { float cwE2 = Math.max(cw, cardMinW()); hs = (cwE2 + 8) * cols > W - 8; }
        else { float bodyPrefW = 0; try { bodyPrefW = body.getPrefWidth(); } catch (Throwable err) {} hs = bodyPrefW > W + 1; }
        arc.scene.ui.ScrollPane sp = c.pane(body).width(W).maxHeight(Math.max(100f, listH() - (nav != null ? navH : 0))).get();
        sp.setScrollingDisabled(!hs, false);
        sp.setFadeScrollBars(false);
        sp.setOverscroll(false, false);
        sp.setSmoothScrolling(true);
        c.row();
        upNav.show(sp);
        if (nav != null && !Vars.mobile) c.add(nav).width(W).pad(4).row();
    }
    @SuppressWarnings("unchecked")
    static void openUpgrade() {
        if (Vars.headless || !isActive()) return;
        if (upDialog != null && upDialog.isShown()) return;
        if (Core.scene.hasDialog()) return;
        if (!nearOwnCore()) { Vars.ui.showInfoToast(tr("[scarlet]Hãy về gần lõi và nhấn vào lõi để nâng cấp!", "[scarlet]Go near your core and tap it to upgrade!"), 2.5f); return; }
        BaseDialog dialog = new BaseDialog(tr("Nâng cấp PvP", "PvP Upgrades"));
        addClose(dialog);
        final String[] lastVer = {null}; final int[] lastTab = {-1}; final long[] lastW = {-1};
        upTab = lastUpTab;
        upPage = 0;
        upNav.reset();
        viewUp = new ArrayList[]{new ArrayList<Object>(recentUp[0]), new ArrayList<Object>(recentUp[1]), new ArrayList<Object>(recentUp[2]), new ArrayList<Object>(recentUp[3])};
        dialog.update(() -> {
            if (!nearOwnCore()) { dialog.hide(); return; }
            long w = (long) Core.graphics.getWidth() * 10000 + Core.graphics.getHeight();
            String cv = curVer();
            boolean verChanged = !cv.equals(lastVer[0]) || lastW[0] != w;
            if (upNav.pending != null && verChanged) upNav.flush();
            if (verChanged || lastTab[0] != upTab) { lastVer[0] = cv; lastTab[0] = upTab; lastW[0] = w; buildDialog(dialog); }
        });
        upDialog = dialog;
        dialog.show();
    }

    // ===== popup EXP =====
    static final float EXP_JUMP_SEC = 0.8f, EXP_FADE_SEC = 1.2f, EXP_MERGE_SEC = 0.25f;
    static final class ExpPop { Label el; int n; long t0; float lastSc = Float.NaN; }
    static ArrayList<ExpPop> expPops = new ArrayList<>();
    static float[] expLastPos = null; // x, y, hitSize
    static void notifyExp(Player pl, float amount) {
        if (isBotPl(pl)) return;
        int n = Math.round(amount);
        if (n < 1) return;
        try {
            if (!Vars.headless && Vars.player != null && pl.id == Vars.player.id) { showExpPopup(n); return; }
        } catch (Throwable err) { logOnce("exppopup", "exp popup failed: " + err, false); return; }
        if (!Vars.net.server()) return;
        String msg = "{\"p\":" + pl.id + ",\"x\":" + n + "}";
        try { Call.clientPacketReliable(pl.con, EXP_KIND, msg); return; } catch (Throwable err) {}
        try { Call.clientPacketReliable(EXP_KIND, msg); } catch (Throwable err) {}
    }
    static void onExpPacket(String content) {
        try {
            JsonValue o = new JsonReader().parse(content);
            if (Vars.player != null && o.getInt("p", Integer.MIN_VALUE) == Vars.player.id) showExpPopup(o.getInt("x", 0));
        } catch (Throwable err) { logOnce("exppacket", "exp packet failed: " + err, false); }
    }
    static Label makeExpLabel(String text) {
        Label el;
        try { el = new Label(text, Styles.outlineLabel); } catch (Throwable err) { el = new Label(text); }
        el.touchable = Touchable.disabled;
        el.color.set(Pal.accent);
        return el;
    }
    static void showExpPopup(int n) {
        if (Vars.headless || Vars.ui == null || Vars.ui.hudGroup == null) return;
        long now = Time.millis();
        ExpPop last = expPops.size() > 0 ? expPops.get(expPops.size() - 1) : null;
        if (last != null && now - last.t0 < EXP_MERGE_SEC * 1000) { last.n += n; last.el.setText("+" + last.n + " EXP"); return; }
        ExpPop p = new ExpPop(); p.el = makeExpLabel("+" + n + " EXP"); p.n = n; p.t0 = now;
        Vars.ui.hudGroup.addChild(p.el);
        try { if (pilotOverlay != null) pilotOverlay.toFront(); } catch (Throwable err) {}
        expPops.add(p);
        updateExpPops();
    }
    static void clearExpPops() {
        for (ExpPop p : expPops) { try { p.el.remove(); } catch (Throwable err) {} }
        expPops = new ArrayList<>();
        expLastPos = null;
    }
    static Vec2 expStagePos(float x, float y) {
        Vec2 v = null;
        try { v = Core.input.mouseScreen(x, y); } catch (Throwable err) {}
        if (v == null || Float.isNaN(v.x) || Float.isNaN(v.y) || Float.isInfinite(v.x) || Float.isInfinite(v.y)) { try { v = Core.camera.project(x, y); } catch (Throwable err) {} }
        return v;
    }
    static void updateExpPops() {
        if (expPops.size() == 0) return;
        if (Vars.state == null || Vars.state.isMenu() || Vars.player == null) { clearExpPops(); return; }
        Unit u = null;
        try { u = Vars.player.unit(); } catch (Throwable err) {}
        if (u != null && u.isValid() && !u.dead) expLastPos = new float[]{u.x, u.y, u.type.hitSize};
        long now = Time.millis();
        float base = Vars.mobile ? 1.1f : 1.25f;
        ArrayList<ExpPop> keep = new ArrayList<>();
        for (ExpPop pp : expPops) {
            float s = (now - pp.t0) / 1000f;
            if (s >= EXP_JUMP_SEC + EXP_FADE_SEC || expLastPos == null) { try { pp.el.remove(); } catch (Throwable err) {} continue; }
            float off, sc = base, a = 1;
            if (s < EXP_JUMP_SEC) {
                float k = s / EXP_JUMP_SEC;
                off = 34 * (1 - (float) Math.pow(1 - k, 3)) + 7 * (float) Math.abs(Math.sin(k * Math.PI * 2.5)) * (1 - k);
                if (k < 0.3f) { float q = k / 0.3f - 1; sc = base * (0.4f + 0.6f * (1 + 2.7f * q * q * q + 1.7f * q * q)); }
            } else {
                float f = (s - EXP_JUMP_SEC) / EXP_FADE_SEC;
                a = 0.5f * (1 + (float) Math.cos(f * Math.PI));
                off = 41 + 14 * f;
            }
            Vec2 v = expStagePos(expLastPos[0], expLastPos[1] + expLastPos[2] / 2 + 6);
            if (v == null) { keep.add(pp); continue; }
            if (pp.lastSc != sc) { pp.lastSc = sc; pp.el.setFontScale(sc); pp.el.setSize(pp.el.getPrefWidth(), pp.el.getPrefHeight()); }
            pp.el.color.a = a;
            pp.el.setPosition(v.x, v.y + off, Align.center);
            keep.add(pp);
        }
        expPops = keep;
    }
    public static void initExpUi() {
        if (Vars.headless) return;
        Events.run(Trigger.update, () -> { try { updateExpPops(); } catch (Throwable err) { logOnce("expupdate", "exp popup update failed: " + err, false); } });
        Events.on(WorldLoadEvent.class, e -> clearExpPops());
    }

    // ===== PHAN 20: giao dien client con lai (JS 7692-8629): menu khoi, lenh linh, xay dung, HUD, camera, phim tat, joystick =====

    // ---- an menu khoi cua game ----
    static final String KEY_HIDE_BLOCKMENU = "newex-pvp-hide-blockmenu";
    static boolean blockHooked = false;
    static int blockHookTry = 0, blockHookTick = 0;
    static String blockHookWay = "";
    static Element blockHookedEl = null;
    static Table hudPanel = null;
    static int hudLastW = 0, hudLastH = 0;

    static boolean blockMenuHidden() { return isFull() && setBool(KEY_HIDE_BLOCKMENU, true); }

    static ArrayList<arc.scene.Group> uiRoots() {
        ArrayList<arc.scene.Group> groups = new ArrayList<>();
        try { if (Vars.ui.hudGroup != null) groups.add(Vars.ui.hudGroup); } catch (Throwable err) {}
        try { if (Core.scene.root != null) groups.add(Core.scene.root); } catch (Throwable err) {}
        return groups;
    }
    static boolean hasScrollDeep(Element el, int depth) {
        if (el == null || depth > 10) return false;
        try {
            if (el instanceof ScrollPane) return true;
            if (el instanceof arc.scene.Group) {
                Seq<Element> ch = ((arc.scene.Group) el).getChildren();
                for (int i = 0; i < ch.size; i++) if (hasScrollDeep(ch.get(i), depth + 1)) return true;
            }
        } catch (Throwable err) {}
        return false;
    }
    static Element topLevelOf(Element el, ArrayList<arc.scene.Group> groups) {
        Element cur = el;
        for (int n = 0; n < 30 && cur != null; n++) {
            arc.scene.Group par = cur.parent;
            if (par == null) return null;
            for (int g = 0; g < groups.size(); g++) if (par == groups.get(g)) return cur;
            cur = par;
        }
        return null;
    }
    static Element findBlockToggler() {
        ArrayList<arc.scene.Group> groups = uiRoots();
        String[] names = {"blockfrag", "blocksSelect", "inputTable", "categoryTable"};
        for (int n = 0; n < names.length; n++) {
            for (int g = 0; g < groups.size(); g++) {
                try {
                    Element found = groups.get(g).find(names[n]);
                    if (found == null) continue;
                    Element top = topLevelOf(found, groups);
                    if (top != null) { blockHookWay = "name:" + names[n]; return top; }
                } catch (Throwable err) {}
            }
        }
        for (int g = 0; g < groups.size(); g++) {
            try {
                Seq<Element> ch = groups.get(g).getChildren();
                for (int i = 0; i < ch.size; i++) {
                    Element el = ch.get(i);
                    try {
                        if (!(el instanceof Table)) continue;
                        if (hudPanel != null && el == hudPanel) continue;
                        int al = ((Table) el).getAlign();
                        if ((al & Align.bottom) == 0 || (al & Align.right) == 0) continue;
                        if (hasScrollDeep(el, 0)) { blockHookWay = "layout"; return el; }
                    } catch (Throwable err2) {}
                }
            } catch (Throwable err) {}
        }
        return null;
    }
    static void dumpHudChildren() {
        ArrayList<arc.scene.Group> groups = uiRoots();
        for (int g = 0; g < groups.size(); g++) {
            try {
                Seq<Element> ch = groups.get(g).getChildren();
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < ch.size && i < 40; i++) {
                    Element el = ch.get(i);
                    String cn = "?"; try { cn = el.getClass().getSimpleName(); } catch (Throwable err) {}
                    String al = ""; try { if (el instanceof Table) al = "" + ((Table) el).getAlign(); } catch (Throwable err) {}
                    if (i > 0) out.append(" | ");
                    out.append(cn).append("(").append(el.name != null ? el.name : "").append(al.length() > 0 ? ",a" + al : "").append(")");
                }
                Log.err("[NexPvp] group" + g + " children: " + out);
            } catch (Throwable err) {}
        }
    }
    static boolean blockHookStale() {
        if (!blockHooked || blockHookedEl == null) return false;
        try { return blockHookedEl.parent == null; } catch (Throwable err) { return true; }
    }
    static void hookBlockMenu() {
        if (Vars.headless) return;
        if (blockHookStale()) { blockHooked = false; blockHookedEl = null; }
        if (blockHooked) return;
        Element tg = findBlockToggler();
        if (tg == null) return;
        final arc.func.Boolp orig = tg.visibility;
        tg.visibility = () -> {
            if (blockMenuHidden()) return false;
            if (orig != null) return orig.get();
            return Vars.ui.hudfrag.shown;
        };
        blockHooked = true;
        blockHookedEl = tg;
        Log.info("[NexPvp] block menu hooked (" + blockHookWay + ")");
    }
    static void initBlockMenuHide() {
        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> {
            try {
                Core.bundle.getProperties().put("setting." + KEY_HIDE_BLOCKMENU + ".name", tr("Ẩn menu khối của game khi chơi PvP", "Hide the game's block menu in PvP"));
                Vars.ui.settings.game.checkPref(KEY_HIDE_BLOCKMENU, true);
            } catch (Throwable err) { Log.err("[NexPvp] cannot add block menu setting: " + err); }
            try { hookBlockMenu(); } catch (Throwable err) { logOnce("blockhook", "block menu hook failed: " + err, false); }
        });
        Events.on(WorldLoadEvent.class, e -> { blockHookTry = 0; try { hookBlockMenu(); } catch (Throwable err) { logOnce("blockhook", "block menu hook failed: " + err, false); } });
        Events.run(Trigger.update, () -> {
            blockHookTick++;
            if (blockHooked) {
                if (blockHookTick % 5 == 0 && blockHookStale()) {
                    blockHooked = false; blockHookedEl = null; blockHookTry = 0;
                    try { hookBlockMenu(); } catch (Throwable err) { logOnce("blockhook", "block menu hook failed: " + err, false); }
                }
                return;
            }
            if (blockHookTry > 60) {
                if (blockHookTick % 120 == 0) blockHookTry = 40;
                else return;
            }
            if (blockHookTick % 15 != 0) return;
            blockHookTry++;
            try { hookBlockMenu(); } catch (Throwable err) { logOnce("blockhook", "block menu hook failed: " + err, false); }
            if (!blockHooked && blockHookTry == 61) { Log.err("[NexPvp] block menu not found, cannot hide it"); dumpHudChildren(); }
        });
    }

    // ---- ra lenh linh ----
    /** Dau danh dau lenh: uid>=0 = unit dich, bpos>=0 = loi dich (JS: truong undefined -> -1). */
    static final class CMark { float x, y; int uid = -1, bpos = -1; String key; int st; }
    static CMark cmdMark = null;
    static boolean cmdPick = false, cmdOpen = false, cmdFailed = false;
    static Table cmdCenter = null, cmdBottom = null;
    static BuildMode buildMode = null;
    static Table buildHud = null;

    static boolean cmdOn() {
        St st = localState();
        return !Vars.headless && st != null && st.cmd == 1 && isActive() && st.b >= 1;
    }
    static CMark cmdTargetAt(Tile tile) {
        final Team team = Vars.player.team();
        final Vec2 mw = Core.input.mouseWorld();
        final Unit[] best = {null}; final float[] bd = {28f * 28f};
        Groups.unit.each(e -> {
            if (e.team == team || e.team == Team.derelict || e.dead || !e.isValid()) return;
            float d = Mathf.dst2(e.x, e.y, mw.x, mw.y);
            if (d < bd[0]) { bd[0] = d; best[0] = e; }
        });
        CMark g = new CMark();
        if (best[0] != null) { g.x = best[0].x; g.y = best[0].y; g.uid = best[0].id; g.key = "u" + best[0].id; return g; }
        Building b = tile.build;
        if (b != null && b.team != team && b.team != Team.derelict && tile.block() instanceof CoreBlock) { g.x = b.x; g.y = b.y; g.bpos = b.tile.pos(); g.key = "b" + b.tile.pos(); return g; }
        if (tile.solid() && tile.build == null) return null;
        g.x = tile.worldx(); g.y = tile.worldy(); g.key = "t" + tile.x + "," + tile.y;
        return g;
    }
    static void cmdTap(Tile tile) {
        if (tile == null) return;
        CMark g = cmdTargetAt(tile);
        if (g == null) return;
        CMark m = cmdMark;
        if (m != null && m.st == 1 && m.key.equals(g.key)) {
            send(REQ_CMD, g.uid >= 0 ? "au:" + g.uid : (g.bpos >= 0 ? "ab:" + g.bpos : "r:" + Math.round(g.x) + "," + Math.round(g.y)));
            g.st = 2; cmdMark = g; cmdPick = false;
            return;
        }
        g.st = 1; cmdMark = g;
    }
    static void drawCmdMark() {
        CMark m = cmdMark;
        if (m == null) return;
        if (!cmdOn()) { cmdMark = null; return; }
        float x = m.x, y = m.y;
        if (m.uid >= 0) {
            Unit tu = Groups.unit.getByID(m.uid);
            if (tu == null || !tu.isValid() || tu.dead) { cmdMark = null; return; }
            x = tu.x; y = tu.y;
        }
        Draw.z(Layer.overlayUI + 1f);
        Lines.stroke(2.5f);
        if (m.st == 1) {
            Draw.color(COL_CMD_X);
            Lines.line(x - 6, y - 6, x + 6, y + 6);
            Lines.line(x - 6, y + 6, x + 6, y - 6);
        } else {
            Draw.color(COL_CMD_O);
            Lines.circle(x, y, 8);
        }
        Draw.reset();
    }
    static String cmdStatus() {
        if (!cmdPick) return "[lightgray]" + tr("Chọn lệnh cho lính", "Pick a soldier command") + "[]";
        if (cmdMark == null) return "[accent]" + tr("Chạm vị trí / lõi / unit địch (dấu X)", "Tap a spot / core / enemy unit (X mark)") + "[]";
        return "[yellow]" + tr("Chạm lần nữa vào dấu X để bắt đầu (O)", "Tap the X again to start (O)") + "[]";
    }
    static void cmdClamp() {
        cmdCenter.setPosition(Mathf.clamp(cmdCenter.x, 0f, Math.max(0f, screenW() - cmdCenter.getWidth())), Mathf.clamp(cmdCenter.y, 0f, Math.max(0f, screenH() - cmdCenter.getHeight())));
    }
    static void buildCmdUi() {
        cmdCenter = new Table(Styles.black6);
        cmdCenter.margin(8);
        cmdCenter.touchable = Touchable.enabled;
        cmdCenter.add("[accent]" + tr("Ra lệnh lính", "Soldier orders") + "[]").pad(4);
        cmdCenter.pack();
        final String kx = "newex-pvp-cmd-x", ky = "newex-pvp-cmd-y";
        if (Core.settings.has(kx) && Core.settings.has(ky)) cmdCenter.setPosition(Core.settings.getFloat(kx, 0f), Core.settings.getFloat(ky, 0f));
        else cmdCenter.setPosition((screenW() - cmdCenter.getWidth()) / 2, (screenH() - cmdCenter.getHeight()) / 2);
        cmdClamp();
        final boolean[] dragging = {false}; final float[] off = new float[2];
        cmdCenter.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) { dragging[0] = false; off[0] = x; off[1] = y; return true; }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - off[0]) > 5 || Math.abs(y - off[1]) > 5) dragging[0] = true;
                if (dragging[0]) { cmdCenter.setPosition(cmdCenter.x + x - off[0], cmdCenter.y + y - off[1]); cmdClamp(); }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragging[0]) { try { Core.settings.put(kx, cmdCenter.x); Core.settings.put(ky, cmdCenter.y); } catch (Throwable err) {} }
                else { cmdOpen = !cmdOpen; if (!cmdOpen) cmdPick = false; }
                dragging[0] = false;
            }
        });
        Core.scene.add(cmdCenter);

        cmdBottom = new Table();
        cmdBottom.setFillParent(true);
        cmdBottom.bottom();
        try { cmdBottom.touchable = Touchable.childrenOnly; } catch (Throwable err) {}
        int padB = Math.round(Math.max(8f, Math.min(Vars.mobile ? 90f : 60f, screenH() * 0.12f)));
        cmdBottom.table(Styles.black6, c -> {
            c.margin(8);
            c.label(() -> cmdStatus()).colspan(2).wrap().width(narrowScreen() ? 240 : 320).pad(4).row();
            c.button(tr("Theo dõi tôi", "Follow me"), () -> { cmdPick = false; send(REQ_CMD, "f"); }).size(narrowScreen() ? 110 : 140, btnH()).pad(3);
            c.button(tr("Điều hướng", "Move / attack"), () -> { cmdPick = true; cmdMark = null; }).size(narrowScreen() ? 110 : 140, btnH()).pad(3);
        }).padBottom(padB);
        Core.scene.add(cmdBottom);
    }
    static void initCmdUi() {
        if (Vars.headless) return;
        Events.run(Trigger.update, () -> {
            if (cmdFailed) return;
            if (cmdCenter == null) {
                if (Core.scene == null || Vars.ui == null || Vars.ui.hudGroup == null) return;
                try { buildCmdUi(); } catch (Throwable err) { logOnce("cmdui", "command ui failed: " + err, false); cmdFailed = true; return; }
            }
            boolean on = cmdOn() && buildMode == null;
            if (!cmdOn()) { cmdOpen = false; cmdPick = false; }
            cmdCenter.visible = on;
            cmdBottom.visible = on && cmdOpen;
        });
    }

    // ---- che do chon vi tri xay ----
    static final class BuildMode { String id; int tx = -1, ty = -1; boolean ok; ArrayList<Tile> ec = new ArrayList<>(); float ecT = -999; }

    static void stopBuild() {
        buildMode = null;
        if (buildHud != null) { try { buildHud.remove(); } catch (Throwable err) {} buildHud = null; }
    }
    static int bagIdxOf(St st, String id) {
        for (int i = 0; i < st.d.size(); i++) {
            int p = st.p.get(i);
            if (st.d.get(i).equals(id) && p != EQUIPPED && !(p >= 0)) return i;
        }
        return -1;
    }
    static Block buildBlock(String id) {
        if (id.equals(WALL_BUILD_ID)) { St s = localState(); return wallBlock(s != null ? s.wl : 0); }
        Def d = droneDefs().map.get(id);
        return d != null ? d.block : null;
    }
    static String buildStatus() {
        BuildMode m = buildMode;
        if (m == null) return "";
        Block b = buildBlock(m.id);
        String nm = b != null ? b.localizedName : "";
        if (m.tx < 0) return "[accent]" + tr("Chạm 2 lần vào vị trí trên nền đặt lõi để xây ", "Tap twice on a core-zone tile to build ") + nm + "[]";
        return m.ok ? "[green]" + tr("Chạm lần nữa vào ô này để xây ", "Tap the same tile again to build ") + nm + "[]"
                    : "[scarlet]" + tr("Vị trí không hợp lệ (cần nền đặt lõi trống, nửa map phe ta)", "Invalid spot (need free core-zone on your half)") + "[]";
    }
    static void fillBuildHud(Table t) {
        t.clear();
        t.bottom();
        final boolean narrow = narrowScreen();
        final float maxW = Math.max(180f, Math.min(screenW() - 20, narrow ? 440f : 680f));
        final float bw = narrow ? 80 : 100;
        int padB = Math.round(Math.max(8f, Math.min(Vars.mobile ? 90f : 60f, screenH() * 0.12f)));
        t.table(Styles.black6, c -> {
            c.margin(narrow ? 6 : 8);
            float lw = narrow ? maxW - 12 : maxW - bw - 24;
            Label lb = c.label(() -> buildStatus()).width(lw).wrap().pad(4).get();
            lb.setFontScale(narrow ? 0.85f : 1f);
            if (narrow) c.row();
            c.button(tr("Hủy", "Cancel"), () -> stopBuild()).size(bw, btnH()).pad(narrow ? 2 : 4);
        }).padBottom(padB);
    }
    static void showBuildHud() {
        final Table t = new Table();
        t.setFillParent(true);
        try { t.touchable = Touchable.childrenOnly; } catch (Throwable err) {}
        final String[] key = {(int) screenW() + "x" + (int) screenH()};
        fillBuildHud(t);
        t.update(() -> {
            String k = (int) screenW() + "x" + (int) screenH();
            if (!k.equals(key[0])) { key[0] = k; fillBuildHud(t); }
        });
        Core.scene.add(t);
        buildHud = t;
    }
    static void startBuild(String id) {
        if (Vars.headless || !isActive()) return;
        St st = localState();
        if (st == null || droneDefs().map.get(id) == null || bagIdxOf(st, id) < 0) return;
        if (upDialog != null) upDialog.hide();
        if (bagDialog != null) bagDialog.hide();
        stopBuild();
        BuildMode m = new BuildMode(); m.id = id; buildMode = m;
        showBuildHud();
    }
    static void startWallBuild() {
        if (Vars.headless || !isActive()) return;
        St st = localState();
        if (st == null || st.w != IN_BAG) return;
        if (upDialog != null) upDialog.hide();
        if (bagDialog != null) bagDialog.hide();
        stopBuild();
        BuildMode m = new BuildMode(); m.id = WALL_BUILD_ID; buildMode = m;
        showBuildHud();
    }
    static boolean buildCheck(BuildMode m, int tx, int ty) {
        CoreBlock.CoreBuild core = Vars.player.team().core();
        Block b = buildBlock(m.id);
        if (core == null || b == null) return false;
        return canBuildAt(core, m.ec, b, tx, ty);
    }
    static void buildTap(Tile tile) {
        BuildMode m = buildMode;
        if (m == null) return;
        CoreBlock.CoreBuild core = Vars.player.team().core();
        if (core != null && Time.time - m.ecT > 30) { m.ec = enemyCores(core.team); m.ecT = Time.time; }
        if (m.tx == tile.x && m.ty == tile.y && m.ok) {
            St st = localState();
            if (m.id.equals(WALL_BUILD_ID)) send(REQ_WALL, "b|" + tile.x + "|" + tile.y);
            else {
                int idx = st != null ? bagIdxOf(st, m.id) : -1;
                if (idx >= 0) send(REQ_MODE, idx + "|b|" + tile.x + "|" + tile.y);
            }
            stopBuild();
            return;
        }
        m.tx = tile.x; m.ty = tile.y;
        m.ok = buildCheck(m, tile.x, tile.y);
    }
    static void drawBuildPreview() {
        BuildMode m = buildMode;
        if (m == null) return;
        CoreBlock.CoreBuild core = Vars.player.team().core();
        Block blk = buildBlock(m.id);
        if (!isActive() || Vars.player.dead() || core == null || blk == null) { stopBuild(); return; }
        if (Time.time - m.ecT > 30) { m.ec = enemyCores(core.team); m.ecT = Time.time; }
        float ts = Vars.tilesize;
        if (BUILD_FLOOR.length() > 0 && isFull() && buildZoneOn()) {
            int cx = Math.round(Core.camera.position.x / ts), cy = Math.round(Core.camera.position.y / ts);
            Draw.z(Layer.overlayUI);
            Draw.color(COL_BUILD_OK, 0.2f);
            for (int x = cx - 24; x <= cx + 24; x++) {
                for (int y = cy - 24; y <= cy + 24; y++) {
                    Tile t = Vars.world.tile(x, y);
                    if (t == null || t.solid() || t.build != null) continue;
                    if (!isCoreZoneFloor(t.floor())) continue;
                    if (ourSide(core, m.ec, x, y)) Fill.rect(x * ts, y * ts, ts, ts);
                }
            }
        }
        if (m.tx >= 0) {
            float sz = blk.size * ts, px = m.tx * ts + blk.offset, py = m.ty * ts + blk.offset;
            Draw.z(Layer.overlayUI + 1f);
            Draw.color(m.ok ? COL_BUILD_OK : COL_BUILD_BAD, 0.4f);
            Fill.rect(px, py, sz, sz);
            Draw.color(m.ok ? COL_BUILD_OK : COL_BUILD_BAD, 1f);
            Lines.stroke(2f);
            Lines.rect(px - sz / 2, py - sz / 2, sz, sz);
            Draw.color(Color.white, 0.6f);
            Draw.rect(blk.uiIcon, px, py, sz, sz);
        }
        Draw.reset();
    }
    static void initTap() {
        Events.on(TapEvent.class, e -> {
            if (Vars.headless || !isActive() || e.player != Vars.player || e.tile == null) return;
            if (buildMode != null) { buildTap(e.tile); return; }
            if (cmdPick) { cmdTap(e.tile); return; }
            if (!(e.tile.block() instanceof CoreBlock)) return;
            if (e.tile.team() != Vars.player.team()) return;
            openUpgrade();
        });
    }

    // ---- HUD EXP ----
    static String hudLvText() { St st = localState(); return st == null ? "" : "[accent]" + tr("Cấp ", "Lv ") + "[white]" + st.lv + "[]"; }
    static String hudPtText() { St st = localState(); return st == null ? "" : "[yellow]" + tr("ĐP ", "Pt ") + st.pts + "[]"; }
    static String hudTailText() {
        St st = localState();
        if (st == null) return "";
        float sl = invulTicksLeft();
        return sl > 0 ? "[cyan]" + tr("Khiên ", "Shield ") + fmtTicks(sl) + "[]" : "[lightgray]" + tierType(activeTier(st)).localizedName + "[]";
    }
    static String expText() {
        St st = localState();
        if (st == null) return "";
        if (st.lv >= maxLevel()) return "MAX";
        return (int) Math.floor(st.exp) + "/" + expNeed(st.lv);
    }
    static float expFrac() {
        St st = localState();
        if (st == null || st.lv >= maxLevel()) return 1f;
        return Math.max(0f, Math.min(1f, st.exp / expNeed(st.lv)));
    }
    static boolean hudVisible() { return setBool(KEY_HUD, true); }
    static void hudSetVisible(boolean v) {
        try { Core.settings.put(KEY_HUD, v); } catch (Throwable err) {}
        cfgBool.put(KEY_HUD, v);
        cfgBoolAt.put(KEY_HUD, Time.time);
    }
    static void hudSavePos() {
        if (hudPanel == null) return;
        try { Core.settings.put(KEY_HUD_X, hudPanel.x); Core.settings.put(KEY_HUD_Y, hudPanel.y); } catch (Throwable err) {}
    }
    static void hudCenter() {
        if (hudPanel == null) return;
        hudPanel.setPosition((screenW() - hudPanel.getWidth()) / 2, screenH() - hudPanel.getHeight() - (Vars.mobile ? 90 : 70));
        hudSavePos();
    }
    static void hudClamp() {
        if (hudPanel == null) return;
        float mx = Math.max(0f, screenW() - hudPanel.getWidth()), my = Math.max(0f, screenH() - hudPanel.getHeight());
        hudPanel.setPosition(Mathf.clamp(hudPanel.x, 0f, mx), Mathf.clamp(hudPanel.y, 0f, my));
        hudSavePos();
    }
    static void initHudPanel() {
        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> {
            try {
                Core.bundle.getProperties().put("setting." + KEY_HUD + ".name", tr("Hiện UI EXP PvP (Shift+2)", "Show PvP EXP UI (Shift+2)"));
                Vars.ui.settings.game.checkPref(KEY_HUD, true);
            } catch (Throwable err) { Log.err("[NexPvp] cannot add HUD setting: " + err); }
            final float W = Vars.mobile ? 150 : 170;
            final Table t = new Table(Styles.black6);
            hudPanel = t;
            t.name = "newex-pvp-hud";
            t.margin(6);
            t.touchable = Touchable.enabled;
            Table top = new Table();
            top.label(() -> hudLvText()).left().growX().get().setFontScale(0.85f);
            top.label(() -> hudPtText()).right().get().setFontScale(0.85f);
            t.add(top).width(W);
            t.row();
            t.add(new Bar(() -> expText(), () -> Pal.accent, () -> expFrac())).size(W, Vars.mobile ? 18 : 16).padTop(4);
            t.row();
            Label tl = t.label(() -> hudTailText()).width(W).padTop(4).get();
            tl.setFontScale(0.8f);
            tl.setAlignment(Align.center);
            t.row();
            TextButton ub = t.button(tr("Nâng cấp", "Upgrade"), Icon.up, () -> openUpgrade()).size(W, Vars.mobile ? 36 : 28).padTop(5).get();
            ub.getLabel().setFontScale(0.8f);
            t.visibility = () -> isActive() && Vars.ui.hudfrag.shown && hudVisible();
            t.pack();
            if (Core.settings.has(KEY_HUD_X) && Core.settings.has(KEY_HUD_Y)) { t.setPosition(Core.settings.getFloat(KEY_HUD_X, 0f), Core.settings.getFloat(KEY_HUD_Y, 0f)); hudClamp(); }
            else hudCenter();
            final boolean[] dragging = {false}; final float[] off = new float[2];
            t.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) { dragging[0] = false; off[0] = x; off[1] = y; return true; }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (Math.abs(x - off[0]) > 5 || Math.abs(y - off[1]) > 5) dragging[0] = true;
                    if (dragging[0]) {
                        float mx = Math.max(0f, screenW() - t.getWidth()), my = Math.max(0f, screenH() - t.getHeight());
                        t.setPosition(Mathf.clamp(t.x + x - off[0], 0f, mx), Mathf.clamp(t.y + y - off[1], 0f, my));
                        hudSavePos();
                    }
                }
                @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) { dragging[0] = false; }
            });
            Vars.ui.hudGroup.addChild(t);
            hudLastW = Core.graphics.getWidth(); hudLastH = Core.graphics.getHeight();
        });
    }

    // ---- camera + phim tat + cheat ----
    static int camBot = 0;
    static int camMode = 0; // 0: theo unit, 1: goc nhin tu do, 2: camera bot
    static java.lang.reflect.Field panField = null; static boolean panTried = false;
    /** InputHandler v157 khong co truong panning (chi lop con nhu DesktopInput) -> dat qua phan xa tren lop that. */
    static void setPanning(boolean v) {
        try {
            Object in = Vars.control.input;
            if (!panTried) { panTried = true; for (Class<?> c = in.getClass(); c != null && panField == null; c = c.getSuperclass()) { try { panField = c.getDeclaredField("panning"); panField.setAccessible(true); } catch (NoSuchFieldException ignored) {} } }
            if (panField != null && panField.getDeclaringClass().isInstance(in)) panField.setBoolean(in, v);
        } catch (Throwable err) {}
    }
    static void camNote() {
        try { Vars.ui.showInfoToast(camMode == 2 ? tr("Camera bot: BẬT", "Bot camera: ON") : camMode == 1 ? tr("Góc nhìn tự do: BẬT", "Free view: ON") : tr("Camera: theo unit", "Camera: follow unit"), 2f); } catch (Throwable err) {}
        try { if (camMode == 0) setPanning(false); } catch (Throwable err) {}
    }
    static void initCameraKeys() {
        if (Vars.headless) return;
        Events.run(Trigger.preDraw, () -> {
            if (coreCine != null) {
                try {
                    CoreCine c = coreCine;
                    float dt = Math.min(6f, Core.graphics.getDeltaTime() * 60f);
                    c.t += dt;
                    setPanning(true);
                    Vec2 cp = Core.camera.position;
                    float k = 1f - (float) Math.exp(-dt / 18f);
                    cp.set(cp.x + (c.x - cp.x) * k, cp.y + (c.y - cp.y) * k);
                    if (c.t > 60 && c.t < c.dur) {
                        c.fx += dt;
                        if (c.fx >= 6) {
                            c.fx = 0;
                            float pw = (c.t - 60) / (c.dur - 60), rx = c.x + ((float) Math.random() - 0.5f) * 60, ry = c.y + ((float) Math.random() - 0.5f) * 60;
                            try { Fx.explosion.at(rx, ry); } catch (Throwable err) {}
                            if (pw > 0.5f) try { Fx.smokeCloud.at(rx, ry); } catch (Throwable err) {}
                            try { Effect.shake(2 + pw * 6, 10, c.x, c.y); } catch (Throwable err) {}
                        }
                    }
                    if (c.t >= c.dur + 90) { coreCine = null; if (camMode == 0) setPanning(false); }
                } catch (Throwable err) { coreCine = null; }
                return;
            }
            if (camMode == 0) return;
            if (!isActive()) { camMode = 0; return; }
            try {
                setPanning(true);
                Vec2 cp = Core.camera.position;
                if (camMode == 2) {
                    Unit bu = bots.size() > 0 ? bots.get(Math.min(camBot, bots.size() - 1)).botPl.unit() : null;
                    if (bu != null && bu.isValid() && !bu.dead) cp.set(bu.x, bu.y);
                } else {
                    float sp = 900f * Math.min(0.1f, Core.graphics.getDeltaTime());
                    if (!Core.scene.hasKeyboard()) {
                        if (Core.input.keyDown(KeyCode.left)) cp.x -= sp;
                        if (Core.input.keyDown(KeyCode.right)) cp.x += sp;
                        if (Core.input.keyDown(KeyCode.up)) cp.y += sp;
                        if (Core.input.keyDown(KeyCode.down)) cp.y -= sp;
                    }
                }
            } catch (Throwable err) {}
        });
        Events.run(Trigger.update, () -> {
            if (hudPanel == null) return;
            int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
            if (w != hudLastW || h != hudLastH) { hudLastW = w; hudLastH = h; hudClamp(); }
            if (!isActive()) return;
            try {
                boolean sh = Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight);
                if (sh && Core.input.keyTap(KeyCode.num2) && !Core.scene.hasKeyboard()) hudSetVisible(!hudVisible());
                if (sh && !Core.scene.hasKeyboard()) {
                    if (Core.input.keyTap(KeyCode.num5)) { camMode = camMode == 1 ? 0 : 1; camNote(); }
                    if (Core.input.keyTap(KeyCode.num6)) { if (camMode == 2 && camBot + 1 < bots.size()) camBot++; else { camMode = camMode == 2 ? 0 : 2; camBot = 0; } camNote(); }
                    if (Core.input.keyTap(KeyCode.num9)) { Vec2 mw = Core.input.mouseWorld(); send(REQ_CMD, "r:" + Math.round(mw.x) + "," + Math.round(mw.y)); }
                    if (Core.input.keyTap(KeyCode.num0)) send(REQ_CMD, "f");
                    if (Core.input.keyTap(KeyCode.num7)) send(REQ_PT, "me");
                    if (Core.input.keyTap(KeyCode.num8)) send(REQ_PT, "foe");
                }
            } catch (Throwable err) {}
        });
        // chuoi phim bi mat (CHEAT_SEQ)
        final int[] cheatIdx = {0}; final float[] cheatT = {0};
        final KeyCode[] cheatKeys = new KeyCode[26];
        for (int i = 0; i < 26; i++) { try { cheatKeys[i] = KeyCode.valueOf(String.valueOf((char) ('a' + i))); } catch (Throwable err) {} }
        Events.run(Trigger.update, () -> {
            if (!isActive()) { cheatIdx[0] = 0; return; }
            try {
                if (Core.scene.hasKeyboard()) { cheatIdx[0] = 0; return; }
                if (cheatIdx[0] > 0 && Time.time - cheatT[0] > CHEAT_GAP_TICKS) cheatIdx[0] = 0;
                for (int i = 0; i < 26; i++) {
                    if (cheatKeys[i] == null || !Core.input.keyTap(cheatKeys[i])) continue;
                    String ch = String.valueOf((char) ('a' + i));
                    if (ch.equals(CHEAT_SEQ[cheatIdx[0]])) cheatIdx[0]++;
                    else cheatIdx[0] = ch.equals(CHEAT_SEQ[0]) ? 1 : 0;
                    cheatT[0] = Time.time;
                    if (cheatIdx[0] >= CHEAT_SEQ.length) { cheatIdx[0] = 0; send(REQ_CHEAT, "1"); }
                    break;
                }
            } catch (Throwable err) {}
        });
    }

    // ---- joystick (mobile) ----
    static Element joyPad = null;
    static float joyDx = 0, joyDy = 0, joyOx = 0, joyOy = 0, joyLagT = 0, joyPrevX = 0, joyPrevY = 0;
    static int joyPointer = -1;
    static Unit joyPrevUnit = null;
    static final float JOY_SIZE = 170f, JOY_R = JOY_SIZE / 2, JOY_KNOB = JOY_R * 0.4f, JOY_REACH = JOY_R - JOY_KNOB, JOY_DEAD = 0.12f;
    static final float JOY_CAM_LAG = 0.5f, JOY_LAG_SEC = 1.5f, JOY_CAM_MIN_SPD = 900f;
    static final Vec2 joyVec = new Vec2();

    static boolean joyEnabled() { return setBool(KEY_JOY, true); }
    static Unit joyUnit() {
        if (Vars.player == null || Vars.player.dead()) return null;
        Unit u = Vars.player.unit();
        if (u == null || !u.isValid() || u.dead) return null;
        return u;
    }
    static boolean joyShown() {
        if (!Vars.mobile || Vars.headless || !pvpCtl() || !joyEnabled()) return false;
        if (!Vars.ui.hudfrag.shown || Core.scene.hasDialog()) return false;
        return joyUnit() != null;
    }
    static boolean joyLocksCam() { return Vars.mobile && !Vars.headless && pvpCtl() && joyEnabled() && camMode == 0 && coreCine == null; }
    static void joyReset() { joyDx = 0; joyDy = 0; joyPointer = -1; }
    static void joyTouch(float x, float y) {
        float dx = (x - joyOx) / JOY_REACH, dy = (y - joyOy) / JOY_REACH;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1) { dx /= len; dy /= len; len = 1; }
        if (len < JOY_DEAD) { joyDx = 0; joyDy = 0; } else { joyDx = dx; joyDy = dy; }
    }
    static void joyPlace() {
        if (joyPad == null) return;
        float gw = 0, gh = 0;
        try { gw = Vars.ui.hudGroup.getWidth(); gh = Vars.ui.hudGroup.getHeight(); } catch (Throwable err) {}
        if (!(gw > 0)) gw = screenW();
        if (!(gh > 0)) gh = screenH();
        joyPad.setPosition(0, 0);
        float side = (float) Math.floor(Math.min(gw, gh) * 0.5f);
        joyPad.setSize(side, side);
    }
    static int joyLastW = 0, joyLastH = 0;
    static void initJoystick() {
        if (Vars.headless) return;
        Events.on(ClientLoadEvent.class, e -> {
            try {
                Core.bundle.getProperties().put("setting." + KEY_JOY + ".name", tr("Bật joystick điều khiển unit (tắt camera tự do)", "Enable unit joystick (disables free camera)"));
                Vars.ui.settings.game.checkPref(KEY_JOY, true);
            } catch (Throwable err) { Log.err("[NexPvp] cannot add joystick setting: " + err); }
            if (!Vars.mobile) return;
            joyPad = new Element() {
                @Override public void draw() {
                    if (joyPointer == -1) return;
                    float zx = x + 2, zy = y + 2, zw = getWidth() - 4, zh = getHeight() - 4;
                    Draw.color(Color.white, 0.55f);
                    Lines.stroke(2.5f);
                    Lines.dashLine(zx, zy, zx + zw, zy, Math.max(2, Math.round(zw / 18)));
                    Lines.dashLine(zx + zw, zy, zx + zw, zy + zh, Math.max(2, Math.round(zh / 18)));
                    Lines.dashLine(zx + zw, zy + zh, zx, zy + zh, Math.max(2, Math.round(zw / 18)));
                    Lines.dashLine(zx, zy + zh, zx, zy, Math.max(2, Math.round(zh / 18)));
                    float cx = x + joyOx, cy = y + joyOy;
                    Draw.color(Color.black, 0.35f);
                    Fill.circle(cx, cy, JOY_R);
                    Draw.color(Color.white, 0.45f);
                    Lines.stroke(3f);
                    Lines.circle(cx, cy, JOY_R);
                    Draw.color(Pal.accent, 0.95f);
                    Fill.circle(cx + joyDx * JOY_REACH, cy + joyDy * JOY_REACH, JOY_KNOB);
                    Draw.reset();
                }
            };
            joyPad.name = "newex-pvp-joystick";
            joyPad.touchable = Touchable.enabled;
            joyPad.visibility = () -> joyShown();
            joyPad.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    if (joyPointer != -1) return false;
                    joyPointer = pointer; joyOx = x; joyOy = y; joyDx = 0; joyDy = 0;
                    return true;
                }
                @Override public void touchDragged(InputEvent event, float x, float y, int pointer) { if (pointer != joyPointer) return; joyTouch(x, y); }
                @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) { if (pointer != joyPointer) return; joyReset(); }
            });
            joyPlace();
            try { Vars.ui.hudGroup.addChildAt(0, joyPad); } catch (Throwable err) { Vars.ui.hudGroup.addChild(joyPad); }
        });
        if (!Vars.mobile) return;
        Events.run(Trigger.preDraw, () -> {
            if (joyPad == null) return;
            int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
            if (w != joyLastW || h != joyLastH) { joyLastW = w; joyLastH = h; joyPlace(); }
            if (joyLocksCam()) {
                Unit cu = joyUnit();
                float dtl = Math.min(0.1f, Core.graphics.getDeltaTime());
                if (cu == null) {
                    if (joyPrevUnit != null) joyLagT = JOY_LAG_SEC;
                    joyPrevUnit = null;
                } else {
                    float jx = cu.x - joyPrevX, jy = cu.y - joyPrevY;
                    if (cu != joyPrevUnit || jx * jx + jy * jy > 400f * 400f) joyLagT = JOY_LAG_SEC;
                    joyPrevUnit = cu; joyPrevX = cu.x; joyPrevY = cu.y;
                }
                if (joyLagT > 0) joyLagT -= dtl;
                if (cu != null) {
                    try { setPanning(false); } catch (Throwable err) {}
                    try {
                        Vec2 cp = Core.camera.position;
                        float dtc = Math.min(0.1f, Core.graphics.getDeltaTime());
                        float ddx = cu.x - cp.x, ddy = cu.y - cp.y, dd = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                        float step = Math.max(dd * (1f - (float) Math.exp(-dtc / JOY_CAM_LAG)), JOY_CAM_MIN_SPD * dtc);
                        if (joyLagT <= 0 || dd <= step) cp.set(cu.x, cu.y);
                        else cp.set(cp.x + ddx / dd * step, cp.y + ddy / dd * step);
                    } catch (Throwable err) {}
                }
            }
            if (!joyShown()) { if (joyPointer != -1 || joyDx != 0 || joyDy != 0) joyReset(); return; }
            if (joyPointer == -1 || (joyDx == 0 && joyDy == 0)) return;
            Unit u = joyUnit();
            if (u == null) return;
            try {
                joyVec.set(joyDx, joyDy).limit(1f).scl(u.speed());
                u.movePref(joyVec);
            } catch (Throwable err) { logOnce("joystick", "joystick move failed: " + err, false); }
        });
    }

    /** JS WorldLoadEvent: dat lai trang thai giao dien (goi tu worldLoad()). */
    static void resetUiState() {
        recentUp = newUpLists(); viewUp = newUpLists();
        pilotRecent = new ArrayList<>(); viewPilot = new ArrayList<>(); bagRecent = new ArrayList<>(); viewBag = new ArrayList<>();
        lastUpTab = 0;
        pilotRegCache.clear();
        if (upDialog != null) { try { upDialog.hide(); } catch (Throwable err) {} upDialog = null; }
    }
    @SuppressWarnings("unchecked")
    static ArrayList<Object>[] newUpLists() { return new ArrayList[]{new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>(), new ArrayList<Object>()}; }

    /** Goi mot lan tu NexMod (cung cho voi initExpUi()). */
    public static void initClientUi() {
        if (Vars.headless) return;
        initBlockMenuHide();
        initCmdUi();
        initTap();
        initHudPanel();
        initCameraKeys();
        initJoystick();
    }

    /** Cau noi thay cho global.NexPvp (NewMode.pvp) - tuong tu Survival.HOOK. */
    static final NewMode.ModeHook HOOK = new NewMode.ModeHook() {
        @Override public int rewardPoints() { return REWARD_POINTS; }
        @Override public void onBegin() { NewModePvp.onBegin(); }
        @Override public void onResume() { NewModePvp.onResume(); }
        @Override public void persistStore() { NewModePvp.apiPersistStore(); }
    };

    /** Goi MOT lan tu NexMod.init() (thu tu main.js: sau SurvivorSkills, truoc ArchuengPvp). */
    public static void init() {
        NewMode.pvp = HOOK;
        ArchuengPvp.pvpActive = NewModePvp::apiIsActive;
        LoadoutSelection.pvpSettingsHook = NewModePvp::addSettings;
        initBase();
        initDrawShield();
        initPilot();
        initUpdate();
        initNet();
        initUnitListUi();
        initExpUi();
        initClientUi();
    }

}
