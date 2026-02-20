package com.jvn.editor.ui.actioneditor;

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

    private Entity2D selectedEntity;
    private String selectedEntityName;
    private boolean draggingEntity = false;
    private double dragEntityStartX, dragEntityStartY;

    private Consumer<String> onEntitySelected;
    private BiConsumer<String, double[]> onEntityMoved;

    public AnimationPreview() {
        canvas = new Canvas(600, 300);
        gc = canvas.getGraphicsContext2D();
        blitter = new FxBlitter2D(gc);
        input = new Input();
        camera = new Camera2D();

        Pane container = new Pane(canvas);
        VBox.setVgrow(container, Priority.ALWAYS);

        getChildren().add(container);
        setStyle("-fx-background-color: #11111b;");

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

        gc.setFill(Color.web("#1e1e2e"));
        gc.fillRect(0, 0, w, h);

        drawGrid(w, h);

        blitter.setViewport(w, h);
        if (scene != null) {
            scene.render(blitter, w, h);
            drawMotionPaths();
            if (selectedEntity != null) drawSelectionHighlight(selectedEntity);
        } else {
            gc.setFill(Color.web("#6c7086"));
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

            gc.setStroke(Color.web("#89b4fa", 0.5));
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

            gc.setFill(Color.web("#89b4fa", 0.8));
            for (SplinePath.Point pt : controlPoints) {
                double sx = (pt.x - camera.getX()) * z;
                double sy = (pt.y - camera.getY()) * z;
                gc.fillOval(sx - 3, sy - 3, 6, 6);
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

    public Entity2D getSelectedEntity() { return selectedEntity; }

    private String findEntityNameAt(double screenX, double screenY) {
        if (scene == null) return null;
        double z = camera.getZoom();
        double worldX = camera.getX() + screenX / z;
        double worldY = camera.getY() + screenY / z;

        String closestName = null;
        Entity2D closestEntity = null;
        double closestDist = 40.0 / z;
        for (var entry : scene.exportNamed().entrySet()) {
            Entity2D entity = entry.getValue();
            double dx = worldX - entity.getX();
            double dy = worldY - entity.getY();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < closestDist) {
                closestDist = dist;
                closestName = entry.getKey();
                closestEntity = entity;
            }
        }
        selectedEntity = closestEntity;
        selectedEntityName = closestName;
        return closestName;
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
                String hitName = findEntityNameAt(e.getX(), e.getY());
                if (hitName != null) {
                    draggingEntity = true;
                    dragEntityStartX = e.getX();
                    dragEntityStartY = e.getY();
                    if (onEntitySelected != null) onEntitySelected.accept(hitName);
                    drawSelectionHighlight(selectedEntity);
                } else {
                    selectedEntity = null;
                    selectedEntityName = null;
                    draggingEntity = false;
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
        });
    }

    private void drawSelectionHighlight(Entity2D entity) {
        if (entity == null) return;
        double z = camera.getZoom();
        double sx = (entity.getX() - camera.getX()) * z;
        double sy = (entity.getY() - camera.getY()) * z;
        double size = 30 * z;

        gc.setStroke(Color.web("#f9e2af"));
        gc.setLineWidth(2);
        gc.setLineDashes(6, 4);
        gc.strokeRect(sx - size / 2, sy - size / 2, size, size);
        gc.setLineDashes((double[]) null);
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
