package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * Đồ họa Bloom Tím ôm sát hình dáng & Saturation NeoWex (tương thích OpenGL 2.0 / Intel GPU / Mobile):
 * - Dùng shader hậu xử lý trích xuất vùng phát sáng thực tế trên màn hình (đạn, tia khoan ore, tia xây/phá khối,
 *   sét, kỹ năng, động cơ, lò phản ứng...) và phủ quầng bloom tím thạch anh êm mắt ôm sát đúng hình dáng vật thể.
 * - Thêm 2 thanh kéo thả trong Cài đặt -> Đồ họa: Độ bão hòa màu (Saturation) & Độ sáng Bloom tím.
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final String SATURATION_KEY = "newex-saturation";
    public static final String BLOOM_KEY = "newex-dark-border-str";
    public static final Color VIOLET_OUTLINE = Color.valueOf("241738");

    private static final String VERT_SRC =
        "attribute vec4 a_position;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = a_position;\n" +
        "}\n";

    /**
     * Shader hậu xử lý OpenGL 2.0:
     * 1) Lấy mẫu 2 vòng sát (1.5px & 3.5px) để tạo quầng bloom tím ôm sát hình dáng mọi thứ phát sáng
     *    (đạn, tia laser khoan/xây/phá khối, sét, lõi, hiệu ứng...).
     * 2) Tông tím thạch anh dịu (Amethyst-Lavender) pha nhẹ màu gốc để gây nghiện mà êm mắt, không chói gắt.
     * 3) Chỉnh độ bão hòa màu (Saturation) theo thanh kéo.
     */
    private static final String FRAG_SRC =
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_invsize;\n" +
        "uniform float u_saturation;\n" +
        "uniform float u_bloom;\n" +
        "varying vec2 v_texCoords;\n" +
        "vec3 brightTap(vec2 uv){\n" +
        "    vec3 col = texture2D(u_texture, uv).rgb;\n" +
        "    float peak = max(col.r, max(col.g, col.b));\n" +
        "    float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));\n" +
        "    float mask = smoothstep(0.68, 0.96, max(peak, luma));\n" +
        "    return col * mask;\n" +
        "}\n" +
        "void main(){\n" +
        "    vec4 c = texture2D(u_texture, v_texCoords);\n" +
        "    vec3 rgb = c.rgb;\n" +
        "    if(u_bloom > 0.005){\n" +
        "        vec2 px = u_invsize;\n" +
        "        vec2 r1 = px * 1.6;\n" +
        "        vec2 r2 = px * 3.6;\n" +
        "        vec3 acc = vec3(0.0);\n" +
        "        acc += brightTap(v_texCoords + vec2( r1.x, 0.0)) * 0.14;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r1.x, 0.0)) * 0.14;\n" +
        "        acc += brightTap(v_texCoords + vec2(0.0,  r1.y)) * 0.14;\n" +
        "        acc += brightTap(v_texCoords + vec2(0.0, -r1.y)) * 0.14;\n" +
        "        acc += brightTap(v_texCoords + vec2( r1.x,  r1.y) * 0.75) * 0.10;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r1.x,  r1.y) * 0.75) * 0.10;\n" +
        "        acc += brightTap(v_texCoords + vec2( r1.x, -r1.y) * 0.75) * 0.10;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r1.x, -r1.y) * 0.75) * 0.10;\n" +
        "        acc += brightTap(v_texCoords + vec2( r2.x, 0.0)) * 0.055;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r2.x, 0.0)) * 0.055;\n" +
        "        acc += brightTap(v_texCoords + vec2(0.0,  r2.y)) * 0.055;\n" +
        "        acc += brightTap(v_texCoords + vec2(0.0, -r2.y)) * 0.055;\n" +
        "        acc += brightTap(v_texCoords + vec2( r2.x,  r2.y) * 0.72) * 0.035;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r2.x,  r2.y) * 0.72) * 0.035;\n" +
        "        acc += brightTap(v_texCoords + vec2( r2.x, -r2.y) * 0.72) * 0.035;\n" +
        "        acc += brightTap(v_texCoords + vec2(-r2.x, -r2.y) * 0.72) * 0.035;\n" +
        "        float glowAmt = max(acc.r, max(acc.g, acc.b));\n" +
        "        vec3 violetTint = vec3(0.56, 0.28, 0.94) * glowAmt;\n" +
        "        vec3 softBloom = mix(violetTint, acc * vec3(0.78, 0.56, 1.05), 0.28);\n" +
        "        float centerBright = smoothstep(0.72, 0.98, max(rgb.r, max(rgb.g, rgb.b)));\n" +
        "        vec3 haloOnly = softBloom * (1.0 - centerBright * 0.62);\n" +
        "        rgb = 1.0 - (1.0 - rgb) * (1.0 - clamp(haloOnly * (0.95 * u_bloom), 0.0, 0.82));\n" +
        "    }\n" +
        "    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "    rgb = mix(vec3(luma), rgb, u_saturation);\n" +
        "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
        "}\n";

    private static boolean initialized = false;
    private static boolean shaderTried = false;
    private static Shader postShader = null;
    private static FrameBuffer postBuf = null;
    private static boolean capturing = false;
    private static float curSat = 1.0f;
    private static float curBloom = 0.65f;
    private static int curW = 1, curH = 1;

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
            Events.run(Trigger.draw, NexDarkGraphics::onDraw);
        }
    }

    /** Thêm thanh kéo Saturation & Bloom tím vào Cài đặt -> Đồ họa của game. */
    private static void registerGraphicsSettings() {
        if (Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.graphics == null) return;
        try {
            if (!Core.settings.has(SATURATION_KEY)) Core.settings.put(SATURATION_KEY, 100);
            if (!Core.settings.has(BLOOM_KEY)) Core.settings.put(BLOOM_KEY, 65);

            boolean en = false;
            try {
                String loc = Core.settings.getString("locale", "default");
                en = loc != null && loc.toLowerCase().startsWith("en");
            } catch (Throwable ignored) {}

            final String satTitle = en ? "Saturation (Color Intensity)" : "Độ bão hòa màu (Saturation)";
            final String bloomTitle = en ? "Violet Bloom Glow (NeoWex)" : "Độ sáng Bloom tím (NeoWex)";
            final String offTxt = en ? "Off" : "Tắt";

            try {
                if (Core.bundle != null && Core.bundle.getProperties() != null) {
                    Core.bundle.getProperties().put("setting." + SATURATION_KEY + ".name", satTitle);
                    Core.bundle.getProperties().put("setting." + BLOOM_KEY + ".name", bloomTitle);
                }
            } catch (Throwable ignored) {}

            Vars.ui.settings.graphics.sliderPref(SATURATION_KEY, 100, 0, 200, 5, i -> i + "%");
            Vars.ui.settings.graphics.sliderPref(BLOOM_KEY, 65, 0, 100, 5, i -> i <= 0 ? offTxt : i + "%");
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
            Log.err("[NexDarkGraphics] Failed to compile violet bloom shader", err);
            postShader = null;
            postBuf = null;
        }
    }

    /** Gán viền tím than đậm sắc nét cho pháo & unit. */
    private static void applyDarkOutlines() {
        try {
            for (Block blk : Vars.content.blocks()) {
                if (!(blk instanceof Turret)) continue;
                blk.outlineColor = VIOLET_OUTLINE;
                if (blk.outlineRadius < 3) blk.outlineRadius = 3;
            }
            for (UnitType ut : Vars.content.units()) {
                if (ut == null) continue;
                ut.outlineColor = VIOLET_OUTLINE;
                if (ut.outlineRadius < 3) ut.outlineRadius = 3;
            }
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] applyDarkOutlines failed", err);
        }
    }

    private static float readSaturation() {
        return Mathf.clamp(Core.settings.getInt(SATURATION_KEY, 100), 0, 200) / 100f;
    }

    private static float readBloomStrength() {
        if (!Core.settings.getBool(BORDER_KEY, true)) return 0f;
        return Mathf.clamp(Core.settings.getInt(BLOOM_KEY, 65), 0, 100) / 100f;
    }

    private static void onDraw() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
        if (!shaderTried) initShader();
        if (postShader == null || postBuf == null) return;
        if (Core.settings.getBool("pixelate", false)) return;

        curSat = readSaturation();
        curBloom = readBloomStrength();
        if (Math.abs(curSat - 1f) < 0.005f && curBloom < 0.005f) return;

        Draw.draw(Layer.floor - 9f, beginPost);
        Draw.draw(Layer.light + 5f, endPost);
    }

    private static void beginCapture() {
        capturing = false;
        if (postBuf == null || postShader == null) return;
        int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;
        curW = w;
        curH = h;
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
            postShader.setUniformf("u_invsize", 1f / Math.max(1, curW), 1f / Math.max(1, curH));
            postShader.setUniformf("u_saturation", curSat);
            postShader.setUniformf("u_bloom", curBloom);
            postBuf.blit(postShader);
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] post blit failed", err);
        }
    }
}
