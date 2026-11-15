package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.Rand;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.abilities.Ability;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.effect.MultiEffect;
import mindustry.entities.part.RegionPart;
import mindustry.entities.part.DrawPart.PartParams;
import mindustry.entities.part.DrawPart.PartProgress;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.gen.TankUnit;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.Env;
import mindustry.world.meta.StatUnit;
import newex.NexBlackHole;
import newex.graphics.Drawn;
import newex.graphics.NexSettings;

/**
 * Unit Tank-15 - xe tăng xung kích hạng nặng.
 *
 * <p>Cơ chế:
 * <ul>
 *   <li>Pháo xoay 4 nòng ở giữa bắn cặp tia rail laser xuyên 300 đơn vị, nạp nhanh dần khi bắn liên tục
 *       (AccelerateReload tới x5 sau 5 giây).</li>
 * </ul>
 */
public final class Tank15 {
    private static final Rand rand = new Rand();
    private static final Vec2 tv = new Vec2();

    public static UnitType tank15;

    private Tank15() {}

    // ------------------------------------------------------------------ màu
    private static Color outline() { return Color.valueOf("26362f"); }
    private static Color heatColor() { return Color.valueOf("9ee8ff"); }
    /** Màu đạn/tia: xanh lam-lục (khớp bảng màu sprite mới). */
    private static Color shootColor() { return Color.valueOf("4fd2f0"); }

