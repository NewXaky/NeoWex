package newex.graphics;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.Texture;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Mat;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.environment.StaticTree;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.environment.TallBlock;
import mindustry.world.blocks.environment.TreeBlock;
import mindustry.world.blocks.power.LightBlock;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.storage.CoreBlock;
import newex.PerfCore;

/**
 * Hệ thống Shader Đổ Bóng & Ánh Sáng Động (gộp 1 FBO hợp nhất, cực mượt, không đè bóng, nguồn sáng xóa bóng):
 * - Gộp toàn bộ bóng tường địa hình, khối xây và prop vào 1 FBO duy nhất ở Layer.block - 0.5f (29.5f):
 *   1) Tuyệt đối KHÔNG đè bóng tối chồng lên nhau.
 *   2) Đặt khối sát tường địa hình không bao giờ bị bóng tường đè lên mặt khối.
 *   3) Chỉ dùng 3 FBO thay vì 15 FBO (nhanh gấp 5x, siêu nhẹ VRAM).
 * - Các nguồn sáng (đèn LightBlock, Lõi, Lò phản ứng, PowerNode, đạn phát sáng, tia bloom, unit phát sáng)
 *   tự động làm tan bóng mượt mà bằng chế độ hòa trộn xóa alpha (Gl.zero, Gl.oneMinusSrcAlpha).
 * - Tích hợp nút bật/tắt tại Cài đặt -> Đồ họa: bật hiện tùy chọn con, tắt ẩn sạch và giải phóng VRAM.
 */
public final class NexDynamicShadows {
    public static final String KEY_ENABLED = "newex-ds-enabled";
    public static final String KEY_DAYNIGHT = "newex-ds-daynight";
    public static final String KEY_ROTATE = "newex-ds-rotate";
    public static final String KEY_UNITS = "newex-ds-units";
    public static final String KEY_ZOOMFADE = "newex-ds-zoomfade";
    public static final String KEY_QUALITY = "newex-ds-quality";
    public static final String KEY_LENGTH = "newex-ds-length";
    public static final String KEY_ALPHA = "newex-ds-alpha";
    public static final String KEY_BLUR = "newex-ds-blur";
    public static final String KEY_TINT = "newex-ds-tint";
    public static final String KEY_CONTACT = "newex-ds-contact";
    public static final String KEY_LIGHT_ERASE = "newex-ds-light-erase";
    public static final String KEY_LITE = "newex-ds-lite";
    public static final String KEY_VIEWONLY = "newex-ds-viewonly";
    private static final String PANEL_SENTINEL = "newex-dynamic-shadows-panel";
    private static final String DS_DEFAULTS_MIGRATED = "newex-ds-defaults-v2";

    public static final boolean DEF_ENABLED = true;
    public static final boolean DEF_LITE = true;
    public static final boolean DEF_VIEWONLY = false;
    public static final boolean DEF_DAYNIGHT = true;
    public static final boolean DEF_UNITS = true;
    public static final boolean DEF_ROTATE = true;
    public static final boolean DEF_ZOOMFADE = false;
    public static final boolean DEF_LIGHT_ERASE = true;
    public static final int DEF_QUALITY = 2;
    public static final int DEF_LENGTH = 12;
    public static final int DEF_ALPHA = 75;
    public static final int DEF_BLUR = 35;
    public static final int DEF_TINT = 85;
    public static final int DEF_CONTACT = 35;

    public static boolean enabled = DEF_ENABLED;
    public static boolean liteMode = DEF_LITE;
    /** true = chỉ dựng bóng đúng vùng đang nhìn thấy (đệm rất nhỏ), ngoài màn hình không xử lý -> đỡ lag. */
    public static boolean viewOnly = DEF_VIEWONLY;
    public static boolean dayNightCycle = DEF_DAYNIGHT;
    public static boolean rotateShadows = DEF_ROTATE;
    public static boolean unitShadowsEnabled = DEF_UNITS;
    public static boolean zoomFadeEnabled = DEF_ZOOMFADE;
    public static boolean lightEraseEnabled = DEF_LIGHT_ERASE;
    /** 0 = Thấp (45%), 1 = Trung bình (65%), 2 = Cao (90%, mặc định theo preset chuẩn) */
    public static int graphicsQuality = DEF_QUALITY;
    public static float shadowLength = DEF_LENGTH;
    public static float shadowAlpha = DEF_ALPHA / 100f;
    public static float blurRadius = DEF_BLUR / 10f;
    public static float shadowTint = DEF_TINT / 100f;
    public static float contactShadow = DEF_CONTACT / 100f;
    public static float darkFadeThreshold = 0.35f, darkFadeStrength = 0.80f;

    public static volatile boolean shadDirty = true;
    private static boolean initialized = false;

    private static final java.nio.IntBuffer fboQueryBuf = arc.util.Buffers.newIntBuffer(1);
    private static final java.nio.IntBuffer vpQueryBuf = arc.util.Buffers.newIntBuffer(4);
    private static int savedVpX = 0, savedVpY = 0, savedVpW = 1, savedVpH = 1;
    private static final Mat savedProj = new Mat();
    private static final Mat shadowProj = new Mat();

    private static int saveFboAndViewport() {
        int prevFbo = 0;
        try {
            fboQueryBuf.clear();
            fboQueryBuf.put(0, 0);
            Gl.getIntegerv(GL20.GL_FRAMEBUFFER_BINDING, fboQueryBuf);
            prevFbo = fboQueryBuf.get(0);
        } catch (Throwable ignored) {}
        savedVpX = 0;
        savedVpY = 0;
        savedVpW = Math.max(1, Core.graphics.getWidth());
        savedVpH = Math.max(1, Core.graphics.getHeight());
        try {
            vpQueryBuf.clear();
            Gl.getIntegerv(GL20.GL_VIEWPORT, vpQueryBuf);
            int vw = vpQueryBuf.get(2), vh = vpQueryBuf.get(3);
            if (vw > 0 && vh > 0) {
                savedVpX = vpQueryBuf.get(0);
                savedVpY = vpQueryBuf.get(1);
                savedVpW = vw;
                savedVpH = vh;
            }
        } catch (Throwable ignored) {}
        return prevFbo;
    }

    private static void restoreFboAndViewport(int prevFbo) {
        try {
            Gl.bindFramebuffer(GL20.GL_FRAMEBUFFER, prevFbo);
            Gl.viewport(savedVpX, savedVpY, Math.max(1, savedVpW), Math.max(1, savedVpH));
        } catch (Throwable ignored) {}
    }

    // Chế độ hòa trộn làm tan bóng mượt quanh nguồn sáng & trừ phần giao bóng núi khỏi bóng khối xây (dst = dst * (1 - srcAlpha))
    private static final Blending LIGHT_ERASE_BLEND = new Blending(Gl.zero, Gl.oneMinusSrcAlpha);

    // Chỉ cần 3 FBO gọn nhẹ (scratchA dùng chung, shadowOut cho bóng khối xây, mtnBlockShadowOut cho bóng địa hình!)
    private static FrameBuffer scratchA, shadowOut, mtnBlockShadowOut;
    private static TextureRegion scratchRegA, shadowRegOut, mtnBlockRegOut;
    private static boolean hasShadowContent = false;
    private static boolean hasMtnBlockShadow = false;

    private static float lastFboScale = -1f;
    private static float lastCachedAngle = -999f;
    private static float lastCachedCamX = 0f, lastCachedCamY = 0f, lastCachedCamW = 1f, lastCachedCamH = 1f;

    private static final ObjectMap<UnitType, TextureRegion> origShadowMap = new ObjectMap<>();
    private static final ObjectMap<UnitType, TextureRegion> origSoftShadowMap = new ObjectMap<>();
    private static final ObjectFloatMap<Block> elevCache = new ObjectFloatMap<>(64);
    private static final ObjectMap<Block, Boolean> floorEraseCache = new ObjectMap<>(64);
    private static TextureRegion emptyRegion;

    private static ShadowBlurShader shadowShader;

    private static final String VERT_SRC =
        "attribute vec4 a_position;\n" +
        "attribute vec4 a_color;\n" +
        "attribute vec2 a_texCoord0;\n" +
        "uniform mat4 u_projTrans;\n" +
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoord;\n" +
        "void main(){\n" +
        "    v_color = a_color;\n" +
        "    v_texCoord = a_texCoord0;\n" +
        "    gl_Position = u_projTrans * a_position;\n" +
        "}\n";

