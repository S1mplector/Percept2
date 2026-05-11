package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

public class TimelinePanel extends VBox {
    private static final double TRACK_HEIGHT = 24;
    private static final double HEADER_HEIGHT = 30;
    private static final double LABEL_WIDTH = 140;
    static final String RUNTIME_CAMERA_TARGET = "__camera__";
    private static final String RUNTIME_CAMERA_LABEL = "Runtime Camera / Frame";
    private static final PropertyType[] GROUP_PROPERTIES = {
        PropertyType.X,
        PropertyType.Y,
        PropertyType.Z,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.ALPHA,
        PropertyType.VISIBILITY,
        PropertyType.MATRIX_MXX,
        PropertyType.MATRIX_MXY,
        PropertyType.MATRIX_MYX,
        PropertyType.MATRIX_MYY,
        PropertyType.MATRIX_TX,
        PropertyType.MATRIX_TY,
        PropertyType.BLUR
    };
    private static final PropertyType[] CAMERA_PROPERTIES = {
        PropertyType.CAMERA_X,
        PropertyType.CAMERA_Y,
        PropertyType.CAMERA_ZOOM,
        PropertyType.CAMERA_DOF_FOCUS,
        PropertyType.CAMERA_DOF_STRENGTH,
        PropertyType.CAMERA_DOF_MAX_BLUR
    };
    private static final Color BG_COLOR = Color.web("#121212");
    private static final Color GRID_COLOR = Color.web("#2a2a2a");
    private static final Color PLAYHEAD_COLOR = Color.web("#f38ba8");
    private static final Color KEYFRAME_SELECTED_COLOR = Color.web("#f0b673");
    private static final Color TEXT_COLOR = Color.web("#e6e6e6");

    // Track color coding per property type
    private static Color trackColorFor(PropertyType prop) {
        return switch (prop) {
            case X, Y -> Color.web("#4da3ff");
            case Z -> Color.web("#8bc4ff");
            case PIVOT_X, PIVOT_Y -> Color.web("#f7d07a");
            case ROTATION -> Color.web("#c77dff");
            case SCALE_X, SCALE_Y -> Color.web("#58d68d");
            case ALPHA -> Color.web("#f38ba8");
            case VISIBILITY -> Color.web("#f5e663");
            case MATRIX_MXX, MATRIX_MXY, MATRIX_MYX, MATRIX_MYY, MATRIX_TX, MATRIX_TY -> Color.web("#7ec8e3");
            case BLUR -> Color.web("#9aa7ff");
            case CAMERA_X, CAMERA_Y, CAMERA_ZOOM,
                CAMERA_DOF_FOCUS, CAMERA_DOF_STRENGTH, CAMERA_DOF_MAX_BLUR -> Color.web("#ff8c42");
        };
    }

    private final AnimationProject project;
    private final PuppeteerCommand.Stack commandStack;
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final Pane canvasContainer;

    // Zoom controls
    private final HBox zoomHeader;
    private final Slider zoomSlider;
    private final Button btnZoomOut;
    private final Button btnZoomIn;
    private final Button btnZoomFit;
    private final Label lblZoomLevel;

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
    private final KeyframeSelectionModel selectionModel = new KeyframeSelectionModel();

    private Consumer<Keyframe> onKeyframeSelected;
    private Consumer<Double> onPlayheadChanged;
    private BiConsumer<String, Boolean> onTargetSelectionChanged;
    private Runnable onEdited;

    private boolean draggingPlayhead = false;
    private boolean draggingKeyframe = false;
    private boolean marqueeSelecting = false;
    private double dragAnchorX;
    private double marqueeStartX;
    private double marqueeStartY;
    private double marqueeEndX;
    private double marqueeEndY;
    private double hoverX = Double.NaN;
    private double hoverY = Double.NaN;
    private double hoverTimeMs = Double.NaN;
    private String hoverReadout = "";
    private String hoverRowStorageName;
    private PropertyType hoverRowProperty;
    private boolean hoverRowGroup;
    private Keyframe hoverKeyframe;
    private final Map<KeyframeSelectionModel.KeyframeRef, Double> dragStartTimes = new HashMap<>();
    private List<ClipboardEntry> copiedKeyframes = List.of();

    // Context menu / interaction state
    private ContextMenu activeContextMenu;
    private static EasingSpec easingClipboard;
    private static Easing.Interpolation interpolationClipboard;
    private EasingCurveDialog easingCurveDialog;

    // Middle-mouse panning
    private boolean middlePanning = false;
    private double middlePanStartX;
    private double middlePanStartY;
    private double middlePanStartScrollX;
    private double middlePanStartScrollY;

    // Auto-scroll during keyframe drag
    private AnimationTimer dragAutoScrollTimer;
    private double lastDragMouseX;
    private static final double AUTO_SCROLL_EDGE_PX = 40.0;
    private static final double AUTO_SCROLL_MAX_SPEED_PX = 18.0;

    public record ClipboardEntry(String sourceName,
                                  boolean group,
                                  PropertyType property,
                                  Keyframe keyframe,
                                  double offsetMs) {}
    private record PasteTarget(EntityTrack track, String selectionName, boolean group) {}
    private record TrackRow(EntityTrack track,
                            String selectionName,
                            String displayLabel,
                            boolean group,
                            boolean runtimeCamera,
                            PropertyType property,
                            double y,
                            double height) {}
    private record KeyframeHit(TrackRow row, Keyframe keyframe) {}

    public TimelinePanel(AnimationProject project) {
        this(project, null);
    }

