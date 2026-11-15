package newex.content;

import arc.graphics.g2d.TextureRegion;
import mindustry.world.blocks.storage.CoreBlock;
import newex.PerfCore;

/**
 * Chuyển từ cores.js. Lõi "exzory": block được tạo bằng code (cùng tên với content/blocks/cores/exzory.hjson,
 * hjson sẽ gán các thuộc tính còn lại). Dùng ảnh "-full" làm icon.
 */
public final class ExzoryCore {
    public static CoreBlock exzory;

    private ExzoryCore() {}

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        exzory = new CoreBlock("exzory") {
            TextureRegion fullIconRegion;

            @Override
            public void load() {
                super.load();
                fullIconRegion = PerfCore.region(this.name + "-full");
            }

            @Override
            public TextureRegion[] icons() {
                return new TextureRegion[]{fullIconRegion};
            }
        };
    }
}
