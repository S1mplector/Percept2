package com.jvn.editor.ui.actioneditor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

public class AnimationPreview extends VBox {
    private static final double PIVOT_MIN = 0.0;
    private static final double PIVOT_MAX = 1.0;
    private static final double TRANSFORM_EPSILON = 1e-6;

    private static final class EntityFrame {
        final double x;
        final double y;
        final double w;
        final double h;
        final double originX;
        final double originY;
        final double scaleX;
        final double scaleY;
        final double rotationRad;

        EntityFrame(double x, double y, double w, double h, double originX, double originY,
                    double scaleX, double scaleY, double rotationRad) {
            this.x = x;
            this.y = y;
            this.w = Math.max(TRANSFORM_EPSILON, w);
            this.h = Math.max(TRANSFORM_EPSILON, h);
            this.originX = originX;
            this.originY = originY;
            this.scaleX = normalizeScale(scaleX);
            this.scaleY = normalizeScale(scaleY);
            this.rotationRad = rotationRad;
        }
    }

    private static final class PivotDragState {
        final double baseX;
        final double baseY;
        final double baseW;
        final double baseH;
        final double baseOriginX;
        final double baseOriginY;
        final double baseScaleX;
        final double baseScaleY;
        final double baseRotationRad;

        PivotDragState(EntityFrame frame) {
            this.baseX = frame.x;
            this.baseY = frame.y;
            this.baseW = frame.w;
            this.baseH = frame.h;
            this.baseOriginX = frame.originX;
            this.baseOriginY = frame.originY;
            this.baseScaleX = frame.scaleX;
            this.baseScaleY = frame.scaleY;
            this.baseRotationRad = frame.rotationRad;
        }
    }

    private final Canvas canvas;
    private final GraphicsContext gc;
    private final FxBlitter2D blitter;
    private final Input input;
    private final Camera2D camera;

    private JesScene2D scene;
    private AnimationProject project;
    private boolean onionSkinning = false;
    private int onionFrames = 3;

    private Entity2D selectedEntity;
    private String selectedEntityName;
    private boolean draggingEntity = false;
    private boolean draggingPivot = false;
    private boolean draggingOrbit = false;
    private boolean draggingOrbitAnchor = false;
    private PivotDragState pivotDragState;
    private double dragEntityStartX, dragEntityStartY;
    private boolean pivotAxisLocked = false;
    private boolean pivotAxisIsHorizontal = false;
    private double pivotDragStartWorldX, pivotDragStartWorldY;
    private String pivotOverlayText = null;
    private double pivotOverlayScreenX, pivotOverlayScreenY;
    private double orbitRadius = 0.0;
    private boolean orbitToolEnabled = false;
    private boolean orbitAlignRotation = true;
    private final Map<String, double[]> orbitAnchors = new HashMap<>();

    private Consumer<String> onEntitySelected;
    private BiConsumer<String, double[]> onEntityMoved;
    private BiConsumer<String, double[]> onEntityPivotChanged;
    private BiConsumer<String, Double> onEntityRotationChanged;
    private ProjectViewportSpec.Dimensions viewportSpec =
        new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
    private double viewportScale = 1.0;
    private double viewportOffsetX = 0.0;
    private double viewportOffsetY = 0.0;
    private double viewportLogicalWidth = ProjectViewportSpec.DEFAULT_WIDTH;
    private double viewportLogicalHeight = ProjectViewportSpec.DEFAULT_HEIGHT;

    public void setProjectRoot(java.io.File root) {
        blitter.setProjectRoot(root);
        viewportSpec = ProjectViewportSpec.resolve(root);
        render();
    }

