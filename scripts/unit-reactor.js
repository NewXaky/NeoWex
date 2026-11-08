// Unit Reactor: hấp thụ unit (payload) -> điện vĩnh viễn theo cấp T1-T5.
// Chỉnh cân bằng tại 3 hằng số dưới đây (đơn vị điện: /tick, x60 = /giây).
const POWER_PER_TIER = [0.4, 1.5, 6, 20, 50];
const CAP_PER_TIER = [64, 32, 16, 8, 4];
const SYNERGY = 1.2; // có đủ 5 cấp -> +20%

const TIER_BY_NAME = {};
[
    // Serpulo
    ["dagger","nova","crawler","flare","mono","risso","retusa"],
    ["mace","pulsar","atrax","horizon","poly","minke","oxynoe"],
    ["fortress","quasar","spiroct","zenith","mega","bryde","cyerce"],
    ["scepter","vela","arkyid","antumbra","quad","sei","aegires"],
    ["reign","corvus","toxopid","eclipse","oct","omura","navanax"],
    // Erekir
    ["stell","merui","elude"],
    ["locus","cleroi","avert"],
    ["precept","anthicus","obviate"],
    ["vanquish","tecta","quell"],
    ["conquer","collaris","disrupt"]
].forEach((arr, i) => arr.forEach(n => TIER_BY_NAME[n] = (i % 5) + 1));

function tierOf(type) {
    let t = TIER_BY_NAME[type.name];
    if (t) return t;
    let h = type.health; // unit mod/NeoWex: suy theo máu
    return h <= 300 ? 1 : h <= 1000 ? 2 : h <= 5000 ? 3 : h <= 15000 ? 4 : 5;
}

const STATE = {}; // id khối -> {c: [số lượng mỗi cấp], p: điện/tick}
function st(b) { return STATE[b.id] || (STATE[b.id] = { c: [0, 0, 0, 0, 0], p: 0, q: [], pc: [0, 0, 0, 0, 0] }); }
function recalc(b) {
    let s = st(b), p = 0, all = true;
    for (let i = 0; i < 5; i++) {
        p += s.c[i] * POWER_PER_TIER[i];
        if (s.c[i] <= 0) all = false;
    }
    s.p = all ? p * SYNERGY : p;
}
let MAXP = 0;
for (let i = 0; i < 5; i++) MAXP += CAP_PER_TIER[i] * POWER_PER_TIER[i];
MAXP *= SYNERGY;

const TIER_COLORS = ["a0e6ff", "7fffa0", "ffe066", "ff9a4d", "ff5c8a"];


// ================= HIỆU ỨNG ĐIỆN ẢNH VŨ TRỤ =================
const WHITE = Color.white;
const COS45 = Math.SQRT1_2;

function iconOf(type) {
    try { return type.fullIcon; } catch (err) {}
    try { return type.region; } catch (err) {}
    return null;
}