    /** Shader Kawase 1-pass (5-tap chéo -> làm mờ mịn cả 2 trục X/Y chỉ trong 1 pass duy nhất, cắt 50% số FBO pass!). */
    private static final String FRAG_SRC =
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform float u_radius;\n" +
        "uniform float u_sunElevation;\n" +
        "uniform float u_shadowTint;\n" +
        "uniform float u_contactShadow;\n" +
        "void main(){\n" +
        "    vec2 texel = 1.0 / u_resolution;\n" +
        "    float elevFactor = 1.0 - clamp(u_sunElevation, 0.0, 1.0);\n" +
        "    float rad = u_radius * (1.0 + elevFactor * 1.15);\n" +
        "    vec2 d = texel * rad;\n" +
        "    float alpha = texture2D(u_texture, v_texCoord).a * 0.28\n" +
        "                + texture2D(u_texture, v_texCoord + vec2( d.x,  d.y)).a * 0.18\n" +
        "                + texture2D(u_texture, v_texCoord + vec2(-d.x,  d.y)).a * 0.18\n" +
        "                + texture2D(u_texture, v_texCoord + vec2( d.x, -d.y)).a * 0.18\n" +
        "                + texture2D(u_texture, v_texCoord + vec2(-d.x, -d.y)).a * 0.18;\n" +
        "    float distFade = smoothstep(0.0, 0.14, alpha);\n" +
        "    float contactBoost = smoothstep(0.55, 1.0, alpha) * u_contactShadow;\n" +
        "    float sunE = u_sunElevation;\n" +
        "    vec3 dayTint = vec3(0.06, 0.07, 0.13);\n" +
        "    float horizonFactor = exp(-sunE * sunE * 8.0);\n" +
        "    vec3 horizonTint = vec3(0.16, 0.06, 0.02) * horizonFactor;\n" +
        "    float nightFactor = clamp(-sunE, 0.0, 1.0);\n" +
        "    vec3 nightTint = vec3(0.04, 0.02, 0.10) * nightFactor;\n" +
        "    vec3 shadowColor = mix(vec3(0.0), dayTint + horizonTint + nightTint, u_shadowTint);\n" +
        "    float finalAlpha = clamp(alpha * distFade * (1.0 + contactBoost), 0.0, 1.0);\n" +
        "    gl_FragColor = vec4(shadowColor, finalAlpha) * v_color;\n" +
        "}\n";

    static final class ShadowBlurShader extends Shader {
        float radius = 3.0f, shadowTint = 0.6f, contactShadow = 0.45f, sunElevation = 0.5f;
        int resW = 1, resH = 1;

        ShadowBlurShader() {
            super(VERT_SRC, FRAG_SRC);
        }

        @Override
        public void apply() {
            setUniformf("u_radius", radius);
            setUniformf("u_shadowTint", shadowTint);
            setUniformf("u_contactShadow", contactShadow);
            setUniformf("u_sunElevation", sunElevation);
            setUniformf("u_resolution", resW, resH);
        }
    }

    static final class UnitShadowData {
        TextureRegion region;
        boolean flying;
        float x, y, w, h, rotation, alpha;
    }

    static final class LightSpot {
        float x, y, r, strength;
    }

    private static final Seq<UnitShadowData> unitShadows = new Seq<>(64);
    private static final Seq<UnitShadowData> unitShadowPool = new Seq<>(64);
    private static final Seq<LightSpot> dynLights = new Seq<>(64);
    private static final Seq<LightSpot> dynLightPool = new Seq<>(64);

    private static UnitShadowData obtainUnitShadow() {
        return unitShadowPool.size > 0 ? unitShadowPool.pop() : new UnitShadowData();
    }

    private static void freeUnitShadows() {
        unitShadowPool.addAll(unitShadows);
        unitShadows.clear();
    }

    private static void addDynLight(float x, float y, float r, float strength) {
        int cap = (Vars.mobile || PerfCore.level() >= 1) ? 32 : 72;
        if (r <= 2f || strength <= 0.02f || dynLights.size >= cap) return;
        LightSpot ls = dynLightPool.size > 0 ? dynLightPool.pop() : new LightSpot();
        ls.x = x; ls.y = y; ls.r = r; ls.strength = strength;
        dynLights.add(ls);
    }

    private static void freeDynLights() {
        dynLightPool.addAll(dynLights);
        dynLights.clear();
    }

    private NexDynamicShadows() {}

