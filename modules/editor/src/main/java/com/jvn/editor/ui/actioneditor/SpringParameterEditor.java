package com.jvn.editor.ui.actioneditor;

import java.util.function.Consumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.editor.ui.CssIcon;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;

/**
 * Interactive editor for SPRING and DAMPED_SPRING easing parameters.
 * Provides labeled sliders with numeric fields for each parameter and a
 * real-time oscillation preview canvas showing the spring response curve
 * with an animated dot.
 */
final class SpringParameterEditor extends VBox {
    private static final String SECTION_STYLE =
        "-fx-background-color: #121212; -fx-border-color: #2f2f2f; -fx-border-radius: 8; " +
        "-fx-background-radius: 8; -fx-padding: 10 12;";
    private static final String FIELD_STYLE =
        "-fx-background-color: #111111; -fx-text-fill: #ececec; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 8; -fx-font-size: 12px;";
    private static final String FIELD_STYLE_ERROR =
        "-fx-background-color: #111111; -fx-text-fill: #ececec; -fx-border-color: #e05577; " +
        "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 5 8; -fx-font-size: 12px;";
    private static final String TITLE_STYLE =
        "-fx-text-fill: #ececec; -fx-font-size: 11px; -fx-font-weight: bold;";
    private static final String LABEL_STYLE =
        "-fx-text-fill: #a0a0a0; -fx-font-size: 10px;";
    private static final String BUTTON_STYLE =
        "-fx-background-color: #242424; -fx-text-fill: #e6e6e6; -fx-background-radius: 6; " +
        "-fx-border-color: #3d3d3d; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String TOGGLE_BUTTON_STYLE =
        "-fx-background-color: #3b3b3b; -fx-text-fill: #f1f1f1; -fx-background-radius: 6; " +
        "-fx-border-color: #6a6a6a; -fx-border-radius: 6; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";

    private static final Color BG_COLOR = Color.web("#0e0e0e");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color AXIS_COLOR = Color.web("#595959");
    private static final Color CURVE_COLOR = Color.web("#8ec8f6");
    private static final Color TARGET_LINE_COLOR = Color.web("#4a7a4a");
    private static final Color OVERSHOOT_COLOR = Color.web("#f0b673", 0.3);
    private static final Color DOT_COLOR = Color.web("#ff6b8a");
    private static final Color SETTLE_BAND_COLOR = Color.web("#3a6a3a", 0.2);
    private static final Font LABEL_FONT = Font.font(Font.getDefault().getFamily(), 9);
    private static final Font BADGE_FONT = Font.font(Font.getDefault().getFamily(), 10);

    private static final double CANVAS_HEIGHT = 160;
    private static final double H_PADDING = 24;
    private static final double TOP_PADDING = 14;
    private static final double BOTTOM_PADDING = 22;
    private static final double DOT_RADIUS = 4.5;

    private boolean isDampedSpring = false;

    // SPRING: stiffness, damping, mass, velocity
    private final Slider slStiffness;
    private final Slider slDamping;
    private final Slider slMass;
    private final Slider slVelocity;
    private final TextField tfStiffness;
    private final TextField tfDamping;
    private final TextField tfMass;
    private final TextField tfVelocity;

    // DAMPED_SPRING: frequency, dampingRatio, response, velocity
    private final Slider slFrequency;
    private final Slider slDampingRatio;
    private final Slider slResponse;
    private final Slider slDsVelocity;
    private final TextField tfFrequency;
    private final TextField tfDampingRatio;
    private final TextField tfResponse;
    private final TextField tfDsVelocity;

    private final VBox springBox;
    private final VBox dampedSpringBox;
    private final Canvas previewCanvas;
    private final Button btnPlayStop;
    private final Label lblSpecReadout;

    private Consumer<EasingSpec> onSpecChanged;
    private boolean updatingUi = false;

    // Animation state
    private boolean animating = false;
    private double animProgress = 0.0;
    private long animStartNanos = 0;
    private AnimationTimer animTimer;

