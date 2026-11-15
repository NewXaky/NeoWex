package newex.content;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Icon;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import newex.NexNet;
import newex.PerfCore;

import java.util.ArrayList;
import java.util.HashMap;

/** Chuyển từ flazerd.js. Laser bám mục tiêu, tích điểm sạc 0-200%, MK2 có 2 laser sóng, MK2B có tới 10 laser phụ. */
public final class Flazerd {
    private Flazerd() {}

    static final float deadZone = 40f;
    static final Color laserColor = Color.valueOf("bf7fff");
    static final Color particleColor = Color.valueOf("e8bfff");
    static final Color greenLaserColor = Color.valueOf("33ff55");

    static final class Req { final int copper, graphite, lead, silicon; Req(int c, int g, int l, int s) { copper = c; graphite = g; lead = l; silicon = s; } }
    static final Req reqMK2 = new Req(400, 400, 0, 0);
    static final Req reqMK3 = new Req(0, 0, 450, 570);

    static final class Sub { float x, y; Sub(float x, float y) { this.x = x; this.y = y; } }
    /** Gộp 5 ObjectMap của JS (tier/charge/milestone/countdown/subTargets) thành 1 trạng thái theo id pháo. */
    static final class State { int tier = 1; float charge = 0f; int countdown = -1; final ArrayList<Sub> subs = new ArrayList<>(); }
    static final HashMap<Integer, State> states = new HashMap<>();
    static State state(int id) { State s = states.get(id); if (s == null) { s = new State(); states.put(id, s); } return s; }

    static TextureRegion bodyRegion, downxRegion, a1Region, a2Region;
    public static PowerTurret flazerd;
    static LaserBullet laserBullet;

    /** Hàm ngôn ngữ riêng của flazerd.js (bài học 8): "không phải en". */
    static boolean isVietnamese() {
        String lang = "";
        try { lang = String.valueOf(Core.settings.getString("locale", "")); } catch (Exception err) { lang = ""; }
        if (lang.equals("") || lang.equals("null") || lang.equals("undefined") || lang.equals("default")) {
            try { lang = String.valueOf(Core.bundle.getLocale().getLanguage()); } catch (Exception err) { lang = ""; }
        }
        if (lang.equals("null") || lang.equals("undefined")) lang = "";
        return !lang.toLowerCase().startsWith("en");
    }

    static void dashCircle(float x, float y, float radius, Color color) {
        Draw.color(color);
        int segments = 48;
        for (int i = 0; i < segments; i += 2) {
            float a1 = (float) i / segments * 360f;
            float a2 = (float) (i + 1) / segments * 360f;
            Lines.line(x + Angles.trnsx(a1, radius), y + Angles.trnsy(a1, radius), x + Angles.trnsx(a2, radius), y + Angles.trnsy(a2, radius));
        }
        Draw.reset();
    }

    static void drawLaserRing(float cx, float cy, float radiusX, float radiusY, float laserAngle, float strokeWidth, Color color) {
        Draw.color(color); Lines.stroke(strokeWidth);
        int steps = 16; float lastX = 0f, lastY = 0f;
        float cosA = (float) Math.cos(laserAngle * Mathf.degRad), sinA = (float) Math.sin(laserAngle * Mathf.degRad);
        for (int i = 0; i <= steps; i++) {
            float angle = (i * (360f / steps)) * Mathf.degRad;
            float lx = (float) Math.cos(angle) * radiusX, ly = (float) Math.sin(angle) * radiusY;
            float rx = cx + (lx * cosA - ly * sinA), ry = cy + (lx * sinA + ly * cosA);
            if (i > 0) Lines.line(lastX, lastY, rx, ry);
            lastX = rx; lastY = ry;
        }
    }

