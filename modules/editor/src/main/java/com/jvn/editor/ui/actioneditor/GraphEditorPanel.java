package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class GraphEditorPanel extends Pane {

    // Layout constants (mirrored from TimelinePanel)
    private static final double LABEL_WIDTH   = 140;
    private static final double HEADER_HEIGHT = 30;
    private static final double DOT_R         = 5.5;
    private static final int    CURVE_SAMPLES = 60;

    // Colours
    private static final Color BG        = Color.web("#121212");
    private static final Color BG_LABEL  = Color.web("#181818");
    private static final Color GRID_H    = Color.web("#2a2a2a");
    private static final Color GRID_V    = Color.web("#252525");
    private static final Color ZERO_LINE = Color.web("#3a3a3a");
    private static final Color TEXT_CLR  = Color.web("#e6e6e6");
    private static final Color DIM_CLR   = Color.web("#666666");
    private static final Color PLAYHEAD  = Color.web("#f38ba8");
    private static final Color KF_SEL    = Color.web("#f0b673");

    // === State synced from TimelinePanel ===
    private final AnimationProject project;
    private final PuppeteerCommand.Stack commandStack;
    private final KeyframeSelectionModel selectionModel;

    private double scrollX        = 0;
    private double pixelsPerMs    = 0.2;
    private String selectedEntity;
    private boolean selectedGroup;
    private PropertyType selectedProperty;
    private Keyframe selectedKeyframe;

    // === Y-axis viewport ===
    private double valueViewMin = -1;
    private double valueViewMax =  1;

    // === Hover ===
    private double hoverX = Double.NaN;
    private double hoverY = Double.NaN;
    private Keyframe hoverKf;
    private PropertyType hoverProp;

    // === Drag state ===
    private boolean draggingPlayhead;
    private boolean draggingDot;
    private boolean middlePanning;
    private Keyframe dragKf;
    private PropertyType dragProp;
    private EntityTrack dragTrack;
    private double dragStartTime;
    private double dragStartValue;
    private double dragAnchorX;
    private double dragAnchorY;
    // For middle-mouse pan
    private double middlePanStartX;
    private double middlePanStartY;
    private double middlePanScrollX;
    private double middlePanViewMin;
    private double middlePanViewMax;

    // === Callbacks ===
    private Runnable onEdited;
    private Consumer<Keyframe> onKeyframeSelected;
    private Consumer<Double> onPlayheadChanged;
    private Runnable onOpenEasingEditor;

    private final Canvas canvas = new Canvas();

    public GraphEditorPanel(AnimationProject project,
                             PuppeteerCommand.Stack commandStack,
                             KeyframeSelectionModel selectionModel) {
        this.project       = project;
        this.commandStack  = commandStack;
        this.selectionModel = selectionModel;

        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener((o, a, b)  -> render());
        canvas.heightProperty().addListener((o, a, b) -> render());

        canvas.setOnMousePressed(this::onPress);
        canvas.setOnMouseDragged(this::onDrag);
        canvas.setOnMouseReleased(this::onRelease);
        canvas.setOnMouseClicked(this::onClick);
        canvas.setOnMouseMoved(this::onMoved);
        canvas.setOnMouseExited(e -> { hoverX = Double.NaN; hoverKf = null; render(); });
        addEventFilter(ScrollEvent.SCROLL, this::onScroll);
    }

    // -------------------------------------------------------------------------
    // Public API — setters / sync
    // -------------------------------------------------------------------------

    public void setOnEdited(Runnable r)                     { onEdited = r; }
    public void setOnKeyframeSelected(Consumer<Keyframe> c) { onKeyframeSelected = c; }
    public void setOnPlayheadChanged(Consumer<Double> c)    { onPlayheadChanged = c; }
    public void setOnOpenEasingEditor(Runnable r)           { onOpenEasingEditor = r; }

    public void sync(double scrollX, double pixelsPerMs,
                     String selectedEntity, boolean selectedGroup,
                     PropertyType selectedProperty, Keyframe selectedKeyframe) {
        boolean entityChanged = !Objects.equals(this.selectedEntity, selectedEntity)
                                || this.selectedGroup != selectedGroup;
        this.scrollX         = scrollX;
        this.pixelsPerMs     = pixelsPerMs;
        this.selectedEntity  = selectedEntity;
        this.selectedGroup   = selectedGroup;
        this.selectedProperty = selectedProperty;
        this.selectedKeyframe = selectedKeyframe;
        if (entityChanged) autoFitY();
        render();
    }

    public void autoFitY() {
        EntityTrack track = resolveTrack();
        if (track == null) { valueViewMin = -1; valueViewMax = 1; return; }
        List<PropertyType> props = animatedProperties(track);
        if (props.isEmpty()) { valueViewMin = -1; valueViewMax = 1; return; }

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (PropertyType p : props) {
            for (Keyframe kf : track.getKeyframes(p)) {
                min = Math.min(min, kf.getValue());
                max = Math.max(max, kf.getValue());
            }
        }
        if (min == max) { min -= 1; max += 1; }
        double pad = Math.max(0.5, (max - min) * 0.2);
        valueViewMin = min - pad;
        valueViewMax = max + pad;
    }

    // -------------------------------------------------------------------------
    // Coordinate mapping
    // -------------------------------------------------------------------------

    private double timeToX(double ms)  { return LABEL_WIDTH + ms * pixelsPerMs - scrollX; }
    private double xToTime(double x)   { return (x - LABEL_WIDTH + scrollX) / pixelsPerMs; }
    private double graphH()            { return Math.max(1, canvas.getHeight() - HEADER_HEIGHT); }

    private double valueToY(double v) {
        return HEADER_HEIGHT + (valueViewMax - v) / (valueViewMax - valueViewMin) * graphH();
    }

    private double yToValue(double y) {
        return valueViewMax - (y - HEADER_HEIGHT) / graphH() * (valueViewMax - valueViewMin);
    }

    // -------------------------------------------------------------------------
    // Track / property resolution
    // -------------------------------------------------------------------------

    private EntityTrack resolveTrack() {
        if (selectedEntity == null || selectedEntity.isBlank()) return null;
        if (TimelinePanel.RUNTIME_CAMERA_TARGET.equals(selectedEntity)) {
            EntityTrack t = project.getTrack(TimelinePanel.RUNTIME_CAMERA_TARGET);
            if (t != null) return t;
            for (EntityTrack track : project.getTracks()) {
                if (track != null && (track.hasKeyframes(PropertyType.CAMERA_X)
                        || track.hasKeyframes(PropertyType.CAMERA_Y)
                        || track.hasKeyframes(PropertyType.CAMERA_ZOOM))) return track;
            }
            return null;
        }
        if (selectedGroup) {
            EntityGroup group = project.getGroup(selectedEntity);
            return group != null ? group.getGroupTrack() : null;
        }
        return project.getTrack(selectedEntity);
    }

    private List<PropertyType> animatedProperties(EntityTrack track) {
        if (track == null) return List.of();
        List<PropertyType> result = new ArrayList<>();
        for (PropertyType p : PropertyType.values()) {
            if (track.hasKeyframes(p)) result.add(p);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Hit detection
    // -------------------------------------------------------------------------

    private record DotHit(EntityTrack track, PropertyType prop, Keyframe kf) {}

    private DotHit findDotAt(double mx, double my) {
        EntityTrack track = resolveTrack();
        if (track == null) return null;
        for (PropertyType prop : animatedProperties(track)) {
            for (Keyframe kf : track.getKeyframes(prop)) {
                double kx = timeToX(kf.getTimeMs());
                double ky = valueToY(kf.getValue());
                if (Math.hypot(mx - kx, my - ky) <= DOT_R + 4) {
                    return new DotHit(track, prop, kf);
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Mouse handlers
    // -------------------------------------------------------------------------

    private void onPress(MouseEvent e) {
        double x = e.getX(), y = e.getY();

        if (e.getButton() == MouseButton.MIDDLE) {
            middlePanning     = true;
            middlePanStartX   = x;
            middlePanStartY   = y;
            middlePanScrollX  = scrollX;
            middlePanViewMin  = valueViewMin;
            middlePanViewMax  = valueViewMax;
            canvas.setCursor(javafx.scene.Cursor.MOVE);
            return;
        }

        if (e.getButton() != MouseButton.PRIMARY) return;

        if (y <= HEADER_HEIGHT + 5) {
            draggingPlayhead = true;
            seekTo(x);
            return;
        }

        DotHit hit = findDotAt(x, y);
        if (hit != null) {
            selectedKeyframe  = hit.kf();
            selectedProperty  = hit.prop();
            selectionModel.clearSelection();
            selectionModel.select(KeyframeSelectionModel.ref(selectedEntity, hit.prop(), hit.kf()));
            if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);

            draggingDot    = true;
            dragKf         = hit.kf();
            dragProp       = hit.prop();
            dragTrack      = hit.track();
            dragStartTime  = hit.kf().getTimeMs();
            dragStartValue = hit.kf().getValue();
            dragAnchorX    = x;
            dragAnchorY    = y;
            render();
        }
    }

    private void onDrag(MouseEvent e) {
        double x = e.getX(), y = e.getY();

        if (middlePanning) {
            double dtPx = x - middlePanStartX;
            scrollX = Math.max(0, middlePanScrollX - dtPx);
            double dvPx = y - middlePanStartY;
            double dv = dvPx / graphH() * (middlePanViewMax - middlePanViewMin);
            valueViewMin = middlePanViewMin + dv;
            valueViewMax = middlePanViewMax + dv;
            render();
            return;
        }

        if (draggingPlayhead) { seekTo(x); return; }

        if (draggingDot && dragKf != null) {
            double newTime  = Math.max(0, dragStartTime  + (x - dragAnchorX) / pixelsPerMs);
            double newValue = dragStartValue - (y - dragAnchorY) / graphH() * (valueViewMax - valueViewMin);

            if (e.isShiftDown()) {
                if (Math.abs(x - dragAnchorX) >= Math.abs(y - dragAnchorY)) {
                    newValue = dragStartValue;  // constrain to time axis
                } else {
                    newTime = dragStartTime;    // constrain to value axis
                }
            }

            dragKf.setTimeMs(newTime);
            dragKf.setValue(newValue);
            dragTrack.sortKeyframes(dragProp);
            render();
        }
    }

    private void onRelease(MouseEvent e) {
        if (middlePanning) {
            middlePanning = false;
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            return;
        }
        draggingPlayhead = false;

        if (draggingDot && dragKf != null) {
            double endTime  = dragKf.getTimeMs();
            double endValue = dragKf.getValue();

            List<PuppeteerCommand> cmds = new ArrayList<>();
            if (Math.abs(endTime - dragStartTime) > 0.01)
                cmds.add(PuppeteerCommand.moveKeyframe(dragKf, dragStartTime, endTime));
            if (Math.abs(endValue - dragStartValue) > 1e-9)
                cmds.add(PuppeteerCommand.changeValue(dragKf, dragStartValue, endValue));

            if (!cmds.isEmpty()) {
                if (commandStack != null)
                    commandStack.execute(PuppeteerCommand.composite("Move keyframe", cmds));
                if (onEdited != null) onEdited.run();
            }

            draggingDot = false;
            dragKf = null;
        }
    }

    private void onClick(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            DotHit hit = findDotAt(e.getX(), e.getY());
            if (hit != null) {
                selectedKeyframe = hit.kf();
                selectedProperty = hit.prop();
                if (onOpenEasingEditor != null) onOpenEasingEditor.run();
            }
        }
    }

    private void onMoved(MouseEvent e) {
        hoverX = e.getX();
        hoverY = e.getY();
        DotHit hit = findDotAt(e.getX(), e.getY());
        hoverKf   = hit != null ? hit.kf()   : null;
        hoverProp = hit != null ? hit.prop()  : null;
        render();
    }

    private void onScroll(ScrollEvent e) {
        if (e.isControlDown()) {
            // Zoom time axis, centered on mouse X
            double mx = e.getX();
            double timeBefore = xToTime(mx);
            double factor = e.getDeltaY() > 0 ? 1.2 : 0.8;
            pixelsPerMs = Math.max(0.01, Math.min(5.0, pixelsPerMs * factor));
            scrollX = Math.max(0, timeBefore * pixelsPerMs - (mx - LABEL_WIDTH));
        } else if (e.isAltDown()) {
            // Zoom value axis, centered on mouse Y
            double my = e.getY();
            double vBefore = yToValue(my);
            double factor = e.getDeltaY() > 0 ? 1.2 : 0.8;
            double range = (valueViewMax - valueViewMin) * factor;
            double frac  = (my - HEADER_HEIGHT) / graphH();
            valueViewMin = vBefore - range * frac;
            valueViewMax = vBefore + range * (1 - frac);
        } else {
            // Pan: X = time scroll, Y = value scroll
            scrollX = Math.max(0, scrollX - e.getDeltaX());
            double dv = e.getDeltaY() / graphH() * (valueViewMax - valueViewMin);
            valueViewMin += dv;
            valueViewMax += dv;
        }
        e.consume();
        render();
    }

    private void seekTo(double canvasX) {
        double t = Math.max(0, xToTime(canvasX));
        project.setPlayheadMs(t);
        if (onPlayheadChanged != null) onPlayheadChanged.accept(t);
        render();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    public void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        drawValueGrid(gc, w, h);
        drawTimeGridAndHeader(gc, w, h);
        drawCurves(gc, w);
        drawKeyframeDots(gc, w);
        drawPlayhead(gc, h);
        drawLabelColumn(gc, h);       // drawn last to overdraw curves in label area
        drawHoverReadout(gc, w);

        // Column separator
        gc.setStroke(Color.web("#333333"));
        gc.setLineWidth(1);
        gc.strokeLine(LABEL_WIDTH, 0, LABEL_WIDTH, h);
    }

    private void drawValueGrid(GraphicsContext gc, double w, double h) {
        double range = valueViewMax - valueViewMin;
        if (range <= 0) return;
        double step = niceStep(range / 7.0);
        double first = Math.ceil(valueViewMin / step) * step;

        for (double v = first; v <= valueViewMax + 1e-9; v += step) {
            double ky = valueToY(v);
            if (ky < HEADER_HEIGHT || ky > h + 1) continue;

            boolean isZero = Math.abs(v) < step * 0.01;
            gc.setStroke(isZero ? ZERO_LINE : GRID_H);
            gc.setLineWidth(isZero ? 1.0 : 0.5);
            gc.strokeLine(LABEL_WIDTH, ky, w, ky);

            // Value label
            gc.setFill(DIM_CLR);
            gc.setFont(Font.font(9));
            gc.fillText(formatValue(v), LABEL_WIDTH + 4, ky - 2);
        }
    }

    private void drawTimeGridAndHeader(GraphicsContext gc, double w, double h) {
        // Header background
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(LABEL_WIDTH, 0, w - LABEL_WIDTH, HEADER_HEIGHT);

        double duration   = project.getTotalDurationMs();
        double step       = computeGridStep();
        double viewStart  = Math.max(0.0, scrollX / pixelsPerMs);
        double viewEnd    = Math.min(duration, (scrollX + w - LABEL_WIDTH) / pixelsPerMs);

        // Vertical time grid lines
        gc.setStroke(GRID_V);
        gc.setLineWidth(0.5);
        for (double t = Math.floor(viewStart / step) * step; t <= viewEnd; t += step) {
            if (t < 0) continue;
            double x = timeToX(t);
            if (x >= LABEL_WIDTH && x <= w) gc.strokeLine(x, HEADER_HEIGHT, x, h);
        }

        // Tick marks and labels in header
        gc.setStroke(GRID_H);
        gc.setLineWidth(1);
        gc.setFill(TEXT_CLR);
        gc.setFont(Font.font(10));
        for (double t = Math.floor(viewStart / step) * step; t <= viewEnd; t += step) {
            if (t < 0) continue;
            double x = timeToX(t);
            if (x < LABEL_WIDTH || x > w) continue;
            gc.strokeLine(x, HEADER_HEIGHT - 6, x, HEADER_HEIGHT);
            gc.fillText(formatTime(t), x - 13, HEADER_HEIGHT - 10);
        }

        gc.setStroke(Color.web("#333333"));
        gc.strokeLine(0, HEADER_HEIGHT, w, HEADER_HEIGHT);
    }

    private void drawCurves(GraphicsContext gc, double w) {
        EntityTrack track = resolveTrack();
        if (track == null) return;
        List<PropertyType> props = animatedProperties(track);

        // Two passes: dim non-selected first, selected on top
        for (int pass = 0; pass < 2; pass++) {
            for (PropertyType prop : props) {
                boolean isSelProp = prop == selectedProperty;
                if (pass == 0 && isSelProp) continue;
                if (pass == 1 && !isSelProp) continue;

                List<Keyframe> kfs = new ArrayList<>(track.getKeyframes(prop));
                kfs.sort(Comparator.comparingDouble(Keyframe::getTimeMs));

                Color c = trackColorFor(prop);
                gc.setStroke(isSelProp ? c : c.deriveColor(0, 0.7, 0.85, 0.35));
                gc.setLineWidth(isSelProp ? 2.0 : 1.0);
                gc.setLineDashes((double[]) null);

                if (kfs.size() >= 2) {
                    gc.beginPath();
                    boolean started = false;
                    for (int i = 0; i < kfs.size() - 1; i++) {
                        Keyframe left  = kfs.get(i);
                        Keyframe right = kfs.get(i + 1);
                        double x1 = timeToX(left.getTimeMs());
                        double x2 = timeToX(right.getTimeMs());
                        if (x2 < LABEL_WIDTH || x1 > w) continue;
                        int samples = Math.max(8, Math.min(CURVE_SAMPLES,
                            (int) ((x2 - x1) / 2)));
                        for (int s = 0; s <= samples; s++) {
                            double frac = (double) s / samples;
                            double t    = left.getTimeMs() + frac * (right.getTimeMs() - left.getTimeMs());
                            double v    = track.getValueAt(prop, t);
                            double cx   = timeToX(t);
                            double cy   = valueToY(v);
                            if (!started) { gc.moveTo(cx, cy); started = true; }
                            else gc.lineTo(cx, cy);
                        }
                    }
                    if (started) gc.stroke();
                }

                // Single-keyframe: draw horizontal dashed line
                if (kfs.size() == 1) {
                    double ky = valueToY(kfs.get(0).getValue());
                    gc.setLineDashes(4, 3);
                    gc.strokeLine(LABEL_WIDTH, ky, w, ky);
                    gc.setLineDashes((double[]) null);
                }
            }
        }
    }

    private void drawKeyframeDots(GraphicsContext gc, double w) {
        EntityTrack track = resolveTrack();
        if (track == null) return;
        List<PropertyType> props = animatedProperties(track);

        for (int pass = 0; pass < 2; pass++) {
            for (PropertyType prop : props) {
                boolean isSelProp = prop == selectedProperty;
                if (pass == 0 && isSelProp) continue;
                if (pass == 1 && !isSelProp) continue;

                Color c = trackColorFor(prop);
                for (Keyframe kf : track.getKeyframes(prop)) {
                    double kx = timeToX(kf.getTimeMs());
                    double ky = valueToY(kf.getValue());
                    if (kx < LABEL_WIDTH - DOT_R - 2 || kx > w + DOT_R) continue;

                    boolean isSelKf = kf == selectedKeyframe;
                    boolean isHov   = kf == hoverKf && prop == hoverProp;
                    double r = (isSelKf || isHov) ? DOT_R + 1.5 : DOT_R;

                    if (isHov && !isSelKf) {
                        gc.setStroke(Color.web("#8bd2ff", 0.7));
                        gc.setLineWidth(1.5);
                        gc.strokeOval(kx - r - 3, ky - r - 3, (r + 3) * 2, (r + 3) * 2);
                    }

                    gc.setFill(isSelKf ? KF_SEL : (isSelProp ? c : c.deriveColor(0, 1, 1, 0.55)));
                    gc.fillOval(kx - r, ky - r, r * 2, r * 2);
                    gc.setStroke(isSelKf ? Color.web("#fff6df", 0.95) : Color.web("#050505", 0.72));
                    gc.setLineWidth(isSelKf ? 1.4 : 0.8);
                    gc.strokeOval(kx - r, ky - r, r * 2, r * 2);
                }
            }
        }
    }

    private void drawPlayhead(GraphicsContext gc, double h) {
        double x = timeToX(project.getPlayheadMs());
        if (x < LABEL_WIDTH || x > canvas.getWidth()) return;
        gc.setStroke(PLAYHEAD);
        gc.setLineWidth(1.5);
        gc.strokeLine(x, 0, x, h);
        gc.setFill(PLAYHEAD);
        gc.fillPolygon(new double[]{x - 5, x + 5, x}, new double[]{0, 0, 8}, 3);
    }

    private void drawLabelColumn(GraphicsContext gc, double h) {
        gc.setFill(BG_LABEL);
        gc.fillRect(0, 0, LABEL_WIDTH, h);

        // Header label
        gc.setFill(Color.web("#a6a6a6"));
        gc.setFont(Font.font(10));
        gc.fillText("Graph", 10, HEADER_HEIGHT - 10);
        gc.setStroke(Color.web("#333333"));
        gc.strokeLine(0, HEADER_HEIGHT, LABEL_WIDTH, HEADER_HEIGHT);

        EntityTrack track = resolveTrack();
        if (track == null) {
            gc.setFill(DIM_CLR);
            gc.setFont(Font.font(11));
            gc.fillText("Select an entity", 8, HEADER_HEIGHT + 20);
            return;
        }

        // Entity name
        String name = selectedEntity != null ? selectedEntity : "";
        if (name.length() > 16) name = name.substring(0, 15) + "…";
        gc.setFill(TEXT_CLR);
        gc.setFont(Font.font(null, FontWeight.BOLD, 11));
        gc.fillText(name, 8, HEADER_HEIGHT + 16);

        // Property list with colour dots
        List<PropertyType> props = animatedProperties(track);
        double py = HEADER_HEIGHT + 32;
        for (PropertyType prop : props) {
            if (py > h - 6) break;
            boolean active = prop == selectedProperty;
            Color c = trackColorFor(prop);

            gc.setFill(active ? c : c.deriveColor(0, 1, 1, 0.5));
            gc.fillOval(8, py - 6, 7, 7);

            gc.setFill(active ? TEXT_CLR : DIM_CLR);
            gc.setFont(Font.font(null, active ? FontWeight.BOLD : FontWeight.NORMAL, 10));
            gc.fillText(prop.getDisplayName(), 20, py);
            py += 14;
        }
    }

    private void drawHoverReadout(GraphicsContext gc, double w) {
        if (Double.isNaN(hoverX) || hoverX < LABEL_WIDTH) return;

        String text;
        if (hoverKf != null && hoverProp != null) {
            text = hoverProp.getDisplayName()
                + "  t=" + formatTime(hoverKf.getTimeMs())
                + "  v=" + formatValue(hoverKf.getValue());
        } else {
            text = formatTime(xToTime(hoverX)) + "  " + formatValue(yToValue(hoverY));
        }

        gc.setFont(Font.font(10));
        double tw = text.length() * 5.8;
        double rx = Math.min(hoverX + 12, w - tw - 8);
        gc.setFill(Color.web("#1e2a38", 0.9));
        gc.fillRoundRect(rx - 3, HEADER_HEIGHT + 4, tw + 6, 16, 4, 4);
        gc.setFill(TEXT_CLR);
        gc.fillText(text, rx, HEADER_HEIGHT + 14);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private double computeGridStep() {
        double base = 100.0 / pixelsPerMs;
        for (double s : new double[]{50, 100, 200, 500, 1000, 2000, 5000}) {
            if (s >= base) return s;
        }
        return 5000;
    }

    private static double niceStep(double rough) {
        if (rough <= 0) return 1;
        double mag = Math.pow(10, Math.floor(Math.log10(rough)));
        for (double n : new double[]{1, 2, 5, 10}) {
            if (n * mag >= rough) return n * mag;
        }
        return 10 * mag;
    }

    private String formatTime(double ms) {
        if (ms >= 1000) return String.format(Locale.ROOT, "%.1fs", ms / 1000);
        return String.format(Locale.ROOT, "%.0fms", ms);
    }

    private String formatValue(double v) {
        double abs = Math.abs(v);
        if (abs >= 1000) return String.format(Locale.ROOT, "%.0f", v);
        if (abs >= 100)  return String.format(Locale.ROOT, "%.1f", v);
        if (abs >= 10)   return String.format(Locale.ROOT, "%.2f", v);
        return String.format(Locale.ROOT, "%.3f", v);
    }

    // Mirrors TimelinePanel.trackColorFor — kept local to avoid coupling
    private static Color trackColorFor(PropertyType prop) {
        return switch (prop) {
            case X, Y                          -> Color.web("#4da3ff");
            case Z                             -> Color.web("#8bc4ff");
            case PIVOT_X, PIVOT_Y              -> Color.web("#f7d07a");
            case ROTATION                      -> Color.web("#c77dff");
            case SCALE_X, SCALE_Y              -> Color.web("#58d68d");
            case ALPHA                         -> Color.web("#f38ba8");
            case VISIBILITY                    -> Color.web("#f5e663");
            case MATRIX_MXX, MATRIX_MXY,
                 MATRIX_MYX, MATRIX_MYY,
                 MATRIX_TX, MATRIX_TY          -> Color.web("#7ec8e3");
            case BLUR                          -> Color.web("#9aa7ff");
            case CAMERA_X, CAMERA_Y,
                 CAMERA_ZOOM, CAMERA_DOF_FOCUS,
                 CAMERA_DOF_STRENGTH,
                 CAMERA_DOF_MAX_BLUR           -> Color.web("#ff8c42");
        };
    }
}
