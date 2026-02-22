package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

import com.jvn.core.animation.Easing;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/**
 * Visual easing curve editor. Draws the selected easing function as a curve
 * on a small canvas, showing the input→output mapping from (0,0) to (1,1).
 * When CUSTOM easing is selected, two Bezier control point handles become
 * draggable, allowing the user to define a custom cubic-bezier(cx1,cy1,cx2,cy2) curve.
 */
public class EasingCurveEditor extends Pane {

    private static final double PADDING = 20;
    private static final double HANDLE_RADIUS = 5;
    private static final Color BG_COLOR = Color.web("#0e0e0e");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color AXIS_COLOR = Color.web("#3a3a3a");
    private static final Color CURVE_COLOR = Color.web("#4da3ff");
    private static final Color LABEL_COLOR = Color.web("#666");
    private static final Color POINT_COLOR = Color.web("#f0b673");
    private static final Color LINEAR_COLOR = Color.web("#333");
    private static final Color HANDLE_COLOR = Color.web("#ff6b6b");
    private static final Color HANDLE2_COLOR = Color.web("#51cf66");
    private static final Color TANGENT_COLOR = Color.web("#555");

    private final Canvas canvas;
    private Easing.Type easingType = Easing.Type.LINEAR;

    // Custom Bezier control points (CSS cubic-bezier format: x in [0,1], y can overshoot)
    private double cx1 = 0.25, cy1 = 0.1;
    private double cx2 = 0.25, cy2 = 1.0;

    // Drag state
    private int draggingHandle = 0; // 0=none, 1=P1, 2=P2

    // Callback when user drags control points
    private Consumer<double[]> onBezierChanged;

    public EasingCurveEditor() {
        canvas = new Canvas(180, 140);
        getChildren().add(canvas);

        setPrefSize(180, 140);
        setMinSize(100, 80);
        setMaxSize(Double.MAX_VALUE, 180);

        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            draw();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            draw();
        });

        canvas.setOnMousePressed(e -> {
            if (easingType != Easing.Type.CUSTOM) return;
            double[] plot = getPlotBounds();
            double p1x = plot[0] + cx1 * plot[2];
            double p1y = plot[1] + (1 - cy1) * plot[3];
            double p2x = plot[0] + cx2 * plot[2];
            double p2y = plot[1] + (1 - cy2) * plot[3];

            double d1 = Math.hypot(e.getX() - p1x, e.getY() - p1y);
            double d2 = Math.hypot(e.getX() - p2x, e.getY() - p2y);

            if (d1 < HANDLE_RADIUS + 4 && d1 <= d2) draggingHandle = 1;
            else if (d2 < HANDLE_RADIUS + 4) draggingHandle = 2;
            else draggingHandle = 0;
        });

        canvas.setOnMouseDragged(e -> {
            if (draggingHandle == 0) return;
            double[] plot = getPlotBounds();
            double nx = Math.max(0, Math.min(1, (e.getX() - plot[0]) / plot[2]));
            double ny = 1 - (e.getY() - plot[1]) / plot[3]; // allow overshoot
            ny = Math.max(-0.5, Math.min(1.5, ny));

            if (draggingHandle == 1) { cx1 = nx; cy1 = ny; }
            else { cx2 = nx; cy2 = ny; }

            draw();
            if (onBezierChanged != null) {
                onBezierChanged.accept(new double[]{ cx1, cy1, cx2, cy2 });
            }
        });

        canvas.setOnMouseReleased(e -> draggingHandle = 0);

        draw();
    }

    public void setEasingType(Easing.Type type) {
        this.easingType = type != null ? type : Easing.Type.LINEAR;
        draw();
    }

    public Easing.Type getEasingType() {
        return easingType;
    }

    public void setBezierParams(double cx1, double cy1, double cx2, double cy2) {
        this.cx1 = cx1; this.cy1 = cy1;
        this.cx2 = cx2; this.cy2 = cy2;
        draw();
    }

    public double[] getBezierParams() {
        return new double[]{ cx1, cy1, cx2, cy2 };
    }

    public void setOnBezierChanged(Consumer<double[]> callback) {
        this.onBezierChanged = callback;
    }

    private double[] getPlotBounds() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        return new double[]{ PADDING, 8, w - PADDING - 8, h - PADDING - 8 };
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        double[] plot = getPlotBounds();
        double plotX = plot[0], plotY = plot[1], plotW = plot[2], plotH = plot[3];
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

        boolean isCustom = easingType == Easing.Type.CUSTOM;
        int steps = Math.max(60, (int) plotW);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double v = isCustom
                ? Easing.cubicBezier(cx1, cy1, cx2, cy2, t)
                : Easing.apply(easingType, t);

            double sx = plotX + t * plotW;
            double sy = plotY + (1 - v) * plotH;
            sy = Math.max(plotY - 10, Math.min(plotY + plotH + 10, sy));

            if (i == 0) gc.moveTo(sx, sy);
            else gc.lineTo(sx, sy);
        }
        gc.stroke();

        // Start and end points
        gc.setFill(POINT_COLOR);
        double startSx = plotX;
        double startSy = plotY + plotH;
        double endSx = plotX + plotW;
        double endSy = plotY;
        gc.fillOval(startSx - 3, startSy - 3, 6, 6);
        gc.fillOval(endSx - 3, endSy - 3, 6, 6);

        // Draw control point handles when CUSTOM
        if (isCustom) {
            double p1x = plotX + cx1 * plotW;
            double p1y = plotY + (1 - cy1) * plotH;
            double p2x = plotX + cx2 * plotW;
            double p2y = plotY + (1 - cy2) * plotH;

            // Tangent lines from endpoints to control points
            gc.setStroke(TANGENT_COLOR);
            gc.setLineWidth(1);
            gc.setLineDashes(3, 2);
            gc.strokeLine(startSx, startSy, p1x, p1y);
            gc.strokeLine(endSx, endSy, p2x, p2y);
            gc.setLineDashes((double[]) null);

            // P1 handle (red)
            gc.setFill(HANDLE_COLOR);
            gc.fillOval(p1x - HANDLE_RADIUS, p1y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
            gc.strokeOval(p1x - HANDLE_RADIUS, p1y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);

            // P2 handle (green)
            gc.setFill(HANDLE2_COLOR);
            gc.fillOval(p2x - HANDLE_RADIUS, p2y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1);
            gc.strokeOval(p2x - HANDLE_RADIUS, p2y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);

            // Bezier param label
            gc.setFill(Color.web("#aaa"));
            gc.setFont(Font.font(Font.getDefault().getFamily(), 9));
            gc.fillText(String.format("cubic-bezier(%.2f, %.2f, %.2f, %.2f)", cx1, cy1, cx2, cy2),
                plotX + 2, plotY + plotH - 4);
        } else {
            // Easing name label
            gc.setFill(Color.web("#888"));
            gc.setFont(Font.font(Font.getDefault().getFamily(), 9));
            String label = easingType.name().replace("EASE_", "").replace("_", " ");
            gc.fillText(label, plotX + 4, plotY + plotH - 4);
        }

        // Axis labels
        gc.setFill(LABEL_COLOR);
        gc.setFont(Font.font(Font.getDefault().getFamily(), 9));
        gc.fillText("0", plotX - 2, plotY + plotH + 12);
        gc.fillText("1", plotX + plotW - 4, plotY + plotH + 12);
        gc.fillText("1", plotX - 12, plotY + 4);
        gc.fillText("0", plotX - 12, plotY + plotH + 4);
    }
}
