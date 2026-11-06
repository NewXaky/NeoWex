// ===== thermion.js — chuyển từ Thermion.java (Mindustry v157, Rhino/Android) =====
// global.NexNet có thể chưa/không được nạp -> tra cứu lười + dự phòng
const NexNet = {
    fx: function(e, x, y, r, c){ const n = global.NexNet; if(n && n.fx) return n.fx.apply(n, arguments); if(e == null) return; if(c != null) e.at(x, y, r || 0, c); else e.at(x, y, r || 0); },
    shake: function(i, d, x, y){ const n = global.NexNet; if(n && n.shake) return n.shake.apply(n, arguments); Effect.shake(i, d, x, y); },
    take: function(core, item, amt){ const n = global.NexNet; if(n && n.take) return n.take.apply(n, arguments); core.items.remove(item, amt); }
};
const isEn = () => Core.settings.getString("locale").startsWith("en");
const T = (vi, en) => isEn() ? en : vi;
const TS = Vars.tilesize, DEG = Math.PI / 180;
const YELLOW = 0, BLUE = 1, PURPLE = 2, DEATH = 3, MAX_LEVEL = 5, CFG_ACTIVE = 10, CFG_UPGRADE = 20;
const AST_NONE = 0, AST_CHARGE = 1, AST_FALL = 2;
const T_FIELD = 0, T_BLADE = 1, T_RCT = 2, T_ILLUSION = 3, T_DRONE = 4, T_SHAPE = 5, T_MIND = 6, T_ASTEROID = 7;
const MAX_COST_ITEMS = 8;
const TRIG = Packages.mindustry.game.EventType.Trigger;

// ---------- helper toán (dùng Math thay Mathf để tránh lỗi overload int/float của Rhino)
const cl = (x, a, b) => Math.min(b === undefined ? 1 : b, Math.max(a === undefined ? 0 : a, x));
const rnd = (a, b) => a + Math.random() * (b - a), rr = a => (Math.random() * 2 - 1) * a;
const ri = (a, b) => a + Math.floor(Math.random() * (b - a + 1)), chance = p => Math.random() < p;
const rs = (s, a, b) => { let x = Math.sin(s * 12.9898 + 78.233) * 43758.5453; x -= Math.floor(x); return a + (b - a) * x; };
const tx = (a, l) => Math.cos(a * DEG) * l, ty = (a, l) => Math.sin(a * DEG) * l;
const lerp = (a, b, t) => a + (b - a) * t, dst = (a, b, c, d) => Math.hypot(c - a, d - b);
const I = n => java.lang.Integer.valueOf(n);

// ---------- bảng số liệu (index = level - 1)
const FORM_CD = [120, 105, 90, 75, 60], FORM_TIME = [5, 4.5, 4, 3.5, 3], HP_BONUS = [75, 50, 20];
const SLOW = [10, 15, 20, 30, 40], SPH_CHARGE = [0, 120, 105, 105, 90], SPH_DMG = [0, 300, 500, 700, 1100], SPH_MAX = [0, 10, 15, 30, 50];
const SH_PCT = [0, 0, 50, 75, 100], SH_CD = [0, 0, 1200, 900, 600], SH_RADIUS = [0, 0, 5, 10, 15];
const MAG = [0, 0, 0, 1.5, 2], BL_COUNT = [0, 0, 0, 1, 2], BL_DMG = [0, 0, 0, 400, 500], BL_CREATE = [0, 0, 0, 1800, 1200];
const EMP_DURATION = 300, EMP_COOLDOWN = 7200;
const BLS_CH = [15, 25, 35, 45, 60], BLS_DMG = [300, 350, 450, 600, 900], BLS_SPLASH = [2, 2, 2.5, 2.5, 3];
const MET_CH = [0, 5, 10, 15, 20], MET_PCT = [0, 1, 1, 2, 2], MET_FLAT = [0, 50, 75, 125, 250];
const LAS_CH = [0, 0, 2, 5, 10], LAS_DMG = [0, 0, 250, 400, 700], LAS_TIME = [0, 0, 2, 2.5, 3];
const RCT_T = [0, 0, 0, 900, 600], RCT_A = [0, 0, 0, 50, 100], LIM_DECAY = [0, 0, 0, 5, 10];
const ILL_T = [5, 4.5, 4, 3.5, 3], ILL_CH = [5, 10, 15, 20, 25], ILL_DUR = [1, 1.5, 2, 2.5, 3];
const DRONE_T = [0, 10, 8.5, 7, 5], SHAPE_T = [0, 0, 10, 9, 8], SHAPE_CH = [0, 0, 5, 10, 15];
const MIND_T = [0, 0, 0, 15, 10], MIND_CH = [0, 0, 0, 10, 15];
const AST_CHARGE_TICKS = 3600, SKY_HEIGHT = 1400, METEOR_FALL = 27, AST_TRAVEL = 360, AST_WAVE = 150;
const AST_BODY_TILES = 100, AST_CRATERS = 32, AST_FLASH = 300, AST_FLASH_HOLD = 0.1, AST_QUAKE = 3600, AST_SHAKE = 20;
const MUSHROOM_TICKS = 2160, MUSHROOM_TILES = 64, AST_COOLDOWN = 18000, DEATH_TICKS = 36000, MEGA_COOLDOWN = 10800;
const BLS_MAX_TICKS = 300, BLS_COOLDOWN_TICKS = 180; // tia plasma: đuổi tối đa 5s, hồi 3s
const ORB_R = 12, METEOR_RAD_TILES = 4, METEOR_PUDDLES = 3, SLAG_CHANCE = 0.75, BLS_SPLASH_FRAC = 1 / 3, LAS_SPLASH_FRAC = 0.5;
const SH_START = 0.1, BONUS_PER_M = 0.005, BONUS_CAP = 10000000, BONUS_BLADE = 0.5;
const SH_REGEN = 120, SH_REGEN_FRAC = 0.1, SH_HIT_GAP = 4, LOOP_TICKS = 46, SPH_SPAWN_R = 15;
const ORB_Z = Layer.flyingUnit + 1, BLADE_Z = Layer.flyingUnitLow, PUSH = 2.5, BLADE_SPEED = 7, BLADE_R = 20;
const BLS_INT = 15, BLS_FALL = 10, LIM_DIV = 3, SHAPE_DELAY = 120, RANGE_REF = 50, DRONE_V2_LEVEL = 4;
const YELLOW_COLOR = Color.gold, BLUE_COLOR = Color.sky, PURPLE_COLOR = Color.purple, GRAY_COLOR = Color.gray;
const AST_BODY_C = Color.valueOf("3a2f2a"), AST_LIGHT_C = Color.valueOf("7a6858"), CR_RIM = Color.valueOf("6b5a4d"),
    CR_PIT = Color.valueOf("231b18"), CR_DEEP = Color.valueOf("120d0b"), SMOKE_C = Color.valueOf("3b322d"), ASH_C = Color.valueOf("6a5c52");
let auraAlpha = 1;

const val = (t, lv) => t[Math.min(Math.max(lv, 1), MAX_LEVEL) - 1];
const powerRequirement = lv => lv * lv * 1000;
const formHealthBonus = (f, lv) => (f < YELLOW || f > PURPLE) ? 0 : HP_BONUS[f] * lv;
const formColor = f => f == YELLOW ? YELLOW_COLOR : f == BLUE ? BLUE_COLOR : f == PURPLE ? PURPLE_COLOR : GRAY_COLOR;
const formName = f => ["Yellow", "Blue", "Purple", "Death"][f];
const colorTag = f => "[#" + formColor(f) + "]";
const fmt = v => Math.abs(v - Math.round(v)) < 0.001 ? String(Math.round(v)) : v.toFixed(1);
const compactNum = v => { const a = Math.abs(v); return a >= 1000000 ? (v / 1000000).toFixed(2) + "M" : a >= 10000 ? (v / 1000).toFixed(1) + "k" : String(Math.round(v)); };
const timeText = t => { const s = Math.max(0, Math.ceil(t / 60)); return Math.floor(s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); };
const isSandbox = () => Vars.state != null && Vars.state.rules != null && Vars.state.rules.mode() == Packages.mindustry.game.Gamemode.sandbox;

// ---------- chi phí nâng cấp: [item, số lượng]
let upCosts = null;
function upgradeCost(lv){
    if(lv < 1 || lv >= MAX_LEVEL) return null;
    if(upCosts == null) upCosts = [
        [[Items.copper, 500], [Items.silicon, 100], [Items.metaglass, 200]],
        [[Items.copper, 1500], [Items.silicon, 600], [Items.metaglass, 1000], [Items.titanium, 500], [Items.plastanium, 200]],
        [[Items.copper, 6000], [Items.silicon, 3000], [Items.titanium, 2500], [Items.plastanium, 1600], [Items.thorium, 1500], [Items.phaseFabric, 600]],
        [[Items.copper, 45000], [Items.silicon, 17000], [Items.titanium, 24000], [Items.plastanium, 11000], [Items.thorium, 21000], [Items.phaseFabric, 12000], [Items.surgeAlloy, 7000]]
    ];
    const b = upCosts[lv - 1];
    return isSandbox() ? b.map(c => [c[0], 0]) : b;
}

// ---------- âm thanh
function snd(names){
    for(let i = 0; i < names.length; i++){ try{ const s = Sounds[names[i]]; if(s != null && s != undefined) return s; }catch(e){} }
    return null;
}
const ASSET_NAMES = ["DeathLaserExplosion", "DeathLaserLoop", "DroneShootLaser", "DualBladeHitSound", "EnergyShieldBreak", "EnergyShieldHit", "MachineGunShootSound", "MeteoroidHitSound", "OrbHitSound", "OrbShootSound"];
const assetSnd = {};
ASSET_NAMES.forEach(n => { try{ assetSnd[n] = Vars.tree.loadSound(n); }catch(e){ Log.err("[Thermion] không nạp được sounds/" + n); } });
const pick = (n, fb) => assetSnd[n] || fb();
const isAssetSound = s => { for(var k in assetSnd) if(assetSnd[k] === s) return true; return false; };
const sndShootBig = () => snd(["shootBig", "shootbig", "bigshot", "shootAltLong", "shootAlt", "shotgun", "shoot"]);
const sndSpark = () => snd(["spark", "electric", "shock", "flux"]);
const sndPulse = () => snd(["pulse", "shockBlast", "empulse", "laserblast", "laserBlast", "lasercharge", "laserCharge"]);
const sndLaser = () => snd(["laser", "lasershoot", "laserShoot", "shootLaser", "beam", "laserbeam", "laserBeam"]);
const sndLaserBig = () => snd(["laserbig", "laserBig", "laserblast", "laserBlast", "laserbeam", "laserBeam", "lasercharge", "laserCharge"]);
const sndExplosionBig = () => snd(["explosionbig", "explosionBig", "bigExplosion", "largeExplosion", "corexplode", "coreExplode", "explosion"]);
const sndOrbShoot = () => pick("OrbShootSound", sndShootBig), sndMachineGun = () => pick("MachineGunShootSound", () => Sounds.shoot);
const sndDroneLaser = () => pick("DroneShootLaser", sndLaser), sndOrbHit = () => pick("OrbHitSound", () => Sounds.none);
const sndShieldHit = () => pick("EnergyShieldHit", sndSpark), sndShieldBreak = () => pick("EnergyShieldBreak", sndPulse);
const sndBladeHit = () => pick("DualBladeHitSound", sndSpark), sndMeteorHit = () => pick("MeteoroidHitSound", () => Sounds.explosion);
const sndDeathLoop = () => pick("DeathLaserLoop", sndLaserBig), sndDeathBlast = () => pick("DeathLaserExplosion", sndExplosionBig);

// ---------- registry: bar/hook/đạn truy cập building qua đây (id -> object JS của building)
const SELF = {};
const bonusFraction = cap => Math.min(Math.max(cap, 0), BONUS_CAP) / 1000000 * BONUS_PER_M;
function nearestYellowCapacity(team, px, py){
    let best = null, bd = 1e30;
    for(var k in SELF){
        const t = SELF[k];
        if(!t.isValid()){ delete SELF[k]; continue; }
        if(t.team != team || t.getForm() != YELLOW) continue;
        const d = (t.x - px) * (t.x - px) + (t.y - py) * (t.y - py);
        if(d < bd){ bd = d; best = t; }
    }
    return best == null ? 0 : best.gridCapacity();
}

