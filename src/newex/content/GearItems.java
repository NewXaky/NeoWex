package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.ui.Styles;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Hệ thống VẬT PHẨM TRANG BỊ của chế độ CHIẾN TRANH (NewMode PvP khi Hỗn loạn tắt).
 *
 * - Quái đợt bị người chơi hạ gục có xác suất rơi vật phẩm theo độ hiếm (càng hiếm càng khó rơi) vào Túi đồ của người đó.
 * - 6 ô trang bị: Vũ khí, Mũ, Giáp, Giày, Nhẫn, Bùa. Chạm ô -> mở Túi đồ -> bấm Lắp.
 * - Mỗi vật phẩm cộng/trừ cấp cho các chỉ số của trang chỉ số cũ (Máu, Giáp, Tấn công...). Trang chỉ số chỉ còn khi bật Hỗn loạn.
 * - Icon lấy từ khối / vật phẩm của game và của mod (thử tên của mod trước, không có thì dùng khối gốc).
 */
final class GearItems {
    private GearItems() {}

    static final String REQ_GEAR = "newex-pvp-gear";
    static final int SLOTS = 6, INV_CAP = 80;
    static final int S_WEAPON = 0, S_HELM = 1, S_ARMOR = 2, S_BOOTS = 3, S_RING = 4, S_CHARM = 5;
    static final int R_COMMON = 0, R_RARE = 1, R_EPIC = 2, R_LEGEND = 3, R_MYTHIC = 4, RARITIES = 5;

