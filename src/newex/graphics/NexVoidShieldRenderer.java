package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.GL20;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.struct.Seq;
import arc.util.Buffers;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;

import java.nio.IntBuffer;

public final class NexVoidShieldRenderer {
    private static final Seq<Unit> units = new Seq<>(false, 4, Unit.class);
    // Lazy-allocated: 0 MB VRAM unless a VoidShieldHost unit is actually on the map.
    private static FrameBuffer shieldBuffer = null;
    private static final IntBuffer fboQueryBuf = Buffers.newIntBuffer(1);

    private NexVoidShieldRenderer() {
    }

    private static int currentFboBinding() {
        try {
            fboQueryBuf.clear();
            Gl.getIntegerv(GL20.GL_FRAMEBUFFER_BINDING, fboQueryBuf);
            return fboQueryBuf.get(0);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void restoreFboBinding(int fbo) {
        if (NexDarkGraphics.rebindIfCapturing()) return;
        try {
            Gl.bindFramebuffer(GL20.GL_FRAMEBUFFER, fbo);
            Gl.viewport(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
        } catch (Throwable ignored) {}
    }

    private static void disposeBuffer() {
        if (shieldBuffer != null) {
            try { shieldBuffer.dispose(); } catch (Throwable ignored) {}
            shieldBuffer = null;
        }
    }

    public static void init() {
        if (!Vars.headless) {
            Events.on(EventType.WorldLoadEvent.class, event -> {
                units.clear();
                disposeBuffer();
            });
            Events.run(EventType.Trigger.draw, NexVoidShieldRenderer::draw);
        }
    }

    public static void add(Unit unit) {
        if (!units.contains(unit, true)) units.add(unit);
    }

    public static void remove(Unit unit) {
        units.remove(unit, true);
    }

    private static void draw() {
        if (Vars.state == null || !Vars.state.isGame()) {
            if (shieldBuffer != null) disposeBuffer();
            return;
        }
        if (units.isEmpty() || !Vars.renderer.animateShields) return;

        NexShaders.CMoonVoidShieldShader shader = NexShaders.cMoonVoidShield;
        if (shader == null) return;

        shader.clear();
        for (int index = units.size - 1; index >= 0; index--) {
            Unit moon = units.get(index);
            if (!(moon instanceof VoidShieldHost) || !moon.isAdded() || moon.dead) {
                units.remove(index);
                continue;
            }
            VoidShieldHost host = (VoidShieldHost) moon;
            shader.add(moon.x, moon.y, host.shieldLongAxis(), host.shieldMinorAxis(),
                    -host.shieldRotation(), host.shieldRenderState(), host.shieldStateProgress(),
                    host.shieldFraction(), host.voidShieldAlpha(), host.voidShieldColor());
        }

        if (!shader.hasShields()) return;

        int w = Math.max(1, Core.graphics.getWidth());
        int h = Math.max(1, Core.graphics.getHeight());
        if (shieldBuffer == null) {
            shieldBuffer = new FrameBuffer(w, h);
        } else if (shieldBuffer.getWidth() != w || shieldBuffer.getHeight() != h) {
            shieldBuffer.resize(w, h);
        }

        int batchCount = shader.batchCount();
        for (int batch = 0; batch < batchCount; batch++) {
            final int batchIndex = batch;
            final int[] prevFbo = {0};
            Draw.drawRange(NexLayers.VOID_SHIELD + batch * 0.0001f, 1f,
                    () -> {
                        Draw.flush();
                        prevFbo[0] = currentFboBinding();
                        if (shieldBuffer != null) shieldBuffer.begin(Color.clear);
                    },
                    () -> {
                        Draw.flush();
                        if (shieldBuffer != null) {
                            shieldBuffer.end();
                            restoreFboBinding(prevFbo[0]);
                            Blending previousBlending = Draw.getBlend();
                            Draw.blend(Blending.normal);
                            shader.setBatch(batchIndex);
                            try {
                                shieldBuffer.blit(shader);
                            } finally {
                                Draw.blend(previousBlending);
                            }
                        } else {
                            restoreFboBinding(prevFbo[0]);
                        }
                    });
        }
    }
}