// ---------- hàm vẽ
function drawMulti(f){ f(1.3, null, 0.3 * auraAlpha, 1.4); f(1, null, auraAlpha, 1); f(0.65, Color.white, 0.9 * auraAlpha, 0.6); }
function drawTight(f){ f(1.12, null, 0.35 * auraAlpha, 1.1); f(1, null, auraAlpha, 1); f(0.82, Color.white, 0.8 * auraAlpha, 0.85); }
function drawCorePulse(x, y, color, scale){
    Draw.color(color);
    const r = (Time.time * 2) % 24 * scale;
    Draw.alpha(Math.max(0, 1 - r / (24 * scale)) * auraAlpha);
    Lines.stroke(2); Lines.poly(x, y, 6, r, 0); Draw.reset();
}
function drawMeteorImpact(x, y, radius, p){
    Draw.color(Color.scarlet, Color.orange, p); Lines.stroke(3 * (1 - p)); Lines.circle(x, y, radius * p);
    Draw.color(Color.orange); Fill.circle(x, y, (1 - p) * 8); Draw.reset();
}
function drawCrater(cx, cy, cr){
    Draw.color(CR_RIM); Draw.alpha(0.55); Fill.circle(cx, cy, cr * 1.12);
    Draw.color(CR_PIT); Draw.alpha(0.75); Fill.circle(cx, cy, cr * 0.94);
    Draw.color(CR_DEEP); Draw.alpha(0.8); Fill.circle(cx - cr * 0.14, cy + cr * 0.14, cr * 0.72); Draw.reset();
}
function drawAsteroidBody(px, py, rad, seed){
    Draw.color(AST_BODY_C); Draw.alpha(0.6); Fill.poly(px, py, 64, rad, 0);
    Draw.color(AST_LIGHT_C); Draw.alpha(0.18); Fill.circle(px - rad * 0.16, py + rad * 0.16, rad * 0.78);
    for(let k = 0; k < AST_CRATERS; k++){
        const u = k / (AST_CRATERS - 1);
        const cr = rad * (0.02 + 0.16 * Math.pow(1 - u, 2.2)) * rs(seed + k * 131 + 5, 0.75, 1.25);
        const ca = rs(seed + k * 197 + 11, 0, 360);
        const cd = (rad - cr * 1.2) * Math.sqrt(rs(seed + k * 263 + 17, 0, 1));
        drawCrater(px + tx(ca, cd), py + ty(ca, cd), cr);
    }
    Draw.reset();
}
function heatColor(heat, alpha){
    heat = cl(heat);
    if(heat > 0.5) Draw.color(Color.orange, Color.white, (heat - 0.5) * 2); else Draw.color(SMOKE_C, Color.orange, heat * 2);
    Draw.alpha(alpha);
}
function drawMushroom(x, y, s, p){
    const sd = 4242, fade = cl((1 - p) * 4), rise = 1 - Math.pow(1 - cl(p / 0.6), 3);
    const capGrow = 1 - Math.pow(1 - cl((p - 0.08) / 0.55), 2), height = s * 2.6 * rise;
    const capR = s * (0.25 + 0.75 * capGrow), capY = y + height, cool = 1.15 - p * 1.5;
    Draw.z(Layer.effect + 1);
    const dustR = s * (0.5 + cl(p / 0.5));
    for(let i = 0; i < 16; i++){
        const a = i * 22.5 + rs(sd + i * 3, -8, 8);
        Draw.color(ASH_C); Draw.alpha(0.55 * fade);
        Fill.circle(x + Math.cos(a * DEG) * dustR, y + Math.sin(a * DEG) * dustR * 0.22, s * 0.22);
    }
    for(let i = 0; i < 22; i++){
        const f = i / 21, px = x + rs(sd + 100 + i, -1, 1) * s * 0.07, py = y + height * f;
        const pr = s * (0.16 + 0.4 * (1 - f) * (1 - f)) * rs(sd + 200 + i, 0.9, 1.15), heat = cool + (1 - f) * 0.3;
        heatColor(heat, 0.22 * fade); Fill.circle(px, py, pr * 1.35);
        heatColor(heat, 0.85 * fade); Fill.circle(px, py, pr);
    }
    const fb = 1 - cl(p / 0.35);
    if(fb > 0){
        Draw.color(Color.white, Color.orange, 1 - fb); Draw.alpha(fade * (0.4 + 0.6 * fb));
        Fill.circle(x, y, s * (0.5 + 0.5 * cl(p / 0.08)) * (0.6 + 0.4 * fb));
    }
    if(capGrow > 0){
        for(let i = 0; i < 12; i++){
            const a = i * 30 + rs(sd + 300 + i, -10, 10);
            heatColor(cool - 0.25, 0.8 * fade);
            Fill.circle(x + Math.cos(a * DEG) * capR * 0.62, capY - capR * 0.28 + Math.sin(a * DEG) * capR * 0.22, capR * 0.27);
        }
        for(let i = 0; i < 28; i++){
            const a = i * (360 / 28) + rs(sd + 500 + i, -6, 6) + p * 40, k = rs(sd + 600 + i, 0.85, 1.05);
            heatColor(cool + 0.15 - 0.3 * Math.sin(a * DEG), 0.9 * fade);
            Fill.circle(x + Math.cos(a * DEG) * capR * k, capY + Math.sin(a * DEG) * capR * 0.5 * k, capR * rs(sd + 650 + i, 0.22, 0.32));
        }
        for(let i = 0; i < 16; i++){
            const a = rs(sd + 700 + i, 0, 360), r2 = capR * 0.75 * Math.sqrt(rs(sd + 800 + i, 0, 1));
            heatColor(cool + 0.3, 0.9 * fade);
            Fill.circle(x + Math.cos(a * DEG) * r2, capY + Math.sin(a * DEG) * r2 * 0.5, capR * rs(sd + 850 + i, 0.2, 0.32));
        }
    }
    Draw.reset();
}
function drawYellowAura(x, y, level, t){
    const core = 7 + Math.sin(t * 0.15) * 2, base = 0;
    drawMulti((scale, ov, alpha) => {
        Draw.color(ov != null ? ov : Color.gold); Draw.alpha(alpha); Fill.circle(x, y, core * scale);
        for(let i = 0; i < 8; i++){
            const ang = base + i * 45, off = Math.sin(t * 0.1 + i) * 5, cr = 16 + Math.max(0, off);
            Fill.poly(x + Math.cos(ang * DEG) * cr, y + Math.sin(ang * DEG) * cr, 3, 4 * scale, ang + 90);
        }
        Draw.reset();
    });
    if(level >= 3) drawMulti((scale, ov, alpha, ss) => {
        if(ov != null) Draw.color(ov); else Draw.color(Color.yellow, Color.orange, (Math.sin(t * 0.05) + 1) * 0.5);
        Draw.alpha(alpha * 0.7); Lines.stroke(1.5 * ss); Lines.poly(x, y, 8, 36 * scale, 0); Draw.reset();
    });
}
function drawBlueAura(x, y, t){
    const sf = 1 + Math.sin(t * 0.1) * 0.25, core = (7 + Math.sin(t * 0.15) * 2) * sf, r1 = 22 * sf, r2 = 30 * sf;
    const a1 = 0, a2 = 180;
    drawMulti((scale, ov, alpha, ss) => {
        Draw.color(ov != null ? ov : Color.sky); Draw.alpha(alpha); Fill.circle(x, y, core * scale);
        Lines.stroke(2 * ss); Lines.arc(x, y, r1, 0.75, a1); Lines.arc(x, y, r2, 0.75, a2); Draw.reset();
    });
}
function drawPurpleAura(x, y, t){
    const radius = 28, base = 0;
    drawMulti((scale, ov, alpha, ss) => {
        for(let i = 0; i < 4; i++){
            const sa = i * 90, sr = 12 + Math.sin(t * 0.1 + i) * 3;
            Draw.color(ov != null ? ov : Color.violet); Draw.alpha(alpha);
            Fill.circle(x + Math.cos(sa * DEG) * sr, y + Math.sin(sa * DEG) * sr, 3.5 * scale);
        }
        Draw.color(ov != null ? ov : Color.purple); Draw.alpha(alpha);
        Fill.circle(x, y, (6 + Math.cos(t * 0.1) * 1.5) * scale);
        Lines.stroke(1.5 * ss); Lines.circle(x, y, radius);
        for(let i = 0; i < 4; i++){
            const ang = base + i * 90;
            Fill.poly(x + Math.cos(ang * DEG) * radius, y + Math.sin(ang * DEG) * radius, 3, 5 * scale, ang);
        }
        Draw.reset();
    });
}
function drawRange(x, y, radius, c){
    Draw.z(Layer.overlayUI); Draw.color(c); Draw.alpha(0.6); Lines.stroke(2); Lines.poly(x, y, 12, radius, 0);
    const pr = (Time.time % 90) / 90 * radius;
    Draw.alpha(1 - pr / radius); Lines.stroke(1); Lines.circle(x, y, pr);
    Draw.alpha(0.8);
    for(let i = 0; i < 4; i++){ const a = i * 90; Fill.square(x + Math.cos(a * DEG) * radius, y + Math.sin(a * DEG) * radius, 3, a); }
    Draw.alpha(0.15); Fill.circle(x, y, radius); Draw.reset();
}
function skyBeam(x, y, width, alpha, height, c){
    const top = y + height;
    drawMulti((scale, ov, a) => {
        Draw.color(ov != null ? ov : c); Draw.alpha(a * alpha); Lines.stroke(Math.max(0.1, width * scale));
        Lines.line(x, y, x, top); Fill.circle(x, y, width * 0.9 * scale); Draw.reset();
    });
}
function beamTo(x1, y1, x2, y2, width, c){
    drawMulti((scale, ov, a) => {
        Draw.color(ov != null ? ov : c); Draw.alpha(a); Lines.stroke(Math.max(0.1, width * scale));
        Lines.line(x1, y1, x2, y2); Fill.circle(x1, y1, width * 0.9 * scale); Draw.reset();
    });
}
function drawSaw(sx, sy, rad, rot, am){
    additive(); Draw.color(Color.gold); Draw.alpha(0.55 * am);
    for(let k = 0; k < 2; k++) Drawf.tri(sx, sy, rad * 0.55, rad * 1.5, rot + k * 180);
    Draw.color(Color.white, Color.gold, 0.25); Draw.alpha(0.95 * am);
    for(let k = 0; k < 2; k++) Drawf.tri(sx, sy, rad * 0.3, rad * 1.15, rot + k * 180);
    Draw.color(Color.gold); Draw.alpha(0.8 * am); Lines.stroke(1.5); Lines.circle(sx, sy, rad * 0.6);
    Draw.blend(); bloom(sx, sy, rad * 0.9, Color.gold, 0.8 * am);
    Draw.color(Color.white); Fill.circle(sx, sy, rad * 0.16); Draw.reset();
}
function drawOrb(ox, oy, r, glow, spinMul){
    const g = cl(glow), len = r * (1.2 + 2.8 * g), w = r * (0.55 + 0.35 * g), rot = 90;
    additive(); Draw.color(Color.gold); Draw.alpha(0.5);
    for(let k = 0; k < 3; k++) Drawf.tri(ox, oy, w * 1.7, len * 1.25, rot + k * 120);
    Draw.color(Color.white, Color.gold, 0.3); Draw.alpha(0.95);
    for(let k = 0; k < 3; k++) Drawf.tri(ox, oy, w, len, rot + k * 120);
    Draw.blend(); bloom(ox, oy, r * (1 + g), Color.gold, 0.7);
    Draw.color(Color.white); Fill.circle(ox, oy, r * 0.35); Draw.reset();
}
function drawElectricRing(x, y, r, t){
    Draw.color(Color.gold, Color.white, 0.3); Draw.alpha(0.85); Lines.stroke(1.2);
    const seg = 10; let px = 0, py = 0;
    for(let i = 0; i <= seg; i++){
        const a = i * 360 / seg, r2 = r + Math.sin(t * 0.7 + i * 2.3) * 2;
        const cx = x + Math.cos(a * DEG) * r2, cy = y + Math.sin(a * DEG) * r2;
        if(i > 0) Lines.line(px, py, cx, cy);
        px = cx; py = cy;
    }
    Draw.reset();
}
function drawStunMark(x, y, r, t, c){
    Draw.color(c); Draw.alpha(0.9); Lines.stroke(1.5);
    Lines.arc(x, y, r + 4, 0.25, 0); Lines.arc(x, y, r + 4, 0.25, 180);
    Fill.square(x, y + r + 8 + Math.sin(t * 0.2) * 2, 2, 45); Draw.reset();
}

// ---------- hiệu ứng
const FX_SPHERE = new Effect(14, 100, e => {
    Draw.color(Color.white, e.color, e.fin()); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, 3 + 9 * e.finpow()); Draw.reset();
});
const FX_SHIELD_BREAK = new Effect(40, 400, e => {
    Draw.color(e.color, Color.white, e.fout()); Lines.stroke(3 * e.fout());
    Lines.poly(e.x, e.y, 6, e.rotation * (1 + 0.4 * e.finpow()), Time.time * 0.3);
    for(let i = 0; i < 6; i++){
        const a = i * 60 + 15, r1 = e.rotation * (1 + 0.2 * e.fin()), r2 = r1 + 14 * e.fout();
        Lines.line(e.x + tx(a, r1), e.y + ty(a, r1), e.x + tx(a, r2), e.y + ty(a, r2));
    }
    Draw.reset();
});
const FX_MAGNETIC = new Effect(30, 100, e => {
    Draw.color(e.color, Color.white, e.fin()); Lines.stroke(1 + 2 * e.fin()); Lines.circle(e.x, e.y, 22 * e.fout());
    for(let i = 0; i < 4; i++){
        const a = i * 90 + 45, r1 = 30 * e.fout(), r2 = 16 * e.fout();
        Lines.line(e.x + tx(a, r1), e.y + ty(a, r1), e.x + tx(a, r2), e.y + ty(a, r2));
    }
    Draw.reset();
});
const FX_BLADE_SLASH = new Effect(12, 100, e => {
    Draw.color(Color.white, e.color, e.fin()); Lines.stroke(3 * e.fout());
    Lines.lineAngleCenter(e.x, e.y, e.rotation, 26 * e.finpow()); Lines.lineAngleCenter(e.x, e.y, e.rotation + 90, 14 * e.finpow()); Draw.reset();
});
const FX_EMP = new Effect(55, 6000, e => {
    const r = e.rotation * e.finpow();
    Draw.color(e.color, Color.white, e.fout()); Lines.stroke(6 * e.fout()); Lines.circle(e.x, e.y, r);
    Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, r * 0.7);
    for(let i = 0; i < 18; i++){
        const a = i * 20 + rs(e.id + i, -6, 6), r2 = r * 0.82;
        Lines.line(e.x + tx(a, r2), e.y + ty(a, r2), e.x + tx(a, r), e.y + ty(a, r));
    }
    Draw.reset();
});
const FX_BLASTER = new Effect(16, 1700, e => {
    const split = 0.6;
    if(e.fin() < split){
        const fall = e.fin() / split, head = e.y + 520 * (1 - fall);
        drawMulti((scale, ov, a) => {
            Draw.color(ov != null ? ov : e.color); Draw.alpha(a); Lines.stroke(5 * scale);
            Lines.line(e.x, head, e.x, head + 110); Fill.circle(e.x, head, 4 * scale); Draw.reset();
        });
    }else{
        const pr = (e.fin() - split) / (1 - split);
        Draw.color(Color.white, e.color, pr); Lines.stroke(3 * (1 - pr)); Lines.circle(e.x, e.y, 6 + 16 * pr);
        Draw.alpha(1 - pr); Fill.circle(e.x, e.y, 6 * (1 - pr));
    }
    Draw.reset();
});
// sprites/shapes/meteor.png -> vùng ảnh "newex-meteor". METEOR_SPRITE_ROT: xoay thêm nếu ảnh gốc không hướng lên trên.
const METEOR_SPRITE_ROT = 0, METEOR_SPRITE_SIZE = 84;
let meteorRegion = null;
const getMeteorRegion = () => { if(meteorRegion == null) meteorRegion = Core.atlas.find("newex-meteor"); return meteorRegion; };
const METEOR_DX = 480, METEOR_DY = 520, STAR_MIN = 2, STAR_MAX = 3.5; // bay từ phải-trên xuống; kích thước & độ dài sao: 4-7 ô
function drawMeteorFall(e, f, hx, hy){
    const ang = Math.atan2(METEOR_DY, METEOR_DX) / DEG, size = rs(e.id * 3 + 1, STAR_MIN, STAR_MAX) * TS, tail = rs(e.id * 5 + 2, STAR_MIN, STAR_MAX) * TS;
    const spin = e.fin() * 540 + rs(e.id, 0, 360);
    // bóng đổ + vòng cảnh báo điểm rơi
    Draw.color(Color.black); Draw.alpha(0.28 * f); Fill.circle(e.x, e.y, 5 + 17 * f);
    Draw.color(Color.scarlet, Color.orange, f); Draw.alpha(0.5 + 0.4 * f); Lines.stroke(2); Lines.circle(e.x, e.y, 23 * (1 - f) + 6);
    additive();
    // đuôi lửa: tam giác phát sáng + 6 ảnh sao mờ dần dọc theo đuôi
    Draw.color(Color.white, Color.scarlet, 0.5); Draw.alpha(0.7); Drawf.tri(hx, hy, size * 0.5, tail, ang);
    Draw.color(Color.orange); Draw.alpha(0.35); Drawf.tri(hx, hy, size * 0.9, tail * 0.8, ang);
    for(let k = 1; k <= 6; k++){
        const d = tail * k / 6, sc = 1 - k / 7;
        Draw.color(Color.orange, Color.scarlet, k / 6); Draw.alpha(0.5 * sc);
        for(let a2 = 0; a2 < 4; a2++) Drawf.tri(hx + tx(ang, d), hy + ty(ang, d), size * 0.2 * sc, size * 0.8 * sc, spin - k * 25 + a2 * 90);
    }
    // sao 4 cánh: lớp ngoài màu cam, lớp trong trắng
    Draw.color(Color.orange, Color.white, 0.3); Draw.alpha(0.6);
    for(let a2 = 0; a2 < 4; a2++) Drawf.tri(hx, hy, size * 0.34, size * 1.25, spin + a2 * 90);
    Draw.color(Color.white); Draw.alpha(0.95);
    for(let a2 = 0; a2 < 4; a2++) Drawf.tri(hx, hy, size * 0.22, size * (a2 % 2 == 0 ? 1 : 0.7), spin + a2 * 90);
    Draw.blend();
    bloom(hx, hy, size * 1.1, Color.orange, 0.9);
    Draw.color(Color.white); Fill.circle(hx, hy, size * 0.12);
    // tàn lửa bắn ra phía sau
    Draw.color(Color.white, Color.orange, 0.5); Lines.stroke(1.6);
    for(let i = 0; i < 14; i++){ const a = ang + rs(e.id * 5 + i, -30, 30), d = 10 + tail * 1.4 * ((e.fin() * 6 + i * 0.29) % 1); Lines.lineAngle(hx + tx(ang, d) + tx(a + 90, rs(i, -8, 8)), hy + ty(ang, d), a, 8 * (1 - d / (tail * 1.5))); }
    Draw.reset();
}
const FX_METEOR = new Effect(45, 900, e => {
    const split = 0.6;
    if(e.fin() < split){
        const f = e.fin() / split;
        drawMeteorFall(e, f, e.x + METEOR_DX * (1 - f), e.y + METEOR_DY * (1 - f));
    }else drawMeteorImpact(e.x, e.y, e.rotation > 0 ? e.rotation : 30, (e.fin() - split) / (1 - split));
    Draw.reset();
});
const FX_DEATH_LASER = new Effect(12, 1700, e => {
    const fade = Math.min(1, e.fout() * 2), pulse = 1 + 0.2 * Math.sin(Time.time * 0.5);
    skyBeam(e.x, e.y, 18 * pulse * fade, 1, SKY_HEIGHT, e.color);
    Draw.color(e.color); Draw.alpha(0.6 * fade); Lines.stroke(2); Lines.poly(e.x, e.y, 16, e.rotation, Time.time * 0.5);
    Draw.alpha(0.08 * fade); Fill.circle(e.x, e.y, e.rotation); Draw.reset();
});
const FX_DEATH_BLAST = new Effect(40, 1700, e => {
    Draw.color(Color.white, e.color, e.fin()); Lines.stroke(8 * e.fout()); Lines.circle(e.x, e.y, e.rotation * e.finpow());
    Draw.alpha(0.35 * e.fout()); Fill.circle(e.x, e.y, e.rotation * 0.5 * e.fout());
    skyBeam(e.x, e.y, 32 * e.fout(), e.fout(), SKY_HEIGHT, e.color); Draw.reset();
});
const FX_HEAL = new Effect(45, 400, e => {
    Draw.color(e.color, Color.white, e.fout()); Lines.stroke(3 * e.fout()); Lines.circle(e.x, e.y, e.rotation * 0.5 + e.rotation * e.finpow());
    for(let i = 0; i < 6; i++){
        const a = i * 60, r2 = e.rotation * 0.6, px = e.x + tx(a, r2), py = e.y + ty(a, r2) + 30 * e.fin(), sz = 3.5 * e.fout();
        Lines.stroke(1.5 * e.fout() + 0.5); Lines.line(px - sz, py, px + sz, py); Lines.line(px, py - sz, px, py + sz);
    }
    Draw.reset();
});
// Thiên thạch lớn kéo xuống: dùng sprites/shapes/meteor.png (vùng "newex-meteor"); thiếu ảnh thì vẽ thân procedural cũ.
const AST_SPRITE_SCALE = 2.15, AST_SPRITE_ROT = 0;
function drawAsteroidSprite(px, py, rad, seed, ap){
    const reg = getMeteorRegion();
    if(reg == null || !reg.found()){ drawAsteroidBody(px, py, rad, seed); return; }
    const d = rad * AST_SPRITE_SCALE, rot = AST_SPRITE_ROT + rs(seed, 0, 360) + ap * 50;
    bloom(px, py, rad * 1.25, Color.orange, 0.35 + 0.4 * ap);               // quầng nhiệt lớn dần khi vào khí quyển
    Draw.reset(); Draw.rect(reg, px, py, d, d, rot);
    Draw.color(Color.scarlet, Color.orange, ap); Draw.alpha(0.25 + 0.35 * ap); // lớp lửa viền
    try{ Draw.blend(Packages.arc.graphics.Blending.additive); }catch(err){}
    Fill.circle(px, py, rad * 1.02);
    Draw.blend(); Draw.reset();
}
const FX_AST_FALL = new Effect(AST_TRAVEL, 40000, e => {
    const acx = Vars.world.unitWidth() / 2, acy = Vars.world.unitHeight() / 2, ap = e.fin();
    const px = lerp(e.x, acx, ap), py = lerp(e.y, acy, ap), rad = AST_BODY_TILES * TS;
    drawAsteroidSprite(px, py, rad, Math.floor(e.rotation * 100), ap);
    drawMulti((scale, ov, a, ss) => {
        Draw.color(ov != null ? ov : Color.orange); Draw.alpha(a * 0.7); Lines.stroke(30 * ss); Lines.poly(px, py, 64, rad, 0); Draw.reset();
    });
    Draw.reset();
});
const FX_AST_FLASH = new Effect(AST_FLASH, 100000, e => {
    Draw.z(Layer.overlayUI + 10); Draw.color(Color.white);
    const left = 1 - cl((e.fin() - AST_FLASH_HOLD) / (1 - AST_FLASH_HOLD));
    Draw.alpha(left * left * (3 - 2 * left));
    Fill.rect(Core.camera.position.x, Core.camera.position.y, Core.camera.width * 1.3, Core.camera.height * 1.3); Draw.reset();
});
const FX_AST_QUAKE = new Effect(AST_QUAKE, 100000, e => { if(!Vars.state.isPaused()) Vars.renderer.shake(AST_SHAKE * Math.pow(e.fout(), 0.6), 6); });
const FX_AST_IMPACT = new Effect(260, 100000, e => {
    // một xung sóng âm lan tới hết bản đồ rồi mới tan
    const reach = Math.max(e.rotation, 400), p = e.fin(), r = reach * 1.05 * p, a = 1 - Math.pow(p, 4);
    additive();
    for(let i = 0; i < 10; i++){
        const rr2 = r - i * 14;
        if(rr2 <= 0) break;
        const k = 1 - i / 10;
        Draw.color(Color.white, Color.orange, i / 10); Draw.alpha(0.85 * a * k * k); Lines.stroke(16 * k + 2); Lines.circle(e.x, e.y, rr2);
    }
    Draw.blend(); Draw.reset();
});
const FX_AST_MUSHROOM = new Effect(MUSHROOM_TICKS, 12000, e => { drawMushroom(e.x, e.y, Math.max(e.rotation, 80), e.fin()); });
const FX_BLAST = new Effect(30, 400, e => {
    const sz = Math.max(6, e.rotation);
    Draw.color(Color.white, Color.orange, e.fin()); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, sz * (0.6 + e.finpow()));
    Draw.alpha(e.fout() * 0.8); Fill.circle(e.x, e.y, Math.max(4, sz * 0.5) * e.fout()); Draw.reset();
});
const FX_PURPLE_BURST = new Effect(30, 200, e => {
    Draw.color(e.color, Color.white, e.fout()); Lines.stroke(2 * e.fout()); Lines.poly(e.x, e.y, 6, 4 + 18 * e.finpow(), e.fin() * 90);
    for(let i = 0; i < 4; i++){
        const a = i * 90 + e.fin() * 45;
        Fill.poly(e.x + tx(a, 8 + 16 * e.finpow()), e.y + ty(a, 8 + 16 * e.finpow()), 3, 3 * e.fout(), a);
    }
    Draw.reset();
});
const FX_SHAPE = new Effect(45, 200, e => {
    Draw.color(e.color, Color.white, e.fout()); Lines.stroke(2 * e.fout());
    const sides = 3 + Math.floor(3 * e.fin());
    Lines.poly(e.x, e.y, sides, 4 + 16 * e.finpow(), e.fin() * 180); Lines.poly(e.x, e.y, sides, 2 + 10 * e.finpow(), -e.fin() * 180); Draw.reset();
});
const FX_MIND = new Effect(55, 200, e => {
    const ey = e.y + e.rotation * 0.5 + 8 + 14 * e.fin(), open = e.fslope(), w = 10, n = 10;
    Draw.color(e.color, Color.white, 0.3); Lines.stroke(1.8 * (0.3 + e.fout()));
    for(let sg = -1; sg <= 1; sg += 2) for(let i = 0; i < n; i++){
        const u0 = -1 + 2 * i / n, u1 = -1 + 2 * (i + 1) / n;
        Lines.line(e.x + u0 * w, ey + sg * 5 * open * (1 - u0 * u0), e.x + u1 * w, ey + sg * 5 * open * (1 - u1 * u1));
    }
    Fill.circle(e.x, ey, 2.4 * open); Lines.stroke(2 * e.fout()); Lines.circle(e.x, e.y, e.rotation * 0.9 * e.fout() + 2); Draw.reset();
});
const FX_HYPNOSIS = new Effect(70, 8000, e => {
    for(let k = 0; k < 3; k++){
        const pr = cl(e.fin() * 1.5 - k * 0.18);
        if(pr <= 0) continue;
        Draw.color(e.color, Color.white, 1 - pr); Lines.stroke(4 * (1 - pr)); Lines.poly(e.x, e.y, 6, e.rotation * pr, k * 30);
        Draw.alpha(0.08 * (1 - pr)); Fill.poly(e.x, e.y, 6, e.rotation * pr, k * 30);
    }
    Draw.reset();
});

