package com.jvn.editor.ui.actioneditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class PuppeteerWindow extends Stage {
    private static final double LEFT_LIBRARY_WORKING_WIDTH = 360.0;
    private static final List<PropertyType> GROUP_PROPERTY_CHOICES = List.of(
        PropertyType.X,
        PropertyType.Y,
        PropertyType.ROTATION,
        PropertyType.SCALE_X,
        PropertyType.SCALE_Y,
        PropertyType.ALPHA
    );

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
    private final KeyframeSelectionModel selectionModel;
    private Consumer<String> onCopyCode;
    private final TextField tfTimelineName;
    private final Map<String, CollapsibleToolbarCluster> toolbarClusters = new LinkedHashMap<>();
    private AnimatedToolbarPane toolbarPane;
    private HBox toolbarCommandBar;
    private HBox toolbarModeBar;
    private VBox toolbarShell;
    private ToggleButton btnToolbarDynamicMode;
    private ToggleButton btnToolbarCompactMode;
    private ToggleButton btnCodePaneToggle;
    private Label lblToolbarCommandSummary;
    private Label statusBar;
    private Label viewportInfoLabel;
    private Button btnSidebarPreviewLayout;
    private BorderPane previewPane;
    private StackPane previewViewportHost;
    private Button btnPreviewFullscreen;
    private Button btnPreviewBack;
    private Label lblSidebarSelectionTarget;
    private Label lblSidebarSelectionScope;
    private Label lblSidebarSelectionProperty;
    private Label lblSidebarSelectionPlayhead;
    private Label lblSidebarSelectionCount;
    private Label lblSidebarSceneTracks;
    private Label lblSidebarSceneGroups;
    private Label lblSidebarSceneDuration;
    private Label lblSidebarSceneViewport;
    private Label lblSidebarSceneCamera;
    private Label lblSidebarSceneCodePane;
    private Label lblSidebarSceneAnchors;
    private Button btnSidebarAddKeyframe;
    private Button btnSidebarFocusSelection;
    private Button btnSidebarClearSelection;
    private Button btnSidebarCodePane;
    private SplitPane topWorkspaceSplit;
    private SplitPane bottomWorkspaceSplit;
    private SplitPane workspaceContentSplit;
    private SplitPane mainWorkspaceSplit;
    private SplitPane previewFocusSplit;
    private StackPane workspaceModeHost;
    private boolean dirty = false;
    private boolean compactExport = false;
    private boolean previewStaged = false;
    private boolean dirtyBeforePreviewStage = false;
    private AnimationProject previewBaselineProject;
    private TransformInteractionState activeTransformInteraction;
    private final ActionEditorDialogOverlay overlayDialog = new ActionEditorDialogOverlay();
    private boolean bypassCloseConfirmation = false;
    private boolean codePaneVisible = true;
    private double codePaneDividerPosition = 0.78;
    private boolean previewFocusMode = false;
    private double previewFocusDividerPosition = 0.72;
    private double topWorkspaceDividerPosition = 0.2;
    private double bottomWorkspaceDividerPosition = 0.28;

    private static final double MOVE_INTERACTION_EPSILON = 0.01;
    private static final Insets TOOLBAR_PADDING_DYNAMIC = new Insets(8, 10, 8, 10);
    private static final Insets TOOLBAR_PADDING_COMPACT = new Insets(1, 4, 1, 4);
    private static final Insets TOOLBAR_COMMAND_BAR_PADDING_DYNAMIC = new Insets(6, 10, 0, 10);
    private static final Insets TOOLBAR_COMMAND_BAR_PADDING_COMPACT = new Insets(1, 4, 0, 4);
    private static final Insets TOOLBAR_MODE_BAR_PADDING_DYNAMIC = new Insets(8, 10, 0, 10);
    private static final Insets TOOLBAR_MODE_BAR_PADDING_COMPACT = new Insets(0, 4, 0, 4);
    private static final double TOOLBAR_SHELL_SPACING_DYNAMIC = 6.0;
    private static final double TOOLBAR_SHELL_SPACING_COMPACT = 1.0;
    private static final String PROP_TOOLBAR_BASE_SPACING = "puppeteerToolbarBaseSpacing";
    private static final String PROP_TOOLBAR_BASE_PREF_WIDTH = "puppeteerToolbarBasePrefWidth";
    private static final String PROP_TOOLBAR_BASE_PREF_HEIGHT = "puppeteerToolbarBasePrefHeight";
    private static final String PROP_TOOLBAR_BASE_ICON_WIDTH = "puppeteerToolbarBaseIconWidth";
    private static final String PROP_TOOLBAR_BASE_ICON_HEIGHT = "puppeteerToolbarBaseIconHeight";
    private static final String PROP_TOOLBAR_MODE_BUTTON_BASE_HEIGHT = "puppeteerToolbarModeButtonBaseHeight";
    private static final PropertyType[] TRANSFORM_INTERACTION_PROPERTIES = {
        PropertyType.X,
        PropertyType.Y,
        PropertyType.PIVOT_X,
        PropertyType.PIVOT_Y,
        PropertyType.ROTATION
    };

    private record TransformInteractionState(
        String entityName,
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates
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
        selectionModel = timelinePanel.getSelectionModel();
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
                refreshSidebarTabs();
            }
        });
        animationPreview.setOnAssetDropped(payload -> {
            if (payload == null || !payload.isValid()) return;
            addAssetToScene(payload.relativePath(), payload.suggestedName(), PuppeteerAssetPlacementRole.PROP);
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
            refreshPropertyPickerChoices();
            refreshSidebarTabs();
        });

        entitySelector.setOnSelectionChanged((name, isGroup) -> {
            timelinePanel.setSelectedTarget(name, isGroup);
            refreshPropertyPickerChoices();
        });

        entitySelector.setOnCreateGroup(groupName -> {
            this.project.getOrCreateGroup(groupName);
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(groupName);
            timelinePanel.setSelectedTarget(groupName, true);
            timelinePanel.refresh();
            updatePreview();
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

        entitySelector.setOnRenameGroup((currentName, nextName) -> {
            if (!this.project.renameGroup(currentName, nextName)) return;
            entitySelector.refresh(this.project);
            entitySelector.selectGroup(nextName);
            timelinePanel.setSelectedTarget(nextName, true);
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
            if (timelinePanel.getSelectionCount() > 1) {
                keyframeEditor.setSelection(new ArrayList<>(timelinePanel.getSelectedKeyframes()), timelinePanel.getSelectedProperty());
                keyframeEditor.setAdjacentKeyframes(null);
            } else {
                keyframeEditor.setKeyframe(kf, timelinePanel.getSelectedProperty());
                // --- A) Feed adjacent keyframes for ghost curve overlay ---
                keyframeEditor.setAdjacentKeyframes(findAdjacentKeyframes(kf, timelinePanel.getSelectedProperty()));
            }
            PropertyType selectedProp = timelinePanel.getSelectedProperty();
            if (selectedProp != null && cbProperty.getValue() != selectedProp) {
                cbProperty.setValue(selectedProp);
            }
            refreshSidebarTabs();
        });

        timelinePanel.setOnPlayheadChanged(time -> {
            this.project.setPlayheadMs(time);
            updateTimeLabel();
            updatePreview();
            refreshSidebarTabs();
        });
        timelinePanel.setOnEdited(this::refreshExportPreviewAndMarkDirty);

        keyframeEditor.setOnKeyframeChanged(() -> {
            if (timelinePanel.getSelectionCount() > 1) {
                for (EntityTrack track : project.getTracks()) {
                    for (PropertyType property : PropertyType.values()) {
                        track.sortKeyframes(property);
                    }
                }
            } else {
                PropertyType property = keyframeEditor.getCurrentProperty();
                if (property != null) {
                    EntityTrack track = selectedTrackForEditing(false);
                    if (track != null) track.sortKeyframes(property);
                }
            }
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        keyframeEditor.setOnDeleteRequested(() -> {
            timelinePanel.deleteSelectedKeyframe();
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
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
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
                activeTransformInteraction = null;
                return;
            }
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) {
                activeTransformInteraction = null;
                return;
            }
            double time = this.project.getPlayheadMs();
            activeTransformInteraction = new TransformInteractionState(
                name,
                time,
                captureTransformSnapshots(name, time)
            );
        });

        animationPreview.setOnEntityMoveInteractionFinished((name, pos) -> {
            TransformInteractionState interaction = activeTransformInteraction;
            activeTransformInteraction = null;
            if (interaction == null || name == null || pos == null || pos.length < 2) return;
            if (!name.equals(interaction.entityName())) return;
            if (!Double.isFinite(pos[0]) || !Double.isFinite(pos[1])) return;

            double time = interaction.timeMs();
            Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates = interaction.beforeStates();
            Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> afterStates = captureTransformSnapshots(time);
            List<PuppeteerCommand> commands = buildTransformInteractionCommands(time, beforeStates, afterStates);

            if (commands.isEmpty()) {
                restoreTransformSnapshots(time, beforeStates);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
                return;
            }

            commandStack.execute(PuppeteerCommand.composite("Edit transform", commands));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        animationPreview.setOnEntityPivotChanged((name, pivot) -> {
            if (name == null || pivot == null || pivot.length < 2) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.PIVOT_X, new Keyframe(time, pivot[0]));
            track.upsertKeyframe(PropertyType.PIVOT_Y, new Keyframe(time, pivot[1]));
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
            if (name == null || rotationDeg == null || !Double.isFinite(rotationDeg)) return;
            EntityTrack track = this.project.getOrCreateTrack(name);
            double time = this.project.getPlayheadMs();
            if (activeTransformInteraction != null && name.equals(activeTransformInteraction.entityName())) {
                time = activeTransformInteraction.timeMs();
            }
            track.upsertKeyframe(PropertyType.ROTATION, new Keyframe(time, rotationDeg));
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        codePreview.setOnCopy(() -> {
            copyExportedCodeToClipboard();
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
        transportBox.setAlignment(Pos.CENTER_LEFT);

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
        durationBox.setAlignment(Pos.CENTER_LEFT);

        // --- Presets ---
        Button presetButton = makeToolbarIconButton("icon-puppeteer-presets", "Apply animation preset to selected entity");
        presetButton.setOnAction(e -> showPresetMenuOverlay());

        // --- Property target + snapping ---
        cbProperty = new ComboBox<>();
        cbProperty.getItems().setAll(PropertyType.values());
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
        refreshPropertyPickerChoices();

        HBox propertyBox = new HBox(4, cbProperty);
        propertyBox.setAlignment(Pos.CENTER_LEFT);

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

        Button slotButton = makeToolbarIconButton("icon-puppeteer-focus-selection", "Place selected entity at a VN character slot");
        slotButton.setOnAction(e -> showSlotMenuOverlay());

        Button btnBatchKeyframe = makeToolbarIconButton("icon-puppeteer-snap", "Add keyframe for ALL entities at playhead (batch)");
        btnBatchKeyframe.setOnAction(e -> {
            PropertyType prop = cbProperty.getValue();
            if (prop == null) prop = PropertyType.X;
            timelinePanel.addKeyframeForAllEntities(project.getPlayheadMs(), prop);
            refreshExportPreviewAndMarkDirty();
        });

        Button btnZoomFit = makeToolbarIconButton("icon-timeline-fit", "Zoom timeline to fit content");
        btnZoomFit.setOnAction(e -> timelinePanel.zoomToFit());
        Button btnFocusSelection = makeToolbarIconButton("icon-puppeteer-focus-selection", "Zoom timeline to the current selection or active track");
        btnFocusSelection.setOnAction(e -> timelinePanel.zoomToSelection());
        Button btnPrevKeyframe = makeToolbarIconButton("icon-puppeteer-rewind", "Jump playhead to previous keyframe (Page Up)");
        btnPrevKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToPreviousKeyframe());
        Button btnNextKeyframe = makeToolbarIconButton("icon-puppeteer-forward", "Jump playhead to next keyframe (Page Down)");
        btnNextKeyframe.setOnAction(e -> timelinePanel.jumpPlayheadToNextKeyframe());

        ToggleButton cbRipple = makeToolbarIconToggle("icon-puppeteer-loop", "Ripple-retime: shift following keys when nudging a selection");
        cbRipple.setSelected(timelinePanel.isRippleRetimeEnabled());
        cbRipple.setOnAction(e -> timelinePanel.setRippleRetimeEnabled(cbRipple.isSelected()));

        Button btnDistributeKeys = makeToolbarIconButton("icon-puppeteer-align-rotation", "Distribute selected keyframes evenly across their current range");
        btnDistributeKeys.setOnAction(e -> {
            if (timelinePanel.distributeSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnReverseKeys = makeToolbarIconButton("icon-puppeteer-rewind", "Reverse selected keyframes within their current range");
        btnReverseKeys.setOnAction(e -> {
            if (timelinePanel.reverseSelectedKeyframes()) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnStretchKeys = makeToolbarIconButton("icon-timeline-fit", "Stretch selected keyframes 25% wider");
        btnStretchKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(1.25)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        Button btnCompressKeys = makeToolbarIconButton("icon-puppeteer-snap", "Compress selected keyframes to 80% of their current range");
        btnCompressKeys.setOnAction(e -> {
            if (timelinePanel.stretchSelectedKeyframes(0.8)) {
                refreshExportPreviewAndMarkDirty();
            }
        });

        ToggleButton cbCompactExport = makeToolbarIconToggle("icon-puppeteer-save-clip", "Use compact export format");
        cbCompactExport.setSelected(false);
        cbCompactExport.setOnAction(e -> {
            compactExport = cbCompactExport.isSelected();
            refreshExportPreview();
        });

        HBox keyframeOpsPrimaryRow = new HBox(4,
            btnCopyKeyframes,
            btnPasteKeyframes,
            btnDuplicateKeyframes,
            btnBatchKeyframe,
            btnSaveClip,
            btnLoadClip,
            slotButton,
            btnPrevKeyframe,
            btnNextKeyframe,
            btnFocusSelection,
            btnZoomFit
        );
        keyframeOpsPrimaryRow.setAlignment(Pos.CENTER_LEFT);

        HBox keyframeOpsSecondaryRow = new HBox(4,
            btnDistributeKeys,
            btnReverseKeys,
            btnStretchKeys,
            btnCompressKeys,
            cbRipple,
            cbCompactExport
        );
        keyframeOpsSecondaryRow.setAlignment(Pos.CENTER_LEFT);

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
        snapBox.setAlignment(Pos.CENTER_LEFT);

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
        autoKeyBox.setAlignment(Pos.CENTER_LEFT);
        HBox previewSnapBox = new HBox(4, cbSnapGrid, cbSnapEntity, cbSpeed, cbWheelMode);
        previewSnapBox.setAlignment(Pos.CENTER_LEFT);

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
        orbitBox.setAlignment(Pos.CENTER_LEFT);

        // --- Help button ---
        Button btnHelp = makeToolbarIconButton("icon-puppeteer-presets", "Show keyboard shortcuts");
        btnHelp.setOnAction(e -> showShortcutsOverlay());

        // --- Audio cues ---
        Button btnAddCue = makeToolbarIconButton("icon-puppeteer-audio-add", "Add audio cue at playhead");
        btnAddCue.setOnAction(e -> showAddAudioCueDialog());
        Button btnClearCues = makeToolbarIconButton("icon-puppeteer-audio-clear", "Remove all timeline audio cues");
        btnClearCues.setOnAction(e -> {
            if (project.getAudioCues().isEmpty()) return;
            overlayDialog.showDialog(
                "Clear Audio Cues",
                "Remove all audio cues from this animation? This cannot be undone from the cue panel.",
                null,
                ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
                ActionEditorDialogOverlay.ActionSpec.danger("Clear", () -> {
                    project.clearAudioCues();
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                })
            );
        });
        HBox cueBox = new HBox(4, btnAddCue, btnClearCues);
        cueBox.setAlignment(Pos.CENTER_LEFT);

        // --- Timeline name + Register ---
        String initialTimelineName = this.project.getName();
        if (initialTimelineName == null || initialTimelineName.isBlank()
            || "Untitled Animation".equalsIgnoreCase(initialTimelineName)) {
            initialTimelineName = "my_animation";
        }
        tfTimelineName = new TextField(initialTimelineName);
        tfTimelineName.setPrefWidth(110);
        tfTimelineName.setPromptText("timeline_name");
        tfTimelineName.setStyle(STYLE_TEXT_FIELD);
        tfTimelineName.setTooltip(new Tooltip("Name for @external jes_timeline"));

        Button btnRegister = makeToolbarSuccessIconButton("icon-puppeteer-register", "Register timeline for VNS interop");
        btnRegister.setOnAction(e -> registerTimeline());

        HBox nameBox = new HBox(4, tfTimelineName, btnRegister);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        // --- Apply Code to Model (text-first round-trip) ---
        codePreview.setOnApplyToModel(() -> {
            stagePreviewFromCode();
        });
        codePreview.setOnCommitPreview(this::commitStagedPreview);
        codePreview.setOnDiscardPreview(this::discardStagedPreview);

        // --- Assemble toolbar ---
        CollapsibleToolbarCluster transportCluster = registerToolbarCluster("transport", "Transport", transportBox);
        CollapsibleToolbarCluster durationCluster = registerToolbarCluster("duration", "Timeline", durationBox);
        CollapsibleToolbarCluster presetsCluster = registerToolbarCluster("presets", "Presets", presetButton);
        CollapsibleToolbarCluster propertyCluster = registerToolbarCluster("property", "Track", propertyBox);
        CollapsibleToolbarCluster keyframesCluster = registerToolbarCluster("keyframes", "Keyframes",
            keyframeOpsPrimaryRow, keyframeOpsSecondaryRow);
        CollapsibleToolbarCluster snapCluster = registerToolbarCluster("snap", "Snap", snapBox);
        CollapsibleToolbarCluster previewCluster = registerToolbarCluster("preview", "Preview", autoKeyBox, previewSnapBox);
        CollapsibleToolbarCluster orbitCluster = registerToolbarCluster("orbit", "Orbit", orbitBox);
        CollapsibleToolbarCluster audioCluster = registerToolbarCluster("audio", "Audio", cueBox);
        CollapsibleToolbarCluster registerCluster = registerToolbarCluster("register", "Register", nameBox);
        CollapsibleToolbarCluster helpCluster = registerToolbarCluster("help", "Help", btnHelp);

        toolbarPane = new AnimatedToolbarPane(10, 8);
        toolbarPane.addCluster(transportCluster);
        toolbarPane.addCluster(durationCluster);
        toolbarPane.addCluster(presetsCluster);
        toolbarPane.addCluster(propertyCluster);
        toolbarPane.addCluster(keyframesCluster);
        toolbarPane.addCluster(snapCluster);
        toolbarPane.addCluster(previewCluster);
        toolbarPane.addCluster(orbitCluster);
        toolbarPane.addCluster(audioCluster);
        toolbarPane.addCluster(registerCluster);
        toolbarPane.addCluster(helpCluster);
        toolbarPane.registerMarker("toolbar-group-transport-duration", transportCluster, durationCluster);
        toolbarPane.registerMarker("toolbar-group-keyframe-ops", propertyCluster, keyframesCluster);
        toolbarPane.registerMarker("toolbar-group-preview-modes", snapCluster, previewCluster);
        toolbarPane.registerMarker("toolbar-group-orbit-audio-register", orbitCluster, audioCluster, registerCluster);
        toolbarPane.setId("puppeteer-toolbar");
        toolbarPane.setPadding(TOOLBAR_PADDING_DYNAMIC);
        toolbarPane.setMinHeight(Region.USE_PREF_SIZE);
        toolbarPane.setMaxWidth(Double.MAX_VALUE);

        ToggleGroup toolbarModeGroup = new ToggleGroup();
        btnToolbarDynamicMode = makeToolbarModeButton("Dynamic", "Use the reorderable, cluster-driven toolbar layout.");
        btnToolbarCompactMode = makeToolbarModeButton("Compact", "Keep all toolbar sections expanded in a stable compact layout.");
        btnToolbarDynamicMode.setToggleGroup(toolbarModeGroup);
        btnToolbarCompactMode.setToggleGroup(toolbarModeGroup);
        btnToolbarDynamicMode.setSelected(true);
        btnToolbarDynamicMode.setOnAction(e -> {
            if (btnToolbarDynamicMode.isSelected()) {
                setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.DYNAMIC);
            }
        });
        btnToolbarCompactMode.setOnAction(e -> {
            if (btnToolbarCompactMode.isSelected()) {
                setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT);
            }
        });
        btnCodePaneToggle = makeToolbarModeButton("Code Pane", "Show or hide the generated timeline code panel.");
        btnCodePaneToggle.setSelected(true);
        btnCodePaneToggle.setOnAction(e -> setCodePaneVisible(btnCodePaneToggle.isSelected()));

        Label toolbarModeLabel = new Label("Toolbar Layout");
        toolbarModeLabel.getStyleClass().add("puppeteer-toolbar-mode-label");
        toolbarModeBar = new HBox(8, toolbarModeLabel, btnToolbarDynamicMode, btnToolbarCompactMode, btnCodePaneToggle);
        toolbarModeBar.getStyleClass().add("puppeteer-toolbar-mode-bar");
        toolbarModeBar.setAlignment(Pos.CENTER_LEFT);
        toolbarModeBar.setMaxWidth(Double.MAX_VALUE);

        toolbarCommandBar = buildToolbarCommandBar();

        toolbarShell = new VBox(6, toolbarCommandBar, toolbarModeBar, toolbarPane) {
            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }

            @Override
            protected double computePrefHeight(double width) {
                Insets insets = getInsets();
                double contentWidth = width <= 0.0
                    ? -1.0
                    : Math.max(1.0, width - insets.getLeft() - insets.getRight());
                double commandBarHeight = toolbarCommandBar.prefHeight(contentWidth);
                double modeBarHeight = toolbarModeBar.prefHeight(contentWidth);
                double clustersHeight = toolbarPane.prefHeight(contentWidth);
                return insets.getTop()
                    + commandBarHeight
                    + getSpacing()
                    + modeBarHeight
                    + getSpacing()
                    + clustersHeight
                    + insets.getBottom();
            }
        };
        toolbarShell.getStyleClass().add("puppeteer-toolbar-shell");
        toolbarShell.setFillWidth(true);
        toolbarShell.setMinHeight(Region.USE_PREF_SIZE);
        toolbarShell.setMaxWidth(Double.MAX_VALUE);

        assetPicker = new AssetPickerPanel();
        assetPicker.setOnAddToScene(this::addAssetToScene);
        entitySelector.setMinWidth(0);
        assetPicker.setMinWidth(0);

        Tab entitiesTab = new Tab("Entities", entitySelector);
        entitiesTab.setClosable(false);
        Tab assetsTab = new Tab("Assets", assetPicker);
        assetsTab.setClosable(false);
        Tab selectionTab = buildSelectionTab();
        selectionTab.setClosable(false);
        Tab sceneTab = buildSceneTab();
        sceneTab.setClosable(false);
        TabPane leftTabs = new TabPane(entitiesTab, assetsTab, selectionTab, sceneTab);
        leftTabs.setMinWidth(0);
        leftTabs.setMaxWidth(Double.MAX_VALUE);
        leftTabs.setTabMinWidth(56);
        leftTabs.setStyle("-fx-background-color: #1a1a1a;");

        StackPane leftTabsContent = new StackPane(leftTabs);
        leftTabsContent.setAlignment(Pos.TOP_LEFT);
        leftTabsContent.setMinWidth(0);
        leftTabsContent.setPrefWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsContent.setMaxWidth(Double.MAX_VALUE);

        ScrollPane leftTabsScrollPane = new ScrollPane(leftTabsContent);
        leftTabsScrollPane.setMinWidth(0);
        leftTabsScrollPane.setPrefViewportWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsScrollPane.setPrefWidth(LEFT_LIBRARY_WORKING_WIDTH);
        leftTabsScrollPane.setMaxWidth(Double.MAX_VALUE);
        leftTabsScrollPane.setFitToWidth(true);
        leftTabsScrollPane.setFitToHeight(true);
        leftTabsScrollPane.setPannable(true);
        leftTabsScrollPane.setMinHeight(0);
        leftTabsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        leftTabsScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        keyframeEditor.setMinWidth(0);
        keyframeEditor.setMinHeight(0);
        final double[] collapsedWorkspaceDivider = {0.4};
        keyframeEditor.setOnCurveEditorExpandedChanged(expanded -> {
            if (workspaceContentSplit == null || workspaceContentSplit.getDividers().isEmpty()) {
                return;
            }
            if (expanded) {
                collapsedWorkspaceDivider[0] = workspaceContentSplit.getDividerPositions()[0];
                workspaceContentSplit.setDividerPositions(Math.min(collapsedWorkspaceDivider[0], 0.42));
                return;
            }
            workspaceContentSplit.setDividerPositions(collapsedWorkspaceDivider[0]);
        });
        viewportInfoLabel = new Label();
        viewportInfoLabel.setStyle("-fx-text-fill: #979797; -fx-font-size: 10px; -fx-padding: 3 8 5 8;");
        viewportInfoLabel.setTooltip(new Tooltip(
            "Preview shows full scene bounds. The red rectangle marks the runtime viewport " +
            "size from jvn.project."
        ));
        updateViewportInfoLabel();

        btnPreviewFullscreen = makeToolbarIconButton("icon-fullscreen", "Focus the preview in the editor workspace");
        btnPreviewFullscreen.getStyleClass().add("puppeteer-preview-overlay-button");
        btnPreviewFullscreen.setText("Focus View");
        btnPreviewFullscreen.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewFullscreen.setGraphicTextGap(8);
        btnPreviewFullscreen.setMinSize(136, 36);
        btnPreviewFullscreen.setPrefSize(136, 36);
        btnPreviewFullscreen.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewFullscreen.setManaged(false);
        btnPreviewFullscreen.setVisible(false);
        btnPreviewFullscreen.setOnAction(e -> enterFullscreenPreview());

        btnPreviewBack = makeToolbarIconButton("icon-puppeteer-rewind", "Return to the standard editor workspace");
        btnPreviewBack.getStyleClass().add("puppeteer-preview-overlay-button");
        btnPreviewBack.setText("Back");
        btnPreviewBack.setContentDisplay(ContentDisplay.LEFT);
        btnPreviewBack.setGraphicTextGap(8);
        btnPreviewBack.setMinSize(102, 36);
        btnPreviewBack.setPrefSize(102, 36);
        btnPreviewBack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        btnPreviewBack.setManaged(false);
        btnPreviewBack.setVisible(false);
        btnPreviewBack.setOnAction(e -> exitFullscreenPreview());

        previewViewportHost = new StackPane(animationPreview, btnPreviewBack, btnPreviewFullscreen);
        previewViewportHost.getStyleClass().add("puppeteer-preview-viewport-host");
        previewViewportHost.setMinHeight(0);
        previewViewportHost.hoverProperty().addListener((obs, wasHover, isHover) -> updatePreviewOverlayVisibility());
        StackPane.setAlignment(btnPreviewBack, Pos.TOP_LEFT);
        StackPane.setMargin(btnPreviewBack, new Insets(10));
        StackPane.setAlignment(btnPreviewFullscreen, Pos.TOP_RIGHT);
        StackPane.setMargin(btnPreviewFullscreen, new Insets(10));
        updatePreviewOverlayVisibility();

        previewPane = new BorderPane(previewViewportHost);
        previewPane.setMinWidth(0);
        animationPreview.setMinHeight(0);
        animationPreview.setMinWidth(0);
        previewPane.setMinHeight(0);
        timelinePanel.setMinWidth(0);
        timelinePanel.setMinHeight(0);
        previewPane.setTop(viewportInfoLabel);
        previewPane.setStyle("-fx-background-color: #121212;");

        topWorkspaceSplit = new SplitPane();
        topWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        topWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        topWorkspaceSplit.setMinWidth(0);
        topWorkspaceSplit.setMinHeight(0);
        topWorkspaceSplit.getItems().addAll(leftTabsScrollPane, previewPane);
        SplitPane.setResizableWithParent(leftTabsScrollPane, Boolean.TRUE);
        SplitPane.setResizableWithParent(previewPane, Boolean.TRUE);
        topWorkspaceSplit.setDividerPositions(0.2);

        bottomWorkspaceSplit = new SplitPane();
        bottomWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        bottomWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        bottomWorkspaceSplit.setMinWidth(0);
        bottomWorkspaceSplit.setMinHeight(0);
        bottomWorkspaceSplit.getItems().addAll(keyframeEditor, timelinePanel);
        SplitPane.setResizableWithParent(keyframeEditor, Boolean.TRUE);
        SplitPane.setResizableWithParent(timelinePanel, Boolean.TRUE);
        bottomWorkspaceSplit.setDividerPositions(0.28);

        workspaceContentSplit = new SplitPane();
        workspaceContentSplit.getStyleClass().add("puppeteer-split-pane");
        workspaceContentSplit.setOrientation(Orientation.VERTICAL);
        workspaceContentSplit.setMinWidth(0);
        workspaceContentSplit.setMinHeight(0);
        workspaceContentSplit.getItems().addAll(topWorkspaceSplit, bottomWorkspaceSplit);
        SplitPane.setResizableWithParent(topWorkspaceSplit, Boolean.TRUE);
        SplitPane.setResizableWithParent(bottomWorkspaceSplit, Boolean.TRUE);
        workspaceContentSplit.setDividerPositions(0.4);
        collapsedWorkspaceDivider[0] = workspaceContentSplit.getDividerPositions()[0];

        mainWorkspaceSplit = new SplitPane();
        mainWorkspaceSplit.getStyleClass().add("puppeteer-split-pane");
        mainWorkspaceSplit.setOrientation(Orientation.HORIZONTAL);
        mainWorkspaceSplit.setMinWidth(0);
        mainWorkspaceSplit.setMinHeight(0);
        codePreview.setMinWidth(0);
        mainWorkspaceSplit.getItems().addAll(workspaceContentSplit, codePreview);
        mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);

        previewFocusSplit = new SplitPane();
        previewFocusSplit.getStyleClass().add("puppeteer-split-pane");
        previewFocusSplit.setOrientation(Orientation.VERTICAL);
        previewFocusSplit.setMinWidth(0);
        previewFocusSplit.setMinHeight(0);
        previewFocusSplit.setVisible(false);
        previewFocusSplit.setManaged(false);

        workspaceModeHost = new StackPane(mainWorkspaceSplit, previewFocusSplit);
        workspaceModeHost.setMinWidth(0);
        workspaceModeHost.setMinHeight(0);

        // --- Status bar with undo/redo labels ---
        statusBar = new Label("Ready");
        statusBar.setMaxWidth(Double.MAX_VALUE);
        statusBar.setStyle("-fx-background-color: #0a0a0a; -fx-text-fill: #555; -fx-font-size: 10px; " +
            "-fx-padding: 4 10; -fx-border-color: #2a2a2a; -fx-border-width: 1 0 0 0;");
        updateStatusBar();

        BorderPane root = new BorderPane();
        root.setTop(toolbarShell);
        root.setCenter(workspaceModeHost);
        root.setBottom(statusBar);
        root.setStyle("-fx-background-color: #121212;");

        StackPane rootStack = new StackPane(root, overlayDialog);
        Scene fxScene = new Scene(rootStack);
        EditorTheme.apply(fxScene);
        setScene(fxScene);
        applyLinuxDefaultWindowState();

        setupKeyboardShortcuts(fxScene);
        setupPlaybackTimer();
        setCodePaneVisible(true);
        applyToolbarChromeDensity(getToolbarLayoutMode());
        tfTimelineName.textProperty().addListener((obs, ov, nv) -> setDirty(dirty));
        setDirty(false);
        setOnCloseRequest(e -> {
            if (bypassCloseConfirmation) {
                if (playbackTimer != null) playbackTimer.stop();
                return;
            }
            e.consume();
            requestWindowClose();
        });

        refreshExportPreview();
    }

    private HBox buildToolbarCommandBar() {
        MenuItem miSaveRegister = new MenuItem("Save & Register");
        miSaveRegister.setOnAction(e -> registerTimeline());
        MenuItem miRefreshCode = new MenuItem("Refresh Generated Code");
        miRefreshCode.setOnAction(e -> refreshExportPreview());
        MenuItem miStagePreview = new MenuItem("Stage Code Preview");
        miStagePreview.setOnAction(e -> stagePreviewFromCode());
        MenuItem miCommitPreview = new MenuItem("Commit Staged Preview");
        miCommitPreview.setOnAction(e -> commitStagedPreview());
        MenuItem miDiscardPreview = new MenuItem("Discard Staged Preview");
        miDiscardPreview.setOnAction(e -> discardStagedPreview());
        MenuItem miCopyExportCode = new MenuItem("Copy Exported Code");
        miCopyExportCode.setOnAction(e -> copyExportedCodeToClipboard());
        MenuItem miSaveClip = new MenuItem("Save Selection as Clip");
        miSaveClip.setOnAction(e -> saveSelectionAsClip());
        MenuItem miLoadClip = new MenuItem("Load Clip at Playhead");
        miLoadClip.setOnAction(e -> loadAndApplyClip());
        MenuItem miClose = new MenuItem("Close Puppeteer");
        miClose.setOnAction(e -> requestWindowClose());

        Menu fileMenu = new Menu("File");
        fileMenu.getItems().addAll(
            miSaveRegister,
            miRefreshCode,
            new SeparatorMenuItem(),
            miStagePreview,
            miCommitPreview,
            miDiscardPreview,
            new SeparatorMenuItem(),
            miCopyExportCode,
            miSaveClip,
            miLoadClip,
            new SeparatorMenuItem(),
            miClose
        );
        fileMenu.setOnShowing(e -> {
            String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
            boolean hasTimelineName = timelineName != null && !timelineName.isBlank();
            boolean hasTrack = selectedTrackForEditing(false) != null;
            miSaveRegister.setText(dirty || previewStaged ? "Save & Register" : "Save & Register Again");
            miSaveRegister.setDisable(!hasTimelineName);
            miStagePreview.setText(previewStaged ? "Restage Code Preview" : "Stage Code Preview");
            miStagePreview.setDisable(codePreview == null || codePreview.getCode() == null || codePreview.getCode().isBlank());
            miCommitPreview.setDisable(!previewStaged);
            miDiscardPreview.setDisable(!previewStaged);
            miCopyExportCode.setDisable(codePreview == null || codePreview.getCode() == null || codePreview.getCode().isBlank());
            miSaveClip.setDisable(!hasTrack || projectRoot == null);
            miLoadClip.setDisable(!hasTrack || !hasSavedClips());
            miClose.setText(dirty || previewStaged ? "Close..." : "Close");
        });

        MenuItem miUndo = new MenuItem("Undo");
        miUndo.setOnAction(e -> executeUndo());
        MenuItem miRedo = new MenuItem("Redo");
        miRedo.setOnAction(e -> executeRedo());
        MenuItem miAddKeyframe = new MenuItem("Add Keyframe at Playhead");
        miAddKeyframe.setOnAction(e -> timelinePanel.addKeyframeAtPlayhead());
        MenuItem miDeleteKeyframes = new MenuItem("Delete Selected Keyframes");
        miDeleteKeyframes.setOnAction(e -> timelinePanel.deleteSelectedKeyframe());
        MenuItem miCopyKeyframes = new MenuItem("Copy Keyframes");
        miCopyKeyframes.setOnAction(e -> copySelectedKeyframesToClipboard());
        MenuItem miPasteKeyframes = new MenuItem("Paste Keyframes at Playhead");
        miPasteKeyframes.setOnAction(e -> pasteCopiedKeyframesAtPlayhead());
        MenuItem miDuplicateKeyframes = new MenuItem("Duplicate Keyframes");
        miDuplicateKeyframes.setOnAction(e -> duplicateSelectedKeyframesBySnapStep());
        MenuItem miDistributeKeyframes = new MenuItem("Distribute Selected Keyframes");
        miDistributeKeyframes.setOnAction(e -> timelinePanel.distributeSelectedKeyframes());
        MenuItem miReverseKeyframes = new MenuItem("Reverse Selected Keyframes");
        miReverseKeyframes.setOnAction(e -> timelinePanel.reverseSelectedKeyframes());
        MenuItem miApplyPreset = new MenuItem("Apply Animation Preset...");
        miApplyPreset.setOnAction(e -> showPresetMenuOverlay());
        MenuItem miPlaceInSlot = new MenuItem("Place Entity in VN Slot...");
        miPlaceInSlot.setOnAction(e -> showSlotMenuOverlay());

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(
            miUndo,
            miRedo,
            new SeparatorMenuItem(),
            miAddKeyframe,
            miDeleteKeyframes,
            new SeparatorMenuItem(),
            miCopyKeyframes,
            miPasteKeyframes,
            miDuplicateKeyframes,
            new SeparatorMenuItem(),
            miDistributeKeyframes,
            miReverseKeyframes,
            new SeparatorMenuItem(),
            miApplyPreset,
            miPlaceInSlot
        );
        editMenu.setOnShowing(e -> {
            int selectionCount = timelinePanel != null ? timelinePanel.getSelectionCount() : 0;
            boolean hasSelection = selectionCount > 0;
            boolean hasTarget = timelinePanel != null
                && timelinePanel.getSelectedEntity() != null
                && !timelinePanel.getSelectedEntity().isBlank();
            boolean entityTarget = hasTarget && !timelinePanel.isSelectedGroup();
            boolean hasEditableKeyframe = hasSelection || (keyframeEditor != null && keyframeEditor.getCurrentKeyframe() != null);

            miUndo.setText(commandStack.canUndo()
                ? "Undo " + commandStack.undoDescription()
                : "Undo");
            miRedo.setText(commandStack.canRedo()
                ? "Redo " + commandStack.redoDescription()
                : "Redo");
            miUndo.setDisable(!commandStack.canUndo());
            miRedo.setDisable(!commandStack.canRedo());
            miAddKeyframe.setDisable(!hasTarget);
            miDeleteKeyframes.setDisable(!hasEditableKeyframe);
            miCopyKeyframes.setDisable(!hasSelection);
            miPasteKeyframes.setDisable(!hasTarget || timelinePanel.getCopiedKeyframeCount() == 0);
            miDuplicateKeyframes.setDisable(!hasSelection);
            miDistributeKeyframes.setDisable(selectionCount < 3);
            miReverseKeyframes.setDisable(selectionCount < 2);
            miApplyPreset.setDisable(!entityTarget);
            miPlaceInSlot.setDisable(!entityTarget);
        });

        CheckMenuItem miShowCodePane = new CheckMenuItem("Show Code Pane");
        miShowCodePane.setOnAction(e -> setCodePaneVisible(miShowCodePane.isSelected()));
        CheckMenuItem miOnionSkin = new CheckMenuItem("Onion Skin Preview");
        miOnionSkin.setOnAction(e -> animationPreview.setOnionSkinning(miOnionSkin.isSelected()));
        RadioMenuItem miLayoutDynamic = new RadioMenuItem("Toolbar Layout: Dynamic");
        RadioMenuItem miLayoutCompact = new RadioMenuItem("Toolbar Layout: Compact");
        ToggleGroup layoutMenuGroup = new ToggleGroup();
        miLayoutDynamic.setToggleGroup(layoutMenuGroup);
        miLayoutCompact.setToggleGroup(layoutMenuGroup);
        miLayoutDynamic.setOnAction(e -> setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.DYNAMIC));
        miLayoutCompact.setOnAction(e -> setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode.COMPACT));
        MenuItem miFocusTimeline = new MenuItem("Focus Timeline on Selection");
        miFocusTimeline.setOnAction(e -> timelinePanel.zoomToSelection());
        MenuItem miZoomFit = new MenuItem("Zoom Timeline to Fit");
        miZoomFit.setOnAction(e -> timelinePanel.zoomToFit());
        MenuItem miFullscreenPreview = new MenuItem("Toggle Focused Preview Layout");
        miFullscreenPreview.setOnAction(e -> togglePreviewFocusMode());

        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(
            miShowCodePane,
            miOnionSkin,
            new SeparatorMenuItem(),
            miLayoutDynamic,
            miLayoutCompact,
            new SeparatorMenuItem(),
            miFocusTimeline,
            miZoomFit,
            miFullscreenPreview
        );
        viewMenu.setOnShowing(e -> {
            miShowCodePane.setSelected(codePaneVisible);
            miOnionSkin.setSelected(animationPreview.isOnionSkinning());
            miLayoutDynamic.setSelected(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.DYNAMIC);
            miLayoutCompact.setSelected(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT);
            miFullscreenPreview.setDisable(scene == null && !isPreviewFullscreenActive());
        });

        MenuItem miPlayPause = new MenuItem("Play");
        miPlayPause.setOnAction(e -> {
            if (project.isPlaying()) pause();
            else play();
        });
        MenuItem miStop = new MenuItem("Stop");
        miStop.setOnAction(e -> stop());
        MenuItem miRewind = new MenuItem("Rewind");
        miRewind.setOnAction(e -> rewind());
        CheckMenuItem miLoopPlayback = new CheckMenuItem("Loop Playback");
        miLoopPlayback.setOnAction(e -> {
            project.setLooping(miLoopPlayback.isSelected());
            if (cbLoop != null) cbLoop.setSelected(miLoopPlayback.isSelected());
            refreshExportPreviewAndMarkDirty();
        });
        MenuItem miLoopIn = new MenuItem("Set Loop In at Playhead");
        miLoopIn.setOnAction(e -> {
            double inMs = project.getPlayheadMs();
            double outMs = project.hasLoopRegion() ? project.getLoopEndMs() : project.getTotalDurationMs();
            if (inMs < outMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopOut = new MenuItem("Set Loop Out at Playhead");
        miLoopOut.setOnAction(e -> {
            double outMs = project.getPlayheadMs();
            double inMs = project.hasLoopRegion() ? project.getLoopStartMs() : 0.0;
            if (outMs > inMs) {
                project.setLoopRegion(inMs, outMs);
                timelinePanel.refresh();
                refreshExportPreviewAndMarkDirty();
            }
        });
        MenuItem miLoopClear = new MenuItem("Clear Loop Region");
        miLoopClear.setOnAction(e -> {
            project.clearLoopRegion();
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
        });

        Menu playbackMenu = new Menu("Playback");
        playbackMenu.getItems().addAll(
            miPlayPause,
            miStop,
            miRewind,
            new SeparatorMenuItem(),
            miLoopPlayback,
            miLoopIn,
            miLoopOut,
            miLoopClear
        );
        playbackMenu.setOnShowing(e -> {
            miPlayPause.setText(project.isPlaying() ? "Pause" : "Play");
            miLoopPlayback.setSelected(project.isLooping());
            miLoopClear.setDisable(!project.hasLoopRegion());
        });

        MenuItem miShowShortcuts = new MenuItem("Keyboard Shortcuts");
        miShowShortcuts.setOnAction(e -> showShortcutsOverlay());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(miShowShortcuts);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, viewMenu, playbackMenu, helpMenu);
        menuBar.setUseSystemMenuBar(false);
        menuBar.setFocusTraversable(false);
        menuBar.setMinHeight(Region.USE_PREF_SIZE);
        menuBar.setMaxWidth(Region.USE_PREF_SIZE);

        lblToolbarCommandSummary = new Label();
        lblToolbarCommandSummary.getStyleClass().add("puppeteer-toolbar-command-summary");
        lblToolbarCommandSummary.setWrapText(false);
        refreshToolbarCommandSummary();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(10, menuBar, spacer, lblToolbarCommandSummary);
        bar.getStyleClass().add("puppeteer-toolbar-command-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    private void refreshToolbarCommandSummary() {
        if (lblToolbarCommandSummary == null) return;
        List<String> parts = new ArrayList<>();
        parts.add(dirty ? "Unsaved" : "Saved");
        if (previewStaged) parts.add("Preview Staged");
        parts.add(codePaneVisible ? "Code Pane On" : "Code Pane Off");
        parts.add(getToolbarLayoutMode() == AnimatedToolbarPane.LayoutMode.COMPACT
            ? "Compact Toolbar"
            : "Dynamic Toolbar");

        if (timelinePanel != null) {
            int selectionCount = timelinePanel.getSelectionCount();
            if (selectionCount > 0) {
                parts.add(selectionCount + " Key" + (selectionCount == 1 ? "" : "s") + " Selected");
            } else if (timelinePanel.getSelectedEntity() != null && !timelinePanel.getSelectedEntity().isBlank()) {
                parts.add(selectionLabel(timelinePanel.getSelectedEntity(), timelinePanel.isSelectedGroup()));
            }
            int copiedCount = timelinePanel.getCopiedKeyframeCount();
            if (copiedCount > 0) {
                parts.add("Clipboard " + copiedCount);
            }
        }
        lblToolbarCommandSummary.setText(String.join("  •  ", parts));
    }

    private Tab buildSelectionTab() {
        lblSidebarSelectionTarget = buildSidebarValueLabel();
        lblSidebarSelectionScope = buildSidebarValueLabel();
        lblSidebarSelectionProperty = buildSidebarValueLabel();
        lblSidebarSelectionPlayhead = buildSidebarValueLabel();
        lblSidebarSelectionCount = buildSidebarValueLabel();

        btnSidebarAddKeyframe = buildSidebarActionButton("Add Keyframe", () -> {
            if (timelinePanel.getSelectedEntity() == null) return;
            timelinePanel.addKeyframeAtPlayhead();
            refreshExportPreviewAndMarkDirty();
            refreshSidebarTabs();
        });
        btnSidebarFocusSelection = buildSidebarActionButton("Focus Timeline", () -> {
            timelinePanel.zoomToSelection();
            refreshSidebarTabs();
        });
        Button btnPrevKey = buildSidebarActionButton("Prev Key", () -> {
            if (timelinePanel.jumpPlayheadToPreviousKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        });
        Button btnNextKey = buildSidebarActionButton("Next Key", () -> {
            if (timelinePanel.jumpPlayheadToNextKeyframe()) {
                updateTimeLabel();
                updatePreview();
            }
            refreshSidebarTabs();
        });
        btnSidebarClearSelection = buildSidebarActionButton("Clear", () -> {
            entitySelector.selectEntity(null);
            animationPreview.clearSelection();
            timelinePanel.setSelectedTarget(null, false);
            keyframeEditor.setEntityName("-");
            keyframeEditor.setKeyframe(null, null);
            refreshSidebarTabs();
        });

        HBox selectionActionsPrimary = buildSidebarButtonRow(btnSidebarAddKeyframe, btnSidebarFocusSelection);
        HBox selectionActionsSecondary = buildSidebarButtonRow(btnPrevKey, btnNextKey, btnSidebarClearSelection);

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Selection",
                buildSidebarInfoBlock("Target", lblSidebarSelectionTarget),
                buildSidebarInfoBlock("Scope", lblSidebarSelectionScope),
                buildSidebarInfoBlock("Property", lblSidebarSelectionProperty),
                buildSidebarInfoBlock("Playhead", lblSidebarSelectionPlayhead),
                buildSidebarInfoBlock("Selected Keyframes", lblSidebarSelectionCount)
            ),
            buildSidebarCard(
                "Actions",
                selectionActionsPrimary,
                selectionActionsSecondary
            )
        );
        Tab tab = new Tab("Selection", content);
        refreshSidebarTabs();
        return tab;
    }

    private Tab buildSceneTab() {
        lblSidebarSceneTracks = buildSidebarValueLabel();
        lblSidebarSceneGroups = buildSidebarValueLabel();
        lblSidebarSceneDuration = buildSidebarValueLabel();
        lblSidebarSceneViewport = buildSidebarValueLabel();
        lblSidebarSceneCamera = buildSidebarValueLabel();
        lblSidebarSceneCodePane = buildSidebarValueLabel();
        lblSidebarSceneAnchors = buildSidebarValueLabel();

        Button btnFitPreview = buildSidebarActionButton("Fit Preview", () -> {
            animationPreview.fitToContent();
            refreshSidebarTabs();
        });
        btnSidebarPreviewLayout = buildSidebarActionButton("Focus Preview", this::togglePreviewFocusMode);
        btnSidebarCodePane = buildSidebarActionButton("Hide Code Pane", () -> {
            setCodePaneVisible(!isCodePaneVisible());
            refreshSidebarTabs();
        });
        Button btnRefreshCode = buildSidebarActionButton("Refresh Code", this::refreshExportPreview);

        HBox previewActions = buildSidebarButtonRow(btnFitPreview, btnSidebarPreviewLayout);
        HBox workspaceActions = buildSidebarButtonRow(btnSidebarCodePane, btnRefreshCode);

        ScrollPane content = buildSidebarTabContent(
            buildSidebarCard(
                "Project",
                buildSidebarInfoBlock("Tracks", lblSidebarSceneTracks),
                buildSidebarInfoBlock("Groups", lblSidebarSceneGroups),
                buildSidebarInfoBlock("Duration", lblSidebarSceneDuration),
                buildSidebarInfoBlock("Orbit Anchors", lblSidebarSceneAnchors)
            ),
            buildSidebarCard(
                "Preview",
                buildSidebarInfoBlock("Viewport", lblSidebarSceneViewport),
                buildSidebarInfoBlock("Camera", lblSidebarSceneCamera),
                buildSidebarInfoBlock("Code Pane", lblSidebarSceneCodePane),
                previewActions,
                workspaceActions
            )
        );
        Tab tab = new Tab("Scene", content);
        refreshSidebarTabs();
        return tab;
    }

    private ScrollPane buildSidebarTabContent(Node... content) {
        VBox body = new VBox(10);
        body.setPadding(new Insets(8));
        body.setFillWidth(true);
        body.setMinWidth(0);
        if (content != null) {
            body.getChildren().addAll(content);
        }

        ScrollPane scrollPane = new ScrollPane(body);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setMinWidth(0);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        return scrollPane;
    }

    private VBox buildSidebarCard(String title, Node... content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_CARD_TITLE);

        VBox card = new VBox(8);
        card.setStyle(STYLE_SIDEBAR_CARD);
        card.setMinWidth(0);
        card.getChildren().add(titleLabel);
        if (content != null) {
            card.getChildren().addAll(content);
        }
        return card;
    }

    private VBox buildSidebarInfoBlock(String title, Label valueLabel) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle(STYLE_SIDEBAR_META_LABEL);
        VBox block = new VBox(2, titleLabel, valueLabel);
        block.setMinWidth(0);
        return block;
    }

    private Label buildSidebarValueLabel() {
        Label label = new Label("-");
        label.setStyle(STYLE_SIDEBAR_VALUE_LABEL);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private Button buildSidebarActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(STYLE_BTN_DARK);
        button.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
            event.consume();
        });
        return button;
    }

    private HBox buildSidebarButtonRow(Button... buttons) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        if (buttons != null) {
            for (Button button : buttons) {
                if (button == null) continue;
                HBox.setHgrow(button, Priority.ALWAYS);
                row.getChildren().add(button);
            }
        }
        return row;
    }

    private void refreshSidebarTabs() {
        String selectedName = timelinePanel != null ? timelinePanel.getSelectedEntity() : null;
        boolean selectedGroup = timelinePanel != null && timelinePanel.isSelectedGroup();
        PropertyType selectedProperty = timelinePanel != null ? timelinePanel.getSelectedProperty() : null;
        int selectionCount = timelinePanel != null ? timelinePanel.getSelectionCount() : 0;
        boolean hasTarget = selectedName != null && !selectedName.isBlank();

        if (lblSidebarSelectionTarget != null) {
            lblSidebarSelectionTarget.setText(hasTarget ? selectionLabel(selectedName, selectedGroup) : "No target");
        }
        if (lblSidebarSelectionScope != null) {
            lblSidebarSelectionScope.setText(
                selectionCount > 1
                    ? "Multi-keyframe selection"
                    : selectedGroup
                        ? "Group track"
                        : hasTarget
                            ? "Entity track"
                            : "Nothing selected");
        }
        if (lblSidebarSelectionProperty != null) {
            lblSidebarSelectionProperty.setText(selectedProperty != null ? selectedProperty.getDisplayName() : "None");
        }
        if (lblSidebarSelectionPlayhead != null) {
            lblSidebarSelectionPlayhead.setText(String.format("%.0f ms", project.getPlayheadMs()));
        }
        if (lblSidebarSelectionCount != null) {
            lblSidebarSelectionCount.setText(String.valueOf(selectionCount));
        }
        if (btnSidebarAddKeyframe != null) {
            btnSidebarAddKeyframe.setDisable(!hasTarget);
        }
        if (btnSidebarFocusSelection != null) {
            btnSidebarFocusSelection.setDisable(!hasTarget);
        }
        if (btnSidebarClearSelection != null) {
            btnSidebarClearSelection.setDisable(!hasTarget && selectionCount == 0);
        }

        if (lblSidebarSceneTracks != null) {
            lblSidebarSceneTracks.setText(String.valueOf(countItems(project.getTracks())));
        }
        if (lblSidebarSceneGroups != null) {
            lblSidebarSceneGroups.setText(String.valueOf(countItems(project.getGroups())));
        }
        if (lblSidebarSceneDuration != null) {
            lblSidebarSceneDuration.setText(String.format("%.0f ms", project.getTotalDurationMs()));
        }
        if (lblSidebarSceneAnchors != null) {
            lblSidebarSceneAnchors.setText(String.valueOf(project.getOrbitAnchorsView().size()));
        }
        if (lblSidebarSceneViewport != null) {
            ProjectViewportSpec.Dimensions viewport = ProjectViewportSpec.resolve(projectRoot);
            lblSidebarSceneViewport.setText(viewport.width() + " x " + viewport.height());
        }
        if (lblSidebarSceneCamera != null) {
            var camera = animationPreview.getCamera();
            lblSidebarSceneCamera.setText(String.format("X %.1f  Y %.1f  Z %.2f", camera.getX(), camera.getY(), camera.getZoom()));
        }
        if (lblSidebarSceneCodePane != null) {
            lblSidebarSceneCodePane.setText(codePaneVisible ? "Visible" : "Hidden");
        }
        if (btnSidebarPreviewLayout != null) {
            btnSidebarPreviewLayout.setText(previewFocusMode ? "Back to Workspace" : "Focus Preview");
            btnSidebarPreviewLayout.setDisable(scene == null && !previewFocusMode);
        }
        if (btnSidebarCodePane != null) {
            btnSidebarCodePane.setText(codePaneVisible ? "Hide Code Pane" : "Show Code Pane");
        }
        refreshToolbarCommandSummary();
    }

    private int countItems(Iterable<?> items) {
        if (items == null) return 0;
        int count = 0;
        for (Object ignored : items) {
            count++;
        }
        return count;
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

    public void setToolbarClustersExpanded(boolean expanded) {
        for (CollapsibleToolbarCluster cluster : toolbarClusters.values()) {
            cluster.setExpanded(expanded);
        }
    }

    public void setToolbarLayoutMode(AnimatedToolbarPane.LayoutMode mode) {
        AnimatedToolbarPane.LayoutMode resolved = mode != null
            ? mode
            : AnimatedToolbarPane.LayoutMode.DYNAMIC;
        if (toolbarPane != null) {
            toolbarPane.setLayoutMode(resolved);
            toolbarPane.setPadding(resolved == AnimatedToolbarPane.LayoutMode.COMPACT
                ? TOOLBAR_PADDING_COMPACT
                : TOOLBAR_PADDING_DYNAMIC);
        }
        applyToolbarChromeDensity(resolved);
        applyToolbarDensity(resolved);
        if (btnToolbarDynamicMode != null && btnToolbarDynamicMode.isSelected() != (resolved == AnimatedToolbarPane.LayoutMode.DYNAMIC)) {
            btnToolbarDynamicMode.setSelected(resolved == AnimatedToolbarPane.LayoutMode.DYNAMIC);
        }
        if (btnToolbarCompactMode != null && btnToolbarCompactMode.isSelected() != (resolved == AnimatedToolbarPane.LayoutMode.COMPACT)) {
            btnToolbarCompactMode.setSelected(resolved == AnimatedToolbarPane.LayoutMode.COMPACT);
        }
        refreshToolbarCommandSummary();
    }

    public AnimatedToolbarPane.LayoutMode getToolbarLayoutMode() {
        return toolbarPane != null ? toolbarPane.getLayoutMode() : AnimatedToolbarPane.LayoutMode.DYNAMIC;
    }

    private void applyToolbarDensity(AnimatedToolbarPane.LayoutMode mode) {
        if (toolbarPane == null) return;
        boolean compact = mode == AnimatedToolbarPane.LayoutMode.COMPACT;
        applyToolbarDensity(toolbarPane, compact);
        toolbarPane.requestLayout();
    }

    private void applyToolbarChromeDensity(AnimatedToolbarPane.LayoutMode mode) {
        boolean compact = mode == AnimatedToolbarPane.LayoutMode.COMPACT;
        if (toolbarShell != null) {
            toolbarShell.setSpacing(compact ? TOOLBAR_SHELL_SPACING_COMPACT : TOOLBAR_SHELL_SPACING_DYNAMIC);
        }
        if (toolbarCommandBar != null) {
            toolbarCommandBar.setPadding(compact ? TOOLBAR_COMMAND_BAR_PADDING_COMPACT : TOOLBAR_COMMAND_BAR_PADDING_DYNAMIC);
            toolbarCommandBar.setSpacing(compact ? 4.0 : 10.0);
        }
        if (toolbarModeBar != null) {
            toolbarModeBar.setPadding(compact ? TOOLBAR_MODE_BAR_PADDING_COMPACT : TOOLBAR_MODE_BAR_PADDING_DYNAMIC);
            toolbarModeBar.setSpacing(compact ? 4.0 : 8.0);
        }
        applyToolbarModeButtonDensity(btnToolbarDynamicMode, compact);
        applyToolbarModeButtonDensity(btnToolbarCompactMode, compact);
        applyToolbarModeButtonDensity(btnCodePaneToggle, compact);
    }

    private void applyToolbarDensity(Node node, boolean compact) {
        if (node == null) return;

        if (node instanceof HBox hBox) {
            double baseSpacing = rememberToolbarMetric(hBox, PROP_TOOLBAR_BASE_SPACING, hBox.getSpacing());
            hBox.setSpacing(compact ? Math.max(0.0, baseSpacing - 4.0) : baseSpacing);
        } else if (node instanceof VBox vBox) {
            double baseSpacing = rememberToolbarMetric(vBox, PROP_TOOLBAR_BASE_SPACING, vBox.getSpacing());
            vBox.setSpacing(compact ? Math.max(0.0, baseSpacing - 5.0) : baseSpacing);
        }

        if (node instanceof ButtonBase button && isToolbarIconControl(button)) {
            applyToolbarButtonDensity(button, compact);
        } else if (node instanceof Label label && label.getStyleClass().contains("puppeteer-toolbar-icon")) {
            applyToolbarIconDensity(label, compact);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                applyToolbarDensity(child, compact);
            }
        }
    }

    private void applyToolbarButtonDensity(ButtonBase button, boolean compact) {
        double baseWidth = rememberToolbarMetric(button, PROP_TOOLBAR_BASE_PREF_WIDTH, sanitizeToolbarSize(button.getPrefWidth(), button.getMinWidth()));
        double baseHeight = rememberToolbarMetric(button, PROP_TOOLBAR_BASE_PREF_HEIGHT, sanitizeToolbarSize(button.getPrefHeight(), button.getMinHeight()));
        double targetWidth = compact ? Math.max(24.0, baseWidth - 6.0) : baseWidth;
        double targetHeight = compact ? Math.max(17.0, baseHeight - 8.0) : baseHeight;
        button.setMinSize(targetWidth, targetHeight);
        button.setPrefSize(targetWidth, targetHeight);
        button.setMaxSize(targetWidth, targetHeight);
    }

    private void applyToolbarIconDensity(Label icon, boolean compact) {
        double baseWidth = rememberToolbarMetric(icon, PROP_TOOLBAR_BASE_ICON_WIDTH, sanitizeToolbarSize(icon.getPrefWidth(), 16.0));
        double baseHeight = rememberToolbarMetric(icon, PROP_TOOLBAR_BASE_ICON_HEIGHT, sanitizeToolbarSize(icon.getPrefHeight(), 16.0));
        double targetWidth = compact ? Math.max(11.0, baseWidth - 3.0) : baseWidth;
        double targetHeight = compact ? Math.max(11.0, baseHeight - 3.0) : baseHeight;
        icon.setMinSize(targetWidth, targetHeight);
        icon.setPrefSize(targetWidth, targetHeight);
        icon.setMaxSize(targetWidth, targetHeight);
    }

    private void applyToolbarModeButtonDensity(ToggleButton button, boolean compact) {
        if (button == null) return;
        double baseHeight = rememberToolbarMetric(button, PROP_TOOLBAR_MODE_BUTTON_BASE_HEIGHT, sanitizeToolbarSize(button.getMinHeight(), 26.0));
        double targetHeight = compact ? Math.max(18.0, baseHeight - 8.0) : baseHeight;
        button.setMinHeight(targetHeight);
        button.setPrefHeight(targetHeight);
    }

    private boolean isToolbarIconControl(ButtonBase button) {
        if (button == null) return false;
        List<String> styles = button.getStyleClass();
        return styles.contains("puppeteer-toolbar-icon-button")
            || styles.contains("puppeteer-toolbar-icon-toggle")
            || styles.contains("puppeteer-toolbar-icon-menu");
    }

    private double rememberToolbarMetric(Node node, String key, double fallback) {
        if (node == null || key == null) return fallback;
        Object existing = node.getProperties().get(key);
        if (existing instanceof Number number) {
            return number.doubleValue();
        }
        double value = fallback;
        node.getProperties().put(key, value);
        return value;
    }

    private double sanitizeToolbarSize(double value, double fallback) {
        if (Double.isFinite(value) && value > 0.0 && value != Region.USE_COMPUTED_SIZE && value != Region.USE_PREF_SIZE) {
            return value;
        }
        if (Double.isFinite(fallback) && fallback > 0.0 && fallback != Region.USE_COMPUTED_SIZE && fallback != Region.USE_PREF_SIZE) {
            return fallback;
        }
        return 16.0;
    }

    public void setCodePaneVisible(boolean visible) {
        codePaneVisible = visible;
        if (btnCodePaneToggle != null && btnCodePaneToggle.isSelected() != visible) {
            btnCodePaneToggle.setSelected(visible);
        }
        if (codePreview != null) {
            codePreview.setManaged(visible);
            codePreview.setVisible(visible);
        }
        if (mainWorkspaceSplit == null) {
            return;
        }
        if (visible) {
            if (!mainWorkspaceSplit.getItems().contains(codePreview)) {
                mainWorkspaceSplit.getItems().add(codePreview);
            }
            mainWorkspaceSplit.setDividerPositions(codePaneDividerPosition);
            refreshSidebarTabs();
            return;
        }
        if (mainWorkspaceSplit.getItems().contains(codePreview)) {
            double[] positions = mainWorkspaceSplit.getDividerPositions();
            if (positions.length >= 1) {
                codePaneDividerPosition = positions[0];
            }
            mainWorkspaceSplit.getItems().remove(codePreview);
        }
        refreshSidebarTabs();
        refreshToolbarCommandSummary();
    }

    public boolean isCodePaneVisible() {
        return codePaneVisible;
    }

    public void setToolbarClusterExpanded(String key, boolean expanded) {
        if (key == null || key.isBlank()) return;
        CollapsibleToolbarCluster cluster = toolbarClusters.get(key.trim().toLowerCase(Locale.ROOT));
        if (cluster != null) {
            cluster.setExpanded(expanded);
        }
    }

    public void setToolbarClusterPinned(String key, boolean pinned) {
        if (key == null || key.isBlank()) return;
        CollapsibleToolbarCluster cluster = toolbarClusters.get(key.trim().toLowerCase(Locale.ROOT));
        if (cluster != null) {
            cluster.setPinned(pinned);
        }
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
        updatePreviewOverlayVisibility();
        refreshSidebarTabs();
    }

    private java.io.File projectRoot;

    public void setProjectRoot(java.io.File root) {
        this.projectRoot = root;
        animationPreview.setProjectRoot(root);
        assetPicker.setProjectRoot(root);
        keyframeEditor.setProjectRoot(root);
        codePreview.setProjectRoot(root);
        updateViewportInfoLabel();
        refreshSidebarTabs();
    }

    public void setSourceScriptFile(java.io.File file) {
        assetPicker.setScriptTargetFile(file);
    }

    private void addAssetToScene(String relativePath, String suggestedName, PuppeteerAssetPlacementRole role) {
        if (relativePath == null || relativePath.isBlank()) return;
        if (scene == null) {
            setScene(new JesScene2D());
        }
        if (scene == null) return;

        PuppeteerAssetPlacementRole resolvedRole = role != null ? role : PuppeteerAssetPlacementRole.PROP;
        String entityName = resolveUniqueEntityName(suggestedName, resolvedRole);
        double[] naturalSize = resolveAssetNaturalSize(relativePath);
        PuppeteerAssetPlacement.Placement placement = PuppeteerAssetPlacement.plan(
            resolvedRole,
            animationPreview.getViewportDimensions(),
            naturalSize[0],
            naturalSize[1]
        );

        com.jvn.core.scene2d.Sprite2D sprite = new com.jvn.core.scene2d.Sprite2D(relativePath, placement.width(), placement.height());
        sprite.setOrigin(placement.originX(), placement.originY());
        sprite.setPosition(placement.x(), placement.y());
        sprite.setZ(placement.z());

        scene.add(sprite);
        scene.registerEntity(entityName, sprite);

        EntityTrack track = project.getOrCreateTrack(entityName);
        track.setLayerOrder((int) Math.round(placement.z()));
        captureProjectSnapshotBaseline();
        entitySelector.refresh(project);
        timelinePanel.refresh();
        timelinePanel.setSelectedTarget(entityName, false);
        animationPreview.selectEntity(entityName);
        updatePreview();
        refreshExportPreviewAndMarkDirty();
    }

    private String resolveUniqueEntityName(String suggestedName, PuppeteerAssetPlacementRole role) {
        String base = suggestedName == null ? "" : suggestedName.trim();
        if (base.isBlank()) {
            base = switch (role != null ? role : PuppeteerAssetPlacementRole.PROP) {
                case BACKGROUND -> "background";
                case CHARACTER -> "character";
                case PROP -> "prop";
            };
        }
        String entityName = base;
        int suffix = 2;
        while (scene != null && scene.find(entityName) != null) {
            entityName = base + "_" + suffix++;
        }
        return entityName;
    }

    private double[] resolveAssetNaturalSize(String relativePath) {
        javafx.scene.image.Image image = loadAssetImage(relativePath);
        if (image != null && image.getWidth() > 1.0 && image.getHeight() > 1.0) {
            return new double[] { image.getWidth(), image.getHeight() };
        }
        return new double[] { -1.0, -1.0 };
    }

    private javafx.scene.image.Image loadAssetImage(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        try {
            java.net.URL resource = getClass().getClassLoader().getResource(relativePath);
            if (resource != null) {
                javafx.scene.image.Image image = new javafx.scene.image.Image(resource.toExternalForm(), false);
                if (image.getWidth() > 1.0 && image.getHeight() > 1.0) {
                    return image;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.io.File file = new java.io.File(relativePath);
            if (!file.isAbsolute() && projectRoot != null) {
                file = new java.io.File(projectRoot, relativePath);
            }
            if (file.isFile()) {
                javafx.scene.image.Image image = new javafx.scene.image.Image(file.toURI().toString(), false);
                if (image.getWidth() > 1.0 && image.getHeight() > 1.0) {
                    return image;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public AnimationProject getProject() { return project; }

    public void setTimelineName(String name) {
        String normalized = name != null ? name.trim() : "";
        if (normalized.isBlank()) return;
        project.setName(normalized);
        tfTimelineName.setText(normalized);
        setDirty(dirty);
    }

    public void setOnCopyCode(Consumer<String> callback) { this.onCopyCode = callback; }

    private void play() {
        if (project.isPlaying()) return;
        project.setPlaying(true);
        lastNanos = System.nanoTime();
        playbackTimer.start();
        refreshTransportButtonStates();
    }

    private void pause() {
        project.setPlaying(false);
        playbackTimer.stop();
        refreshTransportButtonStates();
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
        refreshTransportButtonStates();
    }

    private void updateTimeLabel() {
        lblTime.setText(String.format("%.0f ms", project.getPlayheadMs()));
        refreshSidebarTabs();
    }

    private void refreshPropertyPickerChoices() {
        if (cbProperty == null || timelinePanel == null) return;
        List<PropertyType> allowed = timelinePanel.isSelectedGroup()
            ? GROUP_PROPERTY_CHOICES
            : List.of(PropertyType.values());
        if (!cbProperty.getItems().equals(allowed)) {
            cbProperty.getItems().setAll(allowed);
        }
        PropertyType selected = timelinePanel.getSelectedProperty();
        if (selected == null || !allowed.contains(selected)) {
            selected = allowed.isEmpty() ? null : allowed.get(0);
        }
        if (selected != null && cbProperty.getValue() != selected) {
            cbProperty.setValue(selected);
        }
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
            String entityName = track.getEntityName();
            entity.setZ(project.computeEffectiveLayerOrder(entityName));

            double baseX = baselinePropertyValue(entityName, entity, PropertyType.X);
            double baseY = baselinePropertyValue(entityName, entity, PropertyType.Y);
            double x = project.hasEffectiveAnimation(entityName, PropertyType.X)
                ? project.computeValueAt(entityName, PropertyType.X, time, baseX)
                : baseX;
            double y = project.hasEffectiveAnimation(entityName, PropertyType.Y)
                ? project.computeValueAt(entityName, PropertyType.Y, time, baseY)
                : baseY;
            entity.setPosition(x, y);

            double basePivotX = baselinePropertyValue(entityName, entity, PropertyType.PIVOT_X);
            double basePivotY = baselinePropertyValue(entityName, entity, PropertyType.PIVOT_Y);
            double pivotX = project.hasEffectiveAnimation(entityName, PropertyType.PIVOT_X)
                ? project.computeValueAt(entityName, PropertyType.PIVOT_X, time, basePivotX)
                : basePivotX;
            double pivotY = project.hasEffectiveAnimation(entityName, PropertyType.PIVOT_Y)
                ? project.computeValueAt(entityName, PropertyType.PIVOT_Y, time, basePivotY)
                : basePivotY;
            setEntityPivot(entity, pivotX, pivotY);

            double baseRotation = baselinePropertyValue(entityName, entity, PropertyType.ROTATION);
            double rotation = project.hasEffectiveAnimation(entityName, PropertyType.ROTATION)
                ? project.computeValueAt(entityName, PropertyType.ROTATION, time, baseRotation)
                : baseRotation;
            entity.setRotationDeg(rotation);

            double baseScaleX = baselinePropertyValue(entityName, entity, PropertyType.SCALE_X);
            double baseScaleY = baselinePropertyValue(entityName, entity, PropertyType.SCALE_Y);
            double scaleX = project.hasEffectiveAnimation(entityName, PropertyType.SCALE_X)
                ? project.computeValueAt(entityName, PropertyType.SCALE_X, time, baseScaleX)
                : baseScaleX;
            double scaleY = project.hasEffectiveAnimation(entityName, PropertyType.SCALE_Y)
                ? project.computeValueAt(entityName, PropertyType.SCALE_Y, time, baseScaleY)
                : baseScaleY;
            entity.setScale(scaleX, scaleY);

            double baseAlpha = baselinePropertyValue(entityName, entity, PropertyType.ALPHA);
            double alpha = project.hasEffectiveAnimation(entityName, PropertyType.ALPHA)
                ? project.computeValueAt(entityName, PropertyType.ALPHA, time, baseAlpha)
                : baseAlpha;
            setEntityAlpha(entity, alpha);
        }

        animationPreview.render();
        refreshSidebarTabs();
    }

    private void setEntityAlpha(com.jvn.core.scene2d.Entity2D entity, double alpha) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D a) a.setAlpha(alpha);
        else if (entity instanceof com.jvn.core.scene2d.Label2D l) 
            l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), alpha);
        else if (entity instanceof com.jvn.core.scene2d.Panel2D p)
            p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), alpha);
    }

    private double baselinePropertyValue(com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        return baselinePropertyValue(null, entity, property);
    }

    private double baselinePropertyValue(String entityName, com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        Double initialValue = entityName != null ? project.getInitialSnapshotValue(entityName, property) : null;
        if (initialValue != null && Double.isFinite(initialValue)) {
            return initialValue;
        }
        return fallbackPropertyValue(entity, property);
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

    private void refreshTransportButtonStates() {
        refreshTransportButtonState(btnPlay, btnPause);
    }

    private void refreshTransportButtonState(Button playButton, Button pauseButton) {
        if (playButton == null || pauseButton == null) return;
        if (project.isPlaying()) {
            playButton.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
            playButton.setDisable(true);
            pauseButton.setStyle(STYLE_BTN_ACCENT);
            pauseButton.setDisable(false);
        } else {
            playButton.setStyle(STYLE_BTN_ACCENT);
            playButton.setDisable(false);
            pauseButton.setStyle(STYLE_BTN_DARK + "-fx-opacity: 0.5;");
            pauseButton.setDisable(true);
        }
    }

    private boolean isPreviewFullscreenActive() {
        return previewFocusMode;
    }

    private void togglePreviewFocusMode() {
        if (isPreviewFullscreenActive()) exitFullscreenPreview();
        else enterFullscreenPreview();
    }

    private void enterFullscreenPreview() {
        if (scene == null || isPreviewFullscreenActive()) return;
        topWorkspaceDividerPosition = readDividerPosition(topWorkspaceSplit, topWorkspaceDividerPosition);
        bottomWorkspaceDividerPosition = readDividerPosition(bottomWorkspaceSplit, bottomWorkspaceDividerPosition);

        detachNode(previewPane);
        detachNode(timelinePanel);
        previewFocusSplit.getItems().setAll(previewPane, timelinePanel);
        SplitPane.setResizableWithParent(previewPane, Boolean.TRUE);
        SplitPane.setResizableWithParent(timelinePanel, Boolean.TRUE);

        previewFocusMode = true;
        updatePreviewWorkspaceModeVisibility();
        updatePreviewOverlayVisibility();
        refreshSidebarTabs();
        Platform.runLater(() -> {
            previewFocusSplit.setDividerPositions(previewFocusDividerPosition);
            animationPreview.requestFocus();
            updatePreview();
        });
    }

    private void exitFullscreenPreview() {
        if (!previewFocusMode) return;
        previewFocusDividerPosition = readDividerPosition(previewFocusSplit, previewFocusDividerPosition);
        previewFocusSplit.getItems().clear();
        attachToSplitPane(topWorkspaceSplit, previewPane, 1);
        attachToSplitPane(bottomWorkspaceSplit, timelinePanel, 1);
        previewFocusMode = false;
        updatePreviewWorkspaceModeVisibility();
        updatePreviewOverlayVisibility();
        refreshSidebarTabs();
        Platform.runLater(() -> {
            topWorkspaceSplit.setDividerPositions(topWorkspaceDividerPosition);
            bottomWorkspaceSplit.setDividerPositions(bottomWorkspaceDividerPosition);
            updatePreview();
        });
    }

    private void updatePreviewOverlayVisibility() {
        if (btnPreviewFullscreen == null || previewViewportHost == null) return;
        boolean showFocusButton = scene != null && !isPreviewFullscreenActive() && previewViewportHost.isHover();
        btnPreviewFullscreen.setVisible(showFocusButton);
        if (btnPreviewBack != null) {
            btnPreviewBack.setVisible(isPreviewFullscreenActive());
        }
    }

    private void updatePreviewWorkspaceModeVisibility() {
        if (mainWorkspaceSplit == null || previewFocusSplit == null) return;
        mainWorkspaceSplit.setManaged(!previewFocusMode);
        mainWorkspaceSplit.setVisible(!previewFocusMode);
        previewFocusSplit.setManaged(previewFocusMode);
        previewFocusSplit.setVisible(previewFocusMode);
    }

    private static double readDividerPosition(SplitPane splitPane, double fallback) {
        if (splitPane == null || splitPane.getDividers().isEmpty()) return fallback;
        return splitPane.getDividerPositions()[0];
    }

    private static void attachToSplitPane(SplitPane splitPane, Node node, int index) {
        if (splitPane == null || node == null || splitPane.getItems().contains(node)) return;
        int safeIndex = Math.max(0, Math.min(index, splitPane.getItems().size()));
        splitPane.getItems().add(safeIndex, node);
        SplitPane.setResizableWithParent(node, Boolean.TRUE);
    }

    private static void detachNode(Node node) {
        if (node == null) return;
        Parent parent = node.getParent();
        if (parent instanceof SplitPane splitPane) {
            splitPane.getItems().remove(node);
            return;
        }
        if (parent instanceof BorderPane borderPane) {
            if (borderPane.getTop() == node) borderPane.setTop(null);
            else if (borderPane.getBottom() == node) borderPane.setBottom(null);
            else if (borderPane.getLeft() == node) borderPane.setLeft(null);
            else if (borderPane.getRight() == node) borderPane.setRight(null);
            else if (borderPane.getCenter() == node) borderPane.setCenter(null);
            return;
        }
        if (parent instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
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
            new KeyCodeCombination(KeyCode.ESCAPE),
            () -> {
                if (isPreviewFullscreenActive()) {
                    exitFullscreenPreview();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_UP),
            () -> timelinePanel.jumpPlayheadToPreviousKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.PAGE_DOWN),
            () -> timelinePanel.jumpPlayheadToNextKeyframe()
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            () -> timelinePanel.zoomToSelection()
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
            this::copyExportedCodeToClipboard
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
            this::executeUndo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN, KeyCombination.ALT_DOWN),
            this::executeRedo
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN),
            this::executeRedo
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
            new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (timelinePanel.reverseSelectedKeyframes()) {
                    refreshExportPreviewAndMarkDirty();
                }
            }
        );
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.E, KeyCombination.ALT_DOWN, KeyCombination.SHIFT_DOWN),
            () -> {
                if (timelinePanel.distributeSelectedKeyframes()) {
                    refreshExportPreviewAndMarkDirty();
                }
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
        refreshToolbarCommandSummary();
    }

    private void copyExportedCodeToClipboard() {
        String code = CodeExporter.export(project);
        copyToClipboard(code);
        if (onCopyCode != null) onCopyCode.accept(code);
    }

    private void pasteCopiedKeyframesAtPlayhead() {
        timelinePanel.pasteCopiedKeyframesAtPlayhead();
        refreshToolbarCommandSummary();
    }

    private void duplicateSelectedKeyframesBySnapStep() {
        double delta = Math.max(1.0, timelinePanel.getSnapStepMs());
        timelinePanel.duplicateSelectedKeyframes(delta);
        refreshToolbarCommandSummary();
    }

    private void executeUndo() {
        if (!commandStack.canUndo()) return;
        commandStack.undo();
        timelinePanel.refresh();
        refreshExportPreviewAndMarkDirty();
    }

    private void executeRedo() {
        if (!commandStack.canRedo()) return;
        commandStack.redo();
        timelinePanel.refresh();
        refreshExportPreviewAndMarkDirty();
    }

    private void showPresetMenuOverlay() {
        VBox content = new VBox(10);
        String lastCategory = "";
        for (AnimationPreset preset : AnimationPreset.ALL) {
            if (!preset.getCategory().equals(lastCategory)) {
                lastCategory = preset.getCategory();
                Label header = new Label(lastCategory);
                header.setStyle("-fx-text-fill: #8ea4c6; -fx-font-size: 10px; -fx-font-weight: bold;");
                content.getChildren().add(header);
            }
            content.getChildren().add(buildOverlayMenuButton(
                preset.getName(),
                () -> applyPreset(preset)));
        }
        overlayDialog.showDialog(
            "Animation Presets",
            "Apply a preset to the selected entity at the current playhead.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
    }

    private void showSlotMenuOverlay() {
        VBox content = new VBox(8);
        for (VnSlotHelper.Slot slot : VnSlotHelper.Slot.values()) {
            content.getChildren().add(buildOverlayMenuButton(
                slot.name().replace('_', ' '),
                () -> placeEntityAtSlot(slot)));
        }
        overlayDialog.showDialog(
            "VN Slots",
            "Place the selected entity on a standard visual-novel staging slot.",
            content,
            ActionEditorDialogOverlay.ActionSpec.neutral("Close", overlayDialog::hideOverlay).defaultFocus(true)
        );
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
        refreshSidebarTabs();
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
        refreshToolbarCommandSummary();
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
        TextArea content = new TextArea(
            "Space — Play / Pause\n" +
            "Home — Rewind\n" +
            "Page Up / Page Down — Jump to previous / next keyframe\n" +
            "K — Add keyframe at playhead\n" +
            "Del — Delete selected keyframe\n" +
            "Ctrl/Cmd+Alt+F — Focus timeline on selection\n" +
            "Alt+←/→ — Nudge keyframe by snap step\n" +
            "Alt+Shift+←/→ — Nudge keyframe by 1ms\n" +
            "Alt+Shift+R — Reverse selected keyframes\n" +
            "Alt+Shift+E — Distribute selected keyframes\n" +
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
        content.setEditable(false);
        content.setWrapText(false);
        content.setFocusTraversable(false);
        content.setStyle("-fx-control-inner-background: #121212; -fx-text-fill: #d7dde6; -fx-font-family: Monospaced;");
        content.setPrefColumnCount(36);
        content.setPrefRowCount(14);
        overlayDialog.showDialog(
            "Keyboard Shortcuts",
            "Puppeteer keyboard shortcuts",
            content,
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private void setDirty(boolean value) {
        dirty = value;
        String timelineName = tfTimelineName != null ? tfTimelineName.getText().trim() : "";
        if (timelineName.isBlank()) timelineName = project.getName();
        if (timelineName == null || timelineName.isBlank()) timelineName = "Untitled Animation";
        String dirtySuffix = dirty ? " *" : "";
        String previewSuffix = previewStaged ? " [preview]" : "";
        setTitle("Puppeteer - " + timelineName + dirtySuffix + previewSuffix);
        refreshToolbarCommandSummary();
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
        TextField tfPath = new TextField();
        tfPath.setPromptText("assets/audio/music/softbreeze.mp3");
        tfPath.setStyle(STYLE_TEXT_FIELD);

        ToggleGroup channelGroup = new ToggleGroup();
        RadioButton rbMusic = buildChannelButton("music", channelGroup, true);
        RadioButton rbSound = buildChannelButton("sound", channelGroup, false);
        RadioButton rbVoice = buildChannelButton("voice", channelGroup, false);

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
        grid.add(new HBox(8, rbMusic, rbSound, rbVoice), 1, 1);
        grid.add(lVolume, 0, 2);
        grid.add(new HBox(8, volume, volumeLabel), 1, 2);
        overlayDialog.showDialog(
            "Add Audio Cue",
            "Create an audio trigger at playhead " + String.format("%.0fms", project.getPlayheadMs()),
            grid,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Add Cue", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
            String path = tfPath.getText() != null ? tfPath.getText().trim() : "";
                if (path.isBlank()) {
                    tfPath.requestFocus();
                    return;
                }
                RadioButton selectedChannel = (RadioButton) channelGroup.getSelectedToggle();
                String channel = selectedChannel != null ? selectedChannel.getText() : "music";
                AudioCue cue = new AudioCue(project.getPlayheadMs(), path, channel);
            cue.setVolume(volume.getValue());
            project.addAudioCue(cue);
            timelinePanel.refresh();
            refreshExportPreviewAndMarkDirty();
                overlayDialog.hideOverlay();
            })
        );
    }

    private void requestWindowClose() {
        if (!dirty && !previewStaged) {
            closeNow();
            return;
        }
        String message = previewStaged
            ? "A staged code preview is active. Save & Register keeps the staged changes; Discard closes without them."
            : "Save animation changes before closing Puppeteer?";
        overlayDialog.showDialog(
            "Unsaved Animation",
            message,
            null,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.danger("Discard", this::closeNow),
            ActionEditorDialogOverlay.ActionSpec.accent("Save & Register", () -> {
                if (registerTimeline()) {
                    closeNow();
                }
            })
        );
    }

    private void closeNow() {
        bypassCloseConfirmation = true;
        close();
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
    private static final String STYLE_SIDEBAR_CARD =
        "-fx-background-color: #151515; -fx-border-color: #2f2f2f; -fx-border-radius: 8; "
            + "-fx-background-radius: 8; -fx-padding: 10 12;";
    private static final String STYLE_SIDEBAR_CARD_TITLE =
        "-fx-text-fill: #efefef; -fx-font-size: 12px; -fx-font-weight: bold;";
    private static final String STYLE_SIDEBAR_META_LABEL =
        "-fx-text-fill: #8c8c8c; -fx-font-size: 10px; -fx-font-weight: bold;";
    private static final String STYLE_SIDEBAR_VALUE_LABEL =
        "-fx-text-fill: #f0f0f0; -fx-font-size: 12px;";

    private static Button makeToolbarIconButton(String iconClass, String tooltip) {
        Button btn = new Button();
        btn.getStyleClass().add("puppeteer-toolbar-icon-button");
        btn.setText("");
        btn.setGraphic(makeToolbarIcon(iconClass));
        btn.setTooltip(new Tooltip(tooltip));
        btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setGraphicTextGap(0);
        btn.setMinSize(34, 30);
        btn.setPrefSize(34, 30);
        btn.setMaxSize(34, 30);
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
        toggle.setMinSize(34, 30);
        toggle.setPrefSize(34, 30);
        toggle.setMaxSize(34, 30);
        toggle.setFocusTraversable(false);
        return toggle;
    }

    private static Label makeToolbarIcon(String iconClass) {
        Label icon = new Label();
        icon.getStyleClass().addAll("icon", "puppeteer-toolbar-icon", iconClass);
        icon.setMouseTransparent(true);
        return icon;
    }

    private static Label makeToolbarLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
        return lbl;
    }

    private static ToggleButton makeToolbarModeButton(String text, String tooltip) {
        ToggleButton button = new ToggleButton(text);
        button.getStyleClass().add("puppeteer-toolbar-mode-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setMinHeight(26);
        button.setFocusTraversable(false);
        return button;
    }

    private static RadioButton buildChannelButton(String channel, ToggleGroup group, boolean selected) {
        RadioButton button = new RadioButton(channel);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.setStyle("-fx-text-fill: #d7dde6; -fx-font-size: 11px;");
        return button;
    }

    private Button buildOverlayMenuButton(String label, Runnable action) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle(
            "-fx-background-color: #23262c; -fx-text-fill: #d7dde6; -fx-background-radius: 4; "
                + "-fx-border-color: #3a3f48; -fx-border-radius: 4; -fx-padding: 7 10; -fx-font-size: 11px; -fx-cursor: hand;");
        button.setOnAction(event -> {
            overlayDialog.hideOverlay();
            if (action != null) {
                action.run();
            }
            event.consume();
        });
        return button;
    }

    private CollapsibleToolbarCluster registerToolbarCluster(String key, String title, Node... rows) {
        VBox content = new VBox(6);
        content.setAlignment(Pos.CENTER_LEFT);
        for (Node row : rows) {
            if (row != null) {
                content.getChildren().add(row);
            }
        }
        CollapsibleToolbarCluster cluster = new CollapsibleToolbarCluster(key, title, content);
        toolbarClusters.put(cluster.getClusterKey(), cluster);
        return cluster;
    }

    private static Region makeSpacer(double width) {
        Region r = new Region();
        r.setMinWidth(width);
        r.setPrefWidth(width);
        return r;
    }

    private boolean hasSavedClips() {
        if (projectRoot == null) return false;
        java.io.File clipsDir = new java.io.File(projectRoot, "config/puppeteer/clips");
        if (!clipsDir.isDirectory()) return false;
        java.io.File[] clipFiles = clipsDir.listFiles((dir, name) -> name != null && name.endsWith(".clip"));
        return clipFiles != null && clipFiles.length > 0;
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

        ListView<String> clipList = new ListView<>();
        clipList.getItems().setAll(names);
        clipList.getSelectionModel().select(0);
        clipList.setPrefHeight(Math.min(320.0, Math.max(140.0, names.size() * 28.0 + 12.0)));

        overlayDialog.showDialog(
            "Load Clip",
            "Apply a saved clip to '" + track.getEntityName() + "' at playhead.",
            clipList,
            ActionEditorDialogOverlay.ActionSpec.neutral("Cancel", overlayDialog::hideOverlay).defaultFocus(true),
            ActionEditorDialogOverlay.ActionSpec.stayOpen("Apply", ActionEditorDialogOverlay.ButtonStyle.ACCENT, () -> {
                String selectedClip = clipList.getSelectionModel().getSelectedItem();
                if (selectedClip == null || selectedClip.isBlank()) {
                    clipList.requestFocus();
                    return;
                }
                try {
                    java.nio.file.Path clipPath = clipsDir.toPath().resolve(selectedClip + ".clip");
                    AnimationClip clip = AnimationClip.loadFrom(clipPath);
                    clip.applyToTrack(track, project.getPlayheadMs(), 1.0);
                    timelinePanel.refresh();
                    refreshExportPreviewAndMarkDirty();
                    overlayDialog.hideOverlay();
                } catch (Exception ex) {
                    showOverlayError("Clip Load Failed", "Could not load clip '" + selectedClip + "'", ex.getMessage());
                }
            })
        );
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
        Platform.runLater(() -> showOverlayError(
            "Save Failed",
            "Could not save timeline '" + name + "' to disk.",
            detail != null ? detail : "Unknown error"));
    }

    private void showOverlayError(String title, String header, String detail) {
        Label detailLabel = new Label(detail == null || detail.isBlank() ? "Unknown error" : detail.trim());
        detailLabel.setWrapText(true);
        detailLabel.setStyle("-fx-text-fill: #e6a8b3; -fx-font-size: 11px;");
        overlayDialog.showDialog(
            title,
            header,
            detailLabel,
            ActionEditorDialogOverlay.ActionSpec.accent("Close", overlayDialog::hideOverlay)
        );
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void captureProjectSnapshotBaseline() {
        if (scene == null) {
            project.captureInitialSnapshot();
            return;
        }
        Map<String, Map<PropertyType, Double>> snapshot = new LinkedHashMap<>();
        for (String entityName : scene.names()) {
            if (entityName == null || entityName.isBlank()) continue;
            var entity = scene.find(entityName);
            if (entity == null) continue;
            Map<PropertyType, Double> props = new EnumMap<>(PropertyType.class);
            for (PropertyType property : PropertyType.values()) {
                if (!property.isEntityProperty()) continue;
                props.put(property, baselinePropertyValue(entity, property));
            }
            snapshot.put(entityName, props);
        }
        project.setInitialSnapshot(snapshot);
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
            Platform.runLater(() -> showOverlayError(
                "Preview Failed",
                "Could not parse the edited code.",
                ex.getMessage()));
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
        activeTransformInteraction = null;
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

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(double timeMs) {
        return captureTransformSnapshots(null, timeMs);
    }

    private Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> captureTransformSnapshots(String primaryEntityName, double timeMs) {
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> snapshots = new LinkedHashMap<>();
        Set<String> names = collectProjectEntityNames();
        if (primaryEntityName != null && !primaryEntityName.isBlank()) {
            names.add(primaryEntityName);
            project.getOrCreateTrack(primaryEntityName);
        }
        for (String entityName : names) {
            EntityTrack track = project.getTrack(entityName);
            if (track == null) continue;
            snapshots.put(entityName, captureTransformTrackSnapshots(track, timeMs));
        }
        return snapshots;
    }

    private Map<PropertyType, PuppeteerCommand.PropertySnapshot> captureTransformTrackSnapshots(EntityTrack track, double timeMs) {
        Map<PropertyType, PuppeteerCommand.PropertySnapshot> snapshots = new java.util.EnumMap<>(PropertyType.class);
        var entity = scene != null ? scene.find(track.getEntityName()) : null;
        for (PropertyType property : TRANSFORM_INTERACTION_PROPERTIES) {
            Keyframe keyframe = track.findKeyframeAt(property, timeMs);
            boolean present = keyframe != null;
            double value = present
                ? keyframe.getValue()
                : valueAtTimeOrFallback(track, property, timeMs, fallbackPropertyValue(entity, property));
            snapshots.put(property, new PuppeteerCommand.PropertySnapshot(present, value));
        }
        return snapshots;
    }

    private List<PuppeteerCommand> buildTransformInteractionCommands(
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> beforeStates,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> afterStates
    ) {
        List<PuppeteerCommand> commands = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        names.addAll(beforeStates.keySet());
        names.addAll(afterStates.keySet());
        for (String entityName : names) {
            EntityTrack track = project.getOrCreateTrack(entityName);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> before = beforeStates.getOrDefault(entityName, Map.of());
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> after = afterStates.getOrDefault(entityName, Map.of());
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedBefore = new java.util.EnumMap<>(PropertyType.class);
            Map<PropertyType, PuppeteerCommand.PropertySnapshot> changedAfter = new java.util.EnumMap<>(PropertyType.class);

            for (PropertyType property : TRANSFORM_INTERACTION_PROPERTIES) {
                PuppeteerCommand.PropertySnapshot beforeSnapshot = before.get(property);
                PuppeteerCommand.PropertySnapshot afterSnapshot = after.get(property);
                if (!transformChanged(property, beforeSnapshot, afterSnapshot)) continue;
                changedBefore.put(property, beforeSnapshot != null ? beforeSnapshot : new PuppeteerCommand.PropertySnapshot(false, 0.0));
                changedAfter.put(property, afterSnapshot != null ? afterSnapshot : new PuppeteerCommand.PropertySnapshot(false, 0.0));
            }

            if (!changedBefore.isEmpty()) {
                commands.add(PuppeteerCommand.applyPropertiesAtTime(
                    track,
                    timeMs,
                    changedBefore,
                    changedAfter,
                    "Edit " + entityName
                ));
            }
        }
        return commands;
    }

    private void restoreTransformSnapshots(
        double timeMs,
        Map<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> snapshots
    ) {
        for (Map.Entry<String, Map<PropertyType, PuppeteerCommand.PropertySnapshot>> entry : snapshots.entrySet()) {
            EntityTrack track = project.getOrCreateTrack(entry.getKey());
            for (Map.Entry<PropertyType, PuppeteerCommand.PropertySnapshot> propertyEntry : entry.getValue().entrySet()) {
                restorePropertySnapshot(track, propertyEntry.getKey(), timeMs, propertyEntry.getValue());
            }
        }
        updatePreview();
    }

    private void restorePropertySnapshot(
        EntityTrack track,
        PropertyType property,
        double timeMs,
        PuppeteerCommand.PropertySnapshot snapshot
    ) {
        if (track == null || property == null || snapshot == null) return;
        if (snapshot.present()) {
            track.upsertKeyframe(property, new Keyframe(timeMs, snapshot.value()));
        } else {
            Keyframe keyframe = track.findKeyframeAt(property, timeMs);
            if (keyframe != null) {
                track.removeKeyframe(property, keyframe);
            }
        }
        track.sortKeyframes(property);
    }

    private static boolean transformChanged(
        PropertyType property,
        PuppeteerCommand.PropertySnapshot before,
        PuppeteerCommand.PropertySnapshot after
    ) {
        if (before == null && after == null) return false;
        if (before == null || after == null) return true;
        double epsilon = property == PropertyType.ROTATION ? 0.1 : MOVE_INTERACTION_EPSILON;
        return Math.abs(before.value() - after.value()) > epsilon;
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

    private static double fallbackPropertyValue(com.jvn.core.scene2d.Entity2D entity, PropertyType property) {
        if (property == null) return 0.0;
        if (entity == null) return property.getDefaultValue();
        return switch (property) {
            case X -> entity.getX();
            case Y -> entity.getY();
            case PIVOT_X -> getEntityPivotX(entity);
            case PIVOT_Y -> getEntityPivotY(entity);
            case ROTATION -> entity.getRotationDeg();
            case SCALE_X -> entity.getScaleX();
            case SCALE_Y -> entity.getScaleY();
            case ALPHA -> getEntityAlpha(entity);
            default -> property.getDefaultValue();
        };
    }

    private static double getEntityAlpha(com.jvn.core.scene2d.Entity2D entity) {
        if (entity instanceof com.jvn.core.scene2d.Sprite2D s) return s.getAlpha();
        if (entity instanceof com.jvn.core.scene2d.Label2D l) return l.getAlpha();
        return 1.0;
    }

    private String selectionLabel(String name, boolean group) {
        if (name == null || name.isBlank()) return "-";
        return group ? name + " [Group]" : name;
    }

    private java.util.List<Keyframe> findAdjacentKeyframes(Keyframe kf, PropertyType property) {
        if (kf == null || property == null) return null;
        EntityTrack track = selectedTrackForEditing(false);
        if (track == null) return null;
        java.util.List<Keyframe> all = track.getKeyframes(property);
        if (all == null || all.size() < 2) return null;
        java.util.List<Keyframe> adjacent = new java.util.ArrayList<>();
        int idx = all.indexOf(kf);
        if (idx < 0) return null;
        if (idx > 0) adjacent.add(all.get(idx - 1));
        if (idx < all.size() - 1) adjacent.add(all.get(idx + 1));
        return adjacent.isEmpty() ? null : adjacent;
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