    static void drawCurvedSubLaser(float startX, float startY, float endX, float endY, float laserAngle, float thickness, float sideSign) {
        float dist = Mathf.dst(startX, startY, endX, endY);
        int steps = (int) Math.floor(dist / 8f);
        if (steps < 2) return;
        float lastX = startX, lastY = startY;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float baseX = Mathf.lerp(startX, endX, t);
            float fillY = Mathf.lerp(startY, endY, t);
            float wave = (float) (Math.sin(t * Math.PI * 4 - Time.time * 0.25) * 8.0 * Math.sin(t * Math.PI)) * sideSign;
            float curX = baseX + Angles.trnsx(laserAngle + 90f, wave);
            float curY = fillY + Angles.trnsy(laserAngle + 90f, wave);
            Lines.stroke(thickness);
            Lines.line(lastX, lastY, curX, curY);
            lastX = curX; lastY = curY;
        }
    }

    static boolean isValidTarget(Unit u, float cx, float cy, float range) {
        return u != null && !u.dead() && u.isAdded() && !u.isFlying() && Mathf.dst(cx, cy, u.x, u.y) <= range && Mathf.dst(cx, cy, u.x, u.y) >= deadZone;
    }

    static final class LaserData { int mainTargetId = -1; float lastX, lastY; }

    static final class LaserBullet extends BulletType {
        LaserBullet() {
            speed = 0f; damage = 33f; lifetime = 12f; collides = false;
        }

        @Override
        public void init(Bullet b) {
            if (b == null || !(b.owner instanceof Building)) return;
            Building o = (Building) b.owner;
            LaserData d = new LaserData();
            d.lastX = o.x + Angles.trnsx(b.rotation(), 260f);
            d.lastY = o.y + Angles.trnsy(b.rotation(), 260f);
            b.data = d;
        }

        @Override
        public void update(Bullet b) {
            if (b == null || !(b.owner instanceof Building) || !(b.data instanceof LaserData)) return;
            Building o = (Building) b.owner;
            LaserData data = (LaserData) b.data;
            final float range = 260f, centerX = o.x, centerY = o.y;

            State st = state(o.id);
            int tier = st.tier;
            float currentPoints = st.charge;
            int countdown = st.countdown;

            float baseDamage = 33f;
            if (tier == 2) baseDamage = 33f * 3.5f;
            else if (tier == 3) baseDamage = 33f * 6.0f;
            float currentTickDamage = baseDamage * (1f + currentPoints * 0.5f);

            Teamc mainTarget = null;
            if (data.mainTargetId != -1) {
                Unit potential = Groups.unit.getByID(data.mainTargetId);
                if (isValidTarget(potential, centerX, centerY, range)) mainTarget = potential;
            }
            if (mainTarget == null) {
                // JS: closestTarget(unitPred, tilePred = true): có thể trả về Building (bài học 5).
                mainTarget = Units.closestTarget(b.team, centerX, centerY, range, u -> isValidTarget(u, centerX, centerY, range), t -> true);
                data.mainTargetId = mainTarget != null ? mainTarget.id() : -1;
            }

            if (mainTarget != null) {
                final Teamc mt = mainTarget;
                data.lastX = mt.getX();
                data.lastY = mt.getY();
                if (mt instanceof Healthc) ((Healthc) mt).damage(currentTickDamage / 60f);

                if (currentPoints >= 200f) {
                    if (countdown == -1) {
                        st.countdown = 300;
                    } else if (countdown > 0) {
                        countdown--;
                        st.countdown = countdown;
                    } else if (countdown == 0) {
                        currentPoints = 0f;
                        st.charge = 0f;
                        st.countdown = -1;
                        st.subs.clear();
                    }
                } else {
                    float chargeSpeedMultiplier = (float) (1 + Math.pow(currentPoints / 100f, 2));
                    float speedBonus = tier == 2 ? 1.5f : 1.0f;
                    currentPoints += (1f / 60f) * chargeSpeedMultiplier * speedBonus;
                    if (currentPoints >= 200f) {
                        currentPoints = 200f;
                        st.countdown = 300;
                    }
                    st.charge = currentPoints;
                }

                if (tier == 3) {
                    st.subs.clear();
                    int activeMilestones = 0;
                    for (int k = 1; k <= 10; k++) if (currentPoints >= k * 20) activeMilestones++;

                    if (activeMilestones > 0) {
                        final ArrayList<Unit> pool = new ArrayList<>();
                        Units.nearbyEnemies(b.team, centerX - range, centerY - range, range * 2f, range * 2f, u -> {
                            if (isValidTarget(u, centerX, centerY, range) && u.id() != mt.id()) pool.add(u);
                        });
                        if (pool.size() > 0) {
                            pool.sort((o1, o2) -> {
                                float d1 = Mathf.dst(mt.getX(), mt.getY(), o1.x, o1.y);
                                float d2 = Mathf.dst(mt.getX(), mt.getY(), o2.x, o2.y);
                                return d1 < d2 ? -1 : (d1 > d2 ? 1 : 0);
                            });
                            float subDamageTick = (currentTickDamage * 0.5f) / 60f;
                            int countToShoot = Math.min(activeMilestones, pool.size());
                            for (int i = 0; i < countToShoot; i++) {
                                Unit chosen = pool.get(i);
                                st.subs.add(new Sub(chosen.x, chosen.y));
                                chosen.damage(subDamageTick);
                                if (Mathf.chance(0.15)) Fx.hitLancer.at(chosen.x, chosen.y, greenLaserColor);
                            }
                        }
                    }
                }
                if (Mathf.chance(0.25)) Fx.hitLancer.at(mt.getX(), mt.getY(), laserColor);
            } else {
                data.mainTargetId = -1;
                st.subs.clear();
                data.lastX = centerX + Angles.trnsx(b.rotation(), range);
                data.lastY = centerY + Angles.trnsy(b.rotation(), range);
                st.countdown = -1;
                if (currentPoints > 0f) {
                    // JS: (3 / 60) là chia số thực = 0.05 mỗi tick.
                    currentPoints -= 3f / 60f;
                    if (currentPoints < 0f) currentPoints = 0f;
                    st.charge = currentPoints;
                }
            }
        }

        @Override
        public void draw(Bullet b) {
            if (b == null || !(b.data instanceof LaserData) || !(b.owner instanceof Building)) return;
            Building o = (Building) b.owner;
            LaserData data = (LaserData) b.data;
            State st = states.get(o.id);
            int tier = st != null ? st.tier : 1;
            float startX = o.x, startY = o.y, endX = data.lastX, endY = data.lastY;
            float dist = Mathf.dst(startX, startY, endX, endY);
            float laserAngle = Angles.angle(startX, startY, endX, endY);
            float currentPoints = st != null ? st.charge : 0f;
            int countdown = st != null ? st.countdown : -1;

            float thicknessScale = 1.0f + (currentPoints * 0.02f * 0.5f);

            Draw.color(laserColor); Lines.stroke(3.6f * thicknessScale); Lines.line(startX, startY, endX, endY);
            Draw.color(Color.white); Lines.stroke(1.2f * thicknessScale); Lines.line(startX, startY, endX, endY);

            if (tier == 2 && currentPoints > 2f) {
                Draw.color(particleColor);
                float subThickness = 0.8f * thicknessScale;
                drawCurvedSubLaser(startX, startY, endX, endY, laserAngle, subThickness, 1f);
                drawCurvedSubLaser(startX, startY, endX, endY, laserAngle, subThickness, -1f);
            }

            if (tier == 3 && st != null) {
                for (int m = 0; m < st.subs.size(); m++) {
                    Sub node = st.subs.get(m);
                    Draw.color(greenLaserColor);
                    Lines.stroke(1.8f * thicknessScale);
                    Lines.line(endX, endY, node.x, node.y);
                    Draw.color(Color.white);
                    Lines.stroke(0.5f * thicknessScale);
                    Lines.line(endX, endY, node.x, node.y);
                }
            }

            if (currentPoints > 2f) {
                float scaleFactor = 1f + (currentPoints / 240f);
                float distortionFactor = Math.max(0.05f, 1.0f - (currentPoints / 200f));
                int ringCount = 3;
                for (int r = 0; r < ringCount; r++) {
                    float ringProgress = ((Time.time * 0.8f) + (r * (dist / ringCount))) % dist;
                    float rt = ringProgress / dist;
                    float rx = Mathf.lerp(startX, endX, rt), ry = Mathf.lerp(startY, endY, rt);
                    float rxSize = 1.5f * scaleFactor;
                    float rySize = 4.0f * scaleFactor * distortionFactor;
                    float strokeWidth = 0.4f * Mathf.sin(rt * Mathf.PI) * thicknessScale;
                    drawLaserRing(rx, ry, rxSize, rySize, laserAngle, strokeWidth, laserColor);
                }
            }

            if (currentPoints > 0f) {
                Draw.z(Layer.effect + 10f);
                Draw.color(Color.white);
                Font font = Fonts.outline;
                font.getData().setScale(0.12f);
                String displayString = (int) Math.floor(currentPoints) + "%";
                if (countdown > 0) {
                    int secs = (int) Math.ceil(countdown / 60.0);
                    displayString = ((int) Math.floor(Time.time / 6f) % 2 == 0) ? "[scarlet]" + secs + "s[]" : "[white]" + secs + "s[]";
                }
                font.draw(displayString, o.x, o.y, Align.center);
                font.getData().setScale(1f);
            }
            Draw.reset();
        }
    }

    public static void create() {
        laserBullet = new LaserBullet();
        flazerd = new PowerTurret("flazerd") {
            @Override
            public void load() {
                super.load();
                bodyRegion = PerfCore.region(name + "-body");
                downxRegion = PerfCore.region(name + "-downx");
                a1Region = PerfCore.region(name + "-a1");
                a2Region = PerfCore.region(name + "-a2");
            }

            @Override
            public void drawPlace(int x, int y, int rotation, boolean valid) {
                super.drawPlace(x, y, rotation, valid);
                Draw.color(Pal.remove); Lines.stroke(2f);
                dashCircle(x * Vars.tilesize, y * Vars.tilesize, deadZone, Pal.remove);
                Draw.reset();
            }
        };
        flazerd.health = 3750; flazerd.size = 4;
        flazerd.targetAir = false; flazerd.targetGround = true;
        flazerd.range = 260f; flazerd.reload = 10f;
        flazerd.shootType = laserBullet;
        flazerd.recoil = 0f;
        flazerd.consumePower(22f);
        flazerd.configurable = true;

        flazerd.config(Integer.class, (Building tile, Integer value) -> {
            if (tile == null || value == null) return;
            state(tile.id).tier = value;
            if (value == 1) tile.health = 3750;
            else if (value == 2) tile.health = 5707;
            else if (value == 3) tile.health = 6848;
        });
        flazerd.buildType = () -> new FlazerdBuild(flazerd);
    }

    public static class FlazerdBuild extends PowerTurret.PowerTurretBuild {
        float wingsOffset = 0f;

        public FlazerdBuild(PowerTurret outer) { outer.super(); }

        /** Lesson 6: canControl là phương thức của ControlBlock. */
        @Override
        public boolean canControl() { return false; }

        @Override
        public void placed() {
            super.placed();
            wingsOffset = 0f;
        }

        /** JS override `destroyed()` không tồn tại ở v157 nên chưa từng chạy (bài học 15); dùng onRemoved() để dọn trạng thái. */
        @Override
        public void onRemoved() {
            super.onRemoved();
            states.remove(id);
        }

        @Override
        public void updateTile() {
            super.updateTile();
            boolean hasTarget = Units.closestTarget(team, x, y, 260f, u -> !u.dead() && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone, bb -> true) != null;
            wingsOffset = hasTarget ? Mathf.lerpDelta(wingsOffset, 1f, 0.15f) : Mathf.lerpDelta(wingsOffset, 0f, 0.1f);
        }

        @Override
        public void draw() {
            // JS: PerfCore.region(flazerd.basePrefix + ...) = "undefinedblock-4" không bao giờ thấy → luôn super.draw() (bài học 13).
            super.draw();

            float rad = rotation * Mathf.degRad;
            float cos = (float) Math.cos(rad), sin = (float) Math.sin(rad);

            if (bodyRegion != null && bodyRegion.found()) Draw.rect(bodyRegion, x, y, rotation);
            if (downxRegion != null && downxRegion.found()) Draw.rect(downxRegion, x, y, rotation);

            float wingBackOffset = -4f * wingsOffset;
            float combatSideMove = 4f * wingsOffset;

            if (a1Region != null && a1Region.found()) {
                Draw.rect(a1Region, x + (wingBackOffset * cos) - (combatSideMove * sin), y + (wingBackOffset * sin) + (combatSideMove * cos), rotation);
            }
            if (a2Region != null && a2Region.found()) {
                Draw.rect(a2Region, x + (wingBackOffset * cos) + (combatSideMove * sin), y + (wingBackOffset * sin) - (combatSideMove * cos), rotation);
            }
            Draw.reset();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.clear(); table.row();
            final boolean vi = isVietnamese();
            if (state(id).tier == 1) {
                table.button(Icon.upOpen, Styles.cleari, 40, () -> openUpgrade(vi))
                    .size(50, 40).tooltip(vi ? "Tiến hóa pháo Flazerd" : "Evolve Flazerd turret");
            } else {
                table.button(Icon.lock, Styles.cleari, 40, () -> Vars.ui.showInfo(vi
                    ? "[scarlet]HỆ THỐNG FLAZERD ĐÃ ĐẠT GIỚI HẠN CẤU HÌNH TIẾN HÓA![]"
                    : "[scarlet]FLAZERD SYSTEM HAS REACHED MAXIMUM EVOLUTION LIMIT![]"))
                    .size(50, 40).tooltip(vi ? "Đã đạt cấp tối đa" : "Max level reached");
            }
            table.button(Icon.info, Styles.cleari, 40, () -> openInfo(vi))
                .size(50, 40).tooltip(vi ? "Xem thông số chi tiết hệ thống" : "View system detailed specs");
        }

        private String requirementText(boolean vi) {
            CoreBuild core = team.core();
            if (core == null) return vi ? "[red]Không tìm thấy Lõi Đội![]" : "[red]Team Core not found![]";
            int currentcopper = core.items.get(Items.copper);
            int currentgraphite = core.items.get(Items.graphite);
            int currentlead = core.items.get(Items.lead);
            int currentsilicon = core.items.get(Items.silicon);
            String copColor = currentcopper >= reqMK2.copper ? "[green]" : "[red]";
            String graColor = currentgraphite >= reqMK2.graphite ? "[green]" : "[red]";
            String leaColor = currentlead >= reqMK3.lead ? "[green]" : "[red]";
            String silColor = currentsilicon >= reqMK3.silicon ? "[green]" : "[red]";
            if (vi) {
                        return "[yellow]YÊU CẦU TÀI NGUYÊN KHO LÕI:[]\n" +
                               "[cyan]Nhánh MK2:[]\n" +
                               " • Đồng: " + copColor + currentcopper + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graColor + currentgraphite + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]Nhánh MK2B:[]\n" +
                               " • Chì: " + leaColor + currentlead + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + silColor + currentsilicon + "[] / " + reqMK3.silicon;
            } else {
                        return "[yellow]CORE VAULT RESOURCE REQUIREMENTS:[]\n" +
                               "[cyan]MK2 Path:[]\n" +
                               " • Copper: " + copColor + currentcopper + "[] / " + reqMK2.copper + "\n" +
                               " • Graphite: " + graColor + currentgraphite + "[] / " + reqMK2.graphite + "\n" +
                               "[purple]MK2B Path:[]\n" +
                               " • Lead: " + leaColor + currentlead + "[] / " + reqMK3.lead + "\n" +
                               " • Silicon: " + silColor + currentsilicon + "[] / " + reqMK3.silicon;
            }
        }

        private void openUpgrade(boolean vi) {
            BaseDialog dialog = new BaseDialog(vi ? "Trung tâm nâng cấp pháo Flazerd" : "Flazerd Turret Upgrade Center");
            Cell<Label> reqCell = dialog.cont.add(requirementText(vi));
            reqCell.width(360f); reqCell.get().setWrap(true); reqCell.get().setAlignment(Align.left);
            dialog.cont.row(); dialog.cont.add().height(10).row();

            Table branchesTable = new Table();

            Table b1 = new Table(); b1.background(Styles.black6); b1.margin(12f);
            b1.add("[cyan]===(MK2)===[]").row();
                String b1Text = vi ? "Mô-đun mạch xung hỏa lực bứt tốc:\n" +
                                   " [white]• Tốc độ sạc tụ gia tốc vượt bậc đạt ngưỡng [yellow]150%[].[]\n" +
                                   " [white]• Tăng tiến [red]+50% Sát thương[] tổng ứng với mỗi 1% năng lượng sạc.[]\n" +
                                   " [white]• Gia cố [green]+5,707 Máu[] và mở rộng tầm phát xạ lên [green]276.2 pixel[].[]\n" +
                                   " [white]• Kích hoạt cặp laze sóng uốn cong đối xứng dọc trục chính."
                                : "Accelerated Pulse Fire Circuit Module:\n" +
                                   " [white]• Capacitor charging speed boosted up to [yellow]150%[].[]\n" +
                                   " [white]• Gains [red]+50% Total Damage[] per 1% stored charge.[]\n" +
                                   " [white]• Reinforced to [green]+5,707 Health[] and range expanded to [green]276.2 pixels[].[]\n" +
                                   " [white]• Activates dual curved sine-wave sub-lasers along the main axis.";
            Cell<Label> b1D = b1.add(b1Text);
            b1D.width(340f); b1D.get().setWrap(true); b1D.get().setAlignment(Align.left); b1.row();
            b1.button(vi ? "[green]KÍCH HOẠT MK2[]" : "[green]ACTIVATE MK2[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.copper) >= reqMK2.copper && core.items.get(Items.graphite) >= reqMK2.graphite) {
                    NexNet.take(core, Items.copper, reqMK2.copper); NexNet.take(core, Items.graphite, reqMK2.graphite);
                    NexNet.fx(Fx.upgradeCore, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5f, 5f, x, y);
                    NexNet.configure(this, Integer.valueOf(2)); dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfoToast(vi ? "[red]Không đủ tài nguyên cho nhánh MK2![]" : "[red]Not enough resources for MK2 path![]", 2f);
                }
            }).size(180, 38);

            Table b2 = new Table(); b2.background(Styles.black6); b2.margin(12f);
            b2.add("[purple]===(MK2B)===[]").row();
                String b2Text = vi ? "Lõi hội tụ đa chùm phổ hủy diệt tầng cao:\n" +
                                   " [white]• Siêu gia cố Máu đạt mốc cực đại [green]6,848 Máu[] (Tầm bắn [orange]260 pixel[]).[]\n" +
                                   " [white]• Tự động kích hoạt phóng thêm các tia laze phụ khi điểm sạc vượt các mốc [yellow]20 -> 200[].[]\n" +
                                   " [white]• Mở rộng tối đa lên tới 10 chùm tia quang phổ phân rã mục tiêu xung quanh.[]\n" +
                                   " [white]• Mỗi tia phụ gây lượng sát thương bằng [red]chính xác 50%[] của tia laze chính."
                                : "High-tier Spectrum Multi-beam Focus Core:\n" +
                                   " [white]• Super-reinforced Health reaching maximum [green]6,848 HP[] (Range [orange]260 pixels[]).[]\n" +
                                   " [white]• Automatically fires sub-lasers as charge surpasses thresholds [yellow]20 -> 200[].[]\n" +
                                   " [white]• Expands up to 10 spectral beams disintegrating nearby enemies.[]\n" +
                                   " [white]• Each sub-laser deals [red]exactly 50%[] DMG of the main laser.";
            Cell<Label> b2D = b2.add(b2Text);
            b2D.width(340f); b2D.get().setWrap(true); b2D.get().setAlignment(Align.left); b2.row();
            b2.button(vi ? "[orange]KÍCH HOẠT MK2B[]" : "[orange]ACTIVATE MK2B[]", () -> {
                CoreBuild core = team.core();
                if (core != null && core.items.get(Items.lead) >= reqMK3.lead && core.items.get(Items.silicon) >= reqMK3.silicon) {
                    NexNet.take(core, Items.lead, reqMK3.lead); NexNet.take(core, Items.silicon, reqMK3.silicon);
                    NexNet.fx(Fx.bigShockwave, x, y); NexNet.fx(Fx.mineHuge, x, y); NexNet.shake(5f, 5f, x, y);
                    NexNet.configure(this, Integer.valueOf(3)); dialog.hide(); deselect();
                } else {
                    Vars.ui.showInfoToast(vi ? "[red]Không đủ tài nguyên cho nhánh MK2B![]" : "[red]Not enough resources for MK2B path![]", 2f);
                }
            }).size(180, 38);

            branchesTable.add(b1).width(340f); branchesTable.row();
            branchesTable.add().height(12).row();
            branchesTable.add(b2).width(340f);

            ScrollPane scroll = new ScrollPane(branchesTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        private void openInfo(boolean vi) {
            String title = vi ? " Thông số pháo Flazerd: " : " Flazerd Turret Specs: ";
            String descStr = "";
            State st = states.get(id);
            int currentTier = st != null ? st.tier : 1;
            if (currentTier == 1) {
                title += "[yellow](MK1)[]";
                descStr = vi ? "[gold]⚡ THÔNG SỐ CƠ BẢN (MK1) ⚡[]\n" +
                               "[lightgray]Máu tháp pháo:[] [green]3,750[]\n" +
                               "Tầm bắn hiệu dụng:[] [orange]260 pixel[]\n" +
                               "Sát thương liên tục:[] [white]33.00 hỏa lực/s[]\n\n" +
                               "[sky]⚡ CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH:[]\n" +
                               "• [lightgray]Phát xạ mục tiêu:[] Phóng chùm tia laze đơn liên tục bám khóa chặt kẻ địch mặt đất.\n" +
                               "• [lightgray]Màn hình Holyder:[] Giao diện kỹ thuật số hiển thị thời gian thực số % sạc tụ lõi tâm.\n" +
                               "• [lightgray]Tích tụ năng lượng:[] Giới hạn sạc mở rộng lên [yellow]200%[] giúp tăng tiến dần sát thương tổng."
                             : "[gold]⚡ BASE SPECS (MK1) ⚡[]\n" +
                               "[lightgray]Turret Health:[] [green]3,750[]\n" +
                               "Effective Range:[] [orange]260 pixels[]\n" +
                               "Continuous Damage:[] [white]33.00 DPS[]\n\n" +
                               "[sky]⚡ THERMAL CIRCUIT MECHANICS:[]\n" +
                               "• [lightgray]Target Emission:[] Fires a continuous single laser beam locking onto ground targets.\n" +
                               "• [lightgray]Holyder Display:[] Digital UI displaying real-time core charge %.\n" +
                               "• [lightgray]Energy Accumulation:[] Charge limit expanded up to [yellow]200%[] to continuously scale total damage.";
            } else if (currentTier == 2) {
                title += "[cyan](MK2)[]";
                descStr = vi ? "[cyan]⚡ THÔNG SỐ CƠ BẢN (MK2) ⚡[]\n" +
                               "[lightgray]Máu tháp pháo:[] [green]5,707 [lime](+52%)[]\n" +
                               "Tầm bắn hiệu dụng:[] [orange]276.2 pixel [lime](+6.25%)[]\n" +
                               "Sát thương cơ bản:[] [white]115.50 hỏa lực/s[]\n\n" +
                               "[lime]⚡ CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH:[]\n" +
                               "• [lightgray]Mạch hỏa tốc:[] Tốc độ sạc tụ năng lượng gia tốc vượt bậc đạt ngưỡng đỉnh [yellow]150%[].\n" +
                               "• [lightgray]Xung lực hỏa lực:[] Cứ mỗi 1% điểm sạc tích lũy cộng trực tiếp [red]+50% sát thương[] tổng.\n" +
                               "• [lightgray]Laser Helix:[] Kích hoạt thêm 2 tia sóng phụ uốn lượn hình sin bám dọc đối xứng hai bên trục chính.\n" +
                               "• [lightgray]Xả quá tải an toàn:[] Tự động đếm ngược và reset điểm về 0 sau khi duy trì đỉnh năng lượng."
                             : "[cyan]⚡ BASE SPECS (MK2) ⚡[]\n" +
                               "[lightgray]Turret Health:[] [green]5,707 [lime](+52%)[]\n" +
                               "Effective Range:[] [orange]276.2 pixels [lime](+6.25%)[]\n" +
                               "Base Damage:[] [white]115.50 DPS[]\n\n" +
                               "[lime]⚡ THERMAL CIRCUIT MECHANICS:[]\n" +
                               "• [lightgray]Rapid Fire Circuit:[] Capacitor charging speed greatly accelerated up to [yellow]150%[].\n" +
                               "• [lightgray]Firepower Impulse:[] Adds [red]+50% total damage[] directly per 1% stored charge.\n" +
                               "• [lightgray]Laser Helix:[] Activates 2 sub-wave lasers curving symmetrically along the main axis.\n" +
                               "• [lightgray]Safe Overload Discharge:[] Automatically counts down and resets charge to 0 after holding max energy.";
            } else if (currentTier == 3) {
                title += "[purple](MK2B)[]";
                descStr = vi ? "[purple]⚡ THÔNG SỐ CƠ BẢN (MK2B) ⚡[]\n" +
                               "[lightgray]Máu tháp pháo:[] [green]6,848 [lime](+82.6%)[]\n" +
                               "Tầm bắn hiệu dụng:[] [orange]260 pixel[]\n" +
                               "Sát thương cơ bản:[] [white]198.00 hỏa lực/s[]\n\n" +
                               "[purple]🔥 CƠ CHẾ HOẠT ĐỘNG NHIỆT MẠCH:[]\n" +
                               "• [lightgray]Phân chùm đa mốc:[] Tách thêm tia laze phụ xanh lá bắn phá mục tiêu xung quanh khi điểm sạc vượt qua các mốc [yellow]20, 40, 60, 80, 100, 120, 140, 160, 180, 200[].\n" +
                               "• [lightgray]Hỏa lực rẽ nhánh:[] Mỗi tia phụ rẽ nhánh tự động gây sát thương bằng [red]chính xác 50%[] hỏa lực tia chính.\n" +
                               "• [lightgray]Xả tụ thông minh:[] Tự động thiết lập lại điểm năng lượng về 0 sau chu kỳ bắn để chuẩn bị cho loạt xung năng mới."
                             : "[purple]⚡ BASE SPECS (MK2B) ⚡[]\n" +
                               "[lightgray]Turret Health:[] [green]6,848 [lime](+82.6%)[]\n" +
                               "Effective Range:[] [orange]260 pixels[]\n" +
                               "Base Damage:[] [white]198.00 DPS[]\n\n" +
                               "[purple]🔥 THERMAL CIRCUIT MECHANICS:[]\n" +
                               "• [lightgray]Milestone Multi-beam:[] Splits extra green sub-lasers hitting nearby enemies when charge crosses [yellow]20, 40, 60, 80, 100, 120, 140, 160, 180, 200[].\n" +
                               "• [lightgray]Branched Firepower:[] Each sub-laser automatically deals [red]exactly 50%[] damage of the primary beam.\n" +
                               "• [lightgray]Smart Discharge:[] Resets energy charge to 0 after cycle completion to prepare for the next power burst.";
            }
            BaseDialog dialog = new BaseDialog(title);
            Table infoTable = new Table();
            Cell<Label> cell = infoTable.add(descStr).width(360f);
            cell.get().setWrap(true); cell.get().setAlignment(Align.left);
            ScrollPane scroll = new ScrollPane(infoTable);
            scroll.setScrollingDisabled(true, false);
            dialog.cont.add(scroll).maxHeight(400f);
            dialog.addCloseButton(); dialog.show();
        }

        @Override
        protected void findTarget() {
            target = Units.closestTarget(team, x, y, 260f, u -> !u.dead() && !u.type.flying && Mathf.dst(x, y, u.x, u.y) >= deadZone, bb -> true);
        }

        @Override
        public void drawSelect() {
            super.drawSelect();
            dashCircle(x, y, deadZone, Pal.remove);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            State st = states.get(id);
            write.b(st != null ? st.tier : 1);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            state(id).tier = read.b();
        }
    }
}
