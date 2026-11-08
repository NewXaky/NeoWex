const NexNet = global.NexNet;
const PerfCore = global.PerfCore;

function isBloomEnabled(){
    try {
        return Core.settings.getBool("bloom", true);
    } catch(e){
        return true;
    }
}
print("LAVUNDER - OPTIMIZED & NO-LIMIT SYSTEM LOADED");

const deadZone = 40;
const laserColor = Color.valueOf("bf7fff");
const particleColor = Color.valueOf("e8bfff");
const greenLaserColor = Color.valueOf("33ff55");
const amberTextColor = Color.valueOf("ffaa00");

const reqMK2 = { copper: 400, graphite: 400 };
const reqMK3 = { lead: 450, silicon: 570 };

const turretTierMap = new ObjectMap();
const turretChargeMap = new ObjectMap();
const turretBurstTimerMap = new ObjectMap();
const turretMilestoneMap = new ObjectMap();
const mk3TripleExplosionMap = new ObjectMap();
const greenLaserRenderMap = new ObjectMap();
const turretDamageTimerMap = new ObjectMap();

const packCons2 = (func) => new Cons2({ get: func });
const packRun = (func) => new java.lang.Runnable({ run: func });
const packProv = (func) => new Prov({ get: func });

function dashCircle(x, y, radius, color){
    Draw.color(color);
    let segments = 48;
    for(let i = 0; i < segments; i += 2){
        let a1 = i / segments * 360;
        let a2 = (i + 1) / segments * 360;
        Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius), x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
    }
    Draw.reset();
}

function drawLaserRing(cx, cy, radiusX, radiusY, laserAngle, strokeWidth, color){
    Draw.color(color); Lines.stroke(strokeWidth);
    let steps = 16; let lastX = 0, lastY = 0;
    let cosA = Math.cos(laserAngle * Mathf.degRad); let sinA = Math.sin(laserAngle * Mathf.degRad);
    for(let i = 0; i <= steps; i++){
        let angle = (i * (360 / steps)) * Mathf.degRad;
        let lx = Math.cos(angle) * radiusX; let ly = Math.sin(angle) * radiusY;
        let rx = cx + (lx * cosA - ly * sinA); let ry = cy + (lx * sinA + ly * cosA);
        if(i > 0) Lines.line(lastX, lastY, rx, ry);
        lastX = rx; lastY = ry;
    }
}


// ===== TIA LASER KHỔNG LỒ (Exo Beam) =====
// Khi đạt 100 sạc: 50% bắn một tia laser hồng-trắng quét ngang như trong video, có chớp sao 4 cánh ở gốc.
const EXO_CHANCE = 0.5;
const EXO_PRE_TICKS = 12;          // chớp sáng tích năng lượng trước khi tia phóng ra
const EXO_DURATION = 96;           // thời gian tia tồn tại (tick)
const EXO_SWEEP_DEG = 90;          // góc quét tổng cộng
const EXO_LEN = 420;               // chiều dài tia
const EXO_W0 = 34;                 // bề rộng ở gốc
const EXO_W1 = 110;                // bề rộng ở đầu xa (tia loe dần)
const EXO_PULSE_TICKS = 6;         // chu kỳ gây sát thương
const EXO_DMG_FLAT = 150;          // sát thương mỗi nhịp (nhân theo cấp)
const EXO_DMG_PCT = 0.005;         // + % máu tối đa mỗi nhịp
const EXO_TIER_MULT = { 1: 1.0, 2: 3.0, 3: 2.5 };

const exoColGlow = Color.valueOf("ff2fa8");
const exoColPink = Color.valueOf("ff6fd0");
const exoColLight = Color.valueOf("ffc4ee");
const exoColGold = Color.valueOf("ffd98a");
const exoColCyan = Color.valueOf("35f2e0");
const exoColTeal = Color.valueOf("17b8c9");
const exoColPurple = Color.valueOf("b47bff");

const exoBeamList = [];
const exoCountMap = new ObjectMap();

function exoClamp(v){ return Math.max(0, Math.min(1, v)); }

// độ mạnh của tia theo thời gian: phóng nhanh, giữ, rồi tắt dần
function exoEnv(t){
    if(t < 0) return 0;
    return exoClamp(t / 6) * exoClamp((EXO_DURATION - t) / 22);
}

function exoBeamAngle(bm){
    let k = exoClamp(bm.t / EXO_DURATION);
    let sm = k * k * (3 - 2 * k);
    return bm.base + bm.side * (-EXO_SWEEP_DEG / 2 + EXO_SWEEP_DEG * sm);
}

// 50% bắn tia khi đạt 100 sạc. Quyết định theo seed (id tháp + số lần) để server và client luôn thống nhất.
function triggerExoBeam(b, turretId, tier, target){
    let owner = b.owner;
    if(!owner || !target) return;
    let cnt = exoCountMap.containsKey(turretId) ? exoCountMap.get(turretId) : 0;
    exoCountMap.put(turretId, cnt + 1);
    if(Mathf.randomSeed(turretId * 131 + cnt * 17 + 5) >= EXO_CHANCE) return;

    for(let i = 0; i < exoBeamList.length; i++){
        if(exoBeamList[i].id == turretId) return;
    }

    exoBeamList.push({
        id: turretId,
        owner: owner,
        team: b.team,
        tier: tier,
        x: owner.x,
        y: owner.y,
        base: Angles.angle(owner.x, owner.y, target.x, target.y),
        side: Mathf.randomSeed(turretId * 71 + cnt * 13 + 9) < 0.5 ? 1 : -1,
        t: -EXO_PRE_TICKS,
        pulse: 0,
        seed: turretId * 0.137 + cnt * 3.1,
        started: false
    });
}