// 1) Unit -> đốm sáng (0.2s) -> bay lên phải-trên, để lại vệt sáng mờ dần
const FLIGHT_LIFE = 135, T_SHRINK = 12, T_FLY = 75, FLY_DIST = 1020, FLY_ARC = 95;
const LASER_T0 = T_FLY + 4, APPLY_DELAY = LASER_T0 + 6; // laser bắn xuống sau khi đốm sáng biến mất, rồi mới cộng điện
function flightPos(x, y, s) { // s: 0..1 dọc quỹ đạo cong nhẹ
    let arc = Math.sin(s * Math.PI) * FLY_ARC;
    return [x + COS45 * (s * FLY_DIST) - COS45 * arc, y + COS45 * (s * FLY_DIST) + COS45 * arc];
}
const absorbFlight = new Effect(FLIGHT_LIFE, 1500, cons(e => {
    const d = e.data;
    if (d == null) return;
    const col = Color.valueOf(d.c);
    const T = e.fin() * FLIGHT_LIFE;
    Draw.z(Layer.effect + 0.5);

    if (T < T_SHRINK) { // giai đoạn 1: thu nhỏ thành đốm sáng
        let k = T / T_SHRINK;
        if (d.icon != null) {
            Draw.mixcol(col, Math.min(1, k * 1.6));
            Draw.alpha(1 - k * 0.5);
            let sc = 1 - k * k;
            Draw.rect(d.icon, e.x, e.y, d.w * sc, d.h * sc, d.rot - 90);
            Draw.mixcol();
        }
        Draw.blend(Blending.additive);
        Draw.color(col); Draw.alpha(0.25 + 0.5 * k);
        Fill.circle(e.x, e.y, 5 + 7 * k);
        Draw.color(WHITE); Draw.alpha(0.6 + 0.4 * k);
        Fill.circle(e.x, e.y, 1.5 + 2.5 * k);
        Draw.blend(); Draw.reset();
        return;
    }

    let f = Math.min(1, (T - T_SHRINK) / (T_FLY - T_SHRINK));
    let head = f * f * (0.4 + 0.6 * f);                  // tăng tốc dần
    let zoom = 1 + 5 * Math.pow(f, 1.5);                 // phóng to dần: như rời khỏi quỹ đạo
    let headA = T < T_FLY ? 1 - Math.pow(f, 2.2) : 0;     // mờ dần rồi biến mất
    let fade = T < T_FLY ? 1 : Math.max(0, 1 - (T - T_FLY) / (FLIGHT_LIFE - T_FLY));
    fade = fade * fade * (3 - 2 * fade);                  // vệt mờ dần mượt

    Draw.blend(Blending.additive);
    const N = 28;
    for (let pass = 0; pass < 2; pass++) {
        let prev = flightPos(e.x, e.y, 0);
        for (let i = 1; i <= N; i++) {
            let q = i / N, cur = flightPos(e.x, e.y, head * q);
            let a = Math.pow(q, 1.6) * fade;
            if (pass == 0) { Draw.color(col); Draw.alpha(a * 0.35); Lines.stroke((1.5 + 6 * q * (0.6 + 0.4 * zoom)) * (0.4 + 0.6 * fade)); }
            else { Draw.color(WHITE); Draw.alpha(a * 0.9); Lines.stroke((0.4 + 1.6 * q) * fade); }
            Lines.line(prev[0], prev[1], cur[0], cur[1], false);
            prev = cur;
        }
    }
    for (let i = 0; i < 10; i++) { // tia lấp lánh dọc vệt
        let q = Mathf.randomSeed(e.id * 13 + i), p = flightPos(e.x, e.y, head * q);
        let off = (Mathf.randomSeed(e.id * 29 + i) - 0.5) * 14;
        let tw = 0.5 + 0.5 * Math.sin(T * 0.35 + i * 2.1);
        Draw.color(col, WHITE, 0.5); Draw.alpha(q * fade * tw);
        Fill.circle(p[0] - COS45 * off, p[1] + COS45 * off, 0.8 + 1.2 * tw);
    }
    if (headA > 0) { // đốm sáng đầu: to dần + mờ dần
        let h = flightPos(e.x, e.y, head), r = 2.5 * zoom;
        Draw.color(col); Draw.alpha(0.35 * headA); Fill.circle(h[0], h[1], r * 3);
        Draw.color(col, WHITE, 0.5); Draw.alpha(0.55 * headA); Fill.circle(h[0], h[1], r * 1.6);
        Draw.color(WHITE); Draw.alpha(headA); Fill.circle(h[0], h[1], r);
    }
    if (T >= T_FLY && T < T_FLY + 12) { // chớp ngôi sao 2 cánh 0.2s tại điểm biến mất = dịch chuyển đi
        let k = (T - T_FLY) / 12, o = 1 - k, h = flightPos(e.x, e.y, 1);
        let len = 192 * Math.sin(Math.PI * Math.pow(k, 0.6)), wd = 14 * o + 2;
        let dx = COS45, dy = COS45, px = -COS45, py = COS45;
        for (let side = -1; side <= 1; side += 2) {
            Draw.color(col); Draw.alpha(0.6 * o);
            Fill.tri(h[0] + px * wd * 2, h[1] + py * wd * 2, h[0] - px * wd * 2, h[1] - py * wd * 2, h[0] + dx * len * side, h[1] + dy * len * side);
            Draw.color(WHITE); Draw.alpha(o);
            Fill.tri(h[0] + px * wd, h[1] + py * wd, h[0] - px * wd, h[1] - py * wd, h[0] + dx * len * 0.75 * side, h[1] + dy * len * 0.75 * side);
        }
        Draw.color(col); Draw.alpha(0.5 * o); Fill.circle(h[0], h[1], 36 * o);
        Draw.color(WHITE); Draw.alpha(o); Fill.circle(h[0], h[1], 14 * o);
    }
    Draw.blend(); Draw.reset();
}));