// ---------- nâng cấp điện ảnh: giữ nét vẽ đường/vòng/đa giác gốc, thêm bloom, tia sáng, tia lửa, sóng xung kích và rung màn hình
const additive = () => { try{ Draw.blend(Packages.arc.graphics.Blending.additive); }catch(err){} };
function bloom(x, y, r, col, a){
    if(r <= 0 || a <= 0) return;
    additive();
    for(let i = 0; i < 4; i++){ Draw.color(col, Color.white, i * 0.24); Draw.alpha(Math.min(1, a * (0.22 + 0.14 * i))); Fill.circle(x, y, r * (1 - i * 0.22)); }
    Draw.blend(); Draw.reset();
}
function flare(x, y, len, w, col, rot, a){
    if(len <= 0 || a <= 0) return;
    additive(); Draw.color(col, Color.white, 0.5); Draw.alpha(a);
    for(let k = 0; k < 2; k++){ const r = rot + k * 90; Drawf.tri(x, y, w, len, r); Drawf.tri(x, y, w, len, r + 180); }
    Draw.blend(); Draw.reset();
}
function sparkBurst(x, y, id, p, n, dist, len, col){
    const fo = 1 - p;
    Draw.color(Color.white, col, p);
    for(let i = 0; i < n; i++){
        const a = rs(id * 7 + i, 0, 360), d = dist * Math.pow(p, 0.55) * rs(id * 3 + i * 5, 0.55, 1);
        Lines.stroke(1.8 * fo + 0.3);
        Lines.lineAngle(x + tx(a, d), y + ty(a, d), a, len * fo + 1);
    }
    Draw.reset();
}
function upgradeFx(fx, o){
    const orig = fx.renderer;
    fx.renderer = cons(e => {
        const c = e.color == null ? Color.white : e.color;
        const R = o.rot ? Math.max(e.rotation, o.r || 30) : (o.r || 30);
        const f0 = o.from || 0, on = e.fin() >= f0, p = on ? (e.fin() - f0) / (1 - f0) : 0, fo = 1 - p;
        if(!on && o.pre) o.pre(e, c);
        if(on){
            if(o.flash && p < 0.22){ const k = 1 - p / 0.22; bloom(e.x, e.y, R * (0.45 + 0.9 * p / 0.22), c, k); flare(e.x, e.y, R * 2.2 * k, Math.max(2, R * 0.1), c, o.flareRot || 0, 0.9 * k); }
            if(o.glow) bloom(e.x, e.y, R * (0.35 + 0.35 * p) * fo + 2, c, 0.75 * fo);
            if(o.shake && p < 0.1 && !Vars.state.isPaused()) Effect.shake(o.shake * (1 - p / 0.1), 8, e.x, e.y);
        }
        orig.get(e);
        if(on){
            if(o.ring){ const f = Math.max(0, (p - 0.12) / 0.88); if(f > 0){ Draw.color(c, Color.white, 1 - f); Draw.alpha(0.8 * (1 - f)); Lines.stroke(2.5 * (1 - f)); Lines.circle(e.x, e.y, R * 1.1 * Math.pow(f, 0.5)); Draw.reset(); } }
            if(o.smoke){ for(let i = 0; i < o.smoke; i++){ const q = Math.min(1, p * 1.2), a = rs(e.id + i * 13, 0, 360), d = R * 0.9 * Math.pow(q, 0.5) * rs(e.id * 3 + i, 0.3, 1);
                Draw.color(Color.gray, Color.darkGray, q); Draw.alpha(0.5 * (1 - q) * (1 - q)); Fill.circle(e.x + tx(a, d), e.y + ty(a, d) + 18 * q, R * (0.12 + 0.22 * q)); } Draw.reset(); }
            if(o.sparks) sparkBurst(e.x, e.y, e.id, p, o.sparks, R * 1.15, o.len || 8, c);
        }
    });
}
const FX_PLASMA = new Effect(6, 1700, e => {
    const fl = 1 + 0.25 * Math.sin(Time.time * 0.9 + e.id), a = e.fout();
    skyBeam(e.x, e.y, 9 * fl, a, SKY_HEIGHT, e.color);
    bloom(e.x, e.y, 38 * fl, e.color, 0.9 * a);
    Draw.color(Color.white, e.color, e.fin()); Draw.alpha(0.9 * a); Lines.stroke(2.2 * a); Lines.circle(e.x, e.y, 14 + 12 * e.fin());
    for(let i = 0; i < 6; i++){ const ang = rs(e.id * 7 + i + Math.floor(Time.time / 3), 0, 360), d = 10 + 26 * e.fin(); Lines.stroke(1.6 * a); Lines.lineAngle(e.x + tx(ang, d), e.y + ty(ang, d), ang, 10 * a + 2); }
    Draw.reset();
});
upgradeFx(FX_SPHERE, {r: 14, glow: 1});
upgradeFx(FX_SHIELD_BREAK, {rot: 1, r: 60, flash: 1, sparks: 14, len: 10, ring: 1, shake: 2});
upgradeFx(FX_MAGNETIC, {r: 26, glow: 1, sparks: 6, len: 6});
upgradeFx(FX_BLADE_SLASH, {r: 20, flash: 1, sparks: 8, len: 12});
upgradeFx(FX_EMP, {rot: 1, r: 80, flash: 1, sparks: 28, len: 16, ring: 1, shake: 5});
upgradeFx(FX_BLASTER, {smoke: 5, r: 50, from: 0.6, flash: 1, sparks: 14, len: 12, ring: 1, shake: 2, flareRot: 90,
    pre: (e, c) => { const head = e.y + 520 * (1 - e.fin() / 0.6); bloom(e.x, head, 22, c, 0.7); flare(e.x, head, 46, 5, c, 90, 0.6); }});
upgradeFx(FX_METEOR, {rot: 1, r: 70, from: 0.6, flash: 1, sparks: 26, len: 18, ring: 1, shake: 5, smoke: 9});
upgradeFx(FX_DEATH_LASER, {rot: 1, r: 40, glow: 1, sparks: 6, len: 8});
upgradeFx(FX_DEATH_BLAST, {smoke: 8, rot: 1, r: 70, flash: 1, sparks: 24, len: 14, ring: 1, shake: 6});
upgradeFx(FX_HEAL, {rot: 1, r: 40, glow: 1, sparks: 8, len: 6});
upgradeFx(FX_BLAST, {smoke: 5, r: 46, flash: 1, sparks: 14, len: 10, ring: 1});
upgradeFx(FX_PURPLE_BURST, {r: 30, flash: 1, sparks: 12, len: 10});
upgradeFx(FX_SHAPE, {r: 30, glow: 1, sparks: 10, len: 8});
upgradeFx(FX_MIND, {r: 30, glow: 1, sparks: 10, len: 8, ring: 1});
upgradeFx(FX_HYPNOSIS, {rot: 1, r: 60, glow: 1, sparks: 16, len: 14, ring: 1});

// ---------- status effect + orb bullet
const slowEffects = [];
for(let i = 0; i < MAX_LEVEL; i++){
    const s = new StatusEffect("thermion-slow-" + (i + 1));
    s.speedMultiplier = 1 - SLOW[i] / 100; s.show = false; s.color = YELLOW_COLOR; slowEffects.push(s);
}
const weakened = new StatusEffect("thermion-weakened");
weakened.speedMultiplier = 0.5; weakened.reloadMultiplier = 0.5; weakened.show = false; weakened.color = BLUE_COLOR;

function makeOrb(dmg){
    const bt = extend(BasicBulletType, 24, dmg, {
        hitEntity(b, entity, health){
            this.super$hitEntity(b, entity, health);
            if(Vars.net.client() || !(entity instanceof Unit)) return;
            if(entity.team == b.team || !entity.isValid()) return;
            const f = bonusFraction(nearestYellowCapacity(b.team, b.x, b.y));
            if(f > 0) entity.damage(entity.maxHealth * f);
        },
        hitTile(b, build, hx, hy, ih, direct){
            this.super$hitTile(b, build, hx, hy, ih, direct);
            if(Vars.net.client() || build == null || build.team == b.team || !build.isValid()) return;
            const f = bonusFraction(nearestYellowCapacity(b.team, b.x, b.y));
            if(f > 0) build.damage(build.maxHealth * f);
        },
        draw(b){ Draw.z(ORB_Z); this.drawTrail(b); drawOrb(b.x, b.y, ORB_R, 1, 2); }
    });
    bt.width = ORB_R * 2; bt.height = ORB_R * 2; bt.hitSize = ORB_R * 1.8; bt.lifetime = 80;
    bt.homingPower = 0.35; bt.homingRange = TS * 400;
    bt.backColor = YELLOW_COLOR; bt.frontColor = Color.white; bt.trailColor = YELLOW_COLOR; bt.trailLength = 24; bt.trailWidth = ORB_R * 0.6;
    const lb = new LightningBulletType();
    lb.damage = dmg / 10; lb.lightningDamage = dmg / 10; lb.lightningColor = YELLOW_COLOR; lb.lightningLength = 7;
    bt.intervalBullet = lb; bt.bulletInterval = 6; bt.intervalBullets = 1; bt.intervalRandomSpread = 360;
    bt.collidesGround = true; bt.collidesAir = true;
    const hs = sndOrbHit(); if(hs != null) bt.hitSound = hs;
    return bt;
}
const orbBullets = [];
for(let i = 0; i < MAX_LEVEL; i++) orbBullets.push(SPH_DMG[i] > 0 ? makeOrb(SPH_DMG[i]) : null);

// ---------- drone
function droneBullet(dmg, maxPct, spd, life, w, h, pierces){
    const bt = extend(BasicBulletType, spd, dmg, {
        hitEntity(b, entity, health){
            this.super$hitEntity(b, entity, health);
            if(maxPct <= 0 || Vars.net.client() || !(entity instanceof Unit)) return;
            if(entity.team == b.team || !entity.isValid()) return;
            entity.damage(entity.maxHealth * Math.random() * maxPct);
        },
        hitTile(b, build, hx, hy, ih, direct){
            this.super$hitTile(b, build, hx, hy, ih, direct);
            if(maxPct <= 0 || Vars.net.client() || build == null || build.team == b.team || !build.isValid()) return;
            build.damage(build.maxHealth * Math.random() * maxPct);
        }
    });
    bt.width = w; bt.height = h; bt.lifetime = life; bt.backColor = PURPLE_COLOR; bt.frontColor = Color.white;
    bt.collidesAir = true; bt.collidesGround = true;
    if(pierces > 1){ bt.pierce = true; bt.pierceBuilding = true; bt.pierceCap = pierces; }
    return bt;
}
function setupDroneBody(t, hp){
    const fl = UnitTypes.flare;
    t.constructor = fl.constructor; t.flying = true; t.health = hp; t.speed = fl.speed; t.accel = fl.accel; t.drag = fl.drag;
    t.rotateSpeed = fl.rotateSpeed; t.hitSize = fl.hitSize; t.engineOffset = fl.engineOffset; t.engineSize = fl.engineSize;
    t.lowAltitude = fl.lowAltitude; t.circleTarget = false; t.singleTarget = true; t.itemCapacity = 0;
}
function droneGun(shot){
    const w = new Weapon();
    w.reload = 50; w.x = 0; w.y = 0; w.top = false; w.mirror = false; w.inaccuracy = 4;
    w.shoot.shots = 12; w.shoot.shotDelay = 2;
    const s = sndMachineGun(); if(s != null) w.shootSound = s;
    w.bullet = shot; return w;
}
// Drone hình ngôi sao 2 cánh: ghi đè draw() nên không dùng sprite của UnitType.
function drawDroneStar(unit, big){
    if(unit.inFogTo(Vars.player.team())) return;
    const len = big ? 22 : 15, w = len * 0.28, rot = 90, z = Draw.z();
    Draw.z(Layer.flyingUnit);
    try{ Draw.blend(Packages.arc.graphics.Blending.additive); }catch(err){}
    Draw.color(PURPLE_COLOR); Draw.alpha(0.55);
    for(let k = 0; k < 3; k++) Drawf.tri(unit.x, unit.y, w * 1.9, len * 1.3, rot + k * 120);
    Draw.color(Color.white, PURPLE_COLOR, 0.45); Draw.alpha(0.95);
    for(let k = 0; k < 3; k++) Drawf.tri(unit.x, unit.y, w, len, rot + k * 120);
    Draw.blend();
    bloom(unit.x, unit.y, len * 0.6, PURPLE_COLOR, 0.6);
    Draw.color(PURPLE_COLOR); Fill.circle(unit.x, unit.y, w * 1.35);   // viền tím quanh hố đen
    Draw.color(Color.black); Fill.circle(unit.x, unit.y, w * 1.05);    // lõi đen tuyền
    Draw.reset(); Draw.z(z);
}
const droneType = extend(UnitType, "thermion-drone", { draw(unit){ drawDroneStar(unit, false); } });
setupDroneBody(droneType, 200);
droneType.weapons.add(droneGun(droneBullet(25, 0, 4, 45, 6, 10, 1)));
const droneV2Type = extend(UnitType, "thermion-drone-v2", { draw(unit){ drawDroneStar(unit, true); } });
setupDroneBody(droneV2Type, 800);
droneV2Type.weapons.add(droneGun(droneBullet(90, 0.01, 4, 45, 6, 10, 1)));
(() => {
    const w = new Weapon();
    w.reload = 60; w.x = 7; w.y = 0; w.top = false; w.mirror = true; w.alternate = true; w.inaccuracy = 0;
    const s = sndDroneLaser(); if(s != null) w.shootSound = s;
    w.bullet = droneBullet(400, 0.15, 7, 40, 8, 28, 7);
    droneV2Type.weapons.add(w);
})();

