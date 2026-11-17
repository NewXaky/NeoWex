package newex;

import arc.Events;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.StaticWall;

import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Khối địa hình (tường tĩnh - StaticWall) chặn TOÀN BỘ các loại đạn.
 *
 * Mặc định trong Mindustry đạn bay xuyên/lướt qua tường địa hình. Lớp này kiểm tra mọi viên đạn
 * sau mỗi tick: nếu đoạn đường đi của đạn (từ vị trí tick trước -> vị trí hiện tại) cắt qua một ô
 * tường địa hình thì đạn bị "trúng" tại mép ô đó (nổ / sinh hiệu ứng / mảnh văng như bình thường)
 * rồi bị xóa, kể cả đạn xuyên (pierce) và đạn pháo vòng cung.
 *
 * Cách dùng: gọi {@code TerrainBulletBlock.init();} trong constructor của NexMod.
 *
 * Giới hạn: các loại đạn "tức thời" (tia laser, rail, shrapnel, sét, sap, point...) gây sát thương
 * ngay lúc được tạo bằng Damage.collideLine nên mod không chặn được chúng nếu không sửa mã gốc của game.
 */
public class TerrainBulletBlock {
    /** Bật / tắt toàn bộ tính năng. */
    public static boolean enabled = true;
    /**
     * Đạn pháo vòng cung (artillery: collides = false, collidesTiles = false) có bị chặn không?
     * true  = chặn tất cả (đúng yêu cầu "toàn bộ các loại đạn").
     * false = pháo vẫn bay qua tường như bình thường.
     */
    public static boolean blockArtillery = true;
    /** Có coi vách đá (Blocks.cliff) là vật chặn không. Vách đá chỉ là đường viền nên mặc định tắt. */
    public static boolean blockCliffs = false;

    private static boolean initialized = false;

    private TerrainBulletBlock() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        // afterGameUpdate chạy SAU khi toàn bộ thực thể (đạn) đã di chuyển trong tick này,
        // lúc đó bullet.lastX/lastY là vị trí tick trước, bullet.x/y là vị trí mới.
        Events.run(Trigger.afterGameUpdate, TerrainBulletBlock::update);
    }

    private static void update() {
        if (!enabled || !state.isGame() || world == null || world.isGenerating()) return;
        Groups.bullet.each(TerrainBulletBlock::check);
    }

    private static boolean isTerrain(Tile t) {
        if (t == null) return false;
        Block b = t.block();
        if (b instanceof StaticWall) return true;
        return blockCliffs && b == Blocks.cliff;
    }

    private static void check(Bullet b) {
        if (b == null || b.type == null || !b.isAdded()) return;

        // Đạn đứng yên / vừa sinh ra tại chỗ (tia tức thời, đạn gắn dính...) -> không có đoạn đường để kiểm tra.
        if (b.lastX == b.x && b.lastY == b.y) return;

        // Đạn pháo vòng cung: bỏ qua nếu người dùng muốn cho phép pháo bay qua tường.
        if (!blockArtillery && !b.type.collides && !b.type.collidesTiles) return;

        int x1 = World.toTile(b.lastX), y1 = World.toTile(b.lastY);
        int x2 = World.toTile(b.x), y2 = World.toTile(b.y);

        // Người bắn đang đứng trong tường (đơn vị chân bước đi trên địa hình): cho đạn thoát ra, không chặn.
        if (isTerrain(world.tile(World.toTile(b.originX), World.toTile(b.originY)))) return;

        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, x = x1, y = y1;
        int ww = world.width(), wh = world.height();

        while (x >= 0 && y >= 0 && x < ww && y < wh) {
            if (isTerrain(world.tile(x, y))) {
                hitWall(b, x, y);
                return;
            }
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }

    private static void hitWall(Bullet b, int tx, int ty) {
        float wx = tx * tilesize, wy = ty * tilesize;

        // Đặt điểm nổ ở mép ô tường (phía đạn bay tới) thay vì ở sâu trong tường.
        Vec2 hit = Geometry.raycastRect(b.lastX, b.lastY, b.x, b.y, Tmp.r1.setCentered(wx, wy, tilesize, tilesize));
        float hx, hy;
        if (hit != null) {
            hx = hit.x;
            hy = hit.y;
        } else {
            hx = Mathf.clamp(b.x, wx - tilesize / 2f, wx + tilesize / 2f);
            hy = Mathf.clamp(b.y, wy - tilesize / 2f, wy + tilesize / 2f);
        }
        b.x = hx;
        b.y = hy;

        b.type.hit(b, hx, hy);
        b.hit = true; // tránh gọi despawned() thêm lần nữa -> không lặp hiệu ứng
        b.remove();
    }
}
