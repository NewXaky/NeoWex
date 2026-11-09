const PerfCore = global.PerfCore;
const _perfRect = new Rect();
(function() {
    const smoothHpMap = new ObjectMap();
    const smoothShieldMap = new ObjectMap();
    const lastHpMap = new ObjectMap();
    const lastDamageTimeMap = new ObjectMap();
    const formattedTextMap = new ObjectMap();

     function formatHP(value) {
        let val = Math.max(0, Math.floor(value));
        if (val >= 1000000) {
            let m = val / 1000000;
            return (m % 1 === 0 ? m.toFixed(0) : m.toFixed(1)) + "M";
        } else if (val >= 1000) {
            let k = val / 1000;
            return (k % 1 === 0 ? k.toFixed(0) : k.toFixed(1)) + "k";
        }
        return val.toString();
    }

    function clearHpCache() {
        smoothHpMap.clear();
        smoothShieldMap.clear();
        lastHpMap.clear();
        lastDamageTimeMap.clear();
        formattedTextMap.clear();
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
        Draw.z(117);

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
                lastDamageTimeMap.put(id, Time.time);
                lastHpMap.put(id, curHp);
            }

            let lastDmgTime = lastDamageTimeMap.containsKey(id) ? lastDamageTimeMap.get(id) : -9999;
            let timeDiff = Time.time - lastDmgTime;

             if (timeDiff > 300) {
                smoothHpMap.remove(id);
                smoothShieldMap.remove(id);
                formattedTextMap.remove(id);
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

            if (currentHpStyle === "show-hp") {
                let smoothShield = smoothShieldMap.containsKey(id) ? smoothShieldMap.get(id) : curShield;
                if (!Vars.state.isPaused()) {
                    smoothShield = Mathf.lerpDelta(smoothShield, curShield, 0.1);
                    smoothShieldMap.put(id, smoothShield);
                }

                let centerX = u.x;
                let baseY = u.y + (u.hitSize / 2) + 6;

                let barWidth = Math.max(22, u.hitSize * 1.2);
                let barHeight = 4.0;
                let halfW = barWidth / 2;

                Draw.z(115);

                 Draw.color(PerfCore.color("#0d0d11"), 0.8);
                Fill.rect(centerX, baseY, barWidth + 1.6, barHeight + 1.6);

                 let hpColor = Color.green;
                if (realHpPercent < 25) {
                    hpColor = Color.red;
                } else if (realHpPercent < 55) {
                    hpColor = Color.yellow;
                }

                if (realHpPercent < 20) {
                    Draw.alpha(0.6 + Mathf.absin(Time.time, 5, 0.4));
                } else {
                    Draw.alpha(0.9);
                }

                 Draw.color(hpColor);
                let currentBarWidth = barWidth * (Math.max(0, realHpPercent) / 100);
                Fill.rect(centerX - halfW + (currentBarWidth / 2), baseY, currentBarWidth, barHeight);

                 if (smoothShield > 0.5) {
                    let shieldWidth = Math.min(barWidth, barWidth * (smoothShield / maxHp));
                    Draw.color(PerfCore.color("#4ba3e3"), 0.85);
                    Fill.rect(centerX - halfW + (shieldWidth / 2), baseY + (barHeight / 2) + 0.8, shieldWidth, 1.2);
                }

                 Draw.z(116);
                Draw.color(Color.white, 0.45);
                Fill.rect(centerX, baseY + (barHeight / 2) - 0.5, barWidth, 0.8);
                if (neoMk > 0) {
                    let tx = centerX - halfW + barWidth * neoMk;
                    Draw.color(Color.black, 0.9); Fill.rect(tx, baseY, 2.4, barHeight + 4.4);
                    Draw.color(Color.white, 1); Fill.rect(tx, baseY, 1.2, barHeight + 3.2);
                }

                 let font = Fonts.outline;
                let oldX = font.getData().scaleX;
                let oldY = font.getData().scaleY;

                let fontScale = Math.min(0.2, Math.max(0.1, 0.08 * (u.hitSize / 8)));
                font.getData().setScale(fontScale);
                font.setColor(hpColor);

                let cachedData = formattedTextMap.get(id);
                let fullText;
                if (!cachedData || Math.abs(cachedData.hp - smoothHpVal) > 5) {
                    let curStr = formatHP(smoothHpVal);
                    let maxStr = formatHP(maxHp);
                    fullText = curStr + " / " + maxStr;
                    formattedTextMap.put(id, { hp: smoothHpVal, text: fullText });
                } else {
                    fullText = cachedData.text;
                }

                let textY = baseY + (barHeight / 2) + 3.5 + font.getCapHeight();
                font.draw(fullText, centerX, textY, 0, Align.center, false);

                font.getData().setScale(oldX, oldY);
                Draw.reset();
            }

            else if (currentHpStyle === "hp") {
                let hpPercent = Math.floor(realHpPercent);
                let x = u.x;
                let baseY = u.y + (u.hitSize / 2) + 4;
                let hpColor = hpPercent < 20 ? Color.red : (hpPercent < 50 ? Color.yellow : Color.green);
                let barWidth = Math.max(18, u.hitSize);
                let barHeight = 3;

                Draw.z(115);
                Draw.color(PerfCore.color("#1f1f1f"), 0.85);
                Fill.rect(x, baseY, barWidth + 1.2, barHeight + 1.2);
                Draw.color(hpColor);
                if (hpPercent <= 20) Draw.alpha(0.5 + Mathf.absin(Time.time, 6, 0.5));
                let w = barWidth * Math.max(0, realHpPercent) / 100;
                Fill.rect(x - barWidth / 2 + w / 2, baseY, w, barHeight);
                if (neoMk > 0) {
                    let tx = x - barWidth / 2 + barWidth * neoMk;
                    Draw.z(116);
                    Draw.color(Color.black, 0.9); Fill.rect(tx, baseY, 2.2, barHeight + 4);
                    Draw.color(Color.white, 1); Fill.rect(tx, baseY, 1.1, barHeight + 3);
                }

                Draw.z(116);
                let font = Fonts.outline;
                let oldX = font.getData().scaleX;
                let oldY = font.getData().scaleY;
                font.getData().setScale(0.11 * (u.hitSize / 8));
                font.setColor(hpColor);
                font.draw(hpPercent + "%", x, baseY + barHeight / 2 + 2.5 + font.getCapHeight(), 0, Align.center, false);
                font.getData().setScale(oldX, oldY);
                Draw.reset();
            }
        }));
    });
})();