package com.jvn.editor.ui.actioneditor;

import com.jvn.core.animation.Easing;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Visual easing curve preview. Draws the selected easing function as a curve
 * on a small canvas, showing the input→output mapping from (0,0) to (1,1).
 * Integrates into the KeyframeEditor below the easing dropdown.
 */
public class EasingCurveEditor extends Pane {

    private static final double PADDING = 20;
    private static final Color BG_COLOR = Color.web("#0e0e0e");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color AXIS_COLOR = Color.web("#3a3a3a");
    private static final Color CURVE_COLOR = Color.web("#4da3ff");
    private static final Color LABEL_COLOR = Color.web("#666");
    private static final Color POINT_COLOR = Color.web("#f0b673");
    private static final Color LINEAR_COLOR = Color.web("#333");

    private final Canvas canvas;
    private Easing.Type easingType = Easing.Type.LINEAR;

    public EasingCurveEditor() {
        canvas = new Canvas(180, 120);
        getChildren().add(canvas);

        setPrefSize(180, 120);
        setMinSize(100, 80);
        setMaxSize(Double.MAX_VALUE, 160);

        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            draw();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            draw();
        });

        draw();
    }

    public void setEasingType(Easing.Type type) {
        this.easingType = type != null ? type : Easing.Type.LINEAR;
        draw();
    }

    public Easing.Type getEasingType() {
        return easingType;
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Background
        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        double plotX = PADDING;
        double plotY = 8;
        double plotW = w - PADDING - 8;
        double plotH = h - PADDING - 8;

        if (plotW < 20 || plotH < 20) return;

        // Grid lines (0.25 intervals)
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (int i = 1; i <= 3; i++) {
            double frac = i / 4.0;
            double gx = plotX + frac * plotW;
            double gy = plotY + (1 - frac) * plotH;
            gc.strokeLine(gx, plotY, gx, plotY + plotH);
            gc.strokeLine(plotX, gy, plotX + plotW, gy);
        }

        // Axes
        gc.setStroke(AXIS_COLOR);
        gc.setLineWidth(1);
        gc.strokeRect(plotX, plotY, plotW, plotH);

        // Linear reference line (diagonal)
        gc.setStroke(LINEAR_COLOR);
        gc.setLineWidth(1);
        gc.setLineDashes(4, 3);
        gc.strokeLine(plotX, plotY + plotH, plotX + plotW, plotY);
        gc.setLineDashes((double[]) null);

        // Draw the easing curve
        gc.setStroke(CURVE_COLOR);
        gc.setLineWidth(2);
        gc.beginPath();

        int steps = Math.max(60, (int) plotW);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double v = Easing.apply(easingType, t);

            double sx = plotX + t * plotW;
            double sy = plotY + (1 - v) * plotH;

            // Clamp y to reasonable visible range (for elastic/back overshoots)
            sy = Math.max(plotY - 10, Math.min(plotY + plotH + 10, sy));

            if (i == 0) gc.moveTo(sx, sy);
            else gc.lineTo(sx, sy);
        }
        gc.stroke();

        // Start and end points
        gc.setFill(POINT_COLOR);
        double startX = plotX;
        double startY = plotY + plotH;
        double endX = plotX + plotW;
        double endY = plotY;
        gc.fillOval(startX - 3, startY - 3, 6, 6);
        gc.fillOval(endX - 3, endY - 3, 6, 6);

        // Axis labels
        gc.setFill(LABEL_COLOR);
        gc.setFont(Font.font("Arial", 9));
        gc.fillText("0", plotX - 2, plotY + plotH + 12);
        gc.fillText("1", plotX + plotW - 4, plotY + plotH + 12);
        gc.fillText("1", plotX - 12, plotY + 4);
        gc.fillText("0", plotX - 12, plotY + plotH + 4);

        // Easing name
        gc.setFill(Color.web("#888"));
        gc.setFont(Font.font("Arial", 9));
        String label = easingType.name().replace("EASE_", "").replace("_", " ");
        gc.fillText(label, plotX + 4, plotY + plotH - 4);
    }
}
