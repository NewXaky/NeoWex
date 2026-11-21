package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mathf;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Buffers;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

import java.nio.IntBuffer;

/**
 * Đồ họa Bloom & Saturation NeoWex (tương thích tuyệt đối cả PC OpenGL 2.0 lẫn Mobile Android Mali/Adreno GLES 3.2):
 * - Cấp phát FrameBuffer lười (lazy): khi tắt Bloom & Saturation=100% hoặc ngoài menu -> giải phóng VRAM hoàn toàn (0 MB).
 * - Luôn giữ postBuf khớp đúng kích thước màn hình (không đổi viewport lệch với các renderer khác của game, không resize giật khi FPS đổi).
 * - Lưu & khôi phục chính xác cả FBO lẫn GL_VIEWPORT gốc (4 giá trị), đồng thời hỗ trợ rebindIfCapturing() cho các pass phụ.
 */
public final class NexDarkGraphics {
    public static final String BORDER_KEY = "newex-dark-border";
    public static final String SATURATION_KEY = "newex-saturation";
    public static final String BLOOM_KEY = "newex-dark-border-str";
    public static final String BLOOM_COLOR_KEY = "newex-bloom-color";
    public static final Color VIOLET_OUTLINE = Color.valueOf("241738");
    private static final Color bloomCol = Color.valueOf("8f47f0");

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

    /** Shader 4-tap bilinear (tận dụng lọc tuyến tính phần cứng = 16 texel trong 4 lần đọc -> cực nhẹ trên Intel/Mobile!). */
    private static final String FRAG_SRC =
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_invsize;\n" +
        "uniform float u_saturation;\n" +
        "uniform float u_bloom;\n" +
        "uniform vec3 u_bloomColor;\n" +
        "varying vec2 v_texCoords;\n" +
        "vec3 brightTap(vec2 uv){\n" +
        "    vec3 col = texture2D(u_texture, uv).rgb;\n" +
        "    float peak = max(col.r, max(col.g, col.b));\n" +
        "    float mask = smoothstep(0.66, 0.94, peak);\n" +
        "    return col * mask;\n" +
        "}\n" +
        "void main(){\n" +
        "    vec4 c = texture2D(u_texture, v_texCoords);\n" +
        "    vec3 rgb = c.rgb;\n" +
        "    if(u_bloom > 0.005){\n" +
        "        vec2 d = u_invsize * 2.35;\n" +
        "        vec3 acc = (brightTap(v_texCoords + vec2( d.x,  d.y))\n" +
        "                  + brightTap(v_texCoords + vec2(-d.x,  d.y))\n" +
        "                  + brightTap(v_texCoords + vec2( d.x, -d.y))\n" +
        "                  + brightTap(v_texCoords + vec2(-d.x, -d.y))) * 0.25;\n" +
        "        float glowAmt = max(acc.r, max(acc.g, acc.b));\n" +
        "        vec3 customTint = u_bloomColor * glowAmt;\n" +
        "        vec3 softBloom = mix(customTint, acc * (u_bloomColor * 0.65 + vec3(0.35)), 0.28);\n" +
        "        float centerBright = smoothstep(0.72, 0.98, max(rgb.r, max(rgb.g, rgb.b)));\n" +
        "        vec3 haloOnly = softBloom * (1.0 - centerBright * 0.62);\n" +
        "        rgb = 1.0 - (1.0 - rgb) * (1.0 - clamp(haloOnly * (1.15 * u_bloom), 0.0, 0.84));\n" +
        "    }\n" +
        "    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));\n" +
        "    rgb = mix(vec3(luma), rgb, u_saturation);\n" +
        "    gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), c.a);\n" +
        "}\n";

    static final class PostShader extends Shader {
        float invW = 1f, invH = 1f, sat = 1f, bloom = 0.65f;
        float br = 0.56f, bg = 0.28f, bb = 0.94f;
        PostShader() { super(VERT_SRC, FRAG_SRC); }
        @Override
        public void apply() {
            setUniformf("u_invsize", invW, invH);
            setUniformf("u_saturation", sat);
            setUniformf("u_bloom", bloom);
            setUniformf("u_bloomColor", br, bg, bb);
        }
    }

    private static boolean initialized = false;
    private static boolean shaderTried = false;
    private static PostShader postShader = null;
    private static FrameBuffer postBuf = null;
    private static TextureRegion postReg = null;
    private static boolean capturing = false;
    private static int prevOuterFbo = 0;
    private static int prevVpX = 0, prevVpY = 0, prevVpW = 1, prevVpH = 1;
    private static int curW = 1, curH = 1;
    private static float curSat = 1f, curBloom = 0.65f;

    private static final IntBuffer fboQueryBuf = Buffers.newIntBuffer(1);
    private static final IntBuffer vpQueryBuf = Buffers.newIntBuffer(4);
    private static final Runnable beginPost = NexDarkGraphics::beginCapture;
    private static final Runnable endPost = NexDarkGraphics::endCapture;

