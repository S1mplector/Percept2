package com.jvn.editor.ui.actioneditor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class TimelinePanel extends VBox {
    private static final double TRACK_HEIGHT = 24;
    private static final double HEADER_HEIGHT = 30;
    private static final double LABEL_WIDTH = 140;
    private static final Color BG_COLOR = Color.web("#121212");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color PLAYHEAD_COLOR = Color.web("#f38ba8");
    private static final Color KEYFRAME_COLOR = Color.web("#4da3ff");
    private static final Color KEYFRAME_SELECTED_COLOR = Color.web("#f0b673");
    private static final Color TEXT_COLOR = Color.web("#e6e6e6");

    private final AnimationProject project;
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final Pane canvasContainer;

    private double pixelsPerMs = 0.2;
    private double scrollX = 0;
    private double scrollY = 0;

    private String selectedEntity;
    private PropertyType selectedProperty;
    private Keyframe selectedKeyframe;
    private final Set<Keyframe> selectedKeyframes = new HashSet<>();

    private Consumer<Keyframe> onKeyframeSelected;
    private Consumer<Double> onPlayheadChanged;

    private boolean draggingPlayhead = false;
    private boolean draggingKeyframe = false;
    private double dragStartX;

    public TimelinePanel(AnimationProject project) {
        this.project = project;

        canvas = new Canvas(800, 400);
        canvasContainer = new Pane(canvas);
        scrollPane = new ScrollPane(canvasContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #121212; -fx-background-color: #121212;");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
        setStyle("-fx-background-color: #121212;");

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseClicked(this::handleMouseClicked);
        canvas.setOnScroll(this::handleScroll);

        widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(Math.max(800, newVal.doubleValue()));
            render();
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            double h = computeRequiredHeight();
            canvas.setHeight(Math.max(h, newVal.doubleValue()));
            render();
        });

        render();
    }

    public void setOnKeyframeSelected(Consumer<Keyframe> callback) { this.onKeyframeSelected = callback; }
    public void setOnPlayheadChanged(Consumer<Double> callback) { this.onPlayheadChanged = callback; }

    public void setSelectedEntity(String name) {
        this.selectedEntity = name;
        render();
    }

    public String getSelectedEntity() { return selectedEntity; }
    public PropertyType getSelectedProperty() { return selectedProperty; }

    public void setPlayhead(double timeMs) {
        project.setPlayheadMs(timeMs);
        render();
    }

    public void refresh() {
        double h = computeRequiredHeight();
        canvas.setHeight(Math.max(h, getHeight()));
        render();
    }

    public void addKeyframeAtPlayhead() {
        addKeyframeAtTime(project.getPlayheadMs());
    }

    public void addKeyframeAtTime(double time) {
        if (selectedEntity == null || selectedProperty == null) return;
        EntityTrack track = project.getTrack(selectedEntity);
        if (track == null) return;

        time = Math.max(0, time);
        double value = track.getValueAt(selectedProperty, time);
        Keyframe kf = new Keyframe(time, value);
        track.addKeyframe(selectedProperty, kf);
        selectedKeyframe = kf;
        if (onKeyframeSelected != null) onKeyframeSelected.accept(kf);
        render();
    }

    public void deleteSelectedKeyframe() {
        if (selectedEntity == null) return;
        EntityTrack track = project.getTrack(selectedEntity);
        if (track == null) return;

        if (!selectedKeyframes.isEmpty()) {
            for (PropertyType prop : PropertyType.values()) {
                List<Keyframe> kfs = track.getKeyframes(prop);
                for (Keyframe kf : new java.util.ArrayList<>(kfs)) {
                    if (selectedKeyframes.contains(kf)) {
                        track.removeKeyframe(prop, kf);
                    }
                }
            }
            selectedKeyframes.clear();
        } else if (selectedKeyframe != null && selectedProperty != null) {
            track.removeKeyframe(selectedProperty, selectedKeyframe);
        }
        selectedKeyframe = null;
        if (onKeyframeSelected != null) onKeyframeSelected.accept(null);
        render();
    }

    public Set<Keyframe> getSelectedKeyframes() { return selectedKeyframes; }

    private double computeRequiredHeight() {
        int trackCount = 0;
        for (EntityTrack track : project.getTracks()) {
            trackCount++; // entity header
            for (PropertyType p : PropertyType.values()) {
                if (track.hasKeyframes(p)) trackCount++;
            }
        }
        return HEADER_HEIGHT + trackCount * TRACK_HEIGHT + 50;
    }

    private void render() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(BG_COLOR);
        gc.fillRect(0, 0, w, h);

        drawTimeRuler(gc, w);
        drawLoopRegion(gc, w, h);
        drawTracks(gc, w, h);
        drawAudioCues(gc, w, h);
        drawPlayhead(gc, h);
    }

    private void drawTimeRuler(GraphicsContext gc, double width) {
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(0, 0, width, HEADER_HEIGHT);

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);

        double duration = project.getTotalDurationMs();
        double step = computeGridStep();

        gc.setFill(TEXT_COLOR);
        gc.setFont(javafx.scene.text.Font.font(10));

        for (double t = 0; t <= duration; t += step) {
            double x = LABEL_WIDTH + t * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH || x > width) continue;

            gc.strokeLine(x, HEADER_HEIGHT - 5, x, HEADER_HEIGHT);

            String label = formatTime(t);
            gc.fillText(label, x - 15, HEADER_HEIGHT - 10);
        }

        gc.strokeLine(LABEL_WIDTH, HEADER_HEIGHT, width, HEADER_HEIGHT);
    }

    private void drawTracks(GraphicsContext gc, double width, double height) {
        double y = HEADER_HEIGHT - scrollY;

        for (EntityTrack track : project.getTracks()) {
            String entityName = track.getEntityName();
            boolean isSelected = entityName.equals(selectedEntity);

            // Entity header row
            gc.setFill(isSelected ? Color.web("#2a2a2a") : Color.web("#1a1a1a"));
            gc.fillRect(0, y, width, TRACK_HEIGHT);
            gc.setFill(TEXT_COLOR);
            gc.setFont(javafx.scene.text.Font.font(12));
            gc.fillText(entityName, 8, y + 16);
            drawTrackGridLines(gc, y, width);
            y += TRACK_HEIGHT;

            // Property tracks
            for (PropertyType prop : PropertyType.values()) {
                if (!track.hasKeyframes(prop)) continue;

                boolean propSelected = isSelected && prop == selectedProperty;
                gc.setFill(propSelected ? Color.web("#3a3a3a") : Color.web("#151515"));
                gc.fillRect(0, y, width, TRACK_HEIGHT);

                gc.setFill(Color.web("#a0a0a0"));
                gc.setFont(javafx.scene.text.Font.font(10));
                gc.fillText("  └ " + prop.getDisplayName(), 12, y + 15);

                drawTrackGridLines(gc, y, width);
                drawKeyframes(gc, track, prop, y, width);

                y += TRACK_HEIGHT;
            }
        }
    }

    private void drawTrackGridLines(GraphicsContext gc, double y, double width) {
        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);

        double step = computeGridStep();
        double duration = project.getTotalDurationMs();

        for (double t = 0; t <= duration; t += step) {
            double x = LABEL_WIDTH + t * pixelsPerMs - scrollX;
            if (x >= LABEL_WIDTH && x <= width) {
                gc.strokeLine(x, y, x, y + TRACK_HEIGHT);
            }
        }
    }

    private void drawKeyframes(GraphicsContext gc, EntityTrack track, PropertyType prop, double y, double width) {
        List<Keyframe> keyframes = track.getKeyframes(prop);
        double cy = y + TRACK_HEIGHT / 2;

        for (Keyframe kf : keyframes) {
            double x = LABEL_WIDTH + kf.getTimeMs() * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH - 10 || x > width + 10) continue;

            boolean isSelected = kf == selectedKeyframe || selectedKeyframes.contains(kf);
            gc.setFill(isSelected ? KEYFRAME_SELECTED_COLOR : KEYFRAME_COLOR);

            // Diamond shape
            double size = 6;
            gc.fillPolygon(
                new double[]{x, x + size, x, x - size},
                new double[]{cy - size, cy, cy + size, cy},
                4
            );
        }
    }

    private void drawLoopRegion(GraphicsContext gc, double width, double height) {
        if (!project.hasLoopRegion()) return;

        double x1 = LABEL_WIDTH + project.getLoopStartMs() * pixelsPerMs - scrollX;
        double x2 = LABEL_WIDTH + project.getLoopEndMs() * pixelsPerMs - scrollX;

        x1 = Math.max(LABEL_WIDTH, x1);
        x2 = Math.min(width, x2);
        if (x2 <= x1) return;

        gc.setFill(Color.web("#58d68d", 0.08));
        gc.fillRect(x1, HEADER_HEIGHT, x2 - x1, height - HEADER_HEIGHT);

        gc.setStroke(Color.web("#58d68d", 0.5));
        gc.setLineWidth(1.5);
        gc.setLineDashes(6, 4);
        gc.strokeLine(x1, HEADER_HEIGHT, x1, height);
        gc.strokeLine(x2, HEADER_HEIGHT, x2, height);
        gc.setLineDashes((double[]) null);

        gc.setFill(Color.web("#58d68d", 0.7));
        gc.setFont(javafx.scene.text.Font.font(9));
        gc.fillText("LOOP", x1 + 4, HEADER_HEIGHT + 10);
    }

    private void drawAudioCues(GraphicsContext gc, double width, double height) {
        List<AudioCue> cues = project.getAudioCues();
        if (cues.isEmpty()) return;

        double cueY = height - 20;
        gc.setFont(javafx.scene.text.Font.font(9));

        for (AudioCue cue : cues) {
            double x = LABEL_WIDTH + cue.getTimeMs() * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH - 10 || x > width + 10) continue;

            gc.setFill(Color.web("#f0b673", 0.8));
            gc.fillOval(x - 4, cueY - 4, 8, 8);

            gc.setStroke(Color.web("#f0b673", 0.4));
            gc.setLineWidth(1);
            gc.strokeLine(x, HEADER_HEIGHT, x, cueY - 4);

            gc.setFill(Color.web("#f0b673", 0.7));
            String label = cue.getChannel().substring(0, 1).toUpperCase();
            gc.fillText(label, x - 3, cueY + 12);
        }
    }

    private void drawPlayhead(GraphicsContext gc, double height) {
        double x = LABEL_WIDTH + project.getPlayheadMs() * pixelsPerMs - scrollX;
        if (x < LABEL_WIDTH) return;

        gc.setStroke(PLAYHEAD_COLOR);
        gc.setLineWidth(2);
        gc.strokeLine(x, 0, x, height);

        // Playhead handle
        gc.setFill(PLAYHEAD_COLOR);
        double[] xPoints = {x - 8, x + 8, x};
        double[] yPoints = {0, 0, 12};
        gc.fillPolygon(xPoints, yPoints, 3);
    }

    private double computeGridStep() {
        double baseStep = 100 / pixelsPerMs;
        double[] steps = {50, 100, 200, 500, 1000, 2000, 5000};
        for (double s : steps) {
            if (s >= baseStep) return s;
        }
        return 5000;
    }

    private String formatTime(double ms) {
        if (ms >= 1000) {
            return String.format("%.1fs", ms / 1000);
        }
        return String.format("%.0fms", ms);
    }

    private void handleMousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        // Check playhead drag
        if (y < HEADER_HEIGHT + 5) {
            draggingPlayhead = true;
            updatePlayheadFromX(x);
            return;
        }

        // Check keyframe click
        Keyframe kf = findKeyframeAt(x, y);
        if (kf != null) {
            if (e.isShiftDown()) {
                if (selectedKeyframes.contains(kf)) {
                    selectedKeyframes.remove(kf);
                } else {
                    selectedKeyframes.add(kf);
                }
            } else {
                selectedKeyframes.clear();
                selectedKeyframes.add(kf);
            }
            selectedKeyframe = kf;
            draggingKeyframe = true;
            dragStartX = x;
            if (onKeyframeSelected != null) onKeyframeSelected.accept(kf);
            render();
            return;
        }
        selectedKeyframes.clear();

        // Track selection
        selectTrackAt(y);
    }

    private void handleMouseDragged(MouseEvent e) {
        if (draggingPlayhead) {
            updatePlayheadFromX(e.getX());
        } else if (draggingKeyframe && selectedKeyframe != null) {
            double dx = e.getX() - dragStartX;
            double dt = dx / pixelsPerMs;
            selectedKeyframe.setTimeMs(Math.max(0, selectedKeyframe.getTimeMs() + dt));
            dragStartX = e.getX();
            render();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (draggingKeyframe && selectedEntity != null && selectedProperty != null) {
            EntityTrack track = project.getTrack(selectedEntity);
            if (track != null) track.sortKeyframes(selectedProperty);
        }
        draggingPlayhead = false;
        draggingKeyframe = false;
    }

    private void handleMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            double y = e.getY();
            if (y > HEADER_HEIGHT) {
                selectTrackAt(y);
                double time = (e.getX() - LABEL_WIDTH + scrollX) / pixelsPerMs;
                time = Math.max(0, time);
                addKeyframeAtTime(time);
            }
        }
    }

    private void handleScroll(ScrollEvent e) {
        if (e.isControlDown()) {
            double factor = e.getDeltaY() > 0 ? 1.2 : 0.8;
            pixelsPerMs = Math.max(0.05, Math.min(2.0, pixelsPerMs * factor));
            render();
        } else {
            scrollX = Math.max(0, scrollX - e.getDeltaX());
            double maxScrollY = Math.max(0, computeRequiredHeight() - canvas.getHeight());
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - e.getDeltaY()));
            render();
        }
    }

    private void updatePlayheadFromX(double x) {
        double time = (x - LABEL_WIDTH + scrollX) / pixelsPerMs;
        time = Math.max(0, Math.min(project.getTotalDurationMs(), time));
        project.setPlayheadMs(time);
        if (onPlayheadChanged != null) onPlayheadChanged.accept(time);
        render();
    }

    private Keyframe findKeyframeAt(double mx, double my) {
        double y = HEADER_HEIGHT - scrollY;

        for (EntityTrack track : project.getTracks()) {
            y += TRACK_HEIGHT; // entity header

            for (PropertyType prop : PropertyType.values()) {
                if (!track.hasKeyframes(prop)) continue;

                double cy = y + TRACK_HEIGHT / 2;

                for (Keyframe kf : track.getKeyframes(prop)) {
                    double kx = LABEL_WIDTH + kf.getTimeMs() * pixelsPerMs - scrollX;
                    double dist = Math.sqrt(Math.pow(mx - kx, 2) + Math.pow(my - cy, 2));
                    if (dist < 10) {
                        selectedEntity = track.getEntityName();
                        selectedProperty = prop;
                        return kf;
                    }
                }
                y += TRACK_HEIGHT;
            }
        }
        return null;
    }

    private void selectTrackAt(double my) {
        double y = HEADER_HEIGHT - scrollY;

        for (EntityTrack track : project.getTracks()) {
            if (my >= y && my < y + TRACK_HEIGHT) {
                selectedEntity = track.getEntityName();
                selectedProperty = null;
                render();
                return;
            }
            y += TRACK_HEIGHT;

            for (PropertyType prop : PropertyType.values()) {
                if (!track.hasKeyframes(prop)) continue;

                if (my >= y && my < y + TRACK_HEIGHT) {
                    selectedEntity = track.getEntityName();
                    selectedProperty = prop;
                    render();
                    return;
                }
                y += TRACK_HEIGHT;
            }
        }
    }
}
