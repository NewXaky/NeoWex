package newex.content;

import arc.Events;
import arc.graphics.Color;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.ctype.ContentType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Building;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import arc.util.io.Reads;
import arc.util.io.Writes;
import newex.PerfCore;

/**
 * Chuyển từ zoj.js. Pháo nhiều loại đạn cơ bản: mỗi phát trúng cộng 30 vật phẩm cùng loại vào lõi,
 * phá hủy chính nó sau 100 phát bắn; giá xây tăng gấp đôi theo số lượng đang có.
 */
public final class Zoj {
    private static final int BASE_COST = 16;
    private static final int MAX_COST = 99999;

    private static BulletType zojBullet;
    private static ItemTurret zojBlock;

    private Zoj() {}

    /** Gọi từ NexMod.loadContent(): tạo đạn trước khi game khởi tạo/ load nội dung. */
    public static void create() {
        zojBullet = new BasicBulletType(15f, 30f) {
            {
                width = 4f;
                height = 12f;
                lifetime = 40f;
                frontColor = Color.valueOf("#e0ea87");
                backColor = Color.valueOf("#e5ff00");
                trailColor = Color.valueOf("#daea80");

                homingPower = 0.8f;
                homingRange = 300f;
                homingDelay = 0f;
            }

            @Override
            public void hitEntity(Bullet b, Hitboxc entity, float health) {
                super.hitEntity(b, entity, health);

                if (b.owner instanceof Building) {
                    Building owner = (Building) b.owner;
                    if (owner.team != null && owner.isValid()) {
                        CoreBuild core = owner.team.core();
                        if (core != null) {
                            Item ammoItem = Items.copper;
                            if (owner instanceof ItemTurret.ItemTurretBuild) {
                                ItemTurret.ItemTurretBuild turret = (ItemTurret.ItemTurretBuild) owner;
                                if (turret.ammo != null && !turret.ammo.isEmpty()) {
                                    ItemTurret.ItemEntry entry = (ItemTurret.ItemEntry) turret.ammo.peek();
                                    if (entry != null && entry.item != null) ammoItem = entry.item;
                                }
                            }
                            core.items.add(ammoItem, 30);
                        }
                    }
                }
            }
        };
    }

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> setup());
    }

    private static int getDynamicCost() {
        int count = PerfCore.teamCount(null, zojBlock);
        long cost = (long) Math.floor(BASE_COST * Math.pow(2, count));
        if (cost > MAX_COST) cost = MAX_COST;
        return (int) cost;
    }

    private static void updateCost() {
        if (zojBlock.requirements != null && zojBlock.requirements.length > 0) {
            zojBlock.requirements[0].amount = getDynamicCost();
        }
    }

    private static void setup() {
        Block b = Vars.content.getByName(ContentType.block, "newex-zoj");
        if (!(b instanceof ItemTurret)) return;
        zojBlock = (ItemTurret) b;

        Item[] selectableItems = {Items.copper, Items.lead, Items.coal, Items.sand, Items.scrap};
        for (Item item : selectableItems) {
            zojBlock.ammoTypes.put(item, zojBullet);
        }

        zojBlock.buildType = () -> new ZojBuild(zojBlock);

        Events.run(Trigger.update, () -> {
            if (!PerfCore.every("zoj-cost", 20f)) return;
            updateCost();
        });
    }

    /** ItemTurretBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class ZojBuild extends ItemTurret.ItemTurretBuild {
        /** Public: survivor-skills đặt lại về 0 khi vượt 50. */
        public int shotsFired = 0;

        public ZojBuild(ItemTurret outer) {
            outer.super();
        }

        @Override
        public BulletType useAmmo() {
            BulletType type = super.useAmmo();
            shotsFired++;
            if (shotsFired >= 100) {
                kill();
            }
            return type;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(shotsFired);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            shotsFired = read.i();
        }

        /** Zoj không tự hồi máu. */
        @Override
        public void heal(float amount) {
        }

        public void customSelfHeal(float amount) {
        }
    }
}