    SpringParameterEditor() {
        setSpacing(8);
        setStyle(SECTION_STYLE);
        setMaxWidth(Double.MAX_VALUE);

        Label header = new Label("Spring Parameters");
        header.setStyle(TITLE_STYLE);

        // --- SPRING sliders ---
        slStiffness = createSlider(1, 500, 180, "Spring stiffness (higher = snappier)");
        slDamping = createSlider(0, 80, 20, "Damping coefficient (higher = less oscillation)");
        slMass = createSlider(0.01, 10, 1, "Mass (higher = more inertia)");
        slVelocity = createSlider(-20, 20, 0, "Initial velocity");
        tfStiffness = createField("180");
        tfDamping = createField("20");
        tfMass = createField("1.0");
        tfVelocity = createField("0.0");

        springBox = new VBox(6,
            buildParamRow("Stiffness", slStiffness, tfStiffness, "Higher = snappier response"),
            buildParamRow("Damping", slDamping, tfDamping, "Higher = less bounce"),
            buildParamRow("Mass", slMass, tfMass, "Higher = more inertia"),
            buildParamRow("Velocity", slVelocity, tfVelocity, "Initial launch velocity")
        );
        springBox.setMaxWidth(Double.MAX_VALUE);

        // --- DAMPED_SPRING sliders ---
        slFrequency = createSlider(0.1, 10, 2.2, "Natural frequency (oscillations per unit time)");
        slDampingRatio = createSlider(0, 2, 0.38, "Damping ratio (0=no damping, 1=critical, >1=overdamped)");
        slResponse = createSlider(0.1, 5, 1.0, "Response speed multiplier");
        slDsVelocity = createSlider(-20, 20, 0, "Initial velocity");
        tfFrequency = createField("2.2");
        tfDampingRatio = createField("0.38");
        tfResponse = createField("1.0");
        tfDsVelocity = createField("0.0");

        dampedSpringBox = new VBox(6,
            buildParamRow("Frequency", slFrequency, tfFrequency, "Natural frequency"),
            buildParamRow("Damping Ratio", slDampingRatio, tfDampingRatio, "0=bouncy, 1=critical, >1=sluggish"),
            buildParamRow("Response", slResponse, tfResponse, "Speed multiplier"),
            buildParamRow("Velocity", slDsVelocity, tfDsVelocity, "Initial velocity")
        );
        dampedSpringBox.setMaxWidth(Double.MAX_VALUE);

        // Preview canvas. Keep the Canvas unmanaged so its live width does not
        // feed back into this editor's preferred-width calculation.
        previewCanvas = new Canvas(480, CANVAS_HEIGHT);
        previewCanvas.setManaged(false);
        Pane previewCanvasHost = new Pane(previewCanvas);
        previewCanvasHost.setMinWidth(0);
        previewCanvasHost.setPrefHeight(CANVAS_HEIGHT);
        previewCanvasHost.setMinHeight(CANVAS_HEIGHT);
        previewCanvasHost.setMaxHeight(CANVAS_HEIGHT);
        previewCanvasHost.setMaxWidth(Double.MAX_VALUE);
        previewCanvasHost.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            resizePreviewCanvas(newWidth.doubleValue());
        });

        btnPlayStop = new Button("Preview", CssIcon.play("#d6dbe5"));
        btnPlayStop.setStyle(BUTTON_STYLE);
        btnPlayStop.setTooltip(new Tooltip("Play/stop the animated spring response preview"));
        btnPlayStop.setOnAction(e -> toggleAnimation());

        lblSpecReadout = new Label();
        lblSpecReadout.setStyle(LABEL_STYLE);
        lblSpecReadout.setWrapText(true);

        HBox canvasToolbar = new HBox(8, new Label("Spring Response"), createSpacer(), btnPlayStop);
        canvasToolbar.setAlignment(Pos.CENTER_LEFT);
        ((Label) canvasToolbar.getChildren().get(0)).setStyle(TITLE_STYLE);

        getChildren().addAll(header, springBox, dampedSpringBox, canvasToolbar, previewCanvasHost, lblSpecReadout);

        // Wire slider listeners
        wireSliderPair(slStiffness, tfStiffness);
        wireSliderPair(slDamping, tfDamping);
        wireSliderPair(slMass, tfMass);
        wireSliderPair(slVelocity, tfVelocity);
        wireSliderPair(slFrequency, tfFrequency);
        wireSliderPair(slDampingRatio, tfDampingRatio);
        wireSliderPair(slResponse, tfResponse);
        wireSliderPair(slDsVelocity, tfDsVelocity);

        setSpringType(false);
        drawPreview();
    }

    void setSpringType(boolean dampedSpring) {
        this.isDampedSpring = dampedSpring;
        springBox.setVisible(!dampedSpring);
        springBox.setManaged(!dampedSpring);
        dampedSpringBox.setVisible(dampedSpring);
        dampedSpringBox.setManaged(dampedSpring);
        drawPreview();
    }

    void setSpec(EasingSpec spec) {
        if (spec == null) return;
        updatingUi = true;
        try {
            if (spec.getType() == Easing.Type.SPRING) {
                double[] params = Easing.coerceParameters(Easing.Type.SPRING, spec.getParameters());
                setSliderAndField(slStiffness, tfStiffness, params[0]);
                setSliderAndField(slDamping, tfDamping, params[1]);
                setSliderAndField(slMass, tfMass, params[2]);
                setSliderAndField(slVelocity, tfVelocity, params[3]);
                setSpringType(false);
            } else if (spec.getType() == Easing.Type.DAMPED_SPRING) {
                double[] params = Easing.coerceParameters(Easing.Type.DAMPED_SPRING, spec.getParameters());
                setSliderAndField(slFrequency, tfFrequency, params[0]);
                setSliderAndField(slDampingRatio, tfDampingRatio, params[1]);
                setSliderAndField(slResponse, tfResponse, params[2]);
                setSliderAndField(slDsVelocity, tfDsVelocity, params[3]);
                setSpringType(true);
            }
        } finally {
            updatingUi = false;
        }
        drawPreview();
    }

    EasingSpec resolveSpec() {
        if (isDampedSpring) {
            return EasingSpec.dampedSpring(
                slFrequency.getValue(),
                slDampingRatio.getValue(),
                slResponse.getValue(),
                slDsVelocity.getValue()
            );
        }
        return EasingSpec.spring(
            slStiffness.getValue(),
            slDamping.getValue(),
            slMass.getValue(),
            slVelocity.getValue()
        );
    }

    void setOnSpecChanged(Consumer<EasingSpec> callback) {
        this.onSpecChanged = callback;
    }

    void stopAnimation() {
        animating = false;
        if (animTimer != null) animTimer.stop();
        animProgress = 0.0;
        btnPlayStop.setText("Preview");
        btnPlayStop.setGraphic(CssIcon.play("#d6dbe5"));
        btnPlayStop.setStyle(BUTTON_STYLE);
        drawPreview();
    }

    // ---- Private ----

    private void toggleAnimation() {
        if (animating) {
            stopAnimation();
        } else {
            animating = true;
            animProgress = 0.0;
            animStartNanos = System.nanoTime();
            btnPlayStop.setText("Stop");
            btnPlayStop.setGraphic(CssIcon.stop("#f39aaa"));
            btnPlayStop.setStyle(TOGGLE_BUTTON_STYLE);
            if (animTimer == null) {
                animTimer = new AnimationTimer() {
                    @Override
                    public void handle(long now) {
                        if (!animating) { stop(); return; }
                        double elapsed = (now - animStartNanos) / 1_000_000_000.0;
                        animProgress = elapsed / 2.0; // 2-second cycle
                        if (animProgress >= 1.0) {
                            animProgress = 0.0;
                            animStartNanos = now;
                        }
                        drawPreview();
                    }
                };
            }
            animTimer.start();
        }
    }

    private void notifyChange() {
        if (updatingUi) return;
        EasingSpec spec = resolveSpec();
        lblSpecReadout.setText(spec.toDslString());
        drawPreview();
        if (onSpecChanged != null) onSpecChanged.accept(spec);
    }

    private void drawPreview() {
        double w = previewCanvas.getWidth();
        double h = previewCanvas.getHeight();
        GraphicsContext gc = previewCanvas.getGraphicsContext2D();

        // Background
        gc.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#121315")), new Stop(1, BG_COLOR)));
        gc.fillRect(0, 0, w, h);

        double plotX = H_PADDING;
        double plotY = TOP_PADDING;
        double plotW = Math.max(1, w - H_PADDING - 12);
        double plotH = Math.max(1, h - TOP_PADDING - BOTTOM_PADDING);
        if (plotW < 20 || plotH < 20) return;

        EasingSpec spec = resolveSpec();
        lblSpecReadout.setText(spec.toDslString());

        // Sample curve to find Y range
        int samples = 120;
        double[] values = new double[samples + 1];
        double minV = 0, maxV = 1;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double v = Easing.apply(spec, t);
            values[i] = v;
            if (Double.isFinite(v)) {
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
        }
        // Pad range
        double span = Math.max(0.3, maxV - minV);
        double margin = span * 0.1;
        double rangeMin = minV - margin;
        double rangeMax = maxV + margin;

        // Settle band (±2% around target 1.0)
        double settleTop = screenY(1.02, plotY, plotH, rangeMin, rangeMax);
        double settleBot = screenY(0.98, plotY, plotH, rangeMin, rangeMax);
        gc.setFill(SETTLE_BAND_COLOR);
        gc.fillRect(plotX, Math.min(settleTop, settleBot), plotW, Math.abs(settleBot - settleTop));

        // Grid lines
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (int i = 1; i <= 3; i++) {
            double gx = plotX + (i / 4.0) * plotW;
            gc.strokeLine(gx, plotY, gx, plotY + plotH);
        }
        for (double v : new double[]{0.0, 0.5, 1.0}) {
            double gy = screenY(v, plotY, plotH, rangeMin, rangeMax);
            if (gy >= plotY - 1 && gy <= plotY + plotH + 1) {
                gc.strokeLine(plotX, gy, plotX + plotW, gy);
            }
        }

        // Target line at 1.0
        double targetY = screenY(1.0, plotY, plotH, rangeMin, rangeMax);
        gc.setStroke(TARGET_LINE_COLOR);
        gc.setLineWidth(1);
        gc.setLineDashes(5, 3);
        gc.strokeLine(plotX, targetY, plotX + plotW, targetY);
        gc.setLineDashes((double[]) null);

        // Overshoot highlight
        for (int i = 1; i <= samples; i++) {
            if (values[i] > 1.0 + 0.005) {
                double x1 = plotX + ((double) (i - 1) / samples) * plotW;
                double x2 = plotX + ((double) i / samples) * plotW;
                double y1 = screenY(Math.max(values[i - 1], 1.0), plotY, plotH, rangeMin, rangeMax);
                double y2 = screenY(values[i], plotY, plotH, rangeMin, rangeMax);
                gc.setFill(OVERSHOOT_COLOR);
                gc.fillRect(x1, Math.min(y1, y2), x2 - x1, Math.abs(y2 - y1) + 1);
            }
        }

        // Axis frame
        gc.setStroke(AXIS_COLOR);
        gc.setLineWidth(1);
        double zeroY = screenY(0.0, plotY, plotH, rangeMin, rangeMax);
        double oneY = screenY(1.0, plotY, plotH, rangeMin, rangeMax);
        gc.strokeRect(plotX, Math.min(oneY, zeroY), plotW, Math.abs(oneY - zeroY));

        // Spring response curve
        gc.setStroke(CURVE_COLOR);
        gc.setLineWidth(2);
        gc.beginPath();
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double sx = plotX + t * plotW;
            double sy = screenY(values[i], plotY, plotH, rangeMin, rangeMax);
            if (i == 0) gc.moveTo(sx, sy);
            else gc.lineTo(sx, sy);
        }
        gc.stroke();

        // Animated dot
        if (animating && animProgress >= 0.0) {
            double ap = Math.min(1.0, animProgress);
            double av = Easing.apply(spec, ap);
            double adx = plotX + ap * plotW;
            double ady = screenY(av, plotY, plotH, rangeMin, rangeMax);

            gc.setFill(DOT_COLOR);
            gc.fillOval(adx - DOT_RADIUS, ady - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);
            gc.setStroke(Color.web("#ffffff", 0.7));
            gc.setLineWidth(1.2);
            gc.strokeOval(adx - DOT_RADIUS, ady - DOT_RADIUS, DOT_RADIUS * 2, DOT_RADIUS * 2);

            // Side motion bar: maps value to vertical position
            double barX = plotX + plotW + 4;
            double barW = 6;
            gc.setFill(Color.web("#1a1a1a"));
            gc.fillRoundRect(barX, plotY, barW, plotH, 3, 3);
            double dotBarY = screenY(av, plotY, plotH, rangeMin, rangeMax);
            gc.setFill(DOT_COLOR);
            gc.fillOval(barX - 1, dotBarY - 4, barW + 2, 8);
        }

        // Axis labels
        gc.setFill(Color.web("#787878"));
        gc.setFont(LABEL_FONT);
        gc.fillText("0", plotX - 2, plotY + plotH + 12);
        gc.fillText("1", plotX + plotW - 4, plotY + plotH + 12);
        gc.fillText("1.0", plotX - 22, oneY + 4);
        gc.fillText("0.0", plotX - 22, zeroY + 4);

        // Describe overshoot / settling
        double peak = 0;
        for (double v : values) peak = Math.max(peak, v);
        double overshootPct = peak > 1.001 ? (peak - 1.0) * 100.0 : 0;
        String info = overshootPct > 0.5
            ? String.format("Peak overshoot: %.1f%%", overshootPct)
            : "No overshoot (critically or over-damped)";
        gc.setFill(Color.web("#909090"));
        gc.setFont(BADGE_FONT);
        gc.fillText(info, plotX + 4, plotY + 11);

        if (isDampedSpring) {
            double ratio = slDampingRatio.getValue();
            String damping = ratio < 0.98 ? "Under-damped" : ratio > 1.02 ? "Over-damped" : "Critically damped";
            gc.fillText(damping, plotX + plotW - 90, plotY + 11);
        }
    }

    private void resizePreviewCanvas(double hostWidth) {
        double width = Math.max(1.0, hostWidth);
        if (Math.abs(previewCanvas.getWidth() - width) > 0.5) {
            previewCanvas.setWidth(width);
        }
        if (Math.abs(previewCanvas.getHeight() - CANVAS_HEIGHT) > 0.5) {
            previewCanvas.setHeight(CANVAS_HEIGHT);
        }
        drawPreview();
    }

    private double screenY(double value, double plotY, double plotH, double rangeMin, double rangeMax) {
        double span = Math.max(0.0001, rangeMax - rangeMin);
        double normalized = (rangeMax - value) / span;
        return plotY + Math.max(-0.25, Math.min(1.25, normalized)) * plotH;
    }

    private Slider createSlider(double min, double max, double value, String tooltip) {
        Slider slider = new Slider(min, max, value);
        slider.setTooltip(new Tooltip(tooltip));
        slider.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return slider;
    }

    private TextField createField(String value) {
        TextField field = new TextField(value);
        field.setPrefWidth(68);
        field.setStyle(FIELD_STYLE);
        return field;
    }

    private HBox buildParamRow(String label, Slider slider, TextField field, String hint) {
        Label lbl = new Label(label);
        lbl.setStyle(LABEL_STYLE);
        lbl.setMinWidth(80);
        lbl.setTooltip(new Tooltip(hint));
        HBox row = new HBox(8, lbl, slider, field);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(slider, Priority.ALWAYS);
        return row;
    }

    private void wireSliderPair(Slider slider, TextField field) {
        slider.valueProperty().addListener((obs, o, n) -> {
            if (updatingUi) return;
            field.setText(formatParam(n.doubleValue()));
            notifyChange();
        });
        field.setOnAction(e -> applyFieldToSlider(field, slider));
        field.focusedProperty().addListener((obs, was, is) -> {
            if (!is) applyFieldToSlider(field, slider);
        });
    }

    private void applyFieldToSlider(TextField field, Slider slider) {
        if (updatingUi) return;
        try {
            double v = Double.parseDouble(field.getText().trim());
            v = Math.max(slider.getMin(), Math.min(slider.getMax(), v));
            updatingUi = true;
            slider.setValue(v);
            field.setText(formatParam(v));
            field.setStyle(FIELD_STYLE);
            updatingUi = false;
            notifyChange();
        } catch (NumberFormatException ex) {
            field.setStyle(FIELD_STYLE_ERROR);
        }
    }

    private void setSliderAndField(Slider slider, TextField field, double value) {
        double clamped = Math.max(slider.getMin(), Math.min(slider.getMax(), value));
        slider.setValue(clamped);
        field.setText(formatParam(clamped));
    }

    private String formatParam(double value) {
        if (Math.abs(value) < 0.005) return "0";
        if (Math.abs(value - Math.rint(value)) < 0.005) return String.format("%.0f", value);
        return String.format("%.2f", value);
    }

    private Region createSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
