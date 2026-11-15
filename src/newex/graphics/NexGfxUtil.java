package newex.graphics;

import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Vec2;
import mindustry.Vars;

/** Các hàm tiện ích cho graphics, trích từ WarHammer. */
public final class NexGfxUtil {
    private NexGfxUtil() {}

    public static final Rand rand = new Rand(0);
    private static final Vec2 v1 = new Vec2();

    public static Rand rand(long id) {
        rand.setSeed(id);
        return rand;
    }

    public static float getParallaxFrom(float from, float ref, float elevation) {
        return from + (from - ref) * elevation * Vars.renderer.getDisplayScale() / 50;
    }

    public static Vec2 getParallaxFrom(Vec2 from, Vec2 ref, float elevation) {
        return getParallaxFrom(from, ref, elevation, Vars.renderer.getDisplayScale() / 50);
    }

    public static Vec2 getParallaxFrom(Vec2 from, Vec2 ref, float elevation, float scale) {
        return from.add(v1.set(from).sub(ref).scl(elevation * scale));
    }

    public static void superEllipse(float angle, float r, float centerX, float centerY, float rotation, Vec2 out) {
        superEllipse(angle, r, r, 4, centerX, centerY, rotation, out);
    }

    /** Điểm trên siêu ellipse đã xoay + dịch (n=2: ellipse, n=4: gần chữ nhật). */
    public static void superEllipse(float angle, float a, float b, float n, float centerX, float centerY, float rotation, Vec2 out) {
        float cosT = Mathf.cosDeg(angle);
        float sinT = Mathf.sinDeg(angle);
        float x = a * Mathf.sign(cosT) * Mathf.pow(Math.abs(cosT), 2f / n);
        float y = b * Mathf.sign(sinT) * Mathf.pow(Math.abs(sinT), 2f / n);

        float cosR = Mathf.cosDeg(rotation);
        float sinR = Mathf.sinDeg(rotation);
        out.set(x * cosR - y * sinR, x * sinR + y * cosR);
        out.add(centerX, centerY);
    }
}