    public TimelinePanel(AnimationProject project, PuppeteerCommand.Stack commandStack) {
        this.project = project;
        this.commandStack = commandStack;

        // Initialize zoom controls
        btnZoomOut = new Button("-");
        btnZoomOut.setPrefWidth(30);
        btnZoomOut.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #444; -fx-border-radius: 3;");
        btnZoomOut.setOnAction(e -> zoomStep(0.8));

        btnZoomIn = new Button("+");
        btnZoomIn.setPrefWidth(30);
        btnZoomIn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #444; -fx-border-radius: 3;");
        btnZoomIn.setOnAction(e -> zoomStep(1.2));

        btnZoomFit = new Button("Fit");
        btnZoomFit.setPrefWidth(50);
        btnZoomFit.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-border-color: #444; -fx-border-radius: 3;");
        btnZoomFit.setOnAction(e -> zoomToFit());

        lblZoomLevel = new Label(formatZoomLevel());
        lblZoomLevel.setStyle("-fx-text-fill: #a6a6a6; -fx-font-size: 11px; -fx-min-width: 60;");

        zoomSlider = new Slider(MIN_PIXELS_PER_MS, MAX_PIXELS_PER_MS, pixelsPerMs);
        zoomSlider.setPrefWidth(150);
        zoomSlider.setStyle("-fx-control-inner-background: #2a2a2a;");
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            pixelsPerMs = newVal.doubleValue();
            clampScrollOffsets();
            render();
            lblZoomLevel.setText(formatZoomLevel());
        });

        zoomHeader = new HBox(6, btnZoomOut, zoomSlider, btnZoomIn, btnZoomFit, lblZoomLevel);
        zoomHeader.setAlignment(Pos.CENTER_LEFT);
        zoomHeader.setPadding(new Insets(6, 8, 6, 8));
        zoomHeader.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #333; -fx-border-width: 0 0 1 0;");

        canvas = new Canvas(800, 400);
        canvasContainer = new Pane(canvas);
        scrollPane = new ScrollPane(canvasContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: #121212; -fx-background-color: #121212;");

        getChildren().addAll(zoomHeader, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        setStyle("-fx-background-color: #121212;");

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseClicked(this::handleMouseClicked);
        canvas.setOnMouseMoved(this::handleMouseMoved);
        canvas.setOnMouseExited(this::handleMouseExited);
        canvas.setOnContextMenuRequested(this::handleContextMenuRequested);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, this::handleScroll);

        widthProperty().addListener((obs, oldVal, newVal) -> {
            updateCanvasViewportSize();
            render();
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            updateCanvasViewportSize();
            render();
        });

        updateCanvasViewportSize();
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
    public boolean isRuntimeCameraSelected() { return !selectedGroup && isRuntimeCameraTarget(selectedEntity); }
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
        updateCanvasViewportSize();
        clampScrollOffsets();
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

        time = clampOrExpandTimeline(snapTime(Math.max(0, time)));
        double value = track.getValueAt(selectedProperty, time);
        Keyframe kf;
        if (commandStack != null) {
            PuppeteerCommand cmd = PuppeteerCommand.upsertKeyframe(track, selectedProperty, time, value);
            commandStack.execute(cmd);
            kf = track.findKeyframeAt(selectedProperty, time);
        } else {
            kf = track.upsertKeyframe(selectedProperty, new Keyframe(time, value));
        }
        selectionModel.clearSelection();
        if (kf != null) selectionModel.select(KeyframeSelectionModel.ref(selectedEntity, selectedProperty, kf));
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
        time = clampOrExpandTimeline(snapTime(Math.max(0, time)));
        List<PuppeteerCommand> cmds = new ArrayList<>();
        for (EntityTrack track : project.getTracks()) {
            double value = track.getValueAt(property, time);
            cmds.add(PuppeteerCommand.upsertKeyframe(track, property, time, value));
        }
        if (cmds.isEmpty()) return;
        if (commandStack != null) {
            commandStack.execute(PuppeteerCommand.composite("Add keyframe for all entities", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        notifyEdited();
        render();
    }

    public void deleteSelectedKeyframe() {
        List<PuppeteerCommand> cmds = new ArrayList<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : new ArrayList<>(selectionModel.getSelected())) {
                EntityTrack track = resolveTrackForRef(ref);
                if (track == null || ref.property() == null || ref.keyframe() == null) continue;
                cmds.add(PuppeteerCommand.removeKeyframe(track, ref.property(), ref.keyframe()));
            }
        } else if (selectedKeyframe != null && selectedProperty != null) {
            if (selectedEntity == null) return;
            EntityTrack track = selectedTrack(false);
            if (track == null) return;
            cmds.add(PuppeteerCommand.removeKeyframe(track, selectedProperty, selectedKeyframe));
        } else {
            return;
        }
        if (commandStack != null && !cmds.isEmpty()) {
            commandStack.execute(PuppeteerCommand.composite("Delete keyframes", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        clearKeyframeSelection();
        notifyEdited();
        render();
    }

    public Set<Keyframe> getSelectedKeyframes() {
        return selectionModel.getSelected().stream()
            .map(KeyframeSelectionModel.KeyframeRef::keyframe)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public KeyframeSelectionModel getSelectionModel() { return selectionModel; }
    public int getSelectionCount() { return selectionModel.getSelectionCount(); }
    public int getCopiedKeyframeCount() { return copiedKeyframes.size(); }

    public void nudgeSelectedKeyframes(double deltaMs) {
        List<PuppeteerCommand> cmds = new ArrayList<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                if (ref.keyframe() == null) continue;
                double oldTime = ref.keyframe().getTimeMs();
                double newTime = clampOrExpandTimeline(snapTime(oldTime + deltaMs));
                cmds.add(PuppeteerCommand.moveKeyframe(ref.keyframe(), oldTime, newTime));
            }
        } else if (selectedEntity != null && selectedKeyframe != null && selectedProperty != null) {
            EntityTrack track = selectedTrack(false);
            if (track == null) return;
            double oldTime = selectedKeyframe.getTimeMs();
            double newTime = clampOrExpandTimeline(snapTime(oldTime + deltaMs));
            cmds.add(PuppeteerCommand.moveKeyframe(selectedKeyframe, oldTime, newTime));
        } else {
            return;
        }
        if (commandStack != null && !cmds.isEmpty()) {
            commandStack.execute(PuppeteerCommand.composite("Nudge keyframes", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        // Re-sort affected tracks
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                EntityTrack track = ref.entityName() != null ? project.getTrack(ref.entityName()) : null;
                if (track != null && ref.property() != null) track.sortKeyframes(ref.property());
            }
        } else if (selectedProperty != null) {
            EntityTrack track = selectedTrack(false);
            if (track != null) track.sortKeyframes(selectedProperty);
        }
        notifyEdited();
        render();
    }

    public boolean distributeSelectedKeyframes() {
        if (selectionModel.getSelectionCount() < 3) return false;
        selectionModel.distributeSelected(project, snapEnabled ? snapStepMs : 0.0);
        notifyEdited();
        render();
        return true;
    }

    public boolean reverseSelectedKeyframes() {
        if (selectionModel.getSelectionCount() < 2) return false;
        selectionModel.reverseSelected(project, snapEnabled ? snapStepMs : 0.0);
        notifyEdited();
        render();
        return true;
    }

    public boolean stretchSelectedKeyframes(double factor) {
        if (selectionModel.getSelectionCount() < 2) return false;
        selectionModel.stretchSelected(project, factor, snapEnabled ? snapStepMs : 0.0);
        notifyEdited();
        render();
        return true;
    }

    public boolean isRippleRetimeEnabled() {
        return selectionModel.isRippleRetimeEnabled();
    }

    public void setRippleRetimeEnabled(boolean enabled) {
        selectionModel.setRippleRetimeEnabled(enabled);
    }

    public boolean copySelectedKeyframes() {
        List<ClipboardEntry> entries = collectClipboardEntries();
        if (entries.isEmpty()) return false;
        double anchor = Double.POSITIVE_INFINITY;
        for (ClipboardEntry entry : entries) {
            anchor = Math.min(anchor, entry.keyframe().getTimeMs());
        }
        if (!Double.isFinite(anchor)) return false;

        List<ClipboardEntry> snapshot = new ArrayList<>();
        for (ClipboardEntry entry : entries) {
            snapshot.add(new ClipboardEntry(
                entry.sourceName(),
                entry.group(),
                entry.property(),
                entry.keyframe().copy(),
                entry.keyframe().getTimeMs() - anchor));
        }
        snapshot.sort(Comparator.comparingDouble((ClipboardEntry e) -> e.offsetMs())
            .thenComparing((ClipboardEntry e) -> e.sourceName(), Comparator.nullsLast(String::compareTo))
            .thenComparingInt(e -> e.property().ordinal()));
        copiedKeyframes = List.copyOf(snapshot);
        return !copiedKeyframes.isEmpty();
    }

    public List<ClipboardEntry> getCopiedKeyframes() {
        return List.copyOf(copiedKeyframes);
    }

    public void setCopiedKeyframes(List<ClipboardEntry> entries) {
        if (entries == null) {
            copiedKeyframes = List.of();
        } else {
            copiedKeyframes = List.copyOf(entries);
        }
    }

    public boolean pasteCopiedKeyframesAtPlayhead() {
        if (copiedKeyframes.isEmpty()) return false;
        EntityTrack selectedTargetTrack = selectedTrack(true);
        boolean retargetSingleSource = selectedTargetTrack != null && clipboardHasSingleSourceTarget();

        double playhead = clampToTimeline(snapTime(project.getPlayheadMs()));
        Keyframe previousPrimary = selectedKeyframe;
        PropertyType previousProperty = selectedProperty;

        Set<KeyframeSelectionModel.KeyframeRef> previousRefs = new LinkedHashSet<>(selectionModel.getSelected());
        selectionModel.clearSelection();
        selectedKeyframe = null;

        PropertyType firstProperty = null;
        int pasted = 0;
        for (ClipboardEntry entry : copiedKeyframes) {
            PropertyType property = entry.property();
            PasteTarget target = resolvePasteTarget(entry, selectedTargetTrack, retargetSingleSource);
            if (target == null || !isPropertySupportedForTarget(property, target.selectionName(), target.group())) continue;
            Keyframe copy = entry.keyframe().copy();
            copy.setTimeMs(clampOrExpandTimeline(snapTime(playhead + entry.offsetMs())));
            Keyframe inserted = target.track().upsertKeyframe(property, copy);
            if (inserted == null) continue;
            if (firstProperty == null) firstProperty = property;
            selectionModel.select(KeyframeSelectionModel.ref(target.track().getEntityName(), property, inserted));
            selectedKeyframe = inserted;
            selectedEntity = target.selectionName();
            selectedGroup = target.group();
            pasted++;
        }
        if (pasted == 0) {
            selectionModel.replaceSelection(previousRefs);
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
        List<ClipboardEntry> entries = collectClipboardEntries();
        if (entries.isEmpty()) return false;

        selectionModel.clearSelection();
        selectedKeyframe = null;

        PropertyType firstProperty = null;
        int duplicated = 0;
        for (ClipboardEntry entry : entries) {
            PropertyType property = entry.property();
            PasteTarget target = resolvePasteTarget(entry, null, false);
            if (target == null || !isPropertySupportedForTarget(property, target.selectionName(), target.group())) continue;
            Keyframe copy = entry.keyframe().copy();
            copy.setTimeMs(clampOrExpandTimeline(snapTime(entry.keyframe().getTimeMs() + deltaMs)));
            Keyframe inserted = target.track().upsertKeyframe(property, copy);
            if (inserted == null) continue;
            if (firstProperty == null) firstProperty = property;
            selectionModel.select(KeyframeSelectionModel.ref(target.track().getEntityName(), property, inserted));
            selectedKeyframe = inserted;
            selectedEntity = target.selectionName();
            selectedGroup = target.group();
            duplicated++;
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
        if (shouldShowRuntimeCameraBlock()) {
            trackCount++; // runtime camera header
            for (PropertyType p : CAMERA_PROPERTIES) {
                if (shouldShowPropertyTrack(resolveRuntimeCameraTrack(false), isRuntimeCameraSelected(), false, true, p)) {
                    trackCount++;
                }
            }
        }
        EntityTrack groupTrack = selectedGroupTrack();
        if (selectedGroup && groupTrack != null) {
            trackCount++; // selected group header
            for (PropertyType p : GROUP_PROPERTIES) {
                if (shouldShowPropertyTrack(groupTrack, true, true, false, p)) {
                    trackCount++;
                }
            }
        }
        for (EntityTrack track : project.getTracks()) {
            if (track != null && isRuntimeCameraCarrier(track)) continue;
            trackCount++; // entity header
            boolean isSelected = !selectedGroup && track.getEntityName().equals(selectedEntity);
            for (PropertyType p : PropertyType.values()) {
                if (shouldShowPropertyTrack(track, isSelected, false, false, p)) trackCount++;
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
        drawEventCues(gc, w, h);
        drawAudioCues(gc, w, h);
        drawPlayhead(gc, h);
        drawHoverGuide(gc, w, h);
        drawSelectionMarquee(gc);
    }

    private void drawTimeRuler(GraphicsContext gc, double width) {
        gc.setFill(Color.web("#181818"));
        gc.fillRect(0, 0, LABEL_WIDTH, HEADER_HEIGHT);
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(LABEL_WIDTH, 0, Math.max(0.0, width - LABEL_WIDTH), HEADER_HEIGHT);

        double duration = project.getTotalDurationMs();
        double step = computeGridStep();
        double minorStep = Math.max(10.0, step / 5.0);
        double viewStart = Math.max(0.0, scrollX / pixelsPerMs);
        double viewEnd = Math.min(duration, Math.max(0.0, (scrollX + width - LABEL_WIDTH) / pixelsPerMs));

        gc.setStroke(Color.web("#2f2f2f", 0.58));
        gc.setLineWidth(0.5);
        for (double t = Math.floor(viewStart / minorStep) * minorStep; t <= viewEnd; t += minorStep) {
            if (t < 0) continue;
            if (isMajorGridLine(t, step)) continue;
            double x = LABEL_WIDTH + t * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH || x > width) continue;
            gc.strokeLine(x, HEADER_HEIGHT - 3, x, HEADER_HEIGHT);
        }

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(1);
        gc.setFill(TEXT_COLOR);
        gc.setFont(javafx.scene.text.Font.font(10));

        for (double t = Math.floor(viewStart / step) * step; t <= viewEnd; t += step) {
            if (t < 0) continue;
            double x = LABEL_WIDTH + t * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH || x > width) continue;

            gc.strokeLine(x, HEADER_HEIGHT - 7, x, HEADER_HEIGHT);

            String label = formatTime(t);
            gc.fillText(label, x - 15, HEADER_HEIGHT - 10);
        }

        gc.setFill(Color.web("#a6a6a6"));
        gc.setFont(javafx.scene.text.Font.font(10));
        gc.fillText("Timeline", 10, HEADER_HEIGHT - 10);
        gc.setStroke(Color.web("#333333"));
        gc.strokeLine(LABEL_WIDTH, 0, LABEL_WIDTH, HEADER_HEIGHT);
        gc.strokeLine(LABEL_WIDTH, HEADER_HEIGHT, width, HEADER_HEIGHT);
    }

    private void drawTracks(GraphicsContext gc, double width) {
        double y = HEADER_HEIGHT - scrollY;
        EntityTrack groupTrack = selectedGroupTrack();

        if (shouldShowRuntimeCameraBlock()) {
            y = drawTrackBlock(gc, width, y,
                resolveRuntimeCameraTrack(false),
                RUNTIME_CAMERA_TARGET,
                RUNTIME_CAMERA_LABEL,
                isRuntimeCameraSelected(),
                false,
                true,
                CAMERA_PROPERTIES);
        }

        if (selectedGroup && groupTrack != null && selectedEntity != null) {
            y = drawTrackBlock(gc, width, y, groupTrack, selectedEntity, "[Group] " + selectedEntity, true, true, false, GROUP_PROPERTIES);
        }

        for (EntityTrack track : project.getTracks()) {
            if (track == null || isRuntimeCameraCarrier(track)) continue;
            String entityName = track.getEntityName();
            boolean isSelected = !selectedGroup && entityName.equals(selectedEntity);
            y = drawTrackBlock(gc, width, y, track, entityName, entityName, isSelected, false, false, PropertyType.values());
        }
    }

    private double drawTrackBlock(GraphicsContext gc,
                                  double width,
                                  double y,
                                  EntityTrack track,
                                  String selectionName,
                                  String label,
                                  boolean isSelected,
                                  boolean groupTrack,
                                  boolean runtimeCameraTrack,
                                  PropertyType[] properties) {
        gc.setFill(isSelected ? Color.web("#2a2a2a") : Color.web("#1a1a1a"));
        if (isHoveredRow(selectionName, track, groupTrack, null)) {
            gc.setFill(Color.web("#24313a"));
        }
        gc.fillRect(0, y, width, TRACK_HEIGHT);
        gc.setFill(TEXT_COLOR);
        gc.setFont(javafx.scene.text.Font.font(12));
        gc.fillText(label, 8, y + 16);
        
        // Draw constraint indicator
        if (!groupTrack && !runtimeCameraTrack && project != null) {
            Constraint constraint = project.getConstraint(selectionName);
            if (constraint != null) {
                drawConstraintIndicator(gc, width, y, constraint);
            }
        }
        
        drawTrackGridLines(gc, y, width);
        y += TRACK_HEIGHT;

        for (PropertyType prop : properties) {
            boolean showTrack = shouldShowPropertyTrack(track, isSelected, groupTrack, runtimeCameraTrack, prop);
            if (!showTrack) continue;

            boolean propSelected = isSelected && prop == selectedProperty;
            boolean propHovered = isHoveredRow(selectionName, track, groupTrack, prop);
            gc.setFill(propSelected
                ? Color.web("#3a3a3a")
                : propHovered ? Color.web("#202832") : Color.web("#151515"));
            gc.fillRect(0, y, width, TRACK_HEIGHT);

            Color propColor = trackColorFor(prop);
            // Color coded left accent bar
            gc.setFill(propColor.deriveColor(0, 1, 1, 0.5));
            gc.fillRect(0, y, 3, TRACK_HEIGHT);

            gc.setFill(propColor.deriveColor(0, 0.6, 1, 1));
            gc.setFont(javafx.scene.text.Font.font(10));
            gc.fillText("  └ " + prop.getDisplayName(), 12, y + 15);

            drawTrackGridLines(gc, y, width);
            drawKeyframes(gc, selectionName, track, prop, y, width);
            y += TRACK_HEIGHT;
        }
        return y;
    }

    private void drawTrackGridLines(GraphicsContext gc, double y, double width) {
        double step = computeGridStep();
        double duration = project != null ? project.getTotalDurationMs() : 3000;
        double viewStart = Math.max(0.0, scrollX / pixelsPerMs);
        double viewEnd = Math.min(duration, Math.max(0.0, (scrollX + width - LABEL_WIDTH) / pixelsPerMs));
        
        for (double x = 0; x < width; x += step) {
            gc.setStroke(Color.web("#333333", 0.3));
            gc.setLineWidth(1.0);
            gc.strokeLine(x, y, x, y + TRACK_HEIGHT);
        }

        gc.setStroke(GRID_COLOR);
        gc.setLineWidth(0.5);
        for (double t = Math.floor(viewStart / step) * step; t <= viewEnd; t += step) {
            if (t < 0) continue;
            double x = LABEL_WIDTH + t * pixelsPerMs - scrollX;
            if (x >= LABEL_WIDTH && x <= width) {
                gc.strokeLine(x, y, x, y + TRACK_HEIGHT);
            }
        }
        gc.setStroke(Color.web("#202020"));
        gc.strokeLine(0, y + TRACK_HEIGHT - 0.5, width, y + TRACK_HEIGHT - 0.5);
        gc.setStroke(Color.web("#333333"));
        gc.strokeLine(LABEL_WIDTH, y, LABEL_WIDTH, y + TRACK_HEIGHT);
    }
    
    private void drawConstraintIndicator(GraphicsContext gc, double width, double y, Constraint constraint) {
        // Draw a small icon/indicator on the right side of the track header
        double iconSize = 12;
        double iconX = width - iconSize - 8;
        double iconY = y + (TRACK_HEIGHT - iconSize) / 2;
        
        if (constraint.getType() == Constraint.Type.PARENT_CHILD) {
            // Draw parent-child icon (chain link style)
            gc.setStroke(Color.web("#ffaa00", 0.8));
            gc.setLineWidth(2.0);
            gc.strokeOval(iconX, iconY, iconSize, iconSize);
            gc.setFill(Color.web("#ffaa00", 0.8));
            gc.fillOval(iconX + 3, iconY + 3, 3, 3);
            gc.fillOval(iconX + 6, iconY + 6, 3, 3);
        } else if (constraint.getType() == Constraint.Type.LOOK_AT) {
            // Draw look-at icon (eye style)
            gc.setStroke(Color.web("#00aaff", 0.8));
            gc.setLineWidth(2.0);
            gc.strokeOval(iconX, iconY, iconSize, iconSize);
            gc.setFill(Color.web("#00aaff", 0.8));
            gc.fillOval(iconX + 4, iconY + 4, 4, 4);
        }
    }

    private void drawKeyframes(GraphicsContext gc, String selectionName, EntityTrack track, PropertyType prop, double y, double width) {
        if (track == null || prop == null) return;
        List<Keyframe> keyframes = track.getKeyframes(prop);
        double cy = y + TRACK_HEIGHT / 2;
        drawKeyframeSegments(gc, keyframes, prop, cy, width);

        for (Keyframe kf : keyframes) {
            double x = LABEL_WIDTH + kf.getTimeMs() * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH - 10 || x > width + 10) continue;

            String effectiveSelectionName = selectionName != null ? selectionName : track.getEntityName();
            String storageName = track.getEntityName();
            boolean isSelected = kf == selectedKeyframe
                || isSelected(storageName, prop, kf)
                || (effectiveSelectionName != null
                    && !effectiveSelectionName.equals(storageName)
                    && isSelected(effectiveSelectionName, prop, kf));
            boolean isHovered = hoverKeyframe == kf
                && prop == hoverRowProperty
                && Objects.equals(storageName, hoverRowStorageName);
            gc.setFill(isSelected ? KEYFRAME_SELECTED_COLOR : trackColorFor(prop));

            // Diamond shape
            double size = isSelected || isHovered ? 7 : 6;
            gc.fillPolygon(
                new double[]{x, x + size, x, x - size},
                new double[]{cy - size, cy, cy + size, cy},
                4
            );
            gc.setStroke(isSelected ? Color.web("#fff6df", 0.95) : Color.web("#050505", 0.72));
            gc.setLineWidth(isSelected ? 1.4 : 0.8);
            gc.strokePolygon(
                new double[]{x, x + size, x, x - size},
                new double[]{cy - size, cy, cy + size, cy},
                4
            );
            if (isHovered && !isSelected) {
                gc.setStroke(Color.web("#8bd2ff", 0.8));
                gc.setLineWidth(1.2);
                gc.strokeOval(x - 8.5, cy - 8.5, 17, 17);
            }
        }
    }

    private void drawKeyframeSegments(GraphicsContext gc, List<Keyframe> keyframes, PropertyType prop, double cy, double width) {
        if (keyframes == null || keyframes.size() < 2) return;
        List<Keyframe> sorted = new ArrayList<>(keyframes);
        sorted.sort(Comparator.comparingDouble(Keyframe::getTimeMs));
        Color lineColor = trackColorFor(prop).deriveColor(0, 0.85, 0.95, 0.34);
        gc.setLineWidth(1.6);
        for (int i = 0; i < sorted.size() - 1; i++) {
            Keyframe left = sorted.get(i);
            Keyframe right = sorted.get(i + 1);
            double x1 = LABEL_WIDTH + left.getTimeMs() * pixelsPerMs - scrollX;
            double x2 = LABEL_WIDTH + right.getTimeMs() * pixelsPerMs - scrollX;
            if (x2 < LABEL_WIDTH || x1 > width) continue;
            gc.setStroke(lineColor);
            if (left.getInterpolation() != null && !"TWEEN".equals(left.getInterpolation().name())) {
                gc.setLineDashes(5, 4);
            }
            gc.strokeLine(Math.max(LABEL_WIDTH, x1), cy, Math.min(width, x2), cy);
            gc.setLineDashes((double[]) null);
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

    private void drawEventCues(GraphicsContext gc, double width, double height) {
        List<EditorEventCue> cues = project.getEditorEventCues();
        if (cues.isEmpty()) return;

        double cueY = height - 42;
        gc.setFont(javafx.scene.text.Font.font(9));

        for (EditorEventCue cue : cues) {
            if (cue == null || cue.getType() == null || cue.getType().isBlank()) continue;
            double x = LABEL_WIDTH + cue.getTimeMs() * pixelsPerMs - scrollX;
            if (x < LABEL_WIDTH - 10 || x > width + 10) continue;

            Color markerColor = eventCueColor(cue.getType());
            gc.setFill(markerColor);
            gc.fillRect(x - 5, cueY - 5, 10, 10);

            gc.setStroke(markerColor.deriveColor(0, 1.0, 0.9, 0.35));
            gc.setLineWidth(1);
            gc.strokeLine(x, HEADER_HEIGHT, x, cueY - 6);

            gc.setFill(markerColor.deriveColor(0, 1.0, 1.15, 0.95));
            gc.fillText(eventCueLabel(cue.getType()), x - 4, cueY + 13);
        }
    }

    private static Color eventCueColor(String type) {
        if (type == null) return Color.web("#76d7ea", 0.9);
        return switch (type.trim().toLowerCase()) {
            case "expression" -> Color.web("#7bd88f", 0.92);
            case "show" -> Color.web("#76d7ea", 0.92);
            case "hide" -> Color.web("#ff7b8a", 0.92);
            case "replace" -> Color.web("#f0b673", 0.92);
            case "scene" -> Color.web("#b892ff", 0.92);
            default -> Color.web("#8fa3b8", 0.88);
        };
    }

    private static String eventCueLabel(String type) {
        if (type == null || type.isBlank()) return "?";
        return switch (type.trim().toLowerCase()) {
            case "expression" -> "E";
            case "show" -> "S";
            case "hide" -> "H";
            case "replace" -> "R";
            case "scene" -> "B";
            default -> type.substring(0, 1).toUpperCase();
        };
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
        double width = canvas.getWidth();
        if (x < LABEL_WIDTH || x > width) return;

        gc.setStroke(PLAYHEAD_COLOR);
        gc.setLineWidth(2);
        gc.strokeLine(x, 0, x, height);

        // Playhead handle
        gc.setFill(PLAYHEAD_COLOR);
        double[] xPoints = {x - 8, x + 8, x};
        double[] yPoints = {0, 0, 12};
        gc.fillPolygon(xPoints, yPoints, 3);

        String label = formatTime(project.getPlayheadMs());
        double badgeWidth = Math.max(48.0, label.length() * 6.4 + 16.0);
        double badgeX = clamp(x - badgeWidth / 2.0, LABEL_WIDTH + 4.0, width - badgeWidth - 4.0);
        gc.setFill(Color.web("#2d141d", 0.94));
        gc.fillRoundRect(badgeX, 4, badgeWidth, 18, 6, 6);
        gc.setStroke(Color.web("#f38ba8", 0.55));
        gc.setLineWidth(1);
        gc.strokeRoundRect(badgeX + 0.5, 4.5, badgeWidth - 1.0, 17.0, 6, 6);
        gc.setFill(Color.web("#ffdce6"));
        gc.setFont(javafx.scene.text.Font.font(10));
        gc.fillText(label, badgeX + 8, 17);
    }

    private void drawHoverGuide(GraphicsContext gc, double width, double height) {
        if (!Double.isFinite(hoverTimeMs) || hoverX < LABEL_WIDTH || hoverY < HEADER_HEIGHT) return;
        double x = LABEL_WIDTH + hoverTimeMs * pixelsPerMs - scrollX;
        if (x >= LABEL_WIDTH && x <= width) {
            gc.setStroke(Color.web("#8bd2ff", 0.30));
            gc.setLineWidth(1);
            gc.setLineDashes(3, 4);
            gc.strokeLine(x, HEADER_HEIGHT, x, height);
            gc.setLineDashes((double[]) null);
        }

        String text = hoverReadout == null || hoverReadout.isBlank() ? formatTime(hoverTimeMs) : hoverReadout;
        text = abbreviate(text, Math.max(18, (int) ((width - 28.0) / 6.2)));
        double boxWidth = Math.min(width - 16.0, Math.max(92.0, text.length() * 6.2 + 18.0));
        double boxHeight = 22.0;
        double boxX = clamp(hoverX + 12.0, LABEL_WIDTH + 6.0, width - boxWidth - 8.0);
        double boxY = hoverY + 14.0;
        if (boxY + boxHeight > height - 8.0) {
            boxY = hoverY - boxHeight - 10.0;
        }
        boxY = clamp(boxY, HEADER_HEIGHT + 4.0, Math.max(HEADER_HEIGHT + 4.0, height - boxHeight - 6.0));

        gc.setFill(Color.web("#111820", 0.94));
        gc.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 6, 6);
        gc.setStroke(Color.web("#8bd2ff", 0.45));
        gc.setLineWidth(1);
        gc.strokeRoundRect(boxX + 0.5, boxY + 0.5, boxWidth - 1.0, boxHeight - 1.0, 6, 6);
        gc.setFill(Color.web("#dceeff"));
        gc.setFont(javafx.scene.text.Font.font(10));
        gc.fillText(text, boxX + 9, boxY + 15);
    }

    private void drawSelectionMarquee(GraphicsContext gc) {
        if (!marqueeSelecting) return;
        double x = Math.min(marqueeStartX, marqueeEndX);
        double y = Math.min(marqueeStartY, marqueeEndY);
        double w = Math.abs(marqueeEndX - marqueeStartX);
        double h = Math.abs(marqueeEndY - marqueeStartY);
        if (w < 1 || h < 1) return;
        gc.setFill(Color.web("#4da3ff", 0.12));
        gc.fillRect(x, y, w, h);
        gc.setStroke(Color.web("#4da3ff", 0.75));
        gc.setLineWidth(1.0);
        gc.setLineDashes(5, 4);
        gc.strokeRect(x + 0.5, y + 0.5, Math.max(0.0, w - 1.0), Math.max(0.0, h - 1.0));
        gc.setLineDashes((double[]) null);
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
            return String.format(Locale.ROOT, "%.1fs", ms / 1000);
        }
        return String.format(Locale.ROOT, "%.0fms", ms);
    }

    private String formatKeyframeValue(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        if (text.endsWith(".00")) return text.substring(0, text.length() - 3);
        if (text.endsWith("0")) return text.substring(0, text.length() - 1);
        return text;
    }

    private String buildKeyframeHoverReadout(TrackRow row, Keyframe keyframe) {
        if (row == null || keyframe == null || row.property() == null) return "";
        String target = row.runtimeCamera() ? "Runtime Camera" : row.selectionName();
        if (target == null || target.isBlank()) target = row.displayLabel();
        String interpolation = keyframe.getInterpolation() != null
            ? keyframe.getInterpolation().name().toLowerCase(Locale.ROOT)
            : "tween";
        String easing = keyframe.getEasingSpec() != null ? keyframe.getEasingSpec().toDslString() : "linear";
        return target + " / " + row.property().getDisplayName()
            + "  " + formatTime(keyframe.getTimeMs())
            + "  value " + formatKeyframeValue(keyframe.getValue())
            + "  " + interpolation + " " + easing;
    }

    private String buildRowHoverReadout(TrackRow row, double timeMs) {
        if (row == null) return "Time " + formatTime(timeMs);
        if (row.property() == null) return row.displayLabel() + "  " + formatTime(timeMs);
        String target = row.runtimeCamera() ? "Runtime Camera" : row.selectionName();
        if (target == null || target.isBlank()) target = row.displayLabel();
        return target + " / " + row.property().getDisplayName() + "  " + formatTime(timeMs);
    }

    private void updateHoverFromMouse(MouseEvent e) {
        hoverX = e.getX();
        hoverY = e.getY();
        hoverKeyframe = null;
        hoverRowStorageName = null;
        hoverRowProperty = null;
        hoverRowGroup = false;

        if (hoverX < LABEL_WIDTH || hoverY < HEADER_HEIGHT || hoverX > canvas.getWidth() || hoverY > canvas.getHeight()) {
            hoverTimeMs = Double.NaN;
            hoverReadout = "";
            render();
            return;
        }

        hoverTimeMs = clampToTimeline((hoverX - LABEL_WIDTH + scrollX) / pixelsPerMs);
        KeyframeHit hit = findKeyframeAt(hoverX, hoverY);
        if (hit != null) {
            TrackRow row = hit.row();
            hoverKeyframe = hit.keyframe();
            hoverRowStorageName = storageNameForRow(row);
            hoverRowProperty = row.property();
            hoverRowGroup = row.group();
            hoverReadout = buildKeyframeHoverReadout(row, hit.keyframe());
            render();
            return;
        }

        TrackRow row = findRowAt(hoverY);
        if (row != null) {
            hoverRowStorageName = storageNameForRow(row);
            hoverRowProperty = row.property();
            hoverRowGroup = row.group();
        }
        hoverReadout = buildRowHoverReadout(row, hoverTimeMs);
        render();
    }

    private void clearHoverState() {
        hoverX = Double.NaN;
        hoverY = Double.NaN;
        hoverTimeMs = Double.NaN;
        hoverReadout = "";
        hoverRowStorageName = null;
        hoverRowProperty = null;
        hoverRowGroup = false;
        hoverKeyframe = null;
    }

    private void handleMouseMoved(MouseEvent e) {
        updateHoverFromMouse(e);
    }

    private void handleMouseExited(MouseEvent e) {
        clearHoverState();
        render();
    }

    private void handleMousePressed(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        dismissContextMenu();

        if (e.getButton() == MouseButton.MIDDLE) {
            middlePanning = true;
            middlePanStartX = x;
            middlePanStartY = y;
            middlePanStartScrollX = scrollX;
            middlePanStartScrollY = scrollY;
            canvas.setCursor(javafx.scene.Cursor.MOVE);
            e.consume();
            return;
        }

        if (e.getButton() != MouseButton.PRIMARY) {
            return;
        }

        if (y < HEADER_HEIGHT + 5) {
            draggingPlayhead = true;
            updatePlayheadFromX(x);
            return;
        }

        KeyframeHit hit = findKeyframeAt(x, y);
        if (hit != null) {
            selectedEntity = hit.row().selectionName();
            selectedGroup = hit.row().group();
            selectedProperty = hit.row().property();

            KeyframeSelectionModel.KeyframeRef ref =
                KeyframeSelectionModel.ref(storageNameForRow(hit.row()), hit.row().property(), hit.keyframe());
            if (e.isShiftDown()) {
                selectionModel.toggleSelect(ref);
                selectedKeyframe = selectionModel.getSelectedOrdered().stream()
                    .reduce((first, second) -> second)
                    .map(KeyframeSelectionModel.KeyframeRef::keyframe)
                    .orElse(null);
            } else {
                selectPrimaryKeyframe(hit.row(), hit.keyframe());
            }
            draggingKeyframe = selectedKeyframe != null;
            dragAnchorX = x;
            captureDragStartTimes();
            notifyTargetSelectionChanged();
            notifyKeyframeSelectionChanged();
            render();
            return;
        }

        clearKeyframeSelection();
        selectTrackAt(y);

        marqueeSelecting = y >= HEADER_HEIGHT;
        marqueeStartX = x;
        marqueeStartY = y;
        marqueeEndX = x;
        marqueeEndY = y;
        render();
    }

    private void handleMouseDragged(MouseEvent e) {
        if (middlePanning) {
            double dx = e.getX() - middlePanStartX;
            double dy = e.getY() - middlePanStartY;
            scrollX = Math.max(0.0, middlePanStartScrollX - dx);
            scrollY = Math.max(0.0, middlePanStartScrollY - dy);
            clampScrollOffsets();
            render();
            return;
        }
        if (draggingPlayhead) {
            updatePlayheadFromX(e.getX());
        } else if (draggingKeyframe && selectedKeyframe != null) {
            double dt = (e.getX() - dragAnchorX) / pixelsPerMs;
            if (!dragStartTimes.isEmpty()) {
                for (Map.Entry<KeyframeSelectionModel.KeyframeRef, Double> entry : dragStartTimes.entrySet()) {
                    Keyframe moving = entry.getKey().keyframe();
                    double next = clampOrExpandTimeline(snapTime(entry.getValue() + dt));
                    moving.setTimeMs(next);
                }
            } else {
                double next = clampOrExpandTimeline(snapTime(selectedKeyframe.getTimeMs() + dt));
                selectedKeyframe.setTimeMs(next);
            }
            lastDragMouseX = e.getX();
            ensureDragAutoScrollRunning();
            render();
        } else if (marqueeSelecting) {
            marqueeEndX = e.getX();
            marqueeEndY = e.getY();
            applyMarqueeSelection(e.isShiftDown());
            render();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        e.consume();
        if (middlePanning) {
            middlePanning = false;
            canvas.setCursor(javafx.scene.Cursor.DEFAULT);
            return;
        }
        stopDragAutoScroll();
        if (draggingKeyframe && selectedEntity != null) {
            // Build undo commands for the drag
            if (commandStack != null && !dragStartTimes.isEmpty()) {
                List<PuppeteerCommand> cmds = new ArrayList<>();
                for (Map.Entry<KeyframeSelectionModel.KeyframeRef, Double> entry : dragStartTimes.entrySet()) {
                    Keyframe kf = entry.getKey().keyframe();
                    double oldTime = entry.getValue();
                    double newTime = kf.getTimeMs();
                    if (Math.abs(oldTime - newTime) > 0.001) {
                        cmds.add(PuppeteerCommand.moveKeyframe(kf, oldTime, newTime));
                    }
                }
                if (!cmds.isEmpty()) {
                    // Already executed (drag mutated directly), so push a pre-executed composite
                    commandStack.pushExecuted(PuppeteerCommand.composite("Drag keyframes", cmds));
                }
            }
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
        if (marqueeSelecting) {
            applyMarqueeSelection(e.isShiftDown());
            marqueeSelecting = false;
            notifyKeyframeSelectionChanged();
        }
        draggingPlayhead = false;
        draggingKeyframe = false;
        dragStartTimes.clear();
        marqueeSelecting = false;
        render();
    }

    private void handleMouseClicked(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            double x = e.getX();
            double y = e.getY();
            if (y > HEADER_HEIGHT) {
                KeyframeHit hit = findKeyframeAt(x, y);
                if (hit != null) {
                    selectedEntity = hit.row().selectionName();
                    selectedGroup = hit.row().group();
                    selectedProperty = hit.row().property();
                    selectPrimaryKeyframe(hit.row(), hit.keyframe());
                    notifyTargetSelectionChanged();
                    notifyKeyframeSelectionChanged();
                    render();
                    openEasingCurveEditor();
                } else {
                    selectTrackAt(y);
                    double time = Math.max(0, (x - LABEL_WIDTH + scrollX) / pixelsPerMs);
                    addKeyframeAtTime(time);
                }
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
            zoomSlider.setValue(pixelsPerMs);
            lblZoomLevel.setText(formatZoomLevel());
            // Adjust scroll to keep the time under the mouse stable
            scrollX = Math.max(0, timeBefore * pixelsPerMs - (mouseX - LABEL_WIDTH));
            clampScrollOffsets();
            render();
        } else {
            scrollX = Math.max(0, scrollX - e.getDeltaX());
            double maxScrollY = Math.max(0, computeRequiredHeight() - canvas.getHeight());
            scrollY = Math.max(0, Math.min(maxScrollY, scrollY - e.getDeltaY()));
            render();
        }
        e.consume();
    }

    public double getPixelsPerMs() { return pixelsPerMs; }
    public void setPixelsPerMs(double ppm) {
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, ppm));
        clampScrollOffsets();
        render();
    }

    private void updateCanvasViewportSize() {
        double viewportWidth = Math.max(1.0, getWidth());
        double viewportHeight = Math.max(1.0, getHeight());
        canvas.setWidth(viewportWidth);
        canvas.setHeight(viewportHeight);
        canvasContainer.setPrefSize(viewportWidth, viewportHeight);
        canvasContainer.setMinSize(viewportWidth, viewportHeight);
        canvasContainer.setMaxSize(viewportWidth, viewportHeight);
        clampScrollOffsets();
    }

    private void clampScrollOffsets() {
        double contentWidth = LABEL_WIDTH + project.getTotalDurationMs() * pixelsPerMs + 120.0;
        double maxScrollX = Math.max(0.0, contentWidth - Math.max(1.0, canvas.getWidth()));
        double maxScrollY = Math.max(0.0, computeRequiredHeight() - Math.max(1.0, canvas.getHeight()));
        scrollX = Math.max(0.0, Math.min(maxScrollX, scrollX));
        scrollY = Math.max(0.0, Math.min(maxScrollY, scrollY));
    }

    /** Zoom to fit the entire timeline duration in the visible area. */
    public void zoomToFit() {
        double visible = Math.max(100, canvas.getWidth() - LABEL_WIDTH - 20);
        double duration = Math.max(100, project.getTotalDurationMs());
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, visible / duration));
        scrollX = 0;
        zoomSlider.setValue(pixelsPerMs);
        updateZoomLabel();
        render();
    }

    private void zoomStep(double factor) {
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, pixelsPerMs * factor));
        zoomSlider.setValue(pixelsPerMs);
        clampScrollOffsets();
        updateZoomLabel();
        render();
    }

    private void updateZoomLabel() {
        lblZoomLevel.setText(formatZoomLevel());
    }

    private String formatZoomLevel() {
        double msPerPx = 1.0 / Math.max(MIN_PIXELS_PER_MS, pixelsPerMs);
        if (msPerPx >= 1000) {
            return String.format("%.1fs/px", msPerPx / 1000.0);
        } else {
            return String.format("%.0fms/px", msPerPx);
        }
    }

    public boolean zoomToSelection() {
        List<Double> times = navigationTimes();
        if (times.isEmpty()) {
            zoomToFit();
            return false;
        }
        double[] window = computeFocusWindow(times.get(0), times.get(times.size() - 1), project.getTotalDurationMs());
        double start = window[0];
        double end = window[1];
        double visible = Math.max(100, canvas.getWidth() - LABEL_WIDTH - 20);
        double span = Math.max(100.0, end - start);
        pixelsPerMs = Math.max(MIN_PIXELS_PER_MS, Math.min(MAX_PIXELS_PER_MS, visible / span));
        scrollX = Math.max(0.0, start * pixelsPerMs - 12.0);
        zoomSlider.setValue(pixelsPerMs);
        updateZoomLabel();
        render();
        return true;
    }

    public boolean jumpPlayheadToPreviousKeyframe() {
        return jumpPlayheadToAdjacentKeyframe(false);
    }

    public boolean jumpPlayheadToNextKeyframe() {
        return jumpPlayheadToAdjacentKeyframe(true);
    }

    private void updatePlayheadFromX(double x) {
        double time = clampToTimeline(snapTime((x - LABEL_WIDTH + scrollX) / pixelsPerMs));
        project.setPlayheadMs(time);
        if (onPlayheadChanged != null) onPlayheadChanged.accept(time);
        render();
    }

    private KeyframeHit findKeyframeAt(double mx, double my) {
        for (TrackRow row : buildVisibleRows()) {
            if (row.property() == null) continue;
            if (row.track() == null) continue;
            if (my < row.y() || my > row.y() + row.height()) continue;
            double cy = row.y() + row.height() / 2;
            for (Keyframe keyframe : row.track().getKeyframes(row.property())) {
                double kx = LABEL_WIDTH + keyframe.getTimeMs() * pixelsPerMs - scrollX;
                double dist = Math.sqrt(Math.pow(mx - kx, 2) + Math.pow(my - cy, 2));
                if (dist < 10) {
                    return new KeyframeHit(row, keyframe);
                }
            }
        }
        return null;
    }

    private TrackRow findRowAt(double my) {
        for (TrackRow row : buildVisibleRows()) {
            if (my >= row.y() && my < row.y() + row.height()) {
                return row;
            }
        }
        return null;
    }

    private void selectTrackAt(double my) {
        for (TrackRow row : buildVisibleRows()) {
            if (my < row.y() || my >= row.y() + row.height()) continue;
            selectedEntity = row.selectionName();
            selectedGroup = row.group();
            selectedProperty = resolveSelectionProperty(row);
            notifyTargetSelectionChanged();
            render();
            return;
        }
    }

    private void captureDragStartTimes() {
        dragStartTimes.clear();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                dragStartTimes.put(ref, ref.keyframe().getTimeMs());
            }
        } else if (selectedKeyframe != null) {
            dragStartTimes.put(KeyframeSelectionModel.ref(currentStorageSelectionName(), selectedProperty, selectedKeyframe),
                selectedKeyframe.getTimeMs());
        }
    }

    private Set<PropertyType> collectSelectedProperties(EntityTrack track) {
        Set<PropertyType> props = new HashSet<>();
        if (track == null || !selectionModel.hasSelection()) return props;
        for (PropertyType prop : editablePropertiesForSelection()) {
            for (Keyframe kf : track.getKeyframes(prop)) {
                if (isSelected(track.getEntityName(), prop, kf)) {
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

        if (selectionModel.hasSelection()) {
            for (PropertyType property : editablePropertiesForSelection()) {
                List<Keyframe> matches = new ArrayList<>();
                for (Keyframe keyframe : track.getKeyframes(property)) {
                    if (isSelected(track.getEntityName(), property, keyframe)) matches.add(keyframe);
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

    private List<ClipboardEntry> collectClipboardEntries() {
        List<ClipboardEntry> entries = new ArrayList<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                if (ref == null || ref.keyframe() == null || ref.property() == null) continue;
                String sourceName = ref.entityName();
                if (sourceName == null || sourceName.isBlank()) continue;
                entries.add(new ClipboardEntry(
                    sourceName,
                    isClipboardGroupSource(sourceName),
                    ref.property(),
                    ref.keyframe(),
                    0.0));
            }
            return entries;
        }

        EntityTrack track = selectedTrack(false);
        if (track == null) return entries;
        String sourceName = currentStorageSelectionName();
        if (sourceName == null || sourceName.isBlank()) sourceName = track.getEntityName();
        boolean sourceGroup = selectedGroup;
        Map<PropertyType, List<Keyframe>> selection = selectedKeyframesByProperty(track);
        for (Map.Entry<PropertyType, List<Keyframe>> entry : selection.entrySet()) {
            PropertyType property = entry.getKey();
            for (Keyframe keyframe : entry.getValue()) {
                entries.add(new ClipboardEntry(sourceName, sourceGroup, property, keyframe, 0.0));
            }
        }
        entries.sort(Comparator.comparingDouble((ClipboardEntry e) -> e.keyframe().getTimeMs())
            .thenComparing((ClipboardEntry e) -> e.sourceName(), Comparator.nullsLast(String::compareTo))
            .thenComparingInt(e -> e.property().ordinal()));
        return entries;
    }

    private boolean clipboardHasSingleSourceTarget() {
        String sourceName = null;
        Boolean sourceGroup = null;
        for (ClipboardEntry entry : copiedKeyframes) {
            if (entry == null) continue;
            if (sourceName == null) {
                sourceName = entry.sourceName();
                sourceGroup = entry.group();
                continue;
            }
            if (!Objects.equals(sourceName, entry.sourceName()) || !Objects.equals(sourceGroup, entry.group())) {
                return false;
            }
        }
        return sourceName != null;
    }

    private PasteTarget resolvePasteTarget(
        ClipboardEntry entry,
        EntityTrack selectedTargetTrack,
        boolean retargetSingleSource
    ) {
        if (entry == null) return null;
        if (retargetSingleSource && selectedTargetTrack != null) {
            return new PasteTarget(selectedTargetTrack, selectedEntity, selectedGroup);
        }
        String sourceName = entry.sourceName();
        if (sourceName == null || sourceName.isBlank()) return null;
        if (entry.group()) {
            EntityGroup group = project.getGroup(sourceName);
            return group != null ? new PasteTarget(group.getGroupTrack(), sourceName, true) : null;
        }
        if (isRuntimeCameraTarget(sourceName)) {
            EntityTrack track = resolveRuntimeCameraTrack(true);
            return track != null ? new PasteTarget(track, RUNTIME_CAMERA_TARGET, false) : null;
        }
        EntityTrack track = project.getTrack(sourceName);
        return track != null ? new PasteTarget(track, sourceName, false) : null;
    }

    private boolean isPropertySupportedForTarget(PropertyType property, String targetName, boolean group) {
        if (property == null) return false;
        if (isRuntimeCameraTarget(targetName)) return property.isCameraProperty();
        return !group || isGroupProperty(property);
    }

    private boolean isClipboardGroupSource(String sourceName) {
        if (sourceName == null || sourceName.isBlank()) return false;
        if (project.getGroup(sourceName) == null) return false;
        if (project.getTrack(sourceName) == null) return true;
        return selectedGroup && Objects.equals(selectedEntity, sourceName);
    }

    private void clearKeyframeSelection() {
        selectionModel.clearSelection();
        selectedKeyframe = null;
        dragStartTimes.clear();
        if (onKeyframeSelected != null) onKeyframeSelected.accept(null);
    }

    private List<TrackRow> buildVisibleRows() {
        List<TrackRow> rows = new ArrayList<>();
        double y = HEADER_HEIGHT - scrollY;
        EntityTrack groupTrack = selectedGroupTrack();

        if (shouldShowRuntimeCameraBlock()) {
            EntityTrack runtimeCameraTrack = resolveRuntimeCameraTrack(false);
            rows.add(new TrackRow(runtimeCameraTrack, RUNTIME_CAMERA_TARGET, RUNTIME_CAMERA_LABEL, false, true, null, y, TRACK_HEIGHT));
            y += TRACK_HEIGHT;
            for (PropertyType prop : CAMERA_PROPERTIES) {
                boolean showTrack = shouldShowPropertyTrack(runtimeCameraTrack, isRuntimeCameraSelected(), false, true, prop);
                if (!showTrack) continue;
                rows.add(new TrackRow(runtimeCameraTrack, RUNTIME_CAMERA_TARGET, prop.getDisplayName(), false, true, prop, y, TRACK_HEIGHT));
                y += TRACK_HEIGHT;
            }
        }

        if (selectedGroup && groupTrack != null && selectedEntity != null) {
            rows.add(new TrackRow(groupTrack, selectedEntity, "[Group] " + selectedEntity, true, false, null, y, TRACK_HEIGHT));
            y += TRACK_HEIGHT;
            for (PropertyType prop : GROUP_PROPERTIES) {
                boolean showTrack = shouldShowPropertyTrack(groupTrack, true, true, false, prop);
                if (!showTrack) continue;
                rows.add(new TrackRow(groupTrack, selectedEntity, prop.getDisplayName(), true, false, prop, y, TRACK_HEIGHT));
                y += TRACK_HEIGHT;
            }
        }

        for (EntityTrack track : project.getTracks()) {
            if (track == null || isRuntimeCameraCarrier(track)) continue;
            rows.add(new TrackRow(track, track.getEntityName(), track.getEntityName(), false, false, null, y, TRACK_HEIGHT));
            y += TRACK_HEIGHT;
            boolean isSelected = !selectedGroup && track.getEntityName().equals(selectedEntity);
            for (PropertyType prop : PropertyType.values()) {
                boolean showTrack = shouldShowPropertyTrack(track, isSelected, false, false, prop);
                if (!showTrack) continue;
                rows.add(new TrackRow(track, track.getEntityName(), prop.getDisplayName(), false, false, prop, y, TRACK_HEIGHT));
                y += TRACK_HEIGHT;
            }
        }
        return rows;
    }

    private void selectPrimaryKeyframe(TrackRow row, Keyframe keyframe) {
        selectionModel.clearSelection();
        KeyframeSelectionModel.KeyframeRef primary =
            KeyframeSelectionModel.ref(storageNameForRow(row), row.property(), keyframe);
        selectionModel.select(primary);
        PropertyType paired = pairedProperty(row.property());
        if (paired != null) {
            Keyframe linked = row.track() != null ? row.track().findKeyframeAt(paired, keyframe.getTimeMs()) : null;
            if (linked != null) {
                selectionModel.select(KeyframeSelectionModel.ref(storageNameForRow(row), paired, linked));
            }
        }
        selectedKeyframe = keyframe;
    }

    private void applyMarqueeSelection(boolean additive) {
        double x1 = Math.min(marqueeStartX, marqueeEndX);
        double x2 = Math.max(marqueeStartX, marqueeEndX);
        double y1 = Math.min(marqueeStartY, marqueeEndY);
        double y2 = Math.max(marqueeStartY, marqueeEndY);
        if (x2 - x1 < 2 || y2 - y1 < 2) return;

        List<KeyframeSelectionModel.KeyframeRef> hits = new ArrayList<>();
        for (TrackRow row : buildVisibleRows()) {
            if (row.property() == null) continue;
            if (row.track() == null) continue;
            // Marquee includes any track row whose band overlaps the marquee Y-range,
            // not just rows whose center is fully inside.
            if (row.y() + row.height() < y1 || row.y() > y2) continue;
            for (Keyframe keyframe : row.track().getKeyframes(row.property())) {
                double kx = LABEL_WIDTH + keyframe.getTimeMs() * pixelsPerMs - scrollX;
                if (kx >= x1 && kx <= x2) {
                    hits.add(KeyframeSelectionModel.ref(storageNameForRow(row), row.property(), keyframe));
                }
            }
        }
        if (!additive) selectionModel.clearSelection();
        for (KeyframeSelectionModel.KeyframeRef hit : hits) {
            selectionModel.select(hit);
        }
        selectedKeyframe = selectionModel.getSelectedOrdered().stream()
            .reduce((first, second) -> second)
            .map(KeyframeSelectionModel.KeyframeRef::keyframe)
            .orElse(null);
    }

    private void notifyKeyframeSelectionChanged() {
        if (onKeyframeSelected != null) onKeyframeSelected.accept(selectedKeyframe);
    }

    private boolean isSelected(String entityName, PropertyType property, Keyframe keyframe) {
        return selectionModel.isSelected(KeyframeSelectionModel.ref(entityName, property, keyframe));
    }

    private EntityTrack selectedTrack(boolean createForEntity) {
        if (selectedEntity == null || selectedEntity.isBlank()) return null;
        if (isRuntimeCameraTarget(selectedEntity)) {
            return resolveRuntimeCameraTrack(createForEntity);
        }
        if (selectedGroup) {
            EntityGroup group = project.getGroup(selectedEntity);
            return group != null ? group.getGroupTrack() : null;
        }
        return createForEntity ? project.getOrCreateTrack(selectedEntity) : project.getTrack(selectedEntity);
    }

    private EntityTrack resolveTrackForRef(KeyframeSelectionModel.KeyframeRef ref) {
        if (ref == null || ref.entityName() == null || ref.entityName().isBlank()) return null;
        if (isRuntimeCameraTarget(ref.entityName())) {
            return resolveRuntimeCameraTrack(false);
        }
        // Check if it's a group
        EntityGroup group = project.getGroup(ref.entityName());
        if (group != null) return group.getGroupTrack();
        // Otherwise treat as entity
        return project.getTrack(ref.entityName());
    }

    private EntityTrack selectedGroupTrack() {
        if (!selectedGroup || selectedEntity == null || selectedEntity.isBlank()) return null;
        EntityGroup group = project.getGroup(selectedEntity);
        return group != null ? group.getGroupTrack() : null;
    }

    private static boolean isGroupProperty(PropertyType property) {
        return AnimationProject.isGroupProperty(property);
    }

    private static PropertyType pairedProperty(PropertyType property) {
        if (property == PropertyType.X) return PropertyType.Y;
        if (property == PropertyType.Y) return PropertyType.X;
        if (property == PropertyType.CAMERA_X) return PropertyType.CAMERA_Y;
        if (property == PropertyType.CAMERA_Y) return PropertyType.CAMERA_X;
        if (property == PropertyType.SCALE_X) return PropertyType.SCALE_Y;
        if (property == PropertyType.SCALE_Y) return PropertyType.SCALE_X;
        return null;
    }

    private PropertyType resolveSelectionProperty(TrackRow row) {
        if (row == null) return defaultPropertyForSelection();
        if (row.property() != null) return row.property();
        if (row.runtimeCamera()) {
            return selectedProperty != null && selectedProperty.isCameraProperty()
                ? selectedProperty
                : PropertyType.CAMERA_X;
        }
        if (row.group()) {
            return selectedProperty != null && isGroupProperty(selectedProperty)
                ? selectedProperty
                : PropertyType.X;
        }
        if (selectedProperty != null) {
            if (selectedProperty.isEntityProperty()) {
                return selectedProperty;
            }
            EntityTrack track = row.track();
            if (track != null && track.hasKeyframes(selectedProperty)) {
                return selectedProperty;
            }
        }
        return PropertyType.X;
    }

    private boolean shouldShowPropertyTrack(EntityTrack track, boolean isSelected, boolean groupTrack, boolean runtimeCameraTrack, PropertyType property) {
        if (property == null) return false;
        if (runtimeCameraTrack) {
            return property.isCameraProperty() && ((track != null && track.hasKeyframes(property)) || isSelected);
        }
        if (track == null) return false;
        if (groupTrack) {
            return isGroupProperty(property) && (track.hasKeyframes(property) || isSelected);
        }
        if (property.isCameraProperty()) {
            return false;
        }
        if (track.hasKeyframes(property)) {
            return true;
        }
        if (!isSelected) {
            return false;
        }
        if (property.isEntityProperty()) {
            return true;
        }
        return property == selectedProperty;
    }

    private PropertyType defaultPropertyForSelection() {
        if (isRuntimeCameraSelected()) return PropertyType.CAMERA_X;
        return PropertyType.X;
    }

    private boolean isPropertySupportedForSelection(PropertyType property) {
        if (property == null) return true;
        if (isRuntimeCameraSelected()) return property.isCameraProperty();
        return !selectedGroup || isGroupProperty(property);
    }

    private PropertyType[] editablePropertiesForSelection() {
        if (isRuntimeCameraSelected()) return CAMERA_PROPERTIES.clone();
        if (!selectedGroup) return PropertyType.values();
        return GROUP_PROPERTIES.clone();
    }

    private boolean shouldShowRuntimeCameraBlock() {
        return true;
    }

    private EntityTrack resolveRuntimeCameraTrack(boolean createIfMissing) {
        EntityTrack dedicated = project.getTrack(RUNTIME_CAMERA_TARGET);
        if (dedicated != null) return dedicated;
        for (EntityTrack track : project.getTracks()) {
            if (track == null) continue;
            if (track.hasKeyframes(PropertyType.CAMERA_X)
                || track.hasKeyframes(PropertyType.CAMERA_Y)
                || track.hasKeyframes(PropertyType.CAMERA_ZOOM)) {
                return track;
            }
        }
        return createIfMissing ? project.getOrCreateTrack(RUNTIME_CAMERA_TARGET) : null;
    }

    private boolean isRuntimeCameraCarrier(EntityTrack track) {
        if (track == null) return false;
        EntityTrack runtimeTrack = resolveRuntimeCameraTrack(false);
        return runtimeTrack != null && runtimeTrack == track;
    }

    private String currentStorageSelectionName() {
        EntityTrack track = selectedTrack(false);
        if (track != null && track.getEntityName() != null && !track.getEntityName().isBlank()) {
            return track.getEntityName();
        }
        return selectedEntity;
    }

    private static String storageNameForRow(TrackRow row) {
        if (row == null) return null;
        if (row.track() != null && row.track().getEntityName() != null && !row.track().getEntityName().isBlank()) {
            return row.track().getEntityName();
        }
        return row.selectionName();
    }

    private boolean isHoveredRow(String selectionName, EntityTrack track, boolean groupTrack, PropertyType property) {
        if (!Double.isFinite(hoverTimeMs)) return false;
        String storageName = track != null && track.getEntityName() != null && !track.getEntityName().isBlank()
            ? track.getEntityName()
            : selectionName;
        return Objects.equals(hoverRowStorageName, storageName)
            && hoverRowProperty == property
            && hoverRowGroup == groupTrack;
    }

    static boolean isRuntimeCameraTarget(String name) {
        return RUNTIME_CAMERA_TARGET.equals(name);
    }

    private double snapTime(double timeMs) {
        if (!snapEnabled || snapStepMs <= 0) return timeMs;
        return Math.round(timeMs / snapStepMs) * snapStepMs;
    }

    private double clampToTimeline(double timeMs) {
        return Math.max(0.0, Math.min(project.getTotalDurationMs(), timeMs));
    }

    private double clampOrExpandTimeline(double timeMs) {
        timeMs = Math.max(0.0, timeMs);
        if (timeMs > project.getTotalDurationMs()) {
            double padded = Math.ceil(timeMs / 500.0) * 500.0;
            project.setTotalDurationMs(Math.max(padded, timeMs + 500));
        }
        return timeMs;
    }

    private boolean jumpPlayheadToAdjacentKeyframe(boolean forward) {
        List<Double> times = navigationTimes();
        if (times.isEmpty()) return false;
        double playhead = project.getPlayheadMs();
        double epsilon = 0.001;
        Double target = null;
        if (forward) {
            for (double time : times) {
                if (time > playhead + epsilon) {
                    target = time;
                    break;
                }
            }
        } else {
            for (int i = times.size() - 1; i >= 0; i--) {
                double time = times.get(i);
                if (time < playhead - epsilon) {
                    target = time;
                    break;
                }
            }
        }
        if (target == null) return false;
        project.setPlayheadMs(target);
        ensureTimeVisible(target);
        if (onPlayheadChanged != null) onPlayheadChanged.accept(target);
        render();
        return true;
    }

    private List<Double> navigationTimes() {
        List<Double> times = new ArrayList<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                if (ref != null && ref.keyframe() != null) {
                    times.add(ref.keyframe().getTimeMs());
                }
            }
            return normalizeNavigationTimes(times);
        }

        EntityTrack track = selectedTrack(false);
        if (track != null) {
            if (selectedProperty != null && isPropertySupportedForSelection(selectedProperty)) {
                for (Keyframe keyframe : track.getKeyframes(selectedProperty)) {
                    times.add(keyframe.getTimeMs());
                }
            }
            if (times.isEmpty()) {
                for (PropertyType property : editablePropertiesForSelection()) {
                    if (!isPropertySupportedForSelection(property)) continue;
                    for (Keyframe keyframe : track.getKeyframes(property)) {
                        times.add(keyframe.getTimeMs());
                    }
                }
            }
            if (!times.isEmpty()) {
                return normalizeNavigationTimes(times);
            }
        }

        for (EntityTrack projectTrack : project.getTracks()) {
            for (PropertyType property : PropertyType.values()) {
                for (Keyframe keyframe : projectTrack.getKeyframes(property)) {
                    times.add(keyframe.getTimeMs());
                }
            }
        }
        return normalizeNavigationTimes(times);
    }

    private void ensureTimeVisible(double timeMs) {
        double visibleWidth = Math.max(80.0, canvas.getWidth() - LABEL_WIDTH - 20.0);
        double visibleSpanMs = Math.max(40.0, visibleWidth / Math.max(MIN_PIXELS_PER_MS, pixelsPerMs));
        double leftTime = scrollX / pixelsPerMs;
        double rightTime = leftTime + visibleSpanMs;
        double paddingMs = Math.max(40.0, visibleSpanMs * 0.12);
        if (timeMs < leftTime + paddingMs) {
            scrollX = Math.max(0.0, (timeMs - paddingMs) * pixelsPerMs);
        } else if (timeMs > rightTime - paddingMs) {
            scrollX = Math.max(0.0, (timeMs - visibleSpanMs + paddingMs) * pixelsPerMs);
        }
    }

    static List<Double> normalizeNavigationTimes(List<Double> rawTimes) {
        if (rawTimes == null || rawTimes.isEmpty()) return List.of();
        List<Double> normalized = new ArrayList<>();
        for (Double value : rawTimes) {
            if (value == null || !Double.isFinite(value)) continue;
            normalized.add(value);
        }
        if (normalized.isEmpty()) return List.of();
        Collections.sort(normalized);
        List<Double> unique = new ArrayList<>();
        for (double value : normalized) {
            if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1) - value) > 0.001) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    static double[] computeFocusWindow(double minTimeMs, double maxTimeMs, double durationMs) {
        double min = Math.min(minTimeMs, maxTimeMs);
        double max = Math.max(minTimeMs, maxTimeMs);
        double duration = Math.max(100.0, durationMs);
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new double[]{0.0, duration};
        }
        double span = Math.max(0.0, max - min);
        double padding = Math.max(80.0, span * 0.18);
        double start = Math.max(0.0, min - padding);
        double end = Math.min(duration, max + padding);
        if (end - start < 100.0) {
            double center = (min + max) * 0.5;
            start = Math.max(0.0, center - 50.0);
            end = Math.min(duration, start + 100.0);
            start = Math.max(0.0, end - 100.0);
        }
        return new double[]{start, end};
    }

    private static boolean isMajorGridLine(double timeMs, double stepMs) {
        if (stepMs <= 0.0) return false;
        double nearest = Math.rint(timeMs / stepMs);
        return Math.abs(timeMs - nearest * stepMs) < 0.0001;
    }

    private static String abbreviate(String value, int maxChars) {
        if (value == null) return "";
        if (maxChars <= 3) return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars));
        return value.length() <= maxChars ? value : value.substring(0, maxChars - 3) + "...";
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }

    private void notifyEdited() {
        if (onEdited != null) onEdited.run();
    }

    private void notifyTargetSelectionChanged() {
        if (onTargetSelectionChanged != null) {
            onTargetSelectionChanged.accept(selectedEntity, selectedGroup);
        }
    }

    // ---------------------------------------------------------------------
    // Auto-scroll while dragging keyframes near the canvas horizontal edges
    // ---------------------------------------------------------------------

    private void ensureDragAutoScrollRunning() {
        if (dragAutoScrollTimer != null) return;
        dragAutoScrollTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (!draggingKeyframe) {
                    stopDragAutoScroll();
                    return;
                }
                double width = canvas.getWidth();
                double leftEdge = LABEL_WIDTH + AUTO_SCROLL_EDGE_PX;
                double rightEdge = width - AUTO_SCROLL_EDGE_PX;
                double speed = 0.0;
                if (lastDragMouseX < leftEdge) {
                    double t = Math.max(0.0, Math.min(1.0, (leftEdge - lastDragMouseX) / AUTO_SCROLL_EDGE_PX));
                    speed = -AUTO_SCROLL_MAX_SPEED_PX * t;
                } else if (lastDragMouseX > rightEdge) {
                    double t = Math.max(0.0, Math.min(1.0, (lastDragMouseX - rightEdge) / AUTO_SCROLL_EDGE_PX));
                    speed = AUTO_SCROLL_MAX_SPEED_PX * t;
                }
                if (Math.abs(speed) < 0.5) return;
                double prevScroll = scrollX;
                scrollX = Math.max(0.0, scrollX + speed);
                clampScrollOffsets();
                double effectiveDelta = scrollX - prevScroll;
                if (Math.abs(effectiveDelta) < 0.001) return;
                // Apply equivalent time delta to dragged keyframes so they keep up with view
                double timeDelta = effectiveDelta / pixelsPerMs;
                if (!dragStartTimes.isEmpty()) {
                    for (Map.Entry<KeyframeSelectionModel.KeyframeRef, Double> entry : dragStartTimes.entrySet()) {
                        Keyframe moving = entry.getKey().keyframe();
                        double next = clampOrExpandTimeline(snapTime(moving.getTimeMs() + timeDelta));
                        moving.setTimeMs(next);
                    }
                } else if (selectedKeyframe != null) {
                    double next = clampOrExpandTimeline(snapTime(selectedKeyframe.getTimeMs() + timeDelta));
                    selectedKeyframe.setTimeMs(next);
                }
                render();
            }
        };
        dragAutoScrollTimer.start();
    }

    private void stopDragAutoScroll() {
        if (dragAutoScrollTimer != null) {
            dragAutoScrollTimer.stop();
            dragAutoScrollTimer = null;
        }
    }

    // ---------------------------------------------------------------------
    // Right-click context menu
    // ---------------------------------------------------------------------

    private void dismissContextMenu() {
        if (activeContextMenu != null && activeContextMenu.isShowing()) {
            activeContextMenu.hide();
        }
        activeContextMenu = null;
    }

    private void handleContextMenuRequested(ContextMenuEvent e) {
        double x = e.getX();
        double y = e.getY();
        dismissContextMenu();

        ContextMenu menu;
        KeyframeHit hit = findKeyframeAt(x, y);
        if (hit != null) {
            ensureKeyframeIsSelected(hit);
            menu = buildKeyframeContextMenu(hit);
        } else {
            TrackRow row = findRowAt(y);
            if (row != null && row.property() != null) {
                selectedEntity = row.selectionName();
                selectedGroup = row.group();
                selectedProperty = row.property();
                notifyTargetSelectionChanged();
                render();
                menu = buildRowContextMenu(row, x);
            } else {
                menu = buildEmptyContextMenu(x);
            }
        }

        if (menu == null) return;
        activeContextMenu = menu;
        menu.show(canvas, e.getScreenX(), e.getScreenY());
        e.consume();
    }

    private void ensureKeyframeIsSelected(KeyframeHit hit) {
        KeyframeSelectionModel.KeyframeRef ref =
            KeyframeSelectionModel.ref(storageNameForRow(hit.row()), hit.row().property(), hit.keyframe());
        if (!selectionModel.isSelected(ref)) {
            selectionModel.clearSelection();
            selectionModel.select(ref);
        }
        selectedEntity = hit.row().selectionName();
        selectedGroup = hit.row().group();
        selectedProperty = hit.row().property();
        selectedKeyframe = hit.keyframe();
        notifyTargetSelectionChanged();
        notifyKeyframeSelectionChanged();
        render();
    }

    private ContextMenu buildKeyframeContextMenu(KeyframeHit hit) {
        ContextMenu menu = new ContextMenu();
        int count = Math.max(1, selectionModel.getSelectionCount());
        Keyframe primary = hit.keyframe();
        PropertyType property = hit.row().property();

        MenuItem miHeader = new MenuItem(count > 1
            ? count + " keyframes selected"
            : property.getDisplayName() + " @ " + formatTime(primary.getTimeMs()));
        miHeader.setDisable(true);
        menu.getItems().add(miHeader);
        menu.getItems().add(new SeparatorMenuItem());

        MenuItem miEditCurve = new MenuItem("Edit Easing Curve...");
        miEditCurve.setOnAction(ev -> openEasingCurveEditor());
        menu.getItems().add(miEditCurve);
        menu.getItems().add(new SeparatorMenuItem());

        MenuItem miCopy = new MenuItem("Copy");
        miCopy.setOnAction(ev -> { copySelectedKeyframes(); });
        MenuItem miCut = new MenuItem("Cut");
        miCut.setOnAction(ev -> { copySelectedKeyframes(); deleteSelectedKeyframe(); });
        MenuItem miDuplicate = new MenuItem("Duplicate (+snap step)");
        miDuplicate.setOnAction(ev -> { duplicateSelectedKeyframes(snapStepMs); notifyEdited(); });
        MenuItem miCopyEasing = new MenuItem("Copy Easing");
        miCopyEasing.setOnAction(ev -> {
            easingClipboard = primary.getEasingSpec();
            interpolationClipboard = primary.getInterpolation();
        });
        MenuItem miPasteEasing = new MenuItem("Paste Easing");
        miPasteEasing.setDisable(easingClipboard == null);
        miPasteEasing.setOnAction(ev -> {
            applyEasingToSelectionOrPrimary(easingClipboard, interpolationClipboard);
        });
        MenuItem miDelete = new MenuItem("Delete");
        miDelete.setOnAction(ev -> deleteSelectedKeyframe());

        menu.getItems().addAll(miCopy, miCut, miDuplicate,
            new SeparatorMenuItem(),
            miCopyEasing, miPasteEasing,
            new SeparatorMenuItem());

        Menu easingPresets = new Menu("Set Easing");
        easingPresets.getItems().addAll(
            easingMenuItem("Linear", Easing.Type.LINEAR),
            easingMenuItem("Ease In (Quad)", Easing.Type.EASE_IN_QUAD),
            easingMenuItem("Ease Out (Quad)", Easing.Type.EASE_OUT_QUAD),
            easingMenuItem("Ease In-Out (Quad)", Easing.Type.EASE_IN_OUT_QUAD),
            new SeparatorMenuItem(),
            easingMenuItem("Ease In (Cubic)", Easing.Type.EASE_IN_CUBIC),
            easingMenuItem("Ease Out (Cubic)", Easing.Type.EASE_OUT_CUBIC),
            easingMenuItem("Ease In-Out (Cubic)", Easing.Type.EASE_IN_OUT_CUBIC)
        );
        Menu interpMenu = new Menu("Interpolation");
        for (Easing.Interpolation interp : Easing.Interpolation.values()) {
            MenuItem mi = new MenuItem(interp.name());
            mi.setOnAction(ev -> applyInterpolationToSelectionOrPrimary(interp));
            interpMenu.getItems().add(mi);
        }
        menu.getItems().addAll(easingPresets, interpMenu, new SeparatorMenuItem());

        MenuItem miSnapToPlayhead = new MenuItem("Move to Playhead");
        miSnapToPlayhead.setOnAction(ev -> moveSelectionToTime(project.getPlayheadMs()));
        MenuItem miPlayheadToHere = new MenuItem("Playhead to This Keyframe");
        miPlayheadToHere.setOnAction(ev -> {
            project.setPlayheadMs(primary.getTimeMs());
            if (onPlayheadChanged != null) onPlayheadChanged.accept(primary.getTimeMs());
            render();
        });
        MenuItem miSelectColumn = new MenuItem("Select Column at Time");
        miSelectColumn.setOnAction(ev -> selectKeyframesAtTime(primary.getTimeMs(), 0.5));
        MenuItem miSelectTrack = new MenuItem("Select All on Track");
        miSelectTrack.setOnAction(ev -> selectAllKeyframesOnTrack(hit.row()));
        menu.getItems().addAll(miSnapToPlayhead, miPlayheadToHere, miSelectColumn, miSelectTrack,
            new SeparatorMenuItem(), miDelete);
        return menu;
    }

    private MenuItem easingMenuItem(String label, Easing.Type type) {
        MenuItem mi = new MenuItem(label);
        mi.setOnAction(ev -> applyEasingToSelectionOrPrimary(EasingSpec.of(type), null));
        return mi;
    }

    private ContextMenu buildRowContextMenu(TrackRow row, double mouseX) {
        ContextMenu menu = new ContextMenu();
        double rowTime = clampToTimeline((mouseX - LABEL_WIDTH + scrollX) / pixelsPerMs);
        MenuItem header = new MenuItem(row.displayLabel() + (row.property() != null ? "  /  " + row.property().getDisplayName() : ""));
        header.setDisable(true);
        menu.getItems().add(header);
        menu.getItems().add(new SeparatorMenuItem());

        MenuItem miAddHere = new MenuItem(String.format(Locale.ROOT, "Add Keyframe Here (%s)", formatTime(rowTime)));
        miAddHere.setOnAction(ev -> addKeyframeAtTime(rowTime));
        MenuItem miAddPlayhead = new MenuItem("Add Keyframe at Playhead");
        miAddPlayhead.setOnAction(ev -> addKeyframeAtPlayhead());
        MenuItem miPaste = new MenuItem("Paste at Playhead");
        miPaste.setDisable(copiedKeyframes.isEmpty());
        miPaste.setOnAction(ev -> pasteCopiedKeyframesAtPlayhead());
        MenuItem miSelectAll = new MenuItem("Select All on Track");
        miSelectAll.setOnAction(ev -> selectAllKeyframesOnTrack(row));
        MenuItem miClearTrack = new MenuItem("Clear All on Track");
        miClearTrack.setOnAction(ev -> clearAllKeyframesOnTrack(row));
        menu.getItems().addAll(miAddHere, miAddPlayhead, miPaste, new SeparatorMenuItem(), miSelectAll, miClearTrack);
        return menu;
    }

    private ContextMenu buildEmptyContextMenu(double mouseX) {
        ContextMenu menu = new ContextMenu();
        MenuItem miPaste = new MenuItem("Paste at Playhead");
        miPaste.setDisable(copiedKeyframes.isEmpty());
        miPaste.setOnAction(ev -> pasteCopiedKeyframesAtPlayhead());
        MenuItem miZoomFit = new MenuItem("Zoom to Fit");
        miZoomFit.setOnAction(ev -> zoomToFit());
        MenuItem miZoomSel = new MenuItem("Zoom to Selection");
        miZoomSel.setOnAction(ev -> zoomToSelection());
        menu.getItems().addAll(miPaste, new SeparatorMenuItem(), miZoomFit, miZoomSel);
        return menu;
    }

    // ---------------------------------------------------------------------
    // Public/internal helpers used by context menu and external shortcuts
    // ---------------------------------------------------------------------

    public void openEasingCurveEditor() {
        Keyframe primary = selectedKeyframe != null ? selectedKeyframe
            : selectionModel.getSelectedOrdered().stream()
                .map(KeyframeSelectionModel.KeyframeRef::keyframe)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        if (primary == null) return;
        String title = (selectedEntity != null ? selectedEntity : "?")
            + " / " + (selectedProperty != null ? selectedProperty.getDisplayName() : "?")
            + "  " + formatTime(primary.getTimeMs());
        if (easingCurveDialog != null && easingCurveDialog.isShowing()) {
            easingCurveDialog.close();
        }
        Window owner = canvas.getScene() != null ? canvas.getScene().getWindow() : null;
        easingCurveDialog = new EasingCurveDialog(owner, primary.getEasingSpec(),
            primary.getInterpolation(), title,
            (spec, interp) -> applyEasingToSelectionOrPrimary(spec, interp));
        easingCurveDialog.show();
    }

    public void applyEasingToSelectionOrPrimary(EasingSpec spec, Easing.Interpolation interpolation) {
        if (spec == null && interpolation == null) return;
        List<Keyframe> targets = collectSelectionTargets();
        if (targets.isEmpty()) return;
        List<PuppeteerCommand> cmds = new ArrayList<>();
        for (Keyframe kf : targets) {
            EasingSpec oldSpec = kf.getEasingSpec();
            Easing.Interpolation oldInterp = kf.getInterpolation();
            EasingSpec newSpec = spec != null ? spec : oldSpec;
            Easing.Interpolation newInterp = interpolation != null ? interpolation : oldInterp;
            if (Objects.equals(oldSpec, newSpec) && oldInterp == newInterp) continue;
            cmds.add(new PuppeteerCommand("Change easing",
                () -> { kf.setEasingSpec(newSpec); kf.setInterpolation(newInterp); },
                () -> { kf.setEasingSpec(oldSpec); kf.setInterpolation(oldInterp); }
            ));
        }
        if (cmds.isEmpty()) return;
        if (commandStack != null) {
            commandStack.execute(PuppeteerCommand.composite("Apply easing", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        notifyEdited();
        render();
    }

    public void applyInterpolationToSelectionOrPrimary(Easing.Interpolation interpolation) {
        applyEasingToSelectionOrPrimary(null, interpolation);
    }

    private List<Keyframe> collectSelectionTargets() {
        List<Keyframe> targets = new ArrayList<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                if (ref != null && ref.keyframe() != null) targets.add(ref.keyframe());
            }
        } else if (selectedKeyframe != null) {
            targets.add(selectedKeyframe);
        }
        return targets;
    }

    private void moveSelectionToTime(double timeMs) {
        List<Keyframe> targets = collectSelectionTargets();
        if (targets.isEmpty()) return;
        List<PuppeteerCommand> cmds = new ArrayList<>();
        double snapped = clampOrExpandTimeline(snapTime(timeMs));
        for (Keyframe kf : targets) {
            double oldTime = kf.getTimeMs();
            if (Math.abs(oldTime - snapped) < 0.001) continue;
            cmds.add(PuppeteerCommand.moveKeyframe(kf, oldTime, snapped));
        }
        if (cmds.isEmpty()) return;
        if (commandStack != null) {
            commandStack.execute(PuppeteerCommand.composite("Move keyframes to time", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        sortMovedSelectionTargets();
        notifyEdited();
        render();
    }

    private void sortMovedSelectionTargets() {
        Set<String> sortedChannels = new HashSet<>();
        if (selectionModel.hasSelection()) {
            for (KeyframeSelectionModel.KeyframeRef ref : selectionModel.getSelectedOrdered()) {
                if (ref == null || ref.property() == null) continue;
                EntityTrack track = trackForStorageName(ref.entityName());
                if (track == null) continue;
                String channelKey = ref.entityName() + "\u0000" + ref.property().name();
                if (sortedChannels.add(channelKey)) {
                    track.sortKeyframes(ref.property());
                }
            }
        }
        if (sortedChannels.isEmpty()) {
            EntityTrack track = selectedTrack(false);
            if (track != null && selectedProperty != null) track.sortKeyframes(selectedProperty);
        }
    }

    private EntityTrack trackForStorageName(String name) {
        if (name == null || name.isBlank()) return null;
        if (isRuntimeCameraTarget(name)) return resolveRuntimeCameraTrack(false);
        EntityTrack track = project.getTrack(name);
        if (track != null) return track;
        EntityGroup group = project.getGroup(name);
        return group != null ? group.getGroupTrack() : null;
    }

    public void selectKeyframesAtTime(double timeMs, double toleranceMs) {
        selectionModel.clearSelection();
        for (TrackRow row : buildVisibleRows()) {
            if (row.property() == null || row.track() == null) continue;
            for (Keyframe kf : row.track().getKeyframes(row.property())) {
                if (Math.abs(kf.getTimeMs() - timeMs) <= toleranceMs) {
                    selectionModel.select(KeyframeSelectionModel.ref(storageNameForRow(row), row.property(), kf));
                }
            }
        }
        selectedKeyframe = selectionModel.getSelectedOrdered().stream()
            .reduce((a, b) -> b)
            .map(KeyframeSelectionModel.KeyframeRef::keyframe)
            .orElse(null);
        notifyKeyframeSelectionChanged();
        render();
    }

    void selectAllKeyframesOnTrack(TrackRow row) {
        if (row == null || row.track() == null || row.property() == null) return;
        selectionModel.clearSelection();
        String storage = storageNameForRow(row);
        for (Keyframe kf : row.track().getKeyframes(row.property())) {
            selectionModel.select(KeyframeSelectionModel.ref(storage, row.property(), kf));
        }
        selectedKeyframe = selectionModel.getSelectedOrdered().stream()
            .reduce((a, b) -> b)
            .map(KeyframeSelectionModel.KeyframeRef::keyframe)
            .orElse(null);
        notifyKeyframeSelectionChanged();
        render();
    }

    void clearAllKeyframesOnTrack(TrackRow row) {
        if (row == null || row.track() == null || row.property() == null) return;
        List<Keyframe> kfs = new ArrayList<>(row.track().getKeyframes(row.property()));
        if (kfs.isEmpty()) return;
        List<PuppeteerCommand> cmds = new ArrayList<>();
        for (Keyframe kf : kfs) {
            cmds.add(PuppeteerCommand.removeKeyframe(row.track(), row.property(), kf));
        }
        if (commandStack != null) {
            commandStack.execute(PuppeteerCommand.composite("Clear track keyframes", cmds));
        } else {
            for (PuppeteerCommand cmd : cmds) cmd.execute();
        }
        clearKeyframeSelection();
        notifyEdited();
        render();
    }
}
