package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sàn đường đi "antikei" kiểu game Thủ Thành (Tower Defense) — tối ưu hóa O(1) bằng mảng phẳng:
 * - Tự động tính trường hướng (Flow Field) mượt từ mọi ô antikei dẫn về lõi phe phòng thủ, bo cua mượt & bám giữa đường.
 * - Chỉ điều hướng quái đợt (waveTeam, không phải player) để không cướp điều khiển unit người chơi.
 * - Quái đi lệch khỏi đường có sẵn trường hướng O(1) kéo mượt quay lại mặt đường gần nhất.
 * - Trong Editor: kéo chuột trái vẽ hướng, giữ R + chuột trái xóa hướng, chuột giữa xoay 90°.
 */
public final class Antikei {
    private static final float NO_ANGLE = Float.NaN;
    private static final Pattern FLOW_ENTRY = Pattern.compile("\"(\\d+)_(\\d+)\"\\s*:\\s*(-?[0-9.eE+\\-]+)");
    private static final int[] DX4 = {1, -1, 0, 0};
    private static final int[] DY4 = {0, 0, 1, -1};
    private static final int[] DX8 = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY8 = {0, 0, 1, -1, 1, -1, 1, -1};

    private static Block antikeiBlock;
    private static boolean mapHasAntikei = false;

    private static int mapW = 0, mapH = 0;
    private static boolean[] isRoad = new boolean[0];
    private static int[] roadDist = new int[0];
    private static float[] manualAng = new float[0];
    private static float[] flowAng = new float[0];
    private static float[] toRoadAng = new float[0];
    private static int[] roadIndices = new int[0];
    private static int roadCount = 0;

    private static Tile lastEditorTile = null;
    private static int lastCoreCount = -1;
    private static int tickCount = 0;

    private static final class TargetEntry {
        Teamc t;
        int tick;
    }
    private static final HashMap<Integer, TargetEntry> targetCache = new HashMap<>();

    private Antikei() {}

    public static void init() {
        Events.on(ContentInitEvent.class, e -> antikeiBlock = Vars.content.block("newex-antikei"));

        Events.on(WorldLoadEvent.class, e -> {
            targetCache.clear();
            lastEditorTile = null;
            tickCount = 0;
            rebuildField(true);
        });

        Events.run(Trigger.update, Antikei::update);
        if (!Vars.headless) {
            Events.run(Trigger.draw, Antikei::draw);
        }
    }

    // ------------------------------------------------------------------ Lưu / tải hướng vẽ trong Editor

    private static String getMapKey() {
        if (Vars.state == null) return "antikei_map_default";
        if (Vars.state.map != null && Vars.state.map.name() != null) {
            return "antikei_map_" + Vars.state.map.name().replaceAll("[^a-zA-Z0-9_-]", "_");
        }
        return "antikei_map_editor_test";
    }

