// =====================================================================
//  NewMode SURVIVAL - Kĩ năng khi lên cấp (Rhino Compatible)
// =====================================================================
(function() {
    function isEn() {
        return String(Core.settings.getString("locale")).startsWith("en");
    }

    const KEY_MAX_LEVEL = "newex-survival-max-level";
    const KEY_EXP_PERCENT = "newex-survival-exp-percent";
    const KEY_EXP_BY_HP = "newex-survival-exp-by-hp";
    const DEFAULT_MAX_LEVEL = 10;
    const DEFAULT_EXP_PERCENT = 100;
    const LIMIT_MAX_LEVEL = 999;
    const LIMIT_EXP_PERCENT = 1000;
    const HP_EXP_REF = 130;
    const HP_EXP_MAX_MULT = 50;

    function levelCost(n) {
        if (n <= 0) return 0;
        return Math.round(35 + 20 * (n - 1) + 2.5 * (n - 1) * (n - 2));
    }

    function getMaxLevel() {
        let v = DEFAULT_MAX_LEVEL;
        try { v = Core.settings.getInt(KEY_MAX_LEVEL, DEFAULT_MAX_LEVEL); } catch (err) {}
        if (!(v >= 1)) v = DEFAULT_MAX_LEVEL;
        return Math.min(LIMIT_MAX_LEVEL, Math.floor(v));
    }

    function getExpPercent() {
        let v = DEFAULT_EXP_PERCENT;
        try { v = Core.settings.getInt(KEY_EXP_PERCENT, DEFAULT_EXP_PERCENT); } catch (err) {}
        if (!(v >= 0)) v = DEFAULT_EXP_PERCENT;
        return Math.min(LIMIT_EXP_PERCENT, v);
    }

    function isExpByHp() {
        try { return Core.settings.getBool(KEY_EXP_BY_HP, false); } catch (err) {}
        return false;
    }

    const KILL_CREDIT_RADIUS = 260;
    const SKILL_CHOICE_COUNT = 3;
    const MAX_TIER_SHOTS = 4;
    const MAX_BULLETS_PER_VOLLEY = 12;
    const MIN_RELOAD = 6;

    const DRONE_R_MIN = 22;
    const DRONE_R_MAX = 66;
    const DRONE_WANDER_MIN = 45;
    const DRONE_WANDER_MAX = 140;
    const DRONE_SPRING = 0.012;
    const DRONE_DRAG = 0.92;
    const DRONE_MAX_SPEED = 4.2;
    const DRONE_LEASH = 150;
    const DRONE_TELEPORT = 520;
    const DRONE_SEPARATION = 16;

    const REAL_TURRETS = true;
    const TIER_SPEED_BONUS = 0.2;
    const MAX_CREDIT_RADIUS = 2000;

    const SKILL_SRC = [
        { id: "nucleytor", vi: "Đạn xuyên 3 mục tiêu", en: "Pierces 3 targets", speed: 8, dmg: 24, life: 55, w: 9, h: 14, front: "ffffff", back: "ffef9e", pierce: 3, knock: 0.5, reload: 22, range: 440 },
        { id: "emperfum", vi: "Đạn nặng nổ diện rộng, chậm", en: "Heavy slow shell, big blast", speed: 6, dmg: 40, life: 75, w: 12, h: 12, front: "ffffff", back: "c77dff", splash: 90, radius: 44, homing: 0.03, reload: 75, range: 430 },
        { id: "galaxvorram", vi: "Laser lớn, sát thương cao", en: "Big laser, high damage", kind: "laser", dmg: 85, len: 330, w: 16, back: "ff4444", mid: "ff8866", spread: 3, reload: 110, range: 320 },
        { id: "dor", vi: "Đạn nhanh, hơi tự dẫn", en: "Fast shots, slight homing", speed: 7, dmg: 16, life: 45, w: 7, h: 18, front: "e0f7fa", back: "00bcd4", homing: 0.05, reload: 16, range: 300 },
        { id: "flazerd", vi: "Laser mảnh bắn dồn dập", en: "Rapid thin laser", kind: "laser", dmg: 14, len: 250, w: 6, back: "ff9d3a", mid: "ffd27f", spread: 3, reload: 12, range: 240 },
        { id: "erysidus", vi: "Tên lửa tự dẫn tầm xa", en: "Long-range homing missile", kind: "missile", speed: 5, dmg: 22, life: 100, w: 8, h: 12, front: "e3f2fd", back: "90caf9", splash: 30, radius: 28, homing: 0.09, homingRange: 200, reload: 40, range: 480 },
        { id: "blixalum", vi: "Đạn điện nổ lan, gây giật", en: "Shock blast, area damage", speed: 8, dmg: 14, life: 50, w: 10, h: 14, front: "ffffff", back: "a1ff9a", splash: 34, radius: 36, status: "shocked", statusTime: 60, reload: 24, range: 380 },
        { id: "heliyron", vi: "Đạn nặng tự dẫn mạnh", en: "Heavy homing shots", speed: 12, dmg: 38, life: 36, w: 7, h: 30, front: "e0f7fa", back: "d47c00", homing: 0.1, homingRange: 150, reload: 26, range: 410 },
        { id: "persefer", vi: "Gatling bắn cực nhanh", en: "Very fast gatling", speed: 14, dmg: 8, life: 28, w: 5, h: 12, front: "ffd27f", back: "ff5a3a", pierce: 2, reload: 7, range: 370 },
        { id: "tankani2k", vi: "Phát đạn mạnh, xuyên 2", en: "Strong shot, pierces 2", speed: 15, dmg: 105, life: 30, w: 10, h: 33, front: "e0ea87", back: "e5ff00", pierce: 2, reload: 70, range: 420 },
        { id: "zoj", vi: "Đạn nhẹ tự dẫn, bắn dày", en: "Light homing, rapid fire", speed: 10, dmg: 8, life: 28, w: 4, h: 12, front: "e0ea87", back: "e5ff00", homing: 0.25, homingRange: 200, reload: 9, range: 260 },
        { id: "reflecounum", vi: "Đạn phản chiếu xuyên 4", en: "Reflected shot, pierces 4", speed: 8, dmg: 20, life: 45, w: 8, h: 12, front: "ffffff", back: "7ee9ff", pierce: 4, homing: 0.1, reload: 26, range: 330 },
        { id: "swordoder", vi: "Nhát chém xuyên, sát thương lớn", en: "Piercing slash, heavy damage", speed: 7, dmg: 60, life: 30, w: 22, h: 22, front: "ffffff", back: "ff4d4d", pierce: 5, reload: 55, range: 195 },
        { id: "buffetles", vi: "Đạn xuyên 2, bắn đều", en: "Steady shots, pierce 2", speed: 9, dmg: 26, life: 45, w: 5, h: 15, front: "dae1ee", back: "7d89d8", pierce: 2, reload: 22, range: 370 },
        { id: "maxitoner", vi: "Đạn thủy tinh làm chậm", en: "Glass shots slow enemies", speed: 7, dmg: 14, life: 50, w: 8, h: 12, front: "e2f3f5", back: "22d1ee", status: "slow", statusTime: 90, reload: 28, range: 320 },
        { id: "xylaon", vi: "Đạn nóng, gây cháy", en: "Hot shots, burn enemies", speed: 11, dmg: 9, life: 32, w: 4, h: 12, front: "ffe2a8", back: "feb380", status: "burning", statusTime: 120, reload: 8, range: 320 },
        { id: "lyvervon", vi: "Sét hồ quang liên hoàn", en: "Chain arc lightning", kind: "lightning", dmg: 20, len: 40, back: "9be7ff", reload: 24, range: 140 },
        { id: "vendicum", vi: "Đạn xuyên 3, rất mạnh", en: "Strong shot, pierces 3", speed: 9, dmg: 72, life: 45, w: 11, h: 16, front: "ffffff", back: "e0b080", pierce: 3, knock: 1, reload: 55, range: 380 },
        { id: "drone-launcher", vi: "Đạn drone đuổi theo địch", en: "Drone shots chase enemies", speed: 5, dmg: 11, life: 110, w: 6, h: 6, front: "ffffff", back: "9fd7ff", homing: 0.3, homingRange: 350, reload: 12, range: 500 },
        { id: "holyder", vi: "Đạn cực nhanh, xuyên 4", en: "Ultra-fast, pierces 4", speed: 28, dmg: 46, life: 18, w: 12, h: 16, front: "ffffff", back: "7ad0ff", pierce: 4, reload: 40, range: 470 },
        { id: "plasanod", vi: "Plasma xuyên, làm nóng chảy", en: "Plasma pierce + melt", speed: 9, dmg: 30, life: 40, w: 12, h: 22, front: "ffcc66", back: "e67e22", pierce: 5, splash: 20, radius: 20, status: "melting", statusTime: 90, reload: 26, range: 340 },
        { id: "tankani4k", vi: "Pháo nặng, nổ lan", en: "Heavy shell, area blast", speed: 14, dmg: 120, life: 34, w: 10, h: 33, front: "e0ea87", back: "00ff4c", splash: 100, radius: 46, reload: 80, range: 440 },
        { id: "therdum", vi: "Shotgun tầm gần", en: "Close-range shotgun", speed: 9, dmg: 10, life: 14, w: 6, h: 10, front: "ffaa00", back: "ff5500", pellets: 3, spread: 16, reload: 38, range: 115 },
        { id: "lazash", vi: "Laser đỏ, sát thương vừa", en: "Red laser, steady damage", kind: "laser", dmg: 24, len: 270, w: 8, back: "ff4444", mid: "ff8866", spread: 3, reload: 30, range: 260 },
        { id: "forstarsilum", vi: "Kiếm sao xuyên 5 mục tiêu", en: "Star sword, pierces 5", speed: 10, dmg: 40, life: 45, w: 14, h: 34, front: "bbdefb", back: "90caf9", pierce: 5, reload: 50, range: 420 },
        { id: "hitekalum", vi: "Đạn điện kèm tia sét", en: "Shock shot + lightning", speed: 9, dmg: 18, life: 42, w: 8, h: 12, front: "ffffff", back: "ff5a5a", status: "shocked", statusTime: 60, lightning: 2, lightningLen: 6, lightningDmg: 12, reload: 26, range: 350 },
        { id: "reguilater", vi: "Laser sát thương ngẫu nhiên", en: "Laser, random damage", kind: "laser", len: 220, back: "ff3333", mid: "ff8080", spread: 3, reload: 28, range: 215, variants: [ { dmg: 20, w: 6 }, { dmg: 40, w: 8 }, { dmg: 75, w: 12 } ] },
        { id: "lavunder", vi: "Đạn photon nổ quang phổ", en: "Photon blast shots", speed: 8, dmg: 14, life: 40, w: 10, h: 10, front: "ffffff", back: "c7a0ff", splash: 46, radius: 38, homing: 0.03, reload: 26, range: 300 },
        { id: "blaw", vi: "Luân phiên đạn Băng / Hỏa", en: "Alternates Ice / Fire", cycle: true, speed: 6, life: 60, w: 9, h: 22, front: "ffffff", reload: 20, range: 340, variants: [ { dmg: 20, back: "6fa8ff", status: "freezing", statusTime: 120, pierce: 2 }, { dmg: 16, back: "ff5a2a", splash: 40, radius: 30, status: "burning", statusTime: 100 } ] },
        { id: "dtg-soldern", vi: "Chùm 3 đạn tản nhẹ", en: "3-shot light spread", speed: 8, dmg: 13, life: 40, w: 4, h: 10, front: "e0f7fa", back: "00bcd4", pellets: 3, spread: 9, reload: 22, range: 300 },
        { id: "indeniter", vi: "Đạn xuyên, nổ lan", en: "Piercing shot, splash", speed: 11, dmg: 20, life: 34, w: 10, h: 14, front: "00ffff", back: "0088ff", pierce: 6, splash: 24, radius: 28, reload: 26, range: 350 },
        { id: "rangtaturs", vi: "Đạn mảnh 7 hiệu ứng ngẫu nhiên", en: "Random 7-effect shards", speed: 7, dmg: 12, life: 45, w: 5, h: 12, front: "ffffff", splash: 14, radius: 24, pellets: 2, spread: 14, reload: 16, range: 290, variants: [ { back: "ffab40", status: "blasted", statusTime: 80 }, { back: "ff6d00", status: "burning", statusTime: 120 }, { back: "8fd0ff", status: "freezing", statusTime: 120 }, { back: "fff27a", status: "shocked", statusTime: 80 }, { back: "4d9aff", status: "wet", statusTime: 120 }, { back: "a6ff6a", status: "corroded", statusTime: 120 }, { back: "ffa726", status: "melting", statusTime: 90 } ] },
        { id: "tyber", vi: "3 tên lửa tầm nhiệt", en: "3 heat-seeking missiles", kind: "missile", speed: 4.5, dmg: 14, life: 90, w: 8, h: 12, front: "ffffff", back: "ffb74d", splash: 40, radius: 34, homing: 0.12, homingRange: 220, pellets: 3, spread: 12, reload: 60, range: 380 },
        { id: "endyr", vi: "Chùm 4 đạn năng lượng nổ", en: "4 energy blasts", speed: 6, dmg: 8, life: 55, w: 8, h: 8, front: "ffffff", back: "9be7ff", splash: 30, radius: 30, pellets: 4, spread: 24, reload: 55, range: 310 },
        { id: "sonicor", vi: "Sóng âm xuyên, đẩy lùi", en: "Sonic wave, knockback", speed: 10, dmg: 7, life: 30, w: 14, h: 4, front: "ffffff", back: "cfe9ff", pierce: 6, knock: 5, reload: 20, range: 280 },
        { id: "leolyr-spawner", vi: "Mảnh tinh thể tự dẫn", en: "Homing crystal shards", speed: 3.5, dmg: 16, life: 130, w: 7, h: 7, front: "ffffff", back: "c8ff7a", homing: 0.22, homingRange: 260, pellets: 2, spread: 40, reload: 28, range: 400 }
    ];

    // ---- Stat-boost picks (thay cho hệ thống nâng cấp lv của pháo) ----
    const STAT_PER_POINT = {
        hp:        0.06,   // +6% máu player / điểm
        armor:     1.2,    // +1.2 giáp player / điểm
        build:     0.08,   // +8% tốc độ xây / điểm
        dmg:       0.08,   // +8% sát thương pháo kĩ năng / điểm
        firerate:  0.07,   // +7% tốc độ bắn pháo kĩ năng / điểm
        range:     0.06,   // +6% tầm bắn pháo kĩ năng / điểm
        critChance:0.04,   // +4% tỉ lệ bạo kích / điểm
        critDmg:   0.15,   // +15% sát thương bạo kích / điểm
        shred:     1.0     // +1.0 giảm giáp mục tiêu / điểm
    };
    const STAT_SRC = [
        { id: "stat-hp", stat: "hp", icon: "hp", vi: "Máu", en: "Health",
            vid: "Tăng [green]6%[] máu tối đa của unit", end: "Increases unit max health by [green]6%[]" },
        { id: "stat-armor", stat: "armor", icon: "armor", vi: "Giáp", en: "Armor",
            vid: "Tăng [green]+1.2[] giáp cho unit", end: "Increases unit armor by [green]+1.2[]" },
        { id: "stat-build", stat: "build", icon: "build", vi: "Tốc độ xây", en: "Build Speed",
            vid: "Tăng [green]8%[] tốc độ xây dựng", end: "Increases build speed by [green]8%[]" },
        { id: "stat-dmg", stat: "dmg", icon: "dmg", vi: "Sát thương", en: "Damage",
            vid: "Tăng [green]8%[] sát thương các pháo kĩ năng", end: "Increases skill-cannon damage by [green]8%[]" },
        { id: "stat-firerate", stat: "firerate", icon: "firerate", vi: "Tốc độ bắn", en: "Fire Rate",
            vid: "Tăng [green]7%[] tốc độ bắn các pháo kĩ năng", end: "Increases skill-cannon fire rate by [green]7%[]" },
        { id: "stat-range", stat: "range", icon: "range", vi: "Phạm vi bắn", en: "Range",
            vid: "Tăng [green]6%[] tầm bắn các pháo kĩ năng", end: "Increases skill-cannon range by [green]6%[]" },
        { id: "stat-crit-chance", stat: "critChance", icon: "crit", vi: "Tỉ lệ bạo kích", en: "Crit Chance",
            vid: "Tăng [green]4%[] tỉ lệ bạo kích", end: "Increases crit chance by [green]4%[]" },
        { id: "stat-crit-dmg", stat: "critDmg", icon: "critdmg", vi: "Sát thương bạo kích", en: "Crit Damage",
            vid: "Tăng [green]15%[] sát thương khi bạo kích", end: "Increases crit damage bonus by [green]15%[]" },
        { id: "stat-shred", stat: "shred", icon: "shred", vi: "Giảm giáp mục tiêu", en: "Armor Shred",
            vid: "Giảm [green]1.0[] giáp của mục tiêu bị bắn trúng", end: "Reduces target armor by [green]1.0[] on hit" }
    ];
    function statDesc(s) { return isEn() ? s.end : s.vid; }
    function statName(s) { return isEn() ? s.en : s.vi; }

    const DESC = {
        "nucleytor": ["Bắn tích năng lượng, đầy 100% thì quá tải: tăng tốc bắn", "Charges up while firing; at 100% it overloads for big buffs"],
        "emperfum": ["Pháo hạng nặng bắn chậm, đạn cực mạnh", "Heavy slow-firing cannon, very strong shells"],
        "galaxvorram": ["Pháo khổng lồ bắn đạn hủy diệt, nhiều loại đạn", "Huge cannon with devastating multi-type shots"],
        "dor": ["Pháo đầu tay: bắn thường xen loạt chùm, đủ loạt vào Cuồng Bạo", "Starter gun: normal shots + burst volleys, enters Frenzy"],
        "flazerd": ["Laser khóa mục tiêu, bắn càng lâu càng mạnh", "Locked-on laser, grows stronger the longer it fires"],
        "erysidus": ["Tên lửa quét mục tiêu, đủ năng lượng tung chưởng Repulsor", "Scanning missiles; charged Repulsor blast (+200% vs air)"],
        "blixalum": ["Đạn điện từ nổ diện rộng, địch càng đông bắn càng nhanh", "Electromagnetic area blasts, fires faster vs crowds"],
        "heliyron": ["Mất máu càng nhiều càng mạnh, bắn mưa đạn truy đuổi", "Stronger as it loses HP; homing bullet rain"],
        "persefer": ["Gatling 6 nòng, bắn càng lâu càng nhanh", "6-barrel gatling that spins up the longer it fires"],
        "tankani2k": ["Bắn chính xác 1.115 sát thương, trúng liên tục thì sạc thêm", "Precise 1,115-dmg shots, charges up on consecutive hits"],
        "zoj": ["Pháo làm vội: bắn trúng nhận Copper cho lõi", "Cheap turret: each hit gives Copper to the core"],
        "reflecounum": ["Dựng khiên năng lượng chặn và phản đạn địch", "Energy shield that blocks and reflects enemy bullets"],
        "swordoder": ["Pháo kiếm: bắn đạn và chém, tận diệt mục tiêu dưới 15% máu", "Sword turret: shots + slashes, executes targets under 15% HP"],
        "buffetles": ["Dùng nhiều loại đạn, có thể bùng nổ sức mạnh trong chốc lát", "Multi-ammo turret with a short burst of power"],
        "maxitoner": ["Đạn thủy tinh tích tầng Nitoui, đủ tầng nổ diện rộng", "Glass shots stack Nitoui; full stacks explode in an area"],
        "xylaon": ["Bắn càng lâu càng nóng, tốc bắn tối đa +350%", "Heats up while firing, up to +350% fire rate"],
        "lyvervon": ["Cột thu lôi: hồ quang chính xác, bắn liên tục", "Lightning rod: precise arc strikes, constant fire"],
        "vendicum": ["Đầy năng lượng: +1000% sát thương, +250% tốc bắn", "At full charge: +1000% damage, +250% fire rate"],
        "drone-launcher": ["Triệu hồi drone chiến đấu, tự hủy sau 10 giây", "Launches combat drones that self-destruct after 10s"],
        "holyder": ["Tự sạc tăng sát thương, dựng khiên chặn đạn", "Self-charging heavy energy cannon with a bullet shield"],
        "plasanod": ["Hai tia laser quét liên tục, ngắm lâu gọi sét nổ", "Twin sweeping lasers; long aim calls blast lightning"],
        "tankani4k": ["Bản tối thượng Tankani, 1.673 sát thương, sạc đầy xuyên giáp", "Ultimate Tankani, 1,673 dmg, armor-piercing when fully charged"],
        "therdum": ["Shotgun tầm ngắn", "Close-range shotgun"],
        "lazash": ["Chiếu laser vào mục tiêu", "Beam laser on targets"],
        "forstarsilum": ["Đạn Starsword chính xác, bắn liên tục tích buff", "Accurate Starsword shots; sustained fire stacks buffs"],
        "hitekalum": ["Pháo điện từ cao cấp, sát thương rất lớn", "Advanced electromagnetic cannon, very high damage"],
        "reguilater": ["Ngắm tích sức rồi bắn laser mạnh, sát thương ngẫu nhiên", "Charges its aim, then fires a strong random-damage laser"],
        "lavunder": ["Laser Photon mạnh dần, đầy năng lượng nổ quang phổ lớn", "Photon laser that strengthens; big blast at full charge"],
        "blaw": ["Luân phiên đạn Băng / Hỏa, làm chậm và nổ diện rộng", "Alternates Ice / Fire shells: slow + area damage"],
        "dtg-soldern": ["Đạn đơn khi địch ở xa, shotgun khi địch tới gần", "Single shots at range, shotgun up close"],
        "indeniter": ["Tầm xa nổ lớn, tích tầng Bemod rồi phát nổ lan", "Long-range blasts; Bemod stacks detonate and spread"],
        "rangtaturs": ["Shotgun đạn mảnh 7 hiệu ứng ngẫu nhiên, có Cuồng Nộ", "Shotgun of random 7-effect shards, with Rage mode"],
        "tyber": ["Tên lửa 3 nòng, tự bắn tầm nhiệt và nổ xung kích", "3-barrel missiles, extra heat-seekers and shock pulses"],
        "endyr": ["Xả đạn năng lượng / laser diện rộng vào vùng mục tiêu", "Area energy and laser barrages on a target zone"],
        "sonicor": ["Sóng âm xuyên, đẩy lùi mục tiêu (bản rút gọn)", "Piercing sonic wave with knockback (simplified)"],
        "leolyr-spawner": ["Mảnh tinh thể tự dẫn (bản rút gọn)", "Homing crystal shards (simplified)"]
    };

    function descOf(def) {
        let d = DESC[def.name];
        if (d != null) return isEn() ? d[1] : d[0];
        return isEn() ? def.en : def.vi;
    }

    let skillPool = null;
    let playerStates = {};
    let dialogQueue = [];
    let dialogShowing = false;
    let listDialog = null;
    let savedByUuid = {};
    let meInfo = null;
    let currentOffer = null;

    const TileClass = (typeof Tile !== "undefined") ? Tile : Packages.mindustry.world.Tile;

    function isSurvivalActive() {
        return global.NexSurvival != null && global.NexSurvival.isActive();
    }

    function C(hex) { return Color.valueOf(hex); }

    function colorFromName(name) {
        let hash = 0;
        for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) & 0xffffff;
        let r = 0.35 + ((hash & 0xff) / 255) * 0.65;
        let g = 0.35 + (((hash >> 8) & 0xff) / 255) * 0.65;
        let b = 0.35 + (((hash >> 16) & 0xff) / 255) * 0.65;
        return new Color(r, g, b, 1);
    }

    function merge(a, b) {
        let o = {};
        for (let k in a) o[k] = a[k];
        for (let k in b) o[k] = b[k];
        return o;
    }

    function decorate(b, o) {
        if (o.pierce) { b.pierce = true; b.pierceCap = o.pierce; }
        if (o.homing) { b.homingPower = o.homing; b.homingRange = o.homingRange || 160; }
        if (o.splash) { b.splashDamage = o.splash; b.splashDamageRadius = o.radius || 30; }
        if (o.knock) b.knockback = o.knock;
        if (o.status) {
            let st = StatusEffects[o.status];
            if (st != null) { b.status = st; b.statusDuration = o.statusTime || 90; }
        }
        if (o.lightning) {
            b.lightning = o.lightning;
            b.lightningLength = o.lightningLen || 6;
            b.lightningDamage = o.lightningDmg || Math.max(1, (o.dmg || 10) * 0.6);
            b.lightningColor = C(o.back);
        }
        return b;
    }

    function mkBullet(o) {
        let b = (o.kind === "missile") ? new MissileBulletType(o.speed, o.dmg) : new BasicBulletType(o.speed, o.dmg);
        b.lifetime = o.life;
        b.width = o.w || 7;
        b.height = o.h || 10;
        b.frontColor = C(o.front || "ffffff");
        b.backColor = C(o.back);
        b.hitColor = C(o.back);
        b.trailColor = C(o.back);
        b.hitEffect = Fx.hitBulletColor;
        b.despawnEffect = Fx.hitBulletColor;
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        return decorate(b, o);
    }

    function mkLaser(o) {
        let b = new LaserBulletType(o.dmg);
        b.length = o.len;
        b.width = o.w || 10;
        try { b.colors = [C(o.back), C(o.mid || o.back), C("ffffff")]; } catch (err) {}
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        return decorate(b, o);
    }

    function mkLightning(o) {
        let b = new LightningBulletType();
        b.damage = o.dmg;
        b.lightningLength = o.len;
        b.lightningLengthRand = Math.floor(o.len / 4);
        b.lightningColor = C(o.back);
        b.shootEffect = Fx.none;
        b.smokeEffect = Fx.none;
        if (o.status) {
            let st = StatusEffects[o.status];
            if (st != null) { b.status = st; b.statusDuration = o.statusTime || 90; }
        }
        return b;
    }

    function prep(b) {
        try { b.init(); } catch (err) {}
        try { b.load(); } catch (err) {}
        try {
            if (b.backRegion !== undefined && b.backRegion == null) b.backRegion = Core.atlas.find("bullet-back");
            if (b.frontRegion !== undefined && b.frontRegion == null) b.frontRegion = Core.atlas.find("bullet");
        } catch (err) {}
        return b;
    }

    function buildBullet(o) {
        if (o.kind === "laser") return prep(mkLaser(o));
        if (o.kind === "lightning") return prep(mkLightning(o));
        return prep(mkBullet(o));
    }

    function fallbackBullet(src) {
        return prep(mkBullet({ speed: 8, dmg: src.dmg || 15, life: 36, w: 7, h: 10, front: "ffffff", back: src.back || "ffd37f" }));
    }

    function reachOf(o) {
        if (o.kind === "laser" || o.kind === "lightning") return Infinity;
        return o.speed * o.life * 0.95;
    }

    function ensureSimpleBullets(def) {
        if (def.bullets != null) return;
        let src = def.src;
        def.bullets = [];
        let reach = Infinity;
        let list = src.variants != null ? src.variants : [{}];
        try {
            for (let i = 0; i < list.length; i++) {
                let o = merge(src, list[i]);
                def.bullets.push(buildBullet(o));
                reach = Math.min(reach, reachOf(o));
            }
        } catch (err) {
            def.bullets = [fallbackBullet(src)];
            reach = 8 * 36 * 0.95;
        }
        def.range = Math.min(src.range, reach);
    }

    function buildDef(src, block) {
        return {
            name: src.id,
            src: src,
            block: block,
            vi: src.vi,
            en: src.en,
            color: colorFromName(src.id),
            reload: src.reload,
            pellets: src.pellets || 1,
            spread: src.spread != null ? src.spread : 4,
            cycle: src.cycle === true,
            range: src.range,
            bullets: null,
            isTurret: block instanceof Turret,
            isItem: block instanceof ItemTurret,
            ammoItem: undefined
        };
    }

    function buildSkillPool() {
        if (skillPool != null) return skillPool;
        skillPool = [];
        for (let i = 0; i < SKILL_SRC.length; i++) {
            let src = SKILL_SRC[i];
            try {
                let block = Vars.content.getByName(ContentType.block, "newex-" + src.id);
                if (block == null) continue;
                skillPool.push(buildDef(src, block));
            } catch (err) {}
        }
        return skillPool;
    }

    function setf(o, k, v) {
        try { o[k] = v; } catch (err) {}
    }

    function isDummyBullet(bt) {
        try { return bt.speed === 0 && bt.lifetime === 0 && bt.damage === 0; } catch (err) { return false; }
    }

    function ammoItemFor(block) {
        let best = null;
        try {
            let it = block.ammoTypes.keys();
            while (it.hasNext()) {
                let item = it.next();
                let bt = block.ammoTypes.get(item);
                if (bt == null) continue;
                if (best == null) best = item;
                if (!isDummyBullet(bt)) { best = item; break; }
            }
        } catch (err) {}
        return best;
    }

    function createVirtual(def, team, x, y) {
        let block = def.block;
        let b = block.newBuilding();
        b.create(block, team);
        let tile = new TileClass(0, 0);
        tile.build = b;
        b.init(tile, team, false, 0);
        b.set(x, y);
        return b;
    }

    function disposeVirtual(b) {
        if (b == null) return;
        try { if (b.sound != null) b.sound.stop(); } catch (err) {}
        try { if (b.tile != null && b.tile.build === b) b.tile.build = null; } catch (err) {}
    }

    function disposeAll(states) {
        try {
            for (let pid in states) {
                let st = states[pid];
                if (st == null || st.skills == null) continue;
                for (let i = 0; i < st.skills.length; i++) {
                    disposeVirtual(st.skills[i].virt);
                    st.skills[i].virt = null;
                }
            }
        } catch (err) {}
    }

    function feedAmmo(b, def) {
        if (!def.isItem || def.ammoItem == null) return;
        let max = def.block.maxAmmo;
        let guard = 0;
        while (b.totalAmmo < max * 0.7 && guard < 6) {
            guard++;
            let before = b.totalAmmo;
            b.handleItem(b, def.ammoItem);
            if (b.totalAmmo <= before) break;
        }
    }

    function autoAim(b, sk, unit) {
        if (b.hasTargetSet === undefined || b.selectingTarget === undefined) return;
        sk.hookT -= Time.delta;
        if (sk.hookT > 0) return;
        sk.hookT = 10;
        let r = 400;
        try { r = b.range(); } catch (err) {}
        let t = Units.closestEnemy(unit.team, sk.px, sk.py, r, new Boolf({
            get: function(e) { return e != null && !e.dead; }
        }));
        if (t != null) {
            b.targetX = t.x;
            b.targetY = t.y;
            b.hasTargetSet = true;
        } else {
            b.hasTargetSet = false;
        }
    }

    function driveReal(sk, unit, tier) {
        let def = sk.def;
        if (sk.virt == null) {
            if (def.ammoItem === undefined) def.ammoItem = ammoItemFor(def.block);
            sk.virt = createVirtual(def, unit.team, sk.px, sk.py);
            sk.noAmmo = 0;
            sk.zeroEff = 0;
        }
        let b = sk.virt;
        b.set(sk.px, sk.py);

        setf(b, "enabled", true);
        setf(b, "wasVisible", true);
        try { if (b.power != null) b.power.status = 1; } catch (err) {}

        let fireRateMult = statMult(sk.ownerState, "firerate");
        setf(b, "timeScale", fireRateMult);
        setf(b, "timeScaleDuration", 5);

        if (b.limitCheck !== undefined) b.limitCheck = -1000000;
        if (b.shotsFired !== undefined && b.shotsFired > 50) b.shotsFired = 0;

        feedAmmo(b, def);
        autoAim(b, sk, unit);

        if (sk.forceEff) {
            setf(b, "efficiency", 1);
            setf(b, "optionalEfficiency", 1);
            setf(b, "potentialEfficiency", 1);
            b.updateTile();
        } else {
            b.update();
            let eff = 1;
            try { eff = b.efficiency; } catch (err) {}
            if (b.target != null && !(eff > 0.001)) {
                sk.zeroEff += Time.delta;
                if (sk.zeroEff > 60) {
                    sk.forceEff = true;
                }
            } else {
                sk.zeroEff = 0;
            }
        }

        if (b.health < def.block.health * 0.01) b.health = def.block.health;

        if (b.target != null) {
            sk.aim = b.rotation;
            sk.noTargetTicks = 0;
        }
        try { sk.rangeNow = b.range(); } catch (err) {}

        if (!b.isValid()) {
            disposeVirtual(b);
            sk.virt = null;
            sk.deaths++;
            if (sk.deaths >= 3) throw new Error("building destroyed");
            return;
        }

        if (def.isItem) {
            if (!b.hasAmmo()) {
                sk.noAmmo += Time.delta;
                if (sk.noAmmo > 180) throw new Error("no ammo");
            } else {
                sk.noAmmo = 0;
            }
        }
    }

    let noBodyDrawer = undefined;
    function getNoBodyDrawer() {
        if (noBodyDrawer !== undefined) return noBodyDrawer;
        noBodyDrawer = null;
        try { noBodyDrawer = new Packages.mindustry.world.draw.DrawMulti(); } catch (err) {}
        if (noBodyDrawer == null) {
            try {
                noBodyDrawer = extend(Packages.mindustry.world.draw.DrawBlock, {
                    draw: function(build) {}
                });
            } catch (err) {}
        }
        return noBodyDrawer;
    }

    function drawReal(sk) {
        let b = sk.virt;
        if (b == null || sk.drawErr >= 3) return;
        let drawer = getNoBodyDrawer();
        if (drawer == null) return;
        let blk = sk.def.block;
        let old = null;
        let swapped = false;
        try {
            old = blk.drawer;
            blk.drawer = drawer;
            swapped = true;
            b.set(sk.px, sk.py);
            b.draw();
        } catch (err) {
            sk.drawErr++;
        }
        if (swapped) {
            try { blk.drawer = old; } catch (err) {}
        }
        Draw.reset();
    }

    function switchToSimple(sk, why) {
        sk.why = String(why).substring(0, 70);
        sk.retryT = 600;
        try { Log.warn("[NexSkills] " + sk.name + " -> simple: " + sk.why); } catch (e0) {}
        sk.mode = "simple";
        disposeVirtual(sk.virt);
        sk.virt = null;
        sk.timer = 0;
        ensureSimpleBullets(sk.def);
    }

    // Khóa theo id thực thể người chơi (giống nhau trên mọi máy). Server giữ thêm bản lưu theo uuid
    // để người chơi thoát rồi vào lại vẫn còn exp/pháo.
    function getPlayerId(player) { return String(player.id); }

    function uuidOf(player) {
        try { return String(player.uuid()); } catch (err) {}
        return "id:" + player.id;
    }

    function newState() {
        return {
            level: 0, exp: 0, skills: [], maxRange: 0, rerollPts: 0,
            stats: { hp: 0, armor: 0, build: 0, dmg: 0, firerate: 0, range: 0, critChance: 0, critDmg: 0, shred: 0 },
            baseMaxHealth: 0, pending: 0, offer: null, offerOut: false, dirty: true, lastMe: ""
        };
    }

    function getState(player) {
        let pid = getPlayerId(player);
        let s = playerStates[pid];
        if (s == null) {
            if (!Vars.net.client()) {
                let u = uuidOf(player);
                s = savedByUuid[u];
                if (s == null) { s = newState(); savedByUuid[u] = s; }
                s.offerOut = false;
                s.dirty = true;
            } else {
                s = newState();
            }
            playerStates[pid] = s;
        }
        return s;
    }

    function expForKill(u) {
        let base = 6 + Math.floor(Vars.state.wave / 8);
        let mult = getExpPercent() / 100;
        if (isExpByHp() && u != null) {
            let hp = 0;
            try { hp = u.maxHealth; } catch (err) {}
            if (hp > 0) mult *= Math.max(1, Math.min(HP_EXP_MAX_MULT, Math.sqrt(hp / HP_EXP_REF)));
        }
        return base * mult;
    }

    function findSkill(state, name) {
        for (let i = 0; i < state.skills.length; i++) {
            if (state.skills[i].name === name) return state.skills[i];
        }
        return null;
    }

    function randomStatPick() {
        let s = STAT_SRC[Math.floor(Math.random() * STAT_SRC.length)];
        return { name: s.id, stat: s.stat, vi: s.vi, en: s.en, srcStat: s };
    }

    // Không còn hệ thống tier/lv cho pháo kĩ năng: mỗi pháo chỉ chọn được 1 lần.
    // Sau khi đã sở hữu hết pháo (hoặc để lấp chỗ trống), các lựa chọn còn lại
    // là các nút CỘNG CHỈ SỐ (máu/giáp/tốc xây/sát thương/tốc bắn/tầm bắn/crit/giảm giáp).
    function rollSkillChoices(state) {
        let pool = buildSkillPool();
        let open = [];
        for (let i = 0; i < pool.length; i++) {
            let d = pool[i];
            if (findSkill(state, d.name) == null) open.push(d);
        }

        let want = SKILL_CHOICE_COUNT;
        let picks = [];
        let cannonWant = Math.min(want, open.length);
        let tries = 0;
        while (picks.length < cannonWant && tries < 60) {
            tries++;
            let cand = open[Math.floor(Math.random() * open.length)];
            if (picks.indexOf(cand) === -1) picks.push(cand);
        }
        while (picks.length < want) {
            picks.push(randomStatPick());
        }
        // trộn ngẫu nhiên vị trí hiển thị
        for (let i = picks.length - 1; i > 0; i--) {
            let j = Math.floor(Math.random() * (i + 1));
            let tmp = picks[i]; picks[i] = picks[j]; picks[j] = tmp;
        }
        return picks;
    }

    // id dùng để truyền qua mạng: pháo dùng def.name, chỉ số dùng "stat:<key>"
    function pickId(c) {
        return c.stat ? ("stat:" + c.stat) : c.name;
    }

    function resolvePickById(id) {
        if (id == null) return null;
        if (id.indexOf("stat:") === 0) {
            let key = id.substring(5);
            for (let i = 0; i < STAT_SRC.length; i++) {
                if (STAT_SRC[i].stat === key) {
                    let s = STAT_SRC[i];
                    return { name: s.id, stat: s.stat, vi: s.vi, en: s.en, srcStat: s };
                }
            }
            return null;
        }
        let pool = buildSkillPool();
        for (let i = 0; i < pool.length; i++) {
            if (pool[i].name === id) return pool[i];
        }
        return null;
    }

    function applyStatPick(player, state, pick) {
        let k = pick.stat;
        if (state.stats[k] === undefined) state.stats[k] = 0;
        state.stats[k] += 1;
        if (global.NexNet != null && player != null) {
            global.NexNet.toastTo(player, (isEn() ? "[accent]Stat: " : "[accent]Chỉ số: ") + statName(pick.srcStat), 2.5);
        }
    }

    // Chỉ chạy trên SERVER (host / dedicated). Client chỉ gửi yêu cầu chọn.
    function applySkill(player, state, def) {
        if (def.stat) {
            applyStatPick(player, state, def);
        } else {
            if (findSkill(state, def.name) != null) return;
            addSkill(state, def);
            if (global.NexNet != null && player != null) {
                global.NexNet.toastTo(player, (isEn() ? "[accent]Skill: " : "[accent]Kĩ năng: ") + def.block.localizedName, 2.5);
            }
        }
        state.dirty = true;
    }

    function addSkill(state, def) {
        let real = REAL_TURRETS && def.isTurret;
        if (!real) ensureSimpleBullets(def);
        state.skills.push({
            name: def.name, def: def, tier: 1,
            mode: real ? "real" : "simple",
            timer: Math.random() * def.reload, shot: 0,
            inited: false, px: 0, py: 0, vx: 0, vy: 0,
            offA: 0, offR: 30, wanderT: 0, aim: 0, aimDraw: 0, trail: [], trailT: 0, noTargetTicks: 999,
            virt: null, errs: 0, errRun: 0, deaths: 0, noAmmo: 0, hookT: 0, rangeNow: 0, zeroEff: 0, forceEff: false, drawErr: 0,
            why: "", retryLeft: 2, retryT: 0,
            ownerState: state
        });
    }

    // ---- Hộp thoại chọn thẻ (dùng chung cho host và client) ----
    function showSkillDialog(choices, info, onPick, onReroll, onAbort) {
        let dialog = new BaseDialog(isEn() ? "Level Up!" : "Lên Cấp!");
        dialog.cont.clear();
        dialog.cont.add(isEn() ? "Choose one - Level " + info.level + "/" + info.maxLevel : "Chọn một - Cấp " + info.level + "/" + info.maxLevel).pad(8).row();

        let row = new Table();
        let finished = false;
        let handle = { hide: function() { if (finished) return; finished = true; dialog.hide(); } };

        for (let i = 0; i < choices.length; i++) {
            let c = choices[i];
            let card = new Table(Styles.black6);
            card.margin(8);

            if (c.stat) {
                card.add(isEn() ? "[accent]STAT" : "[accent]CHỈ SỐ").pad(4).row();
                let nameLabel = card.add(statName(c.srcStat)).wrap().width(150).pad(2).get();
                nameLabel.setAlignment(Align.center);
                card.row();
                let noteLabel = card.add("[lightgray]" + statDesc(c.srcStat) + "[]").wrap().width(150).pad(2).get();
                noteLabel.setAlignment(Align.center);
                noteLabel.setFontScale(0.85);
                card.row();
                let have = (info.stats != null && info.stats[c.stat]) || 0;
                card.add(isEn() ? "[accent]Owned: " + have : "[accent]Đã có: " + have).pad(2).row();
            } else {
                card.add(new Image(c.block.uiIcon)).size(40).pad(4).row();
                let nameLabel = card.add(c.block.localizedName).wrap().width(150).pad(2).get();
                nameLabel.setAlignment(Align.center);
                card.row();
                let noteLabel = card.add("[lightgray]" + descOf(c) + "[]").wrap().width(150).pad(2).get();
                noteLabel.setAlignment(Align.center);
                noteLabel.setFontScale(0.85);
                card.row();
                card.add(isEn() ? "[accent]New Cannon" : "[accent]Pháo mới").pad(2).row();
            }

            card.button(isEn() ? "Choose" : "Chọn", new Runnable({
                run: function() {
                    if (finished) return;
                    finished = true;
                    dialog.hide();
                    onPick(c);
                }
            })).size(130, 42).pad(4);
            row.add(card).size(180, 280).pad(6);
        }

        dialog.cont.add(row).row();

        let rerollRow = new Table();
        rerollRow.add((isEn() ? "Reroll points: " : "Điểm đổi: ") + info.reroll).pad(6);
        rerollRow.button(isEn() ? "Reroll choices" : "Đổi lựa chọn", new Runnable({
            run: function() {
                if (finished || info.reroll <= 0) return;
                finished = true;
                dialog.hide();
                if (onReroll) onReroll();
            }
        })).size(170, 42).pad(6);
        dialog.cont.add(rerollRow).row();

        dialog.hidden(new Runnable({
            run: function() {
                if (finished) return;
                finished = true;
                if (onAbort) onAbort();
            }
        }));
        dialog.show();
        return handle;
    }

    // ---- SERVER là nơi duy nhất giữ exp / cấp / lựa chọn của từng người chơi ----
    function showOfferLocal(player, st) {
        if (st.offer == null) return;
        showSkillDialog(st.offer,
            { level: st.level, maxLevel: getMaxLevel(), reroll: st.rerollPts, stats: st.stats },
            function(c) { resolvePick(player, st, c); },
            function() { doReroll(player, st); },
            function() { st.offerOut = false; });
    }

    function serveOffer(player, st) {
        if (st.offer == null) {
            if (st.pending <= 0) return;
            st.offer = rollSkillChoices(st);
            if (st.offer.length === 0) { st.pending = 0; st.offer = null; return; }
        }
        st.offerOut = true;
        if (!Vars.headless && player === Vars.player) {
            showOfferLocal(player, st);
        } else if (player.con != null && global.NexNet != null) {
            let ids = [];
            for (let i = 0; i < st.offer.length; i++) ids.push(pickId(st.offer[i]));
            global.NexNet.sendTo(player, "newex-survival-offer",
                ids.join(",") + "~" + st.rerollPts + "~" + st.level + "~" + getMaxLevel());
        } else {
            resolvePick(player, st, st.offer[Math.floor(Math.random() * st.offer.length)]);
        }
    }

    function resolvePick(player, st, c) {
        applySkill(player, st, c);
        st.pending = Math.max(0, st.pending - 1);
        st.offer = null;
        st.offerOut = false;
        st.dirty = true;
        if (st.pending > 0) serveOffer(player, st);
    }

    function doReroll(player, st) {
        if (st.offer == null || st.rerollPts <= 0) { st.offerOut = false; return; }
        st.rerollPts--;
        st.offer = rollSkillChoices(st);
        serveOffer(player, st);
    }

    function onLevelUp(player, state) {
        state.rerollPts = (state.rerollPts || 0) + 1;
        state.pending++;
        if (!state.offerOut) serveOffer(player, state);
    }

    function grantExp(player, amount) {
        if (player == null) return;
        let state = getState(player);
        let maxLv = getMaxLevel();
        if (state.level >= maxLv) return;
        state.exp += amount;
        while (state.level < maxLv && state.exp >= levelCost(state.level + 1)) {
            state.exp -= levelCost(state.level + 1);
            state.level++;
            onLevelUp(player, state);
        }
    }

    function sendMe(pl, st) {
        let maxLv = getMaxLevel();
        let need = st.level < maxLv ? levelCost(st.level + 1) : 0;
        let s = st.level + "|" + Math.floor(st.exp) + "|" + need + "|" + maxLv + "|" + (st.rerollPts || 0);
        if (s === st.lastMe) return;
        st.lastMe = s;
        global.NexNet.sendTo(pl, "newex-survival-me", s);
    }

    if (global.NexNet != null) {
        // --- server nhận yêu cầu từ client ---
        global.NexNet.on("newex-survival-pick", function(player, payload) {
            if (player == null || Vars.net.client()) return;
            let st = getState(player);
            if (st.offer == null) return;
            let c = null;
            for (let i = 0; i < st.offer.length; i++) {
                if (pickId(st.offer[i]) === payload) { c = st.offer[i]; break; }
            }
            if (c == null) { st.offerOut = false; return; }
            resolvePick(player, st, c);
        }, true, 50);

        global.NexNet.on("newex-survival-reroll", function(player, payload) {
            if (player == null || Vars.net.client()) return;
            doReroll(player, getState(player));
        }, true, 50);

        global.NexNet.on("newex-survival-reopen", function(player, payload) {
            if (player == null || Vars.net.client()) return;
            getState(player).offerOut = false;
        }, true, 200);

        // --- client nhận từ server ---
        global.NexNet.onClient("newex-survival-me", function(payload) {
            let a = String(payload).split("|");
            meInfo = {
                level: parseInt(a[0]) || 0, exp: parseInt(a[1]) || 0, need: parseInt(a[2]) || 0,
                maxLevel: parseInt(a[3]) || 0, reroll: parseInt(a[4]) || 0
            };
        });

        global.NexNet.onClient("newex-survival-offer", function(payload) {
            if (Vars.headless || Vars.player == null) return;
            let seg = String(payload).split("~");
            let ids = seg[0].split(",");
            let choices = [];
            for (let i = 0; i < ids.length; i++) {
                let d = resolvePickById(ids[i]);
                if (d != null) choices.push(d);
            }
            if (choices.length === 0) return;
            if (currentOffer != null) { currentOffer.hide(); currentOffer = null; }
            let mine = playerStates[getPlayerId(Vars.player)];
            currentOffer = showSkillDialog(choices,
                { level: parseInt(seg[2]) || 0, maxLevel: parseInt(seg[3]) || 0, reroll: parseInt(seg[1]) || 0, stats: mine != null ? mine.stats : null },
                function(c) { currentOffer = null; global.NexNet.send("newex-survival-pick", pickId(c)); },
                function() { currentOffer = null; global.NexNet.send("newex-survival-reroll", ""); },
                function() { currentOffer = null; global.NexNet.send("newex-survival-reopen", ""); });
        });
    }

    // Chỉ server cộng exp: mỗi người chơi ở gần xác quái đều nhận exp RIÊNG vào state của mình.
    Events.on(UnitDestroyEvent, new Cons({
        get: function(e) {
            if (Vars.net.client() || !isSurvivalActive() || Vars.state.gameOver) return;
            let u = e.unit;
            if (u == null || u.team != Vars.state.rules.waveTeam) return;
            let gained = expForKill(u);
            let rx = u.x, ry = u.y;

            Groups.player.each(new Cons({
                get: function(pl) {
                    let pu = pl.unit();
                    if (pu == null || !pu.isValid() || pu.dead) return;
                    let st = playerStates[getPlayerId(pl)];
                    let rad = Math.min(MAX_CREDIT_RADIUS, Math.max(KILL_CREDIT_RADIUS, st != null ? st.maxRange : 0));
                    let dx = pu.x - rx, dy = pu.y - ry;
                    if (dx * dx + dy * dy <= rad * rad) grantExp(pl, gained);
                }
            }));
        }
    }));

    Events.on(WorldLoadEvent, new Cons({
        get: function(e) {
            disposeAll(playerStates);
            playerStates = {};
            savedByUuid = {};
            meInfo = null;
            currentOffer = null;
            rosterT = 0;
            serveT = 0;
            if (listDialog != null) { try { listDialog.hide(); } catch (err) {} listDialog = null; }
        }
    }));

    function statMult(state, key) {
        if (state == null || state.stats == null) return 1;
        let pts = state.stats[key] || 0;
        let per = STAT_PER_POINT[key] || 0;
        return 1 + pts * per;
    }

    // Áp các chỉ số (máu/giáp/tốc xây) lên chính unit người chơi mỗi tick.
    function applyPlayerStats(pl, unit, state) {
        if (state == null) return;
        try {
            if (state.baseMaxHealth <= 0) state.baseMaxHealth = unit.maxHealth;
            let hpMult = statMult(state, "hp");
            let newMax = state.baseMaxHealth * hpMult;
            if (unit.maxHealth < newMax) {
                let diff = newMax - unit.maxHealth;
                unit.maxHealth = newMax;
                unit.health += diff;
            } else if (unit.maxHealth !== newMax) {
                unit.maxHealth = newMax;
            }
        } catch (err) {}
        try {
            if (state.baseArmor === undefined) state.baseArmor = unit.armor || 0;
            unit.armor = state.baseArmor + (state.stats.armor || 0) * STAT_PER_POINT.armor;
        } catch (err) {}
        try {
            unit.buildSpeedMultiplier = statMult(state, "build");
        } catch (err) {}
    }

    function pickBullet(def, sk) {
        let n = def.bullets.length;
        if (n === 1) return def.bullets[0];
        if (def.cycle) return def.bullets[sk.shot % n];
        return def.bullets[Math.floor(Math.random() * n)];
    }

    function fireSkill(unit, sk, ox, oy, angle, target) {
        let def = sk.def;
        let tier = Math.min(MAX_TIER_SHOTS, sk.tier);
        let count = Math.min(MAX_BULLETS_PER_VOLLEY, def.pellets * tier);
        let state = sk.ownerState;
        let dmgMult = statMult(state, "dmg");
        let critChance = state != null ? (state.stats.critChance || 0) * STAT_PER_POINT.critChance : 0;
        let critMult = 1 + (state != null ? (state.stats.critDmg || 0) * STAT_PER_POINT.critDmg : 0);
        let shred = state != null ? (state.stats.shred || 0) * STAT_PER_POINT.shred : 0;
        try {
            for (let s = 0; s < count; s++) {
                let b = pickBullet(def, sk);
                let bullet = b.create(unit, unit.team, ox, oy, angle + Mathf.range(def.spread));
                try {
                    if (bullet != null && bullet.damage !== undefined && dmgMult !== 1) {
                        bullet.damage = bullet.damage * dmgMult;
                    }
                    if (bullet != null && critChance > 0 && Math.random() < critChance && bullet.damage !== undefined) {
                        bullet.damage = bullet.damage * critMult;
                    }
                } catch (err2) {}
            }
            if (shred > 0 && target != null) {
                try { target.armor = Math.max(0, target.armor - shred); } catch (err3) {}
            }
            sk.shot++;
        } catch (err) {
            def.bullets = [fallbackBullet(def.src != null ? def.src : {})];
            def.range = Math.min(def.range, 280);
        }
    }

    function updateSimple(unit, sk) {
        sk.timer -= Time.delta;
        if (sk.timer > 0) return;
        let state = sk.ownerState;
        let rangeMult = statMult(state, "range");
        let range = sk.def.range * rangeMult;
        let target = Units.closestEnemy(unit.team, sk.px, sk.py, range, new Boolf({
            get: function(t) { return t != null && !t.dead; }
        }));
        if (target != null) {
            let angle = Angles.angle(sk.px, sk.py, target.x, target.y);
            sk.aim = angle;
            sk.noTargetTicks = 0;
            fireSkill(unit, sk, sk.px, sk.py, angle, target);
            let fireRateMult = statMult(state, "firerate");
            sk.timer = Math.max(MIN_RELOAD, (sk.def.reload * (1 - (sk.tier - 1) * 0.12)) / fireRateMult);
        } else {
            sk.timer = 12;
        }
    }

    function pickWander(sk) {
        sk.offA = Math.random() * 360;
        sk.offR = DRONE_R_MIN + Math.random() * (DRONE_R_MAX - DRONE_R_MIN);
        sk.wanderT = DRONE_WANDER_MIN + Math.random() * (DRONE_WANDER_MAX - DRONE_WANDER_MIN);
    }

    function initDrone(sk, unit) {
        let a = Math.random() * 360;
        sk.px = unit.x + Mathf.cosDeg(a) * 14;
        sk.py = unit.y + Mathf.sinDeg(a) * 14;
        sk.vx = 0;
        sk.vy = 0;
        sk.aim = a;
        sk.aimDraw = a;
        pickWander(sk);
        sk.inited = true;
    }

    function moveDrone(sk, unit, all) {
        let dt = Time.delta;
        sk.wanderT -= dt;
        sk.noTargetTicks += dt;
        if (sk.wanderT <= 0) pickWander(sk);

        let tx = unit.x + Mathf.cosDeg(sk.offA) * sk.offR + unit.vel.x * 10;
        let ty = unit.y + Mathf.sinDeg(sk.offA) * sk.offR + unit.vel.y * 10;
        let dx = tx - sk.px;
        let dy = ty - sk.py;

        let pdx = sk.px - unit.x;
        let pdy = sk.py - unit.y;
        let pd = Math.sqrt(pdx * pdx + pdy * pdy);
        if (pd > DRONE_TELEPORT) {
            initDrone(sk, unit);
            return;
        }

        let k = DRONE_SPRING * (pd > DRONE_LEASH ? 3 : 1);
        sk.vx += dx * k * dt;
        sk.vy += dy * k * dt;

        sk.vx += (Math.random() - 0.5) * 0.07 * dt;
        sk.vy += (Math.random() - 0.5) * 0.07 * dt;

        for (let i = 0; i < all.length; i++) {
            let o = all[i];
            if (o === sk || !o.inited) continue;
            let sx = sk.px - o.px;
            let sy = sk.py - o.py;
            let sd = Math.sqrt(sx * sx + sy * sy);
            if (sd < DRONE_SEPARATION) {
                if (sd < 0.01) { sx = Math.random() - 0.5; sy = Math.random() - 0.5; sd = 0.7; }
                let push = (DRONE_SEPARATION - sd) * 0.03 * dt;
                sk.vx += (sx / sd) * push;
                sk.vy += (sy / sd) * push;
            }
        }

        if (pd < 14) {
            let nx = pd > 0.01 ? pdx / pd : 1;
            let ny = pd > 0.01 ? pdy / pd : 0;
            let push = (14 - pd) * 0.05 * dt;
            sk.vx += nx * push;
            sk.vy += ny * push;
        }

        let drag = Math.pow(DRONE_DRAG, dt);
        sk.vx *= drag;
        sk.vy *= drag;

        let sp = Math.sqrt(sk.vx * sk.vx + sk.vy * sk.vy);
        let maxSp = DRONE_MAX_SPEED * (pd > DRONE_LEASH ? 2.2 : 1);
        if (sp > maxSp) {
            sk.vx *= maxSp / sp;
            sk.vy *= maxSp / sp;
        }

        sk.px += sk.vx * dt;
        sk.py += sk.vy * dt;

        if (sp > 0.6 && sk.noTargetTicks > 20) sk.aim = Math.atan2(sk.vy, sk.vx) * 180 / Math.PI;

        sk.trailT -= dt;
        if (sk.trailT <= 0) {
            sk.trailT = 3;
            sk.trail.push({ x: sk.px, y: sk.py });
            if (sk.trail.length > 7) sk.trail.shift();
        }
    }

    function updateOrbiters(player, unit, state) {
        let skills = state.skills;
        let maxRange = 0;
        for (let i = 0; i < skills.length; i++) {
            let sk = skills[i];
            if (!sk.inited) initDrone(sk, unit);
            moveDrone(sk, unit, skills);

            if (sk.mode === "simple" && REAL_TURRETS && sk.def.isTurret && sk.retryLeft > 0) {
                sk.retryT -= Time.delta;
                if (sk.retryT <= 0) {
                    sk.retryLeft--;
                    sk.mode = "real";
                    sk.errs = 0; sk.errRun = 0; sk.deaths = 0; sk.noAmmo = 0; sk.zeroEff = 0; sk.forceEff = false; sk.drawErr = 0;
                }
            }

            if (sk.mode === "real") {
                try {
                    driveReal(sk, unit, Math.min(MAX_TIER_SHOTS, sk.tier));
                    sk.errRun = 0;
                    if (sk.rangeNow > maxRange) maxRange = sk.rangeNow;
                } catch (err) {
                    sk.errs++;
                    sk.errRun++;
                    if (sk.errRun >= 30 || sk.errs >= 400 || String(err).indexOf("destroyed") >= 0) {
                        switchToSimple(sk, String(err));
                    }
                }
            } else {
                updateSimple(unit, sk);
                if (sk.def.range > maxRange) maxRange = sk.def.range;
            }
        }
        state.maxRange = maxRange;
    }

    // ---- Đồng bộ danh sách pháo + chỉ số của MỌI người chơi tới mọi máy ----
    // Mỗi máy tự mô phỏng pháo của tất cả người chơi (như turret thường của Mindustry): server tính
    // sát thương thật, client thấy đạn/hiệu ứng. Exp/cấp chỉ nằm trên server.
    const STAT_KEYS = ["hp", "armor", "build", "dmg", "firerate", "range", "critChance", "critDmg", "shred"];
    let rosterT = 0;
    let serveT = 0;
    let purgeT = 0;
    let scaledIds = {};
    let scaleClean = 0;

    function buildRoster() {
        let parts = [];
        Groups.player.each(new Cons({
            get: function(pl) {
                let st = playerStates[getPlayerId(pl)];
                if (st == null) return;
                let sv = [];
                let any = false;
                for (let i = 0; i < STAT_KEYS.length; i++) {
                    let v = st.stats[STAT_KEYS[i]] || 0;
                    if (v > 0) any = true;
                    sv.push(v);
                }
                let names = [];
                for (let i = 0; i < st.skills.length; i++) names.push(st.skills[i].name);
                if (!any && names.length === 0) return;
                parts.push(getPlayerId(pl) + "^" + sv.join(",") + "^" + names.join(","));
            }
        }));
        return parts.join("|");
    }

    if (global.NexNet != null) {
        global.NexNet.onBroadcast("newex-survival-roster", function(payload) {
            if (!Vars.net.client() || payload == null || payload.length === 0) return;
            let parts = payload.split("|");
            for (let i = 0; i < parts.length; i++) {
                let seg = parts[i].split("^");
                if (seg.length < 3) continue;
                let pl = Groups.player.getByID(parseInt(seg[0]));
                if (pl == null) continue;
                let st = getState(pl);
                let sv = seg[1].split(",");
                for (let k = 0; k < STAT_KEYS.length; k++) st.stats[STAT_KEYS[k]] = parseFloat(sv[k]) || 0;
                let names = seg[2].length > 0 ? seg[2].split(",") : [];
                for (let j = st.skills.length - 1; j >= 0; j--) {
                    if (names.indexOf(st.skills[j].name) < 0) {
                        disposeVirtual(st.skills[j].virt);
                        st.skills.splice(j, 1);
                    }
                }
                for (let j = 0; j < names.length; j++) {
                    if (findSkill(st, names[j]) != null) continue;
                    let d = resolvePickById(names[j]);
                    if (d != null && !d.stat) addSkill(st, d);
                }
            }
        });
    }

    function purgeStates() {
        for (let pid in playerStates) {
            if (Groups.player.getByID(parseInt(pid)) == null) {
                let st = playerStates[pid];
                if (st != null) {
                    for (let i = 0; i < st.skills.length; i++) {
                        disposeVirtual(st.skills[i].virt);
                        st.skills[i].virt = null;
                    }
                }
                delete playerStates[pid];
            }
        }
    }

    function serverTick() {
        serveT -= Time.delta;
        if (serveT <= 0) {
            serveT = 30;
            Groups.player.each(new Cons({
                get: function(pl) {
                    let st = playerStates[getPlayerId(pl)];
                    if (st == null) return;
                    if (st.pending > 0 && !st.offerOut) serveOffer(pl, st);
                    if (global.NexNet != null && pl !== Vars.player && pl.con != null) sendMe(pl, st);
                }
            }));
        }
        if (global.NexNet == null || !Vars.net.server()) return;
        rosterT -= Time.delta;
        let dirty = false;
        for (let pid in playerStates) {
            if (playerStates[pid].dirty) { dirty = true; playerStates[pid].dirty = false; }
        }
        if (dirty || rosterT <= 0) {
            rosterT = 180;
            global.NexNet.broadcast("newex-survival-roster", buildRoster());
        }
    }

    // Pháo thật (virtual turret) tự tạo đạn nên các chỉ số sát thương/bạo kích/giảm giáp phải áp lên đạn mới sinh.
    function scaleRealBullets(list) {
        if (list.length === 0) return;
        let need = false;
        for (let i = 0; i < list.length; i++) {
            let s = list[i].st.stats;
            if ((s.dmg || 0) > 0 || (s.critChance || 0) > 0 || (s.shred || 0) > 0) { need = true; break; }
        }
        if (!need) return;
        Groups.bullet.each(new Cons({
            get: function(bu) {
                if (bu.time > 4 || scaledIds[bu.id]) return;
                let o = bu.owner;
                for (let i = 0; i < list.length; i++) {
                    if (list[i].b !== o) continue;
                    scaledIds[bu.id] = 1;
                    let st = list[i].st;
                    try {
                        let dm = statMult(st, "dmg");
                        if (dm !== 1) bu.damage = bu.damage * dm;
                        let cc = (st.stats.critChance || 0) * STAT_PER_POINT.critChance;
                        if (cc > 0 && Math.random() < cc) bu.damage = bu.damage * (1 + (st.stats.critDmg || 0) * STAT_PER_POINT.critDmg);
                        let shred = (st.stats.shred || 0) * STAT_PER_POINT.shred;
                        let tg = list[i].b.target;
                        if (shred > 0 && tg != null && tg.armor !== undefined) tg.armor = Math.max(0, tg.armor - shred);
                    } catch (err) {}
                    break;
                }
            }
        }));
        scaleClean -= Time.delta;
        if (scaleClean <= 0) { scaledIds = {}; scaleClean = 600; }
    }

    Events.run(Trigger.update, new Runnable({
        run: function() {
            if (!isSurvivalActive() || Vars.state.gameOver || Vars.state.isPaused()) return;
            let server = !Vars.net.client();
            let virts = [];
            Groups.player.each(new Cons({
                get: function(pl) {
                    let u = pl.unit();
                    if (u == null || !u.isValid() || u.dead) return;
                    let state = server ? getState(pl) : playerStates[getPlayerId(pl)];
                    if (state == null) return;
                    if (server || pl === Vars.player) applyPlayerStats(pl, u, state);
                    if (state.skills.length === 0) return;
                    updateOrbiters(pl, u, state);
                    for (let i = 0; i < state.skills.length; i++) {
                        let sk = state.skills[i];
                        if (sk.mode === "real" && sk.virt != null) virts.push({ b: sk.virt, st: state });
                    }
                }
            }));
            scaleRealBullets(virts);
            if (server) serverTick();
            purgeT -= Time.delta;
            if (purgeT <= 0) { purgeT = 120; purgeStates(); }
        }
    }));

    function fillStar4(x, y, outer, inner, rot) {
        for (let i = 0; i < 4; i++) {
            let a0 = rot + i * 90;
            let a1 = a0 + 45;
            let a2 = a0 + 90;
            let tx0 = x + Mathf.cosDeg(a0) * outer, ty0 = y + Mathf.sinDeg(a0) * outer;
            let vx = x + Mathf.cosDeg(a1) * inner, vy = y + Mathf.sinDeg(a1) * inner;
            let tx1 = x + Mathf.cosDeg(a2) * outer, ty1 = y + Mathf.sinDeg(a2) * outer;
            Fill.tri(x, y, tx0, ty0, vx, vy);
            Fill.tri(x, y, vx, vy, tx1, ty1);
        }
    }

    Events.run(Trigger.draw, new Runnable({
        run: function() {
            if (Vars.headless || !isSurvivalActive()) return;
            Groups.player.each(new Cons({
                get: function(pl) {
                    let u = pl.unit();
                    if (u == null || !u.isValid() || u.dead) return;
                    let state = playerStates[getPlayerId(pl)];
                    if (state == null || state.skills.length === 0) return;
                    for (let i = 0; i < state.skills.length; i++) {
                        let sk = state.skills[i];
                        if (!sk.inited) continue;

                        if (sk.mode === "real") drawReal(sk);

                        Draw.z(Layer.flyingUnit + 1);
                        let col = sk.def.color;
                        let tier = Math.min(MAX_TIER_SHOTS, sk.tier);
                        let outer = 5 + tier * 1.3;
                        let inner = outer * 0.34;
                        let bob = Mathf.sin(Time.time * 0.08 + i * 1.7) * 0.8;
                        let x = sk.px;
                        let y = sk.py + bob;
                        let spin = Time.time * 1.6 + i * 37;

                        let n = sk.trail.length;
                        for (let j = 0; j < n; j++) {
                            let f = (j + 1) / (n + 1);
                            Draw.color(col, f * 0.3);
                            Fill.circle(sk.trail[j].x, sk.trail[j].y, outer * 0.22 * f);
                        }

                        Draw.color(Color.black, 0.22);
                        fillStar4(x + 3, y - 5, outer * 0.9, inner * 0.9, spin);

                        Draw.color(col, 0.2);
                        Fill.circle(x, y, outer * 1.15);
                        Draw.color(col);
                        fillStar4(x, y, outer, inner, spin);
                        Draw.color(Color.white, 0.9);
                        fillStar4(x, y, outer * 0.55, inner * 0.55, spin);
                        Draw.color(Color.white);
                        Fill.circle(x, y, outer * 0.14);
                    }
                    Draw.reset();
                }
            }));
        }
    }));

    function getLocalSkills() {
        if (Vars.player == null || !isSurvivalActive()) return [];
        let s = playerStates[getPlayerId(Vars.player)];
        if (s == null) return [];
        let res = [];
        for (let i = 0; i < s.skills.length; i++) {
            let sk = s.skills[i];
            res.push({
                name: sk.name,
                block: sk.def.block,
                tier: Math.min(MAX_TIER_SHOTS, sk.tier),
                maxTier: MAX_TIER_SHOTS,
                desc: descOf(sk.def),
                why: sk.why,
                simple: sk.mode !== "real"
            });
        }
        return res;
    }

    function showSkillList() {
        if (Vars.headless || !isSurvivalActive()) return;
        if (listDialog != null && listDialog.isShown()) {
            listDialog.hide();
            return;
        }
        if (Core.scene.hasDialog()) return;

        let dialog = new BaseDialog(isEn() ? "Equipped skills" : "Kĩ năng đã trang bị");
        dialog.addCloseButton();
        listDialog = dialog;

        let info = global.NexSkills.getLocalLevelInfo();
        let state = Vars.player != null ? playerStates[getPlayerId(Vars.player)] : null;
        if (info != null) {
            let head = (isEn() ? "Level " : "Cấp ") + info.level + "/" + info.maxLevel +
                (info.need > 0 ? "   EXP " + info.exp + "/" + info.need : "   MAX") +
                ("   " + (isEn() ? "Reroll pts: " : "Điểm đổi: ") + (info.reroll || 0));
            dialog.cont.add("[accent]" + head).pad(6).row();
        }

        if (state != null) {
            let statsLine = [];
            for (let i = 0; i < STAT_SRC.length; i++) {
                let s = STAT_SRC[i];
                let v = state.stats[s.stat] || 0;
                if (v > 0) statsLine.push(statName(s) + " +" + v);
            }
            if (statsLine.length > 0) {
                dialog.cont.add("[lightgray]" + statsLine.join("   ")).wrap().width(380).pad(6).row();
            }
        }

        let list = getLocalSkills();
        if (list.length === 0) {
            dialog.cont.add("[lightgray]" + (isEn() ? "No skills yet. Kill enemies to level up!" : "Chưa có kĩ năng nào. Diệt quái để lên cấp!")).pad(14).row();
        } else {
            dialog.cont.pane(new Cons({
                get: function(p) {
                    p.margin(4);
                    for (let i = 0; i < list.length; i++) {
                        let s = list[i];
                        let row = new Table(Styles.black6);
                        row.margin(8);
                        row.add(new Image(s.block.uiIcon)).size(38).padRight(10).top();

                        let col = new Table();
                        col.left();
                        let simpleTag = s.simple ? "   [scarlet]" + (isEn() ? "(simplified fallback" : "(bản rút gọn") + (s.why ? ": " + s.why : "") + ")[]" : "";
                        col.add("[accent]" + s.block.localizedName + "[]" + simpleTag).left().row();
                        let d = col.add("[lightgray]" + s.desc + "[]").left().growX().get();
                        d.setWrap(true);
                        d.setFontScale(0.9);
                        row.add(col).growX().width(300);

                        p.add(row).width(390).pad(3).row();
                    }
                }
            })).scrollX(false).maxHeight(Core.graphics.getHeight() * 0.65);
        }
        dialog.show();
    }

    global.NexSkills = {
        getMaxLevel: getMaxLevel,
        getLocalLevelInfo: function() {
            if (Vars.player == null || !isSurvivalActive()) return null;
            if (Vars.net.client()) {
                let m = meInfo;
                if (m == null) return { level: 0, maxLevel: getMaxLevel(), exp: 0, need: levelCost(1), reroll: 0 };
                return { level: m.level, maxLevel: m.maxLevel, exp: m.exp, need: m.need, reroll: m.reroll };
            }
            let maxLv = getMaxLevel();
            let s = playerStates[getPlayerId(Vars.player)];
            if (s == null) return { level: 0, maxLevel: maxLv, exp: 0, need: levelCost(1), reroll: 0 };
            let need = s.level < maxLv ? levelCost(s.level + 1) : 0;
            return { level: s.level, maxLevel: maxLv, exp: Math.floor(s.exp), need: need, reroll: s.rerollPts || 0 };
        },
        getLocalSkills: getLocalSkills,
        showSkillList: showSkillList,
        listSkills: function() {
            let pool = buildSkillPool();
            let res = [];
            for (let i = 0; i < pool.length; i++) {
                res.push(pool[i].name);
            }
            return res;
        }
    };
})();