function exoPulse(bm, ang){
    let cosA = Math.cos(ang * Mathf.degRad), sinA = Math.sin(ang * Mathf.degRad);
    let mult = EXO_TIER_MULT[bm.tier] || 1.0;
    let flat = EXO_DMG_FLAT * mult;
    let r = EXO_LEN + 60;

    Units.nearbyEnemies(bm.team, bm.x - r, bm.y - r, r * 2, r * 2, u => {
        if(!u || u.dead || u.isFlying()) return;
        let dx = u.x - bm.x, dy = u.y - bm.y;
        let s = dx * cosA + dy * sinA;
        if(s < 0 || s > EXO_LEN) return;
        let perp = Math.abs(-dx * sinA + dy * cosA);
        let hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5 + u.hitSize / 2;
        if(perp > hw) return;
        u.damage(flat + u.maxHealth * EXO_DMG_PCT);
        Fx.hitLancer.at(u.x, u.y, exoColPink);
    });

    let seen = {};
    for(let s = 0; s <= EXO_LEN; s += 14){
        let hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5;
        for(let q = -1; q <= 1; q++){
            let off = q * hw * 0.7;
            let px = bm.x + cosA * s - sinA * off;
            let py = bm.y + sinA * s + cosA * off;
            let bld = Vars.world.buildWorld(px, py);
            if(bld != null && bld.team != bm.team && !seen[bld.pos()]){
                seen[bld.pos()] = true;
                bld.damage(flat + bld.maxHealth * EXO_DMG_PCT);
                Fx.hitLancer.at(bld.x, bld.y, exoColPink);
            }
        }
    }
}

function exoStrip(cx, cy, ux, uy, len, seg, wScale, env, color, alphaBase){
    let nx = -uy, ny = ux;
    Draw.color(color);
    for(let i = 0; i < seg; i++){
        let s0 = len * i / seg, s1 = len * (i + 1) / seg;
        let h0 = Mathf.lerp(EXO_W0, EXO_W1, s0 / EXO_LEN) * 0.5 * wScale * env;
        let h1 = Mathf.lerp(EXO_W0, EXO_W1, s1 / EXO_LEN) * 0.5 * wScale * env;
        let a = (1.0 - 0.5 * ((i + 0.5) / seg)) * Math.min(1.0, (seg - i) / 3.0);
        Draw.alpha(alphaBase * a);
        Fill.quad(
            cx + ux * s0 + nx * h0, cy + uy * s0 + ny * h0,
            cx + ux * s1 + nx * h1, cy + uy * s1 + ny * h1,
            cx + ux * s1 - nx * h1, cy + uy * s1 - ny * h1,
            cx + ux * s0 - nx * h0, cy + uy * s0 - ny * h0
        );
    }
}

