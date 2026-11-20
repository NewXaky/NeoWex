package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Đồ họa NeoWex (OpenGL 2.0 / GLES 2.0 tương thích Intel & Mobile):
 * 1) Áp viền đen đậm (outlineColor + outlineRadius) cho pháo & unit NeoWex.
 * 2) Hậu xử lý màn hình (FrameBuffer + Shader): tùy chỉnh Saturation (độ bão hòa màu) và viền đen (Vignette).
 * 3) Thêm thanh kéo thả Saturation & Độ đậm viền đen trực tiếp trong Cài đặt -> Đồ họa của game.
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final String SATURATION_KEY = "newex-saturation";
    public static final String VIGNETTE_KEY = "newex-dark-border-str";
    public static final Color DARK_OUTLINE = Color.valueOf("1b1b26");

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
        "uniform float u_vignette;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    vec4 c = texture2D(u_texture, v_texCoords);\n" +
        "    float luma = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "    vec3 rgb = mix(vec3(luma), c.rgb, u_saturation);\n" +
        "    if(u_vignette > 0.001){\n" +
        "        vec2 p = v_texCoords - vec2(0.5, 0.5);\n" +
        "        float dist = length(p * vec2(1.18, 1.18));\n" +
        "        float vig = smoothstep(0.36, 0.86, dist);\n" +
        "        rgb *= (1.0 - vig * u_vignette);\n" +
        "    }\n" +
        "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
        "}\n";

    private static boolean initialized = false;
    private static boolean shaderTried = false;
    private static Shader postShader = null;
    private static FrameBuffer postBuf = null;
    private static boolean capturing = false;
    private static float curSat = 1.15f;
    private static float curVig = 0.45f;

    private static final Color tmpEdge = new Color(0f, 0f, 0f, 0.72f);
    private static final Color tmpZero = new Color(0f, 0f, 0f, 0f);

    private static final Runnable beginPost = NexDarkGraphics::beginCapture;
    private static final Runnable endPost = NexDarkGraphics::endCapture;

    private NexDarkGraphics() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(ContentInitEvent.class, e -> applyDarkOutlines());

        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> Core.app.post(() -> {
                registerGraphicsSettings();
                initShader();
            }));
            Events.run(Trigger.draw, NexDarkGraphics::schedulePost);
            Events.run(Trigger.uiDrawEnd, NexDarkGraphics::drawFallbackBorder);
        }
    }

    /** Thêm thanh kéo Saturation và Độ đậm viền đen vào Cài đặt -> Đồ họa của Mindustry. */
    private static void registerGraphicsSettings() {
        if (Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.graphics == null) return;
        try {
            if (!Core.settings.has(SATURATION_KEY)) Core.settings.put(SATURATION_KEY, 115);
            if (!Core.settings.has(VIGNETTE_KEY)) Core.settings.put(VIGNETTE_KEY, 45);

            boolean en = false;
            try {
                String loc = Core.settings.getString("locale", "default");
                en = loc != null && loc.toLowerCase().startsWith("en");
            } catch (Throwable ignored) {}

            final String satTitle = en ? "NeoWex Saturation" : "Độ bão hòa màu (Saturation)";
            final String vigTitle = en ? "NeoWex Dark Border" : "Độ đậm viền đen (NeoWex)";
            final String offTxt = en ? "Off" : "Tắt";

            // Đưa tên hiển thị vào bundle nếu chưa có để SettingsTable hiện chữ tiếng Việt/Anh rõ ràng
            try {
                if (Core.bundle != null && Core.bundle.getProperties() != null) {
                    Core.bundle.getProperties().put("setting." + SATURATION_KEY + ".name", satTitle);
                    Core.bundle.getProperties().put("setting." + VIGNETTE_KEY + ".name", vigTitle);
                }
            } catch (Throwable ignored) {}

            Vars.ui.settings.graphics.sliderPref(SATURATION_KEY, 115, 0, 200, 5, i -> i + "%");
            Vars.ui.settings.graphics.sliderPref(VIGNETTE_KEY, 45, 0, 100, 5, i -> i <= 0 ? offTxt : i + "%");
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
            Log.err("[NexDarkGraphics] Failed to compile post shader, using fallback border", err);
            postShader = null;
            postBuf = null;
        }
    }

    /** Gán màu viền đen đậm và độ dày viền rõ nét cho pháo & unit NeoWex. */
    private static void applyDarkOutlines() {
        try {
            for (Block blk : Vars.content.blocks()) {
                if (blk instanceof Turret && blk.name != null && blk.name.startsWith("newex-")) {
                    blk.outlineColor = DARK_OUTLINE;
                    if (blk.outlineRadius < 3) blk.outlineRadius = 3;
                }
            }
            for (UnitType ut : Vars.content.units()) {
                if (ut != null && ut.name != null && ut.name.startsWith("newex-")) {
                    ut.outlineColor = DARK_OUTLINE;
                    if (ut.outlineRadius < 3) ut.outlineRadius = 3;
                }
            }
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] applyDarkOutlines failed", err);
        }
    }

    private static float readSaturation() {
        return Mathf.clamp(Core.settings.getInt(SATURATION_KEY, 115), 0, 200) / 100f;
    }

    private static float readVignette() {
        if (!Core.settings.getBool(BORDER_KEY, true)) return 0f;
        return Mathf.clamp(Core.settings.getInt(VIGNETTE_KEY, 45), 0, 100) / 100f;
    }

    private static void schedulePost() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
        if (!shaderTried) initShader();
        if (postShader == null || postBuf == null) return;
        if (Core.settings.getBool("pixelate", false)) return;

        curSat = readSaturation();
        curVig = readVignette();
        if (Math.abs(curSat - 1f) < 0.005f && curVig < 0.005f) return;

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
            postShader.setUniformf("u_vignette", curVig);
            postBuf.blit(postShader);
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] post blit failed", err);
        }
    }

    /** Dự phòng vẽ viền đen theo tọa độ màn hình chuẩn (Ortho) khi shader không khả dụng hoặc bật pixelate. */
    private static void drawFallbackBorder() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
        if (postShader != null && postBuf != null && !Core.settings.getBool("pixelate", false)) return;

        float vig = readVignette();
        if (vig < 0.005f) return;

        float w = Core.graphics.getWidth();
        float h = Core.graphics.getHeight();
        if (w <= 0f || h <= 0f) return;

        float borderY = Math.max(28f, h * 0.075f);
        float borderX = Math.max(28f, w * 0.055f);

        float edgeCol = tmpEdge.set(0f, 0f, 0f, Mathf.clamp(vig, 0f, 0.92f)).toFloatBits();
        float clearCol = tmpZero.toFloatBits();

        Draw.flush();
        Tmp.m2.set(Draw.proj());
        Draw.proj(Tmp.m1.setOrtho(0f, 0f, w, h));
        Fill.quad(0f, 0f, edgeCol, w, 0f, edgeCol, w, borderY, clearCol, 0f, borderY, clearCol);
        Fill.quad(0f, h, edgeCol, w, h, edgeCol, w, h - borderY, clearCol, 0f, h - borderY, clearCol);
        Fill.quad(0f, 0f, edgeCol, 0f, h, edgeCol, borderX, h, clearCol, borderX, 0f, clearCol);
        Fill.quad(w, 0f, edgeCol, w, h, edgeCol, w - borderX, h, clearCol, w - borderX, 0f, clearCol);
        Draw.flush();
        Draw.proj(Tmp.m2);
        Draw.reset();
    }
}
