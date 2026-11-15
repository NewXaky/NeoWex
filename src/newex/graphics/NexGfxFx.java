package newex.graphics;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;
import newex.util.Vec2Seq;

import static arc.graphics.g2d.Draw.color;
import static arc.graphics.g2d.Lines.*;
import static arc.math.Angles.randLenVectors;

/** Các Effect nền mà BeamLightning / PositionLightning / Drawn dùng (trích từ WarHammer). */
public final class NexGfxFx {
    private NexGfxFx() {}

    public static final float lightningAlign = 0.5f;

    public static Effect lightningSpark, posLightning, chainLightningFade, chainLightningFadeReversed;

    private static boolean loaded;

    public static float fout(float fin, float margin) {
        return fin >= 1 - margin ? 1 - (fin - (1 - margin)) / margin : 1;
    }

    /** Gọi 1 lần (NexMod.loadContent hoặc NexGraphics.load). */
    public static void load() {
        if (loaded) return;
        loaded = true;

        lightningSpark = new Effect(Fx.chainLightning.lifetime, e -> {
            color(Color.white, e.color, e.fin() + 0.25f);
            stroke(0.65f + e.fout());
            randLenVectors(e.id, 3, e.fin() * e.rotation + 6.0f, (x, y) -> {
                lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 4.0f + 2.0f);
            });
            Fill.circle(e.x, e.y, 2.5f * e.fout());
        });

        posLightning = new Effect(PositionLightning.lifetime, 1500, e -> {
            if (!(e.data instanceof Vec2Seq)) return;
            Vec2Seq lines = e.data();

            color(e.color, Color.white, e.fout() * 0.6f);
            stroke(e.rotation * e.fout());

            Fill.circle(lines.firstTmp().x, lines.firstTmp().y, getStroke() / 2f);

            for (int i = 0; i < lines.size() - 1; i++) {
                Vec2 cur = lines.setVec2(i, Tmp.v1);
                Vec2 next = lines.setVec2(i + 1, Tmp.v2);

                line(cur.x, cur.y, next.x, next.y, false);
                Fill.circle(next.x, next.y, getStroke() / 2f);
            }
        }).layer(Layer.effect - 0.001f);

        chainLightningFade = new Effect(240f, 500f, e -> {
            if (!NexSettings.effectEnabled()) return;
            if (!(e.data instanceof Position)) return;
            Position p = e.data();
            float tx = p.getX(), ty = p.getY(), dst = Mathf.dst(e.x, e.y, tx, ty);
            Tmp.v1.set(p).sub(e.x, e.y).nor();

            e.lifetime = dst * 0.3f;
            float normx = Tmp.v1.x, normy = Tmp.v1.y;
            float range = e.rotation;
            int links = Mathf.ceil(dst / range);
            float spacing = dst / links;

            stroke(2.5f * Mathf.curve(e.fout(), 0, 0.7f));
            color(e.color, Color.white, e.fout() * 0.6f);

            beginLine();

            Fill.circle(e.x, e.y, getStroke() / 2);
            linePoint(e.x, e.y);

            NexGfxUtil.rand.setSeed(e.id);

            float fin = Mathf.curve(e.fin(), 0, lightningAlign);
            int i;
            float nx = e.x, ny = e.y;
            for (i = 0; i < (int) (links * fin); i++) {
                if (i == links - 1) {
                    nx = tx;
                    ny = ty;
                } else {
                    float len = (i + 1) * spacing;
                    Tmp.v1.setToRandomDirection(NexGfxUtil.rand).scl(range / 2f);
                    nx = e.x + normx * len + Tmp.v1.x;
                    ny = e.y + normy * len + Tmp.v1.y;
                }
                linePoint(nx, ny);
            }

            if (i < links) {
                float f = Mathf.clamp(fin * links % 1);
                float len = (i + 1) * spacing;
                Tmp.v1.setToRandomDirection(NexGfxUtil.rand).scl(range / 2f);
                Tmp.v2.set(nx, ny);
                if (i == links - 1) Tmp.v2.lerp(tx, ty, f);
                else Tmp.v2.lerp(e.x + (normx * len + Tmp.v1.x), e.y + (normy * len + Tmp.v1.y), f);

                linePoint(Tmp.v2.x, Tmp.v2.y);
                Fill.circle(Tmp.v2.x, Tmp.v2.y, getStroke() / 2);
            }

            endLine();
        }).followParent(false);

        chainLightningFadeReversed = new Effect(240f, 500f, e -> {
            if (!NexSettings.effectEnabled()) return;
            if (!(e.data instanceof Position)) return;
            Position p = e.data();
            float tx = e.x, ty = e.y, dst = Mathf.dst(p.getX(), p.getY(), tx, ty);
            Tmp.v1.set(e.x, e.y).sub(p).nor();

            e.lifetime = dst * 0.3f;
            float normx = Tmp.v1.x, normy = Tmp.v1.y;
            float range = e.rotation;
            int links = Mathf.ceil(dst / range);
            float spacing = dst / links;

            stroke(2.5f * Mathf.curve(e.fout(), 0, 0.7f));
            color(e.color, Color.white, e.fout() * 0.6f);

            beginLine();

            Fill.circle(p.getX(), p.getY(), getStroke() / 2);
            linePoint(p.getX(), p.getY());

            NexGfxUtil.rand.setSeed(e.id);

            float fin = Mathf.curve(e.fin(), 0, lightningAlign);
            int i;
            float nx = p.getX(), ny = p.getY();
            for (i = 0; i < (int) (links * fin); i++) {
                if (i == links - 1) {
                    nx = tx;
                    ny = ty;
                } else {
                    float len = (i + 1) * spacing;
                    Tmp.v1.setToRandomDirection(NexGfxUtil.rand).scl(range / 2f);
                    nx = p.getX() + normx * len + Tmp.v1.x;
                    ny = p.getY() + normy * len + Tmp.v1.y;
                }
                linePoint(nx, ny);
            }

            if (i < links) {
                float f = Mathf.clamp(fin * links % 1);
                float len = (i + 1) * spacing;
                Tmp.v1.setToRandomDirection(NexGfxUtil.rand).scl(range / 2f);
                Tmp.v2.set(nx, ny);
                if (i == links - 1) Tmp.v2.lerp(tx, ty, f);
                else Tmp.v2.lerp(p.getX() + (normx * len + Tmp.v1.x), p.getY() + (normy * len + Tmp.v1.y), f);

                linePoint(Tmp.v2.x, Tmp.v2.y);
                Fill.circle(Tmp.v2.x, Tmp.v2.y, getStroke() / 2);
            }

            endLine();
        }).followParent(false);
    }
}