// Vẽ tia: lõi trắng nóng, viền hồng rực, chớp sao 4 cánh ở gốc, mảnh vỡ xanh lam bay dọc tia, tia tím lấp lánh quanh gốc
const exoBeamFx = new Effect(2, 1500, cons(e => {
    let d = e.data;
    if(!d) return;
    let t = d.t;
    let env = exoEnv(t);
    let flare = t < 0 ? exoClamp((t + EXO_PRE_TICKS) / EXO_PRE_TICKS) * 0.8 : Math.max(env, 0.0);
    if(flare <= 0.01) return;

    let lod = PerfCore.level();
    let ang = e.rotation;
    let cx = e.x + Angles.trnsx(ang, 8), cy = e.y + Angles.trnsy(ang, 8);
    let ux = Angles.trnsx(ang, 1), uy = Angles.trnsy(ang, 1);
    let nx = -uy, ny = ux;
    let flick = 0.9 + 0.1 * Math.sin(t * 1.7);

    Draw.z(Layer.effect + 0.2);
    Draw.blend(Blending.additive);

    if(env > 0.01){
        let len = EXO_LEN * (0.35 + 0.65 * exoClamp(t / 5));
        let seg = lod >= 2 ? 10 : 22;
        exoStrip(cx, cy, ux, uy, len, seg, 1.9 * flick, env, exoColGlow, 0.26);
        exoStrip(cx, cy, ux, uy, len, seg, 1.25 * flick, env, exoColPink, 0.5);
        exoStrip(cx, cy, ux, uy, len, seg, 0.78, env, exoColLight, 0.75);
        exoStrip(cx, cy, ux, uy, len, seg, 0.4 * flick, env, Color.white, 0.95);

        // hai đường viền vàng nhạt dọc mép tia
        Draw.color(exoColGold);
        Draw.alpha(0.6 * env);
        Lines.stroke(2.0);
        let eh0 = EXO_W0 * 0.5 * 0.95 * env, eh1 = EXO_W1 * 0.5 * 0.95 * env * (len / EXO_LEN);
        Lines.line(cx + nx * eh0, cy + ny * eh0, cx + ux * len + nx * eh1, cy + uy * len + ny * eh1);
        Lines.line(cx - nx * eh0, cy - ny * eh0, cx + ux * len - nx * eh1, cy + uy * len - ny * eh1);

        // mảnh vỡ xanh lam/tím bay dọc theo tia
        let shards = lod >= 2 ? 10 : 36;
        for(let j = 0; j < shards; j++){
            let ph = (d.seed * 0.37 + j * 0.6180339 + t * (0.012 + (j % 5) * 0.004)) % 1.0;
            let s = ph * len * 0.97;
            let hw = Mathf.lerp(EXO_W0, EXO_W1, s / EXO_LEN) * 0.5 * env;
            let lat = Mathf.randomSeed(Math.floor(d.seed * 100) + j * 7, -1.0, 1.0) * hw * 1.7 + Math.sin(t * 0.3 + j) * 3.0;
            let px = cx + ux * s + nx * lat, py = cy + uy * s + ny * lat;
            let col = (j % 4 == 0) ? exoColPurple : ((j % 4 == 1) ? exoColTeal : ((j % 4 == 2) ? exoColCyan : Color.white));
            Draw.color(col);
            Draw.alpha(env * (0.55 + 0.45 * Math.sin(t * 0.5 + j * 1.9)) * (1.0 - ph * 0.5));
            Fill.square(px, py, 1.1 + (j % 3) * 0.9, t * 3.0 + j * 40.0);
        }
    }

    // chớp sao 4 cánh ở gốc (kiểu ống kính anamorphic)
    let L = 210.0 * flare * flick;
    let wd = 8.0 * flare + 1.0;
    Draw.color(exoColPink);
    Draw.alpha(0.4 * flare);
    let L2 = L * 1.18, wd2 = wd * 2.0;
    Fill.tri(cx - L2, cy, cx, cy + wd2, cx, cy - wd2);
    Fill.tri(cx + L2, cy, cx, cy + wd2, cx, cy - wd2);
    Fill.tri(cx, cy - L2 * 0.9, cx + wd2, cy, cx - wd2, cy);
    Fill.tri(cx, cy + L2 * 0.9, cx + wd2, cy, cx - wd2, cy);
    Draw.color(Color.white);
    Draw.alpha(0.95 * flare);
    Fill.tri(cx - L, cy, cx, cy + wd, cx, cy - wd);
    Fill.tri(cx + L, cy, cx, cy + wd, cx, cy - wd);
    Fill.tri(cx, cy - L * 0.85, cx + wd, cy, cx - wd, cy);
    Fill.tri(cx, cy + L * 0.85, cx + wd, cy, cx - wd, cy);
    // cặp tia chéo ngắn
    let Ld = L * 0.4, wdd = wd * 0.6;
    for(let q = 0; q < 2; q++){
        let a = 45.0 + q * 90.0;
        let dx = Angles.trnsx(a, Ld), dy = Angles.trnsy(a, Ld);
        let px = Angles.trnsx(a + 90.0, wdd), py = Angles.trnsy(a + 90.0, wdd);
        Fill.tri(cx - dx, cy - dy, cx + px, cy + py, cx - px, cy - py);
        Fill.tri(cx + dx, cy + dy, cx + px, cy + py, cx - px, cy - py);
    }

    // lõi sáng + quầng
    Draw.color(exoColPink);
    Draw.alpha(0.35 * flare);
    Fill.circle(cx, cy, 70.0 * flare);
    Draw.color(Color.white);
    Draw.alpha(0.9 * flare);
    Fill.circle(cx, cy, 30.0 * flare * flick);
    Draw.alpha(0.5 * flare);
    Lines.stroke(2.0);
    Lines.circle(cx, cy, 52.0 * flare + 6.0 * Math.sin(t * 0.4));

    // tia lấp lánh tím quanh gốc
    let sp = lod >= 2 ? 3 : 9;
    for(let j = 0; j < sp; j++){
        let a = j * (360.0 / sp) + t * (2.0 + (j % 3));
        let r = 34.0 + (j % 4) * 16.0 + 6.0 * Math.sin(t * 0.3 + j);
        let sx = cx + Angles.trnsx(a, r), sy = cy + Angles.trnsy(a, r);
        let tw = 0.5 + 0.5 * Math.sin(t * 0.6 + j * 2.3);
        let sz = (3.0 + (j % 3) * 2.0) * tw * flare;
        Draw.color(j % 2 == 0 ? exoColPurple : Color.white);
        Draw.alpha(flare * tw);
        Lines.stroke(1.6);
        Lines.line(sx - sz, sy, sx + sz, sy);
        Lines.line(sx, sy - sz, sx, sy + sz);
    }
    Draw.blend();

    if(isBloomEnabled()){
        Drawf.light(cx, cy, 220.0 * flare, exoColPink, 0.9 * flare);
        if(env > 0.01){
            for(let q = 1; q <= 3; q++){
                Drawf.light(cx + ux * (EXO_LEN * q / 3.5), cy + uy * (EXO_LEN * q / 3.5), 130.0 * env, exoColPink, 0.6 * env);
            }
        }
    }
    Draw.reset();
}));

Events.run(Trigger.update, () => {
    if(exoBeamList.length == 0 || Vars.state.isPaused()) return;
    for(let i = exoBeamList.length - 1; i >= 0; i--){
        let bm = exoBeamList[i];
        let o = bm.owner;
        if(!o || !o.isValid() || bm.t >= EXO_DURATION){
            exoBeamList.splice(i, 1);
            continue;
        }
        bm.x = o.x; bm.y = o.y;
        bm.t += Time.delta;
        let ang = exoBeamAngle(bm);

        if(bm.t >= 0 && !bm.started){
            bm.started = true;
            if(!Vars.headless){
                Effect.shake(12, 22, bm.x, bm.y);
                try { Sounds.laserbig.at(bm.x, bm.y, 0.9, 1.0); } catch(err){}
            }
        }

        exoBeamFx.at(bm.x, bm.y, ang, { t: bm.t, seed: bm.seed });

        if(bm.t >= 0){
            if(!Vars.headless && Mathf.chance(0.35)) Effect.shake(3.5, 5, bm.x, bm.y);
            bm.pulse += Time.delta;
            if(bm.pulse >= EXO_PULSE_TICKS){
                bm.pulse = 0;
                if(!Vars.net.client()) exoPulse(bm, ang);
            }
        }
    }
});

