package newex.content;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.ctype.ContentType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.NoiseMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.PlanetDialog;

import java.lang.reflect.Field;

/**
 * Cấu hình hành tinh Zorynex: cây công nghệ, luật chơi, lưới 3D, bỏ cấm block,
 * và hiệu ứng màn chơi Forkneo (zoom hiện tên khi chọn + cắt cảnh zoom-in khi nhấn chơi).
 */
public final class ZorynexPlanet {
    private static Planet zorynexPlanet;
    private static SectorPreset forkneoPreset;

    private static Field fSelected, fZoom;
    private static boolean reflectChecked = false;

    private static boolean forkneoSelected = false;
    private static float previewAnim = 0f;
    private static float initialZoomDoneFor = -1f;

    private static boolean cutsceneActive = false;
    private static float cutsceneTime = 0f;
    private static final float CUTSCENE_DUR = 1.15f;
    private static float cutsceneStartZoom = 0.65f;
    private static Sector cutsceneSector = null;
    private static final Vec3 tmpCamDir = new Vec3();
    private static final Color tmpCol = new Color();

    private ZorynexPlanet() {}

    public static void init() {
        Events.on(ClientLoadEvent.class, e -> {
            Planet zorynex = Vars.content.getByName(ContentType.planet, "newex-zorynex");
            if (zorynex == null) zorynex = Vars.content.getByName(ContentType.planet, "zorynex");
            if (zorynex == null) return;
            zorynexPlanet = zorynex;

            zorynex.techTree = Planets.serpulo.techTree;
            zorynex.defaultEnv = Planets.serpulo.defaultEnv;

            zorynex.allowLaunchLoadout = true;
            zorynex.allowLaunchSchematics = true;
            zorynex.allowLaunchToNumbered = true;
            zorynex.allowSectorInvasion = true;

            zorynex.ruleSetter = r -> {
                r.bannedBlocks.clear();
                r.schematicsAllowed = true;
            };

            GenericMesh[] meshList = new GenericMesh[]{
                new NoiseMesh(zorynex, 123456, 6, 0.62f, 3, 0.4f, 0.8f, 0.1f, Color.valueOf("#2d3142"), Color.valueOf("#4f5d75"), 1, 0.5f, 1.2f, 0.5f),
                new NoiseMesh(zorynex, 987654, 6, 0.64f, 3, 0.5f, 1.2f, 0.2f, Color.valueOf("#2a9d8f"), Color.valueOf("#e9c46a"), 1, 0.5f, 2.0f, 0.55f),
                new NoiseMesh(zorynex, 456789, 6, 0.65f, 5, 0.8f, 0.5f, 1.2f, Color.valueOf("#1d2d44"), Color.valueOf("#748cab"), 2, 0.6f, 1.5f, 0.45f),
                new NoiseMesh(zorynex, 777888, 6, 0.66f, 6, 0.85f, 0.35f, 2.2f, Color.valueOf("#e0f7fa"), Color.valueOf("#80deea"), 2, 0.7f, 0.8f, 0.35f)
            };

            zorynex.mesh = new MultiMesh(meshList);

            // Tìm SectorPreset Forkneo (content/sectors/forkneo.hjson -> maps/forkneo.msav)
            SectorPreset sp = Vars.content.getByName(ContentType.sector, "newex-forkneo");
            if (sp == null) sp = Vars.content.getByName(ContentType.sector, "forkneo");
            forkneoPreset = sp;
            if (forkneoPreset != null) {
                forkneoPreset.alwaysUnlocked = true;
                try {
                    if (forkneoPreset.sector != null) {
                        forkneoPreset.sector.preset = forkneoPreset;
                    }
                } catch (Throwable ignored) {}
            }
        });

        Events.on(WorldLoadEvent.class, e -> {
            cutsceneActive = false;
            cutsceneSector = null;
            if (Vars.state.isCampaign() && Vars.state.getSector() != null && Vars.state.getSector().planet != null) {
                if (Vars.state.getSector().planet.name.contains("zorynex")) {
                    Vars.state.rules.bannedBlocks.clear();
                }
            }
        });

        if (!Vars.headless) {
            Events.run(Trigger.update, ZorynexPlanet::updatePlanetFx);
            Events.run(Trigger.universeDrawEnd, ZorynexPlanet::drawForkneoZoomLabel);
            Events.run(Trigger.uiDrawEnd, ZorynexPlanet::drawCutsceneOverlay);
        }
    }

