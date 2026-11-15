package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.Vec2;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.ctype.ContentType;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Tile;
import newex.NexNet;
import newex.PerfCore;

/** Chuyển từ sonicor.js. Pháo sóng âm: vùng chữ nhật gây 10% Max HP lên không quân + đẩy lùi, liên kết DTG-Soldern để bắn đạn đánh chặn. */
public final class Sonicor {
    private Sonicor() {}

    private static BasicBulletType interceptBullet;

    private static boolean en() { return Core.settings.getString("locale", "").startsWith("en"); }
    private static String t(String enText, String viText) { return en() ? enText : viText; }

    private static void drawSonicWave(float x, float y, float radius, float alpha) {
        if (radius <= 0f || alpha <= 0f) return;
        Draw.color(Pal.accent);
        Draw.alpha(alpha);
        Lines.stroke(3f);
        Lines.circle(x, y, radius);
        Draw.reset();
    }

    private static void drawRect(float x, float y, float w, float h, Color color, float alpha) {
        if (alpha <= 0f) return;
        Draw.color(color);
        Draw.alpha(alpha);
        Lines.dashLine(x, y, x + w, y, (int) Math.floor(w / 8f));
        Lines.dashLine(x + w, y, x + w, y + h, (int) Math.floor(h / 8f));
        Lines.dashLine(x + w, y + h, x, y + h, (int) Math.floor(w / 8f));
        Lines.dashLine(x, y + h, x, y, (int) Math.floor(h / 8f));
        float c = 6f;
        Drawf.square(x, y, c, 0f, color);
        Drawf.square(x + w, y, c, 0f, color);
        Drawf.square(x + w, y + h, c, 0f, color);
        Drawf.square(x, y + h, c, 0f, color);
        Draw.reset();
    }

    public static void init() {
        interceptBullet = new BasicBulletType() {
            @Override
            public void draw(Bullet b) {
                TextureRegion region = PerfCore.region("newex-sonicor-bullet", "sonicor-bullet");
                Draw.color(Pal.accent);
                Draw.rect(region, b.x, b.y, b.rotation() - 90f);
                Draw.reset();
            }
        };
        interceptBullet.speed = 24f;
        interceptBullet.damage = 0f;
        interceptBullet.lifetime = 20f;
        interceptBullet.width = 8f;
        interceptBullet.height = 16f;
        interceptBullet.frontColor = Color.white;
        interceptBullet.backColor = Pal.accent;
        interceptBullet.hitEffect = Fx.hitLaser;
        interceptBullet.despawnEffect = Fx.hitLaser;
        interceptBullet.collides = false;
        interceptBullet.collidesTiles = false;
        interceptBullet.collidesAir = false;
        interceptBullet.collidesGround = false;
        interceptBullet.sprite = "newex-sonicor-bullet";

        Events.on(ContentInitEvent.class, e -> setup());
    }

    private static void setup() {
        Block b = Vars.content.getByName(ContentType.block, "newex-sonicor");
        if (b == null) return;

        b.configurable = true;
        b.config(Integer.class, (SonicorBuild tile, Integer value) -> {
            if (tile != null) tile.setTargetPosition(value);
        });
        b.config(Boolean.class, (SonicorBuild tile, Boolean value) -> {
            if (tile != null) tile.applyDtgLink(value);
        });
        b.buildType = SonicorBuild::new;
    }

    public static class SonicorBuild extends Building {
        float targetX = 0f, targetY = 0f;
        final float rectWidth = 80f;
        boolean selectingTarget = false;
        final float baseMaxRange = 320f;

        boolean linkedWithDTG = false;
        Building dtgBuilding = null;
        int savedDtgTileX = -1, savedDtgTileY = -1;

        float sonicTimer = 0f, waveProgress = 0f;
        boolean isWaveActive = false;
        float shootTimer = 0f;

        @Override
        public void created() {
            super.created();
            targetX = x;
            targetY = y;
        }

        @Override
        public void placed() {
            super.placed();
            targetX = x;
            targetY = y;
        }

        float range() { return baseMaxRange; }

