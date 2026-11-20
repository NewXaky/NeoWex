package newex.graphics;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

import java.util.HashSet;

/**
 * Đồ họa viền đen & Saturation NeoWex (tương thích OpenGL 2.0 / Intel GPU / Mobile):
 * 1) Đổi viền phát sáng trắng quanh đạn / pháo / unit sang viền đen đậm (outlineColor đen + giảm hào quang trắng + viền bóng đen quanh đạn/unit).
 * 2) Hậu xử lý màn hình (FrameBuffer + Shader): tùy chỉnh Saturation (độ bão hòa màu) thời gian thực.
 * 3) Thêm thanh kéo thả Saturation & Độ đậm viền đen vào Cài đặt -> Đồ họa của game.
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final String SATURATION_KEY = "newex-saturation";
    public static final String DARK_GLOW_KEY = "newex-dark-border-str";
    /** Bảng màu tím êm mắt (Obsidian Violet -> Amethyst -> Soft Lavender) gây nghiện, không chói gắt. */
    public static final Color VIOLET_OUTLINE = Color.valueOf("241738");
    private static final Color VIOLET_OUTER = Color.valueOf("7c3aed");
    private static final Color VIOLET_MID = Color.valueOf("a78bfa");
    private static final Color VIOLET_INNER = Color.valueOf("3b1d66");
    private static final Color VIOLET_LIGHT = Color.valueOf("9d7af2");

    private static final String VERT_SRC =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = a_position;\n" +
        "}\n";

    private static final String FRAG_SRC =
        "uniform sampler2D u_texture;\n" +
        "uniform float u_saturation;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    vec4 c = texture2D(u_texture, v_texCoords);\n" +
        "    float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "    vec3 rgb = mix(vec3(luma), c.rgb, u_saturation);\n" +
        "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
        "}\n";

    private static boolean initialized = false;
    private static boolean shaderTried = false;
    private static Shader postShader = null;
    private static FrameBuffer postBuf = null;
    private static boolean capturing = false;
    private static float curSat = 1.0f;

    private static final Rect camRect = new Rect();
    private static final Runnable beginPost = NexDarkGraphics::beginCapture;
    private static final Runnable endPost = NexDarkGraphics::endCapture;

    private NexDarkGraphics() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(ContentInitEvent.class, e -> applyDarkStyleToContent());

        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> Core.app.post(() -> {
                registerGraphicsSettings();
                initShader();
            }));
            Events.run(Trigger.draw, NexDarkGraphics::onDraw);
        }
    }

    /** Thêm thanh kéo Saturation và Độ đậm viền đen vào Cài đặt -> Đồ họa của game. */
    private static void registerGraphicsSettings() {
        if (Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.graphics == null) return;
        try {
            if (!Core.settings.has(SATURATION_KEY)) Core.settings.put(SATURATION_KEY, 100);
            if (!Core.settings.has(DARK_GLOW_KEY)) Core.settings.put(DARK_GLOW_KEY, 70);

            boolean en = false;
            try {
                String loc = Core.settings.getString("locale", "default");
                en = loc != null && loc.toLowerCase().startsWith("en");
            } catch (Throwable ignored) {}

            final String satTitle = en ? "Saturation (Color Intensity)" : "Độ bão hòa màu (Saturation)";
            final String darkTitle = en ? "Violet Glow Outline (Bullets / Units)" : "Độ sáng viền tím (Đạn / Unit)";
            final String offTxt = en ? "Off" : "Tắt";

            try {
                if (Core.bundle != null && Core.bundle.getProperties() != null) {
                    Core.bundle.getProperties().put("setting." + SATURATION_KEY + ".name", satTitle);
                    Core.bundle.getProperties().put("setting." + DARK_GLOW_KEY + ".name", darkTitle);
                }
            } catch (Throwable ignored) {}

            Vars.ui.settings.graphics.sliderPref(SATURATION_KEY, 100, 0, 200, 5, i -> i + "%");
            Vars.ui.settings.graphics.sliderPref(DARK_GLOW_KEY, 70, 0, 100, 5, i -> i <= 0 ? offTxt : i + "%");
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] registerGraphicsSettings failed", err);
        }
    }

    private static void initShader() {
        if (shaderTried || Vars.headless) return;
        shaderTried = true;
        try {
            postShader = new Shader(VERT_SRC, FRAG_SRC);
            postBuf = new FrameBuffer();
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] Failed to compile saturation shader", err);
            postShader = null;
            postBuf = null;
        }
    }

    /** Đổi viền phát sáng trắng trên pháo, unit và đạn sang viền đen đậm sắc nét. */
    private static void applyDarkStyleToContent() {
        try {
            HashSet<BulletType> seen = new HashSet<>();
            for (Block blk : Vars.content.blocks()) {
                if (!(blk instanceof Turret)) continue;
                blk.outlineColor = VIOLET_OUTLINE;
                if (blk.outlineRadius < 3) blk.outlineRadius = 3;
                patchTurretBullets(blk, seen);
            }
            for (UnitType ut : Vars.content.units()) {
                if (ut == null) continue;
                ut.outlineColor = VIOLET_OUTLINE;
                if (ut.outlineRadius < 3) ut.outlineRadius = 3;
                if (ut.weapons != null) {
                    for (int i = 0; i < ut.weapons.size; i++) {
                        Weapon w = ut.weapons.get(i);
                        if (w != null && w.bullet != null) patchBullet(w.bullet, seen);
                    }
                }
            }
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] applyDarkStyleToContent failed", err);
        }
    }

    private static void patchTurretBullets(Block blk, HashSet<BulletType> seen) {
        try {
            Object ammo = blk.getClass().getField("ammoTypes").get(blk);
            if (ammo instanceof ObjectMap) {
                for (Object v : ((ObjectMap<?, ?>) ammo).values()) {
                    if (v instanceof BulletType) patchBullet((BulletType) v, seen);
                }
            }
        } catch (Throwable ignored) {}
        try {
            Object shoot = blk.getClass().getField("shootType").get(blk);
            if (shoot instanceof BulletType) patchBullet((BulletType) shoot, seen);
        } catch (Throwable ignored) {}
    }

    /** Chuyển hào quang sáng trắng sang sắc tím dịu (Amethyst / Soft Lavender), giữ độ tương phản êm mắt. */
    private static void patchBullet(BulletType b, HashSet<BulletType> seen) {
        if (b == null || !seen.add(b)) return;
        try {
            b.lightOpacity *= 0.45f;
            b.lightColor = b.lightColor != null ? b.lightColor.cpy().lerp(VIOLET_LIGHT, 0.72f) : VIOLET_LIGHT.cpy();
            try {
                java.lang.reflect.Field bf = b.getClass().getField("backColor");
                Object v = bf.get(b);
                if (v instanceof Color) {
                    bf.set(b, ((Color) v).cpy().lerp(VIOLET_OUTER, 0.48f));
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
        patchBullet(b.fragBullet, seen);
        patchBullet(b.intervalBullet, seen);
    }

    private static float readSaturation() {
        return Mathf.clamp(Core.settings.getInt(SATURATION_KEY, 100), 0, 200) / 100f;
    }

    private static float readDarkHaloStrength() {
        if (!Core.settings.getBool(BORDER_KEY, true)) return 0f;
        return Mathf.clamp(Core.settings.getInt(DARK_GLOW_KEY, 70), 0, 100) / 100f;
    }

    private static void onDraw() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;

        // 1) Vẽ hào quang viền tím 3 lớp êm mắt (tím khói ngoài -> oải hương dịu -> tím đậm tạo chiều sâu) quanh đạn & unit
        final float darkStr = readDarkHaloStrength();
        if (darkStr > 0.01f) {
            Rect bounds = Core.camera.bounds(camRect);
            Draw.z(Layer.bullet - 0.08f);
            Groups.bullet.intersect(bounds.x, bounds.y, bounds.width, bounds.height, (Cons<Bullet>) b -> {
                if (b == null || !b.isAdded()) return;
                float r = Math.max(3.5f, Math.min(18f, b.hitSize * 1.15f + 2.5f));
                Draw.color(VIOLET_OUTER, 0.14f * darkStr);
                Fill.circle(b.x, b.y, r * 1.65f);
                Draw.color(VIOLET_MID, 0.22f * darkStr);
                Fill.circle(b.x, b.y, r * 1.22f);
                Draw.color(VIOLET_INNER, 0.42f * darkStr);
                Fill.circle(b.x, b.y, r * 0.92f);
            });
            Draw.z(Layer.groundUnit - 0.08f);
            Groups.unit.intersect(bounds.x, bounds.y, bounds.width, bounds.height, (Cons<Unit>) u -> {
                if (u == null || u.dead || !u.isAdded()) return;
                float r = Math.max(6f, u.hitSize * 0.68f + 2.5f);
                Draw.color(VIOLET_OUTER, 0.11f * darkStr);
                Fill.circle(u.x, u.y, r * 1.38f);
                Draw.color(VIOLET_MID, 0.16f * darkStr);
                Fill.circle(u.x, u.y, r * 1.12f);
            });
            Draw.reset();
        }

        // 2) Áp dụng bộ lọc Saturation thời gian thực nếu người chơi kéo khác 100%
        if (!shaderTried) initShader();
        if (postShader == null || postBuf == null) return;
        if (Core.settings.getBool("pixelate", false)) return;

        curSat = readSaturation();
        if (Math.abs(curSat - 1f) < 0.005f) return;

        Draw.draw(Layer.floor - 9f, beginPost);
        Draw.draw(Layer.light + 5f, endPost);
    }

    private static void beginCapture() {
        capturing = false;
        if (postBuf == null || postShader == null) return;
        int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;
        try {
            postBuf.resize(w, h);
            if (!postBuf.isBound()) {
                postBuf.begin(Color.clear);
                capturing = true;
            }
        } catch (Throwable err) {
            capturing = false;
        }
    }

    private static void endCapture() {
        if (!capturing || postBuf == null || postShader == null) return;
        capturing = false;
        try {
            if (!postBuf.isBound()) return;
            postBuf.end();
            Draw.flush();
            postShader.bind();
            postShader.setUniformf("u_saturation", curSat);
            postBuf.blit(postShader);
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] post blit failed", err);
        }
    }
}