    static final String[] SLOT_VI = {"Vũ khí", "Mũ", "Giáp", "Giày", "Nhẫn", "Bùa"};
    static final String[] SLOT_EN = {"Weapon", "Helm", "Armor", "Boots", "Ring", "Charm"};
    static final String[] SLOT_ICON = {"duo", "mender", "thorium-wall", "conveyor", "silicon", "phase-fabric"};
    static final String[] RAR_VI = {"Thường", "Hiếm", "Sử thi", "Huyền thoại", "Thần thoại"};
    static final String[] RAR_EN = {"Common", "Rare", "Epic", "Legendary", "Mythic"};
    static final String[] RAR_HEX = {"b8bcc4", "4da6ff", "c084fc", "ffb347", "ff5a5a"};
    /** Trọng số rơi theo độ hiếm: càng hiếm càng thấp (tổng 1000 -> 60% / 25% / 10% / 4% / 1%). */
    static final int[] RAR_W = {600, 250, 100, 40, 10};
    /** Sàn cấp hiệu dụng của từng chỉ số (cho phép trừ ở Máu/Giáp/Tấn công/Tốc độ/Tốc độ bắn, tránh chỉ số âm vô nghĩa). */
    static final int[] FLOOR = {-4, -6, -10, -8, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    static final class Def {
        final int id, slot, rarity; final String vi, en; final String[] icons; final int[] mods; // mods: cặp {chỉ số, cấp}
        TextureRegion reg;
        Def(int id, int slot, int rarity, String vi, String en, String icons, int... mods) {
            this.id = id; this.slot = slot; this.rarity = rarity; this.vi = vi; this.en = en; this.icons = icons.split(","); this.mods = mods;
        }
    }

    // chỉ số: 0 Máu, 1 Giáp, 2 Tấn công, 3 Tốc độ di chuyển, 4 Tốc độ bắn, 5 Tỉ lệ bạo kích, 6 ST bạo kích, 7 Xuyên giáp,
    // 8 Hồi máu, 9 ST % máu tối đa, 10 ST theo giáp, 11 Đẩy lùi, 12 Tầm bắn lính, 13 ST quái spawn ngẫu nhiên
    static final Def[] DEFS = new Def[42];
    static int n = 0;
    static void d(int slot, int rarity, String vi, String en, String icons, int... mods) { DEFS[n] = new Def(n, slot, rarity, vi, en, icons, mods); n++; }
    static {
        // ---- Vũ khí
        d(S_WEAPON, 0, "Súng đồng", "Copper Gun", "duo", 2, 3, 3, -1);
        d(S_WEAPON, 0, "Nỏ gỉ sét", "Rusty Crossbow", "hail", 4, 3, 5, 1);
        d(S_WEAPON, 1, "Pháo tia lửa", "Spark Cannon", "arc,lancer", 2, 5, 4, 3);
        d(S_WEAPON, 1, "Kiếm laser", "Laser Sword", "swordoder,lancer", 2, 4, 6, 3);
        d(S_WEAPON, 2, "Búa Therdum", "Therdum Hammer", "therdum,salvo", 2, 7, 9, 4, 3, -2);
        d(S_WEAPON, 3, "Pháo Xylaon", "Xylaon Cannon", "xylaon,ripple", 2, 10, 7, 6, 13, 6, 3, -3);
        d(S_WEAPON, 4, "Thần kiếm Heliyron", "Heliyron Godblade", "heliyron,spectre", 2, 16, 4, 8, 6, 8, 9, 6, 3, -4, 1, -3);
        // ---- Mũ
        d(S_HELM, 0, "Mũ đồng", "Copper Cap", "copper-wall,mender", 0, 2, 7, 1);
        d(S_HELM, 0, "Kính ngắm vụn", "Scrap Scope", "scrap-wall,scrap", 5, 3, 12, 1);
        d(S_HELM, 1, "Mũ chiến binh", "Warrior Helm", "titanium-wall,mender", 0, 3, 7, 3, 8, 1);
        d(S_HELM, 1, "Kính xạ thủ", "Marksman Visor", "lancer,mender", 5, 4, 7, 4);
        d(S_HELM, 2, "Mũ Korynd", "Korynd Helm", "korynd,thorium-wall", 0, 5, 1, 3, 12, 4, 3, -2);
        d(S_HELM, 3, "Vương miện Sallowyr", "Sallowyr Crown", "sallowyr,force-projector", 7, 8, 8, 5, 5, 4, 4, -3);
        d(S_HELM, 4, "Mão Hư Vô", "Void Diadem", "newex,surge-wall", 7, 12, 5, 8, 12, 6, 0, -3, 4, -3);
        // ---- Giáp
        d(S_ARMOR, 0, "Áo vải", "Cloth Vest", "copper-wall", 0, 2, 1, 1, 3, -1);
        d(S_ARMOR, 0, "Giáp da", "Leather Armor", "scrap-wall-large,copper-wall-large", 1, 3, 0, 1);
        d(S_ARMOR, 1, "Giáp titan", "Titan Plate", "titanium-wall-large,titanium-wall", 1, 4, 0, 3, 3, -1);
        d(S_ARMOR, 1, "Áo giáp phản lực", "Reactive Vest", "plastanium-wall,thorium-wall", 1, 3, 11, 4, 4, -1);
        d(S_ARMOR, 2, "Giáp Thorium", "Thorium Mail", "thorium-wall-large", 0, 6, 1, 6, 10, 3, 3, -3);
        d(S_ARMOR, 3, "Giáp Nethyr", "Nethyr Plate", "nethyr,plastanium-wall-large", 0, 8, 1, 9, 8, 4, 11, 4, 3, -4);
        d(S_ARMOR, 4, "Chiến giáp Raykstone", "Raykstone Battle Armor", "raykstone,surge-wall-large", 0, 13, 1, 12, 10, 8, 8, 5, 3, -4, 2, -3);
        // ---- Giày
        d(S_BOOTS, 0, "Giày vải", "Cloth Boots", "conveyor", 3, 3, 0, -1);
        d(S_BOOTS, 0, "Giày băng chuyền", "Conveyor Boots", "titanium-conveyor,conveyor", 3, 2, 4, 1);
        d(S_BOOTS, 1, "Giày nhẹ", "Light Boots", "junction,router", 3, 5, 4, 2);
        d(S_BOOTS, 1, "Giày thúc đẩy", "Thruster Boots", "overflow-gate,titanium-conveyor", 3, 4, 11, 3, 1, -1);
        d(S_BOOTS, 2, "Giày Zoj", "Zoj Boots", "zoj,plastanium-conveyor", 3, 8, 4, 5, 11, 2, 1, -2);
        d(S_BOOTS, 3, "Giày Plasanod", "Plasanod Boots", "plasanod,armored-conveyor", 3, 11, 4, 8, 8, 3, 0, -3, 1, -2);
        d(S_BOOTS, 4, "Giày Ánh Sáng", "Lightstep Boots", "starlight,phase-conveyor", 3, 16, 4, 12, 8, 5, 11, 3, 2, -4, 0, -4);
        // ---- Nhẫn
        d(S_RING, 0, "Nhẫn đồng", "Copper Ring", "copper", 6, 2, 8, 1);
        d(S_RING, 0, "Nhẫn chì", "Lead Ring", "lead", 9, 1, 8, 2);
        d(S_RING, 1, "Nhẫn bạc", "Silver Ring", "silicon", 6, 4, 9, 2);
        d(S_RING, 1, "Nhẫn titan", "Titan Ring", "titanium", 8, 4, 2, 2, 3, -1);
        d(S_RING, 2, "Nhẫn Uranilum", "Uranilum Ring", "uranilum,thorium", 6, 7, 9, 3, 8, 3, 2, -2);
        d(S_RING, 3, "Nhẫn Obsidis", "Obsidis Ring", "obsidis,surge-alloy", 6, 10, 9, 6, 8, 6, 4, -3);
        d(S_RING, 4, "Nhẫn Starlight", "Starlight Ring", "starlight,phase-fabric", 6, 16, 9, 9, 5, 8, 8, 8, 2, -3, 0, -3);
        // ---- Bùa
        d(S_CHARM, 0, "Bùa bào tử", "Spore Charm", "spore-pod", 8, 2, 12, 2, 1, -1);
        d(S_CHARM, 0, "Bùa than", "Coal Charm", "coal", 13, 3, 11, 1);
        d(S_CHARM, 1, "Bùa plastanium", "Plastanium Charm", "plastanium", 12, 4, 13, 3, 3, -1);
        d(S_CHARM, 1, "Bùa kích thích", "Stimulant Charm", "pyratite,blast-compound", 4, 4, 13, 4);
        d(S_CHARM, 2, "Bùa Berrybyr", "Berrybyr Charm", "berrybyr,phase-fabric", 13, 8, 12, 4, 8, 3, 0, -2);
        d(S_CHARM, 3, "Bùa Sallowyr", "Sallowyr Charm", "sallowyr,surge-alloy", 13, 12, 12, 6, 9, 5, 2, -3, 3, -3);
        d(S_CHARM, 4, "Bùa Pha Lê Hư Vô", "Void Crystal Charm", "newex-obsidis,newex-starlight", 13, 18, 12, 10, 9, 8, 7, 8, 1, -4, 3, -4);
    }

    // ------------------------------------------------------------------ truy cập chung
    static String tr(String vi, String en) { return NewModePvp.tr(vi, en); }
    /** Chế độ CHIẾN TRANH: trận PvP đang chạy và Hỗn loạn tắt. */
    static boolean war() {
        try { return NewModePvp.isActive() && !ChaosMode.on(); } catch (Throwable ignored) { return false; }
    }
    static String col(int r) { return "[#" + RAR_HEX[r] + "]"; }
    static Color colorOf(int r) { return Color.valueOf(RAR_HEX[Math.max(0, Math.min(RARITIES - 1, r))]); }
    static String name(Def d) { return tr(d.vi, d.en); }
    static String slotName(int s) { return tr(SLOT_VI[s], SLOT_EN[s]); }
    static String rarName(int r) { return tr(RAR_VI[r], RAR_EN[r]); }
    static Def def(int id) { return id >= 0 && id < n ? DEFS[id] : null; }

    // ------------------------------------------------------------------ icon
    static UnlockableContent find(String nm) {
        ContentType[] ts = {ContentType.block, ContentType.item, ContentType.unit};
        String[] names = {nm, "newex-" + nm};
        for (String s : names) for (ContentType t : ts) {
            try {
                Content c = Vars.content.getByName(t, s);
                if (c instanceof UnlockableContent) {
                    UnlockableContent u = (UnlockableContent) c;
                    if (u.uiIcon != null && Core.atlas.isFound(u.uiIcon)) return u;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
    static TextureRegion icon(Def d) {
        if (d == null) return Core.atlas.find("error");
        if (d.reg != null) return d.reg;
        for (String s : d.icons) {
            UnlockableContent u = find(s.trim());
            if (u != null) { d.reg = u.uiIcon; return d.reg; }
        }
        UnlockableContent u = find(SLOT_ICON[d.slot]);
        if (u == null) u = find("copper");
        TextureRegion r = u != null ? u.uiIcon : Core.atlas.find("error");
        d.reg = r;
        return r;
    }
    static TextureRegion slotIcon(int s) {
        UnlockableContent u = find(SLOT_ICON[s]);
        return u != null ? u.uiIcon : Core.atlas.find("error");
    }

    // ------------------------------------------------------------------ chỉ số cộng thêm
    static void refreshDelta(NewModePvp.St st) {
        int key = Arrays.hashCode(st.gEq);
        if (key == st.gKey) return;
        st.gKey = key;
        Arrays.fill(st.gDelta, 0);
        boolean any = false;
        for (int s = 0; s < SLOTS; s++) {
            Def d = def(st.gEq[s]);
            if (d == null) continue;
            for (int i = 0; i + 1 < d.mods.length; i += 2) {
                if (d.mods[i] >= 0 && d.mods[i] < st.gDelta.length) { st.gDelta[d.mods[i]] += d.mods[i + 1]; any = true; }
            }
        }
        st.gAny = any;
    }
    /** Mảng cấp cộng thêm từ vật phẩm; null nếu không ở chế độ chiến tranh hoặc chưa lắp gì. */
    static int[] delta(NewModePvp.St st) {
        if (st == null || !war()) return null;
        refreshDelta(st);
        return st.gAny ? st.gDelta : null;
    }
    /** Mảng chỉ số (st.s + vật phẩm) cho những chỗ đọc chỉ số thô; không cấp phát. */
    static int[] eff(NewModePvp.St st) {
        if (st == null) return null;
        int[] g = delta(st);
        if (g == null) return st.s;
        int[] out = st.gEff;
        for (int i = 0; i < out.length; i++) {
            int v = (i < st.s.length ? st.s[i] : 0) + (i < g.length ? g[i] : 0);
            out[i] = Math.max(FLOOR[Math.min(i, FLOOR.length - 1)], v);
        }
        return out;
    }
    static int floorOf(int i) { return FLOOR[Math.max(0, Math.min(FLOOR.length - 1, i))]; }

    // ------------------------------------------------------------------ lưu / đồng bộ
    static String enc(NewModePvp.St st) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < st.gInv.size(); i++) { if (i > 0) sb.append(','); sb.append(st.gInv.get(i)); }
        sb.append(';');
        for (int i = 0; i < SLOTS; i++) { if (i > 0) sb.append(','); sb.append(st.gEq[i]); }
        return sb.toString();
    }
    static void dec(NewModePvp.St st, String s) {
        st.gInv.clear(); Arrays.fill(st.gEq, -1); st.gKey = Integer.MIN_VALUE;
        if (s == null || s.isEmpty()) return;
        try {
            String[] p = s.split(";", -1);
            if (p.length > 0 && !p[0].isEmpty()) for (String c : p[0].split(",")) { int id = Integer.parseInt(c.trim()); if (def(id) != null) st.gInv.add(id); }
            if (p.length > 1 && !p[1].isEmpty()) {
                String[] q = p[1].split(",");
                for (int i = 0; i < SLOTS && i < q.length; i++) { int id = Integer.parseInt(q[i].trim()); st.gEq[i] = (def(id) != null && def(id).slot == i) ? id : -1; }
            }
        } catch (Throwable ignored) {}
    }
    static String sig(NewModePvp.St st) { return st == null ? "" : st.gInv + "|" + Arrays.toString(st.gEq); }

    // ------------------------------------------------------------------ rơi vật phẩm
    static Player playerById(int pid) {
        final Player[] out = {null};
        try { NewModePvp.eachPl(p -> { if (p != null && p.id == pid) out[0] = p; }); } catch (Throwable ignored) {}
        return out[0];
    }
    /** Xác suất rơi theo máu tối đa của quái: yếu ~6%, càng trâu càng cao, tối đa 60%. */
    static float dropChance(Unit u) { return Math.min(0.6f, 0.06f + u.maxHealth / 4000f * 0.12f); }
    static int rollRarity(float hp) {
        float boost = Math.min(3f, hp / 4000f);            // quái trâu đẩy trọng số lên các bậc hiếm
        float[] w = new float[RARITIES];
        float tot = 0;
        for (int i = 0; i < RARITIES; i++) { w[i] = RAR_W[i] * (1f + boost * i * 0.4f); tot += w[i]; }
        float r = (float) Math.random() * tot;
        for (int i = 0; i < RARITIES; i++) { r -= w[i]; if (r <= 0) return i; }
        return 0;
    }
    static Def rollDef(int rarity) {
        ArrayList<Def> pool = new ArrayList<>();
        for (int i = 0; i < n; i++) if (DEFS[i].rarity == rarity) pool.add(DEFS[i]);
        return pool.isEmpty() ? DEFS[0] : pool.get((int) (Math.random() * pool.size()));
    }
    /** Máy chủ gọi khi quái đợt chết: nếu người chơi pid là người hạ gục thì có thể nhận 1 vật phẩm. */
    static void onWaveKill(Unit u, int pid) {
        if (Vars.net.client() || u == null || !NewModePvp.isFull() || !war() || Vars.state.gameOver) return;
        NewModePvp.St st = NewModePvp.byId.get(pid);
        if (st == null) return;
        if (Math.random() >= dropChance(u)) return;
        if (st.gInv.size() >= INV_CAP) return;
        Def d = rollDef(rollRarity(u.maxHealth));
        st.gInv.add(d.id);
        Player pl = playerById(pid);
        if (pl != null) {
            if (NewModePvp.isBotPl(pl)) botAutoEquip(st);
            else NewModePvp.toast(pl, tr("[accent]Nhận vật phẩm: ", "[accent]Item received: ") + col(d.rarity) + name(d) + " [" + rarName(d.rarity) + "][]" + tr(" - xem trong Túi đồ", " - see your Bag"));
        }
        NewModePvp.changed();
    }
    static int power(Def d) { int p = 0; for (int i = 0; i + 1 < d.mods.length; i += 2) p += d.mods[i + 1]; return p + d.rarity * 3; }
    /** Bot tự lắp vật phẩm tốt hơn vào ô tương ứng. */
    static void botAutoEquip(NewModePvp.St st) {
        for (int k = st.gInv.size() - 1; k >= 0; k--) {
            Def d = def(st.gInv.get(k));
            if (d == null) continue;
            Def cur = def(st.gEq[d.slot]);
            if (cur == null || power(d) > power(cur)) {
                st.gInv.remove(k);
                if (cur != null) st.gInv.add(cur.id);
                st.gEq[d.slot] = d.id;
            }
        }
    }

    // ------------------------------------------------------------------ yêu cầu từ client
    static void define() {
        NewModePvp.onReq(REQ_GEAR, 120, (player, payload) -> {
            if (!war()) return;
            NewModePvp.St st = NewModePvp.reqState(player);
            if (st == null) return;
            String[] a = payload.split("\\|");
            if (a.length < 2) return;
            Integer v = NewModePvp.leadInt(a[1]);
            if (v == null) return;
            String op = a[0];
            if (op.equals("e")) {                         // lắp vật phẩm id=v từ túi
                int idx = st.gInv.indexOf(v);
                Def d = def(v);
                if (idx < 0 || d == null) return;
                st.gInv.remove(idx);
                int old = st.gEq[d.slot];
                if (old >= 0) st.gInv.add(old);
                st.gEq[d.slot] = d.id;
                NewModePvp.toast(player, tr("[accent]Đã lắp ", "[accent]Equipped ") + col(d.rarity) + name(d) + "[]");
            } else if (op.equals("u")) {                  // tháo ô v
                if (v < 0 || v >= SLOTS || st.gEq[v] < 0) return;
                if (st.gInv.size() >= INV_CAP) return;
                st.gInv.add(st.gEq[v]);
                st.gEq[v] = -1;
            } else if (op.equals("d")) {                  // bỏ vật phẩm id=v trong túi
                int idx = st.gInv.indexOf(v);
                if (idx < 0) return;
                st.gInv.remove(idx);
            } else return;
            NewModePvp.changed();
        });
    }

    // ------------------------------------------------------------------ giao diện
    static int filterSlot = -1, pendingFilter = -1;
    /** Chạm vào một ô trang bị: mở Túi đồ, danh sách vật phẩm của ô đó lên đầu. */
    static void openBagFor(int slot) { pendingFilter = slot; NewModePvp.openBag(); }

    /** Nền trắng nhuộm màu (Tex.whiteui khai báo kiểu Drawable nên phải ép sang TextureRegionDrawable mới có tint). */
    static arc.scene.style.Drawable tinted(Color c) {
        return ((arc.scene.style.TextureRegionDrawable) Tex.whiteui).tint(c);
    }

    static String statText(int[] mods) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < mods.length; i += 2) {
            int v = mods[i + 1];
            if (i > 0) sb.append("  ");
            sb.append(v >= 0 ? "[#7cfc00]+" : "[scarlet]").append(v).append(' ').append(tr(NewModePvp.STATS[mods[i]].vi, NewModePvp.STATS[mods[i]].en)).append("[]");
        }
        return sb.toString();
    }

    /** Thẻ vật phẩm: icon + tên + độ hiếm + chỉ số; tối đa 2 nút. */
    static Table itemRow(Def d, float W, boolean narrow, String b1, Runnable r1, String b2, Runnable r2, boolean glow) {
        Table border = new Table(tinted(colorOf(d.rarity).cpy().a(glow ? 0.95f : 0.55f)));
        border.margin(2f);
        Table row = new Table(Styles.black8);
        row.margin(8);
        float iconSz = narrow ? 36 : 44;
        row.add(new Image(icon(d))).size(iconSz).padRight(10);
        row.add(col(d.rarity) + name(d) + "[]  [lightgray]" + slotName(d.slot) + " · " + rarName(d.rarity) + "[]\n" + statText(d.mods)).left().growX().wrap().minWidth(0);
        Table bt = row;
        if (narrow) { row.row(); bt = new Table(); }
        float bw = narrow ? 64 : 72;
        if (b1 != null) bt.button(b1, r1).size(bw, NewModePvp.btnH()).padLeft(6);
        if (b2 != null) bt.button(b2, r2).size(bw, NewModePvp.btnH()).padLeft(4);
        if (narrow) row.add(bt).colspan(2).growX().padTop(6).row();
        border.add(row).growX();
        return border;
    }

    /** Một ô trang bị trên trang chính. */
    static Table slotTile(NewModePvp.St st, final int slot, float w) {
        Def d = def(st.gEq[slot]);
        Color c = d != null ? colorOf(d.rarity) : Color.valueOf("5b6070");
        Table border = new Table(tinted(c.cpy().a(d != null ? 0.95f : 0.5f)));
        border.margin(2f);
        Table in = new Table(Styles.black8);
        in.margin(6);
        in.add("[lightgray]" + slotName(slot) + "[]").top().row();
        if (d != null) {
            in.add(new Image(icon(d))).size(52).pad(4).row();
            in.add(col(d.rarity) + name(d) + "[]").growX().wrap().minWidth(0).labelAlign(arc.util.Align.center).row();
            in.add("[lightgray]" + rarName(d.rarity) + "[]").row();
        } else {
            Image im = new Image(slotIcon(slot));
            im.setColor(1f, 1f, 1f, 0.28f);
            in.add(im).size(52).pad(4).row();
            in.add("[gray]" + tr("Trống - chạm để lắp", "Empty - tap to equip") + "[]").growX().wrap().minWidth(0).labelAlign(arc.util.Align.center).row();
        }
        border.add(in).grow();
        border.touchable = Touchable.enabled;
        border.clicked(() -> openBagFor(slot));
        return border;
    }

    /** Nội dung tab "TRANG BỊ" (thay cho tab CHỈ SỐ khi Hỗn loạn tắt). */
    static void buildTab(Table c, NewModePvp.St st, float W) {
        Table hd = new Table(Styles.black6);
        hd.margin(8);
        hd.add("[accent]" + tr("Trang bị vật phẩm", "Item equipment") + "[]").left().row();
        hd.add("[lightgray]" + tr("Hạ quái đợt để nhận vật phẩm rơi vào Túi đồ (càng hiếm càng khó rơi). Chạm một ô để mở Túi đồ rồi bấm Lắp. Vật phẩm cộng/trừ cấp cho các chỉ số.",
            "Kill wave monsters to get item drops in your Bag (rarer = lower chance). Tap a slot to open the Bag then press Equip. Items add/subtract stat levels.") + "[]").left().growX().wrap().minWidth(0).row();
        c.add(hd).width(W).pad(3).row();

        int cols = W < 440f ? 2 : 3;
        float cw = Math.max(110f, (W - 8f * cols - 8f) / cols);
        Table grid = new Table();
        for (int s = 0; s < SLOTS; s++) {
            grid.add(slotTile(st, s, cw)).width(cw).minHeight(cw * 0.85f).pad(4);
            if ((s + 1) % cols == 0) grid.row();
        }
        c.add(grid).width(W).row();

        Table tot = new Table(Styles.black6);
        tot.margin(8);
        tot.add("[accent]" + tr("Chỉ số cộng thêm từ vật phẩm", "Bonus stats from items") + "[]").left().row();
        int[] g = delta(st);
        boolean any = false;
        if (g != null) for (int i = 0; i < g.length; i++) {
            if (g[i] == 0) continue;
            any = true;
            int base = st.s != null && i < st.s.length ? st.s[i] : 0;
            int lv = Math.max(floorOf(i), base + g[i]);
            tot.add("[white]" + tr(NewModePvp.STATS[i].vi, NewModePvp.STATS[i].en) + "[]  " + (g[i] > 0 ? "[#7cfc00]+" : "[scarlet]") + g[i] + "[]  [lightgray]→ " + NewModePvp.STATS[i].f.get(lv) + "[]")
                .left().growX().wrap().minWidth(0).padTop(3).row();
        }
        if (!any) tot.add("[lightgray]" + tr("Chưa lắp vật phẩm nào.", "No items equipped yet.") + "[]").left().row();
        c.add(tot).width(W).pad(3).row();
    }

    /** Mục "Vật phẩm" ở đầu trang Túi đồ. */
    static void bagSection(Table c, NewModePvp.St st, float W, boolean narrow) {
        Table hd = new Table(Styles.black6);
        hd.margin(8);
        hd.add("[accent]" + tr("Vật phẩm", "Items") + "[]").left().expandX();
        hd.add("[lightgray]" + st.gInv.size() + "/" + INV_CAP + "[]").right();
        c.add(hd).width(W).pad(3).row();

        final int fs = filterSlot;
        if (fs >= 0) {
            c.add("[lightgray]" + tr("Đang chọn ô: ", "Selected slot: ") + "[white]" + slotName(fs) + "[]").left().padLeft(6).padTop(2).row();
        }
        // đang lắp
        boolean anyEq = false;
        for (int s = 0; s < SLOTS; s++) {
            final int slot = s;
            Def d = def(st.gEq[s]);
            if (d == null || (fs >= 0 && fs != s)) continue;
            if (!anyEq) { anyEq = true; c.add("[lightgray]" + tr("Đang lắp", "Equipped") + "[]").left().padLeft(6).padTop(4).row(); }
            c.add(itemRow(d, W, narrow, tr("Tháo", "Remove"), () -> NewModePvp.send(REQ_GEAR, "u|" + slot), null, null, true)).width(W).pad(3).row();
        }
        // trong túi: vật phẩm của ô đang chọn lên đầu, rồi theo độ hiếm
        ArrayList<Def> inv = new ArrayList<>();
        for (int id : st.gInv) { Def d = def(id); if (d != null) inv.add(d); }
        inv.sort((x, y) -> {
            int fx = x.slot == fs ? 0 : 1, fy = y.slot == fs ? 0 : 1;
            if (fx != fy) return fx - fy;
            if (x.rarity != y.rarity) return y.rarity - x.rarity;
            return x.id - y.id;
        });
        if (inv.isEmpty()) {
            c.add("[lightgray]" + tr("Chưa có vật phẩm nào trong túi. Hãy hạ quái đợt!", "No items in your bag yet. Kill wave monsters!") + "[]").left().padLeft(6).padTop(4).width(W - 12).wrap().row();
            return;
        }
        c.add("[lightgray]" + tr("Trong túi", "In bag") + "[]").left().padLeft(6).padTop(4).row();
        for (final Def d : inv) {
            boolean match = fs < 0 || d.slot == fs;
            Table r = itemRow(d, W, narrow, tr("Lắp", "Equip"), () -> NewModePvp.send(REQ_GEAR, "e|" + d.id), tr("Bỏ", "Drop"), () -> NewModePvp.send(REQ_GEAR, "d|" + d.id), match && fs >= 0);
            c.add(r).width(W).pad(3).row();
        }
    }
}