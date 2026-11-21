package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Buffers;
import arc.util.Log;
import arc.util.Tmp;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.graphics.Layer;
import mindustry.graphics.Shaders;

import java.nio.IntBuffer;

import static arc.Core.graphics;
import static arc.Core.settings;

public class NexRenderer{
    private final Seq<BlackHole> holes = new Seq<>();
    private final Vec2 hudQueue = new Vec2();
    private final Vec2 hudReverse = new Vec2();
    private final Vec2 hudFront = new Vec2();
    private final Vec2 hudLaunch = new Vec2();
    private final Vec2 hudTakeoffFrom = new Vec2();
    private final Vec2 hudTakeoffTo = new Vec2();
    public static NexRenderer renderer;

    // Lazy-allocated FrameBuffers: 0 MB VRAM during normal gameplay when no distortion/blackhole is active.
    public FrameBuffer buffer = null;
    public FrameBuffer buffer2 = null;
    public int width, height;
    private boolean capturing;
    private int savedOuterFbo = 0;
    private final Runnable beginDistortion = this::beginDistortion;
    private final Runnable endDistortion = this::endDistortion;

    private static final IntBuffer fboQueryBuf = Buffers.newIntBuffer(1);
    private static final float[][] initFloat = new float[512][];
    private static final float[][] initStrength = new float[512][];
    private static final Pool<BlackHole> holePool = Pools.get(BlackHole.class, BlackHole::new);
    private static final Blending premultiplied = new Blending(Gl.one, Gl.oneMinusSrcAlpha, Gl.one, Gl.oneMinusSrcAlpha);
    private static boolean warnedConvexMissing = false;
    private static boolean warnedRectMissing = false;

    protected NexRenderer(){
        if(!Vars.headless){
            Events.run(EventType.Trigger.draw, this::draw);
            Events.on(EventType.WorldLoadEvent.class, e -> disposeBuffers());
        }
    }

    private static int currentFboBinding(){
        try{
            fboQueryBuf.clear();
            Gl.getIntegerv(GL20.GL_FRAMEBUFFER_BINDING, fboQueryBuf);
            return fboQueryBuf.get(0);
        }catch(Throwable ignored){
            return 0;
        }
    }

    private static void restoreFboBinding(int fbo){
        try{
            Gl.bindFramebuffer(GL20.GL_FRAMEBUFFER, fbo);
            Gl.viewport(0, 0, graphics.getWidth(), graphics.getHeight());
        }catch(Throwable ignored){}
    }

    private FrameBuffer ensureBuffer(){
        int w = Math.max(1, graphics.getWidth());
        int h = Math.max(1, graphics.getHeight());
        if(buffer == null){
            buffer = new FrameBuffer(w, h);
        }else if(buffer.getWidth() != w || buffer.getHeight() != h){
            buffer.resize(w, h);
        }
        return buffer;
    }

    private FrameBuffer ensureBuffer2(){
        int w = Math.max(1, graphics.getWidth());
        int h = Math.max(1, graphics.getHeight());
        if(buffer2 == null){
            buffer2 = new FrameBuffer(w, h);
        }else if(buffer2.getWidth() != w || buffer2.getHeight() != h){
            buffer2.resize(w, h);
        }
        return buffer2;
    }

    public void disposeBuffers(){
        capturing = false;
        clearQueuedHoles();
        if(buffer != null){
            try{ buffer.dispose(); }catch(Throwable ignored){}
            buffer = null;
        }
        if(buffer2 != null){
            try{ buffer2.dispose(); }catch(Throwable ignored){}
            buffer2 = null;
        }
    }

    private static float[] getHoleArray(int count){
        float[] arr = initFloat[count];
        if(arr == null){
            arr = new float[count * 4];
            initFloat[count] = arr;
        }
        return arr;
    }

    private static float[] getStrengthArray(int count){
        float[] arr = initStrength[count];
        if(arr == null){
            arr = new float[count * 4];
            initStrength[count] = arr;
        }
        return arr;
    }

    public void draw(){
        if(Vars.state == null || !Vars.state.isGame()){
            if(buffer != null || buffer2 != null) disposeBuffers();
            return;
        }
        width = graphics.getWidth();
        height = graphics.getHeight();
        // Skip unused hexagonalShield FBO pass (previously cleared & blitted full-screen buffer2 every frame even with 0 shields,
        // and unbound NexDarkGraphics/Bloom outer FBO at Layer 137!).
        advancedDraw();
    }

    public static void init(){
        if(renderer == null) renderer = new NexRenderer();

        if (!Vars.headless && NexShaders.holeShader == null) {
            NexShaders.createHoleShader();
        }
    }