    private NexDarkGraphics() {}

    private static int currentFboBinding() {
        try {
            fboQueryBuf.clear();
            fboQueryBuf.put(0, 0);
            Gl.getIntegerv(GL20.GL_FRAMEBUFFER_BINDING, fboQueryBuf);
            return fboQueryBuf.get(0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void saveOuterState() {
        prevOuterFbo = currentFboBinding();
        prevVpX = 0;
        prevVpY = 0;
        prevVpW = Math.max(1, Core.graphics.getWidth());
        prevVpH = Math.max(1, Core.graphics.getHeight());
        try {
            vpQueryBuf.clear();
            Gl.getIntegerv(GL20.GL_VIEWPORT, vpQueryBuf);
            int vw = vpQueryBuf.get(2), vh = vpQueryBuf.get(3);
            if (vw > 0 && vh > 0) {
                prevVpX = vpQueryBuf.get(0);
                prevVpY = vpQueryBuf.get(1);
                prevVpW = vw;
                prevVpH = vh;
            }
        } catch (Throwable ignored) {}
    }

    private static void restoreOuterState() {
        try {
            Gl.bindFramebuffer(GL20.GL_FRAMEBUFFER, prevOuterFbo);
            Gl.viewport(prevVpX, prevVpY, Math.max(1, prevVpW), Math.max(1, prevVpH));
        } catch (Throwable ignored) {}
    }

    /** Nếu NexDarkGraphics đang mở postBuf, gắn lại đúng postBuf + viewport của nó (dùng sau các FBO pass phụ như lỗ đen/khiên). */
    public static boolean rebindIfCapturing() {
        if (!capturing || postBuf == null) return false;
        try {
            postBuf.bind();
            Gl.viewport(0, 0, postBuf.getWidth(), postBuf.getHeight());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void disposeBuffer() {
        capturing = false;
        if (postBuf != null) {
            try { postBuf.dispose(); } catch (Throwable ignored) {}
            postBuf = null;
            postReg = null;
        }
    }

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(ContentInitEvent.class, e -> applyDarkOutlines());

        if (!Vars.headless) {
            Events.on(ClientLoadEvent.class, e -> Core.app.post(() -> {
                registerGraphicsSettings();
                initShader();
            }));
            Events.on(WorldLoadEvent.class, e -> disposeBuffer());
            Events.run(Trigger.draw, NexDarkGraphics::onDraw);
        }
    }

    private static int defaultBloomPct() {
        return Vars.mobile ? 45 : 65;
    }

    /** Thêm thanh kéo Saturation & Bloom vào Cài đặt -> Đồ họa của game. */
    private static void registerGraphicsSettings() {
        if (Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.graphics == null) return;
        try {
            int defBloom = defaultBloomPct();
            if (!Core.settings.has(SATURATION_KEY)) Core.settings.put(SATURATION_KEY, 100);
            if (!Core.settings.has(BLOOM_KEY)) Core.settings.put(BLOOM_KEY, defBloom);
            if (!Core.settings.has(BLOOM_COLOR_KEY)) Core.settings.put(BLOOM_COLOR_KEY, "8f47f0");
            loadBloomColor();

            boolean en = false;
            try {
                String loc = Core.settings.getString("locale", "default");
                en = loc != null && loc.toLowerCase().startsWith("en");
            } catch (Throwable ignored) {}
            final boolean isEn = en;

            final String satTitle = en ? "Saturation (Color Intensity)" : "Độ bão hòa màu (Saturation)";
            final String bloomTitle = en ? "Bloom Glow Intensity (NeoWex)" : "Độ sáng viền Bloom (NeoWex)";
            final String offTxt = en ? "Off" : "Tắt";

            try {
                if (Core.bundle != null && Core.bundle.getProperties() != null) {
                    Core.bundle.getProperties().put("setting." + SATURATION_KEY + ".name", satTitle);
                    Core.bundle.getProperties().put("setting." + BLOOM_KEY + ".name", bloomTitle);
                }
            } catch (Throwable ignored) {}

            Vars.ui.settings.graphics.sliderPref(SATURATION_KEY, 100, 0, 200, 5, i -> i + "%");
            Vars.ui.settings.graphics.sliderPref(BLOOM_KEY, defBloom, 0, 100, 5, i -> i <= 0 ? offTxt : i + "%");
            Vars.ui.settings.graphics.pref(new SettingsTable.Setting("newex-bloom-color-picker") {
                @Override
                public void add(SettingsTable table) {
                    Table row = new Table();
                    row.left();
                    final TextButton pickBtn = new TextButton(isEn ? "Pick Bloom Color" : "Chọn màu Bloom (Bảng màu)", Styles.defaultt);
                    pickBtn.update(() -> pickBtn.setColor(Tmp.c1.set(bloomCol).lerp(Color.white, 0.25f)));
                    pickBtn.clicked(() -> {
                        if (Vars.ui != null && Vars.ui.picker != null) {
                            Vars.ui.picker.show(bloomCol.cpy(), c -> {
                                bloomCol.set(c);
                                Core.settings.put(BLOOM_COLOR_KEY, bloomCol.toString());
                            });
                        }
                    });
                    row.add(pickBtn).height(40f).padRight(8f);
                    final String[] presets = {"8f47f0", "38bdf8", "f472b6", "fbbf24", "34d399"};
                    for (final String hex : presets) {
                        final Color pc = Color.valueOf(hex);
                        TextButton sw = new TextButton("■", Styles.defaultt);
                        sw.setColor(pc);
                        sw.clicked(() -> {
                            bloomCol.set(pc);
                            Core.settings.put(BLOOM_COLOR_KEY, hex);
                        });
                        row.add(sw).size(36f, 36f).padRight(4f);
                    }
                    table.add(row).left().padTop(2f).padBottom(6f).row();
                }
            });
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] registerGraphicsSettings failed", err);
        }
    }

    private static void loadBloomColor() {
        try {
            Color.valueOf(bloomCol, Core.settings.getString(BLOOM_COLOR_KEY, "8f47f0"));
        } catch (Throwable ignored) {
            bloomCol.set(Color.valueOf("8f47f0"));
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
        return Mathf.clamp(Core.settings.getInt(BLOOM_KEY, defaultBloomPct()), 0, 100) / 100f;
    }

    private static void onDraw() {
        if (Vars.headless || Vars.state == null || !Vars.state.isGame()) {
            if (postBuf != null) disposeBuffer();
            return;
        }
        if (!shaderTried) initShader();
        if (postShader == null) return;
        if (Core.settings.getBool("pixelate", false)) {
            if (postBuf != null) disposeBuffer();
            return;
        }

        curSat = readSaturation();
        curBloom = readBloomStrength();
        if (Math.abs(curSat - 1f) < 0.005f && curBloom < 0.005f) {
            if (postBuf != null) disposeBuffer();
            return;
        }

        // Kẹp từ Layer.floor - 9f (1.0f) đến Layer.light - 1f (139.0f, ngay TRƯỚC LightRenderer ở 140.0f).
        // NexDynamicShadows tự dựng FBO bóng ở Layer.floor - 9.5f (0.5f, TRƯỚC khi postBuf mở) nên tuyệt đối không bao giờ xung đột FBO/viewport!
        Draw.draw(Layer.floor - 9f, beginPost);
        Draw.draw(Layer.light - 1f, endPost);
    }

    private static void beginCapture() {
        capturing = false;
        if (postShader == null) return;
        int w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        if (w <= 0 || h <= 0) return;
        curW = w;
        curH = h;

        try {
            Draw.flush();
            saveOuterState();
            if (postBuf == null || postBuf.getTexture() == null || postBuf.getTexture().getTextureObjectHandle() == 0) {
                disposeBuffer();
                postBuf = new FrameBuffer(w, h);
                postBuf.getTexture().setFilter(Texture.TextureFilter.linear);
                postReg = new TextureRegion(postBuf.getTexture());
                postReg.flip(false, true);
            } else if (postBuf.getWidth() != w || postBuf.getHeight() != h) {
                postBuf.resize(w, h);
                postBuf.getTexture().setFilter(Texture.TextureFilter.linear);
                postReg = new TextureRegion(postBuf.getTexture());
                postReg.flip(false, true);
            }
            postBuf.begin(Color.clear);
            capturing = true;
        } catch (Throwable err) {
            capturing = false;
            restoreOuterState();
        }
    }

    private static void endCapture() {
        if (!capturing || postBuf == null || postShader == null) return;
        capturing = false;
        try {
            Draw.flush();
            postBuf.end();
            // Khôi phục đúng FBO & viewport gốc trước khi vẽ kết quả Saturation + Bloom
            restoreOuterState();

            postShader.invW = 1f / Math.max(1, curW);
            postShader.invH = 1f / Math.max(1, curH);
            postShader.sat = curSat;
            postShader.bloom = curBloom;
            postShader.br = bloomCol.r;
            postShader.bg = bloomCol.g;
            postShader.bb = bloomCol.b;

            Tmp.m2.set(Draw.proj());
            Draw.proj(Tmp.m1.setOrtho(0f, 0f, curW, curH));
            Draw.blend(Blending.disabled);
            Draw.shader(postShader);
            Draw.color(Color.white, 1f);
            Draw.rect(postReg, curW * 0.5f, curH * 0.5f, curW, curH);
            Draw.flush();
            Draw.shader();
            Draw.blend(Blending.normal);
            Draw.proj(Tmp.m2);
            Draw.reset();
        } catch (Throwable err) {
            Log.err("[NexDarkGraphics] post blit failed", err);
            restoreOuterState();
        }
    }
}
