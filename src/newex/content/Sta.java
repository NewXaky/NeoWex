package newex.content;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.StatusEffect;
import newex.PerfCore;

import java.util.HashMap;
import java.util.Map;

/**
 * Chuyển từ sta.js. Các hiệu ứng trạng thái (StatusEffect) dùng chung của mod và các bảng trạng thái toàn cục
 * (JS: global.ceiStacks, global.bemodStacks...). Các pháo/đơn vị khác đọc ghi qua các Map tĩnh ở đây.
 */
public final class Sta {
    // ---- bảng trạng thái toàn cục (theo id đơn vị) ----
    public static final Map<Integer, Float> deotLastHealth = new HashMap<>();
    public static final Map<Integer, Float> deotDamagedTime = new HashMap<>();
    public static final Map<Integer, Integer> doteiStacks = new HashMap<>();
    public static final Map<Integer, Integer> bemodStacks = new HashMap<>();
    public static final Map<Integer, Integer> ceiStacks = new HashMap<>();
    public static final Map<Integer, Float> ceiTimers = new HashMap<>();
    /** Pháo cuối cùng gắn Cei lên đơn vị (JS: global.ceiLastAppliedTurret, do plasanod ghi). */
    public static final Map<Integer, Object> ceiLastAppliedTurret = new HashMap<>();

    @SuppressWarnings("unused")
    private static boolean isDoteiDamageActive = false;

    public static int stacks(Map<Integer, Integer> map, int id) {
        Integer v = map.get(id);
        return v != null ? v : 0;
    }

    // ---- hiệu ứng hình ảnh ----
    public static final Effect atraxShieldHitFx = new Effect(15f, e -> {
        Draw.z(Layer.effect + 0.2f);
        Draw.color(PerfCore.color("ff3333"), Color.white, e.fout());

        float hitAngle = (e.data instanceof Number) ? ((Number) e.data).floatValue() : 0f;
        float size = 16f + e.fout() * 6f;
        float offset = 12f;

        float cx = e.x + Angles.trnsx(hitAngle, offset);
        float cy = e.y + Angles.trnsy(hitAngle, offset);

        Fill.quad(
            cx + Angles.trnsx(hitAngle + 90f, size * 0.6f), cy + Angles.trnsy(hitAngle + 90f, size * 0.6f),
            cx + Angles.trnsx(hitAngle, size * 1.4f), cy + Angles.trnsy(hitAngle, size * 1.4f),
            cx + Angles.trnsx(hitAngle - 90f, size * 0.6f), cy + Angles.trnsy(hitAngle - 90f, size * 0.6f),
            cx + Angles.trnsx(hitAngle + 180f, size * 0.3f), cy + Angles.trnsy(hitAngle + 180f, size * 0.3f)
        );

        Draw.reset();
    });

