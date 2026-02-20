package com.jvn.editor.ui.actioneditor;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.input.Input;
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
        } else {
            gc.setFill(Color.web("#6c7086"));
            gc.fillText("No scene loaded", w / 2 - 40, h / 2);
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
            }
        });

        canvas.setOnMouseReleased(e -> {
            panning[0] = false;
        });
    }

    public void fitToContent() {
        if (scene == null) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

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
