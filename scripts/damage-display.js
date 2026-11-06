// Hiện sát thương nổi trên đầu mục tiêu. Chữ CRIT (tím nhạt) nảy ngay sau số dmg khi bạo kích.
// Các khối khác gọi global.newexCrit(entity) ngay khi tung đòn bạo kích.
const _rect = new Rect(), popups = new Seq(), tmp = new Color(), layout = new GlyphLayout();
const hpCache = new ObjectMap(), dmgCache = new ObjectMap(), critMap = new ObjectMap();
const CRIT = "[#d9b3ff]CRIT![]";

global.newexCrit = e => { if (e != null) critMap.put(e.id, Time.time); };
function takeCrit(id){
    let t = critMap.get(id);
    if (t == null) return false;
    critMap.remove(id);
    return Time.time - t < 6;
}

Events.on(ClientLoadEvent, () => {
    const g = Vars.ui.settings.game;
    g.checkPref("show-hp-popup-eatsuki-tt", true, v => { if (v){ Core.settings.put("show-damage-popup", false); g.rebuild(); } });
    g.checkPref("show-damage-popup", false, v => { if (v){ Core.settings.put("show-hp-popup-eatsuki-tt", false); g.rebuild(); } });
    g.checkPref("show-damage-short-format", true);
});

function clearAll(){ popups.clear(); hpCache.clear(); dmgCache.clear(); critMap.clear(); }
Events.on(WorldLoadEvent, clearAll);
Events.on(StateChangeEvent, e => { if (e.to === GameState.State.playing && e.from !== GameState.State.paused) clearAll(); });
Events.on(UnitDestroyEvent, e => {
    if (e.unit == null) return;
    const id = e.unit.id, d = dmgCache.get(id);
    hpCache.remove(id); critMap.remove(id);
    if (d){ if (d.popup) popups.remove(d.popup); dmgCache.remove(id); }
});

function mode(){
    if (Core.camera.width > 1024) return "none";
    if (Core.settings.getBool("show-hp-popup-eatsuki-tt", true)) return "eatsuki";
    return Core.settings.getBool("show-damage-popup", false) ? "popup" : "none";
}
function fmt(n){
    const a = Math.abs(n);
    if (Core.settings.getBool("show-damage-short-format", true)){
        if (a >= 1e6) return (a / 1e6).toFixed(1) + "M";
        if (a >= 1e3) return (a / 1e3).toFixed(1) + "k";
    }
    return a < 1 ? a.toFixed(1) : Math.round(a).toString();
}
// Độ cao (từ tâm) phía trên thanh máu để số dmg không đè lên chữ HP.
function topOff(ent, isUnit, size){
    if (isUnit && global.newexHpTop) return global.newexHpTop(size);
    return size / 2 + 4;
}

function addEat(ent, dmg, size, isUnit, crit){
    if (dmg < 0.1) return;
    let d = dmgCache.get(ent.id);
    if (!d){ d = {total: 0, idle: 0, popup: null}; dmgCache.put(ent.id, d); }
    d.total += dmg; d.idle = 0;
    let p = d.popup;
    if (p === null || !popups.contains(p)){
        p = d.popup = {type: "eat", id: ent.id, life: 45, max: 45, critAge: -1};
        popups.add(p);
    }
    p.x = ent.x; p.y = ent.y; p.size = size; p.isUnit = isUnit; p.total = d.total; p.life = 45;
    if (crit) p.critAge = 0;
}

// Đòn bạo kích: số dmg nảy lên, rơi xuống rồi biến mất, kèm chữ CRIT tím nhạt.
function addCrit(ent, amount, size, isUnit){
    popups.add({type: "crit", id: 0, x: ent.x, y: ent.y, size: size, isUnit: isUnit, amount: amount, dx: Mathf.random(-14, 14), life: 45, max: 45, critAge: 0});
}

function addFloat(ent, amount, size, isUnit, crit){
    if (Math.abs(amount) < 0.5) return;
    const heal = amount > 0, n = Mathf.rand.nextInt(99999);
    popups.add({
        type: "float", id: n, x: ent.x, y: ent.y, size: size, isUnit: isUnit, amount: Math.abs(amount), heal: heal,
        lane: (n % 3) - 1, life: 40, max: 40, critAge: crit ? 0 : -1
    });
}

