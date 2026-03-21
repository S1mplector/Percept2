package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.Cursor;
import javafx.scene.layout.Pane;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Font;

/**
 * Visual easing curve editor. Draws the selected easing function as a curve
 * on a small canvas, showing the input→output mapping from (0,0) to (1,1).
 * When CUSTOM easing is selected, two Bezier control point handles become
 * draggable, allowing the user to define a custom cubic-bezier(cx1,cy1,cx2,cy2) curve.
 */
public class EasingCurveEditor extends Pane {
    private static final double COMPACT_WIDTH = 520;
    private static final double COMPACT_HEIGHT = 140;
    private static final double EXPANDED_HEIGHT = 320;
    private static final double MIN_WIDTH = 240;

    private static final double H_PADDING = 28;
    private static final double TOP_PADDING = 16;
    private static final double BOTTOM_PADDING = 28;
    private static final double HANDLE_RADIUS = 6.5;
    private static final double HANDLE_HIT_RADIUS = 16;
    private static final double SNAP_INCREMENT = 0.05;

    private static final Color BG_COLOR = Color.web("#0e0e0e");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color AXIS_COLOR = Color.web("#595959");
    private static final Color CURVE_COLOR = Color.web("#d9dee6");
    private static final Color LABEL_COLOR = Color.web("#787878");
    private static final Color POINT_COLOR = Color.web("#f0b673");
    private static final Color LINEAR_COLOR = Color.web("#4a4a4a");
    private static final Color HANDLE_COLOR = Color.web("#ff8f6b");
    private static final Color HANDLE2_COLOR = Color.web("#6ddc82");
    private static final Color TANGENT_COLOR = Color.web("#707070");
    private static final Color HOVER_GUIDE_COLOR = Color.web("#7d8ea5", 0.55);
    private static final Color HOVER_DOT_COLOR = Color.web("#f2f6fb");
    private static final Color OVERSHOOT_BAND_COLOR = Color.web("#151515");
    private static final Color HANDLE_RING_COLOR = Color.web("#ffffff", 0.90);
    private static final Color HANDLE_GLOW_COLOR = Color.web("#ffffff", 0.14);
    private static final Color BADGE_FILL = Color.web("#0f1114", 0.92);
    private static final Color BADGE_STROKE = Color.web("#32363b");
    private static final Font LABEL_FONT = Font.font(Font.getDefault().getFamily(), 9);
    private static final Font BADGE_FONT = Font.font(Font.getDefault().getFamily(), 10);

    private final Canvas canvas;
    private Easing.Type easingType = Easing.Type.LINEAR;
    private double[] easingParams;
    private Easing.Interpolation interpolation = Easing.Interpolation.TWEEN;

    // Custom Bezier control points (CSS cubic-bezier format: x in [0,1], y can overshoot)
    private double cx1 = 0.25, cy1 = 0.1;
    private double cx2 = 0.25, cy2 = 1.0;

    // Drag state
    private int draggingHandle = 0; // 0=none, 1=P1, 2=P2
    private int hoveredHandle = 0;
    private double hoverProgress = Double.NaN;
    private String helperText = "";

    // Callback when user drags control points
    private Consumer<double[]> onBezierChanged;
    private boolean expanded;

    public EasingCurveEditor() {
        canvas = new Canvas(COMPACT_WIDTH, COMPACT_HEIGHT);
        getChildren().add(canvas);

        setMaxWidth(Double.MAX_VALUE);
        setExpanded(false);

        widthProperty().addListener((obs, o, n) -> {
            canvas.setWidth(n.doubleValue());
            draw();
        });
        heightProperty().addListener((obs, o, n) -> {
            canvas.setHeight(n.doubleValue());
            draw();
        });
        disabledProperty().addListener((obs, oldValue, newValue) -> draw());

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(e -> {
            draggingHandle = 0;
            hoveredHandle = resolveHandleAt(e.getX(), e.getY());
            updateCursor(isInsidePlot(e.getX(), e.getY()));
            draw();
        });
        canvas.setOnMouseMoved(e -> {
            hoveredHandle = resolveHandleAt(e.getX(), e.getY());
            hoverProgress = isInsidePlot(e.getX(), e.getY())
                ? clamp01((e.getX() - getPlotBounds()[0]) / getPlotBounds()[2])
                : Double.NaN;
            updateCursor(isInsidePlot(e.getX(), e.getY()));
            draw();
        });
        canvas.setOnMouseExited(e -> {
            draggingHandle = 0;
            hoveredHandle = 0;
            hoverProgress = Double.NaN;
            canvas.setCursor(Cursor.DEFAULT);
            draw();
        });

        draw();
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        double prefHeight = expanded ? EXPANDED_HEIGHT : COMPACT_HEIGHT;
        double minHeight = expanded ? 220 : 80;
        setPrefSize(COMPACT_WIDTH, prefHeight);
        setMinSize(MIN_WIDTH, minHeight);
        setMaxWidth(expanded ? Double.MAX_VALUE : COMPACT_WIDTH);
        setMaxHeight(Double.MAX_VALUE);
        draw();
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setEasingType(Easing.Type type) {
        this.easingType = type != null ? type : Easing.Type.LINEAR;
        if (this.easingType != Easing.Type.CUSTOM) {
            this.easingParams = Easing.coerceParameters(this.easingType, this.easingParams);
        }
        draw();
    }

    public void setEasingSpec(EasingSpec spec) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        this.easingType = resolved.getType();
        this.easingParams = resolved.getParameters();
        if (this.easingType == Easing.Type.CUSTOM && easingParams != null && easingParams.length == 4) {
            this.cx1 = easingParams[0];
            this.cy1 = easingParams[1];
            this.cx2 = easingParams[2];
            this.cy2 = easingParams[3];
        }
        draw();
    }