// 1b) Sau khi đốm sáng biến mất: laser từ trái-trên bắn xuống tâm khối
const absorbLaser = new Effect(FLIGHT_LIFE, 2600, cons(e => {
    const col = Color.valueOf(String(e.data));
    const T = e.fin() * FLIGHT_LIFE;
    if (T < LASER_T0) return;
    let tt = T - LASER_T0, grow = Math.min(1, tt / 6);
    let fd = tt < 14 ? 1 : Math.max(0, 1 - (tt - 14) / 22);
    if (fd <= 0) return;
    let sx = e.x - 1500, sy = e.y + 1700; // dài gấp 10 lần bản trước
    let ex = sx + (e.x - sx) * grow, ey = sy + (e.y - sy) * grow;
    let w = (1.5 + 4 * fd) * (1 + 0.12 * Math.sin(tt * 1.7));
    Draw.z(Layer.effect + 0.6);
    Draw.blend(Blending.additive);
    Draw.color(col); Draw.alpha(0.25 * fd); Lines.stroke(w * 3); Lines.line(sx, sy, ex, ey, false);
    Draw.alpha(0.6 * fd); Lines.stroke(w * 1.5); Lines.line(sx, sy, ex, ey, false);
    Draw.color(WHITE); Draw.alpha(fd); Lines.stroke(w * 0.6); Lines.line(sx, sy, ex, ey, false);
    Draw.color(col); Draw.alpha(0.5 * fd); Fill.circle(sx, sy, 6 * fd);
    Draw.color(WHITE); Draw.alpha(fd); Fill.circle(sx, sy, 2.5 * fd);
    if (grow >= 1) { // điểm chạm ở tâm lò
        Draw.color(col); Draw.alpha(0.45 * fd); Fill.circle(e.x, e.y, 14 + 10 * fd);
        Draw.color(WHITE); Draw.alpha(0.9 * fd); Fill.circle(e.x, e.y, 5 + 5 * fd);
    }
    Draw.blend(); Draw.reset();
}));

// 2) Bùng nổ sóng xung kích tại tâm (rotation = hệ số kích thước)
const absorbBurst = new Effect(34, 400, cons(e => {
    const col = Color.valueOf(String(e.data));
    const sc = e.rotation > 0 ? e.rotation : 1, k = e.fin(), o = 1 - k;
    Draw.z(Layer.effect + 0.4);
    Draw.blend(Blending.additive);
    Draw.color(WHITE); Draw.alpha(o * o); Fill.circle(e.x, e.y, 14 * sc * o);
    Draw.color(col); Draw.alpha(o * 0.45); Fill.circle(e.x, e.y, 24 * sc * (0.3 + 0.7 * k));
    Lines.stroke(2.5 * o * sc); Draw.color(col, WHITE, 0.3); Draw.alpha(o * 0.9);
    Lines.circle(e.x, e.y, 38 * sc * (1 - Math.pow(o, 3)));
    Draw.blend(); Draw.reset();
}));

// 3) Hạt bụi sao bay lơ lửng về phía phải-trên quanh lò
const cosmicMote = new Effect(50, 100, cons(e => {
    const col = Color.valueOf(String(e.data)), k = e.fin(), o = e.fout();
    Draw.z(Layer.effect);
    Draw.blend(Blending.additive);
    let x = e.x + COS45 * 22 * k, y = e.y + COS45 * 22 * k;
    Draw.color(col); Draw.alpha(0.35 * o); Lines.stroke(1.2 * o);
    Lines.line(x, y, x - COS45 * 7 * o, y - COS45 * 7 * o, false);
    Draw.color(WHITE); Draw.alpha(o * (0.6 + 0.4 * Math.sin(e.time))); Fill.circle(x, y, 0.4 + 1.1 * o);
    Draw.blend(); Draw.reset();
}));