    public void drawShader(Shader shader, float layer, float range){
        if(shader != null){
            final int[] prev = {0};
            Draw.drawRange(layer, range, () -> {
                Draw.flush();
                prev[0] = currentFboBinding();
                ensureBuffer2().begin(Color.clear);
            }, () -> {
                Draw.flush();
                if(buffer2 != null){
                    buffer2.end();
                    restoreFboBinding(prev[0]);
                    buffer2.blit(shader);
                }else{
                    restoreFboBinding(prev[0]);
                }
            });
        }
    }

    /**
     * FrameBuffer pixels are already alpha-composited while being captured.
     * The capture pass stores premultiplied RGB, so source RGB must not be multiplied by alpha again.
     */
    public static void blitPremultiplied(FrameBuffer source, Shader shader) {
        Blending previousBlending = Draw.getBlend();
        Draw.flush();
        premultiplied.apply();
        try {
            source.blit(shader);
        } finally {
            previousBlending.apply();
        }
    }

    public static void addShockCircle(float x, float y, float r, float lifetime){
        addShockCircle(x, y, r, lifetime, NexShaders.convex == null ? 0f : NexShaders.convex.strength);
    }

    public static void addShockCircle(float x, float y, float r, float lifetime, float strength){
        float scaledStrength = resolveDistortionStrength(strength, NexShaders.convex != null, true);
        if(scaledStrength <= 0.0001f || NexShaders.convex == null) return;
        NexShaders.convex.add(x, y, r, lifetime, scaledStrength);
    }

    public static void addShockRect(float x, float y, float length, float width, float angle, float lifetime){
        addShockRect(x, y, length, width, angle, lifetime, NexShaders.convexRect == null ? 0f : NexShaders.convexRect.strength);
    }

    public static void addShockRect(float x, float y, float length, float width, float angle, float lifetime, float strength){
        float scaledStrength = resolveDistortionStrength(strength, NexShaders.convexRect != null, false);
        if(scaledStrength <= 0.0001f || NexShaders.convexRect == null) return;
        NexShaders.convexRect.addRect(x, y, length, width, angle, lifetime, scaledStrength);
    }

    private static float resolveDistortionStrength(float baseStrength, boolean shaderReady, boolean circle){
        if(Vars.headless || !NexSettings.distortionEnabled()) return 0f;
        float scale = NexSettings.distortionStrengthScale();
        if(scale <= 0.0001f) return 0f;

        if(!shaderReady){
            if(circle && !warnedConvexMissing){
                warnedConvexMissing = true;
                Log.warn("Convex lens shader is null; NexShaders.init() may have failed.");
            }else if(!circle && !warnedRectMissing){
                warnedRectMissing = true;
                Log.warn("Rect lens shader is null; NexShaders.init() may have failed.");
            }
            return 0f;
        }

        float scaledStrength = baseStrength * scale;
        return scaledStrength <= 0.0001f ? 0f : scaledStrength;
    }

    public static void addBlackHole(float x, float y, float inRadius, float outRadius, float alpha){
        addBlackHole(x, y, inRadius, outRadius, alpha, 1f);
    }

    public static void addBlackHole(float x, float y, float inRadius, float outRadius){
        addBlackHole(x, y, inRadius, outRadius, 1f, 1f);
    }

    public static void addBlackHole(float x, float y, float inRadius, float outRadius, float alpha, float strength){
        if(!Vars.headless && renderer != null){
            renderer.addHole(x, y, inRadius, outRadius, alpha, strength);
        }
    }

    private boolean hasPendingEffects(){
        return !holes.isEmpty()
            || (NexShaders.convex != null && NexShaders.convex.hasAny())
            || (NexShaders.convexRect != null && NexShaders.convexRect.hasAny());
    }

    private void advancedDraw(){
        if(settings.getBool("pixelate") || holes.size >= 512){
            clearQueuedHoles();
            return;
        }
        // Zero overhead when no black holes or distortion lenses are active!
        if(!hasPendingEffects()) return;

        Draw.draw(Layer.floor - 8, beginDistortion);
        Draw.draw(Layer.effect + 0.03f, endDistortion);
    }

    private void beginDistortion() {
        capturing = false;
        if (!hasPendingEffects()) return;
        Draw.flush();
        savedOuterFbo = currentFboBinding();
        FrameBuffer b = ensureBuffer();
        if (!b.isBound()) {
            b.begin(Color.clear);
            capturing = true;
        }
    }

