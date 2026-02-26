package com.jvn.editor.ui.actioneditor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class AnimationPreview extends VBox {
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
    private double dragEntityStartX, dragEntityStartY;
    private double orbitRadius = 0.0;
    private boolean orbitToolEnabled = false;
    private boolean orbitAlignRotation = true;
    private final Map<String, double[]> orbitAnchors = new HashMap<>();

    private Consumer<String> onEntitySelected;
    private BiConsumer<String, double[]> onEntityMoved;
    private BiConsumer<String, double[]> onEntityPivotChanged;
    private BiConsumer<String, Double> onEntityRotationChanged;

    public void setProjectRoot(java.io.File root) {
        blitter.setProjectRoot(root);
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
        render();
    }

    public void setProject(AnimationProject project) {
        this.project = project;
    }

    public JesScene2D getJesScene() { return scene; }
    public Camera2D getCamera() { return camera; }

    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#121212"));
        gc.fillRect(0, 0, w, h);

        drawGrid(w, h);

        blitter.setViewport(w, h);
        if (scene != null) {
            if (onionSkinning && project != null) drawOnionSkins();
            scene.render(blitter, w, h);
            drawMotionPaths();
            if (selectedEntity != null) drawSelectionHighlight(selectedEntity);
        } else {
            gc.setFill(Color.web("#a0a0a0"));
            gc.fillText("No scene loaded", w / 2 - 40, h / 2);
        }
    }

    private void drawMotionPaths() {
        if (project == null) return;
        double z = camera.getZoom();

        for (EntityTrack track : project.getTracks()) {
            java.util.List<SplinePath.Point> controlPoints =
                SplinePath.buildControlPoints(track, project.getTotalDurationMs());
            if (controlPoints.size() < 2) continue;

            java.util.List<SplinePath.Point> curve = SplinePath.catmullRom(controlPoints, 16);

            gc.setStroke(Color.web("#4da3ff", 0.5));
            gc.setLineWidth(1.5);
            gc.setLineDashes(4, 3);
            gc.beginPath();
            boolean first = true;
            for (SplinePath.Point pt : curve) {
                double sx = (pt.x - camera.getX()) * z;
                double sy = (pt.y - camera.getY()) * z;
                if (first) { gc.moveTo(sx, sy); first = false; }
                else gc.lineTo(sx, sy);
            }
            gc.stroke();
            gc.setLineDashes((double[]) null);

            gc.setFill(Color.web("#4da3ff", 0.8));
            for (SplinePath.Point pt : controlPoints) {
                double sx = (pt.x - camera.getX()) * z;
                double sy = (pt.y - camera.getY()) * z;
                gc.fillOval(sx - 3, sy - 3, 6, 6);
            }
        }
    }

    private void drawOnionSkins() {
        if (project == null) return;
        double z = camera.getZoom();
        double now = project.getPlayheadMs();
        double dur = project.getTotalDurationMs();
        double step = dur / 20;

        for (EntityTrack track : project.getTracks()) {
            java.util.List<Keyframe> xFrames = track.getKeyframes(PropertyType.X);
            java.util.List<Keyframe> yFrames = track.getKeyframes(PropertyType.Y);
            if (xFrames.isEmpty() && yFrames.isEmpty()) continue;

            for (int i = -onionFrames; i <= onionFrames; i++) {
                if (i == 0) continue;
                double t = now + i * step;
                if (t < 0 || t > dur) continue;

                double x = track.getValueAt(PropertyType.X, t);
                double y = track.getValueAt(PropertyType.Y, t);
                double sx = (x - camera.getX()) * z;
                double sy = (y - camera.getY()) * z;

                double alpha = 0.3 * (1.0 - Math.abs(i) / (double)(onionFrames + 1));
                Color color = i < 0 ? Color.web("#f38ba8", alpha) : Color.web("#58d68d", alpha);

                gc.setStroke(color);
                gc.setLineWidth(1.5);
                double size = 20 * z;
                gc.strokeRect(sx - size / 2, sy - size / 2, size, size);

                gc.setFill(color);
                gc.setFont(javafx.scene.text.Font.font(8));
                gc.fillText(String.format("%.0f", t), sx + size / 2 + 2, sy);
            }
        }
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
        if (changed) render();
    }

    public void clearSelection() {
        boolean changed = selectedEntity != null || selectedEntityName != null;
        selectedEntity = null;
        selectedEntityName = null;
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
        double z = Math.max(0.0001, camera.getZoom());
        double worldX = camera.getX() + screenX / z;
        double worldY = camera.getY() + screenY / z;

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
            double[] bounds = getEntityBounds(entity);
            if (worldX >= bounds[0] && worldX <= bounds[0] + bounds[2]
                    && worldY >= bounds[1] && worldY <= bounds[1] + bounds[3]) {
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

    private double[] getEntityBounds(Entity2D entity) {
        double x = entity.getX();
        double y = entity.getY();
        double w = 40, h = 40; // default fallback size
        double ox = 0.5, oy = 0.5; // default origin

        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            w = sprite.getWidth();
            h = sprite.getHeight();
            ox = sprite.getOriginX();
            oy = sprite.getOriginY();
        } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D charEnt) {
            w = charEnt.getDrawWidth();
            h = charEnt.getDrawHeight();
            ox = charEnt.getOriginX();
            oy = charEnt.getOriginY();
        }
        return new double[]{ x - ox * w, y - oy * h, w, h };
    }

    private double[] screenToWorld(double screenX, double screenY) {
        double z = Math.max(0.0001, camera.getZoom());
        double worldX = camera.getX() + screenX / z;
        double worldY = camera.getY() + screenY / z;
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
        double z = Math.max(0.0001, camera.getZoom());
        double ax = (anchor[0] - camera.getX()) * z;
        double ay = (anchor[1] - camera.getY()) * z;
        double dx = screenX - ax;
        double dy = screenY - ay;
        return dx * dx + dy * dy <= 100;
    }

    private void setupMouseControls() {
        canvas.setOnScroll(e -> {
            double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
            double z = camera.getZoom();
            double worldX = camera.getX() + e.getX() / z;
            double worldY = camera.getY() + e.getY() / z;
            double newZ = Math.max(0.1, Math.min(5.0, z * factor));
            camera.setZoom(newZ);
            camera.setPosition(worldX - e.getX() / newZ, worldY - e.getY() / newZ);
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
                    draggingPivot = true;
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
                }
                render();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (panning[0]) {
                double dx = e.getX() - panStart[0];
                double dy = e.getY() - panStart[1];
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
                double z = camera.getZoom();
                if (z <= 0) return;

                double worldX = camera.getX() + e.getX() / z;
                double worldY = camera.getY() + e.getY() / z;
                double[] bounds = getEntityBounds(selectedEntity);
                double bw = Math.max(1e-6, bounds[2]);
                double bh = Math.max(1e-6, bounds[3]);

                double pivotX = clampPivot((worldX - bounds[0]) / bw);
                double pivotY = clampPivot((worldY - bounds[1]) / bh);
                setEntityPivot(selectedEntity, pivotX, pivotY);

                if (onEntityPivotChanged != null && selectedEntityName != null) {
                    onEntityPivotChanged.accept(selectedEntityName, new double[]{pivotX, pivotY});
                }
                render();
            } else if (draggingEntity && selectedEntity != null) {
                double z = camera.getZoom();
                double dx = (e.getX() - dragEntityStartX) / z;
                double dy = (e.getY() - dragEntityStartY) / z;
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
        });
    }

    private void drawSelectionHighlight(Entity2D entity) {
        if (entity == null) return;
        double z = camera.getZoom();
        double[] bounds = getEntityBounds(entity);
        double sx = (bounds[0] - camera.getX()) * z;
        double sy = (bounds[1] - camera.getY()) * z;
        double sw = bounds[2] * z;
        double sh = bounds[3] * z;

        gc.setStroke(Color.web("#f0b673"));
        gc.setLineWidth(2);
        gc.setLineDashes(6, 4);
        gc.strokeRect(sx - 2, sy - 2, sw + 4, sh + 4);
        gc.setLineDashes((double[]) null);

        if (supportsPivotEntity(entity)) {
            drawPivotHandle(entity);
        }
        if (selectedEntityName != null && hasOrbitAnchor(selectedEntityName)) {
            drawOrbitAnchor(selectedEntityName, entity);
        }

        if (selectedEntityName != null) {
            gc.setFill(Color.web("#f0b673"));
            gc.setFont(javafx.scene.text.Font.font(javafx.scene.text.Font.getDefault().getFamily(), 10));
            gc.fillText(selectedEntityName, sx, sy - 6);
        }
    }

    private boolean supportsPivotEntity(Entity2D entity) {
        return entity instanceof com.jvn.core.scene2d.Sprite2D ||
               entity instanceof com.jvn.core.scene2d.CharacterEntity2D;
    }

    private boolean isNearPivotHandle(double screenX, double screenY) {
        if (selectedEntity == null) return false;
        double z = camera.getZoom();
        if (z <= 0) return false;
        double px = (selectedEntity.getX() - camera.getX()) * z;
        double py = (selectedEntity.getY() - camera.getY()) * z;
        double dx = screenX - px;
        double dy = screenY - py;
        return dx * dx + dy * dy <= 100;
    }

    private void drawPivotHandle(Entity2D entity) {
        double z = camera.getZoom();
        if (z <= 0) return;
        double px = (entity.getX() - camera.getX()) * z;
        double py = (entity.getY() - camera.getY()) * z;

        gc.setStroke(Color.web("#f7d07a"));
        gc.setLineWidth(1.5);
        gc.strokeLine(px - 7, py, px + 7, py);
        gc.strokeLine(px, py - 7, px, py + 7);
        gc.setFill(Color.web("#f7d07a", 0.8));
        gc.fillOval(px - 3, py - 3, 6, 6);
    }

    private void drawOrbitAnchor(String entityName, Entity2D entity) {
        double[] anchor = getOrbitAnchor(entityName);
        if (anchor == null || entity == null) return;
        double z = Math.max(0.0001, camera.getZoom());
        double ax = (anchor[0] - camera.getX()) * z;
        double ay = (anchor[1] - camera.getY()) * z;
        double ex = (entity.getX() - camera.getX()) * z;
        double ey = (entity.getY() - camera.getY()) * z;

        gc.setStroke(Color.web("#58d68d", 0.75));
        gc.setLineWidth(1.0);
        gc.setLineDashes(4, 3);
        gc.strokeLine(ax, ay, ex, ey);
        gc.setLineDashes((double[]) null);

        gc.setStroke(Color.web("#58d68d"));
        gc.setLineWidth(1.5);
        gc.strokeLine(ax - 7, ay, ax + 7, ay);
        gc.strokeLine(ax, ay - 7, ax, ay + 7);
        gc.setFill(Color.web("#58d68d", 0.9));
        gc.fillOval(ax - 3, ay - 3, 6, 6);
    }

    private void setEntityPivot(Entity2D entity, double pivotX, double pivotY) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            sprite.setOrigin(pivotX, pivotY);
        } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D character) {
            character.setOrigin(pivotX, pivotY);
        }
    }

    private static double clampPivot(double v) {
        return Math.max(-1.0, Math.min(2.0, v));
    }

    public void fitToContent() {
        if (scene == null) return;

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

        double w = canvas.getWidth();
        double h = canvas.getHeight();
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
