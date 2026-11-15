package newex.content;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Tile;

/** Phần dùng chung của Flasallow / Syrufpat Factory (trích từ phần lặp trong hai file JS). */
final class FactoryFx {
    private FactoryFx() {}

    static boolean isEn() {
        return Core.settings.getString("locale", "").startsWith("en");
    }

    /** Vòng tròn mở rộng khi hoàn thành 1 mẻ sản xuất (màu truyền qua tham số color của effect). */
    static final Effect expandCircleFx = new Effect(25f, e -> {
        float radius = 25f * e.fin();
        Draw.color(e.color);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, radius);
    });

    /**
     * Vụ nổ hủy diệt: giết mọi đơn vị trong bán kính, mọi công trình (trừ chính nó) trong bán kính,
     * rồi phát hiệu ứng nổ + rung màn hình và giết chính công trình.
     */
    static void annihilate(Building center, float explosionRadius, float shakeIntensity, float shakeDuration) {
        final float cx = center.x, cy = center.y;

        killUnitsNear(cx, cy, explosionRadius);

        int tileRadius = (int) Math.ceil(explosionRadius / Vars.tilesize);
        int startX = Math.max(0, center.tileX() - tileRadius);
        int startY = Math.max(0, center.tileY() - tileRadius);
        int endX = Math.min(Vars.world.width() - 1, center.tileX() + tileRadius);
        int endY = Math.min(Vars.world.height() - 1, center.tileY() + tileRadius);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                Tile otherTile = Vars.world.tile(x, y);
                if (otherTile != null && otherTile.build != null) {
                    float dist = Mathf.dst(cx, cy, otherTile.worldx(), otherTile.worldy());
                    if (dist <= explosionRadius && otherTile.build != center) {
                        otherTile.build.kill();
                    }
                }
            }
        }

        Fx.impactReactorExplosion.at(cx, cy);
        Effect.shake(shakeIntensity, shakeDuration, cx, cy);

        center.kill();
    }

    private static void killUnitsNear(float x, float y, float radius) {
        mindustry.entities.Units.nearby(null, x, y, radius, unit -> unit.kill());
    }

    /** Hộp thoại thông số có thanh cuộn. */
    static void showSpecs(String title, String descStr) {
        BaseDialog dialog = new BaseDialog(title);
        Table infoTable = new Table();
        Cell<Label> cell = infoTable.add(descStr).width(360f);
        cell.get().setWrap(true);
        cell.get().setAlignment(Align.left);

        ScrollPane scroll = new ScrollPane(infoTable);
        scroll.setScrollingDisabled(true, false);
        dialog.cont.add(scroll).maxHeight(400f);
        dialog.addCloseButton();
        dialog.show();
    }
}
