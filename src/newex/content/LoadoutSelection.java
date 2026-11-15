package newex.content;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Angles;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.TextButton.TextButtonStyle;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.ai.types.BuilderAI;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.units.AIController;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.mod.Mods.LoadedMod;
import mindustry.type.Item;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.environment.OreBlock;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BuildVisibility;
import newex.HiddenOres;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển từ loadout-selection.js: chọn pháo đầu trận, nút HUD (Gọi Wave / Thiên thạch / Spawn / Wipe / Mega...),
 * phím tắt 1-6, ẩn quặng, tối ưu FPS, buff máu địch theo wave, hộp cài đặt mod.
 * Quy ước: mỗi NexNet.on(...) của JS gom vào initMeteorNet/initActionNet/initWaveNet, gọi từ init().
 */
public final class LoadoutSelection {
    private LoadoutSelection() {}

    static final String CURRENT_MOD_NAME = "newex";
    static final String TAG_KEY = "newex-selected-turrets";

    static boolean isEn() { return Core.settings.getString("locale", "").startsWith("en"); }

    static final String[] fallbackTurrets = {
        "nucleytor", "emperfum", "galaxvorram", "bayrowfyr", "dor",
        "flazerd", "erysidus", "blixalum", "heliyron", "persefer",
        "tankani2k", "zoj", "reflecounum", "swordoder", "buffetles",
        "maxitoner", "xylaon", "lyvervon", "vendicum", "drone-launcher",
        "holyder", "plasanod", "tankani4k", "therdum", "lazash",
        "forstarsilum", "hitekalum", "reguilater", "lavunder", "blaw",
        "dtg-soldern", "indeniter", "rangtaturs", "tyber", "endyr",
        "sonicor", "spawner"
    };

    static Seq<Block> turretList = new Seq<>();
    static Seq<Block> selectedTurrets = new Seq<>();
    static Table customWaveContainer = null;
    static boolean waveBtnAdded = false;
    static long lastSkipTime = 0;

    static float btnX = Core.settings.getFloat("newex-wave-btn-x", 200f);
    static float btnY = Core.settings.getFloat("newex-wave-btn-y", 200f);

    static boolean autoFpsSavedEffects = true;
    static boolean isFpsThrottled = false;

    static boolean autoLowFpsSetting = true;
    static boolean showWaveBtnSetting = true;
    static boolean hideOresSetting = false;
    static int hpPerWavePercentSetting = 10;
    static int fpsOptPercentSetting = 0;

    static int countWave = 0, countWave10 = 0, countSelfDestruct = 0, countMeteor = 0,
        countClearER = 0, countDeleteOre = 0, countRandomSpawnE = 0, countWipe = 0, countMega = 0;

    /** Tham chiếu tới các nút trên HUD để phím tắt cập nhật được bộ đếm (JS: object hudButtons). */
    static final ObjectMap<String, TextButton> hudButtons = new ObjectMap<>();

    /** Xác nhận 2 lần cho phím phá toàn bộ công trình. */
    static long wipeArmedUntil = 0;
    static final long WIPE_CONFIRM_MS = 3000;

    // --- BẢNG PHÍM TẮT ---
    static final KeyCode[] HK_WAVE = {KeyCode.num1, KeyCode.numpad1};
    static final KeyCode[] HK_METEOR = {KeyCode.num2, KeyCode.numpad2};
    static final KeyCode[] HK_WAVE10 = {KeyCode.num3, KeyCode.numpad3};
    static final KeyCode[] HK_SPAWNE = {KeyCode.num4, KeyCode.numpad4};
    static final KeyCode[] HK_WIPE = {KeyCode.num5, KeyCode.numpad5};
    static final KeyCode[] HK_MEGA = {KeyCode.num6, KeyCode.numpad6};

    static boolean hotkeyTapped(KeyCode[] codes) {
        for (KeyCode c : codes) if (Core.input.keyTap(c)) return true;
        return false;
    }

    static float targetCamX = -1f, targetCamY = -1f, panDuration = 0.4f, panTimer = 1.0f, panStartX = 0f, panStartY = 0f;