function makeDroneAI(owner){
    return extend(AIController, {
        updateUnit(){
            if(!owner.isValid() || owner.getForm() != PURPLE){
                if(this.unit != null && this.unit.isValid() && !Vars.net.client()) this.unit.kill();
                return;
            }
            const r = owner.affectRadiusWorld();
            if(this.target != null && dst(this.target.getX(), this.target.getY(), owner.x, owner.y) > r) this.target = null;
            this.super$updateUnit();
        },
        findTarget(x, y, range, air, ground){
            if(!owner.isValid()) return null;
            const r = owner.affectRadiusWorld();
            if(r <= 0) return null;
            return Units.closestTarget(this.unit.team, owner.x, owner.y, r, boolf(u => u.checkTarget(air, ground)), boolf(t => ground));
        },
        updateMovement(){
            if(this.target != null && this.unit.hasWeapons()){
                this.moveTo(this.target, this.unit.type.range * 0.8);
                this.unit.lookAt(this.target);
            }else if(owner.tile != null && owner.tile.build != null){
                this.moveTo(owner.tile.build, thermion.size * TS * 1.5);
            }
        }
    });
}

// ---------- mô tả skill (Info): [tên, level mở khóa, chủ động?, mô tả]
const SKILLS = [
    [
        ["ElectrifiedField", 1, false, "Làm chậm mọi kẻ địch trong phạm vi (10/15/20/30/40% theo cấp); mỗi kẻ địch bị chậm có vòng điện bao quanh.", "Slows every enemy in range (10/15/20/30/40% by level); each slowed enemy is ringed with electricity."],
        ["ElectricSphere", 2, false, "Mỗi kẻ địch trong phạm vi tạo một quả cầu điện ở vị trí ngẫu nhiên trong vòng 15 tile quanh khối. Nạp đủ thì mọi quả bay tới mục tiêu gây sát thương kèm sét. Tối đa 10/15/30/50 quả mỗi lượt (ưu tiên địch gần). Nội tại: mỗi lần trúng gây thêm % máu tối đa của mục tiêu: +0.5% cho mỗi 1 triệu sức chứa pin, tính tới 10 triệu.", "Each enemy in range spawns an electric sphere at a random spot within 15 tiles of the block. Once charged, every sphere flies to its target dealing damage plus lightning. Max 10/15/30/50 spheres per volley (nearest enemies first). Passive: each hit deals extra max-HP% damage: +0.5% per 1 million battery capacity, counted up to 10 million."],
        ["EnergyShield", 3, false, "Khiên bán kính 5/10/15 tile (Lv3/4/5) chặn đạn địch và đẩy đơn vị địch ra ngoài. Máu khiên = 50/75/100% dung tích pin của lưới. Mới xuất hiện hoặc hồi sau khi vỡ chỉ có 10% máu, tự sửa mỗi 2 giây bằng điện trong pin (tối đa 10% sức chứa lưới mỗi lần).", "Shield of 5/10/15 tiles radius (Lv3/4/5) that blocks enemy bullets and pushes enemy units outward. Shield HP = 50/75/100% of the grid's battery capacity. A fresh or recovered shield starts with only 10% HP and repairs itself every 2 seconds using battery power (up to 10% of grid capacity each time)."],
        ["MagneticField", 4, false, "Mỗi giây gây sát thương chuẩn 1.5% (Lv5: 2%) máu tối đa lên mọi kẻ địch trong phạm vi.", "Every second deals true damage equal to 1.5% (Lv5: 2%) of max HP to every enemy in range."],
        ["DualBlade", 4, false, "Chế tạo 1 lưỡi cưa (Lv5: 2 lưỡi) tự bay tới kẻ địch gần nhất, bám vào cắt 4 nhát/giây rồi tìm mục tiêu mới. Nội tại giống ElectricSphere nhưng hiệu quả bằng một nửa.", "Builds 1 saw blade (Lv5: 2) that flies to the nearest enemy, latches on and cuts 4 times per second, then picks a new target. Same passive as ElectricSphere at half effectiveness."],
        ["ElectromagneticPulse", 5, true, "Chủ động: vô hiệu hóa mọi đơn vị và công trình địch trong phạm vi 5 giây. Hồi 120 giây.", "Active: disables all enemy units and buildings in range for 5 seconds. Cooldown 120 seconds."]
    ],
    [
        ["Blaster", 1, false, "Mỗi giây, mỗi kẻ địch có xác suất bị khóa và hứng 1..level tia laser từ trời (cách nhau 15 tick). Mỗi tia = sát thương gốc x (1 + số tia/5); tia cuối thêm 0.5 x level% máu tối đa. Địch xung quanh nhận 1/3 sát thương.", "Every second, each enemy has a chance to be locked on and hit by 1..level orbital laser beams (15 ticks apart). Each beam = base damage x (1 + beams/5); the last beam adds 0.5 x level% of max HP. Nearby enemies take 1/3 of the damage."],
        ["Meteor", 2, false, "Mỗi giây, mỗi kẻ địch có xác suất bị 3..level x 3 thiên thạch rơi xuống; mỗi quả nổ bán kính 4 tile (% máu tối đa + sát thương số), để lại 3 vũng (75% xỉ, 25% dầu).", "Every second, each enemy has a chance to be hit by 3..level x 3 meteors; each explodes in a 4-tile radius (max-HP% + flat damage) and leaves 3 puddles (75% slag, 25% oil)."],
        ["DeathLaser", 3, false, "Khóa mục tiêu bằng tia laser từ trời bám theo và gây sát thương mỗi giây (địch khác trong vùng nhận 1/2). Quanh tia (2 x level tile): đơn vị địch giảm 50% hoạt động, công trình chậm 50%. Hết tia thì nổ gây (2..5) x level% máu tối đa.", "Locks onto a target with a tracking orbital laser that deals damage every second (other enemies in the area take 1/2). Around the beam (2 x level tiles): enemy units are 50% less active and buildings 50% slower. When the beam ends it explodes for (2..5) x level% of max HP."],
        ["Limitless", 4, false, "Trong vùng 1/3 phạm vi, mọi đạn không cùng team mất 5% (Lv5: 10%) tốc độ ban đầu mỗi giây.", "Within 1/3 of the range, every non-allied bullet loses 5% (Lv5: 10%) of its initial speed per second."],
        ["RCT", 4, false, "Định kỳ (15 giây, Lv5: 10 giây) hồi 50% (Lv5: 100%) máu tối đa.", "Periodically (every 15 s, Lv5: 10 s) restores 50% (Lv5: 100%) of max HP."],
        ["Asteroid", 5, true, "Chủ động: gồng 1 phút (khóa đổi dạng và phạm vi, địch trong phạm vi bị đóng băng, đạn địch đứng yên, không phụ thuộc lưới điện), rồi tiểu hành tinh bay tới giữa bản đồ tiêu diệt mọi đơn vị/công trình ngoài phạm vi (trừ Thermion). Sau đó vào dạng Death 10 phút rồi trở về Blue. Hồi 300 giây.", "Active: charge for 1 minute (locks form change and range, enemies in range are frozen, enemy bullets stand still, independent of the power grid), then an asteroid flies to the map center and destroys every unit/building outside the range (except Thermion). Afterwards enters Death form for 10 minutes, then returns to Blue. Cooldown 300 seconds."]
    ],
    [
        ["Illusion", 1, false, "Định kỳ mỗi kẻ địch có xác suất rơi vào ảo giác, bị vô hiệu hóa (thời gian giảm theo giáp: x(1 - giáp/100)).", "Periodically each enemy has a chance to fall into an illusion and be disabled (duration reduced by armor: x(1 - armor/100))."],
        ["Drone", 2, false, "Định kỳ triệu hồi 1 drone (không giới hạn). Lv2-3: Drone thường (200 máu); Lv4+: Drone V2 (800 máu, súng máy + 2 laser xuyên). Drone biến mất khi Thermion bị phá hủy hoặc rời dạng Purple.", "Periodically summons 1 drone (unlimited). Lv2-3: normal Drone (200 HP); Lv4+: Drone V2 (800 HP, machine gun + 2 piercing lasers). Drones vanish when Thermion is destroyed or leaves Purple form."],
        ["Shapeshifter", 3, false, "Định kỳ mỗi kẻ địch có xác suất bị khóa; sau 2 giây một đơn vị đồng minh cùng loại với chỉ số y hệt xuất hiện tại đó.", "Periodically each enemy has a chance to be marked; after 2 seconds an allied unit of the same type with identical stats appears there."],
        ["Hypnosis", 3, false, "Bị động: khi bị phá hủy ở dạng Purple, mọi đơn vị địch trong phạm vi thành đồng minh.", "Passive: when destroyed in Purple form, all enemy units in range become allies."],
        ["MindControl", 4, false, "Định kỳ mỗi kẻ địch có xác suất bị biến thành đồng minh.", "Periodically each enemy has a chance to be turned into an ally."],
        ["Mega Hypnosis", 5, true, "Chủ động: kích hoạt Hypnosis không cần bị phá hủy. Hồi 180 giây.", "Active: triggers Hypnosis without needing to be destroyed. Cooldown 180 seconds."]
    ]
];
const FORM_ROLE = [
    ["Phòng thủ bằng điện. Làm chậm và giật điện kẻ địch, dựng khiên chặn đạn và dùng lưỡi cưa xẻ mục tiêu. Có phần thưởng máu tối đa lớn nhất.", "Electric defense. Slows and shocks enemies, raises a bullet-blocking shield and shreds targets with saw blades. Has the largest max-HP bonus."],
    ["Pháo kích từ quỹ đạo. Laser, thiên thạch và tia tử thần giáng từ trời, đạn địch bị làm chậm và Thermion tự hồi máu. Level 5 mở khóa Asteroid.", "Orbital bombardment. Lasers, meteors and death beams strike from the sky, enemy bullets are slowed and Thermion heals itself. Level 5 unlocks Asteroid."],
    ["Kiểm soát và triệu hồi. Vô hiệu hóa, chiêu mộ và nhân bản kẻ địch, triệu hồi drone chiến đấu. Khi bị phá hủy sẽ chiêu mộ mọi thứ xung quanh.", "Control and summoning. Disables, recruits and clones enemies, and summons combat drones. When destroyed it recruits everything around it."]
];

// ---------- UI helper
const dialogWidth = () => Math.min(480, Core.graphics.getWidth() / Scl.scl(1) * 0.92);
function dialogPane(d, build){ d.cont.pane(Styles.noBarPane, cons(build)).size(dialogWidth(), Core.graphics.getHeight() / Scl.scl(1) * 0.62); }
function wrap(t, text, w){ const c = t.add(text).width(w).left(); c.get().setWrap(true); return c; }
function section(t, title){
    t.table(cons(h => { h.add("[#" + Pal.accent + "]" + title + "[]").left().row(); h.image().color(Pal.accent).height(3).growX(); })).left().padTop(14).padBottom(6).row();
}
function addBack(d){ d.addCloseButton(); d.buttons.clear(); d.buttons.button(T("Trở về", "Back"), Styles.flatBordert, run(() => d.hide())).size(210, 64); }

// ---------- thermion-glow: phát sáng theo màu dạng + effect theo dạng
let glowRegion = null;
const glowCol = new Color();
const getGlow = () => { if(glowRegion == null) glowRegion = Core.atlas.find("newex-thermion-glow"); return glowRegion; };
function drawGlow(x, y, c, a, sc){
    const r = getGlow();
    if(r == null || !r.found()) return;
    let w = 64; try{ w = r.width * r.scl(); }catch(err){}
    w *= sc; Draw.color(c); Draw.alpha(Math.min(1, Math.max(0, a))); Draw.rect(r, x, y, w, w); Draw.reset();
}
// Blue: mỗi giây zoom to lên chút rồi tan
const FX_GLOW_ZOOM = new Effect(50, 400, e => { drawGlow(e.x, e.y, e.color, 0.7 * e.fout(), 1 + 0.18 * e.finpow()); });
// Yellow: tia từ ngoài bay vào tâm lõi rồi tan
const FX_GLOW_IN = new Effect(50, 400, e => {
    const d = 52 * e.fout(), px = e.x + tx(e.rotation, d), py = e.y + ty(e.rotation, d);
    additive(); Draw.color(Color.gold, Color.white, e.fin()); Lines.stroke(2.2 * e.fout() + 0.4);
    Lines.lineAngle(px, py, e.rotation + 180, 6 + 10 * e.fout()); Fill.circle(px, py, 2.2 * e.fout() + 0.5);
    Draw.blend(); Draw.reset();
});
// Purple: lõi thu nhỏ lại rồi trôi tới chỗ drone spawn. rotation = góc(từ drone về lõi) + 360 * khoảng cách
const FX_GLOW_DRONE = new Effect(45, 400, e => {
    const ang = e.rotation % 360, dist = Math.floor(e.rotation / 360), cx = e.x + tx(ang, dist), cy = e.y + ty(ang, dist);
    const mv = cl((e.fin() - 0.3) / 0.7), sc = lerp(1, 0.25, cl(e.fin() / 0.5));
    drawGlow(lerp(cx, e.x, mv * mv * (3 - 2 * mv)), lerp(cy, e.y, mv * mv * (3 - 2 * mv)), e.color, 0.75 * (1 - cl((e.fin() - 0.8) / 0.2)), sc);
});