    private void endDistortion() {
        if (!capturing) return;
        capturing = false;
        if (holes.size >= NexShaders.MaxCont) NexShaders.createHoleShader();

        int holeCount = Math.min(holes.size, NexShaders.MaxCont);

        if (buffer == null || !buffer.isBound()) {
            clearQueuedHoles();
            restoreFboBinding(savedOuterFbo);
            return;
        }
        Draw.flush();
        buffer.end();

        boolean hasCircleDistortion = NexShaders.convex != null && NexShaders.convex.hasAny();
        boolean hasRectDistortion = NexShaders.convexRect != null && NexShaders.convexRect.hasAny();

        if (holeCount > 0 && NexShaders.holeShader != null) {
            FrameBuffer b2 = ensureBuffer2();
            b2.begin(Color.clear);
            float[] blackholes = getHoleArray(holeCount);
            float[] strengths = getStrengthArray(holeCount);

            for (int i = 0; i < holeCount; i++) {
                BlackHole hole = holes.get(i);
                blackholes[i * 4] = hole.x;
                blackholes[i * 4 + 1] = hole.y;
                blackholes[i * 4 + 2] = hole.inRadius;
                blackholes[i * 4 + 3] = hole.outRadius;
                strengths[i * 4] = hole.strength;
            }

            NexShaders.holeShader.blackHoles = blackholes;
            NexShaders.holeShader.blackHoleStrengths = strengths;
            blitPremultiplied(buffer, NexShaders.holeShader);
            Draw.flush();
            b2.end();

            if (hasCircleDistortion && hasRectDistortion) {
                buffer.begin(Color.clear);
                NexShaders.convex.blitFrom(b2);
                Draw.flush();
                buffer.end();
                restoreFboBinding(savedOuterFbo);
                NexShaders.convexRect.blitFrom(buffer);
            } else if (hasCircleDistortion) {
                restoreFboBinding(savedOuterFbo);
                NexShaders.convex.blitFrom(b2);
            } else if (hasRectDistortion) {
                restoreFboBinding(savedOuterFbo);
                NexShaders.convexRect.blitFrom(b2);
            } else {
                restoreFboBinding(savedOuterFbo);
                blitPremultiplied(b2, Shaders.screenspace);
            }
        } else {
            // Fast path when only lens distortion (no black holes): skip the redundant buffer -> buffer2 copy!
            if (hasCircleDistortion && hasRectDistortion) {
                FrameBuffer b2 = ensureBuffer2();
                b2.begin(Color.clear);
                NexShaders.convex.blitFrom(buffer);
                Draw.flush();
                b2.end();
                restoreFboBinding(savedOuterFbo);
                NexShaders.convexRect.blitFrom(b2);
            } else if (hasCircleDistortion) {
                restoreFboBinding(savedOuterFbo);
                NexShaders.convex.blitFrom(buffer);
            } else if (hasRectDistortion) {
                restoreFboBinding(savedOuterFbo);
                NexShaders.convexRect.blitFrom(buffer);
            } else {
                restoreFboBinding(savedOuterFbo);
                blitPremultiplied(buffer, Shaders.screenspace);
            }
        }

        //Draw black cores after post-processing to avoid donut-like rings from re-sampling.
        for (int i = 0; i < holeCount; i++) {
            BlackHole hole = holes.get(i);
            Draw.color(Tmp.c2.set(Color.black).a(hole.alpha));
            Fill.circle(hole.x, hole.y, hole.inRadius * newex.NexBlackHole.coreScale);
            holePool.free(hole);
        }
        Draw.color();
        holes.clear();
    }

    private void clearQueuedHoles() {
        for (int i = 0; i < holes.size; i++) {
            holePool.free(holes.get(i));
        }
        holes.clear();
    }

    private void addHole(float x, float y, float inRadius, float outRadius, float alpha, float strength){
        if(inRadius > outRadius || outRadius <= 0) return;

        float safeStrength = Math.max(strength, 0.0001f);
        float scaledStrength = safeStrength * NexSettings.distortionStrengthScale();
        if(!NexSettings.distortionEnabled()) scaledStrength = 0f;

        holes.add(holePool.obtain().set(x, y, inRadius, outRadius, alpha, scaledStrength));
    }

    private static class BlackHole{
        float x, y, inRadius, outRadius, alpha, strength;

        public BlackHole set(float x, float y, float inRadius, float outRadius, float alpha, float strength){
            this.x = x;
            this.y = y;
            this.inRadius = inRadius;
            this.outRadius = outRadius;
            this.alpha = alpha;
            this.strength = strength;
            return this;
        }

        public BlackHole(){
        }
    }
}
