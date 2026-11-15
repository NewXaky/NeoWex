package newex.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;


public final class NexVoidShieldRenderer {
    private static final Seq<Unit> units = new Seq<>(false, 4, Unit.class);
    private static final FrameBuffer shieldBuffer = new FrameBuffer();

    private NexVoidShieldRenderer() {
    }

    public static void init() {
        if (!Vars.headless) {
            Events.on(EventType.WorldLoadEvent.class, event -> units.clear());
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
        if (!Vars.renderer.animateShields) return;

        NexShaders.CMoonVoidShieldShader shader = NexShaders.cMoonVoidShield;
        if (shader == null) return;

        shader.clear();
        for (int index = 0; index < units.size; index++) {
            Unit moon = units.get(index);
            if (!(moon instanceof VoidShieldHost)) continue;
            VoidShieldHost host = (VoidShieldHost) moon;
            if (!moon.isAdded() || moon.dead) continue;

            shader.add(moon.x, moon.y, host.shieldLongAxis(), host.shieldMinorAxis(),
                    -host.shieldRotation(), host.shieldRenderState(), host.shieldStateProgress(),
                    host.shieldFraction(), host.voidShieldAlpha(), host.voidShieldColor());
        }

        if (!shader.hasShields()) return;

        shieldBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        int batchCount = shader.batchCount();
        for (int batch = 0; batch < batchCount; batch++) {
            int batchIndex = batch;
            Draw.drawRange(NexLayers.VOID_SHIELD + batch * 0.0001f, 1f,
                    () -> shieldBuffer.begin(Color.clear),
                    () -> {
                        shieldBuffer.end();
                        Blending previousBlending = Draw.getBlend();
                        Draw.blend(Blending.normal);
                        shader.setBatch(batchIndex);
                        try {
                            shieldBuffer.blit(shader);
                        } finally {
                            Draw.blend(previousBlending);
                        }
                    });
        }
    }
}