package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * Chuyển từ laytery.js. Pháo laser: khóa mục tiêu 60 tick rồi bắn tia 60 sát thương (tối đa x2 khi bắn liên tục 25 phát), hồi 80 tick.
 * Bản JS tạo khối PowerTurret bằng code và đặt lại các thuộc tính; hjson cùng tên vẫn được nạp sau và ghi đè như trước.
 */
public final class Laytery {
    private static final Color laytAimColor = new Color(0f, 1f, 0.66f, 0.27f);
    private static final Color colorNormal = Color.valueOf("#00ffaa");

    private static final Effect laytMuzzleFX = new Effect(15f, e -> {
        Color tColor = (e.data instanceof Color) ? (Color) e.data : colorNormal;
        Draw.color(tColor);
        Lines.stroke(4f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 16f);
        Draw.reset();
    });

    private static final Effect laytHitFX = new Effect(20f, e -> {
        Color tColor = (e.data instanceof Color) ? (Color) e.data : colorNormal;
        Draw.color(Color.white, tColor, e.fin());
        Lines.stroke(2f * e.fout());
        Lines.circle(e.x, e.y, e.fin() * 20f);
        Angles.randLenVectors(e.id, 8, 26f * e.fin(), (x, y) -> Fill.circle(e.x + x, e.y + y, 2f * e.fout()));
        Draw.reset();
    });

    private static BulletType laytFakeBullet;
    private static PowerTurret laytery;

    private Laytery() {}

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        laytFakeBullet = new BulletType() {
            @Override
            public void init(Bullet b) {
                if (b != null) b.remove();
            }

            @Override
            public void draw(Bullet b) {
            }
        };
        laytFakeBullet.speed = 0f;
        laytFakeBullet.lifetime = 1f;

        laytery = new PowerTurret("laytery");

        laytery.health = 13000;
        laytery.size = 4;
        laytery.range = 224f;
        laytery.reload = 9999f;
        laytery.shootType = laytFakeBullet;
        laytery.consumePower(12f);

        laytery.targetAir = true;
        laytery.targetGround = true;
        laytery.configurable = true;

