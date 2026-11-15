package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.IntMap;
import arc.struct.IntSet;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ai.types.FlyingAI;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ drone-launcher.js. Các bảng dùng chung với cargo-drone.js nằm ở đây (public static). */
public final class DroneLauncher {
    /** id unit -> id tháp pháo đã sinh ra nó (JS: global.droneLauncherMap). */
    public static final IntMap<Integer> launcherMap = new IntMap<>();
    /** unit của nhánh MK2B (JS: global.mk2bUnits). */
    public static final IntSet mk2bUnits = new IntSet();
    /** unit đang bị thu hồi (JS: global.droneRecallMap). */
    public static final IntSet recallSet = new IntSet();

    private static final int[] MK2_COST = {2000, 1500, 1000};   // đồng, chì, silicon
    private static final int[] MK2B_COST = {2000, 1500, 1000};  // đồng, chì, titan
    private static final int[] TIER_HP = {3000, 4500, 6000};

    private DroneLauncher() {}

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }

    private static String t(boolean isEn, String e, String vi) { return isEn ? e : vi; }

    private static String col(int have, int need) { return have >= need ? "[green]" : "[red]"; }

    private static void wrap(Cell<Label> c, float w) {
        c.width(w).get().setWrap(true);
        c.get().setAlignment(Align.left);
    }

    private static UnitType unitType(String... names) {
        for (String n : names) {
            UnitType u = Vars.content.getByName(ContentType.unit, n);
            if (u != null) return u;
        }
        return null;
    }

    public static void init() {
        NexNet.on("drone-recall", (player, payload) -> {
            int pos;
            try { pos = Integer.parseInt(payload.trim()); } catch (Exception ex) { return; }
            Building b = Vars.world.build(pos);
            if (!(b instanceof DroneLauncherBuild)) return;
            if (player != null && b.team != player.team()) return;
            ((DroneLauncherBuild) b).recallAllDrones();
        }, false, 500);

        Events.on(ContentInitEvent.class, e -> {
            Block blk = Vars.content.getByName(ContentType.block, "newex-drone-launcher");
            if (blk == null) blk = Vars.content.getByName(ContentType.block, "drone-launcher");
            if (!(blk instanceof PowerTurret)) return;
            PowerTurret turret = (PowerTurret) blk;
            turret.configurable = true;
            turret.config(Integer.class, (Building tile, Integer value) -> {
                if (tile instanceof DroneLauncherBuild) ((DroneLauncherBuild) tile).setTier(value);
            });
            turret.buildType = () -> new DroneLauncherBuild(turret);
        });
    }

    public static class DroneLauncherBuild extends PowerTurret.PowerTurretBuild {
        float spawnTimer = 0f, spawnInterval = 120f;
        public int tierState = 0;
        float doorTimer = 0f, maxDoorTime = 60f;
        float spawnDelayTimer = 0f;
        boolean isSpawning = false;
        float maxHp = 0f;
        float cdT = -1f, cmT = -1f;
        int cdV = 0, cmV = 0;

        public DroneLauncherBuild(PowerTurret outer) {
            outer.super();
        }

        public int getTier() { return tierState; }

        public void setTier(int val) {
            tierState = val;
            if (val >= 0 && val < TIER_HP.length) health = TIER_HP[val];
            maxHp = health;
        }

        @Override
        public float maxHealth() { return maxHp > 0f ? maxHp : super.maxHealth(); }

        public int getMaxDrones() { return getTier() == 1 ? 6 : 4; }

        /** MK2B: tầm bắn +120% (JS gốc có getRange() nhưng không gọi tới). */
        @Override
        public float range() { return getTier() == 2 ? super.range() * 2.2f : super.range(); }

        public void triggerDoor() { doorTimer = maxDoorTime; }

        private UnitType masterType() { return unitType("cargo-master-sword-drone", "newex-cargo-master-sword-drone"); }
        private UnitType swordType() { return unitType("newex-cargo-sword-drone", "cargo-sword-drone", "cargo-sworddrone"); }
        private UnitType mk2Type() { return unitType("newex-cargo-drone-mk2", "cargo-drone-mk2", "newex-cargo-dronemk2", "cargo-dronemk2"); }
        private UnitType normalType() { return unitType("newex-combat-drone", "combat-drone", "newex-cargo-drone", "cargo-drone"); }

        private boolean isMyDrone(Unit u, UnitType... types) {
            if (u == null || u.dead || u.team != team) return false;
            Integer owner = launcherMap.get(u.id);
            if (owner == null || owner != id) return false;
            for (UnitType ty : types) if (ty != null && u.type == ty) return true;
            return false;
        }

        private int count(UnitType... types) {
            int[] c = {0};
            Groups.unit.each(u -> { if (isMyDrone(u, types)) c[0]++; });
            return c[0];
        }

        public int countActiveDrones() {
            if (cdT >= 0f && Time.time - cdT < 10f && Time.time >= cdT) return cdV;
            cdV = count(normalType(), mk2Type(), swordType(), masterType());
            cdT = Time.time;
            return cdV;
        }

        public int countActiveMK2Drones() {
            if (cmT >= 0f && Time.time - cmT < 10f && Time.time >= cmT) return cmV;
            cmV = count(mk2Type());
            cmT = Time.time;
            return cmV;
        }

        public void recallAllDrones() {
            triggerDoor();
            UnitType[] types = {normalType(), mk2Type(), swordType(), masterType()};
            Groups.unit.each(u -> { if (isMyDrone(u, types)) recallSet.add(u.id); });
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (doorTimer > 0f) doorTimer = Math.max(0f, doorTimer - Time.delta);
            if (efficiency <= 0f) return;

            if (isSpawning) {
                spawnDelayTimer -= Time.delta;
                if (spawnDelayTimer <= 0f) {
                    isSpawning = false;
                    spawnCombatDrone();
                }
                return;
            }

            if (isShooting() && countActiveDrones() < getMaxDrones()) {
                spawnTimer += Time.delta;
                if (spawnTimer >= spawnInterval) {
                    spawnTimer = 0f;
                    triggerDoor();
                    isSpawning = true;
                    spawnDelayTimer = 30f;
                }
            }
        }

        public void spawnCombatDrone() {
            if (Vars.net.client()) return;
            int tier = getTier();
            UnitType type = null;

            if (tier == 2 && Mathf.chance(0.10)) type = masterType();

            if (type == null) {
                UnitType sword = swordType();
                if (Mathf.chance(0.50) && sword != null) {
                    type = sword;
                } else {
                    type = normalType();
                    if (tier == 1 && countActiveMK2Drones() < 2) type = mk2Type();
                }
            }

            if (type != null) {
                Unit unit = type.create(team);
                if (unit != null) {
                    unit.set(x, y);
                    launcherMap.put(unit.id, id);
                    if (tier == 2) mk2bUnits.add(unit.id);
                    unit.controller(new FlyingAI());
                    unit.add();
                    Fx.spawn.at(x, y);
                }
            }
        }

        @Override
        public void draw() {
            super.draw();
            float progress = 0f;
            if (doorTimer > 0f) progress = Mathf.sin((1f - doorTimer / maxDoorTime) * Mathf.PI);
            float off = progress * 6f;

            for (int i = 1; i <= 4; i++) {
                TextureRegion r = PerfCore.region("newex-drone-launcher-top" + i, "drone-launcher-top" + i);
                if (r == null || !r.found()) continue;
                float dx = i == 1 ? -off : i == 3 ? off : 0f;
                float dy = i == 2 ? off : i == 4 ? -off : 0f;
                Draw.rect(r, x + dx, y + dy);
            }
        }

        @Override
        public Object config() { return Integer.valueOf(getTier()); }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.b(getTier());
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            setTier(read.b());
        }

        // ---------------- UI ----------------

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            boolean isEn = en();
            Table bt = new Table();

            if (getTier() == 0) {
                bt.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(isEn))
                    .size(45, 40).tooltip(t(isEn, "Upgrade Drone dispatch module", "Nâng cấp mô-đun điều phối Drone"));
            } else {
                bt.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(t(isEn,
                    "[scarlet]TURRET HAS REACHED MAXIMUM EVOLUTION LEVEL![]",
                    "[scarlet]THÁP PHÁO ĐÃ ĐẠT CẤP ĐỘ TIẾN HÓA TỐI ĐA![]")))
                    .size(45, 40).tooltip(t(isEn, "Reached maximum level", "Đã đạt cấp tối đa"));
            }

            bt.button(Icon.leftOpen, Styles.cleari, 40, () -> {
                triggerDoor();
                NexNet.send("drone-recall", String.valueOf(pos()));
                NexNet.fx(Fx.tapBlock, x, y);
            }).size(45, 40).tooltip(t(isEn, "Recall all Drones belonging to this turret", "Thu hồi toàn bộ Drone thuộc tháp pháo này"));

            bt.button(Icon.info, Styles.cleari, 40, () -> openInfo(isEn))
                .size(45, 40).tooltip(t(isEn, "View detailed system stats", "Xem chi tiết thông số hệ thống"));

            table.add(bt);
        }

        private String requirementText(boolean isEn) {
            Building core = team.core();
            if (core == null) return t(isEn, "[red]Core not found![]", "[red]Không tìm thấy Lõi![]");
            int cu = core.items.get(Items.copper), pb = core.items.get(Items.lead);
            int si = core.items.get(Items.silicon), ti = core.items.get(Items.titanium);
            String c = col(cu, MK2_COST[0]), l = col(pb, MK2_COST[1]), s = col(si, MK2_COST[2]), tt = col(ti, MK2B_COST[2]);
            String common = " • " + t(isEn, "Copper: ", "Đồng: ") + c + cu + "[] / " + MK2_COST[0] + "\n"
                + " • " + t(isEn, "Lead: ", "Chì: ") + l + pb + "[] / " + MK2_COST[1] + "\n";
            return isEn
                ? "[yellow]CORE STORAGE REQUIREMENTS:[]\n[cyan]MK2 Branch (Quantity Focus):[]\n" + common
                    + " • Silicon: " + s + si + "[] / " + MK2_COST[2] + "\n[purple]MK2B Branch (Empowerment Focus):[]\n" + common
                    + " • Titanium: " + tt + ti + "[] / " + MK2B_COST[2]
                : "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n[cyan]Nhánh MK2 (Chuyên Số Lượng):[]\n" + common
                    + " • Silicon: " + s + si + "[] / " + MK2_COST[2] + "\n[purple]Nhánh MK2B (Chuyên Tăng Cường Chi Lực):[]\n" + common
                    + " • Titan: " + tt + ti + "[] / " + MK2B_COST[2];
        }

        private Table branch(BaseDialog dialog, boolean isEn, int tier, String head, String text, String btn) {
            Table tb = new Table(); tb.background(Styles.black6); tb.margin(12);
            tb.add(head).row();
            wrap(tb.add(text), 340); tb.row();
            tb.button(btn, () -> {
                Building core = team.core();
                int[] cost = tier == 1 ? MK2_COST : MK2B_COST;
                Item third = tier == 1 ? Items.silicon : Items.titanium;
                boolean ok = core != null && core.items.get(Items.copper) >= cost[0]
                    && core.items.get(Items.lead) >= cost[1] && core.items.get(third) >= cost[2];
                if (!ok) {
                    String n = tier == 1 ? "MK2" : "MK2B";
                    Vars.ui.showInfo(t(isEn, "[red]Not enough resources for " + n + " branch![]", "[red]Không đủ tài nguyên cho nhánh " + n + "![]"));
                    return;
                }
                NexNet.take(core, Items.copper, cost[0]);
                NexNet.take(core, Items.lead, cost[1]);
                NexNet.take(core, third, cost[2]);
                NexNet.fx(tier == 1 ? Fx.upgradeCore : Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5, 5, x, y);
                setTier(tier);
                NexNet.configure(this, Integer.valueOf(tier));
                dialog.hide(); deselect();
            }).size(180, 38);
            return tb;
        }

        private void openUpgrade(boolean isEn) {
            BaseDialog dialog = new BaseDialog(t(isEn, "Drone Launcher Upgrade Center", "Trung tâm nâng cấp Drone Launcher"));
            wrap(dialog.cont.add(requirementText(isEn)), 360);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            String t1 = isEn
                ? "Enhance air force capabilities:\n [white]• Adds [green]+2 Drone Mk2 Units[] (Total 6 Drones: 2 MK2 + 4 Normal/Sword).[]\n [white]• Increases turret health to [green]4500 HP[].[]"
                : "Tăng cường lực lượng không quân:\n [white]• Tăng thêm [green]+2 Unit Drone Mk2[] (Tổng 6 Drones: 2 MK2 + 4 Thường/Sword).[]\n [white]• Tăng máu tháp pháo lên [green]4500 HP[].[]";
            String t2 = isEn
                ? "Comprehensive enhancement tech breakthrough:\n [white]• Adds [green]+100% Base Health[], [green]+100% Armor[] for Drones.[]\n"
                    + " [white]• Adds [red]+100% Damage[], [sky]+100% Range[] for shots/slashes.[]\n [white]• Adds [yellow]+20% Flight Speed[] for Drones.[]\n"
                    + " [white]• Adds [gold]+120% Firing Range[] for Turret.[]\n [white]• Increases turret health to [green]6000 HP[].[]"
                : "Đột phá công nghệ cường hóa toàn diện:\n [white]• Tăng [green]+100% Máu gốc[], [green]+100% Giáp[] cho Drone.[]\n"
                    + " [white]• Tăng [red]+100% Sát thương[], [sky]+100% Tầm bắn[] đạn/chém.[]\n [white]• Tăng [yellow]+20% Tốc độ bay[] cho Drone.[]\n"
                    + " [white]• Tăng [gold]+120% Phạm vi bắn[] cho Tháp Pháo.[]\n [white]• Gia tăng máu tháp pháo lên [green]6000 HP[].[]";

            Table all = new Table();
            all.add(branch(dialog, isEn, 1, t(isEn, "[cyan]===(MK2 CONFIGURATION)===[]", "[cyan]===(CẤU HÌNH MK2)===[]"), t1,
                t(isEn, "[green]ACTIVATE MK2[]", "[green]KÍCH HOẠT MK2[]"))).width(340);
            all.row(); all.add().height(12).row();
            all.add(branch(dialog, isEn, 2, t(isEn, "[purple]===(MK2B CONFIGURATION)===[]", "[purple]===(CẤU HÌNH MK2B)===[]"), t2,
                t(isEn, "[orange]ACTIVATE MK2B[]", "[orange]KÍCH HOẠT MK2B[]"))).width(340);

            ScrollPane scroll = new ScrollPane(all);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean isEn) {
            String title = t(isEn, " Drone Launcher Info: ", " Thông tin Drone Launcher: ");
            String desc;
            int tier = getTier();
            if (tier == 0) {
                title += t(isEn, "[yellow](MK1 - Default)[]", "[yellow](MK1 - Mặc định)[]");
                desc = isEn
                    ? "[gold]⚡ ORIGINAL STATUS (MK1) ⚡[]\n[lightgray]Turret Health:[] [green]3000 HP[]\n[lightgray]Max Drone Count:[] [yellow]4 Units[]\n[lightgray]Drone Type:[] Combat / Sword / Cargo Drone (50% Sword)"
                    : "[gold]⚡ TRẠNG THÁI NGUYÊN BẢN (MK1) ⚡[]\n[lightgray]Máu pháo:[] [green]3000 HP[]\n[lightgray]Số lượng Drone tối đa:[] [yellow]4 Units[]\n[lightgray]Loại Drone:[] Combat / Sword / Cargo Drone (50% Sword)";
            } else if (tier == 1) {
                title += t(isEn, "[cyan](MK2 - Quantity)[]", "[cyan](MK2 - Số Lượng)[]");
                desc = isEn
                    ? "[cyan]⚡ MK2 SUMMON CONFIG ⚡[]\n[lightgray]Turret Health:[] [green]4500 HP[]\n[lightgray]Max Drone Count:[] [green]6 Units[]\n[lightgray]Composition:[] [yellow]2 Cargo Drone Mk2[] + [white]4 Normal/Sword Drones[]"
                    : "[cyan]⚡ CẤU HÌNH TRIỆU HỒI MK2 ⚡[]\n[lightgray]Máu pháo:[] [green]4500 HP[]\n[lightgray]Số lượng Drone tối đa:[] [green]6 Units[]\n[lightgray]Thành phần:[] [yellow]2 Cargo Drone Mk2[] + [white]4 Drone Thường/Sword[]";
            } else {
                title += t(isEn, "[purple](MK2B - Enhancement)[]", "[purple](MK2B - Cường Hóa)[]");
                desc = isEn
                    ? "[purple]⚡ MK2B VARIANT CONFIG ⚡[]\n[lightgray]Turret Health:[] [green]6000 HP[]\n[lightgray]Turret Range:[] [gold]+120%[]\n[lightgray]Drone Count:[] [yellow]4 Units[]\n[lightgray]Drone Health & Armor:[] [green]+100%[]\n[lightgray]Movement Speed:[] [yellow]+20%[]\n[lightgray]Dash & Shooting Damage:[] [red]+100%[]"
                    : "[purple]⚡ CẤU HÌNH BIẾN THỂ MK2B ⚡[]\n[lightgray]Máu pháo:[] [green]6000 HP[]\n[lightgray]Phạm vi pháo:[] [gold]+120%[]\n[lightgray]Số lượng Drone:[] [yellow]4 Units[]\n[lightgray]Máu & Giáp Drone:[] [green]+100%[]\n[lightgray]Tốc độ di chuyển:[] [yellow]+20%[]\n[lightgray]Sát thương Lướt & Bắn:[] [red]+100%[]";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            wrap(info.add(desc), 360);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400);
            dialog.addCloseButton(); dialog.show();
        }
    }
}