const lavunderLaserBullet = extend(BulletType, {
    init(b){
        if(!b) return;
        b.data = {
            hasTarget: false,
            lastX: b.x + Angles.trnsx(b.rotation(), 260),
            lastY: b.y + Angles.trnsy(b.rotation(), 260)
        };
    },

    update(b){
        if(!b || !b.owner) return;

        let turretId = b.owner.id;
        let range = 260;

        if(!turretTierMap.containsKey(turretId)) turretTierMap.put(turretId, 1);
        if(!turretChargeMap.containsKey(turretId)) turretChargeMap.put(turretId, 0);
        if(!turretBurstTimerMap.containsKey(turretId)) turretBurstTimerMap.put(turretId, 0);
        if(!turretMilestoneMap.containsKey(turretId)) turretMilestoneMap.put(turretId, 0);
        if(!turretDamageTimerMap.containsKey(turretId)) turretDamageTimerMap.put(turretId, 0);

        let tier = turretTierMap.get(turretId);
        let currentPoints = turretChargeMap.get(turretId);
        let burstTimer = turretBurstTimerMap.get(turretId);
        let dmgTimer = turretDamageTimerMap.get(turretId);

        if(burstTimer > 0){
            burstTimer--;
            turretBurstTimerMap.put(turretId, burstTimer);
        }

        if(greenLaserRenderMap.containsKey(turretId)){
            let gData = greenLaserRenderMap.get(turretId);
            if(gData.duration > 0){
                gData.duration--;
            } else {
                greenLaserRenderMap.remove(turretId);
            }
        }

        let baseDamage = 112;
        if(tier == 2){
            baseDamage = 112;
        } else if(tier == 3){
            baseDamage = 112 * 1.71;
        }
        let currentTickDamage = baseDamage * (1 + (currentPoints / 100));

        if(mk3TripleExplosionMap.containsKey(turretId)){
            let exData = mk3TripleExplosionMap.get(turretId);
            if(exData.count > 0){
                exData.timer--;
                if(exData.timer <= 0){
                    greenLaserRenderMap.put(turretId, {
                        x: exData.x,
                        y: exData.y,
                        duration: 12
                    });

                    Fx.scatheExplosion.at(exData.x, exData.y);
                    Effect.shake(6, 6, exData.x, exData.y);

                    let targetRadius = 40;
                    let m100DamageValue = 2400 + (currentTickDamage * 3.0);

                    Units.nearbyEnemies(b.team, exData.x - targetRadius, exData.y - targetRadius, targetRadius * 2, targetRadius * 2, u => {
                        if(u && !u.dead && !u.isFlying() && Mathf.dst(exData.x, exData.y, u.x, u.y) <= targetRadius){
                            u.damage(m100DamageValue);
                        }
                    });

                    exData.count--;
                    exData.timer = 30;
                }
            } else {
                mk3TripleExplosionMap.remove(turretId);
            }
        }

        let target = Units.closestTarget(
            b.team, b.x, b.y, range,
            u => u && !u.dead && !u.isFlying() && Mathf.dst(b.x, b.y, u.x, u.y) >= deadZone,
            t => true
        );

        if(target){
            b.data.hasTarget = true;
            b.data.lastX = target.x;
            b.data.lastY = target.y;

            dmgTimer++;
            if(dmgTimer >= 60){
                target.damage(currentTickDamage);
                dmgTimer = 0;
            }
            turretDamageTimerMap.put(turretId, dmgTimer);

            if(currentPoints < 100){
                let chargeSpeedMultiplier = 1 + Math.pow(currentPoints / 50, 2);
                let speedBonus = (tier == 2) ? 3.8 : 1.0;

                currentPoints += (15 / 60) * chargeSpeedMultiplier * speedBonus;

                if(tier == 1 || tier == 2){
                    if(currentPoints >= 100){
                        let refundPoints = (tier == 2 && Mathf.chance(0.50)) ? 80 : 0;
                        currentPoints = refundPoints;

                        turretBurstTimerMap.put(turretId, 14);

                        let explosionRadius = (tier == 2) ? 40 : 60;
                        let hpPercent = (tier == 2) ? 0.02 : 0.01;
                        let flatDamage = (tier == 2) ? 3200 : 1000;

                        Units.nearbyEnemies(b.team, target.x - explosionRadius, target.y - explosionRadius, explosionRadius * 2, explosionRadius * 2, u => {
                            if(u && !u.dead && !u.isFlying() && Mathf.dst(target.x, target.y, u.x, u.y) <= explosionRadius){
                                u.damage((u.maxHealth * hpPercent) + flatDamage);
                            }
                        });

                        Fx.scatheExplosion.at(target.x, target.y);
                        Effect.shake(tier == 2 ? 8 : 6, tier == 2 ? 8 : 6, target.x, target.y);

                        // 50% bắn thêm một tia laser khổng lồ
                        triggerExoBeam(b, turretId, tier, target);
                    }
                }
                else if(tier == 3) {
                    let milestones = [20, 40, 60, 80];
                    let lastScannedMilestone = turretMilestoneMap.get(turretId);

                    for(let i = 0; i < milestones.length; i++){
                        let stone = milestones[i];
                        if(currentPoints >= stone && lastScannedMilestone < stone){
                            turretMilestoneMap.put(turretId, stone);

                            if(Mathf.chance(0.70)){
                                greenLaserRenderMap.put(turretId, {
                                    x: target.x,
                                    y: target.y,
                                    duration: 12
                                });

                                Fx.scatheExplosion.at(target.x, target.y);
                                let targetRadius = 40;
                                let subMilestoneDamage = 800 + (currentTickDamage * 15.0);

                                Units.nearbyEnemies(b.team, target.x - targetRadius, target.y - targetRadius, targetRadius * 2, targetRadius * 2, u => {
                                    if(u && !u.dead && !u.isFlying() && Mathf.dst(target.x, target.y, u.x, u.y) <= targetRadius){
                                        u.damage(subMilestoneDamage);
                                    }
                                });
                            }
                        }
                    }

                    if(currentPoints >= 100){
                        currentPoints = 0;
                        turretMilestoneMap.put(turretId, 0);
                        turretBurstTimerMap.put(turretId, 14);

                        if(Mathf.chance(0.70)){
                            greenLaserRenderMap.put(turretId, {
                                    x: target.x,
                                    y: target.y,
                                    duration: 12
                            });

                            Fx.scatheExplosion.at(target.x, target.y);
                            let targetRadius = 40;
                            let firstBlastDamage = 2400 + (currentTickDamage * 3.0);

                            Units.nearbyEnemies(b.team, target.x - targetRadius, target.y - targetRadius, targetRadius * 2, targetRadius * 2, u => {
                                if(u && !u.dead && !u.isFlying() && Mathf.dst(target.x, target.y, u.x, u.y) <= targetRadius){
                                    u.damage(firstBlastDamage);
                                }
                            });
                        }

                        if(Mathf.chance(0.80)){
                            mk3TripleExplosionMap.put(turretId, {
                                count: 3,
                                timer: 30,
                                x: target.x,
                                y: target.y
                            });
                        }

                        // 50% bắn thêm một tia laser khổng lồ
                        triggerExoBeam(b, turretId, tier, target);
                    }
                }
                turretChargeMap.put(turretId, currentPoints);
            }

            if(Mathf.chance(0.25)){
                Fx.hitLancer.at(target.x, target.y, laserColor);
            }
        } else {
            b.data.hasTarget = false;
            b.data.lastX = b.x + Angles.trnsx(b.rotation(), range);
            b.data.lastY = b.y + Angles.trnsy(b.rotation(), range);

            turretDamageTimerMap.put(turretId, 0);

            if(currentPoints > 0){
                currentPoints -= (2 / 60);
                if(currentPoints < 0) currentPoints = 0;
                turretChargeMap.put(turretId, currentPoints);
                if(currentPoints == 0) turretMilestoneMap.put(turretId, 0);
            }
        }
    },

    draw(b){
        if(!b || !b.data || !b.owner) return;

        let turretId = b.owner.id;
        let startX = b.x; let startY = b.y;
        let endX = b.data.lastX; let endY = b.data.lastY;
        let dist = Mathf.dst(startX, startY, endX, endY);
        let laserAngle = Angles.angle(startX, startY, endX, endY);

        let currentPoints = turretChargeMap.containsKey(turretId) ? turretChargeMap.get(turretId) : 0;
        let burstTimer = turretBurstTimerMap.containsKey(turretId) ? turretBurstTimerMap.get(turretId) : 0;

        if(greenLaserRenderMap.containsKey(turretId)){
            let gData = greenLaserRenderMap.get(turretId);
            let zoom = gData.duration / 12;
            Draw.color(greenLaserColor);
            Lines.stroke(16.0 * zoom);
            Lines.line(startX, startY, gData.x, gData.y);
            Draw.color(Color.white);
            Lines.stroke(5.0 * zoom);
            Lines.line(startX, startY, gData.x, gData.y);
        }

        if(burstTimer > 0){
            let zoomFactor = burstTimer / 14;
            Draw.color(Color.white);
            Lines.stroke(9.0 * zoomFactor);
            Lines.line(startX, startY, endX, endY);
        } else {
            Draw.color(laserColor); Lines.stroke(0.3); Lines.line(startX, startY, endX, endY);
            Draw.color(Color.white); Lines.stroke(0.1); Lines.line(startX, startY, endX, endY);
        }

        if(currentPoints > 2) {
            let scaleFactor = 1 + (currentPoints / 180);
            let ringCount = 2;
            for(let r = 0; r < ringCount; r++){
                let ringProgress = ((Time.time * 0.8) + (r * (dist / ringCount))) % dist;
                let rt = ringProgress / dist;
                let rx = Mathf.lerp(startX, endX, rt); let ry = Mathf.lerp(startY, endY, rt);
                drawLaserRing(rx, ry, 1.5 * scaleFactor, 4.0 * scaleFactor, laserAngle, 0.4 * Mathf.sin(rt * Math.PI), laserColor);
            }

            let particleCount = Math.floor(Mathf.lerp(4, 16, currentPoints / 100));
            Draw.color(particleColor);
            for(let i = 0; i < particleCount; i++){
                let progress = ((Time.time * (0.4 + (i % 3) * 0.25)) + (i * (dist / particleCount))) % dist;
                let t = progress / dist;
                let px = Mathf.lerp(startX, endX, t); let py = Mathf.lerp(startY, endY, t);
                let currentAngle = (Time.time * (1.0 + (i % 4) * 0.7)) + (i * (360 / particleCount));
                let rad = currentAngle * Mathf.degRad;
                let ox = px + ((Math.cos(rad) * 1.2 * scaleFactor) * Math.cos(laserAngle * Mathf.degRad) - (Math.sin(rad) * 3.5 * scaleFactor) * Math.sin(laserAngle * Mathf.degRad));
                let oy = py + ((Math.cos(rad) * 1.2 * scaleFactor) * Math.sin(laserAngle * Mathf.degRad) + (Math.sin(rad) * 3.5 * scaleFactor) * Math.cos(laserAngle * Mathf.degRad));
                Fill.circle(ox, oy, 0.4 + ((i % 3) * 0.2));
            }
        }

        if(b.data.hasTarget){
            let baseRadius = 3.5 + (currentPoints * 0.05);
            let ringRadius = baseRadius * 1.6;

            Draw.color(laserColor);
            Lines.stroke(0.8 + (Mathf.sin(Time.time * 0.15) * 0.3));
            Lines.circle(endX, endY, ringRadius);

            Draw.color(particleColor);
            Fill.circle(endX, endY, baseRadius);
            Draw.color(Color.white);
            Fill.circle(endX, endY, baseRadius * 0.55);

            let pCount = Math.floor(Mathf.lerp(3, 8, currentPoints / 100));
            Draw.color(particleColor);
            for(let i = 0; i < pCount; i++){
                let hSeed = i * 45.7 + turretId;
                let baseProgress = (Time.time * 0.4 + hSeed) % 1.0;
                let acceleratedProgress = Math.pow(baseProgress, 3);
                let invT = 1.0 - acceleratedProgress;
                let pAngle = (hSeed + i * (360 / pCount)) % 360;
                let startDist = ringRadius * 3.5;
                let curDist = startDist * invT;

                let finalAngle = pAngle + (acceleratedProgress * 80.0);
                let hx = endX + Angles.trnsx(finalAngle, curDist);
                let hy = endY + Angles.trnsy(finalAngle, curDist);
                Fill.circle(hx, hy, 0.3 + (acceleratedProgress * 0.5));
            }
        }

        if(currentPoints > 0) {
            Draw.color(amberTextColor);
            Fonts.outline.getData().setScale(0.25);
            Fonts.outline.draw(java.lang.String.valueOf(Math.floor(currentPoints)), b.owner.x, b.owner.y + 3, Align.center);
            Fonts.outline.getData().setScale(1.0);
        }
        Draw.reset();
    }
});

