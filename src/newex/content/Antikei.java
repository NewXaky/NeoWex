package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.core.World;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chuyển từ antikei-logic.js. Sàn "antikei" là dòng chảy điều hướng: đơn vị mặt đất đứng trên sàn bị đẩy theo hướng
 * dòng chảy (do người thiết kế map vẽ trong editor, hoặc tự tính bằng BFS về phía lõi); ở ngoài sàn thì tiến về sàn gần nhất.
 * Trong editor: kéo chuột để vẽ hướng, giữ R + chuột để xóa; chuột giữa xoay 90 độ.
 */
public final class Antikei {
    private static Block antikeiBlock;
    private static boolean mapHasAntikei = false;

    private static final Map<String, Float> flowDirectionMap = new HashMap<>();
    private static final Map<String, Float> autoFlowMap = new HashMap<>();
    private static final List<String> antikeiTilesList = new ArrayList<>();

    private static Tile lastEditorTile = null;
    private static int lastCoreCount = -1;
    private static int frameCount = 0;
    private static int antikeiDrawVersion = 0;
    private static int drawCacheVersion = -1;
    private static int drawCacheLen = -1;
    private static float[] drawXs = new float[0];
    private static float[] drawYs = new float[0];
    private static String[] drawKeys = new String[0];

    private static final Pattern FLOW_ENTRY = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?[0-9.eE+\\-]+)");

    private static final class NearestEntry {
        final Tile t;
        final int f;

        NearestEntry(Tile t, int f) {
            this.t = t;
            this.f = f;
        }
    }

    private static final class TargetEntry {
        final Teamc t;
        final int f;

        TargetEntry(Teamc t, int f) {
            this.t = t;
            this.f = f;
        }
    }

    private static final class AngleEntry {
        final float a;
        final int f;

        AngleEntry(float a, int f) {
            this.a = a;
            this.f = f;
        }
    }

    private static Map<Integer, NearestEntry> nearestCache = new HashMap<>();
    private static Map<Integer, TargetEntry> unitTargetCache = new HashMap<>();
    private static Map<String, AngleEntry> angleCache = new HashMap<>();

    /** Team.enemy() có thể không tồn tại ở bản game này (JS gốc bọc try/catch); tra bằng reflection để giữ nguyên hành vi. */
    private static final Method TEAM_ENEMY;

    static {
        Method m;
        try {
            m = Team.class.getMethod("enemy");
        } catch (Throwable err) {
            m = null;
        }
        TEAM_ENEMY = m;
    }

    private Antikei() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> antikeiBlock = Vars.content.block("newex-antikei"));

        Events.on(WorldLoadEvent.class, e -> {
            cacheAntikeiTiles();
            if (mapHasAntikei) {
                clearOresOnAntikei();
                loadFlowData();
            }
        });

        Events.run(Trigger.update, Antikei::update);
        Events.run(Trigger.draw, Antikei::draw);
    }

    // ------------------------------------------------------------------ tiện ích

    private static Tile findNearestAntikeiCached(Unit unit) {
        NearestEntry e = nearestCache.get(unit.id);
        if (e != null && frameCount - e.f < 20) return e.t;
        Tile t = findNearestAntikeiFast(unit);
        nearestCache.put(unit.id, new NearestEntry(t, frameCount - (unit.id % 10)));
        return t;
    }

    private static String getTileKey(int x, int y) {
        return x + "_" + y;
    }

    private static String getMapId() {
        if (Vars.state == null) return "default_map";
        if (Vars.state.map != null && Vars.state.map.name() != null) {
            return String.valueOf(Vars.state.map.name()).replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        return "editor_test_map";
    }

    private static int getTotalCores() {
        int total = 0;
        if (Vars.state == null || Vars.state.teams == null) return 0;
        for (int i = 0; i < Team.all.length; i++) {
            Team t = Team.all[i];
            TeamData tData = Vars.state.teams.get(t);
            if (tData != null && tData.cores != null) {
                total += tData.cores.size;
            }
        }
        return total;
    }

    private static void saveFlowData() {
        if (Vars.state.isMenu()) return;
        String mapId = "antikei_map_" + getMapId();
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Float> entry : flowDirectionMap.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(entry.getKey()).append("\":").append((double) entry.getValue());
        }
        sb.append('}');
        Core.settings.put(mapId, sb.toString());
    }

    private static void loadFlowData() {
        flowDirectionMap.clear();
        if (Vars.state.isMenu()) return;
        String mapId = "antikei_map_" + getMapId();
        String jsonStr = Core.settings.getString(mapId, "");
        if (!jsonStr.isEmpty()) {
            try {
                Matcher m = FLOW_ENTRY.matcher(jsonStr);
                while (m.find()) {
                    flowDirectionMap.put(m.group(1), Float.valueOf(Float.parseFloat(m.group(2))));
                }
            } catch (Throwable err) {
                Log.info("Error loading antikei flow data: " + err);
            }
        }
    }

    private static CoreBuild closestCoreOf(TeamData teamData, float x, float y) {
        if (teamData != null && teamData.cores != null && teamData.cores.size > 0) {
            CoreBuild closest = teamData.cores.first();
            float minDist = closest.dst2(x, y);
            for (int i = 0; i < teamData.cores.size; i++) {
                CoreBuild c = teamData.cores.get(i);
                float d = c.dst2(x, y);
                if (d < minDist) {
                    minDist = d;
                    closest = c;
                }
            }
            return closest;
        }
        return null;
    }

    private static CoreBuild getClosestCore(float x, float y, Team team) {
        try {
            if (TEAM_ENEMY != null) {
                Team enemyTeam = (Team) TEAM_ENEMY.invoke(team);
                CoreBuild c = closestCoreOf(Vars.state.teams.get(enemyTeam), x, y);
                if (c != null) return c;
            }
        } catch (Throwable ignored) {
        }

        try {
            TeamData playerTeamData = Vars.state.teams.get(Vars.player.team());
            if (playerTeamData != null && playerTeamData.cores != null && playerTeamData.cores.size > 0) {
                return playerTeamData.cores.first();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Tile findNearestAntikeiFast(Unit unit) {
        int uTileX = unit.tileX();
        int uTileY = unit.tileY();

        for (int r = 1; r <= 300; r++) {
            for (int dx = -r; dx <= r; dx++) {
                Tile tile1 = Vars.world.tile(uTileX + dx, uTileY - r);
                if (tile1 != null && tile1.floor() == antikeiBlock) return tile1;
                Tile tile2 = Vars.world.tile(uTileX + dx, uTileY + r);
                if (tile2 != null && tile2.floor() == antikeiBlock) return tile2;
            }
            for (int dy = -r + 1; dy <= r - 1; dy++) {
                Tile tile1 = Vars.world.tile(uTileX - r, uTileY + dy);
                if (tile1 != null && tile1.floor() == antikeiBlock) return tile1;
                Tile tile2 = Vars.world.tile(uTileX + r, uTileY + dy);
                if (tile2 != null && tile2.floor() == antikeiBlock) return tile2;
            }
        }
        return null;
    }

    private static int[] parseKey(String key) {
        int i = key.indexOf('_');
        return new int[]{Integer.parseInt(key.substring(0, i)), Integer.parseInt(key.substring(i + 1))};
    }

    /** Một ô trong hàng đợi BFS. */
    private static final class Node {
        final int x, y;
        final String key;

        Node(int x, int y, String key) {
            this.x = x;
            this.y = y;
            this.key = key;
        }
    }

    private static void cacheAntikeiTiles() {
        antikeiTilesList.clear();
        antikeiDrawVersion++;
        autoFlowMap.clear();
        mapHasAntikei = false;
        if (antikeiBlock == null || Vars.world == null) return;

        for (int x = 0; x < Vars.world.width(); x++) {
            for (int y = 0; y < Vars.world.height(); y++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile != null && tile.floor() == antikeiBlock) {
                    antikeiTilesList.add(getTileKey(x, y));
                    mapHasAntikei = true;
                }
            }
        }

        if (!mapHasAntikei) return;

        lastCoreCount = getTotalCores();

        ArrayDeque<Node> queue = new ArrayDeque<>();
        Map<String, Integer> distanceMap = new HashMap<>();

        for (int i = 0; i < Team.all.length; i++) {
            Team t = Team.all[i];
            TeamData tData = Vars.state.teams.get(t);
            if (tData != null && tData.cores != null) {
                for (int cIdx = 0; cIdx < tData.cores.size; cIdx++) {
                    CoreBuild core = tData.cores.get(cIdx);
                    int coreTileX = World.toTile(core.x);
                    int coreTileY = World.toTile(core.y);

                    int minDistToCore = -1;
                    List<Node> closestAntikeiForThisCore = new ArrayList<>();

                    for (int k = 0; k < antikeiTilesList.size(); k++) {
                        String key = antikeiTilesList.get(k);
                        int[] p = parseKey(key);
                        int tx = p[0], ty = p[1];
                        int dst = Math.abs(tx - coreTileX) + Math.abs(ty - coreTileY);

                        if (minDistToCore == -1 || dst < minDistToCore) {
                            minDistToCore = dst;
                            closestAntikeiForThisCore = new ArrayList<>();
                            closestAntikeiForThisCore.add(new Node(tx, ty, key));
                        } else if (dst == minDistToCore) {
                            closestAntikeiForThisCore.add(new Node(tx, ty, key));
                        }
                    }

                    for (int k = 0; k < antikeiTilesList.size(); k++) {
                        String key = antikeiTilesList.get(k);
                        int[] p = parseKey(key);
                        int tx = p[0], ty = p[1];
                        if (Math.abs(tx - coreTileX) <= 5 && Math.abs(ty - coreTileY) <= 5) {
                            Integer d = distanceMap.get(key);
                            if (d == null || d > 0) {
                                distanceMap.put(key, 0);
                                queue.add(new Node(tx, ty, key));
                            }
                        }
                    }

                    for (int c = 0; c < closestAntikeiForThisCore.size(); c++) {
                        Node item = closestAntikeiForThisCore.get(c);
                        if (distanceMap.get(item.key) == null) {
                            distanceMap.put(item.key, 0);
                            queue.add(item);
                        }
                    }
                }
            }
        }

        if (queue.isEmpty() && antikeiTilesList.size() > 0) {
            String firstKey = antikeiTilesList.get(0);
            int[] p = parseKey(firstKey);
            distanceMap.put(firstKey, 0);
            queue.add(new Node(p[0], p[1], firstKey));
        }

        final int[] dxs = {0, 0, 1, -1};
        final int[] dys = {1, -1, 0, 0};

        while (!queue.isEmpty()) {
            Node curr = queue.poll();
            int currDist = distanceMap.get(curr.key);

            for (int i = 0; i < 4; i++) {
                int nx = curr.x + dxs[i];
                int ny = curr.y + dys[i];
                String nKey = getTileKey(nx, ny);

                Tile tile = Vars.world.tile(nx, ny);
                if (tile != null && tile.floor() == antikeiBlock) {
                    if (distanceMap.get(nKey) == null) {
                        distanceMap.put(nKey, currDist + 1);
                        queue.add(new Node(nx, ny, nKey));
                    }
                }
            }
        }

        for (int i = 0; i < antikeiTilesList.size(); i++) {
            String key = antikeiTilesList.get(i);
            int[] p = parseKey(key);
            int x = p[0], y = p[1];
            Integer currentDist = distanceMap.get(key);
            if (currentDist == null) continue;

            int bestNx = x, bestNy = y;
            int minDst = currentDist;

            for (int j = 0; j < 4; j++) {
                int nx = x + dxs[j];
                int ny = y + dys[j];
                Integer nDist = distanceMap.get(getTileKey(nx, ny));

                if (nDist != null && nDist < minDst) {
                    minDst = nDist;
                    bestNx = nx;
                    bestNy = ny;
                }
            }

            if (bestNx != x || bestNy != y) {
                float worldX = x * Vars.tilesize + Vars.tilesize / 2f;
                float worldY = y * Vars.tilesize + Vars.tilesize / 2f;
                float targetX = bestNx * Vars.tilesize + Vars.tilesize / 2f;
                float targetY = bestNy * Vars.tilesize + Vars.tilesize / 2f;

                float angle = Angles.angle(worldX, worldY, targetX, targetY);
                autoFlowMap.put(key, Float.valueOf(angle));
            }
        }
    }

    private static void clearOresOnAntikei() {
        if (antikeiBlock == null || Vars.world == null) return;

        for (int i = 0; i < antikeiTilesList.size(); i++) {
            int[] p = parseKey(antikeiTilesList.get(i));
            Tile tile = Vars.world.tile(p[0], p[1]);
            if (tile != null && tile.overlay() != null && tile.overlay() != Blocks.air) {
                NexNet.setOverlay(tile, Blocks.air);
            }
        }
    }

    private static float getOptimalAngle(float worldX, float worldY, String key, Team unitTeam) {
        Float flow = flowDirectionMap.get(key);
        if (flow != null) return flow;
        Float auto = autoFlowMap.get(key);
        if (auto != null) return auto;
        CoreBuild core = getClosestCore(worldX, worldY, unitTeam);
        if (core != null) {
            return Angles.angle(worldX, worldY, core.x, core.y);
        }
        return 0f;
    }

    private static boolean isTargetValid(Teamc t) {
        if (t instanceof Unit) return ((Unit) t).isValid();
        if (t instanceof Building) return ((Building) t).isValid();
        return t != null;
    }

    // ------------------------------------------------------------------ cập nhật mỗi tick

    private static void update() {
        if (antikeiBlock == null || Vars.state.isMenu()) return;

        frameCount++;
        if (frameCount % 1800 == 0) {
            nearestCache = new HashMap<>();
            unitTargetCache = new HashMap<>();
        }

        if (Vars.state.isEditor() && !Core.input.keyDown(KeyCode.r)
            && (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.isTouched())) {
            Vec2 mouseVec = Core.camera.unproject(Core.input.mouse());
            Tile currentTile = Vars.world.tileWorld(mouseVec.x, mouseVec.y);

            if (currentTile != null && currentTile.floor() == antikeiBlock) {
                if (lastEditorTile != null && (lastEditorTile.x != currentTile.x || lastEditorTile.y != currentTile.y)) {
                    float dragAngle = Angles.angle(lastEditorTile.worldx(), lastEditorTile.worldy(), currentTile.worldx(), currentTile.worldy());

                    String lastKey = getTileKey(lastEditorTile.x, lastEditorTile.y);
                    String currentKey = getTileKey(currentTile.x, currentTile.y);

                    flowDirectionMap.put(lastKey, Float.valueOf(dragAngle));
                    flowDirectionMap.put(currentKey, Float.valueOf(dragAngle));
                    saveFlowData();
                } else {
                    String currentKey = getTileKey(currentTile.x, currentTile.y);
                    if (!flowDirectionMap.containsKey(currentKey)) {
                        flowDirectionMap.put(currentKey, Float.valueOf(0f));
                        saveFlowData();
                    }
                }
                lastEditorTile = currentTile;
            } else {
                lastEditorTile = null;
            }
        } else {
            lastEditorTile = null;
        }

        if (!mapHasAntikei) return;

        if (Vars.state.isPlaying() && frameCount % 60 == 0) {
            clearOresOnAntikei();
        }

        if (Vars.state.isPlaying() && frameCount % 30 == 0) {
            int currentCores = getTotalCores();
            if (lastCoreCount != -1 && currentCores < lastCoreCount) {
                cacheAntikeiTiles();
            }
            lastCoreCount = currentCores;
        }

        if (Core.input.keyTap(KeyCode.mouseMiddle)) {
            Vec2 mouseVec = Core.camera.unproject(Core.input.mouse());
            Tile tile = Vars.world.tileWorld(mouseVec.x, mouseVec.y);

            if (tile != null && tile.floor() == antikeiBlock) {
                String key = getTileKey(tile.x, tile.y);
                Float rawVal = flowDirectionMap.get(key);
                if (rawVal == null && autoFlowMap.containsKey(key)) {
                    rawVal = autoFlowMap.get(key);
                }
                float currentAngleNum = rawVal != null ? rawVal : 0f;

                float nextAngle = (currentAngleNum + 90f) % 360f;
                flowDirectionMap.put(key, Float.valueOf(nextAngle));
                saveFlowData();
            }
        }

        if (Vars.state.isEditor() && Core.input.keyDown(KeyCode.r)
            && (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.isTouched())) {
            Vec2 mouseVec = Core.camera.unproject(Core.input.mouse());
            Tile tile = Vars.world.tileWorld(mouseVec.x, mouseVec.y);

            if (tile != null && tile.floor() == antikeiBlock) {
                String key = getTileKey(tile.x, tile.y);
                if (flowDirectionMap.containsKey(key)) {
                    flowDirectionMap.remove(key);
                    saveFlowData();
                }
            }
        }

        Groups.unit.each(Antikei::steerUnit);
    }

    private static void steerUnit(Unit unit) {
        if (unit == null || !unit.isAdded() || unit.isFlying()) return;

        Tile currentTile = unit.tileOn();
        float moveAngle;

        if (currentTile != null && currentTile.floor() == antikeiBlock) {
            int uTileX = unit.tileX();
            int uTileY = unit.tileY();
            String currentKey = getTileKey(uTileX, uTileY);
            float worldX = uTileX * Vars.tilesize + Vars.tilesize / 2f;
            float worldY = uTileY * Vars.tilesize + Vars.tilesize / 2f;

            moveAngle = getOptimalAngle(worldX, worldY, currentKey, unit.team);
        } else {
            Tile nearest = findNearestAntikeiCached(unit);
            if (nearest != null) {
                moveAngle = unit.angleTo(nearest.worldx(), nearest.worldy());
            } else {
                CoreBuild core = getClosestCore(unit.x, unit.y, unit.team);
                if (core != null) {
                    moveAngle = unit.angleTo(core.x, core.y);
                } else {
                    moveAngle = unit.rotation;
                }
            }
        }

        unit.vel.trns(moveAngle, unit.speed());

        float range = unit.range();
        Teamc target;
        TargetEntry tc = unitTargetCache.get(unit.id);
        if (tc != null && frameCount - tc.f < 6 && (tc.t == null || isTargetValid(tc.t))) {
            target = tc.t;
        } else {
            target = Units.closestTarget(unit.team, unit.x, unit.y, range);
            unitTargetCache.put(unit.id, new TargetEntry(target, frameCount - (unit.id % 6)));
        }

        if (target != null) {
            Posc tp = (Posc) target;
            unit.lookAt(tp.x(), tp.y());
            unit.aim(tp.x(), tp.y());
            unit.controlWeapons(true, true);
        } else {
            unit.lookAt(moveAngle);
            unit.aim(unit.x + Angles.trnsx(moveAngle, 10f), unit.y + Angles.trnsy(moveAngle, 10f));
            unit.controlWeapons(false, false);
        }
    }

    // ------------------------------------------------------------------ vẽ mũi tên dòng chảy

    private static void draw() {
        if (antikeiBlock == null || Vars.state.isMenu()) return;
        int total = antikeiTilesList.size();
        if (total == 0) return;

        if (drawCacheVersion != antikeiDrawVersion || drawCacheLen != total) {
            drawXs = new float[total];
            drawYs = new float[total];
            drawKeys = new String[total];
            for (int i = 0; i < total; i++) {
                String key = antikeiTilesList.get(i);
                int[] p = parseKey(key);
                drawKeys[i] = key;
                drawXs[i] = p[0] * Vars.tilesize + Vars.tilesize / 2f;
                drawYs[i] = p[1] * Vars.tilesize + Vars.tilesize / 2f;
            }
            drawCacheVersion = antikeiDrawVersion;
            drawCacheLen = total;
            angleCache = new HashMap<>();
        }

        Draw.z(Layer.floor + 0.1f);
        Rect cb = Core.camera.bounds(Tmp.r1);
        float cx0 = cb.x - 8, cy0 = cb.y - 8, cx1 = cb.x + cb.width + 8, cy1 = cb.y + cb.height + 8;
        Draw.color(Pal.accent);
        Lines.stroke(1.2f);

        for (int i = 0; i < total; i++) {
            float worldX = drawXs[i];
            float worldY = drawYs[i];
            if (worldX < cx0 || worldX > cx1 || worldY < cy0 || worldY > cy1) continue;

            String key = drawKeys[i];
            AngleEntry ac = angleCache.get(key);
            if (ac == null || frameCount - ac.f >= 30) {
                ac = new AngleEntry(getOptimalAngle(worldX, worldY, key, Team.crux), frameCount + (i % 15));
                angleCache.put(key, ac);
            }
            float angle = ac.a;

            Lines.lineAngleCenter(worldX, worldY, angle, 4f);
            Lines.lineAngle(worldX + Angles.trnsx(angle, 2f), worldY + Angles.trnsy(angle, 2f), angle + 135f, 2f);
            Lines.lineAngle(worldX + Angles.trnsx(angle, 2f), worldY + Angles.trnsy(angle, 2f), angle - 135f, 2f);
        }

        Draw.reset();
    }
}
