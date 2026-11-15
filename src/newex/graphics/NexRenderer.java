package newex.graphics;

import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Tmp;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.graphics.Shaders;

import newex.graphics.NexSettings;

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

    public FrameBuffer buffer = new FrameBuffer();
    public FrameBuffer buffer2 = new FrameBuffer();
    public int width, height;
    private boolean capturing;
    private final Runnable beginDistortion = this::beginDistortion;
    private final Runnable endDistortion = this::endDistortion;

    private static final float[][] initFloat = new float[512][];
    private static final float[][] initStrength = new float[512][];
    private static final Pool<BlackHole> holePool = Pools.get(BlackHole.class, BlackHole::new);
    private static final Blending premultiplied = new Blending(Gl.one, Gl.oneMinusSrcAlpha, Gl.one, Gl.oneMinusSrcAlpha);
    private static boolean warnedConvexMissing = false;
    private static boolean warnedRectMissing = false;
    private static boolean warnedHoleMissing = false;

    protected NexRenderer(){
        if(!Vars.headless){
            Events.run(EventType.Trigger.draw, this::draw);
        }
    }

    public void draw(){
        width = graphics.getWidth();
        height = graphics.getHeight();

        buffer2.resize(graphics.getWidth(), graphics.getHeight());
       /* drawShader(NexShaders.powerArea, NexLayers.POWER_AREA,1);
        drawShader(NexShaders.powerDynamicArea, NexLayers.POWER_DYNAMIC, 1);*/
        if(Vars.renderer.animateShields){
            drawShader(NexShaders.hexagonalShield, NexLayers.HEXAGONAL_SHIELD, 1);
        }
        advancedDraw();
    }

    public static void init(){
        if(renderer == null) renderer = new NexRenderer();

        for(int i = 0; i < 512; i++){
            initFloat[i] = new float[i * 4];
            initStrength[i] = new float[i * 4];
        }

        if (!Vars.headless && NexShaders.holeShader == null) {
            NexShaders.createHoleShader();
        }
    }

    public void drawShader(Shader shader, float layer, float range){
        if(shader != null){
            Draw.drawRange(layer, range, () -> buffer2.begin(Color.clear), () -> {
                buffer2.end();
                buffer2.blit(shader);
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

    private void advancedDraw(){
        if(settings.getBool("pixelate") || holes.size >= 512){
            clearQueuedHoles();
            return;
        }

        Draw.draw(Layer.floor - 8, beginDistortion);
        Draw.draw(Layer.effect + 0.03f, endDistortion);
    }

    private void beginDistortion() {
        capturing = false;
        if (holes.isEmpty() && (NexShaders.convex == null || !NexShaders.convex.hasAny()) &&
                (NexShaders.convexRect == null || !NexShaders.convexRect.hasAny())) return;
        buffer.resize(graphics.getWidth(), graphics.getHeight());
        if (!buffer.isBound()) {
            buffer.begin(Color.clear);
            capturing = true;
        }
    }

    private void endDistortion() {
        if (!capturing) return;
        capturing = false;
        if (holes.size >= NexShaders.MaxCont) NexShaders.createHoleShader();

        int holeCount = Math.min(holes.size, NexShaders.MaxCont);

        //The capture buffer must be bound from the floor pass; otherwise skip to avoid stale-frame artifacts.
        if (!buffer.isBound()) {
            clearQueuedHoles();
            return;
        }
        buffer.end();

        buffer2.resize(graphics.getWidth(), graphics.getHeight());
        buffer2.begin(Color.clear);
        if (NexShaders.holeShader != null) {
            float[] blackholes = initFloat[holeCount];
            float[] strengths = initStrength[holeCount];

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
            //Compose blackhole pass into buffer2 first, so convex can sample the already-distorted image.
            blitPremultiplied(buffer, NexShaders.holeShader);
        } else {
            blitPremultiplied(buffer, Shaders.screenspace);
        }
        buffer2.end();

        boolean hasCircleDistortion = NexShaders.convex != null && NexShaders.convex.hasAny();
        boolean hasRectDistortion = NexShaders.convexRect != null && NexShaders.convexRect.hasAny();
        if (hasCircleDistortion && hasRectDistortion) {
            buffer.resize(graphics.getWidth(), graphics.getHeight());
            buffer.begin(Color.clear);
            NexShaders.convex.blitFrom(buffer2);
            buffer.end();
            NexShaders.convexRect.blitFrom(buffer);
        } else if (hasCircleDistortion) {
            NexShaders.convex.blitFrom(buffer2);
        } else if (hasRectDistortion) {
            NexShaders.convexRect.blitFrom(buffer2);
        } else {
            blitPremultiplied(buffer2, Shaders.screenspace);
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