lavunderLaserBullet.speed = 0;
lavunderLaserBullet.damage = 112;
lavunderLaserBullet.lifetime = 12;
lavunderLaserBullet.collides = false;

const lavunder = extend(PowerTurret, "lavunder", {
    init(){ this.super$init(); },
    load(){
        this.super$load();
        this.bodyRegion = PerfCore.region(this.name + "-body");
        this.downxRegion = PerfCore.region(this.name + "-downx");
        this.a1Region = PerfCore.region(this.name + "-a1");
        this.a2Region = PerfCore.region(this.name + "-a2");
    },
    drawPlace(x, y, rotation, valid){
        this.super$drawPlace(x, y, rotation, valid);
        Draw.color(Pal.remove); Lines.stroke(2);
        dashCircle(x * Vars.tilesize, y * Vars.tilesize, deadZone, Pal.remove);
        Draw.reset();
    }
});

lavunder.health = 3750;
lavunder.size = 4;
lavunder.targetAir = false;
lavunder.targetGround = true;
lavunder.range = 260;
lavunder.reload = 10;
lavunder.shootType = lavunderLaserBullet;
lavunder.recoil = 0;
lavunder.consumePower(22);
lavunder.configurable = true;
lavunder.canControl = false;

lavunder.config(java.lang.Integer, packCons2((tile, value) => {
    if(tile != null && value != null) {
        let turretId = tile.id;
        let valInt = Math.floor(value);

        turretTierMap.put(turretId, valInt);
        if(valInt == 1) tile.health = 3750;
        else if(valInt == 2) tile.health = 5707;
        else if(valInt == 3) tile.health = 6848;
    }
}));