    /** Gọi từ NexMod.loadContent(). */
    public static void create() {
        final Color ORANGE = shootColor();
        final Color ORANGE_LIGHT = ORANGE.cpy().lerp(Color.white.cpy(), 0.7f);
        final Color HEAT = heatColor();
        final Color OUTLINE = outline();

        // đạn hố đen: bay tới mục tiêu rồi nổ ra hố đen (500% sát thương = 5 x 80), lực hút cấp 2, phạm vi 50 ô
        final NexBlackHole.BlackHoleBulletType hole = new NexBlackHole.BlackHoleBulletType(20f, 90f) {
            @Override
            public void despawned(Bullet b) {
                super.despawned(b);
                holeBusyUntil.put(ownerKey(b), 0f); // hố đen kết thúc -> cho phép tính tỉ lệ tiếp
            }
        };
        hole.damage = 80f * 5f;
        hole.damageInterval = 30f;
        hole.lifetime = HOLE_LIFE;
        hole.pullForce = 0.7f;
        hole.pullRad = 50f * 8f;
        final BasicBulletType holeShot = new BasicBulletType(8f, 80f * 5f) {{
            lifetime = 300f / 8f;
            width = height = 14f;
            frontColor = Color.white;
            backColor = trailColor = lightColor = Color.valueOf("7a3cff");
            trailWidth = 4f;
            trailLength = 12;
            homingPower = 0.08f;
            homingRange = 160f;
            fragBullet = hole;
            fragBullets = 1;
            fragRandomSpread = 0f;
            fragSpread = 0f;
            fragVelocityMin = fragVelocityMax = 1f;
            hitSound = Sounds.none;
        }};

        tank15 = new UnitType("tank-15") {{
            constructor = TankUnit::create;

            localizedName = "Tank-15";
            description = "Xe tăng xung kích hạng nặng trang bị pháo xoay 4 nòng bắn tia rail laser xuyên giáp.";
            details = "Bắn liên tục giúp tốc độ nạp đạn tăng dần tới x5; mỗi loạt bắn ra cặp tia laser xuyên qua mục tiêu, có 10% tỉ lệ bắn ra đạn hố đen.";

            squareShape = true;
            omniMovement = false;
            rotateMoveFirst = true;
            envDisabled = Env.none;

            hitSize = 40;
            speed = 1f;
            rotateSpeed = 1.5f;
            health = 20000;
            armor = 24;
            itemCapacity = 0;
            floorMultiplier = 0.6f;

            outlineRadius = 3;
            outlineColor = OUTLINE;

            float lx = 200 / 2f, ly = 250 / 2f;
            treadPullOffset = 8;
            treadFrames = 16;
            treadRects = new Rect[]{ new Rect(11 - lx, 6 + 4 - ly, 51, 232) };

            abilities.add(new AccelerateReload(300, 60, 5, 150));
            abilities.add(new BlackHoleInfo());

            // ---- pháo xoay chính 4 nòng
            weapons.add(new Weapon("newex-tank-15-weapon1") {{
                layerOffset = 0.04f;
                y = -19 / 4f;
                x = 0;
                reload = 40;
                mirror = false;
                ejectEffect = Fx.casing3Double;
                recoil = 2f;
                shootSound = Vars.tree.loadSound("tank-15-laser");
                inaccuracy = 4f;
                velocityRnd = 0.15f;
                xRand = 0.3f;
                recoils = 4;
                heatColor = HEAT;

                rotate = true;
                rotateSpeed = 2f;

                shoot = new ShootAlternate() {{
                    barrels = 4;
                    shots = 2;
                    shotDelay = 8;
                    spread = 8 / 4f;
                }};

                shootY = 120 / 4f;
                shootX = 0;

                float x2 = 0 / 4f, y1 = 80 / 4f;

                parts.add(new BarrelPart("-barrel") {{
                    x = x2;
                    y = y1 + recoil / 4f;
                    intervalWidth = 16 / 4f;
                    reloadProgress = heatProgress = progress = PartProgress.recoil;
                }});

                bullet = new RailOrHole(holeShot) {{
                    length = 300;
                    damage = 80;
                    pointEffectSpace = 10f;
                    hitColor = ORANGE;
                    hitEffect = Fx.hitBulletColor;
                    pierceDamageFactor = 0.95f;

                    smokeEffect = Fx.colorSpark;

                    endEffect = new Effect(14f, e -> {
                        Draw.color(e.color);
                        Drawf.tri(e.x, e.y, e.fout() * 1.5f, 5f, e.rotation);
                    });

                    shootEffect = new Effect(10, e -> {
                        Draw.color(e.color);
                        float w = 1.2f + 7 * e.fout();

                        Drawf.tri(e.x, e.y, w, 30f * e.fout(), e.rotation);
                        Draw.color(e.color);

                        for (int i : Mathf.signs) {
                            Drawf.tri(e.x, e.y, w * 0.9f, 18f * e.fout(), e.rotation + i * 90f);
                        }

                        Drawf.tri(e.x, e.y, w, 4f * e.fout(), e.rotation + 180f);
                    });

                    lineEffect = new Effect(20f, e -> {
                        if (!(e.data instanceof Vec2)) return;
                        Vec2 v = (Vec2) e.data;

                        Draw.color(e.color);
                        Lines.stroke(e.fout() * 0.9f + 1.2f);

                        Fx.rand.setSeed(e.id);
                        for (int i = 0; i < (length / pointEffectSpace) - 1; i++) {
                            Fx.v.trns(e.rotation, Fx.rand.random(8f, v.dst(e.x, e.y) - 8f));
                            Lines.lineAngleCenter(e.x + Fx.v.x, e.y + Fx.v.y, e.rotation + e.finpow(), e.foutpowdown() * 20f * Fx.rand.random(0.5f, 1f) + 0.3f);
                        }

                        e.scaled(14f, b -> {
                            Lines.stroke(b.fout() * 1.5f);
                            Draw.color(e.color);
                            Lines.line(e.x, e.y, v.x, v.y);
                        });
                    });
                }};
            }});
        }};
    }

    // ================================================================== hiệu ứng

