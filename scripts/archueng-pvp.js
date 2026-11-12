// Archueng trong NewMode PvP: thay unit disrupt (cấp 7) bằng archueng + kỹ năng quạt (nút HUD kéo thả được).
// File độc lập, không sửa newmode-pvp.js. Cần global.NexPvp (isActive) và global.NexNet.
(function() {
    const REQ = "newex-archueng-skill", FAN = "newex-archueng-fan", KEY = "newex-archueng-btn";
    const CD_T = 600;             // hồi chiêu 10s (tick game, đứng yên khi tạm dừng)
    const WINDUP = 42;            // tick quay weapon trước khi bắn quạt
    const SLOW_T = 72;            // 1.2s bay chậm
    const FAST_T = 60;            // 1s bay cực nhanh
    const FADE_T = 45;            // tan biến dần sau khi hết đà (vừa chậm lại vừa mờ)
    const PUSH = 2.2;             // lực đẩy lùi mục tiêu theo đường kiếm khí
    const SLOW_SPD = 0.6, FAST_SPD = 9;
    const DMG = 500, ATK_PCT = 1.6, ARM_PCT = 0.5, HALF = 40, RAD = 70;
    const COL = Color.valueOf("5ab4ff"), COL2 = Color.valueOf("bff4ff"), GOLD = Color.valueOf("ffc83c");
    const FIRE = Color.valueOf("ff8a3c"), FIRE2 = Color.valueOf("ff3c1e");

    let arch = null, field = null, orig = null, swapped = false, netOk = false;
    let fans = [], spins = {}, cdEnd = 0, cdEndSrv = {};
    // luồng bấm 3 lần: 0 = rảnh, 1 = đang chờ bấm lần 2 để chọn hướng, 2 = đã chọn hướng, chờ bấm lần 3 để tung
    let phase = 0, aimAng = 0, uiTapConsumed = false, wasTouchedPrev = false;

    function findArch() {
        if (arch != null) return arch;
        try {
            let a = Vars.content.units().select(new Boolf({ get: function(u) { let n = String(u.name); return n === "archueng" || n.endsWith("-archueng"); } }));
            if (a.size > 0) arch = a.first();
        } catch (err) {}
        return arch;
    }
    function isActive() {
        try { return global.NexPvp != null && global.NexPvp.isActive() === true; } catch (err) { return false; }
    }
    // thay disrupt -> archueng khi mode đang chạy (tierType() của newmode-pvp đọc UnitTypes.disrupt mỗi lần gọi)
    function syncType() {
        let a = findArch();
        if (a == null) return;
        try {
            if (field == null) {
                field = java.lang.Class.forName("mindustry.content.UnitTypes").getField("disrupt");
                orig = field.get(null);
            }
            let want = isActive();
            if (want !== swapped) { field.set(null, want ? a : orig); swapped = want; }
        } catch (err) { Log.err("[Archueng] swap failed: " + err); }
    }
    function isArch(u) { return u != null && arch != null && u.type === arch; }

    // ---- server: nhận yêu cầu dùng skill ----
    function onReq(player, payload) {
        if (!isActive()) return;
        let u = player.unit();
        if (!isArch(u) || u.dead) return;
        if (cdEndSrv[player.id] != null && Time.time < cdEndSrv[player.id]) return;
        cdEndSrv[player.id] = Time.time + CD_T;
        let ang = parseFloat(String(payload)); // hướng do client chọn ở lần bấm thứ 2 (chuột/chạm tới điểm muốn đánh)
        if (isNaN(ang)) ang = u.rotation; // phòng khi payload rỗng: dùng hướng mặt unit như cũ
        global.NexNet.broadcast(FAN, u.id + "," + u.team.id + "," + Math.round(ang * 10) / 10 + "," + u.x + "," + u.y);
    }
    // sát thương = 160% tấn công (DMG gốc x damageMultiplier của unit) + 50% giáp
    function calcDmg(id) {
        let o = Groups.unit.getByID(id);
        return o != null ? DMG * ATK_PCT * Math.max(0.1, o.damageMultiplier) + ARM_PCT * o.armor : DMG * ATK_PCT;
    }
    // ---- mọi máy: nhận lệnh tạo quạt (host/server cũng tự nhận) ----
    function onFan(payload) {
        let p = String(payload).split(",");
        let id = parseInt(p[0]);
        spins[id] = { t: 0, a: parseFloat(p[2]) };
        fans.push({ uid: id, team: parseInt(p[1]), ang: parseFloat(p[2]), x: parseFloat(p[3]), y: parseFloat(p[4]), t: -WINDUP, hit: {}, vic: [], dmg: calcDmg(id) });
    }
    function tryNet() {
        if (netOk || global.NexNet == null) return;
        netOk = true;
        global.NexNet.on(REQ, onReq);
        global.NexNet.onBroadcast(FAN, onFan);
    }

    function hitFan(f) {
        let team = Team.get(f.team);
        let reach = RAD + 6;
        let test = function(x, y) {
            let d = Mathf.dst(f.x, f.y, x, y);
            return d <= RAD + 6 && Math.abs(Angles.angleDist(Angles.angle(f.x, f.y, x, y), f.ang)) <= HALF + (d < 12 ? 180 : 0);
        };
        Units.nearbyEnemies(team, f.x - reach, f.y - reach, reach * 2, reach * 2, new Cons({ get: function(e) {
            if (e.dead || f.hit["u" + e.id] || !test(e.x, e.y)) return;
            f.hit["u" + e.id] = true;
            e.damage(f.dmg);
            f.vic.push(e);
            try { Call.effect(Fx.hitLaserBlast, e.x, e.y, f.ang, COL2); Call.effect(Fx.sparkShoot, e.x, e.y, f.ang, GOLD); } catch (err) {}
        } }));
        Units.nearbyBuildings(f.x, f.y, reach, new Cons({ get: function(b) {
            if (b.team === team || f.hit["b" + b.pos()] || !test(b.x, b.y)) return;
            f.hit["b" + b.pos()] = true;
            b.damage(f.dmg);
            try { Call.effect(Fx.hitLaserBlast, b.x, b.y, f.ang, COL2); } catch (err) {}
        } }));
    }

    Events.run(Trigger.update, new Runnable({ run: function() {
        if (Vars.state == null || !Vars.state.isGame()) { if (fans.length > 0) { fans = []; spins = {}; } return; }
        tryNet();
        syncType();
        if (Vars.state.isPaused()) return; // tạm dừng: đứng yên quạt/hiệu ứng
        for (let i = fans.length - 1; i >= 0; i--) {
            let f = fans[i];
            f.t += Time.delta;
            if (f.t < 0) {
                let u = Groups.unit.getByID(f.uid); // bám theo unit khi đang quay
                if (u != null) { f.x = u.x; f.y = u.y; }
                continue;
            }
            let fl = f.t - (SLOW_T + FAST_T), prog = fl > 0 ? Mathf.clamp(fl / FADE_T) : 0;
            let spd = f.t < SLOW_T ? SLOW_SPD : FAST_SPD * (1 - prog) * (1 - prog);
            f.x += Angles.trnsx(f.ang, spd * Time.delta);
            f.y += Angles.trnsy(f.ang, spd * Time.delta);
            try {
                if (f.t - Time.delta < 0) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL); Effect.shake(2, 6, f.x, f.y); } // thả kiếm khí
                if (f.t > SLOW_T && f.lx === undefined) { f.lx = f.x; f.ly = f.y; }
                if (f.t > SLOW_T && prog === 0) {
                    if (Mathf.chance(0.7)) Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, Mathf.range(RAD * 0.8)), f.y + Angles.trnsy(f.ang + 90, Mathf.range(RAD * 0.8)), f.ang, Mathf.chance(0.3) ? GOLD : COL2);
                } else if (Mathf.chance(0.25)) Fx.sparkShoot.at(f.x + Angles.trnsx(f.ang + 90, Mathf.range(RAD * 0.7)), f.y + Angles.trnsy(f.ang + 90, Mathf.range(RAD * 0.7)), f.ang, GOLD);
                if (f.t - Time.delta <= SLOW_T && f.t > SLOW_T) { Fx.shootBigColor.at(f.x, f.y, f.ang, COL2); Effect.shake(4, 8, f.x, f.y); } // bắt đầu lao nhanh
            } catch (err) {}
            if (prog > 0 && Mathf.chance(0.5)) try { Fx.hitBulletColor.at(f.x + Angles.trnsx(f.ang + 90, Mathf.range(RAD * 0.8)), f.y + Angles.trnsy(f.ang + 90, Mathf.range(RAD * 0.8)), f.ang, Mathf.chance(0.3) ? GOLD : COL2); } catch (err) {}
            if (!Vars.net.client()) {
                if (prog === 0) hitFan(f);
                let cap = FAST_SPD * (1 - prog); // đẩy lùi mục tiêu đã trúng dọc đường kiếm khí cho tới khi tan hẳn
                for (let n = f.vic.length - 1; n >= 0; n--) {
                    let e = f.vic[n];
                    if (e == null || e.dead || !e.isValid()) { f.vic.splice(n, 1); continue; }
                    e.vel.add(Angles.trnsx(f.ang, PUSH * (1 - prog) * Time.delta), Angles.trnsy(f.ang, PUSH * (1 - prog) * Time.delta));
                    if (e.vel.len() > cap) e.vel.setLength(cap);
                }
            }
            if (f.t >= SLOW_T + FAST_T + FADE_T) fans.splice(i, 1);
        }
        for (let k in spins) { spins[k].t += Time.delta; if (spins[k].t > WINDUP) delete spins[k]; }

        // lần bấm thứ 2: chạm/bấm chuột ở vị trí muốn đánh kiếm khí (không tính lần chạm vào nút skill) để chốt hướng
        if (!Vars.headless && Vars.player != null) {
            let pu = Vars.player.unit();
            if (phase !== 0 && (!isArch(pu) || pu.dead)) phase = 0; // đổi unit hoặc chết thì huỷ luôn
            let touched = Core.input.isTouched();
            if (touched && !wasTouchedPrev && !uiTapConsumed && phase === 1 && isArch(pu) && !pu.dead) {
                aimAng = Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                phase = 2;
            }
            if (!touched) uiTapConsumed = false;
            wasTouchedPrev = touched;
        }
    } }));

    if (!Vars.headless) {
        Events.run(Trigger.draw, new Runnable({ run: function() {
            if (arch == null) return;
            let wreg = Core.atlas.find("newex-archueng-weapon"), slash = Core.atlas.find("newex-archueng-slash");
            if (!wreg.found()) wreg = Core.atlas.find("archueng-weapon");

            // đường ngắm: lần 1 đã bấm (đang chờ lần 2) theo chuột/điểm chạm sống; lần 2 đã bấm (chờ lần 3) thì đứng yên theo hướng đã chốt
            if (Vars.player != null && (phase === 1 || phase === 2)) {
                let pu = Vars.player.unit();
                if (isArch(pu) && !pu.dead) {
                    let ang = phase === 2 ? aimAng : Angles.angle(pu.x, pu.y, Vars.player.mouseX, Vars.player.mouseY);
                    let ex = pu.x + Angles.trnsx(ang, 160), ey = pu.y + Angles.trnsy(ang, 160);
                    Draw.z(Layer.flyingUnit + 2);
                    Drawf.dashLine(phase === 2 ? FIRE : COL2, pu.x, pu.y, ex, ey);
                    Draw.reset();
                }
            }

            for (let k in spins) {
                let u = Groups.unit.getByID(parseInt(k)), s = spins[k];
                if (u == null || u.dead) continue;
                let fr = Mathf.clamp(s.t / WINDUP);
                let wx = u.x + Angles.trnsx(u.rotation - 90, -6, -2), wy = u.y + Angles.trnsy(u.rotation - 90, -6, -2);
                Draw.z(Layer.flyingUnit + 1);
                let sw = Interp.pow2Out.apply(fr), a0 = s.a - HALF, cur = a0 + HALF * 2 * sw, L = 64;
                // sạc vũ khí kiểu nổ điện ảnh dưới chân: chỉ còn tia lửa/than hồng bắn toé, không còn vòng tròn hay trận pháp
                let rr = 26 + 16 * fr;
                Draw.z(Layer.groundUnit - 1);
                Draw.blend(Packages.arc.graphics.Blending.additive);
                for (let q = 0; q < 10; q++) {
                    let ph = ((s.t * 0.045) + q * 0.1) % 1, qa = q * 36 + s.t * 1.5, qr = rr * (0.25 + 1.15 * ph);
                    Draw.color(q % 3 === 0 ? FIRE2 : FIRE, (1 - ph) * (0.4 + 0.6 * fr));
                    Fill.circle(u.x + Angles.trnsx(qa, qr), u.y + Angles.trnsy(qa, qr), 1 + (1 - ph) * 2.6);
                }
                Draw.blend(); Draw.reset();
                Draw.z(Layer.flyingUnit + 1);
                let segs = 14, step = (cur - a0) / segs;
                for (let q = 0; q < segs; q++) { // vệt chém: mờ dần phía sau lưỡi kiếm
                    let t1 = a0 + step * q, t2 = t1 + step, al = Math.pow((q + 1) / segs, 1.6) * 0.85 * (1 - fr * 0.5);
                    let cIn = Tmp.c1.set(0.1, 0.4, 0.95, 0).toFloatBits(), cOut = Tmp.c2.set(0.55, 0.95, 1, al).toFloatBits(), cMid = Tmp.c3.set(0.15, 0.55, 1, al * 0.6).toFloatBits();
                    let r0 = 10, r1 = L * 0.55, r2 = L * 1.05;
                    Fill.quad(u.x + Angles.trnsx(t1, r0), u.y + Angles.trnsy(t1, r0), cIn, u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid, u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid, u.x + Angles.trnsx(t2, r0), u.y + Angles.trnsy(t2, r0), cIn);
                    Fill.quad(u.x + Angles.trnsx(t1, r1), u.y + Angles.trnsy(t1, r1), cMid, u.x + Angles.trnsx(t1, r2), u.y + Angles.trnsy(t1, r2), cOut, u.x + Angles.trnsx(t2, r2), u.y + Angles.trnsy(t2, r2), cOut, u.x + Angles.trnsx(t2, r1), u.y + Angles.trnsy(t2, r1), cMid);
                }
                Draw.color();
                if (wreg.found()) { // chính weapon của unit quẹt qua cung quạt (sprite weapon hướng lên nên trừ 90 độ)
                    let wl = wreg.height / 4 * 1.0, wo = Math.max(10, wl * 0.5);
                    Draw.rect(wreg, u.x + Angles.trnsx(cur, wo), u.y + Angles.trnsy(cur, wo), wreg.width / 4, wl, cur - 90);
                }
                try { // hiệu ứng code: tia sáng dọc mũi kiếm
                    let tx = u.x + Angles.trnsx(cur, L), ty = u.y + Angles.trnsy(cur, L);
                    if (Mathf.chance(0.8)) Fx.hitBulletColor.at(tx, ty, cur + 90, COL);
                    if (Mathf.chance(0.35)) Fx.sparkShoot.at(tx, ty, cur, GOLD);
                    if (Mathf.chance(0.3)) Fx.missileTrail.at(tx, ty, 3, COL2);
                } catch (err) {}
                Draw.reset();
            }
            for (let i = 0; i < fans.length; i++) {
                let f = fans[i];
                if (f.t < 0) continue;
                let fast = f.t > SLOW_T, pg = Mathf.clamp((f.t - SLOW_T - FAST_T) / FADE_T), fade = 1 - pg * pg;
                Draw.z(Layer.bullet + 2);
                let pulse = fast ? 1 + 0.3 * pg : 1 + Mathf.sin(f.t, 6, 0.04); // tan ra nở nhẹ
                let sc = RAD * 2 * pulse * (fast ? 1.15 : 1);
                if (slash.found()) {
                    Draw.blend(Packages.arc.graphics.Blending.additive);
                    Draw.color(COL, 0.32 * fade); Draw.rect(slash, f.x, f.y, sc * 1.5, sc * 1.5, f.ang + Mathf.sin(f.t, 9, 1.5)); // hào quang ngoài
                    Draw.color(GOLD, 0.28 * fade); Draw.rect(slash, f.x, f.y, sc * 1.25, sc * 1.25, f.ang - Mathf.sin(f.t, 11, 1.2)); // lớp vàng lệch nhịp
                    Draw.blend();
                    Draw.color(Color.white, fade); Draw.rect(slash, f.x, f.y, sc, sc, f.ang);                                          // lõi kiếm khí
                    Draw.blend(Packages.arc.graphics.Blending.additive);
                    Draw.color(Color.white, 0.55 * fade); Draw.rect(slash, f.x, f.y, sc * 0.82, sc * 0.82, f.ang);
                    Draw.blend();
                }
                // linh quang / phù văn bay quanh mép kiếm khí
                Draw.blend(Packages.arc.graphics.Blending.additive);
                for (let q = 0; q < 9; q++) {
                    let off = (q / 8 - 0.5) * HALF * 2.1 + Mathf.sin(f.t * 0.08 + q * 2, 1, 6);
                    let rd = RAD * (0.95 + 0.25 * Mathf.sin(f.t * 0.12 + q, 1, 1)) + (q % 2) * 8;
                    Draw.color(q % 2 ? GOLD : COL2, (0.5 + 0.5 * Mathf.sin(f.t * 0.2 + q, 1, 1)) * fade);
                    Fill.square(f.x + Angles.trnsx(f.ang + off, rd), f.y + Angles.trnsy(f.ang + off, rd), 1.8 + (q % 3) * 0.6, f.t * 4 + q * 40);
                }
                // vòng sóng xung kích khi bắt đầu lao
                if (f.lx !== undefined && f.t - SLOW_T < 26) {
                    let rp = (f.t - SLOW_T) / 26;
                    Draw.color(COL2, (1 - rp) * 0.9); Lines.stroke(3 * (1 - rp) + 0.5);
                    Lines.circle(f.lx, f.ly, 12 + 95 * Interp.pow3Out.apply(rp));
                    Draw.color(GOLD, (1 - rp) * 0.7); Lines.stroke(1.5 * (1 - rp) + 0.3);
                    Lines.circle(f.lx, f.ly, 8 + 60 * Interp.pow2Out.apply(rp));
                }
                Draw.blend(); Draw.reset();
                Draw.color(COL2, 0.5 * fade);
                Lines.stroke(1.5);
                if (!slash.found()) Lines.arc(f.x, f.y, RAD, HALF * 2 / 360, f.ang - HALF);
                Draw.reset();
                try { Drawf.light(f.x, f.y, RAD * 1.6, COL, 0.5 * fade); } catch (err) {}
            }
        } }));

        // ---- nút kỹ năng kéo thả ----
        let btn = null;
        function clamp(t) {
            t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
        }
        function build() {
            if (btn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
            let a = findArch();
            let t = new Table(Styles.black6);
            btn = t;
            t.name = "newex-archueng-skill";
            t.margin(6);
            t.touchable = Touchable.enabled;
            let sz = Vars.mobile ? 56 : 48;
            if (a != null) t.image(a.uiIcon).size(sz).row();
            let lbl = t.add("").get();
            lbl.setFontScale(0.9);
            t.update(new Runnable({ run: function() {
                let left = cdEnd - Time.time;
                if (left > 0) { lbl.setText((Math.ceil(left / 6) / 10).toFixed(1) + "s"); lbl.setColor(Color.gray); }
                else if (phase === 1) { lbl.setText("CHỌN HƯỚNG"); lbl.setColor(COL2); }
                else if (phase === 2) { lbl.setText("TUNG"); lbl.setColor(FIRE); }
                else { lbl.setText("SKILL"); lbl.setColor(COL2); }
            } }));
            t.visibility = new Boolp({ get: function() {
                return isActive() && Vars.ui.hudfrag.shown && isArch(Vars.player.unit()) && !Vars.player.unit().dead;
            } });
            t.pack();
            if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
            else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3);
            clamp(t);
            let dragging = false, offX = 0, offY = 0;
            t.addListener(extend(InputListener, {
                touchDown(event, x, y, pointer, button) { dragging = false; offX = x; offY = y; uiTapConsumed = true; return true; },
                touchDragged(event, x, y, pointer) {
                    if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                    if (dragging) { t.setPosition(t.x + x - offX, t.y + y - offY); clamp(t); }
                },
                touchUp(event, x, y, pointer, button) {
                    if (dragging) {
                        Core.settings.put(KEY + "-x", java.lang.Integer(Math.round(t.x)));
                        Core.settings.put(KEY + "-y", java.lang.Integer(Math.round(t.y)));
                    } else if (!Vars.state.isPaused() && global.NexNet != null) {
                        let u = Vars.player.unit();
                        if (phase === 0) {
                            // lần bấm 1: chỉ mở chế độ chọn hướng, chưa tung và chưa tính hồi chiêu
                            if (Time.time >= cdEnd && isArch(u) && !u.dead) phase = 1;
                        } else if (phase === 1) {
                            phase = 0; // bấm lại nút khi đang chờ chọn hướng -> huỷ
                        } else if (phase === 2) {
                            // lần bấm 3: tung chiêu theo hướng đã chốt ở lần bấm 2, giờ mới tính hồi chiêu
                            cdEnd = Time.time + CD_T;
                            global.NexNet.send(REQ, String(aimAng));
                            phase = 0;
                        }
                    }
                    dragging = false;
                }
            }));
            Vars.ui.hudGroup.addChild(t);
        }

        // ---- nút hủy, nằm ngay dưới nút skill, chỉ hiện khi đang chọn hướng / đã chốt hướng ----
        let cancelBtn = null;
        function buildCancel() {
            if (cancelBtn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
            let c = new Table(Styles.black6);
            cancelBtn = c;
            c.name = "newex-archueng-cancel";
            c.margin(6);
            c.touchable = Touchable.enabled;
            let lbl = c.add("HỦY").get();
            lbl.setFontScale(0.9);
            lbl.setColor(Color.scarlet);
            c.pack();
            c.update(new Runnable({ run: function() {
                if (btn == null) return;
                c.pack(); // bề rộng chữ "HỦY" cố định nên pack 1 lần là đủ, gọi lại cho an toàn khi scale font đổi
                c.setPosition(btn.x + (btn.getWidth() - c.getWidth()) / 2, btn.y - c.getHeight() - 6);
            } }));
            c.visibility = new Boolp({ get: function() {
                let u = Vars.player != null ? Vars.player.unit() : null;
                return isActive() && Vars.ui.hudfrag.shown && phase !== 0 && isArch(u) && !u.dead;
            } });
            c.addListener(extend(InputListener, {
                touchDown(event, x, y, pointer, button) { uiTapConsumed = true; return true; },
                touchUp(event, x, y, pointer, button) { phase = 0; }
            }));
            Vars.ui.hudGroup.addChild(c);
        }
        Events.on(ClientLoadEvent, new Cons({ get: function(e) { try { build(); buildCancel(); } catch (err) { Log.err("[Archueng] ui: " + err); } } }));
        Events.on(WorldLoadEvent, new Cons({ get: function(e) { cdEnd = 0; cdEndSrv = {}; fans = []; spins = {}; phase = 0; uiTapConsumed = false; wasTouchedPrev = false; } }));
    } else {
        Events.on(WorldLoadEvent, new Cons({ get: function(e) { cdEndSrv = {}; fans = []; spins = {}; } }));
    }
})();