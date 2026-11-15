package newex.content;

import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Intersector;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.World;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.EventType.BlockBuildEndEvent;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Posc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;
import newex.PerfCore;

/** Chuyển từ repulsyron.js: laser tia liên tục, giới hạn số tháp = số lõi. */
public final class Repulsyron {
    private static final Color C_BLUE = Color.valueOf("#00c8ff"), C_RED = Color.valueOf("#ff0055"),
        C_PINK = Color.valueOf("#ff99bb"), C_HEAT = Color.valueOf("#ffbb88"), C_CHARGE = Color.valueOf("#00e1ff");

    private static Effect unibeamHit, overloadHit, smoke, secondaryLaser;
    private static Block block;
    private static TextureRegion wing1, wing2;

    private Repulsyron() {}

    private static void buildFx() {
        unibeamHit = new Effect(14, e -> {
            Draw.color(Color.white, C_BLUE, e.fin());
            Lines.stroke(e.fout() * 2.2f);
            Lines.circle(e.x, e.y, e.fin() * 14f);
            Angles.randLenVectors(e.id, 5, e.fin() * 16f, (x, y) ->
                Lines.lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), e.fout() * 5f));
        });
        overloadHit = new Effect(20, e -> {
            Draw.color(C_RED, C_PINK, e.fin());
            Lines.stroke(e.fout() * 3f);
            Lines.circle(e.x, e.y, e.fin() * 26f);
            Angles.randLenVectors(e.id, 8, e.fin() * 30f, (x, y) -> Fill.circle(e.x + x, e.y + y, e.fout() * 3.5f));
        });
        smoke = new Effect(40, e -> {
            Draw.color(Color.gray, Color.darkGray, e.fout());
            Angles.randLenVectors(e.id, 2, e.fin() * 14f, e.rotation, 30f, (x, y) -> Fill.circle(e.x + x, e.y + y, e.fout() * 3.2f));
        });
        secondaryLaser = new Effect(12, e -> {
            if (!(e.data instanceof Vec2)) return;
            Vec2 t = (Vec2) e.data;
            Draw.color(C_RED, Color.white, e.fout());
            Lines.stroke(e.fout() * 2.5f);
            Lines.line(e.x, e.y, t.x, t.y);
            Fill.circle(t.x, t.y, e.fout() * 3f);
        });
    }

    private static Block findBlock() {
        Block b = Vars.content.block("newex-repulsyron");
        return b != null ? b : Vars.content.block("repulsyron");
    }

    /** Chỉ cho xây khi số tháp của đội < số lõi. */
    private static void updateVisibility() {
        if (Vars.player == null || block == null) return;
        Team team = Vars.player.team();
        int max = team.cores().size;
        block.buildVisibility = PerfCore.teamCount(team, block) < max ? BuildVisibility.shown : BuildVisibility.hidden;
    }

    public static void init() {
        buildFx();

        Events.on(ContentInitEvent.class, e -> {
            block = findBlock();
            if (!(block instanceof PowerTurret)) return;
            PowerTurret turret = (PowerTurret) block;
            turret.buildType = () -> new RepulsyronBuild(turret);
        });

        Events.on(WorldLoadEvent.class, e -> Time.run(10f, Repulsyron::updateVisibility));
        Events.on(BlockBuildEndEvent.class, e -> updateVisibility());

        Events.on(BlockDestroyEvent.class, e -> {
            if (block == null) return;
            Tile tile = e.tile;
            if (tile == null || tile.build == null) return;
            Building destroyed = tile.build;
            Team victim = destroyed.team;

            if (destroyed.block instanceof CoreBlock) {
                int max = Math.max(0, victim.data().cores.size - 1);
                Seq<Building> list = new Seq<>();
                Groups.build.each(b -> { if (b.block == block && b.team == victim) list.add(b); });
                int extra = list.size - max;
                for (int i = 0; i < extra; i++) {
                    Building last = list.pop();
                    Call.sendMessage("[red]Đội " + victim.name + " bị mất Lõi! Pháo Repulsyron thừa đã tự hủy![]");
                    last.kill();
                }
            }
            updateVisibility();
        });
    }

    public static class RepulsyronBuild extends PowerTurret.PowerTurretBuild {
        float beamProgress, damageTimer, firingTimer, fadeProgress, chargeTimer, cooldownTimer;
        boolean isCharging, isFiring, isFading;
        Posc lockedTarget;
        float startX, startY, endX, endY, fadeStartX, fadeStartY, fadeEndX, fadeEndY;
        float wingMoveProgress, wingHeatProgress;

        public RepulsyronBuild(PowerTurret outer) {
            outer.super();
        }

        private boolean validTarget(Posc t) {
            if (t == null) return false;
            if (t instanceof Healthc) {
                Healthc h = (Healthc) t;
                if (h.dead() || h.health() <= 0f) return false;
            }
            return t instanceof Position && within((Position) t, range());
        }

        private void stopFiringAndStartFade(float mx, float my) {
            if (isFiring || isCharging) {
                if (isFiring) cooldownTimer = 300f;
                isFiring = false;
                isCharging = false;
                isFading = true;
                fadeProgress = 0f;
                damageTimer = 0f;
                chargeTimer = 0f;
                fadeStartX = mx; fadeStartY = my;
                fadeEndX = endX; fadeEndY = endY;
            }
        }

        /** Gây sát thương nổ phụ cho kẻ địch quanh (cx, cy) bán kính 120, trừ {@code except}. */
        private void overloadSplash(float cx, float cy, Unit except) {
            Units.nearbyEnemies(team, cx - 120f, cy - 120f, 240f, 240f, sub -> {
                if (sub != null && sub != except && sub.within(cx, cy, 120f)) {
                    sub.damagePierce(5000f);
                    secondaryLaser.at(cx, cy, 0f, Color.white, new Vec2(sub.x, sub.y));
                }
            });
        }

        @Override
        public void updateTile() {
            super.updateTile();

            float mx = x + Angles.trnsx(rotation, block.size * 4f);
            float my = y + Angles.trnsy(rotation, block.size * 4f);

            if (cooldownTimer > 0f) {
                cooldownTimer -= Time.delta;
                if (Mathf.chance(0.25)) smoke.at(mx, my, rotation);
            }

            boolean controlled = isControlled();

            if (!controlled) {
                if (lockedTarget != null && validTarget(lockedTarget)) {
                    target = lockedTarget;
                } else if (lockedTarget != null) {
                    stopFiringAndStartFade(mx, my);
                    lockedTarget = null;
                }
            }

            if (isShooting() && efficiency > 0f && cooldownTimer <= 0f) {
                float tx = 0f, ty = 0f;

                if (controlled) {
                    if (!Float.isNaN(targetPos.x) && !Float.isNaN(targetPos.y)) {
                        tx = targetPos.x; ty = targetPos.y;
                    } else {
                        tx = mx + Angles.trnsx(rotation, range());
                        ty = my + Angles.trnsy(rotation, range());
                    }
                } else {
                    Posc cur = target;
                    if (cur != null && validTarget(cur)) {
                        if (lockedTarget != cur) {
                            lockedTarget = cur;
                            beamProgress = 0f; firingTimer = 0f; damageTimer = 0f; chargeTimer = 0f;
                        }
                        tx = cur.getX();
                        ty = cur.getY();
                    }
                }

                if (controlled || lockedTarget != null) {
                    if (chargeTimer < 30f) {
                        isCharging = true;
                        isFiring = false;
                        chargeTimer += Time.delta;
                    } else {
                        isCharging = false;
                        isFiring = true;
                        isFading = false;
                        firingTimer += Time.delta;

                        if (beamProgress < 1f) beamProgress = Math.min(1f, beamProgress + Time.delta / 12f);

                        startX = mx; startY = my;
                        endX = Mathf.lerp(startX, tx, beamProgress);
                        endY = Mathf.lerp(startY, ty, beamProgress);

                        damageTimer += Time.delta;
                        if (damageTimer >= 6f) {
                            damageTimer = 0f;
                            final float sf = firingTimer / 60f;
                            final float dmg = 20f * efficiency * (1f + sf * 10f);
                            final float width = 12f;
                            final float sx = startX, sy = startY, ex = endX, ey = endY;

                            Units.nearbyEnemies(team, Math.min(sx, ex) - 30f, Math.min(sy, ey) - 30f,
                                Math.abs(ex - sx) + 60f, Math.abs(ey - sy) + 60f, unit -> {
                                    if (unit != null && Intersector.distanceSegmentPoint(sx, sy, ex, ey, unit.x, unit.y) <= width + unit.hitSize / 2f) {
                                        unit.damage(dmg);
                                        if (sf >= 5f) {
                                            overloadHit.at(unit.x, unit.y);
                                            overloadSplash(unit.x, unit.y, unit);
                                        } else {
                                            unibeamHit.at(unit.x, unit.y);
                                        }
                                    }
                                });

                            World.raycast(World.toTile(sx), World.toTile(sy), World.toTile(ex), World.toTile(ey), (wx, wy) -> {
                                Tile tile = Vars.world.tile(wx, wy);
                                if (tile != null && tile.build != null && tile.build.team != team) {
                                    Building b = tile.build;
                                    b.damage(team, dmg);
                                    if (sf >= 5f) {
                                        overloadHit.at(b.x, b.y);
                                        overloadSplash(b.x, b.y, null);
                                    }
                                }
                                return false;
                            });

                            if (sf >= 5f) overloadHit.at(ex, ey); else unibeamHit.at(ex, ey);
                        }
                    }
                } else {
                    stopFiringAndStartFade(mx, my);
                }
            } else {
                stopFiringAndStartFade(mx, my);
            }

            float sec = firingTimer / 60f;
            float targetMove = (isFiring || isCharging) ? 1f : 0f;
            float targetHeat = (isFiring && sec >= 5f) ? 1f : 0f;

            float moveSpeed = targetMove < wingMoveProgress ? Time.delta / 60f : Time.delta / 30f;
            wingMoveProgress = Mathf.lerpDelta(wingMoveProgress, targetMove, moveSpeed);
            wingHeatProgress = Mathf.lerpDelta(wingHeatProgress, targetHeat, Time.delta / 40f);

            if (isFading) {
                fadeProgress += Time.delta / 12f;
                startX = Mathf.lerp(fadeStartX, fadeEndX, fadeProgress);
                startY = Mathf.lerp(fadeStartY, fadeEndY, fadeProgress);
                endX = fadeEndX;
                endY = fadeEndY;
                if (fadeProgress >= 1f) {
                    isFading = false;
                    beamProgress = 0f; fadeProgress = 0f; firingTimer = 0f;
                }
            }
        }

        @Override
        public void draw() {
            super.draw();

            float mx = x + Angles.trnsx(rotation, block.size * 4f);
            float my = y + Angles.trnsy(rotation, block.size * 4f);

            if (wing1 == null || !wing1.found()) wing1 = PerfCore.region("newex-repulsyron-wing1", "repulsyron-wing1");
            if (wing2 == null || !wing2.found()) wing2 = PerfCore.region("newex-repulsyron-wing2", "repulsyron-wing2");

            if (wing1 != null && wing1.found() && wing2 != null && wing2.found()) {
                Draw.z(Layer.turret + 0.05f);
                float d = 6f * wingMoveProgress;
                float w1x = x + Angles.trnsx(rotation - 90f, d), w1y = y + Angles.trnsy(rotation - 90f, d);
                float w2x = x + Angles.trnsx(rotation + 90f, d), w2y = y + Angles.trnsy(rotation + 90f, d);
                Draw.rect(wing1, w1x, w1y, rotation);
                Draw.rect(wing2, w2x, w2y, rotation);

                if (wingHeatProgress > 0f) {
                    Draw.color(C_HEAT);
                    Draw.alpha(wingHeatProgress * 0.85f);
                    Draw.blend(Blending.additive);
                    Draw.rect(wing1, w1x, w1y, rotation);
                    Draw.rect(wing2, w2x, w2y, rotation);
                    Draw.blend();
                    Draw.reset();
                }
            }

            if (isCharging) {
                float r = Mathf.clamp(chargeTimer / 30f);
                Draw.z(Layer.bullet + 2f);
                Draw.color(C_CHARGE, Color.white, r);
                Fill.circle(mx, my, r * 5f);
                Lines.stroke((1f - r) * 3f);
                Lines.circle(mx, my, (1f - r) * 16f);
                Draw.reset();
            }

            if ((isFiring || isFading) && beamProgress > 0f) {
                Draw.z(Layer.bullet + 2f);

                float sec = firingTimer / 60f;
                float size = 1f + Math.min(1.8f, sec * 0.12f);
                float cf = Mathf.clamp((sec - 2f) / 8f);
                Color cOuter = Color.valueOf("#0077ff").cpy().lerp(Color.valueOf("#ff0055"), cf);
                Color cMid = Color.valueOf("#00e1ff").cpy().lerp(Color.valueOf("#ff66aa"), cf);
                float alpha = isFading ? 1f - fadeProgress : 1f;

                float outerW = 7.5f * size, midW = 4f * size, innerW = 1.8f * size;

                Draw.color(cOuter);
                Draw.alpha(alpha * 0.6f);
                Lines.stroke(outerW);
                Lines.line(startX, startY, endX, endY);
                Fill.circle(startX, startY, outerW * 0.5f);
                Fill.circle(endX, endY, outerW * 0.5f);

                float eff = Mathf.clamp(sec / 3f);
                if (eff > 0f && isFiring) {
                    float len = Mathf.len(endX - startX, endY - startY);
                    float angle = Mathf.angle(endX - startX, endY - startY);
                    int segments = (int) Math.floor(len / 8f);

                    Draw.color(cMid);
                    Draw.alpha(alpha * 0.85f * eff);
                    Lines.stroke(1.6f * size * eff);

                    for (int side = -1; side <= 1; side += 2) {
                        float px0 = startX, py0 = startY;
                        for (int i = 1; i <= segments; i++) {
                            float prog = i / (float) segments;
                            float px = Mathf.lerp(startX, endX, prog), py = Mathf.lerp(startY, endY, prog);
                            float wave = Mathf.sinDeg(Time.time * 14f + prog * 360f) * (7f * size * eff) * side;
                            float wx = px + Angles.trnsx(angle + 90f, wave), wy = py + Angles.trnsy(angle + 90f, wave);
                            Lines.line(px0, py0, wx, wy);
                            px0 = wx; py0 = wy;
                        }
                    }

                    Draw.color(Color.white);
                    int photons = (int) Math.floor(14f * eff);
                    for (int p = 0; p < photons; p++) {
                        float pt = (Time.time * 0.05f + p / 14f) % 1f;
                        float px = Mathf.lerp(startX, endX, pt), py = Mathf.lerp(startY, endY, pt);
                        float off = Mathf.sinDeg(Time.time * 20f + p * 50f) * (5f * size);
                        float fx = px + Angles.trnsx(angle + 90f, off), fy = py + Angles.trnsy(angle + 90f, off);
                        float rnd = 0.6f + ((p * 17) % 10) / 10f * 0.9f;
                        Draw.alpha(alpha * 0.9f * eff);
                        Fill.circle(fx, fy, 1.4f * size * rnd);
                    }
                }

                Draw.color(cMid);
                Draw.alpha(alpha * 0.85f);
                Lines.stroke(midW);
                Lines.line(startX, startY, endX, endY);
                Fill.circle(startX, startY, midW * 0.5f);
                Fill.circle(endX, endY, midW * 0.5f);

                Draw.color(Color.white);
                Draw.alpha(alpha);
                Lines.stroke(innerW);
                Lines.line(startX, startY, endX, endY);
                Fill.circle(startX, startY, innerW * 0.5f);
                Fill.circle(endX, endY, innerW * 0.5f);

                Draw.reset();
            }
        }
    }
}
