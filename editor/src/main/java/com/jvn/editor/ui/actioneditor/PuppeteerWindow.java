package com.jvn.editor.ui.actioneditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRegistry;
import com.jvn.editor.ui.EditorTheme;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class PuppeteerWindow extends Stage {
    private final AnimationProject project;
    private JesScene2D scene;

    private final EntitySelector entitySelector;
    private final AssetPickerPanel assetPicker;
    private final TimelinePanel timelinePanel;
    private final KeyframeEditor keyframeEditor;
    private final AnimationPreview animationPreview;
    private final CodePreviewPane codePreview;

    private final Button btnPlay;
    private final Button btnPause;
    private final Button btnStop;
    private final Button btnRewind;
    private final TextField tfDuration;
    private final ToggleButton cbLoop;
    private final Label lblTime;
    private ComboBox<PropertyType> cbProperty;
    private ToggleButton cbSnap;
    private TextField tfSnapMs;
    private ToggleButton cbOrbitTool;
    private ToggleButton cbOrbitAlign;

    private AnimationTimer playbackTimer;
    private long lastNanos = 0;
    private double playbackSpeed = 1.0;
    private boolean autoKeyEnabled = false;

    private final PuppeteerCommand.Stack commandStack = new PuppeteerCommand.Stack();
    private final KeyframeSelectionModel selectionModel = new KeyframeSelectionModel();
    private Consumer<String> onCopyCode;
    private final TextField tfTimelineName;
    private Label statusBar;
    private Label viewportInfoLabel;
    private boolean dirty = false;
    private boolean compactExport = false;
    private boolean previewStaged = false;
    private boolean dirtyBeforePreviewStage = false;
    private AnimationProject previewBaselineProject;
    private DragInteractionState activeMoveInteraction;

    private static final double MOVE_INTERACTION_EPSILON = 0.01;

    private record DragInteractionState(
        String entityName,
        double timeMs,
        boolean hadX,
        double oldX,
        boolean hadY,
        double oldY,
        double startX,
        double startY
    ) {}

    public PuppeteerWindow() {
        this(new AnimationProject());
    }

    public PuppeteerWindow(AnimationProject project) {
        this.project = project != null ? project : new AnimationProject();

        setTitle("Puppeteer - " + this.project.getName());
        setWidth(1400);
        setHeight(900);

        entitySelector = new EntitySelector();
        timelinePanel = new TimelinePanel(this.project);
        keyframeEditor = new KeyframeEditor();
        keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
        animationPreview = new AnimationPreview();
        animationPreview.setProject(this.project);
        animationPreview.setOrbitAnchors(this.project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(this.project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(this.project.getOrbitAnchorSourceOffsetsView());
        animationPreview.setOnOrbitAnchorChanged((entityName, anchor) -> {
            if (entityName == null || anchor == null || anchor.length < 2) return;
            this.project.setOrbitAnchor(entityName, anchor[0], anchor[1]);
        });
        animationPreview.setOnOrbitAnchorSourceChanged(this.project::setOrbitAnchorSource);
        animationPreview.setOnOrbitAnchorSourceOffsetChanged((entityName, offset) -> {
            if (entityName == null || entityName.isBlank()) return;
            if (offset == null || offset.length < 2) {
                this.project.clearOrbitAnchorSourceOffset(entityName);
                return;
            }
            if (!Double.isFinite(offset[0]) || !Double.isFinite(offset[1])) return;
            this.project.setOrbitAnchorSourceOffset(entityName, offset[0], offset[1]);
        });
        animationPreview.setOnOrbitAnchorRemoved(this.project::removeOrbitAnchor);
        animationPreview.setOnCameraStateChanged(state -> {
            if (state != null && state.length >= 3) {
                keyframeEditor.setCameraState(state[0], state[1], state[2]);
            }
        });
        codePreview = new CodePreviewPane();
        keyframeEditor.setCameraState(animationPreview.getCamera().getX(), animationPreview.getCamera().getY(), animationPreview.getCamera().getZoom());

        timelinePanel.setOnTargetSelectionChanged((name, isGroup) -> {
            keyframeEditor.setEntityName(selectionLabel(name, isGroup));
            if (isGroup) {
                entitySelector.selectGroup(name);
                animationPreview.clearSelection();
            } else {
                entitySelector.selectEntity(name);
                animationPreview.selectEntity(name);
            }
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
        });

        entitySelector.setOnSelectionChanged((name, isGroup) -> {
            timelinePanel.setSelectedTarget(name, isGroup);
        });

        entitySelector.setOnCreateGroup(groupName -> {
            this.project.getOrCreateGroup(groupName);
            entitySelector.refresh(this.project);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnAddSelectionToGroup((name, selectionIsGroup, groupName) -> {
            if (selectionIsGroup) {
                this.project.addGroupToGroup(name, groupName);
            } else {
                this.project.addEntityToGroup(name, groupName);
            }
            entitySelector.refresh(this.project);
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnEntityLayerDelta((entityName, delta) -> {
            EntityTrack track = this.project.getTrack(entityName);
            if (track == null) return;
            track.setLayerOrder(track.getLayerOrder() + delta);
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        entitySelector.setOnGroupLayerDelta((groupName, delta) -> {
            EntityGroup group = this.project.getGroup(groupName);
            if (group == null) return;
            group.setLayerOrder(group.getLayerOrder() + delta);
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        timelinePanel.setOnKeyframeSelected(kf -> {
            keyframeEditor.setKeyframe(kf, timelinePanel.getSelectedProperty());
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
        });

        timelinePanel.setOnPlayheadChanged(time -> {
            this.project.setPlayheadMs(time);
            updateTimeLabel();
            updatePreview();
        });
        timelinePanel.setOnEdited(this::refreshExportPreviewAndMarkDirty);

        keyframeEditor.setOnKeyframeChanged(() -> {
            PropertyType property = keyframeEditor.getCurrentProperty();
            if (property != null) {
                EntityTrack track = selectedTrackForEditing(false);
                if (track != null) track.sortKeyframes(property);
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnDeleteRequested(() -> {
            Keyframe kf = keyframeEditor.getCurrentKeyframe();
            PropertyType prop = keyframeEditor.getCurrentProperty();
            if (kf != null && prop != null && timelinePanel.getSelectedProperty() != null) {
                EntityTrack track = selectedTrackForEditing(false);
                if (track != null) track.removeKeyframe(prop, kf);
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntitySelected(name -> {
            timelinePanel.setSelectedTarget(name, false);
        });

        animationPreview.setOnEntityMoved((name, pos) -> {
            if (name == null || pos == null || pos.length < 2) return;
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            boolean liveDragSource = false;
            if (activeMoveInteraction != null && name.equals(activeMoveInteraction.entityName())) {
                time = activeMoveInteraction.timeMs();
                liveDragSource = true;
            }
            double previousX = valueAtTimeOrFallback(track, PropertyType.X, time, pos[0]);
            double previousY = valueAtTimeOrFallback(track, PropertyType.Y, time, pos[1]);
            track.upsertKeyframe(PropertyType.X, new Keyframe(time, pos[0]));
            track.upsertKeyframe(PropertyType.Y, new Keyframe(time, pos[1]));
            if (liveDragSource) {
                double dx = pos[0] - previousX;
                double dy = pos[1] - previousY;
                if (Math.abs(dx) > MOVE_INTERACTION_EPSILON || Math.abs(dy) > MOVE_INTERACTION_EPSILON) {
                    applyAnchorFollowerDelta(name, time, dx, dy, new LinkedHashSet<>());
                }
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityMoveInteractionStarted((name, pos) -> {
            if (name == null || name.isBlank() || pos == null || pos.length < 2) {
                activeMoveInteraction = null;
                return;
            }
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) {
                activeMoveInteraction = null;
                return;
            }
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            Keyframe xKey = track.findKeyframeAt(PropertyType.X, time);
            Keyframe yKey = track.findKeyframeAt(PropertyType.Y, time);
            activeMoveInteraction = new DragInteractionState(
                name,
                time,
                xKey != null,
                xKey != null ? xKey.getValue() : pos[0],
                yKey != null,
                yKey != null ? yKey.getValue() : pos[1],
                pos[0],
                pos[1]
            );
        });

        animationPreview.setOnEntityMoveInteractionFinished((name, pos) -> {
            DragInteractionState interaction = activeMoveInteraction;
            activeMoveInteraction = null;
            if (interaction == null || name == null || pos == null || pos.length < 2) return;
            if (!name.equals(interaction.entityName())) return;
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) return;

            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = interaction.timeMs();
            double endX = pos[0];
            double endY = pos[1];
            boolean moved = Math.abs(endX - interaction.startX()) > MOVE_INTERACTION_EPSILON
                || Math.abs(endY - interaction.startY()) > MOVE_INTERACTION_EPSILON;

            if (!moved) {
                if (!interaction.hadX()) {
                    Keyframe xKey = track.findKeyframeAt(PropertyType.X, time);
                    if (xKey != null) track.removeKeyframe(PropertyType.X, xKey);
                } else {
                    track.upsertKeyframe(PropertyType.X, new Keyframe(time, interaction.oldX()));
                }
                if (!interaction.hadY()) {
                    Keyframe yKey = track.findKeyframeAt(PropertyType.Y, time);
                    if (yKey != null) track.removeKeyframe(PropertyType.Y, yKey);
                } else {
                    track.upsertKeyframe(PropertyType.Y, new Keyframe(time, interaction.oldY()));
                }
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }

            commandStack.execute(PuppeteerCommand.applyPositionAtTime(
                track,
                time,
                interaction.hadX(),
                interaction.oldX(),
                interaction.hadY(),
                interaction.oldY(),
                endX,
                endY
            ));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityPivotChanged((name, pivot) -> {
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, pivot[0]));
            commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, pivot[1]));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnPivotPresetApplied((px, py) -> {
            String name = timelinePanel.getSelectedEntity();
            if (name == null || name.isBlank()) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_X, time, px));
            commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.PIVOT_Y, time, py));
            timelinePanel.refresh();
            updatePreview();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityRotationChanged((name, rotationDeg) -> {
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.ROTATION, time, rotationDeg));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        codePreview.setOnCopy(() -> {
            String code = codePreview.getCode();
            copyToClipboard(code);
            if (onCopyCode != null) onCopyCode.accept(code);
        });

        codePreview.setOnRegenerate(() -> {
            if (previewStaged) {
                discardStagedPreview();
                return;
            }
            refreshExportPreview();
        });

        // --- Transport controls ---
        btnRewind = makeToolbarIconButton("icon-puppeteer-rewind", "Rewind (Home)");
        btnPlay = makeToolbarIconButton("icon-puppeteer-play", "Play (Space)");
        btnPause = makeToolbarIconButton("icon-puppeteer-pause", "Pause (Space)");
        btnStop = makeToolbarIconButton("icon-puppeteer-stop", "Stop");

        btnPlay.setOnAction(e -> play());
        btnPause.setOnAction(e -> pause());
        btnStop.setOnAction(e -> stop());
        btnRewind.setOnAction(e -> rewind());

        lblTime = new Label("0 ms");
        lblTime.setStyle("-fx-text-fill: #e6e6e6; -fx-font-size: 12px; -fx-font-weight: bold; -fx-min-width: 72; -fx-alignment: center;");

        HBox transportBox = new HBox(4, btnRewind, btnPlay, btnPause, btnStop, makeSpacer(6), lblTime);
        transportBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Duration controls ---
        tfDuration = new TextField(String.valueOf((int) this.project.getTotalDurationMs()));
        tfDuration.setPrefWidth(64);
        tfDuration.setStyle(STYLE_TEXT_FIELD);
        tfDuration.setOnAction(e -> {
            try {
                double dur = Double.parseDouble(tfDuration.getText());
                this.project.setTotalDurationMs(dur);
                keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            } catch (NumberFormatException ignored) {}
        });

        Button btnFitDuration = makeToolbarIconButton("icon-timeline-fit", "Fit duration to content");
        btnFitDuration.setOnAction(e -> {
            this.project.fitDurationToContent();
            tfDuration.setText(String.valueOf((int) this.project.getTotalDurationMs()));
            keyframeEditor.setTimelineDurationMs(this.project.getTotalDurationMs());
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        cbLoop = makeToolbarIconToggle("icon-puppeteer-loop", "Loop timeline playback");
        cbLoop.setSelected(this.project.isLooping());
        cbLoop.setOnAction(e -> {
            this.project.setLooping(cbLoop.isSelected());
            refreshExportPreviewAndMarkDirty();
        });

        Button btnLoopIn = makeToolbarIconButton("icon-puppeteer-loop", "Set loop IN at playhead");
        btnLoopIn.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #58d68d; -fx-background-radius: 4; " +
            "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 2 6; -fx-font-size: 9px; -fx-cursor: hand;");
        btnLoopIn.setText("In");
        btnLoopIn.setContentDisplay(ContentDisplay.TEXT_ONLY);
        btnLoopIn.setMinSize(28, 24);
        btnLoopIn.setPrefSize(28, 24);
        btnLoopIn.setMaxSize(28, 24);
        btnLoopIn.setOnAction(e -> {
            double inMs = project.getPlayheadMs();
            double outMs = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();
            if (inMs < outMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopOut = makeToolbarIconButton("icon-puppeteer-loop", "Set loop OUT at playhead");
        btnLoopOut.setStyle(btnLoopIn.getStyle());
        btnLoopOut.setText("Out");
        btnLoopOut.setContentDisplay(ContentDisplay.TEXT_ONLY);
        btnLoopOut.setMinSize(28, 24);
        btnLoopOut.setPrefSize(28, 24);
        btnLoopOut.setMaxSize(28, 24);
        btnLoopOut.setOnAction(e -> {
            double outMs = project.getPlayheadMs();
            double inMs = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
            if (outMs > inMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnLoopClear = makeToolbarIconButton("icon-puppeteer-clear-anchor", "Clear loop region");
        btnLoopClear.setMinSize(24, 24);
        btnLoopClear.setPrefSize(24, 24);
        btnLoopClear.setMaxSize(24, 24);
        btnLoopClear.setOnAction(e -> {
            project.clearLoopRegion();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        tfDuration.setTooltip(new Tooltip("Timeline duration (ms)"));
        HBox durationBox = new HBox(4, tfDuration, btnFitDuration, cbLoop, btnLoopIn, btnLoopOut, btnLoopClear);
        durationBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Presets ---
        MenuButton presetMenu = buildPresetMenu();
        configureToolbarIconMenuButton(presetMenu, "icon-puppeteer-presets", "Apply animation preset to selected entity");
        presetMenu.setTooltip(new Tooltip("Apply animation preset to selected entity"));

        // --- Property target + snapping ---
        cbProperty = new ComboBox<>();
        cbProperty.getItems().addAll(PropertyType.values());
        cbProperty.setValue(PropertyType.X);
        cbProperty.setStyle(STYLE_TEXT_FIELD);
        cbProperty.setPrefWidth(130);
        cbProperty.setTooltip(new Tooltip("Active property track for add-keyframe and keyboard nudging"));
        cbProperty.setOnAction(e -> {
            timelinePanel.setSelectedProperty(cbProperty.getValue());
            PropertyType effective = timelinePanel.getSelectedProperty();
            if (effective != null && cbProperty.getValue() != effective) {
                cbProperty.setValue(effective);
            }
        });
        timelinePanel.setSelectedProperty(PropertyType.X);

        HBox propertyBox = new HBox(4, cbProperty);
        propertyBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Button btnCopyKeyframes = makeToolbarIconButton("icon-timeline-copy", "Copy selected keyframes (Ctrl/Cmd+Alt+C)");
        btnCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        Button btnPasteKeyframes = makeToolbarIconButton("icon-puppeteer-paste", "Paste keyframes at playhead (Ctrl/Cmd+Alt+V)");
        btnPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        Button btnDuplicateKeyframes = makeToolbarIconButton("icon-puppeteer-duplicate", "Duplicate selected keyframes by snap step (Ctrl/Cmd+Alt+D)");
        btnDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        Button btnSaveClip = makeToolbarIconButton("icon-puppeteer-save-clip", "Save selection as reusable clip");
        btnSaveClip.setOnAction(e -> saveSelectionAsClip());
        Button btnLoadClip = makeToolbarIconButton("icon-puppeteer-load-clip", "Load and apply a saved clip at playhead");
        btnLoadClip.setOnAction(e -> loadAndApplyClip());

        MenuButton slotMenu = new MenuButton("Slot");
        slotMenu.setStyle(STYLE_BTN_DARK);
        slotMenu.setTooltip(new Tooltip("Place selected entity at a VN character slot"));
        for (VnSlotHelper.Slot slot : VnSlotHelper.Slot.values()) {
            MenuItem mi = new MenuItem(slot.name().replace('_', ' '));
            mi.setOnAction(e -> placeEntityAtSlot(slot));
            slotMenu.getItems().add(mi);
        }

        Button btnBatchKeyframe = makeToolbarIconButton("icon-puppeteer-snap", "Add keyframe for ALL entities at playhead (batch)");
        btnBatchKeyframe.setOnAction(e -> {
            PropertyType prop = cbProperty.getValue();
            if (prop == null) prop = PropertyType.X;
            timelinePanel.addKeyframeForAllEntities(project.getPlayheadMs(), prop);
            refreshExportPreviewAndMarkDirty();
        });

        Button btnZoomFit = makeToolbarIconButton("icon-timeline-fit", "Zoom timeline to fit content");
        btnZoomFit.setOnAction(e -> timelinePanel.zoomToFit());

        ToggleButton cbCompactExport = makeToolbarIconToggle("icon-puppeteer-save-clip", "Use compact export format");
        cbCompactExport.setSelected(false);
        cbCompactExport.setOnAction(e -> {
            compactExport = cbCompactExport.isSelected();
            refreshExportPreview();
        });

        HBox keyframeOpsBox = new HBox(4, btnCopyKeyframes, btnPasteKeyframes, btnDuplicateKeyframes,
            btnBatchKeyframe, btnSaveClip, btnLoadClip, slotMenu, btnZoomFit, cbCompactExport);
        keyframeOpsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        cbSnap = makeToolbarIconToggle("icon-puppeteer-snap", "Enable snapping");
        cbSnap.setSelected(timelinePanel.isSnapEnabled());
        cbSnap.setOnAction(e -> timelinePanel.setSnapEnabled(cbSnap.isSelected()));

        tfSnapMs = new TextField("50");
        tfSnapMs.setPrefWidth(56);
        tfSnapMs.setStyle(STYLE_TEXT_FIELD);
        tfSnapMs.setTooltip(new Tooltip("Snap step in milliseconds"));
        tfSnapMs.setOnAction(e -> applySnapStepFromField());
        tfSnapMs.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) applySnapStepFromField();
        });
        tfSnapMs.setTooltip(new Tooltip("Snap step in milliseconds"));

        HBox snapBox = new HBox(4, cbSnap, tfSnapMs);
        snapBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Playback speed ---
        ComboBox<String> cbSpeed = new ComboBox<>();
        cbSpeed.getItems().addAll("0.25x", "0.5x", "1x", "2x", "4x");
        cbSpeed.setValue("1x");
        cbSpeed.setStyle(STYLE_TEXT_FIELD);
        cbSpeed.setPrefWidth(64);
        cbSpeed.setTooltip(new Tooltip("Playback speed"));
        cbSpeed.setOnAction(e -> {
            String val = cbSpeed.getValue();
            if (val != null) {
                try { playbackSpeed = Double.parseDouble(val.replace("x", "")); }
                catch (NumberFormatException ignored) { playbackSpeed = 1.0; }
            }
        });

        ComboBox<String> cbWheelMode = new ComboBox<>();
        cbWheelMode.getItems().addAll("Wheel: View", "Wheel: Camera");
        cbWheelMode.setValue("Wheel: View");
        cbWheelMode.setStyle(STYLE_TEXT_FIELD);
        cbWheelMode.setPrefWidth(118);
        cbWheelMode.setTooltip(new Tooltip("Choose what mouse wheel controls in preview"));
        animationPreview.setScrollZoomMode(AnimationPreview.ScrollZoomMode.VIEW);
        cbWheelMode.setOnAction(e -> {
            String mode = cbWheelMode.getValue();
            if ("Wheel: Camera".equals(mode)) {
                animationPreview.setScrollZoomMode(AnimationPreview.ScrollZoomMode.CAMERA);
            } else {
                animationPreview.setScrollZoomMode(AnimationPreview.ScrollZoomMode.VIEW);
            }
        });

        // --- Auto-key toggle ---
        ToggleButton cbAutoKey = makeToolbarIconToggle("icon-puppeteer-snap", "Auto-key: automatically insert keyframe on drag");
        cbAutoKey.setSelected(false);
        cbAutoKey.setOnAction(e -> autoKeyEnabled = cbAutoKey.isSelected());
        Label lblAutoKey = new Label("Auto");
        lblAutoKey.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 10px;");

        // --- Snap-to-grid / snap-to-entity toggles ---
        ToggleButton cbSnapGrid = makeToolbarIconToggle("icon-puppeteer-snap", "Snap entities to grid when dragging");
        cbSnapGrid.setSelected(false);
        cbSnapGrid.setOnAction(e -> animationPreview.setSnapToGridEnabled(cbSnapGrid.isSelected()));
        ToggleButton cbSnapEntity = makeToolbarIconToggle("icon-puppeteer-align-rotation", "Snap to nearby entity positions");
        cbSnapEntity.setSelected(false);
        cbSnapEntity.setOnAction(e -> animationPreview.setSnapToEntityEnabled(cbSnapEntity.isSelected()));

        HBox autoKeyBox = new HBox(4, cbAutoKey, lblAutoKey);
        autoKeyBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox previewSnapBox = new HBox(4, cbSnapGrid, cbSnapEntity, cbSpeed, cbWheelMode);
        previewSnapBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        cbOrbitTool = makeToolbarIconToggle("icon-puppeteer-orbit", "Enable orbit-anchor tool. Shift+click preview to place anchor. Alt+Shift+click another entity to link the anchor at the exact cursor point (joint/nail).");
        cbOrbitTool.setSelected(animationPreview.isOrbitToolEnabled());
        cbOrbitTool.setOnAction(e -> animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected()));

        cbOrbitAlign = makeToolbarIconToggle("icon-puppeteer-align-rotation", "When orbiting, update entity rotation to face outward.");
        cbOrbitAlign.setSelected(animationPreview.isOrbitAlignRotation());
        cbOrbitAlign.setOnAction(e -> animationPreview.setOrbitAlignRotation(cbOrbitAlign.isSelected()));

        Button btnClearAnchor = makeToolbarIconButton("icon-puppeteer-clear-anchor", "Clear orbit anchor for selected entity");
        btnClearAnchor.setOnAction(e -> {
            animationPreview.clearOrbitAnchorForSelectedEntity();
            updatePreview();
        });

        HBox orbitBox = new HBox(4, cbOrbitTool, cbOrbitAlign, btnClearAnchor);
        orbitBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Help button ---
        Button btnHelp = makeToolbarIconButton("icon-puppeteer-presets", "Show keyboard shortcuts");
        btnHelp.setOnAction(e -> showShortcutsOverlay());

        // --- Audio cues ---
        Button btnAddCue = makeToolbarIconButton("icon-puppeteer-audio-add", "Add audio cue at playhead");
        btnAddCue.setOnAction(e -> showAddAudioCueDialog());
        Button btnClearCues = makeToolbarIconButton("icon-puppeteer-audio-clear", "Remove all timeline audio cues");
        btnClearCues.setOnAction(e -> {
            if (project.getAudioCues().isEmpty()) return;
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            EditorTheme.apply(a);
            a.setTitle("Clear Audio Cues");
            a.setHeaderText("Remove all audio cues from this animation?");
            a.setContentText("This cannot be undone from the cue panel.");
            a.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    project.clearAudioCues();
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                }
            });
        });
        HBox cueBox = new HBox(4, btnAddCue, btnClearCues);
        cueBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Timeline name + Register ---
        tfTimelineName = new TextField("my_animation");
        tfTimelineName.setPrefWidth(110);
        tfTimelineName.setPromptText("timeline_name");
        tfTimelineName.setStyle(STYLE_TEXT_FIELD);
        tfTimelineName.setTooltip(new Tooltip("Name for @external jes_timeline"));

        Button btnRegister = makeToolbarSuccessIconButton("icon-puppeteer-register", "Register timeline for VNS interop");
        btnRegister.setOnAction(e -> registerTimeline());

        HBox nameBox = new HBox(4, tfTimelineName, btnRegister);
        nameBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- Apply Code to Model (text-first round-trip) ---
        codePreview.setOnApplyToModel(() -> {
            stagePreviewFromCode();
        });
        codePreview.setOnCommitPreview(this::commitStagedPreview);
        codePreview.setOnDiscardPreview(this::discardStagedPreview);

        // --- Assemble toolbar ---
        HBox toolbar = new HBox(6,
            transportBox,
            makeVSep(),
            durationBox,
            makeVSep(),
            presetMenu,
            makeVSep(),
            propertyBox,
            makeVSep(),
            keyframeOpsBox,
            makeVSep(),
            snapBox,
            makeVSep(),
            autoKeyBox,
            makeVSep(),
            previewSnapBox,
            makeVSep(),
            orbitBox,
            makeVSep(),
            cueBox,
            makeVSep(),
            nameBox,
            makeVSep(),
            btnHelp
        );
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(6, 10, 6, 10));
        toolbar.setStyle("-fx-background-color: #0a0a0a; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;");

        assetPicker = new AssetPickerPanel();
        assetPicker.setOnAddToScene((path, name) -> addAssetToScene(path, name));

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab assetsTab = new Tab("Assets", assetPicker);
        assetsTab.setClosable(false);
        TabPane leftTabs = new TabPane(entitiesTab, assetsTab);
        leftTabs.setTabMinWidth(60);
        leftTabs.setStyle("-fx-background-color: #1a1a1a;");

        SplitPane leftPane = new SplitPane();
        leftPane.setOrientation(Orientation.VERTICAL);
        leftPane.getItems().addAll(leftTabs, keyframeEditor);
        leftPane.setDividerPositions(0.65);

        SplitPane centerPane = new SplitPane();
        centerPane.setOrientation(Orientation.VERTICAL);
        viewportInfoLabel = new Label();
        viewportInfoLabel.setStyle("-fx-text-fill: #7e8da8; -fx-font-size: 10px; -fx-padding: 3 8 5 8;");
        viewportInfoLabel.setTooltip(new Tooltip(
            "Preview shows full scene bounds. The red rectangle marks the runtime viewport " +
            "size from jvn.project."
        ));
        updateViewportInfoLabel();

        BorderPane previewPane = new BorderPane(animationPreview);
        previewPane.setTop(viewportInfoLabel);
        previewPane.setStyle("-fx-background-color: #121212;");
        centerPane.getItems().addAll(previewPane, timelinePanel);
        centerPane.setDividerPositions(0.4);

        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        mainSplit.getItems().addAll(leftPane, centerPane, codePreview);
        mainSplit.setDividerPositions(0.17, 0.78);

        // --- Status bar with undo/redo labels ---
        statusBar = new Label("Ready");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle("-fx-background-color: #0a0a0a; -fx-text-fill: #555; -fx-font-size: 10px; " +
            "-fx-padding: 4 10; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;");
        updateStatusBar();

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(mainSplit);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color: #121212;");

        Scene fxScene = new Scene(root);
        EditorTheme.apply(fxScene);
        setScene(fxScene);
        applyLinuxDefaultWindowState();

        setupKeyboardShortcuts(fxScene);
        setupPlaybackTimer();
        tfTimelineName.textProperty().addListener((obs, ov, nv) -> setDirty(dirty));
        setDirty(false);
        setOnCloseRequest(e -> {
            if (!confirmCloseIfDirty()) {
                e.consume();
                return;
            }
            if (playbackTimer != null) playbackTimer.stop();
        });

        refreshExportPreview();
    }

    private void applyLinuxDefaultWindowState() {
        if (!isLinux()) return;
        setIconified(false);
        setMaximized(true);
        Platform.runLater(() -> {
            setIconified(false);
            setMaximized(true);
        });
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
        animationPreview.setScene(scene);
        entitySelector.setScene(scene);
        if (scene != null) {
            for (String name : scene.names()) {
                EntityTrack track = project.getOrCreateTrack(name);
                var entity = scene.find(name);
                if (entity != null) {
                    track.setLayerOrder((int) Math.round(entity.getZ()));
                }
            }
            captureProjectSnapshotBaseline();
            entitySelector.refresh(project);
            timelinePanel.refresh();
            animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
            animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
            animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());
            updatePreview();
            refreshExportPreview();
        }
    }

    private java.io.File projectRoot;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        animationPreview.setProjectRoot(root);
        assetPicker.setProjectRoot(root);
        codePreview.setProjectRoot(root);
        updateViewportInfoLabel();
    }

    private void addAssetToScene(String relativePath, String suggestedName) {
        if (scene == null) {
            scene = new JesScene2D();
            animationPreview.setScene(scene);
        }

        // Deduplicate name
        String entityName = suggestedName;
        int suffix = 2;
        while (scene.find(entityName) != null) {
            entityName = suggestedName + "_" + suffix++;
        }

        // Load image to get dimensions
        double w = 200, h = 200;
        if (projectRoot != null) {
            java.io.File imgFile = new java.io.File(projectRoot, relativePath);
            if (imgFile.exists()) {
                try {
                    javafx.scene.image.Image img = new javafx.scene.image.Image(
                        imgFile.toURI().toString(), 0, 0, true, false);
                    if (img.getWidth() > 0 && img.getHeight() > 0) {
                        w = img.getWidth();
                        h = img.getHeight();
                        double sceneH = animationPreview.getHeight() > 0 ? animationPreview.getHeight() : 720;
                        double maxH = sceneH * 0.6;
                        if (h > maxH) {
                            double scale = maxH / h;
                            w *= scale;
                            h = maxH;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        double centerX = animationPreview.getWidth() > 0 ? animationPreview.getWidth() / 2.0 : 640;
        double centerY = animationPreview.getHeight() > 0 ? animationPreview.getHeight() / 2.0 : 360;
        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(relativePath, w, h);
        sprite.setOrigin(0.5, 0.5);
        sprite.setPosition(centerX, centerY);

        scene.add(sprite);
        scene.registerEntity(entityName, sprite);

        EntityTrack track = project.getOrCreateTrack(entityName);
        track.setLayerOrder((int) Math.round(sprite.getZ()));
        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        timelinePanel.refresh();
        animationPreview.render();
        refreshExportPreviewAndMarkDirty();
    }

    public AnimationProject getProject() { return project; }

    public void setOnCopyCode(Consumer<String> callback) { this.onCopyCode = callback; }

    private void play() {
        if (project.isPlaying()) return;
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimer.start();
        btnPlay.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
        btnPlay.setDisable(true);
        btnPause.setStyle(STYLE_BTN_ACCENT);
        btnPause.setDisable(false);
    }

    private void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        btnPlay.setStyle(STYLE_BTN_ACCENT);
        btnPlay.setDisable(false);
        btnPause.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
        btnPause.setDisable(true);
    }

    private void stop() {
        pause();
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void rewind() {
        project.setPlayheadMs(0);
        timelinePanel.setPlayhead(0);
        updateTimeLabel();
        updatePreview();
    }

    private void setupPlaybackTimer() {
        playbackTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!project.isPlaying()) return;
                long deltaNanos = now - lastNanos;
                lastNanos = now;
                double deltaMs = deltaNanos / 1_000_000.0;

                double newTime = project.getPlayheadMs() + deltaMs * playbackSpeed;
                double loopEnd = project.hasLoopRegion()
                    ? project.getLoopEndMs()
                    : project.getTotalDurationMs();
                double loopStart = project.hasLoopRegion()
                    ? project.getLoopStartMs()
                    : 0;
                if (newTime >= loopEnd) {
                    if (project.isLooping()) {
                        newTime = loopStart;
                    } else {
                        newTime = project.getTotalDurationMs();
                        pause();
                    }
                }
                project.setPlayheadMs(newTime);
                timelinePanel.setPlayhead(newTime);
                updateTimeLabel();
                updatePreview();
            }
        };
        btnPlay.setStyle(STYLE_BTN_ACCENT);
        btnPause.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
        btnPause.setDisable(true);
    }

    private void updateTimeLabel() {
        lblTime.setText(String.format("%.0f ms", project.getPlayheadMs()));
    }

    private void updatePreview() {
        if (scene == null) return;

        double time = project.getPlayheadMs();
        var previewCamera = animationPreview.getCamera();
        double cameraX = previewCamera.getX();
        double cameraY = previewCamera.getY();
        double cameraZoom = previewCamera.getZoom();
        boolean hasCameraX = false;
        boolean hasCameraY = false;
        boolean hasCameraZoom = false;
        for (EntityTrack track : project.getTracks()) {
            if (track.hasKeyframes(PropertyType.CAMERA_X)) {
                cameraX = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_X, time);
                hasCameraX = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_Y)) {
                cameraY = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_Y, time);
                hasCameraY = true;
            }
            if (track.hasKeyframes(PropertyType.CAMERA_ZOOM)) {
                cameraZoom = project.computeValueAt(track.getEntityName(), PropertyType.CAMERA_ZOOM, time);
                hasCameraZoom = true;
            }
        }

        if (hasCameraX || hasCameraY || hasCameraZoom) {
            previewCamera.setPosition(cameraX, cameraY);
            previewCamera.setZoom(cameraZoom);
        }
        keyframeEditor.setCameraState(previewCamera.getX(), previewCamera.getY(), previewCamera.getZoom());

        for (EntityTrack track : project.getTracks()) {
            var entity = scene.find(track.getEntityName());
            if (entity == null) continue;

            entity.setZ(project.computeEffectiveLayerOrder(track.getEntityName()));

            if (track.hasKeyframes(PropertyType.X) || track.hasKeyframes(PropertyType.Y)) {
                double x = track.hasKeyframes(PropertyType.X)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.X, time)
                    : entity.getX();
                double y = track.hasKeyframes(PropertyType.Y)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.Y, time)
                    : entity.getY();
                entity.setPosition(x, y);
            }
            if (track.hasKeyframes(PropertyType.PIVOT_X) || track.hasKeyframes(PropertyType.PIVOT_Y)) {
                double pivotX = track.hasKeyframes(PropertyType.PIVOT_X)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.PIVOT_X, time)
                    : getEntityPivotX(entity);
                double pivotY = track.hasKeyframes(PropertyType.PIVOT_Y)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.PIVOT_Y, time)
                    : getEntityPivotY(entity);
                setEntityPivot(entity, pivotX, pivotY);
            }
            if (track.hasKeyframes(PropertyType.ROTATION)) {
                double rot = project.computeValueAt(track.getEntityName(), PropertyType.ROTATION, time);
                entity.setRotationDeg(rot);
            }
            if (track.hasKeyframes(PropertyType.SCALE_X) || track.hasKeyframes(PropertyType.SCALE_Y)) {
                double sx = track.hasKeyframes(PropertyType.SCALE_X)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.SCALE_X, time)
                    : entity.getScaleX();
                double sy = track.hasKeyframes(PropertyType.SCALE_Y)
                    ? project.computeValueAt(track.getEntityName(), PropertyType.SCALE_Y, time)
                    : entity.getScaleY();
                entity.setScale(sx, sy);
            }
            if (track.hasKeyframes(PropertyType.ALPHA)) {
                double alpha = project.computeValueAt(track.getEntityName(), PropertyType.ALPHA, time);
                setEntityAlpha(entity, alpha);
            }
        }

        animationPreview.render();
    }

    private void setEntityAlpha(com.jvn.core.scene2d.Entity2D entity, double alpha) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D a) a.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.Label2D l) 
            l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), alpha);
        else if (entity instanceof com.jvn.core.scene2d.Panel2D p)
            p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), alpha);
    }

    private static double getEntityPivotX(com.jvn.core.scene2d.Entity2D entity) {
        return entity.getOriginX();
    }

    private static double getEntityPivotY(com.jvn.core.scene2d.Entity2D entity) {
        return entity.getOriginY();
    }

    private static void setEntityPivot(com.jvn.core.scene2d.Entity2D entity, double pivotX, double pivotY) {
        entity.setOrigin(clampPivot(pivotX), clampPivot(pivotY));
    }

    private static double clampPivot(double value) {
        if (!Double.isFinite(value)) return 0.5;
        if (value < 0.0) return 0.0;
        if (value > 1.0) return 1.0;
        return value;
    }

    private void setupKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.SPACE),
            () -> { if (project.isPlaying()) pause(); else play(); }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.HOME),
            this::rewind
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K),
            () -> {
                timelinePanel.addKeyframeAtPlayhead();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.DELETE),
            () -> {
                timelinePanel.deleteSelectedKeyframe();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                String code = CodeExporter.export(project);
                copyToClipboard(code);
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::copySelectedKeyframesToClipboard
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::pasteCopiedKeyframesAtPlayhead
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::duplicateSelectedKeyframesBySnapStep
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            () -> {
                commandStack.undo();
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN),
            () -> {
                commandStack.redo();
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            () -> {
                commandStack.redo();
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN),
            () -> animationPreview.setOnionSkinning(!animationPreview.isOnionSkinning())
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(-timelinePanel.getSnapStepMs());
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(timelinePanel.getSnapStepMs());
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(-1.0);
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                timelinePanel.nudgeSelectedKeyframes(1.0);
                refreshExportPreviewAndMarkDirty();
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A),
            () -> {
                if (cbOrbitTool == null) return;
                cbOrbitTool.setSelected(!cbOrbitTool.isSelected());
                animationPreview.setOrbitToolEnabled(cbOrbitTool.isSelected());
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.A, KeyCombination.SHIFT_DOWN),
            () -> {
                animationPreview.clearOrbitAnchorForSelectedEntity();
                updatePreview();
            }
        );
    }

    private void copySelectedKeyframesToClipboard() {
        timelinePanel.copySelectedKeyframes();
    }

    private void pasteCopiedKeyframesAtPlayhead() {
        timelinePanel.pasteCopiedKeyframesAtPlayhead();
    }

    private void duplicateSelectedKeyframesBySnapStep() {
        double delta = Math.max(1.0, timelinePanel.getSnapStepMs());
        timelinePanel.duplicateSelectedKeyframes(delta);
    }

    private MenuButton buildPresetMenu() {
        MenuButton mb = new MenuButton("Presets");
        mb.setTooltip(new Tooltip("Apply animation preset to selected entity"));

        String lastCategory = "";
        for (AnimationPreset preset : AnimationPreset.ALL) {
            if (!preset.getCategory().equals(lastCategory)) {
                if (!lastCategory.isEmpty()) mb.getItems().add(new SeparatorMenuItem());
                lastCategory = preset.getCategory();
            }
            MenuItem mi = new MenuItem(preset.getName() + "  [" + preset.getCategory() + "]");
            mi.setOnAction(e -> applyPreset(preset));
            mb.getItems().add(mi);
        }
        return mb;
    }

    private void applyPreset(AnimationPreset preset) {
        if (timelinePanel.isSelectedGroup()) return;
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null) return;
        double startTime = project.getPlayheadMs();
        commandStack.execute(PuppeteerCommand.applyPreset(track, preset, startTime));
        timelinePanel.refresh();
        refreshExportPreviewAndMarkDirty();
    }

    private void refreshExportPreview() {
        codePreview.setCode(compactExport ? CodeExporter.exportCompact(project) : CodeExporter.export(project));
        List<TimelineDiagnostic.Message> diags = new ArrayList<>();
        diags.addAll(TimelineDiagnostic.diagnose(project, knownSceneEntities()));
        diags.addAll(TimelineDiagnostic.diagnoseDsl(codePreview.getCode()));
        codePreview.setDiagnostics(diags);
    }

    private void refreshExportPreviewAndMarkDirty() {
        refreshExportPreview();
        setDirty(true);
        updateStatusBar();
    }

    private void updateStatusBar() {
        if (statusBar == null) return;
        StringBuilder sb = new StringBuilder();
        if (commandStack.canUndo()) {
            sb.append("Undo: ").append(commandStack.undoDescription());
        }
        if (commandStack.canRedo()) {
            if (sb.length() > 0) sb.append("  │  ");
            sb.append("Redo: ").append(commandStack.redoDescription());
        }
        if (sb.length() == 0) sb.append("Ready");
        if (autoKeyEnabled) sb.append("  │  Auto-Key ON");
        sb.append("  │  Speed: ").append(playbackSpeed).append("x");
        statusBar.setText(sb.toString());
    }

    private void updateViewportInfoLabel() {
        if (viewportInfoLabel == null) return;
        ProjectViewportSpec.Dimensions vp = ProjectViewportSpec.resolve(projectRoot);
        viewportInfoLabel.setText(
            "Viewport: " + vp.width() + "x" + vp.height()
                + "  •  Red frame = runtime-visible area; outside frame is extra scene coverage"
        );
    }

    private void showShortcutsOverlay() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        EditorTheme.apply(dialog);
        dialog.setTitle("Keyboard Shortcuts");
        dialog.setHeaderText("Puppeteer Keyboard Shortcuts");
        dialog.setContentText(
            "Space — Play / Pause\n" +
            "Home — Rewind\n" +
            "K — Add keyframe at playhead\n" +
            "Del — Delete selected keyframe\n" +
            "Alt+←/→ — Nudge keyframe by snap step\n" +
            "Alt+Shift+←/→ — Nudge keyframe by 1ms\n" +
            "Ctrl/Cmd+Alt+C — Copy selected keyframes\n" +
            "Ctrl/Cmd+Alt+V — Paste keyframes at playhead\n" +
            "Ctrl/Cmd+Alt+D — Duplicate keyframes\n" +
            "Ctrl/Cmd+Shift+C — Copy exported code\n" +
            "Ctrl/Cmd+Alt+Z — Undo\n" +
            "Ctrl/Cmd+Alt+Y — Redo\n" +
            "Ctrl/Cmd+O — Toggle onion skinning\n" +
            "A — Toggle orbit tool\n" +
            "Shift+A — Clear orbit anchor"
        );
        dialog.showAndWait();
    }

    private void setDirty(boolean value) {
        dirty = value;
        String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (timelineName.isBlank()) timelineName = project.getName();
        if (timelineName == null || timelineName.isBlank()) timelineName = "Untitled Animation";
        String dirtySuffix = dirty ? " *" : "";
        String previewSuffix = previewStaged ? " [preview]" : "";
        setTitle("Puppeteer - " + timelineName + dirtySuffix + previewSuffix);
    }

    private void applySnapStepFromField() {
        try {
            double step = Double.parseDouble(tfSnapMs.getText().trim());
            timelinePanel.setSnapStepMs(step);
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        } catch (Exception ex) {
            tfSnapMs.setText(String.format("%.0f", timelinePanel.getSnapStepMs()));
        }
    }

    private void showAddAudioCueDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        EditorTheme.apply(dialog);
        dialog.setTitle("Add Audio Cue");
        dialog.setHeaderText("Create an audio trigger at playhead " + String.format("%.0fms", project.getPlayheadMs()));

        ButtonType addType = new ButtonType("Add Cue", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(addType, ButtonType.CANCEL);

        TextField tfPath = new TextField();
        tfPath.setPromptText("assets/audio/music/softbreeze.mp3");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        ComboBox<String> cbChannel = new ComboBox<>();
        cbChannel.getItems().addAll("music", "sound", "voice");
        cbChannel.setValue("music");
        cbChannel.setStyle(STYLE_TEXT_FIELD);

        javafx.scene.control.Slider volume = new javafx.scene.control.Slider(0.0, 1.0, 1.0);
        volume.setBlockIncrement(0.05);
        volume.setMajorTickUnit(0.25);
        volume.setMinorTickCount(4);
        volume.setShowTickMarks(false);
        volume.setShowTickLabels(false);

        Label volumeLabel = new Label("1.00");
        volumeLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        volume.valueProperty().addListener((obs, ov, nv) -> volumeLabel.setText(String.format("%.2f", nv.doubleValue())));

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(8, 8, 4, 8));
        Label lPath = makeToolbarLabel("Path");
        Label lChannel = makeToolbarLabel("Channel");
        Label lVolume = makeToolbarLabel("Volume");
        grid.add(lPath, 0, 0);
        grid.add(tfPath, 1, 0);
        grid.add(lChannel, 0, 1);
        grid.add(cbChannel, 1, 1);
        grid.add(lVolume, 0, 2);
        grid.add(new HBox(8, volume, volumeLabel), 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != addType) return;
            String path = tfPath.getText() != null ? tfPath.getText().trim() : "";
            if (path.isBlank()) return;
            AudioCue cue = new AudioCue(project.getPlayheadMs(), path, cbChannel.getValue());
            cue.setVolume(volume.getValue());
            project.addAudioCue(cue);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });
    }

    private boolean confirmCloseIfDirty() {
        if (!dirty && !previewStaged) return true;
        ButtonType save = new ButtonType("Save & Register", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        EditorTheme.apply(alert);
        alert.setTitle("Unsaved Animation");
        if (previewStaged) {
            alert.setHeaderText("A staged code preview is active.");
            alert.setContentText("Choose Save & Register to keep staged changes, or Discard to close without them.");
        } else {
            alert.setHeaderText("Save animation changes before closing Puppeteer?");
            alert.setContentText("Choose Save & Register to persist and register this timeline for runtime use.");
        }
        alert.getButtonTypes().setAll(save, discard, ButtonType.CANCEL);
        var result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return false;
        if (result.get() == discard) return true;
        return registerTimeline();
    }

    public PuppeteerCommand.Stack getCommandStack() { return commandStack; }

    // --- Toolbar styling helpers ---

    private static final String STYLE_BTN_DARK =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 4; " +
        "-fx-border-color: #3a3a3a; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String STYLE_BTN_ACCENT =
        "-fx-background-color: #4da3ff; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #5bb3ff; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_BTN_GREEN =
        "-fx-background-color: #58d68d; -fx-text-fill: #0a0a0a; -fx-background-radius: 4; " +
        "-fx-border-color: #68e69d; -fx-border-radius: 4; -fx-padding: 4 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String STYLE_TEXT_FIELD =
        "-fx-background-color: #1a1a1a; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 3 6; -fx-font-size: 11px;";

    private static Button makeToolbarIconButton(String iconClass, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("puppeteer-toolbar-icon-button");
        btn.setText("");
        btn.setGraphic(makeToolbarIcon(iconClass));
        btn.setTooltip(new Tooltip(tooltip));
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setGraphicTextGap(0);
        btn.setMinSize(30, 28);
        btn.setPrefSize(30, 28);
        btn.setMaxSize(30, 28);
        btn.setFocusTraversable(false);
        return btn;
    }

    private static Button makeToolbarSuccessIconButton(String iconClass, String tooltip) {
        Button btn = makeToolbarIconButton(iconClass, tooltip);
        btn.getStyleClass().add("puppeteer-toolbar-icon-button-success");
        return btn;
    }

    private static ToggleButton makeToolbarIconToggle(String iconClass, String tooltip) {
        ToggleButton toggle = new ToggleButton();
        toggle.getStyleClass().add("puppeteer-toolbar-icon-toggle");
        toggle.setText("");
        toggle.setGraphic(makeToolbarIcon(iconClass));
        toggle.setTooltip(new Tooltip(tooltip));
        toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        toggle.setGraphicTextGap(0);
        toggle.setMinSize(30, 28);
        toggle.setPrefSize(30, 28);
        toggle.setMaxSize(30, 28);
        toggle.setFocusTraversable(false);
        return toggle;
    }

    private static void configureToolbarIconMenuButton(MenuButton button, String iconClass, String tooltip) {
        if (button == null) return;
        button.getStyleClass().add("puppeteer-toolbar-icon-menu");
        button.setText("");
        button.setGraphic(makeToolbarIcon(iconClass));
        button.setTooltip(new Tooltip(tooltip));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphicTextGap(0);
        button.setMinSize(30, 28);
        button.setPrefSize(30, 28);
        button.setMaxSize(30, 28);
        button.setFocusTraversable(false);
    }

    private static Label makeToolbarIcon(String iconClass) {
        Label icon = new Label();
        icon.getStyleClass().addAll("icon", iconClass);
        icon.setMouseTransparent(true);
        return icon;
    }

    private static Label makeToolbarLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        return lbl;
    }

    private static Region makeSpacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private static Separator makeVSep() {
        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-padding: 0 2;");
        return sep;
    }

    private void saveSelectionAsClip() {
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return;
        double start = project.hasLoopRegion() ? project.getLoopStartMs() : 0;
        double end = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();

        AnimationClip clip = new AnimationClip(track.getEntityName() + "_clip");
        clip.captureFromTrack(track, start, end);
        if (clip.getChannels().isEmpty()) return;

        if (projectRoot != null) {
            try {
                java.nio.file.Path clipsDir = projectRoot.toPath().resolve("config").resolve("puppeteer").resolve("clips");
                java.nio.file.Path clipFile = clipsDir.resolve(clip.getName() + ".clip");
                clip.saveTo(clipFile);
                setTitle("Puppeteer - saved clip '" + clip.getName() + "'");
            } catch (java.io.IOException ex) {
                showSaveError(clip.getName(), ex.getMessage());
            }
        }
    }

    private void loadAndApplyClip() {
        EntityTrack track = selectedTrackForEditing(true);
        if (track == null || projectRoot == null) return;
        java.io.File clipsDir = new java.io.File(projectRoot, "config/puppeteer/clips");
        if (!clipsDir.isDirectory()) return;
        java.io.File[] clipFiles = clipsDir.listFiles((d, n) -> n.endsWith(".clip"));
        if (clipFiles == null || clipFiles.length == 0) return;

        java.util.List<String> names = new java.util.ArrayList<>();
        for (java.io.File f : clipFiles) {
            String n = f.getName();
            names.add(n.substring(0, n.length() - 5));
        }
        java.util.Collections.sort(names);

        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(names.get(0), names);
        EditorTheme.apply(dialog);
        dialog.setTitle("Load Clip");
        dialog.setHeaderText("Apply a saved clip to '" + track.getEntityName() + "' at playhead");
        dialog.setContentText("Clip:");
        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return;

        try {
            java.nio.file.Path clipPath = clipsDir.toPath().resolve(result.get() + ".clip");
            AnimationClip clip = AnimationClip.loadFrom(clipPath);
            clip.applyToTrack(track, project.getPlayheadMs(), 1.0);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        } catch (Exception ex) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                EditorTheme.apply(alert);
                alert.setTitle("Clip Load Failed");
                alert.setHeaderText("Could not load clip '" + result.get() + "'");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void placeEntityAtSlot(VnSlotHelper.Slot slot) {
        String entityName = timelinePanel.getSelectedEntity();
        if (entityName == null || entityName.isBlank()) return;
        double viewportW = animationPreview.getWidth() > 0 ? animationPreview.getWidth() : 1280;
        double viewportH = animationPreview.getHeight() > 0 ? animationPreview.getHeight() : 720;
        double time = project.getPlayheadMs();
        EntityTrack track = project.getOrCreateTrack(entityName);
        commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.X, time, VnSlotHelper.slotX(slot, viewportW)));
        commandStack.execute(PuppeteerCommand.upsertKeyframe(track, PropertyType.Y, time, VnSlotHelper.baselineY(viewportH)));
        timelinePanel.refresh();
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    public KeyframeSelectionModel getSelectionModel() { return selectionModel; }

    private boolean registerTimeline() {
        String name = tfTimelineName.getText().trim();
        if (name.isEmpty()) return false;
        TimelineData data = project.toTimelineData(name);
        TimelineRegistry.register(data);
        String code = CodeExporter.exportNamed(project, name);
        codePreview.setCode(code);
        boolean saved = saveTimelineFile(name, code);
        if (saved) {
            if (previewStaged) {
                previewStaged = false;
                previewBaselineProject = null;
                codePreview.markPreviewCommitted();
            }
            setDirty(false);
            setTitle("Puppeteer - " + name + " (saved & registered)");
        } else {
            // Registry succeeded but disk write failed — keep dirty
            setTitle("Puppeteer - " + name + " (registered, save FAILED)");
        }
        return saved;
    }

    private boolean saveTimelineFile(String name, String jesCode) {
        if (projectRoot == null) {
            showSaveError(name, "No project root set. Timeline registered in memory only.");
            return false;
        }
        try {
            Path dir = projectRoot.toPath().resolve("scripts").resolve("timelines");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".jes");
            Files.writeString(file, jesCode);
            return true;
        } catch (IOException ex) {
            showSaveError(name, ex.getMessage());
            return false;
        }
    }

    private void showSaveError(String name, String detail) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            EditorTheme.apply(alert);
            alert.setTitle("Save Failed");
            alert.setHeaderText("Could not save timeline '" + name + "' to disk.");
            alert.setContentText(detail != null ? detail : "Unknown error");
            alert.showAndWait();
        });
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void captureProjectSnapshotBaseline() {
        project.captureInitialSnapshot();
    }

    private Set<String> knownSceneEntities() {
        if (scene == null) return null;
        Set<String> names = new LinkedHashSet<>();
        for (String name : scene.names()) {
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names.isEmpty() ? null : names;
    }

    private void stagePreviewFromCode() {
        String code = codePreview.getCode();
        String name = tfTimelineName.getText().trim();
        if (name.isBlank()) name = project.getName();
        try {
            if (!previewStaged) {
                previewBaselineProject = project.copy();
                dirtyBeforePreviewStage = dirty;
            }

            AnimationProject imported = CodeImporter.importCode(name, code);
            applyImportedProject(imported);
            previewStaged = true;
            codePreview.setPreviewStaged(true);
            setDirty(dirty);
            refreshExportPreview();
        } catch (Exception ex) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                EditorTheme.apply(alert);
                alert.setTitle("Preview Failed");
                alert.setHeaderText("Could not parse the edited code.");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            });
        }
    }

    private void commitStagedPreview() {
        if (!previewStaged) return;
        previewStaged = false;
        previewBaselineProject = null;
        codePreview.markPreviewCommitted();
        setDirty(true);
        refreshExportPreview();
    }

    private void discardStagedPreview() {
        if (!previewStaged) return;
        if (previewBaselineProject != null) {
            applyImportedProject(previewBaselineProject);
        }
        previewStaged = false;
        previewBaselineProject = null;
        codePreview.markPreviewDiscarded();
        setDirty(dirtyBeforePreviewStage);
        refreshExportPreview();
    }

    private void applyImportedProject(AnimationProject imported) {
        if (imported == null) return;
        double playhead = project.getPlayheadMs();
        Map<String, double[]> anchorSnapshot = project.getOrbitAnchorsView();
        Map<String, String> anchorSourceSnapshot = project.getOrbitAnchorSourcesView();
        Map<String, double[]> anchorOffsetSnapshot = project.getOrbitAnchorSourceOffsetsView();
        project.replaceFrom(imported);
        project.setOrbitAnchors(anchorSnapshot);
        project.setOrbitAnchorSources(anchorSourceSnapshot);
        project.setOrbitAnchorSourceOffsets(anchorOffsetSnapshot);
        project.pruneOrbitAnchors(collectProjectEntityNames());
        project.setPlayheadMs(playhead);
        activeMoveInteraction = null;
        commandStack.clear();
        tfDuration.setText(String.valueOf((int) project.getTotalDurationMs()));
        cbLoop.setSelected(project.isLooping());
        keyframeEditor.setTimelineDurationMs(project.getTotalDurationMs());
        keyframeEditor.setKeyframe(null, null);
        entitySelector.refresh(project);
        timelinePanel.refresh();
        timelinePanel.setPlayhead(project.getPlayheadMs());
        animationPreview.setOrbitAnchors(project.getOrbitAnchorsView());
        animationPreview.setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
        animationPreview.setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());
        updateTimeLabel();
        updatePreview();
    }

    private Set<String> collectProjectEntityNames() {
        Set<String> names = new LinkedHashSet<>();
        for (EntityTrack track : project.getTracks()) {
            String name = track.getEntityName();
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    private void applyAnchorFollowerDelta(String sourceEntityName, double timeMs, double dx, double dy, Set<String> visitedSources) {
        if (sourceEntityName == null || sourceEntityName.isBlank()) return;
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) return;
        if (!visitedSources.add(sourceEntityName)) return;

        Map<String, String> sourceLinks = project.getOrbitAnchorSourcesView();
        if (sourceLinks.isEmpty()) return;
        for (Map.Entry<String, String> entry : sourceLinks.entrySet()) {
            String target = entry.getKey();
            String source = entry.getValue();
            if (target == null || target.isBlank()) continue;
            if (!sourceEntityName.equals(source)) continue;

            EntityTrack targetTrack = project.getOrCreateTrack(target);
            double fallbackX = 0.0;
            double fallbackY = 0.0;
            if (scene != null) {
                var targetEntity = scene.find(target);
                if (targetEntity != null) {
                    fallbackX = targetEntity.getX();
                    fallbackY = targetEntity.getY();
                }
            }
            double prevX = valueAtTimeOrFallback(targetTrack, PropertyType.X, timeMs, fallbackX);
            double prevY = valueAtTimeOrFallback(targetTrack, PropertyType.Y, timeMs, fallbackY);
            double nextX = prevX + dx;
            double nextY = prevY + dy;

            targetTrack.upsertKeyframe(PropertyType.X, new Keyframe(timeMs, nextX));
            targetTrack.upsertKeyframe(PropertyType.Y, new Keyframe(timeMs, nextY));

            if (scene != null) {
                var targetEntity = scene.find(target);
                if (targetEntity != null) {
                    targetEntity.setPosition(nextX, nextY);
                }
            }

            applyAnchorFollowerDelta(target, timeMs, dx, dy, visitedSources);
        }
    }

    private static double valueAtTimeOrFallback(EntityTrack track, PropertyType property, double timeMs, double fallback) {
        if (track == null || property == null) return fallback;
        Keyframe key = track.findKeyframeAt(property, timeMs);
        if (key != null && Double.isFinite(key.getValue())) return key.getValue();
        double value = track.getValueAt(property, timeMs);
        return Double.isFinite(value) ? value : fallback;
    }

    private String selectionLabel(String name, boolean group) {
        if (name == null || name.isBlank()) return "-";
        return group ? name + " [Group]" : name;
    }

    private EntityTrack selectedTrackForEditing(boolean createEntityTrack) {
        String name = timelinePanel.getSelectedEntity();
        if (name == null || name.isBlank()) return null;
        if (timelinePanel.isSelectedGroup()) {
            EntityGroup group = project.getGroup(name);
            return group != null ? group.getGroupTrack() : null;
        }
        return createEntityTrack ? project.getOrCreateTrack(name) : project.getTrack(name);
    }

    @Override
    public void close() {
        if (playbackTimer != null) playbackTimer.stop();
        super.close();
    }
}
