package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
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
 * Đồ họa Bloom Tím ôm sát hình dáng & Saturation NeoWex (tương thích OpenGL 2.0 / Intel GPU / Mobile):
 * - KHÔNG dùng FrameBuffer lồng nhau (dùng Gl.copyTexSubImage2D tại Layer.end) -> tuyệt đối không xung đột FBO
 *   với LightRenderer (ngày/đêm), NexDynamicShadows, Bloom hay khiên lực -> không bao giờ bị đen nền!
 * - Tự động trích xuất vùng phát sáng thật trên màn hình (đạn, tia khoan ore, tia xây/phá khối, sét, kỹ năng...)
 *   và phủ quầng bloom tím thạch anh êm mắt ôm sát hình dáng + chỉnh độ bão hòa màu (Saturation).
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final String SATURATION_KEY = "newex-saturation";
    public static final String BLOOM_KEY = "newex-dark-border-str";
    public static final Color VIOLET_OUTLINE = Color.valueOf("241738");

    private static final String VERT_SRC =
        "attribute vec4 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec2 v_texCoords;\n" +
        "void main(){\n" +
        "    v_texCoords = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

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
        "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);\n" +
        "}\n";

    static final class PostShader extends Shader {
        float invW = 1f, invH = 1f, sat = 1f, bloom = 0.65f;
        PostShader() { super(VERT_SRC, FRAG_SRC); }
        @Override
        public void apply() {
            setUniformf("u_invsize", invW, invH);
            setUniformf("u_saturation", sat);
            setUniformf("u_bloom", bloom);
        }
    }

    private static boolean initialized = false;
    private static boolean shaderTried = false;
    private static PostShader postShader = null;
    private static Texture screenTex = null;
    private static TextureRegion screenReg = null;
    private static int texW = 0, texH = 0;

    private static final Runnable applyPost = NexDarkGraphics::renderPostPass;

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
            postShader = new PostShader();
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] Failed to compile violet bloom shader", err);
            postShader = null;
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
        if (postShader == null) return;
        if (Core.settings.getBool("pixelate", false)) return;

        float sat = readSaturation();
        float bloom = readBloomStrength();
        if (Math.abs(sat - 1f) < 0.005f && bloom < 0.005f) return;

        postShader.sat = sat;
        postShader.bloom = bloom;
        // Chạy ở Layer.end (SAU toàn bộ thế giới, bóng, ánh sáng, bloom gốc, TRƯỚC HUD/UI) -> không bao giờ xung đột FBO!
        Draw.draw(Layer.end, applyPost);
    }

    private static void renderPostPass() {
        if (postShader == null) return;
        int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            Draw.flush();
            if (screenTex == null || texW != w || texH != h || screenTex.getTextureObjectHandle() == 0) {
                if (screenTex != null) { try { screenTex.dispose(); } catch (Throwable ignored) {} }
                screenTex = new Texture(w, h, Pixmap.Format.rgba8888);
                screenTex.setFilter(Texture.TextureFilter.linear);
                screenTex.setWrap(Texture.TextureWrap.clampToEdge);
                screenReg = new TextureRegion(screenTex);
                screenReg.flip(false, true);
                texW = w;
                texH = h;
            }

            // Sao chép trực tiếp khung hình thế giới đã vẽ vào texture (không cần bind FBO lồng nhau!)
            screenTex.bind(0);
            Gl.copyTexSubImage2D(GL20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);

            postShader.invW = 1f / Math.max(1, w);
            postShader.invH = 1f / Math.max(1, h);

            Tmp.m2.set(Draw.proj());
            Draw.proj(Tmp.m1.setOrtho(0f, 0f, w, h));
            Draw.blend(arc.graphics.Blending.disabled);
            Draw.shader(postShader);
            Draw.color(Color.white, 1f);
            Draw.rect(screenReg, w * 0.5f, h * 0.5f, w, h);
            Draw.flush();
            Draw.shader();
            Draw.blend(arc.graphics.Blending.normal);
            Draw.proj(Tmp.m2);
            Draw.reset();
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] post pass failed", err);
        }
    }
}