    private static void ensureReflect() {
        if (reflectChecked) return;
        reflectChecked = true;
        try {
            fSelected = PlanetDialog.class.getDeclaredField("selected");
            fSelected.setAccessible(true);
        } catch (Throwable ignored) {}
        try {
            fZoom = PlanetDialog.class.getDeclaredField("zoom");
            fZoom.setAccessible(true);
        } catch (Throwable ignored) {}
    }

    private static Sector getSelected(PlanetDialog dialog) {
        ensureReflect();
        if (fSelected == null || dialog == null) return null;
        try {
            Object v = fSelected.get(dialog);
            return v instanceof Sector ? (Sector) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float getZoom(PlanetDialog dialog) {
        ensureReflect();
        if (dialog == null) return 1f;
        try {
            if (fZoom != null) return fZoom.getFloat(dialog);
        } catch (Throwable ignored) {}
        return dialog.state != null ? dialog.state.zoom : 1f;
    }

    private static void setZoom(PlanetDialog dialog, float z) {
        ensureReflect();
        if (dialog == null) return;
        try {
            if (fZoom != null) fZoom.setFloat(dialog, z);
        } catch (Throwable ignored) {}
        if (dialog.state != null) dialog.state.zoom = z;
    }

    private static boolean isForkneo(Sector s) {
        if (s == null || s.planet == null) return false;
        if (forkneoPreset != null && s.preset == forkneoPreset) return true;
        if (zorynexPlanet != null && s.planet == zorynexPlanet) {
            if (s.preset != null && s.preset.name != null && s.preset.name.toLowerCase().contains("forkneo")) return true;
            if (s.id == 0 && forkneoPreset != null && forkneoPreset.planet == zorynexPlanet) return true;
        }
        return false;
    }

    private static boolean isEn() {
        try {
            String loc = Core.settings.getString("locale", "default");
            return loc != null && loc.toLowerCase().startsWith("en");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Chặn nút Chơi / Phóng mặc định của PlanetDialog khi đang chọn Forkneo để chạy cắt cảnh zoom-in trước khi vào map. */
    private static void hookPlayButtons(Group root, final PlanetDialog dialog, final Sector sector) {
        if (root == null) return;
        for (Element child : root.getChildren()) {
            if (child instanceof TextButton) {
                TextButton btn = (TextButton) child;
                CharSequence txt = btn.getText();
                if (txt != null && btn.userObject != Boolean.TRUE) {
                    String s = txt.toString().toLowerCase();
                    if (s.contains("play") || s.contains("launch") || s.contains("go")
                        || s.contains("chơi") || s.contains("phóng") || s.contains("vào") || s.contains("khởi")) {
                        // Chỉ gắn cho nút thuộc bảng thông tin sector (không gắn nút Quay lại/Back/Hủy)
                        if (!s.contains("back") && !s.contains("quay") && !s.contains("cancel") && !s.contains("hủy")
                            && !s.contains("research") && !s.contains("nghiên") && !s.contains("tech")) {
                            btn.userObject = Boolean.TRUE;
                            btn.getListeners().clear();
                            btn.addListener(new ClickListener() {
                                @Override
                                public void clicked(InputEvent event, float x, float y) {
                                    Sector cur = getSelected(dialog);
                                    if (isForkneo(cur)) {
                                        startCutscene(dialog, cur);
                                    } else if (cur != null) {
                                        try {
                                            Vars.control.playSector(cur);
                                            dialog.hide();
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            });
                        }
                    }
                }
            } else if (child instanceof Group) {
                hookPlayButtons((Group) child, dialog, sector);
            }
        }
    }

    private static void startCutscene(PlanetDialog dialog, Sector sector) {
        if (cutsceneActive || sector == null) return;
        cutsceneActive = true;
        cutsceneTime = 0f;
        cutsceneSector = sector;
        cutsceneStartZoom = Math.max(0.35f, getZoom(dialog));
    }

    private static void updatePlanetFx() {
        if (Vars.ui == null || Vars.ui.planet == null || !Vars.ui.planet.isShown()) {
            forkneoSelected = false;
            previewAnim = 0f;
            initialZoomDoneFor = -1f;
            if (cutsceneActive) {
                cutsceneActive = false;
                cutsceneSector = null;
            }
            return;
        }

        PlanetDialog dialog = Vars.ui.planet;
        PlanetParams params = dialog.state;
        if (params == null || params.planet == null) return;

        // Đang chạy đoạn cắt cảnh zoom nhỏ vào màn chơi Forkneo
        if (cutsceneActive && cutsceneSector != null) {
            cutsceneTime += Time.delta / 60f;
            float p = Mathf.clamp(cutsceneTime / CUTSCENE_DUR);
            float ease = Interp.pow3In.apply(p);
            float z = Mathf.lerp(cutsceneStartZoom, 0.10f, ease);
            setZoom(dialog, z);
            try {
                if (dialog.planets != null && dialog.planets.camPos != null && cutsceneSector.tile != null) {
                    tmpCamDir.set(cutsceneSector.tile.v).nor().scl(cutsceneSector.planet.radius * (1.1f + z * 1.4f));
                    dialog.planets.camPos.lerp(tmpCamDir, Mathf.clamp(Time.delta * 0.16f));
                }
            } catch (Throwable ignored) {}

            if (p >= 1f) {
                Sector target = cutsceneSector;
                cutsceneActive = false;
                cutsceneSector = null;
                try {
                    dialog.hide();
                    Vars.control.playSector(target);
                } catch (Throwable err) {
                    Log.err("[ZorynexPlanet] Failed to launch Forkneo", err);
                }
            }
            return;
        }

        Sector sel = getSelected(dialog);
        boolean isF = isForkneo(sel);
        if (isF) {
            if (!forkneoSelected) {
                forkneoSelected = true;
                previewAnim = 0f;
                initialZoomDoneFor = sel.id;
            }
            previewAnim = Mathf.clamp(previewAnim + Time.delta / 22f);

            // Tự động zoom mượt gần vào Forkneo khi người chơi vừa nhấn chọn
            if (previewAnim < 0.98f) {
                float curZ = getZoom(dialog);
                if (curZ > 0.62f) {
                    setZoom(dialog, Mathf.lerp(curZ, 0.60f, Mathf.clamp(Time.delta * 0.14f)));
                }
            }

            // Chặn nút Play/Launch khi đang chọn Forkneo để phát cắt cảnh zoom-in
            hookPlayButtons(dialog, dialog, sel);
        } else {
            forkneoSelected = false;
            previewAnim = Math.max(0f, previewAnim - Time.delta / 14f);
            initialZoomDoneFor = -1f;
        }
    }

    /** Vẽ khung tên "FORKNEO" phóng to nổi bật ngay trên màn chơi khi nhấn chọn trên hành tinh Zorynex. */
    private static void drawForkneoZoomLabel() {
        if (Vars.ui == null || Vars.ui.planet == null || !Vars.ui.planet.isShown()) return;
        PlanetDialog dialog = Vars.ui.planet;
        PlanetParams params = dialog.state;
        if (params == null || !params.drawUi || params.uiAlpha <= 0.01f) return;

        Sector target = cutsceneActive ? cutsceneSector : getSelected(dialog);
        if (!isForkneo(target) || previewAnim <= 0.01f) return;

        float anim = Interp.swingOut.apply(previewAnim);
        float pulse = 0.85f + 0.15f * Mathf.sin(Time.time / 12f);
        float alpha = params.uiAlpha * Mathf.clamp(previewAnim * 1.4f);

        dialog.planets.drawPlane(target, () -> {
            float scale = 0.55f + 0.50f * anim;
            float yOff = 18f * anim;

            // Vòng sáng định vị quanh ô Forkneo
            Lines.stroke(2.2f);
            Draw.color(tmpCol.set(0.35f, 0.92f, 1f, alpha * 0.85f * pulse));
            Lines.circle(0f, 0f, 12f + 4f * anim);
            Lines.stroke(1.2f);
            Draw.color(1f, 0.88f, 0.45f, alpha * 0.7f);
            Lines.circle(0f, 0f, 16f + 6f * anim);

            // Bảng nền tên map phóng to
            float bw = 96f * scale, bh = 28f * scale;
            Draw.color(0.05f, 0.09f, 0.16f, alpha * 0.86f);
            Fill.rect(0f, yOff + bh * 0.55f, bw, bh);
            Lines.stroke(1.8f);
            Draw.color(0.35f, 0.92f, 1f, alpha * pulse);
            Lines.rect(-bw / 2f, yOff + bh * 0.05f, bw, bh);

            // Tên map FORKNEO phóng to rõ nét
            Fonts.outline.getData().setScale(0.78f * scale);
            Fonts.outline.setColor(0.65f, 0.96f, 1f, alpha);
            Fonts.outline.draw("FORKNEO", 0f, yOff + bh * 0.82f, Align.center);
            Fonts.outline.setColor(Color.white);
            Fonts.outline.getData().setScale(1f);
        });
        Draw.reset();
    }

    /** Hiệu ứng cắt cảnh zoom-in toàn màn hình khi bắt đầu vào màn chơi Forkneo. */
    private static void drawCutsceneOverlay() {
        if (!cutsceneActive || cutsceneSector == null) return;
        float p = Mathf.clamp(cutsceneTime / CUTSCENE_DUR);
        float w = Core.graphics.getWidth();
        float h = Core.graphics.getHeight();
        float cx = w * 0.5f, cy = h * 0.5f;

        // Vòng hội tụ năng lượng khi lao vào hành tinh
        float ringProg = Interp.pow2In.apply(p);
        float maxR = Math.max(w, h) * 0.65f;
        Lines.stroke(Scl.scl(3.5f) * (1f + p * 2f));
        Draw.color(0.38f, 0.92f, 1f, (1f - p * 0.5f) * 0.65f);
        Lines.circle(cx, cy, Math.max(Scl.scl(12f), maxR * (1f - ringProg)));

        // Chữ tiêu đề cắt cảnh
        float textAlpha = p < 0.82f ? Mathf.clamp(p * 4f) : Mathf.clamp((1f - p) / 0.18f);
        float textScale = Scl.scl(1.25f + p * 0.45f);
        Fonts.outline.getData().setScale(textScale);
        Fonts.outline.setColor(0.75f, 0.97f, 1f, textAlpha);
        Fonts.outline.draw("FORKNEO", cx, cy + Scl.scl(42f), Align.center);

        Fonts.outline.getData().setScale(Scl.scl(0.85f));
        Fonts.outline.setColor(1f, 0.92f, 0.65f, textAlpha * 0.95f);
        Fonts.outline.draw(isEn() ? "Descending to Zorynex..." : "Đang đáp xuống Zorynex...", cx, cy - Scl.scl(18f), Align.center);
        Fonts.outline.setColor(Color.white);
        Fonts.outline.getData().setScale(1f);

        // Chớp sáng & chuyển cảnh mượt ở 30% cuối
        if (p > 0.68f) {
            float f = Interp.pow2In.apply((p - 0.68f) / 0.32f);
            Draw.color(0.04f, 0.09f, 0.15f, f * 0.95f);
            Fill.rect(cx, cy, w, h);
        }
        Draw.reset();
    }
}