    public AnimationPreview() {
        canvas = new Canvas(600, 300);
        gc = canvas.getGraphicsContext2D();
        blitter = new FxBlitter2D(gc);
        input = new Input();
        camera = new Camera2D();

        Pane container = new Pane(canvas);
        VBox.setVgrow(container, Priority.ALWAYS);

        getChildren().add(container);
        setStyle("-fx-background-color: #121212;");

        widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(newVal.doubleValue());
            render();
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setHeight(newVal.doubleValue());
            render();
        });

        setupMouseControls();
        render();
    }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
        if (scene != null) {
            scene.setInput(input);
            scene.setCamera(camera);
            Set<String> names = new HashSet<>(scene.names());
            orbitAnchors.keySet().removeIf(name -> !names.contains(name));
            if (selectedEntityName != null) {
                if (!names.contains(selectedEntityName)) {
                    selectedEntity = null;
                    selectedEntityName = null;
                } else {
                    selectedEntity = scene.find(selectedEntityName);
                }
            }
        } else {
            selectedEntity = null;
            selectedEntityName = null;
            orbitAnchors.clear();
        }
        pivotDragState = null;
        render();
    }

    public void setProject(AnimationProject project) {
        this.project = project;
    }

    public JesScene2D getJesScene() { return scene; }
    public Camera2D getCamera() { return camera; }

    public void render() {
        double w = Math.max(1.0, canvas.getWidth());
        double h = Math.max(1.0, canvas.getHeight());
        updateViewportTransform(w, h);

        gc.setFill(Color.web("#121212"));
        gc.fillRect(0, 0, w, h);
        gc.setFill(Color.BLACK);
        gc.fillRect(viewportOffsetX, viewportOffsetY, viewportLogicalWidth * viewportScale, viewportLogicalHeight * viewportScale);

        gc.save();
        gc.beginPath();
        gc.rect(viewportOffsetX, viewportOffsetY, viewportLogicalWidth * viewportScale, viewportLogicalHeight * viewportScale);
        gc.closePath();
        gc.clip();
        gc.translate(viewportOffsetX, viewportOffsetY);
        gc.scale(viewportScale, viewportScale);

        drawGrid(viewportLogicalWidth, viewportLogicalHeight);

        blitter.setViewport(viewportLogicalWidth, viewportLogicalHeight);
        if (scene != null) {
            if (onionSkinning && project != null) drawOnionSkins();
            scene.render(blitter, viewportLogicalWidth, viewportLogicalHeight);
            drawMotionPaths();
            if (selectedEntity != null) drawSelectionHighlight(selectedEntity);
        } else {
            gc.setFill(Color.web("#a0a0a0"));
            gc.fillText("No scene loaded", viewportLogicalWidth / 2 - 40, viewportLogicalHeight / 2);
        }
        gc.restore();

        if (pivotOverlayText != null) {
            gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
            double textW = pivotOverlayText.length() * 7.0;
            double textH = 16.0;
            double ox = Math.min(pivotOverlayScreenX, w - textW - 6);
            double oy = Math.max(pivotOverlayScreenY, textH + 4);
            gc.setFill(Color.web("#1a1a1a", 0.85));
            gc.fillRoundRect(ox - 4, oy - textH + 2, textW + 8, textH + 4, 4, 4);
            gc.setFill(Color.web("#f7d07a"));
            gc.fillText(pivotOverlayText, ox, oy);
        }
    }

    private void updateViewportTransform(double canvasW, double canvasH) {
        if (viewportSpec == null) {
            viewportSpec = new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
        }
        var vp = ViewportScaler2D.fit(viewportSpec.width(), viewportSpec.height(), canvasW, canvasH);
        viewportScale = Math.max(1e-6, vp.scale());
        viewportOffsetX = vp.offsetX();
        viewportOffsetY = vp.offsetY();
        viewportLogicalWidth = vp.targetWidth();
        viewportLogicalHeight = vp.targetHeight();
    }

    private void drawMotionPaths() {
        if (project == null) return;
        double z = Math.max(0.0001, camera.getZoom());

        gc.save();
        applyCameraTransform();

        for (EntityTrack track : project.getTracks()) {
            java.util.List<SplinePath.Point> controlPoints =
                SplinePath.buildControlPoints(track, project.getTotalDurationMs());
            if (controlPoints.size() < 2) continue;

            java.util.List<SplinePath.Point> curve = SplinePath.catmullRom(controlPoints, 16);

            gc.setStroke(Color.web("#4da3ff", 0.5));
            gc.setLineWidth(1.5 / z);
            gc.setLineDashes(4.0 / z, 3.0 / z);
            gc.beginPath();
            boolean first = true;
            for (SplinePath.Point pt : curve) {
                if (first) { gc.moveTo(pt.x, pt.y); first = false; }
                else gc.lineTo(pt.x, pt.y);
            }
            gc.stroke();
            gc.setLineDashes((double[]) null);

            gc.setFill(Color.web("#4da3ff", 0.8));
            for (SplinePath.Point pt : controlPoints) {
                double radius = 3.0 / z;
                gc.fillOval(pt.x - radius, pt.y - radius, radius * 2.0, radius * 2.0);
            }
        }
        gc.restore();
    }

    private void drawOnionSkins() {
        if (project == null) return;
        double z = Math.max(0.0001, camera.getZoom());
        double now = project.getPlayheadMs();
        double dur = project.getTotalDurationMs();
        double step = dur / 20;

        gc.save();
        applyCameraTransform();

        for (EntityTrack track : project.getTracks()) {
            java.util.List<Keyframe> xFrames = track.getKeyframes(PropertyType.X);
            java.util.List<Keyframe> yFrames = track.getKeyframes(PropertyType.Y);
            if (xFrames.isEmpty() && yFrames.isEmpty()) continue;

            boolean hasPivotX = !track.getKeyframes(PropertyType.PIVOT_X).isEmpty();
            boolean hasPivotY = !track.getKeyframes(PropertyType.PIVOT_Y).isEmpty();
            boolean hasRotation = !track.getKeyframes(PropertyType.ROTATION).isEmpty();
            boolean hasScaleX = !track.getKeyframes(PropertyType.SCALE_X).isEmpty();
            boolean hasScaleY = !track.getKeyframes(PropertyType.SCALE_Y).isEmpty();

            for (int i = -onionFrames; i <= onionFrames; i++) {
                if (i == 0) continue;
                double t = now + i * step;
                if (t < 0 || t > dur) continue;

                double x = track.getValueAt(PropertyType.X, t);
                double y = track.getValueAt(PropertyType.Y, t);

                double alpha = 0.3 * (1.0 - Math.abs(i) / (double)(onionFrames + 1));
                Color color = i < 0 ? Color.web("#f38ba8", alpha) : Color.web("#58d68d", alpha);

                gc.setStroke(color);
                gc.setLineWidth(1.5 / z);
                double size = 20.0 / z;

                if (hasPivotX || hasPivotY || hasRotation || hasScaleX || hasScaleY) {
                    double pivX = hasPivotX ? track.getValueAt(PropertyType.PIVOT_X, t) : 0.5;
                    double pivY = hasPivotY ? track.getValueAt(PropertyType.PIVOT_Y, t) : 0.5;
                    double rot = hasRotation ? Math.toRadians(track.getValueAt(PropertyType.ROTATION, t)) : 0.0;
                    double sX = hasScaleX ? track.getValueAt(PropertyType.SCALE_X, t) : 1.0;
                    double sY = hasScaleY ? track.getValueAt(PropertyType.SCALE_Y, t) : 1.0;

                    gc.save();
                    gc.translate(x, y);
                    gc.rotate(Math.toDegrees(rot));
                    gc.scale(sX, sY);
                    double halfW = size / 2;
                    double halfH = size / 2;
                    double offX = -(pivX - 0.5) * size;
                    double offY = -(pivY - 0.5) * size;
                    gc.strokeRect(offX - halfW, offY - halfH, size, size);

                    gc.setFill(color);
                    double pivDot = 2.0 / z / Math.max(0.01, Math.max(Math.abs(sX), Math.abs(sY)));
                    gc.fillOval(-pivDot, -pivDot, pivDot * 2, pivDot * 2);
                    gc.restore();
                } else {
                    gc.strokeRect(x - size / 2, y - size / 2, size, size);
                }

                gc.setFill(color);
                gc.setFont(javafx.scene.text.Font.font(8.0 / z));
                gc.fillText(String.format("%.0f", t), x + size / 2 + (2.0 / z), y);
            }
        }
        gc.restore();
    }

    private void drawGrid(double w, double h) {
        double z = Math.max(0.0001, camera.getZoom());
        double step = 50.0 * z;
        if (step < 8) return;

        gc.setStroke(Color.color(1, 1, 1, 0.04));
        gc.setLineWidth(1);

        double ox = (-camera.getX() * z) % step;
        double oy = (-camera.getY() * z) % step;

        for (double x = ox; x <= w; x += step) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = oy; y <= h; y += step) {
            gc.strokeLine(0, y, w, y);
        }
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnEntityMoved(BiConsumer<String, double[]> callback) { this.onEntityMoved = callback; }
    public void setOnEntityPivotChanged(BiConsumer<String, double[]> callback) { this.onEntityPivotChanged = callback; }
    public void setOnEntityRotationChanged(BiConsumer<String, Double> callback) { this.onEntityRotationChanged = callback; }

    public Entity2D getSelectedEntity() { return selectedEntity; }
    public String getSelectedEntityName() { return selectedEntityName; }

    public void selectEntity(String entityName) {
        if (entityName == null || entityName.isBlank() || scene == null) {
            clearSelection();
            return;
        }
        Entity2D entity = scene.find(entityName);
        if (entity == null) {
            clearSelection();
            return;
        }
        boolean changed = entity != selectedEntity || !entityName.equals(selectedEntityName);
        selectedEntity = entity;
        selectedEntityName = entityName;
        pivotDragState = null;
        if (changed) render();
    }

    public void clearSelection() {
        boolean changed = selectedEntity != null || selectedEntityName != null;
        selectedEntity = null;
        selectedEntityName = null;
        pivotDragState = null;
        if (changed) render();
    }

    public boolean isOnionSkinning() { return onionSkinning; }
    public void setOnionSkinning(boolean onionSkinning) { this.onionSkinning = onionSkinning; render(); }
    public void setOnionFrames(int frames) { this.onionFrames = Math.max(1, Math.min(10, frames)); }
    public boolean isOrbitToolEnabled() { return orbitToolEnabled; }
    public void setOrbitToolEnabled(boolean enabled) { this.orbitToolEnabled = enabled; render(); }
    public boolean isOrbitAlignRotation() { return orbitAlignRotation; }
    public void setOrbitAlignRotation(boolean enabled) { this.orbitAlignRotation = enabled; }
    public boolean hasSelectedOrbitAnchor() {
        return selectedEntityName != null && orbitAnchors.containsKey(selectedEntityName);
    }
    public void clearOrbitAnchorForSelectedEntity() {
        if (selectedEntityName == null) return;
        orbitAnchors.remove(selectedEntityName);
        render();
    }

    private String findEntityNameAt(double screenX, double screenY, boolean updateSelection) {
        if (scene == null) return null;
        if (!isInsideViewport(screenX, screenY)) {
            if (updateSelection) {
                selectedEntity = null;
                selectedEntityName = null;
            }
            return null;
        }
        double[] world = screenToWorld(screenX, screenY);
        double worldX = world[0];
        double worldY = world[1];

        String bestName = null;
        Entity2D bestEntity = null;
        int bestLayer = Integer.MIN_VALUE;
        int bestRenderOrder = Integer.MIN_VALUE;
        var named = scene.exportNamed();
        var children = scene.getChildren();
        Map<Entity2D, Integer> renderOrder = new IdentityHashMap<>();
        for (int i = 0; i < children.size(); i++) {
            renderOrder.put(children.get(i), i);
        }
        for (var entry : named.entrySet()) {
            String name = entry.getKey();
            Entity2D entity = entry.getValue();
            if (entity == null) continue;
            double[] corners = getEntityCorners(entity);
            if (containsPointInQuad(corners, worldX, worldY)) {
                int layer = project != null
                    ? project.computeEffectiveLayerOrder(name)
                    : (int) Math.round(entity.getZ());
                int order = renderOrder.getOrDefault(entity, Integer.MIN_VALUE);
                if (bestEntity == null || layer > bestLayer || (layer == bestLayer && order > bestRenderOrder)) {
                    bestName = name;
                    bestEntity = entity;
                    bestLayer = layer;
                    bestRenderOrder = order;
                }
            }
        }
        if (updateSelection) {
            selectedEntity = bestEntity;
            selectedEntityName = bestName;
        }
        return bestName;
    }

    private EntityFrame describeEntity(Entity2D entity) {
        if (entity == null) return null;
        double w = 40.0;
        double h = 40.0;
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            w = sprite.getWidth();
            h = sprite.getHeight();
        } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D character) {
            w = character.getDrawWidth();
            h = character.getDrawHeight();
        } else if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
            w = panel.getWidth();
            h = panel.getHeight();
        } else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D) {
            w = 40.0;
            h = 40.0;
        }
        return new EntityFrame(
            entity.getX(),
            entity.getY(),
            w,
            h,
            entity.getOriginX(),
            entity.getOriginY(),
            entity.getScaleX(),
            entity.getScaleY(),
            Math.toRadians(entity.getRotationDeg())
        );
    }

    private double[] getEntityCorners(Entity2D entity) {
        EntityFrame frame = describeEntity(entity);
        if (frame == null) return new double[0];
        return computeWorldCorners(frame);
    }

    private static double[] computeWorldCorners(EntityFrame frame) {
        double left = -frame.originX * frame.w;
        double top = -frame.originY * frame.h;
        double right = left + frame.w;
        double bottom = top + frame.h;

        double[] corners = new double[8];
        fillCorner(corners, 0, frame, left, top);
        fillCorner(corners, 2, frame, right, top);
        fillCorner(corners, 4, frame, right, bottom);
        fillCorner(corners, 6, frame, left, bottom);
        return corners;
    }

    private static void fillCorner(double[] out, int offset, EntityFrame frame, double localX, double localY) {
        double sx = localX * frame.scaleX;
        double sy = localY * frame.scaleY;
        double cos = Math.cos(frame.rotationRad);
        double sin = Math.sin(frame.rotationRad);
        out[offset] = frame.x + sx * cos - sy * sin;
        out[offset + 1] = frame.y + sx * sin + sy * cos;
    }

    private static boolean containsPointInQuad(double[] corners, double x, double y) {
        if (corners == null || corners.length < 8) return false;
        double sign = 0.0;
        for (int i = 0; i < 4; i++) {
            int i0 = i * 2;
            int i1 = ((i + 1) % 4) * 2;
            double x0 = corners[i0];
            double y0 = corners[i0 + 1];
            double x1 = corners[i1];
            double y1 = corners[i1 + 1];
            double cross = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0);
            if (Math.abs(cross) <= TRANSFORM_EPSILON) continue;
            double current = Math.signum(cross);
            if (sign == 0.0) {
                sign = current;
            } else if (current != sign) {
                return false;
            }
        }
        return true;
    }

    private boolean isInsideViewport(double screenX, double screenY) {
        double right = viewportOffsetX + viewportLogicalWidth * viewportScale;
        double bottom = viewportOffsetY + viewportLogicalHeight * viewportScale;
        return screenX >= viewportOffsetX && screenX <= right && screenY >= viewportOffsetY && screenY <= bottom;
    }

    private double screenToViewportX(double screenX) {
        return (screenX - viewportOffsetX) / Math.max(1e-6, viewportScale);
    }

    private double screenToViewportY(double screenY) {
        return (screenY - viewportOffsetY) / Math.max(1e-6, viewportScale);
    }

    private double[] screenToWorld(double screenX, double screenY) {
        double z = Math.max(0.0001, camera.getZoom());
        double viewportX = screenToViewportX(screenX);
        double viewportY = screenToViewportY(screenY);
        double worldX = camera.getX() + viewportX / z;
        double worldY = camera.getY() + viewportY / z;
        return new double[]{worldX, worldY};
    }

    private boolean hasOrbitAnchor(String entityName) {
        return entityName != null && orbitAnchors.containsKey(entityName);
    }

    private double[] getOrbitAnchor(String entityName) {
        return entityName == null ? null : orbitAnchors.get(entityName);
    }

    private void setOrbitAnchor(String entityName, double worldX, double worldY) {
        if (entityName == null || entityName.isBlank()) return;
        orbitAnchors.put(entityName, new double[]{worldX, worldY});
    }

    private boolean isNearOrbitAnchorHandle(double screenX, double screenY) {
        double[] anchor = getOrbitAnchor(selectedEntityName);
        if (anchor == null) return false;
        double[] viewportPoint = worldToViewport(anchor[0], anchor[1]);
        double ax = viewportPoint[0];
        double ay = viewportPoint[1];
        double pointerX = screenToViewportX(screenX);
        double pointerY = screenToViewportY(screenY);
        double dx = pointerX - ax;
        double dy = pointerY - ay;
        return dx * dx + dy * dy <= 100;
    }

    private void setupMouseControls() {
        canvas.setOnScroll(e -> {
            if (!isInsideViewport(e.getX(), e.getY())) return;
            double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
            double z = camera.getZoom();
            double viewportX = screenToViewportX(e.getX());
            double viewportY = screenToViewportY(e.getY());
            double worldX = camera.getX() + viewportX / z;
            double worldY = camera.getY() + viewportY / z;
            double newZ = Math.max(0.1, Math.min(5.0, z * factor));
            camera.setZoom(newZ);
            camera.setPosition(worldX - viewportX / newZ, worldY - viewportY / newZ);
            render();
        });

        final double[] panStart = new double[2];
        final boolean[] panning = {false};

        canvas.setOnMousePressed(e -> {
            if (e.isMiddleButtonDown() || e.isSecondaryButtonDown()) {
                panning[0] = true;
                panStart[0] = e.getX();
                panStart[1] = e.getY();
                return;
            }

            if (e.isPrimaryButtonDown()) {
                if (!isInsideViewport(e.getX(), e.getY())) {
                    selectedEntity = null;
                    selectedEntityName = null;
                    draggingEntity = false;
                    draggingOrbit = false;
                    draggingPivot = false;
                    draggingOrbitAnchor = false;
                    pivotDragState = null;
                    render();
                    return;
                }
                if (orbitToolEnabled && e.isShiftDown() && e.isAltDown()) {
                    if (selectedEntityName != null && !selectedEntityName.isBlank()) {
                        String anchorSource = findEntityNameAt(e.getX(), e.getY(), false);
                        if (anchorSource != null && !anchorSource.equals(selectedEntityName)) {
                            Entity2D sourceEntity = scene != null ? scene.find(anchorSource) : null;
                            if (sourceEntity != null) {
                                setOrbitAnchor(selectedEntityName, sourceEntity.getX(), sourceEntity.getY());
                                render();
                                return;
                            }
                        }
                    }
                }

                if (orbitToolEnabled && e.isShiftDown()) {
                    String target = selectedEntityName;
                    if (target == null || target.isBlank()) {
                        target = findEntityNameAt(e.getX(), e.getY(), true);
                        if (target != null && onEntitySelected != null) {
                            onEntitySelected.accept(target);
                        }
                    }
                    if (target != null && !target.isBlank()) {
                        double[] world = screenToWorld(e.getX(), e.getY());
                        setOrbitAnchor(target, world[0], world[1]);
                        render();
                    }
                    return;
                }

                if (orbitToolEnabled && selectedEntity != null && isNearOrbitAnchorHandle(e.getX(), e.getY())) {
                    draggingOrbitAnchor = true;
                    return;
                }

                if (selectedEntity != null && supportsPivotEntity(selectedEntity) && isNearPivotHandle(e.getX(), e.getY())) {
                    pivotDragState = buildPivotDragState(selectedEntity);
                    draggingPivot = true;
                    pivotAxisLocked = false;
                    pivotAxisIsHorizontal = false;
                    double[] startWorld = screenToWorld(e.getX(), e.getY());
                    pivotDragStartWorldX = startWorld[0];
                    pivotDragStartWorldY = startWorld[1];
                    return;
                }

                String hitName = findEntityNameAt(e.getX(), e.getY(), true);
                if (hitName != null) {
                    if (orbitToolEnabled && hasOrbitAnchor(hitName)) {
                        double[] anchor = getOrbitAnchor(hitName);
                        orbitRadius = Math.hypot(selectedEntity.getX() - anchor[0], selectedEntity.getY() - anchor[1]);
                        if (orbitRadius < 0.0001) {
                            double[] world = screenToWorld(e.getX(), e.getY());
                            orbitRadius = Math.hypot(world[0] - anchor[0], world[1] - anchor[1]);
                        }
                        draggingOrbit = true;
                    } else {
                        draggingEntity = true;
                        dragEntityStartX = e.getX();
                        dragEntityStartY = e.getY();
                    }
                    if (onEntitySelected != null) onEntitySelected.accept(hitName);
                    drawSelectionHighlight(selectedEntity);
                } else {
                    selectedEntity = null;
                    selectedEntityName = null;
                    draggingEntity = false;
                    draggingOrbit = false;
                    pivotDragState = null;
                }
                render();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (panning[0]) {
                double dx = (e.getX() - panStart[0]) / Math.max(1e-6, viewportScale);
                double dy = (e.getY() - panStart[1]) / Math.max(1e-6, viewportScale);
                panStart[0] = e.getX();
                panStart[1] = e.getY();
                camera.setPosition(
                    camera.getX() - dx / camera.getZoom(),
                    camera.getY() - dy / camera.getZoom()
                );
                render();
            } else if (draggingOrbitAnchor && selectedEntityName != null) {
                double[] world = screenToWorld(e.getX(), e.getY());
                setOrbitAnchor(selectedEntityName, world[0], world[1]);
                render();
            } else if (draggingOrbit && selectedEntity != null && selectedEntityName != null) {
                double[] anchor = getOrbitAnchor(selectedEntityName);
                if (anchor == null) return;
                double[] world = screenToWorld(e.getX(), e.getY());
                double angle = Math.atan2(world[1] - anchor[1], world[0] - anchor[0]);
                double radius = Math.max(0.0001, orbitRadius);
                double nextX = anchor[0] + radius * Math.cos(angle);
                double nextY = anchor[1] + radius * Math.sin(angle);
                selectedEntity.setPosition(nextX, nextY);

                if (orbitAlignRotation) {
                    double rotDeg = Math.toDegrees(angle);
                    selectedEntity.setRotationDeg(rotDeg);
                    if (onEntityRotationChanged != null) {
                        onEntityRotationChanged.accept(selectedEntityName, rotDeg);
                    }
                }
                if (onEntityMoved != null) {
                    onEntityMoved.accept(selectedEntityName, new double[]{nextX, nextY});
                }
                render();
            } else if (draggingPivot && selectedEntity != null) {
                if (pivotDragState == null) {
                    pivotDragState = buildPivotDragState(selectedEntity);
                }
                if (pivotDragState == null) return;
                double[] world = screenToWorld(e.getX(), e.getY());
                double worldX = world[0];
                double worldY = world[1];

                if (e.isShiftDown()) {
                    if (!pivotAxisLocked) {
                        double adx = Math.abs(worldX - pivotDragStartWorldX);
                        double ady = Math.abs(worldY - pivotDragStartWorldY);
                        if (adx > 2.0 || ady > 2.0) {
                            pivotAxisLocked = true;
                            pivotAxisIsHorizontal = adx >= ady;
                        }
                    }
                    if (pivotAxisLocked) {
                        if (pivotAxisIsHorizontal) {
                            worldY = pivotDragState.baseY;
                        } else {
                            worldX = pivotDragState.baseX;
                        }
                    }
                } else {
                    pivotAxisLocked = false;
                }

                double dx = worldX - pivotDragState.baseX;
                double dy = worldY - pivotDragState.baseY;
                double cos = Math.cos(pivotDragState.baseRotationRad);
                double sin = Math.sin(pivotDragState.baseRotationRad);
                double unrotX = cos * dx + sin * dy;
                double unrotY = -sin * dx + cos * dy;
                double localX = unrotX / pivotDragState.baseScaleX;
                double localY = unrotY / pivotDragState.baseScaleY;

                double pivotX = clampPivot(pivotDragState.baseOriginX + localX / pivotDragState.baseW);
                double pivotY = clampPivot(pivotDragState.baseOriginY + localY / pivotDragState.baseH);
                selectedEntity.setPosition(worldX, worldY);
                setEntityPivot(selectedEntity, pivotX, pivotY);

                pivotOverlayText = String.format("Pivot: (%.2f, %.2f)", pivotX, pivotY);
                pivotOverlayScreenX = e.getX() + 14;
                pivotOverlayScreenY = e.getY() - 6;

                if (onEntityPivotChanged != null && selectedEntityName != null) {
                    onEntityPivotChanged.accept(selectedEntityName, new double[]{pivotX, pivotY});
                }
                if (onEntityMoved != null && selectedEntityName != null) {
                    onEntityMoved.accept(selectedEntityName, new double[]{worldX, worldY});
                }
                render();
            } else if (draggingEntity && selectedEntity != null) {
                double[] prevWorld = screenToWorld(dragEntityStartX, dragEntityStartY);
                double[] world = screenToWorld(e.getX(), e.getY());
                double dx = world[0] - prevWorld[0];
                double dy = world[1] - prevWorld[1];
                dragEntityStartX = e.getX();
                dragEntityStartY = e.getY();
                selectedEntity.setPosition(
                    selectedEntity.getX() + dx,
                    selectedEntity.getY() + dy
                );
                if (onEntityMoved != null && selectedEntityName != null) {
                    onEntityMoved.accept(selectedEntityName,
                        new double[]{selectedEntity.getX(), selectedEntity.getY()});
                }
                render();
            }
        });

        canvas.setOnMouseReleased(e -> {
            panning[0] = false;
            draggingEntity = false;
            draggingPivot = false;
            draggingOrbit = false;
            draggingOrbitAnchor = false;
            pivotDragState = null;
            pivotAxisLocked = false;
            if (pivotOverlayText != null) {
                pivotOverlayText = null;
                render();
            }
        });
    }

    private void drawSelectionHighlight(Entity2D entity) {
        if (entity == null) return;
        double z = Math.max(0.0001, camera.getZoom());
        double[] corners = getEntityCorners(entity);
        if (corners.length < 8) return;
        double[] wx = new double[4];
        double[] wy = new double[4];
        double minWx = Double.POSITIVE_INFINITY;
        double minWy = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            wx[i] = corners[i * 2];
            wy[i] = corners[i * 2 + 1];
            if (wx[i] < minWx) minWx = wx[i];
            if (wy[i] < minWy) minWy = wy[i];
        }

        gc.save();
        applyCameraTransform();
        gc.setStroke(Color.web("#f0b673"));
        gc.setLineWidth(2.0 / z);
        gc.setLineDashes(6.0 / z, 4.0 / z);
        gc.strokePolygon(wx, wy, 4);
        gc.setLineDashes((double[]) null);

        if (supportsPivotEntity(entity)) {
            drawPivotHandleWorld(entity, z);
        }
        if (selectedEntityName != null && hasOrbitAnchor(selectedEntityName)) {
            drawOrbitAnchorWorld(selectedEntityName, entity, z);
        }

        if (selectedEntityName != null) {
            gc.setFill(Color.web("#f0b673"));
            gc.setFont(javafx.scene.text.Font.font(javafx.scene.text.Font.getDefault().getFamily(), 10.0 / z));
            gc.fillText(selectedEntityName, minWx, minWy - (6.0 / z));
        }
        gc.restore();
    }

    private boolean supportsPivotEntity(Entity2D entity) {
        return entity != null;
    }

    private boolean isNearPivotHandle(double screenX, double screenY) {
        if (selectedEntity == null) return false;
        double[] viewportPoint = worldToViewport(selectedEntity.getX(), selectedEntity.getY());
        double px = viewportPoint[0];
        double py = viewportPoint[1];
        double pointerX = screenToViewportX(screenX);
        double pointerY = screenToViewportY(screenY);
        double dx = pointerX - px;
        double dy = pointerY - py;
        return dx * dx + dy * dy <= 100;
    }

    private void drawPivotHandleWorld(Entity2D entity, double zoom) {
        double z = Math.max(0.0001, zoom);
        double px = entity.getX();
        double py = entity.getY();
        double arm = 7.0 / z;
        double radius = 3.0 / z;

        gc.setStroke(Color.web("#f7d07a"));
        gc.setLineWidth(1.5 / z);
        gc.strokeLine(px - arm, py, px + arm, py);
        gc.strokeLine(px, py - arm, px, py + arm);
        gc.setFill(Color.web("#f7d07a", 0.8));
        gc.fillOval(px - radius, py - radius, radius * 2.0, radius * 2.0);

        EntityFrame frame = describeEntity(entity);
        if (frame != null) {
            double ox = frame.originX;
            double oy = frame.originY;
            boolean isNonCenter = Math.abs(ox - 0.5) > 0.01 || Math.abs(oy - 0.5) > 0.01;

            if (isNonCenter) {
                double cos = Math.cos(frame.rotationRad);
                double sin = Math.sin(frame.rotationRad);
                double cx = (0.5 - ox) * frame.w * frame.scaleX;
                double cy = (0.5 - oy) * frame.h * frame.scaleY;
                double centerWx = px + cx * cos - cy * sin;
                double centerWy = py + cx * sin + cy * cos;
                double dotR = 2.0 / z;
                gc.setFill(Color.web("#f7d07a", 0.4));
                gc.fillOval(centerWx - dotR, centerWy - dotR, dotR * 2.0, dotR * 2.0);
                gc.setStroke(Color.web("#f7d07a", 0.3));
                gc.setLineWidth(0.8 / z);
                gc.setLineDashes(3.0 / z, 2.0 / z);
                gc.strokeLine(px, py, centerWx, centerWy);
                gc.setLineDashes((double[]) null);
            }

            String label = String.format("(%.2f, %.2f)", ox, oy);
            gc.setFill(Color.web("#f7d07a", 0.7));
            gc.setFont(javafx.scene.text.Font.font(8.0 / z));
            gc.fillText(label, px + arm + (2.0 / z), py - (2.0 / z));
        }
    }

    private void drawOrbitAnchorWorld(String entityName, Entity2D entity, double zoom) {
        double[] anchor = getOrbitAnchor(entityName);
        if (anchor == null || entity == null) return;
        double z = Math.max(0.0001, zoom);
        double ax = anchor[0];
        double ay = anchor[1];
        double ex = entity.getX();
        double ey = entity.getY();
        double arm = 7.0 / z;
        double radius = 3.0 / z;

        gc.setStroke(Color.web("#58d68d", 0.75));
        gc.setLineWidth(1.0 / z);
        gc.setLineDashes(4.0 / z, 3.0 / z);
        gc.strokeLine(ax, ay, ex, ey);
        gc.setLineDashes((double[]) null);

        gc.setStroke(Color.web("#58d68d"));
        gc.setLineWidth(1.5 / z);
        gc.strokeLine(ax - arm, ay, ax + arm, ay);
        gc.strokeLine(ax, ay - arm, ax, ay + arm);
        gc.setFill(Color.web("#58d68d", 0.9));
        gc.fillOval(ax - radius, ay - radius, radius * 2.0, radius * 2.0);
    }

    private PivotDragState buildPivotDragState(Entity2D entity) {
        EntityFrame frame = describeEntity(entity);
        return frame == null ? null : new PivotDragState(frame);
    }

    private void setEntityPivot(Entity2D entity, double pivotX, double pivotY) {
        entity.setOrigin(clampPivot(pivotX), clampPivot(pivotY));
    }

    private static double clampPivot(double v) {
        return clamp(v, PIVOT_MIN, PIVOT_MAX);
    }

    private static double normalizeScale(double value) {
        if (Math.abs(value) >= TRANSFORM_EPSILON) return value;
        return value < 0.0 ? -TRANSFORM_EPSILON : TRANSFORM_EPSILON;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void applyCameraTransform() {
        gc.translate(-camera.getX(), -camera.getY());
        gc.scale(camera.getZoom(), camera.getZoom());
    }

    private double[] worldToViewport(double worldX, double worldY) {
        Affine transform = new Affine();
        transform.appendTranslation(-camera.getX(), -camera.getY());
        transform.appendScale(camera.getZoom(), camera.getZoom());
        var p = transform.transform(worldX, worldY);
        return new double[]{p.getX(), p.getY()};
    }

    public void fitToContent() {
        if (scene == null) return;
        updateViewportTransform(Math.max(1.0, canvas.getWidth()), Math.max(1.0, canvas.getHeight()));

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (var entity : scene.getChildren()) {
            double x = entity.getX();
            double y = entity.getY();
            minX = Math.min(minX, x - 50);
            minY = Math.min(minY, y - 50);
            maxX = Math.max(maxX, x + 50);
            maxY = Math.max(maxY, y + 50);
        }

        if (minX == Double.MAX_VALUE) return;

        double w = viewportLogicalWidth;
        double h = viewportLogicalHeight;
        double contentW = maxX - minX;
        double contentH = maxY - minY;

        double zx = w / Math.max(1, contentW);
        double zy = h / Math.max(1, contentH);
        double z = Math.max(0.1, Math.min(zx, zy) * 0.8);

        camera.setZoom(z);
        camera.setPosition(
            minX + contentW / 2 - w / (2 * z),
            minY + contentH / 2 - h / (2 * z)
        );
        render();
    }
}
