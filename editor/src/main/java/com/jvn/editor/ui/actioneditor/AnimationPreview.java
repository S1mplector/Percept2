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
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class AnimationPreview extends VBox {
    private static final double PIVOT_MIN = 0.0;
    private static final double PIVOT_MAX = 1.0;
    private static final double TRANSFORM_EPSILON = 1e-6;
    private static final double VIEW_ZOOM_MIN = 0.35;
    private static final double VIEW_ZOOM_MAX = 6.0;

    public enum ScrollZoomMode {
        VIEW,
        CAMERA
    }

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

    private boolean snapToGridEnabled = false;
    private double snapGridSize = 10.0;
    private boolean snapToEntityEnabled = false;
    private static final double ENTITY_SNAP_THRESHOLD = 8.0;

    private Consumer<String> onEntitySelected;
    private BiConsumer<String, double[]> onEntityMoved;
    private BiConsumer<String, double[]> onEntityPivotChanged;
    private BiConsumer<String, Double> onEntityRotationChanged;
    private Consumer<double[]> onCameraStateChanged;
    private ProjectViewportSpec.Dimensions viewportSpec =
        new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
    private double viewportScale = 1.0;
    private double viewportOffsetX = 0.0;
    private double viewportOffsetY = 0.0;
    private double viewportLogicalWidth = ProjectViewportSpec.DEFAULT_WIDTH;
    private double viewportLogicalHeight = ProjectViewportSpec.DEFAULT_HEIGHT;
    private double displayScale = 1.0;
    private double displayOffsetX = 0.0;
    private double displayOffsetY = 0.0;
    private double displayMinX = 0.0;
    private double displayMinY = 0.0;
    private double displayWidth = ProjectViewportSpec.DEFAULT_WIDTH;
    private double displayHeight = ProjectViewportSpec.DEFAULT_HEIGHT;
    private double viewZoomFactor = 1.0;
    private double viewPanX = 0.0;
    private double viewPanY = 0.0;
    private ScrollZoomMode scrollZoomMode = ScrollZoomMode.VIEW;
    private java.io.File projectRoot;
    private final Map<String, double[]> sourceImageSizeCache = new HashMap<>();

    public void setProjectRoot(java.io.File root) {
        projectRoot = root;
        sourceImageSizeCache.clear();
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

        gc.save();
        gc.translate(displayOffsetX, displayOffsetY);
        gc.scale(displayScale, displayScale);
        gc.translate(-displayMinX, -displayMinY);

        gc.setFill(Color.web("#0e0e0e"));
        gc.fillRect(displayMinX, displayMinY, displayWidth, displayHeight);
        drawGrid(displayMinX, displayMinY, displayWidth, displayHeight);

        blitter.setViewport(viewportLogicalWidth, viewportLogicalHeight);
        if (scene != null) {
            Camera2D activeCamera = scene.getCamera();
            scene.setCamera(null);
            try {
                if (onionSkinning && project != null) drawOnionSkins();
                scene.render(blitter, viewportLogicalWidth, viewportLogicalHeight);
                drawMotionPaths();
                if (selectedEntity != null) drawSelectionHighlight(selectedEntity);
            } finally {
                scene.setCamera(activeCamera);
            }
        } else {
            gc.setFill(Color.web("#a0a0a0"));
            gc.fillText("No scene loaded", displayMinX + displayWidth / 2 - 40, displayMinY + displayHeight / 2);
        }
        gc.restore();
        drawRuntimeFrame();
        drawCameraHud();

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

    private void drawRuntimeFrame() {
        double z = Math.max(0.0001, camera.getZoom());
        double left = camera.getX();
        double top = camera.getY();
        double right = left + viewportLogicalWidth / z;
        double bottom = top + viewportLogicalHeight / z;
        double x0 = worldToScreenX(left);
        double y0 = worldToScreenY(top);
        double x1 = worldToScreenX(right);
        double y1 = worldToScreenY(bottom);
        double x = Math.min(x0, x1);
        double y = Math.min(y0, y1);
        double frameW = Math.abs(x1 - x0);
        double frameH = Math.abs(y1 - y0);

        gc.save();
        gc.setStroke(Color.web("#ff4d4d", 0.95));
        gc.setLineWidth(2.0);
        gc.strokeRect(x + 0.5, y + 0.5, Math.max(0, frameW - 1.0), Math.max(0, frameH - 1.0));
        gc.setFill(Color.web("#ff7a7a", 0.9));
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 10));
        gc.fillText("Runtime Frame", x + 6, Math.max(12, y - 6));
        gc.restore();
    }

    private void drawCameraHud() {
        String modeText = scrollZoomMode == ScrollZoomMode.CAMERA ? "Camera Zoom" : "View Zoom";
        String hud = String.format(
            "Wheel: %s  |  Cam (x=%.1f y=%.1f z=%.2f)  |  View %.2fx",
            modeText, camera.getX(), camera.getY(), camera.getZoom(), viewZoomFactor
        );
        gc.save();
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 10));
        double width = Math.max(280.0, hud.length() * 6.0 + 12.0);
        gc.setFill(Color.web("#050505", 0.75));
        gc.fillRoundRect(8, 8, width, 18, 6, 6);
        gc.setFill(Color.web("#d3dae8", 0.95));
        gc.fillText(hud, 14, 21);
        gc.restore();
    }

    private void updateViewportTransform(double canvasW, double canvasH) {
        if (viewportSpec == null) {
            viewportSpec = new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
        }
        viewportLogicalWidth = Math.max(1.0, viewportSpec.width());
        viewportLogicalHeight = Math.max(1.0, viewportSpec.height());

        double[] bounds = computeDisplayBoundsWorld();
        double boundsMinX = bounds[0];
        double boundsMinY = bounds[1];
        double boundsMaxX = bounds[2];
        double boundsMaxY = bounds[3];
        double boundsWidth = Math.max(1.0, boundsMaxX - boundsMinX);
        double boundsHeight = Math.max(1.0, boundsMaxY - boundsMinY);

        var fit = ViewportScaler2D.fit(boundsWidth, boundsHeight, canvasW, canvasH);
        double fitScale = Math.max(1e-6, fit.scale());
        displayScale = fitScale * viewZoomFactor;

        double baseCenterX = (boundsMinX + boundsMaxX) * 0.5;
        double baseCenterY = (boundsMinY + boundsMaxY) * 0.5;
        double centerX = baseCenterX + viewPanX;
        double centerY = baseCenterY + viewPanY;

        displayWidth = Math.max(1.0, canvasW / Math.max(1e-6, displayScale));
        displayHeight = Math.max(1.0, canvasH / Math.max(1e-6, displayScale));
        displayMinX = centerX - displayWidth * 0.5;
        displayMinY = centerY - displayHeight * 0.5;
        displayOffsetX = 0.0;
        displayOffsetY = 0.0;

        // Keep legacy fields aligned with current visible world transform.
        viewportScale = displayScale;
        viewportOffsetX = worldToScreenX(camera.getX());
        viewportOffsetY = worldToScreenY(camera.getY());
    }

    private double[] computeDisplayBoundsWorld() {
        double z = Math.max(0.0001, camera.getZoom());
        double runtimeLeft = camera.getX();
        double runtimeTop = camera.getY();
        double runtimeRight = runtimeLeft + viewportLogicalWidth / z;
        double runtimeBottom = runtimeTop + viewportLogicalHeight / z;

        double minX = runtimeLeft;
        double minY = runtimeTop;
        double maxX = runtimeRight;
        double maxY = runtimeBottom;

        if (scene != null) {
            for (Entity2D entity : scene.getChildren()) {
                if (entity == null || !entity.isVisible()) continue;
                double[] corners = getEntityCorners(entity);
                if (corners.length < 8) continue;
                for (int i = 0; i < 4; i++) {
                    double x = corners[i * 2];
                    double y = corners[i * 2 + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            includeFullBackgroundSourceBounds(minX, minY, maxX, maxY);
            minX = backgroundBoundsAccumulator[0];
            minY = backgroundBoundsAccumulator[1];
            maxX = backgroundBoundsAccumulator[2];
            maxY = backgroundBoundsAccumulator[3];
        }

        double width = Math.max(1.0, maxX - minX);
        double height = Math.max(1.0, maxY - minY);
        double padX = Math.max(24.0, width * 0.06);
        double padY = Math.max(24.0, height * 0.06);
        return new double[] { minX - padX, minY - padY, maxX + padX, maxY + padY };
    }

    private final double[] backgroundBoundsAccumulator = new double[4];

    private void includeFullBackgroundSourceBounds(double minX, double minY, double maxX, double maxY) {
        backgroundBoundsAccumulator[0] = minX;
        backgroundBoundsAccumulator[1] = minY;
        backgroundBoundsAccumulator[2] = maxX;
        backgroundBoundsAccumulator[3] = maxY;
        if (scene == null) return;

        Entity2D bestEntity = null;
        double bestRank = -1.0;
        for (Entity2D entity : scene.getChildren()) {
            if (!(entity instanceof com.jvn.core.scene2d.Sprite2D sprite)) continue;
            String path = sprite.getImagePath();
            if (path == null || path.isBlank()) continue;
            double[] natural = resolveImageSize(path);
            if (natural == null) continue;
            double spriteW = Math.max(1.0, sprite.getWidth());
            double spriteH = Math.max(1.0, sprite.getHeight());
            if (natural[0] <= spriteW * 1.01 && natural[1] <= spriteH * 1.01) continue;

            boolean likelyBackground = isLikelyBackground(path)
                || spriteW * spriteH >= viewportLogicalWidth * viewportLogicalHeight * 0.35;
            if (!likelyBackground) continue;
            double rank = natural[0] * natural[1];
            if (rank > bestRank) {
                bestRank = rank;
                bestEntity = entity;
            }
        }

        if (!(bestEntity instanceof com.jvn.core.scene2d.Sprite2D bestSprite)) return;
        double[] natural = resolveImageSize(bestSprite.getImagePath());
        if (natural == null) return;
        EntityFrame frame = describeEntity(bestEntity);
        if (frame == null) return;

        EntityFrame fullSourceFrame = new EntityFrame(
            frame.x,
            frame.y,
            natural[0],
            natural[1],
            frame.originX,
            frame.originY,
            frame.scaleX,
            frame.scaleY,
            frame.rotationRad
        );
        double[] corners = computeWorldCorners(fullSourceFrame);
        for (int i = 0; i < 4; i++) {
            double x = corners[i * 2];
            double y = corners[i * 2 + 1];
            backgroundBoundsAccumulator[0] = Math.min(backgroundBoundsAccumulator[0], x);
            backgroundBoundsAccumulator[1] = Math.min(backgroundBoundsAccumulator[1], y);
            backgroundBoundsAccumulator[2] = Math.max(backgroundBoundsAccumulator[2], x);
            backgroundBoundsAccumulator[3] = Math.max(backgroundBoundsAccumulator[3], y);
        }
    }

    private boolean isLikelyBackground(String path) {
        String p = path == null ? "" : path.toLowerCase();
        return p.contains("background") || p.contains("/bg") || p.contains("_bg") || p.contains("backdrop");
    }

    private double[] resolveImageSize(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;
        String key = imagePath.trim();
        if (sourceImageSizeCache.containsKey(key)) {
            double[] cached = sourceImageSizeCache.get(key);
            return cached[0] > 0 && cached[1] > 0 ? cached : null;
        }
        Image image = loadImageByPath(key);
        double[] size;
        if (image != null && image.getWidth() > 1 && image.getHeight() > 1) {
            size = new double[] { image.getWidth(), image.getHeight() };
        } else {
            size = new double[] { -1, -1 };
        }
        sourceImageSizeCache.put(key, size);
        return size[0] > 0 && size[1] > 0 ? size : null;
    }

    private Image loadImageByPath(String path) {
        try {
            java.net.URL classpath = getClass().getClassLoader().getResource(path);
            if (classpath != null) return new Image(classpath.toExternalForm(), false);
        } catch (Exception ignored) {
        }
        try {
            java.io.File absolute = new java.io.File(path);
            if (absolute.isAbsolute() && absolute.isFile()) {
                return new Image(absolute.toURI().toString(), false);
            }
        } catch (Exception ignored) {
        }
        try {
            if (projectRoot != null) {
                java.io.File relative = new java.io.File(projectRoot, path);
                if (relative.isFile()) return new Image(relative.toURI().toString(), false);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void drawMotionPaths() {
        if (project == null) return;
        double z = Math.max(1e-6, displayScale);

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
        double z = Math.max(1e-6, displayScale);
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

    private void drawGrid(double minX, double minY, double w, double h) {
        double step = 100.0;
        if (step * displayScale < 8.0) return;
        double maxX = minX + w;
        double maxY = minY + h;
        double startX = Math.floor(minX / step) * step;
        double startY = Math.floor(minY / step) * step;

        gc.setStroke(Color.color(1, 1, 1, 0.04));
        gc.setLineWidth(1.0 / Math.max(1e-6, displayScale));
        for (double x = startX; x <= maxX; x += step) {
            gc.strokeLine(x, minY, x, maxY);
        }
        for (double y = startY; y <= maxY; y += step) {
            gc.strokeLine(minX, y, maxX, y);
        }
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnEntityMoved(BiConsumer<String, double[]> callback) { this.onEntityMoved = callback; }
    public void setOnEntityPivotChanged(BiConsumer<String, double[]> callback) { this.onEntityPivotChanged = callback; }
    public void setOnEntityRotationChanged(BiConsumer<String, Double> callback) { this.onEntityRotationChanged = callback; }
    public void setOnCameraStateChanged(Consumer<double[]> callback) { this.onCameraStateChanged = callback; }

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

    public boolean isSnapToGridEnabled() { return snapToGridEnabled; }
    public void setSnapToGridEnabled(boolean enabled) { this.snapToGridEnabled = enabled; }
    public double getSnapGridSize() { return snapGridSize; }
    public void setSnapGridSize(double size) { this.snapGridSize = Math.max(1, size); }
    public boolean isSnapToEntityEnabled() { return snapToEntityEnabled; }
    public void setSnapToEntityEnabled(boolean enabled) { this.snapToEntityEnabled = enabled; }
    public ScrollZoomMode getScrollZoomMode() { return scrollZoomMode; }
    public void setScrollZoomMode(ScrollZoomMode mode) {
        this.scrollZoomMode = mode == null ? ScrollZoomMode.VIEW : mode;
        render();
    }

    private double[] applySnap(double x, double y) {
        if (snapToGridEnabled && snapGridSize > 0) {
            x = Math.round(x / snapGridSize) * snapGridSize;
            y = Math.round(y / snapGridSize) * snapGridSize;
        }
        if (snapToEntityEnabled && scene != null && selectedEntityName != null) {
            double bestDx = Double.MAX_VALUE, bestDy = Double.MAX_VALUE;
            double snapX = x, snapY = y;
            for (var entry : scene.exportNamed().entrySet()) {
                if (entry.getKey().equals(selectedEntityName)) continue;
                Entity2D other = entry.getValue();
                if (other == null) continue;
                double dx = Math.abs(other.getX() - x);
                double dy = Math.abs(other.getY() - y);
                if (dx < ENTITY_SNAP_THRESHOLD && dx < bestDx) { bestDx = dx; snapX = other.getX(); }
                if (dy < ENTITY_SNAP_THRESHOLD && dy < bestDy) { bestDy = dy; snapY = other.getY(); }
            }
            x = snapX;
            y = snapY;
        }
        return new double[]{x, y};
    }
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
        double right = displayOffsetX + displayWidth * displayScale;
        double bottom = displayOffsetY + displayHeight * displayScale;
        return screenX >= displayOffsetX && screenX <= right && screenY >= displayOffsetY && screenY <= bottom;
    }

    private double screenToViewportX(double screenX) {
        return displayMinX + (screenX - displayOffsetX) / Math.max(1e-6, displayScale);
    }

    private double screenToViewportY(double screenY) {
        return displayMinY + (screenY - displayOffsetY) / Math.max(1e-6, displayScale);
    }

    private double[] screenToWorld(double screenX, double screenY) {
        return new double[]{ screenToViewportX(screenX), screenToViewportY(screenY) };
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
        double ax = worldToScreenX(anchor[0]);
        double ay = worldToScreenY(anchor[1]);
        double pointerX = screenX;
        double pointerY = screenY;
        double dx = pointerX - ax;
        double dy = pointerY - ay;
        return dx * dx + dy * dy <= 100;
    }

    private void setupMouseControls() {
        canvas.setOnScroll(e -> {
            if (!isInsideViewport(e.getX(), e.getY())) return;
            double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
            if (scrollZoomMode == ScrollZoomMode.CAMERA) {
                double z = camera.getZoom();
                double[] world = screenToWorld(e.getX(), e.getY());
                double worldX = world[0];
                double worldY = world[1];
                double relativeX = (worldX - camera.getX()) * z;
                double relativeY = (worldY - camera.getY()) * z;
                double newZ = Math.max(0.1, Math.min(5.0, z * factor));
                camera.setZoom(newZ);
                camera.setPosition(worldX - relativeX / newZ, worldY - relativeY / newZ);
            } else {
                double[] worldBefore = screenToWorld(e.getX(), e.getY());
                viewZoomFactor = clamp(viewZoomFactor * factor, VIEW_ZOOM_MIN, VIEW_ZOOM_MAX);
                updateViewportTransform(Math.max(1.0, canvas.getWidth()), Math.max(1.0, canvas.getHeight()));
                double[] worldAfter = screenToWorld(e.getX(), e.getY());
                viewPanX += worldBefore[0] - worldAfter[0];
                viewPanY += worldBefore[1] - worldAfter[1];
            }
            notifyCameraStateChanged();
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
                double dx = (e.getX() - panStart[0]) / Math.max(1e-6, displayScale);
                double dy = (e.getY() - panStart[1]) / Math.max(1e-6, displayScale);
                panStart[0] = e.getX();
                panStart[1] = e.getY();
                if (scrollZoomMode == ScrollZoomMode.CAMERA) {
                    camera.setPosition(
                        camera.getX() - dx,
                        camera.getY() - dy
                    );
                } else {
                    viewPanX -= dx;
                    viewPanY -= dy;
                }
                notifyCameraStateChanged();
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
                double newX = selectedEntity.getX() + dx;
                double newY = selectedEntity.getY() + dy;
                double[] snapped = applySnap(newX, newY);
                selectedEntity.setPosition(snapped[0], snapped[1]);
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
        double z = Math.max(1e-6, displayScale);
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
        double px = worldToScreenX(selectedEntity.getX());
        double py = worldToScreenY(selectedEntity.getY());
        double pointerX = screenX;
        double pointerY = screenY;
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
        // Scene is rendered in world-overview mode; runtime camera is represented by the red frame.
    }

    private double[] worldToViewport(double worldX, double worldY) {
        return new double[]{worldX, worldY};
    }

    private double worldToScreenX(double worldX) {
        return displayOffsetX + (worldX - displayMinX) * displayScale;
    }

    private double worldToScreenY(double worldY) {
        return displayOffsetY + (worldY - displayMinY) * displayScale;
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
        notifyCameraStateChanged();
        render();
    }

    private void notifyCameraStateChanged() {
        if (onCameraStateChanged != null) {
            onCameraStateChanged.accept(new double[] { camera.getX(), camera.getY(), camera.getZoom() });
        }
    }
}