    public Easing.Type getEasingType() {
        return easingType;
    }

    public void setInterpolation(Easing.Interpolation interpolation) {
        this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
        draw();
    }

    public Easing.Interpolation getInterpolation() {
        return interpolation;
    }

    public void setBezierParams(double cx1, double cy1, double cx2, double cy2) {
        this.cx1 = cx1; this.cy1 = cy1;
        this.cx2 = cx2; this.cy2 = cy2;
        this.easingParams = new double[]{ cx1, cy1, cx2, cy2 };
        draw();
    }

    public double[] getBezierParams() {
        return new double[]{ cx1, cy1, cx2, cy2 };
    }

    public void setOnBezierChanged(Consumer<double[]> callback) {
        this.onBezierChanged = callback;
    }

    public void setHelperText(String helperText) {
        this.helperText = helperText != null ? helperText.trim() : "";
        draw();
    }

    private double[] getPlotBounds() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        return new double[]{
            H_PADDING,
            TOP_PADDING,
            Math.max(1.0, w - H_PADDING - 12),
            Math.max(1.0, h - TOP_PADDING - BOTTOM_PADDING)
        };
    }

    private boolean isInteractiveCustomCurve() {
        return !isDisabled()
            && interpolation == Easing.Interpolation.TWEEN
            && easingType == Easing.Type.CUSTOM;
    }

    private boolean isInsidePlot(double x, double y) {
        double[] plot = getPlotBounds();
        return x >= plot[0] && x <= plot[0] + plot[2]
            && y >= plot[1] && y <= plot[1] + plot[3];
    }

    private void handleMousePressed(MouseEvent event) {
        canvas.requestFocus();
        hoverProgress = isInsidePlot(event.getX(), event.getY())
            ? clamp01((event.getX() - getPlotBounds()[0]) / getPlotBounds()[2])
            : Double.NaN;
        if (!isInteractiveCustomCurve()) {
            updateCursor(isInsidePlot(event.getX(), event.getY()));
            draw();
            return;
        }

        draggingHandle = resolveHandleAt(event.getX(), event.getY());
        if (draggingHandle == 0 && isInsidePlot(event.getX(), event.getY())) {
            draggingHandle = resolvePreferredHandle(event.getX(), event.getY());
            updateHandleFromMouse(event.getX(), event.getY(), event.isShiftDown());
        }
        hoveredHandle = draggingHandle;
        updateCursor(isInsidePlot(event.getX(), event.getY()));
        draw();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (draggingHandle == 0) return;
        hoverProgress = clamp01((event.getX() - getPlotBounds()[0]) / getPlotBounds()[2]);
        updateHandleFromMouse(event.getX(), event.getY(), event.isShiftDown());
        updateCursor(true);
    }

    private void updateHandleFromMouse(double mouseX, double mouseY, boolean snap) {
        double[] plot = getPlotBounds();
        double[] yRange = resolveVisibleYRange();
        double nx = clamp01((mouseX - plot[0]) / plot[2]);
        double ny = valueFromScreenY(mouseY, plot[1], plot[3], yRange[0], yRange[1]);
        if (snap) {
            nx = snap(nx, SNAP_INCREMENT);
            ny = snap(ny, SNAP_INCREMENT);
        }
        ny = clampBezierY(ny);

        if (draggingHandle == 1) {
            cx1 = nx;
            cy1 = ny;
        } else if (draggingHandle == 2) {
            cx2 = nx;
            cy2 = ny;
        }
        easingParams = new double[]{ cx1, cy1, cx2, cy2 };
        draw();
        if (onBezierChanged != null) {
            onBezierChanged.accept(new double[]{ cx1, cy1, cx2, cy2 });
        }
    }

    private int resolveHandleAt(double mouseX, double mouseY) {
        if (!isInteractiveCustomCurve()) return 0;
        double[] plot = getPlotBounds();
        double[] yRange = resolveVisibleYRange();
        double p1x = plot[0] + cx1 * plot[2];
        double p1y = screenYForValue(cy1, plot[1], plot[3], yRange[0], yRange[1]);
        double p2x = plot[0] + cx2 * plot[2];
        double p2y = screenYForValue(cy2, plot[1], plot[3], yRange[0], yRange[1]);

        double d1 = Math.hypot(mouseX - p1x, mouseY - p1y);
        double d2 = Math.hypot(mouseX - p2x, mouseY - p2y);

        if (d1 <= HANDLE_HIT_RADIUS && d1 <= d2) return 1;
        if (d2 <= HANDLE_HIT_RADIUS) return 2;
        return 0;
    }

    private int resolvePreferredHandle(double mouseX, double mouseY) {
        double[] plot = getPlotBounds();
        double[] yRange = resolveVisibleYRange();
        double p1x = plot[0] + cx1 * plot[2];
        double p1y = screenYForValue(cy1, plot[1], plot[3], yRange[0], yRange[1]);
        double p2x = plot[0] + cx2 * plot[2];
        double p2y = screenYForValue(cy2, plot[1], plot[3], yRange[0], yRange[1]);
        double d1 = Math.hypot(mouseX - p1x, mouseY - p1y);
        double d2 = Math.hypot(mouseX - p2x, mouseY - p2y);
        return d1 <= d2 ? 1 : 2;
    }

    private void updateCursor(boolean insidePlot) {
        if (draggingHandle != 0 || hoveredHandle != 0) {
            canvas.setCursor(Cursor.HAND);
            return;
        }
        if (insidePlot) {
            canvas.setCursor(isInteractiveCustomCurve() ? Cursor.CROSSHAIR : Cursor.CROSSHAIR);
            return;
        }
        canvas.setCursor(Cursor.DEFAULT);
    }

    private double[] resolveVisibleYRange() {
        boolean tween = interpolation == Easing.Interpolation.TWEEN;
        boolean isCustom = tween && easingType == Easing.Type.CUSTOM;

        double min = 0.0;
        double max = 1.0;
        int samples = 72;
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double value = evaluateCurveValue(t, tween, isCustom);
            if (Double.isFinite(value)) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (isCustom) {
            min = Math.min(min, Math.min(cy1, cy2));
            max = Math.max(max, Math.max(cy1, cy2));
            min = Math.min(min, -0.12);
            max = Math.max(max, 1.12);
        }

        boolean needsOvershootRoom = isCustom || min < -0.03 || max > 1.03;
        if (!needsOvershootRoom) {
            return new double[]{ 0.0, 1.0 };
        }

        double span = Math.max(0.5, max - min);
        double margin = Math.max(0.08, span * 0.08);
        double rangeMin = Math.max(-0.55, min - margin);
        double rangeMax = Math.min(1.55, max + margin);
        if (rangeMax - rangeMin < 0.5) {
            double mid = (rangeMax + rangeMin) * 0.5;
            rangeMin = mid - 0.25;
            rangeMax = mid + 0.25;
        }
        return new double[]{ rangeMin, rangeMax };
    }

    private double evaluateCurveValue(double t, boolean tween, boolean isCustom) {
        if (!tween) {
            return Easing.applyInterpolation(easingType, interpolation, easingParams, t);
        }
        if (isCustom) {
            return Easing.cubicBezier(cx1, cy1, cx2, cy2, t);
        }
        return Easing.apply(easingType, easingParams, t);
    }

    private double screenYForValue(double value,
                                   double plotY,
                                   double plotH,
                                   double minValue,
                                   double maxValue) {
        double span = Math.max(0.0001, maxValue - minValue);
        double normalized = (maxValue - value) / span;
        return plotY + clamp(normalized, -0.25, 1.25) * plotH;
    }

    private double valueFromScreenY(double screenY,
                                    double plotY,
                                    double plotH,
                                    double minValue,
                                    double maxValue) {
        double span = Math.max(0.0001, maxValue - minValue);
        double normalized = clamp01((screenY - plotY) / plotH);
        return maxValue - normalized * span;
    }

    private double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private double clampBezierY(double value) {
        return clamp(value, -0.5, 1.5);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double snap(double value, double increment) {
        return Math.round(value / increment) * increment;
    }

    private String formatReadout(double progress, double value) {
        return String.format("t %.2f  ->  %.2f", progress, value);
    }

    private void draw() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(new LinearGradient(
            0, 0, 0, 1,
            true,
            CycleMethod.NO_CYCLE,
            new Stop(0.0, Color.web("#121315")),
            new Stop(1.0, BG_COLOR)
        ));
        gc.fillRect(0, 0, w, h);

        double[] plot = getPlotBounds();
        double plotX = plot[0], plotY = plot[1], plotW = plot[2], plotH = plot[3];
        if (plotW < 20 || plotH < 20) return;

        boolean tween = interpolation == Easing.Interpolation.TWEEN;
        boolean isCustom = tween && easingType == Easing.Type.CUSTOM;
        double[] yRange = resolveVisibleYRange();
        double rangeMin = yRange[0];
        double rangeMax = yRange[1];
        double zeroY = screenYForValue(0.0, plotY, plotH, rangeMin, rangeMax);
        double oneY = screenYForValue(1.0, plotY, plotH, rangeMin, rangeMax);
        double unitTop = Math.min(oneY, zeroY);
        double unitBottom = Math.max(oneY, zeroY);
        double unitHeight = Math.max(1.0, unitBottom - unitTop);

        if (rangeMin < 0.0) {
            gc.setFill(OVERSHOOT_BAND_COLOR);
            gc.fillRect(plotX, Math.max(plotY, zeroY), plotW, plotY + plotH - Math.max(plotY, zeroY));
        }
        if (rangeMax > 1.0) {
            gc.setFill(OVERSHOOT_BAND_COLOR);
            gc.fillRect(plotX, plotY, plotW, Math.max(0.0, Math.min(plotH, oneY - plotY)));
        }

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (int i = 1; i <= 3; i++) {
            double frac = i / 4.0;
            double gx = plotX + frac * plotW;
            gc.strokeLine(gx, plotY, gx, plotY + plotH);
        }
        for (double value : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            double gy = screenYForValue(value, plotY, plotH, rangeMin, rangeMax);
            if (gy >= plotY - 0.5 && gy <= plotY + plotH + 0.5) {
                gc.strokeLine(plotX, gy, plotX + plotW, gy);
            }
        }

        // Unit-space frame.
        gc.setStroke(AXIS_COLOR);
        gc.setLineWidth(1);
        gc.strokeRect(plotX, unitTop, plotW, unitHeight);

        // Linear reference line.
        gc.setStroke(LINEAR_COLOR);
        gc.setLineWidth(1);
        gc.setLineDashes(4, 3);
        gc.strokeLine(plotX, zeroY, plotX + plotW, oneY);
        gc.setLineDashes((double[]) null);

        // Draw the easing curve.
        gc.setStroke(CURVE_COLOR);
        gc.setLineWidth(2);
        gc.beginPath();

        int steps = Math.max(60, (int) plotW);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double v = evaluateCurveValue(t, tween, isCustom);
            double sx = plotX + t * plotW;
            double sy = screenYForValue(v, plotY, plotH, rangeMin, rangeMax);

            if (i == 0) gc.moveTo(sx, sy);
            else gc.lineTo(sx, sy);
        }
        gc.stroke();

        if (Double.isFinite(hoverProgress)) {
            double value = evaluateCurveValue(hoverProgress, tween, isCustom);
            double hoverX = plotX + hoverProgress * plotW;
            double hoverY = screenYForValue(value, plotY, plotH, rangeMin, rangeMax);
            gc.setStroke(HOVER_GUIDE_COLOR);
            gc.setLineWidth(1);
            gc.setLineDashes(3, 3);
            gc.strokeLine(hoverX, plotY, hoverX, plotY + plotH);
            gc.strokeLine(plotX, hoverY, plotX + plotW, hoverY);
            gc.setLineDashes((double[]) null);
            gc.setFill(HOVER_DOT_COLOR);
            gc.fillOval(hoverX - 4, hoverY - 4, 8, 8);
        }

        // Start and end points.
        gc.setFill(POINT_COLOR);
        double startSx = plotX;
        double startSy = zeroY;
        double endSx = plotX + plotW;
        double endSy = oneY;
        gc.fillOval(startSx - 3, startSy - 3, 6, 6);
        gc.fillOval(endSx - 3, endSy - 3, 6, 6);

        // Draw control point handles when editable.
        if (isCustom) {
            double p1x = plotX + cx1 * plotW;
            double p1y = screenYForValue(cy1, plotY, plotH, rangeMin, rangeMax);
            double p2x = plotX + cx2 * plotW;
            double p2y = screenYForValue(cy2, plotY, plotH, rangeMin, rangeMax);

            gc.setStroke(TANGENT_COLOR);
            gc.setLineWidth(1);
            gc.setLineDashes(3, 2);
            gc.strokeLine(startSx, startSy, p1x, p1y);
            gc.strokeLine(endSx, endSy, p2x, p2y);
            gc.setLineDashes((double[]) null);

            drawHandle(gc, p1x, p1y, HANDLE_COLOR, "P1", hoveredHandle == 1 || draggingHandle == 1);
            drawHandle(gc, p2x, p2y, HANDLE2_COLOR, "P2", hoveredHandle == 2 || draggingHandle == 2);
            drawFooterLabel(gc,
                String.format("cubic-bezier(%.2f, %.2f, %.2f, %.2f)", cx1, cy1, cx2, cy2),
                plotX + 2,
                plotY + plotH + 15,
                Color.web("#a5adb7"));
        } else {
            String label = tween
                ? describeEasingLabel()
                : interpolation.name();
            drawFooterLabel(gc, label, plotX + 2, plotY + plotH + 15, Color.web("#8d939b"));
        }

        // Axis labels.
        gc.setFill(LABEL_COLOR);
        gc.setFont(LABEL_FONT);
        gc.fillText("0", plotX - 2, plotY + plotH + 12);
        gc.fillText("1", plotX + plotW - 4, plotY + plotH + 12);
        gc.fillText("1", plotX - 16, oneY + 4);
        gc.fillText("0", plotX - 16, zeroY + 4);

        String resolvedHelper = !helperText.isBlank()
            ? helperText
            : isCustom
                ? "Drag handles. Shift snaps to 0.05."
                : "Hover to inspect the curve.";
        drawBadge(gc, plotX + 6, plotY + 6, resolvedHelper, false);
        if (Double.isFinite(hoverProgress)) {
            double value = evaluateCurveValue(hoverProgress, tween, isCustom);
            drawBadge(gc, plotX + plotW - 118, plotY + 6, formatReadout(hoverProgress, value), true);
        }
        if (isDisabled()) {
            drawBadge(gc, plotX + plotW - 80, plotY + plotH - 22, "Locked", true);
        }
    }

    private String describeEasingLabel() {
        if (easingType == Easing.Type.SPRING || easingType == Easing.Type.DAMPED_SPRING) {
            return Easing.formatSpec(EasingSpec.of(easingType, easingParams));
        }
        return Easing.displayName(easingType);
    }

    private void drawHandle(GraphicsContext gc,
                            double x,
                            double y,
                            Color color,
                            String label,
                            boolean highlighted) {
        double haloRadius = highlighted ? HANDLE_RADIUS + 6 : HANDLE_RADIUS + 3;
        gc.setFill(HANDLE_GLOW_COLOR);
        gc.fillOval(x - haloRadius, y - haloRadius, haloRadius * 2, haloRadius * 2);

        gc.setFill(color);
        gc.fillOval(x - HANDLE_RADIUS, y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
        gc.setStroke(HANDLE_RING_COLOR);
        gc.setLineWidth(highlighted ? 1.6 : 1.0);
        gc.strokeOval(x - HANDLE_RADIUS, y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);

        gc.setFill(Color.web("#d7dde5"));
        gc.setFont(LABEL_FONT);
        gc.fillText(label, x + 8, y - 8);
    }

    private void drawFooterLabel(GraphicsContext gc, String text, double x, double y, Color color) {
        gc.setFill(color);
        gc.setFont(LABEL_FONT);
        gc.fillText(text, x, y);
    }

    private void drawBadge(GraphicsContext gc, double x, double y, String text, boolean alignRight) {
        if (text == null || text.isBlank()) return;
        gc.setFont(BADGE_FONT);
        double textWidth = Math.max(12, text.length() * 5.6);
        double badgeWidth = textWidth + 12;
        double badgeHeight = 18;
        double drawX = alignRight ? x - badgeWidth : x;
        gc.setFill(BADGE_FILL);
        gc.fillRoundRect(drawX, y, badgeWidth, badgeHeight, 8, 8);
        gc.setStroke(BADGE_STROKE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(drawX, y, badgeWidth, badgeHeight, 8, 8);
        gc.setFill(Color.web("#d3dae3"));
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(text, drawX + 6, y + badgeHeight * 0.5);
        gc.setTextBaseline(VPos.BASELINE);
    }
}