    private static final Effect atraxSoloAuraFx = new Effect(20f, e -> {
        Draw.z(Layer.effect - 0.01f);
        Draw.color(PerfCore.color("e56f48"));
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 6f + e.fin() * 10f);
        Draw.reset();
    });

    private static final Effect daggerAuraFx = new Effect(20f, e -> {
        Draw.z(Layer.effect - 0.01f);
        Draw.color(PerfCore.color("84f491"));
        Lines.stroke(1.2f * e.fout());
        Lines.circle(e.x, e.y, 4f + e.fin() * 8f);
        Draw.reset();
    });

    private static final Effect ceiSmokeIngatherFx = new Effect(30f, e -> {
        Draw.z(Layer.effect + 0.1f);
        float startDist = 18f + Mathf.randomSeed(e.id, -4f, 6f);
        float dist = startDist * (1f - e.fin());
        float angle = Mathf.randomSeed(e.id * 2L, 0f, 360f);
        float px = e.x + Angles.trnsx(angle, dist);
        float py = e.y + Angles.trnsy(angle, dist);
        float size = (2.5f + Mathf.randomSeed(e.id * 3L, 0f, 1.5f)) * e.fout();
        Draw.color(PerfCore.color("90e0ef"));
        Draw.alpha(0.8f * e.fout());
        Fill.circle(px, py, size);
        Draw.reset();
    });

    private static final Color[] CEI_PARTICLE_COLORS = {
        Color.valueOf("0077b6"),
        Color.valueOf("00b4d8"),
        Color.valueOf("90e0ef"),
        Color.valueOf("caf0f8")
    };

    private static final Effect ceiFieldFx = new Effect(1800f, e -> {
        float radius = (e.data instanceof Number) ? ((Number) e.data).floatValue() : 200f;
        float x = e.x, y = e.y;
        Draw.z(Layer.effect + 0.5f);
        Draw.color(PerfCore.color("90e0ef"));
        Draw.alpha(0.18f * e.fout());
        Fill.circle(x, y, radius);
        Draw.color(PerfCore.color("00b4d8"));
        Draw.alpha(0.85f * e.fout());
        Lines.stroke(1.2f);
        Lines.circle(x, y, radius);

        for (int i = 0; i < 20; i++) {
            long seed = e.id + i * 133L;
            float baseDist = Mathf.randomSeed(seed, 15f, Math.max(16f, radius - 15f));
            float speed = Mathf.randomSeed(seed + 1, 1.8f, 6.0f);
            int dir = (i % 2 == 0) ? 1 : -1;
            float angle = Mathf.randomSeed(seed + 2, 0f, 360f) + (Time.time * speed * dir);
            float distOffset = Mathf.sin((Time.time + seed) * 0.08f) * 12f;
            float currentDist = Mathf.clamp(baseDist + distOffset, 10f, Math.max(11f, radius - 10f));
            float px = x + Angles.trnsx(angle, currentDist);
            float py = y + Angles.trnsy(angle, currentDist);
            float pSize = Mathf.randomSeed(seed + 3, 1.2f, 3.2f);
            int colIndex = (int) Math.floor(Mathf.randomSeed(seed + 4, 0f, CEI_PARTICLE_COLORS.length));
            if (colIndex < 0) colIndex = 0;
            if (colIndex >= CEI_PARTICLE_COLORS.length) colIndex = CEI_PARTICLE_COLORS.length - 1;
            Draw.color(CEI_PARTICLE_COLORS[colIndex]);
            Draw.alpha(0.85f * e.fout());
            Fill.circle(px, py, pSize);
        }

        Lines.stroke(1.5f);
        for (int i = 0; i < 12; i++) {
            long seed = e.id + i * 555L;
            float arcDist = Mathf.randomSeed(seed, 30f, Math.max(31f, radius - 20f));
            float arcSpeed = Mathf.randomSeed(seed + 1, 1.2f, 3.5f);
            int dir = (i % 3 == 0) ? -1 : 1;
            float angle = Mathf.randomSeed(seed + 2, 0f, 360f) + (Time.time * arcSpeed * dir);
            float arcLength = Mathf.randomSeed(seed + 3, 12f, 28f);
            Draw.color(PerfCore.color("caf0f8"));
            Draw.alpha(0.6f * e.fout());
            Lines.arc(x, y, arcDist, arcLength / 360f, angle);
        }
        Draw.reset();
    });

    private static final Effect bemodLoopFx = new Effect(35f, e -> {
        Draw.z(Layer.effect + 0.05f);
        long seed = e.id;
        float baseSize = 20f + Mathf.randomSeed(seed, -5f, 10f);
        float radius = baseSize * (1f - e.fin());
        Draw.color(PerfCore.color("ff4500"));
        Draw.alpha(e.fout());
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, radius);
        Draw.reset();
    });

    private static final Effect customAtkSpeedFx = new Effect(45f, e -> {
        Draw.z(Layer.effect + 0.01f);
        Draw.color(PerfCore.color("ff6e6e"));
        Lines.stroke(1.5f);
        float randX = Mathf.randomSeed(e.id * 2L, -14f, 14f);
        float startY = Mathf.randomSeed(e.id * 3L, -12f, 4f);
        float moveUpY = e.fin() * (16f + Mathf.randomSeed(e.id));
        float topX = e.x + randX;
        float topY = e.y + startY + moveUpY;
        Lines.line(topX - 3.5f, topY - 3.5f, topX, topY);
        Lines.line(topX + 3.5f, topY - 3.5f, topX, topY);
        Draw.reset();
    });

    // ---- các StatusEffect (tạo trong loadContent) ----
    public static StatusEffect cei, deot, dotei, atkspeed, bemod, daggerProtect, daggerSpeed, velaSiege, velaHealBuff,
        atraxSoloBuff, atraxInvulnerableBuff;

    /** Đặt trường float theo tên nếu tồn tại (JS gán thuộc tính động; bản game không có trường đó thì bỏ qua). */
    private static void setFloat(Object o, String name, float v) {
        try {
            o.getClass().getField(name).setFloat(o, v);
        } catch (Throwable ignored) {
        }
    }

    private Sta() {}

    /** Gọi từ NexMod.loadContent(). Game sẽ tự gọi init() của từng StatusEffect khi khởi tạo nội dung. */
    public static void create() {
        atraxSoloBuff = new StatusEffect("atraxSoloBuff") {
            {
                healthMultiplier = 2.5f;
                reloadMultiplier = 3.0f;
                damageMultiplier = 1.2f;
                speedMultiplier = 1.5f;
                color = Color.valueOf("e56f48");
                show = true;
                effect = atraxSoloAuraFx;
                effectChance = 0.08f;
                setFloat(this, "armorMultiplier", 2.0f);
            }

            @Override
            public void draw(Unit unit) {
                super.draw(unit);
                Draw.z(Layer.effect);
                Draw.color(PerfCore.color("e56f48"));
                Lines.stroke(1.2f);
                Lines.circle(unit.x, unit.y, unit.hitSize + 3f + Mathf.sin(Time.time * 0.15f) * 2f);
                Draw.reset();
            }
        };

        atraxInvulnerableBuff = new StatusEffect("atraxInvulnerableBuff") {
            {
                color = Color.valueOf("ff3333");
                show = true;
            }

            @Override
            public void draw(Unit unit) {
                super.draw(unit);
                Draw.z(Layer.effect + 0.1f);
                Draw.color(PerfCore.color("ff3333"));
                Lines.stroke(1.8f);
                Lines.circle(unit.x, unit.y, unit.hitSize + 4f + Mathf.sin(Time.time * 0.3f) * 2f);
                Draw.reset();
            }
        };

        daggerSpeed = new StatusEffect("daggerSpeed") {
            {
                speedMultiplier = 1.10f;
                color = Color.valueOf("84f491");
                show = false;
            }
        };

        daggerProtect = new StatusEffect("daggerProtect") {
            {
                healthMultiplier = 100.0f;
                color = Color.valueOf("84f491");
                show = true;
                effect = daggerAuraFx;
                effectChance = 0.05f;
            }

            @Override
            public void draw(Unit unit) {
                super.draw(unit);
                Draw.z(Layer.effect);
                Draw.color(PerfCore.color("84f491"));
                Lines.stroke(1.2f);
                Lines.circle(unit.x, unit.y, unit.hitSize + 2f + Mathf.sin(Time.time * 0.1f) * 1.5f);
                Draw.reset();
            }
        };

        cei = new StatusEffect("cei") {
            {
                color = Color.valueOf("90e0ef");
            }

            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                int id = unit.id;
                if (!ceiStacks.containsKey(id)) ceiStacks.put(id, 0);
                if (!ceiTimers.containsKey(id)) ceiTimers.put(id, 0f);

                float timer = ceiTimers.get(id) + Time.delta;
                ceiTimers.put(id, timer);
                if (timer >= 60f) {
                    ceiTimers.put(id, 0f);
                    int currentStacks = ceiStacks.get(id) + 1;
                    ceiStacks.put(id, currentStacks);
                    if (currentStacks % 5 == 0) ceiSmokeIngatherFx.at(unit.x, unit.y);

                    int reqLimit = 75;
                    Object lastTurret = ceiLastAppliedTurret.get(id);
                    int perk = (lastTurret instanceof PerkTurret) ? ((PerkTurret) lastTurret).getPerkTier() : 0;

                    if (perk == 3) reqLimit = 60;
                    if (perk == 6) reqLimit = 30;

                    if (currentStacks >= reqLimit) {
                        ceiStacks.put(id, 0);
                        spawnCeiField(unit.x, unit.y, unit, lastTurret);
                    }
                }
            }

            @Override
            public void draw(Unit unit) {
                int stacks = stacks(ceiStacks, unit.id);
                if (stacks <= 0) return;
                Draw.z(Layer.effect + 0.05f);
                Draw.color(PerfCore.color("90e0ef"));
                Lines.stroke(1.2f / Scl.scl(1.0f));
                float arcDegree = 12f;
                float arcFraction = arcDegree / 360f;

                for (int i = 0; i < stacks; i++) {
                    long seed = unit.id * 1000L + i;
                    float baseAngle = Mathf.randomSeed(seed, 0f, 360f);
                    float rotSpeed = Mathf.randomSeed(seed + 1, 1.0f, 3.5f);
                    int dir = (i % 2 == 0) ? 1 : -1;
                    float currentAngle = baseAngle + (Time.time * rotSpeed * dir);
                    float minR = Math.max(3.0f, unit.hitSize * 0.4f);
                    float maxR = unit.hitSize + 2.0f;
                    float baseRadius = Mathf.randomSeed(seed + 2, minR, maxR);
                    float radiusOffset = Mathf.sin((Time.time + seed) * 0.1f) * 1.5f;
                    float currentRadius = Math.max(2.0f, baseRadius + radiusOffset);
                    Lines.arc(unit.x, unit.y, currentRadius, arcFraction, currentAngle);
                }
                Draw.reset();
            }

            @Override
            public void onRemoved(Unit unit) {
                ceiStacks.remove(unit.id);
                ceiTimers.remove(unit.id);
                ceiLastAppliedTurret.remove(unit.id);
            }
        };

        dotei = new StatusEffect("dotei") {
            {
                color = Color.valueOf("a15bf7");
                damage = 0f;
            }

            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                int id = unit.id;
                if (!doteiStacks.containsKey(id)) doteiStacks.put(id, 1);
                float damagePerTick = (1f * doteiStacks.get(id)) / 60f;
                isDoteiDamageActive = true;
                unit.damage(damagePerTick, true);
                isDoteiDamageActive = false;
            }

            @Override
            public void onRemoved(Unit unit) {
                doteiStacks.remove(unit.id);
            }
        };

        deot = new StatusEffect("deot") {
            {
                color = Color.valueOf("4be391");
            }

            @Override
            public void update(Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                int id = unit.id;
                if (!deotDamagedTime.containsKey(id)) deotDamagedTime.put(id, 0f);
            }

            @Override
            public void onRemoved(Unit unit) {
                deotLastHealth.remove(unit.id);
                deotDamagedTime.remove(unit.id);
            }
        };

        bemod = new StatusEffect("bemod") {
            {
                color = Color.valueOf("ff4500");
            }

            @Override
            public void update(final Unit unit, StatusEntry entry) {
                super.update(unit, entry);
                if (Mathf.chanceDelta(1.0 / 60.0)) {
                    int id = unit.id;
                    int stacks = stacksOrOne(id);
                    for (int i = 0; i < stacks; i++) {
                        Time.run(Mathf.random(0f, 30f), () -> {
                            if (unit != null && unit.isValid()) bemodLoopFx.at(unit.x, unit.y);
                        });
                    }
                }
            }

            private int stacksOrOne(int id) {
                Integer v = bemodStacks.get(id);
                return (v != null && v != 0) ? v : 1;
            }

            @Override
            public void onRemoved(Unit unit) {
                bemodStacks.remove(unit.id);
            }
        };

        atkspeed = new StatusEffect("atkspeed") {
            {
                reloadMultiplier = 2.5f;
                effect = customAtkSpeedFx;
                effectChance = 0.18f;
                color = Color.sky;
            }
        };

        velaSiege = new StatusEffect("velaSiege") {
            {
                speedMultiplier = 1.0f;
                color = Color.valueOf("ffaa59");
                show = true;
            }

            @Override
            public void draw(Unit unit) {
                super.draw(unit);
                Draw.z(Layer.effect + 0.1f);

                float radius = unit.hitSize + 6f;
                float rot = Time.time * 1.2f;

                Draw.color(PerfCore.color("ffaa59"));
                Lines.stroke(1.8f);
                Lines.poly(unit.x, unit.y, 6, radius + Mathf.sin(Time.time * 0.1f) * 1.5f, rot);

                Draw.color(PerfCore.color("ff8833"));
                Draw.alpha(0.4f + Mathf.sin(Time.time * 0.2f) * 0.2f);
                Lines.stroke(1.2f);
                Lines.poly(unit.x, unit.y, 6, radius * 0.82f, -rot * 0.6f);

                Draw.reset();
            }
        };

        velaHealBuff = new StatusEffect("velaHealBuff") {
            {
                damageMultiplier = 1.20f;
                color = Color.valueOf("84f491");
                show = true;
            }
        };
    }

    // ---- Cei Frost Field ----
    private static final class CeiField {
        float x, y;
        Team attackerTeam, victimTeam;
        float timer = 0f, totalTime = 1800f;
        float radius;
        int perkTier;
        Object sourceTurret;
    }

    private static final Seq<CeiField> activeCeiFields = new Seq<>();

    private static void spawnCeiField(float x, float y, Unit targetUnit, Object sourceTurret) {
        int perkTier = (sourceTurret instanceof PerkTurret) ? ((PerkTurret) sourceTurret).getPerkTier() : 0;
        float radius = 200f;
        if (perkTier == 2) radius *= 2.20f;
        if (perkTier == 3) radius *= 0.50f;
        if (perkTier == 5) radius *= 0.80f;
        if (perkTier == 6) radius *= 3.00f;

        ceiFieldFx.at(x, y, 0f, Float.valueOf(radius));
        CeiField field = new CeiField();
        field.x = x;
        field.y = y;
        field.attackerTeam = (targetUnit.team == Team.sharded) ? Team.crux : Team.sharded;
        field.victimTeam = targetUnit.team;
        field.radius = radius;
        field.perkTier = perkTier;
        field.sourceTurret = sourceTurret;
        activeCeiFields.add(field);
    }

    private static void updateFields() {
        for (int i = activeCeiFields.size - 1; i >= 0; i--) {
            CeiField field = activeCeiFields.get(i);
            field.timer += Time.delta;
            field.totalTime -= Time.delta;

            if (field.timer >= 180f) {
                field.timer = 0f;
                final float fx = field.x, fy = field.y, r = field.radius;
                final Team vTeam = field.victimTeam;
                final int perk = field.perkTier;
                final float hpPercent = (perk == 5) ? 0.05f : 0.01f;

                Groups.unit.each(u -> {
                    if (u != null && u.isValid() && u.team == vTeam && Mathf.dst(u.x, u.y, fx, fy) <= r) {
                        float baseDmg = 330f;
                        if (perk == 1) baseDmg *= 1.50f;
                        float dmg = baseDmg + (u.maxHealth * hpPercent);
                        if (perk == 2) dmg += 300f;
                        u.damage(dmg);
                        if (perk == 6) u.damage(200f);
                        u.apply(StatusEffects.freezing, 300f);
                        u.apply(StatusEffects.wet, 300f);
                        if (perk == 5) {
                            int uid = u.id;
                            ceiStacks.put(uid, stacks(ceiStacks, uid) + 5);
                        }
                    }
                });

                Vars.indexer.eachBlock(null, fx, fy, r, b -> b.team == vTeam, b -> {
                    float baseDmg = 330f;
                    if (perk == 1) baseDmg *= 1.50f;
                    float dmg = baseDmg + (b.maxHealth * hpPercent);
                    if (perk == 2) dmg += 300f;
                    b.damage(dmg);
                });
            }

            if (field.totalTime <= 0) {
                activeCeiFields.remove(i);
            }
        }
    }

    public static void init() {
        Events.run(Trigger.update, Sta::updateFields);

        Events.on(UnitDestroyEvent.class, e -> {
            int id = e.unit.id;
            deotLastHealth.remove(id);
            deotDamagedTime.remove(id);
            doteiStacks.remove(id);
            bemodStacks.remove(id);
            ceiStacks.remove(id);
            ceiTimers.remove(id);
            ceiLastAppliedTurret.remove(id);
        });

        Events.on(ClientLoadEvent.class, e -> {
            cei.localizedName = "Cei Frost Field";
            cei.description = "Tích lũy 1 điểm mỗi giây. Đủ mốc sẽ tạo Vùng Băng Giá tồn tại 30s.";
            dotei.localizedName = "Dotei";
            deot.localizedName = "Deot";
            atkspeed.localizedName = "Leolyr Frenzy";
            bemod.localizedName = "Bemod";
            daggerProtect.localizedName = "Liên kết Dagger";
            daggerProtect.description = "Giảm 99% sát thương nhận vào khi ở gần Dagger khác.";
            velaSiege.localizedName = "Vela Siege Mode";
            velaSiege.description = "Dừng di chuyển hoàn toàn, nhận lớp khiên lục giác miễn nhiễm sát thương.";
            velaHealBuff.localizedName = "Vela Aura Boost";
            velaHealBuff.description = "Tăng 20% sát thương gây ra khi được Vela khôi phục máu.";
            atraxSoloBuff.localizedName = "Atrax Cơ Động";
            atraxSoloBuff.description = "Tăng mạnh chỉ số, giảm 70% sát thương nhận vào và hồi máu liên tục khi di chuyển.";
            atraxInvulnerableBuff.localizedName = "Atrax Bất Tử";
            atraxInvulnerableBuff.description = "Không nhận bất kỳ sát thương nào trong 10 giây khi máu dưới 15%.";
        });
    }
}
