package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.animation.AnimationTimer;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
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
    private static final double KEYBOARD_NUDGE_INCREMENT = 0.01;
    private static final double CURVE_POINT_MIN_GAP = 0.001;

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
    private double[] curvePoints = Easing.coerceParameters(Easing.Type.CURVE, null);

    // Drag state
    private int draggingHandle = 0; // 0=none, 1=P1, 2=P2
    private int hoveredHandle = 0;
    private int selectedHandle = 0;
    private double hoverProgress = Double.NaN;

    // Callback when user drags control points
    private Consumer<double[]> onBezierChanged;
    private Consumer<EasingSpec> onCurveSpecChanged;
    private boolean expanded;

    // --- A) Ghost curve overlays for adjacent keyframes ---
    private final List<GhostCurve> ghostCurves = new ArrayList<>();

    // --- B) Animated motion preview ---
    private boolean animating = false;
    private double animProgress = 0.0;
    private long animStartNanos = 0;
    private double animDurationMs = 1000.0;
    private AnimationTimer animTimer;
    private static final Color ANIM_DOT_COLOR = Color.web("#ff6b8a");
    private static final Color ANIM_TRAIL_COLOR = Color.web("#ff6b8a", 0.18);
    private static final Color ANIM_BAR_BG = Color.web("#1a1a1a");
    private static final Color ANIM_BAR_FILL = Color.web("#ff6b8a", 0.7);
    private static final double ANIM_DOT_RADIUS = 5.0;
    private static final double MOTION_BAR_HEIGHT = 6.0;

    public EasingCurveEditor() {
        canvas = new Canvas(COMPACT_WIDTH, COMPACT_HEIGHT);
        getChildren().add(canvas);

        setMaxWidth(Double.MAX_VALUE);
        setFocusTraversable(true);
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
        canvas.focusedProperty().addListener((obs, oldValue, newValue) -> draw());

        canvas.setFocusTraversable(true);
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnKeyPressed(this::handleKeyPressed);
        canvas.setOnMouseReleased(e -> {
            boolean wasDraggingHandle = draggingHandle != 0;
            draggingHandle = 0;
            hoveredHandle = resolveHandleAt(e.getX(), e.getY());
            updateCursor(isInsidePlot(e.getX(), e.getY()));
            draw();
            if (wasDraggingHandle) {
                e.consume();
            }
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

    @Override
    public void requestFocus() {
        super.requestFocus();
        canvas.requestFocus();
    }

    public void setEasingType(Easing.Type type) {
        this.easingType = type != null ? type : Easing.Type.LINEAR;
        if (this.easingType != Easing.Type.CUSTOM) {
            this.easingParams = Easing.coerceParameters(this.easingType, this.easingParams);
            selectedHandle = 0;
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
            if (selectedHandle == 0) selectedHandle = 1;
        } else if (this.easingType == Easing.Type.CURVE) {
            this.curvePoints = Easing.coerceParameters(Easing.Type.CURVE, easingParams);
            if (selectedHandle <= 0 || selectedHandle > editableHandleCount()) {
                selectedHandle = 1;
            }
        } else {
            selectedHandle = 0;
        }
        draw();
    }

    public Easing.Type getEasingType() {
        return easingType;
    }

    public void setInterpolation(Easing.Interpolation interpolation) {
        this.interpolation = interpolation != null ? interpolation : Easing.Interpolation.TWEEN;
        if (this.interpolation != Easing.Interpolation.TWEEN) {
            selectedHandle = 0;
        } else if (easingType == Easing.Type.CUSTOM && selectedHandle == 0) {
            selectedHandle = 1;
        }
        draw();
    }

    public Easing.Interpolation getInterpolation() {
        return interpolation;
    }

    public void setBezierParams(double cx1, double cy1, double cx2, double cy2) {
        this.cx1 = cx1; this.cy1 = cy1;
        this.cx2 = cx2; this.cy2 = cy2;
        this.easingParams = new double[]{ cx1, cy1, cx2, cy2 };
        if (selectedHandle == 0) selectedHandle = 1;
        draw();
    }

    public double[] getBezierParams() {
        return new double[]{ cx1, cy1, cx2, cy2 };
    }

    public void setOnBezierChanged(Consumer<double[]> callback) {
        this.onBezierChanged = callback;
    }

    public void setOnCurveSpecChanged(Consumer<EasingSpec> callback) {
        this.onCurveSpecChanged = callback;
    }

    public EasingSpec getEditedSpec() {
        if (easingType == Easing.Type.CUSTOM) {
            return EasingSpec.cubicBezier(cx1, cy1, cx2, cy2);
        }
        if (easingType == Easing.Type.CURVE) {
            return EasingSpec.curve(curvePoints);
        }
        return EasingSpec.of(easingType, easingParams);
    }

    public boolean isMultiPointCurve() {
        return easingType == Easing.Type.CURVE;
    }

    public int getCurvePointCount() {
        return editableHandleCount();
    }

    public boolean addCurvePointAtCurrentFocus() {
        double progress = Double.isFinite(hoverProgress)
            ? hoverProgress
            : selectedHandle > 0
                ? handleX(selectedHandle)
                : 0.5;
        return addCurvePointAt(progress);
    }

    public boolean addCurvePointAt(double progress) {
        if (isDisabled() || interpolation != Easing.Interpolation.TWEEN) return false;
        double target = clamp01(progress);
        if (target <= 0.0 || target >= 1.0) return false;

        EasingSpec before = getEditedSpec();
        if (easingType != Easing.Type.CURVE) {
            easingType = Easing.Type.CURVE;
            curvePoints = Easing.sampledCurveParameters(before, 3);
        }
        target = resolveCurveInsertX(curvePoints, target);
        if (target <= 0.0 || target >= 1.0) return false;
        double value = Easing.apply(before, target);
        curvePoints = insertCurvePoint(curvePoints, target, value);
        easingParams = curvePoints.clone();
        selectedHandle = findClosestCurvePoint(target, value);
        hoveredHandle = selectedHandle;
        commitCurveChange();
        return true;
    }

    public boolean removeSelectedCurvePoint() {
        if (easingType != Easing.Type.CURVE) return false;
        int count = curvePoints.length / 2;
        if (selectedHandle <= 0 || selectedHandle > count || count <= 1) return false;
        double[] next = new double[(count - 1) * 2];
        int write = 0;
        for (int i = 0; i < count; i++) {
            if (i == selectedHandle - 1) continue;
            next[write++] = curvePoints[i * 2];
            next[write++] = curvePoints[i * 2 + 1];
        }
        curvePoints = Easing.coerceParameters(Easing.Type.CURVE, next);
        easingParams = curvePoints.clone();
        selectedHandle = Math.max(1, Math.min(selectedHandle, curvePoints.length / 2));
        hoveredHandle = selectedHandle;
        commitCurveChange();
        return true;
    }

    // --- A) Ghost curve overlay API ---

    public record GhostCurve(String label, EasingSpec spec, Easing.Interpolation interpolation, Color color) {
        public GhostCurve(String label, EasingSpec spec, Easing.Interpolation interpolation) {
            this(label, spec, interpolation, Color.web("#5a7aaa", 0.45));
        }
    }

    public void setGhostCurves(List<GhostCurve> curves) {
        ghostCurves.clear();
        if (curves != null) {
            ghostCurves.addAll(curves);
        }
        draw();
    }

    public void clearGhostCurves() {
        ghostCurves.clear();
        draw();
    }

    // --- B) Animated motion preview API ---

    public void setAnimDurationMs(double durationMs) {
        this.animDurationMs = Math.max(50.0, durationMs);
    }

    public boolean isAnimating() {
        return animating;
    }

    public void startAnimation() {
        if (animating) stopAnimation();
        animating = true;
        animProgress = 0.0;
        animStartNanos = System.nanoTime();
        if (animTimer == null) {
            animTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (!animating) {
                        stop();
                        return;
                    }
                    double elapsedMs = (now - animStartNanos) / 1_000_000.0;
                    animProgress = elapsedMs / animDurationMs;
                    if (animProgress >= 1.0) {
                        animProgress = 0.0;
                        animStartNanos = now;
                    }
                    draw();
                }
            };
        }
        animTimer.start();
        draw();
    }

    public void stopAnimation() {
        animating = false;
        if (animTimer != null) {
            animTimer.stop();
        }
        animProgress = 0.0;
        draw();
    }

    public void toggleAnimation() {
        if (animating) stopAnimation();
        else startAnimation();
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

    private boolean isInteractiveEditableCurve() {
        return !isDisabled()
            && interpolation == Easing.Interpolation.TWEEN
            && Easing.isEditableCurve(easingType);
    }

    private boolean isInsidePlot(double x, double y) {
        double[] plot = getPlotBounds();
        return x >= plot[0] && x <= plot[0] + plot[2]
            && y >= plot[1] && y <= plot[1] + plot[3];
    }

    private void handleMousePressed(MouseEvent event) {
        canvas.requestFocus();
        boolean insidePlot = isInsidePlot(event.getX(), event.getY());
        hoverProgress = insidePlot
            ? clamp01((event.getX() - getPlotBounds()[0]) / getPlotBounds()[2])
            : Double.NaN;
        if (!isInteractiveEditableCurve()) {
            updateCursor(insidePlot);
            draw();
            return;
        }

        if (insidePlot && event.getClickCount() >= 2) {
            if (addCurvePointAt(clamp01((event.getX() - getPlotBounds()[0]) / getPlotBounds()[2]))) {
                event.consume();
            }
            updateCursor(true);
            draw();
            return;
        }

        draggingHandle = resolveHandleAt(event.getX(), event.getY());
        if (draggingHandle == 0 && insidePlot) {
            draggingHandle = resolvePreferredHandle(event.getX(), event.getY());
            updateHandleFromMouse(event.getX(), event.getY(), event.isShiftDown());
        }
        if (draggingHandle != 0) {
            selectedHandle = draggingHandle;
            event.consume();
        }
        hoveredHandle = draggingHandle;
        updateCursor(insidePlot);
        draw();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (draggingHandle == 0) return;
        hoverProgress = clamp01((event.getX() - getPlotBounds()[0]) / getPlotBounds()[2]);
        updateHandleFromMouse(event.getX(), event.getY(), event.isShiftDown());
        updateCursor(true);
        event.consume();
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!isInteractiveEditableCurve()) return;

        if (event.getCode() == KeyCode.TAB) {
            int count = Math.max(1, editableHandleCount());
            if (selectedHandle <= 0) {
                selectedHandle = 1;
            } else if (event.isShiftDown()) {
                selectedHandle = selectedHandle == 1 ? count : selectedHandle - 1;
            } else {
                selectedHandle = selectedHandle == count ? 1 : selectedHandle + 1;
            }
            hoveredHandle = selectedHandle;
            draw();
            event.consume();
            return;
        }
        int digit = digitSelection(event.getCode());
        if (digit > 0 && digit <= editableHandleCount()) {
            selectedHandle = digit;
            hoveredHandle = digit;
            draw();
            event.consume();
            return;
        }
        if (event.getCode() == KeyCode.BACK_SPACE || event.getCode() == KeyCode.DELETE) {
            if (removeSelectedCurvePoint()) {
                event.consume();
            }
            return;
        }
        if (event.getCode() == KeyCode.PLUS || event.getCode() == KeyCode.EQUALS) {
            if (addCurvePointAtCurrentFocus()) {
                event.consume();
            }
            return;
        }
        if (event.getCode() == KeyCode.LEFT
            || event.getCode() == KeyCode.RIGHT
            || event.getCode() == KeyCode.UP
            || event.getCode() == KeyCode.DOWN) {
            if (selectedHandle == 0) {
                selectedHandle = 1;
            }
            double step = event.isShiftDown() ? SNAP_INCREMENT : KEYBOARD_NUDGE_INCREMENT;
            nudgeSelectedHandle(
                event.getCode() == KeyCode.LEFT ? -step : event.getCode() == KeyCode.RIGHT ? step : 0.0,
                event.getCode() == KeyCode.DOWN ? -step : event.getCode() == KeyCode.UP ? step : 0.0,
                event.isShiftDown()
            );
            event.consume();
        }
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
        ny = clampEditableY(ny);
        setHandle(draggingHandle, nx, ny);
        selectedHandle = draggingHandle;
        commitCurveChange();
    }

    private void nudgeSelectedHandle(double deltaX, double deltaY, boolean snap) {
        if (selectedHandle <= 0 || selectedHandle > editableHandleCount()) {
            return;
        }
        double nx = clamp01(handleX(selectedHandle) + deltaX);
        double ny = clampEditableY(handleY(selectedHandle) + deltaY);
        if (snap) {
            nx = snap(nx, SNAP_INCREMENT);
            ny = snap(ny, SNAP_INCREMENT);
        }
        setHandle(selectedHandle, nx, ny);
        commitCurveChange();
    }

    private void commitCurveChange() {
        if (easingType == Easing.Type.CURVE) {
            curvePoints = Easing.coerceParameters(Easing.Type.CURVE, curvePoints);
            easingParams = curvePoints.clone();
        } else {
            easingParams = new double[]{ cx1, cy1, cx2, cy2 };
        }
        draw();
        if (onCurveSpecChanged != null) {
            onCurveSpecChanged.accept(getEditedSpec());
        }
        if (onBezierChanged != null) {
            if (easingType == Easing.Type.CUSTOM) {
                onBezierChanged.accept(new double[]{ cx1, cy1, cx2, cy2 });
            } else {
                onBezierChanged.accept(Easing.coerceParameters(Easing.Type.CUSTOM, null));
            }
        }
    }

    private int resolveHandleAt(double mouseX, double mouseY) {
        if (!isInteractiveEditableCurve()) return 0;
        double[] plot = getPlotBounds();
        double[] yRange = resolveVisibleYRange();
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 1; i <= editableHandleCount(); i++) {
            double px = plot[0] + handleX(i) * plot[2];
            double py = screenYForValue(handleY(i), plot[1], plot[3], yRange[0], yRange[1]);
            double distance = Math.hypot(mouseX - px, mouseY - py);
            if (distance <= HANDLE_HIT_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        if (best != 0) return best;
        return 0;
    }

    private int resolvePreferredHandle(double mouseX, double mouseY) {
        double[] plot = getPlotBounds();
        double[] yRange = resolveVisibleYRange();
        int best = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 1; i <= editableHandleCount(); i++) {
            double px = plot[0] + handleX(i) * plot[2];
            double py = screenYForValue(handleY(i), plot[1], plot[3], yRange[0], yRange[1]);
            double distance = Math.hypot(mouseX - px, mouseY - py);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private void updateCursor(boolean insidePlot) {
        if (draggingHandle != 0 || hoveredHandle != 0 || selectedHandle != 0) {
            canvas.setCursor(Cursor.HAND);
            return;
        }
        if (insidePlot) {
            canvas.setCursor(Cursor.CROSSHAIR);
            return;
        }
        canvas.setCursor(Cursor.DEFAULT);
    }

    private double[] resolveVisibleYRange() {
        boolean tween = interpolation == Easing.Interpolation.TWEEN;
        boolean isCustom = tween && easingType == Easing.Type.CUSTOM;
        boolean isCurve = tween && easingType == Easing.Type.CURVE;

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
        } else if (isCurve) {
            for (int i = 0; i < curvePoints.length / 2; i++) {
                min = Math.min(min, curvePoints[i * 2 + 1]);
                max = Math.max(max, curvePoints[i * 2 + 1]);
            }
            min = Math.min(min, -0.12);
            max = Math.max(max, 1.12);
        }

        boolean needsOvershootRoom = isCustom || isCurve || min < -0.03 || max > 1.03;
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
        if (easingType == Easing.Type.CURVE) {
            return Easing.curve(curvePoints, t);
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

    private int editableHandleCount() {
        if (easingType == Easing.Type.CURVE) {
            return curvePoints.length / 2;
        }
        if (easingType == Easing.Type.CUSTOM) {
            return 2;
        }
        return 0;
    }

    private double handleX(int handle) {
        if (easingType == Easing.Type.CURVE) {
            int index = Math.max(0, Math.min(curvePoints.length / 2 - 1, handle - 1));
            return curvePoints[index * 2];
        }
        return handle == 1 ? cx1 : cx2;
    }

    private double handleY(int handle) {
        if (easingType == Easing.Type.CURVE) {
            int index = Math.max(0, Math.min(curvePoints.length / 2 - 1, handle - 1));
            return curvePoints[index * 2 + 1];
        }
        return handle == 1 ? cy1 : cy2;
    }

    private void setHandle(int handle, double x, double y) {
        if (easingType == Easing.Type.CURVE) {
            int count = curvePoints.length / 2;
            if (handle <= 0 || handle > count) return;
            double[] next = curvePoints.clone();
            int index = handle - 1;
            double minGap = CURVE_POINT_MIN_GAP;
            double lower = index == 0 ? minGap : next[(index - 1) * 2] + minGap;
            double upper = index == count - 1 ? 1.0 - minGap : next[(index + 1) * 2] - minGap;
            next[index * 2] = clamp(x, lower, upper);
            next[index * 2 + 1] = clampEditableY(y);
            curvePoints = Easing.coerceParameters(Easing.Type.CURVE, next);
            return;
        }
        if (handle == 1) {
            cx1 = x;
            cy1 = y;
        } else if (handle == 2) {
            cx2 = x;
            cy2 = y;
        }
    }

    private int digitSelection(KeyCode code) {
        return switch (code) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            case DIGIT5, NUMPAD5 -> 5;
            case DIGIT6, NUMPAD6 -> 6;
            case DIGIT7, NUMPAD7 -> 7;
            case DIGIT8, NUMPAD8 -> 8;
            case DIGIT9, NUMPAD9 -> 9;
            default -> 0;
        };
    }

    private double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private double clampEditableY(double value) {
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

    private String formatHandleReadout(int handle) {
        if (handle > 0 && handle <= editableHandleCount()) {
            return String.format("P%d  x %.3f  y %.3f", handle, handleX(handle), handleY(handle));
        }
        return "";
    }

    private double[] insertCurvePoint(double[] params, double x, double y) {
        int count = params.length / 2;
        double[] next = new double[(count + 1) * 2];
        int insertIndex = count;
        for (int i = 0; i < count; i++) {
            if (x < params[i * 2]) {
                insertIndex = i;
                break;
            }
        }
        int read = 0;
        for (int i = 0; i < count + 1; i++) {
            if (i == insertIndex) {
                next[i * 2] = x;
                next[i * 2 + 1] = clampEditableY(y);
            } else {
                next[i * 2] = params[read * 2];
                next[i * 2 + 1] = params[read * 2 + 1];
                read++;
            }
        }
        return Easing.coerceParameters(Easing.Type.CURVE, next);
    }

    static double resolveCurveInsertX(double[] params, double requestedX) {
        double target = Math.max(0.0, Math.min(1.0, requestedX));
        double[] points = Easing.coerceParameters(Easing.Type.CURVE, params);
        int count = points.length / 2;
        if (count <= 0) {
            return clampStatic(target, CURVE_POINT_MIN_GAP, 1.0 - CURVE_POINT_MIN_GAP);
        }

        boolean tooClose = false;
        for (int i = 0; i < count; i++) {
            if (Math.abs(points[i * 2] - target) < CURVE_POINT_MIN_GAP * 2.0) {
                tooClose = true;
                break;
            }
        }
        if (!tooClose) {
            return clampStatic(target, CURVE_POINT_MIN_GAP, 1.0 - CURVE_POINT_MIN_GAP);
        }

        double prev = 0.0;
        double bestX = target;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= count; i++) {
            double next = i == count ? 1.0 : points[i * 2];
            double usableGap = next - prev - (CURVE_POINT_MIN_GAP * 2.0);
            if (usableGap > 0.0) {
                double midpoint = (prev + next) * 0.5;
                double score = usableGap - Math.abs(midpoint - target);
                if (score > bestScore) {
                    bestScore = score;
                    bestX = midpoint;
                }
            }
            prev = next;
        }
        return clampStatic(bestX, CURVE_POINT_MIN_GAP, 1.0 - CURVE_POINT_MIN_GAP);
    }

    private int findClosestCurvePoint(double x, double y) {
        int count = curvePoints.length / 2;
        int best = 1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double dx = curvePoints[i * 2] - x;
            double dy = curvePoints[i * 2 + 1] - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i + 1;
            }
        }
        return best;
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
        boolean isCurve = tween && easingType == Easing.Type.CURVE;
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

        // --- A) Draw ghost curve overlays (adjacent keyframes) ---
        int steps = Math.max(60, (int) plotW);
        for (GhostCurve ghost : ghostCurves) {
            if (ghost.spec() == null) continue;
            Color ghostColor = ghost.color() != null ? ghost.color() : Color.web("#5a7aaa", 0.45);
            gc.setStroke(ghostColor);
            gc.setLineWidth(1.4);
            gc.setLineDashes(6, 4);
            gc.beginPath();
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                double gv = Easing.applyInterpolation(ghost.spec(), ghost.interpolation(), t);
                double gsx = plotX + t * plotW;
                double gsy = screenYForValue(gv, plotY, plotH, rangeMin, rangeMax);
                if (i == 0) gc.moveTo(gsx, gsy);
                else gc.lineTo(gsx, gsy);
            }
            gc.stroke();
            gc.setLineDashes((double[]) null);
            // Ghost label badge
            if (ghost.label() != null && !ghost.label().isBlank()) {
                gc.setFill(ghostColor.deriveColor(0, 1.0, 1.0, 0.85));
                gc.setFont(LABEL_FONT);
                double labelT = 0.85;
                double labelV = Easing.applyInterpolation(ghost.spec(), ghost.interpolation(), labelT);
                double labelX = plotX + labelT * plotW;
                double labelY = screenYForValue(labelV, plotY, plotH, rangeMin, rangeMax) - 8;
                gc.fillText(ghost.label(), labelX, labelY);
            }
        }

        // Draw the easing curve.
        gc.setStroke(CURVE_COLOR);
        gc.setLineWidth(2);
        gc.beginPath();

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

            drawHandle(gc, p1x, p1y, HANDLE_COLOR, "P1", hoveredHandle == 1 || draggingHandle == 1 || selectedHandle == 1);
            drawHandle(gc, p2x, p2y, HANDLE2_COLOR, "P2", hoveredHandle == 2 || draggingHandle == 2 || selectedHandle == 2);
            drawFooterLabel(gc,
                String.format("cubic-bezier(%.2f, %.2f, %.2f, %.2f)", cx1, cy1, cx2, cy2),
                plotX + 2,
                plotY + plotH + 15,
                Color.web("#a5adb7"));
        } else if (isCurve) {
            gc.setStroke(TANGENT_COLOR);
            gc.setLineWidth(1);
            gc.setLineDashes(3, 2);
            double prevX = startSx;
            double prevY = startSy;
            for (int i = 1; i <= editableHandleCount(); i++) {
                double px = plotX + handleX(i) * plotW;
                double py = screenYForValue(handleY(i), plotY, plotH, rangeMin, rangeMax);
                gc.strokeLine(prevX, prevY, px, py);
                prevX = px;
                prevY = py;
            }
            gc.strokeLine(prevX, prevY, endSx, endSy);
            gc.setLineDashes((double[]) null);
            for (int i = 1; i <= editableHandleCount(); i++) {
                double px = plotX + handleX(i) * plotW;
                double py = screenYForValue(handleY(i), plotY, plotH, rangeMin, rangeMax);
                drawHandle(gc, px, py, i == selectedHandle ? HANDLE2_COLOR : HANDLE_COLOR,
                    "P" + i, hoveredHandle == i || draggingHandle == i || selectedHandle == i);
            }
            drawFooterLabel(gc,
                Easing.formatSpec(EasingSpec.curve(curvePoints)),
                plotX + 2,
                plotY + plotH + 15,
                Color.web("#a5adb7"));
        } else {
            String label = tween
                ? describeEasingLabel()
                : interpolation.name();
            drawFooterLabel(gc, label, plotX + 2, plotY + plotH + 15, Color.web("#8d939b"));
        }

        // --- B) Animated motion preview ---
        if (animating && animProgress >= 0.0) {
            double ap = Math.min(1.0, animProgress);
            double av = evaluateCurveValue(ap, tween, isCustom);
            double adx = plotX + ap * plotW;
            double ady = screenYForValue(av, plotY, plotH, rangeMin, rangeMax);

            // Trail: draw fading segments behind the dot
            int trailSegments = Math.max(6, (int) (ap * 20));
            for (int i = 0; i < trailSegments; i++) {
                double segT = ap * (double) i / trailSegments;
                double segV = evaluateCurveValue(segT, tween, isCustom);
                double segX = plotX + segT * plotW;
                double segY = screenYForValue(segV, plotY, plotH, rangeMin, rangeMax);
                double fade = (double) (i + 1) / trailSegments;
                gc.setFill(ANIM_TRAIL_COLOR.deriveColor(0, 1, 1, fade * 0.6));
                double r = 2.0 + fade * 1.5;
                gc.fillOval(segX - r, segY - r, r * 2, r * 2);
            }

            // Main dot
            gc.setFill(ANIM_DOT_COLOR);
            gc.fillOval(adx - ANIM_DOT_RADIUS, ady - ANIM_DOT_RADIUS, ANIM_DOT_RADIUS * 2, ANIM_DOT_RADIUS * 2);
            gc.setStroke(Color.web("#ffffff", 0.7));
            gc.setLineWidth(1.2);
            gc.strokeOval(adx - ANIM_DOT_RADIUS, ady - ANIM_DOT_RADIUS, ANIM_DOT_RADIUS * 2, ANIM_DOT_RADIUS * 2);

            // Crosshair guides from dot
            gc.setStroke(ANIM_DOT_COLOR.deriveColor(0, 1, 1, 0.3));
            gc.setLineWidth(0.8);
            gc.setLineDashes(2, 3);
            gc.strokeLine(adx, plotY, adx, plotY + plotH);
            gc.strokeLine(plotX, ady, plotX + plotW, ady);
            gc.setLineDashes((double[]) null);

            // Motion progress bar at bottom
            double barY = plotY + plotH + BOTTOM_PADDING - MOTION_BAR_HEIGHT - 2;
            gc.setFill(ANIM_BAR_BG);
            gc.fillRoundRect(plotX, barY, plotW, MOTION_BAR_HEIGHT, 3, 3);
            gc.setFill(ANIM_BAR_FILL);
            gc.fillRoundRect(plotX, barY, plotW * ap, MOTION_BAR_HEIGHT, 3, 3);

            // Value readout badge
            drawBadge(gc, plotX + plotW - 6, plotY + plotH - 42,
                String.format("t %.2f → %.3f", ap, av), true);
        }

        // Axis labels.
        gc.setFill(LABEL_COLOR);
        gc.setFont(LABEL_FONT);
        gc.fillText("0", plotX - 2, plotY + plotH + 12);
        gc.fillText("1", plotX + plotW - 4, plotY + plotH + 12);
        gc.fillText("1", plotX - 16, oneY + 4);
        gc.fillText("0", plotX - 16, zeroY + 4);

        if (Double.isFinite(hoverProgress)) {
            double value = evaluateCurveValue(hoverProgress, tween, isCustom);
            drawBadge(gc, plotX + plotW - 118, plotY + 6, formatReadout(hoverProgress, value), true);
        }
        if (isCustom || isCurve) {
            String handleReadout = formatHandleReadout(selectedHandle);
            if (!handleReadout.isBlank()) {
                drawBadge(gc, plotX + 6, plotY + plotH - 22, handleReadout, false);
            }
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

    private static double clampStatic(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