lavunder.buildType = () => extend(PowerTurret.PowerTurretBuild, lavunder, {
    downxOffset: 0,
    wingsOffset: 0,

    canControl() { return false; },

    placed(){
        this.super$placed();

        this.downxOffset = 0;
        this.wingsOffset = 0;
    },

    destroyed(){
        this.super$destroyed();
        let turretId = this.id;
        turretTierMap.remove(turretId);
        turretChargeMap.remove(turretId);
        turretBurstTimerMap.remove(turretId);
        turretMilestoneMap.remove(turretId);
        mk3TripleExplosionMap.remove(turretId);
        greenLaserRenderMap.remove(turretId);
        turretDamageTimerMap.remove(turretId);
        exoCountMap.remove(turretId);
    },

    updateTile(){
        this.super$updateTile();
        let targetRadius = 260;
        let hasTarget = Units.closestTarget(this.team, this.x, this.y, targetRadius, u => !u.dead && !u.type.flying && Mathf.dst(this.x, this.y, u.x, u.y) >= deadZone, b => true) != null;

        if(hasTarget){
            this.downxOffset = Mathf.lerpDelta(this.downxOffset, -4.0, 0.15);
            this.wingsOffset = Mathf.lerpDelta(this.wingsOffset, 1.0, 0.15);
        } else {
            this.downxOffset = Mathf.lerpDelta(this.downxOffset, 0.0, 0.1);
            this.wingsOffset = Mathf.lerpDelta(this.wingsOffset, 0.0, 0.1);
        }
    },

    draw() {
        let baseRegion = PerfCore.region(lavunder.basePrefix + "block-" + lavunder.size);
        if (baseRegion && baseRegion.found()) {
            Draw.rect(baseRegion, this.x, this.y);
        } else {
            this.super$draw();
        }

        let rad = this.rotation * Mathf.degRad;
        let cos = Math.cos(rad);
        let sin = Math.sin(rad);

        if (lavunder.bodyRegion && lavunder.bodyRegion.found()) {
            Draw.rect(lavunder.bodyRegion, this.x, this.y, this.rotation);
        }

        if (lavunder.downxRegion && lavunder.downxRegion.found()) {
            let dx = this.x + this.downxOffset * cos;
            let dy = this.y + this.downxOffset * sin;
            Draw.rect(lavunder.downxRegion, dx, dy, this.rotation);
        }

        let wingBackOffset = -4.0 * this.wingsOffset;
        let combatSideMove = 4.0 * this.wingsOffset;

        if (lavunder.a1Region && lavunder.a1Region.found()) {
            let a1x = this.x + (wingBackOffset * cos) - (combatSideMove * sin);
            let a1y = this.y + (wingBackOffset * sin) + (combatSideMove * cos);
            Draw.rect(lavunder.a1Region, a1x, a1y, this.rotation);
        }

        if (lavunder.a2Region && lavunder.a2Region.found()) {
            let a2x = this.x + (wingBackOffset * cos) + (combatSideMove * sin);
            let a2y = this.y + (wingBackOffset * sin) - (combatSideMove * cos);
            Draw.rect(lavunder.a2Region, a2x, a2y, this.rotation);
        }

        Draw.reset();
    },

    buildConfiguration(table) {
        table.clear(); table.row();
        let tier = turretTierMap.containsKey(this.id) ? turretTierMap.get(this.id) : 1;
        let isEn = Core.settings.getString("locale").startsWith("en");

        if(tier == 1) {
            table.button(Icon.upOpen, Styles.cleari, 40, packRun(() => {
                let dialog = extend(BaseDialog, isEn ? "Lavunder Upgrade Center" : "Trung tâm nâng cấp pháo Lavunder", {});

                let reqSnapshot = (() => {
                    let core = this.team.core();
                    if(!core) return isEn ? "[red]Team Core not found![]" : "[red]Không tìm thấy Lõi Đội![]";

                    let copperAmt = core.items.get(Items.copper);
                    let graphiteAmt = core.items.get(Items.graphite);
                    let leadAmt = core.items.get(Items.lead);
                    let siliconAmt = core.items.get(Items.silicon);

                    let copperColor = copperAmt >= reqMK2.copper ? "[green]" : "[red]";
                    let graphiteColor = graphiteAmt >= reqMK2.graphite ? "[green]" : "[red]";
                    let leadColor = leadAmt >= reqMK3.lead ? "[green]" : "[red]";
                    let siliconColor = siliconAmt >= reqMK3.silicon ? "[green]" : "[red]";

                    if(isEn){
                        return "[yellow]CORE VAULT REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Branch:[]\n" +
                               " • Copper: " + copperColor + copperAmt + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graphiteColor + graphiteAmt + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]MK2B Branch:[]\n" +
                               " • Lead: " + leadColor + leadAmt + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + siliconColor + siliconAmt + "[] / " + reqMK3.silicon;
                    } else {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Đồng: " + copperColor + copperAmt + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graphiteColor + graphiteAmt + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Chì: " + leadColor + leadAmt + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + siliconColor + siliconAmt + "[] / " + reqMK3.silicon;
                    }
                })();
                let reqCell = dialog.cont.add(reqSnapshot.toString());

                reqCell.width(360).get().setWrap(true);
                reqCell.get().setAlignment(Align.left);
                dialog.cont.row(); dialog.cont.add().height(10).row();

                let branchesTable = new Table();

                let b1 = new Table(); b1.background(Styles.black6); b1.margin(12);
                b1.add("[cyan]===(MK2)===[]").row();
                let b1Text = isEn ?
                    "Sequential Firepower Pulse Module:\n" +
                    " [white]• Base Damage: [yellow]112.00 DMG/sec[] (deals damage every 1s).[]\n" +
                    " [white]• Energy point charging rate accelerated by [green]+380% (3.8x)[].[]\n" +
                    " [white]• At [yellow]100 Charge[]: Deals [red]3,200 flat DMG[] + [cyan]2% Max HP[] (40px radius).[]\n" +
                    " [white]• [green]Feedback Loop[]: [orange]50%[] chance to refund [yellow]80 charge points[] upon detonation.[]\n" +
                    " [white]• [green]+52.17% HP[] (5,707) and expanded [green]+6.25% Range[] (276.2 px)." :
                    "Mô-đun kích xung hỏa lực liên hoàn:\n" +
                    " [white]• Sát thương cơ bản: [yellow]112.00 ST/giây[] (gây dmg mỗi 1s).[]\n" +
                    " [white]• Tốc độ gia tốc sạc điểm năng lượng tăng trưởng nhanh [green]+380% (3.8x)[].[]\n" +
                    " [white]• Khi đạt [yellow]100 sạc[]: Gây [red]3,200 ST flat[] + [cyan]2% HP tối đa[] (bán kính 40 px).[]\n" +
                    " [white]• [green]Mạch Phản Hồi[]: Có [orange]50%[] tỷ lệ hoàn trả [yellow]80 điểm sạc[] ngay sau nổ.[]\n" +
                    " [white]• Tăng [green]+52.17% Máu[] (5,707) và mở rộng [green]+6.25% Tầm bắn[] (276.2 px).";
                let b1D = b1.add(b1Text);
                b1D.width(340).get().setWrap(true); b1D.get().setAlignment(Align.left); b1.row();
                b1.button(isEn ? "[green]ACTIVATE MK2[]" : "[green]KÍCH HOẠT MK2[]", packRun(() => {
                    let core = this.team.core();
                    if(core && core.items.get(Items.copper) >= reqMK2.copper && core.items.get(Items.graphite) >= reqMK2.graphite){
                        NexNet.take(core, Items.copper, reqMK2.copper);
                        NexNet.take(core, Items.graphite, reqMK2.graphite);
                        this.configure(java.lang.Integer(2));
                        dialog.hide(); this.deselect();
                    } else {
                        Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2 branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2![]");
                    }
                })).size(180, 38);

                let b2 = new Table(); b2.background(Styles.black6); b2.margin(12);
                b2.add("[purple]===(MK2B)===[]").row();
                let b2Text = isEn ?
                    "Area Destructive Spectral Focus Core:\n" +
                    " [white]• Base Damage: [pink]191.52 DMG/sec[] (deals damage every 1s).[]\n" +
                    " [white]• [cyan]Multi-Tier Charge[]: At thresholds [yellow]20,40,60,80[] has a [white]70%[] chance for secondary beam blasts.[]\n" +
                    " [white]• At [yellow]100 Charge[]: Triggers a sequence of [orange]3 consecutive explosive blasts[].[]\n" +
                    " [white]• Super reinforced structure grants [green]+82.6% HP[] (6,848).[]\n" +
                    " [white]• Maintains base [yellow]Range[] (260 px)." :
                    "Lõi hội tụ quang phổ hủy diệt diện rộng:\n" +
                    " [white]• Sát thương cơ bản: [pink]191.52 ST/giây[] (gây dmg mỗi 1s).[]\n" +
                    " [white]• [cyan]Cơ chế Sạc Đa Tầng[]: Tại mốc [yellow]20,40,60,80[] có [white]70%[] tỷ lệ nổ phụ tia.[]\n" +
                    " [white]• Khi chạm [yellow]100 sạc[]: Kích hoạt chuỗi [orange]3 loạt bộc phá liên tiếp[] hủy diệt.[]\n" +
                    " [white]• Siêu gia cố cấu trúc tăng [green]+82.6% Máu[] (6,848).[]\n" +
                    " [white]• Giữ nguyên [yellow]Tầm bắn[] (260 px).";
                let b2D = b2.add(b2Text);
                b2D.width(340).get().setWrap(true); b2D.get().setAlignment(Align.left); b2.row();
                b2.button(isEn ? "[orange]ACTIVATE MK2B[]" : "[orange]KÍCH HOẠT MK2B[]", packRun(() => {
                    let core = this.team.core();
                    if(core && core.items.get(Items.lead) >= reqMK3.lead && core.items.get(Items.silicon) >= reqMK3.silicon){
                        NexNet.take(core, Items.lead, reqMK3.lead);
                        NexNet.take(core, Items.silicon, reqMK3.silicon);
                        this.configure(java.lang.Integer(3));
                        dialog.hide(); this.deselect();
                    } else {
                        Vars.ui.showInfo(isEn ? "[red]Not enough resources for MK2B branch![]" : "[red]Không đủ tài nguyên cho nhánh MK2B![]");
                    }
                })).size(180, 38);

                branchesTable.add(b1).width(340); branchesTable.row();
                branchesTable.add().height(12).row();
                branchesTable.add(b2).width(340);

                let scroll = new ScrollPane(branchesTable);
                scroll.setScrollingDisabled(true, false);
                dialog.cont.add(scroll).maxHeight(400);
                dialog.addCloseButton(); dialog.show();
            })).size(50, 40).tooltip(isEn ? "Upgrade Lavunder Turret" : "Nâng cấp tháp pháo Lavunder");
        } else {
            table.button(Icon.lock, Styles.cleari, 40, packRun(() => {
                Vars.ui.showInfo(isEn ? "[scarlet]LAVUNDER SYSTEM HAS REACHED MAXIMUM EVOLUTION TIER![]" : "[scarlet]HỆ THỐNG LAVUNDER ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]");
            })).size(50, 40).tooltip(isEn ? "Max level reached" : "Đã đạt cấp tối đa");
        }

        table.button(Icon.info, Styles.cleari, 40, packRun(() => {
            let title = isEn ? " Lavunder Turret Stats: " : " Thông số pháo Lavunder: ";
            let descStr = "";
            let currentTier = turretTierMap.containsKey(this.id) ? turretTierMap.get(this.id) : 1;

            if (currentTier == 1) {
                title += "[yellow](MK1)[]";
                descStr = isEn ?
                          "[gold]⚡ BASE STATS (MK1) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]3,750[]\n" +
                          "[lightgray]Effective Range:[] [orange]260 pixels[] (Deadzone: <40 px)\n" +
                          "[lightgray]Base Damage:[] [white]112.00 firepower/sec (Interval: 1s/hit)[]\n" +
                          "[lightgray]Power Consumption:[] [lightgray]22/sec[]" :
                          "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]3,750[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]260 pixel[] (Vùng mù: <40 px)\n" +
                          "[lightgray]Sát thương cơ bản:[] [white]112.00 hỏa lực/giây (Chu kỳ: 1s/lần)[]\n" +
                          "[lightgray]Năng lượng tiêu thụ:[] [lightgray]22/giây[]";
            } else if (currentTier == 2) {
                title += "[cyan](MK2)[]";
                descStr = isEn ?
                          "[cyan]⚡ UPGRADED CONFIGURATION STATS (MK2) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]5,707 (+52.17%)[]\n" +
                          "[lightgray]Effective Range:[] [orange]276.2 pixels (+6.25%)[]\n" +
                          "[lightgray]Base Damage:[] [white]112.00 firepower/sec[]\n" +
                          "[lightgray]Charge Acceleration:[] [green]3.8x speed[]\n" +
                          "[lightgray]Detonation Effect:[] [red]3,200 flat DMG[] + [cyan]2% Max HP[]\n" +
                          "[lightgray]Feedback Rate:[] [orange]50% chance[] to refund 80 charge" :
                          "[cyan]⚡ THÔNG SỐ CẤU HÌNH NÂNG CẤP (MK2) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]5,707 (+52.17%)[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]276.2 pixel (+6.25%)[]\n" +
                          "[lightgray]Sát thương cơ bản:[] [white]112.00 hỏa lực/giây[]\n" +
                          "[lightgray]Gia tốc sạc điểm:[] [green]Tốc độ x3.8[]\n" +
                          "[lightgray]Hiệu ứng nổ bộc phá:[] [red]3,200 ST flat[] + [cyan]2% HP max[]\n" +
                          "[lightgray]Tỷ lệ hồi sạc:[] [orange]50% cơ hội[] hoàn 80 điểm sạc";
            } else if (currentTier == 3) {
                title += "[purple](MK2B)[]";
                descStr = isEn ?
                          "[purple]⚡ SPECTRAL VARIANT STATS (MK2B) ⚡[]\n" +
                          "[lightgray]Turret Health:[] [green]6,848 (+82.6%)[]\n" +
                          "[lightgray]Effective Range:[] [orange]260 pixels[]\n" +
                          "[lightgray]Base Damage:[] [pink]191.52 firepower/sec (+71%)[]\n" +
                          "[lightgray]Multi-Tier Charge:[] Blast sub-beams at [yellow]20,40,60,80 charge[]\n" +
                          "[lightgray]Max Charge Burst:[] Trigger [orange]3 consecutive explosive blasts[]" :
                          "[purple]⚡ THÔNG SỐ BIẾN THỂ QUANG PHỔ (MK2B) ⚡[]\n" +
                          "[lightgray]Máu tháp pháo:[] [green]6,848 (+82.6%)[]\n" +
                          "[lightgray]Tầm bắn phát xạ:[] [orange]260 pixel[]\n" +
                          "[lightgray]Sát thương cơ bản:[] [pink]191.52 hỏa lực/giây (+71%)[]\n" +
                          "[lightgray]Sạc Đa Tầng:[] Nổ tia phụ tại các mốc [yellow]20,40,60,80 sạc[]\n" +
                          "[lightgray]Bộc phá Tối đại:[] Kích hoạt chuỗi [orange]3 loạt nổ liên tiếp[]";
            }

            descStr += isEn ?
                "\n[pink]Exo Beam:[] At [yellow]100 charge[] there is a [orange]50%[] chance to fire a giant sweeping laser (pink-white, ~420 px, 1.6s)." :
                "\n[pink]Tia Exo:[] Khi đạt [yellow]100 sạc[] có [orange]50%[] tỷ lệ bắn một tia laser khổng lồ quét ngang (hồng-trắng, ~420 px, 1.6 giây).";

            let dialog = extend(BaseDialog, title, {});
            let infoTable = new Table();
            let cell = infoTable.add(descStr).width(360);
            cell.get().setWrap(true); cell.get().setAlignment(Align.left);
            let scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        })).size(50, 40).tooltip(isEn ? "View Detailed System Stats" : "Xem chi tiết thông số trạng thái");
    }
});