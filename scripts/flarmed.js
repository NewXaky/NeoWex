const C = (h) => Color.valueOf(h);
const cRed = C("e8321a"), cOr = C("ff8c1f"), cYel = C("ffd23a"), cWht = C("fff6b0"), cSoot = C("120a08"), cGry = C("5c5a58");
const sBong = extend(StatusEffect, "bong", { localizedName: "Bỏng", color: cOr, damage: 10 / 60, permanent: false });
const sViem = extend(StatusEffect, "viem-hoa", {
    localizedName: "Viêm hỏa", color: cRed,
    update(u, time) { u.damageContinuousPierce((100 + u.maxHealth * 0.01) / 60); }
});
const WIND = 8, GROW = 22, FADE = 30;

const widthAt = (u) => 4 + 20 * Math.pow(u, 1.2);
const pt = (mx, my, r, d, off) => [mx + Angles.trnsx(r, d) + Angles.trnsx(r + 90, off), my + Angles.trnsy(r, d) + Angles.trnsy(r + 90, off)];
const ease = (x) => Interp.smooth.apply(Mathf.clamp(x));
const rnd = (s) => { let x = Math.sin(s * 12.9898 + 78.233) * 43758.5453; return x - Math.floor(x); };

function drawJet(mx, my, r, len, a, T, id, main) {
    let N = 16, cols = [cRed, cOr, cYel, cWht], sc = [1, 0.75, 0.5, 0.24];
    for (let l = 0; l < 4; l++) {
        Draw.color(cols[l], a * (l == 0 ? 0.8 : 1));
        let prev = null;
        for (let i = 0; i <= N; i++) {
            let u = i / N, tap = 1 - 0.7 * Math.pow(u, 5);
            let w = widthAt(u) * sc[l] * 0.5 * tap * (0.85 + 0.15 * Math.sin(T * 0.35 - i * 0.6));
            let off = Math.sin(T * 0.3 - i * 0.7) * widthAt(u) * 0.15;
            let p1 = pt(mx, my, r, len * u, off + w), p2 = pt(mx, my, r, len * u, off - w);
            if (prev) Fill.quad(prev[0], prev[1], prev[2], prev[3], p2[0], p2[1], p1[0], p1[1]);
            prev = [p1[0], p1[1], p2[0], p2[1]];
        }
    }
    Draw.blend(Blending.additive);
    if (main) for (let j = 0; j < 3; j++) {
        Draw.color(j == 1 ? cWht : cOr, 0.5 * a);
        let lx = mx, ly = my;
        for (let i = 1; i <= 16; i++) {
            let u = i / 16, p = pt(mx, my, r, len * u, Math.sin(T * 0.22 + j * 2.1 + u * 7) * widthAt(u) * 0.55);
            Lines.stroke(2.4 * (1 - u * 0.6));
            Lines.line(lx, ly, p[0], p[1]);
            lx = p[0]; ly = p[1];
        }
    }
    let n = main ? 40 : 14;
    for (let k = 0; k < n; k++) {
        let s = id * 7 + k * 13, p = (T * 0.045 + rnd(s)) % 1, sd = rnd(s + 1) * 2 - 1;
        let q = pt(mx, my, r, len * p, sd * widthAt(p) * 0.45 + Math.sin(T * 0.2 + k) * 2);
        Draw.color(cWht, cYel, cOr, p);
        Draw.alpha(a * Math.pow(1 - p, 0.7) * 0.7);
        Fill.circle(q[0], q[1], 2 + widthAt(p) * 0.3 * (1 - p * 0.4));
    }
    Draw.blend();
}