Events.run(Trigger.update, () => {
    const m = mode();
    if (m === "none"){ if (popups.size > 0) clearAll(); return; }
    if (Vars.state.isPaused()) return;
    const b = Core.camera.bounds(_rect);
    Groups.unit.intersect(b.x, b.y, b.width, b.height, cons(u => {
        if (!u.isValid() || u.health === Number.POSITIVE_INFINITY) return;
        const last = hpCache.get(u.id);
        if (last != null){
            const diff = last - u.health;
            const crit = diff >= 0.8 && takeCrit(u.id);
            if (crit) addCrit(u, diff, u.hitSize, true);
            if (m === "eatsuki" && diff >= 0.8) addEat(u, diff, u.hitSize, true, false);
            else if (m === "popup" && Math.abs(diff) >= 0.8 && !crit) addFloat(u, -diff, u.hitSize, true, false);
        }
        hpCache.put(u.id, u.health);
    }));
    for (let i = popups.size - 1; i >= 0; i--){
        let p = popups.get(i);
        if (p.critAge >= 0) p.critAge += Time.delta;
        p.life -= Time.delta;
        if (p.type === "eat"){
            let d = dmgCache.get(p.id);
            if (!d){ popups.remove(i); continue; }
            d.idle += Time.delta;
            if (d.idle >= 90 || p.life <= 0){ dmgCache.remove(p.id); popups.remove(i); }
        } else if (p.life <= 0) popups.remove(i);
    }
});

// Vẽ "số" + (nếu crit) chữ CRIT nảy ngay sau số, căn giữa cả cụm.
function drawLine(font, text, x, y, crit, age, a){
    layout.setText(font, text);
    const w1 = layout.width;
    let w2 = 0, gap = 3;
    if (crit){ layout.setText(font, CRIT); w2 = layout.width; } else gap = 0;
    const x0 = x - (w1 + gap + w2) / 2;
    tmp.set(Color.white); tmp.a = a; font.setColor(tmp);
    font.draw(text, x0, y, Align.left);
    if (crit){
        const k = Math.max(0, 1 - age / 20), bounce = Math.abs(Math.sin(age * 0.4)) * 7 * k;
        font.draw(CRIT, x0 + w1 + gap, y + bounce, Align.left);
    }
}

Events.run(Trigger.draw, () => {
    const m = mode();
    if (m === "none" || popups.size === 0) return;
    Draw.z(116);
    const font = Fonts.outline, ox = font.getData().scaleX, oy = font.getData().scaleY;
    for (let i = 0; i < popups.size; i++){
        let p = popups.get(i), f = p.life / p.max, crit = p.critAge >= 0;
        if (p.type === "crit"){
            let t = 1 - f, base = p.y + topOff(p, p.isUnit, p.size) + 8;
            font.getData().setScale(0.34 + 0.14 * Math.max(0, 1 - t * 6));
            drawLine(font, "[#d9b3ff]" + fmt(p.amount) + "[]", p.x + p.dx * t, base + 4 * 26 * t * (1 - t), true, p.critAge, t < 0.7 ? 1 : (1 - t) / 0.3);
        } else if (p.type === "eat" && m === "eatsuki"){
            font.getData().setScale(0.32);
            drawLine(font, "[scarlet]" + fmt(p.total) + "[]", p.x, p.y + topOff(p, p.isUnit, p.size) + 8, crit, p.critAge, f * f);
        } else if (p.type === "float" && m === "popup"){
            let t = 1 - f, big = p.amount >= 1000;
            font.getData().setScale(Math.max(0.15, (big ? 0.42 : 0.28) * (1 - t * t)));
            let txt = p.heal ? "[lime]+" + fmt(p.amount) + "[]" : (big ? "[scarlet]" : "[orange]") + fmt(p.amount) + "[]";
            drawLine(font, txt, p.x + p.lane * 14, p.y + topOff(p, p.isUnit, p.size) + 6 + 16 * (1 - f * f), crit, p.critAge, f * f * f);
        }
    }
    font.getData().setScale(ox, oy);
    Draw.reset();
});

Events.on(BuildDamageEvent, e => {
    const m = mode(), b = e.build;
    if (m === "none" || b == null || !b.isValid() || b.health === Number.POSITIVE_INFINITY) return;
    const last = hpCache.get(b.id);
    let diff = 0;
    if (last != null) diff = last - b.health;
    else if (e.source != null) diff = Math.min(e.source.damage, b.health);
    hpCache.put(b.id, b.health);
    if (Math.abs(diff) < 0.8) return;
    const size = b.block.size * Vars.tilesize, crit = diff > 0 && takeCrit(b.id);
    if (crit) addCrit(b, diff, size, false);
    if (m === "eatsuki") addEat(b, diff, size, false, false);
    else if (!crit) addFloat(b, -diff, size, false, false);
});
