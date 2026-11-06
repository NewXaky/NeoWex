// Vòng nổ dùng chung: đúng bán kính sát thương, màu theo đạn, sống 0.25s, viền các vòng gần nhau liền nhau.
// ring(x, y, radius, color?) - color: Color hoặc "#hex"; bỏ trống = trắng.
(function(){
const cache = {}, live = {}, LIFE = 15, MAXN = 40;
function ringFx(radius, col){
    const r = Math.max(8, Math.round(radius / 2) * 2), key = r + (col ? col.toString() : "");
    return cache[key] || (cache[key] = new Effect(LIFE, r * 2 + 100, cons(e => {
        const f = e.fout(), c = col || e.color, now = Time.time, id = String(e.id), nb = [];
        live[id] = {x: e.x, y: e.y, r: r, end: now - e.time + LIFE, id: e.id};
        let cnt = 0, inside = false;
        for(var k in live){
            var o = live[k];
            if(o.end < now){ delete live[k]; continue; }
            cnt++;
            if(k == id) continue;
            var d = Mathf.dst(e.x, e.y, o.x, o.y);
            if(d < r + o.r && nb.length < 6) nb.push(o);
            if(o.id < e.id && d + r <= o.r) inside = true;
        }
        if(cnt > MAXN) nb.length = 0;
        Draw.z(Layer.effect + 0.1);
        if(!inside){ Draw.color(c); Draw.alpha(0.22 * f); Fill.circle(e.x, e.y, r); }
        Draw.color(Color.white, c, e.fin()); Draw.alpha(0.9 * f); Lines.stroke(3 * f + 0.5); Lines.circle(e.x, e.y, r * Math.sqrt(e.fin()));
        Draw.color(c); Draw.alpha(f); Lines.stroke(1.5 * f);
        if(nb.length == 0) Lines.circle(e.x, e.y, r);
        else{
            const n = Math.max(12, Math.min(48, Math.round(r / 4))), st = 360 / n;
            for(let i = 0; i < n; i++){
                var a0 = i * st, a1 = a0 + st, am = a0 + st / 2, mx = e.x + Mathf.cosDeg(am) * r, my = e.y + Mathf.sinDeg(am) * r;
                let hide = false;
                for(let j = 0; j < nb.length; j++) if(Mathf.dst(mx, my, nb[j].x, nb[j].y) < nb[j].r - 0.5){ hide = true; break; }
                if(!hide) Lines.line(e.x + Mathf.cosDeg(a0) * r, e.y + Mathf.sinDeg(a0) * r, e.x + Mathf.cosDeg(a1) * r, e.y + Mathf.sinDeg(a1) * r);
            }
        }
        Draw.alpha(f * 0.85); Fill.circle(e.x, e.y, Math.min(r * 0.25, 10) * f);
        Draw.reset();
    })));
}
const toColor = c => c == null ? Color.white : (typeof c == "string" ? Color.valueOf(c) : c);
exports.ring = (x, y, radius, color) => ringFx(radius).at(x, y, 0, toColor(color));


// Hạt đường thẳng cho đạn vật lý sát thương đơn: bắn ra từ điểm va chạm, hướng theo đạn (lệch ngẫu nhiên ±8°), sống 0.2s.
const SPARK_LIFE = 12, SPREAD = 8, sparkCache = {}, rnd = new Rand();
function sparkFx(dmg, col){
    const sz = Math.max(18, Math.min(48, Math.round((16 + dmg * 0.5) / 4) * 4)), n = Math.max(5, Math.min(9, Math.round(4 + dmg / 10))), key = sz + "_" + n + col.toString();
    return sparkCache[key] || (sparkCache[key] = new Effect(SPARK_LIFE, sz * 2, cons(e => {
        const fin = e.fin(), fo = e.fout(), g = Interp.pow2Out.apply(fin);
        Draw.z(Layer.effect + 0.2); Draw.blend(Blending.additive);
        rnd.setSeed(e.id);
        const cnt = n + 2 + Math.min(2, Math.floor(rnd.random(0, 3))); // +2..4 hạt ngẫu nhiên mỗi lần
        for(let i = 0; i < cnt; i++){
            const ang = e.rotation + rnd.range(SPREAD), far = sz * rnd.random(0.55, 1), ln = sz * rnd.random(0.25, 0.5) * fo + 1,
                  head = far * g, tail = Math.max(0, head - ln), c = Mathf.cosDeg(ang), s = Mathf.sinDeg(ang);
            Draw.color(Color.white, col, fin); Draw.alpha(fo);
            Lines.stroke(1.8 * fo + 0.4);
            Lines.line(e.x + c * tail, e.y + s * tail, e.x + c * head, e.y + s * head);
        }
        // chớp nhỏ tại điểm va chạm
        Draw.color(Color.white); Draw.alpha(fo * fo); Fill.circle(e.x, e.y, sz * 0.12 * fo);
        Draw.blend(); Draw.reset();
    })));
}

// Màu đạn: hitColor (nếu khác trắng) > backColor > trắng.
function bulletColor(b){
    if(b.hitColor != null && !b.hitColor.equals(Color.white)) return b.hitColor;
    return b.backColor != null ? b.backColor : Color.white;
}
// Hiệu ứng theo màu của từng đạn: lấy màu từ chính viên đạn khi nổ.
const seen = new java.util.IdentityHashMap();
function patch(b){
    if(b == null || seen.containsKey(b)) return;
    seen.put(b, true);
    if(b.splashDamage > 0 && b.splashDamageRadius > 0){
        const fx = ringFx(b.splashDamageRadius, bulletColor(b));
        b.hitEffect = b.hitEffect == null || b.hitEffect == Fx.none ? fx : new MultiEffect(b.hitEffect, fx);
        if(b.despawnHit) b.despawnEffect = b.despawnEffect == null || b.despawnEffect == Fx.none ? fx : new MultiEffect(b.despawnEffect, fx);
    }
    else if(b instanceof BasicBulletType && b.collides && b.damage > 0){
        const sx = sparkFx(b.damage, bulletColor(b));
        b.hitEffect = b.hitEffect == null || b.hitEffect == Fx.none ? sx : new MultiEffect(b.hitEffect, sx);
        // hết thời gian tồn tại: despawnHit đã gọi hitEffect nên chỉ thêm khi không có despawnHit (tránh hiện đôi)
        if(!b.despawnHit) b.despawnEffect = b.despawnEffect == null || b.despawnEffect == Fx.none ? sx : new MultiEffect(b.despawnEffect, sx);
    }
    patch(b.fragBullet); patch(b.intervalBullet);
}
function applyAll(){
    try{
        Vars.content.blocks().each(cons(bl => {
            if(!(bl instanceof Turret)) return;
            if(bl.ammoTypes != null) bl.ammoTypes.values().toSeq().each(cons(patch));
            patch(bl.shootType);
        }));
        Vars.content.units().each(cons(u => u.weapons.each(cons(w => patch(w.bullet)))));
    }catch(err){ Log.err("splash-fx: " + err); }
}
Events.on(ClientLoadEvent, applyAll);
Events.on(ServerLoadEvent, applyAll);
})();