// vẽ vành quỹ đạo elip nghiêng; trả về hàm lấy điểm
function ellipsePt(cx, cy, a, b, tilt, t) {
    let ca = Math.cos(tilt), sa = Math.sin(tilt), px = Math.cos(t) * a, py = Math.sin(t) * b;
    return [cx + px * ca - py * sa, cy + px * sa + py * ca];
}

Events.on(ContentInitEvent, () => {
    const block = Vars.content.getByName(ContentType.block, "newex-unit-reactor");
    if (block == null) return;

    block.addBar("nex-ur-power", b => new Bar(
        prov(() => "⚡ " + Math.round(st(b).p * 60) + " /s"),
        prov(() => Pal.powerBar),
        floatp(() => Math.min(1, st(b).p / MAXP))
    ));
    for (let i = 0; i < 5; i++) {
        const k = i;
        block.addBar("nex-ur-t" + (k + 1), b => new Bar(
            prov(() => "T" + (k + 1) + ": " + st(b).c[k] + " / " + CAP_PER_TIER[k]),
            prov(() => Color.valueOf(TIER_COLORS[k])),
            floatp(() => st(b).c[k] / CAP_PER_TIER[k])
        ));
    }

    block.buildType = () => extend(PayloadVoid.PayloadVoidBuild, block, {
        acceptPayload(source, payload) {
            if (this.payload != null || !this.enabled) return false;
            if (!(payload instanceof UnitPayload)) return false;
            let t = tierOf(payload.unit.type);
            let s = st(this);
            return s.c[t - 1] + s.pc[t - 1] < CAP_PER_TIER[t - 1];
        },

        updateTile() {
            let s = st(this), f = Math.min(1, s.p / MAXP);
            if (!Vars.headless && s.p > 0) {
                if (Mathf.chance(0.04 + 0.18 * f)) {
                    let ti = Math.floor(Mathf.random(5));
                    if (s.c[ti] > 0) cosmicMote.at(this.x + Mathf.range(15), this.y + Mathf.range(15), 0, TIER_COLORS[ti]);
                }
                s.t = (s.t || 0) + Time.delta;
                if (s.t > 200 - 90 * f) { s.t = 0; absorbBurst.at(this.x, this.y, 0.45 + 0.4 * f, "66ccff"); }
            }
            for (let i = s.q.length - 1; i >= 0; i--) { // áp dụng điện khi laser chạm tâm
                let it = s.q[i];
                it.d -= Time.delta;
                if (it.d <= 0) {
                    s.q.splice(i, 1);
                    s.pc[it.t - 1]--; s.c[it.t - 1]++;
                    recalc(this);
                    if (!Vars.headless) absorbBurst.at(this.x, this.y, 0.6 + 0.15 * it.t, TIER_COLORS[it.t - 1]);
                    try { Effect.shake(1 + it.t * 1.2, 10 + it.t * 3, this.x, this.y); } catch (err) {}
                }
            }
            if (this.payload == null) return;
            if (this.moveInPayload(false)) {
                let u = this.payload.unit, ty = u.type, t = tierOf(ty);
                if (s.c[t - 1] + s.pc[t - 1] < CAP_PER_TIER[t - 1]) {
                    s.pc[t - 1]++;
                    s.q.push({ t: t, d: APPLY_DELAY });
                    this.payload = null;
                    let ic = iconOf(ty), w = 16, h = 16;
                    if (ic != null) { w = ic.width / 4; h = ic.height / 4; }
                    absorbFlight.at(this.x, this.y, 0, { icon: ic, w: w, h: h, rot: u.rotation, c: TIER_COLORS[t - 1] });
                    absorbLaser.at(this.x, this.y, 0, TIER_COLORS[t - 1]);
                }
            }
        },

        getPowerProduction() {
            return this.enabled ? st(this).p : 0;
        },

        draw() {
            Draw.rect(block.region, this.x, this.y);
            Draw.z(Layer.blockOver);
            this.payRotation = 0;
            this.drawPayload();
            Draw.z(Layer.blockOver + 0.1);
            Draw.rect(block.topRegion, this.x, this.y);

            let s = st(this), f = Math.min(1, s.p / MAXP), tm = Time.time;
            let pulse = 1 + 0.08 * Math.sin(tm / 18), cx = this.x, cy = this.y;
            let live = s.p > 0 ? 1 : 0, glow = 0.25 + 0.75 * live;
            Draw.z(Layer.blockOver + 0.2);
            Draw.blend(Blending.additive);

            // quỹ đạo elip nghiêng cho từng cấp: sáng theo số lượng, hành tinh = unit đã nạp
            for (let i = 0; i < 5; i++) {
                let fill = s.c[i] / CAP_PER_TIER[i], A = 8 + i * 2.2, B = A * (0.3 + 0.04 * i);
                let tilt = (i * 0.63) + tm * (0.004 + 0.002 * i) * (i % 2 ? -1 : 1);
                let col = Color.valueOf(TIER_COLORS[i]);
                Draw.color(col); Draw.alpha(0.08 + 0.4 * fill);
                Lines.stroke(0.8 + 0.7 * fill);
                let pv = ellipsePt(cx, cy, A, B, tilt, 0);
                for (let j = 1; j <= 32; j++) {
                    let pt = ellipsePt(cx, cy, A, B, tilt, j / 32 * 6.2832);
                    Lines.line(pv[0], pv[1], pt[0], pt[1], false);
                    pv = pt;
                }
                let n = Math.min(4, Math.ceil(fill * 4));
                for (let j = 0; j < n; j++) {
                    let pt = ellipsePt(cx, cy, A, B, tilt, tm * (0.03 - 0.004 * i) + j * 6.2832 / n);
                    Draw.color(col); Draw.alpha(0.5); Fill.circle(pt[0], pt[1], 2 + i * 0.25);
                    Draw.color(WHITE); Draw.alpha(0.9); Fill.circle(pt[0], pt[1], 0.9 + i * 0.1);
                }
            }

            // lõi sao: nhiều lớp hào quang xanh -> tím -> trắng
            let R = (3.5 + 5 * f) * pulse;
            Draw.color(Color.valueOf("5a3cff")); Draw.alpha(0.12 * glow); Fill.circle(cx, cy, R * 3.4);
            Draw.color(Color.valueOf("8a6bff")); Draw.alpha(0.2 * glow); Fill.circle(cx, cy, R * 2.3);
            Draw.color(Color.valueOf("66ccff")); Draw.alpha(0.45 * glow); Fill.circle(cx, cy, R * 1.5);
            Draw.color(WHITE); Draw.alpha(0.9 * glow); Fill.circle(cx, cy, R * 0.8);

            // tia sáng xoay + lens flare chữ thập
            Draw.color(Color.valueOf("9fe3ff"));
            Lines.stroke(0.7);
            for (let i = 0; i < 10; i++) {
                Draw.alpha((0.12 + 0.25 * f) * glow * (0.6 + 0.4 * Math.sin(tm / 12 + i)));
                Lines.lineAngle(cx, cy, tm * 0.4 + i * 36, 14 + 30 * f + (i % 2) * 10);
            }
            Draw.color(WHITE); Lines.stroke(1.1);
            Draw.alpha((0.25 + 0.35 * f) * glow);
            let L = 22 + 55 * f * pulse;
            Lines.lineAngle(cx, cy, 0, L); Lines.lineAngle(cx, cy, 180, L);
            Lines.lineAngle(cx, cy, 90, L * 0.55); Lines.lineAngle(cx, cy, 270, L * 0.55);
            Draw.blend(); Draw.reset();
        },

        drawLight() {
            let f = Math.min(1, st(this).p / MAXP);
            Drawf.light(this.x, this.y, 50 + 110 * f, Color.valueOf("66ccff"), 0.45 + 0.4 * f);
        },

        onDestroyed() {
            let p = st(this).p;
            this.super$onDestroyed();
            if (p > 0) {
                Damage.dynamicExplosion(this.x, this.y, 0, Math.min(p * 2, 3000), Math.min(p * 10, 20000), Vars.tilesize * 5, Vars.state.rules.damageExplosions);
            }
        },

        onRemoved() {
            this.super$onRemoved();
            delete STATE[this.id];
        },

        write(write) {
            this.super$write(write);
            write.b(1);
            let c = st(this).c;
            for (let i = 0; i < 5; i++) write.s(c[i] + st(this).pc[i]);
        },

        read(read, revision) {
            this.super$read(read, revision);
            if (read.b() >= 1) {
                let c = st(this).c;
                for (let i = 0; i < 5; i++) c[i] = read.s();
                recalc(this);
            }
        }
    });
});
