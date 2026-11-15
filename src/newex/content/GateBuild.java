package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.StorageBlock;

/**
 * Phần dùng chung của cổng dịch chuyển Wuex / Xeuw (suy ra từ wuex-conveyor.js và xeuw-conveyor.js).
 * Người chơi bấm nút cấu hình rồi chọn 1 ô trong tầm 380px làm mục tiêu.
 * StorageBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}.
 */
public abstract class GateBuild extends StorageBlock.StorageBuild {
    public static final float MAX_RANGE = 380f;

    public float targetX = 0f;
    public float targetY = 0f;
    public boolean isTeleporting = false;
    public boolean selectingTarget = false;
    public final float maxRange = MAX_RANGE;

    protected GateBuild(StorageBlock outer) {
        outer.super();
    }

    /** Nội dung thông báo khi bắt đầu chọn mục tiêu. */
    protected abstract String selectToast();

    /** Nội dung thông báo khi mục tiêu quá xa. */
    protected abstract String rangeToast();

    @Override
    public void created() {
        super.created();
        targetX = x;
        targetY = y;
    }

    @Override
    public void buildConfiguration(Table table) {
        table.button(Icon.commandRally, () -> {
            deselect();
            selectingTarget = true;
            Vars.ui.hudfrag.showToast(selectToast());
        }).size(40f);
    }

    @Override
    public void configured(Unit builder, Object value) {
        super.configured(builder, value);
        if (value instanceof Number) {
            Point2 pos = Point2.unpack(((Number) value).intValue());
            float destX = pos.x * Vars.tilesize;
            float destY = pos.y * Vars.tilesize;

            if (Mathf.dst(x, y, destX, destY) <= maxRange) {
                targetX = destX;
                targetY = destY;
            } else {
                Vars.ui.hudfrag.showToast(rangeToast());
            }
        }
    }

    @Override
    public void write(Writes write) {
        super.write(write);
        write.f(targetX);
        write.f(targetY);
    }

    @Override
    public void read(Reads read, byte revision) {
        super.read(read, revision);
        targetX = read.f();
        targetY = read.f();
    }

    /** Xử lý cú nhấp chuột chọn mục tiêu (gọi đầu updateTile). */
    protected void updateTargetSelection() {
        if (selectingTarget) {
            if (Core.input.keyTap(KeyCode.mouseLeft)) {
                Vec2 worldVec = Core.camera.unproject(Core.input.mouse());
                Tile tile = Vars.world.tileWorld(worldVec.x, worldVec.y);
                if (tile != null) {
                    configure(Point2.pack(tile.x, tile.y));
                }
                selectingTarget = false;
            }
        }
    }

    /** Vòng tròn tầm + đường nét đứt + ô vuông tại con trỏ khi đang chọn. {@code fromMouse}: hướng đường nét đứt. */
    protected void drawSelecting(Color base, boolean fromMouse) {
        if (!selectingTarget) return;
        Draw.color(base);
        Lines.stroke(1f);
        Lines.circle(x, y, maxRange);

        Vec2 mouseWorld = Core.camera.unproject(Core.input.mouse());
        boolean isWithinRange = Mathf.dst(x, y, mouseWorld.x, mouseWorld.y) <= maxRange;
        Color targetColor = isWithinRange ? base : Color.red;

        if (fromMouse) drawMovingDashLine(mouseWorld.x, mouseWorld.y, x, y, targetColor);
        else drawMovingDashLine(x, y, mouseWorld.x, mouseWorld.y, targetColor);

        Tile mouseTile = Vars.world.tileWorld(mouseWorld.x, mouseWorld.y);
        if (mouseTile != null) {
            Drawf.square(mouseTile.drawx(), mouseTile.drawy(), 4f, 0f, targetColor);
        }
    }

    /** Vòng tầm + đường tới mục tiêu đã chọn. {@code fromTarget}: hướng đường nét đứt. */
    protected void drawConfigured(Color base, boolean fromTarget) {
        Draw.color(base);
        Lines.stroke(1f);
        Lines.circle(x, y, maxRange);

        if (Mathf.dst(x, y, targetX, targetY) <= maxRange && (targetX != x || targetY != y)) {
            if (fromTarget) drawMovingDashLine(targetX, targetY, x, y, base);
            else drawMovingDashLine(x, y, targetX, targetY, base);
            Drawf.square(targetX, targetY, 4f, 0f, base);
        }
        Draw.reset();
    }

    /** Vòng xoay tam giác khi đang dịch chuyển. */
    protected void drawTeleportingMark(Color base) {
        if (isTeleporting) {
            Draw.color(base);
            Draw.alpha(0.6f + Mathf.sin(Time.time / 4f) * 0.3f);
            Lines.stroke(1.5f);
            Lines.poly(x, y, 3, 5f + Mathf.sin(Time.time / 5f) * 2f, Time.time * 3f);
            Draw.reset();
        }
    }

    /** Đường nét đứt chạy dọc từ (x1,y1) tới (x2,y2). */
    public static void drawMovingDashLine(float x1, float y1, float x2, float y2, Color color) {
        float len = Mathf.dst(x1, y1, x2, y2);
        float dashLen = 6f;
        float gapLen = 4f;
        float step = dashLen + gapLen;
        float angle = Angles.angle(x1, y1, x2, y2);
        float offset = (Time.time * 1.5f) % step;

        Draw.color(color);
        Lines.stroke(1.5f);

        for (float d = offset; d < len; d += step) {
            float endD = Math.min(d + dashLen, len);
            float sx = x1 + Angles.trnsx(angle, d);
            float sy = y1 + Angles.trnsy(angle, d);
            float ex = x1 + Angles.trnsx(angle, endD);
            float ey = y1 + Angles.trnsy(angle, endD);
            Lines.line(sx, sy, ex, ey);
        }
        Draw.reset();
    }
}