// ---------- cutscene khi lên level 5: pháo bắn tới 4 góc map -> zoom toàn map -> quét lưới 16x16 ô
const CS = {pending: null, age: 0, on: false, stage: 0, t: 0, ox: 0, oy: 0, sx: 0, sy: 0, col: Color.sky, stars: [0, 0, 0, 0], oldScale: 1, oldMin: 1.5, grid: 0};
const CS_PAN = 100, CS_HOLD = 50, CS_ZOOM = 200, CS_GRID = 420, CS_END = 90, CELL = 16;
const csCorner = i => { const W = Vars.world.unitWidth(), H = Vars.world.unitHeight(), m = 4 * TS; return [[m, m], [W - m, m], [W - m, H - m], [m, H - m]][i]; };
const csFit = () => Math.min(Core.graphics.getWidth() / Vars.world.unitWidth(), Core.graphics.getHeight() / Vars.world.unitHeight()) * 0.92;
const smooth = p => { p = cl(p); return p * p * (3 - 2 * p); };
function startCutscene(x, y, col){ if(!CS.on && !Vars.headless) CS.pending = [x, y, col]; }
function beginCutscene(x, y, col){
    CS.on = true; CS.age = 0; CS.stage = 0; CS.t = 0; CS.ox = x; CS.oy = y; CS.sx = Core.camera.position.x; CS.sy = Core.camera.position.y;
    CS.col = col; CS.stars = [0, 0, 0, 0]; CS.grid = 0;
    try{ CS.oldScale = Vars.renderer.getScale(); CS.oldMin = Vars.renderer.minZoom; Vars.renderer.minZoom = 0.01; }catch(err){}
    try{ Vars.control.input.logicCutscene = true; }catch(err){}
}
function endCutscene(){
    CS.on = false;
    try{ Vars.control.input.logicCutscene = false; Vars.renderer.minZoom = CS.oldMin; Vars.renderer.setScale(CS.oldScale); }catch(err){}
}
function csCam(x, y){ Core.camera.position.set(x, y); }
function csUpdate(){
    if(CS.pending != null && Vars.state.isGame() && !Vars.state.isPaused() && !Core.scene.hasDialog()){ const p = CS.pending; CS.pending = null; beginCutscene(p[0], p[1], p[2]); }
    if(!CS.on) return;
    if(!Vars.state.isGame()){ endCutscene(); return; }
    CS.t += Time.delta; CS.age += Time.delta;
    const st = CS.stage, W = Vars.world.width(), H = Vars.world.height();
    if(st < 4){
        const c = csCorner(st);
        if(CS.t < CS_PAN){
            const p = smooth(CS.t / CS_PAN);
            csCam(lerp(CS.sx, c[0], p), lerp(CS.sy, c[1], p));
        }else{
            csCam(c[0], c[1]);
            if(CS.stars[st] == 0){ CS.stars[st] = 0.001; FX_BLAST.at(c[0], c[1], 70, CS.col); Effect.shake(6, 12, c[0], c[1]); }
            CS.stars[st] = Math.min(1, CS.stars[st] + Time.delta / 25);
            if(CS.t >= CS_PAN + CS_HOLD){ CS.stage++; CS.t = 0; CS.sx = c[0]; CS.sy = c[1]; }
        }
    }else if(st == 4){
        const p = smooth(CS.t / CS_ZOOM);
        csCam(lerp(CS.sx, Vars.world.unitWidth() / 2, p), lerp(CS.sy, Vars.world.unitHeight() / 2, p));
        try{ Vars.renderer.setScale(lerp(CS.oldScale, csFit(), p)); }catch(err){}
        if(CS.t >= CS_ZOOM + 40){ CS.stage = 5; CS.t = 0; }
    }else if(st == 5){
        csCam(Vars.world.unitWidth() / 2, Vars.world.unitHeight() / 2);
        const total = Math.ceil(W / CELL) * Math.ceil(H / CELL);
        CS.grid = Math.floor(total * cl(CS.t / CS_GRID));
        if(CS.t >= CS_GRID){ CS.grid = total; CS.stage = 6; CS.t = 0; }
    }else if(st == 6){
        if(CS.t >= CS_END){ CS.stage = 7; CS.t = 0; CS.sx = Vars.world.unitWidth() / 2; CS.sy = Vars.world.unitHeight() / 2; }
    }else{
        const p = smooth(CS.t / 90);
        csCam(lerp(CS.sx, CS.ox, p), lerp(CS.sy, CS.oy, p));
        try{ Vars.renderer.setScale(lerp(csFit(), CS.oldScale, p)); }catch(err){}
        if(CS.t >= 90) endCutscene();
    }
    try{ if(CS.age > 60 && !Core.scene.hasDialog() && Core.input.keyTap(Packages.arc.input.KeyCode.escape)) endCutscene(); }catch(err){}
}
function drawStar4(x, y, len, a, c){
    if(len <= 0) return;
    additive(); Draw.color(c); Draw.alpha(0.5 * a);
    for(let k = 0; k < 4; k++) Drawf.tri(x, y, len * 0.2, len * 1.2, 90 * k + 45 * 0);
    Draw.color(Color.white, c, 0.3); Draw.alpha(a);
    for(let k = 0; k < 4; k++) Drawf.tri(x, y, len * 0.11, len, 90 * k);
    Draw.blend(); bloom(x, y, len * 0.6, c, 0.8 * a); Draw.color(Color.white); Fill.circle(x, y, len * 0.08); Draw.reset();
}
function csDraw(){
    if(!CS.on) return;
    const z = Draw.z(); Draw.z(Layer.overlayUI);
    const st = CS.stage, big = st >= 4 ? 3 : 1;
    // tia bắn từ pháo tới góc đang lia camera
    if(st < 4 && CS.t < CS_PAN + 20){
        const c = csCorner(st), p = smooth(CS.t / CS_PAN), hx = lerp(CS.ox, c[0], p), hy = lerp(CS.oy, c[1], p);
        beamTo(CS.ox, CS.oy, hx, hy, 4 * (1 - 0.5 * p), CS.col); bloom(hx, hy, 26, CS.col, 0.9);
    }
    for(let i = 0; i < 4; i++){
        if(CS.stars[i] <= 0) continue;
        const c = csCorner(i), pop = CS.stars[i], len = (TS * 7) * big * (1 - Math.pow(1 - pop, 3)) * (1 + 0.08 * Math.sin(Time.time * 0.1 + i));
        drawStar4(c[0], c[1], len, 1, CS.col);
    }
    // nối 4 ngôi sao khi zoom toàn map
    if(st >= 4){
        const lp = st == 4 ? smooth(CS.t / CS_ZOOM) : 1;
        for(let i = 0; i < 4; i++){
            const a = csCorner(i), b = csCorner((i + 1) % 4);
            beamTo(a[0], a[1], lerp(a[0], b[0], lp), lerp(a[1], b[1], lp), 3 * big, CS.col);
        }
    }
    // lưới khu vực 16x16 ô, hiện lần lượt
    if(st >= 5){
        const W = Vars.world.width(), H = Vars.world.height(), cols = Math.ceil(W / CELL), rows = Math.ceil(H / CELL), n = Math.min(CS.grid, cols * rows);
        Lines.stroke(2);
        for(let k = 0; k < n; k++){
            const cx = k % cols, cy = Math.floor(k / cols), x0 = cx * CELL * TS, y0 = cy * CELL * TS, w = Math.min(CELL, W - cx * CELL) * TS, h = Math.min(CELL, H - cy * CELL) * TS;
            const fresh = cl(1 - (n - k) / 40);
            Draw.color(CS.col); Draw.alpha(0.12 + 0.3 * (1 - fresh)); Fill.rect(x0 + w / 2, y0 + h / 2, w, h);
            Draw.color(CS.col, Color.white, 0.4 * (1 - fresh)); Draw.alpha(0.5 + 0.4 * (1 - fresh)); Lines.rect(x0, y0, w, h);
        }
        Draw.reset();
    }
    Draw.reset(); Draw.z(z);
}
Events.run(TRIG.update, run(csUpdate));
// Block được khai báo khai báo bằng content/blocks/thermion.hjson (type: Block, size, health,
// category, requirements, buildVisibility...). Ở đây chỉ lấy lại content đó và gắn thêm
// bar + buildType tùy biến (không cần extend(Block, ...) nữa).
let thermion = null;
let thermionBuild = null;
// block từ hjson chỉ tồn tại sau khi content load xong -> gắn bar + buildType trong ContentInitEvent
Events.on(Packages.mindustry.game.EventType.ContentInitEvent, cons(e => {
    thermion = Vars.content.block("newex-thermion");
    if(thermion == null){ Log.err("[Thermion] không tìm thấy block newex-thermion — kiểm tra content/blocks/thermion.hjson"); return; }
    const S = b => SELF[b.id];
    thermion.addBar("thermion-form", func(b => new Bar(prov(() => S(b) ? S(b).formBarText() : ""), prov(() => S(b) ? S(b).formBarColor() : GRAY_COLOR), floatp(() => S(b) ? S(b).formBarFraction() : 0))));
    thermion.addBar("thermion-range", func(b => new Bar(prov(() => T("Phạm vi: ", "Range: ") + (S(b) ? S(b).rangeBarValue().toFixed(1) : "0")), prov(() => S(b) ? formColor(S(b).getForm()) : GRAY_COLOR), floatp(() => S(b) ? cl(S(b).rangeBarValue() / RANGE_REF) : 0))));
    thermion.addBar("thermion-storage", func(b => new Bar(prov(() => S(b) ? S(b).storageBarText() : ""), prov(() => S(b) ? S(b).storageBarColor() : GRAY_COLOR), floatp(() => S(b) ? S(b).storageBarFraction() : 0))));
    thermion.addBar("thermion-cooldown", func(b => new Bar(prov(() => S(b) ? S(b).cooldownBarText() : ""), prov(() => S(b) ? formColor(S(b).getForm()) : GRAY_COLOR), floatp(() => S(b) ? S(b).cooldownBarFraction() : 0))));
    thermion.buildType = thermionBuild;
}));

// vẽ lưỡi cưa trong hook toàn cục (không bị ẩn khi khối ngoài màn hình)
Events.run(TRIG.draw, run(() => {
    if(!Vars.state.isGame()) return;
    for(var k in SELF){
        const b = SELF[k];
        if(!b.isValid()){ delete SELF[k]; continue; }
        b.drawBlades();
    }
}));