function makeFlame(dmg, burnTime, range, lifetime) {
    return extend(BulletType, {
        speed: 0, damage: 0, lifetime: lifetime, collides: false, collidesTiles: false,
        collidesAir: false, collidesGround: false, absorbable: false, hittable: false,
        reflectable: false, keepVelocity: false, hitEffect: Fx.none, despawnEffect: Fx.none,
        shootEffect: Fx.none, smokeEffect: Fx.none, drawSize: range * 3, ammoMultiplier: 2,

        update(b) {
            if (Vars.state.isPaused() || Time.delta <= 0) return;
            let o = b.owner;
            if (o == null || !o.isValid()) { b.remove(); return; }
            if (b.data == null) b.data = o.rotation;
            let r = +b.data; o.rotation = r;
            let t = b.time, mx = o.x + Angles.trnsx(r, 14), my = o.y + Angles.trnsy(r, 14);
            let grow = ease((t - WIND) / GROW), len = range * grow;
            if (grow <= 0) return;
            let tp = pt(mx, my, r, len, 0);
            if (b.timer.get(4, 30)) Effect.shake(0.8 * grow, 10, tp[0], tp[1]);
            if (Vars.net.client() || grow < 0.2 || !b.timer.get(0, 6)) return;
            let cr = Math.cos(r * Mathf.degRad), sr = Math.sin(r * Mathf.degRad);
            let inCone = (tx, ty, hs) => {
                let dx = tx - mx, dy = ty - my, al = dx * cr + dy * sr, pp = Math.abs(-dx * sr + dy * cr);
                return al > 0 && al <= len + hs && pp <= widthAt(Math.min(al / range, 1)) * 0.6 + hs;
            };
            Units.nearbyEnemies(b.team, mx - range, my - range, range * 2, range * 2, new Cons({ get: u => {
                if (u.dead || u.isFlying() || !inCone(u.x, u.y, u.hitSize / 2)) return;
                u.damage(dmg);
                [sBong, sViem, StatusEffects.melting, StatusEffects.burning, StatusEffects.sapped].forEach(e => u.apply(e, burnTime));
            }}));
            Units.nearbyBuildings(mx, my, range + 16, new Cons({ get: bd => {
                if (bd.team == b.team || bd.team == Team.derelict || !inCone(bd.x, bd.y, bd.block.size * 4)) return;
                bd.damage(b.team, dmg * 0.7);
            }}));
        },

        draw(b) {
            let o = b.owner;
            if (o == null || !o.isValid()) return;
            let T = b.time;
            let r = b.data == null ? o.rotation : +b.data; o.rotation = r;
            let mx = o.x + Angles.trnsx(r, 14), my = o.y + Angles.trnsy(r, 14);
            let grow = ease((T - WIND) / GROW), pilot = ease(T / WIND);
            let a = 1 - Mathf.clamp((T - (lifetime - FADE)) / FADE);
            let len = range * grow;

            Draw.z(Layer.bullet + 1);
            Draw.blend(Blending.additive);
            let nz = pilot * a * (1 + 0.12 * Math.sin(T * 0.3));
            Draw.color(cOr, 0.22 * nz); Fill.circle(mx, my, 20 * nz);
            Draw.color(cYel, 0.4 * nz); Fill.circle(mx, my, 10 * nz);
            Draw.color(cWht, 0.8 * nz); Fill.circle(mx, my, 4.5 * nz);
            Draw.blend();
            if (grow <= 0) { Draw.reset(); return; }

            let tc = pt(mx, my, r, len, Math.sin(T * 0.3 - 16 * 0.7) * widthAt(1) * 0.15), fl = 1 + 0.1 * Math.sin(T * 0.5);

            Draw.z(Layer.bullet);
            for (let k = 0; k < 16; k++) {
                let s = b.id * 5 + k * 17, p = (T * 0.016 + rnd(s)) % 1;
                let q = pt(tc[0], tc[1], r, 40 * p + (rnd(s + 1) * 2 - 1) * 10, (rnd(s + 2) * 2 - 1) * 10 + Math.sin(T * 0.04 + k) * 6 * p);
                Draw.color(cSoot, cGry, p);
                Draw.alpha(0.55 * a * Math.sin(Math.PI * Math.pow(p, 0.6)));
                Fill.circle(q[0], q[1] + 12 * p, (8 + 20 * p) * (0.5 + 0.5 * grow));
            }
            Draw.z(Layer.bullet + 1);

            drawJet(mx, my, r, len, a, T, b.id, true);

            Draw.blend(Blending.additive);
            let S = 26 * grow * (0.4 + 0.6 * a) * fl;
            let lc = [cRed, cOr, cYel, cWht], ls = [1, 0.75, 0.5, 0.25];
            for (let l = 0; l < 4; l++) {
                Draw.color(lc[l], a * 0.55);
                for (let k = 0; k < 7; k++) {
                    let s = b.id * 31 + k, ang = k * 51 + T * 0.6 * (k % 2 ? 1 : -1), d = S * (0.2 + rnd(s) * 0.35);
                    Fill.circle(tc[0] + Angles.trnsx(ang, d), tc[1] + Angles.trnsy(ang, d), S * (0.4 + rnd(s + 1) * 0.2) * ls[l]);
                }
            }
            Draw.color(cOr, 0.14 * a); Fill.circle(tc[0], tc[1], S * 2.2);
            Draw.color(cYel, 0.18 * a); Fill.circle(tc[0], tc[1], S * 1.2);
            let sw = (T - WIND - GROW * 0.7) / 40;
            if (sw > 0 && sw < 1) {
                Lines.stroke(4 * (1 - sw)); Draw.color(cYel, 0.7 * (1 - sw));
                Lines.circle(tc[0], tc[1], S * (0.8 + 2 * sw));
            }
            Drawf.light(tc[0], tc[1], S * 6 + 20, cOr, 0.7 * a);
            for (let i = 1; i <= 4; i++) {
                let u = i / 4, p = pt(mx, my, r, len * u, 0);
                Drawf.light(p[0], p[1], widthAt(u) * 3 + 8, cOr, 0.3 * a);
            }
            for (let k = 0; k < 30; k++) {
                let s = b.id * 3 + k * 11, p = (T * 0.02 + rnd(s)) % 1;
                let e = pt(mx, my, r, len * (0.3 + 0.85 * p), (rnd(s + 1) * 2 - 1) * widthAt(p) * 1.4 + Math.sin(T * 0.05 + k) * 4);
                Draw.color(k % 3 == 0 ? cWht : cYel, a * (1 - p));
                Fill.circle(e[0], e[1] - p * p * 10, 1.8 * (1 - p) + 0.4);
            }
            Draw.blend(); Draw.reset();
        }
    });
}

Events.on(EventType.ContentInitEvent, () => {
    const t = Vars.content.getByName(ContentType.block, "newex-flarmed");
    if (t == null) return;
    t.ammoTypes.put(Items.coal, makeFlame(12, 240, t.range, 300));
    t.ammoTypes.put(Items.pyratite, makeFlame(18, 360, t.range, 300));
});
