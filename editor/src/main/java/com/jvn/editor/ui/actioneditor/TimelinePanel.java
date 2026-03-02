package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
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
    private static final Color KEYFRAME_SELECTED_COLOR = Color.web("#f0b673");
    private static final Color TEXT_COLOR = Color.web("#e6e6e6");

    // Track color coding per property type
    private static Color trackColorFor(PropertyType prop) {
        return switch (prop) {
            case X, Y -> Color.web("#4da3ff");
            case PIVOT_X, PIVOT_Y -> Color.web("#f7d07a");
            case ROTATION -> Color.web("#c77dff");
            case SCALE_X, SCALE_Y -> Color.web("#58d68d");
            case ALPHA -> Color.web("#f38ba8");
            case CAMERA_X, CAMERA_Y, CAMERA_ZOOM -> Color.web("#ff8c42");
        };
    }

    private final AnimationProject project;
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final Pane canvasContainer;

    private double pixelsPerMs = 0.2;
    private static final double MIN_PIXELS_PER_MS = 0.01;
    private static final double MAX_PIXELS_PER_MS = 5.0;
    private double scrollX = 0;
    private double scrollY = 0;
    private boolean snapEnabled = true;
    private double snapStepMs = 50;

    private String selectedEntity;
    private boolean selectedGroup = false;
    private PropertyType selectedProperty;
    private Keyframe selectedKeyframe;
    private final Set<Keyframe> selectedKeyframes = new HashSet<>();

    private Consumer<Keyframe> onKeyframeSelected;
    private Consumer<Double> onPlayheadChanged;
    private BiConsumer<String, Boolean> onTargetSelectionChanged;
    private Runnable onEdited;

    private boolean draggingPlayhead = false;
    private boolean draggingKeyframe = false;
    private double dragAnchorX;
    private final Map<Keyframe, Double> dragStartTimes = new HashMap<>();
    private List<ClipboardEntry> copiedKeyframes = List.of();

    private record ClipboardEntry(PropertyType property, Keyframe keyframe, double offsetMs) {}

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
    public void setOnTargetSelectionChanged(BiConsumer<String, Boolean> callback) { this.onTargetSelectionChanged = callback; }
    public void setOnEdited(Runnable callback) { this.onEdited = callback; }

    public void setSelectedEntity(String name) {
        setSelectedTarget(name, false);
    }

    public void setSelectedGroup(String name) {
        setSelectedTarget(name, true);
    }

    public void setSelectedTarget(String name, boolean group) {
        boolean changed = !Objects.equals(this.selectedEntity, name) || this.selectedGroup != group;
        this.selectedEntity = name;
        this.selectedGroup = group;
        if (changed) {
            clearKeyframeSelection();
        }
        if (this.selectedEntity != null && this.selectedProperty == null) {
            this.selectedProperty = PropertyType.X;
        }
        if (this.selectedProperty != null && !isPropertySupportedForSelection(this.selectedProperty)) {
            this.selectedProperty = defaultPropertyForSelection();
        }
        if (changed) {
            notifyTargetSelectionChanged();
        }
        render();
    }

    public String getSelectedEntity() { return selectedEntity; }
    public boolean isSelectedGroup() { return selectedGroup; }
    public PropertyType getSelectedProperty() { return selectedProperty; }
    public void setSelectedProperty(PropertyType property) {
        if (property != null && !isPropertySupportedForSelection(property)) {
            property = defaultPropertyForSelection();
        }
        boolean changed = this.selectedProperty != property;
        this.selectedProperty = property;
        if (changed) {
            clearKeyframeSelection();
        }
        render();
    }

    public boolean isSnapEnabled() { return snapEnabled; }
    public void setSnapEnabled(boolean enabled) { this.snapEnabled = enabled; render(); }
    public double getSnapStepMs() { return snapStepMs; }
    public void setSnapStepMs(double stepMs) {
        this.snapStepMs = Math.max(1.0, stepMs);
        render();
    }

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
        if (selectedEntity == null) return;
        if (selectedProperty == null) selectedProperty = defaultPropertyForSelection();
        if (selectedProperty == null) return;
        if (!isPropertySupportedForSelection(selectedProperty)) return;
        EntityTrack track = selectedTrack(true);
        if (track == null) return;

        time = clampToTimeline(snapTime(Math.max(0, time)));
        double value = track.getValueAt(selectedProperty, time);
        Keyframe kf = track.upsertKeyframe(selectedProperty, new Keyframe(time, value));
        selectedKeyframes.clear();
        if (kf != null) selectedKeyframes.add(kf);
        selectedKeyframe = kf;
        if (onKeyframeSelected != null) onKeyframeSelected.accept(kf);
        notifyEdited();
        render();
    }

    /**
     * Multi-entity batch keyframing: add a keyframe at the given time for every entity track
     * in the project on the currently selected property.
     */
    public void addKeyframeForAllEntities(double time, PropertyType property) {
        if (property == null) return;
        time = clampToTimeline(snapTime(Math.max(0, time)));
        int count = 0;
        for (EntityTrack track : project.getTracks()) {
            double value = track.getValueAt(property, time);
            track.upsertKeyframe(property, new Keyframe(time, value));
            count++;
        }
        if (count > 0) {
            notifyEdited();
            render();
        }
    }

    public void deleteSelectedKeyframe() {
        if (selectedEntity == null) return;
        EntityTrack track = selectedTrack(false);
        if (track == null) return;

        if (!selectedKeyframes.isEmpty()) {
            for (PropertyType prop : editablePropertiesForSelection()) {
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
        clearKeyframeSelection();
        notifyEdited();
        render();
    }

    public Set<Keyframe> getSelectedKeyframes() { return selectedKeyframes; }
    public int getCopiedKeyframeCount() { return copiedKeyframes.size(); }

    public void nudgeSelectedKeyframes(double deltaMs) {
        if (selectedEntity == null) return;
        EntityTrack track = selectedTrack(false);
        if (track == null) return;

        if (!selectedKeyframes.isEmpty()) {
            for (PropertyType prop : editablePropertiesForSelection()) {
                for (Keyframe kf : track.getKeyframes(prop)) {
                    if (!selectedKeyframes.contains(kf)) continue;
                    double next = clampToTimeline(snapTime(kf.getTimeMs() + deltaMs));
                    kf.setTimeMs(next);
                }
                track.sortKeyframes(prop);
            }
        } else if (selectedKeyframe != null && selectedProperty != null) {
            double next = clampToTimeline(snapTime(selectedKeyframe.getTimeMs() + deltaMs));
            selectedKeyframe.setTimeMs(next);
            track.sortKeyframes(selectedProperty);
        }
        notifyEdited();
        render();
    }

    public boolean copySelectedKeyframes() {
        EntityTrack track = selectedTrack(false);
        if (track == null) return false;
        Map<PropertyType, List<Keyframe>> selection = selectedKeyframesByProperty(track);
        if (selection.isEmpty()) return false;

        double anchor = Double.POSITIVE_INFINITY;
        for (List<Keyframe> keyframes : selection.values()) {
            for (Keyframe keyframe : keyframes) {
                anchor = Math.min(anchor, keyframe.getTimeMs());
            }
        }
        if (!Double.isFinite(anchor)) return false;

        List<ClipboardEntry> snapshot = new ArrayList<>();
        for (Map.Entry<PropertyType, List<Keyframe>> entry : selection.entrySet()) {
            PropertyType property = entry.getKey();
            for (Keyframe keyframe : entry.getValue()) {
                snapshot.add(new ClipboardEntry(property, keyframe.copy(), keyframe.getTimeMs() - anchor));
            }
        }
        snapshot.sort(Comparator.comparingDouble(ClipboardEntry::offsetMs)
            .thenComparing(e -> e.property().ordinal()));
        copiedKeyframes = List.copyOf(snapshot);
        return !copiedKeyframes.isEmpty();
    }

    public boolean pasteCopiedKeyframesAtPlayhead() {
        EntityTrack track = selectedTrack(true);
        if (track == null || copiedKeyframes.isEmpty()) return false;

        double playhead = clampToTimeline(snapTime(project.getPlayheadMs()));
        Set<Keyframe> previousSelection = new HashSet<>(selectedKeyframes);
        Keyframe previousPrimary = selectedKeyframe;
        PropertyType previousProperty = selectedProperty;

        selectedKeyframes.clear();
        selectedKeyframe = null;

        PropertyType firstProperty = null;
        int pasted = 0;
        for (ClipboardEntry entry : copiedKeyframes) {
            PropertyType property = entry.property();
            if (!isPropertySupportedForSelection(property)) continue;
            Keyframe copy = entry.keyframe().copy();
            copy.setTimeMs(clampToTimeline(snapTime(playhead + entry.offsetMs())));
            Keyframe inserted = track.upsertKeyframe(property, copy);
            if (inserted == null) continue;
            if (firstProperty == null) firstProperty = property;
            selectedKeyframes.add(inserted);
            selectedKeyframe = inserted;
            pasted++;
        }
        if (pasted == 0) {
            selectedKeyframes.clear();
            selectedKeyframes.addAll(previousSelection);
            selectedKeyframe = previousPrimary;
            selectedProperty = previousProperty;
            if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);
            render();
            return false;
        }

        if (firstProperty != null) selectedProperty = firstProperty;
        if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);
        notifyEdited();
        render();
        return true;
    }

    public boolean duplicateSelectedKeyframes(double deltaMs) {
        if (Math.abs(deltaMs) < 1e-9) return false;
        EntityTrack track = selectedTrack(false);
        if (track == null) return false;
        Map<PropertyType, List<Keyframe>> selection = selectedKeyframesByProperty(track);
        if (selection.isEmpty()) return false;

        selectedKeyframes.clear();
        selectedKeyframe = null;

        PropertyType firstProperty = null;
        int duplicated = 0;
        for (Map.Entry<PropertyType, List<Keyframe>> entry : selection.entrySet()) {
            PropertyType property = entry.getKey();
            for (Keyframe source : entry.getValue()) {
                Keyframe copy = source.copy();
                copy.setTimeMs(clampToTimeline(snapTime(source.getTimeMs() + deltaMs)));
                Keyframe inserted = track.upsertKeyframe(property, copy);
                if (inserted == null) continue;
                if (firstProperty == null) firstProperty = property;
                selectedKeyframes.add(inserted);
                selectedKeyframe = inserted;
                duplicated++;
            }
        }
        if (duplicated == 0) {
            clearKeyframeSelection();
            render();
            return false;
        }

        if (firstProperty != null) selectedProperty = firstProperty;
        if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);
        notifyEdited();
        render();
        return true;
    }

    private double computeRequiredHeight() {
        int trackCount = 0;
        EntityTrack groupTrack = selectedGroupTrack();
        if (selectedGroup && groupTrack != null) {
            trackCount++; // selected group header
            for (PropertyType p : editablePropertiesForSelection()) {
                if (groupTrack.hasKeyframes(p) || p == selectedProperty) {
                    trackCount++;
                }
            }
        }
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
        drawTracks(gc, w);
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

    private void drawTracks(GraphicsContext gc, double width) {
        double y = HEADER_HEIGHT - scrollY;
        EntityTrack groupTrack = selectedGroupTrack();

        if (selectedGroup && groupTrack != null && selectedEntity != null) {
            y = drawTrackBlock(gc, width, y, groupTrack, "[Group] " + selectedEntity, true, editablePropertiesForSelection());
        }

        for (EntityTrack track : project.getTracks()) {
            String entityName = track.getEntityName();
            boolean isSelected = !selectedGroup && entityName.equals(selectedEntity);
            y = drawTrackBlock(gc, width, y, track, entityName, isSelected, PropertyType.values());
        }
    }

    private double drawTrackBlock(GraphicsContext gc,
                                  double width,
                                  double y,
                                  EntityTrack track,
                                  String label,
                                  boolean isSelected,
                                  PropertyType[] properties) {
        if (track == null) return y;
        gc.setFill(isSelected ? Color.web("#2a2a2a") : Color.web("#1a1a1a"));
        gc.fillRect(0, y, width, TRACK_HEIGHT);
        gc.setFill(TEXT_COLOR);
        gc.setFont(javafx.scene.text.Font.font(12));
        gc.fillText(label, 8, y + 16);
        drawTrackGridLines(gc, y, width);
        y += TRACK_HEIGHT;

        for (PropertyType prop : properties) {
            boolean showTrack = track.hasKeyframes(prop) || (isSelected && prop == selectedProperty);
            if (!showTrack) continue;

            boolean propSelected = isSelected && prop == selectedProperty;
            gc.setFill(propSelected ? Color.web("#3a3a3a") : Color.web("#151515"));
            gc.fillRect(0, y, width, TRACK_HEIGHT);

            Color propColor = trackColorFor(prop);
            // Color coded left accent bar
            gc.setFill(propColor.deriveColor(0, 1, 1, 0.5));
            gc.fillRect(0, y, 3, TRACK_HEIGHT);

            gc.setFill(propColor.deriveColor(0, 0.6, 1, 1));
            gc.setFont(javafx.scene.text.Font.font(10));
            gc.fillText("  └ " + prop.getDisplayName(), 12, y + 15);

            drawTrackGridLines(gc, y, width);
            drawKeyframes(gc, track, prop, y, width);
            y += TRACK_HEIGHT;
        }
        return y;
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
            gc.setFill(isSelected ? KEYFRAME_SELECTED_COLOR : trackColorFor(prop));

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

            // Pseudo-waveform visualization: draw a small decorative waveform block after each cue
            drawAudioWaveform(gc, x, cueY, cue);
        }
    }

    private void drawAudioWaveform(GraphicsContext gc, double startX, double baseY, AudioCue cue) {
        // Draw a decorative waveform representation (visual indicator, not actual audio decode)
        double waveWidth = 60 * pixelsPerMs; // ~60ms visual width
        gc.setStroke(Color.web("#f0b673", 0.25));
        gc.setLineWidth(1);
        int bars = (int) Math.max(4, Math.min(20, waveWidth / 3));
        double barW = waveWidth / bars;
        for (int i = 0; i < bars; i++) {
            double bx = startX + 6 + i * barW;
            double amp = 2 + 6 * Math.abs(Math.sin(i * 0.8 + cue.getTimeMs() * 0.01));
            amp *= cue.getVolume();
            gc.strokeLine(bx, baseY - amp, bx, baseY + amp);
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
                    if (selectedKeyframe == kf) {
                        selectedKeyframe = selectedKeyframes.stream().findFirst().orElse(null);
                    }
                } else {
                    selectedKeyframes.add(kf);
                    selectedKeyframe = kf;
                }
            } else {
                selectedKeyframes.clear();
                selectedKeyframes.add(kf);
                selectedKeyframe = kf;
            }
            draggingKeyframe = selectedKeyframe != null;
            dragAnchorX = x;
            captureDragStartTimes();
            notifyTargetSelectionChanged();
            if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);
            render();
            return;
        }
        clearKeyframeSelection();

        // Track selection
        selectTrackAt(y);
    }

    private void handleMouseDragged(MouseEvent e) {
        if (draggingPlayhead) {
            updatePlayheadFromX(e.getX());
        } else if (draggingKeyframe && selectedKeyframe != null) {
            double dt = (e.getX() - dragAnchorX) / pixelsPerMs;
            if (!dragStartTimes.isEmpty()) {
                for (Map.Entry<Keyframe, Double> entry : dragStartTimes.entrySet()) {
                    Keyframe moving = entry.getKey();
                    double next = clampToTimeline(snapTime(entry.getValue() + dt));
                    moving.setTimeMs(next);
                }
            } else {
                double next = clampToTimeline(snapTime(selectedKeyframe.getTimeMs() + dt));
                selectedKeyframe.setTimeMs(next);
            }
            render();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        e.consume();
        if (draggingKeyframe && selectedEntity != null) {
            EntityTrack track = selectedTrack(false);
            if (track != null) {
                Set<PropertyType> affectedProperties = collectSelectedProperties(track);
                if (affectedProperties.isEmpty() && selectedProperty != null) {
                    affectedProperties.add(selectedProperty);
                }
                for (PropertyType prop : affectedProperties) {
                    track.sortKeyframes(prop);
                }
            }
            notifyEdited();
        }
        draggingPlayhead = false;
        draggingKeyframe = false;
        dragStartTimes.clear();
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
            // Zoom centered on mouse X position
            double mouseX = e.getX();
            double timeBefore = (mouseX - LABEL_WIDTH + scrollX) / pixelsPerMs;
            double factor = e.getDeltaY() > 0 ? 1.2 : 0.8;
            pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, pixelsPerMs * factor));
            // Adjust scroll to keep the time under the mouse stable
            scrollX = Math.max(0, timeBefore * pixelsPerMs - (mouseX - LABEL_WIDTH));
            render();
        } else {
            scrollX = Math.max(0, scrollX - e.getDeltaX());
            double maxScrollY = Math.max(0, computeRequiredHeight() - canvas.getHeight());
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - e.getDeltaY()));
            render();
        }
    }

    public double getPixelsPerMs() { return pixelsPerMs; }
    public void setPixelsPerMs(double ppm) {
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, ppm));
        render();
    }

    /** Zoom to fit the entire timeline duration in the visible area. */
    public void zoomToFit() {
        double visible = Math.max(100, canvas.getWidth() - LABEL_WIDTH - 20);
        double duration = Math.max(100, project.getTotalDurationMs());
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, visible / duration));
        scrollX = 0;
        render();
    }

    private void updatePlayheadFromX(double x) {
        double time = clampToTimeline(snapTime((x - LABEL_WIDTH + scrollX) / pixelsPerMs));
        project.setPlayheadMs(time);
        if (onPlayheadChanged != null) onPlayheadChanged.accept(time);
        render();
    }

    private Keyframe findKeyframeAt(double mx, double my) {
        double y = HEADER_HEIGHT - scrollY;
        EntityTrack groupTrack = selectedGroupTrack();

        if (selectedGroup && selectedEntity != null && groupTrack != null) {
            y += TRACK_HEIGHT;
            for (PropertyType prop : editablePropertiesForSelection()) {
                boolean showTrack = groupTrack.hasKeyframes(prop) || prop == selectedProperty;
                if (!showTrack) continue;

                double cy = y + TRACK_HEIGHT / 2;
                for (Keyframe kf : groupTrack.getKeyframes(prop)) {
                    double kx = LABEL_WIDTH + kf.getTimeMs() * pixelsPerMs - scrollX;
                    double dist = Math.sqrt(Math.pow(mx - kx, 2) + Math.pow(my - cy, 2));
                    if (dist < 10) {
                        selectedProperty = prop;
                        return kf;
                    }
                }
                y += TRACK_HEIGHT;
            }
        }

        for (EntityTrack track : project.getTracks()) {
            y += TRACK_HEIGHT; // entity header

            for (PropertyType prop : PropertyType.values()) {
                boolean showTrack = track.hasKeyframes(prop)
                    || (!selectedGroup && track.getEntityName().equals(selectedEntity) && prop == selectedProperty);
                if (!showTrack) continue;

                double cy = y + TRACK_HEIGHT / 2;

                for (Keyframe kf : track.getKeyframes(prop)) {
                    double kx = LABEL_WIDTH + kf.getTimeMs() * pixelsPerMs - scrollX;
                    double dist = Math.sqrt(Math.pow(mx - kx, 2) + Math.pow(my - cy, 2));
                    if (dist < 10) {
                        selectedEntity = track.getEntityName();
                        selectedGroup = false;
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
        EntityTrack groupTrack = selectedGroupTrack();

        if (selectedGroup && selectedEntity != null && groupTrack != null) {
            if (my >= y && my < y + TRACK_HEIGHT) {
                selectedProperty = null;
                notifyTargetSelectionChanged();
                render();
                return;
            }
            y += TRACK_HEIGHT;

            for (PropertyType prop : editablePropertiesForSelection()) {
                boolean showTrack = groupTrack.hasKeyframes(prop) || prop == selectedProperty;
                if (!showTrack) continue;
                if (my >= y && my < y + TRACK_HEIGHT) {
                    selectedProperty = prop;
                    notifyTargetSelectionChanged();
                    render();
                    return;
                }
                y += TRACK_HEIGHT;
            }
        }

        for (EntityTrack track : project.getTracks()) {
            if (my >= y && my < y + TRACK_HEIGHT) {
                selectedEntity = track.getEntityName();
                selectedGroup = false;
                selectedProperty = null;
                notifyTargetSelectionChanged();
                render();
                return;
            }
            y += TRACK_HEIGHT;

            for (PropertyType prop : PropertyType.values()) {
                boolean showTrack = track.hasKeyframes(prop)
                    || (!selectedGroup && track.getEntityName().equals(selectedEntity) && prop == selectedProperty);
                if (!showTrack) continue;

                if (my >= y && my < y + TRACK_HEIGHT) {
                    selectedEntity = track.getEntityName();
                    selectedGroup = false;
                    selectedProperty = prop;
                    notifyTargetSelectionChanged();
                    render();
                    return;
                }
                y += TRACK_HEIGHT;
            }
        }
    }

    private void captureDragStartTimes() {
        dragStartTimes.clear();
        if (!selectedKeyframes.isEmpty()) {
            for (Keyframe keyframe : selectedKeyframes) {
                if (keyframe != null) {
                    dragStartTimes.put(keyframe, keyframe.getTimeMs());
                }
            }
        } else if (selectedKeyframe != null) {
            dragStartTimes.put(selectedKeyframe, selectedKeyframe.getTimeMs());
        }
    }

    private Set<PropertyType> collectSelectedProperties(EntityTrack track) {
        Set<PropertyType> props = new HashSet<>();
        if (track == null || selectedKeyframes.isEmpty()) return props;
        for (PropertyType prop : editablePropertiesForSelection()) {
            for (Keyframe kf : track.getKeyframes(prop)) {
                if (selectedKeyframes.contains(kf)) {
                    props.add(prop);
                    break;
                }
            }
        }
        return props;
    }

    private Map<PropertyType, List<Keyframe>> selectedKeyframesByProperty(EntityTrack track) {
        Map<PropertyType, List<Keyframe>> byProperty = new HashMap<>();
        if (track == null) return byProperty;

        if (!selectedKeyframes.isEmpty()) {
            for (PropertyType property : editablePropertiesForSelection()) {
                List<Keyframe> matches = new ArrayList<>();
                for (Keyframe keyframe : track.getKeyframes(property)) {
                    if (selectedKeyframes.contains(keyframe)) matches.add(keyframe);
                }
                if (!matches.isEmpty()) {
                    matches.sort(Comparator.comparingDouble(Keyframe::getTimeMs));
                    byProperty.put(property, matches);
                }
            }
            return byProperty;
        }

        if (selectedKeyframe == null || selectedProperty == null) return byProperty;
        if (!isPropertySupportedForSelection(selectedProperty)) return byProperty;
        List<Keyframe> list = track.getKeyframes(selectedProperty);
        if (list.contains(selectedKeyframe)) {
            byProperty.put(selectedProperty, List.of(selectedKeyframe));
        }
        return byProperty;
    }

    private void clearKeyframeSelection() {
        selectedKeyframes.clear();
        selectedKeyframe = null;
        dragStartTimes.clear();
        if (onKeyframeSelected != null) onKeyframeSelected.accept(null);
    }

    private EntityTrack selectedTrack(boolean createForEntity) {
        if (selectedEntity == null || selectedEntity.isBlank()) return null;
        if (selectedGroup) {
            EntityGroup group = project.getGroup(selectedEntity);
            return group != null ? group.getGroupTrack() : null;
        }
        return createForEntity ? project.getOrCreateTrack(selectedEntity) : project.getTrack(selectedEntity);
    }

    private EntityTrack selectedGroupTrack() {
        if (!selectedGroup || selectedEntity == null || selectedEntity.isBlank()) return null;
        EntityGroup group = project.getGroup(selectedEntity);
        return group != null ? group.getGroupTrack() : null;
    }

    private static boolean isGroupProperty(PropertyType property) {
        return property == PropertyType.X || property == PropertyType.Y;
    }

    private PropertyType defaultPropertyForSelection() {
        return PropertyType.X;
    }

    private boolean isPropertySupportedForSelection(PropertyType property) {
        if (property == null) return true;
        return !selectedGroup || isGroupProperty(property);
    }

    private PropertyType[] editablePropertiesForSelection() {
        if (!selectedGroup) return PropertyType.values();
        return new PropertyType[]{PropertyType.X, PropertyType.Y};
    }

    private double snapTime(double timeMs) {
        if (!snapEnabled || snapStepMs <= 0) return timeMs;
        return Math.round(timeMs / snapStepMs) * snapStepMs;
    }

    private double clampToTimeline(double timeMs) {
        return Math.max(0.0, Math.min(project.getTotalDurationMs(), timeMs));
    }

    private void notifyEdited() {
        if (onEdited != null) onEdited.run();
    }

    private void notifyTargetSelectionChanged() {
        if (onTargetSelectionChanged != null) {
            onTargetSelectionChanged.accept(selectedEntity, selectedGroup);
        }
    }
}
