package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Styles;
import newex.NexNet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Honivery la unit THEM MOI (bac dac biet 8) trong tab Unit cua NewMode PvP: xem
 * NewModePvp.tierType(8) / TIER_MAX. Khong thay the unit nao (Collaris van la bac 5).
 * Skill (nut HUD keo tha duoc):
 * - Tung combo {@link #COUNT} cu chem song tinh van (tim - hong - xanh lam - loi trang vang) lien tiep,
 *   cu cuoi la vong chem 360 do. Sat thuong moi cu tang dan tu 50% len 120% tan cong.
 * - Nhat chem dac biet (loc xoay phong kiem ngoc luc bao):
 *   + Khi nhan skill: 2% ti le phong nhat chem dac biet (430% tan cong).
 *   + Khi chem trung muc tieu: +10% ti le phong nhat chem dac biet (tong 12%).
 *   + Khi tieu diet muc tieu: 100% phong nhat chem dac biet ve phia muc tieu.
 */
public final class HoniveryPvp {
    private HoniveryPvp() {}

    static final String REQ = "newex-honivery-skill", FX = "newex-honivery-fx", FX_SPEC = "newex-honivery-spec", KEY = "newex-honivery-btn";
    /** 1 giay hoi chieu (60 tick/s), tinh sau khi combo ket thuc. */
    static final float CD_T = 60f;
    /** So cu chem (cu cuoi la cu to), khoang cach giua 2 cu (tick). */
    static final int COUNT = 8;
    static final float GAP = 15f;
    /** Thoi gian combo, va tong thoi gian truoc khi dung skill lai duoc. */
    static final float COMBO_T = (COUNT - 1) * GAP, CD_TOTAL = COMBO_T + CD_T;
    /** Ban kinh / nua goc quat cua cu chem thuong; tam chem dich ra truoc unit toi da LEAD; tam nham muc tieu trong SEEK. */
    static final float RAD = 48f, HALF = 80f, LEAD = 44f, SEEK = 88f, FIN_RAD_MUL = 1.45f;
    /** Tan cong co ban va he so sat thuong combo (tang dan tu 50% o cu 1 len 120% o cu 8). */
    static final float DMG = 120f, SLASH_DMG_MIN = 0.50f, SLASH_DMG_MAX = 1.20f;
    static final float LIFE = 17f, FIN_LIFE = 27f;

    /** Thong so nhat chem dac biet (loc xoay phong kiem ngoc luc bao). */
    static final float SPEC_DMG_MUL = 4.30f;
    static final float SPEC_CHANCE_CAST = 0.02f;
    static final float SPEC_CHANCE_HIT_BONUS = 0.10f;
    static final float SPEC_LIFE = 44f;
    static final float SPEC_DIST = 165f;
    static final float SPEC_RAD = 32f;
    static final float SPEC_BURST_RAD = 44f;
    static final float SPEC_SEEK = 190f;
    static final int SPEC_MAX_CHAIN = 3;

    /**
     * Goc nghieng (do) cua mat phang moi cu chem thuong so voi mat dat, de tao cam giac chem 3D:
     * 0 = chem ngang sat mat dat, ~90 = chem doc tu tren xuong. Dau am = nghieng nguoc phia.
     */
    static final float[] TILTS = {28f, -42f, 62f, -20f, 48f, -58f, 34f};
    /** He so day do cao len man hinh (huong "len" cua the gioi 3D la huong +y tren man hinh top-down). */
    static final float LIFT = 0.85f, BODY_LIFT = 8f;

    /** Bang mau combo song tinh van (tim tham - hong neon - xanh lam dien - vang hong loi). */
    static final Color COL = Color.valueOf("e58cff");
    static final Color COL_OUTER = Color.valueOf("5c24d8");
    static final Color COL_CYAN = Color.valueOf("3ee2ff");
    static final Color COL_MID = Color.valueOf("e442d8");
    static final Color COL_CORE = Color.valueOf("fff3ff");
    static final Color COL_NODE = Color.valueOf("ffba75");

    /** Bang mau nhat chem dac biet (ngoc luc bao - xanh bac ha - trang sang). */
    static final Color SPEC_DARK = Color.valueOf("00995e");
    static final Color SPEC_MAIN = Color.valueOf("1cff9c");
    static final Color SPEC_MINT = Color.valueOf("9effd6");
    static final Color SPEC_CORE = Color.valueOf("f2fff9");

    static boolean netOk = false;
    static float cdEnd = 0;
    /** Server: combo & phong kiem dac biet dang chay. Client: hieu ung dang ve. */
    static final HashMap<Integer, Combo> combos = new HashMap<>();
    static final ArrayList<SpecialProj> specProjs = new ArrayList<>();
    static final ArrayList<Slash> slashes = new ArrayList<>();
    static final ArrayList<SpecialFx> specFxs = new ArrayList<>();

    static final class Combo {
        final Player pl;
        float t = 0f;
        int idx = 0;
        /** Thu tu goc nghieng (TILTS) cua cac cu chem thuong, xao tron ngau nhien moi lan tung. */
        final int[] order = new int[TILTS.length];

        Combo(Player pl) {
            this.pl = pl;
            for (int i = 0; i < order.length; i++) order[i] = i;
            for (int i = order.length - 1; i > 0; i--) {
                int j = Mathf.random(i);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }
        }
    }

    /** Server: luong phong kiem loc xoay dac biet bay ve phia truoc va gay 430% tan cong. */
    static final class SpecialProj {
        final Unit owner;
        final Team team;
        final float sx, sy, ang, dmg;
        final int chainDepth;
        float t = 0f, prevX, prevY;
        boolean burstDone = false;
        final HashSet<Integer> hitIds = new HashSet<>();

        SpecialProj(Unit owner, float sx, float sy, float ang, float dmg, int chainDepth) {
            this.owner = owner;
            this.team = owner.team;
            this.sx = sx;
            this.sy = sy;
            this.prevX = sx;
            this.prevY = sy;
            this.ang = ang;
            this.dmg = dmg;
            this.chainDepth = chainDepth;
        }
    }

    /** Bong thanh kiem phong to dan roi mat khi tung 1 cu chem. */
    static final class Ghost {
        int uid;
        float t = 0f, life;
        boolean big;
        Color col;
    }

    static final ArrayList<Ghost> ghosts = new ArrayList<>();
    static Honivery.HoniverySwordDrawer swordW;

    static Honivery.HoniverySwordDrawer sword() {
        if (swordW != null) return swordW;
        UnitType h = Honivery.honivery;
        if (h == null) return null;
        for (Weapon w : h.weapons) {
            if (w instanceof Honivery.HoniverySwordDrawer) {
                swordW = (Honivery.HoniverySwordDrawer) w;
                break;
            }
        }
        return swordW;
    }

    static final class Slash {
        float x, y, ang, tilt, t = 0f, life;
        int dir, seed;
        boolean big;
    }

    /** Client: hieu ung nhat chem dac biet (loc xoay phong kiem ngoc luc bao). */
    static final class SpecialFx {
        float sx, sy, ang, t = 0f, life = SPEC_LIFE;
        int seed;
    }

    static final HashMap<Integer, Float> cdEndSrv = new HashMap<>();
    static Table btn = null;

    static boolean isActive() {
        try {
            return NewModePvp.apiIsActive();
        } catch (Throwable err) {
            return false;
        }
    }

    static void update() {
        tryNet();
        if (Vars.state == null || !Vars.state.isGame()) {
            if (!combos.isEmpty()) combos.clear();
            if (!specProjs.isEmpty()) specProjs.clear();
            if (!slashes.isEmpty()) slashes.clear();
            if (!specFxs.isEmpty()) specFxs.clear();
            if (!ghosts.isEmpty()) ghosts.clear();
            return;
        }
        if (Vars.state.isPaused()) return;
        if (!Vars.net.client()) {
            tickCombos();
            tickSpecialProjs();
        }
        for (int i = slashes.size() - 1; i >= 0; i--) {
            Slash sl = slashes.get(i);
            sl.t += Time.delta;
            if (sl.t >= sl.life) slashes.remove(i);
        }
        for (int i = specFxs.size() - 1; i >= 0; i--) {
            SpecialFx sf = specFxs.get(i);
            sf.t += Time.delta;
            if (sf.t >= sf.life) specFxs.remove(i);
        }
        for (int i = ghosts.size() - 1; i >= 0; i--) {
            Ghost g = ghosts.get(i);
            g.t += Time.delta;
            if (g.t >= g.life) ghosts.remove(i);
        }
    }

    static boolean isHon(Unit u) {
        return u != null && Honivery.honivery != null && u.type == Honivery.honivery;
    }

    static void tryNet() {
        if (netOk) return;
        netOk = true;
        NexNet.on(REQ, HoniveryPvp::onReq, false);
        NexNet.onBroadcast(FX, HoniveryPvp::onFx);
        NexNet.onBroadcast(FX_SPEC, HoniveryPvp::onSpecFx);
    }

    static float specialTravelDist(float p) {
        float travelP = Interp.pow3Out.apply(Mathf.clamp(p / 0.70f));
        return SPEC_DIST * travelP;
    }

    static float findAimAngle(Unit u, float range) {
        mindustry.gen.Teamc tg = null;
        try {
            tg = Units.closestTarget(u.team, u.x, u.y, range, e -> !e.dead, b -> true);
        } catch (Throwable err) {}
        return tg != null ? Angles.angle(u.x, u.y, tg.x(), tg.y()) : u.rotation;
    }

    /** Server: kich hoat nhat chem dac biet (loc xoay phong kiem 430% tan cong). */
    static void spawnSpecial(Unit u, float ang, int chainDepth) {
        if (u == null || u.dead || chainDepth > SPEC_MAX_CHAIN) return;
        float dmg = DMG * SPEC_DMG_MUL * Math.max(0.1f, u.damageMultiplier);
        specProjs.add(new SpecialProj(u, u.x, u.y, ang, dmg, chainDepth));
        NexNet.broadcast(FX_SPEC, u.x + "," + u.y + "," + ang + "," + u.id);
    }

    // ---- server: nhan yeu cau dung skill ----
    static void onReq(Player player, String payload) {
        if (!isActive()) return;
        Unit u = player.unit();
        if (!isHon(u) || u.dead) return;
        if (combos.containsKey(player.id)) return;
        Float end = cdEndSrv.get(player.id);
        if (end != null && Time.time < end) return;
        cdEndSrv.put(player.id, Time.time + CD_TOTAL);
        combos.put(player.id, new Combo(player));

        // Khi nhan skill: ti le 2% phong ngay nhat chem dac biet
        if (Mathf.chance(SPEC_CHANCE_CAST)) {
            spawnSpecial(u, findAimAngle(u, SPEC_SEEK), 0);
        }
    }

    /** Server: moi tick chay cac combo, den gio thi tung cu chem tiep theo. */
    static void tickCombos() {
        if (combos.isEmpty()) return;
        for (Iterator<Combo> it = combos.values().iterator(); it.hasNext();) {
            Combo c = it.next();
            Unit u = c.pl.unit();
            if (!isActive() || !isHon(u) || u.dead) {
                it.remove();
                continue;
            }
            while (c.idx < COUNT && c.t >= c.idx * GAP) {
                doSlash(u, c);
                c.idx++;
            }
            c.t += Time.delta;
            if (c.idx >= COUNT) it.remove();
        }
    }

    /** Server: cap nhat duong bay & va cham cua nhat chem dac biet (430% tan cong). */
    static void tickSpecialProjs() {
        if (specProjs.isEmpty()) return;
        for (int i = specProjs.size() - 1; i >= 0; i--) {
            SpecialProj sp = specProjs.get(i);
            sp.t += Time.delta;
            float p = Mathf.clamp(sp.t / SPEC_LIFE);
            float dist = specialTravelDist(p);
            float px = sp.sx + Angles.trnsx(sp.ang, dist);
            float py = sp.sy + Angles.trnsy(sp.ang, dist);

            boolean isPeak = !sp.burstDone && p >= 0.70f;
            if (isPeak) sp.burstDone = true;
            float hitRad = isPeak ? SPEC_BURST_RAD : SPEC_RAD;

            float minX = Math.min(sp.prevX, px) - hitRad - 24f;
            float minY = Math.min(sp.prevY, py) - hitRad - 24f;
            float w = Math.abs(px - sp.prevX) + (hitRad + 24f) * 2f;
            float h = Math.abs(py - sp.prevY) + (hitRad + 24f) * 2f;

            final float segX1 = sp.prevX, segY1 = sp.prevY, segX2 = px, segY2 = py;
            Units.nearbyEnemies(sp.team, minX, minY, w, h, e -> {
                if (e.dead || sp.hitIds.contains(e.id)) return;
                float r = hitRad + e.hitSize / 2f;
                if (dstToSegment(e.x, e.y, segX1, segY1, segX2, segY2) > r) return;
                sp.hitIds.add(e.id);
                boolean wasAlive = !e.dead && e.health > 0f;
                e.damage(sp.dmg);
                try {
                    Call.effect(Fx.hitLaserBlast, e.x, e.y, sp.ang, SPEC_MAIN);
                    Call.effect(Fx.sparkShoot, e.x, e.y, sp.ang, SPEC_CORE);
                } catch (Throwable err) {}
                if (wasAlive && (e.dead || e.health <= 0f) && sp.owner != null && !sp.owner.dead) {
                    spawnSpecial(sp.owner, Angles.angle(sp.owner.x, sp.owner.y, e.x, e.y), sp.chainDepth + 1);
                }
            });

            Units.nearbyBuildings(px, py, hitRad + 24f, b -> {
                if (b.team == sp.team || b.dead || sp.hitIds.contains(-b.id)) return;
                float r = hitRad + b.hitSize() / 2f;
                if (dstToSegment(b.x, b.y, segX1, segY1, segX2, segY2) > r) return;
                sp.hitIds.add(-b.id);
                boolean wasAlive = !b.dead && b.health > 0f;
                b.damage(sp.dmg);
                try {
                    Call.effect(Fx.hitLaserBlast, b.x, b.y, sp.ang, SPEC_MAIN);
                } catch (Throwable err) {}
                if (wasAlive && (b.dead || b.health <= 0f) && sp.owner != null && !sp.owner.dead) {
                    spawnSpecial(sp.owner, Angles.angle(sp.owner.x, sp.owner.y, b.x, b.y), sp.chainDepth + 1);
                }
            });

            sp.prevX = px;
            sp.prevY = py;
            if (sp.t >= SPEC_LIFE) {
                specProjs.remove(i);
            }
        }
    }

    static float dstToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 <= 0.0001f) return Mathf.dst(px, py, x1, y1);
        float t = Mathf.clamp(((px - x1) * dx + (py - y1) * dy) / len2);
        return Mathf.dst(px, py, x1 + t * dx, y1 + t * dy);
    }

    static boolean inSector(float cx, float cy, float face, float half, float rad, float x, float y) {
        float d = Mathf.dst(cx, cy, x, y);
        if (d > rad) return false;
        return half >= 180f || d < 14f || Math.abs(Angles.angleDist(Angles.angle(cx, cy, x, y), face)) <= half;
    }

    /** Server: 1 cu chem. Sat thuong tang dan tu 50% (cu 1) len 120% (cu 8). */
    static void doSlash(Unit u, Combo c) {
        int idx = c.idx;
        boolean fin = idx == COUNT - 1;
        final int dir = Mathf.chance(0.5) ? 1 : -1;
        int ti = c.order[idx % c.order.length];
        mindustry.gen.Teamc tg = null;
        try {
            tg = Units.closestTarget(u.team, u.x, u.y, SEEK, e -> !e.dead, b -> true);
        } catch (Throwable err) {}
        float ang = tg != null ? Angles.angle(u.x, u.y, tg.x(), tg.y()) : u.rotation;
        float reach = tg != null ? Math.min(Mathf.dst(u.x, u.y, tg.x(), tg.y()), LEAD) : LEAD * 0.5f;
        final float cx = u.x + Angles.trnsx(ang, reach), cy = u.y + Angles.trnsy(ang, reach);
        final float face = fin ? ang : ang + Mathf.range(35f);
        final float half = fin ? 180f : HALF;
        final float rad = fin ? RAD * FIN_RAD_MUL : RAD;

        // Sat thuong tang dan tu 50% len 120% cho tung nhat chem
        float progress = COUNT > 1 ? (idx / (float) (COUNT - 1)) : 1f;
        float slashScale = Mathf.lerp(SLASH_DMG_MIN, SLASH_DMG_MAX, progress);
        final float dmg = DMG * slashScale * Math.max(0.1f, u.damageMultiplier);

        final Team team = u.team;
        final float reachAll = rad + 16f;
        final boolean[] hitAny = {false};
        final boolean[] killedAny = {false};
        final float[] hitPos = {cx, cy};

        Units.nearbyEnemies(team, cx - reachAll, cy - reachAll, reachAll * 2, reachAll * 2, e -> {
            if (e.dead || !inSector(cx, cy, face, half, rad + e.hitSize / 2f, e.x, e.y)) return;
            boolean wasAlive = !e.dead && e.health > 0f;
            e.damage(dmg);
            hitAny[0] = true;
            hitPos[0] = e.x;
            hitPos[1] = e.y;
            try {
                Call.effect(Fx.hitLaserBlast, e.x, e.y, face, dir > 0 ? COL_CYAN : COL_MID);
                Call.effect(Fx.sparkShoot, e.x, e.y, face, COL_CORE);
            } catch (Throwable err) {}
            // Khi tieu diet muc tieu: phong nhat chem dac biet
            if (wasAlive && (e.dead || e.health <= 0f)) {
                killedAny[0] = true;
                spawnSpecial(u, Angles.angle(u.x, u.y, e.x, e.y), 0);
            }
        });

        Units.nearbyBuildings(cx, cy, reachAll, b -> {
            if (b.team == team || !inSector(cx, cy, face, half, rad + b.hitSize() / 2f, b.x, b.y)) return;
            boolean wasAlive = !b.dead && b.health > 0f;
            b.damage(dmg);
            hitAny[0] = true;
            hitPos[0] = b.x;
            hitPos[1] = b.y;
            try {
                Call.effect(Fx.hitLaserBlast, b.x, b.y, face, dir > 0 ? COL_CYAN : COL_MID);
            } catch (Throwable err) {}
            // Khi pha huy cong trinh muc tieu: phong nhat chem dac biet
            if (wasAlive && (b.dead || b.health <= 0f)) {
                killedAny[0] = true;
                spawnSpecial(u, Angles.angle(u.x, u.y, b.x, b.y), 0);
            }
        });

        // Khi chem trung muc tieu (ma chua kich hoat tu tieu diet): +10% ti le phong nhat chem dac biet (2% + 10% = 12%)
        if (hitAny[0] && !killedAny[0] && Mathf.chance(SPEC_CHANCE_CAST + SPEC_CHANCE_HIT_BONUS)) {
            spawnSpecial(u, Angles.angle(u.x, u.y, hitPos[0], hitPos[1]), 0);
        }

        NexNet.broadcast(FX, cx + "," + cy + "," + face + "," + dir + "," + (fin ? 1 : 0) + "," + ti + "," + u.id);
    }

    // ---- moi may: nhan 1 cu chem -> tao vet chem de ve ----
    static void onFx(String payload) {
        if (Vars.headless) return;
        try {
            String[] p = String.valueOf(payload).split(",");
            Slash sl = new Slash();
            sl.x = Float.parseFloat(p[0]);
            sl.y = Float.parseFloat(p[1]);
            sl.ang = Float.parseFloat(p[2]);
            sl.dir = Integer.parseInt(p[3]);
            sl.big = Integer.parseInt(p[4]) == 1;
            sl.life = sl.big ? FIN_LIFE : LIFE;
            sl.seed = Mathf.random(10000);
            int ti = p.length > 5 ? Integer.parseInt(p[5]) : 0;
            sl.tilt = TILTS[Math.floorMod(ti, TILTS.length)];
            if (p.length > 6) {
                Ghost g = new Ghost();
                g.uid = Integer.parseInt(p[6]);
                g.big = sl.big;
                g.life = sl.big ? 30f : 20f;
                g.col = sl.big ? COL_MID : (sl.dir > 0 ? COL_CYAN : COL_MID);
                ghosts.add(g);
            }
            slashes.add(sl);
            Effect.shake(sl.big ? 5f : 2.2f, sl.big ? 12f : 6f, sl.x, sl.y);
            Sounds.shootReign.at(sl.x, sl.y, sl.big ? 0.7f : 1.3f + sl.dir * 0.08f, 0.5f);
            Fx.sparkShoot.at(sl.x, sl.y, sl.ang, sl.dir > 0 ? COL_CYAN : COL_MID);
        } catch (Throwable err) {
            Log.err("[HoniveryPvp] fx: " + err);
        }
    }

    // ---- moi may: nhan nhat chem dac biet (loc xoay phong kiem ngoc luc bao) ----
    static void onSpecFx(String payload) {
        if (Vars.headless) return;
        try {
            String[] p = String.valueOf(payload).split(",");
            SpecialFx sf = new SpecialFx();
            sf.sx = Float.parseFloat(p[0]);
            sf.sy = Float.parseFloat(p[1]);
            sf.ang = Float.parseFloat(p[2]);
            sf.seed = Mathf.random(10000);
            if (p.length > 3) {
                Ghost g = new Ghost();
                g.uid = Integer.parseInt(p[3]);
                g.big = true;
                g.life = 28f;
                g.col = SPEC_MAIN;
                ghosts.add(g);
            }
            specFxs.add(sf);
            Effect.shake(4.5f, 11f, sf.sx, sf.sy);
            Sounds.shootReign.at(sf.sx, sf.sy, 1.55f, 0.62f);
            Sounds.shootReign.at(sf.sx, sf.sy, 0.68f, 0.55f);
            Fx.sparkShoot.at(sf.sx, sf.sy, sf.ang, SPEC_MAIN);
        } catch (Throwable err) {
            Log.err("[HoniveryPvp] specFx: " + err);
        }
    }

    // ---- ve vong chem 360 do cuoi combo theo phong cach song tinh van ----
    static void drawArcLayer(Slash s, Color cStart, Color cEnd, float rMul, float wMul, float headDelay, float tailBoost, float alphaMul) {
        float p = s.t / s.life;
        float hp = Mathf.clamp((p - headDelay) / (0.42f - headDelay * 0.5f));
        float head = Interp.pow2Out.apply(hp);
        float tail = head * Mathf.clamp((p - 0.08f + tailBoost) / 0.92f);
        float fade = 1f - Mathf.clamp((p - 0.45f) / 0.55f);
        if (head - tail < 0.01f || fade <= 0f) return;

        float half = s.big ? 180f : 95f;
        float R = (s.big ? RAD * FIN_RAD_MUL : RAD) * rMul;
        float W = R * (s.big ? 0.32f : 0.42f) * wMul;
        int N = s.big ? 40 : 22;

        for (int k = 0; k < N; k++) {
            float f1 = Mathf.lerp(tail, head, k / (float) N);
            float f2 = Mathf.lerp(tail, head, (k + 1) / (float) N);
            float tp = (k + 0.5f) / N;
            float a1 = s.ang + s.dir * Mathf.lerp(-half, half, f1);
            float a2 = s.ang + s.dir * Mathf.lerp(-half, half, f2);
            float taper1 = (float) Math.pow(Mathf.sinDeg(f1 * 180f), 0.65) * (0.20f + 0.80f * (k / (float) N));
            float taper2 = (float) Math.pow(Mathf.sinDeg(f2 * 180f), 0.65) * (0.20f + 0.80f * ((k + 1) / (float) N));
            float w1 = W * taper1;
            float w2 = W * taper2;

            Draw.color(cStart, cEnd, tp);
            Draw.alpha(fade * alphaMul * (0.35f + 0.65f * tp));
            Fill.quad(
                s.x + Angles.trnsx(a1, R), s.y + Angles.trnsy(a1, R),
                s.x + Angles.trnsx(a2, R), s.y + Angles.trnsy(a2, R),
                s.x + Angles.trnsx(a2, R - w2), s.y + Angles.trnsy(a2, R - w2),
                s.x + Angles.trnsx(a1, R - w1), s.y + Angles.trnsy(a1, R - w1));
        }
    }

    /**
     * Ve vet chem 3D da lop phong cach song tinh van (lay cam hung tu video 1):
     * - Lop ngoai: dai tinh van tim tham - xanh lam dien rong, tao chieu sau khong gian.
     * - Lop giua: cac soi plasma hong - tim dan xen lech pha (feathered filaments).
     * - Lop loi: luoi cat trang sang + cac nut nang luong vang hong doc theo song kiem + bui sao li ti.
     */
    static void drawArc3d(Slash s) {
        float p = s.t / s.life;
        float head = Interp.pow2Out.apply(Mathf.clamp(p / 0.38f));
        float tail = head * Mathf.clamp((p - 0.08f) / 0.92f);
        float fade = 1f - Mathf.clamp((p - 0.45f) / 0.55f);
        if (head - tail < 0.01f || fade <= 0f) return;

        final float half = 98f, R = RAD, W = R * 0.44f;
        float cosT = Mathf.cosDeg(s.tilt), sinT = Mathf.sinDeg(s.tilt);
        float ax = Mathf.cosDeg(s.ang), ay = Mathf.sinDeg(s.ang);
        float hx = -ay, hy = ax;
        float oy = s.y + BODY_LIFT;

        // Vong dinh vi mat phang nghieng mo (tim - xanh lam)
        Draw.color(COL_OUTER, COL_CYAN, 0.45f);
        Draw.alpha(0.14f * fade);
        Lines.stroke(1.1f);
        for (int k = 0; k < 28; k++) {
            float t1 = k * 360f / 28f, t2 = (k + 1) * 360f / 28f;
            float x1 = R * Mathf.cosDeg(t1), l1 = R * Mathf.sinDeg(t1);
            float x2 = R * Mathf.cosDeg(t2), l2 = R * Mathf.sinDeg(t2);
            Lines.line(
                s.x + x1 * ax + l1 * cosT * hx, oy + x1 * ay + l1 * cosT * hy + l1 * sinT * LIFT,
                s.x + x2 * ax + l2 * cosT * hx, oy + x2 * ay + l2 * cosT * hy + l2 * sinT * LIFT);
        }

        // Lop 1: Dai tinh van rong ben ngoai (tim tham -> xanh lam dien)
        draw3dRibbon(s, ax, ay, hx, hy, oy, cosT, sinT, half, R * 1.04f, W * 1.18f, head, tail, fade,
            COL_OUTER, s.dir > 0 ? COL_CYAN : COL_MID, 0.55f, 22);

        // Lop 2 & 3: Cac soi plasma dan xen (hong neon -> tim sang -> trang)
        float subHead1 = Interp.pow2Out.apply(Mathf.clamp((p - 0.02f) / 0.38f));
        float subTail1 = subHead1 * Mathf.clamp((p - 0.05f) / 0.95f);
        if (subHead1 - subTail1 > 0.01f) {
            draw3dRibbon(s, ax, ay, hx, hy, oy, cosT, sinT, half, R * 0.98f, W * 0.62f, subHead1, subTail1, fade,
                COL_MID, COL_CORE, 0.85f, 20);
        }

        float subHead2 = Interp.pow2Out.apply(Mathf.clamp(p / 0.35f));
        float subTail2 = subHead2 * Mathf.clamp((p - 0.14f) / 0.86f);
        if (subHead2 - subTail2 > 0.01f) {
            draw3dRibbon(s, ax, ay, hx, hy, oy, cosT, sinT, half * 0.96f, R * 0.85f, W * 0.42f, subHead2, subTail2, fade,
                COL_CYAN, COL_MID, 0.72f, 18);
        }

        // Lop 4: Luoi cat trang sang ruc o mep ngoai cung
        draw3dRibbon(s, ax, ay, hx, hy, oy, cosT, sinT, half, R * 1.01f, W * 0.22f, head, tail, fade,
            COL_MID, COL_CORE, 0.95f, 22);

        // Nut nang luong vang hong doc theo song kiem (chevron/spine nodes)
        int nodes = 5;
        for (int i = 0; i < nodes; i++) {
            float nf = Mathf.lerp(tail + (head - tail) * 0.25f, head, (i + 0.5f) / nodes);
            float angNode = s.dir * Mathf.lerp(-half, half, nf);
            float rNode = R * (0.86f + 0.04f * Mathf.sin(i * 2.1f + s.seed));
            float fw = rNode * Mathf.cosDeg(angNode), lat = rNode * Mathf.sinDeg(angNode);
            float nx = s.x + fw * ax + lat * cosT * hx;
            float ny = oy + fw * ay + lat * cosT * hy + lat * sinT * LIFT;
            float sz = (1.6f + 1.4f * (i / (float) nodes)) * fade;
            Draw.color(COL_NODE, COL_CORE, 0.5f);
            Draw.alpha(fade * 0.85f);
            Fill.circle(nx, ny, sz);
        }

        // Hat bui sao tinh van bay theo vet chem
        for (int i = 0; i < 7; i++) {
            float hash = Mathf.sin(s.seed * 13.7f + i * 7.3f) * 0.5f + 0.5f;
            float sf = Mathf.lerp(tail, head, hash);
            float angS = s.dir * Mathf.lerp(-half, half, sf);
            float drift = p * (6f + 10f * ((i % 3) * 0.5f));
            float rS = R * (0.72f + 0.34f * hash) + drift;
            float fw = rS * Mathf.cosDeg(angS), lat = rS * Mathf.sinDeg(angS);
            float sx = s.x + fw * ax + lat * cosT * hx;
            float sy = oy + fw * ay + lat * cosT * hy + lat * sinT * LIFT + p * 4f;
            Draw.color(i % 2 == 0 ? COL_CYAN : COL_CORE);
            Draw.alpha(fade * 0.75f);
            Fill.circle(sx, sy, 0.9f + 0.7f * hash);
        }
    }

    static void draw3dRibbon(
        Slash s, float ax, float ay, float hx, float hy, float oy,
        float cosT, float sinT, float half, float R, float W,
        float head, float tail, float fade, Color cStart, Color cEnd, float alphaMul, int N
    ) {
        float[] v = new float[8];
        for (int k = 0; k < N; k++) {
            float f1 = Mathf.lerp(tail, head, k / (float) N);
            float f2 = Mathf.lerp(tail, head, (k + 1) / (float) N);
            float tp = (k + 0.5f) / N;
            float t1 = s.dir * Mathf.lerp(-half, half, f1);
            float t2 = s.dir * Mathf.lerp(-half, half, f2);
            float c1 = Mathf.cosDeg(t1), n1 = Mathf.sinDeg(t1);
            float c2 = Mathf.cosDeg(t2), n2 = Mathf.sinDeg(t2);
            float w1 = W * (float) Math.pow(Mathf.sinDeg(f1 * 180f), 0.68) * (0.22f + 0.78f * (k / (float) N));
            float w2 = W * (float) Math.pow(Mathf.sinDeg(f2 * 180f), 0.68) * (0.22f + 0.78f * ((k + 1) / (float) N));
            float[] rr = {R, R, R - w2, R - w1};
            float[] cc = {c1, c2, c2, c1};
            float[] nn = {n1, n2, n2, n1};
            float yoSum = 0f;
            for (int q = 0; q < 4; q++) {
                float fw = rr[q] * cc[q], lat = rr[q] * nn[q];
                v[q * 2] = s.x + fw * ax + lat * cosT * hx;
                v[q * 2 + 1] = oy + fw * ay + lat * cosT * hy + lat * sinT * LIFT;
                yoSum += v[q * 2 + 1] - oy;
            }
            float near = Mathf.clamp(-(yoSum / 4f) / R, -1f, 1f);
            Draw.color(cStart, cEnd, Mathf.clamp(tp + near * 0.22f));
            Draw.alpha(fade * alphaMul * (0.72f + 0.28f * near));
            Fill.quad(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]);
        }
    }

    /** Cu cuoi 360 do: vong no tinh van da lop tim - hong - xanh lam - loi vang trang. */
    static void drawZone(Slash s) {
        float p = s.t / s.life;
        float fade = 1f - Mathf.clamp((p - 0.5f) / 0.5f);
        float R = RAD * FIN_RAD_MUL;

        Draw.color(COL_OUTER, COL_MID, 0.45f);
        Draw.alpha(0.11f * fade);
        Fill.circle(s.x, s.y, R);

        Draw.color(COL_CYAN, COL_CORE, 0.35f);
        Draw.alpha(0.45f * fade);
        Lines.stroke(1.8f);
        Lines.circle(s.x, s.y, R);

        // Cac hat sao bay quanh vong 360
        for (int i = 0; i < 12; i++) {
            float a = s.ang + i * 30f + s.dir * p * 140f;
            float r = R * (0.78f + 0.24f * Mathf.sin(i * 1.9f + p * 6f));
            Draw.color(i % 3 == 0 ? COL_NODE : (i % 2 == 0 ? COL_CYAN : COL_CORE));
            Draw.alpha(0.8f * fade);
            Fill.circle(s.x + Angles.trnsx(a, r), s.y + Angles.trnsy(a, r), 1.4f * fade);
        }
    }

    /**
     * Ve nhat chem dac biet (y chang video 2):
     * - Luc phong: nhat chem luoi liem ngoc luc bao + vong song xung kich trang o vi tri xuat phat.
     * - Khi bay: dau kiem luoi liem xanh ngoc + 4 dai loc xoay 3D quan quanh + 2 vong song trang + duoi gio & tia lua.
     * - Khi dat dinh (p ~ 0.70): bung sang trang-ngoc luc bao va tan dan.
     */
    static void drawSpecial(SpecialFx s) {
        float p = Mathf.clamp(s.t / s.life);
        float fade = 1f - Mathf.clamp((p - 0.68f) / 0.32f);
        if (fade <= 0f) return;

        float dist = specialTravelDist(p);
        float px = s.sx + Angles.trnsx(s.ang, dist);
        float py = s.sy + Angles.trnsy(s.ang, dist);
        float ax = Mathf.cosDeg(s.ang), ay = Mathf.sinDeg(s.ang);
        float hx = -ay, hy = ax;

        // 1. Hieu ung bung no & vong song xung kich trang tai diem xuat phat (p < 0.38)
        if (p < 0.38f) {
            float lp = p / 0.38f;
            float lFade = 1f - lp;
            float burstR = 26f + 34f * Interp.pow2Out.apply(lp);

            // Vong song xung kich trang hinh elip vuong goc huong phong
            Draw.color(Color.white, SPEC_MINT, 0.3f);
            Draw.alpha(0.75f * lFade);
            Lines.stroke(2.2f * lFade);
            drawTiltedRing(s.sx + ax * 10f, s.sy + ay * 10f, ax, ay, hx, hy, burstR * 0.38f, burstR, 0f, 360f, 24);

            // Luoi chem khoi dau mau xanh ngoc luc bao
            drawCrescentBlade(s.sx + ax * (14f * lp), s.sy + ay * (14f * lp), s.ang, 36f + 16f * lp, 14f * lFade, 95f, SPEC_MAIN, Color.white, 0.85f * lFade);
        }

        // 2. Cac dai duoi gio (TrailBeam) keo dai tu diem phong toi loc xoay
        float tailDist = Math.max(0f, dist - 78f);
        float tx = s.sx + Angles.trnsx(s.ang, tailDist);
        float ty = s.sy + Angles.trnsy(s.ang, tailDist);
        for (int b = -1; b <= 1; b++) {
            float spread = b * 9f;
            float midX = Mathf.lerp(tx, px, 0.55f) + hx * (spread + Mathf.sin(p * 18f + b * 2f) * 5f);
            float midY = Mathf.lerp(ty, py, 0.55f) + hy * (spread + Mathf.sin(p * 18f + b * 2f) * 5f);
            Draw.color(SPEC_DARK, SPEC_MAIN, b == 0 ? 0.8f : 0.4f);
            Draw.alpha(fade * (b == 0 ? 0.65f : 0.42f));
            Lines.stroke((b == 0 ? 3.2f : 1.8f) * fade);
            Lines.line(tx + hx * (spread * 0.3f), ty + hy * (spread * 0.3f), midX, midY);
            Lines.stroke((b == 0 ? 4.4f : 2.2f) * fade);
            Lines.line(midX, midY, px + hx * (spread * 0.5f), py + hy * (spread * 0.5f));
        }

        // 3. Dau luoi kiem luoi liem dan duong (p < 0.82)
        if (p < 0.82f) {
            float headAlpha = 1f - Mathf.clamp((p - 0.55f) / 0.27f);
            drawCrescentBlade(px + ax * 10f, py + ay * 10f, s.ang, 34f, 12f, 82f, SPEC_MAIN, Color.white, 0.92f * headAlpha);
        }

        // 4. Loc xoay 3D ngoc luc bao (4 dai gio xoan oc quay nhanh quanh tam (px, py))
        float expand = p > 0.70f ? 1f + 0.38f * ((p - 0.70f) / 0.30f) : 1f;
        float vortexR = 25f * expand;
        float spin = s.t * 26f;

        // Loi sang xanh bac ha o giua loc xoay
        Draw.color(SPEC_MAIN, SPEC_CORE, 0.55f);
        Draw.alpha(0.32f * fade);
        Fill.circle(px, py, vortexR * 0.65f);
        Draw.color(Color.white);
        Draw.alpha(0.45f * fade);
        Fill.circle(px, py, vortexR * 0.26f);

        // 4 vong gio xoan nghieng 3D (GreenTwirl)
        float[] ringTilts = {25f, -35f, 65f, -15f};
        float[] ringSpd = {1.0f, -1.25f, 1.4f, -0.9f};
        for (int r = 0; r < 4; r++) {
            float tilt = ringTilts[r];
            float cosR = Mathf.cosDeg(tilt), sinR = Mathf.sinDeg(tilt);
            float rax = ax * cosR - hx * sinR;
            float ray = ay * cosR - hy * sinR;
            float rhx = -ray, rhy = rax;
            float startAng = spin * ringSpd[r] + r * 90f + s.seed;
            float rx = vortexR * (0.85f + 0.12f * r);
            float ry = vortexR * (0.48f + 0.08f * (r % 2));

            // Ve dai gio co do day giam dan tu dau toi duoi
            int segs = 16;
            for (int k = 0; k < segs; k++) {
                float f1 = k / (float) segs, f2 = (k + 1) / (float) segs;
                float a1 = startAng + f1 * 235f, a2 = startAng + f2 * 235f;
                float x1 = px + rax * (rx * Mathf.cosDeg(a1)) + rhx * (ry * Mathf.sinDeg(a1));
                float y1 = py + ray * (rx * Mathf.cosDeg(a1)) + rhy * (ry * Mathf.sinDeg(a1));
                float x2 = px + rax * (rx * Mathf.cosDeg(a2)) + rhx * (ry * Mathf.sinDeg(a2));
                float y2 = py + ray * (rx * Mathf.cosDeg(a2)) + rhy * (ry * Mathf.sinDeg(a2));
                Draw.color(SPEC_DARK, r % 2 == 0 ? SPEC_CORE : SPEC_MAIN, f2);
                Draw.alpha(fade * (0.25f + 0.70f * f2));
                Lines.stroke((1.1f + 3.0f * f2) * fade);
                Lines.line(x1, y1, x2, y2);
            }
        }

        // 5. 2 vong song xung kich trang boc quanh than loc xoay (WindShockwave)
        Draw.color(Color.white, SPEC_MINT, 0.25f);
        Draw.alpha(0.75f * fade);
        Lines.stroke(1.9f * fade);
        drawTiltedRing(px + ax * 7f, py + ay * 7f, ax, ay, hx, hy, vortexR * 0.42f, vortexR * 1.08f, spin * 0.5f, 290f, 22);
        Draw.alpha(0.55f * fade);
        Lines.stroke(1.4f * fade);
        drawTiltedRing(px - ax * 5f, py - ay * 5f, ax, ay, hx, hy, vortexR * 0.52f, vortexR * 1.22f, -spin * 0.7f + 110f, 260f, 20);

        // 6. Bung sang cuc dai khi loc xoay dat tam (p ~ 0.65 -> 0.90)
        if (p >= 0.62f && p <= 0.92f) {
            float bp = (p - 0.62f) / 0.30f;
            float bFlash = Mathf.sinDeg(bp * 180f);
            float bRad = 18f + 36f * bp;
            Draw.color(Color.white, SPEC_MAIN, 0.35f);
            Draw.alpha(0.75f * bFlash);
            Lines.stroke(2.5f * bFlash);
            Lines.circle(px, py, bRad);
            Draw.alpha(0.55f * bFlash);
            Fill.circle(px, py, 16f * bFlash);
        }

        // 7. Tia lua ngoc luc bao & vang nhat bay quanh loc xoay (Specs)
        for (int i = 0; i < 9; i++) {
            float a = spin * 0.85f + i * 40f + s.seed;
            float orbit = vortexR * (0.65f + 0.55f * ((i % 3) * 0.45f));
            float sx = px + Angles.trnsx(a, orbit) - ax * ((i % 3) * 8f);
            float sy = py + Angles.trnsy(a, orbit) - ay * ((i % 3) * 8f);
            Draw.color(i % 3 == 0 ? COL_NODE : (i % 2 == 0 ? SPEC_CORE : SPEC_MAIN));
            Draw.alpha(0.85f * fade);
            Fill.circle(sx, sy, (1.1f + 0.6f * (i % 2)) * fade);
        }
    }

    static void drawTiltedRing(
        float cx, float cy, float ax, float ay, float hx, float hy,
        float rx, float ry, float startDeg, float sweepDeg, int segs
    ) {
        for (int k = 0; k < segs; k++) {
            float a1 = startDeg + sweepDeg * (k / (float) segs);
            float a2 = startDeg + sweepDeg * ((k + 1) / (float) segs);
            float x1 = cx + ax * (rx * Mathf.cosDeg(a1)) + hx * (ry * Mathf.sinDeg(a1));
            float y1 = cy + ay * (rx * Mathf.cosDeg(a1)) + hy * (ry * Mathf.sinDeg(a1));
            float x2 = cx + ax * (rx * Mathf.cosDeg(a2)) + hx * (ry * Mathf.sinDeg(a2));
            float y2 = cy + ay * (rx * Mathf.cosDeg(a2)) + hy * (ry * Mathf.sinDeg(a2));
            Lines.line(x1, y1, x2, y2);
        }
    }

    static void drawCrescentBlade(float cx, float cy, float ang, float R, float W, float halfDeg, Color cOuter, Color cCore, float alpha) {
        int N = 16;
        for (int k = 0; k < N; k++) {
            float f1 = k / (float) N, f2 = (k + 1) / (float) N;
            float a1 = ang + Mathf.lerp(-halfDeg, halfDeg, f1);
            float a2 = ang + Mathf.lerp(-halfDeg, halfDeg, f2);
            float w1 = W * Mathf.sinDeg(f1 * 180f);
            float w2 = W * Mathf.sinDeg(f2 * 180f);
            float midFactor = Mathf.sinDeg(((f1 + f2) * 0.5f) * 180f);
            Draw.color(cOuter, cCore, midFactor);
            Draw.alpha(alpha * midFactor);
            Fill.quad(
                cx + Angles.trnsx(a1, R), cy + Angles.trnsy(a1, R),
                cx + Angles.trnsx(a2, R), cy + Angles.trnsy(a2, R),
                cx + Angles.trnsx(a2, R - w2), cy + Angles.trnsy(a2, R - w2),
                cx + Angles.trnsx(a1, R - w1), cy + Angles.trnsy(a1, R - w1));
        }
    }

    /** Bong thanh kiem: y het thanh kiem, phong to dan va mo dan. */
    static void drawGhosts() {
        Honivery.HoniverySwordDrawer sw = sword();
        if (sw == null) return;
        for (Ghost g : ghosts) {
            Unit u = Groups.unit.getByID(g.uid);
            if (u == null || u.dead) continue;
            float p = g.t / g.life;
            float scale = 1f + (g.big ? 2.4f : 1.6f) * Interp.pow2Out.apply(p);
            float alpha = 0.7f * (1f - p) * (1f - p);
            sw.drawGhost(u, scale, g.col, alpha);
        }
    }

    static void draw() {
        if (Vars.state == null || !Vars.state.isGame() || (slashes.isEmpty() && specFxs.isEmpty() && ghosts.isEmpty())) return;
        float z = Draw.z();
        Draw.z(Layer.effect + 1f);
        Draw.blend(Blending.additive);
        drawGhosts();
        for (Slash s : slashes) {
            if (s.big) {
                drawZone(s);
                drawArcLayer(s, COL_OUTER, COL_CYAN, 1.03f, 1.12f, 0f, 0f, 0.65f);
                drawArcLayer(s, COL_MID, COL_CORE, 0.94f, 0.85f, 0.02f, 0.03f, 0.90f);
                drawArcLayer(s, COL_NODE, COL_CORE, 0.84f, 0.45f, 0f, -0.04f, 0.95f);
            } else {
                drawArc3d(s);
            }
        }
        for (SpecialFx sf : specFxs) {
            drawSpecial(sf);
        }
        Draw.blend();
        Draw.reset();
        Draw.z(z);
    }

    // ---------------- UI ----------------
    static void clamp(Table t) {
        t.setPosition(Mathf.clamp(t.x, 0, Core.graphics.getWidth() - t.getWidth()), Mathf.clamp(t.y, 0, Core.graphics.getHeight() - t.getHeight()));
    }

    static void build() {
        if (btn != null || Vars.ui == null || Vars.ui.hudGroup == null) return;
        UnitType h = Honivery.honivery;
        Table t = new Table(Styles.black6);
        btn = t;
        t.name = "newex-honivery-skill";
        t.margin(6);
        t.touchable = Touchable.enabled;
        float sz = Vars.mobile ? 56 : 48;
        if (h != null) t.image(h.uiIcon).size(sz).row();
        Label lbl = t.add("").get();
        lbl.setFontScale(0.9f);
        t.update(() -> {
            float left = cdEnd - Time.time;
            if (left > 0) {
                lbl.setText(Strings.fixed(Mathf.ceil(left / 6f) / 10f, 1) + "s");
                lbl.setColor(Color.gray);
            } else {
                lbl.setText("SKILL");
                lbl.setColor(COL);
            }
        });
        t.visible(() -> isActive() && Vars.ui.hudfrag.shown && Vars.player != null && isHon(Vars.player.unit()) && !Vars.player.unit().dead);
        t.pack();
        if (Core.settings.has(KEY + "-x") && Core.settings.has(KEY + "-y")) t.setPosition(Core.settings.getInt(KEY + "-x"), Core.settings.getInt(KEY + "-y"));
        else t.setPosition(Core.graphics.getWidth() - t.getWidth() - 20, Core.graphics.getHeight() / 3f + 90);
        clamp(t);
        t.addListener(new InputListener() {
            boolean dragging = false;
            float offX = 0, offY = 0;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                dragging = false;
                offX = x;
                offY = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - offX) > 5 || Math.abs(y - offY) > 5) dragging = true;
                if (dragging) {
                    t.setPosition(t.x + x - offX, t.y + y - offY);
                    clamp(t);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button) {
                if (dragging) {
                    Core.settings.put(KEY + "-x", Math.round(t.x));
                    Core.settings.put(KEY + "-y", Math.round(t.y));
                } else if (!Vars.state.isPaused() && Time.time >= cdEnd) {
                    Unit u = Vars.player.unit();
                    if (isHon(u) && !u.dead) {
                        cdEnd = Time.time + CD_TOTAL;
                        NexNet.send(REQ, "");
                    }
                }
                dragging = false;
            }
        });
        Vars.ui.hudGroup.addChild(t);
    }

    public static void init() {
        Events.run(Trigger.update, HoniveryPvp::update);
        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> {
                try {
                    build();
                } catch (Throwable err) {
                    Log.err("[HoniveryPvp] ui: " + err);
                }
            });
            Events.run(Trigger.draw, HoniveryPvp::draw);
            Events.on(WorldLoadEvent.class, e -> {
                cdEnd = 0;
                cdEndSrv.clear();
                combos.clear();
                specProjs.clear();
                slashes.clear();
                specFxs.clear();
                ghosts.clear();
            });
        } else {
            Events.on(WorldLoadEvent.class, e -> {
                cdEndSrv.clear();
                combos.clear();
                specProjs.clear();
            });
        }
    }
}