        void setTargetPosition(int val) {
            Point2 pos = Point2.unpack(val);
            targetX = pos.x * Vars.tilesize;
            targetY = pos.y * Vars.tilesize;
        }

        int getTargetPosition() {
            return Point2.pack((int) Math.floor(targetX / Vars.tilesize), (int) Math.floor(targetY / Vars.tilesize));
        }

        private static boolean isDtg(Building b) {
            String n = b.block.name;
            return n.contains("soldern") || n.contains("dtg");
        }

        Building findDtgBuilding() {
            final Building[] found = {null};
            Units.nearbyBuildings(x, y, 240f, b -> {
                if (isDtg(b)) found[0] = b;
            });
            return found[0];
        }

        void applyDtgLink(boolean link) {
            Building found = link ? findDtgBuilding() : null;
            if (found != null) {
                dtgBuilding = found;
                linkedWithDTG = true;
                savedDtgTileX = found.tileX();
                savedDtgTileY = found.tileY();
            } else {
                linkedWithDTG = false;
                dtgBuilding = null;
                savedDtgTileX = -1;
                savedDtgTileY = -1;
            }
        }

        /** {minX, minY, width, height} */
        float[] getRectBounds(float tx, float ty) {
            return new float[]{
                Math.min(x, tx) - rectWidth / 2f,
                Math.min(y, ty) - rectWidth / 2f,
                Math.abs(tx - x) + rectWidth,
                Math.abs(ty - y) + rectWidth
            };
        }

        private void openInfoDialog() {
            String title = t(" Sonicor Turret Stats ", " Thông số pháo Sonicor ");
            boolean isLinked = linkedWithDTG && dtgBuilding != null && dtgBuilding.isValid();
            String desc = t(
                "[gold]⚡ SONICOR WAVE TURRET ⚡[]\n[lightgray]Status:[] " + (isLinked ? "[green]Linked with DTG-Soldern[]" : "[yellow]Standalone Mode[]") + "\n[lightgray]Base Damage:[] [red]10% Max HP / wave[]\n[lightgray]Target Type:[] [sky]Air Units Only[]\n\n[sky]⚡ MECHANICS:[]\n• [yellow]Sonic Pulse Repulsion:[] Deals 10% Max HP damage then repels all air units out of the zone.\n• [yellow]DTG-Soldern Link:[] Intercepts enemy bullets inside the zone with Sonicor Bullets.",
                "[gold]⚡ THÔNG SỐ PHÁO SÓNG ÂM SONICOR ⚡[]\n[lightgray]Trạng thái:[] " + (isLinked ? "[green]Đã liên kết DTG-Soldern[]" : "[yellow]Chế độ độc lập[]") + "\n[lightgray]Sát thương cơ bản:[] [red]10% Max HP / đợt[]\n[lightgray]Mục tiêu:[] [sky]Chỉ không quân[]\n\n[sky]⚡ CƠ CHẾ HOẠT ĐỘNG:[]\n• [yellow]Sóng âm đẩy lùi:[] Gây 10% Max HP sát thương rồi đẩy văng mọi loại unit bay ra khỏi vùng chữ nhật.\n• [yellow]Liên kết DTG-Soldern:[] Bắn đạn Sonicor đánh chặn đạn địch đi vào vùng hình chữ nhật.");

            BaseDialog dialog = new BaseDialog(title);
            Table info = new Table();
            Label l = new Label(desc);
            l.setWrap(true);
            l.setAlignment(Align.left);
            info.add(l).width(360f);
            ScrollPane scroll = new ScrollPane(info);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton();
            dialog.show();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();

            table.button(Icon.commandRally, Styles.cleari, 40f, () -> {
                deselect();
                selectingTarget = true;
                Vars.ui.hudfrag.showToast(t("Select target to form rectangular wave zone!", "Nhấp chọn vị trí để tạo vùng hình chữ nhật!"));
            }).size(50f, 40f).tooltip(t("Set Wave Rect Zone", "Đặt vị trí vùng chữ nhật sóng âm"));

            table.button(Icon.link, Styles.cleari, 40f, () -> {
                Building found = findDtgBuilding();
                if (found != null) {
                    NexNet.configure(this, Boolean.TRUE);
                    NexNet.fx(Fx.select, found.x, found.y);
                    Vars.ui.showInfoToast(t("[green]Linked with DTG-Soldern! Interception Active.[]", "[green]Đã liên kết với DTG-Soldern! Kích hoạt đạn đánh chặn.[]"), 3f);
                } else {
                    NexNet.configure(this, Boolean.FALSE);
                    NexNet.fx(Fx.smeltsmoke, x, y);
                    Vars.ui.showInfoToast(t("[red]No DTG-Soldern found within range![]", "[red]Không tìm thấy công trình liên kết trong phạm vi![]"), 3f);
                }
                deselect();
            }).size(50f, 40f).tooltip(t("Link with DTG-Soldern for Buff", "Liên kết với DTG-Soldern nhận Buff"));

            table.button(Icon.info, Styles.cleari, 40f, this::openInfoDialog)
                .size(50f, 40f).tooltip(t("View detailed stats", "Xem thông số chi tiết pháo"));
        }