    /** Tia lửa bắn ra từ nòng. */
    static Effect shootLine(final float range, final float angleRange) {
        return new Effect(37f, e -> {
            rand.setSeed(e.id);

            int num = Mathf.clamp((int) range / 6, 6, 20);
            float thick = Mathf.clamp(0.75f, 1, range / 22f * rand.random(0.5f, 1f));
            Draw.color(e.color, Color.white, e.fout() * 0.7f);

            randLenVectors(e.id, num, 4 + (range * 1.2f) * e.fin(), range * 0.15f * e.fin(), e.rotation, angleRange, (x, y) -> {
                Lines.stroke(thick * e.fout(0.32f));
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), (e.fslope() + e.fin()) * 0.5f * (range * rand.random(0.1f, 0.3f) + rand.random(2f)) + rand.random(2f));
                Drawf.light(e.x + x, e.y + y, e.fslope() * (range * 0.5f + 14f) + 3, e.color, 0.7f);
            });
        });
    }

    /** Các hạt vuông bay ra. */
    static Effect square(float lifetime, final Color color, final int num, final float range, final float size) {
        return new Effect(lifetime, e -> {
            Draw.color(color);
            rand.setSeed(e.id);
            int intensity = detailCount(num, 1);
            Angles.randLenVectors(e.id, intensity, range * e.finpow(), (x, y) -> {
                float s = Mathf.curve(e.fin(), 0f, 0.1f) * e.fout(Interp.pow3In) * (size + rand.range(size / 3.0f));
                Fill.square(e.x + x, e.y + y, s, 45.0f);
                Drawf.light(e.x + x, e.y + y, s * 2.25f, color, 0.7f);
            });
        });
    }

    /** Tia sáng + vòng tròn khi trúng đích. */
    static Effect instHit(final Color color, final boolean square, final int num, final float size) {
        return new Effect(20, size * 1.5f, e -> {
            rand.setSeed(e.id);

            for (int i = 0; i < 2; ++i) {
                Draw.color(i == 0 ? color : color.cpy().lerp(Color.white, 0.25f));
                float m = i == 0 ? 1.0f : 0.5f;

                for (int j = 0; j < num; ++j) {
                    float rot = e.rotation + rand.range(size);
                    float w = 15 * e.fout() * m;
                    Drawn.tri(e.x, e.y, w, (size + rand.range(size * 0.6f)) * m, rot);
                    Drawn.tri(e.x, e.y, w, size * 0.3f * m, rot + 180);
                }
            }

            e.scaled(12, c -> {
                Draw.color(color.cpy().lerp(Color.white, 0.25f));
                Lines.stroke(c.fout() * 2.0f + 0.2f);
                Lines.circle(e.x, e.y, c.fin() * size * 1.1f);
            });

            if (square) e.scaled(18, c -> {
                Draw.color(color);
                int intensity = detailCount((int) size, 4);
                Angles.randLenVectors(e.id, intensity, 8.0f + e.fin() * size * 1.25f, e.rotation, 60.0f, (x, y) ->
                    Fill.square(e.x + x, e.y + y, c.fout() * 3, 45));
            });

            Drawf.light(e.x, e.y, e.fout() * size, color, 0.7f);
        });
    }

    /** Vụ nổ chung: khói + tia lửa + vòng sóng (bỏ hiệu ứng thị sai 2.5D). */
    static Effect generalExplosion(final float lifetime, final Color color, final float size, final int amount, final boolean smooth) {
        return new MultiEffect(new Effect(lifetime * 2.5f, e -> {
            int detailAmount = detailCount(amount, 4);

            Draw.color(color);
            rand.setSeed(e.id);

            e.scaled(lifetime, i -> {
                Lines.stroke(3f * i.fout(Interp.pow2Out));
                if (!smooth) Lines.circle(e.x, e.y, 3f + i.fin(Interp.circleOut) * size);
                if (smooth) {
                    Draw.blend(arc.graphics.Blending.additive);
                    Lines.circle(e.x, e.y, i.fin(Interp.circleOut) * size);
                    if (NexSettings.effectEnabled())
                        Drawn.shockWave(e.x, e.y, size, size * 0.55f * i.fout(Interp.pow2Out), i.fin(Interp.circleOut), color);
                    Draw.blend();
                }
            });

            Draw.color(Color.gray);
            Angles.randLenVectors(e.id, detailAmount / 2, 2f + size * 0.85f * e.finpow(), e.rotation, 360f, (sx, sy) ->
                Fill.circle(e.x + sx, e.y + sy, e.fout() * (Math.max(size / 8, 6) + 0.5f)));

            Draw.color(Pal.lighterOrange, Color.gray.cpy(), e.fout());
            e.scaled(lifetime, e1 -> {
                Lines.stroke(2 * e1.fout());
                float radius = 1f + Mathf.randomSeed(e1.id, 0.5f, 1) * size * 0.85f * e1.finpow();
                Angles.randLenVectors(e1.id + 1, (int) (detailAmount * 1.5f), radius, e1.rotation, 360f, (sx, sy) -> {
                    float ang = Angles.angle(0f, 0f, sx, sy);
                    Lines.lineAngle(e1.x + sx, e1.y + sy, ang, e1.fout() * Math.max(size / 8, 10) * Mathf.randomSeed(e1.id, 0.3f, 1.2f));
                });
                Drawf.light(e1.x, e1.y, size * 1.5f, Pal.lighterOrange, 0.8f * e1.fout());
            });
        }));
    }

    private static int detailCount(int full, int min) {
        if (full <= 0) return 0;
        return NexSettings.effectEnabled() ? full : Math.max(min, full / 2);
    }

    /** randLenVectors có độ dài tối thiểu: vị trí = minLength + random(length). */
    private static void randLenVectors(long seed, int amount, float length, float minLength, float angle, float range, arc.func.Floatc2 cons) {
        rand.setSeed(seed);
        for (int i = 0; i < amount; i++) {
            tv.trns(angle + rand.range(range), minLength + rand.random(length));
            cons.get(tv.x, tv.y);
        }
    }

    // ================================================================== kỹ năng: tăng tốc bắn

    /** Bắn liên tục thì hệ số nạp đạn tăng dần; ngừng bắn đủ lâu mới giảm lại. */
    public static class AccelerateReload extends Ability {
        /** Hệ số nạp đạn tối đa. */
        public float maxMultiplier = 2f;
        /** Thời gian giữ buff đầy sau khi ngừng bắn (tick). */
        public float resetTime = 120;
        /** Thời gian bắn để lên tới tối đa (tick). */
        public float increaseTime = 300;
        /** Thời gian để buff rơi về x1 (tick). */
        public float decreaseTime = 60;
        public boolean liner = true;

        protected float timer = 0;
        protected float reloadMultiplier = 1f;

        public AccelerateReload() {}

        public AccelerateReload(float maxMultiplier, float resetTime) {
            this.maxMultiplier = maxMultiplier;
            this.resetTime = resetTime;
        }

        public AccelerateReload(float increaseTime, float decreaseTime, float maxMultiplier, float resetTime) {
            this.increaseTime = increaseTime;
            this.decreaseTime = decreaseTime;
            this.maxMultiplier = maxMultiplier;
            this.resetTime = resetTime;
        }

        @Override
        public void update(Unit unit) {
            super.update(unit);

            float re = Mathf.clamp(reloadMultiplier, 1, maxMultiplier);
            if (timer <= resetTime) {
                if (unit.isShooting) {
                    reloadMultiplier = Mathf.approachDelta(re, maxMultiplier, (liner ? 1 : re) / increaseTime);
                    timer = 0;
                } else if (reloadMultiplier >= maxMultiplier) {
                    timer += Time.delta;
                }
            } else {
                reloadMultiplier = Mathf.approachDelta(reloadMultiplier, 1, (liner ? 1 : re) / decreaseTime);
                if (Mathf.equal(reloadMultiplier, 1, 0.01f)) {
                    timer = 0;
                }
            }

            unit.reloadMultiplier *= re;
        }

        @Override
        public String localized() {
            return "Tăng tốc nạp đạn";
        }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Tăng khi đang bắn: [white]+" + Strings.autoFixed(60f * 100f / increaseTime, 0) + "%" + StatUnit.perSecond.localized());
            t.row();
            t.add("[lightgray]Giảm khi ngừng bắn: [white]-" + Strings.autoFixed(60f * 100f / decreaseTime, 0) + "%" + StatUnit.perSecond.localized());
            t.row();
            t.add("[lightgray]Tốc độ bắn tối đa: [white]" + Strings.autoFixed(maxMultiplier * 100, 0) + "%");
            t.row();
            t.add("[lightgray]Thời gian duy trì: [white]" + Strings.autoFixed(resetTime / 60f, 0) + " " + StatUnit.seconds.localized());
        }
    }

    static final float HOLE_LIFE = 150f;
    /** Thời điểm (Time.time) mà hố đen của từng xe kết thúc; 0 = không có hố đen đang hoạt động. */
    static final arc.struct.IntFloatMap holeBusyUntil = new arc.struct.IntFloatMap();

    static int ownerKey(Bullet b) { return b.owner instanceof Unit ? ((Unit) b.owner).id : 0; }

    /** Tia rail: 10% thay bằng một viên đạn hố đen bắn tới mục tiêu. */
    public static class RailOrHole extends RailBulletType {
        final BulletType holeShot;

        RailOrHole(BulletType holeShot) { this.holeShot = holeShot; }

        @Override
        public void init(Bullet b) {
            int key = ownerKey(b);
            // chỉ tính 10% khi hố đen trước đó của xe này đã kết thúc
            if (Time.time >= holeBusyUntil.get(key, 0f) && Mathf.chance(0.1)) {
                holeBusyUntil.put(key, Time.time + holeShot.lifetime + HOLE_LIFE + 30f); // dự phòng nếu đạn mang bị hủy
                holeShot.create(b.owner, b.team, b.x, b.y, b.rotation());
                return;
            }
            super.init(b);
        }
    }

    /** Chỉ để hiển thị khả năng "Hố đen" trong bảng thông tin unit. */
    public static class BlackHoleInfo extends Ability {
        @Override
        public String localized() { return "Đạn hố đen"; }

        @Override
        public void addStats(Table t) {
            t.add("[lightgray]Tỉ lệ: [white]10% mỗi tia bắn (chỉ tính khi hố đen trước đã kết thúc)");
            t.row();
            t.add("[lightgray]Sát thương: [white]500% tấn công");
            t.row();
            t.add("[lightgray]Lực hút: [white]cấp 2, phạm vi 50 ô");
        }
    }

    // ================================================================== bộ phận vẽ: cụm nòng xoay

    /** Cụm nòng xoay vòng quanh trục khi bắn. */
    public static class BarrelPart extends RegionPart {
        public Color heatColor = Pal.turretHeat.cpy();
        public float intervalWidth = 12 / 4f;
        public int barrelCount = 5;
        public float layerOffset = -0.001f;
        public float rotateSpeed = 1f;
        public PartProgress progress = PartProgress.warmup;
        public PartProgress reloadProgress = PartProgress.recoil;
        public PartProgress recoilProgress = PartProgress.recoil;

        public TextureRegion barrel, barrelOutline, barrelHeat;

        public BarrelPart(String suffix) {
            this.suffix = suffix;
        }

        public BarrelPart() {}

        @Override
        public void load(String name) {
            super.load(name);
            String realName = this.name == null ? name + suffix : this.name;
            barrel = Core.atlas.find(realName);
            barrelOutline = Core.atlas.find(realName + "-outline");
            barrelHeat = Core.atlas.find(realName + "-heat");
        }

        @Override
        public void getOutlines(Seq<TextureRegion> out) {
            super.getOutlines(out);
            out.add(barrel);
        }

        @Override
        public void draw(PartParams params) {
            float prog = progress.getClamp(params, clampProgress);
            float reload = reloadProgress.getClamp(params, false);
            float recoil = recoilProgress.getClamp(params, clampProgress);
            float time = (360f * 1.5f / barrelCount) * (prog * reload);

            float mx = moveX * prog, my = moveY * prog, mr = moveRot * prog + rotation;

            int len = mirror && params.sideOverride == -1 ? 2 : 1;
            float z = Draw.z() + layerOffset;
            for (int s = 0; s < len; s++) {
                int m = params.sideOverride == -1 ? s : params.sideOverride;

                Vec2 v = Tmp.v1;
                float sign = (m == 0 ? 1 : -1) * params.sideMultiplier;
                Tmp.v1.set((x + mx) * sign, y + my).rotateRadExact((params.rotation - 90) * Mathf.degRad);

                Draw.xscl *= sign;
                float
                    rx = params.x + Tmp.v1.x,
                    ry = params.y + Tmp.v1.y,
                    rot = mr + params.rotation - 90;

                Draw.z(z);
                float angle = 360f / barrelCount;
                for (int i = 0; i < barrelCount; i++) {
                    v.trns(params.rotation - 90f, intervalWidth * Mathf.cosDeg(time - angle * i), 3 * recoil * Mathf.sinDeg(time - angle * i));
                    float z1 = z - 0.003f - Mathf.sinDeg(time - angle * i) * 0.001f;
                    Draw.z(z1);
                    if (barrelOutline != null) {
                        rect(barrelOutline, rx + v.x, ry + v.y, rot);
                    }
                    if (barrel != null) {
                        rect(barrel, rx + v.x, ry + v.y, rot);
                    }
                    if (heat.found() && barrelHeat != null && barrelHeat.found() && z1 >= z - 0.002f) {
                        float hprog = heatProgress.getClamp(params, clampProgress);
                        heatColor.write(Tmp.c1).a(hprog * heatColor.a);
                        Drawf.additive(barrelHeat, Tmp.c1, 1f, rx + v.x, ry + v.y, rot, turretShading ? turretHeatLayer : Draw.z() + heatLayerOffset, originX, originY);
                    }
                }
                Draw.xscl *= sign;
            }
            Draw.z(z);
        }

        void rect(TextureRegion region, float x, float y, float rotation) {
            float w = region.width * region.scl() * Draw.xscl, h = region.height * region.scl() * Draw.yscl;
            Draw.rect(region, x, y, w, h, w / 2f + originX * Draw.xscl, h / 2f + originY * Draw.yscl, rotation);
        }
    }

    // ================================================================== đạn chí mạng

    /** Đạn có tỉ lệ chí mạng: nhân sát thương trực tiếp và sát thương nổ lan (bản rút gọn). */
    public static class CritBulletType extends BasicBulletType {
        public float critChance = 0.1f, critMultiplier = 2f;
        public Effect critEffect = Fx.none;
        public float critEffectChance = 0.35f;

        public CritBulletType(float speed, float damage, String sprite) {
            super(speed, damage, sprite);
            impact = true;
            ammoMultiplier = 1;
            shootEffect = Fx.shootBig;
            smokeEffect = Fx.shootBigSmoke;
            hitEffect = Fx.none;
            hitColor = Pal.lightOrange;
            trailLength = 10;
            status = mindustry.content.StatusEffects.none;
        }

        public CritBulletType(float speed, float damage) {
            this(speed, damage, "bullet");
        }

        public boolean isCrit(Bullet b) {
            return b.data instanceof String;
        }

        @Override
        public void init(Bullet b) {
            if (b.data == null && Mathf.chance(critChance)) {
                b.data = "crit";
            }
            if (isCrit(b)) b.damage *= critMultiplier;
            super.init(b);
        }

        @Override
        public void update(Bullet b) {
            super.update(b);
            if (isCrit(b) && Mathf.chanceDelta(critEffectChance)) {
                critEffect.at(b.x, b.y, b.rotation(), hitColor);
            }
        }

        @Override
        public void createSplashDamage(Bullet b, float x, float y) {
            if (isCrit(b)) {
                float base = splashDamage;
                splashDamage = base * critMultiplier;
                try {
                    super.createSplashDamage(b, x, y);
                } finally {
                    splashDamage = base;
                }
            } else {
                super.createSplashDamage(b, x, y);
            }
        }
    }
}
