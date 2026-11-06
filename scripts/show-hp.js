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

    Events.on(UnitDestroyEvent, e => {
        if (e.unit == null) return;
        let id = e.unit.id;
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