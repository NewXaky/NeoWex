const PerfCore = global.PerfCore;
const _perfRect = new Rect();
const _waveCol = new Color();
(function() {
    const smoothHpMap = new ObjectMap();
    const smoothShieldMap = new ObjectMap();
    const lastHpMap = new ObjectMap();
    const lastDamageTimeMap = new ObjectMap();
    const formattedTextMap = new ObjectMap();
    const healWaveMap = new ObjectMap(); // id -> thời điểm bắt đầu làn sóng hồi máu
    const lastShMap = new ObjectMap(), hpDropMap = new ObjectMap(), shDropMap = new ObjectMap(), shTextMap = new ObjectMap();
    const SH_COL = Color.valueOf("33c6ff"), SWAP_T = 180; // khiên: xanh nước; 3s đổi chỗ thanh
    const WAVE_LIFE = 36, WAVE_W = 0.34, WAVE_N = 7;
    const HP_TOP_Z = 1000; // layer cao nhất: thanh máu luôn nằm trên mọi thứ

    // Làn sóng hồi máu: dải sáng xanh-trắng + vạch sáng ở đầu sóng chạy mượt từ đầu tới cuối thanh.
    // Tối ưu: không đổi blend (không flush batch), chỉ ~9 hình chữ nhật khi đang chạy, 0 chi phí khi không có sóng.
    function drawHealWave(id, left, y, barW, fillW, barH) {
        let st = healWaveMap.get(id);
        if (st == null) return;
        let t = (Time.time - st) / WAVE_LIFE;
        if (t >= 1) { healWaveMap.remove(id); return; }
        if (fillW <= 0.5) return;
        let e = t * t * (3 - 2 * t);
        let c = -WAVE_W + e * (1 + 2 * WAVE_W);
        let fade = Math.min(1, Math.sin(t * Math.PI) * 1.6);
        let seg = (WAVE_W * 2) / WAVE_N;
        Draw.z(HP_TOP_Z + 2);
        for (let i = 0; i < WAVE_N; i++) {
            let a0 = c - WAVE_W + i * seg, a1 = a0 + seg;
            let x0 = Math.max(0, a0 * barW), x1 = Math.min(fillW, a1 * barW);
            if (x1 <= x0) continue;
            let k = 1 - Math.abs(((a0 + a1) / 2 - c) / WAVE_W);
            k = k * k;
            Draw.color(_waveCol.set(Color.lime).lerp(Color.white, k), (0.35 + 0.6 * k) * fade);
            Fill.rect(left + (x0 + x1) / 2, y, x1 - x0, barH + 0.8 + 1.6 * k);
        }
        let hx = c * barW;
        if (hx > 0 && hx < fillW) {
            Draw.color(Color.white, 0.95 * fade);
            Fill.rect(left + hx, y, 1.4, barH + 3.2);
        }
        Draw.reset();
    }

    function clearHpCache() {
        smoothHpMap.clear();
        smoothShieldMap.clear();
        lastHpMap.clear();
        lastDamageTimeMap.clear();
        formattedTextMap.clear();
        healWaveMap.clear();
        lastShMap.clear(); hpDropMap.clear(); shDropMap.clear(); shTextMap.clear();
    }

    Events.on(WorldLoadEvent, clearHpCache);
    Events.on(StateChangeEvent, e => {
        if (e.to !== GameState.State.playing || e.from === GameState.State.paused) return;
        clearHpCache();
    });

    // ===== Thanh máu vỡ như kính khi unit bị hạ (0.3s = 18 tick): rạn nứt -> vỡ mảnh bay tản ra -> mờ dần =====
    const SHATTER_LIFE = 18, CRACK_FRAC = 0.22;
    const hpBarShatterFx = new Effect(SHATTER_LIFE, 200, cons(e => {
        const d = e.data;
        if (d == null) return;
        const W = d.w, H = d.h, k = e.fin(), hx = d.hx;
        const hpCol = Color.valueOf(d.c), bgCol = Color.valueOf("0d0d11");
        const fillL = -W / 2 + 0.8, fillR = fillL + d.fillW;
        Draw.z(HP_TOP_Z + 1);

        if (k < CRACK_FRAC) { // giai đoạn rạn nứt: thanh còn nguyên + vết nứt tỏa ra từ điểm va
            const q = k / CRACK_FRAC;
            Draw.color(bgCol); Draw.alpha(0.8); Fill.rect(e.x, e.y, W, H);
            Draw.color(hpCol); Draw.alpha(0.9); Fill.rect(e.x + (fillL + fillR) / 2, e.y, d.fillW, H - 1.6);
            Draw.color(Color.white); Lines.stroke(0.5);
            for (let i = 0; i < 8; i++) {
                let a = i * 45 + Mathf.randomSeed(e.id * 7 + i) * 30, len = (3 + Mathf.randomSeed(e.id * 11 + i) * 9) * q;
                Draw.alpha(0.9);
                Lines.lineAngle(e.x + hx, e.y, a, len);
            }
            Draw.alpha(0.8 * (1 - q)); Fill.circle(e.x + hx, e.y, 2.2 * (1 - q) + 0.6);
            Draw.reset();
            return;
        }

        const t = (k - CRACK_FRAC) / (1 - CRACK_FRAC);       // 0..1 sau khi vỡ
        const tau = (k - CRACK_FRAC) * SHATTER_LIFE;          // tick kể từ lúc vỡ
        const alpha = 1 - Math.pow(t, 1.6);                   // mờ dần
        const cols = Math.max(5, Math.round(W / 3.6)), rows = 2, cw = W / cols, rh = H / rows;
        // lưới đỉnh có lệch ngẫu nhiên (cố định theo id) để mảnh kính không đều
        const gx = [], gy = [];
        for (let r = 0; r <= rows; r++) for (let c = 0; c <= cols; c++) {
            let inner = c > 0 && c < cols, innerY = r > 0 && r < rows;
            gx.push(-W / 2 + c * cw + (inner ? (Mathf.randomSeed(e.id * 131 + c * 17 + r * 31) - 0.5) * cw * 0.7 : 0));
            gy.push(-H / 2 + r * rh + (innerY ? (Mathf.randomSeed(e.id * 137 + c * 19 + r * 29) - 0.5) * rh * 0.7 : 0));
        }
        const idx = (c, r) => r * (cols + 1) + c;
        for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) {
            const tris = [[idx(c, r), idx(c + 1, r), idx(c, r + 1)], [idx(c + 1, r), idx(c + 1, r + 1), idx(c, r + 1)]];
            for (let ti = 0; ti < 2; ti++) {
                const tr = tris[ti], seed = e.id * 193 + (r * cols + c) * 2 + ti;
                const cxl = (gx[tr[0]] + gx[tr[1]] + gx[tr[2]]) / 3, cyl = (gy[tr[0]] + gy[tr[1]] + gy[tr[2]]) / 3;
                // hướng bay: từ điểm va ra ngoài + trọng lực kéo xuống
                let dx = cxl - hx, dy = cyl + 0.6, dl = Math.max(0.5, Math.sqrt(dx * dx + dy * dy));
                const sp = 0.35 + Mathf.randomSeed(seed) * 0.9;
                const px = cxl + (dx / dl) * sp * tau * 1.1;
                const py = cyl + (dy / dl) * sp * tau * 0.8 - 0.045 * tau * tau;
                const rot = (Mathf.randomSeed(seed + 5) - 0.5) * 0.35 * tau, cr = Math.cos(rot), sr = Math.sin(rot);
                const sc = 1 - 0.3 * t;
                const vx = [], vy = [];
                for (let v = 0; v < 3; v++) {
                    let ox = (gx[tr[v]] - cxl) * sc, oy = (gy[tr[v]] - cyl) * sc;
                    vx.push(e.x + px + ox * cr - oy * sr);
                    vy.push(e.y + py + ox * sr + oy * cr);
                }
                const inFill = cxl >= fillL && cxl <= fillR && Math.abs(cyl) < H / 2 - 0.8;
                Draw.color(inFill ? hpCol : bgCol); Draw.alpha((inFill ? 0.95 : 0.8) * alpha);
                Fill.tri(vx[0], vy[0], vx[1], vy[1], vx[2], vy[2]);
                Draw.color(Color.white); Draw.alpha(0.55 * alpha); Lines.stroke(0.35);
                Lines.line(vx[0], vy[0], vx[1], vy[1]); Lines.line(vx[1], vy[1], vx[2], vy[2]); Lines.line(vx[2], vy[2], vx[0], vy[0]);
            }
        }
        // chớp sáng lúc vỡ
        if (t < 0.35) {
            const fl = 1 - t / 0.35;
            Draw.blend(Blending.additive);
            Draw.color(Color.white); Draw.alpha(0.8 * fl); Fill.circle(e.x + hx, e.y, 2.5 * fl + 0.5);
            Lines.stroke(0.6); Draw.alpha(0.6 * fl);
            Lines.lineAngle(e.x + hx, e.y, 0, 7 * fl); Lines.lineAngle(e.x + hx, e.y, 180, 7 * fl);
            Lines.lineAngle(e.x + hx, e.y, 90, 4 * fl); Lines.lineAngle(e.x + hx, e.y, 270, 4 * fl);
            Draw.blend();
        }
        Draw.reset();
    }));

    Events.on(UnitDestroyEvent, e => {
        if (e.unit == null) return;
        const u = e.unit, id = u.id;
        const style = Core.settings.getString("newex-hp-style", "show-hp");
        const lastDmg = lastDamageTimeMap.containsKey(id) ? lastDamageTimeMap.get(id) : -9999;
        if (style !== "off" && Time.time - lastDmg <= 300 && smoothHpMap.containsKey(id) && !Vars.headless) {
            const pct = Math.max(0, smoothHpMap.get(id) / u.maxHealth * 100);
            let W, H, by, col;
            if (style === "show-hp") { W = Math.max(22, u.hitSize * 1.2) + 1.6; H = 5.6; by = u.y + u.hitSize / 2 + 6; col = pct < 25 ? "ff0000" : pct < 55 ? "ffff00" : "00ff00"; }
            else { W = Math.max(18, u.hitSize) + 1.2; H = 4.2; by = u.y + u.hitSize / 2 + 4; col = pct < 20 ? "ff0000" : pct < 50 ? "ffff00" : "00ff00"; }
            const fillW = Math.max(0.5, (W - 1.6) * Math.min(1, pct / 100));
            const hx = Math.max(-W / 2 + 2, Math.min(W / 2 - 2, -W / 2 + 0.8 + fillW * 0.85));
            hpBarShatterFx.at(u.x, by, 0, { w: W, h: H, fillW: fillW, hx: hx, c: col });
        }
        smoothHpMap.remove(id);
        smoothShieldMap.remove(id);
        lastHpMap.remove(id);
        lastDamageTimeMap.remove(id);
        formattedTextMap.remove(id);
        healWaveMap.remove(id);
        lastShMap.remove(id); hpDropMap.remove(id); shDropMap.remove(id); shTextMap.remove(id);
    });

    // Độ cao (từ tâm đơn vị) tới đỉnh phần hiển thị máu, để số dmg xếp ngay trên mà không đè.
    global.newexHpTop = function(size) {
        let style = Core.settings.getString("newex-hp-style", "show-hp");
        if (style === "off") return size / 2 + 4;
        let font = Fonts.outline, ox = font.getData().scaleX, oy = font.getData().scaleY;
        font.getData().setScale(style === "hp" ? 0.11 * (size / 8) : Math.min(0.2, Math.max(0.1, 0.08 * (size / 8))));
        let ch = font.getCapHeight();
        font.getData().setScale(ox, oy);
        return size / 2 + (style === "hp" ? 4 + 4.5 + ch : 6 + 6.5 + ch);
    };

    function formatHP(v) {
        v = Math.max(0, v);
        if (v >= 1e6) return (v / 1e6).toFixed(1) + "M";
        if (v >= 1e4) return (v / 1e3).toFixed(1) + "k";
        return String(Math.round(v));
    }

    // 1 thanh: nền + phần đầy + vạch sáng (+ sóng hồi máu, vạch kết liễu nếu là thanh máu)
    function drawBar(id, cx, y, bw, bh, frac, col, isHp, bgHex, pad, pulse, neoMk) {
        Draw.z(HP_TOP_Z);
        Draw.color(PerfCore.color(bgHex), 0.8);
        Fill.rect(cx, y, bw + pad, bh + pad);
        let w = bw * Math.max(0, Math.min(1, frac));
        Draw.color(col);
        Draw.alpha(pulse ? 0.6 + Mathf.absin(Time.time, 5, 0.4) : 0.9);
        Fill.rect(cx - bw / 2 + w / 2, y, w, bh);
        if (isHp) drawHealWave(id, cx - bw / 2, y, bw, w, bh);
        Draw.z(HP_TOP_Z + 1);
        Draw.color(Color.white, 0.45);
        Fill.rect(cx, y + bh / 2 - 0.5, bw, 0.8);
        if (isHp && neoMk > 0) {
            let tx = cx - bw / 2 + bw * neoMk;
            Draw.color(Color.black, 0.9); Fill.rect(tx, y, 2.4, bh + 4.4);
            Draw.color(Color.white, 1); Fill.rect(tx, y, 1.2, bh + 3.2);
        }
        Draw.reset();
    }
    function drawTxt(str, x, y, scale, col) {
        let font = Fonts.outline, ox = font.getData().scaleX, oy = font.getData().scaleY;
        font.getData().setScale(scale);
        font.setColor(col);
        font.draw(str, x, y, 0, Align.center, false);
        font.getData().setScale(ox, oy);
    }

    Events.run(Trigger.draw, () => {
        let currentHpStyle = Core.settings.getString("newex-hp-style", "show-hp");
        if (currentHpStyle === "off") return;

        let bounds = Core.camera.bounds(_perfRect);

        Groups.unit.intersect(bounds.x, bounds.y, bounds.width, bounds.height, cons(u => {
            if (u == null || !u.isValid() || u.dead || !u.isAdded()) return;

            let id = u.id;
            let curHp = u.health;
            let maxHp = u.maxHealth;
            let curShield = u.shield || 0;

            if (!lastHpMap.containsKey(id)) {
                lastHpMap.put(id, curHp);
            }

            let lastHp = lastHpMap.get(id);

             if (Math.abs(curHp - lastHp) > 0.1) {
                if (curHp > lastHp && !healWaveMap.containsKey(id)) healWaveMap.put(id, Time.time);
                if (curHp < lastHp) hpDropMap.put(id, Time.time);
                lastDamageTimeMap.put(id, Time.time);
                lastHpMap.put(id, curHp);
            }

            let lastSh = lastShMap.containsKey(id) ? lastShMap.get(id) : curShield;
            if (Math.abs(curShield - lastSh) > 0.1) {
                if (curShield < lastSh) shDropMap.put(id, Time.time);
                lastDamageTimeMap.put(id, Time.time);
            }
            lastShMap.put(id, curShield);

            let lastDmgTime = lastDamageTimeMap.containsKey(id) ? lastDamageTimeMap.get(id) : -9999;
            let timeDiff = Time.time - lastDmgTime;

             if (timeDiff > 300) {
                smoothHpMap.remove(id);
                smoothShieldMap.remove(id);
                formattedTextMap.remove(id);
                healWaveMap.remove(id);
                return;
            }

             let smoothHpVal = smoothHpMap.containsKey(id) ? smoothHpMap.get(id) : curHp;
            if (!Vars.state.isPaused()) {
                smoothHpVal = Mathf.lerpDelta(smoothHpVal, curHp, 0.1);
                smoothHpMap.put(id, smoothHpVal);
            }

            let realHpPercent = Math.max(0, (smoothHpVal / maxHp) * 100);
            // Xaky(Neo): vạch ngưỡng kết liễu trên thanh máu địch (chỉ người chơi trang bị pilot này thấy)
            let neoMk = 0;
            try { if (global.newexNeoMark != null && u.team != Vars.player.team()) neoMk = global.newexNeoMark(); } catch (err) {}

            let sm = smoothShieldMap.containsKey(id) ? smoothShieldMap.get(id) : curShield;
            if (!Vars.state.isPaused()) { sm = Mathf.lerpDelta(sm, curShield, 0.1); smoothShieldMap.put(id, sm); }

            let big = currentHpStyle === "show-hp", hs = u.hitSize;
            let bw = big ? Math.max(22, hs * 1.2) : Math.max(18, hs), bh = big ? 4.0 : 3;
            let by = u.y + hs / 2 + (big ? 6 : 4), pad = big ? 1.6 : 1.2, bg = big ? "#0d0d11" : "#1f1f1f";
            let lo = big ? 25 : 20, mid = big ? 55 : 50;
            let hpColor = realHpPercent < lo ? Color.red : (realHpPercent < mid ? Color.yellow : Color.green);
            let hpScale = big ? Math.min(0.2, Math.max(0.1, 0.08 * (hs / 8))) : 0.11 * (hs / 8);

            // khi có khiên: độ bền khiên (xanh nước) ghi đè thanh máu; khiên tụt -> máu hiện dưới; máu tụt -> máu lên trên 3s
            let now = Time.time;
            let hasSh = sm > 0.5;
            let hpRecent = hpDropMap.containsKey(id) && now - hpDropMap.get(id) < SWAP_T;
            let shRecent = shDropMap.containsKey(id) && now - shDropMap.get(id) < SWAP_T;
            let top = hasSh ? (hpRecent ? 0 : 1) : 0;      // 0 = máu, 1 = khiên
            let both = hasSh && (hpRecent || shRecent);
            let shFrac = Math.min(1, sm / maxHp);

            let cx = u.x, y2 = by - (bh + pad + 1.2);
            let topFrac = top === 0 ? realHpPercent / 100 : shFrac, topCol = top === 0 ? hpColor : SH_COL;
            drawBar(id, cx, by, bw, bh, topFrac, topCol, top === 0, bg, pad, top === 0 && realHpPercent < lo, neoMk);
            let topTxt;
            if (top === 0) {
                if (big) {
                    let cd = formattedTextMap.get(id);
                    if (!cd || Math.abs(cd.hp - smoothHpVal) > 5) { cd = { hp: smoothHpVal, text: formatHP(smoothHpVal) + " / " + formatHP(maxHp) }; formattedTextMap.put(id, cd); }
                    topTxt = cd.text;
                } else topTxt = Math.floor(realHpPercent) + "%";
            } else {
                let sd = shTextMap.get(id);
                if (!sd || Math.abs(sd.v - sm) > 5) { sd = { v: sm, text: formatHP(sm) }; shTextMap.put(id, sd); }
                topTxt = sd.text;
            }
            let font = Fonts.outline, fx0 = font.getData().scaleX, fy0 = font.getData().scaleY;
            font.getData().setScale(hpScale);
            let capH = font.getCapHeight();
            font.getData().setScale(fx0, fy0);
            drawTxt(topTxt, cx, by + bh / 2 + (big ? 3.5 : 2.5) + capH, hpScale, topCol);
            Draw.z(HP_TOP_Z + 1);
            if (both) { // thanh còn lại nằm dưới
                let lowHp = top === 1;
                drawBar(id, cx, y2, bw, bh, lowHp ? realHpPercent / 100 : shFrac, lowHp ? hpColor : SH_COL, lowHp, bg, pad, false, neoMk);
                let lowTxt;
                if (lowHp) lowTxt = big ? formatHP(smoothHpVal) + " / " + formatHP(maxHp) : Math.floor(realHpPercent) + "%";
                else lowTxt = formatHP(sm);
                drawTxt(lowTxt, cx, y2 - bh / 2 - 1.5, hpScale * 0.8, lowHp ? hpColor : SH_COL);
            }
            Draw.reset();
        }));
    });
})();