    private static void saveManualFlow() {
        if (Vars.state == null || Vars.state.isMenu() || mapW <= 0) return;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i < roadCount; i++) {
            int idx = roadIndices[i];
            float a = manualAng[idx];
            if (Float.isNaN(a)) continue;
            if (!first) sb.append(',');
            first = false;
            int x = idx % mapW, y = idx / mapW;
            sb.append('"').append(x).append('_').append(y).append("\":").append(Math.round(a * 10f) / 10f);
        }
        sb.append('}');
        Core.settings.put(getMapKey(), sb.toString());
    }

    private static void loadManualFlow() {
        Arrays.fill(manualAng, NO_ANGLE);
        if (Vars.state == null || Vars.state.isMenu() || mapW <= 0) return;
        String json = Core.settings.getString(getMapKey(), "");
        if (json == null || json.isEmpty()) return;
        try {
            Matcher m = FLOW_ENTRY.matcher(json);
            while (m.find()) {
                int x = Integer.parseInt(m.group(1));
                int y = Integer.parseInt(m.group(2));
                if (x >= 0 && x < mapW && y >= 0 && y < mapH) {
                    manualAng[y * mapW + x] = Float.parseFloat(m.group(3));
                }
            }
        } catch (Throwable err) {
            Log.err("[Antikei] loadManualFlow failed", err);
        }
    }

    // ------------------------------------------------------------------ Xây dựng trường hướng (Flow Field) O(W*H)

    private static int countTargetCores() {
        if (Vars.state == null || Vars.state.teams == null) return 0;
        Team waveTeam = Vars.state.rules != null ? Vars.state.rules.waveTeam : Team.crux;
        int total = 0;
        for (TeamData td : Vars.state.teams.active) {
            if (td != null && td.team != waveTeam && td.team != Team.derelict && td.cores != null) {
                total += td.cores.size;
            }
        }
        return total;
    }

    private static void rebuildField(boolean reloadManual) {
        mapHasAntikei = false;
        roadCount = 0;
        if (antikeiBlock == null || Vars.world == null) return;

        int w = Vars.world.width(), h = Vars.world.height();
        int total = w * h;
        if (total <= 0) return;

        if (w != mapW || h != mapH || isRoad.length != total) {
            mapW = w;
            mapH = h;
            isRoad = new boolean[total];
            roadDist = new int[total];
            manualAng = new float[total];
            flowAng = new float[total];
            toRoadAng = new float[total];
            Arrays.fill(manualAng, NO_ANGLE);
        } else {
            Arrays.fill(isRoad, false);
        }

        // 1. Quét toàn bộ ô antikei trên bản đồ
        int count = 0;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                Tile tile = Vars.world.tile(x, y);
                if (tile != null && tile.floor() == antikeiBlock) {
                    isRoad[row + x] = true;
                    count++;
                    if (reloadManual && tile.overlay() != null && tile.overlay() != Blocks.air) {
                        NexNet.setOverlay(tile, Blocks.air);
                    }
                }
            }
        }

        if (count == 0) return;
        mapHasAntikei = true;
        if (roadIndices.length < count) roadIndices = new int[count];
        roadCount = 0;
        for (int i = 0; i < total; i++) {
            if (isRoad[i]) roadIndices[roadCount++] = i;
        }

        if (reloadManual) loadManualFlow();
        lastCoreCount = countTargetCores();

        // 2. BFS đa nguồn từ các ô antikei gần lõi phòng thủ (mọi phe trừ waveTeam)
        Arrays.fill(roadDist, -1);
        int[] queue = new int[total];
        int qHead = 0, qTail = 0;

        Team waveTeam = (Vars.state != null && Vars.state.rules != null) ? Vars.state.rules.waveTeam : Team.crux;
        if (Vars.state != null && Vars.state.teams != null) {
            for (TeamData td : Vars.state.teams.active) {
                if (td == null || td.team == waveTeam || td.team == Team.derelict || td.cores == null) continue;
                for (int ci = 0; ci < td.cores.size; ci++) {
                    CoreBuild core = td.cores.get(ci);
                    if (core == null || !core.isValid()) continue;
                    int cx = World.toTile(core.x);
                    int cy = World.toTile(core.y);

                    int bestD = Integer.MAX_VALUE;
                    int bestIdx = -1;
                    boolean seededNear = false;

                    for (int k = 0; k < roadCount; k++) {
                        int idx = roadIndices[k];
                        int rx = idx % w, ry = idx / w;
                        int d = Math.abs(rx - cx) + Math.abs(ry - cy);
                        if (d < bestD) {
                            bestD = d;
                            bestIdx = idx;
                        }
                        if (Math.abs(rx - cx) <= 5 && Math.abs(ry - cy) <= 5) {
                            if (roadDist[idx] != 0) {
                                roadDist[idx] = 0;
                                queue[qTail++] = idx;
                            }
                            seededNear = true;
                        }
                    }
                    if (!seededNear && bestIdx >= 0 && roadDist[bestIdx] != 0) {
                        roadDist[bestIdx] = 0;
                        queue[qTail++] = bestIdx;
                    }
                }
            }
        }

        if (qTail == 0 && roadCount > 0) {
            int first = roadIndices[0];
            roadDist[first] = 0;
            queue[qTail++] = first;
        }

        while (qHead < qTail) {
            int curr = queue[qHead++];
            int cx = curr % w, cy = curr / w;
            int nextD = roadDist[curr] + 1;
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX4[d], ny = cy + DY4[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (isRoad[nIdx] && roadDist[nIdx] == -1) {
                    roadDist[nIdx] = nextD;
                    queue[qTail++] = nIdx;
                }
            }
        }

        // 3. Tính vector hướng đi mượt (kết hợp 8 hướng + làm mịn bo cua)
        float[] rawVx = new float[total];
        float[] rawVy = new float[total];
        Arrays.fill(flowAng, NO_ANGLE);

        for (int k = 0; k < roadCount; k++) {
            int idx = roadIndices[k];
            if (!Float.isNaN(manualAng[idx])) {
                rawVx[idx] = Mathf.cosDeg(manualAng[idx]);
                rawVy[idx] = Mathf.sinDeg(manualAng[idx]);
                continue;
            }
            int cd = roadDist[idx];
            if (cd < 0) continue;
            int x = idx % w, y = idx / w;

            float vx = 0f, vy = 0f;
            int bestD = cd;
            for (int d = 0; d < 8; d++) {
                int nx = x + DX8[d], ny = y + DY8[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (!isRoad[nIdx]) continue;
                int nd = roadDist[nIdx];
                if (nd < 0) continue;
                // Tránh đi chéo cắt qua góc tường không phải đường
                if (d >= 4 && (!isRoad[y * w + nx] || !isRoad[ny * w + x])) continue;
                if (nd < cd) {
                    float wgt = (cd - nd) * (d < 4 ? 1.0f : 0.72f);
                    vx += DX8[d] * wgt;
                    vy += DY8[d] * wgt;
                    if (nd < bestD) bestD = nd;
                }
            }
            if (vx != 0f || vy != 0f) {
                float len = (float) Math.sqrt(vx * vx + vy * vy);
                rawVx[idx] = vx / len;
                rawVy[idx] = vy / len;
            }
        }

        // Làm mịn 1 lượt theo hàng xóm cùng trên mặt đường để quái bo cua siêu mượt như game Thủ Thành
        for (int k = 0; k < roadCount; k++) {
            int idx = roadIndices[k];
            if (!Float.isNaN(manualAng[idx])) {
                flowAng[idx] = manualAng[idx];
                continue;
            }
            int x = idx % w, y = idx / w;
            float sx = rawVx[idx] * 2.2f;
            float sy = rawVy[idx] * 2.2f;
            int cd = roadDist[idx];
            for (int d = 0; d < 4; d++) {
                int nx = x + DX4[d], ny = y + DY4[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (isRoad[nIdx] && roadDist[nIdx] >= 0 && roadDist[nIdx] <= cd) {
                    sx += rawVx[nIdx];
                    sy += rawVy[nIdx];
                }
            }
            if (sx != 0f || sy != 0f) {
                flowAng[idx] = Angles.angle(sx, sy);
            }
        }

        // 4. BFS ngoài đường: tạo hướng O(1) kéo quái đứng ngoài mặt đường quay về đường antikei gần nhất
        int[] offDist = new int[total];
        Arrays.fill(offDist, -1);
        Arrays.fill(toRoadAng, NO_ANGLE);
        qHead = 0;
        qTail = 0;
        for (int k = 0; k < roadCount; k++) {
            int idx = roadIndices[k];
            offDist[idx] = 0;
            queue[qTail++] = idx;
        }
        while (qHead < qTail) {
            int curr = queue[qHead++];
            int cx = curr % w, cy = curr / w;
            int nextD = offDist[curr] + 1;
            if (nextD > 60) continue; // tối đa 60 ô quanh đường
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX4[d], ny = cy + DY4[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                int nIdx = ny * w + nx;
                if (offDist[nIdx] == -1) {
                    offDist[nIdx] = nextD;
                    toRoadAng[nIdx] = Angles.angle(nx, ny, cx, cy);
                    queue[qTail++] = nIdx;
                }
            }
        }
    }

    private static CoreBuild closestDefenderCore(float x, float y, Team unitTeam) {
        if (Vars.state == null || Vars.state.teams == null) return null;
        CoreBuild best = null;
        float bestD2 = Float.MAX_VALUE;
        for (TeamData td : Vars.state.teams.active) {
            if (td == null || td.team == unitTeam || td.team == Team.derelict || td.cores == null) continue;
            for (int i = 0; i < td.cores.size; i++) {
                CoreBuild c = td.cores.get(i);
                if (c != null && c.isValid()) {
                    float d2 = c.dst2(x, y);
                    if (d2 < bestD2) { bestD2 = d2; best = c; }
                }
            }
        }
        return best;
    }

    private static boolean isTargetValid(Teamc t) {
        if (t instanceof Unit) return ((Unit) t).isValid() && !((Unit) t).dead;
        if (t instanceof Building) return ((Building) t).isValid();
        return false;
    }

    // ------------------------------------------------------------------ Cập nhật mỗi tick

    private static void update() {
        if (antikeiBlock == null || Vars.state == null || Vars.state.isMenu()) return;

        tickCount++;
        if (tickCount % 1200 == 0) targetCache.clear();

        // Chỉnh sửa hướng dòng chảy trong Editor
        if (!Vars.headless && Vars.state.isEditor()) {
            handleEditorInput();
        }

        if (!mapHasAntikei) return;

        // Cập nhật lại Flow Field nếu số lõi phòng thủ thay đổi
        if (Vars.state.isPlaying() && tickCount % 45 == 0) {
            int cores = countTargetCores();
            if (lastCoreCount != -1 && cores != lastCoreCount) {
                rebuildField(false);
            }
            lastCoreCount = cores;
        }

        // Chỉ điều hướng trên server/host để không giật hình trên client; Mindustry tự đồng bộ mượt
        if (!Vars.net.client() && Vars.state.isPlaying() && !Vars.state.isPaused()) {
            Team waveTeam = Vars.state.rules != null ? Vars.state.rules.waveTeam : Team.crux;
            Groups.unit.each(u -> steerWaveUnit(u, waveTeam));
        }
    }

    private static void handleEditorInput() {
        if (Core.input.keyDown(KeyCode.mouseLeft) || Core.input.isTouched()) {
            Vec2 mv = Core.camera.unproject(Core.input.mouse());
            Tile tile = Vars.world.tileWorld(mv.x, mv.y);
            if (tile != null && tile.floor() == antikeiBlock) {
                if (!mapHasAntikei || tile.x >= mapW || tile.y >= mapH || !isRoad[tile.y * mapW + tile.x]) {
                    rebuildField(true);
                }
                int idx = tile.y * mapW + tile.x;
                if (Core.input.keyDown(KeyCode.r)) {
                    if (!Float.isNaN(manualAng[idx])) {
                        manualAng[idx] = NO_ANGLE;
                        rebuildField(false);
                        saveManualFlow();
                    }
                    lastEditorTile = null;
                    return;
                }
                if (lastEditorTile != null && (lastEditorTile.x != tile.x || lastEditorTile.y != tile.y)) {
                    float ang = Angles.angle(lastEditorTile.worldx(), lastEditorTile.worldy(), tile.worldx(), tile.worldy());
                    int prevIdx = lastEditorTile.y * mapW + lastEditorTile.x;
                    manualAng[prevIdx] = ang;
                    flowAng[prevIdx] = ang;
                    manualAng[idx] = ang;
                    flowAng[idx] = ang;
                    saveManualFlow();
                } else if (Float.isNaN(manualAng[idx])) {
                    manualAng[idx] = 0f;
                    flowAng[idx] = 0f;
                    saveManualFlow();
                }
                lastEditorTile = tile;
            } else {
                lastEditorTile = null;
            }
        } else {
            lastEditorTile = null;
        }

        if (Core.input.keyTap(KeyCode.mouseMiddle) && mapHasAntikei) {
            Vec2 mv = Core.camera.unproject(Core.input.mouse());
            Tile tile = Vars.world.tileWorld(mv.x, mv.y);
            if (tile != null && tile.floor() == antikeiBlock && tile.x < mapW && tile.y < mapH) {
                int idx = tile.y * mapW + tile.x;
                float cur = !Float.isNaN(manualAng[idx]) ? manualAng[idx] : (!Float.isNaN(flowAng[idx]) ? flowAng[idx] : 0f);
                float next = (cur + 90f) % 360f;
                manualAng[idx] = next;
                flowAng[idx] = next;
                saveManualFlow();
            }
        }
    }

    /** Điều hướng mượt riêng cho quái đợt đi bộ (kiểu Thủ Thành) đi dọc mặt đường antikei. */
    private static void steerWaveUnit(Unit unit, Team waveTeam) {
        if (unit == null || unit.dead || !unit.isAdded() || unit.isFlying() || unit.isPlayer()) return;
        if (unit.team != waveTeam) return;

        int tx = unit.tileX(), ty = unit.tileY();
        if (tx < 0 || tx >= mapW || ty < 0 || ty >= mapH) return;
        int idx = ty * mapW + tx;

        float moveAngle;
        if (isRoad[idx]) {
            float fa = flowAng[idx];
            if (!Float.isNaN(fa)) {
                moveAngle = fa;
            } else {
                CoreBuild core = closestDefenderCore(unit.x, unit.y, unit.team);
                moveAngle = core != null ? unit.angleTo(core.x, core.y) : unit.rotation;
            }
            // Kéo nhẹ vào trục giữa dải đường nếu 2 bên mép không phải đường để tránh kẹt góc tường
            float fx = Mathf.cosDeg(moveAngle), fy = Mathf.sinDeg(moveAngle);
            float tileCx = tx * Vars.tilesize + Vars.tilesize * 0.5f;
            float tileCy = ty * Vars.tilesize + Vars.tilesize * 0.5f;
            float offX = tileCx - unit.x, offY = tileCy - unit.y;
            // Thành phần vuông góc với hướng chạy
            float perpDot = offX * (-fy) + offY * fx;
            float steerX = fx + (-fy) * Mathf.clamp(perpDot * 0.08f, -0.35f, 0.35f);
            float steerY = fy + fx * Mathf.clamp(perpDot * 0.08f, -0.35f, 0.35f);
            moveAngle = Angles.angle(steerX, steerY);
        } else {
            float tra = toRoadAng[idx];
            if (!Float.isNaN(tra)) {
                moveAngle = tra;
            } else {
                CoreBuild core = closestDefenderCore(unit.x, unit.y, unit.team);
                moveAngle = core != null ? unit.angleTo(core.x, core.y) : unit.rotation;
            }
        }

        // Chuyển hướng vận tốc mượt (không giật khựng 90° đột ngột)
        float spd = unit.speed();
        Tmp.v1.trns(moveAngle, spd);
        unit.vel.lerp(Tmp.v1, Mathf.clamp(0.28f * Time.delta));

        // Quái vẫn tự động bắn mục tiêu trong tầm trong lúc hành quân dọc đường
        TargetEntry tc = targetCache.get(unit.id);
        if (tc == null) {
            tc = new TargetEntry();
            tc.tick = -999;
            targetCache.put(unit.id, tc);
        }
        if (tickCount - tc.tick >= 10 || (tc.t != null && !isTargetValid(tc.t))) {
            tc.t = Units.closestTarget(unit.team, unit.x, unit.y, unit.range());
            tc.tick = tickCount - (unit.id & 7);
        }

        if (tc.t != null) {
            Posc tp = (Posc) tc.t;
            unit.lookAt(tp.x(), tp.y());
            unit.aim(tp.x(), tp.y());
            unit.controlWeapons(true, true);
        } else {
            unit.lookAt(moveAngle);
            unit.aim(unit.x + Angles.trnsx(moveAngle, 16f), unit.y + Angles.trnsy(moveAngle, 16f));
            unit.controlWeapons(false, false);
        }
    }

    // ------------------------------------------------------------------ Vẽ mũi tên chỉ đường (O(1), không cấp phát)

    private static void draw() {
        if (antikeiBlock == null || !mapHasAntikei || Vars.state == null || Vars.state.isMenu()) return;

        boolean editor = Vars.state.isEditor();
        Draw.z(Layer.floor + 0.1f);
        Rect cb = Core.camera.bounds(Tmp.r1);
        int x0 = Math.max(0, World.toTile(cb.x) - 1);
        int y0 = Math.max(0, World.toTile(cb.y) - 1);
        int x1 = Math.min(mapW - 1, World.toTile(cb.x + cb.width) + 1);
        int y1 = Math.min(mapH - 1, World.toTile(cb.y + cb.height) + 1);

        Draw.color(Pal.accent, editor ? 0.85f : 0.35f);
        Lines.stroke(editor ? 1.25f : 0.95f);

        for (int y = y0; y <= y1; y++) {
            int row = y * mapW;
            float wy = y * Vars.tilesize + Vars.tilesize * 0.5f;
            for (int x = x0; x <= x1; x++) {
                int idx = row + x;
                if (!isRoad[idx]) continue;
                float ang = flowAng[idx];
                if (Float.isNaN(ang)) continue;
                float wx = x * Vars.tilesize + Vars.tilesize * 0.5f;
                float tx = wx + Angles.trnsx(ang, 2.2f);
                float ty = wy + Angles.trnsy(ang, 2.2f);
                Lines.lineAngleCenter(wx, wy, ang, 3.8f);
                Lines.lineAngle(tx, ty, ang + 140f, 1.9f);
                Lines.lineAngle(tx, ty, ang - 140f, 1.9f);
            }
        }
        Draw.reset();
    }
}