    public static void init() {
        if (initialized) return;
        initialized = true;

        Events.on(EventType.ClientLoadEvent.class, e -> {
            if (!Core.settings.getBool(DS_DEFAULTS_MIGRATED, false)) {
                resetToDefaults();
                Core.settings.put(DS_DEFAULTS_MIGRATED, true);
            } else {
                loadSettings();
                if (!Core.settings.has(PANEL_SENTINEL)) Core.settings.put(PANEL_SENTINEL, true);
            }
            Log.info("[NexDynamicShadows] preDraw v2 initialized (enabled=" + enabled + ", lite=" + liteMode + ")");
            if (!Vars.headless) {
                Core.app.post(() -> {
                    registerGraphicsSettings();
                    applyBlockLayers();
                    updateUnitShadows();
                });
            }
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (Vars.headless) return;
            applyBlockLayers();
            ChunkCache.init();
            shadDirty = true;
        });
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if (!enabled || Vars.headless) return;
            if (e.tile != null) ChunkCache.invalidateTile(e.tile.x, e.tile.y);
            else shadDirty = true;
        });
        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if (!enabled || Vars.headless) return;
            if (e.tile != null) ChunkCache.invalidateTile(e.tile.x, e.tile.y);
            else shadDirty = true;
        });
        Events.on(EventType.TileChangeEvent.class, e -> {
            if (!enabled || Vars.headless) return;
            if (e.tile != null) ChunkCache.invalidateTile(e.tile.x, e.tile.y);
            else shadDirty = true;
        });

        if (!Vars.headless) {
            // Dựng & cập nhật FBO bóng ở Trigger.preDraw (TRƯỚC khi Renderer.draw / SortedSpriteBatch / Bloom / NexDarkGraphics bắt đầu!)
            Events.run(EventType.Trigger.preDraw, NexDynamicShadows::prepareShadows);
            // Trong Trigger.draw chỉ gửi các lệnh Draw.rect vẽ ảnh bóng đã dựng sẵn -> 0 đổi FBO, 0 xung đột trên PC & Mobile!
            Events.run(EventType.Trigger.draw, NexDynamicShadows::queue);
        }
    }

    public static void resetToDefaults() {
        Core.settings.put(KEY_ENABLED, DEF_ENABLED);
        Core.settings.put(KEY_LITE, DEF_LITE);
        Core.settings.put(KEY_VIEWONLY, DEF_VIEWONLY);
        Core.settings.put(KEY_DAYNIGHT, DEF_DAYNIGHT);
        Core.settings.put(KEY_ROTATE, DEF_ROTATE);
        Core.settings.put(KEY_UNITS, DEF_UNITS);
        Core.settings.put(KEY_ZOOMFADE, DEF_ZOOMFADE);
        Core.settings.put(KEY_LIGHT_ERASE, DEF_LIGHT_ERASE);
        Core.settings.put(KEY_QUALITY, DEF_QUALITY);
        Core.settings.put(KEY_LENGTH, DEF_LENGTH);
        Core.settings.put(KEY_ALPHA, DEF_ALPHA);
        Core.settings.put(KEY_BLUR, DEF_BLUR);
        Core.settings.put(KEY_TINT, DEF_TINT);
        Core.settings.put(KEY_CONTACT, DEF_CONTACT);
        Core.settings.put(PANEL_SENTINEL, true);
        loadSettings();
        updateUnitShadows();
        shadDirty = true;
    }

    private static void loadSettings() {
        enabled = Core.settings.getBool(KEY_ENABLED, DEF_ENABLED);
        liteMode = Core.settings.getBool(KEY_LITE, DEF_LITE);
        viewOnly = Core.settings.getBool(KEY_VIEWONLY, DEF_VIEWONLY);
        dayNightCycle = Core.settings.getBool(KEY_DAYNIGHT, DEF_DAYNIGHT);
        rotateShadows = Core.settings.getBool(KEY_ROTATE, DEF_ROTATE);
        unitShadowsEnabled = Core.settings.getBool(KEY_UNITS, DEF_UNITS);
        zoomFadeEnabled = Core.settings.getBool(KEY_ZOOMFADE, DEF_ZOOMFADE);
        graphicsQuality = Core.settings.getInt(KEY_QUALITY, DEF_QUALITY);
        shadowLength = Core.settings.getInt(KEY_LENGTH, DEF_LENGTH);
        shadowAlpha = Core.settings.getInt(KEY_ALPHA, DEF_ALPHA) / 100f;
        blurRadius = Core.settings.getInt(KEY_BLUR, DEF_BLUR) / 10f;
        shadowTint = Core.settings.getInt(KEY_TINT, DEF_TINT) / 100f;
        contactShadow = Core.settings.getInt(KEY_CONTACT, DEF_CONTACT) / 100f;
        lightEraseEnabled = Core.settings.getBool(KEY_LIGHT_ERASE, DEF_LIGHT_ERASE);
    }

    /** Bật/tắt chế độ chỉ vẽ bóng trong màn hình (dùng cho nút HUD). */
    public static void setViewOnly(boolean v) {
        viewOnly = v;
        Core.settings.put(KEY_VIEWONLY, v);
        shadDirty = true;
    }

    private static boolean isEn() {
        try {
            String loc = Core.settings.getString("locale", "default");
            return loc != null && loc.toLowerCase().startsWith("en");
        } catch (Throwable ignored) { return false; }
    }

    /** Nút bật/tắt Đổ bóng & Ánh sáng động tại Cài đặt -> Đồ họa; bật mới hiện tùy chọn con, tắt thì ẩn sạch. */
    private static void registerGraphicsSettings() {
        if (Vars.ui == null || Vars.ui.settings == null || Vars.ui.settings.graphics == null) return;
        try {
            Vars.ui.settings.graphics.pref(new SettingsTable.Setting(PANEL_SENTINEL) {
                @Override
                public void add(SettingsTable table) {
                    if (!Core.settings.has(name)) {
                        resetToDefaults();
                    }
                    final boolean en = isEn();
                    Table wrap = new Table();
                    wrap.left().defaults().left();

                    wrap.add(en ? "[#fed17b]-- Dynamic Shadows & Lighting (NeoWex) --[]" : "[#fed17b]-- Đổ Bóng & Ánh Sáng Động (NeoWex) --[]")
                        .padTop(10f).padBottom(4f).row();

                    final Table subBox = new Table();
                    subBox.left().defaults().left();

                    final Runnable rebuildSub = () -> {
                        subBox.clear();
                        if (!enabled) return;

                        addCheck(subBox, en ? "Lite Mode (Ultra Smooth 60 FPS - 1 Pass)" : "Chế độ Lite (Siêu mượt 60 FPS - không lag)", KEY_LITE, liteMode, v -> {
                            liteMode = v;
                            if (v) {
                                hasMtnBlockShadow = false;
                                if (mtnBlockShadowOut != null) {
                                    try { mtnBlockShadowOut.dispose(); } catch (Throwable ignored) {}
                                    mtnBlockShadowOut = null;
                                    mtnBlockRegOut = null;
                                }
                            }
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Shadows only on screen (less lag)" : "Bóng chỉ trong màn hình (đỡ lag)", KEY_VIEWONLY, viewOnly, v -> {
                            viewOnly = v;
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Day / Night Lighting Cycle" : "Chu kỳ ánh sáng Ngày / Đêm", KEY_DAYNIGHT, dayNightCycle, v -> {
                            dayNightCycle = v;
                            if (!v && Vars.state != null && Vars.state.rules != null && Vars.state.rules.ambientLight != null) {
                                Vars.state.rules.ambientLight.set(0f, 0f, 0f, 0f);
                            }
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Dynamic Unit Shadows" : "Đổ bóng Unit động", KEY_UNITS, unitShadowsEnabled, v -> {
                            unitShadowsEnabled = v;
                            updateUnitShadows();
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Rotate Shadows with Sun" : "Xoay hướng bóng theo mặt trời", KEY_ROTATE, rotateShadows, v -> {
                            rotateShadows = v;
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Zoom Fade Optimization" : "Mờ dần khi thu nhỏ (tối ưu FPS)", KEY_ZOOMFADE, zoomFadeEnabled, v -> {
                            zoomFadeEnabled = v;
                            shadDirty = true;
                        });
                        addCheck(subBox, en ? "Light from Units, Blocks & Emitters in Shadows" : "Ánh sáng từ Unit, Khối & Nguồn sáng trong bóng đổ", KEY_LIGHT_ERASE, lightEraseEnabled, v -> {
                            lightEraseEnabled = v;
                            shadDirty = true;
                        });

                        addSlider(subBox, en ? "Shadow Quality" : "Chất lượng bóng", KEY_QUALITY, graphicsQuality, 0, 2, 1,
                            v -> {
                                graphicsQuality = v;
                                shadDirty = true;
                                if (en) return v == 0 ? "Low (Fast)" : v == 1 ? "Medium (Smooth)" : "High";
                                return v == 0 ? "Thấp (Siêu mượt)" : v == 1 ? "Trung bình (Mượt)" : "Cao";
                            });
                        addSlider(subBox, en ? "Shadow Length" : "Độ dài bóng", KEY_LENGTH, Math.round(shadowLength), 2, 24, 1,
                            v -> { shadowLength = v; shadDirty = true; return v + (en ? " tiles" : " ô"); });
                        addSlider(subBox, en ? "Shadow Opacity" : "Độ đậm bóng", KEY_ALPHA, Math.round(shadowAlpha * 100f), 10, 90, 5,
                            v -> { shadowAlpha = v / 100f; shadDirty = true; return v + "%"; });
                        addSlider(subBox, en ? "Shadow Softness (Blur)" : "Độ mềm bóng (Blur)", KEY_BLUR, Math.round(blurRadius * 10f), 10, 60, 5,
                            v -> { blurRadius = v / 10f; shadDirty = true; return (v / 10f) + " px"; });
                        addSlider(subBox, en ? "Light Color Tint" : "Nhuộm màu ánh sáng", KEY_TINT, Math.round(shadowTint * 100f), 0, 100, 5,
                            v -> { shadowTint = v / 100f; shadDirty = true; return v + "%"; });
                        addSlider(subBox, en ? "Contact Shadow" : "Bóng tiếp xúc chân khối", KEY_CONTACT, Math.round(contactShadow * 100f), 0, 100, 5,
                            v -> { contactShadow = v / 100f; shadDirty = true; return v + "%"; });
                    };

                    CheckBox master = new CheckBox(en ? "Enable Dynamic Shadows & Lighting" : "Bật Đổ bóng & Ánh sáng động");
                    master.setChecked(enabled);
                    master.changed(() -> {
                        enabled = master.isChecked();
                        Core.settings.put(KEY_ENABLED, enabled);
                        updateUnitShadows();
                        if (!enabled) {
                            disposeFBOs();
                            if (Vars.state != null && Vars.state.rules != null && Vars.state.rules.ambientLight != null) {
                                Vars.state.rules.ambientLight.set(0f, 0f, 0f, 0f);
                            }
                        } else {
                            shadDirty = true;
                        }
                        rebuildSub.run();
                    });
                    wrap.add(master).left().padBottom(4f).row();
                    rebuildSub.run();
                    wrap.add(subBox).left().padLeft(14f).growX().row();

                    table.add(wrap).growX().left().padBottom(8f).row();
                }
            });
        } catch (Throwable err) {
            Log.err("[NexDynamicShadows] registerGraphicsSettings failed", err);
        }
    }

    private interface IntFmt { String apply(int val); }

    private static void addCheck(Table t, String title, String key, boolean def, Cons<Boolean> onChange) {
        CheckBox cb = new CheckBox(title);
        cb.setChecked(def);
        cb.changed(() -> {
            boolean v = cb.isChecked();
            Core.settings.put(key, v);
            onChange.get(v);
        });
        t.add(cb).left().padBottom(3f).row();
    }

    private static void addSlider(Table t, String title, String key, int cur, int min, int max, int step, IntFmt fmt) {
        Table row = new Table();
        row.left();
        Label lbl = new Label(title + ": [accent]" + fmt.apply(cur) + "[]");
        Slider sl = new Slider(min, max, step, false);
        sl.setValue(cur);
        sl.changed(() -> {
            int v = Math.round(sl.getValue());
            Core.settings.put(key, v);
            lbl.setText(title + ": [accent]" + fmt.apply(v) + "[]");
        });
        row.add(lbl).left().width(240f).padRight(8f);
        row.add(sl).width(160f).left();
        t.add(row).left().padBottom(4f).row();
    }

    public static void updateUnitShadows() {
        if (Vars.headless || Vars.content == null) return;
        TextureRegion clearReg = Core.atlas.find("clear");
        Vars.content.units().each(type -> {
            NexShadowConfig.getOriginalShadow(type);
            if (unitShadowsEnabled && enabled) {
                if (type.shadowRegion != clearReg) {
                    origShadowMap.put(type, type.shadowRegion);
                    type.shadowRegion = clearReg;
                }
                if (type.softShadowRegion != clearReg) {
                    origSoftShadowMap.put(type, type.softShadowRegion);
                    type.softShadowRegion = clearReg;
                }
                type.drawSoftShadow = false;
            } else {
                TextureRegion orig = origShadowMap.get(type, NexShadowConfig.getOriginalShadow(type));
                if (orig != null && type.shadowRegion != orig) type.shadowRegion = orig;
                TextureRegion origSoft = origSoftShadowMap.get(type);
                if (origSoft != null && type.softShadowRegion != origSoft) type.softShadowRegion = origSoft;
                type.drawSoftShadow = true;
            }
        });
    }

    public static void applyBlockLayers() {
        if (Vars.content == null) return;
        for (Block b : Vars.content.blocks()) {
            if (NexShadowConfig.isCrystal(b)) {
                if (b instanceof TallBlock) {
                    TallBlock tb = (TallBlock) b;
                    tb.layer = NexShadowConfig.Z_CRYSTAL;
                    tb.shadowAlpha = 0f;
                } else if (b instanceof Prop) {
                    ((Prop) b).layer = NexShadowConfig.Z_CRYSTAL;
                }
            } else if (NexShadowConfig.isTree(b) && b instanceof Prop) {
                ((Prop) b).layer = NexShadowConfig.Z_TREE;
            }
        }
    }

    private static float qualityScale() {
        if (Vars.mobile) {
            if (graphicsQuality == 0) return 0.25f;
            if (graphicsQuality == 1) return 0.35f;
            return 0.50f;
        }
        if (graphicsQuality == 0) return 0.30f;
        if (graphicsQuality == 1) return 0.42f;
        return 0.60f;
    }

    /** Vẽ quầng xóa bóng mềm 3 lớp quanh nguồn sáng để bóng tan tự nhiên vào ánh sáng. */
    private static void punchSoftLight(float x, float y, float radius, float strength) {
        float s = Mathf.clamp(strength, 0.1f, 1f);
        Draw.color(0f, 0f, 0f, 0.34f * s);
        Fill.circle(x, y, radius);
        Draw.color(0f, 0f, 0f, 0.56f * s);
        Fill.circle(x, y, radius * 0.68f);
        Draw.color(0f, 0f, 0f, 0.88f * s);
        Fill.circle(x, y, radius * 0.40f);
    }

    // Trạng thái khung hình hiện tại được tính 1 lần ở Trigger.preDraw rồi dùng lại ở Trigger.draw
    private static boolean frameActive = false;
    private static float frameAlpha = 0f, frameGlobalFade = 0f, frameUnitFade = 0f;
    private static int frameChX1 = 0, frameChY1 = 0, frameChX2 = -1, frameChY2 = -1;
    private static boolean frameHasVisibleTrees = false;

    /** Bước 1 (Trigger.preDraw): Dựng/cập nhật FBO bóng HOÀN TOÀN NGOÀI SortedSpriteBatch, Bloom và NexDarkGraphics! */
    public static void prepareShadows() {
        frameActive = false;
        if (!enabled || Vars.headless || Vars.state == null || !Vars.state.isGame() || Vars.world == null || Core.camera == null) {
            if (scratchA != null || shadowOut != null || mtnBlockShadowOut != null) disposeFBOs();
            return;
        }
        int gw = Core.graphics.getWidth(), gh = Core.graphics.getHeight();
        if (gw <= 0 || gh <= 0 || Core.camera.width <= 0.001f || Core.camera.height <= 0.001f) return;
        if (emptyRegion == null) emptyRegion = Core.atlas.find("clear");

        // Dùng Time.globalTime (thời gian khung hình thực mượt 60/120Hz) thay vì Time.time để bóng xoay mượt tuyệt đối, không bị khựng theo tick
        final float rotTicks = 1620f * 60f;
        final float cycleProgress = rotateShadows ? (Time.globalTime / rotTicks) % 1f : 0f;
        final float angle = 210f + cycleProgress * 360f;
        final float cosA = Mathf.cosDeg(angle);
        final float sinA = Mathf.sinDeg(angle);
        final float rawSun = Mathf.sin(cycleProgress * Mathf.PI2);

        float darkness = 0f;
        if (dayNightCycle) {
            if (rawSun < 0.64f) {
                float np = (0.64f - rawSun) / 1.64f;
                darkness = Mathf.clamp(np * 0.72f, 0f, 0.52f);
            }
            if (Vars.state.rules != null) {
                Vars.state.rules.lighting = true;
                if (Vars.state.rules.ambientLight == null) Vars.state.rules.ambientLight = new Color(0, 0, 0, 0);
                Vars.state.rules.ambientLight.set(0f, 0f, 0f, darkness);
            }
        } else if (Vars.state.rules != null && Vars.state.rules.ambientLight != null) {
            darkness = Mathf.clamp(Vars.state.rules.ambientLight.a);
        }

        float darkFade = 1f;
        if (darkness > darkFadeThreshold) {
            float depth = Mathf.clamp((darkness - darkFadeThreshold) / (1f - darkFadeThreshold), 0f, 1f);
            darkFade = 1f - depth * darkFadeStrength;
        }

        final float shadowScale = shadowLength * Vars.tilesize * (1f + darkness * 1.15f);
        final float alpha = shadowAlpha * (1f - darkness * 0.5f) * darkFade;

        final float rawTilePixels = (Vars.tilesize / Core.camera.width) * gw;
        float minTilePixels = (Vars.renderer != null && Vars.renderer.minScale() > 0.001f) ? Vars.tilesize * Vars.renderer.minScale() : 6.0f;
        final float tilePixels = minTilePixels > 0.001f ? (rawTilePixels / minTilePixels) * 6.0f : rawTilePixels;
        final float globalFade = zoomFadeEnabled ? Mathf.clamp((tilePixels - 6.0f) / 6.0f, 0f, 1f) : 1f;
        final float unitFade = zoomFadeEnabled ? Mathf.clamp((tilePixels - 5f) / 7f, 0f, 1f) : 1f;
        if (globalFade <= 0.005f && (!unitShadowsEnabled || unitFade <= 0.005f)) {
            freeUnitShadows();
            freeDynLights();
            hasShadowContent = false;
            hasMtnBlockShadow = false;
            return;
        }

        final float camX = Core.camera.position.x, camY = Core.camera.position.y;
        final float camW = Core.camera.width, camH = Core.camera.height;
        // Dựng FBO rộng hơn khung hình (overscan): khi di chuyển trong vùng đệm KHÔNG cần vẽ lại FBO (0ms GPU!) mà bóng vẫn ghim chặt xuống đất 100% không giật!
        // viewOnly: đệm gọn 16% (di chuyển 7% mới vẽ lại) giúp giảm mạnh số ô quét mà KHÔNG bị dựng lại FBO liên tục khi pan camera.
        final float overscan = viewOnly ? 1.16f : 1.28f;
        final float overW = camW * overscan, overH = camH * overscan;
        float margin = viewOnly ? shadowScale * 0.5f : shadowScale + Vars.tilesize * 4f;
        int wMax = Vars.world.width() - 1, hMax = Vars.world.height() - 1;
        final int tx1 = Mathf.clamp((int) ((camX - overW * 0.5f - margin) / Vars.tilesize), 0, wMax);
        final int ty1 = Mathf.clamp((int) ((camY - overH * 0.5f - margin) / Vars.tilesize), 0, hMax);
        final int tx2 = Mathf.clamp((int) ((camX + overW * 0.5f + margin) / Vars.tilesize), 0, wMax);
        final int ty2 = Mathf.clamp((int) ((camY + overH * 0.5f + margin) / Vars.tilesize), 0, hMax);

        final float screenX1 = camX - overW * 0.5f - margin;
        final float screenY1 = camY - overH * 0.5f - margin;
        final float screenX2 = camX + overW * 0.5f + margin;
        final float screenY2 = camY + overH * 0.5f + margin;

        final float fboScale = qualityScale();
        int maxDim = Vars.mobile ? 512 : 896;
        final int fw = Mathf.clamp((int) (gw * fboScale), 1, maxDim);
        final int fh = Mathf.clamp((int) (gh * fboScale), 1, maxDim);

        if (scratchA == null || scratchA.getWidth() != fw || scratchA.getHeight() != fh
                || scratchA.getTexture() == null || scratchA.getTexture().getTextureObjectHandle() == 0
                || lastFboScale != fboScale) {
            int prevCreateFbo = saveFboAndViewport();
            disposeFBOs();
            lastFboScale = fboScale;
            try {
                scratchA = new FrameBuffer(fw, fh); scratchRegA = flipped(scratchA);
                shadowOut = new FrameBuffer(fw, fh); shadowRegOut = flipped(shadowOut);
                scratchA.getTexture().setFilter(Texture.TextureFilter.linear);
                shadowOut.getTexture().setFilter(Texture.TextureFilter.linear);
                if (!liteMode) {
                    mtnBlockShadowOut = new FrameBuffer(fw, fh); mtnBlockRegOut = flipped(mtnBlockShadowOut);
                    mtnBlockShadowOut.getTexture().setFilter(Texture.TextureFilter.linear);
                }
            } catch (Throwable err) {
                Log.err("[NexDynamicShadows] FBO alloc failed", err);
                disposeFBOs();
                return;
            } finally {
                restoreFboAndViewport(prevCreateFbo);
            }
        } else if (!liteMode && (mtnBlockShadowOut == null || mtnBlockShadowOut.getTexture() == null || mtnBlockShadowOut.getTexture().getTextureObjectHandle() == 0)) {
            int prevCreateFbo = saveFboAndViewport();
            if (mtnBlockShadowOut != null) {
                try { mtnBlockShadowOut.dispose(); } catch (Throwable ignored) {}
                mtnBlockShadowOut = null;
                mtnBlockRegOut = null;
            }
            try {
                mtnBlockShadowOut = new FrameBuffer(fw, fh); mtnBlockRegOut = flipped(mtnBlockShadowOut);
                mtnBlockShadowOut.getTexture().setFilter(Texture.TextureFilter.linear);
                shadDirty = true;
            } catch (Throwable err) {
                Log.err("[NexDynamicShadows] mtn FBO alloc failed", err);
                mtnBlockShadowOut = null;
                mtnBlockRegOut = null;
            } finally {
                restoreFboAndViewport(prevCreateFbo);
            }
        } else if (liteMode && mtnBlockShadowOut != null) {
            try { mtnBlockShadowOut.dispose(); } catch (Throwable ignored) {}
            mtnBlockShadowOut = null;
            mtnBlockRegOut = null;
            hasMtnBlockShadow = false;
        }

        if (!ChunkCache.initialized) ChunkCache.init();
        final int chX1 = tx1 / ChunkCache.CHUNK_SIZE, chY1 = ty1 / ChunkCache.CHUNK_SIZE;
        final int chX2 = tx2 / ChunkCache.CHUNK_SIZE, chY2 = ty2 / ChunkCache.CHUNK_SIZE;

        // Thu thập bóng unit + ánh sáng động từ unit & đạn (vẽ quầng sáng trực tiếp lên lớp bóng mà không cần blur lại FBO!)
        freeUnitShadows();
        freeDynLights();
        final boolean lowPerf = PerfCore.level() >= 1;
        if ((unitShadowsEnabled && unitFade > 0.005f) || lightEraseEnabled) {
            Groups.unit.intersect(screenX1, screenY1, screenX2 - screenX1, screenY2 - screenY1, (Cons<Unit>) u -> {
                if (u == null || u.dead) return;
                if (lightEraseEnabled) {
                    float lr = (u.type != null && u.type.lightRadius > 4f) ? u.type.lightRadius * 0.72f : (u.isPlayer() ? 42f : 0f);
                    if (lr > 4f) addDynLight(u.x, u.y, lr, 0.45f);
                    if (u.mining() && u.mineTile != null) addDynLight(u.mineTile.worldx(), u.mineTile.worldy(), 34f, 0.65f);
                }
                if (!unitShadowsEnabled || unitFade <= 0.005f || u.drownTime >= 0.99f) return;
                TextureRegion usReg = NexShadowConfig.getOriginalShadow(u.type);
                if (usReg == null || !usReg.found() || usReg == emptyRegion) return;

                float dFactor = 1f - Mathf.clamp(u.drownTime, 0f, 1f);
                float ufl = shadowScale * (1f + u.elevation * 2.8f) * 0.03f * dFactor;
                float sx = u.x + cosA * ufl, sy = u.y + sinA * ufl;
                if (Vars.world != null) {
                    Floor fl = Vars.world.floorWorld(sx, sy);
                    if (fl != null && !fl.canShadow) return;
                }
                float sizeMult = (1f + u.elevation * 0.22f) * dFactor;
                UnitShadowData d = obtainUnitShadow();
                d.region = usReg;
                d.flying = u.isFlying() || u.elevation > 0.5f;
                d.x = sx; d.y = sy;
                d.w = usReg.width * Draw.scl * sizeMult;
                d.h = usReg.height * Draw.scl * sizeMult;
                d.rotation = u.rotation;
                d.alpha = Mathf.clamp(1f - u.elevation * 0.40f, 0.30f, 1f) * dFactor;
                unitShadows.add(d);
            });
        }
        // Bỏ ánh sáng đạn động trên Mobile hoặc khi FPS bắt đầu tụt để giữ mượt trong giao tranh lớn
        if (lightEraseEnabled && !Vars.mobile && !lowPerf && !Groups.bullet.isEmpty()) {
            Groups.bullet.intersect(screenX1, screenY1, screenX2 - screenX1, screenY2 - screenY1, (Cons<Bullet>) b -> {
                if (b == null || !b.isAdded() || b.type == null) return;
                float lr = b.type.lightRadius > 2f ? b.type.lightRadius : (b.hitSize * 2.5f + 12f);
                if (lr > 6f) addDynLight(b.x, b.y, Math.min(72f, lr * 0.8f), 0.50f);
            });
        }

        // Nhờ overscan, chỉ vẽ lại FBO khi camera đi quá ngưỡng đệm, zoom lệch, hoặc góc mặt trời xoay đáng kể -> cắt giảm 90% tải GPU!
        final float redrawMove = viewOnly ? 0.07f : 0.11f, redrawZoom = viewOnly ? 0.06f : 0.08f;
        final float angleStep = (Vars.mobile || lowPerf) ? 0.35f : 0.22f;
        final boolean needsRedraw = shadDirty
            || Math.abs(angle - lastCachedAngle) > angleStep
            || Math.abs(camX - lastCachedCamX) > camW * redrawMove
            || Math.abs(camY - lastCachedCamY) > camH * redrawMove
            || Math.abs(overW - lastCachedCamW) > camW * redrawZoom
            || Math.abs(overH - lastCachedCamH) > camH * redrawZoom;

        if (needsRedraw) {
            shadDirty = false;
            lastCachedAngle = angle;
            lastCachedCamX = camX;
            lastCachedCamY = camY;
            lastCachedCamW = overW;
            lastCachedCamH = overH;

            if (globalFade <= 0.005f) {
                hasShadowContent = false;
                hasMtnBlockShadow = false;
            } else {
                boolean anyCaster = false;
                for (int cx = chX1; cx <= chX2 && !anyCaster; cx++) {
                    for (int cy = chY1; cy <= chY2; cy++) {
                        if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                        ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                        if (chunk == null || !chunk.valid || !chunk.casters.isEmpty()) {
                            anyCaster = true;
                            break;
                        }
                    }
                }
                hasShadowContent = anyCaster;
                if (!anyCaster) hasMtnBlockShadow = false;
                if (anyCaster) {
                    Draw.flush();
                    int prevFbo = saveFboAndViewport();
                    savedProj.set(Draw.proj());
                    Draw.proj(shadowProj.setOrtho(camX - overW * 0.5f, camY - overH * 0.5f, overW, overH));
                    try {
                        int rebuiltChunks = 0;
                        int maxRebuildPerFrame = Vars.mobile ? 1 : (lowPerf ? 2 : 4);
                        boolean anyMtn = false, anyNonMtn = false;
                        for (int cx = chX1; cx <= chX2; cx++) {
                            for (int cy = chY1; cy <= chY2; cy++) {
                                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                                if (!chunk.valid) {
                                    if (rebuiltChunks >= maxRebuildPerFrame) {
                                        shadDirty = true;
                                        continue;
                                    }
                                    ChunkCache.rebuildChunkSync(cx, cy);
                                    chunk = ChunkCache.chunks[cx][cy];
                                    rebuiltChunks++;
                                }
                                if (!chunk.mountainTiles.isEmpty()) anyMtn = true;
                                if (chunk.hasNonMtnCasters) anyNonMtn = true;
                            }
                        }

                        if (liteMode) {
                            // === CHẾ ĐỘ LITE (SIÊU MƯỢT): 1 FBO duy nhất gộp toàn bộ bóng ===
                            hasMtnBlockShadow = false;
                            hasShadowContent = anyMtn || anyNonMtn;
                            if (hasShadowContent) {
                                scratchA.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                Draw.blend(Blending.normal);
                                Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                for (int cx = chX1; cx <= chX2; cx++) {
                                    for (int cy = chY1; cy <= chY2; cy++) {
                                        if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                                        ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                                        if (chunk == null || !chunk.valid) continue;
                                        Seq<ChunkCache.CasterEntry> list = chunk.casters;
                                        for (int i = 0; i < list.size; i++) {
                                            ChunkCache.CasterEntry e = list.get(i);
                                            if (e.mod == 0f) continue;
                                            if (e.isMtn && !isExposedMountainCaster(e.x, e.y, cosA, sinA)) continue;
                                            float whs = e.size * 0.5f + 1f;
                                            float bx1 = Math.min(e.cx - whs, e.cx - whs + cosA * shadowScale);
                                            float by1 = Math.min(e.cy - whs, e.cy - whs + sinA * shadowScale);
                                            float bx2 = Math.max(e.cx + whs, e.cx + whs + cosA * shadowScale);
                                            float by2 = Math.max(e.cy + whs, e.cy + whs + sinA * shadowScale);
                                            if (bx2 < screenX1 || bx1 > screenX2 || by2 < screenY1 || by1 > screenY2) continue;
                                            if (e.isProp) {
                                                float propLen = (e.region.height * Draw.scl) * (shadowScale / 80f) * 1.35f;
                                                if (propLen < 0.4f) continue;
                                                NexShadowConfig.drawPropShadow(e.cx, e.cy, e.region, e.propType, propLen, cosA, sinA, angle, contactShadow * 0.55f * darkFade, 1f);
                                            } else {
                                                float fLen = shadowScale * e.elev * e.mod;
                                                if (fLen < 0.2f) continue;
                                                Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                                NexShadowConfig.drawExtruded(e.cx, e.cy, fLen, cosA, sinA, null, e.rawSize);
                                            }
                                        }
                                    }
                                }
                                Draw.flush();
                                scratchA.end();

                                shadowOut.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                applyShaderPass(rawSun, scratchRegA, camX, camY, overW, overH, fw, fh);
                                Draw.flush();

                                Draw.blend(Blending.disabled);
                                Draw.color(0f, 0f, 0f, 0f);
                                eraseAllFootprints(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                eraseWallTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                eraseFloorTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                Draw.flush();

                                if (lightEraseEnabled) {
                                    Draw.blend(LIGHT_ERASE_BLEND);
                                    punchChunkLights(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                    Draw.flush();
                                }
                                Draw.blend(Blending.normal);
                                shadowOut.end();
                            }
                        } else {
                            // === CHẾ ĐỘ ĐẦY ĐỦ: Tách riêng bóng tường địa hình (phủ tối lên các khối bên dưới) & bóng khối xây ===
                            hasMtnBlockShadow = anyMtn && mtnBlockShadowOut != null;
                            if (hasMtnBlockShadow) {
                                scratchA.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                Draw.blend(Blending.normal);
                                Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                for (int cx = chX1; cx <= chX2; cx++) {
                                    for (int cy = chY1; cy <= chY2; cy++) {
                                        if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                                        ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                                        if (chunk == null || !chunk.valid || chunk.mountainTiles.isEmpty()) continue;
                                        Seq<ChunkCache.CasterEntry> list = chunk.casters;
                                        for (int i = 0; i < list.size; i++) {
                                            ChunkCache.CasterEntry e = list.get(i);
                                            if (!e.isMtn || e.mod == 0f) continue;
                                            if (!isExposedMountainCaster(e.x, e.y, cosA, sinA)) continue;
                                            float whs = e.size * 0.5f + 1f;
                                            float bx1 = Math.min(e.cx - whs, e.cx - whs + cosA * shadowScale);
                                            float by1 = Math.min(e.cy - whs, e.cy - whs + sinA * shadowScale);
                                            float bx2 = Math.max(e.cx + whs, e.cx + whs + cosA * shadowScale);
                                            float by2 = Math.max(e.cy + whs, e.cy + whs + sinA * shadowScale);
                                            if (bx2 < screenX1 || bx1 > screenX2 || by2 < screenY1 || by1 > screenY2) continue;
                                            float fLen = shadowScale * e.elev * e.mod;
                                            if (fLen < 0.2f) continue;
                                            NexShadowConfig.drawExtruded(e.cx, e.cy, fLen, cosA, sinA, null, e.rawSize);
                                        }
                                    }
                                }
                                Draw.flush();
                                scratchA.end();

                                mtnBlockShadowOut.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                applyShaderPass(rawSun, scratchRegA, camX, camY, overW, overH, fw, fh);
                                Draw.flush();

                                Draw.blend(Blending.disabled);
                                Draw.color(0f, 0f, 0f, 0f);
                                eraseWallTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                eraseFloorTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                Draw.flush();

                                if (lightEraseEnabled) {
                                    Draw.blend(LIGHT_ERASE_BLEND);
                                    punchChunkLights(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                    Draw.flush();
                                }
                                Draw.blend(Blending.normal);
                                mtnBlockShadowOut.end();
                            }

                            hasShadowContent = anyNonMtn;
                            if (hasShadowContent) {
                                scratchA.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                Draw.blend(Blending.normal);
                                Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                for (int cx = chX1; cx <= chX2; cx++) {
                                    for (int cy = chY1; cy <= chY2; cy++) {
                                        if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                                        ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                                        if (chunk == null || !chunk.valid || !chunk.hasNonMtnCasters) continue;
                                        Seq<ChunkCache.CasterEntry> list = chunk.casters;
                                        for (int i = 0; i < list.size; i++) {
                                            ChunkCache.CasterEntry e = list.get(i);
                                            if (e.isMtn || e.mod == 0f) continue;
                                            float whs = e.size * 0.5f + 1f;
                                            float bx1 = Math.min(e.cx - whs, e.cx - whs + cosA * shadowScale);
                                            float by1 = Math.min(e.cy - whs, e.cy - whs + sinA * shadowScale);
                                            float bx2 = Math.max(e.cx + whs, e.cx + whs + cosA * shadowScale);
                                            float by2 = Math.max(e.cy + whs, e.cy + whs + sinA * shadowScale);
                                            if (bx2 < screenX1 || bx1 > screenX2 || by2 < screenY1 || by1 > screenY2) continue;
                                            if (e.isProp) {
                                                float propLen = (e.region.height * Draw.scl) * (shadowScale / 80f) * 1.35f;
                                                if (propLen < 0.4f) continue;
                                                NexShadowConfig.drawPropShadow(e.cx, e.cy, e.region, e.propType, propLen, cosA, sinA, angle, contactShadow * 0.55f * darkFade, 1f);
                                            } else {
                                                float fLen = shadowScale * e.elev * e.mod;
                                                if (fLen < 0.2f) continue;
                                                Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                                NexShadowConfig.drawExtruded(e.cx, e.cy, fLen, cosA, sinA, null, e.rawSize);
                                            }
                                        }
                                    }
                                }
                                Draw.flush();
                                scratchA.end();

                                shadowOut.begin();
                                Gl.clearColor(0f, 0f, 0f, 0f);
                                Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                                applyShaderPass(rawSun, scratchRegA, camX, camY, overW, overH, fw, fh);
                                Draw.flush();

                                Draw.blend(Blending.disabled);
                                Draw.color(0f, 0f, 0f, 0f);
                                eraseAllFootprints(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                eraseWallTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                eraseFloorTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                Draw.flush();

                                if (hasMtnBlockShadow && mtnBlockRegOut != null) {
                                    Draw.blend(LIGHT_ERASE_BLEND);
                                    Draw.color(Color.white, 1f);
                                    Draw.rect(mtnBlockRegOut, camX, camY, overW, overH);
                                    Draw.flush();
                                }
                                if (lightEraseEnabled) {
                                    Draw.blend(LIGHT_ERASE_BLEND);
                                    punchChunkLights(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                                    Draw.flush();
                                }
                                Draw.blend(Blending.normal);
                                shadowOut.end();
                            }
                        }
                    } catch (Throwable err) {
                        Log.err("[NexDynamicShadows] shadow bake failed", err);
                        hasShadowContent = false;
                        hasMtnBlockShadow = false;
                    } finally {
                        Draw.flush();
                        Draw.shader();
                        Draw.blend(Blending.normal);
                        Blending.normal.apply();
                        Draw.reset();
                        Draw.proj(savedProj);
                        restoreFboAndViewport(prevFbo);
                    }
                }
            }
        }

        boolean anyVisibleTrees = false;
        for (int cx = chX1; cx <= chX2 && !anyVisibleTrees; cx++) {
            for (int cy = chY1; cy <= chY2; cy++) {
                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                if (chunk != null && chunk.valid && !chunk.treeTiles.isEmpty()) {
                    anyVisibleTrees = true;
                    break;
                }
            }
        }

        frameAlpha = alpha;
        frameGlobalFade = globalFade;
        frameUnitFade = unitFade;
        frameChX1 = chX1;
        frameChY1 = chY1;
        frameChX2 = chX2;
        frameChY2 = chY2;
        frameHasVisibleTrees = anyVisibleTrees;
        frameActive = true;
    }

    /** Bước 2 (Trigger.draw): CHỈ vẽ quad ảnh bóng đã dựng sẵn — tuyệt đối 0 thao tác FBO, 0 đổi viewport/shader! */
    public static void queue() {
        if (!frameActive || !enabled || Vars.headless || Vars.state == null || !Vars.state.isGame()) return;

        final float alpha = frameAlpha;
        final float globalFade = frameGlobalFade;
        final float unitFade = frameUnitFade;
        final int chX1 = frameChX1, chY1 = frameChY1, chX2 = frameChX2, chY2 = frameChY2;

        // Vẽ kết quả bóng đã dựng lên mặt đất ở Layer.block - 0.5f (29.5f)
        Draw.draw(Layer.block - 0.5f, () -> {
            if (enabled && hasShadowContent && globalFade > 0.005f && shadowRegOut != null && shadowRegOut.texture != null) {
                Draw.blend(Blending.normal);
                Draw.color(Color.white, alpha * globalFade);
                Draw.rect(shadowRegOut, lastCachedCamX, lastCachedCamY, lastCachedCamW, lastCachedCamH);
                Draw.color();
            }
            // Vẽ ánh sáng ấm dịu từ Unit, Khối & Nguồn sáng chiếu sáng trong bóng đổ (nếu bật)
            if (lightEraseEnabled && !dynLights.isEmpty()) {
                Draw.blend(Blending.additive);
                for (int i = 0; i < dynLights.size; i++) {
                    LightSpot ls = dynLights.get(i);
                    Draw.color(1f, 0.95f, 0.82f, 0.11f * ls.strength);
                    Fill.circle(ls.x, ls.y, ls.r);
                    Draw.color(1f, 0.97f, 0.88f, 0.18f * ls.strength);
                    Fill.circle(ls.x, ls.y, ls.r * 0.52f);
                }
                Draw.blend(Blending.normal);
                Draw.color();
            }
            if (!unitShadows.isEmpty() && unitFade > 0.005f) {
                boolean started = false;
                for (int i = 0; i < unitShadows.size; i++) {
                    UnitShadowData ud = unitShadows.get(i);
                    if (ud.flying) continue;
                    if (!started) { started = true; Draw.mixcol(Tmp.c1.set(0.04f, 0.03f, 0.08f, 1f), 1f); }
                    Draw.color(1f, 1f, 1f, ud.alpha * alpha * unitFade);
                    Draw.rect(ud.region, ud.x, ud.y, ud.w, ud.h, ud.rotation - 90f);
                }
                if (started) { Draw.mixcol(); Draw.color(); }
            }
            Draw.reset();
        });

        // Bóng unit bay
        if (!unitShadows.isEmpty() && unitFade > 0.005f) {
            Draw.draw(Math.min(80f, Layer.flyingUnit - 1f), () -> {
                boolean started = false;
                for (int i = 0; i < unitShadows.size; i++) {
                    UnitShadowData ud = unitShadows.get(i);
                    if (!ud.flying) continue;
                    if (!started) { started = true; Draw.mixcol(Tmp.c1.set(0.04f, 0.03f, 0.08f, 1f), 1f); }
                    Draw.color(1f, 1f, 1f, ud.alpha * alpha * unitFade);
                    Draw.rect(ud.region, ud.x, ud.y, ud.w, ud.h, ud.rotation - 90f);
                }
                if (started) { Draw.mixcol(); Draw.color(); }
                Draw.reset();
            });
        }

        // Phủ bóng tường địa hình lên đúng các khối xây nằm dưới bóng tường (chỉ chạy khi tắt chế độ Lite)
        if (!liteMode) {
            Draw.draw(Layer.blockOver + 0.5f, () -> {
                if (enabled && hasMtnBlockShadow && globalFade > 0.005f && mtnBlockRegOut != null && mtnBlockRegOut.texture != null) {
                    Draw.blend(Blending.normal);
                    Draw.color(Color.white, alpha * globalFade);
                    Draw.rect(mtnBlockRegOut, lastCachedCamX, lastCachedCamY, lastCachedCamW, lastCachedCamH);
                    Draw.reset();
                }
            });
        }

        // Cây tĩnh vẽ trên bóng (chỉ đăng ký draw pass nếu trong khung hình thực sự có cây tĩnh)
        if (frameHasVisibleTrees) {
            Draw.draw(NexShadowConfig.Z_TREE, () -> {
                for (int cx = chX1; cx <= chX2; cx++) {
                    for (int cy = chY1; cy <= chY2; cy++) {
                        if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                        ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                        if (chunk == null || !chunk.valid || chunk.treeTiles.isEmpty()) continue;
                        IntSeq list = chunk.treeTiles;
                        for (int i = 0; i < list.size; i++) {
                            int pos = list.get(i);
                            Tile t = Vars.world.tile(Point2.x(pos), Point2.y(pos));
                            if (t != null && t.block() instanceof StaticTree) t.block().drawBase(t);
                        }
                    }
                }
            });
        }
    }

    private static void punchChunkLights(int chX1, int chY1, int chX2, int chY2, float sX1, float sY1, float sX2, float sY2) {
        for (int cx = chX1; cx <= chX2; cx++) {
            for (int cy = chY1; cy <= chY2; cy++) {
                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                if (chunk == null || !chunk.valid || chunk.lights.isEmpty()) continue;
                Seq<LightSpot> lsList = chunk.lights;
                for (int i = 0; i < lsList.size; i++) {
                    LightSpot ls = lsList.get(i);
                    if (ls.x + ls.r < sX1 || ls.x - ls.r > sX2 || ls.y + ls.r < sY1 || ls.y - ls.r > sY2) continue;
                    punchSoftLight(ls.x, ls.y, ls.r, ls.strength);
                }
            }
        }
    }

    private static void eraseAllFootprints(int chX1, int chY1, int chX2, int chY2, float sX1, float sY1, float sX2, float sY2) {
        for (int cx = chX1; cx <= chX2; cx++) {
            for (int cy = chY1; cy <= chY2; cy++) {
                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                if (chunk == null || !chunk.valid) continue;
                Seq<ChunkCache.CasterEntry> list = chunk.casters;
                for (int i = 0; i < list.size; i++) {
                    ChunkCache.CasterEntry e = list.get(i);
                    if (e.isProp) continue;
                    float whs = e.size * 0.5f + 1f;
                    if (e.cx + whs < sX1 || e.cx - whs > sX2 || e.cy + whs < sY1 || e.cy - whs > sY2) continue;
                    float sz = Math.max(0.1f, e.rawSize - 0.15f);
                    Fill.rect(e.cx, e.cy, sz, sz);
                }
            }
        }
    }

    private static void eraseWallTiles(int chX1, int chY1, int chX2, int chY2, float sX1, float sY1, float sX2, float sY2) {
        float ts = Vars.tilesize;
        for (int cx = chX1; cx <= chX2; cx++) {
            for (int cy = chY1; cy <= chY2; cy++) {
                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                if (chunk == null || !chunk.valid || chunk.mountainTiles.isEmpty()) continue;
                IntSeq list = chunk.mountainTiles;
                for (int i = 0; i < list.size; i++) {
                    int pos = list.get(i);
                    float tx = Point2.x(pos) * ts, ty = Point2.y(pos) * ts;
                    if (tx + ts < sX1 || tx - ts > sX2 || ty + ts < sY1 || ty - ts > sY2) continue;
                    Fill.rect(tx, ty, ts + 0.1f, ts + 0.1f);
                }
            }
        }
    }

    private static void eraseFloorTiles(int chX1, int chY1, int chX2, int chY2, float sX1, float sY1, float sX2, float sY2) {
        for (int cx = chX1; cx <= chX2; cx++) {
            for (int cy = chY1; cy <= chY2; cy++) {
                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                if (chunk == null || !chunk.valid || chunk.eraseFloorTiles.isEmpty()) continue;
                IntSeq list = chunk.eraseFloorTiles;
                for (int i = 0; i < list.size; i++) {
                    int p = list.get(i);
                    float wx = Point2.x(p) * 8f, wy = Point2.y(p) * 8f;
                    if (wx + 8f < sX1 || wx - 8f > sX2 || wy + 8f < sY1 || wy - 8f > sY2) continue;
                    Fill.rect(wx, wy, 8.1f, 8.1f);
                }
            }
        }
    }

    private static void applyShaderPass(float sunElev, TextureRegion src,
                                        float cx, float cy, float cw, float ch, int resW, int resH) {
        if (shadowShader == null) {
            try { shadowShader = new ShadowBlurShader(); }
            catch (Throwable err) { Log.err("[NexDynamicShadows] shader compile failed", err); return; }
        }
        float scl = Scl.scl();
        if (scl <= 0.001f) scl = 1f;
        float currentPpu = ((float) Core.graphics.getWidth() / Core.camera.width) / scl;
        float ppuScale = Math.min(1f, currentPpu / 4f);
        shadowShader.radius = Mathf.clamp(blurRadius * ppuScale * 0.85f, 0.5f, blurRadius);
        shadowShader.shadowTint = shadowTint;
        shadowShader.contactShadow = contactShadow;
        shadowShader.sunElevation = sunElev;
        shadowShader.resW = Math.max(1, resW);
        shadowShader.resH = Math.max(1, resH);

        Draw.blend(Blending.disabled);
        Draw.flush();
        Draw.shader(shadowShader);
        Draw.color(Color.white, 1f);
        Draw.rect(src, cx, cy, cw, ch);
        Draw.flush();
        Draw.shader();
        Draw.blend(Blending.normal);
    }

    private static TextureRegion flipped(FrameBuffer fb) {
        TextureRegion r = new TextureRegion(fb.getTexture());
        r.flip(false, true);
        return r;
    }

    private static void disposeFBOs() {
        hasShadowContent = false;
        hasMtnBlockShadow = false;
        shadDirty = true;
        if (scratchA != null) { try { scratchA.dispose(); } catch (Throwable ignored) {} scratchA = null; scratchRegA = null; }
        if (shadowOut != null) { try { shadowOut.dispose(); } catch (Throwable ignored) {} shadowOut = null; shadowRegOut = null; }
        if (mtnBlockShadowOut != null) { try { mtnBlockShadowOut.dispose(); } catch (Throwable ignored) {} mtnBlockShadowOut = null; mtnBlockRegOut = null; }
    }

    /** CHỈ kiểm tra tường địa hình tự nhiên (StaticWall / Mountain), KHÔNG tính khối xây dựng -> đặt khối sát núi không bao giờ làm mất bóng núi! */
    private static boolean isTerrainSolidAt(int x, int y) {
        if (Vars.world == null || x < 0 || y < 0 || x >= Vars.world.width() || y >= Vars.world.height()) return true;
        Tile t = Vars.world.tile(x, y);
        if (t == null) return true;
        if (t.build != null) return false;
        Block b = t.block();
        return b != null && b.solid && (NexShadowConfig.isMountainOrWall(b) || NexShadowConfig.isPine(b));
    }

    private static boolean isBuriedTerrain(int x, int y) {
        return isTerrainSolidAt(x + 1, y) && isTerrainSolidAt(x - 1, y) && isTerrainSolidAt(x, y + 1) && isTerrainSolidAt(x, y - 1);
    }

    private static boolean isExposedMountainCaster(int x, int y, float cosA, float sinA) {
        if (cosA > 0.01f && !isTerrainSolidAt(x + 1, y)) return true;
        if (cosA < -0.01f && !isTerrainSolidAt(x - 1, y)) return true;
        if (sinA > 0.01f && !isTerrainSolidAt(x, y + 1)) return true;
        return sinA < -0.01f && !isTerrainSolidAt(x, y - 1);
    }

    private static float getElev(Block b, float def) {
        if (elevCache.containsKey(b)) return elevCache.get(b, def);
        float v = def;
        try { v = b.getClass().getField("shadowElevation").getFloat(b); } catch (Throwable ignored) {}
        elevCache.put(b, v);
        return v;
    }

    private static boolean isLuminousFloor(Floor floor) {
        if (floor == null || floor.name == null) return false;
        String n = floor.name.toLowerCase();
        return n.contains("slag") || n.contains("lava") || n.contains("magma") || n.contains("hot") || n.contains("cryo");
    }

    private static boolean shouldEraseShadow(Tile t) {
        if (t == null) return true;
        if (t.build != null && t.block().solid) return false;
        Floor fl = t.floor();
        if (fl == null) return false;
        Boolean cached = floorEraseCache.get(fl);
        if (cached != null) return cached;
        boolean res;
        if (fl == Blocks.space || fl == Blocks.empty) res = true;
        else {
            String n = fl.name != null ? fl.name.toLowerCase() : "";
            res = n.contains("space") || n.contains("void") || n.contains("empty")
                || n.contains("slag") || n.contains("lava") || n.contains("magma") || n.contains("hot") || n.contains("cryo")
                || n.contains("crux") || n.contains("rune");
        }
        floorEraseCache.put(fl, res);
        return res;
    }

    /** Chunk cache 16x16 hợp nhất (lưu cả vật đổ bóng lẫn nguồn sáng tĩnh từ công trình). */
    static final class ChunkCache {
        static final int CHUNK_SIZE = 16;
        static CasterChunk[][] chunks;
        static int mapW = 0, mapH = 0;
        static boolean initialized = false;

        static final class CasterChunk {
            final Seq<CasterEntry> casters = new Seq<>(false, 12);
            final Seq<LightSpot> lights = new Seq<>(false, 4);
            final IntSeq eraseFloorTiles = new IntSeq();
            final IntSeq mountainTiles = new IntSeq();
            final IntSeq treeTiles = new IntSeq();
            boolean valid = false;
            boolean hasNonMtnCasters = false;
        }

        static final class CasterEntry {
            int x, y;
            float cx, cy, rawSize, size, elev, mod;
            boolean isProp, isMtn, isBuild;
            Block block;
            TextureRegion region;
            NexShadowConfig.PropType propType;
        }

        static void init() {
            if (Vars.world == null) return;
            mapW = (int) Math.ceil(Vars.world.width() / (double) CHUNK_SIZE);
            mapH = (int) Math.ceil(Vars.world.height() / (double) CHUNK_SIZE);
            chunks = new CasterChunk[mapW][mapH];
            for (int x = 0; x < mapW; x++) {
                for (int y = 0; y < mapH; y++) chunks[x][y] = new CasterChunk();
            }
            initialized = true;
        }

        static void invalidateTile(int x, int y) {
            if (!initialized || chunks == null) return;
            int cx = x / CHUNK_SIZE, cy = y / CHUNK_SIZE;
            if (cx >= 0 && cx < mapW && cy >= 0 && cy < mapH) {
                chunks[cx][cy].valid = false;
                shadDirty = true;
            }
        }

        static void rebuildChunkSync(int cx, int cy) {
            if (!initialized || Vars.world == null || cx < 0 || cx >= mapW || cy < 0 || cy >= mapH) return;
            CasterChunk nc = chunks[cx][cy];
            if (nc == null) {
                nc = new CasterChunk();
                chunks[cx][cy] = nc;
            } else {
                nc.casters.clear();
                nc.lights.clear();
                nc.eraseFloorTiles.clear();
                nc.mountainTiles.clear();
                nc.treeTiles.clear();
                nc.hasNonMtnCasters = false;
                nc.valid = false;
            }
            int sx = cx * CHUNK_SIZE, sy = cy * CHUNK_SIZE;
            int ex = Math.min(sx + CHUNK_SIZE - 1, Vars.world.width() - 1);
            int ey = Math.min(sy + CHUNK_SIZE - 1, Vars.world.height() - 1);

            for (int x = sx; x <= ex; x++) {
                for (int y = sy; y <= ey; y++) {
                    Tile tile = Vars.world.tile(x, y);
                    if (tile == null) continue;
                    if (shouldEraseShadow(tile)) nc.eraseFloorTiles.add(Point2.pack(x, y));

                    boolean isBuild = tile.build != null && tile.isCenter();
                    if (isBuild) {
                        Block b = tile.build.block;
                        // Ghi nhận mọi công trình phát sáng (đèn, lõi, lò phản ứng, cột điện, khối có emitLight) để làm tan bóng xung quanh
                        float lr = 0f;
                        float str = 0.85f;
                        if (b instanceof LightBlock) {
                            lr = Math.max(60f, ((LightBlock) b).radius * 0.85f);
                            str = 1.0f;
                        } else if (b.emitLight && b.lightRadius > 4f) {
                            lr = b.lightRadius * 0.78f;
                            str = 0.90f;
                        } else if (b instanceof CoreBlock) {
                            lr = b.size * Vars.tilesize * 2.2f;
                            str = 0.92f;
                        } else if (b instanceof PowerNode) {
                            lr = b.size * Vars.tilesize * 2.4f;
                            str = 0.78f;
                        }
                        if (lr > 4f) {
                            LightSpot ls = new LightSpot();
                            ls.x = tile.build.x;
                            ls.y = tile.build.y;
                            ls.r = lr;
                            ls.strength = str;
                            nc.lights.add(ls);
                        }
                    }

                    if (isLuminousFloor(tile.floor())) continue;

                    boolean isPine = !isBuild && NexShadowConfig.isPine(tile.block());
                    boolean isTree = !isBuild && NexShadowConfig.isTree(tile.block());
                    boolean isCrystal = !isBuild && NexShadowConfig.isCrystal(tile.block());
                    boolean isMtnBlock = !isBuild && tile.block().solid && (NexShadowConfig.isMountainOrWall(tile.block()) || isPine);
                    if (isMtnBlock) nc.mountainTiles.add(Point2.pack(x, y));
                    if (isTree) nc.treeTiles.add(Point2.pack(x, y));

                    boolean isProp = !isBuild && !isPine && !isMtnBlock
                        && (tile.block() instanceof Prop || tile.block() instanceof TreeBlock || tile.block() instanceof TallBlock || isCrystal || isTree)
                        && !(tile.block() instanceof StaticWall);
                    boolean isMtn = !isBuild && !isProp && tile.block().solid && isMtnBlock;
                    boolean isWall = !isBuild && !isProp && tile.block().solid && !isBuriedTerrain(x, y);
                    if (!isBuild && !isWall && !isProp) continue;
                    if (isBuild && tile.build.block instanceof LightBlock) continue;

                    Block blk = isBuild ? tile.build.block : tile.block();
                    CasterEntry e = new CasterEntry();
                    e.x = x; e.y = y;
                    e.cx = isBuild ? tile.build.x : tile.worldx();
                    e.cy = isBuild ? tile.build.y : tile.worldy();
                    e.rawSize = isBuild ? tile.build.block.size * Vars.tilesize : Vars.tilesize;
                    e.size = e.rawSize + (isMtn ? 2.0f : 0.4f);
                    e.elev = isBuild ? getElev(tile.build.block, 1f) : getElev(tile.block(), 1.6f);
                    e.mod = NexShadowConfig.getModifier(blk);
                    e.isProp = isProp;
                    e.isMtn = isMtn;
                    e.isBuild = isBuild;
                    e.block = blk;
                    if (!isMtn) nc.hasNonMtnCasters = true;
                    if (isBuild) {
                        e.region = NexShadowConfig.getBlockSilhouette(blk);
                    }

                    if (isProp) {
                        Block block = tile.block();
                        TextureRegion reg = block.region;
                        if (block.variants > 0 && block.variantRegions != null && block.variantRegions.length > 0) {
                            int idx = Mathf.randomSeed(tile.pos(), 0, block.variantRegions.length - 1);
                            if (idx >= 0 && idx < block.variantRegions.length && block.variantRegions[idx] != null && block.variantRegions[idx].found()) {
                                reg = block.variantRegions[idx];
                            }
                        }
                        if (reg == null || !reg.found()) continue;
                        e.region = reg;
                        e.propType = NexShadowConfig.getPropType(block, reg);
                    }
                    nc.casters.add(e);
                }
            }
            nc.valid = true;
            if (cx >= 0 && cx < mapW && cy >= 0 && cy < mapH) chunks[cx][cy] = nc;
        }
    }
}
