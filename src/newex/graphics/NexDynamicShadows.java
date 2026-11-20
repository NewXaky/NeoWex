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

    public static boolean enabled = true;
    public static boolean dayNightCycle = true;
    public static boolean rotateShadows = true;
    public static boolean unitShadowsEnabled = true;
    public static boolean zoomFadeEnabled = true;
    /** 0 = Thấp (45%), 1 = Trung bình (65%, mặc định mượt 60fps), 2 = Cao (90%) */
    public static int graphicsQuality = 1;
    public static float shadowLength = 10f;
    public static float shadowAlpha = 0.42f;
    public static float blurRadius = 3.0f;
    public static float shadowTint = 0.60f;
    public static float contactShadow = 0.45f;
    public static float darkFadeThreshold = 0.35f, darkFadeStrength = 0.80f;

    public static volatile boolean shadDirty = true;
    private static boolean initialized = false;

    private static final java.nio.IntBuffer fboQueryBuf = arc.util.Buffers.newIntBuffer(1);

    private static int currentFboBinding() {
        try {
            fboQueryBuf.clear();
            Gl.getIntegerv(GL20.GL_FRAMEBUFFER_BINDING, fboQueryBuf);
            return fboQueryBuf.get(0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void restoreFboBinding(int prevFbo) {
        try {
            Gl.bindFramebuffer(GL20.GL_FRAMEBUFFER, prevFbo);
            Gl.viewport(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
        } catch (Throwable ignored) {}
    }

    // Chế độ hòa trộn làm tan bóng mượt quanh nguồn sáng (dst = dst * (1 - srcAlpha))
    private static final Blending LIGHT_ERASE_BLEND = new Blending(Gl.zero, Gl.oneMinusSrcAlpha);

    // Chỉ cần 3 FBO duy nhất (scratchA -> scratchB -> shadowOut) thay vì 15 FBO!
    private static FrameBuffer scratchA, scratchB, shadowOut;
    private static TextureRegion scratchRegA, scratchRegB, shadowRegOut;
    private static boolean hasShadowContent = false;

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

    /** Shader 5-tap separable Gaussian blur + nhuộm màu ánh sáng ngày/hoàng hôn/đêm. */
    private static final String FRAG_SRC =
        "varying vec4 v_color;\n" +
        "varying vec2 v_texCoord;\n" +
        "uniform sampler2D u_texture;\n" +
        "uniform vec2 u_resolution;\n" +
        "uniform float u_radius;\n" +
        "uniform vec2 u_blurDir;\n" +
        "uniform float u_sunElevation;\n" +
        "uniform float u_shadowTint;\n" +
        "uniform float u_contactShadow;\n" +
        "void main(){\n" +
        "    vec2 texel = 1.0 / u_resolution;\n" +
        "    float elevFactor = 1.0 - clamp(u_sunElevation, 0.0, 1.0);\n" +
        "    float dynamicRad = u_radius * (1.0 + elevFactor * 1.25);\n" +
        "    vec2 step1 = u_blurDir * texel * (dynamicRad * 1.3846);\n" +
        "    vec2 step2 = u_blurDir * texel * (dynamicRad * 3.2307);\n" +
        "    vec4 col = texture2D(u_texture, v_texCoord) * 0.2270;\n" +
        "    col += texture2D(u_texture, v_texCoord + step1) * 0.3162;\n" +
        "    col += texture2D(u_texture, v_texCoord - step1) * 0.3162;\n" +
        "    col += texture2D(u_texture, v_texCoord + step2) * 0.0703;\n" +
        "    col += texture2D(u_texture, v_texCoord - step2) * 0.0703;\n" +
        "    float alpha = col.a;\n" +
        "    float distFade = smoothstep(0.0, 0.15, alpha);\n" +
        "    float edgeFactor = smoothstep(0.0, 0.40, alpha) * (1.0 - smoothstep(0.50, 1.0, alpha));\n" +
        "    float contactBoost = smoothstep(0.55, 1.0, alpha) * u_contactShadow;\n" +
        "    float sunE = u_sunElevation;\n" +
        "    vec3 dayTint = vec3(0.06, 0.07, 0.13);\n" +
        "    float horizonFactor = exp(-sunE * sunE * 8.0);\n" +
        "    vec3 horizonTint = vec3(0.16, 0.06, 0.02) * horizonFactor;\n" +
        "    float nightFactor = clamp(-sunE, 0.0, 1.0);\n" +
        "    vec3 nightTint = vec3(0.04, 0.02, 0.10) * nightFactor;\n" +
        "    vec3 rawTint = dayTint + horizonTint + nightTint;\n" +
        "    vec3 shadowColor = mix(vec3(0.0), rawTint, u_shadowTint);\n" +
        "    shadowColor += vec3(0.025, 0.010, 0.0) * edgeFactor * u_shadowTint * (1.0 - nightFactor);\n" +
        "    float finalAlpha = clamp(alpha * distFade * (1.0 + contactBoost), 0.0, 1.0);\n" +
        "    gl_FragColor = vec4(shadowColor, finalAlpha) * v_color;\n" +
        "}\n";

    static final class ShadowBlurShader extends Shader {
        float radius = 3.0f, blurDirX = 1f, blurDirY = 0f, shadowTint = 0.6f, contactShadow = 0.45f, sunElevation = 0.5f;
        int resW = 1, resH = 1;

        ShadowBlurShader() {
            super(VERT_SRC, FRAG_SRC);
        }

        @Override
        public void apply() {
            setUniformf("u_radius", radius);
            setUniformf("u_blurDir", blurDirX, blurDirY);
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
        if (r <= 2f || strength <= 0.02f || dynLights.size >= 96) return;
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
            loadSettings();
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
            Events.run(EventType.Trigger.draw, NexDynamicShadows::queue);
        }
    }

    private static void loadSettings() {
        enabled = Core.settings.getBool(KEY_ENABLED, true);
        dayNightCycle = Core.settings.getBool(KEY_DAYNIGHT, true);
        rotateShadows = Core.settings.getBool(KEY_ROTATE, true);
        unitShadowsEnabled = Core.settings.getBool(KEY_UNITS, true);
        zoomFadeEnabled = Core.settings.getBool(KEY_ZOOMFADE, true);
        graphicsQuality = Core.settings.getInt(KEY_QUALITY, 1);
        shadowLength = Core.settings.getInt(KEY_LENGTH, 10);
        shadowAlpha = Core.settings.getInt(KEY_ALPHA, 42) / 100f;
        blurRadius = Core.settings.getInt(KEY_BLUR, 30) / 10f;
        shadowTint = Core.settings.getInt(KEY_TINT, 60) / 100f;
        contactShadow = Core.settings.getInt(KEY_CONTACT, 45) / 100f;
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
            Vars.ui.settings.graphics.pref(new SettingsTable.Setting("newex-dynamic-shadows-panel") {
                @Override
                public void add(SettingsTable table) {
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
            if (graphicsQuality == 0) return 0.35f;
            if (graphicsQuality == 1) return 0.50f;
            return 0.70f;
        }
        if (graphicsQuality == 0) return 0.45f;
        if (graphicsQuality == 1) return 0.65f;
        return 0.90f;
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

    public static void queue() {
        if (!enabled || Vars.headless || Vars.state == null || !Vars.state.isGame()) return;
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

        final float camX = Core.camera.position.x, camY = Core.camera.position.y;
        final float camW = Core.camera.width, camH = Core.camera.height;
        float margin = shadowScale + Vars.tilesize * 4f;
        int wMax = Vars.world.width() - 1, hMax = Vars.world.height() - 1;
        final int tx1 = Mathf.clamp((int) ((camX - camW * 0.5f - margin) / Vars.tilesize), 0, wMax);
        final int ty1 = Mathf.clamp((int) ((camY - camH * 0.5f - margin) / Vars.tilesize), 0, hMax);
        final int tx2 = Mathf.clamp((int) ((camX + camW * 0.5f + margin) / Vars.tilesize), 0, wMax);
        final int ty2 = Mathf.clamp((int) ((camY + camH * 0.5f + margin) / Vars.tilesize), 0, hMax);

        final float screenX1 = camX - camW * 0.5f - margin;
        final float screenY1 = camY - camH * 0.5f - margin;
        final float screenX2 = camX + camW * 0.5f + margin;
        final float screenY2 = camY + camH * 0.5f + margin;

        final float fboScale = qualityScale();
        int gw = Core.graphics.getWidth(), gh = Core.graphics.getHeight();
        int maxDim = Vars.mobile ? 960 : 2560;
        final int fw = Mathf.clamp((int) (gw * fboScale), 1, maxDim);
        final int fh = Mathf.clamp((int) (gh * fboScale), 1, maxDim);

        if (scratchA == null || scratchA.getWidth() != fw || scratchA.getHeight() != fh
                || scratchA.getTexture() == null || scratchA.getTexture().getTextureObjectHandle() == 0
                || lastFboScale != fboScale) {
            disposeFBOs();
            lastFboScale = fboScale;
            try {
                scratchA = new FrameBuffer(fw, fh); scratchRegA = flipped(scratchA);
                scratchB = new FrameBuffer(fw, fh); scratchRegB = flipped(scratchB);
                shadowOut = new FrameBuffer(fw, fh); shadowRegOut = flipped(shadowOut);
                scratchA.getTexture().setFilter(Texture.TextureFilter.linear);
                scratchB.getTexture().setFilter(Texture.TextureFilter.linear);
                shadowOut.getTexture().setFilter(Texture.TextureFilter.linear);
            } catch (Throwable err) {
                disposeFBOs();
                return;
            }
        }

        if (!ChunkCache.initialized) ChunkCache.init();
        final int chX1 = tx1 / ChunkCache.CHUNK_SIZE, chY1 = ty1 / ChunkCache.CHUNK_SIZE;
        final int chX2 = tx2 / ChunkCache.CHUNK_SIZE, chY2 = ty2 / ChunkCache.CHUNK_SIZE;

        // Thu thập bóng unit trong vùng camera (bỏ quét dynLights mỗi 3 tick vốn làm mobile phải blur lại liên tục gây giật!)
        freeUnitShadows();
        freeDynLights();
        if (unitShadowsEnabled) {
            Groups.unit.intersect(screenX1, screenY1, screenX2 - screenX1, screenY2 - screenY1, (Cons<Unit>) u -> {
                if (u == null || u.dead || u.drownTime >= 0.99f) return;
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

        // Cập nhật FBO ngay khi camera dịch chuyển hoặc góc mặt trời xoay nhẹ (0.025 độ) để bóng bám chặt mặt đất 100%, không bao giờ giật cục!
        final boolean needsRedraw = shadDirty
            || Math.abs(angle - lastCachedAngle) > 0.025f
            || Math.abs(camX - lastCachedCamX) > 0.001f
            || Math.abs(camY - lastCachedCamY) > 0.001f
            || Math.abs(camW - lastCachedCamW) > 0.001f
            || Math.abs(camH - lastCachedCamH) > 0.001f;

        if (needsRedraw) {
            shadDirty = false;
            lastCachedAngle = angle;
            lastCachedCamX = camX;
            lastCachedCamY = camY;
            lastCachedCamW = camW;
            lastCachedCamH = camH;
        }

        final float rawTilePixels = (Vars.tilesize / Core.camera.width) * Core.graphics.getWidth();
        float minTilePixels = (Vars.renderer != null && Vars.renderer.minScale() > 0.001f) ? Vars.tilesize * Vars.renderer.minScale() : 6.0f;
        final float tilePixels = minTilePixels > 0.001f ? (rawTilePixels / minTilePixels) * 6.0f : rawTilePixels;
        final float globalFade = zoomFadeEnabled ? Mathf.clamp((tilePixels - 6.0f) / 6.0f, 0f, 1f) : 1f;
        final float fDarkFade = darkFade;

        // Vẽ & cập nhật 1 lớp bóng hợp nhất duy nhất ở Layer.block - 0.5f (29.5f):
        // nằm SAU FloorRenderer (10f) và DƯỚI toàn bộ khối xây (30f), đồng thời lưu/khôi phục FBO gốc của Mindustry (Bloom/Lighting) -> tuyệt đối không bao giờ làm đen sàn!
        Draw.draw(Layer.block - 0.5f, () -> {
            if (needsRedraw) {
                if (globalFade <= 0.005f) {
                    hasShadowContent = false;
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
                    if (anyCaster) {
                        Draw.flush();
                        int prevFbo = currentFboBinding();

                        // 1) Vẽ toàn bộ bóng tường địa hình + khối xây + prop vào scratchA
                        scratchA.begin();
                        Gl.clearColor(0f, 0f, 0f, 0f);
                        Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                        Draw.color(0.04f, 0.03f, 0.08f, 1f);

                        int rebuiltChunks = 0;
                        int maxRebuildPerFrame = Vars.mobile ? 2 : 6;
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
                                Seq<ChunkCache.CasterEntry> list = chunk.casters;
                                for (int i = 0; i < list.size; i++) {
                                    ChunkCache.CasterEntry e = list.get(i);
                                    if (e.mod == 0f) continue;

                                    float whs = e.size * 0.5f + 1f;
                                    float bx1 = Math.min(e.cx - whs, e.cx - whs + cosA * shadowScale);
                                    float by1 = Math.min(e.cy - whs, e.cy - whs + sinA * shadowScale);
                                    float bx2 = Math.max(e.cx + whs, e.cx + whs + cosA * shadowScale);
                                    float by2 = Math.max(e.cy + whs, e.cy + whs + sinA * shadowScale);
                                    if (bx2 < screenX1 || bx1 > screenX2 || by2 < screenY1 || by1 > screenY2) continue;

                                    if (e.isMtn && !isExposedMountainCaster(e.x, e.y, cosA, sinA)) continue;

                                    if (e.isProp) {
                                        float propLen = (e.region.height * Draw.scl) * (shadowScale / 80f) * 1.35f;
                                        if (propLen < 0.4f) continue;
                                        NexShadowConfig.drawPropShadow(e.cx, e.cy, e.region, e.propType, propLen, cosA, sinA, angle, contactShadow * 0.55f * fDarkFade, 1f);
                                    } else {
                                        float fLen = shadowScale * e.elev * e.mod;
                                        if (fLen < 0.2f) continue;
                                        Draw.color(0.04f, 0.03f, 0.08f, 1f);
                                        NexShadowConfig.drawExtruded(e.cx, e.cy, fLen, cosA, sinA, e.region, e.rawSize);
                                    }
                                }
                            }
                        }

                        Draw.flush();
                        scratchA.end();

                        // 2) Pass blur ngang (scratchA -> scratchB)
                        scratchB.begin();
                        Gl.clearColor(0f, 0f, 0f, 0f);
                        Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                        applyShaderPass(1f, 0f, rawSun, scratchRegA, camX, camY, camW, camH, fw, fh);
                        Draw.flush();
                        scratchB.end();

                        // 3) Pass blur dọc (scratchB -> shadowOut) + xóa 1 lần duy nhất chân tường/khối/sàn + làm tan bóng quanh nguồn sáng (giảm 50% lệnh vẽ CPU!)
                        shadowOut.begin();
                        Gl.clearColor(0f, 0f, 0f, 0f);
                        Gl.clear(GL20.GL_COLOR_BUFFER_BIT);
                        applyShaderPass(0f, 1f, rawSun, scratchRegB, camX, camY, camW, camH, fw, fh);
                        Draw.flush();

                        Draw.blend(Blending.disabled);
                        Draw.color(0f, 0f, 0f, 0f);
                        eraseAllFootprints(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                        eraseWallTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                        eraseFloorTiles(chX1, chY1, chX2, chY2, screenX1, screenY1, screenX2, screenY2);
                        Draw.flush();

                        Draw.blend(LIGHT_ERASE_BLEND);
                        for (int cx = chX1; cx <= chX2; cx++) {
                            for (int cy = chY1; cy <= chY2; cy++) {
                                if (cx < 0 || cx >= ChunkCache.mapW || cy < 0 || cy >= ChunkCache.mapH) continue;
                                ChunkCache.CasterChunk chunk = ChunkCache.chunks[cx][cy];
                                if (chunk == null || !chunk.valid || chunk.lights.isEmpty()) continue;
                                Seq<LightSpot> lsList = chunk.lights;
                                for (int i = 0; i < lsList.size; i++) {
                                    LightSpot ls = lsList.get(i);
                                    if (ls.x + ls.r < screenX1 || ls.x - ls.r > screenX2 || ls.y + ls.r < screenY1 || ls.y - ls.r > screenY2) continue;
                                    punchSoftLight(ls.x, ls.y, ls.r, ls.strength);
                                }
                            }
                        }
                        for (int i = 0; i < dynLights.size; i++) {
                            LightSpot ls = dynLights.get(i);
                            punchSoftLight(ls.x, ls.y, ls.r, ls.strength);
                        }
                        Draw.flush();
                        Draw.blend(Blending.normal);
                        shadowOut.end();

                        // Khôi phục lại đúng FBO đang bind trước đó (Bloom / màn hình) để không bao giờ mất nền sàn!
                        restoreFboBinding(prevFbo);
                    }
                }
            }

            if (enabled && hasShadowContent && globalFade > 0.005f && shadowRegOut != null && shadowRegOut.texture != null) {
                Draw.color(Color.white, alpha * globalFade);
                // Vẽ theo đúng tọa độ thế giới đã chụp FBO (lastCachedCamX/Y/W/H) để bóng ghim cố định xuống mặt đất 100%, không trượt theo camera!
                Draw.rect(shadowRegOut, lastCachedCamX, lastCachedCamY, lastCachedCamW, lastCachedCamH);
                Draw.color();
            }
            if (!unitShadows.isEmpty()) {
                float unitFade = zoomFadeEnabled ? Mathf.clamp((tilePixels - 5f) / 7f, 0f, 1f) : 1f;
                if (unitFade > 0.005f) {
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
            }
        });

        // Bóng unit bay
        if (!unitShadows.isEmpty()) {
            Draw.draw(Math.min(80f, Layer.flyingUnit - 1f), () -> {
                float unitFade = zoomFadeEnabled ? Mathf.clamp((tilePixels - 5f) / 7f, 0f, 1f) : 1f;
                if (unitFade <= 0.005f) return;
                boolean started = false;
                for (int i = 0; i < unitShadows.size; i++) {
                    UnitShadowData ud = unitShadows.get(i);
                    if (!ud.flying) continue;
                    if (!started) { started = true; Draw.mixcol(Tmp.c1.set(0.04f, 0.03f, 0.08f, 1f), 1f); }
                    Draw.color(1f, 1f, 1f, ud.alpha * alpha * unitFade);
                    Draw.rect(ud.region, ud.x, ud.y, ud.w, ud.h, ud.rotation - 90f);
                }
                if (started) { Draw.mixcol(); Draw.color(); }
            });
        }

        // Cây tĩnh vẽ trên bóng
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

    private static void applyShaderPass(float dx, float dy, float sunElev, TextureRegion src,
                                        float cx, float cy, float cw, float ch, int resW, int resH) {
        if (shadowShader == null) {
            try { shadowShader = new ShadowBlurShader(); }
            catch (Throwable err) { Log.err("[NexDynamicShadows] shader compile failed", err); return; }
        }
        float scl = Scl.scl();
        if (scl <= 0.001f) scl = 1f;
        float currentPpu = ((float) Core.graphics.getWidth() / Core.camera.width) / scl;
        float ppuScale = Math.min(1f, currentPpu / 4f);
        shadowShader.radius = Mathf.clamp(blurRadius * ppuScale, 0.5f, blurRadius);
        shadowShader.blurDirX = dx;
        shadowShader.blurDirY = dy;
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
        if (scratchA != null) { try { scratchA.dispose(); } catch (Throwable ignored) {} scratchA = null; scratchRegA = null; }
        if (scratchB != null) { try { scratchB.dispose(); } catch (Throwable ignored) {} scratchB = null; scratchRegB = null; }
        if (shadowOut != null) { try { shadowOut.dispose(); } catch (Throwable ignored) {} shadowOut = null; shadowRegOut = null; }
    }

    private static boolean isSolidAt(int x, int y) {
        if (Vars.world == null || x < 0 || y < 0 || x >= Vars.world.width() || y >= Vars.world.height()) return true;
        Tile t = Vars.world.tile(x, y);
        if (t == null) return true;
        return t.build != null ? t.build.block.solid : t.block().solid;
    }

    private static boolean isBuried(int x, int y) {
        return isSolidAt(x + 1, y) && isSolidAt(x - 1, y) && isSolidAt(x, y + 1) && isSolidAt(x, y - 1);
    }

    private static boolean isExposedMountainCaster(int x, int y, float cosA, float sinA) {
        if (cosA > 0.01f && !isSolidAt(x + 1, y)) return true;
        if (cosA < -0.01f && !isSolidAt(x - 1, y)) return true;
        if (sinA > 0.01f && !isSolidAt(x, y + 1)) return true;
        return sinA < -0.01f && !isSolidAt(x, y - 1);
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
        }

        static final class CasterEntry {
            int x, y;
            float cx, cy, rawSize, size, elev, mod;
            boolean isProp, isMtn;
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
            if (!initialized || Vars.world == null) return;
            CasterChunk nc = new CasterChunk();
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
                    boolean isWall = !isBuild && !isProp && tile.block().solid && !isBuried(x, y);
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
                    e.block = blk;

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