    // --- ẨN QUẶNG & LƯU BỀN (global.newexHiddenOres -> HiddenOres) ---
    static Block findOreBlockByItem(Item item) {
        Block[] vanilla = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium,
            Blocks.oreThorium, Blocks.oreScrap, Blocks.oreBeryllium, Blocks.oreTungsten};
        for (Block ore : vanilla) {
            if (ore instanceof OreBlock && ((OreBlock) ore).itemDrop == item) return ore;
        }
        final Block[] found = {null};
        Vars.content.blocks().each(b -> {
            if (b instanceof OreBlock && ((OreBlock) b).itemDrop == item) found[0] = b;
        });
        return found[0] != null ? found[0] : Blocks.oreCopper;
    }

    static void saveHiddenOresToSettings(Map<String, Item> hidden) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Item> en : hidden.entrySet()) {
            if (en.getValue() == null) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(en.getKey()).append(':').append(en.getValue().name);
        }
        Core.settings.put("newex-saved-hidden-ores", sb.toString());
    }

    static Map<String, Item> loadHiddenOresFromSettings() {
        Map<String, Item> out = new HashMap<>();
        String str = Core.settings.getString("newex-saved-hidden-ores", "");
        if (!str.isEmpty()) {
            for (String pair : str.split(";")) {
                String[] sub = pair.split(":");
                if (sub.length == 2) {
                    Item item = Vars.content.item(sub[1]);
                    if (item != null) out.put(sub[0], item);
                }
            }
        }
        return out;
    }

    static void applyOreHidingState(boolean hide) {
        if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
        int width = Vars.world.width(), height = Vars.world.height();
        if (hide) {
            HiddenOres.clear();
            Map<String, Item> current = new HashMap<>();
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    Tile t = Vars.world.tile(x, y);
                    if (t != null && t.overlay() != null && t.overlay() != Blocks.air) {
                        Item drop = t.drop();
                        if (drop != null) {
                            HiddenOres.put(t.x, t.y, drop);
                            current.put(HiddenOres.key(t.x, t.y), drop);
                            NexNet.setOverlay(t, Blocks.air);
                        }
                    }
                }
            }
            saveHiddenOresToSettings(current);
        } else {
            if (HiddenOres.isEmpty()) {
                for (Map.Entry<String, Item> en : loadHiddenOresFromSettings().entrySet()) {
                    String[] p = en.getKey().split(",");
                    if (p.length == 2) HiddenOres.put(Strings.parseInt(p[0], 0), Strings.parseInt(p[1], 0), en.getValue());
                }
            }
            for (Map.Entry<String, Item> en : new ArrayList<>(HiddenOres.all().entrySet())) {
                String[] p = en.getKey().split(",");
                if (p.length == 2 && en.getValue() != null) {
                    Tile t = Vars.world.tile(Strings.parseInt(p[0], 0), Strings.parseInt(p[1], 0));
                    if (t != null) NexNet.setOverlay(t, findOreBlockByItem(en.getValue()));
                }
            }
            HiddenOres.clear();
            Core.settings.put("newex-saved-hidden-ores", "");
        }
    }

    static void smoothPanTo(float x, float y) {
        targetCamX = x;
        targetCamY = y;
        panStartX = Core.camera.position.x;
        panStartY = Core.camera.position.y;
        panTimer = 0f;
    }

    static void reloadCachedSettings() {
        autoLowFpsSetting = Core.settings.getBool("newex-auto-low-fps", true);
        showWaveBtnSetting = Core.settings.getBool("newex-show-wave-btn", true);
        hideOresSetting = Core.settings.getBool("newex-hide-ores", false);
        hpPerWavePercentSetting = Core.settings.getInt("newex-hp-per-wave-percent", 10);
        fpsOptPercentSetting = Core.settings.getInt("newex-fps-opt-percent", 0);
    }

    static float normalDelta() { return Math.min(Core.graphics.getDeltaTime() * 60f, 3f); }
    static float slowDelta() { return Math.min(Core.graphics.getDeltaTime() * 60f, 3f) * 0.8f; }

    // ---- Hiệu ứng ----
    static void draw3DRotatedEllipseWave(float cx, float cy, float rx, float ry, float rotDeg) {
        int points = 20;
        float rot = rotDeg * Mathf.degRad, cosR = Mathf.cos(rot), sinR = Mathf.sin(rot);
        float lx = rx, ly = 0f;
        float lastX = cx + (lx * cosR - ly * sinR), lastY = cy + (lx * sinR + ly * cosR);
        for (int i = 1; i <= points; i++) {
            float ang = (i * 360f / points) * Mathf.degRad;
            lx = Mathf.cos(ang) * rx;
            ly = Mathf.sin(ang) * ry;
            float nx = cx + (lx * cosR - ly * sinR), ny = cy + (lx * sinR + ly * cosR);
            Lines.line(lastX, lastY, nx, ny);
            lastX = nx;
            lastY = ny;
        }
    }

    static final Color C_FFB380 = Color.valueOf("ffb380");

    static final Effect customShockwaveEffect = new Effect(40f, e -> {
        Draw.z(Layer.effect + 4f);
        float f = e.fin();
        float alpha = 1f - Interp.pow3Out.apply(f);
        float radius = 10f + 240f * Interp.pow2Out.apply(f);
        Lines.stroke(6f * alpha, C_FFB380);
        Lines.circle(e.x, e.y, radius);
        Lines.stroke(3f * alpha, Color.white);
        Lines.circle(e.x, e.y, radius * 0.85f);
        Draw.reset();
    });

    static final Effect deleteOreEffect = new Effect(45f, e -> {
        Draw.z(Layer.effect + 5f);
        float f = e.fin(), alpha = 1f - f, radius = f * 80f;
        Draw.color(PerfCore.color("80ffea"));
        Lines.stroke(2.5f * alpha);
        Lines.circle(e.x, e.y, radius);
        Draw.color(Color.white);
        Fill.circle(e.x, e.y, (1f - f) * 6f);
        Draw.reset();
    });

    static final Effect meteorFallingEffect = new Effect(50f, e -> {
        Draw.z(Layer.effect + 5f);
        float f = e.fin();
        float portalX = e.x - 220f, portalY = e.y + 450f;
        float curX = Mathf.lerp(portalX, e.x, f), curY = Mathf.lerp(portalY, e.y, f);

        if (f < 0.85f) {
            float portalAlpha = f < 0.15f ? f / 0.15f : (0.85f - f) / 0.7f;
            TextureRegion star = PerfCore.region("newex-star-field", "star-field");
            if (star.found()) {
                Draw.color(Color.white);
                Draw.alpha(portalAlpha * 0.8f);
                Draw.rect(star, portalX, portalY, 108f, 108f, Time.time * 3f);
            }
            TextureRegion hole = PerfCore.region("newex-blackhole-pulse", "blackhole-pulse");
            if (hole.found()) {
                Draw.color(Color.white);
                Draw.alpha(portalAlpha);
                Draw.rect(hole, portalX, portalY, 90f, 90f, Time.time * -6f);
            }
            Draw.color(PerfCore.color("1a1721"));
            Draw.alpha(portalAlpha * 0.6f);
            Fill.circle(portalX, portalY, 18f + Mathf.sin(Time.time * 0.15f) * 2f);
            Draw.color(Color.black);
            Draw.alpha(portalAlpha);
            Fill.circle(portalX, portalY, 14f);
        }

        float waveRadius = 6f + 28f * Interp.pow2Out.apply(f);
        Lines.stroke(2f * (1f - f), C_FFB380);
        draw3DRotatedEllipseWave(curX, curY, waveRadius, waveRadius * 0.4f, 33f);

        float tailAngle = Angles.angle(portalX, portalY, e.x, e.y) + 180f;
        if (Mathf.chance(0.8)) {
            Fx.smoke.at(curX + Angles.trnsx(tailAngle, 6f) + Mathf.range(3f), curY + Angles.trnsy(tailAngle, 6f) + Mathf.range(3f));
        }

        TextureRegion meteor = PerfCore.region("newex-basalt-bluff", "basalt-bluff");
        if (meteor.found()) {
            Draw.color(Color.white);
            Draw.rect(meteor, curX, curY, 24f, 24f, Time.time * 15f);
        } else {
            Draw.color(PerfCore.color("594e48"));
            Fill.circle(curX, curY, 9f);
        }
        Draw.reset();
    });

    // --- ĐỒNG BỘ CAMERA: effect "vô hình" gửi qua NexNet.fx, máy nhận tự lướt camera ---
    static int lastCamPanEffectId = -1;
    static final Effect camPanEffect = new Effect(10f, 10000000f, e -> {
        if (e.id == lastCamPanEffectId) return;
        lastCamPanEffectId = e.id;
        if (targetCamX >= 0 && Math.abs(targetCamX - e.x) < 1f && Math.abs(targetCamY - e.y) < 1f) return;
        smoothPanTo(e.x, e.y);
    });

    static void panAllPlayers(float x, float y) {
        smoothPanTo(x, y);
        try { NexNet.fx(camPanEffect, x, y); } catch (Throwable ignored) {}
    }

    static Team requesterTeam(Player player) {
        return player != null ? player.team() : Vars.state.rules.defaultTeam;
    }

    static void serverMeteor(float tx, float ty) {
        Tile target = Vars.world.tileWorld(tx, ty);
        if (target == null) return;

        Fx.reactorExplosion.at(tx, ty);
        Fx.dynamicExplosion.at(tx, ty);
        Fx.smokeCloud.at(tx, ty);
        customShockwaveEffect.at(tx, ty);
        NexNet.fx(Fx.reactorExplosion, tx, ty);
        NexNet.fx(Fx.dynamicExplosion, tx, ty);
        NexNet.fx(Fx.smokeCloud, tx, ty);
        NexNet.fx(customShockwaveEffect, tx, ty);
        NexNet.shake(14f, 14f, tx, ty);

        Damage.damage(tx, ty, 30f * Vars.tilesize, 1000f);

        int destroyRadius = 15, outerRadius = 30;
        for (int rx = -outerRadius; rx <= outerRadius; rx++) {
            for (int ry = -outerRadius; ry <= outerRadius; ry++) {
                float dist = Mathf.sqrt((float) (rx * rx + ry * ry));
                Tile t = Vars.world.tile(target.x + rx, target.y + ry);
                if (t != null && t.build != null) {
                    if (dist <= destroyRadius) t.build.kill();
                    else if (dist <= outerRadius) t.build.damage(1000f);
                }
            }
        }

        Block[] ores = {Blocks.oreCopper, Blocks.oreLead, Blocks.oreCoal, Blocks.oreTitanium,
            Blocks.oreThorium, Blocks.oreScrap, Blocks.oreBeryllium, Blocks.oreTungsten};
        Block selected = ores[Mathf.floor(Mathf.random(0f, (float) ores.length))];
        if (selected == null) return;

        int oreRadius = Mathf.floor(Mathf.random(2f, 5f));
        for (int rx = -oreRadius; rx <= oreRadius; rx++) {
            for (int ry = -oreRadius; ry <= oreRadius; ry++) {
                float dist = Mathf.sqrt((float) (rx * rx + ry * ry));
                if (dist <= oreRadius && Mathf.chance(1.0 - (dist / (oreRadius + 1.0)))) {
                    Tile t = Vars.world.tile(target.x + rx, target.y + ry);
                    if (t != null && t.floor() != null && !t.floor().isLiquid) NexNet.setOverlay(t, selected);
                }
            }
        }
    }

    static void initMeteorNet() {
        NexNet.on("meteor", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
            String[] a = payload.split("\\|");
            if (a.length < 2) return;
            float px = Strings.parseFloat(a[0], Float.NaN), py = Strings.parseFloat(a[1], Float.NaN);
            if (Float.isNaN(px) || Float.isNaN(py)) return;
            final float tx = Mathf.clamp(px, 0f, Vars.world.width() * Vars.tilesize);
            final float ty = Mathf.clamp(py, 0f, Vars.world.height() * Vars.tilesize);
            Time.run(60f, () -> {
                meteorFallingEffect.at(tx, ty);
                NexNet.fx(meteorFallingEffect, tx, ty);
                NexNet.toastAll(isEn() ? "[orange]Meteor falling onto the map![]" : "[orange]Thiên thạch đang rơi xuống bản đồ![]", 2f);
                Time.run(50f, () -> {
                    if (Vars.state != null && Vars.state.isGame()) serverMeteor(tx, ty);
                });
            });
        }, true, 500);
    }

    // ---- Các nút hành động (client gọi -> NexNet.send -> máy chủ xử lý) ----
    static void triggerRandomMeteor(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
        float ww = Vars.world.width() * Vars.tilesize, wh = Vars.world.height() * Vars.tilesize;
        float mx = ww * 0.1f, my = wh * 0.1f;
        float tx = Mathf.random(mx, ww - mx), ty = Mathf.random(my, wh - my);
        panAllPlayers(tx, ty);
        Vars.ui.showInfoToast(isEn() ? "[orange]Camera panning to meteor target...[]" : "[orange]Đang di chuyển camera đến mục tiêu thiên thạch...[]", 1.5f);
        NexNet.send("meteor", tx + "|" + ty);
        countMeteor++;
        if (btn != null) btn.setText((isEn() ? "Meteor" : "TT rơi") + " (" + countMeteor + ")");
    }

    static void clearEnemyUnitsPercent(int percent) {
        if (Vars.state == null || !Vars.state.isGame()) return;
        NexNet.send("clearE", String.valueOf(percent));
    }

    static void initActionNet() {
        NexNet.on("clearE", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame()) return;
            int parsed = Strings.parseInt(payload, 0);
            final int percent = Mathf.clamp(parsed == 0 ? 99 : parsed, 1, 99); // JS: parseInt(x) || 99
            final Team own = requesterTeam(player);
            final boolean pvp = Vars.state.rules.pvp;
            final Team waveTeam = Vars.state.rules.waveTeam;
            Seq<Unit> enemies = new Seq<>();
            Groups.unit.each(u -> {
                if (u != null && u.team != own && !u.dead && (!pvp || u.team == waveTeam)) enemies.add(u);
            });
            if (enemies.isEmpty()) {
                NexNet.toastTo(player, isEn() ? "No enemy units on the map!" : "Không có quái địch nào trên bản đồ!", 2f);
                return;
            }
            enemies.shuffle();
            int kill = (int) Math.floor(enemies.size * (percent / 100.0));
            if (kill < 1 && enemies.size > 0) kill = 1;
            for (int i = 0; i < kill; i++) {
                Unit u = enemies.get(i);
                if (u != null) u.kill();
            }
            NexNet.toastTo(player, isEn()
                ? "[red]Successfully killed random " + kill + " (" + percent + "%) enemy units![]"
                : "[red]Đã tiêu diệt ngẫu nhiên " + kill + " (" + percent + "%) units địch![]", 2f);
        }, true, 500);

        // --- SPAWN ĐỊCH NGẪU NHIÊN (camera lướt tới, chờ 1 giây rồi spawn) ---
        NexNet.on("randomspawnE", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
            String[] a = payload.split("\\|");
            if (a.length < 2) return;
            float tx = Strings.parseFloat(a[0], Float.NaN), ty = Strings.parseFloat(a[1], Float.NaN);
            if (Float.isNaN(tx) || Float.isNaN(ty)) return;
            final Team enemyTeam = Vars.state.rules.waveTeam != null ? Vars.state.rules.waveTeam : Team.crux;
            final Tile target = Vars.world.tileWorld(tx, ty);
            if (target == null) return;
            Seq<UnitType> types = new Seq<>();
            Vars.content.units().each(u -> { if (u != null && !u.internal) types.add(u); });
            if (types.isEmpty()) return;
            types.shuffle();
            final UnitType chosen = types.first();
            Time.run(60f, () -> {
                if (Vars.state == null || !Vars.state.isGame()) return;
                Unit spawned = chosen.create(enemyTeam);
                if (spawned != null) {
                    float wx = target.worldx(), wy = target.worldy();
                    spawned.set(wx, wy);
                    spawned.add();
                    Fx.spawn.at(wx, wy);
                    Fx.teleportOut.at(wx, wy);
                    NexNet.fx(Fx.spawn, wx, wy);
                    NexNet.toastAll(isEn() ? "[orange]Random enemy spawned: " + chosen.name + "![]" : "[orange]Spawn ngẫu nhiên địch: " + chosen.localizedName + "![]", 2f);
                }
            });
        }, true, 500);

        // --- PHÁ HẾT CÔNG TRÌNH (trừ lõi) + XOÁ TÀI NGUYÊN LÕI ---
        NexNet.on("wipeall", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame()) return;
            Team team = requesterTeam(player);
            if (team == null) return;
            Seq<CoreBuild> cores = team.cores();
            if (cores != null) cores.each(c -> { if (c != null && c.items != null) c.items.clear(); });
            final Seq<Building> list = new Seq<>();
            Groups.build.each(b -> { if (b != null && b.team == team && !(b.block instanceof CoreBlock)) list.add(b); });
            final int total = list.size;
            final int[] idx = {0};
            // Phá theo lô 120 công trình/tick để khỏi giật lag
            Runnable step = new Runnable() {
                @Override public void run() {
                    if (Vars.state == null || !Vars.state.isGame()) return;
                    int end = Math.min(idx[0] + 120, list.size);
                    for (; idx[0] < end; idx[0]++) {
                        Building b = list.get(idx[0]);
                        if (b != null && b.isValid()) b.kill();
                    }
                    if (idx[0] < list.size) Time.run(1f, this);
                }
            };
            step.run();
            NexNet.toastTo(player, isEn()
                ? "[red]Destroyed " + total + " buildings and cleared core resources![]"
                : "[red]Đã phá " + total + " công trình và xóa sạch tài nguyên trong lõi![]", 3f);
        }, true, 1000);

        // --- MEGA THEO NGƯỜI CHƠI & HỖ TRỢ XÂY ---
        NexNet.on("spawnmega", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame()) return;
            Team team = requesterTeam(player);
            if (team == null) return;
            Player ownerPlayer = player != null ? player : Vars.player;
            Unit ownerUnit = ownerPlayer != null ? ownerPlayer.unit() : null;
            float sx, sy;
            if (ownerUnit != null && !ownerUnit.dead) {
                sx = ownerUnit.x + Mathf.range(24f);
                sy = ownerUnit.y + Mathf.range(24f);
            } else {
                CoreBuild core = team.core();
                if (core == null) return;
                sx = core.x + Mathf.range(24f);
                sy = core.y + Mathf.range(24f);
            }
            Unit mega = UnitTypes.mega.create(team);
            if (mega == null) return;
            mega.set(sx, sy);
            try {
                mega.controller(createFollowAssistAI(ownerPlayer));
            } catch (Throwable e) {
                try { mega.controller(new BuilderAI()); } catch (Throwable ignored) {}
            }
            mega.add();
            Fx.spawn.at(sx, sy);
            NexNet.fx(Fx.spawn, sx, sy);
        }, true, 150);

        // --- XOÁ NGẪU NHIÊN 1 BÃI KHOÁNG ---
        NexNet.on("delore", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
            String[] a = payload.split("\\|");
            final Tile sample = a.length >= 2 ? Vars.world.tile(Strings.parseInt(a[0], -1), Strings.parseInt(a[1], -1)) : null;
            if (sample == null || sample.overlay() == null || sample.overlay() == Blocks.air) {
                NexNet.toastTo(player, isEn() ? "No ore deposits found on the map!" : "Không tìm thấy bãi khoáng sản nào trên bản đồ!", 2f);
                return;
            }
            Time.run(60f, () -> {
                Block oreType = sample.overlay();
                if (oreType == null || oreType == Blocks.air) return;
                int cleared = 0, radius = 5;
                for (int rx = -radius; rx <= radius; rx++) {
                    for (int ry = -radius; ry <= radius; ry++) {
                        Tile t = Vars.world.tile(sample.x + rx, sample.y + ry);
                        if (t != null && t.overlay() == oreType) {
                            NexNet.setOverlay(t, Blocks.air);
                            cleared++;
                        }
                    }
                }
                deleteOreEffect.at(sample.worldx(), sample.worldy());
                NexNet.fx(deleteOreEffect, sample.worldx(), sample.worldy());
                NexNet.toastAll(isEn() ? "[orange]Random ore deposit deleted (" + cleared + " tiles)![]" : "[orange]Đã xóa ngẫu nhiên 1 bãi khoáng sản (" + cleared + " ô)![]", 2f);
            });
        }, true, 500);
    }

    static void triggerRandomSpawnEnemy(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
        Seq<Tile> valid = new Seq<>();
        int width = Vars.world.width(), height = Vars.world.height();
        for (int x = 2; x < width - 2; x += 3) {
            for (int y = 2; y < height - 2; y += 3) {
                Tile t = Vars.world.tile(x, y);
                if (t != null && !t.solid() && t.build == null && !t.floor().isLiquid) valid.add(t);
            }
        }
        if (valid.isEmpty()) {
            Vars.ui.showInfoToast(isEn() ? "No valid open location to spawn enemy!" : "Không tìm thấy vị trí trống hợp lệ để spawn địch!", 2f);
            return;
        }
        valid.shuffle();
        Tile spawn = valid.first();
        float tx = spawn.worldx(), ty = spawn.worldy();
        panAllPlayers(tx, ty);
        Vars.ui.showInfoToast(isEn() ? "[orange]Camera panning to enemy spawn target...[]" : "[orange]Đang di chuyển camera đến mục tiêu spawn địch...[]", 1.5f);
        NexNet.send("randomspawnE", tx + "|" + ty);
        countRandomSpawnE++;
        if (btn != null) btn.setText((isEn() ? "Random Spawn E" : "Spawn ngẫu nhiên E") + " (" + countRandomSpawnE + ")");
    }

    static void triggerWipeAll(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame()) return;
        long now = Time.millis();
        if (now > wipeArmedUntil) {
            wipeArmedUntil = now + WIPE_CONFIRM_MS;
            Vars.ui.showInfoToast(isEn()
                ? "[scarlet]Press again within 3s to DESTROY all buildings (except cores) and clear core items![]"
                : "[scarlet]Bấm lại trong 3 giây để PHÁ toàn bộ công trình (trừ lõi) và xóa tài nguyên lõi![]", 3f);
            return;
        }
        wipeArmedUntil = 0;
        NexNet.send("wipeall", "");
        countWipe++;
        if (btn != null) btn.setText((isEn() ? "Wipe Base" : "Phá hết công trình") + " (" + countWipe + ")");
    }

    static AIController createFollowAssistAI(final Player ownerPlayer) {
        return new AIController() {
            @Override public void updateMovement() {
                Unit u = unit;
                if (u == null) return;
                Unit owner = ownerPlayer != null ? ownerPlayer.unit() : null;
                if (owner == null || owner.dead) return;
                u.updateBuilding = true;
                if (owner.activelyBuilding()) {
                    BuildPlan plan = owner.buildPlan();
                    if (plan != null) {
                        // Giống BuilderAI: copy plan đang xây của người chơi
                        u.plans.clear();
                        u.plans.addFirst(plan);
                        moveTo(plan.tile(), u.type.buildRange - 20f);
                        return;
                    }
                }
                if (u.plans.size > 0) u.plans.clear();
                moveTo(owner, 60f);
            }
        };
    }

    static void triggerSpawnMega(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame()) return;
        NexNet.send("spawnmega", "");
        countMega++;
        if (btn != null) btn.setText("Spawn Mega (" + countMega + ")");
    }

    static void deleteRandomOreDeposit(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame() || Vars.world == null) return;
        Seq<Tile> ores = new Seq<>();
        int width = Vars.world.width(), height = Vars.world.height();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Tile t = Vars.world.tile(x, y);
                if (t != null && t.overlay() != null && t.overlay() != Blocks.air) ores.add(t);
            }
        }
        if (ores.isEmpty()) {
            Vars.ui.showInfoToast(isEn() ? "No ore deposits found on the map!" : "Không tìm thấy bãi khoáng sản nào trên bản đồ!", 2f);
            return;
        }
        ores.shuffle();
        Tile sample = ores.first();
        panAllPlayers(sample.worldx(), sample.worldy());
        Vars.ui.showInfoToast(isEn() ? "[orange]Camera panning to ore deposit...[]" : "[orange]Đang di chuyển camera đến bãi khoáng sản...[]", 1.5f);
        NexNet.send("delore", sample.x + "|" + sample.y);
        countDeleteOre++;
        if (btn != null) btn.setText((isEn() ? "Del Ore" : "Xóa mỏ") + " (" + countDeleteOre + ")");
    }

    // ---- Danh sách pháo & hiển thị trong menu xây ----
    static void loadTurretsFromFolder() {
        turretList.clear();
        final List<String> fallback = Arrays.asList(fallbackTurrets);
        LoadedMod mod = Vars.mods.getMod(CURRENT_MOD_NAME);
        if (mod != null && mod.root != null) {
            Fi blocksDir = mod.root.child("content").child("blocks");
            if (blocksDir.exists() && blocksDir.isDirectory()) {
                blocksDir.walk(file -> {
                    if (file.isDirectory()) return;
                    String blockName = file.nameWithoutExtension();
                    if (blockName.equals("repulsyron") || blockName.equals("emperical") || blockName.equals("aether-spiral")
                        || blockName.equals("aetherspiral") || blockName.equals("aether spiral")) return;
                    Block block = Vars.content.block(CURRENT_MOD_NAME + "-" + blockName);
                    if (block != null && (block instanceof Turret || fallback.contains(blockName)) && !turretList.contains(block)) {
                        turretList.add(block);
                    }
                });
            }
        }
        for (String name : fallbackTurrets) {
            Block block = Vars.content.block(CURRENT_MOD_NAME + "-" + name);
            if (block != null && !turretList.contains(block)) turretList.add(block);
        }
    }

    static int getMaxSelectCount() { return Core.settings.getInt("newex-max-turrets", 9999); }

    static void applyTurretVisibility(Seq<String> allowed) {
        boolean sandbox = Vars.state.rules != null && (Vars.state.rules.infiniteResources || Vars.state.rules.editor);
        boolean gacha = Core.settings.getBool("newex-gacha-enabled", false);
        if (sandbox) {
            turretList.each(b -> b.buildVisibility = BuildVisibility.shown);
            return;
        }
        Seq<String> unlocked = Shop.getUnlockedTurrets(turretList);
        turretList.each(b -> {
            boolean isUnlocked = gacha ? unlocked.contains(b.name) : true;
            boolean isSelected = allowed != null && allowed.contains(b.name);
            b.buildVisibility = (isUnlocked && isSelected) ? BuildVisibility.shown : BuildVisibility.hidden;
        });
    }

    static void initWaveNet() {
        NexNet.on("wave", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame() || Vars.logic == null) return;
            int parsed = Strings.parseInt(payload, 0);
            int count = Mathf.clamp(parsed == 0 ? 1 : parsed, 1, 10); // JS: parseInt(x) || 1
            for (int i = 0; i < count; i++) Vars.logic.runWave();
            if (count > 1) {
                NexNet.toastTo(player, isEn() ? "[green]Summoned " + count + " consecutive waves![]" : "[green]Đã triệu hồi " + count + " đợt quái liên tiếp![]", 2f);
            }
        }, true, 350);

        NexNet.on("selfdestruct", (player, payload) -> {
            if (Vars.state == null || !Vars.state.isGame()) return;
            final Team team = requesterTeam(player);
            Seq<CoreBuild> cores = team != null ? team.cores() : null;
            if (cores == null || cores.isEmpty()) {
                NexNet.toastTo(player, isEn() ? "No friendly core found!" : "Không tìm thấy lõi phe ta!", 2f);
                return;
            }
            Time.run(60f, () -> {
                Seq<CoreBuild> list = team.cores().copy();
                list.each(c -> { if (c != null) c.kill(); });
                NexNet.toastTo(player, isEn() ? "[red]Core self-destruction triggered![]" : "[red]Đã kích hoạt tự hủy lõi![]", 2f);
            });
        }, true, 1000);
    }

    static void triggerNextWave(TextButton btn) {
        long now = Time.millis();
        if (now - lastSkipTime >= 400) {
            lastSkipTime = now;
            if (Vars.state != null && Vars.state.isGame()) NexNet.send("wave", "1");
            countWave++;
            if (btn != null) btn.setText((isEn() ? "Call Wave" : "Gọi Quái Wave") + " (" + countWave + ")");
        }
    }

    static void triggerMultipleWaves(int count, TextButton btn) {
        long now = Time.millis();
        if (now - lastSkipTime >= 400) {
            lastSkipTime = now;
            if (Vars.state != null && Vars.state.isGame()) NexNet.send("wave", String.valueOf(count));
            countWave10++;
            if (btn != null) btn.setText((isEn() ? "x10 Call Wave" : "x10 Gọi Wave") + " (" + countWave10 + ")");
        }
    }

    static void selfDestructCores(TextButton btn) {
        if (Vars.state == null || !Vars.state.isGame()) return;
        Team team = Vars.player != null ? Vars.player.team() : Vars.state.rules.defaultTeam;
        if (team == null) return;
        Seq<CoreBuild> cores = team.cores();
        if (cores != null && !cores.isEmpty()) {
            CoreBuild core = cores.first();
            if (core != null) {
                panAllPlayers(core.x, core.y);
                Vars.ui.showInfoToast(isEn() ? "[red]Camera panning to core...[]" : "[red]Đang di chuyển camera tới lõi...[]", 1.5f);
                NexNet.send("selfdestruct", "");
                countSelfDestruct++;
                if (btn != null) btn.setText((isEn() ? "Self-Destruct" : "Tự hủy lõi") + " (" + countSelfDestruct + ")");
            }
        } else {
            Vars.ui.showInfoToast(isEn() ? "No friendly core found!" : "Không tìm thấy lõi phe ta!", 2f);
        }
    }

    static void applyFpsOptimizationLevel(int percent) {
        Core.settings.put("newex-fps-opt-percent", percent);
        fpsOptPercentSetting = percent;
        boolean effects, destroyed, bloom, hits;
        if (percent >= 90) { effects = false; destroyed = false; bloom = false; hits = false; }
        else if (percent >= 50) { effects = false; destroyed = false; bloom = false; hits = true; }
        else if (percent >= 20) { effects = true; destroyed = false; bloom = true; hits = true; }
        else { effects = true; destroyed = true; bloom = true; hits = true; }
        Core.settings.put("effects", effects);
        Core.settings.put("destroyedblocks", destroyed);
        Core.settings.put("bloom", bloom);
        Core.settings.put("hits", hits);
    }

    static boolean canUseHud() { return !Vars.mobile && !Vars.net.client(); }

    static void centerWaveButton() {
        if (!canUseHud() || customWaveContainer == null) return;
        float sw = Core.graphics.getWidth(), sh = Core.graphics.getHeight();
        float cw = customWaveContainer.getWidth() != 0f ? customWaveContainer.getWidth() : 160f;
        float ch = customWaveContainer.getHeight() != 0f ? customWaveContainer.getHeight() : 40f;
        btnX = (sw - cw) / 2f;
        btnY = (sh - ch) / 2f;
        customWaveContainer.setPosition(btnX, btnY);
        Core.settings.put("newex-wave-btn-x", btnX);
        Core.settings.put("newex-wave-btn-y", btnY);
    }

    // ---- Nút HUD kéo thả (Gọi Wave + bảng mở rộng) ----
    static TextButton hudBtn(String text, TextButtonStyle style, float fontScale, Color labelColor) {
        TextButton b = new TextButton(text, style);
        b.getLabel().setFontScale(fontScale);
        if (labelColor != null) b.getLabel().setColor(labelColor);
        return b;
    }

    static void addDragAndClick(Element actor, final Table container, final Runnable onClick) {
        final boolean[] dragging = {false};
        final float[] off = {0f, 0f};
        actor.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                dragging[0] = false;
                off[0] = x;
                off[1] = y;
                return true;
            }
            @Override public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (Math.abs(x - off[0]) > 5f || Math.abs(y - off[1]) > 5f) dragging[0] = true;
                if (dragging[0]) {
                    float rawX = container.x + x - off[0], rawY = container.y + y - off[1];
                    float maxX = Math.max(0f, Core.graphics.getWidth() - container.getWidth());
                    float maxY = Math.max(0f, Core.graphics.getHeight() - container.getHeight());
                    float nx = Mathf.clamp(rawX, 0f, maxX), ny = Mathf.clamp(rawY, 0f, maxY);
                    container.setPosition(nx, ny);
                    btnX = nx;
                    btnY = ny;
                    Core.settings.put("newex-wave-btn-x", btnX);
                    Core.settings.put("newex-wave-btn-y", btnY);
                }
            }
            @Override public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (!dragging[0]) onClick.run();
                dragging[0] = false;
            }
        });
    }

    static void injectWaveButtonToHUD() {
        if (waveBtnAdded || !canUseHud()) return;
        if (Vars.ui == null || Vars.ui.hudGroup == null) return;

        final Table container = new Table();
        container.setPosition(btnX, btnY);

        Table topBar = new Table();
        topBar.setBackground(Tex.whiteui);
        topBar.setColor(Color.black);

        final TextButton btnWave = hudBtn(isEn() ? "Call Wave" : "Gọi Quái Wave", Styles.cleart, 0.8f, null);
        btnWave.setBackground(Tex.whiteui);
        btnWave.setColor(Color.valueOf("4a4a4a"));

        final TextButton btnToggleExpand = hudBtn("v", Styles.cleart, 0.8f, null);
        btnToggleExpand.setBackground(Tex.whiteui);
        btnToggleExpand.setColor(Color.valueOf("3a3a3a"));

        topBar.add(btnWave).size(130f, 38f).pad(2f);
        topBar.add(btnToggleExpand).size(30f, 38f).pad(2f);
        container.add(topBar).row();

        final Table expandTable = new Table();
        expandTable.setBackground(Tex.whiteui);
        expandTable.setColor(Color.valueOf("222222"));
        expandTable.visible = false;
        expandTable.margin(6f);

        int curFps = fpsOptPercentSetting;
        final Label optLabel = expandTable.add(isEn() ? "FPS Opt: " + curFps + "%" : "Tối ưu FPS: " + curFps + "%").fontScale(0.75f).get();
        expandTable.row();
        expandTable.slider(0f, 100f, 1f, curFps, value -> {
            int val = (int) Math.floor(value);
            optLabel.setText(isEn() ? "FPS Opt: " + val + "%" : "Tối ưu FPS: " + val + "%");
            applyFpsOptimizationLevel(val);
        }).width(150f).pad(4f).row();

        final TextButton autoFpsBtn = hudBtn(isEn() ? "Auto hide effects & Slow-mo x0.8 when FPS < 20" : "Tự ẩn effect & Slow motion x0.8 khi FPS < 20", Styles.togglet, 0.65f, null);
        autoFpsBtn.setChecked(autoLowFpsSetting);
        autoFpsBtn.clicked(() -> {
            boolean checked = autoFpsBtn.isChecked();
            Core.settings.put("newex-auto-low-fps", checked);
            autoLowFpsSetting = checked;
        });
        expandTable.add(autoFpsBtn).size(150f, 36f).pad(2f).row();

        final TextButton btnWave10 = hudBtn(isEn() ? "x10 Call Wave" : "x10 Gọi Wave", Styles.flatTogglet, 0.75f, Color.lime);
        btnWave10.clicked(() -> triggerMultipleWaves(10, btnWave10));
        expandTable.add(btnWave10).size(150f, 36f).pad(2f).row();

        final TextButton btnSelfDestruct = hudBtn(isEn() ? "Self-Destruct" : "Tự hủy lõi", Styles.flatTogglet, 0.75f, Color.red);
        btnSelfDestruct.clicked(() -> selfDestructCores(btnSelfDestruct));
        expandTable.add(btnSelfDestruct).size(150f, 36f).pad(2f).row();

        final TextButton btnMeteor = hudBtn(isEn() ? "Meteor" : "TT rơi", Styles.flatTogglet, 0.75f, Color.orange);
        btnMeteor.clicked(() -> triggerRandomMeteor(btnMeteor));
        expandTable.add(btnMeteor).size(150f, 36f).pad(2f).row();

        final TextButton btnClearER = hudBtn(isEn() ? "Clear E" : "Clear E R", Styles.flatTogglet, 0.75f, Color.valueOf("ff5555"));
        btnClearER.clicked(() -> {
            countClearER++;
            btnClearER.setText((isEn() ? "Clear E" : "Clear E R") + " (" + countClearER + ")");
            clearEnemyUnitsPercent(99);
        });
        expandTable.add(btnClearER).size(150f, 36f).pad(2f).row();

        final TextButton btnDeleteOre = hudBtn(isEn() ? "Del Ore" : "Xóa mỏ", Styles.flatTogglet, 0.75f, Color.valueOf("80ffea"));
        btnDeleteOre.clicked(() -> deleteRandomOreDeposit(btnDeleteOre));
        expandTable.add(btnDeleteOre).size(150f, 36f).pad(2f).row();

        final TextButton btnRandomSpawnE = hudBtn(isEn() ? "Random Spawn E" : "Spawn ngẫu nhiên E", Styles.flatTogglet, 0.7f, Color.valueOf("da70d6"));
        btnRandomSpawnE.clicked(() -> triggerRandomSpawnEnemy(btnRandomSpawnE));
        expandTable.add(btnRandomSpawnE).size(150f, 36f).pad(2f).row();

        final TextButton btnWipe = hudBtn(isEn() ? "Wipe Base" : "Phá hết công trình", Styles.flatTogglet, 0.7f, Color.valueOf("ff3333"));
        btnWipe.clicked(() -> triggerWipeAll(btnWipe));
        expandTable.add(btnWipe).size(150f, 36f).pad(2f).row();

        final TextButton btnMega = hudBtn("Spawn Mega", Styles.flatTogglet, 0.75f, Color.valueOf("7fffd4"));
        btnMega.clicked(() -> triggerSpawnMega(btnMega));
        expandTable.add(btnMega).size(150f, 36f).pad(2f).row();

        hudButtons.clear();
        hudButtons.put("wave", btnWave);
        hudButtons.put("wave10", btnWave10);
        hudButtons.put("meteor", btnMeteor);
        hudButtons.put("spawnE", btnRandomSpawnE);
        hudButtons.put("wipe", btnWipe);
        hudButtons.put("mega", btnMega);

        container.add(expandTable).padTop(2f).row();

        addDragAndClick(btnWave, container, () -> triggerNextWave(btnWave));
        addDragAndClick(btnToggleExpand, container, () -> {
            expandTable.visible = !expandTable.visible;
            btnToggleExpand.setText(expandTable.visible ? "^" : "v");
        });

        Vars.ui.hudGroup.addChild(container);
        customWaveContainer = container;
        waveBtnAdded = true;
    }

    static void showReadmeDialog() {
        String content = isEn() ? "README.md not found in mod." : "Không tìm thấy file README.md trong mod.";
        LoadedMod mod = Vars.mods.getMod(CURRENT_MOD_NAME);
        if (mod != null && mod.root != null) {
            Fi readme = mod.root.child("README.md");
            if (readme.exists()) content = readme.readString();
        }
        BaseDialog dialog = new BaseDialog(isEn() ? "Mod Information (README)" : "Thông Tin Mod (README)");
        dialog.setFillParent(true);
        dialog.cont.clear();
        Table table = new Table();
        table.top().left().margin(12f);
        Label label = table.add(content).growX().get();
        label.setWrap(true);
        label.setAlignment(Align.left);
        ScrollPane pane = new ScrollPane(table);
        pane.setFadeScrollBars(false);
        dialog.cont.add(pane).grow().row();
        dialog.addCloseButton();
        dialog.show();
    }

    // ---- Hộp thoại chọn pháo ----
    static void saveSelectionAndApply() {
        Seq<String> allowed = new Seq<>();
        List<String> saved = new ArrayList<>();
        selectedTurrets.each(b -> { saved.add(b.name); allowed.add(b.name); });
        if (Vars.state.rules != null && Vars.state.rules.tags != null) {
            Vars.state.rules.tags.put(TAG_KEY, String.join(",", saved));
        }
        applyTurretVisibility(allowed);
    }

    static void showTurretSelectionDialog() {
        selectedTurrets.clear();
        final int maxCount = getMaxSelectCount();

        final BaseDialog dialog = new BaseDialog(isEn() ? "Select Turrets (" + CURRENT_MOD_NAME + ")" : "Chọn Tháp Pháo (" + CURRENT_MOD_NAME + ")");
        dialog.setFillParent(true);
        Table contentTable = dialog.cont;
        contentTable.clear();

        Label title = contentTable.add(isEn() ? "Please select up to " + maxCount + " unlocked turrets for this match:"
            : "Vui lòng chọn tối đa " + maxCount + " tháp pháo đã mở khóa cho trận đấu này:").pad(8f).get();
        title.setWrap(true);
        title.setAlignment(Align.center);
        contentTable.row();

        Table selectionTable = new Table();
        selectionTable.top().margin(10f);

        final boolean isMobile = Core.graphics.isPortrait() || Vars.mobile;
        final int maxCols = isMobile ? 2 : 3;
        final int[] cols = {0};

        final Seq<String> unlocked = Shop.getUnlockedTurrets(turretList);
        final boolean gacha = Core.settings.getBool("newex-gacha-enabled", false);

        turretList.each(block -> {
            boolean isUnlocked = gacha ? unlocked.contains(block.name) : true;
            if (!isUnlocked) return;
            final TextButton btn = new TextButton(block.localizedName, Styles.togglet);
            btn.getLabel().setWrap(true);
            btn.getLabel().setFontScale(isMobile ? 0.8f : 0.9f);
            btn.clicked(() -> {
                if (btn.isChecked()) {
                    if (selectedTurrets.size < maxCount) {
                        selectedTurrets.add(block);
                    } else {
                        btn.setChecked(false);
                        Vars.ui.showInfo(isEn() ? "Can only select up to " + maxCount + " turrets!" : "Chỉ được chọn tối đa " + maxCount + " tháp pháo!");
                    }
                } else {
                    selectedTurrets.remove(block);
                }
            });
            selectionTable.add(btn).growX().height(isMobile ? 55f : 50f).pad(4f);
            cols[0]++;
            if (cols[0] % maxCols == 0) selectionTable.row();
        });

        ScrollPane pane = new ScrollPane(selectionTable);
        pane.setFadeScrollBars(false);
        contentTable.add(pane).grow().row();

        Table buttons = new Table();
        buttons.button(isEn() ? "Confirm & Sync" : "Xác nhận & Đồng bộ", () -> {
            if (selectedTurrets.size != maxCount) {
                Vars.ui.showInfo(isEn() ? "You need to select exactly " + maxCount + " turrets!" : "Bạn cần chọn đúng " + maxCount + " tháp pháo!");
                return;
            }
            saveSelectionAndApply();
            dialog.hide();
        }).size(180f, 50f).pad(6f);
        buttons.button(isEn() ? "Exit to Battle" : "Thoát vào trận", () -> {
            saveSelectionAndApply();
            dialog.hide();
        }).size(160f, 50f).pad(6f);
        contentTable.add(buttons).pad(10f);
        dialog.show();
    }

    // ---- Cầu nối cho newmode-pvp.js (chưa chuyển): thay cho global.NexPvp.addSettings(content) ----
    /** Nhận bảng cài đặt, trả về hàm "lưu" (hoặc null). Gán từ lớp PVP khi chuyển newmode-pvp.js. */
    public static java.util.function.Function<Table, Runnable> pvpSettingsHook = null;

    static TextButton toggleBtn(String text, float scale, boolean checked) {
        TextButton b = new TextButton(text, Styles.togglet);
        b.getLabel().setFontScale(scale);
        b.setChecked(checked);
        return b;
    }

    static Cell<TextField> numField(Table parent, String init, int maxLen) {
        Cell<TextField> c = parent.field(init, text -> {}).width(120f);
        c.get().setFilter(TextField.TextFieldFilter.digitsOnly);
        c.get().setMaxLength(maxLen);
        return c;
    }

    // ---- Hộp thoại cài đặt ----
    static void showConfigDialog() {
        if (turretList.isEmpty()) loadTurretsFromFolder();

        final BaseDialog dialog = new BaseDialog(isEn() ? "Newex Mod Settings" : "Cài Đặt Mod Newex");
        dialog.cont.clear();

        Table content = new Table();
        content.top().margin(10f);

        content.add(isEn() ? "[accent]-- GACHA SYSTEM --[]" : "[accent]-- CƠ CHẾ GACHA --[]").row();
        final TextButton btnGacha = toggleBtn(isEn() ? "Enable/Disable Gacha\n[gray](When disabled: Don't hide locked turrets)[]" : "Bật/Tắt cơ chế Gacha\n[gray](Khi tắt: Không ẩn pháo chưa mở khóa)[]", 0.8f, Core.settings.getBool("newex-gacha-enabled", false));
        content.add(btnGacha).size(280f, 54f).pad(4f).row();

        final TextButton btnAllow = toggleBtn(isEn() ? "Let other players use Wave/Meteor/Del Ore buttons\n[gray](Applies when you host a server)[]" : "Cho phép người chơi khác dùng nút Wave/TT rơi/Xóa mỏ\n[gray](Áp dụng khi bạn làm chủ server)[]", 0.8f, Core.settings.getBool("newex-allow-client-actions", true));
        content.add(btnAllow).size(280f, 54f).pad(4f).row();

        content.add(isEn() ? "[accent]-- ORE DISPLAY SETTINGS --[]" : "[accent]-- CÀI ĐẶT HIỂN THỊ MỎ KHOÁNG --[]").padTop(10f).row();
        final TextButton btnHideOres = toggleBtn(isEn() ? "Hide Ore Deposits\n[gray](Beryi Drills can still scan & mine)[]" : "Ẩn bãi khoáng sản\n[gray](Khoan Beryi vẫn quét & đào được)[]", 0.8f, Core.settings.getBool("newex-hide-ores", false));
        content.add(btnHideOres).size(280f, 54f).pad(4f).row();

        content.add(isEn() ? "[accent]-- TURRET LIMIT --[]" : "[accent]-- GIỚI HẠN THÁP PHÁO --[]").padTop(10f).row();
        content.add(isEn() ? "Number of turrets selectable per match:" : "Số lượng tháp pháo chọn mỗi trận:").padBottom(5f).row();
        int currentLimit = getMaxSelectCount();
        final Label textLabel = content.add(currentLimit >= turretList.size ? (isEn() ? "All (Full)" : "Tất cả (Full)") : String.valueOf(currentLimit)).fontScale(1.3f).get();
        content.row();
        int maxSliderVal = Math.max(1, turretList.size);
        final Slider slider = content.slider(1f, maxSliderVal, 1f, Math.min(currentLimit, maxSliderVal), value -> {
            int val = (int) Math.floor(value);
            textLabel.setText(val >= turretList.size ? (isEn() ? "All (Full)" : "Tất cả (Full)") : String.valueOf(val));
        }).width(240f).pad(8f).get();
        content.row();

        content.add(isEn() ? "[accent]-- FPS / GRAPHICS OPTIMIZATION --[]" : "[accent]-- TỐI ƯU FPS / ĐỒ HỌA --[]").padTop(10f).row();
        content.add(isEn() ? "Effect reduction level (1%):" : "Mức độ cắt giảm hiệu ứng (1%):").padBottom(4f).row();
        int curFps = fpsOptPercentSetting;
        final Label fpsLabel = content.add(curFps + "%").fontScale(1.2f).get();
        content.row();
        final Slider fpsSlider = content.slider(0f, 100f, 1f, curFps, value -> fpsLabel.setText((int) Math.floor(value) + "%")).width(240f).pad(8f).get();
        content.row();

        final TextButton btnAutoFps = toggleBtn(isEn() ? "Auto hide effects & Slow-mo x0.8 when FPS < 20" : "Tự ẩn effect & Slow motion x0.8 khi FPS < 20", 0.8f, autoLowFpsSetting);
        content.add(btnAutoFps).size(280f, 48f).pad(4f).row();

        content.add(isEn() ? "[accent]-- ENERGY BLOCK GRAPHICS (REDSTONE / CONDUIT) --[]" : "[accent]-- ĐỒ HỌA KHỐI NĂNG LƯỢNG (REDSTONE / COMDUIK) --[]").padTop(10f).row();
        final TextButton btnRedstoneFx = toggleBtn(isEn() ? "Full Redstone/Conduit Effects\n[gray](Disable to boost FPS when mass building)[]" : "Hiệu ứng Redstone/Comduik đầy đủ\n[gray](Tắt đi để tăng FPS khi xây hàng loạt)[]", 0.8f, Core.settings.getBool("newex-redstone-fx", false));
        content.add(btnRedstoneFx).size(280f, 54f).pad(4f).row();

        content.add(isEn() ? "[accent]-- ENEMY STATS PER WAVE --[]" : "[accent]-- CHỈ SỐ ĐỊCH THEO TỪNG WAVE --[]").padTop(10f).row();
        content.add(isEn() ? "% HP increase per Wave:" : "% Máu tăng thêm trên mỗi Wave:").padBottom(4f).row();
        Table hpTable = new Table();
        final TextField hpField = numField(hpTable, String.valueOf(hpPerWavePercentSetting), 3).get();
        hpTable.add("% / Wave").padLeft(8f);
        content.add(hpTable).pad(5f).row();

        content.add(isEn() ? "[accent]-- SURVIVAL LEVELING --[]" : "[accent]-- CẤP ĐỘ & EXP CHẾ ĐỘ SURVIVAL --[]").padTop(10f).row();
        content.add(isEn() ? "Max level:" : "Level tối đa:").padBottom(4f).row();
        Table maxLvTable = new Table();
        final TextField maxLvField = numField(maxLvTable, String.valueOf(Core.settings.getInt("newex-survival-max-level", 10)), 3).get();
        maxLvTable.add(isEn() ? "levels (1 - 999)" : "cấp (1 - 999)").padLeft(8f);
        content.add(maxLvTable).pad(5f).row();

        content.add(isEn() ? "EXP received (%):" : "% EXP nhận vào:").padBottom(4f).row();
        Table expTable = new Table();
        final TextField expField = numField(expTable, String.valueOf(Core.settings.getInt("newex-survival-exp-percent", 100)), 4).get();
        expTable.add(isEn() ? "% (100 = normal)" : "% (100 = bình thường)").padLeft(8f);
        content.add(expTable).pad(5f).row();

        final TextButton btnExpByHp = toggleBtn(isEn() ? "EXP scales with target HP\n[gray](Tougher enemies give more EXP)[]" : "Tăng EXP theo máu mục tiêu\n[gray](Địch càng nhiều máu càng cho nhiều EXP)[]", 0.8f, Core.settings.getBool("newex-survival-exp-by-hp", false));
        content.add(btnExpByHp).size(280f, 54f).pad(4f).row();

        final Runnable pvpSave = pvpSettingsHook != null ? pvpSettingsHook.apply(content) : null;

        content.add(isEn() ? "[accent]-- HP BAR DISPLAY MODE --[]" : "[accent]-- CHẾ ĐỘ HIỂN THỊ THANH MÁU (HP) --[]").padTop(10f).row();
        String curStyle = Core.settings.getString("newex-hp-style", "show-hp");
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);
        Table tableHp = new Table();
        final TextButton btnShowHp = new TextButton(isEn() ? "Enable HP\n[gray](Horizontal + number)[]" : "Bật hp\n[gray](Ngang + số)[]", Styles.togglet);
        final TextButton btnHp = new TextButton(isEn() ? "Enable HP\n[gray](Classic vertical)[]" : "Bật hp\n[gray](Dọc cổ điển)[]", Styles.togglet);
        final TextButton btnOff = new TextButton(isEn() ? "Disable HP" : "Tắt HP", Styles.togglet);
        btnShowHp.getLabel().setFontScale(0.8f);
        btnHp.getLabel().setFontScale(0.8f);
        btnOff.getLabel().setFontScale(0.8f);
        group.add(btnShowHp);
        group.add(btnHp);
        group.add(btnOff);
        if (curStyle.equals("show-hp")) btnShowHp.setChecked(true);
        else if (curStyle.equals("hp")) btnHp.setChecked(true);
        else btnOff.setChecked(true);
        tableHp.add(btnShowHp).size(145f, 54f).pad(3f);
        tableHp.add(btnHp).size(145f, 54f).pad(3f);
        tableHp.add(btnOff).size(85f, 54f).pad(3f);
        content.add(tableHp).row();

        content.add(isEn() ? "[accent]-- DETAILED INFORMATION --[]" : "[accent]-- THÔNG TIN CHI TIẾT --[]").padTop(10f).row();
        content.button(isEn() ? "View README / Update Log" : "Xem README / Update Log", Icon.info, LoadoutSelection::showReadmeDialog).size(240f, 48f).pad(5f).row();

        content.button(isEn() ? "Save Settings" : "Lưu Cài Đặt", () -> {
            Core.settings.put("newex-gacha-enabled", btnGacha.isChecked());
            Core.settings.put("newex-allow-client-actions", btnAllow.isChecked());

            boolean oldHide = Core.settings.getBool("newex-hide-ores", false);
            boolean newHide = btnHideOres.isChecked();
            Core.settings.put("newex-hide-ores", newHide);
            if (oldHide != newHide) applyOreHidingState(newHide);

            Core.settings.put("newex-max-turrets", (int) Math.floor(slider.getValue()));
            applyFpsOptimizationLevel((int) Math.floor(fpsSlider.getValue()));
            Core.settings.put("newex-auto-low-fps", btnAutoFps.isChecked());
            Core.settings.put("newex-redstone-fx", btnRedstoneFx.isChecked());

            int hp = Mathf.clamp(Strings.parseInt(hpField.getText(), 0), 0, 999);
            Core.settings.put("newex-hp-per-wave-percent", hp);

            int maxLv = Strings.parseInt(maxLvField.getText(), 0); // JS: NaN hoặc <1 -> 1
            if (maxLv < 1) maxLv = 1;
            if (maxLv > 999) maxLv = 999;
            Core.settings.put("newex-survival-max-level", maxLv);

            int expPct = Strings.parseInt(expField.getText(), -1); // JS: NaN hoặc <0 -> 100
            if (expPct < 0) expPct = 100;
            if (expPct > 1000) expPct = 1000;
            Core.settings.put("newex-survival-exp-percent", expPct);

            Core.settings.put("newex-survival-exp-by-hp", btnExpByHp.isChecked());
            if (pvpSave != null) pvpSave.run();

            String style = "off";
            if (btnShowHp.isChecked()) style = "show-hp";
            else if (btnHp.isChecked()) style = "hp";
            Core.settings.put("newex-hp-style", style);

            reloadCachedSettings();
            applyTurretVisibility(null);
            Vars.ui.showInfo(isEn() ? "Newex settings saved successfully!" : "Đã lưu cài đặt Newex thành công!");
            dialog.hide();
        }).size(170f, 45f).padTop(12f);

        ScrollPane pane = new ScrollPane(content);
        pane.setFadeScrollBars(false);
        dialog.cont.add(pane).grow().row();
        dialog.addCloseButton();
        dialog.show();
    }

    // ---- Buff địch theo wave & sự kiện ----
    static void applyEnemyBuffs(Unit unit) {
        if (unit == null || unit.team == Vars.state.rules.defaultTeam) return;
        int wave = Vars.state.wave;
        if (wave <= 1) return;
        int hpPercent = hpPerWavePercentSetting;
        if (hpPercent <= 0) return;
        float mult = 1f + ((wave - 1) * (hpPercent / 100f));
        if (mult > 1f) {
            unit.maxHealth *= mult;
            unit.health = unit.maxHealth;
        }
    }

    static void update() {
        boolean inGame = Vars.state != null && Vars.state.isGame();

        if (targetCamX >= 0 && targetCamY >= 0) {
            panTimer += Core.graphics.getDeltaTime();
            float alpha = Math.min(panTimer / panDuration, 1f);
            float smooth = Interp.pow3Out.apply(alpha);
            Core.camera.position.set(Mathf.lerp(panStartX, targetCamX, smooth), Mathf.lerp(panStartY, targetCamY, smooth));
            if (alpha >= 1f) { targetCamX = -1f; targetCamY = -1f; }
        }

        if (inGame) {
            if (autoLowFpsSetting) {
                int fps = Core.graphics.getFramesPerSecond();
                if (fps < 20 && !isFpsThrottled) {
                    autoFpsSavedEffects = Core.settings.getBool("effects", true);
                    Core.settings.put("effects", false);
                    Time.setDeltaProvider(LoadoutSelection::slowDelta);
                    isFpsThrottled = true;
                } else if (fps > 45 && isFpsThrottled) {
                    Core.settings.put("effects", autoFpsSavedEffects);
                    Time.setDeltaProvider(LoadoutSelection::normalDelta);
                    isFpsThrottled = false;
                }
            }

            if (canUseHud()) {
                boolean shift = !Vars.mobile && (Core.input.keyDown(KeyCode.shiftLeft) || Core.input.keyDown(KeyCode.shiftRight));
                if (shift && Core.input.keyTap(KeyCode.n)) {
                    showWaveBtnSetting = !showWaveBtnSetting;
                    Core.settings.put("newex-show-wave-btn", showWaveBtnSetting);
                    if (showWaveBtnSetting) {
                        Vars.ui.showInfoToast(isEn() ? "Wave button shown" : "Đã hiện nút Gọi Wave", 1.5f);
                        centerWaveButton();
                    } else {
                        Vars.ui.showInfoToast(isEn() ? "Wave button hidden" : "Đã ẩn nút Gọi Wave", 1.5f);
                    }
                }

                // --- PHÍM TẮT 1-6 ---
                boolean typing = Core.scene.hasKeyboard() || Core.scene.hasDialog()
                    || (Vars.ui.chatfrag != null && Vars.ui.chatfrag.shown());
                if (!typing && !shift) {
                    if (hotkeyTapped(HK_WAVE)) triggerNextWave(hudButtons.get("wave"));
                    if (hotkeyTapped(HK_METEOR)) triggerRandomMeteor(hudButtons.get("meteor"));
                    if (hotkeyTapped(HK_WAVE10)) triggerMultipleWaves(10, hudButtons.get("wave10"));
                    if (hotkeyTapped(HK_SPAWNE)) triggerRandomSpawnEnemy(hudButtons.get("spawnE"));
                    if (hotkeyTapped(HK_WIPE)) triggerWipeAll(hudButtons.get("wipe"));
                    if (hotkeyTapped(HK_MEGA)) triggerSpawnMega(hudButtons.get("mega"));
                }

                if (showWaveBtnSetting) {
                    if (!waveBtnAdded) injectWaveButtonToHUD();
                    if (customWaveContainer != null) customWaveContainer.visible = true;
                } else if (customWaveContainer != null) {
                    customWaveContainer.visible = false;
                }
            } else if (customWaveContainer != null) {
                customWaveContainer.visible = false;
            }
        } else {
            if (isFpsThrottled) {
                Time.setDeltaProvider(LoadoutSelection::normalDelta);
                isFpsThrottled = false;
            }
            if (customWaveContainer != null) customWaveContainer.visible = false;
        }
    }

    static void onWorldLoad() {
        waveBtnAdded = false;
        hudButtons.clear();
        wipeArmedUntil = 0;
        if (customWaveContainer != null) {
            customWaveContainer.remove();
            customWaveContainer = null;
        }
        if (turretList.isEmpty()) loadTurretsFromFolder();

        // Tự ẩn/khôi phục quặng theo cài đặt hiện tại
        Time.run(10f, () -> applyOreHidingState(Core.settings.getBool("newex-hide-ores", false)));

        boolean sandbox = Vars.state.rules != null && (Vars.state.rules.infiniteResources || Vars.state.rules.editor);
        boolean gacha = Core.settings.getBool("newex-gacha-enabled", false);
        if (sandbox) {
            turretList.each(b -> b.buildVisibility = BuildVisibility.shown);
            return;
        }

        if (getMaxSelectCount() >= turretList.size) {
            Seq<String> unlocked = Shop.getUnlockedTurrets(turretList);
            List<String> saved = new ArrayList<>();
            Seq<String> allowed = new Seq<>();
            turretList.each(b -> {
                boolean isUnlocked = gacha ? unlocked.contains(b.name) : true;
                if (isUnlocked) { saved.add(b.name); allowed.add(b.name); }
            });
            if (Vars.state.rules != null && Vars.state.rules.tags != null) {
                Vars.state.rules.tags.put(TAG_KEY, String.join(",", saved));
            }
            applyTurretVisibility(allowed);
            return;
        }

        turretList.each(b -> b.buildVisibility = BuildVisibility.hidden);
        Core.app.post(LoadoutSelection::showTurretSelectionDialog);
    }

    /** Gọi từ NexMod (thay require("loadout-selection") của main.js). */
    public static void init() {
        initMeteorNet();
        initActionNet();
        initWaveNet();

        Events.on(UnitCreateEvent.class, e -> { if (e.unit != null) applyEnemyBuffs(e.unit); });

        Events.on(UnitDestroyEvent.class, e -> {
            if (e.unit == null || Vars.state == null || !Vars.state.isGame()) return;
            if (!Core.settings.getBool("newex-is-playing-newmode", false)) return;
            if (e.unit.team != Vars.state.rules.defaultTeam) {
                int kills = Core.settings.getInt("newex-enemy-kill-count", 0) + 1;
                if (kills >= 100) {
                    kills -= 100;
                    Shop.saveGachaPoints(Shop.getGachaPoints() + 1);
                    Vars.ui.showInfoToast(isEn() ? "[green]+1 Gacha Point! (Killed 100 enemies)[]" : "[green]+1 điểm Gacha! (Tiêu diệt đủ 100 địch)[]", 2f);
                }
                Core.settings.put("newex-enemy-kill-count", kills);
            }
        });

        Events.on(ClientLoadEvent.class, e -> {
            loadTurretsFromFolder();
            reloadCachedSettings();
            applyFpsOptimizationLevel(fpsOptPercentSetting);
            try {
                Vars.ui.menufrag.addButton(isEn() ? "Turret Shop" : "Cửa Hàng Tháp Pháo (Shop)", Icon.star,
                    () -> Shop.showShopDialog(turretList, LoadoutSelection::loadTurretsFromFolder));
                Vars.ui.menufrag.addButton(isEn() ? "Newex Settings" : "Cài đặt Newex", Icon.settings, LoadoutSelection::showConfigDialog);
            } catch (Throwable ignored) {}
        });

        Events.run(Trigger.update, LoadoutSelection::update);
        Events.on(WorldLoadEvent.class, e -> onWorldLoad());
    }
}