thermionBuild = prov(() => {
    const st = {
    level: 1, form: YELLOW, pendingForm: -1, formChangeTimer: 0, formCooldown: 0,
    timers: [0, 0, 0, 0, 0, 0, 0, 0, 0], secondTimer: 0, secTick: false,
    sphereCharge: 0, orbCount: 0, shield: 0, shieldMax: 0, shieldCooldown: 0, shieldRegenTimer: 0, shieldHitCd: 0, shieldDirty: true,
    blades: [0, 0], bladeX: [0, 0], bladeY: [0, 0], bladeHit: [0, 0], bladeInit: [false, false], bladeTarget: [null, null],
    orbSeed: 0, skillCd: 1, empActiveTimer: 0, upgrading: false, paid: [0, 0, 0, 0, 0, 0, 0, 0],
    plasmas: [], plasmaCd: 0, lasers: [], asteroidPhase: 0, asteroidTimer: 0, asteroidRadius: 0, asteroidTx: 0, asteroidTy: 0,
    deathTimer: 0, chargeLoopTimer: 0, rangeShown: 0, rangeStamp: -1, drones: [], savedDroneIds: [], ringRadius: 0, ringStamp: -1000,

    };
    return extend(Building, {
        getForm(){ return st.form; },
    // ---------- điện / phạm vi
    totalPowerProduction(){ return (this.power != null && this.power.graph != null) ? this.power.graph.getLastPowerProduced() * 60 : 0; },
    totalPowerUse(){ return (this.power != null && this.power.graph != null) ? this.power.graph.getLastPowerNeeded() * 60 : 0; },
    hasEnoughPower(){ if(st.asteroidPhase != AST_NONE) return true; return this.totalPowerProduction() >= powerRequirement(st.level); },
    powerNet(){ return this.totalPowerProduction() - this.totalPowerUse(); },
    gridCapacity(){ return (this.power != null && this.power.graph != null) ? this.power.graph.getTotalBatteryCapacity() : 0; },
    gridStored(){ return (this.power != null && this.power.graph != null) ? this.power.graph.getBatteryStored() : 0; },
    liveAffectRadius(){ return Math.max(0, this.powerNet() / (100 * st.level)); },
    affectRadius(){ return st.asteroidPhase != AST_NONE ? st.asteroidRadius / TS : this.liveAffectRadius(); },
    affectRadiusWorld(){ return this.affectRadius() * TS; },
    rangeBarValue(){
        const now = Time.millis();
        if(st.rangeStamp < 0 || now < st.rangeStamp || now - st.rangeStamp >= 1000){ st.rangeStamp = now; st.rangeShown = this.affectRadius(); }
        return st.rangeShown;
    },
    nexMaxHealth(){ return thermion.health * (1 + formHealthBonus(st.form, st.level) / 100); },
    maxHealth(){ return this.nexMaxHealth(); },

    // ---------- cấp / dạng
    setLevel(n){
        n = Math.min(Math.max(n, 1), MAX_LEVEL);
        if(n == st.level) return;
        const frac = this.healthf(), leveledUp = n > st.level;
        st.level = n; this.clearUpgrade();
        if(leveledUp){
            NexNet.fx(n >= MAX_LEVEL ? Fx.bigShockwave : Fx.upgradeCore, this.x, this.y);
            NexNet.fx(Fx.mineHuge, this.x, this.y);
            NexNet.shake(6, 6, this.x, this.y);
            if(n >= MAX_LEVEL) startCutscene(this.x, this.y, formColor(st.form));
        }
        this.health = Math.max(1, frac * this.nexMaxHealth());
        this.resetSkillStates();
    },
    canChangeForm(to){
        return to >= YELLOW && to <= PURPLE && st.form != DEATH && to != st.form && st.pendingForm < 0 &&
            st.asteroidPhase == AST_NONE && st.formCooldown <= 0 && this.hasEnoughPower();
    },
    requestForm(to){
        if(Vars.net.client() || !this.canChangeForm(to)) return;
        st.pendingForm = to; st.formChangeTimer = val(FORM_TIME, st.level) * 60;
    },
    tickFormChange(){
        if(st.pendingForm < 0) return;
        st.formChangeTimer -= Time.delta;
        if(st.formChangeTimer <= 0){ const to = st.pendingForm; this.setForm(to); st.formCooldown = val(FORM_CD, st.level) * 60; }
    },
    setForm(to){
        if(to < YELLOW || to > DEATH) return;
        const frac = this.healthf();
        if(st.form == PURPLE && to != PURPLE) this.killDrones();
        st.form = to; st.plasmas = []; st.plasmaCd = 0; st.pendingForm = -1; st.formChangeTimer = 0; st.asteroidPhase = AST_NONE;
        if(to == DEATH) st.deathTimer = DEATH_TICKS;
        this.health = Math.max(1, frac * this.nexMaxHealth());
        this.resetSkillStates();
    },
    killDrones(){
        this.restoreDrones();
        for(let i = 0; i < st.drones.length; i++){ const d = st.drones[i]; if(d != null && d.isValid()) d.kill(); }
        st.drones = [];
    },
    restoreDrones(){
        if(st.savedDroneIds.length == 0) return;
        for(let i = 0; i < st.savedDroneIds.length; i++){
            const d = Groups.unit.getByID(st.savedDroneIds[i]);
            if(d != null && d.isValid() && d.team == this.team && st.drones.indexOf(d) < 0){ d.controller(makeDroneAI(this)); st.drones.push(d); }
        }
        st.savedDroneIds = [];
    },
    resetSkillStates(){
        st.sphereCharge = 0; st.orbCount = 0; st.shield = 0; st.shieldCooldown = 0; st.shieldRegenTimer = 0; st.shieldHitCd = 0;
        st.shieldDirty = true; st.blades[0] = 0; st.blades[1] = 0; st.bladeInit[0] = false; st.bladeInit[1] = false;
        st.bladeTarget[0] = null; st.bladeTarget[1] = null; st.lasers = [];
        for(let i = 0; i < st.timers.length; i++) st.timers[i] = 0;
    },
    every(i, ticks){
        if(ticks <= 0) return false;
        st.timers[i] += Time.delta;
        if(st.timers[i] >= ticks){ st.timers[i] -= ticks; return true; }
        return false;
    },

    // ---------- mục tiêu / tiện ích
    unitsAround(cx, cy, r){
        const out = [];
        Units.nearbyEnemies(this.team, cx - r, cy - r, r * 2, r * 2, cons(u => { if(u.isValid() && u.within(cx, cy, r + u.hitSize / 2)) out.push(u); }));
        return out;
    },
    enemyUnits(r){ return r <= 0 ? [] : this.unitsAround(this.x, this.y, r); },
    cachedEnemies(r){ if(st.ceStamp === undefined || Time.time < st.ceStamp || Time.time - st.ceStamp >= 6){ st.ceStamp = Time.time; st.ceList = this.enemyUnits(r); } return st.ceList.filter(u => u.isValid()); },
    buildingsAround(cx, cy, r){
        const out = [], tm = this.team;
        if(r <= 0) return out;
        Vars.indexer.allBuildings(cx, cy, r, cons(b => { if(b.team != tm && b.team != Team.derelict) out.push(b); }));
        return out;
    },
    enemyBuildings(r){ return this.buildingsAround(this.x, this.y, r); },
    fx(e, ex, ey, rot, c){ if(Vars.net.server()) Call.effect(e, ex, ey, rot, c); else e.at(ex, ey, rot, c); },
    sfx(s, sx, sy, vol, pitch){
        if(s == null) return;
        if(isAssetSound(s)){ if(!Vars.headless) s.at(sx, sy, pitch, vol); }
        else if(Vars.net.server()) Call.soundAt(s, sx, sy, vol, pitch); else s.at(sx, sy, pitch, vol);
    },
    stun(u, ticks){ u.apply(StatusEffects.unmoving, ticks); u.apply(StatusEffects.disarmed, ticks); },
    disableBuilding(b, ticks){ b.applySlowdown(0, ticks); },

    // ---------- khiên chặn damage
    handleDamage(amount){
        if(st.form == YELLOW && st.level >= 3 && st.shieldCooldown <= 0 && st.shield > 0 && this.hasEnoughPower()){
            const ab = Math.min(st.shield, amount);
            st.shield -= ab; amount -= ab;
            if(st.shield <= 0){
                st.shield = 0; st.shieldCooldown = val(SH_CD, st.level);
                this.fx(FX_SHIELD_BREAK, this.x, this.y, this.shieldRadius(), YELLOW_COLOR);
                this.sfx(sndShieldBreak(), this.x, this.y, 1.5, 1);
            }
        }
        return amount;
    },

    // ---------- vòng đời
    updateTile(){
        SELF[this.id] = this;
        if(Vars.net.client()) return;
        if(st.form == DEATH){ this.updateDeath(); return; }
        this.restoreDrones();
        if(st.asteroidPhase != AST_NONE){ this.updateAsteroid(); return; }
        if(!this.hasEnoughPower()) return;
        if(st.formCooldown > 0) st.formCooldown -= Time.delta;
        if(st.empActiveTimer > 0) st.empActiveTimer -= Time.delta;
        st.secTick = false;
        st.secondTimer += Time.delta;
        if(st.secondTimer >= 60){ st.secondTimer -= 60; st.secTick = true; }
        if(st.pendingForm >= 0){ this.tickFormChange(); return; }
        if(st.secTick){
            if(st.form == BLUE) this.fx(FX_GLOW_ZOOM, this.x, this.y, 0, BLUE_COLOR);
            else if(st.form == YELLOW) for(let i = 0; i < 8; i++) this.fx(FX_GLOW_IN, this.x, this.y, rnd(0, 360), YELLOW_COLOR);
        }
        if(st.skillCd < 1) st.skillCd = Math.min(1, st.skillCd + Time.delta / this.skillCooldownTicks(st.form));
        const lv = st.level;
        if(st.form == YELLOW){
            this.updateElectrifiedField();
            if(lv >= 2) this.updateElectricSphere();
            if(lv >= 3) this.updateEnergyShield();
            if(lv >= 4){ this.updateMagneticField(); this.updateDualBlade(); }
        }else if(st.form == BLUE){
            this.updateBlaster();
            if(lv >= 2) this.updateMeteor();
            if(lv >= 3) this.updateDeathLaser();
            if(lv >= 4){ this.updateLimitless(); this.updateRCT(); }
        }else if(st.form == PURPLE){
            this.updateIllusion();
            if(lv >= 2) this.updateDrone();
            if(lv >= 3) this.updateShapeshifter();
            if(lv >= 4) this.updateMindControl();
        }
    },
    onDestroyed(){
        if(!Vars.net.client() && st.form == PURPLE && st.level >= 3) this.hypnosis();
        if(!Vars.net.client()) this.killDrones();
        this.super$onDestroyed();
    },
    onRemoved(){
        if(!Vars.net.client()) this.killDrones();
        delete SELF[this.id];
        this.super$onRemoved();
    },
    updateDeath(){
        st.deathTimer -= Time.delta;
        if(st.deathTimer <= 0){ st.deathTimer = 0; this.setForm(BLUE); this.fx(FX_HEAL, this.x, this.y, thermion.size * TS, BLUE_COLOR); }
    },
    version(){ return 2; },
    write(w){
        this.super$write(w);
        w.b(st.level); w.b(st.form); w.b(st.pendingForm); w.f(st.formChangeTimer); w.f(st.formCooldown);
        w.f(st.shield); w.f(st.shieldCooldown); w.f(st.skillCd); w.f(st.empActiveTimer);
        w.b(st.asteroidPhase); w.f(st.asteroidTimer); w.f(st.asteroidRadius); w.f(st.asteroidTx); w.f(st.asteroidTy);
        for(let i = 0; i < 2; i++){ w.f(st.blades[i]); w.f(st.bladeX[i]); w.f(st.bladeY[i]); }
        w.s(st.orbCount); w.f(st.sphereCharge); w.i(st.orbSeed);
        st.drones = st.drones.filter(d => d != null && d.isValid());
        w.s(st.drones.length);
        for(let i = 0; i < st.drones.length; i++) w.i(st.drones[i].id);
        w.bool(st.upgrading);
        for(let i = 0; i < MAX_COST_ITEMS; i++) w.i(st.paid[i]);
        w.f(st.deathTimer);
    },
    read(r, revision){
        this.super$read(r, revision);
        st.level = Math.min(Math.max(r.b(), 1), MAX_LEVEL); st.form = r.b(); st.pendingForm = r.b();
        st.formChangeTimer = r.f(); st.formCooldown = r.f(); st.shield = r.f(); st.shieldCooldown = r.f();
        st.skillCd = r.f(); st.empActiveTimer = r.f();
        st.asteroidPhase = r.b(); st.asteroidTimer = r.f(); st.asteroidRadius = r.f(); st.asteroidTx = r.f(); st.asteroidTy = r.f();
        for(let i = 0; i < 2; i++){ st.blades[i] = r.f(); st.bladeX[i] = r.f(); st.bladeY[i] = r.f(); }
        st.orbCount = r.s(); st.sphereCharge = r.f(); st.orbSeed = r.i();
        const dc = r.s();
        st.savedDroneIds = [];
        for(let i = 0; i < dc; i++) st.savedDroneIds.push(r.i());
        if(revision >= 1){ st.upgrading = r.bool(); for(let i = 0; i < MAX_COST_ITEMS; i++) st.paid[i] = r.i(); }
        else this.clearUpgrade();
        if(revision >= 2) st.deathTimer = r.f(); else if(st.form == DEATH) st.deathTimer = DEATH_TICKS;
        st.shieldDirty = false;
    },

    // ============ YELLOW
    updateElectrifiedField(){
        if(!this.every(T_FIELD, 5)) return;
        const s = slowEffects[st.level - 1], list = this.enemyUnits(this.affectRadiusWorld());
        for(let i = 0; i < list.length; i++) list[i].apply(s, 12);
    },
    orbPos(i){
        const sd = st.orbSeed * 977 + i * 2, a = rs(sd, 0, 360), r = SPH_SPAWN_R * TS * Math.sqrt(rs(sd + 1, 0, 1));
        return [this.x + tx(a, r), this.y + ty(a, r)];
    },
    updateElectricSphere(){
        let enemies = this.cachedEnemies(this.affectRadiusWorld());
        const cap = val(SPH_MAX, st.level), self = this;
        if(enemies.length > cap){
            enemies.sort((a, b) => a.dst2(self.x, self.y) - b.dst2(self.x, self.y));
            enemies = enemies.slice(0, cap);
        }
        st.orbCount = enemies.length;
        if(st.orbCount == 0){ st.sphereCharge = 0; return; }
        st.sphereCharge += Time.delta;
        if(st.sphereCharge < val(SPH_CHARGE, st.level)) return;
        st.sphereCharge = 0;
        const bt = orbBullets[st.level - 1];
        if(bt == null) return;
        for(let i = 0; i < st.orbCount; i++){
            const t = enemies[i], p = this.orbPos(i), ang = Math.atan2(t.y - p[1], t.x - p[0]) / DEG;
            const travel = dst(p[0], p[1], t.x, t.y) / bt.speed, lifeScl = Math.max(1.5, travel * 1.8 / bt.lifetime);
            bt.createNet(this.team, p[0], p[1], ang, -1, 1, lifeScl);
            this.fx(FX_SPHERE, p[0], p[1], ang, YELLOW_COLOR);
        }
        this.sfx(sndOrbShoot(), this.x, this.y, 1.2, rnd(0.95, 1.05));
        st.orbSeed++;
    },
    shieldRadius(){ return val(SH_RADIUS, st.level) * TS; },
    updateEnergyShield(){
        const capacity = this.gridCapacity(), self = this;
        st.shieldMax = val(SH_PCT, st.level) / 100 * capacity;
        if(st.shieldHitCd > 0) st.shieldHitCd -= Time.delta;
        if(st.shieldCooldown > 0){
            st.shieldCooldown -= Time.delta;
            if(st.shieldCooldown <= 0){ st.shieldCooldown = 0; st.shield = Math.max(0, st.shieldMax) * SH_START; st.shieldRegenTimer = 0; }
            return;
        }
        if(st.shieldMax <= 0){ st.shield = 0; st.shieldDirty = true; return; }
        if(st.shieldDirty){ st.shield = st.shieldMax * SH_START; st.shieldDirty = false; }
        if(st.shield > st.shieldMax) st.shield = st.shieldMax;
        if(st.shield < st.shieldMax){
            st.shieldRegenTimer += Time.delta;
            if(st.shieldRegenTimer >= SH_REGEN){
                st.shieldRegenTimer -= SH_REGEN;
                if(this.power != null && this.power.graph != null){
                    const want = Math.min(st.shieldMax - st.shield, capacity * SH_REGEN_FRAC);
                    st.shield += this.power.graph.useBatteries(want);
                }
            }
        }else st.shieldRegenTimer = 0;
        const sr = this.shieldRadius();
        Groups.bullet.intersect(this.x - sr, this.y - sr, sr * 2, sr * 2, cons(b => {
            if(st.shield <= 0 || b.team == self.team || !b.type.absorbable || !b.within(self.x, self.y, sr)) return;
            st.shield -= b.damage();
            b.absorb();
            self.fx(FX_SPHERE, b.x, b.y, 0, YELLOW_COLOR);
            if(st.shieldHitCd <= 0){ st.shieldHitCd = SH_HIT_GAP; self.sfx(sndShieldHit(), b.x, b.y, 0.7, rnd(0.9, 1.1)); }
        }));
        if(st.shield <= 0){
            st.shield = 0; st.shieldCooldown = val(SH_CD, st.level);
            this.fx(FX_SHIELD_BREAK, this.x, this.y, sr, YELLOW_COLOR);
            this.sfx(sndShieldBreak(), this.x, this.y, 1.5, 1);
        }else{
            const list = this.enemyUnits(sr);
            for(let i = 0; i < list.length; i++) this.pushOut(list[i]);
        }
    },
    pushOut(u){
        const dx = u.x - this.x, dy = u.y - this.y, d = Math.hypot(dx, dy);
        let nx, ny;
        if(d < 0.001){ const a = (u.id % 360) * DEG; nx = Math.cos(a); ny = Math.sin(a); }
        else{ nx = dx / d; ny = dy / d; }
        const radial = u.vel.x * nx + u.vel.y * ny;
        if(radial < PUSH){ const add = PUSH - radial; u.vel.add(nx * add, ny * add); }
    },
    updateMagneticField(){
        if(!st.secTick) return;
        const pct = val(MAG, st.level) / 100, list = this.enemyUnits(this.affectRadiusWorld());
        for(let i = 0; i < list.length; i++){ const u = list[i]; u.damagePierce(u.maxHealth * pct); this.fx(FX_MAGNETIC, u.x, u.y, 0, YELLOW_COLOR); }
        if(list.length > 0) this.sfx(sndSpark(), this.x, this.y, 0.8, 0.8);
    },
    updateDualBlade(){
        const count = val(BL_COUNT, st.level), create = val(BL_CREATE, st.level), r = this.affectRadiusWorld(), dmg = val(BL_DMG, st.level);
        const home = thermion.size * TS * 0.5 + BLADE_R + 6, retarget = this.every(T_BLADE, 10);
        let enemies = null;
        for(let i = 0; i < count; i++){
            if(st.blades[i] < create){
                st.blades[i] += Time.delta;
                const oa = Time.time * 6 + i * 180;
                st.bladeX[i] = this.x + tx(oa, home); st.bladeY[i] = this.y + ty(oa, home);
                st.bladeInit[i] = false; st.bladeTarget[i] = null;
                continue;
            }
            if(!st.bladeInit[i]){ st.bladeX[i] = this.x; st.bladeY[i] = this.y; st.bladeHit[i] = 0; st.bladeInit[i] = true; }
            let tg = st.bladeTarget[i];
            if(tg != null && (!tg.isValid() || !tg.within(this.x, this.y, r + tg.hitSize / 2))) tg = null;
            if(tg == null && retarget){
                if(enemies == null) enemies = this.enemyUnits(r);
                const other = count > 1 ? st.bladeTarget[1 - i] : null;
                let best = 1e30;
                for(let k = 0; k < enemies.length; k++){
                    const u = enemies[k], d = dst(u.x, u.y, st.bladeX[i], st.bladeY[i]) + (u == other ? 400 : 0);
                    if(d < best){ best = d; tg = u; }
                }
            }
            st.bladeTarget[i] = tg;
            let tX, tY, cutting = false;
            if(tg != null){
                const reach = tg.hitSize / 2 + 6;
                if(dst(tg.x, tg.y, st.bladeX[i], st.bladeY[i]) <= reach + 4){
                    const ca = Time.time * 14 + i * 180;
                    tX = tg.x + tx(ca, reach * 0.8); tY = tg.y + ty(ca, reach * 0.8); cutting = true;
                }else{ tX = tg.x; tY = tg.y; }
            }else{
                const ha = Time.time * 6 + i * 180;
                tX = this.x + tx(ha, home); tY = this.y + ty(ha, home);
            }
            const dx = tX - st.bladeX[i], dy = tY - st.bladeY[i], dd = Math.hypot(dx, dy);
            if(dd > 0.001){ const step = Math.min(dd, BLADE_SPEED * Time.delta); st.bladeX[i] += dx / dd * step; st.bladeY[i] += dy / dd * step; }
            if(cutting){
                st.bladeHit[i] += Time.delta;
                if(st.bladeHit[i] >= 15){
                    st.bladeHit[i] -= 15;
                    tg.damage(dmg);
                    const bf = bonusFraction(this.gridCapacity()) * BONUS_BLADE;
                    if(bf > 0 && tg.isValid()) tg.damage(tg.maxHealth * bf);
                    this.fx(FX_BLADE_SLASH, tg.x, tg.y, rnd(0, 360), YELLOW_COLOR);
                    this.sfx(sndBladeHit(), tg.x, tg.y, 1.2, rnd(0.9, 1.1));
                }
            }
        }
    },
    activateElectromagneticPulse(){
        if(Vars.net.client() || st.form != YELLOW || st.level < 5 || st.pendingForm >= 0) return;
        if(!this.hasEnoughPower() || st.skillCd < 1) return;
        st.skillCd = 0; st.empActiveTimer = EMP_DURATION;
        const r = this.affectRadiusWorld(), us = this.enemyUnits(r), bs = this.enemyBuildings(r);
        for(let i = 0; i < us.length; i++) this.stun(us[i], EMP_DURATION);
        for(let i = 0; i < bs.length; i++) this.disableBuilding(bs[i], EMP_DURATION);
        this.fx(FX_EMP, this.x, this.y, r, YELLOW_COLOR);
        this.sfx(sndPulse(), this.x, this.y, 2, 0.9);
        NexNet.shake(8, 10, this.x, this.y);
    },

    // ============ BLUE
    updateBlaster(){
        this.tickPlasma();
        if(!st.secTick || st.plasmas.length > 0 || st.plasmaCd > 0) return;
        const ch = val(BLS_CH, st.level) / 100, dmg = val(BLS_DMG, st.level), extraPct = 0.5 * st.level / 100;
        const splashRad = val(BLS_SPLASH, st.level) * TS, list = this.enemyUnits(this.affectRadiusWorld());
        for(let k = 0; k < list.length; k++){
            const u = list[k];
            if(!chance(ch)) continue;
            const beams = ri(1, st.level), perBeam = dmg + dmg * beams / 5, extra = u.maxHealth * extraPct;
            for(let i = 0; i < beams && st.plasmas.length < 12; i++) this.plasmaBeam(u, i, i == beams - 1, perBeam, extra, splashRad);
        }
    },
    // tia plasma từ trên trời: bắt đầu quanh pháo rồi đuổi theo mục tiêu, gây sát thương duy trì (tổng ~ perBeam mỗi giây)
    plasmaBeam(u, idx, last, perBeam, extra, splashRad){
        const x = this.x + rr(TS), y = this.y + rr(TS);
        st.plasmas.push({tgt: u, x: x, y: y, life: BLS_MAX_TICKS, acc: idx * 3, fxT: 0, snd: 0, dmg: perBeam / 6,
            ex: last ? extra / (BLS_MAX_TICKS / 10) : 0, rad: Math.max(splashRad * 0.6, TS * 1.5), speed: 4 + st.level * 0.6 + rnd(0, 1.5)});
        this.fx(FX_BLASTER, x, y, 0, BLUE_COLOR);
    },
    tickPlasma(){
        if(Vars.net.client() || st.plasmas.length == 0){ if(st.plasmaCd > 0 && st.plasmas.length == 0) st.plasmaCd -= Time.delta; return; }
        for(let i = st.plasmas.length - 1; i >= 0; i--){
            const p = st.plasmas[i], u = p.tgt;
            p.life -= Time.delta;
            if(!u.isValid() || p.life <= 0){ this.fx(FX_BLAST, p.x, p.y, 0, BLUE_COLOR); st.plasmas.splice(i, 1); continue; }
            const dx = u.x - p.x, dy = u.y - p.y, d = Math.sqrt(dx * dx + dy * dy), sp = p.speed * Time.delta;
            if(d > sp){ p.x += dx / d * sp; p.y += dy / d * sp; }else{ p.x = u.x; p.y = u.y; }
            p.fxT -= Time.delta; if(p.fxT <= 0){ p.fxT = 3; this.fx(FX_PLASMA, p.x, p.y, 0, BLUE_COLOR); }
            p.acc -= Time.delta;
            if(p.acc <= 0){
                p.acc = 10;
                const around = this.unitsAround(p.x, p.y, p.rad);
                for(let j = 0; j < around.length; j++) around[j].damagePierce(p.dmg);
                if(p.ex > 0 && u.isValid()) u.damagePierce(p.ex);
            }
            p.snd -= Time.delta; if(p.snd <= 0){ p.snd = 30; this.sfx(sndLaser(), p.x, p.y, 0.5, rnd(0.9, 1.2)); }
        }
        if(st.plasmas.length == 0) st.plasmaCd = BLS_COOLDOWN_TICKS;
    },
    updateMeteor(){
        if(!st.secTick) return;
        const ch = val(MET_CH, st.level) / 100, pct = val(MET_PCT, st.level) / 100, flat = val(MET_FLAT, st.level), rad = METEOR_RAD_TILES * TS;
        const list = this.enemyUnits(this.affectRadiusWorld());
        for(let k = 0; k < list.length; k++){
            const u = list[k];
            if(!chance(ch)) continue;
            const count = ri(3, st.level * 3);
            for(let i = 0; i < count; i++) this.meteor(u.x + rr(TS * 2), u.y + rr(TS * 2), rnd(0, 25), rad, pct, flat);
        }
    },
    meteor(ix, iy, d, rad, pct, flat){
        const self = this;
        Time.run(d, run(() => self.fx(FX_METEOR, ix, iy, rad, Color.orange)));
        Time.run(d + METEOR_FALL, run(() => {
            self.sfx(sndMeteorHit(), ix, iy, 1.4, rnd(0.9, 1.1));
            const vs = self.unitsAround(ix, iy, rad);
            for(let i = 0; i < vs.length; i++) vs[i].damage(vs[i].maxHealth * pct + flat);
            const liq = chance(SLAG_CHANCE) ? Liquids.slag : Liquids.oil;
            for(let i = 0; i < METEOR_PUDDLES; i++){
                const t = Vars.world.tileWorld(ix + rr(rad), iy + rr(rad));
                if(t != null) Puddles.deposit(t, liq, 20);
            }
        }));
    },
    updateDeathLaser(){
        if(st.secTick){
            const ch = val(LAS_CH, st.level) / 100, list = this.enemyUnits(this.affectRadiusWorld());
            for(let i = 0; i < list.length; i++){
                const u = list[i];
                if(!chance(ch)) continue;
                st.lasers.push({target: u, tx: u.x, ty: u.y, timeLeft: val(LAS_TIME, st.level) * 60, secTimer: 0, weakenTimer: 0, fxTimer: 5, loopTimer: 0});
                this.sfx(sndDeathLoop(), u.x, u.y, 2, 1);
            }
        }
        const range = 2 * st.level * TS, dps = val(LAS_DMG, st.level);
        for(let i = st.lasers.length - 1; i >= 0; i--){
            const b = st.lasers[i];
            if(b.target != null && b.target.isValid()){ b.tx = b.target.x; b.ty = b.target.y; }
            b.timeLeft -= Time.delta; b.secTimer += Time.delta; b.loopTimer += Time.delta; b.weakenTimer += Time.delta; b.fxTimer += Time.delta;
            if(b.loopTimer >= LOOP_TICKS){
                b.loopTimer -= LOOP_TICKS;
                if(b.timeLeft > LOOP_TICKS * 0.5) this.sfx(sndDeathLoop(), b.tx, b.ty, 2, 1);
            }
            if(b.fxTimer >= 5){ b.fxTimer = 0; this.fx(FX_DEATH_LASER, b.tx, b.ty, range, BLUE_COLOR); }
            if(b.weakenTimer >= 10){
                b.weakenTimer = 0;
                const us = this.unitsAround(b.tx, b.ty, range), bs = this.buildingsAround(b.tx, b.ty, range);
                for(let k = 0; k < us.length; k++) us[k].apply(weakened, 20);
                for(let k = 0; k < bs.length; k++) bs[k].applySlowdown(0.5, 20);
            }
            if(b.secTimer >= 60){
                b.secTimer -= 60;
                if(b.target != null && b.target.isValid()){
                    b.target.damage(dps);
                    const us = this.unitsAround(b.tx, b.ty, range);
                    for(let k = 0; k < us.length; k++) if(us[k] != b.target) us[k].damage(dps * LAS_SPLASH_FRAC);
                }
            }
            if(b.timeLeft <= 0){
                const pct = ri(2, 5) * st.level / 100, us = this.unitsAround(b.tx, b.ty, range);
                for(let k = 0; k < us.length; k++) us[k].damage(us[k].maxHealth * pct);
                this.fx(FX_DEATH_BLAST, b.tx, b.ty, range, BLUE_COLOR);
                this.sfx(sndDeathBlast(), b.tx, b.ty, 1.2, rnd(0.95, 1.05));
                st.lasers.splice(i, 1);
            }
        }
    },
    updateLimitless(){
        const r = this.affectRadiusWorld() / LIM_DIV, pct = val(LIM_DECAY, st.level) / 100, self = this;
        if(r <= 0 || pct <= 0) return;
        Groups.bullet.intersect(this.x - r, this.y - r, r * 2, r * 2, cons(b => {
            if(b.team == self.team || !b.within(self.x, self.y, r)) return;
            const spd = b.vel.len();
            if(spd <= 0.0001) return;
            const base = b.type.speed > 0.0001 ? b.type.speed : spd;
            b.vel.setLength(Math.max(0, spd - base * pct / 60 * Time.delta));
        }));
    },
    updateRCT(){
        if(!this.every(T_RCT, val(RCT_T, st.level))) return;
        this.heal(this.nexMaxHealth() * val(RCT_A, st.level) / 100);
        this.fx(FX_HEAL, this.x, this.y, thermion.size * TS, BLUE_COLOR);
    },
    activateAsteroid(){
        if(Vars.net.client() || st.form != BLUE || st.level < 5 || st.pendingForm >= 0 || st.asteroidPhase != AST_NONE) return;
        if(!this.hasEnoughPower() || st.skillCd < 1) return;
        st.skillCd = 0;
        st.asteroidRadius = this.liveAffectRadius() * TS;
        st.asteroidPhase = AST_CHARGE; st.asteroidTimer = AST_CHARGE_TICKS;
        const w = Vars.world.unitWidth(), h = Vars.world.unitHeight();
        st.asteroidTx = this.x < w / 2 ? 2 * w : -w; st.asteroidTy = 2 * h;
        st.lasers = []; st.chargeLoopTimer = LOOP_TICKS;
    },
    updateAsteroid(){
        const r = st.asteroidRadius, self = this;
        if(this.every(T_ASTEROID, 5)){
            const us = this.enemyUnits(r), bs = this.enemyBuildings(r);
            for(let i = 0; i < us.length; i++) this.stun(us[i], 20);
            for(let i = 0; i < bs.length; i++) this.disableBuilding(bs[i], 20);
        }
        Groups.bullet.intersect(this.x - r, this.y - r, r * 2, r * 2, cons(b => { if(b.team != self.team && b.within(self.x, self.y, r)) b.vel.setZero(); }));
        if(st.asteroidPhase == AST_CHARGE){
            st.chargeLoopTimer += Time.delta;
            if(st.chargeLoopTimer >= LOOP_TICKS){
                st.chargeLoopTimer -= LOOP_TICKS;
                if(st.asteroidTimer > LOOP_TICKS * 0.5) this.sfx(sndDeathLoop(), this.x, this.y, 2, 1);
            }
        }
        st.asteroidTimer -= Time.delta;
        if(st.asteroidTimer > 0) return;
        if(st.asteroidPhase == AST_CHARGE){
            st.asteroidPhase = AST_FALL; st.asteroidTimer = AST_TRAVEL;
            this.fx(FX_AST_FALL, st.asteroidTx, st.asteroidTy, rnd(1, 1000), Color.orange);
        }else if(st.asteroidPhase == AST_FALL) this.asteroidStrike();
    },
    asteroidStrike(){
        const r = st.asteroidRadius, cx = Vars.world.unitWidth() / 2, cy = Vars.world.unitHeight() / 2, self = this;
        const doomedU = [], doomedB = [];
        Groups.unit.each(cons(u => { if(u.isValid() && !u.within(self.x, self.y, r)) doomedU.push(u); }));
        Groups.build.each(cons(b => { if(b == null || !b.isValid() || b.block == thermion || b.within(self.x, self.y, r)) return; doomedB.push(b); }));
        this.fx(FX_AST_FLASH, cx, cy, 0, Color.white);
        this.fx(FX_AST_QUAKE, cx, cy, 0, Color.orange);
        this.fx(FX_AST_IMPACT, cx, cy, dst(cx, cy, 0, 0), Color.orange);
        for(let i = 0; i < doomedU.length; i++) doomedU[i].kill();
        for(let i = 0; i < doomedB.length; i++) doomedB[i].kill();
        this.scorchMap();
        this.setForm(DEATH);
    },
    scorchMap(){
        const W = Vars.world.width(), H = Vars.world.height(), rocks = [Blocks.hotrock, Blocks.magmarock, Blocks.basalt];
        let y0 = 0;
        const step = () => {
            const y1 = Math.min(H, y0 + 10);
            for(let y = y0; y < y1; y++) for(let x = 0; x < W; x++){
                try{
                    const t = Vars.world.tile(x, y);
                    if(t == null) continue;
                    const f = t.floor(), blk = t.block();
                    t.setFloor(f.isLiquid ? (f.liquidDrop == Liquids.water ? Blocks.slag : rocks[Math.floor(rs(x * 7 + y * 131, 0, 2.999))]) : rocks[Math.floor(rs(x * 7 + y * 131, 0, 2.999))]);
                    if(t.build == null && blk != Blocks.air && !blk.synthetic()) t.setAir();
                }catch(err){ Log.err("[Thermion] scorch " + x + "," + y + ": " + err); }
            }
            y0 = y1;
            if(y0 < H) Time.run(1, run(step));
            else try{ Vars.renderer.blocks.floor.clearTiles(); }catch(err){}
        };
        step();
    },

    // ============ PURPLE
    updateIllusion(){
        if(!this.every(T_ILLUSION, val(ILL_T, st.level) * 60)) return;
        const ch = val(ILL_CH, st.level) / 100, base = val(ILL_DUR, st.level) * 60, list = this.enemyUnits(this.affectRadiusWorld());
        for(let i = 0; i < list.length; i++){
            const u = list[i];
            if(!chance(ch)) continue;
            const dur = base * (1 - cl(u.armor / 100));
            if(dur > 0){ this.stun(u, dur); this.fx(FX_PURPLE_BURST, u.x, u.y, 0, PURPLE_COLOR); }
        }
    },
    updateDrone(){
        st.drones = st.drones.filter(d => d != null && d.isValid());
        if(!this.every(T_DRONE, val(DRONE_T, st.level) * 60)) return;
        const type = st.level >= DRONE_V2_LEVEL ? droneV2Type : droneType, d = type.create(this.team), off = thermion.size * TS / 2;
        d.set(this.x + rr(off), this.y + rr(off));
        d.controller(makeDroneAI(this));
        d.add();
        st.drones.push(d);
        this.fx(FX_PURPLE_BURST, d.x, d.y, 0, PURPLE_COLOR);
        const gdx = this.x - d.x, gdy = this.y - d.y;
        this.fx(FX_GLOW_DRONE, d.x, d.y, (Math.atan2(gdy, gdx) / DEG + 360) % 360 + 360 * Math.round(Math.hypot(gdx, gdy)), PURPLE_COLOR);
    },
    updateShapeshifter(){
        if(!this.every(T_SHAPE, val(SHAPE_T, st.level) * 60)) return;
        const ch = val(SHAPE_CH, st.level) / 100, list = this.enemyUnits(this.affectRadiusWorld());
        for(let i = 0; i < list.length; i++){
            if(!chance(ch)) continue;
            this.shapeClone(list[i]);
        }
    },
    shapeClone(u){
        const self = this, tm = this.team, utype = u.type, sx = u.x, sy = u.y, srot = u.rotation;
        const shp = u.health, smax = u.maxHealth, ssh = u.shield, sarm = u.armor;
        this.fx(FX_SHAPE, sx, sy, 0, PURPLE_COLOR);
        Time.run(SHAPE_DELAY, run(() => {
            if(Vars.net.client()) return;
            const s = utype.create(tm);
            s.set(sx, sy); s.rotation = srot; s.maxHealth = smax; s.health = shp; s.shield = ssh; s.armor = sarm; s.add();
            self.fx(FX_SHAPE, sx, sy, 0, PURPLE_COLOR);
        }));
    },
    updateMindControl(){
        if(!this.every(T_MIND, val(MIND_T, st.level) * 60)) return;
        const ch = val(MIND_CH, st.level) / 100, list = this.enemyUnits(this.affectRadiusWorld());
        for(let i = 0; i < list.length; i++) if(chance(ch)) this.convert(list[i]);
    },
    convert(u){ u.team = this.team; u.resetController(); this.fx(FX_MIND, u.x, u.y, u.hitSize, PURPLE_COLOR); },
    activateMegaHypnosis(){
        if(Vars.net.client() || st.form != PURPLE || st.level < 5 || st.pendingForm >= 0) return;
        if(!this.hasEnoughPower() || st.skillCd < 1) return;
        st.skillCd = 0; this.hypnosis();
    },
    hypnosis(){
        const r = this.affectRadiusWorld();
        this.fx(FX_HYPNOSIS, this.x, this.y, r, PURPLE_COLOR);
        const list = this.enemyUnits(r);
        for(let i = 0; i < list.length; i++) this.convert(list[i]);
    },

    // ============ điều khiển
    activateActiveSkill(){
        if(st.form == YELLOW) this.activateElectromagneticPulse();
        else if(st.form == BLUE) this.activateAsteroid();
        else if(st.form == PURPLE) this.activateMegaHypnosis();
    },
    handleConfig(v){
        if(v >= YELLOW && v <= PURPLE) this.requestForm(v);
        else if(v == CFG_ACTIVE) this.activateActiveSkill();
        else if(v == CFG_UPGRADE) this.startUpgrade();
    },
    configured(builder, value){ if(value == null) return; this.handleConfig(Number(value)); },
    buildConfiguration(table){
        const self = this;
        const fb = (name, f) => table.button(name, Styles.flatBordert, run(() => self.configure(I(f)))).size(90, 40).disabled(boolf(b => !self.canChangeForm(f)));
        fb("Yellow", YELLOW); fb("Blue", BLUE); fb("Purple", PURPLE);
        table.row();
        table.button(T("Kỹ năng chủ động", "Active skill"), Styles.flatBordert, run(() => self.configure(I(CFG_ACTIVE)))).colspan(3).growX().height(40)
            .disabled(boolf(b => st.level < 5 || st.form == DEATH || st.asteroidPhase != AST_NONE));
        table.row();
        table.table(cons(t => {
            t.button(T("Nâng cấp", "Upgrade"), Styles.flatBordert, run(() => self.showUpgradeDialog())).size(135, 40).disabled(boolf(b => st.level >= MAX_LEVEL || st.form == DEATH));
            t.button(T("Thông tin", "Info"), Styles.flatBordert, run(() => self.showInfoDialog())).size(135, 40);
        })).colspan(3).growX();
    },

    // ============ nâng cấp (trả góp)
    remaining(cost, i){ return Math.max(0, cost[i][1] - st.paid[i]); },
    coreAmount(item){ const c = this.core(); return c == null ? 0 : c.items.get(item); },
    canPayAny(){
        const cost = upgradeCost(st.level), c = this.core();
        if(cost == null || c == null) return false;
        if(isSandbox()) return true;
        for(let i = 0; i < cost.length; i++) if(this.remaining(cost, i) > 0 && c.items.get(cost[i][0]) > 0) return true;
        return false;
    },
    canUpgrade(){ return st.level < MAX_LEVEL && st.form != DEATH && st.asteroidPhase == AST_NONE && this.canPayAny(); },
    clearUpgrade(){ st.upgrading = false; for(let i = 0; i < st.paid.length; i++) st.paid[i] = 0; },
    paidTotal(){ let s = 0; for(let i = 0; i < st.paid.length; i++) s += st.paid[i]; return s; },
    paidFraction(){
        const cost = upgradeCost(st.level);
        if(cost == null || cost.length == 0) return 1;
        let s = 0;
        for(let i = 0; i < cost.length; i++) s += cost[i][1] <= 0 ? 1 : Math.min(1, st.paid[i] / cost[i][1]);
        return s / cost.length;
    },
    startUpgrade(){ if(Vars.net.client() || !this.canUpgrade()) return; this.payInstallment(); },
    payInstallment(){
        const cost = upgradeCost(st.level), c = this.core();
        if(cost == null){ this.clearUpgrade(); return; }
        if(c == null) return;
        let done = true, took = false;
        for(let i = 0; i < cost.length; i++){
            const need = this.remaining(cost, i);
            if(need > 0){
                const take = Math.min(need, c.items.get(cost[i][0]));
                if(take > 0){ NexNet.take(c, cost[i][0], take); st.paid[i] += take; took = true; }
            }
            if(st.paid[i] < cost[i][1]) done = false;
        }
        if(took) st.upgrading = true;
        if(done) this.setLevel(st.level + 1);
    },
    showUpgradeDialog(){
        const self = this, d = new BaseDialog(T("Nâng cấp Thermion", "Thermion Upgrade"));
        addBack(d);
        const body = new Table();
        let sig = -1;
        body.update(run(() => {
            if(st.level >= MAX_LEVEL){ d.hide(); return; }
            const sg = st.level * 1000003 + self.paidTotal() * 31 + (isSandbox() ? 7 : 0);
            if(sig != sg){ sig = sg; self.buildUpgradeBody(body); }
        }));
        dialogPane(d, p => p.add(body).width(dialogWidth() - 30).left());
        d.show();
    },
    buildUpgradeBody(t){
        const self = this, cw = dialogWidth() - 90, cost = upgradeCost(st.level);
        t.clear(); t.defaults().left().pad(3);
        if(cost == null){ t.add(T("Đã đạt cấp tối đa.", "Max level reached.")).row(); return; }
        t.add("[accent]Level " + st.level + "  ->  " + (st.level + 1) + "[]").padTop(4).row();
        section(t, T("Tài nguyên", "Resources"));
        t.table(cons(c => {
            c.defaults().left().pad(3);
            for(let i = 0; i < cost.length; i++){
                const idx = i, item = cost[i][0], total = cost[i][1];
                c.image(item.uiIcon).size(26).padRight(6);
                c.add(item.localizedName).padRight(10);
                c.label(prov(() => {
                    const have = self.coreAmount(item), rem = Math.max(0, total - st.paid[idx]);
                    const col = rem == 0 ? "[lightgray]" : have >= rem ? "[green]" : "[scarlet]";
                    return col + have + "[] / " + rem + (st.paid[idx] > 0 ? "  [lightgray](" + st.paid[idx] + "/" + total + ")[]" : "");
                }));
                c.row();
            }
            c.add(new Bar(prov(() => T("Đã trả ", "Paid ") + Math.floor(self.paidFraction() * 100) + "%"), prov(() => Pal.accent), floatp(() => self.paidFraction()))).colspan(3).growX().height(26).padTop(6).row();
            wrap(c, T("[lightgray]Trong lõi / còn cần  (đã trả / tổng)[]", "[lightgray]In core / still needed  (paid / total)[]"), cw).colspan(3).row();
        })).growX().padLeft(16).row();
        wrap(t, T("Mỗi lần bấm chỉ trả một lần, lấy đúng lượng tài nguyên lõi đang có. Quay lại bấm tiếp để trả thêm; đủ tất cả thì tự lên cấp.", "Each click pays once, taking exactly what the core currently has. Come back and click again to pay more; once everything is paid the block levels up automatically."), cw).padTop(4).padLeft(16).row();
        const ub = t.button("", Styles.flatBordert, run(() => self.configure(I(CFG_UPGRADE)))).growX().height(46).padTop(12);
        ub.disabled(boolf(b => !self.canUpgrade()));
        const tb = ub.get();
        tb.update(run(() => tb.setText(st.upgrading ? T("Trả góp", "Pay installment") : T("Nâng cấp", "Upgrade"))));
        t.row();
        t.label(prov(() => self.canUpgrade() ? "" : T("[lightgray]Lõi hiện không có tài nguyên nào còn thiếu.[]", "[lightgray]The core has none of the missing resources.[]"))).row();
    },

    // ============ info
    showInfoDialog(){
        const self = this, d = new BaseDialog(T("Thông tin Thermion", "Thermion Info"));
        addBack(d);
        const body = new Table();
        let sig = -1;
        body.update(run(() => { if(sig != st.level){ sig = st.level; self.buildInfoBody(body); } }));
        dialogPane(d, p => p.add(body).width(dialogWidth() - 30).left());
        d.show();
    },
    buildInfoBody(t){
        const self = this, cw = dialogWidth() - 90;
        t.clear(); t.defaults().left().pad(3);
        section(t, T("Tổng quan", "Overview"));
        t.table(cons(c => {
            c.defaults().left().pad(3);
            wrap(c, T("Thermion là cỗ máy 5x5 nhiều dạng chạy bằng lưới điện. Cần công suất điện tối thiểu để hoạt động, điện dư càng lớn thì phạm vi ảnh hưởng càng rộng. Có ba dạng (Yellow / Blue / Purple), mỗi dạng có kỹ năng bị động riêng, mở khóa thêm khi lên cấp. Ở level 5 mỗi dạng có thêm một kỹ năng chủ động.", "Thermion is a 5x5 multi-form machine powered by the power grid. It needs a minimum power output to work, and the more surplus power, the larger its range of effect. It has three forms (Yellow / Blue / Purple), each with its own passive skills that unlock as it levels up. At level 5 each form gains an active skill."), cw).row();
            c.label(prov(() => T("Cấp: ", "Level: ") + st.level + " / " + MAX_LEVEL)).row();
            c.label(prov(() => T("Dạng: ", "Form: ") + colorTag(st.form) + formName(st.form) + "[]")).row();
            c.label(prov(() => {
                const prod = self.totalPowerProduction(), req = powerRequirement(st.level);
                return T("Công suất điện: ", "Power output: ") + (prod >= req ? "[green]" : "[scarlet]") + fmt(prod) + "[] / " + fmt(req) + " /s";
            })).row();
            c.label(prov(() => T("Phạm vi ảnh hưởng: ", "Effect range: ") + self.affectRadius().toFixed(1) + " tile")).row();
            c.label(prov(() => T("Máu tối đa: ", "Max HP: ") + fmt(self.nexMaxHealth()))).row();
        })).growX().padLeft(16).row();

        section(t, T("Tiến trình theo cấp", "Progression by level"));
        t.table(cons(c => {
            c.defaults().pad(4);
            [T("Cấp", "Level"), T("Điện /s", "Power /s"), T("Đổi dạng", "Form change"), T("Hồi", "Cooldown"), T("Máu tối đa +", "Max HP +")].forEach(h => c.add("[lightgray]" + h + "[]"));
            c.row();
            for(let L = 1; L <= MAX_LEVEL; L++){
                const cells = [String(L), fmt(powerRequirement(L)), fmt(val(FORM_TIME, L)) + "s", fmt(val(FORM_CD, L)) + "s",
                    colorTag(YELLOW) + fmt(formHealthBonus(YELLOW, L)) + "[]/" + colorTag(BLUE) + fmt(formHealthBonus(BLUE, L)) + "[]/" + colorTag(PURPLE) + fmt(formHealthBonus(PURPLE, L)) + "[]%"];
                const hl = L == st.level ? "[accent]" : "";
                cells.forEach(s => c.add(hl + s));
                c.row();
            }
        })).growX().padLeft(16).row();

        for(let f = YELLOW; f <= PURPLE; f++){
            const fm = f;
            section(t, formName(f));
            t.table(cons(c => {
                c.defaults().left().pad(3);
                wrap(c, T(FORM_ROLE[fm][0], FORM_ROLE[fm][1]), cw).row();
                c.add(T("[accent]Kỹ năng (bấm để xem chi tiết)[]", "[accent]Skills (tap for details)[]")).padTop(6).row();
                SKILLS[fm].forEach(sk => {
                    if(sk[1] > st.level) return;
                    c.button(colorTag(fm) + sk[0] + "[]" + (sk[2] ? T("  (chủ động)", "  (active)") : ""), Styles.flatBordert, run(() => self.showSkillDialog(fm, sk))).width(cw).height(38).left().row();
                });
            })).growX().padLeft(16).row();
        }
        section(t, "Death");
        wrap(t, T("Sau Asteroid, Thermion rơi vào dạng Death 10 phút: không có kỹ năng, không đổi dạng, không nâng cấp. Sau đó tự trở về dạng Blue. Hồi chiêu Asteroid bị khóa trong thời gian này.", "After Asteroid, Thermion falls into Death form for 10 minutes: no skills, no form change, no upgrades. Then it automatically returns to Blue. The Asteroid cooldown is locked during this time."), cw).padLeft(16).row();
        section(t, T("Cần biết", "Good to know"));
        wrap(t, T("- Công suất điện thấp hơn mức yêu cầu thì Thermion ngừng hoạt động hoàn toàn.\n- Đổi dạng mất vài giây rồi cần thời gian hồi.\n- Mọi kỹ năng chủ động dùng chung một thời gian hồi, đóng băng khi đang đổi dạng.\n- Nâng cấp trả bằng tài nguyên trong lõi và có thể trả góp.", "- If power output is below the requirement, Thermion stops working entirely.\n- Changing form takes a few seconds and then needs a cooldown.\n- All active skills share one cooldown, which freezes while changing form.\n- Upgrades are paid with core resources and can be paid in installments."), cw).padLeft(16).row();
    },
    showSkillDialog(f, sk){
        const self = this, d = new BaseDialog(colorTag(f) + sk[0] + "[]");
        addBack(d);
        dialogPane(d, p => {
            const cw = dialogWidth() - 90;
            p.defaults().left().pad(3);
            p.add(colorTag(f) + formName(f) + "[] - " + (sk[2] ? T("Chủ động", "Active") : T("Bị động", "Passive"))).row();
            p.label(prov(() => st.level >= sk[1] ? T("[green]Đã mở khóa[]", "[green]Unlocked[]") : T("[scarlet]Chưa mở khóa - cần level ", "[scarlet]Locked - requires level ") + sk[1] + T(" (hiện tại: ", " (current: ") + st.level + ")[]")).row();
            section(p, T("Mô tả", "Description"));
            wrap(p, T(sk[3], sk[4]), cw).padLeft(16).row();
        });
        d.show();
    },

    // ============ bar
    skillCooldownTicks(f){ return f == YELLOW ? EMP_COOLDOWN : f == BLUE ? AST_COOLDOWN : f == PURPLE ? MEGA_COOLDOWN : 1; },
    changeProgress(){ return cl(1 - st.formChangeTimer / Math.max(1, val(FORM_TIME, st.level) * 60)); },
    formBarText(){
        if(st.form == DEATH) return "Death: " + timeText(st.deathTimer);
        if(st.pendingForm >= 0) return T("Đang đổi", "Changing");
        if(st.formCooldown > 0) return T("Hồi: ", "Cooldown: ") + (st.formCooldown / 60).toFixed(1) + "s";
        return formName(st.form);
    },
    formBarColor(){
        if(st.pendingForm >= 0) return this.changeProgress() < 0.5 ? formColor(st.form) : formColor(st.pendingForm);
        return formColor(st.form);
    },
    formBarFraction(){
        if(st.form == DEATH) return cl(st.deathTimer / DEATH_TICKS);
        if(st.pendingForm >= 0){ const p = this.changeProgress(); return p < 0.5 ? 1 - p * 2 : (p - 0.5) * 2; }
        if(st.formCooldown > 0) return 1 - cl(st.formCooldown / Math.max(1, val(FORM_CD, st.level) * 60));
        return 1;
    },
    showShieldBar(){ return st.form == YELLOW && st.level >= 3; },
    shieldMaxNow(){ return val(SH_PCT, st.level) / 100 * this.gridCapacity(); },
    storageBarText(){
        if(this.showShieldBar()) return T("Máu khiên: ", "Shield HP: ") + compactNum(st.shield) + "/" + compactNum(this.shieldMaxNow());
        return T("Lưu trữ: ", "Stored: ") + compactNum(this.gridStored()) + "/" + compactNum(this.gridCapacity());
    },
    storageBarColor(){ return this.showShieldBar() ? YELLOW_COLOR : Pal.powerBar; },
    storageBarFraction(){
        const sh = this.showShieldBar(), max = sh ? this.shieldMaxNow() : this.gridCapacity(), cur = sh ? st.shield : this.gridStored();
        return max > 0 ? cl(cur / max) : 0;
    },
    cooldownBarText(){
        if(st.form == DEATH) return T("Hồi chiêu: Khóa", "Cooldown: Locked");
        if(st.asteroidPhase == AST_CHARGE) return T("Đang gồng: ", "Charging: ") + (st.asteroidTimer / 60).toFixed(1) + "s";
        if(st.asteroidPhase == AST_FALL) return T("Va chạm: ", "Impact: ") + (st.asteroidTimer / 60).toFixed(1) + "s";
        if(st.level < 5) return T("Hồi chiêu: --", "Cooldown: --");
        return T("Hồi chiêu: ", "Cooldown: ") + Math.floor(st.skillCd * 100) + "%";
    },
    cooldownBarFraction(){
        if(st.asteroidPhase == AST_CHARGE) return cl(1 - st.asteroidTimer / AST_CHARGE_TICKS);
        if(st.asteroidPhase == AST_FALL) return cl(1 - st.asteroidTimer / AST_TRAVEL);
        if(st.level < 5) return 0;
        return cl(st.skillCd);
    },

    // ============ vẽ
    drawRangeRing(){
        if(st.ringStamp > Time.time || Time.time - st.ringStamp >= 15){ st.ringStamp = Time.time; st.ringRadius = this.affectRadiusWorld(); }
        if(st.ringRadius <= 0) return;
        Draw.z(Layer.blockOver); Draw.color(formColor(st.form), 0.5); Lines.stroke(1.5); Lines.circle(this.x, this.y, st.ringRadius); Draw.reset();
    },
    draw(){
        this.super$draw();
        if(st.form == DEATH) return;
        this.drawRangeRing();
        const t = Time.time;
        if(this.hasEnoughPower() || st.asteroidPhase != AST_NONE){
            glowCol.set(formColor(st.form));
            if(st.pendingForm >= 0) glowCol.lerp(formColor(st.pendingForm), this.changeProgress());
            Draw.z(Layer.block + 0.01); drawGlow(this.x, this.y, glowCol, 0.8 + 0.2 * Math.sin(t * 0.08), 1);
        }
        Draw.z(Layer.blockOver);
        if(st.asteroidPhase != AST_NONE){ this.drawAsteroidBeam(t); Draw.reset(); return; }
        if(!this.hasEnoughPower()) return;
        if(st.pendingForm >= 0){
            const p = this.changeProgress();
            this.drawAuraOf(st.form, t, 1 - p); this.drawAuraOf(st.pendingForm, t, p);
        }else{
            this.drawAuraOf(st.form, t, 1);
            this.drawSkillVisuals(t, formColor(st.form));
        }
        Draw.reset();
    },
    drawAuraOf(f, t, am){
        auraAlpha = am;
        drawCorePulse(this.x, this.y, formColor(f), 1);
        if(f == YELLOW) drawYellowAura(this.x, this.y, st.level, t);
        else if(f == BLUE) drawBlueAura(this.x, this.y, t);
        else if(f == PURPLE) drawPurpleAura(this.x, this.y, t);
        auraAlpha = 1; Draw.reset();
    },
    drawAsteroidBeam(t){
        const cx = Vars.world.unitWidth() / 2, cy = Vars.world.unitHeight() / 2;
        let ex = st.asteroidTx, ey = st.asteroidTy, progress = 1;
        if(st.asteroidPhase == AST_FALL){
            const f = cl(1 - st.asteroidTimer / AST_TRAVEL);
            ex = lerp(st.asteroidTx, cx, f); ey = lerp(st.asteroidTy, cy, f);
        }else progress = cl(1 - st.asteroidTimer / AST_CHARGE_TICKS);
        const sf = 1 + Math.sin(t * 0.1) * 0.25;
        Draw.z(Layer.effect);
        beamTo(this.x, this.y, ex, ey, (5 + 11 * progress) * sf, BLUE_COLOR);
        // hạt xoắn quanh tia laser, chạy dọc tia theo cùng hướng với tia (pháo -> thiên thạch)
        const dx = ex - this.x, dy = ey - this.y, dist = Math.sqrt(dx * dx + dy * dy);
        if(dist > 1){
            const nx = dx / dist, ny = dy / dist, fwd = Math.atan2(dy, dx) / DEG, N = Math.min(260, Math.max(80, Math.floor(dist / 5)));
            const bw = (5 + 11 * progress) * sf;
            additive();
            for(let i = 0; i < N; i++){
                const ph = (t * (4 + rs(i * 7 + 1, 0, 7)) / dist + rs(i * 13 + 5, 0, 1)) % 1;      // 0 = pháo, 1 = thiên thạch
                const len = rs(i * 11 + 3, 1, 3) * TS, rad = bw * rs(i * 17 + 9, 0.9, 2.2) + 4;
                const lat = Math.sin(ph * dist * 0.03 + rs(i * 3 + 2, 0, 6.28) + t * 0.12) * rad;
                const bx = lerp(this.x, ex, ph) - ny * lat, by = lerp(this.y, ey, ph) + nx * lat;
                Draw.color(Color.white, BLUE_COLOR, rs(i * 19 + 7, 0, 1)); Draw.alpha(Math.sin(ph * Math.PI) * 0.95);
                Lines.stroke(1 + rs(i * 23 + 1, 0, 1.4)); Lines.lineAngle(bx, by, fwd, len);
            }
            for(let k = 0; k < 4; k++){ const q = (t * 0.018 + k / 4) % 1; Draw.color(BLUE_COLOR, Color.white, q); Draw.alpha(0.7 * Math.sin(q * Math.PI)); Lines.stroke(2.5 * (1 - q) + 0.5); Lines.circle(this.x, this.y, 90 * (1 - q) + 8); }
            Draw.blend();
            bloom(this.x, this.y, 30 + 40 * progress, BLUE_COLOR, 0.8);
            bloom(ex, ey, 26 + 30 * progress, Color.orange, 0.7);
            Draw.reset();
        }
    },
    drawSkillVisuals(t, fc){
        const r = this.affectRadiusWorld(), enemies = this.cachedEnemies(r);
        for(let i = 0; i < enemies.length; i++) drawCorePulse(enemies[i].x, enemies[i].y, fc, 0.5);
        if(st.form == YELLOW || st.form == PURPLE){
            for(let i = 0; i < enemies.length; i++){
                const u = enemies[i];
                if(u.hasEffect(StatusEffects.disarmed) && u.hasEffect(StatusEffects.unmoving)) drawStunMark(u.x, u.y, u.hitSize * 0.6, t, fc);
            }
        }
        if(st.form == YELLOW) this.drawYellowSkills(t, r, enemies);
        else if(st.form == BLUE) this.drawBlueSkills(t, r);
    },
    drawYellowSkills(t, r, enemies){
        for(let i = 0; i < enemies.length; i++) drawElectricRing(enemies[i].x, enemies[i].y, enemies[i].hitSize * 0.6 + 5, t);
        if(st.level >= 2 && st.orbCount > 0){
            const cf = cl(st.sphereCharge / val(SPH_CHARGE, st.level)), orbR = 9 + 7 * cf, pz = Draw.z();
            Draw.z(ORB_Z);
            for(let i = 0; i < st.orbCount; i++){
                const p = this.orbPos(i);
                drawOrb(p[0] + Math.sin(t * 0.1 + i) * 2, p[1] + Math.cos(t * 0.12 + i) * 2, orbR, cf);
            }
            Draw.z(pz);
        }
        if(st.level >= 3 && st.shield > 0 && st.shieldCooldown <= 0){
            const sr = this.shieldRadius(), cx = this.x, cy = this.y;
            Draw.z(Layer.shields);
            drawMulti((scale, ov, alpha, ss) => {
                Draw.color(ov != null ? ov : YELLOW_COLOR); Draw.alpha(alpha * 0.85); Lines.stroke(4 * ss); Lines.circle(cx, cy, sr);
                Draw.alpha(alpha * 0.05); Fill.circle(cx, cy, sr); Draw.reset();
            });
            Draw.color(YELLOW_COLOR, 0.25); Lines.stroke(1.5); Lines.poly(cx, cy, 6, sr * 0.96, 0);
            Draw.z(Layer.blockOver);
        }
        if(st.level >= 5 && st.empActiveTimer > 0 && r > 0){
            const bs = this.enemyBuildings(r);
            for(let i = 0; i < bs.length; i++) drawStunMark(bs[i].x, bs[i].y, bs[i].block.size * TS / 2, t, YELLOW_COLOR);
        }
    },
    drawBlades(){
        if(!this.isValid() || st.form != YELLOW || st.level < 4 || st.asteroidPhase != AST_NONE || st.pendingForm >= 0 || !this.hasEnoughPower()) return;
        const t = Time.time, bc = val(BL_COUNT, st.level), create = val(BL_CREATE, st.level);
        Draw.z(BLADE_Z);
        for(let i = 0; i < bc; i++){
            if(st.bladeX[i] == 0 && st.bladeY[i] == 0) continue;
            const prog = cl(st.blades[i] / create);
            drawSaw(st.bladeX[i], st.bladeY[i], BLADE_R, t * 25 + i * 40, prog >= 1 ? 1 : 0.15 + 0.35 * prog);
        }
        Draw.reset();
    },
    drawBlueSkills(t, r){
        if(st.level < 4 || r <= 0) return;
        const lr = r / LIM_DIV, self = this;
        Draw.color(BLUE_COLOR); Draw.alpha(0.35); Lines.stroke(1.5);
        for(let i = 0; i < 4; i++) Lines.arc(this.x, this.y, lr, 0.18, i * 90);
        Draw.alpha(0.05); Fill.circle(this.x, this.y, lr);
        Groups.bullet.intersect(this.x - lr, this.y - lr, lr * 2, lr * 2, cons(b => {
            if(b.team == self.team || !b.within(self.x, self.y, lr)) return;
            Draw.color(BLUE_COLOR, Color.white, 0.4); Draw.alpha(0.9); Lines.stroke(1);
            Lines.circle(b.x, b.y, 3 + Math.sin(t * 0.4 + b.id) * 1);
        }));
        Draw.reset();
    },
    drawConfigure(){
        this.super$drawConfigure();
        const r = this.affectRadiusWorld();
        if(r > 0) drawRange(this.x, this.y, r, formColor(st.form));
    }
    });
});