        laytery.buildType = () -> new LaytBuild(laytery);
    }

    private static boolean isEnglish() {
        String loc = Core.settings.getString("locale", "default");
        return loc != null && loc.startsWith("en");
    }

    private static void drawLaytLaserRing(float cx, float cy, float radiusX, float radiusY, float laserAngle, float strokeWidth, Color color, boolean isFill) {
        Draw.color(color);
        if (!isFill) Lines.stroke(strokeWidth);
        int steps = 24;
        float lastX = 0f, lastY = 0f;
        double cosA = Math.cos(laserAngle * Mathf.degRad);
        double sinA = Math.sin(laserAngle * Mathf.degRad);

        if (isFill) {
            for (float r = radiusY; r > 0; r -= 1.5f) {
                float curRadX = (r / radiusY) * radiusX;
                for (int i = 0; i <= steps; i++) {
                    double angle = (i * (360.0 / steps)) * Mathf.degRad;
                    double lx = Math.cos(angle) * curRadX;
                    double ly = Math.sin(angle) * r;
                    float rx = (float) (cx + (lx * cosA - ly * sinA));
                    float ry = (float) (cy + (lx * sinA + ly * cosA));
                    if (i > 0) Lines.line(lastX, lastY, rx, ry);
                    lastX = rx;
                    lastY = ry;
                }
            }
        } else {
            for (int i = 0; i <= steps; i++) {
                double angle = (i * (360.0 / steps)) * Mathf.degRad;
                double lx = Math.cos(angle) * radiusX;
                double ly = Math.sin(angle) * radiusY;
                float rx = (float) (cx + (lx * cosA - ly * sinA));
                float ry = (float) (cy + (lx * sinA + ly * cosA));
                if (i > 0) Lines.line(lastX, lastY, rx, ry);
                lastX = rx;
                lastY = ry;
            }
        }
    }

    /** PowerTurretBuild là lớp lồng nên phải kế thừa qua {@code outer.super()}. */
    public static class LaytBuild extends PowerTurret.PowerTurretBuild {
        float lockTimer = 0f;
        float shootCooldown = 0f;
        float laserActiveTimer = 0f;
        float laserEndX = 0f;
        float laserEndY = 0f;
        Color currentLaserColor = colorNormal;
        float currentLaserSize = 5f;

        float continuousShotCount = 0f;
        final float maxContinuousBonus = 25f;
        float idleResetTimer = 0f;

        public LaytBuild(PowerTurret outer) {
            outer.super();
        }

        @Override
        public boolean canControl() {
            return false;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear();
            table.row();
            final boolean en = isEnglish();

            table.button(Icon.info, Styles.cleari, 40f, () -> {
                String title = en ? " \"Laytery\" Turret Stats: " : " Thông số pháo \"Laytery\": ";
                String descStr = en
                    ? "[gold]⚡ BASIC STATS ⚡[]\n"
                        + "[lightgray]Structure Health:[] [green]13,000[]\n"
                        + "[lightgray]Effective Range:[] [orange]224 pixels[] (28 Tiles)\n"
                        + "[lightgray]Base Damage:[] [yellow]60 DMG / shot[]\n"
                        + "[lightgray]Power Required:[] [gainsboro]12.00 units/sec[]\n\n"
                        + "[sky]⚡ MECHANICS:[]\n"
                        + "• Focuses energy to lock and fire at mixed Air & Ground targets.\n"
                        + "• Barrel rotation speed continuously increases while tracking targets.\n"
                        + "• Damage increases linearly up to +100% during sustained firing."
                    : "[gold]⚡ THÔNG SỐ CƠ BẢN ⚡[]\n"
                        + "[lightgray]Máu cấu trúc:[] [green]13,000[]\n"
                        + "[lightgray]Tầm bắn hiệu dụng:[] [orange]224 pixel[] (28 Ô)\n"
                        + "[lightgray]sát thương gốc:[] [yellow]60 DMG / phát bắn[]\n"
                        + "[lightgray]Năng lượng yêu cầu:[] [gainsboro]12.00 đơn vị/giây[]\n\n"
                        + "[sky]⚡ CƠ CHẾ:[]\n"
                        + "• Tập trung năng lượng khóa và bắn mục tiêu hỗn hợp Không & Đất.\n"
                        + "• Tốc độ xoay nòng tăng tiến liên tục theo thời gian bám đuổi mục tiêu.\n"
                        + "• Sát thương tăng tiến tuyến tính, đạt tối đa +100% khi duy trì xả súng liên tục.";

                FactoryFx.showSpecs(title, descStr);
            }).size(50f, 40f).tooltip(en ? "Turret Stats" : "Thông số tháp pháo");
        }

        @Override
        public void updateTile() {
            if (!enabled) return;

            if (shootCooldown > 0) shootCooldown -= Time.delta;
            if (laserActiveTimer > 0) laserActiveTimer -= Time.delta;

            if (target == null || shootCooldown > 40) {
                idleResetTimer += Time.delta;
                if (idleResetTimer >= 150) {
                    continuousShotCount = Math.max(0f, continuousShotCount - Time.delta * 0.15f);
                }
            } else {
                idleResetTimer = 0f;
            }

            if (power != null && power.status > 0) {
                Teamc currentTarget = Units.closestTarget(team, x, y, range(), u -> u != null && !u.dead(), b -> b != null && !b.dead());

                if (currentTarget != null && !((Healthc) currentTarget).dead()) {
                    target = (Posc) currentTarget;
                    Posc ct = (Posc) currentTarget;

                    float targetAngle = Angles.angle(x, y, ct.x(), ct.y());
                    rotation = targetAngle;

                    if (shootCooldown <= 0) {
                        lockTimer += Time.delta;

                        float requiredLockTime = 60f;

                        if (lockTimer >= requiredLockTime) {
                            lockTimer = 0f;
                            laserActiveTimer = 16f;
                            laserEndX = ct.x();
                            laserEndY = ct.y();

                            shootCooldown = 80f;

                            if (continuousShotCount < maxContinuousBonus) {
                                continuousShotCount++;
                            }

                            float dmgMultiplier = 1f + (continuousShotCount / maxContinuousBonus);
                            float baseDmg = 60f * dmgMultiplier;

                            currentLaserColor = colorNormal;
                            currentLaserSize = 5f;

                            ((Healthc) currentTarget).damage(baseDmg);
                            laytHitFX.at(ct.x(), ct.y(), 0f, colorNormal);
                            Effect.shake(2f, 2f, x, y);
                        }
                    }
                } else {
                    resetLaytStatus();
                }
            } else {
                resetLaytStatus();
            }
        }

        void resetLaytStatus() {
            target = null;
            lockTimer = Math.max(lockTimer - Time.delta * 2f, 0f);
        }

        @Override
        public void draw() {
            super.draw();
            float centerGunX = x;
            float centerGunY = y;

            if (target != null && !((Healthc) target).dead() && shootCooldown <= 0) {
                float requiredLockTime = 60f;
                float chargeProgress = Math.min(1f, lockTimer / requiredLockTime);

                if (chargeProgress > 0.05f) {
                    Draw.z(Layer.bullet + 1f);
                    float maxBallRadius = 12f;
                    float currentRadius = maxBallRadius * chargeProgress + Mathf.absin(Time.time, 1.5f, 1f);
                    Color ballColor = colorNormal;

                    drawLaytLaserRing(centerGunX, centerGunY, currentRadius * 0.6f, currentRadius * 0.6f, rotation + Time.time * 2f, 0f, ballColor, true);
                    drawLaytLaserRing(centerGunX, centerGunY, currentRadius, currentRadius, rotation - Time.time * 3f, 1.5f, ballColor, false);
                    Draw.reset();
                }

                Draw.z(Layer.bullet + 1f);
                float aimStroke = 1f + Mathf.absin(Time.time, 2f, 0.3f);
                Draw.color(laytAimColor);
                Lines.stroke(aimStroke);
                Lines.line(centerGunX, centerGunY, target.x(), target.y());
                Fill.circle(target.x(), target.y(), 2f + Mathf.absin(Time.time, 1.5f, 1f));
                Draw.reset();
            }

            if (laserActiveTimer > 0) {
                Draw.z(Layer.bullet + 2f);
                float progress = laserActiveTimer / 16f;
                float t = 1f - progress;
                float laserZoomStroke = currentLaserSize * (1f + Mathf.absin(Time.time, 1f, 0.2f)) * progress;

                Draw.color(Color.white);
                Lines.stroke(laserZoomStroke * 0.35f);
                Lines.line(centerGunX, centerGunY, laserEndX, laserEndY);

                Draw.color(currentLaserColor, progress);
                Lines.stroke(laserZoomStroke);
                Lines.line(centerGunX, centerGunY, laserEndX, laserEndY);

                float laserAngle = Angles.angle(centerGunX, centerGunY, laserEndX, laserEndY);
                float startOffset = 10f + 40f;
                float endOffset = 10f - 24f;
                float currentOffset = Mathf.lerp(startOffset, endOffset, t);

                float ringX = centerGunX + Angles.trnsx(laserAngle, currentOffset);
                float ringY = centerGunY + Angles.trnsy(laserAngle, currentOffset);
                float baseRadiusX = 2f * (1f + t * 6f);
                float baseRadiusY = 5f * (1f + t * 6f);
                float ringStroke = 1.5f * progress;

                drawLaytLaserRing(ringX, ringY, baseRadiusX, baseRadiusY, laserAngle, ringStroke, currentLaserColor, false);

                float sphereForwardOffset = Mathf.lerp(0f, 45f, t);
                float ballShotX = centerGunX + Angles.trnsx(laserAngle, sphereForwardOffset);
                float ballShotY = centerGunY + Angles.trnsy(laserAngle, sphereForwardOffset);
                float distortedRadiusX = 12f * progress * 1.9f;
                float distortedRadiusY = 12f * progress * 0.2f;

                drawLaytLaserRing(ballShotX, ballShotY, distortedRadiusX, distortedRadiusY, laserAngle, 0f, currentLaserColor, true);
                Draw.reset();
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.f(continuousShotCount);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            continuousShotCount = read.f();
        }
    }
}