        @Override
        public Object config() { return Integer.valueOf(getTargetPosition()); }

        @Override
        public void configured(Unit builder, Object value) {
            super.configured(builder, value);
            if (value instanceof Integer) {
                Point2 pos = Point2.unpack((Integer) value);
                float destX = pos.x * Vars.tilesize, destY = pos.y * Vars.tilesize;
                if (Mathf.dst(x, y, destX, destY) <= range()) {
                    targetX = destX;
                    targetY = destY;
                    Fx.select.at(destX, destY);
                } else {
                    Fx.smeltsmoke.at(destX, destY);
                }
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(targetX);
            write.f(targetY);
            write.bool(linkedWithDTG);
            write.i(dtgBuilding != null ? dtgBuilding.tileX() : savedDtgTileX);
            write.i(dtgBuilding != null ? dtgBuilding.tileY() : savedDtgTileY);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            targetX = read.f();
            targetY = read.f();
            linkedWithDTG = read.bool();
            savedDtgTileX = read.i();
            savedDtgTileY = read.i();
        }

        @Override
        public void updateTile() {
            super.updateTile();

            if (linkedWithDTG && (dtgBuilding == null || !dtgBuilding.isValid())) {
                if (savedDtgTileX >= 0 && savedDtgTileY >= 0) {
                    Building bd = Vars.world.build(savedDtgTileX, savedDtgTileY);
                    if (bd != null && bd.team == team && isDtg(bd)) {
                        dtgBuilding = bd;
                    } else {
                        linkedWithDTG = false;
                        dtgBuilding = null;
                    }
                }
            }

            if (selectingTarget && Core.input.keyTap(KeyCode.mouseLeft)) {
                Vec2 world = Core.camera.unproject(Core.input.mouse());
                Tile tile = Vars.world.tileWorld(world.x, world.y);
                if (tile != null) NexNet.configure(this, Integer.valueOf(Point2.pack(tile.x, tile.y)));
                selectingTarget = false;
            }

            if (efficiency <= 0f) return;

            float[] rect = getRectBounds(targetX, targetY);
            sonicTimer += Time.delta;

            if (sonicTimer >= 120f) {
                final boolean[] hasEnemyAir = {false};
                Groups.unit.intersect(rect[0], rect[1], rect[2], rect[3], u -> {
                    if (u.team != team && u.isFlying()) hasEnemyAir[0] = true;
                });

                if (hasEnemyAir[0]) {
                    sonicTimer = 0f;
                    isWaveActive = true;
                    waveProgress = 0f;

                    Groups.unit.intersect(rect[0], rect[1], rect[2], rect[3], u -> {
                        if (u.team != team && u.isFlying()) {
                            u.damage(u.maxHealth * 0.10f);

                            float angle = Angles.angle(x, y, u.x, u.y);
                            float pushX = Mathf.cosDeg(angle) * 60f, pushY = Mathf.sinDeg(angle) * 60f;
                            u.vel.add(pushX * 0.2f, pushY * 0.2f);
                            u.moveAt(Tmp.v1.set(pushX, pushY));

                            Fx.hitLaser.at(u.x, u.y);
                        }
                    });
                }
            }

            if (isWaveActive) {
                waveProgress += Time.delta / 30f;
                if (waveProgress >= 1f) {
                    isWaveActive = false;
                    waveProgress = 0f;
                }
            }

            if (shootTimer > 0f) shootTimer -= Time.delta;

            if (linkedWithDTG && dtgBuilding != null && dtgBuilding.isValid()) {
                final Bullet[] target = {null};
                Groups.bullet.intersect(rect[0], rect[1], rect[2], rect[3], bl -> {
                    if (target[0] == null && bl.team != team && bl.type != interceptBullet) target[0] = bl;
                });

                if (target[0] != null && shootTimer <= 0f) {
                    Bullet tb = target[0];
                    float angle = Angles.angle(x, y, tb.x, tb.y);
                    interceptBullet.create(this, team, x, y, angle);
                    Fx.shootBig.at(x, y, angle);
                    Fx.hitLaser.at(tb.x, tb.y);
                    tb.remove();
                    shootTimer = 6f;
                }
            }
        }

        @Override
        public void drawConfigure() {
            super.drawConfigure();
            float currentRange = range();
            float[] rect = getRectBounds(targetX, targetY);

            Draw.z(Layer.overlayUI);
            Drawf.dashCircle(x, y, currentRange, Pal.accent);

            if (Mathf.dst(x, y, targetX, targetY) <= currentRange) {
                Drawf.dashLine(Pal.accent, x, y, targetX, targetY);
                drawRect(rect[0], rect[1], rect[2], rect[3], Pal.accent, 1f);
            }

            if (linkedWithDTG && dtgBuilding != null && dtgBuilding.isValid()) {
                Drawf.dashLine(Pal.heal, x, y, dtgBuilding.x, dtgBuilding.y);
            }

            if (isWaveActive) {
                float cx = (x + targetX) / 2f, cy = (y + targetY) / 2f;
                float maxRadius = Math.max(rect[2], rect[3]) / 2f;
                drawSonicWave(cx, cy, waveProgress * maxRadius, 1f - waveProgress);
            }
            Draw.reset();
        }

        @Override
        public void draw() {
            super.draw();

            float[] rect = getRectBounds(targetX, targetY);

            if (isWaveActive) {
                float alpha = 1f - waveProgress;
                Draw.z(Layer.effect);
                drawRect(rect[0], rect[1], rect[2], rect[3], Pal.accent, alpha);

                TextureRegion top = PerfCore.region("newex-sonicor-top", "sonicor-top");
                if (top == null || !Core.atlas.isFound(top)) top = block.region;
                if (top != null && Core.atlas.isFound(top)) {
                    float scale = 1f + waveProgress * 0.8f;
                    Draw.z(Layer.turret + 0.1f);
                    Draw.color(Pal.accent);
                    Draw.alpha(alpha);
                    Draw.rect(top, x, y, top.width * Draw.scl * scale, top.height * Draw.scl * scale);
                    Draw.reset();
                }
            }

            if (selectingTarget) {
                float currentRange = range();
                Draw.z(Layer.overlayUI);
                Drawf.dashCircle(x, y, currentRange, Pal.accent);

                Vec2 mouse = Core.camera.unproject(Core.input.mouse());
                boolean within = Mathf.dst(x, y, mouse.x, mouse.y) <= currentRange;
                Color tc = within ? Pal.accent : Color.red;

                Drawf.dashLine(tc, x, y, mouse.x, mouse.y);

                Tile mt = Vars.world.tileWorld(mouse.x, mouse.y);
                float tx = mt != null ? mt.drawx() : mouse.x;
                float ty = mt != null ? mt.drawy() : mouse.y;
                float[] pr = getRectBounds(tx, ty);
                drawRect(pr[0], pr[1], pr[2], pr[3], tc, 1f);
                Draw.reset();
            }
        }
    }